package pl.polishforests.worldgen.tree;

import java.util.Locale;

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
 */
public record CrownCover(int crownWood, int bare, int exposed) {
	/** Length of the side rays (blocks). */
	public static final int REACH = 6;
	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	public static CrownCover of(TreeVoxels v) {
		int leaves = v.leafCount();
		if (leaves == 0) {
			return new CrownCover(0, 0, 0);
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
		for (int i = 0; i < v.woodCount(); i++) {
			int x = v.woodX(i) - x0;
			int y = v.woodY(i) - y0;
			int z = v.woodZ(i) - z0;
			if (v.woodY(i) < crownBase) {
				continue;
			}
			crown++;
			int around = 0;
			for (int dy = -1; dy <= 1 && around < 2; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (leafAt(leaf, sx, sy, sz, x + dx, y + dy, z + dz)) {
							around++;
						}
					}
				}
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
		return new CrownCover(crown, bare, exposed);
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
