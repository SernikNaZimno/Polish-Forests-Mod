package pl.polishforests.client.datagen;

import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;

/**
 * Data generator of the mod (docs/03-m2-biomy.md §11, step S5): the 36 biomes, the features and placed features of the
 * decoration, the biome tags (vanilla, structures, Serene Seasons, {@code polishforests:*}) and the language files, all
 * from the enums of the habitat package (Z8). The output lives in {@code src/main/generated} in the repository;
 * {@code ./gradlew checkDatagen} runs {@code runDatagen} and fails when the files change.
 *
 * <p>Fabric datagen 26.3 works for {@code Registries.BIOME}, {@code Registries.FEATURE} and
 * {@code Registries.PLACED_FEATURE} (risk R1: trial on one biome and one feature at the start of S5), so the fallback
 * plan (a JSON template decoded with a codec in a bootstrap) is not needed. Vanilla elements may be referenced but not
 * read during the bootstrap (their values are unbound), so the tree features are built here instead of copied.
 */
public final class PolishForestsDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator generator) {
		FabricDataGenerator.Pack pack = generator.createPack();
		pack.addProvider(WorldgenProvider::new);
		pack.addProvider(ModBiomeTagProvider::new);
		pack.addProvider(ModFeatureTagProvider::new);
		pack.addProvider(ModLanguageProvider.English::new);
		pack.addProvider(ModLanguageProvider.Polish::new);
	}

	@Override
	public void buildRegistry(RegistrySetBuilder builder) {
		builder.add(Registries.FEATURE, ModWorldgen::features);
		builder.add(Registries.PLACED_FEATURE, ModWorldgen::placedFeatures);
		builder.add(Registries.BIOME, ModWorldgen::biomes);
	}

	/** Writes the mod's entries of the worldgen registries built in {@link #buildRegistry}. */
	private static final class WorldgenProvider extends FabricDynamicRegistryProvider {
		WorldgenProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
			super(output, registries);
		}

		@Override
		protected void configure(HolderLookup.Provider registries, Entries entries) {
			entries.addAll(registries.lookupOrThrow(Registries.FEATURE));
			entries.addAll(registries.lookupOrThrow(Registries.PLACED_FEATURE));
			entries.addAll(registries.lookupOrThrow(Registries.BIOME));
		}

		@Override
		public String getName() {
			return "Polish Forests worldgen";
		}
	}
}
