package pl.polishforests.worldgen.tree;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Bare wood of a planned tree's crown, from its voxels alone (review of V9, round 2): the wood at or above the tree's
 * lowest leaf that has at most one leaf of its tree among its 26 neighbors and is open to the side, with no leaf of its
 * tree within {@value #REACH} blocks in at least one of the four horizontal directions at its height or one above. Such
 * wood stands bare in a side view of the crown (an arm of a "lamp post", a bare upturned tip, a limb sticking out of the
 * foliage); the wood in the core of a crown, hidden behind the leaves, and the bole below the crown do not count. The
 * leaves of other trees are not looked at, so it is an upper bound of what a player sees in a stand.
 *
 * @param crownWood the wood blocks at or above the lowest leaf
 * @param bare      the part of them with at most one own leaf among the 26 neighbors
 * @param exposed   the part of the bare wood open to the side
 * @param sparse    the crown wood with at most two own leaves among the 26 neighbors ({@link #sparseCrown}, step V10)
 */
public record CrownCover(int crownWood, int bare, int exposed, int sparse) {
	/** Length of the side rays (blocks). */
	public static final int REACH = 6;
	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	public static CrownCover of(TreeVoxels v) {
		int leaves = v.leafCount();
		if (leaves == 0) {
			return new CrownCover(0, 0, 0, 0);
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int i = 0; i < leaves; i++) {
			minX = Math.min(minX, v.leafX(i));
			minY = Math.min(minY, v.leafY(i));
			minZ = Math.min(minZ, v.leafZ(i));
			maxX = Math.max(maxX, v.leafX(i));
			maxY = Math.max(maxY, v.leafY(i));
			maxZ = Math.max(maxZ, v.leafZ(i));
		}
		for (int i = 0; i < v.woodCount(); i++) {
			minX = Math.min(minX, v.woodX(i));
			minZ = Math.min(minZ, v.woodZ(i));
			maxX = Math.max(maxX, v.woodX(i));
			maxY = Math.max(maxY, v.woodY(i));
			maxZ = Math.max(maxZ, v.woodZ(i));
		}
		int crownBase = minY;
		// A box with a margin of one block, from the crown base up.
		int x0 = minX - 1;
		int z0 = minZ - 1;
		int y0 = crownBase - 1;
		int sx = maxX - minX + 3;
		int sz = maxZ - minZ + 3;
		int sy = maxY + 2 - y0;
		boolean[] leaf = new boolean[sx * sy * sz];
		for (int i = 0; i < leaves; i++) {
			leaf[((v.leafY(i) - y0) * sx + v.leafX(i) - x0) * sz + v.leafZ(i) - z0] = true;
		}
		int crown = 0;
		int bare = 0;
		int exposed = 0;
		int sparse = 0;
		for (int i = 0; i < v.woodCount(); i++) {
			int x = v.woodX(i) - x0;
			int y = v.woodY(i) - y0;
			int z = v.woodZ(i) - z0;
			if (v.woodY(i) < crownBase) {
				continue;
			}
			crown++;
			int around = 0;
			for (int dy = -1; dy <= 1 && around < 3; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (leafAt(leaf, sx, sy, sz, x + dx, y + dy, z + dz)) {
							around++;
						}
					}
				}
			}
			if (around <= 2) {
				sparse++;
			}
			if (around > 1) {
				continue;
			}
			bare++;
			for (int[] d : SIDES) {
				boolean open = true;
				for (int s = 1; s <= REACH && open; s++) {
					open = !leafAt(leaf, sx, sy, sz, x + d[0] * s, y, z + d[1] * s)
							&& !leafAt(leaf, sx, sy, sz, x + d[0] * s, y + 1, z + d[1] * s);
				}
				if (open) {
					exposed++;
					break;
				}
			}
		}
		return new CrownCover(crown, bare, exposed, sparse);
	}

	/**
	 * A bare pole (review of V9, round 2): a tree of at least 12 blocks whose leaves are fewer than 12 or fewer than one per
	 * two blocks of its height, a thin trunk with a tuft. The crown cover above counts only the wood at or above the lowest
	 * leaf, so for such a tree the whole trunk is "bole" and passes; this catches it.
	 */
	public static boolean pole(TreeVoxels v) {
		int top = 0;
		for (int i = 0; i < v.woodCount(); i++) {
			top = Math.max(top, v.woodY(i));
		}
		int height = top + 1;
		return v.woodCount() > 0 && height >= 12 && v.leafCount() < Math.max(12, height / 2);
	}

	/**
	 * A sparse crown of a tall broad-leaved canopy tree (step V10, after the frames of the stand preview): at least
	 * {@value #SPARSE_MIN_WOOD} wood blocks at or above the lowest leaf, and {@value #SPARSE_SHARE} of them or more with at most
	 * two own leaves among their 26 neighbors. The territories of the neighbors cut such a crown down to a few tufts along the
	 * upper stem (a beech of 31 blocks with 41 leaves, 8 of its 25 crown blocks nearly bare): from above the canopy it reads as
	 * a gray pole with flat arms, which {@link #pole} (fewer than one leaf per two blocks of height) does not catch.
	 */
	public static final int SPARSE_MIN_WOOD = 8;
	public static final double SPARSE_SHARE = 0.3;

	public boolean sparseCrown() {
		return crownWood >= SPARSE_MIN_WOOD && sparse >= SPARSE_SHARE * crownWood;
	}

	/** Reach of the side check of an open stem ({@link #openStem}, review of V10, round 1). */
	public static final int STEM_REACH = 3;

	/**
	 * The longest run of stem blocks open to one side within the crown (review of V10, round 1): the upright chain of wood
	 * from the base (each block the child right above the one below), at or above the lowest leaf, with no wood or leaf
	 * of the tree within {@value #STEM_REACH} blocks on one of its four sides (at its height or one above). A trunk at the
	 * edge of its territory carried its crown on one side only and its stem stood bare above lower neighbors, a gray pole
	 * seen from above the canopy that the bare-wood counts above miss (the stem has the leaves of its one-sided crown
	 * around it).
	 */
	public static int openStem(TreeVoxels v) {
		return openStem(v, null);
	}

	/** Leaves of the other trees of a stand, by world position. */
	@FunctionalInterface
	public interface Leaves {
		boolean leaf(int x, int y, int z);
	}

	/**
	 * {@link #openStem(TreeVoxels)} in a stand: a side is closed by the leaves of the other trees too (what a player sees
	 * of the stem from above the canopy).
	 */
	public static int openStem(TreeVoxels v, @Nullable Leaves others) {
		int woods = v.woodCount();
		if (woods == 0 || v.leafCount() == 0) {
			return 0;
		}
		int lowest = Integer.MAX_VALUE;
		Set<Long> own = new HashSet<>();
		for (int i = 0; i < v.leafCount(); i++) {
			lowest = Math.min(lowest, v.leafY(i));
			own.add(key(v.leafX(i), v.leafY(i), v.leafZ(i)));
		}
		int root = -1;
		int[] above = new int[woods];
		Arrays.fill(above, -1);
		for (int i = 0; i < woods; i++) {
			own.add(key(v.woodX(i), v.woodY(i), v.woodZ(i)));
			int p = v.parentIndex(i);
			if (p < 0) {
				root = root < 0 ? i : root;
			} else if (v.parent(i) == TreeVoxels.DOWN) {
				above[p] = i;
			}
		}
		int run = 0;
		int best = 0;
		for (int i = root; i >= 0; i = above[i]) {
			int x = v.woodX(i);
			int y = v.woodY(i);
			int z = v.woodZ(i);
			if (y < lowest) {
				continue;
			}
			boolean open = false;
			for (int[] d : SIDES) {
				boolean side = true;
				for (int s = 1; s <= STEM_REACH && side; s++) {
					for (int up = 0; up <= 1 && side; up++) {
						int ax = x + d[0] * s;
						int az = z + d[1] * s;
						side = !own.contains(key(ax, y + up, az)) && (others == null || !others.leaf(v.x() + ax, v.y() + y + up, v.z() + az));
					}
				}
				open |= side;
			}
			run = open ? run + 1 : 0;
			best = Math.max(best, run);
		}
		return best;
	}

	private static long key(int x, int y, int z) {
		return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
	}

	private static boolean leafAt(boolean[] leaf, int sx, int sy, int sz, int x, int y, int z) {
		return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz && leaf[(y * sx + x) * sz + z];
	}

	/** Sums over many trees. */
	public static final class Totals {
		private int trees;
		private long crownWood;
		private long bare;
		private long exposed;

		public void add(CrownCover c) {
			trees++;
			crownWood += c.crownWood();
			bare += c.bare();
			exposed += c.exposed();
		}

		public int trees() {
			return trees;
		}

		public long exposed() {
			return exposed;
		}

		/** Share of the crown wood that is exposed, %. */
		public double exposedShare() {
			return crownWood == 0 ? 0 : 100.0 * exposed / crownWood;
		}

		/** Exposed wood blocks per tree. */
		public double exposedPerTree() {
			return trees == 0 ? 0 : (double) exposed / trees;
		}

		public String report(int chunks) {
			return String.format(Locale.ROOT, "crown wood %d, bare %d, exposed %d (%.2f%% of the crown wood, %.2f per tree, %.1f per "
					+ "chunk)", crownWood, bare, exposed, exposedShare(), exposedPerTree(), (double) exposed / Math.max(1, chunks));
		}
	}
}
