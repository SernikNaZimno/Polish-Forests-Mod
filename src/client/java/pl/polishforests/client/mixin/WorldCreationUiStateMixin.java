package pl.polishforests.client.mixin;

import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.client.screen.PolandPresetEditor;

/**
 * Vanilla keeps preset editors in an immutable map. For the "Poland" presets we return our own
 * editor, so the "Customize" button works in the world creation menu.
 */
@Mixin(WorldCreationUiState.class)
abstract class WorldCreationUiStateMixin {
	@Inject(method = "getPresetEditor", at = @At("HEAD"), cancellable = true)
	private void polishforests$polandEditor(CallbackInfoReturnable<PresetEditor> cir) {
		Holder<WorldPreset> preset = ((WorldCreationUiState) (Object) this).getWorldType().preset();
		if (preset != null && preset.unwrapKey().filter(PolandPresetEditor.PRESETS::contains).isPresent()) {
			cir.setReturnValue(PolandPresetEditor.INSTANCE);
		}
	}
}
