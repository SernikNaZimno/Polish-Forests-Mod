package pl.polishforests.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A root flare (step I1 of docs/04-pionowy-wycinek.md, roots): the buttress of an old tree running from the trunk base
 * over the ground, a thin trunk ({@link TrunkBlock}: radius 1-7 px, the parent towards the trunk, waterlogged) with the
 * bark of its species, whose horizontal segment lies on the ground instead of in the middle of the block: the prism of
 * the radius is lowered so that its bottom is {@link #sink} px below the block's floor, partly buried, and its top
 * {@code 2r - sink} px above it (a thin trunk block lying on its side hovers 8 - r px over the ground, decision V11-i).
 * Towards the trunk the reach of the model runs at the same height into the trunk base, so a flare joins the trunk at the
 * ground; outwards the radius falls, so the root tapers into the ground. A vertical segment (parent up or down, only when
 * a player places one so) is a thin trunk in the middle of the block.
 *
 * <p>A root flare belongs to its tree (default choice I1): it is in the species' log tags, so axes, felling mods and the
 * leaves treat it as part of the trunk; the underground roots are the block {@code tree_root}, not a log.
 */
public final class RootFlareBlock extends TrunkBlock {
	/** Shapes of the horizontal parents by {@link Direction#get3DDataValue()} - 2 and radius - 1. */
	private static final VoxelShape[][] LYING = new VoxelShape[4][MAX_RADIUS];

	static {
		for (int r = MIN_RADIUS; r <= MAX_RADIUS; r++) {
			double top = 2 * r - sink(r);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				VoxelShape s = d.getAxis() == Direction.Axis.Z ? Block.box(8 - r, 0, 0, 8 + r, top, 16)
						: Block.box(0, 0, 8 - r, 16, top, 8 + r);
				LYING[d.get3DDataValue() - 2][r - 1] = s;
			}
		}
	}

	public RootFlareBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	/**
	 * How deep (px) the lying prism of radius r reaches below the floor of its block: half the radius, at least 1, so a
	 * thick flare stands out of the ground and a thin end is mostly buried (r 1: 1 px of it shows, r 7: 11 px).
	 */
	public static int sink(int radius) {
		return Math.max(1, radius / 2);
	}

	/** The shape of a flare: the lowered prism for a horizontal parent (clipped at the floor), else a thin trunk's. */
	public static VoxelShape flareShape(Direction parent, int radius) {
		return parent.getAxis() == Direction.Axis.Y ? shape(parent, radius) : LYING[parent.get3DDataValue() - 2][radius - 1];
	}

	@Override
	protected VoxelShape shapeOf(Direction parent, int radius) {
		return flareShape(parent, radius);
	}

	/** A flare placed by a player against the ground lies along the ground with a middle radius. */
	@Override
	protected int defaultRadius() {
		return 3;
	}
}
