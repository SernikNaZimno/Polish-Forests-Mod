package pl.polishforests.compat.mixin;

import fr.rakambda.fallingtree.fabric.common.FallingTreeCommonsImpl;
import fr.rakambda.fallingtree.fabric.common.wrapper.BlockWrapper;
import fr.rakambda.fallingtree.common.wrapper.IBlock;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.compat.FellingCompat;

/**
 * Step P1: FallingTree's default logs ({@code #minecraft:logs} without the blocks whose id path starts with
 * {@code stripped}) also hold the stripped thin trunks of this mod ({@link FellingCompat#strippedTrunks}), so a tree the
 * player stripped falls as before; the mod's {@code deniedLogs} still apply (it checks them after the default logs).
 */
@Mixin(value = FallingTreeCommonsImpl.class, remap = false)
abstract class FallingTreeLogsMixin {
	@Inject(method = "getAllNonStrippedLogsBlocks", at = @At("RETURN"), cancellable = true, require = 0)
	private void polishforests$strippedTrunks(CallbackInfoReturnable<Set<IBlock>> cir) {
		Set<IBlock> all = new HashSet<>(cir.getReturnValue());
		for (Block b : FellingCompat.strippedTrunks()) {
			all.add(new BlockWrapper(b));
		}
		cir.setReturnValue(all);
	}
}
