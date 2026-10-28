package pl.polishforests.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.PolishForests;

/**
 * Waiting for the view of a screenshot (step T7): instead of a fixed wait after a teleport, polls every
 * {@value #POLL_TICKS} ticks until the frame is drawn, then always waits {@value #MARGIN_TICKS} ticks more (the user's
 * request of 2026-10-27: light updates and a margin for Sodium) before the screenshot.
 *
 * <p>The frame is drawn when, for {@value #STABLE_TICKS} ticks with the camera standing still:
 * <ul>
 * <li>(a, b) every chunk the rays of the view cone ({@link ViewCone}) pass through up to the reach that fills the frame is
 * loaded on the client (a chunk still missing is reported as generating on the server or as on its way);</li>
 * <li>(c) every section in which a ray met a block is meshed and has no pending compile task, and every section where a
 * ray stopped at a view-blocking block has geometry (vanilla: {@code ViewArea} and the section meshes; Sodium and
 * Nvidium: {@code SodiumWorldRenderer.isSectionReady} through reflection, no hard dependency);</li>
 * <li>and either the compile queue is empty ({@code hasRenderedAllSections}, Sodium's {@code isTerrainRenderComplete})
 * with a steady count of rendered sections, or the cone has been complete for {@value #QUEUE_GRACE_TICKS} ticks
 * ({@value #QUEUE_GRACE_TICKS_SODIUM} with Sodium, which has no per-section pending task) while far chunks outside
 * the frame still mesh.</li>
 * </ul>
 * At least {@value #MIN_TICKS} ticks; at most the old fixed wait or {@value #MIN_SAFETY_SECONDS} s, whichever is
 * longer (the safety limit; then a WARN line with the name of the frame). Every wait is logged with its time and reason
 * ({@code [view]}).
 *
 * <p>Switches: {@code -PviewWait=fixed} restores the old fixed waits (the time before step T7); {@code -PviewCheck}
 * takes, after each screenshot, a reference frame {@code <name>_ref} {@code -PviewCheckSeconds} (60) after the
 * teleport, compares them pixel by pixel ({@link FrameDiff}, clouds off for the whole run) and writes
 * {@code build/view-check/report.txt} and the diff images; {@code -PviewCheckOnly=<regex>} limits the check to some
 * frames; {@code -PviewReach=<blocks>} overrides the reach of the cone.
 */
final class ViewReady {
	static final int POLL_TICKS = 5;
	static final int MIN_TICKS = 20;
	static final int STABLE_TICKS = 15;
	static final int QUEUE_GRACE_TICKS = 40;
	static final int QUEUE_GRACE_TICKS_SODIUM = 60;
	static final int MARGIN_TICKS = 60;
	static final int MIN_SAFETY_SECONDS = 30;
	/** Rays through the frame: columns and rows, 10% beyond the frame's edges. */
	static final int RAY_COLUMNS = 24;
	static final int RAY_ROWS = 14;
	static final double RAY_MARGIN = 1.1;
	static final double RAY_STEP = 0.7;
	/** The camera counts as standing still when it moves less than this between two polls (blocks, degrees). */
	static final double STILL = 0.25;

	private static final boolean FIXED = "fixed".equals(System.getProperty("polishforests.view.wait", ""));
	private static final boolean CHECK = Boolean.getBoolean("polishforests.view.check");
	private static final int CHECK_SECONDS = Integer.getInteger("polishforests.view.checkSeconds", 60);
	private static final Pattern CHECK_ONLY = Pattern.compile(System.getProperty("polishforests.view.checkOnly", "").isBlank() ? ".*"
			: System.getProperty("polishforests.view.checkOnly"));
	private static final double REACH_OVERRIDE = Double.parseDouble(System.getProperty("polishforests.view.reach", "0"));

	private static long totalNanos;
	private static int shots;
	private static int timeouts;
	private static int checked;
	private static int different;
	private static boolean cloudsOff;

	private ViewReady() {
	}

	/** Why a wait ended. */
	enum Reason {
		QUEUE_EMPTY("compile queue empty, rendered sections steady"),
		CONE_READY("view cone complete, far sections still meshing"),
		FALLBACK("no section data of this renderer: chunks in the cone and an empty queue"),
		FIXED("fixed wait (-PviewWait=fixed)"),
		TIMEOUT("safety limit reached");

		final String text;

		Reason(String text) {
			this.text = text;
		}
	}

	/** The outcome of a wait: ticks waited, the reason and the last state of the view. */
	record Result(int ticks, Reason reason, Probe probe) {
	}

	// ------------------------------------------------------------------ public helpers

	/**
	 * Waits for the view, the margin, then takes the screenshot {@code name} ({@code size} = {width, height} without the
	 * counter prefix, or null for the window size with the counter prefix, as {@code takeScreenshot(String)} does).
	 * {@code legacySeconds} is the fixed wait the test used before step T7.
	 */
	static Path shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, int[] size, int legacySeconds) {
		return shot(context, sp, name, size, legacySeconds, 0);
	}

	/** As {@link #shot(ClientGameTestContext, TestSingleplayerContext, String, int[], int)} with a minimum wait in seconds. */
	static Path shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, int[] size, int legacySeconds,
			int minSeconds) {
		return take(context, name, size, beforeShot(context, sp, name, size, legacySeconds, minSeconds));
	}

	/**
	 * Takes the screenshot after {@link #beforeShot} (with {@code -PviewCheck} also the reference frame and the comparison)
	 * and logs the time waited.
	 */
	static Path take(ClientGameTestContext context, String name, int[] size, Result ready) {
		Path path = context.takeScreenshot(options(name, size));
		if (CHECK && !FIXED && CHECK_ONLY.matcher(name).find()) {
			check(context, name, size, path, ready.ticks() + MARGIN_TICKS);
		}
		PolishForests.LOG.info(String.format(Locale.ROOT, "[view] %s: screenshot after %.1f s (all frames so far: %d, %.0f s waiting, %d over "
				+ "the safety limit)", name, (ready.ticks() + (FIXED ? 0 : MARGIN_TICKS)) / 20.0, shots, totalNanos / 1e9, timeouts));
		return path;
	}

	/** Waits for the view and the margin before a screenshot the caller takes itself (with an overlay, for example). */
	static Result beforeShot(ClientGameTestContext context, TestSingleplayerContext sp, String name, int[] size, int legacySeconds,
			int minSeconds) {
		long t0 = System.nanoTime();
		if (CHECK && !cloudsOff) {
			context.runOnClient(mc -> mc.options.cloudStatus().set(CloudStatus.OFF));
			cloudsOff = true;
			PolishForests.LOG.info("[view] check mode: clouds off for the whole run, reference frames {} s after the teleport",
					CHECK_SECONDS);
		}
		Result r = await(context, sp, name, size, legacySeconds, minSeconds);
		if (!FIXED) {
			context.waitTicks(MARGIN_TICKS);
		}
		shots++;
		totalNanos += System.nanoTime() - t0;
		return r;
	}

	/**
	 * Waits until the view of the camera is drawn (no margin): at least {@code minSeconds} (and {@value #MIN_TICKS} ticks),
	 * at most {@code max(legacySeconds, MIN_SAFETY_SECONDS)}. With {@code -PviewWait=fixed} waits {@code legacySeconds}.
	 */
	static Result await(ClientGameTestContext context, TestSingleplayerContext sp, String name, int[] size, int legacySeconds,
			int minSeconds) {
		if (FIXED) {
			context.waitTicks(20 * legacySeconds);
			PolishForests.LOG.info("[view] {}: {} s, {}", name, legacySeconds, Reason.FIXED.text);
			return new Result(20 * legacySeconds, Reason.FIXED, null);
		}
		double aspect = size == null ? 0 : (double) size[0] / size[1];
		int min = Math.max(MIN_TICKS, 20 * minSeconds);
		int max = 20 * Math.max(legacySeconds, MIN_SAFETY_SECONDS);
		int ticks = 0;
		int coneSince = -1;
		int steadySince = -1;
		Probe last = null;
		Reason reason = Reason.TIMEOUT;
		while (ticks < max) {
			context.waitTicks(POLL_TICKS);
			ticks += POLL_TICKS;
			Probe p = context.computeOnClient(mc -> probe(mc, aspect));
			if (p.missing() > 0 && sp != null) {
				long[] keys = p.missingChunks();
				int generating = sp.getServer().computeOnServer(s -> {
					int n = 0;
					for (long k : keys) {
						if (s.overworld().getChunkSource().getChunkNow((int) (k >> 32), (int) k) == null) {
							n++;
						}
					}
					return n;
				});
				p = p.withGenerating(generating);
			}
			boolean moved = last != null && last.moved(p);
			if (!p.coneReady() || moved) {
				coneSince = -1;
			} else if (coneSince < 0) {
				coneSince = ticks;
			}
			boolean steady = p.queueEmpty() && last != null && !moved && Math.abs(p.rendered() - last.rendered()) <= Math.max(2,
					p.rendered() / 100);
			if (!steady) {
				steadySince = -1;
			} else if (steadySince < 0) {
				steadySince = ticks;
			}
			last = p;
			if (ticks < min || coneSince < 0) {
				continue;
			}
			int cone = ticks - coneSince + POLL_TICKS;
			if (p.renderer().equals("unknown")) {
				if (steadySince >= 0 && cone >= STABLE_TICKS && ticks >= 5 * 20) {
					reason = Reason.FALLBACK;
					break;
				}
				continue;
			}
			if (steadySince >= 0 && cone >= STABLE_TICKS && ticks - steadySince + POLL_TICKS >= STABLE_TICKS) {
				reason = Reason.QUEUE_EMPTY;
				break;
			}
			if (cone >= (p.renderer().startsWith("sodium") ? QUEUE_GRACE_TICKS_SODIUM : QUEUE_GRACE_TICKS)) {
				reason = Reason.CONE_READY;
				break;
			}
		}
		String state = last == null ? "no state" : last.describe();
		if (reason == Reason.TIMEOUT) {
			timeouts++;
			PolishForests.LOG.warn(String.format(Locale.ROOT, "[view] %s: the view is not drawn after %.1f s (safety limit): %s", name,
					ticks / 20.0, state));
		} else {
			PolishForests.LOG.info(String.format(Locale.ROOT, "[view] %s: ready after %.2f s (%s); %s", name, ticks / 20.0, reason.text,
					state));
		}
		return new Result(ticks, reason, last);
	}

	/** The screenshot options of the tests: a fixed size without the counter prefix, or the window size with it. */
	static TestScreenshotOptions options(String name, int[] size) {
		return size == null ? TestScreenshotOptions.of(name) : TestScreenshotOptions.of(name).disableCounterPrefix().withSize(size[0], size[1]);
	}

	// ------------------------------------------------------------------ check against a long wait

	private static void check(ClientGameTestContext context, String name, int[] size, Path frame, int waited) {
		context.waitTicks(Math.max(0, 20 * CHECK_SECONDS - waited));
		Path ref = context.takeScreenshot(options(name + "_ref", size == null ? null : size));
		try {
			BufferedImage a = ImageIO.read(frame.toFile());
			BufferedImage b = ImageIO.read(ref.toFile());
			FrameDiff.Result d = FrameDiff.compare(a, b);
			checked++;
			if (!d.equal()) {
				different++;
			}
			String line = String.format(Locale.ROOT, "%s\t%.1f s\t%s", name, waited / 20.0, d.describe());
			if (d.equal()) {
				PolishForests.LOG.info("[view] check {}", line);
			} else {
				PolishForests.LOG.warn("[view] check {}", line);
			}
			String dir = System.getProperty("polishforests.view.out");
			if (dir != null && !dir.isBlank()) {
				Path out = Path.of(dir);
				Files.createDirectories(out);
				ImageIO.write(d.diff(), "png", out.resolve(name + "_diff.png").toFile());
				Files.writeString(out.resolve("report.txt"), line + System.lineSeparator(), StandardCharsets.UTF_8,
						StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
			PolishForests.LOG.info("[view] check so far: {} frames compared, {} different", checked, different);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// ------------------------------------------------------------------ the state of the view (client thread)

	/**
	 * The state of the view at one poll. {@code missing}: chunks of the cone not on the client; {@code generating}: of them
	 * not yet full on the server (-1 = not asked); {@code content}: sections in which a ray met a block, {@code unbuilt}: of
	 * them without a mesh or with a pending compile task; {@code hitSections}: sections where a ray stopped at a
	 * view-blocking block, {@code emptyHits}: of them meshed without geometry (vanilla only).
	 */
	record Probe(String renderer, double x, double y, double z, float yaw, float pitch, int reach, int rays, int chunks, int missing,
			long[] missingChunks, int generating, int content, int unbuilt, int hitSections, int emptyHits, boolean queueEmpty,
			int rendered, int queued) {

		Probe withGenerating(int n) {
			return new Probe(renderer, x, y, z, yaw, pitch, reach, rays, chunks, missing, missingChunks, n, content, unbuilt, hitSections,
					emptyHits, queueEmpty, rendered, queued);
		}

		boolean coneReady() {
			return rays > 0 && missing == 0 && unbuilt == 0 && emptyHits == 0;
		}

		boolean moved(Probe o) {
			return Math.abs(x - o.x) > STILL || Math.abs(y - o.y) > STILL || Math.abs(z - o.z) > STILL || Math.abs(yaw - o.yaw) > STILL
					|| Math.abs(pitch - o.pitch) > STILL;
		}

		String describe() {
			if (rays == 0) {
				return "no level or player on the client";
			}
			return String.format(Locale.ROOT, "renderer %s, cone reach %d blocks, %d rays; chunks %d (missing %d%s), content sections %d "
					+ "(not built %d), hit sections %d (empty %d), compile queue %s, rendered sections %d", renderer, reach, rays,
					chunks, missing, generating < 0 ? "" : ", " + generating + " generating on the server", content, unbuilt,
					hitSections, emptyHits, queueEmpty ? "empty" : queued < 0 ? "busy" : queued + " tasks", rendered);
		}
	}

	static Probe probe(Minecraft mc, double frameAspect) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			return new Probe("none", 0, 0, 0, 0, 0, 0, 0, 0, 0, new long[0], -1, 0, 0, 0, 0, false, 0, -1);
		}
		Vec3 eye = mc.player.getEyePosition();
		float yaw = mc.player.getYRot();
		float pitch = mc.player.getXRot();
		int rd = mc.options.getEffectiveRenderDistance();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(eye.x), (int) Math.floor(eye.z));
		double reach = ViewCone.reach(eye.y - ground, rd, REACH_OVERRIDE);
		double limit = ViewCone.horizontalLimit(rd);
		double aspect = frameAspect > 0 ? frameAspect : (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		double[][] rays = ViewCone.rays(yaw, pitch, mc.options.fov().get(), aspect, RAY_COLUMNS, RAY_ROWS, RAY_MARGIN);
		LongOpenHashSet chunks = new LongOpenHashSet();
		LongOpenHashSet missing = new LongOpenHashSet();
		LongOpenHashSet loaded = new LongOpenHashSet();
		LongOpenHashSet content = new LongOpenHashSet();
		LongOpenHashSet hits = new LongOpenHashSet();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		int maxY = level.getMaxY();
		for (double[] d : rays) {
			for (double t = 0.3; t <= reach; t += RAY_STEP) {
				double px = eye.x + d[0] * t;
				double py = eye.y + d[1] * t;
				double pz = eye.z + d[2] * t;
				if (py < minY || Math.hypot(px - eye.x, pz - eye.z) > limit) {
					break;
				}
				if (py > maxY) {
					// Above the build height: only sky on the way up, terrain later on the way down.
					if (d[1] >= 0) {
						break;
					}
					continue;
				}
				int bx = (int) Math.floor(px);
				int by = (int) Math.floor(py);
				int bz = (int) Math.floor(pz);
				long chunk = ((long) (bx >> 4) << 32) | ((bz >> 4) & 0xFFFFFFFFL);
				chunks.add(chunk);
				if (!loaded.contains(chunk)) {
					if (missing.contains(chunk) || level.getChunkSource().getChunk(bx >> 4, bz >> 4, ChunkStatus.FULL, false) == null) {
						missing.add(chunk);
						break;
					}
					loaded.add(chunk);
				}
				BlockState state = level.getBlockState(at.set(bx, by, bz));
				if (state.isAir()) {
					continue;
				}
				long section = BlockPos.asLong(bx >> 4, by >> 4, bz >> 4);
				content.add(section);
				if (blocksView(state)) {
					hits.add(section);
					break;
				}
			}
		}
		int unbuilt = 0;
		int emptyHits = 0;
		boolean queueEmpty;
		int queued = -1;
		String renderer;
		Sodium sodium = Sodium.get();
		ViewArea area = mc.levelRenderer.viewArea();
		if (sodium != null) {
			Object instance = sodium.instance();
			boolean nvidium = RenderProbesClientGameTest.nvidiumEnabled();
			renderer = nvidium ? "sodium_nvidium" : "sodium";
			if (instance == null) {
				unbuilt = content.size();
				queueEmpty = false;
			} else {
				for (long s : content) {
					if (!sodium.ready(instance, BlockPos.getX(s), BlockPos.getY(s), BlockPos.getZ(s))) {
						unbuilt++;
					}
				}
				queueEmpty = sodium.complete(instance);
			}
		} else if (area != null && !FabricLoader.getInstance().isModLoaded("sodium")) {
			renderer = "vanilla";
			for (long s : content) {
				SectionRenderDispatcher.RenderSection rs = area.getRenderSectionAt(new BlockPos(BlockPos.getX(s) << 4, BlockPos.getY(s) << 4,
						BlockPos.getZ(s) << 4));
				if (rs == null) {
					continue;
				}
				SectionMesh mesh = rs.getSectionMesh();
				if (mesh == CompiledSectionMesh.UNCOMPILED || Pending.of(rs)) {
					unbuilt++;
				} else if (hits.contains(s) && !mesh.hasRenderableLayers()) {
					emptyHits++;
				}
			}
			queueEmpty = mc.levelRenderer.hasRenderedAllSections();
			SectionRenderDispatcher dispatcher = mc.levelRenderer.sectionRenderDispatcher();
			queued = dispatcher == null ? 0 : dispatcher.getCompileQueueSize();
		} else {
			renderer = "unknown";
			boolean empty;
			try {
				empty = mc.levelRenderer.hasRenderedAllSections();
			} catch (RuntimeException e) {
				empty = false;
			}
			queueEmpty = empty;
		}
		int rendered;
		try {
			rendered = mc.levelExtractor.countRenderedSections();
		} catch (RuntimeException e) {
			rendered = -1;
		}
		return new Probe(renderer, eye.x, eye.y, eye.z, yaw, pitch, (int) Math.round(reach), rays.length, chunks.size(), missing.size(),
				missing.toLongArray(), -1, content.size(), unbuilt, hits.size(), emptyHits, queueEmpty, rendered, queued);
	}

	/** A block that hides what lies behind it: opaque blocks, leaves and fluids. */
	static boolean blocksView(BlockState state) {
		if (!state.getFluidState().isEmpty()) {
			return true;
		}
		if (state.getRenderShape() == RenderShape.INVISIBLE) {
			return false;
		}
		return state.canOcclude() || state.getBlock() instanceof LeavesBlock;
	}

	// ------------------------------------------------------------------ renderer access through reflection

	/** The pending compile task of a vanilla section ({@code lastCompileTask} neither completed nor cancelled). */
	private static final class Pending {
		private static Field task;
		private static Field completed;
		private static Field cancelled;
		private static boolean failed;

		static boolean of(SectionRenderDispatcher.RenderSection section) {
			if (failed) {
				return false;
			}
			try {
				if (task == null) {
					task = SectionRenderDispatcher.RenderSection.class.getDeclaredField("lastCompileTask");
					task.setAccessible(true);
					Class<?> base = SectionRenderDispatcher.RenderSection.SectionTask.class;
					completed = base.getDeclaredField("isCompleted");
					completed.setAccessible(true);
					cancelled = base.getDeclaredField("isCancelled");
					cancelled.setAccessible(true);
				}
				Object t = task.get(section);
				return t != null && !((AtomicBoolean) completed.get(t)).get() && !((AtomicBoolean) cancelled.get(t)).get();
			} catch (ReflectiveOperationException | RuntimeException e) {
				failed = true;
				PolishForests.LOG.warn("[view] pending compile tasks of sections not readable ({}), only meshes checked", e.toString());
				return false;
			}
		}
	}

	/** Sodium's world renderer through reflection: {@code instanceNullable}, {@code isSectionReady}, {@code isTerrainRenderComplete}. */
	private record Sodium(Method instanceMethod, Method readyMethod, Method completeMethod) {
		private static Sodium cached;
		private static boolean looked;

		static Sodium get() {
			if (!looked) {
				looked = true;
				if (FabricLoader.getInstance().isModLoaded("sodium")) {
					try {
						Class<?> c = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
						cached = new Sodium(c.getMethod("instanceNullable"), c.getMethod("isSectionReady", int.class, int.class, int.class),
								c.getMethod("isTerrainRenderComplete"));
					} catch (ReflectiveOperationException | RuntimeException e) {
						PolishForests.LOG.warn("[view] Sodium's world renderer not found ({}), falling back to chunks and the queue",
								e.toString());
					}
				}
			}
			return cached;
		}

		Object instance() {
			try {
				return instanceMethod.invoke(null);
			} catch (ReflectiveOperationException e) {
				return null;
			}
		}

		boolean ready(Object renderer, int x, int y, int z) {
			try {
				return (Boolean) readyMethod.invoke(renderer, x, y, z);
			} catch (ReflectiveOperationException | RuntimeException e) {
				return false;
			}
		}

		boolean complete(Object renderer) {
			try {
				return (Boolean) completeMethod.invoke(renderer);
			} catch (ReflectiveOperationException | RuntimeException e) {
				return false;
			}
		}
	}
}
