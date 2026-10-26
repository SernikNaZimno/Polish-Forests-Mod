package pl.polishforests.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeafLitterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A litter of 1–4 segments (step V12, {@link GroundPlant.Kind#COVER}; the Scots pine needle litter): the vanilla leaf
 * litter (facing and {@code segment_amount}, needs a sturdy top below), not tinted, with the mossy underside
 * {@link #MOSSY} (PW-7: a moss carpet under the needles, the model's second layer), set by the generation in the pine
 * forests.
 */
public class GroundCoverBlock extends LeafLitterBlock {
	public static final BooleanProperty MOSSY = BerryShrubBlock.MOSSY;

	public GroundCoverBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(MOSSY, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(MOSSY);
	}
}
