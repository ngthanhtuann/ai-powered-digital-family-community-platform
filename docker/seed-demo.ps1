# Nạp dữ liệu DEMO (dữ liệu giả, không phải người thật) vào FamilyConnect đang chạy bằng docker compose (SCRUM-58).
# Cách chạy (PowerShell, trong thư mục repo, sau khi backend báo UP):
#   powershell -ExecutionPolicy Bypass -File docker\seed-demo.ps1
# Chỉ gọi REST API như người dùng thật, không ghi thẳng vào database. Chạy lần hai sẽ tự dừng.
# Họ Nguyễn có 53 người, 5 đời, 4 chi; đủ các trường hợp: nhiều nhánh, người đã mất (tổ tiên chỉ biết năm sinh),
# góa (tự chuyển WIDOWED khi một bên mất), ly hôn rồi tái hôn, con nuôi (ADOPTED), con riêng của vợ (STEP),
# trẻ dưới 16 tuổi, dâu rể ngoài họ, nhánh họ ngoại chưa xếp chi.
param(
    [string]$Api = 'http://localhost:8080/api/v1',
    # Mật khẩu chung của mọi tài khoản demo. Chỉ dùng cho máy demo, không dùng cho dữ liệu thật.
    [string]$Password = 'Demo@2026'
)
$ErrorActionPreference = 'Stop'

function Call([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null) {
    $params = @{ Method = $Method; Uri = "$Api$Path"; ContentType = 'application/json; charset=utf-8'; Headers = @{} }
    if ($Token) { $params.Headers.Authorization = "Bearer $Token" }
    if ($null -ne $Body) { $params.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 6 -Compress)) }
    try { return (Invoke-RestMethod @params).data }
    catch { throw "$Method $Path that bai: $($_.ErrorDetails.Message)" }
}
function Login([string]$Key) { (Call POST '/auth/login' @{ email = "$Key@demo.familyconnect.vn"; password = $Password }).accessToken }
function Register([string]$Key, [string]$Name, [string]$Birth) {
    (Call POST '/users' @{ email = "$Key@demo.familyconnect.vn"; password = $Password; fullName = $Name; dateOfBirth = $Birth }).id
}

try { $null = Login 'phuc'; Write-Host 'Du lieu demo da co san (dang nhap duoc tai khoan phuc). Khong nap lai.'; exit 0 } catch { }

Write-Host '1/7 Tao tai khoan...'
$acc = [ordered]@{
    phuc = @('Nguyễn Văn Phúc', '1950-05-10'); hung = @('Nguyễn Văn Hùng', '1972-08-20'); minh = @('Nguyễn Văn Minh', '1978-11-02')
    ha = @('Nguyễn Thị Hà', '1994-04-09'); lan = @('Nguyễn Thị Lan', '1996-07-15'); khoa = @('Lê Văn Khoa', '1993-01-25')
    tu = @('Đỗ Minh Tú', '2000-03-03'); quy = @('Trần Văn Quý', '1960-10-10')
}
$uid = @{}; $tok = @{}
foreach ($k in $acc.Keys) { $uid[$k] = Register $k $acc[$k][0] $acc[$k][1]; $tok[$k] = Login $k }

Write-Host '2/7 Tao dong ho, chi...'
$fam = (Call POST '/families' @{ name = 'Họ Nguyễn – Nam Định (demo)'; description = 'Dữ liệu giả để demo' } $tok.phuc).id
$famTran = (Call POST '/families' @{ name = 'Họ Trần – Huế (demo)'; description = 'Dữ liệu giả để demo' } $tok.quy).id
$b1 = (Call POST "/families/$fam/branches" @{ name = 'Chi 1 (chi trưởng)' } $tok.phuc).id
$b2 = (Call POST "/families/$fam/branches" @{ name = 'Chi 2' } $tok.phuc).id
$b2a = (Call POST "/families/$fam/branches" @{ name = 'Chi 2a'; parentBranchId = $b2 } $tok.phuc).id
$b3 = (Call POST "/families/$fam/branches" @{ name = 'Chi 3' } $tok.phuc).id

Write-Host '3/7 Tao cay gia pha 5 doi (53 nguoi)...'
# Giữ thân yêu cầu của từng người: PUT hồ sơ thay toàn bộ trường nên lúc đánh dấu đã mất phải gửi lại đủ.
$p = @{}; $body = @{}
function Person($Key, $Name, $Gender, $Birth, $Branch, $Extra = @{}) {
    $b = @{ fullName = $Name; gender = $Gender; birthDate = $Birth; branchId = $Branch }
    foreach ($e in $Extra.Keys) { $b[$e] = $Extra[$e] }
    $body[$Key] = $b
    $p[$Key] = (Call POST "/families/$fam/persons" $b $tok.phuc).id
}
# Đời 1. Tạo như người còn sống để thêm được hôn nhân (BR-GEN-08 cấm thêm hôn nhân cho người đã mất), đánh dấu mất ở cuối bước.
Person to 'Nguyễn Văn Tổ' MALE '1925-02-10' $null @{ birthplace = 'Nam Định' }
Person nhan 'Đào Thị Nhàn' FEMALE '1932-06-15' $null @{ birthplace = 'Nam Định' }
# Đời 2
Person phuc 'Nguyễn Văn Phúc' MALE '1950-05-10' $b1 @{ userId = $uid.phuc; birthplace = 'Nam Định'; currentProvince = 'Nam Định' }
Person hong 'Phạm Thị Hồng' FEMALE '1952-09-18' $b1 @{ currentProvince = 'Nam Định' }
Person loc 'Nguyễn Văn Lộc' MALE '1955-03-22' $b3 @{ currentProvince = 'Hải Phòng' }
Person sen 'Lý Thị Sen' FEMALE '1958-12-01' $b3 @{ currentProvince = 'Hải Phòng' }
Person thoa 'Nguyễn Thị Thoa' FEMALE '1960-07-07' $null @{ currentProvince = 'Thái Bình' }
Person tai 'Đặng Văn Tài' MALE '1957-04-30' $null @{ currentProvince = 'Thái Bình' }
Person hung 'Nguyễn Văn Hùng' MALE '1972-08-20' $b2 @{ currentProvince = 'Hà Nội' }
Person mai 'Trần Thị Mai' FEMALE '1975-02-14' $b2 @{ currentProvince = 'Hà Nội' }
# Đời 3
Person minh 'Nguyễn Văn Minh' MALE '1978-11-02' $b1 @{ currentProvince = 'Nam Định' }
Person thu 'Vũ Thị Thu' FEMALE '1980-05-05' $null @{ currentProvince = 'Nam Định'; note = 'Vợ trước của ông Minh, đã ly hôn' }
Person ngoc 'Bùi Thị Ngọc' FEMALE '1985-10-20' $b1 @{ currentProvince = 'Nam Định' }
Person hue 'Nguyễn Thị Huệ' FEMALE '1980-12-12' $b1 @{ currentProvince = 'Hà Nội' }
Person duc 'Hoàng Văn Đức' MALE '1978-09-09' $b1 @{ currentProvince = 'Hà Nội' }
Person an 'Nguyễn Văn An' MALE '1985-01-15' $b1 @{ currentProvince = 'Đà Nẵng'; note = 'Con nuôi của ông Phúc và bà Hồng' }
Person yen 'Phan Thị Yến' FEMALE '1988-08-08' $b1 @{ currentProvince = 'Đà Nẵng' }
Person long 'Nguyễn Văn Long' MALE '1980-02-02' $b3 @{ currentProvince = 'Hải Phòng' }
Person hanh 'Võ Thị Hạnh' FEMALE '1982-03-03' $b3 @{ currentProvince = 'Hải Phòng' }
Person lien 'Nguyễn Thị Liên' FEMALE '1983-11-11' $b3 @{ currentProvince = 'Hà Nội' }
Person hai 'Từ Văn Hải' MALE '1981-06-06' $b3 @{ currentProvince = 'Hà Nội' }
Person quang 'Nguyễn Văn Quang' MALE '1986-04-04' $b3 @{ currentProvince = 'TP. Hồ Chí Minh' }
Person thao 'Trịnh Thị Thảo' FEMALE '1989-09-19' $b3 @{ currentProvince = 'TP. Hồ Chí Minh' }
Person bao 'Đặng Văn Bảo' MALE '1982-10-10' $null @{ currentProvince = 'Thái Bình' }
Person nhung 'Kiều Thị Nhung' FEMALE '1985-05-25' $null @{ currentProvince = 'Thái Bình' }
Person ha 'Nguyễn Thị Hà' FEMALE '1994-04-09' $b2 @{ currentProvince = 'Hà Nội' }
Person son 'Cao Văn Sơn' MALE '1992-02-20' $b2 @{ currentProvince = 'Hà Nội' }
Person lan 'Nguyễn Thị Lan' FEMALE '1996-07-15' $b2a @{ currentProvince = 'TP. Hồ Chí Minh' }
Person khoa 'Lê Văn Khoa' MALE '1993-01-25' $b2a @{ currentProvince = 'TP. Hồ Chí Minh' }
Person nam 'Nguyễn Văn Nam' MALE '1998-12-24' $b2 @{ currentProvince = 'Hà Nội' }
Person vy 'Lương Thị Vy' FEMALE '1999-01-31' $b2 @{ currentProvince = 'Hà Nội' }
Person na 'Nguyễn Thị Na' FEMALE '2016-09-01' $b2
# Đời 4
Person tuan 'Nguyễn Văn Tuấn' MALE '2003-03-15' $b1 @{ currentProvince = 'Nam Định' }
Person diep 'Lâm Thị Diệp' FEMALE '2004-07-21' $b1 @{ currentProvince = 'Nam Định' }
Person huy 'Bùi Gia Huy' MALE '2008-08-18' $b1 @{ currentProvince = 'Nam Định'; note = 'Con riêng của bà Ngọc' }
Person ngan 'Nguyễn Thị Ngân' FEMALE '2014-02-14' $b1
Person chau 'Hoàng Minh Châu' FEMALE '2005-04-12' $b1 @{ currentProvince = 'Hà Nội' }
Person tin 'Ông Văn Tín' MALE '2003-11-30' $b1 @{ currentProvince = 'Hà Nội' }
Person phat 'Hoàng Gia Phát' MALE '2009-09-02' $b1 @{ currentProvince = 'Hà Nội' }
Person baoanh 'Nguyễn Bảo Anh' FEMALE '2013-06-01' $b1
Person thinh 'Nguyễn Đức Thịnh' MALE '2002-01-20' $b3 @{ currentProvince = 'Hải Phòng' }
Person hoa 'Mạc Thị Hoa' FEMALE '2003-05-15' $b3 @{ currentProvince = 'Hải Phòng' }
Person trang 'Nguyễn Thu Trang' FEMALE '2006-10-10' $b3 @{ currentProvince = 'Hải Phòng' }
Person lienchi 'Từ Liên Chi' FEMALE '2008-03-08' $b3 @{ currentProvince = 'Hà Nội' }
Person haidang 'Từ Hải Đăng' MALE '2011-07-27' $b3
Person khoi 'Nguyễn Minh Khôi' MALE '2015-12-01' $b3
Person thaovy 'Đặng Thảo Vy' FEMALE '2010-01-01' $null
Person hamy 'Cao Hà My' FEMALE '2021-08-15' $b2
Person bin 'Lê Gia Bin' MALE '2020-06-01' $b2a
Person giahan 'Nguyễn Gia Hân' FEMALE '2023-03-03' $b2
# Đời 5
Person phong 'Nguyễn Đức Phong' MALE '2025-04-04' $b3
Person kiet 'Nguyễn Tuấn Kiệt' MALE '2025-09-09' $b1
Person mthu 'Ông Minh Thư' FEMALE '2026-02-01' $b1

# Hôn nhân trước quan hệ cha-con; Minh ly hôn Thu rồi mới tái hôn với Ngọc (BR-GEN-07).
function Marry($A, $B, $Start) { (Call POST '/marriages' @{ person1Id = $p[$A]; person2Id = $p[$B]; startDate = $Start } $tok.phuc).id }
foreach ($m in @(@('to','nhan','1949-05-01'), @('phuc','hong','1975-04-20'), @('loc','sen','1978-02-10'), @('tai','thoa','1980-01-15'),
                 @('hung','mai','1993-12-05'), @('duc','hue','2003-10-10'), @('an','yen','2011-11-11'), @('long','hanh','2001-05-20'),
                 @('hai','lien','2006-12-12'), @('quang','thao','2013-01-06'), @('bao','nhung','2008-09-09'), @('khoa','lan','2019-05-18'),
                 @('son','ha','2020-10-20'), @('nam','vy','2022-06-06'), @('thinh','hoa','2024-02-02'), @('tuan','diep','2024-12-20'),
                 @('tin','chau','2025-05-05'))) {
    $null = Marry $m[0] $m[1] $m[2]
}
$minhThu = Marry 'minh' 'thu' '2002-06-01'
$null = Call PUT "/marriages/$minhThu" @{ status = 'DIVORCED'; endDate = '2010-09-01' } $tok.phuc
$null = Marry 'minh' 'ngoc' '2012-03-10'

# Mỗi dòng: con, cha, mẹ, loại (mặc định BIOLOGICAL). Cha hoặc mẹ để trống nếu không có trong cây.
function Parents($Child, $Father, $Mother, $Kind = 'BIOLOGICAL') {
    if ($Father) { $null = Call POST "/persons/$($p[$Child])/parents" @{ parentId = $p[$Father]; parentRole = 'FATHER'; kind = $Kind } $tok.phuc }
    if ($Mother) { $null = Call POST "/persons/$($p[$Child])/parents" @{ parentId = $p[$Mother]; parentRole = 'MOTHER'; kind = $Kind } $tok.phuc }
}
foreach ($c in @('phuc','loc','thoa','hung')) { Parents $c 'to' 'nhan' }
foreach ($c in @('minh','hue')) { Parents $c 'phuc' 'hong' }
Parents 'an' 'phuc' 'hong' 'ADOPTED'
foreach ($c in @('long','lien','quang')) { Parents $c 'loc' 'sen' }
Parents 'bao' 'tai' 'thoa'
foreach ($c in @('ha','lan','na','nam')) { Parents $c 'hung' 'mai' }
Parents 'tuan' 'minh' 'thu'
Parents 'ngan' 'minh' 'ngoc'
Parents 'huy' $null 'ngoc'
Parents 'huy' 'minh' $null 'STEP'
foreach ($c in @('chau','phat')) { Parents $c 'duc' 'hue' }
Parents 'baoanh' 'an' 'yen'
foreach ($c in @('thinh','trang')) { Parents $c 'long' 'hanh' }
foreach ($c in @('lienchi','haidang')) { Parents $c 'hai' 'lien' }
Parents 'khoi' 'quang' 'thao'
Parents 'thaovy' 'bao' 'nhung'
Parents 'hamy' 'son' 'ha'
Parents 'bin' 'khoa' 'lan'
Parents 'giahan' 'nam' 'vy'
Parents 'phong' 'thinh' 'hoa'
Parents 'kiet' 'tuan' 'diep'
Parents 'mthu' 'tin' 'chau'

# Đánh dấu đã mất: hôn nhân MARRIED của người mất tự chuyển WIDOWED (BR-GEN-08). Tổ tiên chỉ giữ năm sinh (BR-GEN-17).
function Die($Key, $DeathDate, $Burial, $BirthYearOnly = $false) {
    $b = $body[$Key]; $b.isDeceased = $true; $b.deathDate = $DeathDate; $b.burialPlace = $Burial
    if ($BirthYearOnly) { $b.birthYear = [int]$b.birthDate.Substring(0, 4); $b.birthDate = $null }
    $null = Call PUT "/persons/$($p[$Key])" $b $tok.phuc
}
Die 'to' '1998-03-12' 'Nghĩa trang làng Hải Hậu' $true
Die 'nhan' '2005-11-20' 'Nghĩa trang làng Hải Hậu' $true
Die 'loc' '2015-07-01' 'Nghĩa trang Hải Phòng'

Write-Host '4/7 Xin vao gia dinh va duyet...'
foreach ($j in @(@('hung', $b2), @('minh', $b1), @('ha', $b2), @('lan', $b2a), @('khoa', $b2a))) {
    $null = Call POST '/join-requests' @{ code = $j[1]; message = 'Con cháu trong họ xin vào gia đình' } $tok[$j[0]]
}
$pending = Call GET '/join-requests?status=PENDING&page=0&size=50' $null $tok.phuc
foreach ($r in $pending) {
    $key = ($uid.GetEnumerator() | Where-Object { $_.Value -eq $r.userId }).Key
    $null = Call PATCH "/join-requests/$($r.id)" @{ status = 'APPROVED'; personId = $p[$key] } $tok.phuc
}
# Trưởng họ bổ nhiệm trưởng chi (UC-AUTH-14).
$null = Call PUT "/families/$fam/accounts/$($uid.hung)" @{ role = 'BRANCH_ADMIN'; branchId = $b2 } $tok.phuc
# Tú để chờ duyệt, dùng khi demo luồng duyệt thành viên.
$null = Call POST '/join-requests' @{ code = $b1; message = 'Cháu ngoại bác Phúc, xin vào Chi 1' } $tok.tu
foreach ($k in @('hung','minh','ha','lan','khoa')) { $tok[$k] = Login $k }

Write-Host '5/7 Tao thong bao, su kien, bai dang...'
$null = Call POST "/announcements" @{ title = 'Giỗ Tổ họ Nguyễn ngày 12/3 âm lịch'; content = 'Mời con cháu các chi về nhà thờ họ dự giỗ Tổ. Mỗi chi cử 2 người phụ bếp.'; branchId = $null; isPinned = $true } $tok.phuc
$null = Call POST '/events' @{ title = 'Giỗ Tổ họ Nguyễn'; description = 'Lễ giỗ cụ Nguyễn Văn Tổ, họp mặt toàn họ.'; startAt = '2026-12-20T08:00:00+07:00'; endAt = '2026-12-20T14:00:00+07:00'; location = 'Nhà thờ họ Nguyễn, Hải Hậu, Nam Định'; branchId = $null } $tok.phuc
$null = Call POST '/events' @{ title = 'Họp Chi 2 tổng kết năm'; description = 'Tổng kết quỹ khuyến học và kế hoạch năm sau.'; startAt = '2026-12-27T18:00:00+07:00'; location = 'Nhà chú Hùng, Hà Nội'; branchId = $b2 } $tok.hung
$null = Call POST '/posts' @{ content = 'Họ ta chuẩn bị in lại cuốn gia phả. Các chi gửi bổ sung thông tin con cháu trước 30/11.'; type = 'NEWS'; branchId = $null } $tok.phuc
$null = Call POST '/posts' @{ content = 'Chi 1 họp bàn sửa mộ cụ Tổ vào Chủ nhật này, 8h sáng tại nhà bác Phúc.'; type = 'POST'; branchId = $b1 } $tok.minh
$null = Call POST '/posts' @{ content = 'Chi 2 góp quỹ khuyến học năm nay, hạn cuối 30/11. Cháu nào đạt học sinh giỏi báo chú Hùng.'; type = 'POST'; branchId = $b2 } $tok.hung
$null = Call POST '/posts' @{ content = 'Cuối tuần này Chi 2a gặp mặt mừng sinh nhật bé Bin 6 tuổi ở nhà em nhé cả nhà.'; type = 'POST'; branchId = $b2a } $tok.lan
$null = Call POST '/posts' @{ content = 'Cảm ơn cả họ đã đón con rể mới. Hẹn gặp mọi người ở giỗ Tổ!'; type = 'POST'; branchId = $null } $tok.khoa
$null = Call POST '/posts' @{ content = 'Họ Trần lịch tảo mộ cuối năm tại Huế, mời con cháu sắp xếp về.'; type = 'POST'; branchId = $null } $tok.quy

Write-Host '6/7 Tao ho so nghe nghiep (ten co quan la gia)...'
foreach ($j in @(@('hung','Kỹ sư xây dựng','Công ty Xây dựng Demo','Hà Nội'), @('minh','Giáo viên Toán','Trường THPT Demo','Nam Định'),
                 @('an','Bác sĩ','Phòng khám Demo','Đà Nẵng'), @('long','Kế toán','Công ty Cảng Demo','Hải Phòng'),
                 @('quang','Lập trình viên','Công ty Phần mềm Demo','TP. Hồ Chí Minh'), @('ha','Chuyên viên dữ liệu','Ngân hàng Demo','Hà Nội'),
                 @('khoa','Kỹ sư điện','Công ty Điện Demo','TP. Hồ Chí Minh'), @('tuan','Kỹ thuật viên','Xưởng Cơ khí Demo','Nam Định'))) {
    $null = Call PUT "/directory/members/$($p[$j[0]])/profession-profile" @{ jobTitle = $j[1]; company = $j[2]; workLocation = $j[3] } $tok.phuc
}

Write-Host '7/7 Tu kiem tra...'
$all = @(Call GET "/families/$fam/persons?page=0&size=100" $null $tok.phuc)
Write-Host "So nguoi trong ho Nguyen: $($all.Count)"
if ($all.Count -ne $p.Count) { throw "Mong doi $($p.Count) nguoi, API tra ve $($all.Count)" }
# Mỗi dòng: A, B, có hỗ trợ không. Thuật toán chỉ tính tới chắt (3,0); (4,0) trở đi trả supported=false (algorithm-relationship.md).
foreach ($q in @(@('minh','ha',$true), @('bin','hung',$true), @('kiet','phuc',$true), @('mthu','phuc',$true), @('an','phuc',$true),
                 @('huy','minh',$true), @('thinh','to',$true), @('phong','to',$false))) {
    $r = Call GET "/relationships?personA=$($p[$q[0]])&personB=$($p[$q[1]])" $null $tok.phuc
    Write-Host "  $($q[0]) -> $($q[1]): $($r.description)"
    if ([bool]$r.supported -ne $q[2]) { throw "Quan he $($q[0]) -> $($q[1]) tra supported=$($r.supported), mong doi $($q[2])" }
}

Write-Host 'Xong.'
Write-Host "Ma gia dinh ho Nguyen (dung khi xin vao gia dinh): $fam"
Write-Host "Ma Chi 1: $b1 | Ma Chi 2: $b2 | Ma Chi 2a: $b2a | Ma Chi 3: $b3"
Write-Host "Ma gia dinh ho Tran: $famTran"
