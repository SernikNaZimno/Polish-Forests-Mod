package pl.polishforests.worldgen.habitat;

/**
 * Tree and shrub species: placed feature identifiers {@code polishforests:tree/<id>} (the procedural tree of the tree
 * planner, step V9; only for the species with a planner shape), {@code polishforests:tree_substitute/<id>} (the vanilla
 * placeholder of M2, decision PW-3) and {@code polishforests:shrub/<id>} (§8.4) and range rules ({@link SpeciesRanges}).
 * Four species have a flag in the habitat code (4 bits): beech, fir, native spruce and hornbeam. New constants only at
 * the end.
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
	/** Sessile oak (range separate from pedunculate oak; used from M3). */
	SESSILE_OAK("sessile_oak", false, -1),
	LARCH("larch", false, -1),
	YEW("yew", false, -1),
	DWARF_MOUNTAIN_PINE("dwarf_mountain_pine", true, -1),
	OSIER("osier", true, -1),
	HAZEL("hazel", true, -1),
	JUNIPER("juniper", true, -1),
	IVY("ivy", true, -1),
	/** Alder buckthorn, Frangula alnus (the shrub layer of the pine forests, step V9). */
	ALDER_BUCKTHORN("alder_buckthorn", true, -1);

	/** Species flags in the habitat code: bit 0 beech, 1 fir, 2 native spruce, 3 hornbeam. */
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

	/** Path of the placeholder tree of M2 (decision PW-3): {@code tree_substitute/<id>}; for a shrub its {@link #path}. */
	public String substitutePath() {
		return shrub ? path() : "tree_substitute/" + id;
	}

	/** Whether the species is a shrub ({@code shrub/<id>}). */
	public boolean shrub() {
		return shrub;
	}

	/** Placed feature path: {@code tree/<id>} or {@code shrub/<id>}. */
	public String path() {
		return (shrub ? "shrub/" : "tree/") + id;
	}

	/** Flag mask in the habitat code, or 0 when the species has no flag. */
	public int flag() {
		return flagBit < 0 ? 0 : 1 << flagBit;
	}
}
