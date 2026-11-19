<div align="center">

# 🎙️ Avina | آوینا

### صدای فارسی، از متن شما — حتی بدون اینترنت

**یک اپلیکیشن اندرویدی برای تبدیل متن فارسی به گفتار، با پردازش روی دستگاه**

[![Version](https://img.shields.io/badge/version-0.1.0-2563EB?style=for-the-badge)](https://github.com/dezhcode/tts-app/releases/tag/v0.1.0)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#-پیشنیازها)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#-فناوریها)
[![Build](https://img.shields.io/github/actions/workflow/status/dezhcode/tts-app/build-offline-apk.yml?branch=feature%2Fbundled-cilla-mini&style=for-the-badge&label=APK%20Build)](https://github.com/dezhcode/tts-app/actions/workflows/build-offline-apk.yml)

**[⬇️ دریافت APK](https://github.com/dezhcode/tts-app/releases/tag/v0.1.0)** · **[🛠️ وضعیت ساخت](https://github.com/dezhcode/tts-app/actions)** · **[🌿 شاخه توسعه](https://github.com/dezhcode/tts-app/tree/feature/bundled-cilla-mini)**

</div>

---

> [!IMPORTANT]
> **نسخه 0.1.0 یک نسخه آزمایشی است.** فایل APK فعلی با کلید **Debug** امضا می‌شود؛ برای انتشار نهایی در فروشگاه‌ها مناسب نیست. قابلیت‌ها و کیفیت صدا باید روی دستگاه واقعی آزمایش شوند.

## ✨ آوینا چیست؟

**Avina** یک پروژه متن‌باز اندرویدی با تمرکز بر تجربه ساده و فارسی‌محور تبدیل **متن به گفتار (Text-to-Speech)** است. در فرایند ساخت APK، وزن‌های مدل **Cilla Mini** و فایل‌های لازم برای اجرای **Phonon** در بسته برنامه قرار می‌گیرند تا پس از نصب صحیح، تولید گفتار به سرویس آنلاین وابسته نباشد.

<div align="center">

| 📴 پردازش آفلاین | 🗣️ رابط فارسی | 🎚️ کنترل تولید | 💾 خروجی WAV |
|:---:|:---:|:---:|:---:|
| مدل داخل APK | ورود و نرمال‌سازی متن | تنظیم پارامترهای گفتار | ذخیره و اشتراک صدا |

</div>

## 📱 تجربه کاربری

### 01 — بنویسید
متن فارسی را در کادر اصلی وارد کنید یا یکی از متن‌های نمونه مانند **خوشامد، شعر و اعداد** را انتخاب کنید.

### 02 — گوینده را انتخاب کنید
در رابط کاربری، گزینه‌های فشرده **زن، مرد و پریسا** دیده می‌شوند.

> [!NOTE]
> وجود این گزینه‌ها در رابط کاربری به‌معنای تأیید سه صدای مستقل نیست. مدل بسته‌بندی‌شده در فرایند ساخت، فایل صدای **parisa** را دریافت می‌کند؛ عملکرد واقعی و تفاوت خروجی گزینه‌های دیگر باید جداگانه بررسی شود. **کلون‌کردن صدا** به‌عنوان قابلیت تضمین‌شده این نسخه معرفی نمی‌شود.

### 03 — تنظیم و تولید کنید
گزینه‌های موجود در منطق برنامه شامل نرمال‌سازی متن فارسی، **Temperature**، تعداد گام‌های تولید، مکث بین جمله‌ها و حذف سکوت هستند.

### 04 — گوش کنید و نگه دارید
خروجی تولیدشده را پخش کنید، فایل صوتی **WAV** را ذخیره کنید یا از قابلیت اشتراک‌گذاری استفاده کنید. برنامه همچنین ساختار نگهداری تاریخچه کلیپ‌ها را دارد.

## 🎨 زبان طراحی

| مؤلفه | رویکرد |
|---|---|
| سبک بصری | مینیمال، روشن و خوانا |
| رنگ‌ها | زمینه روشن، کارت‌های سفید و تأکید آبی |
| کارت‌ها | گوشه‌های گرد و فاصله‌گذاری منظم |
| انتخاب گوینده | چیپ‌های جمع‌وجور به‌جای پنل بزرگ |
| ناوبری | نوار شناور و سازگار با رابط لمسی |
| محتوا | متن‌های فارسی، چیدمان ساده و کنترل‌های واضح |

> **هدف UX:** مسیر «نوشتن ← انتخاب صدا ← تولید ← شنیدن/ذخیره» کوتاه و قابل‌فهم بماند.

## 🧠 موتور گفتار

| بخش | جزئیات |
|---|---|
| مدل | [mehdi-hf/cilla-mini](https://huggingface.co/mehdi-hf/cilla-mini) |
| پردازش | روی دستگاه با Phonon و WebAssembly |
| زبان هدف | فارسی |
| مدل در زمان ساخت | دریافت با توکن مجاز Hugging Face |
| نمونه فایل‌های مدل | `config.json`، `tokenizer.json`، `model.safetensors` و `voices/parisa.safetensors` |
| نرخ نمونه‌برداری هدف | 24 kHz |

> [!WARNING]
> مدل Cilla Mini دارای شرایط دسترسی و مجوز مستقل است. این مخزن به‌معنای اعطای حق بازتوزیع تجاری مدل نیست.

## 🧰 فناوری‌ها

- **Kotlin** و **Jetpack Compose** برای اپلیکیشن و رابط کاربری
- **Android Gradle Plugin / Gradle** برای ساخت
- **Phonon TTS / WebAssembly** برای اجرای موتور صوتی
- **Coroutines و StateFlow** برای مدیریت وضعیت
- **GitHub Actions** برای ساخت خودکار APK
- **Hugging Face** به‌عنوان منبع مدل با دسترسی کنترل‌شده

## 📂 ساختار پروژه

```text
app/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/example/
    │   ├── MainActivity.kt
    │   ├── ui/
    │   │   ├── MainViewModel.kt
    │   │   └── screens/
    │   │       ├── StudioScreen.kt
    │   │       └── ModelSetupScreen.kt
    │   ├── data/
    │   └── tts/
    └── assets/                  # مدل و runtime در CI تکمیل می‌شوند

.github/
├── scripts/bundle_cilla.py
└── workflows/build-offline-apk.yml
```

## 🚀 دریافت و نصب

1. وارد صفحه **[Avina 0.1.0 در Releases](https://github.com/dezhcode/tts-app/releases/tag/v0.1.0)** شوید.
2. اگر Release با موفقیت منتشر شده باشد، فایل **`Avina-0.1.0.apk`** را از بخش **Assets** دانلود کنید؛ این لینک، دانلود مستقیم APK است نه ZIP.
3. فایل را روی گوشی اندرویدی باز کنید و در صورت نیاز مجوز نصب از این منبع را بدهید.
4. برنامه را اجرا کنید و آماده‌بودن موتور گفتار را بررسی کنید.

**حداقل نسخه اندروید:** API 24 / Android 7.0.

> [!CAUTION]
> چون کلید Debug در اجرای CI مجدداً ساخته می‌شود، ممکن است APK نسخه جدید روی نسخه قبلی نصب نشود. در این حالت حذف برنامه قبلی ممکن است لازم شود و داده‌های محلی آن از بین بروند. برای انتشار پایدار باید از کلید Release ثابت استفاده شود.

## 🏗️ ساخت APK از GitHub Actions

**شاخه فعال توسعه:** `feature/bundled-cilla-mini`

1. برای مدل [Cilla Mini](https://huggingface.co/mehdi-hf/cilla-mini) دسترسی لازم را دریافت کنید.
2. در **Settings → Secrets and variables → Actions**، یک Secret با نام **`HF_TOKEN`** و توکن دارای دسترسی مجاز به مدل ثبت کنید.
3. از صفحه **[GitHub Actions](https://github.com/dezhcode/tts-app/actions/workflows/build-offline-apk.yml)**، workflow با نام **Build APK with bundled Cilla Mini** را اجرا کنید یا به شاخه توسعه Push کنید.
4. workflow مدل و runtime را در محیط CI دریافت می‌کند، کلید Debug موقت می‌سازد، `assembleDebug` را اجرا می‌کند و امضای APK را بررسی می‌کند.
5. در صورت موفقیت همه مراحل، فایل **`Avina-0.1.0.apk`** به Release با عنوان **Avina 0.1.0** و تگ **`v0.1.0`** اضافه می‌شود.

> [!TIP]
> دانلود از بخش **Actions → Artifacts** به‌صورت ZIP است؛ برای دریافت مستقیم فایل **APK** از بخش **Releases → Assets** استفاده کنید.

### ساخت محلی

برای ساخت محلی به محیط Android/Gradle سازگار، فایل‌های مدل و Phonon و پیکربندی امضای Debug نیاز دارید. صرف اجرای Gradle بدون آماده‌سازی دارایی‌های مدل، لزوماً نسخه آفلاین قابل‌استفاده تولید نمی‌کند.

## 🔐 امنیت و حریم خصوصی

- تبدیل گفتار با هدف **پردازش محلی** طراحی شده است؛ پس از بسته‌بندی صحیح مدل، نیازی به ارسال متن به سرویس TTS آنلاین نیست.
- **`HF_TOKEN`** فقط باید در GitHub Secrets ذخیره شود، نه در سورس یا README.
- مجوزهای `INTERNET` و `RECORD_AUDIO` در Manifest تعریف شده‌اند؛ وجود این مجوزها را نباید با ادعای «برنامه هیچ دسترسی شبکه‌ای ندارد» اشتباه گرفت.
- نسخه فعلی **Debug-signed** است و نباید با انتشار Production-signed اشتباه گرفته شود.

## 🗺️ مسیر توسعه

- [x] رابط اصلی فارسی و طراحی روشن
- [x] ورود متن، متن‌های نمونه و کنترل‌های تولید
- [x] فرایند بسته‌بندی مدل و Phonon در GitHub Actions
- [x] تنظیم انتشار مستقیم APK در Releases
- [ ] راستی‌آزمایی صداهای مستقل زن و مرد روی دستگاه واقعی
- [ ] تست جامع کیفیت و پایداری تولید گفتار
- [ ] امضای ثابت Release برای به‌روزرسانی بدون حذف برنامه
- [ ] آماده‌سازی برای انتشار عمومی و بررسی مجوزهای وابستگی‌ها

## 📜 مجوز و اعتبار

این پروژه از مدل **[Cilla Mini](https://huggingface.co/mehdi-hf/cilla-mini)** استفاده می‌کند. **مجوز مدل CC BY-NC 4.0** است؛ استفاده تجاری از آن بدون مجوز مناسب مجاز فرض نمی‌شود. مجوز کتابخانه‌ها، دارایی‌های تصویری و سایر اجزا را نیز پیش از بازتوزیع بررسی کنید. این README به‌تنهایی مجوز جدیدی برای سورس یا مدل صادر نمی‌کند.

---

<div align="center">

### 💙 Avina — آوینا
**تبدیل متن فارسی به صدا، با تمرکز بر سادگی و تجربه آفلاین**

[دریافت نسخه](https://github.com/dezhcode/tts-app/releases) · [مشاهده سورس](https://github.com/dezhcode/tts-app/tree/feature/bundled-cilla-mini) · [گزارش مشکل](https://github.com/dezhcode/tts-app/issues)

<sub>Made for Persian text-to-speech • Version 0.1.0</sub>

</div>
