package com.igormaznitsa.annotator.api.png;

public final class PngConstants {

  public static final byte[] SIGNATURE = {
      (byte) 137, 80, 78, 71, 13, 10, 26, 10
  };

  /**
   * Ancillary chunk: UTF-8 JSON annotations.
   */
  public static final String CHUNK_IANN = "iANN";

  /**
   * Ancillary chunk: original base image as a complete PNG byte sequence.
   */
  public static final String CHUNK_IBSE = "iBSE";

  private PngConstants() {
  }

  public static boolean isIannChunk(final String type) {
    return CHUNK_IANN.equals(type);
  }

  public static boolean isIbseChunk(final String type) {
    return CHUNK_IBSE.equals(type);
  }

  public static boolean isCustomChunk(final String type) {
    return isIannChunk(type) || isIbseChunk(type);
  }
}
