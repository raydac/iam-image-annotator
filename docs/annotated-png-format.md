# Annotated PNG format

IAM Image Annotator stores labels inside ordinary `.png` files using two private ancillary chunks. The file remains a
valid PNG for standard viewers (they show the composed preview); the editor recovers the original base image and
structured annotation data from the chunks.

## Format at a glance

| Topic                 | Behaviour                                                                            |
|-----------------------|--------------------------------------------------------------------------------------|
| **File name**         | Standard `*.png` only (annotations embedded in place)                                |
| **Detection**         | Presence of an `iANN` chunk (content-based)                                          |
| **`iBSE` payload**    | Complete base PNG file (signature + all chunks)                                      |
| **Editor background** | Decoded **only** from `iBSE` when that chunk is present; never from top-level `IDAT` |
| **Chunk names**       | `iANN` and `iBSE` only                                                               |
| **Save**              | Always to the same path that was opened                                              |

The **`iBSE` design** keeps the base raster self-contained. The composed preview may use a different PNG layout (for
example ARGB overlay vs RGB base); the base image must not be rebuilt from display `IHDR` plus raw `IDAT` bytes. If
`iBSE` is present but cannot be decoded, the file **must not** open in the editor using the display raster as a
substitute — that would bake annotation fills into the work background twice.

---

## On-disk layout

A saved annotated file is a normal PNG with extra chunks inserted immediately after `IHDR`:

```
PNG signature
IHDR
iBSE    ← base (original) image
iANN    ← UTF-8 JSON annotations
…       ← standard chunks from composed image (PLTE, IDAT, …)
IEND
```

### Display raster (`IDAT` and standard chunks)

- Built by compositing the **base image** with vector annotations (fills, strokes, handles not included).
- This is what `ImageIO` and other tools decode when opening the file as a plain PNG.
- Color type may differ from the base (commonly **ARGB** for the composed preview).

### `iANN` — annotation document

| Property   | Value                                                      |
|------------|------------------------------------------------------------|
| Chunk type | `iANN`                                                     |
| Content    | UTF-8 JSON                                                 |
| Role       | Machine-readable list of shapes (geometry + label + style) |

**Detection:** a file is treated as annotated if an `iANN` chunk exists (`AnnotatedPng.hasAnnotationChunks`).

The JSON is a single root object with an `annotations` array. Each element is one shape. Order in the array is **draw
order** (earlier entries are painted below later ones).

---

## Annotation JSON schema

### Coordinate system

All geometry is stored in **normalized image coordinates**:

| Axis | Range         | Meaning                                                |
|------|---------------|--------------------------------------------------------|
| `x`  | `0.0` … `1.0` | Horizontal position; `0` = left edge, `1` = right edge |
| `y`  | `0.0` … `1.0` | Vertical position; `0` = top edge, `1` = bottom edge   |

Pixel position (for an image of width `W` and height `H`):

```
pixelX = x * W
pixelY = y * H
```

Widths and heights are also normalized (`width`, `height` ∈ `0.0` … `1.0` relative to image size).

The editor clamps values into valid range on save. OBB corners are reordered to a consistent **clockwise** order around
the centre on load/save (`ObbCorners.normalize`).

### Root object

```json
{
  "annotations": [ /* AnnotationEntry[] */ ]
}
```

| Field         | Type  | Required | Description                           |
|---------------|-------|----------|---------------------------------------|
| `annotations` | array | yes      | All shapes on the image; may be empty |

### Annotation entry (common fields)

Every item in `annotations` has the same top-level shape:

| Field       | Type    | Required | Description                                                                                  |
|-------------|---------|----------|----------------------------------------------------------------------------------------------|
| `id`        | string  | yes      | Label / class name for the shape (unique per file in the editor). Pattern: `[A-Za-z0-9_.-]+` |
| `type`      | string  | yes      | Geometry kind; see table below (stored **lowercase**)                                        |
| `fillColor` | string  | yes      | Fill colour `#RRGGBB` (six hex digits, `#` prefix; saved upper case)                         |
| `lock`      | boolean | no       | If `true`, shape is locked in the editor. **Omitted** when `false`                           |
| `coords`    | object  | yes      | Type-specific geometry; only relevant fields are set                                         |

| `type` value  | Enum        | Description                                   |
|---------------|-------------|-----------------------------------------------|
| `"rectangle"` | `RECTANGLE` | Axis-aligned box                              |
| `"polygon"`   | `POLYGON`   | Closed polygon (typically ≥ 3 vertices)       |
| `"obb"`       | `OBB`       | Oriented bounding box (exactly 4 corners)     |
| `"pose2d"`    | `POSE2D`    | Axis-aligned person/box rectangle + keypoints |

Stroke colour is **not** stored; it is derived from `fillColor` at render time for contrast.

### `coords` object

`coords` is one object; unused fields are **`null` and omitted** from JSON (Gson does not serialize nulls).

| Field     | Type            | Used by               |
|-----------|-----------------|-----------------------|
| `x`       | number          | `rectangle`, `pose2d` |
| `y`       | number          | `rectangle`, `pose2d` |
| `width`   | number          | `rectangle`, `pose2d` |
| `height`  | number          | `rectangle`, `pose2d` |
| `points`  | array of points | `polygon`, `pose2d`   |
| `corners` | array of points | `obb`                 |

### Point object (`points` / `corners` items)

```json
{ "x": 0.25, "y": 0.40, "v": 2 }
```

| Field | Type    | Required | Description                                                              |
|-------|---------|----------|--------------------------------------------------------------------------|
| `x`   | number  | yes      | Normalized horizontal coordinate                                         |
| `y`   | number  | yes      | Normalized vertical coordinate                                           |
| `v`   | integer | no       | Visibility (mainly for **pose2d** keypoints). Default **2** when omitted |

| `v` | Meaning in editor       |
|-----|-------------------------|
| `0` | Not labeled / not drawn |
| `2` | Visible (default)       |

Other positive values are preserved but treated like visible for drawing.

---

## Geometry by type

### `rectangle`

Axis-aligned bounding box as top-left corner plus size (same convention as COCO / many detectors).

**`coords` fields:** `x`, `y`, `width`, `height` — all required.

```json
{
  "id": "car",
  "type": "rectangle",
  "fillColor": "#FF0000",
  "coords": {
    "x": 0.10,
    "y": 0.20,
    "width": 0.35,
    "height": 0.50
  }
}
```

| Field             | Meaning                                                            |
|-------------------|--------------------------------------------------------------------|
| `x`, `y`          | Top-left corner (normalized)                                       |
| `width`, `height` | Box size (normalized); must fit within image bounds after clamping |

Rendered as a filled/stroked rectangle. Four corner handles exist in the UI but are not stored separately.

---

### `polygon`

Closed polygon; vertices are connected in array order, last vertex connects back to the first.

**`coords` fields:** `points` (array, at least 3 points for a useful shape).

```json
{
  "id": "roof",
  "type": "polygon",
  "fillColor": "#00FF88",
  "coords": {
    "points": [
      { "x": 0.20, "y": 0.15 },
      { "x": 0.75, "y": 0.10 },
      { "x": 0.80, "y": 0.60 },
      { "x": 0.25, "y": 0.55 }
    ]
  }
}
```

| Field       | Meaning                                |
|-------------|----------------------------------------|
| `points[i]` | Vertex `i` in order around the polygon |

---

### `obb` (oriented bounding box)

Quadrilateral defined by **exactly four** corners. On load, corners are sorted **clockwise** around the centroid (
compatible with [Ultralytics YOLO OBB](https://docs.ultralytics.com/datasets/obb/) export:
`class x1 y1 x2 y2 x3 y3 x4 y4`).

**`coords` fields:** `corners` (array of 4 points).

```json
{
  "id": "vehicle",
  "type": "obb",
  "fillColor": "#3366FF",
  "coords": {
    "corners": [
      { "x": 0.30, "y": 0.40 },
      { "x": 0.70, "y": 0.35 },
      { "x": 0.65, "y": 0.75 },
      { "x": 0.25, "y": 0.70 }
    ]
  }
}
```

| Field                       | Meaning                                                      |
|-----------------------------|--------------------------------------------------------------|
| `corners[0]` … `corners[3]` | Four vertices of the oriented box (order normalized on read) |

If the editor has more than four corner vertices during editing, the shape may be stored as `polygon` instead; on save
with four corners it is stored as `obb`.

---

### `pose2d`

Combines:

1. An **axis-aligned bounding box** (`x`, `y`, `width`, `height`) — same meaning as `rectangle` (drawn as outline only
   in the overlay, not filled).
2. A list of **keypoints** in `points` (skeleton points inside or around the subject).

**`coords` fields:** `x`, `y`, `width`, `height`, and `points` (all required for a valid pose; keypoints may be empty
array).

```json
{
  "id": "person",
  "type": "pose2d",
  "fillColor": "#FF8800",
  "lock": true,
  "coords": {
    "x": 0.15,
    "y": 0.10,
    "width": 0.40,
    "height": 0.85,
    "points": [
      { "x": 0.32, "y": 0.18 },
      { "x": 0.28, "y": 0.45, "v": 0 },
      { "x": 0.35, "y": 0.70 }
    ]
  }
}
```

| Field                       | Meaning                                                   |
|-----------------------------|-----------------------------------------------------------|
| `x`, `y`, `width`, `height` | Normalized bounding box around the pose instance          |
| `points`                    | Keypoints only (bbox corners are **not** duplicated here) |

In the editor, handle index `0`–`3` are the bbox corners derived from `x/y/width/height`; index `4+` map to `points[0]`,
`points[1]`, … Keypoints with `"v": 0` are skipped when drawing.

---

## Full document example

```json
{
  "annotations": [
    {
      "id": "car",
      "type": "rectangle",
      "fillColor": "#E74C3C",
      "coords": { "x": 0.05, "y": 0.30, "width": 0.40, "height": 0.35 }
    },
    {
      "id": "lane",
      "type": "polygon",
      "fillColor": "#3498DB",
      "coords": {
        "points": [
          { "x": 0.0, "y": 0.6 },
          { "x": 0.5, "y": 0.55 },
          { "x": 1.0, "y": 0.65 },
          { "x": 1.0, "y": 1.0 },
          { "x": 0.0, "y": 1.0 }
        ]
      }
    },
    {
      "id": "sign",
      "type": "obb",
      "fillColor": "#2ECC71",
      "coords": {
        "corners": [
          { "x": 0.60, "y": 0.10 },
          { "x": 0.72, "y": 0.12 },
          { "x": 0.70, "y": 0.28 },
          { "x": 0.58, "y": 0.26 }
        ]
      }
    }
  ]
}
```

Encoding and decoding: `AnnotationJsonCodec` / `AnnotationJsonMapper`.

### `iBSE` — base (original) image

| Property   | Value                                      |
|------------|--------------------------------------------|
| Chunk type | `iBSE`                                     |
| Content    | Full PNG byte sequence of the base image   |
| Role       | Restore the unannotated raster for editing |

The chunk data begins with the PNG magic bytes `89 50 4E 47 0D 0A 1A 0A` and is a self-contained PNG. Decoding uses
`ImageIO` `ImageReader` on the chunk payload (not the top-level file stream).

| Situation                                | Editor base image                                                |
|------------------------------------------|------------------------------------------------------------------|
| `iBSE` present and valid                 | Raster from embedded PNG in `iBSE`                               |
| `iBSE` present but invalid / undecodable | **Open fails** with `IOException` (no silent fallback to `IDAT`) |
| No `iBSE` (plain PNG or legacy file)     | Top-level display raster (`toDisplayPng` → `ImageIO.read`)       |

---

## Read pipeline

Implemented in `AnnotatedPng.read`:

1. Parse all chunks after the PNG signature (`PngChunkIO` / `PngJbbpCodec`
   via [JBBP](https://github.com/raydac/java-binary-block-parser), until `IEND`).
2. **Document** — if `iANN` is present, JSON-decode; otherwise empty `AnnotationDocument`.
3. **Base image** — if `iBSE` is present, decode chunk data as an embedded PNG via `ImageReader`; otherwise rebuild a
   display PNG without custom chunks (`toDisplayPng`) and decode with `ImageIO.read`.

The display raster (`IDAT`) is **not** loaded for editing when `iBSE` exists. It is only used as the base when `iBSE` is
absent.

**Application open path:** files detected with `AnnotatedPng.hasAnnotationChunks` (an `iANN` chunk) always go through
`AnnotatedPng.read` / `EditorSession.open` — never `ImageIO.read` on the file path alone, which would return only the
composed `IDAT` preview.

Plain PNGs without custom chunks open as a new session: base = file raster, empty document.

---

## Write pipeline

Implemented in `AnnotatedPng.write`:

1. `baseImage` — raster under edit (no overlay).
2. `composed` — `AnnotationOverlayRenderer.compose(baseImage, document)`.
3. `ibse` — `encodePng(baseImage)` (full PNG bytes).
4. `iann` — `AnnotationJsonCodec.encode(document)` (UTF-8 JSON).
5. `composedPng` — encode composed image; parse chunks.
6. Insert `iBSE` and `iANN` after `IHDR` via `replaceCustomChunks`.
7. Write chunk stream to the output file.

Save always writes to `EditorSession.filePath()` (the path used when the image was opened).

---

## File naming

Only plain `*.png` files are supported (a single `.png` extension; names like `image.extra.png` are excluded).
Annotations are opened and saved in place on that path.

File tree filtering uses `AllowedImageFiles.isAllowed`.

---

## Implementing an external reader

Minimal steps:

1. Verify PNG signature.
2. Walk chunks (length, type, data, CRC) until `IEND`.
3. **Preview / thumbnails:** skip chunks where `type` is `iANN` or `iBSE`; write a temporary valid PNG and pass to any
   decoder (this is the composed `IDAT` raster).
4. **Annotations:** read `iANN` data as UTF-8 JSON.
5. **Editing / training base:** if `iBSE` is present, decode its payload as a standalone PNG (must start with the PNG
   signature). Do **not** use step 3 as the work background when `iBSE` exists.

Chunk types use lowercase ASCII names (`iANN`, `iBSE`) per PNG convention for private ancillary chunks. Chunk type bytes
are ISO-8859-1 (four ASCII characters).

---

## Related code

| Concern               | Class                                                      |
|-----------------------|------------------------------------------------------------|
| Read / write          | `com.igormaznitsa.annotator.api.png.AnnotatedPng`          |
| Chunk envelope (JBBP) | `com.igormaznitsa.annotator.api.png.PngJbbpCodec`          |
| Chunk I/O facade      | `com.igormaznitsa.annotator.api.png.PngChunkIO`            |
| Chunk names           | `com.igormaznitsa.annotator.api.png.PngConstants`          |
| JSON                  | `com.igormaznitsa.annotator.api.json.AnnotationJsonCodec`  |
| Allowed paths         | `com.igormaznitsa.annotator.api.service.AllowedImageFiles` |
| Round-trip test       | `AnnotatedPngTest`                                         |
