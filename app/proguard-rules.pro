# Reglas de R8 para OpenTV.
# Fase 1: no hacen falta reglas propias; las bibliotecas AndroidX incluyen las suyas.

# Shizuku: el servicio con permisos de shell se instancia por nombre desde otro proceso.
-keep class com.opentv.app.display.ShellUserService { *; }
-keep class com.opentv.app.display.IUserService { *; }
-keep class com.opentv.app.display.IUserService$Stub { *; }
-keep class rikka.shizuku.** { *; }
