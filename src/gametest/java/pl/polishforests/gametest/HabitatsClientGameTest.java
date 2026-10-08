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
import pl.polishforests.PolishForests;
import pl.polishforests.climate.BiomeClimateAccess;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.feature.TreeStandFeature;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;

/**
 * Habitat biomes in the game (step S5, docs/03-m2-biomy.md §3.5, §10, §12.3), in both world scales: the world starts
 * with the mod's 36 biomes, {@code generator.validate()} (feature order) passes, {@code level.getBiome} equals the
 * classifier at 8 places with different biomes, F3 shows {@code polishforests:*} and the habitat line (screenshot
 * {@code habitats_f3_<scale>}), {@code /locate structure} finds a stronghold, a mineshaft and trial chambers,
 * {@code /locate biome polishforests:oak_hornbeam_forest} takes less than 2 s, the BIOMES stage takes at most 0.2 ms per
 * chunk in the lowland (measured also in the Beskids and at a large river), the tree stand census matches the palette
 * within ±20%, and revisited areas keep their chunk habitats. Runs when {@code -Dpolishforests.gametest} is {@code habitats} or {@code all}.
 */
public final class HabitatsClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int PLACES = 8;
	/** Spiral step of the place search in blocks. */
	private static final int SEARCH_STEP = 52;
	private static final double LOCATE_BIOME_LIMIT_MS = 2_000;
	private static final double BIOMES_LIMIT_MS = 0.2;

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polishforests.gametest", "all");
		if (!mode.equals("habitats") && !mode.equals("all")) {
			return;
		}
		for (PolandScale scale : PolandScale.values()) {
			ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET,
					PolishForests.id(scale == PolandScale.REALISTIC ? "poland" : "poland_gameplay"));
			try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset)).create()) {
				sp.getServer().runCommand("gamerule advance_time false");
				sp.getServer().runCommand("gamerule advance_weather false");
				sp.getServer().runCommand("time set 6000");
				sp.getServer().runCommand("weather clear");
				sp.getServer().runCommand("gamemode spectator @a");
				String name = scale.getSerializedName();
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
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(s -> biomesPerChunk(s, real)));
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(HabitatsClientGameTest::habitatMisses));
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(s -> census(s, real, places)));
				PolishForests.LOG.info("[habitats] {}: {}", name, revisit(context, sp, real));
			}
		}
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
	 * Regression limit of BIOMES outside the lowland: in the Beskids one sample may cost up to the D1 budget (12 µs REAL,
	 * 16 µs GAMEPLAY), so 16 samples alone can exceed the 0.2 ms of §3.6 (deviation of S5 for the user's decision,
	 * docs/03-m2-biomy.md §3.5.1); the test only guards against a further regression.
	 */
	private static final double BIOMES_REGRESSION_MS = 0.5;

	/**
	 * BIOMES stage on one thread (the server thread asks one chunk after another): for each area 64 new chunks (8 × 8,
	 * cold grid caches of the area) and then the next 64 chunks to the east (warmer caches); mean time per chunk. The
	 * lowland must meet the budget of §3.6 (0.2 ms); the Beskids and the river are reported and guarded by
	 * {@link #BIOMES_REGRESSION_MS}.
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
			double limit = a == 0 ? BIOMES_LIMIT_MS : BIOMES_REGRESSION_MS;
			if (Math.max(ms[0], ms[1]) > limit) {
				throw new AssertionError(String.format(Locale.ROOT, "BIOMES in %s %.3f / %.3f ms per chunk (limit %.1f ms)",
						BIOMES_AREAS[a], ms[0], ms[1], limit));
			}
		}
		return report.append(String.format(Locale.ROOT, " budget %.1f ms (lowland)", BIOMES_LIMIT_MS)).toString();
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
	 * comparison; for each biome the trunks (a log on the top ground block) against the palette's trees per chunk times
	 * the number of the biome's dry columns / 256. Each biome with at least {@value #CENSUS_MIN_EXPECTED} expected trees
	 * must be within ±20%. Dry beach and white dune columns must have a sand top, and dry forest columns are reported
	 * when their top is bare sand.
	 */
	private static String census(MinecraftServer server, boolean real, List<int[]> places) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = generator(server);
		long seed = level.getSeed();
		LandscapeModel m = gen.model(seed);
		HabitatClassifier k = gen.classifier(seed);
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
			for (int x = cx0 * 16; x < (cx0 + 5) * 16; x++) {
				for (int z = cz0 * 16; z < (cz0 + 5) * 16; z++) {
					ColumnSample s = m.sample(x, z);
					int top = gen.vertical().topBlockY(s.surface());
					if (s.hasWater() && gen.vertical().topBlockY(s.waterLevel()) > top) {
						continue;
					}
					HabitatBiome b = Habitat.biome(k.classify(s, x, z));
					expected[b.ordinal()] += treesPerChunk(stand, b) / 256.0;
					if (level.getBlockState(new BlockPos(x, top + 1, z)).is(BlockTags.LOGS)) {
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

	private static float treesPerChunk(TreeStandFeature stand, HabitatBiome b) {
		return stand.palette().rules().stream().filter(r -> r.biomes().contains(b)).findFirst()
				.map(r -> r.treesPerChunk()).orElse(0.0F);
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
		if (command.startsWith("locate biome") && ms > LOCATE_BIOME_LIMIT_MS) {
			throw new AssertionError(String.format(Locale.ROOT, "/%s took %.0f ms (limit %.0f ms)", command, ms,
					LOCATE_BIOME_LIMIT_MS));
		}
		return String.format(Locale.ROOT, "/%s: %s (%.0f ms)", command, result.getString(), ms);
	}
}
