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
 * @param structures   vegetation mask of the structure pieces near each column ({@code StructureGround}: distance to
 *                     the nearest building piece in the high nibble, to any piece in the low nibble), or
 *                     {@link #NO_STRUCTURES}
 */
public record ChunkHabitats(int[] codes, short[] top, short[] water, float oceanicity, float mountainInfluence,
		byte[] structures) {
	/** {@link #water} of a column without water. */
	public static final short NO_WATER = Short.MIN_VALUE;
	/** {@link #structures} of a chunk without structure pieces nearby: every distance is the cap 15. */
	public static final byte[] NO_STRUCTURES = noStructures();

	private static byte[] noStructures() {
		byte[] b = new byte[256];
		java.util.Arrays.fill(b, (byte) 0xFF);
		return b;
	}

	/** Codec of the persistent attachment: codes, then top and water packed into one int per column. */
	public static final Codec<ChunkHabitats> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT_STREAM.comapFlatMap(s -> columns(s.toArray()), IntStream::of).fieldOf("codes")
					.forGetter(ChunkHabitats::codes),
			Codec.INT_STREAM.comapFlatMap(s -> columns(s.toArray()), IntStream::of).fieldOf("levels")
					.forGetter(ChunkHabitats::packedLevels),
			Codec.FLOAT.fieldOf("oceanicity").forGetter(ChunkHabitats::oceanicity),
			Codec.FLOAT.fieldOf("mountain_influence").forGetter(ChunkHabitats::mountainInfluence),
			// Since round 1 of the S8 review; missing in attachments saved before it (no structures nearby).
			Codec.BYTE_BUFFER.xmap(ChunkHabitats::bytes, java.nio.ByteBuffer::wrap)
					.optionalFieldOf("structures", NO_STRUCTURES).forGetter(ChunkHabitats::structures)
	).apply(i, ChunkHabitats::unpack));

	private static byte[] bytes(java.nio.ByteBuffer buffer) {
		byte[] b = new byte[buffer.remaining()];
		buffer.duplicate().get(b);
		return b.length == 256 ? b : NO_STRUCTURES;
	}

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

	private static ChunkHabitats unpack(int[] codes, int[] levels, float oceanicity, float mountainInfluence, byte[] structures) {
		LOADED.increment();
		short[] top = new short[256];
		short[] water = new short[256];
		for (int k = 0; k < 256; k++) {
			top[k] = (short) (levels[k] >> 16);
			water[k] = (short) levels[k];
		}
		return new ChunkHabitats(codes, top, water, oceanicity, mountainInfluence, structures);
	}

	public ChunkHabitats {
		if (codes.length != 256 || top.length != 256 || water.length != 256 || structures.length != 256) {
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

	/** Distance of the column to the nearest building piece of a structure (blocks, 15 = none nearby). */
	public int buildingDistance(int index) {
		return (structures[index] >> 4) & 15;
	}

	/** Distance of the column to the nearest structure piece of any kind, also a street (blocks, 15 = none nearby). */
	public int pieceDistance(int index) {
		return structures[index] & 15;
	}

	/** Whether the column is covered by water (the water surface is above the top ground block). */
	public boolean hasWater(int index) {
		return water[index] > top[index];
	}
}
