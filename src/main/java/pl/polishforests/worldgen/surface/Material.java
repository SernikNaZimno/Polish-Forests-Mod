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
	// Diagnostic mode (-Dpolishforests.debug.habitats=true): concrete and wool for zones, terracotta, concrete powder and
	// wool for biomes (zoneColor, biomeColor).
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
	BLACK_TERRACOTTA,
	WHITE_WOOL,
	ORANGE_WOOL,
	MAGENTA_WOOL,
	LIGHT_BLUE_WOOL,
	YELLOW_WOOL,
	LIME_WOOL,
	PINK_WOOL,
	GRAY_WOOL,
	LIGHT_GRAY_WOOL,
	CYAN_WOOL,
	PURPLE_WOOL,
	BLUE_WOOL,
	BROWN_WOOL,
	GREEN_WOOL,
	RED_WOOL,
	BLACK_WOOL,
	WHITE_CONCRETE_POWDER,
	ORANGE_CONCRETE_POWDER,
	MAGENTA_CONCRETE_POWDER,
	LIGHT_BLUE_CONCRETE_POWDER,
	YELLOW_CONCRETE_POWDER,
	LIME_CONCRETE_POWDER,
	PINK_CONCRETE_POWDER,
	GRAY_CONCRETE_POWDER,
	LIGHT_GRAY_CONCRETE_POWDER,
	CYAN_CONCRETE_POWDER,
	PURPLE_CONCRETE_POWDER,
	BLUE_CONCRETE_POWDER,
	BROWN_CONCRETE_POWDER,
	GREEN_CONCRETE_POWDER,
	RED_CONCRETE_POWDER,
	BLACK_CONCRETE_POWDER;

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

	/** Wool of the color {@code index} (0–15, in the vanilla dye order). */
	static Material wool(int index) {
		return VALUES[WHITE_WOOL.ordinal() + Math.floorMod(index, COLORS)];
	}

	/** Concrete powder of the color {@code index} (0–15, in the vanilla dye order). */
	static Material concretePowder(int index) {
		return VALUES[WHITE_CONCRETE_POWDER.ordinal() + Math.floorMod(index, COLORS)];
	}

	/**
	 * Diagnostic block of the zone with index {@code index} (zone ordinal − 1): concrete for the first 16 zones, wool
	 * of colors 0–15 for the next ones. Distinct for up to 32 zones.
	 */
	static Material zoneColor(int index) {
		return index < COLORS ? concrete(index) : wool(index - COLORS);
	}

	/**
	 * Diagnostic block of the biome with ordinal {@code index}: terracotta for the first 16 biomes, concrete powder for
	 * the next 16, then wool from color 15 downwards (the colors from 0 up belong to the zones). Distinct from each other
	 * and from the zones for up to 32 + 16 − (zones − 16) biomes (36 biomes and 22 zones: wool 0–5 zones, 12–15 biomes).
	 */
	static Material biomeColor(int index) {
		if (index < COLORS) {
			return terracotta(index);
		}
		if (index < 2 * COLORS) {
			return concretePowder(index - COLORS);
		}
		return wool(COLORS - 1 - (index - 2 * COLORS));
	}

	/** Whether the block is one of the diagnostic colors. */
	public boolean isDiagnostic() {
		return ordinal() >= WHITE_CONCRETE.ordinal();
	}
}
