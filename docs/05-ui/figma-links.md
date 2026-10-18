# Wireframe và link Figma

> Mỗi người vẽ màn hình của module mình theo design system của TV5, có cả bản **mobile (điện thoại)**. Chỉ dán link Figma (quyền xem hoặc comment), **không xuất ảnh wireframe vào repo**; muốn xem thì mở link.

## File Figma chung

Cả nhóm vẽ trong một file: [FamilyConnect — Java](https://www.figma.com/design/LDT41oUdTnmwaUugJlvrLK/Java). Mỗi module một page:

| Page | Nội dung |
|---|---|
| `00 Cover` | Tên dự án, mục lục, quy ước đặt tên khung, bảng người phụ trách |
| `01 Design system` | Biến màu, text style, effect style, component và màn hình Tổng quan 3 cỡ (theo `design-system.md`) |
| `02` đến `06` | Mỗi page có 2 section: "User flow" và "Màn hình (360 / 768 / 1440)" |
| `99 Nháp` | Vẽ thử, không dùng để review |

Quy ước đặt tên khung: `<Mã UC> Tên màn hình / <cỡ>`, ví dụ `UC-AUTH-02 Đăng nhập / 360`.

Màu và chữ dùng biến (collection `Color`, `Size`) và style có sẵn trong file, không nhập mã màu tay. Tên biến và token tương ứng xem mục 7 của `design-system.md`.

**Trước khi vẽ:** đọc khung "Hướng dẫn dùng (đọc trước khi vẽ)" trên page `01 Design system` (cũng có ở `design-system.md` mục 8). Header, sidebar, điều hướng mobile lấy từ `Layout/Header`, `Layout/Sidebar`, `Layout/Điều hướng mobile`; chọn `Vai trò` và mục đang chọn theo `design-system.md` mục 5.2. Màn mẫu làm đúng cách: page `02 Auth và Admin`.

## Link theo module

| Module | Người vẽ | Task Jira | Link Figma | Trạng thái |
|---|---|---|---|---|
| Design system | TV5 Lê Nhựt | SCRUM-25 | [01 Design system](https://www.figma.com/design/LDT41oUdTnmwaUugJlvrLK/Java?node-id=4-112) | Chờ review |
| Gia phả (danh sách, cây, chi tiết, tra quan hệ) | TV2 Trí | SCRUM-29 | [03 Gia phả](https://www.figma.com/design/LDT41oUdTnmwaUugJlvrLK/Java?node-id=4-217) | |
| Community và Events | TV3 PiLo257 | SCRUM-30 | [04 Community và Events](https://www.figma.com/design/LDT41oUdTnmwaUugJlvrLK/Java?node-id=152-103) | Chờ review |
| Heritage, Directory, Dashboard | TV4 Huy Quốc | SCRUM-32 | [05 Heritage, Directory, Dashboard](https://www.figma.com/design/LDT41oUdTnmwaUugJlvrLK/Java?node-id=4-221) | |
| Mobile | TV5 Lê Nhựt | SCRUM-84 | [06 Mobile](https://www.figma.com/design/LDT41oUdTnmwaUugJlvrLK/Java?node-id=4-223) | Chờ review |
| Auth, Admin | TV1 Tuấn | SCRUM-26 | [02 Auth và Admin](https://www.figma.com/design/LDT41oUdTnmwaUugJlvrLK/Java?node-id=4-215) | |

Trang bìa: [00 Cover](https://www.figma.com/design/LDT41oUdTnmwaUugJlvrLK/Java?node-id=4-57). Nháp: [99 Nháp](https://www.figma.com/design/LDT41oUdTnmwaUugJlvrLK/Java?node-id=3-4).

File cũ của TV5 ([Untitled](https://www.figma.com/design/tblrnrxE2SZWtuI3SNPOxp/Untitled?node-id=0-1)) không dùng nữa; nội dung đã chép sang page `01 Design system` và sửa màu theo `design-system.md`.

## Không lưu ảnh wireframe

Figma là nguồn duy nhất của wireframe. Không xuất ảnh khung vào `docs/images/` để tránh ảnh cũ lệch với Figma và làm repo nặng. Người ngoài nhóm mở link với quyền xem. Ảnh chụp màn hình cho báo cáo cuối kỳ lấy từ ứng dụng thật (`report-outline.md` mục 4).
