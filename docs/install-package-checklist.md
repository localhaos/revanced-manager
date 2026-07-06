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
- If install permission is missing, the helper opens Android's per-app unknown-sources settings for the manager package before reporting the missing permission.
- `ActivityNotFoundException` is converted into a clear `IllegalStateException` when no package installer is available.
- Patching now skips mounted-install root unmount when root access is not granted, avoiding the root-service Binder path in no-root mode.
- Failed root unmount attempts are logged and skipped instead of failing the patch worker.
- After signing the patched APK, `PatcherWorker` now requests no-root installation through `PM.installPackage(outputApk)` when root access is not granted.
- If Android blocks installer launch from the worker or the install permission is missing, the patch result is kept and the failure is logged instead of deleting the generated APK.

## Samsung S20 no-root notes

- Use the normal package installer path, not mounted/root install.
- First run usually requires enabling per-app installation permission: Settings -> Apps -> Special access -> Install unknown apps -> ReVanced Manager -> Allow from this source.
- If this permission is missing, `PM.installPackage` now opens the matching settings screen through `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`.
- If Samsung/Android blocks launching the installer from the background worker, retry installation from a foreground UI action or manually open the generated APK from the saved output path.
- Updating an already installed app still requires Android signature compatibility. A patched APK signed with a different key cannot replace an existing package signed with another key without uninstalling the old package first.

## Build/runtime constraints

- Release builds are signed through the debug signing config when `-PsignAsDebug` is provided.
- Build APK workflow generates separate `v7` and `v8` APK artifacts.
- APK artifacts are uploaded from `app/build/outputs/apk`.

## Still to verify in UI flow

- On Android versions that block background activity launch, the UI should expose the generated APK path and allow manually retrying `PM.installPackage(apk)` from a foreground user action.
- If the installer path uses Ackpine or `PackageInstaller` elsewhere, installation result callbacks must surface failure reasons to the UI/logs.
- `InstallSourceResolver` currently exists as a helper; it still needs to be wired into any UI/runtime logic that distinguishes Play Store installs (`com.android.vending`) from other install sources.

## Not included intentionally

- No hidden system install bypass is implemented.
- No package signature or integrity check bypass is implemented.
- No privileged `/system` write is performed during normal install flow.
