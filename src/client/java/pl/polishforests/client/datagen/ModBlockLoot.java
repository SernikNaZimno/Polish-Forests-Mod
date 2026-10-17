package pl.polishforests.client.datagen;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
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
 * touch; otherwise radius 1-2 (a twig or branch up to 25 cm) gives 1-2 sticks and radius 3-7 one log of the species
 * ({@link TreeTrunk#log()}: vanilla oak, birch and spruce logs, D2; the closest vanilla log until the species has its own).
 */
final class ModBlockLoot extends FabricBlockLootSubProvider {
	/** Largest radius that gives sticks, px. */
	static final int STICK_RADIUS = 2;

	ModBlockLoot(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	public void generate() {
		for (TreeTrunk t : TreeTrunk.values()) {
			TrunkBlock block = ModBlocks.trunk(t);
			AnyOfCondition.Builder thin = AnyOfCondition.anyOf();
			for (int r = TrunkBlock.MIN_RADIUS; r <= STICK_RADIUS; r++) {
				thin.or(radius(block, r));
			}
			LootPoolEntryContainer.Builder<?> sticks = ((LootPoolEntryContainer.Builder<?>) applyExplosionDecay(block,
					LootItem.lootTableItem(Items.STICK).apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 2)))))
					.when(thin);
			LootPoolEntryContainer.Builder<?> log = (LootPoolEntryContainer.Builder<?>) applyExplosionCondition(block,
					LootItem.lootTableItem(t.log()));
			add(block, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
					.add(LootItem.lootTableItem(block).when(hasSilkTouch()).otherwise(sticks).otherwise(log))));
		}
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
