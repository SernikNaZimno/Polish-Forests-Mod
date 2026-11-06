package pl.polishforests.gametest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
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
import pl.polishforests.worldgen.feature.plan.ForestBlend;
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
 * <li>"reversed order" (world 5, step L1 of docs/04-pionowy-wycinek.md): the area and its ring brought to FEATURES one
 * chunk at a time in the reversed raster order, so at every chunk border the other chunk of the pair comes first than in
 * world 1; compared with world 1 like the free order (the worst order for the objects that cross a border, with the
 * limit {@link #REVERSED_ORDER_TOLERANCE} for them).</li>
 * </ul>
 *
 * <p><b>Forest floor exactly in any order (step L1, DP-107 and DP-115).</b> In the free and the reversed order the
 * differing blocks of the window are sorted by kind ({@link WindowDiff}): the floor layers (litter, herbs, mosses,
 * bilberry) away from every object must be identical ({@link WindowDiff.Kind#FLOOR_FREE}: limit 0, with and without the
 * optimization pack), because since L1 every plant draws from a random source of its own position
 * ({@code DecorationRandom}); the share limits above apply only to the rest: vanilla features under the ground (ores,
 * sculk, caves), the top ground block, the objects that cross chunk borders and the floor blocks within
 * {@link WindowDiff#CONTACT} blocks of such an object. The differing blocks above the ground of these comparisons go to
 * {@code <label>_window_diff.txt} next to the hashes (position, kind and both states; with {@code -PdecorationRandom=chunk},
 * the draws of before L1, the free floor differences of DP-107 show there). With {@code -PdeterminismDeep} the full
 * columns of the two free-order worlds are also written block by block ({@code <label>_full_<scale>_<mode>_w3|w4.txt.gz})
 * and compared, by the pairs of differing blocks under the window (the question of the review of J2: which blocks deep
 * under the ground differ between free orders).
 *
 * <p>The column hashes go to {@code polishforests.determinism.out} ({@code build/determinism/<plain|optimization>.txt});
 * with {@code -PdeterminismReference=<file>} the run also compares itself with a file written by a run with the other
 * mod set (the optimization pack with C2ME, {@code -Poptimization}, or without it). A reference of the same mod set is
 * compared exactly, terrain and the fixed-order decorated columns of every area. Between two mod sets (user decision J1-q,
 * {@code docs/00} G.32: the optimizations of C2ME are not bit-exact) the comparison is a report with a tolerance of a few
 * blocks ({@link TerrainBlocks}): it counts the differing terrain blocks from the block-by-block files
 * {@code <label>_terrain.txt.gz}, which every run writes next to its hashes. Runs when {@code -Dpolishforests.gametest} is
 * {@code determinism} or {@code all}; {@code -Pscales} limits the scales.
 */
public final class DeterminismClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	/**
	 * North-west corners (blocks) of the areas: the river and Beskids areas of the stages test and (step V9) the sites
	 * oak_hornbeam_1 and fresh_pine_1 of the vertical slice ({@code slice_sites.txt}), where the procedural trees stand;
	 * step F1: the plot lowland_beech_natural_1 ({@code slice_plots.txt}), the chunk orders in the lowland beech forest (a
	 * reference from a build before F1 has no entry for it: no comparison with the reference there); step F2: the plot
	 * alder_carr_natural_1 (realistic) or alder_carr_managed_1 (gameplay, no natural carr plot there), the same for a
	 * reference from before F2; step F3: the plot upland_fir_natural_1 of each scale, the same for a reference from before
	 * F3; step F4: the plot bog_woodland_natural_1 of each scale, the same for a reference from before F4 (gameplay: the
	 * corner 40 blocks west of the plot, not 64, so 3 chunks lie between it and the oak_hornbeam area: the full chunks of
	 * that area decorate their neighbors 2 chunks out, which reached the bog area before its terrain hash).
	 */
	private static final int[][] AREAS_REAL = {{-19_484 - 64, 11_253 - 64}, {154_834 - 64, 1_058_738 - 64}, {438 - 64, -730 - 64},
			{-699 - 64, 1_924 - 64}, {-7_793 - 64, -9_133 - 64}, {2_339 - 64, 29_627 - 64}, {46_472 - 64, 15_684 - 64},
			{-2_507 - 64, 5_061 - 64}};
	private static final int[][] AREAS_GAMEPLAY = {{-1_851 - 64, 6_022 - 64}, {27_609 - 64, 3_254 - 64}, {-1_785 - 64, 886 - 64},
			{-1_195 - 64, -583 - 64}, {-2_004 - 64, -3_609 - 64}, {-5_971 - 64, 1_387 - 64}, {1_706 - 64, 3_775 - 64},
			{-1_640 - 40, 864 - 64}};
	private static final String[] AREA_NAMES = {"river", "beskids", "oak_hornbeam", "fresh_pine", "lowland_beech", "alder_carr",
			"upland_fir", "bog_woodland"};
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
	/**
	 * Largest share of decoration-window blocks that may differ between the raster and the reversed order (step L1): every
	 * object that crosses a chunk border meets the other order. Measured in the second stage of L1 (both mod sets give the
	 * same share, the reversed order is one thread): at most 2.26% of the area (GAMEPLAY N river; the forests of the slice
	 * up to 1.42%, the Beskids 1.04%), so the limit is about twice that (the floor away from objects is exact anyway).
	 */
	static final double REVERSED_ORDER_TOLERANCE = 0.05;
	/** Most differing blocks above the ground written to the window file per comparison and area. */
	static final int WINDOW_DUMP_LIMIT = 4000;

	/** Comparison modes: exact (fixed order), order (free or reversed: exact floor, a share limit for the rest), report only. */
	private enum Mode {
		EXACT, ORDER, REPORT
	}

	/** Worlds per scale and mode: two in the fixed order, two in the free order, one in the reversed order. */
	private static final int WORLDS = 5;
	/** Ring of chunks around a free-order area that is also requested at TERRAIN (concurrent generation of the neighbors). */
	static final int TERRAIN_RING = 1;
	/** Height of the decoration window of a column (blocks) and its part below the terrain ground. */
	private static final int WINDOW = 56;
	private static final int WINDOW_BELOW = 8;
	/** Stable hashes of block states (used on the server thread only). */
	private static final Map<BlockState, Long> STATE_HASHES = new IdentityHashMap<>();
	/** State numbers of this run for the decoration window (server thread only). */
	private static final Map<BlockState, Integer> STATE_IDS = new IdentityHashMap<>();
	/** Texts of block states for the block-by-block terrain (server thread only). */
	private static final Map<BlockState, String> STATE_TEXTS = new IdentityHashMap<>();

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
		String dir = System.getProperty("polishforests.determinism.out", "");
		Path blocksFile = dir.isBlank() ? null : Path.of(dir, TerrainBlocks.blocksFileName(label));
		Path windowFile = dir.isBlank() ? null : Path.of(dir, label + "_window_diff.txt");
		boolean deep = Boolean.getBoolean("polishforests.determinism.deep") && !dir.isBlank();
		try (BufferedWriter blocks = blocksFile == null ? null : gzipWriter(blocksFile);
				BufferedWriter windowDump = windowFile == null ? null : Files.newBufferedWriter(windowFile, StandardCharsets.UTF_8)) {
			worlds(context, scales, tolerance, blocks, windowDump, deep ? Path.of(dir) : null, label, out, failures);
		} catch (IOException e) {
			throw new AssertionError("Cannot write the terrain block by block", e);
		}
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
			compareWithReference(Path.of(reference), out, blocksFile, label, failures);
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Determinism: " + String.join("; ", failures));
		}
	}

	/** Generates the worlds of the test and compares them with each other; the hashes go to {@code out}. */
	private static void worlds(ClientGameTestContext context, String scales, double tolerance, BufferedWriter blocks,
			BufferedWriter windowDump, Path deepDir, String label, List<String> out, List<String> failures) {
		for (PolandScale scale : PolandScale.values()) {
			if (!scales.isBlank() && !scales.contains(scale.getSerializedName())) {
				continue;
			}
			ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET,
					PolishForests.id(scale == PolandScale.REALISTIC ? "poland" : "poland_gameplay"));
			int[][] areas = scale == PolandScale.REALISTIC ? AREAS_REAL : AREAS_GAMEPLAY;
			for (boolean presentDay : new boolean[] {false, true}) {
				String name = scale.getSerializedName() + (presentDay ? " present_day" : " natural");
				Area[][] worlds = new Area[WORLDS][];
				char[][] zones = new char[areas.length][];
				String fileKey = scale.getSerializedName() + "_" + (presentDay ? "present_day" : "natural");
				for (int w = 0; w < WORLDS; w++) {
					boolean fixedOrder = w < 2 || w == 4;
					boolean reversed = w == 4;
					Path deepFile = deepDir != null && (w == 0 || w == 2 || w == 3)
							? deepDir.resolve(label + "_full_" + fileKey + "_w" + (w + 1) + ".txt.gz") : null;
					BufferedWriter full;
					try {
						full = deepFile == null ? null : gzipWriter(deepFile);
					} catch (IOException e) {
						throw new AssertionError("Cannot write " + deepFile, e);
					}
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
							String areaKey = scale.getSerializedName() + " " + (presentDay ? "present_day" : "natural") + " " + a;
							if (w == 0 && blocks != null) {
								sp.getServer().runOnServer(s -> writeTerrain(s.overworld(), ox, oz, areaKey, terrain.terrainHash(), blocks));
							}
							if (fixedOrder) {
								sp.getServer().runOnServer(s -> decorateInOrder(s.overworld(), ox, oz, reversed));
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
							if (w == 0) {
								int ai = a;
								zones[a] = sp.getServer().computeOnServer(s -> stepZones(s.overworld(), ox, oz, worlds[0][ai]));
							}
							if (full != null) {
								sp.getServer().runOnServer(s -> writeTerrain(s.overworld(), ox, oz, areaKey, 0, full));
							}
						}
						PolishForests.LOG.info(String.format(Locale.ROOT, "[determinism] %s: world %d (%s order) generated in %.1f s",
								name, w + 1, reversed ? "reversed" : fixedOrder ? "fixed" : "free", (System.nanoTime() - t0) / 1e9));
					} finally {
						if (full != null) {
							try {
								full.close();
							} catch (IOException e) {
								throw new AssertionError("Cannot write " + deepFile, e);
							}
						}
					}
				}
				for (int a = 0; a < areas.length; a++) {
					String key = scale.getSerializedName() + " " + (presentDay ? "present_day" : "natural") + " " + a;
					int[] corner = areas[a];
					PolishForests.LOG.info("[determinism] {}", compare(name + " / " + AREA_NAMES[a] + ", fixed order",
							worlds[0][a], worlds[1][a], Mode.EXACT, tolerance, corner, windowDump, failures));
					PolishForests.LOG.info("[determinism] {}", compare(name + " / " + AREA_NAMES[a] + ", free order",
							worlds[2][a], worlds[3][a], Mode.ORDER, tolerance, corner, windowDump, failures));
					PolishForests.LOG.info("[determinism] {}", compare(name + " / " + AREA_NAMES[a] + ", fixed against reversed order",
							worlds[0][a], worlds[4][a], Mode.ORDER, REVERSED_ORDER_TOLERANCE, corner, windowDump, failures));
					PolishForests.LOG.info("[determinism] {}", compare(name + " / " + AREA_NAMES[a] + ", fixed against free order "
							+ "(terrain exactly, decoration report only)", worlds[0][a], worlds[2][a], Mode.REPORT, tolerance, corner,
							windowDump, failures));
					out.add("terrain " + key + " " + Long.toHexString(worlds[0][a].terrainHash()));
					out.add("decorated " + key + " " + worlds[0][a].decoratedHex());
					out.add(STEP_ZONE + key + " " + new String(zones[a]));
				}
				if (deepDir != null) {
					deepReport(name, deepDir.resolve(label + "_full_" + fileKey + "_w3.txt.gz"),
							deepDir.resolve(label + "_full_" + fileKey + "_w4.txt.gz"), worlds[2]);
				}
			}
		}
	}

	private static BufferedWriter gzipWriter(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		return new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8));
	}

	private static BufferedReader gzipReader(Path file) throws IOException {
		return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8));
	}

	/**
	 * The terrain of the area (after TERRAIN, before FEATURES, the fixed-order world 1) block by block, for the report of
	 * differences between two mod sets ({@link TerrainBlocks}; {@code zcat} gives a text fit for a diff).
	 */
	private static void writeTerrain(ServerLevel level, int ox, int oz, String areaKey, long terrainHash, BufferedWriter out) {
		var source = level.getChunkSource();
		var at = new net.minecraft.core.BlockPos.MutableBlockPos();
		StringBuilder sb = new StringBuilder();
		try {
			out.write(TerrainBlocks.header(areaKey, terrainHash, level.getMinY(), level.getMaxY()));
			out.write('\n');
			for (int cx = 0; cx < SIZE; cx++) {
				for (int cz = 0; cz < SIZE; cz++) {
					ChunkAccess chunk = source.getChunk(ox + cx, oz + cz, ChunkStatus.TERRAIN, true);
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							sb.setLength(0);
							sb.append(((ox + cx) << 4) + x).append(' ').append(((oz + cz) << 4) + z);
							BlockState last = null;
							for (int y = level.getMinY(); y <= level.getMaxY(); y++) {
								BlockState state = SeasonalProperties.neutral(chunk.getBlockState(at.set(x, y, z)));
								if (state != last) {
									sb.append(' ').append(y).append(':').append(STATE_TEXTS.computeIfAbsent(state, st -> st.toString().replace(' ', '_')));
									last = state;
								}
							}
							out.append(sb).append('\n');
						}
					}
				}
			}
		} catch (IOException e) {
			throw new AssertionError("Cannot write the terrain of " + areaKey, e);
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
	 * chunk at a time in raster order, so every chunk is decorated after the same neighbors in every world; {@code reversed}
	 * the raster order backwards (step L1), so every chunk border meets the other order.
	 */
	private static void decorateInOrder(ServerLevel level, int ox, int oz, boolean reversed) {
		for (int i = -1; i <= SIZE; i++) {
			for (int j = -1; j <= SIZE; j++) {
				int cx = reversed ? SIZE - 1 - i : i;
				int cz = reversed ? SIZE - 1 - j : j;
				level.getChunkSource().getChunk(ox + cx, oz + cz, ChunkStatus.FEATURES, true);
			}
		}
	}

	/**
	 * The full chunks of the area (light and post-processing included, the post-processing run here): one hash per column, and the blocks of the
	 * decoration window ({@link #WINDOW_BELOW} below to {@link #WINDOW} − {@link #WINDOW_BELOW} above the terrain ground) as
	 * state numbers of this run, for the share of differing blocks.
	 */
	private static Area decorated(ServerLevel level, int ox, int oz, Area terrain) {
		long[] decorated = new long[SIZE * SIZE * 256];
		int[] window = new int[decorated.length * WINDOW];
		boolean[] structure = new boolean[decorated.length];
		var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		Map<String, Integer> changed = new java.util.TreeMap<>();
		for (int cx = 0; cx < SIZE; cx++) {
			for (int cz = 0; cz < SIZE; cz++) {
				ChunkAccess chunk = level.getChunkSource().getChunk(ox + cx, oz + cz, ChunkStatus.FULL, true);
				// Review of F1, round 1 (DP-127): the post-processing a chunk gets when it first ticks near a player (shape
				// updates of the marked positions: a floor plant left on a neighbor's erratic boulder goes, as the upper half of
				// a double plant without its lower half) runs here, so the blocks compared are the ones a player sees.
				if (chunk instanceof net.minecraft.world.level.chunk.LevelChunk full) {
					postProcess(level, full, changed);
				}
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
		if (!changed.isEmpty()) {
			pl.polishforests.PolishForests.LOG.info("[determinism] post-processing at chunk {} {}: changed blocks {}", ox, oz, changed);
		}
		// Stage 2 of F3: the columns near a structure piece (StructureReach), whose floor is the structure's.
		StructureReach reach = StructureReach.of(level, ox, oz, ox + SIZE - 1, oz + SIZE - 1);
		for (int i = 0; i < structure.length && !reach.isEmpty(); i++) {
			int c = i & 255;
			int chunk = i >> 8;
			structure[i] = reach.near((ox + chunk / SIZE) * 16 + (c >> 4), terrain.ground()[i], (oz + chunk % SIZE) * 16 + (c & 15));
		}
		return new Area(terrain.terrain(), terrain.ground(), decorated, window, structure);
	}

	/**
	 * Runs the chunk's post-processing (the shape updates of its marked positions, as when it first ticks near a player) and
	 * counts the blocks it changed by pair of states. Review of F1, round 2 (DP-129): as {@code LevelChunk#postProcessGeneration}
	 * but without its fluid ticks. A marked fluid (an aquifer or a spring at a cave wall) would flow one step here, and with
	 * the optimization pack the same step leaves air (45 of the 51 differing columns of the Beskids REAL area, all a single
	 * flowing water or lava block); fluid flow is the work of the ticking world, which goes on after this, not of the
	 * generation compared here. The marked positions are then cleared and the vanilla method runs for the rest (the pending
	 * block entities).
	 */
	private static void postProcess(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk, Map<String, Integer> changed) {
		Map<net.minecraft.core.BlockPos, BlockState> before = new HashMap<>();
		it.unimi.dsi.fastutil.shorts.ShortList[] lists = chunk.getPostProcessing();
		for (int s = 0; s < lists.length; s++) {
			if (lists[s] == null) {
				continue;
			}
			for (short packed : lists[s]) {
				net.minecraft.core.BlockPos p = net.minecraft.world.level.chunk.ProtoChunk.unpackOffsetCoordinates(packed,
						chunk.getSectionYFromSectionIndex(s), chunk.getPos());
				before.put(p, chunk.getBlockState(p));
			}
		}
		for (int s = 0; s < lists.length; s++) {
			if (lists[s] == null) {
				continue;
			}
			for (short packed : lists[s]) {
				net.minecraft.core.BlockPos p = net.minecraft.world.level.chunk.ProtoChunk.unpackOffsetCoordinates(packed,
						chunk.getSectionYFromSectionIndex(s), chunk.getPos());
				BlockState state = chunk.getBlockState(p);
				if (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
					continue;
				}
				BlockState updated = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(state, level, p);
				if (updated != state) {
					level.setBlock(p, updated, 276);
				}
			}
			lists[s].clear();
		}
		chunk.postProcessGeneration(level);
		before.forEach((p, state) -> {
			BlockState now = chunk.getBlockState(p);
			if (now != state) {
				changed.merge(blockName(state) + ">" + blockName(now), 1, Integer::sum);
			}
		});
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

	/**
	 * Terrain exactly; decorated columns exactly in the fixed order; in the free and the reversed order the floor away from
	 * objects exactly and the rest of the window within the tolerance (step L1); report only.
	 */
	private static String compare(String name, Area x, Area y, Mode mode, double tolerance, int[] corner, BufferedWriter windowDump,
			List<String> failures) {
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
		WindowDiff.Result kinds = mode == Mode.EXACT ? null
				: WindowDiff.of(x.window, y.window, x.ground, structureEither(x, y), classes(states), SIZE, WINDOW, WINDOW_BELOW,
						WINDOW_DUMP_LIMIT);
		int free = kinds == null ? 0 : kinds.count(WindowDiff.Kind.FLOOR_FREE);
		// The share limit applies to everything but the free floor differences, which have their own limit 0 (step L1).
		double blockShare = nonAir == 0 ? 0 : (double) (blocks - free) / nonAir;
		double chunkShare = 0;
		for (int c = 0; c < chunkBlocks.length; c++) {
			chunkShare = Math.max(chunkShare, chunkNonAir[c] == 0 ? 0 : (double) chunkBlocks[c] / chunkNonAir[c]);
		}
		StringBuilder top = new StringBuilder();
		pairs.entrySet().stream().sorted((p, q) -> q.getValue() - p.getValue()).limit(4)
				.forEach(e -> top.append(top.isEmpty() ? "" : ", ").append(e.getKey()).append(' ').append(e.getValue()));
		String result = String.format(Locale.ROOT, "%s: terrain before FEATURES %d of %d columns differ; decorated %s; blocks of "
				+ "the decoration window: %d of %d non-air differ (%.3f%% without the free floor, worst chunk %.2f%%; most frequent: %s)",
				name, terrainDiff, x.terrain.length, d, blocks, nonAir, 100 * blockShare, 100 * chunkShare, top);
		if (kinds != null) {
			result += "; by kind: " + kindReport(kinds, states);
			dumpWindow(windowDump, name, corner, kinds, states);
		}
		if (terrainDiff > 0) {
			failures.add(name + ": terrain differs in " + terrainDiff + " columns");
		}
		if (mode == Mode.EXACT && d.count > 0 || mode == Mode.ORDER && blockShare > tolerance) {
			failures.add(String.format(Locale.ROOT, "%s: decorated %s, %.3f%% of the window blocks (limit %s)", name, d,
					100 * blockShare, mode == Mode.EXACT ? "0" : String.format(Locale.ROOT, "%.1f%%", 100 * tolerance)));
		}
		if (mode == Mode.ORDER && free > 0) {
			failures.add(String.format(Locale.ROOT, "%s: %d blocks of the forest floor away from objects differ (limit 0; %s)", name,
					free, kindReport(kinds, states)));
		}
		return result;
	}

	/**
	 * Class of each state number of this run for {@link WindowDiff}: wood, leaves, the vines that hang from the leaves and
	 * the mod's trunk blocks (trunks, root flares, roots) are objects; plants, litters and carpets are the floor.
	 */
	private static byte[] classes(Map<Integer, BlockState> states) {
		byte[] c = new byte[STATE_IDS.size()];
		for (int id = 0; id < c.length; id++) {
			BlockState st = states.get(id);
			c[id] = st == null ? WindowDiff.OTHER : windowClass(st);
		}
		return c;
	}

	static byte windowClass(BlockState st) {
		net.minecraft.world.level.block.Block block = st.getBlock();
		if (st.isAir()) {
			return WindowDiff.AIR;
		}
		// A vine (in the tag replaceable) is a part of the placeholder tree whose leave_vine decorator hangs it from the
		// crown, from beside a leaf down to 4 blocks lower: it follows its crown and stops at whatever it meets, like a crown at a
		// chunk border (second stage of L1: in the river area the only free-floor differences were vines and the plants
		// they met).
		if (st.is(net.minecraft.tags.BlockTags.LOGS) || st.is(net.minecraft.tags.BlockTags.LEAVES)
				|| block instanceof pl.polishforests.block.TrunkBlock || st.is(net.minecraft.world.level.block.Blocks.VINE)) {
			return WindowDiff.OBJECT;
		}
		if (block instanceof net.minecraft.world.level.block.VegetationBlock || block instanceof net.minecraft.world.level.block.CarpetBlock
				|| block instanceof net.minecraft.world.level.block.BigDripleafBlock
				|| block instanceof net.minecraft.world.level.block.BigDripleafStemBlock
				|| block instanceof net.minecraft.world.level.block.SugarCaneBlock || st.is(net.minecraft.tags.BlockTags.REPLACEABLE)) {
			return WindowDiff.FLOOR;
		}
		return WindowDiff.OTHER;
	}

	/** The columns near a structure piece in either generation (the pieces are the same; null when unknown). */
	private static boolean @org.jspecify.annotations.Nullable [] structureEither(Area x, Area y) {
		if (x.structure() == null || y.structure() == null) {
			return x.structure() != null ? x.structure() : y.structure();
		}
		boolean[] out = new boolean[x.structure().length];
		for (int i = 0; i < out.length; i++) {
			out[i] = x.structure()[i] || y.structure()[i];
		}
		return out;
	}

	/** Counts by kind, with the most frequent pairs of the free floor differences. */
	private static String kindReport(WindowDiff.Result kinds, Map<Integer, BlockState> states) {
		StringBuilder sb = new StringBuilder();
		for (WindowDiff.Kind k : WindowDiff.Kind.values()) {
			sb.append(sb.isEmpty() ? "" : ", ").append(k.name().toLowerCase(Locale.ROOT)).append(' ').append(kinds.count(k));
		}
		Map<String, Integer> pairs = new HashMap<>();
		for (WindowDiff.Block b : kinds.blocks()) {
			if (b.kind() == WindowDiff.Kind.FLOOR_FREE) {
				pairs.merge(stateName(states.get(b.ours())) + "/" + stateName(states.get(b.theirs())), 1, Integer::sum);
			}
		}
		if (!pairs.isEmpty()) {
			sb.append(" (free floor:");
			pairs.entrySet().stream().sorted((p, q) -> q.getValue() - p.getValue()).limit(4)
					.forEach(e -> sb.append(' ').append(e.getKey()).append(' ').append(e.getValue()));
			sb.append(')');
		}
		return sb.toString();
	}

	/** Block id without the namespace with its properties (the facing of a litter shows here). */
	private static String stateName(BlockState state) {
		if (state == null) {
			return "?";
		}
		String text = state.toString();
		int brace = text.indexOf('{');
		int colon = text.indexOf(':', Math.max(brace, 0));
		return (colon >= 0 ? text.substring(colon + 1) : text).replace("}", "").replace(' ', '_');
	}

	/** The differing blocks above the ground of one comparison (world coordinates, kind, both states). */
	private static void dumpWindow(BufferedWriter out, String name, int[] corner, WindowDiff.Result kinds,
			Map<Integer, BlockState> states) {
		if (out == null) {
			return;
		}
		try {
			out.write("# " + name + ": " + kinds.blocks().size() + " blocks above the underground (x y z kind first second)\n");
			for (WindowDiff.Block b : kinds.blocks()) {
				// Review of F1, round 1: the area starts at the chunk of its corner (the corners are not chunk-aligned).
				out.write(((corner[0] >> 4 << 4) + b.x()) + " " + b.y() + " " + ((corner[1] >> 4 << 4) + b.z()) + " "
						+ b.kind().name().toLowerCase(Locale.ROOT)
						+ " " + stateName(states.get(b.ours())) + " " + stateName(states.get(b.theirs())) + "\n");
			}
			out.flush();
		} catch (IOException e) {
			throw new AssertionError("Cannot write the window differences", e);
		}
	}

	/**
	 * Report of the full columns of the two free-order worlds (with {@code -PdeterminismDeep}, the question of the review of
	 * J2): per area the differing blocks under the decoration window, above it and in it, the columns with a difference
	 * under the window and the most frequent pairs of differing blocks under it.
	 */
	private static void deepReport(String name, Path first, Path second, Area[] areas) {
		Map<String, int[]> perArea = new java.util.LinkedHashMap<>();
		Map<String, Map<String, Integer>> pairs = new HashMap<>();
		Map<String, boolean[]> deepColumns = new HashMap<>();
		try (BufferedReader a = gzipReader(first); BufferedReader b = gzipReader(second)) {
			TerrainBlocks.compareColumns(a, b, (areaKey, area, column, x, yy, z, ours, theirs) -> {
				int ground = areas[area].ground()[column];
				int[] counts = perArea.computeIfAbsent(areaKey, k -> new int[4]);
				if (yy < ground - WINDOW_BELOW) {
					counts[0]++;
					deepColumns.computeIfAbsent(areaKey, k -> new boolean[SIZE * SIZE * 256])[column] = true;
					pairs.computeIfAbsent(areaKey, k -> new HashMap<>()).merge(TerrainBlocks.blockId(ours) + "/"
							+ TerrainBlocks.blockId(theirs), 1, Integer::sum);
				} else if (yy < ground - WINDOW_BELOW + WINDOW) {
					counts[1]++;
				} else {
					counts[2]++;
				}
			});
		} catch (IOException e) {
			throw new AssertionError("Cannot compare " + first + " with " + second, e);
		}
		perArea.forEach((key, counts) -> {
			boolean[] cols = deepColumns.get(key);
			int n = 0;
			if (cols != null) {
				for (boolean c : cols) {
					n += c ? 1 : 0;
				}
			}
			StringBuilder top = new StringBuilder();
			pairs.getOrDefault(key, Map.of()).entrySet().stream().sorted((p, q) -> q.getValue() - p.getValue()).limit(8)
					.forEach(e -> top.append(top.isEmpty() ? "" : ", ").append(e.getKey()).append(' ').append(e.getValue()));
			PolishForests.LOG.info(String.format(Locale.ROOT, "[determinism] %s / area %s, free order, full columns: under the window %d "
					+ "blocks in %d columns (%.2f%%), in the window %d, above it %d; under the window most frequent: %s", name, key,
					counts[0], n, 100.0 * n / (SIZE * SIZE * 256), counts[1], counts[2], top));
		});
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

	/**
	 * Comparison with a file written by another run: exact with the same mod set, and between two mod sets (decision J1-q) a
	 * report of the differing blocks that fails only above the tolerance of {@link TerrainBlocks}.
	 */
	private static void compareWithReference(Path file, List<String> out, Path blocksFile, String label, List<String> failures) {
		Map<String, String> ref = new HashMap<>();
		try {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				int i = line.lastIndexOf(' ');
				ref.put(line.substring(0, i), line.substring(i + 1));
			}
		} catch (IOException e) {
			throw new AssertionError("Cannot read the determinism reference " + file, e);
		}
		String refName = file.getFileName().toString();
		boolean otherSet = TerrainBlocks.isOtherModSet(refName, label);
		Map<String, TerrainBlocks.AreaDiff> blocks = otherSet ? blockDifferences(file, blocksFile, refName) : Map.of();
		int areas = 0;
		int differing = 0;
		int worst = 0;
		int worstColumns = 0;
		Map<String, String> zones = new HashMap<>();
		for (String line : out) {
			if (line.startsWith(STEP_ZONE)) {
				int i = line.lastIndexOf(' ');
				zones.put(line.substring(STEP_ZONE.length(), i), line.substring(i + 1));
			}
		}
		for (String line : out) {
			int i = line.lastIndexOf(' ');
			String key = line.substring(0, i);
			if (key.startsWith(STEP_ZONE)) {
				continue;
			}
			String other = ref.get(key);
			if (key.startsWith("decorated ") && other != null && zones.containsKey(key.substring("decorated ".length()))) {
				long[] ours = Area.parseHex(line.substring(i + 1));
				long[] theirs = Area.parseHex(other);
				String zone = zones.get(key.substring("decorated ".length()));
				PolishForests.LOG.info("[determinism] {} vs {}: {} differences by zone of the steps F: {}", label, refName, key,
						zoneReport(ours, theirs, zone));
				if (Boolean.getBoolean("polishforests.determinism.stepZones") && !otherSet) {
					// KF-3: against the build before a step F the decoration may differ only in the zones of its forests.
					int elsewhere = 0;
					for (int c = 0; c < ours.length && c < zone.length(); c++) {
						elsewhere += zone.charAt(c) == '.' && ours[c] != theirs[c] ? 1 : 0;
					}
					if (elsewhere > 0) {
						failures.add(label + " vs " + refName + ": " + key + " differs in " + elsewhere
								+ " columns away from the forests of the steps F (KF-3)");
					}
					continue;
				}
			}
			if (other == null) {
				PolishForests.LOG.warn("[determinism] reference {} has no entry {}", file, key);
				continue;
			}
			if (key.startsWith("terrain ")) {
				areas++;
				boolean same = other.equals(line.substring(i + 1));
				if (same || !otherSet) {
					PolishForests.LOG.info("[determinism] {} vs {}: {} {}", label, refName, key, same ? "identical" : "DIFFERENT");
					if (!same) {
						failures.add(label + " vs " + refName + ": " + key + " differs");
					}
					continue;
				}
				differing++;
				TerrainBlocks.AreaDiff diff = blocks.get(key.substring("terrain ".length()));
				if (diff == null || !diff.referenceHash().equals(other)) {
					failures.add(label + " vs " + refName + ": " + key + " differs and the block-by-block file next to the reference "
							+ (diff == null ? "does not have it" : "is from another run"));
					continue;
				}
				worst = Math.max(worst, diff.blocks());
				PolishForests.LOG.info("[determinism] {} vs {}: {} {}", label, refName, key, diff.report());
				if (!diff.withinTolerance()) {
					failures.add(label + " vs " + refName + ": " + key + " " + diff.report());
				}
			} else {
				Diff d = Diff.of(Area.parseHex(line.substring(i + 1)), Area.parseHex(other));
				PolishForests.LOG.info("[determinism] {} vs {}: {} {}", label, refName, key, d);
				if (otherSet) {
					worstColumns = Math.max(worstColumns, d.count);
				}
				if (d.count > (otherSet ? TerrainBlocks.DECORATED_TOLERANCE_COLUMNS : 0)) {
					failures.add(label + " vs " + refName + ": " + key + " " + d);
				}
			}
		}
		if (otherSet) {
			PolishForests.LOG.info(String.format(Locale.ROOT, "[determinism] mod sets report, %s vs %s: terrain of %d of %d areas differs, "
					+ "at most %d blocks per area (limit %d); decorated at most %d columns per area (limit %d)", label, refName, differing,
					areas, worst, TerrainBlocks.TERRAIN_TOLERANCE_BLOCKS, worstColumns, TerrainBlocks.DECORATED_TOLERANCE_COLUMNS));
		}
	}

	/**
	 * Prefix of the lines of the zones of the forests of the steps F (KF-3, step F1): per column of an area of the fixed-order
	 * world 1 {@code F} in such a forest (the forests of {@link ForestBlend#FORESTS} after the transect, the step's own and
	 * the earlier steps'), {@code B} in their belt ({@code ForestBlend} share above 0) or within {@value #STEP_REACH} blocks
	 * of an F or B column inside the area (the ramp of {@code Ecotone}, the crowns); step F4: {@code H} on the open habitats
	 * whose decoration a step F changed ({@link #STEP_OPEN}: the single planner trees and the plants of the heath and the
	 * raised bog, the fringe the heath shares with the gray dune) or within {@value #STEP_REACH} blocks of them; {@code .}
	 * elsewhere. Not compared; the comparison with a reference of the build before the step reports the differing decorated
	 * columns by zone.
	 */
	private static final String STEP_ZONE = "stepzone ";
	private static final int STEP_REACH = pl.polishforests.worldgen.feature.plan.Ecotone.REACH
			+ pl.polishforests.worldgen.feature.plan.Ecotone.WARP + 16;

	/** Step F4: the open habitats whose decoration a step F changed (zone {@code H} of {@link #STEP_ZONE}). */
	private static final pl.polishforests.worldgen.habitat.HabitatBiome[] STEP_OPEN = {
			pl.polishforests.worldgen.habitat.HabitatBiome.HEATH, pl.polishforests.worldgen.habitat.HabitatBiome.RAISED_BOG,
			pl.polishforests.worldgen.habitat.HabitatBiome.GRAY_DUNE};

	/** The zones of the forests of the steps F of an area (see {@link #STEP_ZONE}). */
	private static char[] stepZones(ServerLevel level, int ox, int oz, Area area) {
		int side = SIZE * 16;
		char[] zone = new char[SIZE * SIZE * 256];
		java.util.Arrays.fill(zone, '.');
		if (!(level.getChunkSource().getGenerator() instanceof PolandChunkGenerator gen)) {
			return zone;
		}
		ForestBlend blend = gen.forestBlend(level.getSeed());
		int transect = ForestBlend.TRANSECT.length;
		float[] s = new float[ForestBlend.FORESTS.length + 1];
		boolean[] near = new boolean[side * side];
		boolean[] open = new boolean[side * side];
		for (int cx = 0; cx < SIZE; cx++) {
			for (int cz = 0; cz < SIZE; cz++) {
				ForestBlend.Field field = blend.field(ox + cx, oz + cz);
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						int i = (cx * SIZE + cz) * 256 + x * 16 + z;
						int wx = ((ox + cx) << 4) + x;
						int wz = ((oz + cz) << 4) + z;
						var biome = level.getBiome(new net.minecraft.core.BlockPos(wx, area.ground()[i], wz));
						boolean own = false;
						for (int b = transect; b < ForestBlend.FORESTS.length; b++) {
							own |= biome.is(pl.polishforests.worldgen.chunk.ModBiomeKeys.key(ForestBlend.FORESTS[b]));
						}
						field.at(wx, wz, s);
						boolean belt = false;
						for (int b = transect; b < ForestBlend.FORESTS.length; b++) {
							belt |= s[b] > 0;
						}
						zone[i] = own ? 'F' : belt ? 'B' : '.';
						near[(cx * 16 + x) * side + cz * 16 + z] = own || belt;
						for (var o : STEP_OPEN) {
							open[(cx * 16 + x) * side + cz * 16 + z] |= biome.is(pl.polishforests.worldgen.chunk.ModBiomeKeys.key(o));
						}
					}
				}
			}
		}
		// Within STEP_REACH (Chebyshev) of an F or B column: a summed table of the near columns; then the same for H.
		reach(zone, near, 'B');
		reach(zone, open, 'H');
		return zone;
	}

	/** Marks with {@code mark} the {@code .} columns of a zone within {@link #STEP_REACH} (Chebyshev) of a flagged column. */
	private static void reach(char[] zone, boolean[] flagged, char mark) {
		int side = SIZE * 16;
		int w = side + 1;
		int[] sum = new int[w * w];
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				sum[(x + 1) * w + z + 1] = (flagged[x * side + z] ? 1 : 0) + sum[x * w + z + 1] + sum[(x + 1) * w + z] - sum[x * w + z];
			}
		}
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				int i = ((x >> 4) * SIZE + (z >> 4)) * 256 + (x & 15) * 16 + (z & 15);
				if (zone[i] != '.') {
					continue;
				}
				int x0 = Math.max(0, x - STEP_REACH);
				int z0 = Math.max(0, z - STEP_REACH);
				int x1 = Math.min(side, x + STEP_REACH + 1);
				int z1 = Math.min(side, z + STEP_REACH + 1);
				if (sum[x1 * w + z1] - sum[x0 * w + z1] - sum[x1 * w + z0] + sum[x0 * w + z0] > 0) {
					zone[i] = mark;
				}
			}
		}
	}

	/**
	 * Differing decorated columns by zone of the steps F (in the forests, in their belt and reach, on the open habitats of
	 * step F4 and their reach, elsewhere).
	 */
	private static String zoneReport(long[] ours, long[] theirs, String zones) {
		int[] differ = new int[4];
		int[] columns = new int[4];
		for (int i = 0; i < ours.length && i < zones.length(); i++) {
			char c = zones.charAt(i);
			int z = c == 'F' ? 0 : c == 'B' ? 1 : c == 'H' ? 2 : 3;
			columns[z]++;
			differ[z] += ours[i] != theirs[i] ? 1 : 0;
		}
		return String.format(Locale.ROOT, "in the forests %d of %d, in their belt and reach %d of %d, on the heath, raised bog and "
				+ "gray dune and their reach %d of %d, elsewhere %d of %d", differ[0], columns[0], differ[1], columns[1], differ[2],
				columns[2], differ[3], columns[3]);
	}

	/** Differing blocks against the block-by-block file next to the reference; empty if that file is missing. */
	private static Map<String, TerrainBlocks.AreaDiff> blockDifferences(Path reference, Path ours, String refName) {
		Path theirs = reference.resolveSibling(TerrainBlocks.blocksFileName(refName.substring(0, refName.length() - ".txt".length())));
		if (ours == null || !Files.exists(theirs)) {
			PolishForests.LOG.warn("[determinism] no block-by-block file {}: differing terrain cannot be counted", theirs);
			return Map.of();
		}
		try (BufferedReader a = gzipReader(ours); BufferedReader b = gzipReader(theirs)) {
			return TerrainBlocks.compare(a, b);
		} catch (IOException e) {
			throw new AssertionError("Cannot compare the terrain with " + theirs, e);
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
	private record Area(long[] terrain, int[] ground, long[] decorated, int[] window, boolean[] structure) {
		Area(long[] terrain, int[] ground, long[] decorated, int[] window) {
			this(terrain, ground, decorated, window, null);
		}

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
