# Chuẩn bị dữ liệu GIẢ cho kiểm thử hiệu năng (SCRUM-59). CHỈ chạy trên stack thử, không chạy trên dữ liệu thật.
# Cách chạy (PowerShell, trong thư mục repo, sau khi backend báo UP):
#   powershell -ExecutionPolicy Bypass -File docker\perf\prepare-perf.ps1 -Api http://localhost:8080/api/v1 -DbContainer familyconnect-db
# Tạo tài khoản perf@demo.familyconnect.vn và gia đình "Họ Lê (perf)" qua API, rồi nạp hàng loạt bằng seed-perf.sql.
param(
    [string]$Api = 'http://localhost:8080/api/v1',
    [string]$DbContainer = 'familyconnect-db',
    [int]$People = 5000,
    # Mật khẩu chung của tài khoản thử. Chỉ dùng cho máy thử, không dùng cho dữ liệu thật.
    [string]$Password = 'Demo@2026'
)
$ErrorActionPreference = 'Stop'
$email = 'perf@demo.familyconnect.vn'

function Call([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null) {
    $params = @{ Method = $Method; Uri = "$Api$Path"; ContentType = 'application/json; charset=utf-8'; Headers = @{} }
    if ($Token) { $params.Headers.Authorization = "Bearer $Token" }
    if ($null -ne $Body) { $params.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress)) }
    try { return (Invoke-RestMethod @params).data }
    catch { throw "$Method $Path that bai: $($_.ErrorDetails.Message)" }
}

try { $null = Call POST '/auth/login' @{ email = $email; password = $Password }; Write-Host 'Du lieu perf da co san. Khong nap lai.'; exit 0 } catch { }

$uid = (Call POST '/users' @{ email = $email; password = $Password; fullName = 'Lê Perf'; dateOfBirth = '1990-01-01' }).id
$token = (Call POST '/auth/login' @{ email = $email; password = $Password }).accessToken
$fam = (Call POST '/families' @{ name = 'Họ Lê (perf)'; description = 'Dữ liệu giả để đo hiệu năng' } $token).id
Write-Host "Gia dinh perf: $fam. Nap $People nguoi bang SQL..."

$sql = Join-Path $PSScriptRoot 'seed-perf.sql'
# PowerShell 5.1 mặc định đẩy chuỗi sang chương trình ngoài bằng ASCII, làm hỏng tiếng Việt trong SQL.
$OutputEncoding = New-Object Text.UTF8Encoding $false
Get-Content -Raw -Encoding UTF8 $sql | docker exec -i $DbContainer sh -c "psql -q -U `"`$POSTGRES_USER`" -d `"`$POSTGRES_DB`" -v fam=$fam -v uid=$uid -v n=$People"
if ($LASTEXITCODE -ne 0) { throw "seed-perf.sql that bai (ma $LASTEXITCODE)" }
