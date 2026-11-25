package Mundo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BalasServidor {

    private static final float ANCHO = 10f;
    private static final float ALTO = 4f;
    private static final float VELOCIDAD = 900f;
    private static final float PASO_MAXIMO = 4f;
    private static final float LIMITE_X = 1280f;
    private static final float LIMITE_Y = 720f;
    private static final int MAXIMO_EN_ESTADO = 8;

    private static class Bala {
        final JugadorServidor tirador;
        final float dirX;
        final float dirY;
        float x;
        float y;

        Bala(JugadorServidor tirador, float x, float y, float destinoX, float destinoY) {
            this.tirador = tirador;
            this.x = x;
            this.y = y;

            float dx = destinoX - x;
            float dy = destinoY - y;
            float largo = (float) Math.sqrt(dx * dx + dy * dy);
            if (largo < 0.001f) {
                dx = 1f;
                dy = 0f;
                largo = 1f;
            }
            dirX = dx / largo;
            dirY = dy / largo;
        }

        int getAngulo() {
            return Math.round((float) Math.toDegrees(Math.atan2(dirY, dirX)));
        }
    }

    private final List<float[]> obstaculos;
    private final List<Bala> balas = new ArrayList<>();

    public BalasServidor(List<float[]> obstaculos) {
        this.obstaculos = obstaculos;
    }

    /* La bala sale de la boca del arma del tirador y viaja hacia su mira. */
    public void disparar(JugadorServidor tirador) {
        balas.add(new Bala(tirador, tirador.getXBoca(), tirador.getYBoca(),
            tirador.getXMira(), tirador.getYMira()));
    }

    public void reiniciar() {
        balas.clear();
    }

    public void actualizar(float delta, JugadorServidor jugador1, JugadorServidor jugador2) {
        Iterator<Bala> it = balas.iterator();

        while (it.hasNext()) {
            Bala bala = it.next();
            JugadorServidor rival = (bala.tirador == jugador1) ? jugador2 : jugador1;

            if (avanzar(bala, delta, rival)) {
                it.remove();
            }
        }
    }

    private boolean avanzar(Bala bala, float delta, JugadorServidor rival) {
        float restante = VELOCIDAD * delta;

        while (restante > 0f) {
            float paso = Math.min(PASO_MAXIMO, restante);
            bala.x += bala.dirX * paso;
            bala.y += bala.dirY * paso;
            restante -= paso;

            if (bala.x < 0f || bala.x > LIMITE_X || bala.y < 0f || bala.y > LIMITE_Y) return true;
            if (chocaConObstaculo(bala)) return true;

            if (tocaAlJugador(bala, rival)) {
                rival.recibirDisparo();
                return true;
            }
        }
        return false;
    }

    private boolean chocaConObstaculo(Bala bala) {
        float izquierda = bala.x - ANCHO / 2f;
        float abajo = bala.y - ALTO / 2f;

        for (float[] o : obstaculos) {
            boolean seSuperponen = o[0] < izquierda + ANCHO && izquierda < o[0] + o[2]
                && o[1] < abajo + ALTO && abajo < o[1] + o[3];
            if (seSuperponen) return true;
        }
        return false;
    }

    private boolean tocaAlJugador(Bala bala, JugadorServidor jugador) {
        if (!jugador.isActivo()) return false;

        boolean enX = Math.abs(bala.x - jugador.getX()) < (ANCHO + JugadorServidor.ANCHO) / 2f;
        boolean enY = Math.abs(bala.y - jugador.getY()) < (ALTO + JugadorServidor.ALTO) / 2f;
        return enX && enY;
    }

    public String comoTexto() {
        if (balas.isEmpty()) return "-";

        StringBuilder texto = new StringBuilder();
        int cantidad = Math.min(balas.size(), MAXIMO_EN_ESTADO);

        for (int i = 0; i < cantidad; i++) {
            Bala b = balas.get(i);
            if (i > 0) texto.append('/');
            texto.append(Math.round(b.x)).append(',').append(Math.round(b.y)).append(',').append(b.getAngulo());
        }
        return texto.toString();
    }

    public int cantidad() {
        return balas.size();
    }
}
