package Servidor;

/**
 * La "foto" del juego (texto ESTADO) convertida en datos, para que la ventana del servidor pueda dibujar la partida.
 * Es el mismo formato que usa el cliente (ver ServidorUDP.construirEstado). No se modifica una vez creada.
 */
final class EstadoVista {

    // Mismo orden que FasePartida y AnimacionJugador (el ordinal es el que viaja en el texto)
    static final int FASE_ESPERANDO = 0;
    static final int FASE_CUENTA = 1;
    static final int FASE_EN_CURSO = 2;
    static final int FASE_RONDA_TERMINADA = 3;
    static final int FASE_FINALIZADA = 4;

    static final int ANIM_QUIETO = 0;
    static final int ANIM_CORRER = 1;
    static final int ANIM_SALTO = 2;
    static final int ANIM_AGACHADO = 3;
    static final int ANIM_GOLPE = 4;
    static final int ANIM_RECIBIR_1 = 5;
    static final int ANIM_RECIBIR_2 = 6;
    static final int ANIM_RECIBIR_3 = 7;
    static final int ANIM_MUERTO = 8;
    static final int ANIM_DISPARO = 9;

    static final class DatosJugador {
        final int x, y, vida, animacion, anguloMira;
        final boolean mirandoDerecha, pistola, apuntando, rojo;

        DatosJugador(String texto) {
            String[] c = texto.split(",");
            x = Integer.parseInt(c[0]);
            y = Integer.parseInt(c[1]);
            vida = Integer.parseInt(c[2]);
            animacion = Integer.parseInt(c[3]);
            mirandoDerecha = c[4].equals("1");
            pistola = c[5].equals("1");
            apuntando = c[6].equals("1");
            anguloMira = Integer.parseInt(c[7]);
            rojo = c[8].equals("1");
        }
    }

    final int secuencia, fase, ronda, ganadorRonda, segundosCuenta;
    final int[] rondasGanadas = new int[2];
    final DatosJugador[] jugadores = new DatosJugador[2];
    /** {x, base} o null si no hay item. */
    final int[] botiquin, pistola;
    /** Cada bala es {x, y, angulo}. */
    final int[][] balas;
    private final int revanchas;

    private EstadoVista(String[] t) {
        secuencia = Integer.parseInt(t[1]);
        fase = Integer.parseInt(t[2]);
        ronda = Integer.parseInt(t[3]);
        ganadorRonda = Integer.parseInt(t[4]);
        rondasGanadas[0] = Integer.parseInt(t[5]);
        rondasGanadas[1] = Integer.parseInt(t[6]);
        // t[7] = conectados (la ventana usa el contador de ServidorUDP)
        revanchas = Integer.parseInt(t[8]);
        segundosCuenta = Integer.parseInt(t[9]);
        jugadores[0] = new DatosJugador(t[10]);
        jugadores[1] = new DatosJugador(t[11]);
        botiquin = leerItem(t[12]);
        pistola = leerItem(t[13]);
        balas = leerBalas(t[14]);
    }

    private static int[] leerItem(String texto) {
        if (texto.equals("-")) return null;
        String[] c = texto.split(",");
        return new int[] {Integer.parseInt(c[0]), Integer.parseInt(c[1])};
    }

    private static int[][] leerBalas(String texto) {
        if (texto.equals("-")) return new int[0][];
        String[] cada = texto.split("/");
        int[][] balas = new int[cada.length][];
        for (int i = 0; i < cada.length; i++) {
            String[] c = cada[i].split(",");
            balas[i] = new int[] {Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])};
        }
        return balas;
    }

    /** @return la foto, o null si el texto es null o esta mal formado. */
    static EstadoVista leer(String mensaje) {
        if (mensaje == null) return null;
        try {
            String[] t = mensaje.split(Protocolo.SEPARADOR);
            if (t.length < 15) return null;
            return new EstadoVista(t);
        } catch (RuntimeException e) {
            return null;
        }
    }

    int cantidadRevanchas() {
        return Integer.bitCount(revanchas & 3);
    }
}
