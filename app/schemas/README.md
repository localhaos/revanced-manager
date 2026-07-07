# Room schema export

Room exports schema files per `@Database` class, not per DAO, repository, feature package or entity.

This app currently has one Room database:

```text
app.revanced.manager.data.room.AppDatabase
```

Therefore the expected schema directory is exactly:

```text
app/schemas/app.revanced.manager.data.room.AppDatabase/
```

The versioned JSON files in that directory contain every table registered in `AppDatabase`. There should not be separate schema directories for:

```text
bundles/
selection/
downloader/
apps/downloaded/
apps/installed/
options/
```

Those packages are entity/DAO packages. They are not independent Room databases.

Current `AppDatabase` entities are exported together in the same schema JSON files:

```text
PatchBundleEntity
PatchSelection
SelectedPatch
DownloadedApp
InstalledApp
AppliedPatch
InstalledPatchBundle
OptionGroup
Option
DownloaderEntity
```

Current expected schema files:

```text
app/schemas/app.revanced.manager.data.room.AppDatabase/1.json
app/schemas/app.revanced.manager.data.room.AppDatabase/2.json
app/schemas/app.revanced.manager.data.room.AppDatabase/3.json
app/schemas/app.revanced.manager.data.room.AppDatabase/4.json
app/schemas/app.revanced.manager.data.room.AppDatabase/5.json
```

Create another schema directory only if another class annotated with `@Database` is added. Until then, all Room migrations and exported schemas belong under `AppDatabase`.
