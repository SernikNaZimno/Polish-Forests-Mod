package pl.polishforests.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.client.screen.PolandPresetEditor;
import pl.polishforests.gametest.SliceCameras.Camera;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.BiomeDecoration;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.norms.ForestNorms;
import pl.polishforests.worldgen.tree.ForestStandMap;
import pl.polishforests.worldgen.surface.ChunkSurface;

/**
 * The vertical slice in the game (step V0, docs/04-pionowy-wycinek.md §3 and §5): four worlds (realistic and gameplay
 * scale, natural and present-day mode) with the fixed places of {@link SliceSites} (two oak-hornbeam forests, two fresh
 * pine forests and a transect fresh pine → mixed pine-oak → mixed → oak-hornbeam forest per scale). For each place the
 * test checks that the world still has it (the classifier of the world over the 100 × 100 block square of a site, the
 * biome of the generated chunk at the center, the order of the forests along a transect, block by block) and takes the
 * screenshots of the slice from the fixed cameras of {@link SliceCameras}.
 *
 * <p>Cameras (V0 review round 1): at a site from eye height with a free line of sight ({@code _eye}), straight up under
 * the canopy ({@code _canopy}), from 30 blocks above the ground ({@code _30m}) and close-ups of the trunk of each log
 * block of the slice ({@code _bark_<log>}, natural mode only, each log once per scale); along a transect from eye height
 * in the middle of each forest ({@code _eye_<biome>}, looking towards the oak-hornbeam end), at each border along the
 * line and across it from 35 blocks above ({@code _edge_<biome>_<biome>_eye}, {@code _side}), from 30 blocks above its
 * start ({@code _30m}) and, in the gameplay scale, the whole line from the side ({@code _overview}).
 * {@code -PfindSliceCameras} chooses the cameras anew (in the first world of each scale) and writes the table; every
 * other run uses the table, so the views before and after a step match. When a stored camera stands in a trunk or in
 * leaves (new trees of a later step), the run logs it and removes those blocks.
 *
 * <p>Screenshots {@code slice_<scale>_<mode>_<place>_<camera>}: eye height and bark at 1280 × 720 (bark texture readable),
 * the others at 854 × 480; at noon (the canopy view at time 1000, with the sun outside the frame) in clear weather, without
 * the HUD, with Serene Seasons in {@code mid_summer} (without SS the calendar of the mod; the sub-season goes to the log).
 * The log ({@code [slice]}) gets the camera, the brightness of each screenshot (mean, share of dark pixels, share of sky)
 * with a warning for an eye-height view darker than {@link #MAX_DARK}, and per world the counters of the dispatcher layers
 * ({@link ModFeatures#STATS}) and {@link ModFeatures#HABITAT_MISS} (must stay 0). The baseline of M2 is in
 * {@code docs/wycinek/baza/}.
 *
 * <p>The norms card (step V4, {@link NormsCensus}): in every world the census counts the 1 ha plot (100 × 100 blocks) of
 * each site, the sample plots of {@link SlicePlots} in natural and managed stands (step V9; with {@code -Psites} only when
 * the list holds {@code plots}) and the band of the transect and compares them with the norms of docs/04 §6 (the column
 * of the card by the stand of the stand map at the plot's center, the norms by its age class), also with
 * {@code -Psites=none}; the rows go to
 * {@code build/norms/karta-norm.csv}, a summary line per place to the log. Before that, once per run, the hand-built test
 * plot is built in the sky, read back and counted (it must agree piece by piece). The species of the M2 placeholder trees
 * come from {@code ModFeatures.WOODY_PLACEMENTS}, which the test sets while its worlds exist.
 *
 * <p>Runs only by name ({@code -Pgametest=slice}, not in {@code all}); {@code -Pscales=realistic|gameplay} picks a scale,
 * {@code -Pmodes=natural|present_day} a mode, {@code -Psites=<names>} some places (names of the table, e.g.
 * {@code oak_hornbeam_1,transect}); {@code -Psites=none} only checks the places, without screenshots.
 * {@code -Psites=samples} builds the sample board of the thin trunks instead (step V2, {@link TrunkSamples}).
 * {@code -Psites=trees} plants the board of the tree planner instead (step V6, {@link TreeSamples}).
 * {@code -Psites=leaves} builds the board of the species leaves, saplings and the LITE pine set (step V7, {@link LeafSamples}).
 * {@code -Psites=seasons} shoots the forests in the sub-seasons and checks the herb phases, the bare twigs, the falling
 * leaves and the bilberry fruit with its catch-up (step V13, {@link SeasonSamples}).
 * {@code -Psites=deadwood} builds the board of the dead wood planner and shoots its objects in the worlds (step V11,
 * {@link DeadwoodSamples}).
 * {@code -Psites=roots} builds the board of the roots and shoots the trunk bases, flares and a trench in the worlds (step
 * I1, {@link RootSamples}).
 */
public final class SliceClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int RENDER_DISTANCE = 10;
	/** Render distance for the overview of a gameplay transect, chunks: its ends stay before the fog. */
	private static final int OVERVIEW_RENDER_DISTANCE = 12;
	/** Transect rules, as in {@code SliceSitesTest}: least run of each forest, longest run and share of other biomes. */
	private static final int[] MIN_RUN = {40, 16};
	private static final int[] MAX_OTHER_RUN = {24, 12};
	private static final double MAX_OTHER = 0.15;
	private static final double MIN_SHARE = 0.95;
	/** Screenshot sizes: eye height and bark close-ups, the other views. */
	private static final int[] EYE_SIZE = {1280, 720};
	private static final int[] VIEW_SIZE = {854, 480};
	/**
	 * Time of the shots (noon) and of the canopy view. The sun angle does not grow linearly with the time of day (the
	 * celestial angle is eased towards noon): at 3000 the sun is only about 31-35° from the zenith and its disc showed in
	 * the canopy views of V0 (vertical half field of view 35°). At 1000 it is about 62° from the zenith (28° above the
	 * eastern horizon), clearly outside the frame, and the sky light is already full.
	 */
	private static final int NOON = 6000;
	private static final int CANOPY_TIME = 1000;
	/** Sun disc check of a canopy view: near-white pixels (all channels above 235) in the top band, more is a warning. */
	private static final int SUN_WHITE = 235;
	private static final double SUN_BAND = 0.15;
	private static final int SUN_MAX_PIXELS = 40;
	/** Canopy column: no logs within this horizontal radius, from the feet up to this many blocks (trunks fill the view). */
	private static final int CANOPY_TRUNK_RADIUS = 3;
	private static final int CANOPY_TRUNK_HEIGHT = 8;
	/** Vertical field of view of the screenshots (the game's default, 70°) and the reach of the trunk rays, blocks. */
	private static final double CANOPY_FOV = 70;
	private static final double CANOPY_RAY = 32;
	/** Largest share of a canopy view that trunks may fill when no column is free of logs nearby (rule of the fallback). */
	private static final double CANOPY_MAX_COVER = 0.05;
	/** Free line of sight of an eye-height camera, blocks: least accepted, longest looked for; half-angle of the cone, °. */
	private static final double MIN_RAY = 6;
	private static final double MAX_RAY = 16;
	private static final double CONE = 20;
	/** Up to {@link #NEAR} blocks from the camera the cone is ±{@link #NEAR_CONE}° wide. */
	private static final double NEAR = 3;
	private static final double NEAR_CONE = 45;
	/** Largest share of dark pixels (luma below 40) of an eye-height view without a warning. */
	private static final double MAX_DARK = 0.6;
	/** Search radii of the cameras, blocks: eye height, canopy column, trunks for the bark close-ups. */
	private static final int EYE_RADIUS = 8;
	private static final int CANOPY_RADIUS = 16;
	private static final int BARK_RADIUS = 24;
	/** Height above the ground of the views of the sample plots over the canopy and over a few hectares, blocks. */
	private static final int ABOVE_HEIGHT = 48;
	private static final int OVERVIEW_HEIGHT = 95;
	/** Yaws tried for the eye-height view of a site (30° = south-south-west first). */
	private static final double[] SITE_YAWS = {30, 75, 120, 165, 210, 255, 300, 345};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!System.getProperty("polishforests.gametest", "all").equals("slice")) {
			return;
		}
		String scales = System.getProperty("polishforests.scales", "");
		String modes = System.getProperty("polishforests.modes", "");
		String only = System.getProperty("polishforests.sites", "");
		if (only.equals("samples")) {
			TrunkSamples.run(context);
			return;
		}
		if (only.equals("trees")) {
			TreeSamples.run(context);
			return;
		}
		if (only.equals("leaves")) {
			LeafSamples.run(context);
			return;
		}
		if (only.equals("seasons")) {
			SeasonSamples.run(context);
			return;
		}
		if (only.equals("deadwood")) {
			DeadwoodSamples.run(context);
			return;
		}
		if (only.equals("roots")) {
			RootSamples.run(context);
			return;
		}
		boolean screenshots = !only.equals("none");
		boolean find = Boolean.getBoolean("polishforests.slice.findCameras");
		List<String> names = only.isBlank() || !screenshots ? List.of() : List.of(only.split(","));
		Map<String, List<Camera>> cameras = SliceCameras.read();
		Map<String, List<Camera>> found = new LinkedHashMap<>();
		boolean seasons = FabricLoader.getInstance().isModLoaded("sereneseasons");
		context.runOnClient(mc -> mc.options.renderDistance().set(RENDER_DISTANCE));
		// The species of each tree and shrub placed while the worlds of the test generate (the census of the norms card).
		Map<Long, Species> records = new ConcurrentHashMap<>();
		ModFeatures.WOODY_PLACEMENTS = (at, species) -> records.put(NormsCensus.key(at.getX(), at.getZ()), species);
		ModFeatures.DEADWOOD_PLACEMENTS = (at, plan) -> {
			if (plan.kind().standing()) {
				NormsCensus.DEAD.add(NormsCensus.key(at.getX(), at.getZ()));
			}
		};
		List<String> norms = new ArrayList<>();
		boolean[] testPlot = {false};
		try {
			worlds(context, scales, modes, names, screenshots, find, cameras, found, seasons, records, norms, testPlot);
		} finally {
			ModFeatures.WOODY_PLACEMENTS = null;
			ModFeatures.DEADWOOD_PLACEMENTS = null;
		}
		if (!norms.isEmpty()) {
			NormsCensus.write(norms);
		}
		if (find && !found.isEmpty()) {
			Map<String, List<Camera>> all = new LinkedHashMap<>(cameras);
			all.putAll(found);
			SliceCameras.write(all);
			PolishForests.LOG.info("[slice] cameras of {} places written to {}", found.size(), SliceCameras.file());
		}
	}

	private static void worlds(ClientGameTestContext context, String scales, String modes, List<String> names, boolean screenshots,
			boolean find, Map<String, List<Camera>> cameras, Map<String, List<Camera>> found, boolean seasons, Map<Long, Species> records,
			List<String> norms, boolean[] testPlot) {
		for (boolean real : new boolean[] {true, false}) {
			String scale = real ? "realistic" : "gameplay";
			if (!scales.isBlank() && !scales.contains(scale)) {
				continue;
			}
			ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id(real ? "poland" : "poland_gameplay"));
			Set<String> barks = new HashSet<>();
			for (boolean presentDay : new boolean[] {false, true}) {
				String mode = presentDay ? "present_day" : "natural";
				if (!modes.isBlank() && !modes.contains(mode)) {
					continue;
				}
				List<SliceSites.Place> places = SliceSites.of(real, presentDay).stream()
						.filter(p -> names.isEmpty() || names.contains(p.name())).toList();
				if (places.isEmpty() && !names.contains("plots")) {
					continue;
				}
				records.clear();
				NormsCensus.DEAD.clear();
				try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset, presentDay)).create()) {
					SeasonDates.checkNewWorldStart(sp, "slice");
					sp.getServer().runCommand("gamerule advance_time false");
					sp.getServer().runCommand("gamerule advance_weather false");
					sp.getServer().runCommand("gamerule spawn_mobs false");
					sp.getServer().runCommand("time set " + NOON);
					sp.getServer().runCommand("weather clear");
					sp.getServer().runCommand("gamemode spectator @a");
					// The date decides the leaf colors (V7): mid-summer with SS and on the built-in calendar alike.
					SeasonDates.set(sp, SubSeason.MID_SUMMER, 0, "slice");
					String world = sp.getServer().computeOnServer(s -> {
						PolandChunkGenerator gen = generator(s);
						gen.validate();
						if (gen.settings().agriculture() != presentDay) {
							throw new AssertionError("The world is not in the " + mode + " mode: " + gen.settings());
						}
						return gen.settings().toString();
					});
					PolishForests.LOG.info("[slice] {} {}: world {}", scale, mode, world);
					String season = sp.getServer().computeOnServer(s -> Seasons.provider().subSeason(s.overworld()).name()
							.toLowerCase(Locale.ROOT));
					ForestNorms.Aspect aspect = sp.getServer().computeOnServer(s -> ForestNorms.Aspect.of(Seasons.provider()
							.subSeason(s.overworld())));
					if (!testPlot[0]) {
						SlicePlots.Plot first = SlicePlots.of(real).getFirst();
						int[] at = places.isEmpty() ? new int[] {first.x(), first.z()} : places.get(0).middle();
						String checked = sp.getServer().computeOnServer(s -> NormsCensus.checkTestPlot(s.overworld(), at[0], at[1]));
						PolishForests.LOG.info("[slice] {} {}: {}", scale, mode, checked);
						testPlot[0] = true;
					}
					long[] before = counters();
					int[] last = null;
					for (SliceSites.Place p : places) {
						String prefix = "slice_" + scale + "_" + mode + "_" + p.name();
						NormsCensus.Context ctx = new NormsCensus.Context(scale, mode, p.name(), season, aspect);
						StringBuilder census = new StringBuilder();
						int[][] marks;
						if (p.transect()) {
							TransectCheck check = sp.getServer().computeOnServer(s -> checkTransect(s, p));
							marks = check.marks();
							ForestNorms.Forest forest = NormsCensus.forest("oak_hornbeam_forest", !presentDay);
							norms.addAll(sp.getServer().computeOnServer(s -> NormsCensus.transect(s.overworld(), ctx, forest, p.x(), p.z(),
									p.x2(), p.z2(), check.steps(), check.rank(), generator(s).settings().scale().landscape().local(), records,
									census)));
						} else {
							String report = sp.getServer().computeOnServer(s -> checkSite(s, p));
							PolishForests.LOG.info("[slice] {} {} {}: {}", scale, mode, p.name(), report);
							marks = null;
							// The column of the card by the stand at the center (step V9).
							ForestStandMap.Stand stand = sp.getServer().computeOnServer(s -> stands(s).stand(p.x(), p.z()));
							ForestNorms.Forest forest = NormsCensus.forest(p.biome(), stand.natural());
							norms.addAll(sp.getServer().computeOnServer(s -> NormsCensus.plot(s.overworld(), ctx, forest, stand.age(), p.x(), p.z(), records,
									census)));
						}
						PolishForests.LOG.info("[slice] {} {} norms card, {}", scale, mode, census);
						if (!screenshots) {
							continue;
						}
						String key = scale + " " + p.name();
						List<Camera> list;
						if (find) {
							list = found.computeIfAbsent(key, k -> sp.getServer().computeOnServer(s -> choose(s, p, marks, barks)));
						} else {
							list = cameras.get(key);
							if (list == null) {
								throw new AssertionError("No cameras for " + key + " in " + SliceCameras.file()
										+ " (choose them with -PfindSliceCameras)");
							}
						}
						for (Camera c : list) {
							// The present-day mode only along the transects (review of V9, round 1): in the forests of the slice
							// it looks as the natural mode does (0-2% of the pixels differ), so only the transect, whose fields
							// and meadows differ, is shot in both modes.
							if (presentDay && (c.kind() == SliceCameras.Kind.BARK || !p.transect())) {
								continue;
							}
							last = shot(context, sp, c, prefix + "_" + c.name(), last);
						}
					}
					// The sample plots of the norms card by stand (step V9): natural and managed stands of both forests.
					if (names.isEmpty() || names.contains("plots")) {
						for (SlicePlots.Plot plot : SlicePlots.of(real)) {
							NormsCensus.Context ctx = new NormsCensus.Context(scale, mode, plot.name(), season, aspect);
							StringBuilder census = new StringBuilder();
							ForestStandMap.Stand stand = sp.getServer().computeOnServer(s -> stands(s).stand(plot.x(), plot.z()));
							if (stand.natural() != plot.natural() || plot.age() != null && stand.age() != plot.age()) {
								throw new AssertionError(plot.name() + ": the stand at the center is " + stand + " in this world");
							}
							ForestNorms.Forest forest = NormsCensus.forest(plot.biome(), plot.natural());
							norms.addAll(sp.getServer().computeOnServer(s -> NormsCensus.plot(s.overworld(), ctx, forest, stand.age(), plot.x(),
									plot.z(), records, census)));
							PolishForests.LOG.info("[slice] {} {} norms card, {}", scale, mode, census);
							// Views of the plots (review of V9, round 1), in the natural mode only (the slice forests look the same
							// in both modes): chosen in each run from the world, as the plots are.
							if (screenshots && !presentDay) {
								List<Camera> views = sp.getServer().computeOnServer(s -> plotCameras(s, plot));
								for (Camera c : views) {
									last = shot(context, sp, c, "slice_" + scale + "_" + mode + "_" + plot.name() + "_" + c.name(), last);
								}
							}
						}
					}
					String provider = sp.getServer().computeOnServer(s -> Seasons.provider().name() + " "
							+ Seasons.provider().subSeason(s.overworld()));
					long[] after = counters();
					PolishForests.LOG.info("[slice] {} {}: season {}; {}", scale, mode, provider, layers(before, after));
					if (after[after.length - 1] != before[before.length - 1]) {
						throw new AssertionError(scale + " " + mode + ": habitats missing in " + (after[after.length - 1] - before[before.length - 1])
								+ " chunks (HABITAT_MISS)");
					}
				}
			}
		}
	}

	/** The stand map of the world (the seed, the scale's k and the managed share of its settings). */
	private static ForestStandMap stands(MinecraftServer server) {
		PolandChunkGenerator gen = generator(server);
		return new ForestStandMap(server.overworld().getSeed(), gen.settings().scale().landscape().local(),
				gen.settings().managedForestShare());
	}

	// ------------------------------------------------------------------ checks

	/** The 100 × 100 square of a site holds its biome (classifier of the world) and the generated chunk has it at the center. */
	private static String checkSite(MinecraftServer server, SliceSites.Place p) {
		PolandChunkGenerator gen = generator(server);
		long seed = server.overworld().getSeed();
		LandscapeModel m = gen.model(seed);
		HabitatClassifier k = gen.classifier(seed);
		int hits = 0;
		for (int i = 0; i < 21; i++) {
			for (int j = 0; j < 21; j++) {
				int x = p.x() - 50 + 5 * i;
				int z = p.z() - 50 + 5 * j;
				if (Habitat.biome(k.classify(m.sample(x, z), x, z)).id().equals(p.biome())) {
					hits++;
				}
			}
		}
		double share = hits / 441.0;
		if (share < MIN_SHARE) {
			throw new AssertionError(String.format(Locale.ROOT, "%s: %s only %.1f%% of the 100 x 100 square", p.name(), p.biome(), 100 * share));
		}
		ServerLevel level = server.overworld();
		int y = new Ground(server).y(p.x(), p.z());
		String biome = level.getBiome(new BlockPos(p.x(), y + 1, p.z())).getRegisteredName();
		if (!biome.equals(PolishForests.MOD_ID + ":" + p.biome())) {
			throw new AssertionError(p.name() + ": getBiome at the center is " + biome + ", expected " + p.biome());
		}
		return String.format(Locale.ROOT, "%s at (%d, %d), ground Y %d, %.1f%% of the square", p.biome(), p.x(), p.z(), y, 100 * share);
	}

	/**
	 * A checked transect: the middle {x, z} of the stretch of each forest (0–3) and the {x, z} of the three borders
	 * between them (4–6); the number of steps of the line and the forest of each step (0–3, -1 another biome).
	 */
	record TransectCheck(int[][] marks, int steps, int[] rank) {
	}

	/** The columns of the transect pass the four forests in order (rules of {@code SliceSitesTest}). */
	private static TransectCheck checkTransect(MinecraftServer server, SliceSites.Place p) {
		PolandChunkGenerator gen = generator(server);
		long seed = server.overworld().getSeed();
		LandscapeModel m = gen.model(seed);
		HabitatClassifier k = gen.classifier(seed);
		int s = p.realistic() ? 0 : 1;
		int n = (int) Math.ceil(Math.hypot(p.x2() - p.x(), p.z2() - p.z()));
		int[] firstOf = {-1, -1, -1, -1};
		int[] lastOf = new int[4];
		int[] run = new int[4];
		int rank = 0;
		int other = 0;
		int otherRun = 0;
		int[][] at = new int[n + 1][];
		int[] ranks = new int[n + 1];
		for (int i = 0; i <= n; i++) {
			double x = Math.floor(p.x() + (p.x2() - p.x()) * (double) i / n) + 0.5;
			double z = Math.floor(p.z() + (p.z2() - p.z()) * (double) i / n) + 0.5;
			at[i] = new int[] {(int) Math.floor(x), (int) Math.floor(z)};
			String b = Habitat.biome(k.classify(m.sample(x, z), x, z)).id();
			int r = SliceSites.SEQUENCE.indexOf(b);
			ranks[i] = r;
			if (r < 0) {
				other++;
				if (++otherRun > MAX_OTHER_RUN[s]) {
					throw new AssertionError(p.name() + ": more than " + MAX_OTHER_RUN[s] + " blocks of other biomes at step " + i);
				}
				continue;
			}
			otherRun = 0;
			if (r < rank) {
				throw new AssertionError(p.name() + ": back from " + SliceSites.SEQUENCE.get(rank) + " to " + b + " at step " + i);
			}
			rank = r;
			run[r]++;
			if (firstOf[r] < 0) {
				firstOf[r] = i;
			}
			lastOf[r] = i;
		}
		StringBuilder report = new StringBuilder();
		int[][] marks = new int[7][];
		for (int r = 0; r < 4; r++) {
			if (run[r] < MIN_RUN[s]) {
				throw new AssertionError(p.name() + ": " + SliceSites.SEQUENCE.get(r) + " only " + run[r] + " blocks");
			}
			marks[r] = at[(firstOf[r] + lastOf[r]) / 2];
			report.append(String.format(Locale.ROOT, " %s %d blocks (middle %d, %d);", SliceSites.SEQUENCE.get(r), run[r], marks[r][0],
					marks[r][1]));
		}
		for (int r = 0; r < 3; r++) {
			marks[4 + r] = at[(lastOf[r] + firstOf[r + 1]) / 2];
			report.append(String.format(Locale.ROOT, " border %d at (%d, %d);", r + 1, marks[4 + r][0], marks[4 + r][1]));
		}
		if (other > MAX_OTHER * (n + 1)) {
			throw new AssertionError(String.format(Locale.ROOT, "%s: %.1f%% other biomes", p.name(), 100.0 * other / (n + 1)));
		}
		PolishForests.LOG.info(String.format(Locale.ROOT, "[slice] %s %s: (%d, %d) -> (%d, %d), %d blocks:%s %d other",
				p.realistic() ? "realistic" : "gameplay", p.name(), p.x(), p.z(), p.x2(), p.z2(), n + 1, report, other));
		return new TransectCheck(marks, n, ranks);
	}

	// ------------------------------------------------------------------ the views of the sample plots

	/**
	 * The views of a sample plot of the norms card (review of V9, round 1: the V0 cameras were chosen for the M2 forest):
	 * from eye height at the center ({@code eye}), from {@value #ABOVE_HEIGHT} blocks above the ground 50 blocks back,
	 * looking at the center over the canopy ({@code above}); in a natural stand also from eye height in its deepest canopy
	 * gap ({@code gap_eye}) and from {@value #OVERVIEW_HEIGHT} blocks above 110 blocks back, so the spread of the gaps over
	 * a few hectares shows ({@code overview}).
	 */
	private static List<Camera> plotCameras(MinecraftServer server, SlicePlots.Plot plot) {
		Ground g = new Ground(server);
		List<Camera> list = new ArrayList<>();
		Camera eye = eyeCamera(g, "eye", plot.x(), plot.z(), SITE_YAWS, EYE_RADIUS);
		list.add(eye);
		// Step V12: the forest floor from the eye height, looking down (herb layer, moss and litter close up), and the
		// base of a canopy tree with root flares from 3 blocks (moss at the base and along the flares).
		list.add(new Camera("floor_eye", eye.x(), eye.y(), eye.z(), eye.yaw(), FLOOR_PITCH));
		Camera base = baseCamera(g, plot.x(), plot.z());
		if (base != null) {
			list.add(base);
		}
		double yaw = Math.toRadians(30);
		list.add(aerial(g, "above", (int) Math.round(plot.x() + 50 * Math.sin(yaw)), (int) Math.round(plot.z() - 50 * Math.cos(yaw)),
				ABOVE_HEIGHT, plot.x(), plot.z()));
		if (plot.natural()) {
			ForestStandMap map = stands(server);
			int bx = plot.x();
			int bz = plot.z();
			double best = 0;
			for (int dx = -48; dx <= 48; dx += 2) {
				for (int dz = -48; dz <= 48; dz += 2) {
					double d = map.gapDepth(plot.x() + dx, plot.z() + dz);
					if (d > best) {
						best = d;
						bx = plot.x() + dx;
						bz = plot.z() + dz;
					}
				}
			}
			if (best > 0) {
				list.add(eyeCamera(g, "gap_eye", bx, bz, SITE_YAWS, 3));
			}
			list.add(aerial(g, "overview", (int) Math.round(plot.x() + 110 * Math.sin(yaw)),
					(int) Math.round(plot.z() - 110 * Math.cos(yaw)), OVERVIEW_HEIGHT, plot.x(), plot.z()));
		}
		for (Camera c : list) {
			PolishForests.LOG.info("[slice] {}: chosen {}", plot.name(), c);
		}
		return list;
	}

	/** Pitch of the view of the forest floor, degrees down (step V12). */
	private static final double FLOOR_PITCH = 40;

	/**
	 * A view of a trunk base with root flares (step V12): the trunk with the most flare blocks around its base within
	 * {@value #EYE_RADIUS} × 2 blocks of the point, seen from the eye height of a free column 3 blocks from it, looking
	 * at the base; null when there is none.
	 */
	private static Camera baseCamera(Ground g, int x, int z) {
		int bestFlares = 2;
		int bx = 0;
		int bz = 0;
		int by = 0;
		int r = 2 * EYE_RADIUS;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int y = g.y(x + dx, z + dz) + 1;
				if (!g.state(x + dx, y, z + dz).is(BlockTags.LOGS)) {
					continue;
				}
				int flares = 0;
				for (int ax = -1; ax <= 1; ax++) {
					for (int az = -1; az <= 1; az++) {
						flares += g.state(x + dx + ax, y, z + dz + az).getBlock() instanceof pl.polishforests.block.RootFlareBlock ? 1 : 0;
					}
				}
				if (flares > bestFlares) {
					bestFlares = flares;
					bx = x + dx;
					bz = z + dz;
					by = y;
				}
			}
		}
		if (bestFlares <= 2) {
			return null;
		}
		for (double yaw : SITE_YAWS) {
			double a = Math.toRadians(yaw);
			int cx = (int) Math.round(bx + 3 * Math.sin(a));
			int cz = (int) Math.round(bz - 3 * Math.cos(a));
			int cy = g.y(cx, cz) + 1;
			if (g.free(cx, cy, cz) && g.free(cx, cy + 1, cz) && Math.abs(cy - by) <= 1) {
				double look = Math.toDegrees(Math.atan2(-(bx - cx), bz - cz));
				double pitch = Math.toDegrees(Math.atan2(cy + 1.62 - (by + 0.3), Math.hypot(bx - cx, bz - cz)));
				return new Camera("base_eye", cx, cy, cz, round1(look), round1(pitch));
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ choosing the cameras (-PfindSliceCameras)

	/** The cameras of a place, chosen in this world; {@code barks} are the logs with a close-up in this scale already. */
	private static List<Camera> choose(MinecraftServer server, SliceSites.Place p, int[][] marks, Set<String> barks) {
		Ground g = new Ground(server);
		List<Camera> list = new ArrayList<>();
		if (!p.transect()) {
			Camera eye = eyeCamera(g, "eye", p.x(), p.z(), SITE_YAWS, EYE_RADIUS);
			list.add(eye);
			list.add(canopyCamera(g, p.x(), p.z(), eye));
			// 30 blocks above the ground, 30 blocks back from the center against the view of yaw 30, looking at the center.
			double yaw = 30;
			int cx = (int) Math.round(p.x() + 30 * Math.sin(Math.toRadians(yaw)));
			int cz = (int) Math.round(p.z() - 30 * Math.cos(Math.toRadians(yaw)));
			list.add(aerial(g, "30m", cx, cz, 30, p.x(), p.z()));
			list.addAll(barkCameras(g, p.x(), p.z(), barks));
		} else {
			double len = Math.hypot(p.x2() - p.x(), p.z2() - p.z());
			double ux = (p.x2() - p.x()) / len;
			double uz = (p.z2() - p.z()) / len;
			double[] along = {yawOf(ux, uz)};
			for (int r = 0; r < 4; r++) {
				list.add(eyeCamera(g, "eye_" + SliceSites.SEQUENCE.get(r), marks[r][0], marks[r][1], along, EYE_RADIUS));
			}
			// Each border: along the line from 12 (gameplay 10) blocks before it, and across it from 45 blocks aside, 35 above.
			int back = p.realistic() ? 12 : 10;
			for (int r = 0; r < 3; r++) {
				int[] b = marks[4 + r];
				String edge = "edge_" + SliceSites.SEQUENCE.get(r) + "_" + SliceSites.SEQUENCE.get(r + 1);
				list.add(eyeCamera(g, edge + "_eye", (int) Math.round(b[0] - back * ux), (int) Math.round(b[1] - back * uz), along, 6));
				list.add(aerial(g, edge + "_side", (int) Math.round(b[0] - 45 * uz), (int) Math.round(b[1] + 45 * ux), 35, b[0], b[1]));
			}
			// From 30 blocks above the start, looking along the line, down to the ground 80 blocks ahead (gameplay 40).
			int ground = g.y(p.x(), p.z());
			double ahead = Math.min(p.realistic() ? 80 : 40, len / 2);
			int y = clearAbove(g, p.x(), ground + 30 + 1, p.z());
			list.add(new Camera("30m", p.x(), y, p.z(), along[0], round1(Math.toDegrees(Math.atan2(y + 1.62 - (ground + 1), ahead)))));
			if (!p.realistic()) {
				// The whole gameplay transect from the side: 80 blocks from its middle, 70 above the ground.
				int[] m = p.middle();
				list.add(aerial(g, "overview", (int) Math.round(m[0] - 80 * uz), (int) Math.round(m[1] + 80 * ux), 70, m[0], m[1]));
			}
		}
		for (Camera c : list) {
			PolishForests.LOG.info("[slice] {} {}: chosen {}", p.realistic() ? "realistic" : "gameplay", p.name(), c);
		}
		return list;
	}

	/**
	 * Eye height near (x, z): of the columns up to {@code radius} blocks away whose feet and head blocks are free, and the
	 * given yaws, the one with the longest free line of sight ({@link #ray}, up to {@link #MAX_RAY}), a little in favor of
	 * columns near (x, z).
	 */
	private static Camera eyeCamera(Ground g, String name, int x, int z, double[] yaws, int radius) {
		Camera best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		double bestRay = 0;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int cx = x + dx;
				int cz = z + dz;
				int y = g.y(cx, cz) + 1;
				if (!g.free(cx, y, cz) || !g.free(cx, y + 1, cz)) {
					continue;
				}
				for (double yaw : yaws) {
					double ray = ray(g, cx, y, cz, yaw);
					double score = ray - 0.1 * Math.hypot(dx, dz);
					if (score > bestScore) {
						bestScore = score;
						bestRay = ray;
						best = new Camera(name, cx, y, cz, round1(yaw), 0);
					}
				}
			}
		}
		if (best == null) {
			PolishForests.LOG.warn("[slice] {}: no free column within {} blocks of ({}, {})", name, radius, x, z);
			return new Camera(name, x, g.y(x, z) + 1, z, yaws[0], 0);
		}
		if (bestRay < MIN_RAY) {
			PolishForests.LOG.warn("[slice] {}: the longest free line of sight near ({}, {}) is only {} blocks", name, x, z, bestRay);
		}
		return best;
	}

	/**
	 * Free line of sight from the feet block (x, y, z) along the yaw, blocks: how far a cone of ±{@link #CONE}° (±{@link
	 * #NEAR_CONE}° up to {@link #NEAR} blocks) holds no trunk, leaves or solid block at the feet and eye height (and the
	 * block above the head up to 4 blocks ahead).
	 */
	private static double ray(Ground g, int x, int y, int z, double yaw) {
		for (double d = 1; d <= MAX_RAY; d += 0.5) {
			// Near the camera a wider cone: a trunk 2 blocks aside fills the edge of the frame (horizontal field ±51°).
			double cone = d <= NEAR ? NEAR_CONE : CONE;
			for (double a = -cone; a <= cone; a += cone / (d <= NEAR ? 3 : 2)) {
				double r = Math.toRadians(yaw + a);
				int bx = (int) Math.floor(x + 0.5 - Math.sin(r) * d);
				int bz = (int) Math.floor(z + 0.5 + Math.cos(r) * d);
				for (int dy = 0; dy <= (d <= 4 ? 2 : 1); dy++) {
					if (!g.free(bx, y + dy, bz)) {
						return d - 0.5;
					}
				}
			}
		}
		return MAX_RAY;
	}

	/**
	 * Straight up under the canopy: the nearest column (up to {@link #CANOPY_RADIUS} blocks) with 5 free blocks from the
	 * feet up (the lowest leaves at least 4 blocks above the head), leaves within 30 blocks above it and above the four
	 * columns 2 blocks away, and no logs within {@link #CANOPY_TRUNK_RADIUS} blocks from the feet up to
	 * {@link #CANOPY_TRUNK_HEIGHT} blocks (a trunk next to the camera filled 10-30% of the V0 canopy views, which compare
	 * canopy closure). When no column is free of trunks, the nearest one whose view trunks fill at most
	 * {@link #CANOPY_MAX_COVER} is taken ({@link #trunkCover}), else the one they fill least (logged). The fewest log blocks
	 * in the cylinder still left a trunk over about a quarter of the V1 GAMEPLAY fresh_pine_2 view, because a trunk 3-5
	 * blocks away fills a wide wedge of an upward view; the least cover alone picks the densest canopy around (no sky).
	 * Yaw 90 puts the morning sun of {@link #CANOPY_TIME} outside the frame.
	 */
	private static Camera canopyCamera(Ground g, int x, int z, Camera eye) {
		List<int[]> columns = new ArrayList<>();
		for (int dx = -CANOPY_RADIUS; dx <= CANOPY_RADIUS; dx++) {
			for (int dz = -CANOPY_RADIUS; dz <= CANOPY_RADIUS; dz++) {
				if (dx * dx + dz * dz <= CANOPY_RADIUS * CANOPY_RADIUS) {
					columns.add(new int[] {dx, dz});
				}
			}
		}
		columns.sort(Comparator.<int[]>comparingInt(c -> c[0] * c[0] + c[1] * c[1]).thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
		Camera fewest = null;
		double leastCover = Double.MAX_VALUE;
		for (int[] c : columns) {
			int cx = x + c[0];
			int cz = z + c[1];
			int y = g.y(cx, cz) + 1;
			boolean ok = true;
			for (int dy = 0; dy < 5 && ok; dy++) {
				ok = g.free(cx, y + dy, cz);
			}
			ok = ok && g.leaves(cx, y + 5, y + 30, cz) && g.leaves(cx + 2, y + 3, y + 30, cz) && g.leaves(cx - 2, y + 3, y + 30, cz)
					&& g.leaves(cx, y + 3, y + 30, cz + 2) && g.leaves(cx, y + 3, y + 30, cz - 2);
			if (!ok) {
				continue;
			}
			int logs = g.logsAround(cx, y, y + CANOPY_TRUNK_HEIGHT, cz, CANOPY_TRUNK_RADIUS);
			if (logs == 0) {
				return new Camera("canopy", cx, y, cz, 90, -90);
			}
			double cover = trunkCover(g, cx, y, cz);
			if (cover <= CANOPY_MAX_COVER) {
				PolishForests.LOG.warn("[slice] canopy: every column within {} blocks of ({}, {}) nearer than ({}, {}) has logs within {} "
						+ "blocks; taken ({}, {}), trunks fill {}% of its view", CANOPY_RADIUS, x, z, cx, cz, CANOPY_TRUNK_RADIUS, cx, cz,
						String.format(Locale.ROOT, "%.0f", 100 * cover));
				return new Camera("canopy", cx, y, cz, 90, -90);
			}
			if (cover < leastCover) {
				leastCover = cover;
				fewest = new Camera("canopy", cx, y, cz, 90, -90);
			}
		}
		if (fewest != null) {
			PolishForests.LOG.warn("[slice] canopy: every column under the canopy within {} blocks of ({}, {}) has logs within {} blocks; "
					+ "taken ({}, {}), trunks fill {}% of its view", CANOPY_RADIUS, x, z, CANOPY_TRUNK_RADIUS, fewest.x(), fewest.z(),
					String.format(Locale.ROOT, "%.0f", 100 * leastCover));
			return fewest;
		}
		PolishForests.LOG.warn("[slice] canopy: no column under the canopy within {} blocks of ({}, {}), the eye column", CANOPY_RADIUS, x, z);
		return new Camera("canopy", eye.x(), eye.y(), eye.z(), 90, -90);
	}

	/**
	 * Share of a straight-up view from the feet column (x, y, z) that trunks fill: rays on a 24 × 14 grid over the frame
	 * (16:9, vertical field of view {@link #CANOPY_FOV}°, yaw 90, so the wide side of the frame runs along z) are
	 * followed for up to {@link #CANOPY_RAY} blocks; a ray counts when the first block it meets that is not free is a
	 * log (leaves, solid blocks and the open sky do not).
	 */
	private static double trunkCover(Ground g, int x, int y, int z) {
		double ex = x + 0.5;
		double ey = y + 1.62;
		double ez = z + 0.5;
		double tv = Math.tan(Math.toRadians(CANOPY_FOV / 2));
		double th = tv * 16 / 9.0;
		int hits = 0;
		int rays = 0;
		for (int i = 0; i < 24; i++) {
			for (int j = 0; j < 14; j++) {
				double u = ((i + 0.5) / 24 * 2 - 1) * th;
				double v = ((j + 0.5) / 14 * 2 - 1) * tv;
				double len = Math.sqrt(1 + u * u + v * v);
				rays++;
				for (double s = 0; s < CANOPY_RAY; s += 0.25) {
					int bx = (int) Math.floor(ex + v / len * s);
					int by = (int) Math.floor(ey + s / len);
					int bz = (int) Math.floor(ez + u / len * s);
					if (!g.free(bx, by, bz)) {
						if (g.state(bx, by, bz).is(BlockTags.LOGS)) {
							hits++;
						}
						break;
					}
				}
			}
		}
		return (double) hits / rays;
	}

	/**
	 * A view from {@code height} blocks above the ground at (x, z) (or above the target, whichever is higher), looking at
	 * the ground of (tx, tz); a column up to 3 blocks aside when the head would be in leaves.
	 */
	private static Camera aerial(Ground g, String name, int x, int z, int height, int tx, int tz) {
		int target = g.y(tx, tz) + 1;
		int y = Math.max(g.y(x, z), target - 1) + height;
		int cx = x;
		int cz = z;
		search:
		for (int r = 0; r <= 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == r && g.clearHead(x + dx, y, z + dz)) {
						cx = x + dx;
						cz = z + dz;
						break search;
					}
				}
			}
		}
		if (!g.clearHead(cx, y, cz)) {
			PolishForests.LOG.warn("[slice] {}: leaves around the camera at ({}, {}, {}), cleared when shot", name, cx, y, cz);
		}
		double yaw = yawOf(tx - cx, tz - cz);
		double pitch = Math.toDegrees(Math.atan2(y + 1.62 - target, Math.hypot(tx - cx, tz - cz)));
		return new Camera(name, cx, y, cz, round1(yaw), round1(pitch));
	}

	/**
	 * Close-ups of the trunks near a site: per log block not yet in {@code barks}, the nearest standing trunk (three log
	 * blocks of axis Y above the ground) up to {@link #BARK_RADIUS} blocks away, from about 3 blocks at eye height.
	 */
	private static List<Camera> barkCameras(Ground g, int x, int z, Set<String> barks) {
		Map<String, int[]> nearest = new LinkedHashMap<>();
		Map<String, Double> distance = new HashMap<>();
		for (int dx = -BARK_RADIUS; dx <= BARK_RADIUS; dx++) {
			for (int dz = -BARK_RADIUS; dz <= BARK_RADIUS; dz++) {
				int tx = x + dx;
				int tz = z + dz;
				int ground = g.y(tx, tz);
				BlockState s = g.state(tx, ground + 1, tz);
				if (!trunk(s) || g.state(tx, ground + 2, tz).getBlock() != s.getBlock() || g.state(tx, ground + 3, tz).getBlock() != s.getBlock()) {
					continue;
				}
				String log = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
				double d = Math.hypot(dx, dz);
				if (!barks.contains(log) && d < distance.getOrDefault(log, Double.MAX_VALUE)) {
					distance.put(log, d);
					nearest.put(log, new int[] {tx, ground, tz});
				}
			}
		}
		List<Camera> list = new ArrayList<>();
		List<String> logs = new ArrayList<>(nearest.keySet());
		logs.sort(null);
		int[][] offsets = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}};
		for (String log : logs) {
			int[] t = nearest.get(log);
			for (int[] o : offsets) {
				int cx = t[0] + o[0];
				int cz = t[2] + o[1];
				int y = g.y(cx, cz) + 1;
				if (Math.abs(y - (t[1] + 1)) > 1 || !g.free(cx, y, cz) || !g.free(cx, y + 1, cz)) {
					continue;
				}
				boolean sight = true;
				for (double f : new double[] {1 / 3.0, 2 / 3.0}) {
					int bx = (int) Math.round(cx + (t[0] - cx) * f);
					int bz = (int) Math.round(cz + (t[2] - cz) * f);
					sight = sight && g.free(bx, y, bz) && g.free(bx, y + 1, bz);
				}
				if (sight) {
					double pitch = Math.toDegrees(Math.atan2(y + 1.62 - (t[1] + 3.0), Math.hypot(t[0] - cx, t[2] - cz)));
					list.add(new Camera("bark_" + log, cx, y, cz, round1(yawOf(t[0] - cx, t[2] - cz)), round1(pitch)));
					barks.add(log);
					break;
				}
			}
		}
		return list;
	}

	private static boolean trunk(BlockState s) {
		return s.is(BlockTags.LOGS) && s.hasProperty(BlockStateProperties.AXIS) && s.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
	}

	/** Lowest feet Y from {@code from} at (x, z) with a clear head ({@link Ground#clearHead}). */
	private static int clearAbove(Ground g, int x, int from, int z) {
		for (int y = from; y < from + 64; y++) {
			if (g.clearHead(x, y, z)) {
				return y;
			}
		}
		return from + 64;
	}

	/** Minecraft yaw of the direction (dx, dz): 0 faces south (+z), 90 west (−x). */
	private static double yawOf(double dx, double dz) {
		return round1(Math.toDegrees(Math.atan2(-dx, dz)));
	}

	private static double round1(double v) {
		return Math.round(v * 10) / 10.0;
	}

	// ------------------------------------------------------------------ screenshots

	/**
	 * Takes the screenshot of a camera: removes trunks and leaves the camera stands in (logged), waits for the terrain (30
	 * seconds at a new place, 3 near the last camera), logs the line of sight of an eye-height view and the brightness of
	 * the image. Returns the position of this camera.
	 */
	private static int[] shot(ClientGameTestContext context, TestSingleplayerContext sp, Camera c, String screenshot, int[] last) {
		SliceCameras.Kind kind = c.kind();
		String prepared = sp.getServer().computeOnServer(s -> prepare(s, c));
		boolean overview = c.name().equals("overview");
		if (overview) {
			context.runOnClient(mc -> mc.options.renderDistance().set(OVERVIEW_RENDER_DISTANCE));
		}
		if (kind == SliceCameras.Kind.CANOPY) {
			// Keeps the date (V7 review round 2): a plain 'time set' moves the built-in calendar to day 0, early spring.
			SeasonDates.setTimeOfDay(sp, CANOPY_TIME, "slice");
		}
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f %.1f %.1f", c.x() + 0.5, c.y(), c.z() + 0.5, c.yaw(), c.pitch()));
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		boolean near = last != null && Math.hypot(c.x() - last[0], c.z() - last[1]) < 64 && !overview;
		int wait = near ? 3 : 30;
		context.waitTicks(20 * wait);
		PolishForests.LOG.info("[slice] {}: camera ({}, {}, {}) yaw {} pitch {}{}, client {}", screenshot, c.x(), c.y(), c.z(),
				String.format(Locale.ROOT, "%.1f", c.yaw()), String.format(Locale.ROOT, "%.1f", c.pitch()), prepared,
				context.computeOnClient(mc -> mc.level.getChunkSource().gatherStats()));
		context.waitTicks(20 * Math.min(10, wait));
		int[] size = kind == SliceCameras.Kind.EYE || kind == SliceCameras.Kind.BARK ? EYE_SIZE : VIEW_SIZE;
		Path path = context.takeScreenshot(TestScreenshotOptions.of(screenshot).disableCounterPrefix().withSize(size[0], size[1]));
		double[] st = brightness(path);
		PolishForests.LOG.info(String.format(Locale.ROOT, "[slice] %s: mean luma %.0f, dark %.0f%%, sky %.0f%%%s", screenshot, st[0], 100 * st[1],
				100 * st[2], kind == SliceCameras.Kind.CANOPY ? String.format(Locale.ROOT, ", sun pixels %.0f", st[3]) : ""));
		if (kind == SliceCameras.Kind.CANOPY && st[3] > SUN_MAX_PIXELS) {
			PolishForests.LOG.warn(String.format(Locale.ROOT, "[slice] %s: %.0f near-white pixels in the top %.0f%% of the view, the sun is "
					+ "probably in the frame", screenshot, st[3], 100 * SUN_BAND));
		}
		if (kind == SliceCameras.Kind.EYE && st[1] > MAX_DARK) {
			PolishForests.LOG.warn(String.format(Locale.ROOT, "[slice] %s: %.0f%% dark pixels (more than %.0f%%), something blocks the view",
					screenshot, 100 * st[1], 100 * MAX_DARK));
		}
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		if (kind == SliceCameras.Kind.CANOPY) {
			SeasonDates.setTimeOfDay(sp, NOON, "slice");
		}
		if (overview) {
			context.runOnClient(mc -> mc.options.renderDistance().set(RENDER_DISTANCE));
		}
		return new int[] {c.x(), c.z()};
	}

	/**
	 * Before a shot: the camera must not stand in a trunk, leaves or a solid block (new trees of a later step); such blocks
	 * at the feet and head (around the head of a view from above) are removed and logged. Returns a note for the log: the
	 * free line of sight of an eye-height view, a ground that moved, the removed blocks.
	 */
	private static String prepare(MinecraftServer server, Camera c) {
		Ground g = new Ground(server);
		ServerLevel level = server.overworld();
		StringBuilder note = new StringBuilder();
		List<BlockPos> blocked = new ArrayList<>();
		if (c.kind() == SliceCameras.Kind.AERIAL) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = 0; dy <= 2; dy++) {
						if (!g.free(c.x() + dx, c.y() + dy, c.z() + dz)) {
							blocked.add(new BlockPos(c.x() + dx, c.y() + dy, c.z() + dz));
						}
					}
				}
			}
		} else {
			int ground = g.y(c.x(), c.z());
			if (ground + 1 != c.y()) {
				note.append(String.format(Locale.ROOT, ", ground Y %d instead of %d", ground, c.y() - 1));
			}
			for (int dy = 0; dy <= 1; dy++) {
				if (!g.free(c.x(), c.y() + dy, c.z())) {
					blocked.add(new BlockPos(c.x(), c.y() + dy, c.z()));
				}
			}
		}
		for (BlockPos pos : blocked) {
			note.append(", removed ").append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath()).append(" at ")
					.append(pos.toShortString());
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		if (!blocked.isEmpty()) {
			PolishForests.LOG.warn("[slice] camera {} at ({}, {}, {}) stood in {} blocks, removed", c.name(), c.x(), c.y(), c.z(), blocked.size());
		}
		if (c.kind() == SliceCameras.Kind.EYE) {
			double ray = ray(g, c.x(), c.y(), c.z(), c.yaw());
			note.append(String.format(Locale.ROOT, ", line of sight %.1f blocks", ray));
			if (ray < MIN_RAY) {
				PolishForests.LOG.warn("[slice] camera {}: line of sight only {} blocks", c.name(), ray);
			}
		}
		return note.toString();
	}

	/**
	 * {mean luma, share of pixels with luma below 40, share of sky-blue pixels, near-white pixels in the top
	 * {@link #SUN_BAND} of the image (the sun disc and its glow in a view up)} of a screenshot.
	 */
	private static double[] brightness(Path path) {
		BufferedImage img;
		try {
			img = ImageIO.read(path.toFile());
		} catch (IOException e) {
			throw new AssertionError("Cannot read " + path, e);
		}
		long n = (long) img.getWidth() * img.getHeight();
		double sum = 0;
		long dark = 0;
		long sky = 0;
		long white = 0;
		int band = (int) Math.round(img.getHeight() * SUN_BAND);
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int rgb = img.getRGB(x, y);
				int r = rgb >> 16 & 255;
				int gr = rgb >> 8 & 255;
				int b = rgb & 255;
				double l = 0.299 * r + 0.587 * gr + 0.114 * b;
				sum += l;
				dark += l < 40 ? 1 : 0;
				sky += b > 140 && b > r + 20 && b >= gr ? 1 : 0;
				white += y < band && r > SUN_WHITE && gr > SUN_WHITE && b > SUN_WHITE ? 1 : 0;
			}
		}
		return new double[] {sum / n, (double) dark / n, (double) sky / n, white};
	}

	// ------------------------------------------------------------------ helpers

	/** Ground and blocks of the overworld, with the surface plans of the chunks kept for one search. */
	private static final class Ground {
		private final MinecraftServer server;
		private final ServerLevel level;
		private final Map<Long, ChunkSurface> plans = new HashMap<>();

		Ground(MinecraftServer server) {
			this.server = server;
			this.level = server.overworld();
		}

		/** Top ground block (or water) of the surface plan at (x, z); generates the chunk. */
		int y(int x, int z) {
			int cx = x >> 4;
			int cz = z >> 4;
			ChunkSurface plan = plans.computeIfAbsent((long) cx << 32 | cz & 0xFFFFFFFFL, k -> {
				level.getChunk(cx, cz);
				return generator(server).surface(new ChunkPos(cx, cz), level.getMinY(), level.getMaxY(), level.getSeed(), null,
						level.structureManager());
			});
			int i = ChunkHabitats.index(x & 15, z & 15);
			return plan.wet(i) ? plan.waterTop(i) : plan.top(i);
		}

		BlockState state(int x, int y, int z) {
			return level.getBlockState(new BlockPos(x, y, z));
		}

		boolean free(int x, int y, int z) {
			BlockState s = state(x, y, z);
			return !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && !s.isSolidRender();
		}

		/** Leaves in the column (x, z) from {@code from} to {@code to}. */
		boolean leaves(int x, int from, int to, int z) {
			for (int y = from; y <= to; y++) {
				if (state(x, y, z).is(BlockTags.LEAVES)) {
					return true;
				}
			}
			return false;
		}

		/** Log blocks within {@code radius} blocks (horizontally, Euclidean) of the column (x, z), from {@code from} to {@code to}. */
		int logsAround(int x, int from, int to, int z, int radius) {
			int n = 0;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dz * dz > radius * radius) {
						continue;
					}
					for (int y = from; y <= to; y++) {
						if (state(x + dx, y, z + dz).is(BlockTags.LOGS)) {
							n++;
						}
					}
				}
			}
			return n;
		}

		/** The 3 × 3 blocks around the feet, the head and above the head hold no leaves, logs or solid blocks. */
		boolean clearHead(int x, int y, int z) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = 0; dy <= 2; dy++) {
						if (!free(x + dx, y + dy, z + dz)) {
							return false;
						}
					}
				}
			}
			return true;
		}
	}

	private static PolandChunkGenerator generator(MinecraftServer server) {
		if (!(server.overworld().getChunkSource().getGenerator() instanceof PolandChunkGenerator gen)) {
			throw new AssertionError("The overworld does not use the Poland generator");
		}
		return gen;
	}

	/** Counters of the dispatcher layers (nanoseconds, chunks, placements per layer), then {@link ModFeatures#HABITAT_MISS}. */
	private static long[] counters() {
		BiomeDecoration.Dispatcher[] ds = BiomeDecoration.Dispatcher.values();
		long[] c = new long[ds.length * 3 + 3];
		for (BiomeDecoration.Dispatcher d : ds) {
			ModFeatures.LayerStats st = ModFeatures.STATS.get(d);
			c[d.ordinal() * 3] = st.nanos.sum();
			c[d.ordinal() * 3 + 1] = st.chunks.sum();
			c[d.ordinal() * 3 + 2] = st.placed.sum();
		}
		ModFeatures.LayerStats trees = ModFeatures.STATS.get(BiomeDecoration.Dispatcher.TREE_STAND);
		c[c.length - 3] = trees.wood.sum();
		c[c.length - 2] = trees.leaves.sum();
		c[c.length - 1] = ModFeatures.HABITAT_MISS.sum();
		return c;
	}

	private static String layers(long[] before, long[] after) {
		List<String> parts = new ArrayList<>();
		for (BiomeDecoration.Dispatcher d : BiomeDecoration.Dispatcher.values()) {
			int o = d.ordinal() * 3;
			long chunks = after[o + 1] - before[o + 1];
			parts.add(String.format(Locale.ROOT, "%s %d chunks, %.3f ms/chunk, %.1f placed/chunk", d.path(), chunks,
					(after[o] - before[o]) / 1e6 / Math.max(1, chunks), (double) (after[o + 2] - before[o + 2]) / Math.max(1, chunks)));
		}
		long chunks = Math.max(1, after[BiomeDecoration.Dispatcher.TREE_STAND.ordinal() * 3 + 1]
				- before[BiomeDecoration.Dispatcher.TREE_STAND.ordinal() * 3 + 1]);
		return "dispatcher layers (terrain generated during the visits, without the world start): " + String.join("; ", parts)
				+ String.format(Locale.ROOT, "; tree stand wood %.1f and leaves %.1f per chunk", (double) (after[after.length - 3]
						- before[before.length - 3]) / chunks, (double) (after[after.length - 2] - before[before.length - 2]) / chunks)
				+ String.format(Locale.ROOT, "; procedural trees not planted since the start: structure or water %d, trunk base %d, "
						+ "nothing written %d, suppressed canopy trees %d", pl.polishforests.worldgen.feature.TreeStandFeature.SKIPPED_STRUCTURE.sum(),
						pl.polishforests.worldgen.feature.TreeStandFeature.SKIPPED_BASE.sum(),
						pl.polishforests.worldgen.feature.TreeStandFeature.SKIPPED_WRITE.sum(),
						pl.polishforests.worldgen.feature.TreeStandFeature.SKIPPED_SUPPRESSED.sum())
				+ String.format(Locale.ROOT, "; since the start: forest blend lattice samples %d, kernel sums %d, planner twins %d (step Z1)",
						pl.polishforests.worldgen.feature.plan.ForestBlend.SAMPLES.sum(),
						pl.polishforests.worldgen.feature.plan.ForestBlend.NODES.sum(),
						pl.polishforests.worldgen.feature.TreeStandFeature.TWINS.sum())
				+ "; dead wood objects since the start (step V11): " + pl.polishforests.worldgen.feature.DeadwoodFeature.PLACED
				+ String.format(Locale.ROOT, ", picks not placed: base column %d, not kept by the stand %d, empty plan %d, nothing written %d",
						pl.polishforests.worldgen.feature.DeadwoodFeature.SKIPPED_BASE.sum(), pl.polishforests.worldgen.feature.DeadwoodFeature.NOT_KEPT.sum(),
						pl.polishforests.worldgen.feature.DeadwoodFeature.EMPTY.sum(), pl.polishforests.worldgen.feature.DeadwoodFeature.UNWRITTEN.sum())
				+ "; HABITAT_MISS " + (after[after.length - 1] - before[before.length - 1]);
	}

	static void select(WorldCreationUiState ui, ResourceKey<WorldPreset> preset, boolean presentDay) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(preset)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier())));
		if (presentDay) {
			if (!(ui.getSettings().selectedDimensions().overworld() instanceof PolandChunkGenerator gen)) {
				throw new AssertionError("The preset " + preset.identifier() + " has no Poland generator");
			}
			ui.updateDimensions(PolandPresetEditor.apply(gen.settings().withAgriculture(true)));
		}
	}
}
