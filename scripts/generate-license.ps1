# 许可证签发脚本（签发机使用）
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\generate-license.ps1 -Customer "客户名" -Expire 2027-12-31 [-Mac "AA-BB-CC-DD-EE-FF,11:22:33:44:55:66"]
# 说明：
#   1. 首次运行会生成密钥对（privateKeys.store / publicCerts.store），私钥库仅存签发机；
#   2. 口令不传入时会安全提示输入；也可通过环境变量 BIG_LICENSE_KEY_PWD / BIG_LICENSE_STORE_PWD 提供；
#   3. 产出 ./license/license.lic 与 ./license/publicCerts.store，随服务分发；privateKeys.store 禁止分发。
param(
    [Parameter(Mandatory = $true)][string]$Customer,
    [Parameter(Mandatory = $true)][string]$Expire,
    [string]$Mac = "",
    [string]$Subject = "bigx",
    [string]$OutDir = "license",
    [string]$Issued = ""
)

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

# 1. 确保 big-license 已编译
if (-not (Test-Path "big-license\target\classes\com\cqcnihao\bigx\license\tool\LicenseGenerator.class")) {
    Write-Host "[build] 编译 big-license（首次需联网拉取 truelicense 依赖）..."
    mvn -q -pl big-license install -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "big-license 编译失败" }
}

# 2. 构建运行 classpath
$cpFile = "big-license\target\cp.txt"
if (-not (Test-Path $cpFile)) {
    Write-Host "[build] 解析依赖 classpath..."
    mvn -q -pl big-license dependency:build-classpath -Dmdep.outputFile=target\cp.txt
    if ($LASTEXITCODE -ne 0) { throw "classpath 解析失败" }
}
$cp = "big-license\target\classes;" + (Get-Content $cpFile -Raw).Trim()

# 3. 口令
$keyPwd = if ($env:BIG_LICENSE_KEY_PWD) { $env:BIG_LICENSE_KEY_PWD } else {
    (Read-Host "私钥口令 (key-pwd)") }
$storePwd = if ($env:BIG_LICENSE_STORE_PWD) { $env:BIG_LICENSE_STORE_PWD } else {
    (Read-Host "证书库口令 (store-pwd)") }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$privateStore = Join-Path $OutDir "privateKeys.store"
$publicStore  = Join-Path $OutDir "publicCerts.store"
$licenseFile  = Join-Path $OutDir "license.lic"

# 4. 密钥对（已存在则复用）
if (-not (Test-Path $privateStore)) {
    Write-Host "[keypair] 生成密钥对与自签名证书..."
    java -cp $cp com.cqcnihao.bigx.license.tool.LicenseKeyPairTool `
        --private-store $privateStore --public-store $publicStore `
        --key-pwd $keyPwd --store-pwd $storePwd
    if ($LASTEXITCODE -ne 0) { throw "密钥对生成失败" }
} else {
    Write-Host "[keypair] 复用已有密钥库: $privateStore"
}

# 5. 签发
$genArgs = @(
    "-cp", $cp, "com.cqcnihao.bigx.license.tool.LicenseGenerator",
    "--subject", $Subject, "--customer", $Customer, "--expire", $Expire,
    "--private-store", $privateStore, "--key-pwd", $keyPwd, "--store-pwd", $storePwd,
    "--out", $licenseFile
)
if ($Mac -ne "") { $genArgs += @("--mac", $Mac) }
if ($Issued -ne "") { $genArgs += @("--issued", $Issued) }
& java @genArgs
if ($LASTEXITCODE -ne 0) { throw "许可证签发失败" }

Write-Host ""
Write-Host "[done] 分发清单："
Write-Host "  - $licenseFile   -> 部署目录 ./license/license.lic"
Write-Host "  - $publicStore   -> 部署目录 ./license/publicCerts.store"
Write-Host "  - $privateStore  -> 仅留存签发机，禁止分发！"
Write-Host "服务侧启动：BIG_LICENSE_ENABLED=true / BIG_LICENSE_STORE_PWD=<store-pwd>"
