package pl.polishforests.mixin;

import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.polishforests.climate.BiomeClimateAccess;
import pl.polishforests.climate.BiomeClimate;

/**
 * Dokleja do {@link Biome} pole z profilem klimatu świata "Polska" ({@link BiomeClimateAccess}).
 * Pole jest ulotne, bo profil przypina wątek serwera albo klienta, a czytają go wątki generacji
 * i renderu.
 */
@Mixin(Biome.class)
abstract class BiomeClimateMixin implements BiomeClimateAccess {
	@Unique
	private volatile @Nullable BiomeClimate polishforests$climate;

	@Override
	public @Nullable BiomeClimate polishforests$climate() {
		return polishforests$climate;
	}

	@Override
	public void polishforests$setClimate(@Nullable BiomeClimate climate) {
		this.polishforests$climate = climate;
	}
}
