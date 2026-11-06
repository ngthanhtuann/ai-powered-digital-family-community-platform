# Hướng dẫn tích hợp Task SCRUM-44 (Quan hệ cha-mẹ-con và hôn nhân BE+FE)

## 1. Cấu trúc mã nguồn đã bàn giao trong ZIP này:
- `backend/src/main/resources/db/migration/V4__genealogy_relations.sql`: Flyway Migration tạo bảng `parent_child` và `marriage` với các ràng buộc CHECK và UNIQUE từng phần.
- `backend/src/main/java/com/familyconnect/modules/genealogy/entity/ParentChild.java`: Entity JPA quan hệ cha-mẹ-con.
- `backend/src/main/java/com/familyconnect/modules/genealogy/entity/Marriage.java`: Entity JPA quan hệ hôn nhân.
- `backend/src/main/java/com/familyconnect/modules/genealogy/repository/ParentChildRepository.java`: Repository kèm câu lệnh đệ quy chặn vòng lặp tổ tiên (`isAncestorOf`).
- `backend/src/main/java/com/familyconnect/modules/genealogy/repository/MarriageRepository.java`: Repository kiểm tra trạng thái hôn nhân.
- `backend/src/main/java/com/familyconnect/modules/genealogy/service/RelationshipService.java`: Logic nghiệp vụ kiểm tra quy tắc `GEN_001` đến `GEN_014` (tối đa 2 cha mẹ ruột, cha mẹ sinh trước con, chặn chu trình tổ tiên, kiểm tra trạng thái hôn nhân).
- `backend/src/main/java/com/familyconnect/modules/genealogy/controller/RelationshipController.java`: Các API endpoints theo chuẩn OpenAPI `genealogy.yaml`.
- `frontend/src/components/genealogy/RelationshipManager.tsx`: Component React cho phép thêm cha/mẹ và hôn nhân kèm thông báo lỗi nghiệp vụ trực quan.

## 2. Hướng dẫn tích hợp:
1. Giải nén file zip vào thư mục gốc của dự án `family-connect`.
2. Chạy Flyway migration để cập nhật database PostgreSQL.
3. Build và khởi chạy Spring Boot Backend & Next.js Frontend.
