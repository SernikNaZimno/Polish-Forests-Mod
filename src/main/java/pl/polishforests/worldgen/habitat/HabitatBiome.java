package pl.polishforests.worldgen.habitat;

import java.util.List;

/**
 * Biomes of the "Poland" world: 36 habitat groups (decision M2-A, docs/03-m2-biomy.md §2). The identifiers
 * {@code polishforests:<id>} are frozen from M2, because chunks store biomes by name. The enum order is
 * fixed (the habitat code stores {@link #ordinal()}); new biomes are appended at the end only.
 */
public enum HabitatBiome {
	// Forest (18)
	DRY_PINE_FOREST("dry_pine_forest", Group.FOREST, "Bór suchy", "Dry Pine Forest"),
	FRESH_PINE_FOREST("fresh_pine_forest", Group.FOREST, "Bór świeży", "Fresh Pine Forest"),
	COASTAL_PINE_FOREST("coastal_pine_forest", Group.FOREST, "Nadmorski bór bażynowy", "Coastal Crowberry Pine Forest"),
	MOIST_PINE_FOREST("moist_pine_forest", Group.FOREST, "Bór wilgotny", "Moist Pine Forest"),
	BOG_WOODLAND("bog_woodland", Group.FOREST, "Bór bagienny", "Bog Woodland"),
	MIXED_PINE_FOREST("mixed_pine_forest", Group.FOREST, "Bór mieszany", "Mixed Pine-Oak Forest"),
	MIXED_FOREST("mixed_forest", Group.FOREST, "Las mieszany", "Mixed Forest"),
	OAK_HORNBEAM_FOREST("oak_hornbeam_forest", Group.FOREST, "Grąd", "Oak-Hornbeam Forest"),
	LOWLAND_BEECH_FOREST("lowland_beech_forest", Group.FOREST, "Buczyna niżowa", "Lowland Beech Forest"),
	ALDER_CARR("alder_carr", Group.FOREST, "Ols", "Alder Carr"),
	ASH_ALDER_FOREST("ash_alder_forest", Group.FOREST, "Łęg jesionowo-olszowy", "Ash-Alder Riparian Forest"),
	WILLOW_POPLAR_FOREST("willow_poplar_forest", Group.FOREST, "Łęg wierzbowo-topolowy", "Willow-Poplar Floodplain Forest"),
	ELM_ASH_FOREST("elm_ash_forest", Group.FOREST, "Łęg wiązowo-jesionowy", "Elm-Ash Floodplain Forest"),
	UPLAND_FIR_FOREST("upland_fir_forest", Group.FOREST, "Wyżynna jedlina i buczyna", "Upland Fir and Beech Forest"),
	MONTANE_BEECH_FOREST("montane_beech_forest", Group.FOREST, "Buczyna karpacka", "Carpathian Beech Forest"),
	MONTANE_SPRUCE_FOREST("montane_spruce_forest", Group.FOREST, "Świerczyna górska", "Montane Spruce Forest"),
	GRAY_ALDER_FOREST("gray_alder_forest", Group.FOREST, "Olszyna górska", "Gray Alder Forest"),
	DWARF_PINE_SCRUB("dwarf_pine_scrub", Group.FOREST, "Kosodrzewina", "Dwarf Pine Scrub"),
	// Non-forest terrestrial (12)
	RAISED_BOG("raised_bog", Group.NON_FOREST, "Torfowisko wysokie", "Raised Bog"),
	FEN("fen", Group.NON_FOREST, "Torfowisko niskie i przejściowe", "Fen and Transition Mire"),
	REEDBED("reedbed", Group.NON_FOREST, "Szuwar", "Reedbed"),
	WILLOW_SCRUB("willow_scrub", Group.NON_FOREST, "Wikliny nadrzeczne", "Riverside Willow Scrub"),
	HEATH("heath", Group.NON_FOREST, "Wrzosowisko i murawa napiaskowa", "Heath and Sand Grassland"),
	WET_MEADOW("wet_meadow", Group.NON_FOREST, "Łąka wilgotna", "Wet Meadow"),
	HAY_MEADOW("hay_meadow", Group.NON_FOREST, "Łąka świeża i polana", "Hay Meadow"),
	ARABLE_LAND("arable_land", Group.NON_FOREST, "Pole", "Arable Land"),
	BEACH("beach", Group.NON_FOREST, "Plaża", "Beach"),
	WHITE_DUNE("white_dune", Group.NON_FOREST, "Wydma biała", "White Dune"),
	GRAY_DUNE("gray_dune", Group.NON_FOREST, "Wydma szara", "Gray Dune"),
	ALPINE_GRASSLAND("alpine_grassland", Group.NON_FOREST, "Piętro alpejskie", "Alpine Grassland"),
	// Water (6)
	SEA("sea", Group.WATER, "Morze", "Baltic Sea"),
	LAGOON("lagoon", Group.WATER, "Zalew", "Coastal Lagoon"),
	RIVER("river", Group.WATER, "Rzeka", "River"),
	STREAM("stream", Group.WATER, "Potok", "Mountain Stream"),
	LAKE("lake", Group.WATER, "Jezioro", "Lake"),
	DYSTROPHIC_LAKE("dystrophic_lake", Group.WATER, "Jezioro dystroficzne", "Dystrophic Lake");

	/** Biome group: forest, non-forest terrestrial, water. */
	public enum Group {
		FOREST, NON_FOREST, WATER
	}

	private static final HabitatBiome[] VALUES = values();
	/** Pine forests (coniferous sites), including the crowberry pine forest; for shares and palettes. */
	public static final List<HabitatBiome> PINE_FORESTS = List.of(DRY_PINE_FOREST, FRESH_PINE_FOREST, COASTAL_PINE_FOREST, MOIST_PINE_FOREST, BOG_WOODLAND,
			MIXED_PINE_FOREST);
	/** Floodplain forests (for the "no floodplain forest off the valley floor" test). */
	public static final List<HabitatBiome> FLOODPLAIN_FORESTS = List.of(ASH_ALDER_FOREST, WILLOW_POPLAR_FOREST, ELM_ASH_FOREST);

	private final String id;
	private final Group group;
	private final String polishName;
	private final String englishName;

	HabitatBiome(String id, Group group, String polishName, String englishName) {
		this.id = id;
		this.group = group;
		this.polishName = polishName;
		this.englishName = englishName;
	}

	/** Identifier without a namespace (frozen). */
	public String id() {
		return id;
	}

	public Group group() {
		return group;
	}

	/** Polish name (preliminary; lang comes from datagen in S5). */
	public String polishName() {
		return polishName;
	}

	public String englishName() {
		return englishName;
	}

	public boolean isForest() {
		return group == Group.FOREST;
	}

	public boolean isWater() {
		return group == Group.WATER;
	}

	public boolean isPineForest() {
		return PINE_FORESTS.contains(this);
	}

	/** Biome with the given {@link #ordinal()}. */
	public static HabitatBiome of(int ordinal) {
		return VALUES[ordinal];
	}
}
