package pl.polishforests.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.ClimberBlock;
import pl.polishforests.block.GameRegistries;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.block.TrunkBlock;

/**
 * Round 1 of the F5 review: the hop of a shrub ({@link HopDecorator}, {@link TreeHop#decorate}) is written only beside a
 * block that holds it, so every hop block of the world generation survives ({@link ClimberBlock#canSurvive}); the shoots
 * of an osier (thin trunk of radius 1 px) carry none, its leaves do.
 */
class HopDecoratorSupportTest {
	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
	}

	@Test
	void everyShrubHopHasASupport() {
		BlockState shoot = ModBlocks.trunk(TreeTrunk.WHITE_WILLOW).defaultBlockState().setValue(TrunkBlock.RADIUS, 1);
		BlockState leaf = ModBlocks.leaves(TreeFoliage.OSIER).defaultBlockState();
		assertFalse(ClimberBlock.support(shoot), "a thin shoot does not hold hop");
		// The unit tests run without the data packs, so the tag minecraft:leaves is empty here: the leaves of the test hold
		// hop as in the game, every other block by ClimberBlock.support.
		java.util.function.Predicate<BlockState> holds = st -> st == leaf || ClimberBlock.support(st);
		for (long seed = 1; seed <= 20; seed++) {
			// An osier: a shoot of 4 blocks with a crown of leaves on its upper two blocks and above.
			Map<BlockPos, BlockState> world = new HashMap<>();
			List<BlockPos> blocks = new ArrayList<>();
			int x = (int) seed * 37;
			int z = (int) seed * -11;
			for (int y = 0; y < 4; y++) {
				BlockPos p = new BlockPos(x, 64 + y, z);
				world.put(p, shoot);
				blocks.add(p);
			}
			for (int dy = 2; dy <= 4; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos p = new BlockPos(x + dx, 64 + dy, z + dz);
						if (!world.containsKey(p)) {
							world.put(p, leaf);
							blocks.add(p);
						}
					}
				}
			}
			Map<BlockPos, BlockState> hop = new HashMap<>();
			TreeHop.decorate(seed, blocks, 64 + 4, 1.0, p -> world.containsKey(p) && holds.test(world.get(p)),
					p -> !world.containsKey(p) && !hop.containsKey(p), hop::put);
			assertFalse(hop.isEmpty(), "the leaves carry hop");
			for (Map.Entry<BlockPos, BlockState> e : hop.entrySet()) {
				BlockState support = world.get(e.getKey().relative(e.getValue().getValue(ClimberBlock.FACING)));
				assertTrue(support != null && holds.test(support), "hop at " + e.getKey() + " on " + support);
				assertTrue(e.getKey().getY() < 64 + 4, "below the crown top");
				// Below the crown only the bare shoot stands: no hop beside it (before the review it floated there).
				assertTrue(e.getKey().getY() >= 64 + 2, "hop beside the bare shoot at " + e.getKey());
			}
		}
	}
}
