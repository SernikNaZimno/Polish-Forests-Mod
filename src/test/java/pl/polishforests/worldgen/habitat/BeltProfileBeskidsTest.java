package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.GreatMassifSurvey;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/**
 * Step TT3 (KT-1, docs/04-pionowy-wycinek.md "Projekt TT3"): the belts of the Beskids are unchanged by the rebuild of
 * {@link AltitudinalBelts} on {@link BeltProfile}s. The habitat codes of a test lattice (around the first two large
 * massifs of the survey window, which have every belt up to the alpine grassland, and around an interior point of the
 * Beskids) in both scales and both vegetation modes, in a world without the Tatras, fold into hashes recorded on the
 * code of before the step (commit ddf5cd6).
 */
class BeltProfileBeskidsTest {
	static final long SEED = 20260927L;
	/** Lattice points per side around each center. */
	static final int N = 72;

	/** Hash of the codes: scale, mode, value recorded before the step. */
	private static final Object[][] EXPECTED = {
			{LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL, 0xEAD084E4856D572FL},
			{LandscapeScale.REALISTIC, HabitatClassifier.Mode.PRESENT_DAY, 0x1253A84DA1C18266L},
			{LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.NATURAL, 0xC9795E9F43DF26FFL},
			{LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.PRESENT_DAY, 0x406BC9CB244C0DA7L},
	};

	/** The lattice centers and half sides (m): the first two large massifs and a Beskids interior point. */
	static double[][] centers(LandscapeModel m, LandscapeScale scale) {
		GreatMassifSurvey.Window w = GreatMassifSurvey.window(scale);
		List<LandscapeModel.GreatMassif> massifs = m.greatMassifs(w.minX(), w.minZ(), w.maxX(), w.maxZ());
		assertTrue(massifs.size() >= 2, "large massifs in the window: " + massifs.size());
		double ra = GreatMassifSurvey.ra(scale);
		double[] interior = AltitudinalBeltsTest.beskidsInterior(m);
		return new double[][] {{massifs.get(0).x(), massifs.get(0).z(), 1.3 * ra}, {massifs.get(1).x(), massifs.get(1).z(), 1.3 * ra},
				{interior[0], interior[1], 0.8 * ra}};
	}

	/** Hash of the habitat codes of the lattice (the order of the points is fixed). */
	static long hash(LandscapeModel m, HabitatClassifier c, double[][] centers, java.util.Set<HabitatBiome> seen) {
		long h = 1;
		for (double[] ctr : centers) {
			double step = 2 * ctr[2] / N;
			long[] row = IntStream.range(0, N * N).parallel().mapToLong(q -> {
				double x = ctr[0] - ctr[2] + (q % N + 0.5) * step;
				double z = ctr[1] - ctr[2] + (q / N + 0.5) * step;
				return c.classify(m.sample(x, z), x, z);
			}).toArray();
			for (long v : row) {
				seen.add(Habitat.biome((int) v));
				h = h * 0x9E3779B97F4A7C15L + v;
				h ^= h >>> 29;
			}
		}
		return h;
	}

	@Test
	void beskidsCodesUnchanged() {
		StringBuilder report = new StringBuilder();
		boolean ok = true;
		for (Object[] e : EXPECTED) {
			LandscapeScale scale = (LandscapeScale) e[0];
			HabitatClassifier.Mode mode = (HabitatClassifier.Mode) e[1];
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			HabitatClassifier c = new HabitatClassifier(SEED, m, mode);
			java.util.Set<HabitatBiome> seen = java.util.EnumSet.noneOf(HabitatBiome.class);
			long h = hash(m, c, centers(m, scale), seen);
			// The lattice covers every belt of the Beskids.
			for (HabitatBiome b : List.of(HabitatBiome.MONTANE_BEECH_FOREST, HabitatBiome.MONTANE_SPRUCE_FOREST,
					HabitatBiome.DWARF_PINE_SCRUB, HabitatBiome.ALPINE_GRASSLAND)) {
				assertTrue(mode == HabitatClassifier.Mode.PRESENT_DAY || seen.contains(b), scale.id() + ": no " + b + " on the lattice");
			}
			report.append(String.format(Locale.ROOT, "%s %s 0x%016XL%n", scale.id(), mode, h));
			ok &= h == (long) e[2];
		}
		System.out.print(report);
		assertTrue(ok, "habitat codes of the Beskids changed:\n" + report);
		assertEquals(4, EXPECTED.length);
	}
}
