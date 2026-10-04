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
  `app/src/main/res/raw/third_party_apache_commons.txt`, con un `keep.xml` para que el *resource shrinker* no los
  elimine. Se comprobó con `aapt2` en el APK de release que el recurso existe y es idéntico byte a byte al fuente.
- **Abierto (no se ha tocado):** lo mismo puede afectar a **OkHttp y Okio** (T06) y al resto de dependencias que
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
