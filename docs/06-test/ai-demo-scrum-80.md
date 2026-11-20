# Kịch bản demo AI — SCRUM-80

> Demo dùng dữ liệu giả của `docker/seed-demo.ps1`, tài khoản demo đã xác minh và Gemini API.
> Đáp án dự phòng đã chụp từ lượt đánh giá ngày 15/11/2026; đây là kết quả lưu sẵn, không phải câu trả lời trực tiếp từ API khi trình bày offline.

## 1. Chuẩn bị trước buổi demo

1. Dùng máy/stack đã được nhóm chuẩn bị, đặt `GEMINI_API_KEY` trong `.env` cục bộ (không chia sẻ hoặc commit key). Khởi động `docker compose up -d --build`, chờ `docker compose ps` báo DB/backend/frontend healthy.
2. Nạp seed giả một lần bằng `powershell -ExecutionPolicy Bypass -File docker\seed-demo.ps1`. Script dừng nếu dữ liệu demo đã tồn tại. Tài khoản seed dùng email dạng `<tên>@demo.familyconnect.vn`, mật khẩu mặc định `Demo@2026`; chỉ dùng trên máy demo cô lập.
3. Mở `http://localhost:3000/assistant`, đăng nhập trước với tài khoản đúng cho từng câu bên dưới. Mỗi tài khoản có giới hạn 20 câu chat/giờ; câu gợi ý cũng tiêu tốn lượt. Tránh chạy bộ eval ngay trước demo.
4. Thử trước 1 câu thực tế và câu từ chối. Nếu API lỗi, chuyển sang mục 4; không tiếp tục bấm gọi liên tục để thử vượt quota.

## 2. Năm câu hỏi demo đã có đáp án đo

| # | Tài khoản demo | Câu hỏi nhập nguyên văn | Kết quả kỳ vọng (dữ liệu seed) | Nguồn dự kiến |
|---:|---|---|---|---|
| 1 | `phuc` | Ông Phúc sinh năm nào? | Ông Nguyễn Văn Phúc sinh năm **1950**. | Hồ sơ thành viên (`PERSON`) |
| 2 | `phuc` | Nguyễn Văn An làm nghề gì? | Anh Nguyễn Văn An làm nghề **Bác sĩ**. | Hồ sơ thành viên (`PERSON`) |
| 3 | `lan` | Anh Hùng làm nghề gì? | Anh Nguyễn Văn Hùng làm **Kỹ sư xây dựng**. | Hồ sơ thành viên (`PERSON`) |
| 4 | `hung` | Giỗ Tổ họ Nguyễn tổ chức ở đâu? | Tại **Nhà thờ họ Nguyễn, Hải Hậu, Nam Định**. | Sự kiện (`EVENT`) |
| 5 | `minh` | Sự kiện giỗ Tổ tưởng niệm ai? | Tưởng niệm cụ **Nguyễn Văn Tổ**. | Sự kiện (`EVENT`) |

Mỗi câu trong bảng tương ứng với case thực tế được chấm đúng trong [kết quả JSON SCRUM-69](ai-quality-scrum-69-results.json). Câu trả lời live có thể khác cách diễn đạt; kiểm tra dữ kiện và source citation, không đòi khớp từng chữ.

### Câu hỏi về chính người hỏi (BR-AI-17, PR #161)

Đăng nhập `ha`, hỏi **“Tôi là ai?”** và **“Tôi là gì của Nguyễn Văn Tổ?”**. Kết quả đã thấy khi chạy thử ngày 18/11/2026 trên dữ liệu seed: “Bạn là Nguyễn Thị Hà.”, “Bạn là cháu nội của Nguyễn Văn Tổ.” Quan hệ do thuật toán gia phả tính sẵn; câu này không nằm trong bộ 40 câu SCRUM-69.

### Câu thể hiện từ chối

Đăng nhập `lan`, hỏi: **“Quỹ khuyến học hiện có bao nhiêu tiền?”**. Quỹ không có số dư trong dữ liệu seed; kết quả mong đợi là **“Không tìm thấy thông tin.”**, không đoán số và không có nguồn được trích. Case này cũng được chấm đúng trong lượt SCRUM-69.

## 3. Nhịp demo gợi ý (5–7 phút)

1. Mở trợ lý, giải thích đây là hỏi đáp trên dữ liệu giả đã được lập chỉ mục.
2. Hỏi câu 1 (thành viên) và chỉ ra citation; chuyển sang câu 4 hoặc 5 (sự kiện) để cho thấy cùng một luồng truy xuất nhiều loại dữ liệu.
3. Hỏi câu về quỹ để trình bày giới hạn: thiếu dữ kiện thì từ chối, không bịa.
4. Tóm tắt guard quyền xem: backend giới hạn theo family/branch và ẩn hồ sơ trẻ em trước khi gửi context cho LLM.
5. Nếu thời gian cho phép, dùng câu 2/3 hoặc mở một nguồn citation để kiểm tra.

## 4. Phương án dự phòng khi Gemini/API lỗi

Ứng dụng hiện **không có chế độ chatbot offline**. Khi Gemini timeout/quota/network lỗi, API trả `AI_001` và giao diện hiện thông báo thử lại. Không giả lập đó là câu trả lời live.

1. Chuyển sang bảng **Kết quả đã lưu để trình bày offline** ở dưới. Nói rõ đây là kết quả đã ghi trong lượt đánh giá SCRUM-69 ngày 15/11/2026, không phải phản hồi trực tiếp lúc demo.
2. Có thể dùng ảnh chụp trước buổi demo của màn hình assistant với câu hỏi, trả lời, nguồn; không chụp/đưa lên ảnh key, token, dữ liệu gia đình thật.
3. Tiếp tục demo UI/API không phụ thuộc Gemini (mở hồ sơ, sự kiện, màn hình tìm kiếm/nguồn đã lưu) hoặc trình bày kiến trúc RAG. Nếu stack local ngừng hoạt động, dùng ảnh chụp và bảng ở dưới.
4. Không thay câu trả lời lỗi bằng văn bản lưu sẵn ngay trong giao diện sản phẩm và không nói rằng chatbot vẫn hoạt động offline.

### Kết quả đã lưu để trình bày offline

| Câu hỏi | Đáp án đã được chấm | Nguồn theo lượt đo |
|---|---|---|
| Ông Phúc sinh năm nào? | 1950 | `PERSON` |
| Nguyễn Văn An làm nghề gì? | Bác sĩ | `PERSON` |
| Anh Hùng làm nghề gì? | Kỹ sư xây dựng | `PERSON` |
| Giỗ Tổ họ Nguyễn tổ chức ở đâu? | Nhà thờ họ Nguyễn, Hải Hậu, Nam Định | `EVENT` |
| Sự kiện giỗ Tổ tưởng niệm ai? | Nguyễn Văn Tổ | `EVENT` |
| Quỹ khuyến học hiện có bao nhiêu tiền? | Không tìm thấy thông tin. | Không có |

Đáp án và citation gốc được giữ trong `ai-quality-scrum-69-results.json`, các case số 1, 7, 5, 14, 15 và 23. JSON rút gọn để tra nhanh: [`ai-demo-scrum-80-results.json`](ai-demo-scrum-80-results.json).

## 5. Kiểm tra kết thúc

- Năm câu dữ kiện trả đúng thuộc tính và có citation liên quan.
- Câu hỏi về quỹ trả “Không tìm thấy thông tin.”; không khẳng định số tiền.
- Tài khoản chạy câu hỏi thuộc family/chi có quyền; không thử dữ liệu thật trong demo.
- Khi API lỗi, phân biệt rõ ảnh/kết quả lưu sẵn với câu trả lời live.
