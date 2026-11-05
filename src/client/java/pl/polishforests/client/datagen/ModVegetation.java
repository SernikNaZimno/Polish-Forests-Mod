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
import pl.polishforests.block.BerryShrubBlock;
import pl.polishforests.block.GroundCoverBlock;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.worldgen.feature.DeadwoodFeature;
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
import pl.polishforests.worldgen.tree.SpeciesShape;
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

	/**
	 * Species of the M2 fallen trees (vanilla logs), {@code polishforests:deadwood_substitute/<species>} since step V11
	 * (decision PW-3 b for dead wood: the forests outside the slice keep them).
	 */
	static final List<Species> DEADWOOD = List.of(Species.SCOTS_PINE, Species.SPRUCE, Species.FIR, Species.BIRCH, Species.OAK,
			Species.BEECH, Species.HORNBEAM, Species.BLACK_ALDER, Species.POPLAR, Species.WHITE_WILLOW, Species.GRAY_ALDER);

	/**
	 * Species of the dead wood of the dead wood planner (step V11): every species with a planner shape,
	 * {@code polishforests:deadwood/<species>} (a dead tree) and {@code polishforests:stump/<species>}.
	 */
	static final List<Species> PROCEDURAL_DEADWOOD = List.copyOf(SpeciesShape.species());

	/** A dead tree of the dead wood planner (step V11; until V11 the vanilla fallen tree, now {@link #deadwoodSubstitute}). */
	static String deadwood(Species s) {
		return "deadwood/" + s.id();
	}

	static String deadwoodSubstitute(Species s) {
		return "deadwood_substitute/" + s.id();
	}

	/** A stump of the dead wood planner (managed stands, step V11). */
	static String stump(Species s) {
		return "stump/" + s.id();
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
			context.register(ModTrees.key(deadwoodSubstitute(s)), fallen(s));
		}
		for (Species s : PROCEDURAL_DEADWOOD) {
			context.register(ModTrees.key(deadwood(s)), new DeadwoodFeature(s, false));
			context.register(ModTrees.key(stump(s)), new DeadwoodFeature(s, true));
		}
		// Step V12: wood anemone and liverleaf (own blocks) besides the wildflowers (yellow anemone) and lily of the valley.
		context.register(ModTrees.key(flowers("forest")), flowers(List.of(
				segments(ModBlocks.ground(GroundPlant.WOOD_ANEMONE), FlowerBedBlock.AMOUNT, 1, 4, 4),
				segments(ModBlocks.ground(GroundPlant.HEPATICA), FlowerBedBlock.AMOUNT, 1, 4, 2),
				segments(Blocks.WILDFLOWERS, FlowerBedBlock.AMOUNT, 1, 4, 2), single(Blocks.LILY_OF_THE_VALLEY, 2))));
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
		// The forests with the trees of the tree planner (the vertical slice, step V9 of docs/04-pionowy-wycinek.md, PW-3,
		// and the forests of the steps F): procedural trees in the layered
		// stand of the stand map (canopy, managed canopy, subcanopy, regeneration under the canopy and in canopy gaps) with
		// the chance of a candidate to grow a tree by stand (managed, natural: pole stage, mature, old growth; in a gap),
		// calibrated with the norms card (§6: N1 trunks per chunk, N2 layers, N3 composition). Fresh pine forest: pine with
		// birch, spruce in the subcanopy in the north-east (range flag; elsewhere oak), the pine regenerates only in gaps.
		t.planned(HabitatCondition.biomes(FRESH_PINE_FOREST), 11).add(Species.SCOTS_PINE, 850).add(Species.BIRCH, 120)
				.add(Species.SPRUCE, 30)
				.managed().add(Species.SCOTS_PINE, 880).add(Species.BIRCH, 100).add(Species.SPRUCE, 20)
				.subcanopy().alt(Species.SPRUCE, 600, Species.OAK).add(Species.BIRCH, 200).add(Species.OAK, 200)
				.regeneration().alt(Species.SPRUCE, 300, Species.OAK).add(Species.OAK, 300).add(Species.ROWAN, 250)
				.add(Species.BIRCH, 150)
				.gapRegeneration().add(Species.SCOTS_PINE, 600).add(Species.BIRCH, 400)
				.chances(new float[] {0.93F, 0.70F, 0.60F}, new float[] {0.80F, 0.80F, 0.80F}, 0.6F);
		t.rule(HabitatCondition.biomes(COASTAL_PINE_FOREST), 9).add(Species.SCOTS_PINE, 900).add(Species.BIRCH, 100);
		t.rule(HabitatCondition.biomes(MOIST_PINE_FOREST), 10).add(Species.SCOTS_PINE, 650).add(Species.BIRCH, 250)
				.add(Species.SPRUCE, 100);
		t.rule(HabitatCondition.biomes(BOG_WOODLAND), 6).add(Species.SCOTS_PINE, ModTrees.SCOTS_PINE_LOW, 700)
				.add(Species.BIRCH, 300);
		// The belt between them (PW-3): mixed pine-oak and mixed forest with the trees of the slice.
		t.planned(HabitatCondition.biomes(MIXED_PINE_FOREST), 10).add(Species.SCOTS_PINE, 550).add(Species.OAK, 250)
				.add(Species.BIRCH, 100).alt(Species.BEECH, 100, Species.SPRUCE)
				.managed().add(Species.SCOTS_PINE, 650).add(Species.OAK, 200).add(Species.BIRCH, 80).alt(Species.BEECH, 70, Species.SPRUCE)
				.subcanopy().add(Species.OAK, 400).add(Species.BIRCH, 200).alt(Species.SPRUCE, 300, Species.OAK)
				.alt(Species.BEECH, 100, Species.SPRUCE)
				.regeneration().add(Species.OAK, 300).alt(Species.SPRUCE, 300, Species.OAK).add(Species.ROWAN, 200)
				.add(Species.BIRCH, 100).add(Species.HAZEL, 100)
				.gapRegeneration().add(Species.SCOTS_PINE, 400).add(Species.BIRCH, 300).add(Species.OAK, 300)
				.chances(new float[] {0.90F, 0.68F, 0.58F}, new float[] {0.85F, 0.85F, 0.85F}, 0.7F);
		t.planned(HabitatCondition.biomes(MIXED_FOREST), 9).add(Species.OAK, 400).add(Species.SCOTS_PINE, 300)
				.alt(Species.BEECH, 150, Species.SPRUCE).alt(Species.HORNBEAM, 100, Species.LINDEN).add(Species.BIRCH, 50)
				.managed().add(Species.OAK, 350).add(Species.SCOTS_PINE, 400).alt(Species.BEECH, 120, Species.SPRUCE)
				.alt(Species.HORNBEAM, 80, Species.LINDEN).add(Species.BIRCH, 50)
				.subcanopy().alt(Species.HORNBEAM, 500, Species.LINDEN).alt(Species.SPRUCE, 250, Species.OAK).add(Species.LINDEN, 150)
				.alt(Species.BEECH, 100, Species.SPRUCE)
				.regeneration().alt(Species.HORNBEAM, 400, Species.LINDEN).add(Species.ROWAN, 150).add(Species.HAZEL, 250)
				.alt(Species.SPRUCE, 200, Species.LINDEN)
				.gapRegeneration().add(Species.OAK, 250).add(Species.SCOTS_PINE, 200).add(Species.BIRCH, 250).add(Species.HAZEL, 300)
				.chances(new float[] {0.88F, 0.70F, 0.60F}, new float[] {0.92F, 0.92F, 0.92F}, 0.8F);
		// Oak-hornbeam forest: natural stands two-storied (a1 oak, linden, hornbeam (review of V9, round 1: the name-giving
		// tree of Tilio-Carpinetum, about a fifth of the a1 trunks), maple and ash, beech in the west and spruce in the
		// north-east by their range flags; a2 hornbeam with linden), managed ones dominated by oak; regeneration of hornbeam
		// and linden, hazel denser in the gaps; no oak in the regeneration (N6).
		t.planned(HabitatCondition.biomes(OAK_HORNBEAM_FOREST), 12).add(Species.OAK, 300).add(Species.LINDEN, 180)
				.add(Species.NORWAY_MAPLE, 60).add(Species.ASH, 60).add(Species.BEECH, 150).add(Species.SPRUCE, 130)
				.alt(Species.HORNBEAM, 160, Species.LINDEN)
				.managed().add(Species.OAK, 650).add(Species.LINDEN, 120).add(Species.NORWAY_MAPLE, 40).add(Species.ASH, 40)
				.add(Species.BEECH, 80).add(Species.SPRUCE, 70)
				.subcanopy().alt(Species.HORNBEAM, 700, Species.LINDEN).add(Species.LINDEN, 300)
				.regeneration().alt(Species.HORNBEAM, 500, Species.LINDEN).add(Species.LINDEN, 300).add(Species.NORWAY_MAPLE, 100)
				.add(Species.HAZEL, 100)
				.gapRegeneration().alt(Species.HORNBEAM, 350, Species.LINDEN).add(Species.LINDEN, 250).add(Species.NORWAY_MAPLE, 100)
				.add(Species.ASH, 100).add(Species.HAZEL, 150)
				.chances(new float[] {0.85F, 0.72F, 0.62F}, new float[] {1.0F, 1.0F, 1.0F}, 0.9F);
		// Lowland beech forest (step F1, docs/04 §6 card of the beech forest, research/06 §4.3): a1 beech 70–100% (the
		// biome already lies within the beech range, O and P, so beech keeps its full weight there; with the ramp a beech
		// forest at the range edge had 40% oak, S7 review), a weak a2 (the subcanopy at a third of the chance: the beech
		// shades it out), beech regenerating under the canopy and in the gaps of the fallen trees and the nests of the managed
		// stands (the stand map's beech profile), managed stands single-storied with fewer admixtures. The acid beech forest
		// (Luzulo pilosae-Fagetum, the moderately fertile site) with oak, pine and birch; the fertile one (Galio odorati-
		// Fagetum) with oak, ash, Norway maple and linden (sycamore from step F3). The chances stay at or below the highest
		// ones of the slice forests (TreeStandPlan.surelyTreeless is habitat-free, so a higher chance would move the crown
		// markers of the slice forests too).
		t.planned(HabitatCondition.biomes(LOWLAND_BEECH_FOREST).withSiteTypes(ForestSiteType.FRESH_MIXED_BROADLEAVED), 12)
				.full(Species.BEECH, 820).add(Species.OAK, 120).add(Species.SCOTS_PINE, 30).add(Species.BIRCH, 30)
				.managed().full(Species.BEECH, 870).add(Species.OAK, 90).add(Species.SCOTS_PINE, 20).add(Species.BIRCH, 20)
				.subcanopy().full(Species.BEECH, 800).alt(Species.HORNBEAM, 100, Species.BIRCH).add(Species.BIRCH, 100)
				.regeneration().full(Species.BEECH, 850).add(Species.ROWAN, 150)
				.gapRegeneration().full(Species.BEECH, 650).add(Species.BIRCH, 150).add(Species.ROWAN, 100).add(Species.OAK, 100)
				.chances(new float[] {0.90F, 0.72F, 0.62F}, new float[] {0.95F, 0.95F, 0.95F}, 0.8F).subcanopyChance(0.35F);
		t.planned(HabitatCondition.biomes(LOWLAND_BEECH_FOREST), 12)
				.full(Species.BEECH, 800).add(Species.OAK, 70).add(Species.ASH, 40).add(Species.NORWAY_MAPLE, 45)
				.add(Species.LINDEN, 45)
				.managed().full(Species.BEECH, 860).add(Species.OAK, 60).add(Species.ASH, 25).add(Species.NORWAY_MAPLE, 30)
				.add(Species.LINDEN, 25)
				.subcanopy().full(Species.BEECH, 600).alt(Species.HORNBEAM, 250, Species.LINDEN).add(Species.LINDEN, 150)
				.regeneration().full(Species.BEECH, 800).add(Species.NORWAY_MAPLE, 100).alt(Species.HORNBEAM, 100, Species.LINDEN)
				.gapRegeneration().full(Species.BEECH, 650).add(Species.ASH, 100).add(Species.NORWAY_MAPLE, 100)
				.add(Species.HAZEL, 150)
				.chances(new float[] {0.90F, 0.72F, 0.62F}, new float[] {0.95F, 0.95F, 0.95F}, 0.8F).subcanopyChance(0.35F);
		// Step F2 (docs/04 "Projekt F2", research/06 §4.5-4.6, research 08). Alder carr (Ribeso nigri-Alnetum on the
		// eutrophic peat, Ol): a1 black alder 80-100% with birch and some ash on the drier tussocks, a weak a2 of alder and
		// birch, an open canopy (N4 60-80%), the alder regenerating on the tussocks and in the gaps with birch and alder
		// buckthorn; managed stands an even-aged alder stand (the stand map's carr profile). The sphagnum carr (Sphagno
		// squarrosi-Alnetum on the poorer peat, the boggy mixed broadleaved site type) with more birch and some pine.
		// Stage 2 of F2: the natural carr counted 5.0 trees per chunk in the game (N1 8-16; its gaps, hollows and the
		// tussocks take columns), so the natural chance went from 0.78 to 0.95 and the subcanopy chance from 0.4 to 0.6,
		// the managed chances from 0.85 / 0.65 / 0.55 to those of the ash-alder forest (N1 7.3-7.8 in the game).
		t.planned(HabitatCondition.biomes(ALDER_CARR).withSiteTypes(ForestSiteType.BOGGY_MIXED_BROADLEAVED), 9)
				.add(Species.BLACK_ALDER, 800).add(Species.BIRCH, 170).add(Species.SCOTS_PINE, 30)
				.managed().add(Species.BLACK_ALDER, 860).add(Species.BIRCH, 130).add(Species.SCOTS_PINE, 10)
				.subcanopy().add(Species.BLACK_ALDER, 500).add(Species.BIRCH, 500)
				.regeneration().add(Species.BLACK_ALDER, 350).add(Species.BIRCH, 350).add(Species.ALDER_BUCKTHORN, 300)
				.gapRegeneration().add(Species.BLACK_ALDER, 400).add(Species.BIRCH, 400).add(Species.ALDER_BUCKTHORN, 200)
				.chances(new float[] {0.88F, 0.70F, 0.60F}, new float[] {0.92F, 0.92F, 0.92F}, 0.6F).subcanopyChance(0.6F);
		t.planned(HabitatCondition.biomes(ALDER_CARR), 9)
				.add(Species.BLACK_ALDER, 880).add(Species.BIRCH, 80).add(Species.ASH, 40)
				.managed().add(Species.BLACK_ALDER, 930).add(Species.BIRCH, 50).add(Species.ASH, 20)
				.subcanopy().add(Species.BLACK_ALDER, 650).add(Species.BIRCH, 350)
				.regeneration().add(Species.BLACK_ALDER, 450).add(Species.BIRCH, 200).add(Species.ALDER_BUCKTHORN, 200)
				.add(Species.BIRD_CHERRY, 150)
				.gapRegeneration().add(Species.BLACK_ALDER, 550).add(Species.BIRCH, 300).add(Species.ALDER_BUCKTHORN, 150)
				.chances(new float[] {0.88F, 0.70F, 0.60F}, new float[] {0.95F, 0.95F, 0.95F}, 0.6F).subcanopyChance(0.6F);
		// Ash-alder floodplain forest (Fraxino-Alnetum, OlJ): a1 black alder 50-90% with ash 10-40%, elm and, in the
		// north-east by its range flag, spruce (elsewhere alder); a2 of elm, alder and ash, a closed canopy (N4 70-90%); the
		// bird cherry in the shrub layer (20-50%, the understory and the regeneration) with hazel; the ash regenerating
		// well in the gaps of the ash dieback.
		t.planned(HabitatCondition.biomes(ASH_ALDER_FOREST), 10)
				.add(Species.BLACK_ALDER, 620).add(Species.ASH, 250).add(Species.ELM, 70).alt(Species.SPRUCE, 60, Species.BLACK_ALDER)
				.managed().add(Species.BLACK_ALDER, 700).add(Species.ASH, 220).add(Species.ELM, 40)
				.alt(Species.SPRUCE, 40, Species.BLACK_ALDER)
				.subcanopy().add(Species.ELM, 400).add(Species.BLACK_ALDER, 300).add(Species.ASH, 200)
				.alt(Species.SPRUCE, 100, Species.ELM)
				.regeneration().add(Species.ASH, 250).add(Species.BLACK_ALDER, 200).add(Species.ELM, 150)
				.add(Species.BIRD_CHERRY, 300).add(Species.HAZEL, 100)
				.gapRegeneration().add(Species.BLACK_ALDER, 300).add(Species.ASH, 250).add(Species.BIRD_CHERRY, 250)
				.add(Species.ELM, 100).add(Species.BIRCH, 100)
				.chances(new float[] {0.88F, 0.70F, 0.60F}, new float[] {0.90F, 0.90F, 0.90F}, 0.8F).subcanopyChance(0.6F);
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

	/**
	 * Builder of the tree palette rules: a {@link #rule} with the placeholder trees of M2 ({@code tree_substitute/}, PW-3),
	 * or a {@link #planned} rule with the procedural trees ({@code tree/<species>} where the species has a planner shape) and
	 * the layered stand, whose lists follow {@link #managed}, {@link #subcanopy}, {@link #regeneration} and
	 * {@link #gapRegeneration}.
	 */
	private static final class Trees {
		private final HolderGetter<PlacedFeature> placed;
		private final List<TreePalette.Rule> rules = new ArrayList<>();
		private HabitatCondition condition;
		private float perChunk;
		private boolean planned;
		private float subcanopyChance;
		private List<TreePalette.Entry> entries;
		private List<TreePalette.Entry> managed;
		private List<TreePalette.Entry> subcanopy;
		private List<TreePalette.Entry> regeneration;
		private List<TreePalette.Entry> gapRegeneration;
		private List<TreePalette.Entry> target;
		private float[] managedChance;
		private float[] naturalChance;
		private float gapChance;

		Trees(HolderGetter<PlacedFeature> placed) {
			this.placed = placed;
		}

		Trees rule(HabitatCondition condition, float perChunk) {
			flush();
			this.condition = condition;
			this.perChunk = perChunk;
			this.planned = false;
			this.entries = new ArrayList<>();
			this.target = entries;
			return this;
		}

		/** A rule of the tree planner: procedural trees in a layered stand (the slice forests of step V9, the steps F). */
		Trees planned(HabitatCondition condition, float perChunk) {
			rule(condition, perChunk);
			planned = true;
			subcanopyChance = 1;
			managed = new ArrayList<>();
			subcanopy = new ArrayList<>();
			regeneration = new ArrayList<>();
			gapRegeneration = new ArrayList<>();
			return this;
		}

		Trees managed() {
			target = managed;
			return this;
		}

		Trees subcanopy() {
			target = subcanopy;
			return this;
		}

		Trees regeneration() {
			target = regeneration;
			return this;
		}

		Trees gapRegeneration() {
			target = gapRegeneration;
			return this;
		}

		/** Factor on the chance of the subcanopy's candidates (step F1: a weak a2 layer). */
		Trees subcanopyChance(float factor) {
			subcanopyChance = factor;
			return this;
		}

		Trees chances(float[] managed, float[] natural, float gap) {
			managedChance = managed;
			naturalChance = natural;
			gapChance = gap;
			return this;
		}

		/** The feature path of a species in this rule: the procedural tree in a planned rule, else the placeholder. */
		private String path(Species s) {
			return !planned ? s.substitutePath() : SpeciesShape.supported(s) ? s.path() : ModTrees.SLICE.getOrDefault(s, s.substitutePath());
		}

		Trees add(Species s, int weight) {
			return add(s, path(s), weight, 0);
		}

		Trees add(Species s, int weight, int maxWaterDepth) {
			return add(s, path(s), weight, maxWaterDepth);
		}

		Trees add(Species s, String tree, int weight) {
			return add(s, tree, weight, 0);
		}

		Trees add(Species s, String tree, int weight, int maxWaterDepth) {
			target.add(new TreePalette.Entry(s, tree(tree), weight, Optional.empty(), maxWaterDepth));
			return this;
		}

		Trees alt(Species s, int weight, Species alternative) {
			target.add(new TreePalette.Entry(s, tree(path(s)), weight,
					Optional.of(new TreePalette.Alternative(alternative, tree(path(alternative)))), 0));
			return this;
		}

		/** A species with its full weight, without the range ramp (a biome that already lies within its range). */
		Trees full(Species s, int weight) {
			target.add(new TreePalette.Entry(s, tree(path(s)), weight, Optional.empty(), 0, false));
			return this;
		}

		private Holder<PlacedFeature> tree(String path) {
			return placed.getOrThrow(ModWorldgen.placed(path));
		}

		private void flush() {
			if (condition != null) {
				Optional<TreePalette.Stand> stand = Optional.empty();
				if (planned) {
					stand = Optional.of(new TreePalette.Stand(List.copyOf(managed), List.copyOf(subcanopy), List.copyOf(regeneration),
							List.copyOf(gapRegeneration), floats(managedChance), floats(naturalChance), gapChance, subcanopyChance));
				}
				rules.add(new TreePalette.Rule(condition, perChunk, List.copyOf(entries), stand));
				condition = null;
			}
		}

		List<TreePalette.Rule> rules() {
			flush();
			return rules;
		}

		private static List<Float> floats(float[] values) {
			return List.of(values[0], values[1], values[2]);
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

		/** A land rule with rounded patches (step V12, {@link pl.polishforests.worldgen.feature.plan.ColumnPlan.PatchShape#BLOBS}). */
		Layer blobs(HabitatCondition c, float coverage, int patch, Plant... plants) {
			return blobs(c, coverage, patch, plants, new Plant[0], null);
		}

		/**
		 * A land rule with rounded patches of the clumped plants and the scattered plants drawn per column (round 1 of the
		 * V12 review: the plants without a mossy underside in the pine forests, whose patches read as plates of bare podzol),
		 * and the ground the podzol under its plants without a mossy underside turns into (null: none; rounds 1 and 2 of
		 * the V12 review, {@link PlantPalette.Rule#plantGround}).
		 */
		Layer blobs(HabitatCondition c, float coverage, int patch, Plant[] clumped, Plant[] scattered, BlockState plantGround) {
			return blobs(c, coverage, patch, clumped, scattered, plantGround, false);
		}

		/**
		 * As {@link #blobs(HabitatCondition, float, int, Plant[], Plant[], BlockState)}; {@code allColumns}: the ground also
		 * on every podzol column the rule claims, with or without a plant (step V13, {@link PlantPalette.Rule#plantGroundColumns}).
		 */
		Layer blobs(HabitatCondition c, float coverage, int patch, Plant[] clumped, Plant[] scattered, BlockState plantGround,
				boolean allColumns) {
			rules.add(new PlantPalette.Rule(c, Medium.LAND, 1, 64, List.of(), coverage, patch, List.of(clumped), List.of(),
					pl.polishforests.worldgen.feature.plan.ColumnPlan.PatchShape.BLOBS, List.of(scattered),
					java.util.Optional.ofNullable(plantGround), allColumns));
			return this;
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

	/** Bilberry (step V12), with or without the mossy underside (PW-7). */
	private static Plant bilberry(int weight, boolean mossy) {
		return Plant.block(ModBlocks.ground(GroundPlant.BILBERRY).defaultBlockState().setValue(BerryShrubBlock.MOSSY, mossy), weight);
	}

	/** Scots pine needle litter of 2–4 segments (step V12), with or without the mossy underside. */
	private static Plant[] needles(int weight, boolean mossy) {
		BlockState s = ModBlocks.ground(GroundPlant.NEEDLE_LITTER).defaultBlockState().setValue(GroundCoverBlock.MOSSY, mossy);
		return new Plant[] {Plant.block(s.setValue(LeafLitterBlock.AMOUNT, 2), weight / 3),
				Plant.block(s.setValue(LeafLitterBlock.AMOUNT, 3), weight / 3),
				Plant.block(s.setValue(LeafLitterBlock.AMOUNT, 4), weight - 2 * (weight / 3))};
	}

	/**
	 * The moss floor of the pine forests (step V12, PW-7): one segment of needle litter over the moss layer (the own feather
	 * moss, duller than the vanilla moss carpet, V0 review), so the mossy columns with and without dwarf shrubs look alike.
	 */
	private static Plant mossFloor(int weight) {
		return Plant.block(ModBlocks.ground(GroundPlant.NEEDLE_LITTER).defaultBlockState().setValue(GroundCoverBlock.MOSSY, true)
				.setValue(LeafLitterBlock.AMOUNT, 1), weight);
	}

	/** Wood anemone beds of 2–4 flowers (step V12). */
	private static Plant[] anemone(int weight) {
		return bed(ModBlocks.ground(GroundPlant.WOOD_ANEMONE), weight);
	}

	/** Liverleaf tufts of 2–4 flowers (step V12). */
	private static Plant[] hepatica(int weight) {
		return bed(ModBlocks.ground(GroundPlant.HEPATICA), weight);
	}

	/** Sweet woodruff beds of 2–4 whorls (step F1). */
	private static Plant[] sweetWoodruff(int weight) {
		return bed(ModBlocks.ground(GroundPlant.SWEET_WOODRUFF), weight);
	}

	/** Hairy wood-rush tufts of 2–4 (step F1). */
	private static Plant[] hairyWoodRush(int weight) {
		return bed(ModBlocks.ground(GroundPlant.HAIRY_WOOD_RUSH), weight);
	}

	/** Wood sorrel beds of 2–4 trefoil clumps (step F1). */
	private static Plant[] woodSorrel(int weight) {
		return bed(ModBlocks.ground(GroundPlant.WOOD_SORREL), weight);
	}

	/** Coralroot beds of 2–4 shoots (step F1). */
	private static Plant[] coralroot(int weight) {
		return bed(ModBlocks.ground(GroundPlant.CORALROOT), weight);
	}

	/** Marsh marigold rosettes of 2–4 (step F2). */
	private static Plant[] marigold(int weight) {
		return bed(ModBlocks.ground(GroundPlant.MARSH_MARIGOLD), weight);
	}

	/** A herb of one block of step F2 (iris, tussock sedge, marsh fern, nettle). */
	private static Plant herb(GroundPlant g, int weight) {
		return Plant.block(ModBlocks.ground(g).defaultBlockState(), weight);
	}

	/** Bracken, two blocks (step V12). */
	private static Plant bracken(int weight) {
		return Plant.block(ModBlocks.ground(GroundPlant.BRACKEN).defaultBlockState(), weight);
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

	/**
	 * Deadwood (§8.2). The forests of the tree planner (the slice, step V11, decision PW-5 b; the steps F): the dead trees
	 * of the dead wood planner as a
	 * natural stand has them (reserves) and the stumps of a managed stand, by species weight; at each pick the stand map
	 * decides ({@code DeadwoodFeature}: no stumps in a natural stand, an eighth of the dead trees in a managed one). The
	 * other forests: the M2 fallen trees of the stand's species, 0.15–0.5 per chunk.
	 */
	static PlantPalette deadwood(HolderGetter<PlacedFeature> placed) {
		Layer l = new Layer(placed);
		// Oak-hornbeam forest: about 2 dead wood objects per chunk in a natural stand (BPN), 1-2 stumps in a managed one.
		l.land(biomes(OAK_HORNBEAM_FOREST), deadwoodCoverage(OAK_HORNBEAM_FOREST), 1,
				l.feature(deadwood(Species.OAK), 8), l.feature(deadwood(Species.HORNBEAM), 6), l.feature(deadwood(Species.LINDEN), 4),
				l.feature(deadwood(Species.ASH), 2), l.feature(stump(Species.OAK), stumpWeight(OAK_HORNBEAM_FOREST, 20, 0.72)),
				l.feature(stump(Species.HORNBEAM), stumpWeight(OAK_HORNBEAM_FOREST, 20, 0.14)), l.feature(stump(Species.LINDEN), stumpWeight(OAK_HORNBEAM_FOREST, 20, 0.1)),
				l.feature(stump(Species.ASH), stumpWeight(OAK_HORNBEAM_FOREST, 20, 0.04)));
		// Fresh pine forest: about 1 per chunk in a natural stand (Taborz), 1-2 stumps in a managed one.
		l.land(biomes(FRESH_PINE_FOREST), deadwoodCoverage(FRESH_PINE_FOREST), 1,
				l.feature(deadwood(Species.SCOTS_PINE), 9), l.feature(deadwood(Species.BIRCH), 1),
				l.feature(stump(Species.SCOTS_PINE), stumpWeight(FRESH_PINE_FOREST, 10, 0.94)), l.feature(stump(Species.BIRCH), stumpWeight(FRESH_PINE_FOREST, 10, 0.06)));
		// Mixed pine and mixed forests between them (no norms; between the two).
		l.land(biomes(MIXED_PINE_FOREST), deadwoodCoverage(MIXED_PINE_FOREST), 1,
				l.feature(deadwood(Species.SCOTS_PINE), 7), l.feature(deadwood(Species.OAK), 3),
				l.feature(stump(Species.SCOTS_PINE), stumpWeight(MIXED_PINE_FOREST, 10, 0.8)), l.feature(stump(Species.OAK), stumpWeight(MIXED_PINE_FOREST, 10, 0.2)));
		l.land(biomes(MIXED_FOREST), deadwoodCoverage(MIXED_FOREST), 1, l.feature(deadwood(Species.OAK), 5),
				l.feature(deadwood(Species.SCOTS_PINE), 3), l.feature(deadwood(Species.BEECH), 2),
				l.feature(stump(Species.SCOTS_PINE), stumpWeight(MIXED_FOREST, 10, 0.5)), l.feature(stump(Species.OAK), stumpWeight(MIXED_FOREST, 10, 0.4)),
				l.feature(stump(Species.BEECH), stumpWeight(MIXED_FOREST, 10, 0.1)));
		l.land(biomes(DRY_PINE_FOREST, COASTAL_PINE_FOREST, MOIST_PINE_FOREST), perChunk(0.3), 1,
				l.feature(deadwoodSubstitute(Species.SCOTS_PINE), 9), l.feature(deadwoodSubstitute(Species.BIRCH), 1));
		l.land(biomes(BOG_WOODLAND), perChunk(0.25), 1, l.feature(deadwoodSubstitute(Species.BIRCH), 6),
				l.feature(deadwoodSubstitute(Species.SCOTS_PINE), 4));
		// Lowland beech forest (step F1, docs/04 §6 N10): the natural stands at the level of the reserves (L 06 §4.3, as the
		// natural oak-hornbeam forest), the managed ones by the WISL means; the beech with its admixtures.
		l.land(biomes(LOWLAND_BEECH_FOREST), deadwoodCoverage(LOWLAND_BEECH_FOREST), 1, l.feature(deadwood(Species.BEECH), 16),
				l.feature(deadwood(Species.OAK), 2), l.feature(deadwood(Species.ASH), 1), l.feature(deadwood(Species.NORWAY_MAPLE), 1),
				l.feature(stump(Species.BEECH), stumpWeight(LOWLAND_BEECH_FOREST, 20, 0.85)),
				l.feature(stump(Species.OAK), stumpWeight(LOWLAND_BEECH_FOREST, 20, 0.1)),
				l.feature(stump(Species.ASH), stumpWeight(LOWLAND_BEECH_FOREST, 20, 0.05)));
		l.land(biomes(MONTANE_BEECH_FOREST), perChunk(0.5), 1, l.feature(deadwoodSubstitute(Species.BEECH), 8),
				l.feature(deadwoodSubstitute(Species.FIR), 2));
		l.land(biomes(UPLAND_FIR_FOREST), perChunk(0.45), 1, l.feature(deadwoodSubstitute(Species.FIR), 6),
				l.feature(deadwoodSubstitute(Species.BEECH), 4));
		l.land(biomes(MONTANE_SPRUCE_FOREST), perChunk(0.5), 1, l.feature(deadwoodSubstitute(Species.SPRUCE), 1));
		// Step F2 (docs/04 "Projekt F2", N10): the alder carr with its alders and birches (the soft wood breaks and rots
		// fast in the wet), the ash-alder forest with the alders, the ashes of the ash dieback and the elms of the Dutch elm
		// disease; the stumps of the managed alder stands.
		l.land(biomes(ALDER_CARR), deadwoodCoverage(ALDER_CARR), 1, l.feature(deadwood(Species.BLACK_ALDER), 14),
				l.feature(deadwood(Species.BIRCH), 4), l.feature(deadwood(Species.ASH), 2),
				l.feature(stump(Species.BLACK_ALDER), stumpWeight(ALDER_CARR, 20, 0.9)),
				l.feature(stump(Species.BIRCH), stumpWeight(ALDER_CARR, 20, 0.08)),
				l.feature(stump(Species.ASH), stumpWeight(ALDER_CARR, 20, 0.02)));
		l.land(biomes(ASH_ALDER_FOREST), deadwoodCoverage(ASH_ALDER_FOREST), 1, l.feature(deadwood(Species.BLACK_ALDER), 10),
				l.feature(deadwood(Species.ASH), 7), l.feature(deadwood(Species.ELM), 3),
				l.feature(stump(Species.BLACK_ALDER), stumpWeight(ASH_ALDER_FOREST, 20, 0.7)),
				l.feature(stump(Species.ASH), stumpWeight(ASH_ALDER_FOREST, 20, 0.25)),
				l.feature(stump(Species.ELM), stumpWeight(ASH_ALDER_FOREST, 20, 0.05)));
		// Gray alder forest: the gray bark of its gray alders (pale oak, §8.4), some spruce.
		l.land(biomes(GRAY_ALDER_FOREST), perChunk(0.3), 1, l.feature(deadwoodSubstitute(Species.GRAY_ALDER), 8),
				l.feature(deadwoodSubstitute(Species.SPRUCE), 2));
		l.land(biomes(WILLOW_POPLAR_FOREST), perChunk(0.35), 1, l.feature(deadwoodSubstitute(Species.POPLAR), 5),
				l.feature(deadwoodSubstitute(Species.WHITE_WILLOW), 5));
		l.land(biomes(ELM_ASH_FOREST), perChunk(0.3), 1, l.feature(deadwoodSubstitute(Species.OAK), 1));
		return l.palette();
	}

	/**
	 * Dead tree picks per chunk of the palettes of the forests of the tree planner as a natural stand has them (the slice:
	 * oak-hornbeam, fresh pine, mixed pine and mixed forest; step F1: the lowland beech forest), calibrated so the census of
	 * a natural stand counts the norms of N10 (a broken tree is two objects; step V11, {@code StandCensusSimulationTest})
	 * [S]. Each step F adds its forest.
	 */
	static final Map<HabitatBiome, Double> DEAD_TREES = Map.of(OAK_HORNBEAM_FOREST, 2.1, FRESH_PINE_FOREST, 0.92,
			MIXED_PINE_FOREST, 1.3, MIXED_FOREST, 1.7, LOWLAND_BEECH_FOREST, 1.8, ALDER_CARR, 2.5, ASH_ALDER_FOREST, 1.8);
	/** Stumps per chunk of a managed stand of the forests of the tree planner (WISL: 30-80 per ha, 1-2 per chunk) [S]. */
	static final double STUMPS = 1.5;

	/** Coverage of a dead wood rule of a forest of the tree planner: its dead trees and its stumps per chunk. */
	private static float deadwoodCoverage(HabitatBiome forest) {
		return perChunk(deadTrees(forest) + STUMPS);
	}

	private static double deadTrees(HabitatBiome forest) {
		Double n = DEAD_TREES.get(forest);
		if (n == null) {
			throw new IllegalArgumentException("no dead trees per chunk for " + forest);
		}
		return n;
	}

	/**
	 * Weight of a species' stumps in a palette whose dead trees weigh {@code deadWeight} in all: the stumps of the forest
	 * ({@link #STUMPS}) against its dead trees ({@link #DEAD_TREES}) times the species' share of the stumps.
	 */
	private static int stumpWeight(HabitatBiome forest, int deadWeight, double share) {
		return Math.max(1, (int) Math.round(deadWeight * STUMPS / deadTrees(forest) * share));
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
		// spruce forests (hazel only by the fresh and mixed pine forests, not on the poor sands of the dry and coastal pine
		// forests; round 1 of the S8b review), gray and eared willow (osier) by the alder carrs and willow-poplar forests,
		// with hazel by the ash-alder and gray alder forests; at the edge of the bog woodland towards the raised bog stunted
		// pines and birches, thinning out into the bog (round 1 of the S8b review: the willows belong to the lagg).
		// The forests of the vertical slice (review of V9, round 1): a denser mantle of thin-stemmed hazels, taller ones
		// of 3-5 blocks along the edge, so the forest edge seen from a field is a wall of shrubs, not a colonnade of bare
		// boles; juniper and alder buckthorn by the pine forests.
		l.edge(biomes(OAK_HORNBEAM_FOREST, MIXED_FOREST), Ecotone.Edge.MANTLE, 0.35F, 1, l.feature(ModTrees.HAZEL_MANTLE, 2),
				l.feature(ModTrees.SLICE.get(Species.HAZEL), 1));
		l.edge(biomes(FRESH_PINE_FOREST, MIXED_PINE_FOREST), Ecotone.Edge.MANTLE, 0.3F, 1,
				l.feature(ModTrees.SLICE.get(Species.JUNIPER), 2), l.feature(ModTrees.HAZEL_MANTLE, 1),
				l.feature(ModTrees.SLICE.get(Species.ALDER_BUCKTHORN), 1));
		// Lowland beech forest (step F1): a narrow mantle of the thin-stemmed hazels of the slice (the beech shades its edge;
		// the forests of the tree planner without the M2 shrubs at their edges).
		l.edge(biomes(LOWLAND_BEECH_FOREST), Ecotone.Edge.MANTLE, 0.2F, 1, l.feature(ModTrees.HAZEL_MANTLE, 1),
				l.feature(ModTrees.SLICE.get(Species.HAZEL), 2));
		l.edge(biomes(ELM_ASH_FOREST, UPLAND_FIR_FOREST, MONTANE_BEECH_FOREST), Ecotone.Edge.MANTLE, 0.06F,
				1, l.feature(Species.HAZEL.path(), 1));
		l.edge(biomes(DRY_PINE_FOREST, COASTAL_PINE_FOREST, MOIST_PINE_FOREST, MONTANE_SPRUCE_FOREST), Ecotone.Edge.MANTLE, 0.05F,
				1, l.feature(Species.JUNIPER.path(), 1));
		// Step F2: the mantles of the alder forests of the tree planner, the thin-stemmed shrubs of the slice: bird cherry and
		// hazel by the ash-alder forest, alder buckthorn and the gray willow (the M2 osier) by the alder carr.
		l.edge(biomes(ASH_ALDER_FOREST), Ecotone.Edge.MANTLE, 0.25F, 1, l.feature(ModTrees.SLICE.get(Species.BIRD_CHERRY), 2),
				l.feature(ModTrees.SLICE.get(Species.HAZEL), 1), l.feature(Species.OSIER.path(), 1));
		l.edge(biomes(ALDER_CARR), Ecotone.Edge.MANTLE, 0.2F, 1, l.feature(ModTrees.SLICE.get(Species.ALDER_BUCKTHORN), 2),
				l.feature(Species.OSIER.path(), 1));
		l.edge(biomes(GRAY_ALDER_FOREST), Ecotone.Edge.MANTLE, 0.06F, 1, l.feature(Species.OSIER.path(), 3),
				l.feature(Species.HAZEL.path(), 1));
		l.edge(biomes(WILLOW_POPLAR_FOREST), Ecotone.Edge.MANTLE, 0.06F, 1, l.feature(Species.OSIER.path(), 1));
		l.edge(biomes(BOG_WOODLAND), Ecotone.Edge.MANTLE, 0.02F, 1, l.feature(ModTrees.SCOTS_PINE_LOW, 2),
				l.feature(Species.BIRCH.substitutePath(), 1));
		// About 13 dwarf pines of 21–37 columns each per chunk: the crowns cover about 70% of the ground.
		l.land(biomes(DWARF_PINE_SCRUB), 0.05F, 1, l.feature(Species.DWARF_MOUNTAIN_PINE.path(), 1));
		l.land(biomes(HEATH), 0.01F, 3, l.feature(Species.JUNIPER.path(), 1));
		l.land(biomes(DRY_PINE_FOREST), perChunk(1), 1, l.feature(Species.JUNIPER.path(), 1));
		// Fresh pine forest (step V9, N7: shrub layer 5-30%): juniper, alder buckthorn and rowan.
		// The slice (review of V9, round 1): the thin-stemmed placeholders.
		l.land(biomes(FRESH_PINE_FOREST), perChunk(5), 1, l.feature(ModTrees.SLICE.get(Species.JUNIPER), 2),
				l.feature(ModTrees.SLICE.get(Species.ALDER_BUCKTHORN), 2), l.feature(ModTrees.SLICE.get(Species.ROWAN), 1));
		l.land(biomes(MIXED_PINE_FOREST, MIXED_FOREST), perChunk(1.5), 1, l.feature(ModTrees.SLICE.get(Species.HAZEL), 2),
				l.feature(ModTrees.SLICE.get(Species.JUNIPER), 1));
		l.land(biomes(OAK_HORNBEAM_FOREST), perChunk(3), 1, l.feature(ModTrees.SLICE.get(Species.HAZEL), 1));
		l.land(biomes(ELM_ASH_FOREST), perChunk(3), 1, l.feature(Species.HAZEL.path(), 1));
		l.land(biomes(UPLAND_FIR_FOREST), perChunk(1), 1, l.feature(Species.HAZEL.path(), 1));
		// Step F2 (N7: the shrub layer 20-50%): the ash-alder forest with the bird cherry (Pruno-Fraxinetum) and some hazel;
		// the alder carr: no hazel on waterlogged peat, the alder buckthorn, the bird cherry on the tussocks and the gray
		// willow (Salix cinerea) as the M2 osier.
		l.land(biomes(ASH_ALDER_FOREST), perChunk(7), 1, l.feature(ModTrees.SLICE.get(Species.BIRD_CHERRY), 4),
				l.feature(ModTrees.SLICE.get(Species.HAZEL), 1));
		l.land(biomes(ALDER_CARR), perChunk(7), 1, l.feature(ModTrees.SLICE.get(Species.ALDER_BUCKTHORN), 3),
				l.feature(ModTrees.SLICE.get(Species.BIRD_CHERRY), 1), l.feature(Species.OSIER.path(), 1));
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
		// Step F2: the hollows of the alder forests, by the water and the puddles of the micro-relief: the yellow iris and the
		// marsh marigold with some sedge, by the water also the reed (before the reed rules of the eutrophic waters).
		Plant[] hollow = cat(herb(GroundPlant.YELLOW_IRIS, 6), marigold(5), herb(GroundPlant.TUSSOCK_SEDGE, 3));
		l.rule(biomes(ALDER_CARR, ASH_ALDER_FOREST), Medium.SHORE, 1, 64, List.of(), 0.8F, 3, cat(hollow, reed.withWeight(4)));
		l.rule(biomes(reedBanks), Medium.SHORE, 1, 64, List.of(), 0.5F, 3, reed);
		// Puddles of the micro-relief: reed only here and there (the herb layer of an alder carr is mostly sedges, ferns
		// and iris: the ground layer's tall grass and large fern), more in the reedbed biome.
		l.rule(biomes(ALDER_CARR, ASH_ALDER_FOREST), Medium.PUDDLE_SHORE, 1, 64, List.of(), 0.8F, 3, hollow);
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
	 * geophytes 10–30%, lily of the valley 3%, ferns 5%, grass at most 10%; lowland beech forest leaf litter 60–90% with its
	 * own herb layer (step F1); alder carr and
	 * floodplain forests tall grass and large fern 60–90%; heath heather; meadows grass and flowers; alpine grassland grass
	 * and dwarf shrubs. The forests of the vertical slice (fresh and mixed coniferous, mixed and oak-hornbeam forests) have
	 * the own herb layer blocks and rounded patches of step V12 (docs/04 §6 N8, N9), with the shares at their rules.
	 */
	static PlantPalette groundLayer(HolderGetter<PlacedFeature> placed) {
		Layer l = new Layer(placed);
		Plant tallGrass = b(Blocks.TALL_GRASS, 1);
		Plant largeFern = b(Blocks.LARGE_FERN, 1);
		Plant fern = b(Blocks.FERN, 1);
		Plant grass = b(Blocks.SHORT_GRASS, 1);
		Plant bush = b(Blocks.BUSH, 1);
		Plant moss = b(Blocks.MOSS_CARPET, 1);
		// The own moss carpet of the broadleaved forests of the slice (round 1 of the V12 review: the vanilla one read as
		// yellow-olive rugs); the other habitats keep the vanilla moss until M4.
		Plant forestMoss = b(ModBlocks.forestMossCarpet(), 1);
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
		l.edge(biomes(WET_MEADOW, FEN, REEDBED), Ecotone.Edge.FRINGE, 0.85F, 3, tallGrass.withWeight(50),
				largeFern.withWeight(20), fern.withWeight(10), bush.withWeight(8));
		// The raised bog at the bog woodland: dwarf shrubs (marsh Labrador tea, bog bilberry, heather) on peat moss (round 1
		// of the S8b review).
		l.edge(biomes(RAISED_BOG), Ecotone.Edge.FRINGE, 0.75F, 3, cat(bush.withWeight(45), moss.withWeight(15),
				bed(Blocks.PINK_PETALS, 10)));
		// Step F2 (docs/04 "Projekt F2", N8 60-90% and 70-100%, N9): the own herb layer of the alder forests in rounded
		// patches. The alder carr (Ribeso nigri-Alnetum): the sedges of the tussocks (Carex elongata, C. elata), the marsh
		// fern, the yellow iris and the marsh marigold (more of them by the water and the puddles, the waterside zones), the
		// nettle on the drier tussocks, the reed grass as tall grass; moss in cushions scattered per column (on the tussocks,
		// TreeMoss). The sphagnum carr (Sphagno squarrosi-Alnetum, the boggy mixed broadleaved site type): the sphagnum as
		// the vanilla moss carpet (own sphagnum from step F4), sedges and the marsh fern. The ash-alder forest (Fraxino-
		// Alnetum): the nettle, the sedges (Carex remota), the marsh marigold, the wood anemone (also for the wood stitchwort,
		// a white spring flower of the same stands), the enchanter's nightshade as the vanilla fern. Stage 2 of F2: the herb
		// layer of the card (N8) was 40-55% in the game, so the patches cover more; the short grass and fewer flower beds
		// of the ash-alder forest, since the density rule of docs/03 §4.6 (short grass and small flowers at most 10% of the
		// floodplain forests) failed on its stream banks (24% and 18%). Review of F2, round 1: the alder forests are back
		// in the palette check of §4.6 (GroundLayerPlanTest), so the sphagnum carr has less moss and more sedges and marsh
		// fern (herbs 63% of the columns, before 46%).
		l.blobs(biomes(ALDER_CARR).withSiteTypes(ForestSiteType.BOGGY_MIXED_BROADLEAVED), 0.97F, 5, cat(moss.withWeight(20),
				herb(GroundPlant.TUSSOCK_SEDGE, 30), herb(GroundPlant.MARSH_FERN, 22), marigold(4), fern.withWeight(4)),
				cat(forestMoss.withWeight(6)), null);
		l.blobs(biomes(ALDER_CARR), 0.97F, 5, cat(herb(GroundPlant.TUSSOCK_SEDGE, 26), herb(GroundPlant.MARSH_FERN, 18),
				herb(GroundPlant.YELLOW_IRIS, 8), marigold(7), herb(GroundPlant.NETTLE, 7), tallGrass.withWeight(6),
				b(Blocks.FIREFLY_BUSH, 2)), cat(forestMoss.withWeight(8)), null);
		l.blobs(biomes(ASH_ALDER_FOREST), 0.98F, 5, cat(herb(GroundPlant.NETTLE, 24), herb(GroundPlant.TUSSOCK_SEDGE, 16),
				marigold(4), anemone(4), herb(GroundPlant.MARSH_FERN, 6), fern.withWeight(12), tallGrass.withWeight(6),
				litter(4)), cat(forestMoss.withWeight(4)), null);
		l.land(biomes(GRAY_ALDER_FOREST), 0.88F, 3, tallGrass.withWeight(40), largeFern.withWeight(30),
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
		// Dry pine forest: lichen, dwarf shrubs and dry grass in rounded patches with the bare sand and podzol scattered per
		// column (round 2 of the V12 review: its square M2 cells read as grey and orange plates in the mixed coniferous
		// forest of the gameplay transect, and the palette reaches it within the ecotone belt).
		l.blobs(biomes(DRY_PINE_FOREST), 0.75F, 4, lichen.withWeight(55), bush.withWeight(17), dryGrass.withWeight(8));
		// The forests of the vertical slice (step V12, docs/04 §6 N8 and N9, decisions PW-6 and PW-7): own blocks, rounded
		// patches (no square patches of litter and bare ground, CP-B), the ground nearly covered; the palette shares below are
		// before the trees (the census measures about 0.87 of them in the pine forest, 0.7 in the oak-hornbeam forest).
		// Fresh pine forest: bilberry about half of the columns, all of it over moss (round 1 of the V12 review: patches of
		// bilberry on bare podzol read as orange plates), the moss floor (a segment of needles over the own moss layer),
		// needle litter over moss (a bare mat of needles read as bare red ground, CP-B); fern, grass and bracken (2% each)
		// without a mossy underside scattered per column, not in patches, on the mossy podzol (on bare podzol they read
		// as orange squares); moss with the mossy underside about 91% of the columns, the herb layer about 54%.
		l.blobs(biomes(FRESH_PINE_FOREST), 0.97F, 6, cat(bilberry(48, true), mossFloor(26), needles(17, true)),
				cat(fern.withWeight(2), grass.withWeight(2), bracken(2)), ModBlocks.mossyPodzol().defaultBlockState());
		l.land(biomes(COASTAL_PINE_FOREST), 0.8F, 4, moss.withWeight(35), bush.withWeight(35), lichen.withWeight(10));
		l.land(biomes(MOIST_PINE_FOREST), 0.9F, 4, moss.withWeight(50), bush.withWeight(25), fern.withWeight(12),
				largeFern.withWeight(3));
		// Mixed coniferous forest: less moss, leaf litter of the oaks, bracken as its indicator (about 10%, in stands whose
		// fronds hide the ground); the bilberry over moss, fern, grass and the oak litter scattered per column on the
		// mossy podzol (round 1 of the V12 review), the bracken stands on the mossy podzol too (round 2: its stands on the
		// podzol of the rusty soil read as orange plates). Step V13 (finding 3 of the V12 review): the mossy podzol on every
		// podzol column of the rule, also the columns left without a plant, which still showed bare plates of 3 x 2 to 4 x 3.
		l.blobs(biomes(MIXED_PINE_FOREST), 0.93F, 6, cat(mossFloor(16), bilberry(26, true), bracken(8), needles(10, true)),
				cat(litter(4), fern.withWeight(6), grass.withWeight(3)), ModBlocks.mossyPodzol().defaultBlockState(), true);
		// Mixed forest: leaf litter at most as much as in the oak-hornbeam forest (the order of the transect indicators,
		// N15), wood anemone and liverleaf as in the broadleaved forests; the moss on the ground in small cushions scattered
		// per column (round 1 of the V12 review: in patches it read as square rugs); the herb layer and the litter stand on
		// the turf, not on the podzol patches of the acid brown soil (round 2 of the V12 review: they read as orange plates
		// under the anemone beds, grass and litter; mossy podzol would break the transect moss order, N15).
		// Step V13 (finding 3 of the V12 review): the turf on every podzol column of the rule, also without a plant.
		l.blobs(biomes(MIXED_FOREST), 0.9F, 5, cat(litter(5), anemone(14), hepatica(3), fern.withWeight(8), grass.withWeight(5),
				bilberry(4, false)), cat(forestMoss.withWeight(10)), Blocks.GRASS_BLOCK.defaultBlockState(), true);
		// Oak-hornbeam forest: wood anemone in carpets (spring herb layer about 88%, without it about 38%: the summer one),
		// liverleaf in tufts, lily of the valley, ferns and sedges (as short grass), leaf litter about 10%, moss on the
		// ground little, scattered per column (more at the bases of the trunks, TreeMoss; the own moss carpet since round 1
		// of the V12 review); the turf of the brown soil on top, so the plants also stand on turf where the palette of the
		// transect belt reaches the podzol patches of the mixed forest's soil (round 2 of the V12 review).
		l.blobs(biomes(OAK_HORNBEAM_FOREST), 0.98F, 5, cat(anemone(44), hepatica(10), b(Blocks.LILY_OF_THE_VALLEY, 4),
				fern.withWeight(11), grass.withWeight(9), litter(9)), cat(forestMoss.withWeight(2)), Blocks.GRASS_BLOCK.defaultBlockState());
		// Lowland beech forest (step F1, docs/04 §6 N8, N9; L 06 §3.6): the beech leaf litter in rounded patches on about
		// two thirds of the rule's columns (60–90%, the "buczyna naga" where the herb layer is thin), the herb layer between
		// them; the moss in small cushions scattered per column (more at the bases of the trunks, TreeMoss). The acid beech
		// forest (Luzulo pilosae-Fagetum): hairy wood-rush, wood sorrel, bilberry in patches, wavy hair-grass as short grass
		// and ferns, about a quarter of the columns, more moss; the fertile one (Galio odorati-Fagetum): sweet woodruff,
		// wood anemone, coralroot and wood sorrel, about a third of the columns in spring, on the turf of the mull.
		l.blobs(biomes(LOWLAND_BEECH_FOREST).withSiteTypes(ForestSiteType.FRESH_MIXED_BROADLEAVED), 0.97F, 5,
				cat(litter(64), hairyWoodRush(10), woodSorrel(5), bilberry(6, false), fern.withWeight(2), grass.withWeight(3)),
				cat(forestMoss.withWeight(7)), null);
		l.blobs(biomes(LOWLAND_BEECH_FOREST), 0.97F, 5, cat(litter(62), sweetWoodruff(12), anemone(8), coralroot(4), woodSorrel(6),
				fern.withWeight(2)), cat(forestMoss.withWeight(3)), Blocks.GRASS_BLOCK.defaultBlockState());
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
