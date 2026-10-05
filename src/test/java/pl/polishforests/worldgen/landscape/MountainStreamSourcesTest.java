package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.polishforests.worldgen.landscape.RiverNetworkTest.BESKIDS_GAMEPLAY;
import static pl.polishforests.worldgen.landscape.RiverNetworkTest.SEED;
import static pl.polishforests.worldgen.landscape.RiverNetworkTest.SOURCE_BLOCKS;
import static pl.polishforests.worldgen.landscape.RiverNetworkTest.SOURCE_VALLEY_STEP;
import static pl.polishforests.worldgen.landscape.RiverNetworkTest.find;
import static pl.polishforests.worldgen.landscape.RiverNetworkTest.networkOf;

import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Mountain stream sources without cliffs, split out of {@link RiverNetworkTest} (about 2 minutes on one thread) so that
 * the parallel test forks get smaller units of work. Helpers and limits stay in {@link RiverNetworkTest}.
 */
@Tag("slow")
class MountainStreamSourcesTest {
	/**
	 * At the sources of mountain streams the height difference between neighboring dry columns must not exceed a steep
	 * slope; the old river model produced a vertical cliff here. Up to 12 order 1 sources in the Beskids of both scales,
	 * transects across the valley at t from 0 to 0.5, ±60 m·k wide, steps of 1 m in x and in z between dry columns. Realistic
	 * scale: the plain step below 3 m per 1 m. Both scales (decision D4b, step K4c): the valley-made step (the plain step
	 * beyond the step of the terrain before valleys, {@code rawSurface}) at most {@value RiverNetworkTest#SOURCE_VALLEY_STEP} m per 1 m and
	 * the plain step at most {@value RiverNetworkTest#SOURCE_BLOCKS} blocks per block in the vertical scale of the world.
	 */
	@Test
	void mountainStreamSourcesHaveNoCliffs() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			RiverNetwork net = networkOf(m);
			pl.polishforests.worldgen.chunk.VerticalScale vs = sc == LandscapeScale.GAMEPLAY
					? pl.polishforests.worldgen.chunk.VerticalScale.GAMEPLAY : pl.polishforests.worldgen.chunk.VerticalScale.REAL;
			double[] site = sc == LandscapeScale.REALISTIC ? find(m, s -> s.type() == LandscapeType.BESKIDS, 5_000)
					: BESKIDS_GAMEPLAY;
			assertTrue(site != null, "no Beskids in the test area");
			double spacing = net.spacing(1);
			int sources = 0;
			// {plain step, valley-made step, blocks per block}, with the places.
			double[] worst = new double[3];
			String[] where = {"-", "-", "-"};
			long gi = (long) Math.floor(site[0] / spacing);
			long gj = (long) Math.floor(site[1] / spacing);
			for (long i = gi - 10; i <= gi + 10 && sources < 12; i++) {
				for (long j = gj - 10; j <= gj + 10 && sources < 12; j++) {
					RiverNetwork.Segment s = net.segment(1, i, j);
					if (s == null || !s.source) {
						continue;
					}
					sources++;
					for (double t = 0; t <= 0.5; t += 0.05) {
						double cx = s.px(t);
						double cz = s.pz(t);
						for (int k = -60; k <= 60; k += 3) {
							double tl = Math.hypot(s.dx(t), s.dz(t));
							double x = cx - s.dz(t) / tl * k * sc.local();
							double z = cz + s.dx(t) / tl * k * sc.local();
							// Dry terrain only: the channel bank above the water may be steep.
							ColumnSample c0 = m.sample(x, z);
							ColumnSample c1 = m.sample(x + 1, z);
							ColumnSample c2 = m.sample(x, z + 1);
							if (c0.hasWater() || c1.hasWater() || c2.hasWater()) {
								continue;
							}
							double h0 = c0.surface();
							double r0 = c0.terrain().rawSurface();
							double b0 = vs.blocksForMeters(h0);
							double[] v = {
									Math.max(Math.abs(c1.surface() - h0), Math.abs(c2.surface() - h0)),
									Math.max(Math.abs(c1.surface() - h0) - Math.abs(c1.terrain().rawSurface() - r0),
											Math.abs(c2.surface() - h0) - Math.abs(c2.terrain().rawSurface() - r0)),
									Math.max(Math.abs(vs.blocksForMeters(c1.surface()) - b0),
											Math.abs(vs.blocksForMeters(c2.surface()) - b0))};
							for (int q = 0; q < 3; q++) {
								if (v[q] > worst[q]) {
									worst[q] = v[q];
									where[q] = Math.round(x) + "," + Math.round(z);
								}
							}
						}
					}
				}
			}
			System.out.printf(Locale.ROOT, "[sources] %s: %d mountain stream sources; largest step %.2f m per 1 m at %s, "
					+ "valley-made %.2f m per 1 m at %s (limit %.1f), %.2f blocks per block at %s (limit %.1f)%n", sc.id(), sources,
					worst[0], where[0], worst[1], where[1], SOURCE_VALLEY_STEP, worst[2], where[2], SOURCE_BLOCKS);
			assertTrue(sources > 0, sc.id() + ": no mountain stream sources");
			assertTrue(sc != LandscapeScale.REALISTIC || worst[0] < 3.0, "cliff at a source: difference " + worst[0]
					+ " m per 1 m at " + where[0]);
			assertTrue(worst[1] <= SOURCE_VALLEY_STEP, sc.id() + ": valley-made cliff at a source: " + worst[1]
					+ " m per 1 m at " + where[1]);
			assertTrue(worst[2] <= SOURCE_BLOCKS, sc.id() + ": cliff at a source: " + worst[2] + " blocks per block at "
					+ where[2]);
		}
	}
}
