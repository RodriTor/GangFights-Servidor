package Servidor;


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

    public static final int TIMEOUT_MS = 3000;

    public static final int JUGADORES_POR_PARTIDA = 2;
}
