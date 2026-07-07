# Room schema export

Room exports schema files per `@Database` class, not per DAO or entity.

This app currently has one Room database:

```text
app.revanced.manager.data.room.AppDatabase
```

Therefore the expected schema directory is:

```text
app/schemas/app.revanced.manager.data.room.AppDatabase/
```

The versioned JSON files in that directory contain all tables/entities registered in `AppDatabase`, including patch bundles, patch selections, downloaded apps, installed apps, options and downloader sources.

Do not create separate schema folders for individual DAO/entity packages unless another `@Database` class is added.
