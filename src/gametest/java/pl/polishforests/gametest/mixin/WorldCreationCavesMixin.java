package pl.polishforests.gametest.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.polishforests.client.screen.PolandPresetEditor;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;

/**
 * Step J1: {@code -Pcaves=off} creates the worlds of every game test without the caves (the Poland presets have them on
 * since J1), for the cost of the caves against the same run with caves (decision CP-C.4a) and for checks of the terrain
 * without caves. Without the flag nothing changes.
 */
@Mixin(WorldCreationUiState.class)
abstract class WorldCreationCavesMixin {
	@Inject(method = "setWorldType", at = @At("TAIL"))
	private void polishforestsGametest$cavesOff(WorldCreationUiState.WorldTypeEntry worldType, CallbackInfo ci) {
		if (!"off".equals(System.getProperty("polishforests.caves"))) {
			return;
		}
		WorldCreationUiState ui = (WorldCreationUiState) (Object) this;
		if (ui.getSettings().selectedDimensions().overworld() instanceof PolandChunkGenerator gen && gen.settings().caves()) {
			ui.updateDimensions(PolandPresetEditor.apply(gen.settings().withCaves(false)));
		}
	}
}
