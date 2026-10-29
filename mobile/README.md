# Mobile (React Native + Expo)

Ứng dụng mobile FamilyConnect cho Android và iOS (SCRUM-85). Thiết kế: `docs/02-design/mobile.md`, quyết định công nghệ: `docs/decisions/adr-mobile.md`.

## Công nghệ

- Expo SDK 58, React Native, TypeScript.
- React Navigation: stack gốc chọn nhánh theo đăng nhập và `membership`, menu drawer trong ứng dụng (không dùng tab bar, không dùng Expo Router).
- TanStack Query, axios, react-hook-form + zod (cùng thư viện với web).
- `expo-secure-store` lưu access token và refresh token.
- Kiểu API sinh từ `docs/04-api/openapi/` bằng `openapi-typescript`.

TypeScript giữ bản 5.9 thay vì 6.0 mà Expo SDK 58 đề xuất, vì `openapi-typescript` 7 chỉ hỗ trợ TypeScript 5. Vì vậy `package.json` có `expo.install.exclude: ["typescript"]`.

## Cài và chạy

```bash
cd mobile
npm install
cp .env.example .env        # sửa EXPO_PUBLIC_API_BASE_URL
npm start                   # mở Expo, quét QR bằng Expo Go
```

`EXPO_PUBLIC_API_BASE_URL` là URL gốc của backend, không kèm `/api/v1`:

| Chạy trên                             | Giá trị                                     |
| ------------------------------------- | ------------------------------------------- |
| Điện thoại thật (Expo Go, cùng Wi-Fi) | `http://<IP LAN của máy chạy backend>:8080` |
| Giả lập Android                       | `http://10.0.2.2:8080`                      |
| Giả lập iOS                           | `http://localhost:8080`                     |

Backend chạy bằng Docker ở thư mục gốc: `docker compose up -d`. App native không bị CORS chặn; chỉ cần điện thoại truy cập được cổng 8080 của máy (mở tường lửa nếu cần).

## Lệnh

| Lệnh                | Việc                                                                            |
| ------------------- | ------------------------------------------------------------------------------- |
| `npm start`         | Chạy Expo dev server                                                            |
| `npm run typecheck` | Kiểm tra kiểu TypeScript                                                        |
| `npm run lint`      | ESLint (cấu hình `eslint-config-expo`)                                          |
| `npm run format`    | Prettier                                                                        |
| `npm run gen:api`   | Sinh lại kiểu API vào `src/services/generated/` (commit kết quả, không sửa tay) |

## Cấu trúc

```text
src/
├── navigation/   RootNavigator (chọn nhánh), AppDrawer (menu drawer), types.ts (tên màn hình)
├── screens/      Login, Register, Profile, ChangePassword, Home, MembershipScreens (Nhập mã gia đình, Chờ duyệt)
├── components/   ui.tsx (Button, TextField, Notice, Card, Screen), form-errors.ts
├── session/      SessionContext: người dùng và membership của phiên
├── services/     api.ts (axios, làm mới token, chuẩn hóa lỗi), token-storage.ts, auth.ts, generated/
└── theme.ts      token màu, chữ, bo góc lấy từ frontend/tailwind.config.ts
```

## Thêm màn hình mới

1. Thêm tên màn vào `AppDrawerParamList` (hoặc `RootStackParamList`) trong `src/navigation/types.ts`.
2. Tạo màn trong `src/screens/`, gọi API qua hàm trong `src/services/<module>.ts` và TanStack Query.
3. Đăng ký `Drawer.Screen` trong `src/navigation/AppDrawer.tsx`.

## Xử lý lỗi và token

Theo `mobile.md` mục 4.3 và 4.5:

- `AUTH_002`: gọi `POST /auth/refresh` một lần (các request đang chờ dùng chung kết quả), rồi gọi lại request. Làm mới thất bại thì xóa token, về Đăng nhập.
- `AUTH_003`: xóa token, về Đăng nhập kèm thông báo tài khoản bị khóa.
- `PERM_002`: tải lại `membership` để chuyển sang Chờ duyệt hoặc Nhập mã gia đình.
- `PERM_001`, `VALID_001`, `RATE_001`...: màn hình tự hiện thông báo, lỗi dưới từng ô theo `details[].field`.
- Đổi mật khẩu xong: server thu hồi refresh token, app xóa token và yêu cầu đăng nhập lại.

## Còn thiếu (task khác)

- Gửi, hủy yêu cầu tham gia gia đình và tạo gia đình trên mobile: màn Nhập mã gia đình hiện mới chỉ cho kiểm tra lại trạng thái.
- Các module Bảng tin, Sự kiện, Gia phả, AI, Danh bạ, Di sản.
- Figma mobile chưa có khung Đăng ký, Hồ sơ, Đổi mật khẩu (`mobile.md` mục 5): các màn này dựng theo bản web và design system, cần đối chiếu lại khi có Figma.
- Chọn ngày sinh đang nhập tay dạng `YYYY-MM-DD` (chưa thêm thư viện date picker).
