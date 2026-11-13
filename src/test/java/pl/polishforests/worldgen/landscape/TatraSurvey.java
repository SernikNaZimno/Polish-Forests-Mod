package pl.polishforests.worldgen.landscape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import pl.polishforests.worldgen.chunk.VerticalScale;

/**
 * Step TT1: the Tatra massifs nearest the origin of a few seeds at gameplay scale, shared by the Tatra tests; step TT5:
 * also at realistic scale ({@link #scales} for the parameterized tests). The models are cached per scale and seed (their
 * caches warm up once for all tests of a fork).
 */
final class TatraSurvey {
	/** Seeds of the Tatra tests: the golden seeds A and B and seed 4 (the nearest massif of the probed seeds, 53 km). */
	static final long[] SEEDS = {GoldenTerrainTest.SEED_A, GoldenTerrainTest.SEED_B, 4L};
	/** Half side of the box searched for massifs at gameplay scale (m). */
	static final double BOX = 300_000;
	/**
	 * Step TT5: half side of the box at realistic scale (m): the radius of the wide phase of {@code find tatra}. The deep
	 * mountain belt of the realistic scale is rare (one range in about 1000 km), so the nearest massifs lie 370–1290 km
	 * from the origin.
	 */
	static final double BOX_REAL = 2_000_000;
	/** Step TT5: highest top of a column (Y) with its snow and crowns: gameplay and realistic (docs/04 §3 "Kroki TT"). */
	static final int TOP_GAMEPLAY = 720;
	static final int TOP_REAL = 2_025;

	private static final Map<Long, LandscapeModel> WITH = new ConcurrentHashMap<>();
	private static final Map<Long, LandscapeModel> WITHOUT = new ConcurrentHashMap<>();
	private static final Map<Long, LandscapeModel> WITH_REAL = new ConcurrentHashMap<>();
	private static final Map<Long, LandscapeModel> WITHOUT_REAL = new ConcurrentHashMap<>();

	private TatraSurvey() {
	}

	/** Step TT5: the scales of the parameterized Tatra tests. */
	static Stream<LandscapeScale> scales() {
		return Stream.of(LandscapeScale.GAMEPLAY, LandscapeScale.REALISTIC);
	}

	/** Gameplay model of the seed with the Tatras. */
	static LandscapeModel with(long seed) {
		return with(LandscapeScale.GAMEPLAY, seed);
	}

	/** Gameplay model of the seed without the Tatras. */
	static LandscapeModel without(long seed) {
		return without(LandscapeScale.GAMEPLAY, seed);
	}

	/** Step TT5: model of the scale and seed with the Tatras. */
	static LandscapeModel with(LandscapeScale scale, long seed) {
		return (scale == LandscapeScale.REALISTIC ? WITH_REAL : WITH).computeIfAbsent(seed, s -> new LandscapeModel(s, scale, 1.0, true));
	}

	/** Step TT5: model of the scale and seed without the Tatras. */
	static LandscapeModel without(LandscapeScale scale, long seed) {
		return (scale == LandscapeScale.REALISTIC ? WITHOUT_REAL : WITHOUT).computeIfAbsent(seed,
				s -> new LandscapeModel(s, scale, 1.0, false));
	}

	/** The {@code n} gameplay massifs of the seed nearest the origin. */
	static List<LandscapeModel.Tatra> nearest(long seed, int n) {
		return nearest(LandscapeScale.GAMEPLAY, seed, n);
	}

	/** Step TT5: the {@code n} massifs of the scale and seed nearest the origin. */
	static List<LandscapeModel.Tatra> nearest(LandscapeScale scale, long seed, int n) {
		double box = box(scale);
		List<LandscapeModel.Tatra> ts = new ArrayList<>(with(scale, seed).tatras(-box, -box, box, box));
		ts.sort(Comparator.comparingDouble(t -> Math.hypot(t.x(), t.z())));
		return ts.subList(0, Math.min(n, ts.size()));
	}

	/** Step TT5: half side of the box searched for massifs in the scale (m). */
	static double box(LandscapeScale scale) {
		return scale == LandscapeScale.REALISTIC ? BOX_REAL : BOX;
	}

	/** Step TT5: the vertical scale of the landscape scale. */
	static VerticalScale vertical(LandscapeScale scale) {
		return scale == LandscapeScale.REALISTIC ? VerticalScale.REAL : VerticalScale.GAMEPLAY;
	}

	/** Step TT5: highest top of a column (Y) in the scale. */
	static int top(LandscapeScale scale) {
		return scale == LandscapeScale.REALISTIC ? TOP_REAL : TOP_GAMEPLAY;
	}

	/**
	 * Step TT5: a lattice step of a gameplay test scaled to the forms of the scale (the realistic forms are 3.33 times
	 * larger), so the scans keep their number of points.
	 */
	static double step(LandscapeScale scale, double gameplayStep) {
		return gameplayStep * scale.mountainSpacing() / LandscapeScale.GAMEPLAY.mountainSpacing();
	}

	/** World x of the local point: u across the range (towards the Podhale), v along it. */
	static double x(LandscapeModel.Tatra t, double u, double v) {
		return t.x() + u * t.cos() - v * t.sin();
	}

	static double z(LandscapeModel.Tatra t, double u, double v) {
		return t.z() + u * t.sin() + v * t.cos();
	}

	static String label(long seed, LandscapeModel.Tatra t) {
		return String.format(java.util.Locale.ROOT, "seed %d massif (%.0f, %.0f)", seed, t.x(), t.z());
	}

	/** Step TT5: the label with the scale. */
	static String label(LandscapeScale scale, long seed, LandscapeModel.Tatra t) {
		return scale.id() + " " + label(seed, t);
	}
}
