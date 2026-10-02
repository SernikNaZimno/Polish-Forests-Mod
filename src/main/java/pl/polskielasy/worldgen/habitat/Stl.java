package pl.polskielasy.worldgen.habitat;

/**
 * Siedliskowy typ lasu (STL, nomenklatura Lasów Państwowych). W siedliskach nieleśnych trybu N pole ma
 * wartość {@link #BRAK}; w trybie D niezalesione siedlisko zachowuje STL potencjalny. Najwyżej 32 pozycje.
 */
public enum Stl {
	BRAK("-"),
	/** Bór suchy. */
	BS("Bs"),
	BSW("Bśw"),
	BW("Bw"),
	BB("Bb"),
	BMSW("BMśw"),
	BMW("BMw"),
	BMB("BMb"),
	LMSW("LMśw"),
	LMW("LMw"),
	LMB("LMb"),
	LSW("Lśw"),
	LW("Lw"),
	/** Ols. */
	OL("Ol"),
	/** Ols jesionowy (łęg jesionowo-olszowy). */
	OLJ("OlJ"),
	/** Las łęgowy (łęgi nadrzeczne). */
	LL("Lł"),
	BMWYZ("BMwyż"),
	LMWYZ("LMwyż"),
	LWYZ("Lwyż"),
	/** Las górski (buczyna). */
	LG("LG"),
	LMG("LMG"),
	BMG("BMG"),
	BWG("BWG"),
	/** Bór górski (świerczyna). */
	BG("BG"),
	/** Las łęgowy górski (olszyna). */
	LLG("LłG"),
	OLJG("OlJG"),
	/** Piętro subalpejskie. */
	SUBALP("subalp");

	private static final Stl[] VALUES = values();

	private final String skrot;

	Stl(String skrot) {
		this.skrot = skrot;
	}

	/** Skrót LP (z polskimi znakami). */
	public String skrot() {
		return skrot;
	}

	public static Stl of(int ordinal) {
		return VALUES[ordinal];
	}

	/** STL strefowy z trofii i wilgotności (siedliska niżowe, §3.3). */
	public static Stl strefowy(Trofia t, Wilgotnosc w) {
		return switch (t) {
			case B -> switch (w) {
				case SUCHA -> BS;
				case SWIEZA -> BSW;
				case WILGOTNA -> BW;
				case BAGIENNA -> BB;
			};
			case BM -> switch (w) {
				case SUCHA, SWIEZA -> BMSW;
				case WILGOTNA -> BMW;
				case BAGIENNA -> BMB;
			};
			case LM -> switch (w) {
				case SUCHA, SWIEZA -> LMSW;
				case WILGOTNA -> LMW;
				case BAGIENNA -> LMB;
			};
			case L -> switch (w) {
				case SUCHA, SWIEZA -> LSW;
				case WILGOTNA -> LW;
				case BAGIENNA -> OL;
			};
		};
	}

	/**
	 * STL lasu w biomie leśnym. Biomy strefowe dostają {@link #strefowy}, pozostałe typ wynikający z biomu
	 * (z odmianą według trofii i wilgotności tam, gdzie biom obejmuje kilka typów).
	 */
	public static Stl lasu(Biom b, Trofia t, Wilgotnosc w) {
		boolean wilg = w == Wilgotnosc.WILGOTNA || w == Wilgotnosc.BAGIENNA;
		return switch (b) {
			case BOR_SUCHY -> BS;
			case BOR_SWIEZY -> BSW;
			case BOR_BAZYNOWY -> w == Wilgotnosc.SUCHA ? BS : BSW;
			case BOR_WILGOTNY -> t == Trofia.B ? BW : BMW;
			case BOR_BAGIENNY -> t == Trofia.B ? BB : BMB;
			case BOR_MIESZANY -> wilg ? BMW : BMSW;
			case LAS_MIESZANY -> wilg ? LMW : LMSW;
			case GRAD -> wilg ? LW : LSW;
			case BUCZYNA_NIZINNA -> t == Trofia.L ? LSW : LMSW;
			case OLS -> t == Trofia.L ? OL : LMB;
			case LEG_JESIONOWO_OLSZOWY -> OLJ;
			case LEG_WIERZBOWO_TOPOLOWY, LEG_WIAZOWO_JESIONOWY -> LL;
			case JEDLINA_WYZYNNA -> switch (t) {
				case B, BM -> BMWYZ;
				case LM -> LMWYZ;
				case L -> LWYZ;
			};
			case BUCZYNA_GORSKA -> t == Trofia.L ? LG : LMG;
			case SWIERCZYNA_GORSKA -> wilg ? BWG : t == Trofia.B || t == Trofia.BM ? BG : BMG;
			case OLSZYNA_GORSKA -> w == Wilgotnosc.BAGIENNA ? OLJG : LLG;
			case KOSODRZEWINA -> SUBALP;
			default -> strefowy(t, w);
		};
	}
}
