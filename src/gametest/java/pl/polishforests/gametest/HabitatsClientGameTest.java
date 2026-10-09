package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.Fluids;
import pl.polishforests.PolishForests;
import pl.polishforests.climate.BiomeClimateAccess;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.MaterialStates;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.feature.TreeStandFeature;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.surface.ChunkSurface;
import pl.polishforests.worldgen.surface.SurfaceBuilder;

/**
 * Habitat biomes in the game (step S5, docs/03-m2-biomy.md §3.5, §10, §12.3), in both world scales: the world starts
 * with the mod's 36 biomes, {@code generator.validate()} (feature order) passes, {@code level.getBiome} equals the
 * classifier at 8 places with different biomes, F3 shows {@code polishforests:*} and the habitat line (screenshot
 * {@code habitats_f3_<scale>}), {@code /locate structure} finds a stronghold, a mineshaft and trial chambers,
 * {@code /locate biome polishforests:oak_hornbeam_forest} takes less than 2 s, the BIOMES stage takes at most 0.2 ms per
 * chunk in the lowland and at a large river and 0.3 ms in the Beskids (decision M2-10), the tree stand census matches the palette
 * within ±20%, and revisited areas keep their chunk habitats. Step S6 (§7, §12.3): the ground block equals the soil of
 * the surface plan at 11 places, at least 90% of the shore columns have water beside their top block, no water flows
 * out of the shelf and the puddles in 200 ticks, and fewer than 1% of the packed sections fall back to block writes.
 * Runs when {@code -Dpolishforests.gametest} is {@code habitats} or {@code all}.
 */
public final class HabitatsClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int PLACES = 8;
	/** Spiral step of the place search in blocks. */
	private static final int SEARCH_STEP = 52;
	private static final double LOCATE_BIOME_LIMIT_MS = 2_000;
	/**
	 * With {@code -PtimingsReportOnly} the time limits (BIOMES, {@code /locate biome}) are only reported, for runs on a
	 * machine that is not quiet (another heavy program running); the budgets of §3.6 are checked on a quiet machine.
	 */
	private static final boolean TIMINGS_REPORT_ONLY = Boolean.getBoolean("polishforests.timings.reportOnly");
	/** Budget of the BIOMES stage in the lowland and at rivers (docs/03-m2-biomy.md §3.6). */
	private static final double BIOMES_LIMIT_MS = 0.2;
	/**
	 * Budget of the BIOMES stage in the mountains (decision M2-10, 2026-10-09): 16 samples at the D1 budget of a sample in
	 * the Beskids (12 µs REAL, 16 µs GAMEPLAY) exceed the 0.2 ms of the lowland, so the mountains get about 0.3 ms.
	 */
	private static final double BIOMES_MOUNTAIN_LIMIT_MS = 0.3;

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polishforests.gametest", "all");
		if (!mode.equals("habitats") && !mode.equals("all")) {
			return;
		}
		// -Pscales=realistic|gameplay runs one scale only (both by default).
		String scales = System.getProperty("polishforests.scales", "");
		for (PolandScale scale : PolandScale.values()) {
			if (!scales.isBlank() && !scales.contains(scale.getSerializedName())) {
				continue;
			}
			ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET,
					PolishForests.id(scale == PolandScale.REALISTIC ? "poland" : "poland_gameplay"));
			try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset)).create()) {
				sp.getServer().runCommand("gamerule advance_time false");
				sp.getServer().runCommand("gamerule advance_weather false");
				sp.getServer().runCommand("time set 6000");
				sp.getServer().runCommand("weather clear");
				sp.getServer().runCommand("gamemode spectator @a");
				String name = scale.getSerializedName();
				if (SurfaceBuilder.debugFromSystem()) {
					// Diagnostic mode (-PdebugHabitats): the painted tops break the soil and tree checks, so only the
					// views of the shelf areas with the zones and biomes painted (screenshots s6_debug_*).
					PolishForests.LOG.info("[habitats] {}: diagnostic mode, {}", name, shelf(context, sp, scale == PolandScale.REALISTIC, name));
					continue;
				}
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(HabitatsClientGameTest::registry));
				List<int[]> places = sp.getServer().computeOnServer(HabitatsClientGameTest::findPlaces);
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(s -> checkPlaces(s, places)));
				f3(context, sp, places.getFirst(), name);
				for (String command : List.of("locate structure minecraft:stronghold", "locate structure minecraft:mineshaft",
						"locate structure minecraft:trial_chambers", "locate biome polishforests:oak_hornbeam_forest")) {
					PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(s -> locate(s, command)));
				}
				// The worst case of /locate biome: a biome absent within 6.4 km scans the whole spiral (only reported).
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(
						s -> locateTime(s, "locate biome polishforests:dwarf_pine_scrub")));
				boolean real = scale == PolandScale.REALISTIC;
				waitForIdleGeneration(context, name);
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(s -> biomesPerChunk(s, real)));
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(HabitatsClientGameTest::habitatMisses));
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(s -> census(s, real, places)));
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(s -> soilPlaces(s, real)));
				PolishForests.LOG.info("[habitats] {}: {}", name, shelf(context, sp, real, name));
				PolishForests.LOG.info("[habitats] {}: {}", name, revisit(context, sp, real));
				PolishForests.LOG.info("[habitats] {}: {}", name, packing());
			}
		}
	}

	/**
	 * Waits until the chunks loaded around the player after the teleports have finished generating (no new terrain for
	 * 3 s, at most 3 minutes), so the one-thread BIOMES measurement does not compete with them (review of S6, round 1:
	 * 0.21–0.29 ms on the lowland with the generation of the F3 place still running).
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
		PolishForests.LOG.info("[habitats] {}: {} more chunks with terrain generated in the background, waited {} s before BIOMES",
				name, PolandChunkGenerator.CHUNKS.sum() - before, String.format(Locale.ROOT, "%.0f", (System.nanoTime() - start) / 1e9));
	}

	private static void select(WorldCreationUiState ui, ResourceKey<WorldPreset> preset) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		// The test world builder turns structures off by default; /locate structure needs them.
		ui.setGenerateStructures(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(preset)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier())));
	}

	private static PolandChunkGenerator generator(MinecraftServer server) {
		if (!(server.overworld().getChunkSource().getGenerator() instanceof PolandChunkGenerator gen)) {
			throw new AssertionError("The overworld does not use the Poland generator");
		}
		return gen;
	}

	/** The biome source has exactly the 36 biomes of the mod, each with a climate profile; the feature order is valid. */
	private static String registry(MinecraftServer server) {
		PolandChunkGenerator gen = generator(server);
		// Throws on a "Feature order cycle".
		gen.validate();
		int n = 0;
		for (Holder<Biome> biome : gen.getBiomeSource().possibleBiomes()) {
			String id = biome.getRegisteredName();
			if (!id.startsWith(PolishForests.MOD_ID + ":")) {
				throw new AssertionError("Biome from outside the mod in the biome source: " + id);
			}
			if (BiomeClimateAccess.climate(biome.value()) == null) {
				throw new AssertionError("Biome without a climate profile: " + id);
			}
			n++;
		}
		if (n != HabitatBiome.values().length) {
			throw new AssertionError("The biome source has " + n + " biomes instead of " + HabitatBiome.values().length);
		}
		return "generator.validate() passed; biome source with " + n + " biomes polishforests:*, all with a climate profile, "
				+ "settings " + gen.settings();
	}

	/**
	 * Areas of the BIOMES measurement (north-west corner in blocks): the lowland area of S5 and the Beskids and the large
	 * river of {@code PerformanceClientGameTest} (the {@code stages} areas), per scale.
	 */
	private static final int[][] BIOMES_REAL = {{-50_000, 30_000}, {154_834 - 64, 1_058_738 - 64}, {-19_484 - 64, 11_253 - 64}};
	private static final int[][] BIOMES_GAMEPLAY = {{-50_000, 30_000}, {27_609 - 64, 3_254 - 64}, {-1_851 - 64, 6_022 - 64}};
	private static final String[] BIOMES_AREAS = {"lowland", "beskids", "river"};

	/**
	 * BIOMES stage on one thread (the server thread asks one chunk after another): for each area 64 new chunks (8 × 8,
	 * cold grid caches of the area) and then the next 64 chunks to the east (warmer caches); mean time per chunk. The
	 * lowland and the river must meet the budget of §3.6 ({@link #BIOMES_LIMIT_MS}), the Beskids the mountain budget
	 * ({@link #BIOMES_MOUNTAIN_LIMIT_MS}, decision M2-10).
	 */
	private static String biomesPerChunk(MinecraftServer server, boolean real) {
		ServerLevel level = server.overworld();
		int[][] areas = real ? BIOMES_REAL : BIOMES_GAMEPLAY;
		StringBuilder report = new StringBuilder("BIOMES per chunk (one thread, cold / warm caches):");
		for (int a = 0; a < areas.length; a++) {
			double[] ms = new double[2];
			double[] classifyMs = new double[2];
			for (int pass = 0; pass < 2; pass++) {
				long nanos0 = PolandChunkGenerator.BIOME_NANOS.sum();
				long classify0 = PolandChunkGenerator.BIOME_CLASSIFY_NANOS.sum();
				long chunks0 = PolandChunkGenerator.BIOME_CHUNKS.sum();
				int ox = (areas[a][0] >> 4) + pass * 8;
				int oz = areas[a][1] >> 4;
				for (int cx = 0; cx < 8; cx++) {
					for (int cz = 0; cz < 8; cz++) {
						level.getChunkSource().getChunk(ox + cx, oz + cz, ChunkStatus.BIOMES, true);
					}
				}
				long chunks = PolandChunkGenerator.BIOME_CHUNKS.sum() - chunks0;
				if (chunks < 64) {
					throw new AssertionError("BIOMES measured on only " + chunks + " chunks in " + BIOMES_AREAS[a]);
				}
				ms[pass] = (PolandChunkGenerator.BIOME_NANOS.sum() - nanos0) / 1e6 / chunks;
				classifyMs[pass] = (PolandChunkGenerator.BIOME_CLASSIFY_NANOS.sum() - classify0) / 1e6 / chunks;
			}
			report.append(String.format(Locale.ROOT, " %s %.3f / %.3f ms (sampling and classifying %.3f / %.3f ms);",
					BIOMES_AREAS[a], ms[0], ms[1], classifyMs[0], classifyMs[1]));
			double limit = a == 1 ? BIOMES_MOUNTAIN_LIMIT_MS : BIOMES_LIMIT_MS;
			if (Math.max(ms[0], ms[1]) > limit && TIMINGS_REPORT_ONLY) {
				report.append(String.format(Locale.ROOT, " OVER THE LIMIT %.1f ms (timings only reported);", limit));
			} else if (Math.max(ms[0], ms[1]) > limit) {
				throw new AssertionError(String.format(Locale.ROOT, "BIOMES in %s %.3f / %.3f ms per chunk (limit %.1f ms)",
						BIOMES_AREAS[a], ms[0], ms[1], limit));
			}
		}
		return report.append(String.format(Locale.ROOT, " budget %.1f ms (lowland, river), %.1f ms (Beskids)", BIOMES_LIMIT_MS,
				BIOMES_MOUNTAIN_LIMIT_MS)).toString();
	}

	/**
	 * Census sites of the tree stand (center in blocks): riparian forests on dry valley floors (formerly bare channel-bed
	 * sand) and the willow-poplar forest (willows blocked by vines), and beaches with a grass top (the last site of each scale), found in
	 * the S5 review with seed {@value #SEED}.
	 */
	private static final int[][] CENSUS_REAL = {{47_502, 12_994}, {1_322, -1_594}, {-232_506, -136_558}, {-233_358, -136_402}};
	private static final int[][] CENSUS_GAMEPLAY = {{110, 2_442}, {1_326, 11_222}, {-5_782, -1_802}};
	/** Allowed relative difference of the trunks per biome from the palette, for biomes with enough expected trees. */
	private static final double CENSUS_TOLERANCE = 0.20;
	private static final int CENSUS_MIN_EXPECTED = 40;

	/**
	 * Census of the tree stand: 5 × 5 full chunks around each census site and the first three places of the biome
	 * comparison; for each biome the trunks ({@link #trunk}) against the palette's trees per chunk times
	 * the number of the biome's dry columns / 256. Each biome with at least {@value #CENSUS_MIN_EXPECTED} expected trees
	 * must be within ±20%. Dry beach and white dune columns must have a sand top, and dry forest columns are reported
	 * when their top is bare sand.
	 */
	private static String census(MinecraftServer server, boolean real, List<int[]> places) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = generator(server);
		long seed = level.getSeed();
		TreeStandFeature stand = (TreeStandFeature) server.registryAccess().lookupOrThrow(Registries.FEATURE)
				.getValueOrThrow(ResourceKey.create(Registries.FEATURE, PolishForests.id("tree_stand")));
		int n = HabitatBiome.values().length;
		double[] expected = new double[n];
		long[] trunks = new long[n];
		long grassBeach = 0;
		long beachColumns = 0;
		long sandForest = 0;
		long forestColumns = 0;
		List<int[]> sites = new ArrayList<>(List.of(real ? CENSUS_REAL : CENSUS_GAMEPLAY));
		sites.addAll(places.subList(0, Math.min(3, places.size())));
		for (int[] site : sites) {
			int cx0 = (site[0] >> 4) - 2;
			int cz0 = (site[1] >> 4) - 2;
			for (int cx = cx0; cx < cx0 + 5; cx++) {
				for (int cz = cz0; cz < cz0 + 5; cz++) {
					level.getChunk(cx, cz);
				}
			}
			ChunkSurface[][] plans = new ChunkSurface[5][5];
			int[][][] planCodes = new int[5][5][256];
			for (int cx = 0; cx < 5; cx++) {
				for (int cz = 0; cz < 5; cz++) {
					plans[cx][cz] = gen.surface(new net.minecraft.world.level.ChunkPos(cx0 + cx, cz0 + cz), level.getMinY(),
							level.getMaxY(), seed, planCodes[cx][cz], level.structureManager());
				}
			}
			for (int x = cx0 * 16; x < (cx0 + 5) * 16; x++) {
				for (int z = cz0 * 16; z < (cz0 + 5) * 16; z++) {
					// The top of the surface plan (after the shelf and the micro-relief, step S6).
					ChunkSurface plan = plans[(x >> 4) - cx0][(z >> 4) - cz0];
					int column = ChunkHabitats.index(x & 15, z & 15);
					int top = plan.top(column);
					if (plan.wet(column)) {
						continue;
					}
					int code = planCodes[(x >> 4) - cx0][(z >> 4) - cz0][column];
					HabitatBiome b = Habitat.biome(code);
					expected[b.ordinal()] += stand.treesPerChunk(code) / 256.0;
					if (trunk(level, x, top, z)) {
						trunks[b.ordinal()]++;
					}
					net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(new BlockPos(x, top, z));
					if (b == HabitatBiome.BEACH || b == HabitatBiome.WHITE_DUNE) {
						beachColumns++;
						grassBeach += ground.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) ? 1 : 0;
					} else if (b.isForest()) {
						forestColumns++;
						sandForest += ground.is(net.minecraft.world.level.block.Blocks.SAND) ? 1 : 0;
					}
				}
			}
		}
		List<String> failures = new ArrayList<>();
		StringBuilder report = new StringBuilder(String.format(Locale.ROOT, "top blocks: grass on %d of %d dry beach and "
				+ "white dune columns, sand on %d of %d dry forest columns; tree census (trunks / expected):", grassBeach,
				beachColumns, sandForest, forestColumns));
		if (grassBeach > 0) {
			failures.add(grassBeach + " dry beach or white dune columns with a grass top");
		}
		for (HabitatBiome b : HabitatBiome.values()) {
			double e = expected[b.ordinal()];
			long t = trunks[b.ordinal()];
			if (e < 1 && t == 0) {
				continue;
			}
			report.append(String.format(Locale.ROOT, " %s %d / %.1f;", b.id(), t, e));
			if (e >= CENSUS_MIN_EXPECTED && Math.abs(t - e) > CENSUS_TOLERANCE * e) {
				failures.add(String.format(Locale.ROOT, "%s %d trunks instead of %.1f", b.id(), t, e));
			}
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Tree census off the palette by more than 20%: " + failures + "; " + report);
		}
		return report.toString();
	}

	/**
	 * A tree trunk on the top ground block: vertical logs on the three blocks above it. Since S7 the shrubs (one to three
	 * logs) and the fallen trees (a stump of one log, the log lying on the ground) are not counted.
	 */
	private static boolean trunk(ServerLevel level, int x, int top, int z) {
		for (int dy = 1; dy <= 3; dy++) {
			net.minecraft.world.level.block.state.BlockState s = level.getBlockState(new BlockPos(x, top + dy, z));
			if (!s.is(BlockTags.LOGS) || s.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
					&& s.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) != net.minecraft.core.Direction.Axis.Y) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Revisited areas (§3.6, {@code HABITAT_MISS} below 0.5%). First a save round trip: a new proto-chunk at the TERRAIN
	 * status is written as the game saves it ({@code SerializableChunkData}) and read back, and the read proto-chunk must
	 * hold the same chunk habitats (the attachment is persistent). Then the case of the S5 review: the player flies into a
	 * new area, which leaves proto-chunks between TERRAIN and FEATURES at the edge of the loaded area, moves away until
	 * they are unloaded and saved, and comes back a little further on, so those proto-chunks are loaded from disk and
	 * decorated; the misses must stay below 0.5% of the chunks with terrain.
	 */
	private static String revisit(ClientGameTestContext context, TestSingleplayerContext sp, boolean real) {
		String roundTrip = sp.getServer().computeOnServer(s -> saveRoundTrip(s.overworld(), real));
		long miss0 = ModFeatures.HABITAT_MISS.sum();
		long chunks0 = PolandChunkGenerator.CHUNKS.sum();
		long loaded0 = ChunkHabitats.LOADED.sum();
		int[] a = real ? new int[] {-120_000, 80_000} : new int[] {-12_000, 9_000};
		int[] b = {a[0] + 4_000, a[1]};
		int[] c = {a[0] + 96, a[1] + 96};
		for (int[] p : List.of(a, b, c)) {
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", p[0], real ? 600 : 300, p[1]));
			context.waitTicks(20 * 25);
		}
		long misses = ModFeatures.HABITAT_MISS.sum() - miss0;
		long chunks = PolandChunkGenerator.CHUNKS.sum() - chunks0;
		long loaded = ChunkHabitats.LOADED.sum() - loaded0;
		String report = String.format(Locale.ROOT, "%s; revisit: chunk habitats missing in %d of %d chunks with terrain "
				+ "(%.2f%%), %d attachments read from saved proto-chunks", roundTrip, misses, chunks,
				100.0 * misses / Math.max(1, chunks), loaded);
		if (chunks < 500 || misses > 0.005 * chunks) {
			throw new AssertionError(report);
		}
		return report;
	}

	/** Writes a new TERRAIN proto-chunk as the game saves it and reads it back; the chunk habitats must survive. */
	private static String saveRoundTrip(ServerLevel level, boolean real) {
		int cx = (real ? 90_000 : 9_000) >> 4;
		int cz = (real ? -70_000 : -7_000) >> 4;
		ChunkAccess proto = level.getChunkSource().getChunk(cx, cz, ChunkStatus.TERRAIN, true);
		ChunkHabitats before = proto.getAttached(ModFeatures.CHUNK_HABITATS);
		if (before == null) {
			throw new AssertionError("A TERRAIN proto-chunk has no chunk habitats");
		}
		CompoundTag tag = SerializableChunkData.copyOf(level, proto).write();
		SerializableChunkData parsed = SerializableChunkData.parse(level, level.palettedContainerFactory(), tag);
		ChunkAccess back = parsed.read(level, level.getPoiManager(),
				new RegionStorageInfo("habitats_test", level.dimension(), "chunk"), proto.getPos());
		ChunkHabitats after = back.getAttached(ModFeatures.CHUNK_HABITATS);
		if (after == null || !java.util.Arrays.equals(before.codes(), after.codes())
				|| !java.util.Arrays.equals(before.top(), after.top()) || !java.util.Arrays.equals(before.water(), after.water())
				|| before.oceanicity() != after.oceanicity() || before.mountainInfluence() != after.mountainInfluence()) {
			throw new AssertionError("The chunk habitats did not survive saving the proto-chunk " + proto.getPos());
		}
		return "save round trip of a TERRAIN proto-chunk keeps its chunk habitats";
	}

	/**
	 * Chunk habitats ({@code ChunkHabitats}, §8.3): 36 new chunks generated to the full status in one area; the tree
	 * stand must find the attachment written in {@code fill()} in at least 99.5% of the chunks ({@code HABITAT_MISS},
	 * budget §3.6), and no chunk keeps the attachment after its last stage.
	 */
	private static String habitatMisses(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long miss0 = ModFeatures.HABITAT_MISS.sum();
		long chunks0 = PolandChunkGenerator.CHUNKS.sum();
		int ox = 60_000 >> 4;
		int oz = -40_000 >> 4;
		int kept = 0;
		for (int cx = 0; cx < 6; cx++) {
			for (int cz = 0; cz < 6; cz++) {
				if (level.getChunk(ox + cx, oz + cz).hasAttached(ModFeatures.CHUNK_HABITATS)) {
					kept++;
				}
			}
		}
		long misses = ModFeatures.HABITAT_MISS.sum() - miss0;
		long chunks = PolandChunkGenerator.CHUNKS.sum() - chunks0;
		if (chunks < 36 || misses > 0.005 * chunks) {
			throw new AssertionError("Chunk habitats missing in " + misses + " of " + chunks + " chunks");
		}
		if (kept > 0) {
			throw new AssertionError(kept + " full chunks still hold the chunk habitats");
		}
		return String.format(Locale.ROOT, "chunk habitats missing in %d of %d chunks with terrain, none kept in full chunks",
				misses, chunks);
	}

	/**
	 * Places with {@value #PLACES} different biomes along a spiral from the origin, each at the center of a quart column
	 * whose 3 × 3 quart neighborhood has the same biome (so the biome zoom of {@code getBiome} cannot pick a neighbor).
	 */
	private static List<int[]> findPlaces(MinecraftServer server) {
		PolandChunkGenerator gen = generator(server);
		long seed = server.overworld().getSeed();
		LandscapeModel m = gen.model(seed);
		HabitatClassifier k = gen.classifier(seed);
		Map<HabitatBiome, int[]> found = new LinkedHashMap<>();
		int x = 0;
		int z = 0;
		int dx = 1;
		int dz = 0;
		int leg = 1;
		int stepsInLeg = 0;
		int turns = 0;
		for (int i = 0; i < 200_000 && found.size() < PLACES; i++) {
			int qx = QuartPos.fromBlock(x * SEARCH_STEP);
			int qz = QuartPos.fromBlock(z * SEARCH_STEP);
			HabitatBiome b = biome(m, k, qx, qz);
			if (!found.containsKey(b) && uniform(m, k, qx, qz, b)) {
				found.put(b, new int[] {QuartPos.toBlock(qx) + 2, QuartPos.toBlock(qz) + 2, b.ordinal()});
			}
			x += dx;
			z += dz;
			if (++stepsInLeg == leg) {
				stepsInLeg = 0;
				int t = dx;
				dx = -dz;
				dz = t;
				if (++turns % 2 == 0) {
					leg++;
				}
			}
		}
		if (found.size() < PLACES) {
			throw new AssertionError("Only " + found.size() + " biomes found for the comparison: " + found.keySet());
		}
		return new ArrayList<>(found.values());
	}

	private static HabitatBiome biome(LandscapeModel m, HabitatClassifier k, int qx, int qz) {
		int bx = QuartPos.toBlock(qx) + 2;
		int bz = QuartPos.toBlock(qz) + 2;
		return Habitat.biome(k.classify(m.sample(bx, bz), bx, bz));
	}

	private static boolean uniform(LandscapeModel m, HabitatClassifier k, int qx, int qz, HabitatBiome b) {
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				if ((i != 0 || j != 0) && biome(m, k, qx + i, qz + j) != b) {
					return false;
				}
			}
		}
		return true;
	}

	/** {@code level.getBiome} (generated chunk, biome zoom) equals the classifier at every place, at the ground and above. */
	private static String checkPlaces(MinecraftServer server, List<int[]> places) {
		ServerLevel level = server.overworld();
		StringBuilder report = new StringBuilder("getBiome = classifier at");
		for (int[] p : places) {
			level.getChunk(p[0] >> 4, p[1] >> 4);
			int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, p[0], p[1]);
			String expected = PolishForests.id(HabitatBiome.of(p[2]).id()).toString();
			for (int y : new int[] {ground, ground + 40, level.getMinY() + 8}) {
				String actual = level.getBiome(new BlockPos(p[0], y, p[1])).getRegisteredName();
				if (!actual.equals(expected)) {
					throw new AssertionError(String.format(Locale.ROOT, "getBiome at (%d, %d, %d) is %s, the classifier says %s",
							p[0], y, p[1], actual, expected));
				}
			}
			report.append(String.format(Locale.ROOT, " (%d, %d) %s;", p[0], p[1], HabitatBiome.of(p[2]).id()));
		}
		return report.toString();
	}

	/**
	 * F3 with the biome and the generator lines at the first place: the client biome is {@code polishforests:*}, the
	 * server's generator lines contain the habitat; screenshot {@code habitats_f3_<scale>}.
	 */
	private static void f3(ClientGameTestContext context, TestSingleplayerContext sp, int[] place, String name) {
		int ground = sp.getServer().computeOnServer(s -> s.overworld().getChunkSource().getGenerator().getBaseHeight(place[0],
				place[1], Heightmap.Types.WORLD_SURFACE_WG, s.overworld(), s.overworld().getChunkSource().randomState()));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 30.0 25.0", place[0], ground + 40, place[1]));
		context.waitTicks(20 * 15);
		String lines = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			List<String> out = new ArrayList<>();
			// As the F3 entry "chunk_generation_stats" does: the generator's lines, then the biome source's.
			BlockPos feet = new BlockPos(place[0], ground + 1, place[1]);
			level.getChunkSource().getGenerator().addDebugScreenInfo(out, level.getChunkSource().randomState(), feet, null);
			level.getChunkSource().getGenerator().getBiomeSource().addDebugInfo(out, feet, null);
			return String.join(" | ", out);
		});
		if (!lines.contains("Habitat: polishforests:")) {
			throw new AssertionError("The generator's F3 lines have no habitat: " + lines);
		}
		String clientBiome = context.computeOnClient(mc -> mc.level.getBiome(mc.player.blockPosition()).getRegisteredName());
		if (!clientBiome.startsWith(PolishForests.MOD_ID + ":")) {
			throw new AssertionError("F3 biome on the client is " + clientBiome);
		}
		context.runOnClient(mc -> {
			mc.debugEntries.setStatus(DebugScreenEntries.BIOME, DebugScreenEntryStatus.IN_OVERLAY);
			mc.debugEntries.setStatus(DebugScreenEntries.CHUNK_GENERATION_STATS, DebugScreenEntryStatus.IN_OVERLAY);
			mc.debugEntries.setOverlayVisible(true);
		});
		context.waitTicks(20);
		context.takeScreenshot("habitats_f3_" + name);
		context.runOnClient(mc -> mc.debugEntries.setOverlayVisible(false));
		PolishForests.LOG.info("[habitats] {}: F3 biome {}, generator lines: {}", name, clientBiome, lines);
	}

	/** Runs a locate command at the origin without checking the result; returns the message and the time. */
	private static String locateTime(MinecraftServer server, String command) {
		List<Component> messages = new ArrayList<>();
		long t0 = System.nanoTime();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture(messages))
				.withLevel(server.overworld()).withPosition(new Vec3(0.5, 100, 0.5)), command);
		double ms = (System.nanoTime() - t0) / 1e6;
		return String.format(Locale.ROOT, "/%s: %s (%.0f ms)", command, messages.isEmpty() ? "no message"
				: messages.getFirst().getString(), ms);
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

	/** Runs a locate command at the origin and checks its success message; returns the message and the time. */
	private static String locate(MinecraftServer server, String command) {
		List<Component> messages = new ArrayList<>();
		long t0 = System.nanoTime();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture(messages))
				.withLevel(server.overworld()).withPosition(new Vec3(0.5, 100, 0.5)), command);
		double ms = (System.nanoTime() - t0) / 1e6;
		Component result = messages.isEmpty() ? null : messages.getFirst();
		String key = result != null && result.getContents() instanceof TranslatableContents t ? t.getKey() : null;
		if (key == null || !key.startsWith("commands.locate.") || !key.endsWith(".success")) {
			throw new AssertionError("/" + command + " failed: " + (result == null ? "no message" : result.getString()));
		}
		if (command.startsWith("locate biome") && ms > LOCATE_BIOME_LIMIT_MS && !TIMINGS_REPORT_ONLY) {
			throw new AssertionError(String.format(Locale.ROOT, "/%s took %.0f ms (limit %.0f ms)", command, ms,
					LOCATE_BIOME_LIMIT_MS));
		}
		return String.format(Locale.ROOT, "/%s: %s (%.0f ms)", command, result.getString(), ms);
	}

	// ------------------------------------------------------------------ step S6: soils, shelf, micro-relief

	/**
	 * The 11 soil places of §12.3 (łęg A, łęg B, ols, lake reedbed, willow scrub, dry pine forest, beech forest, upper
	 * montane spruce forest, dwarf pine, beach with dunes, raised bog): center in blocks, found with seed {@value #SEED}
	 * on surface plans (the dwarf pine, the spruce forest and in the gameplay scale the willow scrub at the large massif
	 * nearest to the origin).
	 */
	static final String[] SOIL_NAMES = {"willow_poplar_forest", "ash_alder_forest", "alder_carr", "lake_reedbed",
			"willow_scrub", "dry_pine_forest", "beech_forest", "montane_spruce_forest", "dwarf_pine_scrub", "beach",
			"raised_bog"};
	static final int[][] SOIL_REAL = {{-3_904, 3_904}, {-16, 16}, {656, -672}, {-4_160, 4_096}, {-3_904, 4_032},
			{-231_054, -134_098}, {-1_200, -1_424}, {99_105, 1_034_173}, {98_289, 1_033_389}, {-233_358, -136_402},
			{-230_158, -137_746}};
	static final int[][] SOIL_GAMEPLAY = {{-640, 3_200}, {112, 64}, {96, 32}, {-1_232, 240}, {7_702, -34_380},
			{-1_072, -288}, {-32, -416}, {27_257, 3_622}, {6_854, -33_756}, {-4_160, -4_160}, {-2_944, -896}};
	/** Least number of checked columns per soil place. */
	private static final int SOIL_MIN_COLUMNS = 8;

	/**
	 * The ground block of the world equals the soil of the surface plan ({@code SoilBlocks}, §7.4) at the 11 soil places:
	 * for each place every dry column of the place's habitat in its chunk (full status) that has no tree trunk on it
	 * (a trunk turns the grass under it into dirt) must have the block of the plan at the plan's top Y, or the block of
	 * the soil it takes across a habitat border (soil ecotones, step S8b: {@code PolandChunkGenerator.soilBlend}), and air
	 * or a plant above it.
	 */
	private static String soilPlaces(MinecraftServer server, boolean real) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = generator(server);
		long seed = level.getSeed();
		int[][] places = real ? SOIL_REAL : SOIL_GAMEPLAY;
		StringBuilder report = new StringBuilder("ground block = SoilBlocks at");
		List<String> failures = new ArrayList<>();
		for (int p = 0; p < places.length; p++) {
			int x0 = places[p][0];
			int z0 = places[p][1];
			net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(x0 >> 4, z0 >> 4);
			level.getChunk(pos.x(), pos.z());
			int[] codes = new int[256];
			ChunkSurface plan = gen.surface(pos, level.getMinY(), level.getMaxY(), seed, codes, level.structureManager());
			int[][] around = new int[9][];
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int[] c = dx == 0 && dz == 0 ? codes : new int[256];
					if (c != codes) {
						gen.surface(new net.minecraft.world.level.ChunkPos(pos.x() + dx, pos.z() + dz), level.getMinY(),
								level.getMaxY(), seed, c, level.structureManager());
					}
					around[(dx + 1) * 3 + dz + 1] = c;
				}
			}
			int[] blend = gen.soilBlend(pos, around, seed);
			int center = ChunkHabitats.index(x0 & 15, z0 & 15);
			int blended = 0;
			HabitatBiome biome = Habitat.biome(codes[center]);
			int checked = 0;
			int mismatched = 0;
			int erratics = 0;
			Map<String, Integer> blocks = new java.util.TreeMap<>();
			for (int i = 0; i < 256; i++) {
				if (Habitat.biome(codes[i]) != biome || plan.wet(i)) {
					continue;
				}
				BlockPos ground = new BlockPos(pos.getMinBlockX() + (i >> 4), plan.top(i), pos.getMinBlockZ() + (i & 15));
				net.minecraft.world.level.block.state.BlockState above = level.getBlockState(ground.above());
				if (above.is(BlockTags.LOGS)) {
					continue;
				}
				checked++;
				net.minecraft.world.level.block.Block expected = MaterialStates.of(plan.topMaterial(i)).getBlock();
				net.minecraft.world.level.block.state.BlockState actual = level.getBlockState(ground);
				if (blend[i] != pl.polishforests.worldgen.surface.SoilBlend.KEEP && !actual.is(expected)
						&& actual.is(MaterialStates.of(pl.polishforests.worldgen.surface.Material.values()[blend[i] & 0xFF]).getBlock())) {
					// The soil of a habitat across a border (soil ecotone, step S8b).
					expected = actual.getBlock();
					blended++;
				}
				if (erratic(actual) || erratic(above)) {
					// A glacial erratic of step 2 (S7) lies on the ground.
					erratics++;
					continue;
				}
				boolean solidAbove = above.isSolidRender() && !above.is(BlockTags.LEAVES);
				// No exemption for the vanilla disks of sand, clay and gravel: S8 removed them from step 6.
				if (!actual.is(expected) || solidAbove) {
					mismatched++;
					if (failures.size() < 12) {
						failures.add(String.format(Locale.ROOT, "%s (%d, %d, %d): %s instead of %s, above %s", SOIL_NAMES[p],
								ground.getX(), ground.getY(), ground.getZ(), actual, expected, above));
					}
				}
				blocks.merge(plan.topMaterial(i).id().substring("minecraft:".length()), 1, Integer::sum);
			}
			if (checked < SOIL_MIN_COLUMNS) {
				failures.add(SOIL_NAMES[p] + ": only " + checked + " columns of " + biome.id());
			}
			report.append(String.format(Locale.ROOT, " %s (%d, %d) %s: %d/%d %s%s%s;", SOIL_NAMES[p], x0, z0, biome.id(),
					checked - mismatched - erratics, checked, blocks, erratics > 0 ? ", " + erratics + " under an erratic" : "",
					blended > 0 ? ", " + blended + " of a soil across a border" : ""));
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Ground blocks differ from the surface plan: " + failures + "; " + report);
		}
		return report.toString();
	}

	/** A block of a glacial erratic (§8.1). */
	private static boolean erratic(net.minecraft.world.level.block.state.BlockState state) {
		return state.is(net.minecraft.world.level.block.Blocks.GRANITE) || state.is(net.minecraft.world.level.block.Blocks.DIORITE)
				|| state.is(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE);
	}

	/** Least share of the shore columns with water beside their top block (§12.3). */
	private static final double SHORE_MIN_SHARE = 0.90;
	/** Ticks the water gets to flow after its ticks are scheduled (§12.3). */
	private static final int SPILL_TICKS = 200;
	/** Radius of a shelf area in chunks around its center chunk. */
	private static final int SHELF_RADIUS = 2;

	/**
	 * Bank shelf in the world (§7.2, §12.3) in the areas of the scale: the large river of the stage measurement, the
	 * lake reedbed, the alder carr (puddles), the willow-poplar forest place and the places of the review of S6 (round 1:
	 * oxbow lake; stream, bog woodland by a stream; round 2: stepped mountain stream, seam between channels; confluence,
	 * oxbow lake near a channel). In 5 × 5 full chunks around each area the shore columns of the surface plan (dry shelf
	 * zone or lake shore columns next to water; without the areas of round 2) must have water beside their top
	 * block in at least 90% of cases,
	 * and the shelf must make no step of 2 or more blocks where the model is flat. Then every water block of the plan's
	 * surface (also of the puddles) gets a scheduled fluid tick, as if a neighbor had changed. The plan must have no open
	 * water edge (water with air beside it at the same Y) that the model does not already have, and after
	 * {@value #SPILL_TICKS} ticks no water may stand outside the plan's water in ground that the model had (at or below the
	 * model top) or farther than {@value #FLOW_REACH} blocks from an open edge of the model; water above the model ground
	 * near such an edge (the steps of the river level in the model) is only reported. Screenshot
	 * {@code s6_shelf_<area>_<scale>}.
	 */
	private static String shelf(ClientGameTestContext context, TestSingleplayerContext sp, boolean real, String scale) {
		int[][] soil = real ? SOIL_REAL : SOIL_GAMEPLAY;
		// The four areas of S6 and the places of the review of S6, round 1: the oxbow lake (realistic scale), a stream and the
		// bog woodland by a stream (gameplay scale), where the first shelf made walls and let the water of the model's open
		// edges spread over removed ground.
		// Round 2: a stepped mountain stream with open edges across chunk borders and a seam between channels (realistic
		// scale), a confluence and oxbow lake banks near a channel (gameplay scale), where the guard of round 1 made walls
		// and pillars and let water spill into removed ground.
		// Step S6b: stepped water where the fade of the ramp made walls of 2 blocks, and an oxbow lake beyond a chunk border
		// that only one of two neighbors saw.
		int[][] areas = real
				? new int[][] {{-19_484, 11_253}, soil[3], soil[2], soil[0], {-4_389, 2_169}, {59_458, 136_152}, {87_433, -42_036},
						{74_601, -33_251}, {-27_778, -138_754}}
				: new int[][] {{-1_851, 6_022}, soil[3], soil[2], soil[0], {-134, -52}, {210, 54}, {17_086, 16_598},
						{-14_271, 22_791}, {7_350, -19_466}, {-16_353, -19_489}};
		String[] names = real ? new String[] {"river", "lake_reedbed", "alder_carr", "willow_poplar_forest", "oxbow_lake",
				"cascade", "channel_seam", "fade_step", "oxbow_border"}
				: new String[] {"river", "lake_reedbed", "alder_carr", "willow_poplar_forest", "stream", "bog_woodland_stream",
						"confluence", "oxbow_near_channel", "fade_step", "oxbow_border"};
		StringBuilder report = new StringBuilder("bank shelf:");
		long shoreAll = 0;
		long wetAll = 0;
		List<String> failures = new ArrayList<>();
		String prefix = SurfaceBuilder.debugFromSystem() ? "s6_debug_" : "s6_shelf_";
		for (int a = 0; a < areas.length; a++) {
			int[] c = areas[a];
			int ground = sp.getServer().computeOnServer(s -> s.overworld().getChunkSource().getGenerator().getBaseHeight(c[0],
					c[1], Heightmap.Types.WORLD_SURFACE_WG, s.overworld(), s.overworld().getChunkSource().randomState()));
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0.0 60.0", c[0], ground + 24, c[1]));
			context.waitTicks(20 * 12);
			String name = names[a];
			long[] counts = sp.getServer().computeOnServer(s -> shelfArea(s, c, true));
			context.waitTicks(SPILL_TICKS);
			long[] after = sp.getServer().computeOnServer(s -> shelfArea(s, c, false));
			context.takeScreenshot(prefix + name + "_" + scale);
			// counts: shore, wet shore, scheduled, shelf steps; after: S6 spills, model spills, ticks still scheduled, new
			// open edges
			if (!ROUND2_AREAS.contains(name)) {
				// The areas of round 2 check walls and spills at stepped water, where the guard keeps the banks at the
				// upper water level on purpose; their shore share is only reported.
				shoreAll += counts[0];
				wetAll += counts[1];
			}
			report.append(String.format(Locale.ROOT, " %s: shore columns %d, water beside the top %d, steps of 2 or more by the "
					+ "shelf on flat model ground %d, %d water ticks scheduled, open water edges not in the model %d, after %d "
					+ "ticks water outside the plan in removed ground or away from the open edges of the model %d, above the "
					+ "model ground near them %d (steps of the river level), ticks left %d;", name, counts[0], counts[1], counts[3],
					counts[2], after[3], SPILL_TICKS, after[0], after[1], after[2]));
			if (after[0] > 0 || after[3] > 0 || counts[3] > 0) {
				failures.add(name + ": " + after[0] + " water blocks outside the plan in removed ground or away from the model "
						+ "edges, " + after[3] + " new open edges, " + counts[3] + " shelf steps, e.g. " + SPILLS);
			}
			if (after[2] > 0.1 * Math.max(1, counts[2])) {
				failures.add(name + ": the chunks did not tick (" + after[2] + " of " + counts[2] + " ticks left)");
			}
		}
		double share = (double) wetAll / Math.max(1, shoreAll);
		report.append(String.format(Locale.ROOT, " all areas: %.1f%% of %d shore columns", 100 * share, shoreAll));
		if (shoreAll < 100 || share < SHORE_MIN_SHARE) {
			failures.add(String.format(Locale.ROOT, "shore columns with water beside the top %.1f%% of %d", 100 * share, shoreAll));
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Bank shelf: " + failures + "; " + report);
		}
		return report.toString();
	}

	/**
	 * One shelf area: before the wait ({@code schedule}) counts the shore columns and those with water beside the top
	 * block and schedules a fluid tick on every water surface block of the plan; after the wait counts the water blocks
	 * outside the plan's water (away from the open edges of the model, near them), the fluid ticks still scheduled and the
	 * open water edges of the plan that the model does not have.
	 */
	private static long[] shelfArea(MinecraftServer server, int[] center, boolean schedule) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = generator(server);
		long seed = level.getSeed();
		int ccx = center[0] >> 4;
		int ccz = center[1] >> 4;
		int n = 2 * SHELF_RADIUS + 1;
		ChunkSurface[][] plans = new ChunkSurface[n][n];
		for (int dx = 0; dx < n; dx++) {
			for (int dz = 0; dz < n; dz++) {
				level.getChunk(ccx - SHELF_RADIUS + dx, ccz - SHELF_RADIUS + dz);
				plans[dx][dz] = gen.surface(new net.minecraft.world.level.ChunkPos(ccx - SHELF_RADIUS + dx, ccz - SHELF_RADIUS + dz),
						level.getMinY(), level.getMaxY(), seed, null, level.structureManager());
			}
		}
		int x0 = (ccx - SHELF_RADIUS) << 4;
		int z0 = (ccz - SHELF_RADIUS) << 4;
		int size = n << 4;
		long[] out = new long[4];
		SPILLS.clear();
		for (int x = x0; x < x0 + size; x++) {
			for (int z = z0; z < z0 + size; z++) {
				ChunkSurface plan = plans[(x - x0) >> 4][(z - z0) >> 4];
				int i = ChunkHabitats.index(x & 15, z & 15);
				int top = plan.top(i);
				if (schedule) {
					if ((plan.flags(i) & ChunkSurface.SHORE) != 0) {
						out[0]++;
						for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
							if (level.getFluidState(new BlockPos(x + d[0], top, z + d[1])).is(net.minecraft.tags.FluidTags.WATER)) {
								out[1]++;
								break;
							}
						}
					}
					if (plan.wet(i)) {
						BlockPos pos = new BlockPos(x, plan.waterTop(i), z);
						if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
							level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
							out[2]++;
						}
					}
					continue;
				}
				if (plan.wet(i) && level.getFluidTicks().hasScheduledTick(new BlockPos(x, plan.waterTop(i), z), Fluids.WATER)) {
					out[2]++;
				}
			}
		}
		if (schedule) {
			// Steps of 2 or more blocks between neighboring dry columns, one of them lowered by the shelf, where the model
			// is flat (review of S6, round 1: walls of 2–3 blocks at the inner edge of the first shelf). The micro-relief
			// (puddles −1, hummocks +1) is taken out.
			for (int x = x0; x < x0 + size - 1; x++) {
				for (int z = z0; z < z0 + size - 1; z++) {
					ChunkSurface plan = plans[(x - x0) >> 4][(z - z0) >> 4];
					int i = ChunkHabitats.index(x & 15, z & 15);
					if (plan.wet(i)) {
						continue;
					}
					for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
						ChunkSurface np = plans[(x + d[0] - x0) >> 4][(z + d[1] - z0) >> 4];
						int j = ChunkHabitats.index((x + d[0]) & 15, (z + d[1]) & 15);
						if (np.wet(j)) {
							continue;
						}
						int a = shelfTop(plan, i);
						int b = shelfTop(np, j);
						boolean lowered = a < plan.modelTop(i) || b < np.modelTop(j);
						if (lowered && Math.abs(a - b) >= 2 && plan.modelTop(i) == np.modelTop(j)) {
							out[3]++;
						}
					}
				}
			}
			return out;
		}
		// Open water edges (a water block with air beside it at the same Y) of the plan and of the model: water can leave
		// the plan only through them. The plan must have no edge that the model does not have.
		List<int[]> modelEdges = new ArrayList<>();
		for (int x = x0; x < x0 + size; x++) {
			for (int z = z0; z < z0 + size; z++) {
				ChunkSurface plan = plans[(x - x0) >> 4][(z - z0) >> 4];
				int i = ChunkHabitats.index(x & 15, z & 15);
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nx = x + d[0] - x0;
					int nz = z + d[1] - z0;
					if (nx < 0 || nz < 0 || nx >= size || nz >= size) {
						continue;
					}
					ChunkSurface np = plans[nx >> 4][nz >> 4];
					int j = ChunkHabitats.index(nx & 15, nz & 15);
					int w = plan.wet(i) ? plan.waterTop(i) : ChunkSurface.NO_WATER;
					boolean open = w != ChunkSurface.NO_WATER && np.top(j) < w && (np.wet(j) ? np.waterTop(j) : ChunkSurface.NO_WATER) < w;
					int mw = plan.modelWater(i);
					boolean modelOpen = mw != ChunkSurface.NO_WATER && np.modelTop(j) < mw && np.modelWater(j) < mw;
					if (modelOpen) {
						modelEdges.add(new int[] {x + d[0], z + d[1]});
					}
					if (open && !(modelOpen && mw == w)) {
						out[3]++;
					}
				}
			}
		}
		for (int x = x0; x < x0 + size; x++) {
			for (int z = z0; z < z0 + size; z++) {
				ChunkSurface plan = plans[(x - x0) >> 4][(z - z0) >> 4];
				int i = ChunkHabitats.index(x & 15, z & 15);
				int top = plan.top(i);
				int high = plan.wet(i) ? plan.waterTop(i) : top;
				for (int y = top + 1; y <= high + 2; y++) {
					if (plan.wet(i) && y <= plan.waterTop(i)) {
						continue;
					}
					if (!level.getFluidState(new BlockPos(x, y, z)).is(net.minecraft.tags.FluidTags.WATER)) {
						continue;
					}
					// Water outside the plan: caused by S6 when it stands in ground that the model had (y at or below
					// the model top: removed by the shelf or the micro-relief), or away from the reach of the water
					// flowing from an open edge of the model (a step of the river level); otherwise the model's own.
					boolean model = y > plan.modelTop(i);
					if (model) {
						model = false;
						for (int[] e : modelEdges) {
							if (Math.abs(e[0] - x) + Math.abs(e[1] - z) <= FLOW_REACH) {
								model = true;
								break;
							}
						}
					}
					out[model ? 1 : 0]++;
					if (!model && SPILLS.size() < 8) {
						SPILLS.add(String.format(Locale.ROOT, "(%d, %d, %d) %s, plan top %d, model top %d", x, y, z,
								y <= plan.modelTop(i) ? "removed ground" : "away from the model edges", top, plan.modelTop(i)));
					}
				}
			}
		}
		return out;
	}

	/** Shelf areas of the review of S6, round 2 (stepped water), left out of the shore share. */
	private static final java.util.Set<String> ROUND2_AREAS = java.util.Set.of("cascade", "channel_seam", "confluence",
			"oxbow_near_channel", "fade_step");

	/** First water blocks counted as S6 spills in the last area (for the failure message). */
	private static final List<String> SPILLS = new ArrayList<>();

	/** Top of a plan column after the shelf, without the micro-relief (puddles −1, hummocks +1). */
	private static int shelfTop(ChunkSurface plan, int i) {
		int flags = plan.flags(i);
		return plan.top(i) + ((flags & ChunkSurface.PUDDLE) != 0 ? 1 : 0) - ((flags & ChunkSurface.HUMMOCK) != 0 ? 1 : 0);
	}

	/** Horizontal reach of water flowing from a source block (blocks). */
	private static final int FLOW_REACH = 8;

	/** Sections written block by block because their states did not fit in a palette (budget §3.6: below 1%). */
	private static String packing() {
		long packed = PolandChunkGenerator.PACKED_SECTIONS.sum();
		long fallbacks = PolandChunkGenerator.PACK_FALLBACKS.sum();
		String report = String.format(Locale.ROOT, "PACK_FALLBACKS %d of %d packed sections (%.3f%%), %d neighbor samples "
				+ "outside the chunk for the shelf (%d from the cache, %d samples reused) in %d chunks", fallbacks, packed,
				100.0 * fallbacks / Math.max(1, packed), SurfaceBuilder.OUTSIDE_SAMPLES.sum(), SurfaceBuilder.OUTSIDE_HITS.sum(),
				SurfaceBuilder.REUSED_SAMPLES.sum(), PolandChunkGenerator.CHUNKS.sum());
		if (packed == 0 || fallbacks > 0.01 * packed) {
			throw new AssertionError(report);
		}
		return report;
	}
}
