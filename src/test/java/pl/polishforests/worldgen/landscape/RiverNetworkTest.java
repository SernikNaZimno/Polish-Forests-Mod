package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * River network, sea and coast: water banks, correct flow, no cliffs at sources, and the watercourse
 * fields exported to {@link ColumnSample.Waters} (M2, step S2).
 */
class RiverNetworkTest {
	private static final long SEED = 20260927L;
	/** Beskids interior for the seed {@link #SEED} (the "beskids" patch in {@code golden_terrain_m1.txt}); saves searching. */
	private static final double[] BESKIDS_REAL = {154_834, 1_058_738};
	private static final double[] BESKIDS_GAMEPLAY = {27_609, 3_254};

	@Test
	void waterIsContainedAtCoastStreamsOxbowsAndLakesRealistic() {
		assertSitesContained(new LandscapeModel(SEED, 1.0), 2_000);
	}

	@Test
	void waterIsContainedAtCoastStreamsOxbowsAndLakesGameplay() {
		assertSitesContained(new LandscapeModel(SEED, LandscapeScale.GAMEPLAY, 1.0), 100);
	}

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

	/**
	 * At the sources of mountain streams the height difference between neighboring dry columns must not
	 * exceed a steep slope; the old river model produced a vertical cliff here.
	 */
	@Test
	void mountainStreamSourcesHaveNoCliffs() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		RiverNetwork net = networkOf(m);
		double[] site = find(m, s -> s.type() == LandscapeType.BESKIDS, 5_000);
		assertTrue(site != null, "no Beskids in the test area");
		int sources = 0;
		double worst = 0;
		String where = "";
		long gi = (long) Math.floor(site[0] / 1_250);
		long gj = (long) Math.floor(site[1] / 1_250);
		for (long i = gi - 10; i <= gi + 10 && sources < 12; i++) {
			for (long j = gj - 10; j <= gj + 10 && sources < 12; j++) {
				RiverNetwork.Segment s = net.segment(1, i, j);
				if (s == null || !s.source) {
					continue;
				}
				sources++;
				for (double t = 0; t <= 0.5; t += 0.05) {
					double cx = s.px(t);
					double cz = s.pz(t);
					for (int k = -60; k <= 60; k += 3) {
						double tl = Math.hypot(s.dx(t), s.dz(t));
						double x = cx - s.dz(t) / tl * k;
						double z = cz + s.dx(t) / tl * k;
						// Dry terrain only: the channel bank above the water may be steep.
						ColumnSample c0 = m.sample(x, z);
						ColumnSample c1 = m.sample(x + 1, z);
						ColumnSample c2 = m.sample(x, z + 1);
						if (c0.hasWater() || c1.hasWater() || c2.hasWater()) {
							continue;
						}
						double h0 = c0.surface();
						double step = Math.max(Math.abs(c1.surface() - h0), Math.abs(c2.surface() - h0));
						if (step > worst) {
							worst = step;
							where = Math.round(x) + "," + Math.round(z);
						}
					}
				}
			}
		}
		assertTrue(sources > 0, "no mountain stream sources");
		assertTrue(worst < 3.0, "cliff at a source: difference " + worst + " m per 1 m at " + where);
	}

	/**
	 * River rules of the large massifs (M2-8, step K2) on every massif of {@link GreatMassifSurvey}: no node of order
	 * 1–3 in the core (G &gt; {@link LandscapeModel#GM_CORE}) is a spring and no segment starts its source there. The
	 * cores are then measured on a grid ({@link #coreCut}: every 25 m at realistic scale, 10 m at gameplay scale) and
	 * their valleys are held to the state after the review of K2:
	 * <ul>
	 * <li>summit area (G &gt; 0.9), i.e. no canyon in the dome: no water, cut ({@code landElevation} − surface) at most
	 * {@link #SUMMIT_CUT} m (20 m realistic, 51 m gameplay);</li>
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
	 * at (142063, −1477272) on the massif (143663, −1476297)); step K4 changes the segment geometry and must measure
	 * this again (docs/m2/poprawka-geometrii.md, K2).
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
	 * Limits of {@link #noSpringsOnMassifCore}, realistic and gameplay scale, measured after the review of K2 (summit
	 * cut 19.6 and 50.6 m, core cut 741.3 and 855.6 m, 1598 and 326 floor columns, the deepest at G 0.502 and 0.355).
	 * Largest cut of the summit area (G &gt; 0.9) in m.
	 */
	static final double[] SUMMIT_CUT = {20, 51};
	/** Largest cut of the core (G &gt; 0.3) in m. */
	static final double[] CORE_CUT = {742, 856};
	/** Largest number of core columns on a valley floor or in water. */
	static final int[] CORE_FLOOR = {1_598, 326};
	/** Largest massif strength G of a core column on a valley floor or in water. */
	static final double[] CORE_FLOOR_G = {0.51, 0.36};

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
	 * {@link #mountainStreamSourcesHaveNoCliffs}, but for the order 1 sources in the reach of the massifs of
	 * {@link GreatMassifSurvey} (G &gt; 0 at the source, at most {@value #MASSIF_SOURCES} per massif in the order of the node
	 * grid), in both scales, and also behind the source: transects across the valley at t from −0.5 (on the extension
	 * of the valley axis behind the source) to 0.5, ±60 m·k wide. The first version of K2 had straight valley wedges
	 * with head walls of 100–380 m behind such sources. The limits {@link #MASSIF_SOURCE_STEP} are the state after the
	 * review of K2; the goal of decision D4 (step K4b) is the 3 m per 1 m of {@link #mountainStreamSourcesHaveNoCliffs}.
	 */
	@Test
	void massifStreamSourcesHaveNoCliffs() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			SourceCliffs r = massifSourceCliffs(m);
			int k = sc == LandscapeScale.REALISTIC ? 0 : 1;
			System.out.printf(Locale.ROOT, "%s: %d sources on the massif flanks, largest step %.2f m per 1 m at %s (limit "
					+ "%.2f m, goal 3 m)%n", sc.id(), r.sources(), r.worst(), r.where(), MASSIF_SOURCE_STEP[k]);
			assertTrue(r.sources() >= 20, sc.id() + ": only " + r.sources() + " sources on the massif flanks");
			assertTrue(r.worst() <= MASSIF_SOURCE_STEP[k], sc.id() + ": cliff at a source on a massif flank: " + r.worst()
					+ " m per 1 m at " + r.where());
		}
	}

	static final int MASSIF_SOURCES = 12;
	/**
	 * Largest step per 1 m at the sources on the massif flanks, realistic and gameplay scale, measured after the review of
	 * K2: 1.46 m (108 sources) and 25.87 m (300 sources; at (4989.7, −33301.9) on the massif by the spawn, a jump of the
	 * projection of a short order 1 segment, A2, step K4b).
	 */
	static final double[] MASSIF_SOURCE_STEP = {1.5, 25.9};

	/** Sources checked by {@link #massifStreamSourcesHaveNoCliffs} and the largest step per 1 m found at them. */
	record SourceCliffs(int sources, double worst, String where) {
	}

	static SourceCliffs massifSourceCliffs(LandscapeModel m) {
		LandscapeScale sc = m.scale();
		RiverNetwork net = networkOf(m);
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
		// {largest step, x, z} per source.
		double[][] worst = sources.parallelStream().map(seg -> {
			double best = 0;
			double bx = 0;
			double bz = 0;
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
				}
			}
			return new double[] {best, bx, bz};
		}).toArray(double[][]::new);
		double w = 0;
		String where = "-";
		for (double[] v : worst) {
			if (v[0] > w) {
				w = v[0];
				where = String.format(Locale.ROOT, "(%.1f, %.1f)", v[1], v[2]);
			}
		}
		return new SourceCliffs(sources.size(), w, where);
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

	private static void assertSitesContained(LandscapeModel m, double step) {
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
