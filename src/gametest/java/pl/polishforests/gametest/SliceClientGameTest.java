package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.client.screen.PolandPresetEditor;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.BiomeDecoration;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.surface.ChunkSurface;

/**
 * The vertical slice in the game (step V0, docs/04-pionowy-wycinek.md §3 and §5): four worlds (realistic and gameplay
 * scale, natural and present-day mode) with the fixed places of {@link SliceSites} (two oak-hornbeam forests, two fresh
 * pine forests and a transect fresh pine → mixed pine-oak → mixed → oak-hornbeam forest per scale). For each place the
 * test checks that the world still has it (the classifier of the world over the 100 × 100 block square of a site, the
 * biome of the generated chunk at the center, the order of the forests along a transect, block by block) and takes the
 * screenshots of the slice: at a site from eye height ({@code _eye}), looking up from under the canopy ({@code _canopy})
 * and from 30 blocks above the ground ({@code _30m}); along a transect from eye height in the middle of each forest
 * ({@code _eye_<biome>}, looking towards the oak-hornbeam end) and from 30 blocks above its start ({@code _30m}).
 * Screenshots {@code slice_<scale>_<mode>_<place>_<camera>}, at noon in clear weather, without the HUD; the baseline of
 * M2 is in {@code docs/wycinek/baza/}. Per world the counters of the dispatcher layers ({@link ModFeatures#STATS}: chunks,
 * time and placements per chunk) and {@link ModFeatures#HABITAT_MISS} (must stay 0) go to the log ({@code [slice]}).
 *
 * <p>Runs only by name ({@code -Pgametest=slice}, not in {@code all}); {@code -Pscales=realistic|gameplay} picks a scale,
 * {@code -Pmodes=natural|present_day} a mode, {@code -Psites=<names>} some places (names of the table, e.g.
 * {@code oak_hornbeam_1,transect}); {@code -Psites=none} only checks the places, without screenshots.
 */
public final class SliceClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int RENDER_DISTANCE = 10;
	/** Transect rules, as in {@code SliceSitesTest}: least run of each forest, longest run and share of other biomes. */
	private static final int[] MIN_RUN = {40, 16};
	private static final int[] MAX_OTHER_RUN = {24, 12};
	private static final double MAX_OTHER = 0.15;
	private static final double MIN_SHARE = 0.95;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!System.getProperty("polishforests.gametest", "all").equals("slice")) {
			return;
		}
		String scales = System.getProperty("polishforests.scales", "");
		String modes = System.getProperty("polishforests.modes", "");
		String only = System.getProperty("polishforests.sites", "");
		boolean screenshots = !only.equals("none");
		List<String> names = only.isBlank() || !screenshots ? List.of() : List.of(only.split(","));
		context.runOnClient(mc -> mc.options.renderDistance().set(RENDER_DISTANCE));
		for (boolean real : new boolean[] {true, false}) {
			String scale = real ? "realistic" : "gameplay";
			if (!scales.isBlank() && !scales.contains(scale)) {
				continue;
			}
			ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id(real ? "poland" : "poland_gameplay"));
			for (boolean presentDay : new boolean[] {false, true}) {
				String mode = presentDay ? "present_day" : "natural";
				if (!modes.isBlank() && !modes.contains(mode)) {
					continue;
				}
				List<SliceSites.Place> places = SliceSites.of(real, presentDay).stream()
						.filter(p -> names.isEmpty() || names.contains(p.name())).toList();
				if (places.isEmpty()) {
					continue;
				}
				try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset, presentDay)).create()) {
					sp.getServer().runCommand("gamerule advance_time false");
					sp.getServer().runCommand("gamerule advance_weather false");
					sp.getServer().runCommand("gamerule spawn_mobs false");
					sp.getServer().runCommand("time set 6000");
					sp.getServer().runCommand("weather clear");
					sp.getServer().runCommand("gamemode spectator @a");
					String world = sp.getServer().computeOnServer(s -> {
						PolandChunkGenerator gen = generator(s);
						gen.validate();
						if (gen.settings().agriculture() != presentDay) {
							throw new AssertionError("The world is not in the " + mode + " mode: " + gen.settings());
						}
						return gen.settings().toString();
					});
					PolishForests.LOG.info("[slice] {} {}: world {}", scale, mode, world);
					long[] before = counters();
					for (SliceSites.Place p : places) {
						String prefix = "slice_" + scale + "_" + mode + "_" + p.name();
						if (p.transect()) {
							int[][] middles = sp.getServer().computeOnServer(s -> checkTransect(s, p));
							if (screenshots) {
								transectShots(context, sp, p, middles, prefix);
							}
						} else {
							String report = sp.getServer().computeOnServer(s -> checkSite(s, p));
							PolishForests.LOG.info("[slice] {} {} {}: {}", scale, mode, p.name(), report);
							if (screenshots) {
								siteShots(context, sp, p, prefix);
							}
						}
					}
					long[] after = counters();
					PolishForests.LOG.info("[slice] {} {}: {}", scale, mode, layers(before, after));
					if (after[after.length - 1] != before[before.length - 1]) {
						throw new AssertionError(scale + " " + mode + ": habitats missing in " + (after[after.length - 1] - before[before.length - 1])
								+ " chunks (HABITAT_MISS)");
					}
				}
			}
		}
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
		int y = groundY(server, p.x(), p.z());
		String biome = level.getBiome(new BlockPos(p.x(), y + 1, p.z())).getRegisteredName();
		if (!biome.equals(PolishForests.MOD_ID + ":" + p.biome())) {
			throw new AssertionError(p.name() + ": getBiome at the center is " + biome + ", expected " + p.biome());
		}
		return String.format(Locale.ROOT, "%s at (%d, %d), ground Y %d, %.1f%% of the square", p.biome(), p.x(), p.z(), y, 100 * share);
	}

	/**
	 * The columns of the transect pass the four forests in order (rules of {@code SliceSitesTest}); returns the middle
	 * {x, z} of the stretch of each forest.
	 */
	private static int[][] checkTransect(MinecraftServer server, SliceSites.Place p) {
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
		for (int i = 0; i <= n; i++) {
			double x = Math.floor(p.x() + (p.x2() - p.x()) * (double) i / n) + 0.5;
			double z = Math.floor(p.z() + (p.z2() - p.z()) * (double) i / n) + 0.5;
			at[i] = new int[] {(int) Math.floor(x), (int) Math.floor(z)};
			String b = Habitat.biome(k.classify(m.sample(x, z), x, z)).id();
			int r = SliceSites.SEQUENCE.indexOf(b);
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
		int[][] middles = new int[4][];
		for (int r = 0; r < 4; r++) {
			if (run[r] < MIN_RUN[s]) {
				throw new AssertionError(p.name() + ": " + SliceSites.SEQUENCE.get(r) + " only " + run[r] + " blocks");
			}
			middles[r] = at[(firstOf[r] + lastOf[r]) / 2];
			report.append(String.format(Locale.ROOT, " %s %d blocks (middle %d, %d);", SliceSites.SEQUENCE.get(r), run[r], middles[r][0],
					middles[r][1]));
		}
		if (other > MAX_OTHER * (n + 1)) {
			throw new AssertionError(String.format(Locale.ROOT, "%s: %.1f%% other biomes", p.name(), 100.0 * other / (n + 1)));
		}
		PolishForests.LOG.info(String.format(Locale.ROOT, "[slice] %s %s: (%d, %d) -> (%d, %d), %d blocks:%s %d other",
				p.realistic() ? "realistic" : "gameplay", p.name(), p.x(), p.z(), p.x2(), p.z2(), n + 1, report, other));
		return middles;
	}

	// ------------------------------------------------------------------ screenshots

	/** Eye height, under the canopy and from 30 blocks above the ground at a site. */
	private static void siteShots(ClientGameTestContext context, TestSingleplayerContext sp, SliceSites.Place p, String prefix) {
		// A fixed direction for the views at eye height: towards the south-west, across the square.
		double yaw = 30;
		int[] eye = sp.getServer().computeOnServer(s -> freeSpot(s, p.x(), p.z()));
		shot(context, sp, eye[0], eye[1], eye[2], yaw, 0, prefix + "_eye", 30);
		shot(context, sp, eye[0], eye[1], eye[2], yaw, -70, prefix + "_canopy", 3);
		// 30 blocks above the ground, 30 blocks back from the center against the direction, looking at the center.
		double dx = -Math.sin(Math.toRadians(yaw));
		double dz = Math.cos(Math.toRadians(yaw));
		int cx = (int) Math.round(p.x() - 30 * dx);
		int cz = (int) Math.round(p.z() - 30 * dz);
		int ground = sp.getServer().computeOnServer(s -> groundY(s, p.x(), p.z()));
		int y = sp.getServer().computeOnServer(s -> clear(s, cx, Math.max(ground, groundY(s, cx, cz)) + 30, cz));
		double pitch = Math.toDegrees(Math.atan2(y + 1.62 - (ground + 1), Math.hypot(p.x() - cx, p.z() - cz)));
		shot(context, sp, cx, y, cz, yaw, pitch, prefix + "_30m", 10);
	}

	/** Eye height in the middle of each forest of a transect, looking along it, and from 30 blocks above its start. */
	private static void transectShots(ClientGameTestContext context, TestSingleplayerContext sp, SliceSites.Place p, int[][] middles,
			String prefix) {
		double yaw = Math.toDegrees(Math.atan2(-(p.x2() - p.x()), p.z2() - p.z()));
		for (int r = 0; r < middles.length; r++) {
			int[] m = middles[r];
			int[] eye = sp.getServer().computeOnServer(s -> freeSpot(s, m[0], m[1]));
			shot(context, sp, eye[0], eye[1], eye[2], yaw, 0, prefix + "_eye_" + SliceSites.SEQUENCE.get(r), 30);
		}
		int ground = sp.getServer().computeOnServer(s -> groundY(s, p.x(), p.z()));
		int y = sp.getServer().computeOnServer(s -> clear(s, p.x(), ground + 30, p.z()));
		// Looking along the transect, down to the ground at a third of its length.
		double third = Math.hypot(p.x2() - p.x(), p.z2() - p.z()) / 3;
		double pitch = Math.toDegrees(Math.atan2(y + 1.62 - (ground + 1), third));
		shot(context, sp, p.x(), y, p.z(), yaw, pitch, prefix + "_30m", 30);
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, int x, int y, int z, double yaw, double pitch,
			String screenshot, int waitSeconds) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f %.1f %.1f", x + 0.5, y, z + 0.5, yaw, pitch));
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		context.waitTicks(20 * waitSeconds);
		PolishForests.LOG.info("[slice] {}: camera ({}, {}, {}) yaw {} pitch {}, client {}", screenshot, x, y, z,
				String.format(Locale.ROOT, "%.1f", yaw), String.format(Locale.ROOT, "%.1f", pitch),
				context.computeOnClient(mc -> mc.level.getChunkSource().gatherStats()));
		context.waitTicks(20 * Math.min(10, waitSeconds));
		context.takeScreenshot(screenshot);
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * Feet position {x, y, z} at eye height near (x, z): the nearest column (up to 8 blocks away) whose feet and head blocks
	 * above the ground hold no trunk, leaves or solid block.
	 */
	private static int[] freeSpot(MinecraftServer server, int x, int z) {
		ServerLevel level = server.overworld();
		for (int r = 0; r <= 8; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int y = groundY(server, x + dx, z + dz) + 1;
					if (free(level.getBlockState(new BlockPos(x + dx, y, z + dz))) && free(level.getBlockState(new BlockPos(x + dx, y + 1, z + dz)))) {
						return new int[] {x + dx, y, z + dz};
					}
				}
			}
		}
		return new int[] {x, groundY(server, x, z) + 1, z};
	}

	private static boolean free(BlockState s) {
		return !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && !s.isSolidRender();
	}

	/** Lowest feet Y from {@code from} at (x, z) whose 3 × 3 blocks around the head and above it hold no leaves, logs or solid blocks. */
	private static int clear(MinecraftServer server, int x, int from, int z) {
		ServerLevel level = server.overworld();
		for (int y = from; y < from + 64; y++) {
			boolean ok = true;
			for (int dx = -1; dx <= 1 && ok; dx++) {
				for (int dz = -1; dz <= 1 && ok; dz++) {
					for (int dy = 0; dy <= 2 && ok; dy++) {
						ok = free(level.getBlockState(new BlockPos(x + dx, y + dy, z + dz)));
					}
				}
			}
			if (ok) {
				return y;
			}
		}
		return from + 64;
	}

	/** Top ground block (or water) of the surface plan at (x, z); generates the chunk. */
	private static int groundY(MinecraftServer server, int x, int z) {
		ServerLevel level = server.overworld();
		level.getChunk(x >> 4, z >> 4);
		ChunkSurface plan = generator(server).surface(new ChunkPos(x >> 4, z >> 4), level.getMinY(), level.getMaxY(), level.getSeed(), null,
				level.structureManager());
		int i = ChunkHabitats.index(x & 15, z & 15);
		return plan.wet(i) ? plan.waterTop(i) : plan.top(i);
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
		long[] c = new long[ds.length * 3 + 1];
		for (BiomeDecoration.Dispatcher d : ds) {
			ModFeatures.LayerStats st = ModFeatures.STATS.get(d);
			c[d.ordinal() * 3] = st.nanos.sum();
			c[d.ordinal() * 3 + 1] = st.chunks.sum();
			c[d.ordinal() * 3 + 2] = st.placed.sum();
		}
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
		return "dispatcher layers (this world): " + String.join("; ", parts) + "; HABITAT_MISS "
				+ (after[after.length - 1] - before[before.length - 1]);
	}

	private static void select(WorldCreationUiState ui, ResourceKey<WorldPreset> preset, boolean presentDay) {
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
