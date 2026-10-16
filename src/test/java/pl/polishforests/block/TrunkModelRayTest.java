package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * Ray casts through the generated thin-trunk models (variant D, {@code template_trunk_r1…r7} written by
 * {@code TrunkTemplates}): the seams between the arm, the cap, the inner wall and the reach of a child must look like the
 * ideal trunk, a plain prism of the radius (V2 review, round 2: the top face of the arm showed through the ε slit at the
 * center seam as a light hairline lit like a top face).
 *
 * <p>Each scene is a lone tip (with the stub of its reach) or puts two blocks together (a straight column r → r, a joint
 * r + 1 → r, an elbow r → r and r → r − 1, the second block being a tip) and shoots parallel rays at points of the
 * surface of the ideal trunk from many directions, most of them aimed finely around the edges of the model's faces. For each ray the first front face of the model
 * (back faces are culled, as in the game) is compared with the entry into the ideal solid:
 * <ul>
 * <li>inside a flat part of the ideal surface (farther than {@link #CREASE} px from its edges) the ray must hit the model
 * (no see-through slit), at most {@link #MAX_DEPTH} px from the ideal entry (no deep look into the hollow inside), on a
 * face of the same direction as the ideal surface, so with the same directional shading (a top face lit at 1.0 in a
 * side lit at 0.8 or 0.6 is the hairline of the review);
 * <li>anywhere, the end of a reach (rings) shows only where the ideal surface is an exposed end (a stub);
 * <li>across every convex edge of the ideal trunk (the top of a tip, the ledge of a joint r + 1 → r, the outer side of an
 * elbow, the end of a stub) a dense scan of parallel rays sees only faces of the two directions of the edge and changes
 * the face once (V2b: the flat faces of the cap of round 2 reached 0.09 px past the faces across them, and a scan saw
 * side, top, side, top: the dotted light line along the edges of every tip).
 * </ul>
 * A separate check needs no rays: faces of the model in one plane and facing the same way must meet only at their
 * corners and must not overlap. A corner of one face inside the edge of the other (a T-junction) leaves single pixels of
 * the rasterized seam uncovered, which the analytic rays cannot see (V2b review: sky pixels along the ledge under the
 * caps, where the cap's sides met the wider inner walls).
 * Near the edges of the ideal surface the model may differ by its insets (the reach lies ε = 0.01 px, the cap 0.1 px
 * inside the radius), and rays that get less than {@link #MIN_DEPTH} px under the ideal surface only graze its silhouette.
 * The models are read from {@code src/main/generated}, so {@code runDatagen} must run before this test after a change
 * of the templates ({@code checkDatagen} fails otherwise).
 */
class TrunkModelRayTest {
	private static final Path MODELS = Path.of("src/main/generated/assets/polishforests/models/block");
	/** Largest allowed distance between the model hit and the ideal entry, px. */
	private static final double MAX_DEPTH = 1.5;
	/**
	 * Points of the ideal surface closer than this to its edges may show the neighboring face: the inset parts seen at a
	 * slant (a 0.1 px recess around the base of a thinner child reads as a contact shadow), px.
	 */
	private static final double CREASE = 0.4;
	/** Angles of the rays from the normal of the aimed face, degrees (0.5 and 2: almost level views along a branch). */
	private static final double[] ALPHA = {0.5, 2, 5, 15, 30, 45, 60, 75};
	/** Rays that never get deeper than this under the ideal surface graze its silhouette and are not checked, px. */
	private static final double MIN_DEPTH = 0.25;
	/** Number of azimuths around the normal. */
	private static final int BETA_STEPS = 8;
	/** Rays that enter the ideal trunk at more than this angle from the normal of the face entered are not checked, deg. */
	private static final double MAX_ENTRY_ANGLE = 80;
	/**
	 * Largest share of the rays of an elbow of equal radii that may show the sliver of the end of the child's reach in the
	 * step over the parent's cap, summed over the top and both sides (see {@link #check}); after the V2b review
	 * 0.005–0.018% (the bark of the end lies 0.1 px behind the opening, so steep rays see the cap's top through it). With
	 * the bark in the plane of the end (V2b) 0.04–0.10%, and with the rings there 0.15–0.19% (round 2 of the V2 review).
	 */
	private static final double ELBOW_SLIVER_SHARE = 0.0003;
	/** Largest share of the rays of an elbow of equal radii that may show the ε slit along the joint, both sides (0.005%). */
	private static final double ELBOW_SLIT_SHARE = 0.0001;
	/** Half-width of a scan across a convex edge of the ideal trunk, on each of its two faces, px. */
	private static final double EDGE_SCAN = 0.6;
	/** Step of the aim points of a scan across a convex edge, px. */
	private static final double EDGE_STEP = 0.002;
	/** Scans keep this far from the ends of a convex edge and from the breaks of the model along it, px. */
	private static final double EDGE_MARGIN = 0.4;
	/** Positions of the scans along a convex edge, as fractions of its length. */
	private static final double[] EDGE_AT = {0.1, 0.25, 0.4, 0.5, 0.6, 0.75, 0.9};
	/** Directions of the scans: the angle from the normal of the first face towards the second one, degrees. */
	private static final double[] EDGE_THETA = {10, 30, 45, 60, 80};
	/** Directions of the scans: the tilt along the edge, degrees. */
	private static final double[] EDGE_TILT = {-30, 0, 30};

	/** An axis-aligned rectangle facing {@code axis}/{@code sign}, at {@code plane}, with the extents on the other axes. */
	private record Quad(int axis, int sign, double plane, double[] min, double[] max, boolean end, String label) {
		double hit(double[] o, double[] d) {
			double dn = d[axis] * sign;
			if (dn >= 0) {
				return Double.NaN;                 // a back face (culled) or parallel
			}
			double t = (plane - o[axis]) / d[axis];
			if (t <= 0) {
				return Double.NaN;
			}
			for (int k = 0; k < 3; k++) {
				if (k != axis) {
					double p = o[k] + t * d[k];
					if (p < min[k] || p > max[k]) {
						return Double.NaN;
					}
				}
			}
			return t;
		}
	}

	/** A box of the ideal trunk; {@code endAxis}/{@code endSign} is its end face (the end of a reach) or axis −1. */
	private record Box(double[] min, double[] max, int endAxis, int endSign) {
		boolean inside(double[] p) {
			for (int k = 0; k < 3; k++) {
				if (p[k] <= min[k] || p[k] >= max[k]) {
					return false;
				}
			}
			return true;
		}
	}

	/** A trunk block of a scene: its position (blocks), radius and parent. */
	private record Block(int x, int y, int z, int r, Direction parent) {
	}

	private static final Map<Integer, JsonObject> TEMPLATES = new LinkedHashMap<>();

	@Test
	void seamsLookLikeTheIdealTrunk() throws IOException {
		long start = System.nanoTime();
		Map<String, List<Block>> scenes = scenes();
		List<String> failures = new ArrayList<>();
		List<String> report = new ArrayList<>();
		scenes.entrySet().parallelStream().map(e -> check(e.getKey(), e.getValue())).toList().forEach(res -> {
			report.add(res.summary());
			failures.addAll(res.failures());
		});
		report.forEach(System.out::println);
		System.out.printf(Locale.ROOT, "TrunkModelRayTest: %.1f s%n", (System.nanoTime() - start) / 1e9);
		assertTrue(failures.isEmpty(), () -> "the model differs from the ideal trunk:\n" + String.join("\n", failures));
	}

	/**
	 * Faces of the model in one plane and facing the same way (within one block and between the blocks of every scene)
	 * meet only at their corners: no corner of one lies inside an edge of the other, and they do not overlap.
	 */
	@Test
	void coplanarFacesMeetAtCorners() throws IOException {
		List<String> failures = new ArrayList<>();
		for (Map.Entry<String, List<Block>> scene : scenes().entrySet()) {
			List<Quad> model = new ArrayList<>();
			for (Block b : scene.getValue()) {
				model.addAll(modelQuads(b));
			}
			for (Quad p : model) {
				for (Quad q : model) {
					if (p == q || p.axis() != q.axis() || p.sign() != q.sign() || Math.abs(p.plane() - q.plane()) > TOUCH) {
						continue;
					}
					if (overlapArea(p, q) > TOUCH) {
						failures.add(scene.getKey() + ": " + p.label() + " overlaps " + q.label());
						continue;
					}
					for (double[] corner : corners(p)) {
						if (insideEdge(corner, q)) {
							failures.add(String.format(Locale.ROOT, "%s: a corner of %s at (%.2f, %.2f, %.2f) lies inside an edge of %s",
									scene.getKey(), p.label(), corner[0], corner[1], corner[2], q.label()));
						}
					}
				}
			}
		}
		assertTrue(failures.isEmpty(), () -> "faces of the model meet in T-junctions:\n" + String.join("\n", failures));
	}

	/** Tolerance of the checks of coplanar faces, px (the templates have two decimals). */
	private static final double TOUCH = 1e-6;

	private static double overlapArea(Quad p, Quad q) {
		double area = 1;
		for (int k = 0; k < 3; k++) {
			if (k != p.axis()) {
				area *= Math.max(0, Math.min(p.max()[k], q.max()[k]) - Math.max(p.min()[k], q.min()[k]));
			}
		}
		return area;
	}

	private static List<double[]> corners(Quad q) {
		int t1 = (q.axis() + 1) % 3;
		int t2 = (q.axis() + 2) % 3;
		List<double[]> out = new ArrayList<>();
		for (double u : new double[] {q.min()[t1], q.max()[t1]}) {
			for (double v : new double[] {q.min()[t2], q.max()[t2]}) {
				double[] c = new double[3];
				c[q.axis()] = q.plane();
				c[t1] = u;
				c[t2] = v;
				out.add(c);
			}
		}
		return out;
	}

	/** Whether a point lies on an edge of a quad (in its plane) but not at one of the quad's corners. */
	private static boolean insideEdge(double[] c, Quad q) {
		int t1 = (q.axis() + 1) % 3;
		int t2 = (q.axis() + 2) % 3;
		boolean onU = Math.abs(c[t1] - q.min()[t1]) < TOUCH || Math.abs(c[t1] - q.max()[t1]) < TOUCH;
		boolean onV = Math.abs(c[t2] - q.min()[t2]) < TOUCH || Math.abs(c[t2] - q.max()[t2]) < TOUCH;
		boolean inU = c[t1] > q.min()[t1] + TOUCH && c[t1] < q.max()[t1] - TOUCH;
		boolean inV = c[t2] > q.min()[t2] + TOUCH && c[t2] < q.max()[t2] - TOUCH;
		return onU && inV || onV && inU;
	}

	/** The scenes: a lone tip, a column r → r, a joint r + 1 → r and elbows r → r and r + 1 → r of every radius. */
	private static Map<String, List<Block>> scenes() throws IOException {
		synchronized (TEMPLATES) {
			if (TEMPLATES.isEmpty()) {
				for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
					TEMPLATES.put(r, JsonParser.parseString(Files.readString(MODELS.resolve("template_trunk_r" + r + ".json"))).getAsJsonObject());
				}
			}
		}
		Map<String, List<Block>> scenes = new LinkedHashMap<>();
		for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
			scenes.put("tip r" + r, List.of(new Block(0, 0, 0, r, Direction.DOWN)));
			scenes.put("column r" + r, List.of(new Block(0, 0, 0, r, Direction.DOWN), new Block(0, 1, 0, r, Direction.DOWN)));
			scenes.put("elbow r" + r + " -> r" + r, List.of(new Block(0, 0, 0, r, Direction.DOWN), new Block(0, 0, -1, r, Direction.SOUTH)));
			if (r < TrunkBlock.MAX_RADIUS) {
				scenes.put("joint r" + (r + 1) + " -> r" + r,
						List.of(new Block(0, 0, 0, r + 1, Direction.DOWN), new Block(0, 1, 0, r, Direction.DOWN)));
				scenes.put("elbow r" + (r + 1) + " -> r" + r,
						List.of(new Block(0, 0, 0, r + 1, Direction.DOWN), new Block(0, 0, -1, r, Direction.SOUTH)));
			}
		}
		return scenes;
	}

	/** The result of a scene: a summary line and the failures. */
	private record Result(String summary, List<String> failures) {
	}

	private static Result check(String scene, List<Block> blocks) {
		List<Quad> model = new ArrayList<>();
		List<Box> ideal = new ArrayList<>();
		for (Block b : blocks) {
			model.addAll(modelQuads(b));
			ideal.addAll(idealBoxes(b));
		}
		List<Quad> endFaces = exposedEnds(ideal);
		// Every coordinate where a face of the model begins or ends: the rays are aimed finely around these.
		List<List<Double>> breaks = new ArrayList<>();
		for (int k = 0; k < 3; k++) {
			List<Double> c = new ArrayList<>();
			for (Quad q : model) {
				c.add(k == q.axis() ? q.plane() : q.min()[k]);
				c.add(k == q.axis() ? q.plane() : q.max()[k]);
			}
			breaks.add(c.stream().distinct().sorted().toList());
		}
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, String> examples = new LinkedHashMap<>();
		int rays = 0;
		for (Box box : ideal) {
			for (int axis = 0; axis < 3; axis++) {
				for (int sign : new int[] {-1, 1}) {
					double plane = sign < 0 ? box.min()[axis] : box.max()[axis];
					int t1 = (axis + 1) % 3;
					int t2 = (axis + 2) % 3;
					for (double[] p2 : facePoints(box, axis, plane, t1, t2, breaks)) {
						double[] n = new double[3];
						n[axis] = sign;
						if (!onSurface(ideal, p2, n)) {
							continue;
						}
						for (double alpha : ALPHA) {
							for (int bi = 0; bi < BETA_STEPS; bi++) {
								double a = Math.toRadians(alpha);
								double be = 2 * Math.PI * (bi + 0.37) / BETA_STEPS;
								double[] d = new double[3];
								d[axis] = -sign * Math.cos(a);
								d[t1] = -Math.sin(a) * Math.cos(be);
								d[t2] = -Math.sin(a) * Math.sin(be);
								double[] o = {p2[0] - 200 * d[0], p2[1] - 200 * d[1], p2[2] - 200 * d[2]};
								rays++;
								String fail = ray(o, d, model, ideal, endFaces);
								if (fail != null) {
									counts.merge(fail, 1, Integer::sum);
									examples.putIfAbsent(fail, String.format(Locale.ROOT, "aim (%.3f, %.3f, %.3f) dir (%.3f, %.3f, %.3f)",
											p2[0], p2[1], p2[2], d[0], d[1], d[2]));
								}
							}
						}
					}
				}
			}
		}
		// An elbow of equal radii keeps two known faults, each below its share of the rays: the cap lies 0.1 px inside the
		// faces of the child's reach, so a 0.09 px sliver of the reach's end shows through the step on the top and the two
		// sides facing away from the child at a slant (its bark since V2b, so never the rings: rings fail as "off a stub"
		// before the direction is judged), and the reach lies ε inside the parent's arm, so the arm's face across the branch
		// shows in an ε slit along the joint. The share of each fault is summed over the faces it shows on. Everything else,
		// and every fault of the other scenes, fails the test.
		boolean elbow = blocks.size() > 1 && blocks.get(0).r() == blocks.get(1).r() && blocks.get(1).parent() != Direction.DOWN;
		List<String> failures = new ArrayList<>();
		int total = rays;
		int faults = counts.values().stream().mapToInt(Integer::intValue).sum();
		Map<String, Integer> known = new LinkedHashMap<>();
		counts.forEach((fail, n) -> {
			String fault = !elbow ? null
					: fail.startsWith("face south (") && fail.contains(" down #bark of block 0,0,-1)") ? "the sliver of the reach's end"
					: fail.startsWith("face north (element 0 north #bark of block 0,0,0)") ? "the ε slit" : null;
			if (fault == null) {
				failures.add(String.format(Locale.ROOT, "%s: %s in %d of %d rays, e.g. %s", scene, fail, n, total, examples.get(fail)));
			} else {
				known.merge(fault, n, Integer::sum);
			}
		});
		known.forEach((fault, n) -> {
			double share = fault.equals("the ε slit") ? ELBOW_SLIT_SHARE : ELBOW_SLIVER_SHARE;
			if (n > share * total) {
				failures.add(String.format(Locale.ROOT, "%s: known fault %s in %d of %d rays (more than %.2f%%)", scene, fault, n, total,
						100 * share));
			}
		});
		int[] edges = edgeScans(scene, model, ideal, breaks, failures);
		StringBuilder shares = new StringBuilder();
		counts.forEach((fail, n) -> shares.append(String.format(Locale.ROOT, "%n      %.4f%% %s", 100.0 * n / total, fail)));
		return new Result(String.format(Locale.ROOT, "%-16s %9d rays, %6d faults (%.3f%%); %5d scans across convex edges, %4d faulty%s",
				scene, total, faults, 100.0 * faults / total, edges[0], edges[1], shares), failures);
	}

	/**
	 * Scans across the convex edges of the ideal trunk: along each edge of each ideal box where both faces are ideal
	 * surface and the solid is convex (within {@link #EDGE_MARGIN} along the edge and {@link #EDGE_SCAN} across it), away
	 * from the breaks of the model along the edge, parallel rays aimed at points of the two faces every
	 * {@link #EDGE_STEP} px across the edge, from several directions between the two normals and tilted along the edge.
	 * Rays that another part of the ideal trunk hides, or that go past the inset faces at a slant and hit the model more
	 * than {@link #EDGE_SCAN} + 0.1 px from the edge (a child rising from a ledge), are left out. The faces hit, in the
	 * order of the aim points, must be of the two directions of the edge and change once. Returns
	 * the number of scans and of faulty scans; every faulty scan fails the test.
	 */
	private static int[] edgeScans(String scene, List<Quad> model, List<Box> ideal, List<List<Double>> breaks, List<String> failures) {
		int scans = 0;
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, String> examples = new LinkedHashMap<>();
		for (Box box : ideal) {
			for (int k = 0; k < 3; k++) {
				int i = (k + 1) % 3;
				int j = (k + 2) % 3;
				for (int si : new int[] {-1, 1}) {
					for (int sj : new int[] {-1, 1}) {
						double[] n1 = new double[3];
						double[] n2 = new double[3];
						n1[i] = si;
						n2[j] = sj;
						double lo = box.min()[k];
						double hi = box.max()[k];
						for (double f : EDGE_AT) {
							double[] e = new double[3];
							e[i] = si < 0 ? box.min()[i] : box.max()[i];
							e[j] = sj < 0 ? box.min()[j] : box.max()[j];
							e[k] = lo + f * (hi - lo);
							if (!convexEdge(ideal, e, k, n1, n2) || nearBreak(e[k], breaks.get(k))) {
								continue;
							}
							for (double theta : EDGE_THETA) {
								for (double tilt : EDGE_TILT) {
									scans++;
									String fail = scan(model, ideal, e, k, n1, n2, Math.toRadians(theta), Math.toRadians(tilt));
									if (fail != null) {
										String key = "convex edge " + name(i, si) + "/" + name(j, sj) + ": " + fail;
										counts.merge(key, 1, Integer::sum);
										examples.putIfAbsent(key, String.format(Locale.ROOT, "at (%.3f, %.3f, %.3f), %.0f deg from %s, tilt %.0f deg",
												e[0], e[1], e[2], theta, name(i, si), tilt));
									}
								}
							}
						}
					}
				}
			}
		}
		int faulty = counts.values().stream().mapToInt(Integer::intValue).sum();
		int total = scans;
		counts.forEach((fail, n) -> failures.add(String.format(Locale.ROOT, "%s: %s in %d of %d scans, e.g. %s", scene, fail, n, total,
				examples.get(fail))));
		return new int[] {scans, faulty};
	}

	/** Whether the ideal surface around a point of an edge (axis k, outer normals n1 and n2) is a plain convex edge. */
	private static boolean convexEdge(List<Box> ideal, double[] e, int k, double[] n1, double[] n2) {
		for (double q : new double[] {-EDGE_MARGIN, 0, EDGE_MARGIN}) {
			double[] p = e.clone();
			p[k] += q;
			double[] out = p.clone();
			double[] in = p.clone();
			for (int m = 0; m < 3; m++) {
				out[m] += 1e-3 * (n1[m] + n2[m]);
				in[m] -= 1e-3 * (n1[m] + n2[m]);
			}
			if (inside(ideal, out) || !inside(ideal, in)) {
				return false;
			}
			for (double w = 0.01; w <= EDGE_SCAN + 0.05; w += 0.05) {
				double[] a = p.clone();
				double[] b = p.clone();
				for (int m = 0; m < 3; m++) {
					a[m] -= w * n2[m];
					b[m] -= w * n1[m];
				}
				if (!onSurface(ideal, a, n1) || !onSurface(ideal, b, n2)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean nearBreak(double v, List<Double> breaks) {
		for (double br : breaks) {
			if (Math.abs(v - br) < EDGE_MARGIN) {
				return true;
			}
		}
		return false;
	}

	/**
	 * One scan across a convex edge: rays of a direction between −n1 (theta 0) and −n2 (theta 90°), tilted along the
	 * edge, aimed at the first face (normal n1) from EDGE_SCAN px off the edge up to it, then at the second face; the
	 * failure (the faces seen, in order) or null.
	 */
	private static String scan(List<Quad> model, List<Box> ideal, double[] e, int k, double[] n1, double[] n2, double theta, double tilt) {
		double[] d = new double[3];
		for (int m = 0; m < 3; m++) {
			d[m] = -Math.cos(tilt) * (Math.cos(theta) * n1[m] + Math.sin(theta) * n2[m]);
		}
		d[k] += Math.sin(tilt);
		List<String> runs = new ArrayList<>();
		boolean wrong = false;
		int steps = (int) Math.round(EDGE_SCAN / EDGE_STEP);
		for (int s = -steps; s <= steps; s++) {
			double w = Math.abs(s) * EDGE_STEP + 1e-4;
			double[] p = e.clone();
			for (int m = 0; m < 3; m++) {
				p[m] -= s < 0 ? w * n2[m] : w * n1[m];
			}
			double[] o = {p[0] - 200 * d[0], p[1] - 200 * d[1], p[2] - 200 * d[2]};
			if (Math.abs(entry(ideal, o, d) - 200) > 1e-3) {
				continue;                              // another part of the ideal trunk hides the aim point
			}
			Quad hit = null;
			double th = Double.POSITIVE_INFINITY;
			for (Quad q : model) {
				double t = q.hit(o, d);
				if (t < th) {
					th = t;
					hit = q;
				}
			}
			if (hit == null) {
				continue;
			}
			double[] h = at(o, d, th);
			int i = (k + 1) % 3;
			int j = (k + 2) % 3;
			if (Math.hypot(h[i] - e[i], h[j] - e[j]) > EDGE_SCAN + 0.1) {
				continue;                              // past the inset faces at a slant: another part of the trunk
			}
			String face = name(hit.axis(), hit.sign());
			wrong |= hit.sign() != (int) (n1[hit.axis()] + n2[hit.axis()]);
			if (runs.isEmpty() || !runs.getLast().equals(face)) {
				runs.add(face);
			}
		}
		return wrong || runs.size() > 2 ? "faces " + String.join(", ", runs) : null;
	}

	/** Points on a face of an ideal box: a few lines across and along it, sampled finely near the breaks of the model. */
	private static List<double[]> facePoints(Box box, int axis, double plane, int t1, int t2, List<List<Double>> breaks) {
		List<double[]> out = new ArrayList<>();
		for (int[] tt : new int[][] {{t1, t2}, {t2, t1}}) {
			int across = tt[0];
			int along = tt[1];
			double lo = box.min()[across];
			double hi = box.max()[across];
			double[] lines = {lo + 0.031, lo + 0.23 * (hi - lo), lo + 0.5 * (hi - lo) + 0.013, lo + 0.81 * (hi - lo), hi - 0.043};
			for (double u : lines) {
				for (double v : samples(box.min()[along], box.max()[along], breaks.get(along))) {
					double[] p = new double[3];
					p[axis] = plane;
					p[across] = u;
					p[along] = v;
					out.add(p);
				}
			}
		}
		return out;
	}

	/** Coordinates in (lo, hi): every 0.25 px, and every 0.005 px within 0.2 px of a break. */
	private static List<Double> samples(double lo, double hi, List<Double> breaks) {
		List<Double> out = new ArrayList<>();
		for (double v = lo + 0.0071; v < hi; v += 0.25) {
			out.add(v);
		}
		for (double br : breaks) {
			for (double v = br - 0.2 + 0.0013; v < br + 0.2; v += 0.005) {
				if (v > lo && v < hi) {
					out.add(v);
				}
			}
		}
		return out;
	}

	/** The failed check of one ray, or null. */
	private static String ray(double[] o, double[] d, List<Quad> model, List<Box> ideal, List<Quad> endFaces) {
		// Entry into the ideal solid (the union of its boxes): the first slab entry of any box; the chord is the first run
		// of overlapping box intervals.
		double te = Double.POSITIVE_INFINITY;
		int eAxis = -1;
		int eSign = 0;
		List<double[]> spans = new ArrayList<>();
		for (Box b : ideal) {
			double t0 = Double.NEGATIVE_INFINITY;
			double t1 = Double.POSITIVE_INFINITY;
			int ax = -1;
			int sg = 0;
			for (int k = 0; k < 3; k++) {
				if (d[k] == 0) {
					if (o[k] <= b.min()[k] || o[k] >= b.max()[k]) {
						t0 = Double.POSITIVE_INFINITY;
					}
					continue;
				}
				double a = (b.min()[k] - o[k]) / d[k];
				double c = (b.max()[k] - o[k]) / d[k];
				double near = Math.min(a, c);
				double far = Math.max(a, c);
				if (near > t0) {
					t0 = near;
					ax = k;
					sg = d[k] > 0 ? -1 : 1;
				}
				t1 = Math.min(t1, far);
			}
			if (t0 < t1 && t0 > 0) {
				spans.add(new double[] {t0, t1});
				if (t0 < te) {
					te = t0;
					eAxis = ax;
					eSign = sg;
				}
			}
		}
		if (eAxis < 0) {
			return null;                               // passes the ideal trunk
		}
		spans.sort((p, q) -> Double.compare(p[0], q[0]));
		double chordEnd = spans.getFirst()[1];
		for (double[] sp : spans) {
			if (sp[0] <= chordEnd + 1e-9) {
				chordEnd = Math.max(chordEnd, sp[1]);
			}
		}
		if (-d[eAxis] * eSign < Math.cos(Math.toRadians(MAX_ENTRY_ANGLE))) {
			return null;                               // enters at a grazing angle
		}
		String fail = judge(o, d, te, eAxis, eSign, model, ideal, endFaces);
		if (fail != null && depth(ideal, o, d, te, chordEnd) < MIN_DEPTH) {
			return null;                               // grazes the silhouette
		}
		return fail;
	}

	private static String judge(double[] o, double[] d, double te, int eAxis, int eSign, List<Quad> model, List<Box> ideal,
			List<Quad> endFaces) {
		double[] pe = at(o, d, te);
		Quad hit = null;
		double th = Double.POSITIVE_INFINITY;
		for (Quad q : model) {
			double t = q.hit(o, d);
			if (t < th) {
				th = t;
				hit = q;
			}
		}
		double[] n = new double[3];
		n[eAxis] = eSign;
		boolean flat = flat(ideal, pe, eAxis, n);
		if (hit == null) {
			return flat ? "see-through" : null;
		}
		double[] ph = at(o, d, th);
		if (flat && dist(ph, pe) > MAX_DEPTH) {
			return "deep hit (" + hit.label() + ")";
		}
		// Rings are judged before the direction, so that rings on a flat face of another direction never pass as the known
		// fault of an elbow (the bark of the end in the step).
		if (hit.end()) {
			boolean nearEnd = false;
			for (Quad e : endFaces) {
				nearEnd |= distToQuad(pe, e) <= 0.15;
			}
			if (!nearEnd) {
				return "rings (" + hit.label() + ") off a stub";
			}
		}
		if (flat && (hit.axis() != eAxis || hit.sign() != eSign)) {
			return "face " + name(hit.axis(), hit.sign()) + " (" + hit.label() + ") on an ideal " + name(eAxis, eSign) + " face";
		}
		return null;
	}

	/** The distance along a ray (unit direction d) to its entry into the ideal trunk, or infinity. */
	private static double entry(List<Box> ideal, double[] o, double[] d) {
		double best = Double.POSITIVE_INFINITY;
		for (Box b : ideal) {
			double t0 = Double.NEGATIVE_INFINITY;
			double t1 = Double.POSITIVE_INFINITY;
			for (int k = 0; k < 3; k++) {
				if (d[k] == 0) {
					if (o[k] <= b.min()[k] || o[k] >= b.max()[k]) {
						t0 = Double.POSITIVE_INFINITY;
					}
					continue;
				}
				double a = (b.min()[k] - o[k]) / d[k];
				double c = (b.max()[k] - o[k]) / d[k];
				t0 = Math.max(t0, Math.min(a, c));
				t1 = Math.min(t1, Math.max(a, c));
			}
			if (t0 < t1 && t0 > 0) {
				best = Math.min(best, t0);
			}
		}
		return best;
	}

	/** How deep the ray gets under the ideal surface between t0 and t1 (a lower bound: depth inside one box), px. */
	private static double depth(List<Box> ideal, double[] o, double[] d, double t0, double t1) {
		double best = 0;
		int n = 96;
		for (int i = 1; i < n; i++) {
			double[] p = at(o, d, t0 + (t1 - t0) * i / n);
			for (Box b : ideal) {
				double m = Double.POSITIVE_INFINITY;
				for (int k = 0; k < 3; k++) {
					m = Math.min(m, Math.min(p[k] - b.min()[k], b.max()[k] - p[k]));
				}
				best = Math.max(best, m);
			}
		}
		return best;
	}

	/** Whether the ideal surface around a point stays one flat face within {@link #CREASE} px in every direction. */
	private static boolean flat(List<Box> ideal, double[] p, int axis, double[] n) {
		int t1 = (axis + 1) % 3;
		int t2 = (axis + 2) % 3;
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * i / 4;
			double[] q = p.clone();
			q[t1] += CREASE * Math.cos(a);
			q[t2] += CREASE * Math.sin(a);
			if (!onSurface(ideal, q, n)) {
				return false;
			}
		}
		return true;
	}

	/** Whether a point lies on the ideal surface with the outer normal n: solid just behind it, empty just in front. */
	private static boolean onSurface(List<Box> ideal, double[] p, double[] n) {
		double[] in = {p[0] - 1e-4 * n[0], p[1] - 1e-4 * n[1], p[2] - 1e-4 * n[2]};
		double[] out = {p[0] + 1e-4 * n[0], p[1] + 1e-4 * n[1], p[2] + 1e-4 * n[2]};
		return inside(ideal, in) && !inside(ideal, out);
	}

	private static boolean inside(List<Box> ideal, double[] p) {
		for (Box b : ideal) {
			if (b.inside(p)) {
				return true;
			}
		}
		return false;
	}

	/** The end faces of the ideal reaches that are not covered by another box (the stubs). */
	private static List<Quad> exposedEnds(List<Box> ideal) {
		List<Quad> out = new ArrayList<>();
		for (Box b : ideal) {
			if (b.endAxis() < 0) {
				continue;
			}
			int ax = b.endAxis();
			double plane = b.endSign() < 0 ? b.min()[ax] : b.max()[ax];
			double[] c = new double[3];
			for (int k = 0; k < 3; k++) {
				c[k] = k == ax ? plane + b.endSign() * 1e-3 : (b.min()[k] + b.max()[k]) / 2;
			}
			if (!inside(ideal, c)) {
				out.add(new Quad(ax, b.endSign(), plane, b.min(), b.max(), true, "ideal end"));
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ the model and the ideal trunk

	/** The faces of the generated template of a block, turned to its parent and moved to its position (px). */
	private static List<Quad> modelQuads(Block b) {
		List<Quad> out = new ArrayList<>();
		JsonArray elements = TEMPLATES.get(b.r()).getAsJsonArray("elements");
		for (int i = 0; i < elements.size(); i++) {
			JsonObject e = elements.get(i).getAsJsonObject();
			double[] from = vec(e.getAsJsonArray("from"));
			double[] to = vec(e.getAsJsonArray("to"));
			for (Map.Entry<String, JsonElement> f : e.getAsJsonObject("faces").entrySet()) {
				Direction dir = Direction.byName(f.getKey());
				int axis = dir.getAxis().ordinal();
				int sign = dir.getAxisDirection().getStep();
				double[] lo = from.clone();
				double[] hi = to.clone();
				lo[axis] = hi[axis] = sign < 0 ? from[axis] : to[axis];
				double[][] box = place(b, lo, hi);
				int[] n = rotate(b.parent(), new double[] {dir.getStepX(), dir.getStepY(), dir.getStepZ()});
				int nAxis = n[0] != 0 ? 0 : n[1] != 0 ? 1 : 2;
				String texture = f.getValue().getAsJsonObject().get("texture").getAsString();
				out.add(new Quad(nAxis, n[nAxis], box[0][nAxis], box[0], box[1], "#end".equals(texture),
						"element " + i + " " + f.getKey() + " " + texture + " of block " + b.x() + "," + b.y() + "," + b.z()));
			}
		}
		return out;
	}

	/** The ideal trunk of a block: a prism of its radius from the center of its parent to its own center plus r. */
	private static List<Box> idealBoxes(Block b) {
		double a = 8 - b.r();
		double c = 8 + b.r();
		List<Box> out = new ArrayList<>();
		double[][] arm = place(b, new double[] {a, 0, a}, new double[] {c, 8 + b.r(), c});
		out.add(new Box(arm[0], arm[1], -1, 0));
		double[][] reach = place(b, new double[] {a, -8, a}, new double[] {c, 0, c});
		int ax = b.parent().getAxis().ordinal();
		out.add(new Box(reach[0], reach[1], ax, b.parent().getAxisDirection().getStep()));
		return out;
	}

	/** A box of the template (px, parent = down) turned to the block's parent around the block center and moved. */
	private static double[][] place(Block b, double[] from, double[] to) {
		double[] p = rotatePoint(b.parent(), from);
		double[] q = rotatePoint(b.parent(), to);
		double[] off = {16 * b.x(), 16 * b.y(), 16 * b.z()};
		double[] lo = new double[3];
		double[] hi = new double[3];
		for (int k = 0; k < 3; k++) {
			lo[k] = Math.min(p[k], q[k]) + off[k];
			hi[k] = Math.max(p[k], q[k]) + off[k];
		}
		return new double[][] {lo, hi};
	}

	private static double[] rotatePoint(Direction parent, double[] p) {
		double[] v = {p[0] - 8, p[1] - 8, p[2] - 8};
		double[] r = rotateVec(parent, v);
		return new double[] {r[0] + 8, r[1] + 8, r[2] + 8};
	}

	private static int[] rotate(Direction parent, double[] v) {
		double[] r = rotateVec(parent, v);
		return new int[] {(int) Math.round(r[0]), (int) Math.round(r[1]), (int) Math.round(r[2])};
	}

	/** A rotation that takes the template's down (−y) to the parent (the template is symmetric about its axis). */
	private static double[] rotateVec(Direction parent, double[] v) {
		double x = v[0];
		double y = v[1];
		double z = v[2];
		return switch (parent) {
			case DOWN -> new double[] {x, y, z};
			case UP -> new double[] {x, -y, -z};
			case NORTH -> new double[] {x, -z, y};
			case SOUTH -> new double[] {x, z, -y};
			case WEST -> new double[] {y, -x, z};
			case EAST -> new double[] {-y, x, z};
		};
	}

	private static double[] vec(JsonArray a) {
		return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
	}

	private static double[] at(double[] o, double[] d, double t) {
		return new double[] {o[0] + t * d[0], o[1] + t * d[1], o[2] + t * d[2]};
	}

	private static double dist(double[] p, double[] q) {
		return Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]));
	}

	private static double distToQuad(double[] p, Quad q) {
		double s = 0;
		for (int k = 0; k < 3; k++) {
			double v = k == q.axis() ? q.plane() : Math.max(q.min()[k], Math.min(q.max()[k], p[k]));
			s += (p[k] - v) * (p[k] - v);
		}
		return Math.sqrt(s);
	}

	private static String name(int axis, int sign) {
		return switch (axis) {
			case 0 -> sign < 0 ? "west" : "east";
			case 1 -> sign < 0 ? "down" : "up";
			default -> sign < 0 ? "north" : "south";
		};
	}
}
