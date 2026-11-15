# Đưa ProbeGuard lên GitHub và có link tải `.jar`

Có 2 cách. Cách A không cần cài gì. Cách B dùng Git (chuẩn, về sau sửa code chỉ cần `git push`).

---

## Cách A — chỉ dùng trình duyệt (5 phút)

### A1. Tạo repository

1. Vào https://github.com/new
2. **Repository name**: `ProbeGuard`
3. Chọn **Public** (để người khác tải được) hoặc Private
4. **Không** tick "Add a README file" (vì đã có sẵn)
5. Bấm **Create repository**

### A2. Upload mã nguồn

1. Trong repo vừa tạo, bấm **Add file → Upload files**
2. Mở thư mục `C:\Users\ADMIN\ProbeGuard`, bôi đen **toàn bộ** file/thư mục bên trong
   (`src`, `pom.xml`, `README.md`, `build.ps1`, `.gitignore`, `.github`, `build.log` thì bỏ)
   rồi kéo-thả vào trang web
3. Ô commit message ghi `ProbeGuard 1.0.0`, bấm **Commit changes**

> Lưu ý: kéo cả thư mục `target/` cũng được nhưng không cần — jar sẽ được tạo ở bước A3
> bằng bản build sẵn trên máy bạn.

### A3. Tạo Release để có file `.jar`

1. Vào tab **Releases** (cột phải trang repo) → **Create a new release**
2. **Choose a tag** → gõ `v1.0.0` → **Create new tag: v1.0.0 on publish**
3. **Release title**: `ProbeGuard 1.0.0`
4. Kéo file `C:\Users\ADMIN\ProbeGuard\target\ProbeGuard-1.0.0.jar` vào ô
   **Attach binaries by dropping them here**
5. Bấm **Publish release**

Xong. Link tải trực tiếp, luôn trỏ vào bản mới nhất:

```
https://github.com/<TÊN-BẠN>/ProbeGuard/releases/latest/download/ProbeGuard-1.0.0.jar
```

Link trang release (để người khác xem):

```
https://github.com/<TÊN-BẠN>/ProbeGuard/releases
```

---

## Cách B — dùng Git (khuyến nghị nếu sẽ sửa code tiếp)

### B1. Cài Git

Tải https://git-scm.com/download/win → cài mặc định → mở PowerShell mới.

```powershell
git --version
```

### B2. Khai báo danh tính (1 lần)

```powershell
git config --global user.name "Tên bạn"
git config --global user.email "email@example.com"
```

### B3. Tạo repo rỗng trên GitHub

https://github.com/new → tên `ProbeGuard` → **không** thêm README/gitignore/license → Create.

### B4. Đẩy code lên

```powershell
cd C:\Users\ADMIN\ProbeGuard
git init
git add .
git commit -m "ProbeGuard 1.0.0 - client integrity probes for Paper 26.3"
git branch -M main
git remote add origin https://github.com/<TÊN-BẠN>/ProbeGuard.git
git push -u origin main
```

GitHub sẽ hỏi đăng nhập: dùng **Personal Access Token** (Settings → Developer settings →
Personal access tokens → Tokens (classic) → Generate new token, tick `repo`) làm mật khẩu.
Hoặc cài GitHub CLI rồi `gh auth login`.

### B5. Release bằng Git tag

```powershell
git tag v1.0.0
git push origin v1.0.0
```

Nếu repo đã có file `.github/workflows/build.yml` (đã kèm sẵn trong project này), GitHub Actions sẽ:

1. Cài JDK 25 + Maven
2. Build `target/ProbeGuard-1.0.0.jar`
3. Tự tạo Release `v1.0.0` và đính kèm jar

Xem tiến trình ở tab **Actions**. Sau ~2 phút, vào **Releases** là thấy jar.

Nếu không muốn dùng Actions: vẫn làm như **A3** (upload jar thủ công vào release).

---

## Những gì **không** nên commit

`.gitignore` trong project đã chặn:

```
target/
build.log
.idea/
*.iml
.vscode/
```

Nghĩa là mã nguồn lên Git, còn jar đi theo **Release** (đúng chuẩn). Nếu bạn vẫn muốn
commit jar vào repo cho tiện, bỏ dòng `target/` và thêm:

```powershell
git add -f target/ProbeGuard-1.0.0.jar
```

Nhưng khi đó mỗi lần build lại phải commit lại file 70 KB — không đáng.

---

## Checklist trước khi public

- [ ] `README.md` đã ghi rõ cần **Java 25** và **PacketEvents 2.14.0+**
- [ ] Có cảnh báo "đừng bật `kick-score` ngay" (đã có trong README)
- [ ] Chưa nhét token/webhook Discord thật vào `config.yml`
- [ ] Nếu muốn người khác dùng lại code: thêm file `LICENSE` (MIT/GPLv3)
