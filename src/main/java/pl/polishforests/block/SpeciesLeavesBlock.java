package pl.polishforests.block;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 *
 * <p>Before a leaf at the decay distance falls (the quick fall and the vanilla random tick alike), a search through the
 * leaves around it ({@link #reach}) checks that no wood is within six steps: the distances the tree planner writes are
 * those of each tree's own wood, so where two crowns meet a leaf can store a larger distance than the nearest wood gives
 * and, once a neighbor is felled, pass it on as the decay distance to a leaf the other tree still holds. Such a leaf takes
 * its true distance instead of falling (its neighbors then update from it).
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
			fallOrReconnect(now, level, pos);
		} else {
			level.scheduleTick(pos, this, FALL_MIN + random.nextInt(FALL_MAX - FALL_MIN + 1));
		}
	}

	/** The vanilla decay on a random tick, but a leaf that wood still reaches takes its distance instead (see the class comment). */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (decaying(state)) {
			fallOrReconnect(state, level, pos);
			return;
		}
		super.randomTick(state, level, pos, random);
	}

	private static void fallOrReconnect(BlockState state, ServerLevel level, BlockPos pos) {
		int d = reach(level::getBlockState, pos, s -> s.is(BlockTags.PREVENTS_NEARBY_LEAF_DECAY));
		if (d < LeavesBlock.DECAY_DISTANCE) {
			level.setBlock(pos, state.setValue(LeavesBlock.DISTANCE, d), Block.UPDATE_ALL);
			return;
		}
		dropResources(state, level, pos);
		level.removeBlock(pos, false);
	}

	/**
	 * The distance of the leaf at {@code start} from the nearest wood through leaves (blocks with a leaf distance, as the
	 * vanilla rule walks), 1-6, or {@link LeavesBlock#DECAY_DISTANCE} when no wood is that near; unlike the vanilla update it
	 * does not trust the distances the leaves store.
	 */
	public static int reach(Function<BlockPos, BlockState> level, BlockPos start, Predicate<BlockState> wood) {
		LongOpenHashSet seen = new LongOpenHashSet();
		seen.add(start.asLong());
		LongArrayList ring = new LongArrayList();
		ring.add(start.asLong());
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int d = 1; d < LeavesBlock.DECAY_DISTANCE && !ring.isEmpty(); d++) {
			LongArrayList next = new LongArrayList();
			for (int i = 0; i < ring.size(); i++) {
				long at = ring.getLong(i);
				for (Direction dir : DIRECTIONS) {
					p.set(at).move(dir);
					long key = p.asLong();
					if (!seen.add(key)) {
						continue;
					}
					BlockState s = level.apply(p);
					if (wood.test(s)) {
						return d;
					}
					if (s.hasProperty(BlockStateProperties.DISTANCE)) {
						next.add(key);
					}
				}
			}
			ring = next;
		}
		return LeavesBlock.DECAY_DISTANCE;
	}

	private static final Direction[] DIRECTIONS = Direction.values();

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(PHASE);
	}
}
