package pl.polishforests.client.datagen;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.data.BlockFamily;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.WoodBlocks;
import pl.polishforests.block.WoodSet;

/**
 * The block families of the wood sets (step P2) for the datagen: the planks with their stairs, slab, fence, fence gate,
 * pressure plate and button, the recipe group prefix {@code wooden} and the unlock {@code has_planks} as vanilla's wood
 * families. The models ({@code BlockModelGenerators.family}) and the recipes ({@code RecipeProvider.generateRecipes}) both
 * come from the family. Not registered in vanilla's {@code BlockFamilies} (only the stonecutter recipes read that map).
 */
final class ModWoodFamilies {
	private static final Map<WoodSet, BlockFamily> FAMILIES = new EnumMap<>(WoodSet.class);

	private ModWoodFamilies() {
	}

	static synchronized BlockFamily family(WoodSet set) {
		return FAMILIES.computeIfAbsent(set, s -> {
			WoodBlocks w = ModBlocks.wood(s);
			return new BlockFamily.Builder(w.planks()).button(w.button()).fence(w.fence()).fenceGate(w.fenceGate())
					.pressurePlate(w.pressurePlate()).slab(w.slab()).stairs(w.stairs()).recipeGroupPrefix("wooden")
					.recipeUnlockedBy("has_planks").getFamily();
		});
	}
}
