package pl.polishforests.client.datagen;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BlockStateProviders;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BushFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.SpruceFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.worldgen.feature.ClumpTrunkPlacer;
import pl.polishforests.worldgen.feature.FastTreeFeature;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.TreeForm;

/**
 * Tree and shrub features (docs/03-m2-biomy.md §8.4, decision PW-3 of docs/04-pionowy-wycinek.md):
 * {@code polishforests:shrub/<species>} for the dwarf mountain pine (the creeping shrub of step F6), osier, hazel, juniper,
 * alder buckthorn and bird cherry (the vanilla bushes of M2, step V9; since step F7 in no palette, kept registered), and the
 * thin-stemmed shrubs of the forests ({@link #SLICE}, review of V9). The shrub configurations are of the type
 * {@code polishforests:tree} ({@link FastTreeFeature}), the vanilla tree with a cheaper leaf update. Step F7 removed the
 * placeholder trees of M2 ({@code polishforests:tree_substitute/<species>}, their variants and the shapes inside the oak
 * and birch selectors): no palette used them any more (chunks store blocks, not features, so the saved chunks keep their
 * trees).
 *
 * <p>The target keys {@code polishforests:tree/<species>} (rule ZW-3) are the procedural trees of the tree planner
 * ({@link ProceduralTreeFeature}, a lone mature canopy tree; the tree stand calls the planner itself with the layer, age
 * class and territory) for the species with a planner shape; the palettes of the forests of the vertical slice point to
 * them, all others to the placeholders (step V9). M3 changes only the palettes.
 */
final class ModTrees {
	/** Species with a placeholder tree feature: all trees of {@link Species}. */
	static final List<Species> TREES = java.util.Arrays.stream(Species.values()).filter(s -> !s.shrub()).toList();
	/** Species with a procedural tree feature {@code tree/<species>}: those with a planner shape (step V9). */
	static final List<Species> PROCEDURAL = TREES.stream().filter(SpeciesShape::supported).toList();
	/** Species with a shrub feature (ivy is a climber, from M4). */
	static final List<Species> SHRUBS = List.of(Species.DWARF_MOUNTAIN_PINE, Species.OSIER, Species.HAZEL, Species.JUNIPER,
			Species.ALDER_BUCKTHORN, Species.BIRD_CHERRY);
	/**
	 * Step F4: the growth forms of the planner's trees by site ({@link TreeForm}), {@code tree/<species>/<form>}: the low
	 * crooked pine and birch of the bogs, the thin pine of the dry pine forest, the windswept pine of the coastal wind belt
	 * and the birch of several stems of the birch bog woodland.
	 */
	static final List<Map.Entry<Species, TreeForm.Kind>> FORMS = List.of(Map.entry(Species.SCOTS_PINE, TreeForm.Kind.STUNTED),
			Map.entry(Species.SCOTS_PINE, TreeForm.Kind.SLENDER), Map.entry(Species.SCOTS_PINE, TreeForm.Kind.WINDSWEPT),
			Map.entry(Species.BIRCH, TreeForm.Kind.STUNTED), Map.entry(Species.BIRCH, TreeForm.Kind.MULTI_STEM),
			// Step F6: the narrow candle spruce of the montane spruce forest, the dwarf spruce and the low rowan of the
			// timberline and of the dwarf pine scrub.
			Map.entry(Species.SPRUCE, TreeForm.Kind.MONTANE), Map.entry(Species.SPRUCE, TreeForm.Kind.DWARF),
			Map.entry(Species.ROWAN, TreeForm.Kind.DWARF),
			// Step F7: the lone open-grown trees of the meadows (broad crowns, low branches).
			Map.entry(Species.OAK, TreeForm.Kind.SOLITARY), Map.entry(Species.LINDEN, TreeForm.Kind.SOLITARY),
			Map.entry(Species.BIRCH, TreeForm.Kind.SOLITARY), Map.entry(Species.WHITE_WILLOW, TreeForm.Kind.SOLITARY));

	/** The feature path of a growth form of a species ({@code tree/<species>/<form>}; the default form: {@code tree/<species>}). */
	static String form(Species s, TreeForm.Kind form) {
		return form == TreeForm.Kind.NORMAL ? s.path() : s.path() + "/" + form.id();
	}

	/**
	 * Placeholders of the shrubs and the rowan in the forests of the vertical slice (review of V9, round 1): thin stems of
	 * the thin trunk block of a species with a similar bark (the census takes the species from the planted feature), so a
	 * shrub never has a trunk thicker than the pines around it, and loose crowns without a flat underside; since step F7
	 * the shrubs of every palette.
	 */
	static final Map<Species, String> SLICE = Map.of(Species.HAZEL, "shrub/hazel_thin", Species.JUNIPER, "shrub/juniper_thin",
			Species.ALDER_BUCKTHORN, "shrub/alder_buckthorn_thin", Species.ROWAN, ModTrees.ROWAN_UNDERSTORY,
			Species.BIRD_CHERRY, "shrub/bird_cherry_thin", Species.OSIER, "shrub/osier_thin");
	/**
	 * Step F6 (choice F6-a): the own rowan of the planner in the shrub layer and the mantles of all forests in place of the
	 * thin-stemmed placeholder of the slice ({@code tree_substitute/rowan_thin}, removed in step F7): a lone tree of the lower tree layer
	 * (a2, about 0.62 of the canopy rowan's height: 6-8 blocks), its own trunk, leaves and fruit.
	 */
	static final String ROWAN_UNDERSTORY = "tree/rowan_understory";
	/** Step F6: the sizes of the dwarf mountain pine a sapling grows into (the creeping shrub planner's size factor). */
	static final Map<pl.polishforests.block.SaplingSize, Float> DWARF_PINE_SAPLING = Map.of(pl.polishforests.block.SaplingSize.SMALL,
			0.5F, pl.polishforests.block.SaplingSize.MEDIUM, 0.8F, pl.polishforests.block.SaplingSize.LARGE, 1.2F);
	/** The taller hazel of the forest mantle of the slice (shrubs of 3-5 blocks along the forest edge). */
	static final String HAZEL_MANTLE = "shrub/hazel_mantle_thin";
	/** Step F5: the osier and the bird cherry with hop ({@link pl.polishforests.worldgen.feature.HopDecorator}). */
	static final String OSIER_HOP = "shrub/osier_hop_thin";
	static final String BIRD_CHERRY_HOP = "shrub/bird_cherry_hop_thin";
	/**
	 * Step F7: the own osier of the point bars, the gravel bars and the willow carr (the M2 osier there until F7): the shape
	 * of {@code shrub/osier_thin}, placed also on the sand and the gravel of the bars ({@link #placement}).
	 */
	static final String OSIER_BAR = "shrub/osier_bar_thin";
	/** Step F7: the M2 bushes, kept registered but in no palette ({@code PlannerPalettesTest}). */
	static final List<String> M2_SHRUBS = List.of("shrub/osier", "shrub/hazel", "shrub/juniper", "shrub/alder_buckthorn",
			"shrub/bird_cherry");

	private ModTrees() {
	}

	/** Key of the M2 feature of a shrub ({@code shrub/<species>}). */
	static ResourceKey<Feature> key(Species species) {
		return key(species.path());
	}

	static ResourceKey<Feature> key(String path) {
		return ResourceKey.create(Registries.FEATURE, PolishForests.id(path));
	}

	static void bootstrap(BootstrapContext<Feature> context) {
		Holder<BlockStateProvider> soil = context.lookup(Registries.BLOCK_STATE_PROVIDER)
				.getOrThrow(BlockStateProviders.SOIL_BENEATH_TREE);
		for (Species s : SHRUBS) {
			register(context, key(s), shrub(s, soil));
		}
		// Step V9: the procedural trees of the target keys (a lone mature canopy tree; the tree stand plans its trees itself).
		for (Species s : PROCEDURAL) {
			context.register(key(s.path()), new ProceduralTreeFeature(s, CrownLayer.CANOPY, AgeClass.MATURE));
		}
		// Step F4: the growth forms by site.
		for (Map.Entry<Species, TreeForm.Kind> f : FORMS) {
			context.register(key(form(f.getKey(), f.getValue())), new ProceduralTreeFeature(f.getKey(), CrownLayer.CANOPY, AgeClass.MATURE,
					pl.polishforests.worldgen.tree.TreeSize.ONE, f.getValue()));
		}
		// The placeholders of the slice (review of V9, round 1): thin stems, loose crowns. Round 2: the hazel has two or three
		// stems from one root crown (ClumpTrunkPlacer) and round crowns narrow at their lowest row (the fancy foliage: a
		// cross, two rows of 5 x 5 without the corners, a cross), so no flat plate of leaves stands on a stem at head height;
		// 4-5 blocks tall, along the forest edge a taller hazel of 5-6 blocks.
		register(context, key(SLICE.get(Species.HAZEL)), thinShrub(TreeTrunk.HORNBEAM, 1, new ClumpTrunkPlacer(3, 1, 0, 2),
				Blocks.OAK_LEAVES, new FancyFoliagePlacer(ConstantInt.of(2), ConstantInt.of(0), 3), soil));
		register(context, key(HAZEL_MANTLE), thinShrub(TreeTrunk.HORNBEAM, 1, new ClumpTrunkPlacer(4, 1, 0, 2),
				Blocks.OAK_LEAVES, new FancyFoliagePlacer(ConstantInt.of(2), ConstantInt.of(0), 3), soil));
		// Juniper: a narrow column of needles on a thin dark stem (the bark of the spruce).
		register(context, key(SLICE.get(Species.JUNIPER)), thinShrub(TreeTrunk.SPRUCE, 1, new StraightTrunkPlacer(1, 1, 0),
				Blocks.SPRUCE_LEAVES, new SpruceFoliagePlacer(ConstantInt.of(1), ConstantInt.of(0), ConstantInt.of(2)), soil));
		// Alder buckthorn: thin dark gray stems (the bark of the linden), one or two, and a loose crown of the jungle leaves,
		// lighter and coarser than those of the hazel (V9: the dark oak leaves made a black box in the shade); round 2: the
		// fancy foliage of three rows (a cross, 5 x 5 without the corners, a cross) above a short bare stem, no plate; round 2
		// after the frames: those crowns of 5 x 5 stood on bare stems two blocks up and joined into a hedge-like slab, so a
		// slender crown of radius 1 down to the ground (four rows from the top of the stem), as the open, upright shrub is.
		register(context, key(SLICE.get(Species.ALDER_BUCKTHORN)), thinShrub(TreeTrunk.LINDEN, 1, new ClumpTrunkPlacer(3, 1, 0, 1),
				Blocks.JUNGLE_LEAVES, new FancyFoliagePlacer(ConstantInt.of(1), ConstantInt.of(0), 3), soil));
		// Step F6: the own rowan of the shrub layer and the mantles, a tree of the lower tree layer.
		context.register(key(ROWAN_UNDERSTORY), new ProceduralTreeFeature(Species.ROWAN, CrownLayer.SUBCANOPY, AgeClass.MATURE));
		// Step F6: the dwarf mountain pine's sapling grows into a creeping shrub of its size.
		for (Map.Entry<pl.polishforests.block.SaplingSize, Float> z : DWARF_PINE_SAPLING.entrySet()) {
			context.register(key(pl.polishforests.block.TreeFoliage.DWARF_MOUNTAIN_PINE.featurePath(z.getKey())),
					new pl.polishforests.worldgen.feature.CreepingShrubFeature(Species.DWARF_MOUNTAIN_PINE, z.getValue()));
		}
		// Step F2: the bird cherry of the shrub layer and a2 of the alder forests, a tall shrub of 5-7 blocks of two or three
		// thin stems (the dark bark of the black alder) with a loose crown of its own leaves (phase and leaf curve).
		register(context, key(SLICE.get(Species.BIRD_CHERRY)), birdCherry(5, 2, 2, soil));
		// Its sapling grows into a smaller or a larger shrub of the same form (step S1: three sizes).
		for (pl.polishforests.block.SaplingSize z : pl.polishforests.block.SaplingSize.values()) {
			int[] h = switch (z) {
				case SMALL -> new int[] {3, 1, 1};
				case MEDIUM -> new int[] {4, 2, 2};
				case LARGE -> new int[] {6, 2, 2};
			};
			register(context, key(pl.polishforests.block.TreeFoliage.BIRD_CHERRY.featurePath(z)), birdCherry(h[0], h[1], h[2], soil));
		}
		register(context, key(BIRD_CHERRY_HOP), hop(birdCherry(5, 2, 2, soil), 0.6F, 0.14F));
		// Step F5: the osier of the floodplain forests and of the osier scrub: a dense clump of straight shoots of 3-6 blocks
		// (up to three side stems around the main one, the pale bark of the white willow's thin trunk) with slender crowns of
		// its own narrow leaves down the shoots (phase and leaf curve); with hop on part of the clumps of the floodplain.
		register(context, key(SLICE.get(Species.OSIER)), osier(3, 2, 3, soil));
		register(context, key(OSIER_HOP), hop(osier(3, 2, 3, soil), 0.5F, 0.16F));
		register(context, key(OSIER_BAR), osier(3, 2, 3, soil));
		for (pl.polishforests.block.SaplingSize z : pl.polishforests.block.SaplingSize.values()) {
			int[] h = switch (z) {
				case SMALL -> new int[] {2, 0, 1};
				case MEDIUM -> new int[] {2, 1, 2};
				case LARGE -> new int[] {4, 2, 3};
			};
			register(context, key(pl.polishforests.block.TreeFoliage.OSIER.featurePath(z)), osier(h[0], h[1], h[2], soil));
		}
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
		if (path.equals(Species.OSIER.path()) || path.equals(OSIER_BAR)) {
			// Osiers also grow on the sand of point bars and the gravel of gravel bars (§4.1, §4.3).
			return List.of(net.minecraft.world.level.levelgen.placement.BlockPredicateFilter.forPredicate(BlockPredicate.anyOf(
					BlockPredicate.wouldSurvive(Blocks.OAK_SAPLING),
					BlockPredicate.matchesBlocks(new Vec3i(0, -1, 0), List.of(Blocks.SAND, Blocks.GRAVEL)))));
		}
		if (path.startsWith(Species.SCOTS_PINE.path() + "/")) {
			// Scots pine also on sand: it invades the gray dunes and stands at the edge of the coastal pine forest on bare
			// sand (round 1 of the S8b review; without this the tree ecotone ended at the sand). The sand under the trunk
			// becomes dirt (the vanilla soil provider).
			return List.of(net.minecraft.world.level.levelgen.placement.BlockPredicateFilter.forPredicate(BlockPredicate.anyOf(
					BlockPredicate.wouldSurvive(Blocks.OAK_SAPLING),
					BlockPredicate.matchesBlocks(new Vec3i(0, -1, 0), List.of(Blocks.SAND)))));
		}
		return List.of(PlacementUtils.filteredByBlockSurvival(Blocks.OAK_SAPLING));
	}

	/** Shrubs (§8.4): one or two logs with a bush crown. */
	private static Feature shrub(Species species, Holder<BlockStateProvider> soil) {
		return switch (species) {
			// Dwarf mountain pine (step F6, choice F6-b): the creeping shrub of the own planner, stems along the slope with
			// raised ends and a blanket of its own needles (CreepingShrubPlan), in place of the M2 bush.
			case DWARF_MOUNTAIN_PINE -> new pl.polishforests.worldgen.feature.CreepingShrubFeature(Species.DWARF_MOUNTAIN_PINE, 1);
			// Osier (riverside willows): 1-2 logs, a crown of radius 2.
			case OSIER -> bush(Blocks.OAK_LOG, new StraightTrunkPlacer(1, 1, 0), Blocks.OAK_LEAVES,
					new BushFoliagePlacer(ConstantInt.of(2), ConstantInt.of(1), 2), soil);
			// Hazel: a crown of radius 2 on one log.
			case HAZEL -> bush(Blocks.OAK_LOG, new StraightTrunkPlacer(1, 0, 0), Blocks.OAK_LEAVES,
					new BushFoliagePlacer(ConstantInt.of(2), ConstantInt.of(1), 2), soil);
			// Juniper: a narrow column 2-3 blocks high (spruce foliage of radius 1) on 1-2 logs, so the tree census, which
			// counts three vertical logs as a trunk, does not count it as a tree.
			case JUNIPER -> bush(Blocks.SPRUCE_LOG, new StraightTrunkPlacer(1, 1, 0), Blocks.SPRUCE_LEAVES,
					new SpruceFoliagePlacer(ConstantInt.of(1), ConstantInt.of(0), ConstantInt.of(2)), soil);
			// Alder buckthorn (step V9, the shrub layer of the pine forests): 1-2 dark stems and a loose crown of radius 2.
			case ALDER_BUCKTHORN -> bush(Blocks.DARK_OAK_LOG, new StraightTrunkPlacer(1, 1, 0), Blocks.DARK_OAK_LEAVES,
					new BushFoliagePlacer(ConstantInt.of(2), ConstantInt.of(1), 2), soil);
			// Bird cherry (step F2): the placeholder of the forests outside the slice, 2-3 logs with a crown of radius 2.
			case BIRD_CHERRY -> bush(Blocks.DARK_OAK_LOG, new StraightTrunkPlacer(2, 1, 0), Blocks.OAK_LEAVES,
					new BushFoliagePlacer(ConstantInt.of(2), ConstantInt.of(1), 2), soil);
			default -> throw new IllegalArgumentException("not a shrub: " + species);
		};
	}

	/** The bird cherry of step F2: {@code stems} side stems of a stem of {@code base} + 0…{@code rand} blocks. */
	private static TreeFeature birdCherry(int base, int rand, int stems, Holder<BlockStateProvider> soil) {
		return thinShrub(TreeTrunk.BLACK_ALDER, 1, new ClumpTrunkPlacer(base, rand, 0, stems),
				ModBlocks.leaves(pl.polishforests.block.TreeFoliage.BIRD_CHERRY),
				new FancyFoliagePlacer(ConstantInt.of(2), ConstantInt.of(0), 3), soil);
	}

	/** The osier of step F5: {@code stems} side shoots of a shoot of {@code base} + 0…{@code rand} blocks. */
	private static TreeFeature osier(int base, int rand, int stems, Holder<BlockStateProvider> soil) {
		return thinShrub(TreeTrunk.WHITE_WILLOW, 1, new ClumpTrunkPlacer(base, rand, 0, stems),
				ModBlocks.leaves(pl.polishforests.block.TreeFoliage.OSIER),
				new FancyFoliagePlacer(ConstantInt.of(1), ConstantInt.of(0), 3), soil);
	}

	/** A shrub with the hop decorator of step F5 (the same shape). */
	private static TreeFeature hop(TreeFeature shrub, float shrubChance, float probability) {
		return new TreeFeature(shrub.trunkProvider(), shrub.trunkPlacer(), shrub.foliageProvider(), shrub.foliagePlacer(),
				shrub.rootPlacer(), shrub.minimumSize(), List.of(new pl.polishforests.worldgen.feature.HopDecorator(shrubChance,
						probability)), shrub.ignoreVines(), shrub.belowTrunkProvider());
	}

	/** A placeholder of the slice on a thin stem: the thin trunk block of radius {@code radius} px, growing up. */
	private static TreeFeature thinShrub(TreeTrunk bark, int radius, TrunkPlacer trunk, Block leaves, FoliagePlacer foliage,
			Holder<BlockStateProvider> soil) {
		return new TreeFeature.Builder(BlockStateProvider.of(ModBlocks.trunk(bark).state(radius, Direction.DOWN)), trunk,
				BlockStateProvider.of(leaves), foliage, new TwoLayersFeatureSize(0, 0, 0), soil).ignoreVines().build();
	}

	private static TreeFeature bush(Block log, TrunkPlacer trunk, Block leaves, FoliagePlacer foliage,
			Holder<BlockStateProvider> soil) {
		return new TreeFeature.Builder(BlockStateProvider.of(log), trunk, BlockStateProvider.of(leaves), foliage,
				new TwoLayersFeatureSize(0, 0, 0), soil).ignoreVines().build();
	}
}
