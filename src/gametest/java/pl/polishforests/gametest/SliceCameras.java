package pl.polishforests.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fixed cameras of the vertical slice (V0 review round 1): the table {@code src/test/resources/slice_cameras.txt}, chosen
 * once on the M2 world by {@code -Pgametest=slice -PfindSliceCameras} and used unchanged by every later run, so the
 * screenshots before and after a step show the same view (the build passes the path as
 * {@code -Dpolishforests.slice.cameras}). One line per camera: scale, place, camera, feet block x y z, yaw and pitch.
 * The cameras are shared by both vegetation modes, like the places.
 */
final class SliceCameras {
	/** A camera: the feet block of the player (the view is from x + 0.5, y + 1.62, z + 0.5), yaw and pitch in degrees. */
	record Camera(String name, int x, int y, int z, double yaw, double pitch) {
		Kind kind() {
			if (name.startsWith("bark_")) {
				return Kind.BARK;
			}
			if (name.equals("canopy")) {
				return Kind.CANOPY;
			}
			if (name.equals("eye") || name.startsWith("eye_") || name.endsWith("_eye")) {
				return Kind.EYE;
			}
			return Kind.AERIAL;
		}

		String line(String scale, String place) {
			return String.format(Locale.ROOT, "%s %s %s %d %d %d %.1f %.1f", scale, place, name, x, y, z, yaw, pitch);
		}
	}

	/** Eye height looking across the stand, a close-up of a trunk, looking up under the canopy, from above the forest. */
	enum Kind {
		EYE, BARK, CANOPY, AERIAL
	}

	private SliceCameras() {
	}

	static Path file() {
		String file = System.getProperty("polishforests.slice.cameras", "");
		if (file.isBlank()) {
			throw new AssertionError("No -Dpolishforests.slice.cameras (the build sets it to src/test/resources/slice_cameras.txt)");
		}
		return Path.of(file);
	}

	/** Cameras by "scale place", in the order of the file; empty when the file does not exist yet. */
	static Map<String, List<Camera>> read() {
		Map<String, List<Camera>> cameras = new LinkedHashMap<>();
		Path file = file();
		if (!Files.exists(file)) {
			return cameras;
		}
		try {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] f = line.split("\\s+");
				cameras.computeIfAbsent(f[0] + " " + f[1], k -> new ArrayList<>()).add(new Camera(f[2], Integer.parseInt(f[3]),
						Integer.parseInt(f[4]), Integer.parseInt(f[5]), Double.parseDouble(f[6]), Double.parseDouble(f[7])));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return cameras;
	}

	/** Writes the cameras (by "scale place") in the order of the places of the slice table. */
	static void write(Map<String, List<Camera>> cameras) {
		List<String> lines = new ArrayList<>(List.of(
				"# Fixed cameras of the vertical slice (V0 review round 1), seed 20260927, chosen on the M2 world by",
				"# ./gradlew runClientGameTest -Pgametest=slice -PfindSliceCameras and read by every later -Pgametest=slice run,",
				"# so the screenshots before and after a step show the same view. Columns: scale, place (slice_sites.txt), camera,",
				"# feet block x y z (the view from x + 0.5, y + 1.62, z + 0.5), yaw and pitch in degrees (yaw 0 faces south,",
				"# 90 west; pitch 90 looks down). Cameras: eye (eye height, free line of sight), canopy (straight up under the",
				"# canopy), 30m (30 blocks above the ground), bark_<log> (a trunk from about 3 blocks); transects: eye_<biome>",
				"# (the middle of each forest), edge_<biome>_<biome>_eye / _side (each border, along the line and from 35 blocks",
				"# above, across it), 30m (above the start) and, in the gameplay scale, overview (the whole line from the side)."));
		List<String> order = new ArrayList<>();
		for (SliceSites.Place p : SliceSites.read()) {
			order.add((p.realistic() ? "realistic" : "gameplay") + " " + p.name());
		}
		List<String> keys = new ArrayList<>(cameras.keySet());
		keys.sort((a, b) -> Integer.compare(order.indexOf(a), order.indexOf(b)));
		for (String key : keys) {
			if (!order.contains(key)) {
				continue;
			}
			String[] k = key.split(" ");
			for (Camera c : cameras.get(key)) {
				lines.add(c.line(k[0], k[1]));
			}
		}
		try {
			Files.writeString(file(), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
