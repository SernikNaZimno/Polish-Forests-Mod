package pl.polishforests.worldgen.surface;

import java.util.List;

/**
 * Ground under and around structure pieces (step S8, round 1 of the review; docs/03-m2-biomy.md §10.1). The "Poland"
 * generator fills columns from its surface plan, so the vanilla {@code Beardifier}, which adds a density term in the noise
 * generator, never runs: village houses on a slope hung up to 4 blocks above the ground. This class does the same job on
 * the column tops of the plan:
 *
 * <ul>
 *   <li><b>Beard.</b> Under the footprint of a piece that the vanilla beard adapts ({@code beard_thin} and
 *       {@code beard_box}: rigid pieces of villages and pillager outposts, and pieces that are not pool elements) the top
 *       ground block is {@code groundY − 1}, so the bottom layer of the template stands on the ground and the terrain
 *       above the floor is cut. Around the footprint, up to {@link #BEARD_RADIUS} blocks (the vanilla kernel radius), the
 *       ground is filled or cut to at most one block of height per block of horizontal distance (Euclidean, from the
 *       footprint), so the piece stands on a mound or in a cutting with 45° sides. Several pieces: the tightest bounds of
 *       all; when they contradict each other, the nearest piece decides. A piece whose ground lies more than
 *       {@link #MAX_ADJUST} blocks from the column top is skipped (deep structures, e.g. the ancient city).</li>
 *   <li><b>Vegetation mask.</b> For each column the Chebyshev distance (blocks) to the nearest footprint of a building
 *       piece (rigid pool pieces and pieces that are not pool elements) and of any piece (also the terrain-matching
 *       streets), counted only for pieces whose box reaches the surface ({@link #reachesSurface}), capped at
 *       {@link #FAR}. The tree stand and the plant layers skip columns near pieces ({@code TreeStandFeature},
 *       {@code PlantLayerFeature}): in a forest village trunks stood on the streets and crowns grew into the houses.</li>
 * </ul>
 *
 * Pure Java; the generator collects the pieces from the structure starts of the chunk.
 */
public final class StructureGround {
	/** Horizontal reach of the beard around a footprint (blocks), the vanilla kernel radius. */
	public static final int BEARD_RADIUS = 12;
	/** Largest change of a column top by the beard (blocks); farther pieces are skipped. */
	public static final int MAX_ADJUST = 24;
	/** Distance of a column without a piece nearby in the mask (and the cap of the distances). */
	public static final int FAR = 15;
	/** A piece box counts for the mask when it reaches from this many blocks below the top ... */
	static final int MASK_BELOW = 3;
	/** ... up to this many blocks above it (a tree standing below a house on a slope). */
	static final int MASK_ABOVE = 32;

	/**
	 * A structure piece: its bounding box, the Y of its ground (the first block above the ground: for a pool element the
	 * box minimum plus its ground level delta, otherwise the box minimum), whether the beard adapts the ground under it and
	 * whether it is a building (rigid) piece for the mask.
	 */
	public record Piece(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int groundY, boolean beard, boolean building) {
	}

	private StructureGround() {
	}

	/**
	 * Applies the beard of the pieces to the plan of the chunk with the minimum block corner (minX, minZ) and returns the
	 * vegetation mask (index {@code x * 16 + z}: building distance in the high nibble, any-piece distance in the low
	 * nibble, see {@link #building} and {@link #any}), or null without pieces.
	 */
	public static byte[] apply(ChunkSurface surface, List<Piece> pieces, int minX, int minZ) {
		if (pieces.isEmpty()) {
			return null;
		}
		byte[] mask = new byte[256];
		for (int i = 0; i < 256; i++) {
			int x = minX + (i >> 4);
			int z = minZ + (i & 15);
			int top = surface.top(i);
			int building = FAR;
			int any = FAR;
			int lo = Integer.MIN_VALUE;
			int hi = Integer.MAX_VALUE;
			double nearest = Double.MAX_VALUE;
			int nearestLo = 0;
			int nearestHi = 0;
			for (Piece p : pieces) {
				int dx = Math.max(0, Math.max(p.minX - x, x - p.maxX));
				int dz = Math.max(0, Math.max(p.minZ - z, z - p.maxZ));
				if (reachesSurface(p, top)) {
					int d = Math.max(dx, dz);
					any = Math.min(any, d);
					if (p.building) {
						building = Math.min(building, d);
					}
				}
				if (!p.beard) {
					continue;
				}
				int ground = p.groundY - 1;
				if (Math.abs(ground - top) > MAX_ADJUST) {
					continue;
				}
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > BEARD_RADIUS) {
					continue;
				}
				int slack = (int) Math.floor(d);
				int pLo = ground - slack;
				int pHi = ground + slack;
				lo = Math.max(lo, pLo);
				hi = Math.min(hi, pHi);
				if (d < nearest) {
					nearest = d;
					nearestLo = pLo;
					nearestHi = pHi;
				}
			}
			mask[i] = (byte) (building << 4 | any);
			if (nearest == Double.MAX_VALUE) {
				continue;
			}
			if (lo > hi) {
				lo = nearestLo;
				hi = nearestHi;
			}
			int newTop = Math.clamp(top, lo, hi);
			if (newTop != top) {
				surface.adjustTop(i, newTop);
			}
		}
		return mask;
	}

	/** Whether the box of the piece reaches the surface of a column with the given top (for the mask). */
	static boolean reachesSurface(Piece p, int top) {
		return p.maxY >= top - MASK_BELOW && p.minY <= top + MASK_ABOVE;
	}

	/** Distance of a mask entry to the nearest building piece (blocks, at most {@link #FAR}). */
	public static int building(byte entry) {
		return (entry >> 4) & 15;
	}

	/** Distance of a mask entry to the nearest piece of any kind (blocks, at most {@link #FAR}). */
	public static int any(byte entry) {
		return entry & 15;
	}
}
