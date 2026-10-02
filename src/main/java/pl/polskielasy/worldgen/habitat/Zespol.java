package pl.polskielasy.worldgen.habitat;

/**
 * Wariant w obrębie biomu (zespół, podzespół, postać): pole siedliska czytane przez palety drzewostanu
 * i runa (M3, M4) oraz faunę. Najwyżej 32 pozycje; kolejność stała, nowe tylko na końcu.
 */
public enum Zespol {
	TYPOWY,
	/** Łęg wierzbowy Salicetum albae. */
	SALICETUM_ALBAE,
	/** Łęg topolowy Populetum albae. */
	POPULETUM_ALBAE,
	/** Podgórski łęg jesionowy Carici remotae-Fraxinetum. */
	CARICI_REMOTAE_FRAXINETUM,
	/** Łęg źródliskowy (cardaminetosum amarae). */
	ZRODLISKOWY,
	/** Młaka (olszyna bagienna Caltho laetae-Alnetum). */
	MLAKA,
	/** Jaworzyna na stromym rumoszu (Lunario-Aceretum). */
	JAWORZYNA,
	/** Ziołorośla w żlebach regla górnego. */
	ZIOLOROSLA_ZLEBOWE,
	/** Dolnoreglowa jodłowo-świerkowa Abieti-Piceetum. */
	ABIETI_PICEETUM,
	/** Odmiana kwaśna (buczyny na ubogich siedliskach). */
	KWASNY,
	/** Bór bażynowy karłowy (pas wiatrowy). */
	KARLOWY,
	/** Plaża kamienista pod klifem. */
	KAMIENISTY,
	/** Las wiatrowy za krawędzią klifu. */
	WIATROWY,
	/** Grąd zboczowy na zboczu doliny. */
	ZBOCZOWY,
	/** Łozowisko i ols przy starorzeczu. */
	STARORZECZE,
	/** Bór na mierzei lub przy zalewie. */
	MIERZEJA,
	/** Jezioro lobeliowe (oligotroficzne, sandr). */
	LOBELIOWY,
	/** Zastoisko w dnie dużej doliny. */
	ZASTOISKO;

	private static final Zespol[] VALUES = values();

	public static Zespol of(int ordinal) {
		return VALUES[ordinal];
	}
}
