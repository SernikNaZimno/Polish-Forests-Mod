package pl.polishforests.worldgen.surface;

import pl.polishforests.worldgen.feature.plan.Ecotone;

/**
 * Soil ecotones (rule Z10, step S8b, docs/03-m2-biomy.md §8.8): where the soil changes between two habitats, the top
 * blocks of both soils mix in a belt of a few to a dozen and more blocks ({@link Ecotone#halfWidth} of the layer
 * {@link Ecotone.Layer#SOIL}: 8 m·k between similar forests, 4 m·k at forest edges, 2 m·k on the land side of the
 * waterside zones, none at water), in patches of about {@value #PATCH} blocks with ragged borders, around the border moved
 * by the meander of the ecotones, so the border of podzol and grass, coarse dirt, mud or moss is neither a line nor a
 * checkerboard. A pure function of the habitat codes of the chunk and its neighbors and the world seed; the generator
 * applies it to the top block of the dry columns before the decoration of the chunk, and only where the top block is
 * still the one of the column's own soil profile (not a hummock, a shelf bed or a structure's ground).
 */
public final class SoilBlend {
	/** Salt of the soil ecotone vectors. */
	public static final long SALT = 0x5011_B1E0L;
	/** Patch size of the soil ecotone vectors (blocks). */
	public static final int PATCH = 3;
	/** Packed value of a column whose top stays. */
	public static final int KEEP = -1;

	private SoilBlend() {
	}

	/**
	 * Top blocks of the chunk's columns: for each column either {@link #KEEP} or the materials packed as
	 * {@code own << 8 | taken} (ordinals of {@link Material}): the top of the column's own soil, which the world must
	 * still hold, and the top of the soil taken across the border.
	 *
	 * @param skip columns to leave alone (water, structure pieces), or null
	 */
	public static int[] tops(Ecotone.Region region, long worldSeed, SurfaceBuilder builder, boolean[] skip) {
		int[] out = new int[256];
		java.util.Arrays.fill(out, KEEP);
		int[] own = region.own();
		int[] codes = Ecotone.effective(region, Ecotone.Layer.SOIL, worldSeed, SALT, PATCH);
		int x0 = region.chunkX() << 4;
		int z0 = region.chunkZ() << 4;
		for (int i = 0; i < 256; i++) {
			if (codes[i] == own[i] || skip != null && skip[i]) {
				continue;
			}
			int tops = builder.soilTops(own[i], codes[i], x0 + (i >> 4), z0 + (i & 15));
			if (tops >= 0) {
				out[i] = tops;
			}
		}
		return out;
	}
}
