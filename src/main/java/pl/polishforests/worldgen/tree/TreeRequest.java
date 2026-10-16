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
 */
public record TreeRequest(Species species, CrownLayer layer, AgeClass age, long worldSeed, int x, int y, int z,
		@Nullable CrownTerritory territory, TreeShapePlan.@Nullable Trunks trunks) {
	public TreeRequest {
		if (!SpeciesShape.supported(species)) {
			throw new IllegalArgumentException("no tree shape for " + species);
		}
		if (territory != null && (territory.x() != x || territory.z() != z)) {
			throw new IllegalArgumentException("the territory of " + territory.x() + ", " + territory.z() + " is not the one of "
					+ x + ", " + z);
		}
	}

	/** A lone tree without a territory. */
	public TreeRequest(Species species, CrownLayer layer, AgeClass age, long worldSeed, int x, int y, int z) {
		this(species, layer, age, worldSeed, x, y, z, null, null);
	}
}
