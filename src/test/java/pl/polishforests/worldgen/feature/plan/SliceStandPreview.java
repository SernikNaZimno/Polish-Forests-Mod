package pl.polishforests.worldgen.feature.plan;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownCover;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Views of simulated stands of the vertical slice without the game (review of V9, round 2; {@code ./gradlew
 * standPreview}): uniform stands of {@link SimulatedStands} written as the tree stand writes them and ray-cast from the
 * eye height and from above the canopy, with flat colors (pine upper bark orange), to find bare wood in the crowns before
 * a game run. Prints per stand the bare wood of the crowns ({@link CrownCover}) and the share of the pixels of each view
 * that show wood above the crown base of its tree.
 */
public final class SliceStandPreview {
	private static final int CHUNKS = 8;
	private static final int SIDE = CHUNKS * 16;
	private static final int HEIGHT = 72;
	/** World y of grid row 0 (below the lowest ground of the relief). */
	private static final int BASE = SimulatedStands.GROUND - 7;
	private static final double RELIEF = 5;
	/** Within the ranges of the hornbeam, the beech and the spruce, so every species of the palettes grows. */
	private static final int RANGES = Species.FLAG_HORNBEAM | Species.FLAG_BEECH | Species.FLAG_SPRUCE;
	private static final int W = 800;
	private static final int H = 450;
	private static final byte AIR = 0;
	private static final byte WOOD = 1;
	private static final byte LEAF = 2;
	private static final byte GROUND_CELL = 3;
	private static final int SKY = 0xA9C8E8;
	/** Share of the transparent pixels of a leaf texture (fancy leaves). */
	private static final double LEAF_HOLES = 0.25;
	private static final Map<Species, Integer> BARK = new EnumMap<>(Species.class);
	private static final Map<Species, Integer> LEAVES = new EnumMap<>(Species.class);
	private static final int[] PINE_BARK = {0x6B5848, 0x8A6046, 0xA8683E, 0xC27840};

	static {
		BARK.put(Species.SCOTS_PINE, 0x6B5848);
		BARK.put(Species.SPRUCE, 0x5A4636);
		BARK.put(Species.BIRCH, 0xD8D4CA);
		BARK.put(Species.OAK, 0x4C3D30);
		BARK.put(Species.SESSILE_OAK, 0x4C3D30);
		BARK.put(Species.LINDEN, 0x6A665E);
		BARK.put(Species.HORNBEAM, 0x9C9C94);
		BARK.put(Species.BEECH, 0xA4A49C);
		BARK.put(Species.NORWAY_MAPLE, 0x6E6658);
		BARK.put(Species.ASH, 0x7A746A);
		LEAVES.put(Species.SCOTS_PINE, 0x3F6A4A);
		LEAVES.put(Species.SPRUCE, 0x2F5A40);
		LEAVES.put(Species.BIRCH, 0x7DA04A);
		LEAVES.put(Species.OAK, 0x4A6F2C);
		LEAVES.put(Species.LINDEN, 0x5C8A34);
		LEAVES.put(Species.HORNBEAM, 0x427A32);
		LEAVES.put(Species.BEECH, 0x52823A);
		LEAVES.put(Species.NORWAY_MAPLE, 0x649038);
		LEAVES.put(Species.ASH, 0x6F9650);
	}

	private record Scenario(String name, HabitatBiome biome, boolean natural, AgeClass age) {
	}

	private static final Scenario[] SCENARIOS = {
			new Scenario("oak_hornbeam_natural_old_growth", HabitatBiome.OAK_HORNBEAM_FOREST, true, AgeClass.OLD_GROWTH),
			new Scenario("oak_hornbeam_natural_mature", HabitatBiome.OAK_HORNBEAM_FOREST, true, AgeClass.MATURE),
			new Scenario("oak_hornbeam_managed_mature", HabitatBiome.OAK_HORNBEAM_FOREST, false, AgeClass.MATURE),
			new Scenario("oak_hornbeam_managed_pole_stage", HabitatBiome.OAK_HORNBEAM_FOREST, false, AgeClass.POLE_STAGE),
			new Scenario("fresh_pine_natural_old_growth", HabitatBiome.FRESH_PINE_FOREST, true, AgeClass.OLD_GROWTH),
			new Scenario("fresh_pine_managed_mature", HabitatBiome.FRESH_PINE_FOREST, false, AgeClass.MATURE),
			new Scenario("fresh_pine_managed_old_growth", HabitatBiome.FRESH_PINE_FOREST, false, AgeClass.OLD_GROWTH),
	};

	private final byte[] kind = new byte[SIDE * SIDE * HEIGHT];
	private final int[] color = new int[SIDE * SIDE * HEIGHT];
	private final int[] owner = new int[SIDE * SIDE * HEIGHT];
	// Wood: half width (blocks) across the axis of its parent and that axis (0 x, 1 y, 2 z).
	private final float[] half = new float[SIDE * SIDE * HEIGHT];
	private final byte[] axis = new byte[SIDE * SIDE * HEIGHT];
	private final List<Integer> crownBase = new ArrayList<>();
	private final List<TreeVoxels> trees = new ArrayList<>();
	private final List<String> labels = new ArrayList<>();

	private final TreeShapePlan.Ground ground = SimulatedStands.relief(RELIEF);
	private final int[] groundY = new int[SIDE * SIDE];

	private SliceStandPreview() {
		for (int x = 0; x < SIDE; x++) {
			for (int z = 0; z < SIDE; z++) {
				int g = ground.top(x, z) - BASE;
				groundY[x * SIDE + z] = g;
				for (int y = 0; y <= g; y++) {
					kind[index(x, y, z)] = GROUND_CELL;
					color[index(x, y, z)] = (x * 31 + z * 17) % 7 == 0 ? 0x6E5A3A : 0x5E7A3E;
				}
			}
		}
	}

	public static void main(String[] args) throws IOException {
		File dir = new File(args.length > 0 ? args[0] : "build/stand-preview");
		dir.mkdirs();
		String only = args.length > 1 ? args[1] : "";
		for (Scenario s : SCENARIOS) {
			if (!only.isEmpty() && !s.name().contains(only)) {
				continue;
			}
			SliceStandPreview p = new SliceStandPreview();
			SimulatedStands.plant(s.biome(), s.natural(), s.age(), CHUNKS, RANGES, p.ground, (v, species, layer) -> {
				p.write(v, species);
				p.labels.add(species.id() + " " + layer);
			});
			CrownCover.Totals cover = new CrownCover.Totals();
			List<int[]> worst = new ArrayList<>();
			Map<String, CrownCover.Totals> bySpecies = new java.util.TreeMap<>();
			for (int t = 0; t < p.trees.size(); t++) {
				CrownCover cc = CrownCover.of(p.trees.get(t));
				cover.add(cc);
				bySpecies.computeIfAbsent(p.labels.get(t), k -> new CrownCover.Totals()).add(cc);
				worst.add(new int[] {cc.exposed(), t});
			}
			worst.sort((x, y) -> y[0] - x[0]);
			StringBuilder sb = new StringBuilder();
			double c = SIDE / 2.0;
			sb.append(p.view(dir, s.name() + "_eye_n", c + 3.5, p.eye(c + 3.5, c - 30.5, 1.6), c - 30.5, 0, 10, 80));
			sb.append(p.view(dir, s.name() + "_eye_w", c - 30.5, p.eye(c - 30.5, c + 2.5, 1.6), c + 2.5, 90, 12, 80));
			sb.append(p.view(dir, s.name() + "_above", c, p.eye(c, 10, 46), 10, 0, -38, 70));
			sb.append(p.view(dir, s.name() + "_30m", c, p.eye(c, c - 24, 30), c - 24, 0, -32, 70));
			long wood = p.trees.stream().mapToLong(TreeVoxels::woodCount).sum();
			long leafCount = p.trees.stream().mapToLong(TreeVoxels::leafCount).sum();
			System.out.printf(Locale.ROOT, "[stand view] %s: %d trees, wood %d, leaves %d, %s%s%n", s.name(), p.trees.size(), wood, leafCount,
					cover.report(CHUNKS * CHUNKS), sb);
			bySpecies.forEach((k, v) -> System.out.printf(Locale.ROOT, "      %-28s %4d trees, %.2f exposed per tree%n", k, v.trees(),
					v.exposedPerTree()));
			for (int k = 0; k < Math.min(4, worst.size()); k++) {
				TreeVoxels v = p.trees.get(worst.get(k)[1]);
				System.out.printf(Locale.ROOT, "      worst: %s at %d %d, height %.1f, %d exposed%n", p.labels.get(worst.get(k)[1]), v.x(), v.z(),
						v.height(), worst.get(k)[0]);
			}
			if (args.length > 2) {
				String[] q = args[2].split(",");
				double fx = Double.parseDouble(q[0]) + 0.5;
				double fz = Double.parseDouble(q[1]) + 0.5;
				double fy = Double.parseDouble(q[2]);
				double d = 11;
				double gy = p.eye(fx, fz, fy);
				p.view(dir, s.name() + "_focus_s", fx, gy, fz - d, 0, 0, 60);
				p.view(dir, s.name() + "_focus_w", fx - d, gy, fz, 90, 0, 60);
				p.view(dir, s.name() + "_focus_n", fx, gy, fz + d, 180, 0, 60);
				p.view(dir, s.name() + "_focus_top", fx, gy + 12, fz - 8, 0, -50, 60);
			}
		}
	}

	/** Grid y of a camera at height h above the ground of column (x, z). */
	private double eye(double x, double z, double h) {
		return groundY[(int) x * SIDE + (int) z] + 1 + h;
	}

	private static int index(int x, int y, int z) {
		return (y * SIDE + x) * SIDE + z;
	}

	private static boolean inside(int x, int y, int z) {
		return x >= 0 && z >= 0 && y >= 0 && x < SIDE && z < SIDE && y < HEIGHT;
	}

	/** Writes a tree as {@code ProceduralTreeFeature.write} does (wood on air or leaves, leaves on air). */
	private void write(TreeVoxels v, Species species) {
		int id = trees.size();
		trees.add(v);
		int n = v.woodCount();
		boolean[] ok = new boolean[n];
		int count = 0;
		int base0 = BASE;
		for (int i = 0; i < n; i++) {
			int p = v.parentIndex(i);
			if (p >= 0 && !ok[p]) {
				continue;
			}
			int x = v.x() + v.woodX(i);
			int y = v.y() + v.woodY(i) - base0;
			int z = v.z() + v.woodZ(i);
			if (!inside(x, y, z) || kind[index(x, y, z)] == WOOD || kind[index(x, y, z)] == GROUND_CELL) {
				continue;
			}
			int at = index(x, y, z);
			kind[at] = WOOD;
			owner[at] = id;
			color[at] = species == Species.SCOTS_PINE ? PINE_BARK[v.bark(i)] : BARK.getOrDefault(species, 0x777777);
			half[at] = Math.min(0.5f, v.radius(i) / 16f);
			int parent = v.parent(i);
			axis[at] = (byte) (parent <= TreeVoxels.UP ? 1 : parent <= TreeVoxels.SOUTH ? 2 : 0);
			ok[i] = true;
			count++;
		}
		int leaves = v.leafCount();
		boolean[] placeable = new boolean[leaves];
		boolean all = count == n;
		int lowest = Integer.MAX_VALUE;
		for (int i = 0; i < leaves; i++) {
			int x = v.x() + v.leafX(i);
			int y = v.y() + v.leafY(i) - base0;
			int z = v.z() + v.leafZ(i);
			placeable[i] = inside(x, y, z) && kind[index(x, y, z)] == AIR;
			all &= placeable[i];
			lowest = Math.min(lowest, y);
		}
		crownBase.add(lowest);
		byte[] d = all ? null : v.leafDistances(ok, placeable);
		int leaf = LEAVES.getOrDefault(species, 0x4A7A3A);
		for (int i = 0; i < leaves; i++) {
			int dist = d == null ? v.leafDistance(i) : d[i];
			if (dist < 1 || !placeable[i]) {
				continue;
			}
			int at = index(v.x() + v.leafX(i), v.y() + v.leafY(i) - base0, v.z() + v.leafZ(i));
			kind[at] = LEAF;
			owner[at] = id;
			color[at] = leaf;
		}
	}

	/** Renders a view; returns its bare-wood pixel shares. */
	private String view(File dir, String name, double ex, double ey, double ez, double yawDeg, double pitchDeg, double hfovDeg)
			throws IOException {
		double yaw = Math.toRadians(yawDeg);
		double pitch = Math.toRadians(pitchDeg);
		double[] f = {Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
		double[] r = {Math.cos(yaw), 0, -Math.sin(yaw)};
		double[] u = {f[1] * r[2] - f[2] * r[1], f[2] * r[0] - f[0] * r[2], f[0] * r[1] - f[1] * r[0]};
		double th = Math.tan(Math.toRadians(hfovDeg) / 2);
		double tv = th * H / W;
		BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		int crownWood = 0;
		int orange = 0;
		int side = 0;
		int orangeUp = 0;
		for (int py = 0; py < H; py++) {
			for (int px = 0; px < W; px++) {
				double a = (2.0 * (px + 0.5) / W - 1) * th;
				double b = (1 - 2.0 * (py + 0.5) / H) * tv;
				double dx = f[0] + a * r[0] + b * u[0];
				double dy = f[1] + a * r[1] + b * u[1];
				double dz = f[2] + a * r[2] + b * u[2];
				int[] hit = new int[2];
				int rgb = cast(ex, ey, ez, dx, dy, dz, hit);
				image.setRGB(px, py, rgb);
				if (hit[0] >= 0 && kind[hit[0]] == WOOD && hit[0] / (SIDE * SIDE) >= crownBase.get(owner[hit[0]])) {
					crownWood++;
				}
				if (hit[0] >= 0 && kind[hit[0]] == WOOD && axis[hit[0]] != 1) {
					side++;
				}
				int cr = rgb >> 16 & 255;
				int cg = rgb >> 8 & 255;
				int cb = rgb & 255;
				if (cr > 150 && cr - cg > 40 && cr - cb > 80) {
					orange++;
					orangeUp += hit[0] >= 0 && kind[hit[0]] == WOOD && axis[hit[0]] == 1 ? 1 : 0;
				}
			}
		}
		ImageIO.write(image, "png", new File(dir, name + ".png"));
		return String.format(Locale.ROOT, "%n    %-48s crown wood %.2f%% of pixels, orange %.2f%% (%.0f%% of it on upright wood), branches (wood on its side) %.2f%%",
				name, 100.0 * crownWood / (W * H), 100.0 * orange / (W * H), 100.0 * orangeUp / Math.max(1, orange), 100.0 * side / (W * H));
	}

	/** Casts a ray through the grid (Amanatides–Woo); hit[0] is the cell index or −1. */
	private int cast(double ox, double oy, double oz, double dx, double dy, double dz, int[] hit) {
		hit[0] = -1;
		int x = (int) Math.floor(ox);
		int y = (int) Math.floor(oy);
		int z = (int) Math.floor(oz);
		int sx = dx > 0 ? 1 : -1;
		int sy = dy > 0 ? 1 : -1;
		int sz = dz > 0 ? 1 : -1;
		double tdx = Math.abs(1 / dx);
		double tdy = Math.abs(1 / dy);
		double tdz = Math.abs(1 / dz);
		double tx = (dx > 0 ? x + 1 - ox : ox - x) * tdx;
		double ty = (dy > 0 ? y + 1 - oy : oy - y) * tdy;
		double tz = (dz > 0 ? z + 1 - oz : oz - z) * tdz;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		int face = 1;
		double t = 0;
		for (int step = 0; step < 1200; step++) {
			if (y < 0) {
				double gx = ox + dx * t;
				double gz = oz + dz * t;
				int g = ((int) Math.floor(gx) * 31 + (int) Math.floor(gz) * 17) % 7 == 0 ? 0x6E5A3A : 0x5E7A3E;
				return fog(g, t * len);
			}
			if (x < 0 || z < 0 || x >= SIDE || z >= SIDE || y >= HEIGHT) {
				return fog(SKY, 0);
			}
			int i = index(x, y, z);
			if (kind[i] == WOOD && half[i] < 0.5f) {
				int f2 = post(ox, oy, oz, dx, dy, dz, x, y, z, i);
				if (f2 >= 0) {
					hit[0] = i;
					double shade = f2 == 1 ? 1.0 : f2 == 0 ? 0.82 : 0.7;
					return fog(scale(color[i], shade), t * len);
				}
			} else if (kind[i] == LEAF && hole(ox + dx * t, oy + dy * t, oz + dz * t, face, i)) {
				// A hole of the leaf texture (fancy leaves): the ray goes on.
			} else if (kind[i] != AIR) {
				hit[0] = i;
				double shade = face == 1 ? (sy < 0 ? 1.0 : 0.55) : face == 0 ? 0.82 : 0.7;
				int c = color[i];
				if (kind[i] == LEAF) {
					shade *= 0.9 + 0.2 * ((x * 73 + y * 151 + z * 37) % 11) / 10.0;
				}
				return fog(scale(c, shade), t * len);
			}
			if (tx < ty && tx < tz) {
				x += sx;
				t = tx;
				tx += tdx;
				face = 0;
			} else if (ty < tz) {
				y += sy;
				t = ty;
				ty += tdy;
				face = 1;
			} else {
				z += sz;
				t = tz;
				tz += tdz;
				face = 2;
			}
		}
		return SKY;
	}

	/** Whether the ray meets a hole of the leaf texture of cell i at point (x, y, z) of its face (a share of the pixels). */
	private static boolean hole(double x, double y, double z, int face, int i) {
		double a = face == 0 ? y : x;
		double b = face == 2 ? y : z;
		int u = (int) Math.floor((a - Math.floor(a)) * 16);
		int v = (int) Math.floor((b - Math.floor(b)) * 16);
		long h = pl.polishforests.worldgen.landscape.Noise.mix(i * 0x9E3779B97F4A7C15L ^ (face * 256L + u * 16L + v) * 0xBF58476D1CE4E5B9L);
		return (h >>> 11) * 0x1.0p-53 < LEAF_HOLES;
	}

	/** The face (0 x, 1 y, 2 z) where the ray meets the thin post of a wood cell, or −1 when it misses. */
	private int post(double ox, double oy, double oz, double dx, double dy, double dz, int x, int y, int z, int i) {
		double h = half[i];
		double[] lo = {x + 0.5 - h, y + 0.5 - h, z + 0.5 - h};
		double[] hi = {x + 0.5 + h, y + 0.5 + h, z + 0.5 + h};
		lo[axis[i]] -= 0.5 - h;
		hi[axis[i]] += 0.5 - h;
		double[] o = {ox, oy, oz};
		double[] d = {dx, dy, dz};
		double tmin = -1e9;
		double tmax = 1e9;
		int face = -1;
		for (int a = 0; a < 3; a++) {
			if (Math.abs(d[a]) < 1e-12) {
				if (o[a] < lo[a] || o[a] > hi[a]) {
					return -1;
				}
				continue;
			}
			double t1 = (lo[a] - o[a]) / d[a];
			double t2 = (hi[a] - o[a]) / d[a];
			if (t1 > t2) {
				double s = t1;
				t1 = t2;
				t2 = s;
			}
			if (t1 > tmin) {
				tmin = t1;
				face = a;
			}
			tmax = Math.min(tmax, t2);
		}
		return tmin <= tmax && tmax > 0 ? Math.max(face, 0) : -1;
	}

	private static int scale(int c, double f) {
		int r = (int) Math.min(255, (c >> 16 & 255) * f);
		int g = (int) Math.min(255, (c >> 8 & 255) * f);
		int b = (int) Math.min(255, (c & 255) * f);
		return r << 16 | g << 8 | b;
	}

	private static int fog(int c, double d) {
		double k = Math.min(0.6, d / 220);
		Color a = new Color(c);
		Color s = new Color(SKY);
		return new Color((int) (a.getRed() * (1 - k) + s.getRed() * k), (int) (a.getGreen() * (1 - k) + s.getGreen() * k),
				(int) (a.getBlue() * (1 - k) + s.getBlue() * k)).getRGB() & 0xFFFFFF;
	}
}
