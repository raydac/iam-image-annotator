package com.igormaznitsa.annotator.api.png;

import static com.igormaznitsa.jbbp.io.JBBPOut.BeginBin;

import com.igormaznitsa.jbbp.JBBPParser;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PNG signature and chunk envelope through
 * <a href="https://github.com/raydac/java-binary-block-parser">JBBP</a>.
 */
public final class PngJbbpCodec {

  private static final String PNG_SCRIPT = ""
      + "long header;"
      + "chunk [_]{"
      + "   int length; "
      + "   int type; "
      + "   byte[length] data; "
      + "   int crc;"
      + "}";

  private static final JBBPParser PARSER = JBBPParser.prepare(PNG_SCRIPT);

  private PngJbbpCodec() {
  }

  public static List<PngChunk> readChunks(final InputStream input) throws IOException {
    final PngJbbpFile file = new PngJbbpFile();
    final PngJbbpFile parsed = PARSER.parse(input).mapTo(file, PngJbbpCodec::newChunkInstance);
    if (parsed.header != PngChunkType.SIGNATURE_LONG) {
      throw new IOException("Not a PNG file");
    }
    if (parsed.chunks == null) {
      return List.of();
    }
    final List<PngChunk> result = new ArrayList<>(parsed.chunks.length);
    for (final PngJbbpChunk row : parsed.chunks) {
      if (row.length < 0) {
        throw new IOException("Invalid PNG chunk length: " + row.length);
      }
      final String type = PngChunkType.decode(row.type);
      final byte[] data = row.data == null ? new byte[0] : row.data;
      if (data.length != row.length) {
        throw new IOException("PNG chunk data length mismatch for " + type);
      }
      result.add(new PngChunk(type, data));
      if ("IEND".equals(type)) {
        break;
      }
    }
    return result;
  }

  public static byte[] writeChunks(final List<PngChunk> chunks) throws IOException {
    final PngJbbpFile file = new PngJbbpFile();
    file.header = PngChunkType.SIGNATURE_LONG;
    file.chunks = new PngJbbpChunk[chunks.size()];
    for (int i = 0; i < chunks.size(); i++) {
      final PngChunk chunk = chunks.get(i);
      final PngJbbpChunk row = new PngJbbpChunk();
      row.data = chunk.data();
      row.length = row.data.length;
      row.type = PngChunkType.encode(chunk.type());
      row.crc = PngChunkType.computeCrc(chunk.type(), row.data);
      file.chunks[i] = row;
    }
    return BeginBin().Bin(file).End().toByteArray();
  }

  private static Object newChunkInstance(final Class<?> type) {
    return type == PngJbbpChunk.class ? new PngJbbpChunk() : null;
  }
}
