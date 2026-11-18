package pl.polishforests.entity.squirrel;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.util.BrainUtil;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.squirrel.TreeSpace.Trunk;

/**
 * The red squirrel's own movement in the trees (step FA5, docs/04 "Projekt FA5"): the <b>tree mode</b>. Vanilla's
 * physics and the ground navigation know no climbing (a ladder moves only up and down inside its block), so in the tree mode
 * the squirrel has {@code noPhysics} on, {@link RedSquirrel#travel} does nothing (no gravity, no walk control), and this
 * class sets its position every server tick along a queue of legs, each checked against the collision of every block
 * except the leaves ({@link TreeSpace#clear}) when it is planned and again in every tick:
 *
 * <ul>
 * <li>{@link Kind#RUN}: a straight run (to the foot of a trunk from the ground, around a trunk to another side, onto a
 * lying log);
 * <li>{@link Kind#CLIMB} and {@link Kind#DESCEND}: up or down the trunk on one of its four sides at the clinging distance
 * ({@link TreeGeometry#clingXZ}; the distance follows the widest bark the hitbox spans, {@link TreeSpace#halfOver}), facing the trunk going
 * up and away from it going down head first; a descent ends on the ground where the next step would hit it;
 * <li>{@link Kind#LEAP}: a parabola from one trunk to another 1-4 blocks away ({@link TreeGeometry#arc}), through the
 * leaves of the crowns;
 * <li>{@link Kind#CLING}: holding still on the trunk (eating a cone, resting, watching).
 * </ul>
 *
 * A leg whose box is no longer clear (a block placed, a branch the plan missed) ends the queue: the squirrel clings where it
 * is if its trunk is still there, otherwise it drops ({@link #drop}) and the vanilla physics takes over (a squirrel takes no
 * damage from up to {@value RedSquirrel#SAFE_FALL} blocks). The tree mode ends only where the hitbox is
 * {@linkplain TreeSpace#free free} ({@link #freeNear}: on top of the leaves of a bush when the descent ends in one, or a
 * little aside); deep in a crown with no free place near, the squirrel keeps clinging to its trunk instead of letting go
 * inside the leaves (stage 2 of FA5, FA5-p). A descent that a branch blocks on every side ends in a jump down
 * ({@link #jumpDown}) to a free place on the ground. Nothing of the tree mode is saved: a squirrel loaded in the air falls
 * and catches the nearest trunk ({@link #tryCatch}); one loaded inside the leaves of a crown takes the trunk next to it or
 * climbs out ({@link #rescue}).
 */
public final class TreeMotion {
	/** The kind of the current leg, synchronized to the client for the animation (new kinds only at the end). */
	public enum Kind {
		GROUND, RUN, CLIMB, DESCEND, LEAP, CLING
	}

	/** Climbing speed (blocks per tick) of a calm squirrel and of a fleeing one. */
	public static final double CLIMB_SPEED = 0.12;
	public static final double FLEE_CLIMB_SPEED = 0.24;
	/** Running speed on the bark and on the ground (blocks per tick). */
	static final double RUN_SPEED = 0.18;
	/** Step (blocks) of the boxes checked along a planned leg. */
	private static final double CHECK_STEP = 0.2;
	/** Ticks between two checks that the trunk of a clinging squirrel is still there. */
	private static final int HOLD_CHECK = 20;
	/** Farthest (blocks) from a clinging place at which a stopped squirrel still holds on to the bark. */
	private static final double AT_BARK = 0.75;
	/** Highest hop (blocks) onto a trunk from the ground, and the farthest (blocks, horizontal) to its clinging place. */
	private static final double ATTACH_LIFT = 1.6;
	public static final double ATTACH_REACH = 2.0;
	/** Farthest (blocks) from the bark a jump down looks for its place on the ground. */
	private static final double JUMP_OUT = 1.5;

	/** Counters for the census of the fauna game test. */
	public static final LongAdder CLIMBS = new LongAdder();
	public static final LongAdder CLIMB_TICKS = new LongAdder();
	public static final LongAdder LEAPS = new LongAdder();
	public static final LongAdder GAP_LEAPS = new LongAdder();
	public static final LongAdder DROPS = new LongAdder();
	public static final LongAdder ABORTS = new LongAdder();
	public static final LongAdder LOG_RUNS = new LongAdder();
	public static final LongAdder JUMPS_DOWN = new LongAdder();
	public static final LongAdder GRABS = new LongAdder();
	public static final LongAdder RESCUES = new LongAdder();
	/** Every counter of the class. */
	private static final LongAdder[] COUNTERS = {CLIMBS, CLIMB_TICKS, LEAPS, GAP_LEAPS, DROPS, ABORTS, LOG_RUNS, JUMPS_DOWN, GRABS, RESCUES};

	/** One leg of the queue: for CLIMB and DESCEND only {@code to.y} counts (the x and z follow the trunk). */
	private record Leg(Kind kind, Vec3 to, int ticks, double arc, @Nullable Trunk trunk, Direction side, boolean ground) {
	}

	private final RedSquirrel squirrel;
	private final ArrayDeque<Leg> legs = new ArrayDeque<>();
	private Kind kind = Kind.GROUND;
	private @Nullable Trunk trunk;
	private Direction side = Direction.NORTH;
	private @Nullable Leg leg;
	private Vec3 legFrom = Vec3.ZERO;
	private int legTick;
	private double speed = CLIMB_SPEED;

	TreeMotion(RedSquirrel squirrel) {
		this.squirrel = squirrel;
	}

	public static void resetCounters() {
		for (LongAdder a : COUNTERS) {
			a.reset();
		}
	}

	/** Whether the squirrel is in the tree mode. */
	public boolean active() {
		return kind != Kind.GROUND;
	}

	public Kind kind() {
		return kind;
	}

	/** The trunk it clings to, or null on the ground or in a leap. */
	public @Nullable Trunk trunk() {
		return trunk;
	}

	public Direction side() {
		return side;
	}

	/** Whether it moves along legs (not clinging still). */
	public boolean busy() {
		return leg != null || !legs.isEmpty();
	}

	/** Height (blocks) of the feet above the base of its trunk (0 without a trunk). */
	public double heightOnTrunk() {
		return trunk == null ? 0 : squirrel.getY() - trunk.baseY();
	}

	// ------------------------------------------------------------------ planning

	private Level level() {
		return squirrel.level();
	}

	private AABB boxAt(Vec3 feet) {
		return TreeSpace.box(feet, squirrel.getBbWidth(), squirrel.getBbHeight());
	}

	/**
	 * The feet of a squirrel clinging to the trunk on the side at the height y: off the widest bark its hitbox spans
	 * ({@link TreeSpace#halfOver}; until the review of FA5 the radius of the block at feet + 0.2, which put the box into the
	 * wider core below a step where the trunk narrows).
	 */
	Vec3 clingPoint(Trunk t, Direction s, double y) {
		double[] xz = TreeGeometry.clingXZ(t.x(), t.z(), s, TreeSpace.halfOver(level(), t, y, y + squirrel.getBbHeight()));
		return new Vec3(xz[0], y, xz[1]);
	}

	/** Whether every box along the straight line from a to b (steps of {@value #CHECK_STEP} blocks) is clear. */
	private boolean lineClear(Vec3 a, Vec3 b) {
		int n = Math.max(1, (int) Math.ceil(a.distanceTo(b) / CHECK_STEP));
		for (int i = 1; i <= n; i++) {
			if (!TreeSpace.clear(level(), boxAt(a.lerp(b, i / (double) n)))) {
				return false;
			}
		}
		return true;
	}

	private boolean arcClear(Vec3 a, Vec3 b, double arc, int ticks) {
		for (int i = 1; i <= ticks; i++) {
			if (!TreeSpace.clear(level(), boxAt(TreeGeometry.arc(a, b, i / (double) ticks, arc)))) {
				return false;
			}
		}
		return true;
	}

	private static int runTicks(Vec3 a, Vec3 b, double speed) {
		return Math.max(1, (int) Math.ceil(a.distanceTo(b) / speed));
	}

	/**
	 * From the ground: runs (or hops) to the foot of the trunk on the side, where its feet are now or up to
	 * {@value #ATTACH_LIFT} blocks higher (above the root flares of an old tree, stage 2 of FA5), and enters the tree mode
	 * clinging there. Returns false (nothing changes) when the place or the way is not clear.
	 */
	public boolean attach(Trunk t, Direction s) {
		Vec3 here = squirrel.position();
		for (double lift = 0; lift <= ATTACH_LIFT + 1.0E-6; lift += 0.2) {
			Vec3 at = clingPoint(t, s, here.y + lift);
			if (TreeGeometry.horizontal(here, at) > ATTACH_REACH || !TreeSpace.clear(level(), boxAt(at))) {
				continue;
			}
			// the way checked is the way taken: straight up (a hop) first, then across to the bark
			Vec3 up = new Vec3(here.x, at.y, here.z);
			if (lift > 0 && !lineClear(here, up) || !lineClear(up, at)) {
				continue;
			}
			enter();
			trunk = t;
			side = s;
			if (lift > 0) {
				legs.add(new Leg(Kind.RUN, up, runTicks(here, up, RUN_SPEED), 0, t, s, false));
			}
			legs.add(new Leg(Kind.RUN, at, runTicks(up, at, RUN_SPEED), 0, t, s, false));
			return true;
		}
		return false;
	}

	/**
	 * Up the trunk to the height {@code toY} of the feet (clamped to the top of the trunk), as far as the boxes are clear;
	 * returns the height it will reach (its feet now when it cannot climb at all). Queued after the legs already planned.
	 */
	public double climb(double toY, double climbSpeed) {
		Trunk t = trunk;
		if (t == null) {
			return squirrel.getY();
		}
		double startY = legs.isEmpty() ? squirrel.getY() : legs.peekLast().to().y;
		double top = Math.min(toY, t.topY() + 1.0 - squirrel.getBbHeight() * 0.5);
		double reach = startY;
		for (double y = startY + CHECK_STEP; y <= top + 1.0E-6; y += CHECK_STEP) {
			if (!TreeSpace.clear(level(), boxAt(clingPoint(t, side, y)))) {
				break;
			}
			reach = y;
		}
		if (reach > startY + 0.5) {
			speed = climbSpeed;
			legs.add(new Leg(Kind.CLIMB, new Vec3(0, reach, 0), 0, 0, t, side, false));
			CLIMBS.increment();
		}
		return reach;
	}

	/** Head first down the trunk to the ground (or onto a bush there); false when even the first step is blocked. */
	public boolean descend(double climbSpeed) {
		Trunk t = trunk;
		if (t == null || busy()) {
			return false;
		}
		if (!TreeSpace.clear(level(), boxAt(clingPoint(t, side, squirrel.getY() - CHECK_STEP)))) {
			return false;
		}
		speed = climbSpeed;
		legs.add(new Leg(Kind.DESCEND, new Vec3(0, t.baseY() - 4.0, 0), 0, 0, t, side, true));
		return true;
	}

	/**
	 * Around the trunk to another side at the same height: two runs through the point by the corner between the sides (or
	 * straight across to the opposite side, through both corners). False when a box on the way is not clear.
	 */
	public boolean switchSide(Direction to) {
		Trunk t = trunk;
		if (t == null || busy() || to == side || !to.getAxis().isHorizontal()) {
			return false;
		}
		double y = squirrel.getY();
		Vec3 here = squirrel.position();
		Direction via = to == side.getOpposite() ? side.getClockWise() : null;
		java.util.List<Vec3> points = new java.util.ArrayList<>();
		Direction from = side;
		for (Direction next : via == null ? new Direction[] {to} : new Direction[] {via, to}) {
			Vec3 a = clingPoint(t, from, y);
			Vec3 b = clingPoint(t, next, y);
			// the corner point: the clinging distances of both sides, so the box keeps off the bark there too
			points.add(new Vec3(from.getStepX() != 0 ? a.x : b.x, y, from.getStepZ() != 0 ? a.z : b.z));
			points.add(b);
			from = next;
		}
		Vec3 prev = here;
		for (Vec3 p : points) {
			if (!TreeSpace.clear(level(), boxAt(p)) || !lineClear(prev, p)) {
				return false;
			}
			prev = p;
		}
		prev = here;
		for (int i = 0; i < points.size(); i++) {
			Vec3 p = points.get(i);
			Direction s = i < points.size() - 1 ? side : to;
			legs.add(new Leg(Kind.RUN, p, runTicks(prev, p, RUN_SPEED), 0, t, i == points.size() - 1 ? to : s, false));
			prev = p;
		}
		return true;
	}

	/**
	 * A leap from the trunk it clings to onto the side of {@code to} that faces it, at the nearest height of that trunk
	 * within the drop and the rise of a leap; first around its own trunk to the side that faces the target when it is
	 * elsewhere. False when the leap is out of reach or not clear.
	 */
	public boolean leap(Trunk to) {
		Trunk t = trunk;
		if (t == null || busy() || to.equals(t)) {
			return false;
		}
		Direction out = TreeGeometry.sideTowards(t.x(), t.z(), to.x() + 0.5, to.z() + 0.5);
		Direction in = TreeGeometry.sideTowards(to.x(), to.z(), t.x() + 0.5, t.z() + 0.5);
		double y = squirrel.getY();
		Vec3 start = clingPoint(t, out, y);
		double landY = Mth.clamp(y, to.baseY() + 1.0, to.topY() + 1.0 - squirrel.getBbHeight() * 0.5);
		Vec3 land = clingPoint(to, in, landY);
		double h = TreeGeometry.horizontal(start, land);
		if (!TreeGeometry.leapable(h, land.y - start.y) || !TreeSpace.clear(level(), boxAt(land))) {
			return false;
		}
		int ticks = TreeGeometry.leapTicks(h);
		double arc = TreeGeometry.arcHeight(h, land.y - start.y);
		if (!arcClear(start, land, arc, ticks)) {
			return false;
		}
		if (out != side && !switchSide(out)) {
			return false;
		}
		legs.add(new Leg(Kind.LEAP, land, ticks, arc, to, in, false));
		LEAPS.increment();
		if (gap(start, land, arc)) {
			GAP_LEAPS.increment();
		}
		return true;
	}

	/** A leap over open air: no leaf and no log within a block under the top of the arc. */
	private boolean gap(Vec3 a, Vec3 b, double arc) {
		Vec3 mid = TreeGeometry.arc(a, b, 0.5, arc);
		for (int dy = 0; dy >= -2; dy--) {
			var state = level().getBlockState(net.minecraft.core.BlockPos.containing(mid.x, mid.y + dy, mid.z));
			if (state.is(net.minecraft.tags.BlockTags.LEAVES) || TreeSpace.climbable(state)) {
				return false;
			}
		}
		return true;
	}

	/** From the ground: a hop onto the top of a lying log (its feet there), after which the vanilla physics takes over. */
	public boolean hopOnto(Vec3 top) {
		Vec3 here = squirrel.position();
		double h = TreeGeometry.horizontal(here, top);
		if (h > 1.8 || top.y - here.y > 1.2 || !TreeSpace.free(level(), boxAt(top))) {
			return false;
		}
		int ticks = Math.max(4, (int) Math.ceil(h / RUN_SPEED));
		double arc = 0.3 + Math.max(0, top.y - here.y) * 0.5;
		if (!arcClear(here, top, arc, ticks)) {
			return false;
		}
		enter();
		trunk = null;
		legs.add(new Leg(Kind.LEAP, top, ticks, arc, null, side, true));
		LOG_RUNS.increment();
		return true;
	}

	/**
	 * Down from the trunk when the descent is blocked on every side (a branch under it): a jump down, out from the bark
	 * (up to {@value #JUMP_OUT} blocks) to a free place on the ground at most {@code SAFE_FALL - 1} blocks below, along an
	 * arc clear of every block but the leaves; the vanilla physics takes over there. False when no such place is found.
	 */
	public boolean jumpDown() {
		Trunk t = trunk;
		if (t == null || busy()) {
			return false;
		}
		Vec3 here = squirrel.position();
		double half = TreeSpace.halfOver(level(), t, here.y, here.y + squirrel.getBbHeight());
		for (Direction s : new Direction[] {side, side.getClockWise(), side.getCounterClockWise(), side.getOpposite()}) {
			for (double out = 0.5; out <= JUMP_OUT + 1.0E-6; out += 0.5) {
				Vec3 from = new Vec3(t.x() + 0.5 + s.getStepX() * (half + out), here.y, t.z() + 0.5 + s.getStepZ() * (half + out));
				Vec3 land = groundBelow(from, RedSquirrel.SAFE_FALL - 1);
				if (land == null) {
					continue;
				}
				int ticks = Math.max(4, (int) Math.ceil(here.distanceTo(land) / 0.45));
				if (!arcClear(here, land, 0.3, ticks)) {
					continue;
				}
				legs.add(new Leg(Kind.LEAP, land, ticks, 0.3, null, side, true));
				JUMPS_DOWN.increment();
				return true;
			}
		}
		return false;
	}

	/**
	 * The feet on the first collision under the point that is not leaves (the ground, a log), on a free place there (or up to
	 * 2 blocks higher, on a bush), at most {@code maxFall} blocks down; null when there is none.
	 */
	private @Nullable Vec3 groundBelow(Vec3 from, double maxFall) {
		if (!TreeSpace.clear(level(), boxAt(from))) {
			return null;
		}
		double y = from.y;
		double bottom = from.y - maxFall;
		while (y - 0.25 >= bottom && TreeSpace.clear(level(), boxAt(new Vec3(from.x, y - 0.25, from.z)))) {
			y -= 0.25;
		}
		if (y - 0.25 < bottom) {
			return null;
		}
		// down to 1/16 block above the collision
		while (TreeSpace.clear(level(), boxAt(new Vec3(from.x, y - 1.0 / 16, from.z)))) {
			y -= 1.0 / 16;
		}
		return freeNear(new Vec3(from.x, y, from.z), false);
	}

	/**
	 * A free place for the hitbox near the feet ({@link TreeSpace#free}: no leaves either): the point itself, up to 2 blocks
	 * higher (on top of the leaves of a bush) and, when {@code aside}, up to 1.5 blocks aside at up to 1 block higher; null
	 * when there is none (deep in a crown).
	 */
	@Nullable Vec3 freeNear(Vec3 at, boolean aside) {
		for (double lift = 0; lift <= 2.0 + 1.0E-6; lift += 1.0 / 16) {
			Vec3 up = at.add(0, lift, 0);
			if (TreeSpace.free(level(), boxAt(up))) {
				return up;
			}
		}
		if (aside) {
			for (double r = 0.5; r <= 1.5 + 1.0E-6; r += 0.5) {
				for (int k = 0; k < 8; k++) {
					double a = Math.PI / 4 * k;
					for (double lift = 0; lift <= 1.0 + 1.0E-6; lift += 0.25) {
						Vec3 p = at.add(Math.cos(a) * r, lift, Math.sin(a) * r);
						if (TreeSpace.free(level(), boxAt(p))) {
							return p;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Takes a trunk next to it at the height of its feet: an upright trunk within {@code radius} blocks (not the one it
	 * clings to) whose clinging place, the nearest of the four sides, is clear and reached by a clear run; the tree mode
	 * starts with that run. For a squirrel on top of a crown, inside the leaves of one after a load, or whose clinging place
	 * a growing tree took. False when there is none.
	 */
	public boolean grab(int radius) {
		Vec3 here = squirrel.position();
		int bx = Mth.floor(here.x);
		int by = Mth.floor(here.y + 0.2);
		int bz = Mth.floor(here.z);
		Trunk best = null;
		Direction bestSide = Direction.NORTH;
		Vec3 bestAt = null;
		double bestD = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy : new int[] {0, -1, 1}) {
					pos.set(bx + dx, by + dy, bz + dz);
					if (!level().hasChunkAt(pos) || !TreeSpace.upright(level().getBlockState(pos))) {
						continue;
					}
					Trunk t = TreeSpace.trunkAt(level(), pos);
					if (t == null || t.height() < TreeSpace.MIN_TRUNK || t.equals(trunk)) {
						break;
					}
					double y = Mth.clamp(here.y, t.baseY(), t.topY() + 1.0 - squirrel.getBbHeight() * 0.5);
					for (Direction s : Direction.Plane.HORIZONTAL) {
						Vec3 at = clingPoint(t, s, y);
						double d = at.distanceTo(here);
						if (d < bestD && d <= radius + 0.8 && TreeSpace.clear(level(), boxAt(at)) && (lineClear(here, at)
								|| !TreeSpace.clear(level(), boxAt(here)))) {
							best = t;
							bestSide = s;
							bestAt = at;
							bestD = d;
						}
					}
					break;
				}
			}
		}
		if (best == null) {
			return false;
		}
		boolean inside = !TreeSpace.clear(level(), boxAt(here));
		enter();
		trunk = best;
		side = bestSide;
		if (inside) {
			// inside a block (a rescue): straight onto the clinging place, a run from in there would be stopped at once
			squirrel.setPos(bestAt);
			face(TreeGeometry.climbYaw(bestSide));
		} else {
			legs.add(new Leg(Kind.RUN, bestAt, runTicks(here, bestAt, RUN_SPEED), 0, best, bestSide, false));
		}
		GRABS.increment();
		return true;
	}

	/**
	 * On the ground mode with the hitbox not free (inside the leaves of a crown after a load or a fall, a block placed into
	 * it): the trunk next to it ({@link #grab}), else a free place near ({@link #freeNear}). True when it moved.
	 */
	public boolean rescue() {
		if (active() || TreeSpace.free(level(), boxAt(squirrel.position()))) {
			return false;
		}
		RESCUES.increment();
		if (grab(2)) {
			return true;
		}
		Vec3 p = freeNear(squirrel.position(), true);
		if (p != null) {
			squirrel.setPos(p);
			squirrel.setDeltaMovement(Vec3.ZERO);
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ mode changes

	private void enter() {
		if (kind == Kind.GROUND) {
			squirrel.getNavigation().stop();
			BrainUtil.clearMemory(squirrel, MemoryModuleType.WALK_TARGET);
			squirrel.setDeltaMovement(Vec3.ZERO);
			squirrel.noPhysics = true;
			setKind(Kind.CLING);
		}
		legs.clear();
		leg = null;
	}

	/**
	 * Ends the tree mode at a free place near ({@link #freeNear}: on top of the leaves of a bush when its box is in one, or a
	 * little aside). With no free place near it keeps clinging to its trunk when the trunk is there (the mind tries again
	 * later); only without a trunk it lets go where it is ({@link #rescue} takes over on the ground).
	 */
	private void land() {
		Vec3 p = freeNear(squirrel.position(), true);
		if (p != null) {
			squirrel.setPos(p);
			exit();
			return;
		}
		Trunk t = trunk;
		if (t != null && TreeSpace.holds(level(), t, squirrel.getY() + 0.2) && TreeSpace.clear(level(), boxAt(squirrel.position()))) {
			legs.clear();
			leg = null;
			setKind(Kind.CLING);
			return;
		}
		exit();
	}

	private void exit() {
		legs.clear();
		leg = null;
		trunk = null;
		squirrel.noPhysics = false;
		squirrel.setDeltaMovement(Vec3.ZERO);
		squirrel.resetFallDistance();
		setKind(Kind.GROUND);
	}

	/**
	 * Lets go: the tree mode ends at a free place near and the squirrel falls (the vanilla physics); deep in a crown it
	 * keeps clinging instead ({@link #land}).
	 */
	public void drop() {
		if (active()) {
			DROPS.increment();
			land();
		}
	}

	/**
	 * Stops the legs still to come; the squirrel clings where it is when it is at the bark, else it takes a trunk next to
	 * it or drops (a leap or a run onto a trunk stopped in the air).
	 */
	public void halt() {
		legs.clear();
		leg = null;
		if (active()) {
			if (trunk != null && TreeSpace.holds(level(), trunk, squirrel.getY() + 0.2) && nearBark(trunk)) {
				setKind(Kind.CLING);
			} else if (!grab(2)) {
				drop();
			}
		}
	}

	/**
	 * Whether the squirrel is at the bark of the trunk: within {@value #AT_BARK} blocks of the clinging place of one of its
	 * sides (a run onto the trunk stopped on its way from the ground is not).
	 */
	private boolean nearBark(Trunk t) {
		Vec3 here = squirrel.position();
		for (Direction s : Direction.Plane.HORIZONTAL) {
			if (TreeGeometry.horizontal(here, clingPoint(t, s, here.y)) <= AT_BARK) {
				return true;
			}
		}
		return false;
	}

	private void setKind(Kind k) {
		kind = k;
		squirrel.syncMotion(k);
	}

	/**
	 * A falling squirrel on the ground mode (more than 1.5 blocks of fall) grabs an upright trunk whose clinging place
	 * on the side towards it is within 0.8 blocks and clear: the tree mode starts there, clinging.
	 */
	public boolean tryCatch() {
		if (active() || squirrel.onGround() || squirrel.fallDistance < 1.5) {
			return false;
		}
		Vec3 here = squirrel.position();
		int bx = Mth.floor(here.x);
		int by = Mth.floor(here.y + 0.2);
		int bz = Mth.floor(here.z);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				var pos = new net.minecraft.core.BlockPos(bx + dx, by, bz + dz);
				if (!TreeSpace.upright(level().getBlockState(pos))) {
					continue;
				}
				Trunk t = TreeSpace.trunkAt(level(), pos);
				if (t == null) {
					continue;
				}
				Direction s = TreeGeometry.sideTowards(t.x(), t.z(), here.x, here.z);
				Vec3 at = clingPoint(t, s, here.y);
				if (TreeGeometry.horizontal(here, at) <= 0.8 && TreeSpace.clear(level(), boxAt(at))) {
					enter();
					trunk = t;
					side = s;
					squirrel.setPos(at);
					squirrel.resetFallDistance();
					face(TreeGeometry.climbYaw(s));
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ tick

	/** One server tick of the tree mode (nothing on the ground). */
	void tick() {
		if (!active()) {
			return;
		}
		squirrel.resetFallDistance();
		squirrel.setOnGround(false);
		if (leg == null) {
			leg = legs.poll();
			legFrom = squirrel.position();
			legTick = 0;
			if (leg != null) {
				if (leg.trunk() != null || leg.kind() == Kind.LEAP) {
					trunk = leg.trunk();
				}
				setKind(leg.kind());
			}
		}
		Leg l = leg;
		if (l == null) {
			if (kind != Kind.CLING) {
				setKind(Kind.CLING);
			}
			hold();
			return;
		}
		Vec3 before = squirrel.position();
		Vec3 next;
		boolean done;
		switch (l.kind()) {
			case CLIMB, DESCEND -> {
				Trunk t = l.trunk();
				boolean up = l.kind() == Kind.CLIMB;
				double y = before.y + (up ? speed : -speed);
				done = up ? y >= l.to().y : y <= l.to().y;
				if (done) {
					y = l.to().y;
				}
				next = clingPoint(t, side, y);
				if (up) {
					CLIMB_TICKS.increment();
				}
				face(up ? TreeGeometry.climbYaw(side) : TreeGeometry.descendYaw(side));
			}
			default -> {
				legTick++;
				double t = Math.min(1.0, legTick / (double) Math.max(1, l.ticks()));
				next = l.kind() == Kind.LEAP ? TreeGeometry.arc(legFrom, l.to(), t, l.arc()) : legFrom.lerp(l.to(), t);
				done = legTick >= l.ticks();
				if (next.distanceToSqr(before) > 1.0E-6) {
					face(TreeGeometry.yawFacing(next.x - before.x, next.z - before.z));
				}
			}
		}
		if (!TreeSpace.clear(level(), boxAt(next))) {
			if (l.kind() == Kind.DESCEND && (l.trunk() == null || before.y <= l.trunk().baseY() + 1.6)) {
				// the ground (or a root, a stone) under the next step: the descent ends here
				land();
				return;
			}
			// higher up a branch of the trunk (or of another tree) blocks the way: it clings there, and the mind tries
			// another side
			ABORTS.increment();
			if (l.kind() == Kind.LEAP && l.ground()) {
				land();
				return;
			}
			halt();
			return;
		}
		squirrel.setPos(next);
		squirrel.setDeltaMovement(next.subtract(before));
		if (done) {
			leg = null;
			if (l.trunk() != null) {
				side = l.side();
			}
			if (l.ground()) {
				land();
			} else if (legs.isEmpty()) {
				setKind(Kind.CLING);
				face(TreeGeometry.climbYaw(side));
			}
		}
	}

	/**
	 * Clinging still: every {@value #HOLD_CHECK} ticks a check that the trunk is still there and the place clear, else it
	 * takes another trunk next to it ({@link #grab}) or lets go from a free place near.
	 */
	private void hold() {
		squirrel.setDeltaMovement(Vec3.ZERO);
		if ((squirrel.tickCount + squirrel.getId()) % HOLD_CHECK == 0) {
			Trunk t = trunk;
			if (t == null || !TreeSpace.holds(level(), t, squirrel.getY() + 0.2) || !TreeSpace.clear(level(), boxAt(squirrel.position()))) {
				if (!grab(2)) {
					DROPS.increment();
					Vec3 p = freeNear(squirrel.position(), true);
					if (p != null) {
						squirrel.setPos(p);
					}
					exit();
				}
			}
		}
	}

	private void face(float yaw) {
		squirrel.setYRot(yaw);
		squirrel.setYBodyRot(yaw);
		squirrel.setYHeadRot(yaw);
	}
}
