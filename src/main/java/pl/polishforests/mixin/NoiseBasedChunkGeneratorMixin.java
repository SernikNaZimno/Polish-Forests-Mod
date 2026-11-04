package pl.polishforests.mixin;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * The world type "Default + Polish Forests" (step W1 of docs/04-pionowy-wycinek.md): a vanilla noise generator with the
 * biome source {@link VanillaForestsBiomeSource}. When the TERRAIN stage of a chunk is done (terrain, surface and carvers,
 * before any feature writes into it), the chunk habitats of the forests of the mod are attached
 * ({@code VanillaForestWorld.attach}); the last generation stage removes them, as the "Poland" generator does. Without
 * that biome source nothing changes.
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
	@Inject(method = "buildTerrain", at = @At("RETURN"), cancellable = true)
	private void polishforests$attachHabitats(ChunkAccess chunk, Blender blender, RandomState randomState,
			StructureManager structureManager, BiomeManager biomeManager, WorldGenRegion region, Set<Holder<Biome>> biomes,
			CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
		if (((ChunkGenerator) (Object) this).getBiomeSource() instanceof VanillaForestsBiomeSource source) {
			cir.setReturnValue(cir.getReturnValue().thenApply(c -> {
				source.world().attach(c, randomState, structureManager);
				return c;
			}));
		}
	}

	@Inject(method = "spawnOriginalMobs", at = @At("HEAD"))
	private void polishforests$removeHabitats(WorldGenRegion region, CallbackInfo ci) {
		if (((ChunkGenerator) (Object) this).getBiomeSource() instanceof VanillaForestsBiomeSource) {
			region.getChunk(region.getCenter().x(), region.getCenter().z()).removeAttached(ModFeatures.CHUNK_HABITATS);
		}
	}
}
