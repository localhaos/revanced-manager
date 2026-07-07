# Patch bundle source candidates

This file tracks GitHub repositories that can be useful when testing patch bundle source support.

## Morphe

Recommended starting point:

- `https://github.com/MorpheApp/morphe-patches`

Related repositories:

- `https://github.com/MorpheApp/morphe-manager`
- `https://github.com/MorpheApp/morphe-patches-template`
- `https://github.com/MorpheApp/morphe-cli`
- `https://github.com/MorpheApp/morphe-patcher`

Community/fork candidates found during GitHub search:

- `https://github.com/crimera/piko`
- `https://github.com/hoo-dles/morphe-patches`
- `https://github.com/brosssh/morphe-patches`
- `https://github.com/rushiranpise/morphe-patches`
- `https://github.com/icysymmetra/tiktok-patches-for-morphe`

## ReVanced bundle index candidates

- `https://github.com/Jman-Github/ReVanced-Patch-Bundles`

## URL handling note

The human-facing site `https://morphe-patches.software/` should not be treated as a direct patch bundle source by itself. It is an index page. The manager should use either:

- a GitHub repository/release URL that contains downloadable `.mpp`, `.rvp`, or `.jar` assets; or
- a direct `.mpp`, `.rvp`, or `.jar` asset URL.

The direct `morphe-patches.software` domain is intentionally handled as a diagnostic case so the app can show a clear message instead of trying to parse HTML as a bundle API response.
