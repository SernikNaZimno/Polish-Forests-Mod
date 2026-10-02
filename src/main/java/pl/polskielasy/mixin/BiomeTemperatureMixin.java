package pl.polskielasy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polskielasy.climate.BiomeKlimat;
import pl.polskielasy.climate.KlimatBiomu;

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
	private void polskielasy$temperaturaZMetrow(BlockPos pos, int seaLevel, CallbackInfoReturnable<Float> cir) {
		KlimatBiomu klimat = ((BiomeKlimat) this).polskielasy$klimat();
		if (klimat != null) {
			cir.setReturnValue(klimat.temperatura(pos));
		}
	}
}
