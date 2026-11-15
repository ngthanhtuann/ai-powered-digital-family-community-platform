# FamilyConnect — Frontend

Next.js (App Router) + TypeScript + Tailwind CSS.
Thư viện: axios, @tanstack/react-query, react-hook-form, zod, @xyflow/react, recharts.

## Chạy local

Yêu cầu: Node.js 20+ và backend đang chạy (mặc định http://localhost:8080).

```bash
cd frontend
cp .env.example .env.local      # chỉnh NEXT_PUBLIC_API_BASE_URL nếu cần
npm install
npm run dev                     # http://localhost:3000
```

Trang chủ hiển thị trạng thái `/actuator/health` của backend. Layout chung được định nghĩa ở
`src/components/layout/AppShell.tsx`; UI kit và ví dụ component dùng chung ở `/ui-kit`.
Nếu lỗi CORS, thêm `http://localhost:3000` vào danh sách origin của backend (biến môi trường).

## Lệnh

| Lệnh              | Mục đích                                                             |
| ----------------- | -------------------------------------------------------------------- |
| `npm run dev`     | Chạy dev server                                                      |
| `npm run build`   | Build production                                                     |
| `npm run lint`    | ESLint                                                               |
| `npm run format`  | Prettier                                                             |
| `npm run gen:api` | Sinh kiểu từ `../docs/04-api/openapi/` vào `src/services/generated/` |

## Docker

```bash
docker build -t familyconnect-frontend --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 .
docker run --rm -p 3000:3000 familyconnect-frontend
```

`NEXT_PUBLIC_*` được nhúng lúc build, nên truyền qua `--build-arg`.
Backend đọc danh sách origin CORS từ `CORS_ALLOWED_ORIGINS` trong file `.env` ở thư mục gốc repo.

## Cấu trúc

- `src/app/` — route, layout, providers.
- `src/components/` — UI component dùng lại; `components/ui/` chứa Button, Input, Table, Modal, Card và Toast.
- `src/lib/api.ts` — axios client theo `docs/04-api/conventions.md`:
  - đọc `success/data/meta`; lỗi `error.code/message/details` (`details` luôn là mảng);
  - DELETE trả 200 `{success:true, data:null}`;
  - `AUTH_002` → gọi `/auth/refresh` rồi gọi lại; `PERM_002` → `/pending-approval`; `PERM_001` → thông báo không đủ quyền.
- `src/lib/nav.ts` — khung menu ẩn theo vai trò.
- `src/services/` — kiểu sinh từ OpenAPI. Chỉ commit output của `npm run gen:api`; không sửa tay.
