package pl.polishforests.worldgen.landscape;

import java.util.List;

/**
 * Formy terenu rozpoznawane przez {@link LandscapeModel#describe(double, double)}. Część z nich
 * ({@link #FROM_SAMPLE}) rozpoznaje już tania {@link LandscapeModel#sample} i zapisuje jako bity w
 * {@link ColumnSample.Terrain#landformBits()}.
 */
public enum Landform {
	/** Wydmy na sandrze, co najmniej ok. 4 m wysokości. */
	INLAND_DUNES,
	/** Wał moreny czołowej, co najmniej ok. 25 m nad wysoczyzną. */
	END_MORAINE,
	/** Rynna polodowcowa, także jej suche odcinki. */
	TUNNEL_VALLEY,
	TUNNEL_VALLEY_LAKE,
	KETTLE_POND,
	KETTLE_BOG,
	RIVER,
	/** Dno doliny rzecznej (taras zalewowy z madami). */
	VALLEY_FLOOR,
	/** Zbocze doliny wielkiej rzeki. */
	VALLEY_SLOPE,
	/** Grzbiet górski lub pogórza. */
	RIDGE,
	/** Szczyt: lokalne maksimum wysokości w górach lub na pogórzu. */
	SUMMIT,
	/** Przełęcz: obniżenie grzbietu w miejscu doliny poprzecznej. */
	MOUNTAIN_PASS,
	/** Dno doliny górskiej. */
	MOUNTAIN_VALLEY,
	/** Regiel dolny Beskidów, poniżej nominalnej granicy z {@code habitat.Pietra}. */
	LOWER_MONTANE,
	/** Regiel górny Beskidów, od nominalnej granicy z {@code habitat.Pietra}. */
	UPPER_MONTANE,
	/** Potok (ciek najniższego rzędu). */
	STREAM,
	/** Strefa źródłowa cieku. */
	HEADWATERS,
	/** Starorzecze. */
	OXBOW_LAKE,
	/** Ujście rzeki do morza. */
	RIVER_MOUTH,
	BEACH,
	CLIFF,
	COASTAL_DUNES,
	/** Zalew lub jezioro przybrzeżne za mierzeją. */
	LAGOON;

	/** Formy rozpoznawane w {@link LandscapeModel#sample} (bez dodatkowych próbek), w stałej kolejności. */
	public static final List<Landform> FROM_SAMPLE = List.of(INLAND_DUNES, END_MORAINE, RIDGE, MOUNTAIN_VALLEY, BEACH,
			COASTAL_DUNES, CLIFF, HEADWATERS);

	/** Bit formy w {@link ColumnSample.Terrain#landformBits()} (form jest mniej niż 32). */
	public int bit() {
		return 1 << ordinal();
	}
}
