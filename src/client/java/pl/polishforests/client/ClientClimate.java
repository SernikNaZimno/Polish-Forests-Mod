package pl.polishforests.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.climate.ClimateBinding;
import pl.polishforests.climate.ColumnClimates;
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
 *
 * <p>Step C2 (decision CP-D.11): the client reads the column climates of its own chunks (attachment synchronized by the
 * server, {@link ColumnClimates}) on the client thread, where it draws rain and snow; a client of a remote server gives the
 * habitat biomes the column profile when it enters an overworld that is not a "Poland" world (the world type
 * "Default + Polish Forests"; in any other such world the habitat biomes do not occur, and a chunk without the
 * attachment keeps the biome's own temperature).
 */
public final class ClientClimate {
	private ClientClimate() {
	}

	public static void register() {
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(ClientClimate::attach);
		ColumnClimates.bindClient(ClientClimate::chunk);
	}

	/** The client level's chunk (chunkX, chunkZ), only on the client thread (the render thread), else null. */
	private static @Nullable ChunkAccess chunk(int chunkX, int chunkZ) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || !mc.isSameThread()) {
			return null;
		}
		ClientLevel level = mc.level;
		return level == null ? null : level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
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
		} else if (level.dimension() == Level.OVERWORLD) {
			int n = ClimateBinding.attachVanillaColumnsWithoutDetaching(level.registryAccess());
			PolishForests.LOG.info("Client climate: column profile (Default + Polish Forests) attached to {} biomes", n);
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
