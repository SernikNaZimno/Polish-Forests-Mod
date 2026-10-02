package pl.polskielasy.worldgen.landscape.m1;

// Zamrożona kopia kodu M1 (M2, krok S0), skopiowana bez zmian poza pakietem. Wzorzec dla
// GoldenTerrainTest i SampleKosztTest. NIE ZMIENIAĆ: zob. package-info.java.

/** Utwór powierzchniowy (skała macierzysta gleby) w danym miejscu. */
public enum Substrate {
	/** Piaski sandrowe i wydmowe. */
	SAND,
	/** Piasek plaży i wydmy przedniej: luźny, bez darni (biała wydma). */
	BEACH_SAND,
	/** Glina zwałowa z głazami. */
	GLACIAL_TILL,
	/** Mady rzeczne. */
	ALLUVIUM,
	/** Piasek i żwir korytowy. */
	RIVERBED,
	/** Muł i gytia jeziorna. */
	LAKE_MUD,
	/** Torf niski. */
	PEAT,
	/** Flisz karpacki: piaskowce i łupki. */
	FLYSCH
}
