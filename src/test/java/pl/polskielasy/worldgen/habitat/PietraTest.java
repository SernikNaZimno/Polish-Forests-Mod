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
 * i olszyna ≥ 70%, w 1200–1350 m świerczyna ≥ 80%, kosodrzewina tylko przy dużym masywie, a granica regla
 * na stokach S leży o 80–120 m wyżej niż na N.
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
		int n = 600;
		double bok = 3_000_000;
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
		double[] b = wyn[0];
		for (double[] w : wyn) {
			if (w[2] > b[2]) {
				b = w;
			}
		}
		double[] best = b;
		for (int j = -50; j <= 50; j++) {
			for (int i = -50; i <= 50; i++) {
				double h = m.landElevation(b[0] + i * 100, b[1] + j * 100);
				if (h > best[2]) {
					best = new double[] {b[0] + i * 100, b[1] + j * 100, h};
				}
			}
		}
		return best;
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
	void reglGornyToSwierczynaAKosodrzewinaTylkoNaDuzymMasywie() {
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
		// Kosodrzewina i hala tylko tam, gdzie szczyt w promieniu 3 km przekracza 1470 m.
		AtomicLongArray wysoko = udzialy(m, k, szczyt, 12_000, 300, 1_200, 3_000);
		long kos = wysoko.get(Biom.KOSODRZEWINA.ordinal()) + wysoko.get(Biom.HALA.ordinal());
		if (szczyt[2] <= Pietra.DUZY_MASYW) {
			assertEquals(0, kos, "kosodrzewina bez dużego masywu (najwyższy szczyt " + szczyt[2] + " m)");
		}
		System.out.println("Kolumny kosodrzewiny i hali przy najwyższym masywie: " + kos);
		// Syntetycznie: ten sam stok z dużym masywem i bez.
		Probka bez = Probka.beskidy(1_480);
		bez.szczyt = Pietra.PROG_SZCZYTU - 1;
		Probka z = Probka.beskidy(1_480);
		z.szczyt = Pietra.PROG_SZCZYTU + 1;
		int bezK = 0;
		int zK = 0;
		for (int i = 0; i < 200; i++) {
			bezK += Siedlisko.biom(k.klasyfikuj(bez.build(), i * 97.0, 0)) == Biom.KOSODRZEWINA ? 1 : 0;
			zK += Siedlisko.biom(k.klasyfikuj(z.build(), i * 97.0, 0)) == Biom.KOSODRZEWINA ? 1 : 0;
		}
		assertEquals(0, bezK);
		assertTrue(zK > 150, "kosodrzewina na dużym masywie: " + zK);
	}

	@Test
	void granicaReglaNaStokachPoludniowychLezyWyzej() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		Klasyfikator k = new Klasyfikator(SEED, LandscapeScale.REALISTIC, Klasyfikator.Tryb.N);
		double[] c = wnetrzeBeskidow(m);
		double[] sumy = new double[4];
		int n = 400;
		double bok = 30_000;
		Object lock = new Object();
		IntStream.range(0, n).parallel().forEach(j -> {
			double[] lok = new double[4];
			for (int i = 0; i < n; i++) {
				double x = c[0] - bok / 2 + i * bok / n;
				double z = c[1] - bok / 2 + j * bok / n;
				ColumnSample s = m.sample(x, z);
				Klasyfikator.Kolumna kol = new Klasyfikator.Kolumna(k, s, x, z);
				double e = s.teren().eksp();
				if (kol.nach < 5 || Double.isNaN(e) || s.teren().wBeskidy() < 0.9) {
					continue;
				}
				double g = Pietra.regielGorny(kol);
				if (e >= 135 && e <= 225) {
					lok[0] += g;
					lok[1]++;
				} else if (e >= 315 || e <= 45) {
					lok[2] += g;
					lok[3]++;
				}
			}
			synchronized (lock) {
				for (int q = 0; q < 4; q++) {
					sumy[q] += lok[q];
				}
			}
		});
		double s = sumy[0] / sumy[1];
		double nn = sumy[2] / sumy[3];
		System.out.printf(Locale.ROOT, "Granica regla górnego: stoki S %.0f m (%d), stoki N %.0f m (%d), różnica %.0f m%n", s,
				(long) sumy[1], nn, (long) sumy[3], s - nn);
		assertTrue(sumy[1] > 1_000 && sumy[3] > 1_000, "za mało stoków");
		assertTrue(s - nn >= 80 && s - nn <= 120, "różnica S–N: " + (s - nn));
	}
}
