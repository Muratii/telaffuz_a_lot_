# Telaffuz Koçu (Kotlin) - v2, 1000+ kelime, sözlükten doğrulanmış IPA

v2'nin tüm özellikleri aynı. Kelime listesi 1090 kelime (A1 255, A2 468, B1 367), hepsinde örnek cümle var.

## Veri nereden geliyor
- **IPA, vurgu, hece**: CMU Pronouncing Dictionary'den (Carnegie Mellon, Amerikan İngilizcesi, serbest kullanım)
  derleme sırasında GitHub Actions tarafından üretilir. `tools/build_words.py` bunu yapar.
- **Aynı okunan kelimeler** (right / write gibi) ve **ses karışıklığı mesajları** (think / sink gibi) de aynı sözlükten çıkarılır.
- **Türkçe anlam ve örnek cümleler** `tools/words_source.tsv` dosyasındadır, sözlükten gelmez, Claude tarafından yazılmıştır.
- **Türkçe okunuş** IPA'dan otomatik üretilen yaklaşık bir yardımdır. Vurgulu hece BÜYÜK harfle yazılır.
- Sözlükte bulunmayan bir kelime listeden çıkarılır. Rapor için Actions çalışmasının altındaki `words-report` dosyasına bak
  (kaç kelime üretildi, hangileri çıkarıldı, elle yazılan IPA ile sözlük arasında büyük fark olanlar).

words.json repoda yoktur, her derlemede yeniden üretilir. Sözlük indirilemezse derleme hata verir ve eski/yanlış veriyle APK çıkarmaz.

## Klasör yapısı
```
.github/workflows/
  build.yml
  settings.gradle.kts, build.gradle.kts, gradle.properties
  tools/build_words.py, tools/words_source.tsv
  app/ ...
```

APK: Actions > Build APK > Run workflow.
