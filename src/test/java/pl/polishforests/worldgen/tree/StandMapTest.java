package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The stand map of the world ({@link ForestStandMap}, step V9 of docs/04-pionowy-wycinek.md §5): shares of the age
 * classes and of the natural stands in 10⁴ stands within ±3 percentage points, stand sizes of 2–30 ha·k² in both scales,
 * the borders meander and mix in a ramp (no straight line), canopy gaps of the planned size (about 9% of the area before
 * the crowns of their edge lean in; the census measures 3–8%, N5) only in natural stands, and the map is a pure function
 * of the seed and the position.
 */
class StandMapTest {
	private static final long SEED = 20260927L;
	private static final double[] SCALES = {1.0, 0.5};

	@Test
	void sharesOfAgeClassesAndNaturalStandsIn10000Stands() {
		for (double managed : new double[] {0.85, 0.5}) {
			ForestStandMap map = new ForestStandMap(SEED, 1, managed);
			int natural = 0;
			int[][] ages = new int[2][3];
			for (int i = 0; i < 100; i++) {
				for (int j = 0; j < 100; j++) {
					ForestStandMap.Stand s = map.root(i - 50, j - 50);
					natural += s.natural() ? 1 : 0;
					ages[s.natural() ? 1 : 0][s.age().ordinal()]++;
				}
			}
			assertEquals(1 - managed, natural / 1e4, 0.03, "natural share");
			for (int n = 0; n < 2; n++) {
				int total = Arrays.stream(ages[n]).sum();
				double[] want = n == 1 ? ForestStandMap.NATURAL_AGES : ForestStandMap.MANAGED_AGES;
				for (int a = 0; a < 3; a++) {
					assertEquals(want[a], (double) ages[n][a] / total, 0.03, (n == 1 ? "natural" : "managed") + " age class " + a);
				}
			}
		}
	}

	@Test
	void standsHave2To30HectaresTimesKSquared() {
		for (double k : SCALES) {
			ForestStandMap map = new ForestStandMap(SEED, k, 0.85);
			// A raster of 5 × 5 blocks·k cells over 24 × 24 stand spacings.
			int step = (int) Math.round(5 * k);
			int side = (int) (24 * map.spacing() / step);
			Map<Long, Integer> cells = new HashMap<>();
			Set<Long> border = new HashSet<>();
			for (int a = 0; a < side; a++) {
				for (int b = 0; b < side; b++) {
					long id = map.stand(a * step, b * step).id();
					cells.merge(id, 1, Integer::sum);
					if (a == 0 || b == 0 || a == side - 1 || b == side - 1) {
						border.add(id);
					}
				}
			}
			List<Double> hectares = new ArrayList<>();
			for (Map.Entry<Long, Integer> e : cells.entrySet()) {
				if (!border.contains(e.getKey()) && e.getValue() > 3) {
					hectares.add(e.getValue() * (double) step * step / 10_000 / (k * k));
				}
			}
			hectares.sort(Double::compare);
			double inRange = hectares.stream().filter(h -> h >= 2 && h <= 30).count() / (double) hectares.size();
			double mean = hectares.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
			System.out.printf(Locale.ROOT, "[stands] k %.2f: %d stands, ha·k² p5 %.1f, p25 %.1f, median %.1f, p75 %.1f, p95 %.1f, mean %.1f;"
					+ " in 2-30: %.1f%%%n", k, hectares.size(), q(hectares, 0.05), q(hectares, 0.25), q(hectares, 0.5), q(hectares, 0.75),
					q(hectares, 0.95), mean, 100 * inRange);
			assertTrue(hectares.size() > 300, "enough stands");
			assertTrue(inRange >= 0.9, "at least 90% of the stands have 2-30 ha·k², " + 100 * inRange + "%");
		}
	}

	@Test
	void bordersMeanderAndMixInARamp() {
		ForestStandMap map = new ForestStandMap(SEED, 1, 0.85);
		// Along lines across the map, the columns where the stand changes: a border crossed by a line is no single step
		// (the stands mix column by column over a few blocks), and the crossings of parallel lines one block apart move.
		int crossings = 0;
		int mixed = 0;
		for (int line = 0; line < 40; line++) {
			int z = line * 37;
			long last = map.stand(0, z).id();
			int lastChange = -100;
			for (int x = 1; x < 3000; x++) {
				long id = map.stand(x, z).id();
				if (id != last) {
					if (x - lastChange <= 6) {
						mixed++;
					}
					crossings++;
					lastChange = x;
					last = id;
				}
			}
		}
		System.out.printf(Locale.ROOT, "[stands] %d changes of the stand along 40 lines, %d within 6 blocks of the previous one%n",
				crossings, mixed);
		assertTrue(mixed > crossings / 5, "stand borders mix over a ramp");
	}

	@Test
	void canopyGapsOnlyInNaturalStandsOfThePlannedSize() {
		ForestStandMap map = new ForestStandMap(SEED, 1, 0.85);
		// Gap grid alone over 2000 × 2000 blocks.
		long inGap = 0;
		long all = 0;
		for (int x = 0; x < 2000; x++) {
			for (int z = 0; z < 2000; z++) {
				all++;
				inGap += map.rawGapDepth(x, z) > 0 ? 1 : 0;
			}
		}
		double share = (double) inGap / all;
		// Equivalent diameters of the gaps of the cells in the area (whole gaps only).
		List<Double> diameters = new ArrayList<>();
		for (int i = 1; i < 16; i++) {
			for (int j = 1; j < 16; j++) {
				int area = gapArea(map, i, j);
				if (area > 0) {
					diameters.add(2 * Math.sqrt(area / Math.PI));
				}
			}
		}
		double mean = diameters.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
		double min = diameters.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
		double max = diameters.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
		System.out.printf(Locale.ROOT, "[stands] gaps: %.2f%% of the area, %d gaps, diameter mean %.1f, min %.1f, max %.1f blocks; one per %.2f ha%n",
				100 * share, diameters.size(), mean, min, max, ForestStandMap.GAP_SPACING * ForestStandMap.GAP_SPACING / 1e4 / ForestStandMap.GAP_CHANCE);
		// The planned gaps; the crowns of their edge lean in, so the census measures 18-45 blocks and 3-8% (N5).
		assertTrue(share >= 0.06 && share <= 0.12, "planned gap share 6-12%: " + share);
		assertTrue(min >= ForestStandMap.GAP_MIN - 2 && max <= ForestStandMap.GAP_MAX + 3, "planned gap diameters");
		// Gaps only in natural stands.
		int natural = 0;
		int managedGaps = 0;
		for (int x = 0; x < 3000; x += 3) {
			for (int z = 0; z < 3000; z += 3) {
				boolean gap = map.canopyGap(x, z);
				if (gap && !map.natural(x, z)) {
					managedGaps++;
				}
				natural += gap ? 1 : 0;
			}
		}
		assertEquals(0, managedGaps);
		assertTrue(natural > 0, "some gaps in natural stands");
	}

	/** Columns of the gap of grid cell (i, j), counted around its center. */
	private static int gapArea(ForestStandMap map, int i, int j) {
		double s = ForestStandMap.GAP_SPACING;
		int count = 0;
		for (int x = (int) Math.floor((i - 0.3) * s); x < (int) Math.ceil((i + 1.3) * s); x++) {
			for (int z = (int) Math.floor((j - 0.3) * s); z < (int) Math.ceil((j + 1.3) * s); z++) {
				if (map.rawGapDepth(x, z) > 0 && owner(map, x, z) == ((long) i << 32 | j & 0xFFFF_FFFFL)) {
					count++;
				}
			}
		}
		return count;
	}

	/** The gap cell whose gap is deepest at (x, z) (gaps of neighboring cells never touch in practice). */
	private static long owner(ForestStandMap map, int x, int z) {
		int gx = (int) Math.floor(x / ForestStandMap.GAP_SPACING);
		int gz = (int) Math.floor(z / ForestStandMap.GAP_SPACING);
		double best = -Double.MAX_VALUE;
		long id = 0;
		for (int i = gx - 1; i <= gx + 1; i++) {
			for (int j = gz - 1; j <= gz + 1; j++) {
				double d = map.gapDepthOf(i, j, x, z);
				if (d > best) {
					best = d;
					id = (long) i << 32 | j & 0xFFFF_FFFFL;
				}
			}
		}
		return id;
	}

	@Test
	void pureFunctionOfSeedAndPosition() {
		ForestStandMap a = new ForestStandMap(SEED, 1, 0.85);
		ForestStandMap b = new ForestStandMap(SEED, 1, 0.85);
		ForestStandMap other = new ForestStandMap(SEED + 1, 1, 0.85);
		int differ = 0;
		for (int x = -2000; x < 2000; x += 13) {
			for (int z = -2000; z < 2000; z += 17) {
				assertEquals(a.stand(x, z), b.stand(x, z));
				assertEquals(a.canopyGap(x, z), b.canopyGap(x, z));
				differ += a.stand(x, z).equals(other.stand(x, z)) ? 0 : 1;
			}
		}
		assertTrue(differ > 1000, "another seed, other stands");
	}

	@Test
	void reportCost() {
		ForestStandMap map = new ForestStandMap(SEED, 1, 0.85);
		long sum = 0;
		for (int round = 0; round < 3; round++) {
			long t0 = System.nanoTime();
			for (int x = 0; x < 300; x++) {
				for (int z = 0; z < 300; z++) {
					sum += map.stand(x * 7, z * 5).age().ordinal() + (map.canopyGap(x * 7, z * 5) ? 1 : 0);
				}
			}
			System.out.printf(Locale.ROOT, "[stands] %.3f µs per column (stand and gap)%n", (System.nanoTime() - t0) / 90_000.0 / 1000);
		}
		assertTrue(sum >= 0);
	}

	private static double q(List<Double> sorted, double p) {
		return sorted.get((int) Math.min(sorted.size() - 1, Math.floor(p * sorted.size())));
	}
}
