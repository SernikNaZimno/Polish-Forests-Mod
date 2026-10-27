package pl.polishforests.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.polishforests.season.GroundPhases;
import pl.polishforests.season.SeasonPhase;

/**
 * A spring geophyte in beds of 1–4 flowers (step V12, {@link GroundPlant.Kind#SEASONAL}; wood anemone and liverleaf):
 * the vanilla flower bed (facing and {@code flower_amount}, the vanilla flowerbed models by id, the stem under the grass
 * tint). Its look in the year is drawn by the client (PW-8 (a), step V13: {@link GroundPhases#look}, flowers, leaves only
 * or nothing); the server state does not change with the season. While the plant has withdrawn underground (the wood
 * anemone from June to March) it has no outline on either side (rule R12: both ends know the season), so the player does
 * not aim at an invisible plant, and a block placed there replaces it, as it would replace grass.
 */
public class SeasonalPlantBlock extends FlowerBedBlock {
	/** Height of the flowers, px (vanilla wildflowers: 3). */
	public static final int HEIGHT = 3;

	private final GroundPlant plant;

	public SeasonalPlantBlock(GroundPlant plant, BlockBehaviour.Properties properties) {
		super(properties, HEIGHT);
		this.plant = plant;
	}

	public GroundPlant plant() {
		return plant;
	}

	/** Whether the plant is withdrawn underground now (false where the getter is not a level: generation, meshing). */
	public boolean withdrawn(BlockGetter level, BlockPos pos) {
		return level instanceof Level l && GroundPhases.withdrawn(plant, SeasonPhase.step(l), pos.getX(), pos.getZ());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return withdrawn(level, pos) ? Shapes.empty() : super.getShape(state, level, pos, context);
	}

	@Override
	public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return withdrawn(context.getLevel(), context.getClickedPos()) || super.canBeReplaced(state, context);
	}
}
