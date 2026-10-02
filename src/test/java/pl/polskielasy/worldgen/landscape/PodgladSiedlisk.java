package pl.polskielasy.worldgen.landscape;

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
import pl.polskielasy.worldgen.habitat.Biom;
import pl.polskielasy.worldgen.habitat.BiomeSharesTest;
import pl.polskielasy.worldgen.habitat.Klasyfikator;
import pl.polskielasy.worldgen.habitat.Siedlisko;
import pl.polskielasy.worldgen.habitat.Strefa;
import pl.polskielasy.worldgen.habitat.Trofia;
import pl.polskielasy.worldgen.habitat.Zasiegi;

/**
 * Podgląd siedlisk M2 (docs/03-m2-biomy.md §12.2): mapy biomów z legendą, stref, DGW, trofii, pól
 * regionalnych O i P oraz zasięgów, kadry z planu, przekroje dolin i plik CSV z udziałami biomów.
 * Wołany z {@link LandscapePreview}.
 */
final class PodgladSiedlisk {
	private PodgladSiedlisk() {
	}

	/** Warstwa mapy. */
	enum Warstwa {
		BIOMY, STREFY, DGW, TROFIA
	}

	private static final int LEGENDA = 300;

	/** Kolory biomów (kolejność enuma). */
	static final int[] KOLOR_BIOMU = {
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

	static final Map<Strefa, Integer> KOLOR_STREFY = new EnumMap<>(Strefa.class);

	static {
		if (KOLOR_BIOMU.length != Biom.values().length) {
			throw new IllegalStateException("brak kolorów biomów");
		}
		int[] k = {0, 0x3A78C0, 0x2A9AB0, 0x2FD0C0, 0xC0D040, 0xE0E040, 0xF0E0A0, 0x90E040, 0xFF8040, 0xFF40A0,
				0x40FF80, 0xA040FF, 0xB0B0B0, 0xFFB0FF, 0x00FFFF, 0x804020, 0x206040, 0xFFFFFF, 0xFFF080, 0xC06030,
				0xE0A060, 0x404040, 0xFF2020};
		for (Strefa s : Strefa.values()) {
			KOLOR_STREFY.put(s, k[s.ordinal()]);
		}
	}

	/** Kadr: środek, bok w metrach i rozdzielczość. */
	record Kadr(String nazwa, LandscapeModel model, double cx, double cz, double bok) {
	}

	// ------------------------------------------------------------------ program

	static void main(Path out, long seed) throws IOException {
		LandscapeModel real = new LandscapeModel(seed, 1.0);
		LandscapeModel gry = new LandscapeModel(seed, LandscapeScale.GAMEPLAY, 1.0);
		Klasyfikator kr = new Klasyfikator(seed, LandscapeScale.REALISTIC, Klasyfikator.Tryb.N);
		Klasyfikator kg = new Klasyfikator(seed, LandscapeScale.GAMEPLAY, Klasyfikator.Tryb.N);
		List<Kadr> kadry = new ArrayList<>();
		double[] p;
		p = szukajKlasy(real, kr, true, 1_000);
		double[] duza = p;
		dodaj(kadry, "dolina_duzej_rzeki_2km", real, p, 2_000);
		p = szukajKlasy(gry, kg, true, 50);
		dodaj(kadry, "rozgrywka_dolina_duzej_rzeki_1km", gry, p, 1_000);
		p = szukajMalejRzeki(real, 500);
		double[] mala = p;
		dodaj(kadry, "mala_rzeka_500m", real, p, 500);
		p = RiverNetworkTest.findStream(real);
		double[] potok = p;
		dodaj(kadry, "potok_gorski_1km", real, p, 1_000);
		p = szukajJeziora(real, 700);
		dodaj(kadry, "jezioro_rynnowe_1km", real, p, 1_000);
		p = RiverNetworkTest.find(real, c -> c.waterKind() == WaterKind.KETTLE && c.type() == LandscapeType.SANDR, 400);
		dodaj(kadry, "oczko_sandr_500m", real, p, 500);
		p = RiverNetworkTest.find(real, c -> c.waterKind() == WaterKind.SEA && c.teren().coastD() >= 0
				&& c.waterLevel() - c.surface() > 2, 1_000);
		dodaj(kadry, "wybrzeze_zalew_3km", real, p, 3_000);
		p = LandscapePreview.findInterior(real, LandscapeType.BESKIDY);
		dodaj(kadry, "beskidy_10km", real, p, 10_000);
		double[] wys = szukajWysokich(real);
		dodaj(kadry, "beskidy_wysokie_10km", real, wys, 10_000);
		p = LandscapePreview.findInterior(real, LandscapeType.SANDR);
		dodaj(kadry, "sandr_20km", real, p, 20_000);
		p = LandscapePreview.findInterior(real, LandscapeType.WYSOCZYZNA_MORENOWA);
		dodaj(kadry, "wysoczyzna_20km", real, p, 20_000);
		dodaj(kadry, "rozgrywka_20km", gry, new double[] {0, 0}, 20_000);
		for (Kadr kadr : kadry) {
			Klasyfikator kl = kadr.model() == real ? kr : kg;
			int[][] kod = new int[800][800];
			ColumnSample[][] s = probki(kadr, kl, kod);
			zapisz(kadr, s, kod, kl, Warstwa.BIOMY, out.resolve("m2_" + kadr.nazwa() + "_biomy.png"));
			zapisz(kadr, s, kod, kl, Warstwa.STREFY, out.resolve("m2_" + kadr.nazwa() + "_strefy.png"));
			if (kadr.nazwa().startsWith("dolina") || kadr.nazwa().startsWith("beskidy_10") || kadr.nazwa().startsWith("sandr")
					|| kadr.nazwa().startsWith("wysoczyzna") || kadr.nazwa().startsWith("jezioro")) {
				zapisz(kadr, s, kod, kl, Warstwa.DGW, out.resolve("m2_" + kadr.nazwa() + "_dgw.png"));
				zapisz(kadr, s, kod, kl, Warstwa.TROFIA, out.resolve("m2_" + kadr.nazwa() + "_trofia.png"));
			}
		}
		// Maska lasu w trybie D (S8 kalibruje P_las; tu tylko podgląd mechanizmu).
		Klasyfikator kd = new Klasyfikator(seed, LandscapeScale.REALISTIC, Klasyfikator.Tryb.D);
		p = LandscapePreview.find(real, LandscapeType.SANDR, false);
		if (p != null) {
			Kadr kadr = new Kadr("maska_lasu_D_50km", real, p[0], p[1], 50_000);
			int[][] kod = new int[800][800];
			ColumnSample[][] s = probki(kadr, kd, kod);
			zapisz(kadr, s, kod, kd, Warstwa.BIOMY, out.resolve("m2_maska_lasu_D_50km_biomy.png"));
		}
		mapyRegionalne(real, out);
		udzialyCsv(out);
		przekroje(real, kr, new double[][] {duza, mala, potok}, out.resolve("m2_przekroje_dolin.png"));
	}

	private static void dodaj(List<Kadr> kadry, String nazwa, LandscapeModel m, double[] p, double bok) {
		if (p == null) {
			System.out.println("Podgląd siedlisk: nie znaleziono kadru " + nazwa);
			return;
		}
		kadry.add(new Kadr(nazwa, m, p[0], p[1], bok));
	}

	// ------------------------------------------------------------------ wyszukiwanie kadrów

	/** Rzeka klasy A (duża nizinna): koryto rzędu 3 na nizinie, szerokie. */
	static double[] szukajKlasy(LandscapeModel m, Klasyfikator k, boolean duza, double krok) {
		double minW = m.scale().channel() * 60;
		return RiverNetworkTest.find(m, c -> c.waterKind() == WaterKind.RIVER && c.type().isLowland()
				&& c.wody().rzad() == 3 && c.wody().szerKoryta() >= minW && c.wody().spadek() <= 3, krok);
	}

	/** Mała rzeka nizinna (klasa B): koryto 3–15 m (1:1) z dnem doliny. */
	static double[] szukajMalejRzeki(LandscapeModel m, double krok) {
		double ch = m.scale().channel();
		return RiverNetworkTest.find(m, c -> c.waterKind() == WaterKind.RIVER && c.type() == LandscapeType.WYSOCZYZNA_MORENOWA
				&& c.wody().szerKoryta() / ch >= 4 && c.wody().szerKoryta() / ch < 15 && c.wody().spadek() <= 2
				&& c.wody().polSzerDna() > 60, krok);
	}

	/** Brzeg jeziora rynnowego na glinie (pierścienie olsu i łozowiska), z dala od dolin rzek. */
	static double[] szukajJeziora(LandscapeModel m, double krok) {
		for (int r = 0; r < 800; r++) {
			int n = Math.max(12, r * 2);
			for (int k = 0; k < n; k++) {
				double a = k * (2 * Math.PI / n) + r * 0.37;
				double x = Math.cos(a) * r * krok;
				double z = Math.sin(a) * r * krok;
				ColumnSample c = m.sample(x, z);
				if (c.hasWater() || c.substrate() != Substrate.GLACIAL_TILL || c.wody().odlKoryta() < 400
						|| c.wody().rodzajStojacej() != ColumnSample.RodzajStojacej.JEZIORO_RYNNOWE || c.wody().s() > 20
						|| c.wody().promienStojacej() < 150) {
					continue;
				}
				boolean daleko = true;
				for (int q = 0; q < 4 && daleko; q++) {
					double b = q * Math.PI / 2;
					daleko = m.sample(x + 300 * Math.cos(b), z + 300 * Math.sin(b)).wody().odlKoryta() > 250;
				}
				if (daleko) {
					return new double[] {x, z};
				}
			}
		}
		return null;
	}

	/** Najwyższe Beskidy w promieniu 1500 km (piętra górne). */
	static double[] szukajWysokich(LandscapeModel m) {
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

	static ColumnSample[][] probki(Kadr kadr, Klasyfikator kl, int[][] kod) {
		int n = kod.length;
		double mpp = kadr.bok() / n;
		double x0 = kadr.cx() - kadr.bok() / 2;
		double z0 = kadr.cz() - kadr.bok() / 2;
		ColumnSample[][] s = new ColumnSample[n][n];
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = x0 + (i + 0.5) * mpp;
				double z = z0 + (j + 0.5) * mpp;
				ColumnSample c = kadr.model().sample(x, z);
				s[j][i] = c;
				kod[j][i] = kl.klasyfikuj(c, x, z);
			}
		});
		return s;
	}

	static void zapisz(Kadr kadr, ColumnSample[][] s, int[][] kod, Klasyfikator kl, Warstwa w, Path plik) throws IOException {
		int n = kod.length;
		double mpp = kadr.bok() / n;
		double x0 = kadr.cx() - kadr.bok() / 2;
		double z0 = kadr.cz() - kadr.bok() / 2;
		BufferedImage img = new BufferedImage(n + LEGENDA, n, BufferedImage.TYPE_INT_RGB);
		long[] liczBiom = new long[Biom.values().length];
		long[] liczStrefa = new long[Strefa.values().length];
		long[] liczTrofia = new long[4];
		double[][] dgw = new double[n][n];
		Trofia[][] trof = new Trofia[n][n];
		if (w == Warstwa.DGW || w == Warstwa.TROFIA) {
			IntStream.range(0, n).parallel().forEach(j -> {
				for (int i = 0; i < n; i++) {
					double x = x0 + (i + 0.5) * mpp;
					double z = z0 + (j + 0.5) * mpp;
					dgw[j][i] = kl.dgw(s[j][i], x, z);
					trof[j][i] = kl.trofia(s[j][i], x, z);
				}
			});
		}
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				ColumnSample c = s[j][i];
				int k = kod[j][i];
				Biom b = Siedlisko.biom(k);
				Strefa st = Siedlisko.strefa(k);
				liczBiom[b.ordinal()]++;
				liczStrefa[st.ordinal()]++;
				double shade = cien(s, i, j, mpp);
				int rgb;
				switch (w) {
					case BIOMY -> rgb = LandscapePreview.scale(KOLOR_BIOMU[b.ordinal()], b.wodny() ? 1.0 : shade);
					case STREFY -> {
						if (st == Strefa.BRAK) {
							int base = LandscapePreview.mixRgb(KOLOR_BIOMU[b.ordinal()], 0xFFFFFF, b.wodny() ? 0.3 : 0.55);
							rgb = LandscapePreview.scale(base, b.wodny() ? 1.0 : shade);
						} else {
							rgb = KOLOR_STREFY.get(st);
						}
					}
					case DGW -> rgb = c.hasWater() ? 0x203050 : LandscapePreview.scale(kolorDgw(dgw[j][i]), shade);
					case TROFIA -> {
						if (c.hasWater()) {
							rgb = 0x203050;
						} else {
							liczTrofia[trof[j][i].ordinal()]++;
							rgb = LandscapePreview.scale(KOLOR_TROFII[trof[j][i].ordinal()], shade);
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
		g.fillRect(n, 0, LEGENDA, n);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		g.setColor(Color.WHITE);
		String skala = kadr.model().scale() == LandscapeScale.REALISTIC ? "REAL" : "GAMEPLAY";
		g.drawString(kadr.nazwa() + " (" + skala + ")", n + 8, 18);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.drawString(String.format(Locale.ROOT, "%.0f m, %.2f m/px, %s", kadr.bok(), mpp, w.name().toLowerCase(Locale.ROOT)),
				n + 8, 34);
		int y = 54;
		long all = (long) n * n;
		switch (w) {
			case BIOMY -> {
				for (int b : posortowane(liczBiom)) {
					if (liczBiom[b] == 0 || y > n - 10) {
						continue;
					}
					y = wpis(g, n, y, KOLOR_BIOMU[b], String.format(Locale.ROOT, "%s %.1f%%", Biom.of(b).id(),
							100.0 * liczBiom[b] / all));
				}
			}
			case STREFY -> {
				for (int st : posortowane(liczStrefa)) {
					if (liczStrefa[st] == 0 || st == 0 || y > n - 10) {
						continue;
					}
					y = wpis(g, n, y, KOLOR_STREFY.get(Strefa.of(st)), String.format(Locale.ROOT, "%s %.2f%%",
							Strefa.of(st).id(), 100.0 * liczStrefa[st] / all));
				}
				y = wpis(g, n, y + 6, 0xA0A0A0, "bez strefy: kolor biomu (rozjaśniony)");
			}
			case DGW -> {
				double[] progi = {0, 0.3, 0.5, 0.8, 2, 4, 8, 12};
				for (double pr : progi) {
					y = wpis(g, n, y, kolorDgw(pr), String.format(Locale.ROOT, "DGW %.1f m", pr));
				}
			}
			case TROFIA -> {
				long ladu = liczTrofia[0] + liczTrofia[1] + liczTrofia[2] + liczTrofia[3];
				for (Trofia t : Trofia.values()) {
					y = wpis(g, n, y, KOLOR_TROFII[t.ordinal()], String.format(Locale.ROOT, "%s %.1f%% lądu", t.name(),
							100.0 * liczTrofia[t.ordinal()] / Math.max(1, ladu)));
				}
			}
			default -> {
			}
		}
		g.dispose();
		ImageIO.write(img, "png", plik.toFile());
		System.out.println("Zapisano " + plik.getFileName());
	}

	private static int wpis(Graphics2D g, int n, int y, int rgb, String tekst) {
		g.setColor(new Color(rgb));
		g.fillRect(n + 8, y - 10, 14, 12);
		g.setColor(Color.WHITE);
		g.drawString(tekst, n + 28, y);
		return y + 16;
	}

	private static int[] posortowane(long[] licz) {
		return IntStream.range(0, licz.length).boxed().sorted((a, b) -> Long.compare(licz[b], licz[a]))
				.mapToInt(Integer::intValue).toArray();
	}

	static final int[] KOLOR_TROFII = {0xE8D878, 0xB8C860, 0x78A850, 0x2F7A3A};

	static int kolorDgw(double d) {
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
	private static double cien(ColumnSample[][] s, int i, int j, double mpp) {
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
	static void mapyRegionalne(LandscapeModel m, Path out) throws IOException {
		int n = 800;
		double bok = 2_000_000;
		double mpp = bok / n;
		double[][] o = new double[n][n];
		double[][] p = new double[n][n];
		boolean[][] morze = new boolean[n][n];
		// Bez pełnych próbek: w rozrzuconych punktach budowałyby kafle sieci rzecznej (minuty zamiast sekund).
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = -bok / 2 + (i + 0.5) * mpp;
				double z = -bok / 2 + (j + 0.5) * mpp;
				ColumnSample.Region r = m.region(x, z);
				o[j][i] = r.oceanicznosc();
				p[j][i] = r.podgorskosc();
				morze[j][i] = m.coastDistance(x, z) < 0;
			}
		});
		for (int warstwa = 0; warstwa < 3; warstwa++) {
			BufferedImage img = new BufferedImage(n + LEGENDA, n, BufferedImage.TYPE_INT_RGB);
			for (int j = 0; j < n; j++) {
				for (int i = 0; i < n; i++) {
					int rgb;
					if (morze[j][i]) {
						rgb = 0x1E3A5A;
					} else if (warstwa == 0) {
						rgb = LandscapePreview.mixRgb(0xC86A30, 0x2A80C8, Math.clamp(o[j][i], 0, 1));
					} else if (warstwa == 1) {
						rgb = LandscapePreview.mixRgb(0xE8E0C0, 0x5A3A20, Math.clamp(p[j][i], 0, 1));
					} else {
						boolean buk = Zasiegi.buk(o[j][i], p[j][i]);
						boolean swierk = Zasiegi.swierk(o[j][i], p[j][i]);
						boolean jodla = Zasiegi.jodla(p[j][i], 0, null);
						rgb = jodla ? 0x205040 : buk && swierk ? 0x5A8A4A : buk ? 0x3FA040 : swierk ? 0x2A6AA0 : 0xD8C890;
					}
					img.setRGB(i, j, rgb);
				}
			}
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setColor(new Color(0x202020));
			g.fillRect(n, 0, LEGENDA, n);
			g.setColor(Color.WHITE);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
			String[] tytul = {"O: oceaniczność", "P: podgórskość", "zasięgi gatunków"};
			g.drawString(tytul[warstwa] + " 2000 km (REAL)", n + 8, 18);
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
			g.drawString("zachód = −X (lewo), północ = −Z (góra)", n + 8, 34);
			int y = 54;
			if (warstwa < 2) {
				for (double v = 0; v <= 1.0001; v += 0.1) {
					int rgb = warstwa == 0 ? LandscapePreview.mixRgb(0xC86A30, 0x2A80C8, v)
							: LandscapePreview.mixRgb(0xE8E0C0, 0x5A3A20, v);
					y = wpis(g, n, y, rgb, String.format(Locale.ROOT, "%.1f", v));
				}
			} else {
				y = wpis(g, n, y, 0x205040, "jodła (P ≥ 0,5), też buk i świerk");
				y = wpis(g, n, y, 0x5A8A4A, "buk i świerk");
				y = wpis(g, n, y, 0x3FA040, "buk (O ≥ 0,4 lub P ≥ 0,4)");
				y = wpis(g, n, y, 0x2A6AA0, "świerk (O < 0,3)");
				y = wpis(g, n, y, 0xD8C890, "pas bez buka i świerka");
			}
			y = wpis(g, n, y + 6, 0x1E3A5A, "morze");
			g.dispose();
			String[] plik = {"m2_mapa_O_2000km.png", "m2_mapa_P_2000km.png", "m2_mapa_zasiegow_2000km.png"};
			ImageIO.write(img, "png", out.resolve(plik[warstwa]).toFile());
			System.out.println("Zapisano " + plik[warstwa]);
		}
	}

	// ------------------------------------------------------------------ udziały

	/**
	 * Udziały biomów i stref w trybie N (jak {@code BiomeSharesTest}: 200 tys. kolumn na skalę, 3 ziarna)
	 * do plików CSV: m2_udzialy_biomow.csv i m2_udzialy_stref.csv (procent wszystkich kolumn, z wodą).
	 */
	static void udzialyCsv(Path out) throws IOException {
		BiomeSharesTest.Udzialy r = new BiomeSharesTest.Udzialy();
		BiomeSharesTest.Udzialy g = new BiomeSharesTest.Udzialy();
		for (long seed : new long[] {20260927L, 1L, 2L}) {
			r.dodaj(BiomeSharesTest.policz(seed, LandscapeScale.REALISTIC, Klasyfikator.Tryb.N, 1_000_000, 1_042));
			g.dodaj(BiomeSharesTest.policz(seed, LandscapeScale.GAMEPLAY, Klasyfikator.Tryb.N, 30_000, 1_042));
		}
		long rs = 0;
		long gs = 0;
		for (int i = 0; i < r.biomSandr.length; i++) {
			rs += r.biomSandr[i];
			gs += g.biomSandr[i];
		}
		StringBuilder sb = new StringBuilder("id,nazwa,grupa,real_proc,real_sandr_proc,rozgrywka_proc,rozgrywka_sandr_proc\n");
		for (Biom b : Biom.values()) {
			int i = b.ordinal();
			sb.append(String.format(Locale.ROOT, "%s,%s,%s,%.3f,%.3f,%.3f,%.3f\n", b.id(), b.nazwa(),
					b.grupa().name().toLowerCase(Locale.ROOT), 100.0 * r.biom[i] / r.kolumny, 100.0 * r.biomSandr[i] / Math.max(1, rs),
					100.0 * g.biom[i] / g.kolumny, 100.0 * g.biomSandr[i] / Math.max(1, gs)));
		}
		sb.append(String.format(Locale.ROOT, "lesistosc_ladu,,,%.2f,%.2f,%.2f,%.2f\n", 100 * r.lesistosc(),
				100 * r.lesistoscSandr(), 100 * g.lesistosc(), 100 * g.lesistoscSandr()));
		sb.append(String.format(Locale.ROOT, "bory_w_lesie_wnetrza_sandru,,,,%.2f,,%.2f\n", 100 * r.boryWLesieSandr(),
				100 * g.boryWLesieSandr()));
		Files.writeString(out.resolve("m2_udzialy_biomow.csv"), sb.toString());
		StringBuilder st = new StringBuilder("strefa,real_proc,rozgrywka_proc\n");
		for (Strefa s : Strefa.values()) {
			st.append(String.format(Locale.ROOT, "%s,%.3f,%.3f\n", s.id(), 100.0 * r.strefa[s.ordinal()] / r.kolumny,
					100.0 * g.strefa[s.ordinal()] / g.kolumny));
		}
		Files.writeString(out.resolve("m2_udzialy_stref.csv"), st.toString());
		System.out.println("Zapisano m2_udzialy_biomow.csv, m2_udzialy_stref.csv");
	}

	// ------------------------------------------------------------------ przekroje dolin

	/**
	 * Przekroje dolin klas A, B i C: pas kolorów biomu (góra) i strefy (dół) w funkcji odległości od
	 * koryta wzdłuż osi X, z profilem terenu.
	 */
	static void przekroje(LandscapeModel m, Klasyfikator kl, double[][] miejsca, Path plik) throws IOException {
		String[] nazwy = {"A: duża rzeka (1500 m od koryta)", "B: mała rzeka (150 m)", "C: potok górski (150 m)"};
		double[] zasieg = {1_500, 150, 150};
		int w = 1_000;
		int wiersz = 140;
		BufferedImage img = new BufferedImage(w, wiersz * 3, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x202020));
		g.fillRect(0, 0, w, wiersz * 3);
		for (int r = 0; r < 3; r++) {
			double[] p = miejsca[r];
			if (p == null) {
				continue;
			}
			// Przekrój w poprzek koryta: od brzegu w stronę rosnącego d (gradient d z różnic ±1 m), więc
			// pas idzie prostopadle do koryta i nie przecina go drugi raz w zakolu.
			double x = p[0];
			double z = p[1];
			double[] kier = {1, 0};
			for (int q = 0; q < 2_000 && m.sample(x, z).hasWater(); q++) {
				x += 0.5;
			}
			double krok = zasieg[r] / w;
			double[] hh = new double[w];
			double hmin = Double.MAX_VALUE;
			double hmax = -Double.MAX_VALUE;
			for (int i = 0; i < w; i++) {
				double gx = m.sample(x + 1, z).wody().odlKoryta() - m.sample(x - 1, z).wody().odlKoryta();
				double gz = m.sample(x, z + 1).wody().odlKoryta() - m.sample(x, z - 1).wody().odlKoryta();
				double len = Math.hypot(gx, gz);
				if (Double.isFinite(len) && len > 1e-6) {
					kier = new double[] {gx / len, gz / len};
				}
				x += kier[0] * krok;
				z += kier[1] * krok;
				double px = x;
				double pz = z;
				ColumnSample c = m.sample(px, pz);
				int kod = kl.klasyfikuj(c, px, pz);
				hh[i] = c.surface();
				hmin = Math.min(hmin, hh[i]);
				hmax = Math.max(hmax, hh[i]);
				g.setColor(new Color(KOLOR_BIOMU[Siedlisko.biom(kod).ordinal()]));
				g.fillRect(i, r * wiersz + 20, 1, 50);
				Strefa st = Siedlisko.strefa(kod);
				g.setColor(new Color(st == Strefa.BRAK ? 0x303030 : KOLOR_STREFY.get(st)));
				g.fillRect(i, r * wiersz + 72, 1, 20);
			}
			g.setColor(Color.WHITE);
			for (int i = 1; i < w; i++) {
				double t0 = (hh[i - 1] - hmin) / Math.max(1, hmax - hmin);
				double t1 = (hh[i] - hmin) / Math.max(1, hmax - hmin);
				g.drawLine(i - 1, r * wiersz + 134 - (int) (t0 * 38), i, r * wiersz + 134 - (int) (t1 * 38));
			}
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
			g.drawString(String.format(Locale.ROOT, "%s; teren %.1f–%.1f m (biała linia); góra biom, dół strefa", nazwy[r],
					hmin, hmax), 6, r * wiersz + 14);
		}
		g.dispose();
		ImageIO.write(img, "png", plik.toFile());
		System.out.println("Zapisano " + plik.getFileName());
	}
}
