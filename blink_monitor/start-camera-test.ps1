param(
  [ValidateRange(10,300)][int]$Duration = 60,
  [ValidateSet('long_close','triple_blink')][string]$Gesture = 'triple_blink',
  [switch]$NoPreview,
  [string]$SpeakerPort = 'COM9',
  [ValidateRange(1,30)][int]$SpeakerVolume = 30,
  [switch]$NoSpeaker,
  [switch]$SkipUpload
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonExe = Join-Path $projectRoot '.venv-blink\Scripts\python.exe'
$cameraProfile = 'Wheelchair-Blink-Camera-Test'
$interfaceName = 'Wi-Fi'
$wlan = (netsh wlan show interfaces) -join "`n"
$profileMatch = [regex]::Match($wlan, '(?m)^\s*Profile\s*:\s*(.+?)\s*$')
if (!$profileMatch.Success) { throw 'Connect the computer to its usual Wi-Fi first so it can be restored afterward.' }
$previousProfile = $profileMatch.Groups[1].Value
$localFolder = Join-Path $PSScriptRoot 'local'
New-Item -ItemType Directory -Path $localFolder -Force | Out-Null
$profileFile = Join-Path $localFolder 'camera-profile.xml'
@'
<?xml version="1.0"?>
<WLANProfile xmlns="http://www.microsoft.com/networking/WLAN/profile/v1">
 <name>Wheelchair-Blink-Camera-Test</name>
 <SSIDConfig><SSID><name>ESP32-CAM-MB</name></SSID></SSIDConfig>
 <connectionType>ESS</connectionType><connectionMode>manual</connectionMode>
 <MSM><security><authEncryption><authentication>open</authentication><encryption>none</encryption><useOneX>false</useOneX></authEncryption></security></MSM>
</WLANProfile>
'@ | Set-Content -LiteralPath $profileFile -Encoding UTF8
$testFailed = $false
try {
  netsh wlan add profile filename="$profileFile" interface="$interfaceName" user=current
  if ($LASTEXITCODE -ne 0) { throw 'Could not add camera Wi-Fi profile.' }
  netsh wlan connect name="$cameraProfile" ssid='ESP32-CAM-MB' interface="$interfaceName"
  if ($LASTEXITCODE -ne 0) { throw 'Could not connect to camera Wi-Fi.' }
  $deadline = [DateTime]::UtcNow.AddSeconds(20)
  $connected = $false
  do {
    Start-Sleep -Milliseconds 700
    $state = (netsh wlan show interfaces) -join "`n"
    if ($state -match '(?m)^\s*SSID\s*:\s*ESP32-CAM-MB\s*$') {
      $network = Get-NetIPConfiguration -InterfaceAlias $interfaceName
      if ($network.IPv4DefaultGateway.NextHop -eq '192.168.4.1') { $connected = $true; break }
    }
  } while ([DateTime]::UtcNow -lt $deadline)
  if (!$connected) { throw 'Camera Wi-Fi did not become ready.' }
  $cameraArgs = @((Join-Path $PSScriptRoot 'monitor.py'), '--duration', "$Duration", '--gesture', $Gesture)
  if (!$NoPreview) { $cameraArgs += '--preview' }
  if (!$NoSpeaker) { $cameraArgs += @('--speaker-port', $SpeakerPort, '--speaker-volume', "$SpeakerVolume") }
  & $pythonExe @cameraArgs | Tee-Object -FilePath (Join-Path $localFolder 'last-session.log')
  if ($LASTEXITCODE -ne 0) { $testFailed = $true }
} finally {
  netsh wlan connect name="$previousProfile" interface="$interfaceName"
  Start-Sleep -Seconds 5
  netsh wlan delete profile name="$cameraProfile" interface="$interfaceName"
  'Requested restoration of previous Wi-Fi. Uploading queued test events next.'
}
if (!$SkipUpload) {
  & $pythonExe (Join-Path $PSScriptRoot 'outbox.py')
  if ($LASTEXITCODE -ne 0) { Write-Warning 'Events remain saved locally. Run outbox.py after internet is available.' }
} else { 'Test events remain queued for manual upload.' }
if ($testFailed) { throw 'Camera/model test failed; see local/last-session.log.' }
