package pl.polishforests.worldgen.tree;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.SplittableRandom;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * The creeping shrub of the tree planner (step F6 of docs/04-pionowy-wycinek.md, "Projekt F6"): the dwarf mountain pine
 * (Pinus mugo, research/06 §3.4, §9: 1-3 m, stems several meters long creeping from the base and bent upward at their
 * ends, dense tufts of needles on the raised shoots, clumps of 3-10 m that join into a closed scrub). A pure function of
 * the request (rule ZW-1), as {@link TreeShapePlan}, and its output is a {@link TreeVoxels}, so the feature writes it with
 * the writer of the planner's trees ({@code ProceduralTreeFeature#writeCounted}).
 *
 * <p><b>Skeleton.</b> Three to six stems leave the base (the root block, an upright stump of {@value #BASE_RADIUS} px) at
 * evenly spread azimuths with a jitter, each 3-6 blocks long (by the size factor); a stem lies on the ground: it follows
 * the ground surface of the request ({@link Ground}) one block up a step, hangs over a step down of one block and goes
 * down a higher step along its face (never below the base: the planner's offsets are unsigned in height); it stops at
 * a wall, at an unknown column and where it would cross another stem. Its last one or two blocks rise (the bent-up end),
 * and about half of the stems carry a side shoot from their middle that rises as well. Stems are {@value #BASE_RADIUS}
 * px thick over their first half, 1 px beyond.
 *
 * <p><b>Needles.</b> The raised ends carry tufts (the 3 × 3 around the tip at its height and one above, the top block
 * above the tip), and the outer part of every stem a blanket of needles on top of it and beside it (with holes), so a
 * shrub reads as a low, dense, dark green cushion with the stems showing at its base; a leaf never goes below the ground
 * surface of its column. The leaf distances are the search of {@link TreeVoxels#leafDistances(boolean[])}.
 */
public final class CreepingShrubPlan {
	/** Salt of the shrub's draws (rule ZW-2: a new field, a new salt). */
	public static final String SALT = "tree.creeping_shrub";
	/** Radius of the base and of the first half of the stems, px. */
	static final int BASE_RADIUS = 2;
	/** Least and largest number of stems of a shrub of the size factor 1. */
	static final int MIN_STEMS = 3;
	static final int MAX_STEMS = 6;
	/** Least and largest length of a stem of the size factor 1 (blocks, before its raised end). */
	static final int MIN_LENGTH = 3;
	static final int MAX_LENGTH = 6;
	/** Share of the needle blanket's cells beside a stem that hold a leaf. */
	static final double FILL = 0.7;
	/** Marker of a column whose ground is not known or is blocked. */
	public static final int UNKNOWN = Integer.MIN_VALUE;

	/** The ground of the shrub's columns. */
	@FunctionalInterface
	public interface Ground {
		/**
		 * The height of the first free block above the ground at the column (dx, dz) from the base, relative to the base
		 * (0 = level with the base), or {@link #UNKNOWN}.
		 */
		int free(int dx, int dz);

		/** Flat ground: every column level with the base. */
		Ground FLAT = (dx, dz) -> 0;
	}

	/**
	 * A shrub.
	 *
	 * @param seed   the world seed
	 * @param x      trunk base (the block above the ground), world coordinates
	 * @param size   factor of the stem count and length (1: the shrub of the scrub; the saplings' sizes below and above)
	 * @param ground the ground around the base
	 */
	public record Request(long seed, int x, int y, int z, float size, Ground ground) {
	}

	private CreepingShrubPlan() {
	}

	/** The shrub of a request. */
	public static TreeVoxels plan(Request r) {
		return new Builder(r).build();
	}

	private static final class Builder {
		private final Request r;
		private final SplittableRandom random;
		private final IntArrayList wood = new IntArrayList();
		private final IntArrayList radius = new IntArrayList();
		private final IntArrayList parent = new IntArrayList();
		private final IntArrayList parentIndex = new IntArrayList();
		/** Wood cells: packed position to index. */
		private final Int2IntOpenHashMap woodAt = new Int2IntOpenHashMap();
		/** Leaf cells in the order added (packed), with their set. */
		private final IntArrayList leaves = new IntArrayList();
		private final it.unimi.dsi.fastutil.ints.IntOpenHashSet leafSet = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
		private int top;

		Builder(Request r) {
			this.r = r;
			this.random = new SplittableRandom(new Noise(r.seed()).derive(SALT).hash(r.x(), r.z()));
			woodAt.defaultReturnValue(-1);
		}

		TreeVoxels build() {
			add(0, 0, 0, BASE_RADIUS, TreeVoxels.DOWN, -1);
			float size = Math.max(0.3F, r.size());
			int stems = Math.max(2, (int) Math.round((MIN_STEMS + random.nextInt(MAX_STEMS - MIN_STEMS + 1)) * Math.min(1, size)));
			double start = random.nextDouble() * 2 * Math.PI;
			for (int s = 0; s < stems; s++) {
				double azimuth = start + s * 2 * Math.PI / stems + (random.nextDouble() - 0.5) * 0.9 * Math.PI / stems;
				int length = Math.max(1, (int) Math.round((MIN_LENGTH + random.nextInt(MAX_LENGTH - MIN_LENGTH + 1)) * size));
				int rise = 1 + random.nextInt(2);
				boolean side = random.nextDouble() < 0.5;
				double sideTurn = random.nextBoolean() ? 0.9 : -0.9;
				int end = stem(0, azimuth, length, rise);
				if (side && end >= 0) {
					int mid = middle(end, length / 2);
					if (mid > 0) {
						stem(mid, azimuth + sideTurn, Math.max(1, length / 2), 1);
					}
				}
			}
			// The tuft on the base: a few needles beside the root, so a small shrub is not a bare stump.
			needles(0, 0, 0, 0.5);
			return voxels();
		}

		/** Walks back from {@code index} along its parents by {@code steps} wood blocks (the middle of a stem). */
		private int middle(int index, int steps) {
			int i = index;
			for (int k = 0; k < steps && i > 0; k++) {
				i = parentIndex.getInt(i);
			}
			return i;
		}

		/**
		 * Lays a stem from the wood block {@code from} along an azimuth: {@code length} blocks on the ground, then
		 * {@code rise} blocks up. Returns the index of its last block, or -1 when it could not leave its start.
		 */
		private int stem(int from, double azimuth, int length, int rise) {
			double ax = Math.cos(azimuth);
			double az = Math.sin(azimuth);
			int cur = from;
			int cx = TreeVoxels.unpackX(wood.getInt(from));
			int cy = TreeVoxels.unpackY(wood.getInt(from));
			int cz = TreeVoxels.unpackZ(wood.getInt(from));
			int sx = cx;
			int sz = cz;
			int stepX = ax >= 0 ? 1 : -1;
			int stepZ = az >= 0 ? 1 : -1;
			int laid = 0;
			int last = -1;
			int half = Math.max(1, length / 2);
			while (laid < length) {
				// The next column: the forward face neighbor whose center lies closer to the line of the azimuth.
				double offX = Math.abs((cx + stepX - sx) * az - (cz - sz) * ax);
				double offZ = Math.abs((cx - sx) * az - (cz + stepZ - sz) * ax);
				int tx = offX <= offZ ? cx + stepX : cx;
				int tz = offX <= offZ ? cz : cz + stepZ;
				if (Math.abs(tx) > 60 || Math.abs(tz) > 60) {
					break;
				}
				int g = r.ground().free(tx, tz);
				if (g == UNKNOWN || g > cy + 1) {
					break;
				}
				int rad = laid < half ? BASE_RADIUS : 1;
				if (g == cy + 1) {
					// A step up: rise in the current column first.
					cur = add(cx, cy + 1, cz, Math.min(rad, radius.getInt(cur)), TreeVoxels.DOWN, cur);
					if (cur < 0) {
						break;
					}
					cy++;
				}
				int dir = tx > cx ? TreeVoxels.WEST : tx < cx ? TreeVoxels.EAST : tz > cz ? TreeVoxels.NORTH : TreeVoxels.SOUTH;
				int next = add(tx, cy, tz, Math.min(rad, radius.getInt(cur)), dir, cur);
				if (next < 0) {
					break;
				}
				cur = next;
				cx = tx;
				cz = tz;
				// A step down of more than one block: down the face (the stem hangs over a step of one block).
				while (g < cy - 1 && cy > 0) {
					int down = add(cx, cy - 1, cz, 1, TreeVoxels.UP, cur);
					if (down < 0) {
						break;
					}
					cur = down;
					cy--;
				}
				laid++;
				last = cur;
				if (laid > length / 3) {
					needles(cx, cy, cz, FILL);
				}
			}
			if (last < 0) {
				return -1;
			}
			// The raised end with its tuft.
			for (int k = 0; k < rise; k++) {
				int up = add(cx, cy + 1, cz, 1, TreeVoxels.DOWN, cur);
				if (up < 0) {
					break;
				}
				cur = up;
				cy++;
				needles(cx, cy, cz, 0.85);
			}
			tuft(cx, cy, cz);
			return cur;
		}

		/** The needle blanket at a stem block: on top of it, and beside it with holes. */
		private void needles(int x, int y, int z, double fill) {
			leaf(x, y + 1, z, 1);
			leaf(x + 1, y, z, fill);
			leaf(x - 1, y, z, fill);
			leaf(x, y, z + 1, fill);
			leaf(x, y, z - 1, fill);
			leaf(x + 1, y + 1, z, fill * 0.6);
			leaf(x - 1, y + 1, z, fill * 0.6);
			leaf(x, y + 1, z + 1, fill * 0.6);
			leaf(x, y + 1, z - 1, fill * 0.6);
		}

		/** The tuft of a raised end: the 3 × 3 at the tip's height and above it, the block on top. */
		private void tuft(int x, int y, int z) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					double corner = dx != 0 && dz != 0 ? 0.55 : 0.95;
					leaf(x + dx, y, z + dz, corner);
					leaf(x + dx, y + 1, z + dz, corner * 0.8);
				}
			}
			leaf(x, y + 2, z, 0.7);
		}

		private void leaf(int x, int y, int z, double chance) {
			// One draw per call, so the shrub's draws do not depend on the ground (the same order of draws everywhere).
			double u = random.nextDouble();
			if (u >= chance || y < 0 || Math.abs(x) > 63 || Math.abs(z) > 63) {
				return;
			}
			int g = r.ground().free(x, z);
			if (g == UNKNOWN || y < g) {
				return;
			}
			int p = TreeVoxels.pack(x, y, z);
			if (woodAt.containsKey(p) || !leafSet.add(p)) {
				return;
			}
			leaves.add(p);
			top = Math.max(top, y + 1);
		}

		/** Adds a wood block (the radius at most its parent's); -1 when the cell is wood already (two stems crossing). */
		private int add(int x, int y, int z, int rad, int dir, int from) {
			int p = TreeVoxels.pack(x, y, z);
			if (woodAt.containsKey(p)) {
				return -1;
			}
			if (leafSet.remove(p)) {
				leaves.rem(p);
			}
			int i = wood.size();
			wood.add(p);
			radius.add(rad);
			parent.add(dir);
			parentIndex.add(from);
			woodAt.put(p, i);
			top = Math.max(top, y + 1);
			return i;
		}

		private TreeVoxels voxels() {
			int n = wood.size();
			byte[] rad = new byte[n];
			byte[] par = new byte[n];
			byte[] bark = new byte[n];
			for (int i = 0; i < n; i++) {
				rad[i] = (byte) radius.getInt(i);
				par[i] = (byte) parent.getInt(i);
			}
			int[] w = wood.toIntArray();
			int[] pi = parentIndex.toIntArray();
			int[] l = leaves.toIntArray();
			TreeVoxels raw = new TreeVoxels(r.x(), r.y(), r.z(), w, rad, par, pi, bark, l, new byte[l.length], top);
			boolean[] all = new boolean[n];
			java.util.Arrays.fill(all, true);
			byte[] d = raw.leafDistances(all);
			int kept = 0;
			for (byte b : d) {
				if (b > 0) {
					kept++;
				}
			}
			int[] keptLeaves = new int[kept];
			byte[] keptDistance = new byte[kept];
			int k = 0;
			for (int i = 0; i < l.length; i++) {
				if (d[i] > 0) {
					keptLeaves[k] = l[i];
					keptDistance[k++] = d[i];
				}
			}
			return new TreeVoxels(r.x(), r.y(), r.z(), w, rad, par, pi, bark, keptLeaves, keptDistance, top);
		}
	}
}
