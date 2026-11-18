package pl.polishforests.entity.squirrel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import pl.polishforests.block.TrunkBlock;

/**
 * What the red squirrel needs to know of the trees around it (step FA5, docs/04 "Projekt FA5"): which blocks it climbs,
 * the upright trunks near it, the lying logs it runs onto, and whether its hitbox fits a place.
 *
 * <ul>
 * <li><b>Climbable</b>: every block of {@code #minecraft:logs}: the trunks of the mod of radius 1-7 px
 * ({@link TrunkBlock}, also the stripped ones; the root {@code tree_root} is not a log) and the vanilla logs and wood.
 * <li><b>Upright</b>: a trunk of the mod whose parent is below ({@link TrunkBlock#PARENT} down), or a vanilla log on the
 * Y axis. A squirrel climbs a column of upright blocks (a trunk) from its base to where it bends or ends.
 * <li><b>Lying</b>: a log whose parent or axis is horizontal (the dead wood of the forests, a fallen trunk).
 * <li><b>Clear</b> ({@link #clear}): no collision of a block other than leaves in the box (the live collision of the
 * trunks, {@code TrunkShapes}: core, arms, no lying twigs of radius 1, asked with a collision context, since the getter
 * without one returns the state cache's prism, which goes past the drawn tip of a trunk; stage 2 of FA5, FA5-p) and no fluid: the space of the tree mode, where the
 * squirrel moves through the leaves. <b>Free</b> ({@link #free}): no collision at all, leaves included: where the squirrel
 * may end the tree mode and stand with the vanilla physics again.
 * </ul>
 */
public final class TreeSpace {
	/** Fewest upright blocks of a trunk a squirrel climbs (lower: a stump, a sapling). */
	public static final int MIN_TRUNK = 4;
	/** Longest trunk followed up or down (blocks). */
	static final int MAX_TRUNK = 64;

	private TreeSpace() {
	}

	/** Whether the squirrel climbs the block (a log of the mod or of vanilla). */
	public static boolean climbable(BlockState state) {
		return state.is(BlockTags.LOGS);
	}

	/** Whether the block is an upright trunk block (see the class comment). */
	public static boolean upright(BlockState state) {
		if (!climbable(state)) {
			return false;
		}
		if (state.getBlock() instanceof TrunkBlock) {
			return state.getValue(TrunkBlock.PARENT) == Direction.DOWN;
		}
		return !state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
	}

	/** Whether the block is a lying log (a horizontal trunk of the mod or a vanilla log on a horizontal axis). */
	public static boolean lying(BlockState state) {
		if (!climbable(state)) {
			return false;
		}
		if (state.getBlock() instanceof TrunkBlock) {
			return state.getValue(TrunkBlock.PARENT).getAxis().isHorizontal();
		}
		return state.hasProperty(BlockStateProperties.AXIS) && state.getValue(BlockStateProperties.AXIS).isHorizontal();
	}

	/** Distance (blocks) from the axis to the bark of a climbable block: r/16 for a trunk of the mod, 0.5 for vanilla. */
	public static double half(BlockState state) {
		if (state.getBlock() instanceof TrunkBlock) {
			return TreeGeometry.trunkHalf(state.getValue(TrunkBlock.RADIUS));
		}
		return TreeGeometry.VANILLA_LOG_HALF;
	}

	/** The height (blocks above the block's bottom) of the top of a lying log. */
	public static double lyingTop(BlockState state) {
		if (state.getBlock() instanceof TrunkBlock) {
			return 0.5 + TreeGeometry.trunkHalf(state.getValue(TrunkBlock.RADIUS));
		}
		return 1.0;
	}

	/** The squirrel's hitbox with its feet at the point. */
	public static AABB box(Vec3 feet, float width, float height) {
		double h = width / 2.0;
		return new AABB(feet.x - h, feet.y, feet.z - h, feet.x + h, feet.y + height, feet.z + h);
	}

	/** No collision of a block other than leaves in the box and no fluid (the space of the tree mode). */
	public static boolean clear(Level level, AABB box) {
		return fits(level, box, true);
	}

	/** No collision at all (leaves included) and no fluid in the box: where the vanilla physics may take over again. */
	public static boolean free(Level level, AABB box) {
		return fits(level, box, false);
	}

	private static boolean fits(Level level, AABB box, boolean throughLeaves) {
		return collision(level, box, throughLeaves) == null;
	}

	/**
	 * The first block in the box that collides with it (a fluid, an unloaded chunk too), the leaves skipped when
	 * {@code throughLeaves}; null when the box fits. The fauna game test names it for a squirrel stuck in blocks.
	 */
	public static @Nullable BlockPos collision(Level level, AABB box, boolean throughLeaves) {
		int x0 = Mth.floor(box.minX);
		int y0 = Mth.floor(box.minY);
		int z0 = Mth.floor(box.minZ);
		int x1 = Mth.floor(box.maxX - 1.0E-7);
		int y1 = Mth.floor(box.maxY - 1.0E-7);
		int z1 = Mth.floor(box.maxZ - 1.0E-7);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		VoxelShape boxShape = null;
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					p.set(x, y, z);
					if (!level.hasChunkAt(p)) {
						return p.immutable();
					}
					BlockState state = level.getBlockState(p);
					if (!state.getFluidState().isEmpty()) {
						return p.immutable();
					}
					if (state.isAir() || throughLeaves && state.is(BlockTags.LEAVES)) {
						continue;
					}
					// with a context: the block's own shape in the world (the drawn trunk with its arms, TrunkShapes), as the
					// movement of entities sees it; the getter without one returns the state cache's prism of the trunks
					VoxelShape shape = state.getCollisionShape(level, p, CollisionContext.empty());
					if (shape.isEmpty()) {
						continue;
					}
					if (boxShape == null) {
						boxShape = Shapes.create(box);
					}
					if (Shapes.joinIsNotEmpty(shape.move(p), boxShape, BooleanOp.AND)) {
						return p.immutable();
					}
				}
			}
		}
		return null;
	}

	/** Whether there is a leaf block within one block of the point (the squirrel is in a crown). */
	public static boolean inCrown(Level level, Vec3 at) {
		BlockPos c = BlockPos.containing(at.x, at.y + 0.2, at.z);
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-1, -1, -1), c.offset(1, 1, 1))) {
			if (level.getBlockState(p).is(BlockTags.LEAVES)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ trunks

	/**
	 * An upright trunk: the column (x, z), the y of its lowest and its highest upright block. The squirrel climbs it from
	 * {@code baseY} to {@code topY + 1} (the top of the highest block).
	 */
	public record Trunk(int x, int z, int baseY, int topY) {
		public int height() {
			return topY - baseY + 1;
		}

		/** The axis of the trunk at the height of the point. */
		public Vec3 axis(double y) {
			return new Vec3(x + 0.5, y, z + 0.5);
		}
	}

	/** The trunk the upright block at pos belongs to (followed down and up while upright), or null when pos is not upright. */
	public static @Nullable Trunk trunkAt(Level level, BlockPos pos) {
		if (!upright(level.getBlockState(pos))) {
			return null;
		}
		BlockPos.MutableBlockPos p = pos.mutable();
		int base = pos.getY();
		for (int i = 0; i < MAX_TRUNK && upright(level.getBlockState(p.setY(base - 1))); i++) {
			base--;
		}
		int top = pos.getY();
		for (int i = 0; i < MAX_TRUNK && upright(level.getBlockState(p.setY(top + 1))); i++) {
			top++;
		}
		return new Trunk(pos.getX(), pos.getZ(), base, top);
	}

	/** The half width of the trunk at the height y (its block there), or the nearest block's when y is past an end. */
	public static double halfAt(Level level, Trunk trunk, double y) {
		int by = Mth.clamp(Mth.floor(y), trunk.baseY(), trunk.topY());
		BlockState state = level.getBlockState(new BlockPos(trunk.x(), by, trunk.z()));
		return climbable(state) ? half(state) : TreeGeometry.VANILLA_LOG_HALF;
	}

	/** Whether the trunk is still there at the height y (an upright block in its column). */
	public static boolean holds(Level level, Trunk trunk, double y) {
		int by = Mth.floor(y);
		return by >= trunk.baseY() - 1 && by <= trunk.topY() + 1 && upright(level.getBlockState(new BlockPos(trunk.x(), Mth.clamp(by,
				trunk.baseY(), trunk.topY()), trunk.z())));
	}

	/**
	 * The trunks of at least {@value #MIN_TRUNK} blocks whose base is within 2 blocks of the height {@code y} and whose column
	 * is within {@code radius} blocks (Chebyshev) of (x, z), nearest rings first; at most {@code limit} trunks, the scan
	 * stops one ring after the first trunk.
	 */
	public static List<Trunk> trunksNear(Level level, int x, int y, int z, int radius, int limit) {
		List<Trunk> out = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int stopAt = Integer.MAX_VALUE;
		for (int r = 0; r <= radius && r <= stopAt && out.size() < limit; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int cx = x + dx;
					int cz = z + dz;
					for (int dy = -2; dy <= 2; dy++) {
						p.set(cx, y + dy, cz);
						if (!level.hasChunkAt(p) || !upright(level.getBlockState(p))) {
							continue;
						}
						Trunk t = trunkAt(level, p);
						if (t != null && t.height() >= MIN_TRUNK && Math.abs(t.baseY() - y) <= 2 && !out.contains(t)) {
							out.add(t);
							stopAt = Math.min(stopAt, r + 1);
						}
						break;
					}
				}
			}
		}
		return out;
	}

	/**
	 * The trunks a squirrel at the height y on {@code from} may leap to: columns 2-5 blocks away whose trunk has an upright
	 * block within {@link TreeGeometry#MAX_LEAP_DROP} below and {@link TreeGeometry#MAX_LEAP_RISE} above y.
	 */
	public static List<Trunk> leapTargets(Level level, Trunk from, double y, int limit) {
		List<Trunk> out = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int yi = Mth.floor(y);
		for (int dx = -5; dx <= 5 && out.size() < limit; dx++) {
			for (int dz = -5; dz <= 5 && out.size() < limit; dz++) {
				int d = Math.max(Math.abs(dx), Math.abs(dz));
				if (d < 2) {
					continue;
				}
				for (int dy = (int) TreeGeometry.MAX_LEAP_RISE; dy >= -(int) TreeGeometry.MAX_LEAP_DROP; dy--) {
					p.set(from.x() + dx, yi + dy, from.z() + dz);
					if (level.hasChunkAt(p) && upright(level.getBlockState(p))) {
						Trunk t = trunkAt(level, p);
						if (t != null && t.height() >= MIN_TRUNK && !out.contains(t)) {
							out.add(t);
						}
						break;
					}
				}
			}
		}
		return out;
	}

	/**
	 * A lying log within {@code radius} blocks of the feet whose top the squirrel can stand on (free above it), as the point
	 * of its feet there, or null (up to 12 columns drawn).
	 */
	public static @Nullable Vec3 lyingLogNear(Level level, BlockPos feet, int radius, float width, float height, RandomSource random) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 12; i++) {
			int x = feet.getX() + random.nextInt(2 * radius + 1) - radius;
			int z = feet.getZ() + random.nextInt(2 * radius + 1) - radius;
			for (int dy = 1; dy >= -2; dy--) {
				p.set(x, feet.getY() + dy, z);
				BlockState state = level.getBlockState(p);
				if (!lying(state)) {
					continue;
				}
				Vec3 top = new Vec3(x + 0.5, p.getY() + lyingTop(state) + 0.01, z + 0.5);
				if (free(level, box(top, width, height))) {
					return top;
				}
			}
		}
		return null;
	}
}
