package pl.polskielasy.mixin;

import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.polskielasy.climate.BiomeKlimat;
import pl.polskielasy.climate.KlimatBiomu;

/**
 * Dokleja do {@link Biome} pole z profilem klimatu świata "Polska" ({@link BiomeKlimat}).
 * Pole jest ulotne, bo profil przypina wątek serwera albo klienta, a czytają go wątki generacji
 * i renderu.
 */
@Mixin(Biome.class)
abstract class BiomeKlimatMixin implements BiomeKlimat {
	@Unique
	private volatile @Nullable KlimatBiomu polskielasy$klimat;

	@Override
	public @Nullable KlimatBiomu polskielasy$klimat() {
		return polskielasy$klimat;
	}

	@Override
	public void polskielasy$ustawKlimat(@Nullable KlimatBiomu klimat) {
		this.polskielasy$klimat = klimat;
	}
}
