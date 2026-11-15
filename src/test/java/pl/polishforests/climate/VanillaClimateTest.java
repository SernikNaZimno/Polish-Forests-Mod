package pl.polishforests.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.GameRegistries;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.vanilla.VanillaForests;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * Step C2 (CP-D.11): the temperature of the habitat biomes in the world type "Default + Polish Forests" equals the vanilla
 * temperature of the vanilla biome the column had ({@link VanillaClimate}, {@link ColumnClimate}); without a column climate
 * the biome keeps its own temperature, the one before the step.
 */
class VanillaClimateTest {
	private static final int SEA_LEVEL = 63;
	private static Method heightAdjusted;
	private static Method cachedTemperature;

	@BeforeAll
	static void bootstrap() throws Exception {
		GameRegistries.bootstrap();
		heightAdjusted = Biome.class.getDeclaredMethod("getHeightAdjustedTemperature", BlockPos.class, int.class);
		heightAdjusted.setAccessible(true);
		cachedTemperature = Biome.class.getDeclaredMethod("getTemperature", BlockPos.class, int.class);
		cachedTemperature.setAccessible(true);
	}

	@AfterEach
	void unbind() {
		VanillaClimate.bind(null);
	}

	/** A vanilla biome object with base temperature {@code t} and no temperature modifier (only the climate is set). */
	static Biome biome(float t) throws Exception {
		Class<?> settings = Class.forName("net.minecraft.world.level.biome.Biome$ClimateSettings");
		Constructor<?> c = settings.getDeclaredConstructor(boolean.class, float.class, Biome.TemperatureModifier.class, float.class);
		c.setAccessible(true);
		Object climate = c.newInstance(true, t, Biome.TemperatureModifier.NONE, 0.5F);
		Constructor<Biome> b = Biome.class.getDeclaredConstructor(settings, EnvironmentAttributeMap.class, BiomeSpecialEffects.class,
				BiomeGenerationSettings.class);
		b.setAccessible(true);
		return b.newInstance(climate, null, null, null);
	}

	private static float vanilla(Biome biome, BlockPos pos) throws Exception {
		return (float) heightAdjusted.invoke(biome, pos, SEA_LEVEL);
	}

	/** The base temperatures of the vanilla forests the mod replaces (from the game's biome JSON). */
	private static Map<String, Float> replacedTemperatures() throws Exception {
		Map<String, Float> m = new LinkedHashMap<>();
		for (String path : VanillaForests.REPLACEMENTS.keySet()) {
			var url = VanillaClimateTest.class.getResource("/data/minecraft/worldgen/biome/" + path + ".json");
			assertNotNull(url, path);
			try (var in = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
				m.put(path, JsonParser.parseReader(in).getAsJsonObject().get("temperature").getAsFloat());
			}
		}
		return m;
	}

	@Test
	void theFormulaIsTheVanillaOneBitForBit() throws Exception {
		Random random = new Random(20261115L);
		for (float t : new float[] {-0.5F, 0.2F, 0.25F, 0.3F, 0.6F, 0.7F, 0.8F, 0.95F, 2.0F}) {
			Biome biome = biome(t);
			for (int k = 0; k < 4000; k++) {
				int x = random.nextInt(60_000_000) - 30_000_000;
				int z = random.nextInt(60_000_000) - 30_000_000;
				int y = random.nextInt(384) - 64;
				BlockPos pos = new BlockPos(x, y, z);
				float expected = vanilla(biome, pos);
				assertEquals(Float.floatToIntBits(expected), Float.floatToIntBits(VanillaClimate.temperature(t, x, y, z, SEA_LEVEL)),
						() -> "base " + t + " at " + pos);
			}
		}
	}

	@Test
	void theFormerSnowyTaigaIsColdAtEveryHeight() throws Exception {
		float snowyTaiga = replacedTemperatures().get("snowy_taiga");
		assertEquals(-0.5F, snowyTaiga);
		VanillaClimate.bind((cx, cz) -> ColumnClimate.uniform(snowyTaiga));
		BiomeClimate profile = BiomeClimate.vanillaColumns(0.7F);
		Random random = new Random(7L);
		for (int k = 0; k < 20_000; k++) {
			BlockPos pos = new BlockPos(random.nextInt(200_000) - 100_000, random.nextInt(384) - 64, random.nextInt(200_000) - 100_000);
			float t = profile.temperature(pos, SEA_LEVEL);
			assertTrue(t < PolandClimate.SNOW_THRESHOLD, () -> "rain in the former snowy taiga at " + pos + ": " + t);
		}
		assertEquals(Integer.MIN_VALUE, VanillaClimate.snowLine(snowyTaiga, 0, 0, SEA_LEVEL));
	}

	@Test
	void aWarmForestOnLowGroundGetsRain() throws Exception {
		Map<String, Float> temps = replacedTemperatures();
		BiomeClimate profile = BiomeClimate.vanillaColumns(0.7F);
		Random random = new Random(11L);
		for (String warm : new String[] {"forest", "flower_forest", "birch_forest", "dark_forest", "swamp"}) {
			float base = temps.get(warm);
			VanillaClimate.bind((cx, cz) -> ColumnClimate.uniform(base));
			for (int k = 0; k < 4000; k++) {
				// Up to Y 160 (far above the low ground) these vanilla biomes are warm enough to rain.
				BlockPos pos = new BlockPos(random.nextInt(200_000) - 100_000, random.nextInt(224) - 64, random.nextInt(200_000) - 100_000);
				float t = profile.temperature(pos, SEA_LEVEL);
				assertTrue(t >= PolandClimate.SNOW_THRESHOLD, () -> "snow in " + warm + " at " + pos + ": " + t);
			}
		}
	}

	@Test
	void theTaigasAndTheWindsweptForestSnowHigherUp() throws Exception {
		Map<String, Float> temps = replacedTemperatures();
		for (String cold : new String[] {"taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "windswept_forest"}) {
			float base = temps.get(cold);
			int line = VanillaClimate.snowLine(base, 1234, -5678, SEA_LEVEL);
			// Rain at the line, snow one block above it; the line lies above the sea and below the top of the world.
			assertTrue(VanillaClimate.temperature(base, 1234, line, -5678, SEA_LEVEL) >= PolandClimate.SNOW_THRESHOLD, cold);
			assertTrue(VanillaClimate.temperature(base, 1234, line + 1, -5678, SEA_LEVEL) < PolandClimate.SNOW_THRESHOLD, cold);
			assertTrue(line > SEA_LEVEL + VanillaClimate.SNOW_LEVEL_ABOVE_SEA && line < 320, cold + " snow line " + line);
		}
	}

	@Test
	void aChunkWithoutColumnClimateKeepsTheTemperatureBeforeTheStep() throws Exception {
		Biome own = biome(0.7F);
		BiomeClimate profile = BiomeClimate.vanillaColumns(0.7F);
		Random random = new Random(3L);
		// No lookup bound, and a lookup that finds no attachment: both as before step C2.
		for (VanillaClimate.Columns lookup : new VanillaClimate.Columns[] {null, (cx, cz) -> null}) {
			VanillaClimate.bind(lookup);
			for (int k = 0; k < 4000; k++) {
				BlockPos pos = new BlockPos(random.nextInt(200_000) - 100_000, random.nextInt(384) - 64, random.nextInt(200_000) - 100_000);
				assertEquals(Float.floatToIntBits((float) cachedTemperature.invoke(own, pos, SEA_LEVEL)),
						Float.floatToIntBits(profile.temperature(pos, SEA_LEVEL)), pos::toString);
			}
		}
	}

	@Test
	void theColumnClimateOfTheChunkHoldingThePositionDecides() throws Exception {
		float[] bases = new float[256];
		for (int i = 0; i < 256; i++) {
			bases[i] = (i >> 4) < 8 ? -0.5F : 0.25F;
		}
		ColumnClimate c = ColumnClimate.of(bases);
		Map<Long, ColumnClimate> chunks = Map.of(pack(-1, 2), c);
		VanillaClimate.bind((cx, cz) -> chunks.get(pack(cx, cz)));
		Biome snowy = biome(-0.5F);
		Biome spruce = biome(0.25F);
		BiomeClimate profile = BiomeClimate.vanillaColumns(0.7F);
		for (int y = 40; y < 300; y += 7) {
			// Chunk (-1, 2): blocks x -16..-1, z 32..47; local x < 8 is the former snowy taiga.
			BlockPos a = new BlockPos(-16 + 3, y, 40);
			BlockPos b = new BlockPos(-16 + 12, y, 40);
			assertEquals(vanilla(snowy, a), profile.temperature(a, SEA_LEVEL));
			assertEquals(vanilla(spruce, b), profile.temperature(b, SEA_LEVEL));
		}
		// The neighbor chunk has no attachment: the biome's own temperature.
		BlockPos outside = new BlockPos(5, 100, 40);
		assertEquals(vanilla(biome(0.7F), outside), profile.temperature(outside, SEA_LEVEL));
	}

	@Test
	void theTemperatureIsContinuousAcrossChunkBorders() {
		// Z10: one vanilla biome on both sides of a chunk border gives one temperature field, no step at the border.
		VanillaClimate.bind((cx, cz) -> ColumnClimate.uniform(0.25F));
		BiomeClimate profile = BiomeClimate.vanillaColumns(0.7F);
		float border = 0;
		float inside = 0;
		for (int y = 90; y < 260; y += 10) {
			for (int z = -64; z < 64; z++) {
				for (int x = -64; x < 64; x++) {
					float d = Math.abs(profile.temperature(new BlockPos(x, y, z), SEA_LEVEL)
							- profile.temperature(new BlockPos(x + 1, y, z), SEA_LEVEL));
					if ((x & 15) == 15) {
						border = Math.max(border, d);
					} else {
						inside = Math.max(inside, d);
					}
				}
			}
		}
		// The steps across a border are those of the vanilla noise between any two neighbor blocks.
		assertTrue(border <= inside, "step at the chunk border " + border + ", inside the chunks " + inside);
	}

	@Test
	void onlyTheMontaneSpruceForestReplacesForestsOfDifferentTemperatures() throws Exception {
		Map<String, Float> temps = replacedTemperatures();
		Map<HabitatBiome, Holder<Biome>> targets = new IdentityHashMap<>();
		Map<Holder<Biome>, Holder<Biome>> swaps = new IdentityHashMap<>();
		for (Map.Entry<String, HabitatBiome> e : VanillaForests.REPLACEMENTS.entrySet()) {
			Holder<Biome> target = targets.computeIfAbsent(e.getValue(), h -> {
				try {
					return Holder.direct(biome(0.7F));
				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			});
			swaps.put(Holder.direct(biome(temps.get(e.getKey()))), target);
		}
		Map<Holder<Biome>, Float> bases = VanillaForestsBiomeSource.replacedBases(swaps);
		for (Map.Entry<HabitatBiome, Holder<Biome>> e : targets.entrySet()) {
			float t = bases.get(e.getValue());
			if (e.getKey() == HabitatBiome.MONTANE_SPRUCE_FOREST) {
				assertTrue(Float.isNaN(t), "the montane spruce forest replaces the snowy taiga and the old growth spruce taiga");
			} else {
				assertFalse(Float.isNaN(t), e.getKey().id());
			}
		}
		assertEquals(0.7F, bases.get(targets.get(HabitatBiome.OAK_HORNBEAM_FOREST)));
		assertEquals(0.25F, bases.get(targets.get(HabitatBiome.MIXED_PINE_FOREST)));
		assertEquals(0.2F, bases.get(targets.get(HabitatBiome.MONTANE_BEECH_FOREST)));
	}

	@Test
	void sereneSeasonsTreatsTheReplacedForestsAsTheHabitatBiomes() throws Exception {
		// Serene Seasons adds the season only to biomes with a base temperature of at most 0.8 (Biome#getBaseTemperature):
		// every replaced vanilla forest and every habitat biome of that world is in that class, so the seasons work in the
		// columns as before, now on top of the column's temperature (SS reads Biome#getTemperature).
		for (Map.Entry<String, Float> e : replacedTemperatures().entrySet()) {
			assertTrue(e.getValue() <= PolandClimate.MAX_BASE_TEMPERATURE, e.getKey());
		}
		for (HabitatBiome b : new java.util.HashSet<>(VanillaForests.REPLACEMENTS.values())) {
			assertTrue(b.temperature() <= PolandClimate.MAX_BASE_TEMPERATURE, b.id());
		}
	}

	// ------------------------------------------------------------------ ColumnClimate

	@Test
	void aChunkOfOneBiomeIsStoredAsOneValue() {
		float[] bases = new float[256];
		java.util.Arrays.fill(bases, -0.5F);
		ColumnClimate c = ColumnClimate.of(bases);
		assertEquals(1, c.paletteSize());
		assertEquals(ColumnClimate.uniform(-0.5F), c);
		assertEquals(-0.5F, c.base(7, 9));
		assertEquals(-0.5F, c.min());
	}

	@Test
	void theColumnIndexIsXThenZ() {
		float[] bases = new float[256];
		for (int i = 0; i < 256; i++) {
			bases[i] = (i >> 4) * 0.01F + (i & 15) * 0.0001F;
		}
		ColumnClimate c = ColumnClimate.of(bases);
		assertEquals(256, c.paletteSize());
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				assertEquals(bases[x << 4 | z], c.base(x, z));
				// World coordinates work too (the low four bits).
				assertEquals(bases[x << 4 | z], c.base(-160 + x, 3200 + z));
			}
		}
	}

	@Test
	void theCodecAndTheStreamCodecRoundTrip() {
		float[] bases = new float[256];
		for (int i = 0; i < 256; i++) {
			bases[i] = i % 3 == 0 ? -0.5F : i % 3 == 1 ? 0.25F : 0.8F;
		}
		for (ColumnClimate c : new ColumnClimate[] {ColumnClimate.of(bases), ColumnClimate.uniform(0.3F)}) {
			Tag tag = ColumnClimate.CODEC.encodeStart(NbtOps.INSTANCE, c).getOrThrow();
			assertEquals(c, ColumnClimate.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
			ByteBuf buf = Unpooled.buffer();
			ColumnClimate.STREAM_CODEC.encode(buf, c);
			assertEquals(c, ColumnClimate.STREAM_CODEC.decode(buf));
			assertEquals(0, buf.readableBytes());
		}
	}

	@Test
	void damagedDataIsRejected() {
		CompoundTag tag = (CompoundTag) ColumnClimate.CODEC.encodeStart(NbtOps.INSTANCE, ColumnClimate.of(twoValues())).getOrThrow();
		tag.putByteArray("columns", new byte[17]);
		assertTrue(ColumnClimate.CODEC.parse(NbtOps.INSTANCE, tag).isError());
		byte[] bad = new byte[256];
		bad[5] = 9;
		tag.putByteArray("columns", bad);
		assertTrue(ColumnClimate.CODEC.parse(NbtOps.INSTANCE, tag).isError());
		ByteBuf buf = Unpooled.buffer();
		buf.writeByte(0);
		assertThrows(DecoderException.class, () -> ColumnClimate.STREAM_CODEC.decode(buf));
	}

	private static float[] twoValues() {
		float[] bases = new float[256];
		for (int i = 0; i < 256; i++) {
			bases[i] = i < 128 ? -0.5F : 0.25F;
		}
		return bases;
	}

	private static long pack(int x, int z) {
		return (long) x << 32 | z & 0xFFFFFFFFL;
	}
}
