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
 * <p><b>Ramp (review of S6, round 1).</b> A dry column within {@link #RAMP} blocks (Chebyshev) of fresh model water
 * (channel, lake, oxbow lake; not the sea) goes down a ramp of one block per column from the water: at most to
 * {@code W + k − 1} for every water column within reach (water top {@code W}, distance {@code k}), at most
 * {@code MAX_DROP + 1 − c} blocks at distance {@code c} from the nearest water, so the ramp meets the model top within
 * four columns, and less on banks higher than {@link #MAX_DROP} blocks ({@link #FADE}). The ramp follows the real water
 * of the model, also beyond the chunk border (sampled, {@link ColumnCache}), not the habitat zone or the model's
 * distance fields: it is the same whichever chunk computes it, a channel stretch without water gets no ditch, and the
 * shelf leaves no step at a zone or chunk border. Next to a model step of {@code k} blocks the plan has a step of at
 * most {@code k + 1}, on flat model ground at most 1. Before the round the whole shelf zones went down to the water and
 * their inner edge was a wall of 2–3 blocks.
 *
 * <p><b>Water cannot spill.</b> The ramp never goes below a water surface of the model next to the column (the
 * neighbors outside the chunk are sampled) or within {@link #NEAR_WATER} blocks, below the water of an open edge of the
 * model within {@link #FLOW_GUARD} blocks in the chunk, or below the channel level {@link #FLOW_GUARD} blocks upstream
 * ({@link #levelStep}): near a step of the river level water flows from the upper water over the lower one and would
 * spread over a bank lowered below it. A solid block at the Y of the water is a wall for it.
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
	/** The ramp does not go below any water of the model within this Chebyshev radius in the chunk (blocks). */
	static final int NEAR_WATER = 2;
	/** The ramp does not go below the water of an open edge of the model within this Chebyshev radius (blocks). */
	static final int FLOW_GUARD = 8;
	/** Reach of the ramp: the largest Chebyshev distance from the water of a lowered column. */
	static final int RAMP = MAX_DROP;
	/**
	 * Height above the water (blocks) at which the drop fades out: full drop on banks up to {@link #MAX_DROP} blocks
	 * above the water, then one block less per block of height, none from this height (high banks keep their shape).
	 */
	static final int FADE = 2 * MAX_DROP;
	/**
	 * Distance (blocks) within which the model's {@code channelDist} or {@code s} must put water for the generator to
	 * sample the neighbors outside the chunk when looking for the nearest water (the fields are continuous and off by at
	 * most a few blocks).
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
	 * Drop of a dry column with model top {@code a}, ramp top {@code r} (the lowest {@code W + k − 1} over the water
	 * within reach) and distance {@code c} (blocks, 1–{@link #RAMP}) from the nearest water: down to the ramp, at most
	 * {@code MAX_DROP + 1 − c}, and at most {@code FADE − h + 1 − c} at height {@code h = a − (r − c + 1)} above the
	 * water. With one water level {@code W} this is {@code min(h, MAX_DROP, FADE − h) + 1 − c}, at least 0.
	 */
	static int drop(int a, int r, int c) {
		int drop = Math.min(a - r, MAX_DROP + 1 - c);
		int h = a - (r - c + 1);
		return Math.max(0, Math.min(drop, FADE - h + 1 - c));
	}

	/** Whether the local fields of a sample put a channel or standing water within {@link #NEAR_FIELD} blocks. */
	static boolean waterMayBeNear(ColumnSample s) {
		ColumnSample.Waters w = s.waters();
		return w.channelDist() <= NEAR_FIELD || w.s() <= NEAR_FIELD;
	}

	/**
	 * Lowers the dry columns near water (fills {@code top} and the flags {@link ChunkSurface#SHELF},
	 * {@link ChunkSurface#SHORE}). Before the call {@code top} holds the model tops.
	 */
	static void apply(SurfaceBuilder.Work w, ChunkSurface out) {
		int[] flow = null;
		for (int i = 0; i < 256; i++) {
			if (w.wet(i)) {
				continue;
			}
			ColumnSample s = w.columns[i];
			int a = out.top[i];
			int drop = w.nearestWater(i) ? drop(a, w.rampTop, w.nearestDist) : 0;
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
			if (drop == 0 || sea || adjacent > a) {
				// Not lowered: no ramp, a beach, or a model edge where the water already stands above the ground.
				continue;
			}
			if (flow == null) {
				flow = w.openEdgesWithin(FLOW_GUARD);
			}
			int t = Math.max(Math.max(a - drop, adjacent), Math.max(w.waterNear(i, NEAR_WATER), Math.max(flow[i], levelStep(w, i))));
			if (t < a) {
				out.top[i] = t;
				out.flags[i] |= ChunkSurface.SHELF;
			}
		}
	}

	/**
	 * Water top of the channel level a few blocks upstream: the Y of {@code floor(level + (FLOW_GUARD + 1) · g)}, where the
	 * level is the nearest channel's level and {@code g} its largest change towards a neighbor in the chunk (m per block:
	 * the gradient along the channel). Within {@link #FLOW_GUARD} blocks downstream of a step of the river level this is
	 * the upper water, whose open edge lets water flow over the lower one onto the bank, also from the next chunk;
	 * elsewhere it is the channel's own water top. {@link ChunkSurface#NO_WATER} without a channel, or when the nearest
	 * water is not this channel's.
	 */
	static int levelStep(SurfaceBuilder.Work w, int i) {
		ColumnSample.Waters ws = w.columns[i].waters();
		double level = ws.channelLevel();
		if (Double.isNaN(level)) {
			return ChunkSurface.NO_WATER;
		}
		double g = 0;
		for (int dir = 0; dir < 4; dir++) {
			int n = SurfaceBuilder.Work.neighbor(i, dir);
			if (n >= 0) {
				double other = w.columns[n].waters().channelLevel();
				if (!Double.isNaN(other)) {
					g = Math.max(g, Math.abs(other - level));
				}
			}
		}
		int own = Math.min(w.builder.vertical().topBlockY(Math.floor(level)), w.maxY);
		if (own != w.nearestLevel) {
			// The nearest water is not this channel's (a lake, another channel).
			return ChunkSurface.NO_WATER;
		}
		return Math.min(w.builder.vertical().topBlockY(Math.floor(level + (FLOW_GUARD + 1) * g)), w.maxY);
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
