# Set the output file name
$outputFile = "all-kotlin-files.txt"

# Clear the output file if it exists
if (Test-Path $outputFile) {
    Clear-Content $outputFile
}

# Write header
$header = @"
// ===== KOTLIN PROJECT FILES =====
// Generated on: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
// Current directory: $(Get-Location)

"@
$header | Out-File $outputFile -Encoding UTF8

# Get all Kotlin files recursively
$kotlinFiles = Get-ChildItem -Recurse -Filter "*.kt" | Sort-Object FullName

# Display summary
Write-Host "Found $($kotlinFiles.Count) Kotlin files" -ForegroundColor Green
Write-Host "Starting to combine files..." -ForegroundColor Yellow

# Process each file
$fileCounter = 0
$currentDir = (Get-Location).Path
foreach ($file in $kotlinFiles) {
    $fileCounter++
    
    # Calculate relative path
    $relativePath = $file.FullName.Replace("$currentDir\", "")
    
    # Get file info
    $lineCount = (Get-Content $file.FullName | Measure-Object -Line).Lines
    $fileSize = [math]::Round($file.Length / 1KB, 2)
    
    # Write file separator and info
    $fileHeader = @"

// ===== FILE: $relativePath =====
// Size: $fileSize KB | Lines: $lineCount
// Last modified: $($file.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss"))

"@
    $fileHeader | Out-File $outputFile -Append -Encoding UTF8
    
    # Write file content
    Get-Content $file.FullName -Raw | Out-File $outputFile -Append -Encoding UTF8
    
    # Add spacing between files
    "`n`n" | Out-File $outputFile -Append -Encoding UTF8
    
    # Show progress
    Write-Progress -Activity "Combining Kotlin files" -Status "Processing: $relativePath" -PercentComplete (($fileCounter / $kotlinFiles.Count) * 100)
}

# Write summary at the end
$summary = @"

// ===== END OF FILES =====
// Total files: $($kotlinFiles.Count)
// Total size: $([math]::Round((Get-ChildItem -Recurse -Filter "*.kt" | Measure-Object -Sum Length).Sum / 1MB, 2)) MB
// Generated file: $outputFile
"@
$summary | Out-File $outputFile -Append -Encoding UTF8

# Final output
Write-Host "`nCompleted!" -ForegroundColor Green
Write-Host "Combined $($kotlinFiles.Count) Kotlin files into: $outputFile" -ForegroundColor Cyan
Write-Host "Output file size: $([math]::Round((Get-Item $outputFile).Length / 1MB, 2)) MB" -ForegroundColor Cyan

# Optional: Show first few files that were included
Write-Host "`nFirst 5 files included:" -ForegroundColor Yellow
$kotlinFiles | Select-Object -First 5 | ForEach-Object {
    $relPath = $_.FullName.Replace("$currentDir\", "")
    Write-Host "  - $relPath"
}

# Ask if user wants to open the file
$response = Read-Host "`nDo you want to open the output file? (y/n)"
if ($response -eq 'y') {
    notepad $outputFile
}