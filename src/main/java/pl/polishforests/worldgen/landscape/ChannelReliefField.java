package pl.polishforests.worldgen.landscape;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Coarse grid of the height above the channels (step S8c, decision M2-20 variant d2, docs/03-m2-biomy.md §3.4.1): the
 * steepness of the ground relative to the soft water level of the nearby channels, for the widths of the P_forest
 * transitions of the PRESENT_DAY forest mask ({@code ForestCover.rise}).
 *
 * <p>The value at a node is the height of the top (the ground without the floor micro-relief {@code Waters.floorFine},
 * or the water surface where there is water) above {@code Waters.softChannelLevel} of a full {@link LandscapeModel#sample}
 * at the node; no value where the node has no watercourse in reach (stream order 0 or no soft level). Nodes lie every
 * {@code spacing} meters (64 m·k). The steepness at a node is the length of the vector of the steeper one-sided difference
 * on each axis that has values at both ends, so a node on the channel of a V-shaped valley gets the slope of its sides
 * rather than the zero of a central difference; a node without a value has steepness 0. Between the nodes the steepness
 * is interpolated bilinearly, so it is continuous everywhere, also towards the edge of the reach of the river network
 * (the caller takes the larger of it and its estimate from the distance to the channel, so 0 changes nothing).
 *
 * <p>Unlike {@link CoarseTerrainField} (the terrain without valleys) the grid sees the valley sides: on the side of a wide
 * valley the estimate from the distance to the channel was 3 to 10 times too small (decision M2-20). Narrow valleys
 * (sides shorter than about two node spacings, mostly in GAMEPLAY) still get too small a value; the caller takes the
 * larger of both estimates.
 *
 * <p>Tiles of 8 × 8 cells (9 × 9 nodes from 11 × 11 raw nodes, the corners unused) live in a lock-free
 * {@link DirectCache}. A tile costs 117 samples of the model, about 0.12 samples per chunk in REAL (a tile covers 1024
 * chunks) and 0.46 in GAMEPLAY. A single query in a tile outside the cache computes only its own cell (12 raw nodes)
 * and marks the tile; the second one computes and stores the whole tile. Both ways compute a node with the same
 * function, so the result is bit-identical and depends neither on the order of the queries nor on the threads.
 * The grid is queried only by the PRESENT_DAY mask, never by {@code sample} itself.
 */
final class ChannelReliefField {
	/** Cells per tile side. */
	static final int TILE = 8;
	/** Nodes per tile side. */
	private static final int N = TILE + 1;
	/** Raw nodes per tile side (margin 1). */
	private static final int R = TILE + 3;
	static final int CACHE_SLOTS = 4096;

	private final LandscapeModel model;
	private final double spacing;
	private final double inv;
	private final DirectCache<float[]> tiles;
	private final AtomicLongArray seen;

	ChannelReliefField(LandscapeModel model, double spacing) {
		this(model, spacing, CACHE_SLOTS);
	}

	/** With a different number of cache slots (eviction tests). */
	ChannelReliefField(LandscapeModel model, double spacing, int slots) {
		this.model = model;
		this.spacing = spacing;
		this.inv = 1.0 / spacing;
		this.tiles = new DirectCache<>(slots, this::build);
		this.seen = new AtomicLongArray(slots);
	}

	double spacing() {
		return spacing;
	}

	/** Steepness (m of height above the channels per m) at the point; 0 far from any watercourse. */
	double gradient(double x, double z) {
		double gx = x * inv;
		double gz = z * inv;
		long cx = (long) Math.floor(gx);
		long cz = (long) Math.floor(gz);
		double fx = gx - cx;
		double fz = gz - cz;
		long tx = Math.floorDiv(cx, TILE);
		long tz = Math.floorDiv(cz, TILE);
		float[] t = tiles.peek(tx, tz);
		if (t == null) {
			if (firstTouch(tx, tz)) {
				return interpolate(cell(cx, cz), 0, 1, 2, 3, fx, fz);
			}
			t = tiles.get(tx, tz);
		}
		int i00 = (int) (cz - tz * TILE) * N + (int) (cx - tx * TILE);
		return interpolate(t, i00, i00 + 1, i00 + N, i00 + N + 1, fx, fz);
	}

	private static double interpolate(float[] t, int i00, int i10, int i01, int i11, double fx, double fz) {
		return (1 - fx) * (1 - fz) * t[i00] + fx * (1 - fz) * t[i10] + (1 - fx) * fz * t[i01] + fx * fz * t[i11];
	}

	private boolean firstTouch(long tx, long tz) {
		long h = Noise.mix(tx * 0x2545F4914F6CDD1DL + tz) | 1;
		int i = (int) h & (seen.length() - 1);
		if (seen.get(i) == h) {
			return false;
		}
		seen.set(i, h);
		return true;
	}

	/** Height of the top above the soft channel level at node (i, j), NaN without a watercourse. */
	private double raw(long i, long j) {
		double x = i * spacing;
		double z = j * spacing;
		ColumnSample s = model.sample(x, z);
		ColumnSample.Waters w = s.waters();
		double level = w.softChannelLevel();
		if (w.streamOrder() <= 0 || Double.isNaN(level)) {
			return Double.NaN;
		}
		double top = s.hasWater() ? s.waterLevel() : s.surface() - w.floorFine();
		return top - level;
	}

	/** Tile (tx, tz): steepness at 9 × 9 nodes. */
	private float[] build(long tx, long tz) {
		long ix0 = tx * TILE - 1;
		long iz0 = tz * TILE - 1;
		double[] raw = new double[R * R];
		for (int j = 0; j < R; j++) {
			for (int i = 0; i < R; i++) {
				boolean corner = (i == 0 || i == R - 1) && (j == 0 || j == R - 1);
				raw[j * R + i] = corner ? Double.NaN : raw(ix0 + i, iz0 + j);
			}
		}
		float[] out = new float[N * N];
		for (int b = 0; b < N; b++) {
			for (int a = 0; a < N; a++) {
				out[b * N + a] = node(raw, (b + 1) * R + a + 1, R);
			}
		}
		return out;
	}

	/** The four nodes of cell (cx, cz) from the 12 raw nodes around them, in the order (cx, cz), (cx + 1, cz), (cx, cz + 1), (cx + 1, cz + 1). */
	private float[] cell(long cx, long cz) {
		double[] raw = new double[16];
		for (int j = 0; j < 4; j++) {
			for (int i = 0; i < 4; i++) {
				boolean corner = (i == 0 || i == 3) && (j == 0 || j == 3);
				raw[j * 4 + i] = corner ? Double.NaN : raw(cx - 1 + i, cz - 1 + j);
			}
		}
		return new float[] {node(raw, 5, 4), node(raw, 6, 4), node(raw, 9, 4), node(raw, 10, 4)};
	}

	/**
	 * Steepness at raw index {@code c} (row width {@code row}): on each axis the steeper of the two one-sided differences
	 * that have a value (0 without either), 0 without a value at the node. One function for a tile and a single cell.
	 */
	private float node(double[] raw, int c, int row) {
		double v = raw[c];
		if (Double.isNaN(v)) {
			return 0;
		}
		double gx = steeper(v, raw[c - 1], raw[c + 1]);
		double gz = steeper(v, raw[c - row], raw[c + row]);
		return (float) (Math.sqrt(gx * gx + gz * gz) * inv);
	}

	private static double steeper(double v, double a, double b) {
		double da = Double.isNaN(a) ? 0 : Math.abs(v - a);
		double db = Double.isNaN(b) ? 0 : Math.abs(b - v);
		return Math.max(da, db);
	}
}
