package pl.polishforests.climate;

import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Interface mixed into {@link Biome} by {@code BiomeClimateMixin}: a field holding the climate profile
 * of the "Poland" world. {@code Biome} objects are created anew every time a world is loaded, so the
 * profile does not carry over to other worlds.
 */
public interface BiomeClimateAccess {
	@Nullable BiomeClimate polishforests$climate();

	void polishforests$setClimate(@Nullable BiomeClimate climate);

	/** Climate profile of the biome, or null when the biome computes its temperature the vanilla way. */
	static @Nullable BiomeClimate climate(Biome biome) {
		return ((BiomeClimateAccess) (Object) biome).polishforests$climate();
	}

	static void set(Biome biome, @Nullable BiomeClimate climate) {
		((BiomeClimateAccess) (Object) biome).polishforests$setClimate(climate);
	}
}
