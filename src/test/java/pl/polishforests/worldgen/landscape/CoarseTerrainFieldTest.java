package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Zgrubna siatka terenu (M2, krok S3; docs/03-m2-biomy.md §3.2): sBar, nachylenie i ekspozycja z węzłów
 * {@code landElevation} co 32 m·k, kafle w {@link DirectCache}.
 */
class CoarseTerrainFieldTest {
	private static final long SEED = 20260927L;
	private static final LandscapeScale[] SCALES = {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY};
	private static final LandscapeType[] TYPES = {LandscapeType.OUTWASH_PLAIN, LandscapeType.MORAINE_PLATEAU,
			LandscapeType.OLD_GLACIAL_PLAIN, LandscapeType.FOOTHILLS, LandscapeType.BESKIDS};

	/** Środek obszaru danego typu (Beskidy: łata „beskidy” ze złotego testu dla tego ziarna). */
	private static double[] site(LandscapeModel m, LandscapeType type) {
		if (type == LandscapeType.BESKIDS) {
			return m.scale() == LandscapeScale.REALISTIC ? new double[] {154_834, 1_058_738} : new double[] {27_609, 3_254};
		}
		return LandscapePreview.find(m, type, false);
	}

	/** Deterministyczne punkty w kwadracie o boku 4 km wokół miejsca, poza węzłami siatki. */
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

	/** Model wysokości dla siatki: {@code sample().teren().rawSurface()} to dokładnie {@code landElevation}. */
	@Test
	void nodeValuesComeFromLandElevation() {
		for (LandscapeScale scale : SCALES) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			CoarseTerrainField c = m.coarseTerrain();
			double g = c.spacing();
			assertEquals(32.0 * scale.local(), g, 0.0, "odstęp węzłów");
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
				String at = " w węźle " + ix + "," + iz + " (" + scale.id() + ")";
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
	 * Nachylenie w dowolnym punkcie zgadza się z różnicami centralnymi {@code landElevation} o kroku równym
	 * odstępowi węzłów z dokładnością ±10%: w każdym typie krajobrazu i obu skalach co najmniej 85% punktów
	 * o nachyleniu ≥ 1° mieści się w ±10%, a mediana i średnia stosunku w ±5%. Ekspozycja odbiega od kierunku
	 * spadku z tych różnic o najwyżej 10° w 90% punktów.
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
					System.out.println(String.format(Locale.ROOT, "[siatka terenu] %s %s: tylko %d punktów ≥ 1°, pomijam",
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
						"[siatka terenu] %s %s: %d punktów ≥ 1°, w ±10%%: %.3f, stosunek mediana %.3f, średnia %.3f,"
								+ " 5–95%%: %.3f–%.3f; błąd ekspozycji 50/90%%: %.1f° / %.1f°",
						scale.id(), type, n, within, median, mean, ratio.get(n / 20), ratio.get(19 * n / 20),
						dAsp.get(n / 2), asp90));
				String at = " (" + scale.id() + ", " + type + ")";
				assertTrue(within >= 0.85, "w ±10% tylko " + within + at);
				assertTrue(Math.abs(median - 1) <= 0.05 && Math.abs(mean - 1) <= 0.05,
						"stosunek nachyleń: mediana " + median + ", średnia " + mean + at);
				assertTrue(asp90 <= 10, "błąd ekspozycji 90%: " + asp90 + at);
			}
			assertTrue(typesChecked >= 3, "za mało typów z nachyleniami ≥ 1° w skali " + scale.id());
		}
	}

	/** Ekspozycja to kierunek spadku: krok o odstęp węzłów w jej stronę schodzi w dół (azymut od −Z ku +X). */
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
					// Wschód = +X, północ = −Z.
					if (m.landElevation(p[0] + g * Math.sin(a), p[1] - g * Math.cos(a)) < m.landElevation(p[0], p[1])) {
						down++;
					}
				}
			}
			System.out.println(String.format(Locale.ROOT, "[siatka terenu] %s: krok w stronę ekspozycji w dół w %d z %d",
					scale.id(), down, n));
			assertTrue(n > 500 && down >= 0.95 * n, "ekspozycja nie wskazuje spadku: " + down + " z " + n);
		}
	}

	/** Szybki atan2 zgadza się z {@link Math#atan2} (azymut i nachylenie). */
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
			assertTrue(az >= 0 && az < 360, "azymut poza [0, 360): " + az);
			worst = Math.max(worst, angle(ref, az));
			double t = Math.abs(e);
			worst = Math.max(worst, Math.abs(Math.toDegrees(Math.atan(Math.min(t, 1)))
					- Math.toDegrees(CoarseTerrainField.atan01(Math.min(t, 1)))));
		}
		for (double[] v : new double[][] {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {-1, -1}}) {
			double ref = (Math.toDegrees(Math.atan2(v[0], v[1])) + 360) % 360;
			assertEquals(ref, CoarseTerrainField.azimuth(v[0], v[1]), 1e-5, "azymut " + v[0] + "," + v[1]);
		}
		// Kierunek tuż na zachód od północy: 360 − (prawie 0) zaokrągla się do 360, a zakres to [0, 360).
		for (double[] v : new double[][] {{-1e-17, 1}, {-Double.MIN_VALUE, 1}, {-1e-300, 1e-290}, {-0.0, 1}}) {
			double az = CoarseTerrainField.azimuth(v[0], v[1]);
			assertTrue(az >= 0 && az < 360, "azymut poza [0, 360) dla " + v[0] + "," + v[1] + ": " + az);
			assertEquals(0, angle(0, az), 1e-6, "azymut " + v[0] + "," + v[1]);
		}
		assertTrue(worst < 1e-5, "błąd szybkiego atan: " + worst + "°");
	}

	/**
	 * Wynik nie zależy od stanu pamięci ani od wątków: siatka z 4 miejscami (ciągłe wypychanie kafli) pytana
	 * równolegle w innej kolejności daje te same wartości co siatka domyślna pytana po kolei.
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
				assertEquals(ref[i], par[i], "punkt " + pts[i][0] + "," + pts[i][1]);
			}
		}
	}

	/**
	 * Pierwsze zapytanie w kaflu spoza pamięci liczy samo oczko (4 × 4 węzły surowe), drugie cały kafel; oba
	 * dają ten sam wynik co do bitu. Punkty są w osobnych kaflach, więc każdy pierwszy odczyt idzie drogą oczka.
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
					// Co trzeci kafel w rzędzie, w różnych miejscach oczka (także na węźle i krawędzi oczka).
					double x = site[0] + (3 * i - 60) * tile + (i % 4) * 0.25 * c.spacing() + (i % 3 == 0 ? 0 : 0.37);
					double z = site[1] + (i % 5) * 0.2 * c.spacing() + 0.11 * i;
					CoarseTerrainField.CoarseSample sparse = c.sample(x, z);
					CoarseTerrainField.CoarseSample tiled = c.sample(x, z);
					assertEquals(sparse, tiled, "oczko a kafel w " + x + "," + z + " (" + scale.id() + ", " + type + ")");
					assertEquals(tiled, m.coarseTerrain().sample(x, z), "inna siatka w " + x + "," + z);
				}
			}
		}
	}

	/** Pamięć kafli: kafel wraca z pamięci, a wypchnięty liczy się od nowa z tą samą wartością. */
	@Test
	void directCacheReturnsSameTiles() {
		int[] builds = new int[1];
		DirectCache<long[]> cache = new DirectCache<>(16, (tx, tz) -> {
			builds[0]++;
			return new long[] {tx, tz};
		});
		long[] a = cache.get(3, -7);
		assertTrue(a == cache.get(3, -7), "kafel nie wrócił z pamięci");
		assertEquals(1, builds[0]);
		for (long i = 0; i < 1_000; i++) {
			long[] t = cache.get(i, -i);
			assertEquals(i, t[0]);
			assertEquals(-i, t[1]);
		}
		long[] b = cache.get(3, -7);
		assertEquals(3, b[0]);
		assertEquals(-7, b[1]);
		assertTrue(b == cache.peek(3, -7), "peek nie zwraca kafla z pamięci");
		int before = builds[0];
		assertEquals(null, cache.peek(123_456, 789), "peek kafla spoza pamięci");
		assertEquals(before, builds[0], "peek liczy kafel");
	}

	/** Azymut kierunku spadku (−gradient) od północy (−Z) zgodnie z ruchem wskazówek zegara, w stopniach. */
	private static double azimuthOf(double gx, double gz) {
		double a = Math.toDegrees(Math.atan2(-gx, gz));
		return a < 0 ? a + 360 : a;
	}

	/** Różnica kątów w stopniach, [0, 180]. */
	private static double angle(double a, double b) {
		double d = Math.abs(a - b) % 360;
		return Math.min(d, 360 - d);
	}
}
