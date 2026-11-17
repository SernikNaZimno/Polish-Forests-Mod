package pl.polishforests.entity.wildboar;

import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryCondition;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.base.ExtendedBehaviour;
import net.tslat.smartbrainlib.library.object.MemoryTest;
import net.tslat.smartbrainlib.util.BrainUtil;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.base.PolishAnimal;

/**
 * The warning of a sow with young (step FA3, C6, docs/04-pionowy-wycinek.md "Projekt FA3"): a sow that
 * {@linkplain WildBoar#holdsGround() holds her ground} (her young near, March to June) does not flee from a player within
 * {@value WildBoar#WARN_DISTANCE} blocks but charges at him with a snort, stops {@value #STOP_SHORT} blocks short of him
 * (at {@value #STOP_AT} blocks at the latest, after {@value #CHARGE_TICKS} ticks at the longest), stands
 * {@value #STAND_TICKS} ticks alert and snorting, and then flees with her group as usual. No attack: the boar has no
 * attack at all (no attack damage, no attack behaviour), so a player loses no health; the charge only warns. A sow warns
 * once in {@value WildBoar#WARN_COOLDOWN} ticks.
 */
public class WarnCharge extends ExtendedBehaviour<WildBoar> {
	public static final float SPEED = 2.2F;
	/** How far short of the player the charge aims (blocks). */
	public static final double STOP_SHORT = 2.5;
	/** Distance (blocks) at which the charge stops at the latest. */
	public static final double STOP_AT = 3.0;
	public static final int CHARGE_TICKS = 40;
	public static final int STAND_TICKS = 25;
	/** Warnings of the sows (for the census of the fauna game test). */
	public static final LongAdder WARNINGS = new LongAdder();
	private static final MemoryTest MEMORIES = MemoryTest.builder(1).usesMemories(MemoryModuleType.WALK_TARGET);

	private @Nullable Player target;
	private boolean standing;
	private long phaseEnd;

	public WarnCharge() {
		runFor(b -> CHARGE_TICKS + STAND_TICKS + 5);
	}

	@Override
	public Set<MemoryCondition<?, ?>> getMemoryRequirements() {
		return MEMORIES;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, WildBoar boar) {
		if (!boar.holdsGround()) {
			return false;
		}
		target = nearestPlayer(boar, WildBoar.WARN_DISTANCE);
		return target != null;
	}

	/** The nearest player (not a spectator, alive) within a distance of the boar, or null. */
	static @Nullable Player nearestPlayer(PolishAnimal<?> boar, double within) {
		Player best = null;
		double bestDist = within;
		for (Player p : boar.level().players()) {
			if (p.isSpectator() || !p.isAlive()) {
				continue;
			}
			double d = boar.distanceTo(p);
			if (d < bestDist) {
				best = p;
				bestDist = d;
			}
		}
		return best;
	}

	@Override
	protected void start(WildBoar boar) {
		boar.setWarning(true);
		standing = false;
		phaseEnd = boar.level().getGameTime() + CHARGE_TICKS;
		WARNINGS.increment();
		boar.snort(false);
		aim(boar);
	}

	private void aim(WildBoar boar) {
		Player p = target;
		if (p == null) {
			return;
		}
		Vec3 from = p.position();
		Vec3 dir = boar.position().subtract(from);
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		Vec3 at = flat.lengthSqr() < 1.0E-6 ? boar.position() : from.add(flat.normalize().scale(STOP_SHORT));
		BrainUtil.setMemory(boar, MemoryModuleType.WALK_TARGET, new WalkTarget(at, SPEED, 0));
	}

	@Override
	protected boolean shouldKeepRunning(WildBoar boar) {
		Player p = target;
		if (p == null || !p.isAlive() || p.isRemoved() || p.isSpectator() || p.level() != boar.level()) {
			return false;
		}
		return !standing || boar.level().getGameTime() < phaseEnd;
	}

	@Override
	protected void tick(WildBoar boar) {
		Player p = target;
		if (p == null) {
			return;
		}
		long now = boar.level().getGameTime();
		if (!standing) {
			if (boar.distanceTo(p) <= STOP_AT || now >= phaseEnd) {
				standing = true;
				phaseEnd = now + STAND_TICKS;
				BrainUtil.clearMemory(boar, MemoryModuleType.WALK_TARGET);
				boar.getNavigation().stop();
				boar.setAction(PolishAnimal.Action.ALERT);
				boar.snort(false);
			} else if (now % 5 == 0) {
				aim(boar);
			}
		}
		if (standing) {
			boar.getLookControl().setLookAt(p, 30.0F, 30.0F);
		}
	}

	@Override
	protected void stop(WildBoar boar) {
		boar.setWarning(false);
		boar.warned();
		if (boar.action() == PolishAnimal.Action.ALERT) {
			boar.setAction(PolishAnimal.Action.NONE);
		}
		BrainUtil.clearMemory(boar, MemoryModuleType.WALK_TARGET);
		target = null;
	}
}
