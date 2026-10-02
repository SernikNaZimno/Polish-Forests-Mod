package pl.polishforests.worldgen.landscape;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Renderuje model krajobrazu do PNG bez uruchamiania gry. Uruchamiane zadaniem Gradle
 * {@code landscapePreview}. Argumenty: katalog wyjściowy, ziarno.
 */
public final class LandscapePreview {
	private LandscapePreview() {
	}

	public static void main(String[] args) throws IOException {
		Path out = Path.of(args.length > 0 ? args[0] : "build/preview");
		long seed = args.length > 1 ? Long.parseLong(args[1]) : 20260927L;
		Files.createDirectories(out);
		// Trzeci argument „siedliska”: tylko podgląd siedlisk M2 (kadry, mapy, przekroje, CSV udziałów).
		boolean habitatsOnly = args.length > 2 && args[2].equals("habitats");
		HabitatPreview.main(out, seed);
		if (habitatsOnly) {
			return;
		}
		LandscapeModel model = new LandscapeModel(seed, 1.0);

		// Przegląd: 400 × 400 km, 500 m na piksel, kolory typów krajobrazu.
		render(model, out.resolve("overview_types_400km.png"), 0, 0, 800, 500, true);
		// Ten sam obszar w kolorach wysokości.
		render(model, out.resolve("overview_elevation_400km.png"), 0, 0, 800, 500, false);
		// Zbliżenia na charakterystyczne miejsca wyszukane automatycznie.
		double[] outwashPlain = find(model, LandscapeType.OUTWASH_PLAIN, true);
		double[] moraine = find(model, LandscapeType.MORAINE_PLATEAU, true);
		double[] plain = findRiver(model);
		double[] mountains = findInterior(model, LandscapeType.BESKIDS);
		if (outwashPlain != null) {
			render(model, out.resolve("zoom_outwash_plain_20km.png"), outwashPlain[0], outwashPlain[1], 800, 25, false);
		}
		if (moraine != null) {
			render(model, out.resolve("zoom_moraine_plateau_20km.png"), moraine[0], moraine[1], 800, 25, false);
		}
		if (plain != null) {
			render(model, out.resolve("zoom_river_valley_40km.png"), plain[0], plain[1], 800, 50, false);
		}
		if (mountains != null) {
			render(model, out.resolve("zoom_beskids_40km.png"), mountains[0], mountains[1], 800, 50, false);
			render(model, out.resolve("zoom_beskids_10km.png"), mountains[0], mountains[1], 800, 12.5, false);
			printStats(model, "Beskids", mountains[0], mountains[1], 20_000);
		}
		// Skala przyjazna rozgrywce: przegląd 20 x 20 km i zbliżenia.
		LandscapeModel g = new LandscapeModel(seed, LandscapeScale.GAMEPLAY, 1.0);
		render(g, out.resolve("gameplay_types_20km.png"), 0, 0, 800, 25, true);
		render(g, out.resolve("gameplay_elevation_20km.png"), 0, 0, 800, 25, false);
		double[] gm = findInterior(g, LandscapeType.BESKIDS);
		if (gm != null) {
			render(g, out.resolve("gameplay_beskids_4km.png"), gm[0], gm[1], 800, 5, false);
			printStats(g, "Beskidy (rozgrywka)", gm[0], gm[1], 3_000);
		}
		double[] gl = find(g, LandscapeType.MORAINE_PLATEAU, true);
		if (gl != null) {
			render(g, out.resolve("gameplay_moraine_plateau_4km.png"), gl[0], gl[1], 800, 5, false);
		}
		// Rzeki, doliny i morze.
		double[] coast = RiverNetworkTest.find(model, c -> c.type() == LandscapeType.COASTLAND, 2_000);
		if (coast != null) {
			render(model, out.resolve("coast_40km.png"), coast[0], coast[1], 800, 50, false);
		}
		double[] lagoon = RiverNetworkTest.find(model,
				c -> c.waterKind() == WaterKind.SEA && c.type() == LandscapeType.COASTLAND, 1_000);
		if (lagoon != null) {
			render(model, out.resolve("lagoon_20km.png"), lagoon[0], lagoon[1], 800, 25, false);
			render(model, out.resolve("coast_6km.png"), lagoon[0], lagoon[1], 800, 7.5, false);
		}
		double[] big = RiverNetworkTest.find(model, c -> c.waterKind() == WaterKind.RIVER && c.type().isLowland()
				&& c.surface() - c.waterLevel() < -2.5, 1_000);
		if (big != null) {
			render(model, out.resolve("lowland_river_6km.png"), big[0], big[1], 800, 7.5, false);
		}
		double[] oxbow = RiverNetworkTest.find(model, c -> c.waterKind() == WaterKind.OXBOW, 500);
		if (oxbow != null) {
			render(model, out.resolve("meanders_oxbows_6km.png"), oxbow[0], oxbow[1], 800, 7.5, false);
		}
		double[] stream = RiverNetworkTest.findStream(model);
		if (stream != null) {
			render(model, out.resolve("mountain_stream_3km.png"), stream[0], stream[1], 800, 3.75, false);
		}
		double[] gc = RiverNetworkTest.find(g, c -> c.type() == LandscapeType.COASTLAND, 100);
		if (gc != null) {
			render(g, out.resolve("gameplay_coast_4km.png"), gc[0], gc[1], 800, 5, false);
		}
		double[] gr = RiverNetworkTest.find(g, c -> c.waterKind() == WaterKind.RIVER && c.type().isLowland(), 50);
		if (gr != null) {
			render(g, out.resolve("gameplay_river_2km.png"), gr[0], gr[1], 800, 2.5, false);
		}
		System.out.println("Zapisano podglądy w " + out.toAbsolutePath());
	}

	/** Wypisuje zakres wysokości i rozkład nachyleń w kwadracie o boku {@code size} metrów. */
	static void printStats(LandscapeModel model, String name, double cx, double cz, double size) {
		int n = 400;
		double step = size / n;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		int[] slopeBins = new int[5];
		double[] edges = {5, 15, 30, 45};
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				double x = cx - size / 2 + i * step;
				double z = cz - size / 2 + j * step;
				double h = model.landElevation(x, z);
				min = Math.min(min, h);
				max = Math.max(max, h);
				double gx = (model.landElevation(x + 10, z) - model.landElevation(x - 10, z)) / 20;
				double gz = (model.landElevation(x, z + 10) - model.landElevation(x, z - 10)) / 20;
				double deg = Math.toDegrees(Math.atan(Math.sqrt(gx * gx + gz * gz)));
				int b = 0;
				while (b < edges.length && deg >= edges[b]) {
					b++;
				}
				slopeBins[b]++;
			}
		}
		double total = n * n;
		System.out.printf("%s: wysokość %.0f–%.0f m; nachylenia <5°: %.0f%%, 5–15°: %.0f%%, 15–30°: %.0f%%, 30–45°: %.0f%%, >45°: %.0f%%%n",
				name, min, max, 100 * slopeBins[0] / total, 100 * slopeBins[1] / total, 100 * slopeBins[2] / total,
				100 * slopeBins[3] / total, 100 * slopeBins[4] / total);
	}

	/** Szuka punktu z danym typem krajobrazu (opcjonalnie w jeziorze) na spirali od środka. */
	static double[] find(LandscapeModel model, LandscapeType type, boolean inLake) {
		for (int r = 0; r < 400; r++) {
			for (int k = 0; k < 24; k++) {
				double a = k * Math.PI / 12 + r * 0.37;
				double step = model.scale() == LandscapeScale.REALISTIC ? 2_500 : 150;
				double x = Math.cos(a) * r * step;
				double z = Math.sin(a) * r * step;
				ColumnSample s = model.sample(x, z);
				if (s.type() == type && (!inLake || s.waterKind().isLake())) {
					System.out.printf("%s: x=%.0f z=%.0f (%.0f m n.p.m.)%n", type, x, z, s.surface());
					return new double[] {x, z};
				}
			}
		}
		System.out.println("Nie znaleziono: " + type);
		return null;
	}

	/** Szuka punktu, w którym dany typ ma wagę bliską 1 w promieniu 10 km. */
	static double[] findInterior(LandscapeModel model, LandscapeType type) {
		for (int r = 0; r < 800; r++) {
			for (int k = 0; k < 24; k++) {
				double a = k * Math.PI / 12 + r * 0.37;
				double unit = model.scale() == LandscapeScale.REALISTIC ? 5_000 : 200;
				double x = Math.cos(a) * r * unit;
				double z = Math.sin(a) * r * unit;
				boolean ok = true;
				for (int q = 0; q < 9 && ok; q++) {
					double px = x + ((q % 3) - 1) * 2 * unit;
					double pz = z + ((q / 3) - 1) * 2 * unit;
					ok = model.typeWeights(px, pz)[type.ordinal()] > 0.98;
				}
				if (ok) {
					System.out.printf("%s (wnętrze): x=%.0f z=%.0f%n", type, x, z);
					return new double[] {x, z};
				}
			}
		}
		System.out.println("Nie znaleziono wnętrza: " + type);
		return find(model, type, false);
	}

	/** Szuka koryta wielkiej rzeki. */
	static double[] findRiver(LandscapeModel model) {
		for (int r = 0; r < 600; r++) {
			for (int k = 0; k < 48; k++) {
				double a = k * Math.PI / 24 + r * 0.37;
				double step = model.scale() == LandscapeScale.REALISTIC ? 1_500 : 60;
				double x = Math.cos(a) * r * step;
				double z = Math.sin(a) * r * step;
				ColumnSample s = model.sample(x, z);
				if (s.waterKind() == WaterKind.RIVER) {
					System.out.printf("RZEKA: x=%.0f z=%.0f (lustro %d m n.p.m.)%n", x, z, s.waterLevel());
					return new double[] {x, z};
				}
			}
		}
		System.out.println("Nie znaleziono rzeki");
		return null;
	}

	static void render(LandscapeModel model, Path file, double cx, double cz, int size, double metersPerPx,
			boolean typeTint) throws IOException {
		double[][] h = new double[size][size];
		ColumnSample[][] s = new ColumnSample[size][size];
		double x0 = cx - size * metersPerPx / 2;
		double z0 = cz - size * metersPerPx / 2;
		IntStream.range(0, size).parallel().forEach(j -> {
			for (int i = 0; i < size; i++) {
				ColumnSample c = model.sample(x0 + i * metersPerPx, z0 + j * metersPerPx);
				s[j][i] = c;
				h[j][i] = c.surface();
			}
		});
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		// Przewyższenie tylko dla widoków przeglądowych; zbliżenia w prawdziwej skali.
		double exaggeration = metersPerPx > 100 ? 6.0 : 1.0;
		double lx = -0.55;
		double ly = 0.65;
		double lz = -0.52;
		for (int j = 0; j < size; j++) {
			for (int i = 0; i < size; i++) {
				ColumnSample c = s[j][i];
				double dzdx = (h[j][Math.min(i + 1, size - 1)] - h[j][Math.max(i - 1, 0)]) / (2 * metersPerPx);
				double dzdz = (h[Math.min(j + 1, size - 1)][i] - h[Math.max(j - 1, 0)][i]) / (2 * metersPerPx);
				double nx = -dzdx * exaggeration;
				double nz = -dzdz * exaggeration;
				double len = Math.sqrt(nx * nx + 1 + nz * nz);
				double lambert = (nx * lx + ly + nz * lz) / len;
				double shade = Math.clamp(0.35 + 0.95 * lambert, 0.2, 1.25);
				int rgb;
				if (c.hasWater()) {
					rgb = switch (c.waterKind()) {
						case RIVER -> 0x3A78C0;
						case SEA -> c.type() == LandscapeType.SEA ? scale(0x1E4A7A, Math.clamp(1.0 + c.surface() / 80.0, 0.5, 1.0)) : 0x2E6AA8;
						case OXBOW -> 0x2F8FB0;
						default -> 0x24508F;
					};
				} else {
					int base = typeTint ? typeColor(c.type()) : heightColor(c.surface());
					if (!typeTint && c.substrate() == Substrate.PEAT) {
						base = 0x5B4A32;
					}
					rgb = scale(base, shade);
				}
				img.setRGB(i, j, rgb);
			}
		}
		ImageIO.write(img, "png", file.toFile());
		System.out.println("Zapisano " + file.getFileName());
	}

	static int typeColor(LandscapeType t) {
		return switch (t) {
			case OUTWASH_PLAIN -> 0xD8C98E;
			case MORAINE_PLATEAU -> 0x8FB36A;
			case OLD_GLACIAL_PLAIN -> 0xB9C77F;
			case FOOTHILLS -> 0xA88A5C;
			case BESKIDS -> 0x6E5A48;
			case COASTLAND -> 0xEEE2B0;
			case SEA -> 0x2A5A8A;
		};
	}

	static int heightColor(double m) {
		double[][] stops = {{0, 0x4F8A4B}, {150, 0x9BBF6A}, {300, 0xD9CF8A}, {600, 0xB88A58}, {1200, 0x8A6A52},
				{1800, 0xF0F0F0}};
		for (int k = 1; k < stops.length; k++) {
			if (m <= stops[k][0]) {
				double t = (m - stops[k - 1][0]) / (stops[k][0] - stops[k - 1][0]);
				return mixRgb((int) stops[k - 1][1], (int) stops[k][1], Math.clamp(t, 0, 1));
			}
		}
		return 0xF0F0F0;
	}

	static int mixRgb(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	static int scale(int rgb, double f) {
		int r = (int) Math.clamp(((rgb >> 16) & 255) * f, 0, 255);
		int g = (int) Math.clamp(((rgb >> 8) & 255) * f, 0, 255);
		int b = (int) Math.clamp((rgb & 255) * f, 0, 255);
		return (r << 16) | (g << 8) | b;
	}
}
