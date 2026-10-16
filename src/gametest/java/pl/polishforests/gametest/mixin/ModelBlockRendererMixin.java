package pl.polishforests.gametest.mixin;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.gametest.probe.SeasonalModel;

/**
 * Render probes of step V3 (review round 1): in the Fast graphics mode every renderer asks the static
 * {@code ModelBlockRenderer.forceOpaque(cutoutLeaves, state)} whether a block goes to the solid layer: the vanilla section
 * compiler and the moving block renderer, the default {@code emitQuads} of FRAPI and Sodium's {@code BlockRenderer} (which
 * then moves every quad of the block, also the ones of a wrapped model, to the solid material). A block whose seasonal
 * model draws a cut-out alternative (the bare twigs of the probe leaves) answers false, so the twigs stay cut out in Fast
 * mode in every renderer. Nothing changes outside the probe run: {@link SeasonalModel#cutoutInFastMode} is false for every
 * other block.
 */
@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
	@Inject(method = "forceOpaque", at = @At("HEAD"), cancellable = true)
	private static void polishforestsProbe$keepTwigsCutOut(boolean cutoutLeaves, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (!cutoutLeaves && SeasonalModel.cutoutInFastMode(state)) {
			cir.setReturnValue(false);
		}
	}
}
