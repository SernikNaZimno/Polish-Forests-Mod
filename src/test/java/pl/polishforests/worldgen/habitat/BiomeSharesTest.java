package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.LandscapeType;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Udziały biomów w trybie N „roślinność naturalna” (docs/03-m2-biomy.md §12.1): lesistość lądu ≥ 88%, a na
 * sandrze (wnętrze typu, waga ≥ 0,9) bory ≥ 85% lasu. Udział borów w lesie całego typu SANDR (z pasami
 * mieszania z wysoczyzną) jest tylko wypisywany, do decyzji w punkcie kontrolnym 1. Próbki: 200 tys. kolumn na skalę (3 ziarna), w skupiskach 8 × 8 co 125 m·k
 * rozrzuconych losowo (deterministycznie) po kwadracie 1000 × 1000 km w REAL i 30 × 30 km w GAMEPLAY.
 * Skupiska wykorzystują kafle sieci rzecznej i siatki terenu, więc test jest kilkakrotnie szybszy niż
 * przy punktach całkiem rozrzuconych, a udziały w obszarze zostają nieobciążone.
 */
public class BiomeSharesTest {
	static final long[] SEEDS = {20260927L, 1L, 2L};
	/** Waga typu SANDR, od której kolumna liczy się do wnętrza sandru. */
	static final double OUTWASH_PLAIN_INTERIOR = 0.9;

	/** Liczniki udziałów: kolumny według biomu (cały ląd i woda) i według biomu na sandrze. */
	public static final class Shares {
		public final long[] biome = new long[HabitatBiome.values().length];
		public final long[] biomeOutwashPlain = new long[HabitatBiome.values().length];
		/** Kolumny typu SANDR bez warunku wagi (z pasami mieszania). */
		public final long[] biomeOutwashPlainAll = new long[HabitatBiome.values().length];
		public final long[] zone = new long[Zone.values().length];
		public long columns;

		public synchronized void add(Shares u) {
			for (int i = 0; i < biome.length; i++) {
				biome[i] += u.biome[i];
				biomeOutwashPlain[i] += u.biomeOutwashPlain[i];
				biomeOutwashPlainAll[i] += u.biomeOutwashPlainAll[i];
			}
			for (int i = 0; i < zone.length; i++) {
				zone[i] += u.zone[i];
			}
			columns += u.columns;
		}

		/** Udział lasu w lądzie (biomy leśne / biomy niewodne). */
		public double forestCover() {
			return forestCover(biome);
		}

		public double forestCoverOutwashPlain() {
			return forestCover(biomeOutwashPlain);
		}

		/** Udział borów w lesie we wnętrzu sandru. */
		public double pineShareOutwashPlain() {
			return pineForests(biomeOutwashPlain);
		}

		/** Udział borów w lesie w całym typie SANDR. */
		public double pineShareOutwashPlainAll() {
			return pineForests(biomeOutwashPlainAll);
		}

		private static double pineForests(long[] t) {
			long forest = 0;
			long pineForests = 0;
			for (HabitatBiome b : HabitatBiome.values()) {
				if (b.isForest()) {
					forest += t[b.ordinal()];
					pineForests += b.isPineForest() ? t[b.ordinal()] : 0;
				}
			}
			return forest == 0 ? Double.NaN : (double) pineForests / forest;
		}

		private static double forestCover(long[] t) {
			long land = 0;
			long forest = 0;
			for (HabitatBiome b : HabitatBiome.values()) {
				if (!b.isWater()) {
					land += t[b.ordinal()];
					forest += b.isForest() ? t[b.ordinal()] : 0;
				}
			}
			return land == 0 ? Double.NaN : (double) forest / land;
		}

		public long land() {
			long land = 0;
			for (HabitatBiome b : HabitatBiome.values()) {
				land += b.isWater() ? 0 : biome[b.ordinal()];
			}
			return land;
		}
	}

	/**
	 * Udziały w kwadracie o boku {@code bok} m wokół (0, 0): {@code skupiska} skupisk po 64 kolumny.
	 */
	public static Shares computeShares(long seed, LandscapeScale scale, HabitatClassifier.Mode mode, double sideLength, int clusters) {
		LandscapeModel m = new LandscapeModel(seed, scale, 1.0);
		HabitatClassifier k = new HabitatClassifier(seed, scale, mode);
		Shares result = new Shares();
		double step = 125 * scale.local();
		IntStream.range(0, clusters).parallel().forEach(q -> {
			Shares u = new Shares();
			long h = Noise.mix(seed * 31 + q);
			double cx = ((h >>> 11) * 0x1.0p-53 - 0.5) * sideLength;
			double cz = ((Noise.mix(h) >>> 11) * 0x1.0p-53 - 0.5) * sideLength;
			for (int j = 0; j < 8; j++) {
				for (int i = 0; i < 8; i++) {
					double x = cx + i * step;
					double z = cz + j * step;
					ColumnSample s = m.sample(x, z);
					int code = k.classify(s, x, z);
					int b = Habitat.biome(code).ordinal();
					u.biome[b]++;
					u.zone[Habitat.zone(code).ordinal()]++;
					// Sandr: wnętrze typu (waga ≥ 0,9), bez pasów mieszania z wysoczyzną, które w GAMEPLAY
					// (regiony ok. 1,4 km) zajmują dużą część sandru.
					if (s.type() == LandscapeType.OUTWASH_PLAIN) {
						u.biomeOutwashPlainAll[b]++;
						if (s.terrain().wOutwashPlain() >= OUTWASH_PLAIN_INTERIOR) {
							u.biomeOutwashPlain[b]++;
						}
					}
					u.columns++;
				}
			}
			result.add(u);
		});
		return result;
	}

	/** 200 tys. kolumn na skalę: 3 ziarna po 1042 skupiska po 64 kolumny. */
	static Shares total(LandscapeScale scale, HabitatClassifier.Mode mode) {
		double sideLength = scale == LandscapeScale.REALISTIC ? 1_000_000 : 30_000;
		Shares sum = new Shares();
		for (long seed : SEEDS) {
			sum.add(computeShares(seed, scale, mode, sideLength, 1_042));
		}
		return sum;
	}

	static void print(String label, Shares u) {
		System.out.printf(Locale.ROOT, "%s: %d kolumn, ląd %d; lesistość %.1f%%, na sandrze %.1f%%, bory w lesie wnętrza sandru "
				+ "%.1f%% (całego typu SANDR %.1f%%)%n", label, u.columns, u.land(), 100 * u.forestCover(), 100 * u.forestCoverOutwashPlain(),
				100 * u.pineShareOutwashPlain(), 100 * u.pineShareOutwashPlainAll());
		long outwashPlain = 0;
		for (long v : u.biomeOutwashPlain) {
			outwashPlain += v;
		}
		System.out.println("  biom                      wszystkie  sandr");
		for (HabitatBiome b : HabitatBiome.values()) {
			if (u.biome[b.ordinal()] > 0) {
				System.out.printf(Locale.ROOT, "  %-24s %7.2f%% %7.2f%%%n", b.id(), 100.0 * u.biome[b.ordinal()] / u.columns,
						100.0 * u.biomeOutwashPlain[b.ordinal()] / Math.max(1, outwashPlain));
			}
		}
	}

	@Test
	void naturalVegetationAtRealisticScale() {
		Shares u = total(LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL);
		print("REAL, tryb N", u);
		assertTrue(u.forestCover() >= 0.88, "lesistość " + u.forestCover());
		assertTrue(u.pineShareOutwashPlain() >= 0.85, "bory w lesie sandru " + u.pineShareOutwashPlain());
	}

	@Test
	void naturalVegetationAtGameplayScale() {
		Shares u = total(LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.NATURAL);
		print("GAMEPLAY, tryb N", u);
		assertTrue(u.forestCover() >= 0.88, "lesistość " + u.forestCover());
		assertTrue(u.pineShareOutwashPlain() >= 0.85, "bory w lesie sandru " + u.pineShareOutwashPlain());
	}
}
