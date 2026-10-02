package pl.polishforests.worldgen.chunk;

import net.minecraft.world.level.LevelHeightAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Pionowa rama świata "Polska" (decyzja A4, zmieniona 2026-09-27).
 *
 * <p>Poziom morza jest na wanilijnym Y 63, a dół świata na Y -64, więc wanilijne struktury
 * podziemne i rudy leżą pod terenem jak w zwykłym świecie. Silnik nie pozwala na bloki powyżej
 * Y 2031, dlatego wysokość jest odwzorowana 1:1 do ok. 900 m n.p.m., a powyżej płynnie ściskana
 * (ok. 1,76 razy powyżej 1200 m). Rysy (2499 m) wypadają na Y 2000. Model krajobrazu i piętra
 * roślinności liczą zawsze w prawdziwych metrach.
 *
 * <p>Wartości muszą zgadzać się z data/polskielasy/dimension_type/polska.json.
 */
public final class PolandDimension {
	public static final int MIN_Y = -64;
	/** Y od -64 do 2031, czyli maksimum silnika. */
	public static final int HEIGHT = 2096;
	/** Y górnej ściany wody morskiej, czyli 0 m n.p.m. */
	public static final int SEA_LEVEL_Y = 63;
	/** Poniżej tej wysokości skała przechodzi w łupek głębinowy, jak w wanilii. */
	public static final int DEEP_ROCK_Y = 0;

	/** Wysokość, od której działa ściskanie gór (środek płynnego przejścia). */
	public static final double KNEE_METERS = 1_200.0;
	/** Szerokość płynnego przejścia w metrach. */
	private static final double KNEE_SOFTNESS = 80.0;
	/** Współczynnik ściskania powyżej kolana, dobrany tak, aby Rysy wypadły na Y 2000. */
	public static final double COMPRESSION = 1.7626;
	/** Poniżej tej wysokości odwzorowanie jest dokładną tożsamością. */
	private static final double IDENTITY_BELOW = KNEE_METERS - 4 * KNEE_SOFTNESS;
	private static final double SOFTPLUS_AT_IDENTITY_END = softplus(-4.0);
	private static final double SQUEEZE = 1.0 - 1.0 / COMPRESSION;

	/** Tablica odwrotna: wysokość n.p.m. górnej ściany bloku dla każdego Y świata. */
	private static final double[] METERS_BY_Y = new double[HEIGHT + 1];

	static {
		for (int i = 0; i <= HEIGHT; i++) {
			METERS_BY_Y[i] = metersForBlocks(MIN_Y + i - SEA_LEVEL_Y);
		}
	}

	private PolandDimension() {
	}

	/**
	 * Czy dany poziom ma pionową ramę świata "Polska" w którejkolwiek skali (rozpoznawaną po
	 * zakresie wysokości). Wcześniej rozpoznawała tylko skalę rzeczywistą.
	 */
	public static boolean isPoland(LevelHeightAccessor level) {
		return scaleOf(level) != null;
	}

	/**
	 * Skala pionowa rozpoznana po zakresie wysokości poziomu: 2096 bloków to skala rzeczywista,
	 * 832 to skala rozgrywki; null dla innych ram. To rozpoznanie awaryjne, pewniejszy jest typ
	 * wymiaru ({@link PolandScale#byDimensionType}).
	 */
	public static @Nullable VerticalScale scaleOf(LevelHeightAccessor level) {
		for (PolandScale scale : PolandScale.values()) {
			VerticalScale v = scale.vertical();
			if (level.getMinY() == v.minY() && level.getHeight() == v.height()) {
				return v;
			}
		}
		return null;
	}

	public static int maxY() {
		return MIN_Y + HEIGHT - 1;
	}

	/** Liczba bloków nad poziomem morza odpowiadająca wysokości {@code meters} n.p.m. */
	public static double blocksForMeters(double meters) {
		if (meters <= IDENTITY_BELOW) {
			return meters;
		}
		double x = (meters - KNEE_METERS) / KNEE_SOFTNESS;
		return meters - SQUEEZE * KNEE_SOFTNESS * (softplus(x) - SOFTPLUS_AT_IDENTITY_END);
	}

	/** Odwrotność {@link #blocksForMeters}: wysokość n.p.m. dla liczby bloków nad morzem. */
	public static double metersForBlocks(double blocks) {
		if (blocks <= IDENTITY_BELOW) {
			return blocks;
		}
		double lo = blocks;
		double hi = blocks * COMPRESSION + 1;
		for (int i = 0; i < 60; i++) {
			double mid = 0.5 * (lo + hi);
			if (blocksForMeters(mid) < blocks) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return 0.5 * (lo + hi);
	}

	/** Y najwyższego bloku gruntu dla powierzchni o wysokości {@code meters} n.p.m. */
	public static int topBlockY(double meters) {
		return (int) Math.floor(SEA_LEVEL_Y + blocksForMeters(meters)) - 1;
	}

	/** Wysokość n.p.m. górnej ściany bloku leżącego na {@code y}. */
	public static double metersAboveSea(int y) {
		int i = y + 1 - MIN_Y;
		if (i >= 0 && i < METERS_BY_Y.length) {
			return METERS_BY_Y[i];
		}
		return metersForBlocks(y + 1 - SEA_LEVEL_Y);
	}

	private static double softplus(double x) {
		return x > 30 ? x : Math.log1p(Math.exp(x));
	}
}
