# tradepost Windows build (no Gradle/Loom).
# Compiles the intermediary-named sources directly against the Fabric client-intermediary jar,
# then packages build/libs/tradepost-<version>.jar (fabric.mod.json version substituted).
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\build-windows.ps1
#
# Requirements: JDK 21, a Fabric 1.21.1 instance with fabric-api installed.
param(
    [string]$Instance = 'D:\Minecraft\.minecraft\versions\1.21.1-Fabric 0.19.5',
    [string]$Jdk      = 'C:\Program Files\Java\jdk-21',
    [string]$Libs     = 'D:\Minecraft\.minecraft\libraries'
)
$ErrorActionPreference = 'Stop'
$ROOT  = Split-Path -Parent $PSScriptRoot
$JAVAC = Join-Path $Jdk 'bin\javac.exe'
$JAR   = Join-Path $Jdk 'bin\jar.exe'

$MC     = Join-Path $Instance '.fabric\remappedJars\minecraft-1.21.1-0.19.5\client-intermediary.jar'
$LOADER = Join-Path $Libs 'net\fabricmc\fabric-loader\0.19.5\fabric-loader-0.19.5.jar'
$MIXIN  = Join-Path $Libs 'net\fabricmc\sponge-mixin\0.17.3+mixin.0.8.7\sponge-mixin-0.17.3+mixin.0.8.7.jar'
$BRIG   = Join-Path $Libs 'com\mojang\brigadier\1.3.10\brigadier-1.3.10.jar'
$DFU    = Join-Path $Libs 'com\mojang\datafixerupper\8.0.16\datafixerupper-8.0.16.jar'
$GSON   = Join-Path $Libs 'com\google\code\gson\gson\2.10.1\gson-2.10.1.jar'
$SLF4J  = Join-Path $Libs 'org\slf4j\slf4j-api\2.0.9\slf4j-api-2.0.9.jar'
$NETTY  = @(
    (Get-ChildItem (Join-Path $Libs 'io\netty\netty-buffer') -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
    (Get-ChildItem (Join-Path $Libs 'io\netty\netty-common') -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
) -join ';'

foreach ($f in @($MC, $LOADER, $MIXIN, $BRIG, $DFU, $GSON, $SLF4J)) {
    if (-not (Test-Path $f)) { throw "missing dependency: $f" }
}

$fabricMods = (Get-ChildItem (Join-Path $Instance '.fabric\processedMods') -Filter 'fabric-*.jar' |
    ForEach-Object { $_.FullName }) -join ';'
$apiBundle = Get-ChildItem (Join-Path $Instance 'mods') -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue |
    Select-Object -First 1
$apiBundle = if ($apiBundle) { $apiBundle.FullName } else { '' }

$jadeApi = Join-Path $Root 'libs\jade-api.jar'
if (-not (Test-Path $jadeApi)) { $jadeApi = '' }
$cp = @($MC, $LOADER, $MIXIN, $BRIG, $DFU, $GSON, $SLF4J, $NETTY, $fabricMods, $apiBundle, $jadeApi) -join ';'
$OUT = Join-Path $ROOT 'build\classes'
$LIBDIR = Join-Path $ROOT 'build\libs'
if (-not (Test-Path $OUT)) { New-Item -ItemType Directory -Force -Path $OUT | Out-Null } else { Get-ChildItem $OUT -Recurse -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Force -Path $LIBDIR | Out-Null

$sources  = Get-ChildItem (Join-Path $ROOT 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$listFile = Join-Path $env:TEMP 'tradepost-sources.txt'
[System.IO.File]::WriteAllLines($listFile, $sources, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ("compiling {0} files" -f $sources.Count)
& $JAVAC -encoding UTF-8 --release 21 -nowarn -Xlint:-options -proc:none -cp $cp -d $OUT "@$listFile"
if ($LASTEXITCODE -ne 0) { throw ("javac failed, exit {0}" -f $LASTEXITCODE) }

Copy-Item (Join-Path $ROOT 'src\main\resources\*') $OUT -Recurse -Force
$version = (Select-String -Path (Join-Path $ROOT 'gradle.properties') -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value.Trim()
$fmj = Join-Path $OUT 'fabric.mod.json'
(Get-Content $fmj -Raw -Encoding UTF8).Replace('${version}', $version) | Set-Content $fmj -Encoding UTF8 -NoNewline

$jarName = Join-Path $LIBDIR ("tradepost-{0}.jar" -f $version)
if (Test-Path $jarName) { Remove-Item $jarName -Force }
& $JAR --create --file $jarName -C $OUT .
if ($LASTEXITCODE -ne 0) { throw ("jar failed, exit {0}" -f $LASTEXITCODE) }

Write-Host ("OK: {0} ({1} classes)" -f $jarName, (Get-ChildItem $OUT -Recurse -Filter *.class).Count)
