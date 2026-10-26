package pl.polishforests.worldgen.norms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import pl.polishforests.season.SubSeason;
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

	/** The forests of the card: the columns of §6. */
	public enum Forest {
		OAK_HORNBEAM_NATURAL("grąd naturalny"),
		OAK_HORNBEAM_MANAGED("grąd gospodarczy"),
		FRESH_PINE_MANAGED("bór świeży gospodarczy"),
		FRESH_PINE_NATURAL("bór naturalny");

		private final String polishName;

		Forest(String polishName) {
			this.polishName = polishName;
		}

		/** The column name of the card in docs/04 §6. */
		public String polishName() {
			return polishName;
		}
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
			"scots_pine", "birch", "other");

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
	 */
	public record Norm(String metric, Forest forest, double min, double max, double tolerance, @Nullable Aspect aspect,
			@Nullable AgeClass age, String source) {
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
		// N15 transect: the same for every forest of the card (the transect crosses all four).
		for (Forest f : Forest.values()) {
			norm(TRANSITION_WIDTH, f, 20, 100, "[Ź Stefańska 2007; S]");
			norm(PALETTE_WIDTH, f, 20, 100, "[Ź Stefańska 2007; S]");
			NORMS.add(new Norm(TRANSECT_ORDER, f, 1, 1, 0, null, null, "[Ź Stefańska 2007]"));
		}
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
		Norm general = null;
		for (Norm n : NORMS) {
			if (!n.metric().equals(metric) || n.forest() != forest) {
				continue;
			}
			boolean aspectOk = n.aspect() == null || n.aspect() == aspect;
			boolean ageOk = n.age() == null || n.age() == age;
			if (!aspectOk || !ageOk) {
				continue;
			}
			if (n.aspect() != null || n.age() != null) {
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
