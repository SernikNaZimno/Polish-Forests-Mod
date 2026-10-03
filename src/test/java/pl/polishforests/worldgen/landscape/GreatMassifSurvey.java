package pl.polishforests.worldgen.landscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Survey of the large Beskid massifs (M2-8, step K2 of the terrain geometry fix, docs/m2/poprawka-geometrii.md) in
 * the test windows of the design (§3.2): realistic scale 1000 km around (400, −1250) km, gameplay scale 600 km around
 * (0, 0); seed 20260927, region slider 1.0. For every massif with the center in the window: the realized summit and the
 * areas above 1390 m (timberline, dwarf pine and alpine grassland) and above 1650 m (alpine grassland) of the terrain
 * before valleys and lakes ({@code landElevation}), on a grid every 100 m (realistic) or 20 m (gameplay) in a square
 * of 2.4 Ra around the center, counting only the columns closer to this massif center than to any other. The
 * survey is computed once per scale and shared by the tests (LandscapeModelTest, RiverNetworkTest,
 * AltitudinalBeltsTest).
 */
public final class GreatMassifSurvey {
	public static final long SEED = 20260927L;

	/** Test window: square with the side {@code side} around (cx, cz), with at least {@code minCount} massifs. */
	public record Window(LandscapeScale scale, double cx, double cz, double side, int minCount) {
		public double minX() {
			return cx - side / 2;
		}

		public double minZ() {
			return cz - side / 2;
		}

		public double maxX() {
			return cx + side / 2;
		}

		public double maxZ() {
			return cz + side / 2;
		}
	}

	/**
	 * Surveyed massif: the realized summit (highest {@code landElevation} of its columns) and where it lies, and the
	 * areas in km² of its columns at or above 1390 m and 1650 m.
	 */
	public record Massif(LandscapeModel.GreatMassif massif, double summit, double summitX, double summitZ, double area1390,
			double area1650) {
		/** Area of the belt 1390–1650 m (dwarf pine) in km². */
		public double belt() {
			return area1390 - area1650;
		}
	}

	private static final Map<LandscapeScale, List<Massif>> CACHE = new HashMap<>();
	private static final Map<LandscapeScale, LandscapeModel> MODELS = new HashMap<>();

	private GreatMassifSurvey() {
	}

	public static Window window(LandscapeScale scale) {
		return scale == LandscapeScale.REALISTIC ? new Window(scale, 400_000, -1_250_000, 1_000_000, 6)
				: new Window(scale, 0, 0, 600_000, 15);
	}

	/** Model of the survey (seed {@link #SEED}, region slider 1.0), shared by the tests; the model is thread-safe. */
	static synchronized LandscapeModel model(LandscapeScale scale) {
		return MODELS.computeIfAbsent(scale, s -> new LandscapeModel(SEED, s, 1.0));
	}

	/** Semi-axis Ra of a massif along the range (m), from the model. */
	public static double ra(LandscapeScale scale) {
		return model(scale).greatMassifRa();
	}

	/** Massifs with the center in the window of the scale, in the order of {@link LandscapeModel#greatMassifs}. */
	public static synchronized List<Massif> survey(LandscapeScale scale) {
		List<Massif> cached = CACHE.get(scale);
		if (cached != null) {
			return cached;
		}
		Window w = window(scale);
		LandscapeModel m = model(scale);
		double ra = ra(scale);
		List<LandscapeModel.GreatMassif> list = m.greatMassifs(w.minX(), w.minZ(), w.maxX(), w.maxZ());
		List<LandscapeModel.GreatMassif> all = m.greatMassifs(w.minX() - 3 * ra, w.minZ() - 3 * ra, w.maxX() + 3 * ra,
				w.maxZ() + 3 * ra);
		double step = scale == LandscapeScale.REALISTIC ? 100 : 20;
		int n = (int) Math.ceil(2.4 * ra / step);
		double cell = step * step / 1e6;
		List<Massif> out = new ArrayList<>();
		for (LandscapeModel.GreatMassif g : list) {
			List<LandscapeModel.GreatMassif> near = all.stream()
					.filter(o -> !o.equals(g) && Math.hypot(o.x() - g.x(), o.z() - g.z()) < 4 * ra).toList();
			double x0 = g.x() - n * step / 2;
			double z0 = g.z() - n * step / 2;
			double[][] columns = IntStream.range(0, n * n).parallel().mapToObj(q -> {
				double x = x0 + (q % n) * step;
				double z = z0 + (q / n) * step;
				double d = Math.hypot(x - g.x(), z - g.z());
				for (LandscapeModel.GreatMassif o : near) {
					if (Math.hypot(x - o.x(), z - o.z()) < d) {
						return null;
					}
				}
				return new double[] {m.landElevation(x, z), x, z};
			}).toArray(double[][]::new);
			double best = Double.NEGATIVE_INFINITY;
			double bx = 0;
			double bz = 0;
			int a1390 = 0;
			int a1650 = 0;
			for (double[] c : columns) {
				if (c == null) {
					continue;
				}
				if (c[0] > best) {
					best = c[0];
					bx = c[1];
					bz = c[2];
				}
				a1390 += c[0] >= 1_390 ? 1 : 0;
				a1650 += c[0] >= 1_650 ? 1 : 0;
			}
			out.add(new Massif(g, best, bx, bz, a1390 * cell, a1650 * cell));
		}
		List<Massif> result = List.copyOf(out);
		CACHE.put(scale, result);
		return result;
	}

	/** The surveyed massif with the highest realized summit. */
	public static Massif highest(LandscapeScale scale) {
		Massif best = null;
		for (Massif s : survey(scale)) {
			if (best == null || s.summit() > best.summit()) {
				best = s;
			}
		}
		return best;
	}
}
