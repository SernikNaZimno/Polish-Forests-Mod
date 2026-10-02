package pl.polskielasy.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.LandscapeModel;
import pl.polskielasy.worldgen.landscape.LandscapeScale;
import pl.polskielasy.worldgen.landscape.LandscapeType;
import pl.polskielasy.worldgen.landscape.Noise;

/**
 * Udziały biomów w trybie N „roślinność naturalna” (docs/03-m2-biomy.md §12.1): lesistość lądu ≥ 88%, a na
 * sandrze (wnętrze typu, waga ≥ 0,9) bory ≥ 85% lasu. Próbki: 200 tys. kolumn na skalę (3 ziarna), w skupiskach 8 × 8 co 125 m·k
 * rozrzuconych losowo (deterministycznie) po kwadracie 1000 × 1000 km w REAL i 30 × 30 km w GAMEPLAY.
 * Skupiska wykorzystują kafle sieci rzecznej i siatki terenu, więc test jest kilkakrotnie szybszy niż
 * przy punktach całkiem rozrzuconych, a udziały w obszarze zostają nieobciążone.
 */
public class BiomeSharesTest {
	static final long[] ZIARNA = {20260927L, 1L, 2L};
	/** Waga typu SANDR, od której kolumna liczy się do wnętrza sandru. */
	static final double WNETRZE_SANDRU = 0.9;

	/** Liczniki udziałów: kolumny według biomu (cały ląd i woda) i według biomu na sandrze. */
	public static final class Udzialy {
		public final long[] biom = new long[Biom.values().length];
		public final long[] biomSandr = new long[Biom.values().length];
		public final long[] strefa = new long[Strefa.values().length];
		public long kolumny;

		public synchronized void dodaj(Udzialy u) {
			for (int i = 0; i < biom.length; i++) {
				biom[i] += u.biom[i];
				biomSandr[i] += u.biomSandr[i];
			}
			for (int i = 0; i < strefa.length; i++) {
				strefa[i] += u.strefa[i];
			}
			kolumny += u.kolumny;
		}

		/** Udział lasu w lądzie (biomy leśne / biomy niewodne). */
		public double lesistosc() {
			return lesistosc(biom);
		}

		public double lesistoscSandr() {
			return lesistosc(biomSandr);
		}

		/** Udział borów w lesie na sandrze. */
		public double boryWLesieSandr() {
			long las = 0;
			long bory = 0;
			for (Biom b : Biom.values()) {
				if (b.lesny()) {
					las += biomSandr[b.ordinal()];
					bory += b.bor() ? biomSandr[b.ordinal()] : 0;
				}
			}
			return las == 0 ? Double.NaN : (double) bory / las;
		}

		private static double lesistosc(long[] t) {
			long lad = 0;
			long las = 0;
			for (Biom b : Biom.values()) {
				if (!b.wodny()) {
					lad += t[b.ordinal()];
					las += b.lesny() ? t[b.ordinal()] : 0;
				}
			}
			return lad == 0 ? Double.NaN : (double) las / lad;
		}

		public long lad() {
			long lad = 0;
			for (Biom b : Biom.values()) {
				lad += b.wodny() ? 0 : biom[b.ordinal()];
			}
			return lad;
		}
	}

	/**
	 * Udziały w kwadracie o boku {@code bok} m wokół (0, 0): {@code skupiska} skupisk po 64 kolumny.
	 */
	public static Udzialy policz(long seed, LandscapeScale scale, Klasyfikator.Tryb tryb, double bok, int skupiska) {
		LandscapeModel m = new LandscapeModel(seed, scale, 1.0);
		Klasyfikator k = new Klasyfikator(seed, scale, tryb);
		Udzialy wynik = new Udzialy();
		double krok = 125 * scale.local();
		IntStream.range(0, skupiska).parallel().forEach(q -> {
			Udzialy u = new Udzialy();
			long h = Noise.mix(seed * 31 + q);
			double cx = ((h >>> 11) * 0x1.0p-53 - 0.5) * bok;
			double cz = ((Noise.mix(h) >>> 11) * 0x1.0p-53 - 0.5) * bok;
			for (int j = 0; j < 8; j++) {
				for (int i = 0; i < 8; i++) {
					double x = cx + i * krok;
					double z = cz + j * krok;
					ColumnSample s = m.sample(x, z);
					int kod = k.klasyfikuj(s, x, z);
					int b = Siedlisko.biom(kod).ordinal();
					u.biom[b]++;
					u.strefa[Siedlisko.strefa(kod).ordinal()]++;
					// Sandr: wnętrze typu (waga ≥ 0,9), bez pasów mieszania z wysoczyzną, które w GAMEPLAY
					// (regiony ok. 1,4 km) zajmują dużą część sandru.
					if (s.type() == LandscapeType.SANDR && s.teren().wSandr() >= WNETRZE_SANDRU) {
						u.biomSandr[b]++;
					}
					u.kolumny++;
				}
			}
			wynik.dodaj(u);
		});
		return wynik;
	}

	/** 200 tys. kolumn na skalę: 3 ziarna po 1042 skupiska po 64 kolumny. */
	static Udzialy razem(LandscapeScale scale, Klasyfikator.Tryb tryb) {
		double bok = scale == LandscapeScale.REALISTIC ? 1_000_000 : 30_000;
		Udzialy suma = new Udzialy();
		for (long seed : ZIARNA) {
			suma.dodaj(policz(seed, scale, tryb, bok, 1_042));
		}
		return suma;
	}

	static void wypisz(String nazwa, Udzialy u) {
		System.out.printf(Locale.ROOT, "%s: %d kolumn, ląd %d; lesistość %.1f%%, na sandrze %.1f%%, bory w lesie sandru %.1f%%%n",
				nazwa, u.kolumny, u.lad(), 100 * u.lesistosc(), 100 * u.lesistoscSandr(), 100 * u.boryWLesieSandr());
		long sandr = 0;
		for (long v : u.biomSandr) {
			sandr += v;
		}
		System.out.println("  biom                      wszystkie  sandr");
		for (Biom b : Biom.values()) {
			if (u.biom[b.ordinal()] > 0) {
				System.out.printf(Locale.ROOT, "  %-24s %7.2f%% %7.2f%%%n", b.id(), 100.0 * u.biom[b.ordinal()] / u.kolumny,
						100.0 * u.biomSandr[b.ordinal()] / Math.max(1, sandr));
			}
		}
	}

	@Test
	void naturalnaRoslinnoscWSkaliRealnej() {
		Udzialy u = razem(LandscapeScale.REALISTIC, Klasyfikator.Tryb.N);
		wypisz("REAL, tryb N", u);
		assertTrue(u.lesistosc() >= 0.88, "lesistość " + u.lesistosc());
		assertTrue(u.boryWLesieSandr() >= 0.85, "bory w lesie sandru " + u.boryWLesieSandr());
	}

	@Test
	void naturalnaRoslinnoscWSkaliRozgrywki() {
		Udzialy u = razem(LandscapeScale.GAMEPLAY, Klasyfikator.Tryb.N);
		wypisz("GAMEPLAY, tryb N", u);
		assertTrue(u.lesistosc() >= 0.88, "lesistość " + u.lesistosc());
		assertTrue(u.boryWLesieSandr() >= 0.85, "bory w lesie sandru " + u.boryWLesieSandr());
	}
}
