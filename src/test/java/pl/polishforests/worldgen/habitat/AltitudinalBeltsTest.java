package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.LandscapeType;

/**
 * Altitudinal belts of the Beskids (docs/03-m2-biomy.md §5.1, §12.1): in the lower montane belt (600–1100 m) beech,
 * fir and gray alder forests ≥ 70%, at 1200–1350 m spruce forest ≥ 80%, dwarf pine only on a large massif (a summit
 * above 1470 m within 3 km·mspace, checked independently of the model field), and the beech/spruce forest limit
 * resulting from the classification lies 80–120 m higher on S slopes than on N slopes.
 */
class AltitudinalBeltsTest {
	static final long SEED = 20260927L;

	/** A point in the interior of the Beskids (type weight > 0.98 at 9 points around it), as in the landscape preview. */
	static double[] beskidsInterior(LandscapeModel m) {
		double unit = m.scale() == LandscapeScale.REALISTIC ? 5_000 : 200;
		for (int r = 0; r < 800; r++) {
			for (int k = 0; k < 24; k++) {
				double a = k * Math.PI / 12 + r * 0.37;
				double x = Math.cos(a) * r * unit;
				double z = Math.sin(a) * r * unit;
				boolean ok = true;
				for (int q = 0; q < 9 && ok; q++) {
					ok = m.typeWeights(x + ((q % 3) - 1) * 2 * unit, z + ((q / 3) - 1) * 2 * unit)[LandscapeType.BESKIDS.ordinal()] > 0.98;
				}
				if (ok) {
					return new double[] {x, z};
				}
			}
		}
		throw new AssertionError("no Beskids interior found");
	}

	/** Highest terrain point in a 3000 × 3000 km square (5 km grid, then refined every 100 m). */
	static double[] highestMassif(LandscapeModel m) {
		return summits(m, 3_000_000, 600, 1).get(0);
	}

	/**
	 * Summits in a square with a side of {@code sideLength} around (0, 0): the highest point of an {@code n} × {@code n}
	 * grid in each row, refined every 1/50 of a cell; at most the {@code topCount} highest, in descending order.
	 */
	static java.util.List<double[]> summits(LandscapeModel m, double sideLength, int n, int topCount) {
		double[][] results = new double[n][];
		IntStream.range(0, n).parallel().forEach(j -> {
			double best = -1;
			for (int i = 0; i < n; i++) {
				double x = -sideLength / 2 + (i + 0.5) * sideLength / n;
				double z = -sideLength / 2 + (j + 0.5) * sideLength / n;
				double h = m.landElevation(x, z);
				if (h > best) {
					best = h;
					results[j] = new double[] {x, z, h};
				}
			}
		});
		java.util.List<double[]> l = new java.util.ArrayList<>(java.util.Arrays.asList(results));
		l.sort((a, c) -> Double.compare(c[2], a[2]));
		double cellSize = sideLength / n;
		java.util.List<double[]> out = new java.util.ArrayList<>();
		for (double[] b : l.subList(0, Math.min(topCount, l.size()))) {
			double[] best = b;
			for (int j = -50; j <= 50; j++) {
				for (int i = -50; i <= 50; i++) {
					double x = b[0] + i * cellSize / 50;
					double z = b[1] + j * cellSize / 50;
					double h = m.landElevation(x, z);
					if (h > best[2]) {
						best = new double[] {x, z, h};
					}
				}
			}
			out.add(best);
		}
		out.sort((a, c) -> Double.compare(c[2], a[2]));
		return out;
	}

	/**
	 * Highest terrain in a circle of radius {@code r} around (x, z) from the {@code terrain} grid with a cell size of
	 * {@code step}, anchored at (x0, z0), with {@code n} nodes per side. Independent of the model's {@code terrain.summit} field.
	 */
	static double maxInCircle(float[] terrain, int n, double x0, double z0, double step, double x, double z, double r) {
		int ci = (int) Math.round((x - x0) / step);
		int cj = (int) Math.round((z - z0) / step);
		int rr = (int) Math.floor(r / step);
		double max = Double.NEGATIVE_INFINITY;
		for (int dj = -rr; dj <= rr; dj++) {
			for (int di = -rr; di <= rr; di++) {
				int i = ci + di;
				int j = cj + dj;
				if (di * di + dj * dj <= rr * rr && i >= 0 && j >= 0 && i < n && j < n) {
					max = Math.max(max, terrain[j * n + i]);
				}
			}
		}
		return max;
	}

	/** Biome shares (counters by ordinal) in Beskids columns with a height in [fromH, toH), outside valley floors and water. */
	static AtomicLongArray shares(LandscapeModel m, HabitatClassifier k, double[] c, double sideLength, int n, double fromH, double toH) {
		AtomicLongArray count = new AtomicLongArray(HabitatBiome.values().length);
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = c[0] - sideLength / 2 + i * sideLength / n;
				double z = c[1] - sideLength / 2 + j * sideLength / n;
				ColumnSample s = m.sample(x, z);
				if (s.hasWater() || s.terrain().wBeskids() < 0.9 || s.surface() < fromH || s.surface() >= toH) {
					continue;
				}
				HabitatClassifier.Column col = new HabitatClassifier.Column(k, s, x, z);
				if (col.onValleyFloor()) {
					continue;
				}
				count.incrementAndGet(Habitat.biome(k.classify(s, x, z)).ordinal());
			}
		});
		return count;
	}

	static double share(AtomicLongArray l, HabitatBiome... biomes) {
		long sum = 0;
		long w = 0;
		for (int i = 0; i < l.length(); i++) {
			sum += l.get(i);
		}
		for (HabitatBiome b : biomes) {
			w += l.get(b.ordinal());
		}
		return sum == 0 ? Double.NaN : (double) w / sum;
	}

	static long sum(AtomicLongArray l) {
		long s = 0;
		for (int i = 0; i < l.length(); i++) {
			s += l.get(i);
		}
		return s;
	}

	@Test
	void lowerMontaneIsBeechFirAndGrayAlder() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			HabitatClassifier k = new HabitatClassifier(SEED, sc, HabitatClassifier.Mode.NATURAL);
			double[] c = beskidsInterior(m);
			double sideLength = sc == LandscapeScale.REALISTIC ? 30_000 : 3_000;
			AtomicLongArray l = shares(m, k, c, sideLength, 500, 600, 1_100);
			double u = share(l, HabitatBiome.MONTANE_BEECH_FOREST, HabitatBiome.UPLAND_FIR_FOREST, HabitatBiome.GRAY_ALDER_FOREST);
			System.out.printf(Locale.ROOT, "%s: 600–1100 m, %d columns: beech forest %.1f%%, fir forest %.1f%%, gray alder forest %.1f%%, "
					+ "spruce forest %.1f%% (beech+fir+gray alder together %.1f%%)%n", sc.id(), sum(l),
					100 * share(l, HabitatBiome.MONTANE_BEECH_FOREST), 100 * share(l, HabitatBiome.UPLAND_FIR_FOREST),
					100 * share(l, HabitatBiome.GRAY_ALDER_FOREST), 100 * share(l, HabitatBiome.MONTANE_SPRUCE_FOREST), 100 * u);
			assertTrue(sum(l) > 10_000, "too few lower montane columns: " + sum(l));
			assertTrue(u >= 0.70, sc.id() + ": beech, fir and gray alder forests " + u);
		}
	}

	@Test
	void upperMontaneIsSpruce() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		HabitatClassifier k = new HabitatClassifier(SEED, LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL);
		double[] summit = highestMassif(m);
		System.out.printf(Locale.ROOT, "Highest massif: (%.0f, %.0f), %.0f m%n", summit[0], summit[1], summit[2]);
		AtomicLongArray l = shares(m, k, summit, 12_000, 600, 1_200, 1_350);
		double sw = share(l, HabitatBiome.MONTANE_SPRUCE_FOREST);
		System.out.printf(Locale.ROOT, "1200–1350 m: %d columns, spruce forest %.1f%%, beech forest %.1f%%, dwarf pine %.1f%%%n", sum(l),
				100 * sw, 100 * share(l, HabitatBiome.MONTANE_BEECH_FOREST), 100 * share(l, HabitatBiome.DWARF_PINE_SCRUB));
		assertTrue(sum(l) > 1_000, "too few columns at 1200–1350 m: " + sum(l));
		assertTrue(sw >= 0.80, "spruce forest at 1200–1350 m: " + sw);
	}

	/**
	 * Dwarf pine and alpine grassland (E12) in the world: around summits above 1400 m (2 km grid in a 3000 km square in
	 * REAL, 60 m in a 70 km square in GAMEPLAY) every dwarf pine or alpine grassland column (every fourth, 25 m·k grid)
	 * has terrain higher than 1470 m within 3 km·mspace, computed directly from {@code landElevation} on a 25 m·mspace
	 * grid rather than from the model field. Seed 4 has a 1645 m massif in REAL and must have dwarf pine. The default
	 * seed reaches at most about 1445 m in REAL and about 1310 m in GAMEPLAY, so there is no dwarf pine there
	 * (decision M2-8: higher massifs will come with the terrain geometry fix).
	 */
	@Test
	void dwarfPineOnlyOnLargeMassif() {
		record Case(long seed, LandscapeScale scale) {
		}
		for (Case p : new Case[] {new Case(SEED, LandscapeScale.REALISTIC), new Case(4L, LandscapeScale.REALISTIC),
				new Case(1L, LandscapeScale.REALISTIC), new Case(SEED, LandscapeScale.GAMEPLAY),
				new Case(4L, LandscapeScale.GAMEPLAY)}) {
			LandscapeScale sc = p.scale();
			boolean real = sc == LandscapeScale.REALISTIC;
			LandscapeModel m = new LandscapeModel(p.seed(), sc, 1.0);
			HabitatClassifier k = new HabitatClassifier(p.seed(), sc, HabitatClassifier.Mode.NATURAL);
			java.util.List<double[]> list = summits(m, real ? 3_000_000 : 70_000, real ? 1_500 : 1_166, 40);
			double r = 3_000 * sc.mountainSpacing();
			double terrainStep = 25 * sc.mountainSpacing();
			double columnStep = 25 * sc.local();
			long dwarfPine = 0;
			long bad = 0;
			long checked = 0;
			double highest = list.isEmpty() ? 0 : list.get(0)[2];
			StringBuilder examples = new StringBuilder();
			java.util.List<double[]> done = new java.util.ArrayList<>();
			for (double[] s : list) {
				if (s[2] < 1_400) {
					break;
				}
				// Windows of successive summits do not overlap (summits from neighboring grid rows are the same massif).
				if (done.stream().anyMatch(q -> Math.max(Math.abs(q[0] - s[0]), Math.abs(q[1] - s[1])) < 2 * r)) {
					continue;
				}
				done.add(s);
				// Terrain without valleys on a grid around the summit: the column window (radius r) and a circle r around each column.
				int n = (int) Math.ceil(4 * r / terrainStep) + 1;
				double x0 = s[0] - 2 * r;
				double z0 = s[1] - 2 * r;
				float[] terrain = new float[n * n];
				IntStream.range(0, n).parallel().forEach(j -> {
					for (int i = 0; i < n; i++) {
						terrain[j * n + i] = (float) m.landElevation(x0 + i * terrainStep, z0 + j * terrainStep);
					}
				});
				int nk = (int) (2 * r / columnStep);
				AtomicLongArray tally = new AtomicLongArray(3);
				java.util.List<String> badColumns = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
				IntStream.range(0, nk).parallel().forEach(j -> {
					for (int i = 0; i < nk; i++) {
						double x = s[0] - r + i * columnStep;
						double z = s[1] - r + j * columnStep;
						ColumnSample c = m.sample(x, z);
						HabitatBiome b = Habitat.biome(k.classify(c, x, z));
						if (b != HabitatBiome.DWARF_PINE_SCRUB && b != HabitatBiome.ALPINE_GRASSLAND) {
							continue;
						}
						tally.incrementAndGet(0);
						if (i % 2 != 0 || j % 2 != 0) {
							continue;
						}
						tally.incrementAndGet(1);
						// 1 m tolerance for the terrain grid discretization.
						if (maxInCircle(terrain, n, x0, z0, terrainStep, x, z, r) <= AltitudinalBelts.LARGE_MASSIF - 1) {
							tally.incrementAndGet(2);
							badColumns.add(String.format(Locale.ROOT, " (%.0f, %.0f) H %.0f field %.0f;", x, z, c.surface(),
									c.terrain().summit()));
						}
					}
				});
				dwarfPine += tally.get(0);
				checked += tally.get(1);
				bad += tally.get(2);
				java.util.Collections.sort(badColumns);
				for (String q : badColumns.subList(0, Math.min(5, badColumns.size()))) {
					examples.append(q);
				}
			}
			System.out.printf(Locale.ROOT, "%s, seed %d: highest summit %.0f m, dwarf pine and alpine grassland columns %d (checked %d, "
					+ "without a summit > 1470 m within %.0f m: %d)%n", sc.id(), p.seed(), highest, dwarfPine, checked, r, bad);
			assertEquals(0, bad, sc.id() + ", seed " + p.seed() + ": dwarf pine without a large massif:" + examples);
			if (highest <= AltitudinalBelts.LARGE_MASSIF) {
				assertEquals(0, dwarfPine, sc.id() + ", seed " + p.seed() + ": dwarf pine with a highest summit of " + highest + " m");
			}
			if (real && p.seed() == 4L) {
				assertTrue(highest > 1_600 && dwarfPine > 1_000, "seed 4: massif " + highest + " m, dwarf pine " + dwarfPine);
			}
		}
	}

	@Test
	void largeMassifInSyntheticCase() {
		HabitatClassifier k = new HabitatClassifier(SEED, LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL);
		// The same slope with and without a large massif.
		SyntheticSample without = SyntheticSample.beskids(1_480);
		without.summit = AltitudinalBelts.LARGE_MASSIF - 1;
		SyntheticSample z = SyntheticSample.beskids(1_480);
		z.summit = AltitudinalBelts.LARGE_MASSIF + 1;
		int withoutCount = 0;
		int withCount = 0;
		for (int i = 0; i < 200; i++) {
			withoutCount += Habitat.biome(k.classify(without.build(), i * 97.0, 0)) == HabitatBiome.DWARF_PINE_SCRUB ? 1 : 0;
			withCount += Habitat.biome(k.classify(z.build(), i * 97.0, 0)) == HabitatBiome.DWARF_PINE_SCRUB ? 1 : 0;
		}
		assertEquals(0, withoutCount);
		assertTrue(withCount > 150, "dwarf pine on a large massif: " + withCount);
	}

	/**
	 * Upper montane limit from the classification result: the height at which typical spruce forest (without
	 * Abieti-Piceetum, which replaces beech forest on N slopes from 900 m) covers half of the montane columns (beech and
	 * spruce forest), separately on S slopes (aspect 135–225°) and N slopes (315–45°), slope ≥ 5°, outside valley floors,
	 * in a 30 km square around the highest massif (10 m bins, interpolation of the 50% crossing).
	 */
	@Test
	void beltLimitIsHigherOnSouthernSlopes() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		HabitatClassifier k = new HabitatClassifier(SEED, LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL);
		double[] c = highestMassif(m);
		int bins = 40;
		double fromH = 1_000;
		// [slope side][bin][0 typical, 1 total]
		AtomicLongArray count = new AtomicLongArray(2 * bins * 2);
		int n = 600;
		double sideLength = 30_000;
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = c[0] - sideLength / 2 + i * sideLength / n;
				double z = c[1] - sideLength / 2 + j * sideLength / n;
				ColumnSample s = m.sample(x, z);
				int bin = (int) Math.floor((s.surface() - fromH) / 10);
				if (bin < 0 || bin >= bins || s.hasWater() || s.terrain().wBeskids() < 0.9) {
					continue;
				}
				HabitatClassifier.Column col = new HabitatClassifier.Column(k, s, x, z);
				double e = s.terrain().aspect();
				if (col.slope < 5 || Double.isNaN(e) || col.onValleyFloor()) {
					continue;
				}
				int side = e >= 135 && e <= 225 ? 0 : e >= 315 || e <= 45 ? 1 : -1;
				if (side < 0) {
					continue;
				}
				int code = k.classify(s, x, z);
				HabitatBiome b = Habitat.biome(code);
				if (b != HabitatBiome.MONTANE_BEECH_FOREST && b != HabitatBiome.MONTANE_SPRUCE_FOREST) {
					continue;
				}
				int idx = (side * bins + bin) * 2;
				count.incrementAndGet(idx + 1);
				if (b == HabitatBiome.MONTANE_SPRUCE_FOREST && Habitat.association(code) != Association.ABIETI_PICEETUM) {
					count.incrementAndGet(idx);
				}
			}
		});
		double[] limit = new double[2];
		StringBuilder sb = new StringBuilder();
		for (int side = 0; side < 2; side++) {
			limit[side] = Double.NaN;
			double prev = 0;
			for (int q = 0; q < bins; q++) {
				long total = count.get((side * bins + q) * 2 + 1);
				double u = total < 20 ? prev : (double) count.get((side * bins + q) * 2) / total;
				if (u >= 0.5 && Double.isNaN(limit[side])) {
					double f = u - prev <= 0 ? 0 : (0.5 - prev) / (u - prev);
					limit[side] = fromH + (q - 0.5 + f) * 10;
				}
				prev = u;
				if (q % 4 == 0) {
					sb.append(String.format(Locale.ROOT, " %s%.0f:%.0f%%(%d)", side == 0 ? "S" : "N", fromH + q * 10 + 5, 100 * u, total));
				}
			}
		}
		System.out.printf(Locale.ROOT, "Typical spruce forest limit (classification result): S slopes %.0f m, N slopes %.0f m, difference %.0f m;%s%n",
				limit[0], limit[1], limit[0] - limit[1], sb);
		double difference = limit[0] - limit[1];
		assertTrue(difference >= 80 && difference <= 120, "S–N difference: " + difference);
	}
}
