package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.LandscapeType;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Biome shares (docs/03-m2-biomy.md §12.1). NATURAL mode, "natural vegetation": land forest cover ≥ 88%, and on the
 * outwash plain (interior of the type, weight ≥ 0.9) pine forests ≥ 85% of the forest. The pine share of the forest in the
 * whole OUTWASH_PLAIN type (with the blending belts towards the moraine plateau) is only printed, for the decision at
 * checkpoint 1. PRESENT_DAY mode, "present-day Poland" (step S8): land forest cover 26–34%, pine forests 45–58% of the
 * forest, fresh site types 50–70% of the forest, alder carrs and riparian forests 3–6% of the forest, and the forest
 * cover of each landscape type (interior, weight ≥ 0.9) within {@link #TYPE_COVER}.
 * Samples: 200k columns per scale (3 seeds), in clusters of 8 × 8 every 125 m·k
 * scattered randomly (deterministically) over a 1000 × 1000 km square in REAL and 30 × 30 km in GAMEPLAY.
 * The clusters reuse the river network and terrain grid tiles, so the test is several times faster than
 * with fully scattered points, while the shares over the area stay unbiased. Both modes are counted on the same samples.
 */
@Tag("slow")
public class BiomeSharesTest {
	static final long[] SEEDS = {20260927L, 1L, 2L};
	/** Type weight from which a column counts as the interior of its landscape type. */
	static final double TYPE_INTERIOR = 0.9;
	/** OUTWASH_PLAIN type weight from which a column counts as the interior of the outwash plain. */
	static final double OUTWASH_PLAIN_INTERIOR = TYPE_INTERIOR;
	/** Land types (without the sea). */
	static final LandscapeType[] LAND_TYPES = {LandscapeType.OUTWASH_PLAIN, LandscapeType.MORAINE_PLATEAU,
			LandscapeType.OLD_GLACIAL_PLAIN, LandscapeType.FOOTHILLS, LandscapeType.BESKIDS, LandscapeType.COASTLAND};
	/**
	 * Forest cover of the interior of each land type in the PRESENT_DAY mode, {min, max} (docs/03-m2-biomy.md §3.4.1,
	 * "Stan po S8": outwash plain and moraine plateau from the ecology report, the others from the forest cover of the
	 * provinces in docs/research/05-krajobrazy-polski-geografia.md §6–7), in the order of {@link #LAND_TYPES}.
	 */
	static final double[][] TYPE_COVER = {{0.55, 0.75}, {0.15, 0.30}, {0.18, 0.35}, {0.25, 0.45}, {0.50, 0.80},
			{0.10, 0.40}};
	/** Least number of interior land columns of a type for its forest cover to be checked. */
	static final long TYPE_MIN_COLUMNS = 2_000;
	/**
	 * The three fresh site types Bśw, BMśw and LMśw, about 60% of the forests of Poland (docs/research/06 §1.3; Lśw, about 9%,
	 * is counted with the broadleaved sites).
	 */
	static final Set<ForestSiteType> FRESH = EnumSet.of(ForestSiteType.FRESH_CONIFEROUS, ForestSiteType.FRESH_MIXED_CONIFEROUS,
			ForestSiteType.FRESH_MIXED_BROADLEAVED);
	/** Alder carrs and riparian forests (lowland and mountain). */
	static final Set<HabitatBiome> WET_FORESTS = EnumSet.of(HabitatBiome.ALDER_CARR, HabitatBiome.ASH_ALDER_FOREST,
			HabitatBiome.WILLOW_POPLAR_FOREST, HabitatBiome.ELM_ASH_FOREST, HabitatBiome.GRAY_ALDER_FOREST);

	/** Share counters: columns by biome (all land and water) and by biome on the outwash plain. */
	public static final class Shares {
		public final long[] biome = new long[HabitatBiome.values().length];
		public final long[] biomeOutwashPlain = new long[HabitatBiome.values().length];
		/** Columns of the OUTWASH_PLAIN type without the weight condition (with the blending belts). */
		public final long[] biomeOutwashPlainAll = new long[HabitatBiome.values().length];
		public final long[] zone = new long[Zone.values().length];
		/** Site types of the forest columns. */
		public final long[] forestSite = new long[ForestSiteType.values().length];
		/** Land and forest columns of the interior of each land type ({@link #LAND_TYPES}). */
		public final long[] typeLand = new long[LAND_TYPES.length];
		public final long[] typeForest = new long[LAND_TYPES.length];
		/** Pine forest columns of the interior of each land type. */
		public final long[] typePine = new long[LAND_TYPES.length];
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
			for (int i = 0; i < forestSite.length; i++) {
				forestSite[i] += u.forestSite[i];
			}
			for (int i = 0; i < typeLand.length; i++) {
				typeLand[i] += u.typeLand[i];
				typeForest[i] += u.typeForest[i];
				typePine[i] += u.typePine[i];
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

		/** Forest cover of the interior of the land type {@code LAND_TYPES[t]}. */
		public double forestCover(int t) {
			return typeLand[t] == 0 ? Double.NaN : (double) typeForest[t] / typeLand[t];
		}

		/** Pine forest share of the forest in the outwash plain interior. */
		public double pineShareOutwashPlain() {
			return pineForests(biomeOutwashPlain);
		}

		/** Pine forest share of the forest in the whole OUTWASH_PLAIN type. */
		public double pineShareOutwashPlainAll() {
			return pineForests(biomeOutwashPlainAll);
		}

		/** Pine forest share of the whole forest. */
		public double pineShare() {
			return pineForests(biome);
		}

		/** Share of the fresh lowland site types ({@link #FRESH}) in the forest. */
		public double freshShare() {
			long forest = 0;
			long fresh = 0;
			for (ForestSiteType s : ForestSiteType.values()) {
				forest += forestSite[s.ordinal()];
				fresh += FRESH.contains(s) ? forestSite[s.ordinal()] : 0;
			}
			return forest == 0 ? Double.NaN : (double) fresh / forest;
		}

		/** Share of the alder carrs and riparian forests ({@link #WET_FORESTS}) in the forest. */
		public double wetForestShare() {
			long forest = 0;
			long wet = 0;
			for (HabitatBiome b : HabitatBiome.values()) {
				if (b.isForest()) {
					forest += biome[b.ordinal()];
					wet += WET_FORESTS.contains(b) ? biome[b.ordinal()] : 0;
				}
			}
			return forest == 0 ? Double.NaN : (double) wet / forest;
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

		/** Counts one column with its sample and habitat code. */
		void count(ColumnSample s, int code) {
			HabitatBiome biomeOf = Habitat.biome(code);
			int b = biomeOf.ordinal();
			biome[b]++;
			zone[Habitat.zone(code).ordinal()]++;
			if (biomeOf.isForest()) {
				forestSite[Habitat.siteType(code).ordinal()]++;
			}
			// Outwash plain: interior of the type (weight ≥ 0.9), without the blending belts towards the moraine
			// plateau, which in GAMEPLAY (regions about 1.4 km) take up a large part of the outwash plain.
			if (s.type() == LandscapeType.OUTWASH_PLAIN) {
				biomeOutwashPlainAll[b]++;
				if (s.terrain().wOutwashPlain() >= OUTWASH_PLAIN_INTERIOR) {
					biomeOutwashPlain[b]++;
				}
			}
			if (!biomeOf.isWater()) {
				for (int t = 0; t < LAND_TYPES.length; t++) {
					if (s.type() == LAND_TYPES[t] && typeWeight(s, LAND_TYPES[t]) >= TYPE_INTERIOR) {
						typeLand[t]++;
						typeForest[t] += biomeOf.isForest() ? 1 : 0;
						typePine[t] += biomeOf.isPineForest() ? 1 : 0;
					}
				}
			}
			columns++;
		}
	}

	/** Weight of the landscape type in the column (the coastland belt has its own weight). */
	static double typeWeight(ColumnSample s, LandscapeType type) {
		ColumnSample.Terrain t = s.terrain();
		double coast = Math.clamp(t.wCoastland(), 0.0, 1.0);
		return switch (type) {
			case OUTWASH_PLAIN -> (1 - coast) * t.wOutwashPlain();
			case MORAINE_PLATEAU -> (1 - coast) * t.wMorainePlateau();
			case OLD_GLACIAL_PLAIN -> (1 - coast) * t.wOldGlacialPlain();
			case FOOTHILLS -> (1 - coast) * t.wFoothills();
			case BESKIDS -> (1 - coast) * t.wBeskids();
			case COASTLAND -> coast;
			default -> 0;
		};
	}

	/**
	 * Shares in a square with a side of {@code sideLength} m around (0, 0): {@code clusters} clusters of 64 columns.
	 */
	public static Shares computeShares(long seed, LandscapeScale scale, HabitatClassifier.Mode mode, double sideLength, int clusters) {
		return computeShares(seed, scale, sideLength, clusters, mode)[0];
	}

	/** {@link #computeShares(long, LandscapeScale, HabitatClassifier.Mode, double, int)} for several modes on the same samples. */
	public static Shares[] computeShares(long seed, LandscapeScale scale, double sideLength, int clusters, HabitatClassifier.Mode... modes) {
		LandscapeModel m = new LandscapeModel(seed, scale, 1.0);
		HabitatClassifier[] k = new HabitatClassifier[modes.length];
		Shares[] result = new Shares[modes.length];
		for (int i = 0; i < modes.length; i++) {
			k[i] = new HabitatClassifier(seed, scale, modes[i]);
			result[i] = new Shares();
		}
		double step = 125 * scale.local();
		IntStream.range(0, clusters).parallel().forEach(q -> {
			Shares[] u = new Shares[modes.length];
			for (int i = 0; i < modes.length; i++) {
				u[i] = new Shares();
			}
			long h = Noise.mix(seed * 31 + q);
			double cx = ((h >>> 11) * 0x1.0p-53 - 0.5) * sideLength;
			double cz = ((Noise.mix(h) >>> 11) * 0x1.0p-53 - 0.5) * sideLength;
			for (int j = 0; j < 8; j++) {
				for (int i = 0; i < 8; i++) {
					double x = cx + i * step;
					double z = cz + j * step;
					ColumnSample s = m.sample(x, z);
					for (int n = 0; n < modes.length; n++) {
						u[n].count(s, k[n].classify(s, x, z));
					}
				}
			}
			for (int n = 0; n < modes.length; n++) {
				result[n].add(u[n]);
			}
		});
		return result;
	}

	/** 200k columns per scale: 3 seeds with 1042 clusters of 64 columns each; [0] NATURAL, [1] PRESENT_DAY. */
	static Shares[] total(LandscapeScale scale) {
		double sideLength = scale == LandscapeScale.REALISTIC ? 1_000_000 : 30_000;
		Shares[] sum = {new Shares(), new Shares()};
		for (long seed : SEEDS) {
			Shares[] u = computeShares(seed, scale, sideLength, 1_042, HabitatClassifier.Mode.NATURAL, HabitatClassifier.Mode.PRESENT_DAY);
			sum[0].add(u[0]);
			sum[1].add(u[1]);
		}
		return sum;
	}

	static void print(String label, Shares u) {
		System.out.printf(Locale.ROOT, "%s: %d columns, land %d; forest cover %.1f%%, on the outwash plain %.1f%%, pine forests in the outwash plain interior forest "
				+ "%.1f%% (whole OUTWASH_PLAIN type %.1f%%); in the whole forest pine forests %.1f%%, fresh site types %.1f%%, alder carrs and "
				+ "riparian forests %.1f%%%n", label, u.columns, u.land(), 100 * u.forestCover(), 100 * u.forestCoverOutwashPlain(),
				100 * u.pineShareOutwashPlain(), 100 * u.pineShareOutwashPlainAll(), 100 * u.pineShare(), 100 * u.freshShare(),
				100 * u.wetForestShare());
		StringBuilder types = new StringBuilder("  forest cover of the type interiors:");
		for (int t = 0; t < LAND_TYPES.length; t++) {
			types.append(String.format(Locale.ROOT, " %s %.1f%% (%d columns, pine forests %.0f%% of its forest);", LAND_TYPES[t],
					100 * u.forestCover(t), u.typeLand[t], 100.0 * u.typePine[t] / Math.max(1, u.typeForest[t])));
		}
		System.out.println(types);
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
		StringBuilder sites = new StringBuilder("  site types of the forest:");
		long forest = 0;
		for (long v : u.forestSite) {
			forest += v;
		}
		for (ForestSiteType s : ForestSiteType.values()) {
			if (u.forestSite[s.ordinal()] > 0) {
				sites.append(String.format(Locale.ROOT, " %s %.1f%%;", s.code(), 100.0 * u.forestSite[s.ordinal()] / Math.max(1, forest)));
			}
		}
		System.out.println(sites);
	}

	/** Checks of the NATURAL mode. */
	static List<String> naturalFailures(Shares u) {
		List<String> f = new ArrayList<>();
		if (!(u.forestCover() >= 0.88)) {
			f.add("forest cover " + u.forestCover());
		}
		if (!(u.pineShareOutwashPlain() >= 0.85)) {
			f.add("pine forests in the outwash plain forest " + u.pineShareOutwashPlain());
		}
		return f;
	}

	/** Checks of the PRESENT_DAY mode. */
	static List<String> presentDayFailures(Shares u) {
		List<String> f = new ArrayList<>();
		check(f, "forest cover", u.forestCover(), 0.26, 0.34);
		check(f, "pine forests in the forest", u.pineShare(), 0.45, 0.58);
		check(f, "fresh site types in the forest", u.freshShare(), 0.50, 0.70);
		check(f, "alder carrs and riparian forests in the forest", u.wetForestShare(), 0.03, 0.06);
		for (int t = 0; t < LAND_TYPES.length; t++) {
			if (u.typeLand[t] >= TYPE_MIN_COLUMNS) {
				check(f, "forest cover of " + LAND_TYPES[t], u.forestCover(t), TYPE_COVER[t][0], TYPE_COVER[t][1]);
			}
		}
		return f;
	}

	private static void check(List<String> failures, String what, double v, double min, double max) {
		if (!(v >= min && v <= max)) {
			failures.add(String.format(Locale.ROOT, "%s %.1f%% outside %.0f–%.0f%%", what, 100 * v, 100 * min, 100 * max));
		}
	}

	private static void run(LandscapeScale scale) {
		Shares[] u = total(scale);
		String name = scale == LandscapeScale.REALISTIC ? "REAL" : "GAMEPLAY";
		print(name + ", NATURAL mode", u[0]);
		print(name + ", PRESENT_DAY mode", u[1]);
		List<String> failures = naturalFailures(u[0]);
		failures.addAll(presentDayFailures(u[1]));
		assertTrue(failures.isEmpty(), name + ": " + failures);
	}

	@Test
	void sharesAtRealisticScale() {
		run(LandscapeScale.REALISTIC);
	}

	@Test
	void sharesAtGameplayScale() {
		run(LandscapeScale.GAMEPLAY);
	}
}
