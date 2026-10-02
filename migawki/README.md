# Migawki kodu

Projekt nie jest repozytorium git, więc stan kodu potrzebny do pomiarów porównawczych trzymamy tutaj. Plików nie zmieniać i nie usuwać do końca M2.

## `m1-z-narzedziami-S0.tar`

- SHA-256: `6c5a2ed48ea79ae8f6201aac48b9ed1b9c7d8daa24eccfe95860cb8f5ddf6fa5`
- Data: 2026-10-02, po poprawkach kroku S0 planu M2, przed S1.
- Zawartość: `src/` (wszystkie zbiory źródeł), `build.gradle`, `gradle.properties`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/`, `.gitattributes`, `.gitignore`. Bez `docs/`, `build/`, `.gradle/` i `run/`.
- `src/main` i `src/client` są identyczne z kodem końca M1 (sprawdzone `diff -rq` z migawką sprzed S0). `src/test` i `src/gametest` zawierają narzędzia pomiarowe S0: `GoldenTerrainTest`, `SampleKosztTest`, zamrożoną kopię modelu `landscape.m1` i `PerformanceClientGameTest` z trybem `etapy` (liczniki jako przyrosty, trzy obszary na skalę).

### Pomiar bazowy etapów chunka na kodzie M1

Jeśli `src/main` w projekcie już się zmienił (od S2 model jest inny), pomiar bazowy robi się z migawki, w tej samej sesji co pomiar bieżącego kodu:

```
mkdir "../pl-m1-pomiar"
tar xf migawki/m1-z-narzedziami-S0.tar -C "../pl-m1-pomiar" --force-local
cd "../pl-m1-pomiar"
./gradlew runClientGameTest -Pgametest=etapy
```

W nowym katalogu Loom przygotuje Minecrafta od nowa (pierwsze uruchomienie trwa dłużej). Wyniki są w logu, w wierszach `[wydajnosc]`. Potem ten sam tryb w projekcie, na bieżącym kodzie. Dopóki `src/main` jest równe M1 (przed S2), wystarczy uruchomić tryb `etapy` w samym projekcie.
