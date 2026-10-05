package pl.polishforests.worldgen.landscape;

/**
 * Summit grid: the highest valley-free terrain ({@link LandscapeModel#landElevation}) within about 3 km·mspace
 * (a large massif in the altitudinal belts, E12, docs/03-m2-biomy.md §5.1, post-S4 fix).
 *
 * <p>Two tile levels in {@link DirectCache}. Level 1 is {@code landElevation} at nodes every {@code step}
 * (62.5 m·mspace), tiles of 32 × 32 nodes. Level 2 is the maximum of level 1 in a circle around a node every
 * {@code cellSize} (4 level-1 nodes, i.e. 250 m·mspace), tiles of 8 × 8 nodes. In a column the value is interpolated
 * bilinearly from the four nodes of the cell. The circle radius at a node is 3 km·mspace minus the cell diagonal, so
 * a value above a threshold guarantees a summit above that threshold within 3 km·mspace of the column (interpolation
 * does not exceed the largest of the four nodes, and each of them lies at most one cell diagonal from the column).
 * The 62.5 m·mspace grid underestimates the peak by at most a few meters, which is also on the safe side.
 *
 * <p>The model computes this field only in the Beskids above {@code AltitudinalBelts.SUMMIT_FROM} (about 1180 m), so tiles
 * are created only around the highest ridges. The values are a pure function of the coordinates, so the result
 * depends neither on the cache state nor on thread order.
 */
final class PeakField {
	/** Level-1 nodes per tile side. */
	private static final int T1 = 32;
	/** Level-2 nodes per tile side. */
	private static final int T2 = 8;

	private final LandscapeModel model;
	/** Level-1 node spacing (m). */
	private final double step;
	/** Level-2 node spacing (m). */
	private final double cellSize;
	/** Circle radius at a level-2 node, in level-1 nodes. */
	private final int r1;
	/** Squared radius in level-1 nodes. */
	private final double r1sq;
	/** Level-1 nodes per level-2 cell. */
	private final int nodesPerCell;
	private final DirectCache<float[]> level1;
	private final DirectCache<float[]> level2;

	/**
	 * @param radius search radius for the summit around the column (m)
	 * @param step    spacing of the {@code landElevation} nodes (m)
	 * @param nodesPerCell spacing of the maximum nodes, in level-1 nodes
	 */
	PeakField(LandscapeModel model, double radius, double step, int nodesPerCell) {
		this.model = model;
		this.step = step;
		this.nodesPerCell = nodesPerCell;
		this.cellSize = step * nodesPerCell;
		double nodeRadius = radius - this.cellSize * Math.sqrt(2);
		this.r1 = (int) Math.floor(nodeRadius / step);
		this.r1sq = (nodeRadius / step) * (nodeRadius / step);
		this.level1 = new DirectCache<>(1024, this::buildLevel1);
		this.level2 = new DirectCache<>(1024, this::buildLevel2);
	}

	/** Highest valley-free terrain within the radius (m a.s.l.), interpolated bilinearly from the cell nodes. */
	double sample(double x, double z) {
		double gx = x / cellSize;
		double gz = z / cellSize;
		long cx = (long) Math.floor(gx);
		long cz = (long) Math.floor(gz);
		double fx = gx - cx;
		double fz = gz - cz;
		double v00 = node(cx, cz);
		double v10 = node(cx + 1, cz);
		double v01 = node(cx, cz + 1);
		double v11 = node(cx + 1, cz + 1);
		return (1 - fz) * ((1 - fx) * v00 + fx * v10) + fz * ((1 - fx) * v01 + fx * v11);
	}

	private double node(long ix, long iz) {
		long tx = Math.floorDiv(ix, T2);
		long tz = Math.floorDiv(iz, T2);
		float[] t = level2.get(tx, tz);
		return t[(int) (iz - tz * T2) * T2 + (int) (ix - tx * T2)];
	}

	/** Level-1 tile: {@code landElevation} at 32 × 32 nodes. */
	private float[] buildLevel1(long tx, long tz) {
		float[] out = new float[T1 * T1];
		for (int j = 0; j < T1; j++) {
			for (int i = 0; i < T1; i++) {
				out[j * T1 + i] = (float) model.landElevation((tx * T1 + i) * step, (tz * T1 + j) * step);
			}
		}
		return out;
	}

	/** Level-2 tile: maximum of level 1 in a circle around each of the 8 × 8 nodes. */
	private float[] buildLevel2(long tx, long tz) {
		// Window of level-1 nodes covering the circles of all nodes of the tile.
		long i0 = tx * T2 * nodesPerCell - r1;
		long j0 = tz * T2 * nodesPerCell - r1;
		int windowSide = (T2 - 1) * nodesPerCell + 2 * r1 + 1;
		float[] window = new float[windowSide * windowSide];
		for (int j = 0; j < windowSide; j++) {
			for (int i = 0; i < windowSide; i++) {
				window[j * windowSide + i] = level1At(i0 + i, j0 + j);
			}
		}
		// Circle half-widths in successive rows.
		int[] halfWidths = new int[2 * r1 + 1];
		for (int dj = -r1; dj <= r1; dj++) {
			halfWidths[dj + r1] = (int) Math.floor(Math.sqrt(Math.max(0, r1sq - (double) dj * dj)));
		}
		float[] out = new float[T2 * T2];
		for (int b = 0; b < T2; b++) {
			for (int a = 0; a < T2; a++) {
				int ci = r1 + a * nodesPerCell;
				int cj = r1 + b * nodesPerCell;
				float max = Float.NEGATIVE_INFINITY;
				for (int dj = -r1; dj <= r1; dj++) {
					int hw = halfWidths[dj + r1];
					int row = (cj + dj) * windowSide;
					for (int di = -hw; di <= hw; di++) {
						float v = window[row + ci + di];
						if (v > max) {
							max = v;
						}
					}
				}
				out[b * T2 + a] = max;
			}
		}
		return out;
	}

	private float level1At(long i, long j) {
		long tx = Math.floorDiv(i, T1);
		long tz = Math.floorDiv(j, T1);
		float[] t = level1.get(tx, tz);
		return t[(int) (j - tz * T1) * T1 + (int) (i - tx * T1)];
	}
}
