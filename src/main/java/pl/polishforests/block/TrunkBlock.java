package pl.polishforests.block;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A trunk or branch of variable thickness (variant D of docs/research/wycinek/research-technika-drzew.md §2.3, decisions
 * D8, T-D1, T-D2; step V2 of docs/04-pionowy-wycinek.md): {@link #RADIUS} 1-7 px (diameter 2-14 px, 0.125-0.875 m),
 * {@link #PARENT} the neighbor towards the root, which is at least as thick (pipe model of the tree planner), and
 * {@link #WATERLOGGED}: 7 × 6 × 2 = 84 states per block.
 *
 * <p>The block draws one arm of its radius from its center to the center of the parent: half of it lies in the own
 * block, half reaches 8 px into the parent, where the thicker parent hides it except between the surface of the parent
 * and the face of the block, which is exactly where a gap would be without the reach. Children come from any side on
 * their own, so a block knows neither its children nor their radii and the models are static JSON
 * ({@code template_trunk_r1…r7}, made by the datagen). The core of the block (a cube of the radius around the center)
 * fills the elbow when the trunk turns. The reach outside the block is visual only. Until step P1 the shape for
 * collision, the outline and ray casts was a prism of the radius through the whole block along the parent axis
 * ({@link #shape}), so a tip or an elbow was 8 − r px longer than drawn. Since P1 the outline and the collision in a
 * world follow the drawing: the core, the own half arm and the half arms of the children that are there
 * ({@link TrunkShapes}, read from the neighbors); the state cache of vanilla (light, occlusion, face support, the solidity
 * of the heightmaps) and world generation keep the prism, so they are unchanged.
 *
 * <p>The shape is not a full block, so the trunk lets the sky light through (no shade, light dampening 0, 1 when
 * waterlogged) and does not hide the faces of its neighbors. It is in {@code #minecraft:logs} through the species tags,
 * so it keeps leaves from decaying and counts as a log for heightmaps and movement.
 *
 * <p>Step I1 (roots): the same block class makes the underground root {@code polishforests:tree_root} (own root bark, not
 * a log), and the root flares ({@link RootFlareBlock}) override the shape of a horizontal segment.
 */
public class TrunkBlock extends Block implements SimpleWaterloggedBlock {
	public static final int MIN_RADIUS = 1;
	public static final int MAX_RADIUS = 7;
	/** Radius of a trunk placed by a player against a block that is not a trunk, px. */
	public static final int DEFAULT_RADIUS = 4;
	public static final IntegerProperty RADIUS = IntegerProperty.create("radius", MIN_RADIUS, MAX_RADIUS);
	public static final EnumProperty<Direction> PARENT = EnumProperty.create("parent", Direction.class);
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	/** Shapes by {@link Direction#get3DDataValue()} of the parent and radius - 1, computed once (a 6 × 7 table). */
	private static final VoxelShape[][] SHAPES = new VoxelShape[6][MAX_RADIUS];

	static {
		for (int r = MIN_RADIUS; r <= MAX_RADIUS; r++) {
			// A prism through the whole block along z, rotated to the six parents (north and south give the same box).
			VoxelShape north = Block.boxZ(2 * r, 0, 16);
			Map<Direction, VoxelShape> rotated = Shapes.rotateAll(north);
			for (Direction d : Direction.values()) {
				SHAPES[d.get3DDataValue()][r - 1] = rotated.get(d);
			}
		}
	}

	public TrunkBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(RADIUS, DEFAULT_RADIUS).setValue(PARENT, Direction.DOWN)
				.setValue(WATERLOGGED, false));
	}

	/** The shape of a trunk of the radius with its parent on the given side: a prism through the block along that axis. */
	public static VoxelShape shape(Direction parent, int radius) {
		return SHAPES[parent.get3DDataValue()][radius - 1];
	}

	/** The state of this trunk with the radius (clamped to 1-7) and the parent, not waterlogged. */
	public BlockState state(int radius, Direction parent) {
		return defaultBlockState().setValue(RADIUS, Math.clamp(radius, MIN_RADIUS, MAX_RADIUS)).setValue(PARENT, parent);
	}

	/**
	 * The outline (crosshair, ray casts): in a world the drawn trunk with its children (step P1, {@link TrunkShapes}),
	 * elsewhere (the state cache with an empty block getter, world generation in chunk regions) the prism as before P1.
	 */
	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (level instanceof Level && liveShape()) {
			return TrunkShapes.outline(state, level, pos);
		}
		return shapeOf(state.getValue(PARENT), state.getValue(RADIUS));
	}

	/** Collision with entities: in a world the drawn trunk without the lying twigs of radius 1 (P1), elsewhere the prism. */
	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (level instanceof Level && liveShape()) {
			return TrunkShapes.collision(state, level, pos);
		}
		return shapeOf(state.getValue(PARENT), state.getValue(RADIUS));
	}

	/** Support of torches, snow and the like: the prism as before P1, wherever it is asked (the face support of the cache). */
	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
		return shapeOf(state.getValue(PARENT), state.getValue(RADIUS));
	}

	/** Whether the shape in a world follows the drawing with the children (P1); the root flares keep their own shapes. */
	protected boolean liveShape() {
		return true;
	}

	/**
	 * Whether this trunk, as a child, draws its half arm of its radius into its parent (P1: part of the parent's outline);
	 * a lying root flare draws its fin instead.
	 */
	protected boolean drawsArm(BlockState state) {
		return true;
	}

	/** The shape of a state of this block (step I1: a root flare lowers its horizontal segments). */
	protected VoxelShape shapeOf(Direction parent, int radius) {
		return shape(parent, radius);
	}

	/**
	 * Placed by a player: the parent is the block that was clicked; against a trunk the new block takes its radius
	 * (the trunk goes on), otherwise {@link #DEFAULT_RADIUS}.
	 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction parent = context.getClickedFace().getOpposite();
		BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(parent));
		int radius = against.getBlock() instanceof TrunkBlock ? against.getValue(RADIUS) : defaultRadius();
		boolean water = context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER);
		return state(radius, parent).setValue(WATERLOGGED, water);
	}

	/** Radius of this block placed by a player against a block that is not a trunk. */
	protected int defaultRadius() {
		return DEFAULT_RADIUS;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	/** Structures and templates rotate the direction to the parent with the block. */
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(PARENT, rotation.rotate(state.getValue(PARENT)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(PARENT, mirror.mirror(state.getValue(PARENT)));
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(RADIUS, PARENT, WATERLOGGED);
	}
}
