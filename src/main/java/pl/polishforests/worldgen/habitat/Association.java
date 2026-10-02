package pl.polishforests.worldgen.habitat;

/**
 * Wariant w obrębie biomu (zespół, podzespół, postać): pole siedliska czytane przez palety drzewostanu
 * i runa (M3, M4) oraz faunę. Najwyżej 32 pozycje; kolejność stała, nowe tylko na końcu.
 */
public enum Association {
	TYPICAL,
	/** Łęg wierzbowy Salicetum albae. */
	SALICETUM_ALBAE,
	/** Łęg topolowy Populetum albae. */
	POPULETUM_ALBAE,
	/** Podgórski łęg jesionowy Carici remotae-Fraxinetum. */
	CARICI_REMOTAE_FRAXINETUM,
	/** Łęg źródliskowy (cardaminetosum amarae). */
	SPRING_FED,
	/** Młaka (olszyna bagienna Caltho laetae-Alnetum). */
	SPRING_FEN,
	/** Jaworzyna na stromym rumoszu (Lunario-Aceretum). */
	SYCAMORE_RAVINE_FOREST,
	/** Ziołorośla w żlebach regla górnego. */
	GULLY_TALL_HERBS,
	/** Dolnoreglowa jodłowo-świerkowa Abieti-Piceetum. */
	ABIETI_PICEETUM,
	/** Odmiana kwaśna (buczyny na ubogich siedliskach). */
	ACIDOPHILOUS,
	/** Bór bażynowy karłowy (pas wiatrowy). */
	STUNTED,
	/** Plaża kamienista pod klifem. */
	SHINGLE,
	/** Las wiatrowy za krawędzią klifu. */
	WINDSWEPT,
	/** Grąd zboczowy na zboczu doliny. */
	SLOPE,
	/** Łozowisko i ols przy starorzeczu. */
	OXBOW_LAKE,
	/** Bór na mierzei lub przy zalewie. */
	SPIT,
	/** Jezioro lobeliowe (oligotroficzne, sandr). */
	LOBELIA_LAKE,
	/** Zastoisko w dnie dużej doliny. */
	BACKSWAMP;

	private static final Association[] VALUES = values();

	public static Association of(int ordinal) {
		return VALUES[ordinal];
	}
}
