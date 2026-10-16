package pl.polishforests.worldgen.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.feature.plan.TreeStandPlan;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Crown territories (step V5 of docs/04-pionowy-wycinek.md, decision PW-9 variant a): every kept candidate of the M2
 * trunk pattern ({@link TreeStandPlan.Candidates}) owns the cell of a power diagram (a Voronoi diagram weighted by the
 * squared crown radius), clipped to a disc of its reach. The crowns of the tree planner (step V6) stay inside their
 * territories, so the crowns of two trunks never claim the same column and the result does not depend on the order in
 * which the chunks are decorated.
 *
 * <p><b>Inputs.</b> Only the candidate pattern, the stand map ({@link StandMap}: age class, natural flag, canopy gaps) and
 * a hash of the candidate's column with the salt {@value #SALT} (rule ZW-2), never the species or the habitat code: at
 * the FEATURES stage the habitats exist only for the chunks within radius 1, while a crown of up to {@value #MAX_REACH}
 * blocks has neighbors up to {@value #SEARCH} blocks away, in chunks at radius 2. The layer of a candidate
 * ({@link CrownLayer}) comes from the hash with the shares of its stand (managed or natural, age class), the crown radius
 * from the layer and the age class with a jitter of {@value #JITTER_MIN}–{@value #JITTER_MAX}. A candidate in a canopy
 * gap of a natural stand belongs to the regeneration. A candidate that does not become a tree (palette chance, structure,
 * water) keeps its territory empty: a gap in the canopy, as in nature (crown shyness).
 *
 * <p><b>Layers.</b> Each layer has its own diagram: a canopy tree competes with canopy trees only, since the layers
 * occupy different heights (the tree planner keeps the lower layers below the crown base of the canopy).
 *
 * <p><b>Edges.</b> A candidate in a canopy gap (in the canopy and lower layers) and a candidate on open ground
 * ({@link OpenGround}) are markers: in a pair of a marker and a tree the marker weighs 0 and the tree weighs its crown
 * radius times {@value #EDGE_REACH}, and a tree with such a partner within that distance may reach that far, so its
 * crown leans out over the gap or the open ground (the asymmetric crowns of the forest edge, §1.1). Open ground needs
 * the habitat codes, so it is used in a pair only when the chunks of both trunks are within radius 1 of each other (the
 * relation is symmetric, so both trunks of a pair see the same weights): a trunk's territory then asks about the columns
 * of its own chunk and its 8 neighbors only, which the decoration of that chunk has. Without {@link OpenGround} the
 * territories are a pure function of the seed and the stand map and can be computed from any chunk.
 *
 * <p><b>Exactness.</b> Positions are columns and weights are integers in sixteenths of a block squared, so the ownership
 * of a column is an exact comparison; equal powers go to the trunk with the smaller (x, z). Both trunks of a pair compute
 * the same weights, so their territories are disjoint. In a pair the weights are clamped so that each trunk owns its own
 * column (a small tree next to a large one still has a crown over its trunk).
 */
public final class CrownTerritories {
	/** Salt of the crown hash (rule ZW-2). */
	public static final String SALT = "feature.tree.crown";
	/** Largest distance of a territory column from its trunk (blocks); the write window of 3 × 3 chunks allows 16. */
	public static final int MAX_REACH = 16;
	/** Distance up to which two trunks can share a column: twice {@link #MAX_REACH}. */
	public static final int SEARCH = 2 * MAX_REACH;
	/** Reach of an edge tree over open ground or a canopy gap, as a multiple of its crown radius. */
	public static final double EDGE_REACH = 1.5;
	/** Least and largest factor of the crown radius jitter. */
	public static final double JITTER_MIN = 0.85;
	public static final double JITTER_MAX = 1.2;

	/**
	 * Crown radius (blocks) by layer and age class [S]: upper bounds of the crown spread of the broad-leaved trees of the
	 * slice (oak, linden), since the species is unknown; narrower crowns (pine, birch) do not fill their territory. To be
	 * calibrated against the cover N4 in step V9.
	 */
	private static final double[][] RADIUS = {
			{3.5, 5.5, 7.0}, // canopy: pole stage, mature, old growth
			{2.5, 3.5, 4.5}, // subcanopy
			{1.0, 1.5, 2.0}, // regeneration
	};
	/**
	 * Shares of the canopy and the subcanopy among the candidates (the rest is regeneration) by stand and age class [S]:
	 * managed stands are single-storied, natural ones two-storied (N2: 3–5 canopy and 6–9 subcanopy trees per chunk in a
	 * natural oak-hornbeam forest of about 15). To be calibrated against N1 and N2 in step V9.
	 */
	private static final double[][][] SHARES = {
			{{0.95, 0.05}, {0.85, 0.10}, {0.80, 0.10}}, // managed: pole stage, mature, old growth
			{{0.70, 0.25}, {0.35, 0.50}, {0.30, 0.50}}, // natural
	};
	private static final int R = MAX_REACH;
	private static final long LAYER_SALT = 1;
	private static final long RADIUS_SALT = 2;

	private final long worldSeed;
	private final Noise crown;
	private final StandMap stands;

	public CrownTerritories(long worldSeed, StandMap stands) {
		this.worldSeed = worldSeed;
		this.crown = new Noise(worldSeed).derive(SALT);
		this.stands = stands;
	}

	/**
	 * A kept candidate as a site of the diagrams: its column, layer, age class, crown radius and whether it stands in a
	 * canopy gap. {@code weight} and {@code edgeWeight} are the squared radius and the squared edge reach in sixteenths.
	 */
	public record Site(int x, int z, CrownLayer layer, AgeClass age, float radius, boolean gap, int weight, int edgeWeight) {
		/** Whether this site precedes the other one when their powers are equal. */
		boolean before(Site o) {
			return x != o.x ? x < o.x : z < o.z;
		}
	}

	/** Site of the candidate at column (x, z) (whether or not a candidate stands there): a pure function. */
	public Site site(int x, int z) {
		AgeClass age = stands.ageClass(x, z);
		boolean natural = stands.natural(x, z);
		boolean gap = natural && stands.canopyGap(x, z);
		CrownLayer layer;
		if (gap) {
			layer = CrownLayer.REGENERATION;
		} else {
			double[] s = SHARES[natural ? 1 : 0][age.ordinal()];
			double u = crown.unit(x, z, LAYER_SALT);
			layer = u < s[0] ? CrownLayer.CANOPY : u < s[0] + s[1] ? CrownLayer.SUBCANOPY : CrownLayer.REGENERATION;
		}
		double jitter = JITTER_MIN + (JITTER_MAX - JITTER_MIN) * crown.unit(x, z, RADIUS_SALT);
		double radius = Math.min(MAX_REACH / EDGE_REACH, RADIUS[layer.ordinal()][age.ordinal()] * jitter);
		int weight = (int) Math.round(16 * radius * radius);
		int edgeWeight = (int) Math.round(16 * EDGE_REACH * EDGE_REACH * radius * radius);
		return new Site(x, z, layer, age, (float) radius, gap, weight, edgeWeight);
	}

	/**
	 * A working state for one thread: the memo of the candidate pattern and the sites around the chunks asked about. The
	 * results never depend on what the session was asked before (everything cached is a pure function).
	 */
	public Session session() {
		return new Session();
	}

	public final class Session {
		private static final int MAX_TILES = 256;
		/** Chunks around the trunk's chunk that hold its possible pair partners: {@value #SEARCH} blocks. */
		private static final int TILE_REACH = SEARCH / 16;

		private final TreeStandPlan.Candidates candidates = new TreeStandPlan.Candidates(worldSeed);
		private final Map<Long, Site[]> tiles = new HashMap<>();
		// Pairs of the territory being computed (reused arrays).
		private int[] px = new int[64];
		private int[] pz = new int[64];
		private int[] pa = new int[64];
		private int[] pb = new int[64];
		private boolean[] pFirst = new boolean[64];
		private int n;
		private boolean edge;

		private Session() {
		}

		/** Whether the column (x, z) holds a kept candidate of the M2 pattern. */
		public boolean candidate(int x, int z) {
			return candidates.kept(x, z);
		}

		/** Sites of the kept candidates of the chunk (chunkX, chunkZ), memoized. */
		Site[] tile(int chunkX, int chunkZ) {
			long key = (long) chunkX << 32 | (chunkZ & 0xFFFF_FFFFL);
			Site[] known = tiles.get(key);
			if (known != null) {
				return known;
			}
			if (tiles.size() >= MAX_TILES) {
				tiles.clear();
			}
			List<Site> out = new ArrayList<>();
			candidates.forEach(chunkX * 16, chunkZ * 16, chunkX * 16 + 15, chunkZ * 16 + 15, (x, z) -> out.add(site(x, z)));
			Site[] sites = out.toArray(new Site[0]);
			tiles.put(key, sites);
			return sites;
		}

		/** Territories of every kept candidate of the chunk, in the order of the candidate cells. */
		public List<CrownTerritory> chunk(int chunkX, int chunkZ, @Nullable OpenGround open) {
			List<CrownTerritory> out = new ArrayList<>();
			for (Site s : tile(chunkX, chunkZ)) {
				out.add(territory(s, open));
			}
			return out;
		}

		/**
		 * Territory of the trunk at column (x, z), which must be a kept candidate. With {@code open} the territory asks it
		 * about the columns of the chunks within radius 1 of the trunk's chunk only.
		 */
		public CrownTerritory territory(int x, int z, @Nullable OpenGround open) {
			if (!candidates.kept(x, z)) {
				throw new IllegalArgumentException("no trunk candidate at " + x + ", " + z);
			}
			return territory(site(x, z), open);
		}

		private CrownTerritory territory(Site t, @Nullable OpenGround open) {
			int cx = t.x() >> 4;
			int cz = t.z() >> 4;
			boolean tOpen = open != null && open.open(t.x(), t.z());
			n = 0;
			edge = false;
			for (int ti = -TILE_REACH; ti <= TILE_REACH; ti++) {
				for (int tj = -TILE_REACH; tj <= TILE_REACH; tj++) {
					// Open ground only between trunks whose chunks are within radius 1 of each other (a symmetric relation).
					boolean adjacent = open != null && Math.abs(ti) <= 1 && Math.abs(tj) <= 1;
					pairs(t, tile(cx + ti, cz + tj), adjacent ? open : null, adjacent && tOpen);
				}
			}
			double reach = Math.min(MAX_REACH, edge ? EDGE_REACH * t.radius() : t.radius());
			// Drop the pairs whose boundary lies beyond the reach: on the line to u the powers are equal at s from t.
			int m = 0;
			for (int i = 0; i < n; i++) {
				double d = Math.sqrt(px[i] * px[i] + pz[i] * pz[i]);
				double s = (16 * d * d + pa[i] - pb[i]) / (32 * d);
				if (s <= reach + 0.01) {
					px[m] = px[i];
					pz[m] = pz[i];
					pa[m] = pa[i];
					pb[m] = pb[i];
					pFirst[m] = pFirst[i];
					m++;
				}
			}
			long reach16 = (long) Math.floor(16 * reach * reach);
			long[] rows = new long[2 * R + 1];
			int span = (int) Math.floor(reach);
			for (int dz = -span; dz <= span; dz++) {
				long row = 0;
				for (int dx = -span; dx <= span; dx++) {
					long own = 16L * (dx * dx + dz * dz);
					boolean in = own <= reach16 && wins(dx, dz, own, m);
					if (in) {
						row |= 1L << (dx + R);
					} else if (row != 0) {
						// The territory is convex, so its columns in a row are contiguous.
						break;
					}
				}
				rows[dz + R] = row;
			}
			return new CrownTerritory(t.x(), t.z(), t.layer(), t.radius(), (float) reach, edge, rows);
		}

		/**
		 * Adds the pairs of trunk t with the sites of one chunk. {@code open} is null when the chunks of the pair are not
		 * within radius 1 of each other; {@code tMarker} tells whether t is on open ground for these pairs.
		 */
		private void pairs(Site t, Site[] sites, @Nullable OpenGround open, boolean tMarker) {
			CrownLayer layer = t.layer();
			double edgeLimit = EDGE_REACH * t.radius();
			for (Site u : sites) {
				int dx = u.x() - t.x();
				int dz = u.z() - t.z();
				int d2 = dx * dx + dz * dz;
				if (d2 == 0 || d2 > SEARCH * SEARCH) {
					continue;
				}
				boolean uMarker = (u.gap() && layer != CrownLayer.REGENERATION) || (open != null && open.open(u.x(), u.z()));
				// A site of another layer takes part only as a marker: it has no crown in this layer.
				boolean foreign = u.layer() != layer;
				if (foreign && !uMarker) {
					continue;
				}
				int wt = t.weight();
				int wu = u.weight();
				if (tMarker && !uMarker) {
					wt = 0;
					wu = u.edgeWeight();
				} else if (foreign || uMarker && !tMarker) {
					wt = t.edgeWeight();
					wu = 0;
					if (d2 <= edgeLimit * edgeLimit) {
						edge = true;
					}
				}
				// Each trunk of the layer owns its own column: the power of the other trunk there is never lower. The crown
				// may cover a marker of another layer.
				int a = wt;
				int b = wu;
				if (!foreign) {
					a = Math.min(wt, wu + 16 * d2 - 1);
					b = Math.min(wu, wt + 16 * d2 - 1);
				}
				if (n == px.length) {
					grow();
				}
				px[n] = dx;
				pz[n] = dz;
				pa[n] = a;
				pb[n] = b;
				pFirst[n] = t.before(u);
				n++;
			}
		}

		/** Whether the trunk wins the column (dx, dz) against every pair partner. */
		private boolean wins(int dx, int dz, long own, int m) {
			for (int i = 0; i < m; i++) {
				long ex = dx - px[i];
				long ez = dz - pz[i];
				long mine = own - pa[i];
				long theirs = 16 * (ex * ex + ez * ez) - pb[i];
				if (mine > theirs || (mine == theirs && !pFirst[i])) {
					return false;
				}
			}
			return true;
		}

		private void grow() {
			int size = px.length * 2;
			px = java.util.Arrays.copyOf(px, size);
			pz = java.util.Arrays.copyOf(pz, size);
			pa = java.util.Arrays.copyOf(pa, size);
			pb = java.util.Arrays.copyOf(pb, size);
			pFirst = java.util.Arrays.copyOf(pFirst, size);
		}
	}
}
