package pl.polishforests.gametest;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import pl.polishforests.PolishForests;

/**
 * Measurements of step V10 of the vertical slice (docs/04-pionowy-wycinek.md §3, budgets PW-10 and PW-13). The class uses
 * only vanilla classes and the counters of {@code PerformanceClientGameTest}, so the same file also runs on the M2 baseline
 * (git tag {@code m2-baseline-r1}) for the A/B comparison in one session:
 * <ul>
 * <li>{@link #woodAndLeaves}: wood and leaves of a generated area by block tags (both builds count the same way);</li>
 * <li>{@link #memoryAndStates}: the used heap after a full garbage collection and the number of block states;</li>
 * <li>{@link #flights} ({@code -Pgametest=performance -Psites=slice}): in the oak-hornbeam and the fresh pine forest of
 * each scale, the load of new terrain around a teleport (chunks per second, the C2ME budget with {@code -Poptimization};
 * only this with {@code -PloadOnly}, step O1) and a fixed camera flight: {@value #FLIGHT_POINTS} points on a circle of {@value #ORBIT_RADIUS} blocks around the place,
 * above the canopy and at eye level, with a full turn of the camera at each (frames per second and the render time of
 * each frame);</li>
 * <li>{@link #light} ({@code -Pgametest=performance -Psites=light}): the light at the ground under the canopy at noon, the
 * share of the ground where a monster may spawn by the vanilla rule ({@link Monster#isDarkEnoughToSpawn}) and a live
 * spawn test, against the dark forest and the forest of a vanilla world (PW-13).</li>
 * </ul>
 * A client game test syncs the client and the integrated server at every client tick (at most one per frame), so the
 * numbers compare builds and renderers in one session, not with a normal game.
 */
final class ForestPerformance {
	/** Radius of the camera circle (blocks) and the client ticks of one lap. */
	static final int ORBIT_RADIUS = 40;
	private static final int FLIGHT_POINTS = 8;
	private static final int SWEEP_TICKS = 72;
	/** Height of the camera above the ground of the center for the flight above the canopy (trees are up to ~32 blocks). */
	private static final int CANOPY_FLIGHT = 42;
	private static final int RENDER_DISTANCE = 12;
	private static final long[] FRAMES = new long[4 * SWEEP_TICKS];
	/** Per recorded tick: the client tick (START to END of the client tick event) and the whole loop (END to END). */
	private static final long[] TICKS = new long[4 * SWEEP_TICKS];
	private static final long[] LOOPS = new long[4 * SWEEP_TICKS];
	/** The frame rate of the game ({@link Minecraft#getFps}, frames of the last second) at each recorded tick. */
	private static final int[] FPS = new int[4 * SWEEP_TICKS];
	private static final int FPS_FROM = 20;
	/**
	 * GPU time of the level pass (Fabric's START_MAIN to END_MAIN of every frame), read by a timestamp query: the frames
	 * per second of the window depend on whether it has the focus (a background window is capped by the driver at about
	 * 50-60 frames per second on the test machine), the GPU time per frame does not.
	 */
	private static com.mojang.blaze3d.systems.TimerQuery gpu;
	private static boolean gpuAwaiting;
	private static int gpuWarm;
	private static long gpuSum;
	private static int gpuCount;
	private static int levelFrames;
	private static volatile long tickStart;
	private static volatile long lastEnd;
	private static volatile int frameCount;
	private static volatile boolean recording;
	private static volatile float yawStep;
	private static boolean registered;

	private ForestPerformance() {
	}

	// ------------------------------------------------------------------ blocks, memory, states

	/** Wood (block tag {@code logs}) and leaves (tag {@code leaves}) per chunk in the n × n chunks from (ox, oz). */
	static double[] woodAndLeaves(ServerLevel level, int ox, int oz, int n) {
		long[] count = new long[2];
		for (int cx = 0; cx < n; cx++) {
			for (int cz = 0; cz < n; cz++) {
				LevelChunk chunk = level.getChunk(ox + cx, oz + cz);
				for (LevelChunkSection section : chunk.getSections()) {
					if (section.hasOnlyAir()) {
						continue;
					}
					section.getStates().count((BlockState state, int c) -> {
						if (state.is(BlockTags.LOGS)) {
							count[0] += c;
						} else if (state.is(BlockTags.LEAVES)) {
							count[1] += c;
						}
					});
				}
			}
		}
		return new double[] {(double) count[0] / (n * n), (double) count[1] / (n * n)};
	}

	/** The used heap after a full collection, the loaded chunks of the server and the block states (all and of the mod). */
	static String memoryAndStates(MinecraftServer server) {
		Runtime rt = Runtime.getRuntime();
		long used = Long.MAX_VALUE;
		for (int i = 0; i < 3; i++) {
			System.gc();
			used = Math.min(used, rt.totalMemory() - rt.freeMemory());
		}
		int mod = 0;
		for (Block b : BuiltInRegistries.BLOCK) {
			if (BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals(PolishForests.MOD_ID)) {
				mod += b.getStateDefinition().getPossibleStates().size();
			}
		}
		return String.format(Locale.ROOT, "used heap %.0f MB after GC with %d server chunks loaded; block states %d (mod %d)",
				used / 1048576.0, server.overworld().getChunkSource().getLoadedChunksCount(), Block.BLOCK_STATE_REGISTRY.size(), mod);
	}

	// ------------------------------------------------------------------ load and flight

	/** The load and flight measurements in the oak-hornbeam and the fresh pine forest of the world (natural mode). */
	static void flights(ClientGameTestContext context, TestSingleplayerContext sp, String name, boolean real) {
		register();
		for (SliceSites.Place p : SliceSites.of(real, false)) {
			if (!p.name().equals("oak_hornbeam_1") && !p.name().equals("fresh_pine_1")) {
				continue;
			}
			int[] m = p.middle();
			String label = name + " / " + p.name();
			int ground = sp.getServer().computeOnServer(s -> surface(s.overworld(), m[0], m[1]));
			// New terrain: the client gets the chunks around a teleport (chunks per second; with C2ME the budget of PW-10).
			// Counted around the target, since the client keeps the old chunks for a while. The camera looks straight up
			// (pitch -90): the client and the server of a game test are synced at every client tick, so with the forest in view the slower
			// frames of a build would also slow down its server ticks and the chunk sending; looking at the sky, the load
			// measures the generation and the transfer of the chunks.
			// Three loads, 450 blocks apart (the 25 × 25 chunks of a load do not overlap): east of the place, then north and
			// south of it; the chunks per second are summed up over the three.
			int[][] targets = {{m[0] + ORBIT_RADIUS, m[1]}, {m[0], m[1] - 450}, {m[0], m[1] + 450}};
			int all = (2 * RENDER_DISTANCE + 1) * (2 * RENDER_DISTANCE + 1);
			int target = (int) (0.9 * all);
			int loadedAll = 0;
			double secondsAll = 0;
			StringBuilder loads = new StringBuilder();
			for (int[] t : targets) {
				int tx = t[0] >> 4;
				int tz = t[1] >> 4;
				int ty = sp.getServer().computeOnServer(s -> s.overworld().getChunkSource().getGenerator().getBaseHeight(t[0], t[1],
						Heightmap.Types.WORLD_SURFACE_WG, s.overworld(), s.overworld().getChunkSource().randomState()));
				long t0 = System.nanoTime();
				sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0 -90", t[0], ty + CANOPY_FLIGHT, t[1]));
				int loaded = 0;
				for (int i = 0; i < 6000 && loaded < target; i++) {
					context.waitTick();
					loaded = context.computeOnClient(mc -> {
						int n = 0;
						for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
							for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
								n += mc.level.getChunkSource().getChunk(tx + dx, tz + dz, ChunkStatus.FULL, false) != null ? 1 : 0;
							}
						}
						return n;
					});
				}
				double seconds = (System.nanoTime() - t0) / 1e9;
				loadedAll += loaded;
				secondsAll += seconds;
				loads.append(String.format(Locale.ROOT, " %.1f", loaded / seconds));
			}
			PolishForests.LOG.info(String.format(Locale.ROOT, "[performance] %s: new terrain, client has %d of the %d chunks around "
					+ "three teleports after %.2f s, %.1f chunks/s (per load%s)", label, loadedAll, 3 * all, secondsAll, loadedAll
					/ secondsAll, loads));
			// The whole flight area to FULL on the server first, so the turns do not wait for the generation.
			sp.getServer().runOnServer(s -> {
				int r = RENDER_DISTANCE + (ORBIT_RADIUS >> 4) + 2;
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						s.overworld().getChunkSource().getChunk((m[0] >> 4) + dx, (m[1] >> 4) + dz, ChunkStatus.FULL, true);
					}
				}
			});
			if (Boolean.getBoolean("polishforests.loadOnly")) {
				// Step O1: only the load of new terrain (-PloadOnly), the flight left out.
				continue;
			}
			for (boolean canopy : new boolean[] {true, false}) {
				double[][] points = new double[FLIGHT_POINTS][];
				sp.getServer().runOnServer(s -> {
					for (int i = 0; i < FLIGHT_POINTS; i++) {
						double a = 2 * Math.PI * i / FLIGHT_POINTS;
						double x = m[0] + ORBIT_RADIUS * Math.cos(a);
						double z = m[1] + ORBIT_RADIUS * Math.sin(a);
						points[i] = canopy ? new double[] {x, ground + CANOPY_FLIGHT, z, 25} : eyePoint(s.overworld(), x, z);
					}
				});
				sweeps(context, sp, label + (canopy ? " canopy" : " eye"), points);
			}
		}
	}

	/**
	 * At each point of the path: the camera waits until the view is meshed and the generation is idle, turns once around
	 * to mesh the whole circle, waits again, then turns once more ({@value #SWEEP_TICKS} ticks, the yaw moved every tick)
	 * for the measurement; the measured turns of all points are summed up together. A game
	 * test syncs the client and the server at every client tick, and the client still ticks by the wall clock (20 per
	 * second, at most one tick per frame), so the ticks per second show 20 for every build whose frames take under 50 ms;
	 * the frame rate is therefore the game's own frames per second ({@link Minecraft#getFps}), sampled at every tick after
	 * the first second of each turn. When a frame takes over 50 ms, each frame has its tick and the server waits for it.
	 */
	private static void sweeps(ClientGameTestContext context, TestSingleplayerContext sp, String label, double[][] points) {
		long wall = 0;
		int n = 0;
		long[] f = new long[points.length * SWEEP_TICKS];
		long[] ticks = new long[points.length * SWEEP_TICKS];
		long[] loops = new long[points.length * SWEEP_TICKS];
		double server = 0;
		double fpsSum = 0;
		int fpsN = 0;
		context.runOnClient(mc -> {
			gpuSum = 0;
			gpuCount = 0;
			levelFrames = 0;
		});
		StringBuilder perPoint = new StringBuilder();
		for (double[] q : points) {
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 %.1f", q[0], q[1], q[2], q[3]));
			context.waitTicks(10);
			settle(context);
			// A warm-up turn: the client meshes the sections of the view only, so the first turn would also build the
			// sections behind the camera; the measured turn then draws a meshed view.
			yawStep = 360f / SWEEP_TICKS;
			context.waitTicks(SWEEP_TICKS);
			yawStep = 0;
			settle(context);
			frameCount = 0;
			yawStep = 360f / SWEEP_TICKS;
			recording = true;
			long t0 = System.nanoTime();
			context.waitTicks(SWEEP_TICKS);
			wall += System.nanoTime() - t0;
			recording = false;
			yawStep = 0;
			server += sp.getServer().computeOnServer(s -> s.getAverageTickTimeNanos() / 1e6);
			int c = Math.min(frameCount, SWEEP_TICKS);
			System.arraycopy(FRAMES, 0, f, n, c);
			System.arraycopy(TICKS, 0, ticks, n, c);
			// The frame rate of the game (frames counted over each second) at the ticks after the first second of the turn.
			double pointSum = 0;
			for (int i = FPS_FROM; i < c; i++) {
				fpsSum += FPS[i];
				fpsN++;
				pointSum += FPS[i];
			}
			perPoint.append(String.format(Locale.ROOT, " %.0f", pointSum / Math.max(1, c - FPS_FROM)));
			// The first loop of a turn started before the recording.
			System.arraycopy(LOOPS, 1, loops, n, Math.max(0, c - 1));
			n += c;
		}
		long[] gpuRead = context.computeOnClient(mc -> new long[] {gpuSum, gpuCount, levelFrames});
		f = Arrays.copyOf(f, n);
		Arrays.sort(f);
		ticks = Arrays.copyOf(ticks, n);
		Arrays.sort(ticks);
		loops = Arrays.copyOf(loops, Math.max(0, n - points.length));
		Arrays.sort(loops);
		double mean = Arrays.stream(f).average().orElse(0) / 1e6;
		PolishForests.LOG.info(String.format(Locale.ROOT, "[performance] %s flight: %d points, %d ticks in %.2f s (%.1f ticks/s); "
				+ "FPS %.1f (mean of the game's frames per second over the turns; per point%s), %d frames drawn (%.1f/s); GPU time of the "
				+ "level pass per frame %.2f ms (%d queries); CPU render time per frame median %.2f ms, mean %.2f ms, p95 %.2f ms, "
				+ "p99 %.2f ms, max %.2f ms; server tick %.2f ms (mean of the 100-tick averages after each turn); client tick median "
				+ "%.2f ms, p95 %.2f ms; whole loop median %.2f ms, p95 %.2f ms", label, points.length, n, wall / 1e9, n / (wall / 1e9),
				fpsSum / Math.max(1, fpsN), perPoint, gpuRead[2], gpuRead[2] / (wall / 1e9),
				gpuRead[0] / 1e6 / Math.max(1, gpuRead[1]), gpuRead[1], at(f, 0.5), mean, at(f, 0.95), at(f, 0.99),
				n == 0 ? 0 : f[n - 1] / 1e6, server / points.length, at(ticks, 0.5), at(ticks, 0.95), at(loops, 0.5), at(loops, 0.95)));
		context.takeScreenshot("flight_" + label.replace(" / ", "_").replace(' ', '_'));
	}

	/**
	 * Waits until the view is meshed, no chunk was decorated during the last 20 ticks and 20 ticks take as long as the 20
	 * before them (within 15%): the server has no chunk work left, so the turns measure the frames (at most 3000 ticks).
	 */
	private static void settle(ClientGameTestContext context) {
		long decorated = pl.polishforests.worldgen.chunk.PolandChunkGenerator.DECORATION_CHUNKS.sum();
		long previous = Long.MAX_VALUE;
		for (int i = 0; i < 150; i++) {
			long t0 = System.nanoTime();
			context.waitTicks(20);
			long t = System.nanoTime() - t0;
			long now = pl.polishforests.worldgen.chunk.PolandChunkGenerator.DECORATION_CHUNKS.sum();
			boolean meshed = context.computeOnClient(mc -> mc.levelRenderer.hasRenderedAllSections());
			if (meshed && now == decorated && Math.abs(t - previous) < 0.15 * t) {
				return;
			}
			decorated = now;
			previous = t;
		}
		PolishForests.LOG.warn("[performance] the view did not settle in 3000 ticks");
	}

	private static double at(long[] sorted, double q) {
		return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * q) - 1)] / 1e6;
	}

	private static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		ClientTickEvents.START_CLIENT_TICK.register(mc -> tickStart = System.nanoTime());
		ClientTickEvents.END_CLIENT_TICK.register(ForestPerformance::tick);
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.START_MAIN.register(c -> startLevel());
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_MAIN.register(c -> endLevel());
	}

	/** Start of the level pass of a frame (render thread): counts the frame and starts a GPU timestamp query when free. */
	private static void startLevel() {
		if (recording) {
			levelFrames++;
		}
		if (gpu == null) {
			gpu = new com.mojang.blaze3d.systems.TimerQuery();
		}
		if (gpu.getStatus() != com.mojang.blaze3d.systems.TimerQuery.Status.NOT_RECORDING) {
			return;
		}
		if (gpuAwaiting) {
			// A query finished: get() is the mean of the last three results; the first three are skipped (zeros at start).
			gpuAwaiting = false;
			if (++gpuWarm > 3 && recording) {
				gpuSum += gpu.get();
				gpuCount++;
			}
		}
		if (recording) {
			gpu.beginProfile();
		}
	}

	private static void endLevel() {
		if (gpu != null && gpu.getStatus() == com.mojang.blaze3d.systems.TimerQuery.Status.STARTED) {
			gpu.endProfile();
			gpuAwaiting = true;
		}
	}

	private static void tick(Minecraft mc) {
		long now = System.nanoTime();
		if (recording && frameCount < FRAMES.length) {
			TICKS[frameCount] = now - tickStart;
			LOOPS[frameCount] = now - lastEnd;
			FPS[frameCount] = mc.getFps();
			FRAMES[frameCount++] = mc.getFrameTimeNs();
		}
		lastEnd = now;
		float step = yawStep;
		if (step != 0 && mc.player != null) {
			mc.player.setYRot(mc.player.getYRot() + step);
			mc.player.setYHeadRot(mc.player.getYRot());
		}
	}

	/** An eye-level point near (x, z): the nearest column within 4 blocks whose feet and head blocks are free of leaves and wood. */
	private static double[] eyePoint(ServerLevel level, double x, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		for (int r = 0; r <= 4; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int y = surface(level, bx + dx, bz + dz);
					BlockPos feet = new BlockPos(bx + dx, y, bz + dz);
					if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).isAir()
							&& level.getBlockState(feet.above(2)).isAir()) {
						return new double[] {bx + dx + 0.5, y, bz + dz + 0.5, 0};
					}
				}
			}
		}
		return new double[] {x, surface(level, bx, bz), z, 0};
	}

	/** The first free block above the ground of the column: the top of the terrain under trees, plants and leaves. */
	static int surface(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
		while (pos.getY() > level.getMinY()) {
			pos.move(Direction.DOWN);
			BlockState s = level.getBlockState(pos);
			if (!s.isAir() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && s.isFaceSturdy(level, pos, Direction.UP)) {
				return pos.getY() + 1;
			}
			if (!s.getFluidState().isEmpty()) {
				return pos.getY() + 1;
			}
		}
		return level.getMinY();
	}

	// ------------------------------------------------------------------ light under the canopy (PW-13)

	/**
	 * Light at the ground of the inner 6 × 6 chunks of an 8 × 8 area around (x, z) at noon (after generating it to FULL):
	 * the share of the ground columns under leaves, the mean sky light at the ground (all and under leaves), the share with
	 * sky light ≤ 7, and the mean chance that a monster may spawn there by the vanilla light rule (64 draws of
	 * {@link Monster#isDarkEnoughToSpawn} per column).
	 */
	static String groundLight(ServerLevel level, String label, int x, int z) {
		int ox = (x >> 4) - 4;
		int oz = (z >> 4) - 4;
		for (int cx = 0; cx < 8; cx++) {
			for (int cz = 0; cz < 8; cz++) {
				level.getChunkSource().getChunk(ox + cx, oz + cz, ChunkStatus.FULL, true);
			}
		}
		RandomSource random = RandomSource.create(0x5EED_0013L);
		int columns = 0;
		int covered = 0;
		long sky = 0;
		long skyCovered = 0;
		int dark = 0;
		double chance = 0;
		double chanceCovered = 0;
		int[] histogram = new int[16];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int bx = (ox + 1) << 4; bx < (ox + 7) << 4; bx++) {
			for (int bz = (oz + 1) << 4; bz < (oz + 7) << 4; bz++) {
				int y = surface(level, bx, bz);
				pos.set(bx, y, bz);
				BlockState at = level.getBlockState(pos);
				if (!at.getFluidState().isEmpty() || !at.getCollisionShape(level, pos).isEmpty()
						|| !level.getBlockState(pos.below()).getFluidState().isEmpty()) {
					continue;
				}
				columns++;
				boolean under = false;
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
				for (int yy = y + 1; yy < top && !under; yy++) {
					under = level.getBlockState(new BlockPos(bx, yy, bz)).is(BlockTags.LEAVES);
				}
				int s = level.getBrightness(LightLayer.SKY, pos);
				histogram[s]++;
				int hits = 0;
				BlockPos fixed = pos.immutable();
				for (int k = 0; k < 64; k++) {
					hits += Monster.isDarkEnoughToSpawn(level, fixed, random) ? 1 : 0;
				}
				sky += s;
				chance += hits / 64.0;
				dark += s <= 7 ? 1 : 0;
				if (under) {
					covered++;
					skyCovered += s;
					chanceCovered += hits / 64.0;
				}
			}
		}
		return String.format(Locale.ROOT, "[performance] %s: ground light at noon over %d columns: under leaves %.1f%%, sky light "
				+ "mean %.2f (under leaves %.2f), sky light <= 7 in %.2f%%, chance of a dark enough spawn place %.3f%% (under leaves "
				+ "%.3f%%), sky light histogram 0..15 %s", label, columns, 100.0 * covered / Math.max(1, columns),
				(double) sky / Math.max(1, columns), (double) skyCovered / Math.max(1, covered), 100.0 * dark / Math.max(1, columns),
				100 * chance / Math.max(1, columns), 100 * chanceCovered / Math.max(1, covered), Arrays.toString(histogram));
	}

	/** Places of the light measurement in a vanilla world: the nearest dark forest and forest from (0, 0). */
	static int[] vanillaPlace(ServerLevel level, boolean dark) {
		var found = level.findClosestBiome3d(h -> h.is(dark ? Biomes.DARK_FOREST : Biomes.FOREST), new BlockPos(1200, 64, 1200), 12800, 32,
				64);
		if (found == null) {
			return null;
		}
		return new int[] {found.getFirst().getX(), found.getFirst().getZ()};
	}

	/**
	 * A live spawn test (PW-13): a creative player stands at the ground of (x, z) at noon with mob spawning on; after
	 * {@code ticks} ticks the monsters within 64 blocks horizontally that stand at most 2 blocks below the ground of their
	 * column (no cave spawns) are counted, then removed.
	 */
	static void liveSpawns(ClientGameTestContext context, TestSingleplayerContext sp, String label, int x, int z, int ticks) {
		int ground = sp.getServer().computeOnServer(s -> surface(s.overworld(), x, z));
		sp.getServer().runCommand("gamemode creative @a");
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", x, ground, z));
		context.waitTicks(100);
		sp.getServer().runCommand("difficulty normal");
		sp.getServer().runCommand("kill @e[type=!player]");
		sp.getServer().runCommand("gamerule spawn_mobs true");
		context.waitTicks(ticks);
		sp.getServer().runCommand("gamerule spawn_mobs false");
		String result = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			int surface = 0;
			int all = 0;
			StringBuilder kinds = new StringBuilder();
			for (var e : level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(x - 64, level.getMinY(), z - 64, x + 64,
					level.getMaxY(), z + 64), e -> e.getType().getCategory() == MobCategory.MONSTER)) {
				all++;
				if (e.getY() >= surface(level, e.getBlockX(), e.getBlockZ()) - 2) {
					surface++;
					kinds.append(' ').append(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath());
				}
			}
			return String.format(Locale.ROOT, "%d monsters at the surface (%d in all) after %d ticks at noon:%s", surface, all, ticks,
					kinds);
		});
		PolishForests.LOG.info("[performance] {}: live spawn test, {}", label, result);
		sp.getServer().runCommand("kill @e[type=!player]");
		sp.getServer().runCommand("gamemode spectator @a");
	}

	static Optional<int[]> place(boolean real, String name) {
		return SliceSites.of(real, false).stream().filter(p -> p.name().equals(name)).map(SliceSites.Place::middle).findFirst();
	}
}
