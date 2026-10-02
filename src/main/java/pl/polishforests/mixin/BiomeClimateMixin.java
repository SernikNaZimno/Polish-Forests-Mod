package pl.polishforests.mixin;

import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.polishforests.climate.BiomeClimateAccess;
import pl.polishforests.climate.BiomeClimate;

/**
 * Adds to {@link Biome} a field holding the climate profile of the "Poland" world ({@link BiomeClimateAccess}).
 * The field is volatile, because the profile is attached by the server or client thread and read by the
 * generation and render threads.
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
