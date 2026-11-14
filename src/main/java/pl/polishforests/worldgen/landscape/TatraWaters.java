package pl.polishforests.worldgen.landscape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Step TT2 (docs/04-pionowy-wycinek.md, "Projekt TT2"): the tarns of the Tatra massifs and their outlet brooks.
 *
 * <p>A valley of a massif ({@link TatraField}) holds up to two tarns: one in the overdeepened bowl of its cirque and one
 * behind its frontal moraine (the valley next to the highest summit on the Podhale side holds the largest one, of the
 * Morskie Oko type). Each tarn has its own whole-meter level and a basin: an ellipse in the valley-local frame (q across
 * the valley axis, un from the main ridge). Inside the basin the water fills every column whose ground lies below the
 * level, so the shore follows the ground; a ring {@value #RING} m wide around the basin keeps the ground at least half a
 * meter above the level. Every water column of a tarn therefore has its four neighbors either water of the same tarn at
 * the same level or ground at least at the level: the basin is closed by construction, whatever the ground inside it.
 * The level of a cirque tarn lies a meter under the lowest point of the bowl rim, the level of a moraine tarn a few
 * meters under the moraine crest, so the ring rarely raises the ground (a low natural dam where it does). Round 1 of the
 * review of TT2: the levels come from the land height, but the ground of the column is the land after the incision of the
 * rivers of TT1 ({@link #ground}); a basin over an incised valley (its ground well under the bed, or its ring and dam far
 * above the ground) would hold the water in a tall wall, so the valley has no tarn there ({@link #onGround}). The raise of
 * the outer face of the ring fades out to the edge of the belt, so the belt never ends in a step.
 *
 * <p>A tarn with an outlet has a sill at its level where its bank ring is lowest (a dry notch the water reaches, never
 * crosses) and a brook below it. Step TT7 (TT2-o): the brook leaves the ring at its lowest point on the down-valley half
 * and runs down the steepest slope of the land (a fan of headings looked ahead every few steps, turning at most
 * {@value #TURN} degrees a step), down to a lower tarn of the valley or into the first channel of the river network (TT1)
 * it meets, bending to it across the floor (TT2: a channel along the valley axis from the down-valley end of the basin).
 * Where that brook ends without reaching the water, the next lowest points of the ring are tried (at most
 * {@value #OUTLET_TRIES}); the first brook that joins is kept. Its level falls
 * monotonically in whole meters with the ground (the lowest ground so far, less a meter); it never lies below the water
 * it enters. Its banks are kept at least at its level; along the brook the level steps down as a cascade, as the
 * mountain streams of the river network do.
 *
 * <p>Everything is a pure function of the world seed: the tarns of a valley are computed once from the terrain
 * ({@link LandscapeModel#landElevation}) and the river network, never from {@link LandscapeModel#sample}, and cached.
 */
final class TatraWaters {
	/** Width of the bank ring around a basin (m). */
	static final double RING = 3;
	/** Half width of the outlet sill (m). */
	static final double SILL = 1.5;
	/**
	 * Brook: half width of the channel, width of the bank kept at its level, step of its course along the valley (m). Step
	 * TT7: the bank 5 m wide (3 m in TT2), so that it falls at BANK_SLOPE from 25 m above the brook, near the deepest cut
	 * ({@link #GORGE}).
	 */
	static final double BROOK_HALF = 1.6;
	static final double BROOK_BANK = 5;
	/** Slope of the bank of a brook cut into the ground (m per m): a gorge with walls, no step. */
	static final double BANK_SLOPE = 5;
	static final double BROOK_STEP = 0.5;
	/** Largest fall of the brook level between two steps (m) and the longest brook (steps; times the form scale). */
	static final int MAX_DROP = 2;
	static final int MAX_STEPS = 6_000;
	/**
	 * Step TT7: the steepest descent of a brook: the headings looked ahead (degrees from the current one), the look-ahead
	 * distance (m), the steps between two looks, the largest turn per step (degrees), the steps over which a brook never
	 * comes back near its own course and the distance from a river channel from which it bends to it (m).
	 */
	static final double[] FAN = {0, -20, 20, -40, 40, -60, 60};
	static final double LOOK = 8;
	static final int LOOK_EVERY = 4;
	static final double TURN = 4;
	static final int SELF_GAP = 40;
	static final double NEAR_RIVER = 30;
	/**
	 * Step TT7: the share of the cirque tarns and of the moraine tarns that try an outlet (the Morskie Oko type and every
	 * moraine tarn always do; drawn with the new salt 68, the salt 69 of the moraine tarns kept for a share below 1).
	 */
	static final double OUTLET_CIRQUE = 0.95;
	static final double OUTLET_MORAINE = 1.0;
	/** Step TT7: the most points of the ring an outlet brook is tried from, and how far apart they are (in ring points). */
	static final int OUTLET_TRIES = 8;
	static final int OUTLET_GAP = 2;
	/** Step TT7: points on the ring searched for the outlet. */
	static final int RING_POINTS = 48;
	/**
	 * Deepest cut of a brook below the ground (m; step TT7: 30, 12 in TT2, so that a brook cuts a notch through the lip of
	 * a cirque step; the descent never heads onto ground higher than that above its level) and the shortest brook (steps).
	 */
	static final int GORGE = 30;
	static final int MIN_STEPS = 40;
	/** Width of the belt around a tarn in which the columns record it as their standing water (m). */
	static final double BELT = 40;
	/** Largest cut of a basin into the ground above its level at its center (m): deeper, the valley has no tarn there. */
	static final double MAX_CUT = 30;
	/** Slope of the shore wall above the basin edge and of the outer face of the ring (m per m). */
	static final double WALL = 3;
	static final double DAM = 1.5;
	/** The shore wall steepens away from the edge (its slope doubles every {@value #WALL_BEND} m), so it always meets the
	 * ground within the belt: a cirque tarn lies under a rock wall. */
	static final double WALL_BEND = 8;
	/** Depth of the brook channel under its level (blocks). */
	static final double BROOK_DEPTH = 1.2;
	/**
	 * Round 1 of the review of TT2: the deepest ground of the basin and its ring under the bed of its center, and the highest
	 * raise of the outer face of the ring over the ground of the belt, for a tarn to stay (blocks at its level; deeper or
	 * higher, the basin lies over an incised river valley and would hold its water in a wall). On the seeds of
	 * TatraSurvey the tarns kept reach 1.9 and 4.5 blocks, those over incised valleys 3.0–97 and 5.8–101.
	 */
	static final double DEEPER = 2.5;
	static final double RAISE = 5;

	/**
	 * Outlet brook: the course (step TT7: the points q across the valley axis and un from the main ridge,
	 * {@value #BROOK_STEP} m apart along the steepest descent) and the level of each step, with a lattice of the steps for
	 * the columns.
	 */
	static final class Brook {
		/** Side of the lattice cells of the steps (m): at least the half width of the channel with its bank. */
		private static final double CELL = 7;
		private final double[] q;
		private final double[] un;
		private final int[] level;
		private final boolean joined;
		private final boolean intoTarn;
		private final double width;
		private final Map<Long, int[]> cells = new HashMap<>();

		Brook(double[] q, double[] un, int[] level, boolean joined, boolean intoTarn, double width) {
			this.q = q;
			this.un = un;
			this.level = level;
			this.joined = joined;
			this.intoTarn = intoTarn;
			this.width = width;
			for (int i = 0; i < q.length; i++) {
				long key = cell(q[i], un[i] * width);
				int[] a = cells.get(key);
				int[] b = a == null ? new int[1] : Arrays.copyOf(a, a.length + 1);
				b[b.length - 1] = i;
				cells.put(key, b);
			}
		}

		private static long cell(double q, double along) {
			return Noise.key((long) Math.floor(q / CELL), (long) Math.floor(along / CELL), 0);
		}

		int steps() {
			return q.length;
		}

		int[] level() {
			return level;
		}

		double[] q() {
			return q;
		}

		double[] un() {
			return un;
		}

		/** Whether the brook ends in the river network or a lower tarn (else in a closed pool above a steep step). */
		boolean joined() {
			return joined;
		}

		boolean intoTarn() {
			return intoTarn;
		}

		/**
		 * The step nearest the valley-local point within {@code reach} m, as {index, distance, beyond} (beyond 1 where the
		 * point lies more than half a step past the last step, or before the first one), or null.
		 */
		double[] nearest(double pq, double pun, double reach) {
			double pa = pun * width;
			long cq = (long) Math.floor(pq / CELL);
			long ca = (long) Math.floor(pa / CELL);
			int best = -1;
			double bd = reach;
			for (long dq = -1; dq <= 1; dq++) {
				for (long da = -1; da <= 1; da++) {
					int[] a = cells.get(Noise.key(cq + dq, ca + da, 0));
					if (a == null) {
						continue;
					}
					for (int i : a) {
						double d = Math.hypot(pq - q[i], pa - un[i] * width);
						if (d < bd || d == bd && i < best) {
							bd = d;
							best = i;
						}
					}
				}
			}
			if (best < 0) {
				return null;
			}
			int n = q.length;
			double beyond = 0;
			if (n > 1 && (best == n - 1 || best == 0)) {
				int o = best == 0 ? 1 : n - 2;
				double tq = q[best] - q[o];
				double ta = (un[best] - un[o]) * width;
				double tl = Math.hypot(tq, ta);
				if (tl > 1e-9 && ((pq - q[best]) * tq + (pa - un[best] * width) * ta) / tl > 0.5 * BROOK_STEP) {
					beyond = 1;
				}
			}
			return new double[] {best, bd, beyond};
		}
	}

	/**
	 * A tarn: its valley (massif, side, index), whether it lies behind a moraine (else in a cirque bowl), the basin center
	 * un0 on the axis and its semi-axes (aq across, bu along, m), the level (m a.s.l.), the outlet brook (or null), the
	 * world point of the basin center and an id constant over the tarn.
	 */
	record Tarn(long id, TatraField.Massif massif, boolean north, long valley, boolean moraine, boolean morskie, double un0,
			double aq, double bu, int level, double depth, Brook brook, double x, double z, double outQ, double outA) {
		/** Step TT7: a tarn without an outlet yet (the direction outQ, outA of its outlet is set with the brook). */
		Tarn(long id, TatraField.Massif massif, boolean north, long valley, boolean moraine, boolean morskie, double un0,
				double aq, double bu, int level, double depth, double x, double z) {
			this(id, massif, north, valley, moraine, morskie, un0, aq, bu, level, depth, null, x, z, Double.NaN, Double.NaN);
		}

		/**
		 * Step TT7: whether the valley-local offset (q, a m along the valley from the basin center) at the normalized radius
		 * r lies on the sill: in the strip {@value #SILL} m to each side of the ray from the center through the outlet,
		 * between 0.85 of the basin edge and the outer edge of the ring.
		 */
		boolean onSill(double q, double a, double r) {
			if (brook == null || r <= 0.85 || r >= ring()) {
				return false;
			}
			return q * outQ + a * outA > 0 && Math.abs(q * outA - a * outQ) < SILL;
		}

		/** Normalized radius of the basin ellipse at the valley-local point (1 on its edge). */
		double r(double q, double un, double width) {
			return Math.hypot(q / aq, (un - un0) * width / bu);
		}

		/** Normalized radius of the outer edge of the ring. */
		double ring() {
			return 1 + RING / Math.min(aq, bu);
		}

		/** Distance from the basin edge at the normalized radius r (m, negative inside). */
		double edge(double r) {
			return (r - 1) * Math.min(aq, bu);
		}

		/** Bed of the basin at the normalized radius r below 1: the level less the depth, rising to the level at the edge. */
		double bed(double r) {
			return level - depth * Math.max(0.0, 1 - r * r);
		}

		boolean outlet() {
			return brook != null;
		}
	}

	/** The tarns of one valley side, down the valley, and the un range they and their brooks and belts touch. */
	record Valley(List<Tarn> tarns, double unMin, double unMax) {
	}

	private static final Valley EMPTY = new Valley(List.of(), 0, -1);

	/**
	 * What a column has of the tarns: the tarn whose belt it lies in (or null) with the normalized radius of the column and
	 * its sill, and a brook (or none).
	 */
	static final class Hit {
		Tarn tarn;
		double r = Double.POSITIVE_INFINITY;
		boolean sill;
		/** Distance from the basin edge of the tarn (m). */
		double beltDistance = Double.POSITIVE_INFINITY;
		int brookLevel = ColumnSample.NO_WATER;
		boolean brookChannel;
		/** Distance across the brook from its axis relative to its half width (0 on the axis, 1 at the bank). */
		double brookQ;

		/**
		 * The ground after the brook channel or bank and the tarn: inside the basin its bed (the ground where lower), the sill
		 * at the level, the ring at least half a meter above the level with its outer face, and the shore wall above the
		 * edge (the basin is cut into the ground above the level: a cirque floor overdeepened, the ground behind a moraine).
		 */
		double surface(double surface, double brookDepth) {
			double s = surface;
			boolean brook = brookLevel != ColumnSample.NO_WATER;
			if (brook && brookChannel) {
				// A rounded channel: its walls rise to the level at the edge (no step in the bank).
				s = Math.min(s, brookLevel - brookDepth * Math.max(0.0, 1 - brookQ * brookQ));
			}
			if (tarn != null) {
				s = tarn(s);
			}
			// The bank of a brook last: the shore wall of a lower tarn never cuts it below the brook; where the brook is cut
			// into the ground, the bank falls to it at BANK_SLOPE.
			if (brook && !brookChannel && !inRing()) {
				s = Math.max(Math.min(s, brookLevel + BANK_SLOPE * BROOK_HALF * (brookQ - 1)), brookLevel);
			}
			return s;
		}

		private double tarn(double s) {
			int level = tarn.level();
			if (r < 1) {
				return sill ? Math.min(s, level) : Math.min(s, tarn.bed(r));
			}
			if (sill) {
				return level;
			}
			double e = tarn.edge(r);
			s = Math.min(s, wall(e));
			if (inRing()) {
				return Math.max(s, level + 0.5);
			}
			// The outer face of the ring (where the ground falls away from the basin: a low dam; the brook rises at its foot).
			// Its raise fades out at DAM per meter to the edge of the belt (round 1 of the review of TT2: no step there).
			double dam = level + 0.5 - DAM * (e - RING);
			return dam <= s ? s : Math.min(dam, s + DAM * Math.max(0.0, BELT - e));
		}

		/** The shore wall above the basin edge at the distance e from it (m): the ground is cut down to it. */
		private double wall(double e) {
			return tarn.level() + 0.5 + WALL * e * (1 + e / WALL_BEND);
		}

		/**
		 * Whether the column lies in the basin or ring of its tarn or its ground is the shore wall (the steep cut of the
		 * basin into the ground above the level): the parts of a tarn that are steep on purpose (tests of continuity).
		 */
		boolean steepPart(double surface) {
			return tarn != null && (inRing() || !sill && Math.abs(surface - wall(tarn.edge(r))) < 1e-6);
		}

		/** Whether the column is inside the basin of its tarn (its water where the ground is below the level). */
		boolean inBasin() {
			return tarn != null && r < 1;
		}

		/** Whether the column is in the basin or the ring of its tarn (never other water there). */
		boolean inRing() {
			return tarn != null && r < tarn.ring();
		}
	}

	private final LandscapeModel model;
	private final TatraField field;
	private final DirectCache<Valley> valleys = new DirectCache<>(4_096, this::build);

	TatraWaters(LandscapeModel model, TatraField field) {
		this.model = model;
		this.field = field;
	}

	private static long valleyI(TatraField.Massif m, long j) {
		return m.i() * 4_096 + j;
	}

	private static long valleyJ(TatraField.Massif m, boolean north) {
		return m.j() * 2 + (north ? 0 : 1);
	}

	/** The tarns of valley j of a side of the massif (cached). */
	Valley valley(TatraField.Massif m, boolean north, long j) {
		return valleys.get(valleyI(m, j), valleyJ(m, north));
	}

	/** What the column of the massif has of the tarns and brooks of its valley, or null (nothing). */
	Hit hit(TatraField.Column c) {
		if (c.massif == null || Double.isNaN(c.un)) {
			return null;
		}
		Valley val = valley(c.massif, c.north, c.valley);
		if (c.un < val.unMin() || c.un > val.unMax()) {
			return null;
		}
		double width = c.north ? c.massif.wn() : c.massif.ws();
		Hit hit = null;
		for (Tarn t : val.tarns()) {
			double r = t.r(c.q, c.un, width);
			double edge = t.edge(r);
			if (edge < BELT) {
				hit = hit == null ? new Hit() : hit;
				if (edge < hit.beltDistance) {
					hit.tarn = t;
					hit.r = r;
					hit.beltDistance = edge;
					hit.sill = t.onSill(c.q, (c.un - t.un0()) * width, r);
				}
			}
			Brook b = t.brook();
			if (b != null) {
				// Step TT7: the nearest step of the course; past the last one the bank closes the end of the channel (at the
				// river or the next tarn), before the first one lies the sill.
				double[] near = b.nearest(c.q, c.un, BROOK_HALF + BROOK_BANK);
				if (near != null && !(near[0] == 0 && near[2] > 0)) {
					int at = (int) near[0];
					double dq = near[1];
					hit = hit == null ? new Hit() : hit;
					if (hit.brookLevel == ColumnSample.NO_WATER || b.level()[at] > hit.brookLevel) {
						hit.brookLevel = b.level()[at];
						hit.brookChannel = !(at == b.steps() - 1 && near[2] > 0) && dq < BROOK_HALF;
						hit.brookQ = dq / BROOK_HALF;
					}
				}
			}
		}
		return hit;
	}

	/** The tarns of a massif, both sides, in the order of the valleys. */
	List<Tarn> tarns(TatraField.Massif m) {
		List<Tarn> out = new ArrayList<>();
		for (boolean north : new boolean[] {true, false}) {
			for (long j = firstValley(m, north); j <= lastValley(m, north); j++) {
				out.addAll(valley(m, north, j).tarns());
			}
		}
		return out;
	}

	/** Index of the first and last valley of a side whose axis may cross the massif proper. */
	static long firstValley(TatraField.Massif m, boolean north) {
		double phase = north ? m.phaseN() : m.phaseS();
		return (long) Math.floor(-m.halfLength() / m.spacing() + phase - 1);
	}

	static long lastValley(TatraField.Massif m, boolean north) {
		double phase = north ? m.phaseN() : m.phaseS();
		return (long) Math.ceil(m.halfLength() / m.spacing() + phase + 1);
	}

	// ------------------------------------------------------------------ building

	private Valley build(long vi, long vj) {
		long mi = Math.floorDiv(vi + 2_048, 4_096);
		long j = vi - mi * 4_096;
		long mj = Math.floorDiv(vj, 2);
		boolean north = Math.floorMod(vj, 2) == 0;
		TatraField.Massif m = field.cell(mi, mj);
		if (m == null) {
			return EMPTY;
		}
		double width = north ? m.wn() : m.ws();
		List<Tarn> tarns = new ArrayList<>(2);
		Tarn cirque = cirqueTarn(m, north, j, width, vi, vj);
		Tarn moraine = moraineTarn(m, north, j, width, vi, vj);
		if (cirque != null && moraine != null && moraine.un0() - moraine.bu() * moraine.ring() / width
				< cirque.un0() + (cirque.bu() * cirque.ring() + 2 * BELT) / width) {
			// Two basins of one valley never touch (their belts may): the moraine tarn holds the valley.
			cirque = null;
		}
		if (cirque != null && dry(m, north, j, width, cirque) && onGround(m, north, j, width, cirque)) {
			tarns.add(cirque);
		}
		if (moraine != null && dry(m, north, j, width, moraine) && onGround(m, north, j, width, moraine)) {
			tarns.add(moraine);
		}
		if (tarns.isEmpty()) {
			return EMPTY;
		}
		// The outlet brooks, from the lowest point of the ring down the steepest slope to a lower tarn or into the river
		// network (step TT7, TT2-o: drawn with new salts; TT2 drew with the salts 62 and 65).
		List<Tarn> plain = List.copyOf(tarns);
		for (int t = 0; t < tarns.size(); t++) {
			Tarn tn = tarns.get(t);
			boolean outlet = tn.morskie()
					|| field.draw(vi, vj, tn.moraine() ? 69 : 68) < (tn.moraine() ? OUTLET_MORAINE : OUTLET_CIRQUE);
			if (outlet) {
				// From the lowest point of the ring; where that brook does not reach the water (it ends in a pool above a
				// step or in a hollow), from the next lowest points (at most OUTLET_TRIES), the first that joins; else the
				// brook of the lowest point stays (or none).
				Brook b = null;
				double oq = Double.NaN;
				double oa = Double.NaN;
				for (double theta : lowestRingPoints(m, north, j, width, tn)) {
					double ex = tn.aq() * Math.cos(theta);
					double ea = tn.bu() * Math.sin(theta);
					double len = Math.hypot(ex, ea);
					Brook tried = brook(m, north, j, width, tn, ex / len, ea / len, len, plain);
					if (tried != null && (b == null || tried.joined())) {
						b = tried;
						oq = ex / len;
						oa = ea / len;
					}
					if (b != null && b.joined()) {
						break;
					}
				}
				tarns.set(t, new Tarn(tn.id(), m, north, j, tn.moraine(), tn.morskie(), tn.un0(), tn.aq(), tn.bu(), tn.level(),
						tn.depth(), b, tn.x(), tn.z(), oq, oa));
			}
		}
		double unMin = Double.POSITIVE_INFINITY;
		double unMax = Double.NEGATIVE_INFINITY;
		double pad = (BROOK_HALF + BROOK_BANK + BROOK_STEP) / width;
		for (Tarn t : tarns) {
			double reach = (t.bu() + BELT + RING) / width;
			unMin = Math.min(unMin, t.un0() - reach);
			unMax = Math.max(unMax, t.un0() + reach);
			if (t.brook() != null) {
				for (double un : t.brook().un()) {
					unMin = Math.min(unMin, un - pad);
					unMax = Math.max(unMax, un + pad);
				}
			}
		}
		return new Valley(List.copyOf(tarns), unMin, unMax);
	}

	/** The column of the massif at a valley-local point, or null when it lies off the valley (or the massif proper). */
	private TatraField.Column columnAt(TatraField.Massif m, boolean north, long j, double[] p) {
		TatraField.Column c = field.column(p[0], p[1]);
		if (c == null || c.massif != m || c.north != north || c.valley != j || Double.isNaN(c.un) || c.weight < 0.95) {
			return null;
		}
		return c;
	}

	/** The tarn in the bowl of the cirque of the valley, or null. */
	private Tarn cirqueTarn(TatraField.Massif m, boolean north, long j, double width, long vi, long vj) {
		if (!field.valleyHasCirque(m, north, j)
				|| field.draw(vi, vj, 61) >= Noise.lerp(field.valleyHigh(m, north, j), 0.5, 0.9)) {
			return null;
		}
		double unC = field.valleyCirque(m, north, j);
		double[] center = field.world(m, north, j, 0, unC);
		TatraField.Column c = columnAt(m, north, j, center);
		if (c == null) {
			return null;
		}
		// The basin inside the bowl of TT1 (half width 0.17 d across, 0.075 of the side along).
		double aq = 0.5 * 0.17 * m.spacing();
		double bu = 0.5 * 0.075 * width;
		// The level: the ground a little down the axis from the center (the floor of the cirque is cut below it, the lip
		// down the valley is raised to it by the ring), never more than a few meters above the lowest point of the rim.
		double[] lip = field.world(m, north, j, 0, unC + 0.6 * bu / width);
		int level = (int) Math.floor(Math.min(model.landElevation(lip[0], lip[1]), rim(m, north, j, 0, unC, aq, bu, width) + 6));
		double cut = model.landElevation(center[0], center[1]) - level;
		if (cut > MAX_CUT) {
			return null;
		}
		double depth = (2.5 + 1.5 * field.draw(vi, vj, 66)) * field.formScale() / field.blocksPerMeter(level);
		return new Tarn(Noise.mix(vi * 31 + vj * 17 + 1), m, north, j, false, false, unC, aq, bu, level, depth, center[0],
				center[1]);
	}

	/** The tarn behind the frontal moraine of the valley, or null. */
	private Tarn moraineTarn(TatraField.Massif m, boolean north, long j, double width, long vi, long vj) {
		TatraField.Moraine mo = field.moraine(m, north, j);
		if (mo == null || !mo.morskie() && field.draw(vi, vj, 63) >= Noise.lerp(field.valleyHigh(m, north, j), 0.3, 0.65)) {
			return null;
		}
		double[] crest = field.world(m, north, j, 0, mo.unM());
		if (columnAt(m, north, j, crest) == null) {
			return null;
		}
		int level = (int) Math.floor(model.landElevation(crest[0], crest[1])) - (mo.morskie() ? 3 : 2 + (int) (3 * field.draw(vi, vj, 64)));
		// Up the valley from the inner foot of the moraine until the ground reaches the level (the far shore).
		double near = mo.unM() - 0.8 * mo.halfWidth() / width;
		double max = (mo.morskie() ? 1_200 : 400) * field.k();
		double floor = field.valleyCirque(m, north, j) + 0.09;
		double far = near;
		for (double s = 3; s <= max; s += 3) {
			double un = near - s / width;
			if (un < floor) {
				break;
			}
			far = un;
			double[] p = field.world(m, north, j, 0, un);
			if (model.landElevation(p[0], p[1]) >= level + 1) {
				break;
			}
		}
		// At least a short basin: behind a low moraine it is cut into the floor.
		far = Math.max(floor, Math.min(far, near - 120 * field.k() / width));
		double bu = (mo.unM() - far) * width / 2;
		if (bu < 40 * field.k()) {
			return null;
		}
		double un0 = (mo.unM() + far) / 2;
		double[] center = field.world(m, north, j, 0, un0);
		TatraField.Column c = columnAt(m, north, j, center);
		if (c == null) {
			return null;
		}
		double aq = (mo.morskie() ? 1.6 : 1.1) * c.floorHalf;
		// The ring never raises the ground more than a few meters: a level far above the shore drops to it.
		level = Math.min(level, (int) Math.floor(rim(m, north, j, 0, un0, aq, bu, width)) + 2);
		double cut = model.landElevation(center[0], center[1]) - level;
		if (cut > MAX_CUT) {
			return null;
		}
		double depth = (mo.morskie() ? 6 : 2 + 1.5 * field.draw(vi, vj, 67)) * field.formScale() / field.blocksPerMeter(level);
		return new Tarn(Noise.mix(vi * 31 + vj * 17 + 2), m, north, j, true, mo.morskie(), un0, aq, bu, level, depth,
				center[0], center[1]);
	}

	/**
	 * Whether the basin with its ring lies off the channels of the river network (a tarn never takes a river: the river
	 * network of TT1 keeps its courses, and a tarn on a river would leave water at two levels side by side).
	 */
	private boolean dry(TatraField.Massif m, boolean north, long j, double width, Tarn t) {
		for (int a = -1; a < 16; a++) {
			double rr = a < 0 ? 0 : t.ring();
			double th = 2 * Math.PI * Math.max(a, 0) / 16;
			double[] p = field.world(m, north, j, t.aq() * rr * Math.cos(th), t.un0() + t.bu() * rr * Math.sin(th) / width);
			RiverNetwork.RiverHit r = model.riverProbe(p[0], p[1]);
			if (r.order() > 0 && (r.inChannel() || r.channelDist() < r.channelWidth() + 2 * RING)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The ground of a column before the tarns: the land with the valleys and channels of the river network, as
	 * {@link LandscapeModel#sample} has it (in the Tatras no sink lakes, kettles or tunnel lakes change it).
	 */
	double ground(double x, double z) {
		return ground(model.riverProbe(x, z));
	}

	/** The ground of a river probe: the terrain, raised to the bank level near a channel, the bed in it. */
	private static double ground(RiverNetwork.RiverHit r) {
		double g = r.terrain();
		if (r.order() > 0 && !r.inChannel() && g < r.bankLevel()) {
			g = r.bankLevel();
		}
		if (r.order() > 0 && r.inChannel()) {
			g = Math.min(g, r.channelBottom());
		}
		return g;
	}

	/**
	 * Round 1 of the review of TT2: whether the basin lies on the ground, not over an incised river valley. On 24 rays
	 * the ground of the basin and of its ring lies at most {@value #DEEPER} blocks under the bed of its center, and the outer
	 * face of the ring rises at most {@value #RAISE} blocks over the ground of the belt (its raise fades out to the edge of
	 * the belt, see Hit#surface).
	 */
	private boolean onGround(TatraField.Massif m, boolean north, long j, double width, Tarn t) {
		double bpm = field.blocksPerMeter(t.level());
		double low = t.level() - t.depth() - DEEPER / bpm;
		double raise = RAISE / bpm;
		double side = Math.min(t.aq(), t.bu());
		if (ground(t.x(), t.z()) < low) {
			return false;
		}
		double[] rs = {0.25, 0.5, 0.75, 1, t.ring()};
		double[] es = {6, 14, 24, 34};
		for (int a = 0; a < 24; a++) {
			double th = 2 * Math.PI * a / 24;
			double cos = Math.cos(th);
			double sin = Math.sin(th);
			for (double rr : rs) {
				double[] p = field.world(m, north, j, t.aq() * rr * cos, t.un0() + t.bu() * rr * sin / width);
				if (ground(p[0], p[1]) < low) {
					return false;
				}
			}
			for (double e : es) {
				double rr = 1 + e / side;
				double[] p = field.world(m, north, j, t.aq() * rr * cos, t.un0() + t.bu() * rr * sin / width);
				if (t.level() + 0.5 - DAM * (e - RING) - ground(p[0], p[1]) > raise) {
					return false;
				}
			}
		}
		return true;
	}

	/** The lowest ground on the edge of the basin ellipse (24 points). */
	private double rim(TatraField.Massif m, boolean north, long j, double q0, double un0, double aq, double bu, double width) {
		double min = Double.POSITIVE_INFINITY;
		for (int a = 0; a < 24; a++) {
			double th = 2 * Math.PI * a / 24;
			double[] p = field.world(m, north, j, q0 + aq * Math.cos(th), un0 + bu * Math.sin(th) / width);
			min = Math.min(min, model.landElevation(p[0], p[1]));
		}
		return min;
	}

	/**
	 * Step TT7 (TT2-o): the ellipse angles of the lowest points of the outer edge of the ring of the tarn on its down-valley
	 * half (the ground of the columns, with the rivers, on every point of {@value #RING_POINTS} around), lowest first, at
	 * most {@value #OUTLET_TRIES} at least {@value #OUTLET_GAP} points apart: the outlet, where the water would spill, and
	 * the next places to try.
	 */
	private double[] lowestRingPoints(TatraField.Massif m, boolean north, long j, double width, Tarn t) {
		double rr = t.ring();
		// Only the down-valley half of the ring: up the valley the land rises to the headwall (a lower point there is a
		// hollow of the bowl or a river incision, never the way out).
		int half = RING_POINTS / 2;
		double[] g = new double[half];
		Integer[] order = new Integer[half - 1];
		for (int a = 1; a < half; a++) {
			double th = 2 * Math.PI * a / RING_POINTS;
			double[] p = field.world(m, north, j, t.aq() * rr * Math.cos(th), t.un0() + t.bu() * rr * Math.sin(th) / width);
			g[a] = ground(p[0], p[1]);
			order[a - 1] = a;
		}
		Arrays.sort(order, (x, y) -> x.equals(y) ? 0 : g[x] != g[y] ? Double.compare(g[x], g[y]) : Integer.compare(x, y));
		double[] out = new double[OUTLET_TRIES];
		int[] taken = new int[OUTLET_TRIES];
		int n = 0;
		for (int a : order) {
			boolean far = true;
			for (int i = 0; i < n; i++) {
				far &= Math.abs(a - taken[i]) >= OUTLET_GAP;
			}
			if (far) {
				taken[n] = a;
				out[n++] = 2 * Math.PI * a / RING_POINTS;
				if (n == OUTLET_TRIES) {
					break;
				}
			}
		}
		return Arrays.copyOf(out, n);
	}

	/** The tarn of the list whose ring holds the valley-local point, or null. */
	private static Tarn ringOf(List<Tarn> tarns, double q, double un, double width) {
		for (Tarn o : tarns) {
			if (o.r(q, un, width) < o.ring()) {
				return o;
			}
		}
		return null;
	}

	/**
	 * The outlet brook of the tarn (step TT7, TT2-o): from just outside its ring at the outlet (the unit direction
	 * {@code dq}, {@code da} in the valley-local frame, the basin edge {@code len} m from its center there) down the
	 * steepest slope of the land, to a lower tarn of the valley (whose ring it enters) or to the first channel of the river
	 * network it reaches (it heads for the channel from {@value #NEAR_RIVER} m), at most to the valley mouth or the edge of
	 * the valley frame. It never heads onto ground more than {@value #GORGE} m above its level (it would have to cut deeper);
	 * where every heading does, it ends (a dead end). A brook that meets no water, or meets it above the tarn, leaves the
	 * tarn without an outlet; one that would still cut more than {@value #GORGE} m into the ground (the levels fall at most
	 * {@value #MAX_DROP} m a step) ends in a closed pool above the step.
	 */
	private Brook brook(TatraField.Massif m, boolean north, long j, double width, Tarn t, double dq, double da, double len,
			List<Tarn> tarns) {
		double q = dq * len * t.ring() + 0.5 * dq;
		double along = t.un0() * width + da * len * t.ring() + 0.5 * da;
		double heading = Math.atan2(da, dq);
		double target = heading;
		double halfValley = 0.5 * m.spacing();
		int maxSteps = (int) Math.round(MAX_STEPS * field.formScale());
		// Each step: q, un, the level so far, the floor of the ground less a meter, the along coordinate (m).
		List<double[]> steps = new ArrayList<>();
		Map<Long, List<Integer>> visited = new HashMap<>();
		double selfReach = 2 * BROOK_HALF + BROOK_BANK;
		int last = t.level() - 1;
		boolean joined = false;
		boolean intoTarn = false;
		int floorLevel = ColumnSample.NO_WATER;
		double turn = Math.toRadians(TURN);
		for (int i = 0; i < maxSteps; i++) {
			double un = along / width;
			if (un > 0.97 || un < 0 || Math.abs(q) > halfValley) {
				break;
			}
			Tarn other = ringOf(tarns, q, un, width);
			if (other != null) {
				if (other != t && other.level() < t.level()) {
					joined = true;
					intoTarn = true;
					floorLevel = other.level();
				}
				break;
			}
			double[] p = field.world(m, north, j, q, un);
			TatraField.Column c = columnAt(m, north, j, p);
			// Never back near its own earlier course (water at two levels side by side).
			if (c == null || selfNear(visited, steps, q, along, i - SELF_GAP, selfReach)) {
				break;
			}
			RiverNetwork.RiverHit r = model.riverProbe(p[0], p[1]);
			double ground = r.terrain();
			if (r.order() > 0 && !r.inChannel() && ground < r.bankLevel()) {
				ground = r.bankLevel();
			}
			if (r.order() > 0 && r.inChannel()) {
				ground = Math.min(ground, r.channelBottom());
			}
			last = Math.min(last, (int) Math.floor(ground) - 1);
			steps.add(new double[] {q, un, last, Math.floor(ground) - 1, along});
			visited.computeIfAbsent(selfKey(q, along, selfReach), key -> new ArrayList<>()).add(i);
			if (r.order() > 0 && r.inChannel()) {
				joined = true;
				floorLevel = r.waterLevel();
				break;
			}
			if (r.order() > 0 && r.channelDist() < NEAR_RIVER) {
				// The river is near: head for its channel.
				double bestD = r.channelDist();
				for (double f : FAN) {
					double h = heading + Math.toRadians(f);
					double[] pq = field.world(m, north, j, q + LOOK * Math.cos(h), (along + LOOK * Math.sin(h)) / width);
					RiverNetwork.RiverHit hh = model.riverProbe(pq[0], pq[1]);
					double d = hh.order() <= 0 ? Double.POSITIVE_INFINITY : hh.inChannel() ? -1 : hh.channelDist();
					// Never up a wall it could not cut through (a river in a gorge below a ridge).
					if (d < bestD && ground(hh) <= last + GORGE) {
						bestD = d;
						target = h;
					}
				}
			} else if (i % LOOK_EVERY == 0) {
				// The steepest descent: the lowest ground (with the river valleys, so it finds them) a look ahead among the
				// headings of the fan, never towards its own tarn
				// (the land of a cirque bowl falls back to the tarn beyond its ring), never into a ring and never out of the
				// valley frame; in a hollow the least rise (over a sill the level cuts through, at most GORGE meters).
				double lowest = Double.POSITIVE_INFINITY;
				double own = t.r(q, un, width);
				target = heading;
				for (double f : FAN) {
					double h = heading + Math.toRadians(f);
					double lq = q + LOOK * Math.cos(h);
					double lun = (along + LOOK * Math.sin(h)) / width;
					if (Math.abs(lq) > halfValley || t.r(lq, lun, width) < own || ringOf(tarns, lq, lun, width) != null) {
						continue;
					}
					double[] pq = field.world(m, north, j, lq, lun);
					double g = ground(pq[0], pq[1]);
					if (g < lowest && g <= last + GORGE) {
						lowest = g;
						target = h;
					}
				}
				if (lowest == Double.POSITIVE_INFINITY) {
					// A dead end: every way on rises more than it could cut through.
					break;
				}
			}
			double diff = Math.atan2(Math.sin(target - heading), Math.cos(target - heading));
			heading += Math.clamp(diff, -turn, turn);
			q += BROOK_STEP * Math.cos(heading);
			along += BROOK_STEP * Math.sin(heading);
		}
		// A brook that meets no water down the valley, or meets it above the tarn, leaves the tarn without an outlet.
		if (steps.isEmpty() || !joined || floorLevel >= t.level()) {
			return null;
		}
		int n = steps.size();
		double[] qs = new double[n];
		double[] uns = new double[n];
		int[] levels = new int[n];
		for (int i = 0; i < n; i++) {
			qs[i] = steps.get(i)[0];
			uns[i] = steps.get(i)[1];
			// Never below the water the brook enters (a river or the next tarn).
			levels[i] = Math.max((int) steps.get(i)[2], floorLevel);
		}
		// At most MAX_DROP meters between two steps, down to at most MAX_DROP above the water it enters: from the mouth up
		// the brook is cut lower where it would fall faster (a short gorge), never raised above the ground.
		levels[n - 1] = Math.min(levels[n - 1], floorLevel + MAX_DROP);
		for (int i = n - 2; i >= 0; i--) {
			levels[i] = Math.min(levels[i], levels[i + 1] + MAX_DROP);
		}
		// Where that would cut the brook deeper than GORGE meters into the ground (a steep step of the valley, the wall of
		// an incised river, high ground at the outlet), its water ends in a closed pool above the step: the rest of its way
		// it runs under the scree (the tarn still drains to that water). Too short a brook leaves the tarn without one.
		int end = n;
		for (int i = 0; i < n; i++) {
			if (steps.get(i)[3] - levels[i] > GORGE) {
				end = i;
				break;
			}
		}
		if (end < n) {
			if (end < MIN_STEPS) {
				return null;
			}
			// The head keeps its levels (monotone, falling at most MAX_DROP per step, at most GORGE under the ground).
			return new Brook(Arrays.copyOf(qs, end), Arrays.copyOf(uns, end), Arrays.copyOf(levels, end), false, false, width);
		}
		return new Brook(qs, uns, levels, joined, intoTarn, width);
	}

	private static long selfKey(double q, double along, double cell) {
		return Noise.key((long) Math.floor(q / cell), (long) Math.floor(along / cell), 1);
	}

	/** Whether a step before {@code before} of the course lies within {@code reach} m of the valley-local point. */
	private static boolean selfNear(Map<Long, List<Integer>> visited, List<double[]> steps, double q, double along, int before,
			double reach) {
		if (before <= 0) {
			return false;
		}
		long cq = (long) Math.floor(q / reach);
		long ca = (long) Math.floor(along / reach);
		for (long a = -1; a <= 1; a++) {
			for (long b = -1; b <= 1; b++) {
				List<Integer> l = visited.get(Noise.key(cq + a, ca + b, 1));
				if (l == null) {
					continue;
				}
				for (int i : l) {
					double[] st = steps.get(i);
					if (i < before && Math.hypot(q - st[0], along - st[4]) < reach) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
