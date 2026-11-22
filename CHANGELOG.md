# Registro de cambios - Servidor

## [1.0.0] - 2026-11-22
### Añadido
- Servidor UDP autoritativo (puerto 5000): descubrimiento por broadcast, conexión de 2 jugadores, estado a 30 Hz, latidos, desconexión por tiempo, cierre ordenado.
- Física con Box2D a paso fijo de 1/60 s, solo en el servidor. El mapa (capa `Colisiones` de Aula.tmx) viene dentro del jar.
- Reglas completas del juego: golpes con combo de 3 etapas e inmunidad, botiquín y pistola, modo disparo (mira de -30 a 90 grados, balas en diagonal, 10 de daño, sin aturdir), rondas al mejor de 5, revancha.
- Jar ejecutable con todas las librerías (`gradlew jar`).
