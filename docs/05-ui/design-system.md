# Design system

> **Người viết:** TV5 (Lê Nhựt) | **Reviewer:** TV3 (PiLo257) | **Task Jira:** SCRUM-25
> **Trạng thái:** Chờ review | **Phiên bản:** 0.6 (layout và menu theo vai trò, bổ sung các variant Sprint 2, bộ icon, mục 5.2 và mục 8 hướng dẫn dùng)
> Liên quan: `figma-links.md` (link Figma), SCRUM-84 (bản mobile).
> Figma: page [01 Design system](https://www.figma.com/design/LDT41oUdTnmwaUugJlvrLK/Java?node-id=4-112) của file chung.

## 1. Bảng màu

Mọi cặp màu chữ/nền dưới đây đã được tính tỉ lệ tương phản theo WCAG 2.1: chữ thường cần ≥ 4.5, chữ lớn và viền thành phần giao diện cần ≥ 3.

### 1.1. Màu thương hiệu và trạng thái

| Vai trò | Mã màu | Dùng cho |
|---|---|---|
| Chủ đạo | `#2C1810` | Sidebar, tiêu đề, chữ chính, nền tiêu đề form |
| Phụ (vàng) | `#C9A84C` | **Chỉ làm nền** của nút chính, mục điều hướng đang chọn, điểm nhấn. **Không dùng làm màu chữ** (tương phản chỉ 2.0 đến 2.3) |
| Thành công | `#2E7D32` | Thông báo thành công, badge "Mới", trạng thái hợp lệ |
| Lỗi | `#C62828` | Lỗi nhập liệu, cảnh báo lỗi, thao tác xóa |
| Info | `#1565C0` | Badge "Đã cập nhật", thông tin, vòng focus |
| Warning | `#BF4400` | Badge "Chờ xác nhận", cảnh báo |

### 1.2. Màu hỗ trợ

| Token | Mã màu | Dùng cho |
|---|---|---|
| Background | `#F5F0E8` | Nền trang |
| Surface | `#FFFFFF` | Card, modal, header |
| Surface muted | `#F9F6F0` | Nền ô nhập, vùng nội dung phụ |
| Text secondary | `#7A6348` | Mô tả, nhãn phụ |
| Border | `#D4C4A8` | Đường phân cách, viền card (trang trí, không bắt buộc nhìn rõ) |
| Gold hover | `#B79538` | Nền hover của nút chính và mục điều hướng (chữ nâu `#2C1810`, tương phản 5.9) |
| Cream on dark | `#F5E6C8` | Chữ trên nền nâu (tiêu đề modal, mục điều hướng; tương phản 13.7) |
| Border strong | `#9C8560` | **Viền ô nhập, select, checkbox** (thành phần tương tác, cần ≥ 3) |
| Success background | `#E8F5E9` | Nền thông báo thành công |
| Error background | `#FCE4EC` | Nền thông báo lỗi |
| Warning background | `#FFF3E0` | Nền thông báo cảnh báo, badge "Chờ xác nhận" |
| Info background | `#E3F2FD` | Nền thông báo thông tin, badge "Đã cập nhật" |

### 1.3. Tỉ lệ tương phản đã kiểm tra

| Chữ / thành phần | Nền | Tỉ lệ | Kết quả |
|---|---|---|---|
| Chủ đạo `#2C1810` | Background `#F5F0E8` | 14.9 | Đạt |
| Chủ đạo `#2C1810` | Vàng `#C9A84C` (nút chính, mục đang chọn) | 7.4 | Đạt |
| Text secondary `#7A6348` | Surface `#FFFFFF` | 5.7 | Đạt |
| Text secondary `#7A6348` | Surface muted `#F9F6F0` | 5.3 | Đạt |
| Text secondary `#7A6348` | Background `#F5F0E8` | 5.0 | Đạt |
| Warning `#BF4400` | Surface `#FFFFFF` | 5.2 | Đạt |
| Warning `#BF4400` | Warning background `#FFF3E0` | 4.7 | Đạt |
| Info `#1565C0` | Surface `#FFFFFF` | 5.8 | Đạt |
| Info `#1565C0` | Info background `#E3F2FD` | 5.0 | Đạt |
| Thành công `#2E7D32` | Success background `#E8F5E9` | 4.6 | Đạt |
| Lỗi `#C62828` | Error background `#FCE4EC` | 4.7 | Đạt |
| Chữ trắng | Lỗi `#C62828` (nút danger) | 5.6 | Đạt |
| Chữ trắng | Warning `#BF4400` | 5.2 | Đạt |
| Border strong `#9C8560` | Surface `#FFFFFF` | 3.5 | Đạt (≥ 3) |
| Border strong `#9C8560` | Background `#F5F0E8` | 3.1 | Đạt (≥ 3) |

Không dùng cặp: vàng `#C9A84C` làm chữ trên nền sáng (2.0 đến 2.3); Border `#D4C4A8` làm viền duy nhất của ô nhập (1.5 đến 1.7).

## 2. Font và cỡ chữ (hỗ trợ tiếng Việt)

| Cấp | Font | Cỡ |
|---|---|---|
| Tiêu đề trang | Inter Bold, 700 | 24px, line-height 32px |
| Tiêu đề card/modal | Inter Semi Bold, 600 | 18px, line-height 26px |
| Nội dung | Inter Regular, 400 | 14px, line-height 22px |
| Nhãn form, nút | Inter Medium/Semi Bold, 500/600 | 14px, line-height 20px |
| Chú thích, badge | Inter Regular/Medium, 400/500 | 12px, line-height 18px |
| Số liệu thống kê | Inter Bold, 700 | 28px, line-height 36px |

## 3. Khoảng cách, bo góc, bóng đổ, icon

| Hạng mục | Quy ước |
|---|---|
| Thang khoảng cách | 4 / 8 / 12 / 16 / 24 / 32 px (bội số của 4) |
| Bo góc | Ô nhập, nút, badge 6px; card, modal, toast 12px; avatar bo tròn (theo Figma) |
| Bóng đổ | Card: `0 1px 3px rgba(44,24,16,0.08)`; modal, dropdown: `0 8px 24px rgba(44,24,16,0.16)` |
| Icon | Một bộ duy nhất (đề xuất Lucide), nét 1.5px, cỡ 16/20/24px; icon đi kèm nhãn chữ, không dùng icon đơn lẻ để truyền đạt trạng thái |
| Vòng focus | 2px màu Info `#1565C0`, cách viền 2px (không dùng vàng vì tương phản dưới 3) |

## 4. Component

| Component | Đặc tả |
|---|---|
| Nút | Cao 44px; bo góc 6px. Primary nền vàng `#C9A84C`/chữ nâu `#2C1810`. Hover: nền `#B79538`. Disabled: nền Background, viền Border, chữ Text secondary, không bấm được. Error: nền Error background, viền và chữ `#C62828`. Secondary: nền trắng, viền Border strong, chữ nâu. Danger: nền `#C62828`, chữ trắng (tương phản 5.6), dùng cho xóa, khóa, khôi phục. Focus: vòng focus mục 3. Loading: spinner thay icon, giữ chiều rộng. Figma: component `Button`, property `Kiểu` (Primary, Secondary, Danger) × `State` (Default, Hover, Disabled, Error, Focus, Loading) và `Hiện biểu tượng` (bật/tắt icon). Mỗi màn chỉ một nút Primary. |
| Ô nhập / select | Cao 42px; chữ 14px; bo góc 6px; label phía trên; viền Border strong; nền Surface muted. Hover viền vàng 2px; Focus có vòng focus; lỗi có viền đỏ, icon và thông báo chữ dưới ô. Select là ô nhập kèm icon `chevron-down` bên phải. Figma: `Input`, `State` = Default, Hover, Focus, Disabled, Error. |
| Bảng | Header nền kem; hàng tối thiểu 44px; chữ 14px; đường phân cách Border. Mobile cho cuộn ngang hoặc chuyển sang danh sách card. |
| Card | Nền trắng; bo góc 12px; padding 20 đến 24px; viền Border hoặc bóng nhẹ. |
| Modal | Rộng tối đa 640px, không vượt chiều rộng màn hình trừ 32px; có tiêu đề, đóng, nội dung và vùng hành động. Nội dung dài cuộn bên trong. |
| Thanh điều hướng | Sidebar nền nâu đậm; mục đang chọn nền vàng/chữ nâu; hover nền Gold hover/chữ nâu; mục thường chữ Cream on dark. Mobile: khối điều hướng đặt cuối trang; chuyển thành menu drawer khi làm app. Menu theo vai trò ở mục 5.2. Figma: `Layout/Mục điều hướng` (`Trạng thái` = Mặc định, Đang chọn, Hover; `Nhãn`; `Biểu tượng`), `Layout/Sidebar`, `Layout/Điều hướng mobile`. |
| Header | Desktop cao 64px, nền trắng, viền dưới Border: tiêu đề trang (18/26), mô tả, ô tìm kiếm, nút thông báo, một nút hành động. Tablet và mobile: thanh thương hiệu nền nâu 64px (logo, tên FamilyConnect, tên dòng họ, nút Menu) rồi tiêu đề trang (24/32), mô tả, công cụ. Figma: `Layout/Header`, `Size` = 1440, 768, 360; property `Tiêu đề trang`, `Mô tả`, `Tìm kiếm`, `Nút hành động`. |
| Avatar | Hình tròn; cỡ 48px (hồ sơ, danh sách), 36px (menu), 32 và 40; có ảnh hoặc chữ viết tắt tên. Không dùng màu làm dấu hiệu phân biệt duy nhất. Figma: `Avatar/Hình tròn` (`Size` = 32, 36, 40, 48). |
| Badge | Chữ 12px; nền nhạt và chữ đậm theo trạng thái (cặp màu mục 1.3); bo góc 6px; có icon. Luôn có nhãn chữ. Figma `Badge`, `State`: Default = chờ (warning), Success = mới, đã duyệt, đang hoạt động; Error = khóa, từ chối; Info = đã cập nhật; Disabled = trung tính. |
| Toast | Có icon, thông điệp và nút đóng; Figma `Toast`, `State` = Default (thành công), Error, Info, Warning, Hover, Disabled. Desktop góc trên phải, mobile bên dưới header. |
| Icon | Bộ Lucide, nét 1.5px, cỡ 20px; Figma: component `Icon/<tên>` (32 icon trên page 01, khung "Icon (Lucide)"). Đổi màu bằng biến `text/*` hoặc `status/*`. |

## 5. Layout

Header, sidebar, vùng nội dung; có bản responsive.

- Sidebar: 240px.
- Header: 64px.
- Nội dung: padding ngang 28px, dọc 24px.
- Khoảng cách giữa các khu vực: 24px.
- Bố cục: thống kê phía trên, các khu vực nội dung chia hai cột.

### 5.1. Responsive

Theo NFR-01 trong `srs.md`, giao diện web dùng được ở 3 cỡ màn hình: điện thoại (từ 360 px), máy tính bảng, máy tính. Figma có frame mẫu cho cả ba (màn hình Tổng quan).

| Kích thước màn hình | Frame mẫu | Cách bố trí |
|---|---|---|
| Desktop ≥ 1024px | 1440 | Sidebar 240px; header 64px; nội dung hai cột; thống kê bốn cột |
| Tablet 768 đến 1023px | 768 | Không có sidebar cố định; padding 24px; thống kê bốn cột; nội dung một cột (riêng khối "Hoạt động" hai cột); điều hướng đặt cuối trang |
| Mobile < 768px | 360 | Header gọn; padding 16px; thống kê hai cột x hai hàng; nội dung một cột; điều hướng đặt cuối trang; form chọn hai người xếp dọc; nút có thể xuống dòng |

Menu dạng drawer (mở bằng nút Menu) là hướng cải tiến khi làm frontend, chưa vẽ trong Figma. Phạm vi Figma của SCRUM-25: màn hình Tổng quan ở 3 cỡ; các màn hình của từng module vẽ theo design system này (xem `figma-links.md`; bản mobile của các module do SCRUM-84).

### 5.2. Menu theo vai trò

Menu lấy theo các module trong `srs.md` mục 3 và quyền trong Phụ lục A. Sidebar (1440) và khối điều hướng (768, 360) dùng chung một danh sách; chọn variant `Vai trò` theo người dùng của màn hình.

| Nhóm | Mục (icon) | Màn hình / module | Chưa xác minh | Thành viên | Trưởng chi | Quản trị hệ thống |
|---|---|---|---|---|---|---|
| (đầu menu) | Gia đình (`house`) | Xin tham gia hoặc chờ duyệt (UC-AUTH-06) | Có | | | |
| (đầu menu) | Tổng quan (`layout-dashboard`) | Trang chủ của gia đình | | Có | Có | Có |
| (đầu menu) | Trợ lý AI (`sparkles`) | AI (FR-AI) | | Có | Có | Có |
| Gia phả | Thành viên (`users`), Cây gia phả (`network`), Tra cứu xưng hô (`search`) | Gia phả (FR-GEN) | | Có | Có | Có |
| Cộng đồng | Bảng tin (`message-square`), Sự kiện & Giỗ chạp (`calendar`), Danh bạ (`contact`) | Community, Events, Directory | | Có | Có | Có |
| Di sản | Lịch sử dòng họ (`book-open`), Thư viện ảnh (`image`), Kho lưu trữ số (`archive`) | Heritage (FR-HER) | | Có | Có | Có |
| Quản lý | Duyệt yêu cầu tham gia (`user-check`), Thống kê và báo cáo (`bar-chart`) | UC-AUTH-07, Dashboard (FR-DSH) | | | Có | Có |
| Quản trị hệ thống | Quản lý người dùng (`user-cog`), Kiểm duyệt nội dung (`shield-check`), Nhật ký kiểm toán (`history`), Sao lưu và khôi phục (`database`), Cấu hình hệ thống (`sliders`) | UC-AUTH-08 đến 12 | | | | Có |
| Tài khoản (cuối menu) | Hồ sơ cá nhân (`user`), Đổi mật khẩu (`lock`), Đăng xuất (`log-out`) | UC-AUTH-05, 04, 03 | Có | Có | Có | Có |

- Tên ứng dụng hiển thị: **FamilyConnect** (tên dự án trong `srs.md`); dòng phụ là tên dòng họ đang xem.
- "Tổng quan" là trang chủ cho mọi thành viên đã xác minh; thống kê chi tiết và báo cáo nằm ở "Thống kê và báo cáo" (chỉ Trưởng chi, Quản trị) đúng Phụ lục A. Nội dung trang Tổng quan cho Thành viên do SCRUM-32 chốt.
- Mục "Bản đồ quê hương" trong bản vẽ cũ không có trong `srs.md` nên đã thay bằng "Kho lưu trữ số" (FR-HER-05).
- Theo `mobile.md` mục 2, app mobile (React Native) không có nhóm Quản lý và Quản trị; bản web ở cỡ 360 vẫn hiện đủ theo vai trò.

## 6. Cấu hình `tailwind.config`

Mã màu và cỡ chữ ghi vào `tailwind.config` của frontend. Tên token khớp các bảng ở trên.

```js
export default {
  theme: {
    extend: {
      colors: {
        primary: "#2C1810",
        secondary: "#C9A84C", // chỉ dùng làm nền, không dùng làm màu chữ
        success: "#2E7D32",
        error: "#C62828",
        info: "#1565C0",
        warning: "#BF4400",

        background: "#F5F0E8",
        surface: "#FFFFFF",
        "surface-muted": "#F9F6F0",
        "text-primary": "#2C1810",
        "text-secondary": "#7A6348",
        border: "#D4C4A8",
        "gold-hover": "#B79538",
        "cream-on-dark": "#F5E6C8",
        "border-strong": "#9C8560",
        "success-soft": "#E8F5E9",
        "error-soft": "#FCE4EC",
        "warning-soft": "#FFF3E0",
        "info-soft": "#E3F2FD",
        "accent-green": "#5C8A6E", // chỉ cho cây gia phả, kèm nhãn chữ
        "accent-pink": "#B87A9E",
        "accent-pink-soft": "#E8A0C0",
      },

      fontFamily: {
        sans: ["Inter", "sans-serif"],
      },

      fontSize: {
        "page-title": ["24px", { lineHeight: "32px", fontWeight: "700" }],
        "section-title": ["18px", { lineHeight: "26px", fontWeight: "600" }],
        body: ["14px", { lineHeight: "22px" }],
        label: ["14px", { lineHeight: "20px", fontWeight: "500" }],
        caption: ["12px", { lineHeight: "18px" }],
        metric: ["28px", { lineHeight: "36px", fontWeight: "700" }],
      },

      borderRadius: {
        control: "6px",
        card: "12px",
      },

      boxShadow: {
        card: "0 1px 3px rgba(44,24,16,0.08)",
        overlay: "0 8px 24px rgba(44,24,16,0.16)",
      },

      spacing: {
        sidebar: "240px",
        header: "64px",
      },
    },
  },
};
```

## 7. Token trong Figma

File Figma chung (xem `figma-links.md`) có sẵn các biến và style theo đúng tài liệu này. Khi vẽ, chọn biến hoặc style, không nhập mã màu tay. Tên trong Figma dùng dấu `/` để gom nhóm nên khác tên token ở mục 6:

| Biến Figma (collection `Color`) | Token `tailwind.config` | Mã màu |
|---|---|---|
| `brand/primary` | `primary` | `#2C1810` |
| `brand/secondary` | `secondary` | `#C9A84C` (không chọn được cho màu chữ) |
| `status/success`, `status/error`, `status/info`, `status/warning` | `success`, `error`, `info`, `warning` | Mục 1.1 |
| `bg/background`, `bg/surface`, `bg/surface-muted` | `background`, `surface`, `surface-muted` | Mục 1.2 |
| `bg/success-soft`, `bg/error-soft`, `bg/warning-soft`, `bg/info-soft` | `success-soft`, `error-soft`, `warning-soft`, `info-soft` | Mục 1.2 |
| `text/primary`, `text/secondary`, `text/cream-on-dark` | `text-primary`, `text-secondary`, `cream-on-dark` | Mục 1.2 |
| `border/default`, `border/strong` | `border`, `border-strong` | Mục 1.2 |
| `state/gold-hover` | `gold-hover` | `#B79538` |
| `accent/xanh-lá`, `accent/hồng`, `accent/hồng-nhạt` | `accent-green`, `accent-pink`, `accent-pink-soft` | `#5C8A6E`, `#B87A9E`, `#E8A0C0`: chỉ dùng cho chú giải và avatar trong cây gia phả, luôn kèm nhãn chữ |

- Collection `Size`: `space/4` đến `space/32`, `radius/control` (6), `radius/card` (12), `height/button` (44), `height/input` (42), `height/table-row` (44), `layout/sidebar` (240), `layout/header` (64), `focus/ring-width` (2).
- Text style: `page-title`, `section-title`, `body`, `label`, `caption`, `metric` (mục 2).
- Effect style: `shadow/card`, `shadow/overlay` (mục 3).

### 7.1. Điểm Figma còn lệch tài liệu

Đã xử lý ở v0.6: nút desktop dưới 44px; ô tìm kiếm và ô chọn về 42px; màu ngoài bảng màu (chữ `#A08060`, `#5C4033`, `#6B5C4A`, `#D4B896` và các nền, viền nhạt) gắn về biến mục 1; màu cây gia phả thành biến `accent/*`; bo góc 4, 5, 8, 10 về 6 hoặc 12; chữ dưới 12px về `caption`; thêm kiểu nút Secondary. Sidebar và header cũ thay bằng component mục 5.2.

Còn lại, chờ TV5 quyết định:

- Cỡ chữ 13, 15, 16, 20px và chữ để line-height tự động trong các màn Tổng quan (chưa gắn text style vì đổi cỡ sẽ làm lệch bố cục).
- Chữ trắng trên `accent/xanh-lá` (3.9) và `accent/hồng` (3.3) chưa đạt 4.5; nên dùng chữ to và đậm (từ 18px đậm) hoặc đổi sang màu đậm hơn.
- Component `Table` chỉ có 3 cột cố định, chưa thêm được cột "Thao tác"; `Avatar` (dạng hàng danh sách) và `Card`, `Modal` vẫn dùng 4 variant chung Default, Hover, Disabled, Error.
- 5 khung mobile theo module (SCRUM-84) đã chuyển sang page `06 Mobile`, còn header và điều hướng vẽ tay, chưa đặt tên theo mã UC.

## 8. Hướng dẫn dùng (cho người vẽ màn hình)

Bản đầy đủ có trên page `01 Design system`, khung "Hướng dẫn dùng (đọc trước khi vẽ)". Màn mẫu làm đúng cách: page `02 Auth và Admin`.

1. **Tạo khung** trong section "Màn hình (360 / 768 / 1440)" của page module mình, đủ 3 cỡ, tên `<Mã UC> Tên màn hình / <cỡ>`.
2. **Bản 1440:** khung auto layout ngang gồm `Layout/Sidebar` (chiều cao Fill) và cột nội dung dọc: `Layout/Header` Size=1440, vùng nội dung padding 28 ngang, 24 dọc, khoảng cách 24.
3. **Bản 768 và 360:** khung auto layout dọc gồm `Layout/Header` (Size=768 hoặc 360), vùng nội dung (padding 24 hoặc 16), `Layout/Điều hướng mobile` ở cuối (chiều rộng Fill).
4. **Vai trò và mục đang chọn:** chọn `Vai trò` theo người dùng của màn (mục 5.2). Mở layer `Mục: <tên trang>` → `Trạng thái` = Đang chọn; đưa `Mục: Tổng quan` về Mặc định. Không sửa chữ hay ẩn mục bằng tay.
5. **Header:** điền `Tiêu đề trang`, `Mô tả`; tắt `Tìm kiếm`, `Nút hành động` nếu không cần; đổi chữ nút qua "Nút hành động".
6. **Nút:** mỗi màn 1 Primary; hành động phụ Secondary; xóa, khóa, khôi phục Danger; tắt `Hiện biểu tượng` khi không cần icon.
7. **Màu và chữ:** chỉ dùng biến collection `Color` và 6 text style; không gõ mã màu, không detach, không ghi đè màu.
8. **Nội dung:** chữ trên màn hình bằng tiếng Việt cho người dùng; API, UUID, mã lỗi, tên trường ghi ở dòng "Ghi chú FR / API" dưới khung.
9. **Xong việc:** dán link khung vào `figma-links.md` (không xuất ảnh), nhờ TV5 kiểm tra đồng bộ.
