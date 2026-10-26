package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Shapes of {@link TrunkBlock} (step V2, round 1 of its review): for each parent and radius the shape is a prism of the
 * radius through the whole block along the parent axis (the block shows its lower half and, through the reach of its
 * child, its upper half; the reach of the model into the parent is visual only), and the states behave as the variant D
 * says (water, rotation).
 */
class TrunkShapeTest {
	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
	}

	@Test
	void prismRunsAlongTheParentAxisWithTheRadius() {
		for (Direction parent : Direction.values()) {
			for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
				VoxelShape s = TrunkBlock.shape(parent, r);
				AABB box = s.bounds();
				double h = r / 16.0;
				// Bounds: the whole block along the parent axis, the radius around the center across it.
				for (Direction.Axis axis : Direction.Axis.values()) {
					boolean along = axis == parent.getAxis();
					assertEquals(along ? 0.0 : 0.5 - h, box.min(axis), 1e-9, parent + " r" + r + " " + axis);
					assertEquals(along ? 1.0 : 0.5 + h, box.max(axis), 1e-9, parent + " r" + r + " " + axis);
				}
				// Volume: (2r)^2 × 16 px^3, one box.
				double px = 1 / 16.0;
				double[] v = {0};
				s.forAllBoxes((x0, y0, z0, x1, y1, z1) -> v[0] += (x1 - x0) * (y1 - y0) * (z1 - z0));
				assertEquals(Math.pow(2 * r * px, 2) * 16 * px, v[0], 1e-9, parent + " r" + r);
				// Points just inside the face of the parent and the opposite face are solid (the upper half of a segment,
				// drawn by the reach of the child, can be targeted), a point beside the prism is not.
				Vec3 toParent = parent.getUnitVec3().scale(0.49).add(0.5, 0.5, 0.5);
				Vec3 away = parent.getOpposite().getUnitVec3().scale(0.49).add(0.5, 0.5, 0.5);
				Direction side = parent.getAxis() == Direction.Axis.Y ? Direction.EAST : Direction.UP;
				Vec3 beside = side.getUnitVec3().scale(h + 0.01).add(0.5, 0.5, 0.5);
				assertTrue(contains(s, toParent), parent + " r" + r);
				assertTrue(contains(s, away), parent + " r" + r);
				assertFalse(contains(s, beside), parent + " r" + r);
			}
		}
	}

	@Test
	void waterloggedTrunkHoldsWaterAndRotatesWithItsParent() {
		TrunkBlock b = ModBlocks.trunk(TreeTrunk.OAK);
		assertTrue(b.state(3, Direction.UP).setValue(TrunkBlock.WATERLOGGED, true).getFluidState().is(Fluids.WATER));
		assertTrue(b.state(3, Direction.UP).getFluidState().isEmpty());
		assertEquals(TrunkBlock.MAX_RADIUS, b.state(9, Direction.UP).getValue(TrunkBlock.RADIUS));
		assertEquals(Direction.EAST, b.state(2, Direction.NORTH).rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90)
				.getValue(TrunkBlock.PARENT));
		assertEquals(Direction.DOWN, b.state(2, Direction.DOWN).rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_180)
				.getValue(TrunkBlock.PARENT));
		// Not a full block: the sky light passes, neighbors keep their faces.
		assertTrue(b.state(TrunkBlock.MAX_RADIUS, Direction.DOWN).propagatesSkylightDown());
		assertFalse(b.state(TrunkBlock.MAX_RADIUS, Direction.DOWN).isSolidRender());
	}

	private static boolean contains(VoxelShape s, Vec3 p) {
		for (AABB a : s.toAabbs()) {
			if (a.contains(p)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Step I1: a root flare lying on its side (a horizontal parent) has the prism of its radius lowered to the floor of the
	 * block, from 0 to 2r - sink px high (the part below the floor is only drawn), along the parent axis through the block;
	 * a vertical flare is a thin trunk's prism.
	 */
	@Test
	void rootFlareLiesOnTheGround() {
		for (Direction parent : Direction.values()) {
			for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
				VoxelShape s = RootFlareBlock.flareShape(parent, r);
				if (parent.getAxis() == Direction.Axis.Y) {
					assertEquals(TrunkBlock.shape(parent, r).bounds(), s.bounds(), parent + " r" + r);
					continue;
				}
				AABB box = s.bounds();
				double px = 1 / 16.0;
				assertEquals(0.0, box.minY, 1e-9, parent + " r" + r);
				assertEquals((2 * r - RootFlareBlock.sink(r)) * px, box.maxY, 1e-9, parent + " r" + r);
				for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
					boolean along = axis == parent.getAxis();
					assertEquals(along ? 0.0 : 0.5 - r * px, box.min(axis), 1e-9, parent + " r" + r + " " + axis);
					assertEquals(along ? 1.0 : 0.5 + r * px, box.max(axis), 1e-9, parent + " r" + r + " " + axis);
				}
			}
		}
		// The flares are thin trunks of the species' bark, the underground root is one with its own bark.
		RootFlareBlock oak = ModBlocks.flare(RootFlare.OAK);
		assertTrue(oak instanceof TrunkBlock);
		assertEquals("oak_root_flare", RootFlare.OAK.id());
		assertEquals(ModBlocks.TREE_ROOT, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ModBlocks.treeRoot()).getPath());
		assertFalse(ModBlocks.treeRoot() instanceof RootFlareBlock);
	}
}
