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

	private TreeGeometry() {
	}

	/** Distance (blocks) from the axis to the bark of a trunk of the mod of the radius (px). */
	public static double trunkHalf(int radiusPx) {
		return radiusPx / 16.0;
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
