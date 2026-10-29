package pl.polishforests.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.worldgen.cave.SculkFastPath;

/**
 * Step O2: a sculk vein cannot spread into a block that is neither replaceable, air, a vein nor water; vanilla reads the
 * block behind the placement first. Same answer without the reads ({@link SculkFastPath}).
 */
@Mixin(targets = "net.minecraft.world.level.block.SculkVeinBlock$SculkVeinSpreaderConfig")
abstract class SculkVeinSpreaderConfigMixin {
	@Inject(method = "stateCanBeReplaced", at = @At("HEAD"), cancellable = true)
	private void polishforests$cannotHold(BlockGetter level, BlockPos sourcePos, BlockPos placementPos, Direction placementDirection,
			BlockState existingState, CallbackInfoReturnable<Boolean> cir) {
		if (SculkFastPath.ENABLED && SculkFastPath.cannotHold(existingState)) {
			cir.setReturnValue(false);
		}
	}
}
