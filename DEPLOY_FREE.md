# Triển khai miễn phí để dùng không qua localhost

Hướng dẫn này dùng **Render Free** cho Spring Boot và **Neon Free** cho PostgreSQL. Không dùng H2 trên hosting vì filesystem của web service miễn phí có thể bị xóa khi restart/redeploy.

## Bạn cần chuẩn bị

Bạn không cần cài Java hay Maven nếu triển khai qua Docker. Bạn chỉ cần:

1. Tài khoản GitHub.
2. Tài khoản Neon.
3. Tài khoản Render.
4. Tùy chọn: OpenAI API key nếu muốn câu trả lời AI linh hoạt hơn chế độ nội bộ.

## Bước 1 — đưa source lên GitHub

Giải nén project. Tạo repository GitHub mới rồi upload **toàn bộ nội dung thư mục có `pom.xml`**. Không upload `.env`, thư mục `data`, `target` hay API key.

## Bước 2 — tạo PostgreSQL miễn phí trên Neon

1. Tạo project mới trong Neon.
2. Mở **Connection details**, chọn Java/JDBC nếu có.
3. Ghi lại host, database, username và password.
4. Tạo giá trị `DB_URL` dạng:

```text
jdbc:postgresql://HOST/DATABASE?sslmode=require
```

Không dán các giá trị này vào GitHub.

## Bước 3 — tạo web service trên Render

1. Trong Render chọn **New → Blueprint** và kết nối repository GitHub.
2. Render đọc file `render.yaml`; xác nhận service dùng plan **Free**.
3. Điền các secret được yêu cầu:

| Biến | Giá trị |
|---|---|
| `DB_URL` | JDBC URL ở bước 2 |
| `DB_USERNAME` | username Neon |
| `DB_PASSWORD` | password Neon |

`SPRING_PROFILES_ACTIVE=postgresql` và model tiết kiệm đã nằm trong `render.yaml`.

Nếu muốn bật OpenAI sau này, mở **Environment** của service rồi thêm `OPENAI_API_KEY`. Không thêm biến này nếu muốn giữ chế độ nội bộ miễn phí.

4. Deploy và chờ build Docker hoàn tất.
5. Mở URL `https://...onrender.com`, đăng ký tài khoản rồi đăng nhập.

Flyway tự tạo bảng trong lần chạy đầu. Endpoint kiểm tra là `/actuator/health`.

## AI: miễn phí và bản đầy đủ

- **Không có `OPENAI_API_KEY`:** app vẫn trả lời nội bộ cho nhóm cơ, mức tạ/reps và lịch cơ bản; phân tích món ăn hoạt động với danh sách nguyên liệu có sẵn. Không phát sinh phí API.
- **Có `OPENAI_API_KEY`:** trợ lý hội thoại dùng hồ sơ, bài tập, lịch, lịch sử và dinh dưỡng của tài khoản hiện tại để trả lời linh hoạt hơn. API OpenAI được tính phí riêng; không đặt key trong HTML/JavaScript/GitHub.
- Nếu muốn giảm chi phí, giữ `OPENAI_MODEL=gpt-5.6-luna`. Có thể đổi sang model khác mà tài khoản API được phép dùng.

Nếu site public dùng một API key chung, chủ site chịu chi phí của mọi câu hỏi. Với demo công khai, nên để key trống hoặc chỉ cấp URL cho người tin cậy.

## Dữ liệu cũ từ bản local

Database hosted mới không có dữ liệu cũ nên để trống phần “nhận dữ liệu cũ”. Nếu deploy chính database đã nâng cấp từ bản local:

1. Tạo token dài, ngẫu nhiên và đặt biến server `LEGACY_CLAIM_TOKEN`.
2. Trong lần đăng ký tài khoản chính chủ, mở mục dữ liệu cũ và nhập đúng token.
3. Chỉ tài khoản có token mới nhận dữ liệu legacy. Token sai làm rollback cả đăng ký.
4. Xóa biến này khỏi Render sau khi nhận dữ liệu thành công.

Luôn backup trước khi nâng cấp database thật.

## Giới hạn free tier

- Render Free có thể ngủ sau thời gian không có truy cập; lần mở tiếp theo có thể mất khoảng một phút.
- Không lưu ảnh hoặc H2 vào filesystem của Render. Bản này lưu ảnh trong PostgreSQL, vì vậy cần theo dõi dung lượng Neon.
- Free tier phù hợp demo/hobby, không có cam kết như production. Chính sách và hạn mức của nhà cung cấp có thể đổi.

## Nếu deploy lỗi

Kiểm tra Render Logs theo thứ tự:

1. `DB_URL` phải bắt đầu bằng `jdbc:postgresql://`.
2. Có `?sslmode=require` với Neon.
3. Username/password không có dấu cách thừa.
4. Health check dùng `/actuator/health`.
5. Nếu Flyway báo migration lỗi, không xóa database đang có dữ liệu; lưu log và backup trước khi sửa.
