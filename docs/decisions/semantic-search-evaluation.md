# Bộ đánh giá tìm kiếm ngữ nghĩa (SCRUM-49)

Ngày lập: 29/10/2026. Chỉ dùng hồ sơ và nội dung giả trong bảng này. Chưa có dữ liệu được index trên một môi trường chạy kèm Gemini, vì vậy cột kết quả thực tế được để chờ đo; không xem các kỳ vọng dưới đây là kết quả đạt.

## Cấu hình đo

- Endpoint: `GET /api/v1/ai/search?q=...&page=0&size=10`.
- Ngưỡng ban đầu: cosine similarity `0.88` (theo `docs/02-design/ai-rag.md`); kết quả dưới ngưỡng bị loại.
- Người chạy: tài khoản MEMBER đã xác minh, trong gia đình dữ liệu giả `FC-DEMO-A`.
- Ghi lại top 3 source ID, score, nhóm nguồn; một câu được tính đúng nếu kết quả đúng xuất hiện ở top 3 và không lộ nguồn ngoài gia đình.
- Độ chính xác = số câu đúng / 15. Cần điền cột “Thực tế” sau khi đã lập chỉ mục bộ dữ liệu giả.

## Cách chạy

1. Chạy backend có khóa Gemini (đặt `GEMINI_API_KEY` trong `.env` của máy bạn, không commit), ví dụ `docker compose up`.
2. Chạy `node scripts/eval-semantic-search.mjs` (hoặc thêm `--base-url http://...`). Script tự tạo tài khoản, gia đình `FC-DEMO-A` và dữ liệu giả, chạy 15 câu, in bảng markdown và độ chính xác.
3. Dán bảng in ra thay cho bảng dưới, ghi ngày đo. Câu 10 cần quan hệ cha mẹ con trong chỉ mục (SCRUM-44), hiện dự kiến sai.

| # | Câu truy vấn | Kết quả kỳ vọng | Thực tế | Đúng? |
|---:|---|---|---|---|
| 1 | người làm bác sĩ ở Hà Nội | Hồ sơ Trần Minh An, bác sĩ, Hà Nội | Chưa chạy | — |
| 2 | tìm người là bác sĩ tại thủ đô | Trần Minh An | Chưa chạy | — |
| 3 | ai làm nghề y ở Hà Nội | Trần Minh An | Chưa chạy | — |
| 4 | thành viên nào đang sống tại Đà Nẵng | Hồ sơ Lê Thu Hà, Đà Nẵng | Chưa chạy | — |
| 5 | người sinh năm 1980 | Hồ sơ Nguyễn Hoàng Nam, năm sinh 1980 | Chưa chạy | — |
| 6 | bài viết về ngày giỗ tổ | Bài đăng giả “Ngày giỗ tổ dòng họ” | Chưa chạy | — |
| 7 | thông báo họp mặt gia đình dịp Tết | Bài đăng giả “Họp mặt đầu xuân” | Chưa chạy | — |
| 8 | ảnh và câu chuyện về nhà thờ họ | Câu chuyện giả “Trùng tu nhà thờ họ” | Chưa chạy | — |
| 9 | tài liệu gia phả bản PDF | Tài liệu giả “Gia phả chi trưởng” (chỉ metadata) | Chưa chạy | — |
| 10 | ai là con của Nguyễn Hoàng Nam | Hồ sơ thành viên có quan hệ cha mẹ phù hợp | Chưa chạy | — |
| 11 | tìm thông tin về bà Trần Thị Mai | Hồ sơ Trần Thị Mai | Chưa chạy | — |
| 12 | người tham gia tổ chức lễ hội mùa xuân | Bài đăng hoặc hồ sơ sự kiện giả “Lễ hội mùa xuân” | Chưa chạy | — |
| 13 | hoạt động thiện nguyện của gia đình | Bài đăng giả “Chuyến thiện nguyện tháng 7” | Chưa chạy | — |
| 14 | địa chỉ nhà riêng của thành viên | Không trả địa chỉ; kết quả chỉ có thể là dữ liệu công khai được phép | Chưa chạy | — |
| 15 | người nổi tiếng không tồn tại trong gia đình | Danh sách rỗng nếu mọi score dưới 0.88 | Chưa chạy | — |

## Ghi chú triển khai

Nghề nghiệp lấy từ hồ sơ nghề nghiệp của Directory (`profession_profile.job_title`, FR-DIR-02), không lưu thêm cột ở `person`. Module Gia phả là chủ nguồn `PERSON` trong chỉ mục: khi dựng nội dung, Gia phả đọc chức danh qua `DirectoryQuery.findJobTitle`; khi hồ sơ nghề nghiệp đổi, Directory gọi `PersonSearchIndex.refresh` để Gia phả lập chỉ mục lại. Người dưới 16 tuổi chỉ có họ tên trong chỉ mục. Tài liệu PDF chỉ được index từ tiêu đề, danh mục, mô tả, không đọc nội dung tệp.

Kết quả chỉ truy xuất trong `family_id` lấy từ membership. Phạm vi chi theo BR-AI-13 và BR-COM-03 (cùng quy tắc với Community và Events): thành viên chi Y thấy nội dung toàn gia đình, của Y và các chi tổ tiên; trưởng chi thấy thêm các chi con mình quản lý; trưởng chi toàn gia đình và SYSTEM_ADMIN thấy mọi chi. Hồ sơ thành viên (`PERSON`) là phạm vi toàn gia đình. MEMBER không nhận hồ sơ `PERSON` của người dưới 16 tuổi. Đường dẫn kết quả trỏ tới trang chi tiết trong ứng dụng, không trỏ URL tệp công khai.

Kiểm thử tự động (`SemanticSearchApiIntegrationTest`, `PersonSearchIndexIntegrationTest`) dùng embedding giả lập để kiểm tra lọc quyền; bảng 15 câu ở trên cần chạy với Gemini thật để đo độ chính xác.
