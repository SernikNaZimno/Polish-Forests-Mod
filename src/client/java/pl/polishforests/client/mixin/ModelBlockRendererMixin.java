package pl.polishforests.client.mixin;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.client.ClientSeasonModels;

/**
 * Step V13 (decision V3-b): in the Fast graphics mode every renderer asks the static
 * {@code ModelBlockRenderer.forceOpaque(cutoutLeaves, state)} whether a block goes to the solid layer (the vanilla
 * section compiler and moving block renderer, the default {@code emitQuads} of FRAPI and the {@code BlockRenderer} of
 * Sodium, which then moves every quad of the block, also those of a wrapped model, to the solid material). The leaves of
 * a broadleaf species that show their bare twigs answer false ({@link ClientSeasonModels#cutOutInFastMode}), so a
 * leafless crown stays cut out in the Fast mode in vanilla, Sodium and Nvidium instead of turning into opaque cubes.
 * Every other block is unchanged.
 */
@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
	@Inject(method = "forceOpaque", at = @At("HEAD"), cancellable = true)
	private static void polishforests$keepTwigsCutOut(boolean cutoutLeaves, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (!cutoutLeaves && ClientSeasonModels.cutOutInFastMode(state)) {
			cir.setReturnValue(false);
		}
	}
}
