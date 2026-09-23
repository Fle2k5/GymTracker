# Phân tích độc lập GymTrackerFx và đề xuất Spring Boot Web

## 1. Phạm vi và kết luận nhanh

Báo cáo này chỉ dựa trên file gốc `GymTrackerFx.zip`. Dự án là ứng dụng desktop JavaFX nhỏ, dùng JDBC trực tiếp với MySQL. Ứng dụng quản lý ba nhóm dữ liệu: bài tập hiện tại, lịch tập và lịch sử tập. Ngoài CRUD cơ bản, người dùng có thể gắn tên bài tập vào một lịch, xem bản ghi lịch sử gần nhất theo tên và lưu kết quả tập trong ngày.

Không có file tạo database, dữ liệu mẫu, CSS, Maven/Gradle hay test trong gói. Vì vậy schema bên dưới được **suy luận từ SQL trong mã**, không phải DDL được xác nhận.

## 2. Hiện trạng kỹ thuật

- Java 21 (IntelliJ cấu hình Corretto 21, class file version 65).
- JavaFX 21.0.11, được tham chiếu bằng đường dẫn tuyệt đối trong máy Windows của tác giả.
- MySQL Connector/J 9.7.0, cũng tham chiếu bằng đường dẫn tuyệt đối.
- MySQL tại `localhost:3306/gym_db`.
- JDBC thuần; mỗi thao tác tự mở/đóng một connection.
- Source nằm trong default package, không có build descriptor.
- UI dựng hoàn toàn bằng Java, không có FXML.
- CSS được trỏ tới `file:///C:/Users/admin/style.css`, nhưng file này không có trong gói.
- Thông tin DB (`root` và mật khẩu) được hard-code trong cả bốn DAO.

### Cấu trúc lớp

| Lớp | Vai trò hiện tại |
|---|---|
| `Main` | Khởi động JavaFX, dựng tab Bài tập và phối hợp trực tiếp UI/DAO |
| `Excercise` | Model bản ghi bài tập; tên lớp bị viết sai chính tả |
| `ExercisesDAO` | Đọc/thêm/sửa/xóa bảng `exercises` |
| `Schedule`, `ScheduleDAO` | Model và CRUD lịch tập |
| `ScheduleExercise`, `ScheduleExerciseDAO` | Bài tập được gắn vào lịch và CRUD tương ứng |
| `History`, `HistoryDAO` | Model và đọc/thêm/xóa lịch sử |
| `ScheduleView` | Toàn bộ UI lịch tập và hộp thoại chi tiết lịch |
| `HistoryView` | UI lịch sử |

## 3. Database/model hiện có

### Schema suy luận

```sql
CREATE TABLE exercises (
    id     INT AUTO_INCREMENT PRIMARY KEY,
    name   VARCHAR(...) NOT NULL,
    weight INT NOT NULL,
    reps   INT NOT NULL,
    date   VARCHAR(...) NOT NULL
);

CREATE TABLE history (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    exercise_name VARCHAR(...) NOT NULL,
    weight        INT NOT NULL,
    reps          INT NOT NULL,
    date          VARCHAR(...) NOT NULL
);

CREATE TABLE schedules (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    day          VARCHAR(...) NOT NULL,
    muscle_group VARCHAR(...) NOT NULL
);

CREATE TABLE schedule_exercises (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    schedule_id   INT NOT NULL,
    exercise_name VARCHAR(...) NOT NULL
);
```

Các độ dài chuỗi, index, unique constraint và foreign key không thể xác định từ source. `exercises.id` có trong SQL xóa nhưng không được đưa vào model/UI. `schedule_exercises.schedule_id` có quan hệ logic với `schedules.id`, song source không chứng minh DB có foreign key hoặc cascade.

### Quan hệ logic thực tế

- Một `Schedule` có nhiều `ScheduleExercise` qua `schedule_id`.
- `ScheduleExercise` chỉ lưu `exercise_name`; không tham chiếu `exercises.id`.
- `History` cũng chỉ liên hệ với bài tập bằng chuỗi `exercise_name`.
- `Exercise` vừa đóng vai trò danh mục bài tập, vừa giữ thông số gần/current (`weight`, `reps`, `date`).

Điều này khiến việc đổi tên, trùng tên hoặc xóa bài tập không giữ được tính toàn vẹn tham chiếu.

## 4. Chức năng đầy đủ và hành vi thực tế

### Tab “Bai tap”

- Bảng hiển thị `Date`, `Name`, `Weight`, `Reps`.
- Nút **Xem danh sach** tải toàn bộ `exercises`; dữ liệu không tự tải khi mở ứng dụng.
- Form gồm ngày, tên, cân nặng, số reps. Ngày ban đầu là ngày hiện tại.
- Chọn một dòng sẽ đổ dữ liệu dòng đó vào form.
- **Them**:
  - Parse cân nặng/reps thành số nguyên.
  - Chèn một dòng vào `exercises`.
  - Chèn một dòng giống vậy vào `history`.
  - Reload bảng bài tập.
  - Đây là hai thao tác DB độc lập, không có transaction; có thể thêm bài tập thành công nhưng thêm history thất bại.
- **Sua**:
  - Cập nhật `weight`, `reps` cho mọi dòng có cùng `name`.
  - Không sửa `date` hay tên.
  - Không tạo lịch sử mới.
- **Xoa**:
  - Tìm một `id` đầu tiên theo `name`, rồi xóa theo `id`.
  - Nếu trùng tên, dòng được xóa là không xác định vì truy vấn không có `ORDER BY`.
  - Không xóa history hay các dòng trong lịch.
- **Save**:
  - Lấy tên/cân nặng/reps từ dòng đang chọn và ngày từ ô date để thêm một history.
  - Do chọn dòng tự đổ ngày cũ vào ô date, mặc định thao tác này có thể lưu lại ngày của dòng thay vì ngày hiện tại.
- Mọi validation/lỗi chỉ được in ra console; không có thông báo trong UI.

### Tab “Lich tap”

- Bảng hiển thị `Day`, `MuscleGroup`; ID tồn tại trong model nhưng bị ẩn.
- Nút **Xem danh sach** tải toàn bộ `schedules`.
- Chọn dòng điền ngày, nhóm cơ và ID nội bộ vào các field. Field ID được tạo nhưng không gắn vào layout.
- **Them lich** thêm lịch theo ngày và nhóm cơ, sau đó reload.
- **update** sửa lịch đang chọn theo ID, ngày và nhóm cơ.
- **Xoa** xóa lịch theo ID. Source không xử lý các `schedule_exercises` con; kết quả phụ thuộc constraint/cascade trong DB.
- Double-click lịch mở dialog chi tiết:
  - Tải danh sách tên bài tập của lịch.
  - Nhập tên tự do và **Add exercise** để gắn vào lịch; không kiểm tra bài tập đó có trong `exercises` hay không, cho phép trùng.
  - Chọn rồi **Delete** để bỏ bài tập khỏi lịch.
  - Khi click một bài tập, tải toàn bộ history, quét theo tên và hiển thị dòng khớp cuối cùng dưới nhãn “Nearest”. Vì `SELECT *` không có `ORDER BY`, đây không chắc là bản ghi mới nhất.
  - Nhập cân nặng và reps, nhấn **Save** để ghi history với ngày hệ thống hiện tại, rồi cập nhật cân nặng/reps trong `exercises` theo tên.
  - Nếu bài tập lịch là tên tự do không có trong `exercises`, history vẫn được thêm, còn câu update ảnh hưởng 0 dòng mà UI vẫn báo “Saved!”.
  - Một `dateInput` được tạo nhưng không bao giờ hiển thị hay sử dụng.

### Tab “History”

- Bảng hiển thị `id`, `date`, `exerciseName`, `weight`, `reps`.
- Nút **History** tải toàn bộ bảng `history`.
- Chọn một dòng và nhấn **Xoa** để xóa theo ID rồi reload.
- Không có sửa, tìm kiếm, lọc, sắp xếp hay phân trang ở tầng DB.

## 5. Luồng UI tổng thể

1. Mở ứng dụng → thấy ba tab; các bảng ban đầu rỗng.
2. Người dùng phải bấm nút tải dữ liệu trên từng tab.
3. Tại Bài tập, người dùng thêm/cập nhật/xóa hoặc chọn dòng để lưu history.
4. Tại Lịch tập, người dùng CRUD lịch; double-click một lịch để quản lý danh sách bài tập và log kết quả hôm nay.
5. Tại History, người dùng tải và có thể xóa log.

Điểm đáng giữ khi chuyển sang web là ba khu vực nghiệp vụ này và thao tác “log kết quả ngay từ chi tiết lịch”. Điểm nên thay đổi là tải dữ liệu thủ công, double-click khó khám phá, lỗi chỉ ra console và việc liên kết bằng tên.

## 6. Vấn đề/rủi ro cần xử lý khi chuyển đổi

### Tính đúng đắn dữ liệu

- Date dùng `String`, không validation và không bảo đảm định dạng.
- Cân nặng/reps chấp nhận số âm hoặc vô lý; cân nặng chỉ là `int` nên không lưu được 72.5 kg.
- Sửa bài tập theo tên có thể cập nhật nhiều dòng; xóa theo tên có thể xóa dòng bất kỳ.
- “History gần nhất” không hề sort theo ngày/ID.
- Thêm bài tập + history và lưu kết quả + cập nhật exercise không có transaction.
- Liên kết bằng tên dễ orphan và mất tính nhất quán khi đổi tên.
- Xóa schedule có nguy cơ để lại child orphan hoặc lỗi FK.

### Bảo mật/vận hành

- Credential DB nằm trong source và cần được thay ngay; không tái sử dụng mật khẩu đã lộ.
- Kết nối không dùng pool, resource không dùng try-with-resources; statement/result set không đóng tường minh.
- Không có authentication/authorization; ứng dụng web nếu public sẽ cho mọi người sửa/xóa dữ liệu.
- Không có CSRF strategy, audit, logging có cấu trúc, migration hay backup policy.
- Đường dẫn dependency và CSS phụ thuộc máy tác giả.

### UX

- Bảng không tự tải; lỗi/validation vô hình đối với người dùng.
- Double-click là hành vi khó nhận biết trên web/mobile.
- Trộn tiếng Việt và tiếng Anh; nhãn “Nearest” có thể sai nghĩa.
- Không có confirm khi xóa, empty state, loading state, filter hay pagination.

## 7. Kiến trúc Spring Boot Web tương đương được đề xuất

### Lựa chọn phù hợp

Với phạm vi nhỏ, nên làm **modular monolith** Spring Boot, server-rendered bằng Thymeleaf; có thể dùng Bootstrap và HTMX cho dialog/cập nhật từng phần. Cách này giữ độ đơn giản gần JavaFX, không cần vận hành một SPA riêng. Đồng thời thiết kế controller/service sạch để có thể thêm REST/mobile client sau này.

Stack đề xuất:

- Java 21, Spring Boot 3.x.
- Spring Web MVC + Thymeleaf.
- Spring Data JPA/Hibernate.
- Bean Validation.
- MySQL production; Testcontainers MySQL cho integration test, H2 chỉ khi test đơn giản không phụ thuộc dialect.
- Flyway cho schema migration.
- Spring Security nếu triển khai ngoài máy cá nhân; tối thiểu một tài khoản owner.
- Actuator cho health check; SLF4J/Logback cho logging.
- Maven Wrapper hoặc Gradle Wrapper để build tái lập.

### Các tầng/package

```text
com.example.gymtracker
├── config/                 # security, MVC, clock
├── exercise/
│   ├── Exercise.java
│   ├── ExerciseRepository.java
│   ├── ExerciseService.java
│   ├── ExerciseController.java
│   └── dto/
├── schedule/
│   ├── Schedule.java
│   ├── ScheduleItem.java
│   ├── ...Repository.java
│   ├── ScheduleService.java
│   └── ScheduleController.java
├── workout/
│   ├── WorkoutEntry.java
│   ├── WorkoutEntryRepository.java
│   ├── WorkoutService.java
│   └── HistoryController.java
└── common/                 # exception handler, shared DTO/util
```

Controller chỉ xử lý HTTP/form mapping. Service chứa transaction và quy tắc nghiệp vụ. Repository chỉ truy cập dữ liệu. Entity không được truyền thẳng làm form input; dùng request/response DTO để tránh mass assignment và giữ validation rõ ràng.

### Model đích khuyến nghị

| Entity | Trường chính | Ghi chú |
|---|---|---|
| `Exercise` | `id`, `name`, `currentWeight`, `currentReps`, `lastPerformedOn`, `version` | `name` unique theo owner; `weight` dùng `BigDecimal`; `version` cho optimistic locking |
| `Schedule` | `id`, `dayOfWeek` hoặc `displayDay`, `muscleGroup`, `version` | Nếu lịch thực sự theo tuần, dùng enum `DayOfWeek`; nếu là text tự do thì giữ `displayDay` |
| `ScheduleItem` | `id`, `schedule_id`, `exercise_id`, `position` | Foreign key thật tới bài tập; unique `(schedule_id, exercise_id)` nếu không muốn trùng |
| `WorkoutEntry` | `id`, `exercise_id`, `performedOn`, `weight`, `reps`, `createdAt` | Thay `history`; giữ quan hệ ngay cả khi đổi tên. Có thể thêm `exerciseNameSnapshot` nếu muốn giữ tên lịch sử |
| `AppUser` (nếu multi-user) | `id`, `email/username`, password hash | Mọi entity nghiệp vụ cần `owner_id` hoặc quan hệ qua owner |

Nếu yêu cầu là **bảo toàn schema cũ tuyệt đối**, có thể ánh xạ bốn bảng hiện tại trước rồi chạy migration hai pha: tạo FK nullable/backfill theo tên → xử lý tên trùng/missing → chuyển sang ID và thêm constraint. Không nên tự động đoán khi nhiều bài tập cùng tên.

### Quy tắc service/transaction

- `createExerciseAndInitialEntry(request)`: tạo exercise và history ban đầu trong một `@Transactional` method.
- `recordWorkout(exerciseId, request)`: thêm `WorkoutEntry`, cập nhật current stats/date của `Exercise` trong một transaction.
- `updateExercise(id, request)`, `deleteExercise(id)`: luôn định danh bằng ID; xóa cần policy rõ (restrict/soft delete/cascade schedule items, nhưng không xóa workout history ngoài ý muốn).
- `addScheduleItem(scheduleId, exerciseId)`: xác nhận cả hai entity tồn tại và thuộc cùng owner.
- `deleteSchedule(id)`: cascade `ScheduleItem`, không cascade `Exercise` hay `WorkoutEntry`.
- `latestEntry(exerciseId)`: query `findFirstByExerciseIdOrderByPerformedOnDescCreatedAtDesc` thay vì tải toàn bộ rồi quét.
- Dùng một `Clock` được inject để test “ngày hôm nay” ổn định.

### Validation và lỗi

- `name`: bắt buộc, trim, giới hạn độ dài.
- `weight`: `> 0`, decimal với đơn vị kg rõ ràng.
- `reps`: số nguyên dương và có giới hạn hợp lý.
- `performedOn`: `LocalDate`, không nhận chuỗi tùy ý; có thể cấm ngày tương lai.
- `muscleGroup`: bắt buộc; cân nhắc enum hoặc bảng lookup nếu cần báo cáo.
- Chuyển lỗi validation thành message cạnh field; lỗi not-found/conflict qua `@ControllerAdvice`.
- POST/Redirect/GET và flash message cho create/update/delete; có confirm trước delete.

## 8. Web routes/UI flow đề xuất

### Server-rendered routes

| Method | Route | Chức năng |
|---|---|---|
| GET | `/` | Dashboard: lịch hôm nay, lần tập gần nhất, shortcut log |
| GET | `/exercises` | Danh sách/search/page bài tập |
| GET/POST | `/exercises/new` | Form/tạo bài tập; tùy chọn tạo initial workout |
| GET/POST | `/exercises/{id}/edit` | Form/cập nhật bằng ID |
| POST | `/exercises/{id}/delete` | Xóa/disable theo policy |
| GET | `/schedules` | Danh sách lịch |
| GET | `/schedules/{id}` | Trang chi tiết lịch và các bài tập |
| POST | `/schedules` | Tạo lịch |
| POST | `/schedules/{id}` | Cập nhật lịch |
| POST | `/schedules/{id}/items` | Gắn bài tập bằng `exerciseId` |
| POST | `/schedules/{id}/items/{itemId}/delete` | Gỡ khỏi lịch |
| POST | `/exercises/{id}/workouts` | Log cân nặng/reps/ngày |
| GET | `/history` | Lịch sử có filter bài tập/khoảng ngày và pagination |
| POST | `/history/{id}/delete` | Xóa bản ghi có confirm |

Với HTML form, POST cho update/delete là đủ và CSRF hoạt động tự nhiên. Nếu cung cấp REST API, tách `/api/v1/...` và dùng đúng `GET/POST/PATCH/DELETE` với DTO/HTTP status.

### Luồng giao diện

1. **Dashboard** mở vào lịch hôm nay; mỗi bài tập có nút “Ghi kết quả”.
2. **Bài tập** tự tải, có Add/Edit/Delete và “Xem lịch sử”. Form chỉnh sửa không phụ thuộc tên để định danh.
3. **Lịch tập** tự tải. Click/nút “Chi tiết” thay double-click. Trong chi tiết, chọn bài tập từ combobox/search thay nhập tên tự do; log kết quả ngay tại dòng.
4. **Lịch sử** mặc định sort mới nhất trước; filter theo bài tập/ngày, phân trang; xóa có confirm.
5. Tất cả thao tác có success/error feedback trên trang và giữ lại input khi validation fail.

Để giữ đúng hành vi cũ, trang chi tiết schedule nên hiển thị workout mới nhất cạnh từng bài tập. Tuy nhiên nó phải được query theo `exercise_id`, sort rõ ràng và hiển thị `performedOn`.

## 9. Migration dữ liệu

1. Sao lưu DB hiện tại và lấy DDL thật (`SHOW CREATE TABLE`) trước khi code migration.
2. Audit tên bài tập: trim/case normalization; lập danh sách tên trùng và tên xuất hiện trong history/schedule nhưng thiếu ở exercises.
3. Tạo schema mới bằng Flyway với kiểu `DATE`, `DECIMAL`, FK và index.
4. Import `exercises`, sinh mapping `old name/old id → new exercise_id` sau khi người dùng giải quyết ambiguity.
5. Backfill history và schedule items bằng `exercise_id`; những dòng không map được đưa vào bảng/file quarantine, không âm thầm bỏ.
6. Kiểm tra count, orphan, min/max date, giá trị weight/reps bất hợp lệ.
7. Chạy thử trên bản sao, smoke test UI, rồi mới cut over.

Index tối thiểu: `workout_entry(exercise_id, performed_on DESC, created_at DESC)`, `schedule_item(schedule_id, position)`, unique tên bài tập theo owner (nếu áp dụng).

## 10. Kế hoạch triển khai theo lát cắt

1. **Foundation:** Spring Boot, cấu hình qua env/profile, Flyway, JPA, exception handling, CI và Testcontainers.
2. **Exercise + history:** CRUD bài tập, record workout trong transaction, history sort/filter/page.
3. **Schedule:** CRUD lịch, schedule item theo `exercise_id`, latest workout trong chi tiết.
4. **UX/security:** dashboard, validation/feedback/confirm, responsive UI, owner authentication nếu cần.
5. **Migration/cutover:** làm sạch dữ liệu, rehearsal, reconciliation, backup và rollback plan.

### Test quan trọng

- Unit test service cho create/log/update/delete và validation.
- Integration test repository trên MySQL thật qua Testcontainers, đặc biệt query latest history và cascade/restrict.
- MVC test cho route, validation, CSRF, not-found/conflict.
- End-to-end cho hai hành trình cốt lõi: tạo bài tập → có history; mở lịch → log workout → current stats/history cùng cập nhật.
- Test transaction rollback bằng cách làm bước thứ hai thất bại và xác nhận bước đầu không được commit.

## 11. Các quyết định cần xác nhận trước khi build

- Ứng dụng chỉ cho một người dùng hay nhiều tài khoản?
- `day` là thứ trong tuần hay ngày/date cụ thể?
- Một “workout” chỉ có một cặp weight/reps, hay cần nhiều set cho cùng bài tập?
- Khi xóa bài tập có giữ toàn bộ lịch sử (khuyến nghị: có, dùng archive/soft delete)?
- Có cho nhiều bài tập trùng tên không? Khuyến nghị không trong phạm vi một owner.
- Ngày/cân nặng cũ có dữ liệu không hợp lệ hoặc số thập phân không?
- Có cần giữ UI server-rendered đơn giản, hay API cho mobile/SPA là yêu cầu ngay từ đầu?

## 12. Tiêu chí tương đương tối thiểu

Bản web được xem là tương đương khi có thể: xem/thêm/sửa/xóa bài tập; tự ghi history khi tạo theo quy tắc đã chọn; xem/thêm/sửa/xóa lịch; gắn/gỡ bài tập khỏi lịch; xem workout gần nhất; ghi kết quả từ chi tiết lịch đồng thời cập nhật trạng thái hiện tại; xem/xóa history. Ngoài ra, bản web phải dùng ID/FK, transaction, `LocalDate`, validation, cấu hình DB ngoài source và có phản hồi lỗi trong UI để không lặp lại các lỗi cấu trúc của bản JavaFX.
