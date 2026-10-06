# Assets: placeholders and how to swap in real art

All textures and models live at their final paths from the start, so replacing art never needs a code change.

## Where things go

| Kind | Path (under `src/main/resources/assets/emergentstealth/`) |
|---|---|
| Item textures | `textures/item/<name>.png` |
| Block textures | `textures/block/<name>.png` |
| Entity/NPC skins & outfit layers | `textures/entity/...` (layout documented in Stage 1) |
| GUI / HUD | `textures/gui/...` |
| Item models | `models/item/<name>.json` + client item definition `items/<name>.json` |
| Sounds | `sounds/...` + `sounds.json` (credits in `docs/credits.md`) |

## Swapping in real art

1. Save your PNG over the placeholder at the same path. Same name, PNG with transparency, **16×16** (or 32×32/64×64 for higher-res; keep the square ratio).
2. For an animated texture, add `<name>.png.mcmeta` next to it (standard Minecraft animation format).
3. In game, press **F3+T** to reload resources. No rebuild needed when running from the dev environment.

## Regenerating placeholders

```
python3 tools/programmer_art/generate.py          # writes only missing textures
python3 tools/programmer_art/generate.py --force  # regenerate all placeholders (overwrites!)
```

Specs live in `tools/programmer_art/textures.json` (shape + palette colours). The generator **skips any file that already exists**, so real art is safe unless you pass `--force`. Requires Python 3 + Pillow (`pip install pillow`).

The palette is "ink and paper" (U-02): ink `#1b1a20`, paper `#e8dcc0`, lacquer red `#a8322d`, steel, wood, jade.
