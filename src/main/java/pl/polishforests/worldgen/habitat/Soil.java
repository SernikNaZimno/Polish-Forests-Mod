package pl.polishforests.worldgen.habitat;

/**
 * Soil of a column (docs/03-m2-biomy.md §7.4). Surface blocks are chosen by {@code SoilBlocks} in S6; here only
 * the type, picked from the zone, or from the biome when the zone has no soil of its own. At most 32 entries.
 */
public enum Soil {
	INITIAL_PODZOL,
	PODZOL,
	RUSTY_SOIL,
	SANDY_GLEYSOL,
	BOG_PEAT,
	FEN_PEAT,
	ACID_BROWN_SOIL,
	BROWN_SOIL,
	BEECH_BROWN_SOIL,
	MUCK,
	LIGHT_ALLUVIAL_SOIL,
	HEAVY_ALLUVIAL_SOIL,
	GRAVELLY_ALLUVIAL_SOIL,
	MOUNTAIN_BROWN_SOIL,
	MOUNTAIN_PODZOL,
	RANKER,
	DUNE_SAND,
	BEACH_SAND,
	/** Bare till of a cliff face. */
	TILL,
	CHANNEL_BED,
	STREAM_BED,
	LAKE_BED,
	DYSTROPHIC_LAKE_BED,
	SEA_BED,
	LAGOON_BED;

	private static final Soil[] VALUES = values();

	public static Soil of(int ordinal) {
		return VALUES[ordinal];
	}

	/** Soil for a biome and zone. */
	public static Soil forBiome(HabitatBiome b, Zone s) {
		switch (s) {
			case POINT_BAR, WILLOW_SCRUB -> {
				return b.isWater() ? defaultForBiome(b) : LIGHT_ALLUVIAL_SOIL;
			}
			case GRAVEL_BAR -> {
				return GRAVELLY_ALLUVIAL_SOIL;
			}
			case WILLOW_CARR, SHORE_REEDBED -> {
				return FEN_PEAT;
			}
			case FLOATING_MAT -> {
				return b.isWater() ? DYSTROPHIC_LAKE_BED : BOG_PEAT;
			}
			case SPRING_AREA -> {
				return MUCK;
			}
			case CLIFF_FACE -> {
				return TILL;
			}
			case STRANDLINE -> {
				return Soil.BEACH_SAND;
			}
			case EMBRYO_DUNE -> {
				return DUNE_SAND;
			}
			default -> {
				return defaultForBiome(b);
			}
		}
	}

	private static Soil defaultForBiome(HabitatBiome b) {
		return switch (b) {
			case DRY_PINE_FOREST, HEATH -> INITIAL_PODZOL;
			case FRESH_PINE_FOREST, COASTAL_PINE_FOREST -> PODZOL;
			case MIXED_PINE_FOREST -> RUSTY_SOIL;
			case MOIST_PINE_FOREST -> SANDY_GLEYSOL;
			case RAISED_BOG, BOG_WOODLAND -> BOG_PEAT;
			case ALDER_CARR, FEN, REEDBED -> FEN_PEAT;
			case MIXED_FOREST, UPLAND_FIR_FOREST -> ACID_BROWN_SOIL;
			case OAK_HORNBEAM_FOREST, HAY_MEADOW, ARABLE_LAND -> BROWN_SOIL;
			case LOWLAND_BEECH_FOREST -> BEECH_BROWN_SOIL;
			case ASH_ALDER_FOREST -> MUCK;
			case WILLOW_POPLAR_FOREST, WILLOW_SCRUB -> LIGHT_ALLUVIAL_SOIL;
			case ELM_ASH_FOREST, WET_MEADOW -> HEAVY_ALLUVIAL_SOIL;
			case GRAY_ALDER_FOREST -> GRAVELLY_ALLUVIAL_SOIL;
			case MONTANE_BEECH_FOREST -> MOUNTAIN_BROWN_SOIL;
			case MONTANE_SPRUCE_FOREST -> MOUNTAIN_PODZOL;
			case DWARF_PINE_SCRUB, ALPINE_GRASSLAND -> RANKER;
			case WHITE_DUNE, GRAY_DUNE -> DUNE_SAND;
			case BEACH -> Soil.BEACH_SAND;
			case RIVER -> CHANNEL_BED;
			case STREAM -> STREAM_BED;
			case LAKE -> LAKE_BED;
			case DYSTROPHIC_LAKE -> DYSTROPHIC_LAKE_BED;
			case SEA -> SEA_BED;
			case LAGOON -> LAGOON_BED;
		};
	}
}
