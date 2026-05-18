package com.igormaznitsa.annotator.api.png;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Annotated PNG chunk operations; binary envelope delegated to {@link PngJbbpCodec}.
 */
public final class PngChunkIO {

  public List<PngChunk> readAll(final InputStream input) throws IOException {
    return PngJbbpCodec.readChunks(input);
  }

  public byte[] writeAll(final List<PngChunk> chunks) throws IOException {
    return PngJbbpCodec.writeChunks(chunks);
  }

  public List<PngChunk> replaceCustomChunks(
      final List<PngChunk> source,
      final byte[] iannData,
      final byte[] ibseData) {
    final List<PngChunk> result = new ArrayList<>();
    for (final PngChunk chunk : source) {
      if (PngConstants.isCustomChunk(chunk.type())) {
        continue;
      }
      result.add(chunk);
      if ("IHDR".equals(chunk.type())) {
        result.add(new PngChunk(PngConstants.CHUNK_IBSE, ibseData));
        result.add(new PngChunk(PngConstants.CHUNK_IANN, iannData));
      }
    }
    return result;
  }

  public byte[] toDisplayPng(final List<PngChunk> chunks) throws IOException {
    final List<PngChunk> display = new ArrayList<>();
    for (final PngChunk chunk : chunks) {
      if (PngConstants.isCustomChunk(chunk.type())) {
        continue;
      }
      display.add(chunk);
    }
    return this.writeAll(display);
  }

  public byte[] readAllBytes(final InputStream input) throws IOException {
    return input.readAllBytes();
  }

  public InputStream toInputStream(final byte[] pngBytes) {
    return new ByteArrayInputStream(pngBytes);
  }
}
