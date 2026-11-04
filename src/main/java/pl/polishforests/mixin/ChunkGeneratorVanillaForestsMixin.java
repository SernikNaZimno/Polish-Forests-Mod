package pl.polishforests.mixin;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * Soil ecotones of the world type "Default + Polish Forests" (step W1, rule Z10): before the decoration of a chunk the
 * soil tops of the habitat biomes blend across their borders ({@code VanillaForestWorld.blendSoil}), as the "Poland"
 * generator does in its own {@code applyBiomeDecoration}. Without that biome source nothing changes.
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorVanillaForestsMixin {
	@Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
	private void polishforests$blendSoil(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager,
			CallbackInfo ci) {
		if (((ChunkGenerator) (Object) this).getBiomeSource() instanceof VanillaForestsBiomeSource source) {
			source.world().blendSoil(level, chunk);
		}
	}
}
