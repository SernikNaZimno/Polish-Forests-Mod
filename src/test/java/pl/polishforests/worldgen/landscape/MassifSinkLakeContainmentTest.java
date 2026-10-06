package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.polishforests.worldgen.landscape.RiverNetworkTest.SEED;

import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Review of K7 (docs/m2/poprawka-geometrii.md, K7): rivers at the sink lakes of realistic scale perched above the dry
 * ground next to them and stepping down into the lake. A river that crosses the shore belt of a sink lake keeps the
 * level of its node, while the basin of the lake lowers the ground on both sides of its channel, so the river runs on a
 * raised strip about 3 m wide and drops straight into the lake. M1 had this at ordinary sink lakes (up to 4–7 m); the
 * deep basins below the large massifs of step K2 raise it to 15–24 m. {@link WaterContainmentTest} (sites every 2000 m)
 * and the spots of {@link SurfaceContinuityTest#massifSpotsHaveNoCliffs} did not cover these lakes.
 *
 * <p>Not fixed in the geometry fix (no model change in K7): the limits are the measured state after K7, a regression
 * guard until the model lowers the river bed with the basin inside its reach or ends the river at the basin rim with a
 * graded cascade (M5). Goal: no perched river and no river-to-standing-water step above 1 m.
 */
@Tag("slow")
class MassifSinkLakeContainmentTest {
	/**
	 * {x, z, side (m, 1 m grid), most perched pairs, largest perched height (m), most steps, largest step (m)}: the sink
	 * lakes below the 1719 m, 1698 m, 1723 m and 1659 m massifs, the lake at (213514, -1519439) and an ordinary sink lake
	 * in the Beskids (157239, 1059020) for comparison.
	 */
	static final double[][] LAKES = {
			{263_300, -1_536_950, 2_000, 289, 24, 8, 25}, {146_743, -1_473_317, 1_600, 438, 15, 14, 16},
			{213_514, -1_519_439, 1_600, 325, 16, 7, 17}, {150_900, -1_525_100, 2_200, 236, 10, 4, 11},
			{518_000, -1_404_342, 2_000, 192, 18, 3, 19}, {157_239, 1_059_020, 400, 74, 4, 3, 5}};

	/**
	 * At each lake (1 m grid): pairs of a water column next to dry ground lower than its water level ("perched"), and
	 * pairs of river water next to standing water whose levels differ by more than 1 m ("steps"), within the measured
	 * limits.
	 */
	@Test
	void riversAtMassifSinkLakesStayWithinTheMeasuredState() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		StringBuilder failures = new StringBuilder();
		for (double[] lake : LAKES) {
			double[] r = scan(m, lake[0], lake[1], (int) lake[2]);
			System.out.printf(Locale.ROOT, "[sink lakes] (%.0f, %.0f) %d m: %d water columns, %d perched pairs (largest %.1f m at "
					+ "(%.0f, %.0f)), %d river/standing steps > 1 m (largest %.1f m); limits %d / %.0f m, %d / %.0f m; goal: none%n",
					lake[0], lake[1], (int) lake[2], (long) r[0], (long) r[1], r[2], r[5], r[6], (long) r[3], r[4], (long) lake[3],
					lake[4], (long) lake[5], lake[6]);
			if (r[1] > lake[3] || r[2] > lake[4] || r[3] > lake[5] || r[4] > lake[6]) {
				failures.append(String.format(Locale.ROOT, " (%.0f, %.0f): %d perched up to %.1f m, %d steps up to %.1f m;", lake[0],
						lake[1], (long) r[1], r[2], (long) r[3], r[4]));
			}
		}
		assertTrue(failures.isEmpty(), "rivers at massif sink lakes worse than the measured state:" + failures);
	}

	/** {water columns, perched pairs, largest perched height, steps, largest step, x, z of the largest perched}. */
	static double[] scan(LandscapeModel m, double cx, double cz, int n) {
		int x0 = (int) Math.floor(cx - n / 2.0);
		int z0 = (int) Math.floor(cz - n / 2.0);
		// Per column: water level (Integer.MIN_VALUE when dry), river flag, surface in meters.
		int[][] level = new int[n][];
		boolean[][] river = new boolean[n][];
		double[][] ground = new double[n][];
		IntStream.range(0, n).parallel().forEach(j -> {
			int[] l = new int[n];
			boolean[] rv = new boolean[n];
			double[] g = new double[n];
			for (int i = 0; i < n; i++) {
				ColumnSample s = m.sample(x0 + i, z0 + j);
				l[i] = s.hasWater() ? s.waterLevel() : Integer.MIN_VALUE;
				rv[i] = s.hasWater() && s.waterKind() == WaterKind.RIVER;
				g[i] = s.surfaceMeters();
			}
			level[j] = l;
			river[j] = rv;
			ground[j] = g;
		});
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		long water = 0;
		long perched = 0;
		long steps = 0;
		double worstPerched = 0;
		double worstStep = 0;
		double px = Double.NaN;
		double pz = Double.NaN;
		for (int j = 1; j < n - 1; j++) {
			for (int i = 1; i < n - 1; i++) {
				if (level[j][i] == Integer.MIN_VALUE) {
					continue;
				}
				water++;
				for (int[] d : dirs) {
					int oi = i + d[0];
					int oj = j + d[1];
					if (level[oj][oi] != Integer.MIN_VALUE) {
						boolean cascade = river[j][i] && river[oj][oi];
						boolean flowing = river[j][i] || river[oj][oi];
						double diff = level[j][i] - level[oj][oi];
						if (flowing && !cascade && diff > 1) {
							steps++;
							worstStep = Math.max(worstStep, diff);
						}
					} else {
						double below = level[j][i] - ground[oj][oi];
						if (below > 0) {
							perched++;
							if (below > worstPerched) {
								worstPerched = below;
								px = x0 + i;
								pz = z0 + j;
							}
						}
					}
				}
			}
		}
		return new double[] {water, perched, worstPerched, steps, worstStep, px, pz};
	}
}
