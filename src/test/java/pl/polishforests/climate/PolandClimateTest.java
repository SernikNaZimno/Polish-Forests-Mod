package pl.polishforests.climate;

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
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/** Temperature from meters (docs/03-m2-biomy.md, section 6.1) in both world scales. */
class PolandClimateTest {
	/** Serene Seasons correction in March and November. */
	private static final double MARCH_NOVEMBER_CORRECTION = -0.25;

	@Test
	void temperatureAtSeaLevelIsBaseTemperature() {
		for (PolandScale scale : PolandScale.values()) {
			VerticalScale v = scale.vertical();
			// Y 62: top face of the block at sea level (0 m a.s.l.).
			int y = v.seaLevelY() - 1;
			assertEquals(0.0, v.metersAboveSea(y), 1e-9);
			double sum = 0;
			int n = 0;
			for (int x = -20_000; x <= 20_000; x += 397) {
				for (int z = -20_000; z <= 20_000; z += 401) {
					float t = PolandClimate.temperature(BiomeClimate.T_LOWLAND, v, x, y, z);
					double expected = BiomeClimate.T_LOWLAND + PolandClimate.NOISE_AMPLITUDE * PolandClimate.noise(x, z);
					assertEquals(expected, t, 1e-6);
					assertTrue(Math.abs(t - BiomeClimate.T_LOWLAND) <= PolandClimate.NOISE_AMPLITUDE + 1e-6);
					// Below sea level (lake beds, sea) the temperature does not rise.
					assertEquals(t, PolandClimate.temperature(BiomeClimate.T_LOWLAND, v, x, y - 40, z), 0.0F);
					sum += t;
					n++;
				}
			}
			assertEquals(BiomeClimate.T_LOWLAND, sum / n, 0.0015, "mean T(0 m) at scale " + scale.getSerializedName());
		}
	}

	@Test
	void noiseIsBoundedAndSmooth() {
		double min = 1;
		double max = -1;
		for (int x = 0; x < 3_000; x += 3) {
			for (int z = 0; z < 3_000; z += 7) {
				double s = PolandClimate.noise(x, z);
				min = Math.min(min, s);
				max = Math.max(max, s);
				assertTrue(Math.abs(PolandClimate.noise(x + 1, z) - s) < 0.05, "noise jump at " + x + ", " + z);
			}
		}
		assertTrue(min >= -1 && max <= 1);
		assertTrue(min < -0.5 && max > 0.5, String.format(Locale.ROOT, "noise too flat: %.3f..%.3f", min, max));
	}

	@Test
	void summerSnowLineIsAboveBabiaGora() {
		for (PolandScale scale : PolandScale.values()) {
			for (float baseTemperature : new float[] {BiomeClimate.T_LOWLAND, BiomeClimate.T_SEA}) {
				double[] range = snowLine(scale.vertical(), baseTemperature, 0.0);
				System.out.printf(Locale.ROOT, "%s, T %.2f: summer snow from %.0f..%.0f m%n", scale.getSerializedName(),
						baseTemperature, range[0], range[1]);
				assertTrue(range[0] >= 1_950, String.format(Locale.ROOT,
						"%s, T %.2f: summer snow already from %.0f m", scale.getSerializedName(), baseTemperature, range[0]));
			}
			// Check from the plan: the summit of Babia Gora (1725 m) has T of about 0.226 without noise.
			VerticalScale v = scale.vertical();
			int y = v.topBlockY(1_725);
			double withoutNoise = BiomeClimate.T_LOWLAND - PolandClimate.GRADIENT * v.metersAboveSea(y);
			assertEquals(0.226, withoutNoise, 0.004, "Babia Gora at scale " + scale.getSerializedName());
		}
	}

	@Test
	void marchAndNovemberSnowLineIsAround1100m() {
		for (PolandScale scale : PolandScale.values()) {
			double[] range = snowLine(scale.vertical(), BiomeClimate.T_LOWLAND, MARCH_NOVEMBER_CORRECTION);
			String description = String.format(Locale.ROOT, "%s: snow threshold with -0.25 at %.0f..%.0f m",
					scale.getSerializedName(), range[0], range[1]);
			System.out.println(description);
			assertTrue(range[0] >= 1_050 && range[1] <= 1_150, description);
		}
	}

	@Test
	void winterSnowEverywhere() {
		for (PolandScale scale : PolandScale.values()) {
			VerticalScale v = scale.vertical();
			for (int x = -5_000; x <= 5_000; x += 1_013) {
				float t = PolandClimate.temperature(BiomeClimate.T_SEA, v, x, v.seaLevelY(), x * 3);
				assertTrue(t - 0.8 < PolandClimate.SNOW_THRESHOLD, "no snow above the sea in winter (-0.8)");
			}
		}
	}

	@Test
	void temperatureFallsWithHeight() {
		for (PolandScale scale : PolandScale.values()) {
			VerticalScale v = scale.vertical();
			float prev = Float.MAX_VALUE;
			for (int y = v.seaLevelY() - 1; y <= v.maxY(); y++) {
				float t = PolandClimate.temperature(BiomeClimate.T_LOWLAND, v, 1234, y, -5678);
				assertTrue(t <= prev, "temperature rises at Y " + y);
				prev = t;
			}
		}
	}

	/** Profiles of the mod's biomes (step S5): base temperatures from docs/03-m2-biomy.md §6.1 and freeze modes. */
	@Test
	void profilesOfModBiomes() {
		VerticalScale v = VerticalScale.REAL;
		for (HabitatBiome b : HabitatBiome.values()) {
			String id = "polishforests:" + b.id();
			BiomeClimate k = BiomeClimate.profile(id, v);
			assertTrue(k.baseTemperature() <= PolandClimate.MAX_BASE_TEMPERATURE, id + ": base temperature above the Serene Seasons gate");
			assertTrue(k.baseTemperature() >= 0.6F, id);
			assertEquals(b.temperature(), k.baseTemperature(), id);
			assertTrue(k.scale() == v);
		}
		assertEquals(BiomeClimate.FreezeMode.NEVER, BiomeClimate.profile("polishforests:sea", v).freezeMode());
		assertEquals(BiomeClimate.T_SEA, BiomeClimate.profile("polishforests:sea", v).baseTemperature());
		assertEquals(BiomeClimate.FreezeMode.RIVER, BiomeClimate.profile("polishforests:river", v).freezeMode());
		assertEquals(BiomeClimate.FreezeMode.RIVER, BiomeClimate.profile("polishforests:stream", v).freezeMode());
		assertEquals(BiomeClimate.FreezeMode.VANILLA, BiomeClimate.profile("polishforests:lake", v).freezeMode());
		assertEquals(BiomeClimate.T_LOWLAND, BiomeClimate.profile("polishforests:oak_hornbeam_forest", v).baseTemperature());
		assertEquals(0.65F, BiomeClimate.profile("polishforests:raised_bog", v).baseTemperature());
		// A biome added to the tag by a data pack: the lowland profile.
		assertEquals(BiomeClimate.T_LOWLAND, BiomeClimate.profile("minecraft:forest", v).baseTemperature());
		assertEquals(BiomeClimate.FreezeMode.VANILLA, BiomeClimate.profile("minecraft:forest", v).freezeMode());
	}

	/**
	 * The climate tag (datagen, src/main/generated) covers exactly the 36 biomes of the mod, and the world presets
	 * name no biomes: the biome source takes them from the registry (docs/03-m2-biomy.md §3.5).
	 */
	@Test
	void tagCoversModBiomes() throws Exception {
		Set<String> tag = new TreeSet<>();
		for (JsonElement e : json("/data/polishforests/tags/worldgen/biome/polish_climate.json").getAsJsonObject()
				.getAsJsonArray("values")) {
			tag.add(e.getAsString());
		}
		Set<String> expected = new TreeSet<>();
		for (HabitatBiome b : HabitatBiome.values()) {
			expected.add("polishforests:" + b.id());
		}
		assertEquals(expected, tag);
		for (String preset : List.of("poland", "poland_gameplay")) {
			var source = json("/data/polishforests/worldgen/world_preset/" + preset + ".json").getAsJsonObject()
					.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
					.getAsJsonObject("generator").getAsJsonObject("biome_source");
			assertEquals(Set.of("type"), source.keySet(), "preset " + preset);
			assertEquals("polishforests:poland", source.get("type").getAsString(), "preset " + preset);
		}
	}

	/** Lowest height with snow (T + correction < 0.15) over many columns: [min, max] in meters. */
	private static double[] snowLine(VerticalScale v, float baseTemperature, double correction) {
		List<Double> thresholds = new ArrayList<>();
		for (int x = -30_000; x <= 30_000; x += 487) {
			for (int z = -30_000; z <= 30_000; z += 2_011) {
				for (int y = v.seaLevelY(); y <= v.maxY(); y++) {
					float t = PolandClimate.temperature(baseTemperature, v, x, y, z);
					if ((float) (t + correction) < PolandClimate.SNOW_THRESHOLD) {
						thresholds.add(v.metersAboveSea(y));
						break;
					}
				}
			}
		}
		assertTrue(thresholds.size() > 1_000, "no snow anywhere in the world height");
		double min = thresholds.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
		double max = thresholds.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
		return new double[] {min, max};
	}

	private static JsonElement json(String path) throws Exception {
		try (InputStream in = PolandClimateTest.class.getResourceAsStream(path)) {
			assertTrue(in != null, "missing resource " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}
}
