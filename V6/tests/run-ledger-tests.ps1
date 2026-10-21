param(
    [string]$Jdk = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$Sdk = "$env:USERPROFILE/AppData/Local/Android/Sdk"
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot/../NativeExpenseEntry").Path
$classes = "$projectRoot/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"
$out = "$projectRoot/app/build/ledger-tests"
New-Item -ItemType Directory -Force -Path "$out/json", "$out/classes" | Out-Null
$sourceRoot = Get-ChildItem -LiteralPath "$Sdk/sources" -Directory | Sort-Object Name -Descending | Where-Object { Test-Path "$($_.FullName)/org/json/JSONObject.java" } | Select-Object -First 1
if (!$sourceRoot) { throw 'Install Android SDK sources to run the platform JSON implementation in these JVM tests.' }
foreach ($source in Get-ChildItem -LiteralPath "$($sourceRoot.FullName)/org/json" -Filter '*.java') {
    $code = Get-Content -Raw -LiteralPath $source.FullName
    $code = $code -replace '(?m)^import (?:static )?(?:android|libcore)\.[^;]+;\s*', ''
    $code = $code -replace '@(?:NonNull|Nullable|UnsupportedAppUsage|SystemApi)(?:\([^)]*\))?', ''
    [IO.File]::WriteAllText("$out/json/$($source.Name)", $code, [Text.UTF8Encoding]::new($false))
}
$sources = @(Get-ChildItem -LiteralPath "$out/json" -Filter '*.java' | Select-Object -ExpandProperty FullName)
$sources += "$PSScriptRoot/LedgerTest.java"
$sources += "$PSScriptRoot/SpendingInsightsTest.java"
$sources += "$PSScriptRoot/BillSplitTest.java"
$sources += "$PSScriptRoot/ReceiptParserTest.java"
$sources += "$PSScriptRoot/BackupCompatibilityTest.java"
& "$Jdk/bin/javac.exe" -encoding UTF-8 -cp "$classes;$Sdk/platforms/android-36/android.jar" -d "$out/classes" $sources
if ($LASTEXITCODE -ne 0) { throw 'Ledger tests failed to compile' }
& "$Jdk/bin/java.exe" -cp "$out/classes;$classes;$Sdk/platforms/android-36/android.jar" com.pocketledger.v2.LedgerTest
if ($LASTEXITCODE -ne 0) { throw 'Ledger calculation tests failed' }
& "$Jdk/bin/java.exe" -cp "$out/classes;$classes" com.pocketledger.v2.SpendingInsightsTest
if ($LASTEXITCODE -ne 0) { throw 'Spending insight regression tests failed' }
& "$Jdk/bin/java.exe" -cp "$out/classes;$classes;$Sdk/platforms/android-36/android.jar" com.pocketledger.v2.BillSplitTest
if ($LASTEXITCODE -ne 0) { throw 'Standalone bill split tests failed' }
& "$Jdk/bin/java.exe" -cp "$out/classes;$classes;$Sdk/platforms/android-36/android.jar" com.pocketledger.v2.ReceiptParserTest
if ($LASTEXITCODE -ne 0) { throw 'Receipt parsing tests failed' }
& "$Jdk/bin/java.exe" -cp "$out/classes;$classes;$Sdk/platforms/android-36/android.jar" com.pocketledger.v2.BackupCompatibilityTest
if ($LASTEXITCODE -ne 0) { throw 'Financial backup compatibility tests failed' }
