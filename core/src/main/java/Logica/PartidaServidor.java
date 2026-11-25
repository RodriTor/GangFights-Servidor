package Logica;

public class PartidaServidor {

    public static final int RONDAS_PARA_GANAR = 3;

    private static final float DURACION_CUENTA = 3f;
    private static final float DURACION_RONDA_TERMINADA = 2.5f;

    private final Runnable alIniciarRonda;
    private final int[] rondasGanadas = new int[2];
    private final boolean[] quiereRevancha = new boolean[2];

    private FasePartida fase = FasePartida.ESPERANDO;
    private float tiempoFase = 0f;
    private int ronda = 1;
    private int ganadorRonda = -1;

    public PartidaServidor(Runnable alIniciarRonda) {
        this.alIniciarRonda = alIniciarRonda;
    }

    public void actualizar(float delta, int jugadoresConectados, boolean jugador1Vivo, boolean jugador2Vivo) {
        if (jugadoresConectados < 2) {
            if (fase != FasePartida.ESPERANDO) {
                volverAEsperar();
            }
            return;
        }

        tiempoFase += delta;

        switch (fase) {
            case ESPERANDO:
                empezarPartida();
                break;

            case CUENTA:
                if (tiempoFase >= DURACION_CUENTA) {
                    cambiarFase(FasePartida.EN_CURSO);
                }
                break;

            case EN_CURSO:
                if (!jugador1Vivo) {
                    terminarRonda(1);
                } else if (!jugador2Vivo) {
                    terminarRonda(0);
                }
                break;

            case RONDA_TERMINADA:
                if (tiempoFase >= DURACION_RONDA_TERMINADA) {
                    siguienteRonda();
                }
                break;

            case FINALIZADA:
                if (quiereRevancha[0] && quiereRevancha[1]) {
                    empezarPartida();
                }
                break;
        }
    }

    /* Un jugador pide la revancha  */
    public void pedirRevancha(int indice) {
        if (fase == FasePartida.FINALIZADA) {
            quiereRevancha[indice] = true;
        }
    }

    private void terminarRonda(int indiceGanador) {
        ganadorRonda = indiceGanador;
        rondasGanadas[indiceGanador]++;

        if (rondasGanadas[indiceGanador] >= RONDAS_PARA_GANAR) {
            cambiarFase(FasePartida.FINALIZADA);
        } else {
            cambiarFase(FasePartida.RONDA_TERMINADA);
        }
    }

    private void siguienteRonda() {
        ronda++;
        ganadorRonda = -1;
        alIniciarRonda.run();
        cambiarFase(FasePartida.CUENTA);
    }

    /* Partida nueva sirve tanto para el comienzo*/
    private void empezarPartida() {
        reiniciarDatos();
        alIniciarRonda.run();
        cambiarFase(FasePartida.CUENTA);
    }

    /* Se fue un jugador todo vuelve a empezar y se espera a otro */
    private void volverAEsperar() {
        reiniciarDatos();
        alIniciarRonda.run();
        cambiarFase(FasePartida.ESPERANDO);
    }

    private void reiniciarDatos() {
        rondasGanadas[0] = 0;
        rondasGanadas[1] = 0;
        quiereRevancha[0] = false;
        quiereRevancha[1] = false;
        ronda = 1;
        ganadorRonda = -1;
    }

    private void cambiarFase(FasePartida nueva) {
        fase = nueva;
        tiempoFase = 0f;
    }

    /* Durante la partida los jugadores solo estan quietos en la espera y en la cuenta regresiva. */
    public boolean puedenMoverse() {
        return fase == FasePartida.EN_CURSO
            || fase == FasePartida.RONDA_TERMINADA
            || fase == FasePartida.FINALIZADA;
    }

    public boolean hayCombate() {
        return fase == FasePartida.EN_CURSO;
    }

    public FasePartida getFase() {
        return fase;
    }

    public int getRonda() {
        return ronda;
    }

    public int getGanadorRonda() {
        return ganadorRonda;
    }

    public int getRondasGanadas(int indice) {
        return rondasGanadas[indice];
    }

    /* Segundos que faltan en la cuenta regresiva */
    public int getSegundosCuenta() {
        if (fase != FasePartida.CUENTA) return 0;
        return Math.max(0, (int) Math.ceil(DURACION_CUENTA - tiempoFase));
    }

    /** Bit 0 = jugador 1, bit 1 = jugador 2. */
    public int getMascaraRevanchas() {
        int resultado = 0;
        if (quiereRevancha[0]) {
            resultado += 1;
        }
        if (quiereRevancha[1]) {
            resultado += 2;
        }
        return resultado;
    }
}
