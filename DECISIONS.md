# UltimateTerminal — Decisiones

Registro de las decisiones tomadas durante el desarrollo que no estaban fijadas en `SPEC.md`, o que
se desvían de él. Una entrada por decisión, la más reciente al final de su sección. Formato: fecha,
decisión, motivo, alternativas, impacto. Si una decisión contradice a `SPEC.md`, se anota aquí y el
usuario decide si se actualiza la spec.

## Pendiente de validar en hardware

El usuario ha prohibido probar en el Pixel 8 durante este desarrollo, así que **nada de lo que
requiere ejecutar en un dispositivo se ha verificado**. Hasta que se pruebe, no debe darse por
funcionando:

- Que `libproot.so` arranque y ejecute `/bin/sh` de un rootfs Alpine (T02, ver D-005).
- Que el loader (`libproot-loader.so`, vía `PROOT_LOADER`) se inyecte correctamente en arm64,
  armeabi-v7a y x86_64.
- Comportamiento de seccomp, `ptrace`, `/proc` y `--link2symlink` en Android moderno (API 26–37).
- Ejecutar binarios desde `nativeLibraryDir` con `targetSdk` 28 en Android 15+/16.

Lo que sí está verificado sin dispositivo: compila para las tres ABIs, los ejecutables se empaquetan
en el APK con el tipo ELF y los puntos de entrada esperados, `./gradlew check` y el CI en verde.

## Decisiones

### D-001 · 2026-10-04 · proot: usar el fork de Termux como submódulo git
- **Decisión:** las fuentes de proot son el fork `termux/proot` en el tag `v5.1.107.96`, como
  submódulo en `third_party/proot`.
- **Motivo:** es proot (GPL-2.0-or-later) con los parches para Android que Termux mantiene y usa en
  producción; el submódulo fija el commit exacto y F-Droid lo soporta. Es código, no binarios
  precompilados ni el prefijo `com.termux`, así que cumple SPEC §2 ("compilado desde fuente").
- **Alternativas:** `proot-me/proot` upstream (menos parches Android); copiar las fuentes al repo
  (ensucia el historial y dificulta actualizar).
- **Impacto:** el CI y el build necesitan `submodules: true`. Se acredita en `THIRD_PARTY_NOTICES.md`.

### D-002 · 2026-10-04 · talloc: vendorizado con un `replace.h` propio
- **Decisión:** `talloc.c` y `talloc.h` 2.5.0 sin modificar en `third_party/talloc`, con un
  `replace.h` mínimo escrito a mano (también `MIN`/`MAX` y `memset_explicit`, que bionic no tiene
  en API 26).
- **Motivo:** el `replace.h` de Samba lo genera waf (Python, configure con comprobaciones); traerlo
  aumenta mucho el build. talloc solo necesita unas pocas definiciones.
- **Alternativas:** compilar talloc con su waf (frágil en el cross-compile); usar el `libtalloc` de
  Termux (binario, atado al prefijo).
- **Impacto:** LGPL-3.0-or-later enlazado estáticamente en un proyecto GPL-3.0-or-later: compatible.
  Hash del tarball registrado en `third_party/talloc/README.md`. No se ha verificado la firma GPG del
  tarball (solo el SHA-256 al descargarlo).

### D-003 · 2026-10-04 · ABIs: arm64-v8a, armeabi-v7a y x86_64
- **Decisión:** se compilan esas tres. Sin x86 (32 bits).
- **Motivo:** arm64 y armeabi-v7a cubren móviles/tablets; x86_64 sirve para emuladores y pruebas.
- **Impacto:** el loader de 32 bits (`loader-m32`, que proot usa en arm64/x86_64 para ejecutar
  binarios de 32 bits dentro de una distro de 64) **no se construye**. Los programas de 32 bits en
  una distro de 64 bits no funcionarán. Los rootfs de 64 bits (Debian, Ubuntu, Alpine arm64) no lo
  necesitan.

### D-004 · 2026-10-04 · proot y loader empaquetados como `lib*.so`; NDK fijado
- **Decisión:** `add_executable` con `OUTPUT_NAME`/`PREFIX lib`/`SUFFIX .so` (`libproot.so`,
  `libproot-loader.so`), `useLegacyPackaging = true` y `android:extractNativeLibs="true"` para que
  queden extraídos en `nativeLibraryDir`. `ndkVersion` fijado a `28.2.13676358`.
- **Motivo:** Android solo permite ejecutar desde `nativeLibraryDir` a una app con `targetSdk` 28.
  Empíricamente AGP empaqueta esos ejecutables sin más (se comprobó en el APK). El NDK se fija para
  que el build nativo sea reproducible en F-Droid.
- **Alternativas:** descargar/extraer los binarios en runtime al almacenamiento privado (no
  permitido con targetSdk ≥ 29 y va contra "sin binarios de terceros").
- **Impacto:** el APK es más grande (~0,5 MB por ABI). El loader se localiza con la variable
  `PROOT_LOADER`, no con una ruta compilada. El CI comprueba que las 6 librerías están en el APK.

### D-005 · 2026-10-04 · T02 sin prueba en dispositivo (desviación del plan)
- **Decisión:** T02 se marca como hecha con compilación y empaquetado verificados, pero **sin
  ejecutar proot en dispositivo**. La prueba con Alpine queda pendiente.
- **Motivo:** el usuario prohibió probar en el Pixel 8. Tampoco se ejecuta en el host ni en un
  emulador sin su autorización expresa.
- **Impacto:** el riesgo nº 1 de `SPEC.md` (proot en Android moderno) sigue **abierto**. No debe
  construirse en T07/T08 asumiendo que funciona sin antes validar.

### D-006 · 2026-10-04 · `libandroid-shmem` no se enlaza
- **Decisión:** no se define `WITH_LIBANDROID_SHMEM`.
- **Motivo:** es una biblioteca extra de Termux (otro componente y licencia) que solo emula
  memoria compartida SysV; no es necesaria para un shell, apt/apk, ssh, nmap ni python.
- **Impacto:** programas que usen memoria compartida SysV (p. ej. algunos `postgres`) pueden fallar
  en la distro. Se revisa si hace falta más adelante.

### D-007 · 2026-10-04 · Lanzamiento de proot: flags y entorno por defecto
- **Decisión:** `ProotCommandBuilder` usa `--link2symlink`, `--kill-on-exit`, `-0` (root simulado),
  binds de `/dev`, `/proc` y `/sys`, `PROOT_TMP_DIR` en un directorio privado y un entorno de guest
  limpio (`env -i` con `HOME`, `TERM`, `LANG`, `PATH`). `PROOT_NO_SECCOMP=1` es una opción, apagada
  por defecto.
- **Motivo:** `link2symlink` porque el almacenamiento de la app no admite enlaces duros;
  `PROOT_TMP_DIR` porque `/tmp` no existe en Android; el entorno limpio porque el del proceso es el
  de Android; seccomp se deja como interruptor por si ciertos kernels lo rompen.
- **Alternativas:** `--sysvipc`, `-L`, `--root-id` solo con usuario normal: se decide al probar.
- **Impacto:** los flags son hipótesis razonables **sin validar en dispositivo** (ver la sección de
  pendientes); el constructor está cubierto con tests de host.

### D-008 · 2026-10-04 · Cobertura: el paquete `data` ya cuenta
- **Decisión:** con `data.proot` el umbral de Kover (≥ 85 % en `domain` y `data`) empieza a medirse
  de verdad. Los paquetes críticos al 100 % (`data.rootfs.verify`, `data.backup`) siguen vacíos
  hasta T06/T15.
- **Impacto:** cada código nuevo en `data` necesita tests reales.

### D-009 · 2026-10-04 · El loader se enlaza con `--no-gc-sections`
- **Decisión:** `libproot-loader.so` se enlaza con `--no-gc-sections`.
- **Motivo:** en las builds de release CMake añade `--gc-sections`, que eliminaba `pokedata_workaround`
  (arm64): nadie lo referencia, pero proot localiza su offset en runtime. Se detectó porque
  `loader-info` fallaba en release; sin esa comprobación habría sido un fallo silencioso.
- **Impacto:** el offset generado es idéntico en debug y release (1016 en arm64).
