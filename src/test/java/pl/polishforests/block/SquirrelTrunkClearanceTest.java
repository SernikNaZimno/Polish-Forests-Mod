package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.polishforests.entity.squirrel.RedSquirrel;
import pl.polishforests.entity.squirrel.TreeGeometry;

/**
 * Review of FA5, round 1: a red squirrel clinging to an upright trunk of the mod at any height keeps its hitbox off the
 * live collision of the trunk ({@link TrunkShapes}), also past a step where the trunk narrows going up (the lower block's
 * core reaches 8 + r px into it) or widens (the upper block's arm from the lower block's center), and it clings right at
 * the widest bark its box spans (no extra air). The clinging distance is {@link TreeGeometry#columnHalfOver} with
 * {@link TreeGeometry#blockHalfOver}, as {@code TreeSpace.halfOver} reads them from a world. The old rule (the radius of
 * the block at feet + 0.2) put the box into the wider core below a 7 to 6 step.
 */
class SquirrelTrunkClearanceTest {
	private static double HEIGHT;
	private static final double HALF = TreeGeometry.HALF_WIDTH;

	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
		HEIGHT = RedSquirrel.PROFILE.height();
	}

	@Test
	void clingingClearsEveryStepOfATaperingTrunk() {
		int[][] columns = {
				{7, 7, 6, 6, 5, 5, 4}, {7, 6, 5, 4, 3, 2, 1}, {7, 5, 3, 1}, {6, 5, 4, 4, 2}, {5, 4, 3, 2}, {3, 2, 1, 1},
				// widening going up (a player's build, a regrown stem): the upper arm reaches down to the lower center
				{2, 4, 6, 7}, {4, 4, 6, 3, 5}};
		for (int[] radii : columns) {
			List<VoxelShape> shapes = column(radii);
			int top = radii.length - 1;
			// the heights of the feet a climb or a descent passes, off the 1/16 grid of the shapes
			for (double y = 0.0013; y <= top + 1.0 - HEIGHT * 0.5; y += 0.005) {
				double half = TreeGeometry.columnHalfOver(0, top, y, y + HEIGHT, (by, f0, f1) -> blockHalf(radii, by, f0, f1));
				for (Direction side : Direction.Plane.HORIZONTAL) {
					double[] xz = TreeGeometry.clingXZ(0, 0, side, half);
					String where = java.util.Arrays.toString(radii) + " y=" + String.format("%.4f", y) + " " + side;
					assertFalse(hits(shapes, box(xz, y)), "clinging box in the trunk at " + where);
					if (y >= top + 0.5) {
						continue; // above the core of the top block: the bare tip, nothing to touch
					}
					// right at the bark: a little more than the gap inwards and it touches the trunk
					double in = TreeGeometry.BARK_GAP + 0.002;
					double[] closer = {xz[0] - side.getStepX() * in, xz[1] - side.getStepZ() * in};
					assertTrue(hits(shapes, box(closer, y)), "clinging box farther from the bark than the gap at " + where);
				}
			}
		}
	}

	@Test
	void theOldRuleOverlappedTheCoreBelowASevenToSixStep() {
		int[] radii = {7, 6, 6};
		List<VoxelShape> shapes = column(radii);
		boolean overlapped = false;
		for (double y = 0.0013; y < 2.0; y += 0.005) {
			int by = Math.clamp((long) Math.floor(y + 0.2), 0, radii.length - 1);
			double[] xz = TreeGeometry.clingXZ(0, 0, Direction.NORTH, TreeGeometry.trunkHalf(radii[by]));
			overlapped |= hits(shapes, box(xz, y));
		}
		assertTrue(overlapped, "the old clinging rule should have overlapped the wider core below the step");
	}

	/** The collision of an upright column of trunk blocks of the radii (block 0 at y 0), each the parent of the one above. */
	private static List<VoxelShape> column(int[] radii) {
		List<VoxelShape> out = new ArrayList<>();
		for (int i = 0; i < radii.length; i++) {
			int[] children = new int[6];
			if (i + 1 < radii.length) {
				children[Direction.UP.get3DDataValue()] = radii[i + 1];
			}
			out.add(TrunkShapes.collision(TrunkShapes.key(radii[i], Direction.DOWN, children)).move(0, i, 0));
		}
		return out;
	}

	private static double blockHalf(int[] radii, int by, double f0, double f1) {
		double own = TreeGeometry.trunkHalf(radii[by]);
		double arm = by + 1 < radii.length ? TreeGeometry.trunkHalf(radii[by + 1]) : 0.0;
		return TreeGeometry.blockHalfOver(own, 0.5 + own, arm, f0, f1);
	}

	private static AABB box(double[] xz, double y) {
		return new AABB(xz[0] - HALF, y, xz[1] - HALF, xz[0] + HALF, y + HEIGHT, xz[1] + HALF);
	}

	private static boolean hits(List<VoxelShape> shapes, AABB box) {
		VoxelShape b = Shapes.create(box);
		for (VoxelShape s : shapes) {
			if (Shapes.joinIsNotEmpty(s, b, BooleanOp.AND)) {
				return true;
			}
		}
		return false;
	}
}
