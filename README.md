# ProbeGuard

Plugin kiểm tra "tính toàn vẹn client" cho server **Paper / Minecraft 26.3** (bản release mới nhất).
Nó làm phần lớn việc mà **AntiSpoof / AntiSpoof Pro** quảng cáo: phát hiện client giả brand,
giấu mod, spoof Bedrock, cộng thêm 2 probe hành vi thật (ping/pong và echo plugin-message).

> Đây là plugin tự viết, không phải bản crack/decompile của AntiSpoof Pro.
> Mã nguồn mở, mọi thứ đọc được, sửa được, và **không có "magic Deep-Probe"** — xem phần Giới hạn.

---

## 1. Yêu cầu

| Thành phần | Phiên bản |
|---|---|
| Server | Paper 26.3 (hoặc fork tương thích) |
| Java | **JDK 25** (Paper 26.3 compile bằng class file 69 → javac 21 không đọc được) |
| Bắt buộc | [PacketEvents](https://github.com/retrooper/packetevents) **2.14.0+** (bản hỗ trợ MC 26.3) |
| Tuỳ chọn | Floodgate (nhận diện Bedrock), PlaceholderAPI (placeholder) |

Không có PacketEvents thì plugin **tự tắt** và ghi log lý do — vì toàn bộ phần đọc packet đi qua nó.

---

## 2. Build

```powershell
.\build.ps1
```

Hoặc thủ công:

```powershell
$env:JAVA_HOME = 'C:\Users\ADMIN\tools\jdk-25.0.5+8'   # JDK 25, không phải 21
$env:Path = "$env:JAVA_HOME\bin;C:\Users\ADMIN\tools\apache-maven-3.9.9\bin;$env:Path"
cd C:\Users\ADMIN\ProbeGuard
mvn -B -DskipTests clean package
```

Kết quả: `target/ProbeGuard-1.0.0.jar`.

Cấu hình Maven (`pom.xml`):

- `paper-api` → `26.3.build.252-stable` (provided)
- `packetevents-api` → `2.14.0` từ repo CodeMC (provided) — bản release đầu tiên hỗ trợ 26.3
- `placeholderapi` → `2.11.6` (provided)
- `maven.compiler.release` = **25**

## 3. Cài đặt

1. Chép `ProbeGuard-1.0.0.jar` vào `plugins/`.
2. Chép `PacketEvents` (2.14.0+, bản `packetevents-spigot-*.jar` **đã shade**, ~5.7 MB) vào `plugins/`.
3. Khởi động server, kiểm tra log:
   ```
   [ProbeGuard] ProbeGuard enabled (checks: brand, channels, handshake, settings, bedrock, ping)
   ```
4. Sửa `plugins/ProbeGuard/config.yml` rồi `/probeguard reload`.

> Cẩn thận: artifact `packetevents-spigot` trên CodeMC chỉ là module mỏng (~150 KB) và **không chạy được**
> nếu thiếu thư viện. Hãy dùng file jar tải từ GitHub Releases.

---

## 4. Các check

Mỗi phát hiện là **một finding riêng biệt** (key + detail). Điểm nghi ngờ = tổng weight của
các finding *khác nhau*, nên một finding không tự cộng dồn vô hạn.

### Thụ động (chỉ đọc dữ liệu client đã gửi)

| Key | Ý nghĩa | Weight mặc định |
|---|---|---|
| `brand-empty` | Client không gửi `minecraft:brand` | 4 |
| `brand-blacklisted` | Brand khớp regex blacklist (Wurst, Meteor, LiquidBounce...) | 4 |
| `brand-not-whitelisted` | Brand không khớp whitelist (nếu whitelist không rỗng) | 4 |
| `brand-malformed` | Brand chứa ký tự điều khiển / khoảng trắng bất thường | 4 |
| `channel-blocked` | Client đăng ký channel nằm trong blacklist | 5 |
| `vanilla-with-mod-channels` | Brand "vanilla" nhưng lại đăng ký channel của mod | 5 |
| `channel-unknown-namespace` | Channel có namespace lạ (không thuộc allow-list) | 3 |
| `handshake-mismatch` | Brand vanilla/no-brand nhưng có channel `fml:`/`fabric:`... | 6 |
| `settings-anomaly` | View-distance / locale / skin-mask bất thường | 2 |
| `bedrock-spoof` | Tên có prefix Bedrock (`.`) nhưng Floodgate báo không phải Bedrock | 9 |

### Chủ động (probe hành vi thật)

| Key | Cách hoạt động |
|---|---|
| `ping-missed` | Gửi N packet **play Ping** với id ngẫu nhiên, chờ client trả **Pong** đúng id. Client vanilla luôn trả lời; client chặn packet / giả lập protocol / một số cheat client thì không. |
| `ping-slow` | (tuỳ chọn, mặc định tắt) RTT trung bình của pong vượt ngưỡng → nghi lớp proxy/spoof chèn độ trễ. |
| `echo-no-reply` | (opt-in) Gửi plugin message trên channel mà mod X phải trả lời; không thấy trả lời → client giấu mod. |
| `echo-mod-reply` | (opt-in) Client trả lời trên channel thuộc `flag-replies-on` → có mod đang nói chuyện dù brand không nhắc tới. |

Lưu ý kỹ thuật: **ping probe chỉ chạy trên MC 1.17+** (play-phase Ping/Pong có từ 1.17) và
**bỏ qua player Bedrock** nếu `probe.bedrock-bypass: true`, vì Geyser không trả lời ping kiểu Java.

---

## 5. Cấu hình (`config.yml`)

Các khoá chính (file mẫu có comment đầy đủ):

```yaml
debug: false

checks:
  brand:      { enabled: true, weight: 4, allow-empty: false, blacklist: [...], whitelist: [] }
  channels:   { enabled: true, weight: 5, allowed-namespaces: [...], ignore: [...] }
  handshake:  { enabled: true, weight: 6, grace-seconds: 15 }
  settings:   { enabled: true, weight: 2, min-view-distance: 2, max-view-distance: 32 }
  bedrock:    { enabled: true, weight: 9, prefix: "." }
  ping:       { enabled: true, weight: 4, probes: 3, interval-ticks: 20, timeout-ms: 4000 }
  echo:       { enabled: false, weight: 7, probes: [ { channel: ..., payload: ..., expect-reply: false } ] }

response:
  alert-permission: probeguard.alerts
  alert-score: 6          # >= ngưỡng này thì báo staff
  kick-score: 0           # 0 = không bao giờ auto-kick
  console-commands: []    # ví dụ: - "ban %player% Client spoofing (%reason%)"
  discord: { enabled: false, webhook-url: "", min-score: 6 }

probe:
  start-delay-ticks: 60
  rescan-interval-seconds: 300
  bedrock-bypass: true
```

Placeholder trong `alert-format`, `kick-message`, `console-commands`:
`%player%`, `%uuid%`, `%score%`, `%brand%`, `%channels%`, `%reason%`.
Format nhận **MiniMessage** (`<red>`, `<gradient:...>`) hoặc mã `&c` cũ.

`payload` của echo probe nhận: chuỗi thường, `hex:0a1b2c`, hoặc `base64:...`.

### Khuyến nghị khi bật kick

Đừng bật `kick-score` ngay. Chạy `alert-score: 6`, quan sát 1–2 tuần, xem log những ai bị báo,
rồi mới đặt `kick-score` cao hơn (ví dụ 12) để tránh false positive từ client lạ (Lunar, LabyMod,
Feather, Bedrock, proxy...).

---

## 6. Lệnh & quyền

| Lệnh | Mô tả |
|---|---|
| `/pg check <player>` | Điểm + danh sách finding của player |
| `/pg brand <player>` | Brand thô client gửi |
| `/pg channels <player>` | Channel đã đăng ký (kể cả qua `minecraft:register`) |
| `/pg probe <player>` | Chạy lại ping + echo probe ngay |
| `/pg reload` | Nạp lại `config.yml` |

Alias: `/probeguard`, `/pg`, `/clientguard`.

| Permission | Mặc định | Dùng cho |
|---|---|---|
| `probeguard.command` | op | check / brand / channels |
| `probeguard.admin` | op | probe / reload |
| `probeguard.alerts` | op | nhận alert |
| `probeguard.bypass` | false | bỏ qua toàn bộ check (đặt cho staff) |

PlaceholderAPI: `%probeguard_brand%`, `%probeguard_score%`, `%probeguard_channels%`,
`%probeguard_findings%`, `%probeguard_reason%`, `%probeguard_bedrock%`,
`%probeguard_locale%`, `%probeguard_view_distance%`.

---

## 7. Giới hạn — đọc trước khi tin tưởng

1. **Không có "Deep-Probe" bí mật.** AntiSpoof Pro là closed-source; không ai biết chính xác nó
   làm gì. ProbeGuard làm những gì *chứng minh được*: đọc packet handshake/brand/channel/settings
   và gửi probe có phản hồi xác định. Không có gì "thần thánh" ở đây.
2. **Brand và channel đều do client tự khai** — client sửa được. Vì vậy mới cần probe hành vi.
   Nhưng probe cũng chỉ là heuristic: một client giả lập đầy đủ protocol (trả lời ping, trả lời
   plugin message, gửi brand "vanilla") sẽ **vượt qua**.
3. **Không phải anti-cheat movement.** Nó không phát hiện KillAura/Reach/Fly. Dùng chung với
   Grim/Matrix/Vulcan nếu cần.
4. **Echo probe mặc định tắt** vì nó phụ thuộc mod cụ thể; bật bừa sẽ tạo false positive.
5. **Bedrock/Geyser**: mặc định bỏ qua probe hành vi. Nếu tắt `bedrock-bypass`, player Bedrock
   gần như chắc chắn bị flag `ping-missed`.
6. **Không lưu dữ liệu.** Profile chỉ nằm trong RAM, xoá khi player quit. Muốn log lịch sử thì
   dùng `console-commands` + Discord webhook.
7. **PacketEvents 2.14.0** là bản release đầu tiên hỗ trợ MC 26.3; bản snapshot mới hơn có thể đổi
   API. Nếu build lỗi, kiểm tra lại các wrapper (`WrapperPlayServerPing`, `WrapperPlayClientPong`,
   `WrapperConfigClientPluginMessage`, ...).
8. **`api-version: '1.20'`** trong `plugin.yml` chỉ để Paper chấp nhận plugin; trên thực tế jar
   build bằng Java 25 nên chỉ chạy được trên server MC 26.x (Java 25).

---

## 8. Cấu trúc mã nguồn

```
src/main/java/dev/probeguard/
├── ProbeGuardPlugin.java        # wiring, task, reload, kiểm tra PacketEvents
├── config/Settings.java         # snapshot immutable của config.yml
├── profile/                     # ClientProfile (state 1 kết nối) + ProfileRegistry
├── detect/
│   ├── Checks.java              # 5 check thụ động
│   ├── PingProbe.java           # probe ping/pong
│   ├── EchoProbe.java           # probe plugin-message (opt-in)
│   ├── DetectionEngine.java     # gom finding, tính điểm, quyết định alert/kick
│   └── ViolationType.java       # danh sách key ổn định
├── listener/                    # ProbeListener (PacketEvents) + PlayerListener (Bukkit)
├── response/ResponseManager.java# chat alert, console command, kick, Discord webhook
├── command/ProbeGuardCommand.java
├── integration/                 # FloodgateHook (reflection) + PlaceholderHook
└── util/Text.java               # MiniMessage + legacy & codes
```

Luồng dữ liệu: **netty thread** (`ProbeListener`) → copy giá trị vào `ClientProfile` →
`DetectionEngine.scheduleRescan()` (debounce 250 ms, hop sang main thread) → `Checks.run()` →
`ResponseManager`. Không có Bukkit API nào bị gọi từ netty thread.

---

## 9. So với AntiSpoof Pro

| | ProbeGuard (plugin này) | AntiSpoof Pro |
|---|---|---|
| Giá | Miễn phí, mã nguồn mở | ~$19.99 |
| MC 26.3 | Có | Phụ thuộc tác giả cập nhật |
| Brand / channel / handshake check | Có | Có |
| Behaviour probe | Có (ping/pong, echo) — tự cấu hình | "Deep-Probe" (không rõ nội dung) |
| Discord webhook | Có | Có |
| PlaceholderAPI | Có | Có |
| Bedrock-aware | Có (Floodgate) | Có |
| Lưu lịch sử / database | Không | Không rõ |
| Hỗ trợ / cập nhật | Bạn tự sửa | Tác giả trả lời |

Nếu cần một thứ "cài là chạy, có tác giả support", mua Pro. Nếu cần kiểm soát, đọc được code,
và chấp nhận tự vận hành, dùng cái này.

---

## 10. Đã kiểm chứng

Build và chạy thử thật trên:

- Paper **26.3 build 252** (STABLE), JDK 25.0.5
- PacketEvents **2.14.0** (bản `packetevents-spigot-2.14.0.jar` từ GitHub Releases)

Log khởi động:

```
[ProbeGuard] Loading server plugin ProbeGuard v1.0.0
[ProbeGuard] Enabling ProbeGuard v1.0.0
[ProbeGuard] ProbeGuard enabled (checks: brand, channels, handshake, settings, bedrock, ping)
```

Chưa kiểm chứng được với client thật (môi trường build không có client), nên các probe
`ping-missed` / `echo-*` mới chỉ đúng về mặt biên dịch + logic, chưa có số liệu thực tế.
Hãy chạy `alert-score` trước, đừng bật kick ngay.

