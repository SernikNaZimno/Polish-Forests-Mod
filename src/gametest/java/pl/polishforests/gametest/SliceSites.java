package pl.polishforests.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed places of the vertical slice (step V0, docs/04-pionowy-wycinek.md §3): the table
 * {@code src/test/resources/slice_sites.txt}, found with {@code HabitatTargets} and checked by {@code SliceSitesTest}; the
 * build passes its path as {@code -Dpolishforests.slice.sites}. Read by {@link SliceClientGameTest} and by the
 * {@code stages} measurement of {@link PerformanceClientGameTest} with {@code -Psites=slice}.
 */
final class SliceSites {
	/**
	 * A place: a site (biome id and center) or a transect (biome null; start (x, z) and end (x2, z2)). {@code modes} is
	 * {@code both}, {@code natural} or {@code present_day}.
	 */
	record Place(boolean realistic, String modes, String name, String biome, int x, int z, int x2, int z2) {
		boolean transect() {
			return biome == null;
		}

		boolean in(boolean presentDay) {
			return modes.equals("both") || modes.equals(presentDay ? "present_day" : "natural");
		}

		/** Center of a site, middle of a transect. */
		int[] middle() {
			return transect() ? new int[] {(x + x2) / 2, (z + z2) / 2} : new int[] {x, z};
		}
	}

	/** The fresh pine → mixed pine-oak → mixed → oak-hornbeam sequence of a transect (biome ids). */
	static final List<String> SEQUENCE = List.of("fresh_pine_forest", "mixed_pine_forest", "mixed_forest", "oak_hornbeam_forest");

	private SliceSites() {
	}

	static List<Place> read() {
		String file = System.getProperty("polishforests.slice.sites", "");
		if (file.isBlank()) {
			throw new AssertionError("No -Dpolishforests.slice.sites (the build sets it to src/test/resources/slice_sites.txt)");
		}
		List<Place> places = new ArrayList<>();
		try {
			for (String line : Files.readAllLines(Path.of(file), StandardCharsets.UTF_8)) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] f = line.split("\\s+");
				boolean real = f[0].equals("realistic");
				if (f[3].equals("transect")) {
					places.add(new Place(real, f[1], f[2], null, Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]),
							Integer.parseInt(f[7])));
				} else {
					places.add(new Place(real, f[1], f[2], f[3], Integer.parseInt(f[4]), Integer.parseInt(f[5]), 0, 0));
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return places;
	}

	/** The places of a scale and mode. */
	static List<Place> of(boolean realistic, boolean presentDay) {
		return read().stream().filter(p -> p.realistic() == realistic && p.in(presentDay)).toList();
	}
}
