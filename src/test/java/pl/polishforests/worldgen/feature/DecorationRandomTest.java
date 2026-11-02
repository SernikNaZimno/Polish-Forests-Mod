package pl.polishforests.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.feature.plan.TreeStandPlan;

/** The random sources of the decoration seeded per object position (step L1, DP-107 and DP-115). */
class DecorationRandomTest {
	private static final long WORLD = 20_260_927L;

	@Test
	void switchValues() {
		assertFalse(DecorationRandom.chunkSequence(null));
		assertFalse(DecorationRandom.chunkSequence(" "));
		assertFalse(DecorationRandom.chunkSequence("position"));
		assertTrue(DecorationRandom.chunkSequence("chunk"));
		assertTrue(DecorationRandom.chunkSequence(" CHUNK "));
		assertThrows(IllegalArgumentException.class, () -> DecorationRandom.chunkSequence("off"));
		assertFalse(DecorationRandom.CHUNK_SEQUENCE, "the unit tests run with the default");
	}

	@Test
	void newSaltsAreNotOldOnes() {
		Set<Long> old = Set.of(TreeStandPlan.SALT, TreeStandFeature.TWIN_SALT, TreeMoss.SALT);
		assertFalse(old.contains(DecorationRandom.PLANT_SALT));
		assertFalse(old.contains(DecorationRandom.TREE_SALT));
		assertNotEquals(DecorationRandom.PLANT_SALT, DecorationRandom.TREE_SALT);
	}

	/**
	 * The draws of a plant depend on its column only: the facing drawn for a column is the same whichever plants of the
	 * chunk were placed or skipped before it (the defect of DP-107 was a shared sequence), and differs between the layers.
	 */
	@Test
	void drawsOfAColumnDoNotDependOnTheOthers() {
		long layer = TreeStandPlan.SALT * 31 + 7 * 0x6A09_E667L;
		WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0));
		int[] alone = new int[256];
		for (int c = 0; c < 256; c++) {
			random.setSeed(DecorationRandom.plantSeed(WORLD, 160 + (c >> 4), -48 + (c & 15), layer));
			alone[c] = random.nextInt(4);
		}
		// Every second plant skipped, the others drawing a varying number of values (a feature plant): same values.
		for (int c = 0; c < 256; c += 2) {
			random.setSeed(DecorationRandom.plantSeed(WORLD, 160 + (c >> 4), -48 + (c & 15), layer));
			assertEquals(alone[c], random.nextInt(4));
			for (int k = 0; k < c % 7; k++) {
				random.nextLong();
			}
		}
		Set<Integer> facings = new HashSet<>();
		int sameInOtherLayer = 0;
		for (int c = 0; c < 256; c++) {
			facings.add(alone[c]);
			random.setSeed(DecorationRandom.plantSeed(WORLD, 160 + (c >> 4), -48 + (c & 15), layer + 0x6A09_E667L));
			sameInOtherLayer += random.nextInt(4) == alone[c] ? 1 : 0;
		}
		assertEquals(4, facings.size());
		assertTrue(sameInOtherLayer > 30 && sameInOtherLayer < 100, "layers draw independently: " + sameInOtherLayer);
	}

	@Test
	void seedIsAPureFunctionOfWorldColumnAndSalt() {
		long s = DecorationRandom.seed(WORLD, 12, -7, 99);
		assertEquals(s, DecorationRandom.seed(WORLD, 12, -7, 99));
		assertNotEquals(s, DecorationRandom.seed(WORLD + 1, 12, -7, 99));
		assertNotEquals(s, DecorationRandom.seed(WORLD, 13, -7, 99));
		assertNotEquals(s, DecorationRandom.seed(WORLD, 12, -6, 99));
		assertNotEquals(s, DecorationRandom.seed(WORLD, 12, -7, 100));
	}
}
