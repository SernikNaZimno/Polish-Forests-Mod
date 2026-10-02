package pl.polskielasy.worldgen.habitat;

/**
 * Gleba kolumny (docs/03-m2-biomy.md §7.4). Bloki powierzchni dobiera {@code GlebaBloki} w S6; tu tylko
 * typ, wybierany ze strefy, a gdy strefa nie ma własnej gleby, z biomu. Najwyżej 32 pozycje.
 */
public enum Gleba {
	BIELICA_INICJALNA,
	BIELICA,
	RDZAWA,
	GLEJOWA_PIASZCZYSTA,
	TORF_WYSOKI,
	TORF_NISKI,
	BRUNATNA_KWASNA,
	BRUNATNA,
	BRUNATNA_BUKOWA,
	MURSZ,
	MADA_LEKKA,
	MADA_CIEZKA,
	MADA_ZWIROWA,
	GORSKA_BRUNATNA,
	BIELICA_GORSKA,
	RANKER,
	PIASEK_WYDMY,
	PLAZA,
	/** Goła glina ściany klifu. */
	GLINA,
	DNO_RZEKI,
	DNO_POTOKU,
	DNO_JEZIORA,
	DNO_DYSTROFICZNE,
	DNO_MORZA,
	DNO_ZALEWU;

	private static final Gleba[] VALUES = values();

	public static Gleba of(int ordinal) {
		return VALUES[ordinal];
	}

	/** Gleba dla biomu i strefy. */
	public static Gleba dla(Biom b, Strefa s) {
		switch (s) {
			case LACHA, WIKLINA -> {
				return b.wodny() ? dlaBiomu(b) : MADA_LEKKA;
			}
			case KAMIENIEC -> {
				return MADA_ZWIROWA;
			}
			case LOZOWISKO, SZUWAR_LADOWY -> {
				return TORF_NISKI;
			}
			case PLO -> {
				return b.wodny() ? DNO_DYSTROFICZNE : TORF_WYSOKI;
			}
			case ZRODLISKO -> {
				return MURSZ;
			}
			case KLIF_SCIANA -> {
				return GLINA;
			}
			case KIDZINA -> {
				return PLAZA;
			}
			case WYDMA_INICJALNA -> {
				return PIASEK_WYDMY;
			}
			default -> {
				return dlaBiomu(b);
			}
		}
	}

	private static Gleba dlaBiomu(Biom b) {
		return switch (b) {
			case BOR_SUCHY, WRZOSOWISKO -> BIELICA_INICJALNA;
			case BOR_SWIEZY, BOR_BAZYNOWY -> BIELICA;
			case BOR_MIESZANY -> RDZAWA;
			case BOR_WILGOTNY -> GLEJOWA_PIASZCZYSTA;
			case TORFOWISKO_WYSOKIE, BOR_BAGIENNY -> TORF_WYSOKI;
			case OLS, TORFOWISKO_NISKIE, SZUWAR -> TORF_NISKI;
			case LAS_MIESZANY, JEDLINA_WYZYNNA -> BRUNATNA_KWASNA;
			case GRAD, LAKA_SWIEZA, POLE -> BRUNATNA;
			case BUCZYNA_NIZINNA -> BRUNATNA_BUKOWA;
			case LEG_JESIONOWO_OLSZOWY -> MURSZ;
			case LEG_WIERZBOWO_TOPOLOWY, WIKLINY -> MADA_LEKKA;
			case LEG_WIAZOWO_JESIONOWY, LAKA_WILGOTNA -> MADA_CIEZKA;
			case OLSZYNA_GORSKA -> MADA_ZWIROWA;
			case BUCZYNA_GORSKA -> GORSKA_BRUNATNA;
			case SWIERCZYNA_GORSKA -> BIELICA_GORSKA;
			case KOSODRZEWINA, HALA -> RANKER;
			case WYDMA_BIALA, WYDMA_SZARA -> PIASEK_WYDMY;
			case PLAZA -> PLAZA;
			case RZEKA -> DNO_RZEKI;
			case POTOK -> DNO_POTOKU;
			case JEZIORO -> DNO_JEZIORA;
			case JEZIORO_DYSTROFICZNE -> DNO_DYSTROFICZNE;
			case MORZE -> DNO_MORZA;
			case ZALEW -> DNO_ZALEWU;
		};
	}
}
