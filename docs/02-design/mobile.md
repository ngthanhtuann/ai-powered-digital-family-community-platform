# Thiết kế Mobile (React Native + Expo)

> **Người viết:** TV5 (Lê Nhựt) | **Reviewer:** TV3 (PiLo257) | **Task Jira:** SCRUM-84 | **Hạn nộp review:** Thứ Năm 15/10 (Sprint 2)
> **Trạng thái:** Nháp 
> Cách làm: tạo nhánh `docs/<mã SCRUM>-<tên-ngắn>` từ `develop`, điền vào file này, mở Pull Request vào `develop`. Xem `docs/README.md`.

## 1. Chọn công nghệ
> React Native + Expo (đa nền tảng iOS/Android, cùng hệ React với web Next.js). So sánh ngắn với Flutter. Ghi quyết định ở `../decisions/adr-mobile.md`.

## 2. Phạm vi mobile
| Chức năng | Có trên mobile | Ghi chú |
|---|---|---|
| Đăng nhập, đăng ký, hồ sơ | Có | |
| Bảng tin, tạo bài, ảnh | Có | |
| Sự kiện, RSVP, thông báo | Có | |
| Cây gia phả (xem), tra quan hệ | Có | |
| Chat AI, tìm kiếm, tóm tắt | Có | |
| Danh bạ, Heritage | Có | ưu tiên thấp nhất, cắt đầu tiên nếu trễ |
| Admin, Dashboard chi tiết | Không (chỉ web) | |

## 3. Sơ đồ điều hướng
> Tab bar + stack. Vẽ Mermaid hoặc ảnh.

## 4. Kiến trúc mobile
> Cấu trúc thư mục, quản lý trạng thái, lưu token (`expo-secure-store`), gọi cùng REST API, xử lý offline cơ bản.

## 5. Wireframe
> Link Figma ở `../05-ui/figma-links.md`.
