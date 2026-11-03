param(
    [ValidateSet('test','serve')][string]$Mode = 'test',
    [int]$Port = 33317,
    [int]$ApiPort = 18081,
    [string]$JavaHome = 'C:\D\tools\jdk17',
    [string]$MySqlBin = ''
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (!$MySqlBin) { $MySqlBin = Split-Path (Get-Command mysql.exe -ErrorAction Stop).Source }
$mysqld = Join-Path $MySqlBin 'mysqld.exe'
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw "Port $Port is already in use" }
$runDir = Join-Path ([System.IO.Path]::GetTempPath()) ('zhixing-mysql-qa-' + [guid]::NewGuid().ToString('N'))
$dataDir = Join-Path $runDir 'data'
New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
$initFile = Join-Path $runDir 'bootstrap.sql'
$dbPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
@"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$dbPassword';
CREATE DATABASE training_qa CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
"@ | Set-Content -LiteralPath $initFile -Encoding utf8NoBOM
$server = $null
$exitStatus = 1
try {
    & $mysqld --no-defaults --initialize-insecure "--datadir=$dataDir" "--log-error=$runDir\mysql.log"
    if ($LASTEXITCODE -ne 0) { throw "MySQL initialization failed; see $runDir\mysql.log" }
    $server = Start-Process -FilePath $mysqld -WindowStyle Hidden -PassThru -ArgumentList @('--no-defaults', ('--datadir="' + $dataDir + '"'), "--port=$Port", '--bind-address=127.0.0.1', '--mysqlx=0', ('--init-file="' + $initFile + '"'), ('--log-error="' + $runDir + '\mysql.log"'))
    $ready = $false
    $env:MYSQL_PWD=$dbPassword
    for ($i=0; $i -lt 60; $i++) {
        if ($server.HasExited) { throw "MySQL exited; see $runDir\mysql.log" }
        & (Join-Path $MySqlBin 'mysql.exe') --no-defaults --host=127.0.0.1 "--port=$Port" --user=root --database=training_qa --execute='SELECT 1' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Milliseconds 500
    }
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    if (!$ready) { throw 'Isolated MySQL startup timed out' }
    Remove-Item -LiteralPath $initFile
    $env:JAVA_HOME=$JavaHome
    $env:TRAINING_DB_USER='root'
    $env:TRAINING_DB_PASSWORD=$dbPassword
    $env:TRAINING_DB_URL="jdbc:mysql://127.0.0.1:$Port/training_qa?serverTimezone=UTC&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true"
    $env:TRAINING_TEST_DB_URL=$env:TRAINING_DB_URL
    $env:SERVER_PORT="$ApiPort"
    $env:TRAINING_STORAGE_ROOT=Join-Path $runDir 'uploads'
    Write-Host "Isolated MySQL on 127.0.0.1:$Port; data: $dataDir"
    Push-Location $project
    try {
        if ($Mode -eq 'test') { & mvn -q test } else { $env:SPRING_PROFILES_ACTIVE='local'; & mvn -q spring-boot:run }
        $exitStatus=$LASTEXITCODE
    } finally { Pop-Location }
} finally {
    if ($ready -and $server) {
        $env:MYSQL_PWD=$dbPassword
        & (Join-Path $MySqlBin 'mysqladmin.exe') --no-defaults --host=127.0.0.1 "--port=$Port" --user=root shutdown
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
    if ($server -and !$server.HasExited) { Stop-Process -Id $server.Id -Force }
    if (Test-Path -LiteralPath $initFile) { Remove-Item -LiteralPath $initFile }
    Remove-Item Env:TRAINING_DB_PASSWORD -ErrorAction SilentlyContinue
}
exit $exitStatus
