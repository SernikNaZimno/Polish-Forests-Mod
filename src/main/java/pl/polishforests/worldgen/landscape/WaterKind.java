package pl.polishforests.worldgen.landscape;

public enum WaterKind {
	NONE,
	RIVER,
	/** Tunnel valley lake. */
	LAKE,
	/** Water-filled kettle pond. */
	KETTLE,
	/** Oxbow lake on the valley floor. */
	OXBOW,
	/** Sea and lagoons at sea level. */
	SEA;

	public boolean isLake() {
		return this == LAKE || this == KETTLE || this == OXBOW;
	}
}
