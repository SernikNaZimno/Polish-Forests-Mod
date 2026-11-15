package pl.polishforests.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The report of terrain differences between two mod sets (decision J1-q): counting the blocks, the tolerance, the file. */
class TerrainBlocksTest {
	private static final String HEAD = TerrainBlocks.header("realistic natural 0", 0xABCL, -64, 319);

	private static BufferedReader file(String... lines) {
		return new BufferedReader(new StringReader(String.join("\n", lines)));
	}

	@Test
	void onlyTheOtherModSetsFileGetsTheTolerance() {
		assertTrue(TerrainBlocks.isOtherModSet("plain.txt", "optimization"));
		assertTrue(TerrainBlocks.isOtherModSet("optimization.txt", "plain"));
		// The same mod set, and any file that does not name its mod set, stay exact.
		assertFalse(TerrainBlocks.isOtherModSet("plain.txt", "plain"));
		assertFalse(TerrainBlocks.isOtherModSet("optimization.txt", "optimization"));
		assertFalse(TerrainBlocks.isOtherModSet("base.txt", "optimization"));
	}

	@Test
	void countsSingleBlocksOverDifferentRuns() {
		List<TerrainBlocks.Block> first = new ArrayList<>();
		// The stone run is cut by one air block in one column only (the case of J1: cave density exactly at the limit).
		int n = TerrainBlocks.diffColumn("3 -5 -64:stone 100:air", "3 -5 -64:stone 95:air 96:stone 100:air", 319, first);
		assertEquals(1, n);
		assertEquals(new TerrainBlocks.Block(3, 95, -5, "stone", "air"), first.get(0));
	}

	@Test
	void countsARunThatEndsAtTheTopOfTheColumn() {
		List<TerrainBlocks.Block> first = new ArrayList<>();
		assertEquals(20, TerrainBlocks.diffColumn("0 0 -64:stone 300:air", "0 0 -64:stone 300:water", 319, first));
		assertEquals(TerrainBlocks.REPORTED_BLOCKS, first.size());
		assertEquals(300, first.get(0).y());
	}

	@Test
	void aSoilStepSortsTopSwapsFromOtherChanges() throws IOException {
		// Step C1 (KC-3): coarse dirt and grass on the ranker become scree near the top; a changed block deeper down, or a
		// swap of a block that is not soil, is counted apart.
		Map<String, TerrainBlocks.SoilDiff> r = TerrainBlocks.compareSoil(
				file(HEAD,
						"0 0 -64:Block{minecraft:stone} 98:Block{polishforests:granite_scree} 100:Block{minecraft:air}",
						"0 1 -64:Block{minecraft:stone} 99:Block{minecraft:grass_block} 100:Block{minecraft:air}",
						"0 2 -64:Block{minecraft:stone} 50:Block{minecraft:air} 51:Block{minecraft:stone} 100:Block{minecraft:air}"),
				file(HEAD,
						"0 0 -64:Block{minecraft:stone} 98:Block{minecraft:coarse_dirt} 99:Block{minecraft:grass_block} 100:Block{minecraft:air}",
						"0 1 -64:Block{minecraft:stone} 99:Block{minecraft:grass_block} 100:Block{minecraft:air}",
						"0 2 -64:Block{minecraft:stone} 100:Block{minecraft:air}"));
		TerrainBlocks.SoilDiff d = r.get("realistic natural 0");
		assertEquals(3, d.blocks());
		assertEquals(2, d.columns());
		assertEquals(1, d.outside());
		assertEquals(Map.of("coarse_dirt->granite_scree", 1, "grass_block->granite_scree", 1), d.pairs());
		assertEquals(50, d.firstOutside().get(0).y());
		// A soil block swapped deeper than STEP_SOIL_DEPTH under the top is not a top swap.
		TerrainBlocks.SoilDiff deep = TerrainBlocks.compareSoil(
				file(HEAD, "0 0 -64:Block{minecraft:stone} 90:Block{minecraft:dirt} 91:Block{minecraft:stone} 100:Block{minecraft:air}"),
				file(HEAD, "0 0 -64:Block{minecraft:stone} 90:Block{minecraft:coarse_dirt} 91:Block{minecraft:stone} 100:Block{minecraft:air}"))
				.get("realistic natural 0");
		assertEquals(1, deep.outside());
		assertEquals(99, TerrainBlocks.top("0 0 -64:Block{minecraft:stone} 100:Block{minecraft:air}", 319));
	}

	@Test
	void identicalAreasHaveNoDifferences() throws IOException {
		Map<String, TerrainBlocks.AreaDiff> r = TerrainBlocks.compare(file(HEAD, "0 0 -64:stone"), file(HEAD, "0 0 -64:stone"));
		assertEquals(0, r.get("realistic natural 0").blocks());
		assertEquals("abc", r.get("realistic natural 0").referenceHash());
		assertTrue(r.get("realistic natural 0").withinTolerance());
	}

	@Test
	void theToleranceIsInBlocksPerArea() throws IOException {
		int limit = TerrainBlocks.TERRAIN_TOLERANCE_BLOCKS;
		String top = 319 - limit + 1 + ":air";
		TerrainBlocks.AreaDiff atLimit = TerrainBlocks.compare(file(HEAD, "0 0 -64:stone " + top), file(HEAD, "0 0 -64:stone")).get(
				"realistic natural 0");
		assertEquals(limit, atLimit.blocks());
		assertEquals(1, atLimit.columns());
		assertTrue(atLimit.withinTolerance());
		String over = 319 - limit + ":air";
		assertFalse(TerrainBlocks.compare(file(HEAD, "0 0 -64:stone " + over), file(HEAD, "0 0 -64:stone"))
				.get("realistic natural 0").withinTolerance());
	}

	@Test
	void comparesAreasByTheirKeysAndSkipsTheOnesOfOtherScales() throws IOException {
		String other = TerrainBlocks.header("gameplay natural 0", 1, -64, 319);
		Map<String, TerrainBlocks.AreaDiff> r = TerrainBlocks.compare(file(other, "0 0 -64:stone"),
				file(HEAD, "9 9 -64:air", other, "0 0 -64:stone 0:air"));
		assertEquals(1, r.size());
		assertEquals(320, r.get("gameplay natural 0").blocks());
		// An area the reference does not have gets no entry (the caller reports it, it is not "no differences").
		assertTrue(TerrainBlocks.compare(file(other, "0 0 -64:stone"), file(HEAD, "9 9 -64:air")).isEmpty());
	}

	@Test
	void aReferenceWithOtherColumnsIsAnError() {
		assertThrows(IOException.class, () -> TerrainBlocks.compare(file(HEAD, "0 0 -64:stone", "0 1 -64:stone"),
				file(HEAD, "0 0 -64:stone")));
		assertThrows(IOException.class, () -> TerrainBlocks.compare(file(HEAD, "0 0 -64:stone"),
				file(HEAD, "0 0 -64:stone", "0 1 -64:stone")));
		assertThrows(IllegalArgumentException.class, () -> TerrainBlocks.compare(file(HEAD, "0 0 -64:stone"),
				file(HEAD, "0 1 -64:stone")));
	}
}
