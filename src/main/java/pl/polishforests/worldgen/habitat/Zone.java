package pl.polishforests.worldgen.habitat;

import java.util.Locale;

/**
 * Zones narrower than about 12 m (Z4): they are not biomes but a habitat field at block resolution. They paint
 * soil and microrelief in {@code fill()} and drive vegetation (docs/03-m2-biomy.md §4–§5). A zone is
 * independent of the biome, e.g. HERB_FRINGE inside a willow-poplar floodplain forest. At most 32 entries
 * (5 bits of the code); the order is fixed, new ones go at the end only.
 */
public enum Zone {
	NONE,
	/** River or stream channel (water). */
	CHANNEL,
	/** Pondweeds in water 1.5–5 m deep. */
	SUBMERGED_PLANTS,
	/** Water lilies and yellow water lilies in water 0.8–3 m deep. */
	FLOATING_LEAVED_PLANTS,
	/** Reedbed in water up to 1.5 m deep, in a belt narrower than the biome. */
	REEDBED,
	/** Sedge and reed canary grass reedbed on the shore (h ≤ 0.3). */
	SHORE_REEDBED,
	/** Point bar on the convex bank of a large river. */
	POINT_BAR,
	/** Riverside willow scrub. */
	WILLOW_SCRUB,
	/** Herb fringe (tall herbs) behind the willow scrub and in gaps of the riparian forest. */
	HERB_FRINGE,
	/** Tall herbs on the bank of a small river. */
	TALL_HERBS,
	/** White and crack willow by a wider small river on sand. */
	RIVERSIDE_WILLOWS,
	/** Willow carr (gray willow scrub) at the edge of an alder carr. */
	WILLOW_CARR,
	/** Gravel bar of a mountain stream. */
	GRAVEL_BAR,
	/** Streamside tall herbs high in the mountains and in narrow valleys. */
	MONTANE_TALL_HERBS,
	/** Spring area. */
	SPRING_AREA,
	/** Floating mat of a dystrophic lake. */
	FLOATING_MAT,
	/** Belt of alder or birch by a lobelia lake. */
	SHORE_ALDERS,
	/** Strandline on the dry beach. */
	STRANDLINE,
	/** Embryo dune. */
	EMBRYO_DUNE,
	/** Active cliff face (bare till). */
	CLIFF_FACE,
	/** Cliff top: scrub without trees. */
	CLIFF_TOP,
	/** The last meters below the timberline: stunted spruces. */
	TIMBERLINE,
	/** PRESENT_DAY mode: a row of alders along a class B watercourse. */
	TREE_ROW;

	private static final Zone[] VALUES = values();

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Zone of(int ordinal) {
		return VALUES[ordinal];
	}
}
