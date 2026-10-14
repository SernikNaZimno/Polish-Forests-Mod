package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Forest mask of the PRESENT_DAY mode (step S8c, decision M2-27 b): the early exit of {@link ForestCover#isForested} by
 * the bounds of P_forest gives the decision of the full rule "P_forest ≥ 1, or F &lt; P_forest", and every forest biome
 * except the dwarf pine scrub and the gray alder forest has P_forest within {@link ForestCover#P_LOWEST} …
 * {@link ForestCover#P_HIGHEST}. Clusters of 8 × 8 columns every 4 m·k over the area of the share test, both scales.
 */
class ForestCoverTest {
	private static final long SEED = 20260927L;

	@Test
	void earlyExitKeepsTheDecision() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			HabitatClassifier natural = new HabitatClassifier(SEED, scale, HabitatClassifier.Mode.NATURAL);
			HabitatClassifier d = new HabitatClassifier(SEED, m, HabitatClassifier.Mode.PRESENT_DAY);
			double side = scale == LandscapeScale.REALISTIC ? 400_000 : 30_000;
			double step = 4 * scale.local();
			AtomicLong forest = new AtomicLong();
			AtomicLong checked = new AtomicLong();
			AtomicLong skipped = new AtomicLong();
			double[] range = {Double.MAX_VALUE, -Double.MAX_VALUE};
			IntStream.range(0, 600).parallel().forEach(q -> {
				long h = Noise.mix(SEED * 17 + q);
				double cx = ((h >>> 11) * 0x1.0p-53 - 0.5) * side;
				double cz = ((Noise.mix(h) >>> 11) * 0x1.0p-53 - 0.5) * side;
				for (int i = 0; i < 64; i++) {
					double x = cx + (i % 8) * step;
					double z = cz + (i / 8) * step;
					ColumnSample s = m.sample(x, z);
					Habitat n = Habitat.of(natural.classify(s, x, z));
					if (!n.biome().isForest()) {
						continue;
					}
					HabitatClassifier.Column c = new HabitatClassifier.Column(d, s, x, z);
					ForestSiteType site = ForestSiteType.forBiome(n.biome(), c.fertility(), c.moisture());
					double p = d.forestCover.forestProbability(c, n.biome(), site);
					double f = d.forestCover.f(x, z);
					boolean full = p >= 1 || p > 0 && f < p;
					boolean fast = d.forestCover.isForested(new HabitatClassifier.Column(d, s, x, z), n.biome(), site);
					assertEquals(full, fast, String.format(Locale.ROOT, "early exit at (%.1f, %.1f) %s: P %.4f, F %.4f", x, z, n.biome(), p, f));
					checked.incrementAndGet();
					if (fast) {
						forest.incrementAndGet();
					}
					if (f < ForestCover.P_LOWEST || f >= ForestCover.P_HIGHEST) {
						skipped.incrementAndGet();
					}
					if (n.biome() != HabitatBiome.DWARF_PINE_SCRUB && n.biome() != HabitatBiome.GRAY_ALDER_FOREST) {
						assertTrue(p >= ForestCover.P_LOWEST && p <= ForestCover.P_HIGHEST,
								String.format(Locale.ROOT, "P_forest %.6f outside the bounds at (%.1f, %.1f) %s", p, x, z, n.biome()));
						synchronized (range) {
							range[0] = Math.min(range[0], p);
							range[1] = Math.max(range[1], p);
						}
					}
				}
			});
			System.out.println(String.format(Locale.ROOT, "[forest cover] %s: %d forest columns of the natural mode, %.1f%% forested in the PRESENT_DAY mode,"
					+ " P_forest %.3f–%.3f, early exit without P_forest %.1f%%", scale.id(), checked.get(), 100.0 * forest.get() / checked.get(),
					range[0], range[1], 100.0 * skipped.get() / checked.get()));
			assertTrue(checked.get() > 10_000, "too few forest columns: " + checked.get());
		}
	}
}
