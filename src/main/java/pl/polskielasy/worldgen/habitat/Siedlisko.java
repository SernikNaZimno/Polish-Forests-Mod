package pl.polskielasy.worldgen.habitat;

/**
 * Siedlisko kolumny, pakowane do jednego {@code int} (§3.4 planu M2):
 * biom 6 b | strefa 5 b | STL 5 b | zespół 5 b | pokrycie 2 b | flagi BUK/JODŁA/ŚWIERK/GRAB 4 b | gleba 5 b,
 * od najmłodszych bitów. Rekord służy komendom, testom i podglądowi; generacja trzyma sam kod.
 *
 * @param flagi maska {@link Gatunek#flaga()} gatunków w zasięgu
 */
public record Siedlisko(Biom biom, Strefa strefa, Stl stl, Zespol zespol, Pokrycie pokrycie, int flagi, Gleba gleba) {
	private static final int B_BIOM = 0;
	private static final int B_STREFA = 6;
	private static final int B_STL = 11;
	private static final int B_ZESPOL = 16;
	private static final int B_POKRYCIE = 21;
	private static final int B_FLAGI = 23;
	private static final int B_GLEBA = 27;

	static {
		// Pola muszą mieścić się w swoich bitach (enumy tylko dopisujemy, więc to pilnuje granic).
		check(Biom.values().length, 6);
		check(Strefa.values().length, 5);
		check(Stl.values().length, 5);
		check(Zespol.values().length, 5);
		check(Pokrycie.values().length, 2);
		check(Gleba.values().length, 5);
	}

	private static void check(int n, int bits) {
		if (n > 1 << bits) {
			throw new IllegalStateException("enum nie mieści się w " + bits + " bitach: " + n);
		}
	}

	/** Kod siedliska. */
	public static int pack(Biom biom, Strefa strefa, Stl stl, Zespol zespol, Pokrycie pokrycie, int flagi, Gleba gleba) {
		return biom.ordinal() << B_BIOM | strefa.ordinal() << B_STREFA | stl.ordinal() << B_STL
				| zespol.ordinal() << B_ZESPOL | pokrycie.ordinal() << B_POKRYCIE | (flagi & 15) << B_FLAGI
				| gleba.ordinal() << B_GLEBA;
	}

	public int kod() {
		return pack(biom, strefa, stl, zespol, pokrycie, flagi, gleba);
	}

	public static Siedlisko of(int kod) {
		return new Siedlisko(biom(kod), strefa(kod), stl(kod), zespol(kod), pokrycie(kod), flagi(kod), gleba(kod));
	}

	public static Biom biom(int kod) {
		return Biom.of(kod >>> B_BIOM & 63);
	}

	public static Strefa strefa(int kod) {
		return Strefa.of(kod >>> B_STREFA & 31);
	}

	public static Stl stl(int kod) {
		return Stl.of(kod >>> B_STL & 31);
	}

	public static Zespol zespol(int kod) {
		return Zespol.of(kod >>> B_ZESPOL & 31);
	}

	public static Pokrycie pokrycie(int kod) {
		return Pokrycie.of(kod >>> B_POKRYCIE & 3);
	}

	public static int flagi(int kod) {
		return kod >>> B_FLAGI & 15;
	}

	public static Gleba gleba(int kod) {
		return Gleba.of(kod >>> B_GLEBA & 31);
	}

	/** Czy gatunek z flagą jest w zasięgu (tylko buk, jodła, świerk i grab). */
	public static boolean ma(int kod, Gatunek g) {
		return (flagi(kod) & g.flaga()) != 0;
	}
}
