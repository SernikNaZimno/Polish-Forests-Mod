package pl.polskielasy.worldgen.habitat;

import java.util.Locale;

/**
 * Strefy węższe niż ok. 12 m (Z4): nie są biomami, tylko polem siedliska w rozdzielczości bloku. Malują
 * glebę i mikrorelief w {@code fill()} i sterują roślinnością (docs/03-m2-biomy.md §4–§5). Strefa jest
 * niezależna od biomu, np. OKRAJEK wewnątrz łęgu wierzbowo-topolowego. Najwyżej 32 pozycje (5 bitów kodu);
 * kolejność stała, nowe tylko na końcu.
 */
public enum Strefa {
	BRAK,
	/** Koryto rzeki lub potoku (woda). */
	KORYTO,
	/** Rdestnice w wodzie 1,5–5 m. */
	ELODEIDY,
	/** Grzybienie i grążele w wodzie 0,8–3 m. */
	NYMFEIDY,
	/** Szuwar w wodzie do 1,5 m w pasie węższym niż biom. */
	SZUWAR,
	/** Szuwar turzycowy i mozgowy na brzegu (h ≤ 0,3). */
	SZUWAR_LADOWY,
	/** Łacha na brzegu wypukłym dużej rzeki. */
	LACHA,
	/** Wiklina nadrzeczna. */
	WIKLINA,
	/** Okrajek (ziołorośla) za wikliną i w lukach łęgu. */
	OKRAJEK,
	/** Ziołorośla brzegu małej rzeki. */
	ZIOLOROSLA,
	/** Wierzba biała i krucha przy szerszej małej rzece na piasku. */
	WIERZBY,
	/** Łozowisko (zarośla wierzby szarej) na skraju olsu. */
	LOZOWISKO,
	/** Kamieniec (żwirowa łacha) potoku górskiego. */
	KAMIENIEC,
	/** Ziołorośla nadpotokowe wysoko w górach i w wąskich dolinach. */
	ZIOLOROSLA_GORSKIE,
	/** Źródlisko. */
	ZRODLISKO,
	/** Pło jeziora dystroficznego. */
	PLO,
	/** Pas olszy lub brzozy przy jeziorze lobeliowym. */
	OLSZA_BRZEG,
	/** Kidzina na plaży suchej. */
	KIDZINA,
	/** Wydma inicjalna. */
	WYDMA_INICJALNA,
	/** Czynna ściana klifu (goła glina). */
	KLIF_SCIANA,
	/** Korona klifu: zarośla bez drzew. */
	KLIF_KORONA,
	/** Ostatnie metry pod górną granicą lasu: karłowe świerki. */
	GRANICA_LASU,
	/** Tryb D: szpaler olszy przy cieku klasy B. */
	SZPALER;

	private static final Strefa[] VALUES = values();

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Strefa of(int ordinal) {
		return VALUES[ordinal];
	}
}
