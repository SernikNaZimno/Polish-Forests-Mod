package pl.polishforests.worldgen.surface;

import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Bank shelf and the shore belt of the bed (docs/03-m2-biomy.md §7.2). The landscape model keeps the bank at least
 * 1 m above the water ({@code RiverNetwork}: bank = level + 1 m), so in the realistic scale dry ground lies a block above
 * the water and reeds or firefly bushes, which need water next to the block they stand on, cannot grow. In the zones
 * {@link Zone#POINT_BAR}, {@link Zone#TALL_HERBS}, {@link Zone#SHORE_REEDBED}, in the first 1–2 blocks of
 * {@link Zone#WILLOW_SCRUB} and on the first 1–2 blocks of lake shores the top ground block is lowered to the Y of the
 * top water block.
 *
 * <p>Water cannot spill: a dry column next to water (row 1) is lowered exactly to the highest water surface among its
 * four neighbors (the neighbors outside the chunk are sampled), so a solid block is a wall for the water at the same Y and
 * there is air only above it; a column further from the water is lowered to the level of its channel or lake from the
 * model and has no water next to it. A column is lowered by at most {@link #MAX_DROP} blocks (no pits by steep banks).
 * In the gameplay scale (about 0.4 block per meter) the bank and the water often share a block, and the shelf changes
 * nothing.
 */
final class BankShelf {
	private BankShelf() {
	}

	/** Largest lowering of a column, in blocks. */
	static final int MAX_DROP = 3;
	/** Width of the shelf in the willow scrub and on lake shores (blocks from the water). */
	static final double SHELF_WIDTH = 2;
	/** Width of the shore belt of the bed (blocks of water from the shore). */
	static final int SHORE_BED_WIDTH = 2;

	/** Shelf zones of a column; the willow scrub only in its first {@link #SHELF_WIDTH} blocks. */
	static boolean shelfZone(int code, ColumnSample s) {
		Zone zone = Habitat.zone(code);
		return switch (zone) {
			case POINT_BAR, TALL_HERBS, SHORE_REEDBED -> true;
			case WILLOW_SCRUB -> Math.min(s.waters().channelDist(), s.waters().floorChannelDist()) <= SHELF_WIDTH;
			default -> false;
		};
	}

	/** The column lies on the first {@link #SHELF_WIDTH} blocks of the shore of a lake, kettle pond or oxbow lake. */
	static boolean lakeShore(ColumnSample s) {
		ColumnSample.Waters w = s.waters();
		return switch (w.standingWaterKind()) {
			case TUNNEL_VALLEY_LAKE, SINK_LAKE, KETTLE_POND, OXBOW_LAKE -> w.s() > 0 && w.s() <= SHELF_WIDTH
					&& w.shoreLevel() != ColumnSample.NO_WATER;
			default -> false;
		};
	}

	/**
	 * Lowers the shelf columns (fills {@code top} and the flags {@link ChunkSurface#SHELF}, {@link ChunkSurface#SHORE}).
	 * Before the call {@code top} holds the model tops.
	 */
	static void apply(SurfaceBuilder.Work w, ChunkSurface out) {
		for (int i = 0; i < 256; i++) {
			if (w.wet(i)) {
				continue;
			}
			ColumnSample s = w.columns[i];
			boolean zone = shelfZone(w.codes[i], s);
			boolean lake = lakeShore(s);
			if (!zone && !lake && !w.mayTouchLake(i)) {
				continue;
			}
			// Row 1: the highest water surface among the four neighbors.
			int t = ChunkSurface.NO_WATER;
			boolean touchesLake = false;
			for (int dir = 0; dir < 4; dir++) {
				int water = w.neighborWater(i, dir);
				if (water != ChunkSurface.NO_WATER) {
					t = Math.max(t, water);
					touchesLake |= w.neighborKind(i, dir).isLake();
				}
			}
			if (t != ChunkSurface.NO_WATER) {
				if (!zone && !lake && !touchesLake) {
					continue;
				}
				out.flags[i] |= ChunkSurface.SHORE;
			} else {
				if (!zone && !lake) {
					continue;
				}
				t = w.levelBlock(i, lake);
				if (t == ChunkSurface.NO_WATER) {
					continue;
				}
			}
			int drop = out.top[i] - t;
			if (drop >= 0 && drop <= MAX_DROP) {
				out.top[i] = t;
				out.flags[i] |= ChunkSurface.SHELF;
			}
		}
	}

	/**
	 * Shore belt of the bed: a water column within {@link #SHORE_BED_WIDTH} blocks (Chebyshev) of a dry column of the
	 * chunk, or, near the chunk edge, within that distance of the bank by the model (channel: −d, standing water: −s).
	 */
	static boolean shoreBed(SurfaceBuilder.Work w, int i) {
		int x = i >> 4;
		int z = i & 15;
		int r = SHORE_BED_WIDTH;
		for (int dx = -r; dx <= r; dx++) {
			int nx = x + dx;
			if (nx < 0 || nx > 15) {
				continue;
			}
			for (int dz = -r; dz <= r; dz++) {
				int nz = z + dz;
				if (nz >= 0 && nz <= 15 && !w.wet(nx << 4 | nz)) {
					return true;
				}
			}
		}
		if (x >= r && x <= 15 - r && z >= r && z <= 15 - r) {
			return false;
		}
		ColumnSample s = w.columns[i];
		if (s.waterKind() == WaterKind.RIVER) {
			return -s.waters().channelDist() <= r;
		}
		return s.waterKind().isLake() && -s.waters().s() <= r;
	}
}
