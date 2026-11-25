package Mundo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;

import java.util.List;


public class MundoServidor {

    public static final float PIXELES_POR_METRO = 10f;
    public static final short CATEGORIA_PLATAFORMA = 0x0001;
    public static final short CATEGORIA_JUGADOR = 0x0002;

    private static final float GRAVEDAD = -40f;
    private static final float PASO_FISICA = 1f / 60f;
    private static final int ITERACIONES_VELOCIDAD = 6;
    private static final int ITERACIONES_POSICION = 2;

    private static final float ANCHO_MAPA = 1280f;
    private static final float ALTO_PAREDES = 720f;
    private static final float GROSOR_PARED = 20f;

    private final World mundo;
    private final JugadorServidor[] jugadores = new JugadorServidor[2];
    private final ItemsServidor items;
    private final BalasServidor balas;

    public MundoServidor() {
        mundo = new World(new Vector2(0, GRAVEDAD), true);
        mundo.setContactListener(new DetectorSuelo());

        List<float[]> plataformas = CargadorColisiones.cargar();
        for (float[] p : plataformas) {
            crearPlataforma(p[0], p[1], p[2], p[3]);
        }
        crearPlataforma(-GROSOR_PARED, 0, GROSOR_PARED, ALTO_PAREDES);
        crearPlataforma(ANCHO_MAPA, 0, GROSOR_PARED, ALTO_PAREDES);

        jugadores[0] = new JugadorServidor(mundo, 230, 300, true);
        jugadores[1] = new JugadorServidor(mundo, 1050, 300, false);
        items = new ItemsServidor(plataformas);
        balas = new BalasServidor(plataformas);
    }

    private void crearPlataforma(float x, float y, float ancho, float alto) {
        BodyDef cuerpoDef = new BodyDef();
        cuerpoDef.type = BodyDef.BodyType.StaticBody;
        cuerpoDef.position.set((x + ancho / 2f) / PIXELES_POR_METRO, (y + alto / 2f) / PIXELES_POR_METRO);

        Body cuerpo = mundo.createBody(cuerpoDef);

        PolygonShape forma = new PolygonShape();
        forma.setAsBox((ancho / 2f) / PIXELES_POR_METRO, (alto / 2f) / PIXELES_POR_METRO);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = forma;
        fixtureDef.density = 0f;
        fixtureDef.filter.categoryBits = CATEGORIA_PLATAFORMA;
        fixtureDef.filter.maskBits = -1;

        cuerpo.createFixture(fixtureDef);
        forma.dispose();
    }

    /** Empieza una ronda: jugadores en su lugar, vida llena e items nuevos. */
    public void reiniciarRonda() {
        items.reiniciar();
        balas.reiniciar();
        jugadores[0].reaparecer();
        jugadores[1].reaparecer();
    }

    public void actualizar(boolean puedenMoverse, boolean hayCombate) {
        jugadores[0].actualizar(PASO_FISICA, puedenMoverse);
        jugadores[1].actualizar(PASO_FISICA, puedenMoverse);

        if (hayCombate) {
            jugadores[0].comprobarAtaque(jugadores[1]);
            jugadores[1].comprobarAtaque(jugadores[0]);
            items.actualizar(PASO_FISICA, jugadores[0], jugadores[1]);

            if (jugadores[0].consumirDisparo()) balas.disparar(jugadores[0]);
            if (jugadores[1].consumirDisparo()) balas.disparar(jugadores[1]);
            balas.actualizar(PASO_FISICA, jugadores[0], jugadores[1]);
        } else {
            // Fuera del combate no hay disparos ni balas en el aire
            jugadores[0].consumirDisparo();
            jugadores[1].consumirDisparo();
            balas.reiniciar();
        }

        mundo.step(PASO_FISICA, ITERACIONES_VELOCIDAD, ITERACIONES_POSICION);
    }

    public JugadorServidor getJugador(int indice) {
        return jugadores[indice];
    }

    public ItemsServidor getItems() {
        return items;
    }

    public BalasServidor getBalas() {
        return balas;
    }

    public void dispose() {
        mundo.dispose();
    }


    private static class DetectorSuelo implements ContactListener {

        @Override
        public void beginContact(Contact contacto) {
            procesar(contacto, true);
        }

        @Override
        public void endContact(Contact contacto) {
            procesar(contacto, false);
        }

        private void procesar(Contact contacto, boolean inicio) {
            Fixture a = contacto.getFixtureA();
            Fixture b = contacto.getFixtureB();
            notificar(a, b, inicio);
            notificar(b, a, inicio);
        }

        private void notificar(Fixture posibleSensor, Fixture otro, boolean inicio) {
            if (posibleSensor.isSensor() && !otro.isSensor()
                && posibleSensor.getUserData() instanceof JugadorServidor) {

                JugadorServidor jugador = (JugadorServidor) posibleSensor.getUserData();
                if (inicio) {
                    jugador.sumarContactoSuelo();
                } else {
                    jugador.restarContactoSuelo();
                }
            }
        }

        @Override
        public void preSolve(Contact contacto, Manifold viejo) { }

        @Override
        public void postSolve(Contact contacto, ContactImpulse impulso) { }
    }
}
