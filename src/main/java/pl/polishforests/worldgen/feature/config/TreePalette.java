package pl.polishforests.worldgen.feature.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Tree palette of the tree stand dispatcher (JSON from datagen, docs/03-m2-biomy.md §8.2 and §8.5): for each rule, the
 * biomes it applies to, the number of trees per chunk and the species with their weights. Basic version of step S5: one
 * rule per biome, without zones, site types and associations (they come in step S7). A species with a range flag
 * (beech, fir, native spruce, hornbeam) grows only in columns whose habitat code has the flag (§9).
 *
 * @param rules rules; the first rule containing the column's biome applies
 */
public record TreePalette(List<Rule> rules) {
	public static final Codec<HabitatBiome> BIOME_CODEC = Codec.STRING.comapFlatMap(id -> {
		HabitatBiome b = HabitatBiome.byId(id);
		return b == null ? DataResult.error(() -> "Unknown habitat biome: " + id) : DataResult.success(b);
	}, HabitatBiome::id);

	public static final Codec<Species> SPECIES_CODEC = Codec.STRING.comapFlatMap(id -> Arrays.stream(Species.values())
			.filter(s -> s.id().equals(id)).findFirst().map(DataResult::success)
			.orElseGet(() -> DataResult.error(() -> "Unknown species: " + id)), Species::id);

	/**
	 * A tree species in a rule.
	 *
	 * @param species species (range flag, §9)
	 * @param tree    placed feature of the tree, {@code polishforests:tree/<species>} (§8.4)
	 * @param weight  share weight
	 */
	public record Entry(Species species, Holder<PlacedFeature> tree, int weight) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				SPECIES_CODEC.fieldOf("species").forGetter(Entry::species),
				PlacedFeature.CODEC.fieldOf("tree").forGetter(Entry::tree),
				Codec.intRange(1, 10_000).fieldOf("weight").forGetter(Entry::weight)
		).apply(i, Entry::new));
	}

	/**
	 * A palette rule.
	 *
	 * @param biomes        biomes the rule applies to
	 * @param treesPerChunk mean number of trees per chunk (§8.5)
	 * @param trees         species and weights
	 */
	public record Rule(List<HabitatBiome> biomes, float treesPerChunk, List<Entry> trees) {
		public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
				BIOME_CODEC.listOf().fieldOf("biomes").forGetter(Rule::biomes),
				Codec.floatRange(0.0F, 64.0F).fieldOf("trees_per_chunk").forGetter(Rule::treesPerChunk),
				Entry.CODEC.listOf().fieldOf("trees").forGetter(Rule::trees)
		).apply(i, Rule::new));
	}
}
