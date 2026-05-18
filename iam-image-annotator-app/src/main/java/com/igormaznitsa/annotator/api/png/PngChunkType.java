package com.igormaznitsa.annotator.api.png;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * PNG four-byte chunk type as JBBP {@code int} and ISO-8859-1 string.
 */
public final class PngChunkType {

  public static final long SIGNATURE_LONG = 0x89504E470D0A1A0AL;

  private PngChunkType() {
  }

  public static int encode(final String type) {
    if (type.length() != 4) {
      throw new IllegalArgumentException("PNG chunk type must be exactly 4 characters: " + type);
    }
    return (type.charAt(0) << 24)
        | (type.charAt(1) << 16)
        | (type.charAt(2) << 8)
        | type.charAt(3);
  }

  public static String decode(final int type) {
    return new String(
        new byte[] {
            (byte) (type >> 24),
            (byte) (type >> 16),
            (byte) (type >> 8),
            (byte) type
        },
        StandardCharsets.ISO_8859_1);
  }

  public static int computeCrc(final String type, final byte[] data) {
    final byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
    final CRC32 crc = new CRC32();
    crc.update(typeBytes);
    if (data.length > 0) {
      crc.update(data);
    }
    return (int) crc.getValue();
  }
}
