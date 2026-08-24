#!/usr/bin/env bash
#
# Publish the toolbox to Maven Central from a developer machine.
#
# Deliberately lives in the repo rather than a personal shell profile: credentials come from the
# shared 1Password vault, so anyone with vault access can release. A wrapper in one person's
# ~/.zshrc is single-user by construction.
#
#   ./scripts/publish.sh --local     publish to ~/.m2 (signed, no upload credentials needed)
#   ./scripts/publish.sh             publish to Maven Central
#
set -euo pipefail

VAULT_ITEM="op://Appoly Shared/Appoly Maven Central Signing"
# Two 1Password accounts are registered on some machines; omitting this fails with a misleading
# "no account found for filter".
export OP_ACCOUNT="${OP_ACCOUNT:-appoly.1password.com}"

command -v op >/dev/null || { echo "error: 1Password CLI not found. Install it and enable desktop-app integration." >&2; exit 1; }

read_field() {
  op read "$VAULT_ITEM/$1" 2>/dev/null || {
    echo "error: could not read '$1' from the vault. Is the 1Password desktop app unlocked?" >&2
    exit 1
  }
}

echo "Reading signing credentials from 1Password..."
# Gradle only reads these under the ORG_GRADLE_PROJECT_ prefix, with exactly this camelCase.
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(read_field private-key)"
export ORG_GRADLE_PROJECT_signingInMemoryKeyId="$(read_field key-id)"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$(read_field passphrase)"

if [[ "${1:-}" == "--local" ]]; then
  echo "Publishing to mavenLocal (signed)..."
  exec ./gradlew publishToMavenLocal
fi

export ORG_GRADLE_PROJECT_mavenCentralUsername="$(read_field portal-username)"
export ORG_GRADLE_PROJECT_mavenCentralPassword="$(read_field portal-token)"

echo "Publishing to Maven Central..."
exec ./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
