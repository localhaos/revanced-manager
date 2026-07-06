# Package installation checklist

This document tracks the minimum runtime requirements for installing generated APK packages from the manager.

## Confirmed in manifest

- `android.permission.REQUEST_INSTALL_PACKAGES` is declared.
- `android.permission.REQUEST_DELETE_PACKAGES` is declared.
- `ManagerFileProvider` is declared with `android:exported="false"`.
- `ManagerFileProvider` has `android:grantUriPermissions="true"`.
- `file_provider_paths.xml` exposes app files, app cache, external files, and external cache roots for generated APK handoff.

## Confirmed in runtime helper code

- `PM.installPackage(apk: File)` performs a non-root install handoff through `Intent.ACTION_INSTALL_PACKAGE`.
- The APK path is converted to a `content://` URI through `ManagerFileProvider`.
- The install intents use MIME type `application/vnd.android.package-archive`.
- The helper tries both `Intent.ACTION_INSTALL_PACKAGE` and `Intent.ACTION_VIEW` so system installers and external APK installers can receive the file.
- The install chooser is used when a compatible installer such as a third-party APK installer is available.
- The install intents include `Intent.FLAG_GRANT_READ_URI_PERMISSION`.
- The install intents include `ClipData` with the APK URI for Android versions that require URI grants through clip data.
- Read access is explicitly granted to every resolved installer activity before starting the installer.
- The helper rejects missing files and non-APK file extensions before launching the installer.
- The helper checks `PackageManager.canRequestPackageInstalls()` before launching the installer.
- `ActivityNotFoundException` is converted into a clear `IllegalStateException` when no package installer is available.

## Build/runtime constraints

- Release builds are signed through the debug signing config when `-PsignAsDebug` is provided.
- Build APK workflow generates separate `v7` and `v8` APK artifacts.
- APK artifacts are uploaded from `app/build/outputs/apk`.

## Still to verify in UI flow

- The UI/controller that handles a completed patch should call `PM.installPackage(apk)` or an equivalent installer path.
- If the installer path uses Ackpine or `PackageInstaller` elsewhere, installation result callbacks must surface failure reasons to the UI/logs.
- `InstallSourceResolver` currently exists as a helper; it still needs to be wired into any UI/runtime logic that distinguishes Play Store installs (`com.android.vending`) from other install sources.

## Not included intentionally

- No hidden system install bypass is implemented.
- No package signature or integrity check bypass is implemented.
- No privileged `/system` write is performed during normal install flow.
