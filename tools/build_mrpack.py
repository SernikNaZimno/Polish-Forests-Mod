"""Builds the recommended modpack (.mrpack, Modrinth format) for the "Polish Forests" mod.

Usage (in the project directory, after ./gradlew build):
    python tools/build_mrpack.py

Mod versions are pinned by Modrinth version ids. The script fetches file URLs, sizes and hashes
from the Modrinth API and adds our mod from build/libs as an "overrides" file.
Output goes to build/distributions/.
"""
import glob
import hashlib
import io
import json
import os
import sys
import urllib.request
import zipfile

MINECRAFT = "26.3"
FABRIC_LOADER = "0.19.5"
PACK_NAME = "Polish Forests"

# (slug, version id, side: "both" / "client" / "server", required?, description)
MODS = [
    ("fabric-api", "bNnaTiuM", "both", True, "Fabric API, library required by the mod"),
    ("geckolib", "kSxHvs99", "both", True, "GeckoLib, animal models and animations"),
    ("smartbrainlib", "i5vOn6hO", "both", True, "SmartBrainLib, animal behavior"),
    ("serene-seasons", "V9PxJPuw", "both", True, "Serene Seasons, seasons"),
    ("glitchcore", "aaUghyGp", "both", True, "GlitchCore, library required by Serene Seasons"),
    ("sodium", "bAZQdGpg", "client", True, "Sodium, fast renderer (essential for the tall world)"),
    ("lithium", "WXHRsMRl", "both", True, "Lithium, game logic and server optimizations"),
    ("ferrite-core", "d5ddUdiB", "both", True, "FerriteCore, lower memory usage"),
    ("c2me-fabric", "sSoXjAqP", "both", True, "C2ME, multithreaded chunk generation and saving"),
    ("scalablelux", "g4eqNSKd", "both", True, "ScalableLux, faster lighting calculations"),
    ("immediatelyfast", "3MP9UR23", "client", True, "ImmediatelyFast, faster UI and entity rendering"),
    ("entityculling", "F4loCvYt", "client", True, "EntityCulling, skips hidden entities (important with many animals)"),
    ("moreculling", "zL2UEFXS", "client", True, "MoreCulling, skips hidden block faces, e.g. leaves"),
    ("cloth-config", "fg2uyxOW", "client", True, "Cloth Config, required by MoreCulling"),
    ("badoptimizations", "Sp0ctspw", "client", True, "BadOptimizations, small client optimizations"),
    ("dynamic-fps", "Jwq069rR", "client", True, "Dynamic FPS, lower frame rate in the background"),
    ("modmenu", "kyy7dbrZ", "client", True, "Mod Menu, list of mods and their settings"),
    ("placeholder-api", "lXytLqWj", "client", True, "Placeholder API, required by Mod Menu"),
    ("sodium-extra", "te2y9qZn", "client", True, "Sodium Extra, extra graphics performance settings"),
    ("better-block-entities", "9VvhfLcA", "client", True, "Better Block Entities, faster chests, signs and other block entities"),
    ("asyncparticles", "iDHkUsnf", "client", True, "AsyncParticles, particles computed off the main thread"),
    ("rrls", "CAVJGGfj", "client", True, "RRLS, resource reloading in the background"),
    ("fastquit", "ZZ5dfboC", "client", True, "FastQuit, saves the world in the background after quitting to the menu"),
    ("forcecloseworldloadingscreen", "6XQXbIMc", "client", True, "Force Close Loading Screen, shorter world loading screen"),
    ("modernfix-mvus", "pa9cAfYg", "both", True, "ModernFix, faster startup and lower memory usage"),
    ("debugify", "FMaS2nZn", "both", True, "Debugify, fixes for game bugs that hurt performance"),
    ("packet-fixer", "dTKbGYbb", "both", True, "Packet Fixer, higher packet limits (tall chunks of the Poland world)"),
    ("chunky", "4Eotm6ov", "both", True, "Chunky, pre-generates the world in advance"),
    ("ksyxis", "9CU8nnVG", "both", True, "Ksyxis, faster world loading without permanently loaded spawn chunks"),
    ("structure-layout-optimizer", "crWm7jXS", "both", True, "Structure Layout Optimizer, faster generation of villages and other structures"),
    ("resourceful-config", "IFB0XCI9", "both", True, "Resourceful Config, required by Structure Layout Optimizer"),
    ("zfastnoise", "BWKn67tN", "both", True, "zFastNoise, faster Nether and End generation"),
    ("zconfig", "tsgt79sG", "both", True, "zConfig, required by zFastNoise"),
    ("alternate-current", "nSBWPz6x", "both", True, "Alternate Current, more efficient redstone"),
    ("asynclogger", "Ert0LmWj", "both", True, "AsyncLogger, writes logs in the background"),
    ("clumps", "J4I1wxJZ", "both", True, "Clumps, merges experience orbs"),
    ("nvidium", "8mVK1zbk", "client", False, "Nvidium (optional, NVIDIA GPUs only), faster long-distance rendering"),
    ("distanthorizons", "gfi11b05", "client", False, "Distant Horizons (optional), distant mountain views"),
]

API = "https://api.modrinth.com/v2"


def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "polishforests-mrpack-builder/1.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def resolve(slug, version_id):
    if version_id:
        return get_json(f"{API}/version/{version_id}")
    versions = get_json(
        f'{API}/project/{slug}/version?game_versions=["{MINECRAFT}"]&loaders=["fabric"]'.replace('"', "%22")
        .replace("[", "%5B").replace("]", "%5D"))
    if not versions:
        raise SystemExit(f"No {slug} version for Minecraft {MINECRAFT}")
    return versions[0]


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    jars = [j for j in glob.glob(os.path.join(root, "build", "libs", "polishforests-*.jar"))
            if not j.endswith("-sources.jar")]
    if not jars:
        raise SystemExit("build/libs/polishforests-*.jar not found. Run ./gradlew build first")
    jar = max(jars, key=os.path.getmtime)
    mod_version = os.path.basename(jar)[len("polishforests-"):-len(".jar")]

    files = []
    lines = []
    for slug, vid, side, required, desc in MODS:
        v = resolve(slug, vid)
        f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
        env_client = "required" if required and side in ("both", "client") else (
            "optional" if side in ("both", "client") else "unsupported")
        env_server = "required" if side in ("both", "server") else "unsupported"
        files.append({
            "path": f"mods/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": {"client": env_client, "server": env_server},
            "downloads": [f["url"]],
            "fileSize": f["size"],
        })
        lines.append(f"| {desc} | {v['version_number']} | {side} | {'yes' if required else 'no'} |")
        print(f"{slug:18s} {v['version_number']}")

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": mod_version,
        "name": PACK_NAME,
        "summary": "Procedural 1:1 landscapes of Poland, bundled with recommended performance mods.",
        "files": files,
        "dependencies": {"minecraft": MINECRAFT, "fabric-loader": FABRIC_LOADER},
    }
    out_dir = os.path.join(root, "build", "distributions")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, f"Polish-Forests-{mod_version}.mrpack")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2, ensure_ascii=False))
        z.write(jar, f"overrides/mods/{os.path.basename(jar)}")
    sha = hashlib.sha1(open(out, "rb").read()).hexdigest()
    print(f"Wrote {out} ({os.path.getsize(out) // 1024} KB, sha1 {sha})")

    table = os.path.join(out_dir, "pack-contents.md")
    with io.open(table, "w", encoding="utf-8") as t:
        t.write(f"# {PACK_NAME} {mod_version}: pack contents\n\n"
                "| Mod | Version | Side | Required |\n|---|---|---|---|\n" + "\n".join(lines) + "\n")
    print(f"Wrote {table}")


if __name__ == "__main__":
    sys.exit(main())
