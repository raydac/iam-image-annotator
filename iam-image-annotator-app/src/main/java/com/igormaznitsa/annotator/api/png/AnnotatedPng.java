package com.igormaznitsa.annotator.api.png;

import com.igormaznitsa.annotator.api.json.AnnotationJsonCodec;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.render.AnnotationOverlayRenderer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Annotated PNG: display raster in IDAT, original base PNG in iBSE, JSON in iANN.
 */
public final class AnnotatedPng {

  private final BufferedImage baseImage;
  private final AnnotationDocument document;

  public AnnotatedPng(final BufferedImage baseImage, final AnnotationDocument document) {
    this.baseImage = Objects.requireNonNull(baseImage, "baseImage");
    this.document = Objects.requireNonNull(document, "document");
  }

  public static boolean hasAnnotationChunks(final Path path) throws IOException {
    try (final InputStream input = Files.newInputStream(path)) {
      return hasAnnotationChunks(input);
    }
  }

  public static AnnotationDocument readDocument(final Path path) throws IOException {
    try (final InputStream input = Files.newInputStream(path)) {
      return readDocument(input);
    }
  }

  public static AnnotationDocument readDocument(final InputStream input) throws IOException {
    final DataInputStream data = new DataInputStream(input);
    if (hasInvalidPngSignature(data.readNBytes(PngConstants.SIGNATURE.length))) {
      throw new IOException("Not a PNG file");
    }
    while (true) {
      final int length = data.readInt();
      if (length < 0) {
        throw new IOException("Invalid PNG chunk length: " + length);
      }
      final String type = PngChunkType.decode(data.readInt());
      if (PngConstants.isIannChunk(type)) {
        return new AnnotationJsonCodec().decode(readChunkData(data, length));
      }
      data.skipNBytes((long) length + Integer.BYTES);
      if ("IEND".equals(type)) {
        return new AnnotationDocument();
      }
    }
  }

  public static boolean hasAnnotationChunks(final InputStream input) throws IOException {
    final DataInputStream data = new DataInputStream(input);
    if (hasInvalidPngSignature(data.readNBytes(PngConstants.SIGNATURE.length))) {
      return false;
    }
    while (true) {
      final int length = data.readInt();
      if (length < 0) {
        throw new IOException("Invalid PNG chunk length: " + length);
      }
      final String type = PngChunkType.decode(data.readInt());
      if (PngConstants.isIannChunk(type)) {
        return true;
      }
      data.skipNBytes((long) length + Integer.BYTES);
      if ("IEND".equals(type)) {
        return false;
      }
    }
  }

  public static AnnotatedPng read(final InputStream input) throws IOException {
    final PngChunkIO chunkIo = new PngChunkIO();
    final List<PngChunk> chunks = readAnnotationChunks(input);

    final AnnotationDocument document = resolveDocument(chunks);
    final BufferedImage baseImage = resolveBaseImage(chunkIo, chunks);

    return new AnnotatedPng(baseImage, document);
  }

  private static List<PngChunk> readAnnotationChunks(final InputStream input) throws IOException {
    final DataInputStream data = new DataInputStream(input);
    if (hasInvalidPngSignature(data.readNBytes(PngConstants.SIGNATURE.length))) {
      throw new IOException("Not a PNG file");
    }

    final List<PngChunk> chunks = new java.util.ArrayList<>();
    boolean baseImageFound = false;
    while (true) {
      final int length = data.readInt();
      if (length < 0) {
        throw new IOException("Invalid PNG chunk length: " + length);
      }
      final String type = PngChunkType.decode(data.readInt());
      if (PngConstants.isCustomChunk(type) || !baseImageFound) {
        chunks.add(new PngChunk(type, readChunkData(data, length)));
        baseImageFound = baseImageFound || PngConstants.isIbseChunk(type);
      } else {
        data.skipNBytes(length);
      }
      data.skipNBytes(Integer.BYTES);
      if ("IEND".equals(type)) {
        return List.copyOf(chunks);
      }
    }
  }

  private static byte[] readChunkData(final DataInputStream data, final int length)
      throws IOException {
    final byte[] result = data.readNBytes(length);
    if (result.length != length) {
      throw new IOException("Unexpected end of PNG chunk data");
    }
    return result;
  }

  private static AnnotationDocument resolveDocument(final List<PngChunk> chunks) {
    return chunks.stream()
        .filter(chunk -> PngConstants.isIannChunk(chunk.type()))
        .map(PngChunk::data)
        .findFirst()
        .map(bytes -> new AnnotationJsonCodec().decode(bytes))
        .orElseGet(AnnotationDocument::new);
  }

  private static BufferedImage resolveBaseImage(final PngChunkIO chunkIo,
                                                final List<PngChunk> chunks)
      throws IOException {
    final Optional<byte[]> ibse = chunks.stream()
        .filter(chunk -> PngConstants.isIbseChunk(chunk.type()))
        .map(PngChunk::data)
        .findFirst();
    if (ibse.isPresent()) {
      return decodeBaseImage(ibse.get());
    }
    return decodeDisplayRaster(chunkIo, chunks);
  }

  private static BufferedImage decodeDisplayRaster(final PngChunkIO chunkIo,
                                                   final List<PngChunk> chunks)
      throws IOException {
    final BufferedImage displayImage =
        ImageIO.read(chunkIo.toInputStream(chunkIo.toDisplayPng(chunks)));
    if (displayImage == null) {
      throw new IOException("Unable to decode PNG image");
    }
    return displayImage;
  }

  private static BufferedImage decodeBaseImage(final byte[] ibsePayload) throws IOException {
    if (hasInvalidPngSignature(ibsePayload)) {
      throw new IOException("iBSE chunk is not a PNG file");
    }
    try (ImageInputStream stream = ImageIO.createImageInputStream(
        new ByteArrayInputStream(ibsePayload))) {
      final Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) {
        throw new IOException("No image reader available for iBSE chunk");
      }
      final ImageReader reader = readers.next();
      try {
        reader.setInput(stream, true, true);
        final BufferedImage baseImage = reader.read(0);
        if (baseImage == null) {
          throw new IOException("Unable to decode iBSE chunk");
        }
        return baseImage;
      } finally {
        reader.dispose();
      }
    }
  }

  private static boolean hasInvalidPngSignature(final byte[] data) {
    return data.length < PngConstants.SIGNATURE.length
        || !Arrays.equals(data, 0, PngConstants.SIGNATURE.length, PngConstants.SIGNATURE, 0,
        PngConstants.SIGNATURE.length);
  }

  private static byte[] encodePng(final BufferedImage image) throws IOException {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
    if (!writers.hasNext()) {
      throw new IOException("PNG encoder unavailable");
    }
    final ImageWriter writer = writers.next();
    try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(output);
      writer.write(null, new IIOImage(image, null, null), maxCompression(writer));
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }

  private static ImageWriteParam maxCompression(final ImageWriter writer) {
    final ImageWriteParam param = writer.getDefaultWriteParam();
    if (param.canWriteCompressed()) {
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.0f);
    }
    return param;
  }

  public BufferedImage baseImage() {
    return this.baseImage;
  }

  public AnnotationDocument document() {
    return this.document;
  }

  public void write(final OutputStream output) throws IOException {
    final AnnotationOverlayRenderer renderer = new AnnotationOverlayRenderer();
    final PngDisplayRasterOptimizer optimizer = new PngDisplayRasterOptimizer();
    final BufferedImage displayBase =
        optimizer.optimizeBaseForFilledAnnotations(this.baseImage, this.document);
    final BufferedImage composed =
        optimizer.optimizeEncodedDisplayRaster(renderer.compose(displayBase, this.document));
    final PngChunkIO chunkIo = new PngChunkIO();

    final byte[] ibse = encodePng(this.baseImage);
    final byte[] iann = new AnnotationJsonCodec().encode(this.document);

    final byte[] composedPng = encodePng(composed);
    final List<PngChunk> composedChunks = chunkIo.readAll(new ByteArrayInputStream(composedPng));
    final List<PngChunk> outputChunks = chunkIo.replaceCustomChunks(composedChunks, iann, ibse);
    output.write(chunkIo.writeAll(outputChunks));
  }
}
