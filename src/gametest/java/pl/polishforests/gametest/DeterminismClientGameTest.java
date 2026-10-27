package pl.polishforests.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.client.screen.PolandPresetEditor;
import pl.polishforests.season.SeasonalProperties;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;

/**
 * Determinism of the world generation (step S10, {@code docs/03-m2-biomy.md} §12.3): two worlds with the same seed, in
 * both scales and both vegetation modes, give the same blocks.
 *
 * <p>Each world generates four areas of 8 × 8 chunks: the large river and the Beskids interior of the {@code stages}
 * measurement and, from step V9 of docs/04-pionowy-wycinek.md, an oak-hornbeam and a fresh pine forest site of the
 * vertical slice with the procedural trees. In both, the terrain (every block of every column, after TERRAIN, before FEATURES) is hashed first and
 * must be identical between the worlds. The fixed-order worlds bring the 64 chunks to TERRAIN one at a time on the server
 * thread; the free-order worlds (round 1 of the S10 review) request the 64 chunks and a ring of {@link #TERRAIN_RING}
 * around them at TERRAIN all at once, in a shuffled order (another one in each world), so the generation threads fill
 * them concurrently and in any order (with C2ME on its own thread pool), and the terrain is hashed only when all of them
 * are done. Their terrain is compared exactly with the fixed-order world 1, so the shared caches of the model and the
 * surface plan cannot depend on the order or the threads of {@code fill()} without the test noticing. Features that cross chunk borders (crowns, logs, plants at the border) depend on
 * the order in which a chunk and its neighbors are decorated, as in vanilla, so the decorated blocks are compared in two
 * ways (four worlds per scale and mode):
 * <ul>
 * <li>"fixed order" (worlds 1 and 2): each area and its ring of neighbors are brought to FEATURES one chunk at a time in
 * a fixed raster order, so every chunk is decorated after the same neighbors in both worlds; the decorated columns must
 * be identical;</li>
 * <li>"free order" (worlds 3 and 4): all 64 chunks of an area are requested at once and the generation threads decorate
 * them in any order, so crowns, logs and plants at chunk borders (and vanilla ores underground) can differ, as in
 * vanilla. In the decoration window (from 8 blocks below to 48 above the terrain ground) at most
 * {@link #FREE_ORDER_TOLERANCE_PLAIN} of the non-air blocks of the area may differ without the optimization pack and
 * {@link #FREE_ORDER_TOLERANCE_OPTIMIZATION} with it (C2ME decorates on more threads, so the order changes more often). The report also gives the worst chunk
 * (a whole tree at a border can be a quarter of a sparse chunk), the share of differing columns (any block of the column),
 * the most frequent pairs of differing blocks, the distance of the differing columns from the nearest chunk border and the groups of differing columns (8-connected) that do not
 * reach within {@link #BORDER_BAND} blocks of a border (a whole tree, or the leaf distances around a log that crosses the
 * border, can differ deeper inside the chunk).</li>
 * </ul>
 *
 * <p>The column hashes go to {@code polishforests.determinism.out} ({@code build/determinism/<plain|optimization>.txt});
 * with {@code -PdeterminismReference=<file>} the run also compares itself with a file written by a run with the other
 * mod set (the optimization pack with C2ME, {@code -Poptimization}, or without it): terrain and the fixed-order decorated
 * columns of every area exactly. Runs when {@code -Dpolishforests.gametest} is
 * {@code determinism} or {@code all}; {@code -Pscales} limits the scales.
 */
public final class DeterminismClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	/**
	 * North-west corners (blocks) of the areas: the river and Beskids areas of the stages test and (step V9) the sites
	 * oak_hornbeam_1 and fresh_pine_1 of the vertical slice ({@code slice_sites.txt}), where the procedural trees stand.
	 */
	private static final int[][] AREAS_REAL = {{-19_484 - 64, 11_253 - 64}, {154_834 - 64, 1_058_738 - 64}, {438 - 64, -730 - 64},
			{-699 - 64, 1_924 - 64}};
	private static final int[][] AREAS_GAMEPLAY = {{-1_851 - 64, 6_022 - 64}, {27_609 - 64, 3_254 - 64}, {-1_785 - 64, 886 - 64},
			{-1_195 - 64, -583 - 64}};
	private static final String[] AREA_NAMES = {"river", "beskids", "oak_hornbeam", "fresh_pine"};
	private static final int SIZE = 8;
	/** Distance from a chunk border (0 = edge column) used in the report of groups of differing columns. */
	static final int BORDER_BAND = 3;
	/**
	 * Largest share of decoration-window blocks that may differ between free-order generations, without the optimization
	 * pack (observed: at most 0.08% in S10, 0.001% since the free-order terrain is generated concurrently) and with it
	 * (C2ME, observed: at most 3.4% in S10 and 3.8% in round 1 of the review). Round 1 of the S10 review: separate limits, so
	 * that a regression of the mod's own decoration under concurrency does not hide behind the C2ME tolerance; the C2ME
	 * limit has about twice the observed maximum, because the share there follows the thread scheduling of C2ME (the exact
	 * criteria are the fixed order and the comparison with the reference file).
	 */
	static final double FREE_ORDER_TOLERANCE_PLAIN = 0.005;
	static final double FREE_ORDER_TOLERANCE_OPTIMIZATION = 0.08;
	/** Ring of chunks around a free-order area that is also requested at TERRAIN (concurrent generation of the neighbors). */
	static final int TERRAIN_RING = 1;
	/** Height of the decoration window of a column (blocks) and its part below the terrain ground. */
	private static final int WINDOW = 56;
	private static final int WINDOW_BELOW = 8;
	/** Stable hashes of block states (used on the server thread only). */
	private static final Map<BlockState, Long> STATE_HASHES = new IdentityHashMap<>();
	/** State numbers of this run for the decoration window (server thread only). */
	private static final Map<BlockState, Integer> STATE_IDS = new IdentityHashMap<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polishforests.gametest", "all");
		if (!mode.equals("determinism") && !mode.equals("all")) {
			return;
		}
		String scales = System.getProperty("polishforests.scales", "");
		boolean optimization = FabricLoader.getInstance().getAllMods().stream()
				.anyMatch(m -> m.getMetadata().getId().startsWith("c2me"));
		String label = optimization ? "optimization" : "plain";
		double tolerance = optimization ? FREE_ORDER_TOLERANCE_OPTIMIZATION : FREE_ORDER_TOLERANCE_PLAIN;
		PolishForests.LOG.info("[determinism] mod set: {} ({} mods)", label, FabricLoader.getInstance().getAllMods().size());
		List<String> out = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		for (PolandScale scale : PolandScale.values()) {
			if (!scales.isBlank() && !scales.contains(scale.getSerializedName())) {
				continue;
			}
			ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET,
					PolishForests.id(scale == PolandScale.REALISTIC ? "poland" : "poland_gameplay"));
			int[][] areas = scale == PolandScale.REALISTIC ? AREAS_REAL : AREAS_GAMEPLAY;
			for (boolean presentDay : new boolean[] {false, true}) {
				String name = scale.getSerializedName() + (presentDay ? " present_day" : " natural");
				Area[][] worlds = new Area[4][];
				for (int w = 0; w < 4; w++) {
					boolean fixedOrder = w < 2;
					try (TestSingleplayerContext sp = context.worldBuilder()
							.adjustSettings(ui -> select(ui, preset, presentDay)).create()) {
						sp.getServer().runCommand("gamerule spawn_mobs false");
						sp.getServer().runCommand("gamemode spectator @a");
						long t0 = System.nanoTime();
						worlds[w] = new Area[areas.length];
						for (int a = 0; a < areas.length; a++) {
							int ox = areas[a][0] >> 4;
							int oz = areas[a][1] >> 4;
							Area terrain = fixedOrder ? sp.getServer().computeOnServer(s -> terrain(s.overworld(), ox, oz))
									: terrainConcurrently(sp, ox, oz, (long) w * 31 + a);
							if (fixedOrder) {
								sp.getServer().runOnServer(s -> decorateInOrder(s.overworld(), ox, oz));
							} else {
								// Off the server thread getChunkFuture only queues the request, so all 64 chunks are generated at
								// once by the generation threads (on the server thread it would wait for each chunk in turn).
								ServerChunkCache cache = sp.getServer().computeOnServer(s -> s.overworld().getChunkSource());
								for (int cx = 0; cx < SIZE; cx++) {
									for (int cz = 0; cz < SIZE; cz++) {
										cache.getChunkFuture(ox + cx, oz + cz, ChunkStatus.FULL, true);
									}
								}
							}
							worlds[w][a] = sp.getServer().computeOnServer(s -> decorated(s.overworld(), ox, oz, terrain));
						}
						PolishForests.LOG.info(String.format(Locale.ROOT, "[determinism] %s: world %d (%s order) generated in %.1f s",
								name, w + 1, fixedOrder ? "fixed" : "free", (System.nanoTime() - t0) / 1e9));
					}
				}
				for (int a = 0; a < areas.length; a++) {
					String key = scale.getSerializedName() + " " + (presentDay ? "present_day" : "natural") + " " + a;
					PolishForests.LOG.info("[determinism] {}", compare(name + " / " + AREA_NAMES[a] + ", fixed order",
							worlds[0][a], worlds[1][a], true, tolerance, failures));
					PolishForests.LOG.info("[determinism] {}", compare(name + " / " + AREA_NAMES[a] + ", free order",
							worlds[2][a], worlds[3][a], false, tolerance, failures));
					PolishForests.LOG.info("[determinism] {}", compare(name + " / " + AREA_NAMES[a] + ", fixed against free order "
							+ "(terrain exactly, decoration report only)", worlds[0][a], worlds[2][a], null, tolerance, failures));
					out.add("terrain " + key + " " + Long.toHexString(worlds[0][a].terrainHash()));
					out.add("decorated " + key + " " + worlds[0][a].decoratedHex());
				}
			}
		}
		String dir = System.getProperty("polishforests.determinism.out", "");
		if (!dir.isBlank()) {
			try {
				Path file = Path.of(dir, label + ".txt");
				Files.createDirectories(file.getParent());
				Files.write(file, out, StandardCharsets.UTF_8);
				PolishForests.LOG.info("[determinism] column hashes written to {}", file);
			} catch (IOException e) {
				throw new AssertionError("Cannot write the determinism file", e);
			}
		}
		String reference = System.getProperty("polishforests.determinism.reference", "");
		if (!reference.isBlank()) {
			compareWithReference(Path.of(reference), out, label, failures);
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Determinism: " + String.join("; ", failures));
		}
	}

	/** Terrain of the 8 × 8 chunks after TERRAIN, before FEATURES: one hash per column and the ground height. */
	private static Area terrain(ServerLevel level, int ox, int oz) {
		var source = level.getChunkSource();
		long[] terrain = new long[SIZE * SIZE * 256];
		int[] ground = new int[terrain.length];
		for (int cx = 0; cx < SIZE; cx++) {
			for (int cz = 0; cz < SIZE; cz++) {
				ChunkAccess chunk = source.getChunk(ox + cx, oz + cz, ChunkStatus.TERRAIN, true);
				if (chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
					throw new AssertionError("Chunk " + (ox + cx) + ", " + (oz + cz) + " was decorated before the terrain hash");
				}
				hashColumns(chunk, terrain, (cx * SIZE + cz) * 256);
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						ground[(cx * SIZE + cz) * 256 + x * 16 + z] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
					}
				}
			}
		}
		return new Area(terrain, ground, null, null);
	}

	/**
	 * Terrain of a free-order area: the 8 × 8 chunks and a ring of {@link #TERRAIN_RING} around them requested at TERRAIN
	 * from the test thread all at once, in an order shuffled with {@code shuffle}, so the generation threads fill them
	 * concurrently and in any order; then, in one server task (no tick in between, so the request tickets stay), the server
	 * thread runs the chunk system's tasks until every request is done and hashes the terrain.
	 */
	private static Area terrainConcurrently(TestSingleplayerContext sp, int ox, int oz, long shuffle) {
		ServerChunkCache cache = sp.getServer().computeOnServer(s -> s.overworld().getChunkSource());
		List<int[]> order = new ArrayList<>();
		for (int cx = -TERRAIN_RING; cx < SIZE + TERRAIN_RING; cx++) {
			for (int cz = -TERRAIN_RING; cz < SIZE + TERRAIN_RING; cz++) {
				order.add(new int[] {ox + cx, oz + cz});
			}
		}
		Collections.shuffle(order, new Random(shuffle));
		List<CompletableFuture<ChunkResult<ChunkAccess>>> futures = new ArrayList<>();
		for (int[] c : order) {
			// Off the server thread getChunkFuture only queues the request (as for the free-order decoration below).
			futures.add(cache.getChunkFuture(c[0], c[1], ChunkStatus.TERRAIN, true));
		}
		return sp.getServer().computeOnServer(s -> {
			long deadline = System.nanoTime() + 600_000_000_000L;
			while (!futures.stream().allMatch(CompletableFuture::isDone)) {
				if (!cache.pollTask()) {
					LockSupport.parkNanos(200_000);
				}
				if (System.nanoTime() > deadline) {
					throw new AssertionError("Concurrent terrain of the area at chunk " + ox + ", " + oz + " not done in 600 s");
				}
			}
			for (CompletableFuture<ChunkResult<ChunkAccess>> f : futures) {
				if (f.join().orElse(null) == null) {
					throw new AssertionError("Concurrent terrain request failed: " + f.join().getError());
				}
			}
			return terrain(s.overworld(), ox, oz);
		});
	}

	/**
	 * Decoration in a fixed order: the area and its ring of neighbors (which light needs decorated) brought to FEATURES one
	 * chunk at a time in raster order, so every chunk is decorated after the same neighbors in every world.
	 */
	private static void decorateInOrder(ServerLevel level, int ox, int oz) {
		for (int cx = -1; cx <= SIZE; cx++) {
			for (int cz = -1; cz <= SIZE; cz++) {
				level.getChunkSource().getChunk(ox + cx, oz + cz, ChunkStatus.FEATURES, true);
			}
		}
	}

	/**
	 * The full chunks of the area (light and post-processing included): one hash per column, and the blocks of the
	 * decoration window ({@link #WINDOW_BELOW} below to {@link #WINDOW} − {@link #WINDOW_BELOW} above the terrain ground) as
	 * state numbers of this run, for the share of differing blocks.
	 */
	private static Area decorated(ServerLevel level, int ox, int oz, Area terrain) {
		long[] decorated = new long[SIZE * SIZE * 256];
		int[] window = new int[decorated.length * WINDOW];
		var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		for (int cx = 0; cx < SIZE; cx++) {
			for (int cz = 0; cz < SIZE; cz++) {
				ChunkAccess chunk = level.getChunkSource().getChunk(ox + cx, oz + cz, ChunkStatus.FULL, true);
				int offset = (cx * SIZE + cz) * 256;
				hashColumns(chunk, decorated, offset);
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						int i = offset + x * 16 + z;
						int y0 = terrain.ground()[i] - WINDOW_BELOW;
						for (int k = 0; k < WINDOW; k++) {
							// Rule ZW-5 (step V13): the seasonal properties (the bilberry fruit) at their neutral value.
							BlockState state = SeasonalProperties.neutral(chunk.getBlockState(pos.set(x, y0 + k, z)));
							window[i * WINDOW + k] = STATE_IDS.computeIfAbsent(state, st -> STATE_IDS.size());
						}
					}
				}
			}
		}
		return new Area(terrain.terrain(), terrain.ground(), decorated, window);
	}

	/** One hash per column of all its blocks, bottom to top (an air-only section adds one marker instead of 4096 reads). */
	private static void hashColumns(ChunkAccess chunk, long[] out, int offset) {
		long[] h = new long[256];
		LevelChunkSection[] sections = chunk.getSections();
		for (int s = 0; s < sections.length; s++) {
			LevelChunkSection section = sections[s];
			if (section.hasOnlyAir()) {
				for (int i = 0; i < 256; i++) {
					h[i] = mix(h[i] ^ (0xA1A1_0000L + s));
				}
				continue;
			}
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						BlockState state = SeasonalProperties.neutral(section.getBlockState(x, y, z));
						Long sh = STATE_HASHES.get(state);
						if (sh == null) {
							sh = stableHash(state.toString());
							STATE_HASHES.put(state, sh);
						}
						int i = x * 16 + z;
						h[i] = mix(h[i] ^ sh);
					}
				}
			}
		}
		System.arraycopy(h, 0, out, offset, 256);
	}

	/** A hash of the block state's text (block id and properties), the same in runs with different mod sets. */
	private static long stableHash(String text) {
		long h = 0xCBF2_9CE4_8422_2325L;
		for (int i = 0; i < text.length(); i++) {
			h = (h ^ text.charAt(i)) * 0x100_0000_01B3L;
		}
		return mix(h);
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
		return z ^ (z >>> 31);
	}

	/** Terrain exactly; decorated columns exactly in the fixed order, within the tolerance in the free order, null: report only. */
	private static String compare(String name, Area x, Area y, Boolean exact, double tolerance, List<String> failures) {
		int terrainDiff = differing(x.terrain, y.terrain);
		Diff d = Diff.of(x.decorated, y.decorated);
		int blocks = 0;
		int nonAir = 0;
		int air = STATE_IDS.getOrDefault(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), -1);
		int[] chunkBlocks = new int[SIZE * SIZE];
		int[] chunkNonAir = new int[SIZE * SIZE];
		Map<String, Integer> pairs = new HashMap<>();
		Map<Integer, BlockState> states = new HashMap<>();
		STATE_IDS.forEach((st, id) -> states.put(id, st));
		for (int i = 0; i < x.window.length; i++) {
			if (x.window[i] != air || y.window[i] != air) {
				int chunk = i / WINDOW / 256;
				nonAir++;
				chunkNonAir[chunk]++;
				if (x.window[i] != y.window[i]) {
					blocks++;
					chunkBlocks[chunk]++;
					pairs.merge(blockName(states.get(x.window[i])) + "/" + blockName(states.get(y.window[i])), 1, Integer::sum);
				}
			}
		}
		double blockShare = nonAir == 0 ? 0 : (double) blocks / nonAir;
		double chunkShare = 0;
		for (int c = 0; c < chunkBlocks.length; c++) {
			chunkShare = Math.max(chunkShare, chunkNonAir[c] == 0 ? 0 : (double) chunkBlocks[c] / chunkNonAir[c]);
		}
		StringBuilder top = new StringBuilder();
		pairs.entrySet().stream().sorted((p, q) -> q.getValue() - p.getValue()).limit(4)
				.forEach(e -> top.append(top.isEmpty() ? "" : ", ").append(e.getKey()).append(' ').append(e.getValue()));
		String result = String.format(Locale.ROOT, "%s: terrain before FEATURES %d of %d columns differ; decorated %s; blocks of "
				+ "the decoration window: %d of %d non-air differ (%.3f%%, worst chunk %.2f%%; most frequent: %s)", name, terrainDiff,
				x.terrain.length, d, blocks, nonAir, 100 * blockShare, 100 * chunkShare, top);
		if (terrainDiff > 0) {
			failures.add(name + ": terrain differs in " + terrainDiff + " columns");
		}
		if (exact != null && (exact ? d.count > 0 : blockShare > tolerance)) {
			failures.add(String.format(Locale.ROOT, "%s: decorated %s, %.3f%% of the window blocks (limit %s)", name, d,
					100 * blockShare, exact ? "0" : String.format(Locale.ROOT, "%.1f%%", 100 * tolerance)));
		}
		return result;
	}

	/** Block id without the namespace, with the properties that differ most often (leaf distance, waterlogging). */
	private static String blockName(BlockState state) {
		if (state == null) {
			return "?";
		}
		String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DISTANCE)) {
			id += "@" + state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DISTANCE);
		}
		return id;
	}

	private static int differing(long[] a, long[] b) {
		int n = 0;
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				n++;
			}
		}
		return n;
	}

	/** Comparison with a file written by a run with the other mod set (with or without the optimization pack). */
	private static void compareWithReference(Path file, List<String> out, String label, List<String> failures) {
		Map<String, String> ref = new HashMap<>();
		try {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				int i = line.lastIndexOf(' ');
				ref.put(line.substring(0, i), line.substring(i + 1));
			}
		} catch (IOException e) {
			throw new AssertionError("Cannot read the determinism reference " + file, e);
		}
		for (String line : out) {
			int i = line.lastIndexOf(' ');
			String key = line.substring(0, i);
			String other = ref.get(key);
			if (other == null) {
				PolishForests.LOG.warn("[determinism] reference {} has no entry {}", file, key);
				continue;
			}
			if (key.startsWith("terrain ")) {
				boolean same = other.equals(line.substring(i + 1));
				PolishForests.LOG.info("[determinism] {} vs {}: {} {}", label, file.getFileName(), key, same ? "identical" : "DIFFERENT");
				if (!same) {
					failures.add(label + " vs " + file.getFileName() + ": " + key + " differs");
				}
			} else {
				Diff d = Diff.of(Area.parseHex(line.substring(i + 1)), Area.parseHex(other));
				PolishForests.LOG.info("[determinism] {} vs {}: {} {}", label, file.getFileName(), key, d);
				if (d.count > 0) {
					failures.add(label + " vs " + file.getFileName() + ": " + key + " " + d);
				}
			}
		}
	}

	private static void select(WorldCreationUiState ui, ResourceKey<WorldPreset> preset, boolean presentDay) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		// Structures on, as in a real world: the ground under village pieces and the vegetation mask take part.
		ui.setGenerateStructures(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(preset)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier())));
		if (presentDay) {
			if (!(ui.getSettings().selectedDimensions().overworld() instanceof PolandChunkGenerator gen)) {
				throw new AssertionError("The preset " + preset.identifier() + " has no Poland generator");
			}
			ui.updateDimensions(PolandPresetEditor.apply(gen.settings().withAgriculture(true)));
		}
	}

	/** Column hashes of one area: terrain before FEATURES and the full chunks, index (chunk x · 8 + chunk z) · 256 + x · 16 + z. */
	private record Area(long[] terrain, int[] ground, long[] decorated, int[] window) {
		long terrainHash() {
			long h = 0;
			for (long v : terrain) {
				h = mix(h ^ v);
			}
			return h;
		}

		String decoratedHex() {
			StringBuilder sb = new StringBuilder(decorated.length * 16);
			for (long v : decorated) {
				sb.append(String.format(Locale.ROOT, "%016x", v));
			}
			return sb.toString();
		}

		static long[] parseHex(String hex) {
			long[] v = new long[hex.length() / 16];
			for (int i = 0; i < v.length; i++) {
				v[i] = Long.parseUnsignedLong(hex, i * 16, i * 16 + 16, 16);
			}
			return v;
		}
	}

	/**
	 * Differing decorated columns: count, histogram of the distance from the nearest chunk border, and groups of differing
	 * columns (8-connected over the 128 × 128 area) that do not reach within {@link #BORDER_BAND} blocks of a chunk border.
	 */
	private record Diff(int count, int columns, int[] byBorderDistance, int groups, int innerGroups) {
		static Diff of(long[] a, long[] b) {
			int side = SIZE * 16;
			boolean[] diff = new boolean[side * side];
			int[] hist = new int[8];
			int n = 0;
			for (int i = 0; i < a.length; i++) {
				if (a[i] != b[i]) {
					n++;
					int chunk = i / 256;
					int x = (i % 256) / 16;
					int z = i % 16;
					hist[border(x, z)]++;
					diff[((chunk / SIZE) * 16 + x) * side + (chunk % SIZE) * 16 + z] = true;
				}
			}
			int groups = 0;
			int inner = 0;
			boolean[] seen = new boolean[diff.length];
			int[] stack = new int[diff.length];
			for (int start = 0; start < diff.length; start++) {
				if (!diff[start] || seen[start]) {
					continue;
				}
				groups++;
				boolean reaches = false;
				int top = 0;
				stack[top++] = start;
				seen[start] = true;
				while (top > 0) {
					int c = stack[--top];
					int gx = c / side;
					int gz = c % side;
					reaches |= border(gx & 15, gz & 15) <= BORDER_BAND;
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							int nx = gx + dx;
							int nz = gz + dz;
							if (nx >= 0 && nz >= 0 && nx < side && nz < side) {
								int k = nx * side + nz;
								if (diff[k] && !seen[k]) {
									seen[k] = true;
									stack[top++] = k;
								}
							}
						}
					}
				}
				if (!reaches) {
					inner++;
				}
			}
			return new Diff(n, a.length, hist, groups, inner);
		}

		private static int border(int x, int z) {
			return Math.min(Math.min(x, 15 - x), Math.min(z, 15 - z));
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int d = 0; d < byBorderDistance.length; d++) {
				sb.append(d == 0 ? "" : " ").append(byBorderDistance[d]);
			}
			return String.format(Locale.ROOT, "%d of %d columns differ (%.2f%%; by distance from the chunk border 0..7: %s; "
					+ "%d groups, %d not reaching %d blocks from a border)", count, columns, 100.0 * count / columns, sb, groups,
					innerGroups, BORDER_BAND);
		}
	}
}
