package pl.polishforests.worldgen.chunk;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.dimension.DimensionType;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/** Skala świata "Polska": pozioma, pionowa i odpowiadający jej typ wymiaru. */
public enum PolandScale implements StringRepresentable {
	/** Rzeczywiste rozmiary i wysokości (decyzje A3 i A4). */
	REALISTIC("realistic", LandscapeScale.REALISTIC, VerticalScale.REAL, "poland"),
	/** Krajobrazy ok. 2 razy większe od biomów wanilijnych, wysokości obniżone proporcjonalnie. */
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

	/** Skala, której typem wymiaru jest {@code type}, albo null dla innych typów wymiaru. */
	public static @Nullable PolandScale byDimensionType(ResourceKey<DimensionType> type) {
		for (PolandScale scale : values()) {
			if (scale.dimensionType.equals(type)) {
				return scale;
			}
		}
		return null;
	}
}
