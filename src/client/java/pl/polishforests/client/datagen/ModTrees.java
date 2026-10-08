package pl.polishforests.client.datagen;

import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.Holder;
import net.minecraft.data.worldgen.BlockStateProviders;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.util.valueproviders.WeightedListInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.PineFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.PoplarFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.SpruceFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.LeaveVineDecorator;
import net.minecraft.world.level.levelgen.feature.trunkplacers.FancyTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.PoplarTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Tree features {@code polishforests:tree/<species>} (docs/03-m2-biomy.md §8.4): vanilla tree shapes with the bark and
 * leaves chosen for each species, a placeholder until the mod's own trees of M3, which replace these files under the
 * same identifiers. Basic version of step S5 for the tree stand; the shrubs ({@code shrub/*}) come in step S7.
 */
final class ModTrees {
	/** Species with a tree feature in step S5: all trees of {@link Species} (no shrubs). */
	static final List<Species> TREES = java.util.Arrays.stream(Species.values()).filter(s -> !s.path().startsWith("shrub/"))
			.toList();

	private ModTrees() {
	}

	static ResourceKey<Feature> key(Species species) {
		return ResourceKey.create(Registries.FEATURE, PolishForests.id(species.path()));
	}

	static void bootstrap(BootstrapContext<Feature> context) {
		Holder<BlockStateProvider> soil = context.lookup(Registries.BLOCK_STATE_PROVIDER)
				.getOrThrow(BlockStateProviders.SOIL_BENEATH_TREE);
		for (Species s : TREES) {
			context.register(key(s), tree(s, soil));
		}
	}

	private static TreeFeature tree(Species species, Holder<BlockStateProvider> soil) {
		return switch (species) {
			// Scots pine: straight 6+4, pine foliage, spruce bark and needles.
			case SCOTS_PINE -> builder(Blocks.SPRUCE_LOG, new StraightTrunkPlacer(6, 4, 0), Blocks.SPRUCE_LEAVES,
					new PineFoliagePlacer(ConstantInt.of(1), ConstantInt.of(1), UniformInt.of(3, 4)), 2, soil).ignoreVines().build();
			// Norway spruce: the vanilla spruce.
			case SPRUCE, LARCH -> spruce(Blocks.SPRUCE_LOG, new StraightTrunkPlacer(5, 2, 1), soil);
			// Silver fir: straight with spruce foliage and gray bark (pale oak).
			case FIR -> spruce(Blocks.PALE_OAK_LOG, new StraightTrunkPlacer(8, 3, 1), soil);
			case YEW -> spruce(Blocks.DARK_OAK_LOG, new StraightTrunkPlacer(4, 2, 0), soil);
			case BIRCH -> blob(Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 5, 2, 0, 2, soil);
			// Beech: a fancy oak with gray bark.
			case BEECH -> fancy(Blocks.PALE_OAK_LOG, soil);
			// Hornbeam: straight 6+2, blob of radius 2-3, gray bark.
			case HORNBEAM -> builder(Blocks.PALE_OAK_LOG, new StraightTrunkPlacer(6, 2, 0), Blocks.OAK_LEAVES,
					new BlobFoliagePlacer(UniformInt.of(2, 3), ConstantInt.of(0), 3), 1, soil).ignoreVines().build();
			case OAK, SESSILE_OAK, LINDEN, ASH, ELM, NORWAY_MAPLE, SYCAMORE_MAPLE -> fancy(Blocks.OAK_LOG, soil);
			// Black alder: straight 8+3, blob of radius 2, dark bark and leaves.
			case BLACK_ALDER -> blob(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, 8, 3, 0, 2, soil);
			// Gray alder: straight 6+3, blob of radius 2, gray bark.
			case GRAY_ALDER -> blob(Blocks.PALE_OAK_LOG, Blocks.OAK_LEAVES, 6, 3, 0, 2, soil);
			// White willow: the vanilla swamp oak (vines as hops).
			case WHITE_WILLOW -> builder(Blocks.OAK_LOG, new StraightTrunkPlacer(5, 3, 0), Blocks.OAK_LEAVES,
					new BlobFoliagePlacer(ConstantInt.of(3), ConstantInt.of(0), 3), 1, soil)
					.decorators(List.of(new LeaveVineDecorator(0.25F))).build();
			case POPLAR -> poplar(soil);
			case ROWAN -> blob(Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, 1, 0, 2, soil);
			default -> throw new IllegalArgumentException("not a tree: " + species);
		};
	}

	private static TreeFeature.Builder builder(Block log, TrunkPlacer trunk, Block leaves, FoliagePlacer foliage,
			int lowerSize, Holder<BlockStateProvider> soil) {
		return new TreeFeature.Builder(BlockStateProvider.of(log), trunk, BlockStateProvider.of(leaves), foliage,
				new TwoLayersFeatureSize(lowerSize, 0, lowerSize == 2 ? 2 : 1), soil);
	}

	private static TreeFeature blob(Block log, Block leaves, int base, int a, int b, int radius, Holder<BlockStateProvider> soil) {
		return builder(log, new StraightTrunkPlacer(base, a, b), leaves,
				new BlobFoliagePlacer(ConstantInt.of(radius), ConstantInt.of(0), 3), 1, soil).ignoreVines().build();
	}

	private static TreeFeature spruce(Block log, TrunkPlacer trunk, Holder<BlockStateProvider> soil) {
		return builder(log, trunk, Blocks.SPRUCE_LEAVES,
				new SpruceFoliagePlacer(UniformInt.of(2, 3), UniformInt.of(0, 2), UniformInt.of(1, 2)), 2, soil)
				.ignoreVines().build();
	}

	private static TreeFeature fancy(Block log, Holder<BlockStateProvider> soil) {
		return new TreeFeature.Builder(BlockStateProvider.of(log), new FancyTrunkPlacer(3, 11, 0),
				BlockStateProvider.of(Blocks.OAK_LEAVES), new FancyFoliagePlacer(ConstantInt.of(2), ConstantInt.of(4), 4),
				new TwoLayersFeatureSize(0, 0, 0, OptionalInt.of(4)), soil).ignoreVines().build();
	}

	/** Poplar: the vanilla poplar shape with oak leaves (the vanilla poplars have autumn leaves). */
	private static TreeFeature poplar(Holder<BlockStateProvider> soil) {
		IntProvider foliageHeight = new WeightedListInt(WeightedList.<IntProvider>builder().add(ConstantInt.of(5), 5)
				.add(ConstantInt.of(6), 5).add(ConstantInt.of(7), 1).add(ConstantInt.of(8), 1).build());
		return new TreeFeature.Builder(BlockStateProvider.of(Blocks.POPLAR_LOG),
				new PoplarTrunkPlacer(7, 4, 0, ConstantInt.of(4), UniformInt.of(1, 4)), BlockStateProvider.of(Blocks.OAK_LEAVES),
				new PoplarFoliagePlacer(foliageHeight, ConstantInt.of(0), UniformInt.of(5, 6), 0.15F),
				new TwoLayersFeatureSize(1, 0, 2), soil).ignoreVines().build();
	}
}
