# Части многофайловых классов Kotlin (StringsKt__IndentKt, StringsKt__StringBuilderKt…) наследуют
# друг друга с доступом «внутри пакета». R8 переносил одну часть в другой пакет, а её родителя
# оставлял — и релиз падал при запуске (IllegalAccessError). Переименовывать и ужимать их можно,
# переносить — нет
-keep,allowshrinking,allowoptimization,allowobfuscation class kotlin.**.*Kt__*
