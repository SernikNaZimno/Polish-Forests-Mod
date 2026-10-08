package pl.polishforests.worldgen.chunk;

import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Habitats of one chunk (docs/03-m2-biomy.md §8.3), computed in {@code fill()} and kept as a non-persistent Fabric
 * attachment of the chunk until its last generation stage ({@code spawnOriginalMobs}). The vegetation dispatchers
 * read it instead of sampling the model again. Columns are indexed {@code x * 16 + z} (local coordinates).
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
