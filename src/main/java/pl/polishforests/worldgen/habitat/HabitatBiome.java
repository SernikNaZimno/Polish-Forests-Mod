package pl.polishforests.worldgen.habitat;

import java.util.List;

/**
 * Biomes of the "Poland" world: 36 habitat groups (decision M2-A, docs/03-m2-biomy.md §2). The identifiers
 * {@code polishforests:<id>} are frozen from M2, because chunks store biomes by name. The enum order is
 * fixed (the habitat code stores {@link #ordinal()}); new biomes are appended at the end only.
 */
public enum HabitatBiome {
	// Forest (18)
	DRY_PINE_FOREST("dry_pine_forest", Group.FOREST, "Bór suchy", "Dry Pine Forest",
			0.7F, 0.4F, 0x3D6E70, 0x92BB62, 0x78A93A, 0xA37546),
	FRESH_PINE_FOREST("fresh_pine_forest", Group.FOREST, "Bór świeży", "Fresh Pine Forest",
			0.7F, 0.55F, 0x3D6E70, 0x89BD5E, 0x6CAB36, 0xA37346),
	COASTAL_PINE_FOREST("coastal_pine_forest", Group.FOREST, "Nadmorski bór bażynowy", "Coastal Crowberry Pine Forest",
			0.72F, 0.55F, 0x3A6A7A, 0x88BD5D, 0x6BAC34, 0xA37346),
	MOIST_PINE_FOREST("moist_pine_forest", Group.FOREST, "Bór wilgotny", "Moist Pine Forest",
			0.7F, 0.7F, 0x3D6E70, 0x7FBF5C, 0x60AE33, 0xA36F46),
	BOG_WOODLAND("bog_woodland", Group.FOREST, "Bór bagienny", "Bog Woodland",
			0.67F, 0.9F, 0x3A3326, 0x76C05B, 0x53AF32, 0xA36D46),
	MIXED_PINE_FOREST("mixed_pine_forest", Group.FOREST, "Bór mieszany", "Mixed Pine-Oak Forest",
			0.7F, 0.7F, 0x3D6E70, 0x7FBF5C, 0x60AE33, 0xA36F46),
	MIXED_FOREST("mixed_forest", Group.FOREST, "Las mieszany", "Mixed Forest",
			0.7F, 0.7F, 0x3D6E70, 0x7FBF5C, 0x60AE33, 0xA36F46),
	OAK_HORNBEAM_FOREST("oak_hornbeam_forest", Group.FOREST, "Grąd", "Oak-Hornbeam Forest",
			0.7F, 0.8F, 0x3D6E70, 0x79C05A, 0x59AE30, 0xA36D46),
	LOWLAND_BEECH_FOREST("lowland_beech_forest", Group.FOREST, "Buczyna niżowa", "Lowland Beech Forest",
			0.7F, 0.8F, 0x3D6E70, 0x79C05A, 0x59AE30, 0xA36D46),
	ALDER_CARR("alder_carr", Group.FOREST, "Ols", "Alder Carr",
			0.67F, 0.9F, 0x4A6E5E, 0x76C05B, 0x53AF32, 0xA36D46),
	ASH_ALDER_FOREST("ash_alder_forest", Group.FOREST, "Łęg jesionowo-olszowy", "Ash-Alder Riparian Forest",
			0.685F, 0.85F, 0x4A6E5E, 0x77C05B, 0x55AF31, 0xA36D46),
	WILLOW_POPLAR_FOREST("willow_poplar_forest", Group.FOREST, "Łęg wierzbowo-topolowy", "Willow-Poplar Floodplain Forest",
			0.685F, 0.85F, 0x4A6E5E, 0x77C05B, 0x55AF31, 0xA36D46),
	ELM_ASH_FOREST("elm_ash_forest", Group.FOREST, "Łęg wiązowo-jesionowy", "Elm-Ash Floodplain Forest",
			0.685F, 0.85F, 0x4A6E5E, 0x77C05B, 0x55AF31, 0xA36D46),
	UPLAND_FIR_FOREST("upland_fir_forest", Group.FOREST, "Wyżynna jedlina i buczyna", "Upland Fir and Beech Forest",
			0.7F, 0.8F, 0x3D6E70, 0x79C05A, 0x59AE30, 0xA36D46),
	MONTANE_BEECH_FOREST("montane_beech_forest", Group.FOREST, "Buczyna karpacka", "Carpathian Beech Forest",
			0.7F, 0.8F, 0x4F8FB8, 0x79C05A, 0x59AE30, 0xA36D46),
	MONTANE_SPRUCE_FOREST("montane_spruce_forest", Group.FOREST, "Świerczyna górska", "Montane Spruce Forest",
			0.7F, 0.8F, 0x4F8FB8, 0x79C05A, 0x59AE30, 0xA36D46),
	GRAY_ALDER_FOREST("gray_alder_forest", Group.FOREST, "Olszyna górska", "Gray Alder Forest",
			0.685F, 0.85F, 0x4F8FB8, 0x77C05B, 0x55AF31, 0xA36D46),
	DWARF_PINE_SCRUB("dwarf_pine_scrub", Group.FOREST, "Kosodrzewina", "Dwarf Pine Scrub",
			0.7F, 0.8F, 0x4F8FB8, 0x79C05A, 0x59AE30, 0xA36D46),
	// Non-forest terrestrial (12)
	RAISED_BOG("raised_bog", Group.NON_FOREST, "Torfowisko wysokie", "Raised Bog",
			0.65F, 0.9F, 0x3A3326, 0x76BF5D, 0x55AE35, 0xA36D46),
	FEN("fen", Group.NON_FOREST, "Torfowisko niskie i przejściowe", "Fen and Transition Mire",
			0.67F, 0.9F, 0x4A6E5E, 0x76C05B, 0x53AF32, 0xA36D46),
	REEDBED("reedbed", Group.NON_FOREST, "Szuwar", "Reedbed",
			0.67F, 0.9F, 0x4A6E5E, 0x76C05B, 0x53AF32, 0xA36D46),
	WILLOW_SCRUB("willow_scrub", Group.NON_FOREST, "Wikliny nadrzeczne", "Riverside Willow Scrub",
			0.685F, 0.85F, 0x4A6E5E, 0x77C05B, 0x55AF31, 0xA36D46),
	HEATH("heath", Group.NON_FOREST, "Wrzosowisko i murawa napiaskowa", "Heath and Sand Grassland",
			0.7F, 0.4F, 0x3D6E70, 0x92BB62, 0x78A93A, 0xA37546),
	WET_MEADOW("wet_meadow", Group.NON_FOREST, "Łąka wilgotna", "Wet Meadow",
			0.7F, 0.85F, 0x4A6E5E, 0x76C159, 0x54AF2F, 0xA36D46),
	HAY_MEADOW("hay_meadow", Group.NON_FOREST, "Łąka świeża i polana", "Hay Meadow",
			0.7F, 0.7F, 0x3D6E70, 0x7FBF5C, 0x60AE33, 0xA36F46),
	ARABLE_LAND("arable_land", Group.NON_FOREST, "Pole", "Arable Land",
			0.7F, 0.6F, 0x3D6E70, 0x85BD5E, 0x67AC36, 0xA37346),
	BEACH("beach", Group.NON_FOREST, "Plaża", "Beach",
			0.72F, 0.4F, 0x3A6A7A, 0x92BB5F, 0x77A938, 0xA37546),
	WHITE_DUNE("white_dune", Group.NON_FOREST, "Wydma biała", "White Dune",
			0.72F, 0.4F, 0x3A6A7A, 0x92BB5F, 0x77A938, 0xA37546),
	GRAY_DUNE("gray_dune", Group.NON_FOREST, "Wydma szara", "Gray Dune",
			0.72F, 0.4F, 0x3A6A7A, 0x92BB5F, 0x77A938, 0xA37546),
	ALPINE_GRASSLAND("alpine_grassland", Group.NON_FOREST, "Piętro alpejskie", "Alpine Grassland",
			0.7F, 0.8F, 0x4F8FB8, 0x79C05A, 0x59AE30, 0xA36D46),
	// Water (6)
	SEA("sea", Group.WATER, "Morze", "Baltic Sea",
			0.72F, 0.6F, 0x3A6A7A, 0x85BE5C, 0x67AC33, 0xA37246),
	LAGOON("lagoon", Group.WATER, "Zalew", "Coastal Lagoon",
			0.72F, 0.8F, 0x5B7A5A, 0x78C058, 0x56AF2E, 0xA36D46),
	RIVER("river", Group.WATER, "Rzeka", "River",
			0.7F, 0.8F, 0x4A6E5E, 0x79C05A, 0x59AE30, 0xA36D46),
	STREAM("stream", Group.WATER, "Potok", "Mountain Stream",
			0.7F, 0.8F, 0x4F8FB8, 0x79C05A, 0x59AE30, 0xA36D46),
	LAKE("lake", Group.WATER, "Jezioro", "Lake",
			0.7F, 0.8F, 0x3D6E70, 0x79C05A, 0x59AE30, 0xA36D46),
	DYSTROPHIC_LAKE("dystrophic_lake", Group.WATER, "Jezioro dystroficzne", "Dystrophic Lake",
			0.7F, 0.9F, 0x3A3326, 0x73C158, 0x50B02F, 0xA36D46);

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
	private final float temperature;
	private final float downfall;
	private final int waterColor;
	private final int grassColor;
	private final int foliageColor;
	private final int dryFoliageColor;

	/**
	 * @param temperature     base temperature at sea level (docs/03-m2-biomy.md §6.1; at most 0.8 for the Serene Seasons gate)
	 * @param downfall        downfall; in the "Poland" world it only affects the colors (§6.3)
	 * @param waterColor      water color (§10)
	 * @param grassColor      explicit grass color: starting value from the vanilla colormap at (temperature, downfall)
	 * @param foliageColor    explicit foliage color, from the colormap as above
	 * @param dryFoliageColor explicit dry foliage color (leaf litter), from the colormap as above
	 */
	HabitatBiome(String id, Group group, String polishName, String englishName, float temperature, float downfall,
			int waterColor, int grassColor, int foliageColor, int dryFoliageColor) {
		this.id = id;
		this.group = group;
		this.polishName = polishName;
		this.englishName = englishName;
		this.temperature = temperature;
		this.downfall = downfall;
		this.waterColor = waterColor;
		this.grassColor = grassColor;
		this.foliageColor = foliageColor;
		this.dryFoliageColor = dryFoliageColor;
	}

	/** Background music of the biome (docs/03-m2-biomy.md §10, attributes). */
	public enum Music {
		/** No biome music: the music of the dimension type. */
		DEFAULT,
		FOREST,
		OLD_GROWTH_TAIGA,
		SWAMP,
		MEADOW
	}

	/** Natural spawn profile ({@code gameplay/natural_mob_spawns}, §10; Polish fauna comes in M6). */
	public enum Spawns {
		/** Wolf 5, fox 8, rabbit 4, no farm animals. */
		FOREST,
		/** Forest animals and frogs, no witches (alder carr). */
		WET_FOREST,
		/** Sheep 12, pig 10, chicken 10, cow 8 (meadows, fields, alpine grassland). */
		OPEN,
		/** Frog 10 (peatlands, reedbed). */
		WETLAND,
		/** Rabbit 4 (heath, willow scrub, beach and dunes). */
		SPARSE,
		/** Salmon, drowned with weight 30 (river, stream). */
		RIVER,
		/** No fish (lakes and the lagoon). */
		LAKE,
		/** Cod, no squid, drowned with weight 30 (Baltic). */
		SEA
	}

	/** Bone meal carrier at the end of step 9 ({@code polishforests:bone_meal/<id>}, §8.1): the only element that differs between biomes. */
	public enum BoneMeal {
		FOREST, MEADOW, WETLAND, MOUNTAIN;

		/** Placed feature path, e.g. {@code bone_meal/forest}. */
		public String path() {
			return "bone_meal/" + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Identifier without a namespace (frozen). */
	public String id() {
		return id;
	}

	public Group group() {
		return group;
	}

	/** Polish name: the source of the {@code pl_pl} biome names (datagen). */
	public String polishName() {
		return polishName;
	}

	/** English name: the source of the {@code en_us} biome names (datagen). */
	public String englishName() {
		return englishName;
	}

	/** Base temperature at sea level (biome JSON {@code temperature}, climate profile). */
	public float temperature() {
		return temperature;
	}

	public float downfall() {
		return downfall;
	}

	public int waterColor() {
		return waterColor;
	}

	public int grassColor() {
		return grassColor;
	}

	public int foliageColor() {
		return foliageColor;
	}

	public int dryFoliageColor() {
		return dryFoliageColor;
	}

	/** Water fog color of the dystrophic lake (#3B2A1A, §10), or -1 for the default. */
	public int waterFogColor() {
		return this == DYSTROPHIC_LAKE ? 0x3B2A1A : -1;
	}

	/** Background music (§10): forests, pine and spruce forests, alder carr and peatlands, alpine grassland. */
	public Music music() {
		return switch (this) {
			case DRY_PINE_FOREST, FRESH_PINE_FOREST, COASTAL_PINE_FOREST, MOIST_PINE_FOREST, MIXED_PINE_FOREST, UPLAND_FIR_FOREST,
					MONTANE_SPRUCE_FOREST, DWARF_PINE_SCRUB -> Music.OLD_GROWTH_TAIGA;
			case BOG_WOODLAND, ALDER_CARR, RAISED_BOG, FEN -> Music.SWAMP;
			case ALPINE_GRASSLAND -> Music.MEADOW;
			default -> isForest() ? Music.FOREST : Music.DEFAULT;
		};
	}

	/** Natural spawn profile (§10). */
	public Spawns spawns() {
		return switch (this) {
			case ALDER_CARR -> Spawns.WET_FOREST;
			case WET_MEADOW, HAY_MEADOW, ARABLE_LAND, ALPINE_GRASSLAND -> Spawns.OPEN;
			case RAISED_BOG, FEN, REEDBED -> Spawns.WETLAND;
			case HEATH, WILLOW_SCRUB, BEACH, WHITE_DUNE, GRAY_DUNE -> Spawns.SPARSE;
			case RIVER, STREAM -> Spawns.RIVER;
			case LAGOON, LAKE, DYSTROPHIC_LAKE -> Spawns.LAKE;
			case SEA -> Spawns.SEA;
			default -> Spawns.FOREST;
		};
	}

	/** {@code gameplay/increased_fire_burnout}: alder carr, floodplain forests, peatlands and reedbed (§10). */
	public boolean increasedFireBurnout() {
		return switch (this) {
			case BOG_WOODLAND, ALDER_CARR, ASH_ALDER_FOREST, WILLOW_POPLAR_FOREST, ELM_ASH_FOREST, GRAY_ALDER_FOREST, RAISED_BOG, FEN,
					REEDBED -> true;
			default -> false;
		};
	}

	/** Bone meal carrier at the end of step 9 (§8.1). */
	public BoneMeal boneMeal() {
		return switch (this) {
			case MONTANE_BEECH_FOREST, MONTANE_SPRUCE_FOREST, DWARF_PINE_SCRUB, ALPINE_GRASSLAND -> BoneMeal.MOUNTAIN;
			case BOG_WOODLAND, ALDER_CARR, ASH_ALDER_FOREST, WILLOW_POPLAR_FOREST, ELM_ASH_FOREST, GRAY_ALDER_FOREST, RAISED_BOG, FEN,
					REEDBED, WILLOW_SCRUB, WET_MEADOW -> BoneMeal.WETLAND;
			case HEATH, HAY_MEADOW, ARABLE_LAND, BEACH, WHITE_DUNE, GRAY_DUNE -> BoneMeal.MEADOW;
			default -> isWater() ? BoneMeal.WETLAND : BoneMeal.FOREST;
		};
	}

	/** Montane biomes in {@code #minecraft:is_mountain} (decision M2-C: outposts, mountain portals and mineshafts, §10). */
	public boolean isMountain() {
		return this == MONTANE_BEECH_FOREST || this == MONTANE_SPRUCE_FOREST || this == DWARF_PINE_SCRUB || this == ALPINE_GRASSLAND;
	}

	/** Biome with the given identifier (without a namespace), or null. */
	public static HabitatBiome byId(String id) {
		for (HabitatBiome b : VALUES) {
			if (b.id.equals(id)) {
				return b;
			}
		}
		return null;
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
