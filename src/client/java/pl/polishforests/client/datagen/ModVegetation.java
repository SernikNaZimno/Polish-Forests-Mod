package pl.polishforests.client.datagen;

import static pl.polishforests.worldgen.habitat.HabitatBiome.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.LeafLitterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.BlockBlobFeature;
import net.minecraft.world.level.levelgen.feature.FallenTreeFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import net.minecraft.world.level.levelgen.feature.WeightedRandomSelectorFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.AttachedToLogsDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TrunkVineDecorator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import pl.polishforests.worldgen.feature.config.HabitatCondition;
import pl.polishforests.worldgen.feature.config.PlantPalette;
import pl.polishforests.worldgen.feature.config.PlantPalette.Plant;
import pl.polishforests.worldgen.feature.config.TreePalette;
import pl.polishforests.worldgen.feature.plan.ColumnPlan.Ground;
import pl.polishforests.worldgen.feature.plan.ColumnPlan.Medium;
import pl.polishforests.worldgen.feature.plan.Ecotone;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * Palettes of the vegetation dispatchers of step 9 (docs/03-m2-biomy.md §8.2, §8.5, §8.6, §4.6) and the small features
 * they and the other steps use: fallen trees ({@code polishforests:deadwood/<species>}), the flowers of the bone meal
 * carriers ({@code polishforests:flowers/<group>}, in the tag {@code minecraft:can_spawn_from_bone_meal}) and the
 * glacial erratics of step 2 ({@code polishforests:glacial_erratics}). Only vanilla blocks (decision M2-D): reed is
 * sugar cane, cattail small dripleaf, butterbur big dripleaf, dwarf shrubs (heather, bilberry, crowberry) the
 * {@code bush} block (bilberry too, decision M2-13: no sweet berry bushes, which slow and hurt the player; an own
 * bilberry comes in M4), lichens pale moss carpet, heather on the heath pink petals,
 * spring geophytes wildflowers, marram grass the dry grasses, and beach wrack leaf litter.
 *
 * <p>The densities follow §8.5 (trees per chunk and composition), §8.6 (ground layer cover) and the hard rule of §4.6:
 * in the waterside zones, the floodplain forests and the alder carr short grass and small flowers stay at most 10% of
 * the land columns, tall plants, shrubs, reed and trees cover at least 60%, bare ground at most 25%.
 */
final class ModVegetation {
	private ModVegetation() {
	}

	/** Zones without trees: water, bars, willow scrub, herb fringes, reedbeds, beach and cliff zones (§4, §5.2). */
	static final List<Zone> TREELESS_ZONES = List.of(Zone.CHANNEL, Zone.SUBMERGED_PLANTS, Zone.FLOATING_LEAVED_PLANTS,
			Zone.REEDBED, Zone.SHORE_REEDBED, Zone.POINT_BAR, Zone.WILLOW_SCRUB, Zone.HERB_FRINGE, Zone.TALL_HERBS,
			Zone.WILLOW_CARR, Zone.GRAVEL_BAR, Zone.MONTANE_TALL_HERBS, Zone.FLOATING_MAT, Zone.STRANDLINE, Zone.EMBRYO_DUNE,
			Zone.CLIFF_FACE, Zone.CLIFF_TOP);

	/** Species of the fallen trees, {@code polishforests:deadwood/<species>}. */
	static final List<Species> DEADWOOD = List.of(Species.SCOTS_PINE, Species.SPRUCE, Species.FIR, Species.BIRCH, Species.OAK,
			Species.BEECH, Species.HORNBEAM, Species.BLACK_ALDER, Species.POPLAR, Species.WHITE_WILLOW, Species.GRAY_ALDER);

	static String deadwood(Species s) {
		return "deadwood/" + s.id();
	}

	/** Bone meal flower groups, {@code polishforests:flowers/<group>}. */
	static final List<String> FLOWERS = List.of("forest", "meadow", "wetland", "mountain");

	static String flowers(String group) {
		return "flowers/" + group;
	}

	static final List<String> ERRATICS = List.of("granite", "diorite", "mossy_cobblestone");

	static String erratic(String stone) {
		return "erratic/" + stone;
	}

	// ------------------------------------------------------------------ small features

	static void features(BootstrapContext<Feature> context, HolderGetter<PlacedFeature> placed) {
		for (Species s : DEADWOOD) {
			context.register(ModTrees.key(deadwood(s)), fallen(s));
		}
		context.register(ModTrees.key(flowers("forest")), flowers(List.of(
				segments(Blocks.WILDFLOWERS, FlowerBedBlock.AMOUNT, 1, 4, 4), single(Blocks.LILY_OF_THE_VALLEY, 2))));
		context.register(ModTrees.key(flowers("meadow")), flowers(List.of(single(Blocks.DANDELION, 3), single(Blocks.POPPY, 2),
				single(Blocks.OXEYE_DAISY, 3), single(Blocks.CORNFLOWER, 2), single(Blocks.AZURE_BLUET, 1))));
		// Marsh marigold as dandelion, garlic (Allium angulosum) as allium.
		context.register(ModTrees.key(flowers("wetland")), flowers(List.of(single(Blocks.DANDELION, 2), single(Blocks.ALLIUM, 2),
				single(Blocks.OXEYE_DAISY, 1))));
		// Crocus as allium.
		context.register(ModTrees.key(flowers("mountain")), flowers(List.of(single(Blocks.AZURE_BLUET, 3), single(Blocks.ALLIUM, 2),
				single(Blocks.DANDELION, 1))));
		Map<String, Block> stones = Map.of("granite", Blocks.GRANITE, "diorite", Blocks.DIORITE, "mossy_cobblestone",
				Blocks.MOSSY_COBBLESTONE);
		for (String stone : ERRATICS) {
			context.register(ModTrees.key(erratic(stone)), new BlockBlobFeature(stones.get(stone).defaultBlockState(),
					BlockPredicate.matchesTag(BlockTags.FOREST_ROCK_CAN_PLACE_ON)));
		}
		WeightedList.Builder<Holder<PlacedFeature>> erratics = WeightedList.builder();
		erratics.add(placed.getOrThrow(ModWorldgen.placed(erratic("granite"))), 5);
		erratics.add(placed.getOrThrow(ModWorldgen.placed(erratic("diorite"))), 2);
		erratics.add(placed.getOrThrow(ModWorldgen.placed(erratic("mossy_cobblestone"))), 3);
		context.register(ModTrees.key("glacial_erratics"), new WeightedRandomSelectorFeature(erratics.build()));
	}

	private static SimpleBlockFeature flowers(List<WeightedList<BlockState>> parts) {
		WeightedList.Builder<BlockState> all = WeightedList.builder();
		for (WeightedList<BlockState> part : parts) {
			part.unwrap().forEach(w -> all.add(w.value(), w.weight()));
		}
		return new SimpleBlockFeature(Holder.direct(new WeightedStateProvider(all.build())), false);
	}

	private static WeightedList<BlockState> single(Block block, int weight) {
		return WeightedList.<BlockState>builder().add(block.defaultBlockState(), weight).build();
	}

	/** Segmented blocks (wildflowers, leaf litter) with every amount and facing; total weight {@code weight} per amount. */
	private static WeightedList<BlockState> segments(Block block, IntegerProperty amount, int from, int to, int weight) {
		WeightedList.Builder<BlockState> out = WeightedList.builder();
		for (int a = from; a <= to; a++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				out.add(block.defaultBlockState().setValue(amount, a).setValue(FlowerBedBlock.FACING, d), weight);
			}
		}
		return out.build();
	}

	/** A fallen tree of the species' bark (§8.2): a stump and a log of 4–9 blocks, rarely with mushrooms. */
	private static FallenTreeFeature fallen(Species s) {
		Block log = switch (s) {
			case SCOTS_PINE, SPRUCE -> Blocks.SPRUCE_LOG;
			case FIR, BEECH, HORNBEAM, GRAY_ALDER -> Blocks.PALE_OAK_LOG;
			case BIRCH -> Blocks.BIRCH_LOG;
			case BLACK_ALDER -> Blocks.DARK_OAK_LOG;
			case POPLAR -> Blocks.POPLAR_LOG;
			default -> Blocks.OAK_LOG;
		};
		int min = s == Species.SPRUCE || s == Species.FIR || s == Species.SCOTS_PINE || s == Species.BEECH ? 5 : 4;
		int max = s == Species.SPRUCE || s == Species.FIR || s == Species.SCOTS_PINE || s == Species.BEECH ? 9 : 7;
		FallenTreeFeature.Builder b = FallenTreeFeature.builder(BlockStateProvider.of(log), UniformInt.of(min, max))
				.logDecorator(new AttachedToLogsDecorator(0.05F, Holder.direct(new WeightedStateProvider(WeightedList.<BlockState>builder()
						.add(Blocks.BROWN_MUSHROOM.defaultBlockState(), 2).add(Blocks.RED_MUSHROOM.defaultBlockState(), 1))),
						List.of(Direction.UP)));
		if (s == Species.OAK || s == Species.WHITE_WILLOW) {
			b.stumpDecorator(TrunkVineDecorator.INSTANCE);
		}
		return b.build();
	}

	// ------------------------------------------------------------------ tree stand (§8.5)

	/**
	 * Tree palette: zones first (no trees in the waterside and coast zones of {@link #TREELESS_ZONES}; willows by a small
	 * river; alders and birches by a lobelia lake; alders in a tree row; stunted spruces at the timberline), then the
	 * association and site type variants (Populetum albae, Abieti-Piceetum, gully tall herbs, the wind belt of the coastal
	 * pine forest, the birch bog woodland), then one rule per biome with §8.5. Weights are in tenths of a percent.
	 * "Beech or spruce" is beech with spruce as the alternative species.
	 */
	static TreePalette treePalette(HolderGetter<PlacedFeature> placed) {
		Trees t = new Trees(placed);
		t.rule(HabitatCondition.zones(TREELESS_ZONES.toArray(Zone[]::new)), 0);
		t.rule(HabitatCondition.zones(Zone.RIVERSIDE_WILLOWS), 6).add(Species.WHITE_WILLOW, 800, 1).add(Species.BLACK_ALDER, 200, 1);
		t.rule(HabitatCondition.zones(Zone.SHORE_ALDERS), 8).add(Species.BLACK_ALDER, 700, 1).add(Species.BIRCH, 300);
		t.rule(HabitatCondition.zones(Zone.TREE_ROW), 8).add(Species.BLACK_ALDER, 1000, 1);
		t.rule(HabitatCondition.zones(Zone.TIMBERLINE), 4).add(Species.SPRUCE, ModTrees.SPRUCE_STUNTED, 900).add(Species.ROWAN, 100);
		t.rule(HabitatCondition.biomes(WILLOW_POPLAR_FOREST).withAssociations(Association.POPULETUM_ALBAE), 7)
				.add(Species.WHITE_WILLOW, 400, 2).add(Species.POPLAR, 600);
		t.rule(HabitatCondition.biomes(MONTANE_SPRUCE_FOREST).withAssociations(Association.ABIETI_PICEETUM), 12)
				.add(Species.SPRUCE, 582).add(Species.SPRUCE, ModTrees.SPRUCE_MEGA, 18).add(Species.FIR, 300).add(Species.ROWAN, 100);
		t.rule(HabitatCondition.biomes(MONTANE_SPRUCE_FOREST).withAssociations(Association.GULLY_TALL_HERBS), 3)
				.add(Species.SPRUCE, 700).add(Species.ROWAN, 300);
		t.rule(HabitatCondition.biomes(COASTAL_PINE_FOREST).withAssociations(Association.STUNTED, Association.WINDSWEPT), 5)
				.add(Species.SCOTS_PINE, ModTrees.SCOTS_PINE_LOW, 900).add(Species.BIRCH, 100);
		t.rule(HabitatCondition.biomes(BOG_WOODLAND).withSiteTypes(ForestSiteType.BOGGY_MIXED_CONIFEROUS), 6)
				.add(Species.BIRCH, 800).add(Species.SCOTS_PINE, ModTrees.SCOTS_PINE_LOW, 200);
		t.rule(HabitatCondition.biomes(MONTANE_BEECH_FOREST).withAssociations(Association.SYCAMORE_RAVINE_FOREST), 8)
				.add(Species.SYCAMORE_MAPLE, 500).add(Species.BEECH, 300).add(Species.ASH, 100).add(Species.FIR, 100);
		t.rule(HabitatCondition.biomes(DRY_PINE_FOREST), 7).add(Species.SCOTS_PINE, 665)
				.add(Species.SCOTS_PINE, ModTrees.SCOTS_PINE_LOW, 285).add(Species.BIRCH, 50);
		t.rule(HabitatCondition.biomes(FRESH_PINE_FOREST), 11).add(Species.SCOTS_PINE, 850).add(Species.BIRCH, 100)
				.add(Species.SPRUCE, 50);
		t.rule(HabitatCondition.biomes(COASTAL_PINE_FOREST), 9).add(Species.SCOTS_PINE, 900).add(Species.BIRCH, 100);
		t.rule(HabitatCondition.biomes(MOIST_PINE_FOREST), 10).add(Species.SCOTS_PINE, 650).add(Species.BIRCH, 250)
				.add(Species.SPRUCE, 100);
		t.rule(HabitatCondition.biomes(BOG_WOODLAND), 6).add(Species.SCOTS_PINE, ModTrees.SCOTS_PINE_LOW, 700)
				.add(Species.BIRCH, 300);
		t.rule(HabitatCondition.biomes(MIXED_PINE_FOREST), 10).add(Species.SCOTS_PINE, 550).add(Species.OAK, 250)
				.add(Species.BIRCH, 100).alt(Species.BEECH, 100, Species.SPRUCE);
		t.rule(HabitatCondition.biomes(MIXED_FOREST), 9).add(Species.OAK, 450).add(Species.SCOTS_PINE, 300)
				.alt(Species.BEECH, 150, Species.SPRUCE).add(Species.HORNBEAM, 100);
		t.rule(HabitatCondition.biomes(OAK_HORNBEAM_FOREST), 9).add(Species.OAK, 350).add(Species.HORNBEAM, 300)
				.add(Species.LINDEN, 150).add(Species.ASH, 30).add(Species.NORWAY_MAPLE, 20).add(Species.BEECH, 100)
				.add(Species.SPRUCE, 50);
		// Beech forests: the biome already lies within the beech range (O, P), so beech keeps its full weight there (with
		// the ramp a beech forest at the range edge had 40% oak, S7 review).
		t.rule(HabitatCondition.biomes(LOWLAND_BEECH_FOREST), 7).full(Species.BEECH, 850).add(Species.OAK, 100)
				.add(Species.SYCAMORE_MAPLE, 50);
		t.rule(HabitatCondition.biomes(ALDER_CARR), 9).add(Species.BLACK_ALDER, 850, 1).add(Species.BIRCH, 100)
				.add(Species.ASH, 50);
		t.rule(HabitatCondition.biomes(ASH_ALDER_FOREST), 9).add(Species.BLACK_ALDER, 550, 1).add(Species.ASH, 300)
				.add(Species.ELM, 100).add(Species.BIRCH, 50);
		t.rule(HabitatCondition.biomes(WILLOW_POPLAR_FOREST), 7).add(Species.WHITE_WILLOW, 700, 2).add(Species.POPLAR, 300);
		t.rule(HabitatCondition.biomes(ELM_ASH_FOREST), 8).add(Species.OAK, 350).add(Species.ASH, 300).add(Species.ELM, 250)
				.add(Species.NORWAY_MAPLE, 50).add(Species.LINDEN, 50);
		t.rule(HabitatCondition.biomes(UPLAND_FIR_FOREST), 10).add(Species.FIR, 500).add(Species.BEECH, 250)
				.add(Species.OAK, 150).add(Species.SCOTS_PINE, 100);
		t.rule(HabitatCondition.biomes(MONTANE_BEECH_FOREST), 8).full(Species.BEECH, 700).add(Species.FIR, 200)
				.add(Species.SPRUCE, 50).add(Species.SYCAMORE_MAPLE, 50);
		// Mountains: 3% of the spruces are mega spruces (§8.4).
		t.rule(HabitatCondition.biomes(MONTANE_SPRUCE_FOREST), 12).add(Species.SPRUCE, 873).add(Species.SPRUCE, ModTrees.SPRUCE_MEGA, 27)
				.add(Species.ROWAN, 100);
		t.rule(HabitatCondition.biomes(GRAY_ALDER_FOREST), 8).add(Species.GRAY_ALDER, 700, 1).add(Species.ASH, 150)
				.add(Species.SPRUCE, 100).add(Species.WHITE_WILLOW, 50, 1);
		// Dwarf pine scrub: no trees but one stunted spruce per chunk (the shrubs come from the understory).
		t.rule(HabitatCondition.biomes(DWARF_PINE_SCRUB), 1).add(Species.SPRUCE, ModTrees.SPRUCE_STUNTED, 1000);
		t.rule(HabitatCondition.biomes(RAISED_BOG), 0.3F).add(Species.SCOTS_PINE, ModTrees.SCOTS_PINE_LOW, 1000);
		t.rule(HabitatCondition.biomes(HEATH), 0.5F).add(Species.SCOTS_PINE, 600).add(Species.BIRCH, 400);
		t.rule(HabitatCondition.biomes(WET_MEADOW), 0.1F).add(Species.BLACK_ALDER, 600).add(Species.WHITE_WILLOW, 400);
		t.rule(HabitatCondition.biomes(HAY_MEADOW), 0.1F).add(Species.OAK, 500).add(Species.BIRCH, 300).add(Species.LINDEN, 200);
		t.rule(HabitatCondition.biomes(GRAY_DUNE), 0.125F).add(Species.SCOTS_PINE, 1000);
		return new TreePalette(t.rules());
	}

	/** Builder of the tree palette rules. */
	private static final class Trees {
		private final HolderGetter<PlacedFeature> placed;
		private final List<TreePalette.Rule> rules = new ArrayList<>();
		private HabitatCondition condition;
		private float perChunk;
		private List<TreePalette.Entry> entries;

		Trees(HolderGetter<PlacedFeature> placed) {
			this.placed = placed;
		}

		Trees rule(HabitatCondition condition, float perChunk) {
			flush();
			this.condition = condition;
			this.perChunk = perChunk;
			this.entries = new ArrayList<>();
			return this;
		}

		Trees add(Species s, int weight) {
			return add(s, s.path(), weight, 0);
		}

		Trees add(Species s, int weight, int maxWaterDepth) {
			return add(s, s.path(), weight, maxWaterDepth);
		}

		Trees add(Species s, String tree, int weight) {
			return add(s, tree, weight, 0);
		}

		Trees add(Species s, String tree, int weight, int maxWaterDepth) {
			entries.add(new TreePalette.Entry(s, tree(tree), weight, Optional.empty(), maxWaterDepth));
			return this;
		}

		Trees alt(Species s, int weight, Species alternative) {
			entries.add(new TreePalette.Entry(s, tree(s.path()), weight,
					Optional.of(new TreePalette.Alternative(alternative, tree(alternative.path()))), 0));
			return this;
		}

		/** A species with its full weight, without the range ramp (a biome that already lies within its range). */
		Trees full(Species s, int weight) {
			entries.add(new TreePalette.Entry(s, tree(s.path()), weight, Optional.empty(), 0, false));
			return this;
		}

		private Holder<PlacedFeature> tree(String path) {
			return placed.getOrThrow(ModWorldgen.placed(path));
		}

		private void flush() {
			if (condition != null) {
				rules.add(new TreePalette.Rule(condition, perChunk, List.copyOf(entries)));
				condition = null;
			}
		}

		List<TreePalette.Rule> rules() {
			flush();
			return rules;
		}
	}

	// ------------------------------------------------------------------ column layers

	/** Builder of the rules of a column layer. */
	private static final class Layer {
		private final HolderGetter<PlacedFeature> placed;
		private final List<PlantPalette.Rule> rules = new ArrayList<>();

		Layer(HolderGetter<PlacedFeature> placed) {
			this.placed = placed;
		}

		Layer land(HabitatCondition c, float coverage, int patch, Plant... plants) {
			return rule(c, Medium.LAND, 1, 64, List.of(), coverage, patch, plants);
		}

		Layer land(HabitatCondition c, List<Ground> grounds, float coverage, int patch, Plant... plants) {
			return rule(c, Medium.LAND, 1, 64, grounds, coverage, patch, plants);
		}

		Layer rule(HabitatCondition c, Medium medium, int minDepth, int maxDepth, List<Ground> grounds, float coverage,
				int patch, Plant... plants) {
			rules.add(new PlantPalette.Rule(c, medium, minDepth, maxDepth, grounds, coverage, patch, List.of(plants)));
			return this;
		}

		/** A land rule for the columns of a forest edge class (the mantle or the fringe, step S8b). */
		Layer edge(HabitatCondition c, Ecotone.Edge edge, float coverage, int patch, Plant... plants) {
			rules.add(new PlantPalette.Rule(c, Medium.LAND, 1, 64, List.of(), coverage, patch, List.of(plants), List.of(edge)));
			return this;
		}

		Plant feature(String path, int weight) {
			return Plant.feature(placed.getOrThrow(ModWorldgen.placed(path)), weight);
		}

		PlantPalette palette() {
			return new PlantPalette(rules);
		}
	}

	private static Plant b(Block block, int weight) {
		return Plant.block(block.defaultBlockState(), weight);
	}

	/** Leaf litter of 2–4 segments (the feature turns it to a random facing). */
	private static Plant[] litter(int weight) {
		return new Plant[] {Plant.block(Blocks.LEAF_LITTER.defaultBlockState().setValue(LeafLitterBlock.AMOUNT, 2), weight / 3),
				Plant.block(Blocks.LEAF_LITTER.defaultBlockState().setValue(LeafLitterBlock.AMOUNT, 3), weight / 3),
				Plant.block(Blocks.LEAF_LITTER.defaultBlockState().setValue(LeafLitterBlock.AMOUNT, 4), weight - 2 * (weight / 3))};
	}

	/** A flower bed (wildflowers, pink petals) of 2–4 flowers. */
	private static Plant[] bed(Block block, int weight) {
		return new Plant[] {Plant.block(block.defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 2), weight / 3),
				Plant.block(block.defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 3), weight / 3),
				Plant.block(block.defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 4), weight - 2 * (weight / 3))};
	}

	private static Plant[] cat(Object... parts) {
		List<Plant> out = new ArrayList<>();
		for (Object p : parts) {
			if (p instanceof Plant plant) {
				out.add(plant);
			} else {
				out.addAll(Arrays.asList((Plant[]) p));
			}
		}
		return out.toArray(Plant[]::new);
	}

	private static HabitatCondition biomes(HabitatBiome... biomes) {
		return HabitatCondition.biomes(biomes);
	}

	/** Deadwood (§8.2): fallen trees of the stand's species, 0.15–0.5 per chunk. */
	static PlantPalette deadwood(HolderGetter<PlacedFeature> placed) {
		Layer l = new Layer(placed);
		l.land(biomes(DRY_PINE_FOREST, FRESH_PINE_FOREST, COASTAL_PINE_FOREST, MOIST_PINE_FOREST), perChunk(0.3), 1,
				l.feature(deadwood(Species.SCOTS_PINE), 9), l.feature(deadwood(Species.BIRCH), 1));
		l.land(biomes(MIXED_PINE_FOREST), perChunk(0.3), 1, l.feature(deadwood(Species.SCOTS_PINE), 7),
				l.feature(deadwood(Species.OAK), 3));
		l.land(biomes(BOG_WOODLAND), perChunk(0.25), 1, l.feature(deadwood(Species.BIRCH), 6),
				l.feature(deadwood(Species.SCOTS_PINE), 4));
		l.land(biomes(MIXED_FOREST), perChunk(0.35), 1, l.feature(deadwood(Species.OAK), 5),
				l.feature(deadwood(Species.SCOTS_PINE), 3), l.feature(deadwood(Species.BEECH), 2));
		l.land(biomes(OAK_HORNBEAM_FOREST), perChunk(0.4), 1, l.feature(deadwood(Species.OAK), 5),
				l.feature(deadwood(Species.HORNBEAM), 4), l.feature(deadwood(Species.BIRCH), 1));
		l.land(biomes(LOWLAND_BEECH_FOREST), perChunk(0.5), 1, l.feature(deadwood(Species.BEECH), 9),
				l.feature(deadwood(Species.OAK), 1));
		l.land(biomes(MONTANE_BEECH_FOREST), perChunk(0.5), 1, l.feature(deadwood(Species.BEECH), 8),
				l.feature(deadwood(Species.FIR), 2));
		l.land(biomes(UPLAND_FIR_FOREST), perChunk(0.45), 1, l.feature(deadwood(Species.FIR), 6),
				l.feature(deadwood(Species.BEECH), 4));
		l.land(biomes(MONTANE_SPRUCE_FOREST), perChunk(0.5), 1, l.feature(deadwood(Species.SPRUCE), 1));
		l.land(biomes(ALDER_CARR), perChunk(0.35), 1, l.feature(deadwood(Species.BLACK_ALDER), 8),
				l.feature(deadwood(Species.BIRCH), 2));
		l.land(biomes(ASH_ALDER_FOREST), perChunk(0.3), 1, l.feature(deadwood(Species.BLACK_ALDER), 7),
				l.feature(deadwood(Species.OAK), 3));
		// Gray alder forest: the gray bark of its gray alders (pale oak, §8.4), some spruce.
		l.land(biomes(GRAY_ALDER_FOREST), perChunk(0.3), 1, l.feature(deadwood(Species.GRAY_ALDER), 8),
				l.feature(deadwood(Species.SPRUCE), 2));
		l.land(biomes(WILLOW_POPLAR_FOREST), perChunk(0.35), 1, l.feature(deadwood(Species.POPLAR), 5),
				l.feature(deadwood(Species.WHITE_WILLOW), 5));
		l.land(biomes(ELM_ASH_FOREST), perChunk(0.3), 1, l.feature(deadwood(Species.OAK), 1));
		return l.palette();
	}

	/** Coverage of a column for a mean number per chunk. */
	private static float perChunk(double n) {
		return (float) (n / 256);
	}

	/** Understory (§8.2, §8.5): shrubs of the forest biomes, the dwarf pine belt (70% cover) and the heath. */
	static PlantPalette understory(HolderGetter<PlacedFeature> placed) {
		Layer l = new Layer(placed);
		l.land(HabitatCondition.zones(TREELESS_ZONES.toArray(Zone[]::new)), 0, 1);
		// Forest edges (step S8b, rule Z10): the mantle of shrubs where a forest meets open land, on the outer meters of the
		// forest and the first meter or two of the open land (those columns take the code of the nearest forest column):
		// hazel on the fertile sites (blackthorn, hawthorn and dog rose have no vanilla substitute), juniper by the pine and
		// spruce forests, gray and eared willow (osier) by the wet forests.
		l.edge(biomes(OAK_HORNBEAM_FOREST, ELM_ASH_FOREST, MIXED_FOREST, LOWLAND_BEECH_FOREST, UPLAND_FIR_FOREST,
				MONTANE_BEECH_FOREST), Ecotone.Edge.MANTLE, 0.06F, 1, l.feature(Species.HAZEL.path(), 1));
		l.edge(biomes(DRY_PINE_FOREST, FRESH_PINE_FOREST, COASTAL_PINE_FOREST, MOIST_PINE_FOREST, MIXED_PINE_FOREST,
				MONTANE_SPRUCE_FOREST), Ecotone.Edge.MANTLE, 0.05F, 1, l.feature(Species.JUNIPER.path(), 3),
				l.feature(Species.HAZEL.path(), 1));
		l.edge(biomes(ALDER_CARR, ASH_ALDER_FOREST, WILLOW_POPLAR_FOREST, BOG_WOODLAND, GRAY_ALDER_FOREST), Ecotone.Edge.MANTLE,
				0.06F, 1, l.feature(Species.OSIER.path(), 3), l.feature(Species.HAZEL.path(), 1));
		// About 13 dwarf pines of 21–37 columns each per chunk: the crowns cover about 70% of the ground.
		l.land(biomes(DWARF_PINE_SCRUB), 0.05F, 1, l.feature(Species.DWARF_MOUNTAIN_PINE.path(), 1));
		l.land(biomes(HEATH), 0.01F, 3, l.feature(Species.JUNIPER.path(), 1));
		l.land(biomes(DRY_PINE_FOREST, FRESH_PINE_FOREST), perChunk(1), 1, l.feature(Species.JUNIPER.path(), 1));
		l.land(biomes(MIXED_PINE_FOREST, MIXED_FOREST), perChunk(1.5), 1, l.feature(Species.HAZEL.path(), 2),
				l.feature(Species.JUNIPER.path(), 1));
		l.land(biomes(OAK_HORNBEAM_FOREST, ELM_ASH_FOREST), perChunk(3), 1, l.feature(Species.HAZEL.path(), 1));
		l.land(biomes(ASH_ALDER_FOREST, UPLAND_FIR_FOREST), perChunk(1), 1, l.feature(Species.HAZEL.path(), 1));
		// Alder carr: no hazel on waterlogged peat; gray willow (Salix cinerea) as an osier.
		l.land(biomes(ALDER_CARR), perChunk(1.5), 1, l.feature(Species.OSIER.path(), 1));
		// Gray dune: creeping willow as an osier.
		l.land(biomes(GRAY_DUNE), 0.01F, 3, l.feature(Species.OSIER.path(), 1));
		return l.palette();
	}

	/**
	 * Waterside zones (§4, §8.2): cattail (small dripleaf) in water one block deep on mud, reed (sugar cane) on the bank
	 * shelf of the lowland waters (not by mountain streams: Phragmites has no place in the Carpathian stream vegetation;
	 * by the waters of pine and beech forests and heaths only in the shore reedbed patches the classifier marks, §4.4),
	 * sparsely by puddles of the micro-relief (sedges as the tall grass and large fern of the ground layer), osier in the
	 * willow scrub and willow carr, also seedlings on point bars and gravel bars, butterbur (big dripleaf, 1-2 blocks:
	 * Petasites is 0.3-1.2 m tall) on coarse dirt and rooted dirt of the gravel bars and on coarse dirt, rooted dirt and
	 * peat (mud) of the montane tall herbs and the gray alder and ash-alder forests, beach wrack in a line at the seaward
	 * edge of the strandline and sparse dry grass behind it, marram grass on the embryo dunes, moss on the floating mat
	 * and scrub on the cliff top.
	 */
	static PlantPalette watersideZones(HolderGetter<PlacedFeature> placed) {
		Layer l = new Layer(placed);
		Plant cattail = b(Blocks.SMALL_DRIPLEAF, 1);
		Plant reed = Plant.column(Blocks.SUGAR_CANE.defaultBlockState(), 2, 4, 1);
		HabitatBiome[] lowlandWater = {RIVER, LAKE, LAGOON, REEDBED, FEN, WET_MEADOW, WILLOW_SCRUB, ALDER_CARR, ASH_ALDER_FOREST,
				WILLOW_POPLAR_FOREST, ELM_ASH_FOREST};
		l.rule(HabitatCondition.zones(Zone.REEDBED, Zone.SHORE_REEDBED), Medium.WATER_BOTTOM, 1, 1, List.of(Ground.MUD), 0.6F, 3,
				cattail);
		l.rule(biomes(lowlandWater), Medium.WATER_BOTTOM, 1, 1, List.of(Ground.MUD), 0.3F, 3, cattail);
		// Eutrophic lowland waters: no pine forests, beech forests and heaths (oligotrophic lobelia lakes, §4.4).
		HabitatBiome[] reedBanks = {RIVER, LAKE, LAGOON, REEDBED, FEN, WET_MEADOW, WILLOW_SCRUB, HAY_MEADOW, ARABLE_LAND,
				ALDER_CARR, ASH_ALDER_FOREST, WILLOW_POPLAR_FOREST, ELM_ASH_FOREST, OAK_HORNBEAM_FOREST, MIXED_FOREST};
		// Lowland biomes for the reed of the waterside zones (the mountain biomes left out: Salicetum purpureae,
		// Petasitetum and Alnetum incanae of the mountain streams have no reed).
		HabitatBiome[] lowland = Arrays.stream(HabitatBiome.values()).filter(b -> !b.isWater() && !b.isMountain()
				&& b != GRAY_ALDER_FOREST && b != UPLAND_FIR_FOREST).toArray(HabitatBiome[]::new);
		l.rule(biomes(REEDBED).withZones(), Medium.SHORE, 1, 64, List.of(), 0.75F, 3, reed);
		l.rule(HabitatCondition.zones(Zone.SHORE_REEDBED, Zone.WILLOW_SCRUB, Zone.HERB_FRINGE, Zone.TALL_HERBS, Zone.WILLOW_CARR)
				.withBiomes(lowland), Medium.SHORE, 1, 64, List.of(), 0.7F, 3, reed);
		l.rule(biomes(reedBanks), Medium.SHORE, 1, 64, List.of(), 0.5F, 3, reed);
		// Puddles of the micro-relief: reed only here and there (the herb layer of an alder carr is mostly sedges, ferns
		// and iris: the ground layer's tall grass and large fern), more in the reedbed biome.
		l.rule(biomes(REEDBED), Medium.PUDDLE_SHORE, 1, 64, List.of(), 0.4F, 3, reed);
		l.rule(biomes(FEN, WET_MEADOW), Medium.PUDDLE_SHORE, 1, 64, List.of(), 0.15F, 3, reed);
		l.rule(biomes(reedBanks), Medium.PUDDLE_SHORE, 1, 64, List.of(), 0.1F, 3, reed);
		Plant osier = l.feature(Species.OSIER.path(), 9);
		Plant firefly = b(Blocks.FIREFLY_BUSH, 1);
		// 16% of the columns (S7 review: with 11% the crowns covered 71-78% of the willow scrub in the realistic scale and
		// 62% in the narrower scrub of the gameplay scale; §8.5: 80-100%).
		l.land(HabitatCondition.zones(Zone.WILLOW_SCRUB), 0.16F, 2, osier, firefly);
		l.land(biomes(WILLOW_SCRUB).withZones(Zone.NONE), 0.16F, 2, osier, firefly);
		l.land(HabitatCondition.zones(Zone.WILLOW_CARR), 0.08F, 2, osier, firefly);
		l.land(HabitatCondition.zones(Zone.POINT_BAR), 0.02F, 1, osier);
		Plant butterbur = Plant.column(Blocks.BIG_DRIPLEAF.defaultBlockState(), 1, 2, 1);
		l.land(HabitatCondition.zones(Zone.GRAVEL_BAR), List.of(Ground.DIRT), 0.5F, 1, butterbur);
		l.land(HabitatCondition.zones(Zone.GRAVEL_BAR), 0.05F, 1, osier);
		l.land(HabitatCondition.zones(Zone.MONTANE_TALL_HERBS), List.of(Ground.DIRT, Ground.MUD), 0.3F, 3, butterbur);
		l.land(biomes(GRAY_ALDER_FOREST), List.of(Ground.DIRT, Ground.MUD), 0.12F, 3, butterbur);
		l.land(biomes(ASH_ALDER_FOREST), List.of(Ground.DIRT, Ground.MUD), 0.04F, 3, butterbur);
		// Beach wrack in a line at the seaward edge of the strandline (2 blocks), sea rocket and sea sandwort as sparse
		// dry grass behind it.
		l.rule(HabitatCondition.zones(Zone.STRANDLINE), Medium.SEAWARD_EDGE, 1, 64, List.of(), 0.5F, 2,
				cat(litter(7), b(Blocks.SHORT_DRY_GRASS, 3)));
		l.land(HabitatCondition.zones(Zone.STRANDLINE), 0.08F, 3, b(Blocks.SHORT_DRY_GRASS, 3), b(Blocks.TALL_DRY_GRASS, 1));
		l.land(HabitatCondition.zones(Zone.EMBRYO_DUNE), 0.15F, 3, b(Blocks.SHORT_DRY_GRASS, 6), b(Blocks.TALL_DRY_GRASS, 4));
		l.land(HabitatCondition.zones(Zone.FLOATING_MAT), 0.6F, 3, b(Blocks.MOSS_CARPET, 7), b(Blocks.BUSH, 1));
		l.land(HabitatCondition.zones(Zone.CLIFF_TOP), 0.4F, 3, b(Blocks.BUSH, 6), b(Blocks.SHORT_GRASS, 4));
		return l.palette();
	}

	/**
	 * Ground layer (§8.6, §4.6): zones of tall herbs first, then one rule per biome. Cover in percent of the columns
	 * (coverage × weight share): pine forests moss carpet 30–70% (fresh) and pale moss carpet as lichens 50–80% (dry),
	 * dwarf shrubs 20–40%, bilberry, bracken 5–15% (mixed coniferous); oak-hornbeam forest leaf litter 30–60%, spring
	 * geophytes 10–30%, lily of the valley 3%, ferns 5%, grass at most 10%; beech forest leaf litter 70–90%; alder carr and
	 * floodplain forests tall grass and large fern 60–90%; heath heather; meadows grass and flowers; alpine grassland grass
	 * and dwarf shrubs.
	 */
	static PlantPalette groundLayer(HolderGetter<PlacedFeature> placed) {
		Layer l = new Layer(placed);
		Plant tallGrass = b(Blocks.TALL_GRASS, 1);
		Plant largeFern = b(Blocks.LARGE_FERN, 1);
		Plant fern = b(Blocks.FERN, 1);
		Plant grass = b(Blocks.SHORT_GRASS, 1);
		Plant bush = b(Blocks.BUSH, 1);
		Plant moss = b(Blocks.MOSS_CARPET, 1);
		Plant lichen = b(Blocks.PALE_MOSS_CARPET, 1);
		Plant dryGrass = b(Blocks.SHORT_DRY_GRASS, 1);
		l.land(HabitatCondition.zones(Zone.HERB_FRINGE, Zone.TALL_HERBS, Zone.MONTANE_TALL_HERBS, Zone.WILLOW_SCRUB,
				Zone.WILLOW_CARR, Zone.SPRING_AREA, Zone.SHORE_REEDBED, Zone.RIVERSIDE_WILLOWS, Zone.GRAVEL_BAR, Zone.POINT_BAR),
				0.85F, 3, tallGrass.withWeight(55), largeFern.withWeight(25), bush.withWeight(10), fern.withWeight(10));
		// Forest edges (step S8b, rule Z10): the fringe of tall herbs in front of the mantle (Trifolio-Geranietea and
		// nitrophilous fringes as tall grass, ferns and oxeye daisy, dog rose as rose bush; on sand dwarf shrubs and dry
		// grasses; by wet forests sedges and ferns as the tall grass and large fern).
		l.edge(biomes(HAY_MEADOW, ARABLE_LAND, ALPINE_GRASSLAND), Ecotone.Edge.FRINGE, 0.85F, 3, tallGrass.withWeight(45),
				largeFern.withWeight(10), fern.withWeight(8), bush.withWeight(8), b(Blocks.OXEYE_DAISY, 5), b(Blocks.ROSE_BUSH, 3),
				grass.withWeight(6));
		l.edge(biomes(HEATH, GRAY_DUNE), Ecotone.Edge.FRINGE, 0.75F, 3, bush.withWeight(30), b(Blocks.TALL_DRY_GRASS, 20),
				dryGrass.withWeight(15), tallGrass.withWeight(10), lichen.withWeight(10), grass.withWeight(5));
		l.edge(biomes(WET_MEADOW, FEN, RAISED_BOG, REEDBED), Ecotone.Edge.FRINGE, 0.85F, 3, tallGrass.withWeight(50),
				largeFern.withWeight(20), fern.withWeight(10), bush.withWeight(8));
		l.land(biomes(ALDER_CARR), 0.88F, 3, tallGrass.withWeight(40), largeFern.withWeight(30), fern.withWeight(12),
				bush.withWeight(8), b(Blocks.FIREFLY_BUSH, 3), grass.withWeight(4));
		l.land(biomes(ASH_ALDER_FOREST, GRAY_ALDER_FOREST), 0.88F, 3, tallGrass.withWeight(40), largeFern.withWeight(30),
				fern.withWeight(12), bush.withWeight(10), grass.withWeight(3));
		l.land(biomes(WILLOW_POPLAR_FOREST), 0.86F, 3, tallGrass.withWeight(55), largeFern.withWeight(20), bush.withWeight(12),
				fern.withWeight(5), grass.withWeight(4));
		l.land(biomes(ELM_ASH_FOREST), 0.88F, 3, cat(tallGrass.withWeight(45), largeFern.withWeight(25), fern.withWeight(8),
				litter(10), bed(Blocks.WILDFLOWERS, 4), grass.withWeight(2)));
		l.land(biomes(REEDBED), 0.85F, 3, tallGrass.withWeight(80), largeFern.withWeight(10), grass.withWeight(5));
		l.land(biomes(WILLOW_SCRUB), 0.8F, 3, tallGrass.withWeight(55), largeFern.withWeight(20), bush.withWeight(10));
		l.land(biomes(FEN), 0.75F, 3, tallGrass.withWeight(40), grass.withWeight(25), fern.withWeight(10), moss.withWeight(10),
				b(Blocks.ALLIUM, 3));
		l.land(biomes(WET_MEADOW), 0.85F, 3, tallGrass.withWeight(35), grass.withWeight(35), b(Blocks.ALLIUM, 4),
				b(Blocks.OXEYE_DAISY, 4), b(Blocks.DANDELION, 3));
		l.land(biomes(HAY_MEADOW), 0.85F, 3, grass.withWeight(50), tallGrass.withWeight(10), b(Blocks.DANDELION, 6),
				b(Blocks.POPPY, 4), b(Blocks.OXEYE_DAISY, 6), b(Blocks.CORNFLOWER, 4), b(Blocks.AZURE_BLUET, 3));
		// Fallow field (M2, fields with crops in M8): stubble as short dry grass, some grass, poppy and cornflower.
		l.land(biomes(ARABLE_LAND), 0.45F, 2, dryGrass.withWeight(24), grass.withWeight(12), b(Blocks.POPPY, 4),
				b(Blocks.CORNFLOWER, 4));
		l.land(biomes(HEATH), 0.75F, 4, cat(bed(Blocks.PINK_PETALS, 40), lichen.withWeight(12), grass.withWeight(15),
				dryGrass.withWeight(8), bush.withWeight(5)));
		l.land(biomes(RAISED_BOG), 0.5F, 3, cat(bush.withWeight(31), grass.withWeight(10),
				bed(Blocks.PINK_PETALS, 10)));
		l.land(biomes(BOG_WOODLAND), 0.85F, 4, moss.withWeight(45), bush.withWeight(35), fern.withWeight(5));
		l.land(biomes(DRY_PINE_FOREST), 0.75F, 4, lichen.withWeight(55), bush.withWeight(17), dryGrass.withWeight(8));
		l.land(biomes(FRESH_PINE_FOREST), 0.85F, 4, moss.withWeight(50), bush.withWeight(33), fern.withWeight(5));
		l.land(biomes(COASTAL_PINE_FOREST), 0.8F, 4, moss.withWeight(35), bush.withWeight(35), lichen.withWeight(10));
		l.land(biomes(MOIST_PINE_FOREST), 0.9F, 4, moss.withWeight(50), bush.withWeight(25), fern.withWeight(12),
				largeFern.withWeight(3));
		l.land(biomes(MIXED_PINE_FOREST), 0.85F, 4, cat(moss.withWeight(30), bush.withWeight(26), fern.withWeight(12),
				largeFern.withWeight(3), litter(15)));
		l.land(biomes(MIXED_FOREST), 0.8F, 4, cat(litter(40), moss.withWeight(12), fern.withWeight(8),
				bed(Blocks.WILDFLOWERS, 8), grass.withWeight(5), bush.withWeight(3)));
		l.land(biomes(OAK_HORNBEAM_FOREST), 0.85F, 4, cat(litter(45), bed(Blocks.WILDFLOWERS, 20),
				b(Blocks.LILY_OF_THE_VALLEY, 4), fern.withWeight(6), grass.withWeight(6)));
		l.land(biomes(LOWLAND_BEECH_FOREST), 0.85F, 4, cat(litter(85), fern.withWeight(2)));
		l.land(biomes(MONTANE_BEECH_FOREST), 0.85F, 4, cat(litter(75), fern.withWeight(6), bed(Blocks.WILDFLOWERS, 3)));
		l.land(biomes(UPLAND_FIR_FOREST), 0.8F, 4, cat(litter(40), moss.withWeight(20), fern.withWeight(12),
				bed(Blocks.WILDFLOWERS, 4)));
		l.land(biomes(MONTANE_SPRUCE_FOREST).withAssociations(Association.GULLY_TALL_HERBS), 0.8F, 3,
				tallGrass.withWeight(50), largeFern.withWeight(30));
		l.land(biomes(MONTANE_SPRUCE_FOREST), 0.85F, 4, moss.withWeight(45), fern.withWeight(15), bush.withWeight(20),
				largeFern.withWeight(5));
		l.land(biomes(DWARF_PINE_SCRUB), 0.6F, 3, moss.withWeight(25), bush.withWeight(20), fern.withWeight(8),
				grass.withWeight(10));
		l.land(biomes(ALPINE_GRASSLAND), 0.8F, 3, grass.withWeight(60), bush.withWeight(12), b(Blocks.AZURE_BLUET, 3),
				b(Blocks.ALLIUM, 2), fern.withWeight(3));
		l.land(biomes(WHITE_DUNE), 0.25F, 3, b(Blocks.TALL_DRY_GRASS, 5), dryGrass.withWeight(5));
		l.land(biomes(GRAY_DUNE), 0.55F, 3, dryGrass.withWeight(25), lichen.withWeight(25), bush.withWeight(10),
				grass.withWeight(15));
		return l.palette();
	}

	/**
	 * Aquatic plants (§4.4, §8.2): water lilies on the floating-leaved zone, pondweeds (seagrass, tall seagrass) in the
	 * submerged plants zone and in rivers, seagrass in the lagoon and sparsely in the sea.
	 */
	static PlantPalette aquaticPlants(HolderGetter<PlacedFeature> placed) {
		Layer l = new Layer(placed);
		Plant seagrass = b(Blocks.SEAGRASS, 1);
		Plant tall = b(Blocks.TALL_SEAGRASS, 1);
		l.rule(HabitatCondition.zones(Zone.FLOATING_LEAVED_PLANTS), Medium.WATER_SURFACE, 1, 4, List.of(), 0.45F, 4,
				b(Blocks.LILY_PAD, 1));
		l.rule(HabitatCondition.zones(Zone.SUBMERGED_PLANTS), Medium.WATER_BOTTOM, 2, 6, List.of(), 0.35F, 3,
				seagrass.withWeight(6), tall.withWeight(4));
		l.rule(HabitatCondition.zones(Zone.SUBMERGED_PLANTS), Medium.WATER_BOTTOM, 1, 1, List.of(), 0.3F, 3, seagrass);
		l.rule(biomes(RIVER), Medium.WATER_BOTTOM, 1, 6, List.of(), 0.12F, 3, seagrass.withWeight(8), tall.withWeight(2));
		l.rule(biomes(LAGOON), Medium.WATER_BOTTOM, 1, 4, List.of(), 0.15F, 3, seagrass.withWeight(8), tall.withWeight(2));
		l.rule(biomes(SEA), Medium.WATER_BOTTOM, 2, 10, List.of(), 0.04F, 4, seagrass);
		return l.palette();
	}

	/** Soils of the glacial erratics: the young glacial till and sands of the lowland (§8.1). */
	static final List<Soil> ERRATIC_SOILS = List.of(Soil.BROWN_SOIL, Soil.ACID_BROWN_SOIL, Soil.RUSTY_SOIL, Soil.PODZOL,
			Soil.INITIAL_PODZOL, Soil.BEECH_BROWN_SOIL);
	/**
	 * Greatest mountain influence P of the chunk of a glacial erratic: the Scandinavian ice sheet reached the foreland of
	 * the Carpathians but not the Beskids, whose brown soils match the soil list too (S7 review).
	 */
	static final float ERRATIC_MAX_MOUNTAIN_INFLUENCE = 0.3F;

	static ResourceKey<Feature> key(String path) {
		return ModTrees.key(path);
	}

	static HolderGetter<PlacedFeature> placedLookup(BootstrapContext<Feature> context) {
		return context.lookup(Registries.PLACED_FEATURE);
	}
}
