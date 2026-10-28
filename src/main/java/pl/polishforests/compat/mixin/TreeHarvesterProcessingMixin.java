package pl.polishforests.compat.mixin;

import com.serilum.treeharvester_common_fabric.config.ConfigHandler;
import com.serilum.treeharvester_common_fabric.processing.TreeProcessing;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.compat.FellingCompat;

/**
 * Step G1: Tree Harvester gets the wood of a tree of the tree planner from its parent graph and the count of its
 * log-giving blocks ({@link FellingCompat#treeHarvesterLogs}, {@link FellingCompat#treeHarvesterLogAmount}); other trees
 * keep the mod's rules.
 */
@Mixin(value = TreeProcessing.class, remap = false)
abstract class TreeHarvesterProcessingMixin {
	@Inject(method = "isTreeAndReturnLogAmount", at = @At("HEAD"), cancellable = true, require = 0)
	private static void polishforests$logAmount(Level level, BlockPos pos, Block logType, CallbackInfoReturnable<Integer> cir) {
		Integer amount = FellingCompat.treeHarvesterLogAmount(level, pos, ConfigHandler.ignorePlayerMadeTrees);
		if (amount != null) {
			cir.setReturnValue(amount);
		}
	}

	@Inject(method = "getLogsToBreak", at = @At("HEAD"), cancellable = true, require = 0)
	private static void polishforests$logs(Level level, BlockPos startPos, Block logType, CallbackInfoReturnable<List<BlockPos>> cir) {
		List<BlockPos> logs = FellingCompat.treeHarvesterLogs(level, startPos, ConfigHandler.maxAmountOfLogsBrokenPerHarvest);
		if (logs != null) {
			cir.setReturnValue(logs);
		}
	}
}
