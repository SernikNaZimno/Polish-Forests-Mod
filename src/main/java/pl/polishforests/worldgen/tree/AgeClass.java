package pl.polishforests.worldgen.tree;

/**
 * Age class of a stand ({@link StandMap}, decision PW-4 b, docs/04-pionowy-wycinek.md): the three classes of the lite stand
 * map. Pure worldgen data, not stored in saved worlds.
 */
public enum AgeClass {
	/** Pole stage (drągowina): dense, slender trees with small crowns. */
	POLE_STAGE,
	/** Mature stand (drzewostan dojrzały). */
	MATURE,
	/** Old growth (starodrzew): the largest crowns. */
	OLD_GROWTH
}
