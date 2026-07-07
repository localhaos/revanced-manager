# LocalHaos main feature matrix

This file is the high-level feature contract for the LocalHaos `main` branch. It is intentionally small and grep-friendly so CI and maintainers can verify that upstream syncs did not silently remove local functionality.

## Patch bundle families

```text
REVANCED
MORPHE
AMPLE
```

Expected source asset support:

```text
.rvp
.mpp
.arp
.jar
.json
```

Current runtime status:

```text
REVANCED: native manager runtime, primary supported family
MORPHE: manager runtime dependencies present, MicroG-RE companion required for patched Google app services
AMPLE: family and .arp asset classification present; build-safe runtime fallback is used unless AmpleReVanced GitHub Packages can be resolved in CI
```

## Source parsing contract

The manager should accept these source shapes when available:

```text
GitHub repository URL
GitHub releases URL
GitHub latest release URL
GitHub release tag URL
GitHub API release URL
direct .rvp URL
direct .mpp URL
direct .arp URL
direct .jar URL
direct metadata .json URL
GitHub blob metadata .json URL
raw.githubusercontent.com metadata .json URL
Jman-Github generated bundle metadata JSON
Morphe source shorthand URLs when normalized by GitHubBundleAutoFinder
```

The manager must reject human-only index pages as a single bundle source. Index/catalog pages need a concrete generated metadata URL or release asset URL.

## APK source handling

Expected LocalHaos APK source features:

```text
plain APK import
single APK from APKS/XAPK/APKM/ZIP archive
base APK recovery from split archive
best-effort split merge
framework/config split filtering
system base APK copy for installed split apps
downloaded/cached APK source where available
```

Split support is best-effort. The manager must prefer a clean base APK when merge confidence is low and must not corrupt the user-selected source silently.

## Failure isolation contract

Patch source failures must be per-source, not global:

```text
empty bundle file -> source error only
invalid bundle file -> source error only
ClassNotFoundException / NoClassDefFoundError -> source error only
duplicate patch display names -> allowed
blank patch name -> invalid source
zero parsed patches -> invalid source
```

ReVanced patch suggestions must be calculated from `REVANCED` family bundles only. Morphe and Ample bundles must not pollute ReVanced suggested-version calculations.

## MicroG-RE boundary

MicroG/GmsCore services do not belong inside the manager APK. Licensing, ModuleInstall, PoToken, DroidGuard, Cast, Games, Maps and RCS compatibility belong in the companion MicroG/GmsCore APK, for example MorpheApp/MicroG-RE or a compatible fork.

The manager may document and surface companion requirements, but must not copy MicroG service implementations into the patch manager process.

## Build/release invariants

The LocalHaos `main` branch should preserve:

```text
app/build.gradle.kts
settings.gradle.kts
gradle/libs.versions.toml
app/src/main/java/app/revanced/manager/domain/sources/GitHubBundleAutoFinder.kt
app/src/main/java/app/revanced/manager/domain/repository/PatchBundleRepository.kt
app/src/main/java/app/revanced/manager/patcher/patch/PatchBundle.kt
app/src/main/java/app/revanced/manager/patcher/patch/PatchBundleInfo.kt
app/src/main/java/app/revanced/manager/patcher/patch/PatchBundleType.kt
app/src/main/java/app/revanced/manager/util/AppArchiveImport.kt
app/src/main/java/app/revanced/manager/util/SystemApkSource.kt
docs/localhaos-main-feature-matrix.md
docs/morphe-microg-re-compatibility.md
```

Release builds should keep debug signing fallback enabled unless a proper release keystore is intentionally configured.
