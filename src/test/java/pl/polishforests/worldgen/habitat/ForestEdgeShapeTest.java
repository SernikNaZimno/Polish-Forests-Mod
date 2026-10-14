package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/**
 * Shape of the forest edges of the PRESENT_DAY mode (step S8c, decision M2-20 variant d2, rule Z10;
 * docs/03-m2-biomy.md §3.4.1 "Stan po S8c"). An edge is a pair of neighboring land columns 1 m apart, both of a forest
 * site (a forest biome before the mask), one forested and the other open. Three measures:
 * <ul>
 * <li><b>Rows:</b> 400 random rows of 4000 columns per scale. An edge "on a P jump" has |ΔP_forest| &gt; 0.02 between the
 * two columns (the measure of the S8 review), the rest lie on contours of F. Reported: the share on P jumps (with the gray
 * alder strip and the valley floor margin separately), the mean and median |dP| per block, and the steepness probe of
 * the S8 review ({@code GradProbe}): at edges on valley sides, how often the local |dH| per block (central differences of
 * samples 1 m apart) exceeds the steepness that the mask uses for the ramp widths 3 and 10 times.</li>
 * <li><b>Straightness:</b> 30 windows of 256 × 256 m with forest edges per scale; a forest pixel of an edge lies on a
 * straight stretch when a line through it (90 directions) has edge pixels within ±1.5 blocks at every step over at
 * least 40 blocks (also 80 blocks within ±3 and 160 within ±6, {@link #LENGTHS}).</li>
 * <li><b>The example of the S10 review:</b> REAL, the fresh meadow among the montane beech forest at (150034, 1049784):
 * the west edge from (149980, 1049752) to (150118, 1050040) and the east edge at x ≈ 150310–150334 were almost straight
 * lines; the longest straight stretch of the window and the share of its edge pixels on straight stretches. The mask of
 * the window is written to {@code build/s8c/}.</li>
 * </ul>
 * The same file measured the state before step S8c on its base commit (with the PRESENT_DAY classifier built from the
 * scale, {@link #presentDay}); the assertions hold the state after it.
 */
@Tag("slow")
class ForestEdgeShapeTest {
	private static final long SEED = 20260927L;
	private static final int ROWS = 400;
	private static final int ROW_LENGTH = 4_000;
	private static final int WINDOWS = 30;
	private static final int WINDOW = 256;
	/**
	 * Straight stretches: at least {@code LENGTHS[i]} blocks within ±{@code BANDS[i]} of a line. The first is the measure of
	 * the task (40 blocks within ±1.5); the other two keep the ratio of the band to the length and catch long, slightly
	 * curved arcs that look straight from afar (the example of the S10 review is such an arc of about 200 m).
	 */
	private static final int[] LENGTHS = {40, 80, 160};
	private static final double[] BANDS = {1.5, 3, 6};
	private static final int STRAIGHT = LENGTHS[0];
	private static final double JUMP = 0.02;
	private static final Method RISE;

	static {
		try {
			RISE = ForestCover.class.getDeclaredMethod("rise", HabitatClassifier.Column.class, double.class);
			RISE.setAccessible(true);
		} catch (NoSuchMethodException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private static HabitatClassifier presentDay(LandscapeModel m) {
		return new HabitatClassifier(SEED, m, HabitatClassifier.Mode.PRESENT_DAY);
	}

	/** Steepness that the mask uses for the widths of the transitions (m per m). */
	private static double maskGradient(HabitatClassifier d, HabitatClassifier.Column c) {
		try {
			return (double) RISE.invoke(d.forestCover, c, 1.0);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new IllegalStateException(e);
		}
	}

	/** State of a column: 0 water or no forest site, 1 forested, 2 a forest site left open by the mask. */
	private static int state(ColumnSample s, Habitat h) {
		if (s.hasWater() || h.biome().isWater() || h.siteType() == ForestSiteType.NONE) {
			return 0;
		}
		return h.biome().isForest() ? 1 : 2;
	}

	/** Forest biome of the column before the mask: its own when forested, otherwise that of the NATURAL mode. */
	private static HabitatBiome forestBiome(Habitat d, HabitatClassifier natural, ColumnSample s, double x, double z) {
		if (d.biome().isForest()) {
			return d.biome();
		}
		HabitatBiome n = Habitat.of(natural.classify(s, x, z)).biome();
		// The heath openings of the NATURAL mode on dunes are dry pine forest in the PRESENT_DAY mode.
		return n.isForest() ? n : HabitatBiome.DRY_PINE_FOREST;
	}

	private static double p(HabitatClassifier d, HabitatClassifier.Column c, HabitatBiome biome, Habitat h) {
		return d.forestCover.forestProbability(c, biome, h.siteType());
	}

	// ------------------------------------------------------------------ rows

	/** Counters of the rows: edges, on P jumps, gray alder, floor margin, valley side edges, steeper than 3×, 10×. */
	private static final int EDGES = 0, JUMPS = 1, ALDER = 2, FLOOR = 3, SIDE = 4, SIDE3 = 5, SIDE10 = 6, SIDE_JUMPS = 7,
			SIDE_JUMPS3 = 8, SIDE_JUMPS10 = 9, COUNTERS = 10;

	private record RowResult(long[] counts, double[] dp) {
	}

	private static RowResult row(LandscapeModel m, HabitatClassifier d, HabitatClassifier natural, double x0, double z) {
		int n = ROW_LENGTH + 1;
		ColumnSample[] s = new ColumnSample[n];
		Habitat[] h = new Habitat[n];
		int[] st = new int[n];
		for (int i = 0; i < n; i++) {
			s[i] = m.sample(x0 + i, z);
			h[i] = Habitat.of(d.classify(s[i], x0 + i, z));
			st[i] = state(s[i], h[i]);
		}
		long[] counts = new long[COUNTERS];
		List<Double> dps = new ArrayList<>();
		for (int i = 1; i + 1 < n; i++) {
			if (st[i] == 0 || st[i + 1] == 0 || st[i] == st[i + 1]) {
				continue;
			}
			double x = x0 + i;
			HabitatClassifier.Column a = new HabitatClassifier.Column(d, s[i], x, z);
			HabitatClassifier.Column b = new HabitatClassifier.Column(d, s[i + 1], x + 1, z);
			HabitatBiome ba = forestBiome(h[i], natural, s[i], x, z);
			HabitatBiome bb = forestBiome(h[i + 1], natural, s[i + 1], x + 1, z);
			double dp = Math.abs(p(d, a, ba, h[i]) - p(d, b, bb, h[i + 1]));
			dps.add(dp);
			counts[EDGES]++;
			boolean jump = dp > JUMP;
			if (jump) {
				counts[JUMPS]++;
				if (ba == HabitatBiome.GRAY_ALDER_FOREST || bb == HabitatBiome.GRAY_ALDER_FOREST) {
					counts[ALDER]++;
				} else if (a.onValleyFloor() != b.onValleyFloor() || nearFloor(a) || nearFloor(b)) {
					counts[FLOOR]++;
				}
			}
			if (a.w.streamOrder() > 0 && !a.onValleyFloor() && !Double.isNaN(a.w.softChannelLevel())) {
				HabitatClassifier.Column w = new HabitatClassifier.Column(d, s[i - 1], x - 1, z);
				HabitatClassifier.Column north = new HabitatClassifier.Column(d, m.sample(x, z - 1), x, z - 1);
				HabitatClassifier.Column south = new HabitatClassifier.Column(d, m.sample(x, z + 1), x, z + 1);
				double local = Math.hypot((b.H - w.H) / 2, (south.H - north.H) / 2);
				double used = Math.max(maskGradient(d, a), 1e-6);
				counts[SIDE]++;
				if (local > 3 * used) {
					counts[SIDE3]++;
				}
				if (local > 10 * used) {
					counts[SIDE10]++;
				}
				if (jump) {
					counts[SIDE_JUMPS]++;
					if (local > 3 * used) {
						counts[SIDE_JUMPS3]++;
					}
					if (local > 10 * used) {
						counts[SIDE_JUMPS10]++;
					}
				}
			}
		}
		return new RowResult(counts, dps.stream().mapToDouble(Double::doubleValue).toArray());
	}

	/** Within 1.5 m above the floor edge (FLOOR_H above the soft channel level): the ramp of the floor margin. */
	private static boolean nearFloor(HabitatClassifier.Column c) {
		if (c.w.streamOrder() <= 0 || Double.isNaN(c.w.softChannelLevel())) {
			return false;
		}
		double above = c.H - c.w.softChannelLevel() - Calibration.FLOOR_H;
		return above >= 0 && above < 1.5;
	}

	private static String rows(LandscapeScale scale, LandscapeModel m, HabitatClassifier d, HabitatClassifier natural, double[] shares) {
		double span = scale == LandscapeScale.REALISTIC ? 300_000 : 30_000;
		Random r = new Random(0x5C8C_0001L + (scale == LandscapeScale.REALISTIC ? 0 : 1));
		double[][] starts = new double[ROWS][];
		for (int i = 0; i < ROWS; i++) {
			starts[i] = new double[] {(r.nextDouble() - 0.5) * span, (r.nextDouble() - 0.5) * span};
		}
		RowResult[] res = new RowResult[ROWS];
		IntStream.range(0, ROWS).parallel().forEach(i -> res[i] = row(m, d, natural, Math.floor(starts[i][0]), Math.floor(starts[i][1]) + 0.5));
		long[] c = new long[COUNTERS];
		double[] all = Arrays.stream(res).flatMapToDouble(x -> Arrays.stream(x.dp())).sorted().toArray();
		for (RowResult x : res) {
			for (int k = 0; k < COUNTERS; k++) {
				c[k] += x.counts()[k];
			}
		}
		shares[0] = pct(c[JUMPS], c[EDGES]);
		shares[1] = pct(c[SIDE_JUMPS3], c[SIDE_JUMPS]);
		double mean = Arrays.stream(all).average().orElse(0);
		double median = all.length == 0 ? 0 : all[all.length / 2];
		double p90 = all.length == 0 ? 0 : all[(int) (all.length * 0.9)];
		return String.format(Locale.ROOT,
				"rows %d × %d: %d edges, on P jumps %.1f%% (gray alder strip %.1f%%, floor margin %.1f%%, other %.1f%% of all edges);"
						+ " |dP| per block mean %.4f, median %.4f, 90%% %.4f; valley side edges %d: local |dH| above 3× the mask steepness %.1f%%,"
						+ " 10× %.1f%%; of them on P jumps %d: 3× %.1f%%, 10× %.1f%%",
				ROWS, ROW_LENGTH, c[EDGES], pct(c[JUMPS], c[EDGES]), pct(c[ALDER], c[EDGES]), pct(c[FLOOR], c[EDGES]),
				pct(c[JUMPS] - c[ALDER] - c[FLOOR], c[EDGES]), mean, median, p90, c[SIDE], pct(c[SIDE3], c[SIDE]),
				pct(c[SIDE10], c[SIDE]), c[SIDE_JUMPS], pct(c[SIDE_JUMPS3], c[SIDE_JUMPS]), pct(c[SIDE_JUMPS10], c[SIDE_JUMPS]));
	}

	private static double pct(long a, long b) {
		return b == 0 ? 0 : 100.0 * a / b;
	}

	// ------------------------------------------------------------------ straightness

	/** States of a window of w × h columns from (x0, z0), rows in parallel. */
	private static int[] states(LandscapeModel m, HabitatClassifier d, double x0, double z0, int w, int h) {
		int[] st = new int[w * h];
		IntStream.range(0, h).parallel().forEach(j -> {
			for (int i = 0; i < w; i++) {
				double x = x0 + i;
				double z = z0 + j;
				ColumnSample s = m.sample(x, z);
				st[j * w + i] = state(s, Habitat.of(d.classify(s, x, z)));
			}
		});
		return st;
	}

	/** Forest pixels with a 4-neighbor left open by the mask. */
	private static boolean[] edges(int[] st, int w, int h) {
		boolean[] e = new boolean[w * h];
		for (int j = 1; j < h - 1; j++) {
			for (int i = 1; i < w - 1; i++) {
				int k = j * w + i;
				e[k] = st[k] == 1 && (st[k - 1] == 2 || st[k + 1] == 2 || st[k - w] == 2 || st[k + w] == 2);
			}
		}
		return e;
	}

	/** Longest straight stretch within ±{@code band} through each edge pixel (0 for other pixels): see the class comment. */
	private static int[] straightRuns(boolean[] e, int w, int h, double band) {
		int[] out = new int[w * h];
		double[] ux = new double[90];
		double[] uz = new double[90];
		for (int a = 0; a < 90; a++) {
			ux[a] = Math.cos(Math.toRadians(2 * a));
			uz[a] = Math.sin(Math.toRadians(2 * a));
		}
		IntStream.range(0, h).parallel().forEach(j -> {
			for (int i = 0; i < w; i++) {
				if (!e[j * w + i]) {
					continue;
				}
				int best = 0;
				for (int a = 0; a < 90; a++) {
					int run = walk(e, w, h, i, j, ux[a], uz[a], band) + walk(e, w, h, i, j, -ux[a], -uz[a], band);
					best = Math.max(best, run);
				}
				out[j * w + i] = best;
			}
		});
		return out;
	}

	private static int walk(boolean[] e, int w, int h, int i, int j, double ux, double uz, double band) {
		int t = 1;
		for (; t < 1_000; t++) {
			double cx = i + t * ux;
			double cz = j + t * uz;
			boolean found = false;
			for (double s = -band; s <= band && !found; s += 0.5) {
				int px = (int) Math.round(cx - s * uz);
				int pz = (int) Math.round(cz + s * ux);
				if (px < 0 || pz < 0 || px >= w || pz >= h) {
					return t - 1;
				}
				found = e[pz * w + px];
			}
			if (!found) {
				return t - 1;
			}
		}
		return t - 1;
	}

	/** Runs of every level ({@link #LENGTHS}, {@link #BANDS}). */
	private static int[][] allRuns(boolean[] e, int w, int h) {
		int[][] runs = new int[LENGTHS.length][];
		for (int l = 0; l < LENGTHS.length; l++) {
			runs[l] = straightRuns(e, w, h, BANDS[l]);
		}
		return runs;
	}

	/** Edge pixels, those on straight stretches of every level, and the longest stretch of the first level. */
	private record Straightness(long edges, long[] straight, int longest) {
		Straightness plus(Straightness o) {
			long[] sum = new long[LENGTHS.length];
			for (int l = 0; l < sum.length; l++) {
				sum[l] = straight[l] + o.straight[l];
			}
			return new Straightness(edges + o.edges, sum, Math.max(longest, o.longest));
		}

		double share(int level) {
			return pct(straight[level], edges);
		}

		String describe() {
			StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "%d edge pixels, on straight stretches", edges));
			for (int l = 0; l < LENGTHS.length; l++) {
				b.append(String.format(Locale.ROOT, "%s ≥ %d blocks within ±%.1f %.1f%%", l == 0 ? "" : ",", LENGTHS[l], BANDS[l], share(l)));
			}
			return b.append(String.format(Locale.ROOT, "; longest (±%.1f) %d blocks", BANDS[0], longest)).toString();
		}
	}

	private static Straightness straightness(int[] st, int w, int h) {
		boolean[] e = edges(st, w, h);
		return count(e, allRuns(e, w, h), k -> true);
	}

	private static Straightness count(boolean[] e, int[][] runs, java.util.function.IntPredicate region) {
		long edges = 0;
		long[] straight = new long[LENGTHS.length];
		int longest = 0;
		for (int k = 0; k < e.length; k++) {
			if (e[k] && region.test(k)) {
				edges++;
				for (int l = 0; l < LENGTHS.length; l++) {
					if (runs[l][k] >= LENGTHS[l]) {
						straight[l]++;
					}
				}
				longest = Math.max(longest, runs[0][k]);
			}
		}
		return new Straightness(edges, straight, longest);
	}

	private static String windows(LandscapeScale scale, LandscapeModel m, HabitatClassifier d, Straightness[] total) {
		double span = scale == LandscapeScale.REALISTIC ? 300_000 : 30_000;
		Random r = new Random(0x5C8C_0002L + (scale == LandscapeScale.REALISTIC ? 0 : 1));
		Straightness sum = new Straightness(0, new long[LENGTHS.length], 0);
		int used = 0;
		int withStraight = 0;
		for (int tries = 0; tries < 400 && used < WINDOWS; tries++) {
			double x0 = Math.floor((r.nextDouble() - 0.5) * span);
			double z0 = Math.floor((r.nextDouble() - 0.5) * span);
			// A coarse look first: forested and open forest sites among 8 × 8 points.
			boolean forest = false;
			boolean open = false;
			for (int k = 0; k < 64; k++) {
				double x = x0 + (k % 8) * 32 + 16;
				double z = z0 + (k / 8) * 32 + 16;
				ColumnSample s = m.sample(x, z);
				int v = state(s, Habitat.of(d.classify(s, x, z)));
				forest |= v == 1;
				open |= v == 2;
			}
			if (!forest || !open) {
				continue;
			}
			Straightness s = straightness(states(m, d, x0, z0, WINDOW, WINDOW), WINDOW, WINDOW);
			if (s.edges() < 100) {
				continue;
			}
			used++;
			if (s.longest() >= STRAIGHT) {
				withStraight++;
			}
			sum = sum.plus(s);
		}
		total[0] = sum;
		return String.format(Locale.ROOT, "%d windows %d × %d m (%d with a straight stretch): %s", used, WINDOW, WINDOW, withStraight,
				sum.describe());
	}

	// ------------------------------------------------------------------ the example of the S10 review

	private static final int EX_X0 = 149_880, EX_Z0 = 1_049_680, EX_W = 540, EX_H = 420;

	/**
	 * The window of the example: the whole, the west edge (within 30 m of the line from (149980, 1049752) to
	 * (150118, 1049905), where the edge of the screenshot turns east; the review gave 1050040) and the east edge (x 150290–150354, z 1049730–1049910).
	 */
	private static Straightness[] example(LandscapeModel m, HabitatClassifier d) throws IOException {
		int[] st = states(m, d, EX_X0, EX_Z0, EX_W, EX_H);
		boolean[] e = edges(st, EX_W, EX_H);
		int[][] runs = allRuns(e, EX_W, EX_H);
		BufferedImage img = new BufferedImage(EX_W, EX_H, BufferedImage.TYPE_INT_RGB);
		for (int j = 0; j < EX_H; j++) {
			for (int i = 0; i < EX_W; i++) {
				int k = j * EX_W + i;
				int rgb = switch (st[k]) {
					case 1 -> 0x2E6B2E;
					case 2 -> 0xC8D27A;
					default -> 0x4A78B0;
				};
				if (e[k]) {
					rgb = runs[2][k] >= LENGTHS[2] ? 0xE020E0 : runs[0][k] >= LENGTHS[0] ? 0xE02020 : 0x111111;
				}
				img.setRGB(i, j, rgb);
			}
		}
		File dir = new File("build/s8c");
		dir.mkdirs();
		ImageIO.write(img, "png", new File(dir, "s10_example_mask.png"));
		double ax = 149_980 - EX_X0, az = 1_049_752 - EX_Z0, bx = 150_118 - EX_X0, bz = 1_049_905 - EX_Z0;
		java.util.function.IntPredicate west = k -> {
			double px = k % EX_W - ax, pz = k / EX_W - az, dx = bx - ax, dz = bz - az;
			double t = Math.clamp((px * dx + pz * dz) / (dx * dx + dz * dz), 0.0, 1.0);
			return Math.hypot(px - t * dx, pz - t * dz) <= 30;
		};
		java.util.function.IntPredicate east = k -> {
			int x = k % EX_W + EX_X0, z = k / EX_W + EX_Z0;
			return x >= 150_290 && x <= 150_354 && z >= 1_049_730 && z <= 1_049_910;
		};
		return new Straightness[] {count(e, runs, k -> true), count(e, runs, west), count(e, runs, east)};
	}

	/**
	 * Limits of the state after step S8c (measured: REAL / GAMEPLAY 13.9 / 24.7% of the edges on P jumps, 15.7 / 2.9% of
	 * the valley side edges on P jumps steeper than 3 times the mask steepness, 10.3 / 13.1% of the edge pixels on straight
	 * stretches; before S8c 32.9 / 35.6%, 74.1 / 26.9% and 22.9 / 21.4%).
	 */
	@Test
	void forestEdgesAreNotStraight() throws IOException {
		List<String> failures = new ArrayList<>();
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			boolean real = scale == LandscapeScale.REALISTIC;
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			HabitatClassifier d = presentDay(m);
			HabitatClassifier natural = new HabitatClassifier(SEED, scale, HabitatClassifier.Mode.NATURAL);
			double[] shares = new double[2];
			System.out.println("[forest edges] " + scale.id() + " " + rows(scale, m, d, natural, shares));
			Straightness[] total = new Straightness[1];
			System.out.println("[forest edges] " + scale.id() + " " + windows(scale, m, d, total));
			check(failures, scale.id() + ": edges on P jumps", shares[0], real ? 20 : 30);
			check(failures, scale.id() + ": valley side edges on P jumps steeper than 3 × the mask steepness", shares[1], 25);
			check(failures, scale.id() + ": edge pixels on straight stretches", total[0].share(0), 15);
			if (real) {
				Straightness[] ex = example(m, d);
				System.out.println(String.format(Locale.ROOT, "[forest edges] S10 example (%d, %d) %d × %d m: %s; west edge: %s; east edge: %s",
						EX_X0, EX_Z0, EX_W, EX_H, ex[0].describe(), ex[1].describe(), ex[2].describe()));
				for (int i = 1; i <= 2; i++) {
					String edge = i == 1 ? "S10 example, west edge" : "S10 example, east edge";
					check(failures, edge + ": edge pixels on straight stretches", ex[i].share(0), 15);
					check(failures, edge + ": longest straight stretch (blocks)", ex[i].longest(), 60);
				}
			}
		}
		assertTrue(failures.isEmpty(), String.join("\n", failures));
	}

	private static void check(List<String> failures, String what, double value, double limit) {
		if (value > limit) {
			failures.add(String.format(Locale.ROOT, "%s: %.1f above %.1f", what, value, limit));
		}
	}
}
