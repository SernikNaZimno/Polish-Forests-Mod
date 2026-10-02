package pl.polishforests.worldgen.habitat;

/**
 * Siedliskowy typ lasu (STL, nomenklatura Lasów Państwowych). W siedliskach nieleśnych trybu N pole ma
 * wartość {@link #NONE}; w trybie D niezalesione siedlisko zachowuje STL potencjalny. Najwyżej 32 pozycje.
 */
public enum ForestSiteType {
	NONE("-"),
	/** Bór suchy. */
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
	/** Ols. */
	ALDER_SWAMP("Ol"),
	/** Ols jesionowy (łęg jesionowo-olszowy). */
	ASH_ALDER_SWAMP("OlJ"),
	/** Las łęgowy (łęgi nadrzeczne). */
	RIPARIAN("Lł"),
	UPLAND_MIXED_CONIFEROUS("BMwyż"),
	UPLAND_MIXED_BROADLEAVED("LMwyż"),
	UPLAND_BROADLEAVED("Lwyż"),
	/** Las górski (buczyna). */
	MOUNTAIN_BROADLEAVED("LG"),
	MOUNTAIN_MIXED_BROADLEAVED("LMG"),
	MOUNTAIN_MIXED_CONIFEROUS("BMG"),
	HIGH_MOUNTAIN_CONIFEROUS("BWG"),
	/** Bór górski (świerczyna). */
	MOUNTAIN_CONIFEROUS("BG"),
	/** Las łęgowy górski (olszyna). */
	MOUNTAIN_RIPARIAN("LłG"),
	MOUNTAIN_ASH_ALDER_SWAMP("OlJG"),
	/** Piętro subalpejskie. */
	SUBALPINE_SCRUB("subalp");

	private static final ForestSiteType[] VALUES = values();

	private final String code;

	ForestSiteType(String code) {
		this.code = code;
	}

	/** Skrót LP (z polskimi znakami). */
	public String code() {
		return code;
	}

	public static ForestSiteType of(int ordinal) {
		return VALUES[ordinal];
	}

	/** STL strefowy z trofii i wilgotności (siedliska niżowe, §3.3). */
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
	 * STL lasu w biomie leśnym. Biomy strefowe dostają {@link #zonal}, pozostałe typ wynikający z biomu
	 * (z odmianą według trofii i wilgotności tam, gdzie biom obejmuje kilka typów).
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
