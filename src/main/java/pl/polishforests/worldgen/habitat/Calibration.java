package pl.polishforests.worldgen.habitat;

/**
 * Thresholds and initial values of the habitat classifier (docs/03-m2-biomy.md §2–§5, ecology report).
 * All classification thresholds are here, except for the mountain belt elevations: those are only in
 * {@link AltitudinalBelts} (a single source of the 1150 m threshold).
 *
 * <p>Units: heights and depths in model meters; distances marked "·k" are multiplied by the scale's
 * {@code local} (REALISTIC 1, GAMEPLAY 0.5); zone minimums are in blocks (E11) and do not scale;
 * slopes in degrees (in GAMEPLAY compared with the slope in blocks, §3.2, note for S4).
 */
public final class Calibration {
	private Calibration() {
	}

	// ------------------------------------------------------------------ general

	/** Narrowest zone belt that becomes a biome (3 quarts, i.e. biome cells of 4 blocks, Z4). */
	public static final double BIOME_BAND = 12.0;
	/** Wavelength of the zone boundary jitter noise (m·k), the middle of the 30–60 m range. */
	public static final double BORDER_WAVELENGTH = 45.0;
	/** Zone width jitter: ±20%. */
	public static final double WIDTH_JITTER = 0.20;
	/** Position jitter on the valley floor: ±0.05 u. */
	public static final double U_JITTER = 0.05;
	/** Wavelength of the patch noise (floating-leaved plants, reedbed in patches, gaps in riparian forest, backswamps) in m·k. */
	public static final double PATCH_WAVELENGTH = 35.0;
	/** Wavelength of the within-biome variant noise (lowland beech forest, heath openings) in m·k. */
	public static final double VARIANT_WAVELENGTH = 1_500.0;

	// ------------------------------------------------------------------ DGW (§3.3)

	/** Typical water table depth below the smoothed terrain: OUTWASH_PLAIN, COASTLAND, OLD_GLACIAL_PLAIN, MORAINE_PLATEAU, FOOTHILLS, BESKIDS. */
	public static final double G_OUTWASH_PLAIN = 2.5;
	public static final double G_COASTLAND = 1.5;
	public static final double G_OLD_GLACIAL_PLAIN = 3.0;
	public static final double G_MORAINE_PLATEAU = 4.0;
	public static final double G_FOOTHILLS = 5.0;
	public static final double G_BESKIDS = 8.0;
	/** Water table gradient away from water: sand, till, flysch. */
	public static final double I_SAND = 0.003;
	public static final double I_TILL = 0.01;
	public static final double I_FLYSCH = 0.04;
	/** The water level raises the water table by this much (m). */
	public static final double WATER_LEVEL_OFFSET = 0.3;
	/**
	 * The influence of watercourses and standing water on the water table is phased in with the terrain incision
	 * into a valley or hollow (rawSurface − H) from the first to the second threshold (m); always on the floor.
	 * Without this, DGW jumped at the edge of the river network query range (d = +∞ beyond it), where the terrain
	 * is no longer incised.
	 */
	public static final double INCISION_FROM = 0.5;
	public static final double INCISION_TO = 3.0;
	/** Penalty on the water-derived water table outside a valley (m): disables the L_w + i·r term while keeping continuity. */
	public static final double OUTSIDE_VALLEY_PENALTY = 50.0;
	/** Upper limit of DGW (m). */
	public static final double DGW_MAX = 12.0;
	/** Perched water on till: terrain concavity (m) and the corresponding DGW. */
	public static final double PERCHED_1 = -1.5;
	public static final double PERCHED_1_DGW = 1.0;
	public static final double PERCHED_2 = -3.0;
	public static final double PERCHED_2_DGW = 0.3;
	/** Shore on lake mud: DGW at most this. */
	public static final double LAKE_MUD_DGW = 0.2;

	// ------------------------------------------------------------------ moisture (§3.3)

	public static final double DGW_DRY = 4.0;
	public static final double DGW_FRESH = 2.0;
	public static final double DGW_MOIST = 0.8;
	public static final double DGW_BOGGY = 0.5;
	/** Dry: a dune at least this high (m) or a convexity above {@link #DRY_CONVEXITY}. */
	public static final double DRY_DUNE = 4.0;
	public static final double DRY_CONVEXITY = 2.0;

	// ------------------------------------------------------------------ fertility (§3.3): shares of B, BM, LM, L in %

	static final double[] FERTILITY_OUTWASH_PLAIN = {65, 31, 4, 0};
	static final double[] FERTILITY_MORAINE_PLATEAU = {0, 10, 30, 60};
	static final double[] FERTILITY_OLD_GLACIAL_PLAIN = {15, 25, 30, 30};
	static final double[] FERTILITY_COASTLAND = {70, 20, 10, 0};
	static final double[] FERTILITY_FOOTHILLS = {0, 10, 30, 60};
	static final double[] FERTILITY_BESKIDS = {0, 15, 30, 55};
	/**
	 * Step H (Z9): jitter of the cumulative fertility thresholds, at most this much of the richness quantile and at most
	 * half of the shares on both sides of the threshold (so a share of 0 stays 0, the thresholds stay ordered and the
	 * shares are kept on average). The richness comes from a noise with a 2 km·k wavelength, so without the jitter
	 * the boundaries of the zonal biomes were its isolines: parallel bands that look straight within a few hundred meters.
	 * 0.05 moves a boundary by about ±50 m·k.
	 */
	public static final double FERTILITY_JITTER = 0.05;
	/** Wavelength of the fertility threshold jitter (m·k; 75 m at gameplay scale, Z9: ≥ 64 m). */
	public static final double FERTILITY_JITTER_WAVELENGTH = 150;
	/**
	 * Valley floor by terrain: ground at most this many meters above the water level of the nearest channel (the
	 * model floor lies 1.2–2.2 m above the water level) and no farther from the channel than the floor half-width
	 * (at least {@link #FLOOR_MIN_K}·k). The model's {@code inFloor} flag comes from the dominant valley and is
	 * sometimes cut off by a straight line where the valley changes.
	 */
	public static final double FLOOR_H = 2.3;
	public static final double FLOOR_MIN_K = 80;
	/**
	 * Step H (open S4 problem 1): the zones and riparian forests of a watercourse only by real water, i.e. on ground at
	 * most this many meters above the (soft) water level of the channels. A channel stretch that the valley does not
	 * cut (dry, the water level lies 10 m and more below the ground, e.g. short headwater segments on massif domes)
	 * otherwise drew belts of willows and tall herbs along a line without water. The banks of real channels lie
	 * 1.2–2.3 m above the water (the model floor), dry uncut stretches mostly more than 10 m.
	 */
	public static final double BANK_H = 4.0;
	/**
	 * Ground lower than this many meters above the water level of the nearest channel lies by a different channel
	 * than the one whose floor it is on (a tributary descending in a rapid to the floor of a larger valley): the
	 * {@code inFloor} flag then decides, and h = H − water level − 1 is undefined.
	 */
	public static final double OTHER_CHANNEL_H = 1.15;
	/** The model valley floor lies at least this many meters above the water level of its own channel (water level for DGW). */
	public static final double FLOOR_ABOVE_WATER_LEVEL = 1.2;
	/** Share of outwash plain and coastland sands above which alluvium on valley floors gets the region's fertility instead of L. */
	public static final double ALLUVIUM_ON_SAND = 0.5;
	/**
	 * Step H, round 1 of the review: the {@link #ALLUVIUM_ON_SAND} threshold jitters by ±this much of the weight with a
	 * noise of {@link #FERTILITY_JITTER_WAVELENGTH} m·k. The type weights change slowly (the coastal belt over
	 * 2000 m·k), so the floor of every valley crossing the 0.5 isoline switched from floodplain forest to pine forest
	 * along a straight line across the whole floor (e.g. parallel to the coast in {@code coast_lagoon_3km}).
	 */
	public static final double ALLUVIUM_ON_SAND_JITTER = 0.15;

	// ------------------------------------------------------------------ zonal biomes (§2.1)

	public static final double H_DRY_PINE = 350;
	public static final double H_FRESH_PINE = 500;
	public static final double H_BOG_WOODLAND = 400;
	public static final double H_LOWLAND_BEECH = 350;
	public static final double H_ALDER_CARR = 500;
	public static final double H_ASH_ALDER_RIPARIAN = 600;
	public static final double H_WILLOW_RIPARIAN = 300;
	/** Upland fir forest at P ≥ 0.5 in 250–650 m (lower down, pine forest and mixed forest). */
	public static final double H_FIR_FOREST_FROM = 250;
	public static final double H_FIR_FOREST_TO = 650;
	public static final double P_FIR_FOREST = 0.5;
	/** Lowland beech forest: drainage at a slope above (°) or a convexity ≥ −{@link #BEECH_CONCAVITY}. */
	public static final double SLOPE_BEECH = 3.0;
	/**
	 * Step H: lowland beech forest is excluded only from real hollows, terrain at least this many meters below the
	 * smoothed terrain (rawSurface − sBar). With the S4 limit 0 the boundary followed the sign of a field that on flat
	 * ground is a few decimeters of bilinear texture of the coarse terrain grid (CoarseTerrainField) and of the
	 * smoothing around kettles (circular arcs). Perched water starts at −1.5 m ({@link #PERCHED_1}).
	 */
	public static final double BEECH_CONCAVITY = 0.75;
	/**
	 * Step H (round 1 of the review): the fresh/moist boundary of lowland beech forest (DGW = {@link #DGW_FRESH})
	 * jitters by ±this many meters of DGW with a noise of {@link #FERTILITY_JITTER_WAVELENGTH} m·k. On flat ground, e.g.
	 * the coastal hinterland at the terrain clamp of the shore, DGW follows the slowly changing type weights, so its
	 * isoline was a ruler-straight edge of the beech forest parallel to the shore.
	 */
	public static final double BEECH_DGW_JITTER = 0.3;
	/** The share of lowland beech forest within the beech range grows with O from this threshold to the full value. */
	public static final double O_BEECH_FROM = 0.40;
	public static final double O_BEECH_TO = 0.85;
	/**
	 * Highest share of beech forest among the sites that allow it. Step H: 0.85 → 0.5 together with
	 * {@link #BEECH_CONCAVITY}: flat ground now counts as drained, which doubled the lowland beech forest (REAL 3.7% → 7.2%
	 * of all columns, GAMEPLAY 4.9% → 6.9%); 0.5 brings it back to about the S4 shares.
	 */
	public static final double BEECH_MAX = 0.5;
	/** NATURAL mode: heath openings on dunes (part of the dry pine forest, ≤ 5% of the outwash plain), patches with a wavelength of 150 m·k. */
	public static final double HEATH_SHARE_NATURAL = 0.25;
	public static final double HEATH_OPENING_WAVELENGTH = 150.0;

	// ------------------------------------------------------------------ waterside zones (§4)

	/** Watercourse classes: lowland weight, width at 1:1 scale (m), gradient (‰). */
	public static final double CLASS_LOWLAND_WEIGHT = 0.5;
	public static final double CLASS_A_WR = 30;
	/** Order 3 (dominant valley) gives class A only with a channel at least this wide (m, 1:1). */
	public static final double CLASS_A_WR_ORDER3 = 15;
	/** Wide floor of a large valley (m·k): beyond the riparian forest of a small watercourse, the rest of the floor is elm-ash floodplain forest. */
	public static final double WIDE_FLOOR_K = 250;
	/** Wide floor of a large valley: a dominant valley of at least this order. */
	public static final int WIDE_FLOOR_ORDER = 2;
	public static final double CLASS_GRADIENT = 3.0;
	/** Zone minimums in blocks (E11). */
	public static final double MIN_REEDBED = 2;
	public static final double MIN_WILLOW_SCRUB = 3;
	public static final double MIN_ASH_ALDER = 6;
	public static final double MIN_ALDER_CARR = 10;
	public static final double MIN_HERB_FRINGE = 2;
	public static final double MIN_TALL_HERBS = 2;
	// Class A
	public static final double A_WILLOW_SCRUB_K = 8, A_WILLOW_SCRUB_W = 0.3;
	public static final double A_CONVEX_WILLOW_SCRUB_K = 15, A_CONVEX_WILLOW_SCRUB_W = 1.0;
	public static final double A_POINT_BAR_W = 0.5;
	/** Point bar in patches: patch noise (wavelength 35 m·k) above the threshold (about 60% of the convex bank). */
	public static final double A_POINT_BAR_PATCH = -0.2;
	public static final double A_HERB_FRINGE_K = 4, A_HERB_FRINGE_W = 0.05;
	public static final double A_D_WHITE_WILLOW_W = 1.7, A_D_WHITE_WILLOW_MIN = 30, A_D_WHITE_WILLOW_MAX = 300;
	public static final double A_D_POPLAR_W = 3.3, A_D_POPLAR_MIN = 80, A_D_POPLAR_MAX = 500;
	public static final double A_BACKSWAMP_U = 0.6;
	/**
	 * Backswamps in the lower parts of the floor: h = H − water level − 1 below the threshold (m). The model floor
	 * lies flat 1.2–2.2 m above the water level with noise of wavelength 90 m·k, so h of 0.2–1.2 m describes the
	 * higher and lower parts of the floor (as a stand-in for η, §4). Step K8c added a finer octave of 35 m·k on the
	 * lowland floors; the classifier takes it out of H (Waters.floorFine), so h still has the 90 m·k noise only. The plan used {@code convexity}, but it has
	 * short-wave components (outwash plain undulation) and gave stripes every few meters on the floor (Z9: a biome
	 * only from inputs with a wavelength ≥ 64 m).
	 */
	public static final double A_BACKSWAMP_H = 0.6;
	/** Backswamps in patches: noise quantile with a wavelength of {@link #BACKSWAMP_WAVELENGTH} m (without k) below the share (about 25% of the floor). */
	public static final double BACKSWAMP_SHARE = 0.5;
	public static final double BACKSWAMP_WAVELENGTH = 120;
	public static final double A_BACKSWAMP_D_K = 60, A_BACKSWAMP_D_W = 2.0;
	public static final double A_PEAT_HALF_WIDTH_K = 300, A_PEAT_GRADIENT = 0.5;
	/**
	 * Share of fen in the backswamps of wide floors (the rest is alder carr); patches with a wavelength of
	 * {@link #BACKSWAMP_WAVELENGTH} m without k, because they select the biome (Z9).
	 */
	public static final double A_PEAT_SHARE = 0.4;
	/** Gaps in the riparian forest (HERB_FRINGE zone): patch noise below the threshold. */
	public static final double A_GAPS = -0.5;
	// Class B
	public static final double B_HERBS_K = 1.5, B_HERBS_W = 0.5;
	public static final double B_WILLOWS_WR = 15, B_WILLOWS_K = 20;
	public static final double B_ASH_ALDER_MAX_K = 80, B_ASH_ALDER_MIN_K = 15, B_ASH_ALDER_W = 4;
	public static final double B_ASH_ALDER_OUTWASH_PLAIN_MIN_K = 5, B_ASH_ALDER_OUTWASH_PLAIN_MAX_K = 25;
	public static final double B_ALDER_CARR_HALF_WIDTH_K = 60, B_ALDER_CARR_D_K = 20, B_ALDER_CARR_D_W = 4;
	public static final double B_ALDER_CARR_H = 0.45, B_ALDER_CARR_U = 0.5;
	public static final double B_WILLOW_CARR_K = 15;
	public static final double B_TREE_ROW_FROM_K = 3, B_TREE_ROW_TO_K = 10;
	// Class C
	public static final double C_GRAVEL_BAR_WR = 6, C_GRAVEL_BAR_H_FROM = 300, C_GRAVEL_BAR_H_TO = 1_000;
	public static final double C_WILLOW_SCRUB_WR = 4, C_WILLOW_SCRUB_H = 900;
	public static final double C_GRAY_ALDER_MAX_K = 60, C_GRAY_ALDER_MIN_K = 10, C_GRAY_ALDER_W = 3;
	public static final double C_GRAY_ALDER_H = 1_000, C_GRAY_ALDER_H_N = 900;
	public static final double C_WHOLE_FLOOR_K = 30, C_NARROW_FLOOR_K = 10;
	public static final double C_HERBS_W = 0.5, C_HERBS_MIN = 1;
	public static final double C_CARICI_H = 700, C_CARICI_P = 0.4;
	public static final double C_SPRING_FEN_H_FROM = 400, C_SPRING_FEN_H_TO = 1_100, C_SPRING_FEN_DGW = 0.3;
	/**
	 * Spring areas: the HEADWATERS landform covers the whole floor of the headwater section, so a spring area is
	 * a belt from the channel with a width of 0–{@link #SPRING_AREA_K}·k varying with noise (radius 10–40k), on
	 * average {@link #SPRING_AREA_SHARE} of the full belt.
	 */
	public static final double SPRING_AREA_K = 40;
	public static final double SPRING_AREA_SHARE = 0.3;
	/**
	 * Alder carr on the wide floor of a small river: patches on this fraction of the condition (patch noise quantile
	 * with a wavelength of {@link #ALDER_CARR_PATCH_WAVELENGTH} m without the k multiplier, so that patches are at
	 * least about 10 blocks wide in GAMEPLAY too).
	 */
	public static final double B_ALDER_CARR_SHARE = 0.45;
	public static final double ALDER_CARR_PATCH_WAVELENGTH = 120;
	/** Seep at the foot of a valley side (ash-alder riparian forest): DGW at most this. */
	public static final double SEEP_DGW = 0.5;
	/**
	 * Seep only low above the watercourse: at most this many meters above the (soft) water level of the nearby channels
	 * (the floor lies 1.2–2.3 m above it), in terrain incised into the valley (rawSurface − H ≥ {@link #INCISION_FROM}).
	 * The same limit holds for the bank belt of ash-alder riparian forest on a narrow floor (E11). Step H: equal to
	 * {@link #BANK_H} (S4: 5 m), because the zones of a watercourse exist only up to BANK_H above the water
	 * ({@code Column.byWater}); a higher limit would be dead.
	 */
	public static final double SEEP_HL = BANK_H;
	/**
	 * Spring areas in patches with a wavelength of this many meters without the k multiplier (patches select the
	 * biome, Z9; with a wavelength of 35 m·k, GAMEPLAY produced islets and shreds of riparian forest narrower than
	 * 6 blocks).
	 */
	public static final double SPRING_AREA_WAVELENGTH = 120;
	// Lakes, kettle ponds, oxbow lakes (§4.4)
	public static final double LAKE_DEPTH = 5, LAKE_DEPTH_OXBOW = 3;
	public static final double LAKE_SUBMERGED = 1.5;
	public static final double LAKE_FLOATING_LEAVED_FROM = 0.8, LAKE_FLOATING_LEAVED_TO = 3;
	/** Floating-leaved plants: share of the water surface in oxbow lakes and in bays of large lakes (patch noise quantile). */
	public static final double LAKE_FLOATING_LEAVED_OXBOW = 0.75, LAKE_FLOATING_LEAVED_LAKE = 0.3;
	public static final double LAKE_REEDBED_Z = 1.5;
	/** Reedbed as a biome in water: a water body with a radius (half-width) of at least this much (m·k). */
	public static final double LAKE_REEDBED_BIOME_RADIUS_K = 50;
	public static final double LAKE_SHORE_REEDBED_K = 10, LAKE_SHORE_REEDBED_H = 0.3;
	public static final double LAKE_WILLOW_CARR_K = 30, LAKE_WILLOW_CARR_H = 0.8;
	public static final double LAKE_ALDER_CARR_K = 150, LAKE_ALDER_CARR_KETTLE_K = 40, LAKE_ALDER_CARR_H = 1.0, LAKE_ALDER_CARR_SLOPE = 3.0;
	public static final double LAKE_OXBOW_REEDBED_K = 15, LAKE_OXBOW_WILLOW_CARR_K = 35, LAKE_OXBOW_ALDER_CARR_U = 0.5;
	/**
	 * Oxbow ring only around oxbow lakes with a half-width of at least this much (m): the model also reports a ring
	 * at narrow watercourses, where no oxbow water forms (the channel bank displaces it), which produced a row of
	 * identical willow carr dashes along a stream.
	 */
	public static final double LAKE_OXBOW_MIN = 8;
	/** Lakes on the outwash plain: a lake hash below the threshold gives dystrophic, below the second one oligotrophic. */
	public static final double LAKE_DYSTROPHIC = 0.3, LAKE_OLIGOTROPHIC = 0.55;
	public static final double LAKE_FLOATING_MAT_K = 20;
	public static final double LAKE_SHORE_ALDER_K = 5;
	/** Reedbed of a lobelia lake in patches on 20% of the shore. */
	public static final double LAKE_LOBELIA_REEDBED = 0.8;
	public static final double LAKE_DYSTROPHIC_PEAT_K = 15, LAKE_DYSTROPHIC_BOG_WOODLAND = 45;
	/** Kettle bog: radius of the boundary between raised bog and bog woodland (m·k). */
	public static final double KETTLE_BOG_RADIUS_K = 75;
	/** Ring of bog woodland around ombrotrophic peat (deviation S2: kettle belt of 45 m). */
	public static final double KETTLE_RING = 45;
	// Lagoon (§4.4)
	public static final double LAGOON_Z = 1.5;
	public static final double LAGOON_PEAT_H = 0.4, LAGOON_ALDER_CARR_H = 1.0;

	// ------------------------------------------------------------------ coast (§5.2, lengths · k)

	public static final double BEACH_B = 60, DUNES_D = 220;
	public static final double STRANDLINE_B = 0.35;
	public static final double EMBRYO_DUNE_K = 20;
	public static final double GRAY_DUNE_K = 170;
	/**
	 * Step H (round 1 of the review): the landward end of the gray dune belt (B + D + GRAY_DUNE_K·k) jitters by
	 * ±this share of GRAY_DUNE_K (at most ±100 m·k, typically a few tens of meters) with a noise of
	 * {@link #GRAY_DUNE_JITTER_WAVELENGTH} m·k, so neither the gray dunes nor the dunes running across a valley floor
	 * ({@code Column.duneOverFloor}) end on a line parallel to the shore (±0.3 at 150 m·k still looked straight
	 * across the 800 m wide floors of REAL rivers).
	 */
	public static final double GRAY_DUNE_JITTER = 0.6;
	public static final double GRAY_DUNE_JITTER_WAVELENGTH = 300;
	public static final double WINDSWEPT_PINE_K = 420;
	public static final double COASTAL_PINE_K = 2_000, COASTAL_PINE_H = 40;
	/** Jitter of the crowberry pine forest boundary: ±15% (variant noise). */
	public static final double COASTAL_PINE_JITTER = 0.15;
	/**
	 * Dune shore: the {@code lowShore} field (1 − the share of a high shore with a cliff from the coast shape, D5 in
	 * step K5b) at least this much. There: beach, foredune, gray dunes, spit and lagoon; below it: a cliff. Before K5b
	 * the field was 1 − smoothstep(6, 20, hl) of the compressed relief, and a dune shore needed only 0.25 (the dune
	 * hump hid below the belt terrain at low &lt; 0.5); now the terrain of the shore is the blend of a low shore and a
	 * cliff by the same share, so the midpoint separates them (docs/m2/poprawka-geometrii.md, K5b).
	 */
	public static final double LOW_SHORE = 0.5;
	/**
	 * Step H (D5): on a dune shore the beach, white and gray dunes run across the valley floors of the rivers that reach
	 * the sea, up to the jittered end of the gray dune belt ({@link #GRAY_DUNE_JITTER}), and only the river mouth
	 * itself, at most max(MOUTH_K·k, MOUTH_W·W) from the channel (±20%, noise of 150 m·k), keeps the waterside zones.
	 * The low hinterland of D5 lies at the level of the river floors, so before this rule the floodplain forests reached
	 * the beach on 15% of the dune shores (floors up to 800 m wide). Behind the dune belt the floors keep the waterside
	 * zones: the floor surface there is the terrain clamp of the shore (2.000 m), so the height thresholds of the
	 * lagoon hinterland would cut it along straight lines.
	 */
	public static final double MOUTH_K = 30, MOUTH_W = 1.5;
	public static final double CLIFF_H = 8, CLIFF_TOP_K = 20, CLIFF_WINDSWEPT_FOREST_K = 150;
	/** Shingle beach below a cliff: terrain before incision higher than this (m). */
	public static final double SHINGLE_BEACH_RAW = 8;

	// ------------------------------------------------------------------ PRESENT_DAY mode (§2.2, column D, §4.5): forest mask (S8)

	/** Noise F of the forest mask: long and short octave (m·k), weight of the short one, edge noise (m·k) and its weight. */
	public static final double F_WAVELENGTH = 6_000, F_FINE_WAVELENGTH = 2_000, F_FINE = 0.5, F_EDGE_WAVELENGTH = 300, F_EDGE = 0.08;
	/**
	 * Woodlot octave of F (round 1 of the S8 review, new salt {@code habitat.forest_cover.woodlots}): wavelength (m·k) and
	 * weight. Small woods between the fields, and enough variation of F within a few hundred meters that the forest edges
	 * follow F and not the site boundaries.
	 */
	public static final double F_WOODLOT_WAVELENGTH = 600, F_WOODLOT = 0.35;
	/**
	 * Blends of P_forest across the moisture thresholds (round 1 of the S8 review): half-width of the DGW band (m) and the
	 * jitter of DGW (m) by the context noise (new salt {@code habitat.forest_cover.context}).
	 */
	public static final double P_DGW_BAND = 0.7, P_DGW_JITTER = 0.4;
	/** Blend of P_forest from the alder carr to the moist fertile site over the jittered DGW (m), round 1 of the S8 review. */
	public static final double P_CARR_DGW_FROM = 0.3, P_CARR_DGW_TO = 1.3;
	/** P_forest of the lowland site types (BiomeSharesTest, step S8). */
	public static final double P_DRY_CONIFEROUS = 0.8, P_FRESH_CONIFEROUS = 0.76, P_MOIST_CONIFEROUS = 0.7,
			P_BOGGY_CONIFEROUS = 0.75, P_FRESH_MIXED_CONIFEROUS = 0.3, P_FRESH_MIXED_BROADLEAVED = 0.25,
			P_MOIST_MIXED_BROADLEAVED = 0.5, P_FRESH_BROADLEAVED = 0.1, P_MOIST_BROADLEAVED = 0.35, P_OTHER = 0.5;
	/** P_forest of the floodplain forests, alder carr, coast and mountain forests. */
	public static final double P_WILLOW_POPLAR = 0.15, P_ELM_ASH = 0.06, P_ASH_ALDER = 0.09, P_ALDER_CARR = 0.1,
			P_COASTAL_PINE = 0.9, P_MONTANE_SPRUCE = 0.9, P_MONTANE_BEECH = 0.8, P_UPLAND_FIR = 0.6, P_MOUNTAIN_FLOOR = 0.15,
			P_GRAY_ALDER = 0.6;
	/** Factor of P_forest of the poor sites (B, BM) outside the outwash plain (by its type weight). */
	public static final double P_SAND_OUTSIDE_OUTWASH = 0.75;
	/** Foothills: P_forest of a gentle slope (the steep ones by the context). */
	public static final double P_FOOTHILLS = 0.15;
	/**
	 * Context: P_forest moves towards P_STEEP by the steepness, a smoothstep of the slope (° in blocks) from P_SLOPE_FROM
	 * to P_SLOPE_TO; at least P_MORAINE_DUNE on end moraines and inland dunes and P_VALLEY_SIDE on valley sides.
	 */
	public static final double P_SLOPE_FROM_GAMEPLAY = 12, P_SLOPE_TO_GAMEPLAY = 35;
	public static final double P_STEEP = 0.8, P_SLOPE_FROM = 8, P_SLOPE_TO = 25, P_MORAINE_DUNE = 0.5, P_VALLEY_SIDE = 0.3,
			P_VALLEY_SIDE_GAMEPLAY = 0.1;
	/**
	 * Valley side context (round 1 of the S8 review): it grows with the incision below the pre-valley terrain from FROM to
	 * TO m, the incision jittered by ±JITTER m; it acts on about STRETCHES of the valley sides, chosen by the context noise
	 * of BREAK_WAVELENGTH m·k with a blend of ±BREAK_BLEND in its quantile.
	 */
	public static final double P_VALLEY_INCISION_FROM = 1, P_VALLEY_INCISION_TO = 4, P_VALLEY_INCISION_JITTER = 1;
	public static final double P_VALLEY_SIDE_BREAK_WAVELENGTH = 700, P_VALLEY_SIDE_STRETCHES = 0.6, P_VALLEY_SIDE_BREAK_BLEND = 0.1;
	/**
	 * Gray alder forest (§4.5): the strip by the stream that always stays (clamp(W·2, 5k, 20k) with the zone jitter), and
	 * meadows beyond it on floors wider than D_STREAM_MEADOW_FLOOR_K·k below D_STREAM_MEADOW_H m.
	 */
	public static final double D_GRAY_ALDER_STRIP_W = 2, D_GRAY_ALDER_STRIP_MIN_K = 5, D_GRAY_ALDER_STRIP_MAX_K = 20;
	public static final double D_STREAM_MEADOW_FLOOR_K = 80, D_STREAM_MEADOW_H = 900;

	// ------------------------------------------------------------------ PRESENT_DAY mode (§2.2, column D): non-forest biome

	/** Variant quantile below the threshold: riparian forest → wet meadow (otherwise arable land), alder carr → wet meadow (otherwise fen). */
	public static final double D_RIPARIAN_MEADOW = 0.5;
	public static final double D_ALDER_CARR_MEADOW = 0.7;
	/** Fresh and moist pine forests: arable land (fresh) or wet meadow (moist) below the threshold, otherwise heath or arable land. */
	public static final double D_PINE_ARABLE = 0.6;
	/**
	 * Moist broadleaved and mixed broadleaved sites (Lw, LMw, LMb, Ol): wet meadow below the threshold, otherwise drained
	 * arable land (round 1 of the S8 review: with all of them meadows, the meadows took about a fifth of the land).
	 */
	public static final double D_MOIST_MEADOW = 0.5;
	/**
	 * Other sites: arable land where the parcel quantile lies below the threshold, otherwise hay meadow; on flat ground
	 * (slope below {@link #D_ARABLE_SLOPE}°, in GAMEPLAY {@link #D_ARABLE_SLOPE_GAMEPLAY}° in blocks, as the slope context)
	 * {@link #D_ARABLE}, on slopes {@link #D_ARABLE_STEEP} (round 1 of the S8 review: before it all slopes were meadows).
	 */
	public static final double D_ARABLE = 0.94, D_ARABLE_STEEP = 0.4;
	public static final double D_ARABLE_SLOPE = 5, D_ARABLE_SLOPE_GAMEPLAY = 8;
	/**
	 * Round 1 of the S8 review: wavelength of the parcel noise of the arable land / hay meadow choice (m·k), and the jitter
	 * of the slope threshold (°) with its wavelength (m·k).
	 */
	public static final double D_PARCEL_WAVELENGTH = 400, D_ARABLE_SLOPE_JITTER = 2, D_ARABLE_SLOPE_JITTER_WAVELENGTH = 200;

	// ------------------------------------------------------------------ ranges (§9)

	public static final double BEECH_O = 0.40, BEECH_P = 0.40;
	public static final double FIR_P = 0.50, FIR_H = 1_250;
	public static final double SPRUCE_O = 0.30, SPRUCE_P = 0.50;
	public static final double HORNBEAM_H = 600, HORNBEAM_H_S = 700;
	public static final double GRAY_ALDER_P = 0.50, GRAY_ALDER_O = 0.30;
	public static final double SESSILE_OAK_O = 0.35, SESSILE_OAK_P = 0.40, SESSILE_OAK_H = 600;
	public static final double LARCH_P_FROM = 0.50, LARCH_P_TO = 0.80, LARCH_H_FROM = 250, LARCH_H_TO = 650;
	public static final double IVY_O = 0.45, IVY_P = 0.50;
	public static final double YEW_O = 0.6, YEW_P = 0.6;

	// ------------------------------------------------------------------ slope at gameplay scale

	/**
	 * Derivative of the gameplay-scale vertical mapping (blocks per meter) at elevation {@code meters}:
	 * blocks = 1.89 · m^0.742 ({@code VerticalScale.GAMEPLAY}; consistency is checked by {@code HabitatClassifierTest}).
	 * The slope in blocks is tan(slope) · this factor (§3.2, note for S4).
	 */
	public static double gameplayBlocksPerMeter(double meters) {
		double m = Math.max(1.0, meters);
		return 1.89 * 0.742 * Math.pow(m, 0.742 - 1.0);
	}
}
