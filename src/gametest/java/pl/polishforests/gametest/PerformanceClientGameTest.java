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
import pl.polishforests.client.screen.PolandPresetEditor;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.feature.TreeStandFeature;

/**
 * Performance comparison: average FPS and new-terrain load time in a vanilla world and in a "Poland"
 * world, with the same graphics settings and the same seed.
 * Runs when {@code -Dpolishforests.gametest} is {@code performance} or {@code all}.
 * The {@code stages} mode only measures generation stage times in three areas of each scale (M2
 * baseline, {@code docs/m2/pomiary-bazowe-m1.md}), without measuring FPS and without a vanilla world; since step S10 in
 * both vegetation modes (natural and present-day) and with a fourth "area" sample for the TERRAIN budget of the whole area
 * (M2-11): six contiguous 8 × 8 areas (384 chunks) at pseudo-random places in the D1 area.
 *
 * <p>Step V0 of the vertical slice (docs/04-pionowy-wycinek.md §3): with {@code -Psites=slice} the {@code stages} mode
 * measures 8 × 8 chunk areas centered on the places of the slice ({@link SliceSites}: two oak-hornbeam forests, two fresh
 * pine forests and the middle of the transect, per scale and mode) instead of the M2 areas and the "area" sample; the
 * labels are {@code <world> / slice <place>}. The baseline of M2 for the A/B comparison of step V10 is the git tag
 * {@code m2-baseline-r1}.
 *
 * <p>Step V10: the stages also log the wood and leaves per chunk of each area and, per world, the used heap and the block
 * states; {@code -Pgametest=performance -Psites=slice} measures the load of new terrain and a fixed camera flight in the
 * forests of the slice and {@code -Psites=light} the light under the canopy and the spawns of monsters at noon
 * ({@link ForestPerformance}).
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
		String sites = System.getProperty("polishforests.sites", "");
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(RENDER_DISTANCE);
			mc.options.simulationDistance().set(8);
			mc.options.framerateLimit().set(260);
			mc.options.enableVsync().set(false);
			mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
		});
		if (mode.equals("performance") && sites.equals("slice_fastleaves")) {
			// Diagnosis of step V10: the flights with the leaves drawn without cutout (inner faces of leaves hidden), to see
			// how much of the frame time the leaf faces take.
			context.runOnClient(mc -> mc.options.cutoutLeaves().set(false));
			sites = "slice";
		}
		if (mode.equals("performance") && (sites.equals("slice") || sites.equals("light"))) {
			// Step V10: the flights and the load of new terrain in the forests of the slice, or the light under the canopy.
			boolean light = sites.equals("light");
			if (light) {
				// A default (noise) vanilla world: the plain world builder of the game tests makes a superflat one.
				forest(context, "vanilla", net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL, true);
			}
			// -Pscales=realistic|gameplay: one scale only.
			String scales = System.getProperty("polishforests.scales", "");
			if (!scales.equals("gameplay")) {
				forest(context, "poland", POLAND, light);
			}
			if (!scales.equals("realistic")) {
				forest(context, "poland_gameplay", POLAND_GAMEPLAY, light);
			}
			return;
		}
		if (!stagesOnly) {
			measure(context, "vanilla", null, false, false);
		}
		measure(context, "poland", POLAND, stagesOnly, false);
		measure(context, "poland_gameplay", POLAND_GAMEPLAY, stagesOnly, false);
		if (stagesOnly) {
			// Step S10: the same areas in the present-day mode (forest mask, fields and meadows).
			measure(context, "poland_present_day", POLAND, true, true);
			measure(context, "poland_gameplay_present_day", POLAND_GAMEPLAY, true, true);
		}
	}

	private void measure(ClientGameTestContext context, String name, ResourceKey<WorldPreset> preset,
			boolean stagesOnly, boolean presentDay) {
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> configure(ui, preset, presentDay))
				.create()) {
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("gamerule advance_weather false");
			sp.getServer().runCommand("gamerule spawn_mobs false");
			sp.getServer().runCommand("time set 6000");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamemode spectator @a");

			if (stagesOnly) {
				waitForIdleGeneration(context, name);
			}
			if (stagesOnly && System.getProperty("polishforests.sites", "").equals("slice")) {
				profileSlice(sp, name, preset == POLAND, presentDay);
				PolishForests.LOG.info("[performance] {}: {}", name, sp.getServer().computeOnServer(ForestPerformance::memoryAndStates));
				return;
			}
			profileStages(sp, name, preset == POLAND, stagesOnly);
			if (stagesOnly) {
				PolishForests.LOG.info("[performance] {}: {}", name, sp.getServer().computeOnServer(ForestPerformance::memoryAndStates));
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
			for (int i = 0; i < 2400 && loaded < target; i++) {
				context.waitTicks(2);
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
	/**
	 * Step V10: a world of the scale (natural mode) or a vanilla one: the load and flight measurements in the forests of
	 * the slice ({@link ForestPerformance#flights}), or with {@code light} the light at the ground under the canopy at noon
	 * and a live spawn test (PW-13).
	 */
	private void forest(ClientGameTestContext context, String name, ResourceKey<WorldPreset> preset, boolean light) {
		boolean poland = preset == POLAND || preset == POLAND_GAMEPLAY;
		// The consistent settings of the game test world builder make a superflat world; the vanilla forest needs a normal one.
		try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(poland).adjustSettings(ui -> configure(ui, preset,
				false)).create()) {
			PolishForests.LOG.info("[performance] {}: generator {}", name, sp.getServer().computeOnServer(s -> s.overworld().getChunkSource()
					.getGenerator().getClass().getSimpleName()));
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("gamerule advance_weather false");
			sp.getServer().runCommand("gamerule spawn_mobs false");
			sp.getServer().runCommand("time set 6000");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamemode spectator @a");
			waitForIdleGeneration(context, name);
			PolishForests.LOG.info("[performance] {}: {}", name, sp.getServer().computeOnServer(ForestPerformance::memoryAndStates));
			if (!light) {
				ForestPerformance.flights(context, sp, name, preset == POLAND);
				return;
			}
			if (preset != POLAND && preset != POLAND_GAMEPLAY) {
				for (boolean dark : new boolean[] {true, false}) {
					String label = name + " / " + (dark ? "dark_forest" : "forest");
					int[] at = sp.getServer().computeOnServer(s -> ForestPerformance.vanillaPlace(s.overworld(), dark));
					if (at == null) {
						PolishForests.LOG.warn("[performance] {}: biome not found", label);
						continue;
					}
					PolishForests.LOG.info("[performance] {}: at ({}, {})", label, at[0], at[1]);
					PolishForests.LOG.info(sp.getServer().computeOnServer(s -> ForestPerformance.groundLight(s.overworld(), label, at[0], at[1])));
					ForestPerformance.liveSpawns(context, sp, label, at[0], at[1], LIVE_SPAWN_TICKS);
				}
				return;
			}
			for (SliceSites.Place p : SliceSites.of(preset == POLAND, false)) {
				if (p.name().equals("transect")) {
					continue;
				}
				int[] m = p.middle();
				String label = name + " / " + p.name();
				PolishForests.LOG.info(sp.getServer().computeOnServer(s -> ForestPerformance.groundLight(s.overworld(), label, m[0], m[1])));
				if (p.name().endsWith("_1")) {
					ForestPerformance.liveSpawns(context, sp, label, m[0], m[1], LIVE_SPAWN_TICKS);
				}
			}
		}
	}

	/** Ticks of the live spawn test of step V10 (PW-13). */
	private static final int LIVE_SPAWN_TICKS = 2400;

	private static final int[][] SPOTS_REAL = {{-40_000, 25_000}, {154_834 - 64, 1_058_738 - 64},
			{-19_484 - 64, 11_253 - 64}};
	private static final int[][] SPOTS_GAMEPLAY = {{-40_000, 25_000}, {27_609 - 64, 3_254 - 64},
			{-1_851 - 64, 6_022 - 64}};
	private static final String[] SPOT_NAMES = {"lowland", "beskids", "river"};
	/**
	 * Half size of the "area" sample in blocks (step S10, budget M2-11: TERRAIN for the whole area, like D1): the same area
	 * as the "whole area" of {@code SampleCostTest}, five macroregions around (0, 0) (REAL about ±320 km, GAMEPLAY ±7 km),
	 * six contiguous 8 × 8 areas (384 chunks) at fixed pseudo-random places.
	 */
	private static final int AREA_HALF_REAL = 320_000;
	private static final int AREA_HALF_GAMEPLAY = 7_000;

	/**
	 * Time to generate 64 chunks up to successive stages, on the server thread, in a new area. The model
	 * and fill counters ({@code PolandChunkGenerator.SAMPLE_NANOS}, {@code FILL_NANOS}, {@code CHUNKS})
	 * are static and grow for the whole process (both worlds, world startup), so only the increase during
	 * the measurement of the given area is logged. The increase also covers neighboring chunks needed for
	 * decoration and light, and chunks loaded in the background around the player at the same time.
	 */
	private static void profileStages(TestSingleplayerContext sp, String name, boolean real, boolean allSpots) {
		int[][] spots = real ? SPOTS_REAL : SPOTS_GAMEPLAY;
		for (int i = 0; i < (allSpots ? spots.length : 1); i++) {
			int[] spot = spots[i];
			String label = i == 0 ? name : name + " / " + SPOT_NAMES[i];
			sp.getServer().runOnServer(server -> profileSpot(server.overworld(), label, spot[0] >> 4, spot[1] >> 4));
		}
		if (allSpots) {
			int half = real ? AREA_HALF_REAL : AREA_HALF_GAMEPLAY;
			sp.getServer().runOnServer(server -> profileArea(server.overworld(), name + " / area", half));
		}
	}

	/** The stages in 8 × 8 chunk areas centered on the places of the vertical slice (step V0). */
	private static void profileSlice(TestSingleplayerContext sp, String name, boolean real, boolean presentDay) {
		for (SliceSites.Place p : SliceSites.of(real, presentDay)) {
			int[] m = p.middle();
			sp.getServer().runOnServer(server -> profileSpot(server.overworld(), name + " / slice " + p.name(), (m[0] - 64) >> 4,
					(m[1] - 64) >> 4));
		}
	}

	/**
	 * TERRAIN of the whole area (step S10, M2-11): six contiguous 8 × 8 areas (384 chunks) at pseudo-random places in the D1
	 * area, generated up to TERRAIN only
	 * (STRUCTURE_STARTS, BIOMES, then TERRAIN; the BIOMES stage of each chunk and its neighbors warms the regional caches
	 * first, as contiguous generation in the game does). Logs the TERRAIN stage time and the model and fill counters per
	 * chunk, in the same format as the contiguous areas.
	 */
	private static void profileArea(ServerLevel level, String name, int half) {
		java.util.Random random = new java.util.Random(0x5EED_0001L);
		// Six contiguous 8 x 8 areas at pseudo-random places: contiguous like generation in the game (the surface plan reuses
		// the border columns of its neighbors, the regional grid tiles are shared), scattered over the whole area.
		int[][] chunks = new int[6 * 64][];
		for (int k = 0; k < 6; k++) {
			int bx = (random.nextInt(2 * half) - half) >> 4;
			int bz = (random.nextInt(2 * half) - half) >> 4;
			for (int i = 0; i < 64; i++) {
				chunks[k * 64 + i] = new int[] {bx + i / 8, bz + i % 8};
			}
		}
		ChunkStatus[] stages = {ChunkStatus.STRUCTURE_STARTS, ChunkStatus.BIOMES, ChunkStatus.TERRAIN};
		long sample0 = PolandChunkGenerator.SAMPLE_NANOS.sum();
		long fill0 = PolandChunkGenerator.FILL_NANOS.sum();
		long chunks0 = PolandChunkGenerator.CHUNKS.sum();
		long classify0 = PolandChunkGenerator.CLASSIFY_NANOS.sum();
		long surface0 = PolandChunkGenerator.SURFACE_NANOS.sum();
		StringBuilder sb = new StringBuilder();
		for (ChunkStatus stage : stages) {
			long t0 = System.nanoTime();
			for (int[] c : chunks) {
				level.getChunkSource().getChunk(c[0], c[1], stage, true);
			}
			sb.append(stage.getName()).append('=').append(Math.round((System.nanoTime() - t0) / 1e6)).append(" ms ");
		}
		PolishForests.LOG.info("[performance] {}: stages for 384 chunks (incremental): {}", name, sb);
		long n = PolandChunkGenerator.CHUNKS.sum() - chunks0;
		if (n > 0) {
			PolishForests.LOG.info(String.format(Locale.ROOT, "[performance] %s: terrain of %d chunks during the measurement, "
					+ "sampling %.2f ms/chunk (model %.2f, classification %.2f), filling %.2f ms/chunk, surface plan %.3f ms/chunk",
					name, n, (PolandChunkGenerator.SAMPLE_NANOS.sum() - sample0) / 1e6 / n,
					(PolandChunkGenerator.SAMPLE_NANOS.sum() - sample0 - (PolandChunkGenerator.CLASSIFY_NANOS.sum() - classify0))
							/ 1e6 / n,
					(PolandChunkGenerator.CLASSIFY_NANOS.sum() - classify0) / 1e6 / n,
					(PolandChunkGenerator.FILL_NANOS.sum() - fill0) / 1e6 / n,
					(PolandChunkGenerator.SURFACE_NANOS.sum() - surface0) / 1e6 / n));
		} else {
			PolishForests.LOG.warn("[performance] {}: area was already generated, measurement invalid", name);
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
		long decoration0 = PolandChunkGenerator.DECORATION_NANOS.sum();
		long decorated0 = PolandChunkGenerator.DECORATION_CHUNKS.sum();
		long biome0 = PolandChunkGenerator.BIOME_NANOS.sum();
		long biomeChunks0 = PolandChunkGenerator.BIOME_CHUNKS.sum();
		long classify0 = PolandChunkGenerator.CLASSIFY_NANOS.sum();
		long tree0 = TreeStandFeature.NANOS.sum();
		long treeChunks0 = TreeStandFeature.CHUNKS.sum();
		long trees0 = TreeStandFeature.TREES.sum();
		long plan0 = TreeStandFeature.PLAN_NANOS.sum();
		long planned0 = TreeStandFeature.PLANNED.sum();
		long roots0 = pl.polishforests.worldgen.feature.TreeRoots.NANOS.sum();
		long rootTrees0 = pl.polishforests.worldgen.feature.TreeRoots.TREES.sum();
		long miss0 = ModFeatures.HABITAT_MISS.sum();
		long[] layer0 = new long[pl.polishforests.worldgen.feature.BiomeDecoration.Dispatcher.values().length * 3];
		for (var d : pl.polishforests.worldgen.feature.BiomeDecoration.Dispatcher.values()) {
			ModFeatures.LayerStats st = ModFeatures.STATS.get(d);
			layer0[d.ordinal() * 3] = st.nanos.sum();
			layer0[d.ordinal() * 3 + 1] = st.chunks.sum();
			layer0[d.ordinal() * 3 + 2] = st.placed.sum();
		}
		long surface0 = PolandChunkGenerator.SURFACE_NANOS.sum();
		long packed0 = PolandChunkGenerator.PACKED_SECTIONS.sum();
		long fallbacks0 = PolandChunkGenerator.PACK_FALLBACKS.sum();
		long outside0 = pl.polishforests.worldgen.surface.SurfaceBuilder.OUTSIDE_SAMPLES.sum();
		long hits0 = pl.polishforests.worldgen.surface.SurfaceBuilder.OUTSIDE_HITS.sum();
		long reused0 = pl.polishforests.worldgen.surface.SurfaceBuilder.REUSED_SAMPLES.sum();
		StringBuilder sb = new StringBuilder();
		long total = 0;
		for (ChunkStatus stage : stages) {
			long t0 = System.nanoTime();
			for (int cx = 0; cx < 8; cx++) {
				for (int cz = 0; cz < 8; cz++) {
					level.getChunkSource().getChunk(ox + cx, oz + cz, stage, true);
				}
			}
			long dt = System.nanoTime() - t0;
			total += dt;
			sb.append(stage.getName()).append('=').append(Math.round(dt / 1e6)).append(" ms ");
		}
		PolishForests.LOG.info("[performance] {}: stages for 64 chunks (incremental): {}", name, sb);
		// Step S10 (budget "whole chunk"): the wall time of all stages divided by the 64 chunks of the area. It includes the
		// neighbors that decoration and light need (about 100 chunks in all), so it is compared with M1 in the same session.
		PolishForests.LOG.info(String.format(Locale.ROOT, "[performance] %s: whole chunk %.2f ms/chunk (all stages, 64 chunks)",
				name, total / 1e6 / 64));
		// Step V10: wood and leaves of the area by block tags (counted after the timing; the same count on the M2 baseline).
		double[] wl = ForestPerformance.woodAndLeaves(level, ox, oz, 8);
		PolishForests.LOG.info(String.format(Locale.ROOT, "[performance] %s: blocks per chunk of the 64: wood %.1f, leaves %.1f", name,
				wl[0], wl[1]));
		long decorated = Math.max(1, PolandChunkGenerator.DECORATION_CHUNKS.sum() - decorated0);
		PolishForests.LOG.info(String.format(java.util.Locale.ROOT, "[performance] %s: decoration (applyBiomeDecoration) %.3f ms/chunk "
				+ "over %d chunks", name, (PolandChunkGenerator.DECORATION_NANOS.sum() - decoration0) / 1e6 / decorated, decorated));
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
			// Step V10: the planner time within the tree stand (procedural trees of the slice).
			long planned = TreeStandFeature.PLANNED.sum() - planned0;
			PolishForests.LOG.info(String.format(Locale.ROOT, "[performance] %s: tree planner %.3f ms/chunk (%d trees planned, %.3f "
					+ "ms/tree)", name, (TreeStandFeature.PLAN_NANOS.sum() - plan0) / 1e6 / treeChunks, planned,
					(TreeStandFeature.PLAN_NANOS.sum() - plan0) / 1e6 / Math.max(1, planned)));
			// Step I1: the roots within the tree stand (planner and writing).
			long rootTrees = pl.polishforests.worldgen.feature.TreeRoots.TREES.sum() - rootTrees0;
			PolishForests.LOG.info(String.format(Locale.ROOT, "[performance] %s: roots %.3f ms/chunk (%d trees, %.3f ms/tree)", name,
					(pl.polishforests.worldgen.feature.TreeRoots.NANOS.sum() - roots0) / 1e6 / treeChunks, rootTrees,
					(pl.polishforests.worldgen.feature.TreeRoots.NANOS.sum() - roots0) / 1e6 / Math.max(1, rootTrees)));
			StringBuilder layers = new StringBuilder();
			for (var d : pl.polishforests.worldgen.feature.BiomeDecoration.Dispatcher.values()) {
				ModFeatures.LayerStats st = ModFeatures.STATS.get(d);
				long c = Math.max(1, st.chunks.sum() - layer0[d.ordinal() * 3 + 1]);
				layers.append(String.format(Locale.ROOT, " %s %.3f ms/chunk (%.1f placed/chunk);", d.path(),
						(st.nanos.sum() - layer0[d.ordinal() * 3]) / 1e6 / c, (double) (st.placed.sum() - layer0[d.ordinal() * 3 + 2]) / c));
			}
			PolishForests.LOG.info("[performance] {}: dispatcher layers:{}", name, layers);
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

	private static void configure(WorldCreationUiState ui, ResourceKey<WorldPreset> preset, boolean presentDay) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		if (preset == null) {
			return;
		}
		for (WorldCreationUiState.WorldTypeEntry entry : ui.getNormalPresetList()) {
			if (entry.preset().is(preset)) {
				ui.setWorldType(entry);
				if (presentDay) {
					if (!(ui.getSettings().selectedDimensions().overworld() instanceof PolandChunkGenerator gen)) {
						throw new AssertionError("The preset " + preset.identifier() + " has no Poland generator");
					}
					ui.updateDimensions(PolandPresetEditor.apply(gen.settings().withAgriculture(true)));
				}
				return;
			}
		}
		throw new AssertionError("Missing preset " + preset.identifier());
	}
}
