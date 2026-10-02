package pl.polishforests.worldgen.habitat;

/**
 * Variant within a biome (association, subassociation, form): a habitat field read by the tree stand
 * and ground layer palettes (M3, M4) and by fauna. At most 32 entries; the order is fixed, new ones go at the end only.
 */
public enum Association {
	TYPICAL,
	/** White willow riparian forest, Salicetum albae. */
	SALICETUM_ALBAE,
	/** White poplar riparian forest, Populetum albae. */
	POPULETUM_ALBAE,
	/** Submontane ash riparian forest, Carici remotae-Fraxinetum. */
	CARICI_REMOTAE_FRAXINETUM,
	/** Spring-fed riparian forest (cardaminetosum amarae). */
	SPRING_FED,
	/** Spring fen (swamp alder wood, Caltho laetae-Alnetum). */
	SPRING_FEN,
	/** Sycamore ravine forest on steep rubble (Lunario-Aceretum). */
	SYCAMORE_RAVINE_FOREST,
	/** Tall-herb communities in gullies of the upper montane belt. */
	GULLY_TALL_HERBS,
	/** Lower montane fir-spruce forest, Abieti-Piceetum. */
	ABIETI_PICEETUM,
	/** Acidophilous form (beech forests on poor sites). */
	ACIDOPHILOUS,
	/** Stunted crowberry pine forest (wind belt). */
	STUNTED,
	/** Shingle beach below a cliff. */
	SHINGLE,
	/** Windswept forest behind the cliff edge. */
	WINDSWEPT,
	/** Slope oak-hornbeam forest on a valley side. */
	SLOPE,
	/** Willow carr and alder carr by an oxbow lake. */
	OXBOW_LAKE,
	/** Pine forest on a spit or by a lagoon. */
	SPIT,
	/** Lobelia lake (oligotrophic, outwash plain). */
	LOBELIA_LAKE,
	/** Backswamp on the floor of a large valley. */
	BACKSWAMP;

	private static final Association[] VALUES = values();

	public static Association of(int ordinal) {
		return VALUES[ordinal];
	}
}
