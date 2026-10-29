package pl.polishforests.gametest;

import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.cave.CaveBiomes;
import pl.polishforests.worldgen.chunk.ModBiomeKeys;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;

/**
 * Cave biomes and underground structures of step J2 (docs/04-pionowy-wycinek.md), {@code -Pgametest=cave_biomes} (only by
 * name), in each scale ({@code -Pscales}): a world of the preset (caves on), then
 * <ol>
 * <li>{@code /locate biome} of the four vanilla cave biomes from a lowland forest (the search of the biome source with the
 * vanilla radius 6400 and resolutions 32 × 64, also once as a command) and {@code /locate structure} of the ancient city,
 * the mineshaft, the trial chambers and the stronghold (the generator's map search, radius 100 chunks); the start of the
 * ancient city lies in the deep dark;</li>
 * <li>frames inside each cave biome (the best open spot of the biome near the located point), the ancient city, a geode,
 * a monster room and a mineshaft found near them, and F3 in the lush caves (the client's biome is the cave biome);</li>
 * <li>checks: the spawns of the lush caves have the axolotl, the deep dark has none; the deep dark around the city has no
 * water or lava of the aquifers (counted); the sulfur caves have bands of sulfur and cinnabar;</li>
 * <li>the forest over a lush cave before and after: the same world again with {@code -Dpolishforests.caveBiomes=off}
 * (set by the test for the second world), the surface blocks of 5 × 5 chunks compared block by block (expected: only the
 * azaleas over the lush caves with their rooted dirt, and the sulfur springs), and the same frame from the eyes.</li>
 * </ol>
 * Report {@code build/cave_biomes/<scale>.txt}, log lines {@code [cave_biomes]}, frames {@code cave_biomes_<scale>_*}.
 */
public final class CaveBiomesClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id("poland"));
	private static final ResourceKey<WorldPreset> PRESET_GAMEPLAY = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland_gameplay"));
	private static final String[] NAMES = {"lush", "dripstone", "sulfur", "deep_dark"};
	/** Blocks that the cave biomes may change at the surface: the azaleas of the lush caves and the sulfur springs. */
	private static final List<Block> SURFACE_ALLOWED = List.of(Blocks.AZALEA, Blocks.FLOWERING_AZALEA, Blocks.AZALEA_LEAVES,
			Blocks.FLOWERING_AZALEA_LEAVES, Blocks.OAK_LOG, Blocks.ROOTED_DIRT, Blocks.HANGING_ROOTS, Blocks.MOSS_BLOCK,
			Blocks.MOSS_CARPET, Blocks.SULFUR, Blocks.CINNABAR, Blocks.WATER);

	/** A camera spot. */
	private record Spot(int x, int y, int z, float yaw, float pitch, int score) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"cave_biomes".equals(System.getProperty("polishforests.gametest", "all"))) {
			return;
		}
		String scales = System.getProperty("polishforests.scales", "");
		List<String> failures = new ArrayList<>();
		for (PolandScale scale : PolandScale.values()) {
			if (!scales.isBlank() && !scales.contains(scale.getSerializedName())) {
				continue;
			}
			String name = "cave_biomes_" + scale.getSerializedName();
			ResourceKey<WorldPreset> preset = scale == PolandScale.REALISTIC ? PRESET : PRESET_GAMEPLAY;
			StringBuilder report = new StringBuilder();
			int[] forest;
			Map<String, Integer> after;
			try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset)).create()) {
				prepare(context, sp);
				forest = sp.getServer().computeOnServer(CaveBiomesClientGameTest::forest);
				report.append(String.format(Locale.ROOT, "forest over the lush caves at (%d, %d), lush caves at Y %d%n", forest[0],
						forest[1], forest[2]));
				BlockPos origin = new BlockPos(forest[0], 0, forest[1]);
				// 1. Locate.
				List<BlockPos> located = new ArrayList<>();
				for (int k = 0; k < CaveBiomes.KEYS.size(); k++) {
					ResourceKey<Biome> key = CaveBiomes.KEYS.get(k);
					String line = sp.getServer().computeOnServer(s -> locateBiome(s.overworld(), key, origin, located));
					report.append(line).append('\n');
					if (line.contains("not found")) {
						failures.add(scale.getSerializedName() + ": " + line);
					}
				}
				String locateLush = sp.getServer().computeOnServer(s -> command(s, origin, "locate biome minecraft:lush_caves"));
				report.append(locateLush).append('\n');
				Map<String, BlockPos> structures = new TreeMap<>();
				for (ResourceKey<Structure> key : List.of(BuiltinStructures.ANCIENT_CITY, BuiltinStructures.MINESHAFT,
						BuiltinStructures.TRIAL_CHAMBERS, BuiltinStructures.STRONGHOLD)) {
					String line = sp.getServer().computeOnServer(s -> locateStructure(s.overworld(), key, origin, structures));
					report.append(line).append('\n');
					if (line.contains("not found") && key != BuiltinStructures.STRONGHOLD) {
						failures.add(scale.getSerializedName() + ": " + line);
					}
				}
				String locateCity = sp.getServer().computeOnServer(s -> command(s, origin, "locate structure minecraft:ancient_city"));
				report.append(locateCity).append('\n');
				// 2. Frames inside the cave biomes, and the checks of each.
				Spot lush = null;
				for (int k = 0; k < CaveBiomes.KEYS.size(); k++) {
					if (located.get(k) == null) {
						continue;
					}
					ResourceKey<Biome> key = CaveBiomes.KEYS.get(k);
					BlockPos at = located.get(k);
					Spot spot = sp.getServer().computeOnServer(s -> spot(s.overworld(), at, 40,
							p -> s.overworld().getBiome(p).is(key)));
					if (spot == null) {
						failures.add(scale.getSerializedName() + ": no open spot in " + NAMES[k] + " near " + at.toShortString());
						continue;
					}
					if (k == CaveBiomes.LUSH) {
						lush = spot;
					}
					shot(context, sp, spot, name + "_" + NAMES[k]);
					String check = sp.getServer().computeOnServer(s -> checkBiome(s.overworld(), key, at));
					report.append(NAMES[k]).append(": ").append(check).append('\n');
					if (check.startsWith("FAIL")) {
						failures.add(scale.getSerializedName() + " " + NAMES[k] + ": " + check);
					}
				}
				if (lush != null) {
					f3(context, sp, lush, name + "_f3", failures);
				}
				// The structures and the features near them.
				for (Map.Entry<String, BlockPos> e : structures.entrySet()) {
					if (e.getKey().equals("stronghold") || e.getKey().equals("trial_chambers")) {
						continue;
					}
					ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, net.minecraft.resources.Identifier
							.withDefaultNamespace(e.getKey()));
					Spot spot = sp.getServer().computeOnServer(s -> structureSpot(s.overworld(), key, e.getValue(), report));
					if (spot == null) {
						failures.add(scale.getSerializedName() + ": no spot in " + e.getKey());
						continue;
					}
					shot(context, sp, spot, name + "_" + e.getKey());
				}
				BlockPos around = located.get(CaveBiomes.DRIPSTONE) != null ? located.get(CaveBiomes.DRIPSTONE) : origin;
				for (String feature : List.of("geode", "dungeon")) {
					Spot spot = sp.getServer().computeOnServer(s -> featureSpot(s.overworld(), feature, around, report));
					if (spot == null) {
						failures.add(scale.getSerializedName() + ": no " + feature + " found");
						continue;
					}
					shot(context, sp, spot, name + "_" + feature);
				}
				// 4. The forest over the lush caves, after J2.
				after = sp.getServer().computeOnServer(s -> surface(s.overworld(), forest));
				forestShot(context, sp, forest, name + "_forest_after");
			}
			// 4. The same world without the cave biomes (step J1), the same forest before J2.
			System.setProperty("polishforests.caveBiomes", "off");
			try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset)).create()) {
				prepare(context, sp);
				Map<String, Integer> before = sp.getServer().computeOnServer(s -> surface(s.overworld(), forest));
				forestShot(context, sp, forest, name + "_forest_before");
				String diff = compare(before, after);
				report.append("forest surface before/after: ").append(diff).append('\n');
				if (diff.startsWith("FAIL")) {
					failures.add(scale.getSerializedName() + ": " + diff);
				}
			} finally {
				System.clearProperty("polishforests.caveBiomes");
			}
			PolishForests.LOG.info("[cave_biomes] {}:\n{}", scale.getSerializedName(), report);
			write(scale.getSerializedName(), report.toString());
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Cave biomes: " + String.join("; ", failures));
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

	private static void prepare(ClientGameTestContext context, TestSingleplayerContext sp) {
		sp.getServer().runCommand("gamerule advance_time false");
		sp.getServer().runCommand("gamerule advance_weather false");
		sp.getServer().runCommand("time set 6000");
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("gamemode spectator @a");
		sp.getServer().runCommand("effect give @a minecraft:night_vision infinite 0 true");
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			// As the caves test (J1): without the renderer's cave culling, which leaves out sections seen through openings.
			mc.smartCull = false;
		});
	}

	/**
	 * A lowland forest over the lush caves: from the origin along a spiral of 256-block steps, the first column whose
	 * habitat is a forest and whose biome 40–100 blocks below the top is the lush caves; {x, z, Y of the lush caves}.
	 */
	private static int[] forest(MinecraftServer server) {
		ServerLevel level = server.overworld();
		PolandChunkGenerator gen = (PolandChunkGenerator) level.getChunkSource().getGenerator();
		int x = 0;
		int z = 0;
		int dx = 1;
		int dz = 0;
		int leg = 1;
		int inLeg = 0;
		for (int i = 0; i < 4_000; i++) {
			int bx = x * 256;
			int bz = z * 256;
			int top = gen.getBaseHeight(bx, bz, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
			if (level.getBiome(new BlockPos(bx, top + 2, bz)).is(ModBiomeKeys.FORESTS)) {
				for (int d = 40; d <= 100; d += 8) {
					if (level.getBiome(new BlockPos(bx, top - d, bz)).is(Biomes.LUSH_CAVES)) {
						return new int[] {bx, bz, top - d};
					}
				}
			}
			x += dx;
			z += dz;
			if (++inLeg == leg) {
				inLeg = 0;
				int t = dx;
				dx = -dz;
				dz = t;
				if (dz == 0) {
					leg++;
				}
			}
		}
		throw new AssertionError("No forest over the lush caves within 4000 steps");
	}

	private static String locateBiome(ServerLevel level, ResourceKey<Biome> key, BlockPos origin, List<BlockPos> located) {
		long t0 = System.nanoTime();
		Pair<BlockPos, Holder<Biome>> hit = level.findClosestBiome3d(b -> b.is(key), origin, 6400, 32, 64);
		double ms = (System.nanoTime() - t0) / 1e6;
		located.add(hit == null ? null : hit.getFirst());
		if (hit == null) {
			return String.format(Locale.ROOT, "locate biome %s: not found within 6400 (%.0f ms)", key.identifier(), ms);
		}
		BlockPos p = hit.getFirst();
		Holder<Biome> actual = level.getBiome(p);
		return String.format(Locale.ROOT, "locate biome %s: (%d, %d, %d), %.0f blocks away, getBiome there %s (%.0f ms)",
				key.identifier(), p.getX(), p.getY(), p.getZ(), Math.sqrt(p.distSqr(origin.atY(p.getY()))),
				actual.getRegisteredName(), ms);
	}

	private static String locateStructure(ServerLevel level, ResourceKey<Structure> key, BlockPos origin,
			Map<String, BlockPos> found) {
		Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		long t0 = System.nanoTime();
		Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator().findNearestMapStructure(level,
				HolderSet.direct(structure), origin, 100, false);
		double ms = (System.nanoTime() - t0) / 1e6;
		if (hit == null) {
			return String.format(Locale.ROOT, "locate structure %s: not found within 100 chunks (%.0f ms)", key.identifier(), ms);
		}
		found.put(key.identifier().getPath(), hit.getFirst());
		return String.format(Locale.ROOT, "locate structure %s: (%d, %d), %.0f blocks away (%.0f ms)", key.identifier(),
				hit.getFirst().getX(), hit.getFirst().getZ(), Math.sqrt(hit.getFirst().distSqr(origin)), ms);
	}

	/** Runs a command at {@code at} (as a player would) and returns its message. */
	private static String command(MinecraftServer server, BlockPos at, String command) {
		List<Component> messages = new ArrayList<>();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				messages.add(message);
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		}).withLevel(server.overworld()).withPosition(Vec3.atCenterOf(at)), command);
		return "/" + command + ": " + (messages.isEmpty() ? "no message" : messages.getFirst().getString());
	}

	/** Loads the chunks within {@code radius} blocks of {@code center} to the full status. */
	private static void load(ServerLevel level, BlockPos center, int radius) {
		for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
			for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
	}

	/**
	 * The most open air spot within {@code radius} blocks (and 32 blocks up or down) of {@code center} that passes
	 * {@code inside}, with a floor below; the camera looks along the longest run of air.
	 */
	private static Spot spot(ServerLevel level, BlockPos center, int radius, Predicate<BlockPos> inside) {
		load(level, center, radius + 24);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		Spot best = null;
		for (int x = center.getX() - radius; x <= center.getX() + radius; x += 2) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += 2) {
				for (int y = center.getY() - 32; y <= center.getY() + 32; y += 2) {
					if (y <= level.getMinY() + 1 || !level.getBlockState(at.set(x, y, z)).isAir()
							|| !level.getBlockState(at.set(x, y + 1, z)).isAir()) {
						continue;
					}
					int floor = 0;
					while (floor < 5 && level.getBlockState(at.set(x, y - floor - 1, z)).isAir()) {
						floor++;
					}
					if (floor >= 5 || !inside.test(at.set(x, y, z))) {
						continue;
					}
					int[] runs = {run(level, x, y + 1, z, 1, 0), run(level, x, y + 1, z, -1, 0), run(level, x, y + 1, z, 0, 1),
							run(level, x, y + 1, z, 0, -1)};
					int dir = 0;
					for (int d = 1; d < 4; d++) {
						if (runs[d] > runs[dir]) {
							dir = d;
						}
					}
					int score = runs[0] + runs[1] + runs[2] + runs[3];
					if (best == null || score > best.score()) {
						float yaw = switch (dir) {
							case 0 -> -90.0F;
							case 1 -> 90.0F;
							case 2 -> 0.0F;
							default -> 180.0F;
						};
						best = new Spot(x, y - floor, z, yaw, 15.0F, score);
					}
				}
			}
		}
		return best;
	}

	/** Air run from (x, y, z) along (dx, dz), up to 24 blocks. */
	private static int run(ServerLevel level, int x, int y, int z, int dx, int dz) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int n = 0;
		while (n < 24 && level.getBlockState(at.set(x + dx * (n + 1), y, z + dz * (n + 1))).isAir()) {
			n++;
		}
		return n;
	}

	/**
	 * Checks of a cave biome near its located point: the spawns (lush: the axolotl; deep dark: none), the water and lava in
	 * the deep dark (counted: the vanilla aquifers keep it dry), the bands of the sulfur caves.
	 */
	private static String checkBiome(ServerLevel level, ResourceKey<Biome> key, BlockPos at) {
		load(level, at, 24);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int cells = 0;
		int fluid = 0;
		int bands = 0;
		for (int x = at.getX() - 24; x <= at.getX() + 24; x++) {
			for (int z = at.getZ() - 24; z <= at.getZ() + 24; z++) {
				for (int y = at.getY() - 24; y <= at.getY() + 24; y++) {
					if (y <= level.getMinY() || !level.getBiome(p.set(x, y, z)).is(key)) {
						continue;
					}
					cells++;
					BlockState s = level.getBlockState(p);
					if (s.is(Blocks.WATER) || s.is(Blocks.LAVA)) {
						fluid++;
					}
					if (s.is(Blocks.SULFUR) || s.is(Blocks.CINNABAR)) {
						bands++;
					}
				}
			}
		}
		String spawns = spawns(level, at);
		StringBuilder out = new StringBuilder(String.format(Locale.ROOT, "%d blocks of the biome, %d water or lava, %d sulfur or "
				+ "cinnabar; spawns %s", cells, fluid, bands, spawns));
		boolean fail = cells == 0
				|| key == Biomes.LUSH_CAVES && !spawns.contains("axolotl")
				|| key == Biomes.DEEP_DARK && !spawns.equals("none")
				|| key == Biomes.SULFUR_CAVES && bands == 0;
		return (fail ? "FAIL " : "") + out;
	}

	/** The mob types the natural spawning picks from at the position, over all categories. */
	private static String spawns(ServerLevel level, BlockPos at) {
		List<String> types = new ArrayList<>();
		for (MobCategory category : MobCategory.values()) {
			level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), category, at).unwrap()
					.forEach(w -> types.add(BuiltInRegistries.ENTITY_TYPE.getKey(w.value().type()).getPath()));
		}
		return types.isEmpty() ? "none" : String.join(",", types);
	}

	/** An open spot inside a structure (its start at chunk {@code chunk}); the start of the ancient city lies in the deep dark. */
	private static Spot structureSpot(ServerLevel level, ResourceKey<Structure> key, BlockPos chunk, StringBuilder report) {
		Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValueOrThrow(key);
		StructureStart start = level.structureManager().getStartForStructure(structure,
				level.getChunk(chunk.getX() >> 4, chunk.getZ() >> 4));
		if (start == null || !start.isValid()) {
			report.append(key.identifier().getPath()).append(": no valid start at ").append(chunk.toShortString()).append('\n');
			return null;
		}
		BoundingBox box = start.getBoundingBox();
		BlockPos center = box.getCenter();
		String biome = level.getBiome(start.getPieces().getFirst().getBoundingBox().getCenter()).getRegisteredName();
		report.append(String.format(Locale.ROOT, "%s: start %s, box %s, %d pieces, biome of the first piece %s%n",
				key.identifier().getPath(), chunk.toShortString(), box, start.getPieces().size(), biome));
		BoundingBox first = start.getPieces().getFirst().getBoundingBox();
		BlockPos at = new BlockPos(first.getCenter().getX(), first.minY() + 2, first.getCenter().getZ());
		return spot(level, at, 20, p -> box.isInside(p));
	}

	/** A geode (an air cell with amethyst around it) or a monster room (a spawner) within 6 chunks of {@code around}. */
	private static Spot featureSpot(ServerLevel level, String feature, BlockPos around, StringBuilder report) {
		boolean geode = feature.equals("geode");
		int radius = 96;
		load(level, around, radius);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int top = Math.min(level.getMaxY(), around.getY() + 160);
		int bottom = level.getMinY() + 2;
		for (int r = 0; r <= radius; r += 16) {
			for (int x = around.getX() - r; x <= around.getX() + r; x++) {
				for (int z = around.getZ() - r; z <= around.getZ() + r; z++) {
					if (Math.max(Math.abs(x - around.getX()), Math.abs(z - around.getZ())) < r - 15) {
						continue;
					}
					for (int y = bottom; y <= (geode ? Math.min(top, 40) : top); y++) {
						BlockState s = level.getBlockState(at.set(x, y, z));
						if (geode ? s.is(Blocks.BUDDING_AMETHYST) : s.is(Blocks.SPAWNER)) {
							report.append(String.format(Locale.ROOT, "%s at (%d, %d, %d)%n", feature, x, y, z));
							BlockPos found = new BlockPos(x, y, z);
							Spot spot = spot(level, found, 6, p -> p.distSqr(found) <= 49);
							if (spot != null) {
								return spot;
							}
						}
					}
				}
			}
		}
		report.append(feature).append(": none within ").append(radius).append(" blocks of ").append(around.toShortString())
				.append('\n');
		return null;
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, Spot spot, String name) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.1f %.1f %.1f %.0f %.0f", spot.x() + 0.5, spot.y() + 0.1,
				spot.z() + 0.5, spot.yaw(), spot.pitch()));
		PolishForests.LOG.info("[cave_biomes] {} at ({}, {}, {})", name, spot.x(), spot.y(), spot.z());
		ViewReady.shot(context, sp, name, null, 25);
	}

	/** F3 with the biome line in the lush caves: the client's biome is the cave biome. */
	private static void f3(ClientGameTestContext context, TestSingleplayerContext sp, Spot spot, String name,
			List<String> failures) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.1f %.1f %.1f %.0f %.0f", spot.x() + 0.5, spot.y() + 0.1,
				spot.z() + 0.5, spot.yaw(), spot.pitch()));
		ViewReady.Result ready = ViewReady.beforeShot(context, sp, name, null, 15, 0);
		String clientBiome = context.computeOnClient(mc -> mc.level.getBiome(mc.player.blockPosition()).getRegisteredName());
		if (!clientBiome.equals("minecraft:lush_caves")) {
			failures.add(name + ": F3 biome on the client is " + clientBiome);
		}
		context.runOnClient(mc -> {
			mc.debugEntries.setStatus(DebugScreenEntries.BIOME, DebugScreenEntryStatus.IN_OVERLAY);
			mc.debugEntries.setOverlayVisible(true);
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.waitTicks(20);
		ViewReady.take(context, name, null, ready);
		context.runOnClient(mc -> mc.debugEntries.setOverlayVisible(false));
		PolishForests.LOG.info("[cave_biomes] {}: client biome {}", name, clientBiome);
	}

	/** The forest frame from the eyes, looking north over the place. */
	private static void forestShot(ClientGameTestContext context, TestSingleplayerContext sp, int[] forest, String name) {
		int top = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			level.getChunk(forest[0] >> 4, (forest[1] + 24) >> 4);
			return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, forest[0], forest[1] + 24);
		});
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.1f %.1f %.1f 180 5", forest[0] + 0.5, top + 0.62,
				forest[1] + 24.5));
		ViewReady.shot(context, sp, name, null, 25);
	}

	/**
	 * The surface of the 5 × 5 chunks around the forest place: per block from 8 below to 40 above the top of the column
	 * ({@code WORLD_SURFACE_WG} of the generator), the block id by position.
	 */
	private static Map<String, Integer> surface(ServerLevel level, int[] forest) {
		Map<String, Integer> blocks = new TreeMap<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int cx0 = forest[0] >> 4;
		int cz0 = forest[1] >> 4;
		for (int cx = cx0 - 2; cx <= cx0 + 2; cx++) {
			for (int cz = cz0 - 2; cz <= cz0 + 2; cz++) {
				level.getChunk(cx, cz);
			}
		}
		for (int x = (cx0 - 2) << 4; x < (cx0 + 3) << 4; x++) {
			for (int z = (cz0 - 2) << 4; z < (cz0 + 3) << 4; z++) {
				int top = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
						level.getChunkSource().randomState());
				for (int y = top - 8; y <= top + 40; y++) {
					BlockState s = level.getBlockState(at.set(x, y, z));
					blocks.put(x + " " + y + " " + z, Block.getId(s));
				}
			}
		}
		return blocks;
	}

	/** Differences of the surface before and after: FAIL when a block outside {@link #SURFACE_ALLOWED} changed. */
	private static String compare(Map<String, Integer> before, Map<String, Integer> after) {
		Map<String, Integer> pairs = new TreeMap<>();
		int changed = 0;
		int unexpected = 0;
		for (Map.Entry<String, Integer> e : before.entrySet()) {
			Integer a = after.get(e.getKey());
			if (a == null || a.equals(e.getValue())) {
				continue;
			}
			changed++;
			BlockState b0 = Block.stateById(e.getValue());
			BlockState b1 = Block.stateById(a);
			boolean allowed = SURFACE_ALLOWED.contains(b0.getBlock()) || SURFACE_ALLOWED.contains(b1.getBlock());
			if (!allowed) {
				unexpected++;
			}
			String pair = BuiltInRegistries.BLOCK.getKey(b0.getBlock()).getPath() + "->"
					+ BuiltInRegistries.BLOCK.getKey(b1.getBlock()).getPath() + (allowed ? "" : "(!)");
			pairs.merge(pair, 1, Integer::sum);
		}
		String text = String.format(Locale.ROOT, "%d of %d blocks changed, %d unexpected: %s", changed, before.size(), unexpected,
				pairs);
		return unexpected > 0 ? "FAIL " + text : text;
	}

	private static void write(String scale, String text) {
		Path dir = Path.of(System.getProperty("polishforests.caveBiomes.out", "build/cave_biomes"));
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(scale + ".txt"), text);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}
