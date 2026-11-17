package pl.polishforests.entity.base;

import java.util.Set;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryCondition;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.PathType;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.FollowEntity;
import net.tslat.smartbrainlib.library.object.MemoryTest;
import org.jspecify.annotations.Nullable;

/**
 * A member of a group follows its group (step FA3, docs/04-pionowy-wycinek.md "Projekt FA3"): SmartBrainLib's
 * {@link FollowEntity} with the followed animal taken from the group links of {@link PolishAnimal} (the leader's id and the
 * single file, kept in the animal like a memory; vanilla's memory of visible entities needs a line of sight, which the
 * undergrowth blocks).
 *
 * <ul>
 * <li><b>In single file</b>, while its leader flees: the member follows the animal ahead of it in the file the leader formed
 * at the start of the flight ({@link PolishAnimal#formFile}: the grown ones by rank, the young last) at
 * {@value #FILE_SPEED} times its speed, from {@value #FILE_START} blocks behind it to {@value #FILE_CLOSE}; it counts as
 * fleeing meanwhile (the state of the fauna command, no warning of its own).
 * <li><b>Near the leader</b>, otherwise: a member farther than {@link DailyRoutine#LEADER_REACH} blocks walks back to within
 * {@link DailyRoutine#LEADER_NEAR} blocks at {@value #LEADER_SPEED} times its speed (its daily routine waits meanwhile).
 * </ul>
 * A member that flees on its own (its leader does not flee yet) does not follow. The follow never makes a path through
 * water ({@link FollowEntity} allows water while it runs; the animals of the mod keep out of it, KFA-4).
 *
 * @param <E> the species
 */
public class FollowHerd<E extends PolishAnimal<?>> extends FollowEntity<E> {
	public static final float FILE_SPEED = 2.3F;
	public static final float FILE_START = 2.5F;
	public static final float FILE_CLOSE = 1.5F;
	public static final float LEADER_SPEED = 1.1F;
	private static final MemoryTest MEMORIES = MemoryTest.builder(2).usesMemories(MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET);

	public FollowHerd() {
		super((self, target) -> true);
		speedModifier((self, target) -> inFile(self) ? FILE_SPEED : LEADER_SPEED);
		closeEnoughDist((self, target) -> inFile(self) ? FILE_CLOSE : DailyRoutine.LEADER_NEAR);
		startFollowingAfter((self, target) -> inFile(self) ? FILE_START : DailyRoutine.LEADER_REACH);
	}

	/** Whether a member follows its group in single file now: its leader flees. */
	public static boolean inFile(PolishAnimal<?> animal) {
		PolishAnimal<?> leader = animal.leader();
		return leader != null && leader.isFleeing();
	}

	/** Whether a member wants to follow its group now (in single file, or back to a leader farther than the reach). */
	public static boolean wantsToFollow(PolishAnimal<?> animal) {
		PolishAnimal<?> leader = animal.leader();
		return leader != null && (leader.isFleeing() || animal.distanceTo(leader) > DailyRoutine.LEADER_REACH);
	}

	@Override
	public Set<MemoryCondition<?, ?>> getMemoryRequirements() {
		return MEMORIES;
	}

	@Override
	protected @Nullable LivingEntity getFollowingEntity(E animal) {
		PolishAnimal<?> leader = animal.leader();
		if (leader == null || animal.isNoAi()) {
			return null;
		}
		if (leader.isFleeing()) {
			PolishAnimal<?> ahead = animal.fileAhead();
			return ahead != null && ahead != animal && animal.distanceTo(ahead) <= DailyRoutine.LEADER_REACH * 2 ? ahead : leader;
		}
		// A member in its own flight (from a player its leader has not seen yet) runs on its own.
		return animal.isFleeing() ? null : leader;
	}

	@Override
	protected void start(E animal) {
		super.start(animal);
		animal.setPathfindingMalus(PathType.WATER, -1.0F);
		if (inFile(animal)) {
			animal.markFleeing(20, false);
		}
	}

	@Override
	protected boolean shouldKeepRunning(E animal) {
		return super.shouldKeepRunning(animal) && getFollowingEntity(animal) == followingEntity;
	}

	@Override
	protected void tick(E animal) {
		super.tick(animal);
		if (inFile(animal)) {
			animal.markFleeing(20, false);
		}
	}
}
