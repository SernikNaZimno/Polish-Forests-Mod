package pl.polishforests.worldgen.feature.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Tree palette of the tree stand dispatcher (JSON from datagen, docs/03-m2-biomy.md §8.2 and §8.5): rules with a habitat
 * condition (biomes, zones, forest site types, associations, soils; {@link HabitatCondition}), the number of trees per
 * chunk and the species with their weights. The first rule whose condition matches a column applies, so the rules of
 * zones (no trees on willow scrub and herb fringes, stunted spruces at the timberline) and of associations (Populetum
 * albae, Abieti-Piceetum) come before the rule of their biome. A species with a range flag (beech, fir, native spruce,
 * hornbeam) grows only in columns whose habitat code has the flag, with its weight times the range ramp (§9).
 *
 * @param rules rules in order
 */
public record TreePalette(List<Rule> rules) {
	public static final Codec<Species> SPECIES_CODEC = Codec.STRING.comapFlatMap(id -> Arrays.stream(Species.values())
			.filter(s -> s.id().equals(id)).findFirst().map(DataResult::success)
			.orElseGet(() -> DataResult.error(() -> "Unknown species: " + id)), Species::id);

	/**
	 * A tree species in a rule.
	 *
	 * @param species       species (range flag, §9)
	 * @param tree          placed feature of the tree, {@code polishforests:tree/<species>} or a variant of it (§8.4)
	 * @param weight        share weight
	 * @param alternative   species that takes the weight where {@code species} is out of its range ("beech or spruce",
	 *                      §8.5); without it the weight is shared among the other species
	 * @param maxWaterDepth deepest water (blocks) the tree may stand in: 0 for dry columns only, 1–2 for willows and alders
	 */
	public record Entry(Species species, Holder<PlacedFeature> tree, int weight, Optional<Alternative> alternative,
			int maxWaterDepth) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				SPECIES_CODEC.fieldOf("species").forGetter(Entry::species),
				PlacedFeature.CODEC.fieldOf("tree").forGetter(Entry::tree),
				Codec.intRange(1, 10_000).fieldOf("weight").forGetter(Entry::weight),
				Alternative.CODEC.optionalFieldOf("alternative").forGetter(Entry::alternative),
				Codec.intRange(0, 2).optionalFieldOf("max_water_depth", 0).forGetter(Entry::maxWaterDepth)
		).apply(i, Entry::new));

		public Entry(Species species, Holder<PlacedFeature> tree, int weight) {
			this(species, tree, weight, Optional.empty(), 0);
		}
	}

	/**
	 * Alternative species of an entry.
	 *
	 * @param species species (range flag, §9)
	 * @param tree    placed feature of the tree
	 */
	public record Alternative(Species species, Holder<PlacedFeature> tree) {
		public static final Codec<Alternative> CODEC = RecordCodecBuilder.create(i -> i.group(
				SPECIES_CODEC.fieldOf("species").forGetter(Alternative::species),
				PlacedFeature.CODEC.fieldOf("tree").forGetter(Alternative::tree)
		).apply(i, Alternative::new));
	}

	/**
	 * A palette rule.
	 *
	 * @param condition     habitat condition of the columns the rule applies to
	 * @param treesPerChunk mean number of trees per chunk (§8.5)
	 * @param trees         species and weights (empty with 0 trees: a treeless zone)
	 */
	public record Rule(HabitatCondition condition, float treesPerChunk, List<Entry> trees) {
		public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
				HabitatCondition.MAP_CODEC.forGetter(Rule::condition),
				Codec.floatRange(0.0F, 16.0F).fieldOf("trees_per_chunk").forGetter(Rule::treesPerChunk),
				Entry.CODEC.listOf().optionalFieldOf("trees", List.of()).forGetter(Rule::trees)
		).apply(i, Rule::new));
	}
}
