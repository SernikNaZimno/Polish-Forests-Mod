package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.BigDripleafStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.feature.BiomeDecoration;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.surface.ChunkSurface;

/**
 * Vegetation in the game (step S7, docs/03-m2-biomy.md §8, §4.6, §12.3), in both world scales in the natural-vegetation
 * mode: {@code generator.validate()} (feature order) passes; transects of three rivers per scale (a large lowland river,
 * a small lowland river, a mountain stream; {@value #TRANSECT_RADIUS_CHUNKS} chunks around each crossing) meet the
 * density rule of §4.6 in the waterside zones, the floodplain forests and the alder carr: short grass and small flowers at
 * most 10% of the land columns, tall plants, shrubs, reed, cattail and trees at least 60%, bare ground at most 25% (point
 * bars and gravel bars left out); {@code HABITAT_MISS} below 0.5% of the chunks with terrain. Checkpoint 2: a screenshot
 * {@code vegetation_<place>_<scale>} at each of the 11 places of §12.3 (the soil places of {@code HabitatsClientGameTest}),
 * without the HUD, at noon in clear weather. Runs when {@code -Dpolishforests.gametest} is {@code vegetation} or
 * {@code all}; {@code -Pscales} picks one scale, {@code -Psites} some places (names of
 * {@link HabitatsClientGameTest#SOIL_NAMES}, {@code none} for no screenshots).
 */
public final class VegetationClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int RENDER_DISTANCE = 8;
	/** Radius of a transect area in chunks around the chunk of the river crossing. */
	private static final int TRANSECT_RADIUS_CHUNKS = 2;
	/** Least number of land columns of the dense habitats in a transect. */
	private static final int TRANSECT_MIN_COLUMNS = 150;
	private static final double SMALL_MAX = 0.10;
	private static final double TALL_MIN = 0.60;
	private static final double BARE_MAX = 0.25;

	/** Waterside zones of §4.6 (also inside other biomes). */
	private static final Set<Zone> DENSE_ZONES = EnumSet.of(Zone.WILLOW_SCRUB, Zone.HERB_FRINGE, Zone.TALL_HERBS,
			Zone.MONTANE_TALL_HERBS, Zone.SHORE_REEDBED, Zone.REEDBED, Zone.WILLOW_CARR, Zone.GRAVEL_BAR);
	/** Floodplain forests, alder carrs, willow scrub and reedbeds of §4.6. */
	private static final Set<HabitatBiome> DENSE_BIOMES = EnumSet.of(HabitatBiome.ALDER_CARR, HabitatBiome.ASH_ALDER_FOREST,
			HabitatBiome.WILLOW_POPLAR_FOREST, HabitatBiome.ELM_ASH_FOREST, HabitatBiome.GRAY_ALDER_FOREST,
			HabitatBiome.WILLOW_SCRUB, HabitatBiome.REEDBED);
	/** Zones whose bare ground is not counted (§4.6: "poza łachą i kamieńcem"). */
	private static final Set<Zone> BARS = EnumSet.of(Zone.POINT_BAR, Zone.GRAVEL_BAR);

	/** Kind of cover of a land column, from the block above its top ground block. */
	enum Cover {
		TREE, SHRUB, TALL, SMALL, OTHER, BARE
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polishforests.gametest", "all");
		if (!mode.equals("vegetation") && !mode.equals("all")) {
			return;
		}
		String scales = System.getProperty("polishforests.scales", "");
		context.runOnClient(mc -> mc.options.renderDistance().set(RENDER_DISTANCE));
		for (PolandScale scale : PolandScale.values()) {
			if (!scales.isBlank() && !scales.contains(scale.getSerializedName())) {
				continue;
			}
			ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET,
					PolishForests.id(scale == PolandScale.REALISTIC ? "poland" : "poland_gameplay"));
			try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset)).create()) {
				sp.getServer().runCommand("gamerule advance_time false");
				sp.getServer().runCommand("gamerule advance_weather false");
				sp.getServer().runCommand("gamerule spawn_mobs false");
				sp.getServer().runCommand("time set 6000");
				sp.getServer().runCommand("weather clear");
				sp.getServer().runCommand("gamemode spectator @a");
				String name = scale.getSerializedName();
				boolean real = scale == PolandScale.REALISTIC;
				long miss0 = ModFeatures.HABITAT_MISS.sum();
				long chunks0 = PolandChunkGenerator.CHUNKS.sum();
				PolishForests.LOG.info("[vegetation] {}: {}", name, sp.getServer().computeOnServer(VegetationClientGameTest::validate));
				PolishForests.LOG.info("[vegetation] {}: {}", name, sp.getServer().computeOnServer(s -> transects(s, real)));
				screenshots(context, sp, real, name);
				PolishForests.LOG.info("[vegetation] {}: {}", name, layers());
				long misses = ModFeatures.HABITAT_MISS.sum() - miss0;
				long chunks = PolandChunkGenerator.CHUNKS.sum() - chunks0;
				String report = String.format(Locale.ROOT, "HABITAT_MISS %d of %d chunks with terrain (%.3f%%)", misses, chunks,
						100.0 * misses / Math.max(1, chunks));
				PolishForests.LOG.info("[vegetation] {}: {}", name, report);
				if (chunks == 0 || misses >= 0.005 * chunks) {
					throw new AssertionError(report);
				}
			}
		}
	}

	private static void select(WorldCreationUiState ui, ResourceKey<WorldPreset> preset) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(preset)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier())));
	}

	private static PolandChunkGenerator generator(MinecraftServer server) {
		if (!(server.overworld().getChunkSource().getGenerator() instanceof PolandChunkGenerator gen)) {
			throw new AssertionError("The overworld does not use the Poland generator");
		}
		return gen;
	}

	/** The feature order of the generator is valid (no "Feature order cycle") and the world is in the natural mode. */
	private static String validate(MinecraftServer server) {
		PolandChunkGenerator gen = generator(server);
		gen.validate();
		if (gen.settings().agriculture()) {
			throw new AssertionError("The test world is not in the natural-vegetation mode: " + gen.settings());
		}
		return "generator.validate() passed, natural-vegetation mode";
	}

	/** Time and placements of each dispatcher layer over the process. */
	private static String layers() {
		StringBuilder sb = new StringBuilder("dispatcher layers (process):");
		for (BiomeDecoration.Dispatcher d : BiomeDecoration.Dispatcher.values()) {
			ModFeatures.LayerStats s = ModFeatures.STATS.get(d);
			long chunks = Math.max(1, s.chunks.sum());
			sb.append(String.format(Locale.ROOT, " %s %.3f ms/chunk, %.1f placed/chunk (%d chunks);", d.path(),
					s.nanos.sum() / 1e6 / chunks, (double) s.placed.sum() / chunks, s.chunks.sum()));
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------ transects (§4.6)

	/**
	 * Crossings of the three rivers of a scale (block coordinates): the large river of the stage measurement (class A),
	 * the place of the ash-alder riparian forest of the soil test by a small lowland river (class B) and a mountain stream
	 * with a gray alder forest found near the Beskids area of the stage measurement (class C).
	 */
	private static List<int[]> crossings(MinecraftServer server, boolean real) {
		int[][] soil = real ? HabitatsClientGameTest.SOIL_REAL : HabitatsClientGameTest.SOIL_GAMEPLAY;
		List<int[]> out = new ArrayList<>();
		out.add(real ? new int[] {-19_484, 11_253} : new int[] {-1_851, 6_022});
		out.add(soil[1]);
		out.add(stream(server, real ? new int[] {154_834, 1_058_738} : new int[] {27_609, 3_254}));
		return out;
	}

	/** The nearest column with a gray alder forest or a gravel bar on a spiral (step 8 blocks) around the start. */
	private static int[] stream(MinecraftServer server, int[] start) {
		PolandChunkGenerator gen = generator(server);
		long seed = server.overworld().getSeed();
		LandscapeModel m = gen.model(seed);
		HabitatClassifier k = gen.classifier(seed);
		int x = 0;
		int z = 0;
		int dx = 1;
		int dz = 0;
		int leg = 1;
		int steps = 0;
		int turns = 0;
		for (int i = 0; i < 400_000; i++) {
			int bx = start[0] + x * 8;
			int bz = start[1] + z * 8;
			int code = k.classify(m.sample(bx, bz), bx, bz);
			if (Habitat.biome(code) == HabitatBiome.GRAY_ALDER_FOREST || Habitat.zone(code) == Zone.GRAVEL_BAR) {
				return new int[] {bx, bz};
			}
			x += dx;
			z += dz;
			if (++steps == leg) {
				steps = 0;
				int t = dx;
				dx = -dz;
				dz = t;
				if (++turns % 2 == 0) {
					leg++;
				}
			}
		}
		throw new AssertionError("No mountain stream with a gray alder forest near " + start[0] + ", " + start[1]);
	}

	/**
	 * The three transects of the scale: in the full chunks around each crossing, the land columns of the dense zones and
	 * biomes are sorted by what stands on their top ground block ({@link #cover}); each transect must meet §4.6.
	 */
	private static String transects(MinecraftServer server, boolean real) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = generator(server);
		long seed = level.getSeed();
		String[] names = {"large_river", "small_river", "mountain_stream"};
		List<int[]> crossings = crossings(server, real);
		StringBuilder report = new StringBuilder("transects (§4.6, land columns of the waterside zones, floodplain forests "
				+ "and alder carr):");
		List<String> failures = new ArrayList<>();
		for (int t = 0; t < crossings.size(); t++) {
			int[] c = crossings.get(t);
			int ccx = c[0] >> 4;
			int ccz = c[1] >> 4;
			long[] counts = new long[Cover.values().length];
			long land = 0;
			long bareCounted = 0;
			long bare = 0;
			long cattails = 0;
			long shallow = 0;
			java.util.Map<String, Integer> byZone = new java.util.TreeMap<>();
			for (int cx = ccx - TRANSECT_RADIUS_CHUNKS; cx <= ccx + TRANSECT_RADIUS_CHUNKS; cx++) {
				for (int cz = ccz - TRANSECT_RADIUS_CHUNKS; cz <= ccz + TRANSECT_RADIUS_CHUNKS; cz++) {
					level.getChunk(cx, cz);
					int[] codes = new int[256];
					ChunkSurface plan = gen.surface(new ChunkPos(cx, cz), level.getMinY(), level.getMaxY(), seed, codes);
					for (int i = 0; i < 256; i++) {
						int code = codes[i];
						Zone zone = Habitat.zone(code);
						HabitatBiome biome = Habitat.biome(code);
						if (!DENSE_ZONES.contains(zone) && !DENSE_BIOMES.contains(biome)) {
							continue;
						}
						int x = (cx << 4) + (i >> 4);
						int z = (cz << 4) + (i & 15);
						if (plan.wet(i)) {
							if (plan.waterTop(i) - plan.top(i) == 1) {
								shallow++;
								cattails += level.getBlockState(new BlockPos(x, plan.top(i) + 1, z)).is(Blocks.SMALL_DRIPLEAF) ? 1 : 0;
							}
							continue;
						}
						Cover cover = cover(level, x, plan.top(i), z);
						counts[cover.ordinal()]++;
						land++;
						byZone.merge(zone == Zone.NONE ? biome.id() : zone.id(), 1, Integer::sum);
						if (!BARS.contains(zone)) {
							bareCounted++;
							bare += cover == Cover.BARE ? 1 : 0;
						}
					}
				}
			}
			double small = (double) counts[Cover.SMALL.ordinal()] / Math.max(1, land);
			double tall = (double) (counts[Cover.TREE.ordinal()] + counts[Cover.SHRUB.ordinal()] + counts[Cover.TALL.ordinal()])
					/ Math.max(1, land);
			double bareShare = (double) bare / Math.max(1, bareCounted);
			report.append(String.format(Locale.ROOT, " %s at (%d, %d): %d land columns %s, trees %.1f%%, shrubs %.1f%%, tall "
					+ "plants %.1f%% (together %.1f%%), short grass and small flowers %.1f%%, other %.1f%%, bare %.1f%% (without "
					+ "bars); cattail on %d of %d columns in water 1 block deep;", names[t], c[0], c[1], land, byZone,
					100.0 * counts[Cover.TREE.ordinal()] / Math.max(1, land), 100.0 * counts[Cover.SHRUB.ordinal()] / Math.max(1, land),
					100.0 * counts[Cover.TALL.ordinal()] / Math.max(1, land), 100 * tall, 100 * small,
					100.0 * counts[Cover.OTHER.ordinal()] / Math.max(1, land), 100 * bareShare, cattails, shallow));
			if (land < TRANSECT_MIN_COLUMNS) {
				failures.add(names[t] + ": only " + land + " land columns of the dense habitats");
			} else if (small > SMALL_MAX || tall < TALL_MIN || bareShare > BARE_MAX) {
				failures.add(String.format(Locale.ROOT, "%s: small %.1f%%, tall %.1f%%, bare %.1f%%", names[t], 100 * small,
						100 * tall, 100 * bareShare));
			}
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Density rule of §4.6 not met: " + failures + "; " + report);
		}
		return report.toString();
	}

	/** What stands on a land column with the top ground block at (x, top, z). */
	static Cover cover(ServerLevel level, int x, int top, int z) {
		BlockState above = level.getBlockState(new BlockPos(x, top + 1, z));
		if (above.is(BlockTags.LOGS)) {
			return Cover.TREE;
		}
		if (above.is(BlockTags.LEAVES)) {
			return Cover.SHRUB;
		}
		Block block = above.getBlock();
		if (block instanceof DoublePlantBlock || block instanceof BigDripleafBlock || block instanceof BigDripleafStemBlock
				|| above.is(Blocks.SUGAR_CANE) || above.is(Blocks.BUSH) || above.is(Blocks.FIREFLY_BUSH)
				|| above.is(Blocks.SWEET_BERRY_BUSH)) {
			return Cover.TALL;
		}
		if (above.is(Blocks.SHORT_GRASS) || above.is(BlockTags.SMALL_FLOWERS) || block instanceof FlowerBedBlock) {
			return Cover.SMALL;
		}
		if (above.isAir()) {
			BlockState higher = level.getBlockState(new BlockPos(x, top + 2, z));
			return higher.is(BlockTags.LEAVES) || higher.is(BlockTags.LOGS) ? Cover.SHRUB : Cover.BARE;
		}
		return Cover.OTHER;
	}

	// ------------------------------------------------------------------ checkpoint 2 (§12.3)

	/**
	 * Places seen from above (the camera 10 blocks over the ground): reedbed, willow scrub, beach, bogs, dwarf pine and
	 * the upper montane spruce forest (low dense crowns); the willow scrub from 14 blocks, farther back.
	 */
	private static final Set<String> OPEN = Set.of("lake_reedbed", "willow_scrub", "beach", "raised_bog", "dwarf_pine_scrub",
			"montane_spruce_forest");

	/**
	 * A screenshot at each of the 11 places of §12.3, looking north-east at the place's center with the HUD hidden: in
	 * forests from 3 blocks above the ground about 12 blocks away, below most crowns (the floor, the trunks and the
	 * understory), in open habitats and the upper montane spruce forest from 10 blocks above the ground, the willow scrub
	 * from 14 blocks; the camera spot is the one with the clearest line of sight ({@link #camera}).
	 */
	private static void screenshots(ClientGameTestContext context, TestSingleplayerContext sp, boolean real, String scale) {
		String only = System.getProperty("polishforests.sites", "");
		if (only.equals("none")) {
			return;
		}
		int[][] places = real ? HabitatsClientGameTest.SOIL_REAL : HabitatsClientGameTest.SOIL_GAMEPLAY;
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		for (int p = 0; p < places.length; p++) {
			String name = HabitatsClientGameTest.SOIL_NAMES[p];
			if (!only.isBlank() && !List.of(only.split(",")).contains(name)) {
				continue;
			}
			int[] c = places[p];
			boolean open = OPEN.contains(name);
			int centerY = sp.getServer().computeOnServer(s -> groundY(s, c[0], c[1]));
			int above = name.equals("willow_scrub") ? 14 : open ? 10 : 3;
			int[] camera = sp.getServer().computeOnServer(s -> camera(s, c[0], c[1], centerY, above));
			double dx = c[0] - camera[0];
			double dz = c[1] - camera[2];
			// Yaw: 0 looks to +z, -90 to +x; the pitch aims at the ground of the center (the eyes are 1.62 above the feet).
			double yaw = Math.toDegrees(Math.atan2(-dx, dz));
			double pitch = Math.toDegrees(Math.atan2(camera[1] + 1.62 - (centerY + 1), Math.hypot(dx, dz))) + (open ? 4 : 0);
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", camera[0], camera[1], camera[2], yaw,
					pitch));
			context.waitTicks(20 * 25);
			String client = context.computeOnClient(mc -> mc.level.getChunkSource().gatherStats());
			PolishForests.LOG.info("[vegetation] {} {}: center ({}, {}) ground Y {}, camera ({}, {}, {}) yaw {} pitch {}; client {}",
					scale, name, c[0], c[1], centerY, camera[0], camera[1], camera[2], String.format(Locale.ROOT, "%.1f", yaw),
					String.format(Locale.ROOT, "%.1f", pitch), client);
			context.waitTicks(20 * 15);
			context.takeScreenshot("vegetation_" + name + "_" + scale);
		}
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	/**
	 * Camera feet position south-west of the center (cx, cz): {@code above} blocks over the ground (or water), with free
	 * feet and head blocks (air or a plant, no leaves or logs), 8–14 blocks back along the diagonal and up to 4 blocks to
	 * the side; of these the one whose line of sight to the center's ground has the fewest leaves, logs and solid blocks
	 * in its first 10 blocks.
	 */
	private static int[] camera(MinecraftServer server, int cx, int cz, int centerY, int above) {
		ServerLevel level = server.overworld();
		int[] best = null;
		int bestBlocked = Integer.MAX_VALUE;
		for (int back : above > 10 ? new int[] {18, 16, 20} : new int[] {12, 10, 14, 8}) {
			for (int side : new int[] {0, 2, -2, 4, -4}) {
				int x = cx - back + side;
				int z = cz - back - side;
				int y = groundY(server, x, z) + 1 + above;
				if (!free(level, x, y, z) || !free(level, x, y + 1, z)) {
					continue;
				}
				int blocked = blocked(level, x + 0.5, y + 1.62, z + 0.5, cx + 0.5, centerY + 1.0, cz + 0.5);
				if (blocked < bestBlocked) {
					bestBlocked = blocked;
					best = new int[] {x, y, z};
				}
			}
		}
		if (best != null) {
			return best;
		}
		int x = cx - 12;
		int z = cz - 12;
		int y = groundY(server, x, z) + 1 + above;
		while (!free(level, x, y, z) || !free(level, x, y + 1, z)) {
			y++;
		}
		return new int[] {x, y, z};
	}

	/** Leaves, logs and solid blocks on the first 10 blocks of the line from the eye to the target (steps of 0.5). */
	private static int blocked(ServerLevel level, double x0, double y0, double z0, double x1, double y1, double z1) {
		double length = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
		int n = 0;
		BlockPos last = null;
		for (double t = 0.5; t <= Math.min(10, length - 1); t += 0.5) {
			BlockPos pos = BlockPos.containing(x0 + (x1 - x0) * t / length, y0 + (y1 - y0) * t / length, z0 + (z1 - z0) * t / length);
			if (pos.equals(last)) {
				continue;
			}
			last = pos;
			n += free(level, pos.getX(), pos.getY(), pos.getZ()) ? 0 : 1;
		}
		return n;
	}

	private static boolean free(ServerLevel level, int x, int y, int z) {
		BlockState s = level.getBlockState(new BlockPos(x, y, z));
		return !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && !s.isSolidRender() && s.getFluidState().isEmpty();
	}

	/** Top ground block of the surface plan at (x, z). */
	private static int groundY(MinecraftServer server, int x, int z) {
		ServerLevel level = server.overworld();
		level.getChunk(x >> 4, z >> 4);
		ChunkSurface plan = generator(server).surface(new ChunkPos(x >> 4, z >> 4), level.getMinY(), level.getMaxY(),
				level.getSeed(), null);
		int i = ChunkHabitats.index(x & 15, z & 15);
		return plan.wet(i) ? plan.waterTop(i) : plan.top(i);
	}

}
