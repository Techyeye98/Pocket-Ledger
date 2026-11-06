# Pocket Ledger signing recovery

Verified on 6 November 2026. Pocket Ledger now uses public version **1.0.0** (Android version code **25**). The published app's existing signing certificate is preserved.

| Recovery field | Value |
| --- | --- |
| App name | Pocket Ledger |
| Package ID | `com.pocketledger.v6.preview` |
| Key alias | `androiddebugkey` |
| Keystore format | PKCS12 |
| Certificate SHA-256 | `91:A0:C4:36:BE:B2:3B:41:46:54:58:F3:E7:85:02:65:5B:8B:40:DB:66:0E:D0:1F:C9:C4:17:6B:21:C8:57:D6` |
| Certificate expiry | 3 September 2056 |
| Backup file | `output/signing/Pocket-Ledger-Signing-Key.p12` |
| Backup file SHA-256 | `bdb72d854794079dc08e663faf0ae4bfe4162d1ed65ab60e8a38350067f722b8` |

Keep **Pocket-Ledger-Signing-Key.p12** in a private encrypted-file backup, with another recoverable copy on separate storage. Do not put it in website downloads, Git, a public release or a shared folder. The generated file is in the repository's ignored `output/signing/` folder, inside the owner's OneDrive workspace. Copy it to the intended private backup location; do not rely on this working copy alone.

The password is a cryptographically random 256-bit value. Store it separately in **Google Password Manager**, using an entry named **Pocket Ledger — Android Signing Key** and username **androiddebugkey**. Put the app name, package, alias and certificate SHA-256 above in the entry notes. Password Manager saving remains a manual owner action; the scripts do not access the Google account.

## Copy the password locally

From the repository root, in PowerShell 7:

```powershell
pwsh -NoProfile -File ./tools/signing/Use-ReleaseSigning.ps1 -Action CopyPassword
```

The command verifies the backup's password and certificate, then copies the password without printing it. Paste into Google Password Manager, save, and press Enter in the terminal to clear that clipboard value. Disable clipboard history/cloud sync before copying if enabled; clearing the active clipboard does not erase saved clipboard history. Do not paste the password into chat or a command line.

Until saved in Password Manager, the password is recoverable on this Windows account through Windows DPAPI at `%USERPROFILE%/AppData/Local/PocketLedger/SigningSecrets/preview-backup-password.dpapi.xml`. That directory is restricted to the current Windows user and SYSTEM and is outside Git/OneDrive. This is a local convenience, not a portable password backup: another Windows account/machine cannot decrypt that file. Keep it separate from the portable `.p12` backup.

## Build future releases on this computer

```powershell
pwsh -NoProfile -File ./tools/signing/Use-ReleaseSigning.ps1 -Action Build
```

The helper validates the backup identity, decrypts the local password into process environment variables, builds `assembleRelease`, and verifies the output's signature and package against the published identity. Password variables are cleared when it exits. Output: `V6/NativeExpenseEntry/app/build/outputs/apk/release/app-release.apk`.

Keep package ID and signing certificate unchanged and increase `versionCode` for each future release. This permits updates over compatible existing installations without uninstalling. The release build is **1.0.0**, version code **25**. Its verified APK is the artifact used for the stable `v1.0.0` GitHub Release and website download.

Use **major.minor.patch** for public versions: `1.0.1` for a fix, `1.1.0` for added features and `2.0.0` for a major release. Android version codes always increase independently: `25`, `26`, `27`, and so on. `V6` in source paths and `v6.preview` in the existing package ID are legacy internal identifiers, not the displayed version. Changing the package ID would create a separate installation.

The backup's permanent filename is **Pocket-Ledger-Signing-Key.p12**. The previously generated backup was renamed without changing its bytes, key, alias, certificate or password; its SHA-256 above is unchanged. Reuse this same backup for future versions instead of generating a replacement key.

## Recover on another computer

1. Restore the `.p12` from your private backup and the source repository. Install PowerShell 7, JDK 17+ and Android SDK API 36/build tools. Compare the `.p12` SHA-256 with the value above using `Get-FileHash -Algorithm SHA256`.
2. Retrieve the password from Google Password Manager. Run the command below; it requests a hidden password input, verifies the certificate, and stores a new Windows DPAPI copy on this computer. The original Windows DPAPI file is not needed.

```powershell
pwsh -NoProfile -File ./tools/signing/Use-ReleaseSigning.ps1 -Action RestorePassword -BackupFile 'D:/PrivateBackups/Pocket-Ledger-Signing-Key.p12'
pwsh -NoProfile -File ./tools/signing/Use-ReleaseSigning.ps1 -Action Build -BackupFile 'D:/PrivateBackups/Pocket-Ledger-Signing-Key.p12'
```

Use `-JavaHome` and `-Sdk` if your installation paths differ. Compare the resulting APK certificate with the table using `apksigner verify --verbose --print-certs app-release.apk` before distribution.

## Local settings / CI

Gradle accepts `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` from process environment variables, with gitignored `local.properties` as a fallback. The two password values are the same for this PKCS12 file. Alias: `androiddebugkey`; format: PKCS12. Relative store paths are relative to the app module; use an absolute path to avoid ambiguity.

On CI, restore the `.p12` into a temporary private runner file using the CI secret store and supply those four variables as masked secrets/settings. Run `:app:assembleRelease` and verify the certificate above. Never echo secrets, enable shell tracing, upload the keystore as an artifact, or commit a local password file. Existing website CI only publishes staged website files; it has not been given the key or password.

## Evidence and scope

- Downloaded the public APK from `https://raw.githubusercontent.com/Techyeye98/Pocket-Ledger/main/distribution/downloads/Pocket-Ledger.apk`; package and signing certificate match the source preview and local original key.
- Public APK SHA-256: `ca7ebe626eb3b16458316cfb2e803e0ab158506743129759bc2c67617aa09945`.
- Exported the existing private key into a new password-protected PKCS12 file. Correct-password load/certificate match and wrong-password rejection passed.
- Signed an APK using the backup's private key, then verified its signature and certificate against the published APK. Machine-readable evidence: ignored `output/signing/verification.json`; proof APK: `output/signing/backup-signed-verification.apk`.
- The preview was originally published using an Android debug identity. The preserved certificate/alias still say `Android Debug` / `androiddebugkey`; encrypting its backup cannot change that identity. This backup does not make the preview a Play Store production identity. The original debug keystore still uses the conventional debug password; protect access to that original private key too.
- The older `~/.android/pocket-ledger-release.jks` is untouched. This backup does **not** recover the separate older release-signed package. Updating that older app requires its original key credentials and a matching APK/package check.
- A genuinely new application/package should have its own dedicated production key. Do not replace the published preview key with a newly generated key.

Android references: [app signing and update identity](https://developer.android.com/studio/publish/app-signing), [apksigner verification and environment password inputs](https://developer.android.com/tools/apksigner).
