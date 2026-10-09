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
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
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
 * The "present-day Poland" mode in the game (step S8, docs/03-m2-biomy.md §3.4 step 6, §4.5, §10.1, decision M2-12),
 * in both world scales: a world created in the PRESENT_DAY mode (the options screen's dimensions update, the same as
 * its "Done" button) starts, its generator is in that mode and {@code validate()} passes; the transects of three rivers
 * meet the density rule of §4.6 ({@link VegetationClientGameTest#transects}); {@code /locate structure #minecraft:village}
 * finds villages from {@link #STARTS} (time, distance and the biome at the village are reported, the search must succeed
 * from each start in less than {@value #LOCATE_LIMIT_MS} ms); a screenshot of a mosaic of forest, meadows and fields
 * ({@code present_day_mosaic_<scale>}) and of a village in a forest ({@code present_day_forest_village_<scale>}); after
 * saving and opening the world again the mode and the biome of an arable land column are the same. The same village
 * search runs in a world of the natural-vegetation mode (villages also in forests, M2-12), with a screenshot of a forest
 * village ({@code natural_forest_village_<scale>}). Runs when {@code -Dpolishforests.gametest} is {@code present_day} or
 * {@code all}; {@code -Pscales} picks one scale.
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
				int[] village = sp.getServer().computeOnServer(s -> forestVillage(s, villages));
				PolishForests.LOG.info("[present_day] {}: forest village at ({}, {}), {}", name, village[0], village[1],
						sp.getServer().computeOnServer(s -> shares(s, village[0], village[1])));
				shot(context, sp, village, 34, 26, "present_day_forest_village_" + name);
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
				PolishForests.LOG.info("[present_day] {} natural: forest village at ({}, {}), {}", name, village[0], village[1],
						sp.getServer().computeOnServer(s -> shares(s, village[0], village[1])));
				shot(context, sp, village, 34, 26, "natural_forest_village_" + name);
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
	 * of the command; the time, the distance, the village type and the biome of the village center are reported.
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
			found.add(new int[] {p.getX(), p.getZ()});
			sumMs += ms;
			sumDistance += distance;
			sb.append(String.format(Locale.ROOT, " from (%d, %d): %s at (%d, %d), %.0f blocks, %.0f ms, biome %s;", start[0], start[1],
					nearest.getSecond().unwrapKey().map(k -> k.identifier().getPath()).orElse("?"), p.getX(), p.getZ(), distance, ms,
					biome(server, p.getX(), p.getZ()).id()));
		}
		sb.append(String.format(Locale.ROOT, " mean %.0f blocks, %.0f ms", sumDistance / STARTS.length, sumMs / STARTS.length));
		return sb.toString();
	}

	/**
	 * A village whose center lies in a forest biome: one of the villages found, or the nearest village of further
	 * searches from points around the starts (every 1500 blocks on a spiral, at most 40 searches).
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
			if (nearest != null && biome(server, nearest.getFirst().getX(), nearest.getFirst().getZ()).isForest()) {
				return new int[] {nearest.getFirst().getX(), nearest.getFirst().getZ()};
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
