package pl.polishforests.worldgen.tree;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * PNG preview of the crown territories (step V5, rule ZW-1): {@code ./gradlew crownPreview} writes to
 * {@code docs/wycinek/korony/}. An area of 8 × 8 chunks of the test stand map ({@link PatchyStandMap}, half natural) with
 * a meandering open ground (pale yellow) and a palette that gives a tree to {@value #TREE_SHARE} of the forest candidates
 * (about 11 trees per chunk, as N1; {@link PaletteVacancies}, with the rule of the game: exact vacancies of the trunk's
 * own chunk, sure ones of its neighbors): one image per layer, each territory of a tree in its own color, trunks as black
 * dots, trunks next to a marker (a vacant candidate or a canopy gap within the edge reach) as red dots, vacant forest
 * candidates as gray rings, canopy gap columns of natural stands hatched gray, managed stands with a light blue tint and
 * chunk borders as thin lines. Every column belongs to at most one territory of a layer.
 */
public final class CrownTerritoryPreview {
	private static final long SEED = 20260927L;
	private static final int CHUNKS = 8;
	private static final int PX = 6;
	private static final double TREE_SHARE = 0.65;

	private CrownTerritoryPreview() {
	}

	public static void main(String[] args) throws IOException {
		File dir = new File(args.length > 0 ? args[0] : "docs/wycinek/korony");
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("cannot create " + dir);
		}
		PatchyStandMap stands = new PatchyStandMap(SEED, 0.5);
		Noise n = new Noise(SEED).derive("test.crown_territory.open");
		Vacancies open = (x, z) -> n.fbm(x, z, 90, 2, 0.5) > 0.2;
		Vacancies vacancies = new PaletteVacancies(SEED, TREE_SHARE, open);
		CrownTerritories territories = new CrownTerritories(SEED, stands);
		int x0 = 10 * 16;
		int z0 = -6 * 16;
		for (CrownLayer layer : CrownLayer.values()) {
			BufferedImage image = render(territories, stands, open, vacancies, layer, x0, z0);
			File out = new File(dir, "terytoria_" + layer.name().toLowerCase(java.util.Locale.ROOT) + ".png");
			ImageIO.write(image, "png", out);
			System.out.println("[crown preview] " + out.getPath());
		}
	}

	private static BufferedImage render(CrownTerritories territories, StandMap stands, Vacancies open, Vacancies vacancies,
			CrownLayer layer,
			int x0, int z0) {
		int side = CHUNKS * 16;
		BufferedImage image = new BufferedImage(side * PX, side * PX, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		for (int i = 0; i < side; i++) {
			for (int j = 0; j < side; j++) {
				int x = x0 + i;
				int z = z0 + j;
				Color c = open.vacant(x, z) ? new Color(0xF2E7B6) : stands.natural(x, z) ? new Color(0xFFFFFF) : new Color(0xE6EEF6);
				g.setColor(c);
				g.fillRect(i * PX, j * PX, PX, PX);
				if (stands.canopyGap(x, z) && (i + j) % 2 == 0) {
					g.setColor(new Color(0xC8C8C8));
					g.fillRect(i * PX, j * PX, PX, PX);
				}
			}
		}
		CrownTerritories.Session s = territories.session();
		for (int cx = x0 >> 4; cx < (x0 >> 4) + CHUNKS; cx++) {
			for (int cz = z0 >> 4; cz < (z0 >> 4) + CHUNKS; cz++) {
				List<CrownTerritory> list = s.chunk(cx, cz, vacancies);
				for (CrownTerritory t : list) {
					if (t.layer() != layer) {
						continue;
					}
					long h = Noise.mix((long) t.x() * 31 + t.z());
					Color color = Color.getHSBColor((h >>> 40 & 0xFFFF) / 65536f, 0.45f, 0.85f);
					g.setColor(color);
					for (int dz = -16; dz <= 16; dz++) {
						for (int dx = -16; dx <= 16; dx++) {
							int i = t.x() + dx - x0;
							int j = t.z() + dz - z0;
							if (i >= 0 && j >= 0 && i < side && j < side && t.contains(t.x() + dx, t.z() + dz)) {
								g.fillRect(i * PX, j * PX, PX, PX);
							}
						}
					}
				}
			}
		}
		g.setColor(new Color(0x606060));
		for (int k = 0; k <= CHUNKS; k++) {
			g.drawLine(k * 16 * PX, 0, k * 16 * PX, side * PX);
			g.drawLine(0, k * 16 * PX, side * PX, k * 16 * PX);
		}
		for (int cx = x0 >> 4; cx < (x0 >> 4) + CHUNKS; cx++) {
			for (int cz = z0 >> 4; cz < (z0 >> 4) + CHUNKS; cz++) {
				for (CrownTerritory t : s.chunk(cx, cz, null)) {
					int i = t.x() - x0;
					int j = t.z() - z0;
					if (vacancies.vacant(t.x(), t.z())) {
						if (!open.vacant(t.x(), t.z())) {
							g.setColor(new Color(0x707070));
							g.drawOval(i * PX + 1, j * PX + 1, PX - 3, PX - 3);
						}
						continue;
					}
					if (t.layer() != layer) {
						continue;
					}
					g.setColor(s.territory(t.x(), t.z(), vacancies).edge() ? new Color(0xC0392B) : Color.BLACK);
					g.fillOval(i * PX + 1, j * PX + 1, PX - 2, PX - 2);
				}
			}
		}
		g.dispose();
		return image;
	}
}
