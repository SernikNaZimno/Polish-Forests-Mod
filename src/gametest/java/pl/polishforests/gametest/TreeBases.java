package pl.polishforests.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import pl.polishforests.block.RootFlareBlock;
import pl.polishforests.block.SpeciesLeavesBlock;
import pl.polishforests.block.TrunkBlock;

/**
 * Step F2 (stage 2): which trunk bases of the KF-1 census in {@code habitats} and {@code vanilla_forests} stand for a tree
 * of the norms card, whose N1 counts trees (a1 and a2), not stems. The census counted every column with wood standing on
 * the ground, so the coppice stems of a black alder on a stool (two or three stems of one tree) and the thin stems of the
 * tall shrubs (the bird cherry of the ash-alder forest with three stems of 5-7 blocks, the hazel and the alder buckthorn
 * of the slice) counted as trees: the ash-alder forest had 25 "trunks" per chunk where the card counts 10 trees. Not a
 * tree: a root flare; a trunk block whose parent lies beside it (a coppice stem rises from the trunk base of its tree
 * in the next column, {@code TreeShapePlan.stool}); and the thin stem of a shrub, a trunk block of radius 1 px whose column
 * of trunk blocks is at most {@value #SHRUB_MAX_HEIGHT} blocks high and whose top carries the leaves of a shrub (vanilla
 * leaves or a shrub foliage of the mod, {@code TreeFoliage.shrub()}) rather than the leaves of a tree species. Vanilla
 * logs and the full logs of the thick trunks count as before.
 */
final class TreeBases {
	/** Greatest height of a shrub stem in blocks (the bird cherry of a large sapling: 6 + 2). */
	static final int SHRUB_MAX_HEIGHT = 8;

	private TreeBases() {
	}

	/** Whether the wood block at {@code base}, the lowest block above the ground, is the base of a tree (see the class). */
	static boolean tree(BlockGetter level, BlockPos base) {
		BlockState s = level.getBlockState(base);
		if (!(s.getBlock() instanceof TrunkBlock)) {
			return true;
		}
		if (s.getBlock() instanceof RootFlareBlock || s.getValue(TrunkBlock.PARENT) != Direction.DOWN) {
			return false;
		}
		return !shrubStem(level, base, s);
	}

	private static boolean shrubStem(BlockGetter level, BlockPos base, BlockState s) {
		if (s.getValue(TrunkBlock.RADIUS) > 1) {
			return false;
		}
		BlockPos.MutableBlockPos p = base.mutable();
		int height = 0;
		while (height <= SHRUB_MAX_HEIGHT && level.getBlockState(p).getBlock() instanceof TrunkBlock) {
			height++;
			p.move(Direction.UP);
		}
		if (height > SHRUB_MAX_HEIGHT) {
			return false;
		}
		int top = base.getY() + height - 1;
		int shrubLeaves = 0;
		int treeLeaves = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					BlockState l = level.getBlockState(p.set(base.getX() + dx, top + dy, base.getZ() + dz));
					if (!l.is(BlockTags.LEAVES)) {
						continue;
					}
					if (l.getBlock() instanceof SpeciesLeavesBlock leaves) {
						if (leaves.foliage().shrub()) {
							shrubLeaves++;
						} else {
							treeLeaves++;
						}
					} else if (BuiltInRegistries.BLOCK.getKey(l.getBlock()).getNamespace().equals("minecraft")) {
						shrubLeaves++;
					}
				}
			}
		}
		return shrubLeaves > treeLeaves;
	}
}
