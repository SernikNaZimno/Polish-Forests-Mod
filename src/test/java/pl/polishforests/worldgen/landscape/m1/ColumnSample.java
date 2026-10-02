package pl.polishforests.worldgen.landscape.m1;

// Zamrożona kopia kodu M1 (M2, krok S0), skopiowana bez zmian poza pakietem. Wzorzec dla
// GoldenTerrainTest i SampleKosztTest. NIE ZMIENIAĆ: zob. package-info.java.

/**
 * Wynik próbkowania modelu krajobrazu w jednej kolumnie.
 *
 * @param surface     wysokość powierzchni gruntu w metrach n.p.m. (także dna pod wodą)
 * @param waterLevel  poziom lustra wody w pełnych metrach n.p.m.; {@link Integer#MIN_VALUE}, gdy brak wody
 * @param waterKind   rodzaj wody
 * @param type        dominujący typ krajobrazu
 * @param substrate   utwór powierzchniowy
 * @param coverDepth  miąższość utworów czwartorzędowych lub zwietrzeliny nad skałą litą, w metrach
 */
public record ColumnSample(double surface, int waterLevel, WaterKind waterKind, LandscapeType type,
		Substrate substrate, double coverDepth) {
	public static final int NO_WATER = Integer.MIN_VALUE;

	public boolean hasWater() {
		return waterLevel != NO_WATER && waterLevel > surfaceMeters();
	}

	/** Wysokość górnej ściany najwyższego bloku gruntu, w pełnych metrach. */
	public int surfaceMeters() {
		return (int) Math.floor(surface);
	}
}
