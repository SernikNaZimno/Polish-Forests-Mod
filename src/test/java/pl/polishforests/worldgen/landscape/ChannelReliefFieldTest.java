package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Grid of the height above the channels (step S8c, decision M2-20 variant d2; docs/03-m2-biomy.md §3.4.1): node values
 * from full samples, the same result on the cell and the tile path, in any order and on any thread, and on valley sides
 * a steepness close to the real one where the estimate from the distance to the channel was 3 to 10 times too small.
 */
class ChannelReliefFieldTest {
	private static final long SEED = 20260927L;
	private static final LandscapeScale[] SCALES = {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY};

	/** Sites with valleys: the outwash plain, the moraine plateau and the foothills (both scales). */
	private static List<double[]> sites(LandscapeModel m) {
		List<double[]> out = new ArrayList<>();
		for (LandscapeType type : new LandscapeType[] {LandscapeType.OUTWASH_PLAIN, LandscapeType.MORAINE_PLATEAU, LandscapeType.FOOTHILLS}) {
			double[] site = LandscapePreview.find(m, type, false);
			if (site != null) {
				out.add(site);
			}
		}
		return out;
	}

	/** Deterministic points in a square with a side of 4 km around the site, off the grid nodes. */
	private static double[][] points(double[] site, int n, long salt) {
		double[][] out = new double[n][];
		long h = salt;
		for (int i = 0; i < n; i++) {
			h = h * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
			double x = site[0] + (h >>> 11) % 4_000 - 2_000 + 0.37;
			h = h * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
			double z = site[1] + (h >>> 11) % 4_000 - 2_000 + 0.61;
			out[i] = new double[] {x, z};
		}
		return out;
	}

	/** Height of the top above the soft channel level, as at a node of the grid; NaN without a watercourse. */
	private static double above(LandscapeModel m, double x, double z) {
		ColumnSample s = m.sample(x, z);
		ColumnSample.Waters w = s.waters();
		if (w.streamOrder() <= 0 || Double.isNaN(w.softChannelLevel())) {
			return Double.NaN;
		}
		return (s.hasWater() ? s.waterLevel() : s.surface() - w.floorFine()) - w.softChannelLevel();
	}

	private static double steeper(double v, double a, double b) {
		return Math.max(Double.isNaN(a) ? 0 : Math.abs(v - a), Double.isNaN(b) ? 0 : Math.abs(b - v));
	}

	/**
	 * A point inside cell (cx, cz) at fractions (0.3, 0.7) is the bilinear blend of the four nodes computed from samples, on
	 * a fresh grid (the cell path) and on the model's grid (the tile path).
	 */
	private static void checkCell(LandscapeModel m, long cx, long cz, double g) {
		double fx = 0.3;
		double fz = 0.7;
		double e00 = expectedNode(m, cx, cz, g);
		double e10 = expectedNode(m, cx + 1, cz, g);
		double e01 = expectedNode(m, cx, cz + 1, g);
		double e11 = expectedNode(m, cx + 1, cz + 1, g);
		double expected = (1 - fx) * (1 - fz) * e00 + fx * (1 - fz) * e10 + (1 - fx) * fz * e01 + fx * fz * e11;
		double x = (cx + fx) * g;
		double z = (cz + fz) * g;
		ChannelReliefField fresh = new ChannelReliefField(m, g);
		assertEquals(expected, fresh.gradient(x, z), 1e-6 * Math.max(1, expected), "bilinear, cell path, at " + x + "," + z);
		assertEquals(expected, m.channelReliefGradient(x, z), 1e-6 * Math.max(1, expected), "bilinear, tile path, at " + x + "," + z);
	}

	/** Steepness at node (ix, iz) by the formula of the grid, from samples: 0 without a value at the node. */
	private static double expectedNode(LandscapeModel m, long ix, long iz, double g) {
		double x = ix * g;
		double z = iz * g;
		double v = above(m, x, z);
		if (Double.isNaN(v)) {
			return 0;
		}
		double gx = steeper(v, above(m, x - g, z), above(m, x + g, z));
		double gz = steeper(v, above(m, x, z - g), above(m, x, z + g));
		return (float) (Math.hypot(gx, gz) / g);
	}

	/** At a node the value is the steeper one-sided difference of the samples on each axis, 0 without a watercourse. */
	@Test
	void nodeValuesComeFromSamples() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			ChannelReliefField f = m.channelRelief();
			double g = f.spacing();
			assertEquals(64.0 * scale.local(), g, 0.0, "node spacing");
			int nan = 0;
			int values = 0;
			for (double[] site : sites(m)) {
				for (int i = 0; i < 60; i++) {
					long ix = Math.round(site[0] / g) + i * 7 - 210;
					long iz = Math.round(site[1] / g) - i * 3 + 90;
					double x = ix * g;
					double z = iz * g;
					double v = above(m, x, z);
					double expected = 0;
					if (!Double.isNaN(v)) {
						double gx = steeper(v, above(m, x - g, z), above(m, x + g, z));
						double gz = steeper(v, above(m, x, z - g), above(m, x, z + g));
						expected = (float) (Math.hypot(gx, gz) / g);
						values++;
					} else {
						nan++;
					}
					double got = f.gradient(x, z);
					String at = " at node " + ix + "," + iz + " (" + scale.id() + ")";
					assertEquals(expected, got, 1e-6 * Math.max(1, expected), "steepness" + at);
					assertEquals(got, m.channelReliefGradient(x, z), 0.0, "public accessor" + at);
				}
			}
			System.out.println(String.format(Locale.ROOT, "[channel relief] %s: %d nodes with a value, %d without a watercourse", scale.id(), values, nan));
			assertTrue(values > 50, "too few nodes with a watercourse: " + values);
		}
	}

	/**
	 * At the edge of the reach of the river network (a node with a value next to one without, found along 8 directions from
	 * each site, up to 600 nodes) the nodes follow the formula with the missing neighbors (round 1 of the S8c review: in
	 * REAL the nodes of {@link #nodeValuesComeFromSamples} all have a value, and so does every node along 38 km from the
	 * sites, so this part runs in GAMEPLAY), and a point inside those cells and four cells near each site is the bilinear
	 * blend of its four nodes computed here from samples ({@link #checkCell}).
	 */
	@Test
	void reachEdgeNodesAndInterpolation() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			double g = m.channelRelief().spacing();
			int edges = 0;
			int cells = 0;
			for (double[] site : sites(m)) {
				for (int dir = 0; dir < 8; dir++) {
					int dx = new int[] {1, 1, 0, -1, -1, -1, 0, 1}[dir];
					int dz = new int[] {0, 1, 1, 1, 0, -1, -1, -1}[dir];
					long ix = Math.round(site[0] / g);
					long iz = Math.round(site[1] / g);
					boolean prev = Double.isNaN(above(m, ix * g, iz * g));
					for (int step = 1; step <= 600; step++) {
						long jx = ix + (long) dx * step;
						long jz = iz + (long) dz * step;
						boolean none = Double.isNaN(above(m, jx * g, jz * g));
						if (none == prev) {
							continue;
						}
						edges++;
						// The node with a value, its neighbor without one, and the cells between them.
						long px = jx - dx;
						long pz = jz - dz;
						for (long[] node : new long[][] {{jx, jz}, {px, pz}}) {
							double expected = expectedNode(m, node[0], node[1], g);
							double got = m.channelReliefGradient(node[0] * g, node[1] * g);
							assertEquals(expected, got, 1e-6 * Math.max(1, expected), "reach edge node " + node[0] + "," + node[1] + " (" + scale.id() + ")");
						}
						checkCell(m, Math.min(jx, px), Math.min(jz, pz), g);
						cells++;
						break;
					}
				}
				// Cells near the site too (in REAL every node within tens of kilometers has a watercourse in reach).
				for (int i = 0; i < 4; i++) {
					checkCell(m, Math.round(site[0] / g) + 3 * i - 5, Math.round(site[1] / g) - 2 * i + 3, g);
					cells++;
				}
			}
			System.out.println(String.format(Locale.ROOT, "[channel relief] %s: %d reach edges, %d interpolated cells checked", scale.id(), edges, cells));
			// REAL: the river network reaches every land node along 38 km from the sites (0 found), so only GAMEPLAY has them.
			assertTrue(scale == LandscapeScale.REALISTIC || edges >= 4, "too few edges of the river network reach found: " + edges + " (" + scale.id() + ")");
		}
	}

	/** A grid with 4 slots (constant eviction) queried in parallel in reverse order gives the values of the default grid. */
	@Test
	void resultsDoNotDependOnCacheOrThreads() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			ChannelReliefField full = m.channelRelief();
			ChannelReliefField tiny = new ChannelReliefField(m, full.spacing(), 4);
			double[][] pts = points(sites(m).getFirst(), 3_000, 5);
			double[] ref = new double[pts.length];
			for (int i = 0; i < pts.length; i++) {
				ref[i] = full.gradient(pts[i][0], pts[i][1]);
			}
			double[] par = new double[pts.length];
			IntStream.range(0, pts.length).parallel().forEach(k -> {
				int i = pts.length - 1 - k;
				par[i] = tiny.gradient(pts[i][0], pts[i][1]);
			});
			for (int i = 0; i < pts.length; i++) {
				assertEquals(0, Double.compare(ref[i], par[i]), "point " + pts[i][0] + "," + pts[i][1] + ": " + ref[i] + " vs " + par[i]);
			}
		}
	}

	/** The first query in a tile outside the cache takes the cell path, the second the tile path: bit-identical results. */
	@Test
	void sparseCellMatchesTile() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			ChannelReliefField c = new ChannelReliefField(m, m.channelRelief().spacing());
			double tile = ChannelReliefField.TILE * c.spacing();
			for (double[] site : sites(m)) {
				for (int i = 0; i < 30; i++) {
					double x = site[0] + (3 * i - 45) * tile + (i % 4) * 0.25 * c.spacing() + (i % 3 == 0 ? 0 : 0.37);
					double z = site[1] + (i % 5) * 0.2 * c.spacing() + 0.11 * i;
					double sparse = c.gradient(x, z);
					double tiled = c.gradient(x, z);
					assertEquals(0, Double.compare(sparse, tiled), "cell vs tile at " + x + "," + z + " (" + scale.id() + ")");
					assertEquals(0, Double.compare(tiled, m.channelRelief().gradient(x, z)), "different grid at " + x + "," + z);
				}
			}
		}
	}

	/**
	 * On valley sides (stream in reach, off the water, 2–15 m above the soft channel level, local steepness of the height
	 * above the channels at least 0.03 from central differences of samples 4 m apart) the grid gives at least a third of
	 * the local steepness at 92% (REAL) and 95% (GAMEPLAY) of the points (measured 95.0% and 99.6%; round 1 of the S8c
	 * review raised the limits from 85% and 70%, and GAMEPLAY does not get too small values more often than REAL), the
	 * median ratio lies in 0.6–2.5 and the 90th percentile at most 2.5 (the steeper one-sided difference at the valley
	 * shoulders overestimates; measured REAL 2.14). Before step S8c the estimate from the distance to the channel was at
	 * least 3 times too small at 74% of the edges on moisture ramps (REAL).
	 */
	@Test
	void valleySidesGetTheirSteepness() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			List<Double> ratio = new ArrayList<>();
			for (double[] site : sites(m)) {
				for (double[] p : points(site, 6_000, 17)) {
					double x = p[0];
					double z = p[1];
					ColumnSample s = m.sample(x, z);
					if (s.hasWater()) {
						continue;
					}
					double v = above(m, x, z);
					if (!(v >= 2 && v <= 15)) {
						continue;
					}
					double gx = (above(m, x + 4, z) - above(m, x - 4, z)) / 8;
					double gz = (above(m, x, z + 4) - above(m, x, z - 4)) / 8;
					double local = Math.hypot(gx, gz);
					double grid = m.channelReliefGradient(x, z);
					if (!(local >= 0.03)) {
						continue;
					}
					ratio.add(grid / local);
				}
			}
			int n = ratio.size();
			ratio.sort(null);
			double third = ratio.stream().filter(r -> r >= 1.0 / 3).count() / (double) n;
			double median = ratio.get(n / 2);
			System.out.println(String.format(Locale.ROOT, "[channel relief] %s: %d valley side points, grid/local median %.2f, 10–90%% %.2f–%.2f, at least a third: %.3f",
					scale.id(), n, median, ratio.get(n / 10), ratio.get(9 * n / 10), third));
			boolean real = scale == LandscapeScale.REALISTIC;
			assertTrue(n > 300, "too few valley side points: " + n);
			assertTrue(third >= (real ? 0.92 : 0.95), "grid steepness below a third of the local one too often: " + third + " (" + scale.id() + ")");
			assertTrue(median >= 0.6 && median <= 2.5, "median ratio " + median + " (" + scale.id() + ")");
			double p90 = ratio.get(9 * n / 10);
			assertTrue(p90 <= 2.5, "grid steepness overestimated: 90th percentile of the ratio " + p90 + " (" + scale.id() + ")");
		}
	}
}
