$sourceDir = "c:\Users\GULSHAN KUMAR\AndroidStudioProjects\LeadDialerFlutter"
$targetZip = "c:\Users\GULSHAN KUMAR\AndroidStudioProjects\leaddiallerflutter.zip"
$tempDir = "c:\Users\GULSHAN KUMAR\AndroidStudioProjects\leaddiallerflutter_temp"

Write-Host "Running flutter clean..."
Set-Location $sourceDir
flutter clean

Write-Host "Creating staging directory..."
if (Test-Path $tempDir) { Remove-Item -Recurse -Force $tempDir }
New-Item -ItemType Directory -Path $tempDir | Out-Null

$itemsToCopy = @(
    "lib",
    "android",
    "ios",
    "assets",
    "pubspec.yaml",
    "pubspec.lock"
)

Write-Host "Copying required files..."
foreach ($item in $itemsToCopy) {
    $itemPath = Join-Path $sourceDir $item
    if (Test-Path $itemPath) {
        Copy-Item -Path $itemPath -Destination (Join-Path $tempDir $item) -Recurse -Force
    }
}

Write-Host "Removing unnecessary folders if present in staging..."
$foldersToRemove = @("build", ".gradle", ".dart_tool", ".idea", ".cxx")

# Remove from root of staging
foreach ($folder in $foldersToRemove) {
    $folderPath = Join-Path $tempDir $folder
    if (Test-Path $folderPath) {
        Remove-Item -Recurse -Force $folderPath
    }
}

# Recursively remove these folders from staging (e.g. android/.gradle)
foreach ($folder in $foldersToRemove) {
    Get-ChildItem -Path $tempDir -Recurse -Directory -Filter $folder | Remove-Item -Recurse -Force
}

Write-Host "Creating zip archive..."
if (Test-Path $targetZip) { Remove-Item -Force $targetZip }

# Compress-Archive creates standard ZIP (WinRAR/7-Zip compatible, UTF-8 encoded)
Compress-Archive -Path "$tempDir\*" -DestinationPath $targetZip -Force

Write-Host "Cleaning up staging directory..."
Set-Location "c:\Users\GULSHAN KUMAR\AndroidStudioProjects\LeadDialer"
Remove-Item -Recurse -Force $tempDir

Write-Host "Verifying ZIP file..."
if (Test-Path $targetZip) {
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::OpenRead($targetZip)
        $entryCount = $zip.Entries.Count
        Write-Host "ZIP file successfully created and verified at: $targetZip"
        Write-Host "Total files packed: $entryCount"
        $zip.Dispose()
    } catch {
        Write-Host "Warning: ZIP file created but could not be completely verified."
    }
} else {
    Write-Host "Error: ZIP file creation failed."
}

Write-Host "Done!"
