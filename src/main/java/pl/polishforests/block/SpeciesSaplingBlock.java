package pl.polishforests.block;

import java.util.EnumMap;
import java.util.Map;
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
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;

/**
 * The sapling of a species of the vertical slice (step V7, {@link TreeFoliage}): a vanilla sapling (two stages, random
 * tick at light 9, bone meal with a chance of 0.45) that grows a tree of the tree planner ({@link ProceduralTreeFeature})
 * in its open-grown form.
 *
 * <p><b>Size</b> (step S1, decision CP-C.1): when the sapling grows, it draws the size of its tree from the random source
 * of the growth ({@link SaplingSize#draw}: small, medium or large, most often medium), each the configured feature
 * {@code polishforests:sapling/<species>[/medium|/large]}. The sapling checks the room first: the planner's tree is a pure
 * function of the seed, the position and the configuration, so the check sees exactly the tree that would grow; a size
 * whose tree has no room (every wood block on air or a block that trees replace) falls back to the next smaller one
 * ({@link SaplingSize#choose}); when even the small tree has no room, the sapling stays a sapling. Bone meal is taken when
 * the small tree has room ({@link #hasRoom}). The vanilla {@link TreeGrower} of the block grows the small tree and serves
 * only as its identity (codec, other mods); the growth of this class never calls it.
 *
 * <p>When a key points to another feature type (a datapack), that size behaves as vanilla: no room check, the sapling
 * tries to grow and stays a sapling when the feature fails; a missing key counts as no room.
 */
public class SpeciesSaplingBlock extends SaplingBlock {
	/** Block update flags of vanilla's growth: removing the sapling and putting it back. */
	private static final int REMOVE_FLAGS = 818;
	private static final int RESET_FLAGS = 260;

	private final TreeFoliage foliage;
	private final Map<SaplingSize, ResourceKey<Feature>> features = new EnumMap<>(SaplingSize.class);

	public SpeciesSaplingBlock(TreeFoliage foliage, BlockBehaviour.Properties properties) {
		this(foliage, key(foliage, SaplingSize.SMALL), properties);
	}

	private SpeciesSaplingBlock(TreeFoliage foliage, ResourceKey<Feature> small, BlockBehaviour.Properties properties) {
		super(new TreeGrower(PolishForests.MOD_ID + ":" + foliage.species().id(), WeightedList.of(small), WeightedList.of(),
				WeightedList.of(), null), properties);
		this.foliage = foliage;
		for (SaplingSize s : SaplingSize.values()) {
			features.put(s, key(foliage, s));
		}
	}

	private static ResourceKey<Feature> key(TreeFoliage foliage, SaplingSize size) {
		return ResourceKey.create(Registries.FEATURE, PolishForests.id(foliage.featurePath(size)));
	}

	public TreeFoliage foliage() {
		return foliage;
	}

	/** The configured feature of the small tree (the tree of the saplings before step S1). */
	public ResourceKey<Feature> feature() {
		return feature(SaplingSize.SMALL);
	}

	/** The configured feature of a size of the tree. */
	public ResourceKey<Feature> feature(SaplingSize size) {
		return features.get(size);
	}

	@Override
	public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		if (state.getValue(STAGE) == 0) {
			super.advanceTree(level, pos, state, random);
		} else {
			grow(level, pos, state, random, SaplingSize.draw(random));
		}
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		return super.isValidBonemealTarget(level, pos, state, source) && level instanceof ServerLevel server && hasRoom(server, pos);
	}

	/**
	 * Grows the tree of a size at {@code pos}, or the next smaller size that has room (see the class comment).
	 *
	 * @return the size grown, or {@code null} when the sapling stays a sapling
	 */
	public @Nullable SaplingSize grow(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, SaplingSize drawn) {
		SaplingSize s = SaplingSize.choose(drawn, z -> hasRoom(level, pos, z));
		while (s != null) {
			Feature f = feature(level, s).orElseThrow();
			level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), REMOVE_FLAGS);
			if (f.place(level, level.getChunkSource().getGenerator(), random, pos)) {
				return s;
			}
			level.setBlock(pos, state, RESET_FLAGS);
			if (f instanceof ProceduralTreeFeature) {
				// The room check said yes and the feature no (no ground below): a smaller tree would fail alike.
				return null;
			}
			SaplingSize smaller = s.smaller();
			s = smaller == null ? null : SaplingSize.choose(smaller, z -> hasRoom(level, pos, z));
		}
		return null;
	}

	/** Whether the small tree of this sapling at {@code pos} has room (bone meal; the sapling's own block counts as free). */
	public boolean hasRoom(ServerLevel level, BlockPos pos) {
		return hasRoom(level, pos, SaplingSize.SMALL);
	}

	/**
	 * Whether the tree of a size at {@code pos} has room (the sapling's own block counts as free): the planner's check for
	 * a procedural tree, always true for another feature type (vanilla behavior), false when the key is missing.
	 */
	public boolean hasRoom(ServerLevel level, BlockPos pos, SaplingSize size) {
		Optional<Feature> f = feature(level, size);
		if (f.isEmpty()) {
			return false;
		}
		return !(f.get() instanceof ProceduralTreeFeature tree) || tree.fits(level, pos);
	}

	private Optional<Feature> feature(ServerLevel level, SaplingSize size) {
		return level.registryAccess().lookupOrThrow(Registries.FEATURE).getOptional(features.get(size));
	}
}
