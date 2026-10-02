/**
 * Zamrożona kopia pakietu {@code pl.polishforests.worldgen.landscape} z końca M1 (M2, krok S0).
 *
 * <p>Kod skopiowany bez zmian (poza nazwą pakietu) z migawki {@code migawki/m1-przed-S0.tar}. Służy jako:
 * <ul>
 * <li>dokładny wzorzec terenu M1 dla {@code GoldenTerrainTest}: przy różnicy skrótu test liczy, o ile
 * różnią się wartości (np. 1e-12 m po zmianie kolejności działań, czy prawdziwa zmiana terenu);</li>
 * <li>punkt odniesienia kosztu w {@code SampleKosztTest}: stary i nowy {@code sample} mierzone na
 * przemian w tej samej JVM, co usuwa szum między uruchomieniami (±10–20%).</li>
 * </ul>
 *
 * <p>NIE ZMIENIAĆ tych plików przy pracy nad modelem. Jeśli teren zmienia się zamierzenie (nowy plik
 * wzorcowy złotego testu), kopia nadal opisuje M1; porównanie z nią pokaże wtedy różnicę, i dobrze.
 */
package pl.polishforests.worldgen.landscape.m1;
