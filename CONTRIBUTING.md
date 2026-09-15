# Contributing

Maintainer and contributor notes. The [README](README.md) covers *using* the toolbox; this file
covers building, testing and releasing it.

## Testing an unreleased change

Maven Central publishes only what is released, so there is no equivalent of JitPack's
build-any-branch behaviour — and releases are immutable, so a mistake cannot be corrected in
place. Test locally first. Two options replace JitPack.

### Install locally (the normal loop)

From a checkout of the branch you want to test:

```bash
./scripts/publish-local.sh                    # every module, unsigned — no credentials needed
./scripts/publish-local.sh BaseRepo UiState   # only those modules, for a tight iteration loop
./scripts/publish-local.sh --signed           # every module, signed (= ./scripts/publish.sh --local)
```

Undo it with:

```bash
./scripts/clear-local-publish.sh              # every locally installed version
./scripts/clear-local-publish.sh 1.9.1-local1 # just that version
./scripts/clear-local-publish.sh --dry-run    # list what would go, delete nothing
```

Both are also Android Studio run configurations, checked in under `.run/` and shared through
version control: **Publish to Maven Local**, **Publish to Maven Local (signed)** and **Clear Local
Maven Publish**. They run in the Run window's terminal, so the clear script's confirmation prompt
works there. To publish a subset from the IDE, edit the run configuration's *Script options* field —
or just use the terminal.

`clear-local-publish.sh` only ever touches `~/.m2/repository/uk/co/appoly/droid` (or `PUBLISH_GROUP`
from `scripts/publish.conf`, for a fork). Nothing else in `~/.m2` is read or written.

**Signed or not?** Unsigned is the default because signing needs the release key out of 1Password,
and Gradle does not verify signatures on resolve — an unsigned local install behaves identically to
a signed one for every purpose this loop has. Use `--signed` only when the thing under test *is* the
signing, or the exact artifact set a release would upload. That path is `publish.sh --local`, which
`--signed` simply delegates to.

### Consuming a local install from another project

Add `mavenLocal()` **first** in the consuming project's repository list, so it wins over Central:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

Then depend on the toolbox exactly as usual — the BOM works unchanged, since it is installed
locally alongside everything else:

```kotlin
// <version> is whatever TOOLBOX_VERSION you just installed — this file is not version-synced,
// so read it out of buildSrc/src/main/kotlin/BuildConfig.kt rather than trusting a number here.
implementation(platform("uk.co.appoly.droid:bom:<version>"))
implementation("uk.co.appoly.droid:baserepo")
implementation("uk.co.appoly.droid:uistate")
```

Sync with `--refresh-dependencies` the first time:

```bash
./gradlew --refresh-dependencies :app:assembleStagingDebug
```

Without it Gradle may serve a cached module for that version string — resolved earlier from Central
— and never look in `~/.m2` at all. The same applies in reverse *after* clearing: a consumer that
already resolved the local copy keeps serving it until refreshed.

A narrower alternative, if you would rather `mavenLocal()` could not possibly shadow anything else,
is to scope it to the toolbox group:

```kotlin
exclusiveContent {
    forRepository { mavenLocal() }
    filter { includeGroup("uk.co.appoly.droid") }
}
```

> **Take `mavenLocal()` back out when you are done**, and run `clear-local-publish.sh`. A local
> install carries the same version string as the real release, so leaving either in place means
> resolving your own working tree while believing you are testing the published artifacts. A partial
> install (`publish-local.sh BaseRepo`) is worse still: the other modules in `~/.m2` are whatever was
> installed last, possibly a different build of the same version.

The cleanest way to remove the ambiguity entirely is to bump `TOOLBOX_VERSION` in
`buildSrc/src/main/kotlin/BuildConfig.kt` to something that does not and will not exist on Central —
`1.9.1-local1` — and depend on that from the consuming project. Then there is no version string in
play that could mean two different things, and the dependency-cache problem disappears with it.
Revert the bump before committing.

### Publish a snapshot

Snapshot versions go to Central's snapshot repository rather than the main one, and need it adding
explicitly:

```kotlin
maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
```

Snapshots are changing modules, so pass `--refresh-dependencies` or Gradle will cache one for 24
hours and you may silently keep testing a stale copy.

## Releasing

Releases are run **manually and locally** by a developer. There is no release CI job and no Maven
Central credentials in the repository, so pushing a version tag publishes nothing on its own.

```bash
./scripts/publish.sh --dry-run   # every gate; publishes and tags nothing
./scripts/publish.sh             # gates, then upload, then tag
```

The script cleans, runs the tests and coverage gate, checks the consumer R8 keep rules, publishes
to `~/.m2` and verifies that the published metadata resolves for an Android consumer — then asks
for confirmation before uploading. It tags only *after* a successful upload, so a failed publish
never leaves a tag claiming otherwise.

Bump `TOOLBOX_VERSION` in `buildSrc/src/main/kotlin/BuildConfig.kt` first. Every module shares that
one version; see [Why one version for all modules](#why-one-version-for-all-modules).

> **Releases are immutable.** A version can never be re-uploaded or corrected — the only remedy is
> publishing a new one. Iterate with [`publish-local.sh`](#install-locally-the-normal-loop) *before*
> releasing, never after.

### Central publishing limits — batch releases, do not split modules

Maven Central enforces three per-calendar-month quotas per organisation, from 1 October 2026. Our
applied limits, confirmed by Sonatype on 2026-09-09, are **1,000 files, 80 MB and 7 releases**.
Track usage in the [Usage Center](https://central.sonatype.com/publishing/usage).

One toolbox release is **508 files, 11.55 MB, and one release event** — Central scores a multi-module
deployment bundle as a single release, not one per artifact. So release count is a non-issue and size
is nowhere near. **File count is the binding constraint:** 508 files is roughly half the monthly
allowance, so a second release in the same calendar month lands at ~1,016 and a third cannot fit.

Two consequences for release practice:

- **Batch patch releases.** A flurry of same-month point releases — the 1.8.0 → 1.8.3 pattern of
  August 2026 — would be ~2,540 files, over twice the allowance. Fold fixes into one version and
  iterate through a local install or a snapshot in the meantime.
- **Do not split modules to reduce usage; it does the opposite.** 26 separately-published
  repositories would be 26 release events per version, past the limit of 7 on day one. The single
  batched deployment is the cheapest possible shape under these rules — a further reason for the
  caveat in [Why one version for all modules](#why-one-version-for-all-modules).

Sonatype granted `uk.co.appoly.droid` an **OSS exemption** on 2026-09-09, so Central's
*commercial nature* classification — which is independent of publishing volume and would otherwise
require Publisher Pro — does not apply to us. The same response declined to raise the file-count
ceiling in substance: the "enhanced" limits it granted match what was already applied, sized to a
publishing history of a single release. If the one-release-per-month cap starts to hurt, that is the
thing to go back to `central-support@sonatype.com` about, with a concrete cadence to justify it.

### Credentials

The script resolves credentials in two ways, in this order:

1. **Already in the environment.** If all of the variables below are exported, they are used as-is
   and 1Password is never invoked. This is the path for forks, other secret managers, and CI.
2. **1Password.** Otherwise they are read from a vault item via the `op` CLI. Which item is
   deployment-specific and deliberately not in version control, since this repository is public.

**Appoly maintainers, one-time setup.** The config lives in the same 1Password item as the
credentials — search the shared vault for *Maven Central* and copy its `publish-conf` field into
`scripts/publish.conf`. It is kept there rather than in a wiki because anyone who can release
already has that item: the audience for the config is exactly the set of people who can read it,
and it cannot drift out of sync with what it points at.

```bash
op read "op://<vault>/<item>/publish-conf" > scripts/publish.conf
```

**Everyone else:** copy `scripts/publish.conf.example` to `scripts/publish.conf` and set
`PUBLISH_VAULT_ITEM` to your own item — or skip 1Password entirely and export the five variables.

Gradle reads these only under the `ORG_GRADLE_PROJECT_` prefix, with exact camelCase:

| Variable | 1Password field |
|---|---|
| `ORG_GRADLE_PROJECT_signingInMemoryKey` | `private-key` |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyId` | `key-id` |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` | `passphrase` |
| `ORG_GRADLE_PROJECT_mavenCentralUsername` | `portal-username` |
| `ORG_GRADLE_PROJECT_mavenCentralPassword` | `portal-token` |

Setting *some* of them is rejected rather than topped up from the vault — mixing sources silently is
how a release gets signed with one identity and uploaded with another.

A 1Password field label containing a space or parenthesis cannot be read through an `op://`
reference at all, so these labels use hyphens.

## Forking

Nothing here is Appoly-specific except the defaults. Create `scripts/publish.conf` (git-ignored) to
publish under your own coordinates:

```bash
cp scripts/publish.conf.example scripts/publish.conf
```

```bash
PUBLISH_GROUP="com.example.toolbox"
PUBLISH_RELEASE_BRANCH="main"
```

Then export the five `ORG_GRADLE_PROJECT_*` variables from wherever you keep secrets and run
`./scripts/publish.sh`. The 1Password path is Appoly's convenience, not a requirement — you do not
need the `op` CLI installed.

You will also want to change the POM metadata (`url`, `licenses`, `developers`, `scm`) in the root
`build.gradle.kts`, and the `coordinates(...)` group alongside it. Maven Central rejects an
incomplete POM, and a missing `developers` block is a hard rejection.

## Build and verification

```bash
./gradlew test koverVerify              # unit tests + coverage floor
./gradlew :app:verifyConsumerKeepRules  # R8 keep rules survive minification
./gradlew publishToMavenLocal
./gradlew -p publishing-check verifyPublishedVariantResolution --refresh-dependencies
```

The last one resolves the published modules as an Android consumer would and fails on
platform-variant duplicates (an `okhttp-jvm` beside an `okhttp-android`). It reads `mavenLocal`
only, so publish locally first. All four run in CI on every pull request.

`publish.sh` pins `GRADLE_OPTS` for its own Gradle invocations. Publishing drives Dokka across all
26 modules in a single daemon, which needs more metaspace than a typical personal
`~/.gradle/gradle.properties` allows — and user-level properties take precedence over the repo's, so
the project cannot set this itself. Unpinned, a release fails with a bare `Metaspace` error on an
arbitrary module.

## Why one version for all modules

Every module is published at `TOOLBOX_VERSION`, including modules whose code did not change.
Publishing only what changed is technically possible, but 14 of the 25 modules depend on a sibling
and those dependencies are published as hard version pins. Shipping `baserepo` alone would leave
consumers either silently not getting the fix (no BOM) or running `baserepo-paging` against a
`baserepo` it was never compiled with (BOM forcing the newer version). CI only ever builds one
coherent version set, so it cannot catch either.

Republishing everything costs minutes of upload and no consumer risk. If a module ever genuinely
earns its own release cadence, split it into its own repository rather than versioning it
independently here — but weigh it against
[Central publishing limits](#central-publishing-limits--batch-releases-do-not-split-modules) first,
since each extra repository is another monthly release event.

## Documentation

- **README.md** and each module's README are consumer-facing. Version numbers in them are synced
  automatically by the `UpdateReadmeVersions` Gradle task during sync — do not hand-edit versions.
- **This file** covers everything maintainer-facing.

The JitPack → Central migration is finished; its history is in the
[1.9.0 release notes](https://github.com/appoly/AppolyDroid-Toolbox/releases/tag/1.9.0) and in the
commits around it, rather than in a plan document that would only go stale.
