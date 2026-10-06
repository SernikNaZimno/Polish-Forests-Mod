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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.polishforests.worldgen.chunk.VerticalScale;

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
 * {@code MountainStreamSourcesTest.mountainStreamSourcesHaveNoCliffs}. The largest plain 1 m step is printed.</li>
 * <li>A step in blocks (decision D4b, step K4c): the plain change of the surface over 1 m converted to blocks by the
 * vertical scale of the window ({@link VerticalScale}), at most {@value #D4B_BLOCKS} blocks per block on dry land
 * outside standing water and outside the coastal belt (within 3 km·meso of the shoreline). The steep domes of the
 * gameplay massifs (about 5 m per 1 m, about 1.2 blocks per block) are not cliffs in the game; a step above 2 blocks per
 * block is. The coastal belt is printed separately: the seaward wall of a cliff lies a few meters above the sea, where
 * the gameplay scale maps 1 m to up to 1.9 blocks, so its 2.5 m per 1 m make up to 2.4 blocks per block (until step K5b
 * the whole coast was a flat strip 13–24 m high with such a wall; since D5 only the cliffs, about a fifth of it).</li>
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
 * <p>Transects are computed in parallel, each sampled in order. The scan and the model are deterministic: until step
 * K5.2 the water level of a tunnel valley lake depended on the order in which columns are sampled (an issue inherited
 * from M1, {@link TerrainDeterminismTest}), and {@code realistic_moraine} gave 15 or 16 jumps from run to run.
 */
@Tag("slow")
class SurfaceContinuityTest {
	static final long SEED = 20260927L;
	static final int TRANSECTS = 150;
	static final double LENGTH = 1_000;
	static final double STEP = 0.5;
	static final double THRESHOLD = 0.3;
	static final long TRANSECT_SEED = 11;
	/** Decision D4: largest valley-made step (|Δsurface| − |Δraw| over 1 m) on dry land in every window, in m. */
	static final double D4_STEP = 3.0;
	/** Decision D4b: largest plain step in blocks per block (1 m) on dry land outside the coastal belt, both scales. */
	static final double D4B_BLOCKS = 2.0;

	/**
	 * Test window: square of half-side {@code radius} around (cx, cz).
	 *
	 * @param maxJumps     limit of the number of jumps (enforced)
	 * @param maxHeight    limit of the largest |Δh| of a jump in meters (enforced)
	 * @param maxNearWater limit of the number of jumps at standing water (enforced)
	 * @param maxValley    limit of the largest valley-made step in m per 1 m (enforced): {@link #D4_STEP}, or the measured
	 *                     state where ties of the arms of the projection are left (decision D4a, step K4c)
	 * @param goal         acceptance criterion of the design for the end of the fix (printed, not enforced yet)
	 */
	record Window(String name, LandscapeScale scale, double cx, double cz, double radius, int maxJumps, double maxHeight,
			int maxNearWater, double maxValley, String goal) {
		@Override
		public String toString() {
			return name;
		}
	}

	/** Jump between the samples k − 2 and k − 1 of a transect, at the position of the sample k − 1. */
	record Jump(double x, double z, double dh, String context, boolean nearWater) {
	}

	/**
	 * Result of a window scan: the jumps, the largest valley-made step, the largest plain 1 m step (dry land outside
	 * standing water), the largest plain step in blocks per block outside the coastal belt and inside it, each with the
	 * position of the middle sample.
	 */
	record Scan(List<Jump> jumps, double maxExcess, double excessX, double excessZ, double maxStep, double stepX,
			double stepZ, double maxBlocks, double blocksX, double blocksZ, double coastBlocks, double coastX,
			double coastZ) {
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
	 * 13.64 -> 6 / 2.85 m, gameplay_moraine 19 -> 14, realistic_beskids 1 -> 0, realistic_moraine 15-16 -> 14 (all at
	 * standing water), the massif windows 42 / 22.02 -> 1 / 0.35 m, 87 / 69.51 -> 1 / 0.33 m, 41 / 58.02 -> 0, 16 / 15.70
	 * -> 1 / 0.47 m; realistic_lowland 2 / 1.50 m (a kettle bog, K5.4) and gameplay_tunnel_lake_3519 23 -> 25 jumps (the
	 * perched tunnel valley lake, K5.2) at standing water.
	 *
	 * <p>Step K4c (decisions D4a and D4b): the sweep cut wins only near ties of the arms of the projection, so the valley
	 * sides keep the shape of the projection elsewhere. Measured state (count / largest jump, K4b -> K4c): gameplay_beskids
	 * 1 / 0.68 -> 0, gameplay_stream 6 / 2.85 -> 6 / 3.02 m (the seam of the 3 x 3 region blend at (5540, 2725), A16, step
	 * K6, which the K4b sweep cut had softened), gameplay_moraine 14 -> 13 (all at standing water), gameplay_massif_spawn
	 * 1 / 0.35 -> 0, gameplay_massif_1660 1 / 0.33 -> 3 / 0.58 m (narrow bands on the sides of order 1 valleys where the
	 * projection itself is steep), the other windows unchanged. The valley-made step stays at most 3 m per 1 m except in
	 * four gameplay windows with the ties left by decision D4a (between two well conditioned arms of a short order 1
	 * segment hundreds of meters away, where the sweep cut takes the deeper arm, and near the ends of short bent segments):
	 * gameplay_beskids 4.89, gameplay_massif_spawn 5.10, gameplay_massif_1660 4.78, gameplay_massif_1718 4.71 m per 1 m
	 * (K4b: below 2.5 m per 1 m, with the valley sides reshaped far from the ties). Decision D4b: in every window at most
	 * 2 blocks per block outside the coastal belt (largest 1.54, gameplay_massif_1660; in the coastal belt up to 2.45 at
	 * the coastal wall in gameplay_center, step K5b).
	 *
	 * <p>Round 1 of the review of K4c: the sweep cut is evaluated everywhere (no skip at a "clear" arm) and its maximum is
	 * taken also at the arms of the projection and at the extrema of f, so the fields of ribs at ties are gone. Measured
	 * state: gameplay_beskids valley-made step 4.89 -> 2.40 m per 1 m (back to the limit D4_STEP), gameplay_massif_spawn
	 * 5.10 -> 3.84, gameplay_massif_1660 4.78 -> 3.14 with 3 / 0.58 m -> 2 / 1.14 m jumps (both on creases where two
	 * cross-sections tie, slope 1.5 -> 3.7 m per 1 m without a step: (69755, -29217) and (70079, -27910)),
	 * gameplay_massif_1718 4.71 -> 2.96 (back to D4_STEP); the other windows unchanged. Largest step in blocks 1.55
	 * (gameplay_massif_spawn). The dense grids of {@link #denseGridHasNoCliffs} check the mountain windows in full.
	 *
	 * <p>Step K5 (standing waters: oxbow crescents K5.1, tunnel valley lakes ending before valleys and with a canonical
	 * level K5.2, kettle shores K5.3, basins without a wall and the kettle level after the valleys K5.4 with A3c, lobed
	 * sink lake shores K5.5; K5b: the low dune coast D5 and the lagoon D2): no jump at standing water in any window, so the
	 * goals of the design §3.3 for the windows with standing water are met (gameplay_center 14 -> 0, also without the
	 * kettle bog at (5761, −4758); gameplay_moraine 13 -> 0; realistic_lowland 2 -> 0; realistic_moraine 15–16 -> 0;
	 * gameplay_tunnel_lake_3519 25 -> 0) and their limits are 0. The other windows are unchanged. In the coastal belt the
	 * seaward wall of a cliff (D5: about a fifth of the coast) keeps 2.5 m per 1 m, up to 2.40 blocks per block at gameplay
	 * scale (gameplay_center), a cliff, not a flat strip with a wall along the whole coast as before.
	 *
	 * <p>Step K6 (A16, the 5 × 5 window of the region blend): the seam of the 3 × 3 window at (5540, 2725) is gone, so
	 * gameplay_stream 6 / 3.03 m -> 0 (goal of the design: at most 5). The other windows unchanged.
	 */
	static Stream<Window> windows() {
		LandscapeScale g = LandscapeScale.GAMEPLAY;
		LandscapeScale r = LandscapeScale.REALISTIC;
		String d4 = "; D4: no valley-made step > 3 m per 1 m (enforced since K4, the ties of D4a excepted); D4b: <= 2 blocks"
				+ " per block";
		double v = D4_STEP;
		return Stream.of(
				new Window("gameplay_beskids", g, 27_609, 3_254, 3_000, 0, 0, 0, v, "<= 8, none at water" + d4),
				// Standing water: 14 jumps at kettle bogs and ponds and tunnel valley lakes until K4c, 0 since K5 (goal met).
				new Window("gameplay_center", g, 0, 0, 10_000, 0, 0, 0, v,
						"<= 1 after A3c, 0 at water; without A3c <= 3, at water only (5761, -4758)" + d4),
				new Window("gameplay_stream", g, 3_890, 1_959, 2_000, 0, 0, 0, v, "<= 5 (K6: 0, the A16 seam is gone)" + d4),
				new Window("gameplay_moraine", g, -1_074, -2_368, 3_000, 0, 0, 0, v, "<= 1, 0 at water" + d4),
				new Window("realistic_beskids", r, 154_834, 1_058_738, 30_000, 0, 0, 0, v, "<= 1, <= 1.1 m" + d4),
				// The kettle bog at (-92914, 80060) until K4c; 0 since K5.4 (A3, basins without a wall).
				new Window("realistic_lowland", r, 0, 0, 100_000, 0, 0, 0, v, "0" + d4),
				// 15 or 16 jumps at tunnel valley lakes and kettles until K4c (14 after K4); 0 since K5.
				new Window("realistic_moraine", r, -66_495, 21_873, 20_000, 0, 0, 0, v, "0" + d4),
				new Window("realistic_stream", r, 72_208, 1_009_510, 10_000, 0, 0, 0, v, "0" + d4),
				// Perched tunnel valley lake (review of K3): level 121 m held by a bank 1-2 px wide above a river valley
				// floor at 32-37 m (dry step about 86 m, a straight SW shore about 170 m long). Inherited from M1, moved by
				// K3 (valleyWeight = max lowers tunnelPresence). Measured after K3: 23 jumps, after K4 25, all at standing
				// water, largest 85.83 / 85.81 m. K5.2 (the lake ends before the valley, floorGap) removed it: 0 jumps.
				new Window("gameplay_tunnel_lake_3519", g, 3_519, -4_356, 300, 0, 0, 0, v,
						"0 (K5.2: the tunnel lake ends before the valley)" + d4),
				// Windows on large Beskid massifs (review of the design). K0 without the massifs: 46 / 22.32 m, 54 / 68.13
				// m, 59 / 28.48 m, 51 / 39.28 m, 0; after the review of K2: 42 / 22.02, 93 / 69.50, 41 / 58.01, 16 / 15.69
				// m, 0 (A2 jumps of the projection of short order 1 streams, decision D4); after K4 with K4b: 1 / 0.35, 1 / 0.33,
				// 0, 1 / 0.47 m, 0; after K4c: 0, 3 / 0.58 m, 0, 1 / 0.47 m, 0.
				new Window("gameplay_massif_spawn", g, 6_854, -33_757, 2_000, 0, 0, 0, 3.9, "0" + d4),
				new Window("gameplay_massif_1660", g, 70_230, -30_250, 2_000, 3, 1.15, 0, 3.2,
						"<= 54 (K0, without the massif)" + d4),
				new Window("gameplay_massif_1718", g, -217_490, 246_246, 2_000, 0, 0, 0, v, "max <= 28.48 m (K0)" + d4),
				new Window("gameplay_massif_1710", g, -41_536, 183_081, 2_000, 1, 0.48, 0, v, "0" + d4),
				new Window("realistic_massif_1723", r, 147_582, -1_525_292, 8_000, 0, 0, 0, v, "0" + d4),
				// Review of K5 (D2): lagoons behind a low shore, with their ends along the shore. The first version of D2
				// left the basin at (1 − bowl) · terrain where its depth reached zero, a step back to the terrain of up to
				// 6 m on 1 m (5–7 blocks per block) along the edge and at the ends of a lagoon. After round 1: no jump at the
				// lagoons; the jumps left (2 / 0.99 m and 7 / 0.93 m) are the banks of river mouths raised to 1 m next to a
				// beach at the sea level (M1).
				new Window("realistic_lagoon", r, -220_000, -155_000, 6_000, 2, 1.0, 0, v, "0 at the lagoon (D2)" + d4),
				new Window("gameplay_lagoon", g, 450, 11_000, 1_500, 7, 0.94, 0, v, "0 at the lagoon (D2)" + d4));
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
		VerticalScale vs = win.scale() == LandscapeScale.GAMEPLAY ? VerticalScale.GAMEPLAY : VerticalScale.REAL;
		double coastBelt = 3_000 * m.scale().meso();
		// Per transect: the jumps and {largest valley-made step, x, z, largest plain step, x, z, largest step in blocks
		// outside the coastal belt, x, z, inside it, x, z}.
		List<Object[]> per = IntStream.range(0, TRANSECTS).parallel().mapToObj(t -> {
			double[] tr = transects[t];
			ColumnSample[] s = new ColumnSample[steps + 1];
			List<Jump> out = new ArrayList<>();
			double[] steep = new double[12];
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
					double bl = Math.abs(vs.blocksForMeters(s[k].surface()) - vs.blocksForMeters(s[k - 2].surface()));
					int q = Math.min(s[k].terrain().coastD(), s[k - 2].terrain().coastD()) < coastBelt ? 9 : 6;
					if (bl > steep[q]) {
						steep[q] = bl;
						steep[q + 1] = x;
						steep[q + 2] = z;
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
		double[] best = new double[12];
		for (Object[] p : per) {
			@SuppressWarnings("unchecked")
			List<Jump> j = (List<Jump>) p[0];
			all.addAll(j);
			double[] st = (double[]) p[1];
			for (int q = 0; q < 12; q += 3) {
				if (st[q] > best[q]) {
					System.arraycopy(st, q, best, q, 3);
				}
			}
		}
		return new Scan(all, best[0], best[1], best[2], best[3], best[4], best[5], best[6], best[7], best[8], best[9],
				best[10], best[11]);
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
				+ "(%.1f, %.1f), largest 1 m step %.2f m at (%.1f, %.1f), largest step %.2f blocks per block at (%.1f, %.1f), "
				+ "in the coastal belt %.2f at (%.1f, %.1f) (%.1f s)%n  limits: <= %d jumps, max <= %.2f m, at water <= %d, "
				+ "valley-made step <= %.2f m, <= %.1f blocks per block; goal: %s%n%s", win.name(), win.scale().id(), win.cx(),
				win.cz(), win.radius(), (long) TRANSECTS * ((int) (LENGTH / STEP) + 1), THRESHOLD, STEP, jumps.size(), max,
				nearWater, classes, scan.maxExcess(), scan.excessX(), scan.excessZ(), scan.maxStep(), scan.stepX(), scan.stepZ(),
				scan.maxBlocks(), scan.blocksX(), scan.blocksZ(), scan.coastBlocks(), scan.coastX(), scan.coastZ(), seconds,
				win.maxJumps(), win.maxHeight(), win.maxNearWater(), win.maxValley(), D4B_BLOCKS, win.goal(), list);
		assertTrue(jumps.size() <= win.maxJumps(), win.name() + ": " + jumps.size() + " jumps, limit " + win.maxJumps()
				+ "\n" + list);
		assertTrue(max <= win.maxHeight(), String.format(Locale.ROOT, "%s: jump of %.2f m, limit %.2f m%n%s", win.name(),
				max, win.maxHeight(), list));
		assertTrue(nearWater <= win.maxNearWater(), win.name() + ": " + nearWater + " jumps at standing water, limit "
				+ win.maxNearWater() + "\n" + list);
		assertTrue(scan.maxExcess() <= win.maxValley(), String.format(Locale.ROOT, "%s: valley-made step of %.2f m per 1 m at "
				+ "(%.1f, %.1f), limit %.2f m (decisions D4, D4a)", win.name(), scan.maxExcess(), scan.excessX(), scan.excessZ(),
				win.maxValley()));
		assertTrue(scan.maxBlocks() <= D4B_BLOCKS, String.format(Locale.ROOT, "%s: step of %.2f blocks per block at (%.1f, "
				+ "%.1f), limit %.1f (decision D4b)", win.name(), scan.maxBlocks(), scan.blocksX(), scan.blocksZ(), D4B_BLOCKS));
	}

	/**
	 * Dense scan of decisions D4 and D4b (round 1 of the review of K4c): the gameplay windows with Beskid mountains and
	 * large massifs, where the ties of decision D4a are, on a grid every {@value #DENSE_STEP} m. The {@value #TRANSECTS}
	 * random transects of {@link #surfaceHasNoIsolatedJumps} miss narrow features: the review found on dense grids a
	 * scarp 17–20 m high and 70–180 m long along the edge of the region where the first K4c skipped the sweep cut, and
	 * fields of straight ribs up to 2.5 blocks per block, in windows where the transects showed neither. Between grid
	 * neighbors in both directions, on dry land outside standing water and outside the coastal belt: at most
	 * {@link #D4B_BLOCKS} blocks per block (decision D4b) and at most the valley-made step limit of the window per 1 m
	 * (decision D4, with the ties of D4a: the measured state of the dense scan, {@link #DENSE_VALLEY}), both over 1 m:
	 * a pair whose mean step over 2 m is large is resampled at its midpoint ({@link #pair}, re-review of K4c).
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("denseWindows")
	void denseGridHasNoCliffs(Window win) {
		LandscapeModel m = new LandscapeModel(SEED, win.scale(), 1.0);
		VerticalScale vs = win.scale() == LandscapeScale.GAMEPLAY ? VerticalScale.GAMEPLAY : VerticalScale.REAL;
		double coastBelt = 3_000 * m.scale().meso();
		int n = (int) Math.round(2 * win.radius() / DENSE_STEP) + 1;
		double x0 = win.cx() - win.radius();
		double z0 = win.cz() - win.radius();
		long t0 = System.nanoTime();
		double valleyLimit = DENSE_VALLEY.getOrDefault(win.name(), D4_STEP);
		// Bands of rows, each with the row before it: {largest blocks per block, x, z, largest valley-made step per 1 m, x, z,
		// steps above D4B_BLOCKS, pairs refined at the midpoint}.
		int band = 16;
		double[][] per = IntStream.range(0, (n + band - 1) / band).parallel().mapToObj(b -> {
			double[] best = new double[8];
			double[] surf = new double[n];
			double[] raw = new double[n];
			boolean[] ok = new boolean[n];
			double[] pSurf = new double[n];
			double[] pRaw = new double[n];
			boolean[] pOk = new boolean[n];
			int jStart = Math.max(0, b * band - 1);
			int jEnd = Math.min(n, (b + 1) * band);
			for (int j = jStart; j < jEnd; j++) {
				double z = z0 + j * DENSE_STEP;
				for (int i = 0; i < n; i++) {
					ColumnSample s = m.sample(x0 + i * DENSE_STEP, z);
					surf[i] = s.surface();
					raw[i] = s.terrain().rawSurface();
					ok[i] = dryLand(s) && s.terrain().coastD() >= coastBelt;
				}
				boolean own = j >= b * band;
				for (int i = 0; i < n; i++) {
					if (!ok[i]) {
						continue;
					}
					double x = x0 + i * DENSE_STEP;
					if (own && i > 0 && ok[i - 1]) {
						pair(m, best, vs, surf[i], surf[i - 1], raw[i], raw[i - 1], x, z, x - DENSE_STEP, z, valleyLimit,
								coastBelt);
					}
					if (j > jStart && pOk[i]) {
						pair(m, best, vs, surf[i], pSurf[i], raw[i], pRaw[i], x, z, x, z - DENSE_STEP, valleyLimit, coastBelt);
					}
				}
				double[] t = pSurf;
				pSurf = surf;
				surf = t;
				t = pRaw;
				pRaw = raw;
				raw = t;
				boolean[] o = pOk;
				pOk = ok;
				ok = o;
			}
			return best;
		}).toArray(double[][]::new);
		double[] best = new double[8];
		for (double[] p : per) {
			for (int q = 0; q < 6; q += 3) {
				if (p[q] > best[q]) {
					System.arraycopy(p, q, best, q, 3);
				}
			}
			best[6] += p[6];
			best[7] += p[7];
		}
		System.out.printf(Locale.ROOT, "[dense] %s: %d x %d samples every %.1f m, %d pairs refined at the midpoint: largest 1 m "
				+ "step %.2f blocks per block at (%.1f, %.1f), steps above %.1f: %d; largest valley-made step %.2f m per 1 m at "
				+ "(%.1f, %.1f) (limit %.2f) (%.1f s)%n", win.name(), n, n, DENSE_STEP, (long) best[7], best[0], best[1], best[2],
				D4B_BLOCKS, (long) best[6], best[3], best[4], best[5], valleyLimit, (System.nanoTime() - t0) / 1e9);
		assertTrue(best[0] <= D4B_BLOCKS, String.format(Locale.ROOT, "%s: step of %.2f blocks per block at (%.1f, %.1f), "
				+ "limit %.1f (decision D4b)", win.name(), best[0], best[1], best[2], D4B_BLOCKS));
		assertTrue(best[3] <= valleyLimit, String.format(Locale.ROOT, "%s: valley-made step of %.2f m per 1 m at (%.1f, %.1f), "
				+ "limit %.2f (decisions D4, D4a)", win.name(), best[3], best[4], best[5], valleyLimit));
	}

	/**
	 * One pair of grid neighbors (x, z) and (xp, zp) of {@link #denseGridHasNoCliffs}, {@value #DENSE_STEP} m apart: the
	 * steps over 1 m (one block) into {@code best} (re-review of K4c: the mean over 2 m understated a 1 m step by up to
	 * half). A pair whose mean step over 2 m exceeds {@value #DENSE_REFINE} of a limit is sampled at its midpoint and
	 * measured as two 1 m steps (a 1 m step above a limit with the mean below that share would need the other half to
	 * fall back by more than the rest, i.e. a crest or a notch narrower than 2 m); the other pairs count their mean.
	 */
	private static void pair(LandscapeModel m, double[] best, VerticalScale vs, double h, double hp, double r, double rp,
			double x, double z, double xp, double zp, double valleyLimit, double coastBelt) {
		double b = vs.blocksForMeters(h);
		double bp = vs.blocksForMeters(hp);
		double bl = Math.abs(b - bp) / DENSE_STEP;
		double e = (Math.abs(h - hp) - Math.abs(r - rp)) / DENSE_STEP;
		double mx = 0.5 * (x + xp);
		double mz = 0.5 * (z + zp);
		if (bl > DENSE_REFINE * D4B_BLOCKS || e > DENSE_REFINE * valleyLimit) {
			ColumnSample s = m.sample(mx, mz);
			best[7]++;
			if (dryLand(s) && s.terrain().coastD() >= coastBelt) {
				double hm = s.surface();
				double rm = s.terrain().rawSurface();
				double bm = vs.blocksForMeters(hm);
				double half = DENSE_STEP / 2;
				step(best, Math.abs(b - bm) / half, (Math.abs(h - hm) - Math.abs(r - rm)) / half, 0.5 * (x + mx),
						0.5 * (z + mz));
				step(best, Math.abs(bm - bp) / half, (Math.abs(hm - hp) - Math.abs(rm - rp)) / half, 0.5 * (mx + xp),
						0.5 * (mz + zp));
				return;
			}
		}
		step(best, bl, e, mx, mz);
	}

	/** One step of {@link #pair}: blocks per block and the valley-made step per 1 m at (x, z) into {@code best}. */
	private static void step(double[] best, double bl, double e, double x, double z) {
		if (bl > best[0]) {
			best[0] = bl;
			best[1] = x;
			best[2] = z;
		}
		if (bl > D4B_BLOCKS) {
			best[6]++;
		}
		if (e > best[3]) {
			best[3] = e;
			best[4] = x;
			best[5] = z;
		}
	}

	/** {@link #pair}: share of a limit above which the mean step over 2 m is refined at the midpoint. */
	static final double DENSE_REFINE = 0.4;

	/**
	 * Dense scan of decision D4b at realistic scale (round 2 of the review of K4c): the windows realistic_beskids and
	 * realistic_massif_1723 are too large for a full grid ({@link #denseGridHasNoCliffs}), so the scan takes
	 * {@value #PATCHES} random patches of {@value #PATCH_SIDE} m per window ({@code java.util.Random} with the seed
	 * {@value #PATCH_SEED}) and the places found by the review on a 1 m grid of the cut tiles of realistic_beskids
	 * ({@link #REAL_SPOTS}), each every {@value #PATCH_STEP} m. Between grid neighbors in both directions, on dry land
	 * outside standing water and outside the coastal belt: the largest step in blocks per block and the number of steps
	 * above {@link #D4B_BLOCKS}. Decision D4b is not met at realistic scale (deviation R5, docs/m2/poprawka-geometrii.md):
	 * the sides of the projection are steeper than the designed 1.5 · maxSlope where the valley axis is inclined against
	 * the curve of its segment (the distance from the axis then grows by up to sqrt(1 + (dwander/ds / g)²) ≈ 1.9–2.2 m
	 * per 1 m: (125919, 1050642), (174776, 1050993), (150330, −1530776)), which the sweep cut does not touch (decision
	 * D4a), and at one tie ((142364, 1040652)) the sweep cut softens a scarp of the projection (up to 16.8 m per 1 m) to
	 * 2.7 m per 1 m. The fix (the projection on the valley axis itself, together with the ties of D4a) is in M5; until
	 * then the limits are the measured state ({@link #REAL_DENSE_BLOCKS}, {@link #REAL_DENSE_PAIRS}), so the walls do not
	 * get worse. Step K5 tried once more to widen the wall of the projection by that factor where the distinctness g is
	 * small (prototype R5, docs/m2/poprawka-geometrii.md): the factor changes at the rate of t of the arm, 1 / (|P'| g)
	 * per meter, so the wall width jumped by hundreds of meters within a meter near the folds (up to 101.8 blocks per
	 * block in realistic_massif_1723, 13.1 with a gate on g), and R5 stays an exception until M5. A full scan of
	 * realistic_beskids every 4 m with the steps above 0.5 blocks per block resampled every 1 m finds the same six places
	 * of {@link #REAL_SPOTS} (2.67 blocks per block, 1508 steps above 2) and no other.
	 */
	@ParameterizedTest(name = "{0}")
	@MethodSource("realisticDenseWindows")
	void realisticPatchesHaveNoCliffs(Window win) {
		LandscapeModel m = new LandscapeModel(SEED, win.scale(), 1.0);
		VerticalScale vs = VerticalScale.REAL;
		double coastBelt = 3_000 * m.scale().meso();
		Random rnd = new Random(PATCH_SEED);
		List<double[]> centers = new ArrayList<>();
		for (int p = 0; p < PATCHES; p++) {
			centers.add(new double[] {win.cx() + (rnd.nextDouble() * 2 - 1) * (win.radius() - PATCH_SIDE),
					win.cz() + (rnd.nextDouble() * 2 - 1) * (win.radius() - PATCH_SIDE)});
		}
		for (double[] s : REAL_SPOTS) {
			if (Math.abs(s[0] - win.cx()) <= win.radius() && Math.abs(s[1] - win.cz()) <= win.radius()) {
				centers.add(s);
			}
		}
		int n = (int) Math.round(PATCH_SIDE / PATCH_STEP) + 1;
		long t0 = System.nanoTime();
		// Per patch: {largest blocks per block, x, z, pairs above D4B_BLOCKS}.
		double[][] per = centers.parallelStream().map(c -> {
			double x0 = c[0] - PATCH_SIDE / 2;
			double z0 = c[1] - PATCH_SIDE / 2;
			double[] h = new double[n * n];
			boolean[] ok = new boolean[n * n];
			for (int k = 0; k < n * n; k++) {
				ColumnSample s = m.sample(x0 + (k % n) * PATCH_STEP, z0 + (k / n) * PATCH_STEP);
				h[k] = vs.blocksForMeters(s.surface());
				ok[k] = dryLand(s) && s.terrain().coastD() >= coastBelt;
			}
			double[] best = new double[4];
			for (int k = 0; k < n * n; k++) {
				int i = k % n;
				for (int q : new int[] {i + 1 < n ? k + 1 : -1, k + n < n * n ? k + n : -1}) {
					if (q < 0 || !ok[k] || !ok[q]) {
						continue;
					}
					double bl = Math.abs(h[q] - h[k]) / PATCH_STEP;
					if (bl > best[0]) {
						best[0] = bl;
						best[1] = x0 + 0.5 * (i + q % n) * PATCH_STEP;
						best[2] = z0 + 0.5 * (k / n + q / n) * PATCH_STEP;
					}
					best[3] += bl > D4B_BLOCKS ? 1 : 0;
				}
			}
			return best;
		}).toArray(double[][]::new);
		double[] best = new double[4];
		long patchesOver = 0;
		for (double[] p : per) {
			if (p[0] > best[0]) {
				System.arraycopy(p, 0, best, 0, 3);
			}
			best[3] += p[3];
			patchesOver += p[3] > 0 ? 1 : 0;
		}
		double limit = REAL_DENSE_BLOCKS.get(win.name());
		long pairsLimit = REAL_DENSE_PAIRS.get(win.name());
		System.out.printf(Locale.ROOT, "[dense-real] %s: %d patches of %.0f m every %.1f m: largest step %.2f blocks per block at "
				+ "(%.1f, %.1f), steps above %.1f: %d in %d patches (limits %.2f, %d; deviation R5) (%.1f s)%n", win.name(),
				centers.size(), PATCH_SIDE, PATCH_STEP, best[0], best[1], best[2], D4B_BLOCKS, (long) best[3], patchesOver, limit,
				pairsLimit, (System.nanoTime() - t0) / 1e9);
		assertTrue(best[0] <= limit, String.format(Locale.ROOT, "%s: step of %.2f blocks per block at (%.1f, %.1f), limit %.2f "
				+ "(decision D4b, deviation R5)", win.name(), best[0], best[1], best[2], limit));
		assertTrue(best[3] <= pairsLimit, String.format(Locale.ROOT, "%s: %d steps above %.1f blocks per block, limit %d "
				+ "(decision D4b, deviation R5)", win.name(), (long) best[3], D4B_BLOCKS, pairsLimit));
	}

	/** The windows of {@link #realisticPatchesHaveNoCliffs}: realistic scale, Beskid mountains and a large massif. */
	static Stream<Window> realisticDenseWindows() {
		return windows().filter(w -> w.name().equals("realistic_beskids") || w.name().equals("realistic_massif_1723"));
	}

	/** {@link #realisticPatchesHaveNoCliffs}: random patches per window, their side and grid step (m), the seed. */
	static final int PATCHES = 120;
	static final double PATCH_SIDE = 200;
	static final double PATCH_STEP = 1.0;
	static final long PATCH_SEED = 23;
	/**
	 * {@link #realisticPatchesHaveNoCliffs}: the places of steps above 2 blocks per block found on a 1 m grid of the tiles
	 * with an active sweep cut: by the review of round 1 of K4c in realistic_beskids (2.75, 2.62, 2.54, 2.48, 2.26, 2.12,
	 * 2.11, 2.04 blocks per block) and in round 2 in realistic_massif_1723 (2.17, a side of the projection).
	 */
	static final double[][] REAL_SPOTS = {{142_364, 1_040_652}, {125_919, 1_050_642}, {132_704, 1_042_536},
			{174_776, 1_050_993}, {151_575, 1_071_336}, {171_294, 1_068_637}, {161_943, 1_075_025}, {143_010, 1_040_871},
			{150_330, -1_530_776}};
	/**
	 * {@link #realisticPatchesHaveNoCliffs}: limits, the measured state after round 2 of the review of K4c (deviation R5):
	 * realistic_beskids 2.75 blocks per block, 5910 steps above 2 in 7 of the 128 patches, realistic_massif_1723 2.17 and
	 * 915 steps in 1 of the 121 patches (all at the places of {@link #REAL_SPOTS}; none in the random patches).
	 */
	static final Map<String, Double> REAL_DENSE_BLOCKS = Map.of("realistic_beskids", 2.8, "realistic_massif_1723", 2.2);
	static final Map<String, Long> REAL_DENSE_PAIRS = Map.of("realistic_beskids", 6_000L, "realistic_massif_1723", 950L);

	/** The windows of {@link #denseGridHasNoCliffs}: gameplay scale, Beskid mountains and large massifs. */
	static Stream<Window> denseWindows() {
		return windows().filter(w -> w.scale() == LandscapeScale.GAMEPLAY
				&& (w.name().equals("gameplay_beskids") || w.name().startsWith("gameplay_massif_")));
	}

	/** Grid step of {@link #denseGridHasNoCliffs} (m). */
	static final double DENSE_STEP = 2.0;
	/**
	 * Limits of the valley-made step (m per 1 m) on the dense grid where ties of the arms are left (decision D4a), the
	 * measured state; elsewhere {@link #D4_STEP}. After round 1 of the review of K4c, as the mean over 2 m: 4.26, 4.47,
	 * 4.09, 3.50 m per 1 m (gameplay_massif_1710 2.97), at most 1.56 blocks per block. Since step K5 (re-review of K4c)
	 * the steps are measured over 1 m ({@link #pair}): 5.78, 5.51, 4.57, 3.53 m per 1 m (gameplay_massif_1710 2.99), at
	 * most 1.71 blocks per block, none above 2; the terrain is the same.
	 */
	static final Map<String, Double> DENSE_VALLEY = Map.of("gameplay_beskids", 5.8, "gameplay_massif_spawn", 5.55,
			"gameplay_massif_1660", 4.6, "gameplay_massif_1718", 3.55);

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
	 * dome, 2.5 m apart). Step K4c (the sweep cut only near ties, decision D4a): blades 0 pairs, largest 13.4 m over 2.5 m
	 * at (91689.5, 169759.5) (a side of an order 1 valley, 5.4 m per 1 m, at most 1.6 blocks per block), fan unchanged.
	 * Step K5 (K5.4, A3: the flank of a basin passes into the terrain over 0.4–1.0 of its reach instead of a wall of
	 * 5000 m · smoothstep): the sink lake east of the 1723 m massif 230 / 46.0 m -> 0 / 13.3 m, the sink lake south-east of
	 * the 1659 m massif 148 / 97.4 m -> 50 / 30.2 m (its level lies about 100 m below the steep flank of the massif
	 * around it, and the flank of the basin, 27 m wide, climbs that at up to 6 m per 1 m; the reach of a sink lake basin
	 * is bound to the tile filter of the river network, M5). Blades 11.9 m. Round 1 after the review of K5: the third
	 * harmonic of the sink lake shore (K5.5) moves the shore of the 1659 m lake along the steep flank: 50 / 30.2 m -> 62 /
	 * 31.7 m, the same wall of the basin (M5). Review of K7: three more sink lakes at the foot of large massifs with
	 * walls, added with limits at the measured state as a regression guard (no model change in K7; M5): the lake below the
	 * 1719 m massif (level 663, rivers crossing its shore belt on raised strips), the lake at the 1698 m massif (level 642,
	 * its rim about 30 m from the floor of an order 3 valley at 387–390 m, up to 15 m per 1 m) and the lake at
	 * (213514, -1519439) (level 665). Goal: no pair above {@value #CLIFF} m in any spot.
	 */
	static Stream<Spot> spots() {
		LandscapeScale g = LandscapeScale.GAMEPLAY;
		LandscapeScale r = LandscapeScale.REALISTIC;
		return Stream.of(
				// Box canyon of 378 m behind the source of (145867, -1474074) -> (146745, -1473298) (first version of K2).
				new Spot("realistic_box_canyon_1698", r, 144_478, -1_474_422, 1_000, 5, 0, 5.7,
						"K0 0, 6.9 m; K2 334, 377.9 m; K2 review 0, 5.6 m"),
				// 151 m behind the source of (150591, -1525742), at the new sink lake east of the 1723 m massif.
				new Spot("realistic_sink_lake_1723", r, 150_900, -1_525_100, 1_100, 5, 0, 13.4,
						"K0 0, 7.8 m; K2 313, 151.1 m; K2 review 231, 45.9 m; K4c 230, 46.0 m"),
				// 100 m wall and a star of wedges around the sink lake south-east of the massif (515882, -1401302).
				new Spot("realistic_sink_lake_1659", r, 518_000, -1_404_342, 1_000, 5, 62, 31.8,
						"K0 0, 6.3 m; K2 161, 100.6 m; K2 review 148, 97.3 m; K4c 148, 97.4 m; K5 50, 30.2 m"),
				// The 1 km deep slot canyon in the dome of the massif (258824, -1539366).
				new Spot("realistic_slot_1700", r, 260_024, -1_538_100, 1_000, 5, 0, 10.3,
						"K0 0, 6.2 m; K2 1915, 1021.6 m; K2 review 0, 10.2 m"),
				// Blades of 84 m on the flank of the gameplay massif (90847, 171052).
				new Spot("gameplay_blades_1707", g, 91_727, 169_377, 500, 2.5, 0, 12.0,
						"K0 0, 12.9 m; K2 321, 83.1 m; K2 review 320, 83.1 m; K4 exact minima 10, 31.4 m; K4b 0, 5.9 m"),
				// Fan on the north-west flank of the gameplay massif (129584, 76912).
				new Spot("gameplay_fan_1673", g, 128_900, 76_850, 500, 2.5, 0, 10.7,
						"K0 4, 27.8 m; K2 861, 89.8 m; K2 review 535, 83.1 m; K4 exact minima 0, 10.6 m; K4b 0, 10.6 m"),
				// Review of K7: walls of sink lakes at the foot of large massifs (measured state after K7, M5).
				new Spot("realistic_sink_lake_1719", r, 263_300, -1_536_950, 1_000, 5, 598, 38.3, "K7 598, 38.2 m"),
				new Spot("realistic_sink_lake_1698", r, 146_743, -1_473_317, 800, 5, 548, 75.7, "K7 548, 75.6 m"),
				new Spot("realistic_sink_lake_213514", r, 213_514, -1_519_439, 800, 5, 20, 28.0, "K7 20, 27.9 m"));
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
				+ "%.1f m at (%.1f, %.1f); limits %d / %.1f m; earlier: %s; goal: none%n", spot.name(), spot.scale().id(),
				spot.cx(), spot.cz(), spot.half(), spot.step(), (long) r[0], CLIFF, r[1], r[2], r[3], spot.maxPairs(),
				spot.maxHeight(), spot.before());
		assertTrue(r[0] <= spot.maxPairs(), spot.name() + ": " + (long) r[0] + " cliff pairs, limit " + spot.maxPairs());
		assertTrue(r[1] <= spot.maxHeight(), String.format(Locale.ROOT, "%s: cliff of %.1f m at (%.1f, %.1f), limit %.1f m",
				spot.name(), r[1], r[2], r[3], spot.maxHeight()));
	}
}
