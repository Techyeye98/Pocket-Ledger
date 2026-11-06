# Exports the existing signing key; never generates or replaces a signing identity.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$PublishedApk,
    [Security.SecureString]$SourcePassword,
    [string]$JavaHome = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Sdk = "$env:USERPROFILE/AppData/Local/Android/Sdk",
    [string]$SecretsDirectory = "$env:USERPROFILE/AppData/Local/PocketLedger/SigningSecrets"
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot/../..").Path
$out = Join-Path $repo 'output/signing'
$backup = Join-Path $out 'Pocket-Ledger-Signing-Key.p12'
$passwordFile = Join-Path $SecretsDirectory 'preview-backup-password.dpapi.xml'
$keytool = Join-Path $JavaHome 'bin/keytool.exe'
$env:JAVA_HOME = $JavaHome
$buildTools = Get-ChildItem "$Sdk/build-tools" -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$signer = Join-Path $buildTools.FullName 'apksigner.bat'
$aapt = Join-Path $buildTools.FullName 'aapt.exe'
$expected = '91a0c436beb23b41465458f3e78502655b8b40db660ed01fc9c4176b21c857d6'
function Get-ApkCertificate([string]$Apk) {
    $result = & $signer verify --print-certs $Apk 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $certs = @($result | Select-String '^Signer #\d+ certificate SHA-256 digest: (\w+)$')
    if ($certs.Count -ne 1) { throw 'Expected exactly one APK signer.' }
    return $certs[0].Matches[0].Groups[1].Value.ToLowerInvariant()
}
if ((Test-Path $backup) -or (Test-Path $passwordFile)) { throw 'Backup/password already exists. Refusing to overwrite.' }
$PublishedApk = (Resolve-Path $PublishedApk).Path
if ((Get-ApkCertificate $PublishedApk) -ne $expected) { throw 'Published certificate differs. Stop and recover its original key.' }
$badging = & $aapt dump badging $PublishedApk 2>&1
if ($LASTEXITCODE -ne 0 -or $badging[0] -notmatch "name='com.pocketledger.v6.preview'") { throw 'Published package mismatch.' }
New-Item -ItemType Directory -Force $out, $SecretsDirectory | Out-Null
# Password persistence is Windows DPAPI, outside the repository and outside OneDrive.
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $SecretsDirectory /inheritance:r /grant:r "${identity}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict the password directory ACL.' }
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$password = [Convert]::ToBase64String($bytes)
$env:PL_BACKUP_PASSWORD = $password
if (!$SourcePassword) { $SourcePassword = Read-Host 'Existing signing keystore password' -AsSecureString }
$sourceCredential = New-Object System.Management.Automation.PSCredential('source', $SourcePassword)
$env:PL_SOURCE_PASSWORD = $sourceCredential.GetNetworkCredential().Password
try {
    $sourceKey = "$env:USERPROFILE/.android/debug.keystore"
    $export = & $keytool -importkeystore -srckeystore $sourceKey -srcstorepass:env PL_SOURCE_PASSWORD -srckeypass:env PL_SOURCE_PASSWORD -srcalias androiddebugkey -destkeystore $backup -deststoretype PKCS12 -deststorepass:env PL_BACKUP_PASSWORD -destkeypass:env PL_BACKUP_PASSWORD -destalias androiddebugkey -noprompt 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Key export failed; original key was not modified.' }
    # Persist immediately so an interrupted verification cannot orphan a usable backup.
    ConvertTo-SecureString $password -AsPlainText -Force | Export-Clixml $passwordFile
    & $keytool -exportcert -keystore $backup -storetype PKCS12 -storepass:env PL_BACKUP_PASSWORD -alias androiddebugkey -file "$out/backup-certificate.der" 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Correct password/certificate export failed.' }
    $fingerprint = (Get-FileHash "$out/backup-certificate.der" -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($fingerprint -ne $expected) { throw 'Backup certificate mismatch.' }
    $env:PL_WRONG_PASSWORD = [Guid]::NewGuid().ToString('N')
    & $keytool -list -keystore $backup -storetype PKCS12 -storepass:env PL_WRONG_PASSWORD 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { throw 'Wrong password unexpectedly accepted.' }
    & $signer sign --ks $backup --ks-type PKCS12 --ks-key-alias androiddebugkey --ks-pass env:PL_BACKUP_PASSWORD --key-pass env:PL_BACKUP_PASSWORD --out "$out/backup-signed-verification.apk" $PublishedApk 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Signing with the backup failed.' }
    if ((Get-ApkCertificate "$out/backup-signed-verification.apk") -ne $expected) { throw 'Backup-signed APK certificate mismatch.' }
    $report = [ordered]@{
        appName = 'Pocket Ledger'; packageId = 'com.pocketledger.v6.preview'
        alias = 'androiddebugkey'; certificateSha256 = $expected
        backupFile = $backup; passwordFile = $passwordFile
        backupSha256 = (Get-FileHash $backup -Algorithm SHA256).Hash.ToLowerInvariant()
        publishedApkSha256 = (Get-FileHash $PublishedApk -Algorithm SHA256).Hash.ToLowerInvariant()
        correctPasswordVerified = $true; wrongPasswordRejected = $true
        privateKeySigningVerified = $true; publishedCertificateMatched = $true
        verifiedAtUtc = [DateTime]::UtcNow.ToString('o')
    }
    $report | ConvertTo-Json | Set-Content "$out/verification.json" -Encoding UTF8
    Write-Output 'PASS: preserved certificate, correct/wrong password checks, private-key APK signing and published APK match.'
    Write-Output "Encrypted backup: $backup"
    Write-Output 'Password saved with Windows DPAPI. Use Use-ReleaseSigning.ps1 -Action CopyPassword to copy locally.'
} finally {
    Remove-Item Env:PL_BACKUP_PASSWORD, Env:PL_SOURCE_PASSWORD, Env:PL_WRONG_PASSWORD -ErrorAction SilentlyContinue
    $password = $null
    $sourceCredential = $null
    [Array]::Clear($bytes, 0, $bytes.Length)
}
