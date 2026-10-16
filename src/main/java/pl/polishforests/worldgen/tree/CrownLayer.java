package pl.polishforests.worldgen.tree;

/**
 * Layer of a tree in the stand (docs/04-pionowy-wycinek.md §1.1, §6 N2): the main canopy (a1), the lower layer (a2) and
 * the regeneration. Crown territories ({@link CrownTerritories}) are drawn within a layer; the layers occupy different
 * height bands, which the tree planner (step V6) keeps apart. Pure worldgen data, not stored in saved worlds.
 */
public enum CrownLayer {
	/** Main canopy, a1. */
	CANOPY,
	/** Lower tree layer, a2 (hornbeam and linden in the oak-hornbeam forest, younger cohorts in natural pine forest). */
	SUBCANOPY,
	/** Regeneration: saplings and young trees, more of them in the canopy gaps. */
	REGENERATION
}
