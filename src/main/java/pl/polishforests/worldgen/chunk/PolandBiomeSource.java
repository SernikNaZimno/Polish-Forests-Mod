package pl.polishforests.worldgen.chunk;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import pl.polishforests.worldgen.habitat.AltitudinalBelts;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeType;
import pl.polishforests.worldgen.landscape.Substrate;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Biome source of the "Poland" world. In M1 it maps landscape types to placeholder biomes
 * (vanilla ones, given in the world preset); in M2 the mod's habitat biomes will replace them.
 *
 * <p>The landscape model is bound by {@link PolandChunkGenerator} once the world seed is known.
 */
public final class PolandBiomeSource extends BiomeSource {
	public static final MapCodec<PolandBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Biome.CODEC.fieldOf("outwash_plain").forGetter(s -> s.outwashPlain),
			Biome.CODEC.fieldOf("moraine").forGetter(s -> s.moraine),
			Biome.CODEC.fieldOf("plain").forGetter(s -> s.plain),
			Biome.CODEC.fieldOf("floodplain").forGetter(s -> s.floodplain),
			Biome.CODEC.fieldOf("river").forGetter(s -> s.river),
			Biome.CODEC.fieldOf("lake").forGetter(s -> s.lake),
			Biome.CODEC.fieldOf("peat").forGetter(s -> s.peat),
			Biome.CODEC.fieldOf("foothills").forGetter(s -> s.foothills),
			Biome.CODEC.fieldOf("mountains").forGetter(s -> s.mountains),
			Biome.CODEC.fieldOf("mountains_high").forGetter(s -> s.mountainsHigh),
			Biome.CODEC.optionalFieldOf("sea").forGetter(s -> java.util.Optional.of(s.sea)),
			Biome.CODEC.optionalFieldOf("beach").forGetter(s -> java.util.Optional.of(s.beach))
	).apply(i, i.stable(PolandBiomeSource::new)));

	private final Holder<Biome> outwashPlain;
	private final Holder<Biome> moraine;
	private final Holder<Biome> plain;
	private final Holder<Biome> floodplain;
	private final Holder<Biome> river;
	private final Holder<Biome> lake;
	private final Holder<Biome> peat;
	private final Holder<Biome> foothills;
	private final Holder<Biome> mountains;
	private final Holder<Biome> mountainsHigh;
	private final Holder<Biome> sea;
	private final Holder<Biome> beach;

	private volatile LandscapeModel model;
	private volatile VerticalScale vertical = VerticalScale.REAL;

	public PolandBiomeSource(Holder<Biome> outwashPlain, Holder<Biome> moraine, Holder<Biome> plain, Holder<Biome> floodplain,
			Holder<Biome> river, Holder<Biome> lake, Holder<Biome> peat, Holder<Biome> foothills, Holder<Biome> mountains,
			Holder<Biome> mountainsHigh, java.util.Optional<Holder<Biome>> sea, java.util.Optional<Holder<Biome>> beach) {
		this.outwashPlain = outwashPlain;
		this.moraine = moraine;
		this.plain = plain;
		this.floodplain = floodplain;
		this.river = river;
		this.lake = lake;
		this.peat = peat;
		this.foothills = foothills;
		this.mountains = mountains;
		this.mountainsHigh = mountainsHigh;
		this.sea = sea.orElse(lake);
		this.beach = beach.orElse(outwashPlain);
	}

	void bind(LandscapeModel model, VerticalScale vertical) {
		this.vertical = vertical;
		this.model = model;
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return Stream.of(outwashPlain, moraine, plain, floodplain, river, lake, peat, foothills, mountains, mountainsHigh, sea,
				beach)
				.distinct();
	}

	/** Biome for a column and a block height. */
	public Holder<Biome> biomeFor(ColumnSample s, int blockY) {
		if (s.type() == LandscapeType.SEA) {
			return sea;
		}
		if (s.waterKind() == WaterKind.SEA && s.hasWater()) {
			return lake;
		}
		if (s.waterKind() == WaterKind.RIVER && s.hasWater()) {
			return river;
		}
		if (s.waterKind().isLake() && s.hasWater()) {
			return lake;
		}
		if (s.substrate() == Substrate.PEAT) {
			return peat;
		}
		if (s.substrate() == Substrate.ALLUVIUM) {
			return floodplain;
		}
		return switch (s.type()) {
			case OUTWASH_PLAIN -> outwashPlain;
			case MORAINE_PLATEAU -> moraine;
			case OLD_GLACIAL_PLAIN -> plain;
			case FOOTHILLS -> foothills;
			case BESKIDS -> AltitudinalBelts.isUpperMontane(vertical.metersAboveSea(blockY)) ? mountainsHigh : mountains;
			case COASTLAND -> beach;
			case SEA -> sea;
		};
	}

	@Override
	public BiomeResolver createResolver(Climate.Sampler sampler) {
		LandscapeModel m = model;
		if (m == null) {
			return (qx, qy, qz) -> plain;
		}
		return (qx, qy, qz) -> biomeFor(m.sample(QuartPos.toBlock(qx) + 2, QuartPos.toBlock(qz) + 2),
				QuartPos.toBlock(qy) + 2);
	}

	/**
	 * Resolver for a single chunk: the model sample is computed once per quart column, not
	 * for each of the roughly 760 vertical quart levels.
	 */
	@Override
	public BiomeResolver createResolverForChunk(Climate.Sampler sampler, int minQuartX, int minQuartY, int minQuartZ,
			int quartSizeX, int quartSizeY, int quartSizeZ) {
		LandscapeModel m = model;
		if (m == null) {
			return createResolver(sampler);
		}
		ColumnSample[] cache = new ColumnSample[quartSizeX * quartSizeZ];
		return (qx, qy, qz) -> {
			int ix = qx - minQuartX;
			int iz = qz - minQuartZ;
			if (ix < 0 || iz < 0 || ix >= quartSizeX || iz >= quartSizeZ) {
				return biomeFor(m.sample(QuartPos.toBlock(qx) + 2, QuartPos.toBlock(qz) + 2), QuartPos.toBlock(qy) + 2);
			}
			int idx = ix * quartSizeZ + iz;
			ColumnSample s = cache[idx];
			if (s == null) {
				s = m.sample(QuartPos.toBlock(qx) + 2, QuartPos.toBlock(qz) + 2);
				cache[idx] = s;
			}
			return biomeFor(s, QuartPos.toBlock(qy) + 2);
		};
	}
}
