package pl.polishforests.climate;

import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Interfejs doklejany do {@link Biome} przez {@code BiomeKlimatMixin}: pole z profilem klimatu
 * świata "Polska". Obiekty {@code Biome} powstają od nowa przy każdym wczytaniu świata, więc profil
 * nie przechodzi do innych światów.
 */
public interface BiomeClimateAccess {
	@Nullable BiomeClimate polishforests$climate();

	void polishforests$setClimate(@Nullable BiomeClimate climate);

	/** Profil klimatu biomu albo null, gdy biom liczy temperaturę po wanilijnemu. */
	static @Nullable BiomeClimate climate(Biome biome) {
		return ((BiomeClimateAccess) (Object) biome).polishforests$climate();
	}

	static void set(Biome biome, @Nullable BiomeClimate climate) {
		((BiomeClimateAccess) (Object) biome).polishforests$setClimate(climate);
	}
}
