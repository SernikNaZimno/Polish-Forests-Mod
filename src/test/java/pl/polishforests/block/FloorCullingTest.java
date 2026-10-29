package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Step O2 ({@link FloorCulling}): the mossy floor plants occlude exactly their moss layer and nothing else changes for
 * the game (no collision, the light and the outline as before, the plants without moss occlude nothing); a buried root is
 * hidden only when no neighbor, and no neighbor of a root next to it, lets a camera look in.
 */
class FloorCullingTest {
	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
	}

	@Test
	void mossyPlantsOccludeTheirMossLayerOnly() {
		int checked = 0;
		for (GroundPlant g : new GroundPlant[] {GroundPlant.BILBERRY, GroundPlant.NEEDLE_LITTER}) {
			Block block = ModBlocks.ground(g);
			for (BlockState s : block.getStateDefinition().getPossibleStates()) {
				boolean mossy = s.getValue(BerryShrubBlock.MOSSY);
				String what = s.toString();
				assertTrue(s.canOcclude(), what);
				assertEquals(mossy ? FloorCulling.MOSS_LAYER.toAabbs() : java.util.List.of(), s.getOcclusionShape().toAabbs(), what);
				assertTrue(s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).isEmpty(), what);
				assertFalse(s.isSolidRender(), what);
				assertFalse(s.isSolid(), what);
				assertTrue(s.propagatesSkylightDown(), what);
				assertEquals(0, s.getLightDampening(), what);
				assertFalse(s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty(), what);
				// The top of the ground under a mossy plant is culled, the sides of the neighbors only along the layer.
				assertEquals(mossy, !Block.shouldRenderFace(Blocks.PODZOL.defaultBlockState(), s, Direction.UP), what + " over podzol");
				assertTrue(Block.shouldRenderFace(Blocks.DIRT.defaultBlockState(), s, Direction.NORTH), what + " beside dirt");
				// Two mossy plants side by side: the layer's side faces between them are culled.
				assertEquals(mossy, !Block.shouldRenderFace(s, s, Direction.EAST), what + " beside itself");
				checked++;
			}
		}
		assertTrue(checked >= 8, "states checked: " + checked);
	}

	/** Neighbors from a map of offsets (default solid). */
	private static FloorCulling.Neighbors around(Map<String, FloorCulling.Neighbor> m) {
		return (dx, dy, dz) -> m.getOrDefault(dx + "," + dy + "," + dz, FloorCulling.Neighbor.SOLID);
	}

	@Test
	void buriedRootHiddenOnlyWithoutAViewIn() {
		Map<String, FloorCulling.Neighbor> m = new HashMap<>();
		assertTrue(FloorCulling.hiddenRoot(around(m)), "in solid ground");
		m.put("0,1,0", FloorCulling.Neighbor.ROOT);
		m.put("0,-1,0", FloorCulling.Neighbor.ROOT);
		assertTrue(FloorCulling.hiddenRoot(around(m)), "in a chain of buried roots");
		m.put("0,2,0", FloorCulling.Neighbor.ROOT);
		assertTrue(FloorCulling.hiddenRoot(around(m)), "a root two blocks up is still closed");
		m.put("1,1,0", FloorCulling.Neighbor.OPEN);
		assertFalse(FloorCulling.hiddenRoot(around(m)), "a root next to it touches an opening");
		m.remove("1,1,0");
		m.put("-1,0,0", FloorCulling.Neighbor.OPEN);
		assertFalse(FloorCulling.hiddenRoot(around(m)), "an opening next to it");
		m.remove("-1,0,0");
		m.put("0,-2,0", FloorCulling.Neighbor.OPEN);
		assertFalse(FloorCulling.hiddenRoot(around(m)), "the root below touches an opening");
	}
}
