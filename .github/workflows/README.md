# CI / Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `build-app.yaml` | push to `main`, PRs, manual | Debug build (lint + assemble) signed with the public debug keystore. Use it to grab an installable debug APK from the artifacts. |
| `build-signed-release.yaml` | push to `main`, manual | Release build + signing. Produces installable, signed release APKs on every push to `main`. |
| `release-app.yaml` | push tag `v*`, manual | Full release pipeline: Release build → sign → draft GitHub Release with all APKs attached. Supports `-rc.` tags (marked as pre-release). |
| `common-build.yaml` | reusable | Build/lint helper (`buildNative` + `lint*` + `assemble*`), with Gradle/Go/SDK/native-libs caching. |
| `common-sign.yaml` | reusable | APK signing helper. |
| `update-syncthing-submodule.yaml` | weekly, manual | Bumps the Syncthing submodule to the latest upstream release and opens a PR with the version bump. |
| `update-go-version.yaml` | monthly, manual | Bumps the pinned Go toolchain version and opens a PR. |
| `lock-threads.yml`, `recycle-runs.yml`, `copilot-setup-steps.yml` | inherited | Upstream-only housekeeping; they no-op outside the original repository. |

## Signing

Release signing is always configured:

1. **Preferred (stable signature):** add these repository secrets
   * `SIGNING_KEYSTORE_JKS_BASE64` – base64 of a JKS keystore containing an alias named `Syncthing-Fork`
   * `SIGNING_PASSWORD` – keystore & key password

   Create a keystore locally with:
   ```bash
   keytool -genkeypair -v -keystore signing-keystore.jks -storetype JKS \
     -storepass <password> -keypass <password> -alias Syncthing-Fork \
     -keyalg RSA -keysize 2048 -validity 10950 \
     -dname "CN=Syncthing-Fork, OU=Syncthing-Fork, O=Syncthing-Fork, C=US"
   base64 -w0 signing-keystore.jks   # paste into the secret
   ```

2. **Fallback (no secrets set):** an ephemeral self-signed keystore is generated
   on the runner for each release run. The APKs are properly signed and
   installable, but the signature changes between builds – uninstall before
   installing a new build.

## Releasing

```bash
# 1. Bump version in gradle/libs.versions.toml (version-name / version-code must match)
# 2. Tag and push
git tag v2.1.4.0 && git push origin v2.1.4.0
# 3. "Release App" workflow runs; a draft release with the signed APKs appears
```
