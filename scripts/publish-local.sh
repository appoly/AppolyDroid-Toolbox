#!/usr/bin/env bash
#
# AppolyDroid Toolbox — local install.
#
# Publishes the toolbox to the local Maven repository (~/.m2/repository) so a consuming app can
# resolve the current working tree through mavenLocal(). This is the loop for testing a change
# BEFORE releasing it — Maven Central releases are immutable, so there is no fixing one afterwards.
#
#   ./scripts/publish-local.sh                    every module, unsigned
#   ./scripts/publish-local.sh BaseRepo UiState   only those modules (and their dependencies)
#   ./scripts/publish-local.sh --signed           every module, signed — same as publish.sh --local
#
# WHY UNSIGNED BY DEFAULT. Signing needs the release key out of 1Password, which makes the quick
# iteration loop wait on a vault unlock for a signature nothing local ever verifies. Gradle does not
# check signatures on resolve, so an unsigned local install behaves identically to a signed one for
# every purpose this script exists for. Use --signed when the thing being tested IS the signing or
# the exact artifact set a release would upload; that path is scripts/publish.sh --local.
#
# Undo with ./scripts/clear-local-publish.sh — see CONTRIBUTING.md, "Testing an unreleased change".
#
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Shares fork-specific settings (PUBLISH_GROUP) with publish.sh. Git-ignored; see publish.conf.example.
# shellcheck disable=SC1091
[[ -f scripts/publish.conf ]] && source scripts/publish.conf

readonly GROUP="${PUBLISH_GROUP:-uk.co.appoly.droid}"

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[1;33m'; BOLD=$'\033[1m'; NC=$'\033[0m'
info()  { echo "${GREEN}[INFO]${NC} $1"; }
warn()  { echo "${YELLOW}[WARN]${NC} $1"; }
fail()  { echo "${RED}[ERROR]${NC} $1" >&2; }

MODULES=()
for arg in "$@"; do
    case "$arg" in
        --signed|-s)
            info "Delegating to scripts/publish.sh --local for the signed install."
            exec ./scripts/publish.sh --local
            ;;
        -h|--help)
            cat <<'USAGE'
Usage: ./scripts/publish-local.sh [--signed] [Module ...]

  (no args)       Publish every module to ~/.m2, unsigned. Needs no credentials.
  Module ...      Publish only the named modules, by Gradle project name
                  (e.g. BaseRepo UiState). Faster when iterating on one module.
  --signed, -s    Publish every module signed, via scripts/publish.sh --local.
                  Needs the release signing key.

Remove a local install again with ./scripts/clear-local-publish.sh.
USAGE
            exit 0 ;;
        -*) fail "Unknown option: $arg"; echo "Try --help" >&2; exit 1 ;;
        *)  MODULES+=("${arg#:}") ;;
    esac
done

VERSION=$(sed -n 's/.*TOOLBOX_VERSION *= *"\([^"]*\)".*/\1/p' buildSrc/src/main/kotlin/BuildConfig.kt)
[[ -n "$VERSION" ]] || { fail "Could not read TOOLBOX_VERSION from buildSrc/src/main/kotlin/BuildConfig.kt"; exit 1; }

# Publishing drives Dokka across every module in one daemon, which needs more metaspace than a
# typical personal ~/.gradle/gradle.properties allows — and user-level properties beat the repo's,
# so the project cannot set this itself. Unpinned, this fails with a bare "Metaspace" error on an
# arbitrary module. Same reasoning as publish.sh.
export GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=2048m -Dfile.encoding=UTF-8"

if [[ ${#MODULES[@]} -eq 0 ]]; then
    info "Publishing all modules of ${BOLD}$GROUP${NC} at ${BOLD}$VERSION${NC} to ~/.m2 (unsigned)..."
    TASKS=(publishToMavenLocal)
else
    # A partial install leaves ~/.m2 holding a mix of versions across modules. That is fine while
    # iterating on one module, and wrong the moment you draw a conclusion about the set — hence
    # the warning below and the whole-set default.
    info "Publishing ${BOLD}${MODULES[*]}${NC} at ${BOLD}$VERSION${NC} to ~/.m2 (unsigned)..."
    TASKS=()
    for module in "${MODULES[@]}"; do
        TASKS+=(":${module}:publishToMavenLocal")
    done
fi

./gradlew "${TASKS[@]}"

echo
info "================================================"
info "  Installed to ~/.m2 — $GROUP at $VERSION"
info "================================================"
echo
if [[ ${#MODULES[@]} -gt 0 ]]; then
    warn "Partial install — only these were rebuilt: ${MODULES[*]}. Every other module in ~/.m2"
    warn "is whatever was installed last, which may be a different build of the same version."
fi
cat <<CONSUME
In the consuming project, add mavenLocal() FIRST in settings.gradle.kts:

    dependencyResolutionManagement {
        repositories {
            mavenLocal()
            google()
            mavenCentral()
        }
    }

Then resolve with --refresh-dependencies the first time, or Gradle may serve a cached
copy of the same version string from Central.

CONSUME
warn "A local install carries the same version string as the real release. Take mavenLocal()"
warn "out again afterwards, and run ./scripts/clear-local-publish.sh — otherwise you resolve"
warn "your own artifacts while believing you are testing the published ones."
