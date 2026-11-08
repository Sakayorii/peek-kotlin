**Tiếng Việt** | [English](README.en.md)

# peek-kotlin

Bản port Kotlin của [peek-vanilla](https://github.com/Sakayorii/peek-vanilla), mà peek-vanilla tự nó là bản port 1:1 đã được kiểm chứng của [Peek](https://github.com/doan-labs/peek) do Doan Labs làm (MIT).
Một cái tên đưa vào, một khuôn mặt đi ra: avatar xác định, không mạng, không cơ sở dữ liệu.

Thuộc family peek: `peek` (bản gốc, React), `peek-vanilla` (JS), `peek-kotlin` (JVM), `peek-rust` (Rust).

## Lấy về dùng

Qua [JitPack](https://jitpack.io/#Sakayorii/peek-kotlin):

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { implementation("com.github.Sakayorii:peek-kotlin:v2.0.0") }
```

```
src/main/kotlin/com/sakayori/peek/
├── Js.kt - Ngữ nghĩa JavaScript, chính xác (in số, tidy, JSON)
│   ├── Identity.kt - tidy / FNV-1a / mulberry32 / identify  (port của identity.js)
│   ├── Tables.kt - mặt, màu, bộ phận, biểu cảm        (port của tables.js)
│   ├── Scene.kt - mô hình cây node + prune                 (port của các helper trong draw.js)
│   ├── Draw.kt - phần hình học: draw()                    (port của draw.js)
│   └── Svg.kt - settle / serialize / toSvg              (port của svg.js)
├── src/test/kotlin/com/sakayori/peek/
│   ├── PeekGoldenTest.kt - test golden byte-identical (JUnit4)
│   └── CorpusData.kt - corpus golden được sinh ra (không sửa tay)
├── gen-corpus.mjs - sinh lại CorpusData.kt từ peek-vanilla
└── verify.sh - biên dịch bằng kotlinc và chạy test (không cần Gradle)
```

## Quy tắc 100%

Mọi hành vi của bản gốc ảnh hưởng tới kết quả đều được tái hiện, kể cả những phần nhàm chán: định dạng `String(number)` của JS, cách `Math.round` xử lý số ở giữa, chuẩn hóa NFC, chuyển chữ thường không phụ thuộc locale, và thứ tự key của `JSON.stringify` cho các id SVG xác định.

Bằng chứng là cơ học, không phải cảm tính: `./verify.sh` biên dịch bản port và so `toSvg()` với kết quả của peek-vanilla trên 667 trường hợp (tên được chọn lọc với các ca biên về unicode, emoji, khoảng trắng, mọi biểu cảm, mọi ghi đè bộ phận, mọi slot phụ kiện và các tổ hợp phụ kiện, kích thước, frame, riso, tiêu đề, cộng thêm 300 tên fuzz có seed). Lệch một byte là trượt.

```bash
./verify.sh          # biên dịch + chạy test golden
./verify.sh regen    # sinh lại corpus từ peek-vanilla trước, rồi test
```

Chạy test bằng `./gradlew test` (tự sinh lại corpus), hoặc `./verify.sh` để chạy không cần Gradle với một kotlinc có sẵn ở máy.

## API

```kotlin
import com.sakayori.peek.*

// tên đưa vào, chuỗi SVG đi ra, cùng từng byte như peek-vanilla với cùng đầu vào
val svg: String = toSvg("Sakayori")
val svg2: String = toSvg("Sakayori", PeekOptions(expression = "happy", square = false))

// mặc phụ kiện: ba slot độc lập, mặc định đều là none
val svg3: String = toSvg("Sakayori", PeekOptions(eyewear = "glasses", headwear = "sprout", neckwear = "tie"))

// đổi cách vẽ: cùng một tên, ba diện mạo (peek, haze, bloc)
val svg4: String = toSvg("Sakayori", PeekOptions(variant = "bloc"))

// danh tính mà không cần vẽ
val who: Identity = identify("Sakayori") // face, color, eyes, brows, mouth, cheeks, trait, persona
```

`PeekOptions` mô phỏng các props `toSvg` của peek-vanilla: `face`, `color`, `eyes`, `brows`, `mouth`, `cheeks`, `trait`, `eyewear`, `headwear`, `neckwear` (ghi đè theo trục thắng hàm băm; một món phụ kiện không hợp lệ sẽ bị bỏ qua và giữ lựa chọn theo tên), `expression` (11 trạng thái), `gaze`, `size`, `frame` (`ink`/`bone`/`paper`/`none`), `square`, `riso`, `id`, `title`/`hideTitle`, `version`, `variant` (`peek`, `haze` hoặc `bloc`: cách vẽ mặt, không bao giờ thay đổi danh tính; một biến thể lạ sẽ vẽ như peek).

## Bản này port từ upstream phiên bản nào?

Bản này port từ upstream @doan-labs/peek 2.0.0: hệ thống phụ kiện của bản 1.1 (eyewear, headwear, neckwear) và các biến thể vẽ của bản 2.0 (haze, bloc) trên nền engine 1.0. Các khuôn mặt dùng biến thể peek vẫn render giống hệt trước đây đến từng byte. Corpus golden nay có 667 trường hợp, gồm cả các ca biến thể và biến thể kết hợp phụ kiện.

## Ghi chú cho việc tích hợp vào app

- Phần lõi là Kotlin/JVM thuần, không phụ thuộc, gọi an toàn từ bất cứ đâu.
- `toSvg()` cho một avatar 64px tốn ít hơn một mili giây; sinh lại sau mỗi lần gõ phím (ô nhập tên của tính năng listen-together) là rất rẻ.
- Hệ thống animation (`animate.js` chuyển thành Compose) là một giai đoạn riêng; module này là engine xác định mà nó sẽ điều khiển.

## Animation

`PeekAnimator` là bản port không giao diện của hệ thống `Live` trong peek-vanilla (lò xo, biên đạo, chớp mắt kể cả chớp đôi, liếc mắt, thở, hướng nhìn, chuyển đổi biểu cảm, reduced motion), trừ phần DOM. Phía host sở hữu vòng lặp frame: tạo nó, gọi `step(dt)` ở mỗi frame, và vẽ `Pose` được trả về.

```kotlin
val animator = PeekAnimator(identify("Sakayori"), "normal")
// mỗi frame:
val pose = animator.step(dtSeconds)
draw(animator.who, pose, drawOpts) // -> SNode, render theo cách của bạn
animator.setExpression("happy")
```

Hướng nhìn theo con trỏ: `PeekAnimator(who, watchPointer = true)` cộng `updatePointer(...)` ở mỗi frame. Reduced motion: `setReduced(true)` sẽ nhảy thẳng tới tư thế nghỉ. Truyền một `rng` có seed để test tái hiện được.

Đã kiểm chứng từng tư thế một so với hệ thống JS trên 6 kịch bản có sẵn (1800 tư thế: chuyển đổi, chớp đôi, hướng nhìn theo con trỏ và cố định, cả 11 biểu cảm, gật đầu buồn ngủ): độ lệch lớn nhất 1.4e-15.

## Giấy phép

MIT, giống bản gốc. Xem file LICENSE.
