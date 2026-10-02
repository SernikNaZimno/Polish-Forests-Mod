package pl.polskielasy.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.LandscapeModel;
import pl.polskielasy.worldgen.landscape.LandscapeScale;
import pl.polskielasy.worldgen.landscape.Noise;

/**
 * Wybrzeże na przekrojach brzegu (docs/03-m2-biomy.md §5.2, poprawka po S4): przekrój zaczyna się na linii
 * brzegu i idzie w głąb lądu wzdłuż normalnej do brzegu (gradient odległości od morza) do B + D + 400k.
 * Na brzegu wydmowym ({@code niskiBrzeg} ≥ 0,5) w pasie wydm B ≤ cD &lt; B + D nie ma stref klifu, a wydmy
 * i bór bażynowy zajmują większość kolumn lądu poza dnami dolin i brzegami wód stojących. Na brzegu wysokim
 * jest ściana klifu.
 */
class WybrzezeTest {
	static final long SEED = 20260927L;
	/** Przekroje brzegu wydmowego i wysokiego (każdego rodzaju najwyżej tyle). */
	static final int NA_RODZAJ = 40;

	/**
	 * Przekrój: brzeg wydmowy (w środku pasa wydm), kody kolumn, odległości od morza, kolumny liczone
	 * (ląd poza dnami i brzegami wód stojących) i kolumny brzegu wydmowego.
	 */
	record Przekroj(boolean wydmowy, int[] kody, double[] cD, boolean[] liczona, boolean[] niski, double x, double z) {
	}

	static List<Przekroj> przekroje(LandscapeModel m, Klasyfikator k) {
		LandscapeScale sc = m.scale();
		double kk = sc.local();
		double siatka = sc == LandscapeScale.REALISTIC ? 12_000 : 600;
		int n = sc == LandscapeScale.REALISTIC ? 250 : 150;
		List<long[]> kandydaci = Collections.synchronizedList(new ArrayList<>());
		IntStream.range(0, n * n).parallel().forEach(q -> {
			double x = ((q % n) - n / 2) * siatka;
			double z = ((q / n) - n / 2) * siatka;
			double d = m.coastDistance(x, z);
			if (d > 0 && d < 3_000 * kk) {
				kandydaci.add(new long[] {Noise.mix(SEED + q), q});
			}
		});
		// Wybór niezależny od kolejności wątków: według skrótu.
		List<long[]> l = new ArrayList<>(kandydaci);
		l.sort((a, b) -> Long.compare(a[0], b[0]));
		List<Przekroj> out = new ArrayList<>();
		int wydmowe = 0;
		int wysokie = 0;
		for (long[] c : l) {
			if (wydmowe >= NA_RODZAJ && wysokie >= NA_RODZAJ) {
				break;
			}
			int q = (int) c[1];
			Przekroj p = przekroj(m, k, ((q % n) - n / 2) * siatka, ((q / n) - n / 2) * siatka);
			if (p != null && (p.wydmowy() ? wydmowe++ : wysokie++) < NA_RODZAJ) {
				out.add(p);
			}
		}
		return out;
	}

	static double[] gradient(LandscapeModel m, double x, double z) {
		double gx = m.coastDistance(x + 2, z) - m.coastDistance(x - 2, z);
		double gz = m.coastDistance(x, z + 2) - m.coastDistance(x, z - 2);
		double len = Math.hypot(gx, gz);
		return len > 1e-9 ? new double[] {gx / len, gz / len} : null;
	}

	static Przekroj przekroj(LandscapeModel m, Klasyfikator k, double x0, double z0) {
		double kk = m.scale().local();
		double x = x0;
		double z = z0;
		for (int i = 0; i < 60; i++) {
			double d = m.coastDistance(x, z);
			if (Math.abs(d) < 0.5) {
				break;
			}
			double[] g = gradient(m, x, z);
			if (g == null) {
				return null;
			}
			x -= g[0] * d;
			z -= g[1] * d;
		}
		if (Math.abs(m.coastDistance(x, z)) >= 0.5) {
			return null;
		}
		double[] g = gradient(m, x, z);
		if (g == null) {
			return null;
		}
		double b = Kalibracja.PLAZA_B * kk;
		double dw = Kalibracja.WYDMY_D * kk;
		double dlugosc = b + dw + 400 * kk;
		double krok = 1.0 * kk;
		int ile = (int) (dlugosc / krok);
		int[] kody = new int[ile];
		double[] cD = new double[ile];
		boolean[] liczona = new boolean[ile];
		boolean[] niski = new boolean[ile];
		// Brzeg wydmowy według pola w środku pasa wydm.
		ColumnSample srodek = m.sample(x + g[0] * (b + dw / 2), z + g[1] * (b + dw / 2));
		boolean wydmowy = srodek.teren().niskiBrzeg() >= Kalibracja.NISKI_BRZEG;
		for (int i = 0; i < ile; i++) {
			double px = x + g[0] * (i + 0.5) * krok;
			double pz = z + g[1] * (i + 0.5) * krok;
			ColumnSample s = m.sample(px, pz);
			kody[i] = k.klasyfikuj(s, px, pz);
			cD[i] = s.teren().coastD();
			Klasyfikator.Kolumna c = new Klasyfikator.Kolumna(k, s, px, pz);
			liczona[i] = !s.hasWater() && !c.dno() && !(c.w.s() < Kalibracja.J_OLS_K * kk);
			niski[i] = s.teren().niskiBrzeg() >= Kalibracja.NISKI_BRZEG;
		}
		return new Przekroj(wydmowy, kody, cD, liczona, niski, x, z);
	}

	static void sprawdz(LandscapeScale sc) {
		LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
		Klasyfikator k = new Klasyfikator(SEED, sc, Klasyfikator.Tryb.N);
		List<Przekroj> lista = przekroje(m, k);
		double kk = sc.local();
		double b = Kalibracja.PLAZA_B * kk;
		double dw = Kalibracja.WYDMY_D * kk;
		int wydmowe = 0;
		int wysokie = 0;
		int wysokieZKlifem = 0;
		long pas = 0;
		long wydmy = 0;
		long biala = 0;
		long szara = 0;
		long klif = 0;
		List<String> zKlifem = new ArrayList<>();
		for (Przekroj p : lista) {
			if (!p.wydmowy()) {
				wysokie++;
				boolean sciana = false;
				for (int kod : p.kody()) {
					sciana |= Siedlisko.strefa(kod) == Strefa.KLIF_SCIANA;
				}
				wysokieZKlifem += sciana ? 1 : 0;
				continue;
			}
			wydmowe++;
			for (int i = 0; i < p.kody().length; i++) {
				if (p.cD()[i] < b || p.cD()[i] >= b + dw || !p.liczona()[i] || !p.niski()[i]) {
					continue;
				}
				int kod = p.kody()[i];
				Biom bi = Siedlisko.biom(kod);
				Strefa s = Siedlisko.strefa(kod);
				pas++;
				if (s == Strefa.KLIF_SCIANA || s == Strefa.KLIF_KORONA || Siedlisko.zespol(kod) == Zespol.WIATROWY) {
					klif++;
					if (zKlifem.size() < 5) {
						zKlifem.add(String.format(Locale.ROOT, "(%.0f, %.0f) cD %.0f %s", p.x(), p.z(), p.cD()[i],
								Siedlisko.of(kod)));
					}
				}
				if (bi == Biom.WYDMA_BIALA || bi == Biom.WYDMA_SZARA || bi == Biom.BOR_BAZYNOWY) {
					wydmy++;
				}
				biala += bi == Biom.WYDMA_BIALA ? 1 : 0;
				szara += bi == Biom.WYDMA_SZARA ? 1 : 0;
			}
		}
		double udzial = pas == 0 ? Double.NaN : (double) wydmy / pas;
		System.out.printf(Locale.ROOT,
				"%s: przekroje %d (wydmowe %d, wysokie %d, w tym z klifem %d); pas wydm %d kolumn: wydmy i bór bażynowy %.1f%% "
						+ "(biała %.1f%%, szara %.1f%%), klif %d%n",
				sc.id(), lista.size(), wydmowe, wysokie, wysokieZKlifem, pas, 100 * udzial, 100.0 * biala / Math.max(1, pas),
				100.0 * szara / Math.max(1, pas), klif);
		assertTrue(wydmowe >= 20, sc.id() + ": za mało brzegów wydmowych: " + wydmowe);
		assertEquals(0, klif, sc.id() + ": klif na brzegu wydmowym: " + zKlifem);
		assertTrue(udzial >= 0.9, sc.id() + ": wydmy i bór bażynowy w pasie wydm: " + udzial);
		assertTrue(biala > 0 && szara > 0, sc.id() + ": brak wydmy białej albo szarej");
		assertTrue(wysokie == 0 || wysokieZKlifem > 0, sc.id() + ": brak klifu na wysokim brzegu");
	}

	@Test
	void brzegWydmowyWSkaliRealnej() {
		sprawdz(LandscapeScale.REALISTIC);
	}

	@Test
	void brzegWydmowyWSkaliRozgrywki() {
		sprawdz(LandscapeScale.GAMEPLAY);
	}
}
