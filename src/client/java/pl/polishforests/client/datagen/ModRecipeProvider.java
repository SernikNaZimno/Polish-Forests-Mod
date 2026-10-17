package pl.polishforests.client.datagen;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.item.crafting.Recipe;
import pl.polishforests.block.ModBlocks;

/**
 * Recipes of the LITE Scots pine wood set (step V7, decision D1 (b)), as vanilla's wood recipes: four planks from one log,
 * wood or stripped log or wood ({@code #polishforests:scots_pine_full_logs}; the thin trunks taken with silk touch are
 * not in that tag, so a twig does not make four planks, V2-d), three wood from four logs and three stripped wood from four
 * stripped logs. Charcoal and fuel come with {@code #minecraft:logs_that_burn} and the item properties.
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
				planksFromLogs(ModBlocks.pinePlanks(), ModBlockTagProvider.item(ModBlockTagProvider.PINE_FULL_LOGS), 4);
				woodFromLogs(ModBlocks.pineWood(), ModBlocks.pineLog());
				woodFromLogs(ModBlocks.strippedPineWood(), ModBlocks.strippedPineLog());
			}
		};
	}

	@Override
	public String getName() {
		return "Polish Forests recipes";
	}
}
