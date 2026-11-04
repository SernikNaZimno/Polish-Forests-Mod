package pl.polishforests.worldgen.vanilla;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.GameRegistries;
import pl.polishforests.worldgen.feature.BiomeDecoration;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Feature order of the world type "Default + Polish Forests" (step W1): the vanilla noise generator sorts the features of
 * every possible biome of its source ({@link VanillaForestsBiomeSource}: the vanilla overworld biomes without the
 * replaced forests, plus the habitat biomes replacing them) with the plain vanilla sort, which throws "Feature order cycle
 * found" when two biomes order the same features differently. The lists of the habitat biomes ({@link BiomeDecoration})
 * and of the vanilla biomes (from the game jar) must sort together.
 */
class VanillaFeatureOrderTest {
	/** A placement that only names a feature, so that every test feature is a distinct value. */
	private record Name(String id) implements PlacementModifier {
		@Override
		public void modify(PlacementContext context, RandomSource random, BlockPos origin, Consumer<BlockPos> output) {
		}

		@Override
		public MapCodec<? extends PlacementModifier> codec() {
			throw new UnsupportedOperationException();
		}
	}

	@BeforeAll
	static void bootstrap() {
		GameRegistries.bootstrap();
	}

	private final Map<String, Holder<PlacedFeature>> holders = new HashMap<>();

	private Holder<PlacedFeature> holder(String id) {
		return holders.computeIfAbsent(id, k -> Holder.direct(new PlacedFeature(null, List.of(new Name(k)))));
	}

	private List<HolderSet<PlacedFeature>> sets(List<List<String>> steps) {
		List<HolderSet<PlacedFeature>> out = new ArrayList<>();
		for (List<String> step : steps) {
			out.add(HolderSet.direct(step.stream().map(this::holder).toList()));
		}
		return out;
	}

	private static List<List<String>> vanillaSteps(String biome) {
		var stream = VanillaFeatureOrderTest.class.getResourceAsStream("/data/minecraft/worldgen/biome/" + biome + ".json");
		assertNotNull(stream, "vanilla biome " + biome);
		try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			List<List<String>> steps = new ArrayList<>();
			for (JsonElement step : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("features")) {
				List<String> ids = new ArrayList<>();
				step.getAsJsonArray().forEach(e -> ids.add(e.getAsString()));
				steps.add(ids);
			}
			return steps;
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void theBiomesOfTheWorldTypeSortWithoutACycle() {
		Map<String, List<List<String>>> lists = new HashMap<>();
		LinkedHashSet<String> biomes = new LinkedHashSet<>();
		for (String b : VanillaForestsTest.tag("is_overworld")) {
			HabitatBiome target = VanillaForests.replacement(b);
			String id = target == null ? b : "polishforests:" + target.id();
			if (biomes.add(id)) {
				lists.put(id, target == null ? vanillaSteps(b) : BiomeDecoration.steps(target));
			}
		}
		assertEquals(new LinkedHashSet<>(VanillaForests.REPLACEMENTS.values()).size(),
				biomes.stream().filter(b -> b.startsWith("polishforests:")).count());
		List<String> order = List.copyOf(biomes);
		List<FeatureSorter.StepFeatureData> sorted = assertDoesNotThrow(
				() -> FeatureSorter.buildFeaturesPerStep(order, b -> sets(lists.get(b)), false));
		// Every feature of every biome is placed.
		for (String b : order) {
			List<List<String>> steps = lists.get(b);
			for (int step = 0; step < steps.size(); step++) {
				for (String id : steps.get(step)) {
					assertTrue(sorted.get(step).features().contains(holder(id).value()), b + ": " + id + " in step " + step);
				}
			}
		}
		// Another order of the possible biomes sorts too (the order is the source's).
		assertDoesNotThrow(() -> FeatureSorter.buildFeaturesPerStep(order.reversed(), b -> sets(lists.get(b)), false));
	}
}
