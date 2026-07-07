# Morphe MicroG-RE compatibility notes

This manager can import and classify ReVanced, Morphe and Ample patch bundle families, but MicroG/GmsCore compatibility is provided by a separate app, not by the manager itself.

For Morphe-patched Google apps, the relevant companion target is:

```text
MorpheApp/MicroG-RE
```

MicroG-RE is a fork of microG GmsCore adapted for patched Google apps without root and with an alternate package name. Its README states that it is adapted to work with patched Google apps and uses the Morphe `GmsCore support` patch to enable Google account authentication and Google service replacement.

## Relevant MicroG-RE / upstream GmsCore capability groups

### Account, package and profile routing

Important compatibility changes move exported profile-provider behavior into a package-specific base module and expose active profile data to other package modules. This matters for alternate package deployments and patched Google apps that need profile data through MicroG-compatible providers.

Relevant changes:

```text
773ceb3292a467f66dacadd9c8a6ff2d82f3cc59
Move profile provider to play-services-core-package

e773a870aaaad0c14c51a35f6ad6cd56edff5099
Move profile provider to play-services-base-core-package
```

### Phenotype compatibility

Some Google apps expect non-null or non-missing ExperimentTokens fields. MicroG-RE adds an empty name field to returned ExperimentTokens to avoid app-side failures.

Relevant change:

```text
b9a941c60ccd07801c4e6377b8ad5f30dd8a9b18
Phenotype: Add empty name to ExperimentTokens returned
```

### Location and Maps account settings

MicroG-RE includes low-power network-location handling and Google Maps account-settings URL allowlist expansion, including the Maps Timeline URL.

Relevant changes:

```text
7de8124f62933f38817359668b817f351f7e95d7
Location: Set low-power for network location requests

be20546b46550ac6ed77ca1f088cfd81c994ec52
Added new domain name to Google Maps Timeline
```

Related upstream PRs:

```text
microg/GmsCore#3588
Google Maps location-sharing settings redirect fix
```

### Play licensing / vending compatibility

MicroG-RE adds a settings surface and service support for Play/Vending license checks. This is relevant for patched paid apps that call the Google Play licensing service.

Relevant changes:

```text
4ef22480f113b624c7f159d906ba51ab1f40439f
Add preference for play licensing

6172ca33f60cdec0585c77394a148df9b3b66061
Use SwitchPreference for vending_licensing

05265c60cd07911ffba485f2da87986d69823661
Licensing service

fe9990639ead4273f208f1efdc6dd111ba04e131
Query licenses from multiple Google accounts in a row
```

### ModuleInstall / optional module surface

Some modern Google libraries call ModuleInstall APIs. MicroG-RE stubs the ModuleInstall binder surface so apps do not fail due to a missing optional-module service.

Relevant change:

```text
19946d2b81be29cc2ad6bee66db500a55498e70a
Add ModuleInstall API stub
```

### PoToken / DroidGuard-dependent flows

PoToken support is tied to DroidGuard and device-integrity token flows. These are MicroG/GmsCore-side features; the manager should not attempt to implement them inside the patch manager process.

Relevant change:

```text
3a8cdceb8a22c15cc88d5188f5c3269520ae36dc
Add PoToken service
```

Related upstream PRs:

```text
microg/GmsCore#3524
Implement guardWithRequest service path

microg/GmsCore#3602
Firebase Auth App Check token exchange for SafetyNet attestation
```

### Cast, games, Mapbox and installer compatibility

These are upstream GmsCore compatibility improvements that may matter to patched apps after install, but they are not manager features.

Relevant upstream PRs:

```text
microg/GmsCore#3354
Cast thread safety and memory-leak fix

microg/GmsCore#3505
CastMediaRouteController implementation

microg/GmsCore#3554
Cast framework dynamite module descriptor

microg/GmsCore#3577
No-op Cast ReconnectionService instead of null

microg/GmsCore#3512
Games service login compatibility

microg/GmsCore#3555
Mapbox zero-density bitmap / pixelRatio fix

microg/GmsCore#3543
Vending installer proxy fix for EMUI and splitDeferred

microg/GmsCore#3578
FaceDetector/Snapchat flicker crash mitigation
```

### RCS

RCS work is large and incomplete. It should not be treated as production-ready manager support. It belongs in the MicroG/GmsCore companion layer and requires device/carrier validation.

Relevant upstream PRs:

```text
microg/GmsCore#3497
Google Messages RCS setup support

microg/GmsCore#3604
Partial RCS support / Constellation / Asterism testing branch
```

## Manager integration decision

Do not copy MicroG-RE service code into the manager. The manager should only:

```text
- classify Morphe patch bundles correctly
- avoid crashing when Morphe bundles require MicroG companion functionality
- keep ReVanced, Morphe and Ample bundle families separated
- allow APK/source import and patching workflows to proceed
- document that runtime Google-service compatibility depends on MicroG-RE or a compatible GmsCore fork
```

MicroG service surfaces such as licensing, ModuleInstall, PoToken, DroidGuard, Cast, Games, Maps and RCS must remain in the companion MicroG/GmsCore APK.
