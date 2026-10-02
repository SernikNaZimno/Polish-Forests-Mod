package pl.polishforests.worldgen.landscape;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.BiomeSharesTest;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.habitat.Fertility;
import pl.polishforests.worldgen.habitat.SpeciesRanges;

/**
 * Podgląd siedlisk M2 (docs/03-m2-biomy.md §12.2): mapy biomów z legendą, stref, DGW, trofii, pól
 * regionalnych O i P oraz zasięgów, kadry z planu, przekroje dolin i plik CSV z udziałami biomów.
 * Wołany z {@link LandscapePreview}.
 */
final class HabitatPreview {
	private HabitatPreview() {
	}

	/** Warstwa mapy. */
	enum Layer {
		BIOMES, ZONES, DGW, FERTILITY
	}

	private static final int LEGEND = 300;

	/** Kolory biomów (kolejność enuma). */
	static final int[] BIOME_COLOR = {
			0xD9D27A, // bor_suchy
			0xA9B95A, // bor_swiezy
			0xC4C98A, // bor_bazynowy
			0x7FA05E, // bor_wilgotny
			0x6E7F5A, // bor_bagienny
			0x8DAF4F, // bor_mieszany
			0x5E9A45, // las_mieszany
			0x3F8A3A, // grad
			0x2E6B2F, // buczyna_nizinna
			0x3E6E6A, // ols
			0x2F8C7A, // leg_jesionowo_olszowy
			0x6FC6A0, // leg_wierzbowo_topolowy
			0x4BA77A, // leg_wiazowo_jesionowy
			0x2C5A46, // jedlina_wyzynna
			0x1F5A2A, // buczyna_gorska
			0x1B3D33, // swierczyna_gorska
			0x5BA0A0, // olszyna_gorska
			0x6E7D48, // kosodrzewina
			0xB07A6A, // torfowisko_wysokie
			0x9A8A5A, // torfowisko_niskie
			0xB8C46A, // szuwar
			0xA3D36F, // wikliny
			0xC08AB0, // wrzosowisko
			0xA8D0A0, // laka_wilgotna
			0xC8E08A, // laka_swieza
			0xE0C878, // pole
			0xF2E6B8, // plaza
			0xFAF5DC, // wydma_biala
			0xD8D0B0, // wydma_szara
			0xC8C8B8, // hala
			0x1E4A7A, // morze
			0x4A7AA0, // zalew
			0x3A78C0, // rzeka
			0x56A0D8, // potok
			0x24508F, // jezioro
			0x5A4A2E, // jezioro_dystroficzne
	};

	static final Map<Zone, Integer> ZONE_COLOR = new EnumMap<>(Zone.class);

	static {
		if (BIOME_COLOR.length != HabitatBiome.values().length) {
			throw new IllegalStateException("brak kolorów biomów");
		}
		int[] k = {0, 0x3A78C0, 0x2A9AB0, 0x2FD0C0, 0xC0D040, 0xE0E040, 0xF0E0A0, 0x90E040, 0xFF8040, 0xFF40A0,
				0x40FF80, 0xA040FF, 0xB0B0B0, 0xFFB0FF, 0x00FFFF, 0x804020, 0x206040, 0xFFFFFF, 0xFFF080, 0xC06030,
				0xE0A060, 0x404040, 0xFF2020};
		for (Zone s : Zone.values()) {
			ZONE_COLOR.put(s, k[s.ordinal()]);
		}
	}

	/** Kadr: środek, bok w metrach i rozdzielczość. */
	record Frame(String name, LandscapeModel model, double cx, double cz, double sideLength) {
	}

	// ------------------------------------------------------------------ program

	static void main(Path out, long seed) throws IOException {
		LandscapeModel real = new LandscapeModel(seed, 1.0);
		LandscapeModel gameplayModel = new LandscapeModel(seed, LandscapeScale.GAMEPLAY, 1.0);
		HabitatClassifier realClassifier = new HabitatClassifier(seed, LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL);
		HabitatClassifier gameplayClassifier = new HabitatClassifier(seed, LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.NATURAL);
		List<Frame> frames = new ArrayList<>();
		double[] p;
		p = findClassSite(real, realClassifier, true, 1_000);
		double[] large = p;
		add(frames, "large_river_valley_2km", real, p, 2_000);
		p = findClassSite(gameplayModel, gameplayClassifier, true, 50);
		add(frames, "gameplay_large_river_valley_1km", gameplayModel, p, 1_000);
		p = findSmallRiver(real, 500);
		double[] smallRiver = p;
		add(frames, "small_river_500m", real, p, 500);
		p = RiverNetworkTest.findStream(real);
		double[] mountainStream = p;
		add(frames, "mountain_stream_1km", real, p, 1_000);
		p = findLake(real, 700);
		add(frames, "tunnel_valley_lake_1km", real, p, 1_000);
		p = RiverNetworkTest.find(real, c -> c.waterKind() == WaterKind.KETTLE && c.type() == LandscapeType.OUTWASH_PLAIN, 400);
		add(frames, "kettle_outwash_plain_500m", real, p, 500);
		p = RiverNetworkTest.find(real, c -> c.waterKind() == WaterKind.SEA && c.terrain().coastD() >= 0
				&& c.waterLevel() - c.surface() > 2, 1_000);
		add(frames, "coast_lagoon_3km", real, p, 3_000);
		p = LandscapePreview.findInterior(real, LandscapeType.BESKIDS);
		add(frames, "beskids_10km", real, p, 10_000);
		double[] highSpot = findHighest(real);
		add(frames, "high_beskids_10km", real, highSpot, 10_000);
		p = LandscapePreview.findInterior(real, LandscapeType.OUTWASH_PLAIN);
		add(frames, "outwash_plain_20km", real, p, 20_000);
		p = LandscapePreview.findInterior(real, LandscapeType.MORAINE_PLATEAU);
		add(frames, "moraine_plateau_20km", real, p, 20_000);
		add(frames, "gameplay_20km", gameplayModel, new double[] {0, 0}, 20_000);
		for (Frame frame : frames) {
			HabitatClassifier classifier = frame.model() == real ? realClassifier : gameplayClassifier;
			int[][] code = new int[800][800];
			ColumnSample[][] s = samples(frame, classifier, code);
			save(frame, s, code, classifier, Layer.BIOMES, out.resolve("m2_" + frame.name() + "_biomes.png"));
			save(frame, s, code, classifier, Layer.ZONES, out.resolve("m2_" + frame.name() + "_zones.png"));
			if (frame.name().startsWith("large_river_valley") || frame.name().startsWith("beskids_10") || frame.name().startsWith("outwash_plain")
					|| frame.name().startsWith("moraine_plateau") || frame.name().startsWith("tunnel_valley_lake")) {
				save(frame, s, code, classifier, Layer.DGW, out.resolve("m2_" + frame.name() + "_dgw.png"));
				save(frame, s, code, classifier, Layer.FERTILITY, out.resolve("m2_" + frame.name() + "_fertility.png"));
			}
		}
		// Maska lasu w trybie D (S8 kalibruje P_las; tu tylko podgląd mechanizmu).
		HabitatClassifier presentDayClassifier = new HabitatClassifier(seed, LandscapeScale.REALISTIC, HabitatClassifier.Mode.PRESENT_DAY);
		p = LandscapePreview.find(real, LandscapeType.OUTWASH_PLAIN, false);
		if (p != null) {
			Frame frame = new Frame("forest_mask_present_day_50km", real, p[0], p[1], 50_000);
			int[][] code = new int[800][800];
			ColumnSample[][] s = samples(frame, presentDayClassifier, code);
			save(frame, s, code, presentDayClassifier, Layer.BIOMES, out.resolve("m2_forest_mask_present_day_50km_biomes.png"));
		}
		regionalMaps(real, out);
		sharesCsv(out);
		sections(real, realClassifier, new double[][] {large, smallRiver, mountainStream}, out.resolve("m2_valley_cross_sections.png"));
	}

	private static void add(List<Frame> frames, String name, LandscapeModel m, double[] p, double sideLength) {
		if (p == null) {
			System.out.println("Podgląd siedlisk: nie znaleziono kadru " + name);
			return;
		}
		frames.add(new Frame(name, m, p[0], p[1], sideLength));
	}

	// ------------------------------------------------------------------ wyszukiwanie kadrów

	/** Rzeka klasy A (duża nizinna): koryto rzędu 3 na nizinie, szerokie. */
	static double[] findClassSite(LandscapeModel m, HabitatClassifier k, boolean large, double step) {
		double minW = m.scale().channel() * 60;
		return RiverNetworkTest.find(m, c -> c.waterKind() == WaterKind.RIVER && c.type().isLowland()
				&& c.waters().streamOrder() == 3 && c.waters().channelWidth() >= minW && c.waters().channelGradient() <= 3, step);
	}

	/** Mała rzeka nizinna (klasa B): koryto 3–15 m (1:1) z dnem doliny. */
	static double[] findSmallRiver(LandscapeModel m, double step) {
		double ch = m.scale().channel();
		return RiverNetworkTest.find(m, c -> c.waterKind() == WaterKind.RIVER && c.type() == LandscapeType.MORAINE_PLATEAU
				&& c.waters().channelWidth() / ch >= 4 && c.waters().channelWidth() / ch < 15 && c.waters().channelGradient() <= 2
				&& c.waters().floorHalfWidth() > 60, step);
	}

	/** Brzeg jeziora rynnowego na glinie (pierścienie olsu i łozowiska), z dala od dolin rzek. */
	static double[] findLake(LandscapeModel m, double step) {
		for (int r = 0; r < 800; r++) {
			int n = Math.max(12, r * 2);
			for (int k = 0; k < n; k++) {
				double a = k * (2 * Math.PI / n) + r * 0.37;
				double x = Math.cos(a) * r * step;
				double z = Math.sin(a) * r * step;
				ColumnSample c = m.sample(x, z);
				if (c.hasWater() || c.substrate() != Substrate.GLACIAL_TILL || c.waters().channelDist() < 400
						|| c.waters().standingWaterKind() != ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE || c.waters().s() > 20
						|| c.waters().standingWaterRadius() < 150) {
					continue;
				}
				boolean farFromRivers = true;
				for (int q = 0; q < 4 && farFromRivers; q++) {
					double b = q * Math.PI / 2;
					farFromRivers = m.sample(x + 300 * Math.cos(b), z + 300 * Math.sin(b)).waters().channelDist() > 250;
				}
				if (farFromRivers) {
					return new double[] {x, z};
				}
			}
		}
		return null;
	}

	/** Najwyższe Beskidy w promieniu 1500 km (piętra górne). */
	static double[] findHighest(LandscapeModel m) {
		double best = 0;
		double[] bp = null;
		int n = 600;
		double span = 3_000_000;
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				double x = -span / 2 + (i + 0.5) * span / n;
				double z = -span / 2 + (j + 0.5) * span / n;
				double h = m.landElevation(x, z);
				if (h > best) {
					best = h;
					bp = new double[] {x, z};
				}
			}
		}
		// Doprecyzowanie: najwyższy punkt w kwadracie 10 km wokół.
		double[] c = bp;
		for (int j = -50; j <= 50 && c != null; j++) {
			for (int i = -50; i <= 50; i++) {
				double x = c[0] + i * 100;
				double z = c[1] + j * 100;
				double h = m.landElevation(x, z);
				if (h > best) {
					best = h;
					bp = new double[] {x, z};
				}
			}
		}
		System.out.printf(Locale.ROOT, "Najwyższe Beskidy: x=%.0f z=%.0f (%.0f m)%n", bp[0], bp[1], best);
		return bp;
	}

	// ------------------------------------------------------------------ mapy

	static ColumnSample[][] samples(Frame frame, HabitatClassifier classifier, int[][] code) {
		int n = code.length;
		double mpp = frame.sideLength() / n;
		double x0 = frame.cx() - frame.sideLength() / 2;
		double z0 = frame.cz() - frame.sideLength() / 2;
		ColumnSample[][] s = new ColumnSample[n][n];
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = x0 + (i + 0.5) * mpp;
				double z = z0 + (j + 0.5) * mpp;
				ColumnSample c = frame.model().sample(x, z);
				s[j][i] = c;
				code[j][i] = classifier.classify(c, x, z);
			}
		});
		return s;
	}

	static void save(Frame frame, ColumnSample[][] s, int[][] code, HabitatClassifier classifier, Layer w, Path file) throws IOException {
		int n = code.length;
		double mpp = frame.sideLength() / n;
		double x0 = frame.cx() - frame.sideLength() / 2;
		double z0 = frame.cz() - frame.sideLength() / 2;
		BufferedImage img = new BufferedImage(n + LEGEND, n, BufferedImage.TYPE_INT_RGB);
		long[] countBiomes = new long[HabitatBiome.values().length];
		long[] countZones = new long[Zone.values().length];
		long[] countFertility = new long[4];
		double[][] dgw = new double[n][n];
		Fertility[][] fertilityGrid = new Fertility[n][n];
		if (w == Layer.DGW || w == Layer.FERTILITY) {
			IntStream.range(0, n).parallel().forEach(j -> {
				for (int i = 0; i < n; i++) {
					double x = x0 + (i + 0.5) * mpp;
					double z = z0 + (j + 0.5) * mpp;
					dgw[j][i] = classifier.dgw(s[j][i], x, z);
					fertilityGrid[j][i] = classifier.fertility(s[j][i], x, z);
				}
			});
		}
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				ColumnSample c = s[j][i];
				int k = code[j][i];
				HabitatBiome b = Habitat.biome(k);
				Zone st = Habitat.zone(k);
				countBiomes[b.ordinal()]++;
				countZones[st.ordinal()]++;
				double shade = shade(s, i, j, mpp);
				int rgb;
				switch (w) {
					case BIOMES -> rgb = LandscapePreview.scale(BIOME_COLOR[b.ordinal()], b.isWater() ? 1.0 : shade);
					case ZONES -> {
						if (st == Zone.NONE) {
							int base = LandscapePreview.mixRgb(BIOME_COLOR[b.ordinal()], 0xFFFFFF, b.isWater() ? 0.3 : 0.55);
							rgb = LandscapePreview.scale(base, b.isWater() ? 1.0 : shade);
						} else {
							rgb = ZONE_COLOR.get(st);
						}
					}
					case DGW -> rgb = c.hasWater() ? 0x203050 : LandscapePreview.scale(dgwColor(dgw[j][i]), shade);
					case FERTILITY -> {
						if (c.hasWater()) {
							rgb = 0x203050;
						} else {
							countFertility[fertilityGrid[j][i].ordinal()]++;
							rgb = LandscapePreview.scale(FERTILITY_COLOR[fertilityGrid[j][i].ordinal()], shade);
						}
					}
					default -> throw new IllegalStateException();
				}
				img.setRGB(i, j, rgb);
			}
		}
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x202020));
		g.fillRect(n, 0, LEGEND, n);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		g.setColor(Color.WHITE);
		String scale = frame.model().scale() == LandscapeScale.REALISTIC ? "REAL" : "GAMEPLAY";
		g.drawString(frame.name() + " (" + scale + ")", n + 8, 18);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.drawString(String.format(Locale.ROOT, "%.0f m, %.2f m/px, %s", frame.sideLength(), mpp, w.name().toLowerCase(Locale.ROOT)),
				n + 8, 34);
		int y = 54;
		long all = (long) n * n;
		switch (w) {
			case BIOMES -> {
				for (int b : sortedList(countBiomes)) {
					if (countBiomes[b] == 0 || y > n - 10) {
						continue;
					}
					y = entry(g, n, y, BIOME_COLOR[b], String.format(Locale.ROOT, "%s %.1f%%", HabitatBiome.of(b).id(),
							100.0 * countBiomes[b] / all));
				}
			}
			case ZONES -> {
				for (int st : sortedList(countZones)) {
					if (countZones[st] == 0 || st == 0 || y > n - 10) {
						continue;
					}
					y = entry(g, n, y, ZONE_COLOR.get(Zone.of(st)), String.format(Locale.ROOT, "%s %.2f%%",
							Zone.of(st).id(), 100.0 * countZones[st] / all));
				}
				y = entry(g, n, y + 6, 0xA0A0A0, "bez strefy: kolor biomu (rozjaśniony)");
			}
			case DGW -> {
				double[] thresholds = {0, 0.3, 0.5, 0.8, 2, 4, 8, 12};
				for (double pr : thresholds) {
					y = entry(g, n, y, dgwColor(pr), String.format(Locale.ROOT, "DGW %.1f m", pr));
				}
			}
			case FERTILITY -> {
				long land = countFertility[0] + countFertility[1] + countFertility[2] + countFertility[3];
				for (Fertility t : Fertility.values()) {
					y = entry(g, n, y, FERTILITY_COLOR[t.ordinal()], String.format(Locale.ROOT, "%s %.1f%% lądu", t.name(),
							100.0 * countFertility[t.ordinal()] / Math.max(1, land)));
				}
			}
			default -> {
			}
		}
		g.dispose();
		ImageIO.write(img, "png", file.toFile());
		System.out.println("Zapisano " + file.getFileName());
	}

	private static int entry(Graphics2D g, int n, int y, int rgb, String text) {
		g.setColor(new Color(rgb));
		g.fillRect(n + 8, y - 10, 14, 12);
		g.setColor(Color.WHITE);
		g.drawString(text, n + 28, y);
		return y + 16;
	}

	private static int[] sortedList(long[] count) {
		return IntStream.range(0, count.length).boxed().sorted((a, b) -> Long.compare(count[b], count[a]))
				.mapToInt(Integer::intValue).toArray();
	}

	static final int[] FERTILITY_COLOR = {0xE8D878, 0xB8C860, 0x78A850, 0x2F7A3A};

	static int dgwColor(double d) {
		double[][] stops = {{0, 0x10306A}, {0.5, 0x2A6FB8}, {0.8, 0x4FB0C8}, {2, 0x8FD08A}, {4, 0xE0D070},
				{12, 0xB06A30}};
		for (int k = 1; k < stops.length; k++) {
			if (d <= stops[k][0]) {
				double t = (d - stops[k - 1][0]) / (stops[k][0] - stops[k - 1][0]);
				return LandscapePreview.mixRgb((int) stops[k - 1][1], (int) stops[k][1], Math.clamp(t, 0, 1));
			}
		}
		return 0xB06A30;
	}

	/** Cieniowanie rzeźby (słabe, żeby kolory zostały czytelne). */
	private static double shade(ColumnSample[][] s, int i, int j, double mpp) {
		int n = s.length;
		double dzdx = (s[j][Math.min(i + 1, n - 1)].surface() - s[j][Math.max(i - 1, 0)].surface()) / (2 * mpp);
		double dzdz = (s[Math.min(j + 1, n - 1)][i].surface() - s[Math.max(j - 1, 0)][i].surface()) / (2 * mpp);
		double nx = -dzdx;
		double nz = -dzdz;
		double len = Math.sqrt(nx * nx + 1 + nz * nz);
		double lambert = (nx * -0.55 + 0.65 + nz * -0.52) / len;
		return Math.clamp(0.55 + 0.6 * lambert, 0.7, 1.12);
	}

	// ------------------------------------------------------------------ pola regionalne

	/** Mapy O, P i zasięgów (buk, jodła, świerk) 2000 × 2000 km, 2,5 km na piksel. */
	static void regionalMaps(LandscapeModel m, Path out) throws IOException {
		int n = 800;
		double sideLength = 2_000_000;
		double mpp = sideLength / n;
		double[][] o = new double[n][n];
		double[][] p = new double[n][n];
		boolean[][] sea = new boolean[n][n];
		// Bez pełnych próbek: w rozrzuconych punktach budowałyby kafle sieci rzecznej (minuty zamiast sekund).
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = -sideLength / 2 + (i + 0.5) * mpp;
				double z = -sideLength / 2 + (j + 0.5) * mpp;
				ColumnSample.Region r = m.region(x, z);
				o[j][i] = r.oceanicity();
				p[j][i] = r.mountainInfluence();
				sea[j][i] = m.coastDistance(x, z) < 0;
			}
		});
		for (int layer = 0; layer < 3; layer++) {
			BufferedImage img = new BufferedImage(n + LEGEND, n, BufferedImage.TYPE_INT_RGB);
			for (int j = 0; j < n; j++) {
				for (int i = 0; i < n; i++) {
					int rgb;
					if (sea[j][i]) {
						rgb = 0x1E3A5A;
					} else if (layer == 0) {
						rgb = LandscapePreview.mixRgb(0xC86A30, 0x2A80C8, Math.clamp(o[j][i], 0, 1));
					} else if (layer == 1) {
						rgb = LandscapePreview.mixRgb(0xE8E0C0, 0x5A3A20, Math.clamp(p[j][i], 0, 1));
					} else {
						boolean beech = SpeciesRanges.beech(o[j][i], p[j][i]);
						boolean spruce = SpeciesRanges.spruce(o[j][i], p[j][i]);
						boolean fir = SpeciesRanges.fir(p[j][i], 0, null);
						rgb = fir ? 0x205040 : beech && spruce ? 0x5A8A4A : beech ? 0x3FA040 : spruce ? 0x2A6AA0 : 0xD8C890;
					}
					img.setRGB(i, j, rgb);
				}
			}
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setColor(new Color(0x202020));
			g.fillRect(n, 0, LEGEND, n);
			g.setColor(Color.WHITE);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
			String[] title = {"O: oceaniczność", "P: podgórskość", "zasięgi gatunków"};
			g.drawString(title[layer] + " 2000 km (REAL)", n + 8, 18);
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
			g.drawString("zachód = −X (lewo), północ = −Z (góra)", n + 8, 34);
			int y = 54;
			if (layer < 2) {
				for (double v = 0; v <= 1.0001; v += 0.1) {
					int rgb = layer == 0 ? LandscapePreview.mixRgb(0xC86A30, 0x2A80C8, v)
							: LandscapePreview.mixRgb(0xE8E0C0, 0x5A3A20, v);
					y = entry(g, n, y, rgb, String.format(Locale.ROOT, "%.1f", v));
				}
			} else {
				y = entry(g, n, y, 0x205040, "jodła (P ≥ 0,5), też buk i świerk");
				y = entry(g, n, y, 0x5A8A4A, "buk i świerk");
				y = entry(g, n, y, 0x3FA040, "buk (O ≥ 0,4 lub P ≥ 0,4)");
				y = entry(g, n, y, 0x2A6AA0, "świerk (O < 0,3)");
				y = entry(g, n, y, 0xD8C890, "pas bez buka i świerka");
			}
			y = entry(g, n, y + 6, 0x1E3A5A, "sea");
			g.dispose();
			String[] file = {"m2_map_O_2000km.png", "m2_map_P_2000km.png", "m2_map_species_ranges_2000km.png"};
			ImageIO.write(img, "png", out.resolve(file[layer]).toFile());
			System.out.println("Zapisano " + file[layer]);
		}
	}

	// ------------------------------------------------------------------ udziały

	/**
	 * Udziały biomów i stref w trybie N (jak {@code BiomeSharesTest}: 200 tys. kolumn na skalę, 3 ziarna)
	 * do plików CSV: m2_udzialy_biomow.csv i m2_udzialy_stref.csv (procent wszystkich kolumn, z wodą).
	 */
	static void sharesCsv(Path out) throws IOException {
		BiomeSharesTest.Shares r = new BiomeSharesTest.Shares();
		BiomeSharesTest.Shares g = new BiomeSharesTest.Shares();
		for (long seed : new long[] {20260927L, 1L, 2L}) {
			r.add(BiomeSharesTest.computeShares(seed, LandscapeScale.REALISTIC, HabitatClassifier.Mode.NATURAL, 1_000_000, 1_042));
			g.add(BiomeSharesTest.computeShares(seed, LandscapeScale.GAMEPLAY, HabitatClassifier.Mode.NATURAL, 30_000, 1_042));
		}
		long rs = 0;
		long gs = 0;
		for (int i = 0; i < r.biomeOutwashPlain.length; i++) {
			rs += r.biomeOutwashPlain[i];
			gs += g.biomeOutwashPlain[i];
		}
		StringBuilder sb = new StringBuilder("id,nazwa,grupa,real_proc,real_sandr_proc,rozgrywka_proc,rozgrywka_sandr_proc\n");
		for (HabitatBiome b : HabitatBiome.values()) {
			int i = b.ordinal();
			sb.append(String.format(Locale.ROOT, "%s,%s,%s,%.3f,%.3f,%.3f,%.3f\n", b.id(), b.name(),
					b.group().name().toLowerCase(Locale.ROOT), 100.0 * r.biome[i] / r.columns, 100.0 * r.biomeOutwashPlain[i] / Math.max(1, rs),
					100.0 * g.biome[i] / g.columns, 100.0 * g.biomeOutwashPlain[i] / Math.max(1, gs)));
		}
		sb.append(String.format(Locale.ROOT, "lesistosc_ladu,,,%.2f,%.2f,%.2f,%.2f\n", 100 * r.forestCover(),
				100 * r.forestCoverOutwashPlain(), 100 * g.forestCover(), 100 * g.forestCoverOutwashPlain()));
		sb.append(String.format(Locale.ROOT, "bory_w_lesie_wnetrza_sandru,,,,%.2f,,%.2f\n", 100 * r.pineShareOutwashPlain(),
				100 * g.pineShareOutwashPlain()));
		sb.append(String.format(Locale.ROOT, "bory_w_lesie_calego_sandru,,,,%.2f,,%.2f\n", 100 * r.pineShareOutwashPlainAll(),
				100 * g.pineShareOutwashPlainAll()));
		Files.writeString(out.resolve("m2_biome_shares.csv"), sb.toString());
		StringBuilder st = new StringBuilder("strefa,real_proc,rozgrywka_proc\n");
		for (Zone s : Zone.values()) {
			st.append(String.format(Locale.ROOT, "%s,%.3f,%.3f\n", s.id(), 100.0 * r.zone[s.ordinal()] / r.columns,
					100.0 * g.zone[s.ordinal()] / g.columns));
		}
		Files.writeString(out.resolve("m2_zone_shares.csv"), st.toString());
		System.out.println("Saved m2_biome_shares.csv, m2_zone_shares.csv");
	}

	// ------------------------------------------------------------------ przekroje dolin

	/**
	 * Przekroje dolin klas A, B i C: pas kolorów biomu (góra) i strefy (dół) w funkcji odległości od
	 * koryta wzdłuż osi X, z profilem terenu.
	 */
	static void sections(LandscapeModel m, HabitatClassifier classifier, double[][] sites, Path file) throws IOException {
		String[] names = {"A: duża rzeka (1500 m od koryta)", "B: mała rzeka (150 m)", "C: potok górski (150 m)"};
		double[] range = {1_500, 150, 150};
		int w = 1_000;
		int row = 140;
		BufferedImage img = new BufferedImage(w, row * 3, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x202020));
		g.fillRect(0, 0, w, row * 3);
		for (int r = 0; r < 3; r++) {
			double[] p = sites[r];
			if (p == null) {
				continue;
			}
			// Przekrój w poprzek koryta: od brzegu w stronę rosnącego d (gradient d z różnic ±1 m), więc
			// pas idzie prostopadle do koryta i nie przecina go drugi raz w zakolu.
			double x = p[0];
			double z = p[1];
			double[] direction = {1, 0};
			for (int q = 0; q < 2_000 && m.sample(x, z).hasWater(); q++) {
				x += 0.5;
			}
			double step = range[r] / w;
			double[] hh = new double[w];
			double hmin = Double.MAX_VALUE;
			double hmax = -Double.MAX_VALUE;
			for (int i = 0; i < w; i++) {
				double gx = m.sample(x + 1, z).waters().channelDist() - m.sample(x - 1, z).waters().channelDist();
				double gz = m.sample(x, z + 1).waters().channelDist() - m.sample(x, z - 1).waters().channelDist();
				double len = Math.hypot(gx, gz);
				if (Double.isFinite(len) && len > 1e-6) {
					direction = new double[] {gx / len, gz / len};
				}
				x += direction[0] * step;
				z += direction[1] * step;
				double px = x;
				double pz = z;
				ColumnSample c = m.sample(px, pz);
				int code = classifier.classify(c, px, pz);
				hh[i] = c.surface();
				hmin = Math.min(hmin, hh[i]);
				hmax = Math.max(hmax, hh[i]);
				g.setColor(new Color(BIOME_COLOR[Habitat.biome(code).ordinal()]));
				g.fillRect(i, r * row + 20, 1, 50);
				Zone st = Habitat.zone(code);
				g.setColor(new Color(st == Zone.NONE ? 0x303030 : ZONE_COLOR.get(st)));
				g.fillRect(i, r * row + 72, 1, 20);
			}
			g.setColor(Color.WHITE);
			for (int i = 1; i < w; i++) {
				double t0 = (hh[i - 1] - hmin) / Math.max(1, hmax - hmin);
				double t1 = (hh[i] - hmin) / Math.max(1, hmax - hmin);
				g.drawLine(i - 1, r * row + 134 - (int) (t0 * 38), i, r * row + 134 - (int) (t1 * 38));
			}
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
			g.drawString(String.format(Locale.ROOT, "%s; teren %.1f–%.1f m (biała linia); góra biom, dół strefa", names[r],
					hmin, hmax), 6, r * row + 14);
		}
		g.dispose();
		ImageIO.write(img, "png", file.toFile());
		System.out.println("Zapisano " + file.getFileName());
	}
}
