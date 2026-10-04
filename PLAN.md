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
- [~] **T09 Multitab**: crear, cerrar, renombrar y reordenar pestañas; confirmación al cerrar con procesos vivos. *(implementada y probada solo en host; la barra no se ha visto en ningún dispositivo; ver `DECISIONS.md`, T09)*
- [~] **T10 Paneles divididos** (pantallas anchas): dividir en horizontal/vertical con separadores arrastrables, y layout por `WindowSizeClass` (barra lateral o superior).
  - *Nota:* implementada y probada solo con tests de host; sin validar en dispositivo (ver `DECISIONS.md`, D-T10-9). Perfiles, layouts guardados y emisión a varios paneles son T12b.
- [~] **T11 Entrada** *(lógica y UI implementadas y probadas en host; sin validar en dispositivo y sin ratón; ver `DECISIONS.md`, T11)*: fila de teclas extra configurable con Ctrl/Alt pegajosos; teclado físico, atajos, ratón, copiar/pegar y zoom con pellizco.
- [~] **T12 Temas, modo OLED y fuentes**: esquemas de color, claro/oscuro/sistema y modo OLED (negro puro), fuente incluida y tamaño.
  - *Nota:* lógica y aplicación probadas en host; sin validar en dispositivo y sin pantalla de ajustes (T16). Ver `DECISIONS.md`, T12.
- [ ] **T12b Perfiles, layouts y atajos (estilo Terminator)**: perfiles, layouts de paneles guardados con nombre, atajos configurables y emisión a varios paneles.
  - *Verificación:* un layout guardado se restaura con la misma estructura, perfiles y comandos.

## Fase 3 — Integración
- [~] **T13 Acceso a archivos** *(lógica y pantalla implementadas y probadas en host; el permiso y el montaje reales sin validar, y proot aún no está conectado a las sesiones; ver `DECISIONS.md`, T13)*: bind-mount de `/sdcard` y Descargas en `~/storage`, con petición del permiso solo al activarlo.
  - *Verificación:* `cp` desde la distro aparece en Descargas del dispositivo.
- [~] **T14 Gestor de hosts SSH y claves**: hosts guardados, generar/importar/exportar claves cifradas con Keystore, lanzar `ssh` en una pestaña. *(implementada y probada en host, incluida una autenticación real con `sshd` de OpenSSH; sin validar en dispositivo y sin conectar a las pestañas normales de una distro; ver `DECISIONS.md`, T14)*
- [~] **T15 Copias de seguridad y restauración**: exportar una distro, solo la configuración o todo a `.tar.zst` (la configuración incluye tema, perfiles, atajos, layouts, teclas extra y hosts, en formato versionado), cifrado opcional AES-256-GCM/PBKDF2, restaurar también desde la bienvenida. **100 % de cobertura** en formato y cifrado.
  - *Nota:* implementada y probada en host (100 % de línea y de rama en `data.backup`); sin validar en dispositivo. Desviación de la SPEC: partes comprimidas con **gzip**, no `.tar.zst` (ver `DECISIONS.md`, T15). El selector de archivos es el del sistema (SAF); no hay aún pantalla de bienvenida propia, la acción de restaurar está en la pantalla de distros.
  - *Verificación:* exportar en un dispositivo y restaurar en otro conserva permisos, propietarios y enlaces simbólicos; restaurar solo la configuración reproduce el mismo aspecto, atajos, perfiles y layouts.
- [ ] **T16 Ajustes e i18n**: idioma, tema, scrollback, teclas extra, wakelock, copias de seguridad.

## Fase 4 — Cierre del MVP
- [ ] **T17 Accesibilidad y rendimiento**: TalkBack en la UI, tamaños táctiles, fuente grande; medir arranque hasta prompt y salida masiva.
- [ ] **T18 Tests de UI e integración clave**: instalar distro, ejecutar comando, redimensionar, exportar y restaurar.
- [x] **T19 Versionado y releases**: SemVer en `gradle.properties` con código derivado, firma propia por variables de entorno, builds reproducibles y workflow de release por tag (`RELEASING.md`).
- [ ] **T20 Metadatos F-Droid**: `fastlane/metadata/android/{en-US,es-ES}/`, iconos, capturas, descripciones; revisar reproducibilidad y ausencia de dependencias no libres.
- [ ] **T21 Documentación**: `README.md`, `CONTRIBUTING.md`, política de privacidad, `CHANGELOG.md`. Explicar cada permiso y decisión (almacenamiento, servicio en primer plano, optimización de batería, `targetSdk` 28, phantom process killer).

## Después del MVP (backlog, no implementar aún)
- Proveedor SAF: la distro visible en la app Archivos.
- Copias de seguridad automáticas a SAF/SFTP.
- Restaurar pestañas y scrollback tras un cierre.
- Más distros (Arch, Fedora, Kali...).
- Comandos de integración dentro de la distro (`ut-share`, `ut-open`).
