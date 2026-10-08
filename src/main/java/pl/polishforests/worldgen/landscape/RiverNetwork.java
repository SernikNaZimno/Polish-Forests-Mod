package pl.polishforests.worldgen.landscape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * River network of the "Poland" world (stage "Rivers, valleys and sea").
 *
 * <p>Three watercourse orders, each on its own offset node grid: 1 – streams (mainly in the mountains
 * and foothills), 2 – rivers, 3 – large rivers. From every node water flows to the lowest of its eight
 * neighbors (by smoothed terrain height). When no neighbor is lower, we look for a lower
 * node within a few cells (a gorge); only when there is none does a sink lake form.
 * Watercourses end in the sea, in a lake or in a watercourse of a higher order.
 *
 * <p>Everything is computed locally and deterministically, with results cached per node:
 * <ul>
 * <li>the water level is computed upstream from the mouth, always decreasing downstream, with a gradient
 * limit depending on the order (cascades in the mountains, a calm water surface in the lowlands);</li>
 * <li>channel width grows with the square root of the number of catchment nodes;</li>
 * <li>the course is a Hermite curve through the nodes with a gentle bend and irregular meanders from noise,
 * whose amplitude depends on the gradient;</li>
 * <li>the valley is a smooth transition from the floor to the original relief, with slopes of limited steepness;
 * near the source the valley deepens gradually, so no cliff forms.</li>
 * </ul>
 */
final class RiverNetwork {
	private static final int SINK = 0;
	private static final int NODE = 1;
	private static final int CAPTURE = 2;
	private static final int SEA = 3;

	private final LandscapeModel model;
	private final Noise noise;
	/** Node spacing for orders 1–3 (index = order). */
	private final double[] spacing = new double[4];
	private static final double[] BASE_SPACING = {1, 1_250, 5_000, 20_000};
	/** Smallest meander angle of a segment at which the convex bank is computed (sinuosity about 1.03). */
	static final double CONVEX_MIN_THETA = 0.35;
	/** Width of the belt (m·k) beyond the shore of a sink lake in which a query returns its shore (alder carr ring). */
	static final double LAKE_RING = 150.0;
	/**
	 * K5.2: largest sink lake gap ({@link RiverHit#lakeGap}) in m·k. The tile list holds every lake within the habitat
	 * ring ({@link #LAKE_RING}) of the tile, so the gap is exact up to that distance and capped there; a tunnel valley lake
	 * ends before a sink lake over at most {@code LAKE_GAP_MAX − 30} m·k (LandscapeModel.tunnelLakeAt).
	 */
	static final double LAKE_GAP_MAX = 150.0;
	private final double chan;
	private final double wallScale;
	private final double valleyScale;
	private final double meso;
	private final double tileSize;

	private final ConcurrentHashMap<Long, Node> nodes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Link> links = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Integer> areas = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Double> levels = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Segment> segments = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, SinkLake> sinkLakes = new ConcurrentHashMap<>();
	private final ThreadLocal<TileCache> tileCache = ThreadLocal.withInitial(TileCache::new);
	/**
	 * Step H (cost, decision D1): the outflow segment and the sink lake of a grid node in one lock-free entry, keyed by
	 * (i, 4·j + order). Building the candidate list of a tile visits up to 3 × 15 × 15 nodes, and each visit took three or
	 * four lookups with boxed {@code Long} keys in the maps above ({@code segments}, {@code nodes}, {@code links},
	 * {@code sinkLakes}); a hit here is one unboxed lookup. The entry is a pure function of the node, so the cache does
	 * not change any result. 2^18 slots (1 MB of references, entries of about 56 B only for the visited nodes): 16 384
	 * slots were thrashed by the 400 scattered chunks of {@code SampleCostTest} at realistic scale (about 500 nodes per
	 * tile), which made the REAL whole area about 1% slower instead of faster.
	 */
	private final DirectCache<NodeCandidates> nodeCandidates = new DirectCache<>(1 << 18, this::buildNodeCandidates);

	/** Outflow segment (or null) and sink lake (or null) of a grid node ({@link #nodeCandidates}). */
	private record NodeCandidates(Segment segment, SinkLake lake) {
	}

	/** Grid node: position, height smoothed for routing, ground height, belt shares. */
	record Node(int order, long i, long j, double x, double z, double route, double land, boolean sea,
			double mountains, double foothills) {
	}

	/** Where a node drains to. For {@code CAPTURE} the target is a point on a watercourse of a higher order. */
	record Link(int kind, long di, long dj, double tx, double tz, Segment target, double targetT) {
	}

	/** Lake in an endorheic depression. */
	record SinkLake(double x, double z, double radius, int level, double depth, long seed) {
	}

	/** Watercourse segment. */
	static final class Segment {
		final int order;
		final double x0;
		final double z0;
		final double x1;
		final double z1;
		final double mx0;
		final double mz0;
		final double mx1;
		final double mz1;
		final double len;
		final double level0;
		final double level1;
		final double width0;
		final double width1;
		final double wander1;
		final double wander2;
		/** Largest deviation of the channel from the valley axis (m). */
		final double amp;
		/**
		 * G2: {@link #amp} of the segment downstream on the same watercourse (main tributary of the next node), so the
		 * meander belt of the valley floor has a continuous width across the node; {@link #amp} elsewhere. Set by
		 * {@link RiverNetwork#build} before the segment is published.
		 */
		double ampEnd;
		/** G2: share of the segment length before the node over which {@link #amp} turns into {@link #ampEnd}. */
		double ampBlend = 0.5;
		/**
		 * G3: the segment joins another valley or a sink lake as a side tributary or a capture (not the main tributary of
		 * the next node and not at the sea), so its valley floor widens into a funnel before the mouth. A side tributary
		 * of a node that drains to a sink lake ends at that node (the node has no segment to join), so it has the funnel
		 * there, while the main tributary of the node has none, as at any node.
		 */
		boolean mouth;
		/**
		 * G1B: slopes of the bends {@link #wander1}, {@link #wander2} at t = 0 and t = 1 (d wander / dt), subtracted in the
		 * end quarters so that the valley axis is C1 at the nodes.
		 */
		private final double wanderSlope0;
		private final double wanderSlope1;
		/**
		 * Gradient of the segment at realistic scale ((level0 − level1) / len, at gameplay scale converted by the node
		 * spacing), computed once in {@link RiverNetwork#build} (K4.11).
		 */
		double gradient;
		/** Meander angle θ0 (rad) of the Kinoshita curve. */
		final double theta;
		final double lambda;
		final double envelope;
		/**
		 * Share of the segment from its start over which the meanders fade in: {@link #envelope}, but at a source (A5)
		 * at least the length {@link #headFade} over which the channel itself grows, so that the meander belt, and with
		 * it the valley floor, widens from the source together with the floor margin (review of step K4: in the
		 * lowlands the meanders reached their full amplitude 1.5 wavelengths from the source and the head of the floor
		 * stayed a rectangle).
		 */
		final double envelopeStart;
		final double phase;
		final double headFade;
		final boolean source;
		final Noise noise;
		/**
		 * K4b: the curve as a cubic in t relative to its start, x(t) = x0 + cx1 t + cx2 t² + cx3 t³ (the same for z), and
		 * the parts of the quintic f(t) = (P(t) − X) · P'(t) = ½ d|P(t) − X|²/dt that do not depend on the point X
		 * ({@link RiverNetwork#projectChannel}).
		 */
		private final double cx1;
		private final double cx2;
		private final double cx3;
		private final double cz1;
		private final double cz2;
		private final double cz3;
		private final double f5;
		private final double f4;
		private final double f3;
		private final double f2;
		private final double f1;
		double minX;
		double maxX;
		double minZ;
		double maxZ;
		/** Largest deviation of the curve from the chord, and the reach of influence (for fast rejection). */
		double chordDeviation;
		double reach;
		/**
		 * K4c sweep cut ({@link RiverNetwork#sweepCut}): per node t = k / SWEEP_NODES, k = 0..SWEEP_NODES,
		 * {@link RiverNetwork#SWEEP_STRIDE} values: the curve point relative to (x0, z0), the unit tangent and the speed
		 * |P'(t)| of the curve; set by {@link RiverNetwork#build} before the segment is published.
		 */
		float[] sweepNodes;
		/** K4c sweep cut: the largest |{@link #wanderAt}| over the segment (dense samples, with a margin). */
		double sweepWanderMax;
		/**
		 * K4c sweep cut: per interval of t (k / {@link RiverNetwork#SWEEP_WANDER_BINS}), the lowest and the highest
		 * {@link #wanderAt} (dense samples, with a margin).
		 */
		float[] sweepWanderRange;
		/**
		 * K4c sweep cut: per block of {@link RiverNetwork#SWEEP_BLOCK} edges of the curve polyline, the largest distance of
		 * the curve from the chord of the block (dense samples, with a margin), for the culling of whole blocks.
		 */
		float[] sweepBlockSag;
		/**
		 * K8b1 (round 1 of the review): where the channel of the segment enters a lagoon from the land
		 * ({@link RiverNetwork#lagoonMouth}), computed on the first request; {@link RiverNetwork#NO_MOUTH} when it does
		 * not. A pure function of the segment, so a race between threads only computes the same value twice.
		 */
		volatile LagoonMouth lagoonMouth;

		Segment(int order, double x0, double z0, double x1, double z1, double[] t0, double[] t1, double level0,
				double level1, double width0, double width1, double wander1, double wander2, double theta, double lambda,
				double phase, double headFade, boolean source, Noise noise) {
			this.order = order;
			this.x0 = x0;
			this.z0 = z0;
			this.x1 = x1;
			this.z1 = z1;
			this.len = Math.max(1.0, Math.hypot(x1 - x0, z1 - z0));
			this.mx0 = t0[0] * len * 0.8;
			this.mz0 = t0[1] * len * 0.8;
			this.mx1 = t1[0] * len * 0.8;
			this.mz1 = t1[1] * len * 0.8;
			this.level0 = level0;
			this.level1 = level1;
			this.width0 = width0;
			this.width1 = width1;
			this.wander1 = wander1;
			this.wander2 = wander2;
			this.theta = theta;
			this.amp = theta > 0 ? MeanderField.amplitude(Math.min(MeanderField.THETA_MAX, theta * 1.25)) * lambda : 0;
			this.ampEnd = this.amp;
			this.wanderSlope0 = Math.PI * wander1 + 2 * Math.PI * wander2;
			this.wanderSlope1 = -Math.PI * wander1 + 2 * Math.PI * wander2;
			this.maxBend = 1.12 * Math.abs(wander1) + 1.24 * Math.abs(wander2) + 0.07 * this.len;
			// Hermite basis in powers of t, from the chord (x1 − x0) so that large world coordinates do not cancel.
			double vx = x1 - x0;
			double vz = z1 - z0;
			this.cx1 = mx0;
			this.cz1 = mz0;
			this.cx2 = 3 * vx - 2 * mx0 - mx1;
			this.cz2 = 3 * vz - 2 * mz0 - mz1;
			this.cx3 = -2 * vx + mx0 + mx1;
			this.cz3 = -2 * vz + mz0 + mz1;
			double a33 = cx3 * cx3 + cz3 * cz3;
			double a32 = cx3 * cx2 + cz3 * cz2;
			double a31 = cx3 * cx1 + cz3 * cz1;
			double a22 = cx2 * cx2 + cz2 * cz2;
			double a21 = cx2 * cx1 + cz2 * cz1;
			double a11 = cx1 * cx1 + cz1 * cz1;
			this.f5 = 3 * a33;
			this.f4 = 5 * a32;
			this.f3 = 4 * a31 + 2 * a22;
			this.f2 = 3 * a21;
			this.f1 = a11;
			this.lambda = lambda;
			this.envelope = Math.min(0.5, 1.5 * lambda / len);
			this.envelopeStart = source ? Math.min(0.5, Math.max(1.5 * lambda, headFade) / len) : envelope;
			this.phase = phase;
			this.headFade = headFade;
			this.source = source;
			this.noise = noise;
		}

		double px(double t) {
			double t2 = t * t;
			double t3 = t2 * t;
			return (2 * t3 - 3 * t2 + 1) * x0 + (t3 - 2 * t2 + t) * mx0 + (-2 * t3 + 3 * t2) * x1 + (t3 - t2) * mx1;
		}

		double pz(double t) {
			double t2 = t * t;
			double t3 = t2 * t;
			return (2 * t3 - 3 * t2 + 1) * z0 + (t3 - 2 * t2 + t) * mz0 + (-2 * t3 + 3 * t2) * z1 + (t3 - t2) * mz1;
		}

		double dx(double t) {
			double t2 = t * t;
			return (6 * t2 - 6 * t) * x0 + (3 * t2 - 4 * t + 1) * mx0 + (-6 * t2 + 6 * t) * x1 + (3 * t2 - 2 * t) * mx1;
		}

		double dz(double t) {
			double t2 = t * t;
			return (6 * t2 - 6 * t) * z0 + (3 * t2 - 4 * t + 1) * mz0 + (-6 * t2 + 6 * t) * z1 + (3 * t2 - 2 * t) * mz1;
		}

		double ddx(double t) {
			return (12 * t - 6) * x0 + (6 * t - 4) * mx0 + (-12 * t + 6) * x1 + (6 * t - 2) * mx1;
		}

		double ddz(double t) {
			return (12 * t - 6) * z0 + (6 * t - 4) * mz0 + (-12 * t + 6) * z1 + (6 * t - 2) * mz1;
		}

		/** Meander angle at t: varying every few bends, faded out at the ends of the segment. */
		double thetaAt(double t) {
			if (theta <= 0) {
				return 0;
			}
			double env = Noise.smoothstep(0, envelopeStart, t) * Noise.smoothstep(0, envelope, 1 - t);
			double u = t * len / lambda;
			double f = 0.75 + 0.5 * noise.sample(u / 3.7 + phase, phase * 0.37 + 11.3);
			return Math.clamp(theta * env * f, 0.0, MeanderField.THETA_MAX);
		}

		/** Position along the valley in meander wavelengths (with a slowly varying phase). */
		double meanderU(double t) {
			double u = t * len / lambda;
			return u + phase + 0.6 * noise.sample(u / 5.0 + phase * 0.61, phase * 0.53 - 7.1);
		}

		/** Distance (m) from the channel of a point at signed distance {@code lat} from the curve at t. */
		double meanderDistance(double t, double lat) {
			double v = (lat - wanderAt(t)) / lambda;
			return MeanderField.distance(meanderU(t), v, thetaAt(t)) * lambda;
		}

		/**
		 * {@link #meanderDistance} (into {@code out[0]}, the same value) and the continuous distance for the d field
		 * (into {@code out[1]}), from {@link MeanderField#distances}; {@code out} has at least 4 slots.
		 */
		void meanderDistances(double t, double lat, double[] out) {
			meanderDistances(t, lat, wanderAt(t), out);
		}

		/** {@link #meanderDistances(double, double, double[])} with the bend {@code wander} = {@link #wanderAt}(t) known. */
		void meanderDistances(double t, double lat, double wander, double[] out) {
			double v = (lat - wander) / lambda;
			MeanderField.distances(meanderU(t), v, thetaAt(t), out);
			out[0] *= lambda;
			out[1] *= lambda;
		}

		/** Lateral offset (m) of the channel point nearest to the valley axis at t. */
		double channelOffset(double t) {
			double a = MeanderField.amplitude(thetaAt(t)) * lambda + 1;
			double best = Double.MAX_VALUE;
			double off = 0;
			for (int q = 0; q <= 40; q++) {
				double l = -a + 2 * a * q / 40.0;
				double d = meanderDistance(t, wanderAt(t) + l);
				if (d < best) {
					best = d;
					off = l;
				}
			}
			return wanderAt(t) + off;
		}

		double levelAt(double t) {
			return level0 + (level1 - level0) * t;
		}

		/**
		 * Valley bends without channel meanders: a gentle arc and a few bends at the scale of 1/5 of the segment length.
		 * G1B: in the end quarters the slope of the arc at the node is subtracted (s − s'(end)·(t − end)·q², q falling
		 * from 1 at the node to 0 at a quarter of the segment), so the valley axis has no kink at the nodes (M1: 15–72°),
		 * while the middle half of the segment, and the river in it, stays where it was.
		 */
		double wanderAt(double t) {
			double s = wander1 * Math.sin(Math.PI * t) + wander2 * Math.sin(2 * Math.PI * t);
			if (t < G1B_TAU) {
				double q = 1 - t / G1B_TAU;
				s -= wanderSlope0 * t * q * q;
			} else if (t > 1 - G1B_TAU) {
				double q = 1 - (1 - t) / G1B_TAU;
				s -= wanderSlope1 * (t - 1) * q * q;
			}
			double env = Noise.smoothstep(0, 0.2, t) * Noise.smoothstep(0, 0.2, 1 - t);
			return s + 0.07 * len * env * noise.sample(t * 5 + phase * 0.13, phase * 0.71 - 3.3);
		}

		/** Distance of a point from the segment chord (a lower bound of the distance from the curve after subtracting the deviation). */
		double chordDistance(double x, double z) {
			double vx = x1 - x0;
			double vz = z1 - z0;
			double l2 = vx * vx + vz * vz;
			double t = l2 < 1e-9 ? 0 : Math.clamp(((x - x0) * vx + (z - z0) * vz) / l2, 0.0, 1.0);
			double dx = x - (x0 + vx * t);
			double dz = z - (z0 + vz * t);
			return Math.sqrt(dx * dx + dz * dz);
		}

		double widthAt(double t) {
			return width0 + (width1 - width0) * t;
		}

		/**
		 * Whether a point at signed distance {@code lat} from the curve at t lies on the inner,
		 * convex side of a meander bend (the point bar side). Without pronounced meanders (segment θ0 below
		 * {@link #CONVEX_MIN_THETA}, sinuosity below about 1.03, i.e. mountain streams) false, unless the channel
		 * is wide ({@code wide}: Wr ≥ 6 m, gravel bars of mountain rivers on gentle bends).
		 */
		boolean convexBank(double t, double lat, boolean wide) {
			if (theta < CONVEX_MIN_THETA && !wide) {
				return false;
			}
			double th = thetaAt(t);
			if (th <= 0) {
				return false;
			}
			return MeanderField.innerSide(meanderU(t), (lat - wanderAt(t)) / lambda, th);
		}

		/**
		 * Bound of the bends of the valley axis, |{@link #wanderAt}| ≤ maxBend: the arc and the bends with the G1B end
		 * correction (at most 0.12 |wander1| + 0.24 |wander2|) and the noise of the bends.
		 */
		final double maxBend;

		/**
		 * Bound of the lateral offset of the valley axis and the channel from the curve: the bends (with the G1B end
		 * correction), the noise of the bends and the meanders.
		 */
		double maxLateral() {
			return 1.12 * Math.abs(wander1) + 1.24 * Math.abs(wander2) + 0.1 * len + 1.1 * Math.max(amp, ampEnd);
		}

		/** G2: deviation of the meander belt for the valley floor at t, continuous across the node with the next segment. */
		double ampAt(double t) {
			return ampEnd == amp ? amp : amp + (ampEnd - amp) * Noise.smoothstep(1 - ampBlend, 1.0, t);
		}
	}

	/** G1B: share of the segment at each end over which the slope of the valley bends is removed. */
	static final double G1B_TAU = 0.25;
	/** G4: relative amplitude of the irregular edge of the valley floor (margin and meander belt × (1 + a · noise)). */
	static final double EDGE_AMPLITUDE = 0.3;
	/** G4: wavelengths (m·k) and weights of the two noise waves of the floor edge. */
	static final double EDGE_WAVE_1 = 500;
	static final double EDGE_WAVE_2 = 1_700;
	static final double EDGE_WEIGHT_1 = 0.65;
	static final double EDGE_WEIGHT_2 = 0.35;
	/** G3: the mouth funnel widens the margin by this share of it plus {@link #FUNNEL_BASE} m·k. */
	static final double FUNNEL_SHARE = 0.6;
	static final double FUNNEL_BASE = 30;
	/** G5: height radius (m) of the smooth minimum where the sides of two valleys meet. */
	static final double SMOOTH_MIN_RADIUS = 4;
	/** A5: floor margin right at the source, as a share of the full margin. */
	static final double HEAD_MIN = 0.15;
	/** A5: the floor margin grows to full over this many (margin + 40 m·k) from the source. */
	static final double HEAD_GROWTH = 3;

	/** G4: noise of the irregular floor edge at (x, z), two waves, roughly in [−1, 1] (computed once per column). */
	private double edgeNoise(double x, double z) {
		return EDGE_WEIGHT_1 * noise.at(x + 731_000, z - 113_000, EDGE_WAVE_1 * valleyScale)
				+ EDGE_WEIGHT_2 * noise.at(x - 310_000, z + 977_000, EDGE_WAVE_2 * valleyScale);
	}

	/**
	 * Margin of the valley floor beyond the channel half-width at t (floorHalf = w / 2 + margin), from the floodplain
	 * margin {@code base} = (fpFactor · w + fpBase) · edge, where edge = 1 + {@link #EDGE_AMPLITUDE} · edge noise (G4:
	 * the floor edges are not parallel lines for kilometers). A5: the floor of a source segment grows from
	 * {@link #HEAD_MIN} of the margin at the source over {@link #HEAD_GROWTH} (margin + 40 m·k), so the valley head is an
	 * arc instead of a full-width rectangle (the projection behind t = 0 is clamped to the start of the curve); the
	 * meander belt of the floor narrows with it ({@link #floorBelt}).
	 */
	private double floorMargin(Segment s, double t, double base) {
		return s.source ? base * headFactor(s, t, base) : base;
	}

	/**
	 * A5 (step K4c): how far downstream of the source the rise of the floor of a source valley starts at the distance
	 * {@code va} from the valley axis, r (1 − exp(−va² / (2 r²))) with r the full floor half-width (w / 2 + margin): 0 on
	 * the axis, about va² / (2 r) near it (a parabola, i.e. an arc around the head) and at most r far from it, with a slope
	 * of at most 0.61. The floor of a source segment rises upstream at half the steepness of the sides; measured from this
	 * arc instead of from the line across the axis through the source, the contours of the head are arcs (review of K4: the
	 * head was a wedge of the narrowing floor whose contours ended on straight lines, frame R_head). A circular arc
	 * (r − √(r² − va²)) was tried first: its vertical tangent at va = r made a step along the sides of the head.
	 */
	static double headArc(double va, double r) {
		return r * (1 - Math.exp(-va * va / (2 * r * r)));
	}

	/**
	 * A5: distance from the source of a source segment at t over which its floor has risen, at the distance {@code va}
	 * from the valley axis: t · len minus the arc {@link #headArc}(va, r), which fades out between r and 3 r from the
	 * source (round 1 of the review of K4c: without the fade the arc lifted the floor and the sides of the whole rise zone
	 * by up to 0.5 · maxSlope · r, hundreds of meters from the head at gameplay scale, 11.7% of the dry land by more than
	 * 1 m, and left a narrow trench 1.3–3 m deep along the axis). Nondecreasing in t (the fade only adds), so the floor
	 * still rises monotonically upstream.
	 */
	static double headRise(Segment s, double t, double va, double r) {
		double fromSource = t * s.len;
		return Math.max(0, fromSource - headArc(va, r) * (1 - Noise.smoothstep(r, 3 * r, fromSource)));
	}

	/** A5: share of the floor margin of a source segment at t (see {@link #floorMargin}). */
	private double headFactor(Segment s, double t, double base) {
		double grow = Noise.smoothstep(0, HEAD_GROWTH * (base + 40 * valleyScale), t * s.len);
		return HEAD_MIN + (1 - HEAD_MIN) * grow;
	}

	/**
	 * Meander belt of the valley floor at t, subtracted from the distance from the valley axis in floorDist: the largest
	 * deviation of the channel from the axis ({@link Segment#ampAt}) times the lowland factor {@code beltK} and the G4
	 * edge factor {@code edge}. A5: at a source segment it narrows with the floor margin ({@link #headFactor}), but never
	 * below the meander amplitude the channel has there ({@link Segment#thetaAt} fades the meanders in over
	 * {@link Segment#envelopeStart} of the segment from the source), so the channel stays on the floor. In the lowlands the
	 * belt (up to about 2.4 amplitudes) is most of the floor, so with the margin alone the head stayed a rectangle of full
	 * width cut off at the source (review of step K4).
	 *
	 * @param base floodplain margin as in {@link #floorMargin}
	 */
	private double floorBelt(Segment s, double t, double beltK, double edge, double base) {
		double belt = s.ampAt(t) * beltK * edge;
		if (!s.source || s.amp <= 0) {
			return belt;
		}
		double head = headFactor(s, t, base);
		if (head >= 1) {
			return belt;
		}
		// Upper bound of the meander amplitude at t near the source: thetaAt ≤ 1.25 θ0 · (fade-in from the source).
		double fadeIn = Noise.smoothstep(0, s.envelopeStart, t);
		double local = MeanderField.amplitude(Math.min(MeanderField.THETA_MAX, 1.25 * s.theta * fadeIn)) * s.lambda;
		return belt * Math.max(head, Math.min(1.0, local / s.amp));
	}

	/**
	 * G3: widening of the floor at the mouth of a side tributary or a capture (a funnel over the last 4 (margin + 40 m·k)
	 * of the segment) instead of a sharp straight spur where it cuts into the side of the larger valley, from the
	 * floodplain margin {@code base} as in {@link #floorMargin} (with the A5 narrowing of a source segment). Added to
	 * the floor of the terrain only: the floor of the fields (dominant valley F1, inFloor, u, floor half-width) stays
	 * without it, because the funnel of a tributary reaching into the floor of a large river made the tributary dominant
	 * in triangles of the river floor and gave it its waterside zones there (F2 at gameplay scale: 99.6% of the river
	 * zones in the reach of the poplar riparian forest after K3, 72.7% with the funnel in the fields).
	 */
	private double funnelWidening(Segment s, double t, double base) {
		if (!s.mouth) {
			return 0;
		}
		double lf = 4 * (base + 40 * valleyScale);
		double extra = (FUNNEL_SHARE * base + FUNNEL_BASE * valleyScale) * Noise.smoothstep(lf, 0.25 * lf, (1 - t) * s.len);
		return s.source ? extra * headFactor(s, t, base) : extra;
	}

	/** K4b/K4c sweep cut: number of edges of the polyline of the curve of a segment (nodes at t = k / SWEEP_NODES). */
	static final int SWEEP_NODES = 16;
	/**
	 * K4b sweep cut: the cut of a valley is deepened to its sweep cut only where that is deeper by more than this (m), so
	 * that the terrain stays as the projection gives it wherever the projection is well conditioned.
	 */
	static final double SWEEP_TOLERANCE = 0.05;
	/** Values per node of {@link Segment#sweepNodes}: curve point relative to (x0, z0), unit tangent, speed |P'(t)|. */
	static final int SWEEP_STRIDE = 5;
	/** K4b sweep cut: edges of the polyline per block culled together ({@link Segment#sweepBlockSag}). */
	static final int SWEEP_BLOCK = 4;
	/** K4c sweep cut: samples per edge for the bounds of a block (deviation of the curve from the chord, largest bend). */
	static final int SWEEP_BOUND_SAMPLES = 8;
	/**
	 * K4c sweep cut: intervals of t with the range of the bend of the valley axis ({@link Segment#sweepWanderRange}), so a
	 * cross-section is bounded by the bend near its own estimate of the foot before the bend (noise) is evaluated.
	 */
	static final int SWEEP_WANDER_BINS = 128;
	/**
	 * K4c sweep cut: a cross-section at t that is not the foot of the perpendicular adds this times |along| (the offset of
	 * the point along the tangent of the curve at t) to its distance from the valley axis, so that on a bent curve it does
	 * not measure the point nearer than the projection does at the foot (the lateral distance from the tangent at t
	 * differs from the one at the foot by about κ s² / 2, while |along| ≈ g s).
	 */
	static final double SWEEP_PENALTY = 1.0;
	/**
	 * K4c sweep cut: the penalty is SWEEP_PENALTY · |along| smoothed near along = 0 over this many m·k (Huber: along² /
	 * (2 c) below c), so that a cross-section that wins at a tie has no crease along the normal of its node (with the
	 * plain |along| such a section left V-shaped pits up to 2.7 m deep and 2 m wide, measured as isolated jumps).
	 */
	static final double SWEEP_PENALTY_SOFT = 5.0;
	/**
	 * K4c sweep cut: lower bound of the distinctness g in the Newton estimate of the foot of the perpendicular (round 1 of
	 * the review: 1, was 0.5). The estimate t + along / (speed * g) moves with the point by 1 / (speed * g) per meter along
	 * the curve, and with it the bend of the axis and the floor taken at the estimate: with g down to 0.5 a cross-section far
	 * from its foot fell by up to 7 m per 1 m (2.4 blocks per block on the gameplay massifs), with 1 by at most 4.6 m. Where g
	 * is between 0.5 and 1 the step falls short of the foot by at most half, which {@link #SWEEP_FLOOR_MARGIN} covers.
	 */
	static final double SWEEP_MIN_DISTINCTNESS = 1.0;
	/**
	 * K4c sweep cut (round 1 of the review): lower bound of the speed |P'(t)| in the Newton estimate of the foot, as a share
	 * of the segment length. Near a cusp of the cubic curve the speed falls to a twentieth of the length, the estimate then
	 * moved by up to 1 / 60 of t per meter, and with it the bend of the axis taken there (45 m over 3 m of the curve): the
	 * cross-section fell by 27 m within 0.5 m (realistic scale (152745, 1054317)).
	 */
	static final double SWEEP_MIN_SPEED = 0.4;
	/**
	 * K4c sweep cut: the floor (and everything that grows downstream) of a cross-section is taken at t + (1 + this) times
	 * the Newton step towards the foot when the foot lies upstream, so it is never lower than the floor at the foot
	 * where the projection is well conditioned.
	 */
	static final double SWEEP_FLOOR_MARGIN = 1.0;
	/**
	 * K4c sweep cut (round 2 of the review): an extremum of f = (P − X) · P' (a root of f') enters the maximum with the
	 * weight smoothstep(0, SWEEP_TWIN · k, ξ), ξ = f''² / (2 |f'''| |P''|) at its t. Roots of f' are born in pairs where
	 * f' = f'' = 0; near such a birth f' ≈ a + b (t − t0)², so a = −f''² / (2 f''') at either root, and the column moves
	 * a by |P''| per meter: ξ estimates the distance (m) of the column from the place where the pair is born. A newborn
	 * pair thus enters with the weight 0 instead of stepping into the maximum with its full value (0.58 m at gameplay
	 * scale (−216251, 246905), 12.8 m at realistic scale (156980.5, 1059412) under a lake). The continuity does not cover
	 * the isolated points of a cusp of the fold (f''' = 0, where ξ is undefined and a pair is born and dies at once);
	 * they lie outside the reach of the valleys (re-review of K4c).
	 */
	static final double SWEEP_TWIN = 10;
	/**
	 * K4c sweep cut (round 2 of the review): an interior arm of the projection enters the maximum with the weight
	 * 1 − (1 − w) (1 − smoothstep(0, SWEEP_ARM_BIRTH, g)), w the weight of {@link #SWEEP_TWIN} at its t and g its
	 * distinctness. An arm is born out of an extremum of f (g = 0 there), and then has the weight of that extremum, so it
	 * enters with the value the extremum already had; from g = SWEEP_ARM_BIRTH on it has the full weight.
	 */
	static final double SWEEP_ARM_BIRTH = 0.05;

	/**
	 * K4c sweep cut of the valley of s at (x, z), decisions D4 and D4a: the largest cut of the cross-sections of the valley
	 * along its curve. Each cross-section is measured in the metric of the projection itself ({@link #projectChannel}):
	 * the distance from the valley axis is |lat - wander(t_f)| along the normal of the curve at t, with the bend wander
	 * taken at the Newton estimate t_f of the foot of the perpendicular, plus {@link #SWEEP_PENALTY} * |along| (smoothed near
	 * 0 over {@link #SWEEP_PENALTY_SOFT}); the floor level, the width, the margins (A5, G3) and the source head are taken
	 * upstream of the foot (t + (1 + {@link #SWEEP_FLOOR_MARGIN}) * (t_f - t) when the foot lies upstream, t otherwise) and
	 * the meander belt (which can shrink downstream, G2) is the smaller of both ends of that interval. Every cross-section
	 * is a continuous function of t and (x, z). At the foot of an arm (along = 0) the cross-section is the arm itself, with
	 * a kink maximum over t; next to it the cross-sections do not cut deeper. Where the distance from the curve is nearly
	 * the same along a part of it (ties), the projection switches between arms with very different floor levels within
	 * centimeters, and the sweep cut takes the deepest cross-section.
	 *
	 * <p>The cross-sections taken (round 1 of the review of K4c): the nodes t_k = k / {@link #SWEEP_NODES}, the interior
	 * arms of the projection (their kink maxima) and the extrema of f = (P - X) * P' (the would-be arms, where the
	 * distance is nearly stationary). The first K4c family (the nodes and the point of each edge of the curve polyline
	 * nearest to the column) missed the kink maxima at the arms between the nodes, so at ties the node cross-sections won
	 * in turn and left a field of straight ribs (one facet per node, profiles across them 401-416 m every 4 m, up to 2.5
	 * blocks per block).
	 *
	 * <p>The maximum is continuous when every member is a continuous function of (x, z) while it exists and enters or
	 * leaves the family with a value not above the maximum of the others (round 2 of the review of K4c; round 1 claimed
	 * this for the arms only). Hence: (1) an arm pair is born where an extremum of f reaches 0, at the extremum, and the
	 * arm carries the weight of the extremum there ({@link #SWEEP_ARM_BIRTH}); (2) a pair of extrema of f is born where
	 * f'' = 0 and enters with the weight 0 ({@link #SWEEP_TWIN}; with the full weight it stepped into the maximum, up to
	 * 12.8 m); (3) an extremum leaving through an end of the segment has there at most the value of the end node, because
	 * near the ends the distance of an interior cross-section passes into that of the end arm ({@link Section#fromFoot};
	 * the cut is nonincreasing in the distance without exception, also at the head arc of a source segment, because
	 * {@link #headArc} grows with the distance and so raises the floor; before, the interior formula with the Huber
	 * penalty gave up to 2.1 m more cut than the end arm, a vertical step of 2.0 m on dry land); (4) an arm leaves through
	 * an end with along = 0, where both formulas agree.
	 *
	 * <p>The first version (K4b) measured the cross-sections by the Euclidean distance from a 16-edge polyline of the valley
	 * axis. Its chords passed up to 561 m nearer than the axis (order 3 at realistic scale), and the Euclidean distance
	 * from an axis inclined against the curve is shorter than the projection's distance along the normal, so it deepened
	 * valley sides far from any tie (4.75% of the dry land at realistic scale and 16.9% at gameplay scale by more than 1 m,
	 * up to 136 m; review of K4, docs/m2/poprawka-geometrii.md, K4c).
	 *
	 * <p>Only a cut deeper than {@code limit} matters (see {@link #query}), so the method returns 0 when no cross-section
	 * can exceed it, skipping work by bounds over the whole segment, block by block of {@link #SWEEP_BLOCK} edges (the
	 * bounds hold for every t of a block) and cross-section by cross-section. With a single arm of the projection, a
	 * cross-section whose interval [tLo, tHi] contains the arm and whose floor distance bound is not below that of the arm
	 * is provably not deeper than the arm, i.e. than the projection ({@code Section.notDeeperThanArm}), so it is skipped
	 * before the bend (noise) is evaluated; the first K4c skipped the whole sweep cut at a "clear" arm instead, which was
	 * not safe (vertical scarps up to 20 m along the edge of the skipped region, round 1 of the review).
	 *
	 * @param floorOffset floor height above the water level at (x, z) ({@code floor - level} in {@link #query})
	 * @param skel        distance from the curve, {@code out[8]} of {@link #projectChannel}
	 * @param armFd       with a single arm of the projection, on a segment whose floor falls and whose channel widens
	 *                    downstream: the floor distance of the arm (va − w / 2 − belt, before the clamp at 0, as in
	 *                    {@link #query}); otherwise NaN
	 * @param armRise     the rise of the head of a source segment at the arm ({@link #headRise}; +∞ for other segments)
	 * @param sc          thread buffer with the arms of the projection of s at (x, z) ({@link Scratch#bt},
	 *                    {@link Scratch#arms}) and the roots of f' ({@link Scratch#r4}, {@link Scratch#criticals})
	 * @return the sweep cut when it exceeds {@code limit}, otherwise 0
	 */
	private double sweepCut(Segment s, double x, double z, double terrain, double floorOffset, double fpFactor,
			double fpBase, double edge, double beltK, double maxSlope, double skel, double armFd, double armRise, double limit,
			Scratch sc) {
		double minFloor = Math.min(s.level0, s.level1) + floorOffset;
		double depthMax = terrain - minFloor;
		if (depthMax <= limit) {
			return 0;
		}
		double wallMax = Math.clamp(depthMax / maxSlope, 20 * valleyScale, maxWall());
		double wMax = Math.max(s.width0, s.width1);
		double beltMax = Math.max(s.amp, s.ampEnd) * beltK * edge;
		// Upper bound of the half-width of the terrain floor (the G3 and A5 factors are at most 1).
		double halfMax = wMax / 2 + (fpFactor * wMax + fpBase) * edge * (1 + FUNNEL_SHARE) + FUNNEL_BASE * valleyScale;
		double reachMax = halfMax + wallMax;
		// Every cross-section has d ≥ |lat| + |along| − c / 2 − |wander| ≥ |X − P(t)| − c / 2 − (largest wander), with c
		// the smoothing of the penalty (Huber: along² / (2c) ≥ |along| − c / 2).
		double slack = s.sweepWanderMax + SWEEP_PENALTY_SOFT * valleyScale / 2 + wMax / 2 + beltMax;
		if (cannotCut(skel - slack, halfMax, reachMax, depthMax, limit)) {
			return 0;
		}
		int n = SWEEP_NODES;
		float[] nd = s.sweepNodes;
		float[] blockSag = s.sweepBlockSag;
		double bx = x - s.x0;
		double bz = z - s.z0;
		// Blocks of SWEEP_BLOCK edges: the curve of a block lies within its sagitta of the chord of the block, and every
		// cross-section of the block takes its floor at or upstream of the end of the block (tLo ≤ t), so a block that
		// cannot cut deeper than limit even from that distance and with that floor is skipped as a whole (all t of it).
		int live = 0;
		for (int c = 0; c < blockSag.length; c++) {
			double depth = terrain - Math.min(s.level0, s.levelAt((double) (c + 1) * SWEEP_BLOCK / n)) - floorOffset;
			if (depth <= limit) {
				continue;
			}
			int c0 = SWEEP_STRIDE * c * SWEEP_BLOCK;
			int c1 = SWEEP_STRIDE * (c + 1) * SWEEP_BLOCK;
			double cx = nd[c1] - nd[c0];
			double cz = nd[c1 + 1] - nd[c0 + 1];
			double cpx = bx - nd[c0];
			double cpz = bz - nd[c0 + 1];
			double cl2 = cx * cx + cz * cz;
			double cu = cl2 > 1e-12 ? Math.clamp((cpx * cx + cpz * cz) / cl2, 0.0, 1.0) : 0;
			double cdx = cpx - cu * cx;
			double cdz = cpz - cu * cz;
			double wall = Math.clamp(depth / maxSlope, 20 * valleyScale, maxWall());
			if (!cannotCut(Math.sqrt(cdx * cdx + cdz * cdz) - blockSag[c] - slack, halfMax, halfMax + wall, depth, limit)) {
				live |= 1 << c;
			}
		}
		if (live == 0) {
			return 0;
		}
		Section q = new Section(s, bx, bz, terrain, floorOffset, fpFactor, fpBase, edge, beltK, maxSlope, wMax, beltMax,
				halfMax, reachMax, depthMax);
		if (armFd == armFd) {
			q.armT = sc.bt[0];
			q.armFd = armFd;
			q.armRise = armRise;
		}
		// The interior arms of the projection (the feet, where a cross-section has its kink maximum) and the extrema of
		// f = (P − X) · P' (the would-be arms: where the distance is nearly stationary, an arm pair is born when the
		// extremum of f reaches 0, and the new arm starts at the extremum). Each enters with a weight (round 2 of the
		// review of K4c): an extremum with the weight of SWEEP_TWIN, 0 where it is born together with its twin; an arm
		// with the weight of SWEEP_ARM_BIRTH, equal to that of the extremum it is born out of, so it enters the maximum
		// with the value the extremum already had. A single arm is the projection itself (its cross-section is the cut of
		// the projection, not deeper than limit), so it is not evaluated. These go first: they are the likely maxima, so
		// the bounds then skip more nodes.
		int lastBlock = blockSag.length - 1;
		double best = limit;
		int arms = sc.arms > 1 ? sc.arms : 0;
		double[] f2 = sc.p3;
		for (int j = 0; j < arms + sc.criticals; j++) {
			double t = j < arms ? sc.bt[j] : sc.r4[j - arms];
			if (t > 0 && t < 1 && (live >> Math.min(lastBlock, (int) (t * n) / SWEEP_BLOCK) & 1) != 0) {
				// ξ = f''² / (2 |f'''| |P''|), f'' from the coefficients sc.p3 of distanceMinima (f' in sc.p4).
				double ff = eval(f2, 3, t);
				double fff = f2[1] + t * (2 * f2[2] + 3 * t * f2[3]);
				double ax = 2 * s.cx2 + 6 * t * s.cx3;
				double az = 2 * s.cz2 + 6 * t * s.cz3;
				double xi = ff * ff / (2 * Math.max(1e-300, Math.abs(fff) * Math.sqrt(ax * ax + az * az)));
				double weight = Noise.smoothstep(0, SWEEP_TWIN * valleyScale, xi);
				if (j < arms) {
					double vx = s.cx1 + t * (2 * s.cx2 + 3 * t * s.cx3);
					double vz = s.cz1 + t * (2 * s.cz2 + 3 * t * s.cz3);
					double g = eval(sc.p4, 4, t) / Math.max(1e-300, vx * vx + vz * vz);
					weight = 1 - (1 - weight) * (1 - Noise.smoothstep(0, SWEEP_ARM_BIRTH, g));
				}
				if (weight > 0) {
					best = Math.max(best, weight * q.at(t, best / weight));
				}
			}
		}
		// The nodes: the ends in full (their cross-section can be the end arm), the interior nodes with the bounds of
		// Section.cut before the bend inline (most nodes end there).
		if ((live & 1) != 0) {
			best = Math.max(best, q.cut(0, bx - nd[0], bz - nd[1], nd[2], nd[3], nd[4], true, best));
		}
		if ((live >> lastBlock & 1) != 0) {
			int e = SWEEP_STRIDE * n;
			best = Math.max(best, q.cut(1, bx - nd[e], bz - nd[e + 1], nd[e + 2], nd[e + 3], nd[e + 4], true, best));
		}
		// fdMin of Section.cut: |lat| − |wander| + SWEEP_PENALTY · (|along| − c / 2) − w / 2 − belt.
		double fdSlack = slack + (SWEEP_PENALTY - 1) * SWEEP_PENALTY_SOFT * valleyScale / 2;
		double minWall = 20 * valleyScale;
		double maxWall = maxWall();
		double invSlope = 1 / maxSlope;
		for (int k = 1; k < n; k++) {
			if ((live >> Math.min(lastBlock, k / SWEEP_BLOCK) & 1) == 0) {
				continue;
			}
			int i = SWEEP_STRIDE * k;
			double ex = bx - nd[i];
			double ez = bz - nd[i + 1];
			double tx = nd[i + 2];
			double tz = nd[i + 3];
			double along = ex * tx + ez * tz;
			double lat = tx * ez - tz * ex;
			double aa = Math.abs(along);
			double fdMin = Math.abs(lat) + SWEEP_PENALTY * aa - fdSlack;
			if (fdMin >= reachMax) {
				continue;
			}
			double t = (double) k / n;
			double depthT = terrain - Math.min(s.level0, s.levelAt(t)) - floorOffset;
			if (depthT <= best
					|| cannotCut(fdMin, halfMax, halfMax + Math.clamp(depthT * invSlope, minWall, maxWall), depthT, best)) {
				continue;
			}
			best = Math.max(best, q.fromFoot(t, ex, ez, nd[i + 4], along, lat, best));
		}
		return best > limit ? best : 0;
	}

	/**
	 * Whether no cross-section with the floor distance at least {@code fdMin} (distance from the valley axis minus the
	 * widest half channel and meander belt) can cut deeper than {@code best}: beyond the widest floor and wall, or not
	 * deeper even with the lowest floor ({@code depthMax}).
	 */
	private static boolean cannotCut(double fdMin, double halfMax, double reachMax, double depthMax, double best) {
		return fdMin >= reachMax || (1 - Noise.smoothstep(halfMax, reachMax, fdMin)) * depthMax <= best;
	}

	/**
	 * K4c sweep cut: the cross-sections of one segment at one column (the bounds over the segment and the inputs of
	 * {@link #query} that do not depend on t).
	 */
	private final class Section {
		final Segment s;
		/** The column relative to the start of the segment. */
		final double bx;
		final double bz;
		final double terrain;
		final double floorOffset;
		final double fpFactor;
		final double fpBase;
		final double edge;
		final double beltK;
		final double maxSlope;
		final double wMax;
		final double beltMax;
		final double halfMax;
		final double reachMax;
		final double depthMax;
		final double invSlope;
		final double minWall;
		final double maxWall;
		/** The single arm of the projection, its t and floor distance (NaN: none; see {@code armFd} of sweepCut). */
		double armT = Double.NaN;
		double armFd;
		double armRise;

		Section(Segment s, double bx, double bz, double terrain, double floorOffset, double fpFactor, double fpBase,
				double edge, double beltK, double maxSlope, double wMax, double beltMax, double halfMax, double reachMax,
				double depthMax) {
			this.s = s;
			this.bx = bx;
			this.bz = bz;
			this.terrain = terrain;
			this.floorOffset = floorOffset;
			this.fpFactor = fpFactor;
			this.fpBase = fpBase;
			this.edge = edge;
			this.beltK = beltK;
			this.maxSlope = maxSlope;
			this.wMax = wMax;
			this.beltMax = beltMax;
			this.halfMax = halfMax;
			this.reachMax = reachMax;
			this.depthMax = depthMax;
			this.invSlope = 1 / maxSlope;
			this.minWall = 20 * valleyScale;
			this.maxWall = maxWall();
		}

		/**
		 * Whether the cross-section with the floor and the margins at tLo, the meander belt the smaller of tLo and tHi, and
		 * a floor distance at least {@code fdMin} provably cuts no deeper than the single arm of the projection, which is
		 * the cut of the projection itself (round 1 of the review of K4c). The cut of a cross-section falls with its
		 * floor distance and rises with its floor depth, floor half-width and wall; with the floor falling and the channel
		 * widening downstream, tLo ≤ t* gives a floor not lower and a half-width and wall not wider than at the arm t*, and
		 * t* ≤ tHi a meander belt not wider (the smaller of the ends of an interval around t*, by the bound of the belt
		 * within it, the largest of the segment, compared with the exact belt at the arm). A source head raises the floor
		 * of the cross-section by no less than at the arm when tLo · len is at most the rise of the head at the arm (the
		 * rise of the cross-section is at most tLo · len, {@link #headRise}).
		 */
		private boolean notDeeperThanArm(double tLo, double tHi, double fdMin) {
			return tLo <= armT && armT <= tHi && fdMin >= armFd && tLo * s.len <= armRise;
		}

		/** {@link #cut} at any t, with the curve point, tangent and speed computed exactly. */
		double at(double t, double best) {
			double vx = s.cx1 + t * (2 * s.cx2 + 3 * t * s.cx3);
			double vz = s.cz1 + t * (2 * s.cz2 + 3 * t * s.cz3);
			double speed = Math.max(1e-9, Math.sqrt(vx * vx + vz * vz));
			return cut(t, bx - t * (s.cx1 + t * (s.cx2 + t * s.cx3)), bz - t * (s.cz1 + t * (s.cz2 + t * s.cz3)), vx / speed,
					vz / speed, speed, t <= 0 || t >= 1, best);
		}

		/**
		 * The cut of the cross-section at t, for the point (ex, ez) relative to the curve point P(t), with the unit
		 * tangent (tx, tz) and the speed |P'(t)| there, or {@code −∞} when a bound shows that it is not deeper than
		 * {@code best}. At an end of the segment with the point behind it ({@code end}), the cross-section is the end
		 * arm of the projection (the distance from the end of the axis, the floor at the end).
		 */
		double cut(double t, double ex, double ez, double tx, double tz, double speed, boolean end, double best) {
			double along = ex * tx + ez * tz;
			double lat = tx * ez - tz * ex;
			if (end && (t <= 0 ? along < 0 : along > 0)) {
				double lw = lat - s.wanderAt(t);
				return sectionCut(s, t, t, Math.sqrt(lw * lw + along * along), best);
			}
			// Bounds before the bend (noise): d ≥ |lat| − |wander| + SWEEP_PENALTY · (|along| − c / 2), first beyond the
			// widest wall of the segment, then with the floor at or upstream of t (tLo ≤ t).
			double soft = SWEEP_PENALTY_SOFT * valleyScale;
			double aa = Math.abs(along);
			double fdMin = Math.abs(lat) - s.sweepWanderMax + SWEEP_PENALTY * (aa - soft / 2) - wMax / 2 - beltMax;
			if (fdMin >= reachMax) {
				return Double.NEGATIVE_INFINITY;
			}
			double depthT = terrain - Math.min(s.level0, s.levelAt(t)) - floorOffset;
			if (depthT <= best || cannotCut(fdMin, halfMax, halfMax + Math.clamp(depthT * invSlope, minWall, maxWall),
					depthT, best)) {
				return Double.NEGATIVE_INFINITY;
			}
			return fromFoot(t, ex, ez, speed, along, lat, best);
		}

		/**
		 * The rest of {@link #cut} for an interior cross-section that passed the bounds before the bend: the estimate of
		 * the foot, the bound with the range of the bend near it, the single arm, then the exact cut. Within 1 /
		 * {@link #SWEEP_NODES} of an end the distance passes into that of the end arm (round 2 of the review of K4c); it
		 * only grows, so every bound before it holds.
		 */
		double fromFoot(double t, double ex, double ez, double speed, double along, double lat, double best) {
			double soft = SWEEP_PENALTY_SOFT * valleyScale;
			double aa = Math.abs(along);
			double pen = SWEEP_PENALTY * (aa < soft ? aa * aa / (2 * soft) : aa - soft / 2);
			// Newton estimate of the foot of the perpendicular: t + along / (speed * g), g = 1 - (X - P) * P'' / |P'|^2 (at
			// least SWEEP_MIN_DISTINCTNESS). One step: iterated steps run away from a nearly stationary point of the
			// distance (an end of a short segment level with the point) within centimeters, so the floor taken at the
			// estimate fell by 17 m within 0.4 m.
			double ax = 2 * s.cx2 + 6 * t * s.cx3;
			double az = 2 * s.cz2 + 6 * t * s.cz3;
			double g = Math.max(SWEEP_MIN_DISTINCTNESS, 1 - (ex * ax + ez * az) / (speed * speed));
			double tf = Math.clamp(t + along / (Math.max(speed, SWEEP_MIN_SPEED * s.len) * g), 0.0, 1.0);
			double reach = (1 + SWEEP_FLOOR_MARGIN) * (tf - t);
			double tLo = Math.clamp(t + Math.min(0, reach), 0.0, 1.0);
			double tHi = Math.clamp(t + Math.max(0, reach), 0.0, 1.0);
			// Bound with the range of the bend near the estimated foot, the width and the floor at tLo (the source head only
			// raises the floor), before the bend itself (noise) is evaluated.
			double depth = terrain - s.levelAt(tLo) - floorOffset;
			if (depth <= best) {
				return Double.NEGATIVE_INFINITY;
			}
			int bin = Math.min(SWEEP_WANDER_BINS - 1, (int) (tf * SWEEP_WANDER_BINS));
			double wLo = s.sweepWanderRange[2 * bin];
			double wHi = s.sweepWanderRange[2 * bin + 1];
			double off = lat < wLo ? wLo - lat : lat > wHi ? lat - wHi : 0;
			double w = s.widthAt(tLo);
			double fdMin2 = off + pen - w / 2 - beltMax;
			if (armT == armT && notDeeperThanArm(tLo, tHi, fdMin2)) {
				return Double.NEGATIVE_INFINITY;
			}
			double wall = Math.clamp(depth * invSlope, minWall, maxWall);
			if (fdMin2 >= halfMax + wall) {
				return Double.NEGATIVE_INFINITY;
			}
			// The half-width of the floor at tLo, bounded without the smoothsteps of the head and the funnel (A5, G3).
			double base = (fpFactor * w + fpBase) * edge;
			double half = w / 2 + base + (s.mouth ? FUNNEL_SHARE * base + FUNNEL_BASE * valleyScale : 0);
			if (fdMin2 >= half + wall || (1 - Noise.smoothstep(half, half + wall, fdMin2)) * depth <= best) {
				return Double.NEGATIVE_INFINITY;
			}
			double lw = lat - s.wanderAt(tf);
			double d = Math.abs(lw) + pen;
			// Round 2 of the review of K4c: within 1 / SWEEP_NODES of an end, with the column beyond the normal at t on the
			// side of that end, the distance passes into that of the end arm, sqrt(lw² + along²), where that is larger (by
			// at most c / 2 of the Huber penalty), so an extremum of f leaving the segment through the end has there at most
			// the value of the end node (the end arm) instead of stepping out of the maximum (2.0 m at realistic scale
			// (132704, 1042536.5)). The cut is nonincreasing in the distance without exception: at a source segment the head
			// arc (headArc) grows with the distance, so a larger distance lowers the rise of the head and raises the floor.
			// The interior nodes are unchanged (the blend is 0 at t = k / SWEEP_NODES, 0 < k < SWEEP_NODES).
			double blend = along > 0 ? Noise.smoothstep(1 - 1.0 / SWEEP_NODES, 1, t)
					: along < 0 ? 1 - Noise.smoothstep(0, 1.0 / SWEEP_NODES, t) : 0;
			if (blend > 0) {
				double dEnd = Math.sqrt(lw * lw + along * along);
				if (dEnd > d) {
					d += blend * (dEnd - d);
				}
			}
			return sectionCut(s, tLo, tHi, d, best);
		}

		/**
		 * The cut of the cross-section of s at distance d from the valley axis, as in {@link #query}, with the floor
		 * level, the width and the margins at tLo (they grow downstream) and the meander belt the smaller of tLo and tHi;
		 * {@code −∞} when a bound shows that it is not deeper than {@code best}, 0 beyond its wall.
		 */
		private double sectionCut(Segment s, double tLo, double tHi, double d, double best) {
			if (cannotCut(d - wMax / 2 - beltMax, halfMax, reachMax, depthMax, best)) {
				return Double.NEGATIVE_INFINITY;
			}
			double w = s.widthAt(tLo);
			double base = (fpFactor * w + fpBase) * edge;
			double floor = s.levelAt(tLo) + floorOffset;
			double wall = Math.clamp((terrain - floor) * invSlope, minWall, maxWall);
			if (s.source) {
				floor = Math.max(floor, terrain - 0.5 * maxSlope * headRise(s, tLo, d, w / 2 + base));
			}
			double depth = terrain - floor;
			// Bound of this cross-section (widest floor and meander belt of the segment) before the exact one.
			if (depth <= best || (1 - Noise.smoothstep(halfMax, halfMax + wall, d - w / 2 - beltMax)) * depth <= best) {
				return Double.NEGATIVE_INFINITY;
			}
			double half = w / 2 + floorMargin(s, tLo, base) + funnelWidening(s, tLo, base);
			double belt = floorBelt(s, tLo, beltK, edge, base);
			if (tHi > tLo) {
				belt = Math.min(belt, floorBelt(s, tHi, beltK, edge, base));
			}
			double fd = Math.max(0, d - w / 2 - belt);
			return fd < half + wall ? (1 - Noise.smoothstep(half, half + wall, fd)) * depth : 0;
		}
	}

	/** G5: polynomial smooth minimum of a and b with radius k (k ≤ 0: the ordinary minimum); lowers by at most k / 4. */
	static double smoothMin(double a, double b, double k) {
		if (!(k > 0)) {
			return Math.min(a, b);
		}
		double h = Math.max(k - Math.abs(a - b), 0) / k;
		return Math.min(a, b) - h * h * k * 0.25;
	}

	/**
	 * Query result in a column.
	 *
	 * @param order         order of the watercourse whose valley dominates in the column (0 = none): the valley whose
	 *                      floor the column lies deepest in (F1, key floorHalf − floorDist; a dry head never wins
	 *                      against a floor, {@link #f1Key})
	 * @param terrain       terrain after cutting the valleys (not higher than the input terrain)
	 * @param valleyWeight  1 on the valley floor, decreasing on the slopes (to suppress lakes and substrate);
	 *                      maximum over the segments in range, so continuous where valleys meet
	 * @param inFloor       the column is on the valley floor
	 * @param waterLevel    water level when the column lies in the channel; otherwise {@link ColumnSample#NO_WATER}
	 * @param channelBottom channel bottom (when in the channel)
	 * @param bankLevel     minimum bank height required by nearby channels (water level + 1 m)
	 * @param source        the dominant watercourse has its headwater zone here
	 * @param oxbowLevel    water level of the oxbow lake (when the column is in the oxbow lake)
	 * @param oxbowDepth    depth of the oxbow lake
	 * @param lakeLevel     water level of a nearby sink lake
	 * @param lakeShore     distance from the shore of that lake (negative in the lake)
	 * @param lakeDepth     depth of that lake
	 * @param lakeId        hash of that lake (0 without a lake)
	 * @param lakeRadius    radius of that lake (NaN without a lake)
	 * @param channelDist   d: distance from the bank of the nearest channel, continuous (≤ 0 in the channel, +∞ without a watercourse in range)
	 * @param channelWidth  width of that channel (NaN without a watercourse)
	 * @param channelLevel  water level of that channel, not rounded (NaN without a watercourse)
	 * @param floorU        position on the valley floor, 0 at the channel, 1 at the edge (NaN off the floor); minimum over
	 *                      the floors containing the column
	 * @param floorHalf     half-width of the valley floor (NaN without a watercourse); soft maximum over the segments
	 *                      weighted by exp(key / τ), τ = {@link #F1_TAU} m·k, so continuous where floors overlap
	 * @param slope         gradient of the dominant watercourse in ‰, at 1:1 scale (NaN without a watercourse); the same
	 *                      soft maximum as {@code floorHalf}
	 * @param convexBank    the column is on the inner side of a meander bend of the nearest channel
	 * @param oxbowShore    distance from the shore of the oxbow lake, negative inside it (+∞ outside the 40 m·k ring)
	 * @param oxbowMirror   water level of that oxbow lake, also in the ring around it
	 * @param oxbowId       hash of that oxbow lake (0 without an oxbow lake)
	 * @param oxbowWidth    half-width of that oxbow lake (NaN without an oxbow lake)
	 * @param ringShore     distance from the shore of the nearest sink lake in the wider habitat belt
	 *                      (up to {@link #LAKE_RING} m·k beyond the shore; +∞ further); the terrain uses {@code lakeShore}
	 * @param ringLevel     water level of that lake
	 * @param ringId        hash of that lake (0 without a lake)
	 * @param ringRadius    radius of that lake (NaN without a lake)
	 * @param floorChannelDist  distance from the bank of the channel of the dominant valley (habitat zones on its floor,
	 *                          F2): the nearest channel among the segments in range of the same order as the dominant
	 *                          watercourse and at least half as wide; the same watercourse also across a node. Measured
	 *                          like {@code channelDist}; +∞ without a watercourse
	 * @param floorChannelWidth width of that channel (NaN without a watercourse)
	 * @param floorChannelLevel water level of that channel, not rounded (NaN without a watercourse)
	 * @param floorChannelGradient gradient of the segment of that channel in ‰, at 1:1 scale (NaN without a
	 *                          watercourse); its own, not the soft maximum {@code slope}
	 * @param floorGap      K5.2: distance beyond the edge of the cut of the nearest valley, min over the segments of
	 *                      floorDist − terrainHalf − 0.5 wall (the floor of the terrain with the mouth funnel, G3, and half
	 *                      its wall); continuous, negative in the valley, +∞ without a segment in range (beyond the frame
	 *                      of a segment it is at least about 0.5 maxWall); tunnel valley lakes end before it
	 * @param floorEdgeGap  K8a: distance beyond the edge of the floor of the nearest valley, min over the segments of
	 *                      floorDist − terrainHalf (without the wall, so it changes by about 1 m per meter, while
	 *                      {@code floorGap} changes by up to 4–6 m per meter on steep ground); +∞ without a segment in
	 *                      range; the basins of tunnel valley lakes never reach the floor
	 * @param lakeGap       K5.2: distance from the shore of the nearest sink lake, at most {@link #LAKE_GAP_MAX} m·k
	 *                      (exact up to there: every candidate lake of the tile within it is in the tile list);
	 *                      tunnel valley lakes end before it
	 * @param softChannelLevel water level of the channels near the column blended by their distance (step H, G3): the
	 *                      levels of the candidate channels weighted by (1 − δ / r)², δ = d − d_min, r =
	 *                      {@link #SOFT_LEVEL_RATIO} · d_min + {@link #SOFT_LEVEL_BASE} m·k (at most
	 *                      {@link #SOFT_LEVEL_MAX} m·k), and by a factor that drops to {@link #SOFT_LEVEL_DRY_WEIGHT}
	 *                      for a channel far below the column terrain (a dry, uncut stretch); equal to
	 *                      {@code channelLevel} next to a single channel and continuous across the bisector between two
	 *                      channels, where {@code channelLevel} steps. Only for the habitat fields (NaN without a
	 *                      watercourse)
	 */
	record RiverHit(int order, double terrain, double valleyWeight, boolean inFloor, int waterLevel,
			double channelBottom, double bankLevel, boolean source, int oxbowLevel, double oxbowDepth, int lakeLevel,
			double lakeShore, double lakeDepth, long lakeId, double lakeRadius, double channelDist,
			double channelWidth, double channelLevel, double floorU, double floorHalf, double slope,
			boolean convexBank, double oxbowShore, int oxbowMirror, long oxbowId, double oxbowWidth, double ringShore,
			int ringLevel, long ringId, double ringRadius, double floorChannelDist, double floorChannelWidth,
			double floorChannelLevel, double floorChannelGradient, double floorGap, double floorEdgeGap, double lakeGap,
			double softChannelLevel) {
		boolean inChannel() {
			return waterLevel != ColumnSample.NO_WATER;
		}
	}

	/**
	 * K8b1 (round 1 of the review): the mouth of a river in a lagoon, the center of its delta
	 * ({@code LandscapeModel.lagoonDelta}): the channel point (x, z) where the channel of a segment, followed downstream
	 * from its start on land, first reaches lagoon water of {@link LandscapeModel#landElevation}; the unit direction of
	 * the delta into the lagoon (the mean of the valley tangent and the seaward normal of the coast); the half-width of
	 * the lobe across that direction and its reach along it (m); and the distance from the mouth beyond which the delta
	 * changes nothing (m).
	 */
	record LagoonMouth(double x, double z, double dirX, double dirZ, double half, double reach, double extent) {
	}

	/** K8b1: a segment without a mouth in a lagoon. */
	static final LagoonMouth NO_MOUTH = new LagoonMouth(0, 0, 0, 0, 0, 0, 0);
	/** K8b1: spacing of the samples of the channel when looking for the mouth (m·meso). */
	static final double MOUTH_STEP = 20;

	/** K8b1: the segments whose influence reaches the tile of (x, z) (the candidates of {@link #query}). */
	List<Segment> segmentsNear(double x, double z) {
		return candidates(x, z).segments;
	}

	/** K8b1: the mouth of segment {@code s} in a lagoon ({@link #NO_MOUTH} if its channel does not enter one from land). */
	LagoonMouth lagoonMouth(Segment s) {
		LagoonMouth m = s.lagoonMouth;
		if (m == null) {
			m = findLagoonMouth(s);
			s.lagoonMouth = m;
		}
		return m;
	}

	/** K8b1: lateral offset bound of the channel from the curve of segment {@code s} (bends and meanders, m). */
	private static double channelLateralBound(Segment s) {
		return 1.12 * Math.abs(s.wander1) + 1.24 * Math.abs(s.wander2) + 1.1 * Math.max(s.amp, s.ampEnd) + 2;
	}

	/** K8b1: the channel point of segment {@code s} at t, into {@code out} ({x, z}). */
	private static void channelPoint(Segment s, double t, double[] out) {
		double tx = s.dx(t);
		double tz = s.dz(t);
		double tl = Math.max(1e-9, Math.sqrt(tx * tx + tz * tz));
		double off = s.channelOffset(t);
		out[0] = s.px(t) - tz / tl * off;
		out[1] = s.pz(t) + tx / tl * off;
	}

	/** K8b1: whether (x, z) is lagoon water of {@link LandscapeModel#landElevation} (land side of the coast, below 0 m). */
	private boolean lagoonWater(double x, double z) {
		return model.lagoonStrength(x, z) > 0 && model.coastDistance(x, z) >= 0 && model.landElevation(x, z) < 0;
	}

	/**
	 * K8b1: {@link #lagoonMouth} computed: the channel is sampled every {@link #MOUTH_STEP} m·meso from the start of the
	 * segment; a sample is tested only where the valley curve is within reach of a lagoon (the lagoon noise and the coast
	 * distance at the curve, with the lateral bound of the channel), and the first lagoon sample after land is refined by
	 * bisection. The size comes from the half-width of a lowland valley floor at the mouth (5 w + 40 m·k beyond the
	 * channel, as in {@link #query} without the edge noise): REAL about 70–260 m, GAMEPLAY about 25–45 m. The influence is
	 * kept inside the bounding box of the segment, so every column the delta reaches has the segment in its tile list.
	 */
	private LagoonMouth findLagoonMouth(Segment s) {
		int n = (int) Math.max(8, Math.ceil(s.len / (MOUTH_STEP * meso)));
		double lat = channelLateralBound(s);
		double band = model.lagoonBand() + lat;
		double[] p = new double[2];
		double prevT = 0;
		for (int i = 0; i <= n; i++) {
			double t = (double) i / n;
			double ax = s.px(t);
			double az = s.pz(t);
			if (!model.lagoonPossible(ax, az, lat)) {
				prevT = t;
				continue;
			}
			double ad = model.coastDistance(ax, az);
			if (ad > band || ad < -lat) {
				if (ad < -lat) {
					// The channel reached the sea before any lagoon.
					return NO_MOUTH;
				}
				prevT = t;
				continue;
			}
			channelPoint(s, t, p);
			if (model.coastDistance(p[0], p[1]) < 0) {
				return NO_MOUTH;
			}
			if (lagoonWater(p[0], p[1])) {
				if (i == 0) {
					// The segment starts in the lagoon: its mouth (if any) belongs to the segment upstream.
					return NO_MOUTH;
				}
				double lo = prevT;
				double hi = t;
				for (int it = 0; it < 14; it++) {
					double mid = 0.5 * (lo + hi);
					channelPoint(s, mid, p);
					if (lagoonWater(p[0], p[1])) {
						hi = mid;
					} else {
						lo = mid;
					}
				}
				return mouthAt(s, hi, lat);
			}
			prevT = t;
		}
		return NO_MOUTH;
	}

	private LagoonMouth mouthAt(Segment s, double t, double lat) {
		double[] p = new double[2];
		channelPoint(s, t, p);
		double tx = s.dx(t);
		double tz = s.dz(t);
		double tl = Math.max(1e-9, Math.sqrt(tx * tx + tz * tz));
		// Seaward normal of the coast (the coast distance falls towards the sea).
		double e = Math.max(5, 20 * meso);
		double nx = model.coastDistance(p[0] - e, p[1]) - model.coastDistance(p[0] + e, p[1]);
		double nz = model.coastDistance(p[0], p[1] - e) - model.coastDistance(p[0], p[1] + e);
		double nl = Math.sqrt(nx * nx + nz * nz);
		double dx = tx / tl + (nl > 0 ? nx / nl : 0);
		double dz = tz / tl + (nl > 0 ? nz / nl : 0);
		double dl = Math.sqrt(dx * dx + dz * dz);
		if (!(dl > 1e-6)) {
			dx = tx / tl;
			dz = tz / tl;
			dl = 1;
		}
		double w = s.widthAt(t);
		double floorHalf = w / 2 + 5 * w + 40 * valleyScale;
		double half = LandscapeModel.DELTA_HALF * floorHalf;
		double reach = Math.min(LandscapeModel.DELTA_REACH * floorHalf,
				LandscapeModel.DELTA_WIDTH_SHARE * model.lagoonWidthAt(p[0], p[1]));
		if (!(reach > 0)) {
			return NO_MOUTH;
		}
		// Keep the influence within the bounding box of the segment (s.reach beyond the curve, the mouth within lat of it)
		// and within the belt where the columns look for deltas.
		double room = Math.min(s.reach - lat - 1, model.deltaExtentMax());
		double extent = LandscapeModel.deltaExtent(half, reach);
		for (int it = 0; it < 4 && extent > room; it++) {
			double k = room / extent;
			half *= k;
			reach *= k;
			extent = LandscapeModel.deltaExtent(half, reach);
		}
		if (!(extent <= room)) {
			return NO_MOUTH;
		}
		return new LagoonMouth(p[0], p[1], dx / dl, dz / dl, half, reach, extent);
	}

	RiverNetwork(LandscapeModel model, Noise noise, LandscapeScale scale) {
		this.model = model;
		this.noise = noise;
		this.meso = scale.meso();
		this.spacing[3] = 20_000 * scale.meso();
		this.spacing[2] = 5_000 * Math.max(scale.meso(), 0.2);
		this.spacing[1] = 1_250 * scale.mountainSpacing();
		this.chan = scale.channel();
		this.wallScale = scale == LandscapeScale.REALISTIC ? 1.0 : 1.0 / scale.local();
		this.valleyScale = scale.local();
		this.tileSize = 64;
	}

	// ------------------------------------------------------------------ nodes and drainage

	private static long key(int order, long i, long j) {
		return Noise.key(i, j, 100 + order);
	}

	private static <V> void put(ConcurrentHashMap<Long, V> map, long k, V v) {
		if (map.size() > 250_000) {
			map.clear();
		}
		map.put(k, v);
	}

	Node node(int order, long i, long j) {
		long k = key(order, i, j);
		Node n = nodes.get(k);
		if (n != null) {
			return n;
		}
		double a = spacing[order];
		double x = (i + 0.2 + 0.6 * noise.unit(i, j, 10 + order)) * a;
		double z = (j + 0.2 + 0.6 * noise.unit(i, j, 20 + order)) * a;
		double land = model.landElevation(x, z);
		double r = 0.3 * a;
		double route = land;
		for (int q = 0; q < 4; q++) {
			double ang = q * Math.PI / 2 + 0.4;
			route += model.landElevation(x + r * Math.cos(ang), z + r * Math.sin(ang));
		}
		route /= 5;
		boolean sea = model.coastDistance(x, z) < 0;
		double[] w = model.typeWeights(x, z);
		n = new Node(order, i, j, x, z, route, land, sea, w[LandscapeType.BESKIDS.ordinal()],
				w[LandscapeType.FOOTHILLS.ordinal()]);
		put(nodes, k, n);
		return n;
	}

	/** Where a node drains to. */
	Link link(Node n) {
		long k = key(n.order, n.i, n.j);
		Link cached = links.get(k);
		if (cached != null) {
			return cached;
		}
		Link result;
		if (n.sea) {
			result = new Link(SEA, n.i, n.j, n.x, n.z, null, 0);
		} else {
			// Large massifs (M2-8): a node outside the core of a large massif does not drain straight across it
			// (otherwise a river cuts a canyon up to 1000 m deep through the dome). When no lower node can be reached
			// around the core, the node drains to the lower node whose path crosses the core least, so no new sink
			// lakes appear (the old rule there could cross the summit).
			boolean strict = !model.greatMassifCore(n.x, n.z);
			Node best = lowestNeighbor(n, 1, strict);
			if (best == null) {
				// Gorge: a lower node in a farther ring.
				for (int ring = 2; ring <= 4 && best == null; ring++) {
					best = lowestNeighbor(n, ring, strict);
				}
			}
			if (best == null && strict) {
				best = leastCrossingNeighbor(n);
			}
			result = null;
			if (n.order < 3) {
				double tx = best != null ? best.x : n.x;
				double tz = best != null ? best.z : n.z;
				result = capture(n, tx, tz);
			}
			if (result == null) {
				if (best == null) {
					result = new Link(SINK, n.i, n.j, n.x, n.z, null, 0);
				} else {
					result = new Link(best.sea ? SEA : NODE, best.i, best.j, best.x, best.z, null, 0);
				}
			}
		}
		put(links, k, result);
		return result;
	}

	/**
	 * Lowest node lower than {@code n} on the ring of radius {@code ring} (in grid cells); with {@code strict} only
	 * nodes whose straight path does not cross the core of a large massif.
	 */
	private Node lowestNeighbor(Node n, int ring, boolean strict) {
		Node best = null;
		for (int di = -ring; di <= ring; di++) {
			for (int dj = -ring; dj <= ring; dj++) {
				if (Math.max(Math.abs(di), Math.abs(dj)) != ring) {
					continue;
				}
				Node m = node(n.order, n.i + di, n.j + dj);
				if (m.route < n.route - 1e-6 && (best == null || m.route < best.route)
						&& !(strict && crossesMassifCore(n.x, n.z, m.x, m.z))) {
					best = m;
				}
			}
		}
		return best;
	}

	/**
	 * Lower node in the rings 1–4 whose straight path has the smallest largest strength of a large massif (inner points
	 * as in {@link #crossesMassifCore}), the lower one on a tie; null when there is no lower node. Used only when every
	 * lower node lies across the core (seed 20260927: one node of order 1 by the massif (258824, −1539366), which the old
	 * rule sent across the summit, G 0.79, instead of along the edge of the core, G 0.37).
	 */
	private Node leastCrossingNeighbor(Node n) {
		Node best = null;
		double bestG = Double.MAX_VALUE;
		for (int ring = 1; ring <= 4; ring++) {
			for (int di = -ring; di <= ring; di++) {
				for (int dj = -ring; dj <= ring; dj++) {
					if (Math.max(Math.abs(di), Math.abs(dj)) != ring) {
						continue;
					}
					Node m = node(n.order, n.i + di, n.j + dj);
					if (m.route >= n.route - 1e-6) {
						continue;
					}
					double g = 0;
					int parts = massifPathParts(n.x, n.z, m.x, m.z);
					for (int q = 1; q < parts; q++) {
						double f = (double) q / parts;
						g = Math.max(g, model.greatMassifStrength(n.x + (m.x - n.x) * f, n.z + (m.z - n.z) * f));
					}
					if (best == null || g < bestG || g == bestG && m.route < best.route) {
						best = m;
						bestG = g;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Whether the straight path between two nodes crosses the core of a large massif. The inner points are at most a
	 * quarter of the short semi-axis Rc apart ({@link #massifPathParts}), so a long path (order 3: up to 80 km at
	 * realistic scale) cannot step over the core, which is only about 1.2 Rc wide across the range.
	 */
	private boolean crossesMassifCore(double x0, double z0, double x1, double z1) {
		if (!model.greatMassifNear(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1))) {
			return false;
		}
		int parts = massifPathParts(x0, z0, x1, z1);
		for (int q = 1; q < parts; q++) {
			double f = (double) q / parts;
			if (model.greatMassifCore(x0 + (x1 - x0) * f, z0 + (z1 - z0) * f)) {
				return true;
			}
		}
		return false;
	}

	/** Number of parts of a path checked against the massif cores: at least 8, each at most 0.25 Rc long. */
	private int massifPathParts(double x0, double z0, double x1, double z1) {
		return Math.max(8, (int) Math.ceil(Math.hypot(x1 - x0, z1 - z0) / (0.25 * model.greatMassifRc())));
	}

	/** Grid spacing of the nodes of the given order 1–3 (m). */
	double spacing(int order) {
		return spacing[order];
	}

	/** Looks for a higher-order watercourse on the way from the node to the target; returns a link to its axis or null. */
	private Link capture(Node n, double tx, double tz) {
		Segment bestSeg = null;
		double bestT = 0;
		double bestScore = Double.MAX_VALUE;
		double bestPx = 0;
		double bestPz = 0;
		double reachLow = 0.35 * spacing[n.order];
		for (int order = n.order + 1; order <= 3; order++) {
			double a = spacing[order];
			long gi = (long) Math.floor(n.x / a);
			long gj = (long) Math.floor(n.z / a);
			for (long i = gi - 2; i <= gi + 2; i++) {
				for (long j = gj - 2; j <= gj + 2; j++) {
					Segment s = segment(order, i, j);
					if (s == null) {
						continue;
					}
					double lx = Math.min(n.x, tx) - reachLow;
					double hx = Math.max(n.x, tx) + reachLow;
					double lz = Math.min(n.z, tz) - reachLow;
					double hz = Math.max(n.z, tz) + reachLow;
					if (hx < s.minX || lx > s.maxX || hz < s.minZ || lz > s.maxZ) {
						continue;
					}
					for (int q = 0; q <= 6; q++) {
						double f = q / 6.0;
						double px = n.x + (tx - n.x) * f;
						double pz = n.z + (tz - n.z) * f;
						double[] pr = project(s, px, pz);
						double d = Math.abs(pr[1] - s.wanderAt(pr[0]));
						if (d < reachLow + 0.5 * s.widthAt(pr[0])) {
							double score = d + f * spacing[n.order];
							if (score < bestScore) {
								bestScore = score;
								bestSeg = s;
								// The confluence is where the channel crosses the valley axis.
								bestT = axisCrossing(s, pr[0]);
								bestPx = channelX(s, bestT);
								bestPz = channelZ(s, bestT);
							}
						}
					}
				}
			}
		}
		return bestSeg == null ? null : new Link(CAPTURE, 0, 0, bestPx, bestPz, bestSeg, bestT);
	}

	/** Whether a spring rises at the node: streams mainly in the mountains and foothills, rivers and large rivers everywhere. */
	boolean isSpring(Node n) {
		if (n.sea) {
			return false;
		}
		// No springs on the core of a large massif (M2-8): a stream rising there would start hundreds of meters below
		// the dome (the level of a node is limited by the slope from the node downstream) and cut the summit.
		if (model.greatMassifCore(n.x, n.z)) {
			return false;
		}
		if (n.order > 1) {
			return true;
		}
		double chance = 0.06 + 0.94 * Math.min(1.0, n.mountains + n.foothills);
		return noise.unit(n.i, n.j, 31) < chance;
	}

	/** Number of springs in the catchment of the same order draining through the node (without gorges). */
	int area(Node n) {
		long k = key(n.order, n.i, n.j);
		Integer cached = areas.get(k);
		if (cached != null) {
			return cached;
		}
		int a = isSpring(n) ? 1 : 0;
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				if (di == 0 && dj == 0) {
					continue;
				}
				Node m = node(n.order, n.i + di, n.j + dj);
				Link l = link(m);
				if (l.kind == NODE && l.di == n.i && l.dj == n.j) {
					a += area(m);
				}
			}
		}
		put(areas, k, a);
		return a;
	}

	/** Sink lake of the node (or null if the node has an outflow). */
	SinkLake sinkLake(Node n) {
		if (link(n).kind != SINK) {
			return null;
		}
		long k = key(n.order, n.i, n.j);
		SinkLake cached = sinkLakes.get(k);
		if (cached != null) {
			return cached;
		}
		double a = spacing[n.order];
		double radius = Math.clamp(0.12 * a * Math.sqrt(Math.max(1, area(n))), 25 * valleyScale, 0.4 * a);
		double min = model.landElevation(n.x, n.z);
		for (int q = 0; q < 16; q++) {
			double ang = q * Math.PI / 8;
			min = Math.min(min, model.landElevation(n.x + 1.3 * radius * Math.cos(ang), n.z + 1.3 * radius * Math.sin(ang)));
		}
		int level = (int) Math.floor(min) - 1;
		double depth = 2 + 8 * noise.unit(n.i, n.j, 81 + n.order);
		SinkLake lake = new SinkLake(n.x, n.z, radius, level, depth, Noise.key(n.i, n.j, 90 + n.order));
		put(sinkLakes, k, lake);
		return lake;
	}

	/** Water level at the node, computed upstream from the mouth. */
	double level(Node n) {
		long k = key(n.order, n.i, n.j);
		Double cached = levels.get(k);
		if (cached != null) {
			return cached;
		}
		Link l = link(n);
		double incision = switch (n.order) {
			case 3 -> 8.0;
			case 2 -> 4.0;
			default -> 2.0;
		};
		double maxSlope = switch (n.order) {
			case 3 -> 0.004;
			case 2 -> 0.03;
			default -> 0.15;
		};
		double lvl;
		if (l.kind == SINK) {
			lvl = sinkLake(n).level();
		} else if (l.kind == SEA && n.sea) {
			lvl = 0.0;
		} else {
			double down;
			if (l.kind == SEA) {
				down = 0.0;
			} else if (l.kind == CAPTURE) {
				down = l.target.levelAt(l.targetT);
			} else {
				down = level(node(n.order, l.di, l.dj));
			}
			double len = Math.hypot(l.tx - n.x, l.tz - n.z);
			// Lowest terrain on the way to the target: a watercourse cannot flow above a depression (e.g. a lagoon).
			double pathLand = n.land;
			for (int q = 1; q <= 7; q++) {
				double f = q / 8.0;
				pathLand = Math.min(pathLand, model.landElevation(n.x + (l.tx - n.x) * f, n.z + (l.tz - n.z) * f));
			}
			lvl = Math.max(down + 1e-4 * len + 0.01, Math.min(pathLand - incision, down + maxSlope * len));
		}
		put(levels, k, lvl);
		return lvl;
	}

	double width(Node n) {
		double base = switch (n.order) {
			case 3 -> 12.0;
			case 2 -> 4.0;
			default -> 1.5;
		};
		return Math.clamp(base * Math.sqrt(Math.max(1, area(n))) * chan, 1.5, 400.0);
	}

	/** Whether {@code m} is the main tributary (with the largest catchment) of node {@code d}. */
	private boolean isMainUpstream(Node m, Node d) {
		int best = -1;
		long bi = 0;
		long bj = 0;
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				if (di == 0 && dj == 0) {
					continue;
				}
				Node u = node(d.order, d.i + di, d.j + dj);
				Link ul = link(u);
				if (ul.kind == NODE && ul.di == d.i && ul.dj == d.j) {
					int a = area(u);
					if (a > best) {
						best = a;
						bi = u.i;
						bj = u.j;
					}
				}
			}
		}
		return best >= 0 && bi == m.i && bj == m.j;
	}

	/** Outflow direction from the node, smoothed with the inflow direction of the main tributary. */
	private double[] tangent(Node n) {
		Link l = link(n);
		double ox = l.tx - n.x;
		double oz = l.tz - n.z;
		double ol = Math.hypot(ox, oz);
		if (ol < 1e-9) {
			return new double[] {1, 0};
		}
		ox /= ol;
		oz /= ol;
		Node main = null;
		int mainArea = 0;
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				if (di == 0 && dj == 0) {
					continue;
				}
				Node m = node(n.order, n.i + di, n.j + dj);
				Link ml = link(m);
				if (ml.kind == NODE && ml.di == n.i && ml.dj == n.j) {
					int a = area(m);
					if (a > mainArea) {
						mainArea = a;
						main = m;
					}
				}
			}
		}
		if (main != null) {
			double ix = n.x - main.x;
			double iz = n.z - main.z;
			double il = Math.hypot(ix, iz);
			ox += ix / il;
			oz += iz / il;
			double s = Math.hypot(ox, oz);
			if (s > 1e-9) {
				ox /= s;
				oz /= s;
			}
		}
		return new double[] {ox, oz};
	}

	// ------------------------------------------------------------------ segments

	private static final Segment EMPTY = new Segment(0, 0, 0, 1, 0, new double[] {1, 0}, new double[] {1, 0}, 0, 0,
			0, 0, 0, 0, 0, 1, 0, 0, false, null);

	/** Segment flowing out of the node, or null (sea node, sink node or no springs in the catchment). */
	Segment segment(int order, long i, long j) {
		long k = key(order, i, j);
		Segment cached = segments.get(k);
		if (cached != null) {
			return cached == EMPTY ? null : cached;
		}
		Node n = node(order, i, j);
		Segment s = null;
		Link l = link(n);
		if (!n.sea && l.kind != SINK && area(n) > 0) {
			s = build(n, l);
		}
		put(segments, k, s == null ? EMPTY : s);
		return s;
	}

	private Segment build(Node n, Link l) {
		double x1 = l.tx;
		double z1 = l.tz;
		double len = Math.max(1.0, Math.hypot(x1 - n.x, z1 - n.z));
		double[] t0 = tangent(n);
		Node down0 = l.kind == NODE ? node(n.order, l.di, l.dj) : null;
		// A segment ending in a sink lake near a large massif (M2-8) arrives along its chord. The tangent of a sink node
		// is the default {1, 0} (no outflow), so a segment flowing west into it bent back east near its end, and the
		// projection of points up to 2 km away fell onto the bend with a lateral distance of a few meters: straight
		// valley wedges ending in cliffs up to 1 km high around the sink lakes of the massifs (review of step K2). Only
		// near the massifs: for every sink lake (planned in K2 for step K4) it moved large rivers far from the lakes (the
		// mouth of a tributary on a segment ending in a sink lake moved by 3 km, and with it an order 3 segment of 85 km
		// by up to 1 km), and the exact projection of step K4b no longer turns the bend into cliffs
		// (docs/m2/poprawka-geometrii.md, K4).
		boolean sinkEnd = down0 != null && link(down0).kind == SINK && model.greatMassifNear(
				Math.min(n.x, down0.x) - spacing[n.order], Math.min(n.z, down0.z) - spacing[n.order],
				Math.max(n.x, down0.x) + spacing[n.order], Math.max(n.z, down0.z) + spacing[n.order]);
		double[] t1 = down0 != null && !sinkEnd ? tangent(down0) : new double[] {(x1 - n.x) / len, (z1 - n.z) / len};
		double level0 = level(n);
		double width0 = width(n);
		double level1;
		double width1;
		if (l.kind == NODE && !isMainUpstream(n, node(n.order, l.di, l.dj))) {
			// A side tributary joins the channel below the node, at a place depending on the node, instead of
			// flowing into the node itself (no "stars" of converging rivers).
			Node d = node(n.order, l.di, l.dj);
			Segment down = segment(n.order, d.i, d.j);
			if (down != null) {
				double tj = axisCrossing(down, 0.1 + 0.3 * noise.unit(n.i, n.j, 91 + n.order));
				x1 = channelX(down, tj);
				z1 = channelZ(down, tj);
				len = Math.max(1.0, Math.hypot(x1 - n.x, z1 - n.z));
				double dl = Math.max(1e-9, Math.hypot(down.dx(tj), down.dz(tj)));
				double ex = (x1 - n.x) / len + down.dx(tj) / dl;
				double ez = (z1 - n.z) / len + down.dz(tj) / dl;
				double el = Math.max(1e-9, Math.hypot(ex, ez));
				t1 = new double[] {ex / el, ez / el};
				level1 = Math.min(level(d), down.levelAt(tj));
			} else {
				level1 = level(d);
			}
			width1 = width0;
		} else if (l.kind == NODE) {
			Node d = node(n.order, l.di, l.dj);
			level1 = level(d);
			width1 = width(d);
		} else if (l.kind == CAPTURE) {
			level1 = l.target.levelAt(l.targetT);
			width1 = width0;
		} else {
			level1 = 0.0;
			width1 = width0 * 1.3;
		}
		double r1 = noise.unit(n.i, n.j, 41 + n.order) * 2 - 1;
		double r2 = noise.unit(n.i, n.j, 51 + n.order) * 2 - 1;
		double wander1 = 0.12 * len * r1;
		double wander2 = 0.05 * len * r2;
		// Meanders: wavelength about 11 channel widths; large amplitude when the gradient is small.
		double w = 0.5 * (width0 + width1);
		double slope = Math.max(0, level0 - level1) / len;
		// Gradient converted to realistic scale: at gameplay scale distances are shortened, heights are not.
		double sEff = slope * spacing[n.order] / BASE_SPACING[n.order];
		// Sinuosity: lowland rivers with a gradient below about 0.5 ‰ about 1.8–2.2, mountain streams about 1.02.
		double sinuosity = 1.02 + 1.0 * (1 - Noise.smoothstep(0.0002, 0.003, sEff))
				* (0.8 + 0.4 * noise.unit(n.i, n.j, 61 + n.order));
		double theta = MeanderField.thetaForSinuosity(sinuosity);
		// Meander wavelength about 11 channel widths.
		double lambda = 11 * Math.max(w, 2.0);
		double phase = noise.unit(n.i, n.j, 71 + n.order) * 1_000;
		boolean source = area(n) == 1 && isSpring(n);
		// Length (m) over which the channel near the source grows from zero to full width.
		double headFade = source ? 400 * valleyScale : 0.0;
		Segment s = new Segment(n.order, n.x, n.z, x1, z1, t0, t1, level0, level1, width0, width1, wander1, wander2,
				theta, lambda, phase, headFade, source, noise);
		s.gradient = Math.max(0, level0 - level1) / s.len * spacing[n.order] / BASE_SPACING[n.order];
		boolean mainDown = l.kind == NODE && isMainUpstream(n, node(n.order, l.di, l.dj));
		if (mainDown) {
			// G2: the meander belt (and with it the valley floor) passes into the belt of the next segment of the same
			// watercourse over a few wavelengths and three times the difference of the amplitudes before the node, at
			// most over the last half of the segment. Downstream only, so no cycles.
			Segment down = segment(n.order, l.di, l.dj);
			if (down != null) {
				s.ampEnd = down.amp;
				s.ampBlend = Math.min(0.5, (3 * Math.abs(down.amp - s.amp) + 2 * s.lambda) / s.len);
			}
		}
		// G3: funnel only at the mouths of side tributaries and captures; at the sea it lowered the floor below 0 m next to
		// the sea. (A segment whose own link is SINK is never built, segment(); a side tributary of a node draining to a
		// sink lake keeps the funnel, see the field.)
		s.mouth = !mainDown && l.kind != SEA;
		// Bounding box of influence from the actual curve, enlarged by the meanders, valley floor and slopes.
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (int q = 0; q <= 24; q++) {
			double t = q / 24.0;
			minX = Math.min(minX, s.px(t));
			maxX = Math.max(maxX, s.px(t));
			minZ = Math.min(minZ, s.pz(t));
			maxZ = Math.max(maxZ, s.pz(t));
		}
		double dev = 0;
		for (int q = 0; q <= 24; q++) {
			dev = Math.max(dev, s.chordDistance(s.px(q / 24.0), s.pz(q / 24.0)));
		}
		// K4.9: the floor distance subtracts the meander belt, up to ampAt · beltK · (1 + EDGE_AMPLITUDE · edge noise),
		// i.e. about 3.1 amplitudes, while maxLateral holds only 1.1; 2.2 more keep the frame from relying on the slack
		// of maxWall (segmentCullingIsInvisible).
		double reach = s.maxLateral() + 2.2 * Math.max(s.amp, s.ampEnd) + floorHalfMax(Math.max(width0, width1))
				+ maxWall() + Math.max(width0, width1);
		s.chordDeviation = dev;
		s.reach = reach;
		// K4c sweep cut: per node the curve point, tangent and speed; per block the bounds from dense samples.
		float[] nodes = new float[SWEEP_STRIDE * (SWEEP_NODES + 1)];
		for (int k = 0; k <= SWEEP_NODES; k++) {
			double t = (double) k / SWEEP_NODES;
			double tx = s.dx(t);
			double tz = s.dz(t);
			double tl = Math.max(1e-9, Math.sqrt(tx * tx + tz * tz));
			int i = SWEEP_STRIDE * k;
			nodes[i] = (float) (s.px(t) - s.x0);
			nodes[i + 1] = (float) (s.pz(t) - s.z0);
			nodes[i + 2] = (float) (tx / tl);
			nodes[i + 3] = (float) (tz / tl);
			nodes[i + 4] = (float) tl;
		}
		// The bend at the dense samples t = j / (SWEEP_NODES · SWEEP_BOUND_SAMPLES), shared by the bounds below.
		int dense = SWEEP_NODES * SWEEP_BOUND_SAMPLES;
		double[] wanders = new double[dense + 1];
		for (int j = 0; j <= dense; j++) {
			wanders[j] = s.wanderAt((double) j / dense);
		}
		int blocks = SWEEP_NODES / SWEEP_BLOCK;
		float[] blockSag = new float[blocks];
		double wanderMax = 0;
		for (int c = 0; c < blocks; c++) {
			int i0 = SWEEP_STRIDE * c * SWEEP_BLOCK;
			int i1 = SWEEP_STRIDE * (c + 1) * SWEEP_BLOCK;
			double ex = nodes[i1] - nodes[i0];
			double ez = nodes[i1 + 1] - nodes[i0 + 1];
			double l2 = Math.max(1e-12, ex * ex + ez * ez);
			double sag = 0;
			double wm = 0;
			int samples = SWEEP_BLOCK * SWEEP_BOUND_SAMPLES;
			for (int q = 0; q <= samples; q++) {
				double t = (c * SWEEP_BLOCK + (double) q / SWEEP_BOUND_SAMPLES) / SWEEP_NODES;
				double px = s.px(t) - s.x0 - nodes[i0];
				double pz = s.pz(t) - s.z0 - nodes[i0 + 1];
				double u = Math.clamp((px * ex + pz * ez) / l2, 0.0, 1.0);
				double dx = px - u * ex;
				double dz = pz - u * ez;
				sag = Math.max(sag, Math.sqrt(dx * dx + dz * dz));
				wm = Math.max(wm, Math.abs(wanders[c * samples + q]));
			}
			// Margins for the curve and the bend between the samples (1/128 of the segment apart).
			double spacing = s.len / (SWEEP_NODES * SWEEP_BOUND_SAMPLES);
			blockSag[c] = (float) (1.02 * sag + 0.05 * spacing + 0.01);
			wanderMax = Math.max(wanderMax, 1.05 * wm + 0.05 * spacing + 1);
		}
		// K4c: range of the bend per interval of t (dense samples, with the same margin), for the bounds of a cross-section.
		float[] wanderRange = new float[2 * SWEEP_WANDER_BINS];
		int perBin = dense / SWEEP_WANDER_BINS;
		double binMargin = 0.05 * s.len / dense + 1;
		for (int c = 0; c < SWEEP_WANDER_BINS; c++) {
			double lo = Double.MAX_VALUE;
			double hi = -Double.MAX_VALUE;
			for (int q = 0; q <= perBin; q++) {
				double wa = wanders[c * perBin + q];
				lo = Math.min(lo, wa);
				hi = Math.max(hi, wa);
			}
			double grow = 0.05 * (hi - lo) + binMargin;
			wanderRange[2 * c] = (float) (lo - grow);
			wanderRange[2 * c + 1] = (float) (hi + grow);
		}
		s.sweepNodes = nodes;
		s.sweepBlockSag = blockSag;
		s.sweepWanderMax = wanderMax;
		s.sweepWanderRange = wanderRange;
		s.minX = minX - reach;
		s.maxX = maxX + reach;
		s.minZ = minZ - reach;
		s.maxZ = maxZ + reach;
		return s;
	}

	/**
	 * Bound of the floor half-width of a channel of width w: the widest floor (lowland: 5 w + 40 m·k) with the largest
	 * irregular edge (G4, 1 + {@link #EDGE_AMPLITUDE} · 1.2) and the mouth funnel (G3, 0.6 of the margin + 30 m·k).
	 */
	private double floorHalfMax(double w) {
		double f = (5 * w + 40 * valleyScale) * (1 + EDGE_AMPLITUDE * 1.2);
		return w / 2 + f + 0.6 * f + 30 * valleyScale;
	}

	/** Largest width of a valley side. */
	private double maxWall() {
		return 1_200 * valleyScale;
	}

	// ------------------------------------------------------------------ geometry

	/** Projection of a point onto the segment curve: {t, signed lateral distance}. */
	static double[] project(Segment s, double px, double pz) {
		double bestT = 0;
		double bestD = Double.MAX_VALUE;
		for (int q = 0; q <= 16; q++) {
			double t = q / 16.0;
			double dx = s.px(t) - px;
			double dz = s.pz(t) - pz;
			double d = dx * dx + dz * dz;
			if (d < bestD) {
				bestD = d;
				bestT = t;
			}
		}
		double t = bestT;
		for (int it = 0; it < 5; it++) {
			double ex = s.px(t) - px;
			double ez = s.pz(t) - pz;
			double tx = s.dx(t);
			double tz = s.dz(t);
			double g = ex * tx + ez * tz;
			double h = tx * tx + tz * tz;
			if (h < 1e-12) {
				break;
			}
			t = Math.clamp(t - g / h, 0.0, 1.0);
		}
		double tx = s.dx(t);
		double tz = s.dz(t);
		double tl = Math.hypot(tx, tz);
		double ex = px - s.px(t);
		double ez = pz - s.pz(t);
		double along = tl < 1e-12 ? 0 : (ex * tx + ez * tz) / tl;
		double lateral = tl < 1e-12 ? Math.hypot(ex, ez) : (tx * ez - tz * ex) / tl;
		// Beyond the ends of the segment the distance also grows along the axis.
		if ((t <= 0 && along < 0) || (t >= 1 && along > 0)) {
			lateral = Math.copySign(Math.hypot(lateral, along), lateral == 0 ? 1 : lateral);
		}
		return new double[] {t, lateral};
	}

	/**
	 * Position of a point relative to the watercourse, written to {@code out} (at least 9 slots): {t, distance from the
	 * channel (with meanders), distance from the valley axis (with bends, without meanders), t of the nearest arm, lateral
	 * distance from the curve on it, continuous distance from the channel (the d field, {@link MeanderField#distances}), t
	 * and lateral distance of the arm that gives it, distance from the curve (the smallest over the arms)}; the number of
	 * arms goes to {@link Scratch#arms}, their t to {@link Scratch#bt} and the roots of f' to {@link Scratch#r4} (the sweep
	 * cut, {@link #sweepCut}). The work arrays come from {@code sc} (thread buffer), so a column query does not allocate
	 * them for every segment.
	 * <p>
	 * The nearest point of the curve jumps between the arms of a bend when the point lies on the inner side of the bend.
	 * Therefore all local distance minima (arms) are used: the position along the watercourse (on which water level,
	 * width and valley growth depend) and the distances from the valley axis and from the channel are soft averages over
	 * the arms, weighted by exp(−(distance − the smallest) / σ) times the distinctness of the arm (M1: the distances were
	 * the minimum over the arms, which jumped where an arm was born next to an arm with another bend, e.g. at an end of
	 * the segment, where the bends are 0).
	 * <p>
	 * K4b (decision D4): the arms are the exact local minima of the squared distance on [0, 1], found as the roots of the
	 * quintic f(t) = (P(t) − X) · P'(t) ({@link #distanceMinima}). M1 found them by comparing 17 samples of the curve and
	 * refining each with at most 5 Gauss-Newton steps inside its bracket. A weak minimum (a minimum-maximum pair between
	 * two samples) then appeared and vanished in steps with a weight far from zero, a minimum at the end of the segment
	 * was switched on and off by comparing two samples, and the refinement stopped at the edge of its bracket far from
	 * the foot of the perpendicular (a lateral distance of a few meters for a point 2 km away). The soft t of a short,
	 * steep order 1 stream then jumped by 0.1–0.2 of its length, and its valley level by tens of meters: the cliffs of
	 * A2 on the slopes of the gameplay mountains. With exact minima an arm is born or dies only at a fold, where its
	 * distinctness (fold) is 0, so its weight starts at 0, and an arm at an end of the segment turns continuously into an
	 * interior one; t and the distances are therefore continuous by construction. They are steep only where the distance
	 * is nearly the same along a part of the curve (the point near the center of curvature of an end of a short, bent
	 * segment): the arms there have small weights that change fast, by up to tens of meters of valley floor over a few
	 * centimeters. The terrain does not follow them there: {@link #query} deepens the cut of every valley to its sweep
	 * cut ({@link #sweepCut}), which is continuous by construction (docs/m2/poprawka-geometrii.md, K4b).
	 */
	private static void projectChannel(Segment s, double px, double pz, Scratch sc, double[] out) {
		double bestSkel = Double.MAX_VALUE;
		double dChannel = Double.MAX_VALUE;
		double tNear = 0;
		double latNear = 0;
		double dSmooth = Double.MAX_VALUE;
		double tSmooth = 0;
		double latSmooth = 0;
		double[] md = sc.md;
		double[] bt = sc.bt;
		double[] bf = sc.bf;
		double[] bva = sc.bva;
		double[] bch = sc.bch;
		double[] bchs = sc.bchs;
		int branches = distanceMinima(s, px, pz, sc);
		sc.arms = branches;
		for (int b = 0; b < branches; b++) {
			double t = bt[b];
			double tx = s.dx(t);
			double tz = s.dz(t);
			double tl = Math.max(1e-12, Math.sqrt(tx * tx + tz * tz));
			double ex = px - s.px(t);
			double ez = pz - s.pz(t);
			double along = (ex * tx + ez * tz) / tl;
			double lat = (tx * ez - tz * ex) / tl;
			double wander = s.wanderAt(t);
			double skel;
			double ch;
			double chs;
			double va;
			// Weight of the arm: its distinctness, from g = ½ D''(t) / |P'|² (1 for a straight segment and at a point on the
			// curve, 0 where the minimum is born or dies at a fold, i.e. at the center of curvature).
			double g = 1 - (ex * s.ddx(t) + ez * s.ddz(t)) / (tl * tl);
			double fold = Noise.smoothstep(0, ARM_FOLD, g);
			if ((t <= 0 && along < 0) || (t >= 1 && along > 0)) {
				// Beyond the end of the segment (meanders are faded out here): distance from the end of the axis. The end is
				// a minimum while the point lies behind it; it turns into an interior arm when the point comes level with
				// it (along = 0, the weight is then the distinctness of that arm) and is a full arm END_BLEND behind it.
				skel = Math.sqrt(lat * lat + along * along);
				double lw = lat - wander;
				va = Math.sqrt(lw * lw + along * along);
				ch = va;
				chs = va;
				fold += (1 - fold) * Noise.smoothstep(0, END_BLEND, Math.abs(along));
			} else {
				skel = Math.abs(lat);
				va = Math.abs(lat - wander);
				s.meanderDistances(t, lat, wander, md);
				ch = md[0];
				chs = md[1];
			}
			if (ch < dChannel) {
				dChannel = ch;
				tNear = t;
				latNear = lat;
			}
			if (chs < dSmooth) {
				dSmooth = chs;
				tSmooth = t;
				latSmooth = lat;
			}
			bestSkel = Math.min(bestSkel, skel);
			bf[b] = fold;
			bva[b] = va;
			bch[b] = ch;
			bchs[b] = chs;
		}
		double tSoft = branches > 0 ? bt[0] : 0;
		double dValley = branches > 0 ? bva[0] : Double.MAX_VALUE;
		if (branches > 1) {
			// The position along the watercourse and the distances from the valley axis and from the channel are soft
			// averages over the arms, each weighted by exp(−(its distance − the smallest) / σ) times the distinctness of
			// the arm, which starts at 0 for a newborn arm. (The minimum over the arms jumped where an arm was born next to
			// an arm with another bend, e.g. at an end of the segment, where the bends are 0: cliffs of 35 m on the
			// gameplay massifs.) σ grows with a lower bound of the distance from the valley axis, not with the distance
			// from the curve as in M1: the axis of a long segment can lie kilometers from the curve (bends up to 0.24 of
			// the length), and a point by its channel 7.6 km from the curve then mixed in an arm 2 km away.
			double sigma = 10 + 0.3 * Math.max(0, bestSkel - s.maxBend);
			double vaMin = Double.MAX_VALUE;
			double chMin = Double.MAX_VALUE;
			double chsMin = Double.MAX_VALUE;
			for (int b = 0; b < branches; b++) {
				vaMin = Math.min(vaMin, bva[b]);
				chMin = Math.min(chMin, bch[b]);
				chsMin = Math.min(chsMin, bchs[b]);
			}
			double swv = 0;
			double st = 0;
			double sva = 0;
			double swc = 0;
			double sch = 0;
			double sws = 0;
			double schs = 0;
			for (int b = 0; b < branches; b++) {
				double f = bf[b];
				double wv = Math.exp(-(bva[b] - vaMin) / sigma) * f;
				double wc = Math.exp(-(bch[b] - chMin) / sigma) * f;
				double ws = Math.exp(-(bchs[b] - chsMin) / sigma) * f;
				swv += wv;
				st += wv * bt[b];
				sva += wv * bva[b];
				swc += wc;
				sch += wc * bch[b];
				sws += ws;
				schs += ws * bchs[b];
			}
			if (swv > 1e-300 && swc > 1e-300 && sws > 1e-300) {
				tSoft = st / swv;
				dValley = sva / swv;
				dChannel = sch / swc;
				dSmooth = schs / sws;
			} else {
				// Every arm at a fold (a point at a cusp of the evolute; measure zero): the arm nearest to the axis.
				int nearest = 0;
				for (int b = 1; b < branches; b++) {
					if (bva[b] < bva[nearest]) {
						nearest = b;
					}
				}
				tSoft = bt[nearest];
				dValley = bva[nearest];
				dChannel = bch[nearest];
				dSmooth = bchs[nearest];
			}
		}
		out[0] = tSoft;
		out[1] = dChannel;
		out[2] = dValley;
		out[3] = tNear;
		out[4] = latNear;
		out[5] = dSmooth;
		out[6] = tSmooth;
		out[7] = latSmooth;
		out[8] = bestSkel;
	}

	/**
	 * K4b: distinctness g = ½ D''(t) / |P'|² at which an arm of the projection has its full weight; the weight is
	 * smoothstep(0, ARM_FOLD, g), so it is 0 where the arm is born or dies at a fold (g = 0).
	 */
	static final double ARM_FOLD = 1.0;
	/**
	 * K4b: distance behind an end of the segment (m) over which the end, a constrained minimum, gains the full weight in
	 * the soft t of {@link #projectChannel}; level with the end its weight is the distinctness of the interior arm it
	 * turns into.
	 */
	static final double END_BLEND = 40;

	/**
	 * Exact local minima of the squared distance between (px, pz) and the segment curve on [0, 1], written in increasing
	 * order of t to {@code sc.bt}; returns their number (at most 4). Interior minima are the roots of the quintic
	 * f(t) = (P(t) − X) · P'(t) where f changes sign from − to +; t = 0 is a minimum when f(0) &gt; 0 and t = 1 when
	 * f(1) &lt; 0 (the point lies behind that end). The roots are isolated without sampling: the roots of the derivatives
	 * f''' (a quadratic, solved directly), f'' and f' split [0, 1] into intervals on which the next polynomial is
	 * monotone, so each interval holds at most one of its roots, found by a safeguarded Newton iteration. No root can be
	 * missed between samples, and the result is a continuous function of the point except where a pair of roots is born
	 * or dies (a fold of the distance, where the weight of the arm is 0).
	 */
	static int distanceMinima(Segment s, double px, double pz, Scratch sc) {
		double bx = s.x0 - px;
		double bz = s.z0 - pz;
		double[] p5 = sc.p5;
		double[] p4 = sc.p4;
		double[] p3 = sc.p3;
		p5[0] = bx * s.cx1 + bz * s.cz1;
		p5[1] = s.f1 + 2 * (bx * s.cx2 + bz * s.cz2);
		p5[2] = s.f2 + 3 * (bx * s.cx3 + bz * s.cz3);
		p5[3] = s.f3;
		p5[4] = s.f4;
		p5[5] = s.f5;
		for (int i = 0; i < 5; i++) {
			p4[i] = (i + 1) * p5[i + 1];
		}
		for (int i = 0; i < 4; i++) {
			p3[i] = (i + 1) * p4[i + 1];
		}
		// f''' = p3[1] + 2 p3[2] t + 3 p3[3] t², then the roots of f'', f' and f, each between the roots of its derivative.
		double[] r2 = sc.r2;
		int n2 = quadraticRoots(p3[1], 2 * p3[2], 3 * p3[3], r2);
		double[] r3 = sc.r3;
		int n3 = rootsBetween(p3, 3, r2, n2, r3);
		double[] r4 = sc.r4;
		int n4 = rootsBetween(p4, 4, r3, n3, r4);
		sc.criticals = n4;
		// Roots of f itself in order: minima of the distance where f rises, maxima where it falls. Every maximum lies
		// between two minima (an end is a minimum when the point lies behind it). The barrier of a minimum is how much
		// higher the distance rises at the neighboring maxima (+∞ on a side without one): 0 where the minimum is born or
		// dies together with a maximum (a fold), or where an end becomes a minimum as a maximum enters through it.
		double[] bt = sc.bt;
		int n = 0;
		double lo = 0;
		double flo = p5[0];
		if (flo > 0) {
			bt[n++] = 0;
		}
		for (int k = 0; k <= n4; k++) {
			double hi = k < n4 ? r4[k] : 1;
			double fhi = eval(p5, 5, hi);
			if (flo < 0 && fhi > 0) {
				bt[n++] = solve(p5, 5, lo, hi, flo);
			}
			lo = hi;
			flo = fhi;
		}
		if (flo < 0) {
			bt[n++] = 1;
		}
		if (n == 0) {
			// Only when f is exactly 0 at an end or at a double root (measure zero): the nearer end.
			double ex1 = s.px(1) - px;
			double ez1 = s.pz(1) - pz;
			bt[n++] = bx * bx + bz * bz <= ex1 * ex1 + ez1 * ez1 ? 0 : 1;
		}
		return n;
	}

	/** Value of the polynomial c[0] + c[1] t + … + c[deg] t^deg (Horner). */
	private static double eval(double[] c, int deg, double t) {
		double v = c[deg];
		for (int i = deg - 1; i >= 0; i--) {
			v = v * t + c[i];
		}
		return v;
	}

	/**
	 * Roots in (0, 1) of the polynomial c (degree deg) whose derivative has the roots {@code crit[0..nc)} in (0, 1), in
	 * increasing order: c is monotone between them, so each interval holds at most one root. Returns their number.
	 */
	private static int rootsBetween(double[] c, int deg, double[] crit, int nc, double[] out) {
		int n = 0;
		double lo = 0;
		double flo = c[0];
		for (int k = 0; k <= nc; k++) {
			double hi = k < nc ? crit[k] : 1;
			double fhi = eval(c, deg, hi);
			if (flo < 0 && fhi > 0 || flo > 0 && fhi < 0) {
				out[n++] = solve(c, deg, lo, hi, flo);
			}
			lo = hi;
			flo = fhi;
		}
		return n;
	}

	/**
	 * Root of the polynomial c (degree deg) in (lo, hi), where it is monotone and changes sign (c(lo) = flo): Newton
	 * steps kept inside the shrinking bracket, bisection when a step leaves it.
	 */
	private static double solve(double[] c, int deg, double lo, double hi, double flo) {
		boolean neg = flo < 0;
		double t = 0.5 * (lo + hi);
		for (int it = 0; it < 60; it++) {
			double v = c[deg];
			double d = 0;
			for (int i = deg - 1; i >= 0; i--) {
				d = d * t + v;
				v = v * t + c[i];
			}
			if (v == 0) {
				return t;
			}
			if ((v < 0) == neg) {
				lo = t;
			} else {
				hi = t;
			}
			double nt = t - v / d;
			if (!(nt > lo && nt < hi)) {
				nt = 0.5 * (lo + hi);
			}
			if (Math.abs(nt - t) <= 1e-12 || hi - lo <= 1e-12) {
				return nt;
			}
			t = nt;
		}
		return t;
	}

	/** Roots in (0, 1) of a0 + a1 t + a2 t², in increasing order, written to out; returns their number. */
	private static int quadraticRoots(double a0, double a1, double a2, double[] out) {
		int n = 0;
		if (Math.abs(a2) <= 1e-12 * (Math.abs(a1) + Math.abs(a0))) {
			if (a1 != 0) {
				double r = -a0 / a1;
				if (r > 0 && r < 1) {
					out[n++] = r;
				}
			}
			return n;
		}
		double disc = a1 * a1 - 4 * a2 * a0;
		if (disc <= 0) {
			return 0;
		}
		double q = -0.5 * (a1 + Math.copySign(Math.sqrt(disc), a1));
		double ra = q / a2;
		double rb = q != 0 ? a0 / q : ra;
		double lo = Math.min(ra, rb);
		double hi = Math.max(ra, rb);
		if (lo > 0 && lo < 1) {
			out[n++] = lo;
		}
		if (hi > 0 && hi < 1 && hi != lo) {
			out[n++] = hi;
		}
		return n;
	}

	/**
	 * Work arrays of {@link #projectChannel} and {@link #query} (thread buffer in {@link TileCache}). Every
	 * slot is written before it is read, so stale values do not affect the result.
	 */
	private static final class Scratch {
		final double[] md = new double[4];
		/**
		 * Arms of the projection ({@link #distanceMinima}, at most 4 minima): t, distinctness, distance from the valley
		 * axis, from the channel and the continuous distance from the channel.
		 */
		final double[] bt = new double[6];
		final double[] bf = new double[6];
		final double[] bva = new double[6];
		final double[] bch = new double[6];
		final double[] bchs = new double[6];
		/** {@link #distanceMinima}: the quintic f and its derivatives f', f'' (coefficients), and their roots. */
		final double[] p5 = new double[6];
		final double[] p4 = new double[5];
		final double[] p3 = new double[4];
		final double[] r2 = new double[2];
		final double[] r3 = new double[3];
		final double[] r4 = new double[4];
		final double[] pr = new double[10];
		/** K5.1: buffer of {@link MeanderField#arcDistance} for the oxbow lakes. */
		final double[] arc = new double[2];
		/** Number of arms of the last {@link #projectChannel} (their t in {@link #bt}). */
		int arms;
		/** Number of roots of f' of the last {@link #distanceMinima} (in {@link #r4}). */
		int criticals;
		final double[] chHalf = new double[8];
		final double[] chDist = new double[8];
		final double[] chLevel = new double[8];
		final double[] chDepth = new double[8];
		final double[] chOwn = new double[8];
		/**
		 * Candidates for the channel of the dominant valley (F2): every segment that passes the culling frame of
		 * {@link #query}, in the order of the tile list, packed by {@value #FLOOR_STRIDE} values: {@code pr[5] − W/2},
		 * W, the water level, the order and the gradient of the segment (‰ at 1:1 scale). Primitive values only (no object references: no GC write barrier per
		 * segment and no stale segments kept by the thread buffer). The buffer grows when a column has more
		 * candidates than it holds (once per thread and model, since every {@link RiverNetwork} has its own thread
		 * buffers), so no candidate is ever dropped: a fixed limit would skip the later ones in list order and could
		 * make the zone fields jump.
		 */
		double[] floor = new double[FLOOR_CANDIDATES * FLOOR_STRIDE];
		/** End of the used part of {@link #floor} (number of candidates × {@value #FLOOR_STRIDE}). */
		int floorEnd;

		void addFloorCandidate(double d, double w, double level, int order, double gradient) {
			int q = floorEnd;
			double[] f = floor;
			if (q == f.length) {
				f = growFloor();
			}
			f[q] = d;
			f[q + 1] = w;
			f[q + 2] = level;
			f[q + 3] = order;
			f[q + 4] = gradient;
			floorEnd = q + FLOOR_STRIDE;
		}

		/** Doubles the candidate buffer (rare path, kept out of {@link #addFloorCandidate} so that it stays small). */
		private double[] growFloor() {
			floor = Arrays.copyOf(floor, 2 * floor.length);
			return floor;
		}
	}

	/**
	 * Initial capacity of the F2 candidate buffer ({@link Scratch#floor}, in candidates); WatersideZonesTest checks
	 * that columns do not exceed it, so the buffer does not have to grow in practice.
	 */
	static final int FLOOR_CANDIDATES = 64;
	/** Values per candidate in {@link Scratch#floor}: distance from the bank, width, water level, order, gradient. */
	static final int FLOOR_STRIDE = 5;

	/**
	 * Step H (G3): range of the soft channel level ({@link RiverHit#softChannelLevel}) in the excess distance δ over
	 * the nearest channel: r = SOFT_LEVEL_RATIO · d_min + SOFT_LEVEL_BASE m·k, at most SOFT_LEVEL_MAX m·k. Growing with
	 * the distance, the blend zone around the bisector between two channels widens like a cone from the confluence
	 * instead of being a fixed band. The field is continuous where every candidate within the range passes the culling
	 * frame of {@link #query}, i.e. near the channels (the habitat zones, up to several hundred m·k); far from them
	 * (d_min of roughly 700 m·k and more, review of step H) the candidate set itself changes on straight culling lines,
	 * and {@code channelLevel} steps there too.
	 */
	static final double SOFT_LEVEL_RATIO = 1.0;
	static final double SOFT_LEVEL_BASE = 5.0;
	static final double SOFT_LEVEL_MAX = 80.0;
	/**
	 * Step H, round 1 of the review: a candidate whose water level lies more than SOFT_LEVEL_DRY_FROM m below the
	 * column terrain (up to SOFT_LEVEL_DRY_TO m, smoothstep) loses its weight in the soft level down to
	 * SOFT_LEVEL_DRY_WEIGHT of it. A dry channel stretch that the valley does not cut (level tens of meters below the
	 * ground) otherwise pulled the soft level of the bank of a nearby real channel far below its water, and the bank
	 * lost its zones. The factor is continuous in the terrain and the levels, so the field stays continuous, and with
	 * a single candidate (or only dry ones) it cancels out.
	 */
	static final double SOFT_LEVEL_DRY_FROM = 4.0;
	static final double SOFT_LEVEL_DRY_TO = 12.0;
	static final double SOFT_LEVEL_DRY_WEIGHT = 0.01;

	/** F1: temperature of the soft maximum of the floor half-width and the gradient, in m·k of depth in the floor. */
	static final double F1_TAU = 15.0;
	/** F1: the soft maximum only sums segments within this many τ of the deepest valley (weights below e^−8). */
	static final double F1_SOFT_BAND = 8.0;
	/**
	 * F1: gap below zero for the key of a dry valley head (head fade ≤ 0.5), in m·k; see {@link #f1Key}.
	 */
	static final double F1_HEAD_GAP = 1.0;

	/**
	 * F1 key of a segment: the depth of the column in its valley floor, {@code kh = floorHalf − floorDist} (m, negative
	 * off the floor). A dry valley head (head fade ≤ 0.5) has no floor, so its key is capped below zero: kh − δ off
	 * its floor and −δ² / (δ + kh) on it (δ = {@link #F1_HEAD_GAP} m·k), continuous and increasing in kh and always
	 * negative. A dry head therefore never dominates a valley floor (key > 0), so the column is in the floor of some
	 * valley exactly when it is in the floor of the dominant one, but it keeps its own head and the area behind its
	 * source against valleys whose floors are farther away, as in M1 (the review of K3: a fixed penalty handed the
	 * whole dry head to an unrelated valley and made order and gradient jump on the straight fade-0.5 line).
	 */
	static double f1Key(double floorHalf, double floorDist, double fade, double valleyScale) {
		double kh = floorHalf - floorDist;
		if (fade > 0.5) {
			return kh;
		}
		double gap = F1_HEAD_GAP * valleyScale;
		return kh > 0 ? -gap * gap / (gap + kh) : kh - gap;
	}

	/**
	 * Share of the meander belt in the valley floor, as a multiple of the meander amplitude (TE). It grows with the
	 * lowland share smoothly between 0.2 and 0.4; M1 switched it on at lowland 0.3 with a jump of 1.4 · 0.3 amplitudes,
	 * which made scarps of 5–20 m along the straight contours of the lowland share.
	 */
	static double meanderBeltFactor(double lowland) {
		return 1 + 1.4 * lowland * Noise.smoothstep(0.2, 0.4, lowland);
	}

	/** Valley axis point at t (the channel crosses the axis here when t comes from {@link #axisCrossing}). */
	private static double channelX(Segment s, double t) {
		double tl = Math.max(1e-12, Math.hypot(s.dx(t), s.dz(t)));
		return s.px(t) - s.dz(t) / tl * s.wanderAt(t);
	}

	private static double channelZ(Segment s, double t) {
		double tl = Math.max(1e-12, Math.hypot(s.dx(t), s.dz(t)));
		return s.pz(t) + s.dx(t) / tl * s.wanderAt(t);
	}

	/** Place near t where the channel crosses the valley axis (a tributary joins there). */
	private static double axisCrossing(Segment s, double t) {
		if (s.theta <= 0) {
			return t;
		}
		double span = s.lambda / s.len;
		double best = Double.MAX_VALUE;
		double bestT = t;
		for (int q = -16; q <= 16; q++) {
			double tk = Math.clamp(t + span * q / 16.0, 0.02, 0.98);
			double d = s.meanderDistance(tk, s.wanderAt(tk));
			if (d < best) {
				best = d;
				bestT = tk;
			}
		}
		return bestT;
	}

	// ------------------------------------------------------------------ column query

	/**
	 * Candidates (segments and lakes) for a 64 × 64 m tile, cached per thread. The thread local belongs to one network,
	 * so the cache holds no reference back to it: with such a reference (M1, the field {@code owner}) the value kept its
	 * own thread local reachable, and every network used on the threads of a pool stayed in memory with its caches
	 * (the test JVM ran out of 3 GB after the K4 tests that build many segments).
	 */
	private static final class TileCache {
		long key = Long.MIN_VALUE;
		final List<Segment> segments = new ArrayList<>();
		final List<SinkLake> lakes = new ArrayList<>();
		final Scratch scratch = new Scratch();
	}

	/**
	 * Candidates of the tile containing (x, z). The segment list is built in a fixed order (order 3 to 1, then the
	 * grid indices i and j), and a column only uses the segments that pass its own culling frame, so every column
	 * gets the same candidates in the same order whichever tile, thread or sampling order produced the list. Ties in
	 * {@link #query} (the dominant valley, the channel of the dominant valley) and the cut-off of the F1 soft maximum
	 * ({@link #F1_SOFT_BAND}) depend on this order, so it must not change (e.g. to a hash set or to an order
	 * depending on which thread built a segment first).
	 */
	private TileCache candidates(double x, double z) {
		long tx = (long) Math.floor(x / tileSize);
		long tz = (long) Math.floor(z / tileSize);
		long k = Noise.key(tx, tz, 7);
		TileCache c = tileCache.get();
		if (c.key == k) {
			return c;
		}
		c.key = k;
		c.segments.clear();
		c.lakes.clear();
		double lx = tx * tileSize;
		double lz = tz * tileSize;
		double hx = lx + tileSize;
		double hz = lz + tileSize;
		double ring = LAKE_RING * valleyScale;
		tileRadiusNodes(lx, lz, (order, i, j) -> {
			NodeCandidates nc = nodeCandidates.get(i, 4 * j + order);
			Segment s = nc.segment;
			if (s != null && !(hx < s.minX || lx > s.maxX || hz < s.minZ || lz > s.maxZ)) {
				c.segments.add(s);
			}
			SinkLake lake = nc.lake;
			// The M1 filter (lake.radius * 1.6 + tile) widened by the habitat ring; the terrain uses only the lakes from the
			// M1 filter (nearTile).
			if (lake != null && Math.abs(lake.x - (lx + tileSize / 2)) < lake.radius * 1.6 + ring + tileSize
					&& Math.abs(lake.z - (lz + tileSize / 2)) < lake.radius * 1.6 + ring + tileSize) {
				c.lakes.add(lake);
			}
		});
		return c;
	}

	/** Entry of {@link #nodeCandidates} for the key (i, 4·j + order). */
	private NodeCandidates buildNodeCandidates(long i, long key) {
		int order = (int) Math.floorMod(key, 4L);
		long j = Math.floorDiv(key, 4L);
		return new NodeCandidates(segment(order, i, j), sinkLake(node(order, i, j)));
	}

	/** Grid node of one order (see {@link #tileRadiusNodes}). */
	@FunctionalInterface
	private interface NodeVisitor {
		void visit(int order, long i, long j);
	}

	/**
	 * Visits the grid nodes within {@link #tileRadius} of the tile with the corner (lx, lz), in the fixed order of the
	 * tile list (order 3 to 1, then i and j). Shared by {@link #candidates} and {@link #tileRadiusSegments}, so the test of
	 * the culling sees the same segments as the query (review of K4: one helper instead of two copies of the loop).
	 */
	private void tileRadiusNodes(double lx, double lz, NodeVisitor v) {
		for (int order = 3; order >= 1; order--) {
			double a = spacing[order];
			long gi = (long) Math.floor((lx + tileSize / 2) / a);
			long gj = (long) Math.floor((lz + tileSize / 2) / a);
			int r = tileRadius(order);
			for (long i = gi - r; i <= gi + r; i++) {
				for (long j = gj - r; j <= gj + r; j++) {
					v.visit(order, i, j);
				}
			}
		}
	}

	/**
	 * Radius (in grid cells of the order) around the tile center within which {@link #candidates} looks for segments: it
	 * covers the longest segments (a gorge of up to 4 cells) and the full reach of the valley ({@link #tileRadiusNodes}).
	 */
	private int tileRadius(int order) {
		double a = spacing[order];
		return Math.min(7, (int) Math.ceil((4.5 * a + maxWall() + 600 * valleyScale) / a) + 1);
	}

	/** Culling frame of {@link #query}: whether the segment can influence the column (x, z). */
	private static boolean inFrame(Segment s, double x, double z) {
		return !(x < s.minX || x > s.maxX || z < s.minZ || z > s.maxZ
				|| s.chordDistance(x, z) - s.chordDeviation > s.reach);
	}

	/**
	 * Number of segments that pass the culling frame of {@link #query} at (x, z), i.e. the F2 candidates of the
	 * column (tests: the candidate buffer must not have to grow, {@link #FLOOR_CANDIDATES}).
	 */
	int frameCandidates(double x, double z) {
		int n = 0;
		for (Segment s : candidates(x, z).segments) {
			if (inFrame(s, x, z)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Floor of the valley of one segment at (x, z), measured as in {@link #query} (test code only,
	 * {@code valleyHeadsAreRounded}): {floorDist, floorHalf of the fields, terrainHalf with the mouth funnel}; the terrain
	 * of the segment is its floor exactly where floorDist ≤ terrainHalf. Allocates.
	 */
	double[] floorGeometry(Segment s, double x, double z, double lowland, double foothills, double mountains) {
		double fpFactor = 5.0 * lowland + 1.5 * foothills + 0.3 * mountains;
		double fpBase = (40.0 * lowland + 10.0 * foothills + 2.0 * mountains) * valleyScale;
		double edge = 1 + EDGE_AMPLITUDE * edgeNoise(x, z);
		double[] pr = new double[10];
		projectChannel(s, x, z, new Scratch(), pr);
		double t = pr[0];
		double w = s.widthAt(t);
		double base = (fpFactor * w + fpBase) * edge;
		double floorHalf = w / 2 + floorMargin(s, t, base);
		double floorDist = Math.max(0, pr[2] - w / 2 - floorBelt(s, t, meanderBeltFactor(lowland), edge, base));
		return new double[] {floorDist, floorHalf, floorHalf + funnelWidening(s, t, base)};
	}

	/**
	 * Geometry of every segment of the given order that passes the culling frame at (x, z), measured as in
	 * {@link #query} but without choosing a dominant valley (tests: the F2 measurement defines the river from its own
	 * segments, independently of {@code best} and of the F2 fields). Per segment, in the order of the tile list:
	 * {distance from the bank {@code pr[5] − W/2}, W, water level, distance from the floor edge {@code floorDist},
	 * floor half-width {@code floorHalf}, head fade, gradient in ‰ at realistic scale}. Allocates; test code only.
	 *
	 * @param lowland   share of lowland as passed to {@link #query} (with the coastland)
	 * @param foothills share of foothills
	 * @param mountains share of mountains
	 */
	List<double[]> segmentsAt(int order, double x, double z, double lowland, double foothills, double mountains) {
		double fpFactor = 5.0 * lowland + 1.5 * foothills + 0.3 * mountains;
		double fpBase = (40.0 * lowland + 10.0 * foothills + 2.0 * mountains) * valleyScale;
		Scratch sc = new Scratch();
		double[] pr = new double[10];
		List<double[]> out = new ArrayList<>();
		double edge = 1 + EDGE_AMPLITUDE * edgeNoise(x, z);
		for (Segment s : candidates(x, z).segments) {
			if (s.order != order || !inFrame(s, x, z)) {
				continue;
			}
			projectChannel(s, x, z, sc, pr);
			double t = pr[0];
			double w = s.widthAt(t);
			double base = (fpFactor * w + fpBase) * edge;
			double floorHalf = w / 2 + floorMargin(s, t, base);
			double floorDist = Math.max(0, pr[2] - w / 2 - floorBelt(s, t, meanderBeltFactor(lowland), edge, base));
			double fade = s.headFade > 0 ? Noise.smoothstep(0, s.headFade, t * s.len) : 1.0;
			double slope = s.gradient;
			out.add(new double[] {pr[5] - 0.5 * w, w, s.levelAt(t), floorDist, floorHalf, fade, slope * 1_000});
		}
		return out;
	}

	/**
	 * Influence of the watercourses on a column.
	 *
	 * @param terrain   terrain height before cutting the valleys
	 * @param lowland   share of lowland at the point
	 * @param foothills share of foothills
	 * @param mountains share of mountains
	 */
	RiverHit query(double x, double z, double terrain, double lowland, double foothills, double mountains) {
		TileCache c = candidates(x, z);
		return query(x, z, terrain, lowland, foothills, mountains, c, c.segments, true, true, true);
	}

	/**
	 * {@link #query} without the sweep cut, i.e. the terrain of the projection alone (test code only:
	 * {@code sweepCutKeepsWellConditionedTerrain}, decision D4a).
	 */
	RiverHit queryWithoutSweep(double x, double z, double terrain, double lowland, double foothills, double mountains) {
		TileCache c = candidates(x, z);
		return query(x, z, terrain, lowland, foothills, mountains, c, c.segments, true, false, false);
	}

	/**
	 * {@link #query} with the sweep cut but without the prune of the cross-sections that are provably not deeper than the
	 * single arm of the projection ({@code Section.notDeeperThanArm}); test code only ({@code sweepCutPruneIsExact},
	 * round 1 of the review of K4c): the terrain must be the same as with the prune.
	 */
	RiverHit queryWithoutPrune(double x, double z, double terrain, double lowland, double foothills, double mountains) {
		TileCache c = candidates(x, z);
		return query(x, z, terrain, lowland, foothills, mountains, c, c.segments, true, true, false);
	}

	/** Segments that pass the culling frame of {@link #query} at (x, z), in the order of the tile list (test code only). */
	List<Segment> frameSegments(double x, double z) {
		List<Segment> out = new ArrayList<>();
		for (Segment s : candidates(x, z).segments) {
			if (inFrame(s, x, z)) {
				out.add(s);
			}
		}
		return out;
	}

	/**
	 * Conditioning of the projection of (x, z) on s (test code only, decision D4a): {number of arms, distinctness g of the
	 * dominant arm (the largest weight in the soft averages of {@link #projectChannel}), its share of the weights, 1 when
	 * it is an end of the segment with the point behind it (else 0), the distance of the point behind that end along the
	 * tangent (else 0), its t, the distance of the point from the curve}. Allocates.
	 */
	double[] armInfo(Segment s, double x, double z) {
		Scratch sc = new Scratch();
		double[] pr = new double[10];
		projectChannel(s, x, z, sc, pr);
		int n = distanceMinima(s, x, z, sc);
		double sigma = 10 + 0.3 * Math.max(0, pr[8] - s.maxBend);
		double vaMin = Double.MAX_VALUE;
		for (int b = 0; b < n; b++) {
			vaMin = Math.min(vaMin, sc.bva[b]);
		}
		double sum = 0;
		double top = -1;
		int dom = 0;
		for (int b = 0; b < n; b++) {
			double wv = Math.exp(-(sc.bva[b] - vaMin) / sigma) * sc.bf[b];
			sum += wv;
			if (wv > top) {
				top = wv;
				dom = b;
			}
		}
		double t = sc.bt[dom];
		double tx = s.dx(t);
		double tz = s.dz(t);
		double tl = Math.max(1e-12, Math.sqrt(tx * tx + tz * tz));
		double ex = x - s.px(t);
		double ez = z - s.pz(t);
		double g = 1 - (ex * s.ddx(t) + ez * s.ddz(t)) / (tl * tl);
		double along = (ex * tx + ez * tz) / tl;
		boolean end = t <= 0 && along < 0 || t >= 1 && along > 0;
		return new double[] {n, g, sum > 0 ? top / sum : 0, end ? 1 : 0, end ? Math.abs(along) : 0, t, pr[8]};
	}

	/**
	 * Every segment of the tile radius of {@link #candidates} at (x, z), without the box of influence (test code only,
	 * {@code segmentCullingIsInvisible}).
	 */
	List<Segment> tileRadiusSegments(double x, double z) {
		List<Segment> all = new ArrayList<>();
		tileRadiusNodes(Math.floor(x / tileSize) * tileSize, Math.floor(z / tileSize) * tileSize, (order, i, j) -> {
			Segment s = segment(order, i, j);
			if (s != null) {
				all.add(s);
			}
		});
		return all;
	}

	/**
	 * {@link #query} over the given segments only, without culling them (K4.9, tests): {@code segmentCullingIsInvisible}
	 * passes {@link #tileRadiusSegments}, {@code valleyHeadsAreRounded} a single segment. The sink lakes are those of the
	 * tile, as in {@link #query}. Test code only.
	 */
	RiverHit querySegments(double x, double z, double terrain, double lowland, double foothills, double mountains,
			List<Segment> segments) {
		return querySegments(x, z, terrain, lowland, foothills, mountains, segments, true);
	}

	/** {@link #querySegments} with or without the sweep cut (test code only). */
	RiverHit querySegments(double x, double z, double terrain, double lowland, double foothills, double mountains,
			List<Segment> segments, boolean sweep) {
		return query(x, z, terrain, lowland, foothills, mountains, candidates(x, z), segments, false, sweep, true);
	}

	private RiverHit query(double x, double z, double terrain, double lowland, double foothills, double mountains,
			TileCache c, List<Segment> segments, boolean cull, boolean sweep, boolean prune) {
		double fpFactor = 5.0 * lowland + 1.5 * foothills + 0.3 * mountains;
		double fpBase = (40.0 * lowland + 10.0 * foothills + 2.0 * mountains) * valleyScale;
		// Largest steepness of the valley sides (tangent) before the terrain returns to the original relief.
		double maxSlope = (0.12 * lowland + 0.35 * foothills + 0.7 * mountains) * wallScale;
		maxSlope = Math.max(maxSlope, 0.05);

		double result = terrain;
		double bank = Double.NEGATIVE_INFINITY;
		double beltK = meanderBeltFactor(lowland);
		// Dominant valley (F1): the valley whose floor the column lies deepest in, key = floorHalf − floorDist (m).
		// The fields of the valley floor are continuous across overlapping floors: valleyWeight is the maximum and u
		// the minimum over the segments, floorHalf and the gradient a soft maximum by the key (τ = F1_TAU m·k).
		Segment best = null;
		double bestKey = Double.NEGATIVE_INFINITY;
		double vwMax = 0;
		double uMin = Double.POSITIVE_INFINITY;
		// G3: the column lies in the mouth funnel of some segment (on its terrain floor, beyond its floor without the funnel).
		boolean inFunnel = false;
		double tau = F1_TAU * valleyScale;
		double softBand = F1_SOFT_BAND * tau;
		double sumW = 0;
		double sumFh = 0;
		double sumSl = 0;
		double bestFloorHalf = 0;
		double bestFloorDist = 0;
		double bestT = 0;
		double bestLat = 0;
		double bestFade = 1;
		// K5.1: the next two keys below the dominant valley (its rivals), for the fade of the oxbow lakes where the
		// dominant valley changes.
		double key2 = Double.NEGATIVE_INFINITY;
		Segment seg2 = null;
		double key3 = Double.NEGATIVE_INFINITY;
		int water = ColumnSample.NO_WATER;
		double channelBottom = 0;
		// Nearby channels: {half-width, distance, level, depth}; evaluated after cutting the valleys.
		int channelCount = 0;
		Scratch sc = c.scratch;
		double[] chHalf = sc.chHalf;
		double[] chDist = sc.chDist;
		double[] chLevel = sc.chLevel;
		double[] chDepth = sc.chDepth;
		double[] chOwn = sc.chOwn;
		double[] pr = sc.pr;
		// Nearest channel among all segments in range (habitat fields, they do not affect the terrain).
		double nearDist = Double.POSITIVE_INFINITY;
		Segment nearSeg = null;
		double nearWidth = Double.NaN;
		double nearLevel = Double.NaN;
		double nearT = 0;
		double nearLat = 0;
		sc.floorEnd = 0;
		// G4: 1 + EDGE_AMPLITUDE · edge noise, computed lazily at the first segment in range (NaN until then).
		double edge = Double.NaN;
		// G5: largest floor mask of the segments so far.
		double maskAcc = 0;
		// K5.2: distance beyond the edge of the cut of the nearest valley (tunnel valley lakes end before it).
		double floorGap = Double.POSITIVE_INFINITY;
		// K8a: distance beyond the edge of the floor of the nearest valley (without the wall).
		double floorEdgeGap = Double.POSITIVE_INFINITY;

		for (Segment s : segments) {
			if (cull && !inFrame(s, x, z)) {
				continue;
			}
			if (edge != edge) {
				edge = 1 + EDGE_AMPLITUDE * edgeNoise(x, z);
			}
			projectChannel(s, x, z, sc, pr);
			double t = pr[0];
			double d = pr[1];
			double w = s.widthAt(t);
			double level = s.levelAt(t);
			double sl = s.gradient;
			sc.addFloorCandidate(pr[5] - 0.5 * w, w, level, s.order, sl);
			// The d field from the continuous distance (pr[5]); the terrain still from pr[1], as in M1.
			if (pr[5] - 0.5 * w < nearDist) {
				nearDist = pr[5] - 0.5 * w;
				nearSeg = s;
				nearWidth = w;
				nearLevel = level;
				nearT = pr[6];
				nearLat = pr[7];
			}
			double base = (fpFactor * w + fpBase) * edge;
			double floorHalf = w / 2 + floorMargin(s, t, base);
			// G3: the floor of the terrain with the mouth funnel (the fields use floorHalf without it, funnelWidening).
			double terrainHalf = floorHalf + funnelWidening(s, t, base);
			// The valley floor is measured from the valley axis (without meanders), so it always contains the channel; in the lowlands
			// it covers the whole meander belt on both sides (G2: continuous across the node; G4: with the irregular edge;
			// A5: narrowed at the head, floorBelt).
			double armFd = pr[2] - w / 2 - floorBelt(s, t, beltK, edge, base);
			double floorDist = Math.max(0, armFd);
			double fromSource = t * s.len;
			double fade = s.headFade > 0 ? Noise.smoothstep(0, s.headFade, fromSource) : 1.0;
			// Continuous valley floor (without water level steps), always at least 1.2 m above the water.
			double floorOffset = 1.2 + 1.0 * (0.5 + 0.5 * noise.at(x, z, 90 * valleyScale));
			double floor = level + floorOffset;
			// Valley: the floor, and beyond it a side of limited steepness that blends smoothly into the relief.
			double wall = Math.clamp((terrain - floor) / maxSlope, 20 * valleyScale, maxWall());
			double rise = Double.POSITIVE_INFINITY;
			if (s.source) {
				// Valley head: from the source the floor rises upstream at most at half the steepness of the sides,
				// so the valley closes with a rounded funnel rather than a scarp – regardless of the segment length. A5
				// (step K4c): near the head the rise starts on an arc around it (headRise), so the head is an amphitheater,
				// not a floor of full width ending on a straight line across the axis.
				rise = headRise(s, t, pr[2], w / 2 + base);
				floor = Math.max(floor, terrain - 0.5 * maxSlope * rise);
			}
			double mask = 1 - Noise.smoothstep(terrainHalf, terrainHalf + wall, floorDist);
			floorGap = Math.min(floorGap, floorDist - terrainHalf - 0.5 * wall);
			floorEdgeGap = Math.min(floorEdgeGap, floorDist - terrainHalf);
			double own = mask > 0 ? Noise.lerp(mask, terrain, floor) : terrain;
			// K4b/K4c (D4, D4a): the cut is at least the sweep cut minus SWEEP_TOLERANCE (deeper than the cut of the
			// projection only near ties of its arms, where the projection is ill-conditioned). A fill (floor above the terrain,
			// cut < 0) is lowered only by what the sweep cut exceeds 0, so the deepening is continuous there too.
			// The sweep cut matters only when it brings own below result + SMOOTH_MIN_RADIUS (G5 below takes the plain
			// minimum otherwise, and the result only falls with further segments), so a smaller one is skipped (the
			// terrain, and the cascade test of the channels, are the same either way).
			double armsCut = Math.max(0, terrain - own);
			double limit = Math.max(armsCut, terrain - result - SMOOTH_MIN_RADIUS) + SWEEP_TOLERANCE;
			// The sweep cut provably not deeper than a single arm next to it (see sweepCut, armFd).
			boolean single = prune && sc.arms == 1 && s.level1 <= s.level0 && s.width1 >= s.width0;
			double deeper = sweep ? sweepCut(s, x, z, terrain, floorOffset, fpFactor, fpBase, edge, beltK, maxSlope, pr[8],
					single ? armFd : Double.NaN, rise, limit, sc) - SWEEP_TOLERANCE - armsCut : 0;
			if (deeper > 0) {
				own -= deeper;
			}
			// G5: rounded junctions of the sides of two valleys (spurs, mouths) instead of a sharp crease; the ordinary
			// minimum on the floors of both valleys and outside them (radius 0 where either mask is 0 or both are 1), so
			// a segment present only in part of the tile lists cannot change the result. It lowers by at most a quarter of
			// the radius: never below 0.3 m above this channel's water level and above the sea (at the coast the floor
			// went below 0 m next to sea water).
			double ka = SMOOTH_MIN_RADIUS * Math.min(1.0, 2 * Math.min(mask, maskAcc) * (1 - mask * maskAcc));
			if (ka > 0) {
				ka = Math.min(ka, 4 * Math.max(0, Math.min(result, own) - (Math.max(level, 0) + 0.3)));
			}
			result = smoothMin(result, own, ka);
			maskAcc = Math.max(maskAcc, mask);
			// F1. A dry valley head (fade ≤ 0.5) never wins against a floor (f1Key). Beyond floorHalf + 200 m·k the
			// valley weight of the segment is 0, so the maximum and the minimum skip it (the same result, cheaper).
			double key = f1Key(floorHalf, floorDist, fade, valleyScale);
			// G3 (step K4c): u and the floor flag are measured on the floor of the terrain, with the mouth funnel, so the
			// funnel is valley floor for the habitat fields too (it is flat terrain a few meters above the streams); the key
			// of the dominant valley stays without the funnel, so the funnel of a tributary never takes the floor of the
			// valley it joins (inFloor below). The valley weight stays without the funnel (round 1 of the review of K4c):
			// it decides terrain (kettle ponds and tunnel valley lakes only away from valleys, LandscapeModel), and with
			// the funnel it removed 11 kettles at realistic scale and 25 at gameplay scale.
			if (floorDist < terrainHalf + 200 * valleyScale) {
				if (floorDist < floorHalf + 200 * valleyScale) {
					vwMax = Math.max(vwMax,
							(1 - Noise.smoothstep(floorHalf, floorHalf + 200 * valleyScale, floorDist)) * fade);
				}
				if (floorDist < terrainHalf && fade > 0.5) {
					uMin = Math.min(uMin, floorDist / terrainHalf);
					inFunnel |= floorDist >= floorHalf;
				}
			}
			// Soft maximum only within softBand of the deepest valley so far (further weights < 3.4e-4). When a new
			// maximum appears, the sums are rescaled to it (or dropped when it is more than softBand higher).
			if (key > bestKey - softBand) {
				if (key > bestKey) {
					double r = sumW > 0 && key < bestKey + softBand ? Math.exp((bestKey - key) / tau) : 0;
					sumW *= r;
					sumFh *= r;
					sumSl *= r;
				}
				double wgt = key >= bestKey ? 1.0 : Math.exp((key - bestKey) / tau);
				sumW += wgt;
				sumFh += wgt * terrainHalf;
				sumSl += wgt * sl;
			}
			// Ties: the wider floor, then the earlier segment of the tile list (fixed order, see candidates).
			if (key > bestKey || key == bestKey && floorHalf > bestFloorHalf) {
				key3 = key2;
				key2 = bestKey;
				seg2 = best;
				bestKey = key;
				best = s;
				bestFloorHalf = floorHalf;
				bestFloorDist = floorDist;
				bestT = pr[3];
				bestLat = pr[4];
				bestFade = fade;
			} else if (key > key2) {
				key3 = key2;
				key2 = key;
				seg2 = s;
			} else if (key > key3) {
				key3 = key;
			}
			// Channel (evaluated after the loop, when the terrain after cutting all valleys is known).
			double half = 0.5 * w * fade;
			if (half > 0.3 && d < half + 12 && channelCount < chHalf.length) {
				chHalf[channelCount] = half;
				chDist[channelCount] = d;
				chLevel[channelCount] = level;
				chDepth[channelCount] = (1.0 + Math.min(7.0, 0.02 * w)) * fade;
				chOwn[channelCount] = own;
				channelCount++;
			}
		}
		// When a watercourse enters the deeper valley of another watercourse, its water level descends along that valley's side
		// (rapids), instead of hanging above its floor between artificially raised banks.
		for (int q = 0; q < channelCount; q++) {
			double level = chLevel[q];
			// The terrain (after cutting all valleys) does not rise safely above this watercourse's water level here.
			boolean cascade = result < level + 1.0 && result < chOwn[q] - 0.5;
			if (cascade) {
				// A cascade is cut deeper than an ordinary channel, because the side of the other valley can be steep.
				level = Math.min(level, Math.max(result - 2.5, 0));
			}
			int lvl = (int) Math.floor(level);
			// A channel only where the valley has already come down close to the water level. Higher up (the valley head near
			// the source) a dry valley remains, and the water emerges where the valley floor reaches the water level.
			if (!cascade && result - lvl > 3.0) {
				continue;
			}
			if (chDist[q] < chHalf[q]) {
				// The bottom is measured from the rounded water level, so every channel column has water.
				double bottom = lvl - 0.3 - chDepth[q] * Math.sqrt(1 - chDist[q] / chHalf[q]);
				if (water == ColumnSample.NO_WATER || lvl < water) {
					water = lvl;
					channelBottom = bottom;
				}
			} else {
				bank = Math.max(bank, lvl + 1.0);
			}
		}

		// Sink lakes: terrain from the lakes of the M1 filter, the habitat ring from all candidates.
		int lakeLevel = ColumnSample.NO_WATER;
		double lakeShore = Double.POSITIVE_INFINITY;
		double lakeDepth = 0;
		long lakeId = 0;
		double lakeRadius = Double.NaN;
		double ringShore = Double.POSITIVE_INFINITY;
		int ringLevel = ColumnSample.NO_WATER;
		long ringId = 0;
		double ringRadius = Double.NaN;
		double tcx = (Math.floor(x / tileSize) + 0.5) * tileSize;
		double tcz = (Math.floor(z / tileSize) + 0.5) * tileSize;
		double ringMax = LAKE_RING * valleyScale;
		double lakeGapMax = LAKE_GAP_MAX * valleyScale;
		double lakeGap = lakeGapMax;
		for (SinkLake lake : c.lakes) {
			boolean terrainLake = nearTile(lake, tcx, tcz);
			if (!terrainLake) {
				// A lake from the habitat ring only. The shore lies at most 1.2 R from the center (|lobes| ≤ 1; here
				// with a margin, 1.3 R), so a column farther than the ring width (and the lake gap) from it cannot change
				// the result: skip the noise.
				double ex = x - lake.x;
				double ez = z - lake.z;
				double reach = Math.max(ringMax, lakeGapMax) + 1.3 * lake.radius;
				if (ex * ex + ez * ez > 1.000001 * reach * reach) {
					continue;
				}
			}
			double shore = sinkLakeShore(lake, x - lake.x, z - lake.z);
			lakeGap = Math.min(lakeGap, shore);
			if (shore < ringShore) {
				ringShore = shore;
				ringLevel = lake.level;
				ringId = lake.seed;
				ringRadius = lake.radius;
			}
			if (shore < lakeShore && terrainLake) {
				lakeShore = shore;
				lakeLevel = lake.level;
				lakeDepth = lake.depth;
				lakeId = lake.seed;
				lakeRadius = lake.radius;
			}
		}
		if (ringShore > ringMax) {
			ringShore = Double.POSITIVE_INFINITY;
			ringLevel = ColumnSample.NO_WATER;
			ringId = 0;
			ringRadius = Double.NaN;
		}

		if (best == null) {
			return new RiverHit(0, result, 0, false, ColumnSample.NO_WATER, 0, bank, false, ColumnSample.NO_WATER, 0,
					lakeLevel, lakeShore, lakeDepth, lakeId, lakeRadius, Double.POSITIVE_INFINITY, Double.NaN,
					Double.NaN, Double.NaN, Double.NaN, Double.NaN, false, Double.POSITIVE_INFINITY,
					ColumnSample.NO_WATER, 0, Double.NaN, ringShore, ringLevel, ringId, ringRadius,
					Double.POSITIVE_INFINITY, Double.NaN, Double.NaN, Double.NaN, floorGap, floorEdgeGap, lakeGap, Double.NaN);
		}
		// In the floor of some valley exactly when in the floor of the dominant one (its key is the largest, so positive),
		// or in a mouth funnel (G3, step K4c): the funnel belongs to the floor of the valley it opens into, whose channel
		// gives the waterside zones there (floorChannelDist), not to an unclassified slope between two floors (review of
		// K4: polygonal patches of alder carr at the confluences of Beskid streams).
		boolean inFloor = bestFloorDist < bestFloorHalf && bestFade > 0.5 || inFunnel;
		double valleyWeight = vwMax;
		int oxbowLevel = ColumnSample.NO_WATER;
		double oxbowDepth = 0;
		// Oxbow lakes only on flat lowlands and away from every channel.
		// Gradient at realistic scale; oxbow lakes occur on lowland rivers with gradients up to about 1.5 ‰.
		double oxbowShore = Double.POSITIVE_INFINITY;
		int oxbowMirror = ColumnSample.NO_WATER;
		long oxbowId = 0;
		double oxbowWidth = Double.NaN;
		// K5.1: the gradient of the soft maximum (continuous across the nodes, F1) instead of the gradient of the dominant
		// segment, which steps at its nodes; the oxbow lakes fade out towards OXBOW_MAX_SLOPE (oxbow).
		double softSlope = sumSl / sumW;
		if (inFloor && water == ColumnSample.NO_WATER && bank == Double.NEGATIVE_INFINITY && lowland > 0.6
				&& softSlope < OXBOW_MAX_SLOPE) {
			// The margin of the dominant valley over its nearest rival that is not its own continuation across a node: an
			// oxbow lake belongs to the meanders of the dominant valley, so it fades out where another valley takes the
			// floor (F1), instead of ending in a straight line there.
			double rival = seg2 != null && continues(best, seg2) ? key3 : key2;
			Oxbow ox = oxbow(best, bestT, bestLat, nearDist, bestFloorHalf - bestFloorDist, lowland, terrain, result,
					bestKey - rival, softSlope, sc.arc);
			if (ox != null) {
				if (ox.inside()) {
					oxbowDepth = ox.depth();
					oxbowLevel = ox.level();
				}
				oxbowShore = ox.shore();
				oxbowMirror = ox.level();
				oxbowId = ox.id();
				oxbowWidth = ox.width();
			}
		}
		// Convex bank only near the channel, in the belt of point bars and willow scrub: d ≤ max(W, 15 m·k), like the reach of willow scrub
		// on the convex bank in the ecology report (costs a few noise samples).
		boolean convex = nearDist <= Math.max(nearWidth, 15 * valleyScale)
				&& nearSeg.convexBank(nearT, nearLat, nearWidth >= 6 * chan);
		// Channel of the dominant valley (F2, habitat zones on its floor): the nearest channel among the segments of the
		// same order that are at least half as wide as the dominant watercourse here, so also the same watercourse
		// across a node. Ties go to the earlier segment of the tile list (fixed order, see candidates).
		double floorChannelDist = Double.POSITIVE_INFINITY;
		double floorChannelWidth = Double.NaN;
		double floorChannelLevel = Double.NaN;
		double floorChannelGradient = Double.NaN;
		double minWidth = 0.5 * best.widthAt(bestT);
		double[] f = sc.floor;
		// Step H (G3): the soft channel level, the levels of the candidates near the nearest one weighted by distance.
		double softRange = Math.min(SOFT_LEVEL_RATIO * Math.max(0, nearDist) + SOFT_LEVEL_BASE * valleyScale,
				SOFT_LEVEL_MAX * valleyScale);
		double softEnd = nearDist + softRange;
		double softInv = 1 / softRange;
		double softSum = 0;
		double softWeight = 0;
		for (int q = 0, end = sc.floorEnd; q < end; q += FLOOR_STRIDE) {
			if (f[q + 3] == best.order && f[q + 1] >= minWidth && f[q] < floorChannelDist) {
				floorChannelDist = f[q];
				floorChannelWidth = f[q + 1];
				floorChannelLevel = f[q + 2];
				floorChannelGradient = f[q + 4] * 1_000;
			}
			if (f[q] < softEnd) {
				double a = (softEnd - f[q]) * softInv;
				double above = result - f[q + 2];
				double wet = above <= SOFT_LEVEL_DRY_FROM ? 1
						: SOFT_LEVEL_DRY_WEIGHT + (1 - SOFT_LEVEL_DRY_WEIGHT) * (1 - Noise.smoothstep(SOFT_LEVEL_DRY_FROM, SOFT_LEVEL_DRY_TO, above));
				double weight = a * a * wet;
				softSum += weight * f[q + 2];
				softWeight += weight;
			}
		}
		double softChannelLevel = softWeight > 0 ? softSum / softWeight : nearLevel;
		return new RiverHit(best.order, result, valleyWeight, inFloor, water, channelBottom, bank,
				best.source && bestT < 0.5, oxbowLevel, oxbowDepth, lakeLevel, lakeShore, lakeDepth, lakeId,
				lakeRadius, nearDist, nearWidth, nearLevel, inFloor ? uMin : Double.NaN,
				sumFh / sumW, sumSl / sumW * 1_000, convex, oxbowShore, oxbowMirror, oxbowId, oxbowWidth, ringShore,
				ringLevel, ringId, ringRadius, floorChannelDist, floorChannelWidth, floorChannelLevel,
				floorChannelGradient, floorGap, floorEdgeGap, lakeGap, softChannelLevel);
	}

	/**
	 * K5.5: distance from the shore of a sink lake at the offset (dx, dz) from its center, negative inside. The shore is
	 * R · (1 + 0.2 · lobes), with lobes a noise sampled along a circle in noise space (a function of the angle: lobe
	 * scales 1.1, 2.3 and, after the review of K5, 4.6, clamped to [−1, 1]); M1 sampled a planar noise with the wavelength
	 * 0.6 R, which made a rosette with nearly straight sides about 1 km long. The amplitude falls to 0 at the center, so
	 * the shore distance stays continuous there. The same function serves the terrain and the habitat ring; the shore
	 * lies within 0.8 R … 1.2 R.
	 */
	private double sinkLakeShore(SinkLake lake, double dx, double dz) {
		double d = Math.sqrt(dx * dx + dz * dz);
		double r = lake.radius;
		double ca = d > 1e-9 ? dx / d : 1;
		double sa = d > 1e-9 ? dz / d : 0;
		double off = 1_000 * noise.unit(lake.seed, 0, 92);
		// Review of K5: a third, smaller harmonic (4.6) bends the sides between the lobes, which stayed nearly straight
		// over up to 1.5 km with two harmonics alone.
		double lobes = 0.75 * noise.sample(off + 1.1 * ca, 1.1 * sa - off) + 0.35 * noise.sample(2.3 * ca - off, off + 2.3 * sa)
				+ 0.2 * noise.sample(4.6 * ca + off, off - 4.6 * sa);
		return d - r * (1 + 0.2 * Math.clamp(lobes, -1.0, 1.0) * Noise.smoothstep(0, 0.5 * r, d));
	}

	/**
	 * Review of K5: the smallest of |p − center| − 1.2 R over the sink lakes of the nodes around the tile of (x, z), a
	 * lower bound of the distance from (x, z) to the water of every sink lake near (its shore lies within 1.2 R of its
	 * center, {@link #sinkLakeShore}). A kettle pond closer than its reach does not exist (LandscapeModel.kettleState).
	 */
	double sinkLakeClearance(double x, double z) {
		double lx = Math.floor(x / tileSize) * tileSize;
		double lz = Math.floor(z / tileSize) * tileSize;
		double[] min = {Double.POSITIVE_INFINITY};
		tileRadiusNodes(lx, lz, (order, i, j) -> {
			SinkLake lake = sinkLake(node(order, i, j));
			if (lake != null) {
				min[0] = Math.min(min[0], Math.hypot(x - lake.x, z - lake.z) - 1.2 * lake.radius);
			}
		});
		return min[0];
	}

	/** Whether the lake passes the M1 candidate filter for the tile centered at (cx, cz); only such lakes change the terrain. */
	private boolean nearTile(SinkLake lake, double cx, double cz) {
		return Math.abs(lake.x - cx) < lake.radius * 1.6 + tileSize && Math.abs(lake.z - cz) < lake.radius * 1.6 + tileSize;
	}

	/**
	 * Oxbow lake in the column or in the 40 m·k ring around it.
	 *
	 * @param inside whether the column lies in the oxbow lake (then {@code depth} and {@code level} carve the terrain)
	 * @param shore  distance from the shore of the oxbow lake, negative inside it
	 * @param width  half-width of the oxbow lake
	 */
	private record Oxbow(boolean inside, double depth, int level, double shore, long id, double width) {
	}

	/**
	 * K5.1: oxbow lake, a cut-off meander loop. Its axis is the arc of a former, more developed loop (a Kinoshita curve
	 * with a larger θ0, from inflection point to inflection point) behind a bend of the present channel; its half-width
	 * grows from zero at the horns to {@code 0.5 W + 3 m·k} in the middle (a crescent), and falls smoothly to zero at the
	 * present channel, at the edge of the valley floor, at the boundary of the lowlands and on low terrain (plugged ends
	 * instead of straight cuts; M1 cut a band of constant width by |u − m/2| &gt; 0.3, vOld · side &lt; 0.35 amp and
	 * θ &lt; 0.8). Every parameter depends only on the number of the bend, so the shape and the water level are constant
	 * across the oxbow lake. The neighboring bends are checked too, because a developed loop reaches beyond half a
	 * period. Returns the oxbow lake in the column or in the 40 m·k ring around it, otherwise null. Called after the loop
	 * over the segments of {@link #query} (no nested query).
	 *
	 * @param nearDist  distance from the bank of the nearest channel (the d field)
	 * @param floorRoom depth of the column in the floor of the dominant valley, floorHalf − floorDist (m)
	 * @param terrain   terrain before the valleys
	 * @param floor     terrain after the valleys at the column (the floor the oxbow lake lies in)
	 * @param margin    F1 key of the dominant valley minus that of its nearest rival (not its continuation across a node)
	 * @param slope     gradient of the soft maximum of the floor (fraction)
	 * @param arc       work buffer of {@link MeanderField#arcDistance} (2 slots)
	 */
	private Oxbow oxbow(Segment s, double t, double lat, double nearDist, double floorRoom, double lowland, double terrain,
			double floor, double margin, double slope, double[] arc) {
		if (s.theta * 1.25 < OXBOW_MIN_THETA) {
			return null;
		}
		double lambda = s.lambda;
		double u = s.meanderU(t);
		double v = (lat - s.wanderAt(t)) / lambda;
		long seed = Noise.key((long) (s.x0 * 7), (long) (s.z0 * 7), 70);
		long m0 = (long) Math.floor(u * 2 + 0.5);
		double ring = 40 * valleyScale;
		double lowFade = Noise.smoothstep(0.6, 0.75, lowland) * (1 - Noise.smoothstep(0.8 * OXBOW_MAX_SLOPE, OXBOW_MAX_SLOPE, slope));
		Oxbow best = null;
		for (long m = m0 - 1; m <= m0 + 1; m++) {
			// Whole loops have about twice the area of the former ring sectors, hence 0.22 instead of 0.3.
			if (noise.unit(seed, m, 71) > OXBOW_CHANCE) {
				continue;
			}
			// Everything from the number of the bend: the place on the segment, the angle, the width, the water level.
			double tc = Math.clamp((m * 0.5 - s.phase) * lambda / s.len, 0.0, 1.0);
			double thC = s.thetaAt(tc);
			if (thC < OXBOW_MIN_THETA) {
				continue;
			}
			// Water level one meter below the river at the bend, so the valley floor around it is always higher. At a mouth
			// (level below 1 m) there is no oxbow lake: it would touch the lagoon with another level.
			int level = (int) (Math.floor(s.levelAt(tc)) - 1);
			if (level < 1) {
				continue;
			}
			double thOld = Math.min(MeanderField.THETA_MAX, thC + 0.5 + 0.5 * noise.unit(seed, m, 75));
			double side = (m & 1) == 1 ? 1 : -1;
			// The loop shifted slightly down or up the valley (migration of the bends).
			double du = u - m * 0.5 - 0.08 * (noise.unit(seed, m, 76) - 0.5);
			double w = s.widthAt(tc);
			double owMax = 0.5 * w + 3 * valleyScale;
			// Moved outwards by the bank belt of the channel (12 m) and the half-width, so small rivers (λ = 11 W) also
			// fit an oxbow lake outside the bank belt.
			double dv = side * v - (12 + owMax) / lambda;
			double pad = (owMax + ring) / lambda;
			MeanderField.ArcBounds bb = MeanderField.arcBounds(thOld);
			if (du < bb.lo() - pad || du > bb.hi() + pad || dv < -pad || dv > bb.top() + pad) {
				continue;
			}
			// A part of the former loop (from 2/3 to the whole), asymmetric.
			double a0 = 0.03 + 0.17 * noise.unit(seed, m, 77);
			double a1 = 0.97 - 0.17 * noise.unit(seed, m, 78);
			MeanderField.arcDistance(du, dv, thOld, a0, a1, arc);
			double d = arc[0] * lambda;
			double a = Math.clamp((arc[1] - a0) / (a1 - a0), 0.0, 1.0);
			double horn = Math.sqrt(Math.sin(Math.PI * a));
			// Plugged ends: at the present channel (the bank belt of 12 m and a little more), at the edge of the floor and
			// of the lowlands.
			double plug = 12.5 + 0.2 * w;
			double ow = owMax * horn * Noise.smoothstep(plug, plug + owMax + 5, nearDist)
					* Noise.smoothstep(0, 0.5 * owMax + 10 * valleyScale, Math.min(floorRoom, margin)) * lowFade
					// Terrain before the valleys low above the water level (coast): the oxbow lake narrows to zero.
					* Noise.smoothstep(level + 1.5, level + 3.5, terrain)
					// Review of K5: the floor at the column must lie about 2.5–4 m above the water level, as on the floor of
					// the dominant valley at its bend (measured: 2.5–4.1 m in REAL). Where the column lies on the lower floor
					// of another valley the water would stand above the ground around it, and on a slope above the floor it
					// would fill a narrow shaft with walls of up to 110 m (GAMEPLAY); the oxbow lake narrows to zero there.
					* Noise.smoothstep(level + OXBOW_FLOOR_MIN, level + OXBOW_FLOOR_MIN + 1, floor)
					* (1 - Noise.smoothstep(level + OXBOW_FLOOR_MAX, level + OXBOW_FLOOR_MAX + 1.3, floor));
			double shore = d - ow;
			if (shore > ring || best != null && shore >= best.shore()) {
				continue;
			}
			boolean inside = shore <= 0 && ow > 0;
			double depth = inside ? (1.0 + 2.0 * noise.unit(seed, m, 73)) * horn * (1 - d / ow) : 0;
			best = new Oxbow(inside, depth, level, shore, Noise.key(seed, m, 74), owMax);
		}
		return best;
	}

	/** K5.1: smallest meander angle θ0 at the bend for an oxbow lake (rad); the former loop has θ0 + 0.5…1.0. */
	static final double OXBOW_MIN_THETA = 0.8;
	/** Review of K5: the floor at an oxbow lake lies at least this far above its water level (m), with a fade of 1 m. */
	static final double OXBOW_FLOOR_MIN = 1.5;
	/** Review of K5: the floor at an oxbow lake lies at most this far above its water level (m), with a fade of 1.3 m. */
	static final double OXBOW_FLOOR_MAX = 4.2;
	/** K5.1: chance that a bend has an oxbow lake. */
	static final double OXBOW_CHANCE = 0.22;
	/** Steepest gradient (fraction, at 1:1 scale) with oxbow lakes, about 1.5 ‰ on lowland rivers; they fade in below it. */
	static final double OXBOW_MAX_SLOPE = 0.0015;

	/** K5.1: whether b is the continuation of a across a node (the same order, an end of one is the start of the other). */
	private static boolean continues(Segment a, Segment b) {
		return a.order == b.order && (a.x1 == b.x0 && a.z1 == b.z0 || b.x1 == a.x0 && b.z1 == a.z0);
	}
}
