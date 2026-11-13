package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.polishforests.worldgen.chunk.VerticalScale;

/**
 * Step TT1 (docs/04-pionowy-wycinek.md, "Projekt TT1"): the relief of the Tatra complexes of the nearest massifs of three
 * seeds at gameplay scale, on the land height before the rivers ({@link LandscapeModel#landElevation}).
 * <ul>
 * <li>{@link #massifsArePlacedAndBounded}: every seed has a massif within the search radius of {@code find tatra}, the
 * highest summit is 2450–2530 m and below Y 720, nothing above the High Tatras.</li>
 * <li>{@link #partsHaveTheirHeights}: the crest of the Western Tatras 1900–2250 m, of the High Tatras 2300–2530 m, the
 * regle 1000–1500 m with a Giewont-type ridge up to about 1900 m, the Sub-Tatra Trough 800–1000 m, the foothills up to
 * 1000–1150 m, the Podhale basin 580–700 m.</li>
 * <li>{@link #uValleysHaveFlatFloors}: the cross profile of the U-shaped valleys: a flat floor (the floor width at 10% of
 * the depth against the width at 50%) and steep walls; the floor width against the depth in the report.</li>
 * <li>{@link #cirquesHeadTheValleys}: at least 70% of the U-shaped valleys begin at a cirque, and the floor of 90% of
 * them lies at least 800 m below the crest above it.</li>
 * <li>{@link #noStraightFaults}: no straight boundary (the outline of the complex, the regle edge, the bedrock edge, the
 * Western / High boundary; KT-4, Z10) and no height step anywhere in the complex up to the end of its fades, the
 * transition Tatras → Podhale → Beskids included.</li>
 * </ul>
 */
class TatraReliefTest {
	/** Massifs per seed. */
	static final int MASSIFS = 2;
	/** Lattice spacing of the scans (m). */
	static final double STEP = 30;

	/** A scanned lattice over the complex: u across (towards the Podhale), v along, in the local frame of the massif. */
	record Scan(LandscapeModel m, LandscapeModel.Tatra t, double u0, double v0, int nu, int nv, double step, double[] h,
			TatraPart[] part, int[] forms, Substrate[] rock) {
		int index(int a, int b) {
			return b * nu + a;
		}

		double u(int a) {
			return u0 + a * step;
		}

		double v(int b) {
			return v0 + b * step;
		}
	}

	static Scan scan(LandscapeModel m, LandscapeModel.Tatra t, double uMin, double uMax, double vHalf, double step) {
		int nu = (int) Math.floor((uMax - uMin) / step) + 1;
		int nv = (int) Math.floor(2 * vHalf / step) + 1;
		double[] h = new double[nu * nv];
		TatraPart[] part = new TatraPart[nu * nv];
		int[] forms = new int[nu * nv];
		Substrate[] rock = new Substrate[nu * nv];
		IntStream.range(0, nu * nv).parallel().forEach(k -> {
			double u = uMin + (k % nu) * step;
			double v = -vHalf + (k / nu) * step;
			double x = TatraSurvey.x(t, u, v);
			double z = TatraSurvey.z(t, u, v);
			h[k] = m.landElevation(x, z);
			TatraField.Column c = m.tatraColumn(x, z);
			boolean in = c != null && c.weight > 0.5;
			part[k] = in ? c.part : TatraPart.NONE;
			forms[k] = in ? c.landforms : 0;
			rock[k] = in ? c.rock : null;
		});
		return new Scan(m, t, uMin, -vHalf, nu, nv, step, h, part, forms, rock);
	}

	/** The massif and its Podhale side: from the southern foot to the basin, along the whole massif. */
	static Scan massifScan(LandscapeModel m, LandscapeModel.Tatra t, double step) {
		double k = m.scale().mountainSpacing();
		return scan(m, t, -(TatraField.WS + 1_500) * k, (TatraField.BASIN_CENTER + 1_000) * k, t.halfLength() + 1_500 * k, step);
	}

	static double percentile(List<Double> a, double p) {
		if (a.isEmpty()) {
			return Double.NaN;
		}
		double[] s = a.stream().mapToDouble(Double::doubleValue).sorted().toArray();
		return s[(int) Math.min(s.length - 1, Math.floor(p * s.length))];
	}

	@ParameterizedTest
	@MethodSource("pl.polishforests.worldgen.landscape.TatraSurvey#scales")
	void massifsArePlacedAndBounded(LandscapeScale sc) {
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(sc, seed);
			List<LandscapeModel.Tatra> ts = TatraSurvey.nearest(sc, seed, MASSIFS);
			assertFalse(ts.isEmpty(), "seed " + seed + ": no Tatra massif within " + TatraSurvey.box(sc) + " m");
			double nearest = Math.hypot(ts.get(0).x(), ts.get(0).z());
			// The radius of the wide phase of find tatra (PolishForestsCommands.tatraRadius): 300 km at gameplay scale, 2000
			// km at realistic scale (step TT5).
			assertTrue(nearest < TatraSurvey.box(sc), sc.id() + " seed " + seed + ": the nearest massif is " + nearest + " m away");
			for (LandscapeModel.Tatra t : ts) {
				Scan s = massifScan(m, t, TatraSurvey.step(sc, STEP));
				assertTrue(Arrays.stream(s.h()).allMatch(Double::isFinite), TatraSurvey.label(sc, seed, t) + ": a height that is not finite");
				double max = Arrays.stream(s.h()).max().orElseThrow();
				System.out.printf(Locale.ROOT, "%s: distance %.0f m, half length %.0f m, target %.0f m, highest %.1f m (Y %d), reach %.0f m%n",
						TatraSurvey.label(sc, seed, t), Math.hypot(t.x(), t.z()), t.halfLength(), t.targetSummit(), max,
						TatraSurvey.vertical(sc).topBlockY(max), t.reach());
				assertTrue(max >= 2_450 && max <= 2_530, TatraSurvey.label(sc, seed, t) + ": highest summit " + max + " m");
				assertTrue(TatraSurvey.vertical(sc).topBlockY(max) <= TatraSurvey.top(sc), TatraSurvey.label(sc, seed, t) + ": top above Y "
						+ TatraSurvey.top(sc));
			}
		}
	}

	@ParameterizedTest
	@MethodSource("pl.polishforests.worldgen.landscape.TatraSurvey#scales")
	void partsHaveTheirHeights(LandscapeScale sc) {
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(sc, seed);
			for (LandscapeModel.Tatra t : TatraSurvey.nearest(sc, seed, MASSIFS)) {
				Scan s = massifScan(m, t, TatraSurvey.step(sc, STEP));
				Map<TatraPart, List<Double>> heights = new EnumMap<>(TatraPart.class);
				for (int k = 0; k < s.h().length; k++) {
					heights.computeIfAbsent(s.part()[k], q -> new ArrayList<>()).add(s.h()[k]);
				}
				// The summits of the crest: the highest point of every strip across the range one valley spacing wide (so a
				// strip holds a summit between two passes), in the inner 80% of the massif.
				Map<TatraPart, List<Double>> crest = new EnumMap<>(TatraPart.class);
				int strip = (int) Math.round(TatraField.VALLEY_SPACING * m.scale().mountainSpacing() / s.step());
				for (int b0 = 0; b0 + strip <= s.nv(); b0 += strip) {
					if (Math.abs(s.v(b0 + strip / 2)) > 0.8 * t.halfLength()) {
						continue;
					}
					double best = -1;
					TatraPart bp = TatraPart.NONE;
					for (int b = b0; b < b0 + strip; b++) {
						for (int a = 0; a < s.nu(); a++) {
							int k = s.index(a, b);
							if (s.part()[k].massif() && s.h()[k] > best) {
								best = s.h()[k];
								bp = s.part()[k];
							}
						}
					}
					if (bp != TatraPart.NONE) {
						crest.computeIfAbsent(bp, q -> new ArrayList<>()).add(best);
					}
				}
				StringBuilder sb = new StringBuilder(TatraSurvey.label(sc, seed, t) + ":");
				heights.forEach((p, a) -> sb.append(String.format(Locale.ROOT, "%n   %s n %d p05 %.0f p50 %.0f p95 %.0f max %.0f", p,
						a.size(), percentile(a, 0.05), percentile(a, 0.5), percentile(a, 0.95), percentile(a, 1))));
				crest.forEach((p, a) -> sb.append(String.format(Locale.ROOT, "%n   crest %s n %d p10 %.0f p50 %.0f p90 %.0f max %.0f", p,
						a.size(), percentile(a, 0.1), percentile(a, 0.5), percentile(a, 0.9), percentile(a, 1))));
				System.out.println(sb);
				String l = TatraSurvey.label(sc, seed, t);
				List<Double> western = crest.getOrDefault(TatraPart.WESTERN_TATRA, List.of());
				List<Double> high = crest.getOrDefault(TatraPart.HIGH_TATRA, List.of());
				assertTrue(western.size() >= 4 && high.size() >= 4, l + ": crest strips western " + western.size() + ", high " + high.size());
				// The summits of the strips; a strip at the Western / High boundary may hold a summit of the transition.
				double westernIn = western.stream().filter(h -> h >= 1_900 && h <= 2_250).count() / (double) western.size();
				double highIn = high.stream().filter(h -> h >= 2_300 && h <= 2_530).count() / (double) high.size();
				System.out.printf(Locale.ROOT, "   summits in range: Western %.2f, High %.2f%n", westernIn, highIn);
				assertTrue(westernIn >= 0.75 && percentile(western, 1) <= 2_330, l + ": Western Tatra summits " + western);
				assertTrue(highIn >= 0.75 && percentile(high, 1) <= 2_530 && percentile(high, 0) >= 2_150, l + ": High Tatra summits " + high);
				List<Double> regle = heights.getOrDefault(TatraPart.REGLE, List.of());
				assertTrue(percentile(regle, 0.5) >= 1_000 && percentile(regle, 0.5) <= 1_500, l + ": regle median");
				// Step TT5: up to 1980 m, since the noisy boundary of the regle part (un 0.61–0.71) may take in the end of a side
				// ridge a little higher than the Giewont-type ridge (1967 m in a realistic massif of seed A).
				assertTrue(percentile(regle, 1) >= 1_750 && percentile(regle, 1) <= 1_980, l + ": Giewont-type ridge " + percentile(regle, 1));
				List<Double> trough = heights.getOrDefault(TatraPart.SUBTATRA_TROUGH, List.of());
				assertTrue(percentile(trough, 0.5) >= 800 && percentile(trough, 0.5) <= 1_000, l + ": trough median");
				List<Double> hills = heights.getOrDefault(TatraPart.GUBALOWKA_FOOTHILLS, List.of());
				assertTrue(percentile(hills, 1) >= 1_000 && percentile(hills, 1) <= 1_150, l + ": foothills top");
				List<Double> basin = heights.getOrDefault(TatraPart.PODHALE_BASIN, List.of());
				assertTrue(percentile(basin, 0.5) >= 580 && percentile(basin, 0.5) <= 700, l + ": basin median");
			}
		}
	}

	/** A cross section along the range (v) through a U-shaped valley floor column: the floor and wall widths. */
	record Cross(double relief, double w10, double w50) {
		double ratio() {
			return w10 / w50;
		}

		/** Mean slope of the walls between 10% and 50% of the depth (rise per meter). */
		double wallSlope() {
			return 0.4 * relief / Math.max(1e-9, (w50 - w10) / 2);
		}
	}

	static Cross cross(LandscapeModel m, LandscapeModel.Tatra t, double u, double v, double half, double step) {
		int n = (int) Math.round(half / step);
		double[] h = new double[2 * n + 1];
		for (int q = -n; q <= n; q++) {
			h[q + n] = m.landElevation(TatraSurvey.x(t, u, v + q * step), TatraSurvey.z(t, u, v + q * step));
		}
		int lo = n;
		for (int q = n - 3; q <= n + 3; q++) {
			if (h[q] < h[lo]) {
				lo = q;
			}
		}
		double left = Double.NEGATIVE_INFINITY;
		double right = Double.NEGATIVE_INFINITY;
		for (int q = 0; q < lo; q++) {
			left = Math.max(left, h[q]);
		}
		for (int q = lo + 1; q < h.length; q++) {
			right = Math.max(right, h[q]);
		}
		double relief = Math.min(left, right) - h[lo];
		return new Cross(relief, width(h, lo, h[lo] + 0.1 * relief) * step, width(h, lo, h[lo] + 0.5 * relief) * step);
	}

	/** Width of the contiguous run around {@code lo} below {@code level}. */
	private static double width(double[] h, int lo, double level) {
		int a = lo;
		while (a > 0 && h[a - 1] < level) {
			a--;
		}
		int b = lo;
		while (b < h.length - 1 && h[b + 1] < level) {
			b++;
		}
		return b - a + 1;
	}

	@ParameterizedTest
	@MethodSource("pl.polishforests.worldgen.landscape.TatraSurvey#scales")
	void uValleysHaveFlatFloors(LandscapeScale sc) {
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(sc, seed);
			for (LandscapeModel.Tatra t : TatraSurvey.nearest(sc, seed, MASSIFS)) {
				Scan s = massifScan(m, t, 60);
				double half = 0.5 * TatraField.VALLEY_SPACING * m.scale().mountainSpacing();
				List<Double> ratios = new ArrayList<>();
				List<Double> floorDepth = new ArrayList<>();
				List<Double> slopes = new ArrayList<>();
				int deep = 0;
				for (int k = 0; k < s.h().length; k += 7) {
					if ((s.forms()[k] & Landform.U_VALLEY.bit()) == 0) {
						continue;
					}
					Cross c = cross(m, t, s.u(k % s.nu()), s.v(k / s.nu()), half, 6);
					if (c.relief() < 150) {
						continue;
					}
					deep++;
					ratios.add(c.ratio());
					floorDepth.add(c.w10() / c.relief());
					slopes.add(c.wallSlope());
				}
				System.out.printf(Locale.ROOT, "%s: U-valley cross sections %d (relief >= 150 m), floor/wall width ratio p10 %.2f p50 %.2f p90 %.2f; "
						+ "floor width / depth p50 %.2f; wall slope 10-50%% p10 %.2f p50 %.2f p90 %.2f%n", TatraSurvey.label(sc, seed, t), deep,
						percentile(ratios, 0.1), percentile(ratios, 0.5), percentile(ratios, 0.9), percentile(floorDepth, 0.5),
						percentile(slopes, 0.1), percentile(slopes, 0.5), percentile(slopes, 0.9));
				assertTrue(deep >= 30, TatraSurvey.label(sc, seed, t) + ": " + deep + " U-valley cross sections");
				assertTrue(percentile(ratios, 0.5) >= 0.35, TatraSurvey.label(sc, seed, t) + ": the valleys are V-shaped");
				// Steep walls: at least 1 m per m (45 degrees) in the median between 10% and 50% of the depth. Step TT5: in the
				// realistic scale the forms are 3.33 times wider at the same heights, so the walls rise 0.6 m per m (31 degrees,
				// the walls of the Tatra troughs); in blocks about as steep as the gameplay walls (TatraFormsTest#slopesInBlocks).
				double wallMin = sc == LandscapeScale.REALISTIC ? 0.6 : 1.0;
				assertTrue(percentile(slopes, 0.5) >= wallMin, TatraSurvey.label(sc, seed, t) + ": gentle valley walls");
			}
		}
	}

	@ParameterizedTest
	@MethodSource("pl.polishforests.worldgen.landscape.TatraSurvey#scales")
	void cirquesHeadTheValleys(LandscapeScale sc) {
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(sc, seed);
			for (LandscapeModel.Tatra t : TatraSurvey.nearest(sc, seed, MASSIFS)) {
				Scan s = massifScan(m, t, TatraSurvey.step(sc, STEP));
				int[] label = new int[s.h().length];
				int valleys = 0;
				int headed = 0;
				int small = 0;
				List<Double> reliefs = new ArrayList<>();
				for (int k = 0; k < label.length; k++) {
					if (label[k] != 0 || (s.forms()[k] & Landform.U_VALLEY.bit()) == 0) {
						continue;
					}
					int id = ++valleys;
					List<Integer> cells = flood(s, label, k, id, Landform.U_VALLEY.bit());
					boolean cirque = false;
					for (int c : cells) {
						int a = c % s.nu();
						int b = c / s.nu();
						for (int da = -3; da <= 3 && !cirque; da++) {
							for (int db = -3; db <= 3 && !cirque; db++) {
								int aa = a + da;
								int bb = b + db;
								if (aa >= 0 && aa < s.nu() && bb >= 0 && bb < s.nv()
										&& (s.forms()[s.index(aa, bb)] & Landform.CIRQUE.bit()) != 0) {
									cirque = true;
								}
							}
						}
					}
					if (cells.size() < 40) {
						small++;
						continue;
					}
					if (cirque) {
						headed++;
					}
					// The floor against the crest above the valley: the highest point of the massif in the v range of the valley.
					int b0 = Integer.MAX_VALUE;
					int b1 = Integer.MIN_VALUE;
					double floor = Double.POSITIVE_INFINITY;
					for (int c : cells) {
						b0 = Math.min(b0, c / s.nu());
						b1 = Math.max(b1, c / s.nu());
						floor = Math.min(floor, s.h()[c]);
					}
					double crest = Double.NEGATIVE_INFINITY;
					for (int b = b0; b <= b1; b++) {
						for (int a = 0; a < s.nu(); a++) {
							crest = Math.max(crest, s.h()[s.index(a, b)]);
						}
					}
					reliefs.add(crest - floor);
				}
				int counted = valleys - small;
				double deepShare = reliefs.stream().filter(r -> r >= 800).count() / (double) Math.max(1, reliefs.size());
				System.out.printf(Locale.ROOT, "%s: U-shaped valleys %d (and %d pieces under 40 cells), %d begin at a cirque (%.0f%%); floor to "
						+ "crest p10 %.0f p50 %.0f m, %.0f%% at least 800 m%n", TatraSurvey.label(sc, seed, t), counted, small, headed,
						100.0 * headed / Math.max(1, counted), percentile(reliefs, 0.1), percentile(reliefs, 0.5), 100 * deepShare);
				assertTrue(deepShare >= 0.9, TatraSurvey.label(sc, seed, t) + ": valleys less than 800 m below the crest " + reliefs);
				assertTrue(counted >= 8, TatraSurvey.label(sc, seed, t) + ": " + counted + " U-shaped valleys");
				assertTrue(headed >= 0.7 * counted, TatraSurvey.label(sc, seed, t) + ": " + headed + " of " + counted + " valleys at a cirque");
			}
		}
	}

	/** Flood fill of the 4-connected cells with the landform bit from {@code k}; returns the cells. */
	private static List<Integer> flood(Scan s, int[] label, int k, int id, int bit) {
		List<Integer> out = new ArrayList<>();
		java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
		label[k] = id;
		q.add(k);
		while (!q.isEmpty()) {
			int c = q.poll();
			out.add(c);
			int a = c % s.nu();
			int b = c / s.nu();
			int[][] nb = {{a - 1, b}, {a + 1, b}, {a, b - 1}, {a, b + 1}};
			for (int[] p : nb) {
				if (p[0] < 0 || p[0] >= s.nu() || p[1] < 0 || p[1] >= s.nv()) {
					continue;
				}
				int n = s.index(p[0], p[1]);
				if (label[n] == 0 && (s.forms()[n] & bit) != 0) {
					label[n] = id;
					q.add(n);
				}
			}
		}
		return out;
	}

	@ParameterizedTest
	@MethodSource("pl.polishforests.worldgen.landscape.TatraSurvey#scales")
	void noStraightFaults(LandscapeScale sc) {
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(sc, seed);
			LandscapeModel o = TatraSurvey.without(sc, seed);
			for (LandscapeModel.Tatra t : TatraSurvey.nearest(sc, seed, MASSIFS)) {
				String l = TatraSurvey.label(sc, seed, t);
				Scan s = massifScan(m, t, TatraSurvey.step(sc, STEP));
				// Boundaries across the range: for every v line the u of the first change (from the south) into the regle and
				// the last end of the bedrock, and the northern outline of the complex (weight 0.5).
				double[] regle = new double[s.nv()];
				double[] outline = outline(m, t, s);
				double[] rock = new double[s.nv()];
				for (int b = 0; b < s.nv(); b++) {
					regle[b] = Double.NaN;
					rock[b] = Double.NaN;
					for (int a = 1; a < s.nu(); a++) {
						TatraPart p0 = s.part()[s.index(a - 1, b)];
						TatraPart p1 = s.part()[s.index(a, b)];
						if (Double.isNaN(regle[b]) && p0 != TatraPart.REGLE && p1 == TatraPart.REGLE) {
							regle[b] = s.u(a);
						}
						if (s.u(a) > 0 && s.rock()[s.index(a - 1, b)] != null && s.rock()[s.index(a, b)] == null) {
							rock[b] = s.u(a);
						}
					}
				}
				// Boundary along the range: for every u line in the massif the v of the Western / High change.
				List<Double> westHigh = new ArrayList<>();
				for (int a = 0; a < s.nu(); a++) {
					for (int b = 1; b < s.nv(); b++) {
						TatraPart p0 = s.part()[s.index(a, b - 1)];
						TatraPart p1 = s.part()[s.index(a, b)];
						if ((p0 == TatraPart.WESTERN_TATRA && p1 == TatraPart.HIGH_TATRA)
								|| (p0 == TatraPart.HIGH_TATRA && p1 == TatraPart.WESTERN_TATRA)) {
							westHigh.add(s.v(b));
							break;
						}
					}
				}
				// Step TT5: the window and the least deviation grow with the forms (3 km and 15 m at gameplay scale).
				double window = TatraSurvey.step(sc, 3_000);
				double minDev = TatraSurvey.step(sc, 15);
				double dRegle = minWindowDeviation(regle, s.step(), window);
				double dOutline = minWindowDeviation(outline, s.step(), window);
				double dRock = minWindowDeviation(rock, s.step(), window);
				double sdWestHigh = sd(westHigh);
				// Height steps: the largest difference between neighbors 0.5 m apart, against the old mountains (the steps found
				// and removed in stage 1 were 30-200 m, i.e. 60-400 per m; the steepest walls rise about 7 m per m).
				double jump = maxStep(m, t, 0.5);
				double jumpOld = maxStep(o, t, 0.5);
				System.out.printf(Locale.ROOT, "%s: least deviation from a straight line over %.0f m: regle edge %.0f m, outline %.0f m, "
						+ "rock edge %.0f m; Western / High boundary sd %.0f m (%d lines); steepest 0.5 m step %.2f (old terrain %.2f)%n", l,
						window, dRegle, dOutline, dRock, sdWestHigh, westHigh.size(), jump, jumpOld);
				assertTrue(dRegle >= minDev && dOutline >= minDev && dRock >= minDev, l + ": a straight boundary");
				assertTrue(westHigh.size() >= 20 && sdWestHigh >= TatraSurvey.step(sc, 30), l + ": a straight Western / High boundary");
				assertTrue(jump <= 10, l + ": a height jump of " + jump + " per m");
			}
		}
	}

	/** For every v line of the scan in the inner 60% of the massif: the u where the weight falls below 0.5 north of the basin. */
	static double[] outline(LandscapeModel m, LandscapeModel.Tatra t, Scan s) {
		double k = m.scale().mountainSpacing();
		double[] out = new double[s.nv()];
		IntStream.range(0, s.nv()).parallel().forEach(b -> {
			out[b] = Double.NaN;
			double v = s.v(b);
			if (Math.abs(v) > 0.6 * t.halfLength()) {
				return;
			}
			for (double u = TatraField.BASIN_CENTER * k; u < (TatraField.BASIN_CENTER + TatraField.BASIN + TatraField.FADE_NORTH) * k; u += 10) {
				if (m.tatraWeight(TatraSurvey.x(t, u, v), TatraSurvey.z(t, u, v)) < 0.5) {
					out[b] = u;
					return;
				}
			}
		});
		return out;
	}

	/**
	 * The least, over windows of {@code window} m along the range, of the largest deviation of the boundary positions from
	 * their least squares line (NaN positions skipped; windows with fewer than half the positions skipped).
	 */
	static double minWindowDeviation(double[] pos, double step, double window) {
		int n = (int) Math.round(window / step);
		double least = Double.POSITIVE_INFINITY;
		for (int s = 0; s + n <= pos.length; s += n / 2) {
			double sx = 0;
			double sy = 0;
			double sxx = 0;
			double sxy = 0;
			int c = 0;
			for (int i = s; i < s + n; i++) {
				if (!Double.isNaN(pos[i])) {
					sx += i;
					sy += pos[i];
					sxx += (double) i * i;
					sxy += i * pos[i];
					c++;
				}
			}
			if (c < n / 2) {
				continue;
			}
			double slope = (c * sxy - sx * sy) / (c * sxx - sx * sx);
			double icpt = (sy - slope * sx) / c;
			double dev = 0;
			for (int i = s; i < s + n; i++) {
				if (!Double.isNaN(pos[i])) {
					dev = Math.max(dev, Math.abs(pos[i] - (icpt + slope * i)));
				}
			}
			least = Math.min(least, dev);
		}
		return least;
	}

	static double sd(List<Double> a) {
		double mean = a.stream().mapToDouble(Double::doubleValue).average().orElse(0);
		return Math.sqrt(a.stream().mapToDouble(x -> (x - mean) * (x - mean)).average().orElse(0));
	}

	/** Largest height difference per meter between points {@code d} m apart on a random sample of the complex. */
	static double maxStep(LandscapeModel m, LandscapeModel.Tatra t, double d) {
		// The whole complex to the end of its fades (the transition to the Beskids on every side).
		double k = m.scale().mountainSpacing();
		double uMin = -(TatraField.WS + TatraField.FADE_SOUTH1 + TatraField.EDGE) * k;
		double uMax = (TatraField.BASIN_CENTER + TatraField.BASIN / 2 + TatraField.FADE_NORTH + TatraField.EDGE) * k;
		double vHalf = t.halfLength() + (TatraField.FADE_END1 + TatraField.EDGE) * k;
		return IntStream.range(0, 300_000).parallel().mapToDouble(i -> {
			double fu = (i * 0.6180339887498949) % 1.0;
			double fv = (i * 0.7548776662466927) % 1.0;
			double u = uMin + fu * (uMax - uMin);
			double v = -vHalf + fv * 2 * vHalf;
			double x = TatraSurvey.x(t, u, v);
			double z = TatraSurvey.z(t, u, v);
			// Step TT2: the pinnacles and the gully walls are the sharp relief (KT-4: steep on purpose, TatraFormsTest).
			if (m.tatraSharp(x, z) > 0 || m.tatraSharp(x + d, z) > 0 || m.tatraSharp(x, z + d) > 0) {
				return 0;
			}
			double h = m.landElevation(x, z);
			return Math.max(Math.abs(m.landElevation(x + d, z) - h), Math.abs(m.landElevation(x, z + d) - h)) / d;
		}).max().orElse(0);
	}
}
