# Mô hình hóa quy trình nghiệp vụ

> **Người viết:** TV3 (PiLo257) | **Reviewer:** TV1 (Tuấn Nguyễn Thanh) | **Task Jira:** SCRUM-83 | **Hạn nộp review:** Thứ Năm 15/10 (Sprint 2)  
> **Trạng thái:** Chờ review

Các sơ đồ sử dụng thống nhất bốn làn: **Thành viên**, **Trưởng chi (BRANCH_ADMIN)**, **Hệ thống** và **AI Service**. Theo `srs.md` mục 2, **AI Service** là hệ thống LLM bên ngoài mà lớp AI gọi tới; các bước kiểm tra, tìm ngữ cảnh và lưu dữ liệu nằm ở làn **Hệ thống**.

Cách đọc: ô vàng hình thoi là điểm rẽ nhánh; ô đỏ là lỗi trả về và kết thúc nhánh đó; ô cam là cảnh báo, người dùng xác nhận thì đi tiếp; ô viền nét đứt là trạng thái chờ. File gốc để sửa: `docs/images/business-process-<số>.drawio` (mở bằng draw.io), sửa xong xuất lại file `.drawio.png` cùng tên.

## 1. Đăng ký, xác minh thành viên và tham gia gia đình

![Quy trình 1 - Đăng ký, xác minh thành viên và tham gia gia đình](../images/business-process-1.drawio.png)

## 2. Thêm thành viên và quan hệ trong gia phả

![Quy trình 2 - Thêm thành viên và quan hệ trong gia phả](../images/business-process-2.drawio.png)

## 3. Tạo sự kiện, RSVP và nhắc nhở

![Quy trình 3 - Tạo sự kiện, RSVP và nhắc nhở](../images/business-process-3.drawio.png)

## 4. Hỏi trợ lý AI (RAG)

![Quy trình 4 - Hỏi trợ lý AI (RAG)](../images/business-process-4.drawio.png)

## 5. Đối chiếu UC / FR / BR

| Sơ đồ | Use Case | Functional Requirements | Business Rules / tài liệu nguồn |
|---|---|---|---|
| 1. Đăng ký, xác minh và tham gia gia đình | UC-AUTH-01, UC-AUTH-02, UC-AUTH-06, UC-AUTH-07 | FR-AUTH-01, FR-AUTH-04 | BR-AUTH-07, BR-AUTH-08, BR-AUTH-09, BR-AUTH-10, BR-AUTH-11; `auth.md` |
| 2. Thêm thành viên và quan hệ gia phả | UC-GEN-03, UC-GEN-04, UC-GEN-06 | FR-GEN-03, FR-GEN-04, FR-GEN-06 | BR-GEN-01, BR-GEN-02, BR-GEN-03, BR-GEN-04, BR-GEN-05, BR-GEN-11, BR-GEN-14; `genealogy.md` |
| 3. Tạo sự kiện, RSVP và nhắc nhở | UC-EVT-01, UC-EVT-02, UC-EVT-05 | FR-EVT-01, FR-EVT-02, FR-EVT-05 | BR-EVT-01, BR-EVT-02, BR-EVT-05, BR-EVT-06; `community-events.md` |
| 4. Hỏi trợ lý AI (RAG) | UC-AI-02 | FR-AI-02 | `architecture.md` mục 4.2; `privacy.md` (Dữ liệu gửi cho AI) |
