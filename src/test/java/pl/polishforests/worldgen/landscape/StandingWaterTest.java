package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Standing waters after step K5 (docs/m2/poprawka-geometrii.md, design §3.2): the shape and the containment of the
 * oxbow lakes, one level per tunnel valley lake. A separate
 * class, so the long classes {@link RiverNetworkTest} and {@link LandscapeModelTest} do not grow.
 */
@Tag("slow")
class StandingWaterTest {
	static final long SEED = 20260927L;
	/** Oxbow lakes measured per scale (the first by id, so the choice does not depend on threads). */
	static final int OXBOWS = 25;
	/** Share of the arc (geodesic length of the water body) counted as a horn at each end. */
	static final double HORN = 0.1;
	/**
	 * Largest half-width in the horns as a share of the largest half-width of the oxbow lake. The half-width falls from
	 * the middle to the horns as sqrt(sin(π a)) (a ∈ [0, 1] along the arc), i.e. to 0.56 at a = 0.1; the plugs at the
	 * channel and at the floor edge narrow it further.
	 */
	static final double HORN_SHARE = 0.7;
	/** Largest median of the horn share over the oxbow lakes measured. */
	static final double HORN_MEDIAN = 0.6;
	/** Least share of the oxbow lakes with the horn share at most {@link #HORN_SHARE}. */
	static final double HORN_NARROW = 0.75;
	/**
	 * Deepest position of the edge of a water body inside its shoreline, in grid cells ({@code -s / step} of the water
	 * cells next to a cell outside it; the shore distance changes by at most about 1.5 per cell).
	 */
	static final double BOUNDARY_SHORE = 2;
	/** Highest dry ground (m above the water level) 3 m from an oxbow water column. */
	static final double SHAFT_HEIGHT = 10;
	/** Least median geodesic length of the oxbow lakes in their largest half-widths. */
	static final double MIN_LENGTH = 6;

	/**
	 * K5.1: oxbow lakes are crescents on the arc of a former loop. M1 cut a band of constant width with straight lines
	 * (|u − m/2| &gt; 0.3 and the side of the bend), so its water ended deep inside its shoreline. For each oxbow lake
	 * found on a coarse grid (realistic scale ±60 km every 60 m, gameplay scale ±8 km every 10 m) its water body is
	 * filled on a fine grid (a fifth of its half-width, at least 0.5 m):
	 * <ul>
	 * <li>one water level;</li>
	 * <li>the water ends only at its shoreline: every water cell next to a cell outside it at most
	 * {@value #BOUNDARY_SHORE} cells inside the shore (the oxbow lakes of K4c, i.e. of M1, ended 4.4–5.0 cells inside it
	 * in all 25 at realistic scale);</li>
	 * <li>elongated: median geodesic length at least {@value #MIN_LENGTH} largest half-widths (a piece cut off by a plug
	 * can be short);</li>
	 * <li>narrowing towards the horns: the horn share (the largest half-width, the distance from the nearest column
	 * without its water, in the {@value #HORN} of the length at either end, over the largest half-width) at most
	 * {@value #HORN_MEDIAN} in the median and at most {@value #HORN_SHARE} in {@value #HORN_NARROW} of the oxbow
	 * lakes.</li>
	 * </ul>
	 * Review of K5: every oxbow water column of the coarse grid is contained and lies on the valley floor: none of its 8
	 * neighbors at 1 m is dry ground below the water level, and none at 3 m is dry ground more than
	 * {@value #SHAFT_HEIGHT} m above it (the first version of K5: 62 of 644 oxbow lakes at gameplay scale stood above the
	 * floor of another valley or filled shafts on a slope, with walls of up to 110 m).
	 *
	 * <p>The design asked for 0.3 of the width at 10% of the arc in every oxbow lake, which the crescent profile
	 * sqrt(sin(π a)) of K5.1 itself does not give (0.56), and a water body cut by a plug at the channel (the width falls
	 * to 0 over about one half-width) ends in a short taper (shares up to 0.94). The horn shares alone hardly separate the
	 * old bands (median 0.60 at realistic scale), the shoreline criterion does.
	 */
	@Test
	void oxbowLakesAreCrescents() {
		record Area(LandscapeScale scale, double half, double step) {
		}
		for (Area a : new Area[] {new Area(LandscapeScale.REALISTIC, 60_000, 60), new Area(LandscapeScale.GAMEPLAY, 8_000, 10)}) {
			LandscapeModel m = new LandscapeModel(SEED, a.scale(), 1.0);
			int n = (int) Math.round(2 * a.half() / a.step()) + 1;
			Map<Long, double[]> found = new ConcurrentHashMap<>();
			List<String> uncontained = java.util.Collections.synchronizedList(new ArrayList<>());
			IntStream.range(0, n).parallel().forEach(j -> {
				for (int i = 0; i < n; i++) {
					double x = -a.half() + i * a.step();
					double z = -a.half() + j * a.step();
					ColumnSample s = m.sample(x, z);
					if (s.waterKind() == WaterKind.OXBOW) {
						int level = s.waterLevel();
						for (int q = 0; q < 8; q++) {
							double ca = Math.cos(q * Math.PI / 4);
							double sa = Math.sin(q * Math.PI / 4);
							ColumnSample near = m.sample(x + ca, z + sa);
							ColumnSample far = m.sample(x + 3 * ca, z + 3 * sa);
							if (!near.hasWater() && near.surfaceMeters() < level || !far.hasWater() && far.surface() > level + SHAFT_HEIGHT) {
								uncontained.add(String.format(Locale.ROOT, "(%.0f, %.0f) level %d: ground %.2f at 1 m, %.2f at 3 m", x, z,
										level, near.surface(), far.surface()));
								break;
							}
						}
						found.merge(s.waters().lakeId(), new double[] {x, z, s.waters().standingWaterRadius()},
								(p, q) -> p[0] < q[0] || p[0] == q[0] && p[1] <= q[1] ? p : q);
					}
				}
			});
			List<Long> ids = new ArrayList<>(found.keySet());
			ids.sort(Long::compare);
			List<Long> chosen = ids.subList(0, Math.min(OXBOWS, ids.size()));
			List<String> bad = new ArrayList<>();
			List<Double> shares = new ArrayList<>();
			List<Double> lengths = new ArrayList<>();
			for (long id : chosen) {
				double[] r = measure(m, id, found.get(id));
				if (r == null) {
					continue;
				}
				shares.add(r[0]);
				lengths.add(r[1]);
				if (r[2] > 0 || r[3] < -BOUNDARY_SHORE) {
					bad.add(String.format(Locale.ROOT, "(%.1f, %.1f): levels %d, edge %.1f cells inside the shore", found.get(id)[0],
							found.get(id)[1], (long) r[2] + 1, -r[3]));
				}
			}
			shares.sort(Double::compare);
			lengths.sort(Double::compare);
			double medianLength = lengths.isEmpty() ? Double.NaN : lengths.get(lengths.size() / 2);
			int measured = shares.size();
			double median = measured == 0 ? Double.NaN : shares.get(measured / 2);
			long narrow = shares.stream().filter(v -> v <= HORN_SHARE).count();
			System.out.printf(Locale.ROOT, "[oxbows] %s: %d oxbow lakes found, %d measured; horn half-width / largest: median %.2f "
					+ "(limit %.2f), at most %.2f in %d (limit %.0f%%), all %s; length in half-widths: median %.1f (limit %.1f), "
					+ "shortest %.1f; more than one level or cut: %s%n",
					a.scale().id(), ids.size(), measured, median, HORN_MEDIAN, HORN_SHARE, narrow, 100 * HORN_NARROW,
					shares.stream().map(v -> String.format(Locale.ROOT, "%.2f", v)).toList(), medianLength, MIN_LENGTH,
					lengths.isEmpty() ? Double.NaN : lengths.get(0), bad);
			System.out.printf(Locale.ROOT, "[oxbows] %s: oxbow water columns not contained or not on the floor: %d %s%n",
					a.scale().id(), uncontained.size(), uncontained.stream().limit(5).toList());
			assertTrue(uncontained.isEmpty(), a.scale().id() + ": oxbow lakes not contained or off the floor: " + uncontained.size()
					+ ", first " + uncontained.stream().limit(5).toList());
			assertTrue(measured >= 10, a.scale().id() + ": too few oxbow lakes measured: " + measured);
			assertTrue(bad.isEmpty(), a.scale().id() + ": oxbow lakes with more than one level or cut off inside the shore: "
					+ bad);
			assertTrue(medianLength >= MIN_LENGTH, a.scale().id() + ": oxbow lakes not elongated, median length " + medianLength);
			assertTrue(median <= HORN_MEDIAN, a.scale().id() + ": oxbow lakes do not narrow towards the horns, median " + median);
			assertTrue(narrow >= HORN_NARROW * measured, a.scale().id() + ": too few oxbow lakes narrow at the horns: " + narrow
					+ " of " + measured);
		}
	}

	/**
	 * Review of K5 (A1): a tunnel valley lake has one water level, and two tunnel valley lakes never touch with different
	 * levels. The first version of K5 found the lake by Newton steps started at the column, which at gameplay scale beyond
	 * ±8 km still made stripes north to south with different levels (up to 68 m apart, e.g. (−16500, −8820) and
	 * (2180, 17650)). On a grid (gameplay ±20 km every 25 m; realistic ±60 km every 75 m around the moraine window of
	 * {@link SurfaceContinuityTest}) every tunnel valley lake water
	 * column and its neighbors 1 m east and south: one level per lake id, and no neighbor in another tunnel valley lake
	 * with another level.
	 */
	@Test
	void tunnelLakesHaveOneLevel() {
		record Area(LandscapeScale scale, double cx, double cz, double half, double step) {
		}
		for (Area a : new Area[] {new Area(LandscapeScale.GAMEPLAY, 0, 0, 20_000, 25),
				new Area(LandscapeScale.REALISTIC, -66_495, 21_873, 60_000, 75)}) {
			LandscapeModel m = new LandscapeModel(SEED, a.scale(), 1.0);
			int n = (int) Math.round(2 * a.half() / a.step()) + 1;
			Map<Long, Integer> levels = new ConcurrentHashMap<>();
			List<String> bad = java.util.Collections.synchronizedList(new ArrayList<>());
			java.util.concurrent.atomic.AtomicLong columns = new java.util.concurrent.atomic.AtomicLong();
			IntStream.range(0, n).parallel().forEach(j -> {
				for (int i = 0; i < n; i++) {
					double x = a.cx() - a.half() + i * a.step();
					double z = a.cz() - a.half() + j * a.step();
					ColumnSample s = m.sample(x, z);
					if (!isTunnelLake(s)) {
						continue;
					}
					columns.incrementAndGet();
					Integer prev = levels.putIfAbsent(s.waters().lakeId(), s.waterLevel());
					if (prev != null && prev != s.waterLevel()) {
						bad.add(String.format(Locale.ROOT, "(%.0f, %.0f) lake %d: levels %d and %d", x, z, s.waters().lakeId(), prev,
								s.waterLevel()));
					}
					for (double[] d : new double[][] {{1, 0}, {0, 1}}) {
						ColumnSample o = m.sample(x + d[0], z + d[1]);
						if (isTunnelLake(o) && o.waterLevel() != s.waterLevel()) {
							bad.add(String.format(Locale.ROOT, "(%.0f, %.0f): level %d next to %d", x, z, s.waterLevel(), o.waterLevel()));
						}
					}
				}
			});
			System.out.printf(Locale.ROOT, "[tunnel lake levels] %s: %d water columns of %d lakes, failures %d %s%n", a.scale().id(),
					columns.get(), levels.size(), bad.size(), bad.stream().limit(5).toList());
			assertTrue(levels.size() >= 5, a.scale().id() + ": too few tunnel valley lakes: " + levels.size());
			assertTrue(bad.isEmpty(), a.scale().id() + ": tunnel valley lakes with more than one level: " + bad.size() + ", first "
					+ bad.stream().limit(5).toList());
		}
	}

	private static boolean isTunnelLake(ColumnSample s) {
		return s.waterKind() == WaterKind.LAKE
				&& s.waters().standingWaterKind() == ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE;
	}

	/**
	 * The water body of the oxbow lake id containing the point p = {x, z, half-width}, on a fine grid: {largest half-width
	 * in the horns / largest half-width, geodesic length / largest half-width, number of extra water levels}, or null
	 * when it does not fit the grid.
	 */
	private static double[] measure(LandscapeModel m, long id, double[] p) {
		double owMax = Math.max(1, p[2]);
		double step = Math.max(0.5, owMax / 5);
		double half = 30 * owMax + 30;
		int n = (int) Math.round(2 * half / step) + 1;
		double x0 = p[0] - half;
		double z0 = p[1] - half;
		int[] level = new int[n * n];
		boolean[] water = new boolean[n * n];
		double[] shore = new double[n * n];
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				ColumnSample s = m.sample(x0 + i * step, z0 + j * step);
				water[j * n + i] = s.waterKind() == WaterKind.OXBOW && s.waters().lakeId() == id;
				level[j * n + i] = s.waterLevel();
				shore[j * n + i] = s.waters().s();
			}
		});
		// The water body of the seed: the water cell nearest to p, filled over the 4 neighbors.
		int seed = -1;
		double best = Double.POSITIVE_INFINITY;
		for (int k = 0; k < n * n; k++) {
			if (water[k]) {
				double dx = x0 + (k % n) * step - p[0];
				double dz = z0 + (k / n) * step - p[1];
				if (dx * dx + dz * dz < best) {
					best = dx * dx + dz * dz;
					seed = k;
				}
			}
		}
		if (seed < 0) {
			return null;
		}
		boolean[] body = new boolean[n * n];
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		body[seed] = true;
		queue.add(seed);
		List<Integer> cells = new ArrayList<>();
		while (!queue.isEmpty()) {
			int k = queue.poll();
			cells.add(k);
			int i = k % n;
			int j = k / n;
			if (i == 0 || j == 0 || i == n - 1 || j == n - 1) {
				return null;
			}
			for (int q : new int[] {k - 1, k + 1, k - n, k + n}) {
				if (water[q] && !body[q]) {
					body[q] = true;
					queue.add(q);
				}
			}
		}
		long levels = cells.stream().mapToInt(k -> level[k]).distinct().count() - 1;
		// The water body ends at its shoreline: every water cell next to a cell outside the body lies within
		// BOUNDARY_SHORE cells of the shore (a cut gives a boundary deep inside the lake, s far below 0).
		double boundary = 0;
		for (int k : cells) {
			for (int q : new int[] {k - 1, k + 1, k - n, k + n}) {
				if (!body[q]) {
					boundary = Math.min(boundary, shore[k] / step);
				}
			}
		}
		// Half-width: distance (chamfer 1 / sqrt 2) from the nearest cell outside the body.
		double[] dist = new double[n * n];
		Arrays.fill(dist, Double.POSITIVE_INFINITY);
		for (int k = 0; k < n * n; k++) {
			if (!body[k]) {
				dist[k] = 0;
			}
		}
		chamfer(dist, n);
		// Geodesic distances within the body from one end to the other.
		double[] g0 = geodesic(body, n, seed);
		int endA = argmax(g0, cells);
		double[] ga = geodesic(body, n, endA);
		int endB = argmax(ga, cells);
		double[] gb = geodesic(body, n, endB);
		double length = ga[endB];
		double maxR = 0;
		double hornR = 0;
		for (int k : cells) {
			maxR = Math.max(maxR, dist[k]);
			if (Math.min(ga[k], gb[k]) < HORN * length) {
				hornR = Math.max(hornR, dist[k]);
			}
		}
		return new double[] {hornR / maxR, length / maxR, levels, boundary};
	}

	/** Two-pass chamfer distance transform (weights 1 and sqrt 2, in cells) of the zero cells of d. */
	private static void chamfer(double[] d, int n) {
		double s2 = Math.sqrt(2);
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				int k = j * n + i;
				if (i > 0) {
					d[k] = Math.min(d[k], d[k - 1] + 1);
				}
				if (j > 0) {
					d[k] = Math.min(d[k], d[k - n] + 1);
					if (i > 0) {
						d[k] = Math.min(d[k], d[k - n - 1] + s2);
					}
					if (i < n - 1) {
						d[k] = Math.min(d[k], d[k - n + 1] + s2);
					}
				}
			}
		}
		for (int j = n - 1; j >= 0; j--) {
			for (int i = n - 1; i >= 0; i--) {
				int k = j * n + i;
				if (i < n - 1) {
					d[k] = Math.min(d[k], d[k + 1] + 1);
				}
				if (j < n - 1) {
					d[k] = Math.min(d[k], d[k + n] + 1);
					if (i < n - 1) {
						d[k] = Math.min(d[k], d[k + n + 1] + s2);
					}
					if (i > 0) {
						d[k] = Math.min(d[k], d[k + n - 1] + s2);
					}
				}
			}
		}
	}

	/** Geodesic distance (8 neighbors, weights 1 and sqrt 2, in cells) within the body from the cell from (Dijkstra). */
	private static double[] geodesic(boolean[] body, int n, int from) {
		double[] g = new double[n * n];
		Arrays.fill(g, Double.POSITIVE_INFINITY);
		g[from] = 0;
		java.util.PriorityQueue<double[]> pq = new java.util.PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		pq.add(new double[] {0, from});
		double s2 = Math.sqrt(2);
		int[] di = {1, -1, 0, 0, 1, 1, -1, -1};
		int[] dj = {0, 0, 1, -1, 1, -1, 1, -1};
		while (!pq.isEmpty()) {
			double[] e = pq.poll();
			int k = (int) e[1];
			if (e[0] > g[k]) {
				continue;
			}
			int i = k % n;
			int j = k / n;
			for (int q = 0; q < 8; q++) {
				int ii = i + di[q];
				int jj = j + dj[q];
				if (ii < 0 || jj < 0 || ii >= n || jj >= n) {
					continue;
				}
				int kk = jj * n + ii;
				double w = g[k] + (q < 4 ? 1 : s2);
				if (body[kk] && w < g[kk]) {
					g[kk] = w;
					pq.add(new double[] {w, kk});
				}
			}
		}
		return g;
	}

	private static int argmax(double[] g, List<Integer> cells) {
		int best = cells.get(0);
		for (int k : cells) {
			if (g[k] > g[best]) {
				best = k;
			}
		}
		return best;
	}
}
