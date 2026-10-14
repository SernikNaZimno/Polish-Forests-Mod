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
	 * the local steepness at 85% (REAL) and 70% (GAMEPLAY, narrower valleys) of the points; the median ratio lies in
	 * 0.6–2.5. Before step S8c the estimate from the distance to the channel was at least 3 times too small at 74% of the
	 * edges on moisture ramps (REAL).
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
			assertTrue(third >= (real ? 0.85 : 0.70), "grid steepness below a third of the local one too often: " + third + " (" + scale.id() + ")");
			assertTrue(median >= 0.6 && median <= 2.5, "median ratio " + median + " (" + scale.id() + ")");
		}
	}
}
