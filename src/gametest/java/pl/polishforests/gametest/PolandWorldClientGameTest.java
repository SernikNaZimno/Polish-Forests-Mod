package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.material.Fluids;
import pl.polishforests.PolishForests;
import pl.polishforests.client.ClientClimate;
import pl.polishforests.climate.BiomeClimateAccess;
import pl.polishforests.climate.BiomeClimate;
import pl.polishforests.climate.PolandClimate;
import pl.polishforests.command.PolishForestsCommands;
import pl.polishforests.season.Seasons;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandDimension;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeType;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Test in a real client: creates a "Poland" world, measures the generation speed of full chunks
 * and takes screenshots at characteristic places picked by the landscape model
 * ({@code views} mode). The {@code climate} mode checks temperature from meters in both world scales:
 * no summer snow on the outwash plain and in the Beskids, winter snow on the lowland with Serene Seasons,
 * client precipitation matching the server, and water freeze modes.
 */
public final class PolandWorldClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland"));
	private static final ResourceKey<WorldPreset> PRESET_GAMEPLAY = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland_gameplay"));
	/** Serene Seasons blacklist (river, beach, oceans): no seasonal correction. */
	private static final TagKey<Biome> SS_BLACKLIST = TagKey.create(Registries.BIOME,
			Identifier.fromNamespaceAndPath("sereneseasons", "blacklisted_biomes"));

	/** Render distance in chunks for the views; a site may ask for a longer one. */
	private static final int RENDER_DISTANCE = 10;

	/**
	 * Place to look at: coordinates, camera height above ground, yaw, pitch and render distance in chunks
	 * (0: {@link #RENDER_DISTANCE}).
	 */
	private record Site(String name, int x, int z, int cameraAboveGround, float yaw, float pitch, int renderDistance) {
		Site(String name, int x, int z, int cameraAboveGround, float yaw, float pitch) {
			this(name, x, z, cameraAboveGround, yaw, pitch, 0);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polishforests.gametest", "all");
		boolean views = mode.equals("views") || mode.equals("all");
		boolean climate = mode.equals("climate") || mode.equals("all");
		if (!views && !climate) {
			return;
		}
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(RENDER_DISTANCE);
		});
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(ui -> selectPoland(ui, PRESET))
				.create()) {
			prepare(sp, PolandScale.REALISTIC);
			if (views) {
				views(context, sp);
			}
			// Climate after the benchmark and screenshots: the teleport to the Beskids leaves chunk generation
			// running in the background, which would inflate the ms/chunk measurement.
			if (climate) {
				checkClimate(context, sp);
			}
		}
		if (climate) {
			// Second scale: dimension type poland_gameplay, non-linear m a.s.l. and scale detection on the client.
			try (TestSingleplayerContext sp = context.worldBuilder()
					.adjustSettings(ui -> selectPoland(ui, PRESET_GAMEPLAY))
					.create()) {
				prepare(sp, PolandScale.GAMEPLAY);
				checkClimate(context, sp);
			}
		}
	}

	/** Fixed time and weather, player in spectator mode; checks the generator and its scale. */
	private static void prepare(TestSingleplayerContext sp, PolandScale scale) {
		sp.getServer().runCommand("gamerule advance_time false");
		sp.getServer().runCommand("gamerule advance_weather false");
		sp.getServer().runCommand("time set 6000");
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("gamemode spectator @a");

		boolean isPoland = sp.getServer().computeOnServer(
				s -> s.overworld().getChunkSource().getGenerator() instanceof PolandChunkGenerator gen
						&& gen.vertical() == scale.vertical());
		if (!isPoland) {
			throw new AssertionError("Test world does not use the Poland generator at scale " + scale.getSerializedName());
		}
	}

	/** Generation benchmark and screenshots at characteristic places. */
	private static void views(ClientGameTestContext context, TestSingleplayerContext sp) {
		benchmark(sp);

		List<Site> sites = sp.getServer().computeOnServer(PolandWorldClientGameTest::findSites);
		String only = System.getProperty("polishforests.sites", "");
		if (!only.isBlank()) {
			List<String> names = List.of(only.split(","));
			sites = sites.stream().filter(site -> names.contains(site.name())).toList();
		}
		for (Site site : sites) {
			int groundY = sp.getServer().computeOnServer(s -> surfaceY(s, site.x(), site.z()));
			int camY = groundY + site.cameraAboveGround();
			PolishForests.LOG.info("[test] {}: x={} z={} ground Y={} ({} m a.s.l.)", site.name(), site.x(), site.z(),
					groundY, PolandDimension.metersAboveSea(groundY - 1));
			int renderDistance = site.renderDistance() > 0 ? site.renderDistance() : RENDER_DISTANCE;
			if (renderDistance != RENDER_DISTANCE) {
				setRenderDistance(context, renderDistance);
			}
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %d %d %d %.1f %.1f", site.x(), camY, site.z(),
					site.yaw(), site.pitch()));
			if (renderDistance != RENDER_DISTANCE) {
				waitForChunks(context, site.name(), renderDistance);
			}
			for (int step = 0; step < 2; step++) {
				context.waitTicks(20 * 20);
				int loaded = sp.getServer().computeOnServer(s -> s.overworld().getChunkSource().getLoadedChunksCount());
				String client = context.computeOnClient(mc -> mc.level.getChunkSource().gatherStats() + ", fps " + mc.getFps());
				PolishForests.LOG.info("[test] {} after {} s: server {} chunks; client: {}", site.name(), (step + 1) * 20,
						loaded, client);
			}
			context.takeScreenshot("poland_" + site.name());
			if (renderDistance != RENDER_DISTANCE) {
				setRenderDistance(context, RENDER_DISTANCE);
			}
		}
	}

	/**
	 * Changes the render distance during the game. The server limits the player's view to the distance the client
	 * requested in its client information, so the options are sent to the server again (as the options screen does).
	 */
	private static void setRenderDistance(ClientGameTestContext context, int chunks) {
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(chunks);
			mc.options.broadcastOptions();
		});
	}

	/**
	 * Longer render distance: waits until the client holds 80% of the chunks of the circular view area or the count
	 * stops growing for 15 s (at most 4 minutes); the usual 40 s wait before the screenshot follows.
	 */
	private static void waitForChunks(ClientGameTestContext context, String name, int renderDistance) {
		int target = (int) (0.8 * Math.PI * renderDistance * renderDistance);
		long t0 = System.nanoTime();
		int loaded = 0;
		int previous = -1;
		int still = 0;
		for (int i = 0; i < 240 && loaded < target && still < 15; i++) {
			context.waitTicks(20);
			loaded = context.computeOnClient(mc -> mc.level.getChunkSource().getLoadedChunksCount());
			still = loaded == previous ? still + 1 : 0;
			previous = loaded;
		}
		PolishForests.LOG.info("[test] {}: render distance {}, client has {} chunks (target {}) after {} s", name,
				renderDistance, loaded, target, String.format(Locale.ROOT, "%.1f", (System.nanoTime() - t0) / 1e9));
	}

	/** Elevation below which no snow may lie in summer (docs/03-m2-biomy.md, section 12.3). */
	private static final double NO_SUMMER_SNOW_BELOW = 1_900;

	/**
	 * Temperature from meters: in summer there is no snow on the outwash plain or in the Beskids below 1900 m
	 * (also with the Serene Seasons seasonal correction), with SS it snows on the lowland in winter, Nether and
	 * End biomes have no profile, the client computes precipitation the same way as the server (20 points
	 * around the player), and the freeze modes behave as set in the profile.
	 */
	private static void checkClimate(ClientGameTestContext context, TestSingleplayerContext sp) {
		boolean ss = FabricLoader.getInstance().isModLoaded("sereneseasons");
		if (ss) {
			if (!Seasons.provider().name().equals("Serene Seasons")) {
				throw new AssertionError("Serene Seasons is loaded, but the season provider is " + Seasons.provider().name());
			}
			sp.getServer().runCommand("season set mid_summer");
		}
		int[] p = sp.getServer().computeOnServer(s -> {
			PolandChunkGenerator gen = (PolandChunkGenerator) s.overworld().getChunkSource().getGenerator();
			return highestBeskids(gen.model(s.overworld().getSeed()));
		});
		String summer = sp.getServer().computeOnServer(s -> checkSummer(s, p));
		PolishForests.LOG.info("[test] climate in summer: {}", summer);

		int groundY = sp.getServer().computeOnServer(s -> surfaceY(s, p[0], p[1]));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", p[0], groundY + 30, p[1]));
		context.waitTicks(20 * 15);

		List<BlockPos> points = sp.getServer().computeOnServer(s -> {
			VerticalScale v = ((PolandChunkGenerator) s.overworld().getChunkSource().getGenerator()).vertical();
			List<BlockPos> list = new ArrayList<>();
			for (int k = 0; k < 5; k++) {
				int x = p[0] + (k % 3 - 1) * 24;
				int z = p[1] + (k / 3 * 2 - 1) * 24;
				for (double meters : new double[] {0, 1_000, 1_950, 2_100}) {
					list.add(new BlockPos(x, Math.min(v.topBlockY(meters) + 1, v.maxY()), z));
				}
			}
			return list;
		});
		List<String> server = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			List<String> out = new ArrayList<>();
			for (BlockPos pos : points) {
				out.add(level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()).name());
			}
			return out;
		});
		VerticalScale serverScale = sp.getServer().computeOnServer(
				s -> ((PolandChunkGenerator) s.overworld().getChunkSource().getGenerator()).vertical());
		List<String> client = context.computeOnClient(mc -> {
			if (ClientClimate.scale(mc.level) != serverScale) {
				throw new AssertionError("Client detected a different vertical scale than the server");
			}
			List<String> out = new ArrayList<>();
			for (BlockPos pos : points) {
				if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
					throw new AssertionError("Client has no chunk at point " + pos.toShortString());
				}
				Biome biome = mc.level.getBiome(pos).value();
				if (BiomeClimateAccess.climate(biome) == null) {
					throw new AssertionError("Client biome without a climate profile at " + pos.toShortString());
				}
				out.add(biome.getPrecipitationAt(pos, mc.level.getSeaLevel()).name());
			}
			return out;
		});
		if (!server.equals(client)) {
			throw new AssertionError("Client precipitation " + client + " differs from the server " + server);
		}
		PolishForests.LOG.info("[test] climate: client precipitation matches the server at {} points: {}", points.size(), client);

		if (ss) {
			sp.getServer().runCommand("season set mid_winter");
			String winter = sp.getServer().computeOnServer(PolandWorldClientGameTest::checkWinter);
			PolishForests.LOG.info("[test] climate in winter (Serene Seasons): {}", winter);
			sp.getServer().runCommand("season set mid_summer");
		}
		// Last: the freeze test temporarily swaps the climate profile of the water biome.
		String freeze = sp.getServer().computeOnServer(PolandWorldClientGameTest::checkFreezeModes);
		PolishForests.LOG.info("[test] climate, freezing: {}", freeze);
	}

	/**
	 * Summer, server side: no profiles in the Nether and the End, no snow on the outwash plain and in the Beskids
	 * around the highest place {@code summit}, nor in the air at 1500 and 1850 m a.s.l. above the Beskids.
	 */
	private static String checkSummer(MinecraftServer server, int[] summit) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
		LandscapeModel m = gen.model(level.getSeed());
		VerticalScale v = gen.vertical();
		for (ResourceKey<Level> dimension : List.of(Level.NETHER, Level.END)) {
			ServerLevel other = server.getLevel(dimension);
			if (other == null) {
				throw new AssertionError("Missing dimension " + dimension.identifier());
			}
			for (Holder<Biome> biome : other.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
				if (BiomeClimateAccess.climate(biome.value()) != null) {
					throw new AssertionError("Biome of dimension " + dimension.identifier() + " has a climate profile: "
							+ biome.getRegisteredName());
				}
			}
		}
		StringBuilder report = new StringBuilder();
		int[] outwashPlain = spiral(m, s -> s.type() == LandscapeType.OUTWASH_PLAIN);
		if (outwashPlain == null) {
			throw new AssertionError("No outwash plain found");
		}
		checkSummerSnow(level, m, v, LandscapeType.OUTWASH_PLAIN, outwashPlain, "outwash_plain", report);
		double highest = checkSummerSnow(level, m, v, LandscapeType.BESKIDS, summit, "beskids", report);
		// Test seed: at most about 1311 m (REAL) and 1125 m (GAMEPLAY), because at gameplay scale the Beskids
		// barely exceed 1150 m (docs/03-m2-biomy.md, S0). Higher elevations are checked in the air.
		double minimum = v == PolandScale.REALISTIC.vertical() ? 1_200 : 1_000;
		if (highest < minimum) {
			throw new AssertionError(String.format(Locale.ROOT,
					"Beskids checked only up to %.0f m a.s.l. (at least %.0f m required)", highest, minimum));
		}
		int checked = 0;
		for (int k = 0; k < 25; k++) {
			for (double meters : new double[] {1_500, 1_850}) {
				BlockPos pos = new BlockPos(summit[0] + (k % 5 - 2) * 200, v.topBlockY(meters) + 1, summit[1] + (k / 5 - 2) * 200);
				if (v.metersAboveSea(pos.getY()) >= NO_SUMMER_SNOW_BELOW) {
					throw new AssertionError("Test elevation above 1900 m: " + pos.toShortString());
				}
				Biome biome = level.getBiome(pos).value();
				BiomeClimate climate = BiomeClimateAccess.climate(biome);
				if (climate == null) {
					throw new AssertionError("Biome without a climate profile at " + pos.toShortString());
				}
				float t = Seasons.provider().temperatureInSeason(level, biome, pos, climate.temperature(pos));
				if (biome.coldEnoughToSnow(pos, level.getSeaLevel()) || t < PolandClimate.SNOW_THRESHOLD) {
					throw new AssertionError(String.format(Locale.ROOT, "Snow in summer at %.0f m a.s.l. (seasonal T %.3f, %s)",
							meters, t, pos.toShortString()));
				}
				checked++;
			}
		}
		report.append(String.format(Locale.ROOT, "air above the Beskids: %d points at 1500 and 1850 m without snow; ",
				checked));
		checkSnowBlocks(level, v, summit, false, "beskids", report);
		return report.toString();
	}

	/**
	 * Columns of type {@code type} on a 48 × 48 grid with 40 m spacing around {@code p}, below 1900 m: no snow
	 * on the vanilla path (cache and temperature mixin) or in the seasonal temperature (SS hook, if present).
	 *
	 * @return the highest checked column in meters above sea level
	 */
	private static double checkSummerSnow(ServerLevel level, LandscapeModel m, VerticalScale v, LandscapeType type,
			int[] p, String name, StringBuilder report) {
		int checked = 0;
		double highest = 0;
		float coldest = Float.MAX_VALUE;
		for (int i = -24; i < 24; i++) {
			for (int j = -24; j < 24; j++) {
				int x = p[0] + i * 40;
				int z = p[1] + j * 40;
				ColumnSample s = m.sample(x, z);
				if (s.type() != type || s.surface() >= NO_SUMMER_SNOW_BELOW) {
					continue;
				}
				BlockPos pos = new BlockPos(x, v.topBlockY(s.surface()) + 1, z);
				Biome biome = level.getBiome(pos).value();
				BiomeClimate climate = BiomeClimateAccess.climate(biome);
				if (climate == null) {
					throw new AssertionError("Biome without a climate profile at " + pos.toShortString());
				}
				if (biome.coldEnoughToSnow(pos, level.getSeaLevel())) {
					throw new AssertionError(String.format(Locale.ROOT, "Snow in summer in %s at %.0f m a.s.l. (%s)", name,
							s.surface(), pos.toShortString()));
				}
				float t = Seasons.provider().temperatureInSeason(level, biome, pos, climate.temperature(pos));
				if (t < PolandClimate.SNOW_THRESHOLD) {
					throw new AssertionError(String.format(Locale.ROOT,
							"Summer (with season) T %.3f, i.e. snow, in %s at %.0f m a.s.l. (%s)", t, name, s.surface(),
							pos.toShortString()));
				}
				coldest = Math.min(coldest, t);
				checked++;
				highest = Math.max(highest, s.surface());
			}
		}
		if (checked < 100) {
			throw new AssertionError("Too few columns of type " + name + ": " + checked);
		}
		report.append(String.format(Locale.ROOT, "%s: %d columns without snow, highest %.0f m, lowest T %.3f; ", name,
				checked, highest, coldest));
		return highest;
	}

	/** Winter with Serene Seasons: it snows on the outwash plain and the moraine plateau (biomes not on the SS blacklist). */
	private static String checkWinter(MinecraftServer server) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
		LandscapeModel m = gen.model(level.getSeed());
		VerticalScale v = gen.vertical();
		StringBuilder report = new StringBuilder();
		int[] outwashPlain = null;
		for (LandscapeType type : List.of(LandscapeType.OUTWASH_PLAIN, LandscapeType.MORAINE_PLATEAU)) {
			int[] p = spiral(m, s -> s.type() == type);
			if (p == null) {
				throw new AssertionError("No place of type " + type);
			}
			if (outwashPlain == null) {
				outwashPlain = p;
			}
			int checked = 0;
			int skipped = 0;
			float warmest = -Float.MAX_VALUE;
			for (int i = -24; i < 24; i++) {
				for (int j = -24; j < 24; j++) {
					int x = p[0] + i * 40;
					int z = p[1] + j * 40;
					ColumnSample s = m.sample(x, z);
					if (s.type() != type) {
						continue;
					}
					BlockPos pos = new BlockPos(x, v.topBlockY(s.surface()) + 1, z);
					Holder<Biome> holder = level.getBiome(pos);
					BiomeClimate climate = BiomeClimateAccess.climate(holder.value());
					if (climate == null) {
						throw new AssertionError("Biome without a climate profile at " + pos.toShortString());
					}
					if (holder.is(SS_BLACKLIST)) {
						skipped++;
						continue;
					}
					float t = Seasons.provider().temperatureInSeason(level, holder.value(), pos, climate.temperature(pos));
					if (t >= PolandClimate.SNOW_THRESHOLD) {
						throw new AssertionError(String.format(Locale.ROOT,
								"Rain in winter with SS (T %.3f) on lowland %s, %.0f m a.s.l. (%s, %s)", t, type, s.surface(),
								pos.toShortString(), holder.getRegisteredName()));
					}
					warmest = Math.max(warmest, t);
					checked++;
				}
			}
			if (checked < 100) {
				throw new AssertionError("Too few columns of type " + type + " outside the SS blacklist: " + checked);
			}
			report.append(String.format(Locale.ROOT, "%s: %d columns with snow (skipped on the SS blacklist: %d), "
					+ "highest T %.3f; ", type, checked, skipped, warmest));
		}
		checkSnowBlocks(level, v, outwashPlain, true, "outwash_plain", report);
		return report.toString();
	}

	/**
	 * Snowfall on real blocks: in a 64 × 64 block area around {@code p} (chunks loaded synchronously),
	 * for every column where snow could settle (air above a block that supports a snow layer, as in
	 * {@code ServerLevel.tickPrecipitation}), {@code Biome.shouldSnow} (with the SS hook at the start of the
	 * method) must return {@code expectSnow}. In summer, below 1900 m there may also be no snow from
	 * generation ({@code freeze_top_layer} without seasons).
	 */
	private static void checkSnowBlocks(ServerLevel level, VerticalScale v, int[] p, boolean expectSnow, String name,
			StringBuilder report) {
		int checked = 0;
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				int x = p[0] - 32 + i * 8 + 3;
				int z = p[1] - 32 + j * 8 + 3;
				level.getChunk(x >> 4, z >> 4);
				BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
				Holder<Biome> holder = level.getBiome(pos);
				if (expectSnow ? holder.is(SS_BLACKLIST) : v.metersAboveSea(pos.getY()) >= NO_SUMMER_SNOW_BELOW) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				if (!expectSnow && (state.is(Blocks.SNOW) || level.getBlockState(pos.below()).is(Blocks.SNOW))) {
					throw new AssertionError(String.format(Locale.ROOT, "Snow from generation in summer in %s at %.0f m a.s.l. (%s)",
							name, v.metersAboveSea(pos.getY()), pos.toShortString()));
				}
				if (!state.isAir() || level.getBrightness(LightLayer.BLOCK, pos) >= 10
						|| !Blocks.SNOW.defaultBlockState().canSurvive(level, pos)) {
					continue;
				}
				if (holder.value().shouldSnow(level, pos) != expectSnow) {
					throw new AssertionError(String.format(Locale.ROOT, "%s: shouldSnow = %b in %s at %.0f m a.s.l. (%s)",
							expectSnow ? "Winter" : "Summer", !expectSnow, name, v.metersAboveSea(pos.getY()),
							pos.toShortString()));
				}
				checked++;
			}
		}
		if (checked < 8) {
			throw new AssertionError("Too few columns to check precipitation on blocks in " + name + ": " + checked);
		}
		report.append(String.format(Locale.ROOT, "%s: shouldSnow = %b in %d columns; ", name, expectSnow, checked));
	}

	/**
	 * Freeze modes ({@code BiomeFreezeMixin}): on sea water, temporarily swaps the biome profile for a test
	 * one and checks {@code shouldFreeze} without neighbors. At T of about 0.10 (with season) NEVER and RIVER
	 * do not freeze, while VANILLA does; at T of about -1 RIVER and VANILLA freeze. Restores the profile at the
	 * end. The vanilla temperature cache (per thread, 1024 positions) is filled with other positions before
	 * and after each measurement, because otherwise it would return the temperature of the previous profile.
	 */
	private static String checkFreezeModes(MinecraftServer server) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
		LandscapeModel m = gen.model(level.getSeed());
		VerticalScale v = gen.vertical();
		int[] p = spiral(m, s -> s.type() == LandscapeType.SEA && s.waterKind() == WaterKind.SEA);
		if (p == null) {
			throw new AssertionError("No sea found for the freeze test");
		}
		BlockPos water = null;
		for (int k = 0; k < 16 && water == null; k++) {
			int x = p[0] + k * 16;
			level.getChunk(x >> 4, p[1] >> 4);
			BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, p[1]) - 1, p[1]);
			if (level.getFluidState(pos).is(Fluids.WATER) && level.getBlockState(pos).getBlock() instanceof LiquidBlock
					&& level.getBrightness(LightLayer.BLOCK, pos) < 10) {
				water = pos;
			}
		}
		if (water == null) {
			throw new AssertionError("No sea water surface near " + p[0] + ", " + p[1]);
		}
		Biome biome = level.getBiome(water).value();
		BiomeClimate original = BiomeClimateAccess.climate(biome);
		if (original == null || original.freezeMode() != BiomeClimate.FreezeMode.NEVER) {
			throw new AssertionError("Sea biome without a NEVER profile at " + water.toShortString() + ": " + original);
		}
		StringBuilder report = new StringBuilder();
		try {
			// Seasonal T of about 0.10: between the river threshold (0.05) and the vanilla threshold (0.15).
			float correction = 0.10F - seasonalTemperature(level, biome, water,
					new BiomeClimate(v, 0.10F, BiomeClimate.FreezeMode.VANILLA));
			checkFreeze(level, biome, water, v, 0.10F + correction, false, false, true, report);
			checkFreeze(level, biome, water, v, -1.0F, false, true, true, report);
			BiomeClimateAccess.set(biome, original);
			flushTemperatureCache(biome, level.getSeaLevel());
			if (biome.shouldFreeze(level, water, false)) {
				throw new AssertionError("Sea with the restored profile freezes at " + water.toShortString());
			}
		} finally {
			BiomeClimateAccess.set(biome, original);
			flushTemperatureCache(biome, level.getSeaLevel());
		}
		report.append("water ").append(water.toShortString());
		return report.toString();
	}

	private static void checkFreeze(ServerLevel level, Biome biome, BlockPos water, VerticalScale v, float base,
			boolean never, boolean river, boolean vanilla, StringBuilder report) {
		BiomeClimate.FreezeMode[] modes = BiomeClimate.FreezeMode.values();
		boolean[] expected = new boolean[modes.length];
		expected[BiomeClimate.FreezeMode.NEVER.ordinal()] = never;
		expected[BiomeClimate.FreezeMode.RIVER.ordinal()] = river;
		expected[BiomeClimate.FreezeMode.VANILLA.ordinal()] = vanilla;
		for (BiomeClimate.FreezeMode mode : modes) {
			BiomeClimate test = new BiomeClimate(v, base, mode);
			float t = seasonalTemperature(level, biome, water, test);
			// Guard: the expectations assume T is on the same side of the thresholds as the base temperature.
			boolean cold = base < 0;
			if (cold ? t >= PolandClimate.RIVER_FREEZE_THRESHOLD
					: t < PolandClimate.RIVER_FREEZE_THRESHOLD + 0.01F || t >= PolandClimate.SNOW_THRESHOLD - 0.01F) {
				throw new AssertionError(String.format(Locale.ROOT, "Freeze test: T %.3f out of range for base %.3f",
						t, base));
			}
			BiomeClimateAccess.set(biome, test);
			flushTemperatureCache(biome, level.getSeaLevel());
			boolean frozen = biome.shouldFreeze(level, water, false);
			if (frozen != expected[mode.ordinal()]) {
				throw new AssertionError(String.format(Locale.ROOT, "Freezing %s at T %.3f: %b, expected %b (%s)",
						mode, t, frozen, expected[mode.ordinal()], water.toShortString()));
			}
			report.append(String.format(Locale.ROOT, "%s at T %.3f: %b; ", mode, t, frozen));
		}
	}

	/** Seasonal temperature for the {@code test} profile (attaches it for the duration of the measurement). */
	private static float seasonalTemperature(ServerLevel level, Biome biome, BlockPos pos, BiomeClimate test) {
		BiomeClimate before = BiomeClimateAccess.climate(biome);
		BiomeClimateAccess.set(biome, test);
		flushTemperatureCache(biome, level.getSeaLevel());
		try {
			return Seasons.provider().temperatureInSeason(level, biome, pos, test.temperature(pos));
		} finally {
			BiomeClimateAccess.set(biome, before);
			flushTemperatureCache(biome, level.getSeaLevel());
		}
	}

	/** Evicts all earlier positions from the vanilla biome temperature cache (current thread). */
	private static void flushTemperatureCache(Biome biome, int seaLevel) {
		for (int i = 0; i < 1_100; i++) {
			biome.coldEnoughToSnow(new BlockPos(-29_000_000 + i, 0, -29_000_000), seaLevel);
		}
	}

	/**
	 * Highest Beskids column below 1900 m around the first Beskids place on the spiral: a grid of
	 * ±60 km every 1.5 km, then ±2 km every 250 m and ±300 m every 50 m around the best one. A spiral with an
	 * elevation condition would be too expensive (in REAL the first place above 1150 m lies about 1000 km
	 * from the center).
	 */
	private static int[] highestBeskids(LandscapeModel m) {
		int[] p = spiral(m, s -> s.type() == LandscapeType.BESKIDS);
		if (p == null) {
			throw new AssertionError("No Beskids found");
		}
		int[] best = p;
		double bestMeters = -1;
		int[][] passes = {{1_500, 40}, {250, 8}, {50, 6}};
		for (int[] pass : passes) {
			int[] c = best;
			for (int i = -pass[1]; i <= pass[1]; i++) {
				for (int j = -pass[1]; j <= pass[1]; j++) {
					int x = c[0] + i * pass[0];
					int z = c[1] + j * pass[0];
					ColumnSample s = m.sample(x, z);
					if (s.type() == LandscapeType.BESKIDS && s.surface() < NO_SUMMER_SNOW_BELOW
							&& s.surface() > bestMeters) {
						best = new int[] {x, z};
						bestMeters = s.surface();
					}
				}
			}
		}
		return best;
	}

	private void selectPoland(WorldCreationUiState ui, ResourceKey<WorldPreset> preset) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		for (WorldCreationUiState.WorldTypeEntry entry : ui.getNormalPresetList()) {
			if (entry.preset().is(preset)) {
				ui.setWorldType(entry);
				return;
			}
		}
		throw new AssertionError("World preset " + preset.identifier() + " is missing from the world type list");
	}

	private static int surfaceY(MinecraftServer server, int x, int z) {
		ServerLevel level = server.overworld();
		return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
				level.getChunkSource().randomState());
	}

	/** Synchronously generates an 8 × 8 chunk area to the full status and logs the time. */
	private static void benchmark(TestSingleplayerContext sp) {
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			int ox = 20_000 >> 4;
			int oz = -20_000 >> 4;
			long t0 = System.nanoTime();
			int n = 0;
			for (int cx = 0; cx < 8; cx++) {
				for (int cz = 0; cz < 8; cz++) {
					level.getChunk(ox + cx, oz + cz);
					n++;
				}
			}
			double ms = (System.nanoTime() - t0) / 1e6;
			PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
			LandscapeModel m = gen.model(level.getSeed());
			long t1 = System.nanoTime();
			double sink = 0;
			for (int x = 0; x < 128; x++) {
				for (int z = 0; z < 128; z++) {
					sink += m.sample(40_000 + x, 40_000 + z).surface();
				}
			}
			double modelMs = (System.nanoTime() - t1) / 1e6;
			PolishForests.LOG.info("[test] Landscape model: {} ms per 64 chunks (16,384 columns), checksum {}",
					Math.round(modelMs), Math.round(sink));
			PolishForests.LOG.info("[test] Generation of {} full chunks: {} ms ({} ms/chunk)", n, Math.round(ms),
					Math.round(ms / n));
		});
	}

	/** Picks places for screenshots by searching the landscape model along a spiral. */
	private static List<Site> findSites(MinecraftServer server) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
		LandscapeModel m = gen.model(level.getSeed());
		List<Site> sites = new ArrayList<>();
		int[] p = spiral(m, s -> s.type() == LandscapeType.OUTWASH_PLAIN && s.waterKind() == WaterKind.LAKE);
		if (p != null) {
			sites.add(new Site("outwash_plain_lake", p[0] + 180, p[1] - 180, 45, 45f, 20f));
		}
		p = spiral(m, s -> s.type() == LandscapeType.MORAINE_PLATEAU && s.waterKind() == WaterKind.LAKE);
		if (p != null) {
			sites.add(new Site("moraine_kettles", p[0] + 120, p[1] - 120, 35, 45f, 22f));
		}
		p = spiral(m, s -> s.waterKind() == WaterKind.RIVER);
		if (p != null) {
			sites.add(new Site("river_valley", p[0], p[1], 60, 0f, 25f));
		}
		p = spiral(m, s -> s.type() == LandscapeType.BESKIDS && s.surface() > 1_150);
		if (p != null) {
			sites.add(new Site("beskids", p[0], p[1], 25, 30f, 12f));
		}
		p = spiral(m, s -> s.type() == LandscapeType.FOOTHILLS);
		if (p != null) {
			sites.add(new Site("foothills", p[0], p[1], 40, 60f, 15f));
		}
		addGreatMassif(sites, m, gen.vertical());
		// Rivers, valleys and the sea.
		p = spiral(m, s -> s.waterKind() == WaterKind.RIVER && s.type().isLowland() && s.surface() - s.waterLevel() < -2.5);
		if (p != null) {
			sites.add(new Site("meanders", p[0], p[1], 70, 20f, 50f));
		}
		addTarget(sites, m, PolishForestsCommands.Target.OXBOW_LAKE, "oxbow_lake", 45, 45f);
		addTarget(sites, m, PolishForestsCommands.Target.HEADWATERS, "headwaters", 30, 20f);
		addCoast(sites, m, PolishForestsCommands.Target.BEACH, "beach", 20);
		addCoast(sites, m, PolishForestsCommands.Target.COASTAL_DUNES, "coastal_dunes", 25);
		addCoast(sites, m, PolishForestsCommands.Target.CLIFF, "cliff", 30);
		addTarget(sites, m, PolishForestsCommands.Target.LAGOON, "lagoon", 40, 25f);
		addTarget(sites, m, PolishForestsCommands.Target.RIVER_MOUTH, "river_mouth", 60, 35f);
		return sites;
	}

	private static void addTarget(List<Site> sites, LandscapeModel m, PolishForestsCommands.Target target, String name,
			int above, float pitch) {
		double[] p = PolishForestsCommands.locate(m, target, 0, 0);
		if (p != null) {
			sites.add(new Site(name, (int) p[0] - 40, (int) p[1] - 40, above, -45f, pitch));
		}
	}

	/** Distance of the great massif camera from the summit, in blocks (= m horizontally). */
	private static final int MASSIF_VIEW_DISTANCE = 360;
	/** Render distance for the great massif view: the summit stays in front of the fog (from about 400 blocks). */
	private static final int MASSIF_RENDER_DISTANCE = 28;

	/**
	 * Side view of the summit of the large Beskid massif nearest to (0, 0) ({@link LandscapeModel#nearestGreatMassif};
	 * dwarf pine and alpine grassland in the habitat model): the summit is searched on grids of 100, 20 and 4 m
	 * around the massif center, the camera stands {@link #MASSIF_VIEW_DISTANCE} m away on the steepest of 16 flanks
	 * (largest drop between 180 m and the camera). The camera hangs 12 blocks above the summit level (at least 40 blocks
	 * above the ground, over the spruce crowns) and looks 6° below the summit: from lower on the flank the convex
	 * shoulder of the dome hides the summit. In the game the whole belt above 1150 m still has the stand-in spruce
	 * biome: dwarf pine and alpine grassland come with the biomes of phase 2.
	 */
	private static void addGreatMassif(List<Site> sites, LandscapeModel m, VerticalScale v) {
		LandscapeModel.GreatMassif g = m.nearestGreatMassif(0, 0);
		if (g == null) {
			return;
		}
		double bx = g.x();
		double bz = g.z();
		double best = -Double.MAX_VALUE;
		for (int[] pass : new int[][] {{100, 40}, {20, 10}, {4, 10}}) {
			double cx = bx;
			double cz = bz;
			for (int i = -pass[1]; i <= pass[1]; i++) {
				for (int j = -pass[1]; j <= pass[1]; j++) {
					double x = cx + i * pass[0];
					double z = cz + j * pass[0];
					double s = m.sample(x, z).surface();
					if (s > best) {
						best = s;
						bx = x;
						bz = z;
					}
				}
			}
		}
		double dx = 1;
		double dz = 0;
		double drop = -Double.MAX_VALUE;
		double ground = 0;
		for (int k = 0; k < 16; k++) {
			double a = k * Math.PI / 8;
			double ex = Math.cos(a);
			double ez = Math.sin(a);
			double far = m.sample(bx + ex * MASSIF_VIEW_DISTANCE, bz + ez * MASSIF_VIEW_DISTANCE).surface();
			double d = m.sample(bx + ex * 180, bz + ez * 180).surface() - far;
			if (d > drop) {
				drop = d;
				dx = ex;
				dz = ez;
				ground = far;
			}
		}
		int summitY = v.topBlockY(best);
		int groundY = v.topBlockY(ground);
		int above = Math.max(40, summitY + 12 - groundY);
		int rise = summitY - groundY - above;
		// Camera looks at the summit (opposite to the flank direction); yaw 0 = +Z, 90 = -X.
		float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
		float pitch = (float) (6 - Math.toDegrees(Math.atan2(rise, MASSIF_VIEW_DISTANCE)));
		PolishForests.LOG.info(String.format(Locale.ROOT,
				"[test] great_massif: center (%.0f, %.0f), target %.0f m, summit (%.0f, %.0f) %.0f m, flank drop %.0f m",
				g.x(), g.z(), g.targetSummit(), bx, bz, best, drop));
		sites.add(new Site("great_massif", (int) Math.round(bx + dx * MASSIF_VIEW_DISTANCE),
				(int) Math.round(bz + dz * MASSIF_VIEW_DISTANCE), above, yaw, pitch, MASSIF_RENDER_DISTANCE));
	}

	/** Distance of the coastal cameras beyond the coastline, in meters. */
	private static final int COAST_OFFSHORE = 40;

	/**
	 * Coastal place viewed from the sea, {@link #COAST_OFFSHORE} m beyond the coastline, facing land. The target may
	 * lie well inland (the foredune of a dune coast stands about 150 m behind the waterline), so the camera steps back
	 * by the target's distance from the coastline, and the render distance grows to keep the target 80 blocks in front
	 * of the fog.
	 */
	private static void addCoast(List<Site> sites, LandscapeModel m, PolishForestsCommands.Target target, String name,
			int above) {
		double[] p = PolishForestsCommands.locate(m, target, 0, 0);
		if (p == null) {
			return;
		}
		// Seaward direction: where the distance from the coastline decreases.
		double e = 50;
		double gx = m.coastDistance(p[0] + e, p[1]) - m.coastDistance(p[0] - e, p[1]);
		double gz = m.coastDistance(p[0], p[1] + e) - m.coastDistance(p[0], p[1] - e);
		double l = Math.max(1e-9, Math.hypot(gx, gz));
		double sx = -gx / l;
		double sz = -gz / l;
		// Minecraft yaw: 0 = +Z, 90 = -X; the camera looks opposite to the seaward direction.
		float yaw = (float) Math.toDegrees(Math.atan2(sx, -sz));
		double back = Math.max(0, m.coastDistance(p[0], p[1])) + COAST_OFFSHORE;
		int renderDistance = Math.max(RENDER_DISTANCE, (int) Math.ceil((back + 80) / 16));
		sites.add(new Site(name, (int) (p[0] + sx * back), (int) (p[1] + sz * back), above, yaw, 12f, renderDistance));
	}

	private static int[] spiral(LandscapeModel m, java.util.function.Predicate<ColumnSample> test) {
		for (int r = 0; r < 1_500; r += 2) {
			int samples = Math.max(24, r);
			for (int k = 0; k < samples; k++) {
				double a = k * (2 * Math.PI / samples) + r * 0.37;
				int x = (int) (Math.cos(a) * r * 1_000);
				int z = (int) (Math.sin(a) * r * 1_000);
				if (test.test(m.sample(x, z))) {
					return new int[] {x, z};
				}
			}
		}
		return null;
	}
}
