# Verification report

Ngày kiểm tra: 2026-09-22

## Đã kiểm tra tại workspace

- Đối chiếu chức năng với toàn bộ 10 file Java nguồn của bản JavaFX.
- POM là XML hợp lệ.
- `compose.yaml` parse thành công và có đúng hai service `app`, `db`.
- Migration H2 và MySQL được tách riêng để không dùng cú pháp identity chéo hệ quản trị.
- JavaScript vượt qua `node --check`.
- Rà soát sáu tab mới, nội dung giải thích và fallback về tab Tổng quan khi URL không hợp lệ.
- Kiểm tra migration V2 cho nhật ký dinh dưỡng và ảnh cột mốc trên cả H2 lẫn MySQL.
- Kiểm tra giới hạn loại ảnh/dung lượng tại service và giới hạn multipart tại Spring Boot.
- Kiểm tra logic so sánh mức tạ/reps mới với lịch sử cũ trước khi hiển thị confetti.
- Checker và tester độc lập rà ba vòng cho AI hội thoại, quyền thêm/xóa, token xác nhận, dinh dưỡng AI và BMI.
- Kiểm tra tĩnh token xác nhận theo session: không có xác nhận thì không mutation; token sai/cũ không xóa pending mới.
- Kiểm tra giới hạn mô tả món ăn trước khi lưu vào các cột `food_name` và `notes`.
- Kiểm tra BMI chặn NaN/Infinity và phân loại theo giá trị hiển thị đã làm tròn.
- Kiểm tra lệnh quản lý bài tập bằng tiếng Việt, Anh và Nhật.
- Rà cú pháp tĩnh toàn bộ Java, không phát hiện mẫu lỗi ngoặc, dấu chấm phẩy, chuỗi hoặc khối lệnh chưa đóng.
- Đối chiếu toàn bộ `th:action` trong template với controller endpoint.
- Đối chiếu đầy đủ key giữa ba message bundle Việt, Anh và Nhật.
- Quét project bàn giao: không chứa mật khẩu MySQL cũ hoặc đường dẫn `C:/Users/...` trong mã chạy.
- Kiểm tra thủ công transaction tạo bài + lịch sử và ghi workout + cập nhật exercise.
- Builder đã bổ sung authentication/ownership; tester và checker độc lập rà soát takeover dữ liệu cũ, IDOR ảnh và session AI.
- Đã sửa cơ chế claim legacy bằng token server-side, owner-bind pending AI/meal và xóa session riêng tư sau login.
- Đối chiếu tĩnh profile PostgreSQL, bốn migration, `render.yaml`, Dockerfile 3.0.0 và cấu hình secure cookie/proxy.
- Vòng final phát hiện và sửa mapping ảnh PostgreSQL: bỏ `@Lob` (OID) và dùng `LONG32VARBINARY` cho cột `BYTEA`; vẫn cần chạy PostgreSQL integration test ở môi trường có Docker trước khi public chính thức.

## Test tự động có trong project

- `addingExerciseAlsoCreatesHistory`
- `scheduledWorkoutCreatesHistoryAndUpdatesMasterExercise`
- `rejectsZeroReps`
- `historyFilterUsesNameAndDateRange`
- `scheduledWorkoutDetectsWeightAndRepRecords`
- `nutritionSummaryAddsDailyMacros`
- `milestoneStoresAnImage`
- `assistantRequiresConfirmationBeforeAddingExercise`
- `localMealAnalysisCalculatesKnownIngredients`
- `bmiUsesAdultThresholdsForBothSexes`
- `japaneseDisplayedAddCommandCreatesPendingAction`
- `japaneseCompactAddCommandSupportsNoSpacesAndKeepsQuestionsNonMutating`
- `profileRejectsNonFiniteNumbers`
- `mealAnalysisRejectsUnknownMealType`
- `dashboardRendersAllMainAreas`
- `languageCanChangeToEnglishAndJapanese`
- `coachProvidesLocalFallbackWithoutApiKey`
- `assistantMutationRequiresSessionConfirmation`
- `registrationStoresHashedPassword`
- `unauthenticatedDashboardRedirectsToLogin`
- `postWithoutCsrfIsRejected`
- `anotherUserCannotFetchMilestoneImage`
- `loginClearsPreviousAssistantSessionData`
- `usersCannotSeeOrChangeEachOthersExercises`
- `anotherUserCannotConfirmPendingAssistantAction`
- `addParserAcceptsNaturalSpacingAndPunctuationWithoutCorrectingNames`
- `existingExerciseBecomesConfirmedUpdateAndSimilarSpellingStaysSeparate`
- `japaneseFullWidthPunctuationDoesNotPolluteExerciseName`
- `invalidAssistantMessageClearsOlderPendingProposal`
- `scheduleRelationshipStaysConsistentInsideOneTransaction`

## Giới hạn môi trường kiểm tra

Máy tạo artifact chỉ có Java 17 runtime, không có `javac`, Maven/Docker và không thể tải dependency từ Maven Central do giới hạn mạng. Vì vậy `mvn clean test` và việc khởi động server chưa được thực thi tại đây. Trên máy có JDK 17 cùng Maven hoặc Docker, chạy các lệnh trong `README.md`; test/build lỗi phải được coi là blocker trước khi deploy public.

Render đã thực thi test thật và xác nhận Spring context, H2 cùng Flyway V1–V4 khởi động thành công. Hai test lịch tập từng lỗi do collection JPA stale đã được sửa trong production code; bản sửa cần được Render chạy lại để xác nhận toàn bộ suite xanh.
