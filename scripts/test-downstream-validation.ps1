<#
.SYNOPSIS
  下游网关验证（GatewayValidationFilter）端到端测试脚本

.DESCRIPTION
  覆盖以下校验点：
    1. 直连下游无网关头 / 带任意 X-Internal-Service-Call 头 -> 403（P0 防绕过）
    2. 直连下游签名错误 -> 403
    3. 直连下游签名合法但无 token -> 401（下游二次登录态校验）
    4. 直连下游签名合法 + token -> 200
    5. 白名单路径（/auth/captcha）无需 token -> 200
    6. 非白名单路径（/auth/me）无 token -> 401
    7. 网关链路：无 token 401 / 验证码 200 / 登录 200 / 带 token 200 / CORS 预检 200

.PARAMETER SkipLogin
  跳过需要真实登录的用例（不请求验证码与登录），用于仅验证网关头/签名/登录拦截。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\test-downstream-validation.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\test-downstream-validation.ps1 -SkipLogin

.NOTES
  - 依赖 curl.exe 与本地 Redis（验证码从 Redis 读取）。
  - 每次执行会产生 1 次验证码与 1 次登录请求；连续高频执行可能触发限流（30 次/分钟）。
  - 退出码：0 = 全部通过，1 = 存在失败用例。
#>

[CmdletBinding()]
param(
    # 网关默认 HTTPS 8443（自签证书），可用 -GatewayHost http://127.0.0.1:8080 覆盖
    [string]$GatewayHost = "https://127.0.0.1:8443",
    [string]$AuthHost    = "127.0.0.1:8081",
    [string]$BusinessHost = "127.0.0.1:8082",
    [string]$GatewayName = "big-gateway",
    # 签名密钥：优先取环境变量 GATEWAY_VALIDATION_SECRET，避免默认密钥入库
    [string]$Secret      = $(if ($env:GATEWAY_VALIDATION_SECRET) { $env:GATEWAY_VALIDATION_SECRET } else { "dev-gateway-secret-do-not-use-in-prod" }),
    # 登录凭据：优先取环境变量，仅用于本地回归
    [string]$Username    = $(if ($env:TEST_USERNAME) { $env:TEST_USERNAME } else { "admin" }),
    [string]$Password    = $(if ($env:TEST_PASSWORD) { $env:TEST_PASSWORD } else { "admin123" }),
    # redis-cli 路径：优先取环境变量 REDIS_CLI
    [string]$RedisCli    = $(if ($env:REDIS_CLI) { $env:REDIS_CLI } else { "redis-cli" }),
    [string]$BusinessPath = "/product/page?pageNum=1&pageSize=5",
    [string]$Origin      = "http://localhost:5173",
    [switch]$SkipLogin
)

$ErrorActionPreference = "Stop"
$script:Pass = 0
$script:Fail = 0
$script:Token = $null

function Write-Banner([string]$text) {
    Write-Host ""
    Write-Host "== $text ==" -ForegroundColor Cyan
}

function Add-Result([string]$name, [string]$expected, [string]$actual) {
    $ok = ($expected -eq $actual)
    if ($ok) { $script:Pass++ } else { $script:Fail++ }
    $color = if ($ok) { "Green" } else { "Red" }
    $tag   = if ($ok) { "PASS" } else { "FAIL" }
    Write-Host ("[{0}] {1,-42} 期望={2,-4} 实际={3}" -f $tag, $name, $expected, $actual) -ForegroundColor $color
}

function Get-HttpCode([string]$url, [string[]]$headers, [string]$method = "GET") {
    # -k：跳过自签证书校验（网关默认 HTTPS 自签）
    $args = @("-s", "-k", "-o", "NUL", "-w", "%{http_code}", "-X", $method)
    if ($headers) { foreach ($h in $headers) { $args += @("-H", $h) } }
    $args += $url
    return (& curl.exe @args).Trim()
}

function Get-Body([string]$url, [string[]]$headers, [string]$method = "GET", [string]$dataFile) {
    $args = @("-s", "-k", "-X", $method)
    if ($headers) { foreach ($h in $headers) { $args += @("-H", $h) } }
    if ($dataFile) { $args += @("--data-binary", "@$dataFile") }
    $args += $url
    return (& curl.exe @args)
}

function New-GatewaySignHeaders([string]$path, [string]$method) {
    # 签名与网关一致：HmacSHA256(secret, gatewayName|timestamp|method|uri)
    # 注意：不要在数组字面量里混用 -f 与逗号，否则 PS5.1 会把数组折叠成单个字符串
    $ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $payload = "$GatewayName|$ts|$method|$path"
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes($Secret)
    $sig = [BitConverter]::ToString($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload))).Replace("-", "").ToLower()
    $trace = "test-$ts"
    return @(
        "X-Gateway-Name: $GatewayName"
        "X-Trace-Id: $trace"
        "X-Gateway-Timestamp: $ts"
        "X-Gateway-Sign: $sig"
    )
}

function Invoke-Login {
    Write-Banner "登录（经网关）"
    $capResp = Get-Body "$GatewayHost/auth/captcha"
    $capKey = ($capResp | ConvertFrom-Json).data.captchaKey
    if (-not $capKey) { throw "获取验证码失败：$capResp" }
    $capCode = (& $RedisCli get "auth:captcha:$capKey").Trim()
    if (-not $capCode) { throw "Redis 中未找到验证码 auth:captcha:$capKey" }

    $loginBody = @{
        username    = $Username
        password    = $Password
        captchaKey  = $capKey
        captchaCode = $capCode
    } | ConvertTo-Json -Compress

    $tmpFile = Join-Path $env:TEMP "bigx-login.json"
    [System.IO.File]::WriteAllText($tmpFile, $loginBody, (New-Object System.Text.UTF8Encoding($false)))

    $loginResp = Get-Body "$GatewayHost/auth/login" @("Content-Type: application/json") "POST" $tmpFile
    Remove-Item $tmpFile -ErrorAction SilentlyContinue
    $loginJson = $loginResp | ConvertFrom-Json
    if ($loginJson.code -ne "200" -and $loginJson.code -ne 200) { throw "登录失败：$loginResp" }
    $script:Token = $loginJson.data.token
    Add-Result "登录接口" "200" "$($loginJson.code)"
}

try {
    $businessUriPath = $BusinessPath.Split("?")[0]

    Write-Banner "1. 网关头 / 内部调用头（直连 $BusinessHost）"
    Add-Result "直连无网关头" "403" (Get-HttpCode "http://$BusinessHost$BusinessPath")
    Add-Result "直连带伪造内部头" "403" (Get-HttpCode "http://$BusinessHost$BusinessPath" @("X-Internal-Service-Call: anything"))

    Write-Banner "2. 网关签名（直连 $BusinessHost）"
    Add-Result "签名错误" "403" (Get-HttpCode "http://$BusinessHost$BusinessPath" @(
        "X-Gateway-Name: $GatewayName", "X-Trace-Id: test-bad", "X-Gateway-Timestamp: 0", "X-Gateway-Sign: wrongsig"))
    Add-Result "合法签名但无 token" "401" (Get-HttpCode "http://$BusinessHost$BusinessPath" (New-GatewaySignHeaders $businessUriPath "GET"))

    Write-Banner "3. 白名单 / 登录拦截（直连 $AuthHost）"
    Add-Result "白名单 /auth/captcha 无 token" "200" (Get-HttpCode "http://$AuthHost/auth/captcha" (New-GatewaySignHeaders "/auth/captcha" "GET"))
    Add-Result "非白名单 /auth/me 无 token" "401" (Get-HttpCode "http://$AuthHost/auth/me" (New-GatewaySignHeaders "/auth/me" "GET"))

    Write-Banner "4. 网关链路（经 $GatewayHost）"
    Add-Result "网关无 token 访问业务接口" "401" (Get-HttpCode "$GatewayHost/api/product/page?pageNum=1&pageSize=5")
    Add-Result "网关验证码接口" "200" (Get-HttpCode "$GatewayHost/auth/captcha")
    Add-Result "网关 CORS 预检" "200" (Get-HttpCode "$GatewayHost/api/product/page" @("Origin: $Origin", "Access-Control-Request-Method: GET") "OPTIONS")

    if (-not $SkipLogin) {
        Invoke-Login
        Add-Result "直连 合法签名+token" "200" (Get-HttpCode "http://$BusinessHost$BusinessPath" ((New-GatewaySignHeaders $businessUriPath "GET") + @("Authorization: $script:Token")))
        Add-Result "网关带 token 访问业务接口" "200" (Get-HttpCode "$GatewayHost/api/product/page?pageNum=1&pageSize=5" @("Authorization: $script:Token"))
    }
    else {
        Write-Host "（已跳过需要登录的用例：直连+token、网关+token、登录）"
    }

    Write-Banner "结果汇总"
    Write-Host ("通过 {0} 项，失败 {1} 项" -f $script:Pass, $script:Fail) -ForegroundColor ($(if ($script:Fail -eq 0) { "Green" } else { "Red" }))
    exit $(if ($script:Fail -eq 0) { 0 } else { 1 })
}
catch {
    Write-Host "脚本异常：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
}