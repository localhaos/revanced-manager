# Package installation checklist

This document tracks the minimum runtime requirements for installing generated APK packages from the manager.

## Confirmed in manifest

- `android.permission.REQUEST_INSTALL_PACKAGES` is declared.
- `android.permission.REQUEST_DELETE_PACKAGES` is declared.
- `ManagerFileProvider` is declared with `android:exported="false"`.
- `ManagerFileProvider` has `android:grantUriPermissions="true"`.
- `file_provider_paths.xml` exposes app files, app cache, external files, and external cache roots for generated APK handoff.

## Build/runtime constraints

- Release builds are signed through the debug signing config when `-PsignAsDebug` is provided.
- Build APK workflow generates separate `v7` and `v8` APK artifacts.
- APK artifacts are uploaded from `app/build/outputs/apk`.

## Must be verified in runtime code

- The URI passed to `Intent.ACTION_INSTALL_PACKAGE`, `PackageInstaller`, or Ackpine must be a `content://` URI from `ManagerFileProvider`, not a raw `file://` URI.
- The install intent must grant read permission with `Intent.FLAG_GRANT_READ_URI_PERMISSION`.
- The generated APK file must be written under one of the paths exposed by `file_provider_paths.xml`.
- If the installer path uses Ackpine or `PackageInstaller`, installation result callbacks must surface failure reasons to the UI/logs.
- `InstallSourceResolver` currently exists as a helper; it still needs to be wired into any UI/runtime logic that distinguishes Play Store installs (`com.android.vending`) from other install sources.

## Not included intentionally

- No hidden system install bypass is implemented.
- No package signature or integrity check bypass is implemented.
- No privileged `/system` write is performed during normal install flow.
