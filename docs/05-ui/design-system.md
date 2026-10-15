# Design system

> **Người viết:** TV5 (Lê Nhựt) | **Reviewer:** TV3 (PiLo257) | **Task Jira:** SCRUM-25
> **Trạng thái:** Chờ review | **Phiên bản:** 0.4 (sửa theo review của TV1: WCAG AA, định dạng, khớp với Figma, đủ 3 cỡ màn hình theo NFR-01)
> Liên quan: `figma-links.md` (link Figma và ảnh wireframe), SCRUM-84 (bản mobile).

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
| Nút | Cao 44px; bo góc 6px. Primary nền vàng `#C9A84C`/chữ nâu `#2C1810`. Hover: nền `#B79538`. Disabled: nền Background, viền Border, chữ Text secondary, không bấm được. Error: nền Error background, viền và chữ `#C62828`. **Đã có trong Figma:** primary với 4 trạng thái trên. **Bổ sung ở Sprint 2:** secondary (nền trắng, viền Border strong), danger (nền đỏ, chữ trắng), focus (vòng focus mục 3), loading (spinner thay icon, giữ chiều rộng). |
| Ô nhập / select | Cao 42px; chữ 14px; bo góc 6px; label phía trên; viền Border strong; nền Surface muted. Hover viền vàng 2px; focus có vòng focus (bổ sung ở Sprint 2, Figma hiện mới có Hover); lỗi có viền đỏ, icon và thông báo chữ dưới ô. |
| Bảng | Header nền kem; hàng tối thiểu 44px; chữ 14px; đường phân cách Border. Mobile cho cuộn ngang hoặc chuyển sang danh sách card. |
| Card | Nền trắng; bo góc 12px; padding 20 đến 24px; viền Border hoặc bóng nhẹ. |
| Modal | Rộng tối đa 640px, không vượt chiều rộng màn hình trừ 32px; có tiêu đề, đóng, nội dung và vùng hành động. Nội dung dài cuộn bên trong. |
| Thanh điều hướng | Sidebar nền nâu đậm; mục đang chọn nền vàng/chữ nâu. Mobile: khối điều hướng đặt cuối trang (theo Figma); chuyển thành menu drawer khi làm app. |
| Avatar | Hình tròn; cỡ 48px (hồ sơ, danh sách) và 36px (menu); cỡ 32 và 40 bổ sung ở Sprint 2; có ảnh hoặc chữ viết tắt tên. Không dùng màu làm dấu hiệu phân biệt duy nhất. |
| Badge | Chữ 12px; bo tròn; nền nhạt và chữ đậm theo trạng thái (cặp màu mục 1.3); bo góc 6px. Luôn có nhãn chữ. |
| Toast | Có icon, thông điệp và nút đóng; success, error (đã có trong Figma), info, warning (bổ sung ở Sprint 2). Desktop góc trên phải, mobile bên dưới header. |

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
