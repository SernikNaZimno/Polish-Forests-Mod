package pl.polishforests.entity.jay;

import net.minecraft.world.phys.Vec3;

/**
 * Pure geometry of the flight of the Eurasian jay (step FA6, docs/04-pionowy-wycinek.md "Projekt FA6"): the band of
 * heights it flies in under the canopy, the points a path is checked at and the steering, without a level, so the unit
 * tests check them ({@code JayGeometryTest}).
 *
 * <p><b>The canopy top</b> of a column is the top of vanilla's {@code MOTION_BLOCKING} heightmap (the leaves count), the
 * ground the top of {@code MOTION_BLOCKING_NO_LEAVES}. The <b>local canopy top</b> is the highest canopy top of the
 * columns around a point ({@link #LOCAL_RADIUS}, the crowns next to a flight path or a gap): what the fauna game test
 * measures a flight against (a sample is "under the canopy" when the top of the jay's hitbox is below it). The jay plans
 * its flights in the <b>cruise band</b> ({@link #cruiseLow}, {@link #cruiseHigh}): from {@value #CLEARANCE} blocks above the
 * ground to {@value #UNDER_CANOPY} blocks under the local canopy top (the top of its hitbox below the crowns), at most
 * {@value #CRUISE_ABOVE_GROUND} blocks above the ground (jays fly through the middle storeys of the forest); a column
 * without a canopy of at least {@value #MIN_CANOPY} blocks over the ground has no band.
 *
 * <p>Stage 2 of FA6 (choice FA6-q): a planned flight is checked against the <b>canopy ceiling</b> only
 * ({@link #plannedUnder}: the top of the hitbox at least {@value #CEILING_MARGIN} blocks under the local canopy top, at
 * points {@value #PLAN_STEP} block apart), not against the lower edge of the band: the take-off from the ground and the
 * landing on it lie below the band by nature, and the collisions are checked on their own. At most
 * {@value #ABOVE_SHARE} of the points of a leg may lie over the ceiling (a short crossing of a gap). The band stays the
 * choice of the heights of the waypoints ({@link #cruiseLow}, {@link #cruiseHigh}).
 */
public final class JayGeometry {
	/** Radius (blocks) of the columns of the local canopy top, and the spacing of the columns read. */
	public static final int LOCAL_RADIUS = 4;
	public static final int LOCAL_STEP = 2;
	/** Least height of the feet above the ground in a flight path (blocks). */
	public static final double CLEARANCE = 1.5;
	/** Least gap from the top of the hitbox to the local canopy top in a planned flight (blocks). */
	public static final double UNDER_CANOPY = 0.8;
	/** Highest cruise above the ground (blocks). */
	public static final double CRUISE_ABOVE_GROUND = 12.0;
	/** Least height of the local canopy over the ground for a band at all (blocks): a forest, not a gap or a field. */
	public static final double MIN_CANOPY = 4.0;
	/** Spacing (blocks) of the points a path segment is checked at for collisions. */
	public static final double SWEEP_STEP = 0.25;
	/** Spacing (blocks) of the points a path segment is checked at against the cruise band. */
	public static final double BAND_STEP = 2.0;
	/** Least gap from the top of the hitbox to the local canopy top at the points of a planned flight (blocks). */
	public static final double CEILING_MARGIN = 0.3;
	/** Spacing (blocks) of the points a planned leg is checked at against the canopy ceiling. */
	public static final double PLAN_STEP = 1.0;
	/** The share of the points of a planned leg that may lie over the canopy ceiling (a short crossing of a gap). */
	public static final double ABOVE_SHARE = 0.1;

	private JayGeometry() {
	}

	/** The lowest feet height of the cruise band over a column (NaN when the column has no band). */
	public static double cruiseLow(double ground, double localCanopy, float height) {
		return hasBand(ground, localCanopy, height) ? ground + CLEARANCE : Double.NaN;
	}

	/** The highest feet height of the cruise band over a column (NaN when the column has no band). */
	public static double cruiseHigh(double ground, double localCanopy, float height) {
		if (!hasBand(ground, localCanopy, height)) {
			return Double.NaN;
		}
		return Math.min(localCanopy - UNDER_CANOPY - height, ground + CRUISE_ABOVE_GROUND);
	}

	/** Whether a column has a cruise band: a canopy high enough to fly under with the clearance above the ground. */
	public static boolean hasBand(double ground, double localCanopy, float height) {
		return localCanopy - ground >= MIN_CANOPY && localCanopy - UNDER_CANOPY - height >= ground + CLEARANCE;
	}

	/** Whether feet at {@code y} are in the band of a column (NaN-safe: false without a band). */
	public static boolean inBand(double y, double ground, double localCanopy, float height) {
		return hasBand(ground, localCanopy, height) && y >= cruiseLow(ground, localCanopy, height) - 1.0E-6
				&& y <= cruiseHigh(ground, localCanopy, height) + 1.0E-6;
	}

	/** Whether a flight sample is under the canopy: the top of the hitbox below the local canopy top. */
	public static boolean underCanopy(double feetY, float height, double localCanopy) {
		return feetY + height <= localCanopy;
	}

	/** Whether a point of a planned flight lies under the canopy ceiling: the top of the hitbox {@value #CEILING_MARGIN} under it. */
	public static boolean plannedUnder(double feetY, float height, double localCanopy) {
		return feetY + height + CEILING_MARGIN <= localCanopy + 1.0E-6;
	}

	/** The number of collision checks of a segment ({@value #SWEEP_STEP} blocks apart, both ends included). */
	public static int sweepPoints(double length) {
		return Math.max(1, (int) Math.ceil(length / SWEEP_STEP)) + 1;
	}

	/** The point {@code k} of {@code n} sweep points of the segment from {@code a} to {@code b}. */
	public static Vec3 sweepPoint(Vec3 a, Vec3 b, int k, int n) {
		double t = n <= 1 ? 1.0 : k / (double) (n - 1);
		return a.lerp(b, t);
	}

	/**
	 * The velocity of one tick of the flight towards a waypoint: straight at it at {@code speed} (blocks per tick), less
	 * when it is closer than one tick; the speed changes from {@code current} by at most {@code accel} per tick.
	 */
	public static Vec3 steer(Vec3 from, Vec3 to, double currentSpeed, double speed, double accel) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 1.0E-6) {
			return Vec3.ZERO;
		}
		double s = Math.max(currentSpeed - accel, Math.min(currentSpeed + accel, speed));
		s = Math.min(Math.max(0.05, s), len);
		return d.scale(s / len);
	}

	/** Whether the flight glides on a leg: a descent steeper than {@code glideSlope} (drop per horizontal block). */
	public static boolean glides(Vec3 velocity, double glideSlope) {
		double h = velocity.horizontalDistance();
		return velocity.y < 0 && h > 1.0E-6 && -velocity.y / h >= glideSlope;
	}

	/** The yaw (Minecraft degrees, 0 = south +Z, 90 = west) of a horizontal direction. */
	public static float yawOf(double dx, double dz) {
		return (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
	}
}
