package pl.polishforests.worldgen.tree;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import pl.polishforests.block.FlareProfile;
import pl.polishforests.worldgen.habitat.Species;

/**
 * PNG preview of the root planner (step I1, rule ZW-1), without the game: {@code ./gradlew rootPreview} (and
 * {@code treePreview}) writes to {@code docs/wycinek/korzenie/}.
 * <ul>
 * <li>{@code korzenie_przekroj.png}: a cross-section of the ground under a tree of every species (rows) by age class and
 * layer (columns), looking north: the soil with the top ground block, the trunk base, the root flares lying on the ground
 * in the bark color of the species, the roots in the soil as bars of their radius from each block to its parent (darker
 * nearer the viewer), rooted dirt as lighter cells; level ground;</li>
 * <li>{@code korzenie_gora.png}: the same trees from above: flares, roots colored by depth below the ground (light:
 * {@value RootPlan#COVER} blocks, dark: {@value RootPlan#MAX_DEPTH}), the reach of the crown as a circle.</li>
 * </ul>
 */
public final class RootPreview {
	private static final long SEED = 20260927L;
	private static final int PX = 16;
	private static final int REACH = RootPlan.REACH;
	private static final int SIDE = 2 * REACH + 1;
	private static final int ABOVE = 3;
	private static final int BELOW = 8;
	private static final int LABEL_W = 110;
	private static final int HEAD_H = 22;
	private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
	private static final List<Species> SPECIES = List.of(Species.OAK, Species.BEECH, Species.LINDEN, Species.HORNBEAM,
			Species.NORWAY_MAPLE, Species.ASH, Species.SPRUCE, Species.SCOTS_PINE, Species.BIRCH);

	private record Column(String label, CrownLayer layer, AgeClass age) {
	}

	private static final Column[] COLUMNS = {
			new Column("a1 pole stage", CrownLayer.CANOPY, AgeClass.POLE_STAGE),
			new Column("a1 mature", CrownLayer.CANOPY, AgeClass.MATURE),
			new Column("a1 old growth", CrownLayer.CANOPY, AgeClass.OLD_GROWTH),
			new Column("a2 old growth", CrownLayer.SUBCANOPY, AgeClass.OLD_GROWTH),
	};

	private static final Color SOIL = new Color(0x8A6A4C);
	private static final Color TOP = new Color(0x5E8A3A);
	private static final Color DIRT = new Color(0x7C5E42);
	private static final Color ROOT = new Color(0x2A160C);
	private static final Color SKY = new Color(0xE6EEF2);

	private RootPreview() {
	}

	public static void main(String[] args) throws IOException {
		File dir = new File(args.length > 0 ? args[0] : "docs/wycinek/korzenie");
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("cannot create " + dir);
		}
		write(dir, "korzenie_przekroj.png", sheet(true));
		write(dir, "korzenie_gora.png", sheet(false));
	}

	private static void write(File dir, String name, BufferedImage image) throws IOException {
		File out = new File(dir, name);
		ImageIO.write(image, "png", out);
		System.out.println("[root preview] " + out.getPath());
	}

	private static BufferedImage sheet(boolean side) {
		int cellW = SIDE * PX + 12;
		int cellH = (side ? (ABOVE + BELOW + 1) : SIDE) * PX + 12;
		BufferedImage img = new BufferedImage(LABEL_W + COLUMNS.length * cellW, HEAD_H + SPECIES.size() * cellH,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		g.setFont(FONT);
		g.setColor(Color.BLACK);
		for (int c = 0; c < COLUMNS.length; c++) {
			g.drawString(COLUMNS[c].label(), LABEL_W + c * cellW + 6, 15);
		}
		for (int s = 0; s < SPECIES.size(); s++) {
			Species sp = SPECIES.get(s);
			g.setColor(Color.BLACK);
			g.drawString(sp.id(), 6, HEAD_H + s * cellH + cellH / 2);
			for (int c = 0; c < COLUMNS.length; c++) {
				Column col = COLUMNS[c];
				int x0 = LABEL_W + c * cellW + 6;
				int y0 = HEAD_H + s * cellH + 6;
				TreeVoxels tree = TreeShapePlan.plan(new TreeRequest(sp, col.layer(), col.age(), SEED, 0, 64, 0, null, null,
						StandMap.uniform(col.age(), true)));
				int base = tree.woodCount() == 0 ? 1 : tree.radius(0);
				RootVoxels v = RootPlan.plan(new RootPlan.Request(sp, col.layer(), col.age(), SEED, 0, 64, 0, base, (x, z) -> 63, null));
				if (side) {
					side(g, x0, y0, sp, tree, v);
				} else {
					top(g, x0, y0, sp, v, SpeciesShape.of(sp).crownRadius().get(col.age()) * (col.layer() == CrownLayer.CANOPY ? 1 : 0.7));
				}
				g.setColor(Color.DARK_GRAY);
				g.drawString(String.format(Locale.ROOT, "%d px, %d fl, %d r", base, v.flareCount(), v.rootCount()), x0 + 2, y0 + 12);
			}
		}
		g.dispose();
		return img;
	}

	/** Cross-section looking north: x to the right, y up; deeper z (farther) lighter. */
	private static void side(Graphics2D g, int x0, int y0, Species sp, TreeVoxels tree, RootVoxels v) {
		// Rows: dy from ABOVE (top) to -BELOW; the trunk base is dy 0, the top ground block dy -1.
		g.setColor(SKY);
		g.fillRect(x0, y0, SIDE * PX, ABOVE * PX);
		g.setColor(SOIL);
		g.fillRect(x0, y0 + ABOVE * PX, SIDE * PX, (BELOW + 1) * PX);
		g.setColor(TOP);
		g.fillRect(x0, y0 + ABOVE * PX, SIDE * PX, PX);
		for (int i = 0; i < v.dirtCount(); i++) {
			g.setColor(DIRT);
			g.fillRect(cx(x0, v.dirtX(i)) - PX / 2, cy(y0, v.dirtY(i)) - PX / 2, PX, PX);
		}
		Color bark = TreePreviewColors.bark(sp);
		// The trunk: its lowest blocks up to ABOVE.
		for (int i = 0; i < tree.woodCount(); i++) {
			if (tree.woodX(i) == 0 && tree.woodZ(i) == 0 && tree.woodY(i) < ABOVE) {
				bar(g, bark, cx(x0, 0), cy(y0, tree.woodY(i)), cx(x0, 0), cy(y0, tree.woodY(i)) - PX, tree.radius(i));
			}
		}
		// Roots, farthest first (a lighter color farther north).
		Integer[] order = new Integer[v.rootCount()];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> Integer.compare(v.rootZ(a), v.rootZ(b))); // north (far) first
		for (int i : order) {
			int p = v.rootParentIndex(i);
			int[] step = TreeVoxels.STEP[v.rootParent(i)];
			int px = v.rootX(i) + step[0];
			int py = v.rootY(i) + step[1];
			double far = (REACH - v.rootZ(i)) / (double) (2 * REACH);
			Color c = mix(ROOT, SOIL, 0.4 * far);
			if (v.rootKind(i) == RootVoxels.EXPOSED) {
				c = DIRT;
			}
			bar(g, c, cx(x0, v.rootX(i)), cy(y0, v.rootY(i)), cx(x0, p < 0 ? 0 : px), cy(y0, p < 0 ? -1 : py), v.rootRadius(i));
		}
		// Flares: fins on the ground, from the profile's height at the parent's side down to that of the next stage.
		for (int i = 0; i < v.flareCount(); i++) {
			int s = v.flareRadius(i);
			boolean flat = sp == Species.SPRUCE;
			int[] step = TreeVoxels.STEP[v.flareParent(i)];
			int floor = cy(y0, 0) + PX / 2;
			int mid = cx(x0, v.flareX(i));
			g.setColor(bark.darker());
			if (step[0] == 0) {
				int w = (int) Math.ceil(FlareProfile.footWidth(flat, s) / 2);
				int h = (int) Math.round(FlareProfile.height(flat, s));
				g.fillRect(mid - w, floor - h, 2 * w, h);
			} else {
				int near = mid + step[0] * PX / 2;
				int far = mid - step[0] * PX / 2;
				g.fillPolygon(new int[] {near, near, far, far},
						new int[] {floor, floor - (int) Math.round(FlareProfile.height(flat, s)),
								floor - (int) Math.round(FlareProfile.height(flat, s - 1)), floor}, 4);
			}
		}
	}

	/** Top view: flares in the bark color, roots by depth below the ground. */
	private static void top(Graphics2D g, int x0, int y0, Species sp, RootVoxels v, double crown) {
		g.setColor(TOP.brighter());
		g.fillRect(x0, y0, SIDE * PX, SIDE * PX);
		g.setColor(new Color(0x2E5A1A));
		int rr = (int) Math.round(crown * PX);
		g.drawOval(x0 + REACH * PX + PX / 2 - rr, y0 + REACH * PX + PX / 2 - rr, 2 * rr, 2 * rr);
		Integer[] order = new Integer[v.rootCount()];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		// Deep roots first, the shallow ones over them.
		java.util.Arrays.sort(order, (a, b) -> Integer.compare(v.rootY(a), v.rootY(b)));
		for (int i : order) {
			int depth = -1 - v.rootY(i);
			double t = Math.clamp((depth - RootPlan.COVER) / (double) (RootPlan.MAX_DEPTH - RootPlan.COVER), 0, 1);
			Color c = mix(new Color(0xC8A07A), ROOT, t);
			int[] step = TreeVoxels.STEP[v.rootParent(i)];
			bar(g, c, x0 + v.rootX(i) * PX + REACH * PX + PX / 2, y0 + v.rootZ(i) * PX + REACH * PX + PX / 2,
					x0 + (v.rootX(i) + step[0]) * PX + REACH * PX + PX / 2, y0 + (v.rootZ(i) + step[2]) * PX + REACH * PX + PX / 2,
					v.rootRadius(i));
		}
		Color bark = TreePreviewColors.bark(sp);
		for (int i = 0; i < v.flareCount(); i++) {
			int[] step = TreeVoxels.STEP[v.flareParent(i)];
			bar(g, bark.darker(), x0 + v.flareX(i) * PX + REACH * PX + PX / 2, y0 + v.flareZ(i) * PX + REACH * PX + PX / 2,
					x0 + (v.flareX(i) + step[0]) * PX + REACH * PX + PX / 2, y0 + (v.flareZ(i) + step[2]) * PX + REACH * PX + PX / 2,
					(int) Math.ceil(FlareProfile.footWidth(sp == Species.SPRUCE, v.flareRadius(i)) / 2));
		}
		g.setColor(Color.BLACK);
		g.fillOval(x0 + REACH * PX + 1, y0 + REACH * PX + 1, PX - 2, PX - 2);
	}

	private static int cx(int x0, int dx) {
		return x0 + (dx + REACH) * PX + PX / 2;
	}

	private static int cy(int y0, int dy) {
		return y0 + (ABOVE - 1 - dy) * PX + PX / 2;
	}

	/** A bar of radius r px of a block (r / 16 of a block on each side) between two points. */
	private static void bar(Graphics2D g, Color c, int xa, int ya, int xb, int yb, int r) {
		g.setColor(c);
		g.setStroke(new BasicStroke(Math.max(2f, 2f * r * PX / 16f), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
		g.drawLine(xa, ya, xb, yb);
	}

	private static Color mix(Color a, Color b, double t) {
		return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
				(int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
				(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}

	/** The bark colors of the tree preview. */
	private static final class TreePreviewColors {
		static Color bark(Species s) {
			return switch (s) {
				case SCOTS_PINE -> new Color(0x6B5848);
				case SPRUCE -> new Color(0x5A4636);
				case BIRCH -> new Color(0xB8B4AA);
				case OAK -> new Color(0x4C3D30);
				case LINDEN -> new Color(0x6A665E);
				case HORNBEAM -> new Color(0x8C8C84);
				case BEECH -> new Color(0xA4A49C);
				case NORWAY_MAPLE -> new Color(0x6E6658);
				default -> new Color(0x7A746A);
			};
		}
	}
}
