package pl.polishforests.worldgen.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.Util;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import pl.polishforests.PolishForests;

/**
 * Order of the placed features of a "Poland" generator in each decoration step (step J2 of docs/04-pionowy-wycinek.md).
 * A feature is seeded by its index in the step ({@code ChunkGenerator.applyBiomeDecoration}: {@code setFeatureSeed(seed,
 * index, step)}), and vanilla sorts the features of all possible biomes together ({@link FeatureSorter}); with the vanilla
 * cave biomes among the possible biomes and the underground features of the worlds with caves ({@code polishforests:cave/*})
 * in the habitat biomes, the vanilla sort moved features of the habitats (the deep lava lakes, the glacial erratics, the
 * copper ore) to other indices, so the same world would have generated other lava lakes, erratics and ores. Here each step
 * keeps first the features of the habitat biomes in their order of before step J2 (the vanilla sort of the habitat biomes
 * without the {@code polishforests:cave/*} features), then the other features in the order of the vanilla sort of all
 * possible biomes, so the features of the habitats keep their indices and seeds, and a world without caves generates as
 * before. The full vanilla sort still runs, so an order cycle among the biomes is reported as in vanilla.
 */
public final class FeatureOrder {
	/** Path prefix of the underground features of the worlds with caves, which come after the features of before J2. */
	public static final String CAVE_PREFIX = "cave/";

	private FeatureOrder() {
	}

	/** Whether the placed feature is one of the underground features that the habitat biomes got in step J2. */
	static boolean addedWithCaves(Holder<PlacedFeature> feature) {
		return feature.unwrapKey().map(k -> k.identifier().getNamespace().equals(PolishForests.MOD_ID)
				&& k.identifier().getPath().startsWith(CAVE_PREFIX)).orElse(false);
	}

	/**
	 * The features per step for the possible biomes {@code biomes}, of which {@code habitats} are the habitat biomes.
	 *
	 * @param features the placed features of a biome per step
	 */
	public static List<FeatureSorter.StepFeatureData> build(List<Holder<Biome>> biomes, Set<Holder<Biome>> habitats,
			Function<Holder<Biome>, List<HolderSet<PlacedFeature>>> features) {
		return build(biomes, habitats, features, FeatureOrder::addedWithCaves);
	}

	/** {@link #build} with the test of the features that the habitat biomes got in step J2 ({@code added}). */
	public static <B> List<FeatureSorter.StepFeatureData> build(List<B> biomes, Set<B> habitats,
			Function<B, List<HolderSet<PlacedFeature>>> features, Predicate<Holder<PlacedFeature>> added) {
		List<FeatureSorter.StepFeatureData> full = FeatureSorter.buildFeaturesPerStep(biomes, features, true);
		List<B> habitatList = biomes.stream().filter(habitats::contains).toList();
		Function<B, List<HolderSet<PlacedFeature>>> withoutCaves = b -> {
			List<HolderSet<PlacedFeature>> steps = new ArrayList<>();
			for (HolderSet<PlacedFeature> step : features.apply(b)) {
				List<Holder<PlacedFeature>> kept = step.stream().filter(f -> !added.test(f)).toList();
				steps.add(HolderSet.direct(kept));
			}
			return steps;
		};
		List<FeatureSorter.StepFeatureData> legacy = FeatureSorter.buildFeaturesPerStep(habitatList, withoutCaves, true);
		return merge(legacy.stream().map(FeatureSorter.StepFeatureData::features).toList(),
				full.stream().map(FeatureSorter.StepFeatureData::features).toList()).stream()
				.map(list -> new FeatureSorter.StepFeatureData(list, Util.createIndexIdentityLookup(list))).toList();
	}

	/**
	 * Per step: the elements of {@code first} in their order, then the elements of {@code all} not among them, in their
	 * order (identity). The number of steps is the larger one.
	 */
	public static <T> List<List<T>> merge(List<List<T>> first, List<List<T>> all) {
		int steps = Math.max(first.size(), all.size());
		List<List<T>> out = new ArrayList<>(steps);
		for (int s = 0; s < steps; s++) {
			List<T> head = s < first.size() ? first.get(s) : List.of();
			List<T> list = new ArrayList<>(head);
			Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
			seen.addAll(head);
			if (s < all.size()) {
				for (T t : all.get(s)) {
					if (seen.add(t)) {
						list.add(t);
					}
				}
			}
			out.add(List.copyOf(list));
		}
		return out;
	}
}
