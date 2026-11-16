package pl.polishforests.entity.base;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryCondition;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.Level;
import net.tslat.smartbrainlib.api.core.behaviour.base.ExtendedBehaviour;
import net.tslat.smartbrainlib.library.object.MemoryTest;
import net.tslat.smartbrainlib.util.BrainUtil;

/**
 * A ground animal in water walks to the nearest dry place it can stand on (step FA1, stage 2; docs/04 "Stan po FA1"):
 * the animals of the mod do not path through water (water malus -1), and one that fell or was pushed in leaves it
 * instead of floating where it is (the census found a roe deer floating in a pool for minutes). Like vanilla's
 * {@code TryFindLand}, as a SmartBrainLib behaviour.
 *
 * @param <E> the species
 */
public class LeaveWater<E extends PolishAnimal<?>> extends ExtendedBehaviour<E> {
	private static final MemoryTest MEMORIES = MemoryTest.builder(1).usesMemories(MemoryModuleType.WALK_TARGET);
	private static final int RADIUS = 10;
	private final float speed;

	public LeaveWater(float speed) {
		this.speed = speed;
		runFor(e -> 1);
		cooldownFor(e -> 40);
	}

	@Override
	public Set<MemoryCondition<?, ?>> getMemoryRequirements() {
		return MEMORIES;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, E animal) {
		return animal.isInWater() && !animal.isFleeing();
	}

	@Override
	protected void start(E animal) {
		Level level = animal.level();
		BlockPos at = animal.blockPosition();
		// Nearest first (by Manhattan distance).
		for (BlockPos p : BlockPos.withinClippedManhattan(at, RADIUS, 4, RADIUS)) {
			if ((p.getX() != at.getX() || p.getZ() != at.getZ()) && dry(level, p)) {
				BrainUtil.setMemory(animal, MemoryModuleType.WALK_TARGET, new WalkTarget(p.immutable(), speed, 0));
				return;
			}
		}
	}

	/** A dry place to stand on: no fluid at the feet, above or below, room for the body, a sturdy top below. */
	static boolean dry(Level level, BlockPos p) {
		BlockPos below = p.below();
		return level.getFluidState(p).isEmpty() && level.getFluidState(below).isEmpty() && level.getFluidState(p.above()).isEmpty()
				&& level.getBlockState(p).getCollisionShape(level, p).isEmpty()
				&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
				&& level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}
}
