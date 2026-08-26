#!/usr/bin/env bash
#
# AppolyDroid Toolbox publishing script.
#
# Publishes every module to Maven Central via the Vanniktech Maven Publish plugin.
#
# Unlike FlexiLogger — where a `flexipublish` function in a personal ~/.zshrc supplies the
# credentials and this script only consumes them — credential loading can live here, reading the
# SHARED Appoly vault. A wrapper in one person's shell profile is single-user by construction:
# anyone else with vault access still could not publish.
#
#   ./scripts/publish.sh              publish to Maven Central, then tag
#   ./scripts/publish.sh --dry-run    every gate, no publish and no tag
#   ./scripts/publish.sh --local      signed install to ~/.m2, for testing a branch
#
# CREDENTIALS — two supported paths, checked in this order:
#
#   1. Already in the environment. If the ORG_GRADLE_PROJECT_* variables below are exported,
#      they are used as-is and 1Password is never invoked. This is the path for anyone who
#      forked this repo, uses a different secret manager, or drives the script from CI.
#   2. 1Password. Otherwise the script reads them from the shared Appoly vault via the `op`
#      CLI, which is how Appoly releases.
#
# This repo is public, so the 1Password path must not be the only one — it is unusable by
# anyone outside Appoly, and a release script that hard-fails for every fork is not much of a
# release script.
#
# CONFIGURATION — override by exporting, or in an optional git-ignored scripts/publish.conf:
#
#   PUBLISH_GROUP            Maven group being published    (default: uk.co.appoly.droid)
#   PUBLISH_RELEASE_BRANCH   branch a real release expects  (default: main)
#   PUBLISH_VAULT_ITEM       1Password item, path 2 only    (no default — see publish.conf.example)
#   OP_ACCOUNT               1Password account, path 2 only (no default)
#
set -euo pipefail

# An optional, git-ignored file for fork-specific settings, so a fork needs no diff against
# this script to publish under its own coordinates.
CONF="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/publish.conf"
# shellcheck disable=SC1090
[[ -f "$CONF" ]] && source "$CONF"

# No default: the vault coordinates are deployment-specific and this repository is public.
# Set PUBLISH_VAULT_ITEM in scripts/publish.conf — see scripts/publish.conf.example.
readonly VAULT_ITEM="${PUBLISH_VAULT_ITEM:-}"
readonly RELEASE_BRANCH="${PUBLISH_RELEASE_BRANCH:-main}"
readonly GROUP="${PUBLISH_GROUP:-uk.co.appoly.droid}"

# Pin the daemon's memory for this script's Gradle invocations rather than inheriting whatever
# each developer keeps in ~/.gradle/gradle.properties — which takes precedence over the repo's
# own value, so the project cannot set this itself. Publishing runs Dokka across all 26 modules
# in one daemon, which needs well over the 1 GiB metaspace a typical personal config sets; the
# failure is a bare "Metaspace" on an arbitrary module, with the real cause named nowhere.
# A release must not succeed or fail depending on whose machine it runs on.
export GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=2048m -Dfile.encoding=UTF-8"


# Only exported when configured. Where more than one 1Password account is registered, omitting
# it fails with a misleading "no account found for filter", so publish.conf should set it.
[[ -n "${OP_ACCOUNT:-}" ]] && export OP_ACCOUNT

RED=$'\033[0;31m'; GREEN=$'\033[0;32m'; YELLOW=$'\033[1;33m'; BOLD=$'\033[1m'; NC=$'\033[0m'
info()  { echo "${GREEN}[INFO]${NC} $1"; }
warn()  { echo "${YELLOW}[WARN]${NC} $1"; }
fail()  { echo "${RED}[ERROR]${NC} $1" >&2; }

MODE="release"
for arg in "$@"; do
    case "$arg" in
        --dry-run|-n) MODE="dry-run" ;;
        --local|-l)   MODE="local" ;;
        -h|--help)
            cat <<'USAGE'
Usage: ./scripts/publish.sh [--local|--dry-run]

  (no flags)      Publish every module to Maven Central, then tag the commit.
  --local, -l     Publish signed artifacts to ~/.m2 instead. Needs no upload
                  credentials. Use this to test a branch in a consuming app.
  --dry-run, -n   Run every gate — clean, tests, coverage, keep rules, metadata
                  check — but do not publish or tag.

Credentials come either from the environment (export the five ORG_GRADLE_PROJECT_*
variables) or from 1Password. For the 1Password path, copy
scripts/publish.conf.example to scripts/publish.conf and set PUBLISH_VAULT_ITEM;
the desktop app must be unlocked with CLI integration on.
USAGE
            exit 0 ;;
        *) fail "Unknown argument: $arg"; echo "Try --help" >&2; exit 1 ;;
    esac
done

# ---------------------------------------------------------------- version ---

VERSION=$(sed -n 's/.*TOOLBOX_VERSION *= *"\([^"]*\)".*/\1/p' buildSrc/src/main/kotlin/BuildConfig.kt)
[[ -n "$VERSION" ]] || { fail "Could not read TOOLBOX_VERSION from buildSrc/src/main/kotlin/BuildConfig.kt"; exit 1; }
info "AppolyDroid Toolbox version: ${BOLD}$VERSION${NC}"
[[ "$MODE" != "release" ]] && warn "Mode: $MODE — nothing will be published to Maven Central."

# ------------------------------------------------------------ repo checks ---

# Only enforced for a real release. A local install is precisely how you test uncommitted work.
if [[ "$MODE" == "release" ]]; then
    if [[ -n $(git status --porcelain) ]]; then
        fail "Uncommitted changes. Commit or stash them before publishing."
        git status --short
        exit 1
    fi

    BRANCH=$(git branch --show-current)
    if [[ "$BRANCH" != "$RELEASE_BRANCH" ]]; then
        warn "You are on '$BRANCH', not '$RELEASE_BRANCH'."
        read -rp "Continue anyway? (y/N) " -n 1 reply; echo
        [[ $reply =~ ^[Yy]$ ]] || exit 1
    fi

    if git rev-parse -q --verify "refs/tags/$VERSION" >/dev/null; then
        fail "Tag $VERSION already exists, so this version was published before."
        fail "Maven Central releases are immutable — bump TOOLBOX_VERSION instead."
        exit 1
    fi
fi

# ------------------------------------------------------------ credentials ---

# Only these five names are read by Gradle. Aliases like MAVEN_CENTRAL_USERNAME are what a CI
# workflow might call its secrets, but Gradle never reads them directly — accepting one here would
# pass this check and then fail obscurely at signing or upload.
readonly SIGNING_VARS=(
    ORG_GRADLE_PROJECT_signingInMemoryKey
    ORG_GRADLE_PROJECT_signingInMemoryKeyId
    ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
)
readonly UPLOAD_VARS=(
    ORG_GRADLE_PROJECT_mavenCentralUsername
    ORG_GRADLE_PROJECT_mavenCentralPassword
)

# Signing is needed in EVERY mode, dry run included, even though the root build skips signing when
# no key is present (so that keyless PR CI can run publishToMavenLocal for the metadata gate). A dry
# run that skipped signing would stop exercising the one step a real release cannot survive failing,
# and --local exists precisely to test the signed artifacts. The upload token is not needed for a
# local install, but IS read for a dry run: a dry run exists to fail before the immutable step does,
# and a missing or misnamed credential is exactly what it should catch.
needed=("${SIGNING_VARS[@]}")
[[ "$MODE" != "local" ]] && needed+=("${UPLOAD_VARS[@]}")

missing=()
for var in "${needed[@]}"; do
    [[ -n "${!var:-}" ]] || missing+=("$var")
done

if [[ ${#missing[@]} -eq 0 ]]; then
    # Path 1 — supplied by the environment. Nothing to load, and no 1Password dependency.
    info "Using credentials already present in the environment."
    export "${needed[@]}"
else
    # Path 2 — 1Password. Refuse to load a partial set from the vault on top of a partial set from
    # the environment: mixing sources silently is how you sign a release with one identity and
    # upload it with another.
    if [[ ${#missing[@]} -ne ${#needed[@]} ]]; then
        fail "Some publishing credentials are set in the environment and some are not."
        fail "Set all of them, or none and let 1Password supply them. Missing: ${missing[*]}"
        exit 1
    fi

    if [[ -z "$VAULT_ITEM" ]]; then
        fail "No publishing credentials found, and no 1Password item is configured."
        fail ""
        fail "Either export these and re-run:"
        for var in "${needed[@]}"; do fail "  $var"; done
        fail ""
        fail "Or copy scripts/publish.conf.example to scripts/publish.conf and set"
        fail "PUBLISH_VAULT_ITEM to your own vault item."
        exit 1
    fi

    if ! command -v op >/dev/null; then
        fail "No publishing credentials found, and the 1Password CLI is not installed."
        fail ""
        fail "Either export these and re-run:"
        for var in "${needed[@]}"; do fail "  $var"; done
        fail ""
        fail "Or install the 1Password CLI and enable desktop-app integration. Forks will want"
        fail "the first option, and can set PUBLISH_GROUP in scripts/publish.conf to publish"
        fail "under their own coordinates."
        exit 1
    fi

    read_field() {
        local value
        if ! value=$(op read "$VAULT_ITEM/$1" 2>/dev/null) || [[ -z "$value" ]]; then
            fail "Could not read '$1' from $VAULT_ITEM."
            fail "Is the desktop app unlocked, with CLI integration enabled?"
            exit 1
        fi
        printf '%s' "$value"
    }

    info "Reading signing credentials from 1Password..."
    ORG_GRADLE_PROJECT_signingInMemoryKey=$(read_field private-key)
    ORG_GRADLE_PROJECT_signingInMemoryKeyId=$(read_field key-id)
    ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=$(read_field passphrase)
    export ORG_GRADLE_PROJECT_signingInMemoryKey ORG_GRADLE_PROJECT_signingInMemoryKeyId ORG_GRADLE_PROJECT_signingInMemoryKeyPassword

    if [[ "$MODE" != "local" ]]; then
        info "Reading Maven Central token from 1Password..."
        ORG_GRADLE_PROJECT_mavenCentralUsername=$(read_field portal-username)
        ORG_GRADLE_PROJECT_mavenCentralPassword=$(read_field portal-token)
        export ORG_GRADLE_PROJECT_mavenCentralUsername ORG_GRADLE_PROJECT_mavenCentralPassword
    fi
fi
# No unsetting needed: these live in this script's process, not the caller's shell.

# ------------------------------------------------------------------ local ---

if [[ "$MODE" == "local" ]]; then
    info "Publishing signed artifacts to ~/.m2 ..."
    ./gradlew publishToMavenLocal
    echo
    info "Installed $GROUP:* at $VERSION in ~/.m2"
    warn "Add mavenLocal() to the consuming project — and take it out again afterwards."
    warn "A local install carries the same version string as the real release, so leaving it in"
    warn "means resolving your own artifacts while believing you are testing the published one."
    exit 0
fi

# ------------------------------------------------------------------ gates ---

# Everything runs before the confirmation prompt, so a broken build never waits on it.
info "Cleaning..."
./gradlew clean

info "Running tests and the coverage gate..."
./gradlew test koverVerify || { fail "Tests or coverage gate failed. Fix before publishing."; exit 1; }

info "Verifying consumer R8 keep rules..."
./gradlew :app:verifyConsumerKeepRules || { fail "Consumer keep rules regressed."; exit 1; }

info "Verifying published metadata resolves for an Android consumer..."
./gradlew publishToMavenLocal || { fail "Publishing to ~/.m2 failed; the metadata gate cannot run."; exit 1; }
./gradlew -p publishing-check verifyPublishedVariantResolution --refresh-dependencies \
    || { fail "Published metadata would break an Android consumer."; exit 1; }

info "All gates passed."

if [[ "$MODE" == "dry-run" ]]; then
    echo
    info "================================================"
    info "  Dry run complete for $VERSION"
    info "================================================"
    info "Skipped: ./gradlew publishAndReleaseToMavenCentral"
    info "Skipped: tagging $VERSION"
    info "Re-run without --dry-run to publish for real."
    exit 0
fi

# ---------------------------------------------------------------- confirm ---

MODULES=$(sed -n 's/^include(":\(.*\)")$/\1/p' settings.gradle.kts | grep -v '^app$' | tr 'A-Z' 'a-z' | sort)
COUNT=$(echo "$MODULES" | wc -l | tr -d ' ')

echo
echo "================================================"
echo "  Publishing AppolyDroid Toolbox ${BOLD}$VERSION${NC} to Maven Central"
echo "================================================"
echo
echo "$COUNT modules under ${BOLD}$GROUP${NC}:"
echo "$MODULES" | awk '{printf "  %-34s", $0; if (NR % 2 == 0) print ""} END {if (NR % 2) print ""}'
echo
warn "Maven Central releases are IMMUTABLE. $VERSION can never be re-uploaded or corrected."
warn "If anything is wrong, the only remedy is publishing a new version."
echo
read -rp "Proceed with publish? (y/N) " -n 1 reply; echo
[[ $reply =~ ^[Yy]$ ]] || { info "Publish cancelled."; exit 0; }

# ---------------------------------------------------------------- publish ---

info "Publishing to Maven Central..."
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache

# Tag only after a successful upload, so a failed publish never leaves a tag claiming otherwise.
if git rev-parse -q --verify "refs/tags/$VERSION" >/dev/null; then
    warn "Tag $VERSION already exists — skipping tag creation."
else
    info "Tagging $VERSION and pushing..."
    git tag -a "$VERSION" -m "Release $VERSION"
    git push origin "$VERSION"
fi

echo
info "================================================"
info "  Published AppolyDroid Toolbox $VERSION"
info "================================================"
echo
info "Artifacts appear on Maven Central within ~15 minutes, and in search a little later."
info "Check: https://central.sonatype.com/namespace/$GROUP"
