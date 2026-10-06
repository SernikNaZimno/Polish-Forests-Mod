package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.RiverNetworkProbe;

/**
 * Waterside zones on river cross-sections (docs/03-m2-biomy.md §4, §12.1): up to 150 cross-sections per class A, B
 * and C at each scale, chosen evenly from a 240 × 240 point grid (randomly by a hash of the position, not by the
 * coordinates). A cross-section starts at the channel bank and runs straight along the normal to the
 * channel (gradient of d at the bank) until d starts to decrease (another channel or a bend is closer). Zone order:
 * channel → willow scrub → herb fringe → white willow forest → poplar forest → elm-ash forest → slope (A),
 * channel → tall herbs → OlJ → zonal (B), channel → gravel bar/willow scrub → gray alder forest → zonal (C).
 * Minimum widths in blocks (E11): willow scrub 3, OlJ 6, alder carr 10.
 */
class WatersideZonesTest {
	static final long SEED = 20260927L;
	static final int PER_CLASS = 150;
	/**
	 * Cross-sections per class for the alder carr chords (terrain geometry fix, docs/m2/poprawka-geometrii.md, step
	 * K0): with {@link #PER_CLASS} the gameplay scale has only a few alder carr chords, so the share of chords of at least
	 * 10 blocks was decided by 1–2 chords. The first {@link #PER_CLASS} sections of each class are the same as before,
	 * so the other checks do not change.
	 */
	static final int ALDER_CARR_PER_CLASS = 600;

	/** Cross-section: watercourse class, column codes every {@code step} m from the bank. */
	record Section(WatersideZones.StreamClass streamClass, int[] codes, boolean[] onValleyFloor, boolean[] standingWater, double step, double x,
			double z) {
	}

	static List<Section> sections(LandscapeScale sc, int perClass) {
		LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
		HabitatClassifier k = new HabitatClassifier(SEED, sc, HabitatClassifier.Mode.NATURAL);
		int n = 240;
		List<Section> result = Collections.synchronizedList(new ArrayList<>());
		IntStream.range(0, n * n).parallel().forEach(q -> {
			double[] g = gridPoint(sc, q);
			Section p = section(m, k, g[0], g[1]);
			if (p != null) {
				result.add(p);
			}
		});
		// Order independent of threads and of the position: by the hash of the starting point; perClass per class.
		List<Section> l = new ArrayList<>(result);
		l.sort((a, b) -> Long.compare(hash(a), hash(b)));
		Map<WatersideZones.StreamClass, Integer> count = new EnumMap<>(WatersideZones.StreamClass.class);
		List<Section> out = new ArrayList<>();
		for (Section p : l) {
			int c = count.getOrDefault(p.streamClass(), 0);
			if (c < perClass) {
				out.add(p);
				count.put(p.streamClass(), c + 1);
			}
		}
		return out;
	}

	private static long hash(Section p) {
		return pl.polishforests.worldgen.landscape.Noise.mix(Double.doubleToLongBits(p.x()) * 31 + Double.doubleToLongBits(p.z()));
	}

	/** Cross-section from the bank of the channel nearest to the point (x0, z0), or null when the point is too far from a watercourse. */
	static Section section(LandscapeModel m, HabitatClassifier k, double x0, double z0) {
		ColumnSample s = m.sample(x0, z0);
		ColumnSample.Waters w = s.waters();
		if (s.hasWater() || w.streamOrder() == 0 || !(w.channelDist() > 0 && w.channelDist() < 150 * m.scale().local())) {
			return null;
		}
		// To the bank: down the gradient of d.
		double x = x0;
		double z = z0;
		for (int i = 0; i < 400; i++) {
			double d = m.sample(x, z).waters().channelDist();
			if (d <= 0.25) {
				break;
			}
			double[] g = gradient(m, x, z);
			if (g == null) {
				return null;
			}
			double step = Math.max(0.2, Math.min(d * 0.5, 20));
			x -= g[0] * step;
			z -= g[1] * step;
		}
		ColumnSample bank = m.sample(x, z);
		if (!(Math.abs(bank.waters().channelDist()) < 1.0) || bank.waters().streamOrder() == 0) {
			return null;
		}
		// The channel must have water on the other side of the bank (no dry valley heads).
		double[] g0 = gradient(m, x, z);
		if (g0 == null || !m.sample(x - g0[0] * 1.5, z - g0[1] * 1.5).hasWater()) {
			return null;
		}
		HabitatClassifier.Column c = new HabitatClassifier.Column(k, bank, x, z);
		WatersideZones.StreamClass streamClass = WatersideZones.streamClass(c);
		double channelWidth = bank.waters().channelWidth();
		double kk = m.scale().local();
		double length = switch (streamClass) {
			case A -> Math.max(300 * kk, 3.5 * channelWidth);
			case B -> 120 * kk;
			case C -> 80 * kk;
		};
		double step = 0.5;
		int steps = (int) (length / step);
		int[] codes = new int[steps];
		boolean[] onValleyFloor = new boolean[steps];
		boolean[] standingWater = new boolean[steps];
		// Straight line along the normal to the channel at the bank (gradient of d). Further from the channel the gradient
		// turns with the valley layout (meanders), so a cross-section following it would zigzag back through the same belts.
		double[] g = g0;
		double dPrev = -1;
		for (int i = 0; i < steps; i++) {
			x += g[0] * step;
			z += g[1] * step;
			ColumnSample t = m.sample(x, z);
			double d = t.waters().channelDist();
			if (!(d >= dPrev - 2.0) || t.waters().streamOrder() == 0 || t.hasWater()) {
				// Beyond the ridge of the d field (another watercourse is closer): end of the cross-section.
				return i * step >= 0.5 * length
						? new Section(streamClass, java.util.Arrays.copyOf(codes, i), java.util.Arrays.copyOf(onValleyFloor, i),
								java.util.Arrays.copyOf(standingWater, i), step, x0, z0)
						: null;
			}
			dPrev = Math.max(dPrev, d);
			codes[i] = k.classify(t, x, z);
			onValleyFloor[i] = new HabitatClassifier.Column(k, t, x, z).onValleyFloor();
			standingWater[i] = t.waters().standingWaterKind() != ColumnSample.StandingWaterKind.NONE && Double.isFinite(t.waters().s());
		}
		return new Section(streamClass, codes, onValleyFloor, standingWater, step, x0, z0);
	}

	/** Direction of increasing d (unit vector) from ±1 m differences, or null. */
	static double[] gradient(LandscapeModel m, double x, double z) {
		double gx = m.sample(x + 1, z).waters().channelDist() - m.sample(x - 1, z).waters().channelDist();
		double gz = m.sample(x, z + 1).waters().channelDist() - m.sample(x, z - 1).waters().channelDist();
		double len = Math.hypot(gx, gz);
		return Double.isFinite(len) && len > 1e-6 ? new double[] {gx / len, gz / len} : null;
	}

	/**
	 * Rank of the zone in the order from the channel, or −1 when the column is not part of the order: spring areas,
	 * spring fens, oxbow lakes with rings, standing water and ash-alder forest from seepage at the slope foot (outside the floor).
	 */
	static int rank(WatersideZones.StreamClass streamClass, int code, boolean onValleyFloor, int soFar) {
		HabitatBiome b = Habitat.biome(code);
		Zone s = Habitat.zone(code);
		Association z = Habitat.association(code);
		if (b.isWater() && b != HabitatBiome.LAKE) {
			return 0;
		}
		if (s == Zone.SPRING_AREA || z == Association.SPRING_FED || z == Association.SPRING_FEN || z == Association.OXBOW_LAKE
				|| b == HabitatBiome.LAKE || b == HabitatBiome.REEDBED || b == HabitatBiome.ASH_ALDER_FOREST && !onValleyFloor) {
			return -1;
		}
		return switch (streamClass) {
			case A -> {
				if (s == Zone.POINT_BAR || s == Zone.WILLOW_SCRUB) {
					yield 1;
				}
				if (s == Zone.HERB_FRINGE && soFar < 3) {
					yield 2;
				}
				if (b == HabitatBiome.WILLOW_POPLAR_FOREST) {
					yield z == Association.SALICETUM_ALBAE ? 3 : 4;
				}
				if (b == HabitatBiome.ELM_ASH_FOREST || z == Association.BACKSWAMP) {
					yield 5;
				}
				yield 6;
			}
			case B -> {
				if (s == Zone.TALL_HERBS || s == Zone.RIVERSIDE_WILLOWS) {
					yield 1;
				}
				yield b == HabitatBiome.ASH_ALDER_FOREST ? 2 : 3;
			}
			case C -> {
				if (s == Zone.GRAVEL_BAR || s == Zone.WILLOW_SCRUB || s == Zone.MONTANE_TALL_HERBS) {
					yield 1;
				}
				yield b == HabitatBiome.GRAY_ALDER_FOREST || b == HabitatBiome.ASH_ALDER_FOREST ? 2 : 3;
			}
		};
	}

	/**
	 * Whether the ranks along the cross-section do not decrease. Columns with rank −1 are skipped, and a step back of at
	 * most 1 m (two columns) is treated as threshold flicker on the fine relief of the floor edge, not as a change of order.
	 */
	static boolean isOrdered(Section p) {
		int max = 0;
		int backtracked = 0;
		for (int i = 0; i < p.codes().length; i++) {
			int r = rank(p.streamClass(), p.codes()[i], p.onValleyFloor()[i], max);
			if (r < 0) {
				continue;
			}
			if (r < max) {
				if (++backtracked * p.step() > 1.0) {
					return false;
				}
				continue;
			}
			backtracked = 0;
			max = r;
		}
		return true;
	}

	/** Lengths (m) of completed runs of columns meeting the condition (without runs at the ends of the cross-section). */
	static List<Double> runs(Section p, java.util.function.IntPredicate condition, boolean includeShore) {
		List<Double> l = new ArrayList<>();
		int start = -1;
		for (int i = 0; i <= p.codes().length; i++) {
			boolean w = i < p.codes().length && condition.test(i);
			if (w && start < 0) {
				start = i;
			} else if (!w && start >= 0) {
				if ((start > 0 || includeShore) && i < p.codes().length) {
					l.add((i - start) * p.step());
				}
				start = -1;
			}
		}
		return l;
	}

	static void check(LandscapeScale sc) {
		List<Section> all = sections(sc, ALDER_CARR_PER_CLASS);
		// The first PER_CLASS sections of each class in the same (hash) order: the set checked before step K0.
		List<Section> list = new ArrayList<>();
		Map<WatersideZones.StreamClass, Integer> taken = new EnumMap<>(WatersideZones.StreamClass.class);
		for (Section p : all) {
			if (taken.merge(p.streamClass(), 1, Integer::sum) <= PER_CLASS) {
				list.add(p);
			}
		}
		Map<WatersideZones.StreamClass, int[]> result = new EnumMap<>(WatersideZones.StreamClass.class);
		List<Double> willowScrub = new ArrayList<>();
		List<Double> ashAlder = new ArrayList<>();
		List<Double> alderCarr = new ArrayList<>();
		List<Double> alderCarrBase = new ArrayList<>();
		List<String> unordered = new ArrayList<>();
		for (Section p : list) {
			int[] w = result.computeIfAbsent(p.streamClass(), q -> new int[2]);
			w[0]++;
			if (isOrdered(p)) {
				w[1]++;
			} else if (unordered.size() < 6) {
				unordered.add(String.format(Locale.ROOT, "%s (%.0f, %.0f)", p.streamClass(), p.x(), p.z()));
				System.out.println("  " + p.streamClass() + ": " + rle(p));
			}
			if (p.streamClass() != WatersideZones.StreamClass.B) {
				// Shrub belt at the bank: willow scrub together with the point bar and gravel bar in front of it.
				willowScrub.addAll(runs(p, i -> Habitat.zone(p.codes()[i]) == Zone.WILLOW_SCRUB
						|| Habitat.zone(p.codes()[i]) == Zone.POINT_BAR || Habitat.zone(p.codes()[i]) == Zone.GRAVEL_BAR, true));
			}
			if (p.streamClass() == WatersideZones.StreamClass.B) {
				ashAlder.addAll(runs(p, i -> Habitat.biome(p.codes()[i]) == HabitatBiome.ASH_ALDER_FOREST, true));
			}
			// Riverine alder carr (backswamps, wide floors of small rivers); the river cross-section cuts the rings of
			// standing water obliquely, so their widths are not measured here.
			alderCarrBase.addAll(alderCarrRuns(p));
		}
		for (Section p : all) {
			alderCarr.addAll(alderCarrRuns(p));
		}
		System.out.printf(Locale.ROOT, "%s: cross-sections %s; willow scrub belts %d (min %.1f m, at least 3 blocks %.1f%%), "
				+ "OlJ %d (min %.1f m, at least 6 blocks %.1f%%), alder carr %d (min %.1f m, at least 10 blocks %.1f%%)%n",
				sc.id(), description(result), willowScrub.size(), min(willowScrub), 100 * share(willowScrub, Calibration.MIN_WILLOW_SCRUB), ashAlder.size(),
				min(ashAlder), 100 * share(ashAlder, Calibration.MIN_ASH_ALDER), alderCarr.size(), min(alderCarr), 100 * share(alderCarr, Calibration.MIN_ALDER_CARR));
		System.out.printf(Locale.ROOT, "%s: alder carr chords: %d from %d cross-sections per class (at least 10 blocks %.1f%%), "
				+ "%d from %d per class (at least 10 blocks %.1f%%)%n", sc.id(), alderCarrBase.size(), PER_CLASS,
				100 * share(alderCarrBase, Calibration.MIN_ALDER_CARR), alderCarr.size(), ALDER_CARR_PER_CLASS,
				100 * share(alderCarr, Calibration.MIN_ALDER_CARR));
		System.out.println("  unordered (examples): " + unordered);
		for (WatersideZones.StreamClass cls : WatersideZones.StreamClass.values()) {
			int[] w = result.get(cls);
			assertTrue(w != null && w[0] >= 20, sc.id() + ": too few cross-sections of class " + cls + ": " + description(result));
			assertTrue(w[1] >= 0.9 * w[0], sc.id() + ": class " + cls + " ordered in " + w[1] + " of " + w[0]);
		}
		assertTrue(share(willowScrub, Calibration.MIN_WILLOW_SCRUB) >= 0.95, sc.id() + ": willow scrub narrower than 3 blocks: " + willowScrub);
		assertTrue(share(ashAlder, Calibration.MIN_ASH_ALDER) >= 0.95, sc.id() + ": OlJ narrower than 6 blocks: " + ashAlder);
		// The cross-section cuts alder carr patches at various angles and the chords near patch edges are short, so
		// at least half of the chords must be ≥ 10 blocks (for a circle, a chord shorter than 2/3 of the diameter
		// occurs in about 25% of crossings).
		assertTrue(alderCarr.isEmpty() || share(alderCarr, Calibration.MIN_ALDER_CARR) >= 0.5, sc.id() + ": alder carr narrower than 10 blocks: " + alderCarr);
	}

	private static List<Double> alderCarrRuns(Section p) {
		return runs(p, i -> Habitat.biome(p.codes()[i]) == HabitatBiome.ALDER_CARR && !p.standingWater()[i], false);
	}

	static String rle(Section p) {
		StringBuilder sb = new StringBuilder();
		int prev = Integer.MIN_VALUE;
		int start = 0;
		for (int i = 0; i <= p.codes().length; i++) {
			int code = i < p.codes().length ? p.codes()[i] & ((1 << 21) - 1) : -2;
			if (code != prev) {
				if (prev != Integer.MIN_VALUE) {
					sb.append(Habitat.biome(prev).id()).append('/').append(Habitat.zone(prev).id()).append('/')
							.append(Habitat.association(prev)).append(':').append((i - start) * p.step()).append(' ');
				}
				prev = code;
				start = i;
			}
		}
		return sb.toString();
	}

	private static String description(Map<WatersideZones.StreamClass, int[]> w) {
		StringBuilder sb = new StringBuilder();
		w.forEach((k, v) -> sb.append(k).append(' ').append(v[1]).append('/').append(v[0]).append(' '));
		return sb.toString().trim();
	}

	private static double min(List<Double> l) {
		return l.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
	}

	/** Share of belts not narrower than the minimum (with a tolerance of the 0.5 m cross-section step). */
	private static double share(List<Double> l, double minimum) {
		if (l.isEmpty()) {
			return 1;
		}
		return (double) l.stream().filter(v -> v >= minimum - 0.5).count() / l.size();
	}

	/** Starting point of the cross-section grid (240 × 240 points), shared with {@link #floorZonesFollowDominantRiver}. */
	static double[] gridPoint(LandscapeScale sc, int q) {
		double grid = sc == LandscapeScale.REALISTIC ? 2_500 : 120;
		int n = 240;
		return new double[] {((q % n) - n / 2) * grid + 0.37 * grid * ((q / n) % 3), ((q / n) - n / 2) * grid};
	}

	/**
	 * Whether {@link #floorZonesFollowDominantRiver} requires the share of river zones (≥ 95%). It was false in K1,
	 * where the dominant valley was still chosen by the old rule (P2); true from step K3 (F1).
	 */
	static final boolean F2_SHARE_ENFORCED = true;
	/**
	 * Share of the whole floor where F2 finds the river, required from K3. Realistic scale 95% (measured after K3:
	 * 99.1%). Gameplay scale 92% (measured: 92.9%): the rest are the triangles at the tributary mouths that F1 keeps by
	 * design (the column at the edge of the river floor lies deeper in the floor of the tributary, key floorHalf −
	 * floorDist, within about one tributary floor half-width of the river floor edge). They are larger relative to the
	 * measured strip at gameplay scale, where the floor of an order 1–2 valley is wide compared with the floor of the
	 * river (docs/m2/poprawka-geometrii.md, K3). The margin is 0.9 percentage points: step K4 (G3 widens the tributary
	 * mouths, G4 changes the floor edge) must measure this share again and explain any change instead of lowering the
	 * threshold further. Measured after K4c: 99.1% and 93.0%. Step K5 (D5) moved two confluences of the gameplay scale
	 * onto the low coast (20 and 237 m from the sea), whose flat hinterland lies at the level of the valley floors (90.6%
	 * with them); the coastal flat ({@link #COAST_FLAT}) is left out of the measurement: 99.1% and 93.1%.
	 */
	static final double FLOOR_FOUND_REALISTIC = 0.95;
	/**
	 * Width of the coastal flat of the low coast left out of {@link #measureF2} (m·meso from the shoreline): a quarter of
	 * the rise of its hinterland to the compressed relief ({@code LandscapeModel.COAST_LOW_END}, D5, step K5b).
	 */
	static final double COAST_FLAT = 0.25 * LandscapeModel.COAST_LOW_END;
	static final double FLOOR_FOUND_GAMEPLAY = 0.92;
	/**
	 * Review of K5: the coastal flat left out of {@link #measureF2} is measured on its own, so the exclusion does not hide
	 * the change. At gameplay scale it is 683 of 4834 floor columns (14.1%), where F2 finds the river in 75.7%; the whole
	 * floor with it 90.6%. Bounds: the share of the coastal flat at most {@value}, and the whole floor with it at least
	 * {@link #FLOOR_FOUND_WITH_COAST}. The drop belongs to the open decision on the low coast of D5
	 * (docs/m2/poprawka-geometrii.md, K5).
	 */
	static final double COAST_FLAT_SHARE = 0.16;
	/** Review of K5: least share of the whole floor with the coastal flat where F2 finds the river (both scales). */
	static final double FLOOR_FOUND_WITH_COAST = 0.9;

	/**
	 * F2 (docs/m2/poprawka-geometrii.md, step K1): zones on the floor of a large river follow its channel, and a
	 * smaller tributary keeps only its own belt. Measured on square windows (1.5 km·k, every 10 m·k) around the first
	 * 20 confluences of a tributary of order 2 or 1 with an order-3 valley on a lowland floor
	 * ({@link RiverNetworkProbe#confluences}, windows do not overlap), at both scales.
	 *
	 * <p>The columns are chosen geometrically, independently of the dominant valley of the model and of the F2 fields:
	 * the river is the nearest order-3 channel measured from its own segments ({@link RiverNetworkProbe#river}). A
	 * column counts when it is dry, lies on the floor of an order-3 segment (as {@code inFloor}) at most
	 * {@link Calibration#FLOOR_H} above the river (as {@code onValleyFloor}), the nearest channel of the model is
	 * narrower than the river (a tributary or another small watercourse) and the column lies beyond the widest belt of
	 * ash-alder riparian forest of that channel (+20% jitter), the river is of class A by its own width and gradient,
	 * and the column is within its poplar riparian forest (D_top −20% jitter) below
	 * {@link Calibration#H_WILLOW_RIPARIAN}. Such a column should get the willow scrub or willow-poplar riparian forest
	 * of the river; anything else is a wedge. Failures are split by cause: the dominant valley is the smaller
	 * watercourse (P2: the channel of the dominant valley is not wider than the nearest channel), F2 picked another
	 * channel, or other. Confluences without counted columns report the first filter that removed every column.
	 *
	 * <p>The same filters without the D_top and height limits give the whole floor of the river beyond the belt of the
	 * smaller channel. There the zones come from the river only when F2 found the river as the channel of the dominant
	 * valley; the share of P2 columns (the smaller channel's valley dominant) measures the remaining wedges of the rest
	 * of the floor (elm-ash forest, backswamps), which lie beyond the reach of the poplar riparian forest.
	 *
	 * <p>Required now: the river zones in at least 95% of the counted columns where the model found the river as the
	 * channel of the dominant valley (a check of the F2 zones, not of the choice of the valley), and enough columns
	 * for the measurement. From step K3 ({@link #F2_SHARE_ENFORCED}): the river zones in at least 95% of all counted
	 * columns and the river found in at least {@link #FLOOR_FOUND_REALISTIC} / {@link #FLOOR_FOUND_GAMEPLAY} of the
	 * whole floor. Reported for comparison: the share with the F2
	 * fields removed (zones of the nearest channel, as before K1).
	 *
	 * <p>Also counts the columns of the cross-section grid (both scales) with more segments in the culling frame than
	 * the initial capacity of the F2 candidate buffer: expected 0 (the buffer grows, so results stay correct, but every
	 * thread would reallocate it).
	 */
	@Test
	void floorZonesFollowDominantRiver() {
		LandscapeModel real = new LandscapeModel(SEED, LandscapeScale.REALISTIC, 1.0);
		LandscapeModel gameplay = new LandscapeModel(SEED, LandscapeScale.GAMEPLAY, 1.0);
		F2Result r = measureF2(real, 6);
		F2Result g = measureF2(gameplay, 12);
		// Candidates of the F2 buffer on the cross-section grid of both scales.
		int capacity = RiverNetworkProbe.floorCandidateCapacity();
		StringBuilder buffer = new StringBuilder();
		int over = 0;
		for (LandscapeModel gm : new LandscapeModel[] {real, gameplay}) {
			AtomicInteger max = new AtomicInteger();
			AtomicInteger overflows = new AtomicInteger();
			IntStream.range(0, 240 * 240).parallel().forEach(q -> {
				double[] p = gridPoint(gm.scale(), q);
				int c = RiverNetworkProbe.frameCandidates(gm, p[0], p[1]);
				max.accumulateAndGet(c, Math::max);
				if (c > capacity) {
					overflows.incrementAndGet();
				}
			});
			over += overflows.get();
			buffer.append(String.format(Locale.ROOT, " %s: max %d, over capacity %d of %d;", gm.scale().id(), max.get(),
					overflows.get(), 240 * 240));
		}
		System.out.println("  F2 candidate buffer (capacity " + capacity + "):" + buffer);
		for (F2Result x : new F2Result[] {r, g}) {
			assertTrue(x.confluences() == 20, x.scale() + ": too few confluences: " + x.confluences());
			assertTrue(x.foundRiverZones() >= 0.95 * x.foundRiver(), x.scale() + ": river zones where F2 found the river: "
					+ x.foundRiverZones() + " of " + x.foundRiver());
			if (F2_SHARE_ENFORCED) {
				assertTrue(x.riverZones() >= 0.95 * x.counted(), x.scale() + ": river zones " + x.riverZones() + " of "
						+ x.counted());
				double floorMin = x == r ? FLOOR_FOUND_REALISTIC : FLOOR_FOUND_GAMEPLAY;
				assertTrue(x.floorFound() >= floorMin * x.floor(), x.scale() + ": whole floor, river found in "
						+ x.floorFound() + " of " + x.floor() + " columns (P2 " + x.floorP2() + "), required " + floorMin);
				// The coastal flat left out above stays a small part of the floor, and the whole floor with it is measured
				// too (review of K5).
				assertTrue(x.coastFloor() <= COAST_FLAT_SHARE * (x.floor() + x.coastFloor()), x.scale() + ": coastal flat "
						+ x.coastFloor() + " of " + (x.floor() + x.coastFloor()) + " floor columns");
				assertTrue(x.floorFound() + x.coastFound() >= FLOOR_FOUND_WITH_COAST * (x.floor() + x.coastFloor()), x.scale()
						+ ": whole floor with the coastal flat, river found in " + (x.floorFound() + x.coastFound()) + " of "
						+ (x.floor() + x.coastFloor()));
			}
		}
		assertTrue(r.counted() >= MIN_F2_COLUMNS && r.withColumns() >= 5, "REAL: too few columns for the F2 measurement: "
				+ r.counted() + " at " + r.withColumns() + " confluences");
		assertTrue(g.counted() >= MIN_F2_COLUMNS / 2 && g.withColumns() >= 4, "GAMEPLAY: too few columns for the F2 measurement: "
				+ g.counted() + " at " + g.withColumns() + " confluences");
		assertTrue(over == 0, "columns with more F2 candidates than the buffer capacity:" + buffer);
	}

	/** Smallest number of counted columns of the F2 measurement at each scale (sanity floor of the sample). */
	static final int MIN_F2_COLUMNS = 300;

	/** Result of the F2 measurement at one scale ({@link #floorZonesFollowDominantRiver}). */
	record F2Result(String scale, int confluences, int withColumns, long counted, long riverZones, long riverZonesBefore,
			long p2, long otherChannel, long other, long foundRiver, long foundRiverZones, long floor, long floorFound,
			long floorP2, long coastFloor, long coastFound) {
	}

	/** Filters of the F2 measurement in order; a confluence without counted columns reports the first one that removed all. */
	private static final String[] F2_STAGES = {"no dry river floor", "no smaller channel with the column beyond its belt",
			"river not class A", "beyond D_top or above 300 m"};
	/**
	 * Counters after the stages. Counted columns: river zones, river zones before K1, P2 failures, other-channel
	 * failures, F2 found the river, river zones there. Columns of the whole floor (stages 1–3 passed, without the D_top
	 * and height limits): F2 found the river, P2. Columns of the whole floor on the coastal flat ({@link #COAST_FLAT},
	 * not counted otherwise): all, F2 found the river.
	 */
	private static final int F2_COUNTERS = 10;

	static F2Result measureF2(LandscapeModel m, int gridRadius) {
		LandscapeScale sc = m.scale();
		HabitatClassifier k = new HabitatClassifier(SEED, sc, HabitatClassifier.Mode.NATURAL);
		double kk = sc.local();
		double side = 1_500 * kk;
		double step = 10 * kk;
		int n = (int) Math.round(side / step);
		List<double[]> confluences = RiverNetworkProbe.confluences(m, gridRadius, 20, side);
		int stages = F2_STAGES.length;
		long[] total = new long[F2_COUNTERS + 2];
		int withColumns = 0;
		StringBuilder perConfluence = new StringBuilder();
		for (double[] p : confluences) {
			AtomicLong[] a = new AtomicLong[stages + F2_COUNTERS];
			for (int i = 0; i < a.length; i++) {
				a[i] = new AtomicLong();
			}
			IntStream.range(0, n * n).parallel().forEach(q -> {
				double x = p[0] - side / 2 + (q % n + 0.5) * step;
				double z = p[1] - side / 2 + (q / n + 0.5) * step;
				int stage = f2Stage(m, k, x, z, a);
				for (int i = 0; i < stage; i++) {
					a[i].incrementAndGet();
				}
			});
			long counted = a[stages - 1].get();
			total[F2_COUNTERS] += counted;
			total[F2_COUNTERS + 1] += a[stages - 2].get();
			for (int i = 0; i < F2_COUNTERS; i++) {
				total[i] += a[stages + i].get();
			}
			String reason = "";
			if (counted > 0) {
				withColumns++;
			} else {
				for (int i = 0; i < stages; i++) {
					if (a[i].get() == 0) {
						reason = " (" + F2_STAGES[i] + ")";
						break;
					}
				}
			}
			perConfluence.append(String.format(Locale.ROOT, " (%.0f, %.0f) %d%s: %.1f%% / %.1f%%, P2 %d, floor %d: river %.1f%%, P2 %d;",
					p[0], p[1], counted, reason, 100.0 * a[stages].get() / Math.max(1, counted),
					100.0 * a[stages + 1].get() / Math.max(1, counted), a[stages + 2].get(), a[stages - 2].get(),
					100.0 * a[stages + 6].get() / Math.max(1, a[stages - 2].get()), a[stages + 7].get()));
		}
		long counted = total[F2_COUNTERS];
		long other = counted - total[0] - total[2] - total[3];
		F2Result r = new F2Result(sc.id(), confluences.size(), withColumns, counted, total[0], total[1], total[2], total[3],
				other, total[4], total[5], total[F2_COUNTERS + 1], total[6], total[7], total[8], total[9]);
		System.out.printf(Locale.ROOT, "F2 %s at %d confluences (%d with counted columns): %d columns on the floor of a class A "
				+ "river beyond the belt of a smaller channel, in the reach of its poplar riparian forest; river zones %.1f%% "
				+ "(zones of the nearest channel, before K1: %.1f%%); not river zones: P2 (dominant valley of the smaller "
				+ "channel) %d, F2 picked another channel %d, other %d; where F2 found the river: %d of %d (%.1f%%)%n", sc.id(),
				r.confluences(), r.withColumns(), counted, 100.0 * r.riverZones() / Math.max(1, counted),
				100.0 * r.riverZonesBefore() / Math.max(1, counted), r.p2(), r.otherChannel(), r.other(), r.foundRiverZones(),
				r.foundRiver(), 100.0 * r.foundRiverZones() / Math.max(1, r.foundRiver()));
		System.out.printf(Locale.ROOT, "  whole floor of class A rivers beyond the belt of a smaller channel (no D_top limit): %d columns, "
				+ "F2 found the river in %.1f%%, P2 (the smaller channel's valley dominant: its zones, wedges) %d (%.1f%%)%n",
				r.floor(), 100.0 * r.floorFound() / Math.max(1, r.floor()), r.floorP2(), 100.0 * r.floorP2() / Math.max(1, r.floor()));
		System.out.printf(Locale.ROOT, "  whole floor on the coastal flat (left out above): %d columns, F2 found the river in %.1f%%; "
				+ "whole floor with it: river found in %.1f%%%n", r.coastFloor(), 100.0 * r.coastFound() / Math.max(1, r.coastFloor()),
				100.0 * (r.floorFound() + r.coastFound()) / Math.max(1, r.floor() + r.coastFloor()));
		System.out.println("  per confluence (columns: now / before K1, P2 failures; whole floor):" + perConfluence);
		return r;
	}

	/**
	 * Number of filters of {@link #measureF2} the column (x, z) passes ({@link #F2_STAGES}); for a counted column also
	 * adds its result to the counters {@code a} after the stages ({@link #F2_COUNTERS}).
	 */
	private static int f2Stage(LandscapeModel m, HabitatClassifier k, double x, double z, AtomicLong[] a) {
		ColumnSample s = m.sample(x, z);
		ColumnSample.Waters w = s.waters();
		if (s.hasWater() || w.standingWaterKind() != ColumnSample.StandingWaterKind.NONE || w.streamOrder() == 0) {
			return 0;
		}
		// The low coast of D5 (step K5b): near the sea the hinterland lies 1.5–2 m above it, at the level of the valley
		// floors, so the floors of the river and of its tributaries merge into one coastal flat there, and which floor
		// dominates (F1) says nothing about the valley floors this test measures; two of the confluences of the gameplay
		// scale lie at river mouths 20 and 237 m from the sea (whole floor with them: 90.6%). Their whole floor is
		// counted separately (review of K5), so the exclusion cannot hide a larger drop.
		boolean coastFlat = s.terrain().coastD() < COAST_FLAT * m.scale().meso();
		double[] river = RiverNetworkProbe.river(m, x, z);
		if (river == null || river[4] == 0) {
			return 0;
		}
		double dRiver = river[0];
		double wRiver = river[1];
		HabitatClassifier.Column c = new HabitatClassifier.Column(k, s, x, z);
		double hl = c.H - river[2];
		if (hl >= Calibration.OTHER_CHANNEL_H && hl > Calibration.FLOOR_H) {
			return 0;
		}
		double kk = m.scale().local();
		double ashAlder = Math.min(Calibration.B_ASH_ALDER_MAX_K * kk,
				Math.max(Calibration.B_ASH_ALDER_MIN_K * kk, Calibration.B_ASH_ALDER_W * w.channelWidth()));
		if (c.wOutwashPlain > 0.5) {
			ashAlder = Math.clamp(ashAlder, Calibration.B_ASH_ALDER_OUTWASH_PLAIN_MIN_K * kk,
					Calibration.B_ASH_ALDER_OUTWASH_PLAIN_MAX_K * kk);
		}
		if (!(wRiver > 1.05 * w.channelWidth())
				|| w.channelDist() <= Math.max(Calibration.MIN_ASH_ALDER, ashAlder * (1 + Calibration.WIDTH_JITTER))) {
			return 1;
		}
		if (c.wMountains > 0.5 || river[3] > Calibration.CLASS_GRADIENT || c.wLowland < Calibration.CLASS_LOWLAND_WEIGHT
				|| c.wr(wRiver) < Math.min(Calibration.CLASS_A_WR, Calibration.CLASS_A_WR_ORDER3)) {
			return 2;
		}
		int base = F2_STAGES.length;
		// F2 found the river: the channel of the dominant valley is this river's channel (same distance and width).
		boolean found = Math.abs(w.floorChannelDist() - dRiver) <= 0.01 && Math.abs(w.floorChannelWidth() - wRiver) <= 0.01;
		boolean p2 = !(w.floorChannelWidth() > 1.05 * w.channelWidth());
		if (coastFlat) {
			a[base + 8].incrementAndGet();
			if (found) {
				a[base + 9].incrementAndGet();
			}
			return 0;
		}
		if (found) {
			a[base + 6].incrementAndGet();
		} else if (p2) {
			a[base + 7].incrementAndGet();
		}
		double dPoplar = Math.clamp(Calibration.A_D_POPLAR_W * wRiver, Calibration.A_D_POPLAR_MIN * kk,
				Calibration.A_D_POPLAR_MAX * kk) * (1 - Calibration.WIDTH_JITTER);
		if (dRiver > dPoplar || c.H >= Calibration.H_WILLOW_RIPARIAN) {
			return 3;
		}
		boolean now = riverZone(k.classify(s, x, z));
		if (now) {
			a[base].incrementAndGet();
		}
		ColumnSample.Waters nearest = new ColumnSample.Waters(w.streamOrder(), w.headwaters(), w.channelDist(),
				w.channelWidth(), w.channelLevel(), w.inFloor(), w.u(), w.floorHalfWidth(), w.channelGradient(),
				w.convexBank(), w.s(), w.shoreLevel(), w.standingWaterKind(), w.ombrotrophicPeat(), w.lakeId(),
				w.standingWaterRadius(), Double.POSITIVE_INFINITY, Double.NaN, Double.NaN, Double.NaN);
		ColumnSample before = new ColumnSample(s.surface(), s.waterLevel(), s.waterKind(), s.type(), s.substrate(),
				s.coverDepth(), s.terrain(), nearest, s.region());
		if (riverZone(k.classify(before, x, z))) {
			a[base + 1].incrementAndGet();
		}
		if (found) {
			a[base + 4].incrementAndGet();
			if (now) {
				a[base + 5].incrementAndGet();
			}
		}
		if (!now) {
			if (p2) {
				a[base + 2].incrementAndGet();
			} else if (!found) {
				a[base + 3].incrementAndGet();
			}
		}
		return F2_STAGES.length;
	}

	/** Zones of a large river by its channel: willow scrub, white willow and poplar riparian forest. */
	private static boolean riverZone(int code) {
		HabitatBiome b = Habitat.biome(code);
		return b == HabitatBiome.WILLOW_POPLAR_FOREST || b == HabitatBiome.WILLOW_SCRUB;
	}

	@Test
	void sectionsAtRealisticScale() {
		check(LandscapeScale.REALISTIC);
	}

	@Test
	void sectionsAtGameplayScale() {
		check(LandscapeScale.GAMEPLAY);
	}
}
