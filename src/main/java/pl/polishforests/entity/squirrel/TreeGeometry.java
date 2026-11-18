package pl.polishforests.entity.squirrel;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The geometry of the red squirrel's movement in the trees (step FA5, docs/04-pionowy-wycinek.md "Projekt FA5"), without a
 * world, so the unit tests check it: where a squirrel clings to a trunk, which way it faces, the arc and the length of a
 * leap between two trunks.
 *
 * <p>A clinging squirrel hangs on one of the four cardinal sides of an upright trunk: its centre is
 * {@link #clingDistance} from the trunk's axis, so its hitbox ({@value #HALF_WIDTH} blocks to each side) stays
 * {@value #BARK_GAP} blocks off the bark. The bark plane of the model's climbing animations is
 * {@code tools/fauna/animations.py CLIMB_PLANE} (6.4 model px at the render scale 0.5 = 0.2 blocks), the same half width,
 * so the drawn paws touch the drawn bark. A trunk of the mod of radius r px is r/16 blocks from its axis to the bark; a
 * vanilla log 0.5.
 */
public final class TreeGeometry {
	/** Half the width of the squirrel's hitbox (blocks). */
	public static final double HALF_WIDTH = 0.2;
	/** Air between the bark and the hitbox of a clinging squirrel (blocks). */
	public static final double BARK_GAP = 0.02;
	/** Half the width of a vanilla log (blocks). */
	public static final double VANILLA_LOG_HALF = 0.5;
	/** Shortest and longest leap between two trunks (blocks of horizontal flight from take-off to landing). */
	public static final double MIN_LEAP = 1.0;
	public static final double MAX_LEAP = 4.0;
	/** Largest drop and largest rise of a leap (blocks). */
	public static final double MAX_LEAP_DROP = 3.0;
	public static final double MAX_LEAP_RISE = 1.0;
	/** Horizontal speed of a leap (blocks per tick). */
	public static final double LEAP_SPEED = 0.32;
	/** Shortest leap in ticks. */
	public static final int MIN_LEAP_TICKS = 6;
	/** Tolerance of {@link #blockHalfOver} (blocks). */
	static final double SHAPE_EPS = 1.0E-6;

	private TreeGeometry() {
	}

	/** Distance (blocks) from the axis to the bark of a trunk of the mod of the radius (px). */
	public static double trunkHalf(int radiusPx) {
		return radiusPx / 16.0;
	}

	/**
	 * The largest distance (blocks) from the axis to the bark of one upright block of a trunk over the share [f0, f1] of its
	 * height (0 its bottom, 1 its top), as {@code TrunkShapes} builds the collision of a trunk of the mod (review of FA5,
	 * round 1): the block's own part of the half width {@code own} from its bottom up to {@code coreTop} (0.5 + own: the core
	 * ends r px past the center) and the arm of its upright child above, of the half width {@code arm} (0: no child), from
	 * the center up to the top. So a trunk narrowing going up keeps the lower block's width up to 8 + r px into that block,
	 * and one widening has the upper block's width from the center of the lower one. A vanilla log: own 0.5, coreTop 1, arm 0.
	 * A span touching a part within {@value #SHAPE_EPS} counts (an outward error only adds air off the bark).
	 */
	public static double blockHalfOver(double own, double coreTop, double arm, double f0, double f1) {
		double m = 0;
		if (f0 < coreTop + SHAPE_EPS && f1 > -SHAPE_EPS) {
			m = own;
		}
		if (arm > 0 && f0 < 1 + SHAPE_EPS && f1 > 0.5 - SHAPE_EPS) {
			m = Math.max(m, arm);
		}
		return m;
	}

	/** The largest distance from the axis to the bark of one block of a trunk over the share [f0, f1] of its height. */
	@FunctionalInterface
	public interface BlockHalf {
		double over(int y, double f0, double f1);
	}

	/**
	 * The largest distance (blocks) from the axis to the bark of the trunk from {@code baseY} to {@code topY} over the heights
	 * [y0, y1] (the span of a squirrel's hitbox): the widest of the blocks the span covers, each over its share of the span
	 * ({@link #blockHalfOver}). A span past an end of the trunk, or over the bare tip above a top's core, takes the whole
	 * nearest block (as the clinging distance before the review of FA5).
	 */
	public static double columnHalfOver(int baseY, int topY, double y0, double y1, BlockHalf block) {
		int b0 = Math.clamp((long) Math.floor(y0), baseY, topY);
		int b1 = Math.clamp((long) Math.floor(y1 - 1.0E-7), baseY, topY);
		double m = 0;
		for (int by = b0; by <= b1; by++) {
			double f0 = Math.clamp(y0 - by, 0.0, 1.0);
			double f1 = Math.clamp(y1 - by, 0.0, 1.0);
			if (f1 > f0) {
				m = Math.max(m, block.over(by, f0, f1));
			}
		}
		return m > 0 ? m : block.over(b0, 0.0, 1.0);
	}

	/** Distance (blocks) of a clinging squirrel's centre from the axis of a trunk whose bark is {@code half} from it. */
	public static double clingDistance(double half) {
		return half + HALF_WIDTH + BARK_GAP;
	}

	/** The x and z of a squirrel clinging to the trunk of the column (bx, bz) on the cardinal side {@code side}. */
	public static double[] clingXZ(int bx, int bz, Direction side, double half) {
		double d = clingDistance(half);
		return new double[] {bx + 0.5 + side.getStepX() * d, bz + 0.5 + side.getStepZ() * d};
	}

	/** Minecraft yaw (degrees) of an entity facing the horizontal direction (dx, dz); yaw 0 faces +z (south). */
	public static float yawFacing(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** Yaw of a squirrel climbing up (or clinging to) a trunk on the side: it faces the trunk. */
	public static float climbYaw(Direction side) {
		return yawFacing(-side.getStepX(), -side.getStepZ());
	}

	/** Yaw of a squirrel going down a trunk on the side: head first, it faces away from the trunk (the bark behind it). */
	public static float descendYaw(Direction side) {
		return yawFacing(side.getStepX(), side.getStepZ());
	}

	/** The cardinal side of a trunk at (bx, bz) that looks towards the point (x, z) (the nearest of the four). */
	public static Direction sideTowards(int bx, int bz, double x, double z) {
		double dx = x - (bx + 0.5);
		double dz = z - (bz + 0.5);
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** Whether a leap of the horizontal length and height difference (landing minus take-off) is one a squirrel makes. */
	public static boolean leapable(double horizontal, double dy) {
		return horizontal >= MIN_LEAP && horizontal <= MAX_LEAP && dy >= -MAX_LEAP_DROP && dy <= MAX_LEAP_RISE;
	}

	/** Ticks of a leap of the horizontal length. */
	public static int leapTicks(double horizontal) {
		return Math.max(MIN_LEAP_TICKS, (int) Math.ceil(horizontal / LEAP_SPEED));
	}

	/** Height (blocks) of the top of a leap's arc above the straight line: higher for a longer or a rising leap. */
	public static double arcHeight(double horizontal, double dy) {
		return 0.4 + 0.15 * horizontal + Math.max(0.0, dy);
	}

	/** The point of a leap from a to b at the share t (0-1) of its time: the straight line plus a parabola of the height. */
	public static Vec3 arc(Vec3 a, Vec3 b, double t, double height) {
		double y = a.y + (b.y - a.y) * t + 4.0 * height * t * (1.0 - t);
		return new Vec3(a.x + (b.x - a.x) * t, y, a.z + (b.z - a.z) * t);
	}

	/** Horizontal distance of two points. */
	public static double horizontal(Vec3 a, Vec3 b) {
		double dx = b.x - a.x;
		double dz = b.z - a.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
