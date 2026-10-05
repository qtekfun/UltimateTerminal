<!--
  SPDX-FileCopyrightText: 2026 UltimateTerminal contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->

# Privacy policy / Política de privacidad

Last updated / Última actualización: 2026-10-05 · Version / Versión: 0.1.0

[English](#english) · [Español](#español)

---

## English

UltimateTerminal is free software with no server of its own. **It has no telemetry, no analytics, no
crash reporting, no ads, no accounts and no Google services, and it does not send anything about you
to its developers.** This page says what the app does with data and why it asks for each permission.

### What stays on your device

Everything the app stores is in its private storage on your device: the Linux distributions you
install, your settings and appearance choices, the list of SSH hosts, and SSH keys. Android's own
backup is turned off for the app (`allowBackup=false`), so none of this is copied to Google's cloud by
the system. SSH private keys are encrypted at rest with a key held by the Android Keystore. By design, the app
does not write the content of your terminal, or any secret, to a log.

### Where the network is used

The app only uses the network for what you start:

- **Installing a distribution.** It downloads the root filesystem from the project's official
  mirror and checks it with SHA-256 before using it: Alpine Linux from `dl-cdn.alpinelinux.org`, Ubuntu from
  `cdimage.ubuntu.com`, Debian from `raw.githubusercontent.com` (the repository where the
  Debian maintainers publish their images) and Fedora from `dl.fedoraproject.org` (the official
  container image and its checksum file). Those servers can see your IP address and the file you ask for,
  as with any download. Only HTTPS is used.
- **What you run in the terminal.** `apt`, `apk`, `ssh`, `curl` and anything else you start inside a
  distribution make the connections you tell them to, to servers you choose. Their traffic is not
  handled by this app, and the software of each distribution has its own privacy terms.
- **Name resolution (DNS).** Inside a distribution the app uses the DNS servers of your current network. Only
  if the device reports none, it falls back to the public resolvers **1.1.1.1 (Cloudflare) and 9.9.9.9
  (Quad9)**, which would then see the names you look up. You can replace them with your own servers in
  Settings, Network.

### Permissions, and why each is there

| Permission | Why |
|---|---|
| `INTERNET` | To download distributions and so that the programs you run can reach the network. |
| `ACCESS_NETWORK_STATE` | To read the DNS servers of the current network, for the distribution's `resolv.conf`. Android grants it without asking. |
| `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` | To keep your terminal sessions (an `ssh` connection, a long job) alive when the app is in the background. Android requires a visible notification while it runs. The service type is "special use" because no standard type fits a terminal. |
| `POST_NOTIFICATIONS` (Android 13+) | To show that notification. The app explains it in its own alert before the system prompt, and you can say no (also in Settings, Sessions). |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | So the app can ask you, with the system's own dialog, to let it run without battery optimisation, so a long task is not paused with the screen off. It only opens that dialog (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`); nothing changes unless you accept. It is optional. |
| `WAKE_LOCK` | For the optional "keep awake" setting. Off unless you turn it on. |
| `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` | Only to let the distribution read and write your shared folders (Downloads, Documents, photos) under `~/storage`. The app asks for it **only when you turn that option on**, and the option is off by default. The permission is declared in the app, but it is not used otherwise. On newer versions of Android the system may grant it only for media files. |

The app does **not** ask for the camera, microphone, location, contacts, phone, SMS, accounts or the
"all files access" permission. When you pick a file to import (a font, a backup) or a place to save one
(a backup), the app uses the system file picker and sees only the file you choose.

### Backups and passwords

A backup you export is a file you choose where to save; the app does not upload it anywhere. You can
encrypt it with a password (AES-256-GCM, with the key derived from the password). **The app does not keep
the password and cannot recover it: if you lose it, the backup cannot be opened.** SSH private keys are
included only in an encrypted backup, because the Keystore key that protects them cannot leave the device.

### Why the app targets an old Android version, and is not on Google Play

To run the programs of a Linux distribution from its own private storage, the app declares `targetSdk 28`
(Android 9). On Android 10 and later, an app that targets API 29 or newer is forbidden from executing
files in its storage, which proot needs. Google Play no longer accepts apps that old, so UltimateTerminal is distributed
on F-Droid and GitHub Releases. It does not change what data the app can reach.

### Children, changes and contact

The app is not directed at children and collects nothing from anyone. If this policy changes, the new
version is published in this repository with a new date. Questions or problems:
<https://github.com/qtekfun/UltimateTerminal/issues>.

---

## Español

UltimateTerminal es software libre y no tiene servidor propio. **No tiene telemetría, analíticas, informes
de fallos, anuncios, cuentas ni servicios de Google, y no envía nada sobre ti a sus desarrolladores.** Esta
página explica qué hace la app con los datos y por qué pide cada permiso.

### Lo que se queda en tu dispositivo

Todo lo que la app guarda está en su almacenamiento privado: las distribuciones Linux que instalas, tus
ajustes y elecciones de apariencia, la lista de hosts SSH y las claves SSH. La copia de seguridad del propio
Android está desactivada para la app (`allowBackup=false`), así que el sistema no copia nada de esto a la
nube de Google. Las claves privadas SSH se cifran en reposo con una clave que guarda el Keystore de Android.
Por diseño, la app no escribe en un registro el contenido de tu terminal ni ningún secreto.

### Dónde se usa la red

La app solo usa la red para lo que tú inicias:

- **Instalar una distribución.** Descarga el sistema de archivos del mirror oficial del proyecto y lo
  comprueba con SHA-256 antes de usarlo: Alpine Linux desde `dl-cdn.alpinelinux.org`, Ubuntu desde
  `cdimage.ubuntu.com`, Debian desde `raw.githubusercontent.com` (el repositorio donde los mantenedores de
  Debian publican sus imágenes) y Fedora desde `dl.fedoraproject.org` (la imagen de contenedor oficial y su
  archivo de sumas de comprobación). Esos servidores pueden ver tu dirección IP y el archivo que pides, como en
  cualquier descarga. Solo se usa HTTPS.
- **Lo que ejecutes en la terminal.** `apt`, `apk`, `ssh`, `curl` y todo lo que lances dentro de una
  distribución hacen las conexiones que les indiques, a servidores que tú eliges. Su tráfico no lo gestiona
  esta app, y el software de cada distribución tiene sus propios términos de privacidad.
- **Resolución de nombres (DNS).** Dentro de una distribución la app usa los servidores DNS de tu red actual.
  Solo si el dispositivo no informa de ninguno, recurre a los resolutores públicos **1.1.1.1 (Cloudflare) y
  9.9.9.9 (Quad9)**, que entonces verían los nombres que consultas. Puedes sustituirlos por tus propios
  servidores en Ajustes, Red.

### Permisos, y por qué está cada uno

| Permiso | Para qué |
|---|---|
| `INTERNET` | Para descargar distribuciones y para que los programas que ejecutas accedan a la red. |
| `ACCESS_NETWORK_STATE` | Para leer los servidores DNS de la red actual, para el `resolv.conf` de la distribución. Android lo concede sin preguntar. |
| `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_SPECIAL_USE` | Para mantener vivas tus sesiones de terminal (una conexión `ssh`, una tarea larga) cuando la app está en segundo plano. Android exige una notificación visible mientras funciona. El tipo de servicio es «uso especial» porque ningún tipo estándar encaja con una terminal. |
| `POST_NOTIFICATIONS` (Android 13+) | Para mostrar esa notificación. La app lo explica en un aviso propio antes del del sistema, y puedes decir que no (también en Ajustes, Sesiones). |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Para que la app pueda pedirte, con el diálogo del propio sistema, que la dejes funcionar sin optimización de batería, y que una tarea larga no se pause con la pantalla apagada. Solo abre ese diálogo (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`); no cambia nada si no aceptas. Es opcional. |
| `WAKE_LOCK` | Para el ajuste opcional «mantener despierto». Desactivado salvo que lo actives. |
| `READ_EXTERNAL_STORAGE` y `WRITE_EXTERNAL_STORAGE` | Solo para que la distribución lea y escriba tus carpetas compartidas (Descargas, Documentos, fotos) en `~/storage`. La app lo pide **solo cuando activas esa opción**, que viene desactivada. El permiso está declarado en la app, pero no se usa para nada más. En versiones recientes de Android el sistema puede concederlo solo para archivos multimedia. |

La app **no** pide cámara, micrófono, ubicación, contactos, teléfono, SMS, cuentas ni el permiso de «acceso a
todos los archivos». Cuando eliges un archivo para importar (una fuente, una copia de seguridad) o un lugar
donde guardar uno (una copia de seguridad), la app usa el selector de archivos del sistema y solo ve el
archivo que elijas.

### Copias de seguridad y contraseñas

Una copia de seguridad que exportas es un archivo que guardas donde tú elijas; la app no lo sube a ningún
sitio. Puedes cifrarla con una contraseña (AES-256-GCM, con la clave derivada de la contraseña). **La app no
guarda la contraseña y no puede recuperarla: si la pierdes, la copia no se puede abrir.** Las claves privadas
SSH solo van en una copia cifrada, porque la clave del Keystore que las protege no puede salir del dispositivo.

### Por qué la app apunta a una versión antigua de Android y no está en Google Play

Para ejecutar los programas de una distribución Linux desde su propio almacenamiento privado, la app declara
`targetSdk 28` (Android 9). En Android 10 y posteriores, una app que apunta a la API 29 o más reciente tiene prohibido
ejecutar archivos de su almacenamiento, y proot lo necesita. Google Play ya no acepta apps tan antiguas, por lo que
UltimateTerminal se distribuye en F-Droid y en GitHub Releases. Esto no cambia a qué datos puede acceder la app.

### Menores, cambios y contacto

La app no está dirigida a menores y no recoge nada de nadie. Si esta política cambia, la nueva versión se
publica en este repositorio con una fecha nueva. Preguntas o problemas:
<https://github.com/qtekfun/UltimateTerminal/issues>.
