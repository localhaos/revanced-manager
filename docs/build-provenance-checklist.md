# Final APK provenance checklist

Use this checklist before publishing or installing a generated APK.

## Required repository

- Repository: `localhaos/revanced-manager`
- Branch: `main`
- Build artifact must include a provenance file with the Git commit SHA.

## Required feature files

The checkout used for the APK build must contain:

- `app/src/main/java/app/revanced/manager/util/GeneratedApkInstallHandler.kt`
- `app/src/main/java/app/revanced/manager/domain/sources/GitHubBundleAutoFinder.kt`
- `docs/patch-bundle-sources.md`

## Required features in APK

The final APK should include, or fail the build before publication if missing:

- generated APK install handler for foreground install handoff;
- installed-app source selection through `SelectedApp.Installed`;
- GitHub bundle source recognition;
- Morphe patch index diagnostics;
- APK artifact provenance with repository/ref/SHA/run ID;
- KSP source-set placeholders to avoid building from stale or incomplete checkout state.

## Build workflow guarantees

`Build APK` now performs checkout validation before Gradle starts. It fails early if the repository is not `localhaos/revanced-manager` or if required feature files are missing.

`Release` now validates the checkout before publishing and prepares the keystore before the first Gradle build step.
