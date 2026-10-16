package pl.polishforests.gametest.probe;

import java.util.Arrays;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Frame times of the client while a measurement runs (step V3, cost of a mesh rebuild): on every client tick the render
 * time of the last frame ({@code Minecraft.getFrameTimeNs()}, from the start of the frame to the blit, without the frame
 * limiter) and whether every section is compiled ({@code levelRenderer.hasRenderedAllSections()}, Sodium answers it from
 * its own renderer) and the number of sections with a mesh in view ({@code levelExtractor.countRenderedSections()}, also
 * answered by Sodium): a rebuild that drops the meshes shows as a dip of that number. In a client game test the game
 * renders one frame per tick, so the samples are consecutive frames.
 */
public final class FrameRecorder {
	private static final int MAX = 20_000;
	private static final long[] FRAMES = new long[MAX];
	private static final int[] RENDERED = new int[MAX];
	private static volatile int count;
	private static volatile int rendered;
	private static volatile boolean recording;
	private static volatile boolean allCompiled;

	private FrameRecorder() {
	}

	/** Starts a new series (client thread). */
	public static void start() {
		count = 0;
		recording = true;
	}

	/** Stops the series and returns the frame times in nanoseconds (client thread). */
	public static long[] stop() {
		recording = false;
		return Arrays.copyOf(FRAMES, count);
	}

	/** Sections with a mesh in view on each tick of the last series (read after {@link #stop()}). */
	public static int[] renderedSeries() {
		return Arrays.copyOf(RENDERED, count);
	}

	/** Frames recorded so far in the running series. */
	public static int count() {
		return count;
	}

	/** Sections with a mesh in view (as of the last client tick). */
	public static int rendered() {
		return rendered;
	}

	/**
	 * The last {@code frames} frames of the running series all took at most {@code thresholdNanos} (client thread). The end
	 * of a rebuild (review round 1 of V3): Sodium's {@code hasRenderedAllSections()} can answer true before newly dirty
	 * sections are even scheduled, so a rebuild counts as done only after a quiet streak of frames.
	 */
	public static boolean quiet(int frames, long thresholdNanos) {
		int n = count;
		if (n < frames) {
			return false;
		}
		for (int i = n - frames; i < n; i++) {
			if (FRAMES[i] > thresholdNanos) {
				return false;
			}
		}
		return true;
	}

	/** Every section of the view is compiled (as of the last client tick). */
	public static boolean allCompiled() {
		return allCompiled;
	}

	static void tick(Minecraft mc) {
		if (mc.level == null) {
			allCompiled = false;
			rendered = 0;
			return;
		}
		try {
			allCompiled = mc.levelRenderer.hasRenderedAllSections();
			rendered = mc.levelExtractor.countRenderedSections();
		} catch (RuntimeException e) {
			// Sodium answers only once its renderer exists (right after a level change it does not yet).
			allCompiled = false;
			rendered = 0;
		}
		if (recording && count < MAX) {
			FRAMES[count] = mc.getFrameTimeNs();
			RENDERED[count] = rendered;
			count++;
		}
	}

	/** Median, 95th percentile and largest frame time of a series, in milliseconds, for the log. */
	public static String summary(long[] frames) {
		if (frames.length == 0) {
			return "no frames";
		}
		long[] s = frames.clone();
		Arrays.sort(s);
		return String.format(Locale.ROOT, "%d frames, median %.1f ms, p95 %.1f ms, max %.1f ms", s.length, s[s.length / 2] / 1e6,
				s[Math.min(s.length - 1, (int) Math.ceil(s.length * 0.95) - 1)] / 1e6, s[s.length - 1] / 1e6);
	}

	/** {median, p95, max} in milliseconds. */
	public static double[] stats(long[] frames) {
		if (frames.length == 0) {
			return new double[] {0, 0, 0};
		}
		long[] s = frames.clone();
		Arrays.sort(s);
		return new double[] {s[s.length / 2] / 1e6, s[Math.min(s.length - 1, (int) Math.ceil(s.length * 0.95) - 1)] / 1e6,
				s[s.length - 1] / 1e6};
	}
}
