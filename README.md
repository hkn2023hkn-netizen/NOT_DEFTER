# Not Defteri

Android için not ve görev uygulaması.

- **Notlar** ve **Görevler** bölümleri; notlar sarı not kağıdı görünümünde
- Her iki bölümde kamerayla fotoğraf çekme ve galeriden fotoğraf ekleme
- **Takvim**: aylık görünüm, hatırlatıcılı günler işaretli; seçilen güne görev ya da not eklenebilir
- Alarmlar: zamanından önce (5 dk – 1 gün) ve tam zamanında; kilit ekranında açılır, "Kapat" denene kadar çalar
- Telefon yeniden başlatılınca alarmlar yeniden kurulur
- Simge: pusula logosu

Gereksinim: Android 8.0 ve üzeri.

## Derleme

```
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub'a gönderildiğinde `.github/workflows/apk.yml` APK'yı otomatik derler ve **Releases** sayfasına koyar.
