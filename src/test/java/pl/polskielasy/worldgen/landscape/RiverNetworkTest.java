package pl.polskielasy.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Sieć rzeczna, morze i wybrzeże: brzegi wód, poprawność spływu, brak klifów przy źródłach oraz pola
 * cieków eksportowane do {@link ColumnSample.Wody} (M2, krok S2).
 */
class RiverNetworkTest {
	private static final long SEED = 20260927L;
	/** Wnętrze Beskidów dla ziarna {@link #SEED} (łata „beskidy” w {@code zloty_teren_m1.txt}); oszczędza szukania. */
	private static final double[] BESKIDY_REAL = {154_834, 1_058_738};
	private static final double[] BESKIDY_GAMEPLAY = {27_609, 3_254};

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
											"poziom rośnie przy ujściu dopływu");
								}
								break;
							}
							RiverNetwork.Node d = net.node(order, l.di(), l.dj());
							assertTrue(d.route() < n.route(), "spływ pod górę");
							assertTrue(net.level(d) <= net.level(n) + 1e-9,
									"poziom wody rośnie z biegiem cieku w " + n);
							n = d;
							checked++;
							assertTrue(step < 4_999, "cykl w sieci rzecznej od węzła " + n);
						}
					}
				}
			}
			assertTrue(checked > 50, "za mało sprawdzonych odcinków: " + checked);
		}
	}

	/**
	 * Przy źródłach potoków w górach różnica wysokości między sąsiednimi suchymi kolumnami nie może
	 * przekraczać stromego stoku; stary model rzek dawał tu pionowy klif.
	 */
	@Test
	void mountainStreamSourcesHaveNoCliffs() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		RiverNetwork net = networkOf(m);
		double[] site = find(m, s -> s.type() == LandscapeType.BESKIDY, 5_000);
		assertTrue(site != null, "brak Beskidów w obszarze testowym");
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
						// Tylko suchy teren: brzeg koryta nad wodą może być stromy.
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
		assertTrue(sources > 0, "brak źródeł potoków w górach");
		assertTrue(worst < 3.0, "klif przy źródle: różnica " + worst + " m na 1 m w " + where);
	}

	/**
	 * d (odległość od brzegu koryta) jest ciągła i ma ograniczony spadek. W korycie d ≤ 0, a w dnie
	 * doliny u ∈ [0, 1]. Miejsca: rzeka nizinna, rzeka rzędu 3 (silne meandry, dolina daleko od osi
	 * odcinka), rzeka rzędu 2 na nizinie i potok w Beskidach.
	 *
	 * <p>Każdy krok transektu (1 m) o |Δd| > 1,5 m zagęszczamy do 1/256 m, a największy podkrok jeszcze
	 * do 1/65536 m: skok (nieciągłość) zostaje wtedy duży, a stromy spadek maleje razem z krokiem.
	 * d liczone jest w układzie doliny (u wzdłuż, v w poprzek), w którym rysowane jest koryto. Przy
	 * silnie wygiętej dolinie układ jest ściśnięty, więc spadek d dochodzi lokalnie do ok. 8 m na 1 m
	 * (Odstępstwo S2 w docs/03-m2-biomy.md). Sprawdzamy: brak skoków, spadek w pasie stref nadwodnych
	 * (d ≤ 200 m·k) najwyżej 10 m na 1 m i powyżej 1,5 m na 1 m najwyżej w 1% kroków pasa, poza pasem
	 * najwyżej 4 m na 1 m.
	 */
	@Test
	void channelDistanceIsContinuousAndNonPositiveInChannel() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			boolean real = scale == LandscapeScale.REALISTIC;
			List<double[]> sites = new ArrayList<>();
			sites.add(find(m, c -> c.waterKind() == WaterKind.RIVER && c.type().isLowland(), real ? 500 : 25));
			sites.add(find(m, c -> c.waterKind() == WaterKind.RIVER && c.wody().rzad() == 3, real ? 700 : 40));
			sites.add(find(m, c -> c.waterKind() == WaterKind.RIVER && c.wody().rzad() == 2 && c.type().isLowland(),
					real ? 900 : 60));
			sites.add(findStream(m, real ? BESKIDY_REAL : BESKIDY_GAMEPLAY));
			int length = real ? 2_000 : 500;
			double band = 200 * scale.local();
			// {kroki, kroki w pasie, strome w pasie, kolumny koryta, kolumny dna, skoki}
			long[] cnt = new long[6];
			double[] worst = new double[2];
			String[] where = {"", "", ""};
			for (double[] site : sites) {
				assertTrue(site != null, "brak miejsca testowego w skali " + scale.id());
				for (int a = 0; a < 16; a++) {
					double ang = a * Math.PI / 16 + 0.1;
					double dx = Math.cos(ang);
					double dz = Math.sin(ang);
					double prev = Double.NaN;
					for (int k = -length / 2; k <= length / 2; k++) {
						double x = site[0] + dx * k;
						double z = site[1] + dz * k;
						ColumnSample c = m.sample(x, z);
						ColumnSample.Wody w = c.wody();
						double d = w.odlKoryta();
						if (c.waterKind() == WaterKind.RIVER) {
							cnt[3]++;
							assertTrue(d <= 0, "d > 0 w korycie: " + w + " w " + x + "," + z);
						}
						if (w.wDnie()) {
							cnt[4]++;
							assertTrue(w.u() >= 0 && w.u() <= 1, "u poza [0, 1] w dnie: " + w);
						} else {
							assertTrue(Double.isNaN(w.u()), "u poza dnem: " + w);
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
								where[q] = String.format(Locale.ROOT, "%.2f,%.2f (d %.2f, W %.2f)", x, z, d, w.szerKoryta());
							}
						}
						prev = d;
					}
				}
			}
			System.out.println(String.format(Locale.ROOT,
					"[d koryta] %s: %d kroków (%d w pasie d ≤ 200 m·k, %d powyżej 1,5 m na 1 m), %d kolumn koryta, %d w dnie,"
							+ " %d skoków; największy spadek w pasie %.2f m na 1 m w %s, poza pasem %.2f w %s",
					scale.id(), cnt[0], cnt[1], cnt[2], cnt[3], cnt[4], cnt[5], worst[0], where[0], worst[1], where[1]));
			assertTrue(cnt[3] > 50 && cnt[4] > 500, "za mało koryt i den w transektach");
			assertTrue(cnt[5] == 0, "d nieciągłe (" + cnt[5] + " skoków), np. w " + where[2]);
			assertTrue(worst[0] <= 10, "spadek d w pasie stref " + worst[0] + " m na 1 m w " + where[0]);
			assertTrue(worst[1] <= 4, "spadek d poza pasem stref " + worst[1] + " m na 1 m w " + where[1]);
			assertTrue(cnt[2] <= 0.01 * cnt[1], "za dużo stromych kroków w pasie stref: " + cnt[2] + " z " + cnt[1]);
		}
	}

	/**
	 * Spadek d (m na 1 m) na odcinku 1 m od (x, z) w kierunku (dx, dz), z zagęszczeniem do 1/256 m i
	 * największego podkroku do 1/65536 m; +∞, gdy różnica nie maleje z krokiem (skok).
	 */
	private static double refinedGradient(LandscapeModel m, double x, double z, double dx, double dz) {
		double best = 0;
		int bi = 0;
		double p = m.sample(x, z).wody().odlKoryta();
		for (int q = 1; q <= 256; q++) {
			double t = q / 256.0;
			double v = m.sample(x + dx * t, z + dz * t).wody().odlKoryta();
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
		double pp = m.sample(x + dx * t0, z + dz * t0).wody().odlKoryta();
		double fine = 0;
		for (int q = 1; q <= 256; q++) {
			double t = t0 + q / 65_536.0;
			double v = m.sample(x + dx * t, z + dz * t).wody().odlKoryta();
			fine = Math.max(fine, Math.abs(v - pp));
			pp = v;
		}
		return fine > 0.01 ? Double.POSITIVE_INFINITY : fine * 65_536;
	}

	/**
	 * Brzeg wypukły: w zakolach rzek nizinnych z silnymi meandrami połowa brzegów jest wypukła. Liczymy
	 * przekroje koryta: dla kolumny brzegu szukamy kolumny po drugiej stronie koryta (wzdłuż gradientu d)
	 * i sprawdzamy, czy dokładnie jedna z nich jest wypukła. Udział powierzchni pasa brzegu jest mniejszy
	 * od 1/2, bo w ciasnym zakolu brzeg wewnętrzny jest krótszy od zewnętrznego (promień R − W/2 wobec R + W/2).
	 * Stronę w świecie sprawdzamy na co piątym przekroju: koryto otacza brzeg wewnętrzny, więc w kole
	 * o promieniu 1,5W + d wokół brzegu wypukłego jest więcej wody niż wokół wklęsłego.
	 */
	@Test
	void aboutHalfOfMeanderBanksAreConvex() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			RiverNetwork net = networkOf(m);
			double[] site = find(m, c -> c.waterKind() == WaterKind.RIVER && c.type().isLowland(),
					scale == LandscapeScale.REALISTIC ? 500 : 25);
			assertTrue(site != null, "brak rzeki nizinnej w skali " + scale.id());
			// {kolumny brzegu, wypukłe, przekroje, przekroje z dokładnie jednym brzegiem wypukłym, wypukłe w przekrojach,
			// przekroje z różną ilością wody wokół brzegów, w tym z większą przy wypukłym}
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
						// Środek odcinka (meandry nie są tu wygaszane), na korycie: dolina bywa daleko od osi odcinka.
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
								ColumnSample.Wody wd = c.wody();
								double band = Math.max(0.3 * wd.szerKoryta(), 1.5);
								if (c.hasWater() || !(wd.odlKoryta() > 0 && wd.odlKoryta() <= band)) {
									continue;
								}
								cnt[0]++;
								if (wd.brzegWypukly()) {
									cnt[1]++;
								}
								// Druga strona koryta: wzdłuż gradientu d, o 2d + W.
								double gx = m.sample(x + 0.5, z).wody().odlKoryta() - m.sample(x - 0.5, z).wody().odlKoryta();
								double gz = m.sample(x, z + 0.5).wody().odlKoryta() - m.sample(x, z - 0.5).wody().odlKoryta();
								double gl = Math.hypot(gx, gz);
								if (gl < 0.5) {
									continue;
								}
								double jump = 2 * wd.odlKoryta() + wd.szerKoryta();
								ColumnSample o = m.sample(x - gx / gl * jump, z - gz / gl * jump);
								ColumnSample.Wody ow = o.wody();
								if (o.hasWater() || !(ow.odlKoryta() > 0 && ow.odlKoryta() <= 2 * band)
										|| Math.abs(ow.szerKoryta() - wd.szerKoryta()) > 0.05 * wd.szerKoryta()) {
									continue;
								}
								cnt[2]++;
								if (wd.brzegWypukly() != ow.brzegWypukly()) {
									cnt[3]++;
								}
								cnt[4] += (wd.brzegWypukly() ? 1 : 0) + (ow.brzegWypukly() ? 1 : 0);
								if (wd.brzegWypukly() != ow.brzegWypukly() && (ii * 31 + jj) % 5 == 0) {
									double r = 1.5 * wd.szerKoryta() + wd.odlKoryta();
									double here = riverAround(m, x, z, r);
									double there = riverAround(m, x - gx / gl * jump, z - gz / gl * jump, r);
									if (here != there) {
										cnt[5]++;
										if ((here > there) == wd.brzegWypukly()) {
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
					"[brzeg wypukły] %s: %d odcinków, %d kolumn brzegu (wypukłych %.3f powierzchni), %d przekrojów:"
							+ " dokładnie jeden brzeg wypukły w %.3f, wypukłych brzegów %.3f; więcej wody wokół wypukłego"
							+ " w %.3f z %d rozstrzygniętych",
					scale.id(), reaches, total[0], areaShare, total[2], opposite, share, sideOk, total[5]));
			assertTrue(reaches > 0 && total[2] > 200, "za mało przekrojów zakoli: " + reaches + " odcinków, " + total[2]);
			assertTrue(opposite > 0.8, "w przekroju koryta powinien być dokładnie jeden brzeg wypukły: " + opposite);
			assertTrue(share > 0.4 && share < 0.6, "udział brzegów wypukłych w przekrojach " + share);
			assertTrue(total[5] >= 20 && sideOk > 0.8, "brzeg wypukły po złej stronie łuku: " + sideOk + " z " + total[5]);
		}
	}

	/** Udział kolumn rzeki w kole o promieniu r wokół (x, z): siatka biegunowa 8 × 24 z wagą pola. */
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
	 * Strona łuku w układzie krzywej Kinoshity: w s = 0 krzywizna dθ/ds jest dodatnia (łuk w lewo, środek
	 * po stronie +v), w s = 0,5 ujemna. Punkt tuż obok krzywej po stronie środka łuku jest wewnętrzny.
	 */
	@Test
	void innerSideFollowsMeanderCurvature() {
		double theta = 1.5;
		for (double u : new double[] {0.0, 0.5}) {
			// Położenie krzywej w poprzek przy danym u: minimum odległości.
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
			assertTrue(MeanderField.innerSide(u, v0 + 0.03, theta) == leftInner, "strona +v przy u = " + u);
			assertTrue(MeanderField.innerSide(u, v0 - 0.03, theta) != leftInner, "strona -v przy u = " + u);
		}
		assertFalse(MeanderField.innerSide(0.0, 0.03, 0.0), "bez meandrów brzeg nie jest wypukły");
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
		add(sites, names, "wybrzeże", find(m, s -> s.waterKind() == WaterKind.SEA && s.type() == LandscapeType.MORZE
				&& s.surface() > -2, step));
		add(sites, names, "zalew", find(m, s -> s.waterKind() == WaterKind.SEA && s.type() == LandscapeType.POBRZEZE,
				step / 4));
		add(sites, names, "potok górski", findStream(m));
		add(sites, names, "starorzecze", find(m, s -> s.waterKind() == WaterKind.OXBOW, step / 4));
		add(sites, names, "rzeka nizinna", find(m, s -> s.waterKind() == WaterKind.RIVER && s.type().isLowland(),
				step / 4));
		System.out.println("Miejsca testowe: " + names);
		assertTrue(names.contains("wybrzeże") && names.contains("potok górski") && names.contains("rzeka nizinna"),
				"nie znaleziono wszystkich rodzajów wód: " + names);
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
							// Między kolumnami koryta dozwolone bystrza i kaskady (woda spada do wody),
							// przy ujściu do jeziora lub morza najwyżej stopień 1 m.
							boolean cascade = c.waterKind() == WaterKind.RIVER && o.waterKind() == WaterKind.RIVER;
							boolean flowing = c.waterKind() == WaterKind.RIVER || o.waterKind() == WaterKind.RIVER;
							ok = o.waterLevel() == c.waterLevel() || cascade
									|| flowing && Math.abs(o.waterLevel() - c.waterLevel()) <= 1;
						} else {
							ok = o.surfaceMeters() >= c.waterLevel();
						}
						assertTrue(ok, names.get(q) + ": woda bez brzegu w " + (x0 + i) + "," + (z0 + j) + ": " + c
								+ " obok " + o);
					}
				}
			}
		}
	}

	/** Potok w górach: punkt na osi koryta cieku rzędu 1 lub 2 w Beskidach. */
	static double[] findStream(LandscapeModel m) {
		return findStream(m, find(m, s -> s.type() == LandscapeType.BESKIDY, m.scale() == LandscapeScale.REALISTIC ? 5_000 : 200));
	}

	/** Potok w górach w pobliżu punktu {@code site} w Beskidach. */
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
						if (c.waterKind() == WaterKind.RIVER && c.type() == LandscapeType.BESKIDY) {
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
