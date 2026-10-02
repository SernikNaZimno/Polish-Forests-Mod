package pl.polskielasy.gametest;

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
import pl.polskielasy.PolskieLasy;
import pl.polskielasy.client.screen.PolskaWorldOptionsScreen;
import pl.polskielasy.command.PolskaCommands;
import pl.polskielasy.worldgen.chunk.PolskaChunkGenerator;
import pl.polskielasy.worldgen.chunk.PolskaScale;

/**
 * Ekran opcji generowania i komendy. Uruchamiany, gdy {@code -Dpolskielasy.gametest} to
 * {@code ui} lub {@code wszystko}.
 */
public final class UiAndCommandsClientGameTest implements FabricClientGameTest {
	private static final ResourceKey<WorldPreset> POLSKA = ResourceKey.create(Registries.WORLD_PRESET,
			PolskieLasy.id("polska"));
	private static final ResourceKey<WorldPreset> POLSKA_GAMEPLAY = ResourceKey.create(Registries.WORLD_PRESET,
			PolskieLasy.id("polska_rozgrywka"));

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polskielasy.gametest", "wszystko");
		if (!mode.equals("ui") && !mode.equals("wszystko")) {
			return;
		}
		optionsScreen(context, POLSKA, PolskaScale.REALISTYCZNA, "ui_opcje_realistyczna");
		optionsScreen(context, POLSKA_GAMEPLAY, PolskaScale.ROZGRYWKA, "ui_opcje_rozgrywka");
		commands(context);
	}

	private void optionsScreen(ClientGameTestContext context, ResourceKey<WorldPreset> preset, PolskaScale expected,
			String screenshot) {
		context.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(null)));
		context.waitForScreen(CreateWorldScreen.class);
		context.runOnClient(mc -> {
			CreateWorldScreen screen = (CreateWorldScreen) mc.gui.screen();
			WorldCreationUiState ui = screen.getUiState();
			WorldCreationUiState.WorldTypeEntry entry = ui.getNormalPresetList().stream()
					.filter(e -> e.preset().is(preset)).findFirst()
					.orElseThrow(() -> new AssertionError("Brak typu świata " + preset.identifier()));
			ui.setWorldType(entry);
			PresetEditor editor = ui.getPresetEditor();
			if (editor == null) {
				throw new AssertionError("Typ świata " + preset.identifier() + " nie ma edytora opcji");
			}
			mc.gui.setScreen(editor.createEditScreen(screen, ui.getSettings()));
		});
		context.waitForScreen(PolskaWorldOptionsScreen.class);
		context.waitTicks(10);
		context.takeScreenshot(screenshot);
		context.clickScreenButton("gui.done");
		context.waitForScreen(CreateWorldScreen.class);
		String result = context.computeOnClient(mc -> {
			WorldDimensions dims = ((CreateWorldScreen) mc.gui.screen()).getUiState().getSettings().selectedDimensions();
			if (!(dims.overworld() instanceof PolskaChunkGenerator gen)) {
				return "generator nie jest generatorem Polska";
			}
			if (gen.settings().scale() != expected) {
				return "skala " + gen.settings().scale() + " zamiast " + expected;
			}
			LevelStem stem = dims.dimensions().get(LevelStem.OVERWORLD);
			if (!stem.type().is(expected.dimensionType())) {
				return "typ wymiaru " + stem.type().unwrapKey() + " zamiast " + expected.dimensionType();
			}
			if (stem.type().value().height() != expected.vertical().height()) {
				return "wysokość wymiaru " + stem.type().value().height();
			}
			return null;
		});
		if (result != null) {
			throw new AssertionError(preset.identifier() + ": " + result);
		}
		PolskieLasy.LOG.info("[ui] {}: ekran opcji działa, skala {}, wymiar o wysokości {}", preset.identifier(),
				expected.getSerializedName(), expected.vertical().height());
		context.setScreen(() -> null);
	}

	private void commands(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> {
			ui.setSeed("20260927");
			ui.setAllowCommands(true);
			ui.getNormalPresetList().stream().filter(e -> e.preset().is(POLSKA_GAMEPLAY)).findFirst()
					.ifPresent(ui::setWorldType);
		}).create()) {
			sp.getServer().runCommand("polskielasy lista");
			sp.getServer().runCommand("polskielasy tutaj");
			for (PolskaCommands.Target target : PolskaCommands.Target.values()) {
				sp.getServer().runCommand("polskielasy znajdz " + target.id());
			}
			sp.getServer().runCommand("polskielasy wysokosc 300 500");
			sp.getServer().runCommand("polskielasy najwyzszy 5");
			context.waitTicks(20 * 60);
		}
	}
}
