package com.igormaznitsa.annotator.api.png;

import com.igormaznitsa.jbbp.mapper.Bin;

/**
 * Single PNG chunk row for JBBP read/write.
 */
public final class PngJbbpChunk {

  @Bin(order = 1)
  public int length;

  @Bin(order = 2)
  public int type;

  @Bin(order = 3)
  public byte[] data;

  @Bin(order = 4)
  public int crc;
}
