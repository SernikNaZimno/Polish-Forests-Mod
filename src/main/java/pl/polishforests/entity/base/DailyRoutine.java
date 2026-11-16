package pl.polishforests.entity.base;

import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryCondition;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.base.ExtendedBehaviour;
import net.tslat.smartbrainlib.library.object.MemoryTest;
import net.tslat.smartbrainlib.util.BrainUtil;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.base.AnimalProfile.Activity;

/**
 * The daily routine of an animal of the mod (step FA2, docs/04-pionowy-wycinek.md "Projekt FA2"): bouts of feeding,
 * resting and moving drawn from the shares of the period of the profile's schedule at the time of day; a member of a group
 * takes its leader's bout ({@value #FOLLOW_LEADER} of the time) and stays near it.
 *
 * <ul>
 * <li><b>Feed</b> ({@value #FEED_MIN}-{@value #FEED_MAX} ticks): grazing on the spot, or after a few steps (half of the
 * bouts) to a place within {@value #FEED_RADIUS} blocks.
 * <li><b>Rest</b> ({@value #REST_MIN}-{@value #REST_MAX} ticks): a leader or a lone animal walks to the best
 * {@link Thicket} within {@value #THICKET_RADIUS} blocks and lies down there (on the spot when none is near); a member lies
 * down near its leader.
 * <li><b>Move</b>: a walk to a place within {@value #MOVE_RADIUS} blocks; a leader whose home is farther than the profile's
 * {@code homeRange} walks back towards it, so the daily range stays bounded.
 * </ul>
 *
 * A bout ends when its time is up, when the period of the schedule changes, when a member is farther than
 * {@value #LEADER_REACH} blocks from its leader, when the animal flees, or when {@code interrupt} holds (a player near, an
 * alarm: the alert behaviour of the species takes over).
 *
 * @param <E> the species
 */
public class DailyRoutine<E extends PolishAnimal<?>> extends ExtendedBehaviour<E> {
	public static final int FEED_MIN = 200;
	public static final int FEED_MAX = 600;
	public static final int REST_MIN = 500;
	public static final int REST_MAX = 1400;
	public static final int FEED_RADIUS = 6;
	public static final int THICKET_RADIUS = 16;
	public static final int MOVE_RADIUS = 12;
	/** Distance (blocks) from its leader within which a member stays, and beyond which its bout ends. */
	public static final int LEADER_NEAR = 6;
	public static final int LEADER_REACH = 16;
	/** Share of the bouts of a member that copy its leader's bout. */
	public static final float FOLLOW_LEADER = 0.8F;
	/** Ticks a walk to the place of a bout may take. */
	private static final int WALK_TICKS = 300;
	private static final MemoryTest MEMORIES = MemoryTest.builder(1).usesMemories(MemoryModuleType.WALK_TARGET);

	private final Predicate<E> interrupt;
	private final BiPredicate<E, Vec3> targetOk;
	private Activity activity = Activity.MOVE;
	private int period;
	private long endTick;
	private long walkUntil;
	private @Nullable Vec3 target;
	private boolean settled;

	/**
	 * @param interrupt a condition that ends a bout (a player near, an alarm)
	 * @param targetOk  whether a place may be a walk target of the species (its habitats, no leaves, no water)
	 */
	public DailyRoutine(Predicate<E> interrupt, BiPredicate<E, Vec3> targetOk) {
		this.interrupt = interrupt;
		this.targetOk = targetOk;
		runFor(e -> REST_MAX + WALK_TICKS);
	}

	@Override
	public Set<MemoryCondition<?, ?>> getMemoryRequirements() {
		return MEMORIES;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, E animal) {
		return !animal.isFleeing() && !interrupt.test(animal);
	}

	@Override
	protected void start(E animal) {
		RandomSource random = animal.getRandom();
		AnimalProfile profile = animal.profile();
		long now = animal.level().getGameTime();
		long time = animal.timeOfDay();
		period = profile.periodIndex(time);
		PolishAnimal<?> leader = animal.leader();
		Activity led = leader == null ? null : leader.bout();
		activity = led != null && random.nextFloat() < FOLLOW_LEADER ? led : profile.schedule().get(period).pick(random.nextFloat());
		settled = false;
		target = null;
		int ticks = switch (activity) {
			case FEED -> Mth.nextInt(random, FEED_MIN, FEED_MAX);
			case REST -> Mth.nextInt(random, REST_MIN, REST_MAX);
			case MOVE -> WALK_TICKS;
		};
		Vec3 base = leader != null ? leader.position() : animal.position();
		boolean far = leader != null && animal.distanceTo(leader) > LEADER_NEAR;
		switch (activity) {
			case FEED -> target = far || random.nextBoolean() ? pointNear(animal, base, far ? LEADER_NEAR / 2 : FEED_RADIUS, random) : null;
			case REST -> {
				if (leader != null) {
					target = far ? pointNear(animal, base, 3, random) : null;
				} else {
					BlockPos t = Thicket.find(animal.level(), animal.blockPosition(), THICKET_RADIUS, random);
					target = t == null ? null : Vec3.atBottomCenterOf(t);
				}
			}
			case MOVE -> target = moveTarget(animal, leader, random);
		}
		animal.setAction(PolishAnimal.Action.NONE);
		animal.setBout(activity);
		if (target != null) {
			BrainUtil.setMemory(animal, MemoryModuleType.WALK_TARGET, new WalkTarget(target, far ? 1.15F : 1.0F, 1));
			walkUntil = now + WALK_TICKS;
		} else {
			settle(animal);
		}
		endTick = now + (target != null && activity != Activity.MOVE ? WALK_TICKS : 0) + ticks;
	}

	private void settle(E animal) {
		settled = true;
		animal.setAction(switch (activity) {
			case FEED -> PolishAnimal.Action.EAT;
			case REST -> PolishAnimal.Action.REST;
			case MOVE -> PolishAnimal.Action.NONE;
		});
	}

	@Override
	protected void tick(E animal) {
		if (settled) {
			return;
		}
		long now = animal.level().getGameTime();
		boolean arrived = target == null || animal.position().distanceToSqr(target) < 2.25;
		boolean stuck = now > walkUntil || !animal.getNavigation().isInProgress() && !BrainUtil.hasMemory(animal, MemoryModuleType.WALK_TARGET);
		if (arrived || stuck) {
			BrainUtil.clearMemory(animal, MemoryModuleType.WALK_TARGET);
			animal.getNavigation().stop();
			settle(animal);
		}
	}

	@Override
	protected boolean shouldKeepRunning(E animal) {
		if (animal.isFleeing() || interrupt.test(animal) || animal.level().getGameTime() >= endTick) {
			return false;
		}
		if (activity == Activity.MOVE && settled) {
			return false;
		}
		if (animal.profile().periodIndex(animal.timeOfDay()) != period) {
			return false;
		}
		PolishAnimal<?> leader = animal.leader();
		return leader == null || animal.distanceTo(leader) <= LEADER_REACH;
	}

	@Override
	protected void stop(E animal) {
		if (animal.action() == PolishAnimal.Action.EAT || animal.action() == PolishAnimal.Action.REST) {
			animal.setAction(PolishAnimal.Action.NONE);
		}
		animal.setBout(null);
	}

	/** The target of a walk: near the leader for a member, back towards home beyond the home range, else around. */
	private @Nullable Vec3 moveTarget(E animal, @Nullable PolishAnimal<?> leader, RandomSource random) {
		if (leader != null) {
			return pointNear(animal, leader.position(), LEADER_NEAR, random);
		}
		Vec3 home = animal.home();
		int range = animal.profile().homeRange();
		if (horizontal(animal.position(), home) > range) {
			return pointNear(animal, home, MOVE_RADIUS, random);
		}
		for (int i = 0; i < 4; i++) {
			Vec3 p = pointNear(animal, animal.position(), MOVE_RADIUS, random);
			if (p != null && horizontal(p, home) <= range) {
				return p;
			}
		}
		return null;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** A place to stand within {@code radius} blocks of {@code base} that the species may walk to, or null (8 tries). */
	private @Nullable Vec3 pointNear(E animal, Vec3 base, int radius, RandomSource random) {
		Level level = animal.level();
		for (int i = 0; i < 8; i++) {
			int x = Mth.floor(base.x) + random.nextInt(2 * radius + 1) - radius;
			int z = Mth.floor(base.z) + random.nextInt(2 * radius + 1) - radius;
			BlockPos column = new BlockPos(x, Mth.floor(base.y), z);
			if (!level.hasChunkAt(column)) {
				continue;
			}
			BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (Math.abs(feet.getY() - base.y) > 6 || !Thicket.standable(level, feet)) {
				continue;
			}
			Vec3 p = Vec3.atBottomCenterOf(feet);
			if (targetOk.test(animal, p)) {
				return p;
			}
		}
		return null;
	}
}
