"""
IRIS App Icon Generator
========================

Generates a production-ready Android Adaptive Icon set using Nano Banana 2
(Gemini 3.1 Flash Image) and Pillow for post-processing.

VERIFIED API FORMAT (ai.google.dev/gemini-api/docs/image-generation,
fetched live June 2026):

- Model: gemini-3.1-flash-image ("Nano Banana 2") — high-efficiency,
  optimized for speed/high-volume use. Alternative: gemini-3-pro-image
  ("Nano Banana Pro") for higher fidelity / better text rendering, slower
  and more expensive.
- SDK: `google-genai` package (NOT the older `google-generativeai`)
- Call: client.models.generate_content(model=..., contents=..., config=...)
  with config.response_modalities = ["TEXT", "IMAGE"]
- Image extraction: iterate response.parts, use part.as_image() or
  part.inline_data
- image_size param accepts "512" (0.5K, Flash only), "1K", "2K", "4K"
  (uppercase K required)
- aspect_ratio accepts "1:1", "3:4", "4:3", "9:16", "16:9", "5:4", "4:5",
  "2:3", "3:2", "1:4", "4:1", "1:8", "8:1", "21:9" (Flash-only ones are
  the extreme ratios; 1:1 used here is supported by all)
- CONFIRMED FROM DOCS: "The model does not support generating a
  transparent background" — so for app icon work, the only reliable path
  is: generate on a solid, known background color -> remove it
  programmatically. This script follows that documented approach.

WHAT THIS SCRIPT DOES:
1. Generates the icon art with Nano Banana 2 using a prompt engineered for
   a solid white background (the docs' own example for stickers/icons
   explicitly requests "white background" — that's the documented best
   practice for clean keying, more reliable than a dark/gradient color).
2. Removes the background programmatically (color-key + edge feather) to
   produce a transparent PNG.
3. Produces Android Adaptive Icon layers:
   - ic_launcher_foreground.png (orb, centered, transparent bg, scaled to
     Android's adaptive icon safe-zone)
   - ic_launcher_background.png (solid #18181B, flat color layer)
4. Produces a flat fallback ic_launcher.png (orb composited onto IRIS's
   dark background) for older Android versions / Play Store listing.
5. Writes mipmap-anydpi-v26/ic_launcher.xml adaptive icon descriptor.

SETUP:
    pip install google-genai pillow

    export GEMINI_API_KEY="your-key-here"
    # Get a key at https://aistudio.google.com/apikey

USAGE:
    python generate_iris_icon.py

OUTPUT (in ./icon_output/):
    candidates/candidate_1-4.png   raw generations, white background
    orb_cutout.png                  background removed, cropped
    ic_launcher_foreground.png      432x432, orb + transparent padding
    ic_launcher_background.png      432x432, flat #18181B
    ic_launcher_flat.png            512x512, composited, Play Store/fallback
    ic_launcher.xml                 adaptive icon XML

NOTE ON COST: Nano Banana 2 (gemini-3.1-flash-image) is a paid model for
programmatic API use. Verify current per-image pricing at
https://ai.google.dev/gemini-api/docs/pricing before running at scale.
This script requests 4 candidates per run (4 separate calls, since
generate_content with this model returns one image per call, not a
numberOfImages batch param like Imagen).
"""

import os
import sys
from pathlib import Path

try:
    from google import genai
    from google.genai import types
except ImportError:
    print("Missing dependency. Run: pip install google-genai")
    sys.exit(1)

try:
    from PIL import Image
except ImportError:
    print("Missing dependency. Run: pip install pillow")
    sys.exit(1)


# =============================================================================
# CONFIG
# =============================================================================

OUTPUT_DIR = Path("./icon_output")

# "Nano Banana 2" — fast/cheap tier. Switch to "gemini-3-pro-image" for
# higher fidelity ("Nano Banana Pro") if candidates look inconsistent.
MODEL_NAME = "gemini-3.1-flash-image"

NUM_CANDIDATES = 4

# Android adaptive icon background layer color (IRIS dark theme).
BG_HEX = "#18181B"

# Background used DURING GENERATION for clean keying. The docs' own
# sticker/icon example explicitly asks for "white background" as the
# documented best practice — a flat saturated dark color is harder for
# the model to render perfectly evenly, white is the most reliable
# documented keying target.
GEN_BG = "white"
GEN_BG_RGB = (255, 255, 255)

# The core icon prompt. Style reference: matte clay-like 3D orb, painted
# wave texture, lavender-to-green gradient, per Muhofy's reference image.
ICON_PROMPT = (
    "A simple 3D orb logo icon for a mobile app, sticker style. Soft matte "
    "clay-like material with a painted wave texture flowing diagonally "
    "across its surface. Gradient color from lavender purple to soft "
    "indigo blue to mint green. Smooth rounded shape, no glossy "
    "highlights, soft matte studio lighting, minimal soft shadow directly "
    "beneath the orb. The orb is perfectly centered and small relative to "
    "the frame, with generous empty space around it. The background must "
    "be solid plain white, filling the entire frame edge to edge with no "
    "gradient, vignette, or texture in the background. No text, no face, "
    "no other objects. Clean minimal premium app icon design."
)


# Android adaptive icon geometry (per developer.android.com/develop/ui/
# views/launch/icon_design_adaptive). Full layer is 108x108dp; the inner
# ~66dp circle is the safe zone visible under any launcher mask shape.
# Rendering at 432px (4x reference scale) and keeping the orb within
# roughly 60% of canvas width keeps it safely inside that zone.
CANVAS_SIZE = 432
SAFE_ZONE_RATIO = 0.60


# =============================================================================
# STEP 1 — Generate with Nano Banana 2
# =============================================================================

def generate_candidates(client: "genai.Client") -> list[Path]:
    """
    Calls gemini-3.1-flash-image once per candidate (this model returns one
    image per generate_content call — there is no numberOfImages batch
    parameter like the separate Imagen API has) and saves each result.

    UNTESTED — request shape matches the verified docs example exactly
    (generate_content + response_modalities=["TEXT","IMAGE"] +
    part.as_image()), but actual output composition/cleanliness of the
    white background should be checked manually per candidate.
    """
    candidates_dir = OUTPUT_DIR / "candidates"
    candidates_dir.mkdir(parents=True, exist_ok=True)

    saved_paths = []
    for i in range(1, NUM_CANDIDATES + 1):
        print(f"  Generating candidate {i}/{NUM_CANDIDATES}...")

        response = client.models.generate_content(
            model=MODEL_NAME,
            contents=ICON_PROMPT,
            config=types.GenerateContentConfig(
                response_modalities=["TEXT", "IMAGE"],
                image_config=types.ImageConfig(
                    aspect_ratio="1:1",
                    image_size="2K",
                ),
            ),
        )

        saved_this_round = False
        for part in response.parts:
            if part.text is not None:
                continue
            image = part.as_image()
            if image is not None:
                candidate_path = candidates_dir / f"candidate_{i}.png"
                image.save(candidate_path)
                saved_paths.append(candidate_path)
                saved_this_round = True
                print(f"    Saved: {candidate_path}")
                break

        if not saved_this_round:
            print(f"    WARNING: candidate {i} returned no image part, skipping")

    if not saved_paths:
        raise RuntimeError(
            "No candidates were generated. Check API key, model availability, "
            "and safety-filter rejections in the response."
        )

    print(f"\n{len(saved_paths)} candidate(s) generated in {candidates_dir}/")
    print("Review them and confirm which one to process before continuing.\n")
    return saved_paths


# =============================================================================
# STEP 2 — Background removal (color-key based)
# =============================================================================

def remove_background(src_path: Path, bg_rgb: tuple, tolerance: int = 24) -> Image.Image:
    """
    Removes a near-solid background color via per-pixel color-distance
    keying, with a feathered edge band to avoid hard/jagged cutout edges.

    Chosen over an external ML segmentation library (e.g. rembg) to keep
    this script dependency-light. Since the prompt requests a flat white
    background, simple distance-based keying should work, but lighting
    falloff near the orb's shadow can still leave faint artifacts.

    UNTESTED — tune `tolerance` based on actual output. If the shadow
    beneath the orb gets keyed out entirely (looks too "floating") or the
    orb's bright highlight gets eaten by the keying, lower tolerance and
    re-run. Always inspect orb_cutout.png before using in production.
    """
    img = Image.open(src_path).convert("RGBA")
    bg_r, bg_g, bg_b = bg_rgb
    pixels = img.load()
    w, h = img.size

    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            dist = ((r - bg_r) ** 2 + (g - bg_g) ** 2 + (b - bg_b) ** 2) ** 0.5
            if dist < tolerance:
                pixels[x, y] = (r, g, b, 0)
            elif dist < tolerance * 2.5:
                fade = (dist - tolerance) / (tolerance * 1.5)
                pixels[x, y] = (r, g, b, int(255 * min(max(fade, 0), 1)))

    return img


def crop_to_content(img: Image.Image, padding_ratio: float = 0.18) -> Image.Image:
    """
    Crops to the bounding box of non-transparent content, then re-pads
    symmetrically so the subject is centered in a square canvas.
    """
    bbox = img.getbbox()
    if bbox is None:
        raise ValueError(
            "Cutout produced a fully empty image — background removal "
            "over-keyed everything. Lower the tolerance and retry."
        )

    cropped = img.crop(bbox)
    side = max(cropped.size)
    pad = int(side * padding_ratio)
    canvas_side = side + pad * 2

    canvas = Image.new("RGBA", (canvas_side, canvas_side), (0, 0, 0, 0))
    offset = (
        (canvas_side - cropped.width) // 2,
        (canvas_side - cropped.height) // 2,
    )
    canvas.paste(cropped, offset, cropped)
    return canvas


# =============================================================================
# STEP 3 — Build Adaptive Icon layers
# =============================================================================

def build_foreground_layer(orb_img: Image.Image) -> Image.Image:
    canvas = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
    target_diameter = int(CANVAS_SIZE * SAFE_ZONE_RATIO)

    orb_resized = orb_img.resize((target_diameter, target_diameter), Image.LANCZOS)
    offset = (
        (CANVAS_SIZE - target_diameter) // 2,
        (CANVAS_SIZE - target_diameter) // 2,
    )
    canvas.paste(orb_resized, offset, orb_resized)
    return canvas


def build_background_layer() -> Image.Image:
    bg_rgb = tuple(int(BG_HEX.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4))
    return Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), bg_rgb + (255,))


def build_flat_fallback(foreground: Image.Image, background: Image.Image) -> Image.Image:
    flat = background.copy()
    flat.paste(foreground, (0, 0), foreground)
    return flat.resize((512, 512), Image.LANCZOS)


ADAPTIVE_ICON_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
"""


# =============================================================================
# MAIN
# =============================================================================

def main():
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        print("ERROR: GEMINI_API_KEY environment variable not set.")
        print('Run: export GEMINI_API_KEY="your-key-here"')
        print("Get a key at: https://aistudio.google.com/apikey")
        sys.exit(1)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    print("=" * 60)
    print("IRIS App Icon Generator (Nano Banana 2)")
    print("=" * 60)

    print(f"\n[1/4] Generating {NUM_CANDIDATES} candidates via {MODEL_NAME}...")
    client = genai.Client(api_key=api_key)
    candidate_paths = generate_candidates(client)

    source_path = candidate_paths[0]
    print(f"[2/4] Processing source: {source_path}")
    print("       (edit `source_path` below in this script to pick a")
    print("        different candidate, then re-run from this point)\n")

    cutout = remove_background(source_path, GEN_BG_RGB)
    cutout = crop_to_content(cutout)
    cutout.save(OUTPUT_DIR / "orb_cutout.png")
    print(f"  Saved: {OUTPUT_DIR / 'orb_cutout.png'} (REVIEW THIS before continuing!)")

    print("[3/4] Building adaptive icon layers...")
    foreground = build_foreground_layer(cutout)
    background = build_background_layer()
    flat = build_flat_fallback(foreground, background)

    foreground.save(OUTPUT_DIR / "ic_launcher_foreground.png")
    background.save(OUTPUT_DIR / "ic_launcher_background.png")
    flat.save(OUTPUT_DIR / "ic_launcher_flat.png")

    print("[4/4] Writing adaptive icon XML...")
    (OUTPUT_DIR / "ic_launcher.xml").write_text(ADAPTIVE_ICON_XML)

    print("\n" + "=" * 60)
    print("DONE. Output in ./icon_output/:")
    print("  candidates/candidate_1-4.png   <- review, pick the best source")
    print("  orb_cutout.png                  <- check edges aren't jagged")
    print("  ic_launcher_foreground.png      -> res/mipmap-anydpi-v26/")
    print("  ic_launcher_background.png      -> res/mipmap-anydpi-v26/")
    print("  ic_launcher_flat.png             -> Play Store listing / legacy")
    print("  ic_launcher.xml                  -> res/mipmap-anydpi-v26/")
    print("=" * 60)
    print("\nIf orb_cutout.png has jagged edges or white fringing:")
    print("  - lower `tolerance` in remove_background() for tighter keying")
    print("  - or raise it if the shadow/soft edges are being cut off")
    print("  - or just pick a cleaner candidate and edit `source_path` above")


if __name__ == "__main__":
    main()