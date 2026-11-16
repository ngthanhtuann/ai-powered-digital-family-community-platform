# Mobile (React Native + Expo)

Ứng dụng mobile FamilyConnect cho Android và iOS (SCRUM-85). Thiết kế: `docs/02-design/mobile.md`, quyết định công nghệ: `docs/decisions/adr-mobile.md`.

## Công nghệ

- Expo SDK 58, React Native, TypeScript.
- React Navigation: stack gốc chọn nhánh theo đăng nhập và `membership`, menu drawer trong ứng dụng (không dùng tab bar, không dùng Expo Router).
- TanStack Query, axios, react-hook-form + zod (cùng thư viện với web).
- `expo-secure-store` lưu access token và refresh token.
- `expo-image-picker` chọn ảnh từ thư viện hoặc chụp ảnh để đăng bài (cấp quyền camera khi sử dụng).
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

## Đóng gói và cài APK Android ([7-08])

Địa chỉ backend được **gắn vào APK lúc build** (`EXPO_PUBLIC_API_BASE_URL`), nên đổi máy chủ hoặc IP thì phải build lại. Địa chỉ nằm ở profile `preview` của `eas.json`; sửa ở đó trước khi build.

**Cách 1 (khuyên dùng): EAS Build trên cloud của Expo.** Không cần Android SDK, không tốn RAM máy. Project gắn với tài khoản Expo `thajnktuaans` (`app.json`: `owner`, `extra.eas.projectId`); người khác build cần được mời vào project trên expo.dev.

```powershell
cd mobile
npx eas-cli login --device --non-interactive   # mở link, xác nhận trên trình duyệt
npx eas-cli build --platform android --profile preview
# Xong (khoảng 15-20 phút): EAS in link tải APK; trang build trên expo.dev cũng có nút tải
```

- Package `vn.familyconnect.app`. Keystore do EAS tạo và giữ trên máy chủ Expo (ký cùng một khóa cho mọi bản build sau).
- **Không build được tại máy Windows** bằng `gradlew assembleRelease`: biên dịch C++ của React Native (`react-native-worklets`, `react-native-gesture-handler`) tạo đường dẫn dài hơn 260 ký tự, kể cả khi đặt dự án ở `C:\` (đã thử ngày 12/11/2026). Build tại máy chỉ chạy được trên macOS hoặc Linux.
- `plugins/with-cleartext-traffic.js` chỉ cho phép HTTP khi URL backend là `http://` (demo trong mạng LAN). Triển khai thật nên dùng `https://`, khi đó plugin không bật HTTP.

**Cài lên điện thoại Android:**

1. Chép tệp APK vào điện thoại (cáp USB, Zalo, Google Drive...), mở tệp và cho phép "Cài ứng dụng không rõ nguồn gốc" khi được hỏi.
2. Điện thoại và máy chạy backend **cùng mạng Wi-Fi**; trên máy chạy backend mở cổng 8080 ở tường lửa Windows.
3. Mở app, đăng nhập bằng tài khoản có sẵn (dữ liệu demo: `docker/seed-demo.ps1`).
4. Giả lập Android Studio: kéo thả APK vào cửa sổ giả lập, hoặc `adb install <tệp>.apk`. Giả lập dùng được địa chỉ IP LAN của máy như điện thoại thật.

**iOS:** chưa có tài khoản Apple Developer nên không có bản cài; chạy bằng Expo Go theo mục "Cài và chạy" ở trên.

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
├── screens/      Auth + Bảng tin, Chi tiết/Tạo bài, Sự kiện/Chi tiết, Thông báo
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

## Bảng tin, Sự kiện, Thông báo ([6-10])

- Drawer có **Bảng tin** và **Sự kiện**, chuông trên header mở **Thông báo** và số chưa đọc; không dùng push notification.
- Bảng tin phân trang, kéo làm mới, bài chi tiết/bình luận/reaction; đăng bài POST kèm nhiều ảnh/camera, có tiến trình upload và thử tải tiếp khi lỗi.
- Sự kiện có bộ lọc sắp tới/đã qua, xem chi tiết, RSVP, danh sách người tham dự, gallery.
- Ảnh của bài/sự kiện được đọc qua `/api/v1/media/{id}/content` kèm JWT. Backend hiện dùng `/posts/{id}/media` để upload ảnh bài đăng (khác `/media` cho HERITAGE/PERSON).
- Ảnh tải lên chỉ nhận JPG/PNG/WebP tối đa 5 MB. HEIC từ iPhone phải chuyển sang JPG trước khi chọn.
- Test thủ công Android/iOS theo `docs/06-test/mobile-community-events-6-10.md`; chỉ xác nhận Đạt khi đã chạy trên thiết bị.

## Còn thiếu (task khác)

- Gửi, hủy yêu cầu tham gia gia đình và tạo gia đình trên mobile: màn Nhập mã gia đình hiện mới chỉ cho kiểm tra lại trạng thái.
- Các module Gia phả, AI, Danh bạ, Di sản.
- Figma mobile chưa có khung Đăng ký, Hồ sơ, Đổi mật khẩu (`mobile.md` mục 5): các màn này dựng theo bản web và design system, cần đối chiếu lại khi có Figma.
- Chọn ngày sinh đang nhập tay dạng `YYYY-MM-DD` (chưa thêm thư viện date picker).
