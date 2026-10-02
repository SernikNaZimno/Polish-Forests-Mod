package pl.polishforests.worldgen.habitat;

import java.util.Locale;

/**
 * Strefy węższe niż ok. 12 m (Z4): nie są biomami, tylko polem siedliska w rozdzielczości bloku. Malują
 * glebę i mikrorelief w {@code fill()} i sterują roślinnością (docs/03-m2-biomy.md §4–§5). Strefa jest
 * niezależna od biomu, np. OKRAJEK wewnątrz łęgu wierzbowo-topolowego. Najwyżej 32 pozycje (5 bitów kodu);
 * kolejność stała, nowe tylko na końcu.
 */
public enum Zone {
	NONE,
	/** Koryto rzeki lub potoku (woda). */
	CHANNEL,
	/** Rdestnice w wodzie 1,5–5 m. */
	SUBMERGED_PLANTS,
	/** Grzybienie i grążele w wodzie 0,8–3 m. */
	FLOATING_LEAVED_PLANTS,
	/** Szuwar w wodzie do 1,5 m w pasie węższym niż biom. */
	REEDBED,
	/** Szuwar turzycowy i mozgowy na brzegu (h ≤ 0,3). */
	SHORE_REEDBED,
	/** Łacha na brzegu wypukłym dużej rzeki. */
	POINT_BAR,
	/** Wiklina nadrzeczna. */
	WILLOW_SCRUB,
	/** Okrajek (ziołorośla) za wikliną i w lukach łęgu. */
	HERB_FRINGE,
	/** Ziołorośla brzegu małej rzeki. */
	TALL_HERBS,
	/** Wierzba biała i krucha przy szerszej małej rzece na piasku. */
	RIVERSIDE_WILLOWS,
	/** Łozowisko (zarośla wierzby szarej) na skraju olsu. */
	WILLOW_CARR,
	/** Kamieniec (żwirowa łacha) potoku górskiego. */
	GRAVEL_BAR,
	/** Ziołorośla nadpotokowe wysoko w górach i w wąskich dolinach. */
	MONTANE_TALL_HERBS,
	/** Źródlisko. */
	SPRING_AREA,
	/** Pło jeziora dystroficznego. */
	FLOATING_MAT,
	/** Pas olszy lub brzozy przy jeziorze lobeliowym. */
	SHORE_ALDERS,
	/** Kidzina na plaży suchej. */
	STRANDLINE,
	/** Wydma inicjalna. */
	EMBRYO_DUNE,
	/** Czynna ściana klifu (goła glina). */
	CLIFF_FACE,
	/** Korona klifu: zarośla bez drzew. */
	CLIFF_TOP,
	/** Ostatnie metry pod górną granicą lasu: karłowe świerki. */
	TIMBERLINE,
	/** Tryb D: szpaler olszy przy cieku klasy B. */
	TREE_ROW;

	private static final Zone[] VALUES = values();

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Zone of(int ordinal) {
		return VALUES[ordinal];
	}
}
