# TIÊU CHÍ SẢN PHẨM CUỐI — GYM TRACKER WEB

## 1. Cơ sở và cách hiểu phạm vi

Tài liệu này được suy ra độc lập từ `GymTrackerFx.zip`. Gói nguồn hiện tại là ứng dụng JavaFX/JDK 21 kết nối trực tiếp MySQL `gym_db`, có ba khu vực: **Bài tập**, **Lịch tập**, **Lịch sử**. Không có đặc tả, schema SQL, công cụ build chuẩn, test, tài liệu chạy hay mã web đi kèm.

Vì yêu cầu đích nhắc đến UX web, sản phẩm đạt yêu cầu được hiểu là một **ứng dụng web hoàn chỉnh thay thế giao diện JavaFX**, giữ đủ nghiệp vụ quan sát được trong mã nguồn, có dữ liệu bền vững, cấu hình môi trường an toàn, chạy được theo hướng dẫn trên một máy sạch và triển khai được bằng quy trình có thể lặp lại. Không xem một mockup tĩnh, trang chỉ hiển thị dữ liệu mẫu, hoặc frontend không có lưu trữ thật là sản phẩm hoàn chỉnh.

## 2. Định nghĩa “hoàn thành”

Sản phẩm cuối chỉ đạt khi đồng thời thỏa mãn các điều kiện sau:

1. Người dùng có thể hoàn tất toàn bộ luồng bài tập, lịch tập và lịch sử từ trình duyệt mà không dùng IDE, JavaFX hoặc thao tác trực tiếp trong cơ sở dữ liệu.
2. Mọi thao tác tạo/sửa/xóa/ghi nhận buổi tập được lưu bền vững và còn tồn tại sau khi refresh hoặc khởi động lại dịch vụ.
3. Quan hệ dữ liệu giữa lịch tập, bài trong lịch và lịch sử nhất quán; không xóa nhầm bản ghi trùng tên và không để dữ liệu mồ côi ngoài chủ đích.
4. Có validation, trạng thái tải/rỗng/lỗi/thành công và xác nhận cho hành động phá hủy; lỗi không chỉ được in vào console.
5. Không có mật khẩu, chuỗi kết nối hoặc đường dẫn máy cá nhân được hard-code trong mã nguồn/client bundle.
6. Một reviewer có thể clone/giải nén, cấu hình, khởi tạo dữ liệu, build, test và chạy bằng các lệnh được ghi rõ trong README.
7. Bản build production chạy được và có hướng dẫn deploy/rollback tối thiểu.

## 3. Chức năng bắt buộc

### 3.1. Quản lý bài tập hiện tại

Mô hình tối thiểu: `id` ổn định, `name`, `weight`, `reps`, `date`. Dùng `id` để sửa/xóa; tên không được là khóa kỹ thuật.

- Xem danh sách bài tập với các cột ngày, tên, khối lượng (kg), số lần lặp.
- Tải danh sách tự động khi vào màn hình; có nút tải lại nếu cần nhưng không bắt người dùng bấm “Xem danh sách” mới thấy dữ liệu.
- Thêm bài tập với tên, cân nặng, reps và ngày. Ngày mặc định là ngày hiện tại nhưng được phép chỉnh.
- Sửa một bản ghi đã chọn; lưu xong danh sách cập nhật ngay.
- Xóa đúng bản ghi đã chọn theo `id`, có xác nhận trước khi xóa.
- Chọn bản ghi để điền dữ liệu vào form chỉnh sửa hoặc mở panel/modal chi tiết rõ ràng.
- Có thao tác **Ghi vào lịch sử** cho một bài tập hiện tại. Người dùng thấy ngày sẽ được ghi và có thể chọn ngày hợp lệ.
- Khi tạo bài tập mới, hành vi với lịch sử phải được quy định rõ và nhất quán. Để bảo toàn hành vi nguồn, mặc định tạo đồng thời một bản ghi lịch sử trong cùng transaction; nếu thiết kế mới tách hai hành động thì UI phải nói rõ và test phải chứng minh không ghi trùng.

Validation tối thiểu:

- Tên sau khi trim không rỗng.
- `weight` là số không âm; chấp nhận số thập phân nếu mô hình triển khai hỗ trợ, nếu chỉ chấp nhận số nguyên phải ghi rõ.
- `reps` là số nguyên dương.
- `date` là ngày hợp lệ, định dạng hiển thị nhất quán.
- Chặn submit trùng do double-click; lỗi validation đặt cạnh trường tương ứng.

### 3.2. Lịch tập

Mô hình tối thiểu: lịch có `id`, `day`, `muscleGroup`; bài gắn lịch có `id`, `scheduleId`, `exerciseName` (khuyến nghị tham chiếu `exerciseId` nếu dùng danh mục bài tập chuẩn hóa).

- Xem danh sách lịch tập theo ngày và nhóm cơ.
- Thêm, sửa, xóa một lịch tập; mọi thao tác nhận diện bản ghi bằng `id`.
- Mở chi tiết một lịch bằng hành động dễ khám phá (nút/link hoặc click hàng), không phụ thuộc duy nhất vào double-click.
- Trong chi tiết lịch: xem danh sách bài, thêm bài, xóa đúng bài đã chọn.
- Khi chọn một bài trong lịch, hiển thị lần tập gần nhất gồm ngày, kg và reps; nếu chưa có thì hiện empty state rõ ràng.
- Ghi kết quả hôm nay cho bài trong lịch: nhập weight và reps, hệ thống tạo lịch sử với ngày hiện tại và cập nhật thông số hiện tại của bài tập tương ứng nếu bài đó tồn tại.
- Việc ghi lịch sử và cập nhật bài hiện tại phải là một transaction hoặc có cơ chế tránh trạng thái cập nhật một nửa.
- Nếu tên bài trong lịch không khớp bài hiện tại, UI/API phải xử lý rõ: yêu cầu chọn/tạo bài hợp lệ hoặc vẫn lưu lịch sử nhưng thông báo rằng không có bản ghi hiện tại để cập nhật; không được im lặng thất bại.
- Xóa lịch phải xử lý các bài thuộc lịch bằng foreign key/cascade có chủ đích hoặc chặn và giải thích; không để bản ghi mồ côi.

### 3.3. Lịch sử

Mô hình tối thiểu: `id`, `exerciseName` (hoặc `exerciseId` kèm snapshot tên), `weight`, `reps`, `date`.

- Xem lịch sử với đầy đủ ID nội bộ (có thể ẩn khỏi UI), ngày, tên bài, kg và reps.
- Dữ liệu tải tự động và phản ánh ngay các bản ghi tạo từ màn hình bài tập hoặc lịch tập.
- Xóa đúng một bản ghi theo `id`, có xác nhận.
- Thứ tự mặc định phải hữu ích và xác định, khuyến nghị ngày mới nhất trước rồi đến `id` giảm dần.
- “Lần gần nhất” phải được tính bằng ngày/thời gian và tie-breaker xác định, không dựa vào thứ tự tự nhiên của truy vấn `SELECT *`.
- Tối thiểu có lọc theo tên bài và/hoặc khoảng ngày để lịch sử dài vẫn sử dụng được.

### 3.4. API, dữ liệu và tính nhất quán

- Backend cung cấp API thật cho toàn bộ nghiệp vụ; HTTP status và payload lỗi nhất quán.
- Tất cả truy vấn dùng tham số; không ghép chuỗi dữ liệu người dùng vào SQL.
- Kết nối DB được quản lý/đóng đúng cách, có pooling nếu phù hợp.
- Có migration/schema versioned tạo được các bảng, primary key, foreign key/index và kiểu dữ liệu cần thiết.
- Có seed/demo data tùy chọn, tách biệt production.
- Danh sách có thứ tự ổn định; nếu có phân trang thì giữ nguyên filter/sort khi chuyển trang.
- Mutation quan trọng trả về bản ghi sau cập nhật hoặc dữ liệu đủ để client đồng bộ chắc chắn.
- Không yêu cầu xác thực nếu đây là sản phẩm một người dùng cục bộ. Nếu triển khai công khai hoặc đa người dùng, xác thực và cô lập dữ liệu theo user là bắt buộc; không được để toàn bộ dữ liệu gym dùng chung công khai.

## 4. UX web bắt buộc

### 4.1. Kiến trúc thông tin

- Điều hướng chính rõ ràng giữa **Bài tập**, **Lịch tập**, **Lịch sử**; tab/sidebar thể hiện màn hình đang chọn.
- Tên sản phẩm “Gym Tracker”, đơn vị `kg`, ngôn ngữ giao diện nhất quán (toàn Việt hoặc toàn Anh; không trộn lẫn như ứng dụng nguồn).
- Hành động chính nổi bật, hành động xóa mang sắc thái nguy hiểm và tách khỏi nút lưu.

### 4.2. Trạng thái và phản hồi

- Có skeleton/spinner khi tải; empty state có lời giải thích và CTA tạo bản ghi đầu tiên.
- Thành công báo bằng toast/banner ngắn; lỗi hiển thị trên UI với thông điệp hữu ích và tùy chọn thử lại.
- Nút submit bị khóa trong lúc request; form không mất dữ liệu khi request thất bại.
- Dialog xác nhận xóa nêu đúng tên/ngày đối tượng; sau xóa focus quay lại vị trí hợp lý.
- Cảnh báo thay đổi chưa lưu khi đóng form/modal nếu người dùng đã chỉnh dữ liệu.

### 4.3. Form và khả năng truy cập

- Dùng input phù hợp: date picker cho ngày, numeric input cho kg/reps; label thật, không chỉ placeholder.
- Hoạt động bằng bàn phím; focus visible; modal giữ focus và đóng bằng Esc hợp lý.
- Các control có accessible name, bảng có header, lỗi form liên kết với input; màu chữ/nền và trạng thái control đạt tương phản WCAG AA ở mức thực tế.
- Không dùng double-click là con đường duy nhất tới một chức năng.

### 4.4. Responsive và trình duyệt

- Dùng tốt ở các mốc tối thiểu 360 px, 768 px và 1280 px; không tràn ngang toàn trang. Bảng rộng có cơ chế card/scroll cục bộ hợp lý trên mobile.
- Hỗ trợ hai phiên bản mới nhất của Chrome, Edge, Firefox và Safari tại thời điểm bàn giao.
- Không có lỗi nghiêm trọng hoặc promise rejection không xử lý trong browser console ở các luồng chính.

## 5. Tiêu chí nghiệm thu theo kịch bản

| Mã | Kịch bản | Kết quả bắt buộc |
|---|---|---|
| AC-01 | Mở ứng dụng với DB trống | Cả ba màn hình render được, có empty state, không crash |
| AC-02 | Thêm bài `Bench Press`, 60 kg, 8 reps, hôm nay | Xuất hiện trong danh sách; refresh vẫn còn; lịch sử có đúng một bản ghi theo quy ước đã chọn |
| AC-03 | Sửa bản ghi trên thành 62 kg, 6 reps | Đúng bản ghi được sửa theo ID; dữ liệu khác không đổi |
| AC-04 | Tạo hai bài trùng tên rồi xóa một | Chỉ đúng ID được chọn bị xóa |
| AC-05 | Nhập tên rỗng, weight âm, reps chữ/0, ngày sai | Không tạo dữ liệu; từng trường có lỗi dễ hiểu |
| AC-06 | Tạo lịch `Monday / Chest`, sửa thành `Push` | Refresh vẫn hiển thị giá trị mới |
| AC-07 | Thêm 2 bài vào lịch, xóa 1 bài | Chỉ liên kết được chọn bị xóa; lịch và bài còn lại nguyên vẹn |
| AC-08 | Ghi 65 kg × 5 từ chi tiết lịch | Tạo đúng một lịch sử hôm nay và cập nhật bài hiện tại; không có trạng thái cập nhật một nửa |
| AC-09 | Chọn bài có nhiều lịch sử nhập không theo thứ tự | Hiện đúng bản ghi có ngày mới nhất |
| AC-10 | Xóa một bản ghi lịch sử sau xác nhận | Chỉ đúng ID biến mất và không trở lại sau refresh |
| AC-11 | Backend/DB tạm mất kết nối trong lúc lưu | UI báo lỗi, không mất nội dung form, có thể retry; không có bản ghi nửa vời |
| AC-12 | Thao tác bằng bàn phím và viewport 360 px | Hoàn tất được các luồng chính, không bị che nút hoặc mất nội dung |
| AC-13 | Double-click nút lưu hoặc mạng chậm | Không tạo hai bản ghi ngoài ý muốn |
| AC-14 | Khởi động lại toàn bộ stack | Dữ liệu đã lưu vẫn còn và app health check trở lại healthy |

## 6. Build, run, test và deploy

### 6.1. Build/run cục bộ

- Có một quy trình chính thức được khóa phiên bản (`package-lock.json`/tương đương; phiên bản runtime ghi rõ).
- README ghi prerequisites, lệnh cài dependency, tạo file môi trường từ `.env.example`, khởi tạo/migrate DB, seed tùy chọn, chạy dev và chạy production.
- Khuyến nghị có `docker compose up --build` để chạy web + API + DB bằng một lệnh. Nếu không dùng container, script thay thế phải tương đương và không phụ thuộc IDE hay đường dẫn máy cá nhân.
- Build chạy từ checkout sạch, không cần thư mục `out`, `.idea`, JAR tải thủ công hoặc CSS ở `C:/Users/admin/style.css`.
- Startup thất bại nhanh với thông báo rõ khi thiếu biến môi trường; không tự động dùng credential production mặc định.

### 6.2. Test và chất lượng

- Lệnh test duy nhất được ghi trong README và trả exit code khác 0 khi fail.
- Unit test cho validation và logic xác định “lần gần nhất”.
- Integration test cho CRUD/API và transaction ghi lịch sử + cập nhật bài.
- Ít nhất một smoke/E2E test bao phủ: tạo bài → tạo lịch → thêm bài vào lịch → ghi kết quả → thấy lịch sử → xóa.
- Lint/type-check/build production đều pass; không bỏ qua test lỗi để đạt pipeline xanh.
- Không bắt buộc một tỷ lệ coverage tuyệt đối, nhưng các luồng AC-02, AC-04, AC-08, AC-09 và AC-11 phải có kiểm thử tự động hoặc checklist demo có bằng chứng nếu bị giới hạn môi trường.

### 6.3. Production/deploy

- Cấu hình qua environment/secrets: DB host/port/name/user/password, port, origin; không commit secret thật.
- Có image/build artifact production tái lập được, health endpoint/check và log có cấu trúc đủ chẩn đoán nhưng không lộ secret.
- Migration chạy có kiểm soát trước/đồng thời deploy; có hướng dẫn backup DB và rollback phiên bản/migration.
- HTTPS ở lớp hosting/reverse proxy; CORS giới hạn đúng origin; security headers và giới hạn body hợp lý.
- Nếu public: authentication, authorization, rate limiting cơ bản và quản lý session/token an toàn là gate phát hành.
- Ghi rõ URL production hoặc lệnh chạy artifact, nơi dữ liệu được lưu, cách kiểm tra health và cách khôi phục.

## 7. Rủi ro cần xử lý trước nghiệm thu

| Mức | Rủi ro quan sát từ nguồn | Yêu cầu xử lý |
|---|---|---|
| Chặn phát hành | DB URL/user/password MySQL hard-code, có mật khẩu plaintext | Xóa khỏi mã/lịch sử bàn giao nếu có thể, rotate credential, dùng secret/env |
| Chặn phát hành | Không có schema/migration SQL | Bổ sung migration đầy đủ, chạy được trên DB sạch |
| Cao | Sửa/xóa exercise theo `name` nên tên trùng có thể sửa/xóa nhiều hoặc sai dòng | Dùng primary key ID xuyên suốt UI/API/DB |
| Cao | `Save` từ lịch sử và cập nhật exercise là hai thao tác độc lập | Transaction và rollback khi một bước lỗi |
| Cao | “Nearest” lấy phần tử cuối trong `SELECT *`, không ORDER BY | Truy vấn theo ngày giảm dần + tie-breaker/limit |
| Cao | Xóa schedule không thấy xử lý `schedule_exercises` | Foreign key/cascade hoặc quy tắc chặn xóa rõ ràng |
| Cao | Lỗi chỉ in console; người dùng không biết thao tác thất bại | Error boundary/response UI, retry và giữ input |
| Trung bình | Ngày lưu dạng `String`, format/timezone không định nghĩa | Dùng kiểu DATE/TIMESTAMP thích hợp và quy ước timezone |
| Trung bình | Weight dùng `int`, không hỗ trợ mức tạ thập phân | Chọn DECIMAL hoặc ghi rõ giới hạn nghiệp vụ |
| Trung bình | Bài trong schedule chỉ lưu tên, dễ lệch với exercise hiện tại | Chuẩn hóa bằng `exercise_id` hoặc quy tắc fallback rõ ràng |
| Trung bình | Add exercise vừa tạo exercise vừa tạo history, nút Save có thể tạo thêm bản ghi trùng | Định nghĩa semantic, idempotency/chặn double-submit |
| Trung bình | Không xác thực/phân quyền | Chỉ chấp nhận khi local/single-user; public phải bổ sung auth và ownership |
| Thấp | Chính tả lớp `Excercise`, nhãn Việt/Anh trộn lẫn | Chuẩn hóa tên code và copy giao diện trong bản web |

## 8. Format bàn giao bắt buộc

Một gói bàn giao đạt yêu cầu gồm:

1. **Mã nguồn sạch**: frontend, backend, migration, test; không gồm build output/IDE metadata không cần thiết và không chứa secret.
2. **README.md**: mô tả sản phẩm, kiến trúc ngắn, prerequisites, cấu hình, lệnh dev/build/test/prod, migrate/seed, troubleshooting.
3. **`.env.example`**: đủ tên biến, chỉ dùng giá trị giả an toàn.
4. **Schema/migrations** và seed demo tùy chọn; có sơ đồ hoặc mô tả quan hệ dữ liệu.
5. **Artifact triển khai**: Dockerfile + compose/manifest hoặc hướng dẫn artifact tương đương; image/version/tag được xác định.
6. **Bằng chứng kiểm thử**: output CI hoặc báo cáo test, checklist 14 acceptance case ở trên với Pass/Fail và ghi chú.
7. **Tài liệu vận hành ngắn**: health check, log, backup/restore, migrate, rollback, các giới hạn đã biết.
8. **Demo**: URL hoạt động nếu có môi trường deploy; nếu không, video/ảnh của ba màn hình và lệnh chạy local có thể tái hiện đầy đủ.
9. **Release notes**: phiên bản, ngày, chức năng hoàn thành, khác biệt so với JavaFX cũ và known issues.

## 9. Điều kiện loại trực tiếp

Không nghiệm thu nếu có một trong các trường hợp sau:

- Chỉ có giao diện tĩnh/mock data hoặc dữ liệu mất sau refresh.
- Thiếu một trong ba miền chức năng chính: bài tập, lịch tập, lịch sử.
- Reviewer không thể build/run theo README trên môi trường sạch.
- Cần sửa mã nguồn để đổi credential/host hoặc cần file/JAR/đường dẫn trên máy tác giả.
- Có secret thật trong repository hoặc frontend bundle.
- Xóa/sửa sai bản ghi khi có dữ liệu trùng tên.
- Luồng ghi kết quả có thể lưu lịch sử nhưng không cập nhật bài (hoặc ngược lại) mà không báo lỗi/rollback.
- Lỗi backend chỉ xuất hiện trong console và UI báo thành công hoặc im lặng.
- Bản public không có xác thực và cô lập dữ liệu người dùng.

## 10. Khuyến nghị chấm điểm sau khi qua điều kiện bắt buộc

| Nhóm | Trọng số |
|---|---:|
| Đúng và đủ nghiệp vụ | 35% |
| Tính toàn vẹn dữ liệu/API | 20% |
| UX, responsive, accessibility | 20% |
| Build/test/deploy tái lập | 15% |
| Bảo mật và khả năng vận hành | 10% |

Ngưỡng đề xuất: phải qua toàn bộ gate/điều kiện loại trực tiếp, tất cả AC-01 đến AC-14 đạt, và tổng điểm chất lượng từ 80/100 trở lên.
