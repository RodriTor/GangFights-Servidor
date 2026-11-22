# Registro de cambios - Servidor

## [1.0.0] - 2026-11-22
### Añadido
- Servidor UDP autoritativo (puerto 6767): descubrimiento por broadcast, conexión de 2 jugadores, estado a 30 Hz, latidos, desconexión por tiempo, cierre ordenado.
- Física con Box2D a paso fijo de 1/60 s, solo en el servidor. El mapa (capa `Colisiones` de Aula.tmx) viene dentro del jar.
- Reglas completas del juego: golpes con combo de 3 etapas e inmunidad, botiquín y pistola, modo disparo (mira de -30 a 90 grados, balas en diagonal, 10 de daño, sin aturdir), rondas al mejor de 5, revancha.
- Ventana del servidor con el logo grande, "SERVIDOR CONECTADO", el puerto y los jugadores conectados (n/2).
- Proyecto multimódulo como el cliente: `core` (lógica), `lwjgl3` (lanzador y jar) y `assets` (mapa y logo).
- Jar ejecutable con todas las librerías (`gradlew lwjgl3:jar`).
