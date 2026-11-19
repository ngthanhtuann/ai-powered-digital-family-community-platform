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
    # PowerShell 5.1 đọc JSON không ghi charset theo ISO-8859-1 nên tiếng Việt bị méo ("Nguyá»n");
    # gửi ngược dữ liệu đọc về (như PUT /persons) sẽ ghi tên méo vào DB. Vì vậy tự giải mã UTF-8.
    try {
        $res = Invoke-WebRequest @params -UseBasicParsing
        return ([Text.Encoding]::UTF8.GetString($res.RawContentStream.ToArray()) | ConvertFrom-Json).data
    }
    catch { throw "$Method $Path that bai: $($_.ErrorDetails.Message)" }
}
function Login([string]$Key) { (Call POST '/auth/login' @{ email = "$Key@demo.familyconnect.vn"; password = $Password }).accessToken }
function Register([string]$Key, [string]$Name, [string]$Birth) {
    (Call POST '/users' @{ email = "$Key@demo.familyconnect.vn"; password = $Password; fullName = $Name; dateOfBirth = $Birth }).id
}


# --- Di sản (SCRUM-114): câu chuyện, tài liệu, ảnh đều là dữ liệu giả; ảnh và PDF sinh ngay khi chạy, không lấy ảnh thật. ---
Add-Type -AssemblyName System.Net.Http
Add-Type -AssemblyName System.Drawing

function Upload-Media([string]$File, [string]$Mime, [string]$FamilyId, [string]$Caption, [string]$Token) {
    # PowerShell 5.1 không có Invoke-RestMethod -Form, nên dựng multipart bằng HttpClient.
    $client = New-Object System.Net.Http.HttpClient
    try {
        $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)
        $form = New-Object System.Net.Http.MultipartFormDataContent
        $bytes = New-Object System.Net.Http.ByteArrayContent(, [IO.File]::ReadAllBytes($File))
        $bytes.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($Mime)
        $form.Add($bytes, 'file', [IO.Path]::GetFileName($File))
        $form.Add((New-Object System.Net.Http.StringContent('HERITAGE')), 'ownerType')
        $form.Add((New-Object System.Net.Http.StringContent($FamilyId)), 'ownerId')
        if ($Caption) { $form.Add((New-Object System.Net.Http.StringContent($Caption, [Text.Encoding]::UTF8)), 'caption') }
        $res = $client.PostAsync("$Api/media", $form).Result
        $text = $res.Content.ReadAsStringAsync().Result
        if (-not $res.IsSuccessStatusCode) { throw "POST /media that bai: $text" }
        return ($text | ConvertFrom-Json).data.id
    } finally { $client.Dispose() }
}

function New-DemoImage([string]$Path, [string]$Title, [int]$Variant) {
    # Ảnh minh họa: nền chuyển màu theo bảng màu design system, chữ ghi rõ là ảnh giả.
    $tones = @(@(44, 24, 16), @(92, 138, 110), @(122, 99, 72), @(184, 122, 158), @(21, 101, 192), @(191, 68, 0))
    $t = $tones[$Variant % $tones.Count]
    $bmp = New-Object System.Drawing.Bitmap 960, 640
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    try {
        $g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAlias'
        $rect = New-Object System.Drawing.Rectangle 0, 0, 960, 640
        $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect,
            [System.Drawing.Color]::FromArgb($t[0], $t[1], $t[2]), [System.Drawing.Color]::FromArgb(201, 168, 76), 35)
        $g.FillRectangle($brush, $rect)
        $cream = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(245, 230, 200))
        $g.DrawString($Title, (New-Object System.Drawing.Font('Segoe UI', 40, [System.Drawing.FontStyle]::Bold)), $cream,
            (New-Object System.Drawing.RectangleF 60, 220, 840, 200))
        $g.DrawString('Ảnh minh họa – dữ liệu giả cho demo FamilyConnect', (New-Object System.Drawing.Font('Segoe UI', 20)), $cream, 60, 540)
        $bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $g.Dispose(); $bmp.Dispose() }
}

function New-DemoPdf([string]$Path, [string[]]$Lines) {
    # PDF một trang tối thiểu, chữ ASCII (font chuẩn Helvetica không có dấu tiếng Việt).
    $content = 'BT /F1 16 Tf 72 770 Td ' + (($Lines | ForEach-Object { '(' + $_ + ') Tj 0 -26 Td' }) -join ' ') + ' ET'
    $objs = @('<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
        '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
        "<< /Length $($content.Length) >>`nstream`n$content`nendstream", '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>')
    $sb = New-Object Text.StringBuilder
    [void]$sb.Append("%PDF-1.4`n"); $offsets = @()
    for ($i = 0; $i -lt $objs.Count; $i++) { $offsets += $sb.Length; [void]$sb.Append("$($i + 1) 0 obj`n$($objs[$i])`nendobj`n") }
    $xref = $sb.Length
    [void]$sb.Append("xref`n0 $($objs.Count + 1)`n0000000000 65535 f `n")
    foreach ($o in $offsets) { [void]$sb.Append(('{0:D10} 00000 n ' -f $o) + "`n") }
    [void]$sb.Append("trailer`n<< /Size $($objs.Count + 1) /Root 1 0 R >>`nstartxref`n$xref`n%%EOF`n")
    [IO.File]::WriteAllText($Path, $sb.ToString(), [Text.Encoding]::ASCII)
}

function Seed-Heritage([string]$Token, [string]$FamilyId, $ToPersonId = $null) {
    $dir = Join-Path ([IO.Path]::GetTempPath()) ('fc-demo-' + [Guid]::NewGuid())
    New-Item -ItemType Directory $dir | Out-Null
    try {
        $stories = @(
            @('Cụ Tổ lập làng ven sông Ninh Cơ', 'Theo lời các cụ kể lại, năm 1945 cụ Nguyễn Văn Tổ cùng ba người em dời từ vùng trũng về ven sông Ninh Cơ, Nam Định, khai hoang lập xóm. Cụ đặt quy ước mỗi nhà góp một sào làm ruộng họ để lo giỗ chạp và khuyến học. Ngôi nhà thờ họ đầu tiên dựng bằng tre và lá cọ năm 1952. Câu chuyện này là dữ liệu giả cho bản demo.', $ToPersonId),
            @('Quỹ khuyến học của dòng họ', 'Từ năm 1990, bác Nguyễn Văn Phúc khởi xướng quỹ khuyến học của họ Nguyễn. Mỗi dịp giỗ Tổ, họ trao thưởng cho các cháu đạt học sinh giỏi; năm 2025 có 12 cháu được khen. Câu chuyện này là dữ liệu giả cho bản demo.', $null),
            @('Lần đầu bốn chi về giỗ Tổ đông đủ', 'Năm 2010, lần đầu tiên cả bốn chi họ Nguyễn cùng về nhà thờ họ dự giỗ Tổ, hơn 80 người. Các cụ cao tuổi kể chuyện xưa, con cháu ghi lại để làm gia phả. Câu chuyện này là dữ liệu giả cho bản demo.', $null))
        foreach ($s in $stories) {
            $body = @{ title = $s[0]; content = $s[1] }
            if ($s[2]) { $body.relatedPersonId = $s[2] }
            $null = Call POST '/heritage/stories' $body $Token
        }

        $pdf = Join-Path $dir 'gia-pha-minh-hoa.pdf'
        New-DemoPdf $pdf @('Gia pha ho Nguyen - Nam Dinh (ban minh hoa)', 'Doi 1: Nguyen Van To (1925-1998)', 'Doi 2: Nguyen Van Phuc, Nguyen Van Loc, Nguyen Van Tai',
            '', 'Du lieu gia dung cho demo FamilyConnect.')
        $null = Call POST '/heritage/documents' @{ title = 'Gia phả chép tay (bản minh họa)'; category = 'Gia phả'
            description = 'Bản chép lại ba đời đầu của họ Nguyễn. Tài liệu giả cho demo.'; mediaId = (Upload-Media $pdf 'application/pdf' $FamilyId $null $Token) } $Token
        $award = Join-Path $dir 'bang-khen.png'
        New-DemoImage $award 'Bằng khen khuyến học 2025' 5
        $null = Call POST '/heritage/documents' @{ title = 'Bằng khen khuyến học 2025 (ảnh minh họa)'; category = 'Khen thưởng'
            description = 'Ảnh chụp bằng khen trao dịp giỗ Tổ. Tài liệu giả cho demo.'; mediaId = (Upload-Media $award 'image/png' $FamilyId $null $Token) } $Token

        $photos = @('Nhà thờ họ Nguyễn', 'Giỗ Tổ năm 2025', 'Trao thưởng khuyến học 2025', 'Chi 1 sửa mộ cụ Tổ', 'Họp mặt cuối năm 2024', 'Ruộng họ ven sông Ninh Cơ')
        for ($i = 0; $i -lt $photos.Count; $i++) {
            $file = Join-Path $dir "anh-$i.png"
            New-DemoImage $file $photos[$i] $i
            $null = Upload-Media $file 'image/png' $FamilyId "$($photos[$i]) (ảnh minh họa)" $Token
        }
        Write-Host "  Di san: $($stories.Count) cau chuyen, 2 tai lieu, $($photos.Count) anh."
    } finally { Remove-Item -Recurse -Force $dir }
}

# --- Hồ sơ cá nhân và bài đăng có tương tác (SCRUM-129): toàn bộ là dữ liệu giả, địa chỉ ghi rõ "(giả)". ---
function Post-File([string]$Path, [string]$File, [string]$Mime, [hashtable]$Fields, [string]$Token) {
    # Multipart cho ảnh đại diện (/media, ownerType PERSON) và ảnh bài đăng (/posts/{id}/media).
    $client = New-Object System.Net.Http.HttpClient
    try {
        $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)
        $form = New-Object System.Net.Http.MultipartFormDataContent
        $bytes = New-Object System.Net.Http.ByteArrayContent(, [IO.File]::ReadAllBytes($File))
        $bytes.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($Mime)
        $form.Add($bytes, 'file', [IO.Path]::GetFileName($File))
        foreach ($k in $Fields.Keys) { $form.Add((New-Object System.Net.Http.StringContent($Fields[$k])), $k) }
        $res = $client.PostAsync("$Api$Path", $form).Result
        $text = $res.Content.ReadAsStringAsync().Result
        if (-not $res.IsSuccessStatusCode) { throw "POST $Path that bai: $text" }
        return ($text | ConvertFrom-Json).data.id
    } finally { $client.Dispose() }
}

function New-DemoAvatar([string]$Path, [string]$Name, [int]$Variant) {
    # Ảnh đại diện vẽ sẵn: nền màu, chữ cái đầu của tên; không dùng ảnh người thật.
    $tones = @(@(44, 24, 16), @(92, 138, 110), @(122, 99, 72), @(184, 122, 158), @(21, 101, 192), @(191, 68, 0))
    $t = $tones[$Variant % $tones.Count]
    $bmp = New-Object System.Drawing.Bitmap 400, 400
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    try {
        $g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAlias'
        $g.Clear([System.Drawing.Color]::FromArgb($t[0], $t[1], $t[2]))
        $letter = ($Name.Trim() -split '\s+')[-1].Substring(0, 1).ToUpper()
        $format = New-Object System.Drawing.StringFormat
        $format.Alignment = 'Center'; $format.LineAlignment = 'Center'
        $g.DrawString($letter, (New-Object System.Drawing.Font('Segoe UI', 150, [System.Drawing.FontStyle]::Bold)),
            (New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(245, 230, 200))),
            (New-Object System.Drawing.RectangleF 0, 0, 400, 400), $format)
        $bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $g.Dispose(); $bmp.Dispose() }
}

function Seed-Social {
    $t = @{}
    foreach ($k in @('phuc','hung','minh','ha','lan','khoa')) { $t[$k] = Login $k }
    if ((Call GET '/persons/me' $null $t.ha).avatarMediaId) {
        Write-Host '  Ho so ca nhan va bai dang tuong tac da co, bo qua.'
        return
    }
    $dir = Join-Path ([IO.Path]::GetTempPath()) ('fc-social-' + [Guid]::NewGuid())
    New-Item -ItemType Directory $dir | Out-Null
    try {
        # Mỗi dòng: tài khoản, quê, nơi ở, địa chỉ, giới thiệu, nghề (null là giữ nghề đã có), trường, bằng, năm tốt nghiệp.
        $profiles = @(
            @('phuc', 'Hải Hậu, Nam Định', 'Nam Định', 'Xóm 3, xã Hải Đông (địa chỉ giả)', 'Trưởng họ Nguyễn, giáo viên đã nghỉ hưu, phụ trách quỹ khuyến học.', @('Giáo viên (đã nghỉ hưu)', 'Trường THCS Demo', 'Nam Định'), 'Đại học Sư phạm Hà Nội', 'Cử nhân', 1972),
            @('hung', 'Hải Hậu, Nam Định', 'Hà Nội', 'Số 12 ngõ Demo, Cầu Giấy (địa chỉ giả)', 'Trưởng Chi 2. Thích câu cá và chơi cờ tướng cuối tuần.', $null, 'Đại học Xây dựng', 'Kỹ sư', 1994),
            @('minh', 'Hải Hậu, Nam Định', 'Nam Định', 'Phố Demo, TP. Nam Định (địa chỉ giả)', 'Dạy Toán, mở lớp bồi dưỡng miễn phí cho các cháu trong họ dịp hè.', $null, 'Đại học Sư phạm Hà Nội', 'Thạc sĩ', 2002),
            @('ha', 'Hà Nội', 'Hà Nội', 'Chung cư Demo, Hà Đông (địa chỉ giả)', 'Làm dữ liệu ở ngân hàng, mẹ của bé My. Hay chụp ảnh các buổi họp họ.', $null, 'Đại học Kinh tế Quốc dân', 'Cử nhân', 2016),
            @('lan', 'Hà Nội', 'Hải Phòng', 'Phố Demo, Lê Chân (địa chỉ giả)', 'Thiết kế đồ họa tự do, phụ trách làm thiệp mời giỗ Tổ.', @('Nhà thiết kế đồ họa', 'Studio Demo', 'Hải Phòng'), 'Đại học Mỹ thuật Công nghiệp', 'Cử nhân', 2018),
            @('khoa', 'Cần Thơ', 'TP. Hồ Chí Minh', 'Đường Demo, Thủ Đức (địa chỉ giả)', 'Con rể họ Nguyễn, quê Cần Thơ, mê bóng đá.', $null, 'Đại học Bách khoa TP.HCM', 'Kỹ sư', 2015)
        )
        $i = 0
        foreach ($pr in $profiles) {
            $tok = $t[$pr[0]]
            $me = Call GET '/persons/me' $null $tok
            $img = Join-Path $dir "avatar-$($pr[0]).png"
            New-DemoAvatar $img $me.fullName $i
            $avatar = Post-File '/media' $img 'image/png' @{ ownerType = 'PERSON'; ownerId = $me.id } $tok
            # PUT ghi đè mọi trường: gửi lại trường không đổi (BR-GEN-20: chính chủ tự sửa).
            $null = Call PUT "/persons/$($me.id)" @{
                fullName = $me.fullName; gender = $me.gender; birthDate = $me.birthDate
                birthYear = $(if ($me.birthDate) { $null } else { $me.birthYear })
                birthplace = $pr[1]; currentProvince = $pr[2]; currentAddress = $pr[3]; note = $pr[4]
                branchId = $me.branchId; isDeceased = [bool]$me.isDeceased; deathDate = $me.deathDate
                burialPlace = $me.burialPlace; avatarMediaId = $avatar
            } $tok
            if ($pr[5]) {
                $null = Call PUT "/directory/members/$($me.id)/profession-profile" @{ jobTitle = $pr[5][0]; company = $pr[5][1]; workLocation = $pr[5][2] } $tok
            }
            $null = Call PUT "/directory/members/$($me.id)/education-profile" @{ school = $pr[6]; degree = $pr[7]; graduationYear = $pr[8] } $tok
            $i++
        }

        $branch = @{}
        foreach ($k in @('hung','lan')) { $branch[$k] = (Call GET '/users/me/membership' $null $t[$k]).branchId }
        # Mỗi bài: người đăng, nội dung, chi của ai (null là cả dòng họ), tiêu đề ảnh (null là không ảnh), bình luận, cảm xúc.
        # Bình luận: @(người, nội dung, số thứ tự bình luận được trả lời hoặc -1).
        $posts = @(
            @('ha', 'Hôm nay bé My nhà em tròn 5 tuổi. Cảm ơn ông bà, cô chú đã gửi quà, cả nhà chúc bé ngoan và khỏe nhé!', $null, 'Sinh nhật bé My 5 tuổi',
                @(@('phuc', 'Bác chúc cháu My mạnh khỏe, học giỏi, ngoan ngoãn!', -1), @('lan', 'Bé My xinh quá chị ơi, hôm nào cho em qua chơi với bé.', -1),
                  @('ha', 'Con cảm ơn bác ạ, Tết con đưa cháu về chúc Tết bác.', 0), @('hung', 'Ông ngoại chúc cháu gái lớn nhanh!', -1), @('ha', 'Hẹn em cuối tuần nhé.', 1)),
                @(@('phuc','LOVE'), @('lan','LOVE'), @('hung','LIKE'), @('minh','LIKE'), @('khoa','HAHA'))),
            @('minh', 'Tin vui: cháu Nguyễn Văn Nam đạt giải Nhì môn Toán cấp tỉnh năm nay. Giỗ Tổ sắp tới họ sẽ trao thưởng từ quỹ khuyến học.', $null, 'Giải Nhì Toán cấp tỉnh',
                @(@('phuc', 'Họ ta tự hào lắm. Bác sẽ chuẩn bị phần thưởng cho cháu.', -1), @('ha', 'Em Nam giỏi quá, chị chúc mừng em!', -1),
                  @('minh', 'Con cảm ơn bố, em Nam sẽ cố gắng thi quốc gia.', 0), @('khoa', 'Chúc mừng em, năm sau thi quốc gia nhé.', -1)),
                @(@('phuc','LOVE'), @('ha','LOVE'), @('hung','LIKE'), @('lan','LIKE'), @('khoa','LIKE'))),
            @('phuc', 'Nhà thờ họ đã lợp lại mái xong, kịp trước giỗ Tổ. Cảm ơn các chi đã góp công góp của.', $null, 'Nhà thờ họ sau khi sửa mái',
                @(@('hung', 'Mái mới đẹp quá anh. Chi 2 xin góp thêm tiền sơn lại cổng.', -1), @('minh', 'Hôm lợp mái Chi 1 có 6 người phụ, vui lắm bố ạ.', -1),
                  @('phuc', 'Được, Hùng gom tiền rồi báo anh nhé.', 0), @('khoa', 'Lần đầu cháu thấy nhà thờ họ, Tết này cháu xin về phụ ạ.', -1)),
                @(@('hung','LIKE'), @('minh','LOVE'), @('ha','LIKE'), @('lan','LIKE'), @('khoa','LIKE'))),
            @('hung', 'Chi 2 chốt danh sách đóng quỹ khuyến học năm nay. Nhà nào đóng rồi bình luận để mình ghi sổ nhé.', 'hung', $null,
                @(@('ha', 'Nhà con đóng rồi bố ạ.', -1), @('khoa', 'Tối nay con chuyển khoản bố nhé.', -1), @('hung', 'Bố ghi rồi.', 0),
                  @('lan', 'Nhà con đóng chung với chị Hà rồi bố.', -1)),
                @(@('ha','LIKE'), @('lan','LIKE'), @('khoa','LIKE'))),
            @('khoa', 'Mùa nước nổi ở quê mình Cần Thơ đang đẹp. Cả họ ai rảnh dịp cuối năm vào chơi, con dẫn đi chợ nổi Cái Răng.', $null, 'Chợ nổi Cái Răng',
                @(@('ha', 'Tết này nhà chị vào nhé, bé My mê đi thuyền lắm.', -1), @('lan', 'Nhớ món lẩu mắm quá anh ơi.', -1),
                  @('khoa', 'Chị vào thì báo em ra bến đón ạ.', 0), @('phuc', 'Hay quá, bác cũng muốn một lần vào miền Tây.', -1)),
                @(@('ha','LOVE'), @('lan','HAHA'), @('phuc','LIKE'), @('hung','LIKE'))),
            @('lan', 'Chi 2a cuối tuần này gặp mặt mừng sinh nhật bé Bin ở nhà em, mọi người nhớ đến sớm phụ nấu ăn nhé.', 'lan', $null,
                @(@('khoa', 'Anh mang bánh kem với nước ngọt nhé.', -1), @('hung', 'Bố mẹ đến từ sáng phụ con.', -1), @('lan', 'Dạ con cảm ơn bố ạ.', 1)),
                @(@('khoa','LOVE'), @('hung','LIKE')))
        )
        $n = 0
        foreach ($ps in $posts) {
            $tok = $t[$ps[0]]
            $branchId = $(if ($ps[2]) { $branch[$ps[2]] } else { $null })
            $post = Call POST '/posts' @{ content = $ps[1]; type = 'POST'; branchId = $branchId; audience = $(if ($branchId) { 'BRANCH' } else { 'FAMILY' }) } $tok
            if ($ps[3]) {
                $img = Join-Path $dir "post-$n.png"
                New-DemoImage $img $ps[3] $n
                $null = Post-File "/posts/$($post.id)/media" $img 'image/png' @{} $tok
            }
            $ids = @()
            foreach ($c in $ps[4]) {
                $parent = $(if ($c[2] -ge 0) { $ids[$c[2]] } else { $null })
                $ids += (Call POST "/posts/$($post.id)/comments" @{ content = $c[1]; parentId = $parent } $t[$c[0]]).id
            }
            foreach ($r in $ps[5]) { $null = Call POST "/posts/$($post.id)/reactions" @{ type = $r[1] } $t[$r[0]] }
            $n++
        }
        Write-Host "  Ho so: $($profiles.Count) tai khoan co anh dai dien, que, noi o, hoc van. Bai dang tuong tac: $($posts.Count)."
    } finally { Remove-Item -Recurse -Force $dir }
}

$existing = $null
try { $existing = Login 'phuc' } catch { }
if ($existing) {
    # Demo đã nạp trước: chỉ bổ sung phần còn thiếu (Di sản SCRUM-114, hồ sơ và bài tương tác SCRUM-129), phần khác giữ nguyên.
    $famId = (Call GET '/users/me/membership' $null $existing).familyId
    if (@(Call GET '/heritage/stories?page=0&size=1' $null $existing).Count -eq 0) {
        Write-Host 'Du lieu demo da co san, bo sung phan Di san...'
        Seed-Heritage $existing $famId
    }
    Write-Host 'Du lieu demo da co san, bo sung ho so ca nhan va bai dang tuong tac...'
    Seed-Social
    exit 0
}

Write-Host '1/8 Tao tai khoan...'
$acc = [ordered]@{
    phuc = @('Nguyễn Văn Phúc', '1950-05-10'); hung = @('Nguyễn Văn Hùng', '1972-08-20'); minh = @('Nguyễn Văn Minh', '1978-11-02')
    ha = @('Nguyễn Thị Hà', '1994-04-09'); lan = @('Nguyễn Thị Lan', '1996-07-15'); khoa = @('Lê Văn Khoa', '1993-01-25')
    tu = @('Đỗ Minh Tú', '2000-03-03'); quy = @('Trần Văn Quý', '1960-10-10')
}
$uid = @{}; $tok = @{}
foreach ($k in $acc.Keys) { $uid[$k] = Register $k $acc[$k][0] $acc[$k][1]; $tok[$k] = Login $k }

Write-Host '2/8 Tao dong ho, chi...'
$fam = (Call POST '/families' @{ name = 'Họ Nguyễn – Nam Định (demo)'; description = 'Dữ liệu giả để demo' } $tok.phuc).id
$famTran = (Call POST '/families' @{ name = 'Họ Trần – Huế (demo)'; description = 'Dữ liệu giả để demo' } $tok.quy).id
$b1 = (Call POST "/families/$fam/branches" @{ name = 'Chi 1 (chi trưởng)' } $tok.phuc).id
$b2 = (Call POST "/families/$fam/branches" @{ name = 'Chi 2' } $tok.phuc).id
$b2a = (Call POST "/families/$fam/branches" @{ name = 'Chi 2a'; parentBranchId = $b2 } $tok.phuc).id
$b3 = (Call POST "/families/$fam/branches" @{ name = 'Chi 3' } $tok.phuc).id

Write-Host '3/8 Tao cay gia pha 5 doi (53 nguoi)...'
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

Write-Host '4/8 Xin vao gia dinh va duyet...'
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

Write-Host '5/8 Tao thong bao, su kien, bai dang...'
$null = Call POST "/announcements" @{ title = 'Giỗ Tổ họ Nguyễn ngày 12/3 âm lịch'; content = 'Mời con cháu các chi về nhà thờ họ dự giỗ Tổ. Mỗi chi cử 2 người phụ bếp.'; branchId = $null; isPinned = $true } $tok.phuc
$null = Call POST '/events' @{ title = 'Giỗ Tổ họ Nguyễn'; description = 'Lễ giỗ cụ Nguyễn Văn Tổ, họp mặt toàn họ.'; startAt = '2026-12-20T08:00:00+07:00'; endAt = '2026-12-20T14:00:00+07:00'; location = 'Nhà thờ họ Nguyễn, Hải Hậu, Nam Định'; branchId = $null } $tok.phuc
$null = Call POST '/events' @{ title = 'Họp Chi 2 tổng kết năm'; description = 'Tổng kết quỹ khuyến học và kế hoạch năm sau.'; startAt = '2026-12-27T18:00:00+07:00'; location = 'Nhà chú Hùng, Hà Nội'; branchId = $b2 } $tok.hung
$null = Call POST '/posts' @{ content = 'Họ ta chuẩn bị in lại cuốn gia phả. Các chi gửi bổ sung thông tin con cháu trước 30/11.'; type = 'NEWS'; branchId = $null } $tok.phuc
$null = Call POST '/posts' @{ content = 'Chi 1 họp bàn sửa mộ cụ Tổ vào Chủ nhật này, 8h sáng tại nhà bác Phúc.'; type = 'POST'; branchId = $b1 } $tok.minh
$null = Call POST '/posts' @{ content = 'Chi 2 góp quỹ khuyến học năm nay, hạn cuối 30/11. Cháu nào đạt học sinh giỏi báo chú Hùng.'; type = 'POST'; branchId = $b2 } $tok.hung
$null = Call POST '/posts' @{ content = 'Cuối tuần này Chi 2a gặp mặt mừng sinh nhật bé Bin 6 tuổi ở nhà em nhé cả nhà.'; type = 'POST'; branchId = $b2a } $tok.lan
$null = Call POST '/posts' @{ content = 'Cảm ơn cả họ đã đón con rể mới. Hẹn gặp mọi người ở giỗ Tổ!'; type = 'POST'; branchId = $null } $tok.khoa
$null = Call POST '/posts' @{ content = 'Họ Trần lịch tảo mộ cuối năm tại Huế, mời con cháu sắp xếp về.'; type = 'POST'; branchId = $null } $tok.quy

Write-Host '6/8 Tao ho so nghe nghiep (ten co quan la gia)...'
foreach ($j in @(@('hung','Kỹ sư xây dựng','Công ty Xây dựng Demo','Hà Nội'), @('minh','Giáo viên Toán','Trường THPT Demo','Nam Định'),
                 @('an','Bác sĩ','Phòng khám Demo','Đà Nẵng'), @('long','Kế toán','Công ty Cảng Demo','Hải Phòng'),
                 @('quang','Lập trình viên','Công ty Phần mềm Demo','TP. Hồ Chí Minh'), @('ha','Chuyên viên dữ liệu','Ngân hàng Demo','Hà Nội'),
                 @('khoa','Kỹ sư điện','Công ty Điện Demo','TP. Hồ Chí Minh'), @('tuan','Kỹ thuật viên','Xưởng Cơ khí Demo','Nam Định'))) {
    # BR-DIR-03: người có tài khoản tự khai hồ sơ của mình; Trưởng họ chỉ nhập thay người chưa có tài khoản.
    $who = if ($tok.Contains($j[0])) { $tok[$j[0]] } else { $tok.phuc }
    $null = Call PUT "/directory/members/$($p[$j[0]])/profession-profile" @{ jobTitle = $j[1]; company = $j[2]; workLocation = $j[3] } $who
}

Write-Host '7/8 Tao du lieu Di san (cau chuyen, tai lieu, anh minh hoa gia)...'
Seed-Heritage $tok.phuc $fam $p['to']

Write-Host '7b/8 Ho so ca nhan, anh dai dien va bai dang co binh luan...'
Seed-Social

Write-Host '8/8 Tu kiem tra...'
$all = @(Call GET "/families/$fam/persons?page=0&size=100" $null $tok.phuc)
Write-Host "So nguoi trong ho Nguyen: $($all.Count)"
if ($all.Count -ne $p.Count) { throw "Mong doi $($p.Count) nguoi, API tra ve $($all.Count)" }
# Mỗi dòng: A, B, có hỗ trợ không. Từ 17/11/2026 mọi cặp có tổ tiên chung đều có tên (algorithm-relationship.md mục 2.3b).
foreach ($q in @(@('minh','ha',$true), @('bin','hung',$true), @('kiet','phuc',$true), @('mthu','phuc',$true), @('an','phuc',$true),
                 @('huy','minh',$true), @('thinh','to',$true), @('phong','to',$true))) {
    $r = Call GET "/relationships?personA=$($p[$q[0]])&personB=$($p[$q[1]])" $null $tok.phuc
    Write-Host "  $($q[0]) -> $($q[1]): $($r.description)"
    if ([bool]$r.supported -ne $q[2]) { throw "Quan he $($q[0]) -> $($q[1]) tra supported=$($r.supported), mong doi $($q[2])" }
}

Write-Host 'Xong.'
Write-Host "Ma gia dinh ho Nguyen (dung khi xin vao gia dinh): $fam"
Write-Host "Ma Chi 1: $b1 | Ma Chi 2: $b2 | Ma Chi 2a: $b2a | Ma Chi 3: $b3"
Write-Host "Ma gia dinh ho Tran: $famTran"
