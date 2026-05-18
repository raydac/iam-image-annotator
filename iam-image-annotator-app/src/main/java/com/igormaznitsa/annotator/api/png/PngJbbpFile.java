package com.igormaznitsa.annotator.api.png;

import com.igormaznitsa.jbbp.mapper.Bin;

/**
 * JBBP-mapped PNG file (signature + chunk stream).
 */
public final class PngJbbpFile {

  @Bin(order = 1)
  public long header;

  @Bin(order = 2, name = "chunk")
  public PngJbbpChunk[] chunks;

  public PngJbbpChunk newChunk() {
    return new PngJbbpChunk();
  }
}
