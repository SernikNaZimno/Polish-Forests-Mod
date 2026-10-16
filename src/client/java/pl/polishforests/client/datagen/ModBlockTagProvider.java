package pl.polishforests.client.datagen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.BlockItemTagId;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Block and item tags of the blocks of the mod (step V2, docs/04-pionowy-wycinek.md §3 V2): every trunk is in
 * {@code #polishforests:<species>_logs}; the species block tags of oak, birch and spruce go into the vanilla
 * {@code #oak_logs}, {@code #birch_logs} and {@code #spruce_logs} (D2: the wood of these species is vanilla), the others
 * into {@code #logs_that_burn}, and through them into {@code #logs} (leaves do not decay next to a trunk, axes cut it,
 * heightmaps and movement treat it as a log). All trunks are also in {@code #overworld_natural_logs},
 * {@code #mineable/axe} and {@code #polishforests:trunks}.
 * <p>The items (a trunk taken with silk touch) get {@code #polishforests:<species>_logs} and {@code #polishforests:trunks},
 * and every species item tag goes into {@code #logs_that_burn} (fuel, charcoal), also for oak, birch and spruce: in the
 * vanilla {@code #oak_logs} items a twig of radius 1 (2 × 2 px) would make 4 planks like a whole log (V2 review,
 * choice V2-d); the planks come from the vanilla log a trunk drops without silk touch.
 */
final class ModBlockTagProvider {
	static final TagKey<Block> TRUNKS = TagKey.create(Registries.BLOCK, PolishForests.id("trunks"));

	private ModBlockTagProvider() {
	}

	/** The species tags of the trunks: {@code polishforests:<species>_logs}, in the order of {@link TreeTrunk}. */
	static Map<Species, TagKey<Block>> speciesTags() {
		Map<Species, TagKey<Block>> out = new LinkedHashMap<>();
		for (TreeTrunk t : TreeTrunk.values()) {
			out.computeIfAbsent(t.species(), s -> TagKey.create(Registries.BLOCK, PolishForests.id(s.id() + "_logs")));
		}
		return out;
	}

	/** The vanilla block tag a species tag goes into (the item tags all go into {@code #logs_that_burn}). */
	static BlockItemTagId vanillaParent(Species s) {
		return switch (s) {
			case OAK -> BlockItemTags.OAK_LOGS;
			case BIRCH -> BlockItemTags.BIRCH_LOGS;
			case SPRUCE -> BlockItemTags.SPRUCE_LOGS;
			default -> BlockItemTags.LOGS_THAT_BURN;
		};
	}

	static TagKey<Item> item(TagKey<Block> block) {
		return TagKey.create(Registries.ITEM, block.location());
	}

	static final class Blocks extends FabricTagsProvider.BlockTagsProvider {
		Blocks(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
			super(output, registries);
		}

		@Override
		protected void addTags(HolderLookup.Provider registries) {
			Map<Species, TagKey<Block>> species = speciesTags();
			for (TreeTrunk t : TreeTrunk.values()) {
				ResourceKey<Block> key = key(t);
				builder(species.get(t.species())).add(key);
				builder(TRUNKS).add(key);
				builder(BlockTags.OVERWORLD_NATURAL_LOGS).add(key);
				builder(BlockTags.MINEABLE_WITH_AXE).add(key);
			}
			species.forEach((s, tag) -> builder(vanillaParent(s).block()).addTag(tag));
		}
	}

	static final class Items extends FabricTagsProvider.ItemTagsProvider {
		Items(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries, Blocks blocks) {
			super(output, registries, blocks);
		}

		@Override
		protected void addTags(HolderLookup.Provider registries) {
			Map<Species, TagKey<Block>> species = speciesTags();
			species.forEach((s, tag) -> {
				copy(tag, item(tag));
				builder(BlockItemTags.LOGS_THAT_BURN.item()).addTag(item(tag));
			});
			copy(TRUNKS, item(TRUNKS));
		}
	}

	private static ResourceKey<Block> key(TreeTrunk t) {
		return BuiltInRegistries.BLOCK.getResourceKey(ModBlocks.trunk(t)).orElseThrow();
	}
}
