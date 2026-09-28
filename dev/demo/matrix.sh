#!/usr/bin/env bash
# Check runs (no frames) of dev/demo/run.sh in a continuous pool (JOBS at a time, default 4; the machine-wide client
# slots of ModTest/slots.py cap what really runs; for the standard tiers use `python3 ModTest/mt.py HavingABlast full`): each line of the spec is "<ver> <loader> [extra]".
# extra "profile" adds the user's ModrinthApp profile mods of that version (Sodium, Iris, EntityCulling, Mob Reactions...,
# listed in PROFILE_MODS), "vanilla" runs with the mod's visuals off.
# Usage: matrix.sh <out-dir> [scenes] < spec      Prints one summary line per run; exit 1 if any failed.
set -uo pipefail
OUT=${1:?usage: matrix.sh <out-dir> [scenes] < spec}
SCENES=${2:-tnt1,tnt10,tnt100,creeper,sources,underwater,cave,house_tnt,repair}
HERE=$(cd "$(dirname "$0")" && pwd)
PROFILES=$HOME/.local/share/ModrinthApp/profiles
mkdir -p "$OUT"
one() {
    local ver=$1 loader=$2 extra=${3:-} name="$1-$2${3:+-$3}" mods="" vanilla=""
    if [ "$extra" = profile ]; then
        local prof="$PROFILES/Fabric $ver"
        mods=$(ls "$prof"/mods/*.jar 2>/dev/null | grep -iE "${PROFILE_MODS:-sodium|iris|entityculling|mobreactions}" | grep -v havingablast | tr '\n' ':')
    fi
    [ "$extra" = vanilla ] && vanilla=1
    FRAMES=0 SKIP_BUILD=1 LOADER=$loader MODS="$mods" VANILLA=$vanilla WORK_TAG="m${extra}" \
        bash "$HERE/run.sh" "$ver" "$OUT/$name" "$SCENES" < /dev/null > "$OUT/$name.txt" 2>&1
    echo "$name: $(grep -E ' passed, ' "$OUT/$name.txt" | sed 's/^ *//')$(grep -qE 'FAIL' "$OUT/$name.txt" && echo ' [FAIL]')"
}
while read -r ver loader extra; do
    [ -z "${ver:-}" ] && continue
    while [ "$(jobs -rp | wc -l)" -ge "${JOBS:-4}" ]; do wait -n; done
    one "$ver" "$loader" "${extra:-}" &
done
wait
FAIL=0
grep -l FAIL "$OUT"/*.txt 2>/dev/null && FAIL=1
exit $FAIL
