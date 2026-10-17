package pl.polishforests.block;

import pl.polishforests.worldgen.habitat.Species;

/**
 * The leaf block and the sapling of every species of the vertical slice (step V7 of docs/04-pionowy-wycinek.md, §1.1
 * and decision D2 (b)): {@code polishforests:<species>_leaves} ({@link SpeciesLeavesBlock}), the sapling
 * {@code polishforests:<species>_sapling} ({@link SpeciesSaplingBlock}) and its flower pot
 * {@code polishforests:potted_<species>_sapling}. The ids go into the saved worlds and are frozen after checkpoint CP-B
 * by the registry snapshot test; new species only at the end.
 *
 * <p>Textures (D2, V7): the Scots pine has its own needles and sapling, the broadleaf species share the own broadleaf
 * leaves texture (their color curves tell them apart) and have their own saplings; birch and spruce take the vanilla
 * leaves textures by id, and oak, birch and spruce the vanilla sapling textures by id (no copy of the files).
 */
public enum TreeFoliage {
	SCOTS_PINE(Species.SCOTS_PINE, "Scots Pine", "sosny zwyczajnej"),
	SPRUCE(Species.SPRUCE, "Norway Spruce", "świerka pospolitego"),
	BIRCH(Species.BIRCH, "Birch", "brzozy"),
	OAK(Species.OAK, "Pedunculate Oak", "dębu szypułkowego"),
	HORNBEAM(Species.HORNBEAM, "Hornbeam", "grabu zwyczajnego"),
	LINDEN(Species.LINDEN, "Small-leaved Linden", "lipy drobnolistnej"),
	BEECH(Species.BEECH, "European Beech", "buka zwyczajnego"),
	ASH(Species.ASH, "European Ash", "jesionu wyniosłego"),
	NORWAY_MAPLE(Species.NORWAY_MAPLE, "Norway Maple", "klonu zwyczajnego");

	private final Species species;
	private final String englishName;
	private final String polishGenitive;

	TreeFoliage(Species species, String englishName, String polishGenitive) {
		this.species = species;
		this.englishName = englishName;
		this.polishGenitive = polishGenitive;
	}

	public Species species() {
		return species;
	}

	public String leavesId() {
		return species.id() + "_leaves";
	}

	public String saplingId() {
		return species.id() + "_sapling";
	}

	public String pottedId() {
		return "potted_" + saplingId();
	}

	/** The configured feature the sapling grows into: {@code polishforests:sapling/<species>}. */
	public String featurePath() {
		return "sapling/" + species.id();
	}

	/** Needle-leaved: the Scots pine and the Norway spruce. */
	public boolean conifer() {
		return this == SCOTS_PINE || this == SPRUCE;
	}

	/** Keeps dead leaves over the winter on young trees and lower crowns: oak, hornbeam, beech ({@link MarcescentLeavesBlock}). */
	public boolean marcescent() {
		return this == OAK || this == HORNBEAM || this == BEECH;
	}

	public String englishName() {
		return englishName;
	}

	/** The species name in the Polish genitive ("liście dębu szypułkowego"). */
	public String polishGenitive() {
		return polishGenitive;
	}

	/** The foliage of a species, or null for a species without a leaf block. */
	public static TreeFoliage of(Species species) {
		for (TreeFoliage f : values()) {
			if (f.species == species) {
				return f;
			}
		}
		return null;
	}
}
