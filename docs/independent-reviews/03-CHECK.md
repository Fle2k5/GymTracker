# Audit độc lập GymTrackerFx và checklist nghiệm thu bản Spring Boot web

## 1. Phạm vi và mức độ tin cậy

- Nguồn duy nhất được đọc: `upload/GymTrackerFx.zip`, tập trung vào 10 file Java trong `GymTrackerFx/src/` cùng metadata dự án.
- Không sửa ZIP hay source gốc.
- ZIP không chứa DDL/migration, test, README hoặc file build Maven/Gradle. Vì vậy schema bên dưới được suy ra từ SQL trong DAO; các constraint thực tế của database (PK/FK/UNIQUE/CHECK/cascade) không thể xác minh.
- Source chứa 4 bảng được suy ra: `exercises(id?, name, weight, reps, date)`, `history(id, exercise_name, weight, reps, date)`, `schedules(id, day, muscle_group)`, `schedule_exercises(id, schedule_id, exercise_name)`.

## 2. Chức năng thực tế của ứng dụng gốc

### Tab “Bai tap” (`Main.java`)

- Danh sách bài tập chỉ tải khi bấm **Xem danh sach**, không tự tải lúc mở app.
- Bảng hiển thị theo thứ tự cột: Date, Name, Weight, Reps.
- Khi chọn một dòng, form được điền `date/name/weight/reps` từ dòng đó.
- **Them** chèn một dòng vào `exercises`, rồi chèn một snapshot giống hệt vào `history`, sau đó tải lại danh sách.
- **Sua** cập nhật `weight/reps` theo `name`; không cập nhật `date` hoặc `name`.
- **Xoa** xóa một bài theo tên (DAO tìm một `id` đầu tiên rồi xóa theo `id`).
- **Save** ghi dòng đang chọn thành một bản ghi `history`; weight/reps lấy từ object đang chọn, không lấy giá trị vừa nhập trong form; date lấy từ `dateField`.
- `dateField` ban đầu là ngày hiện tại.

### Tab “Lich tap” (`ScheduleView.java`)

- Danh sách lịch chỉ tải khi bấm **Xem danh sach**.
- Thêm lịch bằng `day` và `muscle_group`; chọn dòng sẽ điền form; sửa/xóa lịch theo `id`.
- Bảng chỉ hiển thị Day và MuscleGroup dù có tạo cột `id`.
- Double-click lịch mở dialog danh sách bài tập của lịch đó.
- Trong dialog có thể thêm/xóa tên bài tập khỏi lịch.
- Click một bài tập trong dialog sẽ hiển thị một bản ghi lịch sử được xem là “Nearest”.
- **Save** trong dialog ghi một history mới với ngày hệ thống hiện tại, rồi cập nhật `exercises.weight/reps` theo tên bài tập.

### Tab “History” (`HistoryView.java`)

- Bấm **History** để tải toàn bộ lịch sử; hiển thị id, date, exerciseName, weight, reps.
- Có thể xóa một history theo `id`.

## 3. Phát hiện cần xử lý

Quy ước: **P0** chặn phát hành/bảo mật nghiêm trọng; **P1** có thể sai hoặc mất dữ liệu; **P2** lỗi hành vi/khả dụng đáng kể; **P3** chất lượng/duy trì.

### P0 — Bí mật DB bị lộ trong source và lịch sử Git

- Tất cả DAO hard-code URL `jdbc:mysql://localhost:3306/gym_db`, user `root` và một mật khẩu plaintext **đã được che khỏi bản bàn giao** (ví dụ `ExercisesDAO.java:14-18`, lặp lại trong các DAO khác). ZIP còn chứa `.git`, nên xóa ở commit mới không làm bí mật biến mất khỏi lịch sử.
- Tác động: lộ credential đặc quyền cao; cấu hình không thể tách môi trường; nếu tái sử dụng mật khẩu thì phạm vi ảnh hưởng rộng hơn database này.
- Sửa cụ thể: đổi/thu hồi mật khẩu ngay; tạo user ứng dụng riêng với quyền tối thiểu; lấy datasource từ environment/secret manager; không commit `.env`/secret; làm sạch lịch sử Git nếu repository từng được chia sẻ. Không log URL có credential.

### P0/P1 — Không có authentication, authorization hoặc tách dữ liệu theo người dùng

- Desktop app giả định một người dùng cục bộ. Mọi DAO đọc/sửa/xóa toàn bộ bảng; model không có `user_id`/owner.
- Khi đưa lên web, bất kỳ client nào truy cập được endpoint có thể xem hoặc sửa dữ liệu của tất cả người dùng; endpoint nhận `id` sẽ dễ thành IDOR.
- Sửa cụ thể: quyết định rõ ứng dụng single-user nội bộ hay multi-user. Nếu multi-user, thêm `users` và `owner_id NOT NULL` vào mọi aggregate, lọc owner ở repository/service (không chỉ UI), kiểm tra quyền trên từng GET/PUT/DELETE; dùng Spring Security, password hash thích hợp hoặc OIDC; bật CSRF cho session/cookie; cookie `HttpOnly`, `Secure`, `SameSite`.

### P1 — Hai thao tác ghi liên quan không có transaction

- **Them** gọi `ExercisesDAO.addExercise` rồi `HistoryDAO.addHistory` (`Main.java:99-103`) bằng hai connection khác nhau. Lệnh hai lỗi sẽ để lại exercise không có history.
- **Save** trong dialog ghi history rồi cập nhật exercise (`ScheduleView.java:135-139`); cập nhật lỗi/không match vẫn có history mới. Ngược lại DAO không kiểm tra affected-row nên UI vẫn báo thành công.
- Sửa cụ thể: gom mỗi use case vào một service method `@Transactional`; kiểm tra row count và ném lỗi khi entity mục tiêu không tồn tại; trả response thành công chỉ sau commit. Viết integration test ép lệnh thứ hai thất bại và xác minh rollback cả hai.

### P1 — Nhận dạng exercise bằng tên gây sửa/xóa nhầm và dữ liệu mồ côi

- `updateExercises` chạy `UPDATE exercises ... WHERE name = ?` nên cập nhật **mọi dòng trùng tên** (`ExercisesDAO.java:90-99`).
- `deleteExercises` tìm `SELECT id ... WHERE name = ?`, chỉ đọc dòng đầu rồi xóa; không có `ORDER BY`, nên dòng bị xóa không xác định khi trùng tên (`ExercisesDAO.java:114-126`).
- `schedule_exercises` và `history` chỉ lưu `exercise_name`, không có FK tới exercise. Đổi chính tả/hoa thường hoặc xóa exercise làm quan hệ logic đứt; một tên trong lịch có thể không tồn tại trong `exercises`.
- Sửa cụ thể: dùng khóa `exercise_id` trong API và mọi update/delete; tạo FK từ `schedule_exercises.exercise_id`; quyết định history là snapshot bất biến thì lưu cả `exercise_id` (có thể nullable khi xóa) và `exercise_name_snapshot`. Nếu tên phải duy nhất theo user, thêm `UNIQUE(owner_id, normalized_name)` và xử lý HTTP 409; nếu không, tuyệt đối không dùng tên làm identity.

### P1 — “Nearest”/latest history trả kết quả không xác định

- UI tải `SELECT * FROM history` không `ORDER BY`, duyệt toàn bộ và giữ dòng match cuối (`ScheduleView.java:87-96`; `HistoryDAO.java:14`). SQL không bảo đảm thứ tự, ngày lại là String, nên dòng cuối không nhất thiết mới nhất.
- Sửa cụ thể: dùng typed `DATE`/`TIMESTAMP`; query `WHERE exercise_id=? ORDER BY performed_at DESC, id DESC LIMIT 1`; thống nhất timezone cho timestamp. Đổi nhãn “Nearest” thành “Latest”/“Gần nhất” nếu đó là ý định.

### P1 — Xóa lịch không định nghĩa vòng đời dữ liệu con

- `ScheduleDAO.deleteSchedule` chỉ xóa `schedules`; các dòng `schedule_exercises` có thể thành orphan hoặc lệnh sẽ thất bại nếu DB có FK không cascade. Không có transaction/cascade được thể hiện trong source.
- Sửa cụ thể: khai báo FK `schedule_exercises.schedule_id REFERENCES schedules(id)` và chọn rõ `ON DELETE CASCADE` (phù hợp nếu dòng con không có ý nghĩa độc lập) hoặc chặn xóa với thông báo; kiểm thử cả trường hợp lịch có bài tập.

### P1 — Thiếu schema quản lý bằng mã nguồn và constraint dữ liệu

- Không có Flyway/Liquibase/DDL trong ZIP; bản web không thể tái tạo database nhất quán.
- Không thấy bằng chứng về `NOT NULL`, FK, UNIQUE, CHECK hay index. `weight/reps` có thể âm/0, tên/ngày có thể rỗng hoặc quá dài; `schedule_id` có thể không tồn tại.
- Sửa cụ thể: thêm migration có PK auto-increment, FK/index, độ dài chuỗi, `NOT NULL`, và CHECK theo rule đã chốt (ví dụ `weight >= 0`, `reps > 0`); index `(owner_id, performed_at)` và các FK. Cần migration dữ liệu/kiểm tra duplicate-orphan trước khi thêm constraint.

### P1/P2 — Kiểu ngày là String và hành vi ngày đang lỗi

- Entity/DAO dùng `String date` và `setString`; chấp nhận chuỗi bất kỳ, khó sort/query đúng.
- Trong **Them**, handler đọc date rồi đặt về hôm nay, nhưng sau insert lại `dateField.clear()` (`Main.java:95-109`); lần thêm tiếp theo mặc định là chuỗi rỗng, không còn ngày hôm nay.
- **Sua** bỏ qua date dù selection điền date vào form. **Save** cho phép tùy ý lấy date trong field. Dialog tạo `dateInput` nhưng không thêm vào layout và không dùng (`ScheduleView.java:79-80`), luôn dùng ngày máy chủ/app hiện tại.
- Sửa cụ thể: API dùng ISO-8601 và `LocalDate` cho ngày buổi tập (hoặc `Instant` cho thời điểm); Bean Validation + lỗi field cụ thể; UI date picker mặc định hôm nay sau mỗi lần lưu. Chốt contract riêng: edit exercise có/không được sửa ngày; quick-save dùng ngày client chọn hay ngày server, rồi test đúng contract.

### P2 — “Save” ở tab bài tập bỏ qua dữ liệu người dùng vừa sửa trong form

- Nút Save lấy weight/reps từ `selected`, không lấy `weightField/repsField` (`Main.java:140-146`). Người dùng chọn dòng, sửa con số trong form rồi bấm Save sẽ lưu giá trị cũ.
- Sửa cụ thể: tách rõ hai hành động/nhãn: “Ghi lại bộ hiện tại” dùng form đã validate, và “Lưu snapshot dòng đã chọn” nếu thật sự cần. Web API nhận DTO rõ ràng thay vì suy từ state selection.

### P2 — Semantics mô hình exercise/history chưa nhất quán

- **Them** vừa tạo `exercises` vừa tạo `history`, nên `exercises` có vẻ là current state/catalog; nhưng người dùng có thể tạo nhiều dòng cùng tên/ngày. Schedule chỉ giữ tên và khi save lại cập nhật mọi exercise cùng tên.
- **Sua** thay current weight/reps nhưng không ghi history; **Save** ghi history nhưng không cập nhật current state. Hai nút tạo các trạng thái khác nhau khó hiểu.
- Sửa cụ thể trước khi migrate: chọn một mô hình miền rõ ràng. Khuyến nghị `exercises` là danh mục (id, name), `workout_sets`/`history` là log bất biến (exercise_id, performed_on, weight, reps), `schedule_items` tham chiếu exercise_id. “Current/latest” tính từ log mới nhất thay vì lưu bản sao dễ lệch. Nếu cần personal record/current target, đặt bảng/cột riêng và update có transaction/version.

### P2 — Validation và phản hồi lỗi gần như không có

- Chỉ kiểm tra rỗng ở một vài selection/name; `Integer.parseInt` nhận số âm và lỗi với khoảng trắng/ký tự; day, muscle group, exercise name có thể rỗng; không giới hạn độ dài.
- Ngoại lệ chỉ được in console và UI thường không có thông báo; user có thể tưởng ghi thành công. DAO cũng luôn in “Completed/Updated” mà không kiểm tra row count.
- Sửa cụ thể: DTO + `@NotBlank`, `@Size`, `@Positive`/`@PositiveOrZero`, typed date; chuẩn hóa trim/Unicode/case theo rule; global exception handler trả RFC 9457/Problem Details hoặc error DTO ổn định; frontend hiển thị lỗi cạnh field và giữ input khi thất bại. Trả 404 khi id không tồn tại, 409 khi conflict, 400/422 khi validation.

### P2 — Web làm tăng bề mặt tấn công

- PreparedStatement hiện được dùng cho input, nên không thấy SQL injection trực tiếp; các `Statement` chỉ chạy chuỗi hằng. Tuy nhiên chuyển web dễ phát sinh repository/query động và stored XSS từ `name/day/muscle_group`.
- Sửa cụ thể: tiếp tục parameterized query/JPA; không ghép sort/filter vào SQL nếu chưa allow-list; template phải escape output, không dùng `th:utext`/`innerHTML` với dữ liệu người dùng; CSP hợp lý; giới hạn request size/rate; không trả stack trace, SQL, credential; audit log hành động nhạy cảm nhưng không log secret/session/PII.

### P2 — Lost update khi nhiều request web đồng thời

- Desktop thường một tiến trình/người dùng nên không lộ rõ; web cho phép hai tab/user sửa cùng row, request sau silently ghi đè request trước.
- Sửa cụ thể: thêm cột `version` với optimistic locking (`@Version`) hoặc precondition `ETag/If-Match`; trả 409/412 và cho người dùng reload/merge. Transaction không thay thế kiểm soát lost update.

### P2/P3 — Truy vấn và kết nối không phù hợp server web

- Mỗi DAO tự mở connection bằng `DriverManager`; resource không dùng try-with-resources, nên exception giữa chừng có thể rò connection/statement/result set. `SELECT *` không phân trang và không `ORDER BY` ở tất cả màn hình.
- JavaFX chạy DB đồng bộ trên UI thread nên app cũ có thể treo; bê nguyên cách này sang controller web sẽ giữ request thread lâu và cạn connection.
- Sửa cụ thể: Spring-managed `DataSource` + Hikari pool; repository/JdbcTemplate/JPA; giới hạn timeout; phân trang và sort xác định (`id`/date); đóng resource tự động; controller mỏng, business logic ở service; đo N+1/query count.

### P3 — Khả năng build/deploy và chất lượng mã

- Không có Maven/Gradle; dependency dựa vào IntelliJ `.iml` và thư viện local; ZIP chứa `.idea`, `out/*.class`, `.git` nhưng thiếu CSS thực tế.
- CSS hard-code `file:///C:/Users/admin/style.css` (`Main.java:199`), không portable. Class `Excercise` và method `deleteExereciseFromSchedule` sai chính tả; default package; field package-private.
- `HistoryView` khai báo `TableColumn<History,String>` cho id/weight/reps dù property là int; JavaFX reflection có thể vẫn hiển thị nhưng generic sai.
- Sửa cụ thể: project Maven/Gradle chuẩn, Java package rõ; CSS/static asset đóng gói; bỏ artifact IDE/binary khỏi source distribution; đổi tên `Exercise` và migration/API tương thích; CI build sạch + test.

## 4. Chi tiết JavaFX dễ bị mất khi đổi sang web

1. **Tải dữ liệu theo nút, không tự động:** cả ba danh sách chỉ xuất hiện sau khi bấm nút. Bản web nên chủ động quyết định giữ hành vi hay tự load; đừng vô tình coi màn hình rỗng là bug dữ liệu.
2. **Selection → form:** click row ở Exercise/Schedule điền form; web cần giữ id ẩn/route param đáng tin cậy, nhưng server không được tin id/owner từ DOM.
3. **Double-click schedule:** đây là lối vào duy nhất của dialog chi tiết. Web/mobile không có double-click đáng tin; cần nút “Chi tiết/Bài tập” hoặc row action có keyboard/touch accessibility.
4. **Dialog giữ context lịch:** thêm/xóa/save trong dialog luôn dùng `selected.getId()` của lịch đã mở. Web route nên dạng `/schedules/{scheduleId}/items`, xác minh item thuộc đúng schedule trước khi xóa.
5. **Refresh sau mutation:** app tải lại cả bảng sau thêm/sửa/xóa. Frontend web phải cập nhật state hoặc refetch sau commit; không optimistic success nếu server rollback.
6. **Ba nghĩa của Save/Update:** Exercise-Save tạo history, Exercise-Sua sửa current row, dialog-Save vừa tạo history vừa cập nhật current exercise. Cần nhãn và API riêng, không gộp mơ hồ.
7. **History là snapshot:** xóa/sửa exercise không sửa history hiện có. Nếu đây là chủ ý, bản web phải giữ tính bất biến của snapshot và không cascade-delete log khi xóa exercise.
8. **Ngày:** add form cho phép date tùy chỉnh, dialog save lại ép ngày hiện tại, trong khi `dateInput` chết. Cần product decision, tránh vô tình đổi timezone/ngày khi server chạy UTC.
9. **Thứ tự hiển thị:** source không quy định thứ tự. Bản web phải đặt default sort cụ thể thay vì phụ thuộc PK/DB insertion order.
10. **Cột ẩn:** Schedule có id nhưng không hiển thị; idField cũng không được add vào layout. Web vẫn phải giữ id nội bộ, không buộc user nhập/sửa id.
11. **History “Nearest”:** click item mới tính và hiển thị label; không preload. Có thể giữ lazy endpoint latest-history, nhưng phải query đúng và xử lý loading/empty/error.
12. **Tab không đóng:** ba khu vực cố định. Điều hướng web cần bảo toàn Exercise / Schedule / History và back/refresh/deep link hợp lý.

## 5. Thiết kế đích khuyến nghị

- Layer: Controller (HTTP/DTO) → Service (`@Transactional`, ownership, rule) → Repository → MySQL.
- Aggregate đề xuất:
  - `users(id, ...)` nếu multi-user.
  - `exercises(id, owner_id, name, normalized_name, version, ...)` là danh mục.
  - `workout_sets(id, owner_id, exercise_id, exercise_name_snapshot, performed_on, weight, reps, created_at)` là log.
  - `schedules(id, owner_id, day/order_or_date, muscle_group, version)`.
  - `schedule_items(id, schedule_id, exercise_id, position)`; unique `(schedule_id, exercise_id)` nếu không cho lặp.
- Không expose JPA entity trực tiếp. Dùng request/response DTO, allow-list field, không bind `ownerId`, `id`, `version` tùy tiện.
- Các endpoint mutation dùng id ổn định; service kiểm tra parent-child và owner. Có pagination cho exercises/history/schedules.
- Flyway/Liquibase là nguồn chân lý schema; secret qua environment/secret manager; profile dev/test/prod riêng.

## 6. Checklist nghiệm thu bản Spring Boot web

### A. Build, cấu hình, triển khai

- [ ] Clone sạch có thể build/test bằng một lệnh Maven/Gradle, không cần IntelliJ hay JAR local.
- [ ] Không còn password/token/private key trong source, history phát hành, log, image hoặc frontend bundle; credential cũ đã rotate.
- [ ] App chạy bằng DB user quyền tối thiểu; cấu hình dev/test/prod tách biệt.
- [ ] Flyway/Liquibase dựng được DB rỗng và nâng cấp được DB có dữ liệu mẫu.
- [ ] Static CSS/JS đóng gói hoặc pipeline rõ, không có đường dẫn `C:/Users/...`.
- [ ] Health/readiness không tiết lộ secret và chỉ ready sau khi DB/migration sẵn sàng.

### B. Authentication và authorization

- [ ] Contract single-user hay multi-user được ghi rõ; môi trường public không để CRUD vô danh ngoài chủ ý.
- [ ] Với multi-user, mọi list/detail/create/update/delete/latest query đều scope theo authenticated owner.
- [ ] Test user A không đọc/sửa/xóa được id của user B, kể cả đổi URL/body thủ công.
- [ ] Session/cookie/OIDC an toàn; CSRF được test cho request thay đổi trạng thái nếu dùng cookie session.
- [ ] 401 và 403 phân biệt đúng; response không tiết lộ entity của user khác có tồn tại.

### C. Database và toàn vẹn dữ liệu

- [ ] Tất cả bảng có PK; relationship dùng FK/index, không dùng tên exercise làm khóa liên kết.
- [ ] `NOT NULL`, length, CHECK cho weight/reps và rule UNIQUE đã được chốt bằng migration.
- [ ] Không còn orphan `schedule_items`; xóa schedule có hành vi cascade/restrict đúng tài liệu.
- [ ] Trùng tên exercise có hành vi xác định (cho phép bằng id, hoặc từ chối 409 theo owner).
- [ ] Add exercise + initial history rollback toàn bộ nếu một bước lỗi.
- [ ] Log workout + update state/PR rollback toàn bộ nếu một bước lỗi.
- [ ] Update/delete id không tồn tại không báo thành công giả.
- [ ] Hai request update đồng thời gây conflict có kiểm soát, không silently lost update.
- [ ] History snapshot không bị đổi ngoài ý muốn khi rename/delete exercise.

### D. Chức năng Exercise

- [ ] Danh sách hiển thị đúng date, name, weight, reps; sort/pagination ổn định.
- [ ] Thêm hợp lệ tạo đúng exercise/log theo domain contract, và UI hiển thị sau commit.
- [ ] Form mới mặc định đúng ngày hôm nay ở timezone sản phẩm; sau save vẫn giữ default đúng.
- [ ] Sửa chỉ cập nhật đúng row bằng id; không ảnh hưởng row khác cùng tên.
- [ ] Hành vi sửa name/date được ghi rõ và test (được phép hoặc field read-only).
- [ ] Xóa đúng row được chọn; confirmation và rule liên quan history/schedule rõ ràng.
- [ ] “Save workout/history” lưu chính giá trị người dùng vừa nhập, không phải object cũ trong bảng.

### E. Chức năng Schedule

- [ ] CRUD schedule bằng id; day/muscle group được validate.
- [ ] Có action chi tiết dùng được bằng click, keyboard và touch; không phụ thuộc double-click.
- [ ] Chi tiết chỉ liệt kê items của đúng schedule.
- [ ] Thêm item từ exercise hợp lệ; duplicate có hành vi xác định.
- [ ] Xóa item xác minh cả `scheduleId` và `itemId`, không xóa item của schedule khác.
- [ ] Save workout từ schedule tạo history cho đúng exercise và cập nhật đúng state theo contract.
- [ ] Khi exercise không tồn tại/đã xóa, UI/API báo lỗi rõ hoặc dùng snapshot theo policy, không success giả.

### F. Chức năng History

- [ ] List history có pagination, default sort `performed_on DESC, id DESC`.
- [ ] “Latest” dùng query có `ORDER BY ... LIMIT 1`, không duyệt `SELECT *` rồi lấy dòng cuối.
- [ ] Trạng thái empty/loading/error hiển thị rõ.
- [ ] Xóa history chỉ đúng id + owner và có confirmation; policy audit/retention được ghi rõ.
- [ ] Ngày là typed date/time, JSON ISO-8601; test timezone sát nửa đêm và leap day.

### G. Validation, lỗi và UX

- [ ] Server bắt buộc validate, không chỉ frontend: blank/trim, độ dài, integer, min/max, date.
- [ ] Test số âm, 0, overflow, Unicode, khoảng trắng, tên rất dài, date sai format.
- [ ] Status code 400/422, 404, 409, 401/403 đúng; response lỗi ổn định và không có stack trace/SQL.
- [ ] UI giữ input khi lỗi, hiển thị lỗi theo field, không báo thành công trước commit.
- [ ] Sau mutation, UI refetch hoặc cập nhật state nhất quán; reload/back/deep link không mất context bất ngờ.

### H. Security web

- [ ] Input SQL luôn parameterized; sort/filter dynamic chỉ nhận allow-list.
- [ ] Tên exercise/day/muscle group chứa `<script>` được hiển thị dạng text, không thực thi; có CSP phù hợp.
- [ ] CSRF, CORS, clickjacking/frame policy, secure headers và cookie flags được kiểm thử theo kiến trúc deploy.
- [ ] Có giới hạn body/page size/rate hợp lý; endpoint list không thể kéo toàn DB.
- [ ] Log/audit không chứa password, token, cookie, connection string hoặc dữ liệu nhạy cảm; action ghi/xóa có correlation/user/id.
- [ ] Dependency scan và secret scan chạy trong CI; không commit `.git`, `.idea`, `out/*.class` vào gói phát hành.

### I. Hiệu năng và vận hành

- [ ] Dùng connection pool, timeout, try-with-resources/Spring repository; test pool không cạn khi exception.
- [ ] Query list/latest có index và `EXPLAIN` chấp nhận được trên dữ liệu lớn.
- [ ] Không N+1 khi tải schedule/items hoặc exercise/latest history.
- [ ] Transaction boundary được đo/test; retry chỉ dùng cho thao tác idempotent hoặc có idempotency key.
- [ ] Backup/restore và rollback migration được diễn tập trước cutover.

### J. Kiểm thử hồi quy tối thiểu

- [ ] Unit test validation và business rule; repository test với MySQL-compatible Testcontainers, không chỉ H2.
- [ ] Integration test từng endpoint gồm happy path, invalid, not found, conflict, unauthorized/forbidden.
- [ ] E2E phủ ba khu vực Exercise/Schedule/History và luồng dialog cũ tương đương.
- [ ] Test duplicate names, partial failure/rollback, concurrent update, schedule deletion with items, latest-history order.
- [ ] Test migration trên bản sao dữ liệu cũ: phát hiện/giải quyết duplicate, bad dates và orphan trước khi bật constraint.

## 7. Thứ tự sửa đề xuất

1. Rotate credential; xác định single/multi-user và domain model chuẩn.
2. Viết migration + script kiểm kê/làm sạch dữ liệu cũ; thay liên kết tên bằng id.
3. Dựng security/ownership và DTO validation trước khi mở endpoint.
4. Cài service transaction cho hai use case ghi kép; thêm optimistic locking.
5. Sửa latest-history, typed date, pagination/order, affected-row handling.
6. Dựng UI web giữ đủ các interaction quan trọng nhưng thay double-click bằng action accessible.
7. Chạy integration/E2E/security tests và diễn tập migrate + rollback trước phát hành.

## 8. Tiêu chí chặn phát hành

Không nghiệm thu nếu còn bất kỳ điểm nào sau: credential cũ còn hiệu lực; CRUD web không có ownership/auth phù hợp; thao tác kép không rollback nguyên tử; update/delete vẫn nhận dạng exercise bằng name; history latest không có ordering xác định; schema không có migration/FK/validation cốt lõi; response lộ stack trace/SQL; chưa có test IDOR, stored XSS, partial failure và migration dữ liệu cũ.
