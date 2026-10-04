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
- Capa de datos (T05): el cableado de Hilt, `AndroidSQLiteDriver` y `java.nio` (enlaces simbólicos,
  permisos, `ATOMIC_MOVE`) en el almacenamiento privado de la app (ver D-T05-9).
- T03, todo lo que necesita un PTY real o una pantalla: que `libtermux.so` cargue y el `fork/exec` de
  `/system/bin/sh` funcione; que el dibujo (colores, cursor, texto ancho, fuente) sea correcto y fluido;
  el teclado en pantalla (IME), el teclado físico, los gestos (scroll y selección) y que `stty size`
  coincida con lo visible tras redimensionar (ver D-T03-4 a D-T03-6 y T04).
- T03, release con R8: que los métodos nativos de `JNI` sobrevivan a la minificación (las reglas por
  defecto de Android conservan los nombres de los `native`, pero no se ha comprobado en un APK real).

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
