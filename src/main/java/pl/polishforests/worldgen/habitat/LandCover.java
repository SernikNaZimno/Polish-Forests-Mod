package pl.polishforests.worldgen.habitat;

/** Physiognomy of a column: forest, scrub, open land or water (2 bits of the habitat code). */
public enum LandCover {
	FOREST,
	/** Shrub scrub: willow scrub, dwarf pine. */
	SCRUB,
	/** Open land: meadows, mires, reedbeds, dunes, beach, arable land, alpine grassland. */
	OPEN,
	WATER;

	private static final LandCover[] VALUES = values();

	public static LandCover of(int ordinal) {
		return VALUES[ordinal];
	}

	/** Land cover of a biome. */
	public static LandCover forBiome(HabitatBiome b) {
		return switch (b) {
			case WILLOW_SCRUB, DWARF_PINE_SCRUB -> SCRUB;
			default -> switch (b.group()) {
				case FOREST -> FOREST;
				case NON_FOREST -> OPEN;
				case WATER -> WATER;
			};
		};
	}
}
