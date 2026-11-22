# GangFights - Servidor

Servidor del juego **GangFights** (proyecto final de *Programación sobre redes*, E.T. N°35 "Latzina").
Equipo: Lafflitto / Torrez / Sivale. Tutor: Prof. Pablo Jasinski.

El cliente está en el repositorio **GangFights-Cliente**.

## Qué hace

- Es el **dueño de la partida**: corre la física (Box2D), los golpes, la pistola y las balas, la vida, las rondas y la revancha.
- Los clientes **solo mandan las teclas** y **dibujan** lo que el servidor les dice (arquitectura cliente-servidor autoritativa).
- No tiene ventana: es un programa de consola. De LibGDX usa únicamente Box2D.
- Atiende **una partida de 2 jugadores**. Un tercer cliente recibe `LLENO`.

## Cómo se ejecuta

Requisitos: **Java 21**.

```
java -jar GangFights-Servidor-1.0.0.jar
```

Se cierra escribiendo `salir` y ENTER (los clientes reciben el aviso `CERRADO`).

### Generar el .jar

```
gradlew jar
```

Queda en `build/libs/GangFights-Servidor-1.0.0.jar` (trae todas las librerías y el mapa adentro).

### Ejecutar desde IntelliJ / Eclipse

Clase principal: `Servidor.ServidorPrincipal`.

## Protocolo (UDP, puerto 5000)

Mensajes de texto con campos separados por `;`.

| Cliente -> Servidor | Para qué |
|---|---|
| `BUSCAR` (broadcast) | Descubrir el servidor sin saber su IP |
| `CONECTAR` | Entrar a la partida |
| `ENTRADA;seq;izq;der;agachar;arriba;contSalto;contGolpe;contApuntar;contDisparo` | Teclas apretadas (60 veces por segundo) |
| `LATIDO` | "Sigo acá" (1 por segundo) |
| `REVANCHA` | Pedir otra partida |
| `SALIR` | Me voy |

| Servidor -> Cliente | Para qué |
|---|---|
| `SERVIDOR;conectados;2` | Respuesta al `BUSCAR` |
| `JUGADOR;n` / `LLENO` | Respuesta al `CONECTAR` |
| `ESTADO;...` | "Foto" completa del juego (30 veces por segundo) |
| `CERRADO` | El servidor se apaga |

Formato de `ESTADO` (ver `ServidorUDP.construirEstado`):

```
ESTADO;seq;fase;ronda;ganadorRonda;rondas1;rondas2;conectados;revanchas;cuenta;jugador1;jugador2;botiquin;pistola;balas
jugador = x,y,vida,animacion,mirandoDerecha,pistola,apuntando,anguloMira,rojo
```

### Cómo se manejan los problemas de red (UDP)

- **Mensajes perdidos:** el cliente manda su estado completo todo el tiempo y los saltos/golpes/disparos viajan como *contadores que solo suben*; el servidor manda fotos completas, no cambios.
- **Mensajes desordenados o repetidos:** `ENTRADA` y `ESTADO` llevan número de secuencia; lo viejo se descarta.
- **Retardos:** el servidor no espera a nadie, avanza a paso fijo de 1/60 s; el cliente suaviza lo que recibe.
- **Puerto ocupado:** se avisa por consola y el programa termina sin colgarse.
- **Cliente que se cae:** 3 s sin mensajes (ni `LATIDO`) = desconectado; la partida vuelve a *esperando rival*.
- **Servidor que se cierra:** avisa `CERRADO` (3 veces) y libera socket e hilos.

## Estructura

```
src/main/java
  Servidor/   ServidorPrincipal (main), ServidorUDP (sockets + 2 hilos), Protocolo
  Logica/     PartidaServidor (rondas, fases, revancha), ReaccionGolpe (combo), FasePartida, AnimacionJugador
  Mundo/      MundoServidor (Box2D), JugadorServidor, ItemsServidor, BalasServidor, CargadorColisiones
src/main/resources/Aula.tmx   mapa (capa "Colisiones")
```

Hilos: un **hilo receptor** (solo lee datagramas y los deja en una cola) y un **hilo del juego** (60 veces por segundo atiende la cola, avanza la física y manda el estado). Solo el hilo del juego toca Box2D, por eso no hace falta `synchronized`.

## Firewall de Windows

La primera vez, Windows pregunta si permitir `java.exe`: elegir **Redes privadas -> Permitir acceso**.
Si no aparece el cartel, pedir la excepción para **UDP 5000 entrante**. No hace falta (ni conviene) apagar el firewall.
