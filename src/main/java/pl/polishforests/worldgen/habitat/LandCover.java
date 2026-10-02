package pl.polishforests.worldgen.habitat;

/** Fizjonomia kolumny: las, zarośla, teren otwarty albo woda (2 bity kodu siedliska). */
public enum LandCover {
	FOREST,
	/** Zarośla krzewiaste: wikliny, kosodrzewina. */
	SCRUB,
	/** Teren otwarty: łąki, torfowiska, szuwar, wydmy, plaża, pole, hala. */
	OPEN,
	WATER;

	private static final LandCover[] VALUES = values();

	public static LandCover of(int ordinal) {
		return VALUES[ordinal];
	}

	/** Pokrycie biomu. */
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
