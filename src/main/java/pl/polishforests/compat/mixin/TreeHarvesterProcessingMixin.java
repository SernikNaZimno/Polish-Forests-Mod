package pl.polishforests.compat.mixin;

import com.serilum.treeharvester_common_fabric.config.ConfigHandler;
import com.serilum.treeharvester_common_fabric.data.Variables;
import com.serilum.treeharvester_common_fabric.processing.TreeProcessing;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import oshi.util.tuples.Quartet;
import pl.polishforests.compat.FellingCompat;

/**
 * Step G1: Tree Harvester gets the wood of a tree of the tree planner from its parent graph and the count of its
 * log-giving blocks ({@link FellingCompat#treeHarvesterLogs}, {@link FellingCompat#treeHarvesterLogAmount}); other trees
 * keep the mod's rules. The replanting of a sapling (the mod's option, on by default) takes only the base of the felled
 * tree ({@link FellingCompat#treeHarvesterReplant}): the mod takes every block of the same kind next to the base and
 * later sets saplings there without looking, so it replaced a shrub stem or a neighbor's trunk beside it.
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

	@Inject(method = "prepareSaplingReplant", at = @At("TAIL"), require = 0)
	private static void polishforests$replant(Level level, BlockPos pos, Block logType, Block leafType, CallbackInfo ci) {
		Set<BlockPos> spots = FellingCompat.treeHarvesterReplant(level, pos);
		if (spots == null) {
			return;
		}
		List<Quartet<Date, BlockPos, CopyOnWriteArrayList<BlockPos>, Block>> all = Variables.saplingPositions;
		for (int i = all.size() - 1; i >= 0; i--) {
			Quartet<Date, BlockPos, CopyOnWriteArrayList<BlockPos>, Block> q = all.get(i);
			if (q.getB().equals(pos)) {
				q.getC().retainAll(spots);
				return;
			}
		}
	}
}
