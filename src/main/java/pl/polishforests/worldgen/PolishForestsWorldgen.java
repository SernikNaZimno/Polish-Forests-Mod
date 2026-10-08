package pl.polishforests.worldgen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.chunk.PolandBiomeSource;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.ModFeatures;

/** Registers the world generation types: generator, biome source, vegetation dispatcher types and the chunk attachment. */
public final class PolishForestsWorldgen {
	private PolishForestsWorldgen() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, PolishForests.id("poland"), PolandChunkGenerator.CODEC);
		Registry.register(BuiltInRegistries.BIOME_SOURCE, PolishForests.id("poland"), PolandBiomeSource.CODEC);
		ModFeatures.register();
	}
}
