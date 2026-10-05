
---

## Correcciones de usabilidad de T12b (rama `fix/t12b-usability`)

Seis fallos de uso hallados en un Pixel 8 real (ninguno era un cierre). Cada uno con su decisión.

### D-FIX-1 · 2026-10-05 · Abrir un perfil o un layout cierra Ajustes entero
- **Decisión:** `ProfilesScreen` y `LayoutsScreen` ganan `onOpened` (por defecto igual a `onClose`). `MainActivity` lo conecta a
  `closeToTerminal`, que cierra Perfiles, Layouts y Ajustes a la vez; "Cerrar" (y Atrás) siguen cerrando solo la pantalla propia.
  La pestaña o el panel nuevo ya tomaban el foco al abrirse (`openTab`/`splitActive`), así que se ve de inmediato.

### D-FIX-2 · 2026-10-05 · La pestaña de un perfil se llama como el perfil
- **Decisión:** `PaneSpec.profileName` (solo si el perfil está guardado) pasa a `SessionInfo.profileName` y a `TabItem.profileName`;
  `tabNames` da `TabName.InProfile(nombre, n)`. Orden: nombre escrito por el usuario, nombre del perfil, nombre de la distro (RF-13),
  "Shell N". Varias pestañas del mismo perfil se numeran ("dev", "dev 2"), como las de una distro.

### D-FIX-3 · 2026-10-05 · Toda la fila de un campo de texto lo enfoca; los avisos no mueven el formulario
- **Decisión:** `IosTextField` e `IosTextArea` envuelven la fila en un `clickable` sin onda que pide el foco (`FocusRequester`) y
  muestra el teclado, así tocar la etiqueta sirve y vale para todos los formularios. En el formulario de perfil los pies de
  "Nombre" y "Historial" reservan una línea en blanco (`RESERVED_FOOTER`) cuando no hay error, para que el aviso al aparecer o
  desaparecer no desplace las filas de abajo. Un aviso de dos líneas sí lo haría crecer; es raro y se acepta.

### D-FIX-4 · 2026-10-05 · La cápsula roja de emisión va en la franja de cabecera del primer panel
- **Decisión:** la franja de cabecera de los paneles pasa de 40 a 48 dp (el alto del objetivo táctil de la cápsula, antes de 48 dp
  sobre 40 de franja: tapaba 8 dp de texto). La cápsula se coloca en la franja del primer panel, a la izquierda del botón "⋯", con el
  ancho del panel menos 48 dp (el texto se recorta con puntos suspensivos si no cabe). Siempre visible mientras se emite, y
  tocarla la detiene. Coste: 8 dp menos de texto por panel en pestañas divididas.
- **Sin validar** en dispositivo con 4 paneles o más (texto recortado).

### D-FIX-5 · 2026-10-05 · El comando inicial se teclea cuando el shell dibujó su primer prompt
- **Decisión:** `StartupInputGate` (pura, con tiempos inyectados): lista cuando el shell ha escrito algo y lleva 300 ms en silencio
  (el prompt está dibujado y esperando), o a los 5 s del arranque como máximo (un shell mudo o muy ruidoso no pierde el comando).
  `TerminalSessionHost.outputCount` cuenta las salidas (`onTextChanged`); `AndroidSessionFactory` lo consulta cada 50 ms. Sustituye
  el retardo fijo de 800 ms de D-T12b-10, que tecleaba antes del prompt y hacía que se viera el eco dos veces.
- **Límite:** un shell cuya salida de arranque tenga pausas de más de 300 ms (proot lento, un `motd`) puede recibir el comando entre
  dos ráfagas; el comando no se pierde, pero el eco puede repetirse. **Sin validar con un shell real.**

### D-FIX-6 · 2026-10-05 · Dividir un panel con perfil hereda su distro y su usuario
- **Decisión:** `SessionController.splitActive` sin perfil explícito usa `PaneOpening.forSplit()` del panel activo: mismo destino
  (distro, usuario), mismo aspecto y mismo perfil, pero **sin** el comando inicial (volvería a ejecutarse). Un panel sin perfil sigue
  dividiéndose en la distro de origen, como antes.
