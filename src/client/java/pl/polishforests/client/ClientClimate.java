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
 * Klimat po stronie klienta: klient sam liczy opad (deszcz albo śnieg) z temperatury biomu, więc
 * musi znać skalę pionową świata. Rozpoznaje ją po typie wymiaru, a awaryjnie po zakresie
 * wysokości overworldu, i przypina profile klimatu do biomów z tagu. W grze jednoosobowej nic nie
 * przypina: klient dzieli obiekty biomów z serwerem zintegrowanym, który przypiął je sam, a rozpoznanie
 * po wysokości mogłoby wtedy zmienić temperatury serwera w świecie bez generatora "Polska".
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
			PolishForests.LOG.info("Klimat klienta: skala {}, profil w {} biomach", ClimateBinding.scaleName(scale), n);
		}
	}

	/**
	 * Skala pionowa świata "Polska" dla poziomu klienta albo null dla innych wymiarów. Przy zmianie
	 * wymiaru profili nie zdejmujemy: biomy innych wymiarów nie należą do tagu.
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
			PolishForests.LOG.warn("Klimat klienta: nieznany typ wymiaru {}, skala {} rozpoznana po wysokości świata",
					level.dimensionTypeRegistration().getRegisteredName(), ClimateBinding.scaleName(fallback));
		}
		return fallback;
	}
}
