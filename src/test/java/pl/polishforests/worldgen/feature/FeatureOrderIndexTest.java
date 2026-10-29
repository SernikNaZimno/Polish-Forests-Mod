package pl.polishforests.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import pl.polishforests.worldgen.cave.CaveBiomes;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Feature order of the "Poland" generator with the cave biomes (step J2, {@link FeatureOrder}): with the 36 habitat
 * biomes of today (lists of {@link BiomeDecoration}) and the 4 vanilla cave biomes (their lists from the game jar), every
 * feature of the habitat biomes of before J2 keeps its index in its step, so its seed and a world without caves stay as
 * they were; the plain vanilla sort of the same biomes would move some of them (checked, so this test stays meaningful).
 */
class FeatureOrderIndexTest {
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
	private final Map<Holder<PlacedFeature>, String> ids = new IdentityHashMap<>();

	private Holder<PlacedFeature> holder(String id) {
		return holders.computeIfAbsent(id, k -> {
			Holder<PlacedFeature> h = Holder.direct(new PlacedFeature(null, List.of(new Name(k))));
			ids.put(h, k);
			return h;
		});
	}

	private List<HolderSet<PlacedFeature>> sets(List<List<String>> steps) {
		List<HolderSet<PlacedFeature>> out = new ArrayList<>();
		for (List<String> step : steps) {
			out.add(HolderSet.direct(step.stream().map(this::holder).toList()));
		}
		return out;
	}

	private static List<List<String>> vanillaSteps(String biome) {
		var stream = FeatureOrderIndexTest.class.getResourceAsStream("/data/minecraft/worldgen/biome/" + biome + ".json");
		assertNotNull(stream, "vanilla biome " + biome);
		try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			List<List<String>> steps = new ArrayList<>();
			for (JsonElement step : json.getAsJsonArray("features")) {
				List<String> ids = new ArrayList<>();
				step.getAsJsonArray().forEach(e -> ids.add(e.getAsString()));
				steps.add(ids);
			}
			return steps;
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static List<List<String>> withoutCaveFeatures(List<List<String>> steps) {
		return steps.stream().map(step -> step.stream().filter(id -> !id.startsWith("polishforests:cave/")).toList()).toList();
	}

	private List<List<String>> names(List<FeatureSorter.StepFeatureData> steps) {
		List<List<String>> out = new ArrayList<>();
		for (FeatureSorter.StepFeatureData step : steps) {
			List<String> names = new ArrayList<>();
			for (PlacedFeature f : step.features()) {
				names.add(((Name) f.placement().getFirst()).id());
			}
			out.add(names);
		}
		return out;
	}

	@Test
	void featuresOfTheHabitatsKeepTheirIndices() {
		Map<String, List<List<String>>> now = new HashMap<>();
		List<String> biomes = new ArrayList<>();
		Set<String> habitats = new LinkedHashSet<>();
		for (HabitatBiome b : HabitatBiome.values()) {
			biomes.add(b.id());
			habitats.add(b.id());
			now.put(b.id(), BiomeDecoration.steps(b));
		}
		for (var key : CaveBiomes.KEYS) {
			String path = key.identifier().getPath();
			biomes.add(path);
			now.put(path, vanillaSteps(path));
		}
		// Before step J2: the habitat biomes alone, without the features of the worlds with caves.
		List<List<String>> before = names(FeatureSorter.buildFeaturesPerStep(List.copyOf(habitats),
				b -> sets(withoutCaveFeatures(now.get(b))), false));
		List<List<String>> after = names(FeatureOrder.build(biomes, habitats, b -> sets(now.get(b)),
				h -> ids.get(h).startsWith("polishforests:cave/")));
		List<List<String>> vanillaSort = names(FeatureSorter.buildFeaturesPerStep(biomes, b -> sets(now.get(b)), false));
		boolean vanillaMoves = false;
		for (int step = 0; step < before.size(); step++) {
			for (int i = 0; i < before.get(step).size(); i++) {
				String id = before.get(step).get(i);
				assertEquals(i, after.get(step).indexOf(id), "step " + step + ": " + id + " moved");
				vanillaMoves |= vanillaSort.get(step).indexOf(id) != i;
			}
		}
		assertTrue(vanillaMoves, "the plain vanilla sort keeps the indices: the test no longer shows anything");
		// Every feature of every biome is placed, once.
		for (String b : biomes) {
			List<List<String>> steps = now.get(b);
			for (int step = 0; step < steps.size(); step++) {
				for (String id : steps.get(step)) {
					assertTrue(after.get(step).contains(id), b + ": " + id + " missing in step " + step);
				}
			}
		}
		for (List<String> step : after) {
			assertEquals(step.size(), new LinkedHashSet<>(step).size(), "a feature twice in " + step);
		}
	}
}
