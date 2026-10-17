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
 * the borders meander and mix in a ramp (no straight line), canopy gaps only in natural stands, fewer in the pole stage,
 * of skewed sizes and without a lattice (review of V9, round 1; the census of the gaps, N5, is reported by
 * {@code StandCensusSimulationTest}), and the map is a pure function of the seed and the position.
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
		int side = 2000;
		double[] shares = new double[3];
		for (AgeClass age : AgeClass.values()) {
			long inGap = 0;
			for (int x = 0; x < side; x++) {
				for (int z = 0; z < side; z++) {
					inGap += map.rawGapDepth(x, z, age) > 0 ? 1 : 0;
				}
			}
			shares[age.ordinal()] = (double) inGap / side / side;
		}
		// Gaps of the old growth: connected areas, their equivalent diameters.
		boolean[] gap = new boolean[side * side];
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				gap[x * side + z] = map.rawGapDepth(x, z, AgeClass.OLD_GROWTH) > 0;
			}
		}
		List<Double> diameters = new ArrayList<>();
		boolean[] seen = new boolean[gap.length];
		int[] queue = new int[gap.length];
		for (int c = 0; c < gap.length; c++) {
			if (!gap[c] || seen[c]) {
				continue;
			}
			int head = 0;
			int tail = 0;
			queue[tail++] = c;
			seen[c] = true;
			boolean border = false;
			while (head < tail) {
				int q = queue[head++];
				int x = q / side;
				int z = q % side;
				border |= x == 0 || z == 0 || x == side - 1 || z == side - 1;
				int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
				for (int[] st : steps) {
					int nx = x + st[0];
					int nz = z + st[1];
					if (nx >= 0 && nz >= 0 && nx < side && nz < side && gap[nx * side + nz] && !seen[nx * side + nz]) {
						seen[nx * side + nz] = true;
						queue[tail++] = nx * side + nz;
					}
				}
			}
			if (!border) {
				diameters.add(2 * Math.sqrt(tail / Math.PI));
			}
		}
		double[] d = diameters.stream().mapToDouble(Double::doubleValue).sorted().toArray();
		double mean = Arrays.stream(d).average().orElseThrow();
		double median = d[d.length / 2];
		long small = Arrays.stream(d).filter(v -> v < 25).count();
		long large = Arrays.stream(d).filter(v -> v >= 45).count();
		// No lattice: the lines of a fixed x or z without any gap over the area (the regular grid of V9 left 28% of them).
		int emptyLines = 0;
		for (int a = 0; a < side; a++) {
			boolean xLine = false;
			boolean zLine = false;
			for (int b = 0; b < side && !(xLine && zLine); b++) {
				xLine |= gap[a * side + b];
				zLine |= gap[b * side + a];
			}
			emptyLines += (xLine ? 0 : 1) + (zLine ? 0 : 1);
		}
		System.out.printf(Locale.ROOT, "[stands] planned gap share pole/mature/old %.2f / %.2f / %.2f%%; old growth: %d gaps, "
				+ "diameter p10 %.1f, median %.1f, mean %.1f, p90 %.1f, max %.1f blocks, %d%% below 25, %d%% from 45; lines "
				+ "without a gap %d of %d%n", 100 * shares[0], 100 * shares[1], 100 * shares[2], d.length, d[d.length / 10], median,
				mean, d[d.length * 9 / 10], d[d.length - 1], 100 * small / d.length, 100 * large / d.length, emptyLines, 2 * side);
		// The planned gaps; the crowns of their edge lean in, so the census measures less (N5: 3-8%, 18-45 blocks).
		assertTrue(shares[0] < shares[1] / 2, "few gaps in the pole stage");
		assertTrue(shares[1] >= 0.06 && shares[1] <= 0.14, "planned gap share of a mature stand 6-14%: " + shares[1]);
		assertTrue(shares[2] >= shares[1] && shares[2] <= 0.16, "planned gap share of the old growth: " + shares[2]);
		assertTrue(median < mean && d[d.length / 10] <= 0.7 * median && small > 0 && large > 0 && large < d.length / 4,
				"skewed gap sizes: from small to few large ones");
		// The largest are clusters of two or three touching gaps.
		assertTrue(d[0] >= 6 && d[d.length - 1] <= 100, "gap diameters within bounds");
		assertTrue(emptyLines <= 2 * side / 100, "no gap-free straight lines (no lattice): " + emptyLines);
		// Gaps only in natural stands.
		int natural = 0;
		int managedGaps = 0;
		for (int x = 0; x < 3000; x += 3) {
			for (int z = 0; z < 3000; z += 3) {
				boolean g = map.canopyGap(x, z);
				if (g && !map.natural(x, z)) {
					managedGaps++;
				}
				natural += g ? 1 : 0;
			}
		}
		assertEquals(0, managedGaps);
		assertTrue(natural > 0, "some gaps in natural stands");
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
