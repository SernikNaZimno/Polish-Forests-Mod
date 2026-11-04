package pl.polishforests.worldgen.vanilla;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.chunk.ModBiomeKeys;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Biome source {@code polishforests:vanilla_forests} of the world type "Default + Polish Forests" (step W1 of
 * docs/04-pionowy-wycinek.md, decision G.36 U-3): the biomes of another source (the vanilla overworld multi-noise source
 * in the world preset) with the vanilla forests replaced by the habitat biomes of the mod ({@link VanillaForests}). The
 * climate, the terrain, the caves, the cave biomes and every other biome stay vanilla; a replaced biome keeps the climate
 * point of the vanilla forest, so the forests of the mod lie where the vanilla ones would. The replacement is a pure
 * function of the source's biome, so {@code /locate biome}, the structures (by the biome tags, which hold the habitat
 * biomes) and the chunks see the same biomes.
 *
 * <p>The source also carries the forests of that world ({@link #world()}: the chunk habitats from the vanilla terrain,
 * the stand map, the blend of the slice forests).
 */
public final class VanillaForestsBiomeSource extends BiomeSource {
	public static final MapCodec<VanillaForestsBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			BiomeSource.CODEC.fieldOf("source").forGetter(VanillaForestsBiomeSource::source),
			RegistryOps.retrieveGetter(Registries.BIOME)
	).apply(i, i.stable(VanillaForestsBiomeSource::new)));

	private final BiomeSource source;
	private final HolderGetter<Biome> biomes;
	private final VanillaForestWorld world;
	private volatile @Nullable Map<Holder<Biome>, Holder<Biome>> swaps;

	public VanillaForestsBiomeSource(BiomeSource source, HolderGetter<Biome> biomes) {
		this.source = source;
		this.biomes = biomes;
		this.world = new VanillaForestWorld(this);
	}

	/** The source whose forests are replaced. */
	public BiomeSource source() {
		return source;
	}

	/** The forests of the world of this source. */
	public VanillaForestWorld world() {
		return world;
	}

	/** The holder of a habitat biome. */
	public Holder<Biome> holder(HabitatBiome biome) {
		return biomes.getOrThrow(ModBiomeKeys.key(biome));
	}

	/** The replacement of each vanilla forest of the source (identity map of the source's holders). */
	private Map<Holder<Biome>, Holder<Biome>> swaps() {
		Map<Holder<Biome>, Holder<Biome>> m = swaps;
		if (m == null) {
			synchronized (this) {
				m = swaps;
				if (m == null) {
					m = new IdentityHashMap<>();
					for (Holder<Biome> h : source.possibleBiomes()) {
						HabitatBiome target = replacement(h);
						if (target != null) {
							m.put(h, holder(target));
						}
					}
					swaps = m;
				}
			}
		}
		return m;
	}

	/** The habitat biome replacing a vanilla biome, or null when it stays. */
	static @Nullable HabitatBiome replacement(Holder<Biome> biome) {
		ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
		if (key == null || !"minecraft".equals(key.identifier().getNamespace())) {
			return null;
		}
		return VanillaForests.replacement(key.identifier().getPath());
	}

	/** The biome of this source for a biome of the wrapped source. */
	public Holder<Biome> swap(Holder<Biome> biome) {
		Holder<Biome> s = swaps().get(biome);
		return s != null ? s : biome;
	}

	/** The vanilla forests the source replaces (for the tests and the commands). */
	public List<Holder<Biome>> replaced() {
		return List.copyOf(swaps().keySet());
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return source.possibleBiomes().stream().map(this::swap).distinct();
	}

	@Override
	public BiomeResolver createResolver(Climate.Sampler sampler) {
		BiomeResolver inner = source.createResolver(sampler);
		return (qx, qy, qz) -> swap(inner.getNoiseBiome(qx, qy, qz));
	}

	@Override
	public BiomeResolver createResolverForChunk(Climate.Sampler sampler, int minQuartX, int minQuartY, int minQuartZ,
			int quartSizeX, int quartSizeY, int quartSizeZ) {
		BiomeResolver inner = source.createResolverForChunk(sampler, minQuartX, minQuartY, minQuartZ, quartSizeX, quartSizeY,
				quartSizeZ);
		return (qx, qy, qz) -> swap(inner.getNoiseBiome(qx, qy, qz));
	}

	@Override
	public void addDebugInfo(List<String> result, BlockPos feetPos, Climate.Sampler sampler) {
		source.addDebugInfo(result, feetPos, sampler);
	}
}
