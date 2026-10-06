# Research: NPC skins & outfit textures we can ship

> **2026-10-06, research only. Nothing has been added to the repo yet.** Licences were read on the source pages. PlanetMinecraft, minecraftskins.com and NameMC were blocked from the cloud, so their terms come from search excerpts of the official ToS. Re-check them before relying on them.

## Bottom line

- No ready-made 64×64 samurai, ashigaru or daimyo skins exist with a genuine CC0 or CC-BY licence.
- **Plan:**
  - Keep the procedural generator as what ships, and feed it CC0 inputs.
  - Redraw a few CC0 designs at 64×64.
  - **Commission the armoured archetypes** (samurai, taisho, daimyo).
- **Licensing rule:** ship only CC0, CC-BY or our own art.
  - Share-alike (CC BY-SA) files and any edits of them must stay share-alike. Commissioned art built on such a base inherits that.
  - NC (non-commercial) licences conflict with CurseForge/Modrinth reward programmes.

## Shortlist

| # | Source | Licence | What it is | Use for us |
|---|---|---|---|---|
| 1 | [Kenney – Blocky Characters 2.0](https://kenney.nl/assets/blocky-characters) | CC0 (page + `License.txt`) | 18 box-UV skins at 1024², flat style, stubbier proportions (not 1:1 Minecraft) | Redraw at 64×64: **ninja** (`texture-r`), **kimono woman** (`texture-n`) → townsfolk; commoners → labourer |
| 2 | [Screaming Brain Studios – Tiny Texture Pack 3](https://screamingbrainstudios.itch.io/tiny-texture-pack-3) (packs 1–2 too) | CC0 | Seamless cloth and weave textures, 128–512 px, no generative AI | Downsample and palette-quantise into generator fills: kimono fabric, hemp, straw (kasa hats, mino capes) |
| 3 | [Ninja Adventure Asset Pack](https://pixel-boy.itch.io/ninja-adventure-asset-pack) | CC0 | 16px top-down Japanese-themed characters | Palette and costume reference for all archetypes |
| 4 | [The Met Open Access](https://www.metmuseum.org/policies/image-resources) (Arms & Armor, textiles) | CC0 (per object) | Photos of real armour and kimono | Reference for artists (lacing, lacquer, kamon) and palette sampling |
| 5 | [Luanti Character Template](https://opengameart.org/content/luanti-character-template) | CC0 | 64×32 template + colour-coded UV map | Convert to a 64×64 UV guide (classic + slim arms) for artists |
| 6 | [Minetest Skins Pack 1](https://opengameart.org/content/minetest-skins-pack-1) | CC0 | 32 legacy 64×32 skins, Western style | Weak; a few farmer skins could become labourer bases |
| 7 | [mcl_skins](https://codeberg.org/mineclonia/mineclonia/src/branch/main/mods/PLAYER/mcl_skins) | Code MIT; textures CC BY-SA (mixed) | 64×64 paper doll: base/footwear/eye/mouth/bottom/top/hair/headwear, rank order, tint masks | **Blueprint for our layer system** (reimplement the idea; don't ship its textures) |
| 8 | [Pixel Perfection CE](https://github.com/Athemis/PixelPerfectionCE) | CC BY-SA 4.0 | 16× resource pack incl. skins | Style reference only (share-alike) |

Traditional patterns (seigaiha, asanoha, ichimatsu, kikkō) are centuries-old public-domain designs. We can generate them in code without any source asset.

## Not usable

| Source | Why |
|---|---|
| NameMC, skinsmc, mskins, Tynker, other aggregators | Scraped player skins, no licence |
| Planet Minecraft | Creators keep copyright and the licence is granted to PMC only; usable only where an individual page states an open licence and the uploader is the author |
| The Skindex | The licence covers only the site; many recolours of other people's work |
| Skinsdb / Addi's MT-Skin DB | Per-skin licences are self-declared and often false; themed skins are BY-SA, 64×32 and low quality |
| GitHub "CC0 skin" collections (e.g. lkjy-coding/MinecraftSkins) | The uploader isn't the author |
| Minecraft remaps of Kenney characters on itch.io without a licence | No licence; do our own remap from the CC0 originals |
| Mojang default skins and vanilla textures | Can't be redistributed |
| Minetest Game textures | CC BY-SA |
| Lynocs Texture Pack | AI-assisted, unclear provenance, blocks only |

## Tools for artists

| Tool | Licence | Use |
|---|---|---|
| [Blockbench](https://github.com/JannisX11/blockbench) | GPL-3.0 (the tool only; output is yours) | Minecraft skin projects with paint layers: body, under-layer and armour kept separate |
| [Pixelorama](https://github.com/Orama-Interactive/Pixelorama) | MIT | Free layered pixel editor |
| [skinview3d](https://github.com/bs-community/skinview3d) | MIT | Browser 3D preview of composited body + outfit PNGs |

## Recommendation

1. **Now:**
   - Upgrade the generator with CC0 cloth and weave inputs plus coded traditional motifs.
   - Hand-redraw Kenney's kimono (townsfolk) and ninja at 64×64.
2. **Commission** samurai, taisho and daimyo armour. Give the artists Met reference photos and a layered Blockbench template.
3. **Layer system v2** (when art starts):
   - Outfit slots with rank order, plus greyscale-and-mask tinting so one outfit yields many colour variants, modelled on mcl_skins.
   - Ship a CC0 64×64 UV guide.
4. **Track credits** for anything CC-BY in `docs/credits.md`.
