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
 * Temperatura z metrów n.p.m. zamiast wanilijnego spadku od Y 80 (docs/03-m2-biomy.md, sekcja 6.2).
 *
 * <p>Podmieniamy prywatne {@code getHeightAdjustedTemperature}, więc wynik trafia do wanilijnego
 * bufora w {@code getTemperature}, a stamtąd do opadu, zamarzania, {@code freeze_top_layer} i do
 * Serene Seasons (które woła {@code getTemperature} i dopiero potem dodaje korektę pory roku).
 * Biomy bez profilu (Nether, End, światy wanilijne) liczą po wanilijnemu.
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
