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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.block.BerryShrubBlock;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.block.MarcescentLeavesBlock;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.SeasonalPlantBlock;
import pl.polishforests.block.SpeciesLeavesBlock;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.client.ClientGroundPlants;
import pl.polishforests.client.ClientLeafFall;
import pl.polishforests.client.ClientSeasonModels;
import pl.polishforests.client.ClientSeasonPhase;
import pl.polishforests.season.BerrySeason;
import pl.polishforests.season.GroundPhases;
import pl.polishforests.season.SeasonPhase;
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
 * <li><b>Lily of the valley</b> (review round 1): the vanilla lilies around the camera show nothing in MID_WINTER, EARLY_SPRING
 * and LATE_AUTUMN and leaves only in MID_SUMMER, and a withdrawn one has no outline on the client and the server;
 * <li><b>Bare twigs</b> (T-D5): in MID_WINTER at least 80% of the non-marcescent broadleaf leaves around the camera show
 * twigs, none in MID_SUMMER; the marcescent leaves never; the canopy frame lets through at least
 * {@value #MIN_WINTER_CANOPY_SKY} of sky in MID_WINTER in REAL ({@value #MIN_WINTER_CANOPY_SKY_GAMEPLAY} in GAMEPLAY) and
 * {@value #MIN_WINTER_CANOPY_RATIO} times the summer share (review round 1: one screen of twigs per crown; step Z2: 15% in
 * REAL, the canopy camera on a free column);
 * <li><b>Fast mode</b> (review round 1) on a rig high over the oak-hornbeam forest in LATE_SPRING, LATE_AUTUMN and
 * MID_WINTER (without SS in both sub-seasons): no more sky in the Fast mode than in the Fancy mode where leaves in leaf
 * stand behind bare twigs, and vanilla leaves kept cut out in a transition step as in the Fancy mode; a Fast-mode frame
 * of the forest from 30 m;
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
	private static final int[] RIG_SIZE = {854, 480};
	private static final int RADIUS = 24;
	/** Sub-seasons of the shots with Serene Seasons (day 0) and without it (day 4, the 2nd color step). */
	private static final List<SubSeason> WITH_SS = List.of(SubSeason.EARLY_SPRING, SubSeason.MID_SPRING, SubSeason.LATE_SPRING,
			SubSeason.EARLY_SUMMER, SubSeason.MID_SUMMER, SubSeason.MID_AUTUMN, SubSeason.LATE_AUTUMN, SubSeason.MID_WINTER);
	private static final List<SubSeason> WITHOUT_SS = List.of(SubSeason.MID_SPRING, SubSeason.MID_AUTUMN);

	/**
	 * Sky through the bare canopy: the least share of sky in the canopy frame in MID_WINTER (review round 1 of V13: 2.5%;
	 * step Z2, CP-C.2: 15% in REAL, the target of that review, after lighter and fewer marcescent crowns) …
	 */
	private static final double MIN_WINTER_CANOPY_SKY = 0.15;
	/**
	 * … and in GAMEPLAY (stage 2 of Z2, to confirm at CP-D): 11%. The GAMEPLAY oak-hornbeam canopy is denser in the frame
	 * (15262 broadleaf leaves around the camera against 13468 in REAL): 12.3-13.1% at the canopy camera
	 * in two runs and 11.6-16.5% (mean 14.0%) at 8 other free columns within 10 blocks, against 17.6% at the REAL camera
	 * (15.7-21.4%, mean 18.8% at 8 other columns).
	 */
	private static final double MIN_WINTER_CANOPY_SKY_GAMEPLAY = 0.11;
	/** Step Z2 (DP-62): how far the canopy camera may move from the fixed one to a free column. */
	private static final int CANOPY_SHIFT = 4;
	/** Wood blocks in one column from 2 to 10 blocks above the camera's feet that make a trunk (step Z2). */
	private static final int TRUNK_RUN = 4;
	/** Stage 2 of Z2: no solid block but wood and leaves this many blocks around the canopy camera's column … */
	private static final int CLEAR_RADIUS = 2;
	/** … from 2 to this many blocks above its feet. */
	private static final int CLEAR_HEIGHT = 6;
	/** … and at least this many times the share in MID_SUMMER (V13 before the review: 1.3% against 0.5%). */
	private static final double MIN_WINTER_CANOPY_RATIO = 5.0;
	/** Fast-mode rig: largest excess of sky in the Fast mode over the Fancy mode (a hole in a crown, review round 1). */
	private static final double MAX_FAST_EXCESS = 0.05;

	private final List<String> results = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<SubSeason, Double> canopySky = new java.util.EnumMap<>(SubSeason.class);
	private boolean real = true;

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
		real = !System.getProperty("polishforests.scales", "").equals("gameplay");
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
			BlockPos rig = sp.getServer().computeOnServer(s -> buildRig(s.overworld(), grad));
			context.waitTicks(100);
			result("Fast-mode rig at %s", rig.toShortString());
			SliceCameras.Camera canopyCam = openCanopy(sp, camera(cameras, scale, "oak_hornbeam_1", "canopy"));
			String p = "seasons_" + scale + "_" + tag + "_";
			for (SubSeason sub : seasons ? WITH_SS : WITHOUT_SS) {
				int day = seasons ? 0 : 4;
				// Step T7: until the view is drawn instead of a fixed 25 s.
				teleport(sp, grad);
				ViewReady.await(context, sp, p + name(sub) + "_arrival", SIZE, 25, 0);
				SeasonDates.setAndWait(context, sp, sub, day, "seasons");
				String name = sub.name().toLowerCase(Locale.ROOT);
				for (String[] view : new String[][] {{"oak_hornbeam_1", "eye"}, {"oak_hornbeam_1", "canopy"}, {"oak_hornbeam_1", "30m"},
						{"fresh_pine_1", "eye"}, {"transect", "eye_mixed_forest"}}) {
					SliceCameras.Camera cam = view[1].equals("canopy") ? canopyCam : camera(cameras, scale, view[0], view[1]);
					if (cam != null) {
						Path path = shot(context, sp, p + name + "_" + view[0] + "_" + view[1], cam, 4);
						if (view[1].equals("canopy")) {
							canopySky.put(sub, skyShare(path));
						}
					}
				}
				go(context, sp, grad, 3);
				rawColors(context, grad, sub);
				herbs(context, sp, grad, sub);
				lilies(context, sp, grad, sub);
				twigs(context, grad, sub);
				if (!seasons || sub == SubSeason.LATE_SPRING || sub == SubSeason.LATE_AUTUMN || sub == SubSeason.MID_WINTER) {
					fastRig(context, sp, rig, sub, p + name + "_", scale);
				}
				if (sub == SubSeason.MID_SUMMER || sub == SubSeason.MID_AUTUMN || sub == SubSeason.LATE_AUTUMN) {
					leafFall(context, sp, grad, sub, sub == SubSeason.LATE_AUTUMN ? p + "late_autumn_falling_leaves" : null);
				}
			}
			canopy();
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
			int[] n = new int[6];
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					for (int dy = -8; dy <= 32; dy++) {
						at.set(cam.x() + dx, cam.y() + dy, cam.z() + dz);
						BlockState s = mc.level.getBlockState(at);
						if (s.getBlock() instanceof SpeciesLeavesBlock leaves && !leaves.foliage().conifer()) {
							boolean dry = MarcescentLeavesBlock.marcescent(s);
							boolean bare = ClientSeasonModels.leafless(s);
							n[dry ? 2 : 0]++;
							n[dry ? 3 : 1] += bare ? 1 : 0;
						} else if (s.is(Blocks.OAK_LEAVES) || s.is(Blocks.JUNGLE_LEAVES) || s.is(Blocks.DARK_OAK_LEAVES)
								|| s.is(Blocks.BIRCH_LEAVES)) {
							// V13-i: the vanilla leaves of the shrubs (hazel, rowan, alder buckthorn) in the Polska world.
							n[4]++;
							n[5] += ClientSeasonModels.vanillaLeafless(s, at) ? 1 : 0;
						}
					}
				}
			}
			return n;
		});
		double share = c[0] == 0 ? 0 : c[1] / (double) c[0];
		double vanilla = c[4] == 0 ? 0 : c[5] / (double) c[4];
		result("%s broadleaf leaves around the camera: %d, bare twigs %.0f%%; marcescent %d, bare %d; vanilla leaves (shrubs) %d, "
				+ "bare twigs %.0f%%", sub, c[0], 100 * share, c[2], c[3], c[4], 100 * vanilla);
		if (c[3] > 0) {
			fail("%s: %d marcescent leaves show twigs", sub, c[3]);
		}
		if (sub == SubSeason.MID_WINTER && share < 0.8 || sub == SubSeason.MID_SUMMER && c[1] > 0) {
			fail("%s: %.0f%% of the broadleaf leaves show twigs", sub, 100 * share);
		}
		if (c[4] >= 20 && (sub == SubSeason.MID_WINTER && vanilla < 0.8 || sub == SubSeason.MID_SUMMER && c[5] > 0)) {
			fail("%s: %.0f%% of the vanilla shrub leaves show twigs", sub, 100 * vanilla);
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
			// Step T7: the view drawn, but still at least the old 10 s, so that as many leaves fall in the frame as before.
			ViewReady.shot(context, sp, shotName, SIZE, 10, 10);
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

	/**
	 * The lilies of the valley around a camera (review round 1 of V13: the vanilla block in Polska follows its phases,
	 * {@link GroundPhases#lilyOfTheValley}): the looks on the client, nothing drawn from October to April, and the outline
	 * of a withdrawn one empty on the client and on the server.
	 */
	private void lilies(ClientGameTestContext context, TestSingleplayerContext sp, SliceCameras.Camera cam, SubSeason sub) {
		int[] counts = context.computeOnClient(mc -> {
			int[] c = new int[GroundPhases.Look.values().length + 1];
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					for (int dy = -8; dy <= 4; dy++) {
						at.set(cam.x() + dx, cam.y() + dy, cam.z() + dz);
						BlockState s = mc.level.getBlockState(at);
						if (s.is(Blocks.LILY_OF_THE_VALLEY)) {
							GroundPhases.Look l = GroundPhases.lilyOfTheValley(ClientSeasonPhase.step(), at.getX(), at.getZ());
							c[l.ordinal()]++;
							if (l == GroundPhases.Look.NONE && c[c.length - 1] == 0) {
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
		if (total == 0) {
			result("%s lilies of the valley around the camera: none", sub);
			return;
		}
		double flowering = counts[GroundPhases.Look.FULL.ordinal()] / (double) total;
		double none = counts[GroundPhases.Look.NONE.ordinal()] / (double) total;
		boolean serverEmpty = none == 0 || sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			int step = SeasonPhase.step(level);
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					for (int dy = -8; dy <= 4; dy++) {
						at.set(cam.x() + dx, cam.y() + dy, cam.z() + dz);
						BlockState st = level.getBlockState(at);
						if (st.is(Blocks.LILY_OF_THE_VALLEY)
								&& GroundPhases.lilyOfTheValley(step, at.getX(), at.getZ()) == GroundPhases.Look.NONE) {
							return st.getShape(level, at).isEmpty();
						}
					}
				}
			}
			return true;
		});
		result("%s lilies of the valley around the camera: %d, flowering %.0f%%, leaves %.0f%%, withdrawn %.0f%%; outline of a "
				+ "withdrawn one: client %s, server %s", sub, total, 100 * flowering,
				100.0 * counts[GroundPhases.Look.LEAVES.ordinal()] / total, 100 * none,
				counts[counts.length - 1] == 0 ? "-" : counts[counts.length - 1] > 0 ? "empty" : "NOT EMPTY",
				none == 0 ? "-" : serverEmpty ? "empty" : "NOT EMPTY");
		boolean dormant = sub == SubSeason.MID_WINTER || sub == SubSeason.EARLY_SPRING || sub == SubSeason.LATE_AUTUMN;
		if (dormant && none < 0.999 || sub == SubSeason.MID_SUMMER && counts[GroundPhases.Look.LEAVES.ordinal()] < total) {
			fail("%s: lilies of the valley flowering %.0f%%, withdrawn %.0f%%", sub, 100 * flowering, 100 * none);
		}
		if (counts[counts.length - 1] < 0 || !serverEmpty) {
			fail("%s: a withdrawn lily of the valley has an outline", sub);
		}
	}

	/**
	 * The sky through the bare canopy (review round 1 of V13): in the frame up into the oak-hornbeam canopy the bare crowns
	 * of MID_WINTER let through at least {@value #MIN_WINTER_CANOPY_SKY} of sky in REAL ({@value #MIN_WINTER_CANOPY_SKY_GAMEPLAY}
	 * in GAMEPLAY) and {@value #MIN_WINTER_CANOPY_RATIO} times
	 * as much as the leafy canopy of MID_SUMMER (one screen of twigs per crown, not a stack of them).
	 */
	private void canopy() {
		Double winter = canopySky.get(SubSeason.MID_WINTER);
		Double summer = canopySky.get(SubSeason.MID_SUMMER);
		StringBuilder all = new StringBuilder();
		canopySky.forEach((k, v) -> all.append(String.format(Locale.ROOT, " %s %.1f%%", k, 100 * v)));
		result("sky in the oak-hornbeam canopy frame:%s", all);
		double least = real ? MIN_WINTER_CANOPY_SKY : MIN_WINTER_CANOPY_SKY_GAMEPLAY;
		if (winter != null && summer != null && (winter < least || winter < MIN_WINTER_CANOPY_RATIO * Math.max(summer, 0.002))) {
			fail("bare canopy: sky %.1f%% in MID_WINTER against %.1f%% in MID_SUMMER (at least %.0f%% and %.0f times)", 100 * winter,
					100 * summer, 100 * least, MIN_WINTER_CANOPY_RATIO);
		}
	}

	/**
	 * Builds the Fast-mode rig of review round 1 of V13 high over the oak-hornbeam camera, against the sky: a wall of species
	 * oak leaves (bare in winter) with marcescent oak leaves behind it (leaves in leaf next to bare twigs), and a wall of
	 * vanilla oak leaves three blocks deep (the vanilla leaves that the Fast mode keeps cut out in a transition step), and a
	 * reference of two layers of marcescent leaves (always in leaf: how much sky the renderer's own Fast mode shows through
	 * leaves; Sodium and Nvidium cut their opaque layer at alpha 0.5, so there the Fast mode shows sky through leaves too).
	 * Returns the corner of the rig (the walls stand at y + 3 … y + 7 and x + 12 k … x + 12 k + 6, from z).
	 */
	private static BlockPos buildRig(ServerLevel level, SliceCameras.Camera cam) {
		// Just over the highest crown around (the sections high above the terrain may have no light on the client yet).
		int top = level.getMinY();
		for (int dx = -24; dx <= 24; dx += 2) {
			for (int dz = -16; dz <= 48; dz += 2) {
				// The heightmap of a chunk that is not loaded is the bottom of the world: load it first.
				level.getChunk((cam.x() + dx) >> 4, (cam.z() + dz) >> 4);
				top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, cam.x() + dx, cam.z() + dz));
			}
		}
		int y = Math.min(top + 2, level.getMaxY() - 12);
		BlockPos corner = new BlockPos(cam.x() - 9, y, cam.z() + 6);
		BlockState bare = ModBlocks.leaves(TreeFoliage.OAK).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)
				.setValue(SpeciesLeavesBlock.PHASE, 1);
		BlockState brown = bare.setValue(MarcescentLeavesBlock.MARCESCENT, true);
		BlockState vanilla = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int x = 0; x < 7; x++) {
			for (int h = 3; h < 8; h++) {
				level.setBlock(corner.offset(x, h, 0), bare, 2);
				level.setBlock(corner.offset(x, h, 1), brown, 2);
				for (int z = 0; z < 3; z++) {
					level.setBlock(corner.offset(x + 12, h, z), vanilla, 2);
				}
				level.setBlock(corner.offset(x + 24, h, 0), brown, 2);
				level.setBlock(corner.offset(x + 24, h, 1), brown, 2);
			}
		}
		return corner;
	}

	/**
	 * The Fast graphics mode at a step (review round 1 of V13): the sky through the rig walls in the Fast mode at most
	 * {@value #MAX_FAST_EXCESS} over the Fancy mode and over the reference wall (no hole where leaves in leaf meet bare twigs),
	 * and, when the vanilla leaves are kept cut out in the Fast mode (a transition step), the vanilla wall as in the Fancy
	 * mode (no hollow crown); then a Fast-mode frame of the oak-hornbeam forest from 30 m.
	 */
	private void fastRig(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos rig, SubSeason sub, String prefix,
			String scale) {
		double[] fancy = rigSky(context, sp, rig, prefix + "rig_fancy");
		context.runOnClient(mc -> mc.options.cutoutLeaves().set(false));
		context.waitTicks(40);
		context.waitFor(SeasonSamples::compiled, 1200);
		double[] fast = rigSky(context, sp, rig, prefix + "rig_fast");
		boolean transition = context.computeOnClient(mc -> ClientSeasonModels.cutOutInFastMode(Blocks.OAK_LEAVES.defaultBlockState()));
		boolean vanillaBare = context.computeOnClient(mc -> ClientSeasonModels.vanillaLeafless(Blocks.OAK_LEAVES.defaultBlockState(),
				rig.offset(15, 5, 0)));
		boolean oursBare = context.computeOnClient(mc -> ClientSeasonModels.leafless(mc.level.getBlockState(rig.offset(3, 5, 0))));
		SliceCameras.Camera view = camera(SliceCameras.read(), scale, "oak_hornbeam_1", "30m");
		if (view != null) {
			shot(context, sp, prefix + "oak_hornbeam_1_30m_fast", view, 3);
		}
		context.runOnClient(mc -> mc.options.cutoutLeaves().set(true));
		context.waitTicks(40);
		context.waitFor(SeasonSamples::compiled, 1200);
		result("%s Fast-mode rig: species oak (bare %s) before marcescent leaves, sky Fancy %.1f%% / Fast %.1f%%; vanilla oak wall "
				+ "(cut out in Fast %s, bare %s) Fancy %.1f%% / Fast %.1f%%; reference (two layers in leaf) Fancy %.1f%% / Fast %.1f%%",
				sub, oursBare, 100 * fancy[0], 100 * fast[0], transition, vanillaBare, 100 * fancy[1], 100 * fast[1], 100 * fancy[2],
				100 * fast[2]);
		// A hole: more sky than in the Fancy mode and than the renderer's own Fast mode shows through two layers in leaf.
		if (fast[0] > Math.max(fancy[0], fast[2]) + MAX_FAST_EXCESS) {
			fail("%s: Fast mode shows %.1f%% sky through bare twigs before leaves in leaf (Fancy %.1f%%, reference %.1f%%): a hole", sub,
					100 * fast[0], 100 * fancy[0], 100 * fast[2]);
		}
		if (transition && Math.abs(fast[1] - fancy[1]) > MAX_FAST_EXCESS) {
			fail("%s: the vanilla oak wall cut out in the Fast mode shows %.1f%% sky (Fancy %.1f%%)", sub, 100 * fast[1], 100 * fancy[1]);
		}
	}

	/** The sky share in the middle of the frames of the three rig walls (camera below them, looking up at them). */
	private static double[] rigSky(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos rig, String name) {
		double[] out = new double[3];
		for (int k = 0; k < 3; k++) {
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 -30", rig.getX() + 12 * k + 3.5,
					rig.getY() + 1.58, rig.getZ() - 3.0));
			// Step T7: until the view is drawn instead of 15 ticks and an empty compile queue.
			Path path = ViewReady.shot(context, sp, name + (k == 0 ? "_twigs" : k == 1 ? "_vanilla" : "_reference"), RIG_SIZE, 1);
			out[k] = RenderProbesClientGameTest.sky(path);
		}
		return out;
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * Step Z2 (DP-62): the canopy camera at the nearest feet block at most {@value #CANOPY_SHIFT} blocks from the fixed one
	 * (same height) that stands free under the canopy ({@link #clearUp}), so that the frame measures the sky through the
	 * crowns and does not look up along a trunk or a wall of ground (the GAMEPLAY camera looked up along a
	 * trunk in V13 and stood beside a wall of dirt in stage 1 of Z2); the fixed camera itself when it is clear, or when no
	 * block is.
	 */
	private SliceCameras.Camera openCanopy(TestSingleplayerContext sp, SliceCameras.Camera cam) {
		if (cam == null) {
			return null;
		}
		int[] shift = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			int[] best = null;
			int bestD = Integer.MAX_VALUE;
			for (int dx = -CANOPY_SHIFT; dx <= CANOPY_SHIFT; dx++) {
				for (int dz = -CANOPY_SHIFT; dz <= CANOPY_SHIFT; dz++) {
					int d = dx * dx + dz * dz;
					if (d < bestD && d <= CANOPY_SHIFT * CANOPY_SHIFT && clearUp(level, cam.x() + dx, cam.y(), cam.z() + dz)) {
						best = new int[] {dx, dz};
						bestD = d;
					}
				}
			}
			return best;
		});
		if (shift == null) {
			result("canopy camera: no free column within %d blocks, the fixed camera %d %d %d", CANOPY_SHIFT, cam.x(), cam.y(),
					cam.z());
			return cam;
		}
		result("canopy camera at %d %d %d (moved %d, %d from the fixed camera to a free column)", cam.x() + shift[0], cam.y(),
				cam.z() + shift[1], shift[0], shift[1]);
		return new SliceCameras.Camera(cam.name(), cam.x() + shift[0], cam.y(), cam.z() + shift[1], cam.yaw(), cam.pitch());
	}

	/**
	 * Whether a camera with its feet at (x, y, z) stands free under the canopy: feet and head free of collision and
	 * leaves, no solid block other than wood and leaves within {@value #CLEAR_RADIUS} blocks of its column from 2 to
	 * {@value #CLEAR_HEIGHT} blocks above the feet (a wall of ground or the root plate of a windthrow beside the head, full
	 * blocks that fill a large part of the frame; stage 2 of Z2: the GAMEPLAY camera had one in about a third of its frame),
	 * and no column of the 3 × 3 around it with {@value #TRUNK_RUN} or more wood blocks from 2 to 10 blocks above the feet
	 * (a bole; a thin branch crossing above does not count).
	 */
	private static boolean clearUp(ServerLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = 0; dy <= 1; dy++) {
			BlockState st = level.getBlockState(at.set(x, y + dy, z));
			if (!st.getCollisionShape(level, at).isEmpty() || st.getBlock() instanceof LeavesBlock) {
				return false;
			}
		}
		for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
			for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
				int wood = 0;
				for (int dy = 2; dy <= 10; dy++) {
					BlockState st = level.getBlockState(at.set(x + dx, y + dy, z + dz));
					boolean log = st.is(net.minecraft.tags.BlockTags.LOGS) || st.getBlock() instanceof pl.polishforests.block.TrunkBlock;
					if (log) {
						wood++;
					}
					if (!log && dy <= CLEAR_HEIGHT && !(st.getBlock() instanceof LeavesBlock) && !st.getCollisionShape(level, at).isEmpty()) {
						return false;
					}
				}
				if (wood >= TRUNK_RUN && Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					return false;
				}
			}
		}
		return true;
	}

	private static SliceCameras.Camera camera(Map<String, List<SliceCameras.Camera>> cameras, String scale, String place, String name) {
		for (SliceCameras.Camera c : cameras.getOrDefault(scale + " " + place, List.of())) {
			if (c.name().equals(name)) {
				return c;
			}
		}
		return null;
	}

	private static void teleport(TestSingleplayerContext sp, SliceCameras.Camera cam) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f %.1f %.1f", cam.x() + 0.5, cam.y(), cam.z() + 0.5,
				cam.yaw(), cam.pitch()));
	}

	/** A fixed wait at a camera, for the checks that need the chunks around it ticking (fruit, catch-up), not for frames. */
	private static void go(ClientGameTestContext context, TestSingleplayerContext sp, SliceCameras.Camera cam, int waitSeconds) {
		teleport(sp, cam);
		context.waitTicks(20 * waitSeconds);
	}

	private static String name(SubSeason sub) {
		return sub.name().toLowerCase(Locale.ROOT);
	}

	/** A frame of a camera: until the view is drawn (step T7; before: 4 s and an empty compile queue). */
	private static Path shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, SliceCameras.Camera cam,
			int legacySeconds) {
		teleport(sp, cam);
		Path path = ViewReady.shot(context, sp, name, SIZE, legacySeconds);
		PolishForests.LOG.info("[seasons] shot {}", name);
		return path;
	}

	/** The share of sky pixels (clearly blue) in a whole frame. */
	static double skyShare(Path path) {
		try {
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(path.toFile());
			int w = img.getWidth();
			int h = img.getHeight();
			int[] px = img.getRGB(0, 0, w, h, null, 0, w);
			int n = 0;
			for (int p : px) {
				int r = (p >> 16) & 255;
				int g = (p >> 8) & 255;
				int b = p & 255;
				if (b >= 120 && b > r + 30 && b >= g) {
					n++;
				}
			}
			return (double) n / px.length;
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
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
