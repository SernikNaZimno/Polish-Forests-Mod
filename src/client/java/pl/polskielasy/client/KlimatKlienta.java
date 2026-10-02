package pl.polskielasy.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import pl.polskielasy.PolskieLasy;
import pl.polskielasy.climate.WiazanieKlimatu;
import pl.polskielasy.worldgen.chunk.PolskaDimension;
import pl.polskielasy.worldgen.chunk.PolskaScale;
import pl.polskielasy.worldgen.chunk.VerticalScale;

/**
 * Klimat po stronie klienta: klient sam liczy opad (deszcz albo śnieg) z temperatury biomu, więc
 * musi znać skalę pionową świata. Rozpoznaje ją po typie wymiaru, a awaryjnie po zakresie
 * wysokości overworldu, i przypina profile klimatu do biomów z tagu. W grze jednoosobowej nic nie
 * przypina: klient dzieli obiekty biomów z serwerem zintegrowanym, który przypiął je sam, a rozpoznanie
 * po wysokości mogłoby wtedy zmienić temperatury serwera w świecie bez generatora "Polska".
 */
public final class KlimatKlienta {
	private KlimatKlienta() {
	}

	public static void register() {
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(KlimatKlienta::przypnij);
	}

	private static void przypnij(Minecraft minecraft, ClientLevel level) {
		if (minecraft.getSingleplayerServer() != null) {
			return;
		}
		VerticalScale skala = skala(level);
		if (skala != null) {
			int n = WiazanieKlimatu.przypnijBezZdejmowania(level.registryAccess(), skala);
			PolskieLasy.LOG.info("Klimat klienta: skala {}, profil w {} biomach", WiazanieKlimatu.nazwa(skala), n);
		}
	}

	/**
	 * Skala pionowa świata "Polska" dla poziomu klienta albo null dla innych wymiarów. Przy zmianie
	 * wymiaru profili nie zdejmujemy: biomy innych wymiarów nie należą do tagu.
	 */
	public static @Nullable VerticalScale skala(Level level) {
		PolskaScale scale = level.dimensionTypeRegistration().unwrapKey().map(PolskaScale::byDimensionType)
				.orElse(null);
		if (scale != null) {
			return scale.vertical();
		}
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		VerticalScale awaryjna = PolskaDimension.scaleOf(level);
		if (awaryjna != null) {
			PolskieLasy.LOG.warn("Klimat klienta: nieznany typ wymiaru {}, skala {} rozpoznana po wysokości świata",
					level.dimensionTypeRegistration().getRegisteredName(), WiazanieKlimatu.nazwa(awaryjna));
		}
		return awaryjna;
	}
}
