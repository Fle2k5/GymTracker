# Kế hoạch kiểm thử chuyển GymTrackerFx sang Spring Boot Web

## 1. Mục tiêu và phạm vi

Tài liệu này được suy ra độc lập từ mã Java trong `GymTrackerFx.zip`. Bản gốc là JavaFX + JDBC trực tiếp, gồm bốn nhóm dữ liệu:

- `exercises`: bài tập hiện tại (`name`, `weight`, `reps`, `date`);
- `history`: các lần ghi nhận kết quả (`id`, `exercise_name`, `weight`, `reps`, `date`);
- `schedules`: lịch tập (`id`, `day`, `muscle_group`);
- `schedule_exercises`: bài tập thuộc lịch (`id`, `schedule_id`, `exercise_name`).

Mục tiêu của bộ test là khóa hành vi nghiệp vụ khi chuyển sang Spring Boot Web, không khóa chi tiết JavaFX/JDBC hoặc các lỗi kỹ thuật của bản cũ. Các đường dẫn HTTP bên dưới là contract đề xuất; nếu implementation chọn URI khác thì đổi URI trong test nhưng giữ nguyên ca nghiệp vụ và expected result.

## 2. Hành vi cần giữ

### 2.1 Bài tập

1. Xem danh sách bài tập với đủ `name`, `weight`, `reps`, `date`.
2. Thêm bài tập tạo một bản ghi `exercises` **và** một bản ghi `history` có cùng dữ liệu.
3. Sửa bài tập thay đổi `weight` và `reps` theo tên; không tự tạo history.
4. Xóa bài tập không xóa history cũ.
5. “Save” từ một bài tập đã chọn chỉ thêm history, không thay đổi bài tập.

### 2.2 Lịch sử

1. Xem toàn bộ lịch sử.
2. Xóa từng history theo `id`.
3. History là nhật ký độc lập: xóa/sửa bài tập không sửa ngược các dòng history đã có.

### 2.3 Lịch tập

1. Xem, thêm, sửa, xóa lịch theo `id`.
2. Xem danh sách bài tập của một lịch.
3. Thêm/xóa bài tập trong lịch.
4. Chọn bài tập trong lịch hiển thị lần tập gần nhất nếu có, nếu không trả trạng thái rỗng.
5. Lưu kết quả tập từ lịch thực hiện nguyên tử hai việc: thêm history với ngày hiện tại của server và cập nhật `weight`/`reps` của bài tập cùng tên.

## 3. Những điểm phải chốt trước khi coi regression test là chuẩn

Đây là các hành vi mơ hồ hoặc lỗi tiềm ẩn trong bản JavaFX; không nên vô tình “đóng đinh” chúng:

| Điểm | Bản cũ | Contract khuyến nghị cho web |
|---|---|---|
| Tên class/bảng | `Excercise` viết sai chính tả | Dùng `Exercise` trong Java/API; giữ tên bảng `exercises` |
| ID của exercise | UI không dùng ID, update theo tên, delete tìm dòng đầu theo tên | Bắt buộc `name` duy nhất, không phân biệt khoảng trắng đầu/cuối; hoặc chuyển mọi mutation sang ID. Test phải phản ánh quyết định này |
| “History gần nhất” | Lấy phần tử khớp cuối cùng từ `SELECT *` không `ORDER BY` | Định nghĩa rõ `ORDER BY date DESC, id DESC LIMIT 1` |
| Kiểu ngày | `String`, không kiểm tra định dạng | API nhận ISO `yyyy-MM-dd`, persistence dùng `DATE` |
| Ngày khi lưu từ lịch | Luôn `LocalDate.now()` | Dùng `Clock` inject được; test không phụ thuộc ngày chạy |
| Xóa schedule | Không rõ xử lý `schedule_exercises` | FK + `ON DELETE CASCADE`, hoặc trả `409`; khuyến nghị cascade vì khớp kỳ vọng xóa lịch khỏi UI |
| Bài tập lịch không tồn tại trong exercises | Bản cũ cho phép thêm tên tự do | Có thể giữ cho compatibility; nếu thêm FK theo tên là thay đổi nghiệp vụ và cần quyết định rõ |
| Thứ tự danh sách | Các câu `SELECT *` không `ORDER BY` | API phải định nghĩa sort ổn định (khuyến nghị `id ASC`) |

Các test bên dưới dùng contract khuyến nghị: tên exercise duy nhất; ngày ISO; “gần nhất” theo `date`, rồi `id`; cascade khi xóa schedule; tên bài tập trong lịch vẫn là chuỗi tự do để giữ hành vi cũ.

## 4. Cấu trúc test đề xuất

```text
src/test/java/.../
  domain/                 # unit test thuần Java
  service/                # Mockito, transaction/orchestration
  repository/             # @DataJpaTest + Testcontainers MySQL
  web/                    # @WebMvcTest
  integration/            # @SpringBootTest + MockMvc + Testcontainers
  workflow/               # workflow xuyên endpoint
src/test/resources/
  application-test.yml
  db/fixture/              # SQL seed nhỏ, rõ ràng
```

- JUnit 5, AssertJ, Mockito.
- `@WebMvcTest` cho binding/status/JSON/validation độc lập DB.
- `@DataJpaTest` và integration dùng cùng một MySQL Testcontainer; không chỉ dùng H2 vì SQL dialect, FK và migration là phần cần kiểm chứng.
- Inject `Clock.fixed(...)` trong test các hành vi dùng ngày hiện tại.
- Dọn trạng thái bằng transaction rollback hoặc `@Sql`; test không phụ thuộc thứ tự chạy.

## 5. Unit test

### 5.1 Validation/domain

| ID | Ca kiểm thử | Input | Expected result |
|---|---|---|---|
| U-01 | Tạo exercise hợp lệ | `Bench Press`, 80, 8, `2026-09-22` | Object/command hợp lệ, giữ đúng bốn giá trị |
| U-02 | Chuẩn hóa tên | `"  Bench Press  "` | Lưu/tra cứu dưới tên `Bench Press` nếu chọn policy trim |
| U-03 | Tên rỗng | `null`, `""`, toàn khoảng trắng | Vi phạm `@NotBlank`; không gọi repository |
| U-04 | Weight âm | `-1` | Vi phạm validation; `0` phải được quyết định (khuyến nghị hợp lệ cho bodyweight) |
| U-05 | Reps không dương | `0`, `-1` | Vi phạm validation; reps tối thiểu 1 |
| U-06 | Ngày sai định dạng/không tồn tại | `22/09/2026`, `2026-02-30` | Binding/validation thất bại, không ghi DB |
| U-07 | Schedule thiếu day | blank/null | Validation thất bại |
| U-08 | Schedule thiếu muscle group | blank/null | Validation thất bại |
| U-09 | Scheduled exercise thiếu tên | blank/null | Validation thất bại |

### 5.2 Service/orchestration

| ID | Ca kiểm thử | Thiết lập | Expected result |
|---|---|---|---|
| U-10 | Thêm exercise ghi hai nơi | Mock exercise/history repository | Mỗi repository `save` đúng 1 lần, dữ liệu giống nhau |
| U-11 | Ghi exercise lỗi | Exercise repository ném exception | History repository không được gọi; transaction rollback |
| U-12 | Ghi history lỗi khi thêm exercise | History repository ném exception | Toàn transaction rollback, không còn exercise |
| U-13 | Update exercise | Existing name, weight/reps mới | Chỉ exercise được update; history repository không được gọi |
| U-14 | Update tên không tồn tại | Repository trả empty | Ném domain `ExerciseNotFound`; map thành HTTP 404 |
| U-15 | Delete exercise | Existing exercise có history | Xóa exercise; không gọi xóa history |
| U-16 | Save history thủ công | Existing exercise + ngày yêu cầu | Thêm đúng 1 history từ snapshot bài tập; exercise không đổi |
| U-17 | Save kết quả từ schedule | Clock cố định `2026-09-22`, 85 kg, 10 reps | Thêm history ngày `2026-09-22` và update exercise cùng tên trong một transaction |
| U-18 | Save kết quả: exercise không tồn tại | Scheduled name không có trong exercises | 404/409 theo contract; không để lại history mồ côi (khuyến nghị 404 + rollback) |
| U-19 | Save kết quả: update lỗi sau khi insert history | Mock update ném exception | Transaction rollback cả history |
| U-20 | History gần nhất | Các dòng cùng tên khác ngày và cùng ngày khác id | Chọn date lớn nhất; nếu cùng date chọn id lớn nhất |
| U-21 | Không có history | Không có dòng cùng tên | Trả empty/`Optional.empty`, không lấy history của tên khác |

## 6. Repository test

Chạy bằng `@DataJpaTest` + MySQL Testcontainers và bật Flyway thật.

| ID | Ca kiểm thử | Expected result |
|---|---|---|
| R-01 | Persist/load exercise | Đọc lại đủ name, weight, reps, date; kiểu ngày không lệch timezone |
| R-02 | Unique exercise name | Insert trùng tên chuẩn hóa bị constraint violation nếu policy tên duy nhất được chọn |
| R-03 | Update theo business key/ID | Chỉ đúng một exercise đổi weight/reps; `date` không đổi |
| R-04 | Delete exercise giữ history | Exercise biến mất, history cùng tên vẫn còn |
| R-05 | Persist/load history | Generated `id` có giá trị; bốn field còn lại đúng |
| R-06 | Delete history by ID | Chỉ dòng đúng ID biến mất |
| R-07 | Find latest history | Nhiều tên/ngày/id | Trả đúng dòng theo `date DESC, id DESC` |
| R-08 | CRUD schedule | Generated ID; update day/group đúng; các dòng khác không đổi |
| R-09 | Query scheduled exercise by schedule | Chỉ trả rows có `schedule_id` yêu cầu, sort ổn định |
| R-10 | Delete scheduled exercise by ID | Chỉ membership được chọn biến mất, schedule còn nguyên |
| R-11 | Delete schedule cascade | Schedule và membership con biến mất; schedule khác còn nguyên |
| R-12 | FK membership → schedule | Insert với `schedule_id` không tồn tại bị reject |
| R-13 | Chuỗi Unicode | Tên `Đẩy ngực`, muscle group `Lưng – xô` round-trip đúng UTF-8 |
| R-14 | Giá trị biên integer | Max hợp lệ theo validation lưu đúng; overflow/beyond column range bị reject |

## 7. MVC slice test

URI đề xuất:

- `GET/POST /api/exercises`, `PUT/DELETE /api/exercises/{id}`;
- `POST /api/exercises/{id}/history`;
- `GET/DELETE /api/history[/{id}]`;
- `GET/POST /api/schedules`, `PUT/DELETE /api/schedules/{id}`;
- `GET/POST /api/schedules/{id}/exercises`;
- `DELETE /api/schedules/{scheduleId}/exercises/{membershipId}`;
- `GET .../{membershipId}/latest-history`, `POST .../{membershipId}/results`.

| ID | Request | Expected result |
|---|---|---|
| M-01 | `GET /api/exercises` | 200 JSON array; content type JSON; đủ field công khai, không lộ entity internals |
| M-02 | `POST /api/exercises` valid | 201 + `Location`; response chứa resource đã tạo |
| M-03 | POST malformed JSON | 400 Problem Details/chuẩn error thống nhất |
| M-04 | POST blank name/negative weight/zero reps | 400; body nêu field lỗi; service không được gọi |
| M-05 | POST invalid date | 400; service không được gọi |
| M-06 | POST duplicate name | 409, không trả stack trace/SQL |
| M-07 | `PUT /api/exercises/{id}` valid | 200 (hoặc 204 nhất quán); service nhận đúng ID và payload |
| M-08 | PUT/DELETE unknown exercise | 404 |
| M-09 | `DELETE /api/exercises/{id}` existing | 204, response body rỗng |
| M-10 | `POST /api/exercises/{id}/history` | 201; gọi save-history đúng một lần |
| M-11 | `GET /api/history` | 200, trả đủ id/name/weight/reps/date |
| M-12 | `DELETE /api/history/{id}` | Existing: 204; missing: 404 |
| M-13 | Schedule CRUD valid | GET 200; POST 201; PUT 200/204; DELETE 204 |
| M-14 | Schedule validation | blank day/group → 400 với lỗi theo field |
| M-15 | List scheduled exercises | 200 array chỉ của schedule trong path |
| M-16 | Add scheduled exercise | 201; schedule missing → 404; blank name → 400 |
| M-17 | Delete membership | Membership thuộc đúng schedule → 204 |
| M-18 | Path ownership mismatch | Membership tồn tại nhưng thuộc schedule khác → 404; không xóa |
| M-19 | Latest history exists | 200 với history gần nhất |
| M-20 | Latest history absent | 204 hoặc 404 theo contract; khuyến nghị 204 để biểu diễn “Empty” của UI |
| M-21 | Save schedule result valid | 201; payload trả history mới và exercise state mới hoặc link đến chúng |
| M-22 | Save schedule result invalid numbers | 400, service không được gọi |
| M-23 | Unsupported method/content type | 405/415 tương ứng |
| M-24 | Exception sanitization | Lỗi DB bất ngờ → 500 error chuẩn, không lộ JDBC URL, username, SQL, stack trace |

## 8. Integration test và tính nguyên tử

Các test này dùng `@SpringBootTest`, `MockMvc`, MySQL Testcontainer, migration thật và không mock service/repository.

| ID | Kịch bản | Expected result |
|---|---|---|
| I-01 | POST exercise rồi GET list | POST 201; GET chứa đúng exercise; DB có 1 exercise + 1 history |
| I-02 | PUT exercise | DB exercise đổi weight/reps; số history không đổi |
| I-03 | DELETE exercise đã có history | Exercise mất; history vẫn truy xuất được |
| I-04 | POST manual history | History tăng 1; exercise không đổi |
| I-05 | Full schedule CRUD | Tạo → đọc → sửa → xóa cho đúng status và state DB |
| I-06 | Add/list/delete scheduled exercise | Mỗi response và DB phản ánh đúng membership |
| I-07 | Latest history deterministic | Seed không theo thứ tự thời gian; endpoint vẫn trả date/id mới nhất |
| I-08 | Save scheduled result | History tăng 1, ngày từ fixed Clock; exercise current values được update |
| I-09 | Rollback save scheduled result | Gây update exercise thất bại sau bước history | Không có history mới; exercise cũ nguyên vẹn |
| I-10 | Concurrent update | Hai request sửa cùng exercise | Nếu có optimistic locking: một thành công, một 409; không silent lost update. Nếu không dùng locking, ghi rõ last-write-wins và test contract đó |
| I-11 | Concurrent duplicate create | Hai POST cùng tên | Đúng một 201, request còn lại 409; DB chỉ có 1 exercise và 1 initial history |

## 9. Workflow người dùng end-to-end ở tầng HTTP

### WF-01 — Ghi bài tập mới

1. POST `Bench Press`, 80 kg, 8 reps, `2026-09-20`.
2. GET exercises.
3. GET history.

Expected: bài tập xuất hiện trong danh sách; history có đúng một dòng khởi tạo cùng dữ liệu; không tạo trùng do refresh/GET.

### WF-02 — Chỉnh bài tập rồi lưu một buổi riêng

1. Tạo exercise.
2. PUT thành 85 kg, 6 reps.
3. Xác nhận history vẫn chỉ chứa snapshot 80/8.
4. POST save-history cho exercise.

Expected: history có thêm snapshot 85/6; exercise vẫn là 85/6.

### WF-03 — Xóa exercise nhưng giữ nhật ký

1. Tạo exercise (tự sinh history).
2. DELETE exercise.
3. GET exercises và GET history.

Expected: exercise không còn; history ban đầu còn nguyên và xóa được riêng bằng history ID.

### WF-04 — Lập lịch và ghi kết quả tập

1. Tạo exercise `Squat` 100/5.
2. Tạo schedule `Monday` / `Legs`.
3. Thêm `Squat` vào schedule.
4. GET latest-history: trả snapshot 100/5 ban đầu.
5. POST result 105/5 với fixed date `2026-09-22`.
6. GET latest-history và GET exercises.

Expected: latest-history là 105/5 ngày 2026-09-22; exercise current cũng là 105/5; tổng history tăng đúng một.

### WF-05 — Lịch chứa tên chưa có trong exercises

1. Tạo schedule; thêm tên tự do `Cable Fly` mà không tạo exercise.
2. GET membership thành công để giữ compatibility.
3. POST result.

Expected: bước 3 trả 404 và không ghi history, trừ khi sản phẩm quyết định tự tạo exercise; nếu chọn tự tạo thì phải đổi expected thành tạo exercise + history trong một transaction.

### WF-06 — Xóa lịch

1. Tạo schedule với hai membership.
2. Xóa schedule.

Expected: schedule và hai membership biến mất; exercise/history cùng tên không bị ảnh hưởng.

### WF-07 — Validation không gây side effect

Gửi lần lượt tên rỗng, weight âm, reps 0, ngày sai, JSON lỗi đến các create/result endpoint.

Expected: tất cả trả 400; số dòng của cả bốn bảng không đổi.

## 10. Kiểm thử schema migration

### Migration contract đề xuất

- Flyway/Liquibase là nguồn chân lý; `ddl-auto=validate` ngoài test, không dùng `create/update` ở production.
- Bốn bảng có PK; `history.id`, `schedules.id`, `schedule_exercises.id` auto-generated; `exercises` nên có `id` auto-generated khi chuyển sang web.
- `schedule_exercises.schedule_id` FK đến `schedules.id`.
- Index cho `history(exercise_name, date, id)` và `schedule_exercises(schedule_id)`.
- Charset/collation hỗ trợ tiếng Việt (`utf8mb4`).
- Credentials không nằm trong migration/source; lấy từ environment/config secrets.

| ID | Ca migration | Expected result |
|---|---|---|
| DB-01 | Migrate database rỗng | Thành công; tạo đủ bảng, PK, FK, index và `flyway_schema_history` |
| DB-02 | Chạy migrate lần hai | Không thay đổi schema/data; không lỗi; không chạy lại migration versioned |
| DB-03 | Validate schema | Flyway validate pass; Hibernate `ddl-auto=validate` khởi động được |
| DB-04 | Fresh install + smoke CRUD | Sau migrate, thực hiện insert/query/delete tối thiểu trên cả bốn bảng thành công |
| DB-05 | FK enforcement | Membership trỏ schedule không tồn tại bị reject |
| DB-06 | Cascade policy | Xóa schedule cho kết quả đúng policy đã chốt; với khuyến nghị, membership con bị cascade |
| DB-07 | Unique policy | Hai exercise cùng tên chuẩn hóa bị reject ở DB, không chỉ ở service |
| DB-08 | Unicode/collation | Dữ liệu tiếng Việt ghi/đọc không biến dạng; so sánh tên theo policy đã chốt |
| DB-09 | Upgrade từ schema legacy | Seed schema cũ không có `exercises.id`/DATE typed, chạy migration | Dữ liệu được giữ đủ, ID backfill duy nhất, chuỗi ngày hợp lệ chuyển thành DATE |
| DB-10 | Legacy ngày không hợp lệ | Seed một date string lỗi trước upgrade | Migration dừng với thông báo hành động được hoặc chuyển theo policy quarantine; tuyệt đối không âm thầm mất dữ liệu |
| DB-11 | Roll-forward failure | Migration cố ý lỗi trong Testcontainer | Version không được đánh dấu thành công; schema không ở trạng thái nửa vời nếu DDL engine hỗ trợ transaction |
| DB-12 | Production profile | App khởi động với migration + validate | Không tự ý thay schema ngoài migration |

Nên có hai pipeline migration riêng: (a) fresh database chạy toàn bộ version; (b) snapshot schema legacy tối thiểu chạy từ baseline lên latest.

## 11. Non-functional và bảo mật tối thiểu

1. SQL injection strings như `"x' OR 1=1 --"` được lưu/tra cứu như dữ liệu, không làm thay đổi query.
2. Payload quá dài bị 400/413 theo giới hạn, không thành lỗi DB 500.
3. Không response/log nào chứa mật khẩu MySQL hard-code từ bản cũ.
4. Nếu web có Spring Security: mutation thiếu CSRF/auth trả 401/403; role read-only không POST/PUT/DELETE được.
5. N+1/query-count check cho list schedules/memberships nếu response lồng nhau.
6. Pagination hoặc giới hạn phải được thêm cho history; test default size, max size và sort ổn định.

## 12. Tiêu chí hoàn tất

- Tất cả unit, MVC, repository, integration, workflow và migration test chạy xanh trong CI trên Java version mục tiêu.
- Không test phụ thuộc timezone máy, thời gian thực, thứ tự test hoặc DB cài sẵn.
- Coverage bắt buộc theo hành vi, không chỉ theo dòng: mọi mutation có success, validation failure, not-found và rollback case.
- Các quyết định ở mục 3 được ghi thành API/schema contract và expected result được cập nhật tương ứng.
- Không dùng production database/credentials trong test.

## 13. Thứ tự triển khai test thực tế

1. Chốt các quyết định ở mục 3 và viết migration tests trước.
2. Viết domain/service unit tests, đặc biệt hai transaction kép.
3. Viết repository tests trên MySQL Testcontainers.
4. Viết MVC slice tests để khóa HTTP contract.
5. Viết integration và bảy workflow chính.
6. Thêm concurrency, security, pagination và performance guard sau khi contract lõi ổn định.
