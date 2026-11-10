# Exam Browser Sekolah

Aplikasi Android untuk membuka CBT sekolah dengan aman: scan barcode, layar penuh, layar dikunci, anti-screenshot.

## Fitur
- **Cek Perangkat** sebelum ujian: internet, Opsi Pengembang, aksesibilitas, root, baterai, Jangan Ganggu, memori, kamera — dengan tombol "Perbaiki"
- Scan barcode (QR), ketik/tempel link, riwayat link, atau bagikan link dari WhatsApp ke aplikasi
- Selama ujian ada peringatan jika internet terputus atau baterai hampir habis
- Layar penuh dan layar disematkan (siswa tidak bisa pindah aplikasi)
- Screenshot dan rekam layar diblokir
- Tombol kembali dimatikan
- Jika siswa keluar aplikasi, muncul peringatan dan jumlahnya tampil merah di atas layar
- Keluar ujian memakai password pengawas
- Setelah keluar, data login dihapus (aman untuk HP bergantian)
- Izin hanya: Internet dan Kamera

## Cara membuat APK (tanpa Android Studio)
1. Buat akun gratis di github.com, lalu login (sebaiknya lewat laptop).
2. Klik **+** (kanan atas) → **New repository**. Isi nama `exam-browser`, pilih **Public**, klik **Create repository**.
3. Klik **uploading an existing file**. Ekstrak file ZIP dulu, lalu seret **semua isi** folder `ExamBrowser` (termasuk folder `.github` dan `app`) ke halaman itu. Klik **Commit changes**.
4. Buka tab **Actions**. Tunggu proses "Rakit APK" sampai centang hijau (±5–10 menit).
5. Klik proses itu → di bagian **Artifacts** klik **ExamBrowser-APK**. File ZIP terunduh; ekstrak untuk mendapat `ExamBrowser.apk`.

Jika folder `.github` tidak ikut terunggah: klik **Add file → Create new file**, ketik nama `.github/workflows/build.yml`, tempel isi file `build.yml`, lalu **Commit changes**.

## Mengubah pengaturan
Buka file `app/src/main/res/values/config.xml` di GitHub → klik ikon pensil → ubah nama sekolah, password pengawas, dll. → **Commit changes**. APK baru otomatis dirakit lagi di tab Actions.

Catatan: jika memasang versi baru, hapus dulu aplikasi lama di HP.

## Cara pakai di HP siswa
1. Pasang `ExamBrowser.apk` (izinkan "Instal dari sumber tidak dikenal").
2. Buka aplikasi → **Scan Barcode Ujian** → arahkan ke barcode CBT.
3. Saat muncul "Sematkan aplikasi?", tekan **Mulai/OK**.
4. Selesai ujian, tekan **Keluar** dan masukkan password pengawas.

Link CBT Apps Script harus di-deploy dengan akses **Siapa saja (Anyone)**.
