package pl.polskielasy.worldgen.habitat;

import pl.polskielasy.worldgen.landscape.Landform;
import pl.polskielasy.worldgen.landscape.LandscapeModel;
import pl.polskielasy.worldgen.landscape.Noise;

/**
 * Maska lasu trybu D „dzisiejsza Polska” (raport ekologii §6). Las, gdy kwantyl gładkiego szumu F
 * (fala 4 km·k z drobnym szumem krawędzi 300 m·k) jest mniejszy niż P_las siedliska, więc przy stałym
 * P_las udział lasu wynosi P_las. Poza lasem biom nieleśny według siedliska (§2.2); strefy przywodne
 * zostają. Wartości P_las są startowe; kalibracja i test udziałów trybu D należą do kroku S8. W trybie N
 * (domyślnym do M8) klasyfikator maski nie używa.
 */
final class Lesistosc {
	private static final double FALA = 4_000;
	private static final double FALA_KRAWEDZI = 300;
	private static final double KRAWEDZ = 0.08;

	private final Noise szum;
	private final double k;

	Lesistosc(Noise szum, double k) {
		this.szum = szum;
		this.k = k;
	}

	/** Kwantyl F w [0, 1]. */
	private double f(double x, double z) {
		double v = szum.at(x, z, FALA * k) + KRAWEDZ * szum.at(x + 5_151, z - 919, FALA_KRAWEDZI * k);
		return LandscapeModel.noiseQuantile(Math.clamp(v / (1 + KRAWEDZ), -1.0, 1.0));
	}

	/** Czy kolumna biomu leśnego zostaje lasem w trybie D. */
	boolean las(Klasyfikator.Kolumna c, Biom biom, Stl stl) {
		return f(c.x, c.z) < pLas(c, biom, stl);
	}

	/** P_las (raport ekologii §6.3, wartości startowe). */
	static double pLas(Klasyfikator.Kolumna c, Biom biom, Stl stl) {
		switch (biom) {
			case KOSODRZEWINA:
				return 1;
			case BOR_BAZYNOWY:
				return 0.90;
			case LEG_WIERZBOWO_TOPOLOWY:
				return 0.45;
			case LEG_WIAZOWO_JESIONOWY:
				return 0.10;
			case LEG_JESIONOWO_OLSZOWY:
				return 0.50;
			case OLS:
				return 0.35;
			case SWIERCZYNA_GORSKA:
				return 0.85;
			case BUCZYNA_GORSKA:
				return c.dno() ? 0.20 : 0.80;
			case OLSZYNA_GORSKA:
				return 0.45;
			default:
				break;
		}
		if (c.wPg > 0.5) {
			return c.nach > 10 ? 0.60 : 0.12;
		}
		return switch (stl) {
			case BS -> 0.90;
			case BSW -> 0.75;
			case BW, BMW -> 0.65;
			case BB, BMB -> 0.85;
			case BMSW -> 0.45;
			case LMSW -> 0.22;
			case LMW, LMB -> 0.25;
			case LSW -> c.t.ma(Landform.WAL_MORENOWY) ? 0.50 : c.nach > 15 ? 0.75 : c.nach >= 5 ? 0.35 : 0.08;
			case LW -> 0.12;
			default -> 0.5;
		};
	}

	/** Biom nieleśny dla niezalesionego siedliska (§2.2, kolumna D). */
	Biom nielesny(Klasyfikator.Kolumna c, Biom biom, Stl stl) {
		double q = c.wariant(3);
		switch (biom) {
			case LEG_WIERZBOWO_TOPOLOWY, LEG_JESIONOWO_OLSZOWY, OLSZYNA_GORSKA:
				return Biom.LAKA_WILGOTNA;
			case LEG_WIAZOWO_JESIONOWY:
				return q < 0.8 ? Biom.LAKA_WILGOTNA : Biom.POLE;
			case OLS:
				return q < 0.7 ? Biom.LAKA_WILGOTNA : Biom.TORFOWISKO_NISKIE;
			case BOR_BAZYNOWY:
				return Biom.WYDMA_SZARA;
			case BUCZYNA_GORSKA, SWIERCZYNA_GORSKA, JEDLINA_WYZYNNA:
				return Biom.LAKA_SWIEZA;
			default:
				break;
		}
		return switch (stl) {
			case BS -> Biom.WRZOSOWISKO;
			case BSW -> q < 0.6 ? Biom.POLE : Biom.WRZOSOWISKO;
			case BW, BMW -> q < 0.6 ? Biom.LAKA_WILGOTNA : Biom.POLE;
			case BB, BMB -> Biom.TORFOWISKO_WYSOKIE;
			case LW, LMW, LMB, OL -> Biom.LAKA_WILGOTNA;
			default -> c.nach < 5 ? (q < 0.85 ? Biom.POLE : Biom.LAKA_SWIEZA) : Biom.LAKA_SWIEZA;
		};
	}
}
