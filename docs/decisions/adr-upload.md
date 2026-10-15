# ADR: Cách lưu và tải ảnh

> Task SCRUM-31 (TV3 PiLo257). So sánh lưu file trên volume Docker và MinIO (S3 tương thích). Ghi quyết định, giới hạn kích thước, định dạng cho phép (jpg, png, webp), đặt tên file bằng UUID.
>
> Đã chốt 9/10/2026: cho phép `pdf` (tối đa 10 MB) với `owner_type = HERITAGE`, xem `conventions.md` mục 7.3; ADR này cần tính đến loại tệp đó.
