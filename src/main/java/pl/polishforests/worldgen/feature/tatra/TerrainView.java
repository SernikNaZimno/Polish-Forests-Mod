package pl.polishforests.worldgen.feature.tatra;

/**
 * The ground the planners of the Tatra details (step TT6) read: the planned top of each column (not the heightmap, which
 * counts the crowns of trees and so depends on the order of generation) and the blocks of the region. Trees and their
 * leaves are not ground: a planner treats their cells as free and the feature clears them where it builds.
 */
public interface TerrainView {
	/** Y of the top ground block of the column (planned surface, without trees). */
	int top(int x, int z);

	/** Whether the cell is solid ground or rock (blocks motion, not a tree, not a plant). */
	boolean solid(int x, int y, int z);

	/** Whether the cell holds water or lava. */
	boolean fluid(int x, int y, int z);

	/** The lowest cell of the world (below it everything is solid). */
	default int minY() {
		return -64;
	}
}
