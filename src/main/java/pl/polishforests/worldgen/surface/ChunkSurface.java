package pl.polishforests.worldgen.surface;

import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Surface plan of one chunk (docs/03-m2-biomy.md §7.1): for each of the 256 columns (index {@code x * 16 + z}) the final
 * top ground block after the bank shelf and the micro-relief, the water surface, the soil profile and where the rock
 * begins. Built once per chunk by {@link SurfaceBuilder}; {@link #material} only reads the arrays, so filling the
 * sections does no sampling and no noise. Pure Java: the generator turns {@link Material} into block states.
 */
public final class ChunkSurface {
	/** {@link #waterTop} of a column without water. */
	public static final int NO_WATER = Integer.MIN_VALUE;

	/** Flags of a column ({@link #flags}). */
	public static final int SHELF = 1;
	/** The column is a dry shelf column next to water (row 1 of the shelf zones and lake shores): the shore column of the test. */
	public static final int SHORE = 2;
	public static final int PUDDLE = 4;
	/** A puddle place that cannot hold water (chunk edge, lower or wet neighbor): mud instead. */
	public static final int PUDDLE_MUD = 8;
	public static final int HUMMOCK = 16;
	/** Water column in the belt of 1–2 blocks by the shore (mud or gravel bed). */
	public static final int SHORE_BED = 32;

	private static final Substrate[] SUBSTRATES = Substrate.values();

	final int[] top = new int[256];
	final int[] waterTop = new int[256];
	final int[] modelTop = new int[256];
	final int[] rockTop = new int[256];
	final int[] bedrockTop = new int[256];
	final int[] deepRockY = new int[256];
	final int[] profile = new int[256];
	final int[] flags = new int[256];
	/** Substrate ordinal ({@link #bandOffset} applies only to flysch). */
	final byte[] substrate = new byte[256];
	final int[] bandOffset = new int[256];
	/** Gravel patch in the upper channel deposit (old {@code DETAIL} rule). */
	final boolean[] gravelPatch = new boolean[256];

	ChunkSurface() {
	}

	/** Y of the top ground block of the column (after the shelf and the micro-relief). */
	public int top(int i) {
		return top[i];
	}

	/** Y of the top water block of the column, or {@link #NO_WATER}. */
	public int waterTop(int i) {
		return waterTop[i];
	}

	/** Whether water lies on the ground of the column. */
	public boolean wet(int i) {
		return waterTop[i] > top[i];
	}

	/** Y of the top ground block from the landscape model, before the shelf and the micro-relief. */
	public int modelTop(int i) {
		return modelTop[i];
	}

	/** Flags of the column ({@link #SHELF}, {@link #SHORE}, {@link #PUDDLE}, ...). */
	public int flags(int i) {
		return flags[i];
	}

	/** Top ground block of the column (the soil or bed top, or the rock where the cover is missing). */
	public Material topMaterial(int i) {
		return material(i, top[i]);
	}

	/** Highest Y with a block other than air in the chunk. */
	public int highest() {
		int h = Integer.MIN_VALUE;
		for (int i = 0; i < 256; i++) {
			h = Math.max(h, Math.max(top[i], waterTop[i]));
		}
		return h;
	}

	/** Block of the column at height {@code y}: bedrock, rock, deposits of the substrate, soil layers, water or air. */
	public Material material(int i, int y) {
		int t = top[i];
		if (y > t) {
			return y <= waterTop[i] ? Material.WATER : Material.AIR;
		}
		if (y <= bedrockTop[i]) {
			return Material.BEDROCK;
		}
		if (y > rockTop[i]) {
			int depth = t - y;
			int p = profile[i];
			if (depth == 0) {
				return SoilBlocks.top(p);
			}
			int l1 = SoilBlocks.layer1Depth(p);
			if (depth <= l1) {
				return SoilBlocks.layer1(p);
			}
			if (depth <= l1 + SoilBlocks.layer2Depth(p)) {
				return SoilBlocks.layer2(p);
			}
			return deposit(SUBSTRATES[substrate[i]], depth, gravelPatch[i]);
		}
		if (y < deepRockY[i]) {
			return Material.DEEPSLATE;
		}
		if (substrate[i] == Substrate.FLYSCH.ordinal()) {
			// Flysch layering: beds of sandstone and shale, slightly tilted.
			return Math.floorMod(y + bandOffset[i], 9) < 3 ? Material.ANDESITE : Material.STONE;
		}
		return Material.STONE;
	}

	/** Deposits of the substrate under the soil (the M1 rules, by the depth below the top block). */
	static Material deposit(Substrate sub, int depth, boolean gravelPatch) {
		return switch (sub) {
			case SAND, BEACH_SAND -> Material.SAND;
			case RIVERBED -> depth < 3 && gravelPatch ? Material.GRAVEL : Material.SAND;
			case LAKE_MUD -> depth < 2 ? Material.MUD : Material.CLAY;
			case PEAT -> depth < 3 ? Material.MUD : Material.CLAY;
			case ALLUVIUM -> depth < 4 ? Material.DIRT : Material.SAND;
			case GLACIAL_TILL -> depth < 3 ? Material.DIRT : Material.CLAY;
			case FLYSCH -> depth < 2 ? Material.DIRT : Material.COARSE_DIRT;
		};
	}

	/** Number of blocks of loose cover (soil and deposits) above the rock in the column. */
	public int coverBlocks(int i) {
		return Math.max(0, top[i] - rockTop[i]);
	}
}
