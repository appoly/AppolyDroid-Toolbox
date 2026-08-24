#!/usr/bin/env bash
#
# AppolyDroid Toolbox publishing script.
#
# Publishes every module to Maven Central via the Vanniktech Maven Publish plugin.
#
# Unlike FlexiLogger — where a `flexipublish` function in a personal ~/.zshrc supplies the
# credentials and this script only consumes them — credential loading lives here, reading the
# SHARED Appoly vault. A wrapper in one person's shell profile is single-user by construction:
# anyone else with vault access still could not publish. There is nothing to install but the
# 1Password CLI.
#
#   ./scripts/publish.sh              publish to Maven Central, then tag
#   ./scripts/publish.sh --dry-run    every gate, no publish and no tag
#   ./scripts/publish.sh --local      signed install to ~/.m2, for testing a branch
#
set -euo pipefail

readonly VAULT_ITEM="op://Appoly Shared/Appoly Maven Central Signing"
readonly RELEASE_BRANCH="main"
readonly GROUP="uk.co.appoly.droid"

# Some machines have two 1Password accounts registered; omitting this fails with a misleading
# "no account found for filter".
export OP_ACCOUNT="${OP_ACCOUNT:-appoly.1password.com}"

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

Credentials are read from 1Password (Appoly Shared -> Appoly Maven Central
Signing). The 1Password desktop app must be unlocked, with CLI integration on.
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

# Only these five names are read by Gradle. Aliases like MAVEN_CENTRAL_USERNAME are what the CI
# workflow calls its secrets, but Gradle never reads them directly — accepting one here would pass
# this check and then fail obscurely at signing or upload.
read_field() {
    local value
    if ! value=$(op read "$VAULT_ITEM/$1" 2>/dev/null) || [[ -z "$value" ]]; then
        fail "Could not read '$1' from 1Password."
        fail "Is the desktop app unlocked, with CLI integration enabled?"
        exit 1
    fi
    printf '%s' "$value"
}

command -v op >/dev/null || { fail "1Password CLI not found. Install it and enable desktop-app integration."; exit 1; }

# Signing is needed in EVERY mode, dry run included: the metadata gate below runs
# publishToMavenLocal, and an unsigned publication fails with "no configured signatory". Only the
# upload token is release-only.
info "Reading signing credentials from 1Password..."
ORG_GRADLE_PROJECT_signingInMemoryKey=$(read_field private-key)
ORG_GRADLE_PROJECT_signingInMemoryKeyId=$(read_field key-id)
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=$(read_field passphrase)
export ORG_GRADLE_PROJECT_signingInMemoryKey ORG_GRADLE_PROJECT_signingInMemoryKeyId ORG_GRADLE_PROJECT_signingInMemoryKeyPassword

if [[ "$MODE" == "release" ]]; then
    info "Reading Maven Central token from 1Password..."
    ORG_GRADLE_PROJECT_mavenCentralUsername=$(read_field portal-username)
    ORG_GRADLE_PROJECT_mavenCentralPassword=$(read_field portal-token)
    export ORG_GRADLE_PROJECT_mavenCentralUsername ORG_GRADLE_PROJECT_mavenCentralPassword
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
./gradlew publishToMavenLocal
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
