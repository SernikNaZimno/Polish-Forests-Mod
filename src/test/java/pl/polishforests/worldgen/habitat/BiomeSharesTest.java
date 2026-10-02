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
 * Biome shares in the NATURAL mode, "natural vegetation" (docs/03-m2-biomy.md §12.1): land forest cover ≥ 88%, and on
 * the outwash plain (interior of the type, weight ≥ 0.9) pine forests ≥ 85% of the forest. The pine share of the forest in the whole
 * OUTWASH_PLAIN type (with the blending belts towards the moraine plateau) is only printed, for the decision at checkpoint 1.
 * Samples: 200k columns per scale (3 seeds), in clusters of 8 × 8 every 125 m·k
 * scattered randomly (deterministically) over a 1000 × 1000 km square in REAL and 30 × 30 km in GAMEPLAY.
 * The clusters reuse the river network and terrain grid tiles, so the test is several times faster than
 * with fully scattered points, while the shares over the area stay unbiased.
 */
public class BiomeSharesTest {
	static final long[] SEEDS = {20260927L, 1L, 2L};
	/** OUTWASH_PLAIN type weight from which a column counts as the interior of the outwash plain. */
	static final double OUTWASH_PLAIN_INTERIOR = 0.9;

	/** Share counters: columns by biome (all land and water) and by biome on the outwash plain. */
	public static final class Shares {
		public final long[] biome = new long[HabitatBiome.values().length];
		public final long[] biomeOutwashPlain = new long[HabitatBiome.values().length];
		/** Columns of the OUTWASH_PLAIN type without the weight condition (with the blending belts). */
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

		/** Forest share of the land (forest biomes / non-water biomes). */
		public double forestCover() {
			return forestCover(biome);
		}

		public double forestCoverOutwashPlain() {
			return forestCover(biomeOutwashPlain);
		}

		/** Pine forest share of the forest in the outwash plain interior. */
		public double pineShareOutwashPlain() {
			return pineForests(biomeOutwashPlain);
		}

		/** Pine forest share of the forest in the whole OUTWASH_PLAIN type. */
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
	 * Shares in a square with a side of {@code sideLength} m around (0, 0): {@code clusters} clusters of 64 columns.
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
					// Outwash plain: interior of the type (weight ≥ 0.9), without the blending belts towards the moraine
					// plateau, which in GAMEPLAY (regions about 1.4 km) take up a large part of the outwash plain.
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

	/** 200k columns per scale: 3 seeds with 1042 clusters of 64 columns each. */
	static Shares total(LandscapeScale scale, HabitatClassifier.Mode mode) {
		double sideLength = scale == LandscapeScale.REALISTIC ? 1_000_000 : 30_000;
		Shares sum = new Shares();
		for (long seed : SEEDS) {
			sum.add(computeShares(seed, scale, mode, sideLength, 1_042));
		}
		return sum;
	}

	static void print(String label, Shares u) {
		System.out.printf(Locale.ROOT, "%s: %d columns, land %d; forest cover %.1f%%, on the outwash plain %.1f%%, pine forests in the outwash plain interior forest "
				+ "%.1f%% (whole OUTWASH_PLAIN type %.1f%%)%n", label, u.columns, u.land(), 100 * u.forestCover(), 100 * u.forestCoverOutwashPlain(),
				100 * u.pineShareOutwashPlain(), 100 * u.pineShareOutwashPlainAll());
		long outwashPlain = 0;
		for (long v : u.biomeOutwashPlain) {
			outwashPlain += v;
		}
		System.out.println("  biome                         all  outwash");
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
		print("REAL, NATURAL mode", u);
		assertTrue(u.forestCover() >= 0.88, "forest cover " + u.forestCover());
		assertTrue(u.pineShareOutwashPlain() >= 0.85, "pine forests in the outwash plain forest " + u.pineShareOutwashPlain());
	}

	@Test
	void naturalVegetationAtGameplayScale() {
		Shares u = total(LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.NATURAL);
		print("GAMEPLAY, NATURAL mode", u);
		assertTrue(u.forestCover() >= 0.88, "forest cover " + u.forestCover());
		assertTrue(u.pineShareOutwashPlain() >= 0.85, "pine forests in the outwash plain forest " + u.pineShareOutwashPlain());
	}
}
