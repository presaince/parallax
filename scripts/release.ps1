# Build, sign and publish the release APK in one command.
#
#   .\scripts\release.ps1 -Notes "One line per row|Second line"
#
# Bumps the version, assembles, signs, writes version.json, deploys, then checks that the
# live manifest names the code it just built. The release certificate is the shared
# "Ukraine Drones" key (see ROADMAP): its untracked keystore.properties is read at run time
# and nothing from it is printed or copied into this repo.
param(
    [Parameter(Mandatory = $true)][string]$Notes,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Step([string]$label, [scriptblock]$body) {
    Write-Host "== $label"
    & $body
    if ($LASTEXITCODE -ne 0) { throw "$label failed (exit $LASTEXITCODE)" }
}

Step 'bump' { node scripts/bump-version.mjs --apply }

Step 'assemble' { .\gradlew.bat :app:assembleRelease }

Step 'sign' {
    $propsPath = Join-Path $root 'app\keystore.properties'
    if (-not (Test-Path $propsPath)) {
        throw "No app\keystore.properties. Release signing reads PARALLAX_KEYSTORE* from it or the environment."
    }
    $p = @{}
    Get-Content $propsPath | ForEach-Object {
        if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $p[$matches[1]] = $matches[2].Trim() }
    }
    $ks = $p['PARALLAX_KEYSTORE']
    if (-not [System.IO.Path]::IsPathRooted($ks)) {
        $ks = Join-Path (Split-Path -Parent $propsPath) $ks
    }
    $buildTools = Join-Path $env:ANDROID_HOME 'build-tools'
    $apksigner = (Get-ChildItem $buildTools -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1).FullName + '\apksigner.bat'
    & $apksigner sign `
        --ks $ks `
        --ks-key-alias $p['PARALLAX_KEY_ALIAS'] `
        --ks-pass ("pass:" + $p['PARALLAX_KEYSTORE_PASSWORD']) `
        --key-pass ("pass:" + $p['PARALLAX_KEY_PASSWORD']) `
        --out app\build\outputs\apk\release\app-release.apk `
        app\build\outputs\apk\release\app-release-unsigned.apk
}

Step 'manifest' { node scripts/make-version-json.mjs --notes ($Notes -replace '\|', "`n") }

$deployArgs = @('-C', $root, '--config=deploy-apk.properties')
if ($DryRun) { $deployArgs += '--dry-run' }
node "$env:USERPROFILE\.config\opencode\scripts\deploy\deploy.mjs" @deployArgs
if ($LASTEXITCODE -ne 0) { throw "deploy failed (exit $LASTEXITCODE)" }

if ($DryRun) {
    Write-Host '== dry run: nothing was published'
    return
}

Write-Host '== verify'
$meta = Get-Content 'app\build\outputs\apk\release\output-metadata.json' -Raw | ConvertFrom-Json
$built = $meta.elements[0]
$publicBase = (Get-Content 'deploy-apk.properties' |
    Where-Object { $_ -match '^publicBase=' }) -replace '^publicBase=', ''
$publicBase = $publicBase.Trim()
$live = Invoke-RestMethod ("$publicBase" + 'version.json?_=' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
if ($live.versionCode -ne $built.versionCode) {
    throw "live versionCode $($live.versionCode) is not the built $($built.versionCode)"
}
Write-Host "live  $publicBase$($built.versionName) (code $($built.versionCode))"
