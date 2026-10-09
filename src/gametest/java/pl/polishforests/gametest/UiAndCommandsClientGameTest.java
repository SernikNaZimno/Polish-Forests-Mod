package pl.polishforests.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.client.screen.PolandWorldOptionsScreen;
import pl.polishforests.command.PolishForestsCommands;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.habitat.HabitatClassifier;

/**
 * World generation options screen (scale, dimension type, and from step S8 the landscape switch to the "present-day
 * Poland" mode) and commands. Runs when {@code -Dpolishforests.gametest} is
 * {@code ui} or {@code all}.
 */
public final class UiAndCommandsClientGameTest implements FabricClientGameTest {
	private static final ResourceKey<WorldPreset> POLAND = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland"));
	private static final ResourceKey<WorldPreset> POLAND_GAMEPLAY = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland_gameplay"));

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polishforests.gametest", "all");
		if (!mode.equals("ui") && !mode.equals("all")) {
			return;
		}
		optionsScreen(context, POLAND, PolandScale.REALISTIC, "ui_options_realistic");
		optionsScreen(context, POLAND_GAMEPLAY, PolandScale.GAMEPLAY, "ui_options_gameplay");
		commands(context);
	}

	private void optionsScreen(ClientGameTestContext context, ResourceKey<WorldPreset> preset, PolandScale expected,
			String screenshot) {
		context.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(null)));
		context.waitForScreen(CreateWorldScreen.class);
		context.runOnClient(mc -> {
			CreateWorldScreen screen = (CreateWorldScreen) mc.gui.screen();
			WorldCreationUiState ui = screen.getUiState();
			WorldCreationUiState.WorldTypeEntry entry = ui.getNormalPresetList().stream()
					.filter(e -> e.preset().is(preset)).findFirst()
					.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier()));
			ui.setWorldType(entry);
			PresetEditor editor = ui.getPresetEditor();
			if (editor == null) {
				throw new AssertionError("World type " + preset.identifier() + " has no options editor");
			}
			mc.gui.setScreen(editor.createEditScreen(screen, ui.getSettings()));
		});
		context.waitForScreen(PolandWorldOptionsScreen.class);
		context.waitTicks(10);
		context.takeScreenshot(screenshot);
		// Step S8: the landscape switch turns the "present-day Poland" mode on (the presets start in the natural mode).
		context.clickScreenButton("polishforests.options.landscape");
		context.waitTicks(2);
		context.takeScreenshot(screenshot + "_present_day");
		context.clickScreenButton("gui.done");
		context.waitForScreen(CreateWorldScreen.class);
		String result = context.computeOnClient(mc -> {
			WorldDimensions dims = ((CreateWorldScreen) mc.gui.screen()).getUiState().getSettings().selectedDimensions();
			if (!(dims.overworld() instanceof PolandChunkGenerator gen)) {
				return "generator is not the Poland generator";
			}
			if (gen.settings().scale() != expected) {
				return "scale " + gen.settings().scale() + " instead of " + expected;
			}
			if (!gen.settings().agriculture() || gen.settings().mode() != HabitatClassifier.Mode.PRESENT_DAY) {
				return "the landscape switch did not select the present-day mode: " + gen.settings();
			}
			LevelStem stem = dims.dimensions().get(LevelStem.OVERWORLD);
			if (!stem.type().is(expected.dimensionType())) {
				return "dimension type " + stem.type().unwrapKey() + " instead of " + expected.dimensionType();
			}
			if (stem.type().value().height() != expected.vertical().height()) {
				return "dimension height " + stem.type().value().height();
			}
			return null;
		});
		if (result != null) {
			throw new AssertionError(preset.identifier() + ": " + result);
		}
		PolishForests.LOG.info("[ui] {}: options screen works, scale {}, dimension height {}, landscape switch selects the "
				+ "present-day mode", preset.identifier(), expected.getSerializedName(), expected.vertical().height());
		context.setScreen(() -> null);
	}

	private void commands(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> {
			ui.setSeed("20260927");
			ui.setAllowCommands(true);
			ui.getNormalPresetList().stream().filter(e -> e.preset().is(POLAND_GAMEPLAY)).findFirst()
					.ifPresent(ui::setWorldType);
		}).create()) {
			sp.getServer().runCommand("polishforests list");
			sp.getServer().runCommand("polishforests here");
			for (PolishForestsCommands.Target target : PolishForestsCommands.Target.values()) {
				sp.getServer().runCommand("polishforests find " + target.id());
			}
			sp.getServer().runCommand("polishforests elevation 300 500");
			sp.getServer().runCommand("polishforests highest 5");
			context.waitTicks(20 * 60);
		}
	}
}
