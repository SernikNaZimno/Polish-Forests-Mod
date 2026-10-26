package pl.polishforests.worldgen.surface;

import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.LooseCover;
import pl.polishforests.worldgen.chunk.PolandDimension;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.landscape.ColumnSample;

/**
 * Cost report of the thin loose cover (step K1) on a single thread, no assertions: the surface plan
 * ({@link SurfaceBuilder#build}) and the block fill as {@code PolandChunkGenerator.fill()} does it (sections of uniform rock
 * skipped by {@link ChunkSurface#uniformRock}, {@link ChunkSurface#material} for the other blocks), thin against realistic,
 * measured alternately in the same JVM over the same lowland chunks (the samples and habitat codes computed beforehand, so
 * the model is not measured). The verdict comes from the thin/realistic ratio within one run. Tagged {@code cost}: it runs
 * only through {@code ./gradlew costTest}.
 */
@Tag("cost")
class LooseCoverCostTest {
	private static final int CHUNKS = 300;
	private static final int ROUNDS = Math.max(3, Integer.getInteger("polishforests.cost.runs", 7));
	private static volatile long sink;

	private record Input(int minX, int minZ, ColumnSample[] columns, int[] codes) {
	}

	@Test
	void reportThinCoverCost() {
		for (PolandScale scale : PolandScale.values()) {
			SurfaceFixture f = new SurfaceFixture(scale, true, LooseCover.THIN);
			Input[] inputs = new Input[CHUNKS];
			int found = 0;
			for (int k = 0; found < CHUNKS && k < 20 * CHUNKS; k++) {
				int cx = (k * 4_831) % 9_000 - 4_500;
				int cz = (k * 2_957) % 9_000 - 4_500;
				int minX = cx << 4;
				int minZ = cz << 4;
				ColumnSample[] columns = new ColumnSample[256];
				int[] codes = new int[256];
				int lowland = 0;
				for (int i = 0; i < 256; i++) {
					columns[i] = f.model.sample(minX + (i >> 4), minZ + (i & 15));
					codes[i] = f.classifier.classify(columns[i], minX + (i >> 4), minZ + (i & 15));
					lowland += columns[i].coverDepth() > SurfaceBuilder.THIN_FROM_METERS ? 1 : 0;
				}
				if (lowland == 256) {
					inputs[found++] = new Input(minX, minZ, columns, codes);
				}
			}
			Input[] chunks = Arrays.copyOf(inputs, found);
			double[][] plan = new double[2][ROUNDS];
			double[][] fill = new double[2][ROUNDS];
			for (int r = -2; r < ROUNDS; r++) {
				for (int c = 0; c < 2; c++) {
					LooseCover cover = (r + c) % 2 == 0 ? LooseCover.THIN : LooseCover.REALISTIC;
					// A fresh builder each round: its border cache starts empty, as in a new area of the world.
					SurfaceBuilder b = new SurfaceBuilder(SurfaceFixture.SEED, f.vertical, true, cover, false);
					long planNanos = 0;
					long fillNanos = 0;
					for (Input in : chunks) {
						long t0 = System.nanoTime();
						ChunkSurface s = b.build(in.columns(), in.codes(), in.minX(), in.minZ(), f.minY(), f.maxY(),
								PolandDimension.DEEP_ROCK_Y, f.model::sample);
						long t1 = System.nanoTime();
						sink += fill(s, f.minY());
						fillNanos += System.nanoTime() - t1;
						planNanos += t1 - t0;
					}
					if (r >= 0) {
						int idx = cover == LooseCover.THIN ? 0 : 1;
						plan[idx][r] = planNanos / 1e6 / chunks.length;
						fill[idx][r] = fillNanos / 1e6 / chunks.length;
					}
				}
			}
			System.out.printf(Locale.ROOT, "%s, %d lowland chunks, %d rounds (median ms per chunk): plan thin %.3f realistic %.3f "
					+ "(ratio %.2f); fill thin %.3f realistic %.3f (ratio %.2f); together ratio %.2f%n", scale, chunks.length,
					ROUNDS, median(plan[0]), median(plan[1]), median(plan[0]) / median(plan[1]), median(fill[0]),
					median(fill[1]), median(fill[0]) / median(fill[1]),
					(median(plan[0]) + median(fill[0])) / (median(plan[1]) + median(fill[1])));
		}
	}

	/** The block fill of the generator: uniform rock sections skipped, {@link ChunkSurface#material} elsewhere. */
	private static long fill(ChunkSurface s, int minY) {
		int[] bounds = s.rockBounds();
		int highest = s.highest();
		long h = 0;
		for (int y0 = minY; y0 <= highest; y0 += 16) {
			Material uniform = ChunkSurface.uniformRock(y0, bounds);
			if (uniform != null) {
				h += uniform.ordinal();
				continue;
			}
			for (int i = 0; i < 256; i++) {
				for (int y = y0; y < y0 + 16; y++) {
					h += s.material(i, y).ordinal();
				}
			}
		}
		return h;
	}

	private static double median(double[] v) {
		double[] c = v.clone();
		Arrays.sort(c);
		return c[c.length / 2];
	}
}
