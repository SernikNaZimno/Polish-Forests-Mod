package pl.polishforests.worldgen.habitat;

/**
 * Forest site type (STL, Polish State Forests nomenclature). On non-forest sites in the NATURAL mode the field is
 * {@link #NONE}; in the PRESENT_DAY mode an unforested site keeps its potential STL. At most 32 entries.
 */
public enum ForestSiteType {
	NONE("-"),
	/** Dry coniferous forest. */
	DRY_CONIFEROUS("Bs"),
	FRESH_CONIFEROUS("Bśw"),
	MOIST_CONIFEROUS("Bw"),
	BOGGY_CONIFEROUS("Bb"),
	FRESH_MIXED_CONIFEROUS("BMśw"),
	MOIST_MIXED_CONIFEROUS("BMw"),
	BOGGY_MIXED_CONIFEROUS("BMb"),
	FRESH_MIXED_BROADLEAVED("LMśw"),
	MOIST_MIXED_BROADLEAVED("LMw"),
	BOGGY_MIXED_BROADLEAVED("LMb"),
	FRESH_BROADLEAVED("Lśw"),
	MOIST_BROADLEAVED("Lw"),
	/** Alder swamp forest. */
	ALDER_SWAMP("Ol"),
	/** Ash-alder swamp forest (ash-alder riparian forest). */
	ASH_ALDER_SWAMP("OlJ"),
	/** Riparian forest (riverside floodplain forests). */
	RIPARIAN("Lł"),
	UPLAND_MIXED_CONIFEROUS("BMwyż"),
	UPLAND_MIXED_BROADLEAVED("LMwyż"),
	UPLAND_BROADLEAVED("Lwyż"),
	/** Mountain broadleaved forest (beech forest). */
	MOUNTAIN_BROADLEAVED("LG"),
	MOUNTAIN_MIXED_BROADLEAVED("LMG"),
	MOUNTAIN_MIXED_CONIFEROUS("BMG"),
	HIGH_MOUNTAIN_CONIFEROUS("BWG"),
	/** Mountain coniferous forest (spruce forest). */
	MOUNTAIN_CONIFEROUS("BG"),
	/** Mountain riparian forest (gray alder forest). */
	MOUNTAIN_RIPARIAN("LłG"),
	MOUNTAIN_ASH_ALDER_SWAMP("OlJG"),
	/** Subalpine belt. */
	SUBALPINE_SCRUB("subalp");

	private static final ForestSiteType[] VALUES = values();

	private final String code;

	ForestSiteType(String code) {
		this.code = code;
	}

	/** State Forests (LP) abbreviation, with Polish diacritics. */
	public String code() {
		return code;
	}

	public static ForestSiteType of(int ordinal) {
		return VALUES[ordinal];
	}

	/** Zonal STL from fertility and moisture (lowland sites, §3.3). */
	public static ForestSiteType zonal(Fertility t, Moisture w) {
		return switch (t) {
			case OLIGOTROPHIC -> switch (w) {
				case DRY -> DRY_CONIFEROUS;
				case FRESH -> FRESH_CONIFEROUS;
				case MOIST -> MOIST_CONIFEROUS;
				case BOGGY -> BOGGY_CONIFEROUS;
			};
			case OLIGO_MESOTROPHIC -> switch (w) {
				case DRY, FRESH -> FRESH_MIXED_CONIFEROUS;
				case MOIST -> MOIST_MIXED_CONIFEROUS;
				case BOGGY -> BOGGY_MIXED_CONIFEROUS;
			};
			case MESOTROPHIC -> switch (w) {
				case DRY, FRESH -> FRESH_MIXED_BROADLEAVED;
				case MOIST -> MOIST_MIXED_BROADLEAVED;
				case BOGGY -> BOGGY_MIXED_BROADLEAVED;
			};
			case EUTROPHIC -> switch (w) {
				case DRY, FRESH -> FRESH_BROADLEAVED;
				case MOIST -> MOIST_BROADLEAVED;
				case BOGGY -> ALDER_SWAMP;
			};
		};
	}

	/**
	 * STL of the forest in a forest biome. Zonal biomes get {@link #zonal}, the others the type implied by the biome
	 * (varied by fertility and moisture where the biome covers several types).
	 */
	public static ForestSiteType forBiome(HabitatBiome b, Fertility t, Moisture w) {
		boolean wet = w == Moisture.MOIST || w == Moisture.BOGGY;
		return switch (b) {
			case DRY_PINE_FOREST -> DRY_CONIFEROUS;
			case FRESH_PINE_FOREST -> FRESH_CONIFEROUS;
			case COASTAL_PINE_FOREST -> w == Moisture.DRY ? DRY_CONIFEROUS : FRESH_CONIFEROUS;
			case MOIST_PINE_FOREST -> t == Fertility.OLIGOTROPHIC ? MOIST_CONIFEROUS : MOIST_MIXED_CONIFEROUS;
			case BOG_WOODLAND -> t == Fertility.OLIGOTROPHIC ? BOGGY_CONIFEROUS : BOGGY_MIXED_CONIFEROUS;
			case MIXED_PINE_FOREST -> wet ? MOIST_MIXED_CONIFEROUS : FRESH_MIXED_CONIFEROUS;
			case MIXED_FOREST -> wet ? MOIST_MIXED_BROADLEAVED : FRESH_MIXED_BROADLEAVED;
			case OAK_HORNBEAM_FOREST -> wet ? MOIST_BROADLEAVED : FRESH_BROADLEAVED;
			case LOWLAND_BEECH_FOREST -> t == Fertility.EUTROPHIC ? FRESH_BROADLEAVED : FRESH_MIXED_BROADLEAVED;
			case ALDER_CARR -> t == Fertility.EUTROPHIC ? ALDER_SWAMP : BOGGY_MIXED_BROADLEAVED;
			case ASH_ALDER_FOREST -> ASH_ALDER_SWAMP;
			case WILLOW_POPLAR_FOREST, ELM_ASH_FOREST -> RIPARIAN;
			case UPLAND_FIR_FOREST -> switch (t) {
				case OLIGOTROPHIC, OLIGO_MESOTROPHIC -> UPLAND_MIXED_CONIFEROUS;
				case MESOTROPHIC -> UPLAND_MIXED_BROADLEAVED;
				case EUTROPHIC -> UPLAND_BROADLEAVED;
			};
			case MONTANE_BEECH_FOREST -> t == Fertility.EUTROPHIC ? MOUNTAIN_BROADLEAVED : MOUNTAIN_MIXED_BROADLEAVED;
			case MONTANE_SPRUCE_FOREST -> wet ? HIGH_MOUNTAIN_CONIFEROUS : t == Fertility.OLIGOTROPHIC || t == Fertility.OLIGO_MESOTROPHIC ? MOUNTAIN_CONIFEROUS : MOUNTAIN_MIXED_CONIFEROUS;
			case GRAY_ALDER_FOREST -> w == Moisture.BOGGY ? MOUNTAIN_ASH_ALDER_SWAMP : MOUNTAIN_RIPARIAN;
			case DWARF_PINE_SCRUB -> SUBALPINE_SCRUB;
			default -> zonal(t, w);
		};
	}
}
