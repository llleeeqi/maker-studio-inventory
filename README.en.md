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
- `msi:v1` payloads only; legacy short payloads are not supported.
- The Add page generates fixed-label payloads; printing is a placeholder.
- Scanning an item only displays fixed label data.
- Stock-in requires item data, weight/quantity, location, and explicit confirmation.
- Location sorting mode can update item locations by continuous scanning after one confirmation.
- Local persistence uses a JSON snapshot first; SQLite/Room can come later.

## Payload Protocol

```text
msi:v1;key=value;key=value
```

Examples:

```text
msi:v1;type=spool;id=FIL-260617-001;brand=Bambu;material=PLA;color=white;tare_g=200
msi:v1;type=part;id=PART-260617-001;name=M3x8 black screw;category=screw;spec=M3x8;unit_weight_g=0.42
msi:v1;type=other;id=ITEM-260617-001;name=Heat gun
msi:v1;type=location;id=LOC-260617-001;name=Rack A shelf 1
msi:v1;type=weight;value_g=712.4
```

QR codes store fixed data only. Current weight, quantity, location, stock status, and transactions live in local app data.

## Directories

| Path | Purpose |
|---|---|
| `mobile_android/` | New native Android mainline. |
| `tools/` | `msi:v1` payload test tools. |
| `core/` | Early JavaScript business core, kept as reference. |
| `app/` | Early Web test UI, kept as reference. |
| `android/` | Historical Android Web shell. |
| `mobile_flutter/` | Historical Flutter 0.2.x implementation. |
| `tests/` | Early JS core tests. |
| `docs/` | Current design and implementation notes. |

## Documentation

Start from [docs/README.md](./docs/README.md).

Key docs:

- [架构决策.md](./架构决策.md): current architecture decision.
- [docs/13-native-android-v1-plan.md](./docs/13-native-android-v1-plan.md): native Android v1 plan.
- [docs/12-msi-v1-and-0.2-scope.md](./docs/12-msi-v1-and-0.2-scope.md): `msi:v1` protocol and local record boundary.
- [docs/01-qr-input-workflows.md](./docs/01-qr-input-workflows.md): scan workflows.
- [docs/04-next-steps.md](./docs/04-next-steps.md): next implementation tasks.
