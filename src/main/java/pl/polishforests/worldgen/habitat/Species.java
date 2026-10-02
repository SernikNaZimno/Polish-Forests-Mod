package pl.polishforests.worldgen.habitat;

/**
 * Gatunki drzew i krzewów: identyfikatory placed features {@code polskielasy:drzewo/<id>} i
 * {@code polskielasy:krzew/<id>} (§8.4) oraz reguły zasięgu ({@link SpeciesRanges}). Cztery gatunki mają flagę
 * w kodzie siedliska (4 bity): buk, jodła, świerk naturalny i grab.
 */
public enum Species {
	SCOTS_PINE("scots_pine", false, -1),
	SPRUCE("spruce", false, 2),
	FIR("fir", false, 1),
	BIRCH("birch", false, -1),
	OAK("oak", false, -1),
	BEECH("beech", false, 0),
	HORNBEAM("hornbeam", false, 3),
	LINDEN("linden", false, -1),
	ASH("ash", false, -1),
	ELM("elm", false, -1),
	NORWAY_MAPLE("norway_maple", false, -1),
	SYCAMORE_MAPLE("sycamore_maple", false, -1),
	BLACK_ALDER("black_alder", false, -1),
	GRAY_ALDER("gray_alder", false, -1),
	WHITE_WILLOW("white_willow", false, -1),
	POPLAR("poplar", false, -1),
	ROWAN("rowan", false, -1),
	/** Dąb bezszypułkowy (zasięg osobny od szypułkowego; użycie od M3). */
	SESSILE_OAK("sessile_oak", false, -1),
	LARCH("larch", false, -1),
	YEW("yew", false, -1),
	DWARF_MOUNTAIN_PINE("dwarf_mountain_pine", true, -1),
	OSIER("osier", true, -1),
	HAZEL("hazel", true, -1),
	JUNIPER("juniper", true, -1),
	IVY("ivy", true, -1);

	/** Flagi gatunków w kodzie siedliska: bit 0 buk, 1 jodła, 2 świerk naturalny, 3 grab. */
	public static final int FLAG_BEECH = 1;
	public static final int FLAG_FIR = 1 << 1;
	public static final int FLAG_SPRUCE = 1 << 2;
	public static final int FLAG_HORNBEAM = 1 << 3;

	private final String id;
	private final boolean shrub;
	private final int flagBit;

	Species(String id, boolean shrub, int flagBit) {
		this.id = id;
		this.shrub = shrub;
		this.flagBit = flagBit;
	}

	public String id() {
		return id;
	}

	/** Ścieżka placed feature'a: {@code drzewo/<id>} albo {@code krzew/<id>}. */
	public String path() {
		return (shrub ? "shrub/" : "tree/") + id;
	}

	/** Maska flagi w kodzie siedliska albo 0, gdy gatunek nie ma flagi. */
	public int flag() {
		return flagBit < 0 ? 0 : 1 << flagBit;
	}
}
