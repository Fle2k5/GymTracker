# BUILD NOTES

## Phạm vi chuyển đổi

Nguồn duy nhất được khảo sát là `upload/GymTrackerFx.zip`; file gốc không bị sửa. App JavaFX có bốn bảng logic (`exercises`, `history`, `schedules`, `schedule_exercises`) và ba tab (`Bài tập`, `Lịch tập`, `History`). Bản web giữ lại toàn bộ luồng nghiệp vụ nhìn thấy trong mã nguồn:

1. CRUD bài tập.
2. Thêm bài tập đồng thời tạo lịch sử.
3. Lưu snapshot của bài tập vào lịch sử.
4. CRUD lịch tập.
5. Thêm/xóa bài tập con trong lịch.
6. Xem lần tập gần nhất theo tên bài tập.
7. Ghi buổi tập từ lịch và cập nhật kg/reps của bài tập chính nếu có tên tương ứng.
8. Xem/xóa lịch sử.

## Quyết định kỹ thuật

- Java 17, Spring Boot 3.5.6, Maven.
- Spring MVC + Thymeleaf để giữ cách tương tác form/table gần ứng dụng desktop.
- Spring Data JPA thay cho JDBC thủ công.
- H2 file database (`./data/gymtracker`) để chạy ngay, không cần MySQL cục bộ hay mật khẩu hard-code.
- `LocalDate` thay chuỗi ngày để kiểm tra và sắp xếp đúng kiểu.
- Tên bài tập chính là duy nhất (không phân biệt hoa/thường) để việc cập nhật từ lịch không mơ hồ.
- Xóa lịch tập sẽ cascade xóa các mục con; lịch sử tập luyện vẫn được giữ.
- Profile `demo` chỉ seed dữ liệu khi database trống; mặc định không tự tạo dữ liệu giả.

## Cải thiện so với bản gốc

- Chặn tên rỗng và giá trị kg/reps âm.
- Thao tác theo ID thay vì xóa/cập nhật bản ghi đầu tiên có cùng tên.
- Không lưu thông tin đăng nhập database trong source.
- Các thao tác nhiều bước dùng transaction.
- Sắp xếp lịch sử mới nhất trước, thay vì phụ thuộc thứ tự tự nhiên của database.
- Giao diện responsive và có thông báo lỗi/thành công.

## Xác minh

Đã thêm kiểm thử tích hợp cho hai nghiệp vụ cốt lõi và kiểm thử render dashboard. Môi trường thực hiện có JDK 17 nhưng không cài lệnh `mvn`, vì vậy chưa thể chạy Maven build tại chỗ. Các lệnh cần chạy trên môi trường có Maven:

```bash
mvn clean test
mvn clean package
```

Sau khi chạy, mở app bằng:

```bash
java -jar target/gym-tracker-web-1.0.0.jar
```

## Giới hạn có chủ đích

- Chưa có đăng nhập/phân quyền vì app JavaFX gốc là ứng dụng một người dùng.
- Không tự động import database MySQL cũ vì gói nguồn không chứa dump/schema/data.
- Không giữ typo `Excercise` trong model mới; tên đúng là `Exercise`.
