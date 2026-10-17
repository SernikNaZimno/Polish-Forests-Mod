package pl.polishforests.worldgen.tree;

import java.util.Arrays;

/**
 * Crown territory of one trunk ({@link CrownTerritories}): the columns over which the crown of the tree may spread, a
 * convex region around the trunk of at most {@value CrownTerritories#MAX_REACH} blocks (no longer convex when a
 * structure mask leaves columns out, {@link #without}). Rows are bit masks: row
 * {@code dz + MAX_REACH} holds bit {@code dx + MAX_REACH} for every column (x + dx, z + dz) of the territory. The trunk's
 * own column always belongs to it. Immutable.
 */
public final class CrownTerritory {
	private static final int R = CrownTerritories.MAX_REACH;

	private final int x;
	private final int z;
	private final CrownLayer layer;
	private final float radius;
	private final float reach;
	private final boolean edge;
	private final long[] rows;

	CrownTerritory(int x, int z, CrownLayer layer, float radius, float reach, boolean edge, long[] rows) {
		this.x = x;
		this.z = z;
		this.layer = layer;
		this.radius = radius;
		this.reach = reach;
		this.edge = edge;
		this.rows = rows;
	}

	/** Trunk column, x. */
	public int x() {
		return x;
	}

	/** Trunk column, z. */
	public int z() {
		return z;
	}

	public CrownLayer layer() {
		return layer;
	}

	/** Crown radius of the trunk's layer and age class (blocks), the weight of its cell. */
	public float radius() {
		return radius;
	}

	/** Largest distance of a territory column from the trunk (blocks): the radius, or more at a forest edge. */
	public float reach() {
		return reach;
	}

	/**
	 * Whether a marker stands within the edge reach of the trunk (a vacant candidate, on open ground or in the forest, or
	 * a canopy gap), so its crown may reach out to {@link CrownTerritories#EDGE_REACH} times its radius.
	 */
	public boolean edge() {
		return edge;
	}

	/** Whether the column (wx, wz) in world coordinates belongs to the territory. */
	public boolean contains(int wx, int wz) {
		int dx = wx - x;
		int dz = wz - z;
		if (dx < -R || dx > R || dz < -R || dz > R) {
			return false;
		}
		return (rows[dz + R] >>> (dx + R) & 1) != 0;
	}

	/** A test of world columns. */
	@FunctionalInterface
	public interface Columns {
		boolean test(int wx, int wz);
	}

	/**
	 * The territory without the columns {@code out} holds (a structure mask, step V9: no crown over a roof or a street),
	 * keeping the trunk's own column; the territory itself when nothing is left out.
	 */
	public CrownTerritory without(Columns out) {
		long[] kept = null;
		for (int dz = -R; dz <= R; dz++) {
			long row = rows[dz + R];
			long bits = row;
			while (bits != 0) {
				int bit = Long.numberOfTrailingZeros(bits);
				bits &= bits - 1;
				int dx = bit - R;
				if ((dx != 0 || dz != 0) && out.test(x + dx, z + dz)) {
					if (kept == null) {
						kept = rows.clone();
					}
					kept[dz + R] &= ~(1L << bit);
				}
			}
		}
		return kept == null ? this : new CrownTerritory(x, z, layer, radius, reach, edge, kept);
	}

	/** Number of columns. */
	public int area() {
		int n = 0;
		for (long row : rows) {
			n += Long.bitCount(row);
		}
		return n;
	}

	/** Mean offset of the columns from the trunk, {dx, dz} (blocks): the lean of the crown. */
	public double[] centroidOffset() {
		double sx = 0;
		double sz = 0;
		int n = 0;
		for (int dz = -R; dz <= R; dz++) {
			long row = rows[dz + R];
			while (row != 0) {
				int dx = Long.numberOfTrailingZeros(row) - R;
				sx += dx;
				sz += dz;
				n++;
				row &= row - 1;
			}
		}
		return new double[] {sx / n, sz / n};
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof CrownTerritory t && t.x == x && t.z == z && t.layer == layer && t.radius == radius
				&& t.reach == reach && t.edge == edge && Arrays.equals(t.rows, rows);
	}

	@Override
	public int hashCode() {
		return 31 * (31 * x + z) + Arrays.hashCode(rows);
	}

	@Override
	public String toString() {
		return "CrownTerritory[" + x + ", " + z + ", " + layer + ", r " + radius + ", reach " + reach + ", area " + area() + "]";
	}
}
