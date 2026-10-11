# Правила сжатия для release-сборки.
# Код приложения не использует рефлексию; библиотеки (Compose, ZXing, корутины) приносят свои правила.
# Номера строк оставляем, чтобы отчёты о падениях было можно прочитать.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
