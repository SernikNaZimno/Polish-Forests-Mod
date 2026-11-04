package pl.polishforests.client.datagen;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.crafting.Recipe;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.WoodBlocks;
import pl.polishforests.block.WoodSet;

/**
 * Recipes of the wood sets (step V7, decision D1 (b): the LITE Scots pine set; step P2, decision U-8: the wood sets of
 * hornbeam, linden, beech, ash and Norway maple and the products of the planks of all of them), as vanilla's wood
 * recipes: four planks from one log, wood or stripped log or wood ({@code #polishforests:<wood>_full_logs}; the thin trunks
 * taken with silk touch are not in that tag, so a twig does not make four planks, V2-d), three wood from four logs and
 * three stripped wood from four stripped logs, and the recipes of vanilla's wood families from the planks (stairs, slab,
 * fence, fence gate, pressure plate, button; group {@code wooden_<variant>}, unlocked by having the planks). Charcoal and
 * fuel come with {@code #minecraft:logs_that_burn} and the item properties; sticks, crafting tables, chests … with
 * {@code #minecraft:planks}.
 */
final class ModRecipeProvider extends FabricRecipeProvider {
	ModRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, BootstrapContext<Recipe<?>> recipes,
			BootstrapContext<Advancement> advancements) {
		return new RecipeProvider(recipes, advancements) {
			@Override
			public void buildRecipes() {
				for (WoodSet set : WoodSet.values()) {
					WoodBlocks w = ModBlocks.wood(set);
					planksFromLogs(w.planks(), ModBlockTagProvider.item(ModBlockTagProvider.fullLogs(set)), 4);
					woodFromLogs(w.wood(), w.log());
					woodFromLogs(w.strippedWood(), w.strippedLog());
					generateRecipes(ModWoodFamilies.family(set), FeatureFlags.VANILLA_SET);
				}
			}
		};
	}

	@Override
	public String getName() {
		return "Polish Forests recipes";
	}
}
