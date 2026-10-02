package pl.polskielasy.worldgen.landscape;

import java.util.List;

/**
 * Formy terenu rozpoznawane przez {@link LandscapeModel#describe(double, double)}. Część z nich
 * ({@link #Z_PROBKI}) rozpoznaje już tania {@link LandscapeModel#sample} i zapisuje jako bity w
 * {@link ColumnSample.Teren#formy()}.
 */
public enum Landform {
	/** Wydmy na sandrze, co najmniej ok. 4 m wysokości. */
	WYDMY,
	/** Wał moreny czołowej, co najmniej ok. 25 m nad wysoczyzną. */
	WAL_MORENOWY,
	/** Rynna polodowcowa, także jej suche odcinki. */
	RYNNA,
	JEZIORO_RYNNOWE,
	OCZKO_WODNE,
	OCZKO_TORFOWE,
	RZEKA,
	/** Dno doliny rzecznej (taras zalewowy z madami). */
	DNO_DOLINY,
	/** Zbocze doliny wielkiej rzeki. */
	ZBOCZE_DOLINY,
	/** Grzbiet górski lub pogórza. */
	GRZBIET,
	/** Szczyt: lokalne maksimum wysokości w górach lub na pogórzu. */
	SZCZYT,
	/** Przełęcz: obniżenie grzbietu w miejscu doliny poprzecznej. */
	PRZELECZ,
	/** Dno doliny górskiej. */
	DOLINA_GORSKA,
	/** Regiel dolny Beskidów, poniżej nominalnej granicy z {@code habitat.Pietra}. */
	REGIEL_DOLNY,
	/** Regiel górny Beskidów, od nominalnej granicy z {@code habitat.Pietra}. */
	REGIEL_GORNY,
	/** Potok (ciek najniższego rzędu). */
	POTOK,
	/** Strefa źródłowa cieku. */
	ZRODLO,
	/** Starorzecze. */
	STARORZECZE,
	/** Ujście rzeki do morza. */
	UJSCIE,
	PLAZA,
	KLIF,
	WYDMY_NADMORSKIE,
	/** Zalew lub jezioro przybrzeżne za mierzeją. */
	ZALEW;

	/** Formy rozpoznawane w {@link LandscapeModel#sample} (bez dodatkowych próbek), w stałej kolejności. */
	public static final List<Landform> Z_PROBKI = List.of(WYDMY, WAL_MORENOWY, GRZBIET, DOLINA_GORSKA, PLAZA,
			WYDMY_NADMORSKIE, KLIF, ZRODLO);

	/** Bit formy w {@link ColumnSample.Teren#formy()} (form jest mniej niż 32). */
	public int bit() {
		return 1 << ordinal();
	}
}
