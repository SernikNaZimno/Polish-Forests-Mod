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
 * {@code java.util.Random} with the seed {@value #TRANSECT_SEED}). Two measures:
 * <ul>
 * <li>A jump is an isolated step between two neighboring samples: |Δh| &gt; {@value #THRESHOLD} m and more than 3
 * times the difference of both neighboring steps, with all four samples dry (no water and no water level). Such steps
 * are the "cliffs" and straight scarps of the reported artifacts: valley floors and slopes cut by straight lines, kettle
 * basins with walls, region seams.</li>
 * <li>A valley-made step (review of step K4): the change of the surface over 1 m (two steps) beyond the change of the
 * terrain before valleys and lakes ({@code rawSurface}) over the same 1 m, |Δsurface| − |Δraw|, with all three
 * samples dry and outside standing water. The isolated jumps miss a wall spread over two or more samples (after K4b
 * walls of 10–50 m at 14–24 m per 1 m on the massif flanks), and the plain 1 m step |Δsurface| cannot tell them from
 * the steep domes of the gameplay massifs (step K2: 3.5 m per 1 m over 125–170 m with no valley). Decision D4: no
 * valley-made step above {@value #D4_STEP} m per 1 m in any window, as in
 * {@code RiverNetworkTest.mountainStreamSourcesHaveNoCliffs}. The largest plain 1 m step is printed.</li>
 * </ul>
 *
 * <p>The test prints every jump with its surroundings (valley floor or slope of order n, kind of standing water,
 * coast) and checks the limits of the window: the number of jumps, the largest jump, the number of jumps at standing
 * water (any of the four samples within 160 m of the shore of a standing water) and the largest valley-made step. The
 * jump limits stop the terrain from getting worse than the current state; each step of the fix tightens them towards
 * the goals in {@link Window#goal()} (design §3.3).
 *
 * <p>The windows are the eight windows of the design (from the review of artifacts), five windows on large Beskid
 * massifs (review of the design), which have a massif since step K2, and a perched tunnel valley lake (review of
 * K3, a check for step K5.2). Seed 20260927, region slider 1.0.
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
	/** Decision D4: largest valley-made step (|Δsurface| − |Δraw| over 1 m) on dry land in every window, in m. */
	static final double D4_STEP = 3.0;

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
	 * Result of a window scan: the jumps, the largest valley-made step and the largest plain 1 m step (dry land outside
	 * standing water), each with the position of the middle sample.
	 */
	record Scan(List<Jump> jumps, double maxExcess, double excessX, double excessZ, double maxStep, double stepX,
			double stepZ) {
	}

	/**
	 * Windows and limits. Limits of step K0: the state measured on the unchanged terrain (2026-10-03), so that the
	 * test passes and stops any step from making a window worse. Exception: {@code realistic_moraine}, where the
	 * known order dependence of the tunnel valley lake level gives 15 or 16 jumps; its limits cover both variants with
	 * a margin until K5.2 (largest jump 4.80 m, at the same lake, plus the largest level difference seen, 2 m: limit
	 * 6.9 m). Goals: design §3.3 and decision D4.
	 *
	 * <p>Step K3 (F1 + TE) tightened the counts to its measured state: gameplay_beskids 43 -> 41, gameplay_center
	 * 21 -> 19, gameplay_stream 43 -> 37, gameplay_moraine 20 -> 19, gameplay_massif_1660 93 -> 87 (largest jumps
	 * unchanged).
	 *
	 * <p>Step K4 with K4b (valley geometry, the projection on exact distance minima and the sweep cut,
	 * docs/m2/poprawka-geometrii.md) tightened every window to its measured state (count / largest, K3 -> K4):
	 * gameplay_beskids 41 / 36.39 -> 1 / 0.68 m, gameplay_center 19 -> 14 (all at standing water), gameplay_stream 37 /
	 * 13.64 -> 6 / 2.85 m (the seam of the 3 x 3 region blend at (5540, 2725), A16, step K6, and steep valley sides),
	 * gameplay_moraine 19 -> 14 (13 at standing water, one 0.37 m on a valley side), realistic_beskids 1 -> 0,
	 * realistic_moraine 15-16 -> 14 (all at standing water), the massif windows 42 / 22.02 -> 1 / 0.35 m, 87 / 69.51 -> 1
	 * / 0.33 m, 41 / 58.02 -> 0, 16 / 15.70 -> 1 / 0.47 m; realistic_lowland 2 / 1.50 m (a kettle bog, K5.4) and
	 * gameplay_tunnel_lake_3519 23 -> 25 jumps (the perched tunnel valley lake, K5.2) at standing water. The jumps left on
	 * valley sides (0.33-0.68 m) are bands about 0.4 m wide at 1.5-2 m per 1 m where the projection is steeper than the
	 * sweep cut (K4b). The largest valley-made step is now below 3 m per 1 m in every window (before K4b up to 22 m per
	 * 1 m on the massifs).
	 */
	static Stream<Window> windows() {
		LandscapeScale g = LandscapeScale.GAMEPLAY;
		LandscapeScale r = LandscapeScale.REALISTIC;
		String d4 = "; D4: no valley-made step > 3 m per 1 m (enforced since K4)";
		return Stream.of(
				new Window("gameplay_beskids", g, 27_609, 3_254, 3_000, 1, 0.69, 0, "<= 8, none at water" + d4),
				// Standing water (kettle bogs and ponds, tunnel valley lakes): K5.4 (A3) and K5.2 must bring it to the goal.
				new Window("gameplay_center", g, 0, 0, 10_000, 14, 20.31, 14,
						"<= 1 after A3c, 0 at water; without A3c <= 3, at water only (5761, -4758)" + d4),
				new Window("gameplay_stream", g, 3_890, 1_959, 2_000, 6, 2.86, 0, "<= 5 (the rest: A16 seam, K6)" + d4),
				new Window("gameplay_moraine", g, -1_074, -2_368, 3_000, 14, 67.16, 13, "<= 1, 0 at water" + d4),
				new Window("realistic_beskids", r, 154_834, 1_058_738, 30_000, 0, 0, 0, "<= 1, <= 1.1 m" + d4),
				// The kettle bog at (-92914, 80060): K5.4 (A3, basins without a wall).
				new Window("realistic_lowland", r, 0, 0, 100_000, 2, 1.51, 2, "0" + d4),
				// 15 or 16 jumps (tunnel valley lake level, see the class comment): the limits cover both until K5.2.
				new Window("realistic_moraine", r, -66_495, 21_873, 20_000, 16, 6.9, 16, "0" + d4),
				new Window("realistic_stream", r, 72_208, 1_009_510, 10_000, 0, 0, 0, "0" + d4),
				// Perched tunnel valley lake (review of K3): level 121 m held by a bank 1-2 px wide above a river valley
				// floor at 32-37 m (dry step about 86 m, a straight SW shore about 170 m long). Inherited from M1, moved by
				// K3 (valleyWeight = max lowers tunnelPresence). Measured after K3: 23 jumps, after K4 25, all at standing
				// water, largest 85.83 / 85.81 m; limits with a margin for the order-dependent lake level (K5.2). K5.2
				// (floorGap) must remove it.
				new Window("gameplay_tunnel_lake_3519", g, 3_519, -4_356, 300, 27, 88.0, 27,
						"0 (K5.2: the tunnel lake ends before the valley)" + d4),
				// Windows on large Beskid massifs (review of the design). K0 without the massifs: 46 / 22.32 m, 54 / 68.13
				// m, 59 / 28.48 m, 51 / 39.28 m, 0; after the review of K2: 42 / 22.02, 93 / 69.50, 41 / 58.01, 16 / 15.69
				// m, 0 (A2 jumps of the projection of short order 1 streams, decision D4); after K4 with K4b: 1 / 0.35, 1 / 0.33,
				// 0, 1 / 0.47 m, 0.
				new Window("gameplay_massif_spawn", g, 6_854, -33_757, 2_000, 1, 0.36, 0, "0" + d4),
				new Window("gameplay_massif_1660", g, 70_230, -30_250, 2_000, 1, 0.34, 0, "<= 54 (K0, without the massif)" + d4),
				new Window("gameplay_massif_1718", g, -217_490, 246_246, 2_000, 0, 0, 0, "max <= 28.48 m (K0)" + d4),
				new Window("gameplay_massif_1710", g, -41_536, 183_081, 2_000, 1, 0.48, 0, "0" + d4),
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

	/** Dry land outside standing water (the samples of a valley-made step). */
	static boolean dryLand(ColumnSample s) {
		return dry(s) && s.waters().standingWaterKind() == ColumnSample.StandingWaterKind.NONE;
	}

	/** All jumps in the window, in the order of transects and samples, and the largest 1 m steps. */
	static Scan scan(LandscapeModel m, Window win) {
		Random rnd = new Random(TRANSECT_SEED);
		double[][] transects = new double[TRANSECTS][];
		for (int t = 0; t < TRANSECTS; t++) {
			double x0 = win.cx() + (rnd.nextDouble() * 2 - 1) * win.radius();
			double z0 = win.cz() + (rnd.nextDouble() * 2 - 1) * win.radius();
			double ang = rnd.nextDouble() * Math.PI;
			transects[t] = new double[] {x0, z0, Math.cos(ang), Math.sin(ang)};
		}
		int steps = (int) (LENGTH / STEP);
		// Per transect: the jumps and {largest valley-made step, x, z, largest plain step, x, z}.
		List<Object[]> per = IntStream.range(0, TRANSECTS).parallel().mapToObj(t -> {
			double[] tr = transects[t];
			ColumnSample[] s = new ColumnSample[steps + 1];
			List<Jump> out = new ArrayList<>();
			double[] steep = {0, 0, 0, 0, 0, 0};
			for (int k = 0; k <= steps; k++) {
				s[k] = m.sample(tr[0] + tr[2] * k * STEP, tr[1] + tr[3] * k * STEP);
				if (k >= 2 && dryLand(s[k]) && dryLand(s[k - 1]) && dryLand(s[k - 2])) {
					double d = Math.abs(s[k].surface() - s[k - 2].surface());
					double e = d - Math.abs(s[k].terrain().rawSurface() - s[k - 2].terrain().rawSurface());
					double x = tr[0] + tr[2] * (k - 1) * STEP;
					double z = tr[1] + tr[3] * (k - 1) * STEP;
					if (e > steep[0]) {
						steep[0] = e;
						steep[1] = x;
						steep[2] = z;
					}
					if (d > steep[3]) {
						steep[3] = d;
						steep[4] = x;
						steep[5] = z;
					}
				}
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
			return new Object[] {out, steep};
		}).toList();
		List<Jump> all = new ArrayList<>();
		double[] best = {0, 0, 0, 0, 0, 0};
		for (Object[] p : per) {
			@SuppressWarnings("unchecked")
			List<Jump> j = (List<Jump>) p[0];
			all.addAll(j);
			double[] st = (double[]) p[1];
			if (st[0] > best[0]) {
				System.arraycopy(st, 0, best, 0, 3);
			}
			if (st[3] > best[3]) {
				System.arraycopy(st, 3, best, 3, 3);
			}
		}
		return new Scan(all, best[0], best[1], best[2], best[3], best[4], best[5]);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("windows")
	void surfaceHasNoIsolatedJumps(Window win) {
		LandscapeModel m = new LandscapeModel(SEED, win.scale(), 1.0);
		long t0 = System.nanoTime();
		Scan scan = scan(m, win);
		List<Jump> jumps = scan.jumps();
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
				+ "%d, max %.3f m, at standing water %d, by surroundings %s; largest valley-made step %.2f m per 1 m at "
				+ "(%.1f, %.1f), largest 1 m step %.2f m at (%.1f, %.1f) (%.1f s)%n  limits: <= %d jumps, max <= %.2f m, "
				+ "at water <= %d, valley-made step <= %.1f m; goal: %s%n%s", win.name(), win.scale().id(), win.cx(), win.cz(),
				win.radius(), (long) TRANSECTS * ((int) (LENGTH / STEP) + 1), THRESHOLD, STEP, jumps.size(), max, nearWater,
				classes, scan.maxExcess(), scan.excessX(), scan.excessZ(), scan.maxStep(), scan.stepX(), scan.stepZ(), seconds,
				win.maxJumps(), win.maxHeight(), win.maxNearWater(), D4_STEP, win.goal(), list);
		assertTrue(jumps.size() <= win.maxJumps(), win.name() + ": " + jumps.size() + " jumps, limit " + win.maxJumps()
				+ "\n" + list);
		assertTrue(max <= win.maxHeight(), String.format(Locale.ROOT, "%s: jump of %.2f m, limit %.2f m%n%s", win.name(),
				max, win.maxHeight(), list));
		assertTrue(nearWater <= win.maxNearWater(), win.name() + ": " + nearWater + " jumps at standing water, limit "
				+ win.maxNearWater() + "\n" + list);
		assertTrue(scan.maxExcess() <= D4_STEP, String.format(Locale.ROOT, "%s: valley-made step of %.2f m per 1 m at (%.1f, "
				+ "%.1f), limit %.1f m (decision D4)", win.name(), scan.maxExcess(), scan.excessX(), scan.excessZ(), D4_STEP));
	}

	/**
	 * Place of a cliff on the flank of a large massif found in the review of step K2 (grid scan, the transects of
	 * {@link #windows()} miss most of them): square of half-side {@code half} around (cx, cz) sampled every {@code step} m.
	 *
	 * @param maxPairs  limit of the dry neighbor pairs with |Δh| &gt; {@value #CLIFF} m (enforced)
	 * @param maxHeight limit of the largest |Δh| of a dry neighbor pair in m (enforced)
	 * @param before    the same measurement in K0 (without the massifs), in the first version of K2 and after the review
	 *                  of K2, printed
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
	 * Spots and limits: the state after step K4 with K4b (pairs, largest |Δh|); {@code before} gives K0 (without the
	 * massifs), the first version of K2 and the state after the review of K2. The review of K2 removed the box canyons
	 * and the slot (a segment ending in a sink lake near a massif bent back near its end, and the projection of points up
	 * to 2 km away fell onto the bend). K4b removed the fan and the blades (A2: jumps of the projection of short order 1
	 * segments at gameplay scale; the exact distance minima left 10 pairs up to 31.4 m at the start of the segment
	 * (91743, 169601) -> (91295, 169780), whose first part is bent around the points of the spot, and the sweep cut
	 * removed them, docs/m2/poprawka-geometrii.md, K4b): 0 pairs, largest 5.9 m and 10.6 m (steep valley sides on the
	 * dome, 2.5 m apart). The bank walls of the new sink lakes at the foot of the massifs remain ({@code applyLake}, step
	 * K5.4, A3: basins without a wall). Goal: no pair above {@value #CLIFF} m in any spot.
	 */
	static Stream<Spot> spots() {
		LandscapeScale g = LandscapeScale.GAMEPLAY;
		LandscapeScale r = LandscapeScale.REALISTIC;
		return Stream.of(
				// Box canyon of 378 m behind the source of (145867, -1474074) -> (146745, -1473298) (first version of K2).
				new Spot("realistic_box_canyon_1698", r, 144_478, -1_474_422, 1_000, 5, 0, 5.7,
						"K0 0, 6.9 m; K2 334, 377.9 m; K2 review 0, 5.6 m"),
				// 151 m behind the source of (150591, -1525742), at the new sink lake east of the 1723 m massif.
				new Spot("realistic_sink_lake_1723", r, 150_900, -1_525_100, 1_100, 5, 230, 46.0,
						"K0 0, 7.8 m; K2 313, 151.1 m; K2 review 231, 45.9 m"),
				// 100 m wall and a star of wedges around the sink lake south-east of the massif (515882, -1401302).
				new Spot("realistic_sink_lake_1659", r, 518_000, -1_404_342, 1_000, 5, 148, 97.4,
						"K0 0, 6.3 m; K2 161, 100.6 m; K2 review 148, 97.3 m"),
				// The 1 km deep slot canyon in the dome of the massif (258824, -1539366).
				new Spot("realistic_slot_1700", r, 260_024, -1_538_100, 1_000, 5, 0, 10.3,
						"K0 0, 6.2 m; K2 1915, 1021.6 m; K2 review 0, 10.2 m"),
				// Blades of 84 m on the flank of the gameplay massif (90847, 171052).
				new Spot("gameplay_blades_1707", g, 91_727, 169_377, 500, 2.5, 0, 6.0,
						"K0 0, 12.9 m; K2 321, 83.1 m; K2 review 320, 83.1 m; K4 exact minima 10, 31.4 m"),
				// Fan on the north-west flank of the gameplay massif (129584, 76912).
				new Spot("gameplay_fan_1673", g, 128_900, 76_850, 500, 2.5, 0, 10.7,
						"K0 4, 27.8 m; K2 861, 89.8 m; K2 review 535, 83.1 m; K4 exact minima 0, 10.6 m"));
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
				+ "%.1f m at (%.1f, %.1f); limits %d / %.1f m; earlier: %s; goal: none (lake banks: K5.4)%n", spot.name(), spot.scale().id(),
				spot.cx(), spot.cz(), spot.half(), spot.step(), (long) r[0], CLIFF, r[1], r[2], r[3], spot.maxPairs(),
				spot.maxHeight(), spot.before());
		assertTrue(r[0] <= spot.maxPairs(), spot.name() + ": " + (long) r[0] + " cliff pairs, limit " + spot.maxPairs());
		assertTrue(r[1] <= spot.maxHeight(), String.format(Locale.ROOT, "%s: cliff of %.1f m at (%.1f, %.1f), limit %.1f m",
				spot.name(), r[1], r[2], r[3], spot.maxHeight()));
	}
}
