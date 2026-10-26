package pl.polishforests.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A root flare (step I1 of docs/04-pionowy-wycinek.md, roots; review of I1, round 1): the buttress of an old tree running
 * from the trunk base over the ground, with the bark of its species. A flare with a horizontal parent (towards the trunk)
 * is a <b>fin</b>: narrow and high, highest at the trunk and sloping down along the block, a wider low foot under it, so
 * the flare climbs the trunk and runs out into the ground instead of lying on it as a log. The block keeps the
 * properties of a thin trunk ({@link TrunkBlock}: {@code radius} 1-7, {@code parent}, waterlogged; registry snapshot
 * PW-11); for a lying flare {@code radius} is the <b>stage</b> of the fin's profile ({@link FlareProfile}): a block of
 * stage s runs from the height {@link FlareProfile#height}(s) at its parent's side down to the height of s - 1 at the far
 * side, so a flare of n blocks has the stages n, n - 1, …, 1 from the trunk outwards, its top falls without a step from
 * block to block and its last block ends in the ground (height 0). The spruce's flares are {@link #flat} surface roots: low and as wide as high.
 * A vertical segment (parent up or down, only when a player places one so) is a thin trunk in the middle of the block.
 *
 * <p>A root flare belongs to its tree (default choice I1): it is in the species' log tags, so axes, felling mods and the
 * leaves treat it as part of the trunk; the underground roots are the block {@code tree_root}, not a log.
 */
public final class RootFlareBlock extends TrunkBlock {
	private final boolean flat;
	/** Shapes of the horizontal parents by {@link Direction#get3DDataValue()} - 2 and stage - 1. */
	private final VoxelShape[][] lying = new VoxelShape[4][MAX_RADIUS];

	public RootFlareBlock(BlockBehaviour.Properties properties, boolean flat) {
		super(properties);
		this.flat = flat;
		for (int s = MIN_RADIUS; s <= MAX_RADIUS; s++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				lying[d.get3DDataValue() - 2][s - 1] = lyingShape(flat, d, s);
			}
		}
	}

	/** Whether the flares of this block are the flat surface roots of the spruce. */
	public boolean flat() {
		return flat;
	}

	/**
	 * The outline of a lying flare with the parent on side d: the foot over the whole block and the fin in two halves
	 * (their heights at the parent's end of each half), in the frame of a parent to the north rotated to d.
	 */
	static VoxelShape lyingShape(boolean flat, Direction d, int stage) {
		double fin = FlareProfile.finWidth(flat, stage);
		double foot = FlareProfile.footWidth(flat, stage);
		double high = FlareProfile.height(flat, stage);
		VoxelShape s = box(d, 8 - foot / 2, 0, 8 + foot / 2, Math.max(1, (flat ? 1 : FlareProfile.FOOT_SHARE) * high), 0, 16);
		if (!flat) {
			s = Shapes.or(s, box(d, 8 - fin / 2, 0, 8 + fin / 2, high, 0, 8),
					box(d, 8 - fin / 2, 0, 8 + fin / 2, Math.max(1, FlareProfile.heightAt(false, stage, 8)), 8, 16));
		}
		return s;
	}

	/** A box (px) given in the frame of a parent to the north (z 0 = the parent's side), turned to the parent d. */
	private static VoxelShape box(Direction d, double x0, double y0, double x1, double y1, double z0, double z1) {
		return switch (d) {
			case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
			case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
			case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
			default -> Block.box(x0, y0, z0, x1, y1, z1);
		};
	}

	/** The shape of a flare: the fin for a horizontal parent, else a thin trunk's prism. */
	public VoxelShape flareShape(Direction parent, int stage) {
		return parent.getAxis() == Direction.Axis.Y ? shape(parent, stage) : lying[parent.get3DDataValue() - 2][stage - 1];
	}

	@Override
	protected VoxelShape shapeOf(Direction parent, int radius) {
		return flareShape(parent, radius);
	}

	/**
	 * A flare placed by a player against the side of a block lies with stage 2 (a fin from 10 px down to 5 px); against
	 * the top of the ground (parent down) it stands as a thin trunk.
	 */
	@Override
	protected int defaultRadius() {
		return 2;
	}
}
