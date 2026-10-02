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
	FLYSCH
}
