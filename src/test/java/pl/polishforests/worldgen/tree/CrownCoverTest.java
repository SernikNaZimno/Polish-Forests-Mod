package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The bare wood of a crown from its voxels (review of V9, round 2): {@link CrownCover} on small hand-made trees. */
class CrownCoverTest {
	/** A trunk of 6 blocks, an arm of 4 blocks east at height 4 and the given leaves (x, y, z triples). */
	private static TreeVoxels tree(int... leaves) {
		int[] wood = new int[10];
		byte[] radius = new byte[10];
		byte[] parent = new byte[10];
		int[] parentIndex = new int[10];
		for (int y = 0; y < 6; y++) {
			wood[y] = TreeVoxels.pack(0, y, 0);
			radius[y] = 3;
			parent[y] = TreeVoxels.DOWN;
			parentIndex[y] = y - 1;
		}
		for (int i = 0; i < 4; i++) {
			wood[6 + i] = TreeVoxels.pack(1 + i, 4, 0);
			radius[6 + i] = 1;
			parent[6 + i] = TreeVoxels.WEST;
			parentIndex[6 + i] = i == 0 ? 4 : 5 + i;
		}
		int[] lp = new int[leaves.length / 3];
		byte[] ld = new byte[lp.length];
		for (int i = 0; i < lp.length; i++) {
			lp[i] = TreeVoxels.pack(leaves[3 * i], leaves[3 * i + 1], leaves[3 * i + 2]);
			ld[i] = 1;
		}
		return new TreeVoxels(0, 64, 0, wood, radius, parent, parentIndex, new byte[10], lp, ld, 7);
	}

	@Test
	void anArmWithOneLeafAtItsTipIsBareAndOpen() {
		// One leaf beyond the tip: the crown base is at 4; trunk blocks 4 and 5 and the arm count as crown wood.
		CrownCover c = CrownCover.of(tree(5, 4, 0));
		assertEquals(6, c.crownWood());
		// Only the arm's tip touches the leaf (one leaf: still bare); every block is open to the side.
		assertEquals(6, c.bare());
		assertEquals(6, c.exposed());
	}

	@Test
	void aSleevedArmIsCovered() {
		int[] leaves = new int[3 * 8 + 6];
		int n = 0;
		for (int x = 1; x <= 4; x++) {
			for (int dz : new int[] {-1, 1}) {
				leaves[n++] = x;
				leaves[n++] = 4;
				leaves[n++] = dz;
			}
		}
		// Leaves around the trunk top too.
		int[][] top = {{0, 6, 0}, {-1, 5, 0}};
		for (int[] t : top) {
			leaves[n++] = t[0];
			leaves[n++] = t[1];
			leaves[n++] = t[2];
		}
		CrownCover c = CrownCover.of(tree(leaves));
		assertEquals(0, c.bare());
		assertEquals(0, c.exposed());
	}

	@Test
	void woodInsideAShellOfLeavesIsBareButHidden() {
		// A shell of leaves 2-3 blocks around the trunk and the arm at their height and one above: the arm has no leaf next to it, but no
		// side ray leaves the crown.
		java.util.List<Integer> l = new java.util.ArrayList<>();
		for (int dx = -2; dx <= 6; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				boolean shell = dx == -2 || dx == 6 || dz == -3 || dz == 3;
				if (shell) {
					for (int y = 4; y <= 5; y++) {
						l.add(dx);
						l.add(y);
						l.add(dz);
					}
				}
			}
		}
		CrownCover c = CrownCover.of(tree(l.stream().mapToInt(Integer::intValue).toArray()));
		assertEquals(6, c.bare());
		assertEquals(0, c.exposed());
	}
}
