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
 * trunk pattern ({@link TreeStandPlan.Candidates}) that grows a tree owns the cell of a power diagram (a Voronoi diagram
 * weighted by the squared crown radius), clipped to a disc of its reach. The crowns of the procedural trees of the tree
 * planner (step V6) stay inside their territories, so two procedural crowns of one layer never claim the same column and
 * the result does not depend on the order in which the chunks are decorated.
 *
 * <p><b>Trees that ignore territories.</b> The guarantee holds between procedural trees only. A candidate with a vanilla
 * substitute tree ({@code tree_substitute/} rules, PW-3: biomes outside the slice, ecotone columns) is not vacant
 * ({@link Vacancies}), so it keeps its cell and its procedural neighbors get no edge reach toward it, but its own crown
 * does not follow the cell and may overlap the procedural crowns next to it; where it does, the leaves written last win
 * (risk R5). The determinism test of step V6 covers such an ecotone.
 *
 * <p><b>Inputs.</b> Only the candidate pattern, the stand map ({@link StandMap}: age class, natural flag, canopy gaps), a
 * hash of the candidate's column with the salt {@value #SALT} (rule ZW-2) and, optionally, the vacant candidates
 * ({@link Vacancies}: exact ones of the trunk's own chunk, sure ones of the chunks within radius 1); never the species:
 * at the FEATURES stage the habitats exist only for the chunks within radius 1, while a crown of up to
 * {@value #MAX_REACH} blocks has neighbors up to {@value #SEARCH} blocks away, in chunks at radius 2. The layer of a
 * candidate ({@link CrownLayer}) comes from the hash with the shares of its stand (managed or natural, age class), the
 * crown radius from the layer and the age class with a jitter of {@value #JITTER_MIN}–{@value #JITTER_MAX}. A candidate in a canopy gap of a natural stand belongs to the regeneration.
 *
 * <p><b>Layers.</b> Each layer has its own diagram: a canopy tree competes with canopy trees only, since the layers
 * occupy different heights (the tree planner keeps the lower layers below the crown base of the canopy).
 *
 * <p><b>Markers: vacancies and edges.</b> A vacant candidate (no tree: palette, open ground; {@link Vacancies}) and, in the
 * canopy and the subcanopy, a candidate in a canopy gap are markers: in a pair of a marker and a tree the marker weighs 0
 * and the tree weighs its crown radius times {@value #EDGE_REACH}, and a tree with such a partner within that distance
 * may reach that far. So the neighbors of a candidate the palette leaves out share its cell (a sparse stand still closes
 * its canopy) and the crowns of the forest edge lean out over the open ground or the gap (§1.1). A marker has no
 * territory, so only the trunk next to it uses the pair; the disjointness of two trees' territories needs only that no
 * candidate called vacant grows a tree ({@link Vacancies}: the exact vacancy depends on the ecotone tree code, known only
 * for the candidates of the chunk being decorated). So a pair takes the exact vacancy of a candidate in the trunk's own
 * chunk, the sure vacancy (no habitat code needed) of a candidate in the chunks within radius 1, and no vacancy farther
 * away; a candidate whose vacancy is not known counts as a tree, which at worst leaves a hole. A trunk's territory is
 * computed by the decoration of its own chunk. Without {@link Vacancies} every candidate counts as a tree and the
 * territories are a pure function of the seed and the stand map that can be computed from any chunk.
 *
 * <p><b>Exactness.</b> Positions are columns and weights are integers in sixteenths of a block squared, so the ownership
 * of a column is an exact comparison; equal powers go to the trunk with the smaller (x, z). Both trunks of a pair compute
 * the same weights, so their territories are disjoint. In a pair of two trees of a layer the weights are clamped so that
 * each trunk owns its own column (a small tree next to a large one still has a crown over its trunk); a marker's column
 * is not reserved, so a crown may spread over it.
 */
public final class CrownTerritories {
	/** Salt of the crown hash (rule ZW-2). */
	public static final String SALT = "feature.tree.crown";
	/** Largest distance of a territory column from its trunk (blocks); the write window of 3 × 3 chunks allows 16. */
	public static final int MAX_REACH = 16;
	/** Distance up to which two trunks can share a column: twice {@link #MAX_REACH}. */
	public static final int SEARCH = 2 * MAX_REACH;
	/** Reach of a tree next to a marker (a vacant candidate or a canopy gap), as a multiple of its crown radius. */
	public static final double EDGE_REACH = 1.5;
	/** Least and largest factor of the crown radius jitter. */
	public static final double JITTER_MIN = 0.85;
	public static final double JITTER_MAX = 1.2;

	/**
	 * Crown radius (blocks) by layer and age class [S]: upper bounds of the crown spread of the broad-leaved trees of the
	 * slice (oak, linden), since the species is unknown; narrower crowns (pine, birch) do not fill their territory. In a
	 * closed stand the density sets the crown size (step V9 kept these values: the cover N4 is within its norms).
	 */
	private static final double[][] RADIUS = {
			{3.5, 5.5, 7.0}, // canopy: pole stage, mature, old growth
			{2.5, 3.5, 4.5}, // subcanopy
			{1.0, 1.5, 2.0}, // regeneration
	};
	/**
	 * Shares of the canopy and the subcanopy among the candidates (the rest is regeneration) by stand and age class [S]:
	 * managed stands are single-storied, natural ones two-storied (N2: 3–5 canopy and 6–9 subcanopy trees per chunk in a
	 * natural oak-hornbeam forest of about 15). Calibrated in step V9 against N1, N2 and N6 (natural mature and old growth
	 * with more of the subcanopy than in V5).
	 */
	private static final double[][][] SHARES = {
			{{0.95, 0.05}, {0.85, 0.10}, {0.80, 0.10}}, // managed: pole stage, mature, old growth
			{{0.70, 0.25}, {0.35, 0.58}, {0.30, 0.64}}, // natural
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
	public record Site(int x, int z, CrownLayer layer, AgeClass age, boolean natural, float radius, boolean gap, int weight,
			int edgeWeight) {
		/** Whether this site precedes the other one when their powers are equal. */
		boolean before(Site o) {
			return x != o.x ? x < o.x : z < o.z;
		}
	}

	/** The stand map of the territories. */
	public StandMap stands() {
		return stands;
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
		return new Site(x, z, layer, age, natural, (float) radius, gap, weight, edgeWeight);
	}

	/**
	 * A working state for one thread: the memo of the candidate pattern and the sites around the chunks asked about. The
	 * results never depend on what the session was asked before (everything cached is a pure function).
	 */
	public Session session() {
		return new Session(0, 1);
	}

	/**
	 * A session that takes the exact vacancies of the candidates within {@code exactChunks} of the trunk's chunk and the
	 * sure ones within {@code sureChunks} (0 and 1 in the game). Only for tests that compare the rule of the game with
	 * the exact vacancies of radius 1 (not available at the FEATURES stage) or with none outside the trunk's chunk.
	 */
	Session session(int exactChunks, int sureChunks) {
		return new Session(exactChunks, sureChunks);
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
		private final int exactChunks;
		private final int sureChunks;
		private int n;
		private boolean edge;

		private Session(int exactChunks, int sureChunks) {
			this.exactChunks = exactChunks;
			this.sureChunks = sureChunks;
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

		/**
		 * Territories of the kept candidates of the chunk that grow a tree (all of them without {@code vacancies}), in the
		 * order of the candidate cells.
		 */
		public List<CrownTerritory> chunk(int chunkX, int chunkZ, @Nullable Vacancies vacancies) {
			List<CrownTerritory> out = new ArrayList<>();
			for (Site s : tile(chunkX, chunkZ)) {
				if (vacancies == null || !vacancies.vacant(s.x(), s.z())) {
					out.add(territory(s, vacancies));
				}
			}
			return out;
		}

		/**
		 * Territory of the trunk at column (x, z), which must be a kept candidate that is not vacant. With
		 * {@code vacancies} the territory asks for the exact vacancy of the candidates of the trunk's chunk and for the sure
		 * vacancy of the candidates of the chunks within radius 1 only, so the decoration of the trunk's chunk computes it.
		 */
		public CrownTerritory territory(int x, int z, @Nullable Vacancies vacancies) {
			if (!candidates.kept(x, z)) {
				throw new IllegalArgumentException("no trunk candidate at " + x + ", " + z);
			}
			if (vacancies != null && vacancies.vacant(x, z)) {
				throw new IllegalArgumentException("vacant trunk candidate at " + x + ", " + z);
			}
			return territory(site(x, z), vacancies);
		}

		private CrownTerritory territory(Site t, @Nullable Vacancies vacancies) {
			int cx = t.x() >> 4;
			int cz = t.z() >> 4;
			n = 0;
			edge = false;
			for (int ti = -TILE_REACH; ti <= TILE_REACH; ti++) {
				for (int tj = -TILE_REACH; tj <= TILE_REACH; tj++) {
					// Exact vacancies only in the trunk's chunk, sure ones within radius 1 (round 2 of the V5 review).
					int ring = Math.max(Math.abs(ti), Math.abs(tj));
					pairs(t, tile(cx + ti, cz + tj), vacancies, ring <= exactChunks, ring <= sureChunks);
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
		 * Adds the pairs of tree t (not vacant) with the sites of one chunk, asking {@code vacancies} for the exact or the
		 * sure vacancy of the sites, or neither.
		 */
		private void pairs(Site t, Site[] sites, @Nullable Vacancies vacancies, boolean exact, boolean sure) {
			CrownLayer layer = t.layer();
			double edgeLimit = EDGE_REACH * t.radius();
			for (Site u : sites) {
				int dx = u.x() - t.x();
				int dz = u.z() - t.z();
				int d2 = dx * dx + dz * dz;
				if (d2 == 0 || d2 > SEARCH * SEARCH) {
					continue;
				}
				boolean uMarker = (u.gap() && layer != CrownLayer.REGENERATION) || (vacancies != null
						&& (exact ? vacancies.vacant(u.x(), u.z()) : sure && vacancies.surelyVacant(u.x(), u.z())));
				// A site of another layer takes part only as a marker: it has no crown in this layer.
				boolean foreign = u.layer() != layer;
				if (foreign && !uMarker) {
					continue;
				}
				int a;
				int b;
				if (uMarker) {
					a = t.edgeWeight();
					b = 0;
					if (d2 <= edgeLimit * edgeLimit) {
						edge = true;
					}
				} else {
					// Two trees of the layer: each owns its own column, the power of the other trunk there is never lower.
					// A marker's column is not reserved, so the crown may spread over it.
					a = Math.min(t.weight(), u.weight() + 16 * d2 - 1);
					b = Math.min(u.weight(), t.weight() + 16 * d2 - 1);
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
