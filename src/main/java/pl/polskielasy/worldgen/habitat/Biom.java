package pl.polskielasy.worldgen.habitat;

import java.util.List;

/**
 * Biomy świata „Polska”: 36 grup siedliskowych (decyzja M2-A, docs/03-m2-biomy.md §2). Identyfikatory
 * {@code polskielasy:<id>} są zamrożone od M2, bo chunki zapisują biomy po nazwie. Kolejność enuma jest
 * stała (kod siedliska zapisuje {@link #ordinal()}); nowe biomy dopisujemy tylko na końcu.
 */
public enum Biom {
	// Leśne (18)
	BOR_SUCHY("bor_suchy", Grupa.LESNY, "Bór suchy", "Dry pine forest"),
	BOR_SWIEZY("bor_swiezy", Grupa.LESNY, "Bór świeży", "Fresh pine forest"),
	BOR_BAZYNOWY("bor_bazynowy", Grupa.LESNY, "Nadmorski bór bażynowy", "Coastal crowberry pine forest"),
	BOR_WILGOTNY("bor_wilgotny", Grupa.LESNY, "Bór wilgotny", "Moist pine forest"),
	BOR_BAGIENNY("bor_bagienny", Grupa.LESNY, "Bór bagienny", "Bog pine forest"),
	BOR_MIESZANY("bor_mieszany", Grupa.LESNY, "Bór mieszany", "Mixed pine-oak forest"),
	LAS_MIESZANY("las_mieszany", Grupa.LESNY, "Las mieszany", "Mixed forest"),
	GRAD("grad", Grupa.LESNY, "Grąd", "Oak-hornbeam forest"),
	BUCZYNA_NIZINNA("buczyna_nizinna", Grupa.LESNY, "Buczyna niżowa", "Lowland beech forest"),
	OLS("ols", Grupa.LESNY, "Ols", "Alder carr"),
	LEG_JESIONOWO_OLSZOWY("leg_jesionowo_olszowy", Grupa.LESNY, "Łęg jesionowo-olszowy", "Ash-alder riparian forest"),
	LEG_WIERZBOWO_TOPOLOWY("leg_wierzbowo_topolowy", Grupa.LESNY, "Łęg wierzbowo-topolowy", "Willow-poplar floodplain forest"),
	LEG_WIAZOWO_JESIONOWY("leg_wiazowo_jesionowy", Grupa.LESNY, "Łęg wiązowo-jesionowy", "Elm-ash floodplain forest"),
	JEDLINA_WYZYNNA("jedlina_wyzynna", Grupa.LESNY, "Wyżynna jedlina i buczyna", "Upland fir and beech forest"),
	BUCZYNA_GORSKA("buczyna_gorska", Grupa.LESNY, "Buczyna karpacka", "Carpathian beech forest"),
	SWIERCZYNA_GORSKA("swierczyna_gorska", Grupa.LESNY, "Świerczyna górska", "Montane spruce forest"),
	OLSZYNA_GORSKA("olszyna_gorska", Grupa.LESNY, "Olszyna górska", "Grey alder forest"),
	KOSODRZEWINA("kosodrzewina", Grupa.LESNY, "Kosodrzewina", "Dwarf mountain pine"),
	// Nieleśne lądowe (12)
	TORFOWISKO_WYSOKIE("torfowisko_wysokie", Grupa.NIELESNY, "Torfowisko wysokie", "Raised bog"),
	TORFOWISKO_NISKIE("torfowisko_niskie", Grupa.NIELESNY, "Torfowisko niskie i przejściowe", "Fen"),
	SZUWAR("szuwar", Grupa.NIELESNY, "Szuwar", "Reed bed"),
	WIKLINY("wikliny", Grupa.NIELESNY, "Wikliny nadrzeczne", "Riverside willow scrub"),
	WRZOSOWISKO("wrzosowisko", Grupa.NIELESNY, "Wrzosowisko i murawa napiaskowa", "Heathland"),
	LAKA_WILGOTNA("laka_wilgotna", Grupa.NIELESNY, "Łąka wilgotna", "Wet meadow"),
	LAKA_SWIEZA("laka_swieza", Grupa.NIELESNY, "Łąka świeża i polana", "Meadow"),
	POLE("pole", Grupa.NIELESNY, "Pole", "Field"),
	PLAZA("plaza", Grupa.NIELESNY, "Plaża", "Beach"),
	WYDMA_BIALA("wydma_biala", Grupa.NIELESNY, "Wydma biała", "White dune"),
	WYDMA_SZARA("wydma_szara", Grupa.NIELESNY, "Wydma szara", "Grey dune"),
	HALA("hala", Grupa.NIELESNY, "Piętro alpejskie", "Alpine meadow"),
	// Wodne (6)
	MORZE("morze", Grupa.WODNY, "Morze", "Sea"),
	ZALEW("zalew", Grupa.WODNY, "Zalew", "Lagoon"),
	RZEKA("rzeka", Grupa.WODNY, "Rzeka", "River"),
	POTOK("potok", Grupa.WODNY, "Potok", "Mountain stream"),
	JEZIORO("jezioro", Grupa.WODNY, "Jezioro", "Lake"),
	JEZIORO_DYSTROFICZNE("jezioro_dystroficzne", Grupa.WODNY, "Jezioro dystroficzne", "Dystrophic lake");

	/** Grupa biomu: leśny, nieleśny lądowy, wodny. */
	public enum Grupa {
		LESNY, NIELESNY, WODNY
	}

	private static final Biom[] VALUES = values();
	/** Bory (siedliska borowe), także bór bażynowy; do udziałów i palet. */
	public static final List<Biom> BORY = List.of(BOR_SUCHY, BOR_SWIEZY, BOR_BAZYNOWY, BOR_WILGOTNY, BOR_BAGIENNY,
			BOR_MIESZANY);
	/** Łęgi (do testu „brak łęgu poza dnem”). */
	public static final List<Biom> LEGI = List.of(LEG_JESIONOWO_OLSZOWY, LEG_WIERZBOWO_TOPOLOWY, LEG_WIAZOWO_JESIONOWY);

	private final String id;
	private final Grupa grupa;
	private final String nazwa;
	private final String nazwaEn;

	Biom(String id, Grupa grupa, String nazwa, String nazwaEn) {
		this.id = id;
		this.grupa = grupa;
		this.nazwa = nazwa;
		this.nazwaEn = nazwaEn;
	}

	/** Identyfikator bez przestrzeni nazw (zamrożony). */
	public String id() {
		return id;
	}

	public Grupa grupa() {
		return grupa;
	}

	/** Nazwa polska (wstępna, lang przychodzi z datagenu w S5). */
	public String nazwa() {
		return nazwa;
	}

	public String nazwaEn() {
		return nazwaEn;
	}

	public boolean lesny() {
		return grupa == Grupa.LESNY;
	}

	public boolean wodny() {
		return grupa == Grupa.WODNY;
	}

	public boolean bor() {
		return BORY.contains(this);
	}

	/** Biom o numerze {@link #ordinal()}. */
	public static Biom of(int ordinal) {
		return VALUES[ordinal];
	}
}
