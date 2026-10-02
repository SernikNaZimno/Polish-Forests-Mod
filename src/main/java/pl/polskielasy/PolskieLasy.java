package pl.polskielasy;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.polskielasy.climate.WiazanieKlimatu;
import pl.polskielasy.command.PolskaCommands;
import pl.polskielasy.season.Seasons;
import pl.polskielasy.worldgen.PolskaWorldgen;

/**
 * Punkt wejścia moda "Przyrodniczo zgodne lasy".
 */
public final class PolskieLasy implements ModInitializer {
	public static final String MOD_ID = "polskielasy";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PolskaWorldgen.register();
		PolskaCommands.register();
		Seasons.init();
		WiazanieKlimatu.register();
		LOG.info("Przyrodniczo zgodne lasy: inicjalizacja zakończona (kalendarz: {})", Seasons.provider().name());
	}
}
