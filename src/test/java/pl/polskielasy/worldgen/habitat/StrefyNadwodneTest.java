package pl.polskielasy.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.LandscapeModel;
import pl.polskielasy.worldgen.landscape.LandscapeScale;

/**
 * Strefy nadwodne na przekrojach rzek (docs/03-m2-biomy.md §4, §12.1): po ok. 35 przekrojów na klasę A, B i C
 * w każdej skali (razem ok. 200 rzek). Przekrój zaczyna się na brzegu koryta i idzie prosto wzdłuż normalnej
 * do koryta (gradient d na brzegu), aż d zacznie maleć (bliżej jest inne koryto albo zakole). Kolejność stref: koryto → wiklina → okrajek → łęg wierzbowy → topolowy →
 * wiązowy → zbocze (A), koryto → ziołorośla → OlJ → strefowe (B), koryto → kamieniec/wiklina → olszyna →
 * strefowe (C). Minima w blokach (E11): wiklina 3, OlJ 6, ols 10.
 */
class StrefyNadwodneTest {
	static final long SEED = 20260927L;
	static final int NA_KLASE = 35;

	/** Przekrój: klasa cieku, kody kolumn co {@code krok} m od brzegu. */
	record Przekroj(StrefyNadwodne.Klasa klasa, int[] kody, boolean[] dno, boolean[] stojaca, double krok, double x,
			double z) {
	}

	static List<Przekroj> przekroje(LandscapeScale sc) {
		LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
		Klasyfikator k = new Klasyfikator(SEED, sc, Klasyfikator.Tryb.N);
		double siatka = sc == LandscapeScale.REALISTIC ? 2_500 : 120;
		int n = 240;
		List<Przekroj> wynik = Collections.synchronizedList(new ArrayList<>());
		IntStream.range(0, n * n).parallel().forEach(q -> {
			double x0 = ((q % n) - n / 2) * siatka + 0.37 * siatka * ((q / n) % 3);
			double z0 = ((q / n) - n / 2) * siatka;
			Przekroj p = przekroj(m, k, x0, z0);
			if (p != null) {
				wynik.add(p);
			}
		});
		// Kolejność niezależna od wątków; po NA_KLASE przekrojów na klasę.
		List<Przekroj> l = new ArrayList<>(wynik);
		l.sort((a, b) -> a.x() != b.x() ? Double.compare(a.x(), b.x()) : Double.compare(a.z(), b.z()));
		Map<StrefyNadwodne.Klasa, Integer> licz = new EnumMap<>(StrefyNadwodne.Klasa.class);
		List<Przekroj> out = new ArrayList<>();
		for (Przekroj p : l) {
			int c = licz.getOrDefault(p.klasa(), 0);
			if (c < NA_KLASE) {
				out.add(p);
				licz.put(p.klasa(), c + 1);
			}
		}
		return out;
	}

	/** Przekrój od brzegu koryta najbliższego punktowi (x0, z0), albo null, gdy punkt jest za daleko od cieku. */
	static Przekroj przekroj(LandscapeModel m, Klasyfikator k, double x0, double z0) {
		ColumnSample s = m.sample(x0, z0);
		ColumnSample.Wody w = s.wody();
		if (s.hasWater() || w.rzad() == 0 || !(w.odlKoryta() > 0 && w.odlKoryta() < 150 * m.scale().local())) {
			return null;
		}
		// Do brzegu: w dół gradientu d.
		double x = x0;
		double z = z0;
		for (int i = 0; i < 400; i++) {
			double d = m.sample(x, z).wody().odlKoryta();
			if (d <= 0.25) {
				break;
			}
			double[] g = gradient(m, x, z);
			if (g == null) {
				return null;
			}
			double krok = Math.max(0.2, Math.min(d * 0.5, 20));
			x -= g[0] * krok;
			z -= g[1] * krok;
		}
		ColumnSample brzeg = m.sample(x, z);
		if (!(Math.abs(brzeg.wody().odlKoryta()) < 1.0) || brzeg.wody().rzad() == 0) {
			return null;
		}
		// Koryto musi mieć wodę po drugiej stronie brzegu (bez suchych głowic dolin).
		double[] g0 = gradient(m, x, z);
		if (g0 == null || !m.sample(x - g0[0] * 1.5, z - g0[1] * 1.5).hasWater()) {
			return null;
		}
		Klasyfikator.Kolumna c = new Klasyfikator.Kolumna(k, brzeg, x, z);
		StrefyNadwodne.Klasa klasa = StrefyNadwodne.klasa(c);
		double szer = brzeg.wody().szerKoryta();
		double kk = m.scale().local();
		double dlugosc = switch (klasa) {
			case A -> Math.max(300 * kk, 3.5 * szer);
			case B -> 120 * kk;
			case C -> 80 * kk;
		};
		double krok = 0.5;
		int ile = (int) (dlugosc / krok);
		int[] kody = new int[ile];
		boolean[] dno = new boolean[ile];
		boolean[] stojaca = new boolean[ile];
		// Linia prosta wzdłuż normalnej do koryta na brzegu (gradient d). Gradient dalej od koryta kręci się
		// razem z układem doliny (meandry), więc przekrój po gradiencie zakosami wracałby przez te same pasy.
		double[] g = g0;
		double dPop = -1;
		for (int i = 0; i < ile; i++) {
			x += g[0] * krok;
			z += g[1] * krok;
			ColumnSample t = m.sample(x, z);
			double d = t.wody().odlKoryta();
			if (!(d >= dPop - 2.0) || t.wody().rzad() == 0 || t.hasWater()) {
				// Za grzbietem pola d (inny ciek bliżej): koniec przekroju.
				return i * krok >= 0.5 * dlugosc
						? new Przekroj(klasa, java.util.Arrays.copyOf(kody, i), java.util.Arrays.copyOf(dno, i),
								java.util.Arrays.copyOf(stojaca, i), krok, x0, z0)
						: null;
			}
			dPop = Math.max(dPop, d);
			kody[i] = k.klasyfikuj(t, x, z);
			dno[i] = new Klasyfikator.Kolumna(k, t, x, z).dno();
			stojaca[i] = t.wody().rodzajStojacej() != ColumnSample.RodzajStojacej.BRAK && Double.isFinite(t.wody().s());
		}
		return new Przekroj(klasa, kody, dno, stojaca, krok, x0, z0);
	}

	/** Kierunek wzrostu d (jednostkowy) z różnic ±1 m albo null. */
	static double[] gradient(LandscapeModel m, double x, double z) {
		double gx = m.sample(x + 1, z).wody().odlKoryta() - m.sample(x - 1, z).wody().odlKoryta();
		double gz = m.sample(x, z + 1).wody().odlKoryta() - m.sample(x, z - 1).wody().odlKoryta();
		double len = Math.hypot(gx, gz);
		return Double.isFinite(len) && len > 1e-6 ? new double[] {gx / len, gz / len} : null;
	}

	/**
	 * Ranga strefy w kolejności od koryta albo −1, gdy kolumna nie wchodzi do porządku: źródliska, młaki,
	 * starorzecza z pierścieniami, wody stojące i łęg jesionowo-olszowy z wysięku u podnóża zbocza (poza dnem).
	 */
	static int ranga(StrefyNadwodne.Klasa klasa, int kod, boolean dno, int dotad) {
		Biom b = Siedlisko.biom(kod);
		Strefa s = Siedlisko.strefa(kod);
		Zespol z = Siedlisko.zespol(kod);
		if (b.wodny() && b != Biom.JEZIORO) {
			return 0;
		}
		if (s == Strefa.ZRODLISKO || z == Zespol.ZRODLISKOWY || z == Zespol.MLAKA || z == Zespol.STARORZECZE
				|| b == Biom.JEZIORO || b == Biom.SZUWAR || b == Biom.LEG_JESIONOWO_OLSZOWY && !dno) {
			return -1;
		}
		return switch (klasa) {
			case A -> {
				if (s == Strefa.LACHA || s == Strefa.WIKLINA) {
					yield 1;
				}
				if (s == Strefa.OKRAJEK && dotad < 3) {
					yield 2;
				}
				if (b == Biom.LEG_WIERZBOWO_TOPOLOWY) {
					yield z == Zespol.SALICETUM_ALBAE ? 3 : 4;
				}
				if (b == Biom.LEG_WIAZOWO_JESIONOWY || z == Zespol.ZASTOISKO) {
					yield 5;
				}
				yield 6;
			}
			case B -> {
				if (s == Strefa.ZIOLOROSLA || s == Strefa.WIERZBY) {
					yield 1;
				}
				yield b == Biom.LEG_JESIONOWO_OLSZOWY ? 2 : 3;
			}
			case C -> {
				if (s == Strefa.KAMIENIEC || s == Strefa.WIKLINA || s == Strefa.ZIOLOROSLA_GORSKIE) {
					yield 1;
				}
				yield b == Biom.OLSZYNA_GORSKA || b == Biom.LEG_JESIONOWO_OLSZOWY ? 2 : 3;
			}
		};
	}

	/**
	 * Czy rangi wzdłuż przekroju nie maleją. Kolumny z rangą −1 pomijamy, a cofnięcie o najwyżej 1 m (dwie
	 * kolumny) traktujemy jak migotanie progu na drobnej rzeźbie skraju dna, nie jak zmianę kolejności.
	 */
	static boolean uporzadkowany(Przekroj p) {
		int max = 0;
		int cofniete = 0;
		for (int i = 0; i < p.kody().length; i++) {
			int r = ranga(p.klasa(), p.kody()[i], p.dno()[i], max);
			if (r < 0) {
				continue;
			}
			if (r < max) {
				if (++cofniete * p.krok() > 1.0) {
					return false;
				}
				continue;
			}
			cofniete = 0;
			max = r;
		}
		return true;
	}

	/** Długości (m) zakończonych ciągów kolumn spełniających warunek (bez ciągów przy końcach przekroju). */
	static List<Double> ciagi(Przekroj p, java.util.function.IntPredicate warunek, boolean takzePrzyBrzegu) {
		List<Double> l = new ArrayList<>();
		int start = -1;
		for (int i = 0; i <= p.kody().length; i++) {
			boolean w = i < p.kody().length && warunek.test(i);
			if (w && start < 0) {
				start = i;
			} else if (!w && start >= 0) {
				if ((start > 0 || takzePrzyBrzegu) && i < p.kody().length) {
					l.add((i - start) * p.krok());
				}
				start = -1;
			}
		}
		return l;
	}

	static void sprawdz(LandscapeScale sc) {
		List<Przekroj> lista = przekroje(sc);
		Map<StrefyNadwodne.Klasa, int[]> wynik = new EnumMap<>(StrefyNadwodne.Klasa.class);
		List<Double> wiklina = new ArrayList<>();
		List<Double> olj = new ArrayList<>();
		List<Double> ols = new ArrayList<>();
		List<String> nieuporzadkowane = new ArrayList<>();
		for (Przekroj p : lista) {
			int[] w = wynik.computeIfAbsent(p.klasa(), q -> new int[2]);
			w[0]++;
			if (uporzadkowany(p)) {
				w[1]++;
			} else if (nieuporzadkowane.size() < 6) {
				nieuporzadkowane.add(String.format(Locale.ROOT, "%s (%.0f, %.0f)", p.klasa(), p.x(), p.z()));
				System.out.println("  " + p.klasa() + ": " + rle(p));
			}
			if (p.klasa() != StrefyNadwodne.Klasa.B) {
				// Pas krzewów przy brzegu: wiklina razem z łachą i kamieńcem przed nią.
				wiklina.addAll(ciagi(p, i -> Siedlisko.strefa(p.kody()[i]) == Strefa.WIKLINA
						|| Siedlisko.strefa(p.kody()[i]) == Strefa.LACHA || Siedlisko.strefa(p.kody()[i]) == Strefa.KAMIENIEC, true));
			}
			if (p.klasa() == StrefyNadwodne.Klasa.B) {
				olj.addAll(ciagi(p, i -> Siedlisko.biom(p.kody()[i]) == Biom.LEG_JESIONOWO_OLSZOWY, true));
			}
			// Ols nadrzeczny (zastoiska, szerokie dna małych rzek); pierścienie wód stojących przecina przekrój
			// rzeki ukośnie, więc ich szerokości tu nie mierzymy.
			ols.addAll(ciagi(p, i -> Siedlisko.biom(p.kody()[i]) == Biom.OLS && !p.stojaca()[i], false));
		}
		System.out.printf(Locale.ROOT, "%s: przekroje %s; pasy wikliny %d (min %.1f m), OlJ %d (min %.1f m), ols %d (min %.1f m)%n",
				sc.id(), opis(wynik), wiklina.size(), min(wiklina), olj.size(), min(olj), ols.size(), min(ols));
		System.out.println("  nieuporządkowane (przykłady): " + nieuporzadkowane);
		for (StrefyNadwodne.Klasa kl : StrefyNadwodne.Klasa.values()) {
			int[] w = wynik.get(kl);
			assertTrue(w != null && w[0] >= 20, sc.id() + ": za mało przekrojów klasy " + kl + ": " + opis(wynik));
			assertTrue(w[1] >= 0.9 * w[0], sc.id() + ": klasa " + kl + " uporządkowana w " + w[1] + " z " + w[0]);
		}
		assertTrue(udzial(wiklina, Kalibracja.MIN_WIKLINA) >= 0.95, sc.id() + ": wiklina węższa niż 3 bloki: " + wiklina);
		assertTrue(udzial(olj, Kalibracja.MIN_OLJ) >= 0.95, sc.id() + ": OlJ węższy niż 6 bloków: " + olj);
		// Płaty olsu przecina przekrój pod różnymi kątami i przy brzegach płatów cięciwy są krótkie, więc
		// wymagamy, by co najmniej połowa cięciw miała ≥ 10 bloków (przy kole cięciwa krótsza niż 2/3 średnicy
		// zdarza się w ok. 25% przecięć).
		assertTrue(ols.isEmpty() || udzial(ols, Kalibracja.MIN_OLS) >= 0.5, sc.id() + ": ols węższy niż 10 bloków: " + ols);
	}

	static String rle(Przekroj p) {
		StringBuilder sb = new StringBuilder();
		int prev = Integer.MIN_VALUE;
		int start = 0;
		for (int i = 0; i <= p.kody().length; i++) {
			int kod = i < p.kody().length ? p.kody()[i] & ((1 << 21) - 1) : -2;
			if (kod != prev) {
				if (prev != Integer.MIN_VALUE) {
					sb.append(Siedlisko.biom(prev).id()).append('/').append(Siedlisko.strefa(prev).id()).append('/')
							.append(Siedlisko.zespol(prev)).append(':').append((i - start) * p.krok()).append(' ');
				}
				prev = kod;
				start = i;
			}
		}
		return sb.toString();
	}

	private static String opis(Map<StrefyNadwodne.Klasa, int[]> w) {
		StringBuilder sb = new StringBuilder();
		w.forEach((k, v) -> sb.append(k).append(' ').append(v[1]).append('/').append(v[0]).append(' '));
		return sb.toString().trim();
	}

	private static double min(List<Double> l) {
		return l.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
	}

	/** Udział pasów nie węższych niż minimum (z tolerancją kroku przekroju 0,5 m). */
	private static double udzial(List<Double> l, double minimum) {
		if (l.isEmpty()) {
			return 1;
		}
		return (double) l.stream().filter(v -> v >= minimum - 0.5).count() / l.size();
	}

	@Test
	void przekrojeWSkaliRealnej() {
		sprawdz(LandscapeScale.REALISTIC);
	}

	@Test
	void przekrojeWSkaliRozgrywki() {
		sprawdz(LandscapeScale.GAMEPLAY);
	}
}
