package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.PolishForests;
import pl.polishforests.climate.BiomeClimateAccess;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.LandscapeModel;

/**
 * Habitat biomes in the game (step S5, docs/03-m2-biomy.md §3.5, §10, §12.3), in both world scales: the world starts
 * with the mod's 36 biomes, {@code generator.validate()} (feature order) passes, {@code level.getBiome} equals the
 * classifier at 8 places with different biomes, F3 shows {@code polishforests:*} and the habitat line (screenshot
 * {@code habitats_f3_<scale>}), {@code /locate structure} finds a stronghold, a mineshaft and trial chambers,
 * {@code /locate biome polishforests:oak_hornbeam_forest} takes less than 2 s, and the BIOMES stage takes at most
 * 0.2 ms per chunk. Runs when {@code -Dpolishforests.gametest} is {@code habitats} or {@code all}.
 */
public final class HabitatsClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int PLACES = 8;
	/** Spiral step of the place search in blocks. */
	private static final int SEARCH_STEP = 52;
	private static final double LOCATE_BIOME_LIMIT_MS = 2_000;
	private static final double BIOMES_LIMIT_MS = 0.2;

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polishforests.gametest", "all");
		if (!mode.equals("habitats") && !mode.equals("all")) {
			return;
		}
		for (PolandScale scale : PolandScale.values()) {
			ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET,
					PolishForests.id(scale == PolandScale.REALISTIC ? "poland" : "poland_gameplay"));
			try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> select(ui, preset)).create()) {
				sp.getServer().runCommand("gamerule advance_time false");
				sp.getServer().runCommand("gamerule advance_weather false");
				sp.getServer().runCommand("time set 6000");
				sp.getServer().runCommand("weather clear");
				sp.getServer().runCommand("gamemode spectator @a");
				String name = scale.getSerializedName();
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(HabitatsClientGameTest::registry));
				List<int[]> places = sp.getServer().computeOnServer(HabitatsClientGameTest::findPlaces);
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(s -> checkPlaces(s, places)));
				f3(context, sp, places.getFirst(), name);
				for (String command : List.of("locate structure minecraft:stronghold", "locate structure minecraft:mineshaft",
						"locate structure minecraft:trial_chambers", "locate biome polishforests:oak_hornbeam_forest")) {
					PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(s -> locate(s, command)));
				}
				// The worst case of /locate biome: a biome absent within 6.4 km scans the whole spiral (only reported).
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(
						s -> locateTime(s, "locate biome polishforests:dwarf_pine_scrub")));
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(HabitatsClientGameTest::biomesPerChunk));
				PolishForests.LOG.info("[habitats] {}: {}", name, sp.getServer().computeOnServer(HabitatsClientGameTest::habitatMisses));
			}
		}
	}

	private static void select(WorldCreationUiState ui, ResourceKey<WorldPreset> preset) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		// The test world builder turns structures off by default; /locate structure needs them.
		ui.setGenerateStructures(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(preset)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier())));
	}

	private static PolandChunkGenerator generator(MinecraftServer server) {
		if (!(server.overworld().getChunkSource().getGenerator() instanceof PolandChunkGenerator gen)) {
			throw new AssertionError("The overworld does not use the Poland generator");
		}
		return gen;
	}

	/** The biome source has exactly the 36 biomes of the mod, each with a climate profile; the feature order is valid. */
	private static String registry(MinecraftServer server) {
		PolandChunkGenerator gen = generator(server);
		// Throws on a "Feature order cycle".
		gen.validate();
		int n = 0;
		for (Holder<Biome> biome : gen.getBiomeSource().possibleBiomes()) {
			String id = biome.getRegisteredName();
			if (!id.startsWith(PolishForests.MOD_ID + ":")) {
				throw new AssertionError("Biome from outside the mod in the biome source: " + id);
			}
			if (BiomeClimateAccess.climate(biome.value()) == null) {
				throw new AssertionError("Biome without a climate profile: " + id);
			}
			n++;
		}
		if (n != HabitatBiome.values().length) {
			throw new AssertionError("The biome source has " + n + " biomes instead of " + HabitatBiome.values().length);
		}
		return "generator.validate() passed; biome source with " + n + " biomes polishforests:*, all with a climate profile, "
				+ "settings " + gen.settings();
	}

	/** BIOMES stage of 64 new chunks (8 × 8, far from the spawn): mean time per chunk on one thread. */
	private static String biomesPerChunk(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long nanos0 = PolandChunkGenerator.BIOME_NANOS.sum();
		long classify0 = PolandChunkGenerator.BIOME_CLASSIFY_NANOS.sum();
		long chunks0 = PolandChunkGenerator.BIOME_CHUNKS.sum();
		int ox = -50_000 >> 4;
		int oz = 30_000 >> 4;
		for (int cx = 0; cx < 8; cx++) {
			for (int cz = 0; cz < 8; cz++) {
				level.getChunkSource().getChunk(ox + cx, oz + cz, ChunkStatus.BIOMES, true);
			}
		}
		long chunks = PolandChunkGenerator.BIOME_CHUNKS.sum() - chunks0;
		double ms = (PolandChunkGenerator.BIOME_NANOS.sum() - nanos0) / 1e6 / Math.max(1, chunks);
		double classifyMs = (PolandChunkGenerator.BIOME_CLASSIFY_NANOS.sum() - classify0) / 1e6 / Math.max(1, chunks);
		PolishForests.LOG.info(String.format(Locale.ROOT, "[habitats] BIOMES %.3f ms per chunk, of which sampling and "
				+ "classifying the 16 columns %.3f ms (%d chunks)", ms, classifyMs, chunks));
		if (chunks < 64) {
			throw new AssertionError("BIOMES measured on only " + chunks + " chunks");
		}
		if (ms > BIOMES_LIMIT_MS) {
			throw new AssertionError(String.format(Locale.ROOT, "BIOMES %.3f ms per chunk (budget %.1f ms)", ms, BIOMES_LIMIT_MS));
		}
		return String.format(Locale.ROOT, "BIOMES %.3f ms per chunk (%d chunks, budget %.1f ms)", ms, chunks, BIOMES_LIMIT_MS);
	}

	/**
	 * Chunk habitats ({@code ChunkHabitats}, §8.3): 36 new chunks generated to the full status in one area; the tree
	 * stand must find the attachment written in {@code fill()} in at least 99.5% of the chunks ({@code HABITAT_MISS},
	 * budget §3.6), and no chunk keeps the attachment after its last stage.
	 */
	private static String habitatMisses(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long miss0 = ModFeatures.HABITAT_MISS.sum();
		long chunks0 = PolandChunkGenerator.CHUNKS.sum();
		int ox = 60_000 >> 4;
		int oz = -40_000 >> 4;
		int kept = 0;
		for (int cx = 0; cx < 6; cx++) {
			for (int cz = 0; cz < 6; cz++) {
				if (level.getChunk(ox + cx, oz + cz).hasAttached(ModFeatures.CHUNK_HABITATS)) {
					kept++;
				}
			}
		}
		long misses = ModFeatures.HABITAT_MISS.sum() - miss0;
		long chunks = PolandChunkGenerator.CHUNKS.sum() - chunks0;
		if (chunks < 36 || misses > 0.005 * chunks) {
			throw new AssertionError("Chunk habitats missing in " + misses + " of " + chunks + " chunks");
		}
		if (kept > 0) {
			throw new AssertionError(kept + " full chunks still hold the chunk habitats");
		}
		return String.format(Locale.ROOT, "chunk habitats missing in %d of %d chunks with terrain, none kept in full chunks",
				misses, chunks);
	}

	/**
	 * Places with {@value #PLACES} different biomes along a spiral from the origin, each at the center of a quart column
	 * whose 3 × 3 quart neighborhood has the same biome (so the biome zoom of {@code getBiome} cannot pick a neighbor).
	 */
	private static List<int[]> findPlaces(MinecraftServer server) {
		PolandChunkGenerator gen = generator(server);
		long seed = server.overworld().getSeed();
		LandscapeModel m = gen.model(seed);
		HabitatClassifier k = gen.classifier(seed);
		Map<HabitatBiome, int[]> found = new LinkedHashMap<>();
		int x = 0;
		int z = 0;
		int dx = 1;
		int dz = 0;
		int leg = 1;
		int stepsInLeg = 0;
		int turns = 0;
		for (int i = 0; i < 200_000 && found.size() < PLACES; i++) {
			int qx = QuartPos.fromBlock(x * SEARCH_STEP);
			int qz = QuartPos.fromBlock(z * SEARCH_STEP);
			HabitatBiome b = biome(m, k, qx, qz);
			if (!found.containsKey(b) && uniform(m, k, qx, qz, b)) {
				found.put(b, new int[] {QuartPos.toBlock(qx) + 2, QuartPos.toBlock(qz) + 2, b.ordinal()});
			}
			x += dx;
			z += dz;
			if (++stepsInLeg == leg) {
				stepsInLeg = 0;
				int t = dx;
				dx = -dz;
				dz = t;
				if (++turns % 2 == 0) {
					leg++;
				}
			}
		}
		if (found.size() < PLACES) {
			throw new AssertionError("Only " + found.size() + " biomes found for the comparison: " + found.keySet());
		}
		return new ArrayList<>(found.values());
	}

	private static HabitatBiome biome(LandscapeModel m, HabitatClassifier k, int qx, int qz) {
		int bx = QuartPos.toBlock(qx) + 2;
		int bz = QuartPos.toBlock(qz) + 2;
		return Habitat.biome(k.classify(m.sample(bx, bz), bx, bz));
	}

	private static boolean uniform(LandscapeModel m, HabitatClassifier k, int qx, int qz, HabitatBiome b) {
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				if ((i != 0 || j != 0) && biome(m, k, qx + i, qz + j) != b) {
					return false;
				}
			}
		}
		return true;
	}

	/** {@code level.getBiome} (generated chunk, biome zoom) equals the classifier at every place, at the ground and above. */
	private static String checkPlaces(MinecraftServer server, List<int[]> places) {
		ServerLevel level = server.overworld();
		StringBuilder report = new StringBuilder("getBiome = classifier at");
		for (int[] p : places) {
			level.getChunk(p[0] >> 4, p[1] >> 4);
			int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, p[0], p[1]);
			String expected = PolishForests.id(HabitatBiome.of(p[2]).id()).toString();
			for (int y : new int[] {ground, ground + 40, level.getMinY() + 8}) {
				String actual = level.getBiome(new BlockPos(p[0], y, p[1])).getRegisteredName();
				if (!actual.equals(expected)) {
					throw new AssertionError(String.format(Locale.ROOT, "getBiome at (%d, %d, %d) is %s, the classifier says %s",
							p[0], y, p[1], actual, expected));
				}
			}
			report.append(String.format(Locale.ROOT, " (%d, %d) %s;", p[0], p[1], HabitatBiome.of(p[2]).id()));
		}
		return report.toString();
	}

	/**
	 * F3 with the biome and the generator lines at the first place: the client biome is {@code polishforests:*}, the
	 * server's generator lines contain the habitat; screenshot {@code habitats_f3_<scale>}.
	 */
	private static void f3(ClientGameTestContext context, TestSingleplayerContext sp, int[] place, String name) {
		int ground = sp.getServer().computeOnServer(s -> s.overworld().getChunkSource().getGenerator().getBaseHeight(place[0],
				place[1], Heightmap.Types.WORLD_SURFACE_WG, s.overworld(), s.overworld().getChunkSource().randomState()));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 30.0 25.0", place[0], ground + 40, place[1]));
		context.waitTicks(20 * 15);
		String lines = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			List<String> out = new ArrayList<>();
			// As the F3 entry "chunk_generation_stats" does: the generator's lines, then the biome source's.
			BlockPos feet = new BlockPos(place[0], ground + 1, place[1]);
			level.getChunkSource().getGenerator().addDebugScreenInfo(out, level.getChunkSource().randomState(), feet, null);
			level.getChunkSource().getGenerator().getBiomeSource().addDebugInfo(out, feet, null);
			return String.join(" | ", out);
		});
		if (!lines.contains("Habitat: polishforests:")) {
			throw new AssertionError("The generator's F3 lines have no habitat: " + lines);
		}
		String clientBiome = context.computeOnClient(mc -> mc.level.getBiome(mc.player.blockPosition()).getRegisteredName());
		if (!clientBiome.startsWith(PolishForests.MOD_ID + ":")) {
			throw new AssertionError("F3 biome on the client is " + clientBiome);
		}
		context.runOnClient(mc -> {
			mc.debugEntries.setStatus(DebugScreenEntries.BIOME, DebugScreenEntryStatus.IN_OVERLAY);
			mc.debugEntries.setStatus(DebugScreenEntries.CHUNK_GENERATION_STATS, DebugScreenEntryStatus.IN_OVERLAY);
			mc.debugEntries.setOverlayVisible(true);
		});
		context.waitTicks(20);
		context.takeScreenshot("habitats_f3_" + name);
		context.runOnClient(mc -> mc.debugEntries.setOverlayVisible(false));
		PolishForests.LOG.info("[habitats] {}: F3 biome {}, generator lines: {}", name, clientBiome, lines);
	}

	/** Runs a locate command at the origin without checking the result; returns the message and the time. */
	private static String locateTime(MinecraftServer server, String command) {
		List<Component> messages = new ArrayList<>();
		long t0 = System.nanoTime();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture(messages))
				.withLevel(server.overworld()).withPosition(new Vec3(0.5, 100, 0.5)), command);
		double ms = (System.nanoTime() - t0) / 1e6;
		return String.format(Locale.ROOT, "/%s: %s (%.0f ms)", command, messages.isEmpty() ? "no message"
				: messages.getFirst().getString(), ms);
	}

	/** A command source that collects the messages. */
	private static CommandSource capture(List<Component> messages) {
		return new CommandSource() {
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
		};
	}

	/** Runs a locate command at the origin and checks its success message; returns the message and the time. */
	private static String locate(MinecraftServer server, String command) {
		List<Component> messages = new ArrayList<>();
		long t0 = System.nanoTime();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture(messages))
				.withLevel(server.overworld()).withPosition(new Vec3(0.5, 100, 0.5)), command);
		double ms = (System.nanoTime() - t0) / 1e6;
		Component result = messages.isEmpty() ? null : messages.getFirst();
		String key = result != null && result.getContents() instanceof TranslatableContents t ? t.getKey() : null;
		if (key == null || !key.startsWith("commands.locate.") || !key.endsWith(".success")) {
			throw new AssertionError("/" + command + " failed: " + (result == null ? "no message" : result.getString()));
		}
		if (command.startsWith("locate biome") && ms > LOCATE_BIOME_LIMIT_MS) {
			throw new AssertionError(String.format(Locale.ROOT, "/%s took %.0f ms (limit %.0f ms)", command, ms,
					LOCATE_BIOME_LIMIT_MS));
		}
		return String.format(Locale.ROOT, "/%s: %s (%.0f ms)", command, result.getString(), ms);
	}
}
