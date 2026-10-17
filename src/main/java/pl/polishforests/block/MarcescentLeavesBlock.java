package pl.polishforests.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import pl.polishforests.season.FoliageCurve;

/**
 * The leaves of a species that can keep its dead leaves over the winter (oak, hornbeam, beech; {@link FoliageCurve#marcescent},
 * V7 review round 1): {@link SpeciesLeavesBlock} plus the property {@link #MARCESCENT}. A marcescent leaf keeps a dry
 * brown from late autumn until leaf-out; the others of the species show the light twig haze of a leafless crown. The tree
 * planner writes it on young trees and the lower crowns (juvenile zone) only, so a mature canopy is not brown all winter.
 * Placed by a player the leaves are not marcescent.
 */
public class MarcescentLeavesBlock extends SpeciesLeavesBlock {
	public static final BooleanProperty MARCESCENT = BooleanProperty.create("marcescent");

	public MarcescentLeavesBlock(TreeFoliage foliage, float leafParticleChance, BlockBehaviour.Properties properties) {
		super(foliage, leafParticleChance, properties);
		registerDefaultState(defaultBlockState().setValue(MARCESCENT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(MARCESCENT);
	}

	/** Whether a leaf state keeps its dead leaves (false for the leaves of a species that never does). */
	public static boolean marcescent(BlockState state) {
		return state.hasProperty(MARCESCENT) && state.getValue(MARCESCENT);
	}
}
