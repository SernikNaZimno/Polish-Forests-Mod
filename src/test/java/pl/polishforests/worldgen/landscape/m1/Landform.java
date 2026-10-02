package pl.polishforests.worldgen.landscape.m1;

// Zamrożona kopia kodu M1 (M2, krok S0), skopiowana bez zmian poza pakietem. Wzorzec dla
// GoldenTerrainTest i SampleKosztTest. NIE ZMIENIAĆ: zob. package-info.java.

/** Formy terenu rozpoznawane przez {@link LandscapeModel#describe(double, double)}. */
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
	/** Regiel dolny Beskidów, poniżej ok. 1150 m. */
	REGIEL_DOLNY,
	/** Regiel górny Beskidów, od ok. 1150 m. */
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
	ZALEW
}
