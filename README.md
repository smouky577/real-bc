# Реален BC

Android приложение, което смята реалния балистичен коефициент (BC) на снаряд от измерени скорости. Въвеждат се началната скорост от хронограф (V0) и скоростта на една или повече дистанции. Моделът на съпротивление е същият като в Ballistic Scope (`ballisticscope-drag-v2`), затова полученият BC е числото, което този двигател трябва да ползва, за да възпроизведе мерените скорости.

Може да се зададе едно общо V0 или отделно V0 за всеки ред. Температурата и надморската височина влизат в плътността на въздуха. След смятането резултатът се записва в `Download/RealBc` като текст (`ballisticscope-load 1`) и като JSON.

От версия 1.2 моделът е точно този на Ballistic Scope (таблица G1, RK4). BC, сметнат с версия 1.0 или 1.1, не пасва на Ballistic Scope. Сметнете го отново.

## Инсталация

Готовият файл е в последния Release: `RealBc.apk`. Копира се на телефона и се инсталира. Нужен е Android 8.0 (API 26) или по-нов.

## Windows

Няма `.bat` файл, който да стартира програмата. Приложението е само за Android.

За сглобяване от командния ред в папката на проекта се пуска `gradlew.bat assembleRelease`. Това прави release APK, не стартира приложение на компютъра. Нужни са Android SDK в `local.properties` (`sdk.dir`) и `keystore.properties` за подпис. Тези два файла не са в хранилището.

---

# Real BC

Android app that fits a projectile's real ballistic coefficient (BC) from measured speeds. Enter the muzzle velocity from a chronograph (V0) and the speed at one or more distances. The drag model is the same one Ballistic Scope uses (`ballisticscope-drag-v2`), so the fitted BC is the number that engine must use to reproduce the measured speeds.

One shared V0 can be used, or a separate V0 on each row. Temperature and altitude affect the air density. After the fit, the result is saved in `Download/RealBc` as text (`ballisticscope-load 1`) and as JSON.

From version 1.2 the model is exactly the Ballistic Scope one (G1 table, RK4). A BC fitted with version 1.0 or 1.1 does not match Ballistic Scope. Fit it again.

## Install

The ready file is in the latest Release: `RealBc.apk`. Copy it to the phone and install it. Android 8.0 (API 26) or newer is required.

## Windows

There is no `.bat` file that launches the program. The app is Android only.

To build from the command line, run `gradlew.bat assembleRelease` in the project folder. That produces a release APK; it does not start an app on the computer. An Android SDK path in `local.properties` (`sdk.dir`) and a `keystore.properties` file for signing are required. Those two files are not in the repository.
