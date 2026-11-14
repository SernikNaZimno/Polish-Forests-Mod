package pl.polishforests.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;

/**
 * Decoration of a "Poland" chunk reads the biomes of one section per chunk (docs/03-m2-biomy.md §13 R11, step S7):
 * {@code ChunkGenerator.applyBiomeDecoration} collects the biomes of every section of the 3 × 3 chunks around the
 * decorated one (1179 sections in the realistic scale, about 5% of the decoration step), but the "Poland" generator gives
 * every section a copy of the same column biomes ({@code PolandChunkGenerator.createBiomes}, biomes do not depend on Y),
 * so the first section holds all of them; with the cave biomes of step J2 the sections below Y {@code CaveBiomes.MAX_Y}
 * hold the cave biomes (they lie only there) and the top section the column habitats (step TT5: under high ground the
 * deep dark can fill every cell of the low sections). The shortcut applies only while the "Poland" generator decorates with its own
 * biome source ({@code PolandChunkGenerator.decoratingColumnBiomeSections()}, a value of the decorating thread): with another
 * biome source, e.g. from a datapack, the generator takes the vanilla biomes, which may depend on Y, and the vanilla loop
 * stays (S7 review: the earlier test of the {@code ChunkHabitats} attachment, which {@code fill()} writes with any
 * biome source, would have skipped the biomes of the upper sections there).
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorDecorationMixin {
	@Redirect(method = "lambda$applyBiomeDecoration$1", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"))
	private static LevelChunkSection[] polishforests$oneSectionOfColumnBiomes(ChunkAccess chunk) {
		LevelChunkSection[] sections = chunk.getSections();
		int n = PolandChunkGenerator.decoratingColumnBiomeSections();
		if (n == 1) {
			return java.util.Arrays.copyOf(sections, 1);
		}
		if (n > 1 && n < sections.length) {
			// Step TT5: the sections below Y CaveBiomes.MAX_Y and the top one, which holds only the column habitats. Under
			// high ground (deeper than the depth cap of the cave biomes, about 320 blocks above Y 256) the deep dark can take
			// every cell of the low sections where the vanilla erosion is low, and the surface biome was then missing from the
			// decoration: no trees, plants or ground layer over whole chunks of the realistic Tatras.
			LevelChunkSection[] out = java.util.Arrays.copyOf(sections, n + 1);
			out[n] = sections[sections.length - 1];
			return out;
		}
		return sections;
	}
}
