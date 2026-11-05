param(
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$Project = 'D:\java_heima_learning\hm-dianping',
    [int]$Count = 500
)
$ErrorActionPreference = 'Stop'
if ($Count -lt 1 -or $Count -gt 500) { throw 'Count must be 1..500' }
# Read this project's simple Redis YAML section; do not change application config.
$config = Get-Content -LiteralPath (Join-Path $Project 'src\main\resources\application.yaml') -Raw
$section = [regex]::Match($config, '(?ms)^  redis:\s*\r?\n(.*?)(?=^  \w|\z)').Groups[1].Value
$redisHost = [regex]::Match($section, '(?m)^    host:\s*([^\r\n#]+)').Groups[1].Value.Trim()
$redisPort = [int][regex]::Match($section, '(?m)^    port:\s*(\d+)').Groups[1].Value
$redisPassword = [regex]::Match($section, '(?m)^    password:\s*([^\r\n#]+)').Groups[1].Value.Trim().Trim('"').Trim("'")
$databaseMatch = [regex]::Match($section, '(?m)^    database:\s*(\d+)')
if (!$redisHost -or !$redisPort) { throw 'Cannot read Redis host/port from application.yaml' }

function Read-RedisLine($Stream) {
    $bytes = [System.Collections.Generic.List[byte]]::new()
    while ($true) {
        $b = $Stream.ReadByte()
        if ($b -lt 0) { throw 'Redis closed connection' }
        if ($b -eq 13) {
            if ($Stream.ReadByte() -ne 10) { throw 'Invalid Redis reply' }
            return [Text.Encoding]::UTF8.GetString($bytes.ToArray())
        }
        $bytes.Add([byte]$b)
    }
}
function Send-Redis($Stream, [string[]]$Parts) {
    $wire = '*' + $Parts.Count + "`r`n"
    foreach ($part in $Parts) {
        $wire += '$' + [Text.Encoding]::UTF8.GetByteCount($part) + "`r`n" + $part + "`r`n"
    }
    $bytes = [Text.Encoding]::UTF8.GetBytes($wire)
    $Stream.Write($bytes, 0, $bytes.Length)
    $line = Read-RedisLine $Stream
    switch ($line.Substring(0, 1)) {
        '+' { return $line.Substring(1) }
        '-' { throw $line.Substring(1) }
        '$' {
            $length = [int]$line.Substring(1)
            if ($length -eq -1) { return $null }
            $data = New-Object byte[] $length
            $offset = 0
            while ($offset -lt $length) {
                $read = $Stream.Read($data, $offset, $length - $offset)
                if ($read -le 0) { throw 'Incomplete Redis reply' }
                $offset += $read
            }
            if ($Stream.ReadByte() -ne 13 -or $Stream.ReadByte() -ne 10) { throw 'Invalid Redis terminator' }
            return [Text.Encoding]::UTF8.GetString($data)
        }
        default { throw "Unexpected Redis reply: $line" }
    }
}
function Invoke-Api([string]$Method, [string]$Path, $Body = $null, $Headers = @{}) {
    $params = @{ Uri=$BaseUrl.TrimEnd('/')+$Path; Method=$Method; TimeoutSec=15; Headers=$Headers }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Compress); $params.ContentType='application/json' }
    $response = Invoke-RestMethod @params
    if ($response.success -ne $true) { throw "API error: $($response.errorMsg)" }
    return $response.data
}
$tcp = [Net.Sockets.TcpClient]::new()
$results = [System.Collections.Generic.List[object]]::new()
$output = Join-Path $PSScriptRoot ('output\' + (Get-Date -Format 'yyyyMMdd_HHmmss_fff'))
New-Item -ItemType Directory -Path $output -Force | Out-Null
try {
    $pending = $tcp.ConnectAsync($redisHost, $redisPort)
    if (!$pending.Wait(5000)) { throw 'Redis connect timeout' }
    $stream = $tcp.GetStream()
    $stream.ReadTimeout = 5000
    $stream.WriteTimeout = 5000
    if ($redisPassword) { $null = Send-Redis $stream @('AUTH', $redisPassword) }
    if ($databaseMatch.Success) { $null = Send-Redis $stream @('SELECT', $databaseMatch.Groups[1].Value) }
    for ($i = 0; $i -lt $Count; $i++) {
        $phone = (13990000000L + $i).ToString()
        $null = Invoke-Api 'POST' ('/user/code?phone=' + $phone)
        $code = Send-Redis $stream @('GET', ('login:code:' + $phone))
        if (!$code) { throw "No verification code for $phone" }
        $token = Invoke-Api 'POST' '/user/login' @{phone=$phone; code=$code}
        if (!$token) { throw "No token for $phone" }
        $me = Invoke-Api 'GET' '/user/me' $null @{authorization=$token}
        if (!$me.id -or $me.nickName -ne ('loadtest_' + $phone)) { throw "Token identity verification failed for $phone" }
        $results.Add([pscustomobject]@{phone=$phone; id=$me.id; token=$token})
        if (($i + 1) % 50 -eq 0) { Write-Host "Verified $($i + 1)/$Count tokens" }
    }
} finally {
    $tcp.Dispose()
    $utf8 = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllLines((Join-Path $output 'tokens.txt'), [string[]]@($results | ForEach-Object {$_.token}), $utf8)
    $results | Export-Csv -LiteralPath (Join-Path $output 'tokens.csv') -NoTypeInformation -Encoding UTF8
    ConvertTo-Json -InputObject @($results.ToArray()) -Depth 4 | Set-Content -LiteralPath (Join-Path $output 'tokens.json') -Encoding UTF8
    Write-Host "Saved $($results.Count) verified tokens to $output"
}
