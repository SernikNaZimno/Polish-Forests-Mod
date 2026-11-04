package pl.polishforests.worldgen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.chunk.PolandBiomeSource;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/** Registers the world generation types: generator, biome source, vegetation dispatcher types and the chunk attachment. */
public final class PolishForestsWorldgen {
	private PolishForestsWorldgen() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, PolishForests.id("poland"), PolandChunkGenerator.CODEC);
		Registry.register(BuiltInRegistries.BIOME_SOURCE, PolishForests.id("poland"), PolandBiomeSource.CODEC);
		// Step W1: the biome source of the world type "Default + Polish Forests" (the vanilla forests replaced).
		Registry.register(BuiltInRegistries.BIOME_SOURCE, PolishForests.id("vanilla_forests"), VanillaForestsBiomeSource.CODEC);
		// The forests of that world read the climate of the world's random state (commands and features of chunks whose
		// TERRAIN stage ran in an earlier session).
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.getChunkSource().getGenerator().getBiomeSource() instanceof VanillaForestsBiomeSource source) {
				source.world().bind(level.getChunkSource().randomState());
			}
		});
		ModFeatures.register();
	}
}
