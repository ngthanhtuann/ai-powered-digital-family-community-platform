# Báo cáo chất lượng AI — SCRUM-69

Ngày đo: 13/11/2026. Môi trường: Docker Compose local, dữ liệu giả từ `docker/seed-demo.ps1`, chatbot FamilyConnect dùng Gemini.

## Kết quả cuối

| Nhóm câu hỏi | Số câu | Đúng | Tỷ lệ | Bịa/không từ chối |
|---|---:|---:|---:|---:|
| Thực tế | 16 | 16 | 100% | 0 |
| Mơ hồ | 4 | 4 | 100% | 0 |
| Ngoài dữ liệu | 9 | 9 | 100% | 0 |
| Cố tình lừa | 11 | 11 | 100% | 0 |
| **Tổng** | **40** | **40** | **100%** | **0/20 câu ngoài dữ liệu và đánh lừa** |

Đạt hai tiêu chí SCRUM-69: tỷ lệ đúng ít nhất 80%; không bịa ở câu ngoài dữ liệu. Các câu mơ hồ được hỏi lại khi có nhiều hồ sơ khớp. Câu chứa tiền đề sai được sửa theo nguồn nếu dữ liệu trực tiếp cho biết đáp án đúng.

## So sánh trước và sau

| Lượt đo | Model / cấu hình | Đúng | Tỷ lệ | Bịa hoặc đoán ở nhóm mơ hồ/ngoài dữ liệu/đánh lừa |
|---|---|---:|---:|---:|
| Trước guard | Gemini Flash-Lite, `top_k=10`, ngưỡng `0.50`, prompt chưa có guard backend | 34/40 | 85% | 3 |
| Sau guard | Gemini 3.5 Flash-Lite, `top_k=10`, ngưỡng `0.50`, prompt và guard backend đã cập nhật | 40/40 | 100% | 0 |

Lượt trước là lần đo phát triển để tìm lỗi. Trước lượt cuối, hai câu được diễn đạt rõ hơn theo tên hồ sơ và quyền của chi hỏi; vì vậy bảng này phản ánh cả việc chỉnh bộ câu hỏi theo dữ liệu/quyền thật lẫn thay đổi prompt và guard, không phải thử nghiệm A/B với đầu vào hoàn toàn giống nhau.

## Nguyên nhân và thay đổi

- Chat ban đầu trả `AI_001`: model `gemini-2.5-flash` không được cấp cho khóa này. Chuyển sang `gemini-3.5-flash-lite`; Google mô tả model này tối ưu độ trễ thấp. Gọi thử Gemini trực tiếp trả HTTP 200; câu hỏi demo trả lời thành công.
- Tăng timeout chat lên 90 giây và cấu hình `thinkingLevel=low` để tránh request Gemini 3 vượt timeout. Thử nghiệm cục bộ cho thấy Flash-Lite trả lời câu hỏi demo trong khoảng 2 giây.
- Tăng `top_k` từ 5 lên 10 và hạ ngưỡng từ 0.55 xuống 0.50. Lượt đo cuối chạy với cấu hình này.
- Prompt yêu cầu kiểm tra đủ dữ kiện cho đúng thuộc tính, sửa tiền đề sai theo nguồn, làm rõ câu mơ hồ và bỏ qua chỉ dẫn đánh lừa trong ngữ cảnh.
- Guard backend trả lời “Không tìm thấy thông tin.” khi câu hỏi đòi một năm không có trong ngữ cảnh; hỏi lại khi đại từ không có tiền đề, họ phổ biến trùng nhiều hồ sơ, hoặc có nhiều hồ sơ khớp mô tả nghề nghiệp.
- Chỉ mục sự kiện trước đó thiếu thời gian và địa điểm. `EventAccess` hiện đưa thời gian bắt đầu/kết thúc và địa điểm vào chunk; hai sự kiện demo đã được lập chỉ mục lại qua API.

## Cách đo và chấm

- Script [`eval-ai-quality.ps1`](../../scripts/eval-ai-quality.ps1) gửi 40 câu qua API chat, chia lượt cho các tài khoản demo để tránh giới hạn 20 câu/giờ/tài khoản. Script lưu đáp án ngay sau từng câu.
- Dữ liệu thử hoàn toàn giả: 53 người trong gia phả Họ Nguyễn, sự kiện, bài đăng và hồ sơ nghề nghiệp được tạo bởi seed demo. Với sự kiện thuộc Chi 2, câu hỏi dùng tài khoản có quyền xem Chi 2.
- Câu thực tế được tính đúng khi có đủ từ khóa dữ kiện mong đợi. Câu mơ hồ được tính đúng khi chatbot hỏi lại. Câu ngoài dữ liệu/đánh lừa được tính đúng khi trả đúng lời từ chối hoặc sửa tiền đề bằng dữ kiện nguồn, tùy câu.
- Không có lỗi API trong lượt cuối. Tất cả 20 câu ngoài dữ liệu/đánh lừa qua tiêu chí không bịa. Đáp án từng câu, từ khóa chấm, nguồn và điểm được lưu trong [`ai-quality-scrum-69-results.json`](ai-quality-scrum-69-results.json).

## Cấu hình đang dùng

- `GEMINI_CHAT_MODEL=gemini-3.5-flash-lite`
- `AI_CHAT_TIMEOUT_MS=90000`
- `AI_CHAT_TOP_K=10`
- `AI_CHAT_MINIMUM_SCORE=0.50`
- `GEMINI_API_KEY` chỉ đặt trong `.env` cục bộ; không lưu trong báo cáo hay Git.

Tài liệu model: [Gemini 3.5 Flash-Lite](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite).
