# دوب‌پلیر (DubPlayer)

اپ اندروید شبیه YouTube با پخش‌کنندهٔ قوی (ExoPlayer) و پشتیبانی کامل از **دوبلهٔ فارسی** و **زیرنویس فارسی** که ابزار [yt-dubber](../yt-dubber) تولیدشان می‌کند.

## امکانات
- 🎬 پخش استریم و فایل محلی (ExoPlayer / Media3)
- 🔊 سوییچ بین صدای اصلی و **دوبلهٔ فارسی** (chip: «دوبلهٔ فارسی / صدای اصلی»)
- 💬 زیرنویس فارسی `.srt` با انتخاب Track
- ⚡ انتخاب کیفیت (Track dialog)، سرعت پخش (۰.۵ تا ۲×)
- 📱 تصویر در تصویر (PiP) و پخش پس‌زمینه (MediaSessionService)
- 🔗 شیر کردن لینک یوتیوب از مرورگر → باز شدن مستقیم در اپ
- 🔍 جستجو با API عمومی Piped / Invidious (بدون نیاز به کلید)

## ساختار پوشهٔ ویدیوها
```
/sdcard/DubPlayer/
  <video-id>/
    dubbed.mp4          ← خروجی yt-dubber (دوبله)
    subtitles.fa.srt    ← زیرنویس فارسی
    source.mp4          ← ویدیوی اصلی (اختیاری)
```

## بیلد APK
GitHub Actions خودکار بیلد می‌کند (پوشهٔ `.github/workflows/build.yml`). فایل APK از
تب **Actions → Build APK → Artifacts** قابل دانلود است (هم release و هم debug).

## استفاده
1. فایل‌های `dubbed.mp4` و `subtitles.fa.srt` خروجی yt-dubber را در `/sdcard/DubPlayer/<id>/` بریزید.
2. اپ را باز کنید → تب کتابخانه → پخش.
3. در پلیر chip «دوبلهٔ فارسی» را بزنید تا صدا سوییچ شود.
