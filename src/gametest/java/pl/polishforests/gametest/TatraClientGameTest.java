package pl.polishforests.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.command.PolishForestsCommands;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.TatraPart;

/**
 * Step TT1 (docs/04-pionowy-wycinek.md, "Projekt TT1"), {@code -Pgametest=tatra} (only by name): a world of the gameplay
 * preset (the Tatras on) with the test seed. Numbers only, no screenshots:
 * <ul>
 * <li>the Tatra targets of {@code /polishforests find} ({@code PolishForestsCommands.locate}): found, their distance and
 * the time of the search;</li>
 * <li>the patches of the golden Tatra file (set GAMEPLAY A, {@code golden_terrain_tatra.txt}): the generated chunk of
 * each, its top ground block against the model ({@code VerticalScale.topBlockY} of the sample: ground there, none above;
 * at most 16 columns of a chunk off, for ground blocks outside the checked tags), the Tatra rock blocks under the
 * surface where the model has a bedrock and none where it has none, the highest block (KT-1: below Y 720), and the time
 * of the generation;</li>
 * <li>the load of a 9 × 9 chunk square on the granite ridge (chunks per second, KT-6 by comparison with
 * {@code -Pgametest=performance}).</li>
 * </ul>
 * Report: {@code build/tatra/report.txt} ({@code -Dpolishforests.tatra.out}); the test fails on a missing target, a top
 * block off the model, a column with a bedrock in the model and no rock block (or the other way round), or a block above
 * Y 720.
 */
public final class TatraClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland_gameplay"));
	/** The patches of set GAMEPLAY A of {@code golden_terrain_tatra.txt}: name, x, z. */
	private static final Object[][] PATCHES = {{"granite_ridge", -24_423, 87_771}, {"cirque", -24_735, 87_952},
			{"regle_limestone", -23_189, 86_849}, {"western_tatra", -24_669, 87_706}, {"podhale", -21_775, 86_238},
			{"beskid_transition", -19_282, 84_795}};
	/** KT-1: no block above this Y. */
	private static final int MAX_Y = 720;
	/** Depth below the top block searched for the rock block of a column with a bedrock (blocks). */
	private static final int ROCK_DEPTH = 24;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"tatra".equals(System.getProperty("polishforests.gametest", "all"))) {
			return;
		}
		List<String> report = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(TatraClientGameTest::select).create()) {
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().computeOnServer(s -> {
				targets(s, report, failures);
				for (Object[] p : PATCHES) {
					patch(s.overworld(), (String) p[0], (Integer) p[1], (Integer) p[2], report, failures);
				}
				load(s.overworld(), (Integer) PATCHES[0][1], (Integer) PATCHES[0][2], report);
				return null;
			});
		}
		report.add(failures.isEmpty() ? "PASS" : "FAIL: " + String.join("; ", failures));
		String dir = System.getProperty("polishforests.tatra.out", "build/tatra");
		try {
			Files.createDirectories(Path.of(dir));
			Files.write(Path.of(dir, "report.txt"), report, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		report.forEach(l -> PolishForests.LOG.info("[tatra] {}", l));
		if (!failures.isEmpty()) {
			throw new AssertionError("Tatras: " + String.join("; ", failures));
		}
	}

	private static void select(WorldCreationUiState ui) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(PRESET)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + PRESET.identifier())));
		if (!(ui.getSettings().selectedDimensions().overworld() instanceof PolandChunkGenerator gen) || !gen.settings().tatra()) {
			throw new AssertionError("The preset " + PRESET.identifier() + " has no Poland generator with the Tatras");
		}
	}

	private static LandscapeModel model(ServerLevel level) {
		return ((PolandChunkGenerator) level.getChunkSource().getGenerator()).model(level.getSeed());
	}

	/** The Tatra targets of {@code find}, from the origin. */
	private static void targets(MinecraftServer server, List<String> report, List<String> failures) {
		LandscapeModel m = model(server.overworld());
		for (PolishForestsCommands.Target t : new PolishForestsCommands.Target[] {PolishForestsCommands.Target.TATRA,
				PolishForestsCommands.Target.HIGH_TATRA, PolishForestsCommands.Target.WESTERN_TATRA,
				PolishForestsCommands.Target.PODHALE, PolishForestsCommands.Target.CIRQUE, PolishForestsCommands.Target.U_VALLEY}) {
			long start = System.nanoTime();
			double[] p = PolishForestsCommands.locate(m, t, 0, 0);
			double ms = (System.nanoTime() - start) / 1e6;
			if (p == null) {
				failures.add("find " + t.id() + ": none");
				report.add(String.format(Locale.ROOT, "find %s: none (%.0f ms)", t.id(), ms));
				continue;
			}
			ColumnSample s = m.sample(p[0], p[1]);
			report.add(String.format(Locale.ROOT, "find %s: (%.0f, %.0f), %.0f m from the origin, %.0f ms; %s, rock %s, %.0f m a.s.l.",
					t.id(), p[0], p[1], Math.hypot(p[0], p[1]), ms, s.terrain().tatraPart(), s.terrain().rock(), s.surface()));
		}
	}

	/** The chunk of a patch against the model. */
	private static void patch(ServerLevel level, String name, int x, int z, List<String> report, List<String> failures) {
		LandscapeModel m = model(level);
		VerticalScale vs = ((PolandChunkGenerator) level.getChunkSource().getGenerator()).vertical();
		long start = System.nanoTime();
		LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
		double ms = (System.nanoTime() - start) / 1e6;
		int offTop = 0;
		int rockMissing = 0;
		int rockStray = 0;
		int highest = Integer.MIN_VALUE;
		Map<String, Integer> tops = new TreeMap<>();
		Map<TatraPart, Integer> parts = new TreeMap<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 256; i++) {
			int wx = (x & ~15) + (i >> 4);
			int wz = (z & ~15) + (i & 15);
			ColumnSample s = m.sample(wx, wz);
			parts.merge(s.terrain().tatraPart(), 1, Integer::sum);
			highest = Math.max(highest, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i >> 4, i & 15));
			// The top ground block of the model: ground there and none right above it (plants, trees, snow and air may be).
			int top = vs.topBlockY(s.surface());
			BlockState ground = chunk.getBlockState(at.set(wx, top, wz));
			if (!s.hasWater() && (!ground(ground) || ground(chunk.getBlockState(at.set(wx, top + 1, wz))))) {
				offTop++;
			}
			tops.merge(id(ground), 1, Integer::sum);
			boolean rock = false;
			for (int y = top; y > top - ROCK_DEPTH && y > level.getMinY(); y--) {
				String b = id(chunk.getBlockState(at.set(wx, y, wz)));
				if (b.startsWith("polishforests:") && (b.endsWith("granite") || b.endsWith("gneiss") || b.endsWith("limestone")
						|| b.endsWith("dolomite"))) {
					rock = true;
					break;
				}
			}
			if (s.terrain().rock() != null && !rock) {
				rockMissing++;
			}
			if (s.terrain().rock() == null && rock) {
				rockStray++;
			}
		}
		report.add(String.format(Locale.ROOT, "patch %s at (%d, %d): chunk %.0f ms; parts %s; top blocks %s; top off the model %d, "
				+ "bedrock without a rock block %d, rock block without a bedrock %d, highest block Y %d", name, x, z, ms, parts, tops,
				offTop, rockMissing, rockStray, highest));
		if (offTop > 16) {
			failures.add(name + ": " + offTop + " columns with the top block off the model");
		}
		if (rockMissing > 0 || rockStray > 0) {
			failures.add(name + ": rock blocks off the model (" + rockMissing + " missing, " + rockStray + " stray)");
		}
		if (highest > MAX_Y) {
			failures.add(name + ": a block at Y " + highest);
		}
	}

	/** Generation of a 9 × 9 chunk square around the place (chunks per second, single requests on the server thread). */
	private static void load(ServerLevel level, int x, int z, List<String> report) {
		int cx0 = (x >> 4) + 40;
		int cz0 = z >> 4;
		long start = System.nanoTime();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				level.getChunk(cx0 + dx, cz0 + dz);
			}
		}
		double s = (System.nanoTime() - start) / 1e9;
		report.add(String.format(Locale.ROOT, "load of 81 new chunks 640 blocks east of the granite ridge: %.1f s, %.1f chunks/s", s,
				81 / s));
	}

	/** A ground block: stone and the Tatra rocks (the overworld stone tag), dirt, sand and gravel. */
	private static boolean ground(BlockState s) {
		return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL);
	}

	private static String id(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
	}
}
