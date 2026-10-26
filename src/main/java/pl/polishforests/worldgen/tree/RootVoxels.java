package pl.polishforests.worldgen.tree;

import java.util.Arrays;

/**
 * Output of the root planner ({@link RootPlan}, step I1): the root flares over the ground and the roots and rooted dirt
 * in the ground of one tree, relative to its trunk base (the block above the ground of the trunk column), without game
 * classes (rule ZW-1); the tree stand writes it after the tree ({@code TreeRoots}).
 *
 * <p><b>Flares</b> lie at the level of the trunk base (dy 0), each flare a run of consecutive blocks from the trunk
 * outwards (the first with the parent index -1, every other one with the index of the block before it), straight out
 * from a face of the trunk: per block the stage of the fin's profile ({@link #flareRadius}: n, …, 1 for a flare of n
 * blocks, the value of the block's {@code radius} property; {@code RootFlareBlock}), the direction to the parent (as
 * {@link TreeVoxels#parent}: the trunk base for the first block of a flare, else the previous block) and the index of
 * the parent flare block (-1: the trunk base).
 *
 * <p><b>Roots</b> lie in the ground (dy -2 and lower), each with a radius 1–7 px, the direction to its parent (towards the
 * trunk) and the index of the parent root (-1: the trunk base above the root collar). The radius never grows from a
 * parent to its child. A cell of the root system that the planner knows to touch open air at a side (a bank, a step of
 * the ground) is rooted dirt instead of a root ({@link #EXPOSED}).
 *
 * <p><b>Rooted dirt</b>: cells of the soil around the roots and the exposed cells of the root system.
 */
public final class RootVoxels {
	/** Offset of dy in the packed positions (TreeVoxels packs dy as an unsigned number). */
	static final int DY = 512;

	private final int x;
	private final int y;
	private final int z;
	private final int[] flare;
	private final byte[] flareRadius;
	private final byte[] flareParent;
	private final int[] flareParentIndex;
	private final int[] root;
	private final byte[] rootRadius;
	private final byte[] rootParent;
	private final int[] rootParentIndex;
	private final byte[] rootKind;
	private final int[] dirt;

	RootVoxels(int x, int y, int z, int[] flare, byte[] flareRadius, byte[] flareParent, int[] flareParentIndex, int[] root,
			byte[] rootRadius, byte[] rootParent, int[] rootParentIndex, byte[] rootKind, int[] dirt) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.flare = flare;
		this.flareRadius = flareRadius;
		this.flareParent = flareParent;
		this.flareParentIndex = flareParentIndex;
		this.root = root;
		this.rootRadius = rootRadius;
		this.rootParent = rootParent;
		this.rootParentIndex = rootParentIndex;
		this.rootKind = rootKind;
		this.dirt = dirt;
	}

	/** An empty plan at a trunk base. */
	static RootVoxels empty(int x, int y, int z) {
		return new RootVoxels(x, y, z, new int[0], new byte[0], new byte[0], new int[0], new int[0], new byte[0], new byte[0],
				new int[0], new byte[0], new int[0]);
	}

	static int pack(int dx, int dy, int dz) {
		return TreeVoxels.pack(dx, dy + DY, dz);
	}

	static int unpackX(int p) {
		return TreeVoxels.unpackX(p);
	}

	static int unpackY(int p) {
		return TreeVoxels.unpackY(p) - DY;
	}

	static int unpackZ(int p) {
		return TreeVoxels.unpackZ(p);
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

	public boolean empty() {
		return flare.length == 0 && root.length == 0 && dirt.length == 0;
	}

	public int flareCount() {
		return flare.length;
	}

	public int flareX(int i) {
		return unpackX(flare[i]);
	}

	public int flareY(int i) {
		return unpackY(flare[i]);
	}

	public int flareZ(int i) {
		return unpackZ(flare[i]);
	}

	/** The stage of flare block i (its {@code radius} property; the last block of a flare has stage 1). */
	public int flareRadius(int i) {
		return flareRadius[i];
	}

	/** Direction from flare block i to its parent ({@link TreeVoxels#NORTH} … {@link TreeVoxels#EAST}). */
	public int flareParent(int i) {
		return flareParent[i];
	}

	/** Index of the parent flare block, -1 for the trunk base. */
	public int flareParentIndex(int i) {
		return flareParentIndex[i];
	}

	public int rootCount() {
		return root.length;
	}

	public int rootX(int i) {
		return unpackX(root[i]);
	}

	public int rootY(int i) {
		return unpackY(root[i]);
	}

	public int rootZ(int i) {
		return unpackZ(root[i]);
	}

	public int rootRadius(int i) {
		return rootRadius[i];
	}

	/** Direction from root i to its parent ({@link TreeVoxels#DOWN} … {@link TreeVoxels#EAST}). */
	public int rootParent(int i) {
		return rootParent[i];
	}

	/** Index of the parent root (always lower than i), -1 for the root collar under the trunk base. */
	public int rootParentIndex(int i) {
		return rootParentIndex[i];
	}

	/** What root i is: {@link #ROOT} or {@link #EXPOSED}. */
	public int rootKind(int i) {
		return rootKind[i];
	}

	/** A root in the soil. */
	public static final int ROOT = 0;
	/**
	 * A cell of the root system at a wall of the ground (a side towards a lower column, water or an unknown column): rooted
	 * dirt instead of a root block, so the wall shows no hole with a root in it.
	 */
	public static final int EXPOSED = 1;

	public int dirtCount() {
		return dirt.length;
	}

	public int dirtX(int i) {
		return unpackX(dirt[i]);
	}

	public int dirtY(int i) {
		return unpackY(dirt[i]);
	}

	public int dirtZ(int i) {
		return unpackZ(dirt[i]);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof RootVoxels v && v.x == x && v.y == y && v.z == z && Arrays.equals(v.flare, flare)
				&& Arrays.equals(v.flareRadius, flareRadius) && Arrays.equals(v.flareParent, flareParent)
				&& Arrays.equals(v.flareParentIndex, flareParentIndex) && Arrays.equals(v.root, root)
				&& Arrays.equals(v.rootRadius, rootRadius) && Arrays.equals(v.rootParent, rootParent)
				&& Arrays.equals(v.rootParentIndex, rootParentIndex) && Arrays.equals(v.rootKind, rootKind)
				&& Arrays.equals(v.dirt, dirt);
	}

	@Override
	public int hashCode() {
		return 31 * (31 * Arrays.hashCode(flare) + Arrays.hashCode(root)) + Arrays.hashCode(dirt);
	}

	@Override
	public String toString() {
		return "RootVoxels[" + x + ", " + y + ", " + z + ", flares " + flare.length + ", roots " + root.length + ", dirt "
				+ dirt.length + "]";
	}
}
