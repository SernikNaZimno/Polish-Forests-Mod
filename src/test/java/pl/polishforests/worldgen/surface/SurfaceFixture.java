package pl.polishforests.worldgen.surface;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import pl.polishforests.worldgen.chunk.PolandDimension;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;

/**
 * Surface plans of chunks built as {@code PolandChunkGenerator.fill()} builds them (samples of 256 columns, habitat codes,
 * {@link SurfaceBuilder#build}), for the soil and shelf tests. One model and classifier per scale (seed of the game tests).
 */
final class SurfaceFixture {
	static final long SEED = 20260927L;
	private static final Map<PolandScale, LandscapeModel> MODELS = new HashMap<>();

	/** Plan of one chunk with its samples and codes. */
	record Chunk(int cx, int cz, ColumnSample[] columns, int[] codes, ChunkSurface surface) {
	}

	final PolandScale scale;
	final VerticalScale vertical;
	final LandscapeModel model;
	final HabitatClassifier classifier;
	final SurfaceBuilder builder;
	private final Map<Long, Chunk> chunks = new HashMap<>();

	SurfaceFixture(PolandScale scale, boolean coverInBlocks) {
		this(scale, coverInBlocks, HabitatClassifier.Mode.NATURAL);
	}

	SurfaceFixture(PolandScale scale, boolean coverInBlocks, HabitatClassifier.Mode mode) {
		this.scale = scale;
		this.vertical = scale.vertical();
		synchronized (MODELS) {
			this.model = MODELS.computeIfAbsent(scale, s -> new LandscapeModel(SEED, s.landscape(), 1.0));
		}
		this.classifier = new HabitatClassifier(SEED, scale.landscape(), mode);
		this.builder = new SurfaceBuilder(SEED, vertical, coverInBlocks, false);
	}

	/** Number of chunk plans built so far. */
	int chunks() {
		return chunks.size();
	}

	int minY() {
		return vertical.minY();
	}

	int maxY() {
		return vertical.maxY();
	}

	Chunk chunk(int cx, int cz) {
		return chunks.computeIfAbsent((long) cx << 32 | (cz & 0xFFFFFFFFL), k -> build(cx, cz));
	}

	private Chunk build(int cx, int cz) {
		int minX = cx << 4;
		int minZ = cz << 4;
		ColumnSample[] columns = new ColumnSample[256];
		int[] codes = new int[256];
		for (int i = 0; i < 256; i++) {
			columns[i] = model.sample(minX + (i >> 4), minZ + (i & 15));
			codes[i] = classifier.classify(columns[i], minX + (i >> 4), minZ + (i & 15));
		}
		ChunkSurface surface = builder.build(columns, codes, minX, minZ, minY(), maxY(), PolandDimension.DEEP_ROCK_Y,
				model::sample);
		return new Chunk(cx, cz, columns, codes, surface);
	}

	/**
	 * First block position along a square spiral from (x0, z0) with the given step (blocks) whose sample and habitat code
	 * pass the test, or null within {@code steps} points.
	 */
	int[] find(int x0, int z0, int step, int steps, Predicate<int[]> test) {
		int x = 0;
		int z = 0;
		int dx = 1;
		int dz = 0;
		int leg = 1;
		int inLeg = 0;
		int turns = 0;
		for (int i = 0; i < steps; i++) {
			int[] p = {x0 + x * step, z0 + z * step};
			if (test.test(p)) {
				return p;
			}
			x += dx;
			z += dz;
			if (++inLeg == leg) {
				inLeg = 0;
				int t = dx;
				dx = -dz;
				dz = t;
				if (++turns % 2 == 0) {
					leg++;
				}
			}
		}
		return null;
	}

	int code(int x, int z) {
		return classifier.classify(model.sample(x, z), x, z);
	}
}
