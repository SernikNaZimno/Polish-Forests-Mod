package pl.polskielasy.worldgen.chunk;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.dimension.DimensionType;
import org.jspecify.annotations.Nullable;
import pl.polskielasy.PolskieLasy;
import pl.polskielasy.worldgen.landscape.LandscapeScale;

/** Skala świata "Polska": pozioma, pionowa i odpowiadający jej typ wymiaru. */
public enum PolskaScale implements StringRepresentable {
	/** Rzeczywiste rozmiary i wysokości (decyzje A3 i A4). */
	REALISTYCZNA("realistyczna", LandscapeScale.REALISTIC, VerticalScale.REAL, "polska"),
	/** Krajobrazy ok. 2 razy większe od biomów wanilijnych, wysokości obniżone proporcjonalnie. */
	ROZGRYWKA("rozgrywka", LandscapeScale.GAMEPLAY, VerticalScale.GAMEPLAY, "polska_rozgrywka");

	public static final Codec<PolskaScale> CODEC = StringRepresentable.fromEnum(PolskaScale::values);

	private final String id;
	private final LandscapeScale landscape;
	private final VerticalScale vertical;
	private final ResourceKey<DimensionType> dimensionType;

	PolskaScale(String id, LandscapeScale landscape, VerticalScale vertical, String dimensionType) {
		this.id = id;
		this.landscape = landscape;
		this.vertical = vertical;
		this.dimensionType = ResourceKey.create(Registries.DIMENSION_TYPE, PolskieLasy.id(dimensionType));
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

	/** Skala, której typem wymiaru jest {@code type}, albo null dla innych typów wymiaru. */
	public static @Nullable PolskaScale byDimensionType(ResourceKey<DimensionType> type) {
		for (PolskaScale scale : values()) {
			if (scale.dimensionType.equals(type)) {
				return scale;
			}
		}
		return null;
	}
}
