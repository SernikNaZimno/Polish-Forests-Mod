package pl.polishforests.client.mixin;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.polishforests.PolishForests;

/**
 * Step W1: the hint of the world type "Default + Polish Forests" under the world type button of the world creation
 * screen, as vanilla shows one for "Amplified" (the listener that updates the button when the world type changes).
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
abstract class WorldTypeHintMixin {
	private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("default_polish_forests"));
	private static final Component HINT = Component.translatable("generator.polishforests.default_polish_forests.info");

	@Inject(method = "lambda$new$1", at = @At("TAIL"))
	private static void polishforests$hint(CycleButton<?> button, CreateWorldScreen screen, WorldCreationUiState state,
			CallbackInfo ci) {
		Holder<WorldPreset> preset = state.getWorldType().preset();
		if (preset != null && preset.is(PRESET)) {
			button.setTooltip(Tooltip.create(HINT));
		}
	}
}
