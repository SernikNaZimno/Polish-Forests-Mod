package pl.polishforests.worldgen.surface;

import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Bank shelf and the shore belt of the bed (docs/03-m2-biomy.md §7.2, §7.6). The landscape model keeps the bank at least
 * 1 m above the water ({@code RiverNetwork}: bank = level + 1 m) and the valley floor often 2–3 blocks above it, so dry
 * ground lies one or more blocks above the water and reeds or firefly bushes, which need water next to the block they
 * stand on, cannot grow.
 *
 * <p><b>Ramp (review of S6, round 1; step S6b).</b> A dry column within {@link #RAMP} blocks (Chebyshev) of fresh model
 * water (channel, lake, oxbow lake; not the sea) goes down a ramp of one block per column from the water. Each water
 * column within reach (water top {@code W}, distance {@code k}, the column {@code h = a − W} blocks above it) allows the
 * drop {@code min(h, MAX_DROP, FADE − h) + 1 − k} ({@link #waterDrop}): down to the water and one block more per column
 * away from it, at most {@link #MAX_DROP} blocks, so the ramp meets the model top within four columns, and less on banks
 * higher than {@code MAX_DROP} blocks above that water ({@link #FADE}). The column takes the largest drop over the
 * waters within reach. The ramp follows the real water of the model, also beyond the chunk border (sampled,
 * {@link ColumnCache}), not the habitat zone or the model's distance fields: a channel stretch without water gets no
 * ditch, and the shelf leaves no step at a zone or chunk border. Each term changes by at most one block between
 * neighbors and is at most 0 at the edge of the window, so on flat model ground the plan has steps of at most 1, and
 * next to a model step of {@code k} blocks at most {@code k + 1}. Before round 1 the whole shelf zones went down to the
 * water and their inner edge was a wall of 2–3 blocks; until step S6b the ramp top (the lowest {@code W + k − 1}) and the
 * fade (the most favorable water) came from different waters within reach, so where the water level steps a water at
 * Chebyshev 3 changed the fade of its neighbors by up to 3 blocks and left walls of 2 blocks.
 *
 * <p><b>Water cannot spill (review of S6, round 2).</b> The ramp never goes below the top of any fresh model water within
 * Manhattan distance {@link #GUARD_FULL} (the reach of water flowing from a source block and a margin for the higher
 * water spreading over the lower one at diagonal steps of the water level), also outside the chunk
 * ({@link SurfaceBuilder.Work#guardField}): near a step of the water level, a confluence or an open edge of the model the
 * water of the higher level would otherwise flow onto the lowered bank (in round 1 the guards saw only the open edges in
 * the chunk and estimated the upstream level from the change of {@code channelLevel} to the neighbors, which jumps where
 * the nearest channel switches, and left walls, pillars and spills). Beyond {@code GUARD_FULL} the guard fades by one block
 * per block and is capped at the column's model top, so it is continuous too. A solid block at the Y of the water is a
 * wall for it. On stepped mountain streams the banks therefore stay at the upper water level.
 *
 * <p>Columns next to sea water are not lowered (beach rules). The flag {@link ChunkSurface#SHORE} marks the dry columns
 * next to water in the zones {@link Zone#POINT_BAR}, {@link Zone#TALL_HERBS}, {@link Zone#SHORE_REEDBED},
 * {@link Zone#WILLOW_SCRUB} and on the first {@link #LAKE_SHORE_WIDTH} blocks of lake shores (the shore columns of the
 * test, at least 90% with water beside the top block); {@link ChunkSurface#SHELF} marks every lowered column.
 */
final class BankShelf {
	private BankShelf() {
	}

	/** Largest lowering of a column, in blocks. */
	static final int MAX_DROP = 3;
	/** Width of the lake shore of the shore columns (blocks from the water). */
	static final double LAKE_SHORE_WIDTH = 2;
	/** Width of the shore belt of the bed (blocks of water from the shore). */
	static final int SHORE_BED_WIDTH = 2;
	/**
	 * The ramp does not go below any fresh water of the model within this Manhattan distance (blocks), also outside the
	 * chunk ({@link SurfaceBuilder.Work#guardField}): water flowing from a source block reaches 7 blocks, and 2 more cover
	 * the higher water that spreads over the lower one at a diagonal step of the water level (two source neighbors turn
	 * the water above the lower water into a source: {@code FlowingFluid.getNewLiquid}).
	 */
	static final int GUARD_FULL = 9;
	/**
	 * Beyond {@link #GUARD_FULL} the guard fades by one block per block, up to this radius: a term {@code MAX_DROP} blocks
	 * beyond it would be at most {@code a − MAX_DROP}, which the shelf never goes below anyway.
	 */
	static final int GUARD_REACH = GUARD_FULL + MAX_DROP - 1;
	/** Reach of the ramp: the largest Chebyshev distance from the water of a lowered column. */
	static final int RAMP = MAX_DROP;
	/**
	 * Height above the water (blocks) at which the drop fades out: full drop on banks up to {@link #MAX_DROP} blocks
	 * above the water, then one block less per block of height, none from this height (high banks keep their shape).
	 */
	static final int FADE = 2 * MAX_DROP;
	/**
	 * Distance (blocks) within which the model's {@code channelDist} or {@code s} must put water for a column to count as
	 * near water in the summaries of the shared cache ({@link SurfaceBuilder#summary}: a puddle on the chunk edge holds
	 * water only next to a neighbor outside the chunk that is surely not lowered).
	 */
	static final double NEAR_FIELD = 10;

	/** Zones of the shore columns (the dry columns next to water in them). */
	static boolean shelfZone(int code) {
		return switch (Habitat.zone(code)) {
			case POINT_BAR, TALL_HERBS, SHORE_REEDBED, WILLOW_SCRUB -> true;
			default -> false;
		};
	}

	/** Standing water that the shelf follows: lakes, kettle ponds and oxbow lakes with a water level. */
	private static boolean standing(ColumnSample.Waters w) {
		return switch (w.standingWaterKind()) {
			case TUNNEL_VALLEY_LAKE, SINK_LAKE, KETTLE_POND, OXBOW_LAKE -> w.shoreLevel() != ColumnSample.NO_WATER;
			default -> false;
		};
	}

	/** The column lies on the first {@link #LAKE_SHORE_WIDTH} blocks of the shore of a lake, kettle pond or oxbow lake. */
	static boolean lakeShore(ColumnSample s) {
		ColumnSample.Waters w = s.waters();
		return standing(w) && w.s() > 0 && w.s() <= LAKE_SHORE_WIDTH;
	}

	/**
	 * Drop that one water column allows a dry column {@code h} blocks above its water top at Chebyshev distance {@code k}
	 * (1–{@link #RAMP}): {@code min(h, MAX_DROP, FADE − h) + 1 − k}, at most 0 when the column is not above the water or
	 * {@code k > MAX_DROP}. The drop of a column is the largest over the waters within reach, at least 0
	 * ({@link SurfaceBuilder.Work#rampDrop}); with one water level it equals the drop of rounds 1 and 2 of the review of
	 * S6. Every term changes by at most one block between two neighbors on flat model ground, so the ramp has no step of 2
	 * there, also where the water level steps (step S6b, see the class comment).
	 */
	static int waterDrop(int h, int k) {
		return Math.min(Math.min(h, MAX_DROP), FADE - h) + 1 - k;
	}

	/**
	 * Distance (blocks) from which the fields of a sample allow water ({@link #waterMayBeWithin}): the distance from the
	 * bank of the nearest channel or from the shore of the nearest standing water with water, less the margin of 2.
	 */
	static double waterReach(ColumnSample s) {
		ColumnSample.Waters w = s.waters();
		return Math.min(w.channelDist(), standingWaterDist(w)) - 2;
	}

	/** Whether the local fields of a sample put a channel or standing water within {@link #NEAR_FIELD} blocks. */
	static boolean waterMayBeNear(ColumnSample s) {
		return waterMayBeWithin(s, NEAR_FIELD - 2);
	}

	/**
	 * Distance (blocks) from the shore of the nearest standing water with water: +∞ for a peat-filled kettle without water
	 * (review of S6, round 2: its {@code s} made every raised bog "near water", so the puddles on its chunk edges were mud).
	 */
	private static double standingWaterDist(ColumnSample.Waters w) {
		return w.standingWaterKind() == ColumnSample.StandingWaterKind.KETTLE_BOG || w.shoreLevel() == ColumnSample.NO_WATER
				? Double.POSITIVE_INFINITY : w.s();
	}

	/**
	 * Lowers the dry columns near water (fills {@code top} and the flags {@link ChunkSurface#SHELF},
	 * {@link ChunkSurface#SHORE}). Before the call {@code top} holds the model tops.
	 */
	static void apply(SurfaceBuilder.Work w, ChunkSurface out) {
		// First the ramp (candidates and their drops), then the guard of the candidates.
		int[] lowered = new int[256];
		boolean[] candidates = new boolean[256];
		boolean any = false;
		for (int i = 0; i < 256; i++) {
			if (w.wet(i)) {
				continue;
			}
			ColumnSample s = w.columns[i];
			int a = out.top[i];
			int drop = w.rampDrop(i);
			boolean shoreZone = shelfZone(w.codes[i]) || lakeShore(s);
			if (drop == 0 && !shoreZone && !w.mayTouchLake(i)) {
				continue;
			}
			// The water surfaces next to the column (the neighbors outside the chunk are sampled).
			int adjacent = ChunkSurface.NO_WATER;
			boolean sea = false;
			boolean touchesLake = false;
			for (int dir = 0; dir < 4; dir++) {
				int water = w.neighborWater(i, dir);
				if (water != ChunkSurface.NO_WATER) {
					adjacent = Math.max(adjacent, water);
					WaterKind kind = w.neighborKind(i, dir);
					sea |= kind == WaterKind.SEA;
					touchesLake |= kind.isLake();
				}
			}
			if (adjacent != ChunkSurface.NO_WATER && (shoreZone || touchesLake)) {
				out.flags[i] |= ChunkSurface.SHORE;
			}
			if (drop == 0 || sea || adjacent >= a) {
				// Not lowered: no ramp, a beach, or water next to the column at or above its top.
				continue;
			}
			lowered[i] = Math.max(a - drop, adjacent);
			candidates[i] = true;
			any = true;
		}
		if (!any) {
			return;
		}
		int[] guard = w.guardField(candidates);
		for (int i = 0; i < 256; i++) {
			if (!candidates[i]) {
				continue;
			}
			int a = out.top[i];
			int t = Math.min(a, Math.max(lowered[i], guard[i]));
			if (t < a) {
				out.top[i] = t;
				out.flags[i] |= ChunkSurface.SHELF;
			}
		}
	}

	/**
	 * Whether water may lie within Chebyshev {@code d} blocks of a column by its own fields (the distance from the bank
	 * of the nearest channel and from the shore of the nearest standing water, both about 1-Lipschitz and off by at most
	 * a few blocks; the fields of an oxbow lake end 2–5 blocks from its shore): the guard
	 * ({@link SurfaceBuilder.Work#guardField}) and the ramp ({@link SurfaceBuilder.Work#rampBand}) sample a column outside
	 * the chunk only then.
	 */
	static boolean waterMayBeWithin(ColumnSample s, double d) {
		return waterReach(s) <= d;
	}

	/**
	 * Shore belt of the bed: a water column of a channel or a lake within {@link #SHORE_BED_WIDTH} blocks (Chebyshev) of
	 * a dry column of the chunk, or, near the chunk edge, within that distance of the bank by the model (channel: −d,
	 * standing water: −s). Sea and lagoon beds keep their own rule.
	 */
	static boolean shoreBed(SurfaceBuilder.Work w, int i) {
		ColumnSample s = w.columns[i];
		WaterKind kind = s.waterKind();
		if (kind != WaterKind.RIVER && !kind.isLake()) {
			return false;
		}
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
		if (kind == WaterKind.RIVER) {
			return -s.waters().channelDist() <= r;
		}
		return -s.waters().s() <= r;
	}
}
