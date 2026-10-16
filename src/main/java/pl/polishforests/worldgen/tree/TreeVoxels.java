package pl.polishforests.worldgen.tree;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Output of the tree planner ({@link TreeShapePlan}): the wood and leaf blocks of one tree, relative to the trunk base
 * (the block above the ground). Immutable, without game classes (rule ZW-1); the feature
 * {@code polishforests:procedural_tree} turns it into block states.
 *
 * <p><b>Wood</b>, in the order of the skeleton from the root to the tips: every block has a radius (1–7 px of a thin
 * trunk, {@code TrunkBlock}, or {@link #FULL} for a full log), the direction to its parent (the face-adjacent block
 * towards the root, as {@code Direction.get3DDataValue()}: {@link #DOWN}, {@link #UP}, {@link #NORTH}, {@link #SOUTH},
 * {@link #WEST}, {@link #EAST}; the root, block 0 at (0, 0, 0), has the parent {@link #DOWN}, the ground), the index of
 * the parent block (always lower than its own, -1 for the root) and the bark ({@link #LOWER} and, for the Scots pine,
 * {@link #TRANSITION}, {@link #TRANSITION_UPPER} and {@link #UPPER}). The radius never grows from a parent to its child.
 *
 * <p><b>Leaves</b> with their distance 1–6 to the wood (the {@code distance} of a vanilla leaf block: steps through the
 * tree's own leaves to the nearest wood block), so none decays.
 */
public final class TreeVoxels {
	/** Directions to the parent, as {@code Direction.get3DDataValue()}. */
	public static final int DOWN = 0;
	public static final int UP = 1;
	public static final int NORTH = 2;
	public static final int SOUTH = 3;
	public static final int WEST = 4;
	public static final int EAST = 5;
	/** Offsets of the six directions: x, y, z. */
	static final int[][] STEP = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

	/** Radius of a full log (a whole block, wider than the 7 px of the thickest thin trunk). */
	public static final int FULL = 8;
	/** Barks: the trunk of the species (for the Scots pine the gray-brown lower bark). */
	public static final int LOWER = 0;
	/** Scots pine: the lower block of the transition zone between the lower bark and the "mirror". */
	public static final int TRANSITION = 1;
	/** Scots pine: the upper block of the transition zone. */
	public static final int TRANSITION_UPPER = 2;
	/** Scots pine: the thin orange bark of the upper trunk and the branches (the "mirror"). */
	public static final int UPPER = 3;
	/** Largest leaf distance that does not decay. */
	public static final int MAX_DISTANCE = 6;

	private final int x;
	private final int y;
	private final int z;
	private final int[] wood;
	private final byte[] radius;
	private final byte[] parent;
	private final int[] parentIndex;
	private final byte[] bark;
	private final int[] leaves;
	private final byte[] distance;
	private final float height;

	TreeVoxels(int x, int y, int z, int[] wood, byte[] radius, byte[] parent, int[] parentIndex, byte[] bark, int[] leaves,
			byte[] distance, float height) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.wood = wood;
		this.radius = radius;
		this.parent = parent;
		this.parentIndex = parentIndex;
		this.bark = bark;
		this.leaves = leaves;
		this.distance = distance;
		this.height = height;
	}

	/** Packs a relative position: dx and dz in -64..63, dy in 0..1023. */
	static int pack(int dx, int dy, int dz) {
		return (dx + 64) << 24 | (dz + 64) << 16 | dy;
	}

	static int unpackX(int p) {
		return (p >>> 24) - 64;
	}

	static int unpackY(int p) {
		return p & 0xFFFF;
	}

	static int unpackZ(int p) {
		return (p >>> 16 & 0xFF) - 64;
	}

	/** World position of the trunk base. */
	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int z() {
		return z;
	}

	/** Planned height of the tree (blocks), before clipping. */
	public float height() {
		return height;
	}

	public int woodCount() {
		return wood.length;
	}

	/** Offset of wood block i from the trunk base. */
	public int woodX(int i) {
		return unpackX(wood[i]);
	}

	public int woodY(int i) {
		return unpackY(wood[i]);
	}

	public int woodZ(int i) {
		return unpackZ(wood[i]);
	}

	/** Radius of wood block i: 1–7 px of a thin trunk or {@link #FULL}. */
	public int radius(int i) {
		return radius[i];
	}

	/** Direction from wood block i to its parent ({@link #DOWN} … {@link #EAST}). */
	public int parent(int i) {
		return parent[i];
	}

	/** Index of the parent of wood block i, -1 for the root. */
	public int parentIndex(int i) {
		return parentIndex[i];
	}

	/** Bark of wood block i ({@link #LOWER}, {@link #TRANSITION}, {@link #TRANSITION_UPPER}, {@link #UPPER}). */
	public int bark(int i) {
		return bark[i];
	}

	public int leafCount() {
		return leaves.length;
	}

	public int leafX(int i) {
		return unpackX(leaves[i]);
	}

	public int leafY(int i) {
		return unpackY(leaves[i]);
	}

	public int leafZ(int i) {
		return unpackZ(leaves[i]);
	}

	/** Distance of leaf i to the wood, 1–6. */
	public int leafDistance(int i) {
		return distance[i];
	}

	/**
	 * Leaf distances when only some wood blocks exist (the feature could not write the others: a block in the way, the
	 * top of the world): the same search through the tree's own leaves from the given wood blocks; 0 for a leaf that is
	 * farther than {@value #MAX_DISTANCE} or not reached at all, which must not be placed.
	 *
	 * @param written whether each wood block exists
	 */
	public byte[] leafDistances(boolean[] written) {
		return leafDistances(written, null);
	}

	/**
	 * Leaf distances when only some wood blocks exist and only some leaves can be placed (a leaf whose block holds
	 * terrain or another feature is skipped, and a path through it does not count): the search runs from the given wood
	 * blocks through the placeable leaves only; 0 for a leaf that is not placeable, farther than {@value #MAX_DISTANCE}
	 * or not reached at all, which must not be placed.
	 *
	 * @param written   whether each wood block exists
	 * @param placeable whether each leaf can be placed, or null for all of them
	 */
	public byte[] leafDistances(boolean[] written, boolean @Nullable [] placeable) {
		if (written.length != wood.length) {
			throw new IllegalArgumentException("one flag per wood block");
		}
		if (placeable != null && placeable.length != leaves.length) {
			throw new IllegalArgumentException("one flag per leaf");
		}
		int minX = 0;
		int maxX = 0;
		int minY = 0;
		int maxY = 0;
		int minZ = 0;
		int maxZ = 0;
		for (int[] list : new int[][] {wood, leaves}) {
			for (int p : list) {
				minX = Math.min(minX, unpackX(p));
				maxX = Math.max(maxX, unpackX(p));
				minY = Math.min(minY, unpackY(p));
				maxY = Math.max(maxY, unpackY(p));
				minZ = Math.min(minZ, unpackZ(p));
				maxZ = Math.max(maxZ, unpackZ(p));
			}
		}
		int sx = maxX - minX + 1;
		int sy = maxY - minY + 1;
		int sz = maxZ - minZ + 1;
		// 0: nothing, 1: leaf not reached yet, 2 + d: reached at distance d.
		byte[] cell = new byte[sx * sy * sz];
		int[] index = new int[cell.length];
		for (int i = 0; i < leaves.length; i++) {
			if (placeable != null && !placeable[i]) {
				continue;
			}
			int c = ((unpackX(leaves[i]) - minX) * sy + unpackY(leaves[i]) - minY) * sz + unpackZ(leaves[i]) - minZ;
			cell[c] = 1;
			index[c] = i;
		}
		int[] queue = new int[cell.length];
		int head = 0;
		int tail = 0;
		for (int i = 0; i < wood.length; i++) {
			if (written[i]) {
				int c = ((unpackX(wood[i]) - minX) * sy + unpackY(wood[i]) - minY) * sz + unpackZ(wood[i]) - minZ;
				cell[c] = 2;
				queue[tail++] = c;
			}
		}
		byte[] out = new byte[leaves.length];
		while (head < tail) {
			int c = queue[head++];
			int d = cell[c] - 2 + 1;
			if (d > MAX_DISTANCE) {
				continue;
			}
			int cx = c / (sy * sz);
			int cy = c / sz % sy;
			int cz = c % sz;
			for (int[] s : STEP) {
				int nx = cx + s[0];
				int ny = cy + s[1];
				int nz = cz + s[2];
				if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
					continue;
				}
				int n = (nx * sy + ny) * sz + nz;
				if (cell[n] == 1) {
					cell[n] = (byte) (2 + d);
					out[index[n]] = (byte) d;
					queue[tail++] = n;
				}
			}
		}
		return out;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TreeVoxels v && v.x == x && v.y == y && v.z == z && Arrays.equals(v.wood, wood)
				&& Arrays.equals(v.radius, radius) && Arrays.equals(v.parent, parent) && Arrays.equals(v.parentIndex, parentIndex)
				&& Arrays.equals(v.bark, bark) && Arrays.equals(v.leaves, leaves) && Arrays.equals(v.distance, distance)
				&& Float.compare(v.height, height) == 0;
	}

	@Override
	public int hashCode() {
		return 31 * (31 * Arrays.hashCode(wood) + Arrays.hashCode(leaves)) + Arrays.hashCode(radius);
	}

	@Override
	public String toString() {
		return "TreeVoxels[" + x + ", " + y + ", " + z + ", wood " + wood.length + ", leaves " + leaves.length + "]";
	}
}
