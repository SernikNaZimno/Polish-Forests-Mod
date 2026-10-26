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
	 * Step I1 (review round 1): a root flare with a horizontal parent is a fin on the ground: from the floor of the block up
	 * to the profile's height at the parent's side, as wide as its foot across the parent axis and through the whole block
	 * along it, the top falling along the block (the far half lower); the stages fall in height and width outwards, the
	 * stage-1 block ends at the ground; the spruce's flat flares are lower. A vertical flare is a thin trunk's prism.
	 */
	@Test
	void rootFlareIsAFinOnTheGround() {
		for (RootFlare f : new RootFlare[] {RootFlare.OAK, RootFlare.SPRUCE}) {
			RootFlareBlock block = ModBlocks.flare(f);
			boolean flat = block.flat();
			assertEquals(f == RootFlare.SPRUCE, flat);
			for (Direction parent : Direction.values()) {
				for (int s = TrunkBlock.MIN_RADIUS; s <= TrunkBlock.MAX_RADIUS; s++) {
					VoxelShape shape = block.flareShape(parent, s);
					String what = f + " " + parent + " s" + s;
					if (parent.getAxis() == Direction.Axis.Y) {
						assertEquals(TrunkBlock.shape(parent, s).bounds(), shape.bounds(), what);
						continue;
					}
					AABB box = shape.bounds();
					double px = 1 / 16.0;
					assertEquals(0.0, box.minY, 1e-9, what);
					double high = flat ? Math.max(1, FlareProfile.height(true, s)) : FlareProfile.height(false, s);
					assertEquals(high * px, box.maxY, 1e-9, what);
					double half = FlareProfile.footWidth(flat, s) / 2;
					for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
						boolean along = axis == parent.getAxis();
						assertEquals(along ? 0.0 : 0.5 - half * px, box.min(axis), 1e-9, what + " " + axis);
						assertEquals(along ? 1.0 : 0.5 + half * px, box.max(axis), 1e-9, what + " " + axis);
					}
					if (!flat && FlareProfile.height(false, s - 1) < FlareProfile.height(false, s)) {
						// The far half of the fin is lower than the near one.
						Vec3 near = new Vec3(0.5 + 0.4 * parent.getStepX(), 0, 0.5 + 0.4 * parent.getStepZ());
						Vec3 far = new Vec3(0.5 - 0.4 * parent.getStepX(), 0, 0.5 - 0.4 * parent.getStepZ());
						assertTrue(top(shape, near) > top(shape, far), what + ": the fin does not fall away from the parent");
					}
					if (s > 1) {
						assertTrue(FlareProfile.height(flat, s) > FlareProfile.height(flat, s - 1) || s > 4, what);
						assertTrue(FlareProfile.footWidth(flat, s) >= FlareProfile.footWidth(flat, s - 1), what);
					}
				}
			}
			assertEquals(0.0, FlareProfile.height(flat, 0), "a flare ends in the ground");
		}
		assertTrue(FlareProfile.height(true, 3) < FlareProfile.height(false, 3), "flat flares as high as fins");
		// The flares are thin trunks of the species' bark, the underground root is one with its own bark.
		RootFlareBlock oak = ModBlocks.flare(RootFlare.OAK);
		assertTrue(oak instanceof TrunkBlock);
		assertEquals("oak_root_flare", RootFlare.OAK.id());
		assertEquals(ModBlocks.TREE_ROOT, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ModBlocks.treeRoot()).getPath());
		assertFalse(ModBlocks.treeRoot() instanceof RootFlareBlock);
	}

	/** The highest y of the shape's boxes over the point (x, z) of the block. */
	private static double top(VoxelShape shape, Vec3 p) {
		double y = 0;
		for (AABB b : shape.toAabbs()) {
			if (p.x >= b.minX && p.x <= b.maxX && p.z >= b.minZ && p.z <= b.maxZ) {
				y = Math.max(y, b.maxY);
			}
		}
		return y;
	}
}
