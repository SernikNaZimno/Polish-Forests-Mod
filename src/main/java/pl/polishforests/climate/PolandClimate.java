package pl.polishforests.climate;

import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Temperature in the "Poland" world (docs/03-m2-biomy.md, section 6.1). A pure stateless function,
 * computed the same way on the server and on the client.
 *
 * <p>Conversion from the climate report: T = 0.20 + 0.05·t[°C], so the vanilla snow threshold 0.15
 * corresponds to a mean annual temperature of -1 °C. The lapse rate is 0.55 °C per 100 m, computed in
 * meters a.s.l. from the vertical mapping of the world, so the snow line is the same in both scales:
 * <pre>
 * T(pos) = T_base − Γ·max(0, m(y)) + 0.011·n(x, z)
 * </pre>
 * The noise n has a fixed seed, independent of the world seed (the client does not know it), and shifts
 * the snow line by about ±40 m. The regional term (continentality) will come in M4 together with the S2 climate.
 */
public final class PolandClimate {
	/** Γ: temperature drop per meter of altitude (0.55 °C/100 m · 0.05 per °C). */
	public static final double GRADIENT = 0.000275;
	/** Amplitude of the temperature noise (about ±40 m shift of the snow line). */
	public static final double NOISE_AMPLITUDE = 0.011;
	/** Noise wavelength in meters. */
	public static final double NOISE_WAVELENGTH = 150.0;
	/** Vanilla threshold: below it snow falls and water freezes ({@code Biome.warmEnoughToRain}). */
	public static final float SNOW_THRESHOLD = 0.15F;
	/** Rivers and streams freeze only below this temperature (about -3 °C). */
	public static final float RIVER_FREEZE_THRESHOLD = 0.05F;
	/** Serene Seasons gate: biomes with a base temperature above this value get no seasonal adjustment. */
	public static final float MAX_BASE_TEMPERATURE = 0.8F;

	/** Noise with a fixed seed; it must not be tied to the world seed, because the client computes it too. */
	private static final Noise TEMPERATURE_NOISE = new Noise(0x7E3B_C1A1_5EA5L);

	private PolandClimate() {
	}

	/** Temperature of a biome with base temperature {@code baseTemperature} at block (x, y, z) of a world with scale {@code scale}. */
	public static float temperature(float baseTemperature, VerticalScale scale, int x, int y, int z) {
		double m = scale.metersAboveSea(y);
		return (float) (baseTemperature - GRADIENT * Math.max(0.0, m) + NOISE_AMPLITUDE * noise(x, z));
	}

	/** Temperature noise in the range [-1, 1]. */
	public static double noise(int x, int z) {
		return Math.clamp(TEMPERATURE_NOISE.at(x, z, NOISE_WAVELENGTH), -1.0, 1.0);
	}

	/** Mean annual temperature in °C corresponding to a Minecraft temperature (inverse of T = 0.20 + 0.05·t). */
	public static double celsius(double t) {
		return (t - 0.20) / 0.05;
	}
}
