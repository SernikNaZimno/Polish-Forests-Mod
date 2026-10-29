package pl.polishforests.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.chunk.ModBiomeKeys;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeType;
import pl.polishforests.worldgen.landscape.WaterKind;
import pl.polishforests.worldgen.surface.ChunkSurface;

/**
 * Caves of step J1 (docs/04-pionowy-wycinek.md), {@code -Pgametest=caves} (only by name): in each scale
 * ({@code -Pscales}) a world of the preset (caves on) and five places: a lowland forest, a river valley, the mountains
 * and the sea coast found in the model, and the nearest start chunk of the vanilla canyon carver. In each place a survey of the generated chunks against their surface plans
 * ({@code PolandChunkGenerator.surface}): the share of cave blocks by depth below the plan top, cave water and lava,
 * cave entrances per km², and the faults: a cave beside or under the water of the surface (a draining river, lake or sea),
 * a trunk or plant over an opened top, lava at Y 0 or higher. Then pictures: the 16 vertical slices of the place's chunk down
 * to the world bottom ({@code build/caves/<scale>_<place>_section.png}, one pixel per block), the inside of the caves
 * found nearby (cheese, spaghetti, noodle, ravine, underground lake, deep lava; {@code caves_<scale>_<place>_<kind>}),
 * a cave entrance in the forest from above and three windows cut into the ground of the lowland and the mountains at the
 * surface, half way down and near the bottom ({@code caves_<scale>_<place>_cut_<level>}), cut after all places were
 * surveyed and shot, so no survey sees a window.
 */
public final class CavesClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland"));
	private static final ResourceKey<WorldPreset> PRESET_GAMEPLAY = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland_gameplay"));
	private static final int[] BINS = {0, 5, 10, 20, 40, 80, 160, 320};
	/**
	 * Lowest Y of a fault of lava: the vanilla aquifers put lava only below Y -10 (fluid level at most -10) and below the
	 * vanilla lava level -54, and the deep lava lakes of M2 ({@code polishforests:deep_lava_lake}, vanilla {@code lake_lava}
	 * placed at Y -54 … 0) fill their lower half, up to 1 block below their origin, so no lava lies at Y 0 or higher.
	 */
	private static final int LAVA_FROM_Y = 0;
	private static final String[] KINDS = {"cheese", "spaghetti", "noodle", "ravine", "lake", "lava", "entrance"};

	/** A place: name, center chunk and survey radius in chunks. */
	private record Place(String name, int cx, int cz, int radius, boolean cut) {
	}

	/** A camera spot inside a cave. */
	private record Spot(String kind, int x, int y, int z, float yaw, float pitch, int score) {
	}

	/** Survey of a place. */
	private record Report(String text, List<String> failures, Map<String, Spot> spots, int top) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"caves".equals(System.getProperty("polishforests.gametest", "all"))) {
			return;
		}
		String scales = System.getProperty("polishforests.scales", "");
		List<String> failures = new ArrayList<>();
		for (PolandScale scale : PolandScale.values()) {
			if (!scales.isBlank() && !scales.contains(scale.getSerializedName())) {
				continue;
			}
			ResourceKey<WorldPreset> preset = scale == PolandScale.REALISTIC ? PRESET : PRESET_GAMEPLAY;
			try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset)).create()) {
				prepare(sp);
				List<Place> places = sp.getServer().computeOnServer(CavesClientGameTest::findPlaces);
				if (places.size() < 5) {
					failures.add(scale.getSerializedName() + ": only " + places.size() + " of 5 places found");
				}
				// The windows are cut only after every place was surveyed and shot: a window opens the ground, and a later place
				// whose area covers it (the REAL river valley next to the lowland forest) would count and shoot it as a cave
				// entrance (J1 review, round 1).
				List<Runnable> cuts = new ArrayList<>();
				for (Place place : places) {
					String name = "caves_" + scale.getSerializedName() + "_" + place.name();
					Report r = sp.getServer().computeOnServer(s -> survey(s, place, name));
					PolishForests.LOG.info("[caves] {} at chunk ({}, {}): {}", name, place.cx(), place.cz(), r.text());
					failures.addAll(r.failures());
					context.runOnClient(mc -> {
						if (!mc.gui.hud.isHidden()) {
							mc.gui.hud.toggle();
						}
						// Without the renderer's cave culling: after a teleport into a cave its section graph sometimes leaves out
						// sections seen through an opening, and the sky shows through the rock (stage 2 of J1, first runs).
						mc.smartCull = false;
					});
					for (Spot spot0 : r.spots().values()) {
						Spot spot = sp.getServer().computeOnServer(s -> inAir(s.overworld(), spot0));
						sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.1f %.1f %.1f %.0f %.0f", spot.x() + 0.5,
								spot.y() + 0.1, spot.z() + 0.5, spot.yaw(), spot.pitch()));
						PolishForests.LOG.info("[caves] {}_{} at ({}, {}, {})", name, spot.kind(), spot.x(), spot.y(), spot.z());
						ViewReady.shot(context, sp, name + "_" + spot.kind(), null, 25);
					}
					if (place.cut()) {
						cuts.add(() -> cut(context, sp, place, r.top(), name, failures));
					}
				}
				cuts.forEach(Runnable::run);
			}
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Caves: " + String.join("; ", failures));
		}
	}

	private static void select(WorldCreationUiState ui, ResourceKey<WorldPreset> preset) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(preset)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier())));
		if (!(ui.getSettings().selectedDimensions().overworld() instanceof PolandChunkGenerator gen) || !gen.settings().caves()) {
			throw new AssertionError("The preset " + preset.identifier() + " has no Poland generator with caves");
		}
	}

	private static void prepare(TestSingleplayerContext sp) {
		sp.getServer().runCommand("gamerule advance_time false");
		sp.getServer().runCommand("gamerule advance_weather false");
		sp.getServer().runCommand("time set 6000");
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("gamemode spectator @a");
		sp.getServer().runCommand("effect give @a minecraft:night_vision infinite 0 true");
	}

	/**
	 * Start points of the searches for the test seed {@value #SEED}: the Beskids interior and a sea cliff of each scale
	 * (the same points as {@code RiverNetworkTest.BESKIDS_REAL}, {@code CoarseTerrainFieldTest} and {@code PolandCavesTest}).
	 * The Beskids of the realistic scale lie about 1070 km from the origin, so a search from the origin never reached them
	 * (stage 2 of J1: the first run stalled for 10 minutes in the model).
	 */
	private static final int[] BESKIDS_REAL = {154_834, 1_058_738};
	private static final int[] BESKIDS_GAMEPLAY = {27_609, 3_254};
	private static final int[] CLIFF_REAL = {-181_586, -207_822};
	private static final int[] CLIFF_GAMEPLAY = {-3_216, 6_103};
	/** Most model samples of one search (a bound on its time: a fresh area of the model costs up to about 1 ms a sample). */
	private static final int MAX_SAMPLES = 40_000;
	/** Largest distance of a search from its start point (m). */
	private static final int MAX_REACH = 40_000;

	/** The places of the seed, found along a spiral in the model around the start point of each. */
	private static List<Place> findPlaces(MinecraftServer server) {
		ServerLevel level = server.overworld();
		LandscapeModel m = ((PolandChunkGenerator) level.getChunkSource().getGenerator()).model(level.getSeed());
		boolean real = m.scale() == pl.polishforests.worldgen.landscape.LandscapeScale.REALISTIC;
		List<Place> places = new ArrayList<>();
		add(places, m, "forest", 4, true, new int[] {0, 0}, (s, p) -> !s.hasWater() && s.type().isLowland()
				&& level.getBiome(new BlockPos(p[0], 200, p[1])).is(ModBiomeKeys.FORESTS) && dryAround(m, p, 40));
		add(places, m, "river", 2, false, new int[] {0, 0}, (s, p) -> s.waterKind() == WaterKind.RIVER && s.type().isLowland());
		add(places, m, "mountains", 2, true, real ? BESKIDS_REAL : BESKIDS_GAMEPLAY, (s, p) -> s.type() == LandscapeType.BESKIDS
				&& s.surface() > 700 && dryAround(m, p, 24));
		add(places, m, "sea", 2, false, real ? CLIFF_REAL : CLIFF_GAMEPLAY, (s, p) -> s.waterKind() == WaterKind.SEA
				&& landNear(m, p));
		canyon(places, level, m);
		return places;
	}

	/**
	 * The nearest start chunk of the vanilla carver {@code minecraft:canyon} under dry land, from the origin along a square
	 * spiral of chunks, seeded as {@code PolandCaves} seeds it (world seed + index 2 of the carver list, as vanilla); the
	 * geometric "ravine" spots of the other places are tall gaps of any cave, this place shows the carver's canyon itself.
	 */
	private static void canyon(List<Place> places, ServerLevel level, LandscapeModel m) {
		net.minecraft.world.level.levelgen.carver.WorldCarver carver = level.registryAccess()
				.lookupOrThrow(Registries.CARVER).getOrThrow(net.minecraft.data.worldgen.Carvers.CANYON).value();
		net.minecraft.world.level.levelgen.WorldgenRandom random = new net.minecraft.world.level.levelgen.WorldgenRandom(
				new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
		int x = 0;
		int z = 0;
		int dx = 1;
		int dz = 0;
		int leg = 1;
		int inLeg = 0;
		int turns = 0;
		for (int k = 0; k < 128 * 128; k++) {
			random.setLargeFeatureSeed(level.getSeed() + 2, x, z);
			if (carver.isStartChunk(random) && !m.sample((x << 4) + 8, (z << 4) + 8).hasWater()) {
				places.add(new Place("canyon", x, z, 2, false));
				PolishForests.LOG.info("[caves] place canyon: start chunk ({}, {}), {} chunks searched", x, z, k + 1);
				return;
			}
			x += dx;
			z += dz;
			if (++inLeg == leg) {
				inLeg = 0;
				int t = dx;
				dx = -dz;
				dz = t;
				if (++turns % 2 == 0) {
					leg++;
				}
			}
		}
		PolishForests.LOG.warn("[caves] no canyon start chunk within 64 chunks of the origin");
	}

	private interface Test2 {
		boolean test(ColumnSample s, int[] p);
	}

	/**
	 * The first point of a spiral around {@code start} that passes the test: rings 64 m apart near the start, wider with
	 * the distance (1/40 of it), points along a ring as far apart as the rings; at most {@link #MAX_SAMPLES} samples within
	 * {@link #MAX_REACH}.
	 */
	private static void add(List<Place> places, LandscapeModel m, String name, int radius, boolean cut, int[] start,
			Test2 test) {
		long t0 = System.nanoTime();
		int tried = 0;
		for (int r = 0; r <= MAX_REACH && tried < MAX_SAMPLES; r += Math.max(64, r / 40)) {
			int step = Math.max(64, r / 40);
			int samples = r == 0 ? 1 : Math.max(16, (int) Math.ceil(2 * Math.PI * r / step));
			for (int k = 0; k < samples && tried < MAX_SAMPLES; k++, tried++) {
				double a = k * (2 * Math.PI / samples) + r * 0.37;
				int[] p = {start[0] + (int) (Math.cos(a) * r), start[1] + (int) (Math.sin(a) * r)};
				if (test.test(m.sample(p[0], p[1]), p)) {
					places.add(new Place(name, p[0] >> 4, p[1] >> 4, radius, cut));
					PolishForests.LOG.info("[caves] place {} at ({}, {}), {} m from the start, {} samples, {} ms", name, p[0], p[1], r,
							tried + 1, (System.nanoTime() - t0) / 1_000_000);
					return;
				}
			}
		}
		PolishForests.LOG.warn("[caves] no place found for {} within {} m of ({}, {}) in {} samples", name, MAX_REACH, start[0],
				start[1], tried);
	}

	private static boolean dryAround(LandscapeModel m, int[] p, int reach) {
		for (int dx = -reach; dx <= reach; dx += 8) {
			for (int dz = -reach; dz <= reach; dz += 8) {
				if (m.sample(p[0] + dx, p[1] + dz).hasWater()) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean landNear(LandscapeModel m, int[] p) {
		for (int dx = -24; dx <= 24; dx += 8) {
			for (int dz = -24; dz <= 24; dz += 8) {
				if (!m.sample(p[0] + dx, p[1] + dz).hasWater()) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean cave(BlockState s) {
		return s.isAir() || s.is(Blocks.WATER) || s.is(Blocks.LAVA);
	}

	/** Survey of the chunks of a place (on the server thread; generates them). */
	private static Report survey(MinecraftServer server, Place place, String name) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
		long seed = level.getSeed();
		int minY = level.getMinY();
		int r = place.radius();
		int side = (2 * r + 1) * 16;
		int x0 = (place.cx() - r) << 4;
		int z0 = (place.cz() - r) << 4;
		Map<Long, ChunkSurface> plans = new HashMap<>();
		int[] tops = new int[side * side];
		int[] waterTops = new int[side * side];
		for (int cx = place.cx() - r; cx <= place.cx() + r; cx++) {
			for (int cz = place.cz() - r; cz <= place.cz() + r; cz++) {
				level.getChunk(cx, cz);
				ChunkSurface plan = gen.surface(new ChunkPos(cx, cz), minY, level.getMaxY(), seed, null, level.structureManager());
				plans.put(ChunkPos.pack(cx, cz), plan);
				for (int i = 0; i < 256; i++) {
					int lx = ((cx << 4) - x0) + (i >> 4);
					int lz = ((cz << 4) - z0) + (i & 15);
					tops[lx * side + lz] = plan.top(i);
					waterTops[lx * side + lz] = plan.wet(i) ? plan.waterTop(i) : Integer.MIN_VALUE;
				}
			}
		}
		long[] caveByDepth = new long[BINS.length];
		long[] allByDepth = new long[BINS.length];
		long water = 0;
		long lava = 0;
		long contacts = 0;
		long floatingTrunks = 0;
		long floatingPlants = 0;
		long highLava = 0;
		List<String> faultsAt = new ArrayList<>();
		long underWater = 0;
		long underWaterFlooded = 0;
		boolean[][] opened = new boolean[side][side];
		Map<String, Spot> spots = new LinkedHashMap<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int center = r * 16 + 8;
		int top0 = tops[center * side + center];
		for (int lx = 0; lx < side; lx++) {
			for (int lz = 0; lz < side; lz++) {
				int x = x0 + lx;
				int z = z0 + lz;
				int top = tops[lx * side + lz];
				LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
				boolean wet = waterTops[lx * side + lz] != Integer.MIN_VALUE;
				for (int y = top; y > minY + 4; y--) {
					BlockState s = chunk.getBlockState(at.set(x, y, z));
					boolean isCave = cave(s);
					int d = top - y;
					int b = BINS.length - 1;
					while (b > 0 && d < BINS[b]) {
						b--;
					}
					allByDepth[b]++;
					if (wet && d < 32) {
						underWater++;
					}
					if (!isCave) {
						continue;
					}
					caveByDepth[b]++;
					if (s.is(Blocks.WATER)) {
						water++;
						if (wet && d < 32) {
							underWaterFlooded++;
						}
					} else if (s.is(Blocks.LAVA)) {
						lava++;
						if (y >= LAVA_FROM_Y) {
							highLava++;
						}
					}
					// Surface water beside or above the cave: a draining river, lake or sea.
					int[][] nbs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
					for (int[] nb : nbs) {
						int nx = lx + nb[0];
						int nz = lz + nb[2];
						if (nx < 0 || nz < 0 || nx >= side || nz >= side) {
							continue;
						}
						int ny = y + nb[1];
						if (ny > tops[nx * side + nz] && ny <= waterTops[nx * side + nz]) {
							contacts++;
						}
					}
				}
				// An opened top: the top block and the 2 below it are a cave (a pit of a windthrow, step V11, is shallower).
				if (cave(chunk.getBlockState(at.set(x, top, z))) && cave(chunk.getBlockState(at.set(x, top - 1, z)))
						&& cave(chunk.getBlockState(at.set(x, top - 2, z)))) {
					opened[lx][lz] = true;
					BlockState above = chunk.getBlockState(at.set(x, top + 1, z));
					boolean trunk = above.is(BlockTags.LOGS) || above.getBlock() instanceof pl.polishforests.block.TrunkBlock;
					if (trunk) {
						floatingTrunks++;
					} else if (!cave(above) && !above.is(BlockTags.LEAVES)) {
						floatingPlants++;
					}
					if ((trunk || !cave(above) && !above.is(BlockTags.LEAVES)) && faultsAt.size() < 6) {
						faultsAt.add(String.format(Locale.ROOT, "%s at (%d, %d, %d)", above, x, top + 1, z));
					}
				}
			}
		}
		findSpots(level, x0, z0, side, tops, opened, spots);
		int entrances = groups(opened);
		double km2 = side * side / 1e6;
		StringBuilder text = new StringBuilder();
		text.append("cave share by depth:");
		for (int b = 0; b < BINS.length; b++) {
			text.append(String.format(Locale.ROOT, " %d+ %.2f%%", BINS[b], allByDepth[b] == 0 ? Double.NaN
					: 100.0 * caveByDepth[b] / allByDepth[b]));
		}
		text.append(String.format(Locale.ROOT, "; cave water %d, lava %d (at or above Y %d: %d); entrances %d (%.0f per km2); "
				+ "under surface water (32 blocks): %d of %d blocks cave water; contacts with surface water %d; trunks over "
				+ "opened tops %d, other blocks over opened tops %d; spots %s", water, lava, LAVA_FROM_Y, highLava, entrances,
				entrances / km2, underWaterFlooded, underWater, contacts, floatingTrunks, floatingPlants, spots.keySet()));
		List<String> failures = new ArrayList<>();
		if (contacts > 0) {
			failures.add(name + ": " + contacts + " cave blocks beside the water of the surface");
		}
		if (floatingTrunks > 0) {
			failures.add(name + ": " + floatingTrunks + " trunks over opened tops " + faultsAt);
		}
		if (floatingPlants > 0) {
			failures.add(name + ": " + floatingPlants + " blocks over opened tops " + faultsAt);
		}
		if (highLava > 0) {
			failures.add(name + ": lava at or above Y " + LAVA_FROM_Y + " in " + highLava + " blocks");
		}
		section(level, place, name);
		return new Report(text.toString(), failures, spots, top0);
	}

	/** Camera spots of the cave kinds within the surveyed chunks (the first good one of each kind, by score). */
	private static void findSpots(ServerLevel level, int x0, int z0, int side, int[] tops, boolean[][] opened,
			Map<String, Spot> spots) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		Map<String, Spot> best = new HashMap<>();
		for (int lx = 8; lx < side - 8; lx += 3) {
			for (int lz = 8; lz < side - 8; lz += 3) {
				int x = x0 + lx;
				int z = z0 + lz;
				int top = tops[lx * side + lz];
				if (opened[lx][lz]) {
					int depth = 0;
					while (depth < 40 && level.getBlockState(at.set(x, top - depth, z)).isAir()) {
						depth++;
					}
					if (depth >= 5) {
						keep(best, new Spot("entrance", x, top + 7, z - 8, 0, 40, depth));
					}
				}
				for (int y = top - 6; y > minY + 6; y -= 2) {
					BlockState s = level.getBlockState(at.set(x, y, z));
					if (s.is(Blocks.LAVA) && y < -54 && level.getBlockState(at.set(x, y + 1, z)).isAir()) {
						keep(best, new Spot("lava", x, y + 3, z, 0, 35, 1));
						continue;
					}
					if (s.is(Blocks.WATER) && level.getBlockState(at.set(x, y + 1, z)).isAir()
							&& level.getBlockState(at.set(x, y + 2, z)).isAir()) {
						keep(best, new Spot("lake", x, y + 2, z, 0, 20, run(level, x, y, z, 1, 0) + run(level, x, y, z, 0, 1)));
						continue;
					}
					if (!s.isAir()) {
						continue;
					}
					int rx = run(level, x, y, z, 1, 0);
					int rz = run(level, x, y, z, 0, 1);
					int ry = runY(level, x, y, z);
					int wide = Math.max(rx, rz);
					int narrow = Math.min(rx, rz);
					float yaw = rx > rz ? 90 : 0;
					if (rx >= 9 && rz >= 9 && ry >= 6) {
						keep(best, new Spot("cheese", x, y - ry / 2 + 1, z, 0, 0, rx * rz * ry));
					} else if (ry >= 20 && narrow <= 7 && wide >= 12) {
						keep(best, new Spot("ravine", x, y - ry / 2, z, yaw, 0, ry * wide));
					} else if (wide >= 12 && narrow >= 3 && narrow <= 6 && ry >= 3 && ry <= 6) {
						keep(best, new Spot("spaghetti", x, y - ry / 2, z, yaw, 0, wide));
					} else if (wide >= 8 && narrow <= 2 && ry <= 3) {
						keep(best, new Spot("noodle", x, y - ry / 2, z, yaw, 0, wide));
					}
				}
			}
		}
		for (String kind : KINDS) {
			if (best.containsKey(kind)) {
				spots.put(kind, best.get(kind));
			}
		}
	}

	/**
	 * The spot with its feet moved (by at most 3 blocks, down first) so that the eye block is air: a spectator camera whose
	 * eye is inside rock sees through the ground (no cave culling), which is no picture of the cave.
	 */
	private static Spot inAir(ServerLevel level, Spot s) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int d = 0; d <= 3; d++) {
			for (int f : new int[] {s.y() - d, s.y() + d}) {
				if (level.getBlockState(at.set(s.x(), f + 1, s.z())).isAir()) {
					return new Spot(s.kind(), s.x(), f, s.z(), s.yaw(), s.pitch(), s.score());
				}
			}
		}
		return s;
	}

	private static void keep(Map<String, Spot> best, Spot s) {
		Spot old = best.get(s.kind());
		if (old == null || s.score() > old.score()) {
			best.put(s.kind(), s);
		}
	}

	/** Air run through (x, y, z) along x or z (both directions, up to 40 blocks). */
	private static int run(ServerLevel level, int x, int y, int z, int dx, int dz) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int n = 1;
		for (int k = 1; k <= 20 && level.getBlockState(at.set(x + k * dx, y, z + k * dz)).isAir(); k++) {
			n++;
		}
		for (int k = 1; k <= 20 && level.getBlockState(at.set(x - k * dx, y, z - k * dz)).isAir(); k++) {
			n++;
		}
		return n;
	}

	private static int runY(ServerLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int n = 1;
		for (int k = 1; k <= 40 && level.getBlockState(at.set(x, y - k, z)).isAir(); k++) {
			n++;
		}
		return n;
	}

	/** Number of 8-connected groups of opened columns. */
	private static int groups(boolean[][] opened) {
		int n = opened.length;
		boolean[][] seen = new boolean[n][n];
		int groups = 0;
		int[] stack = new int[n * n];
		for (int x = 0; x < n; x++) {
			for (int z = 0; z < n; z++) {
				if (!opened[x][z] || seen[x][z]) {
					continue;
				}
				groups++;
				int sp = 0;
				stack[sp++] = x * n + z;
				seen[x][z] = true;
				while (sp > 0) {
					int c = stack[--sp];
					for (int ax = Math.max(0, c / n - 1); ax <= Math.min(n - 1, c / n + 1); ax++) {
						for (int az = Math.max(0, c % n - 1); az <= Math.min(n - 1, c % n + 1); az++) {
							if (opened[ax][az] && !seen[ax][az]) {
								seen[ax][az] = true;
								stack[sp++] = ax * n + az;
							}
						}
					}
				}
			}
		}
		return groups;
	}

	/** The 16 vertical slices (x, y at each z) of the place's chunk down to the world bottom, one pixel per block. */
	private static void section(ServerLevel level, Place place, String name) {
		LevelChunk chunk = level.getChunk(place.cx(), place.cz());
		int minY = level.getMinY();
		int maxTop = minY;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 256; i++) {
			maxTop = Math.max(maxTop, chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, i >> 4,
					i & 15));
		}
		int height = maxTop + 8 - minY;
		int width = 16 * 18;
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				for (int y = minY; y < minY + height; y++) {
					BlockState s = chunk.getBlockState(at.set((place.cx() << 4) + x, y, (place.cz() << 4) + z));
					img.setRGB(z * 18 + x, minY + height - 1 - y, color(s));
				}
			}
		}
		String dir = System.getProperty("polishforests.caves.out", "build/caves");
		try {
			Files.createDirectories(Path.of(dir));
			ImageIO.write(img, "png", Path.of(dir, name + "_section.png").toFile());
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private static int color(BlockState s) {
		if (s.isAir()) {
			return 0xF4F1E8;
		}
		if (s.is(Blocks.WATER)) {
			return 0x2F5FD0;
		}
		if (s.is(Blocks.LAVA)) {
			return 0xF07818;
		}
		if (s.is(Blocks.BEDROCK)) {
			return 0x101010;
		}
		if (s.is(BlockTags.ORES)) {
			return 0xC03050;
		}
		if (s.is(Blocks.DEEPSLATE) || s.is(Blocks.TUFF)) {
			return 0x45454C;
		}
		if (s.is(BlockTags.BASE_STONE_OVERWORLD)) {
			return 0x8A8A8A;
		}
		if (s.is(Blocks.SAND) || s.is(Blocks.SANDSTONE)) {
			return 0xD8C890;
		}
		if (s.is(Blocks.GRAVEL)) {
			return 0x9A8F86;
		}
		if (s.is(Blocks.CLAY) || s.is(Blocks.MUD)) {
			return 0x8C93A6;
		}
		if (s.is(BlockTags.DIRT)) {
			return 0x7A5530;
		}
		if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) {
			return 0x2E6A2A;
		}
		return 0xB040B0;
	}

	/**
	 * Three windows cut into the ground in front of the place's chunk (16 × 24 blocks, 14 deep: the chunk south of it) at
	 * the surface, half way down and near the bottom, each shot from inside the window toward the cut face. The window is
	 * cleared on the server after loading its chunk, not with the {@code fill} command: since the windows are cut after all
	 * places, the player stands far away and {@code fill} would fail on an unloaded chunk without an error in the test, leaving
	 * the camera inside the rock (J1 review, round 2). A window that is not empty afterwards is a test failure.
	 */
	private static void cut(ClientGameTestContext context, TestSingleplayerContext sp, Place place, int top, String name,
			List<String> failures) {
		int minY = -64;
		int x0 = place.cx() << 4;
		int z0 = (place.cz() << 4) + 16;
		int[] levels = {top - 20, (top + minY) / 2, minY + 12};
		String[] names = {"surface", "middle", "bottom"};
		for (int k = 0; k < levels.length; k++) {
			int y0 = levels[k];
			int left = sp.getServer().computeOnServer(s -> clear(s.overworld(), x0, y0, z0));
			if (left > 0) {
				failures.add(name + "_cut_" + names[k] + ": " + left + " blocks left in the window");
			}
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.1f %.1f %.1f 180 0", x0 + 8.0, y0 + 11.0, z0 + 13.0));
			ViewReady.shot(context, sp, name + "_cut_" + names[k], null, 25);
		}
	}

	/** Sets the 16 × 24 × 14 window to air (block updates to the clients only) and returns the blocks that are not air after it. */
	private static int clear(ServerLevel level, int x0, int y0, int z0) {
		level.getChunk(x0 >> 4, z0 >> 4);
		BlockState air = Blocks.AIR.defaultBlockState();
		int left = 0;
		for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x0 + 15, y0 + 23, z0 + 13)) {
			if (!level.getBlockState(p).isAir()) {
				level.setBlock(p, air, Block.UPDATE_CLIENTS);
				left += level.getBlockState(p).isAir() ? 0 : 1;
			}
		}
		return left;
	}
}
