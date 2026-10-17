package pl.polishforests.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import pl.polishforests.season.FoliageCurve;

/**
 * The leaves of a species of the vertical slice (step V7, {@link TreeFoliage}): vanilla leaves with tinted falling leaf
 * particles ({@link TintedParticleLeavesBlock}, so the Fast graphics mode, MoreCulling and Sodium treat them as leaves),
 * colored on the client by the species curve of {@link FoliageCurve} over the raw biome foliage color, plus the property
 * {@link #PHASE} 0-3: the tree's own shift of the curve (earlier or later turning), written by the tree planner from the
 * hash of the trunk (rule ZW-5: deterministic, not seasonal). Placed by a player the leaves take phase 0.
 */
public class SpeciesLeavesBlock extends TintedParticleLeavesBlock {
	public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, FoliageCurve.PHASES - 1);

	private final TreeFoliage foliage;

	public SpeciesLeavesBlock(TreeFoliage foliage, float leafParticleChance, BlockBehaviour.Properties properties) {
		super(leafParticleChance, properties);
		this.foliage = foliage;
		registerDefaultState(defaultBlockState().setValue(PHASE, 0));
	}

	public TreeFoliage foliage() {
		return foliage;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(PHASE);
	}
}
