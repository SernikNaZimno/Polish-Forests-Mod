package pl.polishforests.entity.jay;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The flight of the Eurasian jay under the crowns (step FA6, docs/04 "Projekt FA6"): its own flight mode in place of a
 * vanilla flying navigation (which plans in whole blocks, does not know the thin trunks of the mod and the canopy, and
 * costs a path search per target).
 *
 * <p><b>Planning</b> ({@link #flyTo}): a route of one or two straight legs to the target. The direct leg first; when it
 * does not fit, up to {@value #MID_TRIES} waypoints in the cruise band ({@link JaySpace#cruiseHeight}) around the middle
 * of the way. A leg fits when the hitbox is free at every sweep point ({@link JaySpace#segmentClear}: no block, the leaves
 * included, so the jay flies round the crowns) and at most {@link JayGeometry#OUT_OF_BAND_SHARE} of its band points lie
 * outside the cruise band (under the canopy top; a short crossing of a gap). A target the jay cannot reach so is left.
 *
 * <p><b>Flying</b> ({@link #tick}): the vanilla movement with collisions moves the jay ({@link MoverType#SELF}) by a
 * velocity straight at the next waypoint ({@link JayGeometry#steer}: the speed changes by at most {@value #ACCEL} per
 * tick); no gravity in the flight (the entity's {@code travel} skips vanilla's), the fall distance reset. It lands at the
 * end of the route: on a perch (the top of a branch or a leaf block) or on the ground, where the vanilla physics takes
 * over again. A flight that makes no progress for {@value #STALL_TICKS} ticks (a collision the plan did not see: a block
 * placed, an entity) ends where it is and the jay drops to what is under it (a fall does not hurt it).
 *
 * <p>Counters for the census of the fauna game test (flights, landings on branches, on leaves and on the ground, routes not
 * found, stalled flights, ticks in the air).
 */
public final class JayFlight {
	/** Cruise and flight speeds (blocks per tick; a jay flies at about 5-8 m/s). */
	public static final double CRUISE_SPEED = 0.30;
	public static final double FLEE_SPEED = 0.42;
	/** Most change of the speed per tick (blocks per tick). */
	static final double ACCEL = 0.05;
	/** Waypoints tried around the middle of a blocked direct way. */
	static final int MID_TRIES = 6;
	/** Horizontal spread (blocks) of those waypoints. */
	static final double MID_SPREAD = 4.0;
	/** Distance (blocks) at which a waypoint counts as reached. */
	static final double REACHED = 0.15;
	/** Ticks without progress after which a flight ends. */
	static final int STALL_TICKS = 30;
	/** Longest flight before it ends wherever it is (ticks). */
	static final int MAX_TICKS = 400;
	/** Drop per horizontal block from which a leg is a glide. */
	static final double GLIDE_SLOPE = 0.25;

	public static final LongAdder FLIGHTS = new LongAdder();
	public static final LongAdder FLIGHT_TICKS = new LongAdder();
	public static final LongAdder LANDED_BRANCH = new LongAdder();
	public static final LongAdder LANDED_LEAVES = new LongAdder();
	public static final LongAdder LANDED_GROUND = new LongAdder();
	public static final LongAdder NO_ROUTE = new LongAdder();
	public static final LongAdder STALLED = new LongAdder();
	public static final LongAdder TWO_LEGS = new LongAdder();

	/** The kind of the motion (synchronized to the client for the animation). New kinds only at the end. */
	public enum Kind {
		GROUND, FLY, GLIDE
	}

	/** Where a route ends. */
	public enum Landing {
		GROUND, BRANCH, LEAVES, AIR
	}

	private final EurasianJay jay;
	private final List<Vec3> route = new ArrayList<>(2);
	private Landing landing = Landing.GROUND;
	private double speed;
	private double targetSpeed = CRUISE_SPEED;
	private long started;
	private double bestDist = Double.MAX_VALUE;
	private int stall;
	private @Nullable Vec3 end;

	JayFlight(EurasianJay jay) {
		this.jay = jay;
	}

	public static void resetCounters() {
		for (LongAdder a : new LongAdder[] {FLIGHTS, FLIGHT_TICKS, LANDED_BRANCH, LANDED_LEAVES, LANDED_GROUND, NO_ROUTE, STALLED, TWO_LEGS}) {
			a.reset();
		}
	}

	/** Whether it is in the air on a route. */
	public boolean active() {
		return !route.isEmpty();
	}

	/** Where the current route ends, or null on the ground. */
	public @Nullable Vec3 end() {
		return active() ? end : null;
	}

	public Landing landing() {
		return landing;
	}

	/**
	 * Plans a flight to {@code target} (the feet there) and starts it; returns false (nothing changes) when no route fits.
	 * {@code landing} says what it lands on there.
	 */
	public boolean flyTo(Vec3 target, Landing landing, double speed, RandomSource random) {
		Level level = jay.level();
		Vec3 from = jay.position();
		float w = jay.getBbWidth();
		float h = jay.getBbHeight();
		if (!JaySpace.free(level, target, w, h)) {
			NO_ROUTE.increment();
			return false;
		}
		List<Vec3> legs = plan(level, from, target, w, h, random);
		if (legs == null) {
			NO_ROUTE.increment();
			return false;
		}
		route.clear();
		route.addAll(legs);
		if (legs.size() > 1) {
			TWO_LEGS.increment();
		}
		this.landing = landing;
		this.targetSpeed = speed;
		this.end = target;
		this.speed = Math.max(speed * 0.5, jay.getDeltaMovement().length());
		started = level.getGameTime();
		bestDist = Double.MAX_VALUE;
		stall = 0;
		FLIGHTS.increment();
		jay.getNavigation().stop();
		jay.syncFlight(Kind.FLY);
		return true;
	}

	/** The legs of a route from {@code from} to {@code to}, or null (see the class comment). */
	static @Nullable List<Vec3> plan(Level level, Vec3 from, Vec3 to, float w, float h, RandomSource random) {
		if (fits(level, from, to, w, h)) {
			return List.of(to);
		}
		Vec3 mid = from.lerp(to, 0.5);
		for (int i = 0; i < MID_TRIES; i++) {
			double x = mid.x + (random.nextDouble() * 2 - 1) * MID_SPREAD;
			double z = mid.z + (random.nextDouble() * 2 - 1) * MID_SPREAD;
			double y = JaySpace.cruiseHeight(level, x, z, h, random);
			if (Double.isNaN(y)) {
				continue;
			}
			Vec3 p = new Vec3(x, y, z);
			if (JaySpace.free(level, p, w, h) && fits(level, from, p, w, h) && fits(level, p, to, w, h)) {
				return List.of(p, to);
			}
		}
		return null;
	}

	private static boolean fits(Level level, Vec3 a, Vec3 b, float w, float h) {
		return JaySpace.outOfBand(level, a, b, h) <= JayGeometry.OUT_OF_BAND_SHARE && JaySpace.segmentClear(level, a, b, w, h);
	}

	/** Ends the flight where the jay is (it drops to what is under it). */
	public void stop() {
		route.clear();
		end = null;
		speed = 0;
		jay.syncFlight(Kind.GROUND);
	}

	/** A server tick of the flight (nothing on the ground). */
	void tick() {
		if (route.isEmpty()) {
			return;
		}
		Level level = jay.level();
		long now = level.getGameTime();
		FLIGHT_TICKS.increment();
		jay.resetFallDistance();
		Vec3 here = jay.position();
		Vec3 wp = route.getFirst();
		double dist = here.distanceTo(wp);
		if (dist <= REACHED) {
			route.removeFirst();
			if (route.isEmpty()) {
				land(wp);
				return;
			}
			wp = route.getFirst();
			bestDist = Double.MAX_VALUE;
			dist = here.distanceTo(wp);
		}
		if (dist < bestDist - 0.02) {
			bestDist = dist;
			stall = 0;
		} else if (++stall > STALL_TICKS || now - started > MAX_TICKS) {
			STALLED.increment();
			stop();
			return;
		}
		Vec3 v = JayGeometry.steer(here, wp, speed, targetSpeed, ACCEL);
		speed = v.length();
		jay.setDeltaMovement(v);
		if (v.horizontalDistanceSqr() > 1.0E-6) {
			float yaw = JayGeometry.yawOf(v.x, v.z);
			float turned = Mth.approachDegrees(jay.getYRot(), yaw, 30.0F);
			jay.setYRot(turned);
			jay.setYBodyRot(turned);
			jay.setYHeadRot(turned);
		}
		jay.syncFlight(JayGeometry.glides(v, GLIDE_SLOPE) && speed > CRUISE_SPEED * 0.6 ? Kind.GLIDE : Kind.FLY);
	}

	/** The move of one tick in the air: the velocity of the steering, with the collisions of the vanilla movement. */
	void move() {
		jay.move(MoverType.SELF, jay.getDeltaMovement());
	}

	private void land(Vec3 at) {
		Level level = jay.level();
		if (JaySpace.free(level, at, jay.getBbWidth(), jay.getBbHeight())) {
			jay.setPos(at.x, at.y + 0.01, at.z);
		}
		jay.setDeltaMovement(Vec3.ZERO);
		switch (landing) {
			case BRANCH -> LANDED_BRANCH.increment();
			case LEAVES -> LANDED_LEAVES.increment();
			case GROUND -> LANDED_GROUND.increment();
			case AIR -> {
			}
		}
		route.clear();
		end = null;
		speed = 0;
		jay.syncFlight(Kind.GROUND);
		jay.mind().landed(landing);
	}
}
