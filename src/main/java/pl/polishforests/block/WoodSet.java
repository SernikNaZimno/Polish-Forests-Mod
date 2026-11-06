package pl.polishforests.block;

import java.util.Locale;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The own wood sets of the species without a vanilla wood (step P2 of docs/04-pionowy-wycinek.md, decision U-8 of docs/00
 * G.37): log, wood, stripped log, stripped wood, planks and, from the planks, stairs, slab, fence, fence gate, pressure
 * plate and button ({@link WoodBlocks}). Oak, birch and spruce keep the vanilla wood (D2). The Scots pine had its log,
 * wood, stripped log and wood and planks since step V7 (the LITE set, D1 b); P2 adds the products of its planks. The block
 * ids ({@code <prefix>_planks} …) go into the saved worlds (registry snapshot, PW-11): new wood sets only at the end.
 *
 * <p>The planks texture is derived in the client from a vanilla planks texture of the game ({@link #planksBase()}, by id:
 * no pixel of vanilla in the mod) with {@link PlanksTexture}: its brightness mapped onto the planks palette of the species
 * ({@link #planksPalette()}, dark to light, the colors of the fallback texture of {@code tools/art}) and a small change of
 * the grain decided by the species ({@link #planksStyle()}: the grain of every board moved and mirrored around its butt
 * joint, the contrast of the grain, knots). The seams and the joints of the vanilla planks stay where they are.
 */
public enum WoodSet {
	/** Scots pine (softwood): vanilla's conifer planks as the base, a distinct grain and two knots. */
	SCOTS_PINE("scots_pine", TreeTrunk.SCOTS_PINE, StrippedTrunk.SCOTS_PINE, "Scots Pine", "sosnow", "spruce_planks",
			new int[] {0x8c5d36, 0xae8754, 0xbc945e, 0xc7a068, 0xd2ac73}, 1.10, 2),
	/** Hornbeam: whitish gray, hard, an even fine texture almost without visible grain. */
	HORNBEAM("hornbeam", TreeTrunk.HORNBEAM, StrippedTrunk.HORNBEAM, "Hornbeam", "grabow", "oak_planks",
			new int[] {0x8f8672, 0xaea58f, 0xbdb49d, 0xc9c0a9, 0xd4ccb5}, 0.80, 0),
	/** Small-leaved linden: soft, pale yellowish white, very uniform. */
	LINDEN("linden", TreeTrunk.LINDEN, StrippedTrunk.LINDEN, "Linden", "lipow", "oak_planks",
			new int[] {0x97835e, 0xb7a37c, 0xc4b089, 0xcfbc95, 0xd9c7a0}, 0.75, 1),
	/** European beech: pale reddish brown, a fine grain with flecks. */
	BEECH("beech", TreeTrunk.BEECH, StrippedTrunk.BEECH, "Beech", "bukow", "oak_planks",
			new int[] {0x8c6248, 0xad8264, 0xbb8f70, 0xc69a7b, 0xd0a585}, 0.90, 0),
	/** European ash: light cream, ring-porous with a pronounced grain. */
	ASH("ash", TreeTrunk.ASH, StrippedTrunk.ASH, "Ash", "jesionow", "oak_planks",
			new int[] {0x8f7b58, 0xb19c76, 0xbfaa83, 0xcbb68f, 0xd6c29b}, 1.15, 0),
	/** Norway maple: creamy white with a faint pink cast, a fine even grain. */
	NORWAY_MAPLE("norway_maple", TreeTrunk.NORWAY_MAPLE, StrippedTrunk.NORWAY_MAPLE, "Norway Maple", "klonow", "oak_planks",
			new int[] {0x9c8770, 0xbea891, 0xcab59d, 0xd4c0a8, 0xddcab2}, 0.85, 1),
	/** Black alder (step F2): pale reddish brown to orange-brown, soft, an even fine texture without a heartwood. */
	BLACK_ALDER("black_alder", TreeTrunk.BLACK_ALDER, StrippedTrunk.BLACK_ALDER, "Alder", "olchow", "oak_planks",
			new int[] {0x8a5a40, 0xab7a5c, 0xb98868, 0xc49473, 0xcea07e}, 0.80, 1),
	/** Elm (step F2): a brown heartwood with a lighter sapwood, a coarse, ring-porous and wavy grain. */
	ELM("elm", TreeTrunk.ELM, StrippedTrunk.ELM, "Elm", "wiązow", "oak_planks",
			new int[] {0x6f5238, 0x8f6e4f, 0x9e7c5b, 0xab8966, 0xb89672}, 1.20, 1),
	/**
	 * Silver fir (step F3, softwood): white to pale yellowish with a faint pink cast, no colored heartwood, a distinct
	 * grain of the latewood and a few knots; vanilla's conifer planks as the base.
	 */
	FIR("fir", TreeTrunk.FIR, StrippedTrunk.FIR, "Fir", "jodłow", "spruce_planks",
			new int[] {0x9a8a72, 0xbcac90, 0xc8b99c, 0xd2c4a8, 0xdccfb4}, 1.00, 2),
	/** Sycamore maple (step F3): white to creamy, the lightest wood of the mod, a fine even grain with a silky sheen. */
	SYCAMORE_MAPLE("sycamore_maple", TreeTrunk.SYCAMORE_MAPLE, StrippedTrunk.SYCAMORE_MAPLE, "Sycamore", "jaworow", "oak_planks",
			new int[] {0xa39582, 0xc5b8a4, 0xd1c5b1, 0xdacfbc, 0xe2d8c6}, 0.75, 0),
	/**
	 * White willow (step F5): a light, soft wood, pinkish white sapwood around a pale reddish brown heartwood, a fine even
	 * grain. The poplar keeps the vanilla poplar wood of 26.3 (D2, U-8).
	 */
	WHITE_WILLOW("white_willow", TreeTrunk.WHITE_WILLOW, StrippedTrunk.WHITE_WILLOW, "Willow", "wierzbow", "oak_planks",
			new int[] {0x9a7f6c, 0xbaa08c, 0xc6ad99, 0xd0b8a4, 0xdac3b0}, 0.80, 1),
	/**
	 * Gray alder (step F6): a soft, light wood, pale reddish brown after cutting (as the black alder, a little lighter), a
	 * fine even grain without a visible heartwood.
	 */
	GRAY_ALDER("gray_alder", TreeTrunk.GRAY_ALDER, StrippedTrunk.GRAY_ALDER, "Gray Alder", "olszow", "oak_planks",
			new int[] {0x8f6650, 0xb08670, 0xbc937c, 0xc6a088, 0xd0ad96}, 0.85, 1),
	/** Rowan (step F6): a hard, dense wood, reddish white sapwood around a light reddish brown heartwood, a fine grain. */
	ROWAN("rowan", TreeTrunk.ROWAN, StrippedTrunk.ROWAN, "Rowan", "jarzębow", "oak_planks",
			new int[] {0x8a6556, 0xab8470, 0xb8917c, 0xc29e88, 0xcca994}, 0.95, 2);

	private final String prefix;
	private final TreeTrunk trunk;
	private final StrippedTrunk stripped;
	private final String englishName;
	private final String polishStem;
	private final String planksBase;
	private final int[] palette;
	private final double contrast;
	private final int knots;

	WoodSet(String prefix, TreeTrunk trunk, StrippedTrunk stripped, String englishName, String polishStem, String planksBase,
			int[] palette, double contrast, int knots) {
		this.prefix = prefix;
		this.trunk = trunk;
		this.stripped = stripped;
		this.englishName = englishName;
		this.polishStem = polishStem;
		this.planksBase = planksBase;
		this.palette = palette;
		this.contrast = contrast;
		this.knots = knots;
	}

	/** The id prefix of the blocks of the set ({@code <prefix>_planks}, {@code stripped_<prefix>_log} …). */
	public String prefix() {
		return prefix;
	}

	public Species species() {
		return trunk.species();
	}

	/** The thin trunk of the species (its lower bark): the map color of the bark. */
	public TreeTrunk trunk() {
		return trunk;
	}

	/** The stripped thin trunk of the species: the map color of the bare wood. */
	public StrippedTrunk stripped() {
		return stripped;
	}

	/** The English name of the wood ("Hornbeam" in "Hornbeam Planks"). */
	public String englishName() {
		return englishName;
	}

	/**
	 * The Polish adjective of the wood without its ending ("grabow" in "Deski grabowe", "Kłoda grabowa", "Płot grabowy"),
	 * after the noun as in the names of the pine set of V7.
	 */
	public String polishStem() {
		return polishStem;
	}

	/** The path of the vanilla planks texture the planks texture is derived from ({@code minecraft:block/<path>}). */
	public String planksBase() {
		return planksBase;
	}

	/** The planks palette, RGB dark to light: the derived texture lies on the ramp through these colors. */
	public int[] planksPalette() {
		return palette.clone();
	}

	/** How the planks texture of the species changes the vanilla planks (deterministic from the species). */
	public PlanksTexture.Style planksStyle() {
		return new PlanksTexture.Style(palette.clone(), contrast, knots, PlanksTexture.seed(prefix));
	}

	/** The path of the planks block and of its texture ({@code polishforests:block/<prefix>_planks}). */
	public String planksId() {
		return prefix + "_planks";
	}

	@Override
	public String toString() {
		return name().toLowerCase(Locale.ROOT);
	}
}
