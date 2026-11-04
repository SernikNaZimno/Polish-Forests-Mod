package pl.polishforests.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The outline and the collision of a thin trunk as it is drawn (step P1 of docs/04-pionowy-wycinek.md, hitboxes): the
 * model of a {@link TrunkBlock} of radius r draws, inside its own block, the core (a cube of r around the center) and the
 * half arm towards its parent, and each child (a face neighbor that is a trunk whose parent is this block) draws its own
 * arm of its radius from its center to the center of this block, so half of it lies here. The shape of a block in a world
 * is the union of exactly these parts: a tip ends r px past the center, an elbow has no stub beyond its bend, a fork shows
 * the thinner arms of its children (until P1 every block had a prism through the whole block along its parent axis, 8 − r
 * px longer than drawn at tips and elbows, and the side arms of a fork could not be targeted).
 *
 * <p>The block state does not hold the children (no new properties of the frozen trunk blocks, PW-11), so the shape reads
 * the six neighbors. It does so only in a {@link Level} (the server's and the client's worlds: the crosshair, entity
 * movement): the state cache of vanilla computes the occlusion, the light, the face support, the solidity of the heightmaps
 * and the full-block flags once per state with an empty block getter, and world generation runs in regions of chunks;
 * those keep the prism of {@link TrunkBlock#shape} exactly as before (default choice P1-g), so light, heightmaps and the
 * generated world do not change. A neighbor in a chunk that is not loaded counts as no child.
 *
 * <p>Collision: as the outline, except the twigs: a part of radius 1 that is not upright (a horizontal arm of 2 × 2 px
 * among the leaves) does not collide, as vanilla's thin plants, so a player is not stopped by it; upright stems of radius
 * 1 (the pole stage, a stem of a shrub) collide (default choice P1-h). One can stand on every branch of radius 2 and more.
 *
 * <p>Cost: the shape is a pure function of a 24-bit key (radius, parent, the radius of the child on each side or 0); the
 * shapes of a key are built once and kept in a direct-mapped table of {@value #CACHE_SIZE} entries, so a lookup reads six
 * neighbor states and allocates nothing (a miss builds the union of at most seven boxes once).
 */
public final class TrunkShapes {
	/** Entries of the shape table (a power of 2). */
	static final int CACHE_SIZE = 1 << 13;
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final Entry[] CACHE = new Entry[CACHE_SIZE];
	private static final ThreadLocal<BlockPos.MutableBlockPos> SCRATCH = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

	private record Entry(int key, VoxelShape outline, VoxelShape collision) {
	}

	private TrunkShapes() {
	}

	/** The outline of the trunk {@code state} at {@code pos} with its children in {@code level}. */
	public static VoxelShape outline(BlockState state, BlockGetter level, BlockPos pos) {
		return entry(key(state, level, pos)).outline;
	}

	/** The collision shape of the trunk {@code state} at {@code pos} with its children in {@code level}. */
	public static VoxelShape collision(BlockState state, BlockGetter level, BlockPos pos) {
		return entry(key(state, level, pos)).collision;
	}

	/** The outline of a key ({@link #key(int, Direction, int[])}). */
	static VoxelShape outline(int key) {
		return entry(key).outline;
	}

	/** The collision shape of a key. */
	static VoxelShape collision(int key) {
		return entry(key).collision;
	}

	/**
	 * The key of a trunk: radius - 1 (bits 0-2), the parent's {@link Direction#get3DDataValue()} (bits 3-5) and for each side
	 * d the radius of the child on that side or 0 (3 bits from bit 6 + 3 · d).
	 */
	static int key(BlockState state, BlockGetter level, BlockPos pos) {
		Direction parent = state.getValue(TrunkBlock.PARENT);
		int key = state.getValue(TrunkBlock.RADIUS) - 1 | parent.get3DDataValue() << 3;
		BlockPos.MutableBlockPos n = SCRATCH.get();
		int cx = pos.getX() >> 4;
		int cz = pos.getZ() >> 4;
		Level world = level instanceof Level l ? l : null;
		for (Direction d : DIRECTIONS) {
			if (d == parent) {
				continue;
			}
			n.setWithOffset(pos, d);
			if (world != null && ((n.getX() >> 4) != cx || (n.getZ() >> 4) != cz) && !world.hasChunkAt(n)) {
				continue;
			}
			BlockState s = level.getBlockState(n);
			if (s.getBlock() instanceof TrunkBlock t && s.getValue(TrunkBlock.PARENT) == d.getOpposite() && t.drawsArm(s)) {
				key |= s.getValue(TrunkBlock.RADIUS) << 6 + 3 * d.get3DDataValue();
			}
		}
		return key;
	}

	/** The key of a trunk of the radius with the parent and the radius of the child on each side (by 3D data value, 0 = none). */
	static int key(int radius, Direction parent, int[] children) {
		int key = radius - 1 | parent.get3DDataValue() << 3;
		for (Direction d : DIRECTIONS) {
			if (d != parent) {
				key |= Math.clamp(children[d.get3DDataValue()], 0, TrunkBlock.MAX_RADIUS) << 6 + 3 * d.get3DDataValue();
			}
		}
		return key;
	}

	private static Entry entry(int key) {
		int i = key * 0x9E3779B9 >>> 32 - Integer.numberOfTrailingZeros(CACHE_SIZE);
		Entry e = CACHE[i];
		if (e != null && e.key == key) {
			return e;
		}
		// A miss: build the shapes of the key (pure function, so a racing thread builds the same) and keep them.
		e = build(key);
		CACHE[i] = e;
		return e;
	}

	private static Entry build(int key) {
		int radius = (key & 7) + 1;
		Direction parent = Direction.from3DDataValue(key >> 3 & 7);
		List<VoxelShape> outline = new ArrayList<>();
		List<VoxelShape> collision = new ArrayList<>();
		VoxelShape own = part(parent, radius, radius);
		outline.add(own);
		if (collides(parent, radius)) {
			collision.add(own);
		}
		for (Direction d : DIRECTIONS) {
			int r = key >> 6 + 3 * d.get3DDataValue() & 7;
			if (r == 0) {
				continue;
			}
			VoxelShape arm = part(d, r, 0);
			outline.add(arm);
			if (collides(d, r)) {
				collision.add(arm);
			}
		}
		return new Entry(key, union(outline), union(collision));
	}

	/** Whether a part of the radius along the axis of d collides: all but the twigs of radius 1 that are not upright. */
	static boolean collides(Direction d, int radius) {
		return radius > TrunkBlock.MIN_RADIUS || d.getAxis() == Direction.Axis.Y;
	}

	/**
	 * A box of the radius around the axis of d from the face of the block on side d to {@code past} px beyond the center (0 =
	 * a child's half arm, r = the own half arm with the core).
	 */
	static VoxelShape part(Direction d, int radius, int past) {
		double[] lo = {8 - radius, 8 - radius, 8 - radius};
		double[] hi = {8 + radius, 8 + radius, 8 + radius};
		int a = d.getAxis().ordinal();
		if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
			lo[a] = 8 - past;
			hi[a] = 16;
		} else {
			lo[a] = 0;
			hi[a] = 8 + past;
		}
		return Block.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
	}

	private static VoxelShape union(List<VoxelShape> parts) {
		if (parts.isEmpty()) {
			return Shapes.empty();
		}
		VoxelShape s = parts.getFirst();
		for (int k = 1; k < parts.size(); k++) {
			s = Shapes.joinUnoptimized(s, parts.get(k), net.minecraft.world.phys.shapes.BooleanOp.OR);
		}
		return s.optimize();
	}
}
