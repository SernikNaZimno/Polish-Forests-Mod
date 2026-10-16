package pl.polishforests.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.gametest.probe.FrameRecorder;
import pl.polishforests.gametest.probe.ProbeSeason;
import pl.polishforests.gametest.probe.RenderProbes;
import pl.polishforests.gametest.probe.RenderProbesClient;
import pl.polishforests.season.FallbackCalendar;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;

/**
 * Render trials of step V3 (docs/04-pionowy-wycinek.md §3), {@code -Pgametest=probes}, only by name. They decide B6 (own
 * color curves on the raw foliage color through an own {@code ColorResolver}) and PW-8 (season looks of plants and the
 * winter twigs of the crowns through a wrapped FRAPI model) before checkpoint CP-A. Each run is one combination of the
 * renderer and the season source (Serene Seasons, or the built-in calendar with {@code -PnoSeasons}). The renderer
 * (review round 1) is {@code vanilla}, {@code sodium} ({@code -Poptimization -PnoNvidium}: Sodium with MoreCulling and the
 * rest of the modpack, as on AMD and Intel GPUs) or {@code sodium_nvidium} ({@code -Poptimization} on an NVIDIA GPU with
 * mesh shaders, where Nvidium switches itself on and draws the terrain); the run line records which one ran and the GPU.
 *
 * <p>Part {@code board} ({@code -Psites=board}): in a superflat world a stone floor with a deep wall of the probe leaves
 * ({@link RenderProbes}), a deep wall of vanilla oak leaves (SS seasons their color itself), a screen of the probe leaves one
 * block thick against the sky, a patch of the probe plant and a small tree with a probe crown. In the sub-seasons
 * {@link #SEQUENCE} (set with {@code season set} under SS, with {@code time set} on the built-in calendar, also two steps
 * inside MID_AUTUMN there) the test waits for the color step and the rebuilt meshes and checks on the screenshots
 * ({@code probes_<renderer>_<seasons>_<view>_<sub-season>}, also a view from under the crown):
 * <ul>
 * <li>the color of the probe wall equals the trial curve on the raw biome foliage color (chromaticity of the median pixel
 * against the tint, {@link #CHROMA_TOLERANCE}), and is nearer to it than to the tint of the step checked before (a stale
 * mesh would keep that one); the tint of {@code BlockColors} equals the curve, and the raw foliage color stays the same in
 * every season (no second seasoning by SS);
 * <li>the screen shows leaves in a leafy phase (little sky through it) and the twig model in the leafless phase (much sky);
 * <li>the plant patch has plants in spring and none in its empty phase.
 * </ul>
 * Then the Fast graphics mode: the probe leaves are as opaque or as cut out as vanilla leaves in the same renderer, and the
 * twigs stay cut out and keep their inner faces (as dense as in Fancy). Part {@code rebuild} ({@code -Psites=rebuild}): in
 * the realistic Poland world at the slice site {@code oak_hornbeam_1} from 30 blocks above, render distance 12, the frame
 * times and the sections with a mesh in view during a rebuild of every mesh, 3 times each in three ways
 * ({@code levelExtractor.allChanged()}, which drops the meshes; every section marked dirty at once; rings of dirty columns
 * from the camera section at the start of the wave, one ring every {@link #WAVE_TICKS} ticks) and once through a change of
 * the color step, against frames without a rebuild; a rebuild lasts until its last slow frame (twice the slowest frame
 * without a rebuild) or the last frame with less than 95% of the view meshed. Then the biome blend of the own resolver in
 * the section mesher at places near biome borders ({@link #biomeBlend}). All numbers go to the log ({@code [probes]}) and to
 * {@code build/probes/<renderer>_<seasons>.txt}; the screenshots of a run replace those of the previous run.
 *
 * <p>The game test renders one frame per tick, driven by the test thread; Sodium sizes its per-frame build and upload
 * budget from the measured frame interval, so the frame times here measure the work of the render thread, not the frames
 * of normal play (docs/04 "Stan po V3", review round 1).
 */
public final class RenderProbesClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int NOON = 6000;
	private static final int[] SIZE = {854, 480};
	private static final int[] OVERVIEW_SIZE = {1280, 720};
	/** Largest distance of the chromaticity (r, g, b over their sum) of the probe wall from the tint. */
	private static final double CHROMA_TOLERANCE = 0.03;
	/** Sky share of the central region of the screen view: at most this with leaves, at least {@link #MIN_SKY_BARE} bare. */
	private static final double MAX_SKY_LEAFY = 0.25;
	private static final double MIN_SKY_BARE = 0.35;
	/** Largest difference of the sky share through the twig screen between the Fast and the Fancy mode. */
	private static final double MAX_FAST_TWIG_DIFFERENCE = 0.05;
	/**
	 * A color step inside a sub-season differs from the step before by only 0.012-0.018 in chromaticity, less than
	 * {@link #CHROMA_TOLERANCE}; when the tint of the previously checked board step differs from the current one by at least
	 * this, the wall must also be nearer to the current tint than to that one (a stale mesh would keep the old color).
	 */
	private static final double MIN_STEP_SEPARATION = 0.006;
	/** Plant share of the central region of the plant patch: at least this in the visible phase, at most {@link #MAX_PLANT_EMPTY}. */
	private static final double MIN_PLANT_VISIBLE = 0.08;
	private static final double MAX_PLANT_EMPTY = 0.005;
	/** Sub-seasons of the board and the day inside the sub-season (built-in calendar only; under SS the day is 0). */
	private static final Object[][] SEQUENCE = {
			{SubSeason.EARLY_SPRING, 0}, {SubSeason.MID_SPRING, 0}, {SubSeason.MID_SUMMER, 0}, {SubSeason.MID_AUTUMN, 0},
			{SubSeason.MID_AUTUMN, 4}, {SubSeason.MID_AUTUMN, 8}, {SubSeason.LATE_AUTUMN, 0}, {SubSeason.MID_WINTER, 0}};
	private static final int REBUILD_DISTANCE = 12;
	private static final int REBUILDS = 3;
	/** Ticks between two rings of the wave rebuild; frames over this many nanoseconds count as hitches. */
	private static final int WAVE_TICKS = 2;
	private static final long HITCH_NANOS = 50_000_000L;
	/** Frames in a row that must not be slow before a rebuild counts as done. */
	private static final int QUIET_FRAMES = 20;

	private final List<String> results = new ArrayList<>();
	private boolean nvidiumAtStart;
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!System.getProperty("polishforests.gametest", "all").equals("probes")) {
			return;
		}
		if (!RenderProbes.enabled()) {
			throw new AssertionError("The render probes are not registered");
		}
		String parts = System.getProperty("polishforests.sites", "");
		boolean sodium = FabricLoader.getInstance().isModLoaded("sodium");
		boolean seasons = FabricLoader.getInstance().isModLoaded("sereneseasons");
		// Review round 1: on an NVIDIA GPU with mesh shaders Nvidium (in the -Poptimization pack) draws the terrain instead of
		// Sodium, so the renderer of a run is vanilla, sodium (plain, -PnoNvidium or another GPU) or sodium_nvidium.
		boolean nvidiumLoaded = FabricLoader.getInstance().isModLoaded("nvidium");
		boolean nvidium = context.computeOnClient(mc -> nvidiumEnabled());
		nvidiumAtStart = nvidium;
		String gpu = context.computeOnClient(mc -> gpu());
		String renderer = !sodium ? "vanilla" : nvidium ? "sodium_nvidium" : "sodium";
		String tag = renderer + "_" + (seasons ? "ss" : "noss");
		result("run %s: renderer %s (Sodium %s, Nvidium loaded %s, enabled %s), MoreCulling %s, Serene Seasons %s, calendar %s, GPU %s",
				tag, renderer, sodium, nvidiumLoaded, nvidium, FabricLoader.getInstance().isModLoaded("moreculling"), seasons,
				Seasons.provider().name(), gpu);
		if (parts.isBlank() || parts.contains("board")) {
			board(context, tag, seasons);
		}
		if (parts.isBlank() || parts.contains("rebuild")) {
			rebuild(context, seasons);
		}
		write(tag);
		if (!failures.isEmpty()) {
			throw new AssertionError("Render probes failed:\n" + String.join("\n", failures));
		}
	}

	/** Nvidium switched on (it reads the GPU at start and turns itself off where mesh shaders are missing). */
	private static boolean nvidiumEnabled() {
		if (!FabricLoader.getInstance().isModLoaded("nvidium")) {
			return false;
		}
		try {
			return Class.forName("me.cortex.nvidium.Nvidium").getField("IS_ENABLED").getBoolean(null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Cannot read Nvidium.IS_ENABLED", e);
		}
	}

	/** The OpenGL renderer string (render thread). */
	private static String gpu() {
		try {
			return String.valueOf(org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
		} catch (RuntimeException | LinkageError e) {
			return "unknown (" + e + ")";
		}
	}

	// ------------------------------------------------------------------ board

	private void board(ClientGameTestContext context, String tag, boolean seasons) {
		context.runOnClient(mc -> {
			mc.options.ambientOcclusion().set(true);
			mc.options.cutoutLeaves().set(true);
			mc.options.renderDistance().set(8);
		});
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			for (String c : new String[] {"gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
					"time set " + NOON, "weather clear", "difficulty peaceful", "gamerule send_command_feedback false"}) {
				sp.getServer().runCommand(c);
			}
			int y0 = sp.getServer().computeOnServer(RenderProbesClientGameTest::build);
			sp.getServer().runCommand("gamemode spectator @a");
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.waitTicks(40);
			context.waitFor(mc -> FrameRecorder.allCompiled(), 1200);
			String p = "probes_" + tag + "_";
			for (Object[] s : SEQUENCE) {
				SubSeason sub = (SubSeason) s[0];
				int day = (Integer) s[1];
				if (seasons && day != 0) {
					continue;
				}
				setSeason(context, sp, sub, day, seasons);
				String name = sub.name().toLowerCase(Locale.ROOT) + (day == 0 ? "" : "_d" + day);
				checkBoard(context, sp, y0, p, name, sub);
			}
			// The Fast graphics mode: leaves follow the renderer like vanilla leaves; the twigs of the wrapped model stay cut out
			// (ModelBlockRendererMixin) and keep their faces towards other leaves, so the bare crown is as dense as in Fancy.
			context.runOnClient(mc -> mc.options.cutoutLeaves().set(false));
			context.waitTicks(60);
			for (SubSeason sub : new SubSeason[] {SubSeason.MID_SUMMER, SubSeason.MID_WINTER}) {
				setSeason(context, sp, sub, 0, seasons);
				context.waitFor(mc -> FrameRecorder.allCompiled(), 1200);
				context.waitTicks(20);
				String name = "fast_" + sub.name().toLowerCase(Locale.ROOT);
				shot(context, sp, y0, p + "overview_" + name, OVERVIEW);
				shot(context, sp, y0, p + "tree_" + name, TREE);
				double sky = sky(shot(context, sp, y0, p + "screen_" + name, SCREEN));
				double oakSky = sky(shot(context, sp, y0, p + "oak_screen_" + name, OAK_SCREEN));
				boolean bare = ProbeSeason.leafless(sub);
				result("%s fast %s: screen sky %.1f%%, vanilla oak screen sky %.1f%%%s", tag, sub, 100 * sky, 100 * oakSky,
						bare ? String.format(Locale.ROOT, " (Fancy %.1f%%)", 100 * fancyBareSky) : "");
				// Leaves follow the leaves mode of the renderer like vanilla leaves (vanilla and plain Sodium: opaque; Nvidium
				// cuts its opaque terrain at alpha 0.5, so there they stay cut out); the twigs stay cut out in every renderer.
				if (bare ? sky < MIN_SKY_BARE : (sky > 0.05) != (oakSky > 0.05)) {
					fail("%s fast %s: screen sky %.1f%% (vanilla oak %.1f%%), expected %s", tag, sub, 100 * sky, 100 * oakSky, bare
							? "twigs cut out (>= " + Math.round(100 * MIN_SKY_BARE) + "%)" : "leaves as opaque or as cut out as vanilla leaves");
				}
				// The inner faces of the bare crown stay in Fast mode: the twig screen as dense as in Fancy.
				if (bare && Math.abs(sky - fancyBareSky) > MAX_FAST_TWIG_DIFFERENCE) {
					fail("%s fast %s: twig screen sky %.1f%%, in Fancy %.1f%%: the bare crown lost its inner faces", tag, sub,
							100 * sky, 100 * fancyBareSky);
				}
			}
			context.runOnClient(mc -> mc.options.cutoutLeaves().set(true));
			result("%s: %d mesh rebuilds started by color steps", tag, ProbeSeason.rebuilds());
		}
	}

	/** Builds the board on the server; returns the Y of the floor. */
	private static int build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) - 1;
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState probe = RenderProbes.leaves().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		BlockState oak = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int x = -4; x <= 26; x++) {
			for (int z = -12; z <= 14; z++) {
				put(level, x, y0, z, stone);
			}
		}
		for (int x = 0; x <= 6; x++) {
			for (int y = 1; y <= 6; y++) {
				for (int z = 2; z <= 5; z++) {
					put(level, x, y0 + y, z, probe);
					put(level, x + 9, y0 + y, z, oak);
				}
			}
			for (int y = 1; y <= 7; y++) {
				put(level, x, y0 + y, SCREEN_Z, probe);
				put(level, x + 9, y0 + y, SCREEN_Z, oak);
			}
			// The plant patch on coarse dirt (a plant needs a dirt block; brown, so the plant pixels stay countable).
			for (int z = -9; z <= -3; z++) {
				put(level, x, y0, z, Blocks.COARSE_DIRT.defaultBlockState());
			}
			for (int z = -8; z <= -4; z++) {
				put(level, x, y0 + 1, z, RenderProbes.plant().defaultBlockState());
			}
		}
		// A small tree: oak trunk of radius 3 under a round probe crown.
		for (int y = 1; y <= 5; y++) {
			put(level, TREE_X, y0 + y, 3, ModBlocks.trunk(TreeTrunk.OAK).state(3, Direction.DOWN));
		}
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -2; dy <= 3; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockPos pos = new BlockPos(TREE_X + dx, y0 + 6 + dy, 3 + dz);
					if (dx * dx + dy * dy * 1.6 + dz * dz <= 8.5 && level.getBlockState(pos).isAir()) {
						level.setBlock(pos, probe, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
			}
		}
		return y0;
	}

	/** Places a block without shape updates of the neighbors (the plants must not pop off while the board is built). */
	private static void put(ServerLevel level, int x, int y, int z, BlockState s) {
		level.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static final int SCREEN_Z = 12;
	private static final int TREE_X = 21;
	/** Cameras: {x, eye height above the floor, z, yaw, pitch}. */
	private static final double[] OVERVIEW = {11.5, 8, -15, 0, 20};
	private static final double[] WALL = {3.5, 3.5, -1, 0, 0};
	private static final double[] OAK_WALL = {12.5, 3.5, -1, 0, 0};
	private static final double[] SCREEN = {3.5, 2.2, 9, 0, -30};
	private static final double[] OAK_SCREEN = {12.5, 2.2, 9, 0, -30};
	private static final double[] PLANTS = {3.5, 5, -6, 0, 90};
	private static final double[] TREE = {TREE_X + 0.5, 4, -6, 0, -5};
	/** Under the crown of the small tree, looking up past the trunk (the most common view in a forest; review round 1). */
	private static final double[] TREE_UP = {TREE_X + 2.5, 1.2, 5.5, 135, -65};

	private void setSeason(ClientGameTestContext context, TestSingleplayerContext sp, SubSeason sub, int day, boolean seasons) {
		if (seasons) {
			sp.getServer().runCommand("season set " + sub.name().toLowerCase(Locale.ROOT));
		} else {
			long ticks = (sub.ordinal() * (long) Seasons.DEFAULT_DAYS_PER_SUB_SEASON + day) * FallbackCalendar.TICKS_PER_DAY + NOON;
			sp.getServer().runCommand("time set " + ticks);
		}
		int waited = context.waitFor(mc -> ProbeSeason.subSeason() == sub && ProbeSeason.step() >= 0
				&& (seasons || ProbeSeason.step() % ProbeSeason.STEPS_PER_SUB_SEASON == day / 4), 400);
		context.waitTicks(3);
		context.waitFor(mc -> FrameRecorder.allCompiled(), 1200);
		context.waitTicks(20);
		PolishForests.LOG.info("[probes] {} day {}: color step {} after {} ticks, rebuilds {}", sub, day, ProbeSeason.step(), waited,
				ProbeSeason.rebuilds());
	}

	private void checkBoard(ClientGameTestContext context, TestSingleplayerContext sp, int y0, String p, String name, SubSeason sub) {
		String tag = p.substring("probes_".length(), p.length() - 1);
		int step = ProbeSeason.step();
		BlockPos wallPos = new BlockPos(3, y0 + 3, 2);
		BlockPos oakPos = new BlockPos(12, y0 + 3, 2);
		int[] tints = context.computeOnClient(mc -> {
			BlockState state = mc.level.getBlockState(wallPos);
			if (!state.is(RenderProbes.leaves())) {
				throw new AssertionError("The client has " + state + " at the probe wall");
			}
			int raw = mc.level.getBlockTint(wallPos, ProbeSeason.RAW_FOLIAGE);
			int curve = ProbeSeason.color(step, raw);
			int tint = mc.getBlockColors().getTintSources(state).getFirst().colorInWorld(state, mc.level, wallPos);
			int vanilla = BiomeColors.getAverageFoliageColor(mc.level, oakPos);
			return new int[] {raw, curve, tint, vanilla};
		});
		shot(context, sp, y0, p + "overview_" + name, OVERVIEW);
		shot(context, sp, y0, p + "tree_" + name, TREE);
		shot(context, sp, y0, p + "tree_up_" + name, TREE_UP);
		double[] wall = median(shot(context, sp, y0, p + "wall_" + name, WALL));
		double[] oak = median(shot(context, sp, y0, p + "oak_" + name, OAK_WALL));
		double sky = sky(shot(context, sp, y0, p + "screen_" + name, SCREEN));
		double oakSky = sky(shot(context, sp, y0, p + "oak_screen_" + name, OAK_SCREEN));
		double plants = plants(shot(context, sp, y0, p + "plants_" + name, PLANTS));
		boolean bare = ProbeSeason.leafless(sub);
		double dist = chromaDistance(wall, tints[1]);
		double previousDist = previousTint == 0 ? Double.NaN : chromaDistance(wall, previousTint);
		double separation = previousTint == 0 ? 0 : chromaDistance(rgbOf(previousTint), tints[1]);
		result("%s %s step %d: raw foliage %s, curve %s, BlockColors %s, vanilla foliage (oak) %s; wall median %s, chroma "
				+ "distance %.3f (to the previous checked tint %s: %.3f); oak wall median %s; screen sky %.1f%% (vanilla oak screen "
				+ "%.1f%%); plants %.1f%%", tag, name, step, hex(tints[0]), hex(tints[1]), hex(tints[2]), hex(tints[3]), rgb(wall), dist,
				previousTint == 0 ? "none" : hex(previousTint), previousDist, rgb(oak), 100 * sky, 100 * oakSky, 100 * plants);
		if (tints[2] != tints[1]) {
			fail("%s %s: BlockColors gives %s, the curve %s", tag, name, hex(tints[2]), hex(tints[1]));
		}
		raw.add(tints[0]);
		if (raw.stream().distinct().count() > 1) {
			fail("%s %s: the raw foliage color changed with the season: %s", tag, name, raw.stream().map(RenderProbesClientGameTest::hex).toList());
		}
		if (!bare && dist > CHROMA_TOLERANCE) {
			fail("%s %s: the probe wall %s is not the tint %s (chroma distance %.3f)", tag, name, rgb(wall), hex(tints[1]), dist);
		}
		if (!bare && separation >= MIN_STEP_SEPARATION && dist >= previousDist) {
			fail("%s %s: the probe wall %s is nearer to the previous tint %s (%.3f) than to the current %s (%.3f): stale mesh?", tag,
					name, rgb(wall), hex(previousTint), previousDist, hex(tints[1]), dist);
		}
		previousTint = bare ? 0 : tints[1];
		if (bare && sub == SubSeason.MID_WINTER) {
			fancyBareSky = sky;
		}
		if (bare ? sky < MIN_SKY_BARE : sky > MAX_SKY_LEAFY) {
			fail("%s %s: screen sky %.1f%% in a %s phase", tag, name, 100 * sky, bare ? "leafless" : "leafy");
		}
		boolean empty = ProbeSeason.plantEmpty(sub);
		if (empty ? plants > MAX_PLANT_EMPTY : plants < MIN_PLANT_VISIBLE) {
			fail("%s %s: plant share %.2f%% in the %s phase", tag, name, 100 * plants, empty ? "empty" : "visible");
		}
	}

	/** Raw foliage colors at the probe wall in the sub-seasons of the board: must all be equal. */
	private final List<Integer> raw = new ArrayList<>();
	/** The curve tint of the previously checked leafy board step (0 before the first, or after a leafless one). */
	private int previousTint;
	/** Sky share through the twig screen in the Fancy mode, MID_WINTER (for the Fast mode check). */
	private double fancyBareSky = Double.NaN;

	private static Path shot(ClientGameTestContext context, TestSingleplayerContext sp, int y0, String name, double[] c) {
		// Eye height 1.62 above the feet.
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", c[0], y0 + 1 + c[1] - 1.62, c[2], c[3], c[4]));
		context.waitTicks(10);
		context.waitFor(mc -> FrameRecorder.allCompiled(), 600);
		context.waitTicks(5);
		int[] size = c == OVERVIEW ? OVERVIEW_SIZE : SIZE;
		return context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(size[0], size[1]));
	}

	// ------------------------------------------------------------------ rebuild cost

	private void rebuild(ClientGameTestContext context, boolean seasons) {
		ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id("poland"));
		SliceSites.Place site = SliceSites.of(true, false).stream().filter(pl -> pl.name().equals("oak_hornbeam_1")).findFirst()
				.orElseThrow();
		context.runOnClient(mc -> mc.options.renderDistance().set(REBUILD_DISTANCE));
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> {
			ui.setSeed(SEED);
			ui.setAllowCommands(true);
			ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(preset)).findFirst().orElseThrow());
		}).create()) {
			for (String c : new String[] {"gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
					"time set " + NOON, "weather clear", "gamemode spectator @a"}) {
				sp.getServer().runCommand(c);
			}
			if (seasons) {
				sp.getServer().runCommand("season set mid_summer");
			} else {
				sp.getServer().runCommand("time set " + ((long) SubSeason.MID_SUMMER.ordinal() * Seasons.DEFAULT_DAYS_PER_SUB_SEASON
						* FallbackCalendar.TICKS_PER_DAY + NOON));
			}
			int ground = sp.getServer().computeOnServer(s -> s.overworld().getChunk(site.x() >> 4, site.z() >> 4)
					.getHeight(Heightmap.Types.MOTION_BLOCKING, site.x() & 15, site.z() & 15));
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 30 30", site.x(), ground + 30, site.z() - 20));
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.waitTicks(20 * 30);
			context.waitFor(mc -> FrameRecorder.allCompiled(), 20 * 300);
			context.waitTicks(20 * 10);
			String stats = context.computeOnClient(mc -> mc.levelExtractor.sectionStatistics());
			boolean nvidiumInWorld = context.computeOnClient(mc -> nvidiumEnabled());
			result("rebuild world: Nvidium enabled %s", nvidiumInWorld);
			if (nvidiumInWorld != nvidiumAtStart) {
				fail("Nvidium enabled %s in the world, %s at the start of the run: the renderer tag is wrong", nvidiumInWorld,
						nvidiumAtStart);
			}
			context.runOnClient(mc -> FrameRecorder.start());
			context.waitTicks(200);
			long[] base = context.computeOnClient(mc -> FrameRecorder.stop());
			int[] baseRendered = context.computeOnClient(mc -> FrameRecorder.renderedSeries());
			int r0 = median(baseRendered);
			double[] b = FrameRecorder.stats(base);
			result("rebuild: render distance %d at %s (%d, %d, %d), %s; sections with a mesh in view %d; frames without a rebuild: %s",
					REBUILD_DISTANCE, site.name(), site.x(), ground + 30, site.z() - 20, stats, r0, FrameRecorder.summary(base));
			// A frame is slow above twice the slowest frame without a rebuild (at least 4 ms); a rebuild is done when the
			// renderer reports every section compiled, the view has its meshes back and QUIET_FRAMES frames in a row were not
			// slow (review round 1: Sodium reports "all compiled" before newly dirty sections are scheduled).
			long slow = Math.max(2 * Arrays.stream(base).max().orElse(0), 4_000_000L);
			List<double[]> rebuilds = new ArrayList<>();
			String[] modes = {"allChanged", "sections marked dirty", "wave of dirty rings"};
			for (int i = 0; i < modes.length * REBUILDS + 1; i++) {
				boolean step = i == modes.length * REBUILDS;
				int mode = Math.min(i / REBUILDS, modes.length - 1);
				context.runOnClient(mc -> FrameRecorder.start());
				int before = ProbeSeason.rebuilds();
				if (step) {
					// A real change of the color step: the next step of MID_SUMMER (under SS the next sub-season, so SS
					// rebuilds the meshes as well).
					if (seasons) {
						sp.getServer().runCommand("season set late_summer");
					} else {
						sp.getServer().runCommand("time set " + (((long) SubSeason.MID_SUMMER.ordinal() * Seasons.DEFAULT_DAYS_PER_SUB_SEASON + 4)
								* FallbackCalendar.TICKS_PER_DAY + NOON));
					}
					context.waitFor(mc -> ProbeSeason.rebuilds() > before, 200);
				} else if (mode == 0) {
					context.runOnClient(mc -> mc.levelExtractor.allChanged());
				} else if (mode == 1) {
					context.runOnClient(ProbeSeason::markAllDirty);
				} else {
					// Rings of chunk columns at growing distance from the camera section at the start of the wave, one ring
					// every WAVE_TICKS ticks.
					SectionPos center = context.computeOnClient(ProbeSeason::cameraSection);
					for (int ring = 0; ring <= REBUILD_DISTANCE + 1; ring++) {
						int r = ring;
						context.runOnClient(mc -> ProbeSeason.markRingDirty(mc, center, r));
						context.waitTicks(WAVE_TICKS);
					}
				}
				int issued = context.computeOnClient(mc -> FrameRecorder.count());
				context.waitTicks(3);
				int waited = context.waitFor(mc -> FrameRecorder.allCompiled() && FrameRecorder.rendered() >= 0.98 * r0
						&& FrameRecorder.quiet(QUIET_FRAMES, slow), 20 * 120);
				context.waitTicks(40);
				long[] frames = context.computeOnClient(mc -> FrameRecorder.stop());
				int[] rendered = context.computeOnClient(mc -> FrameRecorder.renderedSeries());
				// The rebuild lasts until the last slow frame or the last frame with less than 95% of the view meshed.
				int lastSlow = -1;
				int lastLow = -1;
				for (int f = 0; f < frames.length; f++) {
					if (frames[f] > slow) {
						lastSlow = f;
					}
					if (rendered[f] < 0.95 * r0) {
						lastLow = f;
					}
				}
				int settled = Math.max(lastSlow, lastLow) + 1;
				double[] d = FrameRecorder.stats(frames);
				int min = Arrays.stream(rendered).min().orElse(0);
				long low = Arrays.stream(rendered).filter(r -> r < 0.95 * r0).count();
				long hitches = Arrays.stream(frames).filter(f -> f > HITCH_NANOS).count();
				long slowFrames = Arrays.stream(frames).filter(f -> f > slow).count();
				rebuilds.add(new double[] {settled, issued, d[0], d[1], d[2], (double) min / Math.max(1, r0), low, hitches, slowFrames});
				String kind = step ? "by a color step (" + (seasons ? "season set late_summer, SS calls allChanged too" : "sections marked dirty")
						+ ")" : modes[mode] + " #" + (i % REBUILDS + 1);
				result("rebuild %s: settled after %d frames (call issued over %d frames, quiet streak after %d more ticks); frames "
						+ "from the start to 40 ticks after the quiet streak: %s, %d over %.1f ms, %d over 50 ms; sections with a mesh "
						+ "in view at least %d of %d (%.0f%%), below 95%% in %d frames", kind, settled, issued, waited,
						FrameRecorder.summary(frames), slowFrames, slow / 1e6, hitches, min, r0, 100.0 * min / Math.max(1, r0), low);
			}
			for (int m = 0; m < modes.length; m++) {
				List<double[]> runs = rebuilds.subList(m * REBUILDS, (m + 1) * REBUILDS);
				double medianSettled = runs.stream().mapToDouble(r -> r[0]).sorted().toArray()[REBUILDS / 2];
				double worst = runs.stream().mapToDouble(r -> r[4]).max().orElse(0);
				double medianP95 = runs.stream().mapToDouble(r -> r[3]).sorted().toArray()[REBUILDS / 2];
				double minView = runs.stream().mapToDouble(r -> r[5]).min().orElse(0);
				double medianLow = runs.stream().mapToDouble(r -> r[6]).sorted().toArray()[REBUILDS / 2];
				double hitches = runs.stream().mapToDouble(r -> r[7]).sorted().toArray()[REBUILDS / 2];
				double slowFrames = runs.stream().mapToDouble(r -> r[8]).sorted().toArray()[REBUILDS / 2];
				result("rebuild summary %s: median %.0f frames to settle, median p95 frame %.1f ms, worst frame %.1f ms, median %.0f "
						+ "frames over %.1f ms and %.0f over 50 ms (without a rebuild: median %.1f ms, max %.1f ms), least share of the "
						+ "view with meshes %.0f%%, median %.0f frames below 95%% of the view", modes[m], medianSettled, medianP95, worst,
						slowFrames, slow / 1e6, hitches, b[0], b[2], 100 * minView, medianLow);
			}
			biomeBlend(context, sp, site, ground, seasons);
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
	}

	/**
	 * The biome blend of the own resolver in the section mesher (review round 1 of V3, B6 and R10): in the REAL world,
	 * probe leaves at up to {@link #BLEND_PLACES} places near biome borders whose raw foliage colors differ (biomes of the
	 * mod have 0x50b02f ... 0x78a93a). The raw color seen by the mesher (vanilla's section region, or Sodium's level slice
	 * with its own color cache and box blur) must equal the blend of vanilla's formula ({@code
	 * ClientLevel.calculateBlockTint}, the biome blend radius of the options) within {@link #BLEND_TOLERANCE} per channel, and
	 * where the blend differs from the color of the center biome, so must the meshed color. Without SS the vanilla foliage
	 * resolver gives the same color.
	 */
	private void biomeBlend(ClientGameTestContext context, TestSingleplayerContext sp, SliceSites.Place site, int ground,
			boolean seasons) {
		// Places: on a 2-block grid within 112 blocks of the site, 2 blocks above the top block, the largest difference of
		// the blend from the center biome color first, at least 8 blocks apart.
		List<long[]> places = context.computeOnClient(mc -> {
			List<long[]> found = new ArrayList<>();
			for (int x = site.x() - 112; x <= site.x() + 112; x += 2) {
				for (int z = site.z() - 112; z <= site.z() + 112; z += 2) {
					int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 2;
					BlockPos pos = new BlockPos(x, y, z);
					int center = ProbeSeason.RAW_FOLIAGE.getColor(mc.level.getBiome(pos).value(), x, z);
					int blend = mc.level.calculateBlockTint(pos, ProbeSeason.RAW_FOLIAGE);
					int diff = channelDifference(center, blend);
					if (diff >= MIN_BLEND_DIFFERENCE) {
						found.add(new long[] {pos.asLong(), diff, blend, center});
					}
				}
			}
			found.sort((a, b) -> Long.compare(b[1], a[1]));
			List<long[]> chosen = new ArrayList<>();
			for (long[] f : found) {
				BlockPos p = BlockPos.of(f[0]);
				if (chosen.size() < BLEND_PLACES && chosen.stream().allMatch(c -> BlockPos.of(c[0]).distManhattan(p) >= 8)) {
					chosen.add(f);
				}
			}
			return chosen;
		});
		int radius = context.computeOnClient(mc -> mc.options.biomeBlendRadius().get());
		if (places.isEmpty()) {
			fail("biome blend: no place within 112 blocks of %s where the blend (radius %d) differs from the center biome by >= %d",
					site.name(), radius, MIN_BLEND_DIFFERENCE);
			return;
		}
		context.runOnClient(mc -> {
			RenderProbesClient.MESHED_RAW.clear();
			RenderProbesClient.recordMeshedRaw = true;
		});
		BlockState probe = RenderProbes.leaves().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		sp.getServer().runOnServer(server -> {
			for (long[] f : places) {
				put(server.overworld(), BlockPos.of(f[0]).getX(), BlockPos.of(f[0]).getY(), BlockPos.of(f[0]).getZ(), probe);
			}
		});
		int waited = context.waitFor(mc -> places.stream().allMatch(f -> RenderProbesClient.MESHED_RAW.containsKey(f[0])), 20 * 60);
		context.waitTicks(20);
		context.runOnClient(mc -> RenderProbesClient.recordMeshedRaw = false);
		int worst = 0;
		for (long[] f : places) {
			BlockPos pos = BlockPos.of(f[0]);
			int meshed = RenderProbesClient.MESHED_RAW.getOrDefault(f[0], 0);
			int blend = (int) f[2];
			int center = (int) f[3];
			int vanilla = context.computeOnClient(mc -> BiomeColors.getAverageFoliageColor(mc.level, pos));
			int diff = channelDifference(meshed, blend);
			worst = Math.max(worst, diff);
			result("biome blend at %s: meshed raw %s, vanilla formula %s (difference %d), center biome %s (blend difference %d)%s",
					pos.toShortString(), hex(meshed), hex(blend), diff, hex(center), f[1],
					seasons ? "" : ", vanilla foliage resolver " + hex(vanilla));
			if (diff > BLEND_TOLERANCE) {
				fail("biome blend at %s: the mesher saw %s, the blend is %s", pos.toShortString(), hex(meshed), hex(blend));
			}
			if (channelDifference(meshed, center) < f[1] - BLEND_TOLERANCE) {
				fail("biome blend at %s: the mesher saw %s, near the unblended center biome %s", pos.toShortString(), hex(meshed),
						hex(center));
			}
			if (!seasons && vanilla != blend) {
				fail("biome blend at %s: without SS the vanilla foliage resolver gives %s, the raw resolver %s", pos.toShortString(),
						hex(vanilla), hex(blend));
			}
		}
		result("biome blend summary: radius %d, %d places near biome borders, meshed after %d ticks, largest difference of the "
				+ "meshed raw color from the vanilla blend %d per channel", radius, places.size(), waited, worst);
	}

	private static final int BLEND_PLACES = 6;
	private static final int MIN_BLEND_DIFFERENCE = 3;
	private static final int BLEND_TOLERANCE = 2;

	/** Largest difference of the red, green and blue channels of two colors. */
	private static int channelDifference(int a, int b) {
		return Math.max(Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)), Math.max(Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)),
				Math.abs((a & 255) - (b & 255))));
	}

	// ------------------------------------------------------------------ image measures

	private static BufferedImage read(Path path) {
		try {
			return ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("Cannot read " + path, e);
		}
	}

	/** Pixels of the central region (a third of the width and of the height). */
	private static int[] center(Path path) {
		BufferedImage img = read(path);
		int w = img.getWidth();
		int h = img.getHeight();
		int x0 = w / 3;
		int y0 = h / 3;
		int[] px = new int[(w / 3) * (h / 3)];
		img.getRGB(x0, y0, w / 3, h / 3, px, 0, w / 3);
		return px;
	}

	private static double[] median(Path path) {
		int[] px = center(path);
		int[][] ch = new int[3][px.length];
		for (int i = 0; i < px.length; i++) {
			ch[0][i] = (px[i] >> 16) & 255;
			ch[1][i] = (px[i] >> 8) & 255;
			ch[2][i] = px[i] & 255;
		}
		double[] m = new double[3];
		for (int c = 0; c < 3; c++) {
			Arrays.sort(ch[c]);
			m[c] = ch[c][px.length / 2];
		}
		return m;
	}

	private static int median(int[] values) {
		if (values.length == 0) {
			return 0;
		}
		int[] s = values.clone();
		Arrays.sort(s);
		return s[s.length / 2];
	}

	/** Share of sky pixels (light and clearly blue) in the central region. */
	private static double sky(Path path) {
		int[] px = center(path);
		int n = 0;
		for (int p : px) {
			int r = (p >> 16) & 255;
			int g = (p >> 8) & 255;
			int b = p & 255;
			if (b >= 150 && b > r + 30 && b >= g) {
				n++;
			}
		}
		return (double) n / px.length;
	}

	/** Share of plant pixels (clearly green, against the gray stone floor) in the central region. */
	private static double plants(Path path) {
		int[] px = center(path);
		int n = 0;
		for (int p : px) {
			int r = (p >> 16) & 255;
			int g = (p >> 8) & 255;
			int b = p & 255;
			if (g > r + 12 && g > b + 12) {
				n++;
			}
		}
		return (double) n / px.length;
	}

	/** Largest difference of the chromaticity (each channel over the sum of the channels) of a pixel and a tint. */
	private static double chromaDistance(double[] pixel, int tint) {
		double[] t = {(tint >> 16) & 255, (tint >> 8) & 255, tint & 255};
		double sp = pixel[0] + pixel[1] + pixel[2];
		double st = t[0] + t[1] + t[2];
		double d = 0;
		for (int c = 0; c < 3; c++) {
			d = Math.max(d, Math.abs(pixel[c] / Math.max(1, sp) - t[c] / Math.max(1, st)));
		}
		return d;
	}

	private static String hex(int argb) {
		return String.format(Locale.ROOT, "#%06x", argb & 0xFFFFFF);
	}

	private static double[] rgbOf(int color) {
		return new double[] {(color >> 16) & 255, (color >> 8) & 255, color & 255};
	}

	private static String rgb(double[] c) {
		return String.format(Locale.ROOT, "(%.0f, %.0f, %.0f)", c[0], c[1], c[2]);
	}

	// ------------------------------------------------------------------ results

	private void result(String format, Object... args) {
		String line = String.format(Locale.ROOT, format, args);
		results.add(line);
		PolishForests.LOG.info("[probes] {}", line);
	}

	private void fail(String format, Object... args) {
		String line = String.format(Locale.ROOT, format, args);
		failures.add(line);
		results.add("FAIL " + line);
		PolishForests.LOG.error("[probes] FAIL {}", line);
	}

	private void write(String tag) {
		String dir = System.getProperty("polishforests.probes.out");
		if (dir == null || dir.isBlank()) {
			return;
		}
		try {
			Path file = Path.of(dir, tag + ".txt");
			Files.createDirectories(file.getParent());
			Files.write(file, results, StandardCharsets.UTF_8);
			PolishForests.LOG.info("[probes] results written to {}", file);
		} catch (IOException e) {
			throw new AssertionError("Cannot write the probe results", e);
		}
	}

}
