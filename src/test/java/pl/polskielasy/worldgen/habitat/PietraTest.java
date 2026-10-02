package pl.polskielasy.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.LandscapeModel;
import pl.polskielasy.worldgen.landscape.LandscapeScale;
import pl.polskielasy.worldgen.landscape.LandscapeType;

/**
 * Piętra Beskidów (docs/03-m2-biomy.md §5.1, §12.1): w reglu dolnym (600–1100 m) buczyna, jedlina
 * i olszyna ≥ 70%, w 1200–1350 m świerczyna ≥ 80%, kosodrzewina tylko przy dużym masywie (szczyt w promieniu
 * 3 km·mspace ponad 1470 m, sprawdzany niezależnie od pola modelu), a granica buczyny i świerczyny w wyniku
 * klasyfikacji leży na stokach S o 80–120 m wyżej niż na N.
 */
class PietraTest {
	static final long SEED = 20260927L;

	/** Punkt wnętrza Beskidów (waga typu > 0,98 w 9 punktach wokół), jak w podglądzie krajobrazu. */
	static double[] wnetrzeBeskidow(LandscapeModel m) {
		double unit = m.scale() == LandscapeScale.REALISTIC ? 5_000 : 200;
		for (int r = 0; r < 800; r++) {
			for (int k = 0; k < 24; k++) {
				double a = k * Math.PI / 12 + r * 0.37;
				double x = Math.cos(a) * r * unit;
				double z = Math.sin(a) * r * unit;
				boolean ok = true;
				for (int q = 0; q < 9 && ok; q++) {
					ok = m.typeWeights(x + ((q % 3) - 1) * 2 * unit, z + ((q / 3) - 1) * 2 * unit)[LandscapeType.BESKIDY.ordinal()] > 0.98;
				}
				if (ok) {
					return new double[] {x, z};
				}
			}
		}
		throw new AssertionError("brak wnętrza Beskidów");
	}

	/** Najwyższy punkt terenu w kwadracie 3000 × 3000 km (siatka 5 km, potem doprecyzowanie co 100 m). */
	static double[] najwyzszyMasyw(LandscapeModel m) {
		return szczyty(m, 3_000_000, 600, 1).get(0);
	}

	/**
	 * Szczyty w kwadracie o boku {@code bok} wokół (0, 0): najwyższy punkt siatki {@code n} × {@code n}
	 * w każdym wierszu, doprecyzowany co 1/50 oczka; najwyżej {@code ile} najwyższych, malejąco.
	 */
	static java.util.List<double[]> szczyty(LandscapeModel m, double bok, int n, int ile) {
		double[][] wyn = new double[n][];
		IntStream.range(0, n).parallel().forEach(j -> {
			double best = -1;
			for (int i = 0; i < n; i++) {
				double x = -bok / 2 + (i + 0.5) * bok / n;
				double z = -bok / 2 + (j + 0.5) * bok / n;
				double h = m.landElevation(x, z);
				if (h > best) {
					best = h;
					wyn[j] = new double[] {x, z, h};
				}
			}
		});
		java.util.List<double[]> l = new java.util.ArrayList<>(java.util.Arrays.asList(wyn));
		l.sort((a, c) -> Double.compare(c[2], a[2]));
		double oczko = bok / n;
		java.util.List<double[]> out = new java.util.ArrayList<>();
		for (double[] b : l.subList(0, Math.min(ile, l.size()))) {
			double[] best = b;
			for (int j = -50; j <= 50; j++) {
				for (int i = -50; i <= 50; i++) {
					double x = b[0] + i * oczko / 50;
					double z = b[1] + j * oczko / 50;
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
	static double maksWKole(float[] teren, int n, double x0, double z0, double krok, double x, double z, double r) {
		int ci = (int) Math.round((x - x0) / krok);
		int cj = (int) Math.round((z - z0) / krok);
		int rr = (int) Math.floor(r / krok);
		double max = Double.NEGATIVE_INFINITY;
		for (int dj = -rr; dj <= rr; dj++) {
			for (int di = -rr; di <= rr; di++) {
				int i = ci + di;
				int j = cj + dj;
				if (di * di + dj * dj <= rr * rr && i >= 0 && j >= 0 && i < n && j < n) {
					max = Math.max(max, teren[j * n + i]);
				}
			}
		}
		return max;
	}

	/** Udział biomów (liczniki według ordinal) w kolumnach Beskidów o wysokości [od, do), poza dnami i wodą. */
	static AtomicLongArray udzialy(LandscapeModel m, Klasyfikator k, double[] c, double bok, int n, double od, double dok) {
		AtomicLongArray licz = new AtomicLongArray(Biom.values().length);
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = c[0] - bok / 2 + i * bok / n;
				double z = c[1] - bok / 2 + j * bok / n;
				ColumnSample s = m.sample(x, z);
				if (s.hasWater() || s.teren().wBeskidy() < 0.9 || s.surface() < od || s.surface() >= dok) {
					continue;
				}
				Klasyfikator.Kolumna kol = new Klasyfikator.Kolumna(k, s, x, z);
				if (kol.dno()) {
					continue;
				}
				licz.incrementAndGet(Siedlisko.biom(k.klasyfikuj(s, x, z)).ordinal());
			}
		});
		return licz;
	}

	static double udzial(AtomicLongArray l, Biom... biomy) {
		long suma = 0;
		long w = 0;
		for (int i = 0; i < l.length(); i++) {
			suma += l.get(i);
		}
		for (Biom b : biomy) {
			w += l.get(b.ordinal());
		}
		return suma == 0 ? Double.NaN : (double) w / suma;
	}

	static long suma(AtomicLongArray l) {
		long s = 0;
		for (int i = 0; i < l.length(); i++) {
			s += l.get(i);
		}
		return s;
	}

	@Test
	void reglDolnyToBuczynaJedlinaIOlszyna() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			Klasyfikator k = new Klasyfikator(SEED, sc, Klasyfikator.Tryb.N);
			double[] c = wnetrzeBeskidow(m);
			double bok = sc == LandscapeScale.REALISTIC ? 30_000 : 3_000;
			AtomicLongArray l = udzialy(m, k, c, bok, 500, 600, 1_100);
			double u = udzial(l, Biom.BUCZYNA_GORSKA, Biom.JEDLINA_WYZYNNA, Biom.OLSZYNA_GORSKA);
			System.out.printf(Locale.ROOT, "%s: 600–1100 m, %d kolumn: buczyna %.1f%%, jedlina %.1f%%, olszyna %.1f%%, "
					+ "świerczyna %.1f%% (razem buczyna+jedlina+olszyna %.1f%%)%n", sc.id(), suma(l),
					100 * udzial(l, Biom.BUCZYNA_GORSKA), 100 * udzial(l, Biom.JEDLINA_WYZYNNA),
					100 * udzial(l, Biom.OLSZYNA_GORSKA), 100 * udzial(l, Biom.SWIERCZYNA_GORSKA), 100 * u);
			assertTrue(suma(l) > 10_000, "za mało kolumn regla dolnego: " + suma(l));
			assertTrue(u >= 0.70, sc.id() + ": buczyna, jedlina i olszyna " + u);
		}
	}

	@Test
	void reglGornyToSwierczyna() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		Klasyfikator k = new Klasyfikator(SEED, LandscapeScale.REALISTIC, Klasyfikator.Tryb.N);
		double[] szczyt = najwyzszyMasyw(m);
		System.out.printf(Locale.ROOT, "Najwyższy masyw: (%.0f, %.0f), %.0f m%n", szczyt[0], szczyt[1], szczyt[2]);
		AtomicLongArray l = udzialy(m, k, szczyt, 12_000, 600, 1_200, 1_350);
		double sw = udzial(l, Biom.SWIERCZYNA_GORSKA);
		System.out.printf(Locale.ROOT, "1200–1350 m: %d kolumn, świerczyna %.1f%%, buczyna %.1f%%, kosodrzewina %.1f%%%n", suma(l),
				100 * sw, 100 * udzial(l, Biom.BUCZYNA_GORSKA), 100 * udzial(l, Biom.KOSODRZEWINA));
		assertTrue(suma(l) > 1_000, "za mało kolumn 1200–1350 m: " + suma(l));
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
	void kosodrzewinaTylkoNaDuzymMasywie() {
		record Przypadek(long ziarno, LandscapeScale skala) {
		}
		for (Przypadek p : new Przypadek[] {new Przypadek(SEED, LandscapeScale.REALISTIC), new Przypadek(4L, LandscapeScale.REALISTIC),
				new Przypadek(1L, LandscapeScale.REALISTIC), new Przypadek(SEED, LandscapeScale.GAMEPLAY),
				new Przypadek(4L, LandscapeScale.GAMEPLAY)}) {
			LandscapeScale sc = p.skala();
			boolean real = sc == LandscapeScale.REALISTIC;
			LandscapeModel m = new LandscapeModel(p.ziarno(), sc, 1.0);
			Klasyfikator k = new Klasyfikator(p.ziarno(), sc, Klasyfikator.Tryb.N);
			java.util.List<double[]> lista = szczyty(m, real ? 3_000_000 : 70_000, real ? 1_500 : 1_166, 40);
			double r = 3_000 * sc.mountainSpacing();
			double krokT = 25 * sc.mountainSpacing();
			double krokK = 25 * sc.local();
			long kos = 0;
			long zle = 0;
			long sprawdzone = 0;
			double najwyzszy = lista.isEmpty() ? 0 : lista.get(0)[2];
			StringBuilder przyklady = new StringBuilder();
			java.util.List<double[]> zrobione = new java.util.ArrayList<>();
			for (double[] s : lista) {
				if (s[2] < 1_400) {
					break;
				}
				// Okna kolejnych szczytów nie zachodzą na siebie (szczyty z sąsiednich wierszy siatki to ten sam masyw).
				if (zrobione.stream().anyMatch(q -> Math.max(Math.abs(q[0] - s[0]), Math.abs(q[1] - s[1])) < 2 * r)) {
					continue;
				}
				zrobione.add(s);
				// Teren bez dolin na siatce wokół szczytu: okno kolumn (promień r) i koło r wokół każdej z nich.
				int n = (int) Math.ceil(4 * r / krokT) + 1;
				double x0 = s[0] - 2 * r;
				double z0 = s[1] - 2 * r;
				float[] teren = new float[n * n];
				IntStream.range(0, n).parallel().forEach(j -> {
					for (int i = 0; i < n; i++) {
						teren[j * n + i] = (float) m.landElevation(x0 + i * krokT, z0 + j * krokT);
					}
				});
				int nk = (int) (2 * r / krokK);
				AtomicLongArray lok = new AtomicLongArray(3);
				java.util.List<String> zleKolumny = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
				IntStream.range(0, nk).parallel().forEach(j -> {
					for (int i = 0; i < nk; i++) {
						double x = s[0] - r + i * krokK;
						double z = s[1] - r + j * krokK;
						ColumnSample c = m.sample(x, z);
						Biom b = Siedlisko.biom(k.klasyfikuj(c, x, z));
						if (b != Biom.KOSODRZEWINA && b != Biom.HALA) {
							continue;
						}
						lok.incrementAndGet(0);
						if (i % 2 != 0 || j % 2 != 0) {
							continue;
						}
						lok.incrementAndGet(1);
						// Tolerancja 1 m na dyskretyzację siatki terenu.
						if (maksWKole(teren, n, x0, z0, krokT, x, z, r) <= Pietra.DUZY_MASYW - 1) {
							lok.incrementAndGet(2);
							zleKolumny.add(String.format(Locale.ROOT, " (%.0f, %.0f) H %.0f pole %.0f;", x, z, c.surface(),
									c.teren().szczyt()));
						}
					}
				});
				kos += lok.get(0);
				sprawdzone += lok.get(1);
				zle += lok.get(2);
				java.util.Collections.sort(zleKolumny);
				for (String q : zleKolumny.subList(0, Math.min(5, zleKolumny.size()))) {
					przyklady.append(q);
				}
			}
			System.out.printf(Locale.ROOT, "%s, ziarno %d: najwyższy szczyt %.0f m, kolumny kosodrzewiny i hali %d (sprawdzone %d, "
					+ "bez szczytu > 1470 m w promieniu %.0f m: %d)%n", sc.id(), p.ziarno(), najwyzszy, kos, sprawdzone, r, zle);
			assertEquals(0, zle, sc.id() + ", ziarno " + p.ziarno() + ": kosodrzewina bez dużego masywu:" + przyklady);
			if (najwyzszy <= Pietra.DUZY_MASYW) {
				assertEquals(0, kos, sc.id() + ", ziarno " + p.ziarno() + ": kosodrzewina przy szczycie " + najwyzszy + " m");
			}
			if (real && p.ziarno() == 4L) {
				assertTrue(najwyzszy > 1_600 && kos > 1_000, "ziarno 4: masyw " + najwyzszy + " m, kosodrzewina " + kos);
			}
		}
	}

	@Test
	void duzyMasywWPrzypadkuSyntetycznym() {
		Klasyfikator k = new Klasyfikator(SEED, LandscapeScale.REALISTIC, Klasyfikator.Tryb.N);
		// Ten sam stok z dużym masywem i bez.
		Probka bez = Probka.beskidy(1_480);
		bez.szczyt = Pietra.DUZY_MASYW - 1;
		Probka z = Probka.beskidy(1_480);
		z.szczyt = Pietra.DUZY_MASYW + 1;
		int bezK = 0;
		int zK = 0;
		for (int i = 0; i < 200; i++) {
			bezK += Siedlisko.biom(k.klasyfikuj(bez.build(), i * 97.0, 0)) == Biom.KOSODRZEWINA ? 1 : 0;
			zK += Siedlisko.biom(k.klasyfikuj(z.build(), i * 97.0, 0)) == Biom.KOSODRZEWINA ? 1 : 0;
		}
		assertEquals(0, bezK);
		assertTrue(zK > 150, "kosodrzewina na dużym masywie: " + zK);
	}

	/**
	 * Granica regla górnego z wyniku klasyfikacji: wysokość, na której świerczyna typowa (bez Abieti-Piceetum,
	 * które na stokach N zastępuje buczynę od 900 m) obejmuje połowę kolumn regla (buczyna i świerczyna),
	 * osobno na stokach S (eksp. 135–225°) i N (315–45°), nachylenie ≥ 5°, poza dnami, w kwadracie 30 km
	 * wokół najwyższego masywu (koszyki co 10 m, interpolacja przejścia przez 50%).
	 */
	@Test
	void granicaReglaNaStokachPoludniowychLezyWyzej() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		Klasyfikator k = new Klasyfikator(SEED, LandscapeScale.REALISTIC, Klasyfikator.Tryb.N);
		double[] c = najwyzszyMasyw(m);
		int koszyki = 40;
		double od = 1_000;
		// [stok][koszyk][0 typowa, 1 razem]
		AtomicLongArray licz = new AtomicLongArray(2 * koszyki * 2);
		int n = 600;
		double bok = 30_000;
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = c[0] - bok / 2 + i * bok / n;
				double z = c[1] - bok / 2 + j * bok / n;
				ColumnSample s = m.sample(x, z);
				int kos = (int) Math.floor((s.surface() - od) / 10);
				if (kos < 0 || kos >= koszyki || s.hasWater() || s.teren().wBeskidy() < 0.9) {
					continue;
				}
				Klasyfikator.Kolumna kol = new Klasyfikator.Kolumna(k, s, x, z);
				double e = s.teren().eksp();
				if (kol.nach < 5 || Double.isNaN(e) || kol.dno()) {
					continue;
				}
				int stok = e >= 135 && e <= 225 ? 0 : e >= 315 || e <= 45 ? 1 : -1;
				if (stok < 0) {
					continue;
				}
				int kod = k.klasyfikuj(s, x, z);
				Biom b = Siedlisko.biom(kod);
				if (b != Biom.BUCZYNA_GORSKA && b != Biom.SWIERCZYNA_GORSKA) {
					continue;
				}
				int idx = (stok * koszyki + kos) * 2;
				licz.incrementAndGet(idx + 1);
				if (b == Biom.SWIERCZYNA_GORSKA && Siedlisko.zespol(kod) != Zespol.ABIETI_PICEETUM) {
					licz.incrementAndGet(idx);
				}
			}
		});
		double[] granica = new double[2];
		StringBuilder sb = new StringBuilder();
		for (int stok = 0; stok < 2; stok++) {
			granica[stok] = Double.NaN;
			double pop = 0;
			for (int q = 0; q < koszyki; q++) {
				long razem = licz.get((stok * koszyki + q) * 2 + 1);
				double u = razem < 20 ? pop : (double) licz.get((stok * koszyki + q) * 2) / razem;
				if (u >= 0.5 && Double.isNaN(granica[stok])) {
					double f = u - pop <= 0 ? 0 : (0.5 - pop) / (u - pop);
					granica[stok] = od + (q - 0.5 + f) * 10;
				}
				pop = u;
				if (q % 4 == 0) {
					sb.append(String.format(Locale.ROOT, " %s%.0f:%.0f%%(%d)", stok == 0 ? "S" : "N", od + q * 10 + 5, 100 * u, razem));
				}
			}
		}
		System.out.printf(Locale.ROOT, "Granica świerczyny typowej (wynik klasyfikacji): stoki S %.0f m, stoki N %.0f m, różnica %.0f m;%s%n",
				granica[0], granica[1], granica[0] - granica[1], sb);
		double roznica = granica[0] - granica[1];
		assertTrue(roznica >= 80 && roznica <= 120, "różnica S–N: " + roznica);
	}
}
