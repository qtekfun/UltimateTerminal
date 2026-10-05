# UltimateTerminal — Especificación (SPEC)

Versión del documento: 0.1 · Estado: borrador para implementación del MVP

Los puntos marcados **(DEFECTO)** son decisiones por defecto no confirmadas por el usuario: revisar antes de implementar.

## 1. Objetivo

Terminal para Android pensada para **mantenedores de servidores** y sucesora espiritual de Termux: UI moderna, **adaptativa de verdad en tablet** (ocupa toda la pantalla y se redimensiona con multiventana, teclado y plegables) y entornos Linux completos tipo **WSL** mediante `proot`. Solo terminal: sin entorno gráfico. Debe ser útil para otras personas y publicarse en F-Droid.

### Problemas que resuelve (Termux)
1. La UI es anticuada.
2. En tablets no redimensiona bien y no se ve a pantalla completa.
3. Gestionar entornos, copias de seguridad y cambio de dispositivo es manual y frágil.

## 2. Alcance y supuestos

| Tema | Decisión |
|---|---|
| Plataforma | Android, `minSdk` 26. **Tablet y móvil a la par**; diseño adaptativo desde el MVP |
| `targetSdk` | **28**, igual que Termux. Android 10+ prohíbe ejecutar binarios del almacenamiento de la app con `targetSdk` ≥ 29. Consecuencia: **no apto para Google Play** (ya descartado por política) |
| Distribución | F-Droid y GitHub Releases |
| Emulador de terminal | Reutilizar `terminal-emulator` de Termux (Apache-2.0, PTY por JNI), con vista y UI propias en Compose |
| Userland | **Solo distros con proot**, estilo WSL. Sin bootstrap/paquetes propios tipo `pkg` ni prefijo `com.termux` |
| Distros del MVP | Debian, Ubuntu y Alpine; **Fedora se añade como T24** (petición del usuario: es su distro habitual). Rootfs oficiales descargados al instalar, verificados con SHA-256. Arch, en backlog |
| proot | **Compilado desde fuente** en el build (NDK; fuentes de proot y talloc fijadas por hash), empaquetado como `.so`. Reproducible y compatible con F-Droid |
| Entorno gráfico | Ninguno. Ni X11 ni Wayland ni VNC |
| Licencia | GPL-3.0-or-later |
| Idiomas | Inglés y español; arquitectura preparada para más |
| Cuentas/servidores | Todo local. No hay backend ni cuenta propia |
| `applicationId` | `com.qtekfun.ultimateterminal` |

## 3. Requisitos funcionales (MVP)

### RF-01 Terminal y emulación
- Emulación xterm completa (colores 256/truecolor, unicode y emoji, ratón, bracketed paste, alternate screen).
- **Criterios:** `vim`, `tmux`, `htop`, `less`, `nano` y `mc` se ven y responden correctamente. El scrollback es configurable (por defecto 10 000 líneas).

### RF-02 Multitab y paneles
- Varias pestañas, renombrables y reordenables; cada una es una sesión independiente de una distro.
- En pantallas anchas, **paneles divididos** (varias terminales a la vez), además de pestañas.
- **Criterios:** crear, cerrar, mover y cambiar de pestaña no interrumpe el resto de procesos. Al cerrar una sesión con procesos en curso se pide confirmación.

### RF-03 Pantalla adaptativa (requisito principal)
- Edge-to-edge. El área de terminal **se redimensiona** al cambiar la ventana (multiventana, split-screen, ventana flotante, plegables, rotación) y al mostrar/ocultar el teclado, y el PTY recibe el nuevo tamaño (`SIGWINCH`).
- Layout por clases de tamaño de ventana (`WindowSizeClass`): barra de pestañas lateral o superior según el ancho.
- **Criterios:**
  - En una tablet la terminal ocupa el 100 % del área disponible, sin bandas ni huecos.
  - Tras redimensionar, `stty size` coincide con lo que se ve.
  - Rotar o entrar en multiventana no cierra ni reinicia las sesiones.

### RF-04 Distros (estilo WSL)
- Instalar, listar, **renombrar, duplicar** y eliminar distros desde la app. Varias instaladas a la vez.
- Descarga del rootfs oficial con **verificación SHA-256**, progreso, reanudación y reintento.
- Usuario por defecto configurable (root o usuario normal) y distro predeterminada.
- Dentro de la distro funcionan `apt`/`apk`, `ssh`, `nmap`, `python`, etc.
- **Criterios:** instalar Debian desde cero lleva a un prompt operativo sin pasos manuales; un fallo de red a mitad no deja una distro corrupta.

### RF-05 Acceso a archivos del dispositivo
- Bind-mount del almacenamiento compartido (`/sdcard`, Descargas) dentro de la distro, p. ej. en `~/storage`, para usar `cp`/`mv` y bajar logs.
- Se solicita el permiso de almacenamiento solo cuando el usuario activa la función.
- **Criterios:** `cp /var/log/syslog ~/storage/downloads/` aparece en la carpeta Descargas del dispositivo.
- Proveedor SAF (la distro visible en la app Archivos) queda fuera del MVP (ver sección 4).

### RF-06 Copias de seguridad y migración
- Exportar **una distro** (rootfs completo), **solo la configuración** o **todo** a un archivo `.tar.zst`.
- La **configuración de la app** incluye todos los ajustes: tema y modo OLED, fuentes, perfiles, esquemas de color, teclas extra, atajos, layouts guardados, hosts SSH y claves, distro predeterminada, etc. Se exporta como un archivo pequeño e independiente de los rootfs, para dejar un dispositivo nuevo igual que el anterior en un momento.
- Las rutas y valores específicos del dispositivo (p. ej. permiso de almacenamiento) se re-solicitan al restaurar, no se copian.
- **Cifrado opcional** AES-256-GCM con clave derivada (PBKDF2) de una contraseña del usuario, como en UltimateDeck: la clave del Keystore no puede salir del dispositivo.
- Restaurar en un dispositivo nuevo, también desde la pantalla de bienvenida.
- **Criterios:**
  - Exportar solo la configuración en un dispositivo y restaurarla en otro deja la app con el mismo aspecto, atajos, perfiles y layouts, sin pasos manuales.
  - Exportar y restaurar conserva permisos, propietarios, enlaces simbólicos y bits ejecutables del rootfs.
  - Una copia cifrada con contraseña errónea falla de forma clara y sin escribir nada.

### RF-07 Persistencia de sesiones
- **Servicio en primer plano** con notificación persistente que mantiene vivas las sesiones con la app en segundo plano.
- Wakelock opcional. Aviso guiado sobre la optimización de batería.
- **Criterios:** un `ssh` o una tarea larga sobreviven al cambiar de app y a apagar la pantalla.
- Restaurar pestañas tras un cierre real (no procesos): **(DEFECTO) backlog**.

### RF-08 Entrada
- **Fila de teclas extra** (Esc, Tab, Ctrl, Alt, flechas, etc.), configurable, con Ctrl/Alt pegajosos. **Solo se muestra mientras el teclado en pantalla está visible**: al ocultarlo desaparece y devuelve su espacio al terminal (opción "ocultar con el teclado", activa por defecto, para quien prefiera verla siempre).
- **Teclado físico completo:** F1–F12, combinaciones Ctrl/Alt, atajos de la app (**(DEFECTO)** `Ctrl+Shift+T` nueva pestaña, `Alt+n` cambiar de pestaña), ratón y rueda.
- **Tipo de teclado en pantalla:** ajuste en Ajustes > Teclado con tres opciones: **Normal** (**(DEFECTO)**, campo de texto sin sugerencias ni variación de contraseña, para que cada móvil muestre su teclado de siempre y no el «teclado seguro» de algunos fabricantes), **Compatible** (campo de contraseña visible, que algunos teclados necesitan para no autocorregir en un terminal) y **Sin procesar** (sin tipo de entrada, como la opción `input-type` de Termux: solo pulsaciones de teclas). Se guarda y entra en la copia de configuración; cambiarlo reinicia la entrada del teclado. (Pendiente de comprobar en dispositivo.)
- Copiar/pegar, selección táctil con asas, zoom con pellizco.

### RF-09 Gestor de hosts SSH
- Lista de servidores guardados (nombre, host, puerto, usuario, clave) que abre una pestaña con `ssh` ya lanzado en la distro elegida.
- Gestión de claves: generar, importar y exportar. **Las claves privadas se cifran en reposo con Android Keystore** y nunca van a logs.
- **Criterios:** conectar a un host guardado requiere un toque; la clave privada no aparece en texto plano fuera de la distro.

### RF-10 Temas, modo OLED y fuentes
- Tema claro, oscuro, del sistema y **modo OLED**: negro puro (#000000) en fondo de terminal e interfaz para ahorrar batería en pantallas OLED. Se activa por separado del tema oscuro.
- Esquemas de color de terminal (incluye Solarized, Dracula, etc.), editables y con importación/exportación.
- Fuentes monoespaciadas incluidas (**(DEFECTO)** una Nerd Font libre) y tamaño ajustable.

### RF-12 Personalización estilo Terminator
- **Perfiles** (**(DEFECTO)**): conjunto con nombre de esquema de color, fuente, tamaño, scrollback, distro, usuario y comando inicial. Cada pestaña o panel puede usar uno distinto.
- **Layouts guardados:** disposición de pestañas y paneles (divisiones horizontales/verticales con proporciones) que se guarda con nombre y se vuelve a abrir con un toque, cada panel con su perfil y su comando inicial (p. ej. 3 paneles abiertos a tres servidores).
- Dividir un panel en horizontal o vertical, redimensionar arrastrando los separadores y reordenar paneles.
- **Atajos totalmente configurables** (dividir, cerrar, cambiar de panel, pantalla completa de un panel, etc.).
- **Emitir a varios paneles** (**(DEFECTO)**): lo escrito en un panel se envía a todos los del grupo, útil para administrar varios servidores.
- *Estado (T12b):* del perfil, hoy se aplican por panel la distro, el usuario, el scrollback y el comando inicial; el esquema, la fuente y el tamaño se guardan pero aún no se aplican por panel (D-T12b-9). Los números de pestaña (`Alt+1` a `9`) no se pueden reasignar todavía.
- **Criterios:** un layout guardado se restaura idéntico (estructura, perfiles y comandos); los atajos y perfiles se incluyen en la copia de configuración (RF-06).

### RF-11 Ajustes e internacionalización
- Inglés y español; sigue el idioma del sistema.
- **Pantalla de Ajustes siempre accesible:** un icono permanente (⚙) en la barra de pestañas y una entrada en el menú de "+". Ese menú se abre con un botón visible (⋯, "Más opciones") junto al "+" en la barra superior, la lateral y el raíl; la pulsación larga en "+" se mantiene como atajo. No puede depender de abrir antes otra pantalla.
- Secciones: **Apariencia** (tema, modo OLED, esquema, fuente, tamaño, márgenes, cursor; RF-10 y T12c), **Terminal** (scrollback, campana), **Teclado** (filas de teclas extra, ocultar con el teclado, atajos), **Sesiones** (wakelock, permiso de segundo plano), **Distros** (instalar, predeterminada, modo de compatibilidad), **Almacenamiento** (`~/storage`), **Red** (DNS de respaldo), **Copias de seguridad** (RF-06) y **Acerca de** (versión, licencias y créditos).
- **Criterios:** todos los ajustes del MVP se cambian desde esa pantalla, sin editar ficheros ni usar adb; se conservan al reiniciar y viajan en la copia de configuración (RF-06).

### RF-13 Arranque y primera ejecución
- Al abrir la app, la primera pestaña abre la **distro predeterminada** (proot) si está lista; con la app cerrada del todo y reabierta ocurre lo mismo.
- Si no hay ninguna distro instalada, se muestra la **configuración inicial** (RF-15) en lugar del shell de Android.
- Las pestañas se nombran con su distro (p. ej. "Alpine"), no con "Shell N", salvo que el usuario las renombre.
- **Criterio:** instalada una distro, cerrar la app y volver a abrirla deja un prompt de esa distro en la primera pestaña. (Comprobado en un Pixel 8; el nombre de la pestaña, pendiente.)

### RF-14 Diseño estilo iOS
El usuario probó la app en un Pixel 8 y la encontró fea ("hay que rediseñarla al estilo iOS"). La interfaz propia (todo menos el área del terminal) pasa a un diseño **inspirado en iOS**, sobre un sistema de componentes común (`ui/ios`):
- **Títulos grandes que colapsan** al desplazar, con una barra de navegación pequeña que los recoge.
- **Barras translúcidas con desenfoque** del contenido que pasa por debajo (desde Android 12; antes, una barra casi sólida con el mismo aspecto).
- **Listas agrupadas** con esquinas redondeadas sobre fondo gris, separadores finos con sangría, chevrones, valores y filas con interruptor o acción destructiva.
- **Interruptores, controles segmentados, campo de búsqueda y botones** (rellenos, tintados y planos) al estilo iOS.
- **Hojas modales con asa** (alturas media y completa, arrastrables) en lugar de diálogos Material; alertas y hojas de acciones; menús contextuales.
- **Tipografía y espaciado de iOS** (escala de Large Title 34 a Caption 12, rejilla de 8 pt), **animaciones de muelle** y **respuesta háptica**; sin botones flotantes (FAB).
- **Claro, oscuro y OLED coherentes con los esquemas de color de T12**: el tinte sale del azul del esquema elegido y se ajusta para ser legible.
- **Accesibilidad:** TalkBack con roles y estados, zonas táctiles de al menos 48 dp, escala de fuente del sistema y contraste de texto de al menos 4,5:1 en todos los temas y esquemas.

**Qué NO se hace:** no se usan **SF Pro, SF Symbols ni ningún recurso de Apple** (su licencia no lo permite). La tipografía es **Inter** (SIL OFL-1.1) y los iconos son **Lucide** (ISC); ambos se acreditan en `THIRD_PARTY_NOTICES.md`. Es un diseño inspirado, no una copia con marca, y no se afirma ninguna afiliación con Apple. El área del terminal (texto monoespaciado, colores del esquema) no cambia de aspecto.

**Criterios:** el catálogo de componentes (solo en builds de depuración) enseña cada uno en claro, oscuro y OLED; las pantallas rediseñadas (T22b y T22c) solo usan componentes de `ui/ios`; ningún texto va fijo en un componente.

### RF-15 Configuración inicial (primer arranque)
Petición del usuario: «si no hay distro, muestra directamente una configuración inicial para instalar una, para que la experiencia sea coherente». Antes, una instalación nueva abría un shell de Android sin distro y el usuario tenía que descubrir Ajustes > Distribuciones > Gestionar > +.
- Al abrir la app **sin ninguna distro lista y sin pestañas abiertas**, en vez de la pestaña del shell de Android se muestra una pantalla completa de bienvenida, en estilo iOS (`ui/ios`): explicación corta, elección de distribución (Alpine —la descarga más pequeña, preseleccionada y recomendada—, Debian, Ubuntu y Fedora, las del catálogo), nombre y usuario por defecto (`root`), una nota de red y de tamaño, y el botón principal **Instalar**.
- **Restaurar desde una copia de seguridad** reutiliza el flujo de Ajustes > Copias de seguridad (selector del sistema, contraseña si está cifrada, progreso y cancelación).
- La instalación es **la misma** que la de Distribuciones (mismo ViewModel e instalador, con progreso, cancelación y errores). Un fallo se muestra con su motivo y el formulario sigue ahí para **reintentar o elegir otra distro**: no hay callejón sin salida.
- Al terminar, la distro nueva es la **predeterminada**, la configuración se cierra y la primera pestaña la abre (RF-13).
- «Omitir, usar por ahora el shell de Android» (botón de texto, no es el camino por defecto) abre el shell de Android; solo se recuerda durante la sesión: sin distro, la configuración vuelve a mostrarse en el siguiente arranque.
- El aviso «Mantener las sesiones activas» no se muestra sobre la configuración: aparece al empezar la primera sesión.
- **Criterio:** en una instalación nueva se ve la bienvenida, no un shell de Android; instalar Alpine desde ella termina en un prompt de Alpine en la primera pestaña. (Pendiente de comprobar en dispositivo.)

### RF-16 Barra lateral de pestañas dinámica
Petición del usuario (0.1.1): «quiero que la barra lateral en horizontal se encoja al pulsar en el terminal, es decir, que tenga un tamaño dinámico». En ventanas anchas (RF-03, desde 600 dp) la columna de pestañas ocupaba un ancho fijo y, p. ej. en media pantalla, dejaba al terminal con 49 columnas. Ahora tiene dos estados:
- **Desplegada** (192 dp): el aspecto de siempre (píldoras con nombre, «+», ⚙) más un botón con un chevrón para contraerla.
- **Contraída** (franja de 56 dp, con zonas táctiles de 48 dp): la inicial de cada pestaña (la activa resaltada), «+» y ⚙, y arriba un chevrón para desplegarla. Tocar la franja (o su chevrón) la despliega; una pulsación larga sobre una pestaña pregunta «¿Cerrar <nombre>?», igual que en la barra completa (ahí están el menú de renombrar/cerrar y el arrastre para reordenar).
- **Automático:** al tocar un panel del terminal la barra se contrae; solo cambia el estado al tocar, nunca se contrae sola por tiempo. Se muestra desplegada al abrir la app. En la barra superior (ventanas estrechas) no cambia nada.
- **Ajuste** en Ajustes > Terminal: «Contraer la barra lateral al usar el terminal» (por defecto activado); desactivado = siempre desplegada, y no se muestra ningún control para contraerla. Viaja en la copia de configuración (RF-06) con un campo versionado.
- **El PTY** se redimensiona **una vez** por cambio, al ancho final (el área del terminal se calcula con el ancho de destino y solo su dibujo se desliza durante la animación), sin perder el scrollback ni el foco del teclado. Con «Quitar animaciones» del sistema el cambio es instantáneo.
- **Accesibilidad:** la franja y sus pestañas dicen nombre, posición («pestaña 2 de 3»), estado y seleccionada; el control de desplegar/contraer tiene etiqueta, y cada pestaña ofrece la acción personalizada «Desplegar/Contraer la barra lateral».
- **Criterios:** tocar el terminal en horizontal en una tablet o en un móvil ancho contrae la barra y el terminal gana columnas (`stty size`); tocar la franja la despliega y el terminal las pierde; el teclado no se cierra al animarse; en vertical en el Pixel 8 todo queda igual. (Pendiente de comprobar en dispositivo.)

### RF-17 Pulsación larga en una pestaña
Petición del usuario (0.1.2): «si mantengo pulsada una pestaña de la barra, que salga la confirmación de cierre». Una pulsación larga sobre una pestaña (barra completa o franja) que se suelta sin moverse muestra «¿Cerrar <nombre>?» con Cerrar/Cancelar, aunque su proceso ya haya terminado; si hay un proceso vivo se añade el aviso de que se detendrá. Pulsación larga y arrastre sigue reordenando (y no pregunta). Tocar selecciona y doble toque renombra, como antes. TalkBack conserva sus acciones (renombrar, cerrar, mover). (Pendiente de comprobar en dispositivo.)

## 4. Fuera de alcance (MVP)
- Cualquier entorno gráfico (X11, Wayland, VNC).
- Bootstrap y gestor de paquetes propios tipo `pkg`; plugins o addons de Termux.
- Proveedor SAF de documentos (la distro en la app Archivos). Backlog.
- Copias de seguridad automáticas a un destino (SAF/SFTP). Backlog.
- Restaurar pestañas y scrollback tras un cierre. Backlog.
- Más distros (Arch, Fedora, Kali...). Backlog.
- Acceso root del dispositivo, `chroot` o contenedores reales.
- Sincronización en la nube, cuenta propia, telemetría o analíticas.
- Cualquier servicio de Google.

## 5. Modelo de datos y almacenamiento

- **Perfil:** `{ id, nombre, esquema, fuente, tamaño, scrollback, distroId, usuario, comando inicial }`. **Layout:** árbol de paneles con proporciones, perfil y comando por panel. La configuración completa se serializa a un formato versionado (**(DEFECTO)** JSON) para el backup.
- **Distro:** `{ id, nombre, tipo (debian/ubuntu/alpine), versión, ruta del rootfs, usuario por defecto, tamaño, fecha }`. El rootfs vive en el almacenamiento privado de la app.
- **Host SSH:** `{ id, nombre, host, puerto, usuario, claveId, distroId }`. **Pestañas/paneles:** estado de UI en memoria; el servicio es la fuente de verdad de las sesiones vivas.
- Metadatos en Room; los rootfs son directorios del sistema de archivos. Esquema versionado con migraciones probadas.
- Copias de seguridad: tar con `zstd` preservando metadatos POSIX (ver riesgos).

## 6. Requisitos no funcionales
- **Rendimiento:** arranque en frío hasta un prompt < 1,5 s con una distro ya instalada; salida de texto fluida (p. ej. `cat` de un archivo grande sin bloquear la UI).
- **Privacidad:** sin telemetría ni servicios de terceros. La app solo usa la red cuando lo pide el usuario (descarga de rootfs, lo que ejecute en la terminal).
- **Seguridad:** claves cifradas con Keystore; `allowBackup` desactivado (se usa el backup propio); sin logs de contenido de terminal ni de secretos. Verificación SHA-256 de todo lo descargado.
- **Accesibilidad:** TalkBack en la UI de la app (la terminal en sí expone texto), tamaños táctiles ≥ 48 dp, escalado de fuente, contraste suficiente.
- **Robustez:** ninguna pérdida de datos de distros ante cierres, falta de espacio o fallos de red; operaciones largas reanudables o transaccionales.

## 7. Calidad y CI
- **GitHub Actions:** en cada PR, build (incluida la compilación NDK de proot) + detekt + ktlint + Android Lint + tests unitarios + Kover.
- **Cobertura (Kover):** ≥85 % global en `domain`/`data`; **100 % en la verificación de rootfs/descargas y en el formato de backup (cifrado, restauración)**; excluidos código generado, `@Preview` y UI Compose pura.
- **Tests de integración en dispositivo/emulador:** instalar una distro, ejecutar un comando, redimensionar y comprobar `stty size`, exportar y restaurar.
- **Dependabot** semanal agrupado; **comprobación de licencias** que falla ante dependencias no libres o Play Services; verificación de dependencias de Gradle.
- Versionado **SemVer**, Conventional Commits. Builds reproducibles y firma propia como en UltimateDeck (ver `RELEASING.md` cuando exista).

## 8. Riesgos conocidos
1. **Compilar proot en el build y que funcione en Android moderno** (`seccomp`, `ptrace`, `/proc`, `link2symlink`). Es la tarea de mayor riesgo; prototipar primero (Fase 0).
2. **Android 12+ y el "phantom process killer":** el sistema puede matar procesos hijo (límite de ~32) aunque haya servicio en primer plano. Documentar el ajuste de desarrollador y mitigar en lo posible. Android 14+ exige declarar el tipo del servicio en primer plano.
3. **`targetSdk` 28 y su obsolescencia:** Google puede endurecer la instalación de apps con `targetSdk` bajo. Vigilarlo; es el motivo de no publicar en Play.
4. **Vista de terminal propia en Compose:** rendimiento del dibujado (Canvas), selección de texto, IME e ratón. Prototipar antes de construir el resto.
5. **Redimensionado en tablet:** validar `SIGWINCH`/`TIOCSWINSZ` con multiventana y teclado (es el problema que motiva el proyecto).
6. **Backup de rootfs:** preservar propietarios, permisos, symlinks y archivos especiales sin root; tamaño y tiempos de rootfs grandes.
7. **Distribución de rootfs de terceros:** solo se descargan de fuentes oficiales y con hash; no se redistribuyen en el APK.

## 9. Decisiones abiertas (a confirmar durante la implementación)
- Fuente/Nerd Font concreta y su licencia.
- Atajos de teclado por defecto.
- URL y esquema exacto de verificación de cada rootfs (hash vs firma).
- Versión mínima de Android efectiva tras probar proot en API 26–28.
- Si "restaurar pestañas" entra en el MVP (por defecto no).
