package pl.polishforests.client.datagen;

import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BlockStateProviders;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.util.valueproviders.WeightedListInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.WeightedRandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BushFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.MegaPineFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.PineFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.PoplarFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.SpruceFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.AlterGroundDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.BeehiveDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.LeaveVineDecorator;
import net.minecraft.world.level.levelgen.feature.trunkplacers.FancyTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.GiantTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.PoplarTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.feature.FastTreeFeature;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Tree and shrub features (docs/03-m2-biomy.md §8.4): vanilla tree shapes with the bark and leaves chosen for each
 * species, a placeholder until the mod's own trees of M3, which replace these files under the same identifiers.
 * {@code polishforests:tree/<species>} for the 20 tree species, {@code polishforests:shrub/<species>} for the dwarf
 * mountain pine, osier, hazel and juniper, and the variants a palette picks by site: the stunted Scots pine of bog
 * woodlands, dry pine forests and the wind belt ({@code tree/scots_pine_low}), the stunted spruce of the timberline
 * ({@code tree/spruce_stunted}) and the mega spruce of the mountains ({@code tree/spruce_mega}). Oak and birch mix two
 * vanilla shapes inside their own feature (fancy oak 60% and small oak 40%; birch 80% and tall birch with rare bee nests
 * 20%), through the placed features {@code tree/oak_fancy}, {@code tree/oak_small}, {@code tree/birch_small} and
 * {@code tree/birch_tall}, which have no filter (the selector is placed through {@code tree/oak} and {@code tree/birch}).
 * All tree configurations are of the type {@code polishforests:tree} ({@link FastTreeFeature}), the vanilla tree with a
 * cheaper leaf update.
 */
final class ModTrees {
	/** Species with a tree feature: all trees of {@link Species}. */
	static final List<Species> TREES = java.util.Arrays.stream(Species.values()).filter(s -> s.path().startsWith("tree/"))
			.toList();
	/** Species with a shrub feature (ivy is a climber, from M4). */
	static final List<Species> SHRUBS = List.of(Species.DWARF_MOUNTAIN_PINE, Species.OSIER, Species.HAZEL, Species.JUNIPER);
	/** Tree variants picked by the palettes. */
	static final String SCOTS_PINE_LOW = "tree/scots_pine_low";
	static final String SPRUCE_STUNTED = "tree/spruce_stunted";
	static final String SPRUCE_MEGA = "tree/spruce_mega";
	static final List<String> VARIANTS = List.of(SCOTS_PINE_LOW, SPRUCE_STUNTED, SPRUCE_MEGA);
	/** Shapes inside the oak and birch features (placed features without a filter). */
	static final String OAK_FANCY = "tree/oak_fancy";
	static final String OAK_SMALL = "tree/oak_small";
	static final String BIRCH_SMALL = "tree/birch_small";
	static final String BIRCH_TALL = "tree/birch_tall";
	static final List<String> SHAPES = List.of(OAK_FANCY, OAK_SMALL, BIRCH_SMALL, BIRCH_TALL);

	private ModTrees() {
	}

	static ResourceKey<Feature> key(Species species) {
		return key(species.path());
	}

	static ResourceKey<Feature> key(String path) {
		return ResourceKey.create(Registries.FEATURE, PolishForests.id(path));
	}

	static void bootstrap(BootstrapContext<Feature> context) {
		Holder<BlockStateProvider> soil = context.lookup(Registries.BLOCK_STATE_PROVIDER)
				.getOrThrow(BlockStateProviders.SOIL_BENEATH_TREE);
		Holder<BlockStateProvider> podzol = context.lookup(Registries.BLOCK_STATE_PROVIDER)
				.getOrThrow(BlockStateProviders.PODZOL_BENEATH_TREE);
		HolderGetter<PlacedFeature> placed = context.lookup(Registries.PLACED_FEATURE);
		for (Species s : TREES) {
			register(context, key(s), tree(s, soil, placed));
		}
		for (Species s : SHRUBS) {
			register(context, key(s), shrub(s, soil));
		}
		// Stunted Scots pine (bog woodland, raised bog, the low share of the dry pine forest and the wind belt): 3+2.
		register(context, key(SCOTS_PINE_LOW), builder(Blocks.SPRUCE_LOG, new StraightTrunkPlacer(3, 2, 0), Blocks.SPRUCE_LEAVES,
				new PineFoliagePlacer(ConstantInt.of(1), ConstantInt.of(1), UniformInt.of(2, 3)), 1, soil).ignoreVines().build());
		// Stunted spruce of the timberline and of the dwarf pine belt: 3+2 with a narrow crown.
		register(context, key(SPRUCE_STUNTED), builder(Blocks.SPRUCE_LOG, new StraightTrunkPlacer(3, 2, 0), Blocks.SPRUCE_LEAVES,
				new SpruceFoliagePlacer(UniformInt.of(1, 2), UniformInt.of(0, 1), UniformInt.of(1, 2)), 1, soil).ignoreVines().build());
		// The vanilla mega spruce (3% of the spruces in the mountains, §8.4).
		register(context, key(SPRUCE_MEGA), new TreeFeature.Builder(BlockStateProvider.of(Blocks.SPRUCE_LOG),
				new GiantTrunkPlacer(13, 2, 14), BlockStateProvider.of(Blocks.SPRUCE_LEAVES),
				new MegaPineFoliagePlacer(ConstantInt.of(0), ConstantInt.of(0), UniformInt.of(13, 17)),
				new TwoLayersFeatureSize(1, 1, 2), soil).decorators(List.of(new AlterGroundDecorator(podzol))).build());
		register(context, key(OAK_FANCY), fancy(Blocks.OAK_LOG, soil));
		register(context, key(OAK_SMALL), blob(Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, 2, 0, 2, soil));
		register(context, key(BIRCH_SMALL), blob(Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 5, 2, 0, 2, soil));
		register(context, key(BIRCH_TALL), builder(Blocks.BIRCH_LOG, new StraightTrunkPlacer(5, 2, 6), Blocks.BIRCH_LEAVES,
				new BlobFoliagePlacer(ConstantInt.of(2), ConstantInt.of(0), 3), 1, soil)
				.decorators(List.of(new BeehiveDecorator(0.002F))).ignoreVines().build());
	}

	/**
	 * Registers a tree feature: a vanilla tree configuration becomes {@code polishforests:tree} ({@link FastTreeFeature}:
	 * the same shape, a cheaper leaf update), other features (the oak and birch selectors) stay as they are.
	 */
	private static void register(BootstrapContext<Feature> context, ResourceKey<Feature> key, Feature feature) {
		context.register(key, feature instanceof TreeFeature tree ? new FastTreeFeature(tree) : feature);
	}

	/** Placement of a tree or shrub: only where a sapling would survive (§8.4); no biome filter (a palette entry). */
	static List<PlacementModifier> placement(String path) {
		if (path.equals(Species.OSIER.path())) {
			// Osiers also grow on the sand of point bars and the gravel of gravel bars (§4.1, §4.3).
			return List.of(net.minecraft.world.level.levelgen.placement.BlockPredicateFilter.forPredicate(BlockPredicate.anyOf(
					BlockPredicate.wouldSurvive(Blocks.OAK_SAPLING),
					BlockPredicate.matchesBlocks(new Vec3i(0, -1, 0), List.of(Blocks.SAND, Blocks.GRAVEL)))));
		}
		return List.of(PlacementUtils.filteredByBlockSurvival(Blocks.OAK_SAPLING));
	}

	private static Feature tree(Species species, Holder<BlockStateProvider> soil, HolderGetter<PlacedFeature> placed) {
		return switch (species) {
			// Scots pine: straight 6+4, pine foliage, spruce bark and needles.
			case SCOTS_PINE -> builder(Blocks.SPRUCE_LOG, new StraightTrunkPlacer(6, 4, 0), Blocks.SPRUCE_LEAVES,
					new PineFoliagePlacer(ConstantInt.of(1), ConstantInt.of(1), UniformInt.of(3, 4)), 2, soil).ignoreVines().build();
			// Norway spruce: the vanilla spruce.
			case SPRUCE, LARCH -> spruce(Blocks.SPRUCE_LOG, new StraightTrunkPlacer(5, 2, 1), soil);
			// Silver fir: straight with spruce foliage and gray bark (pale oak).
			case FIR -> spruce(Blocks.PALE_OAK_LOG, new StraightTrunkPlacer(8, 3, 1), soil);
			case YEW -> spruce(Blocks.DARK_OAK_LOG, new StraightTrunkPlacer(4, 2, 0), soil);
			// Birch: 80% the vanilla birch, 20% the tall birch with rare bee nests (§8.4).
			case BIRCH -> new WeightedRandomSelectorFeature(WeightedList.<Holder<PlacedFeature>>builder()
					.add(placed.getOrThrow(ModWorldgen.placed(BIRCH_SMALL)), 80)
					.add(placed.getOrThrow(ModWorldgen.placed(BIRCH_TALL)), 20).build());
			// Pedunculate oak: 60% fancy oak, 40% small oak (§8.4).
			case OAK -> new WeightedRandomSelectorFeature(WeightedList.<Holder<PlacedFeature>>builder()
					.add(placed.getOrThrow(ModWorldgen.placed(OAK_FANCY)), 60)
					.add(placed.getOrThrow(ModWorldgen.placed(OAK_SMALL)), 40).build());
			// Beech: a tall straight trunk 9+4 with gray bark and a broad crown of radius 3, 4 layers deep (§8.4 had a fancy
			// oak with gray bark, which costs about twice as much in FEATURES; the own shapes come in M3).
			case BEECH -> builder(Blocks.PALE_OAK_LOG, new StraightTrunkPlacer(9, 4, 0), Blocks.OAK_LEAVES,
					new BlobFoliagePlacer(ConstantInt.of(3), ConstantInt.of(0), 4), 1, soil).ignoreVines().build();
			// Hornbeam: straight 6+2, blob of radius 2-3, gray bark.
			case HORNBEAM -> builder(Blocks.PALE_OAK_LOG, new StraightTrunkPlacer(6, 2, 0), Blocks.OAK_LEAVES,
					new BlobFoliagePlacer(UniformInt.of(2, 3), ConstantInt.of(0), 3), 1, soil).ignoreVines().build();
			// Linden, ash, elm, maples and sessile oak: a straight trunk 6+2 with a round crown of radius 3 (the fancy oak of
			// §8.4 costs about twice as much in FEATURES; the own shapes come in M3).
			case SESSILE_OAK, LINDEN, ASH, ELM, NORWAY_MAPLE, SYCAMORE_MAPLE -> builder(Blocks.OAK_LOG,
					new StraightTrunkPlacer(6, 2, 0), Blocks.OAK_LEAVES, new BlobFoliagePlacer(ConstantInt.of(3), ConstantInt.of(0), 3),
					1, soil).ignoreVines().build();
			// Black alder: straight 8+3, blob of radius 2, dark bark and leaves.
			case BLACK_ALDER -> blob(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, 8, 3, 0, 2, soil);
			// Gray alder: straight 6+3, blob of radius 2, gray bark.
			case GRAY_ALDER -> blob(Blocks.PALE_OAK_LOG, Blocks.OAK_LEAVES, 6, 3, 0, 2, soil);
			// White willow: the vanilla swamp oak (vines as hops). Vines of a neighboring willow must not block the trunk
			// (without ignoreVines about 40% of the willows in a stand of 7 trees per chunk failed).
			case WHITE_WILLOW -> builder(Blocks.OAK_LOG, new StraightTrunkPlacer(5, 3, 0), Blocks.OAK_LEAVES,
					new BlobFoliagePlacer(ConstantInt.of(3), ConstantInt.of(0), 3), 1, soil)
					.decorators(List.of(new LeaveVineDecorator(0.25F))).ignoreVines().build();
			case POPLAR -> poplar(soil);
			case ROWAN -> blob(Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, 1, 0, 2, soil);
			default -> throw new IllegalArgumentException("not a tree: " + species);
		};
	}

	/** Shrubs (§8.4): one or two logs with a bush crown. */
	private static TreeFeature shrub(Species species, Holder<BlockStateProvider> soil) {
		return switch (species) {
			// Dwarf mountain pine: one log, a wide low crown of radius 2-3 (spruce bark and needles).
			case DWARF_MOUNTAIN_PINE -> bush(Blocks.SPRUCE_LOG, new StraightTrunkPlacer(1, 0, 0), Blocks.SPRUCE_LEAVES,
					new BushFoliagePlacer(UniformInt.of(2, 3), ConstantInt.of(1), 2), soil);
			// Osier (riverside willows): 1-2 logs, a crown of radius 2.
			case OSIER -> bush(Blocks.OAK_LOG, new StraightTrunkPlacer(1, 1, 0), Blocks.OAK_LEAVES,
					new BushFoliagePlacer(ConstantInt.of(2), ConstantInt.of(1), 2), soil);
			// Hazel: a crown of radius 2 on one log.
			case HAZEL -> bush(Blocks.OAK_LOG, new StraightTrunkPlacer(1, 0, 0), Blocks.OAK_LEAVES,
					new BushFoliagePlacer(ConstantInt.of(2), ConstantInt.of(1), 2), soil);
			// Juniper: a narrow column 2-3 blocks high (spruce foliage of radius 1).
			case JUNIPER -> bush(Blocks.SPRUCE_LOG, new StraightTrunkPlacer(2, 1, 0), Blocks.SPRUCE_LEAVES,
					new SpruceFoliagePlacer(ConstantInt.of(1), ConstantInt.of(0), ConstantInt.of(2)), soil);
			default -> throw new IllegalArgumentException("not a shrub: " + species);
		};
	}

	private static TreeFeature bush(Block log, TrunkPlacer trunk, Block leaves, FoliagePlacer foliage,
			Holder<BlockStateProvider> soil) {
		return new TreeFeature.Builder(BlockStateProvider.of(log), trunk, BlockStateProvider.of(leaves), foliage,
				new TwoLayersFeatureSize(0, 0, 0), soil).ignoreVines().build();
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
		// Heights 4–12 instead of the vanilla 3–14: fewer branches and leaf clusters, a smaller box for the leaf update.
		return new TreeFeature.Builder(BlockStateProvider.of(log), new FancyTrunkPlacer(4, 8, 0),
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
