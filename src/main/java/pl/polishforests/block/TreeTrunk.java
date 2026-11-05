package pl.polishforests.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The thin trunk blocks of the vertical slice (step V2, docs/04-pionowy-wycinek.md §1.1): one {@link TrunkBlock} per
 * species of the slice, and the Scots pine has a second block for the thin orange bark of the upper trunk (the "mirror",
 * decision PW-11) and two more for the transition zone between the two, its lower and its upper block (step V1b, choice
 * V1-i; V1b review round 1). The block id
 * ({@link #id()}) goes into the saved worlds and is frozen after checkpoint CP-B by the registry snapshot test; new
 * trunks only at the end.
 *
 * <p>Bark (D2): birch and spruce use the vanilla log texture by id, the others the textures of {@code tools/art} (oak,
 * pine, hornbeam, linden, beech, in two or three variants), the ash and the Norway maple a shared substitute bark of the
 * broad-crowned archetype (§1.1). Loot (§1.1): radius ≤ 2 gives sticks, radius ≥ 3 the log of the species or, until
 * the species has its own log, the closest vanilla log ({@link #log()}).
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
			"Pień klonu zwyczajnego"),
	/**
	 * The lower block of the Scots pine's transition zone between the lower bark and the "mirror" (step V1b, choice V1-i,
	 * reserved before CP-B): the gray-brown plates of the lower bark start to flake off, the orange bark shows in the
	 * fissures. The tree planner (V6) puts it right on {@link #SCOTS_PINE} and {@link #SCOTS_PINE_TRANSITION_UPPER} right
	 * on it; the textures of the two continue into each other and into their neighbours only in this order.
	 */
	SCOTS_PINE_TRANSITION("scots_pine_transition_trunk", Species.SCOTS_PINE, MapColor.COLOR_BROWN,
			"Scots Pine Transition Trunk", "Przejściowy pień sosny zwyczajnej"),
	/**
	 * The upper block of the transition zone (V1b review round 1, reserved before CP-B): mostly the orange bark of the
	 * "mirror" with the last gray-brown plates at the bottom; {@link #SCOTS_PINE_UPPER} goes right on it.
	 */
	SCOTS_PINE_TRANSITION_UPPER("scots_pine_transition_upper_trunk", Species.SCOTS_PINE, MapColor.COLOR_ORANGE,
			"Scots Pine Upper Transition Trunk", "Górny przejściowy pień sosny zwyczajnej"),
	/**
	 * Black alder (step F2): dark gray-brown bark, smooth with lenticels when young, cracked into small angular plates
	 * when old; also the thin stems of the bird cherry shrub (smooth dark bark with lenticels, default choice of F2).
	 */
	BLACK_ALDER("black_alder_trunk", Species.BLACK_ALDER, MapColor.TERRACOTTA_BLACK, "Black Alder Trunk", "Pień olszy czarnej"),
	/** Elm (step F2, one species ELM): gray-brown bark with long interlaced longitudinal ridges and furrows. */
	ELM("elm_trunk", Species.ELM, MapColor.TERRACOTTA_GRAY, "Elm Trunk", "Pień wiązu");

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
	 * The log dropped by a trunk of radius ≥ 3 (and 1-2 of them by a stem base, S1): the vanilla log of oak, birch and
	 * spruce (D2), the Scots pine log of the LITE set (step V7, D1 b) and, since step P2 (U-8), the own log of the wood set
	 * of hornbeam, linden, beech, ash and Norway maple ({@link WoodSet}).
	 */
	public Block log() {
		return switch (this) {
			case SCOTS_PINE, SCOTS_PINE_UPPER, SCOTS_PINE_TRANSITION, SCOTS_PINE_TRANSITION_UPPER -> ModBlocks.pineLog();
			case SPRUCE -> Blocks.SPRUCE_LOG;
			case BIRCH -> Blocks.BIRCH_LOG;
			case OAK -> Blocks.OAK_LOG;
			case HORNBEAM -> ModBlocks.wood(WoodSet.HORNBEAM).log();
			case LINDEN -> ModBlocks.wood(WoodSet.LINDEN).log();
			case BEECH -> ModBlocks.wood(WoodSet.BEECH).log();
			case ASH -> ModBlocks.wood(WoodSet.ASH).log();
			case NORWAY_MAPLE -> ModBlocks.wood(WoodSet.NORWAY_MAPLE).log();
			case BLACK_ALDER -> ModBlocks.wood(WoodSet.BLACK_ALDER).log();
			case ELM -> ModBlocks.wood(WoodSet.ELM).log();
		};
	}

	/**
	 * The full log the tree planner writes for the thickest parts of a trunk ({@code TreeVoxels.FULL}, from 8 px): the log
	 * of {@link #log()} before step P2 — the vanilla log, the pale oak log for the gray barks of hornbeam and beech, the oak
	 * log for linden, ash and maple. Kept by P2 (default choice P2-c) so the generated world stays block for block the same;
	 * the own logs of the wood sets come into the generation with the new forest structure (step F): the species of the steps
	 * F (black alder and elm, step F2) write their own log.
	 */
	public Block fullLog() {
		return switch (this) {
			case SCOTS_PINE, SCOTS_PINE_UPPER, SCOTS_PINE_TRANSITION, SCOTS_PINE_TRANSITION_UPPER -> ModBlocks.pineLog();
			case SPRUCE -> Blocks.SPRUCE_LOG;
			case BIRCH -> Blocks.BIRCH_LOG;
			case OAK, LINDEN, ASH, NORWAY_MAPLE -> Blocks.OAK_LOG;
			case HORNBEAM, BEECH -> Blocks.PALE_OAK_LOG;
			// Step F2: the species of the steps F write their own log (P2-c: the own logs come in with the new forest
			// structure; no generated world had these trees before).
			case BLACK_ALDER, ELM -> log();
		};
	}

}
