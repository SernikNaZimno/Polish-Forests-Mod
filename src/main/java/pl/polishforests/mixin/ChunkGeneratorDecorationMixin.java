package pl.polishforests.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pl.polishforests.worldgen.feature.ModFeatures;

/**
 * Decoration of a "Poland" chunk reads the biomes of one section per chunk (docs/03-m2-biomy.md §13 R11, step S7):
 * {@code ChunkGenerator.applyBiomeDecoration} collects the biomes of every section of the 3 × 3 chunks around the
 * decorated one (1179 sections in the realistic scale, about 5% of the decoration step), but the "Poland" generator gives
 * every section a copy of the same column biomes ({@code PolandChunkGenerator.createBiomes}, biomes do not depend on Y),
 * so the first section holds all of them. A chunk of the "Poland" world is recognized by its {@code ChunkHabitats}
 * attachment, which it keeps from {@code fill()} to the last generation stage; other chunks keep the vanilla loop.
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorDecorationMixin {
	@Redirect(method = "lambda$applyBiomeDecoration$1", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"))
	private static LevelChunkSection[] polishforests$oneSectionOfColumnBiomes(ChunkAccess chunk) {
		LevelChunkSection[] sections = chunk.getSections();
		if (sections.length > 1 && chunk.hasAttached(ModFeatures.CHUNK_HABITATS)) {
			return new LevelChunkSection[] {sections[0]};
		}
		return sections;
	}
}
