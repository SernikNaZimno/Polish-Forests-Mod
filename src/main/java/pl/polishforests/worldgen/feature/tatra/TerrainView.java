package pl.polishforests.worldgen.feature.tatra;

/**
 * The ground the planners of the Tatra details (step TT6) read: the planned top of each column (not the heightmap, which
 * counts the crowns of trees and so depends on the order of generation) and the blocks of the region. Trees and their
 * leaves are not ground: a planner treats their cells as free; a plan is not built where a trunk stands in its columns
 * ({@link #log}, {@link Fit}), and the feature clears the plants over a carved column.
 */
public interface TerrainView {
	/** Y of the top ground block of the column (planned surface, without trees). */
	int top(int x, int z);

	/** Whether the cell is solid ground or rock (blocks motion, not a tree, not a plant). */
	boolean solid(int x, int y, int z);

	/** Whether the cell holds water or lava. */
	boolean fluid(int x, int y, int z);

	/**
	 * Whether a plan may write the column. A plan stays inside the chunk that builds it: a cell of a neighbor chunk is written
	 * before or after that chunk's own trees, plants and snow depending on the order of generation. Reads of the neighbors'
	 * ground (the planned tops) are not restricted. True by default (the tests' synthetic grounds).
	 */
	default boolean writable(int x, int z) {
		return true;
	}

	/** Whether the cell holds a log (the trunk of a tree), which a plan must not cut: its crown would float. False by default. */
	default boolean log(int x, int y, int z) {
		return false;
	}

	/** The lowest cell of the world (below it everything is solid). */
	default int minY() {
		return -64;
	}
}
