# Leaving JitPack

Migration of AppolyDroid Toolbox publishing from JitPack to Maven Central.

**Status as of 26 Aug 2026** — 1.9.0 released to Maven Central. Phase 5 (consumers) remains.
Current release: 1.8.3 on JitPack. 26 published modules.

Two attempts to fix AAR source navigation were defeated by the same JitPack rewrite.
Publishing to Maven Central removes the rewriter rather than working around it.

---

## Where this stands

| Item | Then (24 Aug) | Now |
|------|---------------|-----|
| Namespace `uk.co.appoly` | TXT record absent from authoritative NS | **Verified** on the Central portal |
| Publishing configuration | 26 hand-written blocks, JitPack workarounds | **Landed** — `e48652e` |
| Release path | Planned as a tag-triggered CI job | **Superseded** — manual local run |
| 1.9.0 on Central | Not started | **Released** — 26 coordinates live, sources verified |

1.9.0 is released and serving. All that remains is moving the four in-house consumers.

---

## Why JitPack can't be fixed from our side

JitPack strips the `-sources` classifier from the file entry inside a sources-typed variant
in the published `.module`. Both available shapes were tried against real JitPack builds:

| Version | Shape | Result for AAR modules | |
|---------|-------|------------------------|---|
| 1.8.1 | Sources variant published | Variant's file entry stripped; Gradle requests a name that 404s and gives up silently | broken |
| 1.8.2 | No module metadata at all | Sources work — but POMs pin platform artifacts, so consumers get duplicate classes and cannot build | worse |
| 1.8.3 | Metadata restored, sources as POM classifier only | Builds fixed. Android Studio binds AAR sources from the variant, so with no variant it decompiles 25 of 26 modules | broken |
| 1.8.4-alpha01 | Sources variant restored for AARs | Stripped again. Android Studio requests ~25 non-existent files per sync, each timing out after 30s against a shared rate limit | worse |

The two reachable states on JitPack are "no AAR source navigation" and "no AAR source
navigation, plus 25 doomed requests every sync". 1.8.3 is the better of those, and it is
where the library sits today.

## What Central changes

| | JitPack | Maven Central |
|---|---------|---------------|
| Metadata fidelity | Rewritten on serve | Byte-for-byte as uploaded |
| AAR source navigation | Not achievable | Works via the standard sources variant |
| Rate limits | Shared per-IP; has blocked us repeatedly | CDN-backed, no practical limit |
| First resolve | Builds on demand; minutes of latency | Already built and hosted |
| Release mutability | Retag and rebuild freely | **Immutable once released** |
| Credentials | None | Portal token + GPG signing key |

Immutability cuts both ways. It is a discipline improvement, but the alpha-retag loop used
throughout the 1.8.3 work stops being possible.

One incidental benefit: the whole `withSourcesJar()` question disappears. The publishing
plugin configures sources and javadoc for Android library variants itself, so the
hand-rolled sources-jar machinery was deleted rather than fixed.

---

## Phases

### 1. Portal and namespace — done

- **Namespace verified.** `uk.co.appoly` shows as Verified on the Central portal under org
  *Appoly*. The TXT record that was genuinely absent from `appoly.co.uk`'s authoritative
  nameservers is now live. A verified namespace covers its subgroups, so
  `uk.co.appoly.droid` needs no separate claim.
- **Signing key.** RSA 4096, no expiry, `2FF86BD312C381D279FA36F23F4AD175B7176969`, uid
  `Appoly (Maven Central Signing)`. In 1Password under *Appoly Shared → Appoly Maven
  Central Signing*, with both revocation certificates pre-generated and no copy on disk.
  The key belongs to the organisation, not a person — anyone with vault access can publish.
- **Public half** on `hkps://keyserver.ubuntu.com`, verified by fetching it back into a
  clean keyring rather than trusting `--send-keys`, whose exit code reports success
  regardless. That keyserver only — which is the one Central checks.

- **Portal token.** `portal-username` and `portal-token` are in the vault item alongside the
  key, added 26 Aug. Field labels use hyphens and no spaces, which matters: a label
  containing a space or parenthesis cannot be read through an `op://` reference at all —
  that is why FlexiLogger's `private key (armored)` needs the JSON workaround its wrapper
  carries, and why this script does not.

`scripts/publish.sh` reads all five by exactly those names and fails clearly if any is
missing, so a misnamed field surfaces immediately rather than mid-upload.

### 2. Publishing configuration — done (`e48652e`)

All 26 modules carry `com.vanniktech.maven.publish` 0.37.0. Shared POM metadata — licence,
developers, SCM, URL — lives once in the root build, so a module declares only its own name
and description. No hand-written `publishing { }` block survives anywhere in the tree.

Central rejects an incomplete POM, and a missing `developers` block is a hard rejection.

- Sources-jar machinery and module-metadata workarounds **deleted**, not ported. They
  existed only to fight the rewriter.
- The BOM's constraints are rewritten to the new coordinates.
- `publishing-check` is kept. Platform-variant leakage is a defect class independent of the
  host, and it runs in CI on every PR.

### 3. Release path — done, revised

**Superseded.** This phase originally specified a tag-triggered GitHub Actions release job.
That is not the chosen path: **releases are run manually, locally, by a developer** for now.
No release CI, and no Maven Central secrets in the repository — repo-level Actions secrets
are empty, which is correct rather than missing. `.github/workflows/release.yml` has been
deleted to match.

The wrapper lives **in the repo**, not a personal shell profile. FlexiLogger's equivalent
sits in one person's `~/.zshrc`, which is single-user by construction; `scripts/publish.sh`
reads the shared vault, so anyone with access can release. It offers `--dry-run` (every
gate, no publish, no tag) and `--local` (signed install to `~/.m2`).

Gradle reads these only under the `ORG_GRADLE_PROJECT_` prefix with exact camelCase — the
script exports all five:

| 1Password field | Environment variable |
|-----------------|----------------------|
| `portal-username` | `ORG_GRADLE_PROJECT_mavenCentralUsername` |
| `portal-token` | `ORG_GRADLE_PROJECT_mavenCentralPassword` |
| `private-key` | `ORG_GRADLE_PROJECT_signingInMemoryKey` |
| `key-id` | `ORG_GRADLE_PROJECT_signingInMemoryKeyId` |
| `passphrase` | `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` |

```bash
export OP_ACCOUNT=appoly.1password.com   # required: two accounts are registered
```

### 4. Publish 1.9.0 and verify — done

A minor bump, not a patch — the coordinates change, so consumers must act.
`TOOLBOX_VERSION` is already `1.9.0`; no `1.9.0` tag exists and Central returns 404 for the
coordinates, so nothing has been claimed yet.

Verification drops the parts that existed only to catch JitPack's rewriting. What remains is
the check 1.8.3's verification missed: confirm the served `.module` carries a sources variant
whose file entry ends `-sources.jar`, then confirm cmd+B lands on real source for an **AAR**
module — not just the one JVM module.

**Immutability bites here.** A released version can never be re-uploaded or corrected.
Iterate with `--local` *before* the release, not after.

**Released 26 Aug 2026.** Deployment `6dde9178-1023-4072-b6a4-d920bdae35bd`, 48/48 components
validated by Central, tag `1.9.0` → `e400b22`. Verified against the bytes Central actually
serves, not against the local build:

- All 26 coordinates return their POM from `repo1.maven.org`.
- `baserepo` — an **AAR** module, the case that defeated JitPack twice — serves a
  `releaseVariantReleaseSourcePublication` variant whose file entry is
  `baserepo-1.9.0-sources.jar`, classifier intact. This is precisely what JitPack stripped.
- That sources jar downloads (19,981 bytes) and is a valid archive containing 14 `.kt` files,
  so it is real source rather than an empty placeholder.

The remaining check is human: cmd+B in a consuming project should land on source rather than
decompiled bytecode. Do it against an AAR module — 1.8.3 passed verification because only the
one JVM module was checked.

The earlier `--dry-run` passed on 26 Aug: all five vault fields read, tests and coverage green,
consumer keep rules intact, 26 modules signed and installed to `~/.m2`, and the variant gate
clean (4 toolbox modules at 1.9.0, no `-jvm` duplicates). The only untested step is the
upload itself — reading the portal token proves it is fetchable, not that Central accepts it.

Note the script pins `GRADLE_OPTS` for its own Gradle runs. Publishing drives Dokka across all
26 modules in a single daemon, which exceeds the 1 GiB metaspace a typical personal
`~/.gradle/gradle.properties` sets — and user-level properties beat the repo's, so the project
cannot fix this itself. Unpinned, the release fails with a bare `Metaspace` error on an
arbitrary module, naming the real cause nowhere.

Owner: Bradley to run `./scripts/publish.sh`; one consumer session to verify.

### 5. Migrate consumers

All known consumers are in-house — WenWe, AssistantHood, AIM Capture, Accelerate-Android —
so a clean cut is simpler than dual-publishing. 1.8.3 stays available on JitPack indefinitely
as the last release there.

Worth pairing deliberately: WenWe and AssistantHood exercise different module sets, so a
single green consumer proves little about the other.

**WenWe migrated first, 26 Aug — passed.** Three findings that apply to the remaining three:

1. **FlexiLogger moves 2.1.3 → 2.1.4.** The toolbox's own source is unchanged from 1.8.3 apart
   from publishing config, but `81d5aac` bumped FlexiLogger, and it arrives transitively via
   `baserepo`, `datehelperutil`, `connectivitymonitor` and `s3uploader`. A consumer that does
   not declare a FlexiLogger version inherits the bump silently — and it is the layer logging
   and crash reporting go through. A resolved-classpath diff on WenWe (949 → 948 coordinates)
   showed this and the toolbox group/case swap were the *only* changes.

2. **Do not remove `jitpack.io` by default.** The toolbox no longer needs it, but consumers may:
   WenWe still resolves `com.github.projectdelta6:PrefsHelperBase` and `ComposeReorderable`
   from JitPack. Removing it there would have broken the build. Check per consumer; assume it
   stays until proven otherwise.

3. **The `mavenLocal()` warning is per-consumer, not universal.** WenWe never declares it, so
   the stale local 1.9.0 in `~/.m2/repository/uk/co/appoly` could not shadow anything. Check
   before advising anyone to clear it.

Also confirmed in WenWe: `okhttp-android` resolving beside `okhttp` is pre-existing (the normal
okhttp 5.x split, present on both sides of the diff), not a migration artefact; no `-jvm` beside
an `-android`; and `assembleStagingRelease` passes under R8 with no new missing-class warnings,
which is the honest duplicate-class test since dexing is what would fail.

Owner: per-app sessions, roughly 30 minutes each.

---

## One deliberate deviation: keyless local publishing

This plan originally warned that once signing was configured, a bare
`./gradlew publishToMavenLocal` would fail with "no configured signatory", and called that
intended. It has since been relaxed, because it broke pull-request CI.

PR CI runs `publishToMavenLocal` to feed the variant-resolution gate, and deliberately holds
no signing key — a pull-request build must not carry the release key. With
`signAllPublications()` unconditional, the gate died at `:bom:signMavenPublication` before
the check it exists to run. Signing is now enabled only when a key is present; the gate reads
module metadata and POMs and never looks at a signature.

The safety property is preserved by a task-graph guard rather than by accident: any Maven
Central upload without a key fails at configuration time with an explicit refusal, instead of
uploading artifacts Central would reject in a release that cannot be undone.

Side effect worth knowing: a keyless local publish leaves *stale* `.asc` files from earlier
signed runs sitting beside freshly-written unsigned artifacts in `~/.m2`. Listing the
directory looks signed. Timestamps are the only honest signal.

---

## What consumers change

| Before | After |
|--------|-------|
| `com.github.appoly.AppolyDroid-Toolbox:BaseRepo` | `uk.co.appoly.droid:baserepo` |
| `com.github.appoly.AppolyDroid-Toolbox:S3Uploader-Multipart` | `uk.co.appoly.droid:s3uploader-multipart` |
| `com.github.appoly.AppolyDroid-Toolbox:AppolyDroid-Toolbox-bom` | `uk.co.appoly.droid:bom` |
| `maven { url = "https://jitpack.io" }` | Removable, if nothing else needs it |

## Risks and gotchas

- **No more retagging.** Released versions are immutable. Pre-release iteration happens
  through `--local`, which behaves differently from the alpha tags used during 1.8.3.
- **The key is on one keyserver only.** `keyserver.ubuntu.com`, which is what Central checks.
  Do not assume it resolves elsewhere.
- **Coordinated cutover.** Four apps need updating; 1.8.3 and 1.9.0 will briefly coexist
  across the estate.
- **Release runs on one machine.** With no CI release job, publishing depends on a
  developer's local toolchain and an unlocked 1Password. That is the accepted trade for now,
  not an oversight.

### Dead ends — do not resurrect

- **"GPG Appoly key"** in the Appoly *Employee* vault (2023) holds a username and passphrase
  but no key material, and the key exists nowhere reachable. Abandoned earlier attempt.
- **"Nexus Staging"** in Appoly Shared (2020) belongs to Calum and is explicitly out of
  scope. Unrelated to this migration.

---

1.8.3 remains the current JitPack release and is unaffected; its heap, progress, notification
and duplicate-class fixes are all verified and unrelated to the sources question.
