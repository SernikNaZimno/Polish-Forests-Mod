package pl.polishforests.block;

import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * A spring geophyte in beds of 1–4 flowers (step V12, {@link GroundPlant.Kind#SEASONAL}; wood anemone and liverleaf):
 * the vanilla flower bed (facing and {@code flower_amount}, the vanilla flowerbed models by id, the stem under the grass
 * tint). Its look in the year is drawn by the client (PW-8 (a): the phases of step V13, nothing in the empty phase); the
 * server state does not change with the season. The type exists so that V13 can tell the seasonal plants apart.
 */
public class SeasonalPlantBlock extends FlowerBedBlock {
	/** Height of the flowers, px (vanilla wildflowers: 3). */
	public static final int HEIGHT = 3;

	public SeasonalPlantBlock(BlockBehaviour.Properties properties) {
		super(properties, HEIGHT);
	}
}
