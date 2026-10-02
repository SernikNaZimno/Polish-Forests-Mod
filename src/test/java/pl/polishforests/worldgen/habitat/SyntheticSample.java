package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.LandscapeType;
import pl.polishforests.worldgen.landscape.Substrate;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Budowniczy syntetycznych próbek kolumny do testów klasyfikatora: domyślnie wnętrze wysoczyzny morenowej
 * na 120 m, bez wód, 100 km od morza, O 0,5, P 0,1. Pola jak w {@link ColumnSample}.
 */
final class SyntheticSample {
	double surface = 120;
	int waterLevel = ColumnSample.NO_WATER;
	WaterKind waterKind = WaterKind.NONE;
	LandscapeType type = LandscapeType.MORAINE_PLATEAU;
	Substrate substrate = Substrate.GLACIAL_TILL;
	double rawSurface = Double.NaN;
	double coastD = 100_000;
	double wOutwashPlain;
	double wMorainePlateau = 1;
	double wOldGlacialPlain;
	double wFoothills;
	double wBeskids;
	double wCoastland;
	int landformBits;
	double convexity;
	double duneHeight;
	double ridgeProfile = Double.NaN;
	double massif;
	double summit;
	double cliffHeight;
	/** Niski brzeg morski (0 = wysoki brzeg z klifem albo poza pasem) i granica gołego piasku. */
	double lowShore;
	double bareSandWidth = Double.NaN;
	double sandiness = 0.5;
	double sBar = Double.NaN;
	double slope = 1;
	double aspect = Double.NaN;
	int streamOrder;
	boolean headwaters;
	double channelDist = Double.POSITIVE_INFINITY;
	double channelWidth = Double.NaN;
	double channelLevel = Double.NaN;
	boolean inFloor;
	double u = Double.NaN;
	double floorHalfWidth = Double.NaN;
	double channelGradient = Double.NaN;
	boolean convexBank;
	double s = Double.POSITIVE_INFINITY;
	int shoreLevel = ColumnSample.NO_WATER;
	ColumnSample.StandingWaterKind kind = ColumnSample.StandingWaterKind.NONE;
	boolean ombrotrophicPeat;
	long lakeId;
	double radius = Double.NaN;
	double o = 0.5;
	double p = 0.1;

	static SyntheticSample morainePlateau() {
		return new SyntheticSample();
	}

	static SyntheticSample outwashPlain() {
		SyntheticSample q = new SyntheticSample();
		q.type = LandscapeType.OUTWASH_PLAIN;
		q.substrate = Substrate.SAND;
		q.wMorainePlateau = 0;
		q.wOutwashPlain = 1;
		q.surface = 140;
		return q;
	}

	static SyntheticSample beskids(double h) {
		SyntheticSample q = new SyntheticSample();
		q.type = LandscapeType.BESKIDS;
		q.substrate = Substrate.FLYSCH;
		q.wMorainePlateau = 0;
		q.wBeskids = 1;
		q.surface = h;
		q.p = 0.9;
		q.ridgeProfile = 0.5;
		return q;
	}

	static SyntheticSample coast(double cD, double h, Substrate sub) {
		SyntheticSample q = new SyntheticSample();
		q.type = LandscapeType.COASTLAND;
		q.substrate = sub;
		q.wMorainePlateau = 0;
		q.wOutwashPlain = 1;
		q.wCoastland = 1;
		q.coastD = cD;
		q.surface = h;
		q.o = 0.7;
		return q;
	}

	SyntheticSample h(double v) {
		surface = v;
		return this;
	}

	SyntheticSample landform(Landform f) {
		landformBits |= f.bit();
		return this;
	}

	/** Woda w kolumnie: lustro i dno. */
	SyntheticSample water(WaterKind k, int level, double bottom) {
		waterKind = k;
		waterLevel = level;
		surface = bottom;
		return this;
	}

	/** Ciek w pobliżu: rząd, szerokość koryta, odległość od brzegu, lustro (domyślnie dno − 1,7 m). */
	SyntheticSample stream(int r, double width, double d, double gradientPermille) {
		streamOrder = r;
		channelWidth = width;
		channelDist = d;
		channelGradient = gradientPermille;
		if (Double.isNaN(channelLevel)) {
			channelLevel = surface - 1.7;
		}
		return this;
	}

	SyntheticSample onValleyFloor(double uFloor, double halfWidth) {
		inFloor = true;
		u = uFloor;
		floorHalfWidth = halfWidth;
		return this;
	}

	SyntheticSample standingWater(ColumnSample.StandingWaterKind r, double shoreDist, int level, double rad, long id) {
		kind = r;
		s = shoreDist;
		shoreLevel = level;
		radius = rad;
		lakeId = id;
		return this;
	}

	ColumnSample build() {
		double raw = Double.isNaN(rawSurface) ? surface : rawSurface;
		double sb = Double.isNaN(sBar) ? surface : sBar;
		ColumnSample.Terrain t = new ColumnSample.Terrain(raw, coastD, wOutwashPlain, wMorainePlateau, wOldGlacialPlain, wFoothills, wBeskids,
				wCoastland, landformBits, convexity, duneHeight, ridgeProfile, massif, summit, cliffHeight, lowShore, bareSandWidth, sandiness, sb, slope, aspect);
		ColumnSample.Waters w = new ColumnSample.Waters(streamOrder, headwaters, channelDist, channelWidth, channelLevel, inFloor, u,
				floorHalfWidth, channelGradient, convexBank, s, shoreLevel, kind, ombrotrophicPeat, lakeId, radius);
		return new ColumnSample(surface, waterLevel, waterKind, type, substrate, 5, t, w,
				new ColumnSample.Region(o, p));
	}
}
