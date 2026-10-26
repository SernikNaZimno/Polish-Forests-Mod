package pl.polishforests.block;

import java.util.Locale;

/**
 * The herb layer and litter blocks of the vertical slice (step V12 of docs/04-pionowy-wycinek.md, decision PW-6 (a): the
 * own blocks of the herb layer before M4). Each is an entry of a block family, so a new species is a new constant and a
 * texture of {@code tools/art}: {@link BerryShrubBlock} (a dwarf shrub with fruit, {@code berries} as the server state
 * of PW-8 and the mossy underside of PW-7), {@link SeasonalPlantBlock} (a spring geophyte in beds of 1–4 like vanilla
 * wildflowers: the stem under the grass tint, the flower without it; the phases of the year are drawn by the client,
 * PW-8 (a), step V13), {@link GroundCoverBlock} (a litter of 1–4 segments like vanilla leaf litter, not tinted, with the
 * mossy underside) and a tall fern of two blocks (vanilla {@code DoublePlantBlock}; green, brown from mid-autumn in V13).
 * The ids go into saved worlds and are frozen (PW-11): new constants only at the end.
 */
public enum GroundPlant {
	/** Bilberry (Vaccinium myrtillus): no thorns and no slowing down (decision M2-13), fruit to pick. */
	BILBERRY(Kind.BERRY_SHRUB),
	/** Wood anemone (Anemone nemorosa): carpets of the oak-hornbeam forest in spring. */
	WOOD_ANEMONE(Kind.SEASONAL),
	/** Liverleaf (Hepatica nobilis): tufts in the oak-hornbeam forest in early spring. */
	HEPATICA(Kind.SEASONAL),
	/** Scots pine needle litter: a rust-brown mat of 1–4 segments. */
	NEEDLE_LITTER(Kind.COVER),
	/** Bracken (Pteridium aquilinum): two blocks, the indicator of the mixed coniferous forest. */
	BRACKEN(Kind.TALL);

	/** The block family of an entry. */
	public enum Kind {
		BERRY_SHRUB, SEASONAL, COVER, TALL
	}

	private final Kind kind;

	GroundPlant(Kind kind) {
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	/** Block and item path, e.g. {@code wood_anemone}. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Whether the block has the mossy underside (PW-7): the dwarf shrub and the litter of the pine forests. */
	public boolean mossy() {
		return kind == Kind.BERRY_SHRUB || kind == Kind.COVER;
	}
}
