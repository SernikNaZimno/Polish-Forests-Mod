package pl.polishforests.gametest;

import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.PolishForests;
import pl.polishforests.season.SeasonalProperties;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.ModBiomeKeys;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.feature.TreeStandFeature;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.vanilla.VanillaForestWorld;
import pl.polishforests.worldgen.vanilla.VanillaForests;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * The world type "Default + Polish Forests" of step W1 (docs/04-pionowy-wycinek.md), {@code -Pgametest=vanilla_forests}
 * (only by name). A world of the preset {@code polishforests:default_polish_forests} with a fixed seed, then
 * <ol>
 * <li>a census of the surface biomes on a 128-block lattice over 16 × 16 km: no replaced vanilla forest, the share of each
 * habitat biome;</li>
 * <li>{@code /locate biome} of every habitat biome of that world (the biome search of the source, and once as a command),
 * the command for a replaced vanilla forest (expected: not found), {@code /polishforests find biome} and
 * {@code /polishforests here};</li>
 * <li>carvers: the source chunks of replaced forests within 8 chunks of a fresh oak-hornbeam chunk carve with the vanilla
 * forest's carvers (round 1 of the W1 review);</li>
 * <li>an area of 8 × 8 chunks in the oak-hornbeam and in the fresh pine forest: trunk bases of the mod on the forest
 * columns (expected: some), blocks of the mod on the foreign columns at least {@value #MARGIN} blocks from any forest
 * column (expected: none), the spawns of the habitat biome;</li>
 * <li>determinism: 5 × 5 chunks of the oak-hornbeam forest generated in raster order in the first world and in the
 * reverse order in a second world of the same seed; the terrain with the chunk habitats (the soil tops, at the end of
 * TERRAIN) must be the same block by block, the decorated surface may differ in a few columns (decoration across chunk
 * borders, at most {@value #DECORATION_TOLERANCE} of the columns);</li>
 * <li>with {@code -Psites=load}: instead of the above, the load of new terrain (chunks per second, three teleports as
 * {@code ForestPerformance}) in this world type and in vanilla Default with the same seed, with {@code -Poptimization}
 * the C2ME comparison of step W1 (reported only, no threshold).</li>
 * </ol>
 * Report {@code build/vanilla_forests/report.txt} (and {@code load.txt}), log lines {@code [vanilla_forests]}.
 */
public final class VanillaForestsClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20261104";
	private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("default_polish_forests"));
	/** Foreign columns at least this far from a forest column must carry no block of the mod (crowns, roots, fallen logs). */
	static final int MARGIN = 12;
	/**
	 * Share of the decorated columns that may differ between the two chunk orders: vanilla decoration alone 0.95%, with
	 * the trees of the mod 2.95% (stage 2 of W1; crowns, roots and fallen logs written across a chunk border depend on the
	 * order as in a "Poland" world, whose test {@code determinism} sees 30–98% of the columns differ in reverse order).
	 */
	static final double DECORATION_TOLERANCE = 0.05;
	private static final int CENSUS_CHUNKS = 8;
	private static final int ORDER_CHUNKS = 5;
	private static final int RENDER_DISTANCE = 12;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"vanilla_forests".equals(System.getProperty("polishforests.gametest", "all"))) {
			return;
		}
		Path out = Path.of(System.getProperty("polishforests.vanillaForests.out", "build/vanilla_forests"));
		if (System.getProperty("polishforests.sites", "").contains("load")) {
			load(context, out);
			return;
		}
		List<String> failures = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		long[][] first;
		BlockPos oakHornbeam;
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, PRESET, true)).create()) {
			prepare(sp);
			// 1. Census of the surface biomes.
			sp.getServer().runOnServer(s -> census(s.overworld(), report, failures));
			// 2. Locate.
			Map<HabitatBiome, BlockPos> located = new TreeMap<>();
			for (HabitatBiome b : new java.util.LinkedHashSet<>(VanillaForests.REPLACEMENTS.values())) {
				String line = sp.getServer().computeOnServer(s -> locate(s.overworld(), b, located));
				report.append(line).append('\n');
				if (line.contains("not found")) {
					failures.add(line);
				}
			}
			for (String command : List.of("locate biome polishforests:oak_hornbeam_forest", "locate biome minecraft:forest",
					"polishforests find biome fresh_pine_forest", "polishforests find biome montane_spruce_forest",
					"polishforests find biome bog_woodland", "polishforests find zone sea", "polishforests here")) {
				String line = sp.getServer().computeOnServer(s -> command(s, BlockPos.ZERO, command));
				report.append(line).append('\n');
				if (line.endsWith("no message")) {
					failures.add(line);
				}
			}
			// Probe: the habitats of a fresh chunk of the mixed forest at the end of TERRAIN (before any feature).
			BlockPos probeAt = located.get(HabitatBiome.MIXED_FOREST);
			if (probeAt != null) {
				String probeLine = sp.getServer().computeOnServer(s -> probe(s.overworld(), probeAt));
				report.append(probeLine).append('\n');
			}
			// Carvers: the source chunks around a fresh chunk of an oak-hornbeam forest carve with the vanilla biome's carvers.
			BlockPos carverAt = sp.getServer().computeOnServer(s -> {
				Pair<BlockPos, Holder<Biome>> hit = s.overworld().findClosestBiome3d(
						h -> h.is(ModBiomeKeys.key(HabitatBiome.OAK_HORNBEAM_FOREST)), new BlockPos(-3072, 64, -3072), 6400, 32, 64);
				return hit == null ? null : hit.getFirst();
			});
			if (carverAt == null) {
				failures.add("no oak-hornbeam forest for the carver check");
			} else {
				String carverLine = sp.getServer().computeOnServer(s -> carvers(s.overworld(), carverAt, failures));
				report.append(carverLine).append('\n');
				PolishForests.LOG.info("[vanilla_forests] " + carverLine);
			}
			// 4. Determinism, the first order: an oak-hornbeam forest away from the spawn (the chunks around the player are
			// generated by the client's view, in no fixed order).
			oakHornbeam = sp.getServer().computeOnServer(s -> {
				Pair<BlockPos, Holder<Biome>> hit = s.overworld().findClosestBiome3d(
						h -> h.is(ModBiomeKeys.key(HabitatBiome.OAK_HORNBEAM_FOREST)), new BlockPos(3072, 64, 3072), 6400, 32, 64);
				return hit == null ? null : hit.getFirst();
			});
			if (oakHornbeam == null) {
				failures.add("no oak-hornbeam forest for the determinism check");
			}
			report.append("determinism area around ").append(oakHornbeam).append('\n');
			BlockPos center = oakHornbeam != null ? oakHornbeam : new BlockPos(3072, 64, 3072);
			first = sp.getServer().computeOnServer(s -> order(s.overworld(), center, false));
			// 3. The areas of the forests of the tree planner in W1 (the two slice forests; step F1: the lowland beech forest,
			// dark_forest in W1; step F2: the alder carr, the swamp in W1, with the planner's black alders).
			for (HabitatBiome b : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.FRESH_PINE_FOREST,
					HabitatBiome.LOWLAND_BEECH_FOREST, HabitatBiome.ALDER_CARR)) {
				BlockPos found = located.get(b);
				if (found == null) {
					continue;
				}
				// A forest of the steps F: the area around its deepest part near the located column (the located column is
				// the forest's edge, whose area of 8 chunks may hold no deep column at all, KF-1).
				BlockPos at = java.util.Arrays.asList(pl.polishforests.worldgen.feature.plan.ForestBlend.TRANSECT).contains(b) ? found
						: sp.getServer().computeOnServer(s -> deepCenter(s.overworld(), b, found));
				sp.getServer().runOnServer(s -> area(s.overworld(), b, at, report, failures));
			}
			report.append(String.format(Locale.ROOT, "counters: habitats attached %d, soil tops %d, soil blended %d, trees %d, "
					+ "planned %d, skipped on blocked columns %d, at structures or water %d, on the trunk base %d, unwritten %d, "
					+ "suppressed %d%n", VanillaForestWorld.ATTACHED.sum(),
					VanillaForestWorld.SOIL_TOPS.sum(), VanillaForestWorld.SOIL_BLENDED.sum(), TreeStandFeature.TREES.sum(),
					TreeStandFeature.PLANNED.sum(), TreeStandFeature.SKIPPED_BLOCKED.sum(), TreeStandFeature.SKIPPED_STRUCTURE.sum(),
					TreeStandFeature.SKIPPED_BASE.sum(), TreeStandFeature.SKIPPED_WRITE.sum(),
					TreeStandFeature.SKIPPED_SUPPRESSED.sum()));
		}
		// 4. Determinism, the reverse order in a second world of the same seed.
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, PRESET, true)).create()) {
			prepare(sp);
			BlockPos center = oakHornbeam != null ? oakHornbeam : new BlockPos(3072, 64, 3072);
			long[][] second = sp.getServer().computeOnServer(s -> order(s.overworld(), center, true));
			int terrain = 0;
			int decoration = 0;
			int[] byBorder = new int[8];
			for (int i = 0; i < first[0].length; i++) {
				terrain += first[0][i] != second[0][i] ? 1 : 0;
				if (first[1][i] != second[1][i]) {
					decoration++;
					// Index (chunk * 256 + x * 16 + z): the distance of the column from the nearest border of its chunk.
					int x = (i & 255) >> 4;
					int z = i & 15;
					byBorder[Math.min(Math.min(x, 15 - x), Math.min(z, 15 - z))]++;
				}
			}
			String line = String.format(Locale.ROOT, "determinism (%d columns, raster against reverse order): terrain differs in "
					+ "%d, decorated surface in %d (%.2f%%; by distance from the chunk border 0..7: %s)", first[0].length, terrain,
					decoration, 100.0 * decoration / first[0].length, java.util.Arrays.toString(byBorder));
			report.append(line).append('\n');
			if (terrain > 0 || decoration > DECORATION_TOLERANCE * first[0].length) {
				failures.add(line);
			}
		}
		write(out.resolve("report.txt"), report, failures);
		if (!failures.isEmpty()) {
			throw new AssertionError("vanilla_forests: " + String.join("; ", failures));
		}
	}

	private static void select(WorldCreationUiState ui, ResourceKey<WorldPreset> preset, boolean ours) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		// Structures as in a normal world: the forests must make room for the villages and the mansions.
		ui.setGenerateStructures(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset() != null && e.preset().is(preset)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier())));
		boolean wrapped = ui.getSettings().selectedDimensions().overworld().getBiomeSource() instanceof VanillaForestsBiomeSource;
		if (wrapped != ours) {
			throw new AssertionError("The preset " + preset.identifier() + (ours ? " has no" : " has a")
					+ " biome source polishforests:vanilla_forests");
		}
	}

	private static void prepare(TestSingleplayerContext sp) {
		sp.getServer().runCommand("gamerule advance_time false");
		sp.getServer().runCommand("gamerule advance_weather false");
		sp.getServer().runCommand("gamerule random_tick_speed 0");
		sp.getServer().runCommand("time set 6000");
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("gamemode spectator @a");
	}

	private static VanillaForestWorld world(ServerLevel level) {
		return ((VanillaForestsBiomeSource) level.getChunkSource().getGenerator().getBiomeSource()).world();
	}

	/** Surface biomes on a 128-block lattice over 16 × 16 km around the origin. */
	private static void census(ServerLevel level, StringBuilder report, List<String> failures) {
		VanillaForestWorld world = world(level);
		Map<String, Integer> counts = new TreeMap<>();
		int n = 0;
		for (int x = -8192; x <= 8192; x += 128) {
			for (int z = -8192; z <= 8192; z += 128) {
				counts.merge(world.surfaceBiome(x, z).getRegisteredName(), 1, Integer::sum);
				n++;
			}
		}
		int mod = 0;
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			String id = e.getKey();
			if (id.startsWith("minecraft:") && VanillaForests.replacement(id.substring("minecraft:".length())) != null) {
				failures.add("census: replaced vanilla forest " + id + " in " + e.getValue() + " samples");
			}
			mod += id.startsWith(PolishForests.MOD_ID + ":") ? e.getValue() : 0;
		}
		report.append(String.format(Locale.ROOT, "census: %d samples, habitat biomes %.1f%%%n", n, 100.0 * mod / n));
		int samples = n;
		counts.forEach((id, c) -> report.append(String.format(Locale.ROOT, "  %-40s %5.1f%%%n", id, 100.0 * c / samples)));
		if (mod == 0) {
			failures.add("census: no habitat biome");
		}
	}

	private static String locate(ServerLevel level, HabitatBiome biome, Map<HabitatBiome, BlockPos> located) {
		ResourceKey<Biome> key = ModBiomeKeys.key(biome);
		long t0 = System.nanoTime();
		Pair<BlockPos, Holder<Biome>> hit = level.findClosestBiome3d(b -> b.is(key), BlockPos.ZERO.atY(64), 6400, 32, 64);
		double ms = (System.nanoTime() - t0) / 1e6;
		if (hit == null) {
			return String.format(Locale.ROOT, "locate biome %s: not found within 6400 (%.0f ms)", key.identifier(), ms);
		}
		BlockPos p = hit.getFirst();
		located.put(biome, p);
		return String.format(Locale.ROOT, "locate biome %s: (%d, %d, %d), %.0f blocks away, getBiome there %s, surface biome %s (%.0f ms)",
				key.identifier(), p.getX(), p.getY(), p.getZ(), Math.sqrt(p.distSqr(BlockPos.ZERO.atY(p.getY()))),
				level.getBiome(p).getRegisteredName(), world(level).surfaceBiome(p.getX(), p.getZ()).getRegisteredName(), ms);
	}

	private static String command(MinecraftServer server, BlockPos at, String command) {
		List<Component> messages = new ArrayList<>();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(new CommandSource() {
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
		}).withLevel(server.overworld()).withPosition(Vec3.atCenterOf(at.atY(100))), command);
		return "/" + command + ": " + (messages.isEmpty() ? "no message" : messages.getLast().getString());
	}

	/**
	 * The chunk habitats of one chunk generated only to the end of TERRAIN: the habitat columns, the blocked ones by reason
	 * (no soil on top, steep), water and structure pieces, and the top blocks of the habitat columns.
	 */
	private static String probe(ServerLevel level, BlockPos at) {
		ChunkAccess chunk = level.getChunkSource().getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.TERRAIN, true);
		ChunkHabitats h = chunk.getAttached(ModFeatures.CHUNK_HABITATS);
		if (h == null) {
			return "probe at " + at.getX() + ", " + at.getZ() + ": no habitats attached at the end of TERRAIN";
		}
		int forest = 0;
		int blocked = 0;
		int noSoil = 0;
		int steep = 0;
		int water = 0;
		int pieces = 0;
		Map<String, Integer> tops = new TreeMap<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 256; i++) {
			blocked += h.blocked(i) ? 1 : 0;
			if (!VanillaForestWorld.forest(h.codes()[i])) {
				continue;
			}
			forest++;
			BlockState top = chunk.getBlockState(p.set(chunk.getPos().getMinBlockX() + (i >> 4), h.top()[i],
					chunk.getPos().getMinBlockZ() + (i & 15)));
			tops.merge(BuiltInRegistries.BLOCK.getKey(top.getBlock()).getPath(), 1, Integer::sum);
			noSoil += VanillaForestWorld.soil(top) ? 0 : 1;
			steep += VanillaForestWorld.steep(h.top(), i) ? 1 : 0;
			water += h.hasWater(i) ? 1 : 0;
			pieces += h.pieceDistance(i) <= TreeStandFeature.PIECE_GAP ? 1 : 0;
		}
		return String.format(Locale.ROOT, "probe chunk (%d, %d) at the end of TERRAIN: habitat columns %d, blocked %d (of the "
				+ "habitat columns without soil %d, steep %d), under water %d, at structure pieces %d, tops %s", chunk.getPos().x(),
				chunk.getPos().z(), forest, blocked, noSoil, steep, water, pieces, tops);
	}

	/** The minimum number of forest source chunks of the carver check (17 × 17 chunks around an oak-hornbeam chunk). */
	static final int CARVER_FOREST_SOURCES = 20;

	/**
	 * The carvers of the forests (round 1 of the W1 review): a fresh chunk of an oak-hornbeam forest is generated to TERRAIN.
	 * Each source chunk within 8 chunks of it then holds the carver settings its carvers used ({@code ChunkAccess.carverBiome},
	 * filled by the carvers of the TERRAIN stage), which for a source chunk of a replaced forest must be those of the vanilla
	 * biome before the swap (caves, cave_extra_underground, canyon), not the empty list of the habitat biome.
	 */
	@SuppressWarnings("deprecation")
	private static String carvers(ServerLevel level, BlockPos at, List<String> failures) {
		int cx = at.getX() >> 4;
		int cz = at.getZ() >> 4;
		level.getChunkSource().getChunk(cx, cz, ChunkStatus.TERRAIN, true);
		var gen = level.getChunkSource().getGenerator();
		VanillaForestsBiomeSource source = (VanillaForestsBiomeSource) gen.getBiomeSource();
		BiomeResolver vanilla = source.source().createUncachedResolver(level.getChunkSource().randomState());
		int forest = 0;
		int matching = 0;
		int uncached = 0;
		int canyon = 0;
		List<String> wrong = new ArrayList<>();
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				ChunkPos pos = new ChunkPos(cx + dx, cz + dz);
				Holder<Biome> biome = vanilla.getNoiseBiome(QuartPos.fromBlock(pos.getMinBlockX()), 0,
						QuartPos.fromBlock(pos.getMinBlockZ()));
				if (source.swap(biome) == biome) {
					continue;
				}
				forest++;
				ChunkAccess chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.STRUCTURE_STARTS, false);
				BiomeGenerationSettings used = chunk == null ? null : chunk.carverBiome(() -> null);
				if (used == null) {
					uncached++;
					continue;
				}
				List<String> expected = new ArrayList<>();
				gen.getBiomeGenerationSettings(biome).getCarvers().forEach(h -> expected.add(h.getRegisteredName()));
				List<String> actual = new ArrayList<>();
				used.getCarvers().forEach(h -> actual.add(h.getRegisteredName()));
				if (expected.equals(actual)) {
					matching++;
					canyon += actual.contains("minecraft:canyon") ? 1 : 0;
				} else if (wrong.size() < 4) {
					wrong.add(pos.x() + "," + pos.z() + " " + biome.getRegisteredName() + " " + actual + " instead of " + expected);
				}
			}
		}
		int mismatched = forest - uncached - matching;
		String line = String.format(Locale.ROOT, "carvers around chunk (%d, %d): source chunks of replaced forests %d, with the "
				+ "vanilla carvers %d (with a canyon %d), other carvers %d %s, settings not cached %d", cx, cz, forest, matching,
				canyon, mismatched, wrong, uncached);
		if (forest < CARVER_FOREST_SOURCES || mismatched > 0 || matching == 0) {
			failures.add(line);
		}
		return line;
	}

	/** Whether the surface biome of the column is a habitat biome (the zoomed biome at the ground, as the chunk habitats). */
	private static boolean forestColumn(ServerLevel level, int x, int ground, int z) {
		return level.getBiome(new BlockPos(x, ground, z)).unwrapKey().map(k -> k.identifier().getNamespace()
				.equals(PolishForests.MOD_ID)).orElse(false);
	}

	private static boolean mod(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals(PolishForests.MOD_ID);
	}

	/** Loads the chunks of a square of {@code n} chunks around a block to FULL (TERRAIN and FEATURES first, raster order). */
	private static int[] loadSquare(ServerLevel level, BlockPos center, int n) {
		int cx0 = (center.getX() >> 4) - n / 2;
		int cz0 = (center.getZ() >> 4) - n / 2;
		for (ChunkStatus status : List.of(ChunkStatus.TERRAIN, ChunkStatus.FEATURES)) {
			for (int cx = cx0 - 1; cx <= cx0 + n; cx++) {
				for (int cz = cz0 - 1; cz <= cz0 + n; cz++) {
					level.getChunkSource().getChunk(cx, cz, status, true);
				}
			}
		}
		for (int cx = cx0; cx < cx0 + n; cx++) {
			for (int cz = cz0; cz < cz0 + n; cz++) {
				level.getChunk(cx, cz);
			}
		}
		return new int[] {cx0 << 4, cz0 << 4};
	}

	/** The census of an area of a habitat biome: trunks on the forest columns, blocks of the mod far from the forests. */
	private static void area(ServerLevel level, HabitatBiome biome, BlockPos at, StringBuilder report, List<String> failures) {
		int size = CENSUS_CHUNKS * 16;
		int[] origin = loadSquare(level, at, CENSUS_CHUNKS);
		var gen = level.getChunkSource().getGenerator();
		int[] ground = new int[size * size];
		boolean[] forest = new boolean[size * size];
		int forestColumns = 0;
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				int x = origin[0] + dx;
				int z = origin[1] + dz;
				int i = dx * size + dz;
				ground[i] = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
				forest[i] = forestColumn(level, x, ground[i], z);
				forestColumns += forest[i] ? 1 : 0;
			}
		}
		// Step F1 (KF-1): the deep columns of the biome, at least DEEP·k blocks from a column of another biome (a summed table
		// of the other columns), with the planner's trunks (mod logs at the base) and the vanilla logs standing as trunks.
		int deepReach = (int) Math.round(DEEP * world(level).localScale());
		int[] other = new int[(size + 1) * (size + 1)];
		boolean[] ownColumn = new boolean[size * size];
		ResourceKey<Biome> own = ModBiomeKeys.key(biome);
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				boolean mine = level.getBiome(new BlockPos(origin[0] + dx, ground[dx * size + dz], origin[1] + dz)).is(own);
				ownColumn[dx * size + dz] = mine;
				other[(dx + 1) * (size + 1) + dz + 1] = (mine ? 0 : 1) + other[dx * (size + 1) + dz + 1]
						+ other[(dx + 1) * (size + 1) + dz] - other[dx * (size + 1) + dz];
			}
		}
		int deepColumns = 0;
		int deepTrunks = 0;
		int deepVanilla = 0;
		int deepDry = 0;
		int deepDryTrunks = 0;
		int ownColumns = 0;
		int ownTrunks = 0;
		int ownVanilla = 0;
		List<String> ownVanillaAt = new ArrayList<>();
		int ownVanillaNear = 0;
		int ownBlocked = 0;
		int ownBuilding = 0;
		int ownOtherHabitat = 0;
		long ownGroundY = 0;
		int ownWater = 0;
		int ownDryTrunks = 0;
		int trunks = 0;
		int edgeTrunks = 0;
		int farColumns = 0;
		int farMod = 0;
		Map<String, Integer> farBlocks = new TreeMap<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				int i = dx * size + dz;
				int x = origin[0] + dx;
				int z = origin[1] + dz;
				BlockState base = level.getBlockState(p.set(x, ground[i], z));
				boolean trunk = mod(base) && base.is(BlockTags.LOGS);
				boolean underWater = !level.getFluidState(p.set(x, ground[i], z)).isEmpty();
				boolean vanillaTrunk = !mod(base) && base.is(BlockTags.LOGS) && level.getBlockState(p.set(x, ground[i] + 1, z)).is(BlockTags.LOGS)
						&& level.getBlockState(p.set(x, ground[i] + 2, z)).is(BlockTags.LOGS);
				if (ownColumn[i]) {
					ownColumns++;
					ownTrunks += trunk ? 1 : 0;
					if (vanillaTrunk) {
						// Review of F1, round 1: how far the trunk stands from a column of another biome, and that biome. Within
						// EDGE_MARGIN blocks the trunk can belong to the neighbor forest (the M2 substitutes of the forests not yet
						// on the planner are vanilla logs; their palette reaches across the border in the ecotone belt, rule Z10),
						// so it is reported, not a failure; deeper it is.
						int edge = 0;
						while (edge < 16 && deep(other, size, dx, dz, edge)) {
							edge++;
						}
						String neighbor = "?";
						for (int ex = Math.max(0, dx - edge); ex <= Math.min(size - 1, dx + edge) && neighbor.equals("?"); ex++) {
							for (int ez = Math.max(0, dz - edge); ez <= Math.min(size - 1, dz + edge); ez++) {
								if (!ownColumn[ex * size + ez]) {
									neighbor = level.getBiome(new BlockPos(origin[0] + ex, ground[ex * size + ez], origin[1] + ez)).getRegisteredName();
									break;
								}
							}
						}
						boolean near = edge <= EDGE_MARGIN;
						ownVanilla += near ? 0 : 1;
						ownVanillaNear += near ? 1 : 0;
						if (ownVanillaAt.size() < 6) {
							ownVanillaAt.add(String.format(Locale.ROOT, "%s at (%d, %d, %d), %s within %d blocks", BuiltInRegistries.BLOCK
									.getKey(base.getBlock()), x, ground[i], z, neighbor, edge));
						}
					}
					ownGroundY += ground[i];
					ownWater += underWater ? 1 : 0;
					ownDryTrunks += trunk && !underWater ? 1 : 0;
					ChunkHabitats habitats = level.getChunk(x >> 4, z >> 4).getAttached(ModFeatures.CHUNK_HABITATS);
					if (habitats != null) {
						int column = ChunkHabitats.index(x & 15, z & 15);
						ownBlocked += habitats.blocked(column) ? 1 : 0;
						ownBuilding += habitats.pieceDistance(column) < 15 ? 1 : 0;
						ownOtherHabitat += habitats.biome(x & 15, z & 15) != biome ? 1 : 0;
					}
				}
				if (deep(other, size, dx, dz, deepReach)) {
					deepColumns++;
					deepTrunks += trunk ? 1 : 0;
					deepVanilla += vanillaTrunk ? 1 : 0;
					deepDry += underWater ? 0 : 1;
					deepDryTrunks += trunk && !underWater ? 1 : 0;
				}
				if (forest[i]) {
					trunks += trunk ? 1 : 0;
					continue;
				}
				edgeTrunks += trunk ? 1 : 0;
				if (!far(forest, size, dx, dz)) {
					continue;
				}
				farColumns++;
				for (int y = ground[i] - 4; y <= ground[i] + 40; y++) {
					BlockState s = level.getBlockState(p.set(x, y, z));
					if (mod(s)) {
						farMod++;
						farBlocks.merge(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(), 1, Integer::sum);
					}
				}
			}
		}
		Holder<Biome> holder = level.getBiome(at);
		BlockPos surface = at.atY(ground[(at.getX() - origin[0]) * size + (at.getZ() - origin[1])]);
		List<String> creatureTypes = new ArrayList<>();
		level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), MobCategory.CREATURE, surface).unwrap()
				.forEach(w -> creatureTypes.add(BuiltInRegistries.ENTITY_TYPE.getKey(w.value().type()).getPath()));
		int creatures = creatureTypes.size();
		String line = String.format(Locale.ROOT, "%s at (%d, %d): forest columns %d of %d, trunk bases on them %d (%.1f per "
				+ "1000 columns), on foreign columns %d; foreign columns at least %d blocks from a forest %d with %d blocks of the "
				+ "mod %s; creature spawns of %s: %s", biome.id(), at.getX(), at.getZ(), forestColumns, size * size, trunks,
				forestColumns == 0 ? 0 : 1000.0 * trunks / forestColumns, edgeTrunks, MARGIN, farColumns, farMod, farBlocks,
				holder.getRegisteredName(), creatureTypes);
		report.append(line).append('\n');
		PolishForests.LOG.info("[vanilla_forests] " + line);
		String deepLine = String.format(Locale.ROOT, "%s deep (>= %d blocks from another biome): %d columns, planner trunks %d "
				+ "(%.1f per chunk); dry deep columns %d, planner trunks on them %d (%.1f per chunk, N1 envelope %.1f-%.1f); vanilla "
				+ "log trunks %d", biome.id(), deepReach, deepColumns, deepTrunks, deepColumns == 0 ? 0 : 256.0 * deepTrunks / deepColumns,
				deepDry, deepDryTrunks, deepDry == 0 ? 0 : 256.0 * deepDryTrunks / deepDry, DEEP_N1_LOW, DEEP_N1_HIGH, deepVanilla);
		report.append(deepLine).append('\n');
		PolishForests.LOG.info("[vanilla_forests] KF-1 " + deepLine);
		if (deepVanilla > 0) {
			failures.add(biome.id() + ": " + deepVanilla + " vanilla log trunks deep in the forest (KF-1)");
		}
		// Step F1 (KF-1 in W1): the replaced vanilla forests of a step F (dark_forest for the beech forest) come in patches
		// among other forests, often with no deep column at all; all columns of the biome then carry the check of the
		// vanilla logs (a vanilla tree stands on a column of its own biome), the planner trunks per chunk are reported.
		String ownLine = String.format(Locale.ROOT, "%s own columns: %d (blocked %d, near a structure piece %d, another "
				+ "habitat in the chunk habitats %d, mean ground y %.0f, under water %d), planner trunks %d (%.1f per chunk, %.1f per chunk "
				+ "of the dry columns), vanilla log trunks %d", biome.id(), ownColumns, ownBlocked, ownBuilding, ownOtherHabitat,
				ownColumns == 0 ? 0 : (double) ownGroundY / ownColumns, ownWater, ownTrunks, ownColumns == 0 ? 0 : 256.0 * ownTrunks / ownColumns,
				ownColumns == ownWater ? 0 : 256.0 * ownDryTrunks / (ownColumns - ownWater), ownVanilla);
		report.append(ownLine).append('\n');
		PolishForests.LOG.info("[vanilla_forests] KF-1 " + ownLine);
		if (ownVanilla > 0 && !java.util.Arrays.asList(pl.polishforests.worldgen.feature.plan.ForestBlend.TRANSECT).contains(biome)) {
			failures.add(biome.id() + ": " + ownVanilla + " vanilla log trunks on the columns of the forest (KF-1) " + ownVanillaAt);
		}
		if (!ownVanillaAt.isEmpty()) {
			String trunksAt = String.format(Locale.ROOT, "%s vanilla log trunks on own columns: %d more than %d blocks from another biome, %d "
					+ "within (the neighbor forest's M2 substitutes): %s", biome.id(), ownVanilla, EDGE_MARGIN, ownVanillaNear, ownVanillaAt);
			report.append(trunksAt).append('\n');
			PolishForests.LOG.info("[vanilla_forests] KF-1 " + trunksAt);
		}
		// Review of F1, round 1: N1 on the dry deep columns (no tree stands under water; the census area of a step-F forest
		// is chosen among dry columns, deepCenter), required on at least DEEP_MIN_COLUMNS of them for a forest of the steps F.
		double deepPerChunk = deepDry == 0 ? 0 : 256.0 * deepDryTrunks / deepDry;
		boolean stepF = !java.util.Arrays.asList(pl.polishforests.worldgen.feature.plan.ForestBlend.TRANSECT).contains(biome);
		if (deepDry >= DEEP_MIN_COLUMNS && (deepPerChunk < DEEP_N1_LOW || deepPerChunk > DEEP_N1_HIGH)) {
			failures.add(String.format(Locale.ROOT, "%s: %.1f planner trunks per chunk on the dry deep columns, outside the N1 "
					+ "envelope (KF-1)", biome.id(), deepPerChunk));
		} else if (stepF && deepDry < DEEP_MIN_COLUMNS) {
			// The replaced vanilla forests of W1 come in small patches (dark_forest for the beech forest: no column of the census
			// area 48 m·k from another biome): N1 then on the dry columns of the biome, at least DEEP_MIN_COLUMNS of them.
			int ownDry = ownColumns - ownWater;
			double ownPerChunk = ownDry == 0 ? 0 : 256.0 * ownDryTrunks / ownDry;
			String n1 = String.format(Locale.ROOT, "%s N1 on the dry columns of the biome (dry deep columns %d < %d): %d columns, "
					+ "%.1f planner trunks per chunk (N1 envelope %.1f-%.1f)", biome.id(), deepDry, DEEP_MIN_COLUMNS, ownDry, ownPerChunk,
					DEEP_N1_LOW, DEEP_N1_HIGH);
			report.append(n1).append('\n');
			PolishForests.LOG.info("[vanilla_forests] KF-1 " + n1);
			if (ownDry < DEEP_MIN_COLUMNS && biome == HabitatBiome.ALDER_CARR) {
				// Step F2 (deviation): the swamp of W1 is mostly shallow water, whose columns stay blocked for the trees; a
				// census area with too few dry columns is reported only (the vanilla-log check above still holds).
				report.append(biome.id()).append(": only ").append(ownDry).append(" dry columns, N1 reported only").append('\n');
			} else if (ownDry < DEEP_MIN_COLUMNS) {
				failures.add(String.format(Locale.ROOT, "%s: only %d dry columns of the biome in the census area, N1 not shown (KF-1)",
						biome.id(), ownDry));
			} else if (ownPerChunk < DEEP_N1_LOW || ownPerChunk > DEEP_N1_HIGH) {
				failures.add(String.format(Locale.ROOT, "%s: %.1f planner trunks per chunk on the dry columns of the biome, outside "
						+ "the N1 envelope (KF-1)", biome.id(), ownPerChunk));
			}
		}
		if (forestColumns > 2000 && trunks == 0) {
			failures.add(biome.id() + ": no trunk of the mod on " + forestColumns + " forest columns");
		}
		if (farMod > 0) {
			failures.add(biome.id() + ": " + farMod + " blocks of the mod far from the forests " + farBlocks);
		}
		if (creatures == 0) {
			failures.add(biome.id() + ": no creature spawns");
		}
	}

	/** Half side of the square searched for the deepest part of a forest of the steps F (blocks) and its lattice step. */
	private static final int DEEP_SEARCH = 1536;
	private static final int DEEP_STEP = 16;

	/**
	 * The center of the census area of a forest of the steps F (step F1, KF-1): of the squares of the area's size centered
	 * on a lattice of {@value #DEEP_STEP} blocks within {@value #DEEP_SEARCH} blocks of the located column, the one with
	 * the most dry surface columns of the biome (first in raster order on a tie; the surface biomes of
	 * {@link VanillaForestWorld} and the noise heights of the generator, no chunk generated). Review of F1, round 1: a
	 * lattice column counts only when its ground is not under water (the ocean floor height equals the world surface
	 * height), because the deepest part of a dark_forest patch can be the floor of a lake whose biome at the bottom is still
	 * the forest's.
	 */
	private static BlockPos deepCenter(ServerLevel level, HabitatBiome biome, BlockPos found) {
		ResourceKey<Biome> own = ModBiomeKeys.key(biome);
		VanillaForestWorld w = world(level);
		var gen = level.getChunkSource().getGenerator();
		var randomState = level.getChunkSource().randomState();
		int half = CENSUS_CHUNKS * 8 / DEEP_STEP;
		int n = 2 * (DEEP_SEARCH / DEEP_STEP + half) + 1;
		int x0 = found.getX() - (DEEP_SEARCH / DEEP_STEP + half) * DEEP_STEP;
		int z0 = found.getZ() - (DEEP_SEARCH / DEEP_STEP + half) * DEEP_STEP;
		int[] sum = new int[(n + 1) * (n + 1)];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				int x = x0 + i * DEEP_STEP;
				int z = z0 + j * DEEP_STEP;
				int mine = w.surfaceBiome(x, z).is(own) && gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState)
						== gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState) ? 1 : 0;
				sum[(i + 1) * (n + 1) + j + 1] = mine + sum[i * (n + 1) + j + 1] + sum[(i + 1) * (n + 1) + j] - sum[i * (n + 1) + j];
			}
		}
		int best = -1;
		BlockPos center = found;
		for (int i = half; i < n - half; i++) {
			for (int j = half; j < n - half; j++) {
				int a = i - half;
				int b = j - half;
				int c = i + half;
				int d = j + half;
				int count = sum[c * (n + 1) + d] - sum[a * (n + 1) + d] - sum[c * (n + 1) + b] + sum[a * (n + 1) + b];
				if (count > best) {
					best = count;
					center = new BlockPos(x0 + i * DEEP_STEP, found.getY(), z0 + j * DEEP_STEP);
				}
			}
		}
		PolishForests.LOG.info("[vanilla_forests] KF-1 {}: census area around ({}, {}), {} of {} lattice columns of the biome, dry "
				+ "(located at ({}, {}))", biome.id(), center.getX(), center.getZ(), best, 4 * half * half, found.getX(), found.getZ());
		return center;
	}

	/** Least distance of a deep column from a column of another biome (KF-1 of step F1), m·k. */
	private static final int DEEP = 48;
	/** The N1 envelope of the slice forests' census in {@code habitats} (living trees per chunk with the tolerances). */
	private static final double DEEP_N1_LOW = 6.4;
	private static final double DEEP_N1_HIGH = 19.2;
	/** Least deep columns for the N1 check (the vanilla-log check holds on any number). */
	private static final int DEEP_MIN_COLUMNS = 1024;
	/** Distance from another biome within which a vanilla log trunk on an own column is reported, not a failure (blocks). */
	private static final int EDGE_MARGIN = 8;

	/** Whether the square of {@code reach} blocks around column (dx, dz) lies inside the area and holds only own columns. */
	private static boolean deep(int[] other, int size, int dx, int dz, int reach) {
		int x0 = dx - reach;
		int z0 = dz - reach;
		int x1 = dx + reach + 1;
		int z1 = dz + reach + 1;
		if (x0 < 0 || z0 < 0 || x1 > size || z1 > size) {
			return false;
		}
		int w = size + 1;
		return other[x1 * w + z1] - other[x0 * w + z1] - other[x1 * w + z0] + other[x0 * w + z0] == 0;
	}

	/** Whether no forest column lies within {@link #MARGIN} blocks of column (dx, dz) of the area (inside the area). */
	private static boolean far(boolean[] forest, int size, int dx, int dz) {
		if (dx < MARGIN || dz < MARGIN || dx >= size - MARGIN || dz >= size - MARGIN) {
			return false;
		}
		for (int x = dx - MARGIN; x <= dx + MARGIN; x++) {
			for (int z = dz - MARGIN; z <= dz + MARGIN; z++) {
				if (forest[x * size + z]) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Column hashes of {@value #ORDER_CHUNKS} × {@value #ORDER_CHUNKS} chunks around a block, generated in raster or in
	 * reverse order: {terrain at the end of TERRAIN, the decorated surface at FULL}.
	 */
	private static long[][] order(ServerLevel level, BlockPos center, boolean reverse) {
		int n = ORDER_CHUNKS;
		int cx0 = (center.getX() >> 4) - n / 2;
		int cz0 = (center.getZ() >> 4) - n / 2;
		List<int[]> ring = new ArrayList<>();
		for (int cx = cx0 - 1; cx <= cx0 + n; cx++) {
			for (int cz = cz0 - 1; cz <= cz0 + n; cz++) {
				ring.add(new int[] {cx, cz});
			}
		}
		if (reverse) {
			java.util.Collections.reverse(ring);
		}
		long[] terrain = new long[n * n * 256];
		for (int[] c : ring) {
			level.getChunkSource().getChunk(c[0], c[1], ChunkStatus.TERRAIN, true);
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int cx = 0; cx < n; cx++) {
			for (int cz = 0; cz < n; cz++) {
				ChunkAccess chunk = level.getChunkSource().getChunk(cx0 + cx, cz0 + cz, ChunkStatus.TERRAIN, true);
				for (int i = 0; i < 256; i++) {
					int x = ((cx0 + cx) << 4) + (i >> 4);
					int z = ((cz0 + cz) << 4) + (i & 15);
					int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15);
					long h = top;
					for (int y = top - 24; y <= top + 4; y++) {
						h = h * 31 + Block.getId(chunk.getBlockState(p.set(x, y, z)));
					}
					terrain[(cx * n + cz) * 256 + i] = h;
				}
			}
		}
		for (int[] c : ring) {
			level.getChunkSource().getChunk(c[0], c[1], ChunkStatus.FEATURES, true);
		}
		long[] decorated = new long[n * n * 256];
		for (int cx = 0; cx < n; cx++) {
			for (int cz = 0; cz < n; cz++) {
				level.getChunk(cx0 + cx, cz0 + cz);
				for (int i = 0; i < 256; i++) {
					int x = ((cx0 + cx) << 4) + (i >> 4);
					int z = ((cz0 + cz) << 4) + (i & 15);
					int top = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
							level.getChunkSource().randomState());
					long h = 0;
					for (int y = top - 8; y <= top + 40; y++) {
						// Rule ZW-5 (step V13): the seasonal properties at their neutral value, as in the determinism test.
						h = h * 31 + Block.getId(SeasonalProperties.neutral(level.getBlockState(p.set(x, y, z))));
					}
					decorated[(cx * n + cz) * 256 + i] = h;
				}
			}
		}
		return new long[][] {terrain, decorated};
	}

	// ------------------------------------------------------------------ load (-Psites=load)

	/** The load of new terrain in this world type and in vanilla Default, same seed, three teleports each. */
	private static void load(ClientGameTestContext context, Path out) {
		StringBuilder report = new StringBuilder();
		report.append("optimization mods: ").append(Boolean.getBoolean("polishforests.optimization") ? "yes" : "see the run")
				.append('\n');
		for (ResourceKey<WorldPreset> preset : List.of(WorldPresets.NORMAL, PRESET)) {
			boolean ours = preset == PRESET;
			try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset, ours)).create()) {
				prepare(sp);
				// The server learns the client's view distance from its options; the client joined with the old one.
				context.runOnClient(mc -> {
					mc.options.renderDistance().set(RENDER_DISTANCE);
					mc.options.broadcastOptions();
				});
				// The same place in both worlds: the nearest vanilla forest of the seed (the same climate in both).
				ResourceKey<Biome> forest = ours ? ModBiomeKeys.key(HabitatBiome.OAK_HORNBEAM_FOREST)
						: ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace("forest"));
				BlockPos at = sp.getServer().computeOnServer(s -> {
					Pair<BlockPos, Holder<Biome>> hit = s.overworld().findClosestBiome3d(b -> b.is(forest), BlockPos.ZERO.atY(64),
							6400, 32, 64);
					return hit == null ? BlockPos.ZERO : hit.getFirst();
				});
				int[][] targets = {{at.getX() + 1000, at.getZ()}, {at.getX(), at.getZ() - 1000}, {at.getX(), at.getZ() + 1000}};
				int all = (2 * RENDER_DISTANCE + 1) * (2 * RENDER_DISTANCE + 1);
				int target = (int) (0.9 * all);
				int loadedAll = 0;
				double secondsAll = 0;
				long attached0 = VanillaForestWorld.ATTACHED.sum();
				long trees0 = TreeStandFeature.TREES.sum();
				StringBuilder loads = new StringBuilder();
				for (int[] t : targets) {
					int tx = t[0] >> 4;
					int tz = t[1] >> 4;
					long t0 = System.nanoTime();
					sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d 200 %d 0 -90", t[0], t[1]));
					int loaded = 0;
					for (int i = 0; i < 6000 && loaded < target; i++) {
						context.waitTick();
						loaded = context.computeOnClient(mc -> {
							int c = 0;
							for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
								for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
									c += mc.level.getChunkSource().getChunk(tx + dx, tz + dz, ChunkStatus.FULL, false) != null ? 1 : 0;
								}
							}
							return c;
						});
					}
					double seconds = (System.nanoTime() - t0) / 1e9;
					loadedAll += loaded;
					secondsAll += seconds;
					loads.append(String.format(Locale.ROOT, " %.1f", loaded / seconds));
				}
				String line = String.format(Locale.ROOT, "%s around (%d, %d): %d of %d chunks after %.2f s, %.1f chunks/s (per load%s); habitats attached %d, "
						+ "trees of the tree stand %d", preset.identifier(), at.getX(), at.getZ(), loadedAll, 3 * all, secondsAll,
						loadedAll / secondsAll, loads, VanillaForestWorld.ATTACHED.sum() - attached0, TreeStandFeature.TREES.sum() - trees0);
				report.append(line).append('\n');
				PolishForests.LOG.info("[vanilla_forests] load " + line);
			}
		}
		write(out.resolve("load.txt"), report, List.of());
	}

	private static void write(Path file, StringBuilder report, List<String> failures) {
		StringBuilder text = new StringBuilder(report);
		text.append(failures.isEmpty() ? "PASS\n" : "FAIL\n");
		failures.forEach(f -> text.append("  ").append(f).append('\n'));
		PolishForests.LOG.info("[vanilla_forests] report\n" + text);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, text);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}
