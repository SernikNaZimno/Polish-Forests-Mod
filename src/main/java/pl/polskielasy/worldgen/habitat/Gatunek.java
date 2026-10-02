package pl.polskielasy.worldgen.habitat;

/**
 * Gatunki drzew i krzewów: identyfikatory placed features {@code polskielasy:drzewo/<id>} i
 * {@code polskielasy:krzew/<id>} (§8.4) oraz reguły zasięgu ({@link Zasiegi}). Cztery gatunki mają flagę
 * w kodzie siedliska (4 bity): buk, jodła, świerk naturalny i grab.
 */
public enum Gatunek {
	SOSNA("sosna", false, -1),
	SWIERK("swierk", false, 2),
	JODLA("jodla", false, 1),
	BRZOZA("brzoza", false, -1),
	DAB("dab", false, -1),
	BUK("buk", false, 0),
	GRAB("grab", false, 3),
	LIPA("lipa", false, -1),
	JESION("jesion", false, -1),
	WIAZ("wiaz", false, -1),
	KLON("klon", false, -1),
	JAWOR("jawor", false, -1),
	OLSZA("olsza", false, -1),
	OLSZA_SZARA("olsza_szara", false, -1),
	WIERZBA("wierzba", false, -1),
	TOPOLA("topola", false, -1),
	JARZAB("jarzab", false, -1),
	/** Dąb bezszypułkowy (zasięg osobny od szypułkowego; użycie od M3). */
	DAB_BEZSZYPULKOWY("dab_bezszypulkowy", false, -1),
	MODRZEW("modrzew", false, -1),
	CIS("cis", false, -1),
	KOSODRZEWINA("kosodrzewina", true, -1),
	WIKLINA("wiklina", true, -1),
	LESZCZYNA("leszczyna", true, -1),
	JALOWIEC("jalowiec", true, -1),
	BLUSZCZ("bluszcz", true, -1);

	/** Flagi gatunków w kodzie siedliska: bit 0 buk, 1 jodła, 2 świerk naturalny, 3 grab. */
	public static final int FLAGA_BUK = 1;
	public static final int FLAGA_JODLA = 1 << 1;
	public static final int FLAGA_SWIERK = 1 << 2;
	public static final int FLAGA_GRAB = 1 << 3;

	private final String id;
	private final boolean krzew;
	private final int bitFlagi;

	Gatunek(String id, boolean krzew, int bitFlagi) {
		this.id = id;
		this.krzew = krzew;
		this.bitFlagi = bitFlagi;
	}

	public String id() {
		return id;
	}

	/** Ścieżka placed feature'a: {@code drzewo/<id>} albo {@code krzew/<id>}. */
	public String sciezka() {
		return (krzew ? "krzew/" : "drzewo/") + id;
	}

	/** Maska flagi w kodzie siedliska albo 0, gdy gatunek nie ma flagi. */
	public int flaga() {
		return bitFlagi < 0 ? 0 : 1 << bitFlagi;
	}
}
