# Run Syncthing as root (rooted devices)

> Applies to rooted devices (Magisk / KernelSU etc.) where su has been granted to this
> app. The feature is off by default; find it under Settings → Experimental → "Run
> Syncthing core as root".

## What it does

With root mode enabled, the Syncthing core runs with root privileges and can sync **any
directory**, typically:

- Other apps' data directories, e.g. `/data/data/<some.app>/files/...` (no SAF bridge
  needed)
- App-private storage like `/data/media/0/Android/data/<package>/`
- System directories and restricted paths on external storage

The built-in folder picker gains a **root browse mode** (terminal icon in the top bar):
directory listings are read through the root shell, so any folder on the device can be
picked for syncing.

## Behaviour and safety notes

1. **umask 000**: the root-uid core creates files (config.xml, certificates, logs and
   bridge staging dirs inside the app's private storage) with modes 666/777 so the
   unprivileged app itself can keep reading and writing them — otherwise it would lock
   itself out. The trade-off: files the core creates inside the app's private storage are
   writable by other apps. On a rooted device the security boundary is different anyway;
   weigh this yourself.
2. **Don't use root mode for shared storage**: paths under /sdcard sync fine in normal
   mode; syncing them as root leaves root-owned files behind that other apps cannot
   access.
3. **Process management goes through the root shell**: a root-uid core is invisible to
   the app's own `ps` and cannot be signalled by it, so stop/restart operations run
   through the root shell and match the full binary path — cores of other installed
   Syncthing clients on the same device are never touched.
4. **Switching root off**: when the setting is turned off, the app uses the still-working
   su to hand app-private storage back to the app UID (chown) before restarting the core
   with normal privileges — config and key generation keep working. A confirmation
   dialog reminds you that folders which only sync with root will stop syncing.
   The same hand-back runs as a safety net on cold starts after a root session.
5. **su can be revoked at any time**: after revocation the next core start falls back to
   normal privileges with a warning log; folders that only sync with root will then
   report access errors, which is expected.
6. The setting takes effect after the **next core start** (restart the app or use
   "Restart Syncthing").

## Acceptance checklist (developers)

- `ps` shows the core with UID 0 and PPID = app; the device connects on the desktop side
- Pick `/data/data/<other.app>/...` via root browse → two-way sync works
- Change the config (e.g. rename a folder) → web UI and home page stay in sync (config
  remains writable for the app while root mode is on)
- Kill the core (`su -c kill -9 <pid>`) → "has crashed (exit code 137)" notification
- Revoke the su grant in Magisk → next start falls back to normal privileges
