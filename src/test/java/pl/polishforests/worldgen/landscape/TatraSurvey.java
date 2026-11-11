package pl.polishforests.worldgen.landscape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step TT1: the Tatra massifs nearest the origin of a few seeds at gameplay scale, shared by the Tatra tests. The models
 * are cached per seed (their caches warm up once for all tests of a fork).
 */
final class TatraSurvey {
	/** Seeds of the Tatra tests: the golden seeds A and B and seed 4 (the nearest massif of the probed seeds, 53 km). */
	static final long[] SEEDS = {GoldenTerrainTest.SEED_A, GoldenTerrainTest.SEED_B, 4L};
	/** Half side of the box searched for massifs (m). */
	static final double BOX = 300_000;

	private static final Map<Long, LandscapeModel> WITH = new ConcurrentHashMap<>();
	private static final Map<Long, LandscapeModel> WITHOUT = new ConcurrentHashMap<>();

	private TatraSurvey() {
	}

	/** Gameplay model of the seed with the Tatras. */
	static LandscapeModel with(long seed) {
		return WITH.computeIfAbsent(seed, s -> new LandscapeModel(s, LandscapeScale.GAMEPLAY, 1.0, true));
	}

	/** Gameplay model of the seed without the Tatras. */
	static LandscapeModel without(long seed) {
		return WITHOUT.computeIfAbsent(seed, s -> new LandscapeModel(s, LandscapeScale.GAMEPLAY, 1.0, false));
	}

	/** The {@code n} massifs of the seed nearest the origin. */
	static List<LandscapeModel.Tatra> nearest(long seed, int n) {
		List<LandscapeModel.Tatra> ts = new ArrayList<>(with(seed).tatras(-BOX, -BOX, BOX, BOX));
		ts.sort(Comparator.comparingDouble(t -> Math.hypot(t.x(), t.z())));
		return ts.subList(0, Math.min(n, ts.size()));
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
}
