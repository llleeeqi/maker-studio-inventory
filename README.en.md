# Maker Studio Inventory Manager

[中文](./README.md)

This is a scan-driven inventory app for a personal workshop or small maker studio.

The current mainline is a native Android v1 app:

```text
mobile_android/
Kotlin + Jetpack Compose + CameraX + ML Kit + JSON snapshot
```

The older Web/Capacitor and Flutter mobile implementations are kept only as historical references. New phone features should target the native Android app.

## Current Scope

Android configuration:

```text
applicationId = studio.inventory.android
minSdk = 31
targetSdk = 36
compileSdk = 36
```

The first version focuses on a local Android workflow:

- Embedded camera scanner, QR code only.
- `v1;` payloads only; legacy short payloads and `msi:v1;` are not supported.
- The Add page generates fixed-label payloads; printing is a placeholder.
- Scanning an item only displays fixed label data.
- Stock-in requires item data, weight/quantity, location, and explicit confirmation.
- Location sorting mode can update item locations by continuous scanning after one confirmation.
- Local persistence uses a JSON snapshot first; SQLite/Room can come later.

## Payload Protocol

```text
v1;key=value;key=value
```

Examples:

```text
v1;type=spool;id=FIL-260617-001;brand=Bambu;material=PLA;color=white;tare_g=200;created_on=260622;note=note
v1;type=part;id=PART-260617-001;name=M3x8 black screw;unit_weight_g=0.42;created_on=260622;note=note
v1;type=other;id=ITEM-260617-001;name=Heat gun;created_on=260622;note=note
v1;type=location;id=LOC-260617-001;name=Rack A shelf 1;created_on=260622;note=note
v1;type=weight;value_g=712.4
```

QR codes store fixed data only. Non-ASCII characters, spaces, and separators in field values are percent-encoded in the QR payload and decoded by the app. Current weight, quantity, location, stock status, and transactions live in local app data.

## Directories

| Path | Purpose |
|---|---|
| `mobile_android/` | New native Android mainline. |
| `tools/` | Virtual shelf test bench. |
| `core/` | Early JavaScript business core, kept as reference. |
| `app/` | Early Web test UI, kept as reference. |
| `android/` | Historical Android Web shell. |
| `mobile_flutter/` | Historical Flutter 0.2.x implementation. |
| `tests/` | Early JS core tests. |
| `docs/` | Current design and implementation notes. |

Virtual shelf test bench:

- GitHub Pages: https://llleeeqi.github.io/maker-studio-inventory/tools/
- Local file: [tools/index.html](./tools/index.html)

## Documentation

Start from [docs/README.md](./docs/README.md).

Key docs:

- [架构决策.md](./架构决策.md): current architecture decision.
- [docs/13-native-android-v1-plan.md](./docs/13-native-android-v1-plan.md): native Android v1 plan.
- [docs/12-v1-protocol-and-scope.md](./docs/12-v1-protocol-and-scope.md): `v1;` protocol and local record boundary.
- [docs/01-qr-input-workflows.md](./docs/01-qr-input-workflows.md): scan workflows.
- [docs/04-next-steps.md](./docs/04-next-steps.md): next implementation tasks.
