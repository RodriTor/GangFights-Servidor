package Servidor;

/**
 * Mensajes y constantes del protocolo UDP de GangFights.
 *
 * Todos los mensajes son texto con campos separados por ";".
 * El cliente tiene una copia igual de esta clase en Red/Protocolo.java: si se cambia una, se cambia la otra.
 *
 *  Cliente -> Servidor                          Servidor -> Cliente
 *  BUSCAR   (por broadcast)                     SERVIDOR;jugadoresConectados;2
 *  CONECTAR                                     JUGADOR;numero     o     LLENO
 *  ENTRADA;seq;izq;der;agachar;arriba;contSalto;contGolpe;contApuntar;contDisparo
 *                                               ESTADO;...  (30 veces por segundo)
 *  LATIDO   (cada 1 s, para avisar que sigue vivo)      CERRADO     (el servidor se apaga)
 *  REVANCHA
 *  SALIR
 */
public final class Protocolo {

    private Protocolo() { }

    public static final int PUERTO = 6767;
    public static final String SEPARADOR = ";";

    // Cliente -> servidor
    public static final String BUSCAR = "BUSCAR";
    public static final String CONECTAR = "CONECTAR";
    public static final String ENTRADA = "ENTRADA";
    public static final String LATIDO = "LATIDO";
    public static final String REVANCHA = "REVANCHA";
    public static final String SALIR = "SALIR";

    // Servidor -> cliente
    public static final String SERVIDOR = "SERVIDOR";
    public static final String JUGADOR = "JUGADOR";
    public static final String LLENO = "LLENO";
    public static final String ESTADO = "ESTADO";
    public static final String CERRADO = "CERRADO";

    public static final int TAMANIO_PAQUETE = 2048;

    /** Si pasan tantos milisegundos sin recibir nada, se considera que el otro lado se desconecto. */
    public static final int TIMEOUT_MS = 3000;

    public static final int JUGADORES_POR_PARTIDA = 2;
}
