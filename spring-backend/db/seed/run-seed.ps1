param(
    [string]$DatabaseHost = "localhost",
    [int]$Port = 5432,
    [string]$Database = "zimpic",
    [string]$Username = "zimpic_user"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    throw "psql was not found. Add the PostgreSQL bin directory to PATH."
}

$seedDirectory = $PSScriptRoot
$seedFiles = 1..9 | ForEach-Object {
    $prefix = $_.ToString("000")
    Get-ChildItem -LiteralPath $seedDirectory -Filter "${prefix}_*.sql" -File
}

if ($seedFiles.Count -ne 9) {
    throw "All Seed SQL files from 001 to 009 are required. Found: $($seedFiles.Count)"
}

$psqlArguments = @(
    "--host", $DatabaseHost,
    "--port", $Port,
    "--username", $Username,
    "--dbname", $Database,
    "--set", "ON_ERROR_STOP=1",
    "--single-transaction"
)

foreach ($seedFile in $seedFiles) {
    $psqlArguments += @("--file", $seedFile.FullName)
}

Write-Host "Loading Seed SQL files 001-009 into database '$Database'."

$previousClientEncoding = $env:PGCLIENTENCODING
$env:PGCLIENTENCODING = "UTF8"

try {
    & psql @psqlArguments
    $psqlExitCode = $LASTEXITCODE
}
finally {
    $env:PGCLIENTENCODING = $previousClientEncoding
}

if ($psqlExitCode -ne 0) {
    throw "Seed SQL execution failed. psql exit code: $psqlExitCode"
}

Write-Host "Seed data loaded successfully."
