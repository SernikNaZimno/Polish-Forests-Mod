package pl.polishforests.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.vanilla.VanillaForests;

/**
 * The origin of the dispatchers in the world type "Default + Polish Forests" (step W1): the first column of the chunk
 * whose code is a habitat biome of that world, so that the biome filter reads the biome of a forest column; none in a
 * chunk of foreign columns only.
 */
class TopOfWorldPlacementTest {
	private static ChunkHabitats habitats(int[] codes) {
		short[] top = new short[256];
		short[] water = new short[256];
		Arrays.fill(top, (short) 80);
		Arrays.fill(water, ChunkHabitats.NO_WATER);
		return new ChunkHabitats(codes, top, water, VanillaForests.OCEANICITY, VanillaForests.MOUNTAIN_INFLUENCE,
				ChunkHabitats.NO_STRUCTURES, ChunkHabitats.NO_CAVES);
	}

	@Test
	void theFirstForestColumn() {
		int[] codes = new int[256];
		Arrays.fill(codes, VanillaForests.foreignCode(VanillaForests.Foreign.LAND));
		assertEquals(-1, TopOfWorldPlacement.firstForest(habitats(codes)));
		codes[37] = VanillaForests.code(HabitatBiome.OAK_HORNBEAM_FOREST);
		codes[200] = VanillaForests.code(HabitatBiome.ALDER_CARR);
		assertEquals(37, TopOfWorldPlacement.firstForest(habitats(codes)));
		codes[3] = VanillaForests.foreignCode(VanillaForests.Foreign.RIVER);
		assertEquals(37, TopOfWorldPlacement.firstForest(habitats(codes)));
	}
}
