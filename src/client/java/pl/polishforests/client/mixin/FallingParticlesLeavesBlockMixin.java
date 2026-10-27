package pl.polishforests.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingParticlesLeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.polishforests.client.ClientLeafFall;

/**
 * Step V13 (the user's request of 2026-10-26): the chance of a falling leaf particle in the animation tick of a leaf
 * block (the read of {@code leafParticleChance} in {@code FallingParticlesLeavesBlock.makeFallingLeavesParticles}) follows
 * the season for the leaves of the slice species and for vanilla leaves in the "Polska" world ({@link ClientLeafFall});
 * everything else (the check of the block below, the particle and its tint) stays vanilla, and other worlds keep the
 * vanilla chance.
 */
@Mixin(FallingParticlesLeavesBlock.class)
abstract class FallingParticlesLeavesBlockMixin {
	@ModifyExpressionValue(method = "makeFallingLeavesParticles", at = @At(value = "FIELD",
			target = "Lnet/minecraft/world/level/block/FallingParticlesLeavesBlock;leafParticleChance:F"))
	private float polishforests$seasonalLeafFall(float original, @Local(argsOnly = true) Level level,
			@Local(argsOnly = true) BlockPos pos) {
		return ClientLeafFall.chance((Block) (Object) this, original, level, pos);
	}
}
