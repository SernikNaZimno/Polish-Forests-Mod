package pl.polishforests.worldgen.feature.tatra;

/**
 * What a planner of the Tatra details (step TT6) puts into a cell; the feature ({@code TatraDetailsFeature}) turns it
 * into a block state. {@code param} of a {@link Placement}: for {@link #STAIRS} the direction the stairs face (0 north,
 * 1 east, 2 south, 3 west, as {@link Dirs}), for {@link #LOG} the axis (0 along x, 1 along z), for {@link #LICHEN} the
 * number of segments (2-4), for the other pieces nothing.
 */
public enum Piece {
	/** Air (a carved cell). */
	AIR,
	/** The solid rock of the site (granite, gneiss, limestone or dolomite by the rock of the anchor). */
	ROCK,
	/** The loose rock debris of the site's rock (granite scree, limestone scree). */
	SCREE,
	/** A water source block. */
	SOURCE,
	/** Flowing water of level 7 beside a source (the lip of a waterfall). */
	FLOW_LIP,
	/** Falling water (the column of a waterfall). */
	FALL,
	/** A rock lichen carpet of {@code param} segments on the top of a boulder. */
	LICHEN,
	/** Larch planks of the mod (the floor and the walls of a hut). */
	PLANKS,
	/** A larch log of the mod (a ridge beam). */
	LOG,
	/** Larch stairs (the roof of a hut), facing {@code param}. */
	STAIRS,
	/** A vanilla hay block (the bedding of a hut). */
	HAY;

	/** Whether the piece is water. */
	public boolean water() {
		return this == SOURCE || this == FLOW_LIP || this == FALL;
	}
}
