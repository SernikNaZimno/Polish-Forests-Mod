package pl.polishforests.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.climate.BiomeClimateAccess;
import pl.polishforests.climate.BiomeClimate;

/**
 * Temperature from meters a.s.l. instead of the vanilla drop above Y 80 (docs/03-m2-biomy.md, section 6.2).
 *
 * <p>We replace the private {@code getHeightAdjustedTemperature}, so the result goes into the vanilla
 * cache in {@code getTemperature} and from there to precipitation, freezing, {@code freeze_top_layer} and
 * Serene Seasons (which calls {@code getTemperature} and only then adds the seasonal adjustment).
 * Biomes without a profile (Nether, End, vanilla worlds) compute it the vanilla way.
 */
@Mixin(Biome.class)
abstract class BiomeTemperatureMixin {
	@Inject(method = "getHeightAdjustedTemperature(Lnet/minecraft/core/BlockPos;I)F", at = @At("HEAD"),
			cancellable = true, require = 1)
	private void polishforests$temperatureFromMeters(BlockPos pos, int seaLevel, CallbackInfoReturnable<Float> cir) {
		BiomeClimate climate = ((BiomeClimateAccess) this).polishforests$climate();
		if (climate != null) {
			cir.setReturnValue(climate.temperature(pos));
		}
	}
}
