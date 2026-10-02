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
