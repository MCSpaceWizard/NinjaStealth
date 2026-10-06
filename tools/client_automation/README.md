# Client automation (cloud sessions)

This lets Claude (or anyone on Linux) run the real game client on a virtual display, type commands and take screenshots. It's how visuals like NPC skins, indicators, the light gem and unlit blocks get checked without a monitor.

## One-time setup

```bash
python3 -m venv /tmp/es-venv && /tmp/es-venv/bin/pip install python-xlib nbtlib
Xvfb :99 -screen 0 1280x720x24 -nolisten tcp &
```

## Test world

1. Generate a superflat world with the dedicated server. Set `level-type=minecraft\:flat` and `level-name=stealthtest_world` in `run/server.properties`, then start `./gradlew runServer` once and stop it.
2. Copy it to `run/saves/StealthTest`, delete `session.lock`, and enable cheats: set `Data.allowCommands = 1` in `level.dat` (nbtlib).
3. In `run/options.txt` set `onboardAccessibility:false` and `pauseOnLostFocus:false`. Otherwise the first-run screen blocks quick play.

## Run

```bash
DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 ./gradlew runClient -PquickPlay=StealthTest &
# wait for "joined the game" in the log, then:
/tmp/es-venv/bin/python tools/client_automation/xin.py cmd "/time set midnight" cmd "/es npc spawn emergentstealth:ashigaru"
import -display :99 -window root /tmp/shot.png   # ImageMagick screenshot
```

`xin.py` actions:
- `key <keysym>`
- `type <text>`
- `cmd <chat command>`
- `click <x> <y>` (root-window coordinates)
- `rclick`
- `look <dx> <dy>`
- `sleep <s>`

## Tips

- Switch to creative (and `/difficulty peaceful`) before typing a run of commands near hostile NPCs or mobs. Guards fight back, and typing takes a few seconds.
- `/tp @s x y z <yaw> <pitch>` aims more reliably than `tp ... facing` (which aims from the feet).
- Stop the client by killing the JVM that has `-Dfml.modFolders` in its arguments, in a separate shell command.
