package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.PolishForests;
import pl.polishforests.client.HabitatDebugEntry;
import pl.polishforests.client.screen.PolandPresetEditor;
import pl.polishforests.client.screen.PolandWorldOptionsScreen;
import pl.polishforests.command.PolishForestsCommands;
import pl.polishforests.worldgen.chunk.LooseCover;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Zone;

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

	/** Fails when the options column runs over the header or the footer of the screen (step K1, review round 1). */
	private static void assertOptionsFit(ClientGameTestContext context, String frame) {
		String problem = context.computeOnClient(mc -> mc.gui.screen() instanceof PolandWorldOptionsScreen screen
				&& !screen.contentsFit()
						? "the options do not fit between the header and the footer at " + mc.getWindow().getWidth() + "x"
								+ mc.getWindow().getHeight() + " (GUI " + screen.width + "x" + screen.height + ")"
						: null);
		if (problem != null) {
			throw new AssertionError(frame + ": " + problem);
		}
	}

	/** Switches the client language without a full resource reload and rebuilds the open screen. */
	private static void setLanguage(ClientGameTestContext context, String code) {
		context.runOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
			mc.gui.screen().resize(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
		});
		context.waitTicks(5);
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
		assertOptionsFit(context, screenshot);
		context.takeScreenshot(screenshot);
		if (expected == PolandScale.REALISTIC) {
			// Step K1, review round 1: the labels have a fixed width, but the Polish frame is checked too.
			setLanguage(context, "pl_pl");
			assertOptionsFit(context, screenshot + "_pl");
			context.takeScreenshot(screenshot + "_pl");
			setLanguage(context, "en_us");
		}
		// Step S8: the landscape switch turns the "present-day Poland" mode on (the presets start in the natural mode).
		context.clickScreenButton("polishforests.options.landscape");
		// Step K1: the presets start with the thin loose cover; the switch selects the realistic one.
		context.clickScreenButton("polishforests.options.loose_cover");
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
			if (gen.settings().looseCover() != LooseCover.REALISTIC) {
				return "the loose cover switch did not select the realistic cover: " + gen.settings();
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
				+ "present-day mode, loose cover switch selects the realistic cover", preset.identifier(),
				expected.getSerializedName(), expected.vertical().height());
		context.setScreen(() -> null);
	}

	/**
	 * Every command and every target (step S9: also every biome and zone of {@code find biome} and {@code find zone}) in
	 * a gameplay-scale world of the natural mode, the meadows, fields and alder tree rows in a world of the present-day
	 * mode; each search must end with "found" (in the natural mode, the targets of the present-day mode with "does not
	 * occur in this mode"), {@code here} must have the habitat line, and the F3 overlay must show the habitat entry.
	 */
	private void commands(ClientGameTestContext context) {
		List<String> problems = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, false)).create()) {
			context.waitTicks(20);
			MinecraftServer[] server = new MinecraftServer[1];
			sp.getServer().runOnServer(s -> server[0] = s);
			List<Component> list = run(context, server[0], "polishforests list", 1);
			int expectedLines = 7;
			if (list.size() != expectedLines) {
				problems.add("list: " + list.size() + " lines instead of " + expectedLines);
			}
			List<Component> here = run(context, server[0], "polishforests here", 2);
			if (here.size() != 2 || !key(here.get(1)).equals("polishforests.command.here_habitat")) {
				problems.add("here: no habitat line: " + here.stream().map(Component::getString).toList());
			} else {
				PolishForests.LOG.info("[ui] here: {} / {}", here.get(0).getString(), here.get(1).getString());
			}
			List<String> commands = new ArrayList<>();
			for (PolishForestsCommands.Target target : PolishForestsCommands.Target.values()) {
				commands.add("polishforests find " + target.id());
			}
			for (HabitatBiome b : HabitatBiome.values()) {
				commands.add("polishforests find biome " + b.id());
			}
			for (Zone z : Zone.values()) {
				if (z != Zone.NONE) {
					commands.add("polishforests find zone " + z.id());
				}
			}
			commands.add("polishforests elevation 300 500");
			commands.add("polishforests highest 5");
			for (String command : commands) {
				boolean presentDayOnly = command.endsWith(" wet_meadow") || command.endsWith(" hay_meadow")
						|| command.endsWith(" arable_land") || command.endsWith(" tree_row");
				String expected = presentDayOnly ? "polishforests.command.not_in_mode"
						: command.endsWith(" montane_tall_herbs") ? "polishforests.command.not_in_scale" : "polishforests.command.found";
				search(context, server[0], command, expected, problems);
			}
			f3(context, problems);
		}
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, true)).create()) {
			MinecraftServer[] server = new MinecraftServer[1];
			sp.getServer().runOnServer(s -> server[0] = s);
			for (String command : List.of("polishforests find biome wet_meadow", "polishforests find biome hay_meadow",
					"polishforests find biome arable_land", "polishforests find zone tree_row", "polishforests find peatland")) {
				search(context, server[0], command, "polishforests.command.found", problems);
			}
		}
		if (!problems.isEmpty()) {
			throw new AssertionError("Commands: " + String.join("; ", problems));
		}
	}

	/** The gameplay-scale world of the test seed, in the present-day mode or in the natural mode of the preset. */
	private static void select(WorldCreationUiState ui, boolean presentDay) {
		ui.setSeed("20260927");
		ui.setAllowCommands(true);
		ui.getNormalPresetList().stream().filter(e -> e.preset().is(POLAND_GAMEPLAY)).findFirst().ifPresent(ui::setWorldType);
		if (presentDay && ui.getSettings().selectedDimensions().overworld() instanceof PolandChunkGenerator gen) {
			ui.updateDimensions(PolandPresetEditor.apply(gen.settings().withAgriculture(true)));
		}
	}

	/** Runs a search command and waits for its result: the translation key {@code expected}, or a problem. */
	private static void search(ClientGameTestContext context, MinecraftServer server, String command, String expected,
			List<String> problems) {
		long t0 = System.nanoTime();
		List<Component> messages = run(context, server, command, expected.endsWith("found") ? 2 : 1);
		double s = (System.nanoTime() - t0) / 1e9;
		Component last = messages.isEmpty() ? null : messages.getLast();
		String result = last == null ? "no message" : last.getString();
		// S9 review round 1: a place found by a search cut short at 60 s counts as found (its nearness is unconfirmed).
		boolean unconfirmed = last != null && expected.equals("polishforests.command.found")
				&& key(last).equals("polishforests.command.found_unconfirmed");
		if (unconfirmed) {
			PolishForests.LOG.warn("[ui] /{}: found, but the search stopped at the time limit", command);
		} else if (last == null || !key(last).equals(expected)) {
			problems.add("/" + command + ": " + result);
		}
		PolishForests.LOG.info(String.format(Locale.ROOT, "[ui] /%s: %s (%.1f s)", command, result, s));
	}

	/** Runs a command on the server and waits (at most {@link #COMMAND_WAIT_TICKS}) until it has sent {@code count} messages. */
	private static List<Component> run(ClientGameTestContext context, MinecraftServer server, String command, int count) {
		List<Component> messages = new CopyOnWriteArrayList<>();
		CommandSourceStack source = server.createCommandSourceStack().withSource(capture(messages)).withLevel(server.overworld())
				.withPosition(new Vec3(0.5, 100, 0.5));
		server.execute(() -> server.getCommands().performPrefixedCommand(source, command));
		for (int t = 0; t < COMMAND_WAIT_TICKS && (messages.size() < count || failed(messages)); t++) {
			if (failed(messages)) {
				break;
			}
			context.waitTick();
		}
		return List.copyOf(messages);
	}

	/** Longest wait for a command (ticks): the searches stop after {@code SEARCH_LIMIT_MS} (60 s). */
	private static final int COMMAND_WAIT_TICKS = 20 * 75;

	private static boolean failed(List<Component> messages) {
		return messages.stream().anyMatch(m -> key(m).startsWith("polishforests.command.") && FAILURES.contains(key(m)));
	}

	private static final java.util.Set<String> FAILURES = java.util.Set.of("polishforests.command.not_found",
			"polishforests.command.search_failed", "polishforests.command.search_timeout", "polishforests.command.not_in_mode",
			"polishforests.command.not_in_scale", "polishforests.command.unknown_target", "polishforests.command.unknown_biome",
			"polishforests.command.unknown_zone", "polishforests.command.not_poland");

	/** Translation key of a message; a failure message wraps it in an empty, red component. */
	private static String key(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		for (Component sibling : c.getSiblings()) {
			String k = key(sibling);
			if (!k.isEmpty()) {
				return k;
			}
		}
		return "";
	}

	/** The F3 overlay shows the habitat entry without enabling it in the debug options (step S9). */
	private static void f3(ClientGameTestContext context, List<String> problems) {
		context.runOnClient(mc -> mc.debugEntries.setOverlayVisible(true));
		context.waitTicks(20);
		context.takeScreenshot("ui_f3_habitat");
		boolean shown = context.computeOnClient(mc -> mc.debugEntries.isCurrentlyEnabled(HabitatDebugEntry.ID));
		context.runOnClient(mc -> mc.debugEntries.setOverlayVisible(false));
		if (!shown) {
			problems.add("F3: the habitat entry is not in the overlay");
		}
		PolishForests.LOG.info("[ui] F3 overlay shows {}: {}", HabitatDebugEntry.ID, shown);
	}

	/** A command source that collects the messages. */
	private static CommandSource capture(List<Component> messages) {
		return new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				messages.add(message);
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
	}
}
