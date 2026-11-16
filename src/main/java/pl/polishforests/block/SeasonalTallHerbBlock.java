package pl.polishforests.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.polishforests.season.GroundPhases;
import pl.polishforests.season.SeasonPhase;

/**
 * A herb of two blocks (step C3 of docs/04-pionowy-wycinek.md, CP-D.10, {@link GroundPlant.Kind#TALL_HERB}: the tall
 * nettle and the tall yellow iris): the lower and upper half of vanilla's double plant (placing, the item that sets both
 * halves, breaking either half removes the other, the lower half needs the ground of a plant, the upper half its lower
 * half), crosses with baked colors like the one-block {@link SeasonalHerbBlock}, without a seasonal block state: the client
 * draws the phases of the one-block plant ({@link GroundPlant#phases()}, PW-8 (a)) from the column, so both halves are
 * always in the same phase. While the plant has withdrawn (from November) neither half has an outline and a block placed
 * in either half replaces it (rule R12, as {@link SeasonalHerbBlock}); the other half then goes as vanilla's double plants
 * do. No water property: the halves are never waterlogged (the iris stands on land by the water).
 */
public class SeasonalTallHerbBlock extends DoublePlantBlock {
	/** The lower half: 14 px wide, the whole block (the stems go on into the upper half). */
	private static final VoxelShape LOWER = Block.column(14.0, 0.0, 16.0);
	/** The upper half: 14 px wide, 14 px high (the plant ends a little under two blocks). */
	private static final VoxelShape UPPER = Block.column(14.0, 0.0, 14.0);

	private final GroundPlant plant;

	public SeasonalTallHerbBlock(GroundPlant plant, BlockBehaviour.Properties properties) {
		super(properties);
		this.plant = plant;
	}

	public GroundPlant plant() {
		return plant;
	}

	/**
	 * Whether the plant is withdrawn now (false where the getter is not a level: generation, meshing). Both halves stand in
	 * one column, so they answer alike.
	 */
	public boolean withdrawn(BlockGetter level, BlockPos pos) {
		return level instanceof Level l && GroundPhases.withdrawn(plant, SeasonPhase.step(l), pos.getX(), pos.getZ());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (withdrawn(level, pos)) {
			return Shapes.empty();
		}
		return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER).move(state.getOffset(pos));
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return withdrawn(context.getLevel(), context.getClickedPos()) || super.canBeReplaced(state, context);
	}
}
