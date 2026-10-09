package pl.polishforests.client.datagen;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FeatureTags;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Feature tags (docs/03-m2-biomy.md §8.1, §8.6): the flowers of the bone meal carriers ({@code polishforests:flowers/*})
 * join {@code minecraft:can_spawn_from_bone_meal}, so bone meal on grass grows the flowers of the biome's group (the
 * vanilla bone meal picks among the features of the biome that are in this tag). Additive ({@code "replace": false}).
 */
final class ModFeatureTagProvider extends FabricTagsProvider<Feature> {
	ModFeatureTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, Registries.FEATURE, registries);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		var appender = builder(FeatureTags.CAN_SPAWN_FROM_BONE_MEAL);
		for (String group : ModVegetation.FLOWERS) {
			appender.add(ModTrees.key(ModVegetation.flowers(group)));
		}
	}

	@Override
	public String getName() {
		return "Polish Forests feature tags";
	}
}
