package pl.polishforests.gametest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.client.screen.PolandPresetEditor;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.surface.ChunkSurface;

/**
 * Screenshots of biome borders (rule Z10, step S8b, docs/03-m2-biomy.md §8.8, {@code docs/m2/przejscia/}): seven
 * borders in both scales, five in the natural-vegetation mode (oak-hornbeam forest and fresh pine forest, pine forest and
 * raised bog, upper montane spruce forest and dwarf pine scrub, gray dune and coastal crowberry pine forest, Carpathian
 * beech forest and upland fir forest) and two in the PRESENT_DAY mode (ash-alder or elm-ash floodplain forest and
 * meadow, forest and field). Each from above the side of the first habitat, looking across the border at its center
 * (the places and the direction from the first habitat to the second come from a search of the classifier), the forest
 * edges also from low above the open land, looking at the mantle and the fringe ({@code ecotone_<border>_edge_<scale>});
 * the camera stands above the ground and the crowns around it. Without the HUD, at noon in clear weather, with the default biome blend of the client. The test uses only classes that existed
 * before S8b, so the same file takes the "before" screenshots on the tree before the ecotones. Runs when
 * {@code -Dpolishforests.gametest} is {@code ecotones}; {@code -Pscales} picks one scale, {@code -Psites} some borders.
 */
public final class EcotoneClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int RENDER_DISTANCE = 10;

	/**
	 * A border: name, mode, center and direction from the first habitat to the second, per scale, and for a forest edge
	 * the side of the open land (1: the first habitat, 2: the second; 0: no forest edge), from which a second, low
	 * screenshot looks at the mantle and the fringe.
	 */
	private record Border(String name, boolean presentDay, int[] real, double[] realDir, int[] gameplay, double[] gameplayDir,
			int open) {
	}

	private static final Border[] BORDERS = {
			new Border("oak_hornbeam_fresh_pine", false, new int[] {-1_248, 1_160}, new double[] {-12.6, -15.9},
					new int[] {-368, 440}, new double[] {11.3, -7.3}, 0),
			new Border("pine_raised_bog", false, new int[] {-230_182, -137_746}, new double[] {11.4, 16.7},
					new int[] {-2_944, -872}, new double[] {-10.1, -8.5}, 2),
			new Border("spruce_dwarf_pine", false, new int[] {99_105, 1_034_181}, new double[] {-17.1, -11.1},
					new int[] {6_614, -33_516}, new double[] {3.6, 4.9}, 0),
			new Border("gray_dune_coastal_pine", false, new int[] {-233_142, -136_018}, new double[] {18.0, 9.9},
					new int[] {-4_304, -3_824}, new double[] {5.8, 12.1}, 1),
			new Border("beech_fir", false, new int[] {155_466, 1_059_066}, new double[] {-13.5, -15.2},
					new int[] {27_249, 3_702}, new double[] {13.4, 3.3}, 0),
			new Border("floodplain_meadow_present_day", true, new int[] {-3_376, 3_616}, new double[] {-18.7, -8.3},
					new int[] {128, 2_192}, new double[] {-12.0, -6.2}, 2),
			new Border("forest_field_present_day", true, new int[] {6_120, 24}, new double[] {-20.4, 2.2},
					new int[] {4_048, 1_536}, new double[] {-13.0, -3.3}, 2)};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!System.getProperty("polishforests.gametest", "all").equals("ecotones")) {
			return;
		}
		String scales = System.getProperty("polishforests.scales", "");
		String only = System.getProperty("polishforests.sites", "");
		context.runOnClient(mc -> mc.options.renderDistance().set(RENDER_DISTANCE));
		for (PolandScale scale : PolandScale.values()) {
			if (!scales.isBlank() && !scales.contains(scale.getSerializedName())) {
				continue;
			}
			boolean real = scale == PolandScale.REALISTIC;
			ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET,
					PolishForests.id(real ? "poland" : "poland_gameplay"));
			for (boolean presentDay : new boolean[] {false, true}) {
				try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset, presentDay))
						.create()) {
					sp.getServer().runCommand("gamerule advance_time false");
					sp.getServer().runCommand("gamerule advance_weather false");
					sp.getServer().runCommand("gamerule spawn_mobs false");
					sp.getServer().runCommand("time set 6000");
					sp.getServer().runCommand("weather clear");
					sp.getServer().runCommand("gamemode spectator @a");
					for (Border b : BORDERS) {
						if (b.presentDay() != presentDay || !only.isBlank() && !java.util.List.of(only.split(",")).contains(b.name())) {
							continue;
						}
						int[] center = real ? b.real() : b.gameplay();
						double[] dir = real ? b.realDir() : b.gameplayDir();
						shot(context, sp, center, dir, real ? 20 : 14, real ? 34 : 24,
								"ecotone_" + b.name() + "_" + scale.getSerializedName());
						if (b.open() != 0) {
							// From the open land, low, looking at the forest edge.
							double[] toForest = b.open() == 1 ? dir : new double[] {-dir[0], -dir[1]};
							shot(context, sp, center, toForest, real ? 5 : 4, real ? 22 : 16,
									"ecotone_" + b.name() + "_edge_" + scale.getSerializedName());
						}
					}
				}
			}
		}
	}

	private static void select(WorldCreationUiState ui, ResourceKey<WorldPreset> preset, boolean presentDay) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(preset)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier())));
		if (presentDay) {
			if (!(ui.getSettings().selectedDimensions().overworld() instanceof PolandChunkGenerator gen)) {
				throw new AssertionError("The preset " + preset.identifier() + " has no Poland generator");
			}
			ui.updateDimensions(PolandPresetEditor.apply(gen.settings().withAgriculture(true)));
		}
	}

	/**
	 * A screenshot from the side of the first habitat: the camera {@code back} blocks from the center against the direction
	 * {@code dir} and {@code above} blocks over the center's ground, looking at the center.
	 */
	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, int[] center, double[] dir, int above,
			int back, String screenshot) {
		double length = Math.hypot(dir[0], dir[1]);
		int cx = center[0] - (int) Math.round(back * dir[0] / length);
		int cz = center[1] - (int) Math.round(back * dir[1] / length);
		int groundY = sp.getServer().computeOnServer(s -> groundY(s, center[0], center[1]));
		// Above the ground at the camera too (terraced fields, slopes) and above any crown around the camera.
		int wanted = sp.getServer().computeOnServer(s -> Math.max(groundY, groundY(s, cx, cz)) + above);
		int y = sp.getServer().computeOnServer(s -> clear(s, cx, wanted, cz));
		double dx = center[0] - cx;
		double dz = center[1] - cz;
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		double pitch = Math.toDegrees(Math.atan2(y + 1.62 - (groundY + 1), Math.hypot(dx, dz)));
		String habitat = sp.getServer().computeOnServer(s -> habitat(s, center[0], center[1]));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", cx, y, cz, yaw, pitch));
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		context.waitTicks(20 * 30);
		PolishForests.LOG.info("[ecotones] {}: center ({}, {}) {}, camera ({}, {}, {}) yaw {} pitch {}, client {}", screenshot,
				center[0], center[1], habitat, cx, y, cz, String.format(Locale.ROOT, "%.1f", yaw),
				String.format(Locale.ROOT, "%.1f", pitch), context.computeOnClient(mc -> mc.level.getChunkSource().gatherStats()));
		context.waitTicks(20 * 10);
		context.takeScreenshot(screenshot);
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static PolandChunkGenerator generator(MinecraftServer server) {
		if (!(server.overworld().getChunkSource().getGenerator() instanceof PolandChunkGenerator gen)) {
			throw new AssertionError("The overworld does not use the Poland generator");
		}
		return gen;
	}

	private static String habitat(MinecraftServer server, int x, int z) {
		PolandChunkGenerator gen = generator(server);
		long seed = server.overworld().getSeed();
		LandscapeModel m = gen.model(seed);
		HabitatClassifier k = gen.classifier(seed);
		int code = k.classify(m.sample(x, z), x, z);
		return Habitat.biome(code).id() + "/" + Habitat.zone(code).id() + " (" + k.mode() + ")";
	}

	/** Lowest feet Y from {@code from} at (x, z) whose 3 × 3 blocks around the head and above it hold no leaves, logs or solid blocks. */
	private static int clear(MinecraftServer server, int x, int from, int z) {
		var level = server.overworld();
		for (int y = from; y < from + 64; y++) {
			boolean free = true;
			for (int dx = -1; dx <= 1 && free; dx++) {
				for (int dz = -1; dz <= 1 && free; dz++) {
					for (int dy = 0; dy <= 2 && free; dy++) {
						var s = level.getBlockState(new net.minecraft.core.BlockPos(x + dx, y + dy, z + dz));
						free = !s.is(net.minecraft.tags.BlockTags.LEAVES) && !s.is(net.minecraft.tags.BlockTags.LOGS) && !s.isSolidRender();
					}
				}
			}
			if (free) {
				return y;
			}
		}
		return from + 64;
	}

	/** Top ground block (or water) of the surface plan at (x, z). */
	private static int groundY(MinecraftServer server, int x, int z) {
		var level = server.overworld();
		level.getChunk(x >> 4, z >> 4);
		ChunkSurface plan = generator(server).surface(new ChunkPos(x >> 4, z >> 4), level.getMinY(), level.getMaxY(),
				level.getSeed(), null, level.structureManager());
		int i = ChunkHabitats.index(x & 15, z & 15);
		return plan.wet(i) ? plan.waterTop(i) : plan.top(i);
	}
}
