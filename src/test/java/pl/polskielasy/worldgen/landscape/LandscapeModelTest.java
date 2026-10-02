package pl.polskielasy.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LandscapeModelTest {
	private static final long SEED = 20260927L;

	@Test
	void sameSeedGivesSameTerrain() {
		LandscapeModel a = new LandscapeModel(SEED, 1.0);
		LandscapeModel b = new LandscapeModel(SEED, 1.0);
		for (int i = 0; i < 200; i++) {
			double x = i * 7_919.0 - 500_000;
			double z = i * -3_571.0 + 250_000;
			assertEquals(a.sample(x, z), b.sample(x, z));
		}
	}

	/** Ziarno, skala i punkt nie zależą od kolejności zapytań: nowe pola też (porównanie rekordów przez equals). */
	@Test
	void habitatFieldsDoNotDependOnQueryOrder() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel a = new LandscapeModel(SEED, scale, 1.0);
			LandscapeModel b = new LandscapeModel(SEED, scale, 1.0);
			double step = scale == LandscapeScale.REALISTIC ? 1_501.0 : 97.0;
			ColumnSample[] first = new ColumnSample[200];
			for (int i = 0; i < first.length; i++) {
				first[i] = a.sample(i * step - 100 * step, (i % 20) * step * 7);
			}
			// Odwrotna kolejność na drugim modelu: inne stany pamięci podręcznych.
			for (int i = first.length - 1; i >= 0; i--) {
				assertEquals(first[i], b.sample(i * step - 100 * step, (i % 20) * step * 7));
			}
		}
	}

	/**
	 * Nowe pola próbki (M2, S2 i S3) są skończone tam, gdzie mają sens, i mieszczą się w zakresach. NaN tylko
	 * jako „nie dotyczy”, +∞ tylko jako „poza zasięgiem”.
	 */
	@Test
	void habitatFieldsAreFiniteAndInRange() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			int rivers = 0;
			int standing = 0;
			int floors = 0;
			int flysch = 0;
			Set<Landform> seen = EnumSet.noneOf(Landform.class);
			List<double[]> points = new ArrayList<>();
			double unit = scale == LandscapeScale.REALISTIC ? 1.0 : 0.05;
			for (int i = 0; i < 6_000; i++) {
				points.add(new double[] {(i % 77) * 7_919.0 * unit - 300_000 * unit, (i / 77) * 7_877.0 * unit - 300_000 * unit});
			}
			// Gęste łaty przy wodach (dna dolin, koryta, jeziora i oczka) i w Beskidach (łata „beskidy”
			// ze złotego testu dla tego ziarna).
			double[] beskidy = scale == LandscapeScale.REALISTIC ? new double[] {154_834, 1_058_738}
					: new double[] {27_609, 3_254};
			for (double[] site : new double[][] {LandscapePreview.findRiver(m),
					LandscapePreview.find(m, LandscapeType.WYSOCZYZNA_MORENOWA, true), beskidy}) {
				for (int i = 0; i < 10_000; i++) {
					points.add(new double[] {site[0] + (i % 100) * 7.0 - 350, site[1] + (i / 100) * 7.0 - 350});
				}
			}
			for (double[] p : points) {
				ColumnSample c = m.sample(p[0], p[1]);
				ColumnSample.Teren t = c.teren();
				ColumnSample.Wody w = c.wody();
				String at = " w " + p[0] + "," + p[1] + ": " + c;
				assertTrue(Double.isFinite(t.rawSurface()) && Double.isFinite(t.coastD()) && Double.isFinite(t.wyp()), "teren" + at);
				double sum = t.wSandr() + t.wWysoczyzna() + t.wRownina() + t.wPogorze() + t.wBeskidy();
				assertEquals(1.0, sum, 1e-9, "wagi typów" + at);
				assertTrue(t.wPobrzeze() >= 0 && t.wPobrzeze() <= 1, "wPobrzeze" + at);
				assertTrue(c.waterKind() == WaterKind.SEA ? Double.isNaN(t.piask()) : t.piask() >= 0 && t.piask() <= 1,
						"piask" + at);
				assertTrue(t.wydma() >= 0 && t.wydma() <= 23, "wydma" + at);
				assertTrue(t.masyw() >= 0 && t.masyw() <= 1, "masyw" + at);
				assertTrue(t.klif() >= 0 && (t.klif() > 0) == t.ma(Landform.KLIF), "klif" + at);
				if (t.wPogorze() + t.wBeskidy() > 0) {
					flysch++;
					assertTrue(t.grzbiet() >= 0 && t.grzbiet() <= 1.05, "grzbiet" + at);
				} else {
					assertTrue(Double.isNaN(t.grzbiet()), "grzbiet poza fliszem" + at);
				}
				for (Landform f : Landform.values()) {
					if (t.ma(f)) {
						assertTrue(Landform.Z_PROBKI.contains(f), "forma spoza próbki" + at);
						seen.add(f);
					}
				}
				if (w.rzad() > 0) {
					rivers++;
					assertTrue(w.rzad() <= 3, "rząd" + at);
					assertTrue(Double.isFinite(w.odlKoryta()) && w.szerKoryta() >= 1.5 - 1e-9
							&& w.szerKoryta() <= 400 && Double.isFinite(w.poziomKoryta()), "koryto" + at);
					assertTrue(w.polSzerDna() > 0 && Double.isFinite(w.polSzerDna()), "dno" + at);
					assertTrue(w.spadek() >= 0 && Double.isFinite(w.spadek()), "spadek" + at);
					if (w.wDnie()) {
						floors++;
						assertTrue(w.u() >= 0 && w.u() <= 1, "u" + at);
					} else {
						assertTrue(Double.isNaN(w.u()), "u poza dnem" + at);
					}
				} else {
					assertTrue(w.odlKoryta() == Double.POSITIVE_INFINITY && !w.wDnie() && !w.zrodlo()
							&& !w.brzegWypukly() && Double.isNaN(w.szerKoryta()), "brak cieku" + at);
				}
				if (w.rodzajStojacej() != ColumnSample.RodzajStojacej.BRAK) {
					standing++;
					assertTrue(Double.isFinite(w.s()) && w.poziomBrzegu() != ColumnSample.NO_WATER
							&& w.promienStojacej() >= 0 && Double.isFinite(w.promienStojacej()) && w.idJeziora() != 0,
							"woda stojąca" + at);
					assertTrue(!w.torfOmbro() || w.rodzajStojacej() == ColumnSample.RodzajStojacej.OCZKO_TORFOWE,
							"torf ombro poza oczkiem torfowym" + at);
					assertTrue(w.s() <= ringOf(w.rodzajStojacej(), scale) + 1e-9, "s poza pasem wody stojącej" + at);
				} else {
					assertTrue(w.s() == Double.POSITIVE_INFINITY && w.poziomBrzegu() == ColumnSample.NO_WATER,
							"brak wody stojącej" + at);
				}
				if (c.waterKind().isLake()) {
					assertTrue(w.s() <= 0, "jezioro bez ujemnej odległości od brzegu" + at);
				}
				if (c.waterKind() == WaterKind.RIVER) {
					assertTrue(w.odlKoryta() <= 0, "d > 0 w korycie" + at);
				}
				ColumnSample.Region reg = c.region();
				assertTrue(reg.oceanicznosc() >= 0 && reg.oceanicznosc() <= 1 && reg.podgorskosc() >= 0
						&& reg.podgorskosc() <= 1, "pola regionalne" + at);
				assertTrue(Double.isFinite(t.sBar()) && t.nach() >= 0 && t.nach() < 90
						&& (Double.isNaN(t.eksp()) || t.eksp() >= 0 && t.eksp() < 360), "siatka terenu" + at);
			}
			System.out.println(String.format(Locale.ROOT,
					"[pola siedlisk] %s: %d kolumn, przy ciekach %d, w dnie %d, przy wodzie stojącej %d, z fliszem %d, formy %s",
					scale.id(), points.size(), rivers, floors, standing, flysch, seen));
			assertTrue(rivers > 1_000 && floors > 500 && standing > 200 && flysch > 100, "za mało kolumn przy wodach lub w górach");
		}
	}

	/** Pas wody stojącej (m), w którym {@link ColumnSample.Wody#s()} jest skończone (javadoc pola). */
	private static double ringOf(ColumnSample.RodzajStojacej kind, LandscapeScale scale) {
		double k = scale.local();
		return switch (kind) {
			case JEZIORO_BEZODPLYWOWE -> 150 * k;
			case JEZIORO_RYNNOWE -> Math.max(70, 150 * k);
			case STARORZECZE -> 40 * k;
			default -> 45;
		};
	}

	/**
	 * Pierścień wokół jeziora bezodpływowego i jeziora rynnowego sięga 150 m·k za brzeg (strefa olsu,
	 * §4.4 planu M2): idąc od jeziora na zewnątrz, s rośnie aż do pasa, a dalej jest +∞.
	 */
	@Test
	void lakeRingsReachOlsBand() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			boolean real = scale == LandscapeScale.REALISTIC;
			for (ColumnSample.RodzajStojacej kind : new ColumnSample.RodzajStojacej[] {
					ColumnSample.RodzajStojacej.JEZIORO_BEZODPLYWOWE, ColumnSample.RodzajStojacej.JEZIORO_RYNNOWE}) {
				double[] site = RiverNetworkTest.find(m, c -> c.waterKind() == WaterKind.LAKE
						&& c.wody().rodzajStojacej() == kind, real ? 900 : 60);
				assertTrue(site != null, "brak jeziora " + kind + " w skali " + scale.id());
				double ring = ringOf(kind, scale);
				double best = 0;
				for (int a = 0; a < 16; a++) {
					double ang = a * Math.PI / 8;
					double far = 0;
					for (int q = 0; q < 6_000; q++) {
						ColumnSample.Wody w = m.sample(site[0] + Math.cos(ang) * q, site[1] + Math.sin(ang) * q).wody();
						if (w.rodzajStojacej() == kind) {
							far = Math.max(far, w.s());
						} else if (w.s() == Double.POSITIVE_INFINITY && far > 0) {
							break;
						}
					}
					best = Math.max(best, far);
				}
				System.out.println(String.format(Locale.ROOT, "[pierścień jeziora] %s %s: największe s %.1f m (pas %.0f m)",
						scale.id(), kind, best, ring));
				assertTrue(best > ring - 3 && best <= ring + 1e-9, "pierścień " + kind + ": największe s " + best);
			}
		}
	}

	/** Piaszczystość to kwantyl szumu: rozkład bliski jednostajnemu na [0, 1]. */
	@Test
	void sandinessIsUniform() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		double[] v = new double[10_000];
		int n = 0;
		for (int i = 0; i < v.length; i++) {
			ColumnSample c = m.sample((i % 100) * 1_217.0 - 60_000, (i / 100) * 1_193.0 - 60_000);
			if (c.waterKind() != WaterKind.SEA) {
				v[n++] = c.teren().piask();
			}
		}
		assertTrue(n > 5_000, "za mało lądu: " + n);
		v = Arrays.copyOf(v, n);
		Arrays.sort(v);
		for (int q = 1; q < 10; q++) {
			assertEquals(q / 10.0, v[q * v.length / 10], 0.05, "kwantyl " + q + "/10 piaszczystości");
		}
	}

	@Test
	void mountainsNeverTouchLowlandRegions() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		int beskidy = 0;
		for (long cx = -40; cx <= 40; cx++) {
			for (long cz = -40; cz <= 40; cz++) {
				if (m.regionType(cx, cz) != LandscapeType.BESKIDY) {
					continue;
				}
				beskidy++;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						assertFalse(m.regionType(cx + dx, cz + dz).isLowland(),
								"Beskidy w komórce " + cx + "," + cz + " graniczą z niziną " + m.regionType(cx + dx, cz + dz) + " w " + (cx + dx) + "," + (cz + dz));
					}
				}
			}
		}
		assertTrue(beskidy > 0, "brak Beskidów w obszarze testowym ok. 5000 × 5000 km");
	}

	@Test
	void allRegionTypesOccur() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		int[] count = new int[LandscapeType.values().length];
		for (long cx = -40; cx <= 40; cx++) {
			for (long cz = -40; cz <= 40; cz++) {
				count[m.regionType(cx, cz).ordinal()]++;
			}
		}
		StringBuilder sb = new StringBuilder();
		for (LandscapeType t : LandscapeType.values()) {
			sb.append(t).append('=').append(count[t.ordinal()]).append(' ');
		}
		System.out.println("Rozkład makroregionów: " + sb);
		for (LandscapeType t : LandscapeType.values()) {
			if (t.isRegionType()) {
				assertTrue(count[t.ordinal()] > 0, "nie wystąpił typ " + t);
			}
		}
	}

	@Test
	void elevationsStayWithinPolishRange() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		for (int i = 0; i < 4_000; i++) {
			double x = (i % 63) * 31_337.0 - 1_000_000;
			double z = (i / 63) * 29_989.0 - 1_000_000;
			ColumnSample s = m.sample(x, z);
			double max = s.type().isLowland() ? 420 : 1_800;
			assertTrue(s.surface() > -120 && s.surface() < max,
					"wysokość poza zakresem: " + s + " w " + x + "," + z);
		}
	}

	/**
	 * Woda stojąca musi być otoczona lądem wyższym od lustra albo wodą o tym samym poziomie.
	 * Dla rzek dopuszczamy stopień 1 m (znane uproszczenie modelu M1).
	 */
	@Test
	void waterIsAlwaysContained() {
		assertWaterContained(new LandscapeModel(SEED, 1.0));
	}

	@Test
	void waterIsAlwaysContainedAtGameplayScale() {
		assertWaterContained(new LandscapeModel(SEED, LandscapeScale.GAMEPLAY, 1.0));
	}

	@Test
	void gameplayScaleHasSmallRegionsAndAllTypesNearby() {
		LandscapeModel m = new LandscapeModel(SEED, LandscapeScale.GAMEPLAY, 1.0);
		assertEquals(1_400.0, m.regionSize(), 1e-9);
		int[] count = new int[LandscapeType.values().length];
		for (long cx = -60; cx <= 60; cx++) {
			for (long cz = -60; cz <= 60; cz++) {
				count[m.regionType(cx, cz).ordinal()]++;
				if (m.regionType(cx, cz) == LandscapeType.BESKIDY) {
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							assertFalse(m.regionType(cx + dx, cz + dz).isLowland());
						}
					}
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		for (LandscapeType t : LandscapeType.values()) {
			sb.append(t).append('=').append(count[t.ordinal()]).append(' ');
			if (t.isRegionType()) {
				assertTrue(count[t.ordinal()] > 0, "w promieniu ok. 85 km brak typu " + t);
			}
		}
		System.out.println("Rozkład (skala rozgrywki, 170 x 170 km): " + sb);
	}

	private static void assertWaterContained(LandscapeModel m) {
		List<double[]> sites = new ArrayList<>();
		double[] sandr = LandscapePreview.find(m, LandscapeType.SANDR, true);
		double[] moraine = LandscapePreview.find(m, LandscapeType.WYSOCZYZNA_MORENOWA, true);
		double[] river = LandscapePreview.findRiver(m);
		for (double[] p : new double[][] {sandr, moraine, river}) {
			if (p != null) {
				sites.add(p);
			}
		}
		assertFalse(sites.isEmpty());
		int waterColumns = 0;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (double[] site : sites) {
			int n = 700;
			int x0 = (int) site[0] - n / 2;
			int z0 = (int) site[1] - n / 2;
			ColumnSample[][] g = new ColumnSample[n][n];
			java.util.stream.IntStream.range(0, n).parallel().forEach(j -> {
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
					waterColumns++;
					for (int[] d : dirs) {
						ColumnSample o = g[j + d[1]][i + d[0]];
						boolean ok;
						if (o.hasWater()) {
							// Między kolumnami koryta dozwolone bystrza i kaskady (woda spada do wody).
							ok = o.waterLevel() == c.waterLevel()
									|| c.waterKind() == WaterKind.RIVER && o.waterKind() == WaterKind.RIVER;
						} else {
							ok = o.surfaceMeters() >= c.waterLevel();
						}
						assertTrue(ok, "woda bez brzegu w " + (x0 + i) + "," + (z0 + j) + ": " + c + " obok " + o);
					}
				}
			}
		}
		assertTrue(waterColumns > 100, "za mało wody w obszarach testowych: " + waterColumns);
	}
}
