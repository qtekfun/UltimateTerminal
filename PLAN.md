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
- [ ] **T05 Modelo Room y repositorios**: distros, hosts SSH y ajustes; abstracción de rootfs/ficheros; migraciones y tests.
- [ ] **T06 Descarga y verificación de rootfs**: Debian, Ubuntu y Alpine desde fuentes oficiales, SHA-256, progreso, reanudación y reintentos. **100 % de cobertura** en la verificación.
- [ ] **T07 Instalación y gestión de distros**: extraer a almacenamiento privado, transaccional (sin distros a medias), listar, renombrar, duplicar y eliminar; usuario por defecto y distro predeterminada.

## Fase 2 — Sesiones y terminal
- [ ] **T08 Servicio en primer plano y sesiones**: el servicio posee PTY y procesos; la UI se reconecta; notificación persistente, wakelock opcional, aviso de batería y tipo de servicio para Android 14+.
- [ ] **T09 Multitab**: crear, cerrar, renombrar y reordenar pestañas; confirmación al cerrar con procesos vivos.
- [ ] **T10 Paneles divididos** (pantallas anchas): dividir en horizontal/vertical con separadores arrastrables, y layout por `WindowSizeClass` (barra lateral o superior).
- [ ] **T11 Entrada**: fila de teclas extra configurable con Ctrl/Alt pegajosos; teclado físico, atajos, ratón, copiar/pegar y zoom con pellizco.
- [ ] **T12 Temas, modo OLED y fuentes**: esquemas de color, claro/oscuro/sistema y modo OLED (negro puro), fuente incluida y tamaño.
- [ ] **T12b Perfiles, layouts y atajos (estilo Terminator)**: perfiles, layouts de paneles guardados con nombre, atajos configurables y emisión a varios paneles.
  - *Verificación:* un layout guardado se restaura con la misma estructura, perfiles y comandos.

## Fase 3 — Integración
- [ ] **T13 Acceso a archivos**: bind-mount de `/sdcard` y Descargas en `~/storage`, con petición del permiso solo al activarlo.
  - *Verificación:* `cp` desde la distro aparece en Descargas del dispositivo.
- [ ] **T14 Gestor de hosts SSH y claves**: hosts guardados, generar/importar/exportar claves cifradas con Keystore, lanzar `ssh` en una pestaña.
- [ ] **T15 Copias de seguridad y restauración**: exportar una distro, solo la configuración o todo a `.tar.zst` (la configuración incluye tema, perfiles, atajos, layouts, teclas extra y hosts, en formato versionado), cifrado opcional AES-256-GCM/PBKDF2, restaurar también desde la bienvenida. **100 % de cobertura** en formato y cifrado.
  - *Verificación:* exportar en un dispositivo y restaurar en otro conserva permisos, propietarios y enlaces simbólicos; restaurar solo la configuración reproduce el mismo aspecto, atajos, perfiles y layouts.
- [ ] **T16 Ajustes e i18n**: idioma, tema, scrollback, teclas extra, wakelock, copias de seguridad.

## Fase 4 — Cierre del MVP
- [ ] **T17 Accesibilidad y rendimiento**: TalkBack en la UI, tamaños táctiles, fuente grande; medir arranque hasta prompt y salida masiva.
- [ ] **T18 Tests de UI e integración clave**: instalar distro, ejecutar comando, redimensionar, exportar y restaurar.
- [ ] **T19 Versionado y releases**: SemVer en `gradle.properties` con código derivado, firma propia por variables de entorno, builds reproducibles y workflow de release por tag (`RELEASING.md`).
- [ ] **T20 Metadatos F-Droid**: `fastlane/metadata/android/{en-US,es-ES}/`, iconos, capturas, descripciones; revisar reproducibilidad y ausencia de dependencias no libres.
- [ ] **T21 Documentación**: `README.md`, `CONTRIBUTING.md`, política de privacidad, `CHANGELOG.md`. Explicar cada permiso y decisión (almacenamiento, servicio en primer plano, optimización de batería, `targetSdk` 28, phantom process killer).

## Después del MVP (backlog, no implementar aún)
- Proveedor SAF: la distro visible en la app Archivos.
- Copias de seguridad automáticas a SAF/SFTP.
- Restaurar pestañas y scrollback tras un cierre.
- Más distros (Arch, Fedora, Kali...).
- Comandos de integración dentro de la distro (`ut-share`, `ut-open`).
