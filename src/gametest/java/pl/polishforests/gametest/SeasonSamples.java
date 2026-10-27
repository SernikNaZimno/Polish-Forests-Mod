package pl.polishforests.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.block.BerryShrubBlock;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.block.MarcescentLeavesBlock;
import pl.polishforests.block.SeasonalPlantBlock;
import pl.polishforests.block.SpeciesLeavesBlock;
import pl.polishforests.client.ClientGroundPlants;
import pl.polishforests.client.ClientLeafFall;
import pl.polishforests.client.ClientSeasonModels;
import pl.polishforests.client.ClientSeasonPhase;
import pl.polishforests.season.BerrySeason;
import pl.polishforests.season.GroundPhases;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;

/**
 * The seasonal look of the vertical slice in the "Polska" world (step V13 of docs/04-pionowy-wycinek.md,
 * {@code -Pgametest=slice -Psites=seasons}, scale {@code -Pscales=realistic|gameplay}, default realistic, natural mode):
 * <ul>
 * <li><b>Shots</b> of the oak-hornbeam forest (eye, under the canopy, 30 m), the fresh pine forest (eye) and the mixed
 * forest of the transect (eye) in 8 sub-seasons with Serene Seasons (EARLY, MID and LATE_SPRING, EARLY and MID_SUMMER,
 * MID and LATE_AUTUMN, MID_WINTER; SS sets the first day) and in 2 without it (MID_SPRING and MID_AUTUMN, the 5th day,
 * the 2nd color step);
 * <li><b>Herb layer</b> (PW-8 (a)): around the oak-hornbeam camera the share of wood anemones the client draws with
 * flowers, leaves only or not at all ({@link GroundPhases#look}) must follow the plan (flowering in MID_SPRING, none in
 * MID_SUMMER), and a withdrawn anemone has no outline on the client and the server (R12);
 * <li><b>Bare twigs</b> (T-D5): in MID_WINTER at least 80% of the non-marcescent broadleaf leaves around the camera show
 * twigs, none in MID_SUMMER; the marcescent leaves never;
 * <li><b>Falling leaves</b> (the user's request of 2026-10-26): the expected particles of our leaves per asked animation
 * tick ({@link ClientLeafFall#counters}) over 10 s under the oak-hornbeam canopy: in MID_SUMMER at most the rare needles,
 * in MID_AUTUMN and LATE_AUTUMN more than vanilla's 0.01; a 10 s shot of the falling leaves in LATE_AUTUMN;
 * <li><b>Bilberry fruit</b> (PW-8, R13) in the fresh pine forest: with the random tick speed raised, the fruit ripens on
 * the fertile plants in MID_SUMMER only and falls in EARLY_AUTUMN; the catch-up: after two sub-seasons away (the chunks
 * unloaded) the chunks of the site are brought up to date within 20 ticks of being loaded (MID_SPRING → MID_SUMMER:
 * fruit; MID_SUMMER → EARLY_AUTUMN: none).
 * </ul>
 * Results in {@code build/seasons/<renderer>_<ss|noss>.txt}, screenshots {@code seasons_<scale>_<renderer>_<ss|noss>_<view>}.
 */
final class SeasonSamples {
	private static final int[] SIZE = {1280, 720};
	private static final int RADIUS = 24;
	/** Sub-seasons of the shots with Serene Seasons (day 0) and without it (day 4, the 2nd color step). */
	private static final List<SubSeason> WITH_SS = List.of(SubSeason.EARLY_SPRING, SubSeason.MID_SPRING, SubSeason.LATE_SPRING,
			SubSeason.EARLY_SUMMER, SubSeason.MID_SUMMER, SubSeason.MID_AUTUMN, SubSeason.LATE_AUTUMN, SubSeason.MID_WINTER);
	private static final List<SubSeason> WITHOUT_SS = List.of(SubSeason.MID_SPRING, SubSeason.MID_AUTUMN);

	private final List<String> results = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	private SeasonSamples() {
	}

	static void run(ClientGameTestContext context) {
		new SeasonSamples().all(context);
	}

	private void all(ClientGameTestContext context) {
		boolean seasons = SeasonDates.serene();
		boolean sodium = FabricLoader.getInstance().isModLoaded("sodium");
		boolean nvidium = context.computeOnClient(mc -> RenderProbesClientGameTest.nvidiumEnabled());
		String tag = (!sodium ? "vanilla" : nvidium ? "sodium_nvidium" : "sodium") + (seasons ? "_ss" : "_noss");
		boolean real = !System.getProperty("polishforests.scales", "").equals("gameplay");
		String scale = real ? "realistic" : "gameplay";
		context.runOnClient(mc -> mc.options.renderDistance().set(10));
		ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id(real ? "poland" : "poland_gameplay"));
		Map<String, List<SliceCameras.Camera>> cameras = SliceCameras.read();
		SliceCameras.Camera grad = camera(cameras, scale, "oak_hornbeam_1", "eye");
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> SliceClientGameTest.select(ui, preset, false))
				.create()) {
			for (String c : new String[] {"gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
					"gamerule send_command_feedback false", "weather clear", "difficulty peaceful", "gamemode spectator @a"}) {
				sp.getServer().runCommand(c);
			}
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			result("%s %s: calendar %s", tag, scale, Seasons.provider().name());
			String p = "seasons_" + scale + "_" + tag + "_";
			for (SubSeason sub : seasons ? WITH_SS : WITHOUT_SS) {
				int day = seasons ? 0 : 4;
				go(context, sp, grad, 25);
				SeasonDates.setAndWait(context, sp, sub, day, "seasons");
				String name = sub.name().toLowerCase(Locale.ROOT);
				for (String[] view : new String[][] {{"oak_hornbeam_1", "eye"}, {"oak_hornbeam_1", "canopy"}, {"oak_hornbeam_1", "30m"},
						{"fresh_pine_1", "eye"}, {"transect", "eye_mixed_forest"}}) {
					SliceCameras.Camera cam = camera(cameras, scale, view[0], view[1]);
					if (cam != null) {
						shot(context, sp, p + name + "_" + view[0] + "_" + view[1], cam, 4);
					}
				}
				go(context, sp, grad, 3);
				rawColors(context, grad, sub);
				herbs(context, sp, grad, sub);
				twigs(context, grad, sub);
				if (sub == SubSeason.MID_SUMMER || sub == SubSeason.MID_AUTUMN || sub == SubSeason.LATE_AUTUMN) {
					leafFall(context, sp, grad, sub, sub == SubSeason.LATE_AUTUMN ? p + "late_autumn_falling_leaves" : null);
				}
			}
			SliceCameras.Camera pine = camera(cameras, scale, "fresh_pine_1", "eye");
			if (pine != null) {
				berries(context, sp, pine);
			} else {
				fail("no camera fresh_pine_1 eye in %s", scale);
			}
		}
		write(tag);
		if (!failures.isEmpty()) {
			throw new AssertionError("[seasons] " + failures.size() + " failures:\n" + String.join("\n", failures));
		}
	}

	// ------------------------------------------------------------------ herb layer and twigs

	private Integer rawGrass;
	private Integer rawFoliage;

	/**
	 * The raw grass and foliage colors of the own resolvers at the camera must not change with the season (no double
	 * seasoning by Serene Seasons: the curves of the leaves, the bilberry and the bracken are drawn over them).
	 */
	private void rawColors(ClientGameTestContext context, SliceCameras.Camera cam, SubSeason sub) {
		int[] c = context.computeOnClient(mc -> {
			BlockPos at = new BlockPos(cam.x(), cam.y(), cam.z());
			return new int[] {mc.level.getBlockTint(at, ClientGroundPlants.RAW_GRASS), mc.level.getBlockTint(at, ClientSeasonPhase.RAW_FOLIAGE)};
		});
		result("%s raw grass %06x, raw foliage %06x", sub, c[0] & 0xFFFFFF, c[1] & 0xFFFFFF);
		if (rawGrass == null) {
			rawGrass = c[0];
			rawFoliage = c[1];
		} else if (rawGrass != c[0] || rawFoliage != c[1]) {
			fail("%s: the raw grass or foliage color changed with the season (%06x -> %06x, %06x -> %06x)", sub, rawGrass & 0xFFFFFF,
					c[0] & 0xFFFFFF, rawFoliage & 0xFFFFFF, c[1] & 0xFFFFFF);
		}
	}

	/** The looks of the wood anemones around a camera on the client, and the outline of a withdrawn one on both sides. */
	private void herbs(ClientGameTestContext context, TestSingleplayerContext sp, SliceCameras.Camera cam, SubSeason sub) {
		int[] counts = context.computeOnClient(mc -> {
			int[] c = new int[GroundPhases.Look.values().length + 1];
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			BlockPos withdrawn = null;
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					for (int dy = -8; dy <= 4; dy++) {
						at.set(cam.x() + dx, cam.y() + dy, cam.z() + dz);
						BlockState s = mc.level.getBlockState(at);
						if (s.getBlock() instanceof SeasonalPlantBlock bed && bed.plant() == GroundPlant.WOOD_ANEMONE) {
							GroundPhases.Look l = GroundPhases.look(GroundPlant.WOOD_ANEMONE, ClientSeasonPhase.step(), at.getX(), at.getZ());
							c[l.ordinal()]++;
							if (l == GroundPhases.Look.NONE && withdrawn == null) {
								withdrawn = at.immutable();
								c[c.length - 1] = s.getShape(mc.level, at).isEmpty() ? 1 : -1;
							}
						}
					}
				}
			}
			return c;
		});
		int total = 0;
		for (int k = 0; k < GroundPhases.Look.values().length; k++) {
			total += counts[k];
		}
		double full = total == 0 ? 0 : counts[GroundPhases.Look.FULL.ordinal()] / (double) total;
		double none = total == 0 ? 0 : counts[GroundPhases.Look.NONE.ordinal()] / (double) total;
		result("%s anemones around the camera: %d, flowering %.0f%%, leaves %.0f%%, withdrawn %.0f%%; outline of a withdrawn one on the "
				+ "client: %s", sub, total, 100 * full, total == 0 ? 0.0 : 100.0 * counts[GroundPhases.Look.LEAVES.ordinal()] / total,
				100 * none, counts[counts.length - 1] == 0 ? "-" : counts[counts.length - 1] > 0 ? "empty" : "NOT EMPTY");
		if (total < 20) {
			fail("%s: only %d wood anemones around the oak-hornbeam camera", sub, total);
			return;
		}
		if (sub == SubSeason.MID_SPRING && full < 0.9 || sub == SubSeason.MID_SUMMER && none < 0.999) {
			fail("%s: wood anemones flowering %.0f%%, withdrawn %.0f%%", sub, 100 * full, 100 * none);
		}
		if (counts[counts.length - 1] < 0) {
			fail("%s: a withdrawn wood anemone has an outline on the client", sub);
		}
		if (sub == SubSeason.MID_SUMMER) {
			boolean serverEmpty = sp.getServer().computeOnServer(s -> {
				ServerLevel level = s.overworld();
				BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
				for (int dx = -RADIUS; dx <= RADIUS; dx++) {
					for (int dz = -RADIUS; dz <= RADIUS; dz++) {
						for (int dy = -8; dy <= 4; dy++) {
							at.set(cam.x() + dx, cam.y() + dy, cam.z() + dz);
							BlockState st = level.getBlockState(at);
							if (st.getBlock() instanceof SeasonalPlantBlock bed && bed.plant() == GroundPlant.WOOD_ANEMONE) {
								return st.getShape(level, at).isEmpty();
							}
						}
					}
				}
				return true;
			});
			result("%s: outline of a withdrawn wood anemone on the server empty: %s", sub, serverEmpty);
			if (!serverEmpty) {
				fail("%s: a withdrawn wood anemone has an outline on the server", sub);
			}
		}
	}

	/** The share of the broadleaf leaves around a camera that show their bare twigs (marcescent leaves counted apart). */
	private void twigs(ClientGameTestContext context, SliceCameras.Camera cam, SubSeason sub) {
		int[] c = context.computeOnClient(mc -> {
			int[] n = new int[4];
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					for (int dy = 0; dy <= 32; dy++) {
						at.set(cam.x() + dx, cam.y() + dy, cam.z() + dz);
						BlockState s = mc.level.getBlockState(at);
						if (s.getBlock() instanceof SpeciesLeavesBlock leaves && !leaves.foliage().conifer()) {
							boolean dry = MarcescentLeavesBlock.marcescent(s);
							boolean bare = ClientSeasonModels.leafless(s);
							n[dry ? 2 : 0]++;
							n[dry ? 3 : 1] += bare ? 1 : 0;
						}
					}
				}
			}
			return n;
		});
		double share = c[0] == 0 ? 0 : c[1] / (double) c[0];
		result("%s broadleaf leaves around the camera: %d, bare twigs %.0f%%; marcescent %d, bare %d", sub, c[0], 100 * share, c[2], c[3]);
		if (c[3] > 0) {
			fail("%s: %d marcescent leaves show twigs", sub, c[3]);
		}
		if (sub == SubSeason.MID_WINTER && share < 0.8 || sub == SubSeason.MID_SUMMER && c[1] > 0) {
			fail("%s: %.0f%% of the broadleaf leaves show twigs", sub, 100 * share);
		}
	}

	/** The expected falling leaves per asked animation tick of our leaves over 10 s at a camera; an optional shot. */
	private void leafFall(ClientGameTestContext context, TestSingleplayerContext sp, SliceCameras.Camera cam, SubSeason sub,
			String shotName) {
		double[] before = context.computeOnClient(mc -> ClientLeafFall.counters());
		context.waitTicks(200);
		double[] after = context.computeOnClient(mc -> ClientLeafFall.counters());
		double asked = after[0] - before[0];
		double rate = asked == 0 ? 0 : (after[1] - before[1]) / asked;
		double vanillaAsked = after[2] - before[2];
		double vanillaRate = vanillaAsked == 0 ? 0 : (after[3] - before[3]) / vanillaAsked;
		result("%s falling leaves over 10 s: our leaves asked %.0f times, mean chance %.4f; vanilla leaves in Polska asked %.0f times, "
				+ "mean chance %.4f (vanilla 0.01)", sub, asked, rate, vanillaAsked, vanillaRate);
		if (asked < 100) {
			fail("%s: our leaves were asked only %.0f times for falling leaves (mixin not applied?)", sub, asked);
		} else if (sub == SubSeason.MID_SUMMER && rate > 0.002 || sub != SubSeason.MID_SUMMER && rate <= 0.01) {
			fail("%s: mean chance of a falling leaf %.4f", sub, rate);
		}
		if (shotName != null) {
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", cam.x() + 0.5, (double) cam.y(),
					cam.z() + 0.5, cam.yaw(), -15.0));
			context.waitTicks(200);
			context.takeScreenshot(TestScreenshotOptions.of(shotName).disableCounterPrefix().withSize(SIZE[0], SIZE[1]));
		}
	}

	// ------------------------------------------------------------------ bilberry fruit

	private void berries(ClientGameTestContext context, TestSingleplayerContext sp, SliceCameras.Camera cam) {
		ChunkPos center = new ChunkPos(cam.x() >> 4, cam.z() >> 4);
		// Random tick: fruit in MID_SUMMER on fertile plants only, none in EARLY_AUTUMN.
		go(context, sp, cam, 20);
		SeasonDates.setAndWait(context, sp, SubSeason.MID_SUMMER, 4, "seasons");
		sp.getServer().runCommand("gamerule random_tick_speed 1000");
		context.waitTicks(200);
		int[] summer = sp.getServer().computeOnServer(s -> count(s.overworld(), center, 2));
		SeasonDates.set(sp, SubSeason.EARLY_AUTUMN, 4, "seasons");
		context.waitTicks(200);
		int[] autumn = sp.getServer().computeOnServer(s -> count(s.overworld(), center, 2));
		sp.getServer().runCommand("gamerule random_tick_speed 3");
		result("random tick: MID_SUMMER %d bilberries, %d with fruit (%d fertile, %d infertile with fruit); EARLY_AUTUMN %d with fruit",
				summer[0], summer[1], summer[2], summer[3], autumn[1]);
		if (summer[0] < 50 || summer[1] < summer[2] * 0.6 || summer[3] > 0 || autumn[1] > summer[1] * 0.2) {
			fail("random tick of the bilberry fruit: %s / %s", java.util.Arrays.toString(summer), java.util.Arrays.toString(autumn));
		}
		// Catch-up after two sub-seasons away: MID_SPRING -> MID_SUMMER (fruit), MID_SUMMER -> EARLY_AUTUMN (none).
		catchUp(context, sp, cam, center, SubSeason.MID_SPRING, SubSeason.MID_SUMMER);
		catchUp(context, sp, cam, center, SubSeason.MID_SUMMER, SubSeason.EARLY_AUTUMN);
	}

	private void catchUp(ClientGameTestContext context, TestSingleplayerContext sp, SliceCameras.Camera cam, ChunkPos center,
			SubSeason from, SubSeason to) {
		go(context, sp, cam, 10);
		SeasonDates.set(sp, from, 4, "seasons");
		context.waitTicks(60);
		// Away: far enough for the site chunks to unload.
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d 250 %d", cam.x() + 4000, cam.z()));
		int waited = 0;
		while (waited < 20 * 90 && sp.getServer().computeOnServer(s -> s.overworld().getChunkSource().getChunkNow(center.x(),
				center.z()) != null)) {
			context.waitTicks(20);
			waited += 20;
		}
		boolean unloaded = sp.getServer().computeOnServer(s -> s.overworld().getChunkSource().getChunkNow(center.x(), center.z()) == null);
		SeasonDates.set(sp, to, 4, "seasons");
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f", cam.x() + 0.5, cam.y(), cam.z() + 0.5));
		int loadedAt = -1;
		int stampedAt = -1;
		for (int t = 0; t < 20 * 30 && stampedAt < 0; t++) {
			context.waitTicks(1);
			int now = t;
			int[] state = sp.getServer().computeOnServer(s -> {
				LevelChunk chunk = s.overworld().getChunkSource().getChunkNow(center.x(), center.z());
				if (chunk == null) {
					return new int[] {0, 0};
				}
				Integer stamp = chunk.getAttached(BerrySeason.STAMP);
				return new int[] {1, stamp != null && stamp == Seasons.provider().subSeason(s.overworld()).ordinal() ? 1 : 0};
			});
			if (state[0] == 1 && loadedAt < 0) {
				loadedAt = now;
			}
			if (state[1] == 1) {
				stampedAt = now;
			}
		}
		int[] c = sp.getServer().computeOnServer(s -> count(s.overworld(), center, 0));
		int delay = loadedAt < 0 || stampedAt < 0 ? -1 : stampedAt - loadedAt;
		result("catch-up %s -> %s: site chunk unloaded %s (after %d ticks), loaded after %d ticks, up to date %d ticks later; "
				+ "%d bilberries, %d with fruit (%d fertile)", from, to, unloaded, waited, loadedAt, delay, c[0], c[1], c[2]);
		boolean fruit = GroundPhases.fruiting(to);
		if (!unloaded || delay < 0 || delay > 20 || c[0] == 0 || (fruit ? c[1] < c[2] * 0.6 : c[1] > 0)) {
			fail("catch-up %s -> %s: unloaded %s, delay %d ticks, %d bilberries, %d with fruit, %d fertile", from, to, unloaded, delay, c[0],
					c[1], c[2]);
		}
	}

	/** {bilberries, with fruit, fertile, infertile with fruit} in the loaded chunks within {@code r} chunks of a chunk. */
	private static int[] count(ServerLevel level, ChunkPos center, int r) {
		int[] n = new int[4];
		for (int cx = center.x() - r; cx <= center.x() + r; cx++) {
			for (int cz = center.z() - r; cz <= center.z() + r; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				for (int s = 0; s < sections.length; s++) {
					if (sections[s].hasOnlyAir() || !sections[s].maybeHas(st -> st.getBlock() instanceof BerryShrubBlock)) {
						continue;
					}
					int y0 = chunk.getSectionYFromSectionIndex(s) << 4;
					for (int y = 0; y < 16; y++) {
						for (int z = 0; z < 16; z++) {
							for (int x = 0; x < 16; x++) {
								BlockState st = sections[s].getBlockState(x, y, z);
								if (st.getBlock() instanceof BerryShrubBlock) {
									boolean fertile = GroundPhases.fertile((cx << 4) + x, y0 + y, (cz << 4) + z);
									boolean fruit = st.getValue(BerryShrubBlock.BERRIES);
									n[0]++;
									n[1] += fruit ? 1 : 0;
									n[2] += fertile ? 1 : 0;
									n[3] += fruit && !fertile ? 1 : 0;
								}
							}
						}
					}
				}
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ helpers

	private static SliceCameras.Camera camera(Map<String, List<SliceCameras.Camera>> cameras, String scale, String place, String name) {
		for (SliceCameras.Camera c : cameras.getOrDefault(scale + " " + place, List.of())) {
			if (c.name().equals(name)) {
				return c;
			}
		}
		return null;
	}

	private static void go(ClientGameTestContext context, TestSingleplayerContext sp, SliceCameras.Camera cam, int waitSeconds) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f %.1f %.1f", cam.x() + 0.5, cam.y(), cam.z() + 0.5,
				cam.yaw(), cam.pitch()));
		context.waitTicks(20 * waitSeconds);
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, SliceCameras.Camera cam,
			int waitSeconds) {
		go(context, sp, cam, waitSeconds);
		context.waitFor(SeasonSamples::compiled, 1200);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(SIZE[0], SIZE[1]));
		PolishForests.LOG.info("[seasons] shot {}", name);
	}

	private static boolean compiled(Minecraft mc) {
		try {
			return mc.levelRenderer.hasRenderedAllSections();
		} catch (RuntimeException e) {
			return false;
		}
	}

	private void result(String format, Object... args) {
		String line = String.format(Locale.ROOT, format, args);
		results.add(line);
		PolishForests.LOG.info("[seasons] {}", line);
	}

	private void fail(String format, Object... args) {
		String line = String.format(Locale.ROOT, format, args);
		failures.add(line);
		results.add("FAIL " + line);
		PolishForests.LOG.error("[seasons] FAIL {}", line);
	}

	private void write(String tag) {
		String dir = System.getProperty("polishforests.seasons.out");
		if (dir == null || dir.isBlank()) {
			return;
		}
		try {
			Path file = Path.of(dir, tag + ".txt");
			Files.createDirectories(file.getParent());
			Files.write(file, results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError("Cannot write the seasons results", e);
		}
	}
}
