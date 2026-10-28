package pl.polishforests.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
 *
 * <p>Step G1 (tree-felling mods): leaves cut off from wood fall within about two seconds instead of waiting for random
 * ticks (on average about a minute per leaf, minutes for a crown of the tree planner). The vanilla distance update still
 * decides: when the scheduled tick of a neighbor change leaves a non-persistent leaf at the decay distance, a second tick
 * {@value #FALL_MIN}-{@value #FALL_MAX} ticks later drops it if it is still there, so a crown falls as a short shower and
 * a leaf that another path to wood reaches in the meantime stays. Nothing changes for leaves that are still connected
 * and for persistent (player-placed) leaves.
 */
public class SpeciesLeavesBlock extends TintedParticleLeavesBlock {
	public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, FoliageCurve.PHASES - 1);
	/** Delay of the fall of a cut-off leaf after its distance reached the decay distance, ticks (step G1). */
	public static final int FALL_MIN = 2;
	public static final int FALL_MAX = 40;

	private final TreeFoliage foliage;

	public SpeciesLeavesBlock(TreeFoliage foliage, float leafParticleChance, BlockBehaviour.Properties properties) {
		super(leafParticleChance, properties);
		this.foliage = foliage;
		registerDefaultState(defaultBlockState().setValue(PHASE, 0));
	}

	public TreeFoliage foliage() {
		return foliage;
	}

	/**
	 * The vanilla distance update, then the quick fall of a cut-off leaf (step G1, see the class comment): a leaf that was
	 * already at the decay distance before this tick and still is falls now; one that has just reached it gets a tick
	 * later.
	 */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		super.tick(state, level, pos, random);
		BlockState now = level.getBlockState(pos);
		if (now.getBlock() != this || !decaying(now)) {
			return;
		}
		if (decaying(state)) {
			dropResources(now, level, pos);
			level.removeBlock(pos, false);
		} else {
			level.scheduleTick(pos, this, FALL_MIN + random.nextInt(FALL_MAX - FALL_MIN + 1));
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(PHASE);
	}
}
