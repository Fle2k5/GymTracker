# GymTrackerWeb

Bản web thay thế ứng dụng desktop JavaFX `GymTrackerFx`. Project dùng Java 17, Spring Boot 3, Thymeleaf, Spring Data JPA và Flyway.

## Chức năng

- Quản lý bài tập: xem, thêm, sửa, xóa theo ID.
- Tự tạo lịch sử khi thêm bài tập và cho phép lưu lại thành tích hiện tại.
- Quản lý lịch tập theo ngày/buổi và nhóm cơ.
- Chọn bài tập đã tồn tại để thêm vào lịch; không còn liên kết bằng chuỗi tên tự do.
- Ghi kg/reps từ lịch trong một transaction: vừa tạo lịch sử vừa cập nhật thành tích hiện tại.
- Hiển thị lần tập gần nhất đúng theo ngày và ID.
- Tìm lịch sử theo tên bài và lọc theo khoảng ngày.
- Dashboard tổng quan: số bài tập, lịch đang hoạt động, số buổi trong 7 ngày, mức tạ/reps cao nhất và calories hôm nay.
- Nhật ký dinh dưỡng theo bữa với calories, protein, carbs và chất béo.
- Album cột mốc lưu ảnh tiến bộ trực tiếp trong database (JPG/PNG/WEBP/GIF, tối đa 8 MB).
- Tự phát hiện kỷ lục mức tạ hoặc reps và chạy hiệu ứng chúc mừng bằng confetti.
- Chuyển ngôn ngữ Việt / English / 日本語 và ghi nhớ lựa chọn bằng cookie.
- AI Coach nằm ngay trong Tổng quan, có hội thoại theo phiên và đề xuất nhóm cơ cùng 3–5 bài tập dựa trên dữ liệu hiện có.
- Trợ lý đọc hồ sơ chiều cao/cân nặng/BMI, lịch tập, lịch sử gần đây và tổng dinh dưỡng hôm nay để trả lời theo ngữ cảnh riêng.
- AI có thể liệt kê, đề nghị thêm hoặc xóa bài tập; mọi thay đổi database đều phải được người dùng xác nhận trong 5 phút.
- Lệnh tự nhiên chấp nhận nhiều cách viết như `thêm Squat 100kg 5reps`, `thêm bài Squat, 100 kg x 5`; nếu bài đã tồn tại, trợ lý đề nghị cập nhật thay vì báo trùng.
- AI phân tích nguyên liệu, định lượng và cách nấu để ước tính calories/protein/carbs/chất béo; kết quả chỉ lưu khi xác nhận.
- Hồ sơ BMI người lớn cho nam/nữ với phân loại thể trạng tham khảo.
- Tự dùng gợi ý nội bộ nếu chưa cấu hình OpenAI hoặc API tạm thời không khả dụng.
- Đăng ký, đăng nhập, đăng xuất; dữ liệu bài tập, lịch, lịch sử, dinh dưỡng, ảnh và hồ sơ được tách theo tài khoản.
- Giao diện responsive cho desktop và điện thoại.

## Đưa lên Internet miễn phí

Project đã có `Dockerfile`, `render.yaml`, profile PostgreSQL và hướng dẫn từng bước. Xem [DEPLOY_FREE.md](DEPLOY_FREE.md). Cách đề xuất là:

- Render Free chạy web và cấp URL HTTPS công khai.
- Neon Free giữ PostgreSQL để dữ liệu không mất khi Render ngủ hoặc redeploy.
- Không cần cài Java/Maven trên máy nếu chỉ upload source lên GitHub rồi kết nối Render.

Đây là free tier dành cho demo/hobby, có giới hạn tài nguyên và có thể thay đổi theo nhà cung cấp. Render có thể ngủ khi không dùng nên lần mở đầu có thể chậm. AI nội bộ không tốn API; OpenAI API là tùy chọn và có chi phí riêng.

## Cách 1: chạy nhanh với H2

Yêu cầu JDK 17+ và Maven 3.9+:

```bash
mvn spring-boot:run
```

Mở [http://localhost:8080](http://localhost:8080). Dữ liệu nằm tại `./data/gymtracker-v2.mv.db` và vẫn còn sau khi khởi động lại.

Chạy với dữ liệu mẫu trong database trống:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

## Cách 2: chạy web + MySQL bằng Docker

```bash
cp .env.example .env
# Đổi các mật khẩu trong .env
docker compose up --build
```

Mở `http://localhost:8080`. Kiểm tra trạng thái backend tại `http://localhost:8080/actuator/health`.

Tắt dịch vụ nhưng giữ database:

```bash
docker compose down
```

Chỉ xóa volume database khi bạn chắc chắn không cần dữ liệu nữa:

```bash
docker compose down -v
```

## Test và đóng gói

```bash
mvn clean test
mvn clean package
java -jar target/gym-tracker-web-3.0.0.jar
```

Flyway tự chọn migration trong `db/migration/h2`, `db/migration/mysql` hoặc `db/migration/postgresql` theo profile. Hibernate chạy ở chế độ `validate`, nên app sẽ dừng sớm nếu schema không khớp model.

### Nếu gặp `Detected failed migration to version 1`

Đây là database demo được tạo dở bởi bản migration cũ. Dừng ứng dụng rồi xóa thư mục `data` trong project, sau đó chạy lại. Không xóa thư mục này nếu nó đã chứa dữ liệu thật mà chưa backup.

## Cấu hình MySQL không dùng Docker

Kích hoạt profile `mysql` và đặt biến môi trường:

```bash
export SPRING_PROFILES_ACTIVE=mysql
export DB_URL='jdbc:mysql://localhost:3306/gym_db?useSSL=false&serverTimezone=UTC'
export DB_USERNAME='gymtracker'
export DB_PASSWORD='your-secret'
mvn spring-boot:run
```

Không ghi mật khẩu thật vào `application*.properties` hoặc commit file `.env`.

## Bật OpenAI cho AI Coach

AI Coach vẫn hoạt động ở chế độ nội bộ khi không có API key. Để dùng OpenAI Responses API, đặt biến môi trường trước khi chạy:

### Git Bash trên Windows

```bash
export OPENAI_API_KEY='your-api-key'
export OPENAI_MODEL='gpt-5.6-luna'
```

### PowerShell

```powershell
$env:OPENAI_API_KEY='your-api-key'
$env:OPENAI_MODEL='gpt-5.6-luna'
```

Sau đó khởi động app. Không đặt API key trong JavaScript, HTML hoặc commit lên Git. Có thể thay model bằng model mà tài khoản OpenAI API của bạn được phép sử dụng. Cách gọi dựa trên [OpenAI Developer Quickstart](https://developers.openai.com/api/docs/quickstart).

## Chạy bằng VS Code khi chưa cài Maven

Mở đúng thư mục có `pom.xml`, mở `GymTrackerApplication.java`, rồi nhấn **Run** phía trên hàm `main`. Khi cần làm sạch cache: `Ctrl + Shift + P` → `Java: Clean Java Language Server Workspace` → restart.

## Cấu trúc chính

```text
src/main/java/com/example/gymtracker/
├── config/       dữ liệu demo và cấu hình ngôn ngữ
├── model/        entity JPA
├── repository/   Spring Data repositories
├── service/      nghiệp vụ và transaction
└── web/          MVC controller

src/main/resources/
├── db/migration/ Flyway schema
├── templates/    giao diện Thymeleaf
└── static/       CSS và JavaScript
```

## Khác với bản JavaFX cũ

- Không còn mật khẩu MySQL hard-code hoặc đường dẫn CSS/JAR phụ thuộc máy Windows.
- Ngày dùng `LocalDate`, dữ liệu sửa/xóa bằng ID, lịch sử sắp xếp xác định.
- Bài trong lịch tham chiếu bài tập bằng foreign key.
- Xóa lịch tự xóa các mục con; xóa bài không xóa lịch sử thành tích đã ghi.
- Các thao tác nhiều bước nằm trong transaction và lỗi được hiển thị theo ngôn ngữ đang chọn.

## Nâng cấp database cũ

Migration giữ dữ liệu cũ dưới một tài khoản hệ thống đã khóa; người đăng ký thông thường không thể nhìn thấy hoặc nhận nhầm dữ liệu đó. Chỉ khi chính chủ đặt biến `LEGACY_CLAIM_TOKEN` trên server và nhập đúng token trong form đăng ký thì dữ liệu cũ mới được chuyển. Với database hosted mới hoàn toàn, để trống trường này.

## Giới hạn hiện tại

Đây là app demo/hobby, chưa phải hệ thống thương mại: chưa có xác minh email, quên mật khẩu, rate limit phân tán hay object storage. Không nên mở đăng ký công khai cho lượng truy cập lớn. Hãy backup PostgreSQL định kỳ nếu dữ liệu quan trọng.

Ảnh cột mốc được lưu trong database để cài đặt đơn giản và đi cùng bản backup. Khi số lượng ảnh lớn, nên chuyển phần lưu file sang object storage và chỉ giữ metadata/URL trong database.

Gợi ý tập luyện không thay thế chẩn đoán hoặc tư vấn y tế. Trợ lý không suy ra mức tạ an toàn chỉ từ chiều cao/cân nặng; nó dùng lịch sử, đề xuất tải thử thận trọng và RIR/RPE để người dùng tự điều chỉnh. Không gửi thông tin sức khỏe nhạy cảm vào ô AI Coach nếu không muốn dữ liệu đó được gửi tới nhà cung cấp API.
