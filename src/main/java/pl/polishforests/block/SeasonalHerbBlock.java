package pl.polishforests.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.polishforests.season.GroundPhases;
import pl.polishforests.season.SeasonPhase;

/**
 * A herb of one block (step F2, {@link GroundPlant.Kind#HERB}: the yellow iris, the tussock sedge, the marsh fern and the
 * stinging nettle of the alder forests): a cross like vanilla's short grass, without a block state (the season is drawn by
 * the client, PW-8 (a): {@link GroundPhases#look} — in flower, leaves only or nothing). While the plant has withdrawn (the
 * nettle and the iris from November, the marsh fern from mid-October) it has no outline on either side and a block placed
 * there replaces it (rule R12, as {@link SeasonalPlantBlock}).
 */
public class SeasonalHerbBlock extends VegetationBlock {
	/** As vanilla's short grass: 12 px wide, 13 px high. */
	private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);

	private final GroundPlant plant;

	public SeasonalHerbBlock(GroundPlant plant, BlockBehaviour.Properties properties) {
		super(properties);
		this.plant = plant;
	}

	public GroundPlant plant() {
		return plant;
	}

	/** Whether the plant is withdrawn now (false where the getter is not a level: generation, meshing). */
	public boolean withdrawn(BlockGetter level, BlockPos pos) {
		return level instanceof Level l && GroundPhases.withdrawn(plant, SeasonPhase.step(l), pos.getX(), pos.getZ());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return withdrawn(level, pos) ? Shapes.empty() : SHAPE.move(state.getOffset(pos));
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return withdrawn(context.getLevel(), context.getClickedPos()) || super.canBeReplaced(state, context);
	}
}
