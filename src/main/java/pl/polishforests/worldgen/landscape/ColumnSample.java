package pl.polishforests.worldgen.landscape;

import java.util.Set;

/**
 * Result of sampling the landscape model in one column.
 *
 * <p>The first six fields are the M1 terrain (the golden test hashes only them). The records {@link Terrain},
 * {@link Waters} and {@link Region} carry quantities for habitats (M2, docs/03-m2-biomy.md §3.1). They hold only
 * primitive types and enums, no arrays, so {@code equals} compares them by value. The value
 * {@link Double#NaN} means "not applicable" (e.g. {@code u} outside the valley floor), and
 * {@link Double#POSITIVE_INFINITY} in distances means "out of range".
 *
 * @param surface     ground surface height in meters a.s.l. (also the bottom under water)
 * @param waterLevel  water surface level in whole meters a.s.l.; {@link Integer#MIN_VALUE} when there is no water
 * @param waterKind   kind of water
 * @param type        dominant landscape type
 * @param substrate   surface deposit
 * @param coverDepth  thickness of Quaternary deposits or regolith above solid bedrock, in meters
 * @param terrain     relief and landforms
 * @param waters      watercourses, valley floors and standing water nearby
 * @param region      regional fields
 */
public record ColumnSample(double surface, int waterLevel, WaterKind waterKind, LandscapeType type,
		Substrate substrate, double coverDepth, Terrain terrain, Waters waters, Region region) {
	public static final int NO_WATER = Integer.MIN_VALUE;

	public boolean hasWater() {
		return waterLevel != NO_WATER && waterLevel > surfaceMeters();
	}

	/** Height of the top face of the highest ground block, in whole meters. */
	public int surfaceMeters() {
		return (int) Math.floor(surface);
	}

	/**
	 * Relief in a column.
	 *
	 * @param rawSurface       terrain before cutting valleys and lakes, after shaping the coast (m a.s.l.)
	 * @param coastD           distance from the sea shoreline (m), positive on land
	 * @param wOutwashPlain    weight of the OUTWASH_PLAIN type from macroregion blending; the five region type weights sum to 1
	 * @param wMorainePlateau  weight of the MORAINE_PLATEAU type
	 * @param wOldGlacialPlain weight of the OLD_GLACIAL_PLAIN type
	 * @param wFoothills       weight of the FOOTHILLS type
	 * @param wBeskids         weight of the BESKIDS type
	 * @param wCoastland       share of the coastal belt 0–1: 1 where the sample gets the COASTLAND type, decreasing
	 *                         to 0 at the edge of the coastal belt (B + D + 2000k); region types are blended with weight
	 *                         1 − wCoastland (macroregions have no COASTLAND type, so this field does not come from Blend)
	 * @param landformBits     {@link Landform#bit()} bits of the landforms already recognised in {@code sample}: INLAND_DUNES,
	 *                         END_MORAINE, RIDGE, MOUNTAIN_VALLEY, BEACH, COASTAL_DUNES, CLIFF, HEADWATERS
	 * @param convexity        local convexity (m): short-wave relief components (undulation and dunes of the outwash plain,
	 *                         hummocks and ridges of the plateau, fine relief of the plain, gullies and roughness of the flysch)
	 *                         weighted by the macroregion weights; positive on knolls, negative in hollows
	 * @param duneHeight       dune height above the outwash plain (m), 0 outside dune fields and without an outwash plain cell in the blend
	 * @param ridgeProfile     profile of the longitudinal flysch valleys: 0 on the valley axis, about 1 on the ridge; NaN without flysch
	 * @param massif           strength of a higher Beskid massif 0–1: the larger of the broad massif field and the strength G
	 *                         of a large massif (Babia Gora type, M2-8, {@code LandscapeModel.greatMassifStrength}); 0 outside
	 *                         the Beskids
	 * @param summit           highest valley-free terrain ({@code landElevation}) within 3 km·mspace (m a.s.l.,
	 *                         {@code PeakField} grid; a large massif in the altitudinal belts, E12); computed only in the Beskids
	 *                         (type weight > 0) from height {@code AltitudinalBelts.SUMMIT_FROM}, 0 lower down and outside the Beskids
	 * @param cliffHeight      height of the cliff edge (m) in the CLIFF landform belt, 0 outside it
	 * @param lowShore         low sea shore 0–1 (D5, step K5b: 1 − the share of a high shore with a cliff from the coast
	 *                         shape, a moraine plateau reaching the sea on part of the coast): 1 on a shore with a beach and
	 *                         dunes, 0 on a high shore with a cliff; 0 outside the belt 25 km·meso from the sea. The CLIFF
	 *                         landform at a high foredune does not mean a cliff when the shore is low
	 * @param bareSandWidth    boundary of the bare sand of the beach and white dune (distance from the shore, m), as in the
	 *                         substrate of the coastal belt (BEACH_SAND closer, SAND further); NaN outside the coastal belt
	 * @param sandiness        sandiness of the deposit 0–1 (quantile of a noise with a 2 km·k wavelength, so uniformly distributed);
	 *                         NaN in the sea and lagoon
	 * @param sBar             smoothed terrain without valleys and lakes (m a.s.l.): mean of 3 × 3 nodes of a 32 m·k grid
	 *                         of {@code landElevation} (window of about 96 m·k), interpolated bilinearly
	 * @param slope            slope of the valley-free terrain from this grid (°), from central differences of the nodes, in model
	 *                         space: at gameplay scale (horizontal distances compressed, heights in meters similar)
	 *                         much larger than the slope in blocks; compare thresholds in degrees with
	 *                         tan(slope) · d(blocks)/d(meters) from {@code VerticalScale} (docs/03-m2-biomy.md, state after S3)
	 * @param aspect           aspect (°): downslope direction from north (−Z) clockwise
	 *                         (90 east +X, 180 south +Z, 270 west −X); NaN on flat terrain
	 * @param beachWidth       width of the sandy beach (m from the shoreline) at this place of the coast (step K8b2: it varies
	 *                         along a low shore, 60 m·k on a cliff); NaN outside the belt 25 km·meso from the sea
	 */
	public record Terrain(double rawSurface, double coastD, double wOutwashPlain, double wMorainePlateau, double wOldGlacialPlain,
			double wFoothills, double wBeskids, double wCoastland, int landformBits, double convexity, double duneHeight, double ridgeProfile,
			double massif, double summit, double cliffHeight, double lowShore, double bareSandWidth, double sandiness, double sBar,
			double slope, double aspect, double beachWidth) {
		/** Whether {@code sample} recognised the landform (only the landforms listed for {@code landformBits}). */
		public boolean has(Landform landform) {
			return (landformBits & landform.bit()) != 0;
		}

		/** Adds the landforms recognised in {@code sample} to the set. */
		public void addLandforms(Set<Landform> into) {
			for (Landform f : Landform.FROM_SAMPLE) {
				if (has(f)) {
					into.add(f);
				}
			}
		}
	}

	/**
	 * Waters near the column. The watercourse fields come from {@code RiverNetwork.query}: distance, width
	 * and water level refer to the nearest channel, while valley floor, order and gradient refer to the watercourse whose valley dominates:
	 * the valley whose floor the column lies deepest in (F1). Where floors overlap, {@code u} is the minimum over the
	 * floors, and floor half-width and gradient are a soft maximum by the depth in the floor, so these fields do not
	 * jump on straight bisectors between valleys; only the order stays discrete. Exceptions: the floor of a valley
	 * starts where its head fade reaches 0.5 ({@code inFloor} and {@code u} jump there, as in M1; a dry valley head
	 * keeps its own order and gradient, see {@code RiverNetwork.f1Key}), and off the floors the dominance (order,
	 * gradient) changes on straight lines, because the key is linear in the distance from the floor edge, so new
	 * habitat rules (M5, N4) should not rely on these fields off the floor without smoothing.
	 *
	 * @param streamOrder         order of the watercourse whose valley dominates (0 = no watercourse in range)
	 * @param headwaters          headwater zone of that watercourse
	 * @param channelDist         d: distance (m) from the bank of the nearest channel, ≤ 0 in the channel, +∞ out of range;
	 *                            continuous, measured in the valley frame (u along, v across) in which the channel
	 *                            is drawn, so in a strongly curved valley it departs from the Euclidean distance
	 *                            (Deviation S2 in docs/03-m2-biomy.md). d ≤ 0 does not mean water: near the source the channel
	 *                            is narrower than W, and in a dry valley head there is none; the channel zone is chosen
	 *                            with {@code waterKind() == RIVER}
	 * @param channelWidth        W: width of that channel (m); NaN without a watercourse
	 * @param channelLevel        water level of that channel (m a.s.l., not rounded to a block); NaN without a watercourse
	 * @param inFloor             the column is on the valley floor
	 * @param u                   position on the valley floor: 0 at the channel, 1 at the edge of the floor; NaN off the floor
	 * @param floorHalfWidth      half-width of the valley floor (m); NaN without a watercourse
	 * @param channelGradient     watercourse gradient in ‰, converted to 1:1 scale; NaN without a watercourse
	 * @param convexBank          the column is on the inner (convex) side of a meander bend of the nearest channel;
	 *                            computed up to d ≤ max(W, 15 m·k), with pronounced meanders (reach sinuosity
	 *                            from about 1.03) or a wide channel (W / chan ≥ 6 m); narrow streams have false
	 * @param s                   distance (m) from the shore of the nearest standing water: positive on land, negative
	 *                            in the water or the peat of a kettle; +∞ outside the belt: sink lake 150 m·k,
	 *                            tunnel valley lake max(150 m·k, 70 m), kettle pond 45 m,
	 *                            oxbow lake 40 m·k
	 * @param shoreLevel          water level of that water (m a.s.l.); {@link ColumnSample#NO_WATER} without standing water
	 * @param standingWaterKind   kind of that water
	 * @param ombrotrophicPeat    ombrotrophic peat kettle (basin edge further than 300 m·k from a channel)
	 * @param lakeId              hash identifying the water body (constant across the whole body), 0 without standing water
	 * @param standingWaterRadius radius of a kettle pond or sink lake, half-width of a tunnel valley lake
	 *                            or oxbow lake at this place (m); NaN without standing water
	 * @param floorChannelDist    distance (m) from the bank of the channel of the dominant valley, measured like
	 *                            {@code channelDist}: the nearest channel of the same order as the dominant watercourse
	 *                            and at least half as wide (the same watercourse also across a node). On the floor of a
	 *                            large river the zones of the riparian forest follow this channel, and a smaller, closer
	 *                            watercourse keeps only its own belt (F2, docs/m2/poprawka-geometrii.md). +∞ without a
	 *                            watercourse
	 * @param floorChannelWidth   width of that channel (m); NaN without a watercourse
	 * @param floorChannelLevel   water level of that channel (m a.s.l., not rounded); NaN without a watercourse
	 * @param floorChannelGradient gradient of that channel's own segment in ‰, converted to 1:1 scale (the class of
	 *                            the channel in F2; {@code channelGradient} is the soft maximum over the valleys and
	 *                            near a tributary mouth mixes in the tributary); NaN without a watercourse
	 * @param softChannelLevel    water level of the channels near the column blended by their distance (step H of the
	 *                            terrain fix, G3): equal to {@code channelLevel} next to a single channel, and continuous
	 *                            across the bisector between two channels, where {@code channelLevel} steps from one
	 *                            channel to the other. The habitat classifier measures the height above the watercourse
	 *                            from it, so the floor and its zones do not end on straight bisectors at confluences
	 *                            (RiverNetwork.SOFT_LEVEL_*); NaN without a watercourse
	 */
	public record Waters(int streamOrder, boolean headwaters, double channelDist, double channelWidth, double channelLevel,
			boolean inFloor, double u, double floorHalfWidth, double channelGradient, boolean convexBank, double s,
			int shoreLevel, StandingWaterKind standingWaterKind, boolean ombrotrophicPeat, long lakeId,
			double standingWaterRadius, double floorChannelDist, double floorChannelWidth, double floorChannelLevel,
			double floorChannelGradient, double softChannelLevel) {
		/** No watercourses and no standing water (sea and places out of range). */
		public static final Waters NONE = new Waters(0, false, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN, false,
				Double.NaN, Double.NaN, Double.NaN, false, Double.POSITIVE_INFINITY, NO_WATER, StandingWaterKind.NONE,
				false, 0L, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
	}

	/** Kind of the standing water nearest to the column. */
	public enum StandingWaterKind {
		NONE,
		/** Lake in a glacial tunnel valley. */
		TUNNEL_VALLEY_LAKE,
		/** Lake in an endorheic depression of the river network. */
		SINK_LAKE,
		/** Water-filled kettle pond. */
		KETTLE_POND,
		/** Peat-filled kettle (without water). */
		KETTLE_BOG,
		/** Oxbow lake on the valley floor. */
		OXBOW_LAKE
	}

	/**
	 * Regional fields from the {@code RegionalField} grid (nodes every 16 km·zs, bilinear interpolation), also at sea.
	 *
	 * @param oceanicity        O: climate oceanicity 0–1 (raised by the west and by proximity to the sea)
	 * @param mountainInfluence P: mountain influence 0–1 (1 at the mountain range axis, P ≥ 0.5 up to about 150–250 km·zs from it)
	 */
	public record Region(double oceanicity, double mountainInfluence) {
	}
}
