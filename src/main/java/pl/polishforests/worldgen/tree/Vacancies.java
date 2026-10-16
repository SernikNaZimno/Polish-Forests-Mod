package pl.polishforests.worldgen.tree;

/**
 * Kept trunk candidates that grow no tree at all, the markers of the crown territories ({@link CrownTerritories}): their
 * cells go to the neighboring trees, so a candidate left out by the palette does not leave a hole in the canopy, and the
 * crowns of the forest edge lean out over the open ground.
 *
 * <p>In the game (step V9) the source is the {@code ChunkHabitats} of the chunk being decorated and of its 8 neighbors (at
 * the FEATURES stage only the chunks within radius 1 have them) with the pure rules of the tree stand plan: a candidate is
 * vacant when the first palette rule of its column's habitat code is missing or does not give it a tree (rule chance,
 * thinning of sparse stands, gap noise, water depth; {@code TreeStandPlan}), on open ground as well as in the forest. A
 * candidate that gets any tree, a procedural one or a vanilla substitute ({@code tree_substitute/} rules, PW-3), is not
 * vacant: it keeps its cell, so the procedural crowns next to it are not granted the reach of an edge toward it. The
 * territories ask only about kept candidate columns in the chunks within radius 1 of the trunk's chunk.
 */
@FunctionalInterface
public interface Vacancies {
	/** Whether the kept candidate at column (x, z) grows no tree. */
	boolean vacant(int x, int z);
}
