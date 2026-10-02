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
 * Water freezing in biomes with a profile: the sea never freezes, rivers only at T < 0.05 (with the
 * seasonal adjustment from Serene Seasons). All other cases fall through to vanilla, so the SS
 * {@code @Redirect} inside {@code shouldFreeze} keeps working unchanged.
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
		// No switch on the enum: javac would add a synthetic class to the mixin package.
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
