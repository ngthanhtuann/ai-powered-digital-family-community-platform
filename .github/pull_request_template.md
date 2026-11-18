## Task Jira
SCRUM-79: https://jira.example.com/browse/SCRUM-79

## Loại thay đổi
- [x] Tài liệu (`docs/`)
- [ ] Code (backend / frontend / mobile)

## Đã làm
- Biên soạn chi tiết báo cáo kiểm thử và kịch bản demo hệ thống trên môi trường máy sạch (Clean Deployment qua Docker) cho dự án FamilyConnect (Mã kế hoạch: 8-09).
- Bổ sung bảng ghi nhận lỗi (Bug Tracking) và hướng khắc phục các lỗi Critical/Major.
- Chuẩn bị nội dung thuyết trình bảo vệ module (3-4 phút) và bộ 3 câu hỏi bảo vệ khó kèm đáp án chi tiết.

## Cách kiểm tra
1. Kiểm tra tài liệu báo cáo tại đường dẫn: `docs/09-report/SCRUM-79-demo-test-report.md`.
2. Khởi chạy thử hệ thống qua Docker Compose (`docker-compose up --build -d`) và kiểm tra các service hoạt động ổn định.

## Checklist
- [x] Tên nhánh và commit có mã `SCRUM-xx` (Nhánh: `feature/SCRUM-79-demo-test`)
- [x] Đã tự kiểm tra theo **Tiêu chí hoàn thành** của task
- [x] (Tài liệu) Đủ các mục theo khung sườn, dùng đúng mã FR và quy ước API
- [ ] (Code) Có unit test, đã cập nhật Swagger, không còn secret trong code
- [x] Đã chọn **Reviewer** đúng người test chéo

