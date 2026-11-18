# AirRemote

Turn an Android phone into a Bluetooth air mouse and remote for Android TV / Fire TV — point the phone at the screen and the cursor follows, with no drift.

Forked from [BT Remote](https://gitlab.com/Atharok/BtRemote) by Atharok.

## Features

- **Drift-free air mouse** — the phone's orientation maps directly to a screen position (sent as a HID pen tablet), so the cursor can't slowly wander off. Neutral is a natural ~30° hand-held pointing angle.
- **One-handed radial pad** — CLICK at the vertex, with BACK, SCROLL and CENTER around it, placed in the right thumb's comfort zone. Media and volume rows above, Home / Menu below.
- **Two scroll modes** (toggle on the bottom row):
  - **DRAG** — tilt to scroll like a finger swipe, axis-locked, faster the further you tilt; horizontal too.
  - **D-PAD** — tilt sends Up / Down / Left / Right presses, so scrolling works in any TV UI; the repeat rate depends only on the tilt angle.
  - Tap SCROLL to latch it, or hold it for momentary scrolling.
- **Stable clicks** — the cursor stays put while CLICK is held, even as your thumb tilts the phone; drags start only on a deliberate move.
- **Cursor auto-hide** after 3 s without real movement.
- **Pauses in the background** — no input is sent when the app isn't in the foreground.
- Keyboard, media and volume keys from the original app.

## Pairing

1. Open AirRemote on the phone.
2. On the TV: **Settings → Remotes & Accessories → Add accessory**, and pick the phone.

Some Fire TV models refuse phone-initiated pairing and need the pairing started from the TV side.
After a HID descriptor change, unpair and pair again — the TV caches the old one.

## Tuning

Air-mouse parameters can be changed live by editing `airmouse.json` in the app's external files directory (`Android/data/<app id>/files/`); it is reloaded every second.

## Building

```sh
./gradlew assembleDefaultDebug
./gradlew testDefaultDebugUnitTest
```

## License

GPL-3.0 — see [LICENSE](LICENSE).
