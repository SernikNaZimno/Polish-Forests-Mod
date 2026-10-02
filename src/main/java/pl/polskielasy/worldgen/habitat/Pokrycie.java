package pl.polskielasy.worldgen.habitat;

/** Fizjonomia kolumny: las, zarośla, teren otwarty albo woda (2 bity kodu siedliska). */
public enum Pokrycie {
	LAS,
	/** Zarośla krzewiaste: wikliny, kosodrzewina. */
	ZAROSLA,
	/** Teren otwarty: łąki, torfowiska, szuwar, wydmy, plaża, pole, hala. */
	OTWARTE,
	WODA;

	private static final Pokrycie[] VALUES = values();

	public static Pokrycie of(int ordinal) {
		return VALUES[ordinal];
	}

	/** Pokrycie biomu. */
	public static Pokrycie dla(Biom b) {
		return switch (b) {
			case WIKLINY, KOSODRZEWINA -> ZAROSLA;
			default -> switch (b.grupa()) {
				case LESNY -> LAS;
				case NIELESNY -> OTWARTE;
				case WODNY -> WODA;
			};
		};
	}
}
