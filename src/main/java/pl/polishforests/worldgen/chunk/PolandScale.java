package pl.polishforests.worldgen.chunk;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.dimension.DimensionType;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/** Scale of the "Poland" world: horizontal, vertical and the matching dimension type. */
public enum PolandScale implements StringRepresentable {
	/** Real sizes and heights (decisions A3 and A4). */
	REALISTIC("realistic", LandscapeScale.REALISTIC, VerticalScale.REAL, "poland"),
	/** Landscapes about 2 times larger than vanilla biomes, heights reduced proportionally. */
	GAMEPLAY("gameplay", LandscapeScale.GAMEPLAY, VerticalScale.GAMEPLAY, "poland_gameplay");

	public static final Codec<PolandScale> CODEC = StringRepresentable.fromEnum(PolandScale::values);

	private final String id;
	private final LandscapeScale landscape;
	private final VerticalScale vertical;
	private final ResourceKey<DimensionType> dimensionType;

	PolandScale(String id, LandscapeScale landscape, VerticalScale vertical, String dimensionType) {
		this.id = id;
		this.landscape = landscape;
		this.vertical = vertical;
		this.dimensionType = ResourceKey.create(Registries.DIMENSION_TYPE, PolishForests.id(dimensionType));
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public LandscapeScale landscape() {
		return landscape;
	}

	public VerticalScale vertical() {
		return vertical;
	}

	public ResourceKey<DimensionType> dimensionType() {
		return dimensionType;
	}

	/** The scale whose dimension type is {@code type}, or null for other dimension types. */
	public static @Nullable PolandScale byDimensionType(ResourceKey<DimensionType> type) {
		for (PolandScale scale : values()) {
			if (scale.dimensionType.equals(type)) {
				return scale;
			}
		}
		return null;
	}
}
