# Puzzle Image Pipeline

Yes, add images now.

For this project, use packaged local images first. That is the simplest path for a single-developer offline app and it gives agents a stable content format to work with.

## Recommended approach

Store puzzle images in:

`app/src/main/assets/puzzles/`

Keep a small manifest beside them:

`app/src/main/assets/puzzles/manifest.json`

This is better than hardcoding image entries in Kotlin because:

- new puzzle content can be added without editing code
- metadata stays close to the asset
- agents can update content in one place
- later migration to downloaded content is easier

## Image guidelines

- Start with 4 to 8 images.
- Prefer portrait-friendly images.
- Recommended minimum size: `1600x2000`.
- Recommended format: `webp` or high-quality `jpg`.
- Avoid images with tiny text, watermarks, or important details near the edges.
- Avoid near-monochrome images for early testing because they make piece matching harder to validate.

## Naming

Use stable slug names:

- `coastal-dawn.webp`
- `forest-path.webp`
- `city-glow.webp`

## Manifest shape

Each image entry should include:

- `id`
- `title`
- `subtitle`
- `description`
- `fileName`

## Example

```json
{
  "images": [
    {
      "id": "coastal-dawn",
      "title": "Coastal Dawn",
      "subtitle": "Soft light and open water",
      "description": "A calm seascape with clear color zones.",
      "fileName": "coastal-dawn.webp"
    }
  ]
}
```

## Next implementation step

Once you add real images, the app should load the manifest and render the real assets instead of the current placeholder catalog.
