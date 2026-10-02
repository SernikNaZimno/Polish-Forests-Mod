package pl.polskielasy.client.mixin;

import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polskielasy.client.screen.PolskaPresetEditor;

/**
 * Wanilia trzyma edytory presetów w niezmiennej mapie. Dla presetu "Polska" zwracamy własny
 * edytor, dzięki czemu w menu tworzenia świata działa przycisk "Dostosuj".
 */
@Mixin(WorldCreationUiState.class)
abstract class WorldCreationUiStateMixin {
	@Inject(method = "getPresetEditor", at = @At("HEAD"), cancellable = true)
	private void polskielasy$polskaEditor(CallbackInfoReturnable<PresetEditor> cir) {
		Holder<WorldPreset> preset = ((WorldCreationUiState) (Object) this).getWorldType().preset();
		if (preset != null && preset.unwrapKey().filter(PolskaPresetEditor.PRESETS::contains).isPresent()) {
			cir.setReturnValue(PolskaPresetEditor.INSTANCE);
		}
	}
}
