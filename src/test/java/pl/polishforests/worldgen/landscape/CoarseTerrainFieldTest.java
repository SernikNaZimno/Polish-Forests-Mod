package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Coarse terrain grid (M2, step S3; docs/03-m2-biomy.md §3.2): sBar, slope and aspect from {@code landElevation}
 * nodes every 32 m·k, tiles in {@link DirectCache}.
 */
class CoarseTerrainFieldTest {
	private static final long SEED = 20260927L;
	private static final LandscapeScale[] SCALES = {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY};
	private static final LandscapeType[] TYPES = {LandscapeType.OUTWASH_PLAIN, LandscapeType.MORAINE_PLATEAU,
			LandscapeType.OLD_GLACIAL_PLAIN, LandscapeType.FOOTHILLS, LandscapeType.BESKIDS};

	/** Center of an area of the given type (Beskids: the "beskids" patch from the golden test for this seed). */
	private static double[] site(LandscapeModel m, LandscapeType type) {
		if (type == LandscapeType.BESKIDS) {
			return m.scale() == LandscapeScale.REALISTIC ? new double[] {154_834, 1_058_738} : new double[] {27_609, 3_254};
		}
		return LandscapePreview.find(m, type, false);
	}

	/** Deterministic points in a square with a side of 4 km around the site, off the grid nodes. */
	private static double[][] points(double[] site, int n, long salt) {
		double[][] out = new double[n][];
		long h = salt;
		for (int i = 0; i < n; i++) {
			h = h * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
			double x = site[0] + (h >>> 11) % 4_000 - 2_000 + 0.37;
			h = h * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
			double z = site[1] + (h >>> 11) % 4_000 - 2_000 + 0.61;
			out[i] = new double[] {x, z};
		}
		return out;
	}

	/** Elevation model of the grid: {@code sample().terrain().rawSurface()} is exactly {@code landElevation}. */
	@Test
	void nodeValuesComeFromLandElevation() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			CoarseTerrainField c = m.coarseTerrain();
			double g = c.spacing();
			assertEquals(32.0 * scale.local(), g, 0.0, "node spacing");
			double[] site = site(m, LandscapeType.FOOTHILLS);
			for (int i = 0; i < 200; i++) {
				long ix = Math.round(site[0] / g) + i * 7 - 700;
				long iz = Math.round(site[1] / g) - i * 3 + 300;
				double x = ix * g;
				double z = iz * g;
				double sum = 0;
				for (int a = -1; a <= 1; a++) {
					for (int b = -1; b <= 1; b++) {
						sum += m.landElevation((ix + a) * g, (iz + b) * g);
					}
				}
				double gx = (m.landElevation(x + g, z) - m.landElevation(x - g, z)) / (2 * g);
				double gz = (m.landElevation(x, z + g) - m.landElevation(x, z - g)) / (2 * g);
				CoarseTerrainField.CoarseSample r = c.sample(x, z);
				String at = " at node " + ix + "," + iz + " (" + scale.id() + ")";
				assertEquals(sum / 9, r.sBar(), 1e-3, "sBar" + at);
				assertEquals(Math.toDegrees(Math.atan(Math.hypot(gx, gz))), r.slope(), 1e-3, "slope" + at);
				if (Math.hypot(gx, gz) > 1e-3) {
					assertEquals(0, angle(azimuthOf(gx, gz), r.aspect()), 0.05, "aspect" + at);
				}
				assertEquals(m.landElevation(x, z), m.sample(x, z).terrain().rawSurface(), 1e-9, "landElevation" + at);
			}
		}
	}

	/**
	 * The slope at any point agrees with central differences of {@code landElevation} with a step equal to the
	 * node spacing to within ±10%: in every landscape type and in both scales at least 85% of the points with a
	 * slope ≥ 1° are within ±10%, and the median and mean of the ratio within ±5%. The aspect deviates from the
	 * downhill direction given by these differences by at most 10° at 90% of the points.
	 */
	@Test
	void slopeMatchesFiniteDifferences() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			CoarseTerrainField c = m.coarseTerrain();
			double g = c.spacing();
			int typesChecked = 0;
			for (LandscapeType type : TYPES) {
				double[] site = site(m, type);
				if (site == null) {
					continue;
				}
				List<Double> ratio = new ArrayList<>();
				List<Double> dAsp = new ArrayList<>();
				for (double[] p : points(site, 4_000, type.ordinal() * 31L + 7)) {
					double x = p[0];
					double z = p[1];
					double gx = (m.landElevation(x + g, z) - m.landElevation(x - g, z)) / (2 * g);
					double gz = (m.landElevation(x, z + g) - m.landElevation(x, z - g)) / (2 * g);
					double ref = Math.hypot(gx, gz);
					if (Math.toDegrees(Math.atan(ref)) < 1) {
						continue;
					}
					CoarseTerrainField.CoarseSample r = c.sample(x, z);
					ratio.add(Math.tan(Math.toRadians(r.slope())) / ref);
					dAsp.add(angle(azimuthOf(gx, gz), r.aspect()));
				}
				int n = ratio.size();
				if (n < 300) {
					System.out.println(String.format(Locale.ROOT, "[terrain grid] %s %s: only %d points ≥ 1°, skipping",
							scale.id(), type, n));
					continue;
				}
				typesChecked++;
				ratio.sort(null);
				dAsp.sort(null);
				double within = ratio.stream().filter(v -> Math.abs(v - 1) <= 0.10).count() / (double) n;
				double mean = ratio.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
				double median = ratio.get(n / 2);
				double asp90 = dAsp.get(9 * n / 10);
				System.out.println(String.format(Locale.ROOT,
						"[terrain grid] %s %s: %d points ≥ 1°, within ±10%%: %.3f, ratio median %.3f, mean %.3f,"
								+ " 5–95%%: %.3f–%.3f; aspect error 50/90%%: %.1f° / %.1f°",
						scale.id(), type, n, within, median, mean, ratio.get(n / 20), ratio.get(19 * n / 20),
						dAsp.get(n / 2), asp90));
				String at = " (" + scale.id() + ", " + type + ")";
				assertTrue(within >= 0.85, "within ±10% only " + within + at);
				assertTrue(Math.abs(median - 1) <= 0.05 && Math.abs(mean - 1) <= 0.05,
						"slope ratio: median " + median + ", mean " + mean + at);
				assertTrue(asp90 <= 10, "aspect error 90%: " + asp90 + at);
			}
			assertTrue(typesChecked >= 3, "too few types with slopes ≥ 1° at scale " + scale.id());
		}
	}

	/** The aspect is the downhill direction: a step of one node spacing towards it goes down (azimuth from −Z towards +X). */
	@Test
	void aspectPointsDownhill() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			CoarseTerrainField c = m.coarseTerrain();
			double g = c.spacing();
			int n = 0;
			int down = 0;
			for (LandscapeType type : new LandscapeType[] {LandscapeType.FOOTHILLS, LandscapeType.BESKIDS}) {
				for (double[] p : points(site(m, type), 2_000, 99)) {
					CoarseTerrainField.CoarseSample r = c.sample(p[0], p[1]);
					if (r.slope() < 5) {
						continue;
					}
					double a = Math.toRadians(r.aspect());
					n++;
					// East = +X, north = −Z.
					if (m.landElevation(p[0] + g * Math.sin(a), p[1] - g * Math.cos(a)) < m.landElevation(p[0], p[1])) {
						down++;
					}
				}
			}
			System.out.println(String.format(Locale.ROOT, "[terrain grid] %s: a step towards the aspect goes down in %d of %d",
					scale.id(), down, n));
			assertTrue(n > 500 && down >= 0.95 * n, "aspect does not point downhill: " + down + " of " + n);
		}
	}

	/** The fast atan2 agrees with {@link Math#atan2} (azimuth and slope). */
	@Test
	void fastAtanMatchesMath() {
		long h = 1;
		double worst = 0;
		for (int i = 0; i < 200_000; i++) {
			h = h * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
			double e = ((h >>> 11) * 0x1.0p-53 - 0.5) * Math.pow(10, (i % 7) - 3);
			h = h * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
			double nn = ((h >>> 11) * 0x1.0p-53 - 0.5) * Math.pow(10, (i % 5) - 2);
			double ref = Math.toDegrees(Math.atan2(e, nn));
			ref = ref < 0 ? ref + 360 : ref;
			double az = CoarseTerrainField.azimuth(e, nn);
			assertTrue(az >= 0 && az < 360, "azimuth outside [0, 360): " + az);
			worst = Math.max(worst, angle(ref, az));
			double t = Math.abs(e);
			worst = Math.max(worst, Math.abs(Math.toDegrees(Math.atan(Math.min(t, 1)))
					- Math.toDegrees(CoarseTerrainField.atan01(Math.min(t, 1)))));
		}
		for (double[] v : new double[][] {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {-1, -1}}) {
			double ref = (Math.toDegrees(Math.atan2(v[0], v[1])) + 360) % 360;
			assertEquals(ref, CoarseTerrainField.azimuth(v[0], v[1]), 1e-5, "azimuth " + v[0] + "," + v[1]);
		}
		// A direction just west of north: 360 − (almost 0) rounds to 360, while the range is [0, 360).
		for (double[] v : new double[][] {{-1e-17, 1}, {-Double.MIN_VALUE, 1}, {-1e-300, 1e-290}, {-0.0, 1}}) {
			double az = CoarseTerrainField.azimuth(v[0], v[1]);
			assertTrue(az >= 0 && az < 360, "azimuth outside [0, 360) for " + v[0] + "," + v[1] + ": " + az);
			assertEquals(0, angle(0, az), 1e-6, "azimuth " + v[0] + "," + v[1]);
		}
		assertTrue(worst < 1e-5, "fast atan error: " + worst + "°");
	}

	/**
	 * The result depends neither on the cache state nor on threads: a grid with 4 slots (constant tile eviction)
	 * queried in parallel in a different order gives the same values as the default grid queried sequentially.
	 */
	@Test
	void resultsDoNotDependOnCacheOrThreads() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			CoarseTerrainField full = m.coarseTerrain();
			CoarseTerrainField tiny = new CoarseTerrainField(m, full.spacing(), 4);
			double[] site = site(m, LandscapeType.BESKIDS);
			double[][] pts = points(site, 6_000, 5);
			CoarseTerrainField.CoarseSample[] ref = new CoarseTerrainField.CoarseSample[pts.length];
			for (int i = 0; i < pts.length; i++) {
				ref[i] = full.sample(pts[i][0], pts[i][1]);
			}
			CoarseTerrainField.CoarseSample[] par = new CoarseTerrainField.CoarseSample[pts.length];
			IntStream.range(0, pts.length).parallel().forEach(k -> {
				int i = pts.length - 1 - k;
				par[i] = tiny.sample(pts[i][0], pts[i][1]);
			});
			for (int i = 0; i < pts.length; i++) {
				assertEquals(ref[i], par[i], "point " + pts[i][0] + "," + pts[i][1]);
			}
		}
	}

	/**
	 * The first query in a tile that is not cached computes only its cell (4 × 4 raw nodes), the second the whole
	 * tile; both give a bit-identical result. The points lie in separate tiles, so every first read takes the cell path.
	 */
	@Test
	void sparseCellMatchesTile() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			CoarseTerrainField c = new CoarseTerrainField(m, m.coarseTerrain().spacing());
			double tile = CoarseTerrainField.TILE * c.spacing();
			for (LandscapeType type : TYPES) {
				double[] site = site(m, type);
				if (site == null) {
					continue;
				}
				for (int i = 0; i < 40; i++) {
					// Every third tile in a row, at various places in the cell (also on a node and on the cell edge).
					double x = site[0] + (3 * i - 60) * tile + (i % 4) * 0.25 * c.spacing() + (i % 3 == 0 ? 0 : 0.37);
					double z = site[1] + (i % 5) * 0.2 * c.spacing() + 0.11 * i;
					CoarseTerrainField.CoarseSample sparse = c.sample(x, z);
					CoarseTerrainField.CoarseSample tiled = c.sample(x, z);
					assertEquals(sparse, tiled, "cell vs tile at " + x + "," + z + " (" + scale.id() + ", " + type + ")");
					assertEquals(tiled, m.coarseTerrain().sample(x, z), "different grid at " + x + "," + z);
				}
			}
		}
	}

	/** Tile cache: a tile comes back from the cache, and an evicted one is recomputed with the same value. */
	@Test
	void directCacheReturnsSameTiles() {
		int[] builds = new int[1];
		DirectCache<long[]> cache = new DirectCache<>(16, (tx, tz) -> {
			builds[0]++;
			return new long[] {tx, tz};
		});
		long[] a = cache.get(3, -7);
		assertTrue(a == cache.get(3, -7), "tile did not come back from the cache");
		assertEquals(1, builds[0]);
		for (long i = 0; i < 1_000; i++) {
			long[] t = cache.get(i, -i);
			assertEquals(i, t[0]);
			assertEquals(-i, t[1]);
		}
		long[] b = cache.get(3, -7);
		assertEquals(3, b[0]);
		assertEquals(-7, b[1]);
		assertTrue(b == cache.peek(3, -7), "peek does not return the cached tile");
		int before = builds[0];
		assertEquals(null, cache.peek(123_456, 789), "peek of a tile that is not cached");
		assertEquals(before, builds[0], "peek computes the tile");
	}

	/** Azimuth of the downhill direction (−gradient) from north (−Z), clockwise, in degrees. */
	private static double azimuthOf(double gx, double gz) {
		double a = Math.toDegrees(Math.atan2(-gx, gz));
		return a < 0 ? a + 360 : a;
	}

	/** Difference of angles in degrees, [0, 180]. */
	private static double angle(double a, double b) {
		double d = Math.abs(a - b) % 360;
		return Math.min(d, 360 - d);
	}
}
