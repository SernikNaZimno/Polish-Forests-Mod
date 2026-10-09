package pl.polishforests.gametest;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import pl.polishforests.PolishForests;
import pl.polishforests.client.screen.PolandPresetEditor;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.chunk.PolandSettings;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.LandscapeModel;

/**
 * The "present-day Poland" mode in the game (step S8, docs/03-m2-biomy.md §3.4 step 6, §4.5, §10.1, decisions M2-12 and
 * M2-17), in both world scales: a world created in the PRESENT_DAY mode (the options screen's dimensions update, the same
 * as its "Done" button) starts, its generator is in that mode and {@code validate()} passes; the transects of three
 * rivers meet the density rule of §4.6 ({@link VegetationClientGameTest#transects}); {@code /locate structure
 * #minecraft:village} finds villages from {@link #STARTS} (time, distance and the biome at the center of the start piece,
 * where vanilla checks it, are reported; the search must succeed from each start in less than {@value #LOCATE_LIMIT_MS}
 * ms; no village starts in a forest biome, M2-17); every village found passes the village checks ({@link #villageCheck}:
 * no trunk on a street, no leaves and no plants under the roofs, no floor column of a building hanging 2 or more blocks
 * above the ground); screenshots of a mosaic of forest, meadows and fields ({@code present_day_mosaic_<scale>}) and of a
 * village from above, from its street at eye level and from the downhill side ({@code present_day_village_<scale>},
 * {@code _street}, {@code _downhill}); after saving and opening the world again the mode and the biome of an arable land
 * column are the same. The same village search and checks run in a world of the natural-vegetation mode (villages also in
 * forests, M2-12), with the three screenshots of a village whose start lies in a forest
 * ({@code natural_forest_village_<scale>}, {@code _street}, {@code _downhill}). Runs when {@code -Dpolishforests.gametest}
 * is {@code present_day} or {@code all}; {@code -Pscales} picks one scale.
 */
public final class PresentDayClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int RENDER_DISTANCE = 12;
	/** Starts of the village search (block coordinates, both scales). */
	static final int[][] STARTS = {{0, 0}, {4_000, -4_000}, {-4_000, 4_000}, {8_000, 8_000}, {-8_000, -8_000}};
	/** Upper limit of one village search (ms): the vanilla search of a common structure. */
	private static final double LOCATE_LIMIT_MS = 5_000;
	/** Side of the mosaic window (blocks) and the step of its samples. */
	private static final int MOSAIC = 192;
	private static final int MOSAIC_STEP = 8;

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polishforests.gametest", "all");
		if (!mode.equals("present_day") && !mode.equals("all")) {
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
			String name = scale.getSerializedName();
			boolean real = scale == PolandScale.REALISTIC;
			TestWorldSave save;
			int[] arable;
			try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset, true)).create()) {
				prepare(sp);
				PolishForests.LOG.info("[present_day] {}: {}", name, sp.getServer().computeOnServer(s -> validate(s, true)));
				PolishForests.LOG.info("[present_day] {}: {}", name, sp.getServer().computeOnServer(s -> VegetationClientGameTest.transects(s, real)));
				List<int[]> villages = new ArrayList<>();
				PolishForests.LOG.info("[present_day] {}: {}", name, sp.getServer().computeOnServer(s -> villages(s, villages)));
				int[] mosaic = sp.getServer().computeOnServer(s -> mosaic(s, real));
				PolishForests.LOG.info("[present_day] {}: mosaic at ({}, {}): {}", name, mosaic[0], mosaic[1],
						sp.getServer().computeOnServer(s -> shares(s, mosaic[0], mosaic[1])));
				shot(context, sp, mosaic, real ? 70 : 60, real ? 100 : 90, "present_day_mosaic_" + name);
				int[] village = villages.getFirst();
				PolishForests.LOG.info("[present_day] {}: village at ({}, {}), {}", name, village[0], village[1],
						sp.getServer().computeOnServer(s -> shares(s, village[0], village[1])));
				villageShots(context, sp, village, "present_day_village_" + name);
				arable = sp.getServer().computeOnServer(s -> find(s, HabitatBiome.ARABLE_LAND));
				save = sp.getWorldSave();
			}
			try (TestSingleplayerContext sp = save.open()) {
				PolishForests.LOG.info("[present_day] {}: reopened, {}", name, sp.getServer().computeOnServer(s -> reopened(s, arable)));
			}
			try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset, false)).create()) {
				prepare(sp);
				PolishForests.LOG.info("[present_day] {} natural: {}", name, sp.getServer().computeOnServer(s -> validate(s, false)));
				List<int[]> villages = new ArrayList<>();
				PolishForests.LOG.info("[present_day] {} natural: {}", name, sp.getServer().computeOnServer(s -> villages(s, villages)));
				int[] village = sp.getServer().computeOnServer(s -> forestVillage(s, villages));
				PolishForests.LOG.info("[present_day] {} natural: forest village at ({}, {}), {}; {}", name, village[0], village[1],
						sp.getServer().computeOnServer(s -> shares(s, village[0], village[1])),
						sp.getServer().computeOnServer(s -> villageCheck(s, village)));
				villageShots(context, sp, village, "natural_forest_village_" + name);
			}
		}
	}

	/** World of the preset with structures, in the PRESENT_DAY mode ({@code presentDay}) or in the natural mode of the preset. */
	private static void select(WorldCreationUiState ui, ResourceKey<WorldPreset> preset, boolean presentDay) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		// The test world builder turns structures off by default; villages need them.
		ui.setGenerateStructures(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(preset)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier())));
		if (presentDay) {
			if (!(ui.getSettings().selectedDimensions().overworld() instanceof PolandChunkGenerator gen)) {
				throw new AssertionError("The preset " + preset.identifier() + " has no Poland generator");
			}
			ui.updateDimensions(PolandPresetEditor.apply(gen.settings().withAgriculture(true)));
		}
	}

	private static void prepare(TestSingleplayerContext sp) {
		sp.getServer().runCommand("gamerule advance_time false");
		sp.getServer().runCommand("gamerule advance_weather false");
		sp.getServer().runCommand("gamerule spawn_mobs false");
		sp.getServer().runCommand("time set 6000");
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("gamemode spectator @a");
	}

	private static PolandChunkGenerator generator(MinecraftServer server) {
		if (!(server.overworld().getChunkSource().getGenerator() instanceof PolandChunkGenerator gen)) {
			throw new AssertionError("The overworld does not use the Poland generator");
		}
		return gen;
	}

	/** The world is in the expected mode and the feature order is valid. */
	private static String validate(MinecraftServer server, boolean presentDay) {
		PolandChunkGenerator gen = generator(server);
		gen.validate();
		PolandSettings s = gen.settings();
		HabitatClassifier.Mode expected = presentDay ? HabitatClassifier.Mode.PRESENT_DAY : HabitatClassifier.Mode.NATURAL;
		if (s.agriculture() != presentDay || gen.classifier(server.overworld().getSeed()).mode() != expected) {
			throw new AssertionError("The world is not in the " + expected + " mode: " + s);
		}
		return "generator.validate() passed, " + expected + " mode, settings " + s;
	}

	/**
	 * {@code /locate structure #minecraft:village} from each start ({@link #STARTS}): the vanilla search with the radius
	 * of the command; the time, the distance, the village type and the biome at the center of the start piece (the place
	 * of the vanilla biome check; the locate position is the corner of the start chunk) are reported. In the PRESENT_DAY
	 * mode no village may start in a forest biome (M2-17). Each village passes {@link #villageCheck}. {@code found}
	 * receives the start piece centers.
	 */
	private static String villages(MinecraftServer server, List<int[]> found) {
		ServerLevel level = server.overworld();
		HolderSet<Structure> set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(StructureTags.VILLAGE);
		StringBuilder sb = new StringBuilder("/locate structure #minecraft:village:");
		double sumMs = 0;
		double sumDistance = 0;
		for (int[] start : STARTS) {
			long t0 = System.nanoTime();
			Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator().findNearestMapStructure(level, set,
					new BlockPos(start[0], 100, start[1]), 100, false);
			double ms = (System.nanoTime() - t0) / 1e6;
			if (nearest == null) {
				throw new AssertionError("No village found from " + start[0] + ", " + start[1]);
			}
			BlockPos p = nearest.getFirst();
			double distance = Math.hypot(p.getX() - start[0], p.getZ() - start[1]);
			if (ms > LOCATE_LIMIT_MS) {
				throw new AssertionError(String.format(Locale.ROOT, "village search from (%d, %d) took %.0f ms", start[0], start[1], ms));
			}
			int[] center = startCenter(level, p, nearest.getSecond().value());
			HabitatBiome biome = biome(server, center[0], center[1]);
			if (generator(server).settings().agriculture() && biome.isForest()) {
				throw new AssertionError("A village of the present-day mode starts in a forest: " + biome.id() + " at ("
						+ center[0] + ", " + center[1] + ")");
			}
			found.add(center);
			sumMs += ms;
			sumDistance += distance;
			sb.append(String.format(Locale.ROOT, " from (%d, %d): %s at (%d, %d), start piece center (%d, %d), %.0f blocks, %.0f ms, biome %s, %s;",
					start[0], start[1], nearest.getSecond().unwrapKey().map(k -> k.identifier().getPath()).orElse("?"), p.getX(), p.getZ(),
					center[0], center[1], distance, ms, biome.id(), villageCheck(server, center)));
		}
		sb.append(String.format(Locale.ROOT, " mean %.0f blocks, %.0f ms", sumDistance / STARTS.length, sumMs / STARTS.length));
		return sb.toString();
	}

	/**
	 * Center of the start piece of the structure whose locate position is {@code p} (the minimum corner of its start
	 * chunk): there vanilla checks the biome of the start ({@code Structure.GenerationContext.isValidBiome}).
	 */
	private static int[] startCenter(ServerLevel level, BlockPos p, Structure structure) {
		StructureStart start = level.getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
		if (start == null || !start.isValid()) {
			throw new AssertionError("No start of the village in the chunk of (" + p.getX() + ", " + p.getZ() + ")");
		}
		BlockPos c = start.getPieces().getFirst().getBoundingBox().getCenter();
		return new int[] {c.getX(), c.getZ()};
	}

	/**
	 * A village whose start piece center lies in a forest biome: one of the villages found, or the nearest village of
	 * further searches from points around the starts (every 1500 blocks on a spiral, at most 40 searches).
	 */
	private static int[] forestVillage(MinecraftServer server, List<int[]> found) {
		for (int[] v : found) {
			if (biome(server, v[0], v[1]).isForest()) {
				return v;
			}
		}
		ServerLevel level = server.overworld();
		HolderSet<Structure> set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(StructureTags.VILLAGE);
		int x = 0;
		int z = 0;
		int dx = 1;
		int dz = 0;
		int leg = 1;
		int steps = 0;
		int turns = 0;
		for (int i = 0; i < 40; i++) {
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
			Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator().findNearestMapStructure(level, set,
					new BlockPos(x * 1_500, 100, z * 1_500), 100, false);
			if (nearest != null) {
				int[] center = startCenter(level, nearest.getFirst(), nearest.getSecond().value());
				if (biome(server, center[0], center[1]).isForest()) {
					return center;
				}
			}
		}
		throw new AssertionError("No village in a forest found");
	}

	private static HabitatBiome biome(MinecraftServer server, int x, int z) {
		PolandChunkGenerator gen = generator(server);
		long seed = server.overworld().getSeed();
		return Habitat.biome(gen.classifier(seed).classify(gen.model(seed).sample(x, z), x, z));
	}

	/** Biome shares (%) in the window of {@value #MOSAIC} blocks around (x, z). */
	private static String shares(MinecraftServer server, int x, int z) {
		Map<HabitatBiome, Integer> counts = counts(server, x, z);
		int all = counts.values().stream().mapToInt(Integer::intValue).sum();
		StringBuilder sb = new StringBuilder("biomes within " + MOSAIC / 2 + " blocks:");
		counts.forEach((b, n) -> sb.append(String.format(Locale.ROOT, " %s %.0f%%", b.id(), 100.0 * n / all)));
		return sb.toString();
	}

	private static Map<HabitatBiome, Integer> counts(MinecraftServer server, int x, int z) {
		PolandChunkGenerator gen = generator(server);
		long seed = server.overworld().getSeed();
		LandscapeModel m = gen.model(seed);
		HabitatClassifier k = gen.classifier(seed);
		Map<HabitatBiome, Integer> counts = new EnumMap<>(HabitatBiome.class);
		for (int dx = -MOSAIC / 2; dx < MOSAIC / 2; dx += MOSAIC_STEP) {
			for (int dz = -MOSAIC / 2; dz < MOSAIC / 2; dz += MOSAIC_STEP) {
				counts.merge(Habitat.biome(k.classify(m.sample(x + dx, z + dz), x + dx, z + dz)), 1, Integer::sum);
			}
		}
		return counts;
	}

	/**
	 * Center of a mosaic of forest, meadows and fields near the origin: of the windows on a grid every 512 blocks within
	 * 8 km (REAL) or 4 km (GAMEPLAY), the one with forest, arable land and meadows closest to a third each, little water.
	 */
	private static int[] mosaic(MinecraftServer server, boolean real) {
		int reach = real ? 8_192 : 4_096;
		int[] best = null;
		double bestScore = -1;
		for (int x = -reach; x <= reach; x += 512) {
			for (int z = -reach; z <= reach; z += 512) {
				Map<HabitatBiome, Integer> c = counts(server, x, z);
				int all = c.values().stream().mapToInt(Integer::intValue).sum();
				double forest = 0;
				double water = 0;
				for (Map.Entry<HabitatBiome, Integer> e : c.entrySet()) {
					forest += e.getKey().isForest() ? e.getValue() : 0;
					water += e.getKey().isWater() ? e.getValue() : 0;
				}
				forest /= all;
				water /= all;
				double arable = c.getOrDefault(HabitatBiome.ARABLE_LAND, 0) / (double) all;
				double meadow = (c.getOrDefault(HabitatBiome.HAY_MEADOW, 0) + c.getOrDefault(HabitatBiome.WET_MEADOW, 0)) / (double) all;
				double score = Math.min(forest, Math.min(arable, meadow)) - water;
				if (score > bestScore) {
					bestScore = score;
					best = new int[] {x, z};
				}
			}
		}
		return best;
	}

	/**
	 * A screenshot of the place (x, z) from {@code above} blocks over its ground, {@code back} blocks to the south-west,
	 * looking at its ground, with the HUD hidden.
	 */
	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, int[] center, int above, int back,
			String screenshot) {
		int groundY = sp.getServer().computeOnServer(s -> VegetationClientGameTest.groundY(s, center[0], center[1]));
		int cx = center[0] - (int) Math.round(back * Math.sqrt(0.5));
		int cz = center[1] - (int) Math.round(back * Math.sqrt(0.5));
		int y = groundY + above;
		double dx = center[0] - cx;
		double dz = center[1] - cz;
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		double pitch = Math.toDegrees(Math.atan2(above + 1.62, Math.hypot(dx, dz)));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", cx, y, cz, yaw, pitch));
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		context.waitTicks(20 * 40);
		PolishForests.LOG.info("[present_day] {}: camera ({}, {}, {}) yaw {} pitch {}, client {}", screenshot, cx, y, cz,
				String.format(Locale.ROOT, "%.1f", yaw), String.format(Locale.ROOT, "%.1f", pitch),
				context.computeOnClient(mc -> mc.level.getChunkSource().gatherStats()));
		context.waitTicks(20 * 10);
		context.takeScreenshot(screenshot);
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	/** The village start whose start piece center is (x, z), with the chunks of its box loaded. */
	private static StructureStart village(ServerLevel level, int[] center) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (Map.Entry<Structure, StructureStart> e : level.getChunk((center[0] >> 4) + dx, (center[1] >> 4) + dz,
						ChunkStatus.STRUCTURE_STARTS).getAllStarts().entrySet()) {
					StructureStart start = e.getValue();
					if (start.isValid() && level.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(e.getKey())
							.is(StructureTags.VILLAGE)) {
						BlockPos c = start.getPieces().getFirst().getBoundingBox().getCenter();
						if (c.getX() == center[0] && c.getZ() == center[1]) {
							return start;
						}
					}
				}
			}
		}
		throw new AssertionError("No village with the start piece center (" + center[0] + ", " + center[1] + ")");
	}

	/** Building pieces of a village: rigid pool elements other than features (houses, town centers, farms, pens). */
	private static List<BoundingBox> buildings(StructureStart start) {
		List<BoundingBox> out = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof PoolElementStructurePiece pool && !(pool.getElement() instanceof FeaturePoolElement)
					&& pool.getElement().getProjection() == StructureTemplatePool.Projection.RIGID) {
				out.add(piece.getBoundingBox());
			}
		}
		return out;
	}

	private static boolean support(BlockState s) {
		return !s.isAir() && s.isSolid() && !s.is(BlockTags.LEAVES);
	}

	/**
	 * Village checks (round 1 of the S8 review) in the box of the village loaded to full status: trunks of the tree stand
	 * on a street (a log on {@code dirt_path}), leaves and plants (moss carpet, grass, ferns, flowers, bushes, petals) under
	 * the roof of a building piece on a block that is not soil, and floor columns of a building piece (a solid block in its
	 * lowest layer) with 2 or more blocks without support below. All must be 0.
	 */
	static String villageCheck(MinecraftServer server, int[] center) {
		ServerLevel level = server.overworld();
		StructureStart start = village(level, center);
		BoundingBox box = start.getBoundingBox();
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.getChunk(cx, cz);
			}
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int trunksOnPath = 0;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				for (int y = top; y > top - 40; y--) {
					if (level.getBlockState(p.set(x, y, z)).is(BlockTags.LOGS) && level.getBlockState(p.set(x, y - 1, z)).is(Blocks.DIRT_PATH)) {
						trunksOnPath++;
						break;
					}
				}
			}
		}
		List<BoundingBox> buildings = buildings(start);
		int leaves = 0;
		int plants = 0;
		int hanging = 0;
		int maxGap = 0;
		for (BoundingBox b : buildings) {
			int y0 = b.minY();
			for (int x = b.minX(); x <= b.maxX(); x++) {
				for (int z = b.minZ(); z <= b.maxZ(); z++) {
					if (support(level.getBlockState(p.set(x, y0, z)))) {
						int gap = 0;
						while (gap < 16 && !support(level.getBlockState(p.set(x, y0 - 1 - gap, z)))) {
							gap++;
						}
						maxGap = Math.max(maxGap, gap);
						hanging += gap >= 2 ? 1 : 0;
					}
					int roof = Integer.MIN_VALUE;
					for (int y = b.maxY(); y > y0 + 1; y--) {
						BlockState s = level.getBlockState(p.set(x, y, z));
						if (support(s) && !s.is(BlockTags.LOGS)) {
							roof = y;
							break;
						}
					}
					for (int y = y0; y < roof; y++) {
						BlockState s = level.getBlockState(p.set(x, y, z));
						if (s.is(BlockTags.LEAVES)) {
							leaves++;
						} else if (plant(s) && !level.getBlockState(p.set(x, y - 1, z)).is(BlockTags.DIRT)) {
							plants++;
						}
					}
				}
			}
		}
		String report = String.format(Locale.ROOT, "village checks: %d buildings, trunks on streets %d, leaves under roofs %d, plants on floors %d, "
				+ "floor columns hanging >= 2 blocks %d (largest gap %d)", buildings.size(), trunksOnPath, leaves, plants, hanging, maxGap);
		if (trunksOnPath + leaves + plants + hanging > 0) {
			throw new AssertionError("Village at (" + center[0] + ", " + center[1] + "): " + report);
		}
		return report;
	}

	/** A plant of the plant layers that must not stand on a floor of a building. */
	private static boolean plant(BlockState s) {
		return s.is(Blocks.MOSS_CARPET) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)
				|| s.is(Blocks.BUSH) || s.is(Blocks.PINK_PETALS) || s.is(Blocks.WILDFLOWERS) || s.is(BlockTags.SMALL_FLOWERS);
	}

	/**
	 * Three screenshots of the village with the start piece center {@code center}: from above ({@code name}), from a street
	 * at eye level towards the village center ({@code name_street}) and from the downhill side towards the building whose
	 * floor lies highest above its surroundings ({@code name_downhill}).
	 */
	private static void villageShots(ClientGameTestContext context, TestSingleplayerContext sp, int[] center, String name) {
		shot(context, sp, center, 34, 26, name);
		int[] street = sp.getServer().computeOnServer(s -> streetView(s, center));
		if (street != null) {
			shotAt(context, sp, street, name + "_street");
		}
		int[] downhill = sp.getServer().computeOnServer(s -> downhillView(s, center));
		if (downhill != null) {
			shotAt(context, sp, downhill, name + "_downhill");
		}
	}

	/** Non-air, non-fluid blocks on the ray from the eye at (x0, y0, z0) to the target (the last 1.5 blocks not counted). */
	private static int obstruction(ServerLevel level, double x0, double y0, double z0, double x1, double y1, double z1) {
		double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
		int steps = (int) Math.ceil(len / 0.5);
		int n = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 1; i < steps; i++) {
			double t = i / (double) steps;
			if ((1 - t) * len < 1.5) {
				break;
			}
			BlockState s = level.getBlockState(p.set(Math.floor(x0 + (x1 - x0) * t), Math.floor(y0 + (y1 - y0) * t), Math.floor(z0 + (z1 - z0) * t)));
			if (!s.isAir() && s.getFluidState().isEmpty()) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Camera {x, y, z, target x, target y, target z} on a ring of radius {@code rMin}–{@code rMax} around the target with
	 * the clearest line of sight, the eye {@code above} blocks over the local ground.
	 */
	private static int[] view(ServerLevel level, int tx, int ty, int tz, int rMin, int rMax, int above) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int[] best = null;
		double bestScore = Double.MAX_VALUE;
		for (int r = rMin; r <= rMax; r += 3) {
			for (int a = 0; a < 24; a++) {
				double phi = a * Math.PI / 12;
				int cx = tx + (int) Math.round(Math.cos(phi) * r);
				int cz = tz + (int) Math.round(Math.sin(phi) * r);
				int cy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) + above - 1;
				if (!level.getBlockState(p.set(cx, cy, cz)).isAir() || !level.getBlockState(p.set(cx, cy + 1, cz)).isAir()) {
					continue;
				}
				double score = obstruction(level, cx + 0.5, cy + 1.62, cz + 0.5, tx + 0.5, ty + 0.5, tz + 0.5) * 10 + r * 0.05
						+ Math.abs(cy - ty) * 0.2;
				if (score < bestScore) {
					bestScore = score;
					best = new int[] {cx, cy, cz, tx, ty, tz};
				}
			}
		}
		return best;
	}

	/** A camera on a street of the village (a {@code dirt_path} block 8–30 blocks from the center) looking at the center. */
	private static int[] streetView(MinecraftServer server, int[] center) {
		ServerLevel level = server.overworld();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center[0], center[1]) + 2;
		int[] best = null;
		double bestScore = Double.MAX_VALUE;
		for (int x = center[0] - 30; x <= center[0] + 30; x += 2) {
			for (int z = center[1] - 30; z <= center[1] + 30; z += 2) {
				double d = Math.hypot(x - center[0], z - center[1]);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				if (d < 8 || d > 30 || !level.getBlockState(p.set(x, y, z)).is(Blocks.DIRT_PATH)
						|| !level.getBlockState(p.set(x, y + 1, z)).isAir() || !level.getBlockState(p.set(x, y + 2, z)).isAir()) {
					continue;
				}
				double score = obstruction(level, x + 0.5, y + 2.62, z + 0.5, center[0] + 0.5, ty + 0.5, center[1] + 0.5) * 10 + Math.abs(d - 18) * 0.1;
				if (score < bestScore) {
					bestScore = score;
					best = new int[] {x, y + 1, z, center[0], ty, center[1]};
				}
			}
		}
		return best;
	}

	/**
	 * A camera on the downhill side of the building whose lowest layer lies highest above the ground model around it,
	 * 2 blocks over the ground, looking at the foot of the building.
	 */
	private static int[] downhillView(MinecraftServer server, int[] center) {
		ServerLevel level = server.overworld();
		BoundingBox highest = null;
		int rise = Integer.MIN_VALUE;
		int lowX = 0;
		int lowZ = 0;
		for (BoundingBox b : buildings(village(level, center))) {
			for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int x = d[0] > 0 ? b.maxX() + 6 : d[0] < 0 ? b.minX() - 6 : (b.minX() + b.maxX()) / 2;
				int z = d[1] > 0 ? b.maxZ() + 6 : d[1] < 0 ? b.minZ() - 6 : (b.minZ() + b.maxZ()) / 2;
				int r = b.minY() - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (r > rise) {
					rise = r;
					highest = b;
					lowX = x;
					lowZ = z;
				}
			}
		}
		if (highest == null) {
			return null;
		}
		BlockPos c = highest.getCenter();
		int tx = c.getX();
		int tz = c.getZ();
		int dx = lowX - tx;
		int dz = lowZ - tz;
		double len = Math.max(1, Math.hypot(dx, dz));
		int span = Math.max(highest.getXSpan(), highest.getZSpan()) / 2;
		int[] best = view(level, tx, highest.minY(), tz, span + 6, span + 18, 2);
		// Prefer the downhill direction: a camera on the line from the center through the low point.
		int cx = tx + (int) Math.round(dx / len * (span + 12));
		int cz = tz + (int) Math.round(dz / len * (span + 12));
		int cy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) + 1;
		if (obstruction(level, cx + 0.5, cy + 1.62, cz + 0.5, tx + 0.5, highest.minY() + 0.5, tz + 0.5) <= 2 || best == null) {
			best = new int[] {cx, cy, cz, tx, highest.minY(), tz};
		}
		PolishForests.LOG.info("[present_day] downhill view of the building at ({}, {}, {}), floor {} blocks above the ground 6 blocks away",
				tx, highest.minY(), tz, rise);
		return best;
	}

	/** A screenshot from the camera {x, y, z} towards the target {tx, ty, tz} with the HUD hidden. */
	private static void shotAt(ClientGameTestContext context, TestSingleplayerContext sp, int[] v, String screenshot) {
		double dx = v[3] + 0.5 - (v[0] + 0.5);
		double dz = v[5] + 0.5 - (v[2] + 0.5);
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		double pitch = Math.toDegrees(Math.atan2(v[1] + 1.62 - (v[4] + 0.5), Math.max(0.01, Math.hypot(dx, dz))));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", v[0], v[1], v[2], yaw, pitch));
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		context.waitTicks(20 * 15);
		PolishForests.LOG.info("[present_day] {}: camera ({}, {}, {}) yaw {} pitch {}, target ({}, {}, {})", screenshot, v[0], v[1], v[2],
				String.format(Locale.ROOT, "%.1f", yaw), String.format(Locale.ROOT, "%.1f", pitch), v[3], v[4], v[5]);
		context.waitTicks(20 * 3);
		context.takeScreenshot(screenshot);
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	/** The nearest column of the biome to the origin (spiral every 16 blocks), with its biome holder checked in the level. */
	private static int[] find(MinecraftServer server, HabitatBiome wanted) {
		for (int r = 0; r < 400; r++) {
			for (int i = -r; i <= r; i++) {
				for (int[] p : new int[][] {{i * 16, -r * 16}, {i * 16, r * 16}, {-r * 16, i * 16}, {r * 16, i * 16}}) {
					if (biome(server, p[0], p[1]) == wanted) {
						return p;
					}
				}
			}
		}
		throw new AssertionError("No " + wanted + " near the origin");
	}

	/** After opening the saved world: still the PRESENT_DAY mode, and the column of arable land has that biome in the level. */
	private static String reopened(MinecraftServer server, int[] arable) {
		String mode = validate(server, true);
		ServerLevel level = server.overworld();
		level.getChunk(arable[0] >> 4, arable[1] >> 4);
		String id = level.getBiome(new BlockPos(arable[0], level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
				arable[0], arable[1]), arable[1])).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
		if (!id.equals("polishforests:" + HabitatBiome.ARABLE_LAND.id())) {
			throw new AssertionError("After reopening, the arable land column at (" + arable[0] + ", " + arable[1] + ") has the biome " + id);
		}
		return mode + "; arable land column at (" + arable[0] + ", " + arable[1] + ") still " + id;
	}
}
