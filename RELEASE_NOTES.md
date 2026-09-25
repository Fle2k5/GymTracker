# Release notes — 3.0.0

Ngày: 2026-09-22

## Hoàn thành

- Chuyển toàn bộ ba khu vực JavaFX sang giao diện web responsive.
- CRUD bài tập, lịch tập, bài trong lịch và lịch sử.
- Ghi buổi tập từ lịch, cập nhật thành tích và lịch sử trong cùng transaction.
- H2 để demo nhanh; MySQL profile và Docker Compose cho triển khai.
- Flyway migration, health endpoint và kiểm thử Spring Boot/MockMvc.
- Bản sửa 1.0.1: tách migration H2/MySQL và đổi các tên cột dễ xung đột từ khóa SQL.
- Bản 1.2.0: thêm giao diện tiếng Việt, tiếng Anh, tiếng Nhật và lưu lựa chọn ngôn ngữ.
- Bản 1.2.0: thêm AI Coach dùng OpenAI Responses API, kèm chế độ gợi ý nội bộ khi chưa có API key.
- Bản 2.0.0: thiết kế lại thành sáu khu vực Tổng quan, Bài tập, Lịch tập, Lịch sử, Dinh dưỡng và Cột mốc.
- Chuyển AI Coach vào Tổng quan để giảm một tab riêng không cần thiết.
- Thêm dashboard chỉ số nhanh và phần giải thích mục đích trong từng tab.
- Thêm nhật ký bữa ăn cùng tổng calories/protein/carbs/chất béo trong ngày.
- Thêm album cột mốc có upload và hiển thị ảnh lưu trong database.
- Thêm phát hiện kỷ lục mức tạ/reps và hiệu ứng confetti chúc mừng.
- Làm mới toàn bộ CSS với màu gradient, glass effect, animation và responsive mobile.
- Bản 2.0.1: thêm bundle `messages_vi.properties` riêng và ép UTF-8 cho message/HTTP response để sửa lỗi chữ tiếng Việt trên Windows.
- Bản 2.2.0: AI Coach có hội thoại theo session và đọc danh sách bài tập hiện tại.
- Thêm lệnh AI liệt kê/thêm/xóa bài tập với bước xem trước, mã xác nhận một lần và thời hạn 5 phút.
- Thêm phân tích dinh dưỡng từ nguyên liệu, số gram và cách nấu; có chế độ OpenAI và bộ ước tính nội bộ khi chưa có API key.
- Thêm hồ sơ BMI người lớn cho nam/nữ và phân loại thể trạng tham khảo; không đưa ra kết luận somatotype thiếu cơ sở.
- Thêm migration V3, giao diện và bản dịch Việt/Anh/Nhật cho toàn bộ tính năng mới.
- Bản 3.0.0: thêm đăng ký, đăng nhập, đăng xuất bằng Spring Security và BCrypt.
- Tách toàn bộ bài tập, lịch, lịch sử, dinh dưỡng, ảnh cột mốc, BMI và ngữ cảnh AI theo tài khoản.
- Trợ lý đọc hồ sơ, lịch, lịch sử gần đây và tổng dinh dưỡng để trả lời, gợi ý lịch và cách chọn kg/reps theo RIR/RPE.
- Thêm profile PostgreSQL, migration Flyway, Docker/Render Blueprint và hướng dẫn Render + Neon free tier.
- Dữ liệu local cũ chỉ được nhận bằng `LEGACY_CLAIM_TOKEN`; không còn cơ chế tài khoản đầu tiên tự nhận dữ liệu.
- Chặn IDOR ảnh cột mốc bằng owner check, trả 404 và `Cache-Control: no-store`.
- Pending action AI và meal analysis gắn owner; login mới xóa dữ liệu hội thoại/pending trong session.
- Cải thiện parser lệnh AI: khoảng trắng/dấu câu linh hoạt, NFKC cho tiếng Nhật, `rep/reps/x/×`, cập nhật bài đã tồn tại và regression test đúng câu `thêm Squat 100kg 5reps`.
- Sửa quan hệ JPA hai chiều giữa lịch tập và bài trong lịch; collection được đồng bộ khi thêm/xóa nên test và cùng một transaction không còn `IndexOutOfBoundsException`.
- Sửa parser tiếng Nhật để nhận lệnh viết liền như `追加ベンチプレス60kg 8回`, xử lý dấu câu và từ chối câu hỏi/phủ định hoặc lệnh thiếu đơn vị số lần.

## Lưu ý nâng cấp dữ liệu

ZIP gốc không có dump hoặc DDL MySQL, nên bản 1.0.0 không tự động nhập database cũ. Hãy backup database `gym_db` và đối chiếu cột thực tế trước khi viết migration import. Đặc biệt cần xử lý tên bài trùng và bài trong lịch không tồn tại ở bảng `exercises`.

## Giới hạn đã biết

- Chưa có xác minh email, quên mật khẩu và rate limit phân tán; chỉ nên dùng như demo/hobby hoặc nhóm nhỏ tin cậy.
- Khối lượng hiện dùng số nguyên để tương thích model gốc.
- Chế độ AI nội bộ miễn phí có phạm vi câu trả lời hẹp hơn OpenAI API.
