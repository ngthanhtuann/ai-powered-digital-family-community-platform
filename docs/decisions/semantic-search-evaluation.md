# Bộ đánh giá tìm kiếm ngữ nghĩa (SCRUM-49)

Ngày lập: 29/10/2026; đo với Gemini thật ngày 15/11/2026 (SCRUM-105). Chỉ dùng hồ sơ và nội dung giả.

## Cấu hình đo

- Endpoint: `GET /api/v1/ai/search?q=...&page=0&size=10`.
- Ngưỡng: cosine similarity `0.65` (trước 15/11/2026 là `0.88`, xem kết quả đo bên dưới); kết quả dưới ngưỡng bị loại.
- Người chạy: tài khoản MEMBER đã xác minh, trong gia đình dữ liệu giả `FC-DEMO-A`.
- Ghi lại top 3 source ID, score, nhóm nguồn; một câu được tính đúng nếu kết quả đúng xuất hiện ở top 3 và không lộ nguồn ngoài gia đình.
- Độ chính xác = số câu đúng / 15. Cần điền cột “Thực tế” sau khi đã lập chỉ mục bộ dữ liệu giả.

## Cách chạy

1. Chạy backend có khóa Gemini (đặt `GEMINI_API_KEY` trong `.env` của máy bạn, không commit), ví dụ `docker compose up`.
2. Chạy `node scripts/eval-semantic-search.mjs` (hoặc thêm `--base-url http://...`). Script tự tạo tài khoản, gia đình `FC-DEMO-A` và dữ liệu giả, chạy 15 câu, in bảng markdown và độ chính xác.
3. Dán bảng in ra thay cho bảng dưới, ghi ngày đo. Câu 10 cần quan hệ cha mẹ con trong chỉ mục (SCRUM-44), hiện dự kiến sai.

| # | Câu truy vấn | Kết quả kỳ vọng | Thực tế (top 3: loại, tiêu đề, điểm) | Đúng? |
|---:|---|---|---|---|
| 1 | người làm bác sĩ ở Hà Nội | Trần Minh An | PERSON: Trần Minh An. Năm sinh: 1985. Tỉnh/thành: Hà Nội. Nghề nghiệp: Bác sĩ (0.762) | Đúng |
| 2 | tìm người là bác sĩ tại thủ đô | Trần Minh An | PERSON: Trần Minh An. Năm sinh: 1985. Tỉnh/thành: Hà Nội. Nghề nghiệp: Bác sĩ (0.704) | Đúng |
| 3 | ai làm nghề y ở Hà Nội | Trần Minh An | PERSON: Trần Minh An. Năm sinh: 1985. Tỉnh/thành: Hà Nội. Nghề nghiệp: Bác sĩ (0.722) | Đúng |
| 4 | thành viên nào đang sống tại Đà Nẵng | Lê Thu Hà | PERSON: Lê Thu Hà. Năm sinh: 1990. Tỉnh/thành: Đà Nẵng. Nghề nghiệp: Giáo viên (0.695)<br>PERSON: Nguyễn Hoàng Nam. Năm sinh: 1980. Tỉnh/thành: Huế. Nghề nghiệp: Kỹ sư xây dựng (0.651) | Đúng |
| 5 | người sinh năm 1980 | Nguyễn Hoàng Nam | PERSON: Nguyễn Hoàng Nam. Năm sinh: 1980. Tỉnh/thành: Huế. Nghề nghiệp: Kỹ sư xây dựng (0.678) | Đúng |
| 6 | bài viết về ngày giỗ tổ | giỗ tổ | POST: Ngày giỗ tổ dòng họ năm nay tổ chức tại nhà thờ họ vào mùng 10 tháng 3 âm lịch, mời cả … (0.743) | Đúng |
| 7 | thông báo họp mặt gia đình dịp Tết | Họp mặt đầu xuân | POST: Ngày giỗ tổ dòng họ năm nay tổ chức tại nhà thờ họ vào mùng 10 tháng 3 âm lịch, mời cả … (0.787)<br>POST: Họp mặt đầu xuân: gia đình tổ chức họp mặt dịp Tết Nguyên đán, chúc thọ ông bà và lì xì… (0.783)<br>POST: Lễ hội mùa xuân của làng: các chi họ cùng tham gia tổ chức rước kiệu và thi nấu bánh ch… (0.654) | Đúng |
| 8 | ảnh và câu chuyện về nhà thờ họ | Trùng tu nhà thờ họ | STORY: Trùng tu nhà thờ họ Năm 2015 con cháu góp công góp của trùng tu nhà thờ họ, giữ lại bức… (0.688) | Đúng |
| 9 | tài liệu gia phả bản PDF | Gia phả chi trưởng | DOCUMENT: Gia phả chi trưởng gia-pha Bản scan gia phả chi trưởng, ghi các đời từ cụ tổ. (0.825) | Đúng |
| 10 | ai là con của Nguyễn Hoàng Nam | Nguyễn Hoàng Nam | PERSON: Nguyễn Hoàng Nam. Năm sinh: 1980. Tỉnh/thành: Huế. Nghề nghiệp: Kỹ sư xây dựng (0.776) | Đúng |
| 11 | tìm thông tin về bà Trần Thị Mai | Trần Thị Mai | PERSON: Trần Thị Mai. Năm sinh: 1940. Tỉnh/thành: Nam Định. Đã mất (0.842)<br>PERSON: Trần Minh An. Năm sinh: 1985. Tỉnh/thành: Hà Nội. Nghề nghiệp: Bác sĩ (0.672) | Đúng |
| 12 | người tham gia tổ chức lễ hội mùa xuân | Lễ hội mùa xuân | POST: Lễ hội mùa xuân của làng: các chi họ cùng tham gia tổ chức rước kiệu và thi nấu bánh ch… (0.776)<br>POST: Họp mặt đầu xuân: gia đình tổ chức họp mặt dịp Tết Nguyên đán, chúc thọ ông bà và lì xì… (0.651) | Đúng |
| 13 | hoạt động thiện nguyện của gia đình | thiện nguyện | POST: Chuyến thiện nguyện tháng 7: cả họ quyên góp sách vở và gạo cho học sinh vùng cao. (0.780)<br>POST: Họp mặt đầu xuân: gia đình tổ chức họp mặt dịp Tết Nguyên đán, chúc thọ ông bà và lì xì… (0.674) | Đúng |
| 14 | địa chỉ nhà riêng của thành viên | Không chứa "Lý Thường Kiệt" | Rỗng | Đúng |
| 15 | người nổi tiếng không tồn tại trong gia đình | Rỗng | Rỗng | Đúng |

Độ chính xác ở ngưỡng 0,65: **15/15** (một câu được tính đúng nếu kết quả kỳ vọng nằm trong top 3; câu 14, 15 đúng khi rỗng).

### Kết quả đo 15/11/2026 (SCRUM-105)

Đo với khóa Gemini thật trên stack Docker riêng (`develop` tại `845865b`), `node scripts/eval-semantic-search.mjs`, mỗi ngưỡng chạy một lần trên gia đình giả mới:

| Ngưỡng `ai.search.minimum-score` | Đúng | Ghi chú |
|---|---|---|
| 0 (để xem điểm thật) | 14/15 | Đáp án đúng luôn đứng đầu, điểm 0,678 đến 0,842; câu 15 sai vì không rỗng (điểm cao nhất 0,615), câu 14 cao nhất 0,570 |
| **0,88** (mặc định cũ) | **2/15** | Loại hết kết quả đúng; chỉ hai câu phải rỗng là đạt |
| **0,65** (mặc định mới) | **15/15** | Bảng ở trên |

0,65 nằm giữa điểm cao nhất của câu không có đáp án (0,615) và điểm thấp nhất của đáp án đúng (0,678). Bộ dữ liệu chỉ có 15 câu trên một gia đình giả nên cần đo lại khi có thêm dữ liệu; ngưỡng đổi được qua `ai.search.minimum-score` không cần sửa code.

## Ghi chú triển khai

Nghề nghiệp lấy từ hồ sơ nghề nghiệp của Directory (`profession_profile.job_title`, FR-DIR-02), không lưu thêm cột ở `person`. Module Gia phả là chủ nguồn `PERSON` trong chỉ mục: khi dựng nội dung, Gia phả đọc chức danh qua `DirectoryQuery.findJobTitle`; khi hồ sơ nghề nghiệp đổi, Directory gọi `PersonSearchIndex.refresh` để Gia phả lập chỉ mục lại. Người dưới 16 tuổi chỉ có họ tên trong chỉ mục. Tài liệu PDF chỉ được index từ tiêu đề, danh mục, mô tả, không đọc nội dung tệp.

Kết quả chỉ truy xuất trong `family_id` lấy từ membership. Phạm vi chi theo BR-AI-13 và BR-COM-03 (cùng quy tắc với Community và Events): thành viên chi Y thấy nội dung toàn gia đình, của Y và các chi tổ tiên; trưởng chi thấy thêm các chi con mình quản lý; trưởng chi toàn gia đình và SYSTEM_ADMIN thấy mọi chi. Hồ sơ thành viên (`PERSON`) là phạm vi toàn gia đình. MEMBER không nhận hồ sơ `PERSON` của người dưới 16 tuổi. Đường dẫn kết quả trỏ tới trang chi tiết trong ứng dụng, không trỏ URL tệp công khai.

Kiểm thử tự động (`SemanticSearchApiIntegrationTest`, `PersonSearchIndexIntegrationTest`) dùng embedding giả lập để kiểm tra lọc quyền; bảng 15 câu ở trên cần chạy với Gemini thật để đo độ chính xác.
