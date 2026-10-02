package pl.polskielasy.worldgen.landscape;

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
