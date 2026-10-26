package pl.polishforests.worldgen.tree;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * The dead wood planner (step V11 of docs/04-pionowy-wycinek.md): one dead wood object of a species, a pure function of
 * the world seed, its position and the ground around it (rule ZW-1), built from the skeleton of the tree planner
 * ({@link TreeShapePlan}, the trunk radii and limbs of a tree of the stand) and written with the thin trunk blocks.
 *
 * <p><b>Objects</b> ({@link Kind}): a lying log, a fragment of a stem with branch stubs (from the stem base with a stub of
 * roots, or from higher up); a snag, a standing dead tree without leaves and twigs, broken at 30–90% of its height with
 * a splinter on top (oaks keep their thick limbs, pines only stubs); a broken tree, a stem snapped 2–8 blocks above the
 * ground with its top lying beside it; a windthrow, a whole stem with its limbs on the ground, the root plate standing on
 * edge at its base and the shallow pit the plate came from; a stump, one block of trunk (managed stands only, decision
 * PW-4 b). How a tree dies depends on its species ({@link #kind}): spruces are thrown by the wind, oaks and ashes stand
 * long as snags, birches and beeches break.
 *
 * <p><b>Lying wood</b> lies along an axis (thin trunks bend only by whole blocks), along the contour on a slope and into
 * the columns the caller knows ({@link TreeShapePlan.Ground}, which answers {@link TreeShapePlan.Ground#UNKNOWN} outside
 * the write window, on water and on streets): a log rests on the ground with at most one block of air below it on at least
 * {@link #MIN_RESTING} of its length, and is shortened where the ground rises into it or falls away (risk of §3 V11).
 * Rotated limbs that would reach into the ground are left out, as are limbs that would stand up from the ground (the
 * census counts standing wood on the ground as a standing object).
 *
 * <p><b>Root plate</b> (windthrow): a disc of soil one block thick standing on edge at the stem base (rooted dirt inside,
 * coarse dirt and dirt at the ragged rim), with roots of the species' trunk block radiating from the stem base through it
 * ({@link DeadwoodVoxels#soil}, {@link DeadwoodVoxels#soilType}); step I1 (roots) may swap the soil cells and the roots for
 * its own blocks without changing the plan's geometry. The pit behind it is one block deep (two in the middle of a large
 * plate).
 *
 * <p>Moss and leaf litter are planned beside the lying wood ({@link DeadwoodVoxels#cover}).
 */
public final class DeadwoodPlan {
	private DeadwoodPlan() {
	}

	/** Salt of the dead wood objects (rule ZW-2: a new field, a new salt). */
	public static final String SALT = "feature.deadwood.objects";

	/** What a dead wood object is (not stored in saved worlds). */
	public enum Kind {
		/** A lying log (a stem fragment). */
		LOG,
		/** A standing dead tree. */
		SNAG,
		/** A stem broken low (a short snag) with its top lying beside it. */
		BROKEN,
		/** A whole fallen tree with its root plate and pit. */
		WINDTHROW,
		/** A stump of a felled tree (managed stands). */
		STUMP;

		/** Whether the object stands on the ground at its base (a snag, the snag of a broken tree, a stump). */
		public boolean standing() {
			return this == SNAG || this == BROKEN || this == STUMP;
		}
	}

	/**
	 * Share of the dead tree picks of a palette that a managed stand keeps [S]: the palettes of the slice give the dead
	 * trees of a natural stand (reserves, PW-5 b); managed forests hold a fraction of that (WISL means against the reserves:
	 * oak-hornbeam about 13 m³/ha against about 130 in BPN and 0.25 objects against 2 per chunk, pine about 11 against 65
	 * in Taborz and 0.1 logs with a snag per ha against 1 object per chunk). Broad-leaved species
	 * ({@link #MANAGED_KEEP}) and conifers ({@link #MANAGED_KEEP_CONIFER}).
	 */
	public static final double MANAGED_KEEP = 0.13;
	public static final double MANAGED_KEEP_CONIFER = 0.22;

	/** Share of the dead tree picks a managed stand keeps for a species. */
	public static double managedKeep(Species species) {
		return species == Species.SCOTS_PINE || species == Species.SPRUCE ? MANAGED_KEEP_CONIFER : MANAGED_KEEP;
	}
	/** Least share of a log's length resting on the ground (at most one block of air below; §3 V11). */
	public static final double MIN_RESTING = 0.8;
	/** Shortest lying log, blocks. */
	public static final int MIN_LOG = 3;
	/** Soil of the root plate: rooted dirt (inside), coarse dirt or dirt (the rim). */
	public static final byte SOIL_CORE = 0;
	public static final byte SOIL_RIM = 1;
	public static final byte SOIL_RIM_DIRT = 2;
	/** Cover beside lying wood: moss carpet, leaf litter. */
	public static final byte COVER_MOSS = 0;
	public static final byte COVER_LITTER = 1;

	/**
	 * Shares of the ways of dying of the trees of a natural stand [S after research-ekologia §5: spruce windthrows and
	 * bark beetle snags, oak snags that stand long, ash dieback, birch and beech breaking, hornbeam and linden rarely
	 * dying]: lying log, snag, broken tree, windthrow.
	 */
	private static double[] naturalForms(Species species) {
		return switch (species) {
			case OAK -> new double[] {0.45, 0.30, 0.17, 0.08};
			case ASH -> new double[] {0.40, 0.35, 0.17, 0.08};
			case BEECH -> new double[] {0.45, 0.10, 0.30, 0.15};
			case SCOTS_PINE -> new double[] {0.40, 0.25, 0.22, 0.13};
			case SPRUCE -> new double[] {0.30, 0.30, 0.05, 0.35};
			case BIRCH -> new double[] {0.40, 0.20, 0.35, 0.05};
			case NORWAY_MAPLE -> new double[] {0.55, 0.20, 0.15, 0.10};
			default -> new double[] {0.60, 0.15, 0.15, 0.10};
		};
	}

	/** Managed stands: the dead trees left after salvage are logs and snags, few broken trees, no windthrows [S]. */
	private static final double[] MANAGED_FORMS = {0.65, 0.30, 0.05, 0};

	/**
	 * The dead tree of a palette pick at world column (x, z), or null when a managed stand does not keep it
	 * ({@link #MANAGED_KEEP}).
	 */
	public static @Nullable Kind deadTree(long worldSeed, Species species, boolean natural, int x, int z) {
		Noise n = noise(worldSeed);
		if (!natural && n.unit(x, z, 1) >= managedKeep(species)) {
			return null;
		}
		double[] forms = natural ? naturalForms(species) : MANAGED_FORMS;
		double u = n.unit(x, z, 2);
		Kind[] kinds = {Kind.LOG, Kind.SNAG, Kind.BROKEN, Kind.WINDTHROW};
		for (int i = 0; i < forms.length; i++) {
			u -= forms[i];
			if (u < 0) {
				return kinds[i];
			}
		}
		return Kind.LOG;
	}

	/**
	 * Layer of the tree that died at world column (x, z): the canopy or the lower tree layer (suppressed trees die more
	 * often: 75% of the standing dead trees of WISL are thinner than 25 cm, so about half of those of an oak-hornbeam forest
	 * come from below the canopy; the pine forests have a thin lower layer). In managed stands the thinnings take the
	 * suppressed trees out before they die, so the dead wood left there (the WISL volumes per piece) is of canopy trees.
	 */
	public static CrownLayer layer(long worldSeed, Species species, boolean natural, int x, int z) {
		double canopy = natural ? species == Species.SCOTS_PINE || species == Species.SPRUCE ? 0.8 : 0.5 : 0.7;
		return noise(worldSeed).unit(x, z, 3) < canopy ? CrownLayer.CANOPY : CrownLayer.SUBCANOPY;
	}

	/**
	 * The object of a palette pick at world column (x, z) with its base at y (the block above the ground), or null when
	 * the stand there has none (a stump in a natural stand, a dead tree a managed stand does not keep): the stand map
	 * decides natural or managed and the age class.
	 *
	 * @param stump whether the pick is a stump ({@code stump/<species>}) rather than a dead tree ({@code deadwood/<species>})
	 */
	public static @Nullable Request request(long worldSeed, Species species, boolean stump, StandMap stands, int x, int y, int z,
			TreeShapePlan.Ground ground) {
		boolean natural = stands.natural(x, z);
		Kind kind = stump ? natural ? null : Kind.STUMP : deadTree(worldSeed, species, natural, x, z);
		if (kind == null) {
			return null;
		}
		CrownLayer layer = kind == Kind.STUMP ? CrownLayer.CANOPY : layer(worldSeed, species, natural, x, z);
		return new Request(species, kind, layer, deadAge(stands.ageClass(x, z), natural), natural, worldSeed, x, y, z, ground);
	}

	/**
	 * Age class of the dead wood of a stand [S]. A natural stand of any age holds the dead wood of old trees (the legacy of
	 * the generation before a disturbance and the old trees that outlive the cohort; the volumes of the reserves, PW-5 b,
	 * come from such wood). A managed stand has the dead wood of its own age class, in the pole stage the stumps and wood of
	 * the previous generation (the final felling), as big as those of a mature stand: the pole-stage stems themselves are
	 * about 1 px thin and would lie as sticks above the ground.
	 */
	public static AgeClass deadAge(AgeClass stand, boolean natural) {
		return natural ? AgeClass.OLD_GROWTH : stand == AgeClass.POLE_STAGE ? AgeClass.MATURE : stand;
	}

	private static Noise noise(long worldSeed) {
		return new Noise(worldSeed).derive(SALT);
	}

	/**
	 * Input of the planner.
	 *
	 * @param species a species with a planner shape ({@link SpeciesShape#supported})
	 * @param kind    the object
	 * @param layer   the layer of the tree that died (its size)
	 * @param age     the age class of the stand
	 * @param natural whether the stand is natural (its tree shapes)
	 * @param x       world column of the base (the stem base, the snag, the stump)
	 * @param y       Y of the base: the block above the ground of (x, z)
	 * @param z       world column of the base
	 * @param ground  the ground of the columns around ({@link TreeShapePlan.Ground#UNKNOWN} where nothing may go)
	 */
	public record Request(Species species, Kind kind, CrownLayer layer, AgeClass age, boolean natural, long worldSeed, int x,
			int y, int z, TreeShapePlan.Ground ground) {
		public Request {
			if (!SpeciesShape.supported(species)) {
				throw new IllegalArgumentException("no tree shape for " + species);
			}
		}
	}

	/**
	 * Plans the object of a request; the result may be empty ({@link DeadwoodVoxels#empty}) when it does not fit (no
	 * direction with room for a log, a suppressed tree).
	 */
	public static DeadwoodVoxels plan(Request r) {
		Random rnd = new Random(noise(r.worldSeed()).hash(r.x(), r.z(), 7 + r.kind().ordinal()));
		Builder b = new Builder(r);
		if (r.kind() == Kind.STUMP) {
			stump(r, b, rnd);
			return b.build(-1);
		}
		TreeVoxels tree = TreeShapePlan.plan(new TreeRequest(r.species(), r.layer(), r.age(), r.worldSeed(), r.x(), r.y(), r.z(),
				null, null, StandMap.uniform(r.age(), r.natural())));
		if (tree.woodCount() == 0) {
			return b.build(-1);
		}
		Skeleton s = new Skeleton(tree);
		int dir = switch (r.kind()) {
			case SNAG -> {
				snag(r, b, s, rnd);
				yield -1;
			}
			case BROKEN -> broken(r, b, s, rnd);
			case WINDTHROW -> windthrow(r, b, s, rnd);
			default -> log(r, b, s, rnd);
		};
		return b.build(dir);
	}

	// ------------------------------------------------------------------ objects

	private static void stump(Request r, Builder b, Random rnd) {
		SpeciesShape shape = SpeciesShape.of(r.species());
		// A felled tree of the stand, a little thinner than the mean (thinnings take the smaller trees), with the swell
		// of the stem base.
		double dbh = shape.dbh().get(r.age()) * (0.7 + 0.4 * rnd.next());
		int radius = Math.clamp((int) Math.round(dbh * 0.08 * 1.15), 2, 7);
		b.wood(0, 0, 0, radius, TreeVoxels.DOWN, -1, TreeVoxels.LOWER);
	}

	private static void snag(Request r, Builder b, Skeleton s, Random rnd) {
		double[] range = switch (r.species()) {
			// Oaks and ashes break above the crown base, among their limbs.
			case OAK -> new double[] {0.6, 0.85};
			case SCOTS_PINE -> new double[] {0.35, 0.8};
			case SPRUCE -> new double[] {0.5, 0.9};
			case BIRCH -> new double[] {0.25, 0.5};
			case ASH -> new double[] {0.6, 0.85};
			default -> new double[] {0.3, 0.65};
		};
		double frac = range[0] + (range[1] - range[0]) * rnd.next();
		int top = Math.max(3, (int) Math.round(frac * s.height()));
		int b0 = s.chainIndexAtHeight(top);
		// Limbs of a dead tree: oaks and ashes keep their thick limbs, the others shorter ones, pines only stubs, spruces
		// many short dry branches.
		int keepRadius = switch (r.species()) {
			case SCOTS_PINE, SPRUCE -> 1;
			default -> 2;
		};
		int maxDepth = switch (r.species()) {
			case OAK -> 6;
			case ASH -> 4;
			case SPRUCE -> 2;
			case SCOTS_PINE, BIRCH -> 1;
			default -> 3;
		};
		// Oaks and ashes die back from the top and stand "stag-headed": their thick limbs rise a few blocks above the broken
		// leader; the other species keep nothing above the break.
		boolean stagHead = r.species() == Species.OAK || r.species() == Species.ASH;
		int snagTop = s.tree.woodY(s.chain[b0]);
		int limbTop = snagTop + (stagHead ? 5 : 0);
		// Conifers: of each whorl only some dry stubs stay (a cross of opposite stubs reads as a telegraph pole).
		double stubChance = switch (r.species()) {
			case SCOTS_PINE -> 0.3;
			case SPRUCE -> 0.55;
			default -> 1;
		};
		int[] kept = new int[s.tree.woodCount()];
		Arrays.fill(kept, -1);
		TreeVoxels t = s.tree;
		for (int i = 0; i < t.woodCount(); i++) {
			int p = t.parentIndex(i);
			boolean onChain = s.chainPos[i] >= 0;
			if (onChain ? s.chainPos[i] > b0 : s.depth[i] > maxDepth || t.radius(i) < keepRadius || s.attach[i] >= b0
					|| t.woodY(i) > limbTop) {
				continue;
			}
			if (p >= 0 && kept[p] < 0) {
				continue;
			}
			if (!onChain && s.depth[i] == 1 && stubChance < 1
					&& (Noise.mix(r.worldSeed() ^ Noise.key(r.x() + t.woodX(i), r.z() + t.woodZ(i), t.woodY(i))) >>> 11) * 0x1.0p-53
							>= stubChance) {
				continue;
			}
			int at = b.woodIfFree(t.woodX(i), t.woodY(i), t.woodZ(i), Math.min(7, t.radius(i)), t.parent(i),
					p < 0 ? -1 : kept[p], t.bark(i));
			kept[i] = at;
		}
		splinter(b, s, kept[s.chain[b0]], s.chain[b0], rnd);
	}

	/** One or two thin blocks above the break of a stem: the splinters of a snapped trunk. */
	private static void splinter(Builder b, Skeleton s, int at, int node, Random rnd) {
		if (at < 0) {
			return;
		}
		TreeVoxels t = s.tree;
		int r = t.radius(node);
		int x = t.woodX(node);
		int y = t.woodY(node);
		int z = t.woodZ(node);
		int first = b.woodIfFree(x, y + 1, z, Math.max(1, r / 2), TreeVoxels.DOWN, at, t.bark(node));
		if (first >= 0 && r >= 3 && rnd.next() < 0.5) {
			b.woodIfFree(x, y + 2, z, Math.max(1, r / 3), TreeVoxels.DOWN, first, t.bark(node));
		}
	}

	private static int broken(Request r, Builder b, Skeleton s, Random rnd) {
		int height = 2 + (int) (rnd.next() * 7);
		int b0 = s.chainIndexAtHeight(height - 1);
		TreeVoxels t = s.tree;
		int prev = -1;
		for (int k = 0; k <= b0; k++) {
			int i = s.chain[k];
			prev = b.woodIfFree(t.woodX(i), t.woodY(i), t.woodZ(i), Math.min(7, t.radius(i)), t.parent(i), prev, t.bark(i));
			if (prev < 0) {
				return -1;
			}
		}
		splinter(b, s, prev, s.chain[b0], rnd);
		// The top lies beside the snag (a gap of one block), from the break to where the stem gets thin.
		int from = b0 + 1;
		int count = s.thickEnd() - from;
		if (count < MIN_LOG) {
			return -1;
		}
		int dir = direction(r, rnd, 2, count, false);
		if (dir < 0) {
			return -1;
		}
		int fit = fit(r, dir, 2, count);
		lying(r, b, s, from, fit, dir, 2, -1, opposite(dir), 2, 4, rnd);
		return dir;
	}

	private static int windthrow(Request r, Builder b, Skeleton s, Random rnd) {
		int count = s.thickEnd();
		if (count < MIN_LOG + 1) {
			return log(r, b, s, rnd);
		}
		int dir = direction(r, rnd, 1, count, true);
		if (dir < 0) {
			return -1;
		}
		int fit = fit(r, dir, 1, count);
		TreeVoxels t = s.tree;
		int[] f = TreeVoxels.STEP[dir];
		int[] l = lateral(dir);
		// The stem base (the root collar) at the base column, its arm into the plate.
		int r0 = Math.min(7, t.radius(0) + 1);
		int hub = b.wood(0, 0, 0, r0, opposite(dir), -1, TreeVoxels.LOWER);
		lying(r, b, s, 0, fit, dir, 1, hub, opposite(dir), 2, 5, rnd);
		// The root plate: a disc of soil one block thick standing on edge at the base, with roots through it [S: a plate
		// of about 2-6 m across for a stem of 0.3-0.8 m, wider for the shallow-rooted spruce].
		double dbh = t.radius(0) / 0.08 / 100;
		double plate = Math.clamp(0.9 + 2.2 * dbh, 1.2, 3.0) * (r.species() == Species.SPRUCE ? 1.25 : 1.0);
		int reach = (int) Math.ceil(plate);
		double cy = plate - 0.5;
		// Roots: left, right, up and the two diagonals, from the stem base out to about the rim.
		int[][] rays = {{-1, 0}, {1, 0}, {0, 1}, {-1, 1}, {1, 1}};
		boolean[][] root = new boolean[2 * reach + 3][2 * reach + 3];
		for (int[] ray : rays) {
			int u = 0;
			int v = 0;
			int parent = hub;
			int length = (int) Math.round(plate - 0.3 + 0.6 * rnd.next());
			for (int k = 0; k < length; k++) {
				int du = 0;
				int dv = 0;
				if (ray[0] != 0 && ray[1] != 0) {
					// A diagonal: sideways first, then up (no vertical step on the ground).
					if (k % 2 == 0) {
						du = ray[0];
					} else {
						dv = 1;
					}
				} else {
					du = ray[0];
					dv = ray[1];
				}
				u += du;
				v += dv;
				// The parent is the previous block: back along the step.
				int pdir = du != 0 ? dirOf(-du * l[0], 0, -du * l[2]) : TreeVoxels.DOWN;
				int radius = k < 2 ? 2 : 1;
				int at = b.woodGrounded(u * l[0], v, u * l[2], radius, pdir, parent, TreeVoxels.LOWER);
				if (at < 0) {
					break;
				}
				parent = at;
				if (Math.abs(u) <= reach + 1 && v <= 2 * reach + 1) {
					root[u + reach + 1][v] = true;
				}
			}
		}
		for (int u = -reach; u <= reach; u++) {
			for (int v = 0; v <= 2 * reach; v++) {
				double rho = Math.sqrt(u * u + (v - cy) * (v - cy));
				if (rho > plate + 0.2 || u == 0 && v == 0 || root[u + reach + 1][v]) {
					continue;
				}
				double rel = rho / plate;
				long h = Noise.mix(r.worldSeed() ^ Noise.key(r.x() + u * 31L, r.z() + v * 17L, 0x5011L));
				double hv = (h >>> 11) * 0x1.0p-53;
				if (rel > 0.85 && hv < 0.35) {
					continue;
				}
				byte type = rel < 0.55 ? SOIL_CORE : hv < 0.65 ? SOIL_RIM : SOIL_RIM_DIRT;
				b.soil(u * l[0], v, u * l[2], type);
			}
		}
		// The pit behind the plate, where the roots tore out.
		int baseTop = r.ground().top(r.x(), r.z());
		for (int a = -reach; a <= -1; a++) {
			for (int u = -reach; u <= reach; u++) {
				double e = (a * a + u * u) / ((plate + 0.25) * (plate + 0.25));
				if (e > 1) {
					continue;
				}
				int dx = a * f[0] + u * l[0];
				int dz = a * f[2] + u * l[2];
				int top = r.ground().top(r.x() + dx, r.z() + dz);
				if (top == TreeShapePlan.Ground.UNKNOWN || Math.abs(top - baseTop) > 1 || !known(r.ground(), r.x() + dx, r.z() + dz)) {
					continue;
				}
				b.pit(dx, dz, e <= 0.35 && plate >= 2 ? 2 : 1);
			}
		}
		return dir;
	}

	private static int log(Request r, Builder b, Skeleton s, Random rnd) {
		int thick = s.thickEnd();
		if (thick < MIN_LOG) {
			return -1;
		}
		boolean fromBase = rnd.next() < 0.35;
		int from = fromBase ? 0 : Math.min(thick - MIN_LOG, (int) ((0.15 + 0.35 * rnd.next()) * thick));
		boolean conifer = r.species() == Species.SCOTS_PINE || r.species() == Species.SPRUCE;
		// Natural broad-leaved stands: fragments of a stem; natural pine forests longer pieces; managed stands mostly whole
		// stems left lying (windfalls, crowns of the fellings), the WISL volume per piece [S].
		double share = !r.natural() ? 0.5 + 0.4 * rnd.next() : conifer ? 0.55 + 0.45 * rnd.next() : 0.3 + 0.45 * rnd.next();
		int count = Math.clamp((int) Math.round(share * (thick - from)), MIN_LOG, thick - from);
		int dir = direction(r, rnd, 0, count, false);
		if (dir < 0) {
			return -1;
		}
		int fit = fit(r, dir, 0, count);
		int first = lying(r, b, s, from, fit, dir, 0, -1, opposite(dir), 2, 2, rnd);
		if (fromBase && first >= 0) {
			// The stub of roots at the stem base: the flare behind it, short roots to the sides and one up.
			TreeVoxels t = s.tree;
			int r0 = Math.min(7, t.radius(0));
			int[] f = TreeVoxels.STEP[dir];
			int[] l = lateral(dir);
			b.woodGrounded(-f[0], 0, -f[2], Math.max(1, r0 - 1), dir, first, TreeVoxels.LOWER);
			for (int side = -1; side <= 1; side += 2) {
				int len = 1 + (int) (rnd.next() * 2);
				int parent = first;
				for (int k = 1; k <= len; k++) {
					parent = b.woodGrounded(side * k * l[0], 0, side * k * l[2], Math.max(1, r0 / 2 - k + 1),
							dirOf(-side * l[0], 0, -side * l[2]), parent, TreeVoxels.LOWER);
					if (parent < 0) {
						break;
					}
				}
			}
			b.woodGrounded(0, 1, 0, Math.max(1, r0 / 2), TreeVoxels.DOWN, first, TreeVoxels.LOWER);
		}
		return first < 0 ? -1 : dir;
	}

	/**
	 * Lays a stretch of the main stem straight along {@code dir} from {@code start} blocks off the base, with its limbs
	 * turned as the tree fell (limbs of at least {@code minRadius} px, at most {@code maxDepth} blocks from the stem), and
	 * plans moss or leaf litter beside it.
	 *
	 * @return the index of the first stem block, or -1
	 */
	private static int lying(Request r, Builder b, Skeleton s, int from, int count, int dir, int start, int parentIndex,
			int parentDir, int minRadius, int maxDepth, Random rnd) {
		TreeVoxels t = s.tree;
		int[] f = TreeVoxels.STEP[dir];
		int[] l = lateral(dir);
		int first = -1;
		int prev = parentIndex;
		int[] placedAt = new int[t.woodCount()];
		Arrays.fill(placedAt, -1);
		boolean conifer = r.species() == Species.SCOTS_PINE || r.species() == Species.SPRUCE;
		for (int k = 0; k < count; k++) {
			int node = s.chain[from + k];
			int a = start + k;
			int at = b.wood(a * f[0], 0, a * f[2], Math.min(7, t.radius(node)), k == 0 ? parentDir : opposite(dir), prev, t.bark(node));
			if (at < 0) {
				break;
			}
			if (k == 0) {
				first = at;
			}
			prev = at;
			placedAt[node] = at;
			for (int side = -1; side <= 1; side += 2) {
				double h = rnd.next();
				if (h < 0.4) {
					byte type = conifer ? h < 0.32 ? COVER_MOSS : COVER_LITTER : h < 0.24 ? COVER_LITTER : COVER_MOSS;
					b.cover(a * f[0] + side * l[0], a * f[2] + side * l[2], type);
				}
			}
		}
		// Limbs: the subtrees of the branches of the laid stem blocks, turned about the fall (up becomes the fall
		// direction, the fall direction becomes down), moved with the stem block they grow from; only their straight first
		// stretch (the dead limbs of a fallen tree break where they bend: no hooks of thin trunk blocks).
		int end = from + count;
		int[] heading = new int[t.woodCount()];
		for (int i = 1; i < t.woodCount(); i++) {
			if (s.chainPos[i] >= 0) {
				continue;
			}
			int p = t.parentIndex(i);
			if (s.attach[i] < from || s.attach[i] >= end || placedAt[p] < 0 || t.radius(i) < minRadius || s.depth[i] > maxDepth) {
				continue;
			}
			int c = s.chain[s.attach[i]];
			int ox = t.woodX(i) - t.woodX(c);
			int oy = t.woodY(i) - t.woodY(c);
			int oz = t.woodZ(i) - t.woodZ(c);
			int a = ox * f[0] + oz * f[2];
			int bl = ox * l[0] + oz * l[2];
			int along = start + (s.attach[i] - from) + oy;
			int up = -a;
			int[] pv = TreeVoxels.STEP[t.parent(i)];
			int pa = pv[0] * f[0] + pv[2] * f[2];
			int pb = pv[0] * l[0] + pv[2] * l[2];
			int pdir = dirOf(pv[1] * f[0] + pb * l[0], -pa, pv[1] * f[2] + pb * l[2]);
			if (up < 0 || up == 0 && (pdir == TreeVoxels.UP || pdir == TreeVoxels.DOWN)
					|| s.chainPos[p] < 0 && pdir != heading[p]) {
				continue;
			}
			int at = b.woodGrounded(along * f[0] + bl * l[0], up, along * f[2] + bl * l[2], Math.min(7, t.radius(i)), pdir,
					placedAt[p], t.bark(i));
			placedAt[i] = at;
			heading[i] = pdir;
		}
		return first;
	}

	// ------------------------------------------------------------------ fitting

	/**
	 * Fall direction of lying wood starting {@code start} blocks off the base and {@code count} long: along the contour on
	 * a slope (the axis across the steeper gradient), windthrows mostly to the east (the prevailing westerly winds) on
	 * level ground, else at random; the first with room for the whole length, else the one with the most room (at least
	 * {@link #MIN_LOG}), or -1.
	 */
	private static int direction(Request r, Random rnd, int start, int count, boolean windthrow) {
		TreeShapePlan.Ground g = r.ground();
		int gx = slope(g, r.x() + 2, r.z(), r.x() - 2, r.z());
		int gz = slope(g, r.x(), r.z() + 2, r.x(), r.z() - 2);
		int[] order;
		boolean flip = rnd.next() < 0.5;
		if (Math.abs(gx) + Math.abs(gz) >= 2) {
			// Along the contour: the axis across the steeper gradient first.
			order = Math.abs(gx) > Math.abs(gz)
					? flip ? new int[] {TreeVoxels.NORTH, TreeVoxels.SOUTH, TreeVoxels.EAST, TreeVoxels.WEST}
							: new int[] {TreeVoxels.SOUTH, TreeVoxels.NORTH, TreeVoxels.WEST, TreeVoxels.EAST}
					: flip ? new int[] {TreeVoxels.EAST, TreeVoxels.WEST, TreeVoxels.NORTH, TreeVoxels.SOUTH}
							: new int[] {TreeVoxels.WEST, TreeVoxels.EAST, TreeVoxels.SOUTH, TreeVoxels.NORTH};
		} else if (windthrow) {
			double u = rnd.next();
			order = u < 0.5 ? new int[] {TreeVoxels.EAST, TreeVoxels.NORTH, TreeVoxels.SOUTH, TreeVoxels.WEST}
					: u < 0.7 ? new int[] {TreeVoxels.NORTH, TreeVoxels.EAST, TreeVoxels.SOUTH, TreeVoxels.WEST}
							: u < 0.9 ? new int[] {TreeVoxels.SOUTH, TreeVoxels.EAST, TreeVoxels.NORTH, TreeVoxels.WEST}
									: new int[] {TreeVoxels.WEST, TreeVoxels.NORTH, TreeVoxels.SOUTH, TreeVoxels.EAST};
		} else {
			order = new int[] {TreeVoxels.NORTH, TreeVoxels.SOUTH, TreeVoxels.WEST, TreeVoxels.EAST};
			for (int i = order.length - 1; i > 0; i--) {
				int j = (int) (rnd.next() * (i + 1));
				int tmp = order[i];
				order[i] = order[j];
				order[j] = tmp;
			}
		}
		int best = -1;
		int bestFit = MIN_LOG - 1;
		for (int d : order) {
			int fit = fit(r, d, start, count);
			if (fit >= count) {
				return d;
			}
			if (fit > bestFit) {
				best = d;
				bestFit = fit;
			}
		}
		return best;
	}

	/** Whether the four neighbors of a column are known ground (no water or street beside a pit). */
	private static boolean known(TreeShapePlan.Ground g, int x, int z) {
		return g.top(x + 1, z) != TreeShapePlan.Ground.UNKNOWN && g.top(x - 1, z) != TreeShapePlan.Ground.UNKNOWN
				&& g.top(x, z + 1) != TreeShapePlan.Ground.UNKNOWN && g.top(x, z - 1) != TreeShapePlan.Ground.UNKNOWN;
	}

	private static int slope(TreeShapePlan.Ground g, int x1, int z1, int x0, int z0) {
		int a = g.top(x1, z1);
		int b = g.top(x0, z0);
		return a == TreeShapePlan.Ground.UNKNOWN || b == TreeShapePlan.Ground.UNKNOWN ? 0 : a - b;
	}

	/**
	 * Length of lying wood that fits along {@code dir} from {@code start} blocks off the base, at most {@code count}: the
	 * blocks stay above the ground of their columns, never over more than two blocks of air, the last one resting, and at
	 * least {@link #MIN_RESTING} of them resting (at most one block of air below); 0 when not even one fits.
	 */
	static int fit(Request r, int dir, int start, int count) {
		int[] f = TreeVoxels.STEP[dir];
		int[] gap = new int[count];
		int n = 0;
		for (int k = 0; k < count; k++) {
			int top = r.ground().top(r.x() + (start + k) * f[0], r.z() + (start + k) * f[2]);
			if (top == TreeShapePlan.Ground.UNKNOWN || top >= r.y()) {
				break;
			}
			int air = r.y() - 1 - top;
			if (air > 2) {
				break;
			}
			gap[n++] = air;
		}
		int best = 0;
		int high = 0;
		for (int len = 1; len <= n; len++) {
			if (gap[len - 1] > 1) {
				high++;
			}
			if (gap[len - 1] <= 1 && high <= (1 - MIN_RESTING) * len) {
				best = len;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ directions

	/** The horizontal direction to the left of a fall direction ({@link TreeVoxels#STEP} vector). */
	static int[] lateral(int dir) {
		int[] f = TreeVoxels.STEP[dir];
		return new int[] {-f[2], 0, f[0]};
	}

	static int opposite(int dir) {
		return dir ^ 1;
	}

	/** The direction of a unit vector. */
	static int dirOf(int dx, int dy, int dz) {
		for (int d = 0; d < 6; d++) {
			int[] s = TreeVoxels.STEP[d];
			if (s[0] == dx && s[1] == dy && s[2] == dz) {
				return d;
			}
		}
		throw new IllegalArgumentException("not a unit vector: " + dx + ", " + dy + ", " + dz);
	}

	// ------------------------------------------------------------------ skeleton

	/** The main stem of a planned tree and, for every other wood block, the stem block its branch grows from. */
	private static final class Skeleton {
		final TreeVoxels tree;
		/** Wood indices of the main stem from the root: the thickest child at each step, upward first. */
		final int[] chain;
		/** Position of each wood block on the main stem, -1 off it. */
		final int[] chainPos;
		/** For a block off the stem the stem position its branch grows from; for a stem block its own position. */
		final int[] attach;
		/** Blocks from the stem (0 on it). */
		final int[] depth;

		Skeleton(TreeVoxels t) {
			this.tree = t;
			int n = t.woodCount();
			int[] best = new int[n];
			Arrays.fill(best, -1);
			for (int i = 1; i < n; i++) {
				int p = t.parentIndex(i);
				int c = best[p];
				if (c < 0 || t.radius(i) > t.radius(c) || t.radius(i) == t.radius(c) && t.parent(i) == TreeVoxels.DOWN
						&& t.parent(c) != TreeVoxels.DOWN) {
					best[p] = i;
				}
			}
			int[] list = new int[n];
			int m = 0;
			for (int i = 0; i >= 0; i = best[i]) {
				list[m++] = i;
			}
			chain = Arrays.copyOf(list, m);
			chainPos = new int[n];
			Arrays.fill(chainPos, -1);
			for (int k = 0; k < m; k++) {
				chainPos[chain[k]] = k;
			}
			attach = new int[n];
			depth = new int[n];
			for (int i = 0; i < n; i++) {
				if (chainPos[i] >= 0) {
					attach[i] = chainPos[i];
					depth[i] = 0;
				} else {
					int p = t.parentIndex(i);
					attach[i] = attach[p];
					depth[i] = depth[p] + 1;
				}
			}
		}

		/** Height of the tree's wood, blocks above the base. */
		int height() {
			return tree.woodY(chain[chain.length - 1]) + 1;
		}

		/** The last stem position at or below a height above the base. */
		int chainIndexAtHeight(int dy) {
			int k = 0;
			while (k + 1 < chain.length && tree.woodY(chain[k + 1]) <= dy) {
				k++;
			}
			return k;
		}

		/** Stem blocks from the base while the stem is at least 2 px thick (the thin top breaks off a dead tree). */
		int thickEnd() {
			int k = 0;
			while (k < chain.length && tree.radius(chain[k]) >= 2) {
				k++;
			}
			return k;
		}
	}

	// ------------------------------------------------------------------ output

	/** A small deterministic random sequence from a hash. */
	private static final class Random {
		private long state;

		Random(long seed) {
			this.state = seed;
		}

		double next() {
			state += 0x9E37_79B9_7F4A_7C15L;
			return (Noise.mix(state) >>> 11) * 0x1.0p-53;
		}
	}

	/** Collects the blocks of an object, at most one per position. */
	private static final class Builder {
		private final Request r;
		private int[] wood = new int[64];
		private byte[] radius = new byte[64];
		private byte[] parent = new byte[64];
		private int[] parentIndex = new int[64];
		private byte[] bark = new byte[64];
		private int woods;
		private int[] soil = new int[16];
		private byte[] soilType = new byte[16];
		private int soils;
		private int[] pit = new int[16];
		private int pits;
		private int[] cover = new int[16];
		private byte[] coverType = new byte[16];
		private int covers;
		private final java.util.HashSet<Integer> taken = new java.util.HashSet<>();

		Builder(Request r) {
			this.r = r;
		}

		/** A wood block at an offset (no ground check); -1 when the position is taken or out of the packing range. */
		int wood(int dx, int dy, int dz, int rad, int dir, int parentIdx, int barkType) {
			if (dy < 0 || dx < -64 || dx > 63 || dz < -64 || dz > 63 || !taken.add(TreeVoxels.pack(dx, dy, dz))) {
				return -1;
			}
			if (woods == wood.length) {
				int n = woods * 2;
				wood = Arrays.copyOf(wood, n);
				radius = Arrays.copyOf(radius, n);
				parent = Arrays.copyOf(parent, n);
				parentIndex = Arrays.copyOf(parentIndex, n);
				bark = Arrays.copyOf(bark, n);
			}
			wood[woods] = TreeVoxels.pack(dx, dy, dz);
			radius[woods] = (byte) Math.clamp(rad, 1, 7);
			parent[woods] = (byte) dir;
			parentIndex[woods] = parentIdx;
			bark[woods] = (byte) barkType;
			return woods++;
		}

		/** A wood block whose column is known and whose block lies above the ground there. */
		int woodGrounded(int dx, int dy, int dz, int rad, int dir, int parentIdx, int barkType) {
			if (parentIdx < 0 || !above(dx, dy, dz)) {
				return -1;
			}
			return wood(dx, dy, dz, rad, dir, parentIdx, barkType);
		}

		/** A wood block of a standing tree: above the ground of its column. */
		int woodIfFree(int dx, int dy, int dz, int rad, int dir, int parentIdx, int barkType) {
			if (!above(dx, dy, dz)) {
				return -1;
			}
			return wood(dx, dy, dz, rad, dir, parentIdx, barkType);
		}

		private boolean above(int dx, int dy, int dz) {
			int top = r.ground().top(r.x() + dx, r.z() + dz);
			return top != TreeShapePlan.Ground.UNKNOWN && r.y() + dy > top;
		}

		void soil(int dx, int dy, int dz, byte type) {
			if (!above(dx, dy, dz) || !taken.add(TreeVoxels.pack(dx, dy, dz))) {
				return;
			}
			if (soils == soil.length) {
				soil = Arrays.copyOf(soil, soils * 2);
				soilType = Arrays.copyOf(soilType, soils * 2);
			}
			soil[soils] = TreeVoxels.pack(dx, dy, dz);
			soilType[soils++] = type;
		}

		void pit(int dx, int dz, int depth) {
			if (pits == pit.length) {
				pit = Arrays.copyOf(pit, pits * 2);
			}
			pit[pits++] = TreeVoxels.pack(dx, depth, dz);
		}

		/** Cover at the ground level of the base (only where the ground of the column is the base's ground). */
		void cover(int dx, int dz, byte type) {
			int top = r.ground().top(r.x() + dx, r.z() + dz);
			if (top == TreeShapePlan.Ground.UNKNOWN || top != r.y() - 1 || taken.contains(TreeVoxels.pack(dx, 0, dz))) {
				return;
			}
			for (int i = 0; i < covers; i++) {
				if (cover[i] == TreeVoxels.pack(dx, 0, dz)) {
					return;
				}
			}
			if (covers == cover.length) {
				cover = Arrays.copyOf(cover, covers * 2);
				coverType = Arrays.copyOf(coverType, covers * 2);
			}
			cover[covers] = TreeVoxels.pack(dx, 0, dz);
			coverType[covers++] = type;
		}

		DeadwoodVoxels build(int dir) {
			// Cover never under wood or soil written later.
			int kept = 0;
			for (int i = 0; i < covers; i++) {
				if (!taken.contains(cover[i])) {
					cover[kept] = cover[i];
					coverType[kept++] = coverType[i];
				}
			}
			covers = kept;
			TreeVoxels t = new TreeVoxels(r.x(), r.y(), r.z(), Arrays.copyOf(wood, woods), Arrays.copyOf(radius, woods),
					Arrays.copyOf(parent, woods), Arrays.copyOf(parentIndex, woods), Arrays.copyOf(bark, woods), new int[0], new byte[0],
					woods == 0 ? 0 : 1);
			return new DeadwoodVoxels(r.kind(), r.species(), dir, t, Arrays.copyOf(soil, soils), Arrays.copyOf(soilType, soils),
					Arrays.copyOf(pit, pits), Arrays.copyOf(cover, covers), Arrays.copyOf(coverType, covers));
		}
	}
}
