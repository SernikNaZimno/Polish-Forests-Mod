package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.polishforests.worldgen.chunk.VerticalScale;

/**
 * Lagoon floors and river deltas (step K8b1 and round 1 of its review, docs/m2/poprawka-geometrii.md, K8b1). On 1 m grids
 * around river mouths in lagoons, at both scales:
 * <ul>
 * <li>no step of {@value #FLOOR_STEP_LIMIT} blocks or more between two neighboring columns of lagoon water (the floor
 * seen through the shallow water). The first deltas of K8b1 measured the distance from the shore on a sampled ray and
 * took their size from the valley floor of the column: flat tongues ending in a break, jagged edges and needles of one
 * column with steps of 3 blocks (review of K8b1). Steps of 2 blocks remain at the inner shore of the GAMEPLAY basins,
 * as before K8b1 (base 47140e0: up to 105 pairs in a window of 1.5 km);</li>
 * <li>there is delta land in the windows at river mouths (dry in {@code sample}, lagoon water in
 * {@code landElevation}), so the deltas are not lost silently;</li>
 * <li>on dry land at most {@value #DRY_BLOCKS} blocks per block between neighboring columns (D4b), and no water next to
 * lower dry ground.</li>
 * </ul>
 */
class LagoonDeltaTest {
	static final long SEED_A = 20260927L;
	static final long SEED_B = -7_316_550_294_015_845_337L;
	/** Least floor step (blocks) between neighboring lagoon columns that fails the test. */
	static final int FLOOR_STEP_LIMIT = 3;
	/** D4b: largest step on dry land in blocks per block. */
	static final int DRY_BLOCKS = 2;

	record Window(String name, LandscapeScale scale, long seed, double cx, double cz, int side, boolean delta) {
		@Override
		public String toString() {
			return name;
		}
	}

	static Stream<Window> windows() {
		LandscapeScale r = LandscapeScale.REALISTIC;
		LandscapeScale g = LandscapeScale.GAMEPLAY;
		return Stream.of(
				// The delta of review frame R_delta1 (a river along the shore, then into the lagoon).
				new Window("realistic_a_delta", r, SEED_A, -220_500, -155_150, 800, true),
				// REAL B: K8b1 had a step of 3 blocks at (-12104, -85135) and a straight front of 450 m.
				new Window("realistic_b_delta", r, SEED_B, -12_000, -85_050, 600, true),
				// GAMEPLAY A: K8b1 strip of land along a channel at the shore (5170, -12737).
				new Window("gameplay_a_strip", g, SEED_A, 5_170, -12_737, 800, true),
				// GAMEPLAY A: K8b1 steps of 3 blocks at (-12351, 11586), two deltas merged across a narrow lagoon.
				new Window("gameplay_a_merged", g, SEED_A, -12_200, 11_800, 800, true),
				// GAMEPLAY A: K8b1 needle of one column at (4964, -13152) on a delta measured along the channel line; no
				// mouth there now, so only the floor is checked.
				new Window("gameplay_a_needle", g, SEED_A, 4_948, -13_123, 500, false),
				// GAMEPLAY B: K8b1 strip of land along the northern half of a lagoon; a delta at a mouth to the east.
				new Window("gameplay_b_delta", g, SEED_B, -1_000, -12_520, 600, true));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("windows")
	void lagoonFloorsAndDeltasHaveNoSteps(Window win) {
		LandscapeModel m = new LandscapeModel(win.seed(), win.scale(), 1.0);
		VerticalScale vs = win.scale() == LandscapeScale.GAMEPLAY ? VerticalScale.GAMEPLAY : VerticalScale.REAL;
		int n = win.side();
		int x0 = (int) Math.floor(win.cx() - n / 2.0);
		int z0 = (int) Math.floor(win.cz() - n / 2.0);
		ColumnSample[][] g = new ColumnSample[n][n];
		boolean[][] lagoonLand = new boolean[n][n];
		long t0 = System.nanoTime();
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = x0 + i;
				double z = z0 + j;
				g[j][i] = m.sample(x, z);
				lagoonLand[j][i] = m.coastDistance(x, z) >= 0 && m.landElevation(x, z) < 0;
			}
		});
		long lagoon = 0;
		long delta = 0;
		long floor2 = 0;
		long floorBad = 0;
		long dryBad = 0;
		long leaks = 0;
		List<String> examples = new ArrayList<>();
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				ColumnSample c = g[j][i];
				boolean cl = lagoon(c);
				if (cl) {
					lagoon++;
				}
				if (!c.hasWater() && lagoonLand[j][i]) {
					delta++;
				}
				for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
					int ii = i + d[0];
					int jj = j + d[1];
					if (ii >= n || jj >= n) {
						continue;
					}
					ColumnSample o = g[jj][ii];
					double db = Math.abs(Math.floor(vs.blocksForMeters(c.surface())) - Math.floor(vs.blocksForMeters(o.surface())));
					if (cl && lagoon(o)) {
						if (db >= 2) {
							floor2++;
						}
						if (db >= FLOOR_STEP_LIMIT) {
							floorBad++;
							note(examples, "floor step %.0f blocks at (%d, %d)", db, x0 + i, z0 + j);
						}
					}
					if (!c.hasWater() && !o.hasWater() && db > DRY_BLOCKS) {
						dryBad++;
						note(examples, "dry step %.0f blocks at (%d, %d)", db, x0 + i, z0 + j);
					}
					ColumnSample w = c.hasWater() ? c : o;
					ColumnSample dry = c.hasWater() ? o : c;
					if (w.hasWater() && !dry.hasWater() && dry.surface() < w.waterLevel()) {
						leaks++;
						note(examples, "water next to lower dry ground at (%d, %d)", x0 + i, z0 + j);
					}
				}
			}
		}
		String report = String.format(Locale.ROOT,
				"%s (%s, (%.0f, %.0f), %d m): lagoon %d, delta land %d columns, floor steps >= 2 blocks %d, >= %d blocks %d,"
						+ " dry steps > %d blocks %d, leaks %d (%.1f s)",
				win.name(), win.scale().id(), win.cx(), win.cz(), n, lagoon, delta, floor2, FLOOR_STEP_LIMIT, floorBad,
				DRY_BLOCKS, dryBad, leaks, (System.nanoTime() - t0) / 1e9);
		System.out.println(report);
		examples.forEach(e -> System.out.println("  " + e));
		assertTrue(lagoon > 0 && (delta > 0 || !win.delta()), report + ": no lagoon or no delta in the window");
		assertTrue(floorBad == 0 && dryBad == 0 && leaks == 0, report + "\n  " + String.join("\n  ", examples));
	}

	static boolean lagoon(ColumnSample c) {
		return c.hasWater() && c.waterKind() == WaterKind.SEA && c.terrain().coastD() >= 0;
	}

	private static void note(List<String> examples, String format, Object... args) {
		if (examples.size() < 8) {
			examples.add(String.format(Locale.ROOT, format, args));
		}
	}
}
