# tools/art/overrides

Hand-made corrections of the generated textures (decision D5, rule ZW-9 in `docs/04-pionowy-wycinek.md`).

- A file `<texture name>.png` here replaces the texture of that name made by `tools/art/recipes.py`, for example
  `oak_bark.png` or `scots_pine_bark_2.png` (the names are listed in `TEXTURES` in `recipes.py` and in
  `src/main/resources/assets/polishforests/textures/block/`).
- `python tools/art/generate.py` (or `./gradlew generateArt`) copies it into the mod as it is; `./gradlew checkArt`
  then compares the mod file with the override instead of the generator.
- The override must pass the style lint (`python tools/art/lint.py`): 16 x 16, 4-9 colors, grayscale for tinted
  textures, no seam, no banding, and the bounds of its recipe (`expect` in `recipes.py`).
- A PNG whose name matches no texture makes `checkArt` fail (a misnamed correction would never reach the game).
- The seam rule of the lint is weak for sparse leaves: after correcting a leaves texture, look at its 3 x 3 tile in
  `docs/wycinek/grafika/kafle_3x3.png` (`python tools/art/sheet.py`).
- Do not copy, trace or edit textures of Minecraft or of other mods here; overrides are under CC BY 4.0 like every
  asset of the mod (`LICENSE-assets`).
