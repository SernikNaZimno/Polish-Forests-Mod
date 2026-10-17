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
	/**
	 * Step S9: the tree species of the first rule matching a habitat code, in rule order (with the layers of a stand of the
	 * vertical slice, step V9), as the stand grows them there:
	 * a species with a range flag outside its range ({@link pl.polishforests.worldgen.habitat.Habitat#has}) is replaced
	 * by its alternative or left out (for {@code /polishforests here}; ecotones and the range ramp are not considered).
	 */
	public List<Species> species(int code) {
		for (Rule r : rules) {
			if (!r.condition().match().matches(code)) {
				continue;
			}
			List<Species> out = new java.util.ArrayList<>();
			List<Entry> entries = new java.util.ArrayList<>(r.trees());
			// Step V9: the layers of the stand too (managed canopy, subcanopy, regeneration).
			r.stand().ifPresent(st -> {
				entries.addAll(st.managedCanopy());
				entries.addAll(st.subcanopy());
				entries.addAll(st.regeneration());
				entries.addAll(st.gapRegeneration());
			});
			for (Entry e : entries) {
				Species s = e.species();
				if (e.rangeRamp() && s.flag() != 0 && !pl.polishforests.worldgen.habitat.Habitat.has(code, s)) {
					s = e.alternative().map(Alternative::species).orElse(null);
					if (s != null && s.flag() != 0 && !pl.polishforests.worldgen.habitat.Habitat.has(code, s)) {
						s = null;
					}
				}
				if (s != null && !out.contains(s)) {
					out.add(s);
				}
			}
			return out;
		}
		return List.of();
	}

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
	 * @param rangeRamp     whether the species' range flag and ramp apply (false: full weight, for a biome that lies
	 *                      within the species' range by definition, such as beech in the beech forests)
	 */
	public record Entry(Species species, Holder<PlacedFeature> tree, int weight, Optional<Alternative> alternative,
			int maxWaterDepth, boolean rangeRamp) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				SPECIES_CODEC.fieldOf("species").forGetter(Entry::species),
				PlacedFeature.CODEC.fieldOf("tree").forGetter(Entry::tree),
				Codec.intRange(1, 10_000).fieldOf("weight").forGetter(Entry::weight),
				Alternative.CODEC.optionalFieldOf("alternative").forGetter(Entry::alternative),
				Codec.intRange(0, 2).optionalFieldOf("max_water_depth", 0).forGetter(Entry::maxWaterDepth),
				Codec.BOOL.optionalFieldOf("range_ramp", true).forGetter(Entry::rangeRamp)
		).apply(i, Entry::new));

		public Entry(Species species, Holder<PlacedFeature> tree, int weight) {
			this(species, tree, weight, Optional.empty(), 0, true);
		}

		public Entry(Species species, Holder<PlacedFeature> tree, int weight, Optional<Alternative> alternative,
				int maxWaterDepth) {
			this(species, tree, weight, alternative, maxWaterDepth, true);
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
	 * The stand of a rule of the vertical slice (step V9 of docs/04-pionowy-wycinek.md): the rule's trees become the
	 * layered stand of the stand map ({@code ForestStandMap}): every kept candidate of the M2 pattern has a crown layer
	 * (canopy a1, subcanopy a2 or regeneration; {@code CrownTerritories}) and becomes a tree with the chance of its stand
	 * (managed or natural, by age class; in a canopy gap {@code gap_chance}), of a species from the list of its layer.
	 * The canopy takes {@link Rule#trees} ({@code managed_canopy} instead in managed stands, when given), the subcanopy
	 * {@code subcanopy}, the regeneration under the canopy {@code regeneration} and in the inner part of a canopy gap
	 * {@code gap_regeneration}. The trees per chunk of the rule are then only nominal; the M2 gap noise does not apply
	 * (natural stands have canopy gaps, managed ones none).
	 *
	 * @param managedCanopy   canopy species of managed stands (empty: the rule's trees)
	 * @param subcanopy       species of the subcanopy
	 * @param regeneration    species of the regeneration under the canopy
	 * @param gapRegeneration species of the regeneration in canopy gaps
	 * @param managedChance   chance of a candidate to grow a tree in a managed stand, by age class (pole stage, mature,
	 *                        old growth)
	 * @param naturalChance   the same in a natural stand
	 * @param gapChance       chance in a canopy gap
	 */
	public record Stand(List<Entry> managedCanopy, List<Entry> subcanopy, List<Entry> regeneration, List<Entry> gapRegeneration,
			List<Float> managedChance, List<Float> naturalChance, float gapChance) {
		private static final Codec<List<Float>> CHANCES = Codec.floatRange(0, 1).listOf(3, 3);
		public static final Codec<Stand> CODEC = RecordCodecBuilder.create(i -> i.group(
				Entry.CODEC.listOf().optionalFieldOf("managed_canopy", List.of()).forGetter(Stand::managedCanopy),
				Entry.CODEC.listOf().fieldOf("subcanopy").forGetter(Stand::subcanopy),
				Entry.CODEC.listOf().fieldOf("regeneration").forGetter(Stand::regeneration),
				Entry.CODEC.listOf().fieldOf("gap_regeneration").forGetter(Stand::gapRegeneration),
				CHANCES.fieldOf("managed_chance").forGetter(Stand::managedChance),
				CHANCES.fieldOf("natural_chance").forGetter(Stand::naturalChance),
				Codec.floatRange(0, 1).fieldOf("gap_chance").forGetter(Stand::gapChance)
		).apply(i, Stand::new));
	}

	/**
	 * A palette rule.
	 *
	 * @param condition     habitat condition of the columns the rule applies to
	 * @param treesPerChunk mean number of trees per chunk (§8.5); nominal for a rule with a {@code stand}
	 * @param trees         species and weights (empty with 0 trees: a treeless zone); the canopy of a rule with a stand
	 * @param stand         the layered stand of the vertical slice (step V9), or empty for the M2 stand
	 */
	public record Rule(HabitatCondition condition, float treesPerChunk, List<Entry> trees, Optional<Stand> stand) {
		public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
				HabitatCondition.MAP_CODEC.forGetter(Rule::condition),
				Codec.floatRange(0.0F, 16.0F).fieldOf("trees_per_chunk").forGetter(Rule::treesPerChunk),
				Entry.CODEC.listOf().optionalFieldOf("trees", List.of()).forGetter(Rule::trees),
				Stand.CODEC.optionalFieldOf("stand").forGetter(Rule::stand)
		).apply(i, Rule::new));

		public Rule(HabitatCondition condition, float treesPerChunk, List<Entry> trees) {
			this(condition, treesPerChunk, trees, Optional.empty());
		}
	}
}
