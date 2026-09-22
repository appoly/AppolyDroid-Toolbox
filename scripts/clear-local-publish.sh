#!/usr/bin/env bash
#
# AppolyDroid Toolbox — remove a local install.
#
# Deletes the toolbox from the local Maven repository (~/.m2/repository), undoing
# scripts/publish-local.sh or scripts/publish.sh --local.
#
#   ./scripts/clear-local-publish.sh               remove every locally installed version
#   ./scripts/clear-local-publish.sh 1.9.1-rc01    remove just that version
#   ./scripts/clear-local-publish.sh --dry-run     list what would go, delete nothing
#   ./scripts/clear-local-publish.sh --yes         skip the confirmation prompt
#
# WHY THIS MATTERS. A local install carries the same version string as the real release, and
# mavenLocal() wins over mavenCentral(). Left in place it silently shadows the published artifacts:
# the consuming project resolves your working tree while the version number says otherwise. Clearing
# it is how you get back to testing what consumers actually receive.
#
# Only ever touches the toolbox's own group directory — nothing else in ~/.m2 is read or written.
#
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Shares fork-specific settings (PUBLISH_GROUP) with publish.sh. Git-ignored; see publish.conf.example.
# shellcheck disable=SC1091
[[ -f scripts/publish.conf ]] && source scripts/publish.conf

readonly GROUP="${PUBLISH_GROUP:-uk.co.appoly.droid}"
readonly M2_REPO="${M2_REPO:-$HOME/.m2/repository}"
readonly GROUP_DIR="$M2_REPO/${GROUP//.//}"

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[1;33m'; BOLD=$'\033[1m'; NC=$'\033[0m'
info()  { echo "${GREEN}[INFO]${NC} $1"; }
warn()  { echo "${YELLOW}[WARN]${NC} $1"; }
fail()  { echo "${RED}[ERROR]${NC} $1" >&2; }

DRY_RUN=false
ASSUME_YES=false
VERSION=""
for arg in "$@"; do
    case "$arg" in
        --dry-run|-n) DRY_RUN=true ;;
        --yes|-y)     ASSUME_YES=true ;;
        -h|--help)
            cat <<'USAGE'
Usage: ./scripts/clear-local-publish.sh [--dry-run] [--yes] [VERSION]

  (no args)       Remove every locally installed toolbox version from ~/.m2.
  VERSION         Remove only that version (e.g. 1.9.1-rc01).
  --dry-run, -n   List what would be removed, delete nothing.
  --yes, -y       Do not ask for confirmation.

Only the toolbox's own group directory is touched. Install again with
./scripts/publish-local.sh.
USAGE
            exit 0 ;;
        -*) fail "Unknown option: $arg"; echo "Try --help" >&2; exit 1 ;;
        *)
            [[ -z "$VERSION" ]] || { fail "Only one version can be given (got '$VERSION' and '$arg')."; exit 1; }
            VERSION="$arg" ;;
    esac
done

if [[ ! -d "$GROUP_DIR" ]]; then
    info "Nothing to clear — $GROUP is not installed in $M2_REPO."
    exit 0
fi

# Collect the artifact/version directories to delete, so the prompt shows exactly what goes.
TARGETS=()
while IFS= read -r dir; do
    TARGETS+=("$dir")
done < <(
    if [[ -n "$VERSION" ]]; then
        find "$GROUP_DIR" -mindepth 2 -maxdepth 2 -type d -name "$VERSION" | sort
    else
        find "$GROUP_DIR" -mindepth 1 -maxdepth 1 -type d | sort
    fi
)

if [[ ${#TARGETS[@]} -eq 0 ]]; then
    if [[ -n "$VERSION" ]]; then
        info "Nothing to clear — no module of $GROUP is installed at version $VERSION."
    else
        info "Nothing to clear — $GROUP_DIR holds no module directories."
    fi
    exit 0
fi

SIZE=$(du -sh "$GROUP_DIR" 2>/dev/null | cut -f1 | tr -d "[:space:]" || echo "?")

echo
echo "================================================"
if [[ -n "$VERSION" ]]; then
    echo "  Clearing ${BOLD}$GROUP${NC} ${BOLD}$VERSION${NC} from ~/.m2"
else
    echo "  Clearing ${BOLD}$GROUP${NC} (all versions) from ~/.m2"
fi
echo "================================================"
echo
echo "${#TARGETS[@]} director$([[ ${#TARGETS[@]} -eq 1 ]] && echo "y" || echo "ies") under $GROUP_DIR:"
for target in "${TARGETS[@]}"; do
    echo "  ${target#"$GROUP_DIR"/}"
done
echo
info "Group directory currently uses $SIZE on disk."

if [[ "$DRY_RUN" == true ]]; then
    echo
    info "Dry run — nothing was deleted."
    exit 0
fi

if [[ "$ASSUME_YES" != true ]]; then
    echo
    read -rp "Delete these? (y/N) " -n 1 reply; echo
    [[ $reply =~ ^[Yy]$ ]] || { info "Cancelled — nothing was deleted."; exit 0; }
fi

for target in "${TARGETS[@]}"; do
    rm -rf "$target"
done

# With a version filter the artifact directories survive, holding other versions — or nothing, if
# that was the only one installed. Prune the husks so a later run reports honestly instead of
# listing empty directories.
find "$GROUP_DIR" -mindepth 1 -type d -empty -delete
rmdir "$GROUP_DIR" 2>/dev/null || true

echo
info "Cleared. The consuming project now resolves $GROUP from its remote repositories again."
warn "Gradle caches resolved modules per project. If a consumer already resolved the local copy,"
warn "it needs --refresh-dependencies to notice, or it keeps serving the build you just deleted."
