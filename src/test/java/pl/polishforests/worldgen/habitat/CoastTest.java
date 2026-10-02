package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Wybrzeże na przekrojach brzegu (docs/03-m2-biomy.md §5.2, poprawka po S4): przekrój zaczyna się na linii
 * brzegu i idzie w głąb lądu wzdłuż normalnej do brzegu (gradient odległości od morza) do B + D + 400k.
 * Na brzegu wydmowym ({@code niskiBrzeg} ≥ 0,5) w pasie wydm B ≤ cD &lt; B + D nie ma stref klifu, a wydmy
 * i bór bażynowy zajmują większość kolumn lądu poza dnami dolin i brzegami wód stojących. Na brzegu wysokim
 * jest ściana klifu.
 */
class CoastTest {
	static final long SEED = 20260927L;
	/** Przekroje brzegu wydmowego i wysokiego (każdego rodzaju najwyżej tyle). */
	static final int PER_KIND = 40;

	/**
	 * Przekrój: brzeg wydmowy (w środku pasa wydm), kody kolumn, odległości od morza, kolumny liczone
	 * (ląd poza dnami i brzegami wód stojących) i kolumny brzegu wydmowego.
	 */
	record Section(boolean duneShore, int[] codes, double[] cD, boolean[] counted, boolean[] low, double x, double z) {
	}

	static List<Section> sections(LandscapeModel m, HabitatClassifier k) {
		LandscapeScale sc = m.scale();
		double kk = sc.local();
		double grid = sc == LandscapeScale.REALISTIC ? 12_000 : 600;
		int n = sc == LandscapeScale.REALISTIC ? 250 : 150;
		List<long[]> candidates = Collections.synchronizedList(new ArrayList<>());
		IntStream.range(0, n * n).parallel().forEach(q -> {
			double x = ((q % n) - n / 2) * grid;
			double z = ((q / n) - n / 2) * grid;
			double d = m.coastDistance(x, z);
			if (d > 0 && d < 3_000 * kk) {
				candidates.add(new long[] {Noise.mix(SEED + q), q});
			}
		});
		// Wybór niezależny od kolejności wątków: według skrótu.
		List<long[]> l = new ArrayList<>(candidates);
		l.sort((a, b) -> Long.compare(a[0], b[0]));
		List<Section> out = new ArrayList<>();
		int duneShores = 0;
		int highShores = 0;
		for (long[] c : l) {
			if (duneShores >= PER_KIND && highShores >= PER_KIND) {
				break;
			}
			int q = (int) c[1];
			Section p = section(m, k, ((q % n) - n / 2) * grid, ((q / n) - n / 2) * grid);
			if (p != null && (p.duneShore() ? duneShores++ : highShores++) < PER_KIND) {
				out.add(p);
			}
		}
		return out;
	}

	static double[] gradient(LandscapeModel m, double x, double z) {
		double gx = m.coastDistance(x + 2, z) - m.coastDistance(x - 2, z);
		double gz = m.coastDistance(x, z + 2) - m.coastDistance(x, z - 2);
		double len = Math.hypot(gx, gz);
		return len > 1e-9 ? new double[] {gx / len, gz / len} : null;
	}

	static Section section(LandscapeModel m, HabitatClassifier k, double x0, double z0) {
		double kk = m.scale().local();
		double x = x0;
		double z = z0;
		for (int i = 0; i < 60; i++) {
			double d = m.coastDistance(x, z);
			if (Math.abs(d) < 0.5) {
				break;
			}
			double[] g = gradient(m, x, z);
			if (g == null) {
				return null;
			}
			x -= g[0] * d;
			z -= g[1] * d;
		}
		if (Math.abs(m.coastDistance(x, z)) >= 0.5) {
			return null;
		}
		double[] g = gradient(m, x, z);
		if (g == null) {
			return null;
		}
		double b = Calibration.BEACH_B * kk;
		double dw = Calibration.DUNES_D * kk;
		double length = b + dw + 400 * kk;
		double step = 1.0 * kk;
		int steps = (int) (length / step);
		int[] codes = new int[steps];
		double[] cD = new double[steps];
		boolean[] counted = new boolean[steps];
		boolean[] low = new boolean[steps];
		// Brzeg wydmowy według pola w środku pasa wydm.
		ColumnSample middle = m.sample(x + g[0] * (b + dw / 2), z + g[1] * (b + dw / 2));
		boolean duneShore = middle.terrain().lowShore() >= Calibration.LOW_SHORE;
		for (int i = 0; i < steps; i++) {
			double px = x + g[0] * (i + 0.5) * step;
			double pz = z + g[1] * (i + 0.5) * step;
			ColumnSample s = m.sample(px, pz);
			codes[i] = k.classify(s, px, pz);
			cD[i] = s.terrain().coastD();
			HabitatClassifier.Column c = new HabitatClassifier.Column(k, s, px, pz);
			counted[i] = !s.hasWater() && !c.onValleyFloor() && !(c.w.s() < Calibration.LAKE_ALDER_CARR_K * kk);
			low[i] = s.terrain().lowShore() >= Calibration.LOW_SHORE;
		}
		return new Section(duneShore, codes, cD, counted, low, x, z);
	}

	static void check(LandscapeScale sc) {
		LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
		HabitatClassifier k = new HabitatClassifier(SEED, sc, HabitatClassifier.Mode.NATURAL);
		List<Section> list = sections(m, k);
		double kk = sc.local();
		double b = Calibration.BEACH_B * kk;
		double dw = Calibration.DUNES_D * kk;
		int duneShores = 0;
		int highShores = 0;
		int highShoresWithCliff = 0;
		long band = 0;
		long dunes = 0;
		long white = 0;
		long gray = 0;
		long cliffs = 0;
		List<String> cliffExamples = new ArrayList<>();
		for (Section p : list) {
			if (!p.duneShore()) {
				highShores++;
				boolean cliffFace = false;
				for (int code : p.codes()) {
					cliffFace |= Habitat.zone(code) == Zone.CLIFF_FACE;
				}
				highShoresWithCliff += cliffFace ? 1 : 0;
				continue;
			}
			duneShores++;
			for (int i = 0; i < p.codes().length; i++) {
				if (p.cD()[i] < b || p.cD()[i] >= b + dw || !p.counted()[i] || !p.low()[i]) {
					continue;
				}
				int code = p.codes()[i];
				HabitatBiome bi = Habitat.biome(code);
				Zone s = Habitat.zone(code);
				band++;
				if (s == Zone.CLIFF_FACE || s == Zone.CLIFF_TOP || Habitat.association(code) == Association.WINDSWEPT) {
					cliffs++;
					if (cliffExamples.size() < 5) {
						cliffExamples.add(String.format(Locale.ROOT, "(%.0f, %.0f) cD %.0f %s", p.x(), p.z(), p.cD()[i],
								Habitat.of(code)));
					}
				}
				if (bi == HabitatBiome.WHITE_DUNE || bi == HabitatBiome.GRAY_DUNE || bi == HabitatBiome.COASTAL_PINE_FOREST) {
					dunes++;
				}
				white += bi == HabitatBiome.WHITE_DUNE ? 1 : 0;
				gray += bi == HabitatBiome.GRAY_DUNE ? 1 : 0;
			}
		}
		double share = band == 0 ? Double.NaN : (double) dunes / band;
		System.out.printf(Locale.ROOT,
				"%s: przekroje %d (wydmowe %d, wysokie %d, w tym z klifem %d); pas wydm %d kolumn: wydmy i bór bażynowy %.1f%% "
						+ "(biała %.1f%%, szara %.1f%%), klif %d%n",
				sc.id(), list.size(), duneShores, highShores, highShoresWithCliff, band, 100 * share, 100.0 * white / Math.max(1, band),
				100.0 * gray / Math.max(1, band), cliffs);
		assertTrue(duneShores >= 20, sc.id() + ": za mało brzegów wydmowych: " + duneShores);
		assertEquals(0, cliffs, sc.id() + ": klif na brzegu wydmowym: " + cliffExamples);
		assertTrue(share >= 0.9, sc.id() + ": wydmy i bór bażynowy w pasie wydm: " + share);
		assertTrue(white > 0 && gray > 0, sc.id() + ": brak wydmy białej albo szarej");
		assertTrue(highShores == 0 || highShoresWithCliff > 0, sc.id() + ": brak klifu na wysokim brzegu");
	}

	@Test
	void duneShoreAtRealisticScale() {
		check(LandscapeScale.REALISTIC);
	}

	@Test
	void duneShoreAtGameplayScale() {
		check(LandscapeScale.GAMEPLAY);
	}
}
