# UltimateTerminal — instrucciones para Claude Code

Terminal moderna para Android (sucesora de Termux) con distros Linux vía **proot**, estilo WSL. Adaptativa en tablet, solo terminal, software libre (GPLv3), destino final: F-Droid.

Lee siempre `SPEC.md` (qué construir) y `PLAN.md` (en qué orden) antes de empezar. Si algo de este archivo contradice a la spec, para y pregunta.

## Identidad del proyecto
- Nombre: **UltimateTerminal**
- `applicationId`: `com.qtekfun.ultimateterminal`
- Licencia: **GPL-3.0-or-later** (cabecera SPDX en cada archivo fuente). El código de Termux que se reutilice conserva su licencia original y se atribuye.
- Idiomas de la UI: inglés (por defecto) y español. **Ninguna cadena visible va hardcodeada**: todo en `strings.xml` (`values/` y `values-es/`).

## Stack (no cambiar sin preguntar)
- Kotlin, Jetpack Compose, Material 3 (colores dinámicos + modo oscuro), `WindowSizeClass`/layouts adaptativos
- `minSdk` 26, **`targetSdk` 28** (obligatorio: permite ejecutar binarios desde el almacenamiento de la app). No subirlo sin preguntar.
- Arquitectura: MVVM + capas `ui` / `domain` / `data`, flujo unidireccional (StateFlow)
- Inyección: Hilt · Persistencia: Room (metadatos) · Segundo plano: servicio en primer plano + WorkManager para descargas
- Emulador: `terminal-emulator` de Termux (Apache-2.0) envuelto en la capa `terminal`; vista propia en Compose
- proot y talloc: compilados desde fuente con NDK, fijados por hash
- Gradle con Kotlin DSL y catálogo de versiones (`gradle/libs.versions.toml`)

## Reglas de software libre (F-Droid) — innegociables
- **Prohibido**: Firebase, Google Play Services, Crashlytics, analíticas, SDKs propietarios, cualquier dependencia no libre. **Prohibida** toda telemetría.
- Antes de añadir una dependencia: comprueba su licencia (compatible con GPLv3) y pregunta al usuario.
- No incluir binarios precompilados de terceros en el repo ni en el APK: proot se compila en el build; los rootfs se **descargan** de fuentes oficiales con verificación SHA-256.
- Metadatos de publicación en formato fastlane: `fastlane/metadata/android/{en-US,es-ES}/`.
- Builds reproducibles: sin timestamps ni valores no deterministas.

## Comandos
- Build debug: `./gradlew assembleDebug`
- Tests unitarios: `./gradlew testDebugUnitTest`
- Tests instrumentados: `./gradlew connectedDebugAndroidTest`
- Lint y estilo: `./gradlew detekt ktlintCheck lintDebug`
- Cobertura: `./gradlew koverVerify koverHtmlReport`
- Todo lo que corre la CI: `./gradlew check`

## Calidad y tests
- Cada tarea termina con `./gradlew check` en verde. No marques una tarea como hecha si falla.
- Stack de tests: JUnit5 + MockK, Turbine, MockWebServer (descargas), Room en memoria; pruebas instrumentadas para PTY, proot y redimensionado.
- **Cobertura (Kover):** ≥85 % en `domain` y `data`; **100 %** en verificación de rootfs/descargas y en el formato de backup (cifrado y restauración). Excluidos: código generado, `@Preview`, UI Compose pura.
- **Nunca escribas tests vacíos o tautológicos** para subir el número.
- Warnings de Kotlin y Lint tratados como errores.

## Flujo de trabajo
- **Una tarea de `PLAN.md` cada vez**, en una rama `feat/<tarea>`.
- Empieza en modo plan: propón el enfoque y espera confirmación antes de tocar código.
- Commits **Conventional Commits** (`feat:`, `fix:`, `test:`, `chore:`, `docs:`...), pequeños y atómicos.
- No hagas `git push --force`, no reescribas historia compartida, no toques `main` directamente.
- Al terminar cada tarea: resume en 2-3 líneas qué se hizo y qué queda; marca la tarea en `PLAN.md`.
- Si la spec es ambigua o falta información: **pregunta**, no inventes.

## Convenciones de código
- Un archivo por clase pública relevante; paquetes por feature dentro de cada capa.
- Sin lógica de negocio en composables ni en ViewModels pesados: va en `domain`.
- Inmutabilidad por defecto. Errores de red/IO con tipos sellados, no excepciones sueltas hacia la UI.
- **Todo I/O fuera del hilo principal** (Dispatchers inyectables). Operaciones largas (descarga, extracción, backup, restauración) en servicio con notificación y reanudables o transaccionales: nunca dejar una distro a medias.
- **El acceso a rootfs y ficheros pasa por una abstracción** (`DistroRepository`/`FileSystemRepository`); sin `java.io.File` suelto en `ui` ni `domain`.
- Los PTY y procesos los gestiona el servicio, no la Activity: la UI se reconecta a sesiones vivas.
- Los secretos (claves SSH, contraseñas de backup) se cifran con Android Keystore; nunca en logs ni en texto plano. Nunca se registra el contenido de la terminal.
- Accesibilidad: `contentDescription`, tamaños táctiles ≥ 48 dp, fuente grande.

## Qué NO hacer
- No implementes nada marcado como "Fuera de alcance" en `SPEC.md` (sobre todo: entorno gráfico, bootstrap/`pkg` propio, SAF).
- No dependas del prefijo `com.termux` ni de binarios de Termux.
- No cambies versiones de dependencias a mano: lo gestiona Dependabot.
- No desactives ni relajes detekt, ktlint, Lint o Kover para que pase la CI.
