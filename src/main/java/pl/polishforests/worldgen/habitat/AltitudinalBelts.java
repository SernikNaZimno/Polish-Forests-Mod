package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.Landform;

/**
 * Piętra roślinności Beskidów i Pogórza (§5.1, raport ekologii §3, wzorzec Babiej Góry). Jedyne miejsce
 * z wysokościami pięter, także z nominalną granicą regla górnego 1150 m, z której korzystają opis terenu
 * ({@code LandscapeModel.describe}), źródło biomów M1 i komendy.
 *
 * <pre>
 * dT   = 40·szum(λ 150k)                                   // m
 * eks  = nach ≥ 5° ? 50·cos(eksp − 180°) : 0               // stok S +50 m, stok N −50 m
 * regielDolny = 550  + eks + dT
 * regielGorny = 1150 + eks + dT
 * granicaLasu = 1390 + eks − 60·[GRZBIET i nach &lt; 15°] + dT
 * progHali    = 1650 + eks + dT
 * duzyMasyw   = szczyt w promieniu 3 km·mspace > 1470 m (E12), z pola teren.szczyt
 * </pre>
 *
 * Nachylenie w skali rozgrywki jest przeliczone na bloki ({@link HabitatClassifier.Column#slope}). Zasięgi buka
 * i świerka (§9) nie są tu sprawdzane: w pasie górskim (wP + wB > 0,5) P jest bliskie 1, a oba gatunki mają
 * zasięg przy P ≥ 0,4–0,5, więc buczyna i świerczyna górska leżą w zasięgu z geografii pól regionalnych.
 * Jedlina wyżynna pod reglem dolnym ma te same warunki co strefowa ({@link HabitatClassifier#firForest}) i nie
 * wchodzi w dna dolin.
 */
public final class AltitudinalBelts {
	private AltitudinalBelts() {
	}

	/** Nominalne wysokości pięter (m n.p.m.), przed korektą ekspozycji i szumu. */
	public static final double LOWER_MONTANE = 550;
	public static final double UPPER_MONTANE = 1_150;
	public static final double TIMBERLINE = 1_390;
	public static final double ALPINE_THRESHOLD = 1_650;
	/**
	 * Najwyższy szczyt w promieniu 3 km·mspace, od którego masyw ma kosodrzewinę i halę (E12). Pole
	 * {@code teren.szczyt} to maksimum terenu bez dolin z siatki {@code PeakField}, więc próg jest wprost
	 * w metrach szczytu.
	 */
	public static final double LARGE_MASSIF = 1_470;
	/** Przesunięcie progu przez ekspozycję (m) i amplituda szumu przejść (m, fala 150 m·k). */
	public static final double ASPECT = 50;
	/** Ekspozycja działa od tego nachylenia (°); niżej teren jest płaski. */
	public static final double ASPECT_MIN_SLOPE = 5;
	public static final double TEMPERATURE_NOISE = 40;
	public static final double NOISE_WAVELENGTH = 150;
	/** Obniżenie granicy lasu na grzbietach wystawionych na wiatr (m) przy nachyleniu poniżej 15°. */
	public static final double WINDY_RIDGE = 60;
	/** Pas karłowych świerków pod granicą lasu (m). */
	public static final double BORDER_BAND = 60;
	/** Największe możliwe obniżenie progu (ekspozycja i szum), do pomijania szumu nisko. */
	public static final double MAX_LOWERING = ASPECT + TEMPERATURE_NOISE;
	/**
	 * Najniższa wysokość, na której duży masyw coś zmienia (pas granicy lasu na grzbiecie przy największym
	 * obniżeniu). Model liczy pole {@code teren.szczyt} tylko od tej wysokości.
	 */
	public static final double SUMMIT_FROM = TIMBERLINE - MAX_LOWERING - WINDY_RIDGE - BORDER_BAND;
	/** Grzbiet wystawiony na wiatr: nachylenie poniżej (°). */
	static final double WINDY_RIDGE_SLOPE = 15;
	/** Wyżej niż tyle w reglu dolnym stoki N mają Abieti-Piceetum (m). */
	static final double ABIETI_N = 900;
	/** Inwersja: dna dolin powyżej tej wysokości przy półszerokości dna > 30 m·k (m). */
	static final double ABIETI_FLOOR = 700;
	static final double ABIETI_FLOOR_K = 30;
	/** Pogórze: buczyna karpacka na stokach N powyżej (m). */
	static final double FOOTHILLS_BEECH = 450;
	/** Grąd na pogórzu na siedliskach L poniżej (m). */
	static final double FOOTHILLS_OAK_HORNBEAM = 400;
	/** Jaworzyna: nachylenie ponad (°) na stokach N–E (azymut 0–90°), w dolnej części zbocza. */
	static final double SYCAMORE_RAVINE_SLOPE = 30;
	/** Ziołorośla w żlebach regla górnego: profil fliszu poniżej. */
	static final double GULLY_RIDGE = 0.15;
	/** Jaworzyna w dolnej części zbocza: profil fliszu poniżej. */
	static final double SYCAMORE_RAVINE_RIDGE = 0.5;
	/** Jaworzyna na stokach od N do E: ekspozycja najwyżej (°). */
	static final double SYCAMORE_RAVINE_ASPECT = 90;

	/** Nominalny regiel górny (bez korekty): opis terenu, komendy, źródło biomów M1. */
	public static boolean isUpperMontane(double meters) {
		return meters >= UPPER_MONTANE;
	}

	/** Przesunięcie progów: ekspozycja i szum (m). */
	static double correction(HabitatClassifier.Column c) {
		double dT = TEMPERATURE_NOISE * c.classifier.belts.at(c.x, c.z, NOISE_WAVELENGTH * c.k);
		return ASPECT * c.aspect() + dT;
	}

	/** Górna granica regla dolnego w kolumnie (m), z ekspozycją i szumem. */
	static double upperMontaneLimit(HabitatClassifier.Column c) {
		return UPPER_MONTANE + correction(c);
	}

	/** Duży masyw: szczyt w promieniu 3 km·mspace ponad {@link #LARGE_MASSIF} (E12). */
	static boolean largeMassif(HabitatClassifier.Column c) {
		return c.t.summit() > LARGE_MASSIF;
	}

	/** Krok 4 klasyfikatora: piętra w pasie górskim (wP + wB > 0,5); {@code Wynik.BRAK} poza nim i nisko. */
	static int classify(HabitatClassifier.Column c) {
		if (c.wMountains <= 0.5) {
			return HabitatClassifier.Result.NONE;
		}
		double h = c.H;
		double corr = correction(c);
		boolean large = largeMassif(c);
		boolean onRidge = c.t.has(Landform.RIDGE);
		if (large && h >= ALPINE_THRESHOLD + corr) {
			return HabitatClassifier.Result.of(HabitatBiome.ALPINE_GRASSLAND, Zone.NONE, Association.TYPICAL);
		}
		double limit = TIMBERLINE + corr - (onRidge && c.slope < WINDY_RIDGE_SLOPE ? WINDY_RIDGE : 0);
		if (large && h >= limit) {
			return HabitatClassifier.Result.of(HabitatBiome.DWARF_PINE_SCRUB, Zone.NONE, Association.TYPICAL);
		}
		if (h >= UPPER_MONTANE + corr) {
			Zone s = large && h >= limit - BORDER_BAND ? Zone.TIMBERLINE : Zone.NONE;
			double p0 = c.t.ridgeProfile();
			Association z = !Double.isNaN(p0) && p0 < GULLY_RIDGE ? Association.GULLY_TALL_HERBS : Association.TYPICAL;
			return HabitatClassifier.Result.of(HabitatBiome.MONTANE_SPRUCE_FOREST, s, z);
		}
		Fertility t = c.fertility();
		boolean poor = t == Fertility.OLIGOTROPHIC || t == Fertility.OLIGO_MESOTROPHIC;
		double aspectOffset = c.aspect();
		if (h >= LOWER_MONTANE + corr) {
			boolean abieti = onRidge && poor || aspectOffset < 0 && h > ABIETI_N
					|| c.onValleyFloor() && h > ABIETI_FLOOR && c.w.floorHalfWidth() > ABIETI_FLOOR_K * c.k;
			if (abieti) {
				return HabitatClassifier.Result.of(HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.NONE, Association.ABIETI_PICEETUM);
			}
			return HabitatClassifier.Result.of(HabitatBiome.MONTANE_BEECH_FOREST, Zone.NONE, beechAssociation(c, poor));
		}
		if (c.wFoothills > c.wBeskids && aspectOffset < 0 && h > FOOTHILLS_BEECH) {
			return HabitatClassifier.Result.of(HabitatBiome.MONTANE_BEECH_FOREST, Zone.NONE, beechAssociation(c, poor));
		}
		if (t == Fertility.EUTROPHIC && h < FOOTHILLS_OAK_HORNBEAM) {
			return HabitatClassifier.Result.of(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE, Association.TYPICAL);
		}
		if (HabitatClassifier.firForest(c, t) && c.moisture() != Moisture.BOGGY) {
			return HabitatClassifier.Result.of(HabitatBiome.UPLAND_FIR_FOREST, Zone.NONE, Association.TYPICAL);
		}
		return HabitatClassifier.Result.NONE;
	}

	/** Jaworzyna na stromych stokach N–E w dolnej części zbocza, odmiana kwaśna na ubogich siedliskach. */
	private static Association beechAssociation(HabitatClassifier.Column c, boolean poor) {
		double e = c.t.aspect();
		double p0 = c.t.ridgeProfile();
		if (c.slope > SYCAMORE_RAVINE_SLOPE && !Double.isNaN(e) && e <= SYCAMORE_RAVINE_ASPECT && !Double.isNaN(p0)
				&& p0 < SYCAMORE_RAVINE_RIDGE) {
			return Association.SYCAMORE_RAVINE_FOREST;
		}
		return poor ? Association.ACIDOPHILOUS : Association.TYPICAL;
	}
}
