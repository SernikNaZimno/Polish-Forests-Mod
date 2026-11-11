package pl.polishforests.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
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
import pl.polishforests.command.HabitatTargets;
import pl.polishforests.command.PolishForestsCommands;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.TatraPart;
import pl.polishforests.worldgen.surface.Material;

/**
 * Step TT1 (docs/04-pionowy-wycinek.md, "Projekt TT1"), {@code -Pgametest=tatra} (only by name): a world of the gameplay
 * preset (the Tatras on) with the test seed. Numbers only, no screenshots:
 * <ul>
 * <li>the Tatra targets of {@code /polishforests find} ({@code PolishForestsCommands.locate}): found, their distance and
 * the time of the search;</li>
 * <li>the patches of the golden Tatra file (set GAMEPLAY A, {@code golden_terrain_tatra.txt}): the generated chunk of
 * each, its top ground block against the model ({@code VerticalScale.topBlockY} of the sample: ground there, none above;
 * at most 16 columns of a chunk off, for ground blocks outside the checked tags), the Tatra rock blocks under the
 * surface where the model has a bedrock (the rock block of that bedrock) and none where it has none, the highest block
 * (KT-1: below Y 720), and the time of the generation;</li>
 * <li>the summit of the massif nearest to the origin (the highest point of the model on a 40 m grid, refined on a 4 m
 * one): 2450–2530 m, the generated top block there as in the model, no block of its chunk above Y 720;</li>
 * <li>three transects across the range (a quarter of the main ridge west of its middle, the middle, a quarter east), from
 * 6 km south of the main ridge through the regle, the trough, the foothills and the Podhale basin to 2 km beyond the
 * reach (the Beskids), and one along the main ridge 2 km past its ends, every 250 m: the height profile of the model
 * against the generated top ground block (at most 3% of the dry points off, at most 15% lower with air at the model top:
 * cave openings and carvers), the highest top, the drop from the crest
 * to the basin floor and the slopes between the points;</li>
 * <li>the census of the rock blocks by part of the complex (the first Tatra rock block under the top of every checked
 * column);</li>
 * <li>the load of a 9 × 9 chunk square on the granite ridge (chunks per second, KT-6 by comparison with
 * {@code -Pgametest=performance}).</li>
 * <li>step TT2: the census of the tarns of the nearest massif ({@value #TARNS} of them, the largest first): the generated
 * water of every column the model gives to a tarn, each water block of its top layer with a neighbor of the same layer
 * that is neither water nor a solid block (a leak) and each water column with air under it (hanging water), both must be
 * 0; the outlet brooks;</li>
 * <li>step TT2: the census of the top blocks of the transect points by the bedrock of the model (rock, scree, snow and the
 * soils on granite, gneiss, limestone, dolomite and flysch);</li>
 * <li>step TT2: the histogram of the slopes in blocks (central differences of the model tops, 16 m grid over the massif)
 * by landform (pinnacle, gully, scree, moraine, cirque, other) in {@code build/tatra/slopes.txt};</li>
 * <li>step TT2: a snow patch ({@code find snow_patch}): a snow block at its top, still there after
 * {@code /season set mid_summer} and 200 ticks at a random tick speed of 300.</li>
 * </ul>
 * Report: {@code build/tatra/report.txt} ({@code -Dpolishforests.tatra.out}); the test fails on a missing target, a top
 * block off the model, a column with a bedrock in the model without its rock block, a rock block without a bedrock, a
 * transect off the model, a summit out of range or a block above Y 720.
 */
public final class TatraClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland_gameplay"));
	/** The patches of set GAMEPLAY A of {@code golden_terrain_tatra.txt}: name, x, z. */
	private static final Object[][] PATCHES = {{"granite_ridge", -24_456, 87_894}, {"cirque", -24_735, 87_952},
			{"regle_limestone", -23_189, 86_849}, {"western_tatra", -24_669, 87_706}, {"podhale", -21_775, 86_238},
			{"beskid_transition", -19_282, 84_795}};
	/** KT-1: no block above this Y. */
	private static final int MAX_Y = 720;
	/** Depth below the top block searched for the rock block of a column with a bedrock (blocks). */
	private static final int ROCK_DEPTH = 24;
	/** Ticks waited after each step of the work (the generated chunks unload and save). */
	private static final int SETTLE_TICKS = 100;
	/** Spacing of the transect points (m). */
	private static final double TRANSECT_STEP = 250;
	/** Half side of the box searched for the massif nearest to the origin (m, as the wide phase of find tatra). */
	private static final double BOX = 300_000;
	/** Step TT2: tarns of the nearest massif in the census. */
	private static final int TARNS = 6;
	/** Step TT2: grid spacing of the slope histogram (m). */
	private static final double SLOPE_GRID = 16;
	/** The ids of the Tatra rock blocks. */
	private static final List<String> TATRA_SCREE = List.of(Material.GRANITE_SCREE.id(), Material.LIMESTONE_SCREE.id());
	private static final List<String> TATRA_ROCKS = List.of(Material.TATRA_GRANITE.id(), Material.GNEISS.id(),
			Material.LIMESTONE.id(), Material.DOLOMITE.id());

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"tatra".equals(System.getProperty("polishforests.gametest", "all"))) {
			return;
		}
		List<String> report = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(TatraClientGameTest::select).create()) {
			sp.getServer().runCommand("gamemode spectator @a");
			// The work in steps with ticks between them: the chunks generated by a step (a temporary ticket each) unload and
			// save in those ticks, so the world does not close with hundreds of chunks to save at once (the stall watchdog).
			Map<TatraPart, Map<String, Integer>> census = new TreeMap<>();
			Map<String, Map<String, Integer>> surface = new TreeMap<>();
			LandscapeModel.Tatra t = sp.getServer().computeOnServer(s -> {
				targets(s, report, failures);
				for (Object[] p : PATCHES) {
					patch(s.overworld(), (String) p[0], (Integer) p[1], (Integer) p[2], census, report, failures);
				}
				// Before the summit and the transects, so none of their chunks is in the square.
				load(s.overworld(), (Integer) PATCHES[0][1], (Integer) PATCHES[0][2], report);
				LandscapeModel.Tatra n = nearest(model(s.overworld()));
				if (n == null) {
					failures.add("no Tatra massif within " + BOX + " m of the origin");
				} else {
					summit(s.overworld(), n, report, failures);
				}
				return n;
			});
			context.waitTicks(SETTLE_TICKS);
			if (t != null) {
				for (double f : new double[] {-0.5, 0, 0.5}) {
					double v = f * t.halfLength();
					sp.getServer().runOnServer(s -> transect(s.overworld(), t, String.format(Locale.ROOT,
							"across at %.2f of the half length", f), -6_000, v, t.reach() + 2_000, v, census, surface, report,
							failures));
					context.waitTicks(SETTLE_TICKS);
				}
				sp.getServer().runOnServer(s -> transect(s.overworld(), t, "along the main ridge", 0, -t.halfLength() - 2_000, 0,
						t.halfLength() + 2_000, census, surface, report, failures));
				context.waitTicks(SETTLE_TICKS);
				// Step TT2: the tarns, one per server task with ticks between them (their chunks unload in between).
				List<LandscapeModel.TatraTarn> tarns = sp.getServer().computeOnServer(s -> tarnsOf(model(s.overworld()), t));
				report.add(String.format(Locale.ROOT, "tarns of the massif: %d (%d with an outlet brook, %d behind a moraine)",
						tarns.size(), tarns.stream().filter(LandscapeModel.TatraTarn::outlet).count(),
						tarns.stream().filter(LandscapeModel.TatraTarn::moraine).count()));
				if (tarns.size() < 15) {
					failures.add("tarns: " + tarns.size() + " in the nearest massif");
				}
				for (int n = 0; n < Math.min(TARNS, tarns.size()); n++) {
					LandscapeModel.TatraTarn tn = tarns.get(n);
					sp.getServer().runOnServer(s -> tarn(s.overworld(), tn, report, failures));
					context.waitTicks(SETTLE_TICKS);
				}
				sp.getServer().runOnServer(s -> slopes(model(s.overworld()), t, report));
			}
			census.forEach((part, rocks) -> report.add("rock blocks of " + part + ": " + rocks));
			surface.forEach((rock, tops) -> report.add("top blocks on " + rock + ": " + tops));
			// Step TT2: the snow patch keeps its snow through the summer (Serene Seasons melts only snow layers and ice).
			double[] snow = sp.getServer().computeOnServer(s -> PolishForestsCommands.locate(
					HabitatTargets.World.of(s.overworld()), PolishForestsCommands.Target.SNOW_PATCH, 0, 0));
			if (snow == null) {
				failures.add("find snow_patch: none");
			} else {
				String before = sp.getServer().computeOnServer(s -> topId(s.overworld(), snow));
				sp.getServer().runCommand("season set mid_summer");
				sp.getServer().runCommand("gamerule random_tick_speed 300");
				context.waitTicks(200);
				String after = sp.getServer().computeOnServer(s -> topId(s.overworld(), snow));
				sp.getServer().runCommand("gamerule random_tick_speed 3");
				report.add(String.format(Locale.ROOT, "snow patch at (%.0f, %.0f): top %s, after mid summer %s", snow[0], snow[1],
						before, after));
				if (!"minecraft:snow_block".equals(before) || !before.equals(after)) {
					failures.add("snow patch: " + before + " -> " + after);
				}
			}
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
		// The snow patch is a habitat zone: the targets are searched with the world's classifier, as the command does.
		HabitatTargets.World world = HabitatTargets.World.of(server.overworld());
		for (PolishForestsCommands.Target t : new PolishForestsCommands.Target[] {PolishForestsCommands.Target.TATRA,
				PolishForestsCommands.Target.HIGH_TATRA, PolishForestsCommands.Target.WESTERN_TATRA,
				PolishForestsCommands.Target.PODHALE, PolishForestsCommands.Target.CIRQUE, PolishForestsCommands.Target.U_VALLEY,
				PolishForestsCommands.Target.TARN, PolishForestsCommands.Target.PINNACLE, PolishForestsCommands.Target.GULLY,
				PolishForestsCommands.Target.SCREE, PolishForestsCommands.Target.MORAINE, PolishForestsCommands.Target.SNOW_PATCH}) {
			long start = System.nanoTime();
			double[] p = PolishForestsCommands.locate(world, t, 0, 0);
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
	private static void patch(ServerLevel level, String name, int x, int z, Map<TatraPart, Map<String, Integer>> census,
			List<String> report, List<String> failures) {
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
			String rock = rock(chunk, wx, top, wz, level.getMinY());
			Material want = Material.tatraRock(s.terrain().rock());
			if (want != null && !want.id().equals(rock)) {
				rockMissing++;
			}
			if (want == null && rock != null) {
				rockStray++;
			}
			if (rock != null) {
				census.computeIfAbsent(s.terrain().tatraPart(), k -> new TreeMap<>()).merge(rock, 1, Integer::sum);
			}
		}
		report.add(String.format(Locale.ROOT, "patch %s at (%d, %d): chunk %.0f ms; parts %s; top blocks %s; top off the model %d, "
				+ "bedrock without its rock block %d, rock block without a bedrock %d, highest block Y %d", name, x, z, ms, parts, tops,
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

	/** The Tatra massif nearest to the origin, or null. */
	private static LandscapeModel.Tatra nearest(LandscapeModel m) {
		return m.tatras(-BOX, -BOX, BOX, BOX).stream().min(Comparator.comparingDouble(t -> Math.hypot(t.x(), t.z())))
				.orElse(null);
	}

	/** World x of the local point of a massif: u across the range (towards the Podhale), v along it. */
	private static double wx(LandscapeModel.Tatra t, double u, double v) {
		return t.x() + u * t.cos() - v * t.sin();
	}

	private static double wz(LandscapeModel.Tatra t, double u, double v) {
		return t.z() + u * t.sin() + v * t.cos();
	}

	/** The summit of the massif: the model on a 40 m grid, refined on a 4 m one, against its generated chunk. */
	private static void summit(ServerLevel level, LandscapeModel.Tatra t, List<String> report, List<String> failures) {
		LandscapeModel m = model(level);
		VerticalScale vs = ((PolandChunkGenerator) level.getChunkSource().getGenerator()).vertical();
		double bx = t.x();
		double bz = t.z();
		double best = Double.NEGATIVE_INFINITY;
		for (double v = -t.halfLength(); v <= t.halfLength(); v += 40) {
			for (double u = -4_000; u <= 4_000; u += 40) {
				double h = m.sample(wx(t, u, v), wz(t, u, v)).surface();
				if (h > best) {
					best = h;
					bx = wx(t, u, v);
					bz = wz(t, u, v);
				}
			}
		}
		double cx = bx;
		double cz = bz;
		for (int dx = -60; dx <= 60; dx += 4) {
			for (int dz = -60; dz <= 60; dz += 4) {
				double h = m.sample(Math.floor(cx) + dx, Math.floor(cz) + dz).surface();
				if (h > best) {
					best = h;
					bx = Math.floor(cx) + dx;
					bz = Math.floor(cz) + dz;
				}
			}
		}
		int x = (int) Math.floor(bx);
		int z = (int) Math.floor(bz);
		ColumnSample s = m.sample(x, z);
		LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
		int highest = Integer.MIN_VALUE;
		for (int i = 0; i < 256; i++) {
			highest = Math.max(highest, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i >> 4, i & 15));
		}
		int gen = groundTop(chunk, x, z, level.getMinY());
		int want = vs.topBlockY(s.surface());
		report.add(String.format(Locale.ROOT, "summit of the massif (%.0f, %.0f), %.0f m from the origin: (%d, %d), %.1f m a.s.l., "
				+ "%s, rock %s, model top Y %d, generated top ground Y %d, highest block of the chunk Y %d", t.x(), t.z(),
				Math.hypot(t.x(), t.z()), x, z, s.surface(), s.terrain().tatraPart(), s.terrain().rock(), want, gen, highest));
		if (s.surface() < 2_450 || s.surface() > 2_530) {
			failures.add("summit " + s.surface() + " m out of 2450-2530 m");
		}
		if (gen != want) {
			failures.add("summit: generated top ground Y " + gen + " instead of " + want);
		}
		if (highest > MAX_Y) {
			failures.add("summit chunk: a block at Y " + highest);
		}
	}

	/**
	 * A transect of the massif from (u0, v0) to (u1, v1) in its local frame, every 250 m: the model against the generated
	 * top ground block, the crest, the basin floor and the slopes between the points (blocks per block).
	 */
	private static void transect(ServerLevel level, LandscapeModel.Tatra t, String name, double u0, double v0, double u1,
			double v1, Map<TatraPart, Map<String, Integer>> census, Map<String, Map<String, Integer>> surface, List<String> report,
			List<String> failures) {
		LandscapeModel m = model(level);
		VerticalScale vs = ((PolandChunkGenerator) level.getChunkSource().getGenerator()).vertical();
		double length = Math.hypot(u1 - u0, v1 - v0);
		int steps = (int) Math.floor(length / TRANSECT_STEP);
		StringBuilder profile = new StringBuilder();
		int top = Integer.MIN_VALUE;
		int prev = Integer.MIN_VALUE;
		double maxSlope = 0;
		double sumSlope = 0;
		int dry = 0;
		int off = 0;
		int cut = 0;
		double crest = Double.NEGATIVE_INFINITY;
		double basin = Double.POSITIVE_INFINITY;
		Map<TatraPart, Integer> parts = new TreeMap<>();
		TatraPart last = null;
		for (int i = 0; i <= steps; i++) {
			double d = i * TRANSECT_STEP;
			double u = u0 + (u1 - u0) * d / length;
			double v = v0 + (v1 - v0) * d / length;
			int x = (int) Math.floor(wx(t, u, v));
			int z = (int) Math.floor(wz(t, u, v));
			ColumnSample s = m.sample(x, z);
			TatraPart part = s.terrain().tatraPart();
			parts.merge(part, 1, Integer::sum);
			if (part == TatraPart.HIGH_TATRA || part == TatraPart.WESTERN_TATRA) {
				crest = Math.max(crest, s.surface());
			}
			if (part == TatraPart.PODHALE_BASIN && !s.hasWater()) {
				basin = Math.min(basin, s.surface());
			}
			LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
			int gen = groundTop(chunk, x, z, level.getMinY());
			int want = vs.topBlockY(s.surface());
			top = Math.max(top, gen);
			if (prev != Integer.MIN_VALUE) {
				double slope = Math.abs(gen - prev) / TRANSECT_STEP;
				maxSlope = Math.max(maxSlope, slope);
				sumSlope += slope;
			}
			prev = gen;
			if (!s.hasWater() && part != TatraPart.NONE) {
				String bedrock = s.terrain().rock() == null ? "flysch" : s.terrain().rock().name().toLowerCase(Locale.ROOT);
				surface.computeIfAbsent(bedrock, k -> new TreeMap<>()).merge(id(chunk.getBlockState(new BlockPos(x, gen, z))), 1,
						Integer::sum);
			}
			boolean wrong = false;
			if (!s.hasWater()) {
				dry++;
				if (gen < want && chunk.getBlockState(new BlockPos(x, want, z)).isAir()) {
					// Air at the model top and the ground lower: a cave opening (J1) or a carver.
					cut++;
					wrong = true;
				} else if (gen != want) {
					off++;
					wrong = true;
				}
			}
			String rock = rock(chunk, x, want, z, level.getMinY());
			if (rock != null) {
				census.computeIfAbsent(part, k -> new TreeMap<>()).merge(rock, 1, Integer::sum);
			}
			if (part != last) {
				profile.append(" | ").append(part);
				last = part;
			}
			profile.append(String.format(Locale.ROOT, " %.0f:%.0f/%d", d, s.surface(), gen));
			if (wrong) {
				// An off point: the model top, the block there and the block above it.
				BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
				profile.append(String.format(Locale.ROOT, "!%d(%s,%s;%d,%d)", want, id(chunk.getBlockState(at.set(x, want, z))),
						id(chunk.getBlockState(at.set(x, want + 1, z))), x, z));
			}
		}
		report.add(String.format(Locale.ROOT, "transect %s (u %.0f..%.0f m, v %.0f..%.0f m): %d dry points, %d off the model, "
				+ "%d cut lower with air at the model top (caves); highest top Y %d; crest %.0f m, basin floor %.0f m, drop %.0f m; slope between the points (blocks per block) "
				+ "largest %.3f, mean %.3f; points by part %s", name, u0, u1, v0, v1, dry, off, cut, top, crest, basin, crest - basin,
				maxSlope, steps > 0 ? sumSlope / steps : 0, parts));
		report.add("  profile (distance m: model m / generated top Y):" + profile);
		if (off > 0.03 * dry) {
			failures.add("transect " + name + ": " + off + " of " + dry + " dry points off the model");
		}
		if (cut > 0.15 * dry) {
			failures.add("transect " + name + ": " + cut + " of " + dry + " dry points cut below the model");
		}
		if (top > MAX_Y) {
			failures.add("transect " + name + ": a top at Y " + top);
		}
	}

	/** Step TT2: the tarns of the massif, the largest first. */
	private static List<LandscapeModel.TatraTarn> tarnsOf(LandscapeModel m, LandscapeModel.Tatra t) {
		List<LandscapeModel.TatraTarn> out = new ArrayList<>(m.tatraTarns(t.x() - 1, t.z() - 1, t.x() + 1, t.z() + 1));
		out.sort(Comparator.comparingDouble(tn -> -tn.across() * tn.along()));
		return out;
	}

	/**
	 * Step TT2: the generated water of a tarn: every column the model gives to the tarn (a box around its basin), the top
	 * water block of each with its four neighbors on the same layer (water or a solid block, else a leak) and the block
	 * under its lowest water block (not air, else hanging water).
	 */
	private static void tarn(ServerLevel level, LandscapeModel.TatraTarn tn, List<String> report, List<String> failures) {
		LandscapeModel m = model(level);
		VerticalScale vs = ((PolandChunkGenerator) level.getChunkSource().getGenerator()).vertical();
		double reach = Math.max(tn.across(), tn.along()) * 1.2 + 8;
		int x0 = (int) Math.floor(tn.x() - reach);
		int z0 = (int) Math.floor(tn.z() - reach);
		int n = (int) Math.ceil(2 * reach);
		int water = 0;
		int dry = 0;
		int leaks = 0;
		int hanging = 0;
		List<String> examples = new ArrayList<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int a = 0; a < n; a++) {
			for (int b = 0; b < n; b++) {
				int x = x0 + a;
				int z = z0 + b;
				ColumnSample s = m.sample(x, z);
				if (s.waterKind() != pl.polishforests.worldgen.landscape.WaterKind.TARN) {
					continue;
				}
				int y = vs.topBlockY(s.waterLevel());
				LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
				if (!chunk.getFluidState(at.set(x, y, z)).is(net.minecraft.tags.FluidTags.WATER)) {
					dry++;
					continue;
				}
				water++;
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					BlockState o = level.getBlockState(at.set(x + d[0], y, z + d[1]));
					if (!o.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && !o.isSolid()) {
						leaks++;
						if (examples.size() < 4) {
							examples.add(String.format(Locale.ROOT, "(%d, %d, %d) %s", x + d[0], y, z + d[1], id(o)));
						}
					}
				}
				int yb = y;
				while (yb > level.getMinY() && chunk.getFluidState(at.set(x, yb - 1, z)).is(net.minecraft.tags.FluidTags.WATER)) {
					yb--;
				}
				if (chunk.getBlockState(at.set(x, yb - 1, z)).isAir()) {
					hanging++;
				}
			}
		}
		report.add(String.format(Locale.ROOT, "tarn %d at (%.0f, %.0f), %.0f × %.0f m, level %d m (Y %d)%s%s: %d water columns, "
				+ "%d model water columns without water, %d leaks %s, %d hanging", tn.id(), tn.x(), tn.z(), 2 * tn.across(),
				2 * tn.along(), tn.level(), vs.topBlockY(tn.level()), tn.morskie() ? ", Morskie Oko type" : tn.moraine() ? ", moraine"
						: ", cirque", tn.outlet() ? ", outlet brook" : ", no outlet", water, dry, leaks, examples, hanging));
		if (water == 0 || leaks > 0 || hanging > 0) {
			failures.add("tarn " + tn.id() + ": " + water + " water columns, " + leaks + " leaks, " + hanging + " hanging");
		}
	}

	/**
	 * Step TT2: the histogram of the slopes in blocks (degrees, central differences of the model tops over 1 m) on a
	 * {@value #SLOPE_GRID} m grid over the massif, by landform, to {@code build/tatra/slopes.txt}.
	 */
	private static void slopes(LandscapeModel m, LandscapeModel.Tatra t, List<String> report) {
		VerticalScale vs = VerticalScale.GAMEPLAY;
		String[] names = {"pinnacle", "gully", "scree", "moraine", "cirque", "other"};
		pl.polishforests.worldgen.landscape.Landform[] forms = {pl.polishforests.worldgen.landscape.Landform.PINNACLE,
				pl.polishforests.worldgen.landscape.Landform.GULLY, pl.polishforests.worldgen.landscape.Landform.SCREE,
				pl.polishforests.worldgen.landscape.Landform.MORAINE, pl.polishforests.worldgen.landscape.Landform.CIRQUE};
		int[][] hist = new int[names.length][10];
		for (double u = -0.35 * t.reach(); u <= 0.35 * t.reach(); u += SLOPE_GRID) {
			for (double v = -t.halfLength(); v <= t.halfLength(); v += SLOPE_GRID) {
				double x = wx(t, u, v);
				double z = wz(t, u, v);
				ColumnSample s = m.sample(x, z);
				if (s.terrain().tatraPart() == TatraPart.NONE || s.hasWater()) {
					continue;
				}
				int k = names.length - 1;
				for (int f = 0; f < forms.length; f++) {
					if (s.terrain().has(forms[f])) {
						k = f;
						break;
					}
				}
				double gx = vs.blocksForMeters(m.sample(x + 1, z).surface()) - vs.blocksForMeters(m.sample(x - 1, z).surface());
				double gz = vs.blocksForMeters(m.sample(x, z + 1).surface()) - vs.blocksForMeters(m.sample(x, z - 1).surface());
				double deg = Math.toDegrees(Math.atan(Math.hypot(gx, gz) / 2));
				hist[k][Math.min(9, (int) (deg / 10))]++;
			}
		}
		List<String> lines = new ArrayList<>();
		lines.add("slopes in blocks (degrees, bins of 10) by landform, " + SLOPE_GRID + " m grid over the massif");
		for (int k = 0; k < names.length; k++) {
			lines.add(names[k] + ": " + java.util.Arrays.toString(hist[k]));
		}
		report.addAll(lines);
		try {
			String dir = System.getProperty("polishforests.tatra.out", "build/tatra");
			Files.createDirectories(Path.of(dir));
			Files.write(Path.of(dir, "slopes.txt"), lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** Step TT2: the id of the top ground block (snow included) at a point. */
	private static String topId(ServerLevel level, double[] p) {
		int x = (int) Math.floor(p[0]);
		int z = (int) Math.floor(p[1]);
		LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
		int y = groundTop(chunk, x, z, level.getMinY());
		return id(chunk.getBlockState(new BlockPos(x, y, z)));
	}

	/** The top ground block of a column below the world surface (trees, plants and snow above skipped), or the min Y. */
	private static int groundTop(LevelChunk chunk, int x, int z, int minY) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
		while (y > minY && !ground(chunk.getBlockState(at.set(x, y, z)))) {
			y--;
		}
		return y;
	}

	/** The first Tatra rock block (a block of the mod) at most {@link #ROCK_DEPTH} below {@code top}, or null. */
	private static String rock(LevelChunk chunk, int x, int top, int z, int minY) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int y = top; y > top - ROCK_DEPTH && y > minY; y--) {
			String b = id(chunk.getBlockState(at.set(x, y, z)));
			if (TATRA_ROCKS.contains(b)) {
				return b;
			}
		}
		return null;
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

	/**
	 * A ground block: stone and the Tatra rocks (the overworld stone tag), the overworld substrate (dirt, grass blocks and
	 * podzols, mud and moss blocks: in 26.3 the dirt tag holds only dirt, coarse and rooted dirt), sand and gravel.
	 */
	private static boolean ground(BlockState s) {
		// Step TT2: the scree of the mod and the snow block of the snow patches.
		return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SUBSTRATE_OVERWORLD) || s.is(BlockTags.SAND)
				|| s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || TATRA_SCREE.contains(id(s));
	}

	private static String id(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
	}
}
