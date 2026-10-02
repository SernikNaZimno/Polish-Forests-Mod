package pl.polishforests.worldgen.landscape.m1;

// Zamrożona kopia kodu M1 (M2, krok S0), skopiowana bez zmian poza pakietem. Wzorzec dla
// GoldenTerrainTest i SampleKosztTest. NIE ZMIENIAĆ: zob. package-info.java.

/**
 * Skala pozioma krajobrazu. Model liczy zawsze w metrach; ta klasa mówi, jak duże są
 * poszczególne rzędy form terenu.
 *
 * @param regionSize      średni rozmiar makroregionu w metrach (przy suwaku 100%)
 * @param meso            mnożnik form średnich: doliny rzek, rynny, pasma moren, pola wydm, poziom bazowy
 * @param local           mnożnik form lokalnych: pagórki, oczka, wały, jeziora w rynnach
 * @param mountainSpacing mnożnik rozstawu grzbietów i dolin w górach
 * @param channel         mnożnik szerokości koryt rzecznych
 */
public record LandscapeScale(String id, double regionSize, double meso, double local, double mountainSpacing,
		double channel) {
	/** Rzeczywiste rozmiary: makroregiony ok. 64 km, formy 1:1. */
	public static final LandscapeScale REALISTIC = new LandscapeScale("realistyczna", 64_000, 1.0, 1.0, 1.0, 1.0);

	/**
	 * Skala przyjazna rozgrywce: krajobrazy ok. 1,4 km, czyli ok. 2 razy większe od typowych biomów
	 * wanilijnych. Formy średnie są ok. 7 razy mniejsze, lokalne o połowę, a wysokości ściska
	 * osobne odwzorowanie pionowe, żeby nachylenia stoków zostały podobne.
	 */
	public static final LandscapeScale GAMEPLAY = new LandscapeScale("rozgrywka", 1_400, 0.15, 0.5, 0.3, 0.2);

	/** Stosunek rozmiaru regionu do rzeczywistego; skaluje pola stref (pasma górskie, zlodowacenie). */
	public double zone() {
		return regionSize / REALISTIC.regionSize;
	}
}
