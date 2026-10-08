package pl.polishforests.gametest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.feature.TreeStandFeature;

/**
 * Performance comparison: average FPS and new-terrain load time in a vanilla world and in a "Poland"
 * world, with the same graphics settings and the same seed.
 * Runs when {@code -Dpolishforests.gametest} is {@code performance} or {@code all}.
 * The {@code stages} mode only measures generation stage times in three areas of each scale (M2
 * baseline, {@code docs/m2/pomiary-bazowe-m1.md}), without measuring FPS and without a vanilla world.
 */
public final class PerformanceClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int RENDER_DISTANCE = 12;
	private static final ResourceKey<WorldPreset> POLAND = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland"));
	private static final ResourceKey<WorldPreset> POLAND_GAMEPLAY = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland_gameplay"));

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polishforests.gametest", "all");
		if (!mode.equals("performance") && !mode.equals("all") && !mode.equals("stages")) {
			return;
		}
		boolean stagesOnly = mode.equals("stages");
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(RENDER_DISTANCE);
			mc.options.simulationDistance().set(8);
			mc.options.framerateLimit().set(260);
			mc.options.enableVsync().set(false);
			mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
		});
		if (!stagesOnly) {
			measure(context, "vanilla", null, false);
		}
		measure(context, "poland", POLAND, stagesOnly);
		measure(context, "poland_gameplay", POLAND_GAMEPLAY, stagesOnly);
	}

	private void measure(ClientGameTestContext context, String name, ResourceKey<WorldPreset> preset,
			boolean stagesOnly) {
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> configure(ui, preset)).create()) {
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("gamerule advance_weather false");
			sp.getServer().runCommand("gamerule spawn_mobs false");
			sp.getServer().runCommand("time set 6000");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamemode spectator @a");

			if (stagesOnly) {
				waitForIdleGeneration(context, name);
			}
			profileStages(sp, name, stagesOnly);
			if (stagesOnly) {
				return;
			}

			// Teleport to a new place 3 km from spawn, 30 blocks above ground, level view.
			int x = 3_000;
			int z = 3_000;
			int ground = sp.getServer().computeOnServer(s -> {
				ServerLevel level = s.overworld();
				return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
						level, level.getChunkSource().randomState());
			});
			long t0 = System.nanoTime();
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 45.0 8.0", x, ground + 30, z));
			// Wait until the client has loaded 90% of the chunks within render distance (at most 4 minutes).
			int target = (int) (0.9 * (2 * RENDER_DISTANCE + 1) * (2 * RENDER_DISTANCE + 1));
			int loaded = 0;
			for (int i = 0; i < 240 && loaded < target; i++) {
				context.waitTicks(20);
				loaded = context.computeOnClient(mc -> mc.level.getChunkSource().getLoadedChunksCount());
			}
			double loadSeconds = (System.nanoTime() - t0) / 1e9;
			PolishForests.LOG.info("[performance] {}: client has {} of {} chunks after {} s", name, loaded, target,
					String.format(Locale.ROOT, "%.1f", loadSeconds));
			// Give the client time to build chunk meshes.
			context.waitTicks(20 * 15);
			double sum = 0;
			int min = Integer.MAX_VALUE;
			int samples = 15;
			for (int i = 0; i < samples; i++) {
				context.waitTicks(20);
				int fps = context.computeOnClient(mc -> mc.getFps());
				sum += fps;
				min = Math.min(min, fps);
			}
			PolishForests.LOG.info("[performance] {}: new terrain loaded in {} s, FPS average {}, minimum {}", name,
					String.format(Locale.ROOT, "%.1f", loadSeconds), Math.round(sum / samples), min);
			context.takeScreenshot("performance_" + name);
		}
	}

	/**
	 * 8 × 8 chunk areas for the stage measurement: north-west corner in blocks, seed {@link #SEED}. The first one,
	 * "lowland", is the same spot as in M1 (flat lowland without rivers in both scales), so the results can be
	 * compared with earlier ones. The {@code stages} mode also measures the interior of the Beskids and a large
	 * lowland river: that is where M2 adds the most work (altitudinal belts, waterside zones, bank shelf). Centers
	 * taken from the {@code beskids} and {@code large_river} patches in
	 * {@code src/test/resources/golden_terrain_m1.txt}, far from the spawn point.
	 */
	private static final int[][] SPOTS_REAL = {{-40_000, 25_000}, {154_834 - 64, 1_058_738 - 64},
			{-19_484 - 64, 11_253 - 64}};
	private static final int[][] SPOTS_GAMEPLAY = {{-40_000, 25_000}, {27_609 - 64, 3_254 - 64},
			{-1_851 - 64, 6_022 - 64}};
	private static final String[] SPOT_NAMES = {"lowland", "beskids", "river"};

	/**
	 * Time to generate 64 chunks up to successive stages, on the server thread, in a new area. The model
	 * and fill counters ({@code PolandChunkGenerator.SAMPLE_NANOS}, {@code FILL_NANOS}, {@code CHUNKS})
	 * are static and grow for the whole process (both worlds, world startup), so only the increase during
	 * the measurement of the given area is logged. The increase also covers neighboring chunks needed for
	 * decoration and light, and chunks loaded in the background around the player at the same time.
	 */
	private static void profileStages(TestSingleplayerContext sp, String name, boolean allSpots) {
		boolean real = name.equals("poland");
		int[][] spots = real ? SPOTS_REAL : SPOTS_GAMEPLAY;
		for (int i = 0; i < (allSpots ? spots.length : 1); i++) {
			int[] spot = spots[i];
			String label = i == 0 ? name : name + " / " + SPOT_NAMES[i];
			sp.getServer().runOnServer(server -> profileSpot(server.overworld(), label, spot[0] >> 4, spot[1] >> 4));
		}
	}

	/**
	 * Waits until the world start has finished generating the chunks around the spawn (no new terrain for 3 s, at most
	 * 3 minutes), so the first area is not measured together with them (S5 review: the lowland area competed with about
	 * 1070 world-start chunks).
	 */
	private static void waitForIdleGeneration(ClientGameTestContext context, String name) {
		long start = System.nanoTime();
		long before = PolandChunkGenerator.CHUNKS.sum();
		for (int i = 0; i < 60; i++) {
			long last = PolandChunkGenerator.CHUNKS.sum();
			context.waitTicks(60);
			if (PolandChunkGenerator.CHUNKS.sum() == last) {
				break;
			}
		}
		PolishForests.LOG.info("[performance] {}: world start generated {} more chunks with terrain, waited {} s before the measurement", name,
				PolandChunkGenerator.CHUNKS.sum() - before, String.format(Locale.ROOT, "%.0f", (System.nanoTime() - start) / 1e9));
	}

	private static void profileSpot(ServerLevel level, String name, int ox, int oz) {
		ChunkStatus[] stages = {ChunkStatus.STRUCTURE_STARTS, ChunkStatus.BIOMES, ChunkStatus.TERRAIN,
				ChunkStatus.FEATURES, ChunkStatus.LIGHT, ChunkStatus.FULL};
		long sample0 = PolandChunkGenerator.SAMPLE_NANOS.sum();
		long fill0 = PolandChunkGenerator.FILL_NANOS.sum();
		long chunks0 = PolandChunkGenerator.CHUNKS.sum();
		long biome0 = PolandChunkGenerator.BIOME_NANOS.sum();
		long biomeChunks0 = PolandChunkGenerator.BIOME_CHUNKS.sum();
		long classify0 = PolandChunkGenerator.CLASSIFY_NANOS.sum();
		long tree0 = TreeStandFeature.NANOS.sum();
		long treeChunks0 = TreeStandFeature.CHUNKS.sum();
		long trees0 = TreeStandFeature.TREES.sum();
		long miss0 = ModFeatures.HABITAT_MISS.sum();
		long surface0 = PolandChunkGenerator.SURFACE_NANOS.sum();
		long packed0 = PolandChunkGenerator.PACKED_SECTIONS.sum();
		long fallbacks0 = PolandChunkGenerator.PACK_FALLBACKS.sum();
		long outside0 = pl.polishforests.worldgen.surface.SurfaceBuilder.OUTSIDE_SAMPLES.sum();
		long hits0 = pl.polishforests.worldgen.surface.SurfaceBuilder.OUTSIDE_HITS.sum();
		long reused0 = pl.polishforests.worldgen.surface.SurfaceBuilder.REUSED_SAMPLES.sum();
		StringBuilder sb = new StringBuilder();
		for (ChunkStatus stage : stages) {
			long t0 = System.nanoTime();
			for (int cx = 0; cx < 8; cx++) {
				for (int cz = 0; cz < 8; cz++) {
					level.getChunkSource().getChunk(ox + cx, oz + cz, stage, true);
				}
			}
			sb.append(stage.getName()).append('=').append(Math.round((System.nanoTime() - t0) / 1e6)).append(" ms ");
		}
		PolishForests.LOG.info("[performance] {}: stages for 64 chunks (incremental): {}", name, sb);
		long n = PolandChunkGenerator.CHUNKS.sum() - chunks0;
		if (n > 0) {
			PolishForests.LOG.info("[performance] {}: terrain of {} chunks during the measurement, sampling {} ms/chunk (model {}, "
					+ "classification {}), filling {} ms/chunk", name, n,
					String.format(Locale.ROOT, "%.2f", (PolandChunkGenerator.SAMPLE_NANOS.sum() - sample0) / 1e6 / n),
					String.format(Locale.ROOT, "%.2f", (PolandChunkGenerator.SAMPLE_NANOS.sum() - sample0
							- (PolandChunkGenerator.CLASSIFY_NANOS.sum() - classify0)) / 1e6 / n),
					String.format(Locale.ROOT, "%.2f", (PolandChunkGenerator.CLASSIFY_NANOS.sum() - classify0) / 1e6 / n),
					String.format(Locale.ROOT, "%.2f", (PolandChunkGenerator.FILL_NANOS.sum() - fill0) / 1e6 / n));
			long biomeChunks = Math.max(1, PolandChunkGenerator.BIOME_CHUNKS.sum() - biomeChunks0);
			long treeChunks = Math.max(1, TreeStandFeature.CHUNKS.sum() - treeChunks0);
			PolishForests.LOG.info(String.format(Locale.ROOT, "[performance] %s: BIOMES %.3f ms/chunk (%d chunks), classification in "
					+ "fill %.3f ms/chunk, tree stand %.2f ms/chunk (%d chunks, %.1f trees/chunk), habitats missing in %d chunks", name,
					(PolandChunkGenerator.BIOME_NANOS.sum() - biome0) / 1e6 / biomeChunks, biomeChunks,
					(PolandChunkGenerator.CLASSIFY_NANOS.sum() - classify0) / 1e6 / n,
					(TreeStandFeature.NANOS.sum() - tree0) / 1e6 / treeChunks, treeChunks,
					(double) (TreeStandFeature.TREES.sum() - trees0) / treeChunks, ModFeatures.HABITAT_MISS.sum() - miss0));
			PolishForests.LOG.info(String.format(Locale.ROOT, "[performance] %s: surface plan in fill %.3f ms/chunk (soil, shelf, "
					+ "micro-relief; %.1f neighbor samples/chunk, %.1f neighbor summaries from the cache/chunk, %.1f samples "
					+ "reused/chunk), PACK_FALLBACKS %d of %d packed sections", name,
					(PolandChunkGenerator.SURFACE_NANOS.sum() - surface0) / 1e6 / n,
					(double) (pl.polishforests.worldgen.surface.SurfaceBuilder.OUTSIDE_SAMPLES.sum() - outside0) / n,
					(double) (pl.polishforests.worldgen.surface.SurfaceBuilder.OUTSIDE_HITS.sum() - hits0) / n,
					(double) (pl.polishforests.worldgen.surface.SurfaceBuilder.REUSED_SAMPLES.sum() - reused0) / n,
					PolandChunkGenerator.PACK_FALLBACKS.sum() - fallbacks0, PolandChunkGenerator.PACKED_SECTIONS.sum() - packed0));
		} else if (name.startsWith("poland")) {
			PolishForests.LOG.warn("[performance] {}: area was already generated, measurement invalid", name);
		}
	}

	private static void configure(WorldCreationUiState ui, ResourceKey<WorldPreset> preset) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		if (preset == null) {
			return;
		}
		for (WorldCreationUiState.WorldTypeEntry entry : ui.getNormalPresetList()) {
			if (entry.preset().is(preset)) {
				ui.setWorldType(entry);
				return;
			}
		}
		throw new AssertionError("Missing preset " + preset.identifier());
	}
}
