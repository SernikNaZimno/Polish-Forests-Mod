package pl.polskielasy.climate;

import pl.polskielasy.worldgen.chunk.VerticalScale;
import pl.polskielasy.worldgen.landscape.Noise;

/**
 * Temperatura w świecie "Polska" (docs/03-m2-biomy.md, sekcja 6.1). Czysta funkcja bez stanu,
 * liczona tak samo na serwerze i u klienta.
 *
 * <p>Przeliczenie z raportu klimatu: T = 0,20 + 0,05·t[°C], więc wanilijny próg śniegu 0,15
 * odpowiada -1 °C średniej rocznej. Spadek z wysokością to 0,55 °C na 100 m, liczony w metrach
 * n.p.m. z odwzorowania pionowego świata, dzięki czemu granica śniegu jest taka sama w obu skalach:
 * <pre>
 * T(pos) = T_bazowa − Γ·max(0, m(y)) + 0,011·n(x, z)
 * </pre>
 * Szum n ma stałe ziarno, niezależne od ziarna świata (klient go nie zna), i przesuwa granicę
 * śniegu o ok. ±40 m. Człon regionalny (kontynentalizm) dojdzie w M4 razem z klimatem S2.
 */
public final class PolskaKlimat {
	/** Γ: spadek temperatury na metr wysokości (0,55 °C/100 m · 0,05 na °C). */
	public static final double GRADIENT = 0.000275;
	/** Amplituda szumu temperatury (ok. ±40 m przesunięcia granicy śniegu). */
	public static final double AMPLITUDA_SZUMU = 0.011;
	/** Długość fali szumu w metrach. */
	public static final double FALA_SZUMU = 150.0;
	/** Wanilijny próg: poniżej pada śnieg i zamarza woda ({@code Biome.warmEnoughToRain}). */
	public static final float PROG_SNIEGU = 0.15F;
	/** Rzeki i potoki zamarzają dopiero poniżej tej temperatury (ok. -3 °C). */
	public static final float PROG_ZAMARZANIA_RZEK = 0.05F;
	/** Bramka Serene Seasons: biomy o temperaturze bazowej powyżej tej wartości nie dostają korekty pory roku. */
	public static final float MAKS_T_BAZOWA = 0.8F;

	/** Szum o stałym ziarnie; nie wolno go wiązać z ziarnem świata, bo liczy go też klient. */
	private static final Noise SZUM = new Noise(0x7E3B_C1A1_5EA5L);

	private PolskaKlimat() {
	}

	/** Temperatura biomu o temperaturze bazowej {@code tBazowa} w bloku (x, y, z) świata o skali {@code skala}. */
	public static float temperatura(float tBazowa, VerticalScale skala, int x, int y, int z) {
		double m = skala.metersAboveSea(y);
		return (float) (tBazowa - GRADIENT * Math.max(0.0, m) + AMPLITUDA_SZUMU * szum(x, z));
	}

	/** Szum temperatury w przedziale [-1, 1]. */
	public static double szum(int x, int z) {
		return Math.clamp(SZUM.at(x, z, FALA_SZUMU), -1.0, 1.0);
	}

	/** Średnia roczna w °C odpowiadająca temperaturze Minecrafta (odwrotność T = 0,20 + 0,05·t). */
	public static double celsjusze(double t) {
		return (t - 0.20) / 0.05;
	}
}
