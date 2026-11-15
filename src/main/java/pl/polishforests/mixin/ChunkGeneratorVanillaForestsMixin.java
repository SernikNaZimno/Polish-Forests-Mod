package pl.polishforests.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.polishforests.climate.ColumnClimates;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * Soil ecotones of the world type "Default + Polish Forests" (step W1, rule Z10): before the decoration of a chunk the
 * soil tops of the habitat biomes blend across their borders ({@code VanillaForestWorld.blendSoil}), as the "Poland"
 * generator does in its own {@code applyBiomeDecoration}. Without that biome source nothing changes.
 *
 * <p>Step C2: during the decoration of such a chunk the thread reads the column climates of its generation region
 * ({@link ColumnClimates#decorating}), so {@code freeze_top_layer} lays snow and ice by the temperature of the column's
 * vanilla biome. A wrapper, so the mark is removed also when the decoration throws.
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorVanillaForestsMixin {
	@WrapMethod(method = "applyBiomeDecoration")
	private void polishforests$columnClimates(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager,
			Operation<Void> original) {
		if (!(((ChunkGenerator) (Object) this).getBiomeSource() instanceof VanillaForestsBiomeSource)) {
			original.call(level, chunk, structureManager);
			return;
		}
		LevelReader previous = ColumnClimates.decorating(level);
		try {
			original.call(level, chunk, structureManager);
		} finally {
			ColumnClimates.done(previous);
		}
	}

	@Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
	private void polishforests$blendSoil(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager,
			CallbackInfo ci) {
		if (((ChunkGenerator) (Object) this).getBiomeSource() instanceof VanillaForestsBiomeSource source) {
			source.world().blendSoil(level, chunk);
		}
	}
}
