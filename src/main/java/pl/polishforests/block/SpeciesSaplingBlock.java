package pl.polishforests.block;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;

/**
 * The sapling of a species of the vertical slice (step V7, {@link TreeFoliage}): a vanilla sapling (two stages, random
 * tick at light 9, bone meal with a chance of 0.45) whose {@link TreeGrower} grows the configured feature
 * {@code polishforests:sapling/<species>}, a tree of the tree planner ({@link ProceduralTreeFeature}, the young pole
 * stage in its open-grown form). Unlike vanilla, the sapling checks the room first ({@link #hasRoom}): it advances to the
 * tree, and accepts bone meal, only when every wood block of the planned tree lands on air or a block that trees replace
 * (the planner's tree is a pure function of the seed and the position, so the check sees exactly the tree that would
 * grow); a sapling under a roof or next to a wall stays a sapling.
 */
public class SpeciesSaplingBlock extends SaplingBlock {
	private final TreeFoliage foliage;
	private final ResourceKey<Feature> feature;

	public SpeciesSaplingBlock(TreeFoliage foliage, BlockBehaviour.Properties properties) {
		this(foliage, ResourceKey.create(Registries.FEATURE, PolishForests.id(foliage.featurePath())), properties);
	}

	private SpeciesSaplingBlock(TreeFoliage foliage, ResourceKey<Feature> feature, BlockBehaviour.Properties properties) {
		super(new TreeGrower(PolishForests.MOD_ID + ":" + foliage.species().id(), WeightedList.of(feature), WeightedList.of(),
				WeightedList.of(), null), properties);
		this.foliage = foliage;
		this.feature = feature;
	}

	public TreeFoliage foliage() {
		return foliage;
	}

	/** The configured feature the sapling grows into. */
	public ResourceKey<Feature> feature() {
		return feature;
	}

	@Override
	public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		if (state.getValue(STAGE) == 0 || hasRoom(level, pos)) {
			super.advanceTree(level, pos, state, random);
		}
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		return super.isValidBonemealTarget(level, pos, state, source) && level instanceof ServerLevel server && hasRoom(server, pos);
	}

	/** Whether the tree of this sapling at {@code pos} has room (the sapling's own block counts as free). */
	public boolean hasRoom(ServerLevel level, BlockPos pos) {
		Optional<Feature> f = level.registryAccess().lookupOrThrow(Registries.FEATURE).getOptional(feature);
		return f.isPresent() && f.get() instanceof ProceduralTreeFeature tree && tree.fits(level, pos);
	}
}
