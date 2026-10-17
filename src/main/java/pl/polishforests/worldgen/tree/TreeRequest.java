package pl.polishforests.worldgen.tree;

import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Input of the tree planner ({@link TreeShapePlan}, step V6): the species, the layer and age class of the tree, the world
 * seed and the position of the trunk base (the block above the ground), and optionally the crown territory of the trunk
 * ({@link CrownTerritories}), to which the crown and the branches are clipped, and the columns of the other trunks. The
 * shape is a pure function of these values; the base height {@code y} only moves the tree, it does not change its shape.
 *
 * @param territory the crown territory of the trunk at (x, z) in its layer, or null for a lone tree (a sapling, a
 *                  preview, the feature {@code polishforests:procedural_tree}), which is clipped to a disc of
 *                  {@value CrownTerritories#MAX_REACH} blocks only
 * @param trunks    the columns where other trunks may stand (in the game the kept candidates of the M2 pattern,
 *                  {@code CrownTerritories.Session#candidate}): the wood of a subcanopy or regeneration tree keeps one
 *                  column away from them, so it never meets the trunk or a dry stub of a canopy tree; null for none
 * @param stands    the stand map of a tree in a stand: its age class at each column sets the layer split surfaces
 *                  ({@link TreeShapePlan#split}), which every tree of the stand must see alike; null for the age class of
 *                  the request everywhere. A request with neither a territory nor a stand map is a lone, open-grown tree
 *                  (a longer and wider crown, no split below the canopy)
 * @param ground    the ground of the columns around the trunk (step V9): the split surfaces stand at their height above
 *                  the ground of each column, so they follow a slope; null for level ground at the trunk base
 */
public record TreeRequest(Species species, CrownLayer layer, AgeClass age, long worldSeed, int x, int y, int z,
		@Nullable CrownTerritory territory, TreeShapePlan.@Nullable Trunks trunks, @Nullable StandMap stands,
		TreeShapePlan.@Nullable Ground ground) {
	/** A request on level ground. */
	public TreeRequest(Species species, CrownLayer layer, AgeClass age, long worldSeed, int x, int y, int z,
			@Nullable CrownTerritory territory, TreeShapePlan.@Nullable Trunks trunks, @Nullable StandMap stands) {
		this(species, layer, age, worldSeed, x, y, z, territory, trunks, stands, null);
	}

	public TreeRequest {
		if (!SpeciesShape.supported(species)) {
			throw new IllegalArgumentException("no tree shape for " + species);
		}
		if (territory != null && (territory.x() != x || territory.z() != z)) {
			throw new IllegalArgumentException("the territory of " + territory.x() + ", " + territory.z() + " is not the one of "
					+ x + ", " + z);
		}
	}

	/** A lone, open-grown tree without a territory. */
	public TreeRequest(Species species, CrownLayer layer, AgeClass age, long worldSeed, int x, int y, int z) {
		this(species, layer, age, worldSeed, x, y, z, null, null, null, null);
	}

	/** Whether the tree grows alone (no territory and no stand map): an open-grown crown, no split below the canopy. */
	public boolean openGrown() {
		return territory == null && stands == null;
	}

	/**
	 * Height of the ground of world column (x, z) above the ground of the trunk (the block below the trunk base), 0 on
	 * level ground or where the ground is unknown.
	 */
	public int groundShift(int wx, int wz) {
		if (ground == null) {
			return 0;
		}
		int top = ground.top(wx, wz);
		return top == TreeShapePlan.Ground.UNKNOWN ? 0 : top + 1 - y;
	}

	/** Age class of the stand at column (x, z): from the stand map, or the request's own. */
	public AgeClass ageAt(int wx, int wz) {
		return stands == null ? age : stands.ageClass(wx, wz);
	}
}
