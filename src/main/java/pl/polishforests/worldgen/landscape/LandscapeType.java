package pl.polishforests.worldgen.landscape;

/**
 * Typy krajobrazu. Pięć pierwszych to typy makroregionów przydzielane komórkom; morze i pobrzeże
 * wynikają z pola ląd–morze i kształtu brzegu. Pełna lista docelowych typów jest w
 * docs/01-architektura.md, sekcja 3.3.
 */
public enum LandscapeType {
	/** Równina sandrowa: piaski, spadek 1–3‰, wydmy, rynny z jeziorami. */
	OUTWASH_PLAIN(Belt.LOWLAND, Substrate.SAND),
	/** Wysoczyzna morenowa młodoglacjalna: pagórki 5–30 m, wały moren czołowych, oczka, rynny. */
	MORAINE_PLATEAU(Belt.LOWLAND, Substrate.GLACIAL_TILL),
	/** Równina staroglacjalna: płaska, bez jezior, doliny wielkich rzek. */
	OLD_GLACIAL_PLAIN(Belt.LOWLAND, Substrate.GLACIAL_TILL),
	/** Pogórze karpackie: garby 300–600 m, deniwelacje 100–250 m. */
	FOOTHILLS(Belt.FOOTHILLS, Substrate.FLYSCH),
	/** Beskidy: kopulaste góry fliszowe 500–1725 m, deniwelacje 400–900 m. */
	BESKIDS(Belt.MOUNTAINS, Substrate.FLYSCH),
	/** Pobrzeże: plaża, wydmy nadmorskie, klif lub mierzeja z zalewem. */
	COASTLAND(Belt.LOWLAND, Substrate.SAND),
	/** Morze (Bałtyk): płytki szelf z piaszczystym dnem. */
	SEA(Belt.SEA, Substrate.SAND);

	public enum Belt {
		SEA, LOWLAND, FOOTHILLS, MOUNTAINS
	}

	private final Belt belt;
	private final Substrate substrate;

	LandscapeType(Belt belt, Substrate substrate) {
		this.belt = belt;
		this.substrate = substrate;
	}

	public Belt belt() {
		return belt;
	}

	public Substrate defaultSubstrate() {
		return substrate;
	}

	public boolean isLowland() {
		return belt == Belt.LOWLAND;
	}

	public boolean isYoungGlacial() {
		return this == OUTWASH_PLAIN || this == MORAINE_PLATEAU;
	}

	/** Czy typ jest przydzielany komórkom makroregionów (a nie wynika z brzegu morza). */
	public boolean isRegionType() {
		return this != COASTLAND && this != SEA;
	}
}
