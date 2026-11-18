package pl.polishforests.entity.squirrel;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.util.BrainUtil;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.base.AnimalProfile.Activity;
import pl.polishforests.entity.base.AnimalProfile.Period;
import pl.polishforests.entity.base.PolishAnimal.Action;
import pl.polishforests.entity.base.Thicket;
import pl.polishforests.entity.spawn.FaunaHabitat;
import pl.polishforests.entity.squirrel.TreeSpace.Trunk;

/**
 * What the red squirrel does (step FA5, docs/04 "Projekt FA5"): its own small state machine, ticked by the entity on the
 * server, in place of the behaviours of a SmartBrainLib brain (whose only behaviour left is the walk to a target on the
 * ground). It plans the legs of {@link TreeMotion} in the trees and sets the walk target of the ground navigation.
 *
 * <ul>
 * <li><b>Bouts</b> of feeding, resting and moving drawn from the shares of the period of its schedule
 * ({@link RedSquirrel#schedule}: the summer day, the short winter day), {@value #BOUT_MIN}-{@value #BOUT_MAX} ticks each.
 * Feeding is on the ground ({@value #GROUND_FEED} of the bouts; it hops a few blocks between feeding stops and runs onto a
 * lying log when one is near) or in the crown (a cone, holding on to the trunk); resting is high on a trunk in the crown
 * (the drey); moving is a series of leaps between the trunks of the crowns, or a run on the ground to another tree within
 * {@value #TREE_RADIUS} blocks and up it. A squirrel on the ground at a rest bout goes up a tree.
 * <li><b>Flight</b>: a player within its flight distance, an alarm heard at the flight intensity, or a hurt. On the ground
 * it runs to the best trunk within {@value #FLEE_RADIUS} blocks (the nearest, those towards the threat last) and climbs
 * at least {@value #SAFE_HEIGHT} blocks up; without a trunk it runs away on the ground. In a tree with the threat less than
 * {@value #SAFE_HEIGHT} blocks below it climbs higher, else goes round to the side of the trunk away from the threat, else
 * leaps into the next crown. A squirrel high enough watches (alert).
 * </ul>
 */
public final class SquirrelMind {
	static final int BOUT_MIN = 200;
	static final int BOUT_MAX = 700;
	static final float GROUND_FEED = 0.6F;
	static final int TREE_RADIUS = 12;
	static final int FLEE_RADIUS = 12;
	/** Height (blocks) above the threat (or above the base of the trunk) at which a fleeing squirrel feels safe. */
	static final double SAFE_HEIGHT = 4.0;
	/** Ticks a run to a trunk may take. */
	private static final int TO_TREE_TICKS = 240;
	private static final int THINK = 10;

	/** Counters for the census of the fauna game test. */
	public static final LongAdder FLIGHTS = new LongAdder();
	public static final LongAdder FLIGHTS_TO_TREE = new LongAdder();
	/** Ticks from the start of a flight on the ground to {@value #SAFE_HEIGHT} blocks up a trunk, summed over those flights. */
	public static final LongAdder FLIGHT_CLIMB_TICKS = new LongAdder();
	public static final LongAdder GROUND_FLIGHTS = new LongAdder();

	enum Goal {
		IDLE, FORAGE, TO_TREE, TREE, RUN_AWAY
	}

	private final RedSquirrel s;
	private Goal goal = Goal.IDLE;
	private Activity bout = Activity.FEED;
	private long boutEnd;
	private int period = -1;
	private boolean fleeing;
	private long flightStart = -1;
	private @Nullable Vec3 threat;
	private long threatUntil;
	private @Nullable Trunk target;
	private Direction targetSide = Direction.NORTH;
	private long goalUntil;
	private long nextStop;
	private @Nullable Vec3 logTop;
	private int stuckTries;
	private boolean climbed;

	SquirrelMind(RedSquirrel squirrel) {
		this.s = squirrel;
	}

	public static void resetCounters() {
		for (LongAdder a : new LongAdder[] {FLIGHTS, FLIGHTS_TO_TREE, FLIGHT_CLIMB_TICKS, GROUND_FLIGHTS}) {
			a.reset();
		}
	}

	Goal goal() {
		return goal;
	}

	/** Hurt by something at {@code from} (or nowhere in particular): a flight from there. */
	void hurt(@Nullable Vec3 from) {
		threat = from != null ? from : s.position().add(s.getRandom().nextGaussian(), 0, s.getRandom().nextGaussian());
		threatUntil = s.level().getGameTime() + 60;
	}

	// ------------------------------------------------------------------ tick

	void tick(ServerLevel level) {
		long now = level.getGameTime();
		TreeMotion m = s.motion();
		if (m.active() && goal != Goal.TREE) {
			goal = Goal.TREE;
		} else if (!m.active() && goal == Goal.TREE) {
			goal = Goal.IDLE;
			if (s.action() != Action.NONE) {
				s.setAction(Action.NONE);
			}
		}
		if (fleeing && flightStart >= 0 && m.active() && m.heightOnTrunk() >= SAFE_HEIGHT) {
			FLIGHTS_TO_TREE.increment();
			FLIGHT_CLIMB_TICKS.add(now - flightStart);
			flightStart = -1;
		}
		if (Math.floorMod(now + s.getId(), THINK) != 0) {
			if (goal == Goal.TO_TREE) {
				approach(now);
			}
			return;
		}
		Vec3 danger = danger(now);
		if (danger != null) {
			flee(level, danger, now);
			if (goal != Goal.IDLE) {
				return;
			}
		} else if (fleeing && !s.isFleeing()) {
			fleeing = false;
			flightStart = -1;
		}
		switch (goal) {
			case IDLE -> decide(level, now);
			case FORAGE -> forage(level, now);
			case TO_TREE -> approach(now);
			case TREE -> tree(level, now);
			case RUN_AWAY -> {
				if (now > goalUntil || !s.getNavigation().isInProgress()) {
					goal = Goal.IDLE;
				}
			}
		}
	}

	/** The source of a danger now: a player within its flight distance, an alarm it flees from, or a recent hurt. */
	private @Nullable Vec3 danger(long now) {
		Player p = s.nearestThreat();
		if (p != null) {
			return p.position();
		}
		Vec3 alarm = s.alarmSource();
		if (alarm != null) {
			s.clearAlarm();
			threat = alarm;
			threatUntil = now + 60;
		}
		return now < threatUntil ? threat : null;
	}

	// ------------------------------------------------------------------ flight

	private void flee(ServerLevel level, Vec3 from, long now) {
		TreeMotion m = s.motion();
		if (m.active()) {
			Trunk t = m.trunk();
			if (m.busy() || t == null) {
				return;
			}
			double above = s.getY() - from.y;
			if (above >= SAFE_HEIGHT && m.heightOnTrunk() >= SAFE_HEIGHT) {
				s.setAction(Action.ALERT);
				return;
			}
			s.markFleeing(60, false);
			s.setAction(Action.NONE);
			if (m.climb(Math.max(s.getY() + SAFE_HEIGHT, from.y + SAFE_HEIGHT + 1), TreeMotion.FLEE_CLIMB_SPEED) > s.getY() + 0.5) {
				return;
			}
			Direction away = TreeGeometry.sideTowards(t.x(), t.z(), 2 * (t.x() + 0.5) - from.x, 2 * (t.z() + 0.5) - from.z);
			if (away != m.side() && m.switchSide(away)) {
				return;
			}
			leapAway(level, t, from);
			return;
		}
		if (goal == Goal.TO_TREE && fleeing) {
			return;
		}
		if (!fleeing) {
			FLIGHTS.increment();
			flightStart = now;
		}
		fleeing = true;
		s.markFleeing(100, false);
		s.setAction(Action.NONE);
		List<Trunk> trunks = TreeSpace.trunksNear(level, s.getBlockX(), s.getBlockY(), s.getBlockZ(), FLEE_RADIUS, 6);
		Trunk best = null;
		double bestScore = Double.MAX_VALUE;
		Vec3 here = s.position();
		for (Trunk t : trunks) {
			Vec3 axis = t.axis(here.y);
			double d = TreeGeometry.horizontal(here, axis);
			// towards the threat (the trunk on the threat's side of the squirrel) only when nothing else is near
			double toward = (axis.x - here.x) * (from.x - here.x) + (axis.z - here.z) * (from.z - here.z);
			double score = d + (toward > 0 && TreeGeometry.horizontal(axis, from) < TreeGeometry.horizontal(here, from) ? 8 : 0);
			if (score < bestScore) {
				best = t;
				bestScore = score;
			}
		}
		if (best != null) {
			goTo(best, now, true);
			return;
		}
		// No trunk near: away on the ground, as the other animals.
		Vec3 away = LandRandomPos.getPosAway(s, 12, 5, from);
		if (away != null) {
			GROUND_FLIGHTS.increment();
			BrainUtil.setMemory(s, MemoryModuleType.WALK_TARGET, new WalkTarget(away, 2.0F, 0));
			goal = Goal.RUN_AWAY;
			goalUntil = now + 80;
		}
	}

	private void leapAway(ServerLevel level, Trunk t, Vec3 from) {
		List<Trunk> targets = TreeSpace.leapTargets(level, t, s.getY(), 8);
		Trunk best = null;
		double bestDist = -1;
		for (Trunk o : targets) {
			double d = TreeGeometry.horizontal(o.axis(0), new Vec3(from.x, 0, from.z));
			if (d > bestDist) {
				best = o;
				bestDist = d;
			}
		}
		if (best != null) {
			s.motion().leap(best);
		}
	}

	// ------------------------------------------------------------------ bouts

	private Period period(long time) {
		List<Period> schedule = RedSquirrel.schedule(s.subSeason());
		int t = (int) Math.floorMod(time, 24_000L);
		int index = schedule.size() - 1;
		for (int i = 0; i < schedule.size(); i++) {
			if (schedule.get(i).fromTick() <= t) {
				index = i;
			}
		}
		period = index;
		return schedule.get(index);
	}

	private int periodIndex(long time) {
		List<Period> schedule = RedSquirrel.schedule(s.subSeason());
		int t = (int) Math.floorMod(time, 24_000L);
		int index = schedule.size() - 1;
		for (int i = 0; i < schedule.size(); i++) {
			if (schedule.get(i).fromTick() <= t) {
				index = i;
			}
		}
		return index;
	}

	/** A new bout from the period of the schedule. */
	private void newBout(long now) {
		RandomSource r = s.getRandom();
		bout = period(s.timeOfDay()).pick(r.nextFloat());
		boutEnd = now + Mth.nextInt(r, BOUT_MIN, BOUT_MAX);
		s.setBout(bout);
		climbed = false;
		stuckTries = 0;
	}

	private boolean boutOver(long now) {
		return now >= boutEnd || periodIndex(s.timeOfDay()) != period;
	}

	/** On the ground without a goal: a new bout. */
	private void decide(ServerLevel level, long now) {
		newBout(now);
		RandomSource r = s.getRandom();
		switch (bout) {
			case FEED -> {
				if (r.nextFloat() < GROUND_FEED) {
					startForage(now);
				} else {
					toTree(level, now);
				}
			}
			case REST, MOVE -> toTree(level, now);
		}
	}

	private void startForage(long now) {
		goal = Goal.FORAGE;
		nextStop = now;
		logTop = null;
	}

	/** Feeding on the ground: stops of feeding, hops between them, a lying log when one is near. */
	private void forage(ServerLevel level, long now) {
		if (boutOver(now)) {
			s.setAction(Action.NONE);
			// after the feeding, up a tree (to rest, to the next bout)
			toTree(level, now);
			return;
		}
		Vec3 top = logTop;
		if (top != null) {
			if (TreeGeometry.horizontal(s.position(), top) <= 1.6) {
				logTop = null;
				if (s.motion().hopOnto(top)) {
					nextStop = now + 20;
				}
			} else if (!s.getNavigation().isInProgress()) {
				logTop = null;
			}
			return;
		}
		if (now < nextStop || s.getNavigation().isInProgress()) {
			return;
		}
		RandomSource r = s.getRandom();
		if (s.action() == Action.EAT && r.nextFloat() < 0.5F) {
			// move on to the next feeding stop: a lying log near, or a few blocks on the ground
			s.setAction(Action.NONE);
			Vec3 log = r.nextFloat() < 0.5F ? TreeSpace.lyingLogNear(level, s.blockPosition(), 5, s.getBbWidth(), s.getBbHeight(), r) : null;
			if (log != null) {
				logTop = log;
				BrainUtil.setMemory(s, MemoryModuleType.WALK_TARGET, new WalkTarget(log, 1.3F, 1));
				return;
			}
			Vec3 spot = groundSpot(level, s.position(), 5, r);
			if (spot != null) {
				BrainUtil.setMemory(s, MemoryModuleType.WALK_TARGET, new WalkTarget(spot, 1.3F, 0));
			}
			nextStop = now + 40;
			return;
		}
		s.setCone(RedSquirrel.coneForest(FaunaHabitat.surface(level, s.getBlockX(), s.getBlockZ())));
		s.setAction(Action.EAT);
		nextStop = now + Mth.nextInt(r, 40, 140);
	}

	/** A place to stand on the ground within the radius, near its home (8 tries), or null. */
	private @Nullable Vec3 groundSpot(ServerLevel level, Vec3 base, int radius, RandomSource r) {
		Vec3 home = s.home();
		for (int i = 0; i < 8; i++) {
			int x = Mth.floor(base.x) + r.nextInt(2 * radius + 1) - radius;
			int z = Mth.floor(base.z) + r.nextInt(2 * radius + 1) - radius;
			if (!level.hasChunkAt(new BlockPos(x, Mth.floor(base.y), z))) {
				continue;
			}
			BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (Math.abs(feet.getY() - base.y) > 4 || !Thicket.standable(level, feet)) {
				continue;
			}
			Vec3 p = Vec3.atBottomCenterOf(feet);
			if (TreeGeometry.horizontal(p, home) <= s.profile().homeRange() || TreeGeometry.horizontal(p, home) < TreeGeometry.horizontal(base, home)) {
				return p;
			}
		}
		return null;
	}

	/** Runs to a trunk near (the nearest few, back towards home beyond the home range) to climb it. */
	private void toTree(ServerLevel level, long now) {
		List<Trunk> trunks = TreeSpace.trunksNear(level, s.getBlockX(), s.getBlockY(), s.getBlockZ(), TREE_RADIUS, 6);
		if (trunks.isEmpty()) {
			// no tree near: feed on the ground (the next bout looks again)
			startForage(now);
			return;
		}
		Vec3 home = s.home();
		boolean far = TreeGeometry.horizontal(s.position(), home) > s.profile().homeRange();
		Trunk pick = trunks.get(s.getRandom().nextInt(trunks.size()));
		if (far) {
			for (Trunk t : trunks) {
				if (TreeGeometry.horizontal(t.axis(0), new Vec3(home.x, 0, home.z)) < TreeGeometry.horizontal(pick.axis(0), new Vec3(home.x, 0,
						home.z))) {
					pick = t;
				}
			}
		}
		goTo(pick, now, false);
	}

	private void goTo(Trunk t, long now, boolean flight) {
		target = t;
		targetSide = TreeGeometry.sideTowards(t.x(), t.z(), s.getX(), s.getZ());
		goal = Goal.TO_TREE;
		goalUntil = now + TO_TREE_TICKS;
		s.setAction(Action.NONE);
		walkTo(t, flight);
	}

	private void walkTo(Trunk t, boolean flight) {
		Vec3 at = s.motion().clingPoint(t, targetSide, t.baseY());
		BrainUtil.setMemory(s, MemoryModuleType.WALK_TARGET, new WalkTarget(at, flight ? 2.2F : 1.2F, 0));
	}

	/** On the way to the trunk: close enough, it takes the trunk (the side towards it, else another) and climbs. */
	private void approach(long now) {
		Trunk t = target;
		if (t == null || now > goalUntil) {
			target = null;
			goal = Goal.IDLE;
			return;
		}
		TreeMotion m = s.motion();
		Vec3 foot = m.clingPoint(t, targetSide, s.getY());
		if (TreeGeometry.horizontal(s.position(), foot) > 1.4) {
			if (!s.getNavigation().isInProgress() && Math.floorMod(now + s.getId(), 20L) == 0) {
				walkTo(t, fleeing);
			}
			return;
		}
		Direction first = TreeGeometry.sideTowards(t.x(), t.z(), s.getX(), s.getZ());
		Direction[] sides = {first, first.getClockWise(), first.getCounterClockWise(), first.getOpposite()};
		for (Direction side : sides) {
			if (m.attach(t, side)) {
				double toY = fleeing ? t.baseY() + SAFE_HEIGHT + 1.5 + s.getRandom().nextInt(3) : crownHeight(t);
				m.climb(toY, fleeing ? TreeMotion.FLEE_CLIMB_SPEED : TreeMotion.CLIMB_SPEED);
				goal = Goal.TREE;
				target = null;
				climbed = true;
				return;
			}
		}
		target = null;
		goal = Goal.IDLE;
	}

	/** The height of the feet in the crown of a trunk: 1.5-3 blocks below its top, at least 3 above its base. */
	private double crownHeight(Trunk t) {
		double top = t.topY() + 1.0 - 1.5 - s.getRandom().nextDouble() * 1.5;
		return Math.max(t.baseY() + 3.0, top);
	}

	// ------------------------------------------------------------------ in the tree

	private void tree(ServerLevel level, long now) {
		TreeMotion m = s.motion();
		if (m.busy() || s.isFleeing()) {
			return;
		}
		Trunk t = m.trunk();
		if (t == null) {
			return;
		}
		if (boutOver(now)) {
			newBout(now);
		}
		RandomSource r = s.getRandom();
		switch (bout) {
			case REST -> {
				if (!climbed) {
					climbed = true;
					s.setAction(Action.NONE);
					if (m.climb(crownHeight(t), TreeMotion.CLIMB_SPEED) > s.getY() + 0.5) {
						return;
					}
				}
				if (s.action() != Action.REST) {
					s.setAction(Action.REST);
				}
			}
			case FEED -> {
				boolean cone = RedSquirrel.coneForest(FaunaHabitat.surface(level, s.getBlockX(), s.getBlockZ()));
				if (!cone && s.action() != Action.EAT || r.nextFloat() < 0.02F) {
					down(m);
					return;
				}
				if (!climbed) {
					climbed = true;
					if (m.climb(crownHeight(t), TreeMotion.CLIMB_SPEED) > s.getY() + 0.5) {
						return;
					}
				}
				s.setCone(true);
				if (s.action() != Action.EAT) {
					s.setAction(Action.EAT);
				}
			}
			case MOVE -> {
				s.setAction(Action.NONE);
				if (r.nextFloat() < 0.3F) {
					// along the crowns: a leap to the next trunk, or up the trunk first
					if (m.heightOnTrunk() < 3 && m.climb(crownHeight(t), TreeMotion.CLIMB_SPEED) > s.getY() + 0.5) {
						return;
					}
					List<Trunk> targets = TreeSpace.leapTargets(level, t, s.getY(), 8);
					if (!targets.isEmpty() && m.leap(targets.get(r.nextInt(targets.size())))) {
						return;
					}
					if (++stuckTries > 4) {
						down(m);
					}
				}
			}
		}
	}

	/** Down the trunk to the ground: head first, from another side when a branch blocks the way, a drop when low enough. */
	private void down(TreeMotion m) {
		s.setAction(Action.NONE);
		if (m.descend(TreeMotion.CLIMB_SPEED)) {
			return;
		}
		Direction side = m.side();
		for (Direction other : new Direction[] {side.getClockWise(), side.getCounterClockWise(), side.getOpposite()}) {
			if (m.switchSide(other)) {
				return;
			}
		}
		if (m.heightOnTrunk() < RedSquirrel.SAFE_FALL - 2) {
			m.drop();
		}
	}
}
