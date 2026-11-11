package pl.polishforests.worldgen.norms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.tree.AgeClass;

/**
 * The norms card of the vertical slice (docs/04-pionowy-wycinek.md §6): what a sample plot of 1 ha (100 × 100 blocks,
 * about 39 chunks) of each forest of the slice should hold, with the source of every number, like {@code Calibration}
 * for the classifier. The census ({@link ForestCensus}) measures the same keys ({@link #METRICS}); {@link #verdict}
 * compares a value with its norm.
 *
 * <p>A norm is a range [min, max] in the unit of its metric. The tolerance of §6 widens it on both ends relatively:
 * accepted is [min·(1 − t), max·(1 + t)], with t = {@link #TOLERANCE} (±20%) and {@link #TOLERANCE_WIDE} (±30%) for dead
 * wood and gaps. A single value of the card ("about 15") is a range of one value. Some norms depend on the aspect of the
 * year (the herb layer of the oak-hornbeam forest in spring and in summer) or on the age class of the stand (the trunks
 * per chunk of the managed pine forest); a norm without a qualifier holds for all of them, and a lookup with an unknown
 * qualifier (null) takes the norm without one.
 *
 * <p>Sources ([Ź] read in a source, [L] the local reports of docs/research, [S] an estimate of the plan): Miścicki 2016
 * (Białowieża strict reserve, 160 permanent plots), L 06 = docs/research/06-lasy-polski-fitosocjologia-stl.md, the
 * ecology report docs/research/wycinek/research-ekologia.md (Popiołek, Janów Lubelski, BPN gaps, Bobiec et al. 2011),
 * GIOŚ 9170 monitoring, Mróz and Pawlaczyk, Piętka, WISL 2010–2014, Rawlik 2021, Stefańska 2007.
 */
public final class ForestNorms {
	private ForestNorms() {
	}

	/** Relative tolerance of a norm (§6: ±20%). */
	public static final double TOLERANCE = 0.20;
	/** Relative tolerance of the dead wood and gap norms (§6: ±30%). */
	public static final double TOLERANCE_WIDE = 0.30;

	/** A sample plot: 1 ha, 100 × 100 blocks in both scales (trees 1:1, PW-12). */
	public static final int PLOT_SIDE = 100;
	/** Columns of a chunk, for the per-chunk norms. */
	public static final double CHUNK_AREA = 256;
	/** Square meters of a hectare (one column is 1 m² in both scales for the tree and ground layers). */
	public static final double HECTARE = 10_000;

	/**
	 * The forests of the card: the columns of §6, a forest of the tree planner in its natural or managed stands (step F1:
	 * the lowland beech forest appended; each step F appends its forest's columns).
	 */
	public enum Forest {
		OAK_HORNBEAM_NATURAL("grąd naturalny", HabitatBiome.OAK_HORNBEAM_FOREST, true),
		OAK_HORNBEAM_MANAGED("grąd gospodarczy", HabitatBiome.OAK_HORNBEAM_FOREST, false),
		FRESH_PINE_MANAGED("bór świeży gospodarczy", HabitatBiome.FRESH_PINE_FOREST, false),
		FRESH_PINE_NATURAL("bór naturalny", HabitatBiome.FRESH_PINE_FOREST, true),
		LOWLAND_BEECH_NATURAL("buczyna naturalna", HabitatBiome.LOWLAND_BEECH_FOREST, true),
		LOWLAND_BEECH_MANAGED("buczyna gospodarcza", HabitatBiome.LOWLAND_BEECH_FOREST, false),
		ALDER_CARR_NATURAL("ols naturalny", HabitatBiome.ALDER_CARR, true),
		ALDER_CARR_MANAGED("ols gospodarczy", HabitatBiome.ALDER_CARR, false),
		ASH_ALDER_NATURAL("łęg jesionowo-olszowy naturalny", HabitatBiome.ASH_ALDER_FOREST, true),
		ASH_ALDER_MANAGED("łęg jesionowo-olszowy gospodarczy", HabitatBiome.ASH_ALDER_FOREST, false),
		UPLAND_FIR_NATURAL("jedlina naturalna", HabitatBiome.UPLAND_FIR_FOREST, true),
		UPLAND_FIR_MANAGED("jedlina gospodarcza", HabitatBiome.UPLAND_FIR_FOREST, false),
		MONTANE_BEECH_NATURAL("buczyna karpacka naturalna", HabitatBiome.MONTANE_BEECH_FOREST, true),
		MONTANE_BEECH_MANAGED("buczyna karpacka gospodarcza", HabitatBiome.MONTANE_BEECH_FOREST, false),
		DRY_PINE_NATURAL("bór suchy naturalny", HabitatBiome.DRY_PINE_FOREST, true),
		DRY_PINE_MANAGED("bór suchy gospodarczy", HabitatBiome.DRY_PINE_FOREST, false),
		MOIST_PINE_NATURAL("bór wilgotny naturalny", HabitatBiome.MOIST_PINE_FOREST, true),
		MOIST_PINE_MANAGED("bór wilgotny gospodarczy", HabitatBiome.MOIST_PINE_FOREST, false),
		BOG_WOODLAND_NATURAL("bór bagienny naturalny", HabitatBiome.BOG_WOODLAND, true),
		BOG_WOODLAND_MANAGED("bór bagienny gospodarczy", HabitatBiome.BOG_WOODLAND, false),
		COASTAL_PINE_NATURAL("bór bażynowy naturalny", HabitatBiome.COASTAL_PINE_FOREST, true),
		COASTAL_PINE_MANAGED("bór bażynowy gospodarczy", HabitatBiome.COASTAL_PINE_FOREST, false),
		ELM_ASH_NATURAL("łęg wiązowo-jesionowy naturalny", HabitatBiome.ELM_ASH_FOREST, true),
		ELM_ASH_MANAGED("łęg wiązowo-jesionowy gospodarczy", HabitatBiome.ELM_ASH_FOREST, false),
		WILLOW_POPLAR_NATURAL("łęg wierzbowo-topolowy naturalny", HabitatBiome.WILLOW_POPLAR_FOREST, true),
		WILLOW_POPLAR_MANAGED("łęg wierzbowo-topolowy gospodarczy", HabitatBiome.WILLOW_POPLAR_FOREST, false),
		/** Step F5: the osier scrub, no forest (one column for its natural and managed stands, {@link #of}). */
		WILLOW_SCRUB("wiklina nadrzeczna", HabitatBiome.WILLOW_SCRUB, true),
		MONTANE_SPRUCE_NATURAL("świerczyna górska naturalna", HabitatBiome.MONTANE_SPRUCE_FOREST, true),
		MONTANE_SPRUCE_MANAGED("świerczyna górska gospodarcza", HabitatBiome.MONTANE_SPRUCE_FOREST, false),
		GRAY_ALDER_NATURAL("olszynka górska naturalna", HabitatBiome.GRAY_ALDER_FOREST, true),
		GRAY_ALDER_MANAGED("olszynka górska gospodarcza", HabitatBiome.GRAY_ALDER_FOREST, false),
		/** Step F6: the dwarf pine scrub, its natural stands only (no managed column: the belt is protected, choice F6-g). */
		DWARF_PINE_NATURAL("kosodrzewina", HabitatBiome.DWARF_PINE_SCRUB, true);

		private final String polishName;
		private final HabitatBiome biome;
		private final boolean natural;

		Forest(String polishName, HabitatBiome biome, boolean natural) {
			this.polishName = polishName;
			this.biome = biome;
			this.natural = natural;
		}

		/** The column name of the card in docs/04 §6. */
		public String polishName() {
			return polishName;
		}

		/** The habitat biome of the column. */
		public HabitatBiome biome() {
			return biome;
		}

		/** Whether the column is the forest's natural stands (else its managed ones). */
		public boolean natural() {
			return natural;
		}

		/** Whether the forest lies on the transect of the slice (N15). */
		public boolean transect() {
			return biome == HabitatBiome.OAK_HORNBEAM_FOREST || biome == HabitatBiome.FRESH_PINE_FOREST;
		}

		/**
		 * The column of a forest and stand, or null when the card has none; a biome that is no forest (step F5: the osier
		 * scrub) has one column for both stands.
		 */
		public static @Nullable Forest of(HabitatBiome biome, boolean natural) {
			Forest any = null;
			for (Forest f : values()) {
				if (f.biome == biome && f.natural == natural) {
					return f;
				}
				if (f.biome == biome) {
					any = f;
				}
			}
			return any != null && biome.group() != HabitatBiome.Group.FOREST ? any : null;
		}
	}

	/**
	 * Site variant of a forest for the norms that differ by it (step F1: the herb layer of the fertile and of the acid
	 * beech forest, N8); a lookup without a site (null) takes the norm without one.
	 */
	public enum Site {
		/** The fertile form (e.g. Galio odorati-Fagetum, site type Lśw). */
		FERTILE,
		/** The acid form (e.g. Luzulo pilosae-Fagetum, site type LMśw). */
		ACID,
		/** Step F3: the sycamore ravine forest of the Carpathian beech forest (Lunario-Aceretum, its association). */
		RAVINE,
		/** Step F4: the birch bog woodland (Vaccinio uliginosi-Betuletum pubescentis, the boggy mixed coniferous site type). */
		BIRCH_BOG,
		/** Step F5: the white poplar forest of the willow-poplar forest (Populetum albae, its association). */
		POPLAR,
		/** Step F6: the fir-spruce forest of the montane spruce forest (Abieti-Piceetum montanum, its association). */
		FIR_SPRUCE,
		/**
		 * Step TT4: the spruce-stone pine forest of the montane spruce forest (Pino cembrae-Piceetum, its association; in the
		 * Tatra National Park, natural only).
		 */
		STONE_PINE
	}

	/**
	 * The site variant of a habitat code (the norms that differ by it): the fertile and the acid lowland and Carpathian
	 * beech forest by the forest site type (Lśw and LMśw, LG and LMG), the sycamore ravine forest by its association, the
	 * birch bog woodland by its site type (BMb, step F4); else
	 * null (the norm without a site).
	 */
	public static @Nullable Site siteOf(int code) {
		if (pl.polishforests.worldgen.habitat.Habitat.association(code) == pl.polishforests.worldgen.habitat.Association.SYCAMORE_RAVINE_FOREST) {
			return Site.RAVINE;
		}
		if (pl.polishforests.worldgen.habitat.Habitat.association(code) == pl.polishforests.worldgen.habitat.Association.POPULETUM_ALBAE) {
			return Site.POPLAR;
		}
		if (pl.polishforests.worldgen.habitat.Habitat.association(code) == pl.polishforests.worldgen.habitat.Association.ABIETI_PICEETUM) {
			return Site.FIR_SPRUCE;
		}
		if (pl.polishforests.worldgen.habitat.Habitat.association(code) == pl.polishforests.worldgen.habitat.Association.PINO_CEMBRAE_PICEETUM
				&& pl.polishforests.worldgen.habitat.Habitat.biome(code) == HabitatBiome.MONTANE_SPRUCE_FOREST) {
			return Site.STONE_PINE;
		}
		if (pl.polishforests.worldgen.habitat.Habitat.biome(code) == HabitatBiome.BOG_WOODLAND
				&& pl.polishforests.worldgen.habitat.Habitat.siteType(code) == pl.polishforests.worldgen.habitat.ForestSiteType.BOGGY_MIXED_CONIFEROUS) {
			return Site.BIRCH_BOG;
		}
		return switch (pl.polishforests.worldgen.habitat.Habitat.siteType(code)) {
			case FRESH_BROADLEAVED, MOUNTAIN_BROADLEAVED -> Site.FERTILE;
			case FRESH_MIXED_BROADLEAVED, MOUNTAIN_MIXED_BROADLEAVED -> Site.ACID;
			default -> null;
		};
	}

	/** Aspect of the year for the norms that change with it (the herb layer, N8). */
	public enum Aspect {
		SPRING, SUMMER, OTHER;

		/** Spring from early to late spring, summer from early to late summer, the rest other. */
		public static Aspect of(SubSeason s) {
			return switch (s) {
				case EARLY_SPRING, MID_SPRING, LATE_SPRING -> SPRING;
				case EARLY_SUMMER, MID_SUMMER, LATE_SUMMER -> SUMMER;
				default -> OTHER;
			};
		}
	}

	// ------------------------------------------------------------------ metrics (keys of the census and of the norms)

	/** N1: living trees of all layers per chunk (trunks; shrubs not counted). */
	public static final String TREES_PER_CHUNK = "N1.trees_per_chunk";
	/** N2: trees of the main canopy (a1) per chunk, their mean height (blocks), the lower layer (a2) likewise. */
	public static final String A1_PER_CHUNK = "N2.a1_per_chunk";
	public static final String A1_HEIGHT = "N2.a1_height";
	public static final String A2_PER_CHUNK = "N2.a2_per_chunk";
	public static final String A2_HEIGHT = "N2.a2_height";
	/** N2: share of a2 among the trees of a1 and a2, %. */
	public static final String A2_SHARE = "N2.a2_share";
	/** N2 (no norm): top height of the plot, the mean height of the 100 tallest trees per ha, blocks; the layers are its thirds. */
	public static final String TOP_HEIGHT = "N2.top_height";
	/** N3: share of a species among the a1 trunks, %: {@code N3.<species id>} or a group of {@link #GROUPS}. */
	public static final String COMPOSITION = "N3.";
	/** N4: canopy closure, share of the columns with leaves more than 2 blocks above the ground, %. */
	public static final String CANOPY = "N4.canopy";
	/** N5: share of the plot in canopy gaps, %, and the mean equivalent diameter of the gaps, blocks. */
	public static final String GAP_SHARE = "N5.gap_share";
	public static final String GAP_DIAMETER = "N5.gap_diameter";
	/** N6 (no norm): regeneration (young trees below a third of the top height) per ha. */
	public static final String REGENERATION = "N6.regeneration_per_ha";
	/** N6: regeneration in gaps over regeneration under the canopy (density per area). */
	public static final String REGENERATION_RATIO = "N6.gap_ratio";
	/** N6: oak regeneration (young oaks taller than 0.5 m) per ha. */
	public static final String OAK_REGENERATION = "N6.oak_per_ha";
	/** N6: Scots pine regeneration under the closed canopy (outside the gaps) per ha. */
	public static final String PINE_REGENERATION_UNDER_CANOPY = "N6.pine_under_canopy_per_ha";
	/** N7: understory (shrub layer b: shrubs and regeneration) cover, %. */
	public static final String UNDERSTORY = "N7.understory";
	/** N7 (no norm): shrubs (shrub species) per chunk. */
	public static final String SHRUBS_PER_CHUNK = "N7.shrubs_per_chunk";
	/**
	 * N8: herb layer cover, % of the columns, as it is in summer (without the spring geophytes, which the client shows
	 * only in spring, PW-8 (a)); the same in spring ({@link #HERBS_SPRING}, with them; step V12: both from one census, the
	 * server state of the plants does not change with the season); the bilberry alone.
	 */
	public static final String HERBS = "N8.herbs";
	public static final String HERBS_SPRING = "N8.herbs_spring";
	public static final String BILBERRY = "N8.bilberry";
	/** N8 (no norm): leaf litter cover, % of the columns (an indicator of the transect). */
	public static final String LITTER = "N8.litter";
	/** N9: moss cover, % of the columns. */
	public static final String MOSS = "N9.moss";
	/** N10: dead wood objects per chunk (logs, snags and stumps), logs and stumps alone. */
	public static final String DEADWOOD_PER_CHUNK = "N10.objects_per_chunk";
	public static final String LOGS_PER_CHUNK = "N10.logs_per_chunk";
	public static final String STUMPS_PER_CHUNK = "N10.stumps_per_chunk";
	/**
	 * N10 (step V11): dead trees per chunk without the stumps (logs and snags): the managed oak-hornbeam forest has its
	 * WISL mean of dead wood plus the stumps of the thinnings (research-ekologia §5: "ok. 0,25 obiektu, pniaki").
	 */
	public static final String PIECES_PER_CHUNK = "N10.pieces_per_chunk";
	/** N10 (no norm): standing dead trees (snags) per chunk. */
	public static final String SNAGS_PER_CHUNK = "N10.snags_per_chunk";
	/** N10: dead wood volume without the stumps (lying logs and snags), m³/ha from the voxels. */
	public static final String DEADWOOD_VOLUME = "N10.volume";
	/** N10: coarse dead wood pieces (longer than 3 m, at least 50 cm thick) per ha (GIOŚ). */
	public static final String COARSE_PIECES = "N10.coarse_per_ha";
	/** N15: width of the transitions along the transect, m·k (REAL blocks, GAMEPLAY blocks / k). */
	public static final String TRANSITION_WIDTH = "N15.transition_width";
	/**
	 * N15 (step Z1): width of the transition of the palettes along the transect (part {@code trees:}, {@code ground:} or
	 * {@code field:} and the pair of forests; {@code TransectPalettes}), m·k.
	 */
	public static final String PALETTE_WIDTH = "N15.palette_width";
	/** N15 (no norm, step Z1): the ends of the interval of a transition width ({@code ForestCensus.WIDTH_INTERVAL}), m·k. */
	public static final String TRANSITION_WIDTH_LOW = "N15.transition_width_low";
	public static final String TRANSITION_WIDTH_HIGH = "N15.transition_width_high";
	/** N15: the indicators keep the order fresh pine → mixed pine-oak → mixed → oak-hornbeam forest: 1 yes, 0 no. */
	public static final String TRANSECT_ORDER = "N15.order";
	/** N15 (no norm): share of conifers (Scots pine, spruce, fir, larch) among the trees of a stretch of the transect, %. */
	public static final String CONIFERS = "N15.conifers";
	/** Check, not a norm (review of V9): bare wood in the upper half of the stand per chunk. */
	public static final String BARE_WOOD = "X.bare_wood_per_chunk";
	/** Check, not a norm: the part of {@link #BARE_WOOD} open to the side (review of V9, round 2). */
	public static final String EXPOSED_WOOD = "X.exposed_wood_per_chunk";
	/** Check, not a norm: bare arms of living trees at any height, blocks per chunk (review of V9, round 2). */
	public static final String BARE_ARMS = "X.bare_arms_per_chunk";

	/**
	 * Species groups of the composition norms: the maples and the ash share one norm in the oak-hornbeam forest
	 * ("Kl/Js 5–15"), the census writes the share of the group as {@code N3.maple_ash}; "Db" are both oaks. The trunks of
	 * the other species and of an unknown species go to {@code N3.other}.
	 */
	public static final Map<String, List<String>> GROUPS = Map.of("maple_ash", List.of("norway_maple", "sycamore_maple", "ash"),
			"oak", List.of("oak", "sessile_oak"));
	/** Composition keys of the card ({@code N3.<key>}): groups of {@link #GROUPS} or species ids, then {@code other}. */
	public static final List<String> COMPOSITION_KEYS = List.of("oak", "linden", "maple_ash", "hornbeam", "spruce", "beech",
			"scots_pine", "birch", "black_alder", "elm", "fir", "white_willow", "poplar", "gray_alder", "rowan",
			// Step TT4: the stone pine and the larch (the card of the spruce-stone pine forest).
			"stone_pine", "larch", "other");

	/**
	 * A norm of the card.
	 *
	 * @param metric    key of the metric
	 * @param forest    column of the card
	 * @param min       lower end of the range, in the unit of the metric
	 * @param max       upper end ({@link Double#POSITIVE_INFINITY}: at least {@code min})
	 * @param tolerance relative tolerance on both ends
	 * @param aspect    aspect of the year it holds for, or null for all
	 * @param age       age class of the stand it holds for, or null for all
	 * @param source    source of the numbers (as in §6)
	 * @param site      site variant it holds for, or null for all (step F1)
	 */
	public record Norm(String metric, Forest forest, double min, double max, double tolerance, @Nullable Aspect aspect,
			@Nullable AgeClass age, String source, @Nullable Site site) {
		public Norm(String metric, Forest forest, double min, double max, double tolerance, @Nullable Aspect aspect,
				@Nullable AgeClass age, String source) {
			this(metric, forest, min, max, tolerance, aspect, age, source, null);
		}

		/** Lowest accepted value with the tolerance. */
		public double low() {
			return min * (1 - tolerance);
		}

		/** Highest accepted value with the tolerance. */
		public double high() {
			return max * (1 + tolerance);
		}

		/** The range as text, e.g. {@code 80–95}, {@code 15}, {@code ≥ 4}. */
		public String range() {
			if (max == Double.POSITIVE_INFINITY) {
				return "≥ " + number(min);
			}
			return min == max ? number(min) : number(min) + "–" + number(max);
		}
	}

	/** Result of a comparison with a norm. */
	public enum Verdict {
		/** Within the range. */
		IN,
		/** Outside the range, within the tolerance. */
		TOLERANCE,
		/** Outside the tolerance. */
		OUT,
		/** No value measured (e.g. no a2 trees) or no norm. */
		NONE
	}

	/** Descriptions of the metrics, in the order of the card (the census writes them in this order). */
	public static final Map<String, String> METRICS = new LinkedHashMap<>();
	private static final List<Norm> NORMS = new ArrayList<>();

	static {
		METRICS.put(TREES_PER_CHUNK, "living trees of all layers per chunk");
		METRICS.put(A1_PER_CHUNK, "main canopy (a1) trees per chunk");
		METRICS.put(A1_HEIGHT, "mean height of a1 trees, blocks");
		METRICS.put(A2_PER_CHUNK, "lower layer (a2) trees per chunk");
		METRICS.put(A2_HEIGHT, "mean height of a2 trees, blocks");
		METRICS.put(A2_SHARE, "a2 share of a1 and a2 trees, %");
		METRICS.put(TOP_HEIGHT, "top height (mean of the 100 tallest trees per ha), blocks");
		METRICS.put(COMPOSITION + "oak", "oak (both species) share of a1 trunks, %");
		METRICS.put(COMPOSITION + "linden", "linden share of a1 trunks, %");
		METRICS.put(COMPOSITION + "maple_ash", "maple and ash share of a1 trunks, %");
		METRICS.put(COMPOSITION + "hornbeam", "hornbeam share of a1 trunks, %");
		METRICS.put(COMPOSITION + "spruce", "spruce share of a1 trunks, %");
		METRICS.put(COMPOSITION + "beech", "beech share of a1 trunks, %");
		METRICS.put(COMPOSITION + "scots_pine", "Scots pine share of a1 trunks, %");
		METRICS.put(COMPOSITION + "birch", "birch share of a1 trunks, %");
		METRICS.put(COMPOSITION + "black_alder", "black alder share of a1 trunks, %");
		METRICS.put(COMPOSITION + "elm", "elm share of a1 trunks, %");
		METRICS.put(COMPOSITION + "fir", "silver fir share of a1 trunks, %");
		METRICS.put(COMPOSITION + "white_willow", "white willow share of a1 trunks, %");
		METRICS.put(COMPOSITION + "poplar", "poplar share of a1 trunks, %");
		METRICS.put(COMPOSITION + "gray_alder", "gray alder share of a1 trunks, %");
		METRICS.put(COMPOSITION + "rowan", "rowan share of a1 trunks, %");
		METRICS.put(COMPOSITION + "stone_pine", "stone pine share of a1 trunks, %");
		METRICS.put(COMPOSITION + "larch", "larch share of a1 trunks, %");
		METRICS.put(COMPOSITION + "other", "other or unknown species share of a1 trunks, %");
		METRICS.put(CANOPY, "canopy closure: columns with leaves more than 2 blocks up, %");
		METRICS.put(GAP_SHARE, "plot area in canopy gaps, %");
		METRICS.put(GAP_DIAMETER, "mean equivalent diameter of the gaps, blocks");
		METRICS.put(REGENERATION, "regeneration (young trees in the shrub layer) per ha");
		METRICS.put(REGENERATION_RATIO, "regeneration density in gaps over under the canopy");
		METRICS.put(OAK_REGENERATION, "young oaks (regeneration) per ha");
		METRICS.put(PINE_REGENERATION_UNDER_CANOPY, "young Scots pines of the tree planner under the canopy per ha");
		METRICS.put(UNDERSTORY, "understory (shrubs and regeneration) cover, %");
		METRICS.put(SHRUBS_PER_CHUNK, "shrubs per chunk");
		METRICS.put(HERBS, "herb layer cover in summer, % of columns");
		METRICS.put(HERBS_SPRING, "herb layer cover in spring, % of columns");
		METRICS.put(BILBERRY, "bilberry cover, % of columns");
		METRICS.put(LITTER, "leaf litter cover, % of columns");
		METRICS.put(MOSS, "moss cover, % of columns");
		METRICS.put(DEADWOOD_PER_CHUNK, "dead wood objects (logs, snags, stumps) per chunk");
		METRICS.put(LOGS_PER_CHUNK, "lying logs per chunk");
		METRICS.put(PIECES_PER_CHUNK, "dead trees without the stumps (logs and snags) per chunk");
		METRICS.put(STUMPS_PER_CHUNK, "stumps per chunk");
		METRICS.put(SNAGS_PER_CHUNK, "standing dead trees (snags) per chunk");
		METRICS.put(DEADWOOD_VOLUME, "dead wood volume without stumps, m3/ha");
		METRICS.put(COARSE_PIECES, "coarse dead wood pieces (> 3 m, >= 50 cm) per ha");
		METRICS.put(TRANSITION_WIDTH, "width of a transition along the transect, m·k");
		METRICS.put(TRANSITION_WIDTH_LOW, "smallest width of the interval of a transition, m·k");
		METRICS.put(TRANSITION_WIDTH_HIGH, "largest width of the interval of a transition, m·k");
		METRICS.put(PALETTE_WIDTH, "width of a transition of the palettes (tree stand, ground layer, blend field) along the transect, m·k");
		METRICS.put(TRANSECT_ORDER, "indicators in the order Bsw -> BMsw -> LMsw -> grad (1 yes, 0 no)");
		METRICS.put(CONIFERS, "conifer share of the trees of a transect stretch, %");
		METRICS.put(BARE_WOOD, "wood above half the top height with at most one leaf among its 26 neighbors, blocks per chunk");
		METRICS.put(EXPOSED_WOOD, "the part of that bare wood open to the side (no leaf within 6 blocks in one of the four "
				+ "horizontal directions), blocks per chunk");
		METRICS.put(BARE_ARMS, "bare arms of living trees: wood on its side at least 3 blocks up with at most one leaf among its 26 "
				+ "neighbors in a horizontal run of two or more, blocks per chunk");

		Forest ohn = Forest.OAK_HORNBEAM_NATURAL;
		Forest ohm = Forest.OAK_HORNBEAM_MANAGED;
		Forest fpm = Forest.FRESH_PINE_MANAGED;
		Forest fpn = Forest.FRESH_PINE_NATURAL;
		String miscicki = "[Ź Miścicki 2016; L 06 §6]";
		String layers = "[L 06 §3.5; Ź Janów]";
		String composition = "[L 06 §3.5, §6.2]";
		String closure = "[L 06 §4.1; Ź Janów]";
		String understory = "[L 06 §4.1]";
		String herbs = "[Ź Popiołek; L]";
		String deadwood = "[Ź GIOŚ 9170; Mróz i Pawlaczyk; Piętka; PW-5 b]";
		String managedDeadwood = "[Ź WISL 2010–2014; Piętka; L W13]";

		// N1 trunks per chunk (588 ± 53 trees/ha of dbh ≥ 8 cm in the strict reserve of BPN, about 15 per chunk).
		norm(TREES_PER_CHUNK, ohn, 15, 15, miscicki);
		norm(TREES_PER_CHUNK, ohm, 9, 14, miscicki);
		norm(TREES_PER_CHUNK, fpm, 8, 16, miscicki);
		norm(TREES_PER_CHUNK, fpm, AgeClass.POLE_STAGE, 15, 16, miscicki);
		norm(TREES_PER_CHUNK, fpm, AgeClass.MATURE, 10, 12, miscicki);
		norm(TREES_PER_CHUNK, fpm, AgeClass.OLD_GROWTH, 8, 10, miscicki);
		norm(TREES_PER_CHUNK, fpn, 8, 14, miscicki);
		// N2 layers.
		norm(A1_PER_CHUNK, ohn, 3, 5, layers);
		norm(A1_HEIGHT, ohn, 28, 40, layers);
		norm(A2_PER_CHUNK, ohn, 6, 9, layers);
		norm(A2_HEIGHT, ohn, 15, 22, layers);
		norm(A1_HEIGHT, ohm, 22, 30, layers);
		// Step V9: a managed oak-hornbeam forest in the pole stage (21-40 years) is lower (oak about 12-18 m) [S].
		norm(A1_HEIGHT, ohm, AgeClass.POLE_STAGE, 12, 18, layers + " [S: tablice zasobności dębu]");
		norm(A1_HEIGHT, fpm, 20, 28, layers);
		// Step V9: a managed pine forest in the pole stage (21-40 years) is lower (yield tables of the Scots pine, site
		// classes I-II: about 11-17 m at 30-40 years) [S].
		norm(A1_HEIGHT, fpm, AgeClass.POLE_STAGE, 11, 17, layers + " [S: tablice zasobności sosny]");
		norm(A2_SHARE, fpm, 0, 20, layers);
		// N3 composition of a1.
		norm(COMPOSITION + "oak", ohn, 20, 50, composition);
		norm(COMPOSITION + "linden", ohn, 10, 40, composition);
		norm(COMPOSITION + "maple_ash", ohn, 5, 15, composition);
		// Hornbeam, the name-giving tree of Tilio-Carpinetum, in the a1 of a natural stand (review of V9, round 1) [S].
		norm(COMPOSITION + "hornbeam", ohn, 10, 30, "[S]");
		norm(COMPOSITION + "spruce", ohn, 0, 30, composition + " (NE)");
		norm(COMPOSITION + "beech", ohn, 0, 30, composition + " (W)");
		norm(COMPOSITION + "oak", ohm, 50, 80, composition);
		for (Forest pine : new Forest[] {fpm, fpn}) {
			norm(COMPOSITION + "scots_pine", pine, 80, 95, composition);
			norm(COMPOSITION + "birch", pine, 5, 15, composition);
		}
		// N4 canopy closure.
		norm(CANOPY, ohn, 80, 95, closure);
		norm(CANOPY, ohm, 75, 90, closure);
		norm(CANOPY, fpm, 60, 80, closure);
		norm(CANOPY, fpn, 50, 75, closure);
		// N5 gaps: natural oak-hornbeam forest 18–45 blocks across, 3–8% of the area [S]; none in managed forests.
		wide(GAP_SHARE, ohn, 3, 8, "[Ź BPN; S]");
		wide(GAP_DIAMETER, ohn, 18, 45, "[Ź BPN]");
		wide(GAP_SHARE, ohm, 0, 0, "[Ź BPN]");
		wide(GAP_SHARE, fpm, 0, 0, "[Ź BPN]");
		// N6 regeneration: 4 times denser in gaps; the oak without regeneration (2 young oaks taller than 0.5 m per ha in
		// BPN); the Scots pine regenerates only in gaps.
		norm(REGENERATION_RATIO, ohn, 4, Double.POSITIVE_INFINITY, "[Ź BPN]");
		norm(OAK_REGENERATION, ohn, 0, 2, "[Ź Bobiec i in. 2011]");
		norm(PINE_REGENERATION_UNDER_CANOPY, fpm, 0, 0, "[Ź Paluch; Sokołowski]");
		norm(PINE_REGENERATION_UNDER_CANOPY, fpn, 0, 0, "[Ź Paluch; Sokołowski]");
		// N7 understory.
		norm(UNDERSTORY, ohn, 10, 40, understory);
		norm(UNDERSTORY, ohm, 10, 30, understory);
		norm(UNDERSTORY, fpm, 5, 30, understory);
		norm(UNDERSTORY, fpn, 10, 30, understory);
		// N8 herb layer: the oak-hornbeam forest in spring (with the geophytes) and in summer, the pine forest the same all
		// year with the bilberry (step V12: both aspects from one census, HERBS_SPRING and HERBS).
		norm(HERBS_SPRING, ohn, 60, 100, herbs);
		norm(HERBS, ohn, 20, 60, herbs);
		norm(HERBS_SPRING, ohm, 50, 90, herbs);
		for (Forest pine : new Forest[] {fpm, fpn}) {
			norm(HERBS, pine, 40, 80, herbs);
			norm(BILBERRY, pine, 40, 70, herbs);
		}
		// N9 mosses (with the mossy ground of PW-7).
		norm(MOSS, ohn, 5, 20, "[Ź Popiołek]");
		norm(MOSS, ohm, 5, 15, "[Ź Popiołek]");
		norm(MOSS, fpm, 60, 95, "[Ź Popiołek]");
		norm(MOSS, fpn, 60, 90, "[Ź Popiołek]");
		// N10 dead wood: natural forests at the level of the reserves (PW-5 b), managed ones by the WISL means.
		wide(DEADWOOD_PER_CHUNK, ohn, 2, 2, deadwood);
		wide(DEADWOOD_VOLUME, ohn, 100, 160, deadwood);
		wide(COARSE_PIECES, ohn, 5, Double.POSITIVE_INFINITY, "[Ź GIOŚ 9170]");
		// Step V11: the 0.25 objects of the managed oak-hornbeam forest are its dead trees; the stumps of the thinnings come
		// on top, about as many as in the managed pine forest [S] (decision V11-a, docs/00 G.20).
		wide(PIECES_PER_CHUNK, ohm, 0.25, 0.25, managedDeadwood);
		wide(STUMPS_PER_CHUNK, ohm, 1, 2, managedDeadwood + " [S]");
		wide(DEADWOOD_VOLUME, ohm, 10, 16, managedDeadwood);
		wide(LOGS_PER_CHUNK, fpm, 0.1, 0.1, managedDeadwood);
		wide(STUMPS_PER_CHUNK, fpm, 1, 2, managedDeadwood);
		wide(DEADWOOD_VOLUME, fpm, 5, 11, managedDeadwood);
		wide(DEADWOOD_PER_CHUNK, fpn, 1, 1, deadwood + " (Taborz)");
		wide(DEADWOOD_VOLUME, fpn, 50, 80, deadwood + " (Taborz)");
		beechCard();
		alderCard();
		mountainCard();
		pineCard();
		floodplainCard();
		montaneSpruceCard();
		stonePineCard();
		// N15 transect: the same for every forest of the transect (the transect crosses all four).
		for (Forest f : Forest.values()) {
			if (!f.transect()) {
				continue;
			}
			norm(TRANSITION_WIDTH, f, 20, 100, "[Ź Stefańska 2007; S]");
			norm(PALETTE_WIDTH, f, 20, 100, "[Ź Stefańska 2007; S]");
			NORMS.add(new Norm(TRANSECT_ORDER, f, 1, 1, 0, null, null, "[Ź Stefańska 2007]"));
		}
	}

	/**
	 * The card of the lowland beech forest (step F1, docs/04 §6): L 06 §3.6 (Luzulo pilosae-Fagetum and Galio odorati-
	 * Fagetum: a1 beech 70–100%, closure 80–95%, b 0–10/0–15%, c 5–40% acid and 20–70% fertile, d 5–30% acid and 0–10%
	 * fertile), the dead wood of L 06 §4.3 (natural stands at the level of the reserves as the natural oak-hornbeam forest
	 * of V11, managed ones the WISL mean of the broadleaved lowland sites, 17 m³/ha with the stumps) [S where noted].
	 */
	private static void beechCard() {
		String card = "[L 06 §3.6]";
		String deadwood = "[L 06 §4.3; PW-5 b; S]";
		String managedDeadwood = "[Ź WISL 2010–2014; L 06 §4.3; S]";
		for (Forest f : new Forest[] {Forest.LOWLAND_BEECH_NATURAL, Forest.LOWLAND_BEECH_MANAGED}) {
			boolean natural = f.natural();
			// N1 trunks per chunk: the old beeches stand wide (200-350 trees per ha of 7 cm and more in the beech reserves of
			// Pomerania, 5-9 per chunk); managed stands thin out with age as the managed pine forest [S].
			if (natural) {
				norm(TREES_PER_CHUNK, f, 6, 12, "[L 06 §3.6; S]");
			} else {
				norm(TREES_PER_CHUNK, f, 7, 15, "[L 06 §3.6; S]");
				norm(TREES_PER_CHUNK, f, AgeClass.POLE_STAGE, 12, 15, "[L 06 §3.6; S]");
				norm(TREES_PER_CHUNK, f, AgeClass.MATURE, 9, 12, "[L 06 §3.6; S]");
				norm(TREES_PER_CHUNK, f, AgeClass.OLD_GROWTH, 7, 10, "[L 06 §3.6; S]");
			}
			// N3 the beech in a1, N4 closure, N7 the shrub layer (b 0–10% acid, 0–15% fertile), N9 the mosses.
			norm(COMPOSITION + "beech", f, 70, 100, card);
			norm(CANOPY, f, 80, 95, card);
			norm(UNDERSTORY, f, 0, 15, card);
			norm(MOSS, f, 0, 30, card);
			// N8 the herb layer: the fertile form in spring 20–70% (woodruff, anemone, wood sorrel), the acid one 5–40% all
			// year (hairy wood rush, wood sorrel, bilberry in patches); a plot of unknown form: the union.
			NORMS.add(new Norm(HERBS_SPRING, f, 20, 70, TOLERANCE, null, null, card, Site.FERTILE));
			NORMS.add(new Norm(HERBS_SPRING, f, 5, 40, TOLERANCE, null, null, card, Site.ACID));
			NORMS.add(new Norm(HERBS, f, 5, 40, TOLERANCE, null, null, card, Site.ACID));
			norm(HERBS_SPRING, f, 5, 70, card + " [S: żyzna lub kwaśna]");
		}
		Forest bkn = Forest.LOWLAND_BEECH_NATURAL;
		Forest bkm = Forest.LOWLAND_BEECH_MANAGED;
		// N10 dead wood.
		wide(DEADWOOD_PER_CHUNK, bkn, 2, 2, deadwood);
		wide(DEADWOOD_VOLUME, bkn, 80, 150, deadwood);
		wide(COARSE_PIECES, bkn, 5, Double.POSITIVE_INFINITY, deadwood);
		wide(PIECES_PER_CHUNK, bkm, 0.25, 0.25, managedDeadwood);
		wide(STUMPS_PER_CHUNK, bkm, 1, 2, managedDeadwood);
		wide(DEADWOOD_VOLUME, bkm, 10, 17, managedDeadwood);
	}

	/**
	 * The card of the alder carr and the ash-alder forest (step F2, docs/04 "Projekt F2"): L 06 §4.5–4.6 and research 08
	 * (Ribeso nigri-Alnetum: a1 black alder 80–100%, closure 60–80%, b 20–50%, c 60–90%; Fraxino-Alnetum: a1 alder
	 * 50–90% with ash 10–40%, closure 70–90%, b 20–50% with the bird cherry, c 70–100%; mosses on the tussocks and logs),
	 * the dead wood as the other broadleaved forests of the planner [S where noted].
	 */
	private static void alderCard() {
		String card = "[L 06 §4.5–4.6; L 08]";
		String deadwood = "[L 06 §4.3; PW-5 b; S]";
		String managedDeadwood = "[Ź WISL 2010–2014; S]";
		Forest[] carr = {Forest.ALDER_CARR_NATURAL, Forest.ALDER_CARR_MANAGED};
		Forest[] ash = {Forest.ASH_ALDER_NATURAL, Forest.ASH_ALDER_MANAGED};
		for (Forest f : carr) {
			// N1 trunks per chunk: 400-800 trees per ha of the alder carr [S].
			norm(TREES_PER_CHUNK, f, 8, 16, card + " [S]");
			norm(COMPOSITION + "black_alder", f, 80, 100, card);
			norm(CANOPY, f, 60, 80, card);
			norm(UNDERSTORY, f, 20, 50, card);
			norm(HERBS, f, 60, 90, card);
			norm(HERBS_SPRING, f, 60, 90, card);
			norm(MOSS, f, 10, 40, card + " [S]");
		}
		for (Forest f : ash) {
			norm(TREES_PER_CHUNK, f, 8, 16, card + " [S]");
			norm(COMPOSITION + "black_alder", f, 50, 90, card);
			// The ash in the group of the maples and the ash (no maple in the ash-alder forest).
			norm(COMPOSITION + "maple_ash", f, 10, 40, card);
			norm(CANOPY, f, 70, 90, card);
			norm(UNDERSTORY, f, 20, 50, card);
			norm(HERBS, f, 70, 100, card);
			norm(HERBS_SPRING, f, 70, 100, card);
			norm(MOSS, f, 5, 20, card + " [S]");
		}
		// N10 dead wood.
		wide(DEADWOOD_PER_CHUNK, Forest.ALDER_CARR_NATURAL, 1.6, 1.6, deadwood);
		wide(DEADWOOD_VOLUME, Forest.ALDER_CARR_NATURAL, 60, 120, deadwood);
		wide(DEADWOOD_PER_CHUNK, Forest.ASH_ALDER_NATURAL, 1.8, 1.8, deadwood);
		wide(DEADWOOD_VOLUME, Forest.ASH_ALDER_NATURAL, 70, 140, deadwood);
		for (Forest f : new Forest[] {Forest.ALDER_CARR_MANAGED, Forest.ASH_ALDER_MANAGED}) {
			wide(PIECES_PER_CHUNK, f, 0.2, 0.25, managedDeadwood);
			wide(STUMPS_PER_CHUNK, f, 1, 2, managedDeadwood);
			wide(DEADWOOD_VOLUME, f, 8, 16, managedDeadwood);
		}
	}

	/**
	 * The card of the upland fir forest and the Carpathian beech forest with its sycamore ravine forest (step F3, docs/04
	 * "Projekt F3"): L 06 §3.3, §3.6, §3.8 and §4.3 (Abietetum polonicum and Galio-Abietetum: a1 fir 50–90% with beech,
	 * spruce and pine, a many-storied stand, closure 70–95%, b of the fir regeneration 20–50%, c 20–60%, d 30–70%;
	 * Dentario glandulosae-Fagetum: a1 beech 50–90% with fir 10–40% and sycamore 5–15%, closure 70–95%, b 5–30%, c in
	 * spring 30–80% and in summer 10–50%; Luzulo luzuloidis-Fagetum: c 10–50%; Lunario-Aceretum: a1 sycamore 50–90%, c
	 * 50–90%), the dead wood of the natural mountain forests (the Carpathian reserves, 100–200 m³/ha) and of the managed
	 * ones (the WISL mean of the mountain sites) [S where noted].
	 */
	private static void mountainCard() {
		String card = "[L 06 §3.3, §3.6, §3.8]";
		String deadwood = "[L 06 §4.3; PW-5 b; S]";
		String managedDeadwood = "[Ź WISL 2010–2014; L 06 §4.3; S]";
		for (Forest f : new Forest[] {Forest.UPLAND_FIR_NATURAL, Forest.UPLAND_FIR_MANAGED}) {
			// N1 trunks per chunk: the many-storied fir stands are dense (300-600 trees per ha of 7 cm and more) [S].
			norm(TREES_PER_CHUNK, f, 8, 18, card + " [S]");
			norm(COMPOSITION + "fir", f, 50, 90, card);
			norm(CANOPY, f, 70, 95, card);
			// N7 the shrub layer of the fir regeneration (the fir regenerates densely under its own canopy).
			norm(UNDERSTORY, f, 20, 50, card);
			norm(HERBS, f, 20, 60, card + " [S]");
			norm(HERBS_SPRING, f, 20, 60, card + " [S]");
			norm(MOSS, f, 30, 70, card);
		}
		for (Forest f : new Forest[] {Forest.MONTANE_BEECH_NATURAL, Forest.MONTANE_BEECH_MANAGED}) {
			if (f.natural()) {
				norm(TREES_PER_CHUNK, f, 6, 12, card + " [S]");
			} else {
				norm(TREES_PER_CHUNK, f, 7, 15, card + " [S]");
			}
			// N3: the beech with fir and sycamore; the ravine forest of the sycamore (the maples and the ash, its group).
			norm(COMPOSITION + "beech", f, 50, 90, card);
			NORMS.add(new Norm(COMPOSITION + "beech", f, 5, 40, TOLERANCE, null, null, card + " [S]", Site.RAVINE));
			norm(COMPOSITION + "fir", f, 10, 40, card);
			NORMS.add(new Norm(COMPOSITION + "fir", f, 0, 20, TOLERANCE, null, null, card + " [S]", Site.RAVINE));
			norm(COMPOSITION + "maple_ash", f, 0, 15, card + " [S: żyzna lub kwaśna]");
			NORMS.add(new Norm(COMPOSITION + "maple_ash", f, 5, 15, TOLERANCE, null, null, card, Site.FERTILE));
			NORMS.add(new Norm(COMPOSITION + "maple_ash", f, 55, 95, TOLERANCE, null, null, card, Site.RAVINE));
			norm(CANOPY, f, 70, 95, card);
			norm(UNDERSTORY, f, 5, 30, card);
			norm(MOSS, f, 0, 20, card + " [S]");
			// N8 the herb layer: the fertile form's toothwort carpets in spring, the acid form's wood-rush all year, the
			// ravine's tall herbs; a plot of unknown form: the union.
			NORMS.add(new Norm(HERBS_SPRING, f, 30, 80, TOLERANCE, null, null, card, Site.FERTILE));
			NORMS.add(new Norm(HERBS, f, 10, 50, TOLERANCE, null, null, card, Site.FERTILE));
			NORMS.add(new Norm(HERBS_SPRING, f, 10, 50, TOLERANCE, null, null, card, Site.ACID));
			NORMS.add(new Norm(HERBS, f, 10, 50, TOLERANCE, null, null, card, Site.ACID));
			NORMS.add(new Norm(HERBS_SPRING, f, 50, 90, TOLERANCE, null, null, card, Site.RAVINE));
			NORMS.add(new Norm(HERBS, f, 50, 90, TOLERANCE, null, null, card, Site.RAVINE));
			norm(HERBS_SPRING, f, 10, 90, card + " [S: każda postać]");
		}
		// N10 dead wood.
		wide(DEADWOOD_PER_CHUNK, Forest.UPLAND_FIR_NATURAL, 2, 2, deadwood);
		wide(DEADWOOD_VOLUME, Forest.UPLAND_FIR_NATURAL, 80, 150, deadwood);
		wide(DEADWOOD_PER_CHUNK, Forest.MONTANE_BEECH_NATURAL, 2.2, 2.2, deadwood);
		wide(DEADWOOD_VOLUME, Forest.MONTANE_BEECH_NATURAL, 100, 200, deadwood);
		wide(COARSE_PIECES, Forest.MONTANE_BEECH_NATURAL, 5, Double.POSITIVE_INFINITY, deadwood);
		for (Forest f : new Forest[] {Forest.UPLAND_FIR_MANAGED, Forest.MONTANE_BEECH_MANAGED}) {
			wide(PIECES_PER_CHUNK, f, 0.25, 0.25, managedDeadwood);
			wide(STUMPS_PER_CHUNK, f, 1, 2, managedDeadwood);
			wide(DEADWOOD_VOLUME, f, 10, 20, managedDeadwood);
		}
	}

	/**
	 * The card of the pine forests and the bog woodland (step F4, docs/04 "Projekt F4"): L 06 §3.1-3.2 and §4.1
	 * (Cladonio-Pinetum: a1 pine 90–100% of 10–18 m, closure 40–60%, b juniper 0–5%, c heather and cowberry 10–40%, d the
	 * lichens 60–90%; Molinio-Pinetum: pine 70–90%, downy birch 10–30%, spruce in the north-east, b the alder buckthorn
	 * 10–30%, c the purple moor-grass in tussocks 40–80%, d with the sphagnum of the hollows 50–80%; Vaccinio uliginosi-
	 * Pinetum: low crooked pines 60–90% of 6–15 m, birch 10–30%, closure 30–60%, c Labrador tea 20–60% and bog bilberry
	 * 20–50%, d the sphagnum 80–100%; its birch form Vaccinio uliginosi-Betuletum: birch 60–90%; Empetro nigri-Pinetum:
	 * pine 70–90% with birch, the crowberry dominant), N9 counts the lichens of the dry pine forest and the sphagnum of the
	 * bog woodland (ForestCensus.MOSS_PLANTS, MOSS_GROUNDS), the dead wood as the natural fresh pine forest (Taborz) and the
	 * WISL means of the managed pine stands [S where noted].
	 */
	private static void pineCard() {
		String card = "[L 06 §3.1–3.2, §4.1]";
		String deadwood = "[L 06 §4.3; Taborz; PW-5 b; S]";
		String managedDeadwood = "[Ź WISL 2010–2014; S]";
		for (Forest f : new Forest[] {Forest.DRY_PINE_NATURAL, Forest.DRY_PINE_MANAGED}) {
			// N1: the thin pines of the poorest sand stand as densely as those of the fresh pine forest, their crowns narrow [S].
			norm(TREES_PER_CHUNK, f, 8, 16, card + " [S]");
			norm(COMPOSITION + "scots_pine", f, 90, 100, card);
			norm(CANOPY, f, 40, 60, card);
			norm(UNDERSTORY, f, 0, 5, card);
			norm(HERBS, f, 10, 40, card);
			norm(MOSS, f, 60, 90, card + " (porosty)");
		}
		for (Forest f : new Forest[] {Forest.MOIST_PINE_NATURAL, Forest.MOIST_PINE_MANAGED}) {
			norm(TREES_PER_CHUNK, f, 8, 16, card + " [S]");
			norm(COMPOSITION + "scots_pine", f, 70, 90, card);
			norm(COMPOSITION + "birch", f, 10, 30, card);
			norm(CANOPY, f, 60, 80, card + " [S]");
			norm(UNDERSTORY, f, 10, 30, card);
			norm(HERBS, f, 40, 80, card);
			norm(MOSS, f, 50, 80, card);
		}
		for (Forest f : new Forest[] {Forest.BOG_WOODLAND_NATURAL, Forest.BOG_WOODLAND_MANAGED}) {
			// N1: the low trees of the bog stand open [S]; the birch form by its site variant.
			norm(TREES_PER_CHUNK, f, 6, 14, card + " [S]");
			norm(COMPOSITION + "scots_pine", f, 60, 90, card);
			NORMS.add(new Norm(COMPOSITION + "scots_pine", f, 10, 40, TOLERANCE, null, null, card + " [S]", Site.BIRCH_BOG));
			norm(COMPOSITION + "birch", f, 10, 30, card);
			NORMS.add(new Norm(COMPOSITION + "birch", f, 60, 90, TOLERANCE, null, null, card, Site.BIRCH_BOG));
			norm(CANOPY, f, 30, 60, card);
			norm(UNDERSTORY, f, 0, 20, card + " [S]");
			norm(HERBS, f, 40, 80, card);
			norm(MOSS, f, 80, 100, card + " (torfowce)");
		}
		for (Forest f : new Forest[] {Forest.COASTAL_PINE_NATURAL, Forest.COASTAL_PINE_MANAGED}) {
			norm(TREES_PER_CHUNK, f, 8, 16, card + " [S]");
			norm(COMPOSITION + "scots_pine", f, 70, 90, card);
			norm(COMPOSITION + "birch", f, 5, 30, card + " [S]");
			norm(CANOPY, f, 50, 80, card + " [S]");
			norm(UNDERSTORY, f, 0, 15, card + " [S]");
			norm(HERBS, f, 40, 80, card);
			norm(MOSS, f, 30, 70, card + " [S]");
		}
		// N10 dead wood.
		wide(DEADWOOD_PER_CHUNK, Forest.DRY_PINE_NATURAL, 0.9, 0.9, deadwood);
		wide(DEADWOOD_VOLUME, Forest.DRY_PINE_NATURAL, 20, 50, deadwood);
		wide(DEADWOOD_PER_CHUNK, Forest.MOIST_PINE_NATURAL, 1.1, 1.1, deadwood);
		wide(DEADWOOD_VOLUME, Forest.MOIST_PINE_NATURAL, 40, 80, deadwood);
		wide(DEADWOOD_PER_CHUNK, Forest.BOG_WOODLAND_NATURAL, 1.2, 1.2, deadwood);
		wide(DEADWOOD_VOLUME, Forest.BOG_WOODLAND_NATURAL, 15, 45, deadwood);
		wide(DEADWOOD_PER_CHUNK, Forest.COASTAL_PINE_NATURAL, 1, 1, deadwood);
		wide(DEADWOOD_VOLUME, Forest.COASTAL_PINE_NATURAL, 30, 70, deadwood);
		for (Forest f : new Forest[] {Forest.DRY_PINE_MANAGED, Forest.MOIST_PINE_MANAGED, Forest.BOG_WOODLAND_MANAGED,
				Forest.COASTAL_PINE_MANAGED}) {
			wide(PIECES_PER_CHUNK, f, 0.1, 0.25, managedDeadwood);
			wide(STUMPS_PER_CHUNK, f, 1, 2, managedDeadwood);
			wide(DEADWOOD_VOLUME, f, 3, 11, managedDeadwood);
		}
	}

	/**
	 * The card of the floodplain forests and the osier scrub (step F5, docs/04 "Projekt F5"): L 06 §4.5 (Salicetum albae:
	 * a1 white willow 50–80% with poplar, closure 50–70%, b of the osier and the young willows 10–40%, c of the nettle and
	 * the reed canary grass 70–100%, mosses scarce on the silt; Populetum albae: a1 poplar 30–70%; Ficario-Ulmetum: a1 oak
	 * 20–50%, ash 20–50% and elm 10–40% with maple and linden, closure 70–90%, b of the bird cherry, the elder and the
	 * dogwood 20–60%, c in spring the lesser celandine carpets 70–100%, in summer the nettle 50–90%; Salicetum
	 * triandro-viminalis: the osier 80–100%, research 08), the dead wood of the natural floodplain forests as the ash-alder
	 * forest's, more in the willow forest (the brittle willows, the floods), and the WISL means of the managed stands [S where
	 * noted].
	 */
	private static void floodplainCard() {
		String card = "[L 06 §4.5]";
		String deadwood = "[L 06 §4.3; PW-5 b; S]";
		String managedDeadwood = "[Ź WISL 2010–2014; S]";
		for (Forest f : new Forest[] {Forest.ELM_ASH_NATURAL, Forest.ELM_ASH_MANAGED}) {
			norm(TREES_PER_CHUNK, f, 8, 16, card + " [S]");
			norm(COMPOSITION + "oak", f, 20, 50, card);
			// The ash with the maples (their group); the card's ash 20–50% and some maple.
			norm(COMPOSITION + "maple_ash", f, 20, 55, card + " [S: z klonem]");
			norm(COMPOSITION + "elm", f, 10, 40, card);
			norm(CANOPY, f, 70, 90, card);
			norm(UNDERSTORY, f, 20, 60, card);
			norm(HERBS_SPRING, f, 70, 100, card);
			norm(HERBS, f, 50, 90, card + " [S]");
			norm(MOSS, f, 0, 15, card + " [S]");
		}
		for (Forest f : new Forest[] {Forest.WILLOW_POPLAR_NATURAL, Forest.WILLOW_POPLAR_MANAGED}) {
			boolean natural = f == Forest.WILLOW_POPLAR_NATURAL;
			// N1: the large crowns of the old willows and poplars stand far apart [S]; the even-aged managed pole stage denser.
			norm(TREES_PER_CHUNK, f, 4, natural ? 10 : 14, card + " [S]");
			if (natural) {
				norm(COMPOSITION + "white_willow", f, 50, 80, card);
				NORMS.add(new Norm(COMPOSITION + "white_willow", f, 10, 40, TOLERANCE, null, null, card + " [S]", Site.POPLAR));
				norm(COMPOSITION + "poplar", f, 10, 40, card + " [S]");
				NORMS.add(new Norm(COMPOSITION + "poplar", f, 30, 70, TOLERANCE, null, null, card, Site.POPLAR));
				norm(CANOPY, f, 50, 70, card);
			} else {
				// The managed floodplain stands are mostly poplar plantings: the taller even-aged poplars hold the a1, the
				// willows stay in the a2; a closed canopy of the planted rows [S].
				norm(COMPOSITION + "white_willow", f, 0, 60, card + " [S: gospodarcze]");
				NORMS.add(new Norm(COMPOSITION + "white_willow", f, 0, 40, TOLERANCE, null, null, card + " [S: gospodarcze]", Site.POPLAR));
				norm(COMPOSITION + "poplar", f, 30, 95, card + " [S: gospodarcze]");
				NORMS.add(new Norm(COMPOSITION + "poplar", f, 50, 95, TOLERANCE, null, null, card + " [S: gospodarcze]", Site.POPLAR));
				norm(CANOPY, f, 60, 95, card + " [S: gospodarcze]");
			}
			norm(UNDERSTORY, f, 10, 40, card);
			norm(HERBS, f, 70, 100, card);
			norm(HERBS_SPRING, f, 70, 100, card);
			norm(MOSS, f, 0, 10, card + " [S]");
		}
		// The osier scrub: the shrub cover of the osier (b), few trees, the tall herbs under it.
		norm(UNDERSTORY, Forest.WILLOW_SCRUB, 80, 100, card + " [L 08]");
		norm(TREES_PER_CHUNK, Forest.WILLOW_SCRUB, 0, 2, card + " [S]");
		norm(HERBS, Forest.WILLOW_SCRUB, 40, 90, card + " [S]");
		// N10 dead wood.
		// The large oaks, ashes and old willows give much volume per object: fewer objects than the ash-alder forest for a
		// similar or larger volume [S].
		wide(DEADWOOD_PER_CHUNK, Forest.ELM_ASH_NATURAL, 1.5, 1.5, deadwood);
		wide(DEADWOOD_VOLUME, Forest.ELM_ASH_NATURAL, 90, 170, deadwood);
		wide(DEADWOOD_PER_CHUNK, Forest.WILLOW_POPLAR_NATURAL, 1.3, 1.3, deadwood);
		wide(DEADWOOD_VOLUME, Forest.WILLOW_POPLAR_NATURAL, 100, 220, deadwood);
		for (Forest f : new Forest[] {Forest.ELM_ASH_MANAGED, Forest.WILLOW_POPLAR_MANAGED}) {
			wide(PIECES_PER_CHUNK, f, 0.2, 0.25, managedDeadwood);
			wide(STUMPS_PER_CHUNK, f, 1, 2, managedDeadwood);
			wide(DEADWOOD_VOLUME, f, 8, 16, managedDeadwood);
		}
	}

	/**
	 * The card of the montane spruce forest, the gray alder forest and the dwarf pine scrub (step F6, docs/04 "Projekt
	 * F6"): L 06 §3.4-3.5 and §4.4 (Plagiothecio-Piceetum: a1 spruce 90–100% of narrow crowns in clumps, rowan, closure
	 * 50–80%, b 0–10% (rowan and the spruce's regeneration), c the bilberry 20–60% with the hairy small-reed, d mosses
	 * 60–95%; Abieti-Piceetum montanum: fir 20–50%; Alnetum incanae: a1 gray alder 60–100% with ash and spruce, closure
	 * 60–85%, b 10–40%, c the butterbur and the tall herbs 70–100%; Pinetum mugo carpaticum: the dwarf pine 50–90% as the
	 * shrub layer b, a few dwarf spruces and rowans, c the bilberry and the small-reed), the dead wood of the natural
	 * Carpathian spruce forests (the windthrows and the bark beetle, 100–300 m³/ha) and of the managed ones (the WISL mean of
	 * the mountain sites) [S where noted].
	 */
	private static void montaneSpruceCard() {
		String card = "[L 06 §3.4–3.5, §4.4]";
		String deadwood = "[L 06 §4.3; PW-5 b; S]";
		String managedDeadwood = "[Ź WISL 2010–2014; L 06 §4.3; S]";
		for (Forest f : new Forest[] {Forest.MONTANE_SPRUCE_NATURAL, Forest.MONTANE_SPRUCE_MANAGED}) {
			boolean natural = f.natural();
			// N1: the narrow candle spruces stand dense in their clumps, with the windthrow gaps between them [S].
			norm(TREES_PER_CHUNK, f, natural ? 6 : 8, natural ? 14 : 16, card + " [S]");
			norm(COMPOSITION + "spruce", f, 80, 100, card);
			NORMS.add(new Norm(COMPOSITION + "spruce", f, 45, 80, TOLERANCE, null, null, card, Site.FIR_SPRUCE));
			NORMS.add(new Norm(COMPOSITION + "fir", f, 20, 50, TOLERANCE, null, null, card, Site.FIR_SPRUCE));
			norm(COMPOSITION + "rowan", f, 0, 15, card + " [S]");
			norm(CANOPY, f, natural ? 50 : 60, natural ? 80 : 90, card + (natural ? "" : " [S: gospodarcze]"));
			norm(UNDERSTORY, f, 0, 15, card + " [S]");
			norm(HERBS, f, 40, 90, card + " [S]");
			norm(BILBERRY, f, 20, 60, card);
			norm(MOSS, f, 60, 95, card);
		}
		wide(GAP_SHARE, Forest.MONTANE_SPRUCE_NATURAL, 5, 20, card + " [S]");
		for (Forest f : new Forest[] {Forest.GRAY_ALDER_NATURAL, Forest.GRAY_ALDER_MANAGED}) {
			norm(TREES_PER_CHUNK, f, 8, 16, card + " [S]");
			norm(COMPOSITION + "gray_alder", f, 60, 100, card);
			norm(CANOPY, f, 60, 85, card);
			norm(UNDERSTORY, f, 10, 40, card);
			norm(HERBS, f, 70, 100, card);
			norm(HERBS_SPRING, f, 60, 100, card + " [S]");
			norm(MOSS, f, 0, 15, card + " [S]");
		}
		// The dwarf pine scrub: the cover of the creeping shrubs (b), a few low trees, the dwarf shrubs and mosses under it.
		Forest dp = Forest.DWARF_PINE_NATURAL;
		norm(UNDERSTORY, dp, 50, 90, card);
		norm(TREES_PER_CHUNK, dp, 0, 3, card + " [S]");
		norm(HERBS, dp, 20, 70, card + " [S]");
		norm(BILBERRY, dp, 10, 50, card + " [S]");
		norm(MOSS, dp, 20, 70, card + " [S]");
		// N10 dead wood.
		wide(DEADWOOD_PER_CHUNK, Forest.MONTANE_SPRUCE_NATURAL, 2.5, 2.5, deadwood);
		wide(DEADWOOD_VOLUME, Forest.MONTANE_SPRUCE_NATURAL, 100, 250, deadwood);
		wide(COARSE_PIECES, Forest.MONTANE_SPRUCE_NATURAL, 5, Double.POSITIVE_INFINITY, deadwood);
		wide(DEADWOOD_PER_CHUNK, Forest.GRAY_ALDER_NATURAL, 1.4, 1.4, deadwood);
		wide(DEADWOOD_VOLUME, Forest.GRAY_ALDER_NATURAL, 40, 100, deadwood);
		// The managed spruce stands keep more pieces (the windthrows left after the salvage), the managed gray alder stands
		// less volume (small, soft wood) [S].
		wide(PIECES_PER_CHUNK, Forest.MONTANE_SPRUCE_MANAGED, 0.2, 0.5, managedDeadwood);
		wide(STUMPS_PER_CHUNK, Forest.MONTANE_SPRUCE_MANAGED, 1, 2, managedDeadwood);
		wide(DEADWOOD_VOLUME, Forest.MONTANE_SPRUCE_MANAGED, 5, 20, managedDeadwood);
		wide(PIECES_PER_CHUNK, Forest.GRAY_ALDER_MANAGED, 0.15, 0.3, managedDeadwood);
		wide(STUMPS_PER_CHUNK, Forest.GRAY_ALDER_MANAGED, 1, 2, managedDeadwood);
		wide(DEADWOOD_VOLUME, Forest.GRAY_ALDER_MANAGED, 2, 12, managedDeadwood);
	}

	/**
	 * The card of the spruce-stone pine forest of the Tatras (step TT4, docs/04 "Projekt TT4"): a site variant of the natural
	 * montane spruce forest (the Tatra National Park, no managed stands; choice TT4-c). L 06 §3.5, §5.1 (Pino
	 * cembrae-Piceetum: a1 spruce 40–60%, stone pine 20–40%, larch 5–20%, rowan; 15–20 m, a loose stand of clumps at the
	 * timberline, b the rowan, the dwarf pine and the young stone pines, c the bilberry with the small-reed and the alpine
	 * coltsfoot, d mosses), the dead wood of the natural high montane stands (the snags and broken tops of the stone pine,
	 * the windthrows of the spruce) [S where noted].
	 */
	private static void stonePineCard() {
		String card = "[L 06 §3.5, §5.1]";
		String deadwood = "[L 06 §4.3; PW-5 b; S]";
		Forest f = Forest.MONTANE_SPRUCE_NATURAL;
		Site site = Site.STONE_PINE;
		// N1: a loose stand of clumps, the stone pines smaller than the spruces [S].
		NORMS.add(new Norm(TREES_PER_CHUNK, f, 5, 13, TOLERANCE, null, null, card + " [S]", site));
		NORMS.add(new Norm(COMPOSITION + "spruce", f, 40, 60, TOLERANCE, null, null, card, site));
		NORMS.add(new Norm(COMPOSITION + "stone_pine", f, 20, 40, TOLERANCE, null, null, card, site));
		NORMS.add(new Norm(COMPOSITION + "larch", f, 5, 20, TOLERANCE, null, null, card, site));
		NORMS.add(new Norm(COMPOSITION + "rowan", f, 0, 15, TOLERANCE, null, null, card + " [S]", site));
		NORMS.add(new Norm(CANOPY, f, 40, 70, TOLERANCE, null, null, card + " [S]", site));
		NORMS.add(new Norm(UNDERSTORY, f, 0, 20, TOLERANCE, null, null, card + " [S]", site));
		NORMS.add(new Norm(HERBS, f, 40, 90, TOLERANCE, null, null, card + " [S]", site));
		NORMS.add(new Norm(BILBERRY, f, 20, 60, TOLERANCE, null, null, card, site));
		NORMS.add(new Norm(MOSS, f, 50, 95, TOLERANCE, null, null, card + " [S]", site));
		// N10: less dead wood than the spruce forest (the windfirm stone pines die standing, slowly) [S].
		NORMS.add(new Norm(DEADWOOD_PER_CHUNK, f, 2.5, 2.5, TOLERANCE_WIDE, null, null, deadwood, site));
		NORMS.add(new Norm(DEADWOOD_VOLUME, f, 60, 200, TOLERANCE_WIDE, null, null, deadwood, site));
		NORMS.add(new Norm(COARSE_PIECES, f, 3, Double.POSITIVE_INFINITY, TOLERANCE_WIDE, null, null, deadwood, site));
	}

	private static void norm(String metric, Forest forest, double min, double max, String source) {
		NORMS.add(new Norm(metric, forest, min, max, TOLERANCE, null, null, source));
	}

	private static void norm(String metric, Forest forest, Aspect aspect, double min, double max, String source) {
		NORMS.add(new Norm(metric, forest, min, max, TOLERANCE, aspect, null, source));
	}

	private static void norm(String metric, Forest forest, AgeClass age, double min, double max, String source) {
		NORMS.add(new Norm(metric, forest, min, max, TOLERANCE, null, age, source));
	}

	private static void wide(String metric, Forest forest, double min, double max, String source) {
		NORMS.add(new Norm(metric, forest, min, max, TOLERANCE_WIDE, null, null, source));
	}

	/** All norms of the card, in the order they were defined. */
	public static List<Norm> all() {
		return List.copyOf(NORMS);
	}

	/**
	 * The norm of a metric for a forest: the one for the given aspect and age class if there is one, else the one without
	 * a qualifier. A norm that has only qualified rows (the herb layer of the oak-hornbeam forest) gives nothing for
	 * another or an unknown aspect.
	 */
	public static Optional<Norm> of(String metric, Forest forest, @Nullable Aspect aspect, @Nullable AgeClass age) {
		return of(metric, forest, aspect, age, null);
	}

	/** {@link #of(String, Forest, Aspect, AgeClass)} for a plot of a known site variant (step F1; null: unknown). */
	public static Optional<Norm> of(String metric, Forest forest, @Nullable Aspect aspect, @Nullable AgeClass age,
			@Nullable Site site) {
		Norm general = null;
		for (Norm n : NORMS) {
			if (!n.metric().equals(metric) || n.forest() != forest) {
				continue;
			}
			boolean aspectOk = n.aspect() == null || n.aspect() == aspect;
			boolean ageOk = n.age() == null || n.age() == age;
			boolean siteOk = n.site() == null || n.site() == site;
			if (!aspectOk || !ageOk || !siteOk) {
				continue;
			}
			if (n.aspect() != null || n.age() != null || n.site() != null) {
				return Optional.of(n);
			}
			general = n;
		}
		return Optional.ofNullable(general);
	}

	/** Compares a measured value with a norm; NaN (nothing measured) gives {@link Verdict#NONE}. */
	public static Verdict verdict(double value, Norm norm) {
		if (Double.isNaN(value)) {
			return Verdict.NONE;
		}
		if (value >= norm.min() && value <= norm.max()) {
			return Verdict.IN;
		}
		// A small epsilon: values computed as shares (e.g. 0.1 logs per chunk) must not fall out by rounding.
		double eps = 1e-9;
		return value >= norm.low() - eps && value <= norm.high() + eps ? Verdict.TOLERANCE : Verdict.OUT;
	}

	/** A number for the card: at most 2 decimals, no trailing zeros, a dot as the separator; infinity {@code inf}. */
	public static String number(double v) {
		if (Double.isNaN(v)) {
			return "";
		}
		if (Double.isInfinite(v)) {
			return v > 0 ? "inf" : "-inf";
		}
		String s = String.format(Locale.ROOT, "%.2f", v);
		s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
		return s.equals("-0") ? "0" : s;
	}
}
