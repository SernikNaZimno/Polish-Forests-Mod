package pl.polishforests.worldgen.tree;

import pl.polishforests.worldgen.habitat.Species;

/**
 * Output of the dead wood planner ({@link DeadwoodPlan}, step V11): the blocks of one dead wood object relative to its
 * base (the block above the ground at the base column). Without game classes (rule ZW-1); the feature
 * {@code polishforests:procedural_deadwood} turns it into block states.
 *
 * @param kind      the object
 * @param species   its species (the trunk block of the wood)
 * @param direction the fall direction of the lying wood ({@link TreeVoxels#NORTH} … {@link TreeVoxels#EAST}), -1 for
 *                  a standing object or an empty plan
 * @param wood      the wood with radii, parents and barks, as a tree of the planner (no leaves); the root first
 * @param soil      the soil blocks of a root plate (packed offsets, {@link #x}, {@link #y}, {@link #z})
 * @param soilType  {@link DeadwoodPlan#SOIL_CORE}, {@link DeadwoodPlan#SOIL_RIM} or {@link DeadwoodPlan#SOIL_RIM_DIRT} of
 *                  each soil block
 * @param pit       the pit columns: packed (dx, depth, dz), the ground of the column lowered by depth blocks
 * @param cover     moss or leaf litter at the level of the base (packed offsets, dy 0)
 * @param coverType {@link DeadwoodPlan#COVER_MOSS} or {@link DeadwoodPlan#COVER_LITTER} of each cover block
 */
public record DeadwoodVoxels(DeadwoodPlan.Kind kind, Species species, int direction, TreeVoxels wood, int[] soil, byte[] soilType,
		int[] pit, int[] cover, byte[] coverType) {
	/** Whether nothing was planned. */
	public boolean empty() {
		return wood.woodCount() == 0;
	}

	/**
	 * The wood indices of the lying stem (the blocks at the base level whose parent lies back against the fall direction),
	 * ordered along the fall direction; empty for a standing object.
	 */
	public int[] lyingStem() {
		if (direction < 0) {
			return new int[0];
		}
		int[] f = TreeVoxels.STEP[direction];
		int back = direction ^ 1;
		return java.util.stream.IntStream.range(0, wood.woodCount())
				.filter(i -> wood.woodY(i) == 0 && wood.parent(i) == back).boxed()
				.sorted(java.util.Comparator.comparingInt(i -> wood.woodX(i) * f[0] + wood.woodZ(i) * f[2]))
				.mapToInt(Integer::intValue).toArray();
	}

	public int x(int packed) {
		return TreeVoxels.unpackX(packed);
	}

	public int y(int packed) {
		return TreeVoxels.unpackY(packed);
	}

	public int z(int packed) {
		return TreeVoxels.unpackZ(packed);
	}
}
