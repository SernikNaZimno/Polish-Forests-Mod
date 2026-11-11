package pl.polishforests.worldgen.landscape;

/**
 * Part of the Tatra complex (steps TT, docs/04-pionowy-wycinek.md §3 "Kroki TT") a column lies in; {@link #NONE}
 * outside it. The order is saved nowhere, but new constants still go at the end.
 */
public enum TatraPart {
	NONE,
	/** Western Tatras: crystalline core (gneiss), domed grassy ridges 1900–2250 m, a limestone summit cover. */
	WESTERN_TATRA,
	/** High Tatras: granite, sharp ridges, cirques, 2300–2530 m. */
	HIGH_TATRA,
	/** Belianske Tatras: the limestone eastern end of the High Tatras. */
	BELIANSKE_TATRA,
	/** The regle belt on the Podhale side: limestone and dolomite, 1000–1500 m, a Giewont-type ridge up to about 1900 m. */
	REGLE,
	/** Sub-Tatra Trough (Rów Podtatrzański): 800–1000 m at the northern foot of the massif. */
	SUBTATRA_TROUGH,
	/** Flysch foothills of the Gubałówka type: 800–1100 m. */
	GUBALOWKA_FOOTHILLS,
	/** The Podhale basin (Nowy Targ type): 580–700 m. */
	PODHALE_BASIN,
	/** The southern foot of the massif (the other side of the main ridge). */
	SOUTH_FOOT;

	/** Whether the part belongs to the mountains proper (massif and regle), not to the foot and Podhale. */
	public boolean massif() {
		return this == WESTERN_TATRA || this == HIGH_TATRA || this == BELIANSKE_TATRA || this == REGLE;
	}

	/** Whether the part is the High Tatras in the wide sense (with the Belianske Tatras). */
	public boolean high() {
		return this == HIGH_TATRA || this == BELIANSKE_TATRA;
	}
}
