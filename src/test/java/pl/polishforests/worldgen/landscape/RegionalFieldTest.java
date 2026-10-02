package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Regional fields O and P (M2, step S3; docs/03-m2-biomy.md §3.2, §9, §12.1): range [0, 1], smoothness, a belt
 * without beech and spruce on 10–20% of the land and the reach of P ≥ 0.5 around mountain ranges.
 */
class RegionalFieldTest {
	private static final long[] SEEDS = {20260927L, 1L, 2L};
	/** Half-side of the share measurement area at zs = 1 (about 6–7 province noise waves per side). */
	private static final double HALF = 3_000_000;
	private static final LandscapeScale[] SCALES = {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY};

	private static double zs(LandscapeModel m) {
		return m.regionSize() / LandscapeModel.BASE_REGION_SIZE;
	}

	/** O and P are finite and lie in [0, 1] on land and at sea; the model sample carries the grid values. */
	@Test
	void valuesAreInUnitRange() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEEDS[0], scale, 1.0);
			RegionalField rf = m.regional();
			double zs = zs(m);
			double min = 1;
			double max = 0;
			for (int i = 0; i < 200; i++) {
				for (int j = 0; j < 200; j++) {
					double x = (-HALF + i * 30_000.0 + 0.37) * zs;
					double z = (-HALF + j * 30_000.0 + 0.61) * zs;
					ColumnSample.Region r = rf.sample(x, z);
					assertTrue(r.oceanicity() >= 0 && r.oceanicity() <= 1, "O outside [0, 1] at " + x + "," + z + ": " + r);
					assertTrue(r.mountainInfluence() >= 0 && r.mountainInfluence() <= 1, "P outside [0, 1] at " + x + "," + z + ": " + r);
					min = Math.min(min, r.oceanicity());
					max = Math.max(max, r.oceanicity());
				}
			}
			System.out.println(String.format(Locale.ROOT, "[regional fields] %s: O from %.3f to %.3f", scale.id(), min, max));
			for (int i = 0; i < 300; i++) {
				double x = (i * 7_919.0 - 1_000_000) * zs;
				double z = (i * -3_571.0 + 500_000) * zs;
				assertEquals(rf.sample(x, z), m.sample(x, z).region(), "sample fields at " + x + "," + z);
			}
		}
	}

	/**
	 * At a grid node O is the node value and P the mean of the 5 × 5 node values before averaging (stored as float);
	 * between nodes the value lies between the values at the cell corners.
	 */
	@Test
	void nodesHoldExactValues() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEEDS[0], scale, 1.0);
			RegionalField rf = m.regional();
			double g = rf.spacing();
			for (int i = 0; i < 60; i++) {
				long ix = i * 37L - 1_000;
				long iz = i * -23L + 400;
				ColumnSample.Region r = rf.sample(ix * g, iz * g);
				assertEquals(rf.oceanicity(ix * g, iz * g), r.oceanicity(), 1e-6, "O at node " + ix + "," + iz);
				double sum = 0;
				for (int a = -2; a <= 2; a++) {
					for (int b = -2; b <= 2; b++) {
						sum += rf.mountainInfluence((ix + a) * g, (iz + b) * g);
					}
				}
				assertEquals(sum / 25, r.mountainInfluence(), 1e-6, "P at node " + ix + "," + iz);
				ColumnSample.Region mid = rf.sample((ix + 0.3) * g, (iz + 0.6) * g);
				double lo = 1;
				double hi = 0;
				for (int a = 0; a <= 1; a++) {
					for (int b = 0; b <= 1; b++) {
						double o = rf.sample((ix + a) * g, (iz + b) * g).oceanicity();
						lo = Math.min(lo, o);
						hi = Math.max(hi, o);
					}
				}
				assertTrue(mid.oceanicity() >= lo - 1e-9 && mid.oceanicity() <= hi + 1e-9, "O between nodes");
			}
		}
	}

	/** Smoothness: on lines along X, Z and the diagonal, the change of O and P per 1 km·zs does not exceed 0.02. */
	@Test
	void fieldsAreSmooth() {
		for (LandscapeScale scale : SCALES) {
			for (long seed : SEEDS) {
				LandscapeModel m = new LandscapeModel(seed, scale, 1.0);
				RegionalField rf = m.regional();
				double km = 1_000 * zs(m);
				double maxO = 0;
				double maxP = 0;
				for (int line = 0; line < 30; line++) {
					double c = (-HALF + 50_000 + line * 197_000.0) * zs(m);
					for (int dir = 0; dir < 3; dir++) {
						double[] d = dir == 0 ? new double[] {1, 0} : dir == 1 ? new double[] {0, 1}
								: new double[] {Math.sqrt(0.5), Math.sqrt(0.5)};
						double x0 = dir == 1 ? c : -HALF * zs(m);
						double z0 = dir == 0 ? c : dir == 1 ? -HALF * zs(m) : c - HALF * zs(m);
						ColumnSample.Region prev = rf.sample(x0, z0);
						for (int k = 1; k <= 4_000; k++) {
							ColumnSample.Region r = rf.sample(x0 + d[0] * k * km, z0 + d[1] * k * km);
							maxO = Math.max(maxO, Math.abs(r.oceanicity() - prev.oceanicity()));
							maxP = Math.max(maxP, Math.abs(r.mountainInfluence() - prev.mountainInfluence()));
							prev = r;
						}
					}
				}
				System.out.println(String.format(Locale.ROOT,
						"[regional fields] %s, seed %d: largest change per km·zs: O %.4f, P %.4f", scale.id(), seed, maxO,
						maxP));
				assertTrue(maxO <= 0.02, "O changes by " + maxO + " per km·zs");
				assertTrue(maxP <= 0.02, "P changes by " + maxP + " per km·zs");
			}
		}
	}

	/**
	 * Between the beech range (O ≥ 0.40 or P ≥ 0.40) and the natural spruce range (O &lt; 0.30 or P ≥ 0.50)
	 * lies a belt without either species, as in Mazovia (§9). It covers 10–20% of the land for each of the three seeds.
	 */
	@Test
	void bandWithoutBeechAndSpruceCoversTenToTwentyPercentOfLand() {
		for (long seed : SEEDS) {
			double[] share = shares(new LandscapeModel(seed, LandscapeScale.REALISTIC, 1.0));
			System.out.println(String.format(Locale.ROOT,
					"[regional fields] realistic, seed %d: belt without beech and spruce %.3f of land; beech %.3f, spruce %.3f,"
							+ " fir (P ≥ 0.5) %.3f, O ≥ 0.75 %.3f",
					seed, share[0], share[1], share[2], share[3], share[4]));
			assertTrue(share[0] >= 0.10 && share[0] <= 0.20, "belt without beech and spruce: " + share[0]);
		}
		// The gameplay scale uses the same fields scaled by zs (no separate calibration).
		double[] share = shares(new LandscapeModel(SEEDS[0], LandscapeScale.GAMEPLAY, 1.0));
		System.out.println(String.format(Locale.ROOT, "[regional fields] gameplay, seed %d: belt %.3f of land", SEEDS[0],
				share[0]));
		assertTrue(share[0] >= 0.10 && share[0] <= 0.20, "belt without beech and spruce (gameplay): " + share[0]);
	}

	/** Land shares: {belt, beech, spruce, P ≥ 0.5, O ≥ 0.75} on a grid every 20 km·zs. */
	private static double[] shares(LandscapeModel m) {
		RegionalField rf = m.regional();
		double zs = zs(m);
		int land = 0;
		int[] n = new int[5];
		for (double i = -HALF; i < HALF; i += 20_000) {
			for (double j = -HALF; j < HALF; j += 20_000) {
				double x = (i + 3_000) * zs;
				double z = (j + 7_000) * zs;
				if (m.seaField(x, z) < 0) {
					continue;
				}
				land++;
				ColumnSample.Region r = rf.sample(x, z);
				double o = r.oceanicity();
				double p = r.mountainInfluence();
				boolean beech = o >= 0.40 || p >= 0.40;
				boolean spruce = o < 0.30 || p >= 0.50;
				n[0] += !beech && !spruce ? 1 : 0;
				n[1] += beech ? 1 : 0;
				n[2] += spruce ? 1 : 0;
				n[3] += p >= 0.5 ? 1 : 0;
				n[4] += o >= 0.75 ? 1 : 0;
			}
		}
		double[] out = new double[5];
		for (int k = 0; k < 5; k++) {
			out[k] = n[k] / (double) land;
		}
		return out;
	}

	/**
	 * P ≥ 0.5 reaches about 150–250 km·zs from the range axis (§3.2): the median distance of the P = 0.5 boundary from
	 * the range axis (zero line of {@code mountainRaw}, where the range mask and the sea offset are ≥ 0.5). Only the
	 * boundary along a range, where the masks are almost full (≥ 0.9), is counted, not at the ends of the chains. Boundary
	 * points are collected from {@link #RANGE_SEEDS} seeds together: a single seed sometimes has only a few dozen, and the
	 * per-seed medians spread more than the plan interval (they are printed for information).
	 */
	@Test
	void mountainInfluenceReachesAroundRanges() {
		List<Double> all = new ArrayList<>();
		StringBuilder perSeed = new StringBuilder();
		for (long seed : RANGE_SEEDS) {
			List<Double> dist = rangeBoundaryDistances(new LandscapeModel(seed, LandscapeScale.REALISTIC, 1.0));
			all.addAll(dist);
			dist.sort(null);
			perSeed.append(dist.isEmpty() ? String.format(Locale.ROOT, " %d: none;", seed)
					: String.format(Locale.ROOT, " %d: %.0f km (%d);", seed, dist.get(dist.size() / 2) / 1e3, dist.size()));
		}
		all.sort(null);
		assertTrue(all.size() >= 1_000, "too few P = 0.5 boundary points near ranges: " + all.size());
		double median = all.get(all.size() / 2) / 1e3;
		System.out.println(String.format(Locale.ROOT,
				"[regional fields] P = 0.5 boundary from the range axis, %d seeds together (%d points): quartiles %.0f / %.0f / %.0f km;"
						+ " per-seed medians:%s",
				RANGE_SEEDS.length, all.size(), all.get(all.size() / 4) / 1e3, median, all.get(3 * all.size() / 4) / 1e3,
				perSeed));
		assertTrue(median >= 150 && median <= 250, "median reach of P ≥ 0.5: " + median + " km");
	}

	/** Seeds for measuring the reach of P (those from {@link #SEEDS} and the next seven). */
	private static final long[] RANGE_SEEDS = {20260927L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L};

	/**
	 * Distances of P = 0.5 boundary points (a change along X on a 4 km grid in a ±1500 km square, masks ≥ 0.9)
	 * from the nearest point of a range axis (a sign change of {@code mountainRaw} between neighboring grid points,
	 * masks ≥ 0.5), up to 450 km.
	 */
	private static List<Double> rangeBoundaryDistances(LandscapeModel m) {
		RegionalField rf = m.regional();
		double half = 1_500_000;
		double step = 4_000;
		int n = (int) (2 * half / step);
		boolean[][] high = new boolean[n][n];
		double[][] raw = new double[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double x = -half + i * step;
				double z = -half + j * step;
				high[i][j] = rf.sample(x, z).mountainInfluence() >= 0.5;
				raw[i][j] = m.mountainRaw(x, z);
			}
		}
		double bin = 100_000;
		Map<Long, List<double[]>> axis = new HashMap<>();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				boolean crossX = i + 1 < n && (raw[i][j] < 0) != (raw[i + 1][j] < 0);
				boolean crossZ = j + 1 < n && (raw[i][j] < 0) != (raw[i][j + 1] < 0);
				if (!crossX && !crossZ) {
					continue;
				}
				double x = -half + i * step;
				double z = -half + j * step;
				if (masks(m, x, z) >= 0.5) {
					axis.computeIfAbsent(binKey(x, z, bin), k -> new ArrayList<>()).add(new double[] {x, z});
				}
			}
		}
		List<Double> dist = new ArrayList<>();
		for (int i = 0; i + 1 < n; i++) {
			for (int j = 0; j < n; j++) {
				if (high[i][j] == high[i + 1][j]) {
					continue;
				}
				double x = -half + i * step;
				double z = -half + j * step;
				if (!(masks(m, x, z) >= 0.9)) {
					continue;
				}
				double best = Double.POSITIVE_INFINITY;
				long bx = Math.floorDiv((long) x, (long) bin);
				long bz = Math.floorDiv((long) z, (long) bin);
				for (long a = bx - 5; a <= bx + 5; a++) {
					for (long b = bz - 5; b <= bz + 5; b++) {
						for (double[] q : axis.getOrDefault(a * 1_000_003L + b, List.of())) {
							best = Math.min(best, Math.hypot(q[0] - x, q[1] - z));
						}
					}
				}
				if (best < 450_000) {
					dist.add(best);
				}
			}
		}
		return dist;
	}

	/** Range mask times the sea offset: mountainLinear / (1 − |mountainRaw|). */
	private static double masks(LandscapeModel m, double x, double z) {
		double ridge = 1 - Math.abs(m.mountainRaw(x, z));
		return ridge > 0 ? m.mountainLinear(x, z) / ridge : 0;
	}

	private static long binKey(double x, double z, double bin) {
		return Math.floorDiv((long) x, (long) bin) * 1_000_003L + Math.floorDiv((long) z, (long) bin);
	}

	/** Proximity of the sea raises O: the mean O in a 50 km·zs belt from the shore is higher than over 300 km·zs from it. */
	@Test
	void coastIsMoreOceanicThanInterior() {
		LandscapeModel m = new LandscapeModel(SEEDS[0], LandscapeScale.REALISTIC, 1.0);
		double coast = 0;
		int nc = 0;
		double inland = 0;
		int ni = 0;
		for (double x = -HALF; x < HALF; x += 25_000) {
			for (double z = -HALF; z < HALF; z += 25_000) {
				double d = m.coastDistance(x, z);
				if (d > 0 && d < 50_000) {
					coast += m.regional().sample(x, z).oceanicity();
					nc++;
				} else if (d > 300_000 && m.seaField(x, z) > 0) {
					inland += m.regional().sample(x, z).oceanicity();
					ni++;
				}
			}
		}
		coast /= nc;
		inland /= ni;
		System.out.println(String.format(Locale.ROOT, "[regional fields] mean O: near the shore %.3f (%d), inland %.3f (%d)",
				coast, nc, inland, ni));
		assertTrue(coast > inland + 0.05, "O near the shore " + coast + ", inland " + inland);
	}

	/**
	 * The Wz term looks west (−X, decision M2-3): on land 50–150 km·zs from the shore of a sea lying to the west,
	 * O is higher on average (by about 0.06) than in the same belt next to a sea lying to the east; with the term
	 * direction reversed the difference would have the opposite sign. The side of the sea is given by the
	 * gradient of {@code seaField} (increasing inland), only at shores running almost north–south.
	 */
	@Test
	void westernSeaRaisesOceanicity() {
		double west = 0;
		int nw = 0;
		double east = 0;
		int ne = 0;
		for (long seed : SEEDS) {
			LandscapeModel m = new LandscapeModel(seed, LandscapeScale.REALISTIC, 1.0);
			for (double x = -HALF; x < HALF; x += 10_000) {
				for (double z = -HALF; z < HALF; z += 10_000) {
					double d = m.coastDistance(x, z);
					if (d < 50_000 || d > 150_000) {
						continue;
					}
					double e = 5_000;
					double gx = m.seaField(x + e, z) - m.seaField(x - e, z);
					double gz = m.seaField(x, z + e) - m.seaField(x, z - e);
					if (Math.abs(gx) < 0.9 * Math.hypot(gx, gz)) {
						continue;
					}
					double o = m.regional().sample(x, z).oceanicity();
					if (gx > 0) {
						west += o;
						nw++;
					} else {
						east += o;
						ne++;
					}
				}
			}
		}
		west /= nw;
		east /= ne;
		System.out.println(String.format(Locale.ROOT,
				"[regional fields] mean O 50–150 km from the shore: sea to the west %.3f (%d), to the east %.3f (%d)", west,
				nw, east, ne));
		assertTrue(nw >= 500 && ne >= 500, "too few points: " + nw + ", " + ne);
		assertTrue(west > east + 0.03, "O with the sea to the west " + west + ", to the east " + east);
	}

	/**
	 * The result depends neither on the cache state nor on threads: fields with 4 cache slots (constant tile eviction)
	 * queried in parallel in reverse order give the same values as the default fields queried sequentially.
	 */
	@Test
	void resultsDoNotDependOnCacheOrThreads() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEEDS[0], scale, 1.0);
			RegionalField full = m.regional();
			RegionalField tiny = full.withSlots(4);
			double zs = zs(m);
			double[][] pts = new double[4_000][];
			long h = 11;
			for (int i = 0; i < pts.length; i++) {
				h = h * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
				double x = ((h >>> 11) * 0x1.0p-53 * 2 - 1) * HALF * zs;
				h = h * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
				double z = ((h >>> 11) * 0x1.0p-53 * 2 - 1) * HALF * zs;
				pts[i] = new double[] {x, z};
			}
			ColumnSample.Region[] ref = new ColumnSample.Region[pts.length];
			for (int i = 0; i < pts.length; i++) {
				ref[i] = full.sample(pts[i][0], pts[i][1]);
			}
			ColumnSample.Region[] par = new ColumnSample.Region[pts.length];
			IntStream.range(0, pts.length).parallel().forEach(k -> {
				int i = pts.length - 1 - k;
				par[i] = tiny.sample(pts[i][0], pts[i][1]);
			});
			for (int i = 0; i < pts.length; i++) {
				assertEquals(ref[i], par[i], "point " + pts[i][0] + "," + pts[i][1] + " (" + scale.id() + ")");
			}
		}
	}
}
