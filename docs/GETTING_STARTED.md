# Getting started: get the repo and build it

## 1. One-time setup

1. **Install Git:** <https://git-scm.com/downloads>
2. **Install JDK 25** (Eclipse Temurin recommended): <https://adoptium.net/temurin/releases/?version=25>
   - Windows: use the `.msi` installer and tick **"Set JAVA_HOME"**.
   - macOS: use the `.pkg`. Linux: your package manager or the `.tar.gz`.
   - Check it in a new terminal: `java -version` should say `25`.
   - If you skip this, Gradle will try to download a JDK 25 automatically on first build, which usually works too.
3. **Optional, recommended: IntelliJ IDEA Community** (<https://www.jetbrains.com/idea/download/>) with the **Minecraft Development** plugin.

## 2. Get the code

```bash
git clone https://github.com/MCSpaceWizard/NinjaStealth.git
cd NinjaStealth
git checkout claude/pensive-euler-7o41g4
```

> **Important:** work happens on the branch above until it's merged into `main` through a pull request. If you're on `main` you won't see the latest code.

To pick up new work later:
```bash
git checkout claude/pensive-euler-7o41g4
git pull
```

## 3. Build and run

From the repo folder (on Windows use `gradlew.bat` instead of `./gradlew`):

| Command | What it does |
|---|---|
| `./gradlew build` | Compiles and produces `build/libs/emergentstealth-<version>.jar` |
| `./gradlew runClient` | Starts Minecraft with the mod (dev account "Dev") |
| `./gradlew runServer` | Starts a dedicated server (accept the EULA in `run/eula.txt` the first time) |
| `./gradlew runClient2` | Second client "Dev2", for co-op tests against `runServer` (connect to `localhost`) |
| `./gradlew runGameTestServer` | Runs the automated in-game tests headlessly |

The **first build takes a while** (5–15 min): it downloads Minecraft, NeoForge and libraries. Later builds are much faster.

**In IntelliJ:** File → Open → pick the repo folder → "Trust project". Wait for the Gradle sync, then use the run configurations (`runClient` etc.) in the top-right dropdown. If the JDK is wrong: File → Project Structure → SDK → 25.

## 4. Playing the jar in a normal launcher (friends/testers)

1. Install **NeoForge 26.1.2** in your launcher (Prism Launcher / CurseForge app / the official installer from neoforged.net).
2. Drop `build/libs/emergentstealth-<version>.jar` into that instance's `mods/` folder.
3. Server and clients must run the **same jar**.

## 5. When something breaks

- **Build error:** copy the first error block from the terminal (it starts with `error:` or `FAILURE:`), or open `build/reports/problems/problems-report.html`, and send it over.
- **Crash in game:** send `run/logs/latest.log` (dev) or the crash report from `run/crash-reports/`.
- **"Unsupported class file major version" / Java errors:** the wrong JDK is in use. Check `java -version` says 25, or set `JAVA_HOME` to the JDK 25 folder.
- **Weird Gradle state:** `./gradlew --stop`, then try again. As a last resort, delete the `.gradle/` and `build/` folders in the repo.

## 6. Cloud sessions (Claude's environment)

So Claude can compile and run tests inside cloud sessions, the environment needs:

1. **Setup script** (environment settings → *Setup script*):
   ```bash
   bash scripts/cloud-setup.sh
   ```
   It installs a checksum-verified JDK 25 and points Gradle at it.
2. **Network access** (environment settings → *Network access* → **Custom**). Keep the default package-manager list and add these allowed domains:
   ```
   maven.neoforged.net
   piston-meta.mojang.com
   piston-data.mojang.com
   launchermeta.mojang.com
   libraries.minecraft.net
   resources.download.minecraft.net
   repo.maven.apache.org
   repo1.maven.org
   plugins.gradle.org
   services.gradle.org
   api.foojay.io
   api.adoptium.net
   maven.parchmentmc.org
   api.modrinth.com
   cursemaven.com
   maven.theillusivec4.top
   dl.cloudsmith.io
   maven.terraformersmc.com
   maven.blamejared.com
   maven.createmod.net
   maven.shedaniel.me
   ```
   The first six are required to build at all. The rest cover Gradle, the JDK and the optional mod integrations (Curios, GeckoLib, EMI, Jade, …).

   Docs: <https://code.claude.com/docs/en/cloud-environments#network-access>
