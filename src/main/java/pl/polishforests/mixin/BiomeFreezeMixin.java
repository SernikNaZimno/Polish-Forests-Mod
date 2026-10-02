package pl.polishforests.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.climate.BiomeClimateAccess;
import pl.polishforests.climate.BiomeClimate;
import pl.polishforests.climate.PolandClimate;
import pl.polishforests.season.Seasons;

/**
 * Zamarzanie wody w biomach z profilem: morze nigdy, rzeki dopiero przy T < 0,05 (z korektą pory
 * roku z Serene Seasons). Pozostałe przypadki przechodzą do wanilii, więc {@code @Redirect} SS
 * wewnątrz {@code shouldFreeze} działa dalej bez zmian.
 */
@Mixin(Biome.class)
abstract class BiomeFreezeMixin {
	@Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z",
			at = @At("HEAD"), cancellable = true, require = 1)
	private void polishforests$freezing(LevelReader level, BlockPos pos, boolean checkNeighbors,
			CallbackInfoReturnable<Boolean> cir) {
		BiomeClimate climate = ((BiomeClimateAccess) this).polishforests$climate();
		if (climate == null) {
			return;
		}
		// Bez switcha po enumie: javac dodałby syntetyczną klasę w pakiecie mixinów.
		BiomeClimate.FreezeMode mode = climate.freezeMode();
		if (mode == BiomeClimate.FreezeMode.NEVER) {
			cir.setReturnValue(false);
		} else if (mode == BiomeClimate.FreezeMode.RIVER) {
			float t = Seasons.provider().temperatureInSeason(level, (Biome) (Object) this, pos, climate.temperature(pos));
			if (t >= PolandClimate.RIVER_FREEZE_THRESHOLD) {
				cir.setReturnValue(false);
			}
		}
	}
}
