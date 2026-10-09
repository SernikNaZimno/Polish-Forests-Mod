package pl.polishforests.worldgen.feature.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import pl.polishforests.worldgen.feature.plan.ColumnPlan;
import pl.polishforests.worldgen.feature.plan.Ecotone;

/**
 * Palette of a column layer (deadwood, understory, waterside zones, ground layer, aquatic plants; JSON from datagen,
 * docs/03-m2-biomy.md §8.2, §8.6, §4.6): rules with a habitat condition ({@link HabitatCondition}), a medium (dry land,
 * dry land beside water, the bottom or the surface of water of a depth range), the top ground blocks, the share of the
 * columns covered and the plants with their weights. The first rule that applies to a column decides it
 * ({@link ColumnPlan}).
 *
 * @param rules rules in order
 */
public record PlantPalette(List<Rule> rules) {
	public static final Codec<ColumnPlan.Medium> MEDIUM = HabitatCondition.enumCodec(ColumnPlan.Medium.class,
			ColumnPlan.Medium.values());
	public static final Codec<ColumnPlan.Ground> GROUND = HabitatCondition.enumCodec(ColumnPlan.Ground.class,
			ColumnPlan.Ground.values());
	public static final Codec<Ecotone.Edge> EDGE = HabitatCondition.enumCodec(Ecotone.Edge.class, Ecotone.Edge.values());

	/**
	 * A plant of a rule: a block state (a double plant gets both halves, a block with a height above 1 is a column such
	 * as sugar cane) or a placed feature (shrubs {@code polishforests:shrub/*}, fallen trees), with its weight.
	 *
	 * @param block     block state to place, or empty
	 * @param feature   placed feature to place, or empty
	 * @param minHeight least height of a block column
	 * @param maxHeight greatest height of a block column
	 * @param weight    share weight within the rule
	 */
	public record Plant(Optional<BlockState> block, Optional<Holder<PlacedFeature>> feature, int minHeight, int maxHeight,
			int weight) {
		public static final Codec<Plant> CODEC = RecordCodecBuilder.<Plant>create(i -> i.group(
				BlockState.CODEC.optionalFieldOf("block").forGetter(Plant::block),
				PlacedFeature.CODEC.optionalFieldOf("feature").forGetter(Plant::feature),
				Codec.intRange(1, 8).optionalFieldOf("min_height", 1).forGetter(Plant::minHeight),
				Codec.intRange(1, 8).optionalFieldOf("max_height", 1).forGetter(Plant::maxHeight),
				Codec.intRange(1, 10_000).fieldOf("weight").forGetter(Plant::weight)
		).apply(i, Plant::new)).validate(p -> p.block().isPresent() == p.feature().isPresent()
				? DataResult.error(() -> "a plant needs either a block or a feature") : p.minHeight() > p.maxHeight()
						? DataResult.error(() -> "min_height above max_height") : DataResult.success(p));

		public static Plant block(BlockState state, int weight) {
			return new Plant(Optional.of(state), Optional.empty(), 1, 1, weight);
		}

		public static Plant column(BlockState state, int minHeight, int maxHeight, int weight) {
			return new Plant(Optional.of(state), Optional.empty(), minHeight, maxHeight, weight);
		}

		public static Plant feature(Holder<PlacedFeature> feature, int weight) {
			return new Plant(Optional.empty(), Optional.of(feature), 1, 1, weight);
		}

		/** The same plant with another weight (plants are deduplicated without their weights). */
		public Plant withWeight(int weight) {
			return new Plant(block, feature, minHeight, maxHeight, weight);
		}
	}

	/**
	 * A palette rule.
	 *
	 * @param condition habitat condition
	 * @param medium    medium of the plants
	 * @param minDepth  least water depth in blocks (water media)
	 * @param maxDepth  greatest water depth in blocks (water media)
	 * @param grounds   top ground blocks the plants need (empty: any)
	 * @param coverage  share of the matching columns that get a plant
	 * @param patch     patch size in blocks (1: none)
	 * @param plants    plants and their weights
	 * @param edges     forest edge classes of the columns (empty: any; step S8b, {@link Ecotone#edges})
	 */
	public record Rule(HabitatCondition condition, ColumnPlan.Medium medium, int minDepth, int maxDepth,
			List<ColumnPlan.Ground> grounds, float coverage, int patch, List<Plant> plants, List<Ecotone.Edge> edges) {
		public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
				HabitatCondition.MAP_CODEC.forGetter(Rule::condition),
				MEDIUM.optionalFieldOf("medium", ColumnPlan.Medium.LAND).forGetter(Rule::medium),
				Codec.intRange(0, 64).optionalFieldOf("min_depth", 1).forGetter(Rule::minDepth),
				Codec.intRange(0, 64).optionalFieldOf("max_depth", 64).forGetter(Rule::maxDepth),
				GROUND.listOf().optionalFieldOf("grounds", List.of()).forGetter(Rule::grounds),
				Codec.floatRange(0.0F, 1.0F).fieldOf("coverage").forGetter(Rule::coverage),
				Codec.intRange(1, 16).optionalFieldOf("patch", 1).forGetter(Rule::patch),
				Plant.CODEC.listOf().fieldOf("plants").forGetter(Rule::plants),
				EDGE.listOf().optionalFieldOf("edges", List.of()).forGetter(Rule::edges)
		).apply(i, Rule::new));

		public Rule {
			grounds = List.copyOf(grounds);
			plants = List.copyOf(plants);
			edges = List.copyOf(edges);
		}

		/** A rule for any forest edge class. */
		public Rule(HabitatCondition condition, ColumnPlan.Medium medium, int minDepth, int maxDepth,
				List<ColumnPlan.Ground> grounds, float coverage, int patch, List<Plant> plants) {
			this(condition, medium, minDepth, maxDepth, grounds, coverage, patch, plants, List.of());
		}
	}
}
