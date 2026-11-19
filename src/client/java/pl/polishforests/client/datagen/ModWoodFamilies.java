package pl.polishforests.client.datagen;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.data.BlockFamily;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.WoodBlocks;
import pl.polishforests.block.WoodParts;
import pl.polishforests.block.WoodSet;

/**
 * The block families of the wood sets (step P2) for the datagen: the planks with their stairs, slab, fence, fence gate,
 * pressure plate and button, the recipe group prefix {@code wooden} and the unlock {@code has_planks} as vanilla's wood
 * families. The models ({@code BlockModelGenerators.family}) and the recipes ({@code RecipeProvider.generateRecipes}) both
 * come from the family. Not registered in vanilla's {@code BlockFamilies} (only the stonecutter recipes read that map).
 *
 * <p>Step FA7: the family of the models also has the door and the standing and wall sign (vanilla's family models); the
 * family of the recipes in addition the trapdoor, the hanging and wall hanging sign and the log and stripped log (the
 * hanging sign is made of the stripped log, as vanilla's). The trapdoor, the hanging signs and the shelf get their models
 * in {@code ModBlockModels} (the trapdoor turns with its facing or not by its vanilla base, the particle of the hanging
 * signs and the shelf is the side of the stripped log, which has no texture of its own name).
 */
final class ModWoodFamilies {
	private static final Map<WoodSet, BlockFamily> FAMILIES = new EnumMap<>(WoodSet.class);
	private static final Map<WoodSet, BlockFamily> RECIPES = new EnumMap<>(WoodSet.class);

	private ModWoodFamilies() {
	}

	/** The family of the models (and the planks products of P2). */
	static synchronized BlockFamily family(WoodSet set) {
		return FAMILIES.computeIfAbsent(set, s -> {
			WoodBlocks w = ModBlocks.wood(s);
			WoodParts parts = ModBlocks.woodParts(s);
			return products(w).door(parts.door()).sign(parts.sign(), parts.wallSign()).getFamily();
		});
	}

	/** The family of the recipes: every product of the planks and the stripped log of vanilla's wood families. */
	static synchronized BlockFamily recipes(WoodSet set) {
		return RECIPES.computeIfAbsent(set, s -> {
			WoodBlocks w = ModBlocks.wood(s);
			WoodParts parts = ModBlocks.woodParts(s);
			return products(w).log(w.log()).strippedLog(w.strippedLog()).door(parts.door()).trapdoor(parts.trapdoor())
					.sign(parts.sign(), parts.wallSign()).hangingSign(parts.hangingSign(), parts.wallHangingSign()).getFamily();
		});
	}

	private static BlockFamily.Builder products(WoodBlocks w) {
		return new BlockFamily.Builder(w.planks()).button(w.button()).fence(w.fence()).fenceGate(w.fenceGate())
				.pressurePlate(w.pressurePlate()).slab(w.slab()).stairs(w.stairs()).recipeGroupPrefix("wooden")
				.recipeUnlockedBy("has_planks");
	}
}
