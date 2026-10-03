package pl.polishforests.worldgen.landscape;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Cost report of {@link LandscapeModel#sample} on a single thread (M2, step S0). No assertions: the result
 * serves the budget from the M2 plan (§3.6, "sample +≤ 5%") and goes into {@code docs/m2/pomiary-bazowe-m1.md}.
 *
 * <p>The current model and the frozen M1 copy ({@code landscape.m1}) are measured alternately in the same JVM,
 * over the same chunks. The noise between runs (±10–20%) is larger than the budget, so the verdict comes from
 * the current/M1 ratio within one run, not from comparing numbers from different days.
 *
 * <p>We sample the way the generator does: whole chunks of 16 × 16 columns in order. 400 distinct chunks
 * (102 400 columns) scattered over an area of about 10 macroregions. Warm-up on other chunks, then
 * {@value #DEFAULT_ROUNDS} rounds (more: {@code -PcostRuns=15}); we report the median
 * and the minimum. The first round hits cold regional caches (cells, river network), the following ones
 * measure the in-game state. Separately, 400 chunks in the Beskids interior (the most expensive terrain: a dense
 * stream network), at the location of the "beskids" patch from the golden test, and 400 chunks on a large Beskid
 * massif (step K2 of the terrain geometry fix; the M1 copy has no massif there). The whole area at realistic scale
 * (±320 km around 0, 0) does not include mountains.
 */
class SampleCostTest {
	private static final long SEED = 20260927L;
	private static final int CHUNKS = 400;
	private static final int DEFAULT_ROUNDS = 7;
	private static final int ROUNDS = Math.max(3, Integer.getInteger("polishforests.cost.runs", DEFAULT_ROUNDS));
	/** Beskids interior for the seed {@link #SEED} (the "beskids" patch in {@code golden_terrain_m1.txt}). */
	private static final long[] BESKIDS_REAL = {154_834, 1_058_738};
	private static final long[] BESKIDS_GAMEPLAY = {27_609, 3_254};
	/**
	 * Large Beskid massifs (M2-8, step K2): the 1723 m massif at realistic scale and the massif nearest to the spawn at
	 * gameplay scale. The M1 copy has no massif there, so the ratio is the cost of the massif with its PeakField area.
	 */
	private static final long[] MASSIF_REAL = {147_582, -1_525_292};
	private static final long[] MASSIF_GAMEPLAY = {6_854, -33_757};

	/** Prevents the JIT from removing the calls. */
	private static volatile double sink;

	@Test
	void reportSampleCost() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			pl.polishforests.worldgen.landscape.m1.LandscapeModel old = new pl.polishforests.worldgen.landscape.m1.LandscapeModel(
					SEED, scale == LandscapeScale.REALISTIC ? pl.polishforests.worldgen.landscape.m1.LandscapeScale.REALISTIC
							: pl.polishforests.worldgen.landscape.m1.LandscapeScale.GAMEPLAY, 1.0);
			long half = (long) (m.regionSize() * 5);
			long[][] chunks = chunks(0, 0, half, 0x5EED_0001L);
			long[][] warm = chunks(0, 0, half, 0x5EED_0002L);
			for (int r = 0; r < 3; r++) {
				run(m, warm);
				runM1(old, warm);
			}
			report(scale.id() + " (whole area)", m, old, chunks);
			long[] b = scale == LandscapeScale.REALISTIC ? BESKIDS_REAL : BESKIDS_GAMEPLAY;
			long bHalf = (long) (m.regionSize() * 0.15);
			report(scale.id() + " (Beskids)", m, old, chunks(b[0], b[1], bHalf, 0x5EED_0003L));
			long[] g = scale == LandscapeScale.REALISTIC ? MASSIF_REAL : MASSIF_GAMEPLAY;
			report(scale.id() + " (large massif)", m, old, chunks(g[0], g[1], (long) m.greatMassifRa(),
					0x5EED_0004L));
		}
	}

	/**
	 * Rounds over the same chunks, alternating the current model and the M1 copy (the order is reversed every
	 * round), then a breakdown of the current model by landscape type.
	 */
	private static void report(String name, LandscapeModel m, pl.polishforests.worldgen.landscape.m1.LandscapeModel old,
			long[][] chunks) {
		double[] now = new double[ROUNDS];
		double[] m1 = new double[ROUNDS];
		double[] ratio = new double[ROUNDS];
		double columns = chunks.length * 256.0;
		for (int r = 0; r < ROUNDS; r++) {
			if (r % 2 == 0) {
				now[r] = timeNow(m, chunks) / columns;
				m1[r] = timeM1(old, chunks) / columns;
			} else {
				m1[r] = timeM1(old, chunks) / columns;
				now[r] = timeNow(m, chunks) / columns;
			}
			ratio[r] = now[r] / m1[r];
		}
		System.out.println(String.format(Locale.ROOT,
				"[sample cost] %s: %d columns; current: median %.2f µs/column, minimum %.2f; M1: median %.2f, minimum %.2f;"
						+ " current/M1: median %.3f, from the minima %.3f",
				name, (int) columns, median(now), min(now), median(m1), min(m1), median(ratio), min(now) / min(m1)));
		System.out.println(String.format(Locale.ROOT, "[sample cost] %s: rounds current %s, M1 %s", name, format(now),
				format(m1)));
		System.out.println(String.format(Locale.ROOT,
				"[sample cost] %s: µs/column by type at the chunk center (number of chunks):%s", name, perType(m, chunks)));
	}

	private static double timeNow(LandscapeModel m, long[][] chunks) {
		long t0 = System.nanoTime();
		run(m, chunks);
		return (System.nanoTime() - t0) / 1e3;
	}

	private static double timeM1(pl.polishforests.worldgen.landscape.m1.LandscapeModel m, long[][] chunks) {
		long t0 = System.nanoTime();
		runM1(m, chunks);
		return (System.nanoTime() - t0) / 1e3;
	}

	/** Cost by the landscape type at the chunk center (single pass, approximate). */
	private static String perType(LandscapeModel m, long[][] chunks) {
		int types = LandscapeType.values().length;
		long[] nanos = new long[types];
		int[] count = new int[types];
		for (long[] c : chunks) {
			int t = m.sample(c[0] + 8, c[1] + 8).type().ordinal();
			long t0 = System.nanoTime();
			runChunk(m, c);
			nanos[t] += System.nanoTime() - t0;
			count[t]++;
		}
		StringBuilder perType = new StringBuilder();
		for (LandscapeType t : LandscapeType.values()) {
			if (count[t.ordinal()] > 0) {
				perType.append(String.format(Locale.ROOT, " %s=%.2f (%d)", t, nanos[t.ordinal()] / 1e3
						/ (count[t.ordinal()] * 256.0), count[t.ordinal()]));
			}
		}
		return perType.toString();
	}

	/**
	 * {@link #CHUNKS} distinct chunks scattered deterministically over a square with a half-side of
	 * {@code half} around (cx, cz).
	 */
	private static long[][] chunks(long cx, long cz, long half, long salt) {
		Set<String> seen = new LinkedHashSet<>();
		long[][] out = new long[CHUNKS][];
		long h = salt;
		int i = 0;
		for (int attempt = 0; i < CHUNKS; attempt++) {
			h = h * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
			long x = Math.floorMod(h >>> 20, 2 * half) - half;
			h = h * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
			long z = Math.floorMod(h >>> 20, 2 * half) - half;
			long[] c = {Math.floorDiv(cx + x, 16) * 16, Math.floorDiv(cz + z, 16) * 16};
			// Repeats only when the area has too few chunks (does not happen for the areas of this test).
			if (seen.add(c[0] + "," + c[1]) || attempt > 100 * CHUNKS) {
				out[i++] = c;
			}
		}
		return out;
	}

	private static void run(LandscapeModel m, long[][] chunks) {
		for (long[] c : chunks) {
			runChunk(m, c);
		}
	}

	private static void runChunk(LandscapeModel m, long[] c) {
		double acc = 0;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				acc += m.sample(c[0] + x, c[1] + z).surface();
			}
		}
		sink += acc;
	}

	/** The same for the M1 copy; a separate method so that the JIT does not mix the profiles of both models in one call. */
	private static void runM1(pl.polishforests.worldgen.landscape.m1.LandscapeModel m, long[][] chunks) {
		for (long[] c : chunks) {
			double acc = 0;
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					acc += m.sample(c[0] + x, c[1] + z).surface();
				}
			}
			sink += acc;
		}
	}

	private static double median(double[] v) {
		double[] s = v.clone();
		Arrays.sort(s);
		return s.length % 2 == 1 ? s[s.length / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2;
	}

	private static double min(double[] v) {
		return Arrays.stream(v).min().orElse(Double.NaN);
	}

	private static String format(double[] v) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < v.length; i++) {
			sb.append(i == 0 ? "" : ", ").append(String.format(Locale.ROOT, "%.2f", v[i]));
		}
		return sb.append(']').toString();
	}
}
