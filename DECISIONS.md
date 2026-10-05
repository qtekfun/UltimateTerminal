# UltimateTerminal — Decisiones

Registro de las decisiones tomadas durante el desarrollo que no estaban fijadas en `SPEC.md`, o que
se desvían de él. Una entrada por decisión, la más reciente al final de su sección. Formato: fecha,
decisión, motivo, alternativas, impacto. Si una decisión contradice a `SPEC.md`, se anota aquí y el
usuario decide si se actualiza la spec.

## Pendiente de validar en hardware

Los agentes que desarrollan **no** prueban en dispositivos: lo hace el orquestador, y solo en el
Pixel 8 (el usuario lo autorizó el 2026-10-04). Lo verificado allí consta en la sección "Primeras
pruebas en hardware"; **lo que no aparece ahí sigue sin verificar** y no debe darse por
funcionando. Pendiente, entre otras cosas:

- Que `libproot.so` arranque y ejecute `/bin/sh` de un rootfs Alpine (T02, ver D-005).
- Que el loader (`libproot-loader.so`, vía `PROOT_LOADER`) se inyecte correctamente en arm64,
  armeabi-v7a y x86_64.
- Comportamiento de seccomp, `ptrace`, `/proc` y `--link2symlink` en Android moderno (API 26–37).
- Ejecutar binarios desde `nativeLibraryDir` con `targetSdk` 28 en Android 15+/16.
- T07, instalación de distros: descargar, descomprimir y mover un rootfs real en el almacenamiento privado,
  el cálculo de espacio libre y la pantalla de gestión (ver "T07: lo que NO se ha validado").
- Capa de datos (T05): el cableado de Hilt, `AndroidSQLiteDriver` y `java.nio` (enlaces simbólicos,
  permisos, `ATOMIC_MOVE`) en el almacenamiento privado de la app (ver D-T05-9).
- T03, todo lo que necesita un PTY real o una pantalla: que `libtermux.so` cargue y el `fork/exec` de
  `/system/bin/sh` funcione; que el dibujo (colores, cursor, texto ancho, fuente) sea correcto y fluido;
  el teclado en pantalla (IME), el teclado físico, los gestos (scroll y selección) y que `stty size`
  coincida con lo visible tras redimensionar (ver D-T03-4 a D-T03-6 y T04).
- T03, release con R8: que los métodos nativos de `JNI` sobrevivan a la minificación (las reglas por
  defecto de Android conservan los nombres de los `native`, pero no se ha comprobado en un APK real).
- T08b, arranque de una pestaña en una distro: que `libproot.so` se ejecute desde `nativeLibraryDir` con
  `targetSdk` 28, que `uname -a` y `apk update` funcionen dentro de Alpine, que haya red (DNS), que `su -l <usuario>`
  funcione, que los montajes de T13 se vean y que el modo de compatibilidad (sin seccomp) arregle lo que falle
  (ver D-T08b-8 con el criterio de aceptación de la primera prueba en la tablet).
- T14, SSH: el Keystore real, `ssh` dentro de proot con la clave temporal, el selector de archivos y la
  limpieza del fichero de la clave (ver "T14: lo que NO se ha validado").
- T08, servicio en primer plano: que Android lo arranque con el tipo `specialUse` en 12–16, que la
  notificación aparezca y sus acciones (nueva sesión, salir) funcionen, que el shell sobreviva a
  cerrar la actividad y a apagar la pantalla, y que la UI se reconecte a la sesión viva. También el
  wake lock real y el efecto del *phantom process killer* (ver D-T08-5).
- T09, pestañas: los gestos de la barra (tocar, doble toque, pulsación larga + arrastre para
  reordenar, scroll de la propia barra) pueden competir entre sí; los menús desplegables, los
  diálogos, la barra lateral en pantallas anchas (y con idioma de derecha a izquierda), los 48 dp
  táctiles, TalkBack, y que al cambiar de pestaña el pty de la que pasa a primer plano reciba el
  tamaño correcto (ver D-T09-8).

Lo que sí está verificado sin dispositivo: compila para las tres ABIs, los ejecutables se empaquetan
en el APK con el tipo ELF y los puntos de entrada esperados, `./gradlew check` y el CI en verde.
- T13, acceso a `/sdcard`: que el permiso de almacenamiento se conceda con `targetSdk` 28 en Android 11, 12, 13 y posteriores y
  que con él los procesos nativos (proot) puedan leer y escribir en `/storage/emulated/0`, incluidos Descargas y Documentos;
  que `cp /var/log/syslog ~/storage/downloads/` aparezca en la carpeta Descargas (ver D-T13-3 y "T13: lo que NO se ha validado").
- T10, paneles divididos: ver D-T10-9. Los separadores arrastrables, el foco por toque, el menú del
  panel, el tamaño de cada pty tras dividir, cerrar o hacer zoom, y los gestos de cada panel se han
  probado solo con tests de host.

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

## T03 — Terminal (emulador y vista)

### D-T03-1 · 2026-10-04 · `terminal-emulator` vendorizado como módulo, no por JitPack
- **Decisión:** los fuentes de `terminal-emulator` de Termux (tag `v0.118.3`) van **sin modificar** en el
  módulo Gradle `:terminal-emulator` (Java, JNI `termux.c` y sus tests). Solo son nuestros su
  `build.gradle.kts` y un `CMakeLists.txt` que sustituye al `Android.mk`.
- **Motivo:** F-Droid compila desde fuente y no admite un binario ya compilado por terceros (el artefacto
  de JitPack); además el JNI hay que compilarlo con el NDK de todos modos, y así se fija el commit exacto
  y el build es reproducible.
- **Alternativas:** dependencia `com.termux.termux-app:terminal-emulator` de JitPack (comodidad, pero un
  binario ajeno y un repositorio más); escribir un emulador propio (SPEC §8: meses de trabajo y riesgo).
- **Impacto:** 3 ABIs (arm64-v8a, armeabi-v7a, x86_64) y el mismo NDK que `:app`. Actualizar Termux es
  copiar de nuevo desde otro tag. Los 145 tests de upstream pasan en la JVM del host
  (`:terminal-emulator:testDebugUnitTest`) con JUnit 4 (solo test).

### D-T03-2 · 2026-10-04 · Licencia del emulador: punto abierto que hay que confirmar
- **Qué se encontró:** ningún fichero de `terminal-emulator/` lleva cabecera de licencia y el directorio
  no tiene `LICENSE`. La única declaración es el `LICENSE.md` raíz de termux-app: el repositorio es
  GPL-3.0-only **salvo** el código derivado de Android Terminal Emulator (Jack Palevich), en las
  librerías `terminal-view` y `terminal-emulator`, que es Apache-2.0. No lo dice fichero a fichero y la
  librería ha crecido desde entonces (p. ej. soporte sixel y bitmap).
- **Decisión:** se usa bajo esa declaración, sin modificar, con el texto Apache-2.0 en
  `terminal-emulator/LICENSE`, el crédito a Termux y a Jack Palevich, y el aviso MIT de
  `jquast/wcwidth` (del que deriva `WcWidth.java`) en `terminal-emulator/NOTICE-wcwidth.txt`. No se
  copia nada más de termux-app.
- **Riesgo:** si alguna parte fuera en realidad GPL-3.0-only, seguiría siendo compatible con nuestra
  GPL-3.0-or-later, pero **no** con la parte "or-later" ni con la etiqueta Apache-2.0. Es una
  incertidumbre de upstream, no algo que podamos resolver desde aquí.
- **Acción propuesta (la decide el usuario):** pedir a los mantenedores de Termux que confirmen la
  licencia de la librería (o que añadan cabeceras) antes de publicar en F-Droid; si no la confirman,
  sustituir el emulador por uno propio. Esto no es asesoría legal.

### D-T03-3 · 2026-10-04 · Vista propia en Compose; `terminal-view` no se usa
- **Decisión:** se escribió desde cero un `TerminalPainter` (Android `Canvas` dentro de un `Canvas` de
  Compose) que solo lee el emulador. No se copió código de `TerminalRenderer` ni de `terminal-view`;
  esa clase se leyó para entender cómo se interpretan los estilos (color indexado y de 24 bits, negrita
  con colores brillantes, atenuado a 2/3, inverso).
- **Motivo:** SPEC §2 pide una vista propia en Compose y evita depender de la parte con licencia dudosa
  (D-T03-2). Las reglas de color se aislaron en `domain/terminal/CellStyles` para probarlas en host.
- **Impacto:** el pintado agrupa celdas ASCII del mismo estilo en una sola llamada y dibuja aparte el
  resto (CJK, emoji, combinados), encogiéndolos a sus celdas si no miden lo esperado. Sin parpadeo
  (`blink`), sin sixel ni imágenes, sin subrayados especiales: fuera del prototipo.

### D-T03-4 · 2026-10-04 · Teclado mediante una `View` invisible, no el protocolo de texto de Compose
- **Decisión:** el teclado en pantalla y el físico llegan a una `TerminalInputView` (1 dp, enfocable) con
  `onCreateInputConnection`. Se pide entrada de contraseña visible sin sugerencias para que el teclado
  envíe los caracteres según se escriben; lo que llegue como "composición" se retiene y se envía al
  confirmarla. Retroceso se envía como `DEL`, Enter como `\r`.
- **Motivo:** es el patrón que ya usan los terminales Android y no depende de las APIs experimentales de
  Compose (`PlatformTextInputModifierNode`). Sin dispositivo no se puede validar ninguna de las dos, así
  que se eligió la de menor riesgo.
- **Impacto:** **no validado**: queda por comprobar Gboard y otros teclados (composición, autocorrector,
  retroceso en campo vacío, teclas muertas), Ctrl/Alt pegajosos (T11) y el foco al volver a la app.

### D-T03-5 · 2026-10-04 · Gestos y selección: mínimos, sin validar
- **Decisión:** un toque pide el teclado; arrastre vertical recorre el scrollback; pulsación larga y
  arrastre seleccionan por flujo y aparece "Copiar". Todavía no hay botón de pegar (el emulador ya
  sabe pegar con bracketed paste; falta la acción en la interfaz).
- **Motivo:** cubrir lo imprescindible (RF-01, RF-08) para el prototipo. Los tres detectores de gestos
  van en el mismo nodo y **podrían competir** (arrastre vertical frente a pulsación larga): no se ha
  podido comprobar sin dispositivo.
- **Impacto:** pendientes para T11: asas de selección, selección por palabra, pegar, ratón (mouse
  reporting), rueda, zoom con pellizco y desplazamiento con inercia.

### D-T03-6 · 2026-10-04 · Rendimiento: no medido
- **Estado:** no se ha medido nada (no hay dispositivo). El diseño evita reservar memoria por fotograma
  (una sola `Run` y un `Paint` reutilizados) y agrupa texto en tramos; es una expectativa, no un dato.
- **Pendiente:** medir con salida masiva (`cat` de un fichero grande, `yes`) y desplazamiento, con el
  criterio de SPEC §6 (fluido sin bloquear la UI). Si no basta, se evalúa un caché de filas o pintar en
  una `SurfaceView`/capa propia.

### D-T03-7 · 2026-10-04 · Lint relajado solo en el módulo vendorizado
- **Decisión:** en `:terminal-emulator` Lint no trata los avisos como errores (`warningsAsErrors=false`,
  `abortOnError=false`, `checkReleaseBuilds=false`). En `:app` sigue estricto.
- **Motivo:** es código de terceros que no se modifica a propósito; sus hallazgos de Lint no son
  nuestros. Es una excepción a la regla de `CLAUDE.md` "no relajes Lint", acotada a ese módulo.
- **Alternativas:** parchear los fuentes upstream (rompería "sin modificar" y la actualización fácil).

### D-T03-8 · 2026-10-04 · Prototipo: shell de Android y lógica en `domain/terminal`
- **Decisión:** el prototipo lanza `/system/bin/sh` con un entorno mínimo (`TERM=xterm-256color`,
  `COLORTERM=truecolor`, `HOME` y `TMPDIR` privados, `PATH` del sistema y solo las variables de
  `ANDROID_*` del proceso). El proot se conectará en T07/T08. Todo lo que no toca Android (estilos de
  celda, tamaño de rejilla, scroll, selección, codificación de teclas, entorno) está en
  `domain/terminal` y se prueba en host; el pegamento Android (`TerminalSessionHost`, `TerminalPainter`,
  `TerminalInputView`, la pantalla) queda fuera de Kover, como la UI.
- **Impacto:** secuencias como las que imprimen `ls --color` y `top` (colores, posicionamiento del
  cursor, borrado, pantalla alterna, scrollback, ancho doble, redimensionado) se verifican contra el
  emulador real con tests de host (`EmulatorScreenTest`). Programas reales (vim, tmux, htop, `ls`,
  `top`) no se han ejecutado: no hay distro ni dispositivo.
- **Logs:** el cliente de la librería descarta sus mensajes de log: pueden contener texto de la
  terminal y SPEC §6 prohíbe registrarlo.

## T04

### D-T04-1 · 2026-10-04 · Una sola función decide el tamaño del terminal
- **Decisión:** `terminalLayoutFor(ventana, EdgeInsets, métricas de celda)` (en `domain/terminal`) es la
  única fuente del tamaño. El área dibujada es la ventana menos los insets, y la UI aplica como padding
  esos mismos números; así lo que se dibuja y lo que se le dice al pty no pueden desviarse. Los insets
  son los de las barras del sistema, el recorte de pantalla y el teclado, combinados borde a borde con
  el máximo (`EdgeInsets.union`): el teclado sustituye a la barra de navegación, no se suma.
- **Cambio respecto a T03:** la UI ya no usa `safeDrawingPadding()` ni mide el `Canvas`; mide la ventana
  completa (el fondo negro cubre también bajo las barras: sin bandas) y calcula el layout con la función.
- **Alternativas:** dejar que Compose reparta los insets y medir el `Canvas` (más simple, pero la
  semántica de los insets queda fuera del dominio y sin tests).

### D-T04-2 · 2026-10-04 · Redimensionado con debounce y sin reiniciar la sesión
- **Decisión:** los layouts pasan por `Flow.settled(120 ms)`: el primero se entrega al instante (el shell
  arranca ya con su tamaño) y después solo llega el último, cuando no entra otro en 120 ms; los iguales
  se descartan. Así una animación del teclado o arrastrar el borde de una ventana no envía un `SIGWINCH`
  por fotograma. `TerminalSessionHost.resize` ya ignoraba tamaños iguales y llama a
  `TerminalSession.updateSize`, que redimensiona el emulador y el pty (`TIOCSWINSZ`) sin tocar el
  proceso: el contenido y el shell se conservan.
- **Motivo del valor:** 120 ms salta los ~8 fotogramas de la animación del teclado sin notarse como
  retraso. Es una estimación sin medir.
- **Dependencias:** `kotlinx-coroutines-core` 1.11.0, declarada explícitamente porque el código la usa
  directamente (antes llegaba transitiva con `lifecycle`, en 1.9.0), y `kotlinx-coroutines-test` 1.11.0
  (solo tests). Ambas Apache-2.0. Lint (`NewerVersionAvailable`) falla con 1.9.0 y no aceptaba 1.11.0
  solo para tests mientras producción resolvía 1.9.0. Se anota en `THIRD_PARTY_NOTICES.md`.

### D-T04-3 · 2026-10-04 · Actividad: sin recreación y redimensionable
- **Decisión:** `configChanges` añade `navigation|fontScale|layoutDirection|locale` a lo que ya estaba
  (orientación, tamaños, teclado, `uiMode`, densidad), y `resizeableActivity="true"` es explícito (para
  `targetSdk` 28 ya es el valor por defecto, pero así no depende de un valor implícito en plegables y
  multiventana). `ManifestWindowConfigTest` falla si alguien lo quita. El `ViewModel` (y con él el
  shell) ya sobrevive a una recreación, pero recrear reconstruiría la vista y el campo de entrada.
- **Descartado:** `androidx.window` / `WindowSizeClass` en este prototipo: el cálculo no depende de
  clases de tamaño. Se evaluará (licencia Apache-2.0) en T10, cuando el layout de pestañas y paneles
  dependa del ancho.

### D-T04-4 · 2026-10-04 · Riesgo: `adjustResize` con edge-to-edge
- **Riesgo:** se mantiene `windowSoftInputMode="adjustResize"` y la app pinta edge-to-edge. Desde API 30
  el teclado llega como insets y la ventana no se redimensiona; en API 26-29 el comportamiento depende de
  cómo propague `enableEdgeToEdge` los insets. Si en algún nivel de API el sistema redimensionara la
  ventana **y** además se restara el teclado como inset, el terminal perdería el alto del teclado dos
  veces.
- **Pendiente:** comprobarlo en API 26-29 y 30+ (ver abajo). Si ocurre, la salida es no restar el
  teclado en las APIs en que `adjustResize` ya lo hace.

### T04: lo que NO se ha validado (sin dispositivo; hace falta una tablet y un móvil reales)
Cubierto en host (26 tests): cálculo de la rejilla para móvil, tablet vertical y apaisada, pantalla
dividida, ventana flotante, recorte lateral y teclado; el debounce con tiempo virtual; que el emulador
real adopta el nuevo tamaño y conserva el contenido y el historial; y los atributos del manifest.
Sin validar:
1. `stty size` y `echo $LINES $COLUMNS` coinciden con lo visible tras rotar, entrar/salir de
   multiventana y de pantalla dividida, abrir/cerrar el teclado y plegar/desplegar.
2. Que el pty recibe realmente `TIOCSWINSZ` y los programas (`vim`, `tmux`, `htop`) se redibujan.
3. Que no hay bandas ni huecos: el área ocupa el 100 % bajo las barras en tablet y móvil, con y sin
   recorte de pantalla, y con la barra de tareas de la tablet.
4. El comportamiento del teclado en API 26-29 frente a 30+ (D-T04-4) y con teclado físico conectado.
5. Que la actividad no se recrea en ninguno de esos cambios (el test solo comprueba el manifest).
6. Que 120 ms de debounce se siente bien y no hace saltar el contenido.
7. Redimensionar la ventana flotante arrastrando (freeform) en tablets con ese modo.

## T05 — Room y repositorios

### D-T05-1 · 2026-10-04 · Room 3 como en UltimateDeck, SQLite del sistema en la app
- **Decisión:** `androidx.room3` 3.0.3 (las versiones de Deck). En la app, `AndroidSQLiteDriver`
  (SQLite del sistema); en los tests de host, el `BundledSQLiteDriver` de JVM. Esquema exportado a
  `app/schemas`, versión 1, sin migración destructiva: subir `UltimateTerminalDatabase.VERSION` exige
  añadir su migración (lo comprueba `DatabaseSchemaTest`) y un caso en `MigrationTest`.
- **Motivo:** mismo patrón probado en Deck; el SQLite del sistema no engorda el APK.
- **Alternativas:** SQLite empaquetado en el APK (más peso); DataStore o JSON a mano (sin esquema ni
  migraciones verificables).
- **Impacto:** `MigrationTest` crea una base con cada esquema exportado y la abre con el código
  actual; hoy solo existe la versión 1.

### D-T05-2 · 2026-10-04 · Ajustes en una tabla clave-valor de Room
- **Decisión:** `AppSettings` se guarda en la tabla `setting` (`key`, `value` como texto), no en
  DataStore. Un valor ausente o ilegible cae a su valor por defecto; el *scrollback* se acota a
  100..1 000 000 al guardar.
- **Motivo:** los ajustes viajan en la copia de seguridad (T15) junto con el resto de metadatos y
  quedan en una sola base; no añade dependencia. Una fila dañada no debe romper la app.
- **Alternativas:** DataStore (otra dependencia y otro fichero que respaldar).
- **Impacto:** **los nombres de las claves (`SettingKeys`) son parte del formato de backup**: no
  renombrarlos sin migración.

### D-T05-3 · 2026-10-04 · Rutas siempre relativas y validadas (`FsPath`)
- **Decisión:** el directorio de cada distro se guarda como ruta relativa a la raíz de
  almacenamiento (`FsPath`), nunca absoluta. `FsPath` rechaza vacío, absolutas, segmentos vacíos,
  `.`, `..`, `\` y NUL. Las filas cuya ruta no sea válida (p. ej. de una copia manipulada) no se
  devuelven.
- **Motivo:** una copia restaurada en otro dispositivo (T15) no debe depender de dónde esté
  `filesDir`, y una ruta venida de un backup no debe poder salir de la raíz.
- **Impacto:** `ui` y `domain` no usan `java.io.File`; la ruta absoluta solo existe como `String` en
  `FileSystemRepository.absolutePathOf`, para construir la línea de comandos de proot.

### D-T05-4 · 2026-10-04 · `FileSystemRepository` no sigue enlaces simbólicos y copia de forma atómica
- **Decisión:** si algún componente intermedio de una ruta es un enlace simbólico, la operación se
  rechaza (`InvalidPath`); los recorridos de árboles no siguen enlaces. `copyRecursively` construye
  una copia `<destino>.partial` y la renombra (todo o nada; un `.partial` de un cierre anterior se
  reemplaza). El borrado da permisos de escritura al propietario en directorios de solo lectura. Los
  ficheros especiales (sockets, pipes, dispositivos) se omiten y los enlaces duros quedan como
  copias independientes.
- **Motivo:** un rootfs está lleno de enlaces absolutos (`/var/run -> /run`); seguirlos permitiría
  borrar o leer fuera de la raíz. Es la propiedad de seguridad más importante de esta capa y tiene
  tests específicos.
- **Alternativas:** `Files.copy` simple sin carpeta temporal (deja distros a medias, contra la regla
  de CLAUDE.md).
- **Impacto:** T07 (duplicar/eliminar) y T15 (restaurar) deben usar solo esta interfaz.

### D-T05-5 · 2026-10-04 · Una única distro predeterminada, garantizada por transacción
- **Decisión:** la primera distro registrada es la predeterminada. `setDefault` deja exactamente
  una. Al eliminar la predeterminada, la **más antigua** de las restantes pasa a serlo. Se hace en
  transacciones del DAO, no con una restricción en la base (SQLite vía Room no ofrece índices
  parciales).
- **Motivo:** una pestaña nueva siempre debe tener dónde abrirse; sin esta regla, borrar la
  predeterminada dejaba el estado sin distro por defecto.
- **Impacto:** el SPEC no fija este comportamiento; si se prefiere "ninguna", es un cambio de una
  línea en `deleteAndPromote`.

### D-T05-6 · 2026-10-04 · Nombres únicos sin distinguir mayúsculas; entradas SSH validadas
- **Decisión:** distros, perfiles, layouts y hosts tienen nombre único sin distinguir mayúsculas
  (`COLLATE NOCASE` + comprobación transaccional), de 1 a 64 caracteres y sin caracteres de control.
  El host SSH debe cumplir `[A-Za-z0-9._:%\[\]-]+` y no empezar por `-`; el usuario, un patrón sin
  espacios ni `-` inicial; el puerto, 1..65535. Perfiles: fuente 6..72 sp, *scrollback* 100..1 000 000.
- **Motivo:** un host `-oProxyCommand=...` pasado a `ssh` se interpretaría como opción (inyección de
  argumentos). Se rechaza ya al guardar, además de lo que deba hacer T14 al lanzar el comando.
- **Impacto:** T14 debe seguir pasando `--` antes del destino aunque los datos ya estén validados.

### D-T05-7 · 2026-10-04 · El árbol de paneles se guarda como JSON
- **Decisión:** `LayoutNode` (`pane` / `split`) se serializa con kotlinx.serialization a una columna
  de texto, con `type` como discriminador y claves desconocidas ignoradas (un layout de una versión
  posterior sigue abriéndose). El `ratio` de un `Split` debe estar estrictamente entre 0 y 1; un
  layout con JSON ilegible o `ratio` inválido no se devuelve en vez de romper la lista.
- **Alternativas:** tablas relacionales de nodos (más complejas sin ganancia: el árbol siempre se
  lee y se escribe entero).
- **Impacto:** el formato JSON de la copia de configuración (T15) puede reutilizar este.

### D-T05-8 · 2026-10-04 · Fakes: solo los que ya hacen falta, verificados con el mismo contrato
- **Decisión:** `FakeDistroRepository` e `InMemoryFileSystemRepository` (en `src/test`) pasan los
  mismos tests de contrato que las implementaciones reales (`DistroRepositoryContract`,
  `FileSystemRepositoryContract`), para que un fake no se desvíe de la realidad. No hay fakes de
  perfiles, layouts, hosts ni ajustes todavía; se añaden cuando una tarea los necesite.
- **Impacto:** T07 puede probar la instalación de distros sin base de datos ni disco.

### D-T05-9 · 2026-10-04 · Qué NO está validado
- Todo se verificó en la JVM del host (Room en memoria con SQLite empaquetado, `java.nio` sobre el
  sistema de ficheros de Linux). **No se ha probado en Android**: el cableado de Hilt (`di/`), el
  `AndroidSQLiteDriver`, la creación de `files/storage`, y que `java.nio` (enlaces simbólicos,
  permisos POSIX, `ATOMIC_MOVE`) se comporte igual en el almacenamiento privado de la app.
- Cobertura de `domain` + `data` en el momento de esta tarea: 99,4 % de líneas y 92,4 % de ramas.

## T11

### D-T11-1 · 2026-10-04 · Un solo enrutador decide qué significa cada entrada
- **Decisión:** `InputRouter` (en `domain/terminal`) recibe todo lo que llega del teclado (teclas
  físicas, texto del teclado en pantalla y toques en la fila de teclas extra) y devuelve
  `RoutedInput`: atajo de la app, tecla para el shell, texto, o nada. `TerminalKeyboard` solo
  ejecuta ese resultado. Así Ctrl/Alt pegajosos valen igual para los tres orígenes (tocar CTRL y
  escribir `c` en el teclado en pantalla envía Ctrl+C) y todo se prueba en el host.
- **Teclas modificadoras solas** (Shift, Ctrl, Alt, Meta, Bloq Mayús...) se ignoran y no gastan un
  modificador armado.
- **Alternativas:** dejar la lógica en `TerminalInputView` (no se puede probar en host).

### D-T11-2 · 2026-10-04 · Ctrl/Alt pegajosos: un toque arma, otro bloquea
- **Decisión:** cada toque cicla apagado → armado (solo la siguiente tecla) → bloqueado → apagado.
  "Doble toque = bloqueado" se implementa como un segundo toque mientras sigue armado, sin ventana
  de tiempo: es determinista y no depende de cuánto tarde el usuario.
- **Alternativa:** pulsación larga para bloquear (como Termux). Descartada: más difícil de descubrir
  y de hacer accesible.
- **Accesibilidad:** el estado (activa para la próxima tecla / bloqueada) se anuncia con
  `stateDescription`; los nombres hablados de cada tecla están en `strings.xml` (en/es).

### D-T11-3 · 2026-10-04 · Configuración de teclas extra y atajos como texto, sin dependencia nueva
- **Decisión:** `ExtraKeysConfig` y `ShortcutMap` son modelos puros e inmutables con
  `serialize()`/`parse()` en texto plano (una fila por línea con ids; `ctrl+shift+t=new_tab`).
  El `parse` es tolerante: descarta lo desconocido y lo devuelve en una lista de rechazos, y si no
  queda nada usa el valor por defecto. `ExtraKeysStore` es solo una interfaz: la persistencia
  (ajustes/Room) la hace otra tarea.
- **Motivo:** el formato JSON previsto para el backup (SPEC §5) pediría `kotlinx-serialization`,
  una dependencia nueva que aquí no hace falta. Cuando exista la copia de configuración (T15), estos
  textos pueden ir tal cual como campos de ese JSON.
- **Fila por defecto:** dos filas de siete teclas, que caben en 360 dp con el tamaño táctil de 48 dp.

### D-T11-4 · 2026-10-04 · Atajos por defecto y sus choques conocidos
- **Valores:** Ctrl+Shift+T nueva pestaña, Ctrl+Shift+W cerrar, Ctrl+Tab / Ctrl+Shift+Tab siguiente
  y anterior, Alt+1..9 ir a la pestaña n, Ctrl+Shift+C / Ctrl+Insert copiar, Ctrl+Shift+V /
  Shift+Insert pegar, Ctrl+Shift+`+` / Ctrl+Shift+`-` / Ctrl+Shift+0 zoom (más Ctrl+`+` y
  Ctrl+`-` del teclado numérico).
- **Choques:** Alt+dígito choca con los argumentos numéricos de readline (la SPEC lo pide, es
  reasignable). Ctrl+`-` y Ctrl+0 normales **no** se usan para el zoom porque el terminal los
  necesita (`^_` deshacer en readline).
- **Regla:** un atajo debe llevar Ctrl o Alt (Shift solo escribe mayúsculas), con la única
  excepción de Shift+Insert; `bind` y `parse` rechazan lo demás, de modo que un atajo mal
  configurado no puede quitarle al usuario la escritura normal.
- **Sin efecto todavía:** los atajos de pestañas salen como eventos (`appShortcuts`) que nadie
  atiende hasta T09. Copiar, pegar y zoom sí actúan. Con Ctrl+Shift+C y sin selección no se envía
  nada al shell.

### D-T11-5 · 2026-10-04 · Pegado: se usa el del emulador
- **Decisión:** no se reimplementa. `TerminalEmulator.paste` ya quita ESC y los C1, convierte los
  saltos de línea en retorno de carro y envuelve con `ESC[200~ … ESC[201~` si el programa activó el
  modo bracketed. `PasteTest` lo comprueba con el emulador real, incluido que un texto pegado que
  contiene `ESC[201~` no puede cerrar el corchete antes de tiempo.
- **Pendiente:** el texto que llega por `commitText` del teclado en pantalla (autocompletado de
  texto largo) se envía sin bracketed paste: es escritura, no pegado. Si algún teclado pega así,
  habrá que tratarlo.

### D-T11-6 · 2026-10-04 · Zoom con pellizco: tamaño exacto aparte del mostrado
- **Decisión:** `FontZoom` guarda el tamaño exacto (entre 8 y 40 sp) y muestra el redondeado a
  medio punto. Un pellizco lento da muchos factores muy pequeños; redondear cada uno haría que el
  tamaño no se moviera nunca. Los pasos de atajo son de 1 sp y el reinicio vuelve a 14 sp.
- **Efecto:** al cambiar el tamaño cambia la celda, y el layout se recalcula (T04) y llega al pty
  con el debounce de siempre. Sin persistencia todavía.

### D-T11-7 · 2026-10-04 · La fila de teclas extra reserva su alto en el layout
- **Decisión:** la fila va encima del teclado y las barras; su alto (filas × 48 dp, o 0 si está
  oculta) se suma al borde inferior con `EdgeInsets.reserveBottom` antes de calcular la rejilla,
  así el pty no cuenta ese espacio. Alto fijo de 48 dp por fila, el mínimo táctil de accesibilidad.
- **Fila oculta:** la configuración tiene `visible`; el ajuste que lo cambia llega con T16.

### T11: lo que NO se ha validado (sin dispositivo) y lo que falta
Cubierto en host (todo en `domain/terminal`): estados pegajosos, catálogo y (de)serialización de
teclas extra, coincidencia exacta de modificadores y (de)serialización de atajos, el enrutador con
`KeyEncoder` real (Ctrl+C, Alt+x, Ctrl+flecha...), zoom, reserva de alto y pegado con el emulador
real.
Sin validar en dispositivo:
1. **Tres gestos en el mismo nodo** (toque, desplazamiento vertical, pulsación larga) más el nuevo
   pellizco. El pellizco va el último en la cadena de `pointerInput` para ver los eventos primero y
   solo consume con dos o más dedos, pero no se ha comprobado que no compita con el desplazamiento
   (un dedo que ya arrastró antes de que entre el segundo) ni con la selección.
2. **IME real:** `commitText`, `setComposingText` y las teclas de otros teclados con Ctrl/Alt
   pegajosos (un teclado que manda el carácter ya compuesto no pasa por `KeyEvent`).
3. **Teclado físico real:** que `onKeyDown` reciba Alt+dígito, Ctrl+Tab y Ctrl+Shift+letra (el
   sistema o el teclado pueden quedárselos), el `numLock` y los teclados no estadounidenses (el
   código de `+` y de `=` cambia con la distribución).
4. **La fila de teclas extra:** que tocarla no le quite el foco a la vista de entrada y cierre el
   teclado; su aspecto; su altura real frente a los 48 dp calculados.
5. **Rendimiento del zoom:** recrear el `TerminalPainter` en cada paso del pellizco.
6. **Portapapeles real** con Ctrl+Shift+C/V.
Falta por hacer:
- **Ratón y rueda** (reporte de ratón al shell, rueda para el historial): no está en este cambio.
- **Persistencia y ajuste de la fila y de los atajos:** otra tarea (ajustes, T16).
- **Efecto de los atajos de pestañas:** T09.

## T08 — Servicio en primer plano y sesiones

### D-T08-1 · 2026-10-04 · El ciclo de vida de las sesiones es lógica pura en `domain/session`

- **Decisión:** `Sessions` (instantánea inmutable) y `SessionController` (con `SessionFactory`,
  `SessionHandle` y `ServiceControl` como interfaces) deciden qué sesiones existen, cuál es la activa
  y cuándo debe correr el servicio. No usan tipos de Android.
- **Motivo:** CLAUDE.md pide la lógica de negocio en `domain`, y así las reglas de la SPEC RF-07
  («el servicio vive mientras corra un shell y solo entonces») se prueban en el host.
- **Reglas:** el servicio corre si hay al menos un shell *en ejecución*; una sesión terminada sigue
  listada, para poder leer su salida o reiniciarla, pero no mantiene el servicio. Al cerrar la
  activa se prefiere una en ejecución. Los ids no se reutilizan. Un shell que no puede arrancar queda
  como sesión terminada con estado `-1`; uno que termina antes de que `start` devuelva se registra
  igualmente (la sesión se publica antes de arrancar el shell).
- **Impacto:** `SessionManager` (Android, `@Singleton`) es solo un adaptador fino. La capa Android no
  tiene tests de host porque necesita un PTY.

### D-T08-2 · 2026-10-04 · Tipo de servicio `specialUse`, no `dataSync`

- **Decisión:** `foregroundServiceType="specialUse"` con el subtipo `terminal_sessions`, más los
  permisos `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_SPECIAL_USE`.
- **Motivo:** ningún tipo estándar describe un terminal. `dataSync` es el que se usa por costumbre,
  pero está pensado para transferencias finitas y Android 15 le pone un tope de 6 horas por día
  cuando el `targetSdk` es ≥ 35; cortaría un SSH largo. `specialUse` no tiene tope. La revisión que
  Google Play exige para `specialUse` no aplica: no se publica allí (SPEC §2).
- **Alternativas:** no declarar tipo (lo que hace Termux con `targetSdk` 28), válido hoy porque el
  tipo solo es obligatorio con `targetSdk` ≥ 34; se declara de todos modos para no tener que
  tocarlo el día que se suba el `targetSdk`.
- **Impacto:** `ServiceCompat.startForeground` pasa el tipo solo desde API 34. **Sin validar en
  dispositivo.** Si algún Android rechazara `specialUse` con `targetSdk` 28, la alternativa es quitar
  el tipo y dejar solo los permisos.

### D-T08-3 · 2026-10-04 · El servicio no posee las sesiones; las posee `SessionManager`

- **Decisión:** las sesiones viven en un singleton de Hilt con la vida del proceso. El servicio solo
  mantiene vivo el proceso, muestra la notificación, sostiene el wake lock y se detiene solo.
  `TerminalViewModel` ya no arranca ni para el shell: se reconecta al activo. Cerrar la actividad
  con «atrás» no mata el shell mientras el servicio corra.
- **Motivo:** la UI se reconecta a sesiones vivas tras recrear o cerrar la actividad (SPEC RF-07),
  y un servicio *bound* o un `Binder` habría añadido un ciclo de vida más sin ganar nada: servicio y
  actividad comparten proceso.
- **Detalles:** el contador de fotogramas del ViewModel cuenta cada emisión del host activo, porque
  los contadores de dos hosts pueden coincidir y la pantalla no se redibujaría al cambiar de
  sesión. «Salir» en la notificación cierra todas las sesiones y la actividad; después del primer
  layout el ViewModel no vuelve a crear una sesión por su cuenta, para no resucitar el shell
  mientras la pantalla se cierra.
- **Pendiente:** el cambio entre sesiones desde la UI (pestañas) es T09; aquí solo hay una activa a
  la vez, creada al abrir o desde la notificación.

### D-T08-4 · 2026-10-04 · Permisos pedidos en contexto y optimización de batería solo como aviso

- **Decisión:** cuando hay un shell en ejecución se muestra, una vez por arranque y como diálogo con
  explicación, primero el permiso de notificaciones (API 33+) y después el aviso de batería. El
  orden y las condiciones están en `nextPrompt`, probado. Lo rechazado no se vuelve a pedir en ese
  arranque (persistirlo, con los ajustes, es T16).
- **Batería:** se abre `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, la pantalla de ajustes del
  sistema. **No** se declara `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, que mostraría un diálogo de
  exclusión directa: la app no se excluye sola y un test lo vigila. Es el «sin exclusión forzada»
  que pidió el plan.
- **Notificaciones con `targetSdk` 28:** Android 13+ solo exige el permiso en tiempo de ejecución a
  apps con `targetSdk` ≥ 33, y a las demás les muestra su propio aviso al crear el canal. La app lo
  pide explícitamente, con su explicación, antes de que ocurra. **Sin validar en dispositivo** cuál
  de los dos aparece primero.
- **Wake lock:** `PARTIAL_WAKE_LOCK` sin tiempo máximo, mientras haya un shell y el ajuste
  `keepAwake` (modelo de T05) esté activo; se suelta al terminar el último shell y en `onDestroy`.
  `WakelockTimeout` de Lint está suprimido en ese punto, con motivo: un tope anularía la función.
  Cambiar el ajuste desde la UI llega con T16.

### D-T08-5 · 2026-10-04 · Phantom process killer (Android 12+): documentado, sin mitigación en código

- **Qué es:** desde Android 12 el sistema limita a 32 los procesos hijo («fantasma») por app y mata
  el más antiguo al pasarse, **aunque haya un servicio en primer plano**. Cada programa que lance un
  shell (y con proot, cada programa de la distro) cuenta. Un `tmux` con muchos paneles o varias
  sesiones con SSH, `ssh` y `top` pueden llegar al límite y morir sin aviso.
- **Qué hace falta del usuario hoy:** en Android 14 QPR1 y posteriores, Opciones de desarrollador →
  «Desactivar restricciones de procesos secundarios». En 12–13 solo se desactiva con `adb`
  (`settings put global settings_enable_monitor_phantom_procs false`, o
  `device_config put activity_manager max_phantom_processes 2147483647`).
- **Decisión:** la app no puede cambiar ese ajuste (necesita un permiso de sistema), así que no se
  intenta nada en código. Se documentará en el README y la política de privacidad (T21) y es
  un punto a explicar en la primera ejecución cuando exista la pantalla de ayuda.
- **Impacto:** el servicio mantiene vivo el proceso, pero no garantiza que sobrevivan todos sus hijos.
  **Sin medir:** no se sabe cuántos procesos fantasma cuenta cada shell con proot (T07).

### D-T08-7 · 2026-10-04 · La selección sale del `ViewModel` a `SelectionController`

- **Decisión:** `SelectionController` (en `terminal/`, como `FontSizeController`) guarda la selección y
  copia el texto del host activo. `TerminalViewModel` la expone como `selection` y la pantalla llama a
  `viewModel.selection.start/extend/clear/copy`.
- **Motivo:** detekt (`TooManyFunctions`, 11/11) falló en `TerminalViewModel` al sumar el ciclo de vida
  de sesiones. No se relajó la regla (CLAUDE.md): se movió la lógica a un colaborador, el patrón que ya
  usaban el zoom y los atajos.
- **Impacto:** cambio de API interna sin efecto visible. Sin validar en dispositivo, como el resto.

### D-T08-8 · 2026-10-04 · `POST_NOTIFICATIONS` con guarda de `SDK_INT`, y un test corregido

- **Decisión:** el permiso se pide solo si `SDK_INT >= 33`, con una comprobación explícita en lugar de
  silenciar `InlinedApi`. El diálogo ya solo se mostraba en API 33+, pero Lint no lo sabía.
- **Test corregido:** `activatingASessionResizesItToTheCurrentLayout` suponía que la sesión no activa
  recibía el cambio de tamaño, pero tras `newSession()` la activa es la última, así que el
  comportamiento del controlador era el correcto y el test, erróneo. Ahora comprueba que la sesión
  inactiva solo se redimensiona al activarla y que la activa lo hace en `onLayout`.

### D-T08-6 · 2026-10-04 · Qué NO está validado en T08

Todo lo que necesita un dispositivo, por la prohibición del usuario: arranque del servicio y su tipo
en cada versión de Android, la notificación y sus acciones, la reconexión de la UI al cambiar de
actividad, el wake lock real, el comportamiento tras «Salir» y los diálogos de permisos. En el host
están probados el ciclo de vida de las sesiones (`SessionsTest`, `SessionControllerTest`), el orden de
los avisos (`SessionPromptsTest`) y el manifiesto del servicio (`ManifestServiceTest`).

## T19 — Versionado y releases

### D-T19-1 · 2026-10-04 · Release solo con clave: el workflow se niega a publicar sin firma
- **Decisión:** `release.yml` falla si `UT_KEYSTORE_BASE64` no está definido; también falla si el
  `CHANGELOG.md` no tiene notas de esa versión. Sin variables `UT_*`, `assembleRelease` sigue
  produciendo un APK sin firmar (lo que F-Droid compara).
- **Motivo:** el workflow de UltimateDeck publicaría un APK sin firmar si faltara el secreto; un
  usuario no podría actualizar después sobre él.
- **Impacto:** el primer tag necesita la clave creada y los 4 secretos (ver `RELEASING.md`). No se creó
  ninguna clave, secreto, tag ni release en esta tarea.

### D-T19-2 · 2026-10-04 · Reproducibilidad: rutas fuera del código nativo
- **Decisión:** `-ffile-prefix-map=<raíz del proyecto>=.` en las compilaciones C de `:app` (proot y
  talloc) y de `:terminal-emulator`. No hay marcas de tiempo ni `git describe` en los scripts de
  build (`PROOT_VERSION` es una constante). NDK y CMake siguen fijados.
- **Comprobado:** en un clon fuera del repo, ninguna lib de proot ni del emulador contiene la ruta
  del checkout.
- **NO comprobado:** que dos máquinas distintas produzcan un APK idéntico bit a bit (`diffoscope`).
  Queda como paso previo al primer envío a F-Droid (`RELEASING.md`).

### D-T19-3 · 2026-10-04 · `fdroid/com.qtekfun.ultimateterminal.yml` con valores de ejemplo
- **Decisión:** versión `0.1.0`, `submodules: true`, NDK `28.2.13676358` y `AllowedAPKSigningKeys` con un
  marcador `REPLACE_WITH_THE_SHA256_OF_THE_RELEASE_CERTIFICATE`. Solo se ofrecen versiones finales
  (`UpdateCheckMode: Tags ^v…$`, sin `-rc`).
- **Pendiente:** huella real del certificado, tag real y revisión de antifeatures. Los campos
  `ndk:` y `submodules:` siguen la sintaxis de fdroiddata pero no se han validado con `fdroid lint`.
- **Fuera de alcance aquí:** metadatos fastlane y capturas (T20).

## T06 — Descarga y verificación de rootfs

### D-T06-1 · 2026-10-04 · De dónde sale la URL y el hash: el índice oficial de cada distro
- **Decisión:** el catálogo (`OfficialRootfsCatalog`) no lleva URLs ni hashes escritos en el código.
  Lee en el momento el índice que publica cada proyecto, y de ahí sale el fichero actual:
  - **Alpine:** `https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/<arch>/latest-releases.yaml`.
    La entrada `alpine-minirootfs` trae `file`, `version`, `size` y `sha256`. Arquitecturas: `aarch64`,
    `armv7`, `x86_64`.
  - **Ubuntu:** `https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/SHA256SUMS`, con líneas
    `<sha256> *ubuntu-base-<versión>-base-<arch>.tar.gz`. Hay varias versiones puntuales en la misma
    carpeta, así que se elige la más alta comparando números (24.04.10 > 24.04.9). El índice no da
    tamaños: el tamaño de Ubuntu es `null` y solo se verifica el hash. Arquitecturas: `arm64`, `armhf`,
    `amd64`.
  - **Debian:** el rootfs oficial `slim` que construye [debuerreotype] y publica como imagen OCI el
    repositorio `debuerreotype/docker-debian-artifacts` (ramas `dist-arm64v8`, `dist-arm32v7`,
    `dist-amd64`). El `image-manifest.json` da el `digest` (SHA-256) y el tamaño de la única capa, y el
    fichero es `<suite>/slim/oci/blobs/rootfs.tar.gz` (un `tar.gz`, unos 30 MB), servido por
    `raw.githubusercontent.com`.
- **Motivo:** las URLs de los mirrors caducan (cada versión puntual sustituye a la anterior y las
  antiguas se retiran). Descubrirlas en el índice evita publicar una versión de la app solo para
  actualizar una URL, y el hash viaja siempre junto al fichero que describe.
- **Alternativas descartadas:**
  - *URLs y hashes fijos en el código:* se rompen con la siguiente versión puntual.
  - *Debian con `debootstrap` en el dispositivo:* necesita red, es lento y complejo bajo proot, y deja el
    resultado sin un hash único que verificar.
  - *Imágenes cloud de Debian:* son discos `qcow2`/`raw`, no un rootfs.
  - *`rootfs.tar.xz` del mismo repositorio:* solo existe su `.sha256`; el fichero no está en la rama.
    El que se publica y se puede descargar es el `tar.gz` de la capa OCI.
- **Impacto:** se usan las constantes de `RootfsEndpoints` (Alpine `latest-stable`, Ubuntu `24.04` LTS,
  Debian `trixie`); cambiar de versión mayor es una actualización de la app, a propósito (ver D-T06-4).

[debuerreotype]: https://github.com/debuerreotype/debuerreotype

### D-T06-2 · 2026-10-04 · Hash sobre HTTPS, sin comprobar firma GPG (riesgo aceptado, a mejorar)
- **Decisión:** la integridad se basa en el **SHA-256 publicado por el propio proyecto**, obtenido por
  HTTPS, y en comprobar el tamaño cuando el índice lo da. **No** se verifican las firmas GPG que
  publican Ubuntu (`SHA256SUMS.gpg`), Alpine (`.asc`) o Debian (`InRelease`).
- **Motivo:** verificar firmas exige una biblioteca OpenPGP y llevar dentro las claves públicas de cada
  proyecto (y mantenerlas al día), lo que añade una dependencia y una superficie de errores grande para
  el MVP.
- **Riesgo (qué NO protege esto):** quien controle a la vez el mirror (o la cuenta de GitHub, para
  Debian) y su hash, o rompa la validación TLS, puede servir un rootfs manipulado que pasaría la
  comprobación. El hash sí protege de descargas corruptas o cortadas, de mirrors que sirven un fichero
  distinto al del índice y de equivocaciones en la reanudación.
- **Mejora pendiente (backlog):** comprobar la firma de `SHA256SUMS`/`InRelease` con claves incluidas en
  la app (y revisar su licencia y distribución). No se hace sin consultar al usuario.

### D-T06-3 · 2026-10-04 · Debian: manifiesto y capa se leen en dos peticiones sobre una rama que cambia
- **Riesgo:** `dist-*` es una rama que se reescribe con frecuencia (se republica al reconstruir la imagen).
  Entre leer el `image-manifest.json` y descargar el `rootfs.tar.gz` puede publicarse una versión nueva,
  y entonces el hash del manifiesto ya no corresponde al fichero.
- **Decisión:** no se intenta evitar en T06. El descargador lo trata como lo que es, un `HashMismatch`:
  borra el parcial y no instala nada. T07 debe **volver a resolver el catálogo y reintentar una vez**
  antes de mostrar el error. (Se podría fijar a un commit con la API de GitHub, pero añade una consulta
  con límite de peticiones; se descarta de momento.)

### D-T06-4 · 2026-10-04 · Versiones elegidas
- **Decisión:** Alpine `latest-stable` (se actualiza solo), Ubuntu **24.04 LTS** (la última puntual de
  esa serie) y Debian **trixie**. `RootfsEndpoints` las agrupa.
- **Motivo:** una versión estable y con soporte largo es más segura para un usuario que mantiene
  servidores; Alpine es la que más se mueve y por eso sigue `latest-stable`.
- **Impacto:** al salir otra LTS o estable hay que cambiar la constante a mano y publicar la app.

### D-T06-5 · 2026-10-04 · Protocolo de descarga: parcial, Range, reintentos e instalación atómica
- **Decisión (`HttpRootfsDownloader`):**
  - Descarga a `<destino>.part`; el fichero solo aparece en `<destino>` tras verificarlo, con un
    `ATOMIC_MOVE` en el mismo directorio (existe completo o no existe). Un destino anterior se sustituye.
  - **Reanudación:** si hay parcial, se pide `Range: bytes=<n>-`. Con `206` se añade al parcial, pero
    solo si `Content-Range` empieza exactamente en `<n>`; si no, se descarta el parcial y se reintenta.
    Con `200` (el servidor ignoró el rango) se empieza de cero. Con `416` se verifica el parcial: si ya
    es el fichero completo se instala, y si no se borra y se reintenta.
  - **Reintentos:** hasta 4 intentos con espera exponencial (1 s, 2 s, 4 s) ante errores de red, `5xx`,
    `408` y `429`; el parcial se conserva para continuar. Cualquier otro `4xx` (p. ej. `404`) falla sin
    reintentar. (OkHttp ya reintenta él solo un primer `408`.)
  - **Verificación:** el tamaño (si se conoce) y el SHA-256 se comprueban sobre el fichero completo. Si
    fallan, se **borra el parcial** y se devuelve `SizeMismatch`/`HashMismatch`: no hay reintento, porque
    repetir la descarga de lo mismo no arregla un hash que no corresponde.
  - **Cancelación:** se comprueba en cada bloque leído; el parcial se conserva para reanudar después.
  - Errores con tipos sellados (`RootfsError`/`RootfsResult`), no excepciones hacia la UI.
- **Motivo:** son las garantías que pedía la spec (RF-04): un fallo de red a mitad no deja una distro
  corrupta y no se instala nada sin verificar.

### D-T06-6 · 2026-10-04 · Solo HTTPS, aplicado en el código
- **Decisión:** el descargador rechaza (`InsecureUrl`) cualquier URL que no sea `https://`, sin hacer la
  petición. Un parámetro `requireHttps` (por defecto `true`) existe solo para los tests con
  `MockWebServer`.
- **Motivo:** con `targetSdk` 28 el tráfico en claro está permitido por defecto en Android, así que no
  se puede delegar en el sistema.

### D-T06-7 · 2026-10-04 · Dependencias nuevas
- **Decisión:** OkHttp 5.5.0 (Apache-2.0; arrastra Okio y AndroidX Startup, también Apache-2.0),
  `kotlinx-serialization-json` 1.11.0 (Apache-2.0) y, solo para tests, `mockwebserver3` y
  `mockwebserver3-junit5`. Son las mismas versiones y bibliotecas que usa UltimateDeck. Anotadas en
  `THIRD_PARTY_NOTICES.md`; pasan `licensee` (solo Apache-2.0) y `checkForbiddenDependencies`.
- **Motivo del JSON:** `org.json` de Android está vacío en los tests de host, y una expresión regular
  sobre JSON es frágil. El YAML de Alpine y `SHA256SUMS` son formatos planos y se leen a mano, sin
  añadir un parser de YAML.

### D-T06-8 · 2026-10-04 · Cobertura crítica del paquete `data.rootfs.verify`
- **Decisión:** `Sha256Verifier` (tamaño y SHA-256) está al **100 % de líneas y ramas** con tests de
  host que cubren: coincidencia, hash en mayúsculas, tamaño desconocido, tamaño o hash erróneos,
  fichero mayor que el búfer, fichero vacío, checksum mal formado y fichero ilegible. Las constantes
  están a nivel de archivo porque en un `companion object` privado Kover contaba el accesor sintético
  como una línea sin cubrir.
- **Impacto:** `koverVerifyCritical` mide ya código real; antes el paquete estaba vacío.

### T06: lo que NO está validado
- Los índices reales se consultaron con `curl` el 2026-10-04 y los tests usan extractos de esas
  respuestas, pero **la app no ha descargado todavía de los mirrors reales** (sin dispositivo ni
  autorización para usar la red desde uno). El formato de esos índices puede cambiar.
- Comportamiento de red real en Android: tiempos de espera (el cliente de OkHttp por defecto corta tras
  10 s sin datos y se reintenta), cambios de red a mitad, ahorro de datos y la validación TLS del
  sistema.
- Que `ATOMIC_MOVE` funcione en el almacenamiento privado de la app en todos los dispositivos (es el
  mismo directorio, así que debería).
- La extracción, la elección de arquitectura con `Build.SUPPORTED_ABIS` (`Architecture.fromAbis`) y el
  reintento por `HashMismatch` de Debian son de T07; aquí solo están las interfaces `RootfsCatalog` y
  `RootfsDownloader` y sus implementaciones.

## T07 — Instalación y gestión de distros

### D-T07-1 · 2026-10-04 · Una instalación es una transacción: preparar al lado y mover al final
- **Decisión:** `DistroInstaller` registra la distro como `INSTALLING` **antes** de descargar (así un nombre
  repetido falla sin gastar datos), descarga y descomprime en `distros-tmp/<token>/` y solo mueve el árbol
  terminado a `distros/<token>` con un renombrado atómico; después marca la fila `READY`. Cada instalación
  tiene su propio token (UUID), así que dos instalaciones, o una instalación y los restos de una caída, nunca
  comparten directorio.
- **Fallo o cancelación:** un `finally` bajo `NonCancellable` borra el directorio temporal, el destino y la
  fila. Así, cancelar a mitad de la extracción no deja ni ficheros ni registros (hay un test que cancela con
  el extractor detenido y comprueba ambas cosas).
- **Si el proceso muere:** `DistroManager.recoverInterrupted()` borra toda fila que no sea `READY` con sus
  ficheros y el área temporal. Se llama una vez al arrancar la pantalla, antes de permitir instalar.
- **Alternativas descartadas:** extraer directamente en el destino y borrar si falla (un corte de energía
  dejaría una distro a medias que parece válida); no registrar la fila hasta el final (permitiría descargar
  100 MB para descubrir que el nombre ya existe).
- **Impacto:** T08 mueve la instalación al servicio en primer plano; la lógica de limpieza no cambia.

### D-T07-2 · 2026-10-04 · Ante un hash que no coincide, se vuelve a resolver el catálogo y se reintenta una vez
- **Decisión:** cumple lo que pedía D-T06-3. Solo el error `HashMismatch` repite el intento completo (resolver,
  descargar, verificar, extraer); cualquier otro fallo (red, 404, disco) se muestra sin repetir. Un segundo
  `HashMismatch` se informa al usuario. Cada intento es una transacción entera, así que el primero ya no deja nada.
- **Motivo:** la rama de Debian se reescribe a menudo y el hash del índice puede quedar obsoleto entre las dos
  peticiones. No se repite ante fallos de red para no gastar datos del usuario en bucle.
- **Riesgo:** un hash incorrecto *real* (un fichero manipulado) también se reintenta una vez; el segundo intento
  vuelve a fallar y no se instala nada, así que el único coste es una descarga extra.

### D-T07-3 · 2026-10-04 · Extractor propio sobre Apache Commons Compress, solo gzip y tar
- **Decisión:** `TarGzExtractor` usa `commons-compress` 1.28.0 (Apache-2.0). Solo se leen **gzip y tar plano**,
  que es lo que publican las tres fuentes de T06. xz, bzip2 y zstd se reconocen por su cabecera y se rechazan
  con un error que nombra el formato (`ArchiveFormat`), en vez de un fallo genérico.
- **Dependencias que entran en el APK** (comprobadas con `licensee`, todas Apache-2.0): `commons-compress`
  1.28.0, `commons-io` 2.20.0, `commons-codec` 1.19.0 y `commons-lang3` 3.18.0. xz, zstd y brotli son
  opcionales de Commons Compress y **no** se incluyen (reglas `-dontwarn` en R8).
- **Alternativas descartadas:** `tar` del sistema vía `ProcessBuilder` (no está garantizado en Android y no se
  puede probar en el host); escribir un lector de tar propio (reinventa un formato con muchas rarezas, PAX,
  GNU long names…); añadir `xz` (otra dependencia para un formato que ninguna fuente usa hoy).
- **Impacto:** si una fuente pasa a servir `.tar.xz` hay que añadir `org.tukaani:xz` (dominio público, se
  puede usar) y quitar el rechazo.

### D-T07-4 · 2026-10-04 · Reglas de seguridad de la extracción (lo más delicado de T07)
Un rootfs descargado se trata como entrada hostil aunque su hash coincida, porque el hash solo prueba que es
el fichero que publicó el proyecto, no que sea inofensivo. Reglas (código en `SafeTreeWriter`, probadas con
tars sintéticos hostiles):
- **Nada fuera del destino.** Un nombre con `..` se rechaza (`UnsafeEntry`). Las barras iniciales se quitan
  como hace GNU tar.
- **Nunca se escribe a través de un enlace simbólico.** Cada directorio padre se comprueba sin seguir enlaces:
  si es un enlace, se rechaza todo el archivo. Esto cierra el ataque clásico de crear `link -> /ruta/fuera` y
  luego escribir `link/fichero`.
- **Los enlaces simbólicos en sí se guardan tal cual**, incluso los absolutos (`/bin/sh -> /bin/busybox`), porque
  un rootfs normal está lleno de ellos y solo se resuelven dentro de proot, nunca en el sistema anfitrión.
- **Enlaces duros:** solo hacia un fichero regular ya extraído dentro del destino; hacia fuera, hacia un enlace
  simbólico o hacia la raíz se rechazan. Si el sistema de ficheros no admite enlaces duros, se copia el contenido.
- **Un directorio no puede sustituir a un fichero ni al revés**, y un fichero no puede ocupar el sitio de un
  directorio padre.
- **Permisos:** se conservan los nueve bits rwx; **se descartan setuid, setgid y sticky**. Los directorios
  siempre conservan rwx para el propietario (la app) para poder borrarlos; el modo real se aplica al final para
  que un directorio de solo lectura no bloquee a sus hijos. No se restaura el propietario (todo es de la app).
- **Dispositivos, sockets y FIFO se omiten** (no se pueden crear sin root) y se cuentan en `skippedSpecialFiles`.
- **Límites contra bombas de descompresión:** 2 000 000 entradas, 16 GiB y 4096 caracteres por ruta (valores
  muy por encima de cualquier rootfs real; configurables). Se rechaza un archivo **sin ninguna entrada** (un
  fichero vacío o equivocado no es una distro).
- **Un nombre con byte nulo no puede colar una ruta:** Commons Compress corta el nombre en el primer `\0`, como
  `tar`, de modo que lo que se valida es lo que se escribe. Se comprobó con un tar construido a mano.
- **Cancelación:** se comprueba entre entradas y cada MiB de datos, así que cancelar responde sin esperar a que
  termine un fichero grande.

### D-T07-5 · 2026-10-04 · Espacio libre: se comprueba antes de descargar
- **Decisión:** se exige `max(64 MiB, 5 × tamaño del archivo)` (el archivo se conserva mientras se descomprime y
  un rootfs ocupa unas 4 veces su tamaño comprimido); si el índice no da tamaño (Ubuntu) se asume 512 MiB.
- **Motivo:** rechazar con un mensaje claro y el espacio necesario es mejor que llenar el disco y fallar al
  final. Es una estimación **sin medir en un rootfs real**: queda pendiente de validar.

### D-T07-6 · 2026-10-04 · Duplicar y eliminar son seguros ante interrupciones
- **Duplicar:** la copia se construye junto a su destino y se renombra (`copyRecursively` de T05); su fila solo
  pasa a `READY` después. Un fallo o una cancelación dejan sin copia ni fila. La copia no es la distro
  predeterminada.
- **Eliminar:** la fila se marca `FAILED` **antes** de borrar ficheros. Si el borrado se interrumpe, la distro
  se ve como "dañada" (y se puede volver a eliminar) en vez de parecer intacta. No se puede eliminar una distro
  que se está instalando.
- **Predeterminada:** solo una distro `READY` puede serlo, para que una pestaña nueva siempre pueda abrirla.
  Al eliminar la predeterminada, T05 ya pasa la marca a la más antigua que quede.

### D-T07-7 · 2026-10-04 · Pantalla de gestión: mínima y provisional
- **Decisión:** `DistroScreen` (lista, instalar, renombrar, duplicar, eliminar, predeterminada, progreso y
  cancelar) sin lógica en los composables: el estado y las acciones están en `DistroViewModel`, con los
  mensajes de error traducidos en `DistroMessages`. Se abre desde un botón provisional en `MainActivity`
  que T09 (pestañas) sustituirá. Textos en `values/` y `values-es/`; botones de al menos 48 dp.
- **La instalación vive en el `ViewModel`**: sobrevive a rotar la pantalla pero **no a cerrar la app**. T08 la
  pasa al servicio en primer plano. Mientras tanto, si el proceso muere a mitad, `recoverInterrupted()` limpia
  al arrancar.
- **Permiso `INTERNET`:** añadido al manifiesto, solo para descargar el rootfs de la distro que el usuario elige
  instalar. Debe explicarse en `PRIVACY.md` (T21).

### D-T07-8 · 2026-10-04 · Créditos de Apache Commons: el APK no conserva sus avisos, se empaquetan a mano
- **Hallazgo:** al comprobar el APK, Android Gradle Plugin **no incluye** los `META-INF/NOTICE.txt` ni
  `LICENSE.txt` de estas librerías (el único `NOTICE` presente es el de Jakarta Injection). La Apache-2.0 exige
  conservar el aviso al redistribuir en binario.
- **Decisión:** los textos de las cuatro librerías se copian **sin modificar** a
  `app/src/main/res/raw/third_party_notices_apache.txt`, con un `keep.xml` para que el *resource shrinker* no los
  elimine. Se comprobó con `aapt2` en el APK de release que el recurso existe y es idéntico byte a byte al fuente.
- **Abierto en T07, cerrado en T23 (D-T23-2):** lo mismo puede afectar a **OkHttp y Okio** (T06) y al resto de dependencias que
  ya estaban en `master`; hay que comprobarlo y, si es así, empaquetar también sus avisos y mostrar una pantalla
  de licencias. Se deja para decidir; no se amplió el alcance de T07 sin preguntar.

### T07: lo que NO se ha validado (sin dispositivo)
- **Que la instalación funcione de principio a fin en un dispositivo:** descargar de los mirrors reales,
  descomprimir un rootfs de verdad (decenas de miles de ficheros) y que `ATOMIC_MOVE` y los enlaces simbólicos
  funcionen en el almacenamiento privado (ya señalado en D-T05-9).
- **El tiempo y el espacio reales** de instalar Debian, Ubuntu y Alpine, y si el cálculo de espacio libre
  (D-T07-5) es acertado.
- **Que un rootfs extraído por esta vía arranque con proot** (T02 tampoco está validada): los permisos, los
  enlaces absolutos y la ausencia de dispositivos pueden necesitar ajustes que solo se ven al ejecutarlo.
- **La pantalla:** el aspecto y el uso real en móvil y tablet, la rotación durante una instalación, los
  diálogos con el teclado, y TalkBack. Solo se han probado el `ViewModel` y la lógica, no los composables.
- **Rendimiento de la extracción** con el almacenamiento real, y que cancelar responda a tiempo en un fichero
  muy grande.

## T09 — Pestañas

### D-T09-1 · 2026-10-04 · Una pestaña es una sesión; el modelo vive en `Sessions`
**Decisión:** no hay un modelo de pestañas aparte. `SessionInfo` gana `title` (lo que escribe el
usuario, null = nombre por defecto) y `distroId`, y `Sessions` gana reducers puros: `renamed`,
`moved`, `switched(TabSwitch)` y `closeAction`. El orden de las pestañas es el orden de `items`.
**Motivo:** una pestaña no tiene vida propia sin su sesión; un segundo modelo habría que mantenerlo
sincronizado (¿qué pasa si el shell termina?). Así toda la lógica es inmutable y se prueba en host.
**Alternativas:** `Tab` separado con referencia a `SessionId` (más piezas, mismo comportamiento).
**Impacto:** `SessionInfo` cambia con valores por defecto, por lo que el código y los tests de T08
siguen compilando.

### D-T09-2 · 2026-10-04 · Los cambios puros pasan por `SessionEditor.edit`
**Decisión:** `SessionController` implementa `SessionEditor` (`state`, `newSession(distroId)`,
`close`, `edit { ... }`). Renombrar, reordenar y cambiar de pestaña son un `edit` que no toca los
shells; solo si cambia la pestaña activa se redimensiona el pty que pasa a primer plano al tamaño
actual (lo mismo que ya hacía `activate`, que ahora es un `edit`).
**Motivo:** añadir una función por operación al controlador superaba el límite de `TooManyFunctions`
de detekt (11), que no se relaja, y mezclaba el ciclo de vida de los procesos con ediciones que no
lo cambian. La interfaz permite probar `TabsController` con el controlador real y fakes.
**Alternativas:** una función por operación (rompe detekt); `TabsController` dependiendo de
`SessionManager` (Android, sin pruebas de host).

### D-T09-3 · 2026-10-04 · Cerrar: confirmación solo si el shell sigue vivo
**Decisión:** cerrar una pestaña cuyo shell ya terminó la cierra sin preguntar; si sigue en marcha,
`TabsController` guarda la pestaña en `closeConfirmation` y la UI muestra un diálogo; solo al
confirmar se detiene el shell. Una pregunta sobre una pestaña que desaparece mientras tanto (el
shell se cerró por otro lado) se descarta sola. Cerrar la última pestaña cierra la app, igual que
«Salir» de la notificación (comportamiento de T08: sin sesiones, la pantalla se cierra).
**Motivo:** SPEC RF-02 («al cerrar una sesión con procesos en curso se pide confirmación»); no
perder trabajo por un toque accidental.
**Alternativa:** abrir una pestaña nueva al cerrar la última. Más cómodo, pero contradice «Salir» y
deja el servicio en primer plano sin que el usuario lo pida.

### D-T09-4 · 2026-10-04 · La distro de cada pestaña se registra, pero aún no se usa para arrancar
**Decisión:** una pestaña nueva se abre en la distro predeterminada si está `READY` (`defaultDistroId`)
y, si no hay ninguna, en el shell de Android. Una pulsación larga en «+» ofrece elegir entre el shell
y las distros `READY` (`distroOptions`). El `distroId` queda guardado en la sesión, y «reiniciar» una
sesión terminada la abre en la misma distro. `AndroidSessionFactory` sigue arrancando el shell de
Android sea cual sea la distro: enlazar `ProotCommandBuilder` llega con T07, que es quien instala
distros. «Nueva sesión» de la notificación abre siempre el shell de Android.
**Motivo:** SPEC RF-02/RF-04; dejar el selector y el modelo listos sin inventar un arranque que aún
no se puede probar (no hay distros instaladas).
**Impacto:** cuando T07 enlace el arranque, bastará con que la fábrica lea el `distroId`.

### D-T09-5 · 2026-10-04 · Barra superior o lateral según el ancho, sin dependencia nueva
**Decisión:** `tabBarPlacement(anchoDp)`: lateral (columna de 192 dp) desde 600 dp, superior (48 dp)
por debajo. Es el umbral de la clase «medium» de las guías de Material, calculado a mano. El espacio
de la barra se descuenta del layout del pty con `reserveForTabBar`, igual que la fila de teclas
extra en T04, de modo que `stty size` debería seguir coincidiendo con lo visible.
**Motivo:** `WindowSizeClass` es otra dependencia (Apache-2.0) para una sola comparación.
**Alternativas:** `material3-window-size-class`.
**Pendiente:** la barra lateral va siempre en el borde izquierdo físico, también con idioma de derecha
a izquierda (la terminal es de izquierda a derecha); sin validar en dispositivo.

### D-T09-6 · 2026-10-04 · Atajos de pestañas con efecto real
**Decisión:** `ShortcutHandler` recibe un `TabCommands` y resuelve todos los atajos (ya no existe el
flujo de «atajos sin manejar»). `Alt+n` selecciona la pestaña n contando desde 1 y no hace nada si no
existe (no salta a la última como en los navegadores); `Ctrl+Tab` y `Ctrl+Shift+Tab` avanzan y
retroceden dando la vuelta.
**Aviso:** `Alt+dígito` choca con los argumentos numéricos de readline (ver T11); se mantiene porque lo
pide la SPEC y se puede reasignar.

### D-T09-7 · 2026-10-04 · `TerminalViewModel` pasa a ser `@HiltViewModel`
**Decisión:** recibe `SessionManager` y `DistroRepository` por inyección, en lugar de leer el gestor
de la clase `Application`. `viewModel()` de Compose usa la fábrica de Hilt de la actividad
(`@AndroidEntryPoint`), así que no hace falta `hilt-navigation-compose`. Al cambiar la sesión activa
se vuelve al final del historial y se borra la selección.
**Limitación:** la posición del scroll es del ViewModel, no de cada pestaña: al volver a una pestaña
se empieza en la pantalla viva, no donde se dejó.
**Alternativa:** guardar la posición por sesión (más estado; no se ha pedido).

### D-T09-8 · 2026-10-04 · Qué NO está validado (sin dispositivo)
Todo se probó con tests de host (380 en total, 47 nuevos de pestañas): reducers, reordenación por
arrastre (`dropIndex`), estado de la barra, controlador con el `SessionController` real y los
atajos. No se ha visto la barra en ninguna pantalla. Pendiente en una tablet y un móvil reales:
1. Que los gestos de cada pestaña (toque, doble toque, pulsación larga + arrastre) no se pisen entre
   sí ni con el scroll de la barra, y que el doble toque para renombrar se reconozca sin retrasar el
   toque simple.
2. Los menús desplegables y los diálogos (renombrar, confirmar cierre) y que el teclado no los tape.
3. La barra lateral: ancho, desplazamiento con muchas pestañas y la pantalla dividida (umbral de 600 dp).
4. Que al cambiar de pestaña el pty recibe el tamaño correcto (`stty size` y `SIGWINCH`).
5. Accesibilidad: descripciones de TalkBack («Shell 2, pestaña 2 de 3, en ejecución»), tamaños
   táctiles de 48 dp y el rol de pestaña.
6. El efecto de tener muchas pestañas con el *phantom process killer* de Android 12+ (SPEC §8).

## T10 — Paneles divididos

### D-T10-1 · 2026-10-04 · El árbol de paneles y el foco viven en `Sessions`
**Decisión:** una pestaña con paneles sigue siendo una sesión (la que la abrió); sus paneles extra
son otras sesiones de `items` que no salen en la barra. `Sessions` gana `panes: Map<SessionId,
PaneTab>` (árbol, panel con el teclado y zoom) y `tabs` (lo que muestra la barra). `activeId` pasa
a ser el panel que tiene el teclado. Las operaciones nuevas (`split`, `focused`, `ratioSet`,
`swappedPanes`, `zoomToggled`, `closedPane`) son funciones de extensión; los reducers de T09
(`closed`, `activated`, `switched`, `moved`, `closeAction`) pasan a contar pestañas, no sesiones.
**Motivo:** una sola fuente de verdad inmutable y probada en host; el servicio, la notificación y el
tamaño del pty siguen viendo todas las sesiones sin cambios. Las extensiones evitan superar el
límite de `TooManyFunctions` de detekt (10 funciones en `Sessions`), que no se relaja.
**Alternativas:** un `Workspace` aparte con `Map<tab, árbol>` (dos modelos que sincronizar cuando un
shell termina o se cierra); mover `Sessions` a «pestaña = árbol» (reescribe T09 entero).
**Impacto:** `nextId` pasa de privado a público (hace falta para crear el id del panel nuevo). Con
panes sin dividir todo se comporta como en T09 (los 380 tests de T09 siguen pasando).

### D-T10-2 · 2026-10-04 · Dos modelos: el de ejecución y el guardado
**Decisión:** `PaneNode` (hojas = sesiones vivas) es el árbol de ejecución y `LayoutNode` (T05) sigue
siendo el guardado. `toLayoutNode()` descarta las sesiones y conserva forma y proporciones;
`paneNodeOf(layout) { sesión }` asigna sesiones nuevas en orden de lectura. No cambia el esquema de
Room ni `LayoutCodec` (hay un test de ida y vuelta por JSON).
**Motivo:** el esquema guardado no debe depender de ids de sesión que no sobreviven al proceso.
Guardar y restaurar layouts con nombre, perfiles y comandos por panel es T12b.
**Alternativas:** guardar el `PaneNode` (ids efímeros en disco).

### D-T10-3 · 2026-10-04 · Orientación: nombra la línea que divide
**Decisión:** `HORIZONTAL` es una línea horizontal (el primer panel arriba, el segundo abajo) y
`VERTICAL` una línea vertical (izquierda y derecha), como en Terminator. «Dividir a la derecha» usa
`VERTICAL`; «Dividir hacia abajo», `HORIZONTAL`. El panel nuevo va siempre en la segunda mitad.
**Motivo:** `Layout.kt` (T05) no definía qué significaban; fijarlo ahora evita layouts guardados
ambiguos. **Impacto:** documentado en `PaneNode`; si se prefiere la otra convención, se cambia en un
solo sitio antes de que existan layouts guardados.

### D-T10-4 · 2026-10-04 · Geometría pura: la UI dibuja lo que el dominio calcula
**Decisión:** `paneScene` reparte el área en rectángulos en píxeles enteros (el divisor entre las dos
mitades, el reparto exacto, sin huecos) y `paneLayouts` calcula la rejilla de cada pty con la misma
`terminalLayoutFor` de T04. La UI solo coloca cajas en esos rectángulos. El arrastre se mide contra el
«span» del divisor (`ratioForPointer`) con un tamaño mínimo y ajuste a 50 % (margen de 0,03).
**Motivo:** lo que se ve y el tamaño que recibe el pty salen de la misma función, así que `stty size`
debe coincidir con lo visible (a validar en tablet). Se prueba con tamaños de móvil y tablet.
**Alternativas:** medir cada panel en Compose y avisar al pty desde el `onSizeChanged` (dos fuentes de
verdad y más difícil de probar).

### D-T10-5 · 2026-10-04 · Tamaño mínimo y aviso en vez de bloqueo
**Decisión:** un panel no baja de 20 columnas × 4 filas (`canSplit`, `ratioForPointer`). Dividir donde
no cabe no hace nada y avisa con un toast («No hay espacio suficiente para dividir aquí»); en un
móvil en vertical se puede dividir hasta ese límite. No se usa `WindowSizeClass`: los paneles se
pueden crear en cualquier ancho y la barra de pestañas ya cambia a 600 dp (D-T09-5).
**Motivo:** SPEC RF-02 y la petición del usuario: «en móvil permite splits pero con tamaño mínimo y
avisos». **Alternativas:** impedir los splits en pantallas estrechas.

### D-T10-6 · 2026-10-04 · Tamaños de pty con debounce, también al arrastrar
**Decisión:** el controlador envía el tamaño de cada panel con el mismo `settled(120 ms)` de T04
(ahora genérico) y solo si cambió (`PtySizes`). Al arrastrar un divisor se mueve el dibujo en cada
fotograma, pero cada shell recibe un único `SIGWINCH` cuando el arrastre se detiene. Una pestaña sin
dividir sigue dimensionándose con el área completa (`onLayout`); un panel con zoom ocupa toda el
área. **Motivo:** un `SIGWINCH` por fotograma hace que `vim`/`htop` se redibujen sin parar.
**Impacto:** al dividir, el panel nuevo arranca con el tamaño completo y recibe el suyo ~120 ms
después.

### D-T10-7 · 2026-10-04 · Foco, zoom, intercambio y cierre
**Decisión:** el panel con el teclado lleva un borde (solo si la pestaña está dividida). Tocar otro
panel le da el teclado; los atajos de T11 mueven el foco por geometría (el vecino más cercano de ese
lado, y entre ellos el que más se solapa). Zoom: solo se dibuja el panel enfocado, a toda el área,
sin cerrar los demás. Intercambiar cambia los lugares de dos paneles, no las sesiones. Cerrar un panel
con el shell vivo pide confirmación; el hermano ocupa el espacio y, si era el panel «propio» de la
pestaña, el siguiente en orden de lectura pasa a ser la pestaña conservando su sitio y su nombre.
Cerrar una pestaña cierra todos sus paneles (primero los demás, al final el propio) y la confirmación
cuenta todos los paneles.
**Atajos nuevos** (configurables, T11): Ctrl+Shift+O dividir abajo, Ctrl+Shift+E dividir a la derecha,
Ctrl+Shift+Q cerrar panel (Ctrl+Shift+W sigue cerrando la pestaña), Ctrl+Shift+X zoom y Ctrl+Alt+flecha
para el foco, para que Alt+flecha siga yendo por palabras en readline. Sin Ctrl/Alt no se aceptan.

### D-T10-8 · 2026-10-04 · Lo que NO se hizo (alcance de T12b)
Perfiles por panel, layouts guardados con nombre y comando inicial, atajos editables en la interfaz y
emisión a varios paneles son T12b. «Reiniciar» un panel terminado cierra ese panel y abre una
pestaña nueva (no lo reinicia en su sitio). El desplazamiento y la selección de texto solo existen
en el panel con el teclado; los demás se dibujan en vivo y un toque les da el foco. Reordenar
paneles arrastrándolos tampoco se hizo: se intercambian con el menú del panel o por geometría.

### D-T10-9 · 2026-10-04 · Qué NO está validado (sin dispositivo)
Todo se probó con tests de host (tests nuevos de árbol, geometría, `Sessions`, controladores y
atajos): el árbol, el reparto de píxeles, el foco por geometría, los reducers y que cada pty reciba el
tamaño de su panel. No se ha visto ninguna pantalla con paneles. Pendiente en una tablet y un móvil
reales:
1. Que `stty size` coincida con lo visible en cada panel tras dividir, cerrar, arrastrar y hacer zoom.
2. El arrastre del divisor (zona táctil de 48 dp) y que no compita con el scroll del panel.
3. Los gestos del panel enfocado (scroll, selección, pellizco) y el toque para dar el foco.
4. El menú «⋮» y el diálogo de cierre, y que el teclado en pantalla no los tape.
5. El rendimiento al dibujar varios paneles a la vez (el pintor compartido dibuja uno tras otro).
6. TalkBack: las descripciones de panel y de separador.
7. El efecto de tener muchos shells con el *phantom process killer* de Android 12+.

## T12 — Temas, modo OLED y fuentes

### D-T12-1 · 2026-10-04 · Fuente: JetBrains Mono 2.304, incluida sin modificar
- **Decisión:** el terminal usa JetBrains Mono (Regular, Bold, Italic, Bold Italic) como fuente incluida, en `res/font`.
- **Motivo:** licencia SIL OFL-1.1 verificada en el repositorio oficial (`Copyright 2020 The JetBrains Mono Project Authors`), compatible con distribuir la fuente dentro de una app GPL-3.0-or-later siempre que no se venda suelta y se incluya el texto de la licencia. Sin *Reserved Font Name*.
- **Verificado:** los cuatro `.ttf` son idénticos byte a byte (SHA-256) a los de la release oficial `v2.304`. El texto de la OFL y la lista de autores van en el APK (`assets/licenses/`).
- **Alternativas:** Fira Code, Hack o una Nerd Font (las Nerd Fonts son parches de otras fuentes con licencias mezcladas: más trabajo de verificar). **Impacto:** unos 1,1 MB más en el APK. **Pendiente:** las Nerd Fonts / iconos de powerline, por si el usuario los quiere (no es MVP).

### D-T12-2 · 2026-10-04 · Esquemas incluidos y por qué NO está Tango
- **Decisión:** vienen Dracula (predeterminado), Solarized Dark, Solarized Light, Gruvbox Dark, Nord y un esquema OLED propio. Se acreditan en `THIRD_PARTY_NOTICES.md` y con el texto MIT completo en `assets/licenses/ColorSchemes-MIT.txt`.
- **Verificado en los repositorios oficiales:** Solarized (© 2011 Ethan Schoonover, MIT), Dracula (© 2023 Dracula Theme, MIT), Nord (© 2016-presente Sven Greb, MIT) y Gruvbox (© Pavel Pertsev; MIT/X11 según su README y `package.json`, aunque el repositorio no trae un fichero `LICENSE`).
- **Tango, retirado:** el primer borrador lo incluía como predeterminado diciendo «dominio público». **No pude verificar esa afirmación**: las guías de Tango se publican bajo CC BY-SA 2.5, que no es compatible con GPLv3, y no encontré una declaración clara para la paleta. Al no poder comprobarlo y querer evitar reclamaciones, se quitó. Si más adelante se verifica una fuente clara de licencia, se puede añadir.
- **Cambios a las paletas:** retoques mínimos de legibilidad, documentados junto a cada esquema en `BuiltInSchemes.kt` y exigidos por el test de contraste: Solarized Light (verde, amarillo y cian 1–2 % más oscuros: los oficiales dan 2,9–3,0:1) y Gruvbox Dark (rojo normal más claro: el oficial `CC241D` da 2,7:1). Los esquemas MIT permiten modificar con atribución.
- **Impacto:** el esquema predeterminado cambia a Dracula. Un usuario que importe un esquema responde de su origen.

### D-T12-3 · 2026-10-04 · Modo OLED = variante del tema oscuro
- **Decisión:** `resolveTheme` solo activa OLED cuando el tema resultante es oscuro (`oled = oledBlack && dark`). En OLED, un esquema oscuro pasa a fondo `#000000` puro (`forOled()`); uno claro no se toca.
- **Motivo:** apagar los píxeles solo tiene sentido en oscuro; un fondo negro con un esquema claro dejaría texto ilegible.
- **Impacto:** la interfaz Material y la vista del terminal usan la misma decisión.

### D-T12-4 · 2026-10-04 · El esquema se aplica a todas las sesiones, también a las futuras
- **Decisión:** `SessionManager.applyScheme` delega en `AndroidSessionFactory`, que recuerda el esquema, lo aplica a todos los hosts en marcha y a cada host nuevo antes de arrancar su emulador.
- **Motivo:** con varias pestañas (T09) un esquema por host dejaría pestañas con colores antiguos. La librería de Termux lee sus colores iniciales de una paleta estática compartida; se escribe ahí y se reinician los colores del emulador activo (los que un programa fijó con OSC 4/10/11 se descartan: el esquema nuevo manda).
- **Impacto:** la integración con los cambios de T08/T09 se rehízo sobre `master`; el diseño de H, que asumía un único host, no valía tal cual.

### D-T12-5 · 2026-10-04 · No se dibuja nada hasta tener los ajustes
- **Decisión:** `MainActivity` espera a que lleguen los ajustes guardados (`produceState`) antes de componer la pantalla.
- **Motivo:** evita un primer fotograma con el esquema y tamaño de fuente por defecto que luego salte a los guardados.
- **Alternativa:** una pantalla de carga; descartada por ser un instante. **Riesgo:** si el repositorio tardara, se vería la ventana vacía; no medido.

### D-T12-6 · 2026-10-04 · Persistencia y tamaño de fuente
- **Decisión:** tema, modo OLED, colores dinámicos, esquema elegido, esquemas importados y tamaño de fuente viven en el repositorio de ajustes de T05 (clave-valor). El tamaño del zoom de T11 se restaura una vez y solo se guardan los cambios del usuario, con un *debounce* de 500 ms (un pellizco genera muchos valores).
- **Importación/exportación:** JSON versionado (`version: 1`) con `kotlinx.serialization`, probado con ida y vuelta. Un fichero de una versión desconocida se rechaza. Un test cazó que, con `encodeDefaults` apagado, `kotlinx.serialization` omitía el campo `version` al exportar (por tener valor por defecto): los ficheros no llevaban versión. Se activó `encodeDefaults`.

### D-T12-7 · 2026-10-04 · Contraste mínimo comprobado en tests
- **Decisión:** cada esquema incluido debe cumplir: texto sobre fondo ≥ 4,5:1 (WCAG AA), cursor ≥ 3:1, texto sobre selección ≥ 3:1, cada color ANSI sobre el fondo ≥ 3:1 y el «negro brillante» ≥ 1,5:1; y lo mismo en su variante OLED.
- **Motivo:** una paleta con colores ilegibles es un fallo de accesibilidad; el test lo caza antes de publicar.
- **Nota:** el umbral de 1,5:1 del negro brillante es deliberadamente bajo: en muchos esquemas es el color de texto «atenuado» y subirlo lo desvirtúa.

### D-T12-8 · 2026-10-04 · Las barras del sistema siguen al esquema
- **Decisión:** los iconos de las barras de estado y navegación se eligen según el fondo del **esquema** (`SystemBarStyle.auto`), no según el tema del sistema, porque el terminal se dibuja bajo las barras.

### Pendiente de validar en dispositivo (T12)
Nada de esto se ha visto en pantalla; solo hay tests de host.
1. Que los colores se ven bien de verdad: contraste real con brillo bajo y al sol, y que OLED apaga los píxeles.
2. Que el cambio de esquema en caliente repinta todas las pestañas sin reiniciarlas, y los colores que un programa fijó con OSC.
3. El renderizado de JetBrains Mono: negrita/cursiva con las cuatro variantes, ancho de celda correcto, y que no cambia el tamaño de la rejilla (`stty size`) respecto a la fuente del sistema.
4. Iconos de las barras del sistema con esquemas claros y oscuros.
5. La pantalla de ajustes para elegir esquema/tema aún no existe (T16): hoy solo se cambian por el repositorio de ajustes.

## T14 — Hosts SSH y claves

Alcance: lista de hosts guardados con alta/edición/borrado, conexión con un toque en una pestaña
nueva, y gestión de claves (generar, importar, exportar, borrar) cifradas en reposo. Todo se ha
probado solo en la JVM del host; ver "T14: lo que NO se ha validado" al final.

### D-T14-1 · 2026-10-04 · `ssh` se lanza como vector de argumentos, sin shell
**Decisión:** `SshCommand.build` devuelve una lista de argumentos (`ssh -o ServerAliveInterval=30 …
-p <puerto> [-i <clave> -o IdentitiesOnly=yes] -- usuario@host`) que proot ejecuta directamente. No
hay ninguna cadena de shell, así que no hace falta escapar nada: no existe el intérprete que lo
leería. Aun así se validan host, usuario y puerto (`Validation`: un host que empieza por `-` se
leería como opción de `ssh`; no se aceptan espacios ni metacaracteres), `--` termina las opciones
antes del destino y la ruta de la clave solo puede ser `/tmp/.ut-ssh-<hex>`.
**Motivo:** una inyección de comandos a través del nombre de un host guardado (o importado de una
copia de seguridad) sería la vulnerabilidad más obvia de esta función. Eliminar el shell es más
robusto que escapar bien.
**Alternativas:** `sh -c "ssh …"` con escapado de comillas (un solo fallo de escapado es una
ejecución de comandos); `ProxyCommand`/`LocalCommand` quedan fuera: no se emite ninguna opción
`-o` que ejecute programas.
**Impacto:** hay tests con entradas hostiles (`;`, `$(…)`, comillas, saltos de línea, `-oProxyCommand=…`,
rutas con `..`). El nombre del host guardado es solo una etiqueta y nunca llega a la línea de comandos.

### D-T14-2 · 2026-10-04 · Formato de claves: OpenSSH propio, sin librerías nuevas
**Decisión:** el formato `openssh-key-v1` (clave privada sin passphrase) y la línea `authorized_keys`
se escriben y leen en `OpenSshKeyFormat`, para Ed25519 y RSA. La importación acepta además PKCS#8 y
PKCS#1 (solo RSA, que son las claves antiguas habituales) en `PrivateKeyText`. Una clave con passphrase
se rechaza con un mensaje que explica cómo quitarla (`ssh-keygen -p`). No se admiten ECDSA, DSA ni
certificados, ni Ed25519 en PKCS#8.
**Motivo:** `ssh` necesita ese formato y la API de criptografía de la plataforma no lo produce. Es
poco código (unos 200 líneas) y se prueba contra claves reales de `ssh-keygen`; BouncyCastle añadiría
~2 MB y una licencia más para algo que no necesitamos.
**Alternativas:** BouncyCastle (descartada por peso); generar las claves dentro de la distro con
`ssh-keygen` (la clave nacería en texto plano en el rootfs, justo lo que se quiere evitar).
**Impacto:** Ed25519 en PKCS#8 no se puede importar porque habría que derivar la clave pública de la
semilla y el JDK no lo expone. Quien tenga una clave así la convierte con `ssh-keygen -p -m RFC4716`.

### D-T14-3 · 2026-10-04 · Generación con la criptografía de la plataforma; Ed25519 solo desde Android 13
**Decisión:** `JcaSshKeyGenerator` usa `KeyPairGenerator` (RSA de 3072 bits en todas las versiones;
Ed25519 donde la plataforma lo tiene, Android 13 o posterior). La pantalla solo ofrece los tipos que
`supportedTypes` dice que el dispositivo soporta. La generación RSA corre fuera del hilo principal.
**Motivo:** `minSdk` 26 no tiene Ed25519 en `java.security`. RSA 3072 tarda unos segundos en un móvil;
4096 podía tardar medio minuto.
**Alternativas:** BouncyCastle para Ed25519 en API 26–32 (ver D-T14-2); ofrecer solo RSA.
**Impacto:** en Android 8–12 el único tipo para generar es RSA. Importar Ed25519 funciona en todas.

### D-T14-4 · 2026-10-04 · Cifrado en reposo: AES-256-GCM con una clave del Android Keystore
**Decisión:** la clave privada (texto OpenSSH) se sella con `AesGcmSecretBox`: AES-256-GCM, IV de 12
bytes aleatorio, formato `versión | longitud del IV | IV | texto cifrado + etiqueta`. El alias de la
clave va como dato autenticado, así que un blob no se puede mover a otra clave. La clave AES está en
el Android Keystore (`KeystoreSecretKey`, no exportable). Cada clave son dos ficheros en
`files/storage/ssh-keys/`: `<alias>.key` (sellado, 0600) y `<alias>.meta` (JSON con nombre, tipo, clave
pública y huella; el alias es el nombre del fichero). El `.meta` se escribe el último: una clave cuya
escritura se interrumpió no aparece en la lista.
**Motivo:** el Keystore impide sacar la clave del dispositivo, así que un volcado del almacenamiento (o
una copia de seguridad) no revela las claves privadas. El formato y las comprobaciones de integridad
se prueban en la JVM con una clave en memoria; solo el Keystore real necesita un dispositivo.
**Alternativas:** `EncryptedFile`/Jetpack Security (obsoleto); exigir autenticación biométrica para
cada uso (`setUserAuthenticationRequired`): mejor seguridad pero rompe la conexión de un toque.
**Impacto:** como la clave del Keystore no sale del dispositivo, las claves de SSH **no se pueden
restaurar tal cual en otro móvil** con la copia de seguridad (T15). Habrá que exportarlas (descifradas,
con aviso) o cifrarlas con una contraseña del usuario, como ya hace Deck con las sesiones.

### D-T14-5 · 2026-10-04 · Cómo llega la clave a `ssh`: un fichero temporal 0600 en el rootfs
**Decisión:** al conectar, `SshConnector` descifra la clave y la escribe en
`<rootfs>/tmp/.ut-ssh-<16 hex aleatorios>` (solo legible por la app, escritura atómica), pasa
`-i /tmp/.ut-ssh-…` a `ssh` y borra el fichero cuando la sesión termina o se cierra. Los ficheros que
deje un cierre brusco se borran al iniciar la siguiente conexión, salvo los de sesiones vivas. Las
rutas pasan por `FileTrees.resolveInside`, que rechaza un enlace simbólico en el camino: el `/tmp` de un
rootfs ajeno no puede apuntar fuera del almacenamiento.
**Motivo:** `ssh -i` necesita un fichero. Es lo más simple que funciona con cualquier distro.
**Alternativas:** `ssh-agent` dentro de la distro con `ssh-add -` leyendo la clave por la entrada
estándar (la clave no llegaría a un fichero, pero hace falta orquestar el agente y su socket en cada
pestaña); una FIFO (`ssh` abre la clave más de una vez); la variable de entorno (visible en
`/proc/<pid>/environ`). El agente es la mejora natural si el modelo de amenazas lo pide.
**Impacto:** la clave está en claro en el almacenamiento privado mientras dura la sesión (ver D-T14-6).

### D-T14-6 · 2026-10-04 · Modelo de amenazas
**Protege contra:** un volcado del almacenamiento o una copia de seguridad del sistema con la app
cerrada (las claves están selladas con una clave que no sale del Keystore); una clave que acabe en
un log (los tipos de clave no imprimen sus campos, y la interfaz solo recibe partes públicas); un host
con caracteres de shell o que se lea como opción de `ssh` (D-T14-1); un `/tmp` enlazado fuera del
almacenamiento; borrar una clave que aún usa un host.
**No protege contra:** otra app con el mismo UID (ninguna) o con root en el dispositivo, que pueden leer
el fichero temporal de la clave mientras la sesión está abierta o la memoria del proceso; un dispositivo
desbloqueado en manos de otra persona (no se pide autenticación para usar la clave, ver D-T14-4);
programas dentro de la distro, que pueden leer `/tmp/.ut-ssh-…` mientras dura la sesión; que el
usuario guarde la clave privada exportada en un sitio inseguro (la pantalla avisa antes).
**Impacto:** es un nivel parecido al de `~/.ssh/id_*` sin passphrase en cualquier Linux, con la
ventaja de que en reposo está cifrada.

### D-T14-7 · 2026-10-04 · Hueco encontrado: nada conectaba proot con las sesiones
**Decisión:** T09 y T07 dejaron el `distroId` guardado en cada pestaña, pero `AndroidSessionFactory`
solo sabía arrancar el shell de Android; `ProotCommandBuilder` (T02) no estaba en la inyección. T14 lo
necesita para ejecutar `ssh` en una distro, así que añade el mínimo: `SessionLaunch` (comando, entorno
y limpieza), `LaunchingSessionFactory` (una factoría que además sabe ejecutar un comando),
`SessionController.newSession(distroId, launch)` y `DistroLaunchFactory`, que arma la línea de proot.
`ProotCommandBuilder` queda inyectado en `SshModule`.
**Motivo:** sin esto el botón "Conectar" no podría abrir nada.
**Alternativas:** cambiar la firma de `SessionFactory` (rompía los tests de T08 y T09).
**Impacto:** **las pestañas normales de una distro siguen abriendo el shell de Android**: lanzar proot
en una pestaña sin comando es la misma pieza pero no se ha conectado al botón "+" de la barra
(`TabsController.newTabIn`) para no tocar T09. Queda como tarea pendiente y es lo primero que habrá
que hacer para que las distros sirvan para algo más que `ssh`. Tampoco hay "reiniciar" para una
pestaña de `ssh`: `restartActive` abre un shell de Android.

### D-T14-8 · 2026-10-04 · DNS dentro de proot
**Decisión:** antes de conectar, `SshConnector` escribe `/etc/resolv.conf` del rootfs con
`1.1.1.1` y `9.9.9.9` si falta o está vacío. No toca uno que el usuario haya configurado.
**Motivo:** Android no tiene un `resolv.conf` que compartir y el de un rootfs recién instalado no
funciona (el de Ubuntu es un enlace a un fichero que no existe), así que `ssh servidor.ejemplo`
fallaría al resolver el nombre aunque la red funcione.
**Alternativas:** leer los DNS del sistema (una app no puede, desde Android 8); montar uno propio con
`-b`; no hacerlo (solo funcionarían las IP).
**Impacto:** hay una decisión de privacidad: los nombres se resuelven con esos resolutores públicos,
no con los de la red del usuario. Es solo para la distro. **Debería ser configurable** (T16).
**Sustituida por D-T08b-6 (2026-10-04):** ahora hay un único mecanismo para todas las pestañas. Se usan los
DNS de la red activa y `1.1.1.1`/`9.9.9.9` solo como último recurso. `SshConnector` ya no escribe nada en el
rootfs. Nota sobre la alternativa descartada aquí: una app sí puede leer los DNS del sistema con
`ConnectivityManager.getLinkProperties` (lo que no puede desde Android 8 es leer `net.dns1` con `getprop`).

### D-T14-9 · 2026-10-04 · Pantallas, ViewModels y punto de entrada
**Decisión:** dos pantallas (hosts y claves) con un ViewModel cada una (`SshViewModel`,
`SshKeysViewModel`); la lista de claves de la pantalla de hosts es de solo lectura. La exportación de
la clave privada y la importación desde fichero usan el selector del sistema (`CreateDocument` y
`OpenDocument`), con un aviso antes de guardar la privada. La clave pública se copia al portapapeles.
Una clave en uso por algún host no se puede borrar (`KeyInUse`). Botón "SSH" provisional junto al de
"Distros" en `MainActivity`; debe ir al menú de "nueva pestaña" que T09 posee (como D-T07-7).
**Motivo:** el límite de funciones por clase de detekt, y que cada pantalla tenga un solo trabajo.
**Alternativas:** un único ViewModel (superaba el límite de funciones).
**Impacto:** hosts sin clave abren `ssh` normal y piden la contraseña en la terminal.

### D-T14-10 · 2026-10-04 · `known_hosts` y verificación de la clave del servidor
**Decisión:** no se toca. `ssh` pregunta por la huella la primera vez y guarda `known_hosts` en
`~/.ssh` del rootfs, como en cualquier Linux. No se pasa `StrictHostKeyChecking=no`.
**Motivo:** desactivarlo anularía la protección contra un servidor suplantado.
**Impacto:** el historial vive en el rootfs, así que viaja con la copia de seguridad de la distro.

### D-T14-11 · 2026-10-04 · Verificado contra OpenSSH real, en el host
**Decisión:** además de los tests unitarios, el formato de claves se contrastó con `ssh-keygen` y
`sshd` de OpenSSH 10.2 en la máquina de desarrollo (no en un dispositivo): se leyeron claves
generadas por `ssh-keygen` (OpenSSH Ed25519 y RSA, PKCS#1, PKCS#8) y las huellas coincidieron con
`ssh-keygen -l`; `ssh-keygen -y` aceptó las copias que escribe `OpenSshKeyFormat` y dedujo la misma
clave pública; las claves Ed25519 y RSA 3072 generadas por `JcaSshKeyGenerator` fueron aceptadas por
`ssh-keygen`, y un `sshd` de usuario en el puerto 2222 de loopback las admitió con la misma forma de
comando que construye `SshCommand` (`-p … -i … -o IdentitiesOnly=yes -- usuario@host`) y rechazó una
clave ajena. Una clave ECDSA y las dos con passphrase (PEM y OpenSSH) se rechazaron con el error
esperado.
**Motivo:** que un formato "parezca" correcto no basta: solo `ssh` real dice si lo acepta.
**Impacto:** no se commitean claves de prueba (un escáner de secretos las marcaría); el contraste se
hizo con claves desechables y no es un test automático. Los tests unitarios cubren el round trip,
la corrupción y los casos hostiles; si cambia el formato hay que repetir esta comprobación a mano.

### T14: lo que NO se ha validado (sin dispositivo)
- **Keystore real:** que `KeystoreSecretKey` cree la clave AES-256 y que `AesGcmSecretBox` selle y
  abra con ella en Android 8 a 16 (el formato se probó con una clave en memoria, no con el Keystore).
- **Generación en el móvil:** el tiempo real de RSA 3072 en un móvil de gama baja, y que Ed25519 esté
  disponible en Android 13 o posterior (`supportedTypes` lo decide en tiempo de ejecución).
- **`ssh` dentro de proot:** que la distro tenga `openssh-client` (no se instala; el usuario debe hacer
  `apt install openssh-client` o `apk add openssh`; la pantalla no lo avisa todavía), que proot ejecute
  `ssh` con la línea de `DistroLaunchFactory`, que `-i` acepte el fichero 0600 cuando proot simula root
  (propietario y permisos vistos desde dentro) y que la red y el DNS funcionen (D-T14-8).
- **Limpieza de la clave:** que el fichero temporal desaparezca al cerrar la pestaña y al morir el
  proceso, y que los restos se borren en la siguiente conexión.
- **Selector de archivos del sistema** (importar y exportar) y el portapapeles; la rotación con los
  diálogos abiertos (`HostDraft` se guarda con `listSaver`); TalkBack y los 48 dp.
- **Pestañas normales de una distro**: siguen abriendo el shell de Android (D-T14-7).

## T13 — Acceso a los archivos del dispositivo

### D-T13-1 · 2026-10-04 · Qué se monta y dónde
- **Decisión:** con la función activada, cada sesión de proot recibe `~/storage/shared` (todo el almacenamiento compartido) y atajos a las carpetas habituales que existan en el dispositivo: `downloads` (Download), `dcim`, `documents`, `pictures`, `music` y `movies`. El origen es `Environment.getExternalStorageDirectory()` (normalmente `/storage/emulated/0`), no `/sdcard`, que es un enlace simbólico.
- **Motivo:** `~/storage/downloads` es lo que pide la SPEC (RF-05) y `shared` cubre cualquier otra carpeta sin tener que listarlas todas. Solo se montan las carpetas que existen para no enseñar atajos rotos.
- **Alternativas:** montar solo Descargas (poco útil); montar `/sdcard` directamente en `~/storage` (no deja sitio para los atajos).
- **Impacto:** el cálculo vive en `domain/storage` (`SharedStoragePlanner`) y se aplica con `ProotSession.withSharedStorage`.

### D-T13-2 · 2026-10-04 · Ajuste global, no por distro
- **Decisión:** un solo interruptor (`AppSettings.sharedStorage`, clave `shared_storage`, desactivado por defecto) para todas las distros.
- **Motivo:** la SPEC dice «por distro/ajuste»; el permiso de Android es del app entero, así que un interruptor por distro solo añadiría una pantalla sin dar más control real.
- **Alternativas:** una columna en la tabla de distros (migración de Room y pantalla por distro). Se puede añadir más tarde sin romper nada.
- **Impacto:** la clave entra en el backup de ajustes (T15) con el resto.

### D-T13-3 · 2026-10-04 · Permiso: READ/WRITE_EXTERNAL_STORAGE, sin MANAGE_EXTERNAL_STORAGE (NO verificado en hardware)
- **Decisión:** al activar el interruptor se piden `READ_EXTERNAL_STORAGE` y `WRITE_EXTERNAL_STORAGE`, y solo entonces. No se declara ni se pide `MANAGE_EXTERNAL_STORAGE`. Si el usuario ya concedió «acceso a todos los archivos» en los ajustes del sistema, también se acepta (`Environment.isExternalStorageManager()`).
- **Motivo (hipótesis, sin comprobar en un dispositivo):** la app apunta a la API 28 a propósito (SPEC §2). A esa versión Android la trata como app con almacenamiento «clásico» y deja que un proceso lea y escriba por ruta con el permiso de siempre; es el modelo de Termux, que también apunta a 28. Pedir `MANAGE_EXTERNAL_STORAGE` obligaría a mandar al usuario a una pantalla de ajustes, es un permiso muy amplio y Google Play lo restringe; no hace falta si lo anterior funciona.
- **Riesgos conocidos:** (1) en Android 13 y posteriores los permisos de almacenamiento se dividieron por tipo de medio y puede que, para una app que apunte a una versión anterior, el sistema solo conceda fotos, vídeo y audio y no Descargas ni Documentos; (2) desde Android 11 `Android/data` y `Android/obb` quedan fuera de alcance; (3) el sistema de archivos del almacenamiento compartido no admite enlaces simbólicos, bits de ejecución ni `chmod`, así que `ln -s`, `chmod +x` y los ejecutables dentro de `~/storage` fallarán o no tendrán efecto (igual que en Termux), y `--link2symlink` no puede emular enlaces duros allí.
- **Plan si falla:** ofrecer «acceso a todos los archivos» con `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`. El código ya lo reconoce como permiso concedido; solo habría que declarar el permiso y añadir el botón. Queda pendiente de decidir tras probarlo en una tablet real.
- **Para PRIVACY.md (T21):** el permiso solo se pide cuando el usuario activa la función; sirve para que las distros lean y escriban sus carpetas compartidas; ningún dato sale del dispositivo por ello. Ojo: el permiso está **declarado** en el manifiesto aunque la función esté apagada, y por eso aparece en la lista de permisos de la app.

### D-T13-4 · 2026-10-04 · Degradar, no fallar
- **Decisión:** si el permiso no está concedido, el almacenamiento no está montado o no se pueden crear los puntos de montaje, el plan queda como `Degraded(motivo)`, la sesión arranca igual y sin `~/storage`. Con la función apagada ni siquiera se consulta el permiso.
- **Motivo:** un terminal que no abre por un permiso revocado es peor que uno sin `~/storage`.
- **Impacto:** el aviso al usuario hoy es el texto bajo el interruptor («permiso no concedido»). El aviso al abrir una sesión llegará cuando el arranque de proot se conecte (ver siguiente punto).
- **Importante, sin conectar:** todavía no hay ningún sitio que lance proot con una distro (T07 instala y T08 gestiona sesiones con el shell de Android). `SharedStorageMounts.prepare(...)` y `withSharedStorage(...)` están listos y probados, pero **nadie los llama aún**. Quien conecte proot a las sesiones debe usarlos; hasta entonces el interruptor se guarda pero no tiene efecto en una sesión.

### D-T13-5 · 2026-10-04 · Los puntos de montaje se crean por el repositorio de ficheros
- **Decisión:** los directorios vacíos donde proot monta (`<distro>/rootfs/root/storage/...`) se crean con `FileSystemRepository.createDirectories` y rutas validadas `FsPath`, en cada arranque (es idempotente). Se hace para que `ls ~/storage` los muestre: proot no inventa entradas en un directorio.
- **Impacto:** no hay `java.io.File` en `domain` ni `ui`. Se hizo público `DistroInstaller.UNPACKED_NAME` (`rootfs`) para no duplicar la constante (cambio mínimo en código de T07).

### D-T13-6 · 2026-10-04 · El código de Android va en `platform`, fuera de la cobertura
- **Decisión:** `AndroidSharedStorageAccess` (permiso, ruta, `File.isDirectory`) está en `com.qtekfun.ultimateterminal.platform`, sin lógica propia. Kover solo mide `domain` y `data`, así que no cuenta; la lógica que decide está toda en `domain.storage` con tests.
- **Motivo:** no se puede ejecutar en la JVM del host y un test que lo simule solo probaría el simulacro.
- **Nota:** `Environment.getExternalStorageDirectory()` está obsoleta (API 29) y se usa con `@Suppress("DEPRECATION")` porque dar una ruta a un proceso nativo es justo lo que `targetSdk` 28 permite.

### T13: lo que NO se ha validado (sin dispositivo)
1. Que el diálogo de permiso salga y que, concedido, `/storage/emulated/0` se pueda leer y escribir desde proot (ver D-T13-3 y sus riesgos), en la tablet (API 31) y en Android 13 o posterior.
2. El criterio de la SPEC: `cp /var/log/syslog ~/storage/downloads/` aparece en Descargas.
3. Que proot monte (`-b`) esas rutas con `--link2symlink` y `--kill-on-exit` sin error, y que `ls ~/storage` muestre los puntos de montaje.
4. La pantalla: el interruptor, el cambio del permiso en los ajustes del sistema mientras la app está en segundo plano y el botón «Abrir ajustes de la app».
5. Que desactivar el interruptor no deje montajes colgados en una sesión ya abierta (cambia al abrir la siguiente).

## T08b — Conectar proot a las sesiones

Contexto: tras T07 (instalar distros), T09 (pestañas) y T13 (montajes) nadie lanzaba proot: la fábrica de sesiones
arrancaba siempre `/system/bin/sh`. T08b une las piezas. **Nada de esto se ha ejecutado en un dispositivo**: todas las
decisiones sobre el comportamiento real de proot son hipótesis hasta la primera prueba en la tablet (D-T08b-8).

### D-T08b-1 · 2026-10-04 · Un planificador puro decide; la fábrica solo ejecuta
- **Decisión:** `ProotSessionPlanner` (`data/proot`) recibe el `distroId` de la pestaña (null = shell de Android) y devuelve un `LaunchPlan`: `AndroidShell`, `FallbackToAndroid(aviso)`, `InDistro(proot, aviso?)` o `Failed(problema)`. No toca procesos. `AndroidSessionFactory` lo ejecuta. Solo se usa cuando el que abre la pestaña **no** trae un `SessionLaunch` propio (ver D-T08b-9).
- **Motivo:** todas las reglas (qué distro, qué usuario, qué montajes, qué errores) quedan probadas en la JVM. Lo único que necesita un dispositivo es el `fork/exec`.
- **Alternativas:** decidir dentro de `TerminalSessionHost` (imposible de probar sin PTY); un `runBlocking` en el hilo principal (bloquearía la UI al consultar Room y el disco).
- **Impacto:** `data/proot` pasa a cubrir también la política de arranque (Kover ≥85 % en `data`). El planificador se inyecta por constructor; los puertos del dispositivo (`ProotRuntime`, `ResolvConfSource`) tienen su implementación Android en `platform/`, sin lógica propia.

### D-T08b-2 · 2026-10-04 · El arranque es asíncrono: el host espera al plan y al tamaño
- **Decisión:** `SessionFactory.start` no cambia de firma y devuelve el manejador al instante. El host recuerda el tamaño que pide la pantalla; la fábrica lanza una corrutina (hilo principal, que es donde la librería del emulador entrega sus callbacks) que obtiene el plan y llama a `host.launch(...)`. El PTY se crea cuando se conocen **las dos cosas**: qué ejecutar y qué tamaño tiene.
- **Motivo:** el plan necesita Room y el disco (suspend). Evita tocar la interfaz `SessionFactory` y sus fakes de test. El distro de la pestaña se lee del estado que el controlador ya publica antes de llamar a la fábrica, y hay un test que lo fija. La fábrica implementa `LaunchingSessionFactory` (de T14), así que la firma que ve el controlador no cambia.
- **Alternativas:** añadir el `ShellRequest` a `SessionFactory.start` (rompe los fakes y no resuelve la parte suspend).
- **Impacto:** `TerminalSessionHost.stop()` olvida lo que había que ejecutar; «reiniciar» abre una sesión nueva (ya era así). Un `Handle.stop()` cancela la corrutina, así que una pestaña cerrada mientras arrancaba no deja nada.

### D-T08b-3 · 2026-10-04 · Qué se hace cuando no se puede abrir la distro
- **Decisión:** sin distro pedida → shell de Android sin aviso (es la elección explícita «Android shell»). Distro pedida que no está lista o ya no existe → **shell de Android con un aviso descartable** y acceso a la pantalla de distros. Distro lista pero sin sus archivos, proot ausente, usuario no válido o sin carpeta temporal → **error explicado** (`LaunchProblem`) y la sesión termina como fallida; nunca una excepción.
- **Una pestaña con `SessionLaunch` propio (ssh) no pasa por aquí**, así que nunca cae al shell de Android: si no puede arrancar, falla con su propio mensaje.
- **Motivo:** la SPEC pide «sin distro lista, fallback al shell de Android» y «errores sellados, nunca un crash». Se separa «no hay distro todavía» (normal en un primer arranque) de «algo está roto».
- **Impacto:** el aviso se dibuja encima de las primeras filas (no cambia el tamaño del PTY) y los textos están en inglés y español.

### D-T08b-4 · 2026-10-04 · Pestaña por defecto: la distro predeterminada si está lista
- **Decisión:** al arrancar la app y desde la notificación, `SessionManager.newDefaultSession()` consulta la distro predeterminada y abre en ella solo si está `READY`; si no, shell de Android. El botón «+» ya hacía lo mismo.
- **Motivo:** antes el arranque pasaba siempre `null` (shell de Android) aunque hubiera una distro predeterminada.
- **Alternativas:** abrir siempre el shell de Android (ignora la distro predeterminada).

### D-T08b-5 · 2026-10-04 · Usuario de la distro: `su -l`, con el nombre validado
- **Decisión:** `root` ejecuta el shell directamente (`-0`). Otro usuario pasa por `su -l <usuario>`. El nombre se valida con `[a-z_][a-z0-9_-]{0,31}`: un nombre que empiece por `-` se leería como opción de `su`. Un comando (`ProotSession.command`, lo usa la conexión SSH) es una **lista de argumentos**, nunca una línea de shell, y corre con la identidad de proot (root), no con la del usuario.
- **Motivo:** el nombre llega de la base de datos; validarlo en el planificador protege aunque una fila antigua no lo estuviera. Hay tests con nombres hostiles.
- **Hipótesis sin validar:** que `su -l` funcione con proot y `-0` en Debian, Ubuntu y Alpine (busybox `su`), y que el usuario exista en la distro; si no existe, el shell termina con error de `su`.
- **Límite:** `/bin/sh -l` es el shell de login por defecto en las tres distros (dash en Debian/Ubuntu, ash en Alpine); no se elige `bash` aunque exista.

### D-T08b-6 · 2026-10-04 · `/etc/resolv.conf` desde los DNS del dispositivo, con permiso `ACCESS_NETWORK_STATE`
- **Decisión:** la app escribe `filesDir/resolv.conf` con los DNS de la red activa (`ConnectivityManager`) y lo monta con `-b` sobre `/etc/resolv.conf` de la distro. Solo se admiten literales IPv4/IPv6 (sin zona ni nombres ni saltos de línea) y como máximo tres (límite de glibc). Si el dispositivo no informa de ninguno, se usan `1.1.1.1` y `9.9.9.9`. Si no se puede escribir el fichero, la sesión arranca igual con un aviso.
- **Motivo:** Android no tiene `/etc/resolv.conf` y el de un rootfs apunta a un resolvedor inexistente. No se depende de rutas de Termux. Montar un fichero evita modificar el rootfs.
- **Un solo mecanismo (unificado con T14):** lo usan las pestañas normales (`ProotSessionPlanner`) y la conexión SSH (`DistroLaunchFactory`) por igual, a través de `ResolvConfSource` y `resolvConfBinds`. `SshConnector.ensureResolver` y su decisión D-T14-8 (escribir `1.1.1.1`/`9.9.9.9` en el rootfs) desaparecen. Los DNS del dispositivo van primero; los públicos son **último recurso**, constante `ResolvConf.FALLBACK_SERVERS`, para que T16 los haga configurables.
- **Privacidad (para `PRIVACY.md`, T21):** los DNS de reserva son resolvedores públicos de terceros (Cloudflare y Quad9); solo se usan cuando el dispositivo no da ninguno. `ACCESS_NETWORK_STATE` es un permiso normal (no se pide al usuario) y solo sirve para leer esos servidores.
- **Alternativas:** escribir `8.8.8.8` siempre (envía todo a un tercero aunque la red tenga DNS propio, y rompe DNS internos); copiar el `/etc/resolv.conf` del sistema (no existe desde Android 8).
- **Sin validar:** que `getLinkProperties(activeNetwork)` devuelva servidores en todas las redes (VPN, datos móviles) y que el bind de un fichero sobre `/etc/resolv.conf` funcione con proot.

### D-T08b-7 · 2026-10-04 · Modo de compatibilidad (proot sin seccomp) como ajuste visible
- **Decisión:** `AppSettings.prootCompatibilityMode` (clave `proot_compatibility_mode`, desactivado) pone `PROOT_NO_SECCOMP=1`. Hay un interruptor en la pantalla de distros y se aplica a las pestañas que se abran después.
- **Motivo:** en algunos núcleos el filtro seccomp hace que proot falle o se cuelgue; es lo primero que hay que probar si una distro no arranca, y sin ajuste habría que recompilar la app para probarlo en la tablet. Entra en la copia de ajustes de T15 con el resto.
- **Coste:** sin seccomp, proot intercepta todas las llamadas al sistema con `ptrace`: es notablemente más lento.

### D-T08b-8 · 2026-10-04 · Hipótesis sin validar y criterio de aceptación de la primera prueba real
- **Ejecutar desde `nativeLibraryDir`:** el manifiesto ya declara `android:extractNativeLibs="true"` y Gradle `useLegacyPackaging = true`, así que los binarios son ficheros reales. Con `targetSdk` 28 la app puede ejecutarlos; no se ha comprobado en Android 10–16 (SELinux de `untrusted_app_27/28`).
- **`PROOT_TMP_DIR`:** `cacheDir/proot-tmp`; proot no tiene `/tmp` en Android.
- **Binds:** `/dev`, `/proc`, `/sys`, `/etc/resolv.conf` y los de T13. Android 8+ no deja leer `/proc/stat` ni `/proc/loadavg` a las apps: `top`, `uptime` o `free` pueden fallar. Termux lo resuelve con ficheros falsos; no se hace aquí todavía.
- **El *phantom process killer* de Android 12+** puede matar procesos hijo de proot aunque el servicio esté en primer plano (D-T08-5).
- **Criterio de aceptación (primera prueba en la tablet, solo cuando el usuario la autorice):** (1) instalar Alpine desde la pantalla de distros; (2) abrir una pestaña nueva y comprobar que el prompt es el de Alpine, no el de Android; (3) `uname -a` y `cat /etc/os-release` muestran Alpine; (4) `apk update` descarga índices (red y DNS); (5) con el almacenamiento compartido activado, `ls ~/storage/downloads`; (6) cerrar y reabrir la app con la sesión viva; (7) si (2) o (3) fallan, repetir con el modo de compatibilidad activado y anotar el resultado aquí. Hasta entonces, ninguna de estas afirmaciones es cierta.

### D-T08b-9 · 2026-10-04 · Un solo mecanismo de «qué ejecutar» por pestaña: `SessionLaunch`
- **Contexto:** T14 (ya en `master`) añadió `SessionLaunch(command, environment, onClosed)` para que la pantalla de hosts abra una pestaña con `ssh`, y yo había añadido un `initialCommand` por pestaña con el mismo fin. Eran dos caminos para lo mismo.
- **Decisión:** gana `SessionLaunch`, porque lleva la limpieza `onClosed` (el fichero de la clave SSH se borra pase lo que pase con la sesión) y ya la usa la pantalla de hosts. Mi `initialCommand`, `ShellRequest`, los problemas `InvalidCommand`/`CommandNeedsDistro` y el citado `su -c` (`ShellQuote`) se eliminan: sin comandos del planificador no tenían uso.
- **Reglas:** (1) con `SessionLaunch` se ejecuta tal cual, con proot ya construido por quien lo crea (`DistroLaunchFactory`); (2) sin él, decide `ProotSessionPlanner` según el distro de la pestaña. Nunca los dos.
- **«+» y los demás atajos** abren por (2): distro predeterminada si está lista, shell de Android si no, con aviso.
- **Reiniciar una pestaña SSH que terminó** (`restartActive`) abre un shell normal **de la misma distro** (por el planificador), no el de Android; el fichero de clave ya se borró al terminar, así que no se puede repetir la conexión: hay que reconectar desde la pantalla de hosts. Si la distro ya no sirve, se ve el aviso de «shell de Android» o el error; no pasa en silencio.
- **Entradas de menú:** los botones provisionales «Distros» (T07) y «SSH» (T14) de `MainActivity` pasan al menú de «nueva pestaña» (pulsación larga en «+»).

### D-T08b-10 · 2026-10-04 · Corrección de un fallo de T13: el directorio de la distro **es** el rootfs
- **Hallazgo:** `DistroInstaller.publish` mueve lo que extrajo (`staging/rootfs`) a `distro.directory`; por tanto la raíz de la distro es `distro.directory` y no existe una carpeta `rootfs` dentro. T14 lo usa bien, pero `SharedStorageMounts` (T13, ya mergeada) creaba los puntos de montaje en `distro.directory/rootfs/…` (no existía, así que el montaje de `~/storage` no habría aparecido) y mi primera versión del planificador usaba la misma ruta (habría dado toda distro como «dañada»).
- **Decisión:** `SharedStorageMounts` y el planificador usan `distro.directory`. `DistroInstaller.UNPACKED_NAME` pasa a ser privada (es solo el nombre de la carpeta de preparación) y su comentario, que decía lo contrario, se corrige. Los tests de T13 usaban la misma ruta equivocada y se corrigen; hay un test nuevo que fija `-r` en `distros/<token>`.
- **Por qué no lo cazaron los tests:** el fake de sistema de ficheros y los tests de T13 construían la ruta con el mismo supuesto equivocado que el código. Lección: un test que comparte el supuesto con el código no lo comprueba; hace falta una prueba de extremo a extremo con un rootfs real en la primera prueba en la tablet.

## T08c — Correcciones halladas en hardware

Salen de las primeras pruebas reales en un Pixel 8 (Android 17, API 37, app con `targetSdk` 28), hechas
por el orquestador cuando el usuario las autorizó; este fork no tocó ningún dispositivo. **Lo que
funcionó** (informado por el orquestador): el shell con PTY real en una pestaña, la entrada, el área
del terminal encogiéndose por encima del teclado y de la fila de teclas extra (`stty size` = 18×49, lo
visible), el servicio en primer plano (`specialUse`) y proot con un rootfs de Alpine lanzado a mano
desde `nativeLibraryDir` (red, `apk update`, `ssh`, `nmap` y `python3` instalados dentro). **Lo que
falló o se veía mal** es lo que corrige esta sección. Todo lo de abajo está probado solo en el host.

### D-T08c-1 · 2026-10-04 · Permiso de batería: el diálogo del sistema (sustituye a D-T08-4 en este punto)

- **Qué se vio:** el diálogo «Mantener las sesiones activas» (notificaciones) y, encadenado, el aviso
  de batería llevaban a la lista general *Uso de batería de las aplicaciones* de Ajustes. El usuario
  quiere la pantalla de permitir de verdad.
- **Decisión:** el aviso de batería ahora pulsa «Permitir» y lanza
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` con `package:<la app>`, que es el diálogo del sistema
  «¿Permitir que la app se ejecute siempre en segundo plano?». Si el dispositivo no lo tiene
  (`ActivityNotFoundException`) o lo rechaza (`SecurityException`), se abre la lista general
  (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`). El orden y los datos están en `BatteryExemption`,
  probados; abrir las pantallas es Android y queda por comprobar. No se pide si la app ya está
  exenta (`nextPrompt` no cambia).
- **Permiso:** se declara `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (permiso normal, sin pregunta de
  instalación). **Solo abre ese diálogo: no cambia nada salvo que el usuario acepte allí.** F-Droid lo
  admite; Google Play lo restringe, y esta app no se publica allí (`targetSdk` 28, SPEC §2). Lint
  avisa con `BatteryLife`: se suprime **solo en esa línea del manifiesto**, con el motivo escrito
  al lado, no en la configuración global.
- **Por qué se sustituye D-T08-4:** la anterior era «sin exclusión forzada», y un diálogo del sistema
  que el usuario acepta o rechaza no fuerza nada. Lo demás de D-T08-4 (orden de los avisos, una vez
  por arranque, wake lock) sigue igual.
- **Para `PRIVACY.md` (T21):** explicar que el permiso existe para que una conexión SSH o una tarea
  larga no se pause con la pantalla apagada, que no recoge datos y que solo abre el diálogo.
- **Sin validar en dispositivo:** que el diálogo del sistema aparezca en Android 17 con `targetSdk` 28.

### D-T08c-2 · 2026-10-04 · Interruptor de almacenamiento: no se pudo reproducir; dos fallos reales corregidos

- **Qué se vio:** en una instalación limpia, la tarjeta «Almacenamiento del dispositivo en `~/storage`»
  mostraba el interruptor **activado**, aunque T13 (D-T13-2) lo documenta apagado por defecto.
- **Qué se comprobó:** ningún código escribe `shared_storage = true` por su cuenta; el valor por
  defecto de `AppSettings` es `false` y un test ya lo vigila (`SharedStorageSettingTest`). Así que
  **la causa exacta no se ha podido determinar sin el dispositivo**.
- **Fallo latente 1 (probable causa):** el callback del permiso usaba `results.values.all { it }`, y
  `all` sobre un mapa vacío es `true`: un resultado vacío (petición interrumpida) activaba la
  función **sin ningún permiso**. Ahora `StorageToggle.allGranted` exige que el resultado no esté vacío.
- **Fallo latente 2:** la UI mostraba `enabled` sin mirar si el permiso estaba concedido, así que un
  «activado» guardado sin permiso se veía como si funcionara. Ahora el interruptor se muestra
  encendido solo si el ajuste está activo **y** el permiso concedido (`StorageToggle.isShownOn`).
- **Pendiente:** reinstalar en limpio y comprobar el interruptor. Si sigue apareciendo activado, la
  causa es otra y hay que mirar el estado real de `setting` en el dispositivo.

### D-T08c-3 · 2026-10-04 · Pantalla de Distros: la cabecera en dos filas

El título «Distribuciones» y el botón «Instalar una distro» compartían fila y se solapaban en un
móvil de ~360 dp. Ahora el título y «Cerrar» comparten fila (el título con `weight(1f)`) y el botón
de instalar ocupa la suya, a todo el ancho. Así no se solapa con ningún ancho ni escala de fuente.
Sin validar en dispositivo (el solape sí se vio; la corrección es de diseño).

### D-T08c-4 · 2026-10-04 · `/proc` falso dentro de proot (aproximado a propósito)

- **Qué se vio:** dentro de proot, `/proc/stat`, `uptime`, `loadavg`, `version`, `vmstat` y otros dan
  `Permission denied` (SELinux no deja a una app leerlos), así que `top`, `uptime`, `free`, `vmstat`
  y `htop` no funcionan.
- **Decisión:** como hace proot-distro, cada lanzamiento escribe en el almacenamiento privado
  (`files/fake-proc/`) cinco ficheros falsos y los monta con `-b <fichero>:/proc/<nombre>` **después**
  de `-b /proc` (los binds se aplican en orden). Rige en las pestañas normales y en las de SSH
  (`ProotSessionPlanner` y `DistroLaunchFactory`). Si no se pueden escribir, la sesión arranca sin
  ellos (`FakeProcSource.None`), sin error.
- **Qué contiene (`FakeProc`, puro y probado):** el **formato** es exacto para que lo lean `procps` y
  `busybox`; los **valores son inventados**: `stat` reparte el tiempo de CPU en proporciones fijas
  del uptime (3 % usuario, 4 % sistema, 1 % iowait, 1 % softirq, el resto ocioso) con una línea por
  CPU real y contadores que crecen con el uptime; `uptime` es el real del dispositivo (con el ocioso
  de todas las CPU); `loadavg` es una constante (`0.12 0.07 0.02`); `version` lleva la versión de
  kernel real (saneada: sin saltos de línea ni caracteres de control, para que un valor raro no
  inyecte líneas); `vmstat` tiene los contadores que `procps` pide, con valores plausibles.
- **Consecuencia:** `top` y `htop` mostrarán un uso de CPU **falso** y constante en el tiempo. Sirve
  para que arranquen, no para medir nada. `/proc/meminfo` y las carpetas por proceso **no** se
  falsean: se asumen legibles (sin comprobar en el dispositivo).
- **No se falsea:** `cpuinfo` (parcial en el listado visto), `/proc/sys/kernel/cap_last_cap`.
- **Sin validar en dispositivo:** que proot admita el bind de un fichero sobre uno de `/proc` en
  Android 17 (proot-distro lo hace en Termux) y que `top`, `uptime` y `free` queden funcionando.

### D-T08c-5 · 2026-10-04 · El círculo oscuro era el menú de paneles; margen del texto

- **El círculo:** no era un asa ni un artefacto: es el botón «⋮» del menú de paneles de T10
  (`PaneMenu`), dibujado siempre en la esquina superior izquierda del panel enfocado, con un fondo
  oscuro redondo. Tapaba el principio de la primera línea aunque no hubiera ninguna división.
- **Decisión:** el botón solo aparece cuando la pestaña está dividida (entonces sirve para zoom,
  cerrar e intercambiar) y pasa a la esquina **superior derecha**. Dividir un panel que está solo se
  hace desde el menú «+» de la barra de pestañas (pulsación larga), junto a SSH y Distros, con
  «Dividir a la derecha» y «Dividir hacia abajo»; los atajos de T11 siguen igual. Los enlaces de ese
  menú se agrupan en `TabBarLinks` por el límite de parámetros de detekt.
- **Margen:** 6 dp alrededor del texto (hoy tocaba el borde de la pantalla). No es rejilla: se
  resta del área (`EdgeInsets.withTextMargin`, probado) y la pantalla rellena lo mismo
  (`padding` antes de medir el área de los paneles), así que el PTY sigue sabiendo exactamente el
  tamaño que se dibuja. 6 dp es una elección de ojo, sin ver el resultado.
- **Fuera de alcance, para T12c:** fuentes propias, editor de esquemas y estilo de pestañas.
- **Sin validar en dispositivo:** que el margen quede bien en móvil y tablet, que el tamaño de rejilla
  siga coincidiendo con lo visible (`stty size`) y que dividir desde el menú «+» funcione.

## T12c — Apariencia personalizable

Sale de la primera prueba real en un Pixel 8: el usuario dijo que la vista del terminal era fea y
preguntó si podía haber fuentes y diseño propios (y, entendido así, que **no** se parezca a Termux).
Antes de T12c los esquemas y JetBrains Mono existían, pero **no había ninguna pantalla** para elegirlos.

### D-T12c-1 · 2026-10-04 · Una pantalla "Apariencia" con vista previa en vivo, enlazada desde el menú "+"

- **Decisión:** `AppearanceScreen` (tema claro/oscuro/sistema, negro OLED, colores dinámicos, esquema,
  fuente, tamaño, interlineado, espaciado entre letras, margen, esquinas, forma y parpadeo del cursor, estilo
  de las barras) con una vista previa que dibuja un terminal de muestra con esos valores. Se abre desde
  el menú del "+" de la barra de pestañas ("Apariencia…"). T16 la enlazará desde los ajustes generales.
- **Motivo:** cambiar algo sin ver el resultado obliga a ir y volver al terminal. La vista previa usa
  `Text` de Compose con la misma fuente (`FontFamily(Typeface)`), el mismo esquema y los mismos espaciados.
- **Alternativas:** ajustes planos sin vista previa (más rápido, peor para decidir); previsualizar con el
  emulador real (más fiel, pero arrastra una sesión entera a una pantalla de ajustes).
- **Impacto:** la vista previa es una aproximación: el interlineado y el espaciado de Compose no son
  idénticos a los del `TerminalPainter`. Lo definitivo es lo que se ve en el terminal.

### D-T12c-2 · 2026-10-04 · Fuentes propias: se validan antes de aceptarlas y no se redistribuyen

- **Decisión:** importar con `ACTION_OPEN_DOCUMENT`, leer como mucho **8 MB**, y aceptar solo si: la
  cabecera es TrueType (`0x00010000` o `true`) u OpenType (`OTTO`); `Typeface.Builder` la carga; tiene los
  glifos básicos (`a-z`, dígitos, signos de uso común) y es **monoespaciada** (15 glifos de anchos muy
  distintos, `iIl1.:|WMm@#0OQ`, miden lo mismo con un 1 % de tolerancia). Si falla cualquier paso se
  **borra el archivo** y no queda nada. Se guarda en `files/fonts/<id>.ttf|otf` con un id propio
  (`font-<nombre>-<sufijo aleatorio>`), nunca con el nombre del archivo original.
- **Motivo:** una fuente proporcional rompe la cuadrícula (cada celda mide lo mismo); una fuente de iconos
  o sin letras hace ilegible el terminal. El nombre de fichero es un dato no fiable: se descarta.
- **Alternativas:** aceptar colecciones `.ttc` (`ttcf`, descartado: una colección trae varias caras y habría
  que elegir una); no validar y avisar al usar (peor: el usuario ve un terminal roto sin saber por qué).
- **Impacto:** las fuentes importadas quedan en el dispositivo y **no se suben ni se redistribuyen**: la
  pantalla avisa de que su licencia es responsabilidad del usuario. El negrita y la cursiva de una fuente
  importada los sintetiza Android (`Typeface.create(family, BOLD)`) si la familia no los trae.
- **Sin validar:** que `Typeface.Builder(file)` cargue fuentes reales de distintos formatos en Android 8–17,
  y la medición de anchos con el motor de texto real (los tests usan una sonda falsa).

### D-T12c-3 · 2026-10-04 · El espaciado entre letras ensancha la celda; el interlineado la alarga y centra el texto

- **Decisión:** `Paint.letterSpacing` se aplica antes de medir `cellWidth` ("X"), así que toda la cuadrícula
  usa la celda ensanchada y `terminalLayoutFor` calcula las columnas con ella. El interlineado multiplica
  la altura de celda y el texto se centra en ella (el espacio extra se reparte arriba y abajo).
- **Rangos:** interlineado 0,8 a 1,6; espaciado −0,05 a 0,3 em; margen 0 a 24 dp; esquinas 0 a 16 dp. Todo
  valor guardado o importado se **recorta** a su rango (`sanitized()`), y uno no finito vuelve al defecto.
- **Impacto:** un interlineado menor que 1 puede recortar los trazos de las letras altas: es el precio de
  permitirlo (rango mínimo 0,8).
- **Sin validar:** el aspecto real con cada fuente, y que el glifo ancho (emoji, CJK) siga alineado con
  espaciado distinto de 0 (la corrección existente de T03 los comprime a su celda).

### D-T12c-4 · 2026-10-04 · La forma del cursor la elige el usuario y manda sobre la que pida el programa

- **Decisión:** el cursor (bloque, subrayado, barra) y su parpadeo salen de la apariencia; el
  `TerminalPainter` ya no mira `emulator.cursorStyle`.
- **Motivo:** predecible. Con el estilo del programa, un `vim` o un `fish` que cambie la forma por modo
  deja al usuario sin control.
- **Alternativa:** respetar la forma que pide el programa (DECSCUSR) y usar la elegida solo por defecto.
  Es más fiel a lo que hace un terminal de escritorio. **Revisable**: se puede añadir un ajuste
  "respetar al programa".
- **Sin validar:** el parpadeo (un temporizador de 530 ms que invalida el canvas) y su coste de batería.

### D-T12c-5 · 2026-10-04 · Las barras toman los colores del esquema, y siempre se leen

- **Decisión:** `ChromeColorsFor.scheme` deriva de la paleta del esquema los colores de la barra de pestañas,
  la pestaña activa, las teclas extra, el borde de panel y el acento, mezclando fondo y texto
  (7 % / 18 % / 30 %). Si esa mezcla deja el texto de la barra por debajo de **4,5:1** (Solarized lo hace
  por diseño), `ColorMath.readableOn` lo empuja hacia blanco o negro lo mínimo necesario. `ChromeStyle`
  permite volver a los colores del tema del sistema.
- **Motivo:** con colores dinámicos activos el Pixel mostraba las barras en el azul del fondo de pantalla,
  ajeno al esquema. Con esto la ventana entera parece una pieza.
- **Hallazgo:** el test de legibilidad de todos los esquemas integrados cazó Solarized Dark antes de
  llegar a la pantalla.
- **Por defecto:** `SCHEME`. Quien quiera lo anterior elige "Los del tema del sistema".

### D-T12c-6 · 2026-10-04 · Sin barras "compactas": rompen los 48 dp táctiles

- **Decisión:** no hay opción de barra compacta. En su lugar se personaliza el **color** y el
  **radio de las esquinas**.
- **Motivo:** la SPEC (§6, accesibilidad) exige objetivos táctiles de al menos 48 dp, y la barra de pestañas
  y la fila de teclas ya miden eso. Una barra de 36 dp sería una regresión de accesibilidad.
- **Alternativa:** permitirlo avisando. Descartado: mejor no ofrecer lo que no debe usarse.
- **Detalle:** el color de cada tecla se dibuja con un hueco de 2 dp, pero **toda la celda** es el objetivo
  táctil.

### D-T12c-7 · 2026-10-04 · El botón `⋮` flotante ya estaba resuelto en T08c

- El círculo oscuro sobre el prompt era el menú de paneles de T10. T08c ya lo muestra solo cuando hay
  paneles divididos (el split de un panel único vive en el menú "+"). T12c solo le da los colores de
  la paleta de las barras.

### D-T12c-8 · 2026-10-04 · Editor de esquemas sin librería de color: hexadecimal y tres deslizadores

- **Decisión:** editor a pantalla completa con el nombre y los **20 colores** (16 ANSI, texto, fondo,
  cursor, selección). Cada color se cambia con un cuadro de texto hexadecimal (`#rrggbb`, también `#rgb`) y
  tres deslizadores (rojo, verde, azul) sincronizados. Duplicar, crear, editar, eliminar, importar y
  exportar (JSON versionado de T12). Un esquema no puede llamarse como otro, integrado o propio.
- **Aviso de contraste:** lista hasta cinco colores difíciles de leer sobre el fondo (texto 4,5:1; cursor y
  ANSI 3:1; se exceptúan negro y negro brillante, que se funden con el fondo a propósito). **Nunca bloquea.**
- **Motivo de no usar un selector de color:** una dependencia más para un control que esto hace igual de bien.
- **Alternativa:** rueda de color HSV propia. Más cómoda; más código para validar sin dispositivo.

### D-T12c-9 · 2026-10-04 · Para la copia de seguridad (T15): claves y archivos de fuente

- **Claves del repositorio de ajustes que T15 debe serializar** (forman parte del formato de backup: no
  renombrar): `appearance_font_id`, `appearance_line_spacing`, `appearance_letter_spacing`,
  `appearance_margin_dp`, `appearance_cursor_shape`, `appearance_cursor_blink`, `appearance_chrome_style`,
  `appearance_corner_dp`, `custom_fonts` (JSON: `id`, `name`, `fileName`) y las ya existentes
  `terminal_scheme`, `terminal_font_size_sp`, `custom_schemes`, `theme_mode`, `oled_black`, `dynamic_color`.
- **Los archivos de fuente NO están en esas claves:** viven en `files/fonts/`. **T15 debe incluir esa
  carpeta** en la copia de configuración; si solo lleva el JSON, una fuente restaurada vuelve a la incluida
  (`FontCatalog.idOrBundled`), sin romper nada.
- **Restauración segura:** `FontCatalog.decodeList` descarta entradas cuyo `fileName` sea una ruta (`/`, `\`,
  `..`, `.`) o venga vacío, y `AndroidFontStore` solo toca nombres que sean un nombre de archivo simple
  dentro de `files/fonts`. Un backup manipulado no puede escribir fuera de esa carpeta.

### D-T12c-10 · 2026-10-04 · Los deslizadores guardan al soltar

- Cada `Slider` mantiene su valor mientras se arrastra y escribe en los ajustes al levantar el dedo, así
  que se hace una escritura por gesto y no cientos. A cambio, la vista previa se actualiza al soltar.
- El tamaño de fuente de esta pantalla y el zoom por pellizco comparten el mismo valor guardado
  (`terminal_font_size_sp`): un valor nuevo del almacén se reaplica al terminal vivo.

### T12c: lo que NO se ha validado (sin dispositivo)

Solo hay tests de host. Falta comprobar en un móvil y en la tablet:

- La pantalla "Apariencia" completa (diseño, desplazamiento, teclado, rotación, TalkBack y objetivos de 48 dp).
- El selector de documentos para importar una fuente y para importar o exportar un esquema.
- Que `Typeface.Builder` cargue de verdad .ttf y .otf reales, y que la comprobación de monoespaciado
  acepte las fuentes buenas y rechace las proporcionales con el motor de texto real.
- El aspecto del terminal con interlineado y espaciado distintos de 1 y 0, y con fuentes ajenas.
- La forma del cursor y su parpadeo, el margen, y los colores de las barras con cada esquema (también
  con colores dinámicos activos y con el modo OLED).
- Que cambiar la fuente o el espaciado con sesiones abiertas repinte y reajuste el pty sin cortar nada.
## T15 — Copias de seguridad y restauración

### D-T15-1 · 2026-10-04 · Formato: un tar plano, cifrado opcional por encima

- **Decisión:** un archivo `.utbackup` es un `tar` plano (legible con cualquier `tar`) con `manifest.json` primero, `config.json` y una parte `distros/N.tar.gz` por distro. El cifrado, si se pide, envuelve todo el tar. El manifiesto lleva versión de formato, tipo, y el SHA-256 y tamaño de cada parte.
- **Motivo:** restaurar puede validar el archivo entero antes de tocar nada, y un backup sin contraseña sigue siendo recuperable a mano.
- **Alternativas:** un contenedor binario propio (más compacto, pero opaco e imposible de rescatar sin la app); un `.zip` (obliga a leer el directorio central al final: mal para flujos).
- **Detalle:** el contenedor usa `BIGNUMBER_STAR` y no `BIGNUMBER_POSIX`: este último antepone una cabecera PAX a cada entrada y el archivo ya no empieza por `manifest.json`, que es como se reconoce un backup plano.

### D-T15-2 · 2026-10-04 · Desviación de la SPEC: gzip en lugar de zstd

- **Decisión:** las distros se comprimen con **gzip** (`java.util.zip`), no con zstd como pide la SPEC (`.tar.zst`).
- **Motivo:** zstd en Java necesita una librería. `zstd-jni` empaqueta `.so` precompilados (prohibido por CLAUDE.md para F-Droid). `aircompressor` es Apache-2.0 y Java puro, pero su v3 se apoya en APIs recientes del JDK y en `Unsafe`, y no puedo validarlo en Android sin dispositivo. gzip no añade dependencias y ya es lo que el instalador sabe leer.
- **Impacto:** archivos algo mayores que con zstd. Si más adelante se valida una librería, solo cambia el nombre y el compresor de las partes de distro: `format` sube a 2.

### D-T15-3 · 2026-10-04 · Cifrado: AES-256-GCM por trozos con clave PBKDF2

- **Decisión:** clave de 256 bits derivada de la contraseña con PBKDF2-HMAC-SHA256 (600 000 iteraciones, sal aleatoria de 16 bytes). Los datos se cifran en trozos de 64 KiB, cada uno con su etiqueta GCM. El nonce de cada trozo es un prefijo aleatorio del archivo, el número de trozo y una marca de «último»; la cabecera entera se autentica con cada trozo.
- **Motivo:** el rootfs no cabe en RAM, y con esta construcción un trozo cambiado, repetido, reordenado o un archivo cortado (incluso justo entre dos trozos) falla la autenticación igual que una contraseña errónea. Una cabecera con iteraciones o tamaño de trozo absurdos se rechaza antes de gastar CPU.
- **Impacto:** contraseña errónea y archivo manipulado son indistinguibles (`WrongPasswordOrCorrupt`): no se escribe nada en ninguno de los dos casos.

### D-T15-4 · 2026-10-04 · Claves SSH: solo dentro de un backup cifrado

- **Decisión:** las claves privadas SSH están cifradas con el Keystore del dispositivo, y esa clave no puede salir. Por eso un backup las lleva **en claro dentro del contenedor cifrado con la contraseña del usuario**, y la exportación **se niega** (`KeysNeedPassword`) a incluirlas sin contraseña. El usuario puede dejarlas fuera.
- **Motivo:** la alternativa, escribirlas sin proteger en un archivo que viaja entre dispositivos, es justo lo que el modelo de amenazas de T14 evita.
- **Impacto:** al restaurar sin las claves, un host que apuntaba a una clave pierde ese alias y queda sin clave, no roto.

### D-T15-5 · 2026-10-04 · Restaurar solo añade; los ajustes se sustituyen

- **Decisión:** restaurar nunca borra ni pisa lo que hay: perfiles, layouts, hosts y claves cuyo nombre (o alias) ya existe se conservan y se cuentan como «omitidos». Los ajustes sí se sustituyen, porque el objetivo es dejar la app igual. Las referencias entre elementos van por nombre o posición, nunca por id de base de datos, porque cambian entre dispositivos.
- **Valores propios del dispositivo:** el interruptor de almacenamiento compartido **no se copia** (el permiso es del dispositivo y se vuelve a pedir). Valores fuera de rango se ajustan al límite o vuelven al valor por defecto.
- **No incluido todavía:** teclas extra y atajos no están persistidos en la app, así que no hay nada que copiar. El `config.json` ignora campos desconocidos y lleva versión, de modo que se pueden añadir.

### D-T15-6 · 2026-10-04 · Dos pasadas y distros transaccionales

- **Decisión:** la primera pasada lee todo el archivo y comprueba etiquetas de cifrado, manifiesto, hash y tamaño de cada parte y la configuración, **sin escribir nada**. La segunda restaura y vuelve a comprobar cada parte al vuelo (el archivo puede haber cambiado entre pasadas, o venir de una fuente que cambia).
- **Cada distro** es todo o nada: se registra, se desempaqueta en un directorio temporal con el extractor seguro de T07 (sin path traversal, sin escribir a través de enlaces, con límites de tamaño), se comprueba el hash y solo entonces se mueve a su sitio y se marca `READY`. Un fallo o una cancelación en cualquier punto la elimina.
- **Entre distros no hay transacción:** si falla la segunda, la primera, ya completa y verificada, se queda. La configuración se aplica al final, después de las distros a las que puede referirse.
- **Si el nombre ya existe**, la distro restaurada se llama `Nombre (restored)`, luego `(restored 2)`, etc.
- **Impacto en disco:** exportar archiva cada distro primero en un directorio de trabajo privado (para conocer su hash antes de escribir el manifiesto), así que hace falta sitio para una copia comprimida temporal. Restaurar no necesita copia: lee del flujo.

### D-T15-7 · 2026-10-04 · Lo que se conserva y lo que no del rootfs

- Se conservan los nueve bits de permiso, los enlaces simbólicos como enlaces, las fechas, y los directorios. Los **sockets, tuberías y dispositivos se omiten**, como hace el extractor de T07.
- Los **enlaces duros se archivan como ficheros independientes** (el tamaño crece si hay muchos).
- **Propietarios:** todas las entradas se escriben como `root`. En el dispositivo todos los ficheros son del usuario de la app y proot presenta la propiedad por su cuenta, así que no hay un propietario real que conservar.
- Un fichero del rootfs sin permiso de lectura para el propietario hace fallar la exportación con un error claro y sin dejar un archivo a medias (no se ha tratado de forzar el permiso). Es una hipótesis: si aparece en distros reales, habrá que cambiarlo.

### D-T15-8 · 2026-10-04 · Cobertura

- `data.backup` está medido por el umbral crítico de Kover: **100 % de línea y de rama (749/749 y 362/362)**. Para llegar se eliminaron ramas defensivas inalcanzables, y se añadieron al filtro de exclusión de Kover las clases `@Serializable` y los `$$serializer`: son datos puros con código generado cuyas ramas ningún test puede alcanzar, igual que ya se excluían Hilt y Room.
- `TarGzExtractor` ganó la sobrecarga que extrae desde un flujo (para leer la parte de una distro sin copiarla); su comportamiento sobre ficheros no cambia y sus tests siguen en verde.

### D-T15-9 · 2026-10-04 · Qué NO está validado (sin dispositivo)

1. El selector de archivos del sistema (`ACTION_CREATE_DOCUMENT` y `ACTION_OPEN_DOCUMENT`) con proveedores reales (Drive, tarjeta SD), y que `openOutputStream(uri, "wt")` trunque en todos ellos.
2. Una exportación y restauración de una distro **real** (cientos de MB) en un móvil: tiempos, uso de memoria, y que PBKDF2 con 600 000 iteraciones no tarde demasiado en un móvil de gama baja.
3. Que el rootfs restaurado arranque con proot (depende de la validación de T02/T08b).
4. Si una exportación larga sobrevive con la app en segundo plano: corre en el ámbito del ViewModel, **no** en el servicio en primer plano, así que Android puede matarla.
5. La interfaz (diálogos, contraseña, progreso) y su accesibilidad con TalkBack.
6. `mkfifo` hace falta en el host para uno de los tests (hay `mkfifo` en Linux y macOS).

## Requisitos añadidos tras las primeras pruebas en hardware (2026-10-04)

El usuario probó la app en un Pixel 8 y pidió tres cosas. Se anotan aquí qué requisito se tocó y por qué.

### D-REQ-1 · 2026-10-04 · La fila de teclas extra sigue al teclado en pantalla
- **Decisión:** `ExtraKeysConfig.onlyWithKeyboard` (por defecto `true`): la fila se dibuja solo mientras el teclado está visible (`WindowInsets.ime`), y su altura deja de reservarse en el cálculo de la rejilla cuando no se ve. SPEC RF-08 actualizado.
- **Motivo:** con el teclado cerrado la fila (dos filas de 48 dp) comía terminal sin servir para escribir.
- **Alternativas:** ocultarla siempre y mostrarla con un botón (más toques); atarla a "hay teclado físico" (no es fiable en Android). Se deja la opción para verla siempre.
- **Impacto:** el cambio de tamaño del pty al abrir y cerrar el teclado ya estaba debounced (D-T04-2); la fila entra y sale con el teclado. El formato de texto guardado añade la línea `onlyWithKeyboard=`; un texto antiguo sin ella cuenta como `true`.
- **Sin validar en el dispositivo:** que no haya parpadeo de la rejilla al animarse el teclado.

### D-REQ-2 · 2026-10-04 · Arranque en la distro predeterminada: ya cumplido, con requisito explícito (RF-13)
- **Comprobado en un Pixel 8:** con Alpine instalada, cerrar la app del todo y reabrirla deja `localhost:~#` (proot) en la primera pestaña.
- **Decisión:** se convierte en el requisito RF-13 y se añade T09b para lo que falta: nombrar las pestañas con su distro y el repintado de pestañas nuevas.

### D-REQ-3 · 2026-10-04 · La pantalla de Ajustes pasa a ser un requisito de acceso, no solo de contenido
- **Hallazgo:** no existía ninguna forma de llegar a unos ajustes (T12, T13, T14 y T15 dejaron cada uno su tarjeta en la pantalla de distros). T16 era una tarea del final del plan.
- **Decisión:** RF-11 exige un icono ⚙ permanente y secciones definidas; T16 se amplía con ellas y sube de prioridad.
- **Motivo:** sin ajustes visibles, T12c (apariencia) y todas las opciones de T11–T15 no se pueden usar.

## T22a — Sistema de diseño estilo iOS: componentes base

### D-T22a-1 · 2026-10-04 · Componentes propios sobre `foundation`, sin Material 3 ni librería de widgets
- **Decisión:** los componentes (`ui/ios`) se escriben sobre `compose.foundation` y `compose.ui`, sin usar los componentes de Material 3. El tema Material de las pantallas actuales no se toca hasta T22b y T22c.
- **Motivo:** Material pone su propio ripple, formas, estados y tipografía; para parecer iOS habría que desactivarlos casi todos. La accesibilidad (roles, estados, 48 dp, escala de fuente) se hace a mano y queda cubierta en cada componente.
- **Alternativas:** *compose-cupertino* (Apache-2.0, 1 659 estrellas): su último cambio es de octubre de 2025 y está construido sobre Compose Multiplatform 1.6.1, muy anterior a nuestro BOM; añadiría dependencias de Multiplatform a un proyecto solo Android. *Material 3 tematizado*: ver arriba.
- **Impacto:** sin dependencias nuevas ni cambios en `verification-metadata.xml`.

### D-T22a-2 · 2026-10-04 · Tipografía: Inter (SIL OFL-1.1), cuatro pesos sin modificar
- **Decisión:** Inter 4.1 (Regular, Medium, SemiBold, Bold), copiada sin cambios del `extras/ttf` de la release oficial (`Inter-4.1.zip`, SHA-256 `9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e`). Licencia y créditos en `THIRD_PARTY_NOTICES.md` y `assets/licenses/Inter-OFL-1.1.txt`.
- **Motivo:** SF Pro no se puede usar. Inter es la alternativa libre más cercana en forma y tiene los pesos que pide la escala de iOS (Large Title 34 a Caption 12).
- **Alternativas:** la fuente variable de Inter (menos ficheros, pero API de variaciones más delicada y más peso por uso); la fuente del sistema (Roboto, que no se parece a iOS).
- **Impacto:** el APK crece unos 1,6 MB. La fuente del terminal sigue siendo JetBrains Mono (T12).

### D-T22a-2b · 2026-10-04 · Iconos: Lucide (ISC), 16 vectores con las rutas sin cambiar
- **Decisión:** 16 iconos de Lucide 1.52.0 (chevrones, check, plus, x, search, ellipsis, settings, trash, folder, download, terminal, info, copy, key) convertidos a `res/drawable/ic_ios_*.xml`; una enumeración `IosGlyph` los nombra.
- **Motivo:** SF Symbols no se puede usar. Lucide es ISC (compatible con GPL-3.0) y su trazo redondeado de 2 px se parece al de iOS. Algunos iconos heredan de Feather (MIT): se acreditan los dos.
- **Cómo se convirtió:** cada elemento SVG (`path`, `line`, `circle`, `rect`, `polyline`, `ellipse`) pasó a una ruta de vector con el mismo trazo; el color se pone al dibujar (`ColorFilter.tint`).
- **Nota:** `trash-2` no existe en 1.52.0; se usa `trash`.

### D-T22a-3 · 2026-10-04 · Barras translúcidas: desenfoque real del fondo con `GraphicsLayer`, sin librería
- **Decisión:** el contenido que se desplaza se graba una vez en una capa (`GraphicsLayer`); la barra dibuja una copia desenfocada de la parte que tiene detrás (`RenderEffect`, desde Android 12) con un tinte encima. Antes de Android 12 la barra es casi sólida (97 %). La política es lógica pura (`BarStyle.forSdk`, con tests).
- **Motivo:** `Modifier.blur` solo desenfoca el propio contenido, no lo de detrás; el efecto de iOS necesita desenfocar lo que hay debajo.
- **Alternativa:** *Haze* (Apache-2.0, activo): hace lo mismo con más pulido. Queda como plan B si el nuestro da problemas, a cambio de una dependencia nueva.
- **Riesgo (sin validar):** coste de volver a desenfocar en cada fotograma de un scroll, sobre todo en gama baja. Hay que medirlo en el dispositivo.

### D-T22a-4 · 2026-10-04 · Paleta: colores de iOS con el texto subido a 4,5:1, y el tinte sale del esquema de T12
- **Decisión:** `iosPalette(decisión, esquema)` da fondos, celdas, etiquetas, separador, tinte y rojo destructivo en claro, oscuro y OLED. El tinte parte del azul ANSI (índice 4) del esquema; el tinte, el rojo y la etiqueta secundaria se acercan a negro o blanco solo lo justo para llegar a 4,5:1 sobre la celda y sobre la página (`ColorAdjust.ensureContrast`).
- **Motivo:** el azul del sistema de iOS (≈4,0:1) y su etiqueta secundaria (≈3,3:1) no llegan a AA en claro. RF-14 exige 4,5:1.
- **OLED:** fondo y página en negro puro; las celdas en `#111113` (no `#000000`) para que se distingan, y separador `#2A2A2D`.
- **Prueba:** un test recorre los 3 temas × (sin esquema + todos los incluidos) y exige 7:1 para la etiqueta y 4,5:1 para el resto.

### D-T22a-5 · 2026-10-04 · Hoja inferior: un `Dialog` arrastrable por el asa, con detents por proyección de velocidad
- **Decisión:** la hoja es un `Dialog` a pantalla completa con fondo propio; se arrastra por el asa (48 dp de alto) y, al soltar, `SheetDetents.snap` proyecta la velocidad 0,15 s y elige el detent más cercano (medio 0,5, grande 0,94) o la cierra si queda por debajo de la mitad del más bajo. Toque en el fondo, botón atrás y una acción de accesibilidad también la cierran.
- **Motivo:** un `Dialog` da gratis el modo modal y el botón atrás.
- **Límite conocido:** el contenido de la hoja se desplaza por su cuenta; no arrastra la hoja (como mucho, el asa). Es más simple y no pelea con el scroll.

### D-T22a-6 · 2026-10-04 · Alertas y hojas de acciones: `Dialog` con ventana propia
- **Decisión:** `IosAlert` (270 dp, dos acciones lado a lado) e `IosActionSheet` (pegada abajo, cancelar en tarjeta aparte) son `Dialog`. Cada acción cierra el diálogo tras ejecutarse; todas miden al menos 48 dp.

### D-T22a-7 · 2026-10-04 · Lo que el catálogo enseña y dónde vive
- **Decisión:** `IosCatalog` y su actividad están en el *source set* `debug` (con sus cadenas en inglés y español), así que no entran en el APK de producción. Se abre con `adb shell am start -n com.qtekfun.ultimateterminal/.ui.ios.IosCatalogActivity`. Un segmentado cambia entre claro, oscuro y OLED, y una fila recorre los esquemas.

### D-T22a-8 · 2026-10-04 · Lo que Lint y detekt obligaron a cambiar (sin relajar nada)
- `Modifier.offset` con valores de estado debe usar la sobrecarga con lambda (el interruptor y el segmentado).
- Un recurso de cadenas sin usar (se usa como título de la sección de tema del catálogo).
- Constructores con más de 7 parámetros (`IosColors`, `IosTypography`): ahora reciben la paleta o un mapa de roles de texto.
- Ficheros que no coinciden con su única declaración (`IosGlyph`, `IosAccessory`, `IosButtonStyle`, `BackdropState`): renombrados o separados.

### D-T22a-9 · 2026-10-04 · Qué NO está validado (sin dispositivo)
- Cómo se ve cada componente en el dispositivo y en la tablet, y que el catálogo abre.
- **El desenfoque de las barras**: que se vea bien y que el scroll siga fluido (D-T22a-3).
- La respuesta háptica (los tipos `SegmentTick`, `ToggleOn`/`ToggleOff`, `Confirm` dependen de la versión de Android y de si el usuario la desactivó).
- TalkBack con roles y estados (interruptor, segmentos, hoja con acciones personalizadas).
- La hoja inferior: tacto del arrastre, velocidad de proyección y que el `Dialog` sin atenuación de ventana se vea bien con el teclado.
- El campo de búsqueda con el teclado (foco, IME, botón de borrar).
- Rendimiento y consumo de memoria de las capas de desenfoque.

## T20-T21 — Metadatos F-Droid y documentación

Trabajo de texto: no se compiló, no se tocó código ni ninguna pantalla y no se usó ningún dispositivo. Lo
que se afirma sobre qué está verificado en un dispositivo sale de lo que el orquestador anotó en
`DECISIONS.md` (sección T08c y siguientes) y en `PLAN.md`; no se ha comprobado de nuevo.

### D-T20-1 · 2026-10-04 · Los textos de fastlane se reescriben; el changelog es el de `10001`

- **Qué había:** un borrador de un agente anterior, de cuando solo existía T03 ("hoy la app muestra un
  terminal con el shell de Android; las distros y las pestañas son futuro"). Ya no es verdad.
- **Decisión:** se reescribieron `title`, `short_description`, `full_description` y el changelog, en
  `en-US` y `es-ES`, con lo que existe en `master` y diciendo expresamente qué está probado solo en un
  ordenador. Límites comprobados con un script: título 16/30, resumen 64 y 65/80, descripción 2442 y
  2570/4000, changelog 320 y 358/500.
- **Changelog `10001`:** `0.1.0-rc.1` da `(0*10000 + 1*100 + 0)*100 + 1 = 10001`, que es el fichero. **No** se
  crea `10099` (el `0.1.0` final) porque esa versión aún no existe: lo pide `RELEASING.md` al cortar la release.
- **Por qué decirlo en la ficha:** una ficha de F-Droid que prometiera tablets, paneles o copias de seguridad
  "funcionando" sería falsa hoy: solo se ha probado en un móvil. Se dice "versión preliminar temprana".

### D-T20-2 · 2026-10-04 · Icono

- Se reutiliza `images/icon.png` (512×512, RGB) que dejó el agente anterior: el prompt `>_` verde sobre el
  fondo oscuro del icono del launcher. Se miró a mano y coincide con `ic_launcher_foreground.xml`/el color
  de fondo; no se regeneró. Es la misma imagen en las dos localizaciones.
- **Pendiente:** si se rediseña el icono del launcher (T22b/c), regenerar este.

### D-T20-3 · 2026-10-04 · Capturas: pendientes, no inventadas

- No hay `phoneScreenshots/` ni `sevenInchScreenshots/`: una captura tiene que ser real y de la build actual, y
  no se puede hacer sin un dispositivo. `fastlane/README.md` lista las que convienen.
- **Pendiente (orquestador, en un dispositivo):** hacerlas, sobre todo una en tablet, que es el caso por el que
  existe el proyecto.

### D-T20-4 · 2026-10-04 · Receta `fdroid/com.qtekfun.ultimateterminal.yml`: lo que cuadra y lo que no

- **Coherente con el repo:** `submodules: true` (proot sale del submódulo), `ndk: 28.2.13676358` (igual que
  `ndkVersion` de `app` y de `terminal-emulator`), `subdir: app`, JDK 21, `UpdateCheckMode: Tags` con solo
  versiones finales, `Binaries` para el build reproducible, `License: GPL-3.0-or-later`.
- **Riesgos que no se pueden comprobar sin ejecutar `fdroid build`:**
  1. **CMake 3.31.6** está fijado en `app/build.gradle.kts` (`externalNativeBuild.cmake.version`) y la receta no
     lo instala. Si el servidor de F-Droid no lo tiene, Gradle intenta bajarlo con `sdkmanager` y puede fallar. Hay
     que probarlo y, si hace falta, añadir un `prebuild`/`sudo` que lo instale.
  2. `AllowedAPKSigningKeys` es un marcador: se rellena con la huella del certificado de release (ver `RELEASING.md`).
  3. `versionName`/`versionCode`/`commit` (`0.1.0`, `10099`, `v0.1.0`) son los de la primera versión final, no los del
     árbol (`0.1.0-rc.1`, `10001`); las `-rc.N` no se ofrecen en F-Droid a propósito.
  4. `targetSdk 28`: no se ha comprobado cómo lo trata la política de inclusión de F-Droid (no lo prohíbe, pero puede
     avisar). Verificarlo al presentar la receta.
- **No se cambió la receta:** cada cambio sería una suposición sobre el servidor de F-Droid que no puedo comprobar.

### D-T20-5 · 2026-10-04 · Anti-características: ninguna segura, una a consultar

- **No aplican:** `Ads`, `Tracking`, `NonFreeAdd`, `NonFreeAssets` (la fuente y los iconos son libres: OFL/ISC),
  `NonFreeDep` (sin servicios de Google ni blobs; lo vigila `checkForbiddenDependencies` y `licensee`),
  `UpstreamNonFree`, `KnownVuln`, `NoSourceSince`.
- **A consultar con F-Droid, sin afirmar:** `NonFreeNet`. Para instalar **Debian** la app descarga el manifiesto y
  el `rootfs.tar.gz` de `raw.githubusercontent.com/debuerreotype/docker-debian-artifacts` (D-T06-3). GitHub es un
  servicio alojado propietario, aunque lo que se baja es software libre; no sé si F-Droid lo cuenta como
  "depende de un servicio de red no libre" para una descarga opcional que el usuario inicia. Ubuntu
  (`cdimage.ubuntu.com`) y Alpine (`dl-cdn.alpinelinux.org`) salen de los mirrors de los propios proyectos.
- **Descargar y ejecutar software de terceros** (las distros) no tiene anti-característica propia que yo conozca;
  se menciona en la descripción y en `PRIVACY.md`. Confirmarlo al presentar la receta.

### D-T20-6 · 2026-10-04 · Licencia declarada y un punto que sigue abierto

- **Declarada:** GPL-3.0-or-later. Compatible con lo que lleva: Apache-2.0 (Termux emulator, AndroidX, Kotlin…),
  OFL-1.1 (JetBrains Mono, Inter), ISC (Lucide), MIT (esquemas), GPL-2.0-or-later (PRoot), LGPL-3.0-or-later
  (talloc); todo está en `THIRD_PARTY_NOTICES.md`.
- **Abierto, no resuelto aquí (D-T03-2):** los ficheros de `terminal-emulator` de Termux no llevan cabecera de
  licencia y Apache-2.0 solo consta en el `LICENSE.md` raíz de upstream. Hay que **confirmarlo con los
  mantenedores de Termux antes de presentar la app a F-Droid**. No es asesoría legal.

### D-T21-1 · 2026-10-04 · El README distingue "verificado en un dispositivo" de "solo en un ordenador"

- **Decisión:** una tabla con dos columnas (dispositivo / solo host). Verificado en un Pixel 8: arranque, shell con
  PTY, entrada y redimensionado con el teclado (`stty size`), servicio en primer plano, instalar Alpine desde la app,
  abrirla con proot en una pestaña, `apk add` con red, `ssh` y `python3` en esa pestaña, `nmap` instalado, y que al
  arrancar se abre la distro predeterminada. Todo lo demás, solo host.
- **Motivo:** el proyecto se ha hecho casi sin dispositivo y un README optimista es peor que uno que dice qué falta.
  Lo que se arregló y aún no se ha vuelto a mirar (la fila que se oculta con el teclado, el texto que llega al shell
  mientras se escribe, el diálogo de batería directo, el `/proc` falso) se lista aparte como "en el código, no mirado".
- **Dato desfasado que no se tocó:** la sección "Pendiente de validar en hardware", al principio de este fichero, empieza
  diciendo que "el usuario ha prohibido probar en el Pixel 8". Es cierto de cuando se escribió, pero el usuario lo
  autorizó después para el orquestador. No se edita aquí para no chocar con los demás agentes que añaden a este
  fichero; conviene actualizarla cuando se cierre la validación en dispositivo.

### D-T21-2 · 2026-10-04 · `PRIVACY.md`: cada permiso con su motivo, en inglés y español

- **Contenido:** sin telemetría ni cuentas; qué se guarda y dónde (almacenamiento privado, `allowBackup=false`, claves
  cifradas con el Keystore); los **tres dominios** a los que se conecta para bajar distros (`dl-cdn.alpinelinux.org`,
  `cdimage.ubuntu.com`, `raw.githubusercontent.com`) y que solo se usa HTTPS; el DNS de respaldo público
  (`1.1.1.1`/`9.9.9.9`) como **último recurso** y su efecto en la privacidad; una tabla con los nueve permisos
  del manifiesto (`INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE(_SPECIAL_USE)`, `POST_NOTIFICATIONS`,
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `WAKE_LOCK`, `READ/WRITE_EXTERNAL_STORAGE`) y los que **no** pide; la
  contraseña de las copias de seguridad (no se guarda, no se recupera); y por qué `targetSdk 28` y no Google Play.
- **Matiz que se quiso evitar:** "no escribe contenido en registros" se redactó como "por diseño", porque es una regla
  del código (`CLAUDE.md`) y no algo que este trabajo haya auditado.
- **El permiso de almacenamiento** se describe tal cual está: declarado siempre, pedido solo al activar `~/storage`,
  y con la salvedad de que en Android 13 o posterior puede concederse solo para multimedia (D-T13-3, sin verificar).
- **Hay que revisarlo cuando cambie:** el manifiesto (cualquier permiso nuevo), T16 (los DNS pasarán a ser un ajuste),
  y si se añade cualquier otro dominio al que se conecte la app.

### D-T21-3 · 2026-10-04 · `CONTRIBUTING.md` recoge lo aprendido en esta etapa

- Rama principal `master`; Conventional Commits; PR con **todos** los checks, incluido GitGuardian (que puede
  marcar falsos positivos: se lee y se explica, no se ignora); **no fusionar `master` dentro de la rama** (GitGuardian
  releyó código de T15 en PRs ajenas) sino rebasar; `lintDebug` en local antes de abrir la PR (Lint con avisos como
  errores); tests deterministas (el reloj en los fixtures de tar dejó la cobertura crítica fallando 2 de cada 6
  ejecuciones); cobertura 85 % y 100 % crítica; `targetSdk` 28 intocable; créditos en `THIRD_PARTY_NOTICES.md`.
- **No afirma** nada que no se haya hecho: no promete reporte privado de vulnerabilidades de GitHub (no sé si está
  activado), solo "contacta en privado con el mantenedor".

### Pendiente de este trabajo

- Capturas reales (D-T20-3). Huella `AllowedAPKSigningKeys`, comprobar CMake en el servidor de F-Droid y consultar
  `NonFreeNet`, el `targetSdk` 28 y el punto de licencia de Termux (D-T20-4 a D-T20-6) al presentar la receta.
- No se ejecutó `fdroid lint` ni `fdroid readmeta`: no están en esta máquina. Hay que pasarlos antes de enviar la receta.

## T09b — Pestañas: repintado de las nuevas y nombre de distro

### D-T09b-1 · 2026-10-04 · Causa raíz de la pestaña en blanco: `activeHost` se calculaba antes de que existiera el host
- **Hallazgo (Pixel 8):** una pestaña nueva salía en blanco hasta cambiar de pestaña y volver, y con el teclado visible lo escrito no se repintaba hasta ocultarlo. Se reprodujo en cold start y en pestañas creadas con "+".
- **Causa:** `SessionManager.activeHost` era `state.map { factory.host(it.activeId) }.distinctUntilChanged()`. El estado publica la pestaña nueva **antes** de arrancar su sesión (el planificador lee la distro del estado ya publicado), y el host se registra en la fábrica después. En ese instante `factory.host(...)` devolvía `null`, `distinctUntilChanged` impedía repetir la consulta y el ViewModel retransmitía `flowOf(0)`: ningún aviso de cambio del emulador llegaba a la vista. Cualquier cosa que cambiara el estado (cambiar de pestaña, el cambio de tamaño al ocultar el teclado) repetía la consulta y "arreglaba" la pantalla, lo que explica los síntomas.
- **Decisión:** `HostRegistry<T>` (dominio, genérico) guarda los hosts y expone un contador observable; `follow(activeId)` repite la consulta cada vez que un host se registra o se quita. `AndroidSessionFactory` lo usa en lugar de un `mutableMapOf`, `SessionManager.activeHost` es `registry.follow(...)`, y los paneles no activos (`InactivePane`) releen su host cuando cambia el registro (`hostChanges`), porque tenían el mismo defecto.
- **Alternativas:** forzar un repintado periódico (esconde el fallo y gasta batería); registrar el host antes de publicar el estado (cambia el orden de arranque de T08, más riesgo).
- **Prueba:** `HostRegistryTest` reproduce el orden del fallo (estado primero, host después) y comprueba que se sigue el host, que no hay emisiones repetidas y que un host cerrado deja de seguirse.
- **Sin validar en dispositivo:** que la pestaña nueva se dibuje al instante y que lo escrito se repinte sin cambiar de pestaña.

### D-T09b-2 · 2026-10-04 · Nombre de pestaña: la distro, numerada si se repite (RF-13)
- **Decisión:** `tabNames` (dominio, puro) da `Custom` si el usuario renombró, `InDistro(distro, ordinal)` si no ("Alpine", "Alpine 2"...), y `Plain(n)` ("Shell n") para el shell de Android. Una pestaña renombrada no consume número de su distro.
- **Motivo:** con varias pestañas en una distro, "Shell 1/2/3" no dice nada; el nombre del usuario siempre gana.

### D-T09b-3 · 2026-10-04 · El círculo oscuro con "⋮" sobre el prompt no lo dibuja la app (sin confirmar)
- **Hallazgo:** en las capturas del Pixel 8 hay un círculo oscuro con tres puntos en la esquina superior izquierda del terminal. Ninguna parte del código lo dibuja allí: el menú de paneles (`PaneMenu`) está arriba a la derecha y solo aparece con paneles divididos. Está anclado al origen de la vista de entrada (`TerminalInputView`, 1 dp), así que parece una función del sistema para editores con foco.
- **Mitigación aplicada, sin confirmar:** `importantForAutofill = NO`, `setAutoHandwritingEnabled(false)` desde Android 14 y `IME_FLAG_NO_PERSONALIZED_LEARNING`. T08c ya lo había atribuido al menú de paneles sin mirar en el dispositivo y el círculo siguió ahí; no doy por resuelto este punto hasta verlo en un dispositivo.

## T22b — Cromo del terminal estilo iOS

### D-T22b-1 · 2026-10-04 · El cromo usa `ui/ios` (T22a) con los colores del esquema del terminal
- **Decisión:** `TerminalScreen` envuelve el contenido en `IosTheme(decision, scheme)`, con `dark` y `oled` sacados del esquema (no del tema del sistema): la pantalla se dibuja en los colores del esquema y su cromo tiene que combinar con ellos. Las barras siguen usando `ChromePalette` (T12c) para superficies y texto.

### D-T22b-2 · 2026-10-04 · Barra de pestañas: cápsulas, y el menú de la pestaña activa
- **Decisión:** cada pestaña es una cápsula (`capsule`, dibujada con 6 dp de margen vertical dentro de un objetivo táctil de 48 dp, así que la cápsula es más baja que lo que se toca). La activa lleva el relleno `selected`, el texto en seminegrita y un botón `⋯` (menú con renombrar y cerrar, `IosContextMenu`); las demás, solo texto. Línea fina (0,5 dp) entre la barra y el terminal. Todas las pestañas ofrecen renombrar y cerrar como **acciones de accesibilidad**, así que ninguna función depende de ver el botón.
- **Gestos:** se conservan (tocar selecciona, doble toque renombra, pulsación larga y arrastre reordena).
- **Alternativas:** menú contextual por pulsación larga en todas (choca con el arrastre para reordenar).

### D-T22b-3 · 2026-10-04 · Sin desenfoque en la barra de pestañas
- **Decisión:** la barra usa el color `surface` del esquema, sin `backdropBar`. El desenfoque de T22a necesita contenido que pase *por debajo* de la barra, y aquí la barra no se solapa con el terminal (el cálculo de la rejilla la excluye). Solaparlas cambiaría el cálculo del tamaño del pty y no se puede validar sin dispositivo.
- **Pendiente:** decidir en T22c / una vez probado en dispositivo si el terminal se dibuja bajo la barra.

### D-T22b-4 · 2026-10-04 · Teclas extra como teclas de teclado
- **Decisión:** cada tecla es una tecla redondeada con una línea inferior (el filo de una tecla), sobre una bandeja del color `surface`. Los colores salen del dominio (`ChromeColorsFor`: `key`, `keyPressed`, `onKey`): la tecla es **más clara que la bandeja** en esquemas claros y oscuros, y más oscura al pulsarla; el texto cumple contraste ≥ 4,5:1 en todos los esquemas incluidos (test). Ctrl/Alt armados y bloqueados siguen con el acento. `onlyWithKeyboard` se mantiene.
- **Háptica:** la de `IosPressable` en cada tecla, como el clic de un teclado.

### D-T22b-5 · 2026-10-04 · Menús y avisos con los componentes iOS
- **"+" y paneles:** `IosContextMenu` con iconos Lucide a la derecha (D-T22a). Las entradas se construyen como lista para que la última no lleve separador. **Renombrar:** alerta iOS con campo de texto (`NamePrompt`: título, campo, nota y dos botones lado a lado), que toma el teclado sola; **cerrar:** `IosAlert` con el botón destructivo en rojo. **Aviso de lanzamiento:** tarjeta redondeada con sombra sobre las primeras filas; el icono va en rojo si es un problema.
- **Botón de paneles:** objetivo de 48 dp con una cápsula de 30 dp dentro (cubre menos texto) y solo con paneles divididos.
- **No tocado (T22c):** los diálogos de `SessionPrompts` (permisos), Distros, SSH, Apariencia y Ajustes.

### D-T22b-6 · 2026-10-04 · Refactor forzado por detekt
- `NamePrompt` recibe sus textos agrupados (`NamePromptTexts`), `TabChip` se parte en `TabChip`, `TabLabel` y `tabSemantics` con dos clases de contexto, los avisos y alertas de pestañas viven en `TabPrompts.kt`, y `TerminalScreen` agrupa lo que pasa a `TerminalContent`. Ninguna regla se relajó.

### D-T22b-7 · 2026-10-04 · Qué NO está validado (sin dispositivo)
- El aspecto real de las cápsulas, las teclas y los menús en un móvil y una tablet, en claro, oscuro y OLED.
- La alerta de renombrar con el teclado (que el campo se vea y no lo tape), y el foco automático del campo.
- Que el menú contextual salga en el sitio que se espera desde el borde de la pantalla (se ancla a la esquina superior derecha de su botón).
- Los gestos de las pestañas con la cápsula, y TalkBack con las acciones personalizadas.
- Que el círculo con "⋮" desaparezca (D-T09b-3).

### D-T22b-8 · 2026-10-04 · Lección: pruebas deterministas
- Esta rama no añade tests que dependan del reloj, del azar ni del tamaño de archivos comprimidos (la cobertura crítica fue intermitente por eso; ver D-T15-*/la PR de fixtures deterministas).

## Primeras pruebas en hardware (Pixel 8, 2026-10-04)

Pruebas manuales del orquestador en un **Pixel 8** (Android 17, API 37, arm64, app debug con `targetSdk` 28), con `adb` y capturas de pantalla. Los agentes de desarrollo no tocan dispositivos.

### Verificado en el dispositivo
- **Arranque y PTY (T03):** la app arranca en ~540 ms; la primera pestaña es un shell con PTY real (`uname -a` devuelve el kernel real, aarch64).
- **Redimensionado (T04):** con el teclado abierto, `stty size` da 18×49 y coincide con lo visible (la vista se encoge sobre el teclado).
- **Servicio en primer plano (T08):** corre con `targetSdk` 28 en Android 17 (`isForeground=true`, tipo `specialUse`, canal `sessions` con 2 acciones).
- **Pestañas (T09):** crear y cambiar de pestaña funcionan.
- **Distros (T06/T07):** instalar Alpine 3.24.2 desde la app (descarga, SHA-256, extracción) tarda ~3 s y la deja lista y predeterminada.
- **proot (T02/T08b):** una pestaña nueva abre `localhost:~#`, un shell root de Alpine bajo proot; al cerrar del todo la app y reabrirla, la primera pestaña vuelve a ser la distro (6 de 6 arranques).
- **Herramientas dentro de la distro:** `apk update` (28 553 paquetes) y `apk add` de 41 paquetes con red y DNS del dispositivo; OpenSSH 10.3, Python 3.14.8 y nmap 7.99 funcionan. `free -m` y `uptime` funcionan.

### Fallos encontrados (y su estado)
- **Crash al instalar una distro:** `SecurityException: getFileStore` (Android prohíbe leer `/proc/mounts` a las apps). Corregido con `StatFs` (PR #19).
- **Lo escrito no aparecía hasta ocultar el teclado:** `setComposingText` no enviaba el texto hasta `finishComposingText`. Corregido enviando la composición al momento (`ComposingText`, PR #23). **Pendiente de comprobar en el dispositivo.**
- **Pestaña nueva en blanco hasta cambiar de pestaña y volver:** causa raíz en `SessionManager.activeHost` (T09b). Corregido en T22b; pendiente de comprobar.
- **Diálogo de batería** abría una lista general de Ajustes: se cambia al diálogo del sistema (T08c).
- **Título de Distros solapado con el botón de instalar:** T08c y T22c.
- **La fila de teclas seguía visible con el teclado oculto:** corregido (`onlyWithKeyboard`, PR #22); pendiente de comprobar.
- **`/proc` parcial:** `stat`, `loadavg` y otros dan `Permission denied` dentro de proot (SELinux), como en Termux; `top` y `htop` pueden fallar sin ficheros falsos montados encima (T08c).

### Sin probar todavía
Paneles divididos (T10), colores y fuentes (T12/T12c), montaje de `~/storage` (T13), hosts y claves SSH (T14), copias de seguridad (T15), gestos y teclado físico, y **todo lo de la tablet** (redimensionado, multiventana, paneles anchos).

### Segunda ronda (Pixel 8, 2026-10-04, APK de `master` con T22b y T09b)

Verificado en el dispositivo con el APK de `master` en `b6e3475`:

- **Entrada con el teclado abierto (arreglo #23):** lo escrito aparece al instante, antes de pulsar Enter (`echo primero`, `echo segundo` y su salida, y `echo tercero` mientras se teclea). Ya no hay que ocultar el teclado.
- **La fila de teclas sigue al teclado (RF-08):** con el teclado abierto se ve (teclas redondeadas en gris derivado del esquema) y al ocultarlo desaparece y el terminal ocupa toda la pantalla.
- **Pestaña nueva (T09b):** pulsar "+" crea "Alpine Linux 2" y dibuja su prompt a la primera, sin cambiar de pestaña. Las pestañas se nombran con su distro (RF-13).
- **Paneles divididos (T10):** "Dividir a la derecha" crea un segundo panel con su propio shell de Alpine (3 procesos proot en total); el borde morado marca el foco, la entrada va al panel con foco y las líneas largas hacen wrap.
- **Apariencia (T12 y T12c):** la pantalla se abre desde el menú del "+"; muestra una vista previa con colores ANSI, el tema (sistema/claro/oscuro), el interruptor de Negro OLED, los colores dinámicos y la lista de esquemas. Elegir Solarized Dark repinta al instante todos los paneles y la barra de pestañas toma el acento del esquema; devolver Dracula restaura el fondo `#282A36`.
- **Menú del "+" (T22b):** menú contextual oscuro con esquinas redondeadas e iconos de línea: Shell de Android, la distro, dividir a la derecha y hacia abajo, SSH, Gestionar distros y Apariencia.

Defectos vistos (sin corregir todavía):
- **El botón `⋯` del panel con foco tapa el final de la primera línea** cuando el texto llega al borde derecho.
- **El diálogo "Mantener las sesiones activas" y la pantalla de Apariencia siguen con el aspecto de Material;** las rehacen T22c y T16.
- **No comprobado todavía:** el rendimiento al desplazar con la barra translúcida, el repaso de TalkBack, la rotación y todo lo de la tablet.

### D-REQ-4 · 2026-10-04 · Fedora pasa del backlog a tarea (T24)
- **Decisión:** el usuario dijo que su distro fetiche es Fedora y preguntó por qué no estaba. En la entrevista inicial se dejaron Arch y Fedora en el backlog (SPEC §2); ahora Fedora entra en el plan como T24. Arch sigue en el backlog.
- **Por qué no era trivial:** las imágenes de Fedora vienen en tar.xz y el extractor de T07 solo lee gzip (D-T07-3), así que T24 incluye descompresión xz con las mismas reglas de seguridad.
- **Impacto:** SPEC §2 y PLAN actualizados. Se acreditará `org.tukaani:xz` en `THIRD_PARTY_NOTICES.md`.

## T12b — Perfiles, layouts, emisión y atajos (solo dominio, sin interfaz)

Todo vive en `domain/profile`, `domain/broadcast` y `domain/terminal/ShortcutConflicts.kt`, sin tocar ninguna pantalla,
`SessionController` ni `TabsController`. 51 tests de host nuevos, deterministas (sin reloj ni azar). Sin validar en
dispositivo: no hay nada que ejecutar todavía, falta la interfaz.

### D-T12b-1 · 2026-10-04 · En un perfil, los valores por defecto guardados significan "usar el ajuste global"
- **Decisión:** `Profile` conserva `colorSchemeId = "default"`, `fontFamily = "monospace"` y `fontSizeSp = 14` como "no
  personalizado". `PaneSpecResolver` los traduce a `null` en `PaneLook` y solo un valor distinto sustituye al global.
  Así cambiar el esquema o la fuente globales (T12/T12c) sigue llegando a los paneles que no los personalizaron.
- **Motivo:** el perfil se guardó en T05, antes de que existiera la apariencia global; cambiar su esquema de Room para
  admitir `null` obligaba a una migración. El coste es que un perfil no puede pedir "exactamente 14 sp" cuando el
  global es otro: si hiciera falta, habrá que migrar el campo a anulable (la clave del problema está en `Profile.kt`).
- **Un esquema o fuente que ya no existen** (uno importado y borrado) no rompen el panel: se usa el global y sale un
  `PaneNotice.SchemeMissing`/`FontMissing`. Un número fuera de rango se acerca a su rango con `ValueAdjusted`.

### D-T12b-2 · 2026-10-04 · El comando de arranque se escribe en el shell, no se ejecuta en su lugar
- **Decisión:** `PaneSpec.startupInput` es el comando seguido de Enter (`\r`), que la sesión teclea cuando el shell ya
  corre. No se lanza `sh -c <comando>`.
- **Motivo:** el shell sigue siendo interactivo (si el comando termina o falla queda un prompt), no hay nada que
  entrecomillar, y la entrada no pasa por la línea de comandos de proot. El coste: el comando se ve en pantalla y
  depende de que el prompt esté listo (la UI debe teclearlo tras el primer prompt o con un breve margen).
- **Qué se acepta** (`StartupCommand`): una sola línea, sin saltos (se ejecutarían varios comandos desde un campo
  pensado para uno), sin caracteres de control (un Tab pediría completar; un Escape manejaría el shell) y de hasta
  1000 caracteres. En blanco significa "ninguno"; el comando de un panel de un layout sustituye al del perfil, y uno en
  blanco lo anula.

### D-T12b-3 · 2026-10-04 · Un layout guardado siempre se abre: lo que falla se degrada y avisa
- **Decisión:** `PaneSpecResolver.resolve` rechaza con un `ProfileProblem` sellado (distro inexistente, distro no lista,
  usuario no válido, comando no válido), nunca con una excepción. `resolveOrDegrade`, que usa la restauración, abre ese
  panel como el perfil por defecto y **sin comando de arranque**, con `PaneNotice.Degraded(motivo)`; un perfil
  borrado da el perfil por defecto, conserva el comando del propio layout y avisa con `ProfileMissing`.
- **Motivo:** un layout viene del disco, de una copia o de otro dispositivo, y no debe perderse entero porque una
  distro ya no esté. Un comando escrito para una distro que no existe podría hacer daño en otra, así que no se
  conserva al degradar.
- **Alternativa descartada:** negarse a abrir el layout. Se pierde la estructura por un solo panel roto.

### D-T12b-4 · 2026-10-04 · Un layout es un dato no fiable: tamaño acotado y proporciones saneadas
- **Decisión:** `LayoutRestorePlanner` rechaza (`LayoutRefusal`) un árbol de más de 16 paneles (`TOO_MANY_PANES`) o de más
  de 8 niveles de división (`TOO_DEEP`; la comprobación se detiene en el límite, así que su recursión está acotada
  aunque el JSON sea malicioso). Una división cuya parte sea menor que el 10 % se lleva al 10 % con un aviso
  `RatioAdjusted`; una cercana al 50 % (±3 %) se ajusta a la mitad sin avisar, como al arrastrar el separador.
- **Compatibilidad:** se probó con `LayoutCodec` (T05) la ida y vuelta y la lectura de un JSON antiguo, con paneles sin
  perfil ni comando y con una clave que una versión futura podría añadir.
- **Números:** 16 paneles y 8 niveles son límites de seguridad, no de diseño; se pueden subir sin migrar nada.

### D-T12b-5 · 2026-10-04 · Emisión a varios paneles: solo en memoria y con frenos
- **Decisión:** `BroadcastState` (`Off`, `AllPanes`, `Group(nombre)`) no se guarda: una emisión que sobreviviera a un
  reinicio escribiría en servidores por sorpresa. `targets(activo, paneles, tipo)` devuelve a qué paneles va lo escrito,
  en el orden de la pestaña y nunca vacío. Frenos:
  - con un solo panel no hay a quién emitir;
  - con `textOnly` (por defecto) las teclas de control (Ctrl/Alt, Esc, flechas) solo van al panel activo, de modo que un
    Ctrl+C o una flecha pensados para un servidor no llegan a los demás; texto pegado o tecleado sí se emite;
  - un panel fuera del grupo no se cuela en él, y un panel que no está en la pestaña solo se recibe a sí mismo.
- `pruned(vivos)` olvida los paneles cerrados (el número de un panel cerrado no debe coincidir con uno nuevo) y apaga una
  emisión a un grupo que se quedó sin paneles. `isEmitting(paneles)` permite a la interfaz mostrar de forma clara que
  teclear llegará a otros: es el aviso que no debe faltar.
- **Alternativa descartada:** un modo "todos los paneles de todas las pestañas": demasiado peligroso para el primer
  corte; se puede añadir como otro `BroadcastMode`.

### D-T12b-6 · 2026-10-04 · Atajos: tres acciones nuevas y los conflictos son avisos
- **Acciones nuevas** en `AppShortcut` (los `when` de `ShortcutHandler` tienen `else`, así que quedan sin efecto hasta
  que la interfaz las conecte): `ToggleBroadcast` (`Ctrl+Shift+B`), `SaveLayout` (`Ctrl+Shift+S`) y `OpenLayouts`
  (`Ctrl+Shift+L`). Terminator usa Alt+letra, pero Alt+letra es el Meta de readline (Alt+b y Alt+f mueven por palabras),
  así que van con Ctrl+Shift, como el resto de los de paneles (D-T11-4).
- **Detección** (`ShortcutConflicts.kt`): `terminalConflict()` marca Ctrl+letra sin más modificadores
  (`StealsControlKey`: Ctrl+C interrumpe, Ctrl+D cierra la entrada) y Alt+letra o Alt+dígito sin Ctrl
  (`StealsReadlineMeta`). Son avisos, no rechazos: la SPEC pide Alt+dígito para elegir pestaña (D-T11-4), y los únicos
  atajos por defecto que chocan son esos nueve. `bindChecked` devuelve el mapa nuevo, lo que reemplazó y el coste, o
  `Refused` si la combinación robaría teclado normal (antes lanzaba una excepción). `ShortcutText.analyze` detecta una
  combinación que el texto guardado da a dos acciones distintas (gana la última línea, como en `parse`).
- **Una combinación para dos acciones** no puede darse dentro de `ShortcutMap` (es un mapa por combinación); varias
  combinaciones para una acción sí (Copiar tiene dos por defecto).

### D-T12b-7 · 2026-10-04 · Lo que NO se hizo: persistir los atajos y meterlos en la copia de seguridad
- **Hallazgo:** los atajos no se guardan en ninguna parte hoy (`InputRouter` arranca con `ShortcutMap.defaults()`), y
  `ConfigSnapshot` (T15) no lleva atajos, ni las teclas extra, ni la apariencia de T12c. Los perfiles y los layouts **sí**
  van ya en la copia: los layouts refieren a los perfiles por posición, no por id, así que viajan bien.
- **Decisión:** no tocar `data.backup`. Su cobertura crítica exige el 100 % de ramas y no existe aún un almacén de
  atajos, así que añadirlo allí sin almacén sería código muerto o sin probar. Queda definida la interfaz `ShortcutStore`
  (como `ExtraKeysStore`) y el formato de texto ya existente (`ShortcutMap.serialize/parse`).
- **Actualización (2026-10-05):** hecho, ver D-T12b-8. La lista "Lo que debe hacer la interfaz posterior" de abajo está
  hecha, salvo lo que D-T12b-9 y D-T12b-16 dejan fuera.
- **Para completarlo:** una clave en el repositorio de ajustes con el texto de `ShortcutMap.serialize()`; añadirla a
  `SettingsDto` o a un campo nuevo de `ConfigSnapshot` (la lectura es indulgente: un campo que falta no rompe una copia
  antigua); `ConfigCollector` la lee y `ConfigApplier` la aplica con `ShortcutText.analyze` para no perder las líneas
  buenas; test de ida y vuelta, y mantener el 100 % de `data.backup`.

### Lo que debe hacer la interfaz posterior
1. **Abrir un panel con un perfil:** llamar a `PaneSpecResolver.resolve(perfil)` con las distros, los esquemas y las
   fuentes conocidos; mostrar un `Rejected` con un mensaje por cada `ProfileProblem`, y los `PaneNotice` como aviso
   no bloqueante. Construir la sesión con el lanzamiento que ya existe a partir de `PaneSpec.target`, aplicar `look` a
   ese panel (hoy el esquema y la fuente son globales: el pintor debe admitir un `PaneLook` por panel) y teclear
   `startupInput` tras el primer prompt.
2. **Pantallas de perfiles** (lista, alta, edición, borrado con confirmación; `Profile` ya valida nombre y rangos) y de
   **layouts** (lista, guardar con `LayoutSaving.build` desde el árbol de la pestaña y `PaneDescription` de cada panel,
   renombrar, borrar, abrir).
3. **Abrir un layout:** `LayoutRestorePlanner.plan` con los perfiles por id; si devuelve `Refused`, decirlo; si no, crear
   una pestaña con `paneNodeOf(...)`/las sesiones en orden de lectura (`LayoutRestorePlan.panes`) y mostrar los
   `LayoutNotice`, que indican el panel por su posición.
4. **Emisión:** un `BroadcastState` por pestaña en el ViewModel; enrutar lo tecleado por `targets(...)` (`TEXT` para
   texto y pegado, `CONTROL` para el resto); un indicador siempre visible mientras `isEmitting` sea cierto; asignar
   paneles a grupos desde el menú del panel; llamar a `pruned` al cerrar un panel y al cambiar de pestaña.
5. **Atajos:** conectar `ToggleBroadcast`, `SaveLayout` y `OpenLayouts` en `ShortcutHandler`; una pantalla de atajos que use
   `bindChecked` y muestre los `ShortcutConflict`; persistirlos y llevarlos a la copia (D-T12b-7).
6. **Sin validar:** tecleado del comando de arranque tras el primer prompt en un shell real, rendimiento de la emisión con
   varios paneles y la restauración de un layout de 16 paneles con proot en un dispositivo.

### D-T12b-8 · 2026-10-05 · Atajos: persistidos como un ajuste más y en la copia con un campo versionado
- **Decisión:** `AppSettings.shortcuts` (un `ShortcutMap`, ahora con igualdad por valor) se guarda en la tabla `setting` con la clave
  `shortcuts` y el texto de `ShortcutMap.serialize()`; sin clave, los atajos por defecto; al leer, una línea ilegible se
  salta y el resto se conserva. `TerminalViewModel` aplica el mapa al `InputRouter` en cuanto cambia.
- **Copia de seguridad:** `SettingsDto.shortcuts: ShortcutsDto?` (`version = 1`, `bindings` en el mismo texto). Es opcional y
  `ConfigCodec.VERSION` no cambia, como hizo D-T16-6. Al restaurar (`ConfigApplier`) se **conservan los atajos del
  dispositivo** si la copia no los trae, si su `version` no es la que esta app sabe leer (no se adivina un formato de una
  versión posterior) o si ninguna línea es utilizable; si trae algunas válidas, `ShortcutText.analyze` descarta las
  malas y se aplican las buenas. Todas esas ramas tienen test, y `data.backup` sigue al 100 % de línea y de rama.
- **`ShortcutStore`** (la interfaz que dejó D-T12b-7) se elimina: los atajos van por `SettingsRepository`, como las teclas extra.

### D-T12b-9 · 2026-10-05 · El esquema, la fuente y el tamaño de un perfil se guardan pero no se aplican por panel
- **Hallazgo:** los colores iniciales de un emulador se leen de `TerminalColors.COLOR_SCHEME`, un objeto **compartido por toda la
  librería** (`TerminalSessionHost.applyScheme` escribe ahí), y el pintor (`TerminalPainter`) es único para todos los paneles
  y toma la fuente y el tamaño globales. Un esquema por panel exige cambios en el emulador de Termux y un pintor por panel.
- **Decisión:** el formulario de perfil edita nombre, distro, usuario, historial y comando inicial, que sí se aplican
  por panel. El esquema, la fuente y el tamaño siguen en el modelo, se conservan al editar y viajan en la copia, pero no se
  editan ni se aplican; el formulario lo dice en una nota. Cuando el pintor admita un `PaneLook`, bastará con añadir los campos.
- **Por qué no fingirlo:** una lista de ajustes que no hacen nada sería engañosa (misma razón que D-T16-7).

### D-T12b-10 · 2026-10-05 · Cada panel recuerda con qué se abrió; el comando se teclea 0,8 s después de lanzar el shell
- **Decisión:** `SessionController` guarda un `PaneOpening` (el `PaneSpec` y el comando que le dio su layout) por sesión, hasta que se
  cierra. `PaneEditor` gana `openTab(PlannedNode)` (una pestaña con todos sus paneles, `Sessions.openedTab`),
  `splitActive(orientación, opening)` y `openingOf(id)`. `AndroidSessionFactory` lo lee al empezar: el historial del panel,
  el usuario (`ProotSessionPlanner.plan(distro, user)`, que revalida el nombre) y el comando.
- **Comando inicial:** `AndroidSessionFactory` escribe `startupInput` 800 ms después de lanzar el shell, solo si hubo lanzamiento
  (no con un fallo). La pty guarda lo escrito hasta que el shell lo lee, así que un proot lento no lo pierde; la pausa evita que caiga
  en medio de la salida de arranque. **Sin validar con un shell real.**
- **Guardar:** un panel se describe con su perfil y su comando propio, no con el del perfil (`PlannedNode.Pane.command`), de modo que
  guardar de nuevo un layout restaurado lo deja igual. Un panel sin perfil se guarda sin distro (`LayoutNode.Pane` solo tiene
  perfil y comando): al reabrirlo usa la distro predeterminada, aunque se hubiera abierto en otra. Se anota como límite.
- **Dividir con un perfil** abre el panel nuevo en la distro del perfil, no en la del panel dividido; sin perfil sigue siendo la del origen.

### D-T12b-11 · 2026-10-05 · Abrir un layout siempre abre una pestaña nueva y avisa de lo que cambió
- **Decisión:** `PaneOpener.restore` planifica con `LayoutRestorePlanner` y abre **una pestaña nueva** (nunca reemplaza la actual ni sus
  paneles); la pantalla de layouts se cierra sola si no hubo cambios y, si los hubo (perfil borrado, distro no lista, división
  corregida), los lista en una alerta antes de cerrar (`LayoutNotice`, panel y división contados desde 1). Un layout rechazado
  (`TOO_MANY_PANES`, `TOO_DEEP`) no abre nada y lo dice.
- **Perfiles:** abrir un perfil (en pestaña, o en una división a la derecha o debajo) usa `PaneSpecResolver.resolve`; si lo rechaza
  (distro borrada o no lista, usuario o comando no válidos) se explica y no se abre nada.

### D-T12b-12 · 2026-10-05 · Guardar un layout: nombre único, reemplazo con confirmación, siempre el árbol completo
- **Decisión:** `LayoutSaver.save(nombre, reemplazar)` guarda el árbol de la pestaña activa (entero, aunque un panel esté ampliado) con
  `LayoutSaving.build`. Un nombre en uso (sin distinguir mayúsculas) falla con `NameTaken` y la hoja pregunta "¿Reemplazar?"; solo
  entonces se llama con `reemplazar = true`. Desde la lista, "Reemplazar con los paneles de esta pestaña" sobrescribe sin hoja.
  `asLayoutSaveProblem` traduce el error al mensaje (nombre, en uso, sin pestaña, comando, límite de 100, otro).

### D-T12b-13 · 2026-10-05 · Emisión: interruptor en el menú del panel, indicador rojo y qué teclas se emiten
- **Decisión:** `BroadcastController` (uno por proceso, estado por pestaña, solo en memoria) decide a qué paneles va lo tecleado.
  `TerminalOutput` gana `write(texto, InputKind)` y `writeCodePoint(…, InputKind)` (con implementación por defecto que ignora el
  tipo); `ActiveSessionOutput` reparte a `targets(tipo)` y el pegado va por `pasteFromClipboard`.
- **Qué teclas:** `KeyInput.inputKind()`: Ctrl o Alt, Esc, flechas, Inicio, Fin, Re/Av Pág, Insertar, Suprimir y F1 a F12 son `CONTROL`
  (solo al panel activo); letras, Enter, Tab, Retroceso y texto son `TEXT` (a todos). Así Enter, Tab y Retroceso, que se necesitan para
  escribir una orden en varios servidores, sí llegan, y Ctrl+C no.
- **Frenos añadidos:** con un solo panel no se puede activar (`toggle` no hace nada) y una pestaña que se queda con un panel olvida su
  emisión, para que dividir de nuevo más tarde no teclee en el panel nuevo sin que el usuario lo pida.
- **Indicador (obligatorio):** una cápsula roja arriba a la izquierda, "Lo que escribes llega a N paneles", mientras `isEmitting`; tocarla
  detiene la emisión (48 dp, con descripción para TalkBack). Los paneles que reciben llevan un borde rojo.
- **Grupos:** tres nombres fijos (Grupo A, B, C) desde "Grupo de este panel…" y "Escribir en el grupo de este panel"; no se expone
  `textOnly` (queda en `true`). El menú del panel solo aparece con la pestaña dividida (T10), que es cuando la emisión tiene sentido;
  el atajo `Ctrl+Shift+B` funciona siempre (sin efecto con un panel).

### D-T12b-14 · 2026-10-05 · Atajos editables: una hoja por acción, con vista previa de lo que cuesta
- **Decisión:** Ajustes > Teclado > Atajos lista todas las acciones (también las que se quedaron sin combinación); tocar una abre una
  hoja con sus combinaciones (tocar una pide quitarla) y un campo donde se **pulsan las teclas** en un teclado físico
  (`onPreviewKeyEvent`, solo con Ctrl o Alt) o se **escribe** (`ctrl+shift+t`). Antes de añadir, `ShortcutEditing.preview` dice si es
  ilegible, si falta Ctrl o Alt, si ya la tiene la acción, qué otra acción la pierde y si roba una tecla de control o la Meta de
  readline (avisos, no rechazos, D-T12b-6). "Restaurar los atajos por defecto" pide confirmación.
- **Los números de pestaña** (Alt+1 a 9) siguen siendo una fila de solo lectura; hacerlos configurables exige un modelo de "prefijo +
  1 a 9" que no está. `ShortcutDisplay.allRows` lista todas las filas; `rows` solo las que tienen combinación.
- **Sin validar:** la captura con un teclado físico real y con el IME (una combinación que el sistema se queda no llega a la app).

### D-T12b-15 · 2026-10-05 · Dónde se llega a cada pantalla
- **Perfiles y layouts:** filas en la raíz de Ajustes, entradas en el menú "+" de la barra de pestañas (con "Guardar el layout…") y en el
  menú del panel; `Ctrl+Shift+S` pide el nombre y `Ctrl+Shift+L` abre los layouts (`TerminalRequest`, que el `TerminalViewModel`
  emite y la pantalla convierte en abrir una hoja de la actividad). `ScreenLinks` agrupa estos tres enlaces en `ProfileLinks`, y
  `TabBarLinks` lleva el `ScreenLinks` entero, por el límite de parámetros de detekt.
- **Pantallas** con los componentes de `ui/ios` (T22a/c) y cadenas en `values/` y `values-es/`; las alertas y hojas siguen la
  convención de que la hoja de acciones se cierra sola tras la acción (`closeActions` mira el estado actual).

### D-T12b-16 · 2026-10-05 · Qué queda por validar en un dispositivo
- Comando inicial tecleado tras arrancar el shell (incluido con proot lento); emisión con varios paneles de proot y el rendimiento;
  restaurar un layout de 16 paneles; el aspecto de las hojas (perfil, layouts, atajos) y de la cápsula roja, y que no tape texto;
  la captura de teclas en un teclado físico; y TalkBack en las pantallas nuevas.
- **Cobertura:** la lógica nueva está en `domain` (`PaneOpener`, `LayoutSaver`, `ProfileForm`, `BroadcastController`, `ShortcutEditing`,
  `Sessions.openedTab`) con tests de host; los ViewModels y las pantallas Compose no entran en la cobertura, como el resto de la UI.

## T23 — Limpieza pendiente

### D-T23-1 · 2026-10-04 · Tests con base de datos real: `runDatabaseTest` con margen de 5 minutos
- **Problema:** `MigrationTest` y `LayoutAndSettingsRepositoryTest` (y otros seis ficheros de tests de T05, T08b, T12 y T12c) fallaban a veces con `UncompletedCoroutinesError: After waiting for 1m` cuando la máquina estaba cargada (carga de 90 en 14 núcleos; pasaban solos en 47 s). Room abre SQLite en un hilo real y cargar su librería nativa y tocar el disco puede pasar de 1 minuto, que es lo que `runTest` espera por defecto.
- **Decisión:** `runDatabaseTest` en `data/local/TestDatabase.kt`, un `runTest(timeout = 5.minutes)`, usado en los **ocho** ficheros y en el contrato `DistroRepositoryContract` que abren una base de datos real. No se cambia el dispatcher: la espera sigue siendo de pared, porque lo que se prueba es la E/S real.
- **Alternativas descartadas:** un dispatcher de prueba que no espere (los tests dejarían de ejercitar Room de verdad); subir el timeout de todos los `runTest` (taparía un bloqueo real en tests que no usan base de datos y no tienen ese problema).
- **Impacto:** un test realmente colgado sigue acabando a los 5 minutos con el mismo error. Ningún test depende del reloj.

### D-T23-2 · 2026-10-04 · Avisos de licencia Apache: medidos, no supuestos
- **Medido en el APK de release y en los jars de la clasepath de ejecución (245 dependencias):** solo **cinco** publican su propio `NOTICE`/`LICENSE` en `META-INF`: Commons Compress, IO, Codec y Lang, y Jakarta Inject. El resto (OkHttp, Okio, kotlinx, AndroidX, Hilt, Room, Compose…) no trae ningún `NOTICE` propio; solo AndroidX deja un `LICENSE.txt` con la Apache-2.0 estándar, que sí llega al APK.
- **Estado en el APK:** de esas cinco, el empaquetado solo conserva por casualidad el `NOTICE.md` de Jakarta; las cuatro de Commons no llegan. T07 (D-T07-8) ya las empaquetó a mano en `res/raw`.
- **Decisión:** el mismo fichero (renombrado a `res/raw/third_party_notices_apache`) lleva ahora también el `NOTICE` y la licencia de Jakarta Inject, copiados sin modificar de su jar. Con esto queda **cerrado** el punto abierto de D-T07-8 sobre OkHttp/Okio: no publican aviso, así que no hay nada que conservar de ellas más allá de su mención en `THIRD_PARTY_NOTICES.md`.
- **API para la pantalla "Acerca de" (T16):** `LicenseTexts` en `domain/license` (inyectable con Hilt, ya hay `LicenseModule`). `entries` lista los avisos (`id`, título y licencia SPDX), `read(id)` devuelve el texto y `missing()` los que no estén. `Z`/T16 solo tiene que mostrar `entries` y, al tocar una, abrir `read(id)`. Tests de host: leen los ficheros reales del árbol de fuentes y fallan si un texto desaparece o un recurso se renombra.
- **Sin validar en dispositivo:** que `AndroidLicenseSource` encuentre los recursos en el APK de release con el *shrinker* (se comprobó con `aapt2` que `raw/third_party_notices_apache` existe en el APK; la lectura en ejecución no).
- **No es asesoría legal:** se cumple lo que las licencias piden (conservar el aviso al redistribuir en binario); conviene que alguien lo revise antes de publicar.

### D-T23-3 · 2026-10-04 · GitGuardian: frase de prueba renombrada y `.gitguardian.yaml` documentado
- **Hallazgo:** dos falsos positivos de "Generic Password" (el literal `"s3cret"` de `BackupExportTest` y el campo `passwordFor` de `BackupViewModel`) bloquearon varias PRs. No son credenciales.
- **Decisión:** el literal pasa a ser una constante con un comentario (`TEST_PHRASE = "open sesame"`), sin cambiar el comportamiento del test, y se añade `.gitguardian.yaml` que excluye **solo** `data/backup/**` de los tests, con la razón escrita en el propio fichero. `passwordFor` se deja como está: es un nombre de campo, no un valor.
- **Límite:** la exclusión no cubre código de producción ni otros tests; una credencial real en cualquier sitio seguiría marcándose. Si GitGuardian no lee ese fichero en este plan, los incidentes se descartan en su panel (incidentes 37863678 y 37863679).

## T22d — Estilo de las teclas extra

El usuario probó la app en un Pixel 8 y dijo que las teclas especiales (Esc, Tab, Ctrl, flechas…) tenían "un acabado muy feo". Eran cápsulas del color `surfaceVariant` de Material con una línea de sombra inferior de 1 dp: un efecto de bisel que contradice el estilo plano de iOS, y un gris que no pertenece ni al esquema ni al teclado azul marino de Gboard que queda justo debajo. Pidió **elegir el estilo en Ajustes, con las planas por defecto**.

### D-T22d-1 · 2026-10-04 · Tres estilos, `FLAT` por defecto
- **Decisión:** `ExtraKeyStyle { FLAT, CAPSULE, CLASSIC }`, con `FLAT` por defecto; lo que no se reconoce o falta se lee como `FLAT`.
  - **FLAT:** sin fondo por tecla; solo el símbolo (peso medio), separadores finos (0,5 dp, 20 % de opacidad) entre teclas y filas, y un óvalo tintado con el acento (18 %) al pulsar. Ctrl/Alt armados son una cápsula rellena con el acento; **bloqueados**, la misma más un subrayado bajo la etiqueta, para que la diferencia no dependa solo del color.
  - **CAPSULE:** cada tecla es una cápsula del color del texto al 10 % de opacidad, sin sombra, con 6 dp de separación entre teclas; armada, el acento al 28 %.
  - **CLASSIC:** el aspecto anterior (cápsula con filo inferior), pero con los colores del **esquema**: tecla = fondo mezclado con el primer plano al 12 %, filo = fondo oscurecido al 40 %. Ya no sale de `surfaceVariant`.
- **Motivo:** que el usuario elija, y que ninguna variante sea un gris ajeno.
- **Impacto:** `TerminalAppearance.extraKeyStyle`; se guarda en `appearance_extra_key_style` (mismo patrón que el resto de la apariencia de T12c).

### D-T22d-2 · 2026-10-04 · La lógica de color es pura y está probada con todos los esquemas
- **Decisión:** `ExtraKeyPaletteFor.of(KeyChromeInputs, style)` en `domain/appearance` devuelve colores ARGB opacos (mezclas precompuestas, no transparencias), y `ExtraKeyPalette.look(latch, pressed)` dice cómo se ve una tecla según su estado. Las entradas son el fondo y el primer plano del esquema y los colores de las barras, tomados del esquema o del tema del sistema (`ChromeStyle.SYSTEM`), para que el mismo estilo valga con los dos.
- **Pruebas:** contraste WCAG ≥ 4,5:1 de la etiqueta sobre su fondo en reposo y pulsada, de la etiqueta de una tecla armada y de una bloqueada, para **todos los esquemas incluidos y su variante OLED, por cada uno de los tres estilos**. El texto de Solarized, de contraste bajo por diseño, se empuja hacia negro o blanco solo lo necesario (`readableOn`).
- **Cambio:** los colores `key`, `keyPressed` y `onKey` de `ChromeColors` y `ChromePalette` desaparecen (solo los usaba la fila y habrían quedado muertos); sus tres tests los sustituyen los nuevos.

### D-T22d-3 · 2026-10-04 · Desviación: la bandeja no es translúcida ni se desenfoca
- **Pedido:** barra translúcida con desenfoque desde la API 31 y color sólido en 26–30, reutilizando T22a y T22b.
- **Qué pasa:** el desenfoque de T22a (`BackdropState`) desenfoca el contenido que pasa **por debajo** de la barra. Esta fila va debajo del terminal, sin solaparlo, así que no hay nada que desenfocar.
- **Decisión:** la bandeja de `FLAT` es el fondo del terminal mezclado un 4 % con el primer plano (los otros estilos, un 7 %), con la línea fina superior de siempre. Así se lee como parte del terminal y no como una losa gris. Si algún día la fila se superpone al contenido, se puede pasar a `BackdropState`.

### D-T22d-4 · 2026-10-04 · Selector reutilizable: `ExtraKeyStylePicker`
- **API:** `ExtraKeyStylePicker(settings: AppSettings, onStyle: (ExtraKeyStyle) -> Unit, modifier)` en `ui/ExtraKeyStylePicker.kt`. Muestra un `IosSegmentedControl` con las tres opciones y debajo **una vista previa real** (Esc, Tab, Ctrl armado y dos flechas) en los colores del esquema actual; la vista previa es la propia `ExtraKeysRow` sobre el fondo del terminal.
- **No guarda nada:** lee `settings` y avisa con `onStyle`. La pantalla de Apariencia la muestra en la sección "Teclas especiales" (`KeysSection`); **Z puede enlazarla desde Ajustes > Teclado** llamándola igual, con el mismo `update { it.copy(appearance = it.appearance.copy(extraKeyStyle = style)) }`.
- **Accesibilidad:** las opciones miden 48 dp; la vista previa lleva una descripción y no expone sus teclas, que no hacen nada.

### D-T22d-5 · 2026-10-04 · Copia de seguridad
- `SettingsDto.extraKeyStyle` (texto, por defecto `FLAT`). Una copia anterior, sin el campo, se lee como `FLAT`; un valor desconocido también. Se aplica al restaurar. Con ida y vuelta probada.
- **Lo que esta tarea no resuelve:** el backup de T15 **tampoco guarda el resto de la apariencia de T12c** (fuente, márgenes, cursor, estilo de barras), a pesar de lo que dice D-T12c. Solo he añadido mi campo; el resto es trabajo aparte.

### D-T22d-6 · 2026-10-04 · Geometría
- Cada fila mide 48 dp, el objetivo táctil de siempre; la separación entre teclas va dentro de la celda. `FLAT` deja 4 dp de aire por lado, las otras 3 dp (6 dp entre dos cápsulas). La háptica y las acciones de accesibilidad (nombre de la tecla y estado armada/bloqueada) son las de T22b.

### D-T22d-7 · 2026-10-04 · Qué NO está validado (sin dispositivo)
- Cómo se ve cada estilo de verdad (sobre todo `FLAT` frente al teclado del sistema), el espesor de los separadores en pantallas de distinta densidad, el óvalo de pulsación, el subrayado de una tecla bloqueada, la vista previa en la pantalla de Apariencia y TalkBack. El orquestador lo prueba en el Pixel 8.

## T16 / T22c — Ajustes por secciones y pantallas en estilo iOS

Hecha en un clon aparte, sin dispositivos. Lo que solo puede decir el dispositivo está en la lista del final.

### D-T16-1 · 2026-10-04 · La pantalla de Ajustes es una pila de páginas, con la navegación como dato puro
- **Decisión:** `SettingsNavigation` (`domain/settings`) guarda la pila de `SettingsPage` con la raíz siempre abajo; "atrás" saca una página y desde la raíz cierra la pantalla. Se guarda con un `Saver` (nombres de página), así que sobrevive a girar el móvil. Cada página conoce a su padre (`parent`) para rotular el botón de volver.
- **Motivo:** la lógica de moverse entre páginas se prueba en host (no hay que ver la pantalla), y la UI solo la dibuja.
- **Alternativa descartada:** Navigation Compose: una dependencia nueva para 11 páginas sin argumentos.
- **Apariencia y Distribuciones** no son páginas de Ajustes: abren sus pantallas propias encima (T12c y T22c), y al cerrarlas se vuelve a Ajustes.

### D-T16-2 · 2026-10-04 · Dónde está el acceso: un ⚙ permanente junto al "+" y la primera entrada de su menú
- **Decisión:** `TabBar` pone un botón ⚙ de 48 dp, con `contentDescription`, en la ranura del final de la barra, siempre a la vista (en la barra superior y en la lateral). El menú del "+" lleva "Ajustes" como primera entrada que no es una pestaña ni una división; "Apariencia…" y "Gestionar distros…" siguen ahí y además se abren desde dentro de Ajustes.
- **Motivo:** el usuario no veía cómo entrar a Ajustes; el acceso solo estaba tras una pulsación larga del "+". RF-11 lo exige permanente.
- **Alcance:** esto toca `ScreenLinks`, `TabBarLinks`, `TabBar` y `NewTabMenu` (cada uno, unas líneas). Primero se hizo de forma aditiva para no pisar a T22b; al mergearse T22b, el icono se colocó directamente en su barra.

### D-T16-3 · 2026-10-04 · Los ajustes nuevos viven en la misma tabla clave-valor y las teclas extra pasan a guardarse
- **Hallazgo:** `ExtraKeysStore` era una interfaz sin implementación, así que la configuración de la fila de teclas no se guardaba nunca (`TerminalViewModel` arrancaba siempre con la de por defecto) y no había forma de cambiarla.
- **Decisión:** `AppSettings` gana `extraKeys` y `dnsFallbackServers`; se guardan en la tabla `setting` (`extra_keys` en su texto `visible=`/`onlyWithKeyboard=`/filas, y `dns_fallback` con las direcciones separadas por comas). Un valor ilegible da el de por defecto. `TerminalViewModel` lee las teclas del repositorio de ajustes (cambian en vivo); `ExtraKeysStore` se elimina.
- **Límites de edición** (`ExtraKeysEditing`): hasta 3 filas de 8 teclas, cada tecla del catálogo una sola vez. Quitar la última tecla de una fila quita la fila.

### D-T16-4 · 2026-10-04 · El historial (scrollback) era un ajuste que no hacía nada; ahora se aplica
- **Hallazgo:** `AppSettings.defaultScrollbackLines` existía, pero `TerminalSessionHost` usaba una constante de 10 000.
- **Decisión:** el host tiene `transcriptRows` y `AndroidSessionFactory` lo fija antes de lanzar el shell leyendo el ajuste (`SessionManager` lo inyecta). Se aplica a las pestañas que se abran después. Opciones: 1 000, 2 000, 5 000, 10 000, 20 000 y 50 000, que es el máximo que admite el emulador; un valor guardado fuera de rango se acerca al más cercano al mostrarse y se recorta (100–50 000) al usarse.
- **Sin validar:** el efecto en una sesión real y el consumo de memoria en 50 000 líneas.

### D-T16-5 · 2026-10-04 · DNS de respaldo editables, con validación estricta
- **Decisión:** `ResolvConf.render(servidores, respaldo)` usa el respaldo del usuario cuando el dispositivo no da ninguno, y los de siempre (1.1.1.1 y 9.9.9.9) si el suyo no tiene nada utilizable. Lo escrito en la hoja se lee con `DnsServers`: direcciones IP separadas por comas, espacios o `;`, máximo tres; una zona escrita a mano (`fe80::1%wlan0`) se rechaza, no se descarta en silencio. Vacío significa los de siempre.
- **Aviso en la pantalla:** los de siempre son resolutores públicos de terceros (Cloudflare y Quad9), como ya recogía D-T08b-6.

### D-T16-6 · 2026-10-04 · La copia de seguridad lleva ahora toda la configuración que le faltaba
- **Hallazgo:** T15 no incluía la apariencia de T12c, el modo de compatibilidad de proot, el DNS ni las teclas extra, que RF-06 exige.
- **Decisión:** `SettingsDto` gana cuatro campos **opcionales** (`prootCompatibilityMode`, `dnsFallbackServers`, `extraKeys`, `appearance`). La versión del formato no cambia: el lector ya ignora campos desconocidos y estos tienen valor por defecto. Una copia anterior que no los trae **deja lo que el dispositivo ya tiene**, no lo reinicia. Lo restaurado se valida: DNS por `DnsServers`, teclas por `ExtraKeysConfig.parse` (sin teclas útiles, las de por defecto) y números y nombres de la apariencia por `sanitized()`.
- **Fuentes importadas:** sus ficheros no viajan (son binarios de terceros, de los que la licencia es responsabilidad del usuario); si la fuente de la copia no existe en este dispositivo, se usa la incluida.

### D-T16-7 · 2026-10-04 · Atajos: una lista de consulta, no editable todavía
- **Decisión:** la página muestra los atajos por defecto en orden fijo, con las teclas con nombre legible (`Ctrl+Shift+T`) y los números de pestaña en una sola línea (`Alt+1–9`), y avisa de que no se pueden cambiar.
- **Motivo:** cambiarlos exige capturar combinaciones de un teclado físico y gestionar choques entre atajos; no se puede validar sin dispositivo y es el alcance de T12b. Mostrar una lista es veraz; fingir una edición no lo sería.

### D-T16-8 · 2026-10-04 · "Acerca de" lee `THIRD_PARTY_NOTICES.md` copiado al APK por Gradle
- **Decisión:** una tarea `copyNotices` copia el fichero de la raíz a los assets de cada variante en cada compilación (`addGeneratedSourceDirectory`), y `Notices` lo convierte en secciones (cabeceras `##`, tablas en una línea, sin marcas Markdown). Un test lee el fichero real, así que un cambio de su formato que rompa la pantalla se ve en el CI.
- **Motivo:** una sola fuente que mantener; una copia en el repositorio se desincronizaría y el APK llevaría créditos viejos.

### D-T16-9 · 2026-10-04 · Distros y copias en estilo iOS, y el error de encadenar diálogos
- **Decisión (T22c):** la pantalla de Distros es una lista con título grande; tocar una fila abre una hoja de acciones (predeterminada, renombrar, duplicar, eliminar), instalar y renombrar son hojas con campos y eliminar es una alerta. Las tarjetas de almacenamiento, copias y modo de compatibilidad salen de ahí y pasan a Ajustes. Los diálogos de exportar y de contraseña de las copias son hojas.
- **Error evitado:** `IosActionSheet` ejecuta la acción y **después** llama a `onDismiss`. Si la acción abre otro diálogo (renombrar), ese cierre lo deshacía. El cierre de la hoja solo cierra si lo vigente sigue siendo la hoja, leyendo el estado en el momento, no el capturado.
- **Componentes nuevos en `ui/ios`:** `IosTextField` (fila de formulario), `IosSheetHeader` (Cancelar, título y confirmar, con el botón inerte mientras no es válido) e `IosProgress`.
- **Sin migrar:** los diálogos de SSH (T14) y de apariencia (T12c) siguen en Material.

### D-T16-10 · 2026-10-04 · Lo que NO está validado (sin dispositivo)
- El aspecto y el tacto de todas las pantallas nuevas: listas agrupadas, hojas, alertas, el ⚙ en la barra superior y en la lateral.
- Las hojas con teclado: `imePadding` dentro de un `Dialog`, que el campo se vea con el teclado abierto y el comportamiento de los campos de contraseña.
- Los flujos del sistema: permiso de notificaciones, exención de batería, pantalla de la app, selector de archivos de las copias y apertura del enlace al código fuente.
- Que "atrás" del sistema se apile bien cuando Apariencia o Distribuciones se abren encima de Ajustes.
- TalkBack: orden de lectura, rol y estado de los interruptores, y las filas de teclas.
- El efecto real del historial, las teclas extra y el DNS en una sesión.

## T17 — Accesibilidad y rendimiento

### D-T17-1 · 2026-10-05 · Método: auditoría de código y lógica pura, sin pruebas de Compose en la JVM
- **Contexto:** el repositorio no tiene `ui-test` ni Robolectric (y añadir dependencias exige licencia y `THIRD_PARTY_NOTICES.md`). Las pruebas de diseño con `fontScale` 2,0 no se pueden hacer en el host sin ellas.
- **Decisión:** se auditaron a mano todos los composables (cromo del terminal, pestañas, teclas extra, menú de paneles, Ajustes, Apariencia, Distros, copias, SSH, `ui/ios`) buscando roles, estados, encabezados, semántica fusionada, acciones personalizadas, objetivos < 48 dp y texto con `maxLines = 1` o filas fijas que se cortan con fuente grande; lo que se pudo expresar como lógica va a `domain` con test. El contraste ya estaba cubierto (`IosPaletteTest`, `ChromeColorsTest`, `BuiltInSchemesTest` en todos los temas y esquemas).
- **Pendiente en dispositivo:** TalkBack de verdad y fuente 2,0 (Ajustes > Accesibilidad > Tamaño de fuente) en cada pantalla; ver D-T17-8.

### D-T17-2 · 2026-10-05 · Componentes `ui/ios`: semántica y fuente grande
- `IosSection`: la cabecera es un encabezado (`heading`). `IosListRow` sin acción fusiona título, subtítulo y valor en un solo elemento. El título pequeño de la barra de navegación se oculta a TalkBack (el grande ya es el encabezado y se leía dos veces).
- `IosBarButton` e `IosBarIconButton`: mínimo 48 × 48 dp (antes solo 48 de alto: "OK" medía menos de ancho).
- `IosAlert`: título como encabezado y cuerpo desplazable. `IosButton`, `ActionButton` e `IosMenuItem` dejan de forzar `maxLines = 1`: con fuente 2,0 el texto pasa a otra línea en vez de cortarse con puntos suspensivos.
- `IosSegmentedControl`: alto mínimo de 48 dp que crece si una etiqueta se parte; la pista dibujada sigue siendo de 32 dp. `IosSheetHeader`: con escala de fuente > 1,3 los botones toman el ancho que necesitan y el título se parte en lo que queda (el reparto fijo 1:2:1 cortaba "Cancelar").

### D-T17-3 · 2026-10-05 · Pestañas: todo gesto tiene una acción de accesibilidad
- Tocar una pestaña era un gesto de puntero sin acción de clic: se añade `onClick` ("Seleccionar"). Reordenar por arrastre era el único camino: se añaden las acciones "Mover antes" y "Mover después" (solo si hay hueco). Los botones "+" y "⋯" declaran `Role.Button`.

### D-T17-4 · 2026-10-05 · Terminal y paneles
- **Vista del terminal:** `contentDescription` "Terminal" y acción de clic "Mostrar el teclado". **Decisión: no se expone el texto de la pantalla** (SPEC: "la terminal en sí expone texto" queda como el contenido que el usuario puede copiar con la selección): leer cada redibujado en voz alta es ruido, un nodo con cientos de caracteres que cambia con cada byte es caro, y el contenido de la terminal no debe llegar a ningún registro. Si hace falta lectura, el diseño correcto es una región en vivo con las líneas nuevas, y es trabajo aparte.
- El panel sin foco tiene la misma acción de clic. El separador de paneles, que solo se movía arrastrando, ofrece "Mover el separador hacia atrás/adelante" (pasos de 48 dp). El botón del menú de paneles declara rol y clic.
- Comprobado por búsqueda: ningún `Log.*` ni `println` toca el contenido del terminal.

### D-T17-5 · 2026-10-05 · Fila de teclas extra: la fuente se frena en 1,2
- La fila mide 48 dp por fila y cada tecla es una fracción del ancho: el rótulo no puede crecer sin límite. `ExtraKeyFit.MAX_FONT_SCALE` = 1,2: los rótulos dibujados siguen la fuente del sistema solo hasta ahí (el nombre hablado de cada tecla no cambia). `ExtraKeyFitTest` comprueba, con una estimación conservadora del ancho (0,65 em por letra), que todas las teclas por defecto caben en 360 dp con los tres estilos a escala 2,0.
- **Alternativa descartada:** dejar crecer las filas con la fuente. Rompe el cálculo del alto del pty (D de T12/T22b: fila × 48 dp) y comería el terminal.

### D-T17-6 · 2026-10-05 · Pantallas de Material (SSH, Apariencia): filas que se parten y encabezados
- Las filas de botones de SSH (cabecera, tarjetas de host y de clave) y de esquemas pasan a `FlowRow`: con fuente grande los botones bajan a otra línea en vez de salirse de la pantalla. Los títulos de pantalla y `SectionTitle` son encabezados. `LabeledSlider` anuncia su valor (`stateDescription`) además de la etiqueta. Cambios pequeños a propósito: T22c y T12b editan esas pantallas.

### D-T17-7 · 2026-10-05 · Rendimiento: guarda en el host y procedimiento en el dispositivo
- `FeedThroughputTest` (módulo `terminal-emulator`) alimenta el emulador con la salida de `seq 1 200000`, con 20 000 líneas de color que se parten y con Unicode ancho/combinante, en trozos de 4 KiB como el lector del pty. Comprueba el resultado (última línea, historial acotado) y un límite de **30 s**, dos órdenes de magnitud sobre lo que tarda un portátil (milisegundos): solo falla con una regresión de complejidad, no por una máquina lenta ni por el reloj. No mide dibujo ni el dispositivo.
- `docs/PERFORMANCE.md`: procedimiento reproducible para el arranque en frío hasta el prompt (el propio `PS1` escribe la hora del primer prompt; `am start -W` da el primer frame) y para `seq 1 200000` (`time`, `dumpsys gfxinfo`, interfaz viva durante la salida, paneles y fuente grande). Objetivo SPEC: < 1,5 s.

### D-T17-8 · 2026-10-05 · Lo que NO está validado (sin dispositivo)
1. TalkBack: orden de lectura y anuncios de las pestañas (acciones Seleccionar/Renombrar/Cerrar/Mover), del terminal, del separador de paneles, de los encabezados y de las filas fusionadas de Ajustes.
2. Fuente 2,0 en: barra de pestañas (48 dp fijos arriba: una pestaña con nombre largo se recorta a una línea), fila de teclas, hoja de formulario (`IosSheetHeader`), alertas, `NamePrompt` (no se tocó: es de T22c), control segmentado de Apariencia y pantallas SSH.
3. El rendimiento: ninguna cifra del dispositivo; los objetivos de la SPEC siguen sin comprobarse hasta ejecutar `docs/PERFORMANCE.md`.

## T24 — Fedora como distro

Petición del usuario (Fedora es su distro habitual). Hecho sin dispositivo: probado en host con muestras sintéticas y con las respuestas reales de Fedora (listados y `CHECKSUM`) copiadas a los tests. **Pendiente de la prueba real en el Pixel 8.**

### D-T24-1 · 2026-10-04 · Origen: la imagen de contenedor `Base` del release oficial, con su `CHECKSUM` firmado
- **Decisión:** se usa `https://dl.fedoraproject.org/pub/fedora/linux/releases/<versión>/Container/<arq>/images/Fedora-Container-Base-Generic-<versión>-<compose>.<arq>.oci.tar.xz` (63 MB en Fedora 44, aarch64) y el hash SHA-256 de `Fedora-Container-<versión>-<compose>-<arq>-CHECKSUM`, que Fedora firma con PGP y que trae también el tamaño.
- **Por qué esa imagen y no otra:** la capa trae `dnf5` (`usr/bin/dnf -> dnf5`, visto en su listado), que es lo que el usuario va a usar. La `Minimal` (49 MB) lleva una palabra más en el nombre y no se elige.
- **Alternativa descartada:** `fedora-cloud/docker-brew-fedora` (rama `44`, `aarch64/fedora-<fecha>.tar`). Es más sencilla —su "`.tar`" es en realidad un gzip (empieza por `1f8b`), el extractor actual la leería—, pero el repositorio **no publica ningún SHA-256** (solo el SHA-1 de blob de git), así que no habría una verificación a la altura de las otras distros. Además su fecha cambia con cada actualización.
- **Impacto:** el hash lo verifica `Sha256Verifier` como en las demás (T06), sobre el `.oci.tar.xz` entero.

### D-T24-2 · 2026-10-04 · Qué versión: la más reciente que tenga imagen, sin URLs fijas
- **Decisión:** el catálogo lee el listado de `releases/`, ordena los números de mayor a menor y prueba los tres primeros hasta dar con uno con `Container/<arq>/images/` y su `CHECKSUM`. Hoy (2026-10-04) 45 aparece listado pero no tiene directorio de contenedores (404), así que se elige **Fedora 44**.
- **Motivo:** como D-T06-1: ninguna URL ni hash en el código, y una versión nueva se recoge sin actualizar la app. Probar solo la última fallaría justo cuando se abre una rama nueva.
- **Riesgo aceptado:** la **firma PGP del `CHECKSUM` no se comprueba**: se lee el texto claro, que llega por HTTPS desde el servidor del proyecto. Es el mismo riesgo que D-T06-2. Mejora posible: llevar las claves de publicación de Fedora y verificar la firma (haría falta una biblioteca OpenPGP, hoy no justificada).
- **Impacto:** las imágenes del release están congeladas en su fecha (la de Fedora 44 es del 2026-04-22); la distro se actualiza después con `dnf upgrade`.

### D-T24-3 · 2026-10-04 · ABIs: arm64 y x86_64; Fedora no publica 32 bits de ARM
- **Comprobado:** en Fedora 44 el directorio `armhfp`/`armv7hl` está vacío. El catálogo devuelve `CatalogUnavailable("Fedora has no image for armv7")` **sin tocar la red**.
- **Pendiente:** el diálogo de instalación sigue listando Fedora en un dispositivo armv7 y falla al intentarlo con ese mensaje. Ocultar la opción por arquitectura queda para la pantalla de Distros nueva (T22c).

### D-T24-4 · 2026-10-04 · Dos niveles de archivo: xz, tar OCI y capa gzip; la capa se halla por su contenido
- **Hecho comprobado leyendo la imagen real en flujo:** el `.oci.tar.xz` contiene, en este orden, `blobs/`, `blobs/sha256/`, un blob JSON (config), **la capa de 66,8 MB** (gzip), otro blob JSON (manifiesto), `index.json` y `oci-layout`. **El manifiesto llega después de la capa**, así que no se puede localizar la capa leyéndolo.
- **Decisión:** `OciArchive` reconoce una imagen OCI por su primera entrada y toma como capa el blob de `blobs/sha256/<64 hex>` cuyo contenido es un tar comprimido o plano (por la cabecera: gzip, xz, zstd, bzip2 o `ustar`), no JSON. La extrae en flujo con las mismas reglas de seguridad de T07 (`TarPass` y `SafeTreeWriter` no cambian).
- **Integridad de la capa:** los blobs OCI se llaman como su propio SHA-256. Se calcula el de los bytes de la capa a medida que se leen (incluidos los que el descompresor no pide) y se compara con su nombre al terminar; si no coincide, `Corrupt("the OCI layer does not match its digest")` y el instalador descarta el directorio temporal (D-T07-1). Sirve de segunda comprobación sobre el hash del archivo entero.
- **Una sola capa**, como Debian (D-T06-3): tras la capa se recorre el resto del archivo y otra capa daría `UnsupportedFormat("an OCI image with several layers")` en lugar de instalar medio sistema.
- **Detalle técnico:** el flujo de la capa no admite `mark`/`reset` y rehace `skip` con `read`, porque cualquiera de las dos cosas esquivaría el cálculo del hash.

### D-T24-5 · 2026-10-04 · Biblioteca xz: `org.tukaani:xz` 1.12 (0BSD), con límite de memoria
- **Licencia:** 0BSD, comprobada en el POM de Maven Central y en `COPYING` del repositorio oficial (`tukaani-project/xz-java`); compatible con GPL-3.0-or-later. 0BSD no exige conservar ningún aviso; se acredita igualmente en `THIRD_PARTY_NOTICES.md`. `licensee` permite ahora `0BSD`.
- **Aviso en el APK:** el jar de xz-java no trae `NOTICE` ni `LICENSE` (solo `META-INF/MANIFEST.MF` y el módulo), y 0BSD no obliga a conservar ningún aviso, así que, con el criterio de D-T23-2 (se empaquetan los textos de las dependencias que publican el suyo), no se añade nada a `res/raw` ni a `assets/licenses`. Queda acreditada en `THIRD_PARTY_NOTICES.md`.
- **Verificación de dependencias:** el `jar` y el `pom` (Maven Central no publica `.module`) se descargaron y sus SHA-256 coinciden con los `.sha256` que publica Maven Central; se insertaron en `verification-metadata.xml` sin reformatear el fichero.
- **Memoria:** `XZInputStream` se abre con un límite de **64 MiB** (el diccionario de un `xz -9`; el de Fedora pide del orden de 1 MiB). xz-java reserva el diccionario en el heap de Java, y un flujo que pida más se rechaza (`TooLarge`) en vez de arriesgar un `OutOfMemoryError`. Los demás errores del descompresor se clasifican como `Corrupt`.
- **Bombas:** el xz comprime mucho más que gzip, así que la defensa no es el tamaño comprimido: la cuentan los límites que ya había sobre lo que se **escribe** (16 GiB y 2 millones de entradas) y se añade un tope de 8 GiB para el tamaño declarado de una capa.

### D-T24-6 · 2026-10-04 · El formato se decide siempre por los primeros bytes, nunca por la extensión
- `ArchiveFormat.open` ya miraba la cabecera mágica; ahora además lee xz. El instalador guarda el archivo descargado con un nombre fijo (`archive`) y los tests lo comprueban con un gzip llamado `.tar.xz` y un xz llamado `.tar.gz`. zstd y bzip2 siguen sin soporte y se nombran en el error.

### D-T24-7 · 2026-10-04 · Espacio: la regla de T07 ya cubre Fedora
- `InstallSpace` exige 5 veces el tamaño del archivo: unos 330 MB para los 66 MB de Fedora (el archivo más lo descomprimido, que se estima en unos 200 MB). **El tamaño descomprimido real no se ha medido**: se medirá en el dispositivo.

### D-T24-8 · 2026-10-04 · Lo que Fedora necesita del lanzamiento de proot: nada nuevo, con tres cosas por vigilar
- **`/etc/resolv.conf`:** la capa no lo trae y T08b lo resuelve con un bind de proot, no escribiendo en el rootfs: sirve igual.
- **`PATH`:** el genérico (`/usr/local/sbin:…:/bin`) vale; `/bin` y `/sbin` son enlaces **relativos** a `usr/…` y proot los resuelve dentro de la distro. No hay ninguna comprobación de `bin/sh` en el lado del anfitrión (el repositorio de ficheros no sigue enlaces y la daría por inexistente).
- **Enlaces relativos** (`etc/os-release -> ../usr/lib/os-release`, `usr/sbin -> bin`, `usr/local/sbin -> bin`): el extractor los guarda tal cual, y la regla de no escribir a través de un enlace sigue vigente.
- **Por vigilar en el dispositivo:** `dnf5` bajo proot (seccomp, `ptrace`, `/proc` parcial, la base de datos de `rpm`) y el tiempo de la primera descarga.

### D-T24-9 · 2026-10-04 · Qué se probó y qué no
- **Tests de host (sin red real):** xz válido, truncado, dañado, con solo su magia, bomba y exceso de memoria (editando el diccionario de la cabecera del bloque y recalculando su CRC); imagen OCI de Fedora (capa única, envoltorio gzip o plano, nombres con `./`, capa tar plano, digest que no coincide, sin capa, varias capas, capa hostil con `..` y con escritura a través de enlace, bomba dentro de la capa); el flujo de la capa (lectura, `skip`, `mark`/`reset`, cierre); parsers y catálogo de Fedora con las respuestas reales (la nueva rama sin imagen, armv7, tres versiones sin imagen, `CHECKSUM` sin entrada); y el instalador registra `DistroType.FEDORA`. Las fechas de los tars son fijas y ningún test depende del reloj ni de los tamaños comprimidos.
- **No cubierto por tests:** el tope de 8 GiB de una capa (hace falta una cabecera de tar con un tamaño absurdo escrita a mano).
- **Sin validar en hardware, y criterios de la prueba real en el Pixel 8:**
  1. Instalar Fedora desde la app: descarga de ~63 MB con progreso, comprobación de hash, extracción sin error y la distro "lista"; anotar el tiempo y el tamaño descomprimido.
  2. Abrir una pestaña: `cat /etc/fedora-release` (debe decir Fedora Linux 44) y `echo $0`.
  3. `dnf --version` y `dnf install -y nano` (red, DNS del dispositivo, escritura en `/var/lib` y `/var/cache`); `nano --version`.
  4. Si `dnf` falla: probar el modo de compatibilidad (proot sin seccomp) y apuntar el error exacto.

### Tercera ronda (Pixel 8, 2026-10-04, APK de `master` con T16, T22d y T24)

**Fedora (T24), verificado de extremo a extremo en el dispositivo.**
- **Instalación desde la app:** "Fedora" aparece en la hoja "Instalar una distro", junto a Debian, Ubuntu y Alpine. Descarga con barra de progreso y botón de cancelar, verifica y extrae el tar.xz: Fedora 44 queda "lista", con **195 MB** descomprimidos.
- **Pestaña:** el menú del "+" ofrece "Fedora" y abre un bash propio (`[root@localhost ~]#`) bajo proot.
- **`dnf` bajo proot (el mayor riesgo de T24): funciona.** `dnf --version` da dnf5 5.4.1 con sus plugins; `dnf install -y nano` carga los repositorios de Fedora 44 (65,4 MiB y 11,6 MiB de metadatos), descarga 724 KiB y la transacción de `rpm` termina en "Complete!". Después `nano --version` da 8.7.1 y `rpm -q nano bash` responde con `nano-8.7.1-2.fc44.aarch64` y `bash-5.3.9-3.fc44.aarch64`, así que la base de datos de `rpm` es válida.
- `clear` no existe en la imagen mínima de Fedora: no es un fallo de la app.

**Ajustes (T16) y Distros (T22c).**
- El ⚙ se ve siempre junto al "+" y abre la pantalla de Ajustes (título grande, listas agrupadas con chevrons, "Listo"), con las nueve secciones. La página de Red muestra los DNS de respaldo con su explicación; la de Distribuciones enlaza "Gestionar distribuciones" y el modo de compatibilidad.
- La pantalla de Distribuciones rehecha (lista agrupada, "+" para instalar, hoja modal con asa) ya no choca el título con el botón.
- **No probado todavía:** las páginas Terminal, Teclado, Sesiones, Almacenamiento, Copias de seguridad y Acerca de; el diálogo de segundo plano, que sigue con aspecto de Material.

**Teclas especiales (T22d):** las tres variantes funcionan y se eligen en Apariencia con vista previa en vivo. Planas (por defecto): símbolos con separadores finos, sin cápsulas; Cápsulas: cápsulas tenues con Ctrl armado en acento; Clásicas: teclas con filo inferior en grises derivados del esquema.

**Fallo hallado y corregido: el botón/gesto de "atrás" no hacía nada en ninguna pantalla** (ni en Ajustes, ni en Apariencia, ni desde el terminal). Causa: `MainActivity` no declaraba `android:enableOnBackInvokedCallback` y, con `targetSdk` 28 en Android 17, los `BackHandler` de Compose no recibían el evento. Una línea en el manifiesto lo arregla (PR #44) y está verificado en el dispositivo: atrás vuelve de una página de Ajustes a la raíz, cierra Ajustes y vuelve de Apariencia. Un test del manifiesto lo protege. Observación de la versión anterior: pulsar "atrás" varias veces dentro del terminal acababa mostrando "Display all 395 possibilities?" de bash, lo que sugiere que la tecla llegaba al shell como un tabulador; **hay que comprobar con la build nueva que "atrás" en el terminal ya no escribe nada en el shell.**

**Retirado: el supuesto fallo de "Negro OLED activado y ningún esquema seleccionado".** No era de la app: el esquema seleccionado era "OLED Black" (el sexto de la lista, fuera de pantalla en mi captura) y Negro OLED estaba activado, coherente entre sí. Casi seguro lo cambié yo con toques de prueba mientras desplazaba la pantalla de Apariencia. Restauré Dracula con OLED apagado.


**Comprobado en el Pixel 8 con la build de `master` (2026-10-04): "atrás" en el terminal no escribe nada en el shell.** Con el teclado visible y `echo prueba` sin enviar, una pulsación de "atrás" oculta el teclado (y con él la fila de teclas especiales) y la línea queda intacta, sin "Display all … possibilities?". Una pulsación más, sin teclado, deja la app en segundo plano, como se espera. La sospecha de la versión anterior queda cerrada. El diálogo "Mantener las sesiones activas" sigue con aspecto de Material (pendiente).

### Cuarta ronda (2026-10-05, build de `master` en el Pixel 8 y en la tablet Huawei MRO-W09)

**Corrección del usuario:** el requisito original sí era la tablet (el redimensionado de Termux); mi frase de que no lo era fue un error de lectura.

**Tablet Huawei MRO-W09 (Android 12, 2800×1840, arm64): primera validación real de T04/T10.**
- Con ninguna distro instalada, la app abre una pestaña "Shell 1" con el shell del sistema y la barra lateral de pestañas (pantalla ancha) con "+" y ⚙ abajo.
- `stty size` coincide con lo visible: apaisado 41 filas × 123 columnas (unas 125 × 42 con márgenes); vertical (rotación 0) 64 × 72 sin teclado y 58 × 72 con la fila de teclas extra, que aparece y desaparece con el teclado (RF-08 también aquí).
- Multiventana (ventana flotante forzada con `--windowingMode 5` y `am task resize`): ventana pequeña 23 × 45 y ventana grande 49 × 60; cada cambio de tamaño actualiza el PTY y coincide con lo visible.
- No probado: pantalla dividida (`--windowingMode 3/4` falla en este EMUI con una excepción del sistema), paneles divididos, distro instalada, teclado físico.
- La captura de esta tablet no incluye el teclado en pantalla (`mInputShown=true` confirma que está abierto).
- Ajustes del sistema que toqué para probar (rotación fija, `enable_freeform_support`, `force_resizable_activities`) restaurados al terminar.

**Pixel 8.**
- "Atrás" en el terminal oculta el teclado y no escribe nada en el shell (verificado, sección anterior).
- Páginas de Ajustes ahora probadas: Terminal (historial, 10.000 líneas marcado), Teclado, Sesiones (batería sin restricciones: "Permitido"; notificaciones: "No permitido"), Almacenamiento, Copias de seguridad, Acerca de (0.1.0-rc.1, GPL-3.0-or-later) y Créditos y licencias.
- **Fallo 1:** el teclado en pantalla sigue abierto sobre Ajustes y tapa contenido al venir del terminal.
- **Fallo 2:** "Créditos y licencias" muestra el texto con los saltos de línea duros del `.md` como filas separadas y la tabla como filas sueltas, en inglés. Legible pero feo; no se pierde ningún crédito.
- Ambos fallos se pasan a un agente (rama `fix/ime-and-credits`).

## Correcciones tras pruebas en el Pixel 8

### D-FIX-1 · 2026-10-05 · El teclado se oculta mientras otra pantalla cubre el terminal

- **Hallazgo:** el terminal sigue compuesto bajo Ajustes, Apariencia, Distros y SSH (se dibujan encima), así que su `TerminalInputView` conservaba el foco y el teclado quedaba abierto sobre esas pantallas.
- **Decisión:** `MainActivity` publica `LocalTerminalCovered` (true si alguna de esas pantallas está abierta). `TerminalOverlays` llama a `TerminalInputView.hideKeyboard()` (quita el foco y oculta el IME) al pasar a cubierto, y el teclado automático de la primera composición no se abre si ya empieza cubierto (p. ej. al girar con Ajustes abierto). Al volver, el teclado no se fuerza: lo abre un toque en el terminal.
- **Sin test unitario:** es pegamento de vista e IME sin lógica pura; hay que comprobarlo en el dispositivo.

- **Atrás en la tableta Huawei (Android 12):** el botón/gesto "atrás" no hacía nada en las subpáginas de Ajustes. Causa probable: la vista de entrada, aún con el foco bajo Ajustes, consumía **todas** las teclas (también `KEYCODE_BACK`) en `onKeyDown`, así que el `BackHandler` de Compose no se ejecutaba (y casa con el antiguo "atrás llega a bash como Tab"). Dos capas: `SystemKeys.isSystemKey` (Atrás, Inicio, Apps recientes, Menú, encendido y volumen) hace que `onKeyDown` y `sendKeyEvent` de la vista no consuman ni codifiquen esas teclas (con test), y la vista suelta el foco (`clearFocus`) cuando otra pantalla cubre el terminal (D-FIX-1). **Sin verificar en la tableta.**

### D-FIX-2 · 2026-10-05 · "Créditos y licencias": párrafos unidos y una fila por componente

- **Hallazgo:** `Notices.parse` trataba cada línea del `.md` (cortada a mano) como una fila y las tablas salían como "Componente · Licencia · Notas".
- **Decisión:** las líneas consecutivas se unen en un párrafo (los saltos en blanco y las viñetas separan); cada fila de tabla es un `NoticeItem` con el componente como título y el resto de celdas (licencia, notas) como texto secundario. Se omiten solo la fila de cabecera de las tablas (los nombres de columna) y el párrafo "This file must be updated…", que es una instrucción a los mantenedores. Ningún crédito, aviso ni texto de licencia se pierde (los textos de licencia van aparte, en `LicenseTexts`).
- **Pendiente:** el contenido de `THIRD_PARTY_NOTICES.md` sigue en inglés (texto legal fuente); la interfaz que lo rodea sí está localizada.

## T22c (resto) — Diálogos de sesiones, SSH y Apariencia en estilo iOS

Hecha en un clon aparte, sin tocar ningún dispositivo. Todo probado solo en host.

### D-T22c-1 · 2026-10-05 · «Mantener las sesiones activas»: `IosAlert`, y «Permitir» ya pedía el diálogo del sistema
- **Decisión:** los dos avisos de `SessionPrompts` (notificaciones y batería) son un `IosAlert` de dos botones: «Ahora no» (cancelar, en negrita) y «Permitir». El comportamiento no cambia.
- **Ruta de «Permitir» (comprobada en el código):** el aviso de batería llama a `requestBatteryExemption`, que prueba en orden `BatteryExemption.intentsFor` (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` con `package:<app>` y, solo si falta o se rechaza, la lista general). No abre la lista salvo como respaldo, así que no había nada que corregir; solo se abre la lista general desde Ajustes cuando la app ya está exenta (para poder revocarla). Eso sigue como en D-T08c-1.
- **Tema:** estos avisos y las pantallas de SSH y Apariencia estaban fuera de `IosTheme` en `MainActivity`; ahora están dentro (sin ello `IosAlert` usaba la paleta clara por defecto).
- **Sin validar en dispositivo:** que el diálogo del sistema aparezca tras «Permitir» en Android 17 con `targetSdk` 28 (ya pendiente en D-T08c-1).

### D-T22c-2 · 2026-10-05 · SSH (T14): lista de anfitriones y claves como listas agrupadas, formularios en hojas
- **Decisión:** `SshScreen` y `SshKeysScreen` son `IosLargeTitleScreen`. Un anfitrión o una clave se pulsa y abre una `IosActionSheet` (conectar/editar/borrar; copiar pública/guardar privada/borrar). Añadir un anfitrión es el «+» de la barra; añadir una clave abre una hoja de acciones (generar/importar). Los formularios (anfitrión, generar, importar) son `IosBottomSheet` con `IosSheetHeader` y `IosTextField`; la clave y la distro del anfitrión son listas con marca (`IosAccessory.Check`). Borrar y la advertencia de guardar la clave privada son `IosAlert` (la advertencia no va en rojo). Mensajes y «Trabajando…» son filas, como en Distros.
- **Componentes nuevos en `ui/ios`:** `IosTextArea` (texto de varias líneas, para pegar una clave), el hueco `detail` de `IosListRow` y `IosAccessory.Swatch` (recuadro de color con su código).
- **Cadenas:** `ssh_key_add` (EN/ES); se quitan `ssh_connect_to` (no se usa ya).

### D-T22c-3 · 2026-10-05 · Apariencia (T12c): pantalla con título grande, controles iOS y `IosSlider`
- **Decisión:** la pantalla, el editor de esquemas y el selector de color dejan Material. Tema, cursor y estilo de barras son `IosSegmentedControl`; los interruptores, filas con `IosAccessory.Toggle`; fuentes y esquemas, listas con marca y una `IosActionSheet` por elemento (usar, editar, duplicar, exportar, borrar; borrar un esquema pide confirmación con `IosAlert`). Las fuentes propias se quitan desde su hoja. El editor y el selector son `IosBottomSheet` grandes (Cancelar/Guardar y Cancelar/OK).
- **`IosSlider`:** no existía y Material no era una opción. Es de `foundation` (pista, relleno y pulgar con gestos de toque y arrastre, 48 dp de alto, `setProgress` para TalkBack). La aritmética (posición del dedo, valor, pasos de TalkBack) es pura y está probada (`SliderMath`, `SliderMathTest`). Se guarda al soltar, como antes.
- **Cadenas:** nuevas `appearance_scheme_use` y `appearance_font_use`; se quitan `appearance_scheme_menu`, `appearance_font_delete` y `appearance_color_row` (ya sin uso; Lint falla con recursos sin usar). TalkBack lee ahora título + código hex de cada fila de color por la fila agrupada.
- **No cambia:** la vista previa del terminal sigue con `Text` de Material, porque es una muestra de texto con fuente propia, no un diálogo; `ChromePalette` sigue leyendo `MaterialTheme` (D-T12c). El texto de «Colores dinámicos» habla de menús y diálogos: ahora afecta sobre todo a las barras y teclas en modo «del sistema» (a revisar con el usuario).

### D-T22c-4 · 2026-10-05 · El botón «⋯» de paneles va en una franja propia, no encima del texto
- **Qué se vio:** con la pestaña dividida, el botón (en la esquina superior derecha del panel enfocado) tapaba el final de la primera línea.
- **Decisión:** en una pestaña dividida **cada** panel reserva una franja de 40 dp arriba (`PaneHeaderHeight`), enfocado o no, para que mover el foco no cambie el tamaño de ningún pty. El PTY de cada panel se dimensiona con el rectángulo menos esa franja (`paneLayouts(..., headerPx)` y `belowHeader`, puros y probados), el texto se dibuja debajo, y el botón está en la franja. Su zona táctil sigue siendo de 48 dp: sobresale 8 dp por debajo de la franja, solo en la esquina derecha, donde no hay texto visible que se tape. `canSplit` y el mínimo al arrastrar un divisor cuentan la franja (cada mitad necesita la franja más 4 filas). Una pestaña de un solo panel no cambia (sin franja, sin botón). El botón «Copiar» de la selección baja por debajo de la franja.
- **Alternativa descartada:** encoger solo la primera fila del panel enfocado: cambia el tamaño del pty al mover el foco y provoca un `SIGWINCH` en cada toque.
- **Coste:** 40 dp menos de alto por panel en una división. Sin validar en dispositivo: que 40 dp se vean bien, que la zona de 48 dp no moleste y la tablet.

### D-T22c-5 · 2026-10-05 · Los botones de Material que quedaban en el terminal
- «Copiar» (selección) y «Sesión terminada, toca para reiniciar» eran `TextButton`/`Button` de Material sobre el terminal; ahora son `IosButton` (tintado y relleno). No quedan `AlertDialog`, `ModalBottomSheet` ni `DropdownMenu` fuera de `ui/ios`, ni imports sin usar de ellos en `PaneViews`.

### D-T22c-6 · 2026-10-05 · Qué NO está validado (sin dispositivo)
- Aspecto real de las pantallas de SSH y Apariencia y de las hojas de formulario (teclado, desplazamiento dentro de la hoja con el deslizador), el arrastre del `IosSlider` dentro de una hoja que también se desplaza en vertical, TalkBack en filas de color y deslizadores, y la franja de los paneles en móvil y tablet (D-T22c-4).

## Usuario no root sin `su` (fix/non-root-user)

Evidencia (tablet Huawei): Fedora 44 mínima no trae `su` y el usuario elegido no existía en `/etc/passwd`; `su -l <usuario>` terminaba con código 127. Sustituye a D-T08b-5. Hecho sin dispositivo.

### D-USER-1 · 2026-10-05 · Identidad con `-i uid:gid` de proot, sin `su`
- **Decisión:** un usuario que no es root ejecuta directamente su shell de login (`<shell> -l`) con `-i uid:gid` en lugar de `-0`, y `env -i` con `HOME`, `USER`, `LOGNAME`, `SHELL`, `TERM`, `LANG`, `PATH`. Los ids, el home y el shell salen de `/etc/passwd` del rootfs (`GuestAccounts.find`, función pura; se salta comentarios y líneas rotas; con nombres duplicados vale la primera). Un shell `nologin`/`false`/vacío/relativo se cambia por `/bin/sh`.
- **Comprobado en el código de proot (`third_party/proot`, fork de Termux):** `-i, --change-id=uid:gid` hace que el usuario y grupo actuales aparezcan como uid:gid y que los ficheros del usuario real aparezcan con ese dueño; `-0` es `-i 0:0`. En el rootfs todos los ficheros son del uid de la app, así que dentro aparecen del usuario: no hace falta `chown`.
- **Root y comandos:** root no cambia (`-0`, `/bin/sh -l`). Un comando (SSH) sigue corriendo con `-0`, ignorando el usuario.
- **Sin validar en dispositivo:** que `-i` funcione con seccomp en Android 17 y que el prompt muestre el usuario (Pixel 8).

### D-USER-2 · 2026-10-05 · Si el usuario no existe, se crea (obligatorio por decisión del usuario)
- **Decisión:** `GuestAccountResolver` crea el usuario si falta: uid libre desde 1000 (hasta 59999), grupo con su nombre (se reutiliza si existe, con su gid) y gid igual al uid si está libre, línea `nombre:x:uid:gid:nombre:/home/nombre:shell` en `/etc/passwd` (sin hash: no hay contraseña ni `/etc/shadow`, porque no se usa `su`), y `/home/<nombre>` por `FileSystemRepository`. Shell: `/bin/bash` si `/etc/shells` lo lista, si no `/bin/sh`. Nunca se sobrescribe una entrada existente ni se crea `root`; funciona con ficheros sin salto de línea final.
- **Transaccional:** `FileSystemRepository` gana `readText` y `writeText` (escribe junto al destino y renombra con `ATOMIC_MOVE`; rechaza enlaces simbólicos finales y ficheros de más de 1 MiB). Primero se escribe `/etc/group` y al final `/etc/passwd`: un grupo sin usuario es inofensivo, así que un fallo entre medias no cambia el login de nadie.
- **Propiedad y modo del home:** el directorio lo crea la app con el modo por defecto del repositorio; por `-i` aparece del usuario. No se fuerza 0700 (la abstracción no expone modos); queda pendiente si se quiere.
- **Fallo:** si `/etc/passwd` no se puede leer, no se puede crear o hay un fallo al escribir, no se lanza una sesión condenada: `LaunchProblem.UserUnavailable` con aviso localizado (EN/ES) que sugiere usar root. Un home fuera de `/home` no se crea; la sesión empieza en `/`.

### D-USER-3 · 2026-10-05 · Códigos 127 y 126
- La tarjeta «Sesión terminada» muestra un texto distinto para 127 («no se encontró un programa») y 126 («no se pudo ejecutar»), vía `ExitHint` (puro, probado). Otros códigos como antes.

### Quinta ronda (Pixel 8, 2026-10-05, build de `master` en `ec4bb2c`: T17, T12b, T22c, usuario sin `su`)

**Rendimiento (SPEC §6; build de depuración, Fedora 44 bajo proot, `docs/PERFORMANCE.md`).**
- **Arranque en frío hasta el prompt:** seis arranques con `am start -W` y un `PROMPT_COMMAND` que escribe la hora (reloj del dispositivo): 1.471, 1.653, 1.478, 1.585, 1.650 y 1.628 ms; mediana de las cinco últimas **1.628 ms** (rango 1.478–1.653). Primer fotograma (`TotalTime`): 467–508 ms. **El objetivo de 1,5 s se supera por unos 130 ms en una build de depuración**; falta medir una build de release.
- **`seq 1 200000`:** `time` da `real 1,073 s` (`sys 0,352 s`); `gfxinfo` en tres pasadas: 98/126/127 fotogramas, 0–2,04 % con jank, p50 5 ms, p90 5–6 ms, p99 9–150 ms (el 150 ms sale en la primera pasada). La interfaz sigue fluida.
- La primera tanda de medidas (mismo día) no vale: el Pixel estaba bloqueado con la pantalla apagada y el shell no arrancó; se repitió con la pantalla encendida. Una medida con la distro Alpine apuntaba en realidad a Fedora (la predeterminada); el gancho en `.profile` de Fedora funciona porque `/bin/sh -l` es bash en modo POSIX y no lee `.bash_profile`.

**Usuario no root (D-USER-1..3).** En Alpine, instalar una distro con usuario por defecto `ops` (que no existía) crea el usuario: `id` da `uid=1000(ops) gid=1000(ops) groups=3003,9997,20399,50399` y `pwd` es `/home/ops`. Los grupos 3003, 9997 y los de aplicación vienen de Android (proot los hereda); no son un fallo, pero un usuario "no root" los conserva. Pendiente de repetir en Fedora (la causa del código 127 era la falta de `su`, que ya no se usa).

**Fallos hallados.**
1. **Cierre de la app (T12b):** "Perfiles" > "+" y Ajustes > Teclado > Atajos > una fila cierran la app con `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints` (scroll vertical anidado en una hoja). Las listas de Perfiles y de Layouts se abren bien. En manos de un agente (`fix/profile-form-crash`).
2. **Distros ya instaladas:** la hoja de acciones solo ofrece Renombrar, Duplicar y Eliminar. No hay forma de cambiar el usuario por defecto ni de elegir la distro predeterminada (SPEC §5 y RF-13); el usuario solo se fija al instalar.
3. **Barra de pestañas:** con tres pestañas el botón "⋯" de la pestaña activa queda recortado por el borde del "+".
4. **Pendiente de probar:** guardar y abrir un layout (la hoja de "Guardar los paneles"), el formulario de perfil y la difusión; dependen del arreglo 1.

**Bien en el dispositivo:** el diálogo "Mantener las sesiones activas" ya es una alerta iOS; "+" abre una pestaña de la distro predeterminada y la pulsación larga muestra el menú (distros, dividir, perfiles, layouts, ajustes, SSH, gestionar distros, apariencia); Ajustes tiene "Perfiles" y "Layouts".

## Fallo al abrir la hoja de perfil (fix/profile-form-crash)

Evidencia (Pixel 8, Android 17): Ajustes > Perfiles > «+» cerraba la app con `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`. Hecho sin dispositivo.

### D-FIX-1 · 2026-10-05 · El contenido de `IosBottomSheet` se desplaza una sola vez, y lo vigila un test
- **Causa:** `IosBottomSheet` ya envuelve su contenido en un `Column` con `verticalScroll` (altura acotada por el detent). `ProfileEditorSheet` y `ShortcutSheet` (T12b) añadían otro `verticalScroll` dentro, que recibe altura infinita y lanza la excepción.
- **Decisión:** se quita el `verticalScroll` interior de ambas hojas; el desplazamiento y el límite de altura son responsabilidad de la hoja. Revisadas todas las demás hojas (SSH, Apariencia: editor de esquemas y selector de color, distros, copias, DNS, guardar disposición): no anidan ningún scroll ni `LazyColumn`.
- **Protección:** `SheetScrollNestingTest` (sin dependencias nuevas) lee las fuentes y falla si un fichero que abre un `IosBottomSheet` usa `verticalScroll`, `LazyColumn` o cuadrículas verticales perezosas, y comprueba que la hoja desplaza una sola vez. Regla: contenido de hoja = columna simple; si hace falta una lista larga, hacerla con `Column` o dar a la hoja una altura acotada explícita.
- **Sin validar en dispositivo:** que «+» y editar perfil, y editar un atajo, abran la hoja y se desplacen con el teclado abierto (Pixel 8).

## Distros: usuario y predeterminada desde la lista (feat/distro-user-default)

Evidencia (Pixel 8): la hoja de acciones de una distro instalada no permitía cambiar su usuario por defecto ni elegir otra distro predeterminada. Hecho sin dispositivo.

### D-DISTRO-1 · 2026-10-05 · Cambiar el usuario de una distro
- **Decisión:** nueva acción «Cambiar usuario…» (solo distros `READY`) que abre una hoja con un campo de texto (`UserSheet`, mismo patrón que `NameSheet`, sin desplazamiento propio). `DistroManager.setDefaultUser` recorta el nombre y lo valida con `GuestUser.isValid` (root permitido) antes de llamar al repositorio, que ya tenía `setDefaultUser`; el `Validation.user` del repositorio es más laxo y se mantiene como segunda red. Un nombre inválido da `InvalidValue("user")` y el botón OK de la hoja queda desactivado mientras no sea válido.
- **Usuario inexistente:** la hoja avisa de que se crea al empezar la siguiente sesión (D-USER-2) y de que root evita crear nada. No se crea nada al guardar.

### D-DISTRO-2 · 2026-10-05 · Distro predeterminada
- **Decisión:** la acción ya existía en el código (`setDefault`, solo si es `READY` y no es ya la predeterminada; la repo la hace exclusiva en una transacción). Se renombra a «Establecer como predeterminada» / «Set as default». En el Pixel la fila mostraba «Predeterminada» y por eso la acción estaba oculta; con una sola distro no hay otra a la que cambiar.
- **Backup:** sin cambios de formato. El manifiesto ya guarda `defaultUser` e `isDefault` por distro y la configuración `DistroRefDto(name, isDefault)`; la restauración los aplica como antes.

### D-DISTRO-3 · 2026-10-05 · La pestaña activa siempre se ve entera
- **Decisión:** el «⋯» solo existe en la pestaña activa, que crece al activarse y podía quedar bajo el «+» al final de la barra. Cada pestaña lleva un `BringIntoViewRequester` y, cuando pasa a activa (o cambia el número de pestañas), espera un fotograma (ya con el nuevo tamaño) y se desplaza a la vista. Sin cálculo propio, así que no hay lógica pura que probar.
- **Por validar en dispositivo:** con 3 o más pestañas en móvil, el «⋯» de la activa queda completo y tocable.

### Sexta ronda (Pixel 8, 2026-10-05, build de `master` en `e2f115a`: arreglo del cierre de las hojas de T12b)

**Verificado en el dispositivo.**
- **El cierre de T12b está arreglado (#58):** "Perfiles" > "+", editar un perfil y la hoja de un atajo (Ajustes > Teclado > Atajos) abren sin cerrar la app y se guardan.
- **Perfil con usuario inexistente en Fedora:** perfil "dev" (distro predeterminada = Fedora, usuario `ops`, comando `echo hola`) → "Abrir en una pestaña nueva": el prompt es `[ops@localhost ~]$` y el comando se ejecuta. La causa del 127 de la tablet (falta de `su`, usuario inexistente) queda resuelta también en Fedora.
- **División y difusión:** "Dividir a la derecha" funciona; el botón `⋯` está ahora en una franja sobre el panel y no pisa el texto. "Escribir en todos los paneles" muestra el aviso rojo "Lo que escribes llega a 2 paneles", con marco rojo en ambos paneles, y el texto llega a los dos shells (uno como `ops`, otro como `root`).
- **Layouts:** guardar los paneles ("dos · 2 paneles"), listarlo y abrirlo reproduce los dos paneles con sus usuarios.

**Fallos de uso hallados (no cierran la app).**
1. **Ajustes se queda abierto tras abrir un perfil o un layout:** al elegir "Abrir en una pestaña nueva" o "Abrir" el usuario sigue en la raíz de Ajustes y tiene que pulsar "Listo" para ver la pestaña.
2. **El nombre de la pestaña ignora el del perfil:** la pestaña abierta desde el perfil "dev" se llama "Fedora 2".
3. **Los campos de texto de los formularios solo toman el foco al tocar su parte derecha:** tocar la etiqueta ("Nombre", "Usuario") no hace nada. Además, al escribir en el primero, el texto de ayuda en rojo desaparece y el formulario se desplaza, así que un segundo toque posterior cae en otro campo.
4. **El aviso rojo de difusión tapa el comienzo de la primera línea del panel.**
5. **El comando inicial se ve dos veces:** se escribe 800 ms tras arrancar el shell, antes de que dibuje el prompt, y el eco del terminal lo repite (una vez sin prompt y otra con él). Es solo estético.
6. El panel nuevo de una división no lleva el perfil del panel original (usa la distro predeterminada).

**Pendiente de verificar con la build siguiente:** "Cambiar usuario…" (#59) y el recorte del `⋯` con tres pestañas, que ya están en `master` pero no en la build probada.


---

## Correcciones de usabilidad de T12b (sexta ronda, rama `fix/t12b-usability`)

Seis fallos de uso hallados en el Pixel 8 (ninguno era un cierre). Numeración continuada desde D-FIX-2.

### D-FIX-3 · 2026-10-05 · Abrir un perfil o un layout cierra Ajustes entero
- **Decisión:** `ProfilesScreen` y `LayoutsScreen` ganan `onOpened` (por defecto igual a `onClose`). `MainActivity` lo conecta a un cierre
  de Perfiles, Layouts y Ajustes a la vez; "Cerrar" (y Atrás) siguen cerrando solo la pantalla propia. La pestaña o el panel nuevo ya
  tomaban el foco al abrirse (`openTab`/`splitActive`), así que se ve de inmediato. Los tres indicadores de pantallas de perfil son un
  solo estado (`ProfileOverlay`, con su `listSaver`) para que `Screens` no crezca más.

### D-FIX-4 · 2026-10-05 · La pestaña de un perfil se llama como el perfil
- **Decisión:** `PaneSpec.profileName` (solo si el perfil está guardado) pasa a `SessionInfo.profileName` y a `TabItem.profileName`;
  `tabNames` da `TabName.InProfile(nombre, n)`. Orden: nombre escrito por el usuario, nombre del perfil, nombre de la distro (RF-13),
  "Shell N". Varias pestañas del mismo perfil se numeran ("dev", "dev 2"), como las de una distro.

### D-FIX-5 · 2026-10-05 · Toda la fila de un campo de texto lo enfoca; los avisos no mueven el formulario
- **Decisión:** `IosTextField` e `IosTextArea` envuelven la fila en un `clickable` sin onda que pide el foco (`FocusRequester`) y
  muestra el teclado, así tocar la etiqueta sirve y vale para todos los formularios. En el formulario de perfil los pies de
  "Nombre" y "Historial" reservan una línea en blanco (`RESERVED_FOOTER`) cuando no hay error, para que el aviso al aparecer o
  desaparecer no desplace las filas de abajo. Un aviso de dos líneas sí lo haría crecer; es raro y se acepta.

### D-FIX-6 · 2026-10-05 · La cápsula roja de emisión va en la franja de cabecera del primer panel
- **Decisión:** la franja de cabecera de los paneles pasa de 40 a 48 dp (el alto del objetivo táctil de la cápsula, que antes tapaba
  8 dp de texto). La cápsula se coloca en la franja del primer panel, a la izquierda del botón "⋯", con el ancho del panel menos
  48 dp (el texto se recorta con puntos suspensivos si no cabe). Siempre visible mientras se emite; tocarla detiene la emisión.
  Coste: 8 dp menos de texto por panel en pestañas divididas.
- **Sin validar** en dispositivo con 4 paneles o más (texto recortado).

### D-FIX-7 · 2026-10-05 · El comando inicial se teclea cuando el shell dibujó su primer prompt
- **Decisión:** `StartupInputGate` (pura, con tiempos inyectados): lista cuando el shell ha escrito algo y lleva 300 ms en silencio
  (el prompt está dibujado y esperando), o a los 5 s del arranque como máximo (un shell mudo o muy ruidoso no pierde el comando).
  `TerminalSessionHost.outputCount` cuenta las salidas (`onTextChanged`); `AndroidSessionFactory` lo consulta cada 50 ms. Sustituye
  el retardo fijo de 800 ms de D-T12b-10, que tecleaba antes del prompt y hacía que el eco se viera dos veces.
- **Límite:** un shell cuya salida de arranque tenga pausas de más de 300 ms (proot lento, un `motd`) puede recibir el comando entre
  dos ráfagas; no se pierde, pero el eco puede repetirse. **Sin validar con un shell real.**

### D-FIX-8 · 2026-10-05 · Dividir un panel con perfil hereda su distro y su usuario
- **Decisión:** `SessionController.splitActive` sin perfil explícito usa `PaneOpening.forSplit()` del panel activo: mismo destino
  (distro, usuario), mismo aspecto y mismo perfil, pero **sin** el comando inicial (volvería a ejecutarse). Un panel sin perfil sigue
  dividiéndose en la distro de origen, como antes.
- **Efecto en guardar:** el panel heredado se guarda con el perfil (`LayoutNode.Pane(profileId)`), así que al reabrir ese layout
  correrá el comando del perfil también en él. Mantiene la distro (D-T12b-10 la perdía) a cambio de eso.

## T18 · Tests de integración y de dispositivo (feat/t18-integration-tests)

Hecho sin dispositivo: los tests de host corren en `./gradlew check`; los instrumentados solo se compilan (`assembleDebugAndroidTest`) y los ejecuta el orquestador en un Pixel 8. Guía de uso en `docs/TESTING.md`.

### D-T18-1 · Tests de integración en host: piezas reales, solo los bordes simulados
- **Decisión:** `app/src/test/.../integration/` une clases reales de `domain` y `data` con los únicos bordes que exigen un dispositivo como fakes (catálogo oficial, proceso, almacenamiento compartido, biblioteca nativa, pty). `InstallAndLaunchIntegrationTest` instala desde un `.tar.gz` servido por MockWebServer (descargador real con SHA-256, extracción transaccional real en un directorio real, Room real) y planifica la sesión con `ProotSessionPlanner`; `BackupRoundTripIntegrationTest` exporta (en claro y cifrado) y restaura en un segundo dispositivo vacío y compara ficheros, distro y usuario por defecto, perfiles, disposiciones, atajos, teclas extra, apariencia y ajustes; `ResizeIntegrationTest` sigue ventana, barras, teclado y margen hasta las llamadas al pty falso, con y sin paneles divididos.
- **Sin código de producción nuevo:** los umbrales de Kover (85 % en `domain`/`data`, 100 % en `data.rootfs.verify` y `data.backup`) no cambian y siguen en verde. Los tests reutilizan `Device`, `TarBuilder` y los fakes ya existentes; no repiten lo que cubren los tests por piezas, sino la unión entre ellas (p. ej. que el usuario por defecto instalado acabe como `-i uid:gid` en la línea de proot, o que un hash falso no deje ni distro ni ficheros).
- **Límite:** el catálogo oficial es un `FakeCatalog` (sus parsers ya tienen tests propios, T06); el proceso y el pty reales solo se prueban en dispositivo.

### D-T18-2 · Pruebas instrumentadas: sin rootfs fijado, el instalador de la app con su SHA-256
- **Decisión:** `ProotGuestTest` y `BackupDeviceRoundTripTest` obtienen Alpine con `DistroInstaller` + `OfficialRootfsCatalog` + `HttpRootfsDownloader`: el índice oficial da URL y hash y la app verifica el archivo. No hay ninguna descarga ni binario fijados en el repositorio. Sin red (índice o descarga inalcanzables) el test se salta con `Assume`; un hash o un HTTP erróneos **fallan**, porque es lo que la verificación debe detectar.
- **Pty y proot reales:** se usa `TerminalSessionHost` con el `ShellStart.proot` que produce el planificador, o sea el mismo camino que la app; se escribe por el pty y se lee la transcripción del emulador. Las comprobaciones evitan falsos positivos con el eco del comando (la línea `echo ok` tecleada no es igual a `ok`). El redimensionado se comprueba con `stty size` en el invitado (24 80, 30 100, 12 60).
- **Copia de seguridad en dispositivo:** exporta una distro real cifrada a un fichero del almacenamiento del dispositivo, restaura en un almacenamiento vacío, compara cada fichero (hash de contenido y destino de los enlaces) y arranca la distro restaurada. Las claves SSH no entran (el almacén usa el Keystore): la ronda con claves queda cubierta en host.

### D-T18-3 · Aislamiento: nunca se tocan las distros del usuario
- **Decisión:** `TestStorage` crea `files/it-t18-<aleatorio>/` con su propio `NioFileSystemRepository` y una base de datos Room **en memoria**, y lo borra en `close()`. No abre `ultimateterminal.db` ni `files/storage`. `MainActivityLaunchTest` es la excepción obligada: lanza la actividad real con los datos reales, pero solo abre y cierra pantallas (el editor de perfil se descarta sin guardar).
- **Aviso operativo:** por defecto el plugin de Android desinstala la app tras `connectedDebugAndroidTest`, lo que borraría los datos del usuario; por eso `docs/TESTING.md` manda pasar `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.

### D-T18-4 · UI Automator en vez de Compose Test
- **Decisión:** la prueba de la actividad usa UI Automator (Apache-2.0) y las cadenas de `strings.xml` (`R.string.*`), así sirve en inglés y en español. Compose Test exigiría `ui-test-manifest` como `debugImplementation`, que entraría en el APK de depuración; UI Automator es solo `androidTestImplementation`.
- **Qué comprueba:** Atrás desde Ajustes vuelve a la terminal; Perfiles con su hoja de edición, y Atajos de teclado con su hoja, se abren sin cierre inesperado. Los toques por texto/descripción son la parte más frágil y no se han podido ejecutar aquí: si falla un selector tras un cambio de la interfaz, hay que ajustarlo en `MainActivityLaunchTest`.

### D-T18-5 · Dependencias de prueba
- **Añadidas** (todas Apache-2.0, solo `androidTestImplementation`, no entran en ningún APK): `androidx.test:runner` 1.7.0, `androidx.test:core` 1.7.0, `androidx.test.ext:junit` 1.3.0 y `androidx.test.uiautomator:uiautomator` 2.4.0, con el runner `androidx.test.runner.AndroidJUnitRunner`.
- **Verificación de dependencias:** `verification-metadata.xml` regenerado con un `GRADLE_USER_HOME` limpio y `--write-verification-metadata sha256`; solo se añadieron los componentes que faltaban (115 líneas, ninguna eliminada ni reformateada). Acreditadas en `THIRD_PARTY_NOTICES.md` en el mismo cambio.
- **Por validar en dispositivo:** que las tres clases pasen en el Pixel 8 con red (`ProotGuestTest`, `BackupDeviceRoundTripTest`, `MainActivityLaunchTest`), en especial los selectores de la interfaz y que `stty` exista en el rootfs de Alpine.

### Séptima ronda (Pixel 8, 2026-10-05, `master` en `f372372`): T18 instrumentados y comprobaciones manuales

- **Tests instrumentados de T18 (`connectedDebugAndroidTest`, solo en el Pixel 8 con `ANDROID_SERIAL`): 7 de 7 pasan** tras corregir un fallo del propio test. `ProotGuestTest` (proot real en Alpine con el instalador de la app: `uname -a`, `echo ok`, `/etc/os-release`, y `stty size` tras dos redimensionados del PTY) y `BackupDeviceRoundTripTest` (copia cifrada de una distro real restaurada en un almacenamiento vacío, con contenido y enlaces idénticos, y contraseña incorrecta sin restaurar nada) pasaron a la primera. `MainActivityLaunchTest` (Atrás desde Ajustes, Perfiles con su hoja, Atajos con su hoja) fallaba con "the terminal screen did not appear": el aviso "Mantener las sesiones activas" aparece como alerta modal al arrancar y oculta lo de debajo a UI Automator; ahora el test pulsa "Ahora no" si aparece (`prompt_not_now`), sin cambiar ningún ajuste.
- **Comprobaciones manuales de #59 y #61, todas bien:** abrir un perfil o un layout cierra Ajustes y enfoca la pestaña; la pestaña se llama como el perfil ("dev"); el comando inicial sale una sola vez; tocar la etiqueta de un campo enfoca el campo y el formulario no se mueve; el aviso de difusión no tapa texto (se recorta a "Lo que escri…" en un panel estrecho; mejorable); con cuatro pestañas el `⋯` de la activa se ve entero; un panel dividido desde un perfil hereda distro y usuario sin repetir el comando; "Cambiar usuario…" y "Establecer como predeterminada" aparecen en el menú de cada distro que no es la predeterminada (`dev2` en Alpine: `uid=1000(dev2)`).
- **Corrección de la quinta ronda:** el hueco "no se puede elegir la predeterminada" era un error mío al mirar: la opción se oculta justo en la distro que ya lo es.

## T21 (segunda pasada) — Revisión de la documentación (docs/t21-review)

Solo texto, sin tocar código ni dispositivos. Todo lo que dice estar verificado sale de las rondas de pruebas de este fichero.

### D-T21-4 · 2026-10-05 · Convenciones de la documentación de cara al usuario
- **Tabla del README:** cada fila dice en qué dispositivo se verificó (Pixel 8 o tablet Huawei) y, si la verificación es parcial, lo dice en la propia fila. Una corrección que está en `master` pero no se ha vuelto a ver en un dispositivo no sube a la tabla: va en el párrafo «Fixed in the code and not looked at on a device yet». Los números de rendimiento se citan con su contexto (build de depuración, Pixel 8, Fedora) y sin redondear a favor.
- **Tests instrumentados:** se describen como «escritos y compilados, sin ejecución registrada» mientras ninguna ronda anote que se ejecutaron (D-T18-1 a 5).
- **`CHANGELOG.md`:** se mantiene una sola sección `[Unreleased]`, agrupada en Added/Changed/Fixed/Known limitations; cada arreglo hallado en un dispositivo indica si se volvió a verificar allí.
- **`PRIVACY.md`:** se revisó contra el manifiesto (sin cambios en los nueve permisos) y contra `OfficialRootfsCatalog` (`dl-cdn.alpinelinux.org`, `cdimage.ubuntu.com`, `raw.githubusercontent.com` y `dl.fedoraproject.org`). Cambios: el dominio de Fedora, y que los DNS de respaldo ya se editan en Ajustes > Red (T16).
- **`fastlane`:** `changelogs/10002.txt` (en/es) se añade para el siguiente `versionCode` (`0.1.0-rc.2` daría `(0*10000 + 1*100 + 0)*100 + 2 = 10002`), siguiendo el nombre de `10001.txt`. `gradle.properties` sigue en `0.1.0-rc.1`: subir la versión es una decisión de release (`RELEASING.md`) que no se tomó aquí; mientras tanto el fichero 10002 no se usa. Límites comprobados con un script: título 16/30, resumen 72/80 (en y es), descripción 3112 y 3331/4000, changelogs 371 y 426/500.
- **`CONTRIBUTING.md`:** recoge la inicialización del submódulo, los tests instrumentados (`ANDROID_SERIAL`, `leaveApksInstalledAfterRun`), la regeneración de `verification-metadata.xml` y la convención de añadir al final de `DECISIONS.md` (y cómo se resuelven los conflictos al rebasar).

### D-T21-5 · 2026-10-05 · Lo que no se pudo comprobar
- **Redirecciones de Fedora:** `PRIVACY.md` nombra solo `dl.fedoraproject.org`, el host de las URLs del catálogo. No se comprobó si ese servidor redirige a otros mirrors al descargar; si lo hace, esos hosts también verían la IP.
- **El aviso de notificaciones:** se describe como «un aviso propio antes del del sistema» (`SessionPrompts`) sin afirmar cuándo aparece.
- **Atrás en la tablet Android 12:** el README y el changelog lo dejan como no verificado (D-FIX-1 de «Correcciones tras pruebas»).
- **Dato desfasado que sigue sin tocarse:** la sección «Pendiente de validar en hardware», al principio de este fichero, aún dice que los agentes no prueban en dispositivos y no recoge las seis rondas; no se edita para no chocar con otras ramas (ver D-T21-1).

### D-T20-7 · 2026-10-05 · Capturas de F-Droid
- **Decisión:** seis capturas de teléfono por idioma (`fastlane/metadata/android/{en-US,es-ES}/images/phoneScreenshots/1..6.png`), tomadas en el Pixel 8 con la app en cada idioma (`cmd locale set-app-locales`) y la barra de estado fija (modo demo del sistema, restaurado al acabar): terminal con Fedora, panel dividido, Ajustes, Apariencia, Distribuciones y Perfiles. Salen la distro de prueba "AlpineOps" y el perfil "dev" de las pruebas; se pueden retomar sin ellas más adelante.
- **Pendiente:** una captura de tablet (`tenInchScreenshots`/`sevenInchScreenshots`) cuando la tablet esté disponible; la huella `AllowedAPKSigningKeys` y `fdroid lint` (D-T20-1..6).
