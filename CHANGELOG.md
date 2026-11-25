# Registro de cambios - Servidor

## [Actualización] - 2026-11-23
### Cambiado
- Los hilos pasan a ser clases propias como en los trabajos de referencia: `HiloServer extends Thread` (dueño del socket, recibe y envía) y `HiloJuego extends Thread` (bucle del juego), más las clases `Cliente` y `DireccionRed`. `ServidorUDP` queda como coordinador. El comportamiento y el protocolo no cambian.
### Añadido
- La ventana del servidor muestra la partida en vivo (solo mirar): mapa, jugadores, balas, items y HUD, con una franja arriba con el logo, "SERVIDOR CONECTADO" y n/2. Pantalla de inicio a la resolución de la partida (1280x810, escalable); vuelve al inicio cuando la partida se corta.
- `ServidorUDP.getUltimoEstado()`: la ventana lee la última foto del juego sin tocar los datos del servidor.

## [1.0.0] - 2026-11-22
### Añadido
- Servidor UDP autoritativo (puerto 6767): descubrimiento por broadcast, conexión de 2 jugadores, estado a 30 Hz, latidos, desconexión por tiempo, cierre ordenado.
- Física con Box2D a paso fijo de 1/60 s, solo en el servidor. El mapa (capa `Colisiones` de Aula.tmx) viene dentro del jar.
- Reglas completas del juego: golpes con combo de 3 etapas e inmunidad, botiquín y pistola, modo disparo (mira de -30 a 90 grados, balas en diagonal, 10 de daño, sin aturdir), rondas al mejor de 5, revancha.
- Ventana del servidor con el logo grande, "SERVIDOR CONECTADO", el puerto y los jugadores conectados (n/2).
- Proyecto multimódulo como el cliente: `core` (lógica), `lwjgl3` (lanzador y jar) y `assets` (mapa y logo).
- Jar ejecutable con todas las librerías (`gradlew lwjgl3:jar`).
