[CmdletBinding()]
param(
    [ValidateSet('Build', 'CopyPassword', 'RestorePassword')][string]$Action = 'Build',
    [string]$BackupFile,
    [string]$PasswordFile = "$env:USERPROFILE/AppData/Local/PocketLedger/SigningSecrets/preview-backup-password.dpapi.xml",
    [string]$JavaHome = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Sdk = "$env:USERPROFILE/AppData/Local/Android/Sdk"
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot/../..").Path
if (!$BackupFile) { $BackupFile = Join-Path $repo 'output/signing/Pocket-Ledger-Signing-Key.p12' }
$BackupFile = (Resolve-Path $BackupFile).Path
if ($Action -eq 'RestorePassword') {
    if (Test-Path $PasswordFile) { throw 'Local secret already exists; refusing to overwrite.' }
    $secure = Read-Host 'Paste the backup password from Google Password Manager' -AsSecureString
} else {
    $secure = Import-Clixml $PasswordFile
}
$credential = New-Object System.Management.Automation.PSCredential('signing', $secure)
$env:RELEASE_STORE_PASSWORD = $credential.GetNetworkCredential().Password
$env:RELEASE_KEY_PASSWORD = $env:RELEASE_STORE_PASSWORD
$env:RELEASE_STORE_FILE = $BackupFile
$env:RELEASE_KEY_ALIAS = 'androiddebugkey'
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$expected = '91a0c436beb23b41465458f3e78502655b8b40db660ed01fc9c4176b21c857d6'
try {
    $listing = & "$JavaHome/bin/keytool.exe" -list -v -storetype PKCS12 -keystore $BackupFile -alias androiddebugkey -storepass:env RELEASE_STORE_PASSWORD 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Backup password/alias validation failed.' }
    $match = @($listing | Select-String 'SHA256: ([A-Fa-f0-9:]+)')
    if ($match.Count -ne 1 -or $match[0].Matches[0].Groups[1].Value.Replace(':', '').ToLowerInvariant() -ne $expected) { throw 'Certificate mismatch; refusing to use a different signing identity.' }
    if ($Action -eq 'RestorePassword') {
        $directory = Split-Path $PasswordFile
        New-Item -ItemType Directory -Force $directory | Out-Null
        $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
        & icacls.exe $directory /inheritance:r /grant:r "${identity}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not restrict the password directory ACL.' }
        $secure | Export-Clixml $PasswordFile
        Write-Output 'Password validated and saved locally with Windows DPAPI.'
    } elseif ($Action -eq 'CopyPassword') {
        Set-Clipboard -Value $env:RELEASE_STORE_PASSWORD
        Write-Output 'Password copied locally. Save in Google Password Manager, then press Enter to clear this clipboard value.'
        try { Read-Host | Out-Null }
        finally {
            if ((Get-Clipboard -Raw) -eq $env:RELEASE_STORE_PASSWORD) { Set-Clipboard -Value '' }
        }
    } else {
        Push-Location "$repo/V6/NativeExpenseEntry"
        try {
            & ./gradlew.bat --no-daemon --console=plain assembleRelease
            if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
        } finally { Pop-Location }
        $buildTools = Get-ChildItem "$Sdk/build-tools" -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
        $apk = "$repo/V6/NativeExpenseEntry/app/build/outputs/apk/release/app-release.apk"
        $verification = & "$($buildTools.FullName)/apksigner.bat" verify --verbose --print-certs $apk 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'Release APK signature verification failed.' }
        $signers = @($verification | Select-String '^Signer #\d+ certificate SHA-256 digest: (\w+)$')
        if ($signers.Count -ne 1 -or $signers[0].Matches[0].Groups[1].Value.ToLowerInvariant() -ne $expected) { throw 'Release APK signer differs from published APK.' }
        $badging = & "$($buildTools.FullName)/aapt.exe" dump badging $apk 2>&1
        if ($LASTEXITCODE -ne 0 -or $badging[0] -notmatch "name='com.pocketledger.v6.preview'") { throw 'Release package differs from published package.' }
        Write-Output "PASS: release APK signature/package match published app. APK: $apk"
    }
} finally {
    Remove-Item Env:RELEASE_STORE_PASSWORD, Env:RELEASE_KEY_PASSWORD, Env:RELEASE_STORE_FILE, Env:RELEASE_KEY_ALIAS -ErrorAction SilentlyContinue
    $credential = $null
    $secure = $null
}
