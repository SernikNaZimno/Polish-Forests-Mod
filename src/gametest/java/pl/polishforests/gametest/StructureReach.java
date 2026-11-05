package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.surface.StructureGround;

/**
 * Columns near a vanilla structure piece (stage 2 of step F3). The feature elements of a jigsaw structure (the random
 * patches and the trees of a taiga village's decoration) stand on a one-block piece and write up to 7 blocks away from it,
 * also into the neighbor chunks, so their blocks are the structure's: a vanilla spruce of a village in a forest of the
 * planner, ferns of a village patch that depend on the order of the chunks (as in vanilla). The chunk's own vegetation mask
 * ({@code ChunkHabitats.pieceDistance}) only sees the starts that reference the chunk, which a one-block element of a
 * start in the neighbor chunk does not, so the tests look at the starts of the area with a margin of one chunk.
 */
final class StructureReach {
	/** Chebyshev distance (blocks) from a piece's bounding box within which a block is the structure's. */
	static final int REACH = 8;

	private final List<StructureGround.Piece> pieces;

	private StructureReach(List<StructureGround.Piece> pieces) {
		this.pieces = pieces;
	}

	/** The pieces of every structure start referenced by a chunk from (cx0, cz0) to (cx1, cz1), each widened by one chunk. */
	static StructureReach of(ServerLevel level, int cx0, int cz0, int cx1, int cz1) {
		List<StructureGround.Piece> pieces = new ArrayList<>();
		for (int cx = cx0 - 1; cx <= cx1 + 1; cx++) {
			for (int cz = cz0 - 1; cz <= cz1 + 1; cz++) {
				pieces.addAll(PolandChunkGenerator.structurePieces(level.structureManager(), new ChunkPos(cx, cz)));
			}
		}
		return new StructureReach(pieces);
	}

	/**
	 * Whether the block (x, y, z) is within {@link #REACH} of a structure piece (also in height, so the pieces of a mineshaft
	 * deep below do not take the forest floor above them).
	 */
	boolean near(int x, int y, int z) {
		for (StructureGround.Piece p : pieces) {
			if (x >= p.minX() - REACH && x <= p.maxX() + REACH && z >= p.minZ() - REACH && z <= p.maxZ() + REACH
					&& y >= p.minY() - REACH && y <= p.maxY() + REACH) {
				return true;
			}
		}
		return false;
	}

	boolean isEmpty() {
		return pieces.isEmpty();
	}
}
