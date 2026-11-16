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
 * it is {@code beyond} blocks further than that distance (or {@value #MAX_TICKS} ticks have passed). Step FA2: also from the
 * source of a {@link ForestAlarm} heard at the flight intensity ({@link PolishAnimal#alarmSource()}), for
 * {@value #ALARM_TICKS} ticks; a flight starts with the animal's warning ({@link PolishAnimal#markFleeing(int, boolean)}),
 * except a flight from the warning of another animal.
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
	/** Ticks of a flight from an alarm. */
	public static final int ALARM_TICKS = 100;
	private @Nullable Player threat;
	private @Nullable Vec3 alarm;
	private boolean warns;
	private long alarmUntil;

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
		alarm = threat == null ? animal.alarmSource() : null;
		warns = threat != null || !animal.alarmFromBark();
		return threat != null || alarm != null;
	}

	@Override
	protected void start(E animal) {
		if (alarm != null) {
			alarmUntil = animal.level().getGameTime() + ALARM_TICKS;
			animal.clearAlarm();
		}
		runAway(animal);
	}

	@Override
	protected boolean shouldKeepRunning(E animal) {
		if (alarm != null) {
			return animal.level().getGameTime() < alarmUntil;
		}
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
		alarm = null;
	}

	private void runAway(E animal) {
		Player p = threat;
		Vec3 from = p != null ? p.position() : alarm;
		if (from == null) {
			return;
		}
		Vec3 away = DefaultRandomPos.getPosAway(animal, LEG, 7, from);
		if (away != null) {
			BrainUtil.setMemory(animal, MemoryModuleType.WALK_TARGET, new WalkTarget(away, speed, 0));
		}
		animal.markFleeing(40, warns);
	}
}
