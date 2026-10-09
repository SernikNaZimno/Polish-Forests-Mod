package pl.polishforests.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Beard and vegetation mask under structure pieces ({@link StructureGround}, round 1 of the S8 review). */
class StructureGroundTest {
	/** A plan whose top rises by one block per block in x: 70 at local x = 0. */
	private static ChunkSurface slope() {
		ChunkSurface s = new ChunkSurface();
		for (int i = 0; i < 256; i++) {
			s.top[i] = 70 + (i >> 4);
			s.rockTop[i] = s.top[i] - 4;
			s.bedrockTop[i] = -60;
			s.waterTop[i] = ChunkSurface.NO_WATER;
		}
		return s;
	}

	private static int index(int x, int z) {
		return x << 4 | z;
	}

	@Test
	void noPiecesNoMask() {
		assertNull(StructureGround.apply(slope(), List.of(), 0, 0));
	}

	@Test
	void footprintStandsOnTheGroundAndTheSidesSlopeOneToOne() {
		ChunkSurface s = slope();
		// A house over local x 4..8, z 4..8 with its ground at Y 76 (the ground top at 75).
		StructureGround.Piece house = new StructureGround.Piece(4, 75, 4, 8, 82, 8, 76, true, true);
		byte[] mask = StructureGround.apply(s, List.of(house), 0, 0);
		for (int x = 4; x <= 8; x++) {
			for (int z = 4; z <= 8; z++) {
				assertEquals(75, s.top(index(x, z)), "footprint column " + x + ", " + z);
				assertEquals(0, StructureGround.building(mask[index(x, z)]));
			}
		}
		// Two blocks east of the footprint the slope rose to 80: cut to at most 75 + 2.
		assertEquals(77, s.top(index(10, 6)));
		// Four blocks west the ground is 70: filled to at least 75 − 4.
		assertEquals(71, s.top(index(0, 6)));
		// Within the slope bounds nothing changes: x = 9 has 79 > 75 + 1, so it is cut to 76.
		assertEquals(76, s.top(index(9, 6)));
		assertEquals(2, StructureGround.building(mask[index(10, 6)]));
		assertEquals(2, StructureGround.any(mask[index(10, 6)]));
		// A lowered column keeps its cover thickness.
		assertEquals(4, s.coverBlocks(index(10, 6)));
		// A raised one keeps its rock and gets a thicker cover.
		assertEquals(70 - 4, s.rockTop[index(0, 6)]);
	}

	@Test
	void streetsAndDeepPiecesDoNotMoveTheGround() {
		ChunkSurface s = slope();
		StructureGround.Piece street = new StructureGround.Piece(0, 70, 7, 15, 86, 8, 70, false, false);
		StructureGround.Piece deep = new StructureGround.Piece(0, -40, 0, 15, -30, 15, -40, true, true);
		byte[] mask = StructureGround.apply(s, List.of(street, deep), 0, 0);
		for (int i = 0; i < 256; i++) {
			assertEquals(70 + (i >> 4), s.top(i));
			// The deep piece does not reach the surface: no building nearby.
			assertEquals(StructureGround.FAR, StructureGround.building(mask[i]));
		}
		assertEquals(0, StructureGround.any(mask[index(3, 7)]));
		assertEquals(3, StructureGround.any(mask[index(3, 11)]));
	}

	@Test
	void contradictingPiecesFollowTheNearest() {
		ChunkSurface s = slope();
		StructureGround.Piece low = new StructureGround.Piece(0, 69, 0, 2, 75, 15, 70, true, true);
		StructureGround.Piece high = new StructureGround.Piece(6, 79, 0, 8, 85, 15, 80, true, true);
		StructureGround.apply(s, List.of(low, high), 0, 0);
		for (int z = 0; z < 16; z++) {
			assertEquals(69, s.top(index(1, z)));
			assertEquals(79, s.top(index(7, z)));
			// Between them, 3 blocks from each footprint: the bounds of both cannot hold; the nearer (x = 3: the low one)
			// decides, and each column stays within one block per block of its nearest footprint.
			int t3 = s.top(index(3, z));
			assertTrue(t3 >= 69 - 1 && t3 <= 69 + 1, "x = 3: " + t3);
			int t5 = s.top(index(5, z));
			assertTrue(t5 >= 79 - 1 && t5 <= 79 + 1, "x = 5: " + t5);
		}
	}
}
