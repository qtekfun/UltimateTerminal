# UltimateTerminal — Plan de tareas

Reglas: una tarea cada vez, en su rama `feat/<tarea>`, con `./gradlew check` en verde antes de cerrarla. Marca `[x]` al completar. Cada tarea debe poder verificarse (test o prueba manual descrita).

## Fase 0 — Cimientos y prototipos de riesgo
- [x] **T00 Proyecto base**: módulo Android, Gradle KTS, `libs.versions.toml`, Hilt, Compose, tema Material 3, `strings.xml` en/es, cabeceras SPDX, `LICENSE` (GPLv3), `targetSdk` 28.
  - *Verificación:* `./gradlew assembleDebug` compila y la app arranca con pantalla vacía.
- [x] **T01 CI y calidad**: detekt, ktlint, Lint (warnings como errores), Kover con umbrales, verificación de dependencias, chequeo de licencias/Play Services, workflow de GitHub Actions, Dependabot.
  - *Verificación:* un PR de prueba pasa CI; una dependencia de Play Services añadida a propósito la hace fallar.
- [x] **T02 Prototipo proot (mayor riesgo)** *(compila y se empaqueta para las 3 ABIs; la ejecución en dispositivo sigue pendiente de validar, ver `DECISIONS.md`)*: compilar proot y talloc desde fuente con NDK en el build, empaquetados como `.so`; arrancar un rootfs Alpine y ejecutar un comando.
  - *Verificación:* en un dispositivo real (API 26–28 y una versión reciente), `proot` ejecuta `/bin/sh` y `uname -a`; documentar `seccomp`, `link2symlink` y límites en `SPEC.md` (sección 9).
- [x] **T03 Prototipo PTY + vista de terminal** *(emulador y vista compilan y están probados en host con 145 tests upstream + los propios; PTY real, dibujo, teclado y gestos sin validar en dispositivo, ver `DECISIONS.md`)*: integrar `terminal-emulator` de Termux y dibujarlo en Compose (Canvas); validar `vim`, `tmux` y `htop`.
  - *Verificación:* demo manual; decisión de rendimiento, selección e IME documentada.
- [~] **T04 Prototipo de redimensionado adaptativo** *(implementada y probada en host; pendiente de validar en tablet real: sin ese dispositivo no se ha comprobado que `stty size` coincida con lo visible ni el comportamiento con multiventana, rotación y teclado; ver `DECISIONS.md`, T04)*: terminal a pantalla completa en tablet con multiventana, rotación y teclado; el PTY recibe el tamaño correcto.
  - *Verificación:* en una tablet, `stty size` coincide con lo visible tras cada cambio; sin bandas ni huecos.

## Fase 1 — Distros y datos
- [x] **T05 Modelo Room y repositorios**: distros, perfiles, layouts, hosts SSH y ajustes; abstracción de rootfs/ficheros; migraciones y tests.
  - *Hecho (solo verificado en JVM de host, ver `DECISIONS.md` D-T05-1..9):* esquema v1 exportado, `MigrationTest`, repositorios Room con fakes verificados por contrato, `FileSystemRepository` sobre `java.nio` que no sigue enlaces simbólicos. Pendiente de validar en Android: cableado Hilt, `AndroidSQLiteDriver` y `java.nio` en el almacenamiento de la app.
- [x] **T06 Descarga y verificación de rootfs**: Debian, Ubuntu y Alpine desde fuentes oficiales, SHA-256, progreso, reanudación y reintentos. **100 % de cobertura** en la verificación.
  - *Nota:* verificado con tests de host (MockWebServer) y con las fuentes reales consultadas por `curl`; la app no ha descargado aún de los mirrors reales (ver `DECISIONS.md`, T06). La instalación y la reconexión al `RootfsCatalog`/`RootfsDownloader` llegan en T07.
- [~] **T07 Instalación y gestión de distros**: extraer a almacenamiento privado, transaccional (sin distros a medias), listar, renombrar, duplicar y eliminar; usuario por defecto y distro predeterminada.
  - *Nota:* implementada y probada solo en el host (385 tests, extracción contra tars hostiles, cancelación a mitad, reintento por hash obsoleto). No se ha instalado ninguna distro en un dispositivo ni se ha comprobado que el rootfs extraído arranque con proot (ver `DECISIONS.md`, T07). La instalación vive en el `ViewModel` hasta que T08 la pase al servicio.

## Fase 2 — Sesiones y terminal
- [~] **T08 Servicio en primer plano y sesiones**: el servicio posee PTY y procesos; la UI se reconecta; notificación persistente, wakelock opcional, aviso de batería y tipo de servicio para Android 14+. *(ciclo de vida de sesiones, servicio, notificación, wake lock y avisos implementados; lógica y manifiesto probados en host, sin validar en dispositivo; ver D-T08-1 a D-T08-6 en `DECISIONS.md`)*
- [~] **T08b Conectar proot a las sesiones** *(añadida: faltaba en el plan)*: T07 instala distros y T09 abre pestañas con
  la distro elegida, pero nada lanzaba proot: toda pestaña abría el shell de Android. Un planificador decide qué arranca
  cada pestaña (distro `READY` con proot, o shell de Android con aviso, o error explicado), con `/etc/resolv.conf`,
  el usuario de la distro, los montajes de T13 y un modo de compatibilidad sin seccomp. Incluye el punto de extensión
  unifica con el `SessionLaunch` de T14 en un único mecanismo de «qué ejecutar» por pestaña, corrige la ruta del rootfs
  de T13 y mueve los botones «Distros» y «SSH» al menú de «nueva pestaña».
  *(Lógica probada en host; **no se ha ejecutado proot en ningún dispositivo**: ver `DECISIONS.md`, T08b.)*
- [~] **T08c Correcciones halladas en hardware** *(añadida: sale de las primeras pruebas reales en un Pixel 8)*: permiso de batería
  con el diálogo del sistema, interruptor de almacenamiento que no engaña, cabecera de la pantalla de Distros
  sin solapes, `/proc` falso dentro de proot (`top`, `uptime`, `free`) y un pulido mínimo del terminal (margen del
  texto y el botón de paneles fuera de la primera línea).
  - *Verificación:* tests de host (`BatteryExemptionTest`, `StorageToggleTest`, `FakeProcTest`, `TerminalLayoutTest`,
    planificador). *Sin probar en hardware*: hay que volver a comprobarlos en el Pixel 8 (ver `DECISIONS.md`, T08c).
- [~] **T09 Multitab**: crear, cerrar, renombrar y reordenar pestañas; confirmación al cerrar con procesos vivos. *(implementada y probada solo en host; la barra no se ha visto en ningún dispositivo; ver `DECISIONS.md`, T09)*
- [~] **T09b Pestañas con nombre de distro y repintado** (RF-13): una pestaña nueva se queda en blanco hasta cambiar de pestaña y volver (hallado en un Pixel 8: la vista no se repinta al activar una sesión nueva ni tras escribir en ella); nombrar la pestaña con su distro en vez de "Shell N"; con una sola distro lista, la primera pestaña al arrancar ya abre esa distro (comprobado) y lo dice en su título.
  - *Hecho (2026-10-04), probado solo en host:* la causa era `SessionManager.activeHost` (se calculaba al publicarse el estado, antes de registrarse el host de la pestaña nueva, y no se reevaluaba); ahora el registro de hosts es observable (`HostRegistry`) y las pestañas se nombran con su distro (`tabNames`). Falta confirmarlo en un dispositivo: pestaña nueva visible al instante y lo escrito repintado sin cambiar de pestaña. Ver D-T09b-1 a 3.
- [~] **T10 Paneles divididos** (pantallas anchas): dividir en horizontal/vertical con separadores arrastrables, y layout por `WindowSizeClass` (barra lateral o superior).
  - *Nota:* implementada y probada solo con tests de host; sin validar en dispositivo (ver `DECISIONS.md`, D-T10-9). Perfiles, layouts guardados y emisión a varios paneles son T12b.
- [~] **T11 Entrada** *(lógica y UI implementadas y probadas en host; sin validar en dispositivo y sin ratón; ver `DECISIONS.md`, T11)*: fila de teclas extra configurable con Ctrl/Alt pegajosos; teclado físico, atajos, ratón, copiar/pegar y zoom con pellizco.
  - *Cambio (2026-10-04):* la fila de teclas extra solo se muestra con el teclado en pantalla visible (opción `onlyWithKeyboard`, activa por defecto): probada en host, pendiente de ver en el dispositivo.
- [~] **T12 Temas, modo OLED y fuentes**: esquemas de color, claro/oscuro/sistema y modo OLED (negro puro), fuente incluida y tamaño.
  - *Nota:* lógica y aplicación probadas en host; sin validar en dispositivo y sin pantalla de ajustes (T16). Ver `DECISIONS.md`, T12.
- [~] **T12c Apariencia personalizable** *(implementada y probada en host; sin validar en dispositivo)*: pantalla "Apariencia" con vista previa en vivo (tema, OLED, esquema, fuente, espaciados, margen, cursor, estilo de las barras), **fuentes propias** importadas (.ttf/.otf monoespaciadas, validadas) y **editor de esquemas** de color con aviso de contraste. Sale de la prueba real en un Pixel 8 ("la vista del terminal es fea, ¿podemos poner fuentes custom y diseño custom?"). T16 la enlazará desde los ajustes generales; T15 debe incluir sus claves y `files/fonts/` en la copia de configuración.
  - *Verificación:* tests de host del dominio (`domain/appearance`), de los ajustes y del importador de fuentes; la apariencia real (colores, fuente, espaciados, cursor, barras) y el selector de documentos hay que verlos en un dispositivo (ver `DECISIONS.md`, T12c).
- [ ] **T12b Perfiles, layouts y atajos (estilo Terminator)**: perfiles, layouts de paneles guardados con nombre, atajos configurables y emisión a varios paneles.
  - *Verificación:* un layout guardado se restaura con la misma estructura, perfiles y comandos.

## Fase 3 — Integración
- [~] **T13 Acceso a archivos** *(lógica y pantalla implementadas y probadas en host; el permiso y el montaje reales sin validar, y proot aún no está conectado a las sesiones; ver `DECISIONS.md`, T13)*: bind-mount de `/sdcard` y Descargas en `~/storage`, con petición del permiso solo al activarlo.
  - *Verificación:* `cp` desde la distro aparece en Descargas del dispositivo.
- [~] **T14 Gestor de hosts SSH y claves**: hosts guardados, generar/importar/exportar claves cifradas con Keystore, lanzar `ssh` en una pestaña. *(implementada y probada en host, incluida una autenticación real con `sshd` de OpenSSH; sin validar en dispositivo y sin conectar a las pestañas normales de una distro; ver `DECISIONS.md`, T14)*
- [~] **T15 Copias de seguridad y restauración**: exportar una distro, solo la configuración o todo a `.tar.zst` (la configuración incluye tema, perfiles, atajos, layouts, teclas extra y hosts, en formato versionado), cifrado opcional AES-256-GCM/PBKDF2, restaurar también desde la bienvenida. **100 % de cobertura** en formato y cifrado.
  - *Nota:* implementada y probada en host (100 % de línea y de rama en `data.backup`); sin validar en dispositivo. Desviación de la SPEC: partes comprimidas con **gzip**, no `.tar.zst` (ver `DECISIONS.md`, T15). El selector de archivos es el del sistema (SAF); no hay aún pantalla de bienvenida propia, la acción de restaurar está en la pantalla de distros.
  - *Verificación:* exportar en un dispositivo y restaurar en otro conserva permisos, propietarios y enlaces simbólicos; restaurar solo la configuración reproduce el mismo aspecto, atajos, perfiles y layouts.
- [ ] **T16 Ajustes e i18n** (RF-11, ampliada tras las primeras pruebas en un Pixel 8, donde el usuario no veía ningún menú de ajustes): icono ⚙ permanente en la barra de pestañas y entrada en el menú de "+"; pantalla con secciones Apariencia (enlaza T12c), Terminal, Teclado (filas de teclas extra, "ocultar con el teclado", atajos), Sesiones (wakelock y permiso de segundo plano), Distros, Almacenamiento, Red (DNS de respaldo configurables), Copias de seguridad (T15) y Acerca de (versión, licencias y créditos desde `THIRD_PARTY_NOTICES.md`); idioma.
  - *Verificación:* cada ajuste se cambia desde la pantalla y persiste tras reiniciar; todo cabe en la copia de configuración de T15.

## Fase 3b — Rediseño estilo iOS (RF-14)
- [~] **T22a Sistema de diseño estilo iOS: componentes base** *(hecha en host y sin validar en dispositivo: ver D-T22a-9 en `DECISIONS.md`)*: paquete `ui/ios` con tokens, tema (claro, oscuro y OLED desde los esquemas de T12), tipografía Inter, iconos Lucide, título grande colapsable con barra translúcida, lista agrupada, interruptor, control segmentado, botones, campo de búsqueda, hoja modal con detents, alerta, hoja de acciones y menú contextual; lógica pura en `domain/ios` con tests; catálogo de componentes solo en depuración.
  - *Verificación:* tests de host del dominio (contraste de todos los esquemas en los tres temas); el orquestador revisa el catálogo en el dispositivo (`adb shell am start -n com.qtekfun.ultimateterminal/.ui.ios.IosCatalogActivity`).
- [~] **T22b Cromo del terminal en estilo iOS**: barra de pestañas (píldoras o segmentos), fila de teclas extra con teclas redondeadas, menús de "+" y de paneles, botón de paneles `⋮` y avisos, usando solo `ui/ios`. El área del terminal no cambia.
  - *Hecho (2026-10-04), probado solo en host:* barra de pestañas en cápsulas, teclas extra como teclas de teclado, menús "+" y de paneles con `IosContextMenu`, renombrar/cerrar con alertas iOS, aviso de lanzamiento como tarjeta. Sin validar en dispositivo (aspecto real, gestos, desenfoque). Ver D-T22b-1 a 8.
- [ ] **T22c Distros, diálogos y Ajustes en estilo iOS**: la pantalla de distros, los diálogos (instalar, renombrar, contraseña, SSH, copias), y la pantalla de Ajustes de T16 con listas agrupadas, hojas modales y alertas de `ui/ios`.

## Fase 4 — Cierre del MVP
- [ ] **T17 Accesibilidad y rendimiento**: TalkBack en la UI, tamaños táctiles, fuente grande; medir arranque hasta prompt y salida masiva.
- [ ] **T18 Tests de UI e integración clave**: instalar distro, ejecutar comando, redimensionar, exportar y restaurar.
- [x] **T19 Versionado y releases**: SemVer en `gradle.properties` con código derivado, firma propia por variables de entorno, builds reproducibles y workflow de release por tag (`RELEASING.md`).
- [~] **T20 Metadatos F-Droid**: `fastlane/metadata/android/{en-US,es-ES}/` con título, resúmenes, descripciones, changelog `10001` e icono, revisión de la receta `fdroid/…yml`. *(Texto y límites comprobados; **faltan las capturas reales** —se hacen en un dispositivo, mejor una en tablet—, la huella de `AllowedAPKSigningKeys`, comprobar CMake 3.31.6 en el servidor de F-Droid y pasar `fdroid lint`; ver `DECISIONS.md`, D-T20-1 a 6.)*
- [~] **T21 Documentación**: `README.md` (estado honesto, tabla dispositivo/host), `CONTRIBUTING.md`, `PRIVACY.md` (en/es, cada permiso y cada dominio), `CHANGELOG.md`. *(Escritos con lo que existe en `master` el 2026-10-04; hay que **revisarlos al cerrar T16, T22b/c y T12b** y al cambiar el manifiesto; lo que dicen estar verificado en dispositivo sale de las pruebas en un Pixel 8, ver `DECISIONS.md`, D-T21-1 a 3.)*

- [ ] **T24 Fedora como distro** (petición del usuario: su distro habitual; estaba en el backlog de la SPEC §2): añadir Fedora al catálogo (`data/rootfs`) con su imagen de contenedor oficial. Hallazgos previos: (a) los rootfs de Fedora vienen en **tar.xz**, y el extractor de T07 solo lee gzip: hace falta descompresión xz (`org.tukaani:xz`, dominio público/0BSD, ya dependencia de Commons Compress; comprobar licencia y acreditarla), manteniendo las reglas de seguridad de T07 (path traversal, enlaces, bombas de descompresión; el xz tiene mucho más ratio, revisar el límite); (b) la descarga es mayor (~60-70 MB comprimidos frente a ~4 MB de Alpine): progreso, reanudación y espacio libre ya existen en T06/T07, comprobar el espacio necesario descomprimido (~200 MB); (c) origen y hash: investigar el origen oficial vigente (`docker-brew-fedora` o los `Container/<arch>/images` de dl.fedoraproject.org con su fichero CHECKSUM) y cómo evitar URLs que caducan, como D-T06-1; el hash por HTTPS sin firma GPG es el mismo riesgo aceptado de D-T06-2; (d) `dnf` dentro de proot: documentar lo que se sepa (modo de compatibilidad, `--link2symlink`, `rpm` y SELinux) y dejar la prueba real en el Pixel 8 (`dnf install` de un paquete) como criterio. Usuario por defecto root; añadir Fedora a la pantalla de instalación y a las cadenas en/es.
  - *Verificación:* tests de host de la descompresión xz y del catálogo (con muestras sintéticas, sin red real); prueba en el dispositivo: instalar Fedora, abrir pestaña, `cat /etc/fedora-release`, `dnf --version`.

## Después del MVP (backlog, no implementar aún)
- Proveedor SAF: la distro visible en la app Archivos.
- Copias de seguridad automáticas a SAF/SFTP.
- Restaurar pestañas y scrollback tras un cierre.
- Más distros (Arch, Fedora, Kali...).
- Comandos de integración dentro de la distro (`ut-share`, `ut-open`).
