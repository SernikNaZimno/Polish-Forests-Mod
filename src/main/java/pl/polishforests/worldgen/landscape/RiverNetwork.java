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
 * neighbours (by smoothed terrain height). When no neighbour is lower, we look for a lower
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
		 * K4b sweep cut ({@link RiverNetwork#sweepCut}): per node t = k / SWEEP_NODES, k = 0..SWEEP_NODES,
		 * {@link RiverNetwork#SWEEP_STRIDE} values: the curve point relative to (x0, z0) and the unit tangent of the curve
		 * (for t), and the point of the valley axis relative to (x0, z0) (for the distance from the axis); set by
		 * {@link RiverNetwork#build} before the segment is published.
		 */
		float[] sweepNodes;
		/**
		 * K4b sweep cut: the largest |{@link #wanderAt}| on the nodes and inside the edges, and the largest sagitta of the
		 * axis over an edge of its polyline (with a margin), for the bounds of {@link RiverNetwork#sweepCut}.
		 */
		double sweepWanderMax;
		double sweepSagMax;
		/**
		 * K4b sweep cut: per block of {@link RiverNetwork#SWEEP_BLOCK} edges of the axis polyline, the largest distance of
		 * its nodes from the chord of the block (with a margin), for the culling of whole blocks.
		 */
		float[] sweepBlockSag;
		/** K4b sweep cut: bounding box of the nodes of the axis polyline, relative to (x0, z0). */
		double sweepMinX;
		double sweepMaxX;
		double sweepMinZ;
		double sweepMaxZ;
		/** K4b sweep cut: bounds of the speed |P'(t)| of the curve on [0, 1] (3% margin), for the along-valley extrapolation. */
		final double speedMin;
		final double speedMax;

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
			double vMin = Double.MAX_VALUE;
			double vMax = 0;
			for (int q = 0; q <= 64; q++) {
				double t = q / 64.0;
				double vx1 = cx1 + t * (2 * cx2 + 3 * t * cx3);
				double vz1 = cz1 + t * (2 * cz2 + 3 * t * cz3);
				double v = Math.sqrt(vx1 * vx1 + vz1 * vz1);
				vMin = Math.min(vMin, v);
				vMax = Math.max(vMax, v);
			}
			this.speedMin = Math.max(1e-9, 0.97 * vMin);
			this.speedMax = Math.max(1e-9, 1.03 * vMax);
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

	/** K4b sweep cut: number of edges of the polyline of the valley axis of a segment (nodes at t = k / SWEEP_NODES). */
	static final int SWEEP_NODES = 16;
	/**
	 * K4b sweep cut: the cut of a valley is deepened to its sweep cut only where that is deeper by more than this (m), so
	 * that the terrain stays as the projection gives it wherever the projection is well conditioned (the sweep cut of
	 * a straight or gently bent valley is within millimeters of it, or below it between the nodes).
	 */
	static final double SWEEP_TOLERANCE = 0.05;
	/** Values per node of {@link Segment#sweepNodes} (curve point, curve tangent, axis point). */
	static final int SWEEP_STRIDE = 6;
	/** K4b sweep cut: edges of the axis polyline per block culled together ({@link Segment#sweepBlockSag}). */
	static final int SWEEP_BLOCK = 4;

	/**
	 * K4b sweep cut of the valley of s at (x, z), decision D4 (the rest of A2 near the ends of short, bent segments):
	 * the largest cut mask · (terrain − floor) over the cross-sections of the valley along the edges of the polyline of
	 * its axis A(t) = P(t) + wander(t) · normal through the nodes t_k = k / {@link #SWEEP_NODES}. On each edge the
	 * cross-section is measured from the nearest point of the edge (so about the distance from the axis itself; beyond
	 * the ends of the segment the nearest point is the end of the axis, as for the projection), at a t extrapolated from
	 * both nodes of the edge along their curve tangents by
	 * along / speed (the largest speed of the curve downstream of the node and the smallest upstream, so that the floor is
	 * not taken lower than the foot of the perpendicular gives to first order) and blended by the position on the edge.
	 * Everything else (floor level and width, A5, G3, G4, the source head) is the cross-section of {@link #query} at that
	 * t. Each edge gives a continuous function of (x, z) with a bounded gradient (the wall steepness, the stream gradient
	 * and the terrain slope), and the sweep cut is their maximum, so it is continuous by construction, whatever the arms
	 * of {@link #projectChannel} do: where the distance from the curve is nearly the same along a part of it, the soft t
	 * of the projection moves fast between arms with very different floor levels (up to 0.1 of a steep segment, tens of
	 * meters of floor), while the sweep cut takes the deepest of these cross-sections and changes at the wall steepness.
	 * It also measures the side from the axis itself: the projection measures it at the perpendicular foot on the curve,
	 * which is too far where the bends of the axis change fast (the axis passes nearer, by up to tens of meters at
	 * gameplay scale), so there the sweep cut is deeper. A first version with point cross-sections at the nodes (distance
	 * from the node axis point) left a crease at every node where that happens (review of K4, docs).
	 *
	 * <p>Only a cut deeper than {@code limit} matters (see {@link #query}), so the method returns 0 when no cross-section
	 * can exceed it, skipping work by bounds: over the whole segment (the lowest floor, the widest floor and wall, the
	 * distance from the curve minus the largest wander and sagitta, the box of the axis nodes), then block by block of
	 * {@link #SWEEP_BLOCK} edges (the sagitta of the block), then cross-section by cross-section. The bounds only skip
	 * cross-sections that cannot exceed the best one so far, so the result is the same as without them.
	 *
	 * @param floorOffset floor height above the water level at (x, z) ({@code floor − level} in {@link #query})
	 * @param skel        distance from the curve, {@code out[8]} of {@link #projectChannel}
	 * @return the sweep cut when it exceeds {@code limit}, otherwise 0
	 */
	private double sweepCut(Segment s, double x, double z, double terrain, double floorOffset, double fpFactor,
			double fpBase, double edge, double beltK, double maxSlope, double skel, double limit) {
		double minFloor = Math.min(s.level0, s.level1) + floorOffset;
		double depthMax = terrain - minFloor;
		if (depthMax <= limit) {
			return 0;
		}
		double minWall = 20 * valleyScale;
		double maxWall = maxWall();
		double wallMax = Math.clamp(depthMax / maxSlope, minWall, maxWall);
		double wMax = Math.max(s.width0, s.width1);
		double beltMax = Math.max(s.amp, s.ampEnd) * beltK * edge;
		// Upper bound of the half-width of the terrain floor (the G3 and A5 factors are at most 1).
		double halfMax = wMax / 2 + (fpFactor * wMax + fpBase) * edge * (1 + FUNNEL_SHARE) + FUNNEL_BASE * valleyScale;
		// Every point of the axis is at least skel − (largest wander) away and the polyline at most the largest sagitta
		// nearer (1 m to spare for the wander sampled between the nodes), so no cross-section can cut more than this.
		double reachMax = halfMax + wallMax;
		double bx = x - s.x0;
		double bz = z - s.z0;
		// The polyline lies in the box of its nodes, so it is at least the distance from that box away.
		double ox = Math.max(0, Math.max(s.sweepMinX - bx, bx - s.sweepMaxX));
		double oz = Math.max(0, Math.max(s.sweepMinZ - bz, bz - s.sweepMaxZ));
		double fdMin = Math.max(skel - s.sweepWanderMax - s.sweepSagMax - 1, Math.sqrt(ox * ox + oz * oz))
				- wMax / 2 - beltMax;
		if (fdMin >= reachMax || (1 - Noise.smoothstep(halfMax, reachMax, fdMin)) * depthMax <= limit) {
			return 0;
		}
		int n = SWEEP_NODES;
		float[] nd = s.sweepNodes;
		float[] blockSag = s.sweepBlockSag;
		double best = limit;
		for (int c = 0; c < blockSag.length; c++) {
			// Blocks of SWEEP_BLOCK edges: the nodes and edges of a block lie within its sagitta of the chord of the block,
			// so a block that cannot cut deeper than best even from that distance is skipped as a whole.
			int k0 = c * SWEEP_BLOCK;
			int k1 = k0 + SWEEP_BLOCK;
			int c0 = SWEEP_STRIDE * k0;
			int c1 = SWEEP_STRIDE * k1;
			double cx = nd[c1 + 4] - nd[c0 + 4];
			double cz = nd[c1 + 5] - nd[c0 + 5];
			double cpx = bx - nd[c0 + 4];
			double cpz = bz - nd[c0 + 5];
			double cl2 = cx * cx + cz * cz;
			double cu = cl2 > 1e-12 ? Math.clamp((cpx * cx + cpz * cz) / cl2, 0.0, 1.0) : 0;
			double cdx = cpx - cu * cx;
			double cdz = cpz - cu * cz;
			double blockFd = Math.sqrt(cdx * cdx + cdz * cdz) - blockSag[c] - wMax / 2 - beltMax;
			if (blockFd >= reachMax || (1 - Noise.smoothstep(halfMax, reachMax, blockFd)) * depthMax <= best) {
				continue;
			}
			double tPrev = 0;
			for (int k = k0; k <= k1; k++) {
				int i = SWEEP_STRIDE * k;
				// t extrapolated from the node along its curve tangent (the largest speed downstream and the smallest
				// upstream, so never further than the foot of the perpendicular gives to first order).
				double along = (bx - nd[i]) * nd[i + 2] + (bz - nd[i + 1]) * nd[i + 3];
				double tk = (double) k / n + along / (along >= 0 ? s.speedMax : s.speedMin);
				if (k < k1 || k == n) {
					// The cross-section at the node, from its axis point (the last node of a block belongs to the next).
					double qx = bx - nd[i + 4];
					double qz = bz - nd[i + 5];
					best = sectionCut(s, Math.sqrt(qx * qx + qz * qz), tk, terrain, floorOffset, fpFactor, fpBase, edge, beltK,
							maxSlope, wMax, beltMax, halfMax, reachMax, depthMax, best);
				}
				if (k > k0) {
					// The cross-section along the edge from the previous node: from the nearest point of the edge, at the t
					// of the two nodes blended by the position on the edge.
					int h = i - SWEEP_STRIDE;
					double ex = nd[i + 4] - nd[h + 4];
					double ez = nd[i + 5] - nd[h + 5];
					double px = bx - nd[h + 4];
					double pz = bz - nd[h + 5];
					double l2 = ex * ex + ez * ez;
					double u = l2 > 1e-12 ? Math.clamp((px * ex + pz * ez) / l2, 0.0, 1.0) : 0;
					if (u > 0 && u < 1) {
						double dx = px - u * ex;
						double dz = pz - u * ez;
						best = sectionCut(s, Math.sqrt(dx * dx + dz * dz), tPrev + u * (tk - tPrev), terrain, floorOffset,
								fpFactor, fpBase, edge, beltK, maxSlope, wMax, beltMax, halfMax, reachMax, depthMax, best);
					}
				}
				tPrev = tk;
			}
		}
		return best > limit ? best : 0;
	}

	/**
	 * K4b sweep cut: the larger of {@code best} and the cut of the cross-section of s at distance {@code va} from the
	 * valley axis and at t (clamped to [0, 1]), as in {@link #query}; the bounds over the segment ({@code halfMax},
	 * {@code reachMax} = halfMax + the widest wall, {@code depthMax} from the lowest floor) skip it early.
	 */
	private double sectionCut(Segment s, double va, double tRaw, double terrain, double floorOffset, double fpFactor,
			double fpBase, double edge, double beltK, double maxSlope, double wMax, double beltMax, double halfMax,
			double reachMax, double depthMax, double best) {
		double fdMin = va - wMax / 2 - beltMax;
		// Bounds over the segment first (no t needed): beyond the reach, or not deeper than best even with the widest
		// floor and wall and the lowest floor.
		if (fdMin >= reachMax || (1 - Noise.smoothstep(halfMax, reachMax, fdMin)) * depthMax <= best) {
			return best;
		}
		double t = Math.clamp(tRaw, 0.0, 1.0);
		double w = s.widthAt(t);
		double floor = s.levelAt(t) + floorOffset;
		double wall = Math.clamp((terrain - floor) / maxSlope, 20 * valleyScale, maxWall());
		if (s.source) {
			floor = Math.max(floor, terrain - 0.5 * maxSlope * t * s.len);
		}
		double depth = terrain - floor;
		// Bound of this cross-section (widest floor and meander belt of the segment) before the exact one.
		if (depth <= best || (1 - Noise.smoothstep(halfMax, halfMax + wall, va - w / 2 - beltMax)) * depth <= best) {
			return best;
		}
		double base = (fpFactor * w + fpBase) * edge;
		double half = w / 2 + floorMargin(s, t, base) + funnelWidening(s, t, base);
		double fd = Math.max(0, va - w / 2 - floorBelt(s, t, beltK, edge, base));
		if (fd < half + wall) {
			return Math.max(best, (1 - Noise.smoothstep(half, half + wall, fd)) * depth);
		}
		return best;
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
	 */
	record RiverHit(int order, double terrain, double valleyWeight, boolean inFloor, int waterLevel,
			double channelBottom, double bankLevel, boolean source, int oxbowLevel, double oxbowDepth, int lakeLevel,
			double lakeShore, double lakeDepth, long lakeId, double lakeRadius, double channelDist,
			double channelWidth, double channelLevel, double floorU, double floorHalf, double slope,
			boolean convexBank, double oxbowShore, int oxbowMirror, long oxbowId, double oxbowWidth, double ringShore,
			int ringLevel, long ringId, double ringRadius, double floorChannelDist, double floorChannelWidth,
			double floorChannelLevel, double floorChannelGradient) {
		boolean inChannel() {
			return waterLevel != ColumnSample.NO_WATER;
		}
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
		// K4b sweep cut: per node the curve point and tangent (for t) and the axis point; the largest sagitta of the axis
		// over an edge (measured at the middle of each edge) and the largest wander, for the bounds.
		float[] nodes = new float[SWEEP_STRIDE * (SWEEP_NODES + 1)];
		double wanderMax = 0;
		double[] a = new double[2];
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
			wanderMax = Math.max(wanderMax, axisPoint(s, t, a));
			nodes[i + 4] = (float) (a[0] - s.x0);
			nodes[i + 5] = (float) (a[1] - s.z0);
		}
		for (int k = 0; k < SWEEP_NODES; k++) {
			int i = SWEEP_STRIDE * k;
			double ax0 = nodes[i + 4] + s.x0;
			double az0 = nodes[i + 5] + s.z0;
			double ex = nodes[i + SWEEP_STRIDE + 4] - nodes[i + 4];
			double ez = nodes[i + SWEEP_STRIDE + 5] - nodes[i + 5];
			double el = Math.max(1e-9, Math.sqrt(ex * ex + ez * ez));
			// The sagitta at the middle of the edge, doubled for the rest of the edge (it only loosens the bounds).
			wanderMax = Math.max(wanderMax, axisPoint(s, (k + 0.5) / SWEEP_NODES, a));
			double sag = Math.abs((a[0] - ax0) * ez - (a[1] - az0) * ex) / el;
			s.sweepSagMax = Math.max(s.sweepSagMax, 2 * sag + 0.01);
		}
		s.sweepNodes = nodes;
		s.sweepWanderMax = wanderMax;
		float[] blockSag = new float[SWEEP_NODES / SWEEP_BLOCK];
		for (int c = 0; c < blockSag.length; c++) {
			int i0 = SWEEP_STRIDE * c * SWEEP_BLOCK;
			int i1 = SWEEP_STRIDE * (c + 1) * SWEEP_BLOCK;
			double ex = nodes[i1 + 4] - nodes[i0 + 4];
			double ez = nodes[i1 + 5] - nodes[i0 + 5];
			double l2 = Math.max(1e-12, ex * ex + ez * ez);
			double sag = 0;
			for (int k = c * SWEEP_BLOCK + 1; k < (c + 1) * SWEEP_BLOCK; k++) {
				double px = nodes[SWEEP_STRIDE * k + 4] - nodes[i0 + 4];
				double pz = nodes[SWEEP_STRIDE * k + 5] - nodes[i0 + 5];
				double u = Math.clamp((px * ex + pz * ez) / l2, 0.0, 1.0);
				double dx = px - u * ex;
				double dz = pz - u * ez;
				sag = Math.max(sag, Math.sqrt(dx * dx + dz * dz));
			}
			blockSag[c] = (float) (sag + 0.01);
		}
		s.sweepBlockSag = blockSag;
		s.sweepMinX = Double.MAX_VALUE;
		s.sweepMaxX = -Double.MAX_VALUE;
		s.sweepMinZ = Double.MAX_VALUE;
		s.sweepMaxZ = -Double.MAX_VALUE;
		for (int k = 0; k <= SWEEP_NODES; k++) {
			s.sweepMinX = Math.min(s.sweepMinX, nodes[SWEEP_STRIDE * k + 4]);
			s.sweepMaxX = Math.max(s.sweepMaxX, nodes[SWEEP_STRIDE * k + 4]);
			s.sweepMinZ = Math.min(s.sweepMinZ, nodes[SWEEP_STRIDE * k + 5]);
			s.sweepMaxZ = Math.max(s.sweepMaxZ, nodes[SWEEP_STRIDE * k + 5]);
		}
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

	/** Point of the valley axis of s at t into {@code out}; returns |wander| there. */
	private static double axisPoint(Segment s, double t, double[] out) {
		double tx = s.dx(t);
		double tz = s.dz(t);
		double tl = Math.max(1e-9, Math.sqrt(tx * tx + tz * tz));
		double w = s.wanderAt(t);
		out[0] = s.px(t) - tz / tl * w;
		out[1] = s.pz(t) + tx / tl * w;
		return Math.abs(w);
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
	 * Position of a point relative to the watercourse, written to {@code out} (9 slots): {t, distance from the channel
	 * (with meanders), distance from the valley axis (with bends, without meanders), t of the nearest arm, lateral
	 * distance from the curve on it, continuous distance from the channel (the d field, {@link MeanderField#distances}), t
	 * and lateral distance of the arm that gives it, distance from the curve (the smallest over the arms)}. The work arrays come from {@code sc} (thread buffer), so
	 * a column query does not allocate them for every segment.
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
		final double[] pr = new double[9];
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
		for (int order = 3; order >= 1; order--) {
			double a = spacing[order];
			long gi = (long) Math.floor((lx + tileSize / 2) / a);
			long gj = (long) Math.floor((lz + tileSize / 2) / a);
			int r = tileRadius(order);
			for (long i = gi - r; i <= gi + r; i++) {
				for (long j = gj - r; j <= gj + r; j++) {
					Segment s = segment(order, i, j);
					if (s != null && !(hx < s.minX || lx > s.maxX || hz < s.minZ || lz > s.maxZ)) {
						c.segments.add(s);
					}
					SinkLake lake = sinkLake(node(order, i, j));
					// The M1 filter (lake.radius * 1.6 + tile) widened by the habitat ring; the terrain uses only
					// the lakes from the M1 filter (nearTile).
					double ring = LAKE_RING * valleyScale;
					if (lake != null && Math.abs(lake.x - (lx + tileSize / 2)) < lake.radius * 1.6 + ring + tileSize
							&& Math.abs(lake.z - (lz + tileSize / 2)) < lake.radius * 1.6 + ring + tileSize) {
						c.lakes.add(lake);
					}
				}
			}
		}
		return c;
	}

	/**
	 * Radius (in grid cells of the order) around the tile center within which {@link #candidates} looks for segments: it
	 * covers the longest segments (a gorge of up to 4 cells) and the full reach of the valley. Shared with
	 * {@link #tileRadiusSegments}, so the test of the culling sees the same segments.
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
		double[] pr = new double[9];
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
		double[] pr = new double[9];
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
		return query(x, z, terrain, lowland, foothills, mountains, c, c.segments, true);
	}

	/**
	 * Every segment of the tile radius of {@link #candidates} at (x, z), without the box of influence (test code only,
	 * {@code segmentCullingIsInvisible}).
	 */
	List<Segment> tileRadiusSegments(double x, double z) {
		List<Segment> all = new ArrayList<>();
		double lx = Math.floor(x / tileSize) * tileSize;
		double lz = Math.floor(z / tileSize) * tileSize;
		for (int order = 3; order >= 1; order--) {
			double a = spacing[order];
			long gi = (long) Math.floor((lx + tileSize / 2) / a);
			long gj = (long) Math.floor((lz + tileSize / 2) / a);
			int r = tileRadius(order);
			for (long i = gi - r; i <= gi + r; i++) {
				for (long j = gj - r; j <= gj + r; j++) {
					Segment s = segment(order, i, j);
					if (s != null) {
						all.add(s);
					}
				}
			}
		}
		return all;
	}

	/**
	 * {@link #query} over the given segments only, without culling them (K4.9, tests): {@code segmentCullingIsInvisible}
	 * passes {@link #tileRadiusSegments}, {@code valleyHeadsAreRounded} a single segment. The sink lakes are those of the
	 * tile, as in {@link #query}. Test code only.
	 */
	RiverHit querySegments(double x, double z, double terrain, double lowland, double foothills, double mountains,
			List<Segment> segments) {
		return query(x, z, terrain, lowland, foothills, mountains, candidates(x, z), segments, false);
	}

	private RiverHit query(double x, double z, double terrain, double lowland, double foothills, double mountains,
			TileCache c, List<Segment> segments, boolean cull) {
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
			double floorDist = Math.max(0, pr[2] - w / 2 - floorBelt(s, t, beltK, edge, base));
			double fromSource = t * s.len;
			double fade = s.headFade > 0 ? Noise.smoothstep(0, s.headFade, fromSource) : 1.0;
			// Continuous valley floor (without water level steps), always at least 1.2 m above the water.
			double floorOffset = 1.2 + 1.0 * (0.5 + 0.5 * noise.at(x, z, 90 * valleyScale));
			double floor = level + floorOffset;
			// Valley: the floor, and beyond it a side of limited steepness that blends smoothly into the relief.
			double wall = Math.clamp((terrain - floor) / maxSlope, 20 * valleyScale, maxWall());
			if (s.source) {
				// Valley head: from the source the floor rises upstream at most at half the steepness of the sides,
				// so the valley closes with a rounded funnel rather than a scarp – regardless of the segment length.
				floor = Math.max(floor, terrain - 0.5 * maxSlope * fromSource);
			}
			double mask = 1 - Noise.smoothstep(terrainHalf, terrainHalf + wall, floorDist);
			double own = mask > 0 ? Noise.lerp(mask, terrain, floor) : terrain;
			// K4b (D4): the cut is at least the sweep cut minus SWEEP_TOLERANCE (continuous by construction; deeper than
			// the cut of the projection only where the projection is ill-conditioned). A fill (floor above the terrain,
			// cut < 0) is lowered only by what the sweep cut exceeds 0, so the deepening is continuous there too.
			// The sweep cut matters only when it brings own below result + SMOOTH_MIN_RADIUS (G5 below takes the plain
			// minimum otherwise, and the result only falls with further segments), so a smaller one is skipped (the
			// terrain, and the cascade test of the channels, are the same either way).
			double armsCut = Math.max(0, terrain - own);
			double limit = Math.max(armsCut, terrain - result - SMOOTH_MIN_RADIUS) + SWEEP_TOLERANCE;
			double deeper = sweepCut(s, x, z, terrain, floorOffset, fpFactor, fpBase, edge, beltK, maxSlope, pr[8], limit)
					- SWEEP_TOLERANCE - armsCut;
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
			if (floorDist < floorHalf + 200 * valleyScale) {
				vwMax = Math.max(vwMax,
						(1 - Noise.smoothstep(floorHalf, floorHalf + 200 * valleyScale, floorDist)) * fade);
				if (floorDist < floorHalf && fade > 0.5) {
					uMin = Math.min(uMin, floorDist / floorHalf);
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
				sumFh += wgt * floorHalf;
				sumSl += wgt * sl;
			}
			// Ties: the wider floor, then the earlier segment of the tile list (fixed order, see candidates).
			if (key > bestKey || key == bestKey && floorHalf > bestFloorHalf) {
				bestKey = key;
				best = s;
				bestFloorHalf = floorHalf;
				bestFloorDist = floorDist;
				bestT = pr[3];
				bestLat = pr[4];
				bestFade = fade;
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
		for (SinkLake lake : c.lakes) {
			boolean terrainLake = nearTile(lake, tcx, tcz);
			if (!terrainLake) {
				// A lake from the habitat ring only. The shore lies at most 1.2 R from the center (|noise| ≤ 1; here
				// with a margin, 1.3 R), so a column farther than the ring width from it cannot change the result: skip the noise.
				double ex = x - lake.x;
				double ez = z - lake.z;
				double reach = ringMax + 1.3 * lake.radius;
				if (ex * ex + ez * ez > 1.000001 * reach * reach) {
					continue;
				}
			}
			double ldx = x - lake.x;
			double ldz = z - lake.z;
			double dist = Math.sqrt(ldx * ldx + ldz * ldz);
			double shore = dist - lake.radius * (1 + 0.2 * noise.at(x, z, Math.max(40, lake.radius * 0.6)));
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
					Double.POSITIVE_INFINITY, Double.NaN, Double.NaN, Double.NaN);
		}
		// In the floor of some valley exactly when in the floor of the dominant one (its key is the largest, so positive).
		boolean inFloor = bestFloorDist < bestFloorHalf && bestFade > 0.5;
		double valleyWeight = vwMax;
		int oxbowLevel = ColumnSample.NO_WATER;
		double oxbowDepth = 0;
		// Oxbow lakes only on flat lowlands and away from every channel.
		// Gradient at realistic scale; oxbow lakes occur on lowland rivers with gradients up to about 1.5 ‰.
		double bestSlope = best.gradient;
		double oxbowShore = Double.POSITIVE_INFINITY;
		int oxbowMirror = ColumnSample.NO_WATER;
		long oxbowId = 0;
		double oxbowWidth = Double.NaN;
		if (inFloor && water == ColumnSample.NO_WATER && bank == Double.NEGATIVE_INFINITY && lowland > 0.6
				&& bestSlope < 0.0015) {
			Oxbow ox = oxbow(best, bestT, bestLat);
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
		for (int q = 0, end = sc.floorEnd; q < end; q += FLOOR_STRIDE) {
			if (f[q + 3] == best.order && f[q + 1] >= minWidth && f[q] < floorChannelDist) {
				floorChannelDist = f[q];
				floorChannelWidth = f[q + 1];
				floorChannelLevel = f[q + 2];
				floorChannelGradient = f[q + 4] * 1_000;
			}
		}
		return new RiverHit(best.order, result, valleyWeight, inFloor, water, channelBottom, bank,
				best.source && bestT < 0.5, oxbowLevel, oxbowDepth, lakeLevel, lakeShore, lakeDepth, lakeId,
				lakeRadius, nearDist, nearWidth, nearLevel, inFloor ? uMin : Double.NaN,
				sumFh / sumW, sumSl / sumW * 1_000, convex, oxbowShore, oxbowMirror, oxbowId, oxbowWidth, ringShore,
				ringLevel, ringId, ringRadius, floorChannelDist, floorChannelWidth, floorChannelLevel,
				floorChannelGradient);
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
	 * Oxbow lake: a cut-off meander loop – a crescent behind a bend of the present channel, on its outer
	 * side. Returns the oxbow lake in the column or in the 40 m·k ring around it, otherwise null.
	 * The water level is constant for the whole oxbow lake.
	 */
	private Oxbow oxbow(Segment s, double t, double lat) {
		double theta = s.thetaAt(t);
		if (theta < 0.8) {
			return null;
		}
		double w = s.widthAt(t);
		double lambda = s.lambda;
		double u = s.meanderU(t);
		double v = (lat - s.wanderAt(t)) / lambda;
		double amp = MeanderField.amplitude(theta);
		// Half a period = one bend; the bends alternate on both sides of the valley axis.
		long m = (long) Math.floor(u * 2 + 0.5);
		double side = (m & 1) == 1 ? 1 : -1;
		long seed = Noise.key((long) (s.x0 * 7), (long) (s.z0 * 7), 70);
		if (noise.unit(seed, m, 71) > 0.3 || Math.abs(u - m * 0.5) > 0.3) {
			return null;
		}
		double shift = 0.9 * amp + 1.5 * w / lambda + 0.05;
		double vOld = v - side * shift;
		if (vOld * side < 0.35 * amp) {
			return null;
		}
		double d = MeanderField.distance(u, vOld, theta) * lambda;
		double ow = 0.45 * w + 3;
		if (d > ow + 40 * valleyScale) {
			return null;
		}
		boolean inside = d <= ow;
		double depth = inside ? (1.0 + 2.0 * noise.unit(seed, m, 73)) * (1 - d / ow) : 0;
		// Water level one metre below the river at the bend, so the valley floor around it is always higher. Computed only
		// from the bend number, so it is constant across the whole oxbow lake.
		double tc = Math.clamp((m * 0.5 - s.phase) * lambda / s.len, 0.0, 1.0);
		return new Oxbow(inside, depth, (int) (Math.floor(s.levelAt(tc)) - 1), d - ow, Noise.key(seed, m, 74), ow);
	}
}
