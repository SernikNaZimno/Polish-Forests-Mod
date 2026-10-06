package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * River network, sea and coast: water banks, correct flow, no cliffs at sources, and the watercourse
 * fields exported to {@link ColumnSample.Waters} (M2, step S2).
 */
@Tag("slow")
class RiverNetworkTest {
	static final long SEED = 20260927L;
	/** Beskids interior for the seed {@link #SEED} (the "beskids" patch in {@code golden_terrain_m1.txt}); saves searching. */
	static final double[] BESKIDS_REAL = {154_834, 1_058_738};
	static final double[] BESKIDS_GAMEPLAY = {27_609, 3_254};

	@Test
	void networkIsAcyclicAndLevelsNeverRiseDownstream() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			RiverNetwork net = networkOf(m);
			int checked = 0;
			for (int order = 1; order <= 3; order++) {
				for (long i = -12; i <= 12; i += 3) {
					for (long j = -12; j <= 12; j += 3) {
						RiverNetwork.Node n = net.node(order, i, j);
						for (int step = 0; step < 5_000; step++) {
							RiverNetwork.Link l = net.link(n);
							if (l.kind() != 1) {
								if (l.kind() == 2) {
									assertTrue(net.level(n) >= l.target().levelAt(l.targetT()) - 1e-9,
											"level rises at a tributary mouth");
								}
								break;
							}
							RiverNetwork.Node d = net.node(order, l.di(), l.dj());
							assertTrue(d.route() < n.route(), "flow runs uphill");
							assertTrue(net.level(d) <= net.level(n) + 1e-9,
									"water level rises downstream at " + n);
							n = d;
							checked++;
							assertTrue(step < 4_999, "cycle in the river network from node " + n);
						}
					}
				}
			}
			assertTrue(checked > 50, "too few segments checked: " + checked);
		}
	}

	/** Decisions D4 and D4b: largest valley-made step (m per 1 m) and plain step in blocks per block at sources. */
	static final double SOURCE_VALLEY_STEP = 3.0;
	static final double SOURCE_BLOCKS = 2.0;

	/**
	 * TE (step K3, docs/m2/poprawka-geometrii.md): the meander belt share of the valley floor grows smoothly with the
	 * lowland share ({@link RiverNetwork#meanderBeltFactor}). In M1 it was switched on at lowland 0.3, which made
	 * scarps of 5–20 m in the valleys along the straight contours of the lowland share. Realistic scale ±60 km every
	 * 150 m along x, gameplay scale ±10 km every 25 m (1400 m region cells, many more crossings): every crossing of
	 * lowland (with the coastland, as passed to the river network) through 0.3 is bisected to a pair of columns a few
	 * µm apart, and a dry pair must not differ by more than 0.5 m. Crossings where the lowland share itself jumps (by
	 * more than 10⁻⁴ between the two columns: a seam of the 3 × 3 region blend; e.g. gameplay (5727.8, 2725) jumped
	 * from 0.2904 to 0.3000) are not threshold crossings and are only counted. The frozen M1 copy is scanned the same
	 * way to show that the scan finds the old scarps. Step K6 (A16, the 5 × 5 window): no such seam is left at either
	 * scale (the M1 copy has 1 at gameplay scale), and the test requires none.
	 */
	@Test
	void noStepAtLowlandThreshold() {
		lowlandThreshold(LandscapeScale.REALISTIC, pl.polishforests.worldgen.landscape.m1.LandscapeScale.REALISTIC, 60_000,
				150, 500);
		lowlandThreshold(LandscapeScale.GAMEPLAY, pl.polishforests.worldgen.landscape.m1.LandscapeScale.GAMEPLAY, 10_000, 25,
				500);
	}

	private static void lowlandThreshold(LandscapeScale scale, pl.polishforests.worldgen.landscape.m1.LandscapeScale m1Scale,
			double half, double step, int minCrossings) {
		LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
		ThresholdScan now = scanLowlandThreshold((x, z) -> {
			LandscapeModel.Blend b = m.blend(x, z);
			return b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU)
					+ b.weight(LandscapeType.OLD_GLACIAL_PLAIN) + b.weight(LandscapeType.COASTLAND);
		}, (x, z) -> {
			ColumnSample c = m.sample(x, z);
			return c.hasWater() ? Double.NaN : c.surface();
		}, half, step);
		pl.polishforests.worldgen.landscape.m1.LandscapeModel old = new pl.polishforests.worldgen.landscape.m1.LandscapeModel(
				SEED, m1Scale, 1.0);
		ThresholdScan m1 = scanLowlandThreshold((x, z) -> {
			pl.polishforests.worldgen.landscape.m1.LandscapeModel.Blend b = old.blend(x, z);
			return b.weight(pl.polishforests.worldgen.landscape.m1.LandscapeType.SANDR)
					+ b.weight(pl.polishforests.worldgen.landscape.m1.LandscapeType.WYSOCZYZNA_MORENOWA)
					+ b.weight(pl.polishforests.worldgen.landscape.m1.LandscapeType.ROWNINA_STAROGLACJALNA)
					+ b.weight(pl.polishforests.worldgen.landscape.m1.LandscapeType.POBRZEZE);
		}, (x, z) -> {
			pl.polishforests.worldgen.landscape.m1.ColumnSample c = old.sample(x, z);
			return c.hasWater() ? Double.NaN : c.surface();
		}, half, step);
		System.out.println("Lowland threshold 0.3, " + scale.id() + ": now " + now + "; frozen M1 copy " + m1);
		assertTrue(now.crossings() > minCrossings, scale.id() + ": too few threshold crossings: " + now.crossings());
		assertTrue(m1.steps() > 0, scale.id() + ": the scan does not find the M1 scarps, so it proves nothing: " + m1);
		assertTrue(now.steps() == 0, scale.id() + ": scarps at the lowland threshold: " + now);
		assertTrue(now.seams() == 0, scale.id() + ": seams of the region blend at the lowland threshold (A16): " + now);
	}

	private interface Field {
		double at(double x, double z);
	}

	/**
	 * Result of {@link #scanLowlandThreshold}: crossings of 0.3 with a continuous lowland share, dry pairs among them,
	 * pairs differing by more than 0.5 m (the largest and its place), and crossings at a jump of the lowland share.
	 */
	private record ThresholdScan(int crossings, int dry, int steps, double worst, String where, int seams) {
		@Override
		public String toString() {
			return String.format(Locale.ROOT, "%d crossings (%d dry), %d steps > 0.5 m (largest %.2f m at %s), %d blend seams",
					crossings, dry, steps, worst, where, seams);
		}
	}

	private static ThresholdScan scanLowlandThreshold(Field lowland, Field drySurface, double half, double step) {
		int n = (int) Math.round(2 * half / step);
		int crossings = 0;
		int dry = 0;
		int steps = 0;
		int seams = 0;
		double worst = 0;
		String where = "-";
		for (int iz = 0; iz <= n; iz++) {
			double z = -half + iz * step;
			double l0 = lowland.at(-half, z) - 0.3;
			for (int ix = 0; ix < n; ix++) {
				double xa = -half + ix * step;
				double xb = xa + step;
				double l1 = lowland.at(xb, z) - 0.3;
				boolean crossing = l0 * l1 < 0;
				double la = l0;
				l0 = l1;
				if (!crossing) {
					continue;
				}
				for (int it = 0; it < 25; it++) {
					double xm = 0.5 * (xa + xb);
					double lm = lowland.at(xm, z) - 0.3;
					if (la * lm <= 0) {
						xb = xm;
					} else {
						xa = xm;
						la = lm;
					}
				}
				if (Math.abs(lowland.at(xa, z) - lowland.at(xb, z)) > 1e-4) {
					seams++;
					continue;
				}
				crossings++;
				double ha = drySurface.at(xa, z);
				double hb = drySurface.at(xb, z);
				if (Double.isNaN(ha) || Double.isNaN(hb)) {
					continue;
				}
				dry++;
				double dh = Math.abs(ha - hb);
				if (dh > 0.5) {
					steps++;
					if (dh > worst) {
						worst = dh;
						where = String.format(Locale.ROOT, "(%.1f, %.1f)", xa, z);
					}
				}
			}
		}
		return new ThresholdScan(crossings, dry, steps, worst, where, seams);
	}

	/**
	 * River rules of the large massifs (M2-8, step K2) on every massif of {@link GreatMassifSurvey}: no node of order
	 * 1–3 in the core (G &gt; {@link LandscapeModel#GM_CORE}) is a spring and no segment starts its source there. The
	 * cores are then measured on a grid ({@link #coreCut}: every 25 m at realistic scale, 10 m at gameplay scale) and
	 * their valleys are held to the state after the review of K2:
	 * <ul>
	 * <li>summit area (G &gt; 0.9), i.e. no canyon in the dome: no water, cut ({@code landElevation} − surface) at most
	 * {@link #SUMMIT_CUT} m (21 m realistic, 59 m gameplay; the design asked for less than 50 m, see the limits);</li>
	 * <li>the 1 km deep slot canyon of the first version of K2 at (260024, −1538766), realistic scale (G 0.87 on the
	 * massif (258824, −1539366)): cut by at most 50 m (now 0);</li>
	 * <li>whole core: at most {@link #CORE_FLOOR} columns on a valley floor or in water and none of them deeper in the
	 * core than G = {@link #CORE_FLOOR_G}, cut at most {@link #CORE_CUT} m.</li>
	 * </ul>
	 *
	 * <p>The design asked for a cut below 50 m on the whole core. The cut there is the massif flank above the rivers that
	 * flow around the massif (742 m at G 0.41 above an order 3 river at realistic scale, 856 m at G 0.30 at gameplay
	 * scale): the lifted dome rises from their valley at the side steepness, as Babia Gora rises 1000 m above the
	 * valleys at its foot. It is not a canyon in the dome, so it is only held at the measured state. The valley floors
	 * on the core edge come from the segment curves: the rule of {@code RiverNetwork.link} checks the straight path
	 * between nodes, while the curve (tangents, wander) of a long segment bulges up to G 0.50 (an order 2 source segment
	 * at (142063, −1477272) on the massif (143663, −1476297)); step K4 changed the segment geometry and measured this
	 * again (the same deepest place, docs/m2/poprawka-geometrii.md, K4).
	 */
	@Test
	void noSpringsOnMassifCore() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			RiverNetwork net = networkOf(m);
			double ra = GreatMassifSurvey.ra(sc);
			int coreNodes = 0;
			for (GreatMassifSurvey.Massif s : GreatMassifSurvey.survey(sc)) {
				LandscapeModel.GreatMassif g = s.massif();
				for (int order = 1; order <= 3; order++) {
					double spacing = net.spacing(order);
					long i0 = (long) Math.floor((g.x() - 1.3 * ra) / spacing);
					long i1 = (long) Math.floor((g.x() + 1.3 * ra) / spacing);
					long j0 = (long) Math.floor((g.z() - 1.3 * ra) / spacing);
					long j1 = (long) Math.floor((g.z() + 1.3 * ra) / spacing);
					for (long i = i0; i <= i1; i++) {
						for (long j = j0; j <= j1; j++) {
							RiverNetwork.Node n = net.node(order, i, j);
							if (m.greatMassifCore(n.x(), n.z())) {
								coreNodes++;
								assertFalse(net.isSpring(n), sc.id() + ": spring on the core of " + g + " at " + n);
							}
							RiverNetwork.Segment seg = net.segment(order, i, j);
							assertFalse(seg != null && seg.source && m.greatMassifCore(seg.px(0), seg.pz(0)),
									sc.id() + ": a source on the core of " + g + " at node " + n);
						}
					}
				}
			}
			assertTrue(coreNodes > 0, sc.id() + ": no nodes on the cores");
			CoreCut c = coreCut(m);
			int k = sc == LandscapeScale.REALISTIC ? 0 : 1;
			System.out.printf(Locale.ROOT, "%s: %d massifs, %d nodes on the cores (no springs); cores %d columns: largest cut "
					+ "%.1f m at %s (limit %.0f m, goal 50 m), valley floor or water %d columns (limit %d), the deepest at %s "
					+ "(limit G %.2f); summit areas %d columns: largest cut %.1f m at %s (limit %.0f m), water %d%s%n", sc.id(),
					GreatMassifSurvey.survey(sc).size(), coreNodes, c.columns(), c.maxCut(), c.maxCutAt(), CORE_CUT[k],
					c.floorColumns(), CORE_FLOOR[k], c.floorAt(), CORE_FLOOR_G[k], c.summitColumns(), c.maxSummitCut(),
					c.summitAt(), SUMMIT_CUT[k], c.summitWater(), Double.isNaN(c.slotCut()) ? ""
							: String.format(Locale.ROOT, "; slot (260024, -1538766) cut %.1f m", c.slotCut()));
			assertTrue(c.summitColumns() > 0, sc.id() + ": no summit areas");
			assertTrue(c.summitWater() == 0, sc.id() + ": water in the summit area of a massif");
			assertTrue(c.maxSummitCut() <= SUMMIT_CUT[k], sc.id() + ": summit area cut by " + c.maxSummitCut() + " m at "
					+ c.summitAt());
			assertTrue(c.maxCut() <= CORE_CUT[k], sc.id() + ": core cut by " + c.maxCut() + " m at " + c.maxCutAt());
			assertTrue(c.floorColumns() <= CORE_FLOOR[k], sc.id() + ": " + c.floorColumns()
					+ " columns of a valley floor or water on the cores, the deepest at " + c.floorAt());
			assertTrue(c.maxFloorG() <= CORE_FLOOR_G[k], sc.id() + ": a valley floor or water deep in a core at " + c.floorAt());
			assertTrue(Double.isNaN(c.slotCut()) || c.slotCut() <= 50, sc.id()
					+ ": the slot canyon at (260024, -1538766) is back, cut " + c.slotCut() + " m");
		}
	}

	/**
	 * Limits of {@link #noSpringsOnMassifCore}, realistic and gameplay scale, measured after step K4c (the valley geometry
	 * of K4: irregular floor edge G4, the node continuity G1B, the projection on exact minima, the narrowed meander belt at
	 * the heads A5; K4c: the sweep cut only near ties, the arc of the head, the mouth funnel as valley floor for the
	 * habitat fields): summit cut 20.4 and 51.5 m (K4 with the first sweep cut K4b: 20.5 and 58.0 m; after the review of K2:
	 * 19.6 and 50.6 m), core cut 737.3 and 855.8 m (K4b: 737.3, 855.8), 1620 and 151 floor columns (K4b: 1604, 151; the
	 * funnels of G3 count as floor now), the deepest at G 0.502 and 0.345. Step K5: 1621 floor or water columns at
	 * realistic scale (the lobed shores of the sink lakes, K5.5, which are the only water of the cores). Largest cut of the
	 * summit area (G &gt; 0.9) in m.
	 *
	 * <p>Deviation from the design (criterion: cut of the summit area below 50 m; docs/m2/poprawka-geometrii.md, K4 and
	 * K4c): at gameplay scale the deepest cut, 51.5 m at (109332, 286015) on the massif (109062, 285995), is the upper edge of
	 * the side of a 1470 m deep valley of an order 1 stream on the flank, about 530 m from its valley axis, where the side
	 * (up to 600 m wide at gameplay scale) is steeper than 3 m per 1 m. With the first sweep cut (K4b) it was 58.0 m (the
	 * Euclidean distance from the axis polyline is shorter than the distance of the projection); without the irregular floor
	 * edge G4 on the cores it would be 51.9 m, so the widening of the floor edge is not the cause (step K4c). It is not a
	 * canyon in the dome (no floor and no water in the summit areas); the depth of such valleys on the domes is a matter of
	 * K2/S3.
	 */
	static final double[] SUMMIT_CUT = {21, 52};
	/** Largest cut of the core (G &gt; 0.3) in m. */
	static final double[] CORE_CUT = {742, 856};
	/** Largest number of core columns on a valley floor or in water. */
	static final int[] CORE_FLOOR = {1_625, 151};
	/** Largest massif strength G of a core column on a valley floor or in water. */
	static final double[] CORE_FLOOR_G = {0.51, 0.35};

	/**
	 * Valleys on the cores of the massifs of {@link GreatMassifSurvey}: columns with G &gt; 0.3 on a grid every 25 m
	 * (realistic) or 10 m (gameplay) in a square of 1.5 Ra around each massif center.
	 *
	 * @param columns       core columns
	 * @param maxCut        largest {@code landElevation} − surface on the cores
	 * @param floorColumns  core columns on a valley floor ({@code inFloor}) or in water
	 * @param maxFloorG     largest G of such a column ({@code floorAt}), 0 without them
	 * @param summitColumns columns with G &gt; 0.9
	 * @param slotCut       cut at (260024, −1538766) at realistic scale (the slot canyon of the first version of K2), else NaN
	 */
	record CoreCut(int columns, double maxCut, String maxCutAt, int floorColumns, double maxFloorG, String floorAt,
			int summitColumns, double maxSummitCut, String summitAt, int summitWater, double slotCut) {
	}

	static CoreCut coreCut(LandscapeModel m) {
		LandscapeScale sc = m.scale();
		double ra = GreatMassifSurvey.ra(sc);
		double step = sc == LandscapeScale.REALISTIC ? 25 : 10;
		int k = (int) Math.ceil(0.75 * ra / step);
		int side = 2 * k + 1;
		int columns = 0;
		int floor = 0;
		int summit = 0;
		int summitWater = 0;
		double maxCut = 0;
		double maxFloorG = 0;
		double maxSummitCut = 0;
		String maxCutAt = "-";
		String floorAt = "-";
		String summitAt = "-";
		for (GreatMassifSurvey.Massif s : GreatMassifSurvey.survey(sc)) {
			LandscapeModel.GreatMassif g = s.massif();
			// {G, cut, valley floor or water, water, x, z} of the core columns, in the order of the grid.
			double[][] core = IntStream.range(0, side * side).parallel().mapToObj(q -> {
				double x = g.x() + (q % side - k) * step;
				double z = g.z() + (q / side - k) * step;
				double strength = m.greatMassifStrength(x, z);
				if (strength <= LandscapeModel.GM_CORE) {
					return null;
				}
				ColumnSample c = m.sample(x, z);
				return new double[] {strength, m.landElevation(x, z) - c.surface(),
						c.hasWater() || c.waters().inFloor() ? 1 : 0, c.hasWater() ? 1 : 0, x, z};
			}).filter(v -> v != null).toArray(double[][]::new);
			for (double[] c : core) {
				String at = String.format(Locale.ROOT, "(%.0f, %.0f) G %.2f on %s", c[4], c[5], c[0], g);
				columns++;
				if (c[1] > maxCut) {
					maxCut = c[1];
					maxCutAt = at;
				}
				if (c[2] > 0) {
					floor++;
					if (c[0] > maxFloorG) {
						maxFloorG = c[0];
						floorAt = at;
					}
				}
				if (c[0] > 0.9) {
					summit++;
					summitWater += (int) c[3];
					if (c[1] > maxSummitCut) {
						maxSummitCut = c[1];
						summitAt = at;
					}
				}
			}
		}
		double slot = Double.NaN;
		if (sc == LandscapeScale.REALISTIC) {
			slot = m.landElevation(260_024, -1_538_766) - m.sample(260_024, -1_538_766).surface();
		}
		return new CoreCut(columns, maxCut, maxCutAt, floor, maxFloorG, floorAt, summit, maxSummitCut, summitAt, summitWater, slot);
	}

	/**
	 * Sources of streams on the flanks of the large massifs (review of step K2). Like
	 * {@link MountainStreamSourcesTest#mountainStreamSourcesHaveNoCliffs}, but for the order 1 sources in the reach of the massifs of
	 * {@link GreatMassifSurvey} (G &gt; 0 at the source, at most {@value #MASSIF_SOURCES} per massif in the order of the node
	 * grid), in both scales, and also behind the source: transects across the valley at t from −0.5 (on the extension
	 * of the valley axis behind the source) to 0.5, ±60 m·k wide. The first version of K2 had straight valley wedges
	 * with head walls of 100–380 m behind such sources. Two measures per 1 m (in x and in z, dry columns): the plain
	 * step, held at the measured state {@link #MASSIF_SOURCE_STEP}, and the valley-made step, the plain step beyond the
	 * step of the terrain before valleys ({@code rawSurface}), which decision D4 (step K4b) holds to the 3 m per 1 m of
	 * {@link MountainStreamSourcesTest#mountainStreamSourcesHaveNoCliffs}. The plain step cannot meet that goal at gameplay scale: the domes of the
	 * massifs (step K2) are steeper than 3 m per 1 m over 100–170 m without any valley (review of K4), and a valley side
	 * cut into such a flank adds its own steepness.
	 */
	@Test
	void massifStreamSourcesHaveNoCliffs() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			SourceCliffs r = massifSourceCliffs(m);
			int k = sc == LandscapeScale.REALISTIC ? 0 : 1;
			System.out.printf(Locale.ROOT, "%s: %d sources on the massif flanks, largest step %.2f m per 1 m at %s (limit "
					+ "%.2f m); largest valley-made step %.2f m per 1 m at %s (limit %.1f m); largest step %.2f blocks per block at "
					+ "%s (D4b limit %.1f)%n", sc.id(), r.sources(), r.worst(), r.where(), MASSIF_SOURCE_STEP[k], r.worstExcess(),
					r.whereExcess(), MASSIF_SOURCE_EXCESS[k], r.worstBlocks(), r.whereBlocks(), SOURCE_BLOCKS);
			assertTrue(r.sources() >= 20, sc.id() + ": only " + r.sources() + " sources on the massif flanks");
			assertTrue(r.worst() <= MASSIF_SOURCE_STEP[k], sc.id() + ": cliff at a source on a massif flank: " + r.worst()
					+ " m per 1 m at " + r.where());
			assertTrue(r.worstExcess() <= MASSIF_SOURCE_EXCESS[k], sc.id() + ": valley-made cliff at a source on a massif "
					+ "flank: " + r.worstExcess() + " m per 1 m at " + r.whereExcess());
			assertTrue(r.worstBlocks() <= SOURCE_BLOCKS, sc.id() + ": cliff at a source on a massif flank: " + r.worstBlocks()
					+ " blocks per block at " + r.whereBlocks());
		}
	}

	static final int MASSIF_SOURCES = 12;
	/**
	 * Largest step per 1 m at the sources on the massif flanks, realistic and gameplay scale. After the review of K2: 1.46
	 * m (108 sources) and 25.87 m (300 sources; at (4989.7, −33301.9) on the massif by the spawn, a jump of the projection
	 * of a short order 1 segment, A2). After step K4 with K4b (exact distance minima and the sweep cut,
	 * docs/m2/poprawka-geometrii.md, K4b): 1.46 m and 4.06 m at (−186812.8, −274959.9), where the terrain before valleys
	 * already falls 2.7 m per 1 m (the valley-made part is 1.3 m). After step K4c (the sweep cut only near ties, decision
	 * D4a): 1.46 m and 6.01 m at (247697.4, −287790.7), a tie of two arms of a short order 1 segment on the flank of the
	 * massif (249214, −287025), 1.4 blocks per block. After round 1 of the review of K4c (the sweep cut everywhere, its
	 * maximum also at the arms): 1.46 m and 4.74 m at (−186815.1, −274954.4), 1.18 blocks per block.
	 */
	static final double[] MASSIF_SOURCE_STEP = {1.5, 4.8};
	/**
	 * Decision D4: largest valley-made step per 1 m (beyond the step of {@code rawSurface}) at those sources, realistic and
	 * gameplay scale: 3 m, at gameplay scale the measured state after K4c (5.40 m at (247697.4, −287790.7), the tie of
	 * decision D4a above; K4b: 2.54 m); after round 1 of the review of K4c 3.27 m at (201512.9, 121054.9).
	 */
	static final double[] MASSIF_SOURCE_EXCESS = {3.0, 3.3};

	/**
	 * Sources checked by {@link #massifStreamSourcesHaveNoCliffs}, the largest step per 1 m found at them and the largest
	 * valley-made step (the step beyond the step of the terrain before valleys).
	 */
	record SourceCliffs(int sources, double worst, String where, double worstExcess, String whereExcess,
			double worstBlocks, String whereBlocks) {
	}

	static SourceCliffs massifSourceCliffs(LandscapeModel m) {
		LandscapeScale sc = m.scale();
		RiverNetwork net = networkOf(m);
		pl.polishforests.worldgen.chunk.VerticalScale vs = sc == LandscapeScale.GAMEPLAY
				? pl.polishforests.worldgen.chunk.VerticalScale.GAMEPLAY : pl.polishforests.worldgen.chunk.VerticalScale.REAL;
		double ra = GreatMassifSurvey.ra(sc);
		double spacing = net.spacing(1);
		double half = 60 * sc.local();
		double lateralStep = 3 * sc.local();
		List<RiverNetwork.Segment> sources = new ArrayList<>();
		for (GreatMassifSurvey.Massif s : GreatMassifSurvey.survey(sc)) {
			LandscapeModel.GreatMassif g = s.massif();
			long i0 = (long) Math.floor((g.x() - 1.3 * ra) / spacing);
			long i1 = (long) Math.floor((g.x() + 1.3 * ra) / spacing);
			long j0 = (long) Math.floor((g.z() - 1.3 * ra) / spacing);
			long j1 = (long) Math.floor((g.z() + 1.3 * ra) / spacing);
			int taken = 0;
			for (long i = i0; i <= i1 && taken < MASSIF_SOURCES; i++) {
				for (long j = j0; j <= j1 && taken < MASSIF_SOURCES; j++) {
					RiverNetwork.Segment seg = net.segment(1, i, j);
					if (seg != null && seg.source && m.greatMassifStrength(seg.px(0), seg.pz(0)) > 0) {
						sources.add(seg);
						taken++;
					}
				}
			}
		}
		// {largest step, x, z, largest valley-made step, x, z, largest step in blocks, x, z} per source.
		double[][] worst = sources.parallelStream().map(seg -> {
			double best = 0;
			double bx = 0;
			double bz = 0;
			double bestExcess = 0;
			double ex = 0;
			double ez = 0;
			double bestBlocks = 0;
			double kx = 0;
			double kz = 0;
			double l0 = Math.hypot(seg.dx(0), seg.dz(0));
			for (int q = -10; q <= 10; q++) {
				double t = q * 0.05;
				double tx = t >= 0 ? seg.dx(t) : seg.dx(0);
				double tz = t >= 0 ? seg.dz(t) : seg.dz(0);
				double tl = t >= 0 ? Math.hypot(tx, tz) : l0;
				double cx = t >= 0 ? seg.px(t) : seg.px(0) + tx / tl * t * seg.len;
				double cz = t >= 0 ? seg.pz(t) : seg.pz(0) + tz / tl * t * seg.len;
				for (double k = -half; k <= half + 1e-9; k += lateralStep) {
					double x = cx - tz / tl * k;
					double z = cz + tx / tl * k;
					// Dry terrain only: the channel bank above the water may be steep.
					ColumnSample c0 = m.sample(x, z);
					ColumnSample c1 = m.sample(x + 1, z);
					ColumnSample c2 = m.sample(x, z + 1);
					if (c0.hasWater() || c1.hasWater() || c2.hasWater()) {
						continue;
					}
					double h0 = c0.surface();
					double step = Math.max(Math.abs(c1.surface() - h0), Math.abs(c2.surface() - h0));
					if (step > best) {
						best = step;
						bx = x;
						bz = z;
					}
					double r0 = c0.terrain().rawSurface();
					double excess = Math.max(Math.abs(c1.surface() - h0) - Math.abs(c1.terrain().rawSurface() - r0),
							Math.abs(c2.surface() - h0) - Math.abs(c2.terrain().rawSurface() - r0));
					if (excess > bestExcess) {
						bestExcess = excess;
						ex = x;
						ez = z;
					}
					double b0 = vs.blocksForMeters(h0);
					double blocks = Math.max(Math.abs(vs.blocksForMeters(c1.surface()) - b0),
							Math.abs(vs.blocksForMeters(c2.surface()) - b0));
					if (blocks > bestBlocks) {
						bestBlocks = blocks;
						kx = x;
						kz = z;
					}
				}
			}
			return new double[] {best, bx, bz, bestExcess, ex, ez, bestBlocks, kx, kz};
		}).toArray(double[][]::new);
		double w = 0;
		String where = "-";
		double we = 0;
		String whereExcess = "-";
		double wb = 0;
		String whereBlocks = "-";
		for (double[] v : worst) {
			if (v[6] > wb) {
				wb = v[6];
				whereBlocks = String.format(Locale.ROOT, "(%.1f, %.1f)", v[7], v[8]);
			}
			if (v[0] > w) {
				w = v[0];
				where = String.format(Locale.ROOT, "(%.1f, %.1f)", v[1], v[2]);
			}
			if (v[3] > we) {
				we = v[3];
				whereExcess = String.format(Locale.ROOT, "(%.1f, %.1f)", v[4], v[5]);
			}
		}
		return new SourceCliffs(sources.size(), w, where, we, whereExcess, wb, whereBlocks);
	}

	/**
	 * d (distance from the channel bank) is continuous and has a bounded gradient. In the channel d ≤ 0, and on the
	 * valley floor u ∈ [0, 1]. Sites: a lowland river, an order 3 river (strong meanders, valley far from the segment
	 * axis), an order 2 river in the lowland and a stream in the Beskids.
	 *
	 * <p>Every transect step (1 m) with |Δd| > 1.5 m is refined to 1/256 m, and the largest substep further
	 * to 1/65536 m: a jump (discontinuity) then stays large, while a steep gradient shrinks with the step.
	 * d is computed in the valley frame (u along, v across) in which the channel is drawn. In a
	 * strongly bent valley the frame is compressed, so the gradient of d locally reaches about 8 m per 1 m
	 * (Deviation S2 in docs/03-m2-biomy.md). We check: no jumps, a gradient in the waterside zone belt
	 * (d ≤ 200 m·k) of at most 10 m per 1 m and above 1.5 m per 1 m in at most 1% of the belt steps, outside the belt
	 * at most 4 m per 1 m.
	 */
	@Test
	void channelDistanceIsContinuousAndNonPositiveInChannel() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			boolean real = scale == LandscapeScale.REALISTIC;
			List<double[]> sites = new ArrayList<>();
			sites.add(find(m, c -> c.waterKind() == WaterKind.RIVER && c.type().isLowland(), real ? 500 : 25));
			sites.add(find(m, c -> c.waterKind() == WaterKind.RIVER && c.waters().streamOrder() == 3, real ? 700 : 40));
			sites.add(find(m, c -> c.waterKind() == WaterKind.RIVER && c.waters().streamOrder() == 2 && c.type().isLowland(),
					real ? 900 : 60));
			sites.add(findStream(m, real ? BESKIDS_REAL : BESKIDS_GAMEPLAY));
			int length = real ? 2_000 : 500;
			double band = 200 * scale.local();
			// {steps, steps in the belt, steep in the belt, channel columns, floor columns, jumps}
			long[] cnt = new long[6];
			double[] worst = new double[2];
			String[] where = {"", "", ""};
			for (double[] site : sites) {
				assertTrue(site != null, "no test site at scale " + scale.id());
				for (int a = 0; a < 16; a++) {
					double ang = a * Math.PI / 16 + 0.1;
					double dx = Math.cos(ang);
					double dz = Math.sin(ang);
					double prev = Double.NaN;
					for (int k = -length / 2; k <= length / 2; k++) {
						double x = site[0] + dx * k;
						double z = site[1] + dz * k;
						ColumnSample c = m.sample(x, z);
						ColumnSample.Waters w = c.waters();
						double d = w.channelDist();
						if (c.waterKind() == WaterKind.RIVER) {
							cnt[3]++;
							assertTrue(d <= 0, "d > 0 in the channel: " + w + " at " + x + "," + z);
						}
						if (w.inFloor()) {
							cnt[4]++;
							assertTrue(w.u() >= 0 && w.u() <= 1, "u outside [0, 1] on the floor: " + w);
						} else {
							assertTrue(Double.isNaN(w.u()), "u outside the floor: " + w);
						}
						if (Double.isFinite(prev) && Double.isFinite(d)) {
							cnt[0]++;
							boolean zones = Math.min(d, prev) <= band;
							if (zones) {
								cnt[1]++;
							}
							double grad = Math.abs(d - prev);
							if (grad > 1.5) {
								if (zones) {
									cnt[2]++;
								}
								grad = refinedGradient(m, x - dx, z - dz, dx, dz);
								if (Double.isInfinite(grad)) {
									cnt[5]++;
									where[2] = String.format(Locale.ROOT, "%.3f,%.3f (d %.2f)", x, z, d);
								}
							}
							int q = zones ? 0 : 1;
							if (Double.isFinite(grad) && grad > worst[q]) {
								worst[q] = grad;
								where[q] = String.format(Locale.ROOT, "%.2f,%.2f (d %.2f, W %.2f)", x, z, d, w.channelWidth());
							}
						}
						prev = d;
					}
				}
			}
			System.out.println(String.format(Locale.ROOT,
					"[channel d] %s: %d steps (%d in the belt d ≤ 200 m·k, %d above 1.5 m per 1 m), %d channel columns, %d on the floor,"
							+ " %d jumps; largest gradient in the belt %.2f m per 1 m at %s, outside the belt %.2f at %s",
					scale.id(), cnt[0], cnt[1], cnt[2], cnt[3], cnt[4], cnt[5], worst[0], where[0], worst[1], where[1]));
			assertTrue(cnt[3] > 50 && cnt[4] > 500, "too few channels and floors in the transects");
			assertTrue(cnt[5] == 0, "d discontinuous (" + cnt[5] + " jumps), e.g. at " + where[2]);
			assertTrue(worst[0] <= 10, "gradient of d in the zone belt " + worst[0] + " m per 1 m at " + where[0]);
			assertTrue(worst[1] <= 4, "gradient of d outside the zone belt " + worst[1] + " m per 1 m at " + where[1]);
			assertTrue(cnt[2] <= 0.01 * cnt[1], "too many steep steps in the zone belt: " + cnt[2] + " of " + cnt[1]);
		}
	}

	/**
	 * Gradient of d (m per 1 m) over 1 m from (x, z) in the direction (dx, dz), refined to 1/256 m and the
	 * largest substep to 1/65536 m; +∞ when the difference does not shrink with the step (a jump).
	 */
	private static double refinedGradient(LandscapeModel m, double x, double z, double dx, double dz) {
		double best = 0;
		int bi = 0;
		double p = m.sample(x, z).waters().channelDist();
		for (int q = 1; q <= 256; q++) {
			double t = q / 256.0;
			double v = m.sample(x + dx * t, z + dz * t).waters().channelDist();
			if (Math.abs(v - p) > best) {
				best = Math.abs(v - p);
				bi = q;
			}
			p = v;
		}
		if (best <= 0.1) {
			return best * 256;
		}
		double t0 = (bi - 1) / 256.0;
		double pp = m.sample(x + dx * t0, z + dz * t0).waters().channelDist();
		double fine = 0;
		for (int q = 1; q <= 256; q++) {
			double t = t0 + q / 65_536.0;
			double v = m.sample(x + dx * t, z + dz * t).waters().channelDist();
			fine = Math.max(fine, Math.abs(v - pp));
			pp = v;
		}
		return fine > 0.01 ? Double.POSITIVE_INFINITY : fine * 65_536;
	}

	/**
	 * Convex bank: in the bends of strongly meandering lowland rivers half of the banks are convex. We count
	 * channel cross-sections: for a bank column we look for the column on the other side of the channel (along the
	 * gradient of d) and check that exactly one of them is convex. The area share of the bank belt is less
	 * than 1/2, because in a tight bend the inner bank is shorter than the outer one (radius R − W/2 versus R + W/2).
	 * The side in the world is checked on every fifth cross-section: the channel wraps around the inner bank, so a
	 * circle of radius 1.5W + d around a convex bank contains more water than one around a concave bank.
	 */
	@Test
	void aboutHalfOfMeanderBanksAreConvex() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			RiverNetwork net = networkOf(m);
			double[] site = find(m, c -> c.waterKind() == WaterKind.RIVER && c.type().isLowland(),
					scale == LandscapeScale.REALISTIC ? 500 : 25);
			assertTrue(site != null, "no lowland river at scale " + scale.id());
			// {bank columns, convex, cross-sections, cross-sections with exactly one convex bank, convex in cross-sections,
			// cross-sections with different amounts of water around the banks, of which with more at the convex one}
			long[] total = new long[7];
			int reaches = 0;
			for (int order = 3; order >= 2 && reaches < 5; order--) {
				double a = order == 3 ? 20_000 * scale.meso() : 5_000 * Math.max(scale.meso(), 0.2);
				long gi = (long) Math.floor(site[0] / a);
				long gj = (long) Math.floor(site[1] / a);
				for (long i = gi - 8; i <= gi + 8 && reaches < 5; i++) {
					for (long j = gj - 8; j <= gj + 8 && reaches < 5; j++) {
						RiverNetwork.Segment s = net.segment(order, i, j);
						if (s == null || s.theta < 1.0 || s.len < 6 * s.lambda) {
							continue;
						}
						// Middle of the segment (meanders are not damped here), on the channel: the valley may be far from the segment axis.
						double tl = Math.hypot(s.dx(0.5), s.dz(0.5));
						double off = s.channelOffset(0.5);
						double cx = s.px(0.5) - s.dz(0.5) / tl * off;
						double cz = s.pz(0.5) + s.dx(0.5) / tl * off;
						if (!m.sample(cx, cz).type().isLowland()) {
							continue;
						}
						reaches++;
						double w = s.widthAt(0.5);
						double half = 1.2 * s.lambda + w;
						double step = Math.max(0.5, w / 6);
						int n = (int) (2 * half / step);
						long[] sum = IntStream.range(0, n).parallel().mapToObj(jj -> {
							long[] cnt = new long[7];
							for (int ii = 0; ii < n; ii++) {
								double x = cx - half + ii * step;
								double z = cz - half + jj * step;
								ColumnSample c = m.sample(x, z);
								ColumnSample.Waters wd = c.waters();
								double band = Math.max(0.3 * wd.channelWidth(), 1.5);
								if (c.hasWater() || !(wd.channelDist() > 0 && wd.channelDist() <= band)) {
									continue;
								}
								cnt[0]++;
								if (wd.convexBank()) {
									cnt[1]++;
								}
								// Other side of the channel: along the gradient of d, by 2d + W.
								double gx = m.sample(x + 0.5, z).waters().channelDist() - m.sample(x - 0.5, z).waters().channelDist();
								double gz = m.sample(x, z + 0.5).waters().channelDist() - m.sample(x, z - 0.5).waters().channelDist();
								double gl = Math.hypot(gx, gz);
								if (gl < 0.5) {
									continue;
								}
								double jump = 2 * wd.channelDist() + wd.channelWidth();
								ColumnSample o = m.sample(x - gx / gl * jump, z - gz / gl * jump);
								ColumnSample.Waters ow = o.waters();
								if (o.hasWater() || !(ow.channelDist() > 0 && ow.channelDist() <= 2 * band)
										|| Math.abs(ow.channelWidth() - wd.channelWidth()) > 0.05 * wd.channelWidth()) {
									continue;
								}
								cnt[2]++;
								if (wd.convexBank() != ow.convexBank()) {
									cnt[3]++;
								}
								cnt[4] += (wd.convexBank() ? 1 : 0) + (ow.convexBank() ? 1 : 0);
								if (wd.convexBank() != ow.convexBank() && (ii * 31 + jj) % 5 == 0) {
									double r = 1.5 * wd.channelWidth() + wd.channelDist();
									double here = riverAround(m, x, z, r);
									double there = riverAround(m, x - gx / gl * jump, z - gz / gl * jump, r);
									if (here != there) {
										cnt[5]++;
										if ((here > there) == wd.convexBank()) {
											cnt[6]++;
										}
									}
								}
							}
							return cnt;
						}).reduce(new long[7], (x, y) -> {
							long[] r = new long[7];
							for (int q = 0; q < 7; q++) {
								r[q] = x[q] + y[q];
							}
							return r;
						});
						for (int q = 0; q < 7; q++) {
							total[q] += sum[q];
						}
					}
				}
			}
			double areaShare = total[0] == 0 ? 0 : (double) total[1] / total[0];
			double opposite = total[2] == 0 ? 0 : (double) total[3] / total[2];
			double share = total[2] == 0 ? 0 : total[4] / (2.0 * total[2]);
			double sideOk = total[5] == 0 ? 0 : (double) total[6] / total[5];
			System.out.println(String.format(Locale.ROOT,
					"[convex bank] %s: %d segments, %d bank columns (convex %.3f of the area), %d cross-sections:"
							+ " exactly one convex bank in %.3f, convex banks %.3f; more water around the convex one"
							+ " in %.3f of %d decided",
					scale.id(), reaches, total[0], areaShare, total[2], opposite, share, sideOk, total[5]));
			assertTrue(reaches > 0 && total[2] > 200, "too few bend cross-sections: " + reaches + " segments, " + total[2]);
			assertTrue(opposite > 0.8, "a channel cross-section should have exactly one convex bank: " + opposite);
			assertTrue(share > 0.4 && share < 0.6, "share of convex banks in cross-sections " + share);
			assertTrue(total[5] >= 20 && sideOk > 0.8, "convex bank on the wrong side of the bend: " + sideOk + " of " + total[5]);
		}
	}

	/** Share of river columns in a circle of radius r around (x, z): an 8 × 24 polar grid weighted by area. */
	private static double riverAround(LandscapeModel m, double x, double z, double r) {
		double water = 0;
		for (int i = 0; i < 8; i++) {
			double rr = r * (i + 0.5) / 8;
			for (int a = 0; a < 24; a++) {
				double ang = a * Math.PI / 12;
				if (m.sample(x + rr * Math.cos(ang), z + rr * Math.sin(ang)).waterKind() == WaterKind.RIVER) {
					water += i + 0.5;
				}
			}
		}
		return water;
	}

	/**
	 * Side of the bend in the Kinoshita curve frame: at s = 0 the curvature dθ/ds is positive (a left bend, center
	 * on the +v side), at s = 0.5 negative. A point just next to the curve on the side of the bend center is inner.
	 */
	@Test
	void innerSideFollowsMeanderCurvature() {
		double theta = 1.5;
		for (double u : new double[] {0.0, 0.5}) {
			// Position of the curve across the valley at the given u: minimum of the distance.
			double v0 = 0;
			double best = Double.MAX_VALUE;
			for (int q = -400; q <= 400; q++) {
				double v = q * 0.002;
				double d = MeanderField.distance(u, v, theta);
				if (d < best) {
					best = d;
					v0 = v;
				}
			}
			boolean leftInner = u == 0.0;
			assertTrue(MeanderField.innerSide(u, v0 + 0.03, theta) == leftInner, "side +v at u = " + u);
			assertTrue(MeanderField.innerSide(u, v0 - 0.03, theta) != leftInner, "side -v at u = " + u);
		}
		assertFalse(MeanderField.innerSide(0.0, 0.03, 0.0), "without meanders no bank is convex");
	}

	/**
	 * Decision D4a (step K4c, round 1 of the review, docs/m2/poprawka-geometrii.md): the sweep cut changes the terrain of
	 * the projection only near ties. On random land points of both scales ({@value #SWEEP_POINTS} uniform in a large
	 * square and {@value #SWEEP_WINDOW_POINTS} in each window of {@link SurfaceContinuityTest}), the river terrain with
	 * the sweep cut ({@code query}, which evaluates every cross-section; there is no skip any more) is compared with the
	 * terrain of the projection alone ({@code queryWithoutSweep}). A point is <em>clear</em> when every segment that cuts
	 * there, with or without the sweep cut, has one dominant arm (distinctness g ≥ {@value #CLEAR_DISTINCTNESS}, share of
	 * the weights of the soft averages ≥ {@value #CLEAR_SHARE}, an end of the segment only when the point lies at least
	 * {@code END_BLEND} behind it) <em>and</em> no other part of its curve is nearly as close: at every sample t = j / 256
	 * outside the neighborhood of the arm (|t − t*| ≥ 1/64 or farther from the foot than 0.5 · D* + 10 m·k, D* the
	 * distance from the curve), the point is not nearly level with the curve, |along| ≥ {@value #CLEAR_ALONG} · |X − P(t)|.
	 * The first version of this test used the arm alone and compared the terrain with a version of the sweep cut that
	 * skipped exactly those points, so it could not fail (review of K4c); a point level with the end of a short segment 3 m
	 * farther than the arm, or next to a near cusp of the curve, is a tie, where the sweep cut is meant to act. At every
	 * clear point the two terrains differ by at most {@code SWEEP_TOLERANCE}; elsewhere the sweep cut may deepen the
	 * terrain, but by more than 1 m on at most {@value #SWEEP_CHANGED_LIMIT} of the land (the first version of K4b: 4.75%
	 * at realistic and 16.9% at gameplay scale, up to 136 m, far from any tie).
	 */
	@Test
	void sweepCutKeepsWellConditionedTerrain() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			RiverNetwork net = networkOf(m);
			java.util.Random rnd = new java.util.Random(77);
			List<double[]> points = new ArrayList<>();
			double area = sc == LandscapeScale.REALISTIC ? 60_000 : 12_000;
			for (int k = 0; k < SWEEP_POINTS; k++) {
				points.add(new double[] {(rnd.nextDouble() * 2 - 1) * area, (rnd.nextDouble() * 2 - 1) * area});
			}
			for (SurfaceContinuityTest.Window w : SurfaceContinuityTest.windows().filter(w -> w.scale() == sc).toList()) {
				for (int k = 0; k < SWEEP_WINDOW_POINTS; k++) {
					points.add(new double[] {w.cx() + (rnd.nextDouble() * 2 - 1) * w.radius(),
							w.cz() + (rnd.nextDouble() * 2 - 1) * w.radius()});
				}
			}
			double k = sc.local();
			// Per land point: {x, z, terrain with the sweep cut − without it, clear (1/0), segments that cut there}.
			double[][] res = points.parallelStream().map(p -> {
				double x = p[0];
				double z = p[1];
				if (m.coastDistance(x, z) < 0) {
					return null;
				}
				LandscapeModel.Blend b = m.blend(x, z);
				double lowland = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU)
						+ b.weight(LandscapeType.OLD_GLACIAL_PLAIN) + b.weight(LandscapeType.COASTLAND);
				double foothills = b.weight(LandscapeType.FOOTHILLS);
				double mountains = b.weight(LandscapeType.BESKIDS);
				double terrain = m.landElevation(x, z);
				double d = net.query(x, z, terrain, lowland, foothills, mountains).terrain()
						- net.queryWithoutSweep(x, z, terrain, lowland, foothills, mountains).terrain();
				int cutting = 0;
				boolean clear = true;
				for (RiverNetwork.Segment s : net.frameSegments(x, z)) {
					List<RiverNetwork.Segment> one = List.of(s);
					if (net.querySegments(x, z, terrain, lowland, foothills, mountains, one, true).terrain() >= terrain
							&& net.querySegments(x, z, terrain, lowland, foothills, mountains, one, false).terrain() >= terrain) {
						continue;
					}
					cutting++;
					double[] a = net.armInfo(s, x, z);
					clear &= a[1] >= CLEAR_DISTINCTNESS && a[2] >= CLEAR_SHARE && (a[3] == 0 || a[4] >= RiverNetwork.END_BLEND)
							&& !nearlyLevelElsewhere(s, x, z, a[5], a[6], k);
				}
				return new double[] {x, z, d, clear && cutting > 0 ? 1 : 0, cutting};
			}).filter(v -> v != null).toArray(double[][]::new);
			long land = res.length;
			long clear = 0;
			long cutting = 0;
			long changed = 0;
			long changed1 = 0;
			double worstClear = 0;
			String worstAt = "-";
			double max = 0;
			for (double[] r : res) {
				double ad = Math.abs(r[2]);
				cutting += r[4] > 0 ? 1 : 0;
				changed += ad > RiverNetwork.SWEEP_TOLERANCE ? 1 : 0;
				changed1 += ad > 1 ? 1 : 0;
				max = Math.max(max, ad);
				if (r[3] > 0) {
					clear++;
					if (ad > worstClear) {
						worstClear = ad;
						worstAt = String.format(Locale.ROOT, "(%.2f, %.2f)", r[0], r[1]);
					}
				}
			}
			System.out.printf(Locale.ROOT, "[sweep] %s: %d land points, %d cut by a valley, %d clear; largest change at a clear "
					+ "point %.4f m at %s (limit %.2f m); changed by > %.2f m: %d, by > 1 m: %d (%.2f%% of the land, limit "
					+ "%.1f%%), largest %.2f m%n", sc.id(), land, cutting, clear, worstClear, worstAt, RiverNetwork.SWEEP_TOLERANCE,
					RiverNetwork.SWEEP_TOLERANCE, changed, changed1, 100.0 * changed1 / land, 100 * SWEEP_CHANGED_LIMIT, max);
			assertTrue(clear > 1_000, sc.id() + ": too few clear points: " + clear);
			assertTrue(worstClear <= RiverNetwork.SWEEP_TOLERANCE, sc.id() + ": the sweep cut changes a clear point by "
					+ worstClear + " m at " + worstAt);
			assertTrue(changed1 <= SWEEP_CHANGED_LIMIT * land, sc.id() + ": the sweep cut changes " + changed1 + " of " + land
					+ " land points by more than 1 m");
		}
	}

	/**
	 * Whether another part of the curve of s is nearly as close to (x, z) as the arm at tArm (distance dArm from the
	 * curve): {@link #sweepCutKeepsWellConditionedTerrain}. {@code k} is the local scale (m·k).
	 */
	static boolean nearlyLevelElsewhere(RiverNetwork.Segment s, double x, double z, double tArm, double dArm, double k) {
		double ax = s.px(tArm);
		double az = s.pz(tArm);
		for (int j = 0; j <= 256; j++) {
			double t = j / 256.0;
			double px = s.px(t);
			double pz = s.pz(t);
			if (Math.abs(t - tArm) < 1.0 / 64 && Math.hypot(px - ax, pz - az) < 0.5 * dArm + 10 * k) {
				continue;
			}
			double vx = s.dx(t);
			double vz = s.dz(t);
			double ex = x - px;
			double ez = z - pz;
			double along = Math.abs(ex * vx + ez * vz) / Math.max(1e-12, Math.hypot(vx, vz));
			if (along < CLEAR_ALONG * Math.hypot(ex, ez)) {
				return true;
			}
		}
		return false;
	}

	/** Points of {@link #sweepCutKeepsWellConditionedTerrain} per scale in the large square and in each window. */
	static final int SWEEP_POINTS = 6_000;
	static final int SWEEP_WINDOW_POINTS = 300;
	/** A clear arm of {@link #sweepCutKeepsWellConditionedTerrain}: distinctness and share of the weights. */
	static final double CLEAR_DISTINCTNESS = 0.5;
	static final double CLEAR_SHARE = 0.999;
	/** {@link #nearlyLevelElsewhere}: the least |along| / |X − P(t)| of the rest of the curve. */
	static final double CLEAR_ALONG = 0.2;
	/** Largest share of the land changed by the sweep cut by more than 1 m. */
	static final double SWEEP_CHANGED_LIMIT = 0.005;

	/**
	 * Round 1 of the review of K4c: the places of the reviewed scarps and ribs of the sweep cut, each on a dense grid
	 * ({@value #SCARP_STEP} m over a square of {@value #SCARP_SIDE} m). The first K4c skipped the sweep cut where the
	 * projection had one clear arm, and left vertical scarps along the edge of the skipped region (16.5 m at (27656,
	 * 2596), 11.6 m at (26647, 1308), 20.5 m at (70804, −31979), 20.1 m at (5409, −33312), 3.6 m at (−1400, 2542) at
	 * gameplay scale, 18.3 m at (152761, 1054313) and 2.9 m at (126918, 1032080) at realistic scale); its maximum over a
	 * fixed family of node cross-sections left fields of straight ribs at ties ((27130, 2156), (70385, −31570), (28492,
	 * 872), the massifs at (−216920, 247938), (−217788, 244544), (70403, −32435)); the random transects of
	 * {@link SurfaceContinuityTest} missed both. On dry land outside standing water: no discontinuity of the river
	 * terrain (every pair of neighbors that differs by more than {@value #FAMILY_PAIR} m and whose midpoint does not
	 * split the difference is bisected as in {@link #sweepCutIsContinuousWhereItsFamilyChanges}, at most
	 * {@value #FAMILY_JUMP} m left; the scarps were 3–20 m between columns 0.25 m apart, and the first threshold of 3 m per
	 * 0.5 m did not see steps of 0.5–2 m, re-review of K4c) and at most {@code SurfaceContinuityTest.D4B_BLOCKS} blocks per
	 * block over 1 m (decision D4b).
	 */
	@Test
	void sweepCutIsContinuousAtTheReviewedScarps() {
		double[][] gameplay = {{27_656.4, 2_595.9}, {26_646.5, 1_308.4}, {70_803.6, -31_978.6}, {5_409.2, -33_311.8},
				{-1_400.1, 2_541.6}, {27_130, 2_156}, {70_385, -31_570}, {28_492, 872}, {-216_920.2, 247_937.8},
				{-217_787.8, 244_544.2}, {70_403, -32_435}};
		double[][] realistic = {{152_760.95, 1_054_313}, {126_918, 1_032_080}};
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.GAMEPLAY, LandscapeScale.REALISTIC}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			pl.polishforests.worldgen.chunk.VerticalScale vs = sc == LandscapeScale.GAMEPLAY
					? pl.polishforests.worldgen.chunk.VerticalScale.GAMEPLAY : pl.polishforests.worldgen.chunk.VerticalScale.REAL;
			int n = (int) Math.round(SCARP_SIDE / SCARP_STEP) + 1;
			int per = (int) Math.round(1 / SCARP_STEP);
			for (double[] c : sc == LandscapeScale.GAMEPLAY ? gameplay : realistic) {
				// Rows of {surface, raw surface, dry land (1/0)}.
				double[][][] rows = IntStream.range(0, n).parallel().mapToObj(j -> {
					double[][] row = new double[3][n];
					for (int i = 0; i < n; i++) {
						ColumnSample s = m.sample(c[0] - SCARP_SIDE / 2 + i * SCARP_STEP, c[1] - SCARP_SIDE / 2 + j * SCARP_STEP);
						row[0][i] = s.surface();
						row[1][i] = s.terrain().rawSurface();
						row[2][i] = SurfaceContinuityTest.dryLand(s) ? 1 : 0;
					}
					return row;
				}).toArray(double[][][]::new);
				double x0 = c[0] - SCARP_SIDE / 2;
				double z0 = c[1] - SCARP_SIDE / 2;
				RiverNetwork net = networkOf(m);
				// Re-review of K4c: every pair of dry neighbors that differs by more than FAMILY_PAIR and whose midpoint
				// does not split the difference is bisected on the river terrain (a threshold of 3 m per 0.5 m did not see
				// steps of 0.5–2 m); per pair {step after bisection, x, z}.
				double[][] steps = IntStream.range(0, 2 * n * n).parallel().mapToObj(q -> {
					int k = q >> 1;
					int i = k % n;
					int j = k / n;
					int i1 = (q & 1) == 0 ? i + 1 : i;
					int j1 = (q & 1) == 0 ? j : j + 1;
					if (i1 >= n || j1 >= n || rows[j][2][i] == 0 || rows[j1][2][i1] == 0
							|| Math.abs(rows[j1][0][i1] - rows[j][0][i]) <= FAMILY_PAIR) {
						return null;
					}
					return bisectedStep(m, net, x0 + i * SCARP_STEP, z0 + j * SCARP_STEP, x0 + i1 * SCARP_STEP,
							z0 + j1 * SCARP_STEP);
				}).filter(v -> v != null).toArray(double[][]::new);
				double jump = 0;
				String jumpAt = "-";
				for (double[] s : steps) {
					if (s[0] > jump) {
						jump = s[0];
						jumpAt = String.format(Locale.ROOT, "(%.4f, %.4f)", s[1], s[2]);
					}
				}
				double blocks = 0;
				double valley = 0;
				String blocksAt = "-";
				for (int j = 0; j < n; j++) {
					for (int i = 0; i < n; i++) {
						if (rows[j][2][i] == 0) {
							continue;
						}
						for (int dir = 0; dir < 2; dir++) {
							int i2 = dir == 0 ? i + per : i;
							int j2 = dir == 0 ? j : j + per;
							double x = x0 + i * SCARP_STEP;
							double z = z0 + j * SCARP_STEP;
							if (i2 < n && j2 < n && rows[j2][2][i2] > 0) {
								double bl = Math.abs(vs.blocksForMeters(rows[j2][0][i2]) - vs.blocksForMeters(rows[j][0][i]));
								if (bl > blocks) {
									blocks = bl;
									blocksAt = String.format(Locale.ROOT, "(%.2f, %.2f)", x, z);
								}
								valley = Math.max(valley, Math.abs(rows[j2][0][i2] - rows[j][0][i])
										- Math.abs(rows[j2][1][i2] - rows[j][1][i]));
							}
						}
					}
				}
				System.out.printf(Locale.ROOT, "[scarps] %s (%.1f, %.1f): %d pairs bisected, largest remaining step of the river "
						+ "terrain %.6f m at %s, %.2f blocks per block at %s, valley-made step %.2f m per 1 m%n", sc.id(), c[0], c[1],
						steps.length, jump, jumpAt, blocks, blocksAt, valley);
				assertTrue(jump <= FAMILY_JUMP, String.format(Locale.ROOT, "%s (%.1f, %.1f): the river terrain steps by %.3f m at %s",
						sc.id(), c[0], c[1], jump, jumpAt));
				assertTrue(blocks <= SurfaceContinuityTest.D4B_BLOCKS, String.format(Locale.ROOT,
						"%s (%.1f, %.1f): %.2f blocks per block at %s (decision D4b)", sc.id(), c[0], c[1], blocks, blocksAt));
			}
		}
	}

	/**
	 * Round 1 of the review of K4c: the prune of the sweep cut ({@code Section.notDeeperThanArm}, which replaced the unsafe
	 * skip of the whole sweep cut at a "clear" arm) changes nothing. It drops only cross-sections provably not deeper than
	 * the single arm of the projection, i.e. not deeper than the cut of the projection, below which the sweep cut does not
	 * act, so the river terrain with the prune ({@code query}) and without it ({@code queryWithoutPrune}) must be
	 * identical (not merely within {@code SWEEP_TOLERANCE}). Dense grids: every mountain window of
	 * {@link SurfaceContinuityTest} at gameplay scale and the Beskids and the massif window at realistic scale
	 * ({@value #PRUNE_GRID} × {@value #PRUNE_GRID} columns each), and the places of the reviewed scarps of
	 * {@link #sweepCutIsContinuousAtTheReviewedScarps} ({@value #SCARP_STEP} m over {@value #SCARP_SIDE} m), where the
	 * removed skip made scarps of 3–20 m.
	 */
	@Test
	void sweepCutPruneIsExact() {
		double[][] gameplay = {{27_656.4, 2_595.9}, {26_646.5, 1_308.4}, {70_803.6, -31_978.6}, {5_409.2, -33_311.8},
				{-1_400.1, 2_541.6}, {27_130, 2_156}, {70_385, -31_570}, {28_492, 872}};
		double[][] realistic = {{152_760.95, 1_054_313}, {126_918, 1_032_080}};
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.GAMEPLAY, LandscapeScale.REALISTIC}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			RiverNetwork net = networkOf(m);
			// Grids: {center x, center z, half side, step}.
			List<double[]> grids = new ArrayList<>();
			SurfaceContinuityTest.windows().filter(w -> w.scale() == sc
					&& (w.name().contains("beskids") || w.name().contains("massif")))
					.forEach(w -> grids.add(new double[] {w.cx(), w.cz(), w.radius(), 2 * w.radius() / (PRUNE_GRID - 1)}));
			for (double[] c : sc == LandscapeScale.GAMEPLAY ? gameplay : realistic) {
				grids.add(new double[] {c[0], c[1], SCARP_SIDE / 2, SCARP_STEP});
			}
			long columns = 0;
			long cut = 0;
			long differ = 0;
			double max = 0;
			String at = "-";
			for (double[] g : grids) {
				int n = (int) Math.round(2 * g[2] / g[3]) + 1;
				// Per column: {x, z, |difference|, cut by a valley (1/0)}.
				double[][] res = IntStream.range(0, n * n).parallel().mapToObj(k -> {
					double x = g[0] - g[2] + (k % n) * g[3];
					double z = g[1] - g[2] + (k / n) * g[3];
					if (m.coastDistance(x, z) < 0) {
						return null;
					}
					LandscapeModel.Blend b = m.blend(x, z);
					double lowland = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU)
							+ b.weight(LandscapeType.OLD_GLACIAL_PLAIN) + b.weight(LandscapeType.COASTLAND);
					double foothills = b.weight(LandscapeType.FOOTHILLS);
					double mountains = b.weight(LandscapeType.BESKIDS);
					double terrain = m.landElevation(x, z);
					double with = net.query(x, z, terrain, lowland, foothills, mountains).terrain();
					double without = net.queryWithoutPrune(x, z, terrain, lowland, foothills, mountains).terrain();
					return new double[] {x, z, Math.abs(with - without), with < terrain ? 1 : 0};
				}).filter(v -> v != null).toArray(double[][]::new);
				for (double[] r : res) {
					columns++;
					cut += (long) r[3];
					if (r[2] > 0) {
						differ++;
					}
					if (r[2] > max) {
						max = r[2];
						at = String.format(Locale.ROOT, "(%.2f, %.2f)", r[0], r[1]);
					}
				}
			}
			System.out.printf(Locale.ROOT, "[prune] %s: %d grids, %d land columns, %d cut by a valley; differ with and without "
					+ "the prune: %d, largest %.6f m at %s%n", sc.id(), grids.size(), columns, cut, differ, max, at);
			assertTrue(cut > 100_000, sc.id() + ": too few columns cut by a valley: " + cut);
			assertTrue(differ == 0, String.format(Locale.ROOT, "%s: the prune of the sweep cut changes %d columns, by up to "
					+ "%.4f m at %s", sc.id(), differ, max, at));
		}
	}

	/** {@link #sweepCutPruneIsExact}: columns per side of the grid of a window. */
	static final int PRUNE_GRID = 401;

	/**
	 * Round 2 of the review of K4c: the maximum of the sweep cut stays continuous where its family of cross-sections
	 * changes. The extrema of f = (P − X) · P' (the roots of f') are members of the family; a member that appeared with
	 * its full value made a step: (a) a pair of roots of f' born where f'' = 0 (0.58 m at gameplay scale (−216251,
	 * 246905), 12.8 m at realistic scale (156980.5, 1059412) under a lake), (b) a root of f' leaving through an end of
	 * the segment, where the node at the end is measured as the end arm (2.0 m at realistic scale (132704, 1042536.5),
	 * 0.5 m at (131841.4, 1086475)). The river terrain of {@code query} on a grid of {@value #FAMILY_STEP} m over
	 * {@value #FAMILY_SIDE} m around each place: every pair of neighbors that differs by more than
	 * {@value #FAMILY_PAIR} m and whose midpoint does not split the difference is bisected
	 * ({@value #FAMILY_BISECTIONS} halvings, 1e-11 m); the remaining step must be at most {@value #FAMILY_JUMP} m (a
	 * continuous surface leaves at most its slope times 1e-11 m; the threshold of 3 m per 0.5 m of
	 * {@link #sweepCutIsContinuousAtTheReviewedScarps} does not see steps of 0.5–2 m).
	 */
	@Test
	void sweepCutIsContinuousWhereItsFamilyChanges() {
		double[][] gameplay = {{-216_251.0, 246_904.75}};
		double[][] realistic = {{132_704.0, 1_042_536.5}, {131_841.4, 1_086_475}, {156_980.5, 1_059_412}};
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.GAMEPLAY, LandscapeScale.REALISTIC}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			RiverNetwork net = networkOf(m);
			int n = (int) Math.round(FAMILY_SIDE / FAMILY_STEP) + 1;
			for (double[] c : sc == LandscapeScale.GAMEPLAY ? gameplay : realistic) {
				double x0 = c[0] - FAMILY_SIDE / 2;
				double z0 = c[1] - FAMILY_SIDE / 2;
				double[] h = new double[n * n];
				IntStream.range(0, n * n).parallel().forEach(k -> h[k] = riverTerrain(m, net, x0 + (k % n) * FAMILY_STEP,
						z0 + (k / n) * FAMILY_STEP));
				// Per pair: {step after bisection, x, z}.
				double[][] steps = IntStream.range(0, 2 * n * n).parallel().mapToObj(q -> {
					int k = q >> 1;
					int i = k % n;
					int j = k / n;
					int i1 = (q & 1) == 0 ? i + 1 : i;
					int j1 = (q & 1) == 0 ? j : j + 1;
					if (i1 >= n || j1 >= n) {
						return null;
					}
					if (Math.abs(h[j1 * n + i1] - h[k]) <= FAMILY_PAIR) {
						return null;
					}
					return bisectedStep(m, net, x0 + i * FAMILY_STEP, z0 + j * FAMILY_STEP, x0 + i1 * FAMILY_STEP,
							z0 + j1 * FAMILY_STEP);
				}).filter(v -> v != null).toArray(double[][]::new);
				double worst = 0;
				String at = "-";
				for (double[] s : steps) {
					if (s[0] > worst) {
						worst = s[0];
						at = String.format(Locale.ROOT, "(%.4f, %.4f)", s[1], s[2]);
					}
				}
				System.out.printf(Locale.ROOT, "[family] %s (%.1f, %.1f): %d pairs bisected, largest remaining step %.6f m at %s%n",
						sc.id(), c[0], c[1], steps.length, worst, at);
				assertTrue(worst <= FAMILY_JUMP, String.format(Locale.ROOT, "%s (%.1f, %.1f): the river terrain steps by %.3f m at %s",
						sc.id(), c[0], c[1], worst, at));
			}
		}
	}

	/**
	 * The step of the river terrain between (xa, za) and (xb, zb) that is left after {@value #FAMILY_BISECTIONS} halvings
	 * towards the larger half of the difference, less the step of the terrain before the valleys
	 * ({@code landElevation}) over the same final interval, {step, x, z}; null when the terrain differs by at most
	 * {@value #FAMILY_PAIR} m or the midpoint splits the difference (no more than 0.7 of it on either side). A seam of
	 * the terrain itself (until step K6 the 3 × 3 window of the region blend, A16: 2 cm at gameplay scale (28464,
	 * 887.52)) is not a step of the valleys.
	 */
	private static double[] bisectedStep(LandscapeModel m, RiverNetwork net, double xa, double za, double xb, double zb) {
		double ha = riverTerrain(m, net, xa, za);
		double hb = riverTerrain(m, net, xb, zb);
		if (Math.abs(hb - ha) <= FAMILY_PAIR) {
			return null;
		}
		double hm = riverTerrain(m, net, 0.5 * (xa + xb), 0.5 * (za + zb));
		if (Math.max(Math.abs(hm - ha), Math.abs(hb - hm)) <= 0.7 * Math.abs(hb - ha)) {
			return null;
		}
		for (int it = 0; it < FAMILY_BISECTIONS; it++) {
			double xm = 0.5 * (xa + xb);
			double zm = 0.5 * (za + zb);
			double v = riverTerrain(m, net, xm, zm);
			if (Math.abs(v - ha) >= Math.abs(hb - v)) {
				xb = xm;
				zb = zm;
				hb = v;
			} else {
				xa = xm;
				za = zm;
				ha = v;
			}
		}
		double raw = Math.abs(m.landElevation(xb, zb) - m.landElevation(xa, za));
		return new double[] {Math.abs(hb - ha) - raw, 0.5 * (xa + xb), 0.5 * (za + zb)};
	}

	/** River terrain of {@code RiverNetwork.query} at (x, z), with the landscape shares of the model there. */
	private static double riverTerrain(LandscapeModel m, RiverNetwork net, double x, double z) {
		LandscapeModel.Blend b = m.blend(x, z);
		double lowland = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU)
				+ b.weight(LandscapeType.OLD_GLACIAL_PLAIN) + b.weight(LandscapeType.COASTLAND);
		return net.query(x, z, m.landElevation(x, z), lowland, b.weight(LandscapeType.FOOTHILLS),
				b.weight(LandscapeType.BESKIDS)).terrain();
	}

	/** {@link #sweepCutIsContinuousWhereItsFamilyChanges}: grid side and step (m), the pairs bisected and the limit. */
	static final double FAMILY_SIDE = 24;
	static final double FAMILY_STEP = 0.5;
	static final double FAMILY_PAIR = 0.02;
	static final int FAMILY_BISECTIONS = 36;
	static final double FAMILY_JUMP = 0.01;

	/** {@link #sweepCutIsContinuousAtTheReviewedScarps}: grid step and side (m). */
	static final double SCARP_STEP = 0.5;
	static final double SCARP_SIDE = 80;

	/**
	 * K4.9 (docs/m2/poprawka-geometrii.md): the culling of segments in {@code RiverNetwork.query} (the box of influence
	 * of the tile list and the chord frame) changes nothing visible. On random points of both scales (uniform in a large
	 * square and in the windows of {@link SurfaceContinuityTest}) the query with culling and without it (every segment
	 * of the tile radius) must give identical terrain, water level, bank, valley weight and floor flag; where the column
	 * is in reach of a valley (valley weight &gt; 0 or on a floor) also the dominant valley and its fields (order, u, floor
	 * half-width, gradient, source), and the channel of the dominant valley (F2) and the nearest channel when nearer
	 * than the reach of the waterside zones (300 m·k). Review of K5: also the fields of the standing waters (the gap to a
	 * valley up to the longest end of a tunnel valley lake, the gap to a sink lake, the oxbow lake and its ring).
	 */
	@Test
	void segmentCullingIsInvisible() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			RiverNetwork net = networkOf(m);
			java.util.Random rnd = new java.util.Random(409);
			List<double[]> points = new ArrayList<>();
			// 20 000 points per scale, as in the design (step K4c; K4: 10 000 while the leak of TileCache.owner kept the
			// networks in memory), 400 in each window and the rest in a moderate square: every point builds the segments of
			// the whole tile radius (hundreds), so points spread over a much larger area would fill the segment caches.
			List<SurfaceContinuityTest.Window> windows = SurfaceContinuityTest.windows().filter(w -> w.scale() == sc).toList();
			double area = sc == LandscapeScale.REALISTIC ? 60_000 : 12_000;
			for (int k = 0; k < CULLING_POINTS - 400 * windows.size(); k++) {
				points.add(new double[] {(rnd.nextDouble() * 2 - 1) * area, (rnd.nextDouble() * 2 - 1) * area});
			}
			for (SurfaceContinuityTest.Window w : windows) {
				for (int k = 0; k < 400; k++) {
					points.add(new double[] {w.cx() + (rnd.nextDouble() * 2 - 1) * w.radius(),
							w.cz() + (rnd.nextDouble() * 2 - 1) * w.radius()});
				}
			}
			double zones = 300 * sc.local();
			// {compared, in reach of a valley, differences}
			long[] cnt = new long[3];
			String[] first = {null};
			points.parallelStream().forEach(p -> {
				double x = p[0];
				double z = p[1];
				if (m.coastDistance(x, z) < 0) {
					return;
				}
				LandscapeModel.Blend b = m.blend(x, z);
				double lowland = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU)
						+ b.weight(LandscapeType.OLD_GLACIAL_PLAIN) + b.weight(LandscapeType.COASTLAND);
				double foothills = b.weight(LandscapeType.FOOTHILLS);
				double mountains = b.weight(LandscapeType.BESKIDS);
				double terrain = m.landElevation(x, z);
				RiverNetwork.RiverHit a = net.query(x, z, terrain, lowland, foothills, mountains);
				RiverNetwork.RiverHit u = net.querySegments(x, z, terrain, lowland, foothills, mountains,
						net.tileRadiusSegments(x, z));
				List<String> diff = new ArrayList<>();
				same(diff, "terrain", a.terrain(), u.terrain());
				same(diff, "valleyWeight", a.valleyWeight(), u.valleyWeight());
				same(diff, "bankLevel", a.bankLevel(), u.bankLevel());
				same(diff, "waterLevel", a.waterLevel(), u.waterLevel());
				same(diff, "channelBottom", a.channelBottom(), u.channelBottom());
				same(diff, "inFloor", a.inFloor() ? 1 : 0, u.inFloor() ? 1 : 0);
				// K5 (review): the fields of the standing waters. The gap to a valley matters to the tunnel valley lakes
				// only up to the end of their lens (TUNNEL_END_GAP + TUNNEL_END_MAX m·k), the oxbow lake in its ring.
				double gapCap = (LandscapeModel.TUNNEL_END_GAP + LandscapeModel.TUNNEL_END_MAX) * sc.local();
				same(diff, "floorGap", Math.min(a.floorGap(), gapCap), Math.min(u.floorGap(), gapCap));
				same(diff, "lakeGap", a.lakeGap(), u.lakeGap());
				same(diff, "oxbowLevel", a.oxbowLevel(), u.oxbowLevel());
				same(diff, "oxbowDepth", a.oxbowDepth(), u.oxbowDepth());
				same(diff, "oxbowMirror", a.oxbowMirror(), u.oxbowMirror());
				if (Double.isFinite(a.oxbowShore()) || Double.isFinite(u.oxbowShore())) {
					same(diff, "oxbowShore", a.oxbowShore(), u.oxbowShore());
				}
				boolean reach = a.valleyWeight() > 0 || a.inFloor();
				if (reach) {
					same(diff, "order", a.order(), u.order());
					same(diff, "floorU", a.floorU(), u.floorU());
					same(diff, "floorHalf", a.floorHalf(), u.floorHalf());
					same(diff, "slope", a.slope(), u.slope());
					same(diff, "source", a.source() ? 1 : 0, u.source() ? 1 : 0);
					if (Math.min(a.floorChannelDist(), u.floorChannelDist()) < zones) {
						same(diff, "floorChannelDist", a.floorChannelDist(), u.floorChannelDist());
						same(diff, "floorChannelWidth", a.floorChannelWidth(), u.floorChannelWidth());
						same(diff, "floorChannelLevel", a.floorChannelLevel(), u.floorChannelLevel());
						same(diff, "floorChannelGradient", a.floorChannelGradient(), u.floorChannelGradient());
					}
				}
				if (Math.min(a.channelDist(), u.channelDist()) < zones) {
					same(diff, "channelDist", a.channelDist(), u.channelDist());
					same(diff, "channelWidth", a.channelWidth(), u.channelWidth());
					same(diff, "channelLevel", a.channelLevel(), u.channelLevel());
				}
				synchronized (cnt) {
					cnt[0]++;
					cnt[1] += reach ? 1 : 0;
					if (!diff.isEmpty()) {
						cnt[2]++;
						if (first[0] == null) {
							first[0] = String.format(Locale.ROOT, "(%.2f, %.2f): %s", x, z, diff);
						}
					}
				}
			});
			System.out.printf(Locale.ROOT, "[culling] %s: %d points (%d in reach of a valley), %d differ%s%n", sc.id(), cnt[0],
					cnt[1], cnt[2], first[0] == null ? "" : ", e.g. " + first[0]);
			assertTrue(cnt[1] > 1_000, sc.id() + ": too few points in reach of a valley: " + cnt[1]);
			assertTrue(cnt[2] == 0, sc.id() + ": the culling of segments is visible in " + cnt[2] + " points, e.g. " + first[0]);
		}
	}

	/** Points per scale of {@link #segmentCullingIsInvisible}. */
	static final int CULLING_POINTS = 20_000;

	private static void same(List<String> diff, String name, double a, double b) {
		if (Double.compare(a, b) != 0) {
			diff.add(name + " " + a + " vs " + b);
		}
	}

	/**
	 * G1B (step K4): the valley axis (the curve shifted by the bends, {@code wanderAt}) has no kink at the nodes where a
	 * watercourse continues into the next segment of the same order (the main tributary). Before K4 the axis broke there
	 * by 15–72° (median 15–22°), because the bends had a nonzero slope at the ends. Measured on all segments of the given
	 * grid ranges of both scales, from the axis directions 10⁻⁴ of the segment before and after the node: 90th percentile
	 * at most 1°, maximum at most 3°.
	 */
	@Test
	void valleyAxisIsSmoothAtNodes() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			RiverNetwork net = networkOf(m);
			List<Double> kinks = new ArrayList<>();
			for (int order = 3; order >= 1; order--) {
				int r = order == 3 ? 10 : order == 2 ? 24 : 40;
				java.util.Map<Long, RiverNetwork.Segment> byStart = new java.util.HashMap<>();
				List<RiverNetwork.Segment> all = new ArrayList<>();
				for (long i = -r; i <= r; i++) {
					for (long j = -r; j <= r; j++) {
						RiverNetwork.Segment s = net.segment(order, i, j);
						if (s != null) {
							byStart.put(Double.doubleToLongBits(s.x0) * 31 + Double.doubleToLongBits(s.z0), s);
							all.add(s);
						}
					}
				}
				for (RiverNetwork.Segment s : all) {
					RiverNetwork.Segment next = byStart.get(Double.doubleToLongBits(s.x1) * 31 + Double.doubleToLongBits(s.z1));
					if (next != null && next.x0 == s.x1 && next.z0 == s.z1) {
						double[] a = axisDirection(s, 1 - 1e-4, 1);
						double[] b = axisDirection(next, 0, 1e-4);
						kinks.add(Math.toDegrees(Math.acos(Math.clamp(a[0] * b[0] + a[1] * b[1], -1.0, 1.0))));
					}
				}
			}
			kinks.sort(null);
			int n = kinks.size();
			double p90 = kinks.get((int) (0.9 * (n - 1)));
			double max = kinks.get(n - 1);
			System.out.printf(Locale.ROOT, "[axis kinks] %s: %d nodes, median %.3f deg, p90 %.3f deg, max %.3f deg (limits 1 and 3 deg)%n", sc.id(),
					n, kinks.get(n / 2), p90, max);
			assertTrue(n > 500, sc.id() + ": too few nodes: " + n);
			assertTrue(p90 <= 1.0 && max <= 3.0, sc.id() + ": the valley axis breaks at the nodes: p90 " + p90 + " deg, max " + max + " deg");
		}
	}

	/** Unit direction of the valley axis between t0 and t1 of the segment. */
	private static double[] axisDirection(RiverNetwork.Segment s, double t0, double t1) {
		double[] a = axisPoint(s, t0);
		double[] b = axisPoint(s, t1);
		double l = Math.hypot(b[0] - a[0], b[1] - a[1]);
		return new double[] {(b[0] - a[0]) / l, (b[1] - a[1]) / l};
	}

	private static double[] axisPoint(RiverNetwork.Segment s, double t) {
		double tl = Math.hypot(s.dx(t), s.dz(t));
		double w = s.wanderAt(t);
		return new double[] {s.px(t) - s.dz(t) / tl * w, s.pz(t) + s.dx(t) / tl * w};
	}

	/**
	 * A5 (step K4): the valley of a river of order 2–3 starts with a rounded head instead of a floor of full width cut off
	 * by a straight line perpendicular to the axis at the source (M1: e.g. 260 m wide; every node of order 2–3 is a
	 * source, so medium and large rivers started that way). For the sources of order 2 and 3 in a grid range of both
	 * scales, with F the full half-width of the floor at the source (w/2 + fpFactor·w + fpBase from the landscape shares
	 * at the source, without the narrowing and the irregular edge), the valley of the source segment alone
	 * ({@code RiverNetwork.querySegments}, so other valleys do not count):
	 * <ul>
	 * <li>behind the source, on the extension of the axis 0.5 F away, does not cut the terrain by more than 0.5 m (the
	 * end of the segment there has t = 0 and its floor at the terrain; K4b blends the soft t of the arms, so this guards
	 * against a trough behind the head);</li>
	 * <li>the floor of its terrain (the columns where the segment's floor distance is at most its terrain half-width,
	 * {@code RiverNetwork.floorGeometry}, i.e. where its terrain is the flat floor; on a transect across the valley axis,
	 * ±(2 F + 60 m·k) every 1 m·k) is narrow at the source: its width 0.25 F from the source is at most
	 * {@value #HEAD_RATIO_MEDIAN} of its width 4 F further down in the median over the heads, and below
	 * {@value #HEAD_RATIO_HIGH} in at least {@value #HEAD_SHARE_PERCENT} percent of them (heads whose segment is shorter
	 * than 5 F or whose transects reach the sea are skipped).</li>
	 * </ul>
	 * Measured after the review of K4 (the meander belt of the floor narrows with the margin, and at a source the meanders
	 * fade in over the length over which the channel grows): median 0.22 (realistic, 672 heads, all below 0.6) and 0.21
	 * (gameplay, 457 of 459 below 0.6). Without A5 (the K3 state, a floor of full width up to the source) the median is
	 * 1.00; with the narrowed margin alone (the first version of K4) 0.61, the lowland heads 0.58, because there the
	 * meander belt is most of the floor (review of K4; docs/m2/poprawka-geometrii.md, K4).
	 */
	@Test
	void valleyHeadsAreRounded() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			RiverNetwork net = networkOf(m);
			List<RiverNetwork.Segment> heads = new ArrayList<>();
			for (int order = 2; order <= 3; order++) {
				int r = order == 3 ? 6 : 14;
				for (long i = -r; i <= r; i++) {
					for (long j = -r; j <= r; j++) {
						RiverNetwork.Segment s = net.segment(order, i, j);
						if (s != null && s.source && m.coastDistance(s.x0, s.z0) > 0) {
							heads.add(s);
						}
					}
				}
			}
			double k = sc.local();
			// {behind checked, behind cut}; the worst cut behind and its place; the ratios of the floor widths
			long[] cnt = new long[2];
			double[] worst = {0};
			String[] worstAt = {"-"};
			List<double[]> ratios = new ArrayList<>();
			heads.parallelStream().forEach(s -> {
				LandscapeModel.Blend b = m.blend(s.x0, s.z0);
				double low = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU)
						+ b.weight(LandscapeType.OLD_GLACIAL_PLAIN) + b.weight(LandscapeType.COASTLAND);
				double foothills = b.weight(LandscapeType.FOOTHILLS);
				double mountains = b.weight(LandscapeType.BESKIDS);
				double w = s.width0;
				double full = w / 2 + (5.0 * low + 1.5 * foothills + 0.3 * mountains) * w
						+ (40.0 * low + 10.0 * foothills + 2.0 * mountains) * k;
				double tl = Math.hypot(s.dx(0), s.dz(0));
				double bx = s.x0 - s.dx(0) / tl * 0.5 * full;
				double bz = s.z0 - s.dz(0) / tl * 0.5 * full;
				RiverNetwork.RiverHit behind = alone(m, net, s, bx, bz);
				double near = headFloorWidth(m, net, s, 0.25 * full, full, k);
				double far = s.len > 5 * full ? headFloorWidth(m, net, s, 4 * full, full, k) : Double.NaN;
				synchronized (cnt) {
					if (behind != null) {
						cnt[0]++;
						double cut = m.landElevation(bx, bz) - behind.terrain();
						if (cut > 0.5) {
							cnt[1]++;
						}
						if (cut > worst[0]) {
							worst[0] = cut;
							worstAt[0] = String.format(Locale.ROOT, "(%.1f, %.1f) order %d", bx, bz, s.order);
						}
					}
					if (!Double.isNaN(near) && !Double.isNaN(far) && far > 0) {
						ratios.add(new double[] {near / far, low});
					}
				}
			});
			double[] all = ratios.stream().mapToDouble(v -> v[0]).sorted().toArray();
			double[] lowland = ratios.stream().filter(v -> v[1] > 0.6).mapToDouble(v -> v[0]).sorted().toArray();
			int n = all.length;
			double median = n > 0 ? all[n / 2] : Double.NaN;
			long below = java.util.Arrays.stream(all).filter(v -> v < HEAD_RATIO_HIGH).count();
			System.out.printf(Locale.ROOT, "[valley heads] %s: %d sources of order 2-3; behind the head %d columns, cut > 0.5 m in %d "
					+ "(largest %.2f m at %s); floor width 0.25 F / 4 F from the source in %d heads: median %.2f, p75 %.2f, "
					+ "below %.2f in %d (%.0f percent); lowland heads (%d): median %.2f%n", sc.id(), heads.size(), cnt[0], cnt[1],
					worst[0], worstAt[0], n, median, n > 0 ? all[3 * n / 4] : Double.NaN, HEAD_RATIO_HIGH, below,
					100.0 * below / Math.max(1, n), lowland.length, lowland.length > 0 ? lowland[lowland.length / 2] : Double.NaN);
			assertTrue(cnt[0] > 50 && n > 50, sc.id() + ": too few valley heads checked (" + cnt[0] + ", " + n + ")");
			assertTrue(cnt[1] == 0, sc.id() + ": the terrain is cut behind " + cnt[1] + " valley heads, the deepest by "
					+ worst[0] + " m at " + worstAt[0]);
			assertTrue(median <= HEAD_RATIO_MEDIAN, sc.id() + ": the valley floor is not narrower at the source: median ratio "
					+ median);
			assertTrue(below * 100 >= (long) HEAD_SHARE_PERCENT * n, sc.id() + ": the valley floor is not narrower at the source in "
					+ (n - below) + " of " + n + " heads");
		}
	}

	/** Largest median ratio of the floor widths 0.25 F and 4 F from the source ({@link #valleyHeadsAreRounded}). */
	static final double HEAD_RATIO_MEDIAN = 0.4;
	/** Ratio below which a head counts as narrowed, and the share of heads (percent) that must be below it. */
	static final double HEAD_RATIO_HIGH = 0.6;
	static final int HEAD_SHARE_PERCENT = 80;

	/**
	 * Width (m) of the floor of the valley of s on a transect across its axis at the distance {@code along} from the
	 * source (t = along / len), ±(2 full + 60 m·k) every 1 m·k: the columns where floorDist ≤ terrainHalf
	 * ({@code RiverNetwork.floorGeometry}). NaN when part of the transect is at sea.
	 */
	private static double headFloorWidth(LandscapeModel m, RiverNetwork net, RiverNetwork.Segment s, double along,
			double full, double k) {
		double t = Math.min(1.0, along / s.len);
		double tx = s.dx(t);
		double tz = s.dz(t);
		double tl = Math.hypot(tx, tz);
		double wa = s.wanderAt(t);
		double ax = s.px(t) - tz / tl * wa;
		double az = s.pz(t) + tx / tl * wa;
		double half = 2 * full + 60 * k;
		int steps = (int) Math.ceil(2 * half / k);
		int c = 0;
		for (int i = 0; i <= steps; i++) {
			double o = -half + i * k;
			double x = ax - tz / tl * o;
			double z = az + tx / tl * o;
			if (m.coastDistance(x, z) < 0) {
				return Double.NaN;
			}
			LandscapeModel.Blend b = m.blend(x, z);
			double lowland = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU)
					+ b.weight(LandscapeType.OLD_GLACIAL_PLAIN) + b.weight(LandscapeType.COASTLAND);
			double[] f = net.floorGeometry(s, x, z, lowland, b.weight(LandscapeType.FOOTHILLS), b.weight(LandscapeType.BESKIDS));
			c += f[0] <= f[2] ? 1 : 0;
		}
		return c * k;
	}

	/** Query of the segment alone at (x, z) on land, as {@code LandscapeModel.sample} passes it; null at sea. */
	private static RiverNetwork.RiverHit alone(LandscapeModel m, RiverNetwork net, RiverNetwork.Segment s, double x, double z) {
		if (m.coastDistance(x, z) < 0) {
			return null;
		}
		LandscapeModel.Blend b = m.blend(x, z);
		double lowland = b.weight(LandscapeType.OUTWASH_PLAIN) + b.weight(LandscapeType.MORAINE_PLATEAU)
				+ b.weight(LandscapeType.OLD_GLACIAL_PLAIN) + b.weight(LandscapeType.COASTLAND);
		return net.querySegments(x, z, m.landElevation(x, z), lowland, b.weight(LandscapeType.FOOTHILLS),
				b.weight(LandscapeType.BESKIDS), List.of(s));
	}

	static RiverNetwork networkOf(LandscapeModel m) {
		try {
			var f = LandscapeModel.class.getDeclaredField("rivers");
			f.setAccessible(true);
			return (RiverNetwork) f.get(m);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	static double[] find(LandscapeModel m, Predicate<ColumnSample> test, double step) {
		for (int r = 0; r < 800; r++) {
			int n = Math.max(12, r * 2);
			for (int k = 0; k < n; k++) {
				double a = k * (2 * Math.PI / n) + r * 0.37;
				double x = Math.cos(a) * r * step;
				double z = Math.sin(a) * r * step;
				if (test.test(m.sample(x, z))) {
					return new double[] {x, z};
				}
			}
		}
		return null;
	}

	static void assertSitesContained(LandscapeModel m, double step) {
		List<double[]> sites = new ArrayList<>();
		List<String> names = new ArrayList<>();
		add(sites, names, "coast", find(m, s -> s.waterKind() == WaterKind.SEA && s.type() == LandscapeType.SEA
				&& s.surface() > -2, step));
		add(sites, names, "lagoon", find(m, s -> s.waterKind() == WaterKind.SEA && s.type() == LandscapeType.COASTLAND,
				step / 4));
		add(sites, names, "mountain stream", findStream(m));
		add(sites, names, "oxbow lake", find(m, s -> s.waterKind() == WaterKind.OXBOW, step / 4));
		add(sites, names, "lowland river", find(m, s -> s.waterKind() == WaterKind.RIVER && s.type().isLowland(),
				step / 4));
		System.out.println("Test sites: " + names);
		assertTrue(names.contains("coast") && names.contains("mountain stream") && names.contains("lowland river"),
				"not all water kinds were found: " + names);
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int q = 0; q < sites.size(); q++) {
			double[] site = sites.get(q);
			int n = 500;
			int x0 = (int) site[0] - n / 2;
			int z0 = (int) site[1] - n / 2;
			ColumnSample[][] g = new ColumnSample[n][n];
			IntStream.range(0, n).parallel().forEach(j -> {
				for (int i = 0; i < n; i++) {
					g[j][i] = m.sample(x0 + i, z0 + j);
				}
			});
			for (int j = 1; j < n - 1; j++) {
				for (int i = 1; i < n - 1; i++) {
					ColumnSample c = g[j][i];
					if (!c.hasWater()) {
						continue;
					}
					for (int[] d : dirs) {
						ColumnSample o = g[j + d[1]][i + d[0]];
						boolean ok;
						if (o.hasWater()) {
							// Rapids and cascades are allowed between channel columns (water falls into water),
							// at a mouth into a lake or the sea at most a 1 m step.
							boolean cascade = c.waterKind() == WaterKind.RIVER && o.waterKind() == WaterKind.RIVER;
							boolean flowing = c.waterKind() == WaterKind.RIVER || o.waterKind() == WaterKind.RIVER;
							ok = o.waterLevel() == c.waterLevel() || cascade
									|| flowing && Math.abs(o.waterLevel() - c.waterLevel()) <= 1;
						} else {
							ok = o.surfaceMeters() >= c.waterLevel();
						}
						assertTrue(ok, names.get(q) + ": water without a bank at " + (x0 + i) + "," + (z0 + j) + ": " + c
								+ " next to " + o);
					}
				}
			}
		}
	}

	/** Mountain stream: a point on the channel axis of an order 1 or 2 watercourse in the Beskids. */
	static double[] findStream(LandscapeModel m) {
		return findStream(m, find(m, s -> s.type() == LandscapeType.BESKIDS, m.scale() == LandscapeScale.REALISTIC ? 5_000 : 200));
	}

	/** Mountain stream near the point {@code site} in the Beskids. */
	static double[] findStream(LandscapeModel m, double[] site) {
		RiverNetwork net = networkOf(m);
		if (site == null) {
			return null;
		}
		for (int order = 1; order <= 2; order++) {
			double a = order == 1 ? 1_250 * m.scale().mountainSpacing() : 5_000 * Math.max(m.scale().meso(), 0.2);
			long gi = (long) Math.floor(site[0] / a);
			long gj = (long) Math.floor(site[1] / a);
			for (long i = gi - 6; i <= gi + 6; i++) {
				for (long j = gj - 6; j <= gj + 6; j++) {
					RiverNetwork.Segment s = net.segment(order, i, j);
					if (s == null) {
						continue;
					}
					for (double t = 0.5; t < 0.95; t += 0.05) {
						double tl = Math.hypot(s.dx(t), s.dz(t));
						double off = s.channelOffset(t);
						double x = s.px(t) - s.dz(t) / tl * off;
						double z = s.pz(t) + s.dx(t) / tl * off;
						ColumnSample c = m.sample(x, z);
						if (c.waterKind() == WaterKind.RIVER && c.type() == LandscapeType.BESKIDS) {
							return new double[] {x, z};
						}
					}
				}
			}
		}
		return null;
	}

	private static void add(List<double[]> sites, List<String> names, String name, double[] p) {
		if (p != null) {
			sites.add(p);
			names.add(name);
		}
	}
}
