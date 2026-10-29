package pl.polishforests.worldgen.cave;

import java.util.Arrays;
import java.util.Locale;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeType;
import pl.polishforests.worldgen.surface.ChunkSurface;

/**
 * Cost of the caves of step J1 per chunk (wall clock, no assertions; {@code ./gradlew costTest} on a quiet machine): the
 * surface plan of the chunk against the noise caves alone and the noise caves with the vanilla carvers, in the lowland
 * and in the mountains of both scales, medians of {@code -PcostRuns} rounds (default 7) over 64 chunks each.
 */
@Tag("cost")
class CaveCostTest {
	private static final int CHUNKS = 64;

	@Test
	void reportCostPerChunk() {
		int runs = Integer.getInteger("polishforests.cost.runs", 7);
		for (PolandScale scale : PolandScale.values()) {
			CaveFixture f = new CaveFixture(scale);
			int[] mountains = CaveFixture.find(0, 0, 512, 20_000, p -> {
				ColumnSample s = f.model.sample(p[0], p[1]);
				return s.type() == LandscapeType.BESKIDS && s.surface() > 800;
			});
			int[][] origins = {{200, -300}, mountains == null ? new int[] {0, 0} : new int[] {mountains[0] >> 4, mountains[1] >> 4}};
			String[] names = {"lowland", "mountains"};
			for (int o = 0; o < origins.length; o++) {
				ChunkSurface[] plans = new ChunkSurface[CHUNKS];
				double[] plan = new double[runs];
				double[] noise = new double[runs];
				double[] carvers = new double[runs];
				long carved = 0;
				for (int r = 0; r < runs; r++) {
					long t0 = System.nanoTime();
					for (int k = 0; k < CHUNKS; k++) {
						plans[k] = f.chunkPlan(origins[o][0] + (k & 7), origins[o][1] + (k >> 3));
					}
					long t1 = System.nanoTime();
					for (int k = 0; k < CHUNKS; k++) {
						f.caves.carve(new ChunkPos(origins[o][0] + (k & 7), origins[o][1] + (k >> 3)), plans[k], f.minY(), f.maxY(),
								f.randomState, f.columns(), null, null, null);
					}
					long t2 = System.nanoTime();
					carved = 0;
					for (int k = 0; k < CHUNKS; k++) {
						carved += f.caves.carve(new ChunkPos(origins[o][0] + (k & 7), origins[o][1] + (k >> 3)), plans[k], f.minY(),
								f.maxY(), f.randomState, f.columns(), null, null, f.carverContext).carvedBlocks();
					}
					long t3 = System.nanoTime();
					plan[r] = (t1 - t0) / 1e6 / CHUNKS;
					noise[r] = (t2 - t1) / 1e6 / CHUNKS;
					carvers[r] = (t3 - t2) / 1e6 / CHUNKS;
				}
				System.out.printf(Locale.ROOT, "[cave cost] %s %s: plan %.3f ms, noise caves %.3f ms, noise caves and carvers "
						+ "%.3f ms per chunk (medians of %d rounds of %d chunks; %d carved blocks per chunk)%n", scale, names[o],
						median(plan), median(noise), median(carvers), runs, CHUNKS, carved / CHUNKS);
			}
		}
	}

	private static double median(double[] v) {
		double[] s = v.clone();
		Arrays.sort(s);
		return s[s.length / 2];
	}
}
