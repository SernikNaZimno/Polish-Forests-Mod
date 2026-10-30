package pl.polishforests.worldgen.cave;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.GameRegistries;

/**
 * Step O2: the early answer of the sculk fast path ({@link SculkFastPath#cannotHold}) is a sufficient condition of
 * vanilla's answer false for every block state: the last branch of {@code SculkVeinSpreaderConfig.stateCanBeReplaced} is
 * "replaceable, air, the vein block or a water source", and every other branch answers false. (The game test
 * {@code determinism} with {@code -ProotedDirtShare=0.3} compared whole worlds with the build before the fast path.)
 */
class SculkFastPathTest {
	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
	}

	@Test
	void earlyAnswerOnlyWhereVanillaSaysNo() {
		int states = 0;
		int early = 0;
		for (BlockState s : Block.BLOCK_STATE_REGISTRY) {
			boolean lastBranch = s.canBeReplaced() || s.isAir() || s.is(Blocks.SCULK_VEIN)
					|| s.is(Blocks.WATER) && s.getFluidState().isSource();
			if (SculkFastPath.cannotHold(s)) {
				assertFalse(lastBranch, s.toString());
				early++;
			}
			states++;
		}
		assertTrue(states > 20_000 && early > states / 2, "states " + states + ", early " + early);
		for (Block b : new Block[] {Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.SCULK, Blocks.COBBLED_DEEPSLATE}) {
			assertTrue(SculkFastPath.cannotHold(b.defaultBlockState()), b.toString());
		}
		for (Block b : new Block[] {Blocks.AIR, Blocks.CAVE_AIR, Blocks.WATER, Blocks.SCULK_VEIN, Blocks.SHORT_GRASS, Blocks.LAVA}) {
			assertFalse(SculkFastPath.cannotHold(b.defaultBlockState()), b.toString());
		}
		assertTrue(SculkFastPath.ENABLED, "the fast path is on by default");
	}
}
