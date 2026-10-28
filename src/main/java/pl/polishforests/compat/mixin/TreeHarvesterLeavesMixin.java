package pl.polishforests.compat.mixin;

import com.serilum.treeharvester_common_fabric.processing.LeafProcessing;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.polishforests.block.SpeciesLeavesBlock;
import pl.polishforests.compat.FellingCompat;

/**
 * Step G1: Tree Harvester does not clear the leaves of this mod's species after a harvest. Its clearing takes every leaf
 * of the top block in a box around the tree that is not within 2 blocks of wood, so also the leaves of neighboring crowns
 * 3-6 blocks from their own wood; the leaves of this mod fall on their own once cut off from wood
 * ({@link SpeciesLeavesBlock}).
 */
@Mixin(value = LeafProcessing.class, remap = false)
abstract class TreeHarvesterLeavesMixin {
	@Inject(method = "breakTreeLeaves", at = @At("HEAD"), cancellable = true, require = 0)
	private static void polishforests$ownFall(Level level, List<BlockPos> logsToBreak, BlockPos lowestCenterLogPos,
			BlockPos highestLogPos, Block highestLeafBlock, ItemStack harvestTool, CallbackInfo ci) {
		if (highestLeafBlock instanceof SpeciesLeavesBlock) {
			FellingCompat.TREE_HARVESTER_LEAF_SKIPS.increment();
			ci.cancel();
		}
	}
}
