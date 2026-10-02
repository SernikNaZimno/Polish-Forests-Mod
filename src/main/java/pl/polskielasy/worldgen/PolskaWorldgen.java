package pl.polskielasy.worldgen;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import pl.polskielasy.PolskieLasy;
import pl.polskielasy.worldgen.chunk.PolskaBiomeSource;
import pl.polskielasy.worldgen.chunk.PolskaChunkGenerator;

/** Rejestracja typów generacji świata. */
public final class PolskaWorldgen {
	private PolskaWorldgen() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, PolskieLasy.id("polska"), PolskaChunkGenerator.CODEC);
		Registry.register(BuiltInRegistries.BIOME_SOURCE, PolskieLasy.id("polska"), PolskaBiomeSource.CODEC);
	}
}
