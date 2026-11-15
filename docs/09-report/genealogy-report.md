# Báo cáo phần Gia phả (SCRUM-66, kế hoạch 7-02)

**Người viết:** TV1 Tuấn (nhận lại từ TV2 ngày 15/11/2026) | **Reviewer:** TV5 | **Đối chiếu:** `develop` tại `e9793a7`

Báo cáo này dùng cho chương 3 (thiết kế CSDL), chương 4 (cài đặt) và chương 5 (kiểm thử) của báo cáo cuối kỳ (`report-outline.md`). Số liệu lấy từ các báo cáo kiểm thử đã có; mục nào chưa đo ghi "Chưa xác minh".

## 1. Phạm vi module

| Mục | Nội dung | Nguồn |
|---|---|---|
| Use case | UC-GEN-01 đến UC-GEN-08: quản lý gia đình, chi, thành viên, quan hệ cha mẹ con, hôn nhân, cây gia phả, tra quan hệ | `docs/01-srs/use-cases/genealogy.md` |
| API | `docs/04-api/openapi/genealogy.yaml` (tiền tố `/api/v1`) | |
| Interface cho module khác | `GenealogyQuery` (quan hệ, chi, trẻ em, danh bạ), `GenealogyPort` | `docs/02-design/architecture.md` |
| Web | Thành viên, cây gia phả (`@xyflow/react`); tra quan hệ trong khung chi tiết người trên cây (`PersonPanel`). Mục menu "Tra cứu xưng hô" riêng chưa có trang (đang hiện "sắp ra mắt") | `frontend/src/app/members`, `persons`, `tree`; `frontend/src/components/genealogy/PersonPanel.tsx` |
| Mobile | Cây gia phả dạng danh sách, chi tiết thành viên, tra cứu xưng hô (SCRUM-90) | `mobile/src/screens` |

## 2. Thiết kế cơ sở dữ liệu

Năm bảng, đều có `created_at`, `updated_at`, `deleted_at` (xóa mềm) và khóa ngoại chỉ trong module (`data-dictionary.md` mục Gia phả).

| Bảng | Vai trò | Ràng buộc chính |
|---|---|---|
| `family` | Gia đình, dòng họ | |
| `branch` | Chi họ, cây chi qua `parent_branch_id` | FK `family_id`, `parent_branch_id`, `origin_person_id` |
| `person` | Thành viên | `gender` MALE/FEMALE/OTHER; người còn sống phải có `birth_date` (BR-GEN-17), người đã mất có thể chỉ có `birth_year`; `death_date >= birth_date`; `user_id` duy nhất khi còn hiệu lực |
| `parent_child` | Cạnh cha/mẹ - con | `parent_role` FATHER/MOTHER, `kind` BIOLOGICAL/ADOPTED/STEP; không tự là cha mẹ của mình; mỗi con có tối đa một cha ruột và một mẹ ruột |
| `marriage` | Hôn nhân | `status` MARRIED/DIVORCED/WIDOWED; tự chuyển WIDOWED khi một bên mất (BR-GEN-08) |

**Lý do mô hình đồ thị bằng bảng cạnh:** gia phả là đồ thị có hướng (cha mẹ → con) có thể có nhiều cha mẹ (nuôi, kế), nên lưu cạnh trong `parent_child` thay vì cột `father_id`, `mother_id`. Truy vấn tổ tiên, hậu duệ dùng CTE đệ quy của PostgreSQL trên index `parent_child(child_id)`, `parent_child(parent_id)`.

**Tìm kiếm họ tên:** so không phân biệt hoa thường và không dấu bằng extension `unaccent` (V12), giống Danh bạ và trang quản trị (SCRUM-102, mục 5).

## 3. Thuật toán xác định quan hệ ("A là gì của B")

Đặc tả đầy đủ: `docs/02-design/algorithm-relationship.md`.

1. Tìm tổ tiên của A và của B bằng duyệt lên `parent_child`, tối đa 6 đời, đi qua cha trước mẹ, giữ đường ngắn nhất.
2. Chọn tổ tiên chung gần nhất (LCA): tổng số bước nhỏ nhất, hòa thì ưu tiên đường qua cha, rồi id nhỏ hơn, để kết quả luôn cố định.
3. Từ cặp số bước (a, b), giới tính, tuổi và bên nội/ngoại, tra bảng tên gọi chuẩn miền Nam (cha, mẹ, anh, chị, em, ông bà nội/ngoại, cố, chắt, bác, chú, cô, cậu, dì, cháu, anh chị em họ). Con nuôi, con kế thêm nhãn "(nuôi)", "(kế)".
4. Không có huyết thống thì xét lớp hôn nhân (vợ, chồng, vợ cũ, chồng cũ, thím, mợ, dượng, vợ bác). Lớp này chỉ gọi hàm huyết thống nên không đệ quy vô hạn.
5. Cặp chưa có trong bảng trả `supported = false` kèm (a, b), không phải lỗi.

**Độ phức tạp:** mỗi người duyệt tối đa 2^6 = 64 tổ tiên ruột, nên O(V + E) với V, E nhỏ; giao hai tập tổ tiên bằng bảng băm.

**Riêng tư:** kết quả chỉ trả `id`, `fullName` trên đường đi (BR-GEN-10) vì được dùng làm ngữ cảnh cho AI giải thích quan hệ (FR-AI-03).

## 4. Kết quả kiểm thử và đo

| Hạng mục | Kết quả | Nguồn |
|---|---|---|
| Kiểm thử hồi quy module Gia phả | 183 test đạt, gồm 153 ca thuật toán quan hệ (`RelationshipCaseCatalogTest`) | `docs/06-test/regression-7-01.md` |
| Bug sau hồi quy | 1 Minor (SCRUM-102), đã sửa ở SCRUM-66 | mục 5 |
| API cây 3 đời, gia đình 6.250 người | p95 120 ms (10 người dùng), 475 ms (50 người dùng): đạt NFR-12 (< 1 giây) | `docs/06-test/performance-6-04.md` mục 5 |
| Cả cây 6.250 người một lần | p95 831 ms (10 người dùng, đạt); 4.283 ms (50 người dùng, **không đạt**) | như trên |
| API tra quan hệ | p95 24 ms (10 người dùng), 139 ms (50 người dùng) | như trên |
| Tối ưu đã làm | Tính đời bằng JOIN thay subquery tương quan, chỉ tính cho người trên cây: 950 ms còn 122 ms | như trên, mục 6.2 |

## 5. Bug đã sửa trong SCRUM-66

| Mã | Mô tả | Cách sửa | Kiểm tra |
|---|---|---|---|
| SCRUM-102 (Minor) | `GET /families/{id}/persons?q=` chỉ khớp khi gõ đúng dấu (`q=to` không ra "Tổ") | `PersonRepository.search` so `lower(unaccent(full_name))` với mẫu LIKE đã escape `!`, `%`, `_`; `genealogy.yaml` mô tả lại tham số `q`; mobile bỏ ghi chú "Gõ có dấu" ở màn Chọn thành viên | `GenealogyApiIntegrationTest`: "EM BE" khớp "Em bé", "chinh toi" khớp "Chính tôi", "%" không còn khớp mọi người |

## 6. Hạn chế

- Chưa hỗ trợ họ xa từ (3, 3), (1, 3) trở đi (anh em họ đời thứ hai, ông bác); trả `supported = false`.
- Quan hệ qua hôn nhân chỉ gồm các trường hợp ở mục 3 bước 4; chưa có chị dâu, em rể, con dâu, con rể.
- Chưa gọi theo thứ (Hai, Ba) và chưa có biến thể vùng miền ngoài miền Nam.
- Cặp có nhiều đường nối chỉ trả một đường (ngắn nhất, ưu tiên qua cha).
- Tải cả cây 6.250 người cùng lúc ở 50 người dùng chưa đạt 1 giây; giao diện tải 3 đời rồi mở dần nên không gặp trong sử dụng thường.

## 7. Gợi ý nội dung thuyết trình (5 phút)

1. Bài toán: gia phả là đồ thị, câu hỏi "A là gì của B" theo cách gọi Việt Nam.
2. Mô hình dữ liệu: 5 bảng, cạnh `parent_child` có loại ruột, nuôi, kế.
3. Thuật toán: tổ tiên chung gần nhất, cặp (a, b), bảng tên gọi; ví dụ "Dũng là chú của An".
4. Demo: cây gia phả trên web, tra cứu xưng hô trên mobile.
5. Kết quả: 153 ca thuật toán đạt; cây 3 đời p95 dưới 0,5 giây ở 50 người dùng.
6. Hạn chế và hướng phát triển (mục 6).
