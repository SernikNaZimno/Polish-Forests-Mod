package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Review of F6, round 1: in world generation the creeping dwarf pine ({@code CreepingShrubFeature}) takes the ground of
 * its columns from the chunk habitats ({@link CreepingShrubPlan.Ground#ofTops}), not from the blocks other features
 * have already written, so its plan is the same in every chunk order (ZW-1). The ground is the free block over the top
 * ground block relative to the base, unknown where the habitats do not know a column and beyond the reach.
 */
class CreepingShrubGroundTest {
	/** The reach of {@code CreepingShrubFeature}. */
	private static final int REACH = 3;
	private static final BlockPos BASE = new BlockPos(1_000, 140, -2_000);

	/** A slope of one block per 3 columns east, unknown in one column. */
	private static final TreeShapePlan.Ground SLOPE = (x, z) -> x == BASE.getX() + 2 && z == BASE.getZ() + 1
			? TreeShapePlan.Ground.UNKNOWN : BASE.getY() - 1 + Math.floorDiv(x - BASE.getX(), 3);

	@Test
	void groundIsRelativeToTheBase() {
		CreepingShrubPlan.Ground g = CreepingShrubPlan.Ground.ofTops(SLOPE, BASE.getX(), BASE.getY(), BASE.getZ(), REACH);
		assertEquals(0, g.free(0, 0));
		assertEquals(1, g.free(3, 0));
		assertEquals(-1, g.free(-1, 5));
		assertEquals(REACH, g.free(3 * REACH, 0));
		assertEquals(CreepingShrubPlan.UNKNOWN, g.free(3 * REACH + 3, 0), "beyond the reach");
		assertEquals(CreepingShrubPlan.UNKNOWN, g.free(-3 * REACH - 1, 0), "beyond the reach below");
		assertEquals(CreepingShrubPlan.UNKNOWN, g.free(2, 1), "a column the habitats do not know");
	}

	/** The plan on the habitats ground depends only on the seed, the base and the habitats (the same in any chunk order). */
	@Test
	void planIsAPureFunctionOfTheHabitats() {
		for (int k = 0; k < 40; k++) {
			BlockPos base = BASE.offset(17 * k, 0, -11 * k);
			TreeVoxels a = CreepingShrubPlan.plan(new CreepingShrubPlan.Request(7L, base.getX(), base.getY(), base.getZ(), 1,
					CreepingShrubPlan.Ground.ofTops((x, z) -> base.getY() - 1, base.getX(), base.getY(), base.getZ(), REACH)));
			TreeVoxels b = CreepingShrubPlan.plan(new CreepingShrubPlan.Request(7L, base.getX(), base.getY(), base.getZ(), 1,
					CreepingShrubPlan.Ground.ofTops((x, z) -> base.getY() - 1, base.getX(), base.getY(), base.getZ(), REACH)));
			assertEquals(a, b, "shrub " + k);
			assertTrue(a.woodCount() > 0, "shrub " + k + " has wood");
		}
	}
}
