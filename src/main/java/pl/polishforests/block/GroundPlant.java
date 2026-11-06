package pl.polishforests.block;

import java.util.Locale;

/**
 * The herb layer and litter blocks of the vertical slice (step V12 of docs/04-pionowy-wycinek.md, decision PW-6 (a): the
 * own blocks of the herb layer before M4). Each is an entry of a block family, so a new species is a new constant and a
 * texture of {@code tools/art}: {@link BerryShrubBlock} (a dwarf shrub with fruit, {@code berries} as the server state
 * of PW-8 and the mossy underside of PW-7), {@link SeasonalPlantBlock} (a spring geophyte in beds of 1–4 like vanilla
 * wildflowers: the stem under the grass tint, the flower without it; the phases of the year are drawn by the client,
 * PW-8 (a), step V13), {@link GroundCoverBlock} (a litter of 1–4 segments like vanilla leaf litter, not tinted, with the
 * mossy underside), a tall fern of two blocks (vanilla {@code DoublePlantBlock}; green, brown from mid-autumn in V13) and,
 * since step F2, a herb of one block as a cross ({@link SeasonalHerbBlock}: sedges, iris, ferns, nettle).
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
	BRACKEN(Kind.TALL),
	/** Sweet woodruff (Galium odoratum): star-like leaf whorls of the fertile beech forest, white in May (step F1). */
	SWEET_WOODRUFF(Kind.SEASONAL),
	/** Hairy wood-rush (Luzula pilosa): evergreen tufts of the acid beech forest, flowering in April and May (step F1). */
	HAIRY_WOOD_RUSH(Kind.SEASONAL),
	/** Wood sorrel (Oxalis acetosella): trefoils of the beech and spruce forests, white flowers in April and May (step F1). */
	WOOD_SORREL(Kind.SEASONAL),
	/** Coralroot (Cardamine bulbifera): a spring geophyte of the fertile beech forest, lilac in May (step F1). */
	CORALROOT(Kind.SEASONAL),
	/** Marsh marigold (Caltha palustris): rosettes of round glossy leaves in the hollows of the alder forests, yellow in April and May (step F2). */
	MARSH_MARIGOLD(Kind.SEASONAL),
	/** Yellow iris (Iris pseudacorus): sword leaves by the water of the alder carr, yellow flowers in June (step F2). */
	YELLOW_IRIS(Kind.HERB),
	/** Tussock sedge (Carex elata, C. elongata, C. remota): the tussocks of the alder forests, spikes in May and June (step F2). */
	TUSSOCK_SEDGE(Kind.HERB),
	/** Marsh fern (Thelypteris palustris): the fern of the alder carr, fronds from May to October (step F2). */
	MARSH_FERN(Kind.HERB),
	/** Stinging nettle (Urtica dioica): the nitrophilous herb of the ash-alder and alder forests, flowering in summer (step F2). */
	NETTLE(Kind.HERB),
	/**
	 * Glandular toothwort (Dentaria glandulosa = Cardamine glanduligera): the spring geophyte of the Carpathian beech forest
	 * (Dentario glandulosae-Fagetum), nodding purple flowers in April and May, gone from June (step F3).
	 */
	GLANDULAR_TOOTHWORT(Kind.SEASONAL),
	/**
	 * White wood-rush (Luzula luzuloides): evergreen tufts of the acid Carpathian beech forest (Luzulo luzuloidis-Fagetum) and
	 * the fir forests, whitish flowers in June and July (step F3).
	 */
	WHITE_WOOD_RUSH(Kind.SEASONAL),
	/** Perennial honesty (Lunaria rediviva): the tall herb of the sycamore ravines (Lunario-Aceretum), lilac in May and June (step F3). */
	PERENNIAL_HONESTY(Kind.HERB),
	/**
	 * Reindeer lichen (Cladonia rangiferina, C. arbuscula, C. mitis): the gray-white cushions of the dry pine forest
	 * (Cladonio-Pinetum, 60–90% of the floor) and the heath, a litter of 1–4 segments (step F4; the {@code pale_moss_carpet}
	 * of M2).
	 */
	REINDEER_LICHEN(Kind.COVER),
	/** Heather (Calluna vulgaris): the evergreen dwarf shrub of the heath and the dry pine forest, purple in August and September (step F4; the {@code pink_petals} of M2). */
	HEATHER(Kind.SEASONAL),
	/** Crowberry (Empetrum nigrum): the evergreen mats of the coastal pine forest (Empetro nigri-Pinetum) and the raised bog, black berries from July (step F4). */
	CROWBERRY(Kind.SEASONAL),
	/** Bog bilberry (Vaccinium uliginosum): the blue-green dwarf shrub of the bog woodland, a dwarf shrub of the bilberry's family (step F4). */
	BOG_BILBERRY(Kind.BERRY_SHRUB),
	/** Labrador tea (Rhododendron tomentosum = Ledum palustre): the aromatic shrub of the bog woodland, white umbels in May and June (step F4). */
	LABRADOR_TEA(Kind.HERB),
	/** Purple moor-grass (Molinia caerulea): the tussocks of the moist pine forest (Molinio-Pinetum), the purple panicles from July (step F4). */
	PURPLE_MOOR_GRASS(Kind.HERB);

	/**
	 * The block family of an entry (not saved: the order may change). Step F2: {@link #HERB}, a single-block cross plant
	 * of the size of vanilla's short grass with baked colors (not tinted) and the seasonal looks of {@link SeasonalHerbBlock}.
	 */
	public enum Kind {
		BERRY_SHRUB, SEASONAL, COVER, TALL, HERB
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
