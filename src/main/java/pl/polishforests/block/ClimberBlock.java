package pl.polishforests.block;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import pl.polishforests.season.GroundPhases;
import pl.polishforests.season.SeasonPhase;

/**
 * A climber on the side of a trunk, a log or leaves (step F5 of docs/04-pionowy-wycinek.md, {@link GroundPlant.Kind#CLIMBER}:
 * the hop of the floodplain forests and the osier scrub): a plane on one face of the block like vanilla's vine,
 * {@link #FACING} towards its support, no collision, climbable (tag {@code minecraft:climbable}). It holds on a thick trunk
 * ({@link TrunkBlock} of radius at least {@value #MIN_TRUNK_RADIUS} px, whose bark lies within a few pixels of the plane), a
 * log or leaves, and drops when the support goes. The season is drawn by the client as for the herbs (PW-8 (a):
 * {@link GroundPhases#look} — with its cones, leaves only or nothing); while it has died back (from November to April) it
 * has no outline, does not hold a climbing entity (round 1 of the F5 review, {@code LivingEntityClimbableMixin}) and a
 * block placed there replaces it (rule R12).
 *
 * <p>World generation writes it only on air (the trees and the plants take its place in either order: it is in the tags
 * {@code minecraft:replaceable_by_trees} and {@code minecraft:replaceable}, and the column layers treat it as open), so
 * the climber never takes the place of a leaf or a plant (rule of step L1).
 */
public class ClimberBlock extends Block {
	/** The direction of the support (the climber lies on the face of its block towards it). */
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** Least radius of a thin trunk that holds a climber (the bark within 3 px of the plane). */
	public static final int MIN_TRUNK_RADIUS = 5;
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Block.boxZ(16.0, 0.0, 1.0));

	private final GroundPlant plant;

	public ClimberBlock(GroundPlant plant, BlockBehaviour.Properties properties) {
		super(properties);
		this.plant = plant;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public GroundPlant plant() {
		return plant;
	}

	/** Whether a block holds a climber: a thick thin trunk, a log or leaves. */
	public static boolean support(BlockState state) {
		if (state.getBlock() instanceof TrunkBlock) {
			return !state.hasProperty(TrunkBlock.RADIUS) || state.getValue(TrunkBlock.RADIUS) >= MIN_TRUNK_RADIUS;
		}
		return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
	}

	/** Whether the climber has died back now (false where the getter is not a level: generation, meshing). */
	public boolean withdrawn(BlockGetter level, BlockPos pos) {
		return level instanceof Level l && GroundPhases.withdrawn(plant, SeasonPhase.step(l), pos.getX(), pos.getZ());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return withdrawn(level, pos) ? Shapes.empty() : SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return support(level.getBlockState(pos.relative(state.getValue(FACING))));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (directionToNeighbour == state.getValue(FACING) && !support(neighbourState)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return withdrawn(context.getLevel(), context.getClickedPos()) || super.canBeReplaced(state, context);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		for (Direction d : context.getNearestLookingDirections()) {
			if (d.getAxis().isHorizontal()) {
				BlockState s = defaultBlockState().setValue(FACING, d);
				if (s.canSurvive(context.getLevel(), context.getClickedPos())) {
					return s;
				}
			}
		}
		return null;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
