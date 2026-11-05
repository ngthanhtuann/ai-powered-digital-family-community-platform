# Kiểm tra AI tóm tắt (5-09)

`AiSummaryServiceTest` chạy 10 nguồn giả, xác nhận nội dung nguồn được chuyển nguyên vẹn tới lớp LLM, kết quả được trả nguyên văn và cache lưu theo SHA-256. Đây là kiểm thử luồng với LLM giả lập; nó không chứng minh chất lượng grounding của Gemini.

Khi chạy đánh giá thủ công với khóa Gemini đã cấu hình, dùng các mẫu giả dưới đây. Tóm tắt đạt khi chỉ nhắc dữ kiện có trong mẫu, không thêm tên, ngày, địa điểm hoặc nguyên nhân. Không gửi nội dung gia đình thật khi dùng gói miễn phí.

| # | Nội dung giả | Dữ kiện được phép xuất hiện |
|---|---|---|
| 1 | Gia đình họp mặt vào tháng 3 tại nhà ông An. Mọi người cùng ăn tối. | Tháng 3, nhà ông An, ăn tối |
| 2 | Bà Mai sinh năm 1940 ở Huế. Bà chuyển vào Đà Lạt năm 1965. | 1940, Huế, Đà Lạt, 1965 |
| 3 | Ngày 12 tháng 6, các thành viên trồng 20 cây bàng trong sân nhà. | 12/6, 20 cây bàng, sân nhà |
| 4 | Ông Bình làm nghề mộc. Ông đóng chiếc bàn đầu tiên cho gia đình năm 1972. | Nghề mộc, chiếc bàn, 1972 |
| 5 | Bữa giỗ tổ chức vào Chủ nhật. Có 18 người tham dự. | Chủ nhật, 18 người |
| 6 | Bức ảnh được chụp tại bến đò cũ năm 1988. Trong ảnh có ba anh em. | Bến đò cũ, 1988, ba anh em |
| 7 | Gia đình mở cửa hàng gạo năm 1995 và đóng cửa hàng năm 2001. | Mở 1995, đóng 2001, cửa hàng gạo |
| 8 | Cô Lan học điều dưỡng ở trường tỉnh trong hai năm. Văn bản không nêu năm học. | Điều dưỡng, trường tỉnh, hai năm; không tự thêm năm |
| 9 | Tài liệu ghi tên làng Tân Phú và ngày lập 4/9/1936. Không có thông tin về tác giả. | Tân Phú, 4/9/1936; không tự đặt tác giả |
| 10 | Chuỗi bình luận: cuộc gặp dời từ thứ Bảy sang Chủ nhật; địa điểm vẫn là nhà văn hóa. | Đổi sang Chủ nhật, nhà văn hóa |

Ghi lại kết quả đúng/sai cho từng dữ kiện trước khi chấp nhận chất lượng thực tế. Hiện chưa có kết quả gọi Gemini trong môi trường này nếu `GEMINI_API_KEY` chưa được thiết lập.
