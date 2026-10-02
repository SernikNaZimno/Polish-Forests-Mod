package pl.polishforests.worldgen.landscape.m1;

// Zamrożona kopia kodu M1 (M2, krok S0), skopiowana bez zmian poza pakietem. Wzorzec dla
// GoldenTerrainTest i SampleKosztTest. NIE ZMIENIAĆ: zob. package-info.java.

public enum WaterKind {
	NONE,
	RIVER,
	/** Jezioro rynnowe. */
	LAKE,
	/** Oczko wytopiskowe z wodą. */
	KETTLE,
	/** Starorzecze w dnie doliny. */
	OXBOW,
	/** Morze i zalewy na poziomie morza. */
	SEA;

	public boolean isLake() {
		return this == LAKE || this == KETTLE || this == OXBOW;
	}
}
