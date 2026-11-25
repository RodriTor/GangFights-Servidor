package Mundo;

import Logica.AnimacionJugador;
import Logica.ReaccionGolpe;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;

import java.util.concurrent.ThreadLocalRandom;


public class JugadorServidor {

    public static final int VIDA_MAXIMA = 100;
    public static final float ANCHO = 30f;
    public static final float ALTO = 60f;

    private static final float VELOCIDAD = 13f;
    private static final float VELOCIDAD_SALTO = 21f;
    private static final int DANIO_GOLPE = 10;
    private static final int DANIO_DISPARO = 10;
    private static final float DURACION_ROJO_DISPARO = 0.2f; // la bala solo lo pone rojo un instante
    private static final float PROBABILIDAD_ESCAPE = 0.3f;

    /** La animacion de golpe tiene 2 imagenes de 0.15 s cada una. */
    private static final float DURACION_GOLPE = 0.3f;
    private static final float RANGO_GOLPE_X = 3.0f;
    private static final float RANGO_GOLPE_Y = 2.0f;
    private static final float MITAD_GROSOR_SENSOR = 0.2f;

    // Modo disparo: la mira gira alrededor de la boca del arma (0 grados = recto, 90 = vertical hacia arriba)
    public static final float DISTANCIA_MIRA = 70f;
    private static final float VELOCIDAD_MIRA = 90f; // grados por segundo
    private static final float ALTURA_BOCA = 8f;
    private static final float ANGULO_MIRA_INICIAL = 0f;
    private static final float ANGULO_MIRA_MINIMO = -30f;
    private static final float ANGULO_MIRA_MAXIMO = 90f;
    private static final float TIEMPO_ENTRE_DISPAROS = 0.5f;
    private static final float SALIDA_BALA_X = 22f;

    private final World mundo;
    private final float spawnX;
    private final float spawnY;
    private final boolean mirandoDerechaInicial;
    private final ReaccionGolpe reaccion = new ReaccionGolpe();

    private Body cuerpo;
    private int contactosSuelo = 0;
    private int vida = VIDA_MAXIMA;
    private float tiempoGolpe = 0f;

    private boolean activo = true;
    private boolean estaGolpeando;
    private boolean golpeConectado;
    private boolean estaAgachado;
    private boolean estaCorriendo;
    private boolean estaSaltando;
    private boolean mirandoDerecha;
    private boolean tienePistola;
    private boolean apuntando;
    private float anguloMira = ANGULO_MIRA_INICIAL;
    private float tiempoEntreDisparos = 0f;
    private float tiempoRojo = 0f;
    private boolean disparoRealizado;

    // Lo que mando el cliente
    private boolean izquierda;
    private boolean derecha;
    private boolean agachar;
    private boolean arriba;
    private boolean saltoPedido;
    private boolean golpePedido;
    private boolean apuntarPedido;
    private boolean disparoPedido;

    // Para detectar el momento en que se aprieta izquierda o derecha
    private boolean izquierdaAnterior;
    private boolean derechaAnterior;

    public JugadorServidor(World mundo, float x, float y, boolean mirandoDerecha) {
        this.mundo = mundo;
        this.spawnX = x;
        this.spawnY = y;
        this.mirandoDerechaInicial = mirandoDerecha;
        this.mirandoDerecha = mirandoDerecha;
        crearCuerpo();
    }

    private void crearCuerpo() {
        BodyDef cuerpoDef = new BodyDef();
        cuerpoDef.type = BodyDef.BodyType.DynamicBody;
        cuerpoDef.position.set(spawnX / MundoServidor.PIXELES_POR_METRO, spawnY / MundoServidor.PIXELES_POR_METRO);
        cuerpoDef.fixedRotation = true;
        cuerpo = mundo.createBody(cuerpoDef);

        float mitadAncho = (ANCHO / 2f) / MundoServidor.PIXELES_POR_METRO;
        float mitadAlto = (ALTO / 2f) / MundoServidor.PIXELES_POR_METRO;

        PolygonShape forma = new PolygonShape();
        forma.setAsBox(mitadAncho, mitadAlto);

        FixtureDef fixtureCuerpo = new FixtureDef();
        fixtureCuerpo.shape = forma;
        fixtureCuerpo.density = 1.0f;
        fixtureCuerpo.friction = 0f;
        fixtureCuerpo.filter.categoryBits = MundoServidor.CATEGORIA_JUGADOR;
        fixtureCuerpo.filter.maskBits = MundoServidor.CATEGORIA_PLATAFORMA;
        cuerpo.createFixture(fixtureCuerpo);

        // Sensor debajo de los pies: avisa cuando se toca el suelo
        forma.setAsBox(mitadAncho * 0.8f, MITAD_GROSOR_SENSOR, new Vector2(0, -mitadAlto), 0f);

        FixtureDef fixtureSensor = new FixtureDef();
        fixtureSensor.shape = forma;
        fixtureSensor.isSensor = true;
        fixtureSensor.filter.categoryBits = MundoServidor.CATEGORIA_JUGADOR;
        fixtureSensor.filter.maskBits = MundoServidor.CATEGORIA_PLATAFORMA;
        cuerpo.createFixture(fixtureSensor).setUserData(this);

        forma.dispose();
    }

    public void sumarContactoSuelo() {
        contactosSuelo++;
    }

    public void restarContactoSuelo() {
        contactosSuelo = Math.max(0, contactosSuelo - 1);
    }

    public boolean estaEnSuelo() {
        return contactosSuelo > 0;
    }


    /** Teclas mantenidas, segun el ultimo mensaje ENTRADA del cliente. */
    public void setEntrada(boolean izquierda, boolean derecha, boolean agachar, boolean arriba) {
        this.izquierda = izquierda;
        this.derecha = derecha;
        this.agachar = agachar;
        this.arriba = arriba;
    }

    public void pedirSalto() {
        saltoPedido = true;
    }

    public void pedirGolpe() {
        golpePedido = true;
    }

    public void pedirApuntar() {
        apuntarPedido = true;
    }

    public void pedirDisparo() {
        disparoPedido = true;
    }

    /** Se usa cuando el jugador se desconecta: deja de apretar todo. */
    public void limpiarEntrada() {
        setEntrada(false, false, false, false);
        saltoPedido = false;
        golpePedido = false;
        apuntarPedido = false;
        disparoPedido = false;
    }


    /** Un paso del servidor. Si puedeActuar es false (cuenta regresiva) el jugador se queda quieto. */
    public void actualizar(float delta, boolean puedeActuar) {
        if (activo) {
            reaccion.actualizar(delta);
            tiempoEntreDisparos = Math.max(0f, tiempoEntreDisparos - delta);
            tiempoRojo = Math.max(0f, tiempoRojo - delta);

            if (estaGolpeando) {
                tiempoGolpe += delta;
                if (tiempoGolpe >= DURACION_GOLPE) {
                    estaGolpeando = false;
                }
            }

            estaSaltando = !estaEnSuelo();

            if (puedeActuar) {
                procesarAcciones(delta);
            } else {
                estaCorriendo = false;
                estaAgachado = false;
                cuerpo.setLinearVelocity(0, cuerpo.getLinearVelocity().y);
            }
        }

        // Los "se apreto ahora" valen para un solo paso
        izquierdaAnterior = izquierda;
        derechaAnterior = derecha;
        saltoPedido = false;
        golpePedido = false;
        apuntarPedido = false;
        disparoPedido = false;
    }

    private void procesarAcciones(float delta) {
        float velocidadY = cuerpo.getLinearVelocity().y;

        if (reaccion.estaAturdido()) {
            intentarEscapar();
        }

        if (reaccion.estaAturdido()) {
            estaCorriendo = false;
            estaAgachado = false;
            cuerpo.setLinearVelocity(0, velocidadY);
            return;
        }

        if (estaGolpeando) {
            cuerpo.setLinearVelocity(0, velocidadY);
            return;
        }

        if (apuntando) {
            procesarApuntado(delta, velocidadY);
            return;
        }

        if (apuntarPedido && tienePistola && !estaSaltando) {
            apuntando = true;
            anguloMira = ANGULO_MIRA_INICIAL;
            estaCorriendo = false;
            estaAgachado = false;
            cuerpo.setLinearVelocity(0, velocidadY);
            return;
        }

        if (golpePedido) {
            estaGolpeando = true;
            golpeConectado = false;
            estaCorriendo = false;
            tiempoGolpe = 0f;
            cuerpo.setLinearVelocity(0, velocidadY);
            return;
        }

        if (agachar && !estaSaltando) {
            estaAgachado = true;
            estaCorriendo = false;
            cuerpo.setLinearVelocity(0, velocidadY);
            return;
        }
        estaAgachado = false;

        if (izquierda) {
            cuerpo.setLinearVelocity(-VELOCIDAD, velocidadY);
            estaCorriendo = !estaSaltando;
            mirandoDerecha = false;
        } else if (derecha) {
            cuerpo.setLinearVelocity(VELOCIDAD, velocidadY);
            estaCorriendo = !estaSaltando;
            mirandoDerecha = true;
        } else {
            cuerpo.setLinearVelocity(0, velocidadY);
            estaCorriendo = false;
        }

        if (saltoPedido && !estaSaltando) {
            cuerpo.setLinearVelocity(cuerpo.getLinearVelocity().x, VELOCIDAD_SALTO);
            estaSaltando = true;
            estaCorriendo = false;
        }
    }

    /**
     * Con la pistola en mano el jugador no se mueve (como al agacharse):
     * subir / bajar mueve la mira, izquierda / derecha solo lo da vuelta, disparar tira una bala
     * y apuntar de nuevo vuelve al modo normal.
     */
    private void procesarApuntado(float delta, float velocidadY) {
        cuerpo.setLinearVelocity(0, velocidadY);
        estaCorriendo = false;
        estaAgachado = false;

        if (apuntarPedido) {
            apuntando = false;
            return;
        }

        if (izquierda) {
            mirandoDerecha = false;
        } else if (derecha) {
            mirandoDerecha = true;
        }

        if (arriba) {
            anguloMira += VELOCIDAD_MIRA * delta;
        }
        if (agachar) {
            anguloMira -= VELOCIDAD_MIRA * delta;
        }
        anguloMira = Math.max(ANGULO_MIRA_MINIMO, Math.min(ANGULO_MIRA_MAXIMO, anguloMira));

        if (disparoPedido && tiempoEntreDisparos <= 0f) {
            tiempoEntreDisparos = TIEMPO_ENTRE_DISPAROS;
            disparoRealizado = true;
        }
    }

    /** Apretar cualquier tecla (menos agacharse) da una chance de salirse del combo. */
    private void intentarEscapar() {
        boolean izquierdaNueva = izquierda && !izquierdaAnterior;
        boolean derechaNueva = derecha && !derechaAnterior;
        boolean apretoTecla = izquierdaNueva || derechaNueva || saltoPedido || golpePedido;

        if (apretoTecla && ThreadLocalRandom.current().nextFloat() < PROBABILIDAD_ESCAPE) {
            reaccion.escapar();
        }
    }


    public void comprobarAtaque(JugadorServidor rival) {
        if (!estaGolpeando || golpeConectado || !activo || !rival.activo) return;

        Vector2 miPosicion = cuerpo.getPosition();
        Vector2 posicionRival = rival.cuerpo.getPosition();

        float diferenciaX = posicionRival.x - miPosicion.x;
        float distanciaY = Math.abs(posicionRival.y - miPosicion.y);

        boolean rivalDeFrente = mirandoDerecha ? diferenciaX > -0.5f : diferenciaX < 0.5f;

        if (rivalDeFrente && Math.abs(diferenciaX) < RANGO_GOLPE_X && distanciaY < RANGO_GOLPE_Y) {
            golpeConectado = true;
            rival.recibirGolpe(this);
        }
    }

    private void recibirGolpe(JugadorServidor atacante) {
        if (!reaccion.recibirGolpe()) return;

        vida = Math.max(0, vida - DANIO_GOLPE);
        estaGolpeando = false;
        apuntando = false;
        estaCorriendo = false;
        estaAgachado = false;
        mirandoDerecha = atacante.getX() > getX();
        cuerpo.setLinearVelocity(0, cuerpo.getLinearVelocity().y);

        if (vida == 0) {
            activo = false;
        }
    }

    /**
     * Lo llama la bala cuando toca a este jugador. Resta vida y lo pone rojo un instante,
     * pero no lo aturde ni le cambia la animacion (y no hace nada si esta en la inmunidad del combo).
     */
    public void recibirDisparo() {
        if (!activo || reaccion.esInmune()) return;

        vida = Math.max(0, vida - DANIO_DISPARO);
        tiempoRojo = DURACION_ROJO_DISPARO;

        if (vida == 0) {
            activo = false;
            apuntando = false;
            estaGolpeando = false;
            estaCorriendo = false;
            estaAgachado = false;
            cuerpo.setLinearVelocity(0, cuerpo.getLinearVelocity().y);
        }
    }

    /** @return true una sola vez por cada disparo hecho (el mundo crea la bala). */
    public boolean consumirDisparo() {
        boolean fue = disparoRealizado;
        disparoRealizado = false;
        return fue;
    }

    public void curar(int cantidad) {
        vida = Math.min(VIDA_MAXIMA, vida + cantidad);
    }

    public void equiparPistola() {
        tienePistola = true;
    }

    /** Vuelve al lugar de salida con la vida llena (cada ronda nueva). */
    public void reaparecer() {
        mundo.destroyBody(cuerpo);
        contactosSuelo = 0;
        crearCuerpo();

        activo = true;
        estaAgachado = false;
        estaCorriendo = false;
        estaSaltando = false;
        estaGolpeando = false;
        golpeConectado = false;
        vida = VIDA_MAXIMA;
        tienePistola = false;
        apuntando = false;
        anguloMira = ANGULO_MIRA_INICIAL;
        tiempoEntreDisparos = 0f;
        tiempoRojo = 0f;
        disparoRealizado = false;
        tiempoGolpe = 0f;
        reaccion.reiniciar();
        mirandoDerecha = mirandoDerechaInicial;
        limpiarEntrada();
    }


    /** Posicion en pixeles (centro del jugador). */
    public float getX() {
        return cuerpo.getPosition().x * MundoServidor.PIXELES_POR_METRO;
    }

    public float getY() {
        return cuerpo.getPosition().y * MundoServidor.PIXELES_POR_METRO;
    }

    public int getVida() {
        return vida;
    }

    public boolean isActivo() {
        return activo;
    }

    public boolean estaAgachado() {
        return estaAgachado;
    }

    public boolean tienePistola() {
        return tienePistola;
    }

    public boolean estaMirandoDerecha() {
        return mirandoDerecha;
    }

    public boolean estaApuntando() {
        return apuntando;
    }

    /** true mientras dura el destello rojo de una bala. */
    public boolean estaRojo() {
        return tiempoRojo > 0f;
    }

    /** Angulo de la mira en grados (0 = recto, 90 = hacia arriba). */
    public float getAnguloMira() {
        return anguloMira;
    }

    /** Donde nace la bala (x): delante del jugador. */
    public float getXBoca() {
        return getX() + (mirandoDerecha ? SALIDA_BALA_X : -SALIDA_BALA_X);
    }

    /** Altura de la boca del arma (y). */
    public float getYBoca() {
        return getY() + ALTURA_BOCA;
    }

    /** Punto (x) hacia donde apunta la mira. */
    public float getXMira() {
        return getXBoca() + (mirandoDerecha ? 1 : -1) * DISTANCIA_MIRA * (float) Math.cos(Math.toRadians(anguloMira));
    }

    /** Punto (y) hacia donde apunta la mira. */
    public float getYMira() {
        return getYBoca() + DISTANCIA_MIRA * (float) Math.sin(Math.toRadians(anguloMira));
    }

    /** Misma prioridad que usa el modo local para elegir la imagen. */
    public AnimacionJugador getAnimacion() {
        if (!activo) return AnimacionJugador.MUERTO;

        if (reaccion.estaAturdido()) {
            switch (reaccion.getEtapa()) {
                case 1: return AnimacionJugador.RECIBIR_1;
                case 2: return AnimacionJugador.RECIBIR_2;
                default: return AnimacionJugador.RECIBIR_3;
            }
        }

        if (apuntando) return AnimacionJugador.DISPARO;
        if (estaGolpeando) return AnimacionJugador.GOLPE;
        if (estaSaltando) return AnimacionJugador.SALTO;
        if (estaAgachado) return AnimacionJugador.AGACHADO;
        if (estaCorriendo) return AnimacionJugador.CORRER;
        return AnimacionJugador.QUIETO;
    }
}
