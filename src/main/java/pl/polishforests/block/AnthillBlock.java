package pl.polishforests.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import pl.polishforests.season.SeasonPhase;
import pl.polishforests.season.SubSeason;

/**
 * The mound of the red wood ant (Formica rufa, step FA8 of docs/04-pionowy-wycinek.md, option O4 of CP-D.2): a block of
 * pine needles and twigs in eight heights of 2 px ({@link #LAYERS}, 8 = a full block), so the generation builds a dome of
 * 1–2 blocks height and 2–3 blocks width from columns of stacked layers ({@code AnthillPlan}); a full block carries another
 * mound block. Dug with a shovel; a player places one layer per item, and another item on top adds a layer, as snow. It
 * does not melt or fall; without a full face below it breaks.
 *
 * <p>The ants are client particles only ({@link #animateTick}: on the top block of a column, by day, without rain, in the
 * active season of the ants, April to October: from MID_SPRING to MID_AUTUMN of the season provider), no entities and no
 * server cost.
 */
public class AnthillBlock extends Block {
	public static final int MAX_LAYERS = 8;
	public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
	/** The shapes by layers: index 0 is empty, 8 a full block (2 px per layer). */
	private static final VoxelShape[] SHAPES = Block.boxes(MAX_LAYERS, layers -> Block.column(16.0, 0.0, layers * 2));
	/** A mob walks over layers below this (as over snow). */
	static final int PATHFINDABLE_BELOW = 5;

	public AnthillBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LAYERS, MAX_LAYERS));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LAYERS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return type == PathComputationType.LAND && state.getValue(LAYERS) < PATHFINDABLE_BELOW;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.is(this) ? below.getValue(LAYERS) == MAX_LAYERS
				: Block.isFaceFull(below.getCollisionShape(level, pos.below()), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		int layers = state.getValue(LAYERS);
		if (!context.getItemInHand().is(asItem()) || layers >= MAX_LAYERS) {
			return false;
		}
		return !context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState here = context.getLevel().getBlockState(context.getClickedPos());
		if (here.is(this)) {
			return here.setValue(LAYERS, Math.min(MAX_LAYERS, here.getValue(LAYERS) + 1));
		}
		BlockState placed = super.getStateForPlacement(context);
		return placed == null ? null : placed.setValue(LAYERS, 1);
	}

	/** Whether the ants are out: the season of the ants (April to October) by the season provider. */
	public static boolean antsActive(SubSeason subSeason) {
		return subSeason.ordinal() >= SubSeason.MID_SPRING.ordinal() && subSeason.ordinal() <= SubSeason.MID_AUTUMN.ordinal();
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (level.getBlockState(pos.above()).is(this) || random.nextInt(3) != 0 || !level.isBrightOutside() || level.isRainingAt(pos.above())
				|| !antsActive(SeasonPhase.subSeason(SeasonPhase.step(level)))) {
			return;
		}
		double top = pos.getY() + state.getValue(LAYERS) * 2 / 16.0 + 0.02;
		int ants = 1 + random.nextInt(2);
		for (int i = 0; i < ants; i++) {
			level.addParticle(ModParticles.ant(), pos.getX() + 0.1 + 0.8 * random.nextDouble(), top, pos.getZ() + 0.1 + 0.8 * random.nextDouble(),
					0, 0, 0);
		}
	}
}
