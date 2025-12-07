# Publishing

Single-file Maven Central publishing script supporting snapshots (OSSRH) and releases (Portal API).

## Workflow

**Snapshots** → Publish directly to OSSRH  
**Releases** → Stage → Sign → Hash → Bundle → Upload to Portal API

See [gradle/PUBLISH_GUIDE.md](gradle/PUBLISH_GUIDE.md) for complete documentation.

## Quick Start

### Prerequisites

1. **Maven Central Account**: Create at [central.sonatype.com](https://central.sonatype.com/) and generate user token
2. **GPG Key** (releases only):
```shell
gpg --gen-key
gpg --list-keys
gpg --armor --export <key-id> --output public.gpg
gpg --armor --export-secret-key <key-id> --output secret.gpg
gpg --keyserver keyserver.ubuntu.com --send-keys <key-id>
```

### Setup Credentials

**Option 1: Environment variables**
```shell
export MAVENCENTRAL_USERNAME="your-username"
export MAVENCENTRAL_PASSWORD="your-token"
export GPG_PRIVATE_KEY="$(cat secret.gpg)"  # For releases
export GPG_PASSPHRASE="your-passphrase"     # For releases
```

**Option 2: gradle.properties**
```properties
# ~/.gradle/gradle.properties
MAVENCENTRAL_USERNAME=your-username
MAVENCENTRAL_PASSWORD=your-token
signing.keyId=ABCD1234
signing.password=your-passphrase
signing.secretKeyRingFile=/path/to/.gnupg/secring.gpg
```

## Publish Snapshot

```shell
./gradlew :record-builder:publishSnapshot
```

Publishes to: `https://s01.oss.sonatype.org/content/repositories/snapshots/`

## Publish Release

```shell
./gradlew :record-builder:publishRelease
```

This executes:
1. Publish to `build/staging-deploy/`
2. GPG sign all artifacts
3. Compute checksums (MD5, SHA-1, SHA-256, SHA-512)
4. Create `build/bundle.zip`
5. Upload to Maven Central Portal API

## GitHub Secrets

```shell
gh secret set MAVENCENTRAL_USERNAME --body "your-username"
gh secret set MAVENCENTRAL_PASSWORD --body "your-token"
gh secret set GPG_PRIVATE_KEY --body < secret.gpg
gh secret set GPG_PASSPHRASE --body "your-passphrase"
```

## Features

- ✅ Single file: `gradle/publish.gradle`
- ✅ Auto-detects snapshot vs release
- ✅ Separate workflows for each
- ✅ Smart GPG signing (releases only)
- ✅ Portal API integration
- ✅ Manual step-by-step execution

## Troubleshooting

**Check tasks:**
```shell
./gradlew :record-builder:tasks --group publishing
```

**Test staging:**
```shell
./gradlew :record-builder:publishMavenPublicationToStagingRepository
ls -la build/staging-deploy/
```

**Individual steps:**
```shell
./gradlew :record-builder:signStagedArtifacts
./gradlew :record-builder:computeChecksums
./gradlew :record-builder:createBundle
./gradlew :record-builder:uploadToMavenCentral
```

For detailed documentation, see [gradle/PUBLISH_GUIDE.md](gradle/PUBLISH_GUIDE.md).



