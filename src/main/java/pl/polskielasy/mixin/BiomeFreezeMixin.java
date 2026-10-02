package pl.polskielasy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polskielasy.climate.BiomeKlimat;
import pl.polskielasy.climate.KlimatBiomu;
import pl.polskielasy.climate.PolskaKlimat;
import pl.polskielasy.season.Seasons;

/**
 * Zamarzanie wody w biomach z profilem: morze nigdy, rzeki dopiero przy T < 0,05 (z korektą pory
 * roku z Serene Seasons). Pozostałe przypadki przechodzą do wanilii, więc {@code @Redirect} SS
 * wewnątrz {@code shouldFreeze} działa dalej bez zmian.
 */
@Mixin(Biome.class)
abstract class BiomeFreezeMixin {
	@Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z",
			at = @At("HEAD"), cancellable = true, require = 1)
	private void polskielasy$zamarzanie(LevelReader level, BlockPos pos, boolean checkNeighbors,
			CallbackInfoReturnable<Boolean> cir) {
		KlimatBiomu klimat = ((BiomeKlimat) this).polskielasy$klimat();
		if (klimat == null) {
			return;
		}
		// Bez switcha po enumie: javac dodałby syntetyczną klasę w pakiecie mixinów.
		KlimatBiomu.Zamarzanie tryb = klimat.zamarzanie();
		if (tryb == KlimatBiomu.Zamarzanie.NIGDY) {
			cir.setReturnValue(false);
		} else if (tryb == KlimatBiomu.Zamarzanie.RZEKA) {
			float t = Seasons.provider().temperatureInSeason(level, (Biome) (Object) this, pos, klimat.temperatura(pos));
			if (t >= PolskaKlimat.PROG_ZAMARZANIA_RZEK) {
				cir.setReturnValue(false);
			}
		}
	}
}
