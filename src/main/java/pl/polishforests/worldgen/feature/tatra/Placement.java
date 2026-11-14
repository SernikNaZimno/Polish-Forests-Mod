package pl.polishforests.worldgen.feature.tatra;

/** A piece in a world cell (step TT6, {@link Piece}). */
public record Placement(int x, int y, int z, Piece piece, int param) {
	public Placement(int x, int y, int z, Piece piece) {
		this(x, y, z, piece, 0);
	}

	/** Packed cell key (see {@link Cells#pack}). */
	public long key() {
		return Cells.pack(x, y, z);
	}
}
