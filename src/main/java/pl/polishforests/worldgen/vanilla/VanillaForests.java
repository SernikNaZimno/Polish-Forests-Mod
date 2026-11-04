package pl.polishforests.worldgen.vanilla;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.LandCover;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * The world type "Default + Polish Forests" (step W1 of docs/04-pionowy-wycinek.md, decision G.36 U-3): which vanilla
 * biomes are forests replaced by a habitat biome of the mod ({@link #REPLACEMENTS}, the table of docs/00 G.40), and the
 * habitat code a column of each biome carries in the chunk habitats of that world (pure Java, no game classes, so the
 * unit tests check the table without the registries).
 *
 * <p>A replaced column carries the canonical code of its habitat biome ({@link #code}): no zone, the fresh site type of
 * the biome, the typical association, the biome's soil and the range flags of the species the forest has in Poland
 * ({@link #flags}; the species ramp is full in that world, {@link #OCEANICITY}, {@link #MOUNTAIN_INFLUENCE}). A column of
 * any other vanilla biome is "foreign": its code ({@link #foreignCode}) only tells the forest edges of the mod what lies
 * beyond them (open land, a river, the sea, a beach), and the mod places nothing on it (the blocked mask of
 * {@code ChunkHabitats}).
 */
public final class VanillaForests {
	/**
	 * Vanilla forest biome (path in the {@code minecraft} namespace) → habitat biome of the mod. The forests of
	 * {@link #KEPT} stay vanilla (W1-a).
	 */
	public static final Map<String, HabitatBiome> REPLACEMENTS = replacements();

	/**
	 * Vanilla forests and wooded biomes that are not replaced (W1-a): the jungles and the mangrove swamp (tropical), the
	 * pale garden, the cherry grove and the grove (no Polish counterpart), and the dappled forest of 26.3 (an autumn poplar
	 * forest away from the rivers: the poplars of the mod grow in the floodplain willow-poplar forest only).
	 */
	public static final Set<String> KEPT = Set.of("jungle", "sparse_jungle", "bamboo_jungle", "mangrove_swamp", "pale_garden",
			"cherry_grove", "grove", "dappled_forest");

	/** Oceanicity O of the chunk habitats of that world: between the beech and spruce thresholds (the flags decide). */
	public static final float OCEANICITY = 0.45F;
	/**
	 * Mountain influence P of the chunk habitats: high enough that the range ramp of every flagged species is full
	 * ({@code SpeciesRamp}: beech, fir and spruce from P 0.40–0.50 plus the ramp width 0.10 and the jitter 0.05); the
	 * glacial erratics (P ≤ 0.3) do not appear in that world.
	 */
	public static final float MOUNTAIN_INFLUENCE = 0.65F;

	private static final Map<HabitatBiome, Integer> CODES = codes();

	private VanillaForests() {
	}

	private static Map<String, HabitatBiome> replacements() {
		Map<String, HabitatBiome> m = new LinkedHashMap<>();
		m.put("forest", HabitatBiome.OAK_HORNBEAM_FOREST);
		m.put("flower_forest", HabitatBiome.OAK_HORNBEAM_FOREST);
		m.put("birch_forest", HabitatBiome.MIXED_FOREST);
		m.put("old_growth_birch_forest", HabitatBiome.MIXED_FOREST);
		m.put("dark_forest", HabitatBiome.LOWLAND_BEECH_FOREST);
		m.put("taiga", HabitatBiome.MIXED_PINE_FOREST);
		m.put("old_growth_pine_taiga", HabitatBiome.FRESH_PINE_FOREST);
		m.put("old_growth_spruce_taiga", HabitatBiome.MONTANE_SPRUCE_FOREST);
		m.put("snowy_taiga", HabitatBiome.MONTANE_SPRUCE_FOREST);
		m.put("windswept_forest", HabitatBiome.MONTANE_BEECH_FOREST);
		m.put("swamp", HabitatBiome.ALDER_CARR);
		return Collections.unmodifiableMap(m);
	}

	/** The habitat biome replacing the vanilla biome with this path, or null when the biome stays. */
	public static @Nullable HabitatBiome replacement(String vanillaPath) {
		return REPLACEMENTS.get(vanillaPath);
	}

	/** Whether a habitat biome replaces some vanilla forest in that world. */
	public static boolean present(HabitatBiome biome) {
		return CODES.containsKey(biome);
	}

	/**
	 * Range flags of the species the forest carries in that world: hornbeam in the oak-hornbeam, mixed and beech forests,
	 * beech in the beech forests, spruce in the mixed pine-oak forest (the boreal-like Polish taiga, NE Poland) and the
	 * mountain forests, fir in the mountain forests.
	 */
	static int flags(HabitatBiome biome) {
		return switch (biome) {
			case OAK_HORNBEAM_FOREST, MIXED_FOREST -> Species.FLAG_HORNBEAM;
			case LOWLAND_BEECH_FOREST -> Species.FLAG_BEECH | Species.FLAG_HORNBEAM;
			case MIXED_PINE_FOREST -> Species.FLAG_SPRUCE;
			case MONTANE_SPRUCE_FOREST -> Species.FLAG_SPRUCE | Species.FLAG_FIR;
			case MONTANE_BEECH_FOREST -> Species.FLAG_BEECH | Species.FLAG_FIR | Species.FLAG_SPRUCE;
			default -> 0;
		};
	}

	/** Forest site type of the canonical code (the fresh sites of the slice forests as {@code ForestBlend.code}). */
	static ForestSiteType siteType(HabitatBiome biome) {
		return switch (biome) {
			case FRESH_PINE_FOREST -> ForestSiteType.FRESH_CONIFEROUS;
			case MIXED_PINE_FOREST -> ForestSiteType.FRESH_MIXED_CONIFEROUS;
			case MIXED_FOREST -> ForestSiteType.FRESH_MIXED_BROADLEAVED;
			case OAK_HORNBEAM_FOREST, LOWLAND_BEECH_FOREST -> ForestSiteType.FRESH_BROADLEAVED;
			case MONTANE_SPRUCE_FOREST -> ForestSiteType.MOUNTAIN_CONIFEROUS;
			case MONTANE_BEECH_FOREST -> ForestSiteType.MOUNTAIN_BROADLEAVED;
			case ALDER_CARR -> ForestSiteType.ALDER_SWAMP;
			default -> ForestSiteType.NONE;
		};
	}

	private static Map<HabitatBiome, Integer> codes() {
		Map<HabitatBiome, Integer> m = new EnumMap<>(HabitatBiome.class);
		for (HabitatBiome b : REPLACEMENTS.values()) {
			m.put(b, Habitat.pack(b, Zone.NONE, siteType(b), Association.TYPICAL, LandCover.forBiome(b), flags(b),
					Soil.forBiome(b, Zone.NONE)));
		}
		return m;
	}

	/** The canonical code of a habitat biome of {@link #REPLACEMENTS}. */
	public static int code(HabitatBiome biome) {
		Integer c = CODES.get(biome);
		if (c == null) {
			throw new IllegalArgumentException(biome + " replaces no vanilla forest");
		}
		return c;
	}

	/** What a foreign column stands for at the forest edges of the mod (its code, {@link #foreignCode}). */
	public enum Foreign {
		/** Any land: plains, meadows, deserts, mountains, the vanilla forests that stay. */
		LAND(HabitatBiome.HAY_MEADOW),
		RIVER(HabitatBiome.RIVER),
		SEA(HabitatBiome.SEA),
		BEACH(HabitatBiome.BEACH);

		private final HabitatBiome biome;

		Foreign(HabitatBiome biome) {
			this.biome = biome;
		}

		public HabitatBiome biome() {
			return biome;
		}
	}

	private static final int[] FOREIGN_CODES = foreignCodes();

	private static int[] foreignCodes() {
		int[] c = new int[Foreign.values().length];
		for (Foreign f : Foreign.values()) {
			HabitatBiome b = f.biome();
			c[f.ordinal()] = Habitat.pack(b, Zone.NONE, ForestSiteType.NONE, Association.TYPICAL, LandCover.forBiome(b), 0,
					Soil.forBiome(b, Zone.NONE));
		}
		return c;
	}

	/** The code of a foreign column of this kind. */
	public static int foreignCode(Foreign kind) {
		return FOREIGN_CODES[kind.ordinal()];
	}

	/**
	 * The kind of a vanilla biome that is not replaced, from its tags ({@code is_river}, {@code is_ocean},
	 * {@code is_beach}) and, without them, from its path.
	 */
	public static Foreign foreign(String path, boolean river, boolean ocean, boolean beach) {
		if (river || path.contains("river")) {
			return Foreign.RIVER;
		}
		if (ocean || path.contains("ocean")) {
			return Foreign.SEA;
		}
		if (beach || path.contains("beach") || path.equals("stony_shore")) {
			return Foreign.BEACH;
		}
		return Foreign.LAND;
	}
}
