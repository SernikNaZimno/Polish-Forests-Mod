package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;

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

	/** Cross-section: watercourse class, column codes every {@code step} m from the bank. */
	record Section(WatersideZones.StreamClass streamClass, int[] codes, boolean[] onValleyFloor, boolean[] standingWater, double step, double x,
			double z) {
	}

	static List<Section> sections(LandscapeScale sc) {
		LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
		HabitatClassifier k = new HabitatClassifier(SEED, sc, HabitatClassifier.Mode.NATURAL);
		double grid = sc == LandscapeScale.REALISTIC ? 2_500 : 120;
		int n = 240;
		List<Section> result = Collections.synchronizedList(new ArrayList<>());
		IntStream.range(0, n * n).parallel().forEach(q -> {
			double x0 = ((q % n) - n / 2) * grid + 0.37 * grid * ((q / n) % 3);
			double z0 = ((q / n) - n / 2) * grid;
			Section p = section(m, k, x0, z0);
			if (p != null) {
				result.add(p);
			}
		});
		// Order independent of threads and of the position: by the hash of the starting point; PER_CLASS per class.
		List<Section> l = new ArrayList<>(result);
		l.sort((a, b) -> Long.compare(hash(a), hash(b)));
		Map<WatersideZones.StreamClass, Integer> count = new EnumMap<>(WatersideZones.StreamClass.class);
		List<Section> out = new ArrayList<>();
		for (Section p : l) {
			int c = count.getOrDefault(p.streamClass(), 0);
			if (c < PER_CLASS) {
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
		List<Section> list = sections(sc);
		Map<WatersideZones.StreamClass, int[]> result = new EnumMap<>(WatersideZones.StreamClass.class);
		List<Double> willowScrub = new ArrayList<>();
		List<Double> ashAlder = new ArrayList<>();
		List<Double> alderCarr = new ArrayList<>();
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
			alderCarr.addAll(runs(p, i -> Habitat.biome(p.codes()[i]) == HabitatBiome.ALDER_CARR && !p.standingWater()[i], false));
		}
		System.out.printf(Locale.ROOT, "%s: cross-sections %s; willow scrub belts %d (min %.1f m, at least 3 blocks %.1f%%), "
				+ "OlJ %d (min %.1f m, at least 6 blocks %.1f%%), alder carr %d (min %.1f m, at least 10 blocks %.1f%%)%n",
				sc.id(), description(result), willowScrub.size(), min(willowScrub), 100 * share(willowScrub, Calibration.MIN_WILLOW_SCRUB), ashAlder.size(),
				min(ashAlder), 100 * share(ashAlder, Calibration.MIN_ASH_ALDER), alderCarr.size(), min(alderCarr), 100 * share(alderCarr, Calibration.MIN_ALDER_CARR));
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

	@Test
	void sectionsAtRealisticScale() {
		check(LandscapeScale.REALISTIC);
	}

	@Test
	void sectionsAtGameplayScale() {
		check(LandscapeScale.GAMEPLAY);
	}
}
