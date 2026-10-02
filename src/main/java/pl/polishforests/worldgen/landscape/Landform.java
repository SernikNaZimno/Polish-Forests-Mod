package pl.polishforests.worldgen.landscape;

import java.util.List;

/**
 * Landforms recognised by {@link LandscapeModel#describe(double, double)}. Some of them
 * ({@link #FROM_SAMPLE}) are already recognised by the cheap {@link LandscapeModel#sample} and stored as bits in
 * {@link ColumnSample.Terrain#landformBits()}.
 */
public enum Landform {
	/** Dunes on an outwash plain, at least about 4 m high. */
	INLAND_DUNES,
	/** End moraine ridge, at least about 25 m above the plateau. */
	END_MORAINE,
	/** Glacial tunnel valley, including its dry sections. */
	TUNNEL_VALLEY,
	TUNNEL_VALLEY_LAKE,
	KETTLE_POND,
	KETTLE_BOG,
	RIVER,
	/** River valley floor (floodplain terrace with alluvial soils). */
	VALLEY_FLOOR,
	/** Valley side of a large river. */
	VALLEY_SLOPE,
	/** Mountain or foothill ridge. */
	RIDGE,
	/** Summit: a local height maximum in the mountains or foothills. */
	SUMMIT,
	/** Mountain pass: a dip in the ridge where a transverse valley crosses it. */
	MOUNTAIN_PASS,
	/** Mountain valley floor. */
	MOUNTAIN_VALLEY,
	/** Lower montane belt of the Beskids, below the nominal boundary from {@code habitat.AltitudinalBelts}. */
	LOWER_MONTANE,
	/** Upper montane belt of the Beskids, from the nominal boundary from {@code habitat.AltitudinalBelts}. */
	UPPER_MONTANE,
	/** Stream (lowest-order watercourse). */
	STREAM,
	/** Headwater zone of a watercourse. */
	HEADWATERS,
	/** Oxbow lake. */
	OXBOW_LAKE,
	/** River mouth at the sea. */
	RIVER_MOUTH,
	BEACH,
	CLIFF,
	COASTAL_DUNES,
	/** Coastal lagoon or coastal lake behind a spit. */
	LAGOON;

	/** Landforms recognised in {@link LandscapeModel#sample} (without extra samples), in a fixed order. */
	public static final List<Landform> FROM_SAMPLE = List.of(INLAND_DUNES, END_MORAINE, RIDGE, MOUNTAIN_VALLEY, BEACH,
			COASTAL_DUNES, CLIFF, HEADWATERS);

	/** Bit of the landform in {@link ColumnSample.Terrain#landformBits()} (there are fewer than 32 landforms). */
	public int bit() {
		return 1 << ordinal();
	}
}
