package Logica;

public class ReaccionGolpe {

    public static final int ETAPAS = 3;

    private static final float VENTANA_COMBO = 1.3f;
    private static final float DURACION_ULTIMA_ETAPA = 0.5f;
    private static final float INMUNIDAD_FIN_COMBO = 1.2f;
    private static final float INMUNIDAD_ESCAPE = 1.0f;

    private int etapa = 0;
    private float tiempoAturdido = 0f;
    private float tiempoInmune = 0f;

    public boolean recibirGolpe() {
        if (tiempoInmune > 0f) return false;
        etapa = Math.min(etapa + 1, ETAPAS);
        tiempoAturdido = (etapa == ETAPAS) ? DURACION_ULTIMA_ETAPA : VENTANA_COMBO;
        return true;
    }

    /* true si estaba aturdido y logro salirse del combo. */
    public boolean escapar() {
        if (!estaAturdido()) return false;

        liberar(INMUNIDAD_ESCAPE);
        return true;
    }

    public void actualizar(float delta) {
        if (tiempoInmune > 0f) {
            tiempoInmune = Math.max(0f, tiempoInmune - delta);
        }
        if (tiempoAturdido > 0f) {
            tiempoAturdido -= delta;
            if (tiempoAturdido <= 0f) {
                boolean combo3 = (etapa == ETAPAS);
                liberar(combo3 ? INMUNIDAD_FIN_COMBO : 0f);
            }
        }
    }

    public void reiniciar() {
        liberar(0f);
    }

    private void liberar(float inmunidad) {
        etapa = 0;
        tiempoAturdido = 0f;
        tiempoInmune = inmunidad;
    }

    public boolean estaAturdido() {
        return tiempoAturdido > 0f;
    }

    public boolean esInmune() {
        return tiempoInmune > 0f;
    }

    /* 0 = libre, 1..3 = imagen de "recibir" que se muestra. */
    public int getEtapa() {
        return etapa;
    }
}
