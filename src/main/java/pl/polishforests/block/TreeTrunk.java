package pl.polishforests.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The thin trunk blocks of the vertical slice (step V2, docs/04-pionowy-wycinek.md §1.1): one {@link TrunkBlock} per
 * species of the slice, and the Scots pine has a second block for the thin orange bark of the upper trunk (the "mirror",
 * decision PW-11). The block id ({@link #id()}) goes into the saved worlds and is frozen after checkpoint CP-B by the
 * registry snapshot test; new trunks only at the end.
 *
 * <p>Bark (D2): birch and spruce use the vanilla log texture by id, the others the textures of {@code tools/art} (oak,
 * pine, hornbeam, linden, beech), the ash and the Norway maple a shared substitute bark of the broad-crowned archetype
 * (§1.1). Loot (§1.1): radius ≤ 2 gives sticks, radius ≥ 3 the log of the species or, until the species has its own log,
 * the closest vanilla log ({@link #log()}).
 */
public enum TreeTrunk {
	SCOTS_PINE("scots_pine_trunk", Species.SCOTS_PINE, MapColor.COLOR_BROWN, "Scots Pine Trunk", "Pień sosny zwyczajnej"),
	/** The upper trunk of the Scots pine: thin, flaky, rust-orange bark ("mirror"), a block of its own (PW-11). */
	SCOTS_PINE_UPPER("scots_pine_upper_trunk", Species.SCOTS_PINE, MapColor.COLOR_ORANGE, "Scots Pine Upper Trunk",
			"Górny pień sosny zwyczajnej"),
	SPRUCE("spruce_trunk", Species.SPRUCE, MapColor.PODZOL, "Norway Spruce Trunk", "Pień świerka pospolitego"),
	BIRCH("birch_trunk", Species.BIRCH, MapColor.QUARTZ, "Birch Trunk", "Pień brzozy"),
	OAK("oak_trunk", Species.OAK, MapColor.PODZOL, "Pedunculate Oak Trunk", "Pień dębu szypułkowego"),
	HORNBEAM("hornbeam_trunk", Species.HORNBEAM, MapColor.STONE, "Hornbeam Trunk", "Pień grabu zwyczajnego"),
	LINDEN("linden_trunk", Species.LINDEN, MapColor.COLOR_GRAY, "Small-leaved Linden Trunk", "Pień lipy drobnolistnej"),
	BEECH("beech_trunk", Species.BEECH, MapColor.COLOR_LIGHT_GRAY, "European Beech Trunk", "Pień buka zwyczajnego"),
	ASH("ash_trunk", Species.ASH, MapColor.COLOR_GRAY, "European Ash Trunk", "Pień jesionu wyniosłego"),
	NORWAY_MAPLE("norway_maple_trunk", Species.NORWAY_MAPLE, MapColor.COLOR_GRAY, "Norway Maple Trunk",
			"Pień klonu zwyczajnego");

	private final String id;
	private final Species species;
	private final MapColor mapColor;
	private final String englishName;
	private final String polishName;

	TreeTrunk(String id, Species species, MapColor mapColor, String englishName, String polishName) {
		this.id = id;
		this.species = species;
		this.mapColor = mapColor;
		this.englishName = englishName;
		this.polishName = polishName;
	}

	/** Block and item path in the {@code polishforests} namespace (saved in worlds). */
	public String id() {
		return id;
	}

	public Species species() {
		return species;
	}

	public MapColor mapColor() {
		return mapColor;
	}

	public String englishName() {
		return englishName;
	}

	public String polishName() {
		return polishName;
	}

	/**
	 * The log dropped by a trunk of radius ≥ 3: the vanilla log of oak, birch and spruce (D2); for the others the closest
	 * vanilla log until their own wood comes (the LITE set of the Scots pine in V7, the other species in M3): the gray
	 * barks of hornbeam and beech give the pale oak log, linden, ash and maple the oak log, the pine the spruce log.
	 */
	public Block log() {
		return switch (this) {
			case SCOTS_PINE, SCOTS_PINE_UPPER, SPRUCE -> Blocks.SPRUCE_LOG;
			case BIRCH -> Blocks.BIRCH_LOG;
			case OAK, LINDEN, ASH, NORWAY_MAPLE -> Blocks.OAK_LOG;
			case HORNBEAM, BEECH -> Blocks.PALE_OAK_LOG;
		};
	}
}
