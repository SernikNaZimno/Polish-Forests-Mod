package pl.polishforests.worldgen.surface;

import java.util.Locale;

/**
 * Blocks that the "Poland" generator writes in {@code fill()} (docs/03-m2-biomy.md §7). Only vanilla blocks until M4
 * (decision M2-D): peat, muck and sphagnum use the substitutes of §7.4 (mud, mud and moss block). The surface plan
 * ({@link ChunkSurface}) is pure Java and works with this enum; the generator turns it into block states lazily
 * ({@code MaterialStates}), so tests can check the plan without the game. The order is not saved anywhere.
 */
public enum Material {
	AIR,
	WATER,
	BEDROCK,
	STONE,
	DEEPSLATE,
	ANDESITE,
	GRASS_BLOCK,
	DIRT,
	COARSE_DIRT,
	PODZOL,
	ROOTED_DIRT,
	MUD,
	MOSS_BLOCK,
	SAND,
	GRAVEL,
	CLAY,
	COBBLESTONE,
	// Diagnostic mode (-Dpolishforests.debug.habitats=true): concrete for zones, terracotta for biomes.
	WHITE_CONCRETE,
	ORANGE_CONCRETE,
	MAGENTA_CONCRETE,
	LIGHT_BLUE_CONCRETE,
	YELLOW_CONCRETE,
	LIME_CONCRETE,
	PINK_CONCRETE,
	GRAY_CONCRETE,
	LIGHT_GRAY_CONCRETE,
	CYAN_CONCRETE,
	PURPLE_CONCRETE,
	BLUE_CONCRETE,
	BROWN_CONCRETE,
	GREEN_CONCRETE,
	RED_CONCRETE,
	BLACK_CONCRETE,
	WHITE_TERRACOTTA,
	ORANGE_TERRACOTTA,
	MAGENTA_TERRACOTTA,
	LIGHT_BLUE_TERRACOTTA,
	YELLOW_TERRACOTTA,
	LIME_TERRACOTTA,
	PINK_TERRACOTTA,
	GRAY_TERRACOTTA,
	LIGHT_GRAY_TERRACOTTA,
	CYAN_TERRACOTTA,
	PURPLE_TERRACOTTA,
	BLUE_TERRACOTTA,
	BROWN_TERRACOTTA,
	GREEN_TERRACOTTA,
	RED_TERRACOTTA,
	BLACK_TERRACOTTA;

	private static final Material[] VALUES = values();
	/** Number of diagnostic colors of each kind. */
	static final int COLORS = 16;

	/** Block id, e.g. {@code minecraft:grass_block}. */
	public String id() {
		return "minecraft:" + name().toLowerCase(Locale.ROOT);
	}

	public static Material of(int ordinal) {
		return VALUES[ordinal];
	}

	/** Concrete of the color {@code index} (0–15, in the vanilla dye order). */
	static Material concrete(int index) {
		return VALUES[WHITE_CONCRETE.ordinal() + Math.floorMod(index, COLORS)];
	}

	/** Terracotta of the color {@code index} (0–15, in the vanilla dye order). */
	static Material terracotta(int index) {
		return VALUES[WHITE_TERRACOTTA.ordinal() + Math.floorMod(index, COLORS)];
	}

	/** Whether the block is one of the diagnostic colors. */
	public boolean isDiagnostic() {
		return ordinal() >= WHITE_CONCRETE.ordinal();
	}
}
