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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.polishforests.worldgen.habitat.HabitatClassifier;

/**
 * Step TT1 (KT-1): the Tatras change nothing outside their reach. A world with the Tatras equals the same world without
 * them bit for bit (the column sample, the terrain description with its landforms, the habitat code) everywhere outside
 * the influence square of every complex: the reach of the complex ({@link LandscapeModel#tatraReach}) plus
 * {@link #RIVER_MARGIN} order-3 node spacings of the river network (the nodes within {@link RiverNetwork#LOCAL_MARGIN}
 * spacings of a reach are computed anew, and a column reads the segments of the nodes around it). The patches of the
 * golden file {@code golden_terrain_m2.txt} at gameplay scale outside that square have the same hashes with the Tatras;
 * the patches within it (listed) are compared only without them ({@link GoldenTerrainTest}). Step TT5: the same in the
 * realistic scale (the Tatras on in new realistic worlds), around the nearest massifs of each seed and over the box of the
 * test places near the origin; the golden patches of the realistic sets too.
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

	/**
	 * The points compared: rings just outside the influence squares of the nearest massifs and a lattice over the box
	 * (gameplay: ±150 km around the origin; step TT5, realistic: the box of the test places ±150 km around the origin and
	 * the two massifs nearest the origin, which lie hundreds of kilometers away).
	 */
	static List<double[]> points(LandscapeModel m) {
		double r = influence(m);
		List<double[]> out = new ArrayList<>();
		List<LandscapeModel.Tatra> rings = new ArrayList<>(m.tatras(-150_000, -150_000, 150_000, 150_000));
		if (m.scale() == LandscapeScale.REALISTIC) {
			double box = TatraSurvey.BOX_REAL;
			List<LandscapeModel.Tatra> all = new ArrayList<>(m.tatras(-box, -box, box, box));
			all.sort(java.util.Comparator.comparingDouble(t -> Math.hypot(t.x(), t.z())));
			for (LandscapeModel.Tatra t : all.subList(0, Math.min(2, all.size()))) {
				if (!rings.contains(t)) {
					rings.add(t);
				}
			}
		}
		for (LandscapeModel.Tatra t : rings) {
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
		if (m.scale() == LandscapeScale.REALISTIC) {
			// Step TT5: a lattice over the influence square of each ring massif and 30 km beyond it (most of it inside, so
			// the points left are the margin between the square and 30 km out).
			for (LandscapeModel.Tatra t : rings) {
				double h = r + 30_000;
				for (double x = -h; x <= h; x += 3_050) {
					for (double z = -h; z <= h; z += 3_050) {
						out.add(new double[] {t.x() + x + 37, t.z() + z - 53});
					}
				}
			}
		}
		out.removeIf(p -> influenced(m, p[0], p[1], p[0], p[1]));
		return out;
	}

	@ParameterizedTest
	@MethodSource("pl.polishforests.worldgen.landscape.TatraSurvey#scales")
	void outsideTheReachNothingChanges(LandscapeScale sc) {
		for (long seed : SEEDS) {
			LandscapeModel with = new LandscapeModel(seed, sc, 1.0, true);
			LandscapeModel without = new LandscapeModel(seed, sc, 1.0, false);
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
			System.out.printf(Locale.ROOT, sc.id() + " seed %d: %d points outside the influence squares (half side %.0f m = reach %.0f m + %.0f "
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
	@ParameterizedTest
	@MethodSource("pl.polishforests.worldgen.landscape.TatraSurvey#scales")
	void changeEndsWithinTheInfluenceSquare(LandscapeScale sc) {
		for (long seed : SEEDS) {
			LandscapeModel with = new LandscapeModel(seed, sc, 1.0, true);
			LandscapeModel without = new LandscapeModel(seed, sc, 1.0, false);
			LandscapeModel.Tatra t = TatraSurvey.nearest(sc, seed, 1).get(0);
			double ds = TatraSurvey.step(sc, 250);
			double r = influence(with);
			// Step TT5: the samples within the influence square of another massif are skipped (in the realistic scale the
			// second massif of seed A lies 161 km east of the first, within 1.5 squares of it).
			List<LandscapeModel.Tatra> others = new ArrayList<>(with.tatras(t.x() - 3 * r, t.z() - 3 * r, t.x() + 3 * r, t.z() + 3 * r));
			others.removeIf(o -> o.x() == t.x() && o.z() == t.z());
			double[] last = new double[32];
			IntStream.range(0, 32).parallel().forEach(q -> {
				double a = 2 * Math.PI * q / 32;
				for (double d = 0; d <= 1.5 * r; d += ds) {
					double x = t.x() + d * Math.cos(a);
					double z = t.z() + d * Math.sin(a);
					if (others.stream().anyMatch(o -> Math.abs(x - o.x()) < r && Math.abs(z - o.z()) < r)) {
						continue;
					}
					if (!with.sample(x, z).equals(without.sample(x, z))) {
						last[q] = d;
					}
				}
			});
			double far = java.util.Arrays.stream(last).max().orElse(0);
			System.out.printf(Locale.ROOT, "%s: the farthest changed sample %.0f m from the middle (reach %.0f m, square half side %.0f m)%n",
					TatraSurvey.label(sc, seed, t), far, t.reach(), r);
			for (int q = 0; q < 32; q++) {
				double a = 2 * Math.PI * q / 32;
				double cheb = Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a))) * last[q];
				assertTrue(cheb < r, TatraSurvey.label(sc, seed, t) + ": a change outside the influence square at " + last[q] + " m");
			}
		}
	}

	/**
	 * Step TT5 (KT-2, KT-3): the fixed places of the realistic in-game tests of seed A (the sample plots and sites of the
	 * slice, the areas of {@code determinism}, the places of {@code habitats}, {@code caves}, {@code cave_biomes} and the
	 * performance harness) lie outside the influence of every realistic Tatra complex, so the Tatras now on in the
	 * realistic preset change none of them: no influence square meets the box of ±300 km around the origin (every place but
	 * two lies within it), and the two far places of the Beskids (the {@code beskids} and {@code dwarf_pine} areas of
	 * {@code determinism}, about 1060 km north) with 3 km around them lie outside every square.
	 */
	@Test
	void realisticTestPlacesLieOutsideTheTatras() {
		LandscapeModel m = TatraSurvey.with(LandscapeScale.REALISTIC, GoldenTerrainTest.SEED_A);
		double r = influence(m);
		double box = 300_000;
		List<String> near = new ArrayList<>();
		for (LandscapeModel.Tatra t : m.tatras(-box - r, -box - r, box + r, box + r)) {
			if (Math.abs(t.x()) < box + r && Math.abs(t.z()) < box + r) {
				near.add(String.format(Locale.ROOT, "(%.0f, %.0f)", t.x(), t.z()));
			}
		}
		assertEquals(List.of(), near, "realistic Tatra massifs whose influence meets ±300 km around the origin");
		int[][] far = {{154_834, 1_058_738}, {97_137, 1_034_797}};
		for (int[] p : far) {
			assertTrue(!influenced(m, p[0] - 3_000, p[1] - 3_000, p[0] + 3_000, p[1] + 3_000),
					"the place (" + p[0] + ", " + p[1] + ") lies within the influence of a realistic Tatra complex");
		}
		System.out.printf(Locale.ROOT, "realistic seed A: no Tatra influence within ±300 km (half side of the squares %.0f m) "
				+ "nor at the far Beskid places%n", r);
	}

	/**
	 * The golden file M2 at gameplay scale with the Tatras on: every patch outside the influence squares has its hash; the
	 * patches within them are listed (compared only without the Tatras by {@link GoldenTerrainTest}). Step TT5: also the
	 * realistic sets.
	 */
	@Test
	void goldenM2PatchesOutsideTheReachAreUnchanged() throws IOException {
		Map<String, GoldenTerrainTest.GoldenSet> golden = GoldenTerrainTest.read(GoldenTerrainTest.RESOURCE_CURRENT);
		int compared = 0;
		List<String> within = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (GoldenTerrainTest.SetKey k : GoldenTerrainTest.SETS) {
			if (k.scale() != LandscapeScale.GAMEPLAY && k.scale() != LandscapeScale.REALISTIC) {
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
		assertTrue(compared > 40, "too few patches compared: " + compared);
		assertEquals(List.of(), errors, "golden M2 patches changed by the Tatras outside their reach");
	}
}
