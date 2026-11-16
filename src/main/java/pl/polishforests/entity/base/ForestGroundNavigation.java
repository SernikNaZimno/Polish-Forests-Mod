package pl.polishforests.entity.base;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.tslat.smartbrainlib.api.core.navigation.SmoothGroundNavigation;

/**
 * SmartBrainLib's smooth ground navigation for the ground animals of the mod, which never walk on leaves (step FA1,
 * stage 2): vanilla lets a mob walk on top of a leaf block (only into one is blocked), so a deer would climb a low bush or
 * a crown that touches the slope, while the spawn never puts it there ({@code FaunaSpawner}, KFA-4: no animal on the
 * leaves). A node whose block below is in {@code #minecraft:leaves} is blocked.
 */
public class ForestGroundNavigation extends SmoothGroundNavigation {
	public ForestGroundNavigation(Mob mob, Level level) {
		super(mob, level);
	}

	@Override
	protected PathFinder createPathFinder(int maxVisitedNodes) {
		this.nodeEvaluator = new NoLeavesNodeEvaluator();
		this.nodeEvaluator.setCanPassDoors(true);
		return createSmoothPathFinder(this.nodeEvaluator, maxVisitedNodes);
	}

	/** The walk node evaluator with the nodes on leaves blocked. */
	static final class NoLeavesNodeEvaluator extends WalkNodeEvaluator {
		@Override
		public PathType getPathType(PathfindingContext context, int x, int y, int z) {
			PathType type = super.getPathType(context, x, y, z);
			if (type != PathType.BLOCKED && type != PathType.OPEN && context.getBlockState(new BlockPos(x, y - 1, z)).is(BlockTags.LEAVES)) {
				return PathType.BLOCKED;
			}
			return type;
		}
	}
}
