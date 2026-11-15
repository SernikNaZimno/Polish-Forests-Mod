package pl.polishforests.climate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;

/**
 * Step C2 (decision CP-D.11, G.58-b): the base temperature of the vanilla biome each column of a chunk of the world type
 * "Default + Polish Forests" had before the forests of the mod replaced it, read at the end of the TERRAIN stage with the
 * vanilla zoom of the noise biomes at the column's top block (as {@code level.getBiome} does). The habitat biomes of that
 * world compute their temperature from it with the vanilla height adjustment ({@link VanillaClimate}), so snow lies where
 * vanilla had it.
 *
 * <p>Compact form: a palette of the distinct base temperatures of the chunk (most chunks hold one or two vanilla biomes)
 * and, only when the palette has more than one entry, one index byte per column ({@code i = x << 4 | z}, local
 * coordinates, as in {@code ChunkHabitats}). The attachment is persistent ({@link #CODEC}) and synchronized with the
 * client ({@link #STREAM_CODEC}): the client draws rain and snow, and it does not know the vanilla biome source.
 */
public final class ColumnClimate {
	/** Columns of a chunk. */
	public static final int COLUMNS = 256;
	private static final byte[] NO_COLUMNS = new byte[0];

	private final float[] palette;
	private final byte[] columns;

	private ColumnClimate(float[] palette, byte[] columns) {
		this.palette = palette;
		this.columns = columns;
	}

	/** The climate of a chunk from the base temperature of each of its 256 columns ({@code i = x << 4 | z}). */
	public static ColumnClimate of(float[] bases) {
		if (bases.length != COLUMNS) {
			throw new IllegalArgumentException("Expected 256 columns, got " + bases.length);
		}
		float[] palette = new float[COLUMNS];
		int n = 0;
		byte[] columns = new byte[COLUMNS];
		for (int i = 0; i < COLUMNS; i++) {
			int k = indexOf(palette, n, bases[i]);
			if (k < 0) {
				k = n;
				palette[n++] = bases[i];
			}
			columns[i] = (byte) k;
		}
		return new ColumnClimate(Arrays.copyOf(palette, n), n == 1 ? NO_COLUMNS : columns);
	}

	/** A chunk with one base temperature in every column. */
	public static ColumnClimate uniform(float base) {
		return new ColumnClimate(new float[] {base}, NO_COLUMNS);
	}

	private static int indexOf(float[] palette, int n, float value) {
		int bits = Float.floatToIntBits(value);
		for (int k = 0; k < n; k++) {
			if (Float.floatToIntBits(palette[k]) == bits) {
				return k;
			}
		}
		return -1;
	}

	/** Base temperature of column i ({@code i = x << 4 | z}). */
	public float base(int i) {
		return columns.length == 0 ? palette[0] : palette[columns[i] & 0xFF];
	}

	/** Base temperature of the column at local (or world) block coordinates x, z. */
	public float base(int x, int z) {
		return base((x & 15) << 4 | z & 15);
	}

	/** Number of distinct base temperatures in the chunk. */
	public int paletteSize() {
		return palette.length;
	}

	/** The coldest base temperature of the chunk. */
	public float min() {
		float m = Float.POSITIVE_INFINITY;
		for (float t : palette) {
			m = Math.min(m, t);
		}
		return m;
	}

	// ------------------------------------------------------------------ codecs

	private static DataResult<ColumnClimate> read(List<Float> palette, ByteBuffer columns) {
		if (palette.isEmpty() || palette.size() > COLUMNS) {
			return DataResult.error(() -> "Column climate: palette of " + palette.size() + " entries");
		}
		byte[] c = new byte[columns.remaining()];
		columns.duplicate().get(c);
		if (palette.size() == 1 ? c.length != 0 : c.length != COLUMNS) {
			return DataResult.error(() -> "Column climate: " + c.length + " column indices for a palette of " + palette.size());
		}
		float[] p = new float[palette.size()];
		for (int k = 0; k < p.length; k++) {
			p[k] = palette.get(k);
		}
		for (byte b : c) {
			if ((b & 0xFF) >= p.length) {
				return DataResult.error(() -> "Column climate: index " + (b & 0xFF) + " outside a palette of " + p.length);
			}
		}
		return DataResult.success(new ColumnClimate(p, c));
	}

	private List<Float> paletteList() {
		List<Float> l = new ArrayList<>(palette.length);
		for (float t : palette) {
			l.add(t);
		}
		return l;
	}

	/** The stored form: the palette and the column indices (empty for a one-entry palette). */
	private record Stored(List<Float> palette, ByteBuffer columns) {
		static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.FLOAT.listOf().fieldOf("palette").forGetter(Stored::palette),
				Codec.BYTE_BUFFER.optionalFieldOf("columns", ByteBuffer.wrap(NO_COLUMNS)).forGetter(Stored::columns)
		).apply(i, Stored::new));
	}

	/** Codec of the persistent attachment: the palette and, for more than one entry, the 256 column indices. */
	public static final Codec<ColumnClimate> CODEC = Stored.CODEC.comapFlatMap(s -> read(s.palette(), s.columns()),
			c -> new Stored(c.paletteList(), ByteBuffer.wrap(c.columns)));

	/** Stream codec of the synchronization with the client (the same content as {@link #CODEC}). */
	public static final StreamCodec<ByteBuf, ColumnClimate> STREAM_CODEC = StreamCodec.of(
			(buf, c) -> {
				VarInt.write(buf, c.palette.length);
				for (float t : c.palette) {
					buf.writeFloat(t);
				}
				buf.writeBytes(c.columns);
			},
			buf -> {
				int n = VarInt.read(buf);
				if (n < 1 || n > COLUMNS) {
					throw new DecoderException("Column climate: palette of " + n + " entries");
				}
				float[] p = new float[n];
				for (int k = 0; k < n; k++) {
					p[k] = buf.readFloat();
				}
				byte[] c = n == 1 ? NO_COLUMNS : new byte[COLUMNS];
				buf.readBytes(c);
				for (int i = 0; i < c.length; i++) {
					if ((c[i] & 0xFF) >= n) {
						throw new DecoderException("Column climate: index " + (c[i] & 0xFF) + " outside a palette of " + n);
					}
				}
				return new ColumnClimate(p, c);
			});

	@Override
	public boolean equals(Object o) {
		return o instanceof ColumnClimate c && Arrays.equals(palette, c.palette) && Arrays.equals(columns, c.columns);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(palette) + Arrays.hashCode(columns);
	}

	@Override
	public String toString() {
		return "ColumnClimate" + Arrays.toString(palette) + (columns.length == 0 ? "" : " by column");
	}
}
