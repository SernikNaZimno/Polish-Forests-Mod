package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Continuity of the ground surface on dry land (terrain geometry fix, docs/m2/poprawka-geometrii.md, §3.3 of the
 * design). In each window: {@value #TRANSECTS} random straight transects of {@value #LENGTH} m sampled every
 * {@value #STEP} m (start points uniform in the square of the given radius around the center, random direction,
 * {@code java.util.Random} with the seed {@value #TRANSECT_SEED}). A jump is an isolated step between two
 * neighboring samples: |Δh| &gt; {@value #THRESHOLD} m and more than 3 times the difference of both neighboring
 * steps, with all four samples dry (no water and no water level). Such steps are the "cliffs" and straight scarps of
 * the reported artifacts: valley floors and slopes cut by straight lines, kettle basins with walls, region seams.
 *
 * <p>The test prints every jump with its surroundings (valley floor or slope of order n, kind of standing water,
 * coast) and checks the limits of the window: the number of jumps, the largest jump and the number of jumps at
 * standing water (any of the four samples within 160 m of the shore of a standing water). The limits only stop the terrain from getting worse than the current state; each step of the fix
 * tightens them towards the goals in {@link Window#goal()} (design §3.3 and decision D4: no jump larger than 3 m in
 * any window, as in {@code RiverNetworkTest.mountainStreamSourcesHaveNoCliffs}).
 *
 * <p>The windows are the eight windows of the design (from the review of artifacts) and five windows on large Beskid
 * massifs (review of the design), which have a massif since step K2. Seed 20260927, region slider 1.0.
 *
 * <p>Transects are computed in parallel, each sampled in order. The scan itself is deterministic, but the model is not
 * yet: the water level of a tunnel valley lake depends on the order in which columns are sampled (known issue
 * inherited from M1, {@link TerrainDeterminismTest}, fixed in step K5.2). In {@code realistic_moraine} the lake at
 * (−82818, 10254) then gives 15 or 16 jumps (−2.51 m, or −2.18 m and +1.82 m) from run to run, and its limits keep a
 * margin of more than the possible 1–2 m level change until K5.2. The other windows gave the same result in repeated
 * runs.
 */
class SurfaceContinuityTest {
	static final long SEED = 20260927L;
	static final int TRANSECTS = 150;
	static final double LENGTH = 1_000;
	static final double STEP = 0.5;
	static final double THRESHOLD = 0.3;
	static final long TRANSECT_SEED = 11;

	/**
	 * Test window: square of half-side {@code radius} around (cx, cz).
	 *
	 * @param maxJumps     limit of the number of jumps (enforced)
	 * @param maxHeight    limit of the largest |Δh| of a jump in meters (enforced)
	 * @param maxNearWater limit of the number of jumps at standing water (enforced)
	 * @param goal         acceptance criterion of the design for the end of the fix (printed, not enforced yet)
	 */
	record Window(String name, LandscapeScale scale, double cx, double cz, double radius, int maxJumps, double maxHeight,
			int maxNearWater, String goal) {
		@Override
		public String toString() {
			return name;
		}
	}

	/** Jump between the samples k − 2 and k − 1 of a transect, at the position of the sample k − 1. */
	record Jump(double x, double z, double dh, String context, boolean nearWater) {
	}

	/**
	 * Windows and limits. Limits of step K0: the state measured on the unchanged terrain (2026-10-03), so that the
	 * test passes and stops any step from making a window worse. Exception: {@code realistic_moraine}, where the
	 * known order dependence of the tunnel valley lake level gives 15 or 16 jumps; its limits cover both variants with
	 * a margin until K5.2 (largest jump 4.80 m, at the same lake, plus the largest level difference seen, 2 m: limit
	 * 6.9 m). Goals: design §3.3 and decision D4 (step K4b: no jump larger than 3 m in any window, the massif windows
	 * included).
	 */
	static Stream<Window> windows() {
		LandscapeScale g = LandscapeScale.GAMEPLAY;
		LandscapeScale r = LandscapeScale.REALISTIC;
		String d4 = "; D4 (K4b): no jump > 3 m";
		String massif = "not more than before K2 (K0 limit), " + d4.substring(2);
		return Stream.of(
				new Window("gameplay_beskids", g, 27_609, 3_254, 3_000, 43, 36.39, 0, "<= 8, none at water" + d4),
				new Window("gameplay_center", g, 0, 0, 10_000, 21, 20.31, 14,
						"<= 1 after A3c, 0 at water; without A3c <= 3, at water only (5761, -4758)" + d4),
				new Window("gameplay_stream", g, 3_890, 1_959, 2_000, 43, 13.64, 0, "<= 5" + d4),
				new Window("gameplay_moraine", g, -1_074, -2_368, 3_000, 20, 67.31, 16, "<= 1, 0 at water" + d4),
				new Window("realistic_beskids", r, 154_834, 1_058_738, 30_000, 1, 1.07, 0, "<= 1, <= 1.1 m"),
				new Window("realistic_lowland", r, 0, 0, 100_000, 2, 1.51, 2, "0"),
				// 15 or 16 jumps (tunnel valley lake level, see the class comment): the limits cover both until K5.2.
				new Window("realistic_moraine", r, -66_495, 21_873, 20_000, 16, 6.9, 16, "0"),
				new Window("realistic_stream", r, 72_208, 1_009_510, 10_000, 0, 0, 0, "0"),
				// Windows on large Beskid massifs (review of the design), limits measured after the review of K2 (with
				// the massifs). K0 without the massifs: 46 / 22.32 m, 54 / 68.13 m, 59 / 28.48 m, 51 / 39.28 m, 0; first
				// version of K2: 46 / 22.02, 79 / 69.50, 44 / 19.54, 15 / 15.69, 3 / 68.52 m. The review removed the
				// jumps of the realistic window (a segment bent back at a sink lake, RiverNetwork.build) and lifts the
				// foothills cells in the reach of a massif as well (the dome no longer ends in a wall at the region cell
				// boundary). The deeper valleys in those foothills cells give more A2 jumps of short order 1 streams in
				// two gameplay windows: 1660 m 54 (K0) -> 93, 1718 m largest 28.48 (K0) -> 58.01 m at (-216918,
				// 247864). Removing them is decision D4 (step K4b), docs/m2/poprawka-geometrii.md, K2.
				new Window("gameplay_massif_spawn", g, 6_854, -33_757, 2_000, 42, 22.02, 0, massif),
				new Window("gameplay_massif_1660", g, 70_230, -30_250, 2_000, 93, 69.51, 0, "<= 54 (K0, without the massif)" + d4),
				new Window("gameplay_massif_1718", g, -217_490, 246_246, 2_000, 41, 58.02, 0, "max <= 28.48 m (K0)" + d4),
				new Window("gameplay_massif_1710", g, -41_536, 183_081, 2_000, 16, 15.70, 0, massif),
				new Window("realistic_massif_1723", r, 147_582, -1_525_292, 8_000, 0, 0, 0, "0" + d4));
	}

	static boolean dry(ColumnSample s) {
		return !s.hasWater() && s.waterLevel() == ColumnSample.NO_WATER;
	}

	/** Standing water whose belt contains the sample (within 160 m of its shore, as in SkokiLista), or null. */
	static ColumnSample.StandingWaterKind standingWater(ColumnSample s) {
		ColumnSample.Waters w = s.waters();
		return w.standingWaterKind() != ColumnSample.StandingWaterKind.NONE && w.s() < 160 ? w.standingWaterKind() : null;
	}

	/**
	 * Surroundings of a jump, as in the prototype tool (SkokiLista): standing water when any of the four samples of the
	 * jump lies in its belt (a basin wall ends where the belt ends, so the sample at the jump itself may already be
	 * outside), otherwise the coast (within 3 km·meso of the shoreline), the valley of the sample at the jump, or the
	 * landscape type. The first sample is the one at the jump.
	 */
	static String context(LandscapeModel m, ColumnSample... four) {
		for (ColumnSample s : four) {
			ColumnSample.StandingWaterKind k = standingWater(s);
			if (k != null) {
				return "standing water " + k;
			}
		}
		ColumnSample s = four[0];
		ColumnSample.Waters w = s.waters();
		if (s.terrain().coastD() < 3_000 * m.scale().meso()) {
			return "coast";
		}
		if (w.streamOrder() > 0) {
			return "valley order " + w.streamOrder() + (w.inFloor() ? " floor" : " slope");
		}
		return "other " + s.type();
	}

	/** All jumps in the window, in the order of transects and samples. */
	static List<Jump> scan(LandscapeModel m, Window win) {
		Random rnd = new Random(TRANSECT_SEED);
		double[][] transects = new double[TRANSECTS][];
		for (int t = 0; t < TRANSECTS; t++) {
			double x0 = win.cx() + (rnd.nextDouble() * 2 - 1) * win.radius();
			double z0 = win.cz() + (rnd.nextDouble() * 2 - 1) * win.radius();
			double ang = rnd.nextDouble() * Math.PI;
			transects[t] = new double[] {x0, z0, Math.cos(ang), Math.sin(ang)};
		}
		int steps = (int) (LENGTH / STEP);
		List<List<Jump>> per = IntStream.range(0, TRANSECTS).parallel().mapToObj(t -> {
			double[] tr = transects[t];
			ColumnSample[] s = new ColumnSample[steps + 1];
			List<Jump> out = new ArrayList<>();
			for (int k = 0; k <= steps; k++) {
				s[k] = m.sample(tr[0] + tr[2] * k * STEP, tr[1] + tr[3] * k * STEP);
				if (k < 3 || !dry(s[k]) || !dry(s[k - 1]) || !dry(s[k - 2]) || !dry(s[k - 3])) {
					continue;
				}
				double d = s[k - 1].surface() - s[k - 2].surface();
				double dl = s[k - 2].surface() - s[k - 3].surface();
				double dr = s[k].surface() - s[k - 1].surface();
				if (Math.abs(d) > THRESHOLD && Math.abs(d) > 3 * Math.max(Math.abs(dl), Math.abs(dr))) {
					String c = context(m, s[k - 1], s[k - 2], s[k], s[k - 3]);
					out.add(new Jump(tr[0] + tr[2] * (k - 1) * STEP, tr[1] + tr[3] * (k - 1) * STEP, d, c,
							c.startsWith("standing water")));
				}
			}
			return out;
		}).toList();
		List<Jump> all = new ArrayList<>();
		per.forEach(all::addAll);
		return all;
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("windows")
	void surfaceHasNoIsolatedJumps(Window win) {
		LandscapeModel m = new LandscapeModel(SEED, win.scale(), 1.0);
		long t0 = System.nanoTime();
		List<Jump> jumps = scan(m, win);
		double seconds = (System.nanoTime() - t0) / 1e9;
		double max = 0;
		int nearWater = 0;
		Map<String, Integer> classes = new TreeMap<>();
		StringBuilder list = new StringBuilder();
		for (Jump j : jumps) {
			max = Math.max(max, Math.abs(j.dh()));
			nearWater += j.nearWater() ? 1 : 0;
			classes.merge(j.context(), 1, Integer::sum);
			list.append(String.format(Locale.ROOT, "  jump (%.1f, %.1f) %+.2f m %s%n", j.x(), j.z(), j.dh(), j.context()));
		}
		System.out.printf(Locale.ROOT, "[continuity] %s (%s, (%.0f, %.0f), r %.0f m): %d samples, jumps > %.1f m per %.1f m: "
				+ "%d, max %.3f m, at standing water %d, by surroundings %s (%.1f s)%n  limits: <= %d jumps, max <= %.2f m, "
				+ "at water <= %d; goal: %s%n%s", win.name(), win.scale().id(), win.cx(), win.cz(), win.radius(),
				(long) TRANSECTS * ((int) (LENGTH / STEP) + 1), THRESHOLD, STEP, jumps.size(), max, nearWater, classes, seconds,
				win.maxJumps(), win.maxHeight(), win.maxNearWater(), win.goal(), list);
		assertTrue(jumps.size() <= win.maxJumps(), win.name() + ": " + jumps.size() + " jumps, limit " + win.maxJumps()
				+ "\n" + list);
		assertTrue(max <= win.maxHeight(), String.format(Locale.ROOT, "%s: jump of %.2f m, limit %.2f m%n%s", win.name(),
				max, win.maxHeight(), list));
		assertTrue(nearWater <= win.maxNearWater(), win.name() + ": " + nearWater + " jumps at standing water, limit "
				+ win.maxNearWater() + "\n" + list);
	}

	/**
	 * Place of a cliff on the flank of a large massif found in the review of step K2 (grid scan, the transects of
	 * {@link #windows()} miss most of them): square of half-side {@code half} around (cx, cz) sampled every {@code step} m.
	 *
	 * @param maxPairs  limit of the dry neighbor pairs with |Δh| &gt; {@value #CLIFF} m (enforced)
	 * @param maxHeight limit of the largest |Δh| of a dry neighbor pair in m (enforced)
	 * @param before    the same measurement in K0 (without the massifs) and in the first version of K2, printed
	 */
	record Spot(String name, LandscapeScale scale, double cx, double cz, double half, double step, int maxPairs,
			double maxHeight, String before) {
		@Override
		public String toString() {
			return name;
		}
	}

	/** Height difference of a dry neighbor pair counted as a cliff by {@link #massifSpotsHaveNoCliffs}, in m. */
	static final double CLIFF = 25;

	/**
	 * Spots and limits: the state after the review of K2 (pairs, largest |Δh|); {@code before} gives K0 (without the
	 * massifs) and the first version of K2. The review removed the box canyons and the slot (a segment ending in a sink
	 * lake near a massif bent back near its end, and the projection of points up to 2 km away fell onto the bend). What
	 * remains are the bank walls of the new sink lakes at the foot of the massifs ({@code applyLake}, step K5.4, A3:
	 * basins without a wall) and A2 jumps of the projection of short order 1 segments at gameplay scale (step K4b,
	 * decision D4). Goal: no step larger than 3 m per 1 m, i.e. no pair above {@value #CLIFF} m in any spot.
	 */
	static Stream<Spot> spots() {
		LandscapeScale g = LandscapeScale.GAMEPLAY;
		LandscapeScale r = LandscapeScale.REALISTIC;
		return Stream.of(
				// Box canyon of 378 m behind the source of (145867, -1474074) -> (146745, -1473298) (first version of K2).
				new Spot("realistic_box_canyon_1698", r, 144_478, -1_474_422, 1_000, 5, 0, 5.7, "K0 0, 6.9 m; K2 334, 377.9 m"),
				// 151 m behind the source of (150591, -1525742), at the new sink lake east of the 1723 m massif.
				new Spot("realistic_sink_lake_1723", r, 150_900, -1_525_100, 1_100, 5, 231, 46.0, "K0 0, 7.8 m; K2 313, 151.1 m"),
				// 100 m wall and a star of wedges around the sink lake south-east of the massif (515882, -1401302).
				new Spot("realistic_sink_lake_1659", r, 518_000, -1_404_342, 1_000, 5, 148, 97.4, "K0 0, 6.3 m; K2 161, 100.6 m"),
				// The 1 km deep slot canyon in the dome of the massif (258824, -1539366).
				new Spot("realistic_slot_1700", r, 260_024, -1_538_100, 1_000, 5, 0, 10.3, "K0 0, 6.2 m; K2 1915, 1021.6 m"),
				// Blades of 84 m on the flank of the gameplay massif (90847, 171052).
				new Spot("gameplay_blades_1707", g, 91_727, 169_377, 500, 2.5, 320, 83.2, "K0 0, 12.9 m; K2 321, 83.1 m"),
				// Fan on the north-west flank of the gameplay massif (129584, 76912).
				new Spot("gameplay_fan_1673", g, 128_900, 76_850, 500, 2.5, 535, 83.2, "K0 4, 27.8 m; K2 861, 89.8 m"));
	}

	/** {pairs above {@link #CLIFF}, largest |Δh|, x, z of the largest} of the dry neighbor pairs (x and z) in the spot. */
	static double[] spotScan(LandscapeModel m, Spot spot) {
		int n = (int) Math.round(2 * spot.half() / spot.step());
		double x0 = spot.cx() - spot.half();
		double z0 = spot.cz() - spot.half();
		float[][] h = new float[n][];
		boolean[][] wet = new boolean[n][];
		IntStream.range(0, n).parallel().forEach(j -> {
			float[] row = new float[n];
			boolean[] w = new boolean[n];
			for (int i = 0; i < n; i++) {
				ColumnSample s = m.sample(x0 + i * spot.step(), z0 + j * spot.step());
				row[i] = (float) s.surface();
				w[i] = !dry(s);
			}
			h[j] = row;
			wet[j] = w;
		});
		long pairs = 0;
		double max = 0;
		double mx = 0;
		double mz = 0;
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				if (wet[j][i]) {
					continue;
				}
				double d = 0;
				if (i + 1 < n && !wet[j][i + 1]) {
					d = Math.max(d, Math.abs(h[j][i + 1] - h[j][i]));
				}
				if (j + 1 < n && !wet[j + 1][i]) {
					d = Math.max(d, Math.abs(h[j + 1][i] - h[j][i]));
				}
				pairs += d > CLIFF ? 1 : 0;
				if (d > max) {
					max = d;
					mx = x0 + i * spot.step();
					mz = z0 + j * spot.step();
				}
			}
		}
		return new double[] {pairs, max, mx, mz};
	}

	/**
	 * Cliffs on the flanks of the large massifs found in the review of step K2 (box canyons behind stream sources, wedges
	 * around new sink lakes, the slot canyon in a dome, A2 blades and fans at gameplay scale): dry neighbor pairs with
	 * |Δh| &gt; {@value #CLIFF} m on a grid every 5 m (realistic) or 2.5 m (gameplay).
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("spots")
	void massifSpotsHaveNoCliffs(Spot spot) {
		LandscapeModel m = new LandscapeModel(SEED, spot.scale(), 1.0);
		double[] r = spotScan(m, spot);
		System.out.printf(Locale.ROOT, "[cliffs] %s (%s, (%.0f, %.0f), half-side %.0f m, every %.1f m): %d pairs > %.0f m, largest "
				+ "%.1f m at (%.1f, %.1f); limits %d / %.1f m; earlier: %s; goal: none (A2: K4b, lake banks: K5.4)%n", spot.name(), spot.scale().id(),
				spot.cx(), spot.cz(), spot.half(), spot.step(), (long) r[0], CLIFF, r[1], r[2], r[3], spot.maxPairs(),
				spot.maxHeight(), spot.before());
		assertTrue(r[0] <= spot.maxPairs(), spot.name() + ": " + (long) r[0] + " cliff pairs, limit " + spot.maxPairs());
		assertTrue(r[1] <= spot.maxHeight(), String.format(Locale.ROOT, "%s: cliff of %.1f m at (%.1f, %.1f), limit %.1f m",
				spot.name(), r[1], r[2], r[3], spot.maxHeight()));
	}
}
