package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.HabitatClassifier;

/**
 * Step TT1 (KT-1): the Tatras change nothing outside their reach. A world with the Tatras equals the same world without
 * them bit for bit (the column sample, the terrain description with its landforms, the habitat code) everywhere outside
 * the influence square of every complex: the reach of the complex ({@link LandscapeModel#tatraReach}) plus
 * {@link #RIVER_MARGIN} order-3 node spacings of the river network (the nodes within {@link RiverNetwork#LOCAL_MARGIN}
 * spacings of a reach are computed anew, and a column reads the segments of the nodes around it). The patches of the
 * golden file {@code golden_terrain_m2.txt} at gameplay scale outside that square have the same hashes with the Tatras;
 * the patches within it (listed) are compared only without them ({@link GoldenTerrainTest}).
 */
@Tag("slow")
class TatraIsolationTest {
	/** Order-3 node spacings added to the reach: {@link RiverNetwork#LOCAL_MARGIN} for the local nodes and 2 for the query. */
	static final double RIVER_MARGIN = RiverNetwork.LOCAL_MARGIN + 2;
	static final long[] SEEDS = {GoldenTerrainTest.SEED_A, GoldenTerrainTest.SEED_B};

	/** Half side of the influence square around the middle of a massif (m). */
	static double influence(LandscapeModel m) {
		return m.tatraReach() + RIVER_MARGIN * m.rivers().spacing(3);
	}

	/** Whether the box can be changed by a Tatra complex: it meets the influence square of a massif. */
	static boolean influenced(LandscapeModel m, double minX, double minZ, double maxX, double maxZ) {
		double r = influence(m);
		for (LandscapeModel.Tatra t : m.tatras(minX - r, minZ - r, maxX + r, maxZ + r)) {
			if (t.x() + r > minX && t.x() - r < maxX && t.z() + r > minZ && t.z() - r < maxZ) {
				return true;
			}
		}
		return false;
	}

	/** The points compared: rings just outside the influence squares of the nearest massifs and a lattice over the box. */
	static List<double[]> points(LandscapeModel m) {
		double r = influence(m);
		List<double[]> out = new ArrayList<>();
		for (LandscapeModel.Tatra t : m.tatras(-150_000, -150_000, 150_000, 150_000)) {
			for (double extra : new double[] {50, 600, 2_000}) {
				double h = r + extra;
				for (int q = 0; q < 160; q++) {
					double f = (q + 0.5) / 40.0;
					int side = q / 40;
					double a = -h + 2 * h * (f - side);
					double[] p = switch (side) {
						case 0 -> new double[] {t.x() + a, t.z() - h};
						case 1 -> new double[] {t.x() + h, t.z() + a};
						case 2 -> new double[] {t.x() - a, t.z() + h};
						default -> new double[] {t.x() - h, t.z() - a};
					};
					out.add(p);
				}
			}
		}
		for (double x = -150_000; x <= 150_000; x += 6_100) {
			for (double z = -150_000; z <= 150_000; z += 6_100) {
				out.add(new double[] {x + 37, z - 53});
			}
		}
		out.removeIf(p -> influenced(m, p[0], p[1], p[0], p[1]));
		return out;
	}

	@Test
	void outsideTheReachNothingChanges() {
		for (long seed : SEEDS) {
			LandscapeModel with = new LandscapeModel(seed, LandscapeScale.GAMEPLAY, 1.0, true);
			LandscapeModel without = new LandscapeModel(seed, LandscapeScale.GAMEPLAY, 1.0, false);
			HabitatClassifier cw = new HabitatClassifier(seed, with, HabitatClassifier.Mode.NATURAL);
			HabitatClassifier co = new HabitatClassifier(seed, without, HabitatClassifier.Mode.NATURAL);
			List<double[]> pts = points(with);
			List<String> diffs = java.util.Collections.synchronizedList(new ArrayList<>());
			IntStream.range(0, pts.size()).parallel().forEach(i -> {
				double x = pts.get(i)[0];
				double z = pts.get(i)[1];
				ColumnSample a = with.sample(x, z);
				ColumnSample b = without.sample(x, z);
				if (!a.equals(b)) {
					diffs.add(String.format(Locale.ROOT, "sample at (%.0f, %.0f)", x, z));
					return;
				}
				if (!with.describe(x, z).equals(without.describe(x, z))) {
					diffs.add(String.format(Locale.ROOT, "description at (%.0f, %.0f)", x, z));
				}
				if (cw.classify(a, x, z) != co.classify(b, x, z)) {
					diffs.add(String.format(Locale.ROOT, "habitat at (%.0f, %.0f)", x, z));
				}
			});
			System.out.printf(Locale.ROOT, "seed %d: %d points outside the influence squares (half side %.0f m = reach %.0f m + %.0f "
					+ "order-3 spacings of %.0f m), %d differ%n", seed, pts.size(), influence(with), with.tatraReach(), RIVER_MARGIN,
					with.rivers().spacing(3), diffs.size());
			assertTrue(pts.size() > 2_000, "seed " + seed + ": too few points");
			assertTrue(diffs.isEmpty(), "seed " + seed + ": the Tatras change the world outside their reach: "
					+ diffs.subList(0, Math.min(20, diffs.size())));
		}
	}

	/**
	 * Where the change actually ends: along 32 directions from the middle of the nearest massif of each seed, the
	 * farthest sample that differs (information for docs/04; the test asserts only that it lies within the square).
	 */
	@Test
	void changeEndsWithinTheInfluenceSquare() {
		for (long seed : SEEDS) {
			LandscapeModel with = new LandscapeModel(seed, LandscapeScale.GAMEPLAY, 1.0, true);
			LandscapeModel without = new LandscapeModel(seed, LandscapeScale.GAMEPLAY, 1.0, false);
			LandscapeModel.Tatra t = TatraSurvey.nearest(seed, 1).get(0);
			double r = influence(with);
			double[] last = new double[32];
			IntStream.range(0, 32).parallel().forEach(q -> {
				double a = 2 * Math.PI * q / 32;
				for (double d = 0; d <= 1.5 * r; d += 250) {
					double x = t.x() + d * Math.cos(a);
					double z = t.z() + d * Math.sin(a);
					if (!with.sample(x, z).equals(without.sample(x, z))) {
						last[q] = d;
					}
				}
			});
			double far = java.util.Arrays.stream(last).max().orElse(0);
			System.out.printf(Locale.ROOT, "%s: the farthest changed sample %.0f m from the middle (reach %.0f m, square half side %.0f m)%n",
					TatraSurvey.label(seed, t), far, t.reach(), r);
			for (int q = 0; q < 32; q++) {
				double a = 2 * Math.PI * q / 32;
				double cheb = Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a))) * last[q];
				assertTrue(cheb < r, TatraSurvey.label(seed, t) + ": a change outside the influence square at " + last[q] + " m");
			}
		}
	}

	/**
	 * The golden file M2 at gameplay scale with the Tatras on: every patch outside the influence squares has its hash; the
	 * patches within them are listed (compared only without the Tatras by {@link GoldenTerrainTest}).
	 */
	@Test
	void goldenM2PatchesOutsideTheReachAreUnchanged() throws IOException {
		Map<String, GoldenTerrainTest.GoldenSet> golden = GoldenTerrainTest.read(GoldenTerrainTest.RESOURCE_CURRENT);
		int compared = 0;
		List<String> within = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (GoldenTerrainTest.SetKey k : GoldenTerrainTest.SETS) {
			if (k.scale() != LandscapeScale.GAMEPLAY) {
				continue;
			}
			LandscapeModel m = new LandscapeModel(k.seed(), k.scale(), k.regionScale(), true);
			GoldenTerrainTest.GoldenSet set = golden.get(k.key());
			List<GoldenTerrainTest.Patch> outside = new ArrayList<>();
			for (GoldenTerrainTest.Patch p : set.patches()) {
				double half = (double) p.n() / 2 * p.step();
				if (influenced(m, p.x() - half, p.z() - half, p.x() + half, p.z() + half)) {
					within.add(k.label() + " / " + p.name());
				} else {
					outside.add(p);
				}
			}
			Map<String, String> hashes = GoldenTerrainTest.hashes(outside, GoldenTerrainTest.columns(GoldenTerrainTest.samples(m, outside)));
			for (GoldenTerrainTest.Patch p : outside) {
				compared++;
				String key = "patch " + p.name();
				if (!set.hashes().get(key).equals(hashes.get(key))) {
					errors.add(k.label() + " / " + p.name());
				}
			}
		}
		System.out.println("Golden M2 with the Tatras: " + compared + " patches compared, within the reach (compared only without "
				+ "the Tatras): " + within);
		assertTrue(compared > 20, "too few patches compared");
		assertEquals(List.of(), errors, "golden M2 patches changed by the Tatras outside their reach");
	}
}
