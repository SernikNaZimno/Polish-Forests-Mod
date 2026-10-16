package pl.polishforests.worldgen.tree;

/**
 * Columns outside the forest for the forest edge asymmetry of the crown territories ({@link CrownTerritories}). In the
 * game the source is the {@code ChunkHabitats} of the chunk being decorated and of its 8 neighbors (at the FEATURES stage
 * only the chunks within radius 1 have them): a column is open when its habitat biome is not a forest biome. The
 * territories ask only about columns in the chunks within radius 1 of the trunk's chunk.
 */
@FunctionalInterface
public interface OpenGround {
	/** Whether column (x, z) lies outside the forest. */
	boolean open(int x, int z);
}
