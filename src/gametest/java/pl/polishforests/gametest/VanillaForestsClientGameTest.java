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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
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
import pl.polishforests.worldgen.chunk.ModBiomeKeys;
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
	/** Share of the decorated columns that may differ between the two chunk orders. */
	static final double DECORATION_TOLERANCE = 0.02;
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
					"polishforests find biome fresh_bog_forest", "polishforests find zone sea", "polishforests here")) {
				String line = sp.getServer().computeOnServer(s -> command(s, BlockPos.ZERO, command));
				report.append(line).append('\n');
				if (line.endsWith("no message")) {
					failures.add(line);
				}
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
			// 3. The areas of the two slice forests.
			for (HabitatBiome b : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.FRESH_PINE_FOREST)) {
				BlockPos at = located.get(b);
				if (at == null) {
					continue;
				}
				sp.getServer().runOnServer(s -> area(s.overworld(), b, at, report, failures));
			}
			report.append(String.format(Locale.ROOT, "counters: habitats attached %d, soil tops %d, soil blended %d, trees %d, "
					+ "planned %d, skipped on blocked columns %d%n", VanillaForestWorld.ATTACHED.sum(),
					VanillaForestWorld.SOIL_TOPS.sum(), VanillaForestWorld.SOIL_BLENDED.sum(), TreeStandFeature.TREES.sum(),
					TreeStandFeature.PLANNED.sum(), TreeStandFeature.SKIPPED_BLOCKED.sum()));
		}
		// 4. Determinism, the reverse order in a second world of the same seed.
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, PRESET, true)).create()) {
			prepare(sp);
			BlockPos center = oakHornbeam != null ? oakHornbeam : new BlockPos(3072, 64, 3072);
			long[][] second = sp.getServer().computeOnServer(s -> order(s.overworld(), center, true));
			int terrain = 0;
			int decoration = 0;
			for (int i = 0; i < first[0].length; i++) {
				terrain += first[0][i] != second[0][i] ? 1 : 0;
				decoration += first[1][i] != second[1][i] ? 1 : 0;
			}
			String line = String.format(Locale.ROOT, "determinism (%d columns, raster against reverse order): terrain differs in "
					+ "%d, decorated surface in %d (%.2f%%)", first[0].length, terrain, decoration, 100.0 * decoration / first[0].length);
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
		return String.format(Locale.ROOT, "locate biome %s: (%d, %d, %d), %.0f blocks away, getBiome there %s (%.0f ms)",
				key.identifier(), p.getX(), p.getY(), p.getZ(), Math.sqrt(p.distSqr(BlockPos.ZERO.atY(p.getY()))),
				level.getBiome(p).getRegisteredName(), ms);
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
				context.runOnClient(mc -> mc.options.renderDistance().set(RENDER_DISTANCE));
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
				String line = String.format(Locale.ROOT, "%s around (%d, %d): %d of %d chunks after %.2f s, %.1f chunks/s (per load%s)",
						preset.identifier(), at.getX(), at.getZ(), loadedAll, 3 * all, secondsAll, loadedAll / secondsAll, loads);
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
