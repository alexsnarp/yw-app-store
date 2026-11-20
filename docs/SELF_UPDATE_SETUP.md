# YW App Store self-update publishing

The App Store self-update APK and release manifest are published to the separate Cloudflare R2 bucket:

- Bucket: `yw-app-store-updates`
- APK object: `yw-app-store.apk`
- Manifest object: `latest.json`
- Public APK URL: `https://pub-8cbd861b18084f04b3f76f963c0bea0f.r2.dev/yw-app-store.apk`
- Public manifest URL: `https://pub-8cbd861b18084f04b3f76f963c0bea0f.r2.dev/latest.json`

## GitHub Actions secrets

In GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
| --- | --- |
| `YW_KEYSTORE_BASE64` | Base64-encoded contents of the permanent release keystore |
| `YW_KEYSTORE_PASSWORD` | Keystore password |
| `YW_KEY_ALIAS` | Signing key alias |
| `YW_KEY_PASSWORD` | Signing key password |
| `R2_ACCESS_KEY_ID` | Access key ID from an R2 API token |
| `R2_SECRET_ACCESS_KEY` | Secret access key from that R2 API token |

Generate a new permanent signing keystore locally (keep it backed up securely and never commit it):

```powershell
keytool -genkeypair -v -keystore yw-app-store-release.jks -alias yw-app-store -keyalg RSA -keysize 2048 -validity 10000
```

In PowerShell, copy its Base64 value for the `YW_KEYSTORE_BASE64` secret:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\yw-app-store-release.jks")) | Set-Clipboard
```

Create a Cloudflare R2 API token with **Object Read & Write** access scoped only to `yw-app-store-updates`. Use the token's S3-compatible access key ID and secret access key for the two R2 secrets above. The workflow uses the account's R2 S3 endpoint and uploads both objects.

After adding all six secrets, run **Actions → Android build → Run workflow** on `main`. The workflow builds a signed release APK, writes `latest.json`, uploads both objects to R2, and retains the debug APK/source artifacts separately.

## Signing transition

The previous CI APK used an ephemeral GitHub-runner debug signing key. The original private key was not present in the repository or inspected workflow artifacts. A new permanent key therefore has a different certificate: install the first APK signed with the new key as a fresh install (uninstall the old copy first), then subsequent signed releases can update it in place. Keep the new keystore backed up; losing it prevents future in-place updates.

On Android versions where the app is not the device owner, Android may still display its own installation confirmation after the notification action starts the install session.
