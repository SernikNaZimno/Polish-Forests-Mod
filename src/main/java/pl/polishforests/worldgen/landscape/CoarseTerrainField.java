package pl.polishforests.worldgen.landscape;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Coarse terrain grid: smoothed terrain {@code sBar}, slope and aspect (docs/03-m2-biomy.md §3.2).
 *
 * <p>Nodes lie every {@code spacing} metres (32 m·k: 32 m at realistic scale, 16 m at gameplay scale), and the value
 * at a node is {@link LandscapeModel#landElevation}, i.e. the terrain without river valleys and lakes. This is the only call to
 * {@code landElevation} added in M2 to the {@link LandscapeModel#sample} path (rule Z6); the M1 code still calls it
 * for kettle ponds, lake levels and river network nodes (with its own cache). At a node we compute the mean of 3 × 3 nodes
 * ({@code sBar}, a window of about 96 m·k) and the gradient from central differences. In a column all three quantities are
 * interpolated bilinearly from the four nodes of the cell, so they are continuous, and at a node the gradient is exactly the
 * central difference.
 *
 * <p>Slope is computed in model space (horizontal and vertical metres of this scale). At gameplay scale
 * horizontal distances are compressed (k = 0.5, ranges 0.3) with similar heights in metres, so in the model
 * slopes are much steeper than at realistic scale; in the world this is evened out by the vertical mapping
 * ({@code VerticalScale}). The classifier (S4) compares the thresholds in degrees from the plan (§2, §4, §5.1) with the slope
 * in blocks: tan(slope) · d(blocks)/d(metres) at height {@code sBar} (docs/03-m2-biomy.md, state after S3).
 *
 * <p>A tile has 8 × 8 cells: derivatives at 9 × 9 nodes (sharing a node with the neighbouring tile) from 11 × 11 raw
 * nodes (margin 1). Tiles are immutable and live in a {@link DirectCache}; 4096 tiles take about 4 MB.
 * At realistic scale a tile covers 256 chunks (about 0.5 nodes per chunk), at gameplay scale 64 chunks
 * (about 2 nodes per chunk).
 *
 * <p>Single, scattered queries (commands, share tests, biome search with a large step) do not compute
 * a whole tile (121 calls to {@code landElevation}, about 0.5 ms). The first query in a tile that is not
 * in the cache computes only its own cell from 4 × 4 raw nodes (16 calls) and marks the tile; only the second one
 * computes and stores the whole tile. Both ways compute a node with the same function ({@link #node}) and interpolate
 * the same way, so the result is bit-identical. The marks are only a cost hint: a thread race
 * or an overwritten mark changes at most which way is used.
 */
final class CoarseTerrainField {
	/** Cells per tile side. */
	static final int TILE = 8;
	/** Nodes with derivatives per tile side. */
	private static final int N = TILE + 1;
	/** Raw nodes per tile side (margin 1). */
	private static final int R = TILE + 3;
	/** Number of slots in the tile cache. */
	static final int CACHE_SLOTS = 4096;

	/**
	 * Result in a column.
	 *
	 * @param sBar smoothed terrain without valleys (m a.s.l.)
	 * @param slope slope (°)
	 * @param aspect aspect (°): downslope direction clockwise from north (−Z),
	 *             90 = east (+X), 180 = south (+Z), 270 = west (−X); NaN on flat terrain
	 */
	record CoarseSample(double sBar, double slope, double aspect) {
	}

	private final LandscapeModel model;
	private final double spacing;
	private final double inv;
	private final DirectCache<float[]> tiles;
	/** Marks of tiles already queried (tile hash, 0 = none); one entry per cache slot. */
	private final AtomicLongArray seen;

	CoarseTerrainField(LandscapeModel model, double spacing) {
		this(model, spacing, CACHE_SLOTS);
	}

	/** With a different number of cache slots (eviction tests). */
	CoarseTerrainField(LandscapeModel model, double spacing, int slots) {
		this.model = model;
		this.spacing = spacing;
		this.inv = 1.0 / spacing;
		this.tiles = new DirectCache<>(slots, this::build);
		this.seen = new AtomicLongArray(slots);
	}

	/** Node spacing (m). */
	double spacing() {
		return spacing;
	}

	/** Smoothed terrain, slope and aspect at a point. */
	CoarseSample sample(double x, double z) {
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
				return interpolate(cell(cx, cz), 0, 3, 6, 9, fx, fz);
			}
			t = tiles.get(tx, tz);
		}
		int i00 = ((int) (cz - tz * TILE) * N + (int) (cx - tx * TILE)) * 3;
		return interpolate(t, i00, i00 + 3, i00 + 3 * N, i00 + 3 * N + 3, fx, fz);
	}

	/**
	 * Result from the four nodes of a cell (three numbers each: sBar, gradient x, gradient z) at indices i00 (−x, −z),
	 * i10 (+x), i01 (+z), i11; (fx, fz) is the position within the cell.
	 */
	private static CoarseSample interpolate(float[] t, int i00, int i10, int i01, int i11, double fx, double fz) {
		double w00 = (1 - fx) * (1 - fz);
		double w10 = fx * (1 - fz);
		double w01 = (1 - fx) * fz;
		double w11 = fx * fz;
		double s = w00 * t[i00] + w10 * t[i10] + w01 * t[i01] + w11 * t[i11];
		double dx = w00 * t[i00 + 1] + w10 * t[i10 + 1] + w01 * t[i01 + 1] + w11 * t[i11 + 1];
		double dz = w00 * t[i00 + 2] + w10 * t[i10 + 2] + w01 * t[i01 + 2] + w11 * t[i11 + 2];
		double m = Math.sqrt(dx * dx + dz * dz);
		double slope = Math.toDegrees(m <= 1 ? atan01(m) : Math.PI / 2 - atan01(1 / m));
		// The downslope direction is −gradient: east component −dx, north (−Z) component +dz.
		double aspect = m > 0 ? azimuth(-dx, dz) : Double.NaN;
		return new CoarseSample(s, slope, aspect);
	}

	/**
	 * Whether this is the first query for tile (tx, tz) outside the cache; marks the tile. Lock-free: the result
	 * affects only the cost, not the values.
	 */
	private boolean firstTouch(long tx, long tz) {
		long h = Noise.mix(tx * 0x632BE59BD9B4E019L + tz) | 1;
		int i = (int) h & (seen.length() - 1);
		if (seen.get(i) == h) {
			return false;
		}
		seen.set(i, h);
		return true;
	}

	/** Tile (tx, tz): sBar and gradient at 9 × 9 nodes, three numbers per node. */
	private float[] build(long tx, long tz) {
		long ix0 = tx * TILE - 1;
		long iz0 = tz * TILE - 1;
		double[] raw = new double[R * R];
		for (int j = 0; j < R; j++) {
			for (int i = 0; i < R; i++) {
				raw[j * R + i] = model.landElevation((ix0 + i) * spacing, (iz0 + j) * spacing);
			}
		}
		float[] out = new float[3 * N * N];
		for (int b = 0; b < N; b++) {
			for (int a = 0; a < N; a++) {
				node(raw, (b + 1) * R + a + 1, R, out, (b * N + a) * 3);
			}
		}
		return out;
	}

	/**
	 * Only the four nodes of cell (cx, cz) from 4 × 4 raw nodes, in the order (cx, cz), (cx + 1, cz),
	 * (cx, cz + 1), (cx + 1, cz + 1); values as in a tile.
	 */
	private float[] cell(long cx, long cz) {
		double[] raw = new double[16];
		for (int j = 0; j < 4; j++) {
			for (int i = 0; i < 4; i++) {
				raw[j * 4 + i] = model.landElevation((cx - 1 + i) * spacing, (cz - 1 + j) * spacing);
			}
		}
		float[] out = new float[12];
		node(raw, 5, 4, out, 0);
		node(raw, 6, 4, out, 3);
		node(raw, 9, 4, out, 6);
		node(raw, 10, 4, out, 9);
		return out;
	}

	/**
	 * Node at index {@code c} in the raw node array with row width {@code row}: sBar (3 × 3 mean)
	 * and the gradient from central differences, into {@code out[k..k + 2]}. One function for a tile and for a
	 * single cell, so both give the same numbers.
	 */
	private void node(double[] raw, int c, int row, float[] out, int k) {
		double sum = 0;
		for (int dj = -row; dj <= row; dj += row) {
			sum += raw[c + dj - 1] + raw[c + dj] + raw[c + dj + 1];
		}
		double inv2 = 0.5 * inv;
		out[k] = (float) (sum / 9);
		out[k + 1] = (float) ((raw[c + 1] - raw[c - 1]) * inv2);
		out[k + 2] = (float) ((raw[c + row] - raw[c - row]) * inv2);
	}

	/**
	 * Azimuth of the vector (east, north) in degrees [0, 360), clockwise from north.
	 * A fast variant of {@code atan2} (error about 10⁻⁶°), deterministic like all double arithmetic in Java.
	 */
	static double azimuth(double east, double north) {
		double ae = Math.abs(east);
		double an = Math.abs(north);
		// Angle from the north axis in [0, π/2].
		double a = ae <= an ? atan01(ae / an) : Math.PI / 2 - atan01(an / ae);
		if (north < 0) {
			a = Math.PI - a;
		}
		double deg = Math.toDegrees(a);
		if (east >= 0) {
			return deg;
		}
		// For deg below about 3·10⁻¹⁴ the difference 360 − deg rounds to 360, i.e. outside [0, 360).
		double r = 360 - deg;
		return r >= 360 ? 0 : r;
	}

	/** atan(t) for t ∈ [0, 1] (Abramowitz and Stegun 4.4.49, error ≤ 2·10⁻⁸ rad). */
	static double atan01(double t) {
		double t2 = t * t;
		return t * (0.9999993329 + t2 * (-0.3332985605 + t2 * (0.1994653599 + t2 * (-0.1390853351
				+ t2 * (0.0964200441 + t2 * (-0.0559098861 + t2 * (0.0218612288 - 0.0040540580 * t2)))))));
	}
}
