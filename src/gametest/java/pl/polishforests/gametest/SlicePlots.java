package pl.polishforests.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.tree.AgeClass;

/**
 * Sample plots of the norms card by stand (step V9, docs/04-pionowy-wycinek.md §6): the table
 * {@code src/test/resources/slice_plots.txt}, found and checked by {@code SlicePlotsTest}; the build passes its path as
 * {@code -Dpolishforests.slice.plots}. Read by {@link SliceClientGameTest}.
 */
final class SlicePlots {
	/** A plot: scale, natural or managed stands, the age class of a managed pine plot (null: any), name, biome id, center. */
	record Plot(boolean realistic, boolean natural, @Nullable AgeClass age, String name, String biome, int x, int z) {
	}

	private SlicePlots() {
	}

	static List<Plot> read() {
		String file = System.getProperty("polishforests.slice.plots", "");
		if (file.isBlank()) {
			throw new AssertionError("No -Dpolishforests.slice.plots (the build sets it to src/test/resources/slice_plots.txt)");
		}
		List<Plot> plots = new ArrayList<>();
		try {
			for (String line : Files.readAllLines(Path.of(file), StandardCharsets.UTF_8)) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] f = line.split("\s+");
				plots.add(new Plot(f[0].equals("realistic"), f[1].equals("natural"),
						f[2].equals("any") ? null : AgeClass.valueOf(f[2].toUpperCase(Locale.ROOT)), f[3], f[4], Integer.parseInt(f[5]),
						Integer.parseInt(f[6])));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return plots;
	}

	/** The plots of a scale. */
	static List<Plot> of(boolean realistic) {
		return read().stream().filter(p -> p.realistic() == realistic).toList();
	}
}
