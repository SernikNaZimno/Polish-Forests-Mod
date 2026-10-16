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
 * Shapes of {@link TrunkBlock} (step V2): for each parent and radius the shape is the core cube of the radius plus the
 * arm from the center to the face of the parent, inside the block (the reach of the model into the parent is visual
 * only), and the states behave as the variant D says (water, rotation).
 */
class TrunkShapeTest {
	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
	}

	@Test
	void armPointsToTheParentWithTheRadius() {
		for (Direction parent : Direction.values()) {
			for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
				VoxelShape s = TrunkBlock.shape(parent, r);
				AABB box = s.bounds();
				double h = r / 16.0;
				// Bounds: the core ±r around the center, along the parent axis from the face of the parent to center + r.
				for (Direction.Axis axis : Direction.Axis.values()) {
					double lo = box.min(axis);
					double hi = box.max(axis);
					if (axis == parent.getAxis()) {
						boolean positive = parent.getAxisDirection() == Direction.AxisDirection.POSITIVE;
						assertEquals(positive ? 0.5 - h : 0.0, lo, 1e-9, parent + " r" + r + " " + axis);
						assertEquals(positive ? 1.0 : 0.5 + h, hi, 1e-9, parent + " r" + r + " " + axis);
					} else {
						assertEquals(0.5 - h, lo, 1e-9, parent + " r" + r + " " + axis);
						assertEquals(0.5 + h, hi, 1e-9, parent + " r" + r + " " + axis);
					}
				}
				// Volume: core (2r)^3 plus the arm from the core to the face, (2r)^2 (8 - r), in px^3.
				double px = 1 / 16.0;
				double volume = Math.pow(2 * r * px, 3) + Math.pow(2 * r * px, 2) * (8 - r) * px;
				double[] v = {0};
				s.forAllBoxes((x0, y0, z0, x1, y1, z1) -> v[0] += (x1 - x0) * (y1 - y0) * (z1 - z0));
				assertEquals(volume, v[0], 1e-9, parent + " r" + r);
				// A point just inside the face of the parent is solid, one at the opposite face is not.
				Vec3 toParent = parent.getUnitVec3().scale(0.49).add(0.5, 0.5, 0.5);
				Vec3 away = parent.getOpposite().getUnitVec3().scale(0.49).add(0.5, 0.5, 0.5);
				assertTrue(contains(s, toParent), parent + " r" + r);
				assertFalse(contains(s, away), parent + " r" + r);
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
}
