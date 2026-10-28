package pl.polishforests.client.datagen;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchBlock;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.block.TrunkBlock;

/**
 * Loot tables of the blocks of the mod (step V2). A thin trunk (docs/04-pionowy-wycinek.md §1.1) gives itself with silk
 * touch; otherwise radius 3-7 one log of the species ({@link TreeTrunk#log()}: vanilla oak, birch and spruce logs, D2;
 * the closest vanilla log until the species has its own), radius 2 (a branch up to 25 cm) 1-2 sticks and radius 1 (a
 * twig) 0-1 stick. Step G1 (tree-felling mods, decisions G1-e and G1-f): an upright segment of radius 2 (parent below or
 * above: the stem of a young tree, which had given no log at all) gives a log too, and a twig at most one stick, so a
 * felled crown of 200-300 blocks does not shower hundreds of sticks. {@code pl.polishforests.compat.TreeWood#dropsLog}
 * follows these rules.
 */
final class ModBlockLoot extends FabricBlockLootSubProvider {
	/** Largest radius that gives sticks, px (an upright segment of this radius gives a log, G1-e). */
	static final int STICK_RADIUS = 2;

	ModBlockLoot(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	public void generate() {
		for (TreeTrunk t : TreeTrunk.values()) {
			trunk(ModBlocks.trunk(t), t);
		}
		// Step I1: a root flare as the trunk of its species; the underground root gives itself with silk touch, else 1-2
		// sticks.
		for (pl.polishforests.block.RootFlare f : pl.polishforests.block.RootFlare.values()) {
			trunk(ModBlocks.flare(f), f.trunk());
		}
		TrunkBlock root = ModBlocks.treeRoot();
		LootPoolEntryContainer.Builder<?> rootSticks = (LootPoolEntryContainer.Builder<?>) applyExplosionDecay(root,
				LootItem.lootTableItem(Items.STICK).apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 2))));
		add(root, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
				.add(LootItem.lootTableItem(root).when(hasSilkTouch()).otherwise(rootSticks))));
		lite();
		ground();
		// Round 1 of the V12 review: the own moss carpet gives itself, the mossy podzol itself with silk touch, else dirt (as
		// vanilla podzol).
		dropSelf(ModBlocks.forestMossCarpet());
		add(ModBlocks.mossyPodzol(), b -> createSingleItemTableWithSilkTouch(b, net.minecraft.world.level.block.Blocks.DIRT));
	}

	/**
	 * Step V12: the herb layer. The dwarf shrub gives itself with shears or silk touch, and its fruit when it has some
	 * (1-2, as picking it); a flower bed gives as many flowers as it has (as vanilla's wildflowers); the litter gives its
	 * segments (as vanilla's leaf litter); the tall fern gives itself with shears only.
	 */
	private void ground() {
		for (pl.polishforests.block.GroundPlant g : pl.polishforests.block.GroundPlant.values()) {
			Block block = ModBlocks.ground(g);
			switch (g.kind()) {
				case BERRY_SHRUB -> {
					LootItemCondition.Builder ripe = MatchBlock.blockMatches(blocks, block, StatePropertiesPredicate.Builder.properties()
							.hasProperty(pl.polishforests.block.BerryShrubBlock.BERRIES, true));
					add(block, LootTable.lootTable()
							.withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).when(hasShearsOrSilkTouch())
									.add(LootItem.lootTableItem(block)))
							.withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).when(ripe)
									.add((LootPoolEntryContainer.Builder<?>) applyExplosionDecay(block, LootItem.lootTableItem(ModBlocks.bilberries())
											.apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 2)))))));
				}
				case SEASONAL, COVER -> add(block, createSegmentedBlockDrops(block));
				case TALL -> add(block, createDoublePlantShearsDrop(block));
			}
		}
	}

	/**
	 * A thin trunk (or a root flare) with the loot of a trunk: itself with silk touch, 0-1 stick for a twig, 1-2 sticks for
	 * a branch of radius 2 that is not upright, else the log.
	 */
	private void trunk(TrunkBlock block, TreeTrunk t) {
		LootPoolEntryContainer.Builder<?> twig = ((LootPoolEntryContainer.Builder<?>) applyExplosionDecay(block,
				LootItem.lootTableItem(Items.STICK).apply(SetItemCountFunction.setCount(ContextIntProviders.between(0, 1)))))
				.when(radius(block, TrunkBlock.MIN_RADIUS));
		AnyOfCondition.Builder branch = AnyOfCondition.anyOf();
		for (int r = TrunkBlock.MIN_RADIUS + 1; r <= STICK_RADIUS; r++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				branch.or(MatchBlock.blockMatches(blocks, block, StatePropertiesPredicate.Builder.properties()
						.hasProperty(TrunkBlock.RADIUS, r).hasProperty(TrunkBlock.PARENT, d.getSerializedName())));
			}
		}
		LootPoolEntryContainer.Builder<?> sticks = ((LootPoolEntryContainer.Builder<?>) applyExplosionDecay(block,
				LootItem.lootTableItem(Items.STICK).apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 2)))))
				.when(branch);
		LootPoolEntryContainer.Builder<?> log = (LootPoolEntryContainer.Builder<?>) applyExplosionCondition(block,
				LootItem.lootTableItem(t.log()));
		add(block, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
				.add(LootItem.lootTableItem(block).when(hasSilkTouch()).otherwise(twig).otherwise(sticks).otherwise(log))));
	}

	private void lite() {
		// Step V7: the LITE Scots pine set drops itself; the leaves as vanilla leaves (shears or silk touch: the leaves,
		// else the sapling with vanilla's chances and sticks); a sapling itself, a pot its sapling and the pot.
		for (Block b : java.util.List.of(ModBlocks.pineLog(), ModBlocks.pineWood(), ModBlocks.strippedPineLog(),
				ModBlocks.strippedPineWood(), ModBlocks.pinePlanks())) {
			dropSelf(b);
		}
		for (TreeFoliage f : TreeFoliage.values()) {
			add(ModBlocks.leaves(f), createLeavesDrops(ModBlocks.leaves(f), ModBlocks.sapling(f), NORMAL_LEAVES_SAPLING_CHANCES));
			dropSelf(ModBlocks.sapling(f));
			dropPottedContents(ModBlocks.potted(f));
		}
	}

	private LootItemCondition.Builder radius(TrunkBlock block, int r) {
		return MatchBlock.blockMatches(blocks, block, StatePropertiesPredicate.Builder.properties().hasProperty(TrunkBlock.RADIUS, r));
	}
}
