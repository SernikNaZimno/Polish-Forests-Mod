"""Buduje zalecaną paczkę modów (.mrpack, format Modrinth) dla moda "Przyrodniczo zgodne lasy".

Użycie (w katalogu projektu, po ./gradlew build):
    python tools/build_mrpack.py

Wersje modów są przypięte identyfikatorami wersji z Modrinth. Skrypt pobiera z API Modrinth
adresy, rozmiary i sumy kontrolne plików, a nasz mod dołącza z build/libs jako plik "overrides".
Wynik trafia do build/distributions/.
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
PACK_NAME = "Przyrodniczo zgodne lasy"

# (slug, id wersji, strona: "both" / "client" / "server", wymagany?, opis)
MODS = [
    ("fabric-api", "bNnaTiuM", "both", True, "Fabric API, biblioteka wymagana przez mod"),
    ("geckolib", "kSxHvs99", "both", True, "GeckoLib, modele i animacje zwierząt"),
    ("smartbrainlib", "i5vOn6hO", "both", True, "SmartBrainLib, zachowania zwierząt"),
    ("serene-seasons", "V9PxJPuw", "both", True, "Serene Seasons, pory roku"),
    ("glitchcore", "aaUghyGp", "both", True, "GlitchCore, biblioteka Serene Seasons"),
    ("sodium", "bAZQdGpg", "client", True, "Sodium, szybki renderer (kluczowy przy wysokim świecie)"),
    ("lithium", "WXHRsMRl", "both", True, "Lithium, optymalizacja logiki gry i serwera"),
    ("ferrite-core", "d5ddUdiB", "both", True, "FerriteCore, mniejsze zużycie pamięci"),
    ("c2me-fabric", "sSoXjAqP", "both", True, "C2ME, wielowątkowa generacja i zapis chunków"),
    ("scalablelux", "g4eqNSKd", "both", True, "ScalableLux, szybsze obliczanie światła"),
    ("immediatelyfast", "3MP9UR23", "client", True, "ImmediatelyFast, szybsze rysowanie interfejsu i encji"),
    ("entityculling", "F4loCvYt", "client", True, "EntityCulling, pomija niewidoczne encje (ważne przy wielu zwierzętach)"),
    ("moreculling", "zL2UEFXS", "client", True, "MoreCulling, pomija niewidoczne ściany bloków, np. liści"),
    ("cloth-config", "fg2uyxOW", "client", True, "Cloth Config, wymagany przez MoreCulling"),
    ("badoptimizations", "Sp0ctspw", "client", True, "BadOptimizations, drobne optymalizacje klienta"),
    ("dynamic-fps", "Jwq069rR", "client", True, "Dynamic FPS, mniej klatek w tle"),
    ("modmenu", "kyy7dbrZ", "client", True, "Mod Menu, lista modów i ich ustawień"),
    ("placeholder-api", "lXytLqWj", "client", True, "Placeholder API, wymagany przez Mod Menu"),
    ("sodium-extra", "te2y9qZn", "client", True, "Sodium Extra, dodatkowe ustawienia wydajności grafiki"),
    ("better-block-entities", "9VvhfLcA", "client", True, "Better Block Entities, szybsze skrzynie, tabliczki i inne bloki z encją"),
    ("asyncparticles", "iDHkUsnf", "client", True, "AsyncParticles, cząsteczki liczone poza głównym wątkiem"),
    ("rrls", "CAVJGGfj", "client", True, "RRLS, przeładowanie zasobów w tle"),
    ("fastquit", "ZZ5dfboC", "client", True, "FastQuit, zapis świata w tle po wyjściu do menu"),
    ("forcecloseworldloadingscreen", "6XQXbIMc", "client", True, "Force Close Loading Screen, krótszy ekran wczytywania świata"),
    ("modernfix-mvus", "pa9cAfYg", "both", True, "ModernFix, szybsze uruchamianie i mniej pamięci"),
    ("debugify", "FMaS2nZn", "both", True, "Debugify, poprawki błędów gry wpływających na wydajność"),
    ("packet-fixer", "dTKbGYbb", "both", True, "Packet Fixer, większe limity pakietów (wysokie chunki świata Polska)"),
    ("chunky", "4Eotm6ov", "both", True, "Chunky, pregeneracja świata z wyprzedzeniem"),
    ("ksyxis", "9CU8nnVG", "both", True, "Ksyxis, szybsze wczytywanie świata bez stałych chunków spawnu"),
    ("structure-layout-optimizer", "crWm7jXS", "both", True, "Structure Layout Optimizer, szybsza generacja wiosek i innych struktur"),
    ("resourceful-config", "IFB0XCI9", "both", True, "Resourceful Config, wymagany przez Structure Layout Optimizer"),
    ("zfastnoise", "BWKn67tN", "both", True, "zFastNoise, szybsza generacja Netheru i Endu"),
    ("zconfig", "tsgt79sG", "both", True, "zConfig, wymagany przez zFastNoise"),
    ("alternate-current", "nSBWPz6x", "both", True, "Alternate Current, wydajniejszy redstone"),
    ("asynclogger", "Ert0LmWj", "both", True, "AsyncLogger, zapis logów w tle"),
    ("clumps", "J4I1wxJZ", "both", True, "Clumps, łączenie kul doświadczenia"),
    ("nvidium", "8mVK1zbk", "client", False, "Nvidium (opcjonalny, tylko karty NVIDIA), szybsze renderowanie dużego zasięgu"),
    ("distanthorizons", "gfi11b05", "client", False, "Distant Horizons (opcjonalny), daleki widok gór"),
]

API = "https://api.modrinth.com/v2"


def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "polskielasy-mrpack-builder/1.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def resolve(slug, version_id):
    if version_id:
        return get_json(f"{API}/version/{version_id}")
    versions = get_json(
        f'{API}/project/{slug}/version?game_versions=["{MINECRAFT}"]&loaders=["fabric"]'.replace('"', "%22")
        .replace("[", "%5B").replace("]", "%5D"))
    if not versions:
        raise SystemExit(f"Brak wersji {slug} dla {MINECRAFT}")
    return versions[0]


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    jars = [j for j in glob.glob(os.path.join(root, "build", "libs", "polskielasy-*.jar"))
            if not j.endswith("-sources.jar")]
    if not jars:
        raise SystemExit("Brak build/libs/polskielasy-*.jar. Najpierw uruchom ./gradlew build")
    jar = max(jars, key=os.path.getmtime)
    mod_version = os.path.basename(jar)[len("polskielasy-"):-len(".jar")]

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
        lines.append(f"| {desc} | {v['version_number']} | {side} | {'tak' if required else 'nie'} |")
        print(f"{slug:18s} {v['version_number']}")

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": mod_version,
        "name": PACK_NAME,
        "summary": "Proceduralne krajobrazy Polski w skali 1:1 z zalecanymi modami optymalizacyjnymi.",
        "files": files,
        "dependencies": {"minecraft": MINECRAFT, "fabric-loader": FABRIC_LOADER},
    }
    out_dir = os.path.join(root, "build", "distributions")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, f"Przyrodniczo-zgodne-lasy-{mod_version}.mrpack")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2, ensure_ascii=False))
        z.write(jar, f"overrides/mods/{os.path.basename(jar)}")
    sha = hashlib.sha1(open(out, "rb").read()).hexdigest()
    print(f"Zapisano {out} ({os.path.getsize(out) // 1024} KB, sha1 {sha})")

    table = os.path.join(out_dir, "zawartosc-paczki.md")
    with io.open(table, "w", encoding="utf-8") as t:
        t.write("| Mod | Wersja | Strona | Wymagany |\n|---|---|---|---|\n" + "\n".join(lines) + "\n")
    print(f"Zapisano {table}")


if __name__ == "__main__":
    sys.exit(main())
