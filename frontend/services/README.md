# services/

Kiểu dữ liệu và hàm gọi API theo từng module.

- Kiểu được **sinh tự động** từ `docs/04-api/openapi/` vào `services/generated/` bằng `npm run gen:api`
  (openapi-typescript). Không viết tay, không commit chỉnh sửa vào thư mục `generated/`.
- Hàm gọi API của module (ví dụ `posts.ts`) dùng `api` từ `@/lib/api` và import kiểu từ `generated/`.
