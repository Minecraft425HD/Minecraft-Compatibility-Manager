# Minecraft Compatibility Manager

Scans your Minecraft mods folder for compatibility issues — duplicate mods, version conflicts, Mixin clashes, shaded library collisions, and more.

Works in two modes:
- **Standalone scanner** — a plain JAR, no Minecraft installation required, supports every MC version ever released
- **In-game mod** — shows issues in a GUI overlay at the title screen (Fabric · Forge · NeoForge · Quilt)

---

## Requirements

| Tool | Version |
|------|---------|
| Java | 17 or newer |
| Gradle | bundled via `./gradlew` (no install needed) |

---

## Quick Start — Standalone Scanner

The standalone scanner is the easiest way to check any mods folder without launching Minecraft.

### 1. Build the JAR

```bash
./gradlew :standalone:jar
```

The fat JAR is written to `standalone/build/libs/compatmanager-standalone-1.0.0-standalone.jar`.

### 2. Run it

```bash
# Auto-detect your mods folder (checks ~/.minecraft/mods, PrismLauncher, etc.)
java -jar standalone/build/libs/compatmanager-standalone-1.0.0-standalone.jar

# Point it at a specific folder
java -jar standalone/build/libs/compatmanager-standalone-1.0.0-standalone.jar /path/to/mods

# With MC version + loader context for more accurate results
java -jar standalone/build/libs/compatmanager-standalone-1.0.0-standalone.jar \
    ~/.minecraft/mods \
    --mc-version 1.20.1 \
    --loader fabric
```

### Common Options

| Flag | Description |
|------|-------------|
| `--mc-version <ver>` | Minecraft version (e.g. `1.20.1`) for version-aware checks |
| `--loader <name>` | Loader hint: `fabric` · `forge` · `neoforge` · `quilt` |
| `--config-dir <path>` | Also scan a config folder for keybind / registry-ID conflicts |
| `--fix` | Move conflicting mods to `disabled-by-compatmanager/` automatically |
| `--dry-run` | Preview what `--fix` would do without touching any files |
| `--script` | Generate `fix.bat` + `fix.sh` fix scripts |
| `--html [file]` | Write an HTML report (default: `compat-report.html`) |
| `--json [file]` | Write a JSON report (default: `compat-report.json`) |
| `--verbose` | List all mods, not just the problematic ones |
| `--offline` | Disable network calls, use cached data only |
| `--help` | Show full usage |

### Exit Codes

| Code | Meaning |
|------|---------|
| `0` | No issues found |
| `1` | Issues found (non-critical) |
| `2` | Critical issues found |

---

## Building the In-Game Mod

The in-game mod targets **Minecraft 1.20.1** by default. Change `minecraft_version` in `gradle.properties` to target a different version (see the version matrix in that file).

### Build all modern loaders

```bash
./gradlew :fabric:build :forge:build :neoforge:build :quilt:build
```

JARs are placed in `<loader>/build/libs/`.

### Build the legacy Forge mod (1.7.10 – 1.12.2)

```bash
./gradlew :forge-legacy:build
```

### Install

Copy the built JAR into your Minecraft instance's `mods/` folder like any other mod.

---

## Supported Version Matrix

| Module | MC Versions | Loaders |
|--------|-------------|---------|
| `standalone` | ALL (1.0 – latest) | None — reads JARs directly |
| `forge-legacy` | 1.7.10 – 1.12.2 | Forge (FML 7-14) |
| `common` + `fabric` | 1.16.5 – latest | Fabric (fabric-loader ≥ 0.12) |
| `common` + `forge` | 1.16.5 – 1.20.1 | MinecraftForge (≥ 36.x) |
| `common` + `neoforge` | 1.20.2 – latest | NeoForge (≥ 20.4) |
| `common` + `quilt` | 1.18.2 – latest | Quilt (≥ 0.20) |

---

## What It Detects

- **Duplicate mods** — same mod ID loaded more than once
- **Version conflicts** — mods that declare incompatible version ranges
- **Mixin conflicts** — two mods targeting the same class method
- **Shaded library collisions** — bundled libraries with conflicting packages
- **Class namespace conflicts** — overlapping class paths between mods
- **Coremod conflicts** (legacy Forge)
- **Config / keybind conflicts**
- **Resource pack conflicts**
- **Java bytecode incompatibilities** — mod compiled for a newer Java than available
- **Transitive dependency issues** — missing or wrong-version required mods
- **CurseForge / Modrinth compatibility data** (requires network access)

---

## Project Structure

```
├── common/        Shared API and in-game GUI (all modern loaders)
├── fabric/        Fabric platform implementation
├── forge/         Forge platform implementation (1.16.5 – 1.20.1)
├── neoforge/      NeoForge platform implementation
├── quilt/         Quilt platform implementation
├── forge-legacy/  Legacy Forge module (1.7.10 – 1.12.2)
└── standalone/    Self-contained CLI scanner (no Minecraft required)
```

---

## License

[LICENSE](LICENSE)
