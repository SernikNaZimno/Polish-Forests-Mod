package pl.polishforests.tools.sound;

import com.github.axet.libogg.Jogg_packet;
import com.github.axet.libogg.Jogg_page;
import com.github.axet.libogg.Jogg_stream_state;
import com.github.axet.libvorbis.Jvorbis_block;
import com.github.axet.libvorbis.Jvorbis_comment;
import com.github.axet.libvorbis.Jvorbis_dsp_state;
import com.github.axet.libvorbis.Jvorbis_info;
import com.github.axet.libvorbis.Jvorbis_pcm;
import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * The OGG Vorbis step of tools/sound (step FA4, decision G.62-d): only in the Gradle tasks {@code generateSound} and
 * {@code checkSound}, with its own configuration {@code soundCodec} from Maven Central, never in the mod.
 *
 * <ul>
 * <li>{@code encode <wav dir> <ogg dir>}: every {@code <path>.wav} (16-bit mono PCM, written by tools/sound/generate.py)
 * to {@code <ogg dir>/<path>.ogg} with the pure-Java port of libvorbis 1.3.6 ({@code com.github.axet:jvorbis}, BSD-style
 * license of Xiph), variable bit rate at the quality of {@code <wav dir>/encode.properties}, one logical stream with a
 * serial number from the path (the same input gives the same file); OGG files in the target that no WAV file makes are
 * removed (the generator owns that directory).
 * <li>{@code decode <ogg dir> <out dir>}: every {@code <path>.ogg} with JOrbis ({@code com.jcraft:jorbis}, the decoder
 * Minecraft itself uses) to {@code <out dir>/<path>.f32} (float samples, little endian, the first channel) and
 * {@code <path>.txt} (channels, rate, samples, last granule position); the samples end at the granule position of the last
 * page, as a complete decoder does. tools/sound/check.py compares them with the generator with a tolerance.
 * </ul>
 */
public final class SoundCodec {
	private static final int CHUNK = 1024;

	private SoundCodec() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 3 || !(args[0].equals("encode") || args[0].equals("decode"))) {
			throw new IllegalArgumentException("Usage: SoundCodec encode <wav dir> <ogg dir> | decode <ogg dir> <out dir>");
		}
		Path from = Path.of(args[1]);
		Path to = Path.of(args[2]);
		if (args[0].equals("encode")) {
			encodeAll(from, to);
		} else {
			decodeAll(from, to);
		}
	}

	// ------------------------------------------------------------------ encode

	private static void encodeAll(Path wavDir, Path oggDir) throws IOException {
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(wavDir.resolve("encode.properties"))) {
			p.load(in);
		}
		float quality = Float.parseFloat(p.getProperty("quality"));
		List<Path> wavs = files(wavDir, ".wav");
		List<Path> made = new ArrayList<>();
		long bytes = 0;
		for (Path wav : wavs) {
			String rel = wavDir.relativize(wav).toString().replace('\\', '/');
			rel = rel.substring(0, rel.length() - 4);
			Path ogg = oggDir.resolve(rel + ".ogg");
			Files.createDirectories(ogg.getParent());
			byte[] data = encode(readWav(wav), quality, rel);
			Files.write(ogg, data);
			made.add(ogg.toAbsolutePath().normalize());
			bytes += data.length;
		}
		int removed = 0;
		for (Path old : files(oggDir, ".ogg")) {
			if (!made.contains(old.toAbsolutePath().normalize())) {
				Files.delete(old);
				removed++;
			}
		}
		System.out.printf(Locale.ROOT, "sound: encoded %d files (%.1f KiB) at quality %.2f into %s; removed %d stray files%n", made.size(),
				bytes / 1024.0, quality, oggDir, removed);
	}

	/** Mono 16-bit PCM of a WAV file and its rate. */
	record Pcm(short[] samples, int rate) {
	}

	static Pcm readWav(Path path) throws IOException {
		ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
		if (b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) {
			throw new IOException(path + " is not a RIFF/WAVE file");
		}
		int pos = 12;
		int rate = -1;
		short[] samples = null;
		while (pos + 8 <= b.limit()) {
			int id = b.getInt(pos);
			int size = b.getInt(pos + 4);
			int body = pos + 8;
			if (id == 0x20746d66) { // "fmt "
				int format = b.getShort(body) & 0xffff;
				int channels = b.getShort(body + 2);
				rate = b.getInt(body + 4);
				int bits = b.getShort(body + 14);
				if (format != 1 || channels != 1 || bits != 16) {
					throw new IOException(path + ": only 16-bit mono PCM (format " + format + ", " + channels + " channels, " + bits + " bits)");
				}
			} else if (id == 0x61746164) { // "data"
				samples = new short[size / 2];
				for (int i = 0; i < samples.length; i++) {
					samples[i] = b.getShort(body + 2 * i);
				}
			}
			pos = body + size + (size & 1);
		}
		if (rate <= 0 || samples == null) {
			throw new IOException(path + ": no fmt or data chunk");
		}
		return new Pcm(samples, rate);
	}

	static byte[] encode(Pcm pcm, float quality, String name) throws IOException {
		Jvorbis_info vi = new Jvorbis_info();
		vi.vorbis_info_init();
		if (vi.vorbis_encode_init_vbr(1, pcm.rate(), quality) != 0) {
			throw new IOException("libvorbis cannot encode mono " + pcm.rate() + " Hz at quality " + quality);
		}
		Jvorbis_comment vc = new Jvorbis_comment();
		vc.vorbis_comment_init();
		vc.vorbis_comment_add_tag("TITLE", name);
		vc.vorbis_comment_add_tag("ARTIST", "Polish Forests (tools/sound)");
		vc.vorbis_comment_add_tag("LICENSE", "CC BY 4.0");
		Jvorbis_dsp_state vd = new Jvorbis_dsp_state();
		vd.vorbis_analysis_init(vi);
		Jvorbis_block vb = new Jvorbis_block();
		vd.vorbis_block_init(vb);
		Jogg_stream_state os = new Jogg_stream_state();
		os.ogg_stream_init(name.hashCode() & 0x7fffffff);
		Jogg_page og = new Jogg_page();
		Jogg_packet op = new Jogg_packet();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Jogg_packet header = new Jogg_packet();
		Jogg_packet comment = new Jogg_packet();
		Jogg_packet codebooks = new Jogg_packet();
		vd.vorbis_analysis_headerout(vc, header, comment, codebooks);
		os.ogg_stream_packetin(header);
		os.ogg_stream_packetin(comment);
		os.ogg_stream_packetin(codebooks);
		while (os.ogg_stream_flush(og) != 0) {
			write(out, og);
		}
		short[] s = pcm.samples();
		boolean[] eos = {false};
		for (int at = 0; at < s.length; at += CHUNK) {
			int n = Math.min(CHUNK, s.length - at);
			Jvorbis_pcm buf = vd.vorbis_analysis_buffer(n);
			float[] ch = buf.pcm[0];
			for (int i = 0; i < n; i++) {
				ch[buf.pcmret + i] = s[at + i] / 32768.0F;
			}
			vd.vorbis_analysis_wrote(n);
			drain(vd, vb, os, og, op, out, eos);
		}
		vd.vorbis_analysis_wrote(0);
		drain(vd, vb, os, og, op, out, eos);
		while (os.ogg_stream_flush(og) != 0) {
			write(out, og);
		}
		return out.toByteArray();
	}

	private static void drain(Jvorbis_dsp_state vd, Jvorbis_block vb, Jogg_stream_state os, Jogg_page og, Jogg_packet op, OutputStream out,
			boolean[] eos) throws IOException {
		while (vd.vorbis_analysis_blockout(vb)) {
			vb.vorbis_analysis(null);
			vb.vorbis_bitrate_addblock();
			while (vd.vorbis_bitrate_flushpacket(op)) {
				os.ogg_stream_packetin(op);
				while (!eos[0] && os.ogg_stream_pageout(og) != 0) {
					write(out, og);
					eos[0] = og.ogg_page_eos();
				}
			}
		}
	}

	private static void write(OutputStream out, Jogg_page og) throws IOException {
		out.write(og.header_base, og.header, og.header_len);
		out.write(og.body_base, og.body, og.body_len);
	}

	// ------------------------------------------------------------------ decode

	private static void decodeAll(Path oggDir, Path outDir) throws IOException {
		int n = 0;
		for (Path ogg : files(oggDir, ".ogg")) {
			String rel = oggDir.relativize(ogg).toString().replace('\\', '/');
			rel = rel.substring(0, rel.length() - 4);
			Decoded d;
			try (InputStream in = Files.newInputStream(ogg)) {
				d = decode(in, rel);
			}
			Path f32 = outDir.resolve(rel + ".f32");
			Files.createDirectories(f32.getParent());
			ByteBuffer b = ByteBuffer.allocate(d.samples.length * 4).order(ByteOrder.LITTLE_ENDIAN);
			for (float v : d.samples) {
				b.putFloat(v);
			}
			Files.write(f32, b.array());
			Files.writeString(outDir.resolve(rel + ".txt"), String.format(Locale.ROOT, "channels %d%nrate %d%nsamples %d%ngranule %d%ndecoded %d%n",
					d.channels, d.rate, d.samples.length, d.granule, d.decoded), StandardCharsets.UTF_8);
			n++;
		}
		System.out.printf(Locale.ROOT, "sound: decoded %d files from %s into %s%n", n, oggDir, outDir);
	}

	/** The first channel of a decoded stream, cut at the last granule position, and what the stream says of itself. */
	record Decoded(float[] samples, int channels, int rate, long granule, int decoded) {
	}

	static Decoded decode(InputStream in, String name) throws IOException {
		SyncState oy = new SyncState();
		StreamState os = new StreamState();
		Page og = new Page();
		Packet op = new Packet();
		Info vi = new Info();
		Comment vc = new Comment();
		DspState vd = new DspState();
		Block vb = new Block(vd);
		oy.init();
		read(oy, in);
		if (oy.pageout(og) != 1) {
			throw new IOException(name + ": not an Ogg stream");
		}
		os.init(og.serialno());
		vi.init();
		vc.init();
		if (os.pagein(og) < 0 || os.packetout(op) != 1 || vi.synthesis_headerin(vc, op) < 0) {
			throw new IOException(name + ": not a Vorbis stream");
		}
		int headers = 1;
		while (headers < 3) {
			int result = oy.pageout(og);
			if (result == 0) {
				if (read(oy, in) == 0) {
					throw new IOException(name + ": the stream ends inside the headers");
				}
				continue;
			}
			if (result == 1) {
				os.pagein(og);
				while (headers < 3) {
					result = os.packetout(op);
					if (result == 0) {
						break;
					}
					if (result < 0 || vi.synthesis_headerin(vc, op) < 0) {
						throw new IOException(name + ": broken Vorbis header");
					}
					headers++;
				}
			}
		}
		vd.synthesis_init(vi);
		vb.init(vd);
		float[][][] pcmHolder = new float[1][][];
		int[] index = new int[vi.channels];
		FloatList out = new FloatList();
		long granule = -1;
		boolean eos = false;
		while (!eos) {
			int result = oy.pageout(og);
			if (result == 0) {
				if (read(oy, in) == 0) {
					break;
				}
				continue;
			}
			if (result < 0) {
				continue;
			}
			os.pagein(og);
			if (og.granulepos() >= 0) {
				granule = og.granulepos();
			}
			while (true) {
				result = os.packetout(op);
				if (result == 0) {
					break;
				}
				if (result < 0) {
					continue;
				}
				if (vb.synthesis(op) == 0) {
					vd.synthesis_blockin(vb);
				}
				int samples;
				while ((samples = vd.synthesis_pcmout(pcmHolder, index)) > 0) {
					float[] ch = pcmHolder[0][0];
					for (int j = 0; j < samples; j++) {
						out.add(ch[index[0] + j]);
					}
					vd.synthesis_read(samples);
				}
			}
			if (og.eos() != 0) {
				eos = true;
			}
		}
		int decoded = out.size;
		int keep = granule >= 0 ? (int) Math.min(decoded, granule) : decoded;
		float[] samples = java.util.Arrays.copyOf(out.data, keep);
		return new Decoded(samples, vi.channels, vi.rate, granule, decoded);
	}

	private static int read(SyncState oy, InputStream in) throws IOException {
		int index = oy.buffer(4096);
		int bytes = in.read(oy.data, index, 4096);
		bytes = Math.max(0, bytes);
		oy.wrote(bytes);
		return bytes;
	}

	private static final class FloatList {
		float[] data = new float[1 << 16];
		int size;

		void add(float v) {
			if (size == data.length) {
				data = java.util.Arrays.copyOf(data, size * 2);
			}
			data[size++] = v;
		}
	}

	private static List<Path> files(Path dir, String suffix) throws IOException {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> s = Files.walk(dir)) {
			return s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(suffix)).sorted().toList();
		}
	}
}
