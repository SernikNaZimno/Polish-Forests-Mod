package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Step G1: the search that keeps a leaf from falling while wood still reaches it ({@link SpeciesLeavesBlock#reach})
 * ignores the distances the leaves store (the planner writes each tree's own, so they can be too large where crowns meet)
 * and walks only through leaves, at most six steps.
 */
class LeafReachTest {
	private static final Predicate<BlockState> WOOD = s -> s.is(Blocks.OAK_LOG);

	@BeforeAll
	static void bootstrap() {
		GameRegistries.bootstrap();
	}

	/** A row of leaves east of a log, all storing the decay distance; air elsewhere. */
	private static Map<BlockPos, BlockState> row(int leaves) {
		Map<BlockPos, BlockState> world = new HashMap<>();
		world.put(BlockPos.ZERO, Blocks.OAK_LOG.defaultBlockState());
		BlockState stale = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, LeavesBlock.DECAY_DISTANCE);
		for (int x = 1; x <= leaves; x++) {
			world.put(new BlockPos(x, 0, 0), stale);
		}
		return world;
	}

	private static int reach(Map<BlockPos, BlockState> world, BlockPos at) {
		return SpeciesLeavesBlock.reach(p -> world.getOrDefault(p, Blocks.AIR.defaultBlockState()), at, WOOD);
	}

	@Test
	void trueDistanceDespiteStoredDecayDistance() {
		Map<BlockPos, BlockState> world = row(8);
		assertEquals(1, reach(world, new BlockPos(1, 0, 0)));
		assertEquals(4, reach(world, new BlockPos(4, 0, 0)));
		assertEquals(6, reach(world, new BlockPos(6, 0, 0)));
		assertEquals(LeavesBlock.DECAY_DISTANCE, reach(world, new BlockPos(7, 0, 0)), "seven steps is out of reach");
	}

	@Test
	void onlyThroughLeaves() {
		Map<BlockPos, BlockState> world = row(5);
		world.remove(new BlockPos(2, 0, 0));
		assertEquals(LeavesBlock.DECAY_DISTANCE, reach(world, new BlockPos(3, 0, 0)), "a gap of air cuts the leaves off");
		// A detour over the gap through leaves above: 3 -> (3,1) -> (2,1) -> (1,1) -> 1 -> log.
		BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			world.put(new BlockPos(x, 1, 0), leaf);
		}
		assertEquals(5, reach(world, new BlockPos(3, 0, 0)));
	}
}
