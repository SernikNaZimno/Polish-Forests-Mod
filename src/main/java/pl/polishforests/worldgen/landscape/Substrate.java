package pl.polishforests.worldgen.landscape;

/** Surface deposit (soil parent material) at a given place. */
public enum Substrate {
	/** Outwash and dune sands. */
	SAND,
	/** Sand of the beach and the foredune: loose, without turf (white dune). */
	BEACH_SAND,
	/** Glacial till with boulders. */
	GLACIAL_TILL,
	/** River alluvium. */
	ALLUVIUM,
	/** Channel sand and gravel. */
	RIVERBED,
	/** Lake mud and gyttja. */
	LAKE_MUD,
	/** Fen peat. */
	PEAT,
	/** Carpathian flysch: sandstones and shales. */
	FLYSCH,
	/**
	 * Tatra granite (step TT1): the bedrock of the High Tatras. The Tatra rocks are the bedrock of the massif
	 * ({@code ColumnSample.Terrain#rock}), not a surface deposit: until TT2 the deposit of the Tatra columns stays flysch.
	 */
	GRANITE,
	/** Crystalline rocks of the Western Tatras: gneisses and schists (step TT1). */
	CRYSTALLINE,
	/** Limestone of the regle, the summit cover of the Western Tatras and the Belianske Tatras (step TT1). */
	LIMESTONE,
	/** Dolomite of the regle (step TT1). */
	DOLOMITE;

	/** Whether the substrate is a solid rock of the Tatras (step TT1). */
	public boolean isTatraRock() {
		return this == GRANITE || this == CRYSTALLINE || this == LIMESTONE || this == DOLOMITE;
	}
}
