#!/usr/bin/env bash
# Dev-only: film and check Having a Blast in the real client, off-screen. A thin wrapper over the shared launcher
# ModTest/client.py (headless KWin, every loader, machine-wide client slots, mixin audit); for test tiers use
# `python3 ModTest/mt.py HavingABlast quick|full|release` instead. The mod's demo director (demo/Director, active with
# -Dhavingablast.demo) plays every scene in the flat world .work/world-<ver>, saves each scene's frames to
# <out>/<scene>/ and the checks to <out>/results.json. Sound plays at -80 dB (MASTER_VOLUME, default 0.0001), so the
# sound engine really runs.
# Usage: run.sh <mc-version> <out-dir> [scenes, comma separated]
# Env: LOADER=fabric|neoforge|forge|vanilla (default fabric; vanilla: the plain client, no mod, for interop; Forge and NeoForge come from the installs Tidy Pockets' self-test
#      made, TidyPockets/dev/selftest/install_loaders.sh), MODS="a.jar:b.jar" adds mods, PACKS="x.zip:y.zip" adds
#      resource packs and enables them, FRAMES=0 (checks only, no screenshots), CAM=side|hero|fp|near (camera),
#      FPS=<cap> (default 120; 0 = uncapped), WIDTH/HEIGHT (default 960x540), VANILLA=1 (the mod's visuals off, for
#      vanilla-vs-mod captures), FX=<name> (-Dhavingablast.fx, a look variant under review), WORK_TAG=x (own game
#      dir, so runs of one loader can go in parallel), SKIP_BUILD=1, SERVER=host:port (join a server; dev/demo/interop.py),
#      TIMEOUT=<seconds> (default 600; a vanilla client never quits by itself), PROFILE=1 (the user's profile mods;
#      EXCLUDE=regex drops some), SHADERS=1 (its shader pack on), MP_SECONDS, PROBE_POSITIONS=1, TICKRATE=<tps>.
# Exit code 1 when a check fails, the client never wrote results, or the log shows a mixin error.
set -euo pipefail
VER=${1:?usage: run.sh <mc-version> <out-dir> [scenes]}
OUT=${2:?out dir}
SCENES=${3:-tnt1}
LOADER=${LOADER:-fabric}
SHOTS=true
[ "${FRAMES:-1}" = 0 ] && SHOTS=false
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
TARGET="$VER-$LOADER"
GAME="$HERE/.work/game-$TARGET${WORK_TAG:+-$WORK_TAG}"

[ -n "${SKIP_BUILD:-}" ] || [ "$LOADER" = vanilla ] || (cd "$ROOT" && ./gradlew --console=plain -q ":$TARGET:build" -x test)
ARGS=()
[ "$LOADER" = vanilla ] || ARGS+=(--jar "$(ls "$ROOT"/versions/"$TARGET"/build/libs/havingablast-*+"$TARGET".jar | head -1)")
IFS=: read -r -a M <<< "${MODS:-}"
for m in "${M[@]}"; do [ -n "$m" ] && ARGS+=(--mod "$m"); done
IFS=: read -r -a P <<< "${PACKS:-}"
for p in "${P[@]}"; do [ -n "$p" ] && ARGS+=(--pack "$p"); done
# PROFILE=1: every mod of the user's ModrinthApp profile "Fabric <ver>" (Sodium, Iris, EntityCulling, Mob Reactions,
# Explosive Enhancement, Fabric API...), except an installed Having a Blast (this build is the one under test)
PROF="$HOME/.local/share/ModrinthApp/profiles/Fabric $VER"
if [ -n "${PROFILE:-}" ]; then
    for m in "$PROF"/mods/*.jar; do
        case "$(basename "$m")" in havingablast*) continue ;; esac
        [ -n "${EXCLUDE:-}" ] && basename "$m" | grep -qiE "$EXCLUDE" && continue
        ARGS+=(--mod "$m")
    done
fi
[ -n "${SHADERS:-}" ] && ARGS+=(--shaders "$(ls "$PROF"/shaderpacks/*.zip | head -1)")
if [ -n "${SERVER:-}" ]; then ARGS+=(--join "$SERVER"); else
    WORLD="$HERE/.work/world-$VER"
    [ -d "$WORLD" ] || WORLD="$HERE/.work/world"
    ARGS+=(--world "$WORLD")
fi
MAXFPS=${FPS:-120}
[ "$MAXFPS" = 0 ] && MAXFPS=260
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
set +e
python3 "$ROOT/../ModTest/client.py" "$VER" "$LOADER" "$OUT" --game "$GAME" "${ARGS[@]}" \
    --user BlastCam --width "${WIDTH:-960}" --height "${HEIGHT:-540}" --timeout "${TIMEOUT:-600}" \
    --opt "fps=$MAXFPS" --opt "volume=${MASTER_VOLUME:-0.0001}" --opt render_distance=6 \
    --log-errors 'ERROR\]: havingablast\.mixins\.json|Having a Blast: .*(failed|cannot)' \
    -D "havingablast.demo=$OUT" -D "havingablast.demo.scenes=$SCENES" -D "havingablast.demo.frames=$SHOTS" \
    -D "havingablast.demo.cam=${CAM:-side}" -D "havingablast.vanilla=${VANILLA:-}" -D "havingablast.fx=${FX:-}" \
    -D "havingablast.demo.mp=${MP_SECONDS:-90}" ${PROBE_POSITIONS:+-D havingablast.probe.positions=1} \
    -D modtest.audit=1 -D "modtest.tickrate=${TICKRATE:-}" --label "HavingABlast $TARGET"
STATUS=$?
set -e
if [ "$SHOTS" = true ]; then
    for d in "$OUT"/*/; do [ -d "$d" ] && echo "  $(basename "$d"): $(ls "$d" | grep -c png) frames"; done
fi
exit $STATUS
