# GangFights - Servidor

Servidor del juego **GangFights** (proyecto final de *Programación sobre redes*, E.T. N°35 "Latzina").
Equipo: Lafflitto / Torrez / Sivale. Tutor: Prof. Pablo Jasinski.

El cliente está en el repositorio **GangFights-Cliente**.

## Qué hace

- Es el **dueño de la partida**: corre la física (Box2D), los golpes, la pistola y las balas, la vida, las rondas y la revancha.
- Los clientes **solo mandan las teclas** y **dibujan** lo que el servidor les dice (arquitectura cliente-servidor autoritativa).
- Abre una **ventana** (Swing) con el logo, **SERVIDOR CONECTADO**, el puerto UDP y los jugadores conectados (**0/2**, **1/2**, **2/2**) con el estado "esperando jugadores" / "partida en curso". Al cerrar la ventana se apaga el servidor. También sigue mostrando mensajes en la consola. De LibGDX usa únicamente Box2D (sin dibujo).
- Atiende **una partida de 2 jugadores**. Un tercer cliente recibe `LLENO`.

## Cómo se ejecuta

Requisitos: **Java 21**.

```
java -jar GangFights-Servidor-1.0.0.jar
```

Se cierra con la **X de la ventana** o escribiendo `salir` y ENTER en la consola (los clientes reciben el aviso `CERRADO`).

### Generar el .jar

```
gradlew lwjgl3:jar
```

Queda en `lwjgl3/build/libs/GangFights-Servidor-1.0.0.jar` (trae todas las librerías, el mapa y el logo adentro).

### Ejecutar desde IntelliJ / Eclipse

Clase principal: `Servidor.ServidorPrincipal` (módulo `lwjgl3`, carpeta de trabajo `assets`).

## Protocolo (UDP, puerto 6767)

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
- **Puerto ocupado:** se avisa por consola y en un cartel, y el programa termina sin colgarse.
- **Cliente que se cae:** 3 s sin mensajes (ni `LATIDO`) = desconectado; la partida vuelve a *esperando rival*.
- **Servidor que se cierra:** avisa `CERRADO` (3 veces) y libera socket e hilos.

## Estructura

```
core/src/main/java          (toda la logica del servidor)
  Servidor/   ServidorUDP (sockets + 2 hilos), VentanaServidor (ventana), Protocolo
  Logica/     PartidaServidor (rondas, fases, revancha), ReaccionGolpe (combo), FasePartida, AnimacionJugador
  Mundo/      MundoServidor (Box2D), JugadorServidor, ItemsServidor, BalasServidor, CargadorColisiones
lwjgl3/src/main/java        (lanzador de escritorio)
  Servidor/   ServidorPrincipal (main)
assets/       Aula.tmx (mapa, capa "Colisiones") y LogoPrincipal.png (logo de la ventana)
```

Hilos: un **hilo receptor** (solo lee datagramas y los deja en una cola) y un **hilo del juego** (60 veces por segundo atiende la cola, avanza la física y manda el estado). Solo el hilo del juego toca Box2D, por eso no hace falta `synchronized`.

## Firewall de Windows

La primera vez, Windows pregunta si permitir `java.exe`: elegir **Redes privadas -> Permitir acceso**.
Si no aparece el cartel, pedir la excepción para **UDP 6767 entrante**. No hace falta (ni conviene) apagar el firewall.
