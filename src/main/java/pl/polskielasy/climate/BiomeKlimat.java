package pl.polskielasy.climate;

import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Interfejs doklejany do {@link Biome} przez {@code BiomeKlimatMixin}: pole z profilem klimatu
 * świata "Polska". Obiekty {@code Biome} powstają od nowa przy każdym wczytaniu świata, więc profil
 * nie przechodzi do innych światów.
 */
public interface BiomeKlimat {
	@Nullable KlimatBiomu polskielasy$klimat();

	void polskielasy$ustawKlimat(@Nullable KlimatBiomu klimat);

	/** Profil klimatu biomu albo null, gdy biom liczy temperaturę po wanilijnemu. */
	static @Nullable KlimatBiomu klimat(Biome biome) {
		return ((BiomeKlimat) (Object) biome).polskielasy$klimat();
	}

	static void ustaw(Biome biome, @Nullable KlimatBiomu klimat) {
		((BiomeKlimat) (Object) biome).polskielasy$ustawKlimat(klimat);
	}
}
