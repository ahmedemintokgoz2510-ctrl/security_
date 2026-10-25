# Secret Vault (decoy home screen + PIN-gated second screen)

## Derleme
1. Android Studio (Ladybug veya üstü) ile proje klasörünü aç; Gradle sync otomatik
   olarak Gradle 8.9 / AGP 8.7.2 / Kotlin 2.0.21'i indirecektir.
2. `minSdk = 24`, `compileSdk = targetSdk = 35`.
3. Gradle wrapper jar'ı bu ortamda internet erişimi olmadığı için üretilemedi;
   Android Studio ilk açılışta kendisi indirir. Komut satırından derlemek
   istersen önce `gradle wrapper --gradle-version 8.9` çalıştırman yeterli.

## Akış
- `FakeHomeScreen`: "Sistem Veri Aktarımı" görünümlü, butonları işlevsiz sahte ekran.
- Ortadaki ⚙ ikonuna 7 saniye kesintisiz basılı tutulursa `PinEntryScreen` açılır
  (`LaunchedEffect` + `delay(7000)`; parmak erken çekilirse sayaç iptal olur).
- Doğru 4 haneli PIN (`CORRECT_PIN`, varsayılan `2580`, kodda değiştirilebilir)
  girilince `SecretVaultScreen` açılır.
- `SecretVaultScreen`: cihazda kurulu, başlatılabilir uygulamaları listeler ve
  standart bir `Intent` ile açar.

## Bilinçli olarak eklenmeyenler
Önceki konuşmada üzerinde anlaştığımız gibi şunlar bu projeye **dahil değildir**:
- VPN/Proxy kimlik bilgisi paneli
- Sahte cihaz markası/modeli/saat dilimi/MCC-MNC (cihaz kimliği sahteciliği)
- Uygulamaları "yalıtılmış" bir context/process içinde başlatma

`SecretVaultScreen` işlevsel olarak normal bir uygulama kısayolundan farksızdır:
sadece `PackageManager.getLaunchIntentForPackage` ile standart başlatma yapar.
