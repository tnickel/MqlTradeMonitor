# Connection Diagnostics for TradeMonitor
# Run this script to troubleshoot connection issues between this PC and the TradeMonitor server.

$domains = @("monitor.tnickel-ki.de", "monitor.ki-software-schmiede.de")
$ipv4_target = "84.46.247.222"
$ipv6_target = "2a02:c207:3019:2495::1"
$port = 443

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Starting TradeMonitor Connection Diagnostics" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# 1. Check DNS Resolution
Write-Host "[1] Checking DNS Resolution..." -ForegroundColor Yellow
foreach ($domain in $domains) {
    Write-Host "Resolving $domain... " -NoNewline
    try {
        $ips = [System.Net.Dns]::GetHostAddresses($domain)
        $ipstrings = $ips | ForEach-Object { $_.IPAddressToString }
        Write-Host "OK ($($ipstrings -join ', '))" -ForegroundColor Green
    }
    catch {
        Write-Host "FAILED ($($_.Exception.Message))" -ForegroundColor Red
    }
}
Write-Host ""

# 2. Check Network Connection (IPv4 and IPv6) on Port 443
Write-Host "[2] Checking TCP Port $port Connection..." -ForegroundColor Yellow

# IPv4 Test
Write-Host "Connecting to IPv4 ($ipv4_target) on port $port... " -NoNewline
try {
    $client = New-Object System.Net.Sockets.TcpClient
    $connect = $client.BeginConnect($ipv4_target, $port, $null, $null)
    $success = $connect.AsyncWaitHandle.WaitOne(3000, $false)
    if ($success) {
        $client.EndConnect($connect)
        Write-Host "SUCCESS" -ForegroundColor Green
    } else {
        Write-Host "FAILED (Timeout after 3s)" -ForegroundColor Red
    }
    $client.Close()
}
catch {
    Write-Host "FAILED ($($_.Exception.Message))" -ForegroundColor Red
}

# IPv6 Test
Write-Host "Connecting to IPv6 ($ipv6_target) on port $port... " -NoNewline
try {
    $client = New-Object System.Net.Sockets.TcpClient([System.Net.Sockets.AddressFamily]::InterNetworkV6)
    $connect = $client.BeginConnect($ipv6_target, $port, $null, $null)
    $success = $connect.AsyncWaitHandle.WaitOne(3000, $false)
    if ($success) {
        $client.EndConnect($connect)
        Write-Host "SUCCESS" -ForegroundColor Green
    } else {
        Write-Host "FAILED (Timeout after 3s)" -ForegroundColor Red
        Write-Host "  -> Note: If IPv4 works but IPv6 fails, Windows might default to IPv6 and cause MetaTrader errors (5203)." -ForegroundColor DarkYellow
    }
    $client.Close()
}
catch {
    Write-Host "FAILED ($($_.Exception.Message))" -ForegroundColor Red
    Write-Host "  -> Note: If IPv4 works but IPv6 fails, Windows might default to IPv6 and cause MetaTrader errors (5203)." -ForegroundColor DarkYellow
}
Write-Host ""

# 3. Test API Endpoint Accessibility & Redirection
Write-Host "[3] Checking API Endpoint behavior..." -ForegroundColor Yellow
foreach ($domain in $domains) {
    $url = "https://$domain/api/heartbeat"
    Write-Host "Sending POST to $url... " -NoNewline
    
    # We send a dummy payload that should fail authorization, but not redirect.
    $body = @{ accountId = 999999; version = "1.11"; timestamp = "2026.07.16 12:00:00" } | ConvertTo-Json
    
    try {
        # We use a custom SessionVariable to check redirects, or manually catch the web response
        $r = Invoke-WebRequest -Uri $url -Method POST -Body $body -ContentType "application/json" -UseBasicParsing -MaximumRedirection 0 -ErrorAction SilentlyContinue
        
        # If it returned a success code or redirect code
        $statusCode = $r.StatusCode
        $location = $r.Headers.Location
        $contentType = $r.Headers["Content-Type"]
    }
    catch {
        # Catch exceptions (like 401/404/500 which throw in PowerShell, but are acceptable)
        $statusCode = $_.Exception.Response.StatusCode.value__
        $contentType = $_.Exception.Response.Headers["Content-Type"]
        $location = $_.Exception.Response.Headers.Location
    }
    
    if ($statusCode -eq 302 -or $statusCode -eq 301 -or ($location -and $location.Contains("/login"))) {
        Write-Host "CRITICAL REDIRECT DETECTED" -ForegroundColor Red
        Write-Host "  -> Server redirected to: $location" -ForegroundColor Red
        Write-Host "  -> FAILURE: Spring Security is blocking anonymous API access and redirecting to the login page!" -ForegroundColor Red
    }
    elseif ($contentType -and $contentType.Contains("text/html")) {
        Write-Host "HTML RESPONSE DETECTED" -ForegroundColor Red
        Write-Host "  -> FAILURE: Server returned HTML page instead of API response. This will cause MetaTrader to fail." -ForegroundColor Red
    }
    else {
        if ($statusCode -eq 401 -or $statusCode -eq 400 -or $statusCode -eq 404 -or $statusCode -eq 200) {
            Write-Host "OK (HTTP $statusCode)" -ForegroundColor Green
        } else {
            Write-Host "WARNING (HTTP $statusCode)" -ForegroundColor Yellow
        }
    }
}
Write-Host ""
Write-Host "Diagnostics complete." -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
