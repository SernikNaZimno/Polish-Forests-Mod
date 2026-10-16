package pl.polishforests.worldgen.tree;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * PNG preview of the tree planner (step V6, rule ZW-1): {@code ./gradlew treePreview} writes to
 * {@code docs/wycinek/drzewa/}, without the game.
 * <ul>
 * <li>{@code iglaste_bok.png} / {@code iglaste_gora.png}, {@code lisciaste_bok.png} / {@code lisciaste_gora.png}: side
 * and top views of every species (rows) by age class and layer (columns: canopy in the pole stage, mature stand and old
 * growth, subcanopy of the mature stand and of the old growth, regeneration of the old growth), one lone tree each;</li>
 * <li>{@code iglaste_warianty.png}, {@code lisciaste_warianty.png}: six canopy trees of the mature stand of each species
 * at other columns (the spread of the shapes);</li>
 * <li>{@code drzewostan_bor.png}, {@code drzewostan_grad.png}: a stand of 3 × 3 chunks on level ground, the trees of
 * every kept candidate of the M2 pattern with their crown territories ({@link CrownTerritories}): a managed mature pine
 * stand (Scots pine, some birch; spruce and birch below) and a natural old oak-hornbeam stand (oak, linden, maple, ash and
 * beech in the canopy, hornbeam and linden below, hornbeam and linden regeneration); top view with chunk borders and a
 * side view of the middle row of chunks.</li>
 * </ul>
 * Side views look north: wood as bars of its radius (blocks are 6 px, a radius r px is 2r × 6 / 16 px wide), barks in
 * their colors (the pine's lower bark, transition zone and "mirror"), leaves shaded by depth. Top views: the highest
 * block of each column, leaves lighter with height.
 */
public final class TreePreview {
	private static final long SEED = 20260927L;
	private static final int PX = 6;
	private static final int REACH = TreeShapePlan.REACH;
	private static final int SIDE = 2 * REACH + 1;
	private static final int ROWS_H = 44;
	private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

	private record Column(String label, CrownLayer layer, AgeClass age) {
	}

	private static final Column[] COLUMNS = {
			new Column("a1 pole stage", CrownLayer.CANOPY, AgeClass.POLE_STAGE),
			new Column("a1 mature", CrownLayer.CANOPY, AgeClass.MATURE),
			new Column("a1 old growth", CrownLayer.CANOPY, AgeClass.OLD_GROWTH),
			new Column("a2 mature", CrownLayer.SUBCANOPY, AgeClass.MATURE),
			new Column("a2 old growth", CrownLayer.SUBCANOPY, AgeClass.OLD_GROWTH),
			new Column("regeneration", CrownLayer.REGENERATION, AgeClass.OLD_GROWTH),
	};

	static final Species[] CONIFERS = {Species.SCOTS_PINE, Species.SPRUCE};
	static final Species[] BROADLEAVES = {Species.BIRCH, Species.OAK, Species.LINDEN, Species.HORNBEAM, Species.BEECH,
			Species.NORWAY_MAPLE, Species.ASH};

	private static final Map<Species, Color> BARK = new EnumMap<>(Species.class);
	private static final Map<Species, Color> LEAF = new EnumMap<>(Species.class);

	static {
		BARK.put(Species.SCOTS_PINE, new Color(0x6B5848));
		BARK.put(Species.SPRUCE, new Color(0x5A4636));
		BARK.put(Species.BIRCH, new Color(0xD8D4CA));
		BARK.put(Species.OAK, new Color(0x4C3D30));
		BARK.put(Species.LINDEN, new Color(0x6A665E));
		BARK.put(Species.HORNBEAM, new Color(0x8C8C84));
		BARK.put(Species.BEECH, new Color(0xA4A49C));
		BARK.put(Species.NORWAY_MAPLE, new Color(0x6E6658));
		BARK.put(Species.ASH, new Color(0x7A746A));
		LEAF.put(Species.SCOTS_PINE, new Color(0x4F7A5A));
		LEAF.put(Species.SPRUCE, new Color(0x2F5A40));
		LEAF.put(Species.BIRCH, new Color(0x8DB04A));
		LEAF.put(Species.OAK, new Color(0x5A7F34));
		LEAF.put(Species.LINDEN, new Color(0x6C9A3C));
		LEAF.put(Species.HORNBEAM, new Color(0x4F8A3A));
		LEAF.put(Species.BEECH, new Color(0x62923E));
		LEAF.put(Species.NORWAY_MAPLE, new Color(0x74A040));
		LEAF.put(Species.ASH, new Color(0x7FA65A));
	}

	private static final Color[] PINE_BARK = {new Color(0x6B5848), new Color(0x8A6046), new Color(0xA8683E), new Color(0xC27840)};

	private TreePreview() {
	}

	public static void main(String[] args) throws IOException {
		File dir = new File(args.length > 0 ? args[0] : "docs/wycinek/drzewa");
		boolean conifersOnly = args.length > 1 && args[1].equals("conifers");
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("cannot create " + dir);
		}
		write(dir, "iglaste_bok.png", sheet(CONIFERS, true));
		write(dir, "iglaste_gora.png", sheet(CONIFERS, false));
		write(dir, "iglaste_warianty.png", variants(CONIFERS));
		write(dir, "drzewostan_bor.png", stand(false));
		if (conifersOnly) {
			return;
		}
		write(dir, "lisciaste_bok.png", sheet(BROADLEAVES, true));
		write(dir, "lisciaste_gora.png", sheet(BROADLEAVES, false));
		write(dir, "lisciaste_warianty.png", variants(BROADLEAVES));
		write(dir, "drzewostan_grad.png", stand(true));
	}

	private static void write(File dir, String name, BufferedImage image) throws IOException {
		File out = new File(dir, name);
		ImageIO.write(image, "png", out);
		System.out.println("[tree preview] " + out.getPath());
	}

	private static TreeVoxels lone(Species species, CrownLayer layer, AgeClass age, int x, int z) {
		return TreeShapePlan.plan(new TreeRequest(species, layer, age, SEED, x, 0, z));
	}

	/** Species by age class and layer, side or top views. */
	private static BufferedImage sheet(Species[] species, boolean side) {
		int cellW = SIDE * PX;
		int cellH = (side ? ROWS_H : SIDE) * PX;
		int labelW = 110;
		BufferedImage image = new BufferedImage(labelW + COLUMNS.length * cellW, 20 + species.length * (cellH + 4),
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = graphics(image);
		for (int c = 0; c < COLUMNS.length; c++) {
			g.setColor(Color.BLACK);
			g.drawString(COLUMNS[c].label(), labelW + c * cellW + 6, 14);
		}
		for (int r = 0; r < species.length; r++) {
			int y0 = 20 + r * (cellH + 4);
			g.setColor(Color.BLACK);
			g.drawString(species[r].id(), 6, y0 + cellH / 2);
			for (int c = 0; c < COLUMNS.length; c++) {
				Column col = COLUMNS[c];
				TreeVoxels v = lone(species[r], col.layer(), col.age(), 100 + 37 * r, 200 + 53 * c);
				int x0 = labelW + c * cellW;
				g.setColor(new Color(0xF4F4F0));
				g.fillRect(x0 + 1, y0, cellW - 2, cellH);
				List<Placed> trees = List.of(new Placed(v, species[r], 0, 0));
				if (side) {
					side(g, trees, x0 + REACH * PX, y0 + cellH - PX, Integer.MIN_VALUE, Integer.MAX_VALUE);
					g.setColor(Color.DARK_GRAY);
					g.drawString(String.format(Locale.ROOT, "%.1f m, %d+%d", v.height(), v.woodCount(), v.leafCount()), x0 + 4,
							y0 + 12);
				} else {
					top(g, trees, x0 + REACH * PX, y0 + REACH * PX);
				}
			}
		}
		g.dispose();
		return image;
	}

	/** Six mature canopy trees of each species at other columns. */
	private static BufferedImage variants(Species[] species) {
		int n = 6;
		int cellW = SIDE * PX;
		int cellH = ROWS_H * PX;
		BufferedImage image = new BufferedImage(110 + n * cellW, 20 + species.length * (cellH + 4), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = graphics(image);
		g.setColor(Color.BLACK);
		g.drawString("a1 mature, six columns", 116, 14);
		for (int r = 0; r < species.length; r++) {
			int y0 = 20 + r * (cellH + 4);
			g.setColor(Color.BLACK);
			g.drawString(species[r].id(), 6, y0 + cellH / 2);
			for (int c = 0; c < n; c++) {
				TreeVoxels v = lone(species[r], CrownLayer.CANOPY, AgeClass.MATURE, -400 + 91 * c, 700 + 29 * r);
				int x0 = 110 + c * cellW;
				g.setColor(new Color(0xF4F4F0));
				g.fillRect(x0 + 1, y0, cellW - 2, cellH);
				side(g, List.of(new Placed(v, species[r], 0, 0)), x0 + REACH * PX, y0 + cellH - PX, Integer.MIN_VALUE,
						Integer.MAX_VALUE);
			}
		}
		g.dispose();
		return image;
	}

	/** A tree with its offset from the origin of a view. */
	record Placed(TreeVoxels voxels, Species species, int x, int z) {
	}

	/** Species of a stand tree by layer and the hash of its column. */
	static Species standSpecies(boolean grad, CrownLayer layer, int x, int z) {
		double u = (Noise.mix(Noise.key(x, z, grad ? 77 : 78)) >>> 11) * 0x1.0p-53;
		if (!grad) {
			return switch (layer) {
				case CANOPY -> u < 0.88 ? Species.SCOTS_PINE : Species.BIRCH;
				case SUBCANOPY -> u < 0.6 ? Species.SPRUCE : Species.BIRCH;
				case REGENERATION -> u < 0.5 ? Species.SPRUCE : Species.SCOTS_PINE;
			};
		}
		return switch (layer) {
			case CANOPY -> u < 0.4 ? Species.OAK : u < 0.7 ? Species.LINDEN : u < 0.8 ? Species.NORWAY_MAPLE : u < 0.9
					? Species.ASH : Species.BEECH;
			case SUBCANOPY -> u < 0.8 ? Species.HORNBEAM : Species.LINDEN;
			case REGENERATION -> u < 0.6 ? Species.HORNBEAM : Species.LINDEN;
		};
	}

	/** The trees of every kept candidate of 3 × 3 chunks with their territories, on level ground. */
	static List<Placed> standTrees(boolean grad, int cx0, int cz0) {
		AgeClass age = grad ? AgeClass.OLD_GROWTH : AgeClass.MATURE;
		CrownTerritories territories = new CrownTerritories(SEED, StandMap.uniform(age, grad));
		CrownTerritories.Session s = territories.session();
		List<Placed> out = new ArrayList<>();
		for (int cx = cx0; cx < cx0 + 3; cx++) {
			for (int cz = cz0; cz < cz0 + 3; cz++) {
				for (CrownTerritory t : s.chunk(cx, cz, null)) {
					Species sp = standSpecies(grad, t.layer(), t.x(), t.z());
					TreeVoxels v = TreeShapePlan.plan(new TreeRequest(sp, t.layer(), age, SEED, t.x(), 0, t.z(), t, s::candidate));
					out.add(new Placed(v, sp, t.x() - cx0 * 16, t.z() - cz0 * 16));
				}
			}
		}
		return out;
	}

	private static BufferedImage stand(boolean grad) {
		int cx0 = grad ? 27 : -44;
		int cz0 = grad ? -46 : 120;
		List<Placed> trees = standTrees(grad, cx0, cz0);
		int w = 48 * PX;
		int topH = 48 * PX;
		int sideH = 40 * PX;
		BufferedImage image = new BufferedImage(2 * w + 30, 24 + Math.max(topH, sideH), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = graphics(image);
		g.setColor(Color.BLACK);
		g.drawString((grad ? "natural old oak-hornbeam" : "managed mature pine") + ", 3 x 3 chunks, " + trees.size()
				+ " trees: top view, side view of the middle chunk row (z 16-31, looking north)", 6, 14);
		g.setColor(new Color(0xF4F4F0));
		g.fillRect(0, 22, w, topH);
		g.fillRect(w + 30, 22, w, sideH);
		g.setClip(0, 22, w, topH);
		top(g, trees, 0, 22);
		g.setClip(null);
		g.setColor(new Color(0x606060));
		for (int k = 0; k <= 3; k++) {
			g.drawLine(k * 16 * PX, 22, k * 16 * PX, 22 + topH);
			g.drawLine(0, 22 + k * 16 * PX, w, 22 + k * 16 * PX);
		}
		g.setClip(w + 30, 22, w, sideH);
		side(g, trees, w + 30, 22 + sideH - PX, 16, 31);
		g.setClip(null);
		g.dispose();
		return image;
	}

	private static Graphics2D graphics(BufferedImage image) {
		Graphics2D g = image.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(FONT);
		return g;
	}

	private record Block(int x, int y, int z, Color color, int radius, int parent) {
	}

	/**
	 * Side view looking north (towards -z): the blocks of the trees with z in [zMin, zMax] (view coordinates), the
	 * farthest first; (ox, oy) is the pixel of block x = 0, y = 0.
	 */
	private static void side(Graphics2D g, List<Placed> trees, int ox, int oy, int zMin, int zMax) {
		List<Block> blocks = blocks(trees, zMin, zMax);
		blocks.sort(Comparator.comparingInt(Block::z).reversed());
		int zFar = blocks.stream().mapToInt(Block::z).max().orElse(0);
		int zNear = blocks.stream().mapToInt(Block::z).min().orElse(0);
		for (Block b : blocks) {
			int px = ox + b.x() * PX;
			int py = oy - b.y() * PX;
			double depth = zFar == zNear ? 0.5 : (double) (b.z() - zNear) / (zFar - zNear);
			if (b.radius() == 0) {
				g.setColor(shade(b.color(), 1.15 - 0.45 * depth));
				g.fillRect(px, py, PX, PX);
				continue;
			}
			g.setColor(shade(b.color(), 1.05 - 0.3 * depth));
			int thick = b.radius() >= TreeVoxels.FULL ? PX : Math.max(1, (int) Math.round(2 * b.radius() * PX / 16.0));
			int off = (PX - thick) / 2;
			switch (b.parent()) {
				case TreeVoxels.DOWN, TreeVoxels.UP -> g.fillRect(px + off, py, thick, PX);
				case TreeVoxels.WEST, TreeVoxels.EAST -> g.fillRect(px, py + off, PX, thick);
				default -> g.fillRect(px + off, py + off, thick, thick);
			}
		}
	}

	/** Top view: the highest block of each column; (ox, oz) is the pixel of block x = 0, z = 0. */
	private static void top(Graphics2D g, List<Placed> trees, int ox, int oz) {
		Map<Long, Block> highest = new java.util.HashMap<>();
		for (Block b : blocks(trees, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
			long key = (long) b.x() << 32 | (b.z() & 0xFFFFFFFFL);
			Block old = highest.get(key);
			if (old == null || b.y() > old.y()) {
				highest.put(key, b);
			}
		}
		for (Block b : highest.values()) {
			double light = b.radius() == 0 ? 0.65 + 0.5 * Math.min(1, b.y() / 30.0) : 1;
			g.setColor(shade(b.color(), light));
			g.fillRect(ox + b.x() * PX, oz + b.z() * PX, PX, PX);
		}
		for (Placed p : trees) {
			g.setColor(Color.BLACK);
			g.fillRect(ox + p.x() * PX + PX / 2 - 1, oz + p.z() * PX + PX / 2 - 1, 2, 2);
		}
	}

	private static List<Block> blocks(List<Placed> trees, int zMin, int zMax) {
		List<Block> out = new ArrayList<>();
		for (Placed p : trees) {
			TreeVoxels v = p.voxels();
			Color barkColor = BARK.get(p.species());
			for (int i = 0; i < v.woodCount(); i++) {
				int z = p.z() + v.woodZ(i);
				if (z < zMin || z > zMax) {
					continue;
				}
				Color c = p.species() == Species.SCOTS_PINE ? PINE_BARK[v.bark(i)] : barkColor;
				out.add(new Block(p.x() + v.woodX(i), v.woodY(i), z, c, v.radius(i), v.parent(i)));
			}
			Color leaf = LEAF.get(p.species());
			for (int i = 0; i < v.leafCount(); i++) {
				int z = p.z() + v.leafZ(i);
				if (z < zMin || z > zMax) {
					continue;
				}
				out.add(new Block(p.x() + v.leafX(i), v.leafY(i), z, leaf, 0, 0));
			}
		}
		return out;
	}

	private static Color shade(Color c, double f) {
		return new Color((int) Math.clamp(c.getRed() * f, 0, 255), (int) Math.clamp(c.getGreen() * f, 0, 255),
				(int) Math.clamp(c.getBlue() * f, 0, 255));
	}
}
