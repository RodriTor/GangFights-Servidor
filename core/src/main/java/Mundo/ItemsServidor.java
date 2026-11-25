package Mundo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ItemsServidor {

    // Tamanos en pixeles ya con la escala x2 (imagenes de 21x13 y 11x9 sin borde transparente)
    private static final float ANCHO_BOTIQUIN = 42f;
    private static final float ALTO_BOTIQUIN = 26f;
    private static final float ANCHO_PISTOLA = 22f;
    private static final float ALTO_PISTOLA = 18f;

    private static final int CURACION = 30;

    // Espacio libre que necesita un item para aparecer
    private static final float ANCHO_LIBRE = 48f;
    private static final float ALTO_LIBRE = 40f;
    private static final float PASO_ENTRE_PUNTOS = 24f;
    private static final float ALTURA_MAXIMA = 224f;
    private static final float SEPARACION_ENTRE_ITEMS = 80f;

    private static final float PRIMERA_ESPERA_MIN = 4f;
    private static final float PRIMERA_ESPERA_MAX = 8f;
    private static final float ESPERA_MIN = 12f;
    private static final float ESPERA_MAX = 22f;

    private final List<float[]> obstaculos;
    private final List<float[]> puntos = new ArrayList<>();
    private final Random azar = new Random();

    private float[] botiquin;
    private float[] pistola;
    private float esperaBotiquin;
    private float esperaPistola;

    public ItemsServidor(List<float[]> obstaculos) {
        this.obstaculos = obstaculos;
        buscarPuntosLibres();
        reiniciar();
    }

    public void reiniciar() {
        botiquin = null;
        pistola = null;
        esperaBotiquin = numeroEntre(PRIMERA_ESPERA_MIN, PRIMERA_ESPERA_MAX);
        esperaPistola = numeroEntre(PRIMERA_ESPERA_MIN, PRIMERA_ESPERA_MAX);
    }

    public void actualizar(float delta, JugadorServidor jugador1, JugadorServidor jugador2) {
        if (botiquin == null) {
            esperaBotiquin -= delta;
            if (esperaBotiquin <= 0f) {
                botiquin = elegirPunto(pistola);
                if (botiquin == null) esperaBotiquin = 1f;
            }
        } else if (recoger(botiquin, ANCHO_BOTIQUIN, ALTO_BOTIQUIN, true, jugador1, jugador2)) {
            botiquin = null;
            esperaBotiquin = numeroEntre(ESPERA_MIN, ESPERA_MAX);
        }

        if (pistola == null) {
            esperaPistola -= delta;
            if (esperaPistola <= 0f) {
                pistola = elegirPunto(botiquin);
                if (pistola == null) esperaPistola = 1f;
            }
        } else if (recoger(pistola, ANCHO_PISTOLA, ALTO_PISTOLA, false, jugador1, jugador2)) {
            pistola = null;
            esperaPistola = numeroEntre(ESPERA_MIN, ESPERA_MAX);
        }
    }

    private boolean recoger(float[] item, float ancho, float alto, boolean esBotiquin,
                            JugadorServidor jugador1, JugadorServidor jugador2) {
        return intentarRecoger(item, ancho, alto, esBotiquin, jugador1)
            || intentarRecoger(item, ancho, alto, esBotiquin, jugador2);
    }

    private boolean intentarRecoger(float[] item, float ancho, float alto, boolean esBotiquin,
                                    JugadorServidor jugador) {
        if (!jugador.isActivo() || !jugador.estaAgachado()) return false;

        float centroY = item[1] + alto / 2f;
        boolean toca = Math.abs(item[0] - jugador.getX()) < (ancho + JugadorServidor.ANCHO) / 2f
            && Math.abs(centroY - jugador.getY()) < (alto + JugadorServidor.ALTO) / 2f;
        if (!toca) return false;

        if (esBotiquin) {
            if (jugador.getVida() >= JugadorServidor.VIDA_MAXIMA) return false;
            jugador.curar(CURACION);
        } else {
            if (jugador.tienePistola()) return false;
            jugador.equiparPistola();
        }
        return true;
    }

    private void buscarPuntosLibres() {
        for (float[] base : obstaculos) {
            float superficie = base[1] + base[3];
            if (superficie > ALTURA_MAXIMA) continue;

            for (float x = base[0] + ANCHO_LIBRE / 2f; x <= base[0] + base[2] - ANCHO_LIBRE / 2f; x += PASO_ENTRE_PUNTOS) {
                if (hayEspacioLibre(x - ANCHO_LIBRE / 2f, superficie)) {
                    puntos.add(new float[] {x, superficie});
                }
            }
        }
    }

    private boolean hayEspacioLibre(float x, float y) {
        float cajaY = y + 0.5f;
        for (float[] o : obstaculos) {
            boolean seSuperponen = o[0] < x + ANCHO_LIBRE && x < o[0] + o[2]
                && o[1] < cajaY + ALTO_LIBRE && cajaY < o[1] + o[3];
            if (seSuperponen) return false;
        }
        return true;
    }

    private float[] elegirPunto(float[] otroItem) {
        if (puntos.isEmpty()) return null;

        float[] punto = puntos.get(azar.nextInt(puntos.size()));
        for (int intento = 0; intento < 5 && estaCerca(punto, otroItem); intento++) {
            punto = puntos.get(azar.nextInt(puntos.size()));
        }
        return new float[] {punto[0], punto[1]};
    }

    private boolean estaCerca(float[] punto, float[] item) {
        return item != null && Math.abs(item[0] - punto[0]) < SEPARACION_ENTRE_ITEMS;
    }

    private float numeroEntre(float minimo, float maximo) {
        return minimo + azar.nextFloat() * (maximo - minimo);
    }

    /** "x,base" para el mensaje de estado, o "-" si no hay item. */
    public String botiquinComoTexto() {
        return comoTexto(botiquin);
    }

    public String pistolaComoTexto() {
        return comoTexto(pistola);
    }

    private String comoTexto(float[] item) {
        if (item == null) return "-";
        return Math.round(item[0]) + "," + Math.round(item[1]);
    }
}
