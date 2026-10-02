package pl.polskielasy.worldgen.landscape;

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
 * Pola regionalne O i P (M2, krok S3; docs/03-m2-biomy.md §3.2, §9, §12.1): zakres [0, 1], gładkość, pas bez
 * buka i świerka na 10–20% lądu i zasięg P ≥ 0,5 wokół pasm górskich.
 */
class RegionalFieldTest {
	private static final long[] SEEDS = {20260927L, 1L, 2L};
	/** Połowa boku obszaru pomiaru udziałów przy zs = 1 (ok. 6–7 fal szumu prowincji na bok). */
	private static final double HALF = 3_000_000;
	private static final LandscapeScale[] SCALES = {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY};

	private static double zs(LandscapeModel m) {
		return m.regionSize() / LandscapeModel.BASE_REGION_SIZE;
	}

	/** O i P są skończone i leżą w [0, 1] na lądzie i morzu; próbka modelu niesie wartości siatki. */
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
					assertTrue(r.oceanicznosc() >= 0 && r.oceanicznosc() <= 1, "O poza [0, 1] w " + x + "," + z + ": " + r);
					assertTrue(r.podgorskosc() >= 0 && r.podgorskosc() <= 1, "P poza [0, 1] w " + x + "," + z + ": " + r);
					min = Math.min(min, r.oceanicznosc());
					max = Math.max(max, r.oceanicznosc());
				}
			}
			System.out.println(String.format(Locale.ROOT, "[pola regionalne] %s: O od %.3f do %.3f", scale.id(), min, max));
			for (int i = 0; i < 300; i++) {
				double x = (i * 7_919.0 - 1_000_000) * zs;
				double z = (i * -3_571.0 + 500_000) * zs;
				assertEquals(rf.sample(x, z), m.sample(x, z).region(), "pola próbki w " + x + "," + z);
			}
		}
	}

	/**
	 * W węźle siatki O jest wartością węzła, a P średnią 5 × 5 węzłów przed uśrednieniem (zapis w float),
	 * między węzłami wartość leży między wartościami narożników oczka.
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
				assertEquals(rf.oceanicznosc(ix * g, iz * g), r.oceanicznosc(), 1e-6, "O w węźle " + ix + "," + iz);
				double sum = 0;
				for (int a = -2; a <= 2; a++) {
					for (int b = -2; b <= 2; b++) {
						sum += rf.podgorskosc((ix + a) * g, (iz + b) * g);
					}
				}
				assertEquals(sum / 25, r.podgorskosc(), 1e-6, "P w węźle " + ix + "," + iz);
				ColumnSample.Region mid = rf.sample((ix + 0.3) * g, (iz + 0.6) * g);
				double lo = 1;
				double hi = 0;
				for (int a = 0; a <= 1; a++) {
					for (int b = 0; b <= 1; b++) {
						double o = rf.sample((ix + a) * g, (iz + b) * g).oceanicznosc();
						lo = Math.min(lo, o);
						hi = Math.max(hi, o);
					}
				}
				assertTrue(mid.oceanicznosc() >= lo - 1e-9 && mid.oceanicznosc() <= hi + 1e-9, "O między węzłami");
			}
		}
	}

	/** Gładkość: na liniach wzdłuż X, Z i po przekątnej zmiana O i P na 1 km·zs nie przekracza 0,02. */
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
							maxO = Math.max(maxO, Math.abs(r.oceanicznosc() - prev.oceanicznosc()));
							maxP = Math.max(maxP, Math.abs(r.podgorskosc() - prev.podgorskosc()));
							prev = r;
						}
					}
				}
				System.out.println(String.format(Locale.ROOT,
						"[pola regionalne] %s, ziarno %d: największa zmiana na km·zs: O %.4f, P %.4f", scale.id(), seed, maxO,
						maxP));
				assertTrue(maxO <= 0.02, "O zmienia się o " + maxO + " na km·zs");
				assertTrue(maxP <= 0.02, "P zmienia się o " + maxP + " na km·zs");
			}
		}
	}

	/**
	 * Między zasięgiem buka (O ≥ 0,40 lub P ≥ 0,40) a zasięgiem naturalnego świerka (O &lt; 0,30 lub P ≥ 0,50)
	 * leży pas bez obu gatunków, jak na Mazowszu (§9). Zajmuje 10–20% lądu na każdym z trzech ziaren.
	 */
	@Test
	void bandWithoutBeechAndSpruceCoversTenToTwentyPercentOfLand() {
		for (long seed : SEEDS) {
			double[] share = shares(new LandscapeModel(seed, LandscapeScale.REALISTIC, 1.0));
			System.out.println(String.format(Locale.ROOT,
					"[pola regionalne] realistyczna, ziarno %d: pas bez buka i świerka %.3f lądu; buk %.3f, świerk %.3f,"
							+ " jodła (P ≥ 0,5) %.3f, O ≥ 0,75 %.3f",
					seed, share[0], share[1], share[2], share[3], share[4]));
			assertTrue(share[0] >= 0.10 && share[0] <= 0.20, "pas bez buka i świerka: " + share[0]);
		}
		// Skala rozgrywki to te same pola w skali zs (bez osobnej kalibracji).
		double[] share = shares(new LandscapeModel(SEEDS[0], LandscapeScale.GAMEPLAY, 1.0));
		System.out.println(String.format(Locale.ROOT, "[pola regionalne] rozgrywka, ziarno %d: pas %.3f lądu", SEEDS[0],
				share[0]));
		assertTrue(share[0] >= 0.10 && share[0] <= 0.20, "pas bez buka i świerka (rozgrywka): " + share[0]);
	}

	/** Udziały lądu: {pas, buk, świerk, P ≥ 0,5, O ≥ 0,75} na siatce co 20 km·zs. */
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
				double o = r.oceanicznosc();
				double p = r.podgorskosc();
				boolean buk = o >= 0.40 || p >= 0.40;
				boolean swierk = o < 0.30 || p >= 0.50;
				n[0] += !buk && !swierk ? 1 : 0;
				n[1] += buk ? 1 : 0;
				n[2] += swierk ? 1 : 0;
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
	 * P ≥ 0,5 sięga ok. 150–250 km·zs od osi pasma (§3.2): mediana odległości granicy P = 0,5 od osi pasma
	 * (linia zerowa {@code mountainRaw}, tam gdzie maski pasma i odsunięcia od morza ≥ 0,5). Liczymy tylko granicę
	 * wzdłuż pasma, gdzie maski są prawie pełne (≥ 0,9), a nie przy końcach łańcuchów. Punkty granicy zbieramy
	 * z {@link #RANGE_SEEDS} ziaren razem: na pojedynczym ziarnie bywa ich kilkadziesiąt, a mediany ziaren
	 * rozchodzą się bardziej niż przedział planu (wypisujemy je informacyjnie).
	 */
	@Test
	void podgorskoscReachesAroundRanges() {
		List<Double> all = new ArrayList<>();
		StringBuilder perSeed = new StringBuilder();
		for (long seed : RANGE_SEEDS) {
			List<Double> dist = rangeBoundaryDistances(new LandscapeModel(seed, LandscapeScale.REALISTIC, 1.0));
			all.addAll(dist);
			dist.sort(null);
			perSeed.append(dist.isEmpty() ? String.format(Locale.ROOT, " %d: brak;", seed)
					: String.format(Locale.ROOT, " %d: %.0f km (%d);", seed, dist.get(dist.size() / 2) / 1e3, dist.size()));
		}
		all.sort(null);
		assertTrue(all.size() >= 1_000, "za mało punktów granicy P = 0,5 przy pasmach: " + all.size());
		double median = all.get(all.size() / 2) / 1e3;
		System.out.println(String.format(Locale.ROOT,
				"[pola regionalne] granica P = 0,5 od osi pasma, %d ziaren razem (%d punktów): kwartyle %.0f / %.0f / %.0f km;"
						+ " mediany ziaren:%s",
				RANGE_SEEDS.length, all.size(), all.get(all.size() / 4) / 1e3, median, all.get(3 * all.size() / 4) / 1e3,
				perSeed));
		assertTrue(median >= 150 && median <= 250, "mediana zasięgu P ≥ 0,5: " + median + " km");
	}

	/** Ziarna pomiaru zasięgu P (te z {@link #SEEDS} i siedem kolejnych). */
	private static final long[] RANGE_SEEDS = {20260927L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L};

	/**
	 * Odległości punktów granicy P = 0,5 (zmiana wzdłuż X na siatce co 4 km w kwadracie ±1500 km, maski ≥ 0,9)
	 * od najbliższego punktu osi pasma (zmiana znaku {@code mountainRaw} między sąsiednimi punktami siatki,
	 * maski ≥ 0,5), do 450 km.
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
				high[i][j] = rf.sample(x, z).podgorskosc() >= 0.5;
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

	/** Maska pasma razy odsunięcie od morza: mountainLinear / (1 − |mountainRaw|). */
	private static double masks(LandscapeModel m, double x, double z) {
		double ridge = 1 - Math.abs(m.mountainRaw(x, z));
		return ridge > 0 ? m.mountainLinear(x, z) / ridge : 0;
	}

	private static long binKey(double x, double z, double bin) {
		return Math.floorDiv((long) x, (long) bin) * 1_000_003L + Math.floorDiv((long) z, (long) bin);
	}

	/** Bliskość morza podnosi O: średnie O w pasie 50 km·zs od brzegu jest wyższe niż ponad 300 km·zs od niego. */
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
					coast += m.regional().sample(x, z).oceanicznosc();
					nc++;
				} else if (d > 300_000 && m.seaField(x, z) > 0) {
					inland += m.regional().sample(x, z).oceanicznosc();
					ni++;
				}
			}
		}
		coast /= nc;
		inland /= ni;
		System.out.println(String.format(Locale.ROOT, "[pola regionalne] średnie O: przy brzegu %.3f (%d), w głębi lądu %.3f (%d)",
				coast, nc, inland, ni));
		assertTrue(coast > inland + 0.05, "O przy brzegu " + coast + ", w głębi " + inland);
	}

	/**
	 * Człon Wz patrzy na zachód (−X, decyzja M2-3): na lądzie 50–150 km·zs od brzegu morza leżącego na zachodzie
	 * O jest średnio wyższe (o ok. 0,06) niż w takim samym pasie przy morzu leżącym na wschodzie; przy odwróconym
	 * kierunku członu różnica miałaby przeciwny znak. Stronę morza wyznacza
	 * gradient {@code seaField} (rośnie w głąb lądu), tylko przy brzegu biegnącym prawie z północy na południe.
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
					double o = m.regional().sample(x, z).oceanicznosc();
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
				"[pola regionalne] średnie O 50–150 km od brzegu: morze na zachodzie %.3f (%d), na wschodzie %.3f (%d)", west,
				nw, east, ne));
		assertTrue(nw >= 500 && ne >= 500, "za mało punktów: " + nw + ", " + ne);
		assertTrue(west > east + 0.03, "O przy morzu na zachodzie " + west + ", na wschodzie " + east);
	}

	/**
	 * Wynik nie zależy od stanu pamięci ani od wątków: pola z 4 miejscami w pamięci (ciągłe wypychanie kafli)
	 * pytane równolegle w odwrotnej kolejności dają te same wartości co pola domyślne pytane po kolei.
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
				assertEquals(ref[i], par[i], "punkt " + pts[i][0] + "," + pts[i][1] + " (" + scale.id() + ")");
			}
		}
	}
}
