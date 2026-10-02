package pl.polishforests.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.climate.ClimateBinding;
import pl.polishforests.worldgen.chunk.PolandDimension;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.chunk.VerticalScale;

/**
 * Client-side climate: the client computes precipitation (rain or snow) from the biome temperature
 * on its own, so it has to know the vertical scale of the world. It detects the scale from the
 * dimension type, falling back to the overworld height range, and attaches climate profiles to the
 * biomes in the tag. In singleplayer it attaches nothing: the client shares biome objects with the
 * integrated server, which has already attached them itself, and height-based detection could then
 * change the server's temperatures in a world without the "Poland" generator.
 */
public final class ClientClimate {
	private ClientClimate() {
	}

	public static void register() {
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(ClientClimate::attach);
	}

	private static void attach(Minecraft minecraft, ClientLevel level) {
		if (minecraft.getSingleplayerServer() != null) {
			return;
		}
		VerticalScale scale = scale(level);
		if (scale != null) {
			int n = ClimateBinding.attachWithoutDetaching(level.registryAccess(), scale);
			PolishForests.LOG.info("Client climate: scale {}, profile attached to {} biomes",
					ClimateBinding.scaleName(scale), n);
		}
	}

	/**
	 * Vertical scale of the "Poland" world for the client level, or null for other dimensions.
	 * Profiles are not detached on a dimension change: biomes of other dimensions are not in the tag.
	 */
	public static @Nullable VerticalScale scale(Level level) {
		PolandScale scale = level.dimensionTypeRegistration().unwrapKey().map(PolandScale::byDimensionType)
				.orElse(null);
		if (scale != null) {
			return scale.vertical();
		}
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		VerticalScale fallback = PolandDimension.scaleOf(level);
		if (fallback != null) {
			PolishForests.LOG.warn("Client climate: unknown dimension type {}, scale {} detected from the world height",
					level.dimensionTypeRegistration().getRegisteredName(), ClimateBinding.scaleName(fallback));
		}
		return fallback;
	}
}
