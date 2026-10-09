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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * a small lowland river, a mountain stream; {@value #TRANSECT_RADIUS_CHUNKS} chunks around each crossing, each with at
 * least {@value #TRANSECT_MIN_ZONE_COLUMNS} land columns of the waterside zones and {@value #TRANSECT_MIN_WATER_COLUMNS}
 * columns of the river's water) meet the density rule of §4.6 in the waterside zones, the floodplain forests and the
 * alder carr: short grass and small flowers at most 10% of the land columns, tall plants, shrubs, reed, cattail and
 * trees at least 60% (the one-block bush, firefly bush and sweet berry bush count as dwarf shrubs, not as tall), bare
 * ground at most 25% (point bars and gravel bars left out); the crown cover of the willow scrub and the dwarf pine scrub
 * is reported; trees leave no decaying leaves (below {@value #DECAYING_MAX} of the leaves in the transects and at the
 * places of the screenshots); {@code HABITAT_MISS} below 0.5% of the chunks with terrain. Checkpoint 2: a screenshot
 * {@code vegetation_<place>_<scale>} at each of the 11 places of §12.3 (the soil places of {@code HabitatsClientGameTest};
 * the willow scrub of the gameplay scale on the large river, where the zone is wide) and at the common forests
 * ({@link #EXTRA_NAMES}), without the HUD, at noon in clear weather. Runs when {@code -Dpolishforests.gametest} is
 * {@code vegetation} or {@code all}; {@code -Pscales} picks one scale, {@code -Psites} some places (names of
 * {@link HabitatsClientGameTest#SOIL_NAMES} and {@link #EXTRA_NAMES}, {@code none} for no screenshots).
 */
public final class VegetationClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int RENDER_DISTANCE = 8;
	/** Radius of a transect area in chunks around the chunk of the river crossing. */
	private static final int TRANSECT_RADIUS_CHUNKS = 2;
	/** Least number of land columns of the dense habitats in a transect. */
	private static final int TRANSECT_MIN_COLUMNS = 150;
	/** Least number of land columns of the waterside zones in a transect (the crossing is a real river bank). */
	private static final int TRANSECT_MIN_ZONE_COLUMNS = 40;
	/**
	 * Least number of columns of the river's water (river or stream biome) in a transect: a stream of the gameplay scale
	 * is 1–2 blocks wide, so its 80 blocks of a transect hold only 15 or so.
	 */
	private static final int TRANSECT_MIN_WATER_COLUMNS = 10;
	/** Greatest share of decaying leaves (distance 7, not persistent) among the leaves of the scanned areas. */
	private static final double DECAYING_MAX = 0.001;
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
		TREE, SHRUB, TALL, SMALL, OTHER, BARE, DWARF
	}

	/**
	 * Extra places of checkpoint 2 (S7 review): the common forests of Poland without a soil place. Centers checked in the
	 * classifier; {@code null} where the scale has no such place near the test areas.
	 */
	static final String[] EXTRA_NAMES = {"oak_hornbeam_forest", "fresh_pine_forest", "mixed_forest", "montane_beech_forest",
			"upland_fir_forest", "elm_ash_forest", "gray_alder_forest"};
	private static final int[][] EXTRA_REAL = {{-144, 32}, {-231_006, -134_146}, {16, 0}, {99_409, 1_034_685}, null,
			{-19_612, 11_205}, {155_490, 1_059_162}};
	private static final int[][] EXTRA_GAMEPLAY = {{-32, 32}, {-1_280, -496}, {0, 0}, {27_273, 3_606}, {27_305, 3_718},
			{-1_787, 6_086}, {28_072, 3_880}};
	/** Radius (blocks) of the disk around the center of an extra place in which its biome must dominate. */
	private static final int EXTRA_SHARE_RADIUS = 16;
	/**
	 * Least share of the named biome in that disk (S7 review round 2: the gameplay gray alder forest at (27825, 3374)
	 * was a single column of it inside an oak-hornbeam forest, 9% of the disk). The narrow belt of the realistic gray alder
	 * forest along its stream fills 39%.
	 */
	private static final double EXTRA_MIN_SHARE = 0.30;
	/** Start of the search for the willow scrub of the gameplay scale: the large river of the transect. */
	private static final int[] WILLOW_SCRUB_GAMEPLAY = {-1_851, 6_022};

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
				PolishForests.LOG.info("[vegetation] {}: {}", name, sp.getServer().computeOnServer(s -> crownCover(s, real)));
				List<int[]> shot = screenshots(context, sp, real, name);
				PolishForests.LOG.info("[vegetation] {}: {}", name, sp.getServer().computeOnServer(s -> decaying(s, real, shot)));
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
	 * a small lowland river (class B: a column of its tall herbs or riverside willows, the zones of class B; S7 review:
	 * the place of the ash-alder riparian forest of the soil test, used before, has no river in the gameplay scale and only
	 * a stream in the realistic one; the starts are the nearest stretches of class B to it, 4.5 km and 1.5 km away, whose
	 * 5 × 5 chunks hold about 440 columns of the class B zones) and a mountain stream
	 * with a gray alder forest found near the Beskids area of the stage measurement (class C).
	 */
	private static List<int[]> crossings(MinecraftServer server, boolean real) {
		List<int[]> out = new ArrayList<>();
		out.add(real ? new int[] {-19_484, 11_253} : new int[] {-1_851, 6_022});
		out.add(find(server, real ? new int[] {800, -4_304} : new int[] {-1_400, -296},
				code -> Habitat.zone(code) == Zone.TALL_HERBS || Habitat.zone(code) == Zone.RIVERSIDE_WILLOWS, "small lowland river"));
		out.add(find(server, real ? new int[] {154_834, 1_058_738} : new int[] {27_609, 3_254},
				code -> Habitat.biome(code) == HabitatBiome.GRAY_ALDER_FOREST || Habitat.zone(code) == Zone.GRAVEL_BAR,
				"mountain stream with a gray alder forest"));
		return out;
	}

	/** The nearest column whose habitat code passes the test, on a spiral (step 8 blocks) around the start. */
	private static int[] find(MinecraftServer server, int[] start, java.util.function.IntPredicate test, String what) {
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
			if (test.test(code)) {
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
		throw new AssertionError("No " + what + " near " + start[0] + ", " + start[1]);
	}

	/**
	 * The three transects of the scale: in the full chunks around each crossing, the land columns of the dense zones and
	 * biomes are sorted by what stands on their top ground block ({@link #cover}); each transect must meet §4.6.
	 */
	static String transects(MinecraftServer server, boolean real) {
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
			long zoneLand = 0;
			long river = 0;
			java.util.Map<String, Integer> byZone = new java.util.TreeMap<>();
			java.util.Map<String, Integer> bareTops = new java.util.TreeMap<>();
			for (int cx = ccx - TRANSECT_RADIUS_CHUNKS; cx <= ccx + TRANSECT_RADIUS_CHUNKS; cx++) {
				for (int cz = ccz - TRANSECT_RADIUS_CHUNKS; cz <= ccz + TRANSECT_RADIUS_CHUNKS; cz++) {
					level.getChunk(cx, cz);
					int[] codes = new int[256];
					ChunkSurface plan = gen.surface(new ChunkPos(cx, cz), level.getMinY(), level.getMaxY(), seed, codes);
					for (int i = 0; i < 256; i++) {
						int code = codes[i];
						Zone zone = Habitat.zone(code);
						HabitatBiome biome = Habitat.biome(code);
						if (plan.wet(i) && (biome == HabitatBiome.RIVER || biome == HabitatBiome.STREAM)) {
							river++;
						}
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
						zoneLand += DENSE_ZONES.contains(zone) ? 1 : 0;
						byZone.merge(zone == Zone.NONE ? biome.id() : zone.id(), 1, Integer::sum);
						if (!BARS.contains(zone)) {
							bareCounted++;
							bare += cover == Cover.BARE ? 1 : 0;
							if (cover == Cover.BARE) {
								bareTops.merge(level.getBlockState(new BlockPos(x, plan.top(i), z)).getBlock().getDescriptionId()
										.replace("block.minecraft.", "") + (plan.flags(i) != 0 ? "/flags" + plan.flags(i) : ""), 1, Integer::sum);
							}
						}
					}
				}
			}
			double small = (double) counts[Cover.SMALL.ordinal()] / Math.max(1, land);
			double tall = (double) (counts[Cover.TREE.ordinal()] + counts[Cover.SHRUB.ordinal()] + counts[Cover.TALL.ordinal()])
					/ Math.max(1, land);
			double bareShare = (double) bare / Math.max(1, bareCounted);
			report.append(String.format(Locale.ROOT, " %s at (%d, %d): %d land columns (%d of the waterside zones) %s, %d "
					+ "columns of river water, trees %.1f%%, shrubs %.1f%%, tall plants %.1f%% (together %.1f%%), dwarf shrubs "
					+ "%.1f%%, short grass and small flowers %.1f%%, other %.1f%%, bare %.1f%% (without bars, tops %s); cattail on %d of %d "
					+ "columns in water 1 block deep;", names[t], c[0], c[1], land, zoneLand, byZone, river,
					100.0 * counts[Cover.TREE.ordinal()] / Math.max(1, land), 100.0 * counts[Cover.SHRUB.ordinal()] / Math.max(1, land),
					100.0 * counts[Cover.TALL.ordinal()] / Math.max(1, land), 100 * tall,
					100.0 * counts[Cover.DWARF.ordinal()] / Math.max(1, land), 100 * small,
					100.0 * counts[Cover.OTHER.ordinal()] / Math.max(1, land), 100 * bareShare, bareTops, cattails, shallow));
			if (land < TRANSECT_MIN_COLUMNS) {
				failures.add(names[t] + ": only " + land + " land columns of the dense habitats");
			} else if (zoneLand < TRANSECT_MIN_ZONE_COLUMNS || river < TRANSECT_MIN_WATER_COLUMNS) {
				failures.add(names[t] + ": not a river bank (" + zoneLand + " land columns of the waterside zones, " + river
						+ " columns of river water)");
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
				|| above.is(Blocks.SUGAR_CANE)) {
			return Cover.TALL;
		}
		if (above.is(Blocks.BUSH) || above.is(Blocks.FIREFLY_BUSH) || above.is(Blocks.SWEET_BERRY_BUSH)) {
			return Cover.DWARF;
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

	/** Whether leaves or a log stand 1–4 blocks above the top ground block (the crown cover of shrubs and low trees). */
	private static boolean crown(ServerLevel level, int x, int top, int z) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = 1; dy <= 4; dy++) {
			BlockState s = level.getBlockState(at.set(x, top + dy, z));
			if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Crown cover of the shrub habitats (S7 review; §4.1 and §8.5: the willow scrub 80–100%, the dwarf pine scrub about
	 * 70%): the share of the land columns with leaves or a log 1–4 blocks above the ground, in the willow scrub zone and
	 * biome of the large river transect and of the willow scrub place, and in the dwarf pine scrub biome around its soil
	 * place ({@value #TRANSECT_RADIUS_CHUNKS} chunks around each). Reported, not asserted.
	 */
	private static String crownCover(MinecraftServer server, boolean real) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = generator(server);
		long seed = level.getSeed();
		int[][] soil = real ? HabitatsClientGameTest.SOIL_REAL : HabitatsClientGameTest.SOIL_GAMEPLAY;
		int[] large = real ? new int[] {-19_484, 11_253} : new int[] {-1_851, 6_022};
		int[] scrub = willowScrubPlace(server, real);
		StringBuilder report = new StringBuilder("crown cover (leaves or a log 1-4 blocks above the ground):");
		Object[][] areas = {{"willow scrub at the large river", large, true}, {"willow scrub at its place", scrub, true},
				{"dwarf pine scrub at its place", soil[8], false}};
		for (Object[] a : areas) {
			int[] c = (int[]) a[1];
			boolean willow = (Boolean) a[2];
			long columns = 0;
			long covered = 0;
			for (int cx = (c[0] >> 4) - TRANSECT_RADIUS_CHUNKS; cx <= (c[0] >> 4) + TRANSECT_RADIUS_CHUNKS; cx++) {
				for (int cz = (c[1] >> 4) - TRANSECT_RADIUS_CHUNKS; cz <= (c[1] >> 4) + TRANSECT_RADIUS_CHUNKS; cz++) {
					level.getChunk(cx, cz);
					int[] codes = new int[256];
					ChunkSurface plan = gen.surface(new ChunkPos(cx, cz), level.getMinY(), level.getMaxY(), seed, codes);
					for (int i = 0; i < 256; i++) {
						boolean in = willow ? Habitat.zone(codes[i]) == Zone.WILLOW_SCRUB
								|| Habitat.biome(codes[i]) == HabitatBiome.WILLOW_SCRUB && Habitat.zone(codes[i]) == Zone.NONE
								: Habitat.biome(codes[i]) == HabitatBiome.DWARF_PINE_SCRUB;
						if (!in || plan.wet(i)) {
							continue;
						}
						columns++;
						covered += crown(level, (cx << 4) + (i >> 4), plan.top(i), (cz << 4) + (i & 15)) ? 1 : 0;
					}
				}
			}
			report.append(String.format(Locale.ROOT, " %s (%d, %d): %.1f%% of %d land columns;", a[0], c[0], c[1],
					100.0 * covered / Math.max(1, columns), columns));
		}
		return report.toString();
	}

	/**
	 * Decaying leaves (distance 7, not persistent: they drop off with the first random ticks) in the transects and at the
	 * places of the screenshots, {@value #TRANSECT_RADIUS_CHUNKS} chunks around each (full chunks, so every neighbor's
	 * trees stand), up to 48 blocks above the ground: below {@value #DECAYING_MAX} of the leaves (S7 review: 1% before the
	 * repair of the leaf distances in {@code FastTreeFeature}).
	 */
	private static String decaying(MinecraftServer server, boolean real, List<int[]> places) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = generator(server);
		long seed = level.getSeed();
		List<int[]> areas = new ArrayList<>(crossings(server, real));
		areas.addAll(places);
		java.util.Set<Long> done = new java.util.HashSet<>();
		long leaves = 0;
		long decaying = 0;
		StringBuilder report = new StringBuilder();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int[] c : areas) {
			long l0 = leaves;
			long d0 = decaying;
			for (int cx = (c[0] >> 4) - TRANSECT_RADIUS_CHUNKS; cx <= (c[0] >> 4) + TRANSECT_RADIUS_CHUNKS; cx++) {
				for (int cz = (c[1] >> 4) - TRANSECT_RADIUS_CHUNKS; cz <= (c[1] >> 4) + TRANSECT_RADIUS_CHUNKS; cz++) {
					if (!done.add(ChunkPos.pack(cx, cz))) {
						continue;
					}
					level.getChunk(cx, cz);
					ChunkSurface plan = gen.surface(new ChunkPos(cx, cz), level.getMinY(), level.getMaxY(), seed, null);
					for (int i = 0; i < 256; i++) {
						int x = (cx << 4) + (i >> 4);
						int z = (cz << 4) + (i & 15);
						int from = plan.wet(i) ? plan.waterTop(i) : plan.top(i);
						for (int y = from; y <= from + 48; y++) {
							BlockState s = level.getBlockState(at.set(x, y, z));
							if (s.is(BlockTags.LEAVES)) {
								leaves++;
								if (s.hasProperty(BlockStateProperties.DISTANCE) && s.getValue(BlockStateProperties.DISTANCE) == 7
										&& !s.getValue(BlockStateProperties.PERSISTENT)) {
									decaying++;
								}
							}
						}
					}
				}
			}
			report.append(String.format(Locale.ROOT, " (%d, %d) %d of %d;", c[0], c[1], decaying - d0, leaves - l0));
		}
		String out = String.format(Locale.ROOT, "decaying leaves %d of %d (%.3f%%):%s", decaying, leaves,
				100.0 * decaying / Math.max(1, leaves), report);
		if (leaves == 0 || decaying > DECAYING_MAX * leaves) {
			throw new AssertionError(out);
		}
		return out;
	}

	/** The willow scrub place of the scale: the soil place (realistic), the large river's scrub (gameplay). */
	private static int[] willowScrubPlace(MinecraftServer server, boolean real) {
		if (real) {
			return HabitatsClientGameTest.SOIL_REAL[4];
		}
		return find(server, WILLOW_SCRUB_GAMEPLAY, code -> Habitat.zone(code) == Zone.WILLOW_SCRUB
				|| Habitat.biome(code) == HabitatBiome.WILLOW_SCRUB, "willow scrub at the large river");
	}

	// ------------------------------------------------------------------ checkpoint 2 (§12.3)

	/**
	 * Places seen from above: reedbed, willow scrub, beach, bogs and dwarf pine, with the camera {@value #OPEN_ABOVE}
	 * blocks over the ground (the willow scrub {@value #CROWN_ABOVE}, above the crowns). The upper montane spruce forest is
	 * seen from inside like the other forests (from above its crowns filled the frame, S7 review).
	 */
	private static final Set<String> OPEN = Set.of("lake_reedbed", "willow_scrub", "beach", "raised_bog", "dwarf_pine_scrub");
	private static final int OPEN_ABOVE = 10;
	private static final int CROWN_ABOVE = 16;

	/**
	 * A screenshot at each of the 11 places of §12.3 and at the extra forests ({@link #EXTRA_NAMES}), looking at the
	 * place's center with the HUD hidden: in forests from 2–4 blocks above the ground 8–16 blocks away, below most crowns
	 * (the floor, the trunks and the understory), in open habitats from {@value #OPEN_ABOVE} blocks above the ground, the
	 * willow scrub from {@value #CROWN_ABOVE}; the camera spot is the one with the clearest
	 * view in 16 directions ({@link #camera}).
	 *
	 * @return the centers of the places taken
	 */
	private static List<int[]> screenshots(ClientGameTestContext context, TestSingleplayerContext sp, boolean real,
			String scale) {
		List<int[]> taken = new ArrayList<>();
		String only = System.getProperty("polishforests.sites", "");
		if (only.equals("none")) {
			return taken;
		}
		int[][] soil = real ? HabitatsClientGameTest.SOIL_REAL : HabitatsClientGameTest.SOIL_GAMEPLAY;
		int[][] extra = real ? EXTRA_REAL : EXTRA_GAMEPLAY;
		List<String> names = new ArrayList<>(List.of(HabitatsClientGameTest.SOIL_NAMES));
		names.addAll(List.of(EXTRA_NAMES));
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		for (int p = 0; p < names.size(); p++) {
			String name = names.get(p);
			if (!only.isBlank() && !List.of(only.split(",")).contains(name)) {
				continue;
			}
			int[] c = p < soil.length ? soil[p] : extra[p - soil.length];
			if (c == null) {
				continue;
			}
			if (name.equals("willow_scrub")) {
				c = sp.getServer().computeOnServer(s -> willowScrubPlace(s, real));
			}
			int[] center = c;
			taken.add(center);
			HabitatBiome named = HabitatBiome.byId(name);
			double share = named == null ? Double.NaN
					: sp.getServer().computeOnServer(s -> biomeShare(s, center[0], center[1], named));
			if (p >= soil.length && !(share >= EXTRA_MIN_SHARE)) {
				throw new AssertionError(String.format(Locale.ROOT, "%s %s: center (%d, %d) holds only %.1f%% of %s within %d blocks",
						scale, name, center[0], center[1], 100 * share, name, EXTRA_SHARE_RADIUS));
			}
			boolean open = OPEN.contains(name);
			int centerY = sp.getServer().computeOnServer(s -> groundY(s, center[0], center[1]));
			String biome = sp.getServer().computeOnServer(s -> centerBiome(s, center[0], center[1]));
			int above = name.equals("willow_scrub") ? CROWN_ABOVE : open ? OPEN_ABOVE : 3;
			int[] camera = sp.getServer().computeOnServer(s -> camera(s, center[0], center[1], centerY, above));
			double dx = center[0] - camera[0];
			double dz = center[1] - camera[2];
			// Yaw: 0 looks to +z, -90 to +x; the pitch aims at the ground of the center (the eyes are 1.62 above the feet).
			double yaw = Math.toDegrees(Math.atan2(-dx, dz));
			double pitch = Math.toDegrees(Math.atan2(camera[1] + 1.62 - (centerY + 1), Math.hypot(dx, dz))) + (open ? 4 : 0);
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", camera[0], camera[1], camera[2], yaw,
					pitch));
			context.waitTicks(20 * 25);
			String client = context.computeOnClient(mc -> mc.level.getChunkSource().gatherStats());
			PolishForests.LOG.info("[vegetation] {} {}: center ({}, {}) {} ({} of the biome within {} blocks) ground Y {}, "
					+ "camera ({}, {}, {}) yaw {} pitch {}, view score {} (blocked steps); client {}", scale, name, center[0],
					center[1], biome, Double.isNaN(share) ? "-" : String.format(Locale.ROOT, "%.0f%%", 100 * share),
					EXTRA_SHARE_RADIUS, centerY, camera[0], camera[1], camera[2], String.format(Locale.ROOT, "%.1f", yaw),
					String.format(Locale.ROOT, "%.1f", pitch), camera[3], client);
			context.waitTicks(20 * 15);
			context.takeScreenshot("vegetation_" + name + "_" + scale);
		}
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		return taken;
	}

	/** Biome of the habitat code at the column (x, z) (the log shows that a place is what its name says). */
	/** Share of the columns (every 2 blocks) of the disk of {@link #EXTRA_SHARE_RADIUS} around (x, z) in the biome. */
	private static double biomeShare(MinecraftServer server, int x, int z, HabitatBiome biome) {
		PolandChunkGenerator gen = generator(server);
		long seed = server.overworld().getSeed();
		LandscapeModel m = gen.model(seed);
		HabitatClassifier k = gen.classifier(seed);
		int r = EXTRA_SHARE_RADIUS;
		int all = 0;
		int hits = 0;
		for (int dx = -r; dx <= r; dx += 2) {
			for (int dz = -r; dz <= r; dz += 2) {
				if (dx * dx + dz * dz <= r * r) {
					all++;
					if (Habitat.biome(k.classify(m.sample(x + dx, z + dz), x + dx, z + dz)) == biome) {
						hits++;
					}
				}
			}
		}
		return (double) hits / all;
	}

	private static String centerBiome(MinecraftServer server, int x, int z) {
		PolandChunkGenerator gen = generator(server);
		long seed = server.overworld().getSeed();
		int code = gen.classifier(seed).classify(gen.model(seed).sample(x, z), x, z);
		return Habitat.biome(code).id() + "/" + Habitat.zone(code).id();
	}

	/**
	 * Camera feet position around the center (cx, cz): {@code above} blocks over the ground (or water; in forests also
	 * one block lower or higher), with the feet, the head and the block above the head free (air or a plant, no leaves or
	 * logs), 8–16 blocks away in one of 16 directions. Of these the one with the clearest view: the fewest leaves, logs
	 * and solid blocks on the line of sight to the center's ground (first 30 blocks, weight 3) and on three lines to the
	 * edges of the frame (about 30 degrees to either side of the center, 4 blocks above it; first 12 blocks), so no trunk
	 * or crown fills a side of the frame (ties: the nearer to the south-west, the first distance).
	 *
	 * @return the feet position and the score (blocked steps)
	 */
	private static int[] camera(MinecraftServer server, int cx, int cz, int centerY, int above) {
		ServerLevel level = server.overworld();
		int[] best = null;
		int bestScore = Integer.MAX_VALUE;
		int[] lifts = above <= 3 ? new int[] {above, above - 1, above + 1} : new int[] {above};
		for (int back : new int[] {12, 10, 14, 16, 8}) {
			for (int a = 0; a < 16; a++) {
				// a = 0: south-west of the center (looking north-east), then around.
				double angle = Math.PI * 1.25 + a * Math.PI / 8;
				int x = cx + (int) Math.round(back * Math.cos(angle));
				int z = cz + (int) Math.round(back * Math.sin(angle));
				int ground = groundY(server, x, z);
				for (int lift : lifts) {
					int y = ground + 1 + lift;
					if (!free(level, x, y, z) || !free(level, x, y + 1, z) || !free(level, x, y + 2, z)) {
						continue;
					}
					double ex = x + 0.5;
					double ey = y + 1.62;
					double ez = z + 0.5;
					double tx = cx + 0.5;
					double tz = cz + 0.5;
					// Side targets at about 30 degrees to either side: the edges of the frame.
					double px = -(tz - ez) * 0.6;
					double pz = (tx - ex) * 0.6;
					int score = 3 * blocked(level, ex, ey, ez, tx, centerY + 1.0, tz, 30)
							+ blocked(level, ex, ey, ez, tx + px, centerY + 1.0, tz + pz, 12)
							+ blocked(level, ex, ey, ez, tx - px, centerY + 1.0, tz - pz, 12)
							+ blocked(level, ex, ey, ez, tx, centerY + 5.0, tz, 12);
					if (score < bestScore) {
						bestScore = score;
						best = new int[] {x, y, z, score};
					}
				}
			}
		}
		if (best != null) {
			return best;
		}
		int x = cx - 12;
		int z = cz - 12;
		int y = groundY(server, x, z) + 1 + above;
		while (!free(level, x, y, z) || !free(level, x, y + 1, z) || !free(level, x, y + 2, z)) {
			y++;
		}
		return new int[] {x, y, z, -1};
	}

	/** Leaves, logs and solid blocks on the first {@code reach} blocks of the line from the eye to the target (steps of 0.5). */
	private static int blocked(ServerLevel level, double x0, double y0, double z0, double x1, double y1, double z1, int reach) {
		double length = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
		int n = 0;
		BlockPos last = null;
		for (double t = 0.5; t <= Math.min(reach, length - 1); t += 0.5) {
			BlockPos pos = BlockPos.containing(x0 + (x1 - x0) * t / length, y0 + (y1 - y0) * t / length, z0 + (z1 - z0) * t / length);
			if (pos.equals(last)) {
				continue;
			}
			last = pos;
			n += free(level, pos.getX(), pos.getY(), pos.getZ()) ? 0 : 1;
		}
		return n;
	}

	static boolean free(ServerLevel level, int x, int y, int z) {
		BlockState s = level.getBlockState(new BlockPos(x, y, z));
		return !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && !s.isSolidRender() && s.getFluidState().isEmpty();
	}

	/** Top ground block of the surface plan at (x, z). */
	static int groundY(MinecraftServer server, int x, int z) {
		ServerLevel level = server.overworld();
		level.getChunk(x >> 4, z >> 4);
		ChunkSurface plan = generator(server).surface(new ChunkPos(x >> 4, z >> 4), level.getMinY(), level.getMaxY(),
				level.getSeed(), null);
		int i = ChunkHabitats.index(x & 15, z & 15);
		return plan.wet(i) ? plan.waterTop(i) : plan.top(i);
	}

}
