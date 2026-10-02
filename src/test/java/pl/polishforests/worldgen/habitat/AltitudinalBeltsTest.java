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
 * Piętra Beskidów (docs/03-m2-biomy.md §5.1, §12.1): w reglu dolnym (600–1100 m) buczyna, jedlina
 * i olszyna ≥ 70%, w 1200–1350 m świerczyna ≥ 80%, kosodrzewina tylko przy dużym masywie (szczyt w promieniu
 * 3 km·mspace ponad 1470 m, sprawdzany niezależnie od pola modelu), a granica buczyny i świerczyny w wyniku
 * klasyfikacji leży na stokach S o 80–120 m wyżej niż na N.
 */
class AltitudinalBeltsTest {
	static final long SEED = 20260927L;

	/** Punkt wnętrza Beskidów (waga typu > 0,98 w 9 punktach wokół), jak w podglądzie krajobrazu. */
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
		throw new AssertionError("brak wnętrza Beskidów");
	}

	/** Najwyższy punkt terenu w kwadracie 3000 × 3000 km (siatka 5 km, potem doprecyzowanie co 100 m). */
	static double[] highestMassif(LandscapeModel m) {
		return summits(m, 3_000_000, 600, 1).get(0);
	}

	/**
	 * Szczyty w kwadracie o boku {@code bok} wokół (0, 0): najwyższy punkt siatki {@code n} × {@code n}
	 * w każdym wierszu, doprecyzowany co 1/50 oczka; najwyżej {@code ile} najwyższych, malejąco.
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
	 * Najwyższy teren w kole o promieniu {@code r} wokół (x, z) z siatki {@code teren} o oczku {@code krok},
	 * zaczepionej w (x0, z0), o {@code n} węzłach na bok. Niezależne od pola {@code teren.szczyt} modelu.
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

	/** Udział biomów (liczniki według ordinal) w kolumnach Beskidów o wysokości [od, do), poza dnami i wodą. */
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
			System.out.printf(Locale.ROOT, "%s: 600–1100 m, %d kolumn: buczyna %.1f%%, jedlina %.1f%%, olszyna %.1f%%, "
					+ "świerczyna %.1f%% (razem buczyna+jedlina+olszyna %.1f%%)%n", sc.id(), sum(l),
					100 * share(l, HabitatBiome.MONTANE_BEECH_FOREST), 100 * share(l, HabitatBiome.UPLAND_FIR_FOREST),
					100 * share(l, HabitatBiome.GRAY_ALDER_FOREST), 100 * share(l, HabitatBiome.MONTANE_SPRUCE_FOREST), 100 * u);
			assertTrue(sum(l) > 10_000, "za mało kolumn regla dolnego: " + sum(l));
			assertTrue(u >= 0.70, sc.id() + ": buczyna, jedlina i olszyna " + u);
		}
	}

	@Test
	void upperMontaneIsSpruce() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		HabitatClassifier k = new HabitatClassifier(SEED, LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL);
		double[] summit = highestMassif(m);
		System.out.printf(Locale.ROOT, "Najwyższy masyw: (%.0f, %.0f), %.0f m%n", summit[0], summit[1], summit[2]);
		AtomicLongArray l = shares(m, k, summit, 12_000, 600, 1_200, 1_350);
		double sw = share(l, HabitatBiome.MONTANE_SPRUCE_FOREST);
		System.out.printf(Locale.ROOT, "1200–1350 m: %d kolumn, świerczyna %.1f%%, buczyna %.1f%%, kosodrzewina %.1f%%%n", sum(l),
				100 * sw, 100 * share(l, HabitatBiome.MONTANE_BEECH_FOREST), 100 * share(l, HabitatBiome.DWARF_PINE_SCRUB));
		assertTrue(sum(l) > 1_000, "za mało kolumn 1200–1350 m: " + sum(l));
		assertTrue(sw >= 0.80, "świerczyna w 1200–1350 m: " + sw);
	}

	/**
	 * Kosodrzewina i hala (E12) w świecie: wokół szczytów ponad 1400 m (siatka 2 km w kwadracie 3000 km w REAL,
	 * 60 m w kwadracie 70 km w GAMEPLAY) każda kolumna kosodrzewiny lub hali (co czwarta, siatka 25 m·k) ma w
	 * promieniu 3 km·mspace teren wyższy niż 1470 m, liczony wprost z {@code landElevation} na siatce 25 m·mspace,
	 * a nie z pola modelu. Ziarno 4 ma w REAL masyw 1645 m i musi mieć kosodrzewinę. Ziarno domyślne ma w REAL
	 * najwyżej ok. 1445 m, a GAMEPLAY ok. 1310 m, więc tam kosodrzewiny nie ma (decyzja M2-8: wyższe masywy
	 * przyjdą z poprawką geometrii terenu).
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
				// Okna kolejnych szczytów nie zachodzą na siebie (szczyty z sąsiednich wierszy siatki to ten sam masyw).
				if (done.stream().anyMatch(q -> Math.max(Math.abs(q[0] - s[0]), Math.abs(q[1] - s[1])) < 2 * r)) {
					continue;
				}
				done.add(s);
				// Teren bez dolin na siatce wokół szczytu: okno kolumn (promień r) i koło r wokół każdej z nich.
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
						// Tolerancja 1 m na dyskretyzację siatki terenu.
						if (maxInCircle(terrain, n, x0, z0, terrainStep, x, z, r) <= AltitudinalBelts.LARGE_MASSIF - 1) {
							tally.incrementAndGet(2);
							badColumns.add(String.format(Locale.ROOT, " (%.0f, %.0f) H %.0f pole %.0f;", x, z, c.surface(),
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
			System.out.printf(Locale.ROOT, "%s, ziarno %d: najwyższy szczyt %.0f m, kolumny kosodrzewiny i hali %d (sprawdzone %d, "
					+ "bez szczytu > 1470 m w promieniu %.0f m: %d)%n", sc.id(), p.seed(), highest, dwarfPine, checked, r, bad);
			assertEquals(0, bad, sc.id() + ", ziarno " + p.seed() + ": kosodrzewina bez dużego masywu:" + examples);
			if (highest <= AltitudinalBelts.LARGE_MASSIF) {
				assertEquals(0, dwarfPine, sc.id() + ", ziarno " + p.seed() + ": kosodrzewina przy szczycie " + highest + " m");
			}
			if (real && p.seed() == 4L) {
				assertTrue(highest > 1_600 && dwarfPine > 1_000, "ziarno 4: masyw " + highest + " m, kosodrzewina " + dwarfPine);
			}
		}
	}

	@Test
	void largeMassifInSyntheticCase() {
		HabitatClassifier k = new HabitatClassifier(SEED, LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL);
		// Ten sam stok z dużym masywem i bez.
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
		assertTrue(withCount > 150, "kosodrzewina na dużym masywie: " + withCount);
	}

	/**
	 * Granica regla górnego z wyniku klasyfikacji: wysokość, na której świerczyna typowa (bez Abieti-Piceetum,
	 * które na stokach N zastępuje buczynę od 900 m) obejmuje połowę kolumn regla (buczyna i świerczyna),
	 * osobno na stokach S (eksp. 135–225°) i N (315–45°), nachylenie ≥ 5°, poza dnami, w kwadracie 30 km
	 * wokół najwyższego masywu (koszyki co 10 m, interpolacja przejścia przez 50%).
	 */
	@Test
	void beltLimitIsHigherOnSouthernSlopes() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		HabitatClassifier k = new HabitatClassifier(SEED, LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL);
		double[] c = highestMassif(m);
		int bins = 40;
		double fromH = 1_000;
		// [stok][koszyk][0 typowa, 1 razem]
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
		System.out.printf(Locale.ROOT, "Granica świerczyny typowej (wynik klasyfikacji): stoki S %.0f m, stoki N %.0f m, różnica %.0f m;%s%n",
				limit[0], limit[1], limit[0] - limit[1], sb);
		double difference = limit[0] - limit[1];
		assertTrue(difference >= 80 && difference <= 120, "różnica S–N: " + difference);
	}
}
