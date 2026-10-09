package pl.polishforests.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;

/**
 * No villages in the forests of the "present-day Poland" mode (round 1 of the S8 review, decision M2-17, default to be
 * confirmed; docs/03-m2-biomy.md §10.1). The biome tags {@code has_structure/village_*} do not depend on the vegetation
 * mode, and decision M2-12 put villages also into forest biomes (in the natural mode almost all land is forest). In the
 * present-day mode villages of today's Poland lie among fields and meadows, and the forest mask keeps forests mostly
 * on slopes, so a village in a forest stood under the crowns and on a slope. For a structure of the vanilla tag
 * {@code #minecraft:village} in a "Poland" world of that mode, the biome check of the start
 * ({@code Structure.GenerationContext.isValidBiome}) also rejects the biomes of the tag {@code polishforests:forests};
 * the vanilla loop over the structures of the set then tries the next village type, as for any other invalid biome.
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorStructureMixin {
	@ModifyVariable(method = "tryGenerateStructure", at = @At("STORE"))
	private Predicate<Holder<Biome>> polishforests$noPresentDayForestVillages(Predicate<Holder<Biome>> biomes,
			@Local(argsOnly = true) StructureSet.StructureSelectionEntry selected) {
		if ((Object) this instanceof PolandChunkGenerator poland) {
			return poland.structureBiomes(selected.structure(), biomes);
		}
		return biomes;
	}
}
