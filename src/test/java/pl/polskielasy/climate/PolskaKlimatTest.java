package pl.polskielasy.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import pl.polskielasy.worldgen.chunk.PolskaScale;
import pl.polskielasy.worldgen.chunk.VerticalScale;

/** Temperatura z metrów (docs/03-m2-biomy.md, sekcja 6.1) w obu skalach świata. */
class PolskaKlimatTest {
	/** Korekta Serene Seasons w marcu i listopadzie. */
	private static final double KOREKTA_III_XI = -0.25;

	@Test
	void temperatureAtSeaLevelIsBaseTemperature() {
		for (PolskaScale scale : PolskaScale.values()) {
			VerticalScale v = scale.vertical();
			// Y 62: górna ściana bloku na poziomie morza (0 m n.p.m.).
			int y = v.seaLevelY() - 1;
			assertEquals(0.0, v.metersAboveSea(y), 1e-9);
			double suma = 0;
			int n = 0;
			for (int x = -20_000; x <= 20_000; x += 397) {
				for (int z = -20_000; z <= 20_000; z += 401) {
					float t = PolskaKlimat.temperatura(KlimatBiomu.T_NIZINY, v, x, y, z);
					double oczekiwana = KlimatBiomu.T_NIZINY + PolskaKlimat.AMPLITUDA_SZUMU * PolskaKlimat.szum(x, z);
					assertEquals(oczekiwana, t, 1e-6);
					assertTrue(Math.abs(t - KlimatBiomu.T_NIZINY) <= PolskaKlimat.AMPLITUDA_SZUMU + 1e-6);
					// Pod poziomem morza (dna jezior, morze) temperatura nie rośnie.
					assertEquals(t, PolskaKlimat.temperatura(KlimatBiomu.T_NIZINY, v, x, y - 40, z), 0.0F);
					suma += t;
					n++;
				}
			}
			assertEquals(KlimatBiomu.T_NIZINY, suma / n, 0.0015, "średnia T(0 m) w skali " + scale.getSerializedName());
		}
	}

	@Test
	void noiseIsBoundedAndSmooth() {
		double min = 1;
		double max = -1;
		for (int x = 0; x < 3_000; x += 3) {
			for (int z = 0; z < 3_000; z += 7) {
				double s = PolskaKlimat.szum(x, z);
				min = Math.min(min, s);
				max = Math.max(max, s);
				assertTrue(Math.abs(PolskaKlimat.szum(x + 1, z) - s) < 0.05, "skok szumu przy " + x + ", " + z);
			}
		}
		assertTrue(min >= -1 && max <= 1);
		assertTrue(min < -0.5 && max > 0.5, String.format(Locale.ROOT, "szum za płaski: %.3f..%.3f", min, max));
	}

	@Test
	void summerSnowLineIsAboveBabiaGora() {
		for (PolskaScale scale : PolskaScale.values()) {
			for (float tBazowa : new float[] {KlimatBiomu.T_NIZINY, KlimatBiomu.T_MORZA}) {
				double[] zakres = snowLine(scale.vertical(), tBazowa, 0.0);
				System.out.printf(Locale.ROOT, "%s, T %.2f: latem śnieg od %.0f..%.0f m%n", scale.getSerializedName(),
						tBazowa, zakres[0], zakres[1]);
				assertTrue(zakres[0] >= 1_950, String.format(Locale.ROOT,
						"%s, T %.2f: latem śnieg już od %.0f m", scale.getSerializedName(), tBazowa, zakres[0]));
			}
			// Kontrola z planu: szczyt Babiej Góry (1725 m) ma T ok. 0,226 bez szumu.
			VerticalScale v = scale.vertical();
			int y = v.topBlockY(1_725);
			double bezSzumu = KlimatBiomu.T_NIZINY - PolskaKlimat.GRADIENT * v.metersAboveSea(y);
			assertEquals(0.226, bezSzumu, 0.004, "Babia Góra w skali " + scale.getSerializedName());
		}
	}

	@Test
	void marchAndNovemberSnowLineIsAround1100m() {
		for (PolskaScale scale : PolskaScale.values()) {
			double[] zakres = snowLine(scale.vertical(), KlimatBiomu.T_NIZINY, KOREKTA_III_XI);
			String opis = String.format(Locale.ROOT, "%s: próg śniegu przy -0,25 w %.0f..%.0f m",
					scale.getSerializedName(), zakres[0], zakres[1]);
			System.out.println(opis);
			assertTrue(zakres[0] >= 1_050 && zakres[1] <= 1_150, opis);
		}
	}

	@Test
	void winterSnowEverywhere() {
		for (PolskaScale scale : PolskaScale.values()) {
			VerticalScale v = scale.vertical();
			for (int x = -5_000; x <= 5_000; x += 1_013) {
				float t = PolskaKlimat.temperatura(KlimatBiomu.T_MORZA, v, x, v.seaLevelY(), x * 3);
				assertTrue(t - 0.8 < PolskaKlimat.PROG_SNIEGU, "zimą (-0,8) bez śniegu nad morzem");
			}
		}
	}

	@Test
	void temperatureFallsWithHeight() {
		for (PolskaScale scale : PolskaScale.values()) {
			VerticalScale v = scale.vertical();
			float prev = Float.MAX_VALUE;
			for (int y = v.seaLevelY() - 1; y <= v.maxY(); y++) {
				float t = PolskaKlimat.temperatura(KlimatBiomu.T_NIZINY, v, 1234, y, -5678);
				assertTrue(t <= prev, "temperatura rośnie przy Y " + y);
				prev = t;
			}
		}
	}

	@Test
	void profilesOfPlaceholderBiomes() {
		VerticalScale v = VerticalScale.REAL;
		for (String id : KlimatBiomu.ZASTEPCZE) {
			KlimatBiomu k = KlimatBiomu.zastepczy(id, v);
			assertTrue(k.tBazowa() <= PolskaKlimat.MAKS_T_BAZOWA, id + ": T_bazowa ponad bramką Serene Seasons");
			assertTrue(k.tBazowa() >= 0.6F, id);
			assertTrue(k.skala() == v);
		}
		assertEquals(KlimatBiomu.Zamarzanie.NIGDY, KlimatBiomu.zastepczy("minecraft:ocean", v).zamarzanie());
		assertEquals(KlimatBiomu.T_MORZA, KlimatBiomu.zastepczy("minecraft:ocean", v).tBazowa());
		assertEquals(KlimatBiomu.Zamarzanie.RZEKA, KlimatBiomu.zastepczy("minecraft:river", v).zamarzanie());
		assertEquals(KlimatBiomu.Zamarzanie.WANILIA, KlimatBiomu.zastepczy("minecraft:forest", v).zamarzanie());
		assertEquals(KlimatBiomu.T_NIZINY, KlimatBiomu.zastepczy("minecraft:forest", v).tBazowa());
	}

	/** Tag klimatu obejmuje dokładnie biomy zastępcze z obu presetów świata. */
	@Test
	void tagCoversPresetBiomes() throws Exception {
		Set<String> tag = new TreeSet<>();
		for (JsonElement e : json("/data/polskielasy/tags/worldgen/biome/klimat_polski.json").getAsJsonObject()
				.getAsJsonArray("values")) {
			tag.add(e.getAsString());
		}
		assertEquals(new TreeSet<>(KlimatBiomu.ZASTEPCZE), tag);
		for (String preset : List.of("polska", "polska_rozgrywka")) {
			Set<String> biomy = new TreeSet<>();
			json("/data/polskielasy/worldgen/world_preset/" + preset + ".json").getAsJsonObject()
					.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
					.getAsJsonObject("generator").getAsJsonObject("biome_source").entrySet()
					.forEach(en -> {
						if (!en.getKey().equals("type")) {
							biomy.add(en.getValue().getAsString());
						}
					});
			assertEquals(tag, biomy, "preset " + preset);
		}
	}

	/** Najniższa wysokość ze śniegiem (T + korekta < 0,15) w wielu kolumnach: [min, max] w metrach. */
	private static double[] snowLine(VerticalScale v, float tBazowa, double korekta) {
		List<Double> progi = new ArrayList<>();
		for (int x = -30_000; x <= 30_000; x += 487) {
			for (int z = -30_000; z <= 30_000; z += 2_011) {
				for (int y = v.seaLevelY(); y <= v.maxY(); y++) {
					float t = PolskaKlimat.temperatura(tBazowa, v, x, y, z);
					if ((float) (t + korekta) < PolskaKlimat.PROG_SNIEGU) {
						progi.add(v.metersAboveSea(y));
						break;
					}
				}
			}
		}
		assertTrue(progi.size() > 1_000, "brak śniegu w całej wysokości świata");
		double min = progi.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
		double max = progi.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
		return new double[] {min, max};
	}

	private static JsonElement json(String path) throws Exception {
		try (InputStream in = PolskaKlimatTest.class.getResourceAsStream(path)) {
			assertTrue(in != null, "brak zasobu " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}
}
