package pl.polishforests.entity.base;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryCondition;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.base.ExtendedBehaviour;
import net.tslat.smartbrainlib.library.object.MemoryTest;
import net.tslat.smartbrainlib.util.BrainUtil;
import org.jspecify.annotations.Nullable;

/**
 * Flight from players (step FA1, stage 2; docs/04-pionowy-wycinek.md "Stan po FA1"): when a player is closer than the
 * animal's {@link PolishAnimal#fleeDistance flight distance}, the animal runs away from it, re-aiming every second, until
 * it is {@code beyond} blocks further than that distance (or {@value #MAX_TICKS} ticks have passed).
 *
 * <p>Unlike SmartBrainLib's {@code AvoidEntity} this does not read the memory of visible entities: that memory needs a
 * line of sight from the animal's eyes, which the trunks and the leaves of a forest block within a few blocks, and its
 * range is vanilla's shared targeting range, set by whichever vanilla sensor ticked last. A deer hears and smells a
 * walking person through the undergrowth, so the distance alone decides (an invisible player is a threat only from very
 * close, see {@link PolishAnimal#fleeDistance}).
 *
 * @param <E> the species
 */
public class FleeFromPlayers<E extends PolishAnimal<?>> extends ExtendedBehaviour<E> {
	/** Longest flight before the animal stops to look back (ticks). */
	public static final int MAX_TICKS = 200;
	private static final MemoryTest MEMORIES = MemoryTest.builder(1).usesMemories(MemoryModuleType.WALK_TARGET);
	/** Length of one leg of the flight (blocks). */
	private static final int LEG = 16;

	private final float speed;
	private final float beyond;
	private @Nullable Player threat;

	public FleeFromPlayers(float speed, float beyond) {
		this.speed = speed;
		this.beyond = beyond;
		runFor(e -> MAX_TICKS);
	}

	@Override
	public Set<MemoryCondition<?, ?>> getMemoryRequirements() {
		return MEMORIES;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, E animal) {
		threat = animal.nearestThreat();
		return threat != null;
	}

	@Override
	protected void start(E animal) {
		runAway(animal);
	}

	@Override
	protected boolean shouldKeepRunning(E animal) {
		Player p = threat;
		return p != null && p.isAlive() && !p.isRemoved() && p.level() == animal.level() && !p.isSpectator()
				&& animal.distanceTo(p) < animal.fleeDistance(p) + beyond;
	}

	@Override
	protected void tick(E animal) {
		if (!animal.getNavigation().isInProgress() || animal.tickCount % 20 == 0) {
			runAway(animal);
		}
	}

	@Override
	protected void stop(E animal) {
		threat = null;
	}

	private void runAway(E animal) {
		Player p = threat;
		if (p == null) {
			return;
		}
		Vec3 away = DefaultRandomPos.getPosAway(animal, LEG, 7, p.position());
		if (away != null) {
			BrainUtil.setMemory(animal, MemoryModuleType.WALK_TARGET, new WalkTarget(away, speed, 0));
		}
		animal.markFleeing(40);
	}
}
