package pl.polishforests.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;

/**
 * The cheap structure check sees the M2-17 rule too (round 2 of the S8 review). {@code StructureCheck.canCreateStructure},
 * which {@code /locate}, explorer maps and {@code ChunkGenerator.getStructureGeneratingAt} use before loading a
 * candidate chunk, builds its own generation context with the plain biome tag of the structure. Without this, every
 * forest candidate of a village in the present-day mode passed the check, and the search then loaded that chunk to
 * STRUCTURE_STARTS synchronously only to find no start. The wrapped call gets a context whose biome predicate is the one
 * {@link PolandChunkGenerator#structureBiomes} gives, the same as in {@code ChunkGeneratorStructureMixin}.
 */
@Mixin(StructureCheck.class)
abstract class StructureCheckMixin {
	@WrapOperation(method = "canCreateStructure", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/structure/Structure;findValidGenerationPoint(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;)Ljava/util/Optional;"))
	private Optional<Structure.GenerationStub> polishforests$noPresentDayForestVillages(Structure structure,
			Structure.GenerationContext context, Operation<Optional<Structure.GenerationStub>> original) {
		if (context.chunkGenerator() instanceof PolandChunkGenerator poland && poland.presentDay()) {
			Holder<Structure> holder = context.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(structure);
			Predicate<Holder<Biome>> narrowed = poland.structureBiomes(holder, context.validBiome());
			if (narrowed != context.validBiome()) {
				context = new Structure.GenerationContext(context.registryAccess(), context.chunkGenerator(), context.biomeSource(),
						context.climateSampler(), context.biomeResolver(), context.randomState(), context.structureTemplateManager(),
						context.random(), context.seed(), context.chunkPos(), context.heightAccessor(), narrowed);
			}
		}
		return original.call(structure, context);
	}
}
