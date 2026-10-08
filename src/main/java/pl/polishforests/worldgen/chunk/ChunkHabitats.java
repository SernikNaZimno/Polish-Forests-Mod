package pl.polishforests.worldgen.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.IntStream;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Habitats of one chunk (docs/03-m2-biomy.md §8.3), computed in {@code fill()} and kept as a Fabric attachment of
 * the chunk until its last generation stage ({@code spawnOriginalMobs}). The vegetation dispatchers read it instead of
 * sampling the model again. The attachment is persistent ({@link #CODEC}), so a proto-chunk saved between TERRAIN and
 * FEATURES (the player moves away and comes back, the server stops) keeps it; full chunks no longer hold it. Columns are
 * indexed {@code x * 16 + z} (local coordinates).
 *
 * <p>The arrays are not copied and must not be modified after construction.
 *
 * @param codes        habitat code of each column ({@link Habitat})
 * @param top          Y of the top ground block of each column
 * @param water        Y of the top water block of each column, or {@link #NO_WATER}
 * @param oceanicity   oceanicity O at the chunk center (practically constant over a chunk, §9)
 * @param mountainInfluence mountain influence P at the chunk center
 */
public record ChunkHabitats(int[] codes, short[] top, short[] water, float oceanicity, float mountainInfluence) {
	/** {@link #water} of a column without water. */
	public static final short NO_WATER = Short.MIN_VALUE;

	/** Codec of the persistent attachment: codes, then top and water packed into one int per column. */
	public static final Codec<ChunkHabitats> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT_STREAM.comapFlatMap(s -> columns(s.toArray()), IntStream::of).fieldOf("codes")
					.forGetter(ChunkHabitats::codes),
			Codec.INT_STREAM.comapFlatMap(s -> columns(s.toArray()), IntStream::of).fieldOf("levels")
					.forGetter(ChunkHabitats::packedLevels),
			Codec.FLOAT.fieldOf("oceanicity").forGetter(ChunkHabitats::oceanicity),
			Codec.FLOAT.fieldOf("mountain_influence").forGetter(ChunkHabitats::mountainInfluence)
	).apply(i, ChunkHabitats::unpack));

	private static DataResult<int[]> columns(int[] values) {
		return values.length == 256 ? DataResult.success(values)
				: DataResult.error(() -> "ChunkHabitats needs 256 columns, got " + values.length);
	}

	private int[] packedLevels() {
		int[] out = new int[256];
		for (int k = 0; k < 256; k++) {
			out[k] = top[k] << 16 | (water[k] & 0xFFFF);
		}
		return out;
	}

	/** Number of attachments read from saved proto-chunks (diagnostics of the game test "habitats"). */
	public static final java.util.concurrent.atomic.LongAdder LOADED = new java.util.concurrent.atomic.LongAdder();

	private static ChunkHabitats unpack(int[] codes, int[] levels, float oceanicity, float mountainInfluence) {
		LOADED.increment();
		short[] top = new short[256];
		short[] water = new short[256];
		for (int k = 0; k < 256; k++) {
			top[k] = (short) (levels[k] >> 16);
			water[k] = (short) levels[k];
		}
		return new ChunkHabitats(codes, top, water, oceanicity, mountainInfluence);
	}

	public ChunkHabitats {
		if (codes.length != 256 || top.length != 256 || water.length != 256) {
			throw new IllegalArgumentException("ChunkHabitats needs 256 columns");
		}
	}

	/** Index of the column with local coordinates (x, z). */
	public static int index(int x, int z) {
		return x << 4 | z;
	}

	public int code(int x, int z) {
		return codes[index(x, z)];
	}

	public HabitatBiome biome(int x, int z) {
		return Habitat.biome(code(x, z));
	}

	/** Whether the column is covered by water (the water surface is above the top ground block). */
	public boolean hasWater(int index) {
		return water[index] > top[index];
	}
}
