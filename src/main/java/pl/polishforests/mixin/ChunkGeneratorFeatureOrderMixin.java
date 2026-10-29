package pl.polishforests.mixin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.worldgen.chunk.ModBiomeKeys;
import pl.polishforests.worldgen.chunk.PolandBiomeSource;
import pl.polishforests.worldgen.feature.FeatureOrder;

/**
 * The feature order of a generator with the "Poland" biome source ({@link FeatureOrder}, step J2): the features of the
 * habitat biomes keep their indices of before the cave biomes, the cave biomes' features and the underground features of
 * the worlds with caves come after them in each step. The target is the body of the memoized supplier of
 * {@code ChunkGenerator.featuresPerStep} (built in the constructor from the biome source and the settings getter).
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorFeatureOrderMixin {
	@Inject(method = "lambda$new$1", at = @At("HEAD"), cancellable = true)
	private static void polishforests$habitatFeaturesFirst(BiomeSource source, Function<Holder<Biome>, BiomeGenerationSettings> settings,
			CallbackInfoReturnable<List<FeatureSorter.StepFeatureData>> cir) {
		if (source instanceof PolandBiomeSource) {
			List<Holder<Biome>> biomes = List.copyOf(source.possibleBiomes());
			Set<Holder<Biome>> habitats = new HashSet<>();
			for (Holder<Biome> b : biomes) {
				if (b.unwrapKey().map(ModBiomeKeys.all()::contains).orElse(false)) {
					habitats.add(b);
				}
			}
			cir.setReturnValue(FeatureOrder.build(biomes, habitats, b -> settings.apply(b).features()));
		}
	}
}
