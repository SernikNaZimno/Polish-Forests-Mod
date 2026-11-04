package pl.polishforests.compat.mixin;

import com.serilum.treeharvester_common_fabric.data.Variables;
import com.serilum.treeharvester_common_fabric.util.Util;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.compat.FellingCompat;

/**
 * Step P1: Tree Harvester takes the thin trunks of this mod, stripped ones included, as tree logs
 * ({@link FellingCompat#ownTrunk}). Its own rule leaves out every block whose name in the player's language contains
 * "stripped", so a stripped tree fell in some languages and not in others; the mod's blacklist still applies.
 */
@Mixin(value = Util.class, remap = false)
abstract class TreeHarvesterLogMixin {
	@Inject(method = "isTreeLog", at = @At("HEAD"), cancellable = true, require = 0)
	private static void polishforests$ownTrunk(Block block, CallbackInfoReturnable<Boolean> cir) {
		if (FellingCompat.ownTrunk(block)) {
			cir.setReturnValue(Variables.blacklistedLogBlocks == null || !Variables.blacklistedLogBlocks.contains(block));
		}
	}
}
