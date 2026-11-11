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
	SEA,
	/**
	 * Step TT2: a Tatra tarn in the bowl of a cirque or behind a moraine, with its own level; always in a closed basin (its
	 * shore at least at the level).
	 */
	TARN;

	public boolean isLake() {
		return this == LAKE || this == KETTLE || this == OXBOW || this == TARN;
	}
}
