package pl.polishforests.entity.jay;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.util.BrainUtil;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.base.AnimalProfile.Activity;
import pl.polishforests.entity.base.ForestAlarm;
import pl.polishforests.entity.base.PolishAnimal.Action;
import pl.polishforests.entity.jay.JayFlight.Landing;
import pl.polishforests.entity.spawn.FaunaHabitat;
import pl.polishforests.entity.spawn.FaunaSpawner;
import pl.polishforests.season.SubSeason;
import pl.polishforests.sound.ModSounds;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * What the Eurasian jay does (step FA6, docs/04 "Projekt FA6"): its own small state machine, ticked by the entity on the
 * server every {@value #THINK} ticks, in place of the behaviours of a SmartBrainLib brain (whose only behaviour left is the
 * walk to a target on the ground, the hops).
 *
 * <ul>
 * <li><b>Bouts</b> of feeding, resting and moving drawn from the shares of the period of its schedule
 * ({@link EurasianJay#PROFILE}), {@value #BOUT_MIN}-{@value #BOUT_MAX} ticks each. Feeding is on the ground
 * ({@value #GROUND_FEED} of the bouts: it flies down, hops between feeding stops and pecks) or on a perch in the crowns
 * (gleaning, the same pecking pose); resting is on a perch (at night the roost); moving is a flight to another perch
 * {@value #MOVE_MIN}-{@value #MOVE_MAX} blocks away (back towards its home when it is beyond its home range).
 * <li><b>Hiding acorns</b> in the autumn (early to late autumn) in the oak forests ({@link #acornForest}): {@value #CACHE_SHARE}
 * of the feeding bouts there pick up an acorn on the ground (it shows in the bill), fly
 * {@value #CACHE_MIN}-{@value #CACHE_MAX} blocks to another spot on the ground and hide it there (the pecking pose,
 * {@value #CACHE_TICKS} ticks); no block changes. Counted ({@link #CACHES}).
 * <li><b>The alarm</b>: a running player it sees (sprinting, visible, within {@value #ALARM_RANGE} blocks and in its line
 * of sight; {@link ForestAlarm#runningThreat}) makes it screech (the sound of {@code tools/sound}, FA4) and raise a
 * {@link ForestAlarm.Kind#JAY_BIRD} alarm of radius {@value #ALARM_RADIUS} that the roe deer and the wild boars hear, at
 * most once in {@value #SCREECH_COOLDOWN}-{@value #SCREECH_COOLDOWN_MAX} ticks, not when it roosts at night.
 * <li><b>Flight</b>: a player within its flight distance, an alarm heard at the flight intensity (not the running player's
 * own alarm: the jay uses its eyes), or a hurt make it fly to a perch {@value #FLEE_MIN}-{@value #FLEE_MAX} blocks away on
 * the far side from the threat (else a spot on the ground there), then watch (alert).
 * </ul>
 */
public final class JayMind {
	static final int THINK = 10;
	static final int BOUT_MIN = 200;
	static final int BOUT_MAX = 700;
	static final float GROUND_FEED = 0.55F;
	static final float CACHE_SHARE = 0.6F;
	static final int MOVE_MIN = 8;
	static final int MOVE_MAX = 24;
	static final int PERCH_MIN = 3;
	static final int PERCH_MAX = 14;
	static final int CACHE_MIN = 8;
	static final int CACHE_MAX = 24;
	static final int CACHE_TICKS = 60;
	static final int PICK_TICKS = 40;
	public static final int ALARM_RANGE = 24;
	public static final double ALARM_RADIUS = 32;
	public static final float ALARM_STRENGTH = 1.0F;
	public static final int SCREECH_COOLDOWN = 200;
	public static final int SCREECH_COOLDOWN_MAX = 400;
	static final int FLEE_MIN = 10;
	static final int FLEE_MAX = 22;
	/** Ticks it watches (alert) after a flight or a screech. */
	static final int WATCH_TICKS = 60;
	/** Columns tried for a perch per search. */
	static final int PERCH_TRIES = 10;

	public static final LongAdder SCREECHES = new LongAdder();
	public static final LongAdder SCREECH_HEARD = new LongAdder();
	public static final LongAdder CACHES = new LongAdder();
	public static final LongAdder FLIGHTS_FROM_THREAT = new LongAdder();
	public static final LongAdder GROUND_FEEDS = new LongAdder();
	public static final LongAdder NO_PERCH = new LongAdder();

	enum Goal {
		IDLE, FORAGE, PICK, CARRY, HIDE, PERCHED, FLYING, WATCH
	}

	private final EurasianJay j;
	private Goal goal = Goal.IDLE;
	private Activity bout = Activity.MOVE;
	private long boutEnd;
	private long goalUntil;
	private long nextStop;
	private long nextScreech;
	private boolean perched;
	private boolean fleeing;
	private @Nullable Vec3 threat;
	private long threatUntil;
	/** A flight down to the ground at the start of hiding an acorn: it picks the acorn up where it lands. */
	private boolean pickAfterLanding;

	JayMind(EurasianJay jay) {
		this.j = jay;
	}

	public static void resetCounters() {
		for (LongAdder a : new LongAdder[] {SCREECHES, SCREECH_HEARD, CACHES, FLIGHTS_FROM_THREAT, GROUND_FEEDS, NO_PERCH}) {
			a.reset();
		}
	}

	/** Whether it sits on a perch (a branch or a crown) after a landing there. */
	public boolean perched() {
		return perched && !j.flight().active();
	}

	/** Its goal, for the fauna game test (numbers only). */
	public String describe() {
		return goal + " (" + bout + (fleeing ? ", fleeing" : "") + (perched ? ", perched" : "") + ")";
	}

	void hurt(@Nullable Vec3 from) {
		threat = from != null ? from : j.position().add(j.getRandom().nextGaussian(), 0, j.getRandom().nextGaussian());
		threatUntil = j.level().getGameTime() + 60;
	}

	/** Called by the flight at the end of a route. */
	void landed(Landing landing) {
		perched = landing == Landing.BRANCH || landing == Landing.LEAVES;
		long now = j.level().getGameTime();
		if (pickAfterLanding) {
			pickAfterLanding = false;
			if (!perched && goal == Goal.FLYING && !fleeing) {
				pick(now);
				return;
			}
		}
		switch (goal) {
			case CARRY -> {
				goal = Goal.HIDE;
				goalUntil = now + CACHE_TICKS;
				j.setAction(Action.EAT);
			}
			case FLYING -> {
				if (fleeing) {
					goal = Goal.WATCH;
					goalUntil = now + WATCH_TICKS;
					j.setAction(Action.ALERT);
				} else if (perched) {
					goal = Goal.PERCHED;
					j.setAction(bout == Activity.REST ? Action.REST : bout == Activity.FEED ? Action.EAT : Action.NONE);
				} else {
					goal = bout == Activity.FEED ? Goal.FORAGE : Goal.IDLE;
					nextStop = now;
				}
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ tick

	void tick(ServerLevel level) {
		long now = level.getGameTime();
		JayFlight f = j.flight();
		if (f.active()) {
			perched = false;
		}
		if (Math.floorMod(now + j.getId(), THINK) != 0) {
			return;
		}
		watchForRunners(level, now);
		Vec3 danger = danger(now);
		if (danger != null) {
			flee(level, danger, now);
			return;
		}
		if (fleeing && !j.isFleeing()) {
			fleeing = false;
		}
		if (f.active()) {
			return;
		}
		if (j.isInWater()) {
			toPerch(level, now, PERCH_MIN, PERCH_MAX);
			return;
		}
		switch (goal) {
			case IDLE, FLYING -> decide(level, now);
			case FORAGE -> forage(level, now);
			case PICK -> {
				if (now >= goalUntil) {
					carry(level, now);
				}
			}
			case CARRY -> goal = Goal.IDLE;
			case HIDE -> {
				if (now >= goalUntil) {
					CACHES.increment();
					j.setCarrying(false);
					j.setAction(Action.NONE);
					goal = Goal.IDLE;
				}
			}
			case PERCHED -> {
				if (boutOver(now) || !j.onGround() && !perched) {
					j.setAction(Action.NONE);
					goal = Goal.IDLE;
				}
			}
			case WATCH -> {
				if (now >= goalUntil) {
					j.setAction(Action.NONE);
					goal = perched ? Goal.PERCHED : Goal.IDLE;
				}
			}
		}
	}

	// ------------------------------------------------------------------ the alarm

	/** A running player it sees: the screech and the alarm (see the class comment). */
	private void watchForRunners(ServerLevel level, long now) {
		if (now < nextScreech || bout == Activity.REST && j.profile().period(j.timeOfDay()).main() == Activity.REST) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (ForestAlarm.runningThreat(p) && p.distanceTo(j) <= ALARM_RANGE && j.hasLineOfSight(p)) {
				screech(level, now);
				return;
			}
		}
	}

	/** The screech: the sound and a {@link ForestAlarm.Kind#JAY_BIRD} alarm; returns how many animals heard it. */
	public int screech(ServerLevel level, long now) {
		RandomSource r = j.getRandom();
		nextScreech = now + SCREECH_COOLDOWN + r.nextInt(SCREECH_COOLDOWN_MAX - SCREECH_COOLDOWN + 1);
		level.playSound(null, j.getX(), j.getEyeY(), j.getZ(), ModSounds.JAY, SoundSource.NEUTRAL, 3.0F, 0.94F + r.nextFloat() * 0.12F);
		SCREECHES.increment();
		int heard = ForestAlarm.raise(level, new ForestAlarm(j.getEyePosition(), ALARM_RADIUS, ALARM_STRENGTH, ForestAlarm.Kind.JAY_BIRD, j));
		SCREECH_HEARD.add(heard);
		if (!j.flight().active() && goal != Goal.WATCH) {
			j.setAction(Action.ALERT);
			goal = Goal.WATCH;
			goalUntil = now + WATCH_TICKS;
		}
		return heard;
	}

	/** The source of a danger now: a player within its flight distance, an alarm it flees from, or a recent hurt. */
	private @Nullable Vec3 danger(long now) {
		Player p = j.nearestThreat();
		if (p != null) {
			return p.position();
		}
		Vec3 alarm = j.alarmSource();
		if (alarm != null) {
			j.clearAlarm();
			threat = alarm;
			threatUntil = now + 60;
		}
		return now < threatUntil ? threat : null;
	}

	private void flee(ServerLevel level, Vec3 from, long now) {
		JayFlight f = j.flight();
		if (f.active() && fleeing) {
			return;
		}
		if (!fleeing) {
			FLIGHTS_FROM_THREAT.increment();
		}
		fleeing = true;
		j.markFleeing(80, false);
		j.setAction(Action.NONE);
		if (j.isCarrying()) {
			j.setCarrying(false);
		}
		RandomSource r = j.getRandom();
		List<JaySpace.Perch> perches = JaySpace.perchesNear(level, j.position(), FLEE_MIN, FLEE_MAX, PERCH_TRIES, 3, j.getBbWidth(),
				j.getBbHeight(), r, from);
		for (JaySpace.Perch p : perches) {
			if (f.flyTo(p.feet(), p.branch() ? Landing.BRANCH : Landing.LEAVES, JayFlight.FLEE_SPEED, r)) {
				goal = Goal.FLYING;
				return;
			}
		}
		Vec3 spot = groundSpot(level, from, FLEE_MIN, FLEE_MAX, r);
		if (spot != null && f.flyTo(spot, Landing.GROUND, JayFlight.FLEE_SPEED, r)) {
			goal = Goal.FLYING;
			return;
		}
		NO_PERCH.increment();
		j.setAction(Action.ALERT);
		goal = Goal.WATCH;
		goalUntil = now + WATCH_TICKS;
	}

	// ------------------------------------------------------------------ bouts

	private void newBout(long now) {
		RandomSource r = j.getRandom();
		bout = j.profile().period(j.timeOfDay()).pick(r.nextFloat());
		boutEnd = now + Mth.nextInt(r, BOUT_MIN, BOUT_MAX);
		j.setBout(bout);
	}

	private boolean boutOver(long now) {
		return now >= boutEnd;
	}

	private void decide(ServerLevel level, long now) {
		newBout(now);
		RandomSource r = j.getRandom();
		j.setAction(Action.NONE);
		switch (bout) {
			case REST -> {
				if (perched) {
					goal = Goal.PERCHED;
					j.setAction(Action.REST);
				} else {
					toPerch(level, now, PERCH_MIN, PERCH_MAX);
				}
			}
			case FEED -> {
				if (cachingSeason(j.subSeason()) && acornForest(FaunaHabitat.surface(level, j.getBlockX(), j.getBlockZ()))
						&& r.nextFloat() < CACHE_SHARE) {
					startCache(level, now);
				} else if (r.nextFloat() < GROUND_FEED) {
					toGround(level, now);
				} else if (perched) {
					goal = Goal.PERCHED;
					j.setAction(Action.EAT);
				} else {
					toPerch(level, now, PERCH_MIN, PERCH_MAX);
				}
			}
			case MOVE -> moveOn(level, now);
		}
	}

	/** A flight to a perch {@code min}-{@code max} blocks away; on the ground or where it is when there is none. */
	private void toPerch(ServerLevel level, long now, int min, int max) {
		RandomSource r = j.getRandom();
		Vec3 away = homeAway();
		for (JaySpace.Perch p : JaySpace.perchesNear(level, j.position(), min, max, PERCH_TRIES, 3, j.getBbWidth(), j.getBbHeight(), r, away)) {
			if (j.flight().flyTo(p.feet(), p.branch() ? Landing.BRANCH : Landing.LEAVES, JayFlight.CRUISE_SPEED, r)) {
				goal = Goal.FLYING;
				return;
			}
		}
		NO_PERCH.increment();
		goal = j.onGround() && !perched ? Goal.FORAGE : Goal.PERCHED;
		nextStop = now;
	}

	/** Beyond its home range: the point opposite its home (the perches are drawn on the far side of it), else null. */
	private @Nullable Vec3 homeAway() {
		Vec3 home = j.home();
		Vec3 here = j.position();
		double dx = here.x - home.x;
		double dz = here.z - home.z;
		if (dx * dx + dz * dz <= (double) j.profile().homeRange() * j.profile().homeRange()) {
			return null;
		}
		return new Vec3(here.x + dx, here.y, here.z + dz);
	}

	private void moveOn(ServerLevel level, long now) {
		toPerch(level, now, MOVE_MIN, MOVE_MAX);
	}

	/** Down to the ground for a feeding bout (from a perch, a flight to a spot on the ground near it). */
	private void toGround(ServerLevel level, long now) {
		GROUND_FEEDS.increment();
		if (!perched && j.onGround()) {
			goal = Goal.FORAGE;
			nextStop = now;
			return;
		}
		RandomSource r = j.getRandom();
		Vec3 spot = groundSpot(level, null, 2, 10, r);
		if (spot != null && j.flight().flyTo(spot, Landing.GROUND, JayFlight.CRUISE_SPEED, r)) {
			goal = Goal.FLYING;
			return;
		}
		goal = Goal.PERCHED;
		j.setAction(Action.EAT);
	}

	/** Feeding on the ground: pecking at a stop, then a few hops to the next. */
	private void forage(ServerLevel level, long now) {
		if (boutOver(now)) {
			j.setAction(Action.NONE);
			goal = Goal.IDLE;
			return;
		}
		if (now < nextStop) {
			return;
		}
		RandomSource r = j.getRandom();
		if (j.action() == Action.EAT) {
			// the next stop a few hops away
			j.setAction(Action.NONE);
			Vec3 to = groundSpot(level, null, 2, 5, r);
			if (to != null) {
				BrainUtil.setMemory(j, MemoryModuleType.WALK_TARGET, new WalkTarget(to, 1.0F, 0));
			}
			nextStop = now + 40;
		} else if (!j.getNavigation().isInProgress() || now > nextStop + 60) {
			j.getNavigation().stop();
			j.setAction(Action.EAT);
			nextStop = now + 40 + r.nextInt(80);
		}
	}

	// ------------------------------------------------------------------ acorns

	/** The sub-seasons in which jays hide acorns (the acorns ripen and fall from September to November). */
	public static boolean cachingSeason(@Nullable SubSeason s) {
		return s == SubSeason.EARLY_AUTUMN || s == SubSeason.MID_AUTUMN || s == SubSeason.LATE_AUTUMN;
	}

	/** The forests with oaks whose acorns the jay hides: the oak-hornbeam forests and the mixed forests with oak. */
	public static boolean acornForest(@Nullable HabitatBiome b) {
		return b == HabitatBiome.OAK_HORNBEAM_FOREST || b == HabitatBiome.MIXED_FOREST || b == HabitatBiome.MIXED_PINE_FOREST
				|| b == HabitatBiome.ELM_ASH_FOREST;
	}

	private void startCache(ServerLevel level, long now) {
		if (perched || !j.onGround()) {
			RandomSource r = j.getRandom();
			Vec3 spot = groundSpot(level, null, 2, 10, r);
			if (spot != null && j.flight().flyTo(spot, Landing.GROUND, JayFlight.CRUISE_SPEED, r)) {
				// it picks the acorn up where it lands
				goal = Goal.FLYING;
				pickAfterLanding = true;
				return;
			}
			toGround(level, now);
			return;
		}
		pick(now);
	}

	private void pick(long now) {
		goal = Goal.PICK;
		goalUntil = now + PICK_TICKS;
		j.getNavigation().stop();
		j.setAction(Action.EAT);
	}

	private void carry(ServerLevel level, long now) {
		RandomSource r = j.getRandom();
		j.setCarrying(true);
		j.setAction(Action.NONE);
		Vec3 spot = groundSpot(level, null, CACHE_MIN, CACHE_MAX, r);
		if (spot != null && j.flight().flyTo(spot, Landing.GROUND, JayFlight.CRUISE_SPEED, r)) {
			goal = Goal.CARRY;
			return;
		}
		// nowhere to fly: it hides the acorn a few hops away
		goal = Goal.HIDE;
		goalUntil = now + CACHE_TICKS;
		j.setAction(Action.EAT);
	}

	// ------------------------------------------------------------------ places

	/**
	 * A spot on the ground where the jay can stand ({@link FaunaSpawner#placeUncounted}: dry, light, room for the hitbox),
	 * {@code min}-{@code max} blocks away, on the far side from {@code away} when given; or null.
	 */
	private @Nullable Vec3 groundSpot(ServerLevel level, @Nullable Vec3 away, int min, int max, RandomSource r) {
		Vec3 here = j.position();
		for (int i = 0; i < 6; i++) {
			double angle = r.nextDouble() * Math.PI * 2;
			if (away != null) {
				angle = Math.atan2(here.z - away.z, here.x - away.x) + (r.nextDouble() - 0.5) * Math.PI * 0.9;
			}
			double d = min + r.nextDouble() * (max - min);
			int x = Mth.floor(here.x + Math.cos(angle) * d);
			int z = Mth.floor(here.z + Math.sin(angle) * d);
			Vec3 at = FaunaSpawner.placeUncounted(level, ModEntities.eurasianJay(), x, z);
			if (at != null && Math.abs(at.y - here.y) < 16) {
				return at;
			}
		}
		return null;
	}
}
