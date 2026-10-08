package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.feature.config.TreePalette;
import pl.polishforests.worldgen.feature.plan.TreeStandPlan;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Tree stand dispatcher {@code polishforests:tree_stand} (docs/03-m2-biomy.md §8.2): one placed feature in step 9 of
 * every biome, which plants the trees of the whole chunk from the chunk habitats ({@link ChunkHabitats}) and the tree
 * palette ({@link TreePalette}, JSON). The plan is a pure function ({@link TreeStandPlan}); the dispatcher has its own
 * random source from (world seed, chunk, layer salt), so features of other mods do not shift it. Each tree is a placed
 * feature {@code polishforests:tree/<species>} placed on the top ground block.
 *
 * <p>Basic version of step S5: tree palettes per biome, without zones (step S7).
 */
public final class TreeStandFeature implements Feature {
	public static final MapCodec<TreeStandFeature> CODEC = TreePalette.Rule.CODEC.listOf().fieldOf("rules")
			.xmap(rules -> new TreeStandFeature(new TreePalette(rules)), f -> f.palette.rules());

	private final TreePalette palette;
	private final List<Holder<PlacedFeature>> trees;
	private final TreeStandPlan.Palette plan;

	public TreeStandFeature(TreePalette palette) {
		this.palette = palette;
		this.trees = new ArrayList<>();
		int n = HabitatBiome.values().length;
		float[] perChunk = new float[n];
		int[][] index = new int[n][];
		int[][] weights = new int[n][];
		int[][] flags = new int[n][];
		for (HabitatBiome b : HabitatBiome.values()) {
			TreePalette.Rule rule = palette.rules().stream().filter(r -> r.biomes().contains(b)).findFirst().orElse(null);
			List<TreePalette.Entry> entries = rule == null ? List.of() : rule.trees();
			int k = b.ordinal();
			perChunk[k] = rule == null ? 0 : rule.treesPerChunk();
			index[k] = new int[entries.size()];
			weights[k] = new int[entries.size()];
			flags[k] = new int[entries.size()];
			for (int i = 0; i < entries.size(); i++) {
				TreePalette.Entry e = entries.get(i);
				int t = trees.indexOf(e.tree());
				if (t < 0) {
					trees.add(e.tree());
					t = trees.size() - 1;
				}
				index[k][i] = t;
				weights[k][i] = e.weight();
				flags[k][i] = e.species().flag();
			}
		}
		this.plan = new TreeStandPlan.Palette(perChunk, index, weights, flags);
	}

	public TreePalette palette() {
		return palette;
	}

	@Override
	public MapCodec<TreeStandFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		ChunkAccess chunk = level.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
		ChunkHabitats habitats = ModFeatures.habitats(chunk, generator, level.getSeed());
		if (habitats == null) {
			return false;
		}
		ChunkPos pos = chunk.getPos();
		boolean[] water = new boolean[256];
		for (int i = 0; i < 256; i++) {
			water[i] = habitats.hasWater(i);
		}
		long seed = TreeStandPlan.seed(level.getSeed(), pos.x(), pos.z(), TreeStandPlan.SALT);
		int[] planned = TreeStandPlan.of(habitats.codes(), water, plan, seed);
		WorldgenRandom treeRandom = new WorldgenRandom(new XoroshiroRandomSource(seed));
		boolean placed = false;
		for (int p : planned) {
			int column = p >>> 16;
			BlockPos at = new BlockPos(pos.getMinBlockX() + (column >> 4), habitats.top()[column] + 1,
					pos.getMinBlockZ() + (column & 15));
			placed |= trees.get(p & 0xFFFF).value().place(level, generator, treeRandom, at);
		}
		return placed;
	}

	@Override
	public Stream<Holder<Feature>> getSubFeatures() {
		return trees.stream().flatMap(t -> t.value().getFeatures());
	}
}
