package pl.polishforests.worldgen.landscape;

import java.util.ArrayList;
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
		/** Meander angle θ0 (rad) of the Kinoshita curve. */
		final double theta;
		final double lambda;
		final double envelope;
		final double phase;
		final double headFade;
		final boolean source;
		final Noise noise;
		double minX;
		double maxX;
		double minZ;
		double maxZ;
		/** Largest deviation of the curve from the chord, and the reach of influence (for fast rejection). */
		double chordDeviation;
		double reach;

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
			this.lambda = lambda;
			this.envelope = Math.min(0.5, 1.5 * lambda / len);
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
			double env = Noise.smoothstep(0, envelope, t) * Noise.smoothstep(0, envelope, 1 - t);
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
			double v = (lat - wanderAt(t)) / lambda;
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

		/** Valley bends without channel meanders: a gentle arc and a few bends at the scale of 1/5 of the segment length. */
		double wanderAt(double t) {
			double s = wander1 * Math.sin(Math.PI * t) + wander2 * Math.sin(2 * Math.PI * t);
			double env = Noise.smoothstep(0, 0.2, t) * Noise.smoothstep(0, 0.2, 1 - t);
			return s + 0.07 * len * env * noise.sample(t * 5 + phase * 0.13, phase * 0.71 - 3.3);
		}

		/** Distance of a point from the segment chord (a lower bound of the distance from the curve after subtracting the deviation). */
		double chordDistance(double x, double z) {
			double vx = x1 - x0;
			double vz = z1 - z0;
			double l2 = vx * vx + vz * vz;
			double t = l2 < 1e-9 ? 0 : Math.clamp(((x - x0) * vx + (z - z0) * vz) / l2, 0.0, 1.0);
			return Math.hypot(x - (x0 + vx * t), z - (z0 + vz * t));
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

		double maxLateral() {
			return Math.abs(wander1) + Math.abs(wander2) + 0.1 * len + 1.1 * amp;
		}
	}

	/**
	 * Query result in a column.
	 *
	 * @param order         order of the watercourse whose valley dominates in the column (0 = none)
	 * @param terrain       terrain after cutting the valleys (not higher than the input terrain)
	 * @param valleyWeight  1 on the valley floor, decreasing on the slopes (to suppress lakes and substrate)
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
	 * @param floorU        position on the valley floor of the dominant watercourse, 0 at the channel, 1 at the edge (NaN off the floor)
	 * @param floorHalf     half-width of the floor of that valley (NaN without a watercourse)
	 * @param slope         gradient of the dominant watercourse in ‰, at 1:1 scale (NaN without a watercourse)
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
	 */
	record RiverHit(int order, double terrain, double valleyWeight, boolean inFloor, int waterLevel,
			double channelBottom, double bankLevel, boolean source, int oxbowLevel, double oxbowDepth, int lakeLevel,
			double lakeShore, double lakeDepth, long lakeId, double lakeRadius, double channelDist,
			double channelWidth, double channelLevel, double floorU, double floorHalf, double slope,
			boolean convexBank, double oxbowShore, int oxbowMirror, long oxbowId, double oxbowWidth, double ringShore,
			int ringLevel, long ringId, double ringRadius) {
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
			Node best = lowestNeighbor(n, 1);
			if (best == null) {
				// Gorge: a lower node in a farther ring.
				for (int ring = 2; ring <= 4 && best == null; ring++) {
					best = lowestNeighbor(n, ring);
				}
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

	/** Lowest node lower than {@code n} on the ring of radius {@code ring} (in grid cells). */
	private Node lowestNeighbor(Node n, int ring) {
		Node best = null;
		for (int di = -ring; di <= ring; di++) {
			for (int dj = -ring; dj <= ring; dj++) {
				if (Math.max(Math.abs(di), Math.abs(dj)) != ring) {
					continue;
				}
				Node m = node(n.order, n.i + di, n.j + dj);
				if (m.route < n.route - 1e-6 && (best == null || m.route < best.route)) {
					best = m;
				}
			}
		}
		return best;
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
	private boolean isSpring(Node n) {
		if (n.sea) {
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
		double[] t1 = l.kind == NODE ? tangent(node(n.order, l.di, l.dj))
				: new double[] {(x1 - n.x) / len, (z1 - n.z) / len};
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
		double reach = s.maxLateral() + floorHalfMax(Math.max(width0, width1)) + maxWall() + Math.max(width0, width1);
		s.chordDeviation = dev;
		s.reach = reach;
		s.minX = minX - reach;
		s.maxX = maxX + reach;
		s.minZ = minZ - reach;
		s.maxZ = maxZ + reach;
		return s;
	}

	private double floorHalfMax(double w) {
		return w / 2 + 5 * w + 40 * valleyScale;
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
	 * Position of a point relative to the watercourse, written to {@code out} (8 slots): {t, distance from the channel
	 * (with meanders), distance from the valley axis (with bends, without meanders), t of the nearest arm, lateral
	 * distance from the curve on it, continuous distance from the channel (the d field, {@link MeanderField#distances}), t
	 * and lateral distance of the arm that gives it}. The work arrays come from {@code sc} (thread buffer), so
	 * a column query does not allocate them for every segment.
	 * <p>
	 * The nearest point of the curve jumps between the arms of a bend when the point lies on the inner
	 * side of the bend. Therefore all local distance minima are checked, and the position along the
	 * watercourse (on which water level, width and valley growth depend) is their soft average
	 * – continuous also where the jump happens. The distances are the minimum over the arms, so they are continuous as well.
	 */
	private static void projectChannel(Segment s, double px, double pz, Scratch sc, double[] out) {
		final int n = 16;
		double[] ds = sc.ds;
		for (int q = 0; q <= n; q++) {
			double t = (double) q / n;
			double ex = s.px(t) - px;
			double ez = s.pz(t) - pz;
			ds[q] = ex * ex + ez * ez;
		}
		double bestSkel = Double.MAX_VALUE;
		double dChannel = Double.MAX_VALUE;
		double dValley = Double.MAX_VALUE;
		double tNear = 0;
		double latNear = 0;
		double dSmooth = Double.MAX_VALUE;
		double tSmooth = 0;
		double latSmooth = 0;
		double[] md = sc.md;
		double[] bt = sc.bt;
		double[] bd = sc.bd;
		double[] bf = sc.bf;
		// As with a fresh array: without any minimum (e.g. NaN) t = 0.
		bt[0] = 0;
		int branches = 0;
		for (int q = 0; q <= n; q++) {
			boolean min = (q == 0 || ds[q] <= ds[q - 1]) && (q == n || ds[q] < ds[q + 1]);
			if (!min) {
				continue;
			}
			double t = refine(s, px, pz, (double) q / n, Math.max(0, q - 1.0) / n, Math.min(n, q + 1.0) / n);
			double tx = s.dx(t);
			double tz = s.dz(t);
			double tl = Math.max(1e-12, Math.hypot(tx, tz));
			double ex = px - s.px(t);
			double ez = pz - s.pz(t);
			double along = (ex * tx + ez * tz) / tl;
			double lat = (tx * ez - tz * ex) / tl;
			double skel;
			double ch;
			double chs;
			double va;
			// Distinctness of the minimum: 1 for a straight segment, 0 where the minimum vanishes (centre of curvature).
			double fold = 1;
			if (t > 0 && t < 1) {
				double g = 1 - (ex * s.ddx(t) + ez * s.ddz(t)) / (tl * tl);
				fold = Noise.smoothstep(0, 0.5, g);
			}
			if ((t <= 0 && along < 0) || (t >= 1 && along > 0)) {
				// Beyond the end of the segment (meanders are faded out here): distance from the end of the axis.
				skel = Math.hypot(lat, along);
				va = Math.hypot(lat - s.wanderAt(t), along);
				ch = va;
				chs = va;
			} else {
				skel = Math.abs(lat);
				va = Math.abs(lat - s.wanderAt(t));
				s.meanderDistances(t, lat, md);
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
			dValley = Math.min(dValley, va);
			bestSkel = Math.min(bestSkel, skel);
			if (branches < bt.length) {
				bt[branches] = t;
				bd[branches] = skel;
				bf[branches] = fold;
				branches++;
			}
		}
		double tSoft = bt[0];
		if (branches > 1) {
			double sigma = 10 + 0.3 * bestSkel;
			double sw = 0;
			double st = 0;
			for (int b = 0; b < branches; b++) {
				double wgt = Math.exp(-(bd[b] - bestSkel) / sigma) * (bf[b] + 1e-3);
				sw += wgt;
				st += wgt * bt[b];
			}
			tSoft = st / sw;
		}
		out[0] = tSoft;
		out[1] = dChannel;
		out[2] = dValley;
		out[3] = tNear;
		out[4] = latNear;
		out[5] = dSmooth;
		out[6] = tSmooth;
		out[7] = latSmooth;
	}

	/**
	 * Work arrays of {@link #projectChannel} and {@link #query} (thread buffer in {@link TileCache}). Every
	 * slot is written before it is read, so stale values do not affect the result.
	 */
	private static final class Scratch {
		final double[] ds = new double[17];
		final double[] md = new double[4];
		final double[] bt = new double[4];
		final double[] bd = new double[4];
		final double[] bf = new double[4];
		final double[] pr = new double[8];
		final double[] chHalf = new double[8];
		final double[] chDist = new double[8];
		final double[] chLevel = new double[8];
		final double[] chDepth = new double[8];
		final double[] chOwn = new double[8];
	}

	/** Newton iteration on the distance from the curve, within [lo, hi]. */
	private static double refine(Segment s, double px, double pz, double t, double lo, double hi) {
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
			t = Math.clamp(t - g / h, lo, hi);
		}
		return t;
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

	/** Candidates (segments and lakes) for a 64 × 64 m tile, cached per thread. */
	private static final class TileCache {
		long key = Long.MIN_VALUE;
		RiverNetwork owner;
		final List<Segment> segments = new ArrayList<>();
		final List<SinkLake> lakes = new ArrayList<>();
		final Scratch scratch = new Scratch();
	}

	private TileCache candidates(double x, double z) {
		long tx = (long) Math.floor(x / tileSize);
		long tz = (long) Math.floor(z / tileSize);
		long k = Noise.key(tx, tz, 7);
		TileCache c = tileCache.get();
		if (c.key == k && c.owner == this) {
			return c;
		}
		c.key = k;
		c.owner = this;
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
			// The radius covers the longest segments (a gorge of up to 4 cells) and the full reach of the valley.
			int r = Math.min(7, (int) Math.ceil((4.5 * a + maxWall() + 600 * valleyScale) / a) + 1);
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
	 * Influence of the watercourses on a column.
	 *
	 * @param terrain   terrain height before cutting the valleys
	 * @param lowland   share of lowland at the point
	 * @param foothills share of foothills
	 * @param mountains share of mountains
	 */
	RiverHit query(double x, double z, double terrain, double lowland, double foothills, double mountains) {
		TileCache c = candidates(x, z);
		double fpFactor = 5.0 * lowland + 1.5 * foothills + 0.3 * mountains;
		double fpBase = (40.0 * lowland + 10.0 * foothills + 2.0 * mountains) * valleyScale;
		// Largest steepness of the valley sides (tangent) before the terrain returns to the original relief.
		double maxSlope = (0.12 * lowland + 0.35 * foothills + 0.7 * mountains) * wallScale;
		maxSlope = Math.max(maxSlope, 0.05);

		double result = terrain;
		double bank = Double.NEGATIVE_INFINITY;
		Segment best = null;
		double bestScore = Double.MAX_VALUE;
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

		for (Segment s : c.segments) {
			if (x < s.minX || x > s.maxX || z < s.minZ || z > s.maxZ
					|| s.chordDistance(x, z) - s.chordDeviation > s.reach) {
				continue;
			}
			projectChannel(s, x, z, sc, pr);
			double t = pr[0];
			double d = pr[1];
			double w = s.widthAt(t);
			double level = s.levelAt(t);
			// The d field from the continuous distance (pr[5]); the terrain still from pr[1], as in M1.
			if (pr[5] - 0.5 * w < nearDist) {
				nearDist = pr[5] - 0.5 * w;
				nearSeg = s;
				nearWidth = w;
				nearLevel = level;
				nearT = pr[6];
				nearLat = pr[7];
			}
			double floorHalf = w / 2 + fpFactor * w + fpBase;
			// The valley floor is measured from the valley axis (without meanders), so it always contains the channel; in the lowlands
			// it covers the whole meander belt on both sides.
			double floorDist = Math.max(0, pr[2] - w / 2 - s.amp * (1 + (lowland > 0.3 ? 1.4 * lowland : 0)));
			double fromSource = t * s.len;
			double fade = s.headFade > 0 ? Noise.smoothstep(0, s.headFade, fromSource) : 1.0;
			// Continuous valley floor (without water level steps), always at least 1.2 m above the water.
			double floor = level + 1.2 + 1.0 * (0.5 + 0.5 * noise.at(x, z, 90 * valleyScale));
			// Valley: the floor, and beyond it a side of limited steepness that blends smoothly into the relief.
			double wall = Math.clamp((terrain - floor) / maxSlope, 20 * valleyScale, maxWall());
			if (s.source) {
				// Valley head: from the source the floor rises upstream at most at half the steepness of the sides,
				// so the valley closes with a rounded funnel rather than a scarp – regardless of the segment length.
				floor = Math.max(floor, terrain - 0.5 * maxSlope * fromSource);
			}
			double mask = 1 - Noise.smoothstep(floorHalf, floorHalf + wall, floorDist);
			double own = mask > 0 ? Noise.lerp(mask, terrain, floor) : terrain;
			result = Math.min(result, own);
			double score = floorDist / Math.max(1.0, floorHalf);
			if (score < bestScore) {
				bestScore = score;
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
				// A lake from the habitat ring only. The shore lies at most 1.2 R from the centre (|noise| ≤ 1; here
				// with a margin, 1.3 R), so a column farther than the ring width from it cannot change the result: skip the noise.
				double ex = x - lake.x;
				double ez = z - lake.z;
				double reach = ringMax + 1.3 * lake.radius;
				if (ex * ex + ez * ez > 1.000001 * reach * reach) {
					continue;
				}
			}
			double dist = Math.hypot(x - lake.x, z - lake.z);
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
					ColumnSample.NO_WATER, 0, Double.NaN, ringShore, ringLevel, ringId, ringRadius);
		}
		boolean inFloor = bestFloorDist < bestFloorHalf && bestFade > 0.5;
		double valleyWeight = (1 - Noise.smoothstep(bestFloorHalf, bestFloorHalf + 200 * valleyScale, bestFloorDist))
				* bestFade;
		int oxbowLevel = ColumnSample.NO_WATER;
		double oxbowDepth = 0;
		// Oxbow lakes only on flat lowlands and away from every channel.
		// Gradient at realistic scale; oxbow lakes occur on lowland rivers with gradients up to about 1.5 ‰.
		double bestSlope = Math.max(0, best.level0 - best.level1) / best.len * spacing[best.order]
				/ BASE_SPACING[best.order];
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
		return new RiverHit(best.order, result, valleyWeight, inFloor, water, channelBottom, bank,
				best.source && bestT < 0.5, oxbowLevel, oxbowDepth, lakeLevel, lakeShore, lakeDepth, lakeId,
				lakeRadius, nearDist, nearWidth, nearLevel, inFloor ? bestFloorDist / bestFloorHalf : Double.NaN,
				bestFloorHalf, bestSlope * 1_000, convex, oxbowShore, oxbowMirror, oxbowId, oxbowWidth, ringShore,
				ringLevel, ringId, ringRadius);
	}

	/** Whether the lake passes the M1 candidate filter for the tile centred at (cx, cz); only such lakes change the terrain. */
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
