package pl.polishforests.worldgen.landscape;

/**
 * Landscape types. The first five are macroregion types assigned to cells; sea and coastland
 * follow from the land-sea field and the shape of the coast. The full list of target types is in
 * docs/01-architektura.md, section 3.3.
 */
public enum LandscapeType {
	/** Outwash plain: sands, gradient 1–3‰, dunes, tunnel valleys with lakes. */
	OUTWASH_PLAIN(Belt.LOWLAND, Substrate.SAND),
	/** Young-glacial moraine plateau: hummocks 5–30 m, end moraine ridges, kettle ponds, tunnel valleys. */
	MORAINE_PLATEAU(Belt.LOWLAND, Substrate.GLACIAL_TILL),
	/** Old glacial plain: flat, without lakes, valleys of large rivers. */
	OLD_GLACIAL_PLAIN(Belt.LOWLAND, Substrate.GLACIAL_TILL),
	/** Carpathian Foothills: rounded hills 300–600 m, relief 100–250 m. */
	FOOTHILLS(Belt.FOOTHILLS, Substrate.FLYSCH),
	/** Beskids: dome-shaped flysch mountains 500–1725 m, relief 400–900 m. */
	BESKIDS(Belt.MOUNTAINS, Substrate.FLYSCH),
	/** Coastland: beach, coastal dunes, cliff or spit with a lagoon. */
	COASTLAND(Belt.LOWLAND, Substrate.SAND),
	/** Sea (Baltic): shallow shelf with a sandy bottom. */
	SEA(Belt.SEA, Substrate.SAND);

	public enum Belt {
		SEA, LOWLAND, FOOTHILLS, MOUNTAINS
	}

	private final Belt belt;
	private final Substrate substrate;

	LandscapeType(Belt belt, Substrate substrate) {
		this.belt = belt;
		this.substrate = substrate;
	}

	public Belt belt() {
		return belt;
	}

	public Substrate defaultSubstrate() {
		return substrate;
	}

	public boolean isLowland() {
		return belt == Belt.LOWLAND;
	}

	public boolean isYoungGlacial() {
		return this == OUTWASH_PLAIN || this == MORAINE_PLATEAU;
	}

	/** Whether the type is assigned to macroregion cells (rather than following from the sea coast). */
	public boolean isRegionType() {
		return this != COASTLAND && this != SEA;
	}
}
