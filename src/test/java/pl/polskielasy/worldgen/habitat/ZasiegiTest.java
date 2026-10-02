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
import pl.polskielasy.worldgen.landscape.Noise;

/**
 * Zasięgi gatunków (docs/03-m2-biomy.md §9, §12.1): w świecie (3 ziarna REAL na 1000 × 1000 km, 1 ziarno
 * GAMEPLAY na 30 × 30 km) nie ma buka przy O &lt; 0,35 i P &lt; 0,35, jodły przy P &lt; 0,45 ani naturalnego
 * świerka przy O &gt; 0,35 i P &lt; 0,45 (flagi siedliska i biomy, które ich wymagają).
 */
class ZasiegiTest {
	static final int BUK = 0;
	static final int JODLA = 1;
	static final int SWIERK = 2;
	static final int BUCZYNY = 3;
	static final int JEDLINA = 4;
	static final int KOLUMNY = 5;
	static final int Z_BUKIEM = 6;
	static final int Z_JODLA = 7;
	static final int ZE_SWIERKIEM = 8;

	static AtomicLongArray sprawdz(long seed, LandscapeScale sc, double bok, int skupiska) {
		LandscapeModel m = new LandscapeModel(seed, sc, 1.0);
		Klasyfikator k = new Klasyfikator(seed, sc, Klasyfikator.Tryb.N);
		AtomicLongArray l = new AtomicLongArray(9);
		double krok = 125 * sc.local();
		IntStream.range(0, skupiska).parallel().forEach(q -> {
			long h = Noise.mix(seed * 17 + q);
			double cx = ((h >>> 11) * 0x1.0p-53 - 0.5) * bok;
			double cz = ((Noise.mix(h) >>> 11) * 0x1.0p-53 - 0.5) * bok;
			for (int j = 0; j < 8; j++) {
				for (int i = 0; i < 8; i++) {
					double x = cx + i * krok;
					double z = cz + j * krok;
					ColumnSample s = m.sample(x, z);
					if (s.hasWater()) {
						continue;
					}
					int kod = k.klasyfikuj(s, x, z);
					double o = s.region().oceanicznosc();
					double p = s.region().podgorskosc();
					Biom b = Siedlisko.biom(kod);
					l.incrementAndGet(KOLUMNY);
					l.addAndGet(Z_BUKIEM, Siedlisko.ma(kod, Gatunek.BUK) ? 1 : 0);
					l.addAndGet(Z_JODLA, Siedlisko.ma(kod, Gatunek.JODLA) ? 1 : 0);
					l.addAndGet(ZE_SWIERKIEM, Siedlisko.ma(kod, Gatunek.SWIERK) ? 1 : 0);
					if (o < 0.35 && p < 0.35) {
						l.addAndGet(BUK, Siedlisko.ma(kod, Gatunek.BUK) ? 1 : 0);
						l.addAndGet(BUCZYNY, b == Biom.BUCZYNA_NIZINNA || b == Biom.BUCZYNA_GORSKA ? 1 : 0);
					}
					if (p < 0.45) {
						l.addAndGet(JODLA, Siedlisko.ma(kod, Gatunek.JODLA) ? 1 : 0);
						l.addAndGet(JEDLINA, b == Biom.JEDLINA_WYZYNNA ? 1 : 0);
					}
					if (o > 0.35 && p < 0.45) {
						l.addAndGet(SWIERK, Siedlisko.ma(kod, Gatunek.SWIERK) ? 1 : 0);
					}
				}
			}
		});
		return l;
	}

	static void wypisz(String nazwa, AtomicLongArray l) {
		double n = l.get(KOLUMNY);
		System.out.printf(Locale.ROOT, "%s: %d kolumn lądu; buk w zasięgu %.1f%%, jodła %.1f%%, świerk naturalny %.1f%%%n", nazwa,
				l.get(KOLUMNY), 100 * l.get(Z_BUKIEM) / n, 100 * l.get(Z_JODLA) / n, 100 * l.get(ZE_SWIERKIEM) / n);
	}

	static void sprawdzZera(String nazwa, AtomicLongArray l) {
		assertEquals(0, l.get(BUK), nazwa + ": buk przy O < 0,35 i P < 0,35");
		assertEquals(0, l.get(BUCZYNY), nazwa + ": buczyny przy O < 0,35 i P < 0,35");
		assertEquals(0, l.get(JODLA), nazwa + ": jodła przy P < 0,45");
		assertEquals(0, l.get(JEDLINA), nazwa + ": jedlina przy P < 0,45");
		assertEquals(0, l.get(SWIERK), nazwa + ": świerk naturalny przy O > 0,35 i P < 0,45");
		// Zasięgi nie mogą być puste: buk, jodła i świerk naturalny rosną gdzieś w świecie.
		assertTrue(l.get(Z_BUKIEM) > 0 && l.get(Z_JODLA) > 0 && l.get(ZE_SWIERKIEM) > 0, nazwa + ": pusty zasięg");
	}

	@Test
	void zasiegiWSkaliRealnej() {
		for (long seed : BiomeSharesTest.ZIARNA) {
			AtomicLongArray l = sprawdz(seed, LandscapeScale.REALISTIC, 1_000_000, 800);
			wypisz("REAL, ziarno " + seed, l);
			sprawdzZera("REAL, ziarno " + seed, l);
		}
	}

	@Test
	void zasiegiWSkaliRozgrywki() {
		AtomicLongArray l = sprawdz(20260927L, LandscapeScale.GAMEPLAY, 30_000, 800);
		wypisz("GAMEPLAY", l);
		// W 30 × 30 km skali rozgrywki (ok. 1400 km·zs w REAL) pola regionalne zmieniają się mało, więc
		// sprawdzamy tylko zera (zasięg może być w całości po jednej stronie progu).
		assertEquals(0, l.get(BUK));
		assertEquals(0, l.get(BUCZYNY));
		assertEquals(0, l.get(JODLA));
		assertEquals(0, l.get(JEDLINA));
		assertEquals(0, l.get(SWIERK));
	}
}
