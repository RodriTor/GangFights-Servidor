package Servidor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Dibuja la partida en la ventana del servidor (solo mirar: no manda nada ni toca el juego).
 * Usa las mismas medidas que el cliente (mundo de 1280x720, el origen del juego esta ABAJO a la izquierda;
 * aca se convierte a coordenadas de pantalla con y = 720 - y). Lee la ultima "foto" del servidor y suaviza
 * el movimiento igual que el cliente, asi el servidor muestra lo mismo que ven los jugadores.
 */
final class PartidaVista {

    static final int ANCHO = 1280;
    static final int ALTO = 720;

    private static final int ANCHO_FRAME = 32;
    private static final int ALTO_FRAME = 42;
    private static final float ANCHO_CUERPO = 30f;
    private static final float ALTO_CUERPO = 60f;

    private static final float VELOCIDAD_SUAVIZADO = 20f;
    private static final float DISTANCIA_PARA_TELETRANSPORTAR = 150f;

    // Mira (mismas medidas que JugadorServidor)
    private static final float DISTANCIA_MIRA = 70f;
    private static final float ALTURA_BOCA = 8f;
    private static final float SALIDA_BALA_X = 22f;
    private static final float TAMANIO_MIRA = 12f;
    private static final float GROSOR_MIRA = 2f;

    // Balas
    private static final float VELOCIDAD_BALA = 900f;
    private static final float TIEMPO_MAXIMO_DESLIZADO = 0.08f;

    // Items: las imagenes se dibujan al doble de tamano
    private static final float ESCALA_ITEMS = 2f;

    // HUD (mismas medidas que el HUD del cliente)
    private static final float MARGEN = 20f;
    private static final float CAJA = 90f;
    private static final float PANEL_ANCHO = 330f;
    private static final float PANEL_ALTO = 90f;
    private static final float RETRATO = 70f;
    private static final float BARRA_ANCHO = 215f;
    private static final float BARRA_ALTO = 20f;
    private static final float SLOT = 54f;
    private static final float SEPARACION_SLOT = 10f;

    private static final Color FONDO = new Color(13, 13, 20);
    private static final Color FONDO_BARRA = new Color(38, 38, 46);
    private static final Color FONDO_SLOT = new Color(140, 140, 153);
    private static final Color FONDO_RETRATO = new Color(51, 51, 64);
    private static final Color FONDO_RETRATO_MUERTO = new Color(115, 13, 13);
    private static final Color[] COLORES = {Color.CYAN, Color.ORANGE};
    private static final Color AMARILLO = new Color(255, 255, 0);

    /** Las imagenes de un personaje, ya recortadas en cuadros de 32x42 (cada una tambien en version "roja"). */
    private static final class ImagenesPersonaje {
        final BufferedImage[][] quieto, correr, golpe, recibir;
        final BufferedImage agachado, salto, muerto, disparo;

        ImagenesPersonaje(String carpeta, String nombre) throws java.io.IOException {
            quieto = cuadros(carpeta, nombre, "Quieto", 3);
            correr = cuadros(carpeta, nombre, "Correr", 3);
            golpe = cuadros(carpeta, nombre, "Golpe", 2);
            recibir = cuadros(carpeta, nombre, "Recibir", 3);
            agachado = cuadros(carpeta, nombre, "Agachado", 1)[0][0];
            salto = cuadros(carpeta, nombre, "Salto", 1)[0][0];
            muerto = cuadros(carpeta, nombre, "Muerto", 1)[0][0];
            disparo = cuadros(carpeta, nombre, "Disparo", 1)[0][0];
        }

        /** Devuelve {normales, rojos}. Orden de los cuadros: [0][0], [0][1], [1][0] (como en el cliente). */
        private static BufferedImage[][] cuadros(String carpeta, String nombre, String accion, int cantidad)
            throws java.io.IOException {
            BufferedImage hoja = Dibujo.imagen("/Spritesheets/" + carpeta + "/" + nombre + accion + ".png");
            int[][] posiciones = {{0, 0}, {0, 1}, {1, 0}};

            BufferedImage[][] resultado = new BufferedImage[2][cantidad];
            for (int i = 0; i < cantidad; i++) {
                int fila = posiciones[i][0], columna = posiciones[i][1];
                boolean existe = (columna + 1) * ANCHO_FRAME <= hoja.getWidth() && (fila + 1) * ALTO_FRAME <= hoja.getHeight();
                if (!existe) { fila = 0; columna = 0; } // si la hoja no tiene ese cuadro, usa el primero

                BufferedImage cuadro = hoja.getSubimage(columna * ANCHO_FRAME, fila * ALTO_FRAME, ANCHO_FRAME, ALTO_FRAME);
                resultado[0][i] = cuadro;
                resultado[1][i] = Dibujo.enRojo(cuadro);
            }
            return resultado;
        }
    }

    /** Como se ve un jugador: posicion suavizada y tiempos de animacion. */
    private static final class JugadorDibujo {
        float x, y, objetivoX, objetivoY;
        int animacion;
        boolean mirandoDerecha, rojo, apuntando;
        int anguloMira;
        float tiempoAnimacion, tiempoGolpe;
        boolean primeraVez = true;

        void recibir(EstadoVista.DatosJugador d) {
            if (d.animacion == EstadoVista.ANIM_GOLPE && animacion != EstadoVista.ANIM_GOLPE) tiempoGolpe = 0f;

            objetivoX = d.x;
            objetivoY = d.y;
            animacion = d.animacion;
            mirandoDerecha = d.mirandoDerecha;
            rojo = d.rojo;
            apuntando = d.apuntando;
            anguloMira = d.anguloMira;

            if (primeraVez) {
                x = objetivoX;
                y = objetivoY;
                primeraVez = false;
            }
        }

        void actualizar(float delta) {
            tiempoAnimacion += delta;
            if (animacion == EstadoVista.ANIM_GOLPE) tiempoGolpe += delta;

            float dx = objetivoX - x, dy = objetivoY - y;
            if (Math.abs(dx) > DISTANCIA_PARA_TELETRANSPORTAR || Math.abs(dy) > DISTANCIA_PARA_TELETRANSPORTAR) {
                x = objetivoX; // empezo una ronda nueva: no tiene sentido deslizarse
                y = objetivoY;
                return;
            }
            float avance = Math.min(1f, VELOCIDAD_SUAVIZADO * delta);
            x += dx * avance;
            y += dy * avance;
        }
    }

    private final BufferedImage mapa;
    private final ImagenesPersonaje[] personajes = new ImagenesPersonaje[2];
    private final JugadorDibujo[] jugadores = {new JugadorDibujo(), new JugadorDibujo()};
    private final BufferedImage botiquin, pistola, calavera;
    private final BufferedImage[] retratos = new BufferedImage[2];
    private final String[] nombres = {"JASINSKI", "SCHEPIS"};

    private EstadoVista ultimo;
    private int ultimaSecuencia = -1;
    private float tiempoDesdeEstado;

    /** @throws Exception si falta algun archivo del mapa o de las imagenes (la ventana sigue funcionando sin la vista de partida) */
    PartidaVista() throws Exception {
        mapa = MapaVista.crear();
        personajes[0] = new ImagenesPersonaje("Jasinski", "Jasinski");
        personajes[1] = new ImagenesPersonaje("Schepis", "Schepis");
        retratos[0] = personajes[0].quieto[0][0];
        retratos[1] = personajes[1].quieto[0][0];
        botiquin = Dibujo.sinBordeTransparente(Dibujo.imagen("/Elementos/Vida.png"));
        pistola = Dibujo.sinBordeTransparente(Dibujo.imagen("/Elementos/Pistola.png"));
        calavera = Dibujo.imagen("/Elementos/Calavera.png");
    }

    /** Se llama en cada cuadro de la ventana con la ultima foto del servidor. */
    void actualizar(EstadoVista estado, float delta) {
        if (estado == null) return;

        if (estado.secuencia != ultimaSecuencia) {
            ultimaSecuencia = estado.secuencia;
            ultimo = estado;
            tiempoDesdeEstado = 0f;
            for (int i = 0; i < 2; i++) jugadores[i].recibir(estado.jugadores[i]);
        }

        tiempoDesdeEstado += delta;
        for (JugadorDibujo jugador : jugadores) jugador.actualizar(delta);
    }

    /** Dibuja la partida en un area de 1280x720 con el origen arriba a la izquierda. */
    void dibujar(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, ANCHO, ALTO);

        // El mapa (544 px de alto) se apoya abajo; arriba queda el espacio negro del HUD
        g.drawImage(mapa, 0, ALTO - mapa.getHeight(), null);

        if (ultimo == null) return;

        dibujarItem(g, botiquin, ultimo.botiquin);
        dibujarItem(g, pistola, ultimo.pistola);
        for (int i = 0; i < 2; i++) dibujarJugador(g, i);
        dibujarBalas(g);

        dibujarHud(g);
        dibujarMensajes(g);
    }

    // ---------------------------------------------------------------- Items, jugadores y balas

    private void dibujarItem(Graphics2D g, BufferedImage imagen, int[] posicion) {
        if (posicion == null) return;

        int ancho = Math.round(imagen.getWidth() * ESCALA_ITEMS);
        int alto = Math.round(imagen.getHeight() * ESCALA_ITEMS);
        g.drawImage(imagen, posicion[0] - ancho / 2, ALTO - posicion[1] - alto, ancho, alto, null);
    }

    private void dibujarJugador(Graphics2D g, int indice) {
        JugadorDibujo j = jugadores[indice];
        ImagenesPersonaje p = personajes[indice];

        boolean muerto = j.animacion == EstadoVista.ANIM_MUERTO;
        boolean aturdido = j.animacion >= EstadoVista.ANIM_RECIBIR_1 && j.animacion <= EstadoVista.ANIM_RECIBIR_3;
        int version = (!muerto && (aturdido || j.rojo)) ? 1 : 0; // 1 = version roja

        BufferedImage imagen = elegirImagen(p, j, version);

        if (muerto) {
            // Muerto: la imagen se acuesta (girada 90 grados) y queda apoyada en el piso
            float giro = j.mirandoDerecha ? 90f : -90f;
            float centroY = j.y - (ALTO_CUERPO - ANCHO_CUERPO) / 2f;

            AffineTransform original = g.getTransform();
            g.translate(j.x, ALTO - centroY);
            g.rotate(Math.toRadians(-giro)); // en pantalla "y" crece hacia abajo, por eso el signo
            dibujarImagenCentrada(g, imagen, j.mirandoDerecha);
            g.setTransform(original);
            return;
        }

        AffineTransform original = g.getTransform();
        g.translate(j.x, ALTO - j.y);
        dibujarImagenCentrada(g, imagen, j.mirandoDerecha);
        g.setTransform(original);

        if (j.apuntando) dibujarMira(g, j);
    }

    /** Las imagenes miran a la derecha: si mira a la izquierda se espeja. */
    private void dibujarImagenCentrada(Graphics2D g, BufferedImage imagen, boolean mirandoDerecha) {
        int izquierda = Math.round(-ANCHO_CUERPO / 2f);
        int arriba = Math.round(-ALTO_CUERPO / 2f);
        int ancho = Math.round(ANCHO_CUERPO);
        int alto = Math.round(ALTO_CUERPO);

        if (mirandoDerecha) {
            g.drawImage(imagen, izquierda, arriba, ancho, alto, null);
        } else {
            g.drawImage(imagen, izquierda + ancho, arriba, izquierda, arriba + alto, 0, 0,
                imagen.getWidth(), imagen.getHeight(), null);
        }
    }

    private BufferedImage elegirImagen(ImagenesPersonaje p, JugadorDibujo j, int v) {
        switch (j.animacion) {
            case EstadoVista.ANIM_MUERTO:
                return p.muerto;
            case EstadoVista.ANIM_RECIBIR_1:
                return p.recibir[v][0];
            case EstadoVista.ANIM_RECIBIR_2:
                return p.recibir[v][1];
            case EstadoVista.ANIM_RECIBIR_3:
                return p.recibir[v][2];
            case EstadoVista.ANIM_DISPARO:
                return v == 1 ? Dibujo.enRojoCacheado(p.disparo) : p.disparo;
            case EstadoVista.ANIM_GOLPE: {
                int cuadro = Math.min(1, (int) (j.tiempoGolpe / 0.15f)); // 0.15 s por cuadro, se queda en el ultimo
                return p.golpe[v][cuadro];
            }
            case EstadoVista.ANIM_SALTO:
                return v == 1 ? Dibujo.enRojoCacheado(p.salto) : p.salto;
            case EstadoVista.ANIM_AGACHADO:
                return v == 1 ? Dibujo.enRojoCacheado(p.agachado) : p.agachado;
            case EstadoVista.ANIM_CORRER:
                return p.correr[v][(int) (j.tiempoAnimacion / 0.10f) % 3];
            default:
                return p.quieto[v][(int) (j.tiempoAnimacion / 0.15f) % 3];
        }
    }

    /** Un cuadradito hueco que gira alrededor de la boca del arma: hacia ahi va a salir la bala. */
    private void dibujarMira(Graphics2D g, JugadorDibujo j) {
        float sentido = j.mirandoDerecha ? 1f : -1f;
        double radianes = Math.toRadians(j.anguloMira);

        float centroX = j.x + sentido * SALIDA_BALA_X + sentido * DISTANCIA_MIRA * (float) Math.cos(radianes);
        float centroY = j.y + ALTURA_BOCA + DISTANCIA_MIRA * (float) Math.sin(radianes);

        int lado = Math.round(TAMANIO_MIRA);
        int grosor = Math.round(GROSOR_MIRA);
        int izquierda = Math.round(centroX - TAMANIO_MIRA / 2f);
        int arriba = Math.round(ALTO - centroY - TAMANIO_MIRA / 2f);

        g.setColor(Color.WHITE);
        g.fillRect(izquierda, arriba, lado, grosor);
        g.fillRect(izquierda, arriba + lado - grosor, lado, grosor);
        g.fillRect(izquierda, arriba, grosor, lado);
        g.fillRect(izquierda + lado - grosor, arriba, grosor, lado);
    }

    /** Las balas se deslizan un poquito entre foto y foto para que se vean fluidas. */
    private void dibujarBalas(Graphics2D g) {
        float desliz = VELOCIDAD_BALA * Math.min(tiempoDesdeEstado, TIEMPO_MAXIMO_DESLIZADO);
        g.setColor(Color.YELLOW);

        for (int[] bala : ultimo.balas) {
            double radianes = Math.toRadians(bala[2]);
            float x = bala[0] + (float) Math.cos(radianes) * desliz;
            float y = bala[1] + (float) Math.sin(radianes) * desliz;

            AffineTransform original = g.getTransform();
            g.translate(x, ALTO - y);
            g.rotate(-radianes);
            g.fillRect(-5, -2, 10, 4);
            g.setTransform(original);
        }
    }

    // ---------------------------------------------------------------- HUD y mensajes

    private float cajaX(int i) { return i == 0 ? MARGEN : ANCHO - MARGEN - CAJA; }
    private float panelX(int i) { return i == 0 ? MARGEN + CAJA + 16f : ANCHO - MARGEN - CAJA - 16f - PANEL_ANCHO; }
    private float panelY() { return ALTO - MARGEN - PANEL_ALTO; }
    private float retratoX(int i) { return i == 0 ? panelX(i) + 10f : panelX(i) + PANEL_ANCHO - 10f - RETRATO; }
    private float barraX(int i) { return i == 0 ? panelX(i) + RETRATO + 22f : panelX(i) + 10f; }
    private float slotX(int i) { return i == 0 ? panelX(i) : panelX(i) + PANEL_ANCHO - SLOT; }
    private float slotY() { return panelY() - SEPARACION_SLOT - SLOT; }

    /** Rectangulo con coordenadas del juego (y hacia arriba): se pasa de la esquina de abajo a la de arriba. */
    private void rect(Graphics2D g, Color color, float x, float y, float ancho, float alto) {
        g.setColor(color);
        g.fillRect(Math.round(x), Math.round(ALTO - y - alto), Math.round(ancho), Math.round(alto));
    }

    private boolean estaEliminado(int i) {
        boolean rondaCerrada = ultimo.fase == EstadoVista.FASE_RONDA_TERMINADA || ultimo.fase == EstadoVista.FASE_FINALIZADA;
        return rondaCerrada && ultimo.ganadorRonda != i;
    }

    private Color colorDeVida(float fraccion) {
        Color a, b;
        float t;
        if (fraccion > 0.5f) {
            a = Color.YELLOW; b = Color.GREEN; t = (fraccion - 0.5f) * 2f;
        } else {
            a = Color.RED; b = Color.YELLOW; t = fraccion * 2f;
        }
        return new Color(
            Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
            Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private void dibujarHud(Graphics2D g) {
        for (int i = 0; i < 2; i++) {
            EstadoVista.DatosJugador d = ultimo.jugadores[i];
            float fraccion = Math.max(0f, Math.min(1f, d.vida / 100f));

            // Caja de rondas ganadas
            rect(g, COLORES[i], cajaX(i) - 3f, panelY() - 3f, CAJA + 6f, CAJA + 6f);
            rect(g, FONDO, cajaX(i), panelY(), CAJA, CAJA);

            // Panel con retrato y barra de vida
            rect(g, COLORES[i], panelX(i) - 3f, panelY() - 3f, PANEL_ANCHO + 6f, PANEL_ALTO + 6f);
            rect(g, FONDO, panelX(i), panelY(), PANEL_ANCHO, PANEL_ALTO);
            rect(g, estaEliminado(i) ? FONDO_RETRATO_MUERTO : FONDO_RETRATO, retratoX(i), panelY() + 10f, RETRATO, RETRATO);

            float barraY = panelY() + 16f;
            rect(g, Color.WHITE, barraX(i) - 2f, barraY - 2f, BARRA_ANCHO + 4f, BARRA_ALTO + 4f);
            rect(g, FONDO_BARRA, barraX(i), barraY, BARRA_ANCHO, BARRA_ALTO);

            // La barra se vacia hacia afuera: siempre queda pegada del lado del retrato
            float anchoVida = BARRA_ANCHO * fraccion;
            float inicioVida = (i == 0) ? barraX(i) : barraX(i) + BARRA_ANCHO - anchoVida;
            rect(g, colorDeVida(fraccion), inicioVida, barraY, anchoVida, BARRA_ALTO);

            // Cuadrito del arma
            rect(g, COLORES[i], slotX(i) - 3f, slotY() - 3f, SLOT + 6f, SLOT + 6f);
            rect(g, FONDO_SLOT, slotX(i), slotY(), SLOT, SLOT);

            dibujarRetrato(g, i);
            if (d.pistola) dibujarArmaEnCuadrito(g, i);

            Dibujo.texto(g, nombres[i], 24, COLORES[i], barraX(i), ALTO - (panelY() + PANEL_ALTO - 14f));
            Dibujo.textoCentrado(g, String.valueOf(ultimo.rondasGanadas[i]), 40, Color.WHITE,
                cajaX(i) + CAJA / 2f, ALTO - (panelY() + CAJA - 14f));
            Dibujo.textoCentrado(g, "RONDAS", 14, Color.LIGHT_GRAY, cajaX(i) + CAJA / 2f, ALTO - (panelY() + 26f));
        }

        Dibujo.textoCentrado(g, ultimo.rondasGanadas[0] + " - " + ultimo.rondasGanadas[1], 56, AMARILLO,
            ANCHO / 2f, MARGEN);
        Dibujo.textoCentrado(g, "RONDA " + ultimo.ronda + "   |   PARA GANAR: " + Logica.PartidaServidor.RONDAS_PARA_GANAR,
            18, Color.WHITE, ANCHO / 2f, MARGEN + 75f);
    }

    private void dibujarRetrato(Graphics2D g, int i) {
        float x = retratoX(i);
        float y = panelY() + 10f;

        if (estaEliminado(i)) {
            float tamanio = RETRATO - 14f;
            g.drawImage(calavera, Math.round(x + (RETRATO - tamanio) / 2f), Math.round(ALTO - (y + (RETRATO - tamanio) / 2f) - tamanio),
                Math.round(tamanio), Math.round(tamanio), null);
            return;
        }

        float alto = RETRATO - 8f;
        float ancho = alto * ANCHO_FRAME / ALTO_FRAME;
        int px = Math.round(x + (RETRATO - ancho) / 2f);
        int arriba = Math.round(ALTO - (y + 4f) - alto);

        if (i == 0) {
            g.drawImage(retratos[i], px, arriba, Math.round(ancho), Math.round(alto), null);
        } else {
            g.drawImage(retratos[i], px + Math.round(ancho), arriba, px, arriba + Math.round(alto), 0, 0,
                ANCHO_FRAME, ALTO_FRAME, null); // el jugador 2 mira a la izquierda
        }
    }

    private void dibujarArmaEnCuadrito(Graphics2D g, int i) {
        float maximo = SLOT - 12f;
        float escala = Math.min(maximo / pistola.getWidth(), maximo / pistola.getHeight());
        float ancho = pistola.getWidth() * escala;
        float alto = pistola.getHeight() * escala;

        g.drawImage(pistola, Math.round(slotX(i) + (SLOT - ancho) / 2f),
            Math.round(ALTO - (slotY() + (SLOT - alto) / 2f) - alto), Math.round(ancho), Math.round(alto), null);
    }

    private void dibujarMensajes(Graphics2D g) {
        switch (ultimo.fase) {
            case EstadoVista.FASE_CUENTA:
                Dibujo.textoCentrado(g, String.valueOf(ultimo.segundosCuenta), 120, AMARILLO, ANCHO / 2f, ALTO - 460f);
                Dibujo.textoCentrado(g, "RONDA " + ultimo.ronda, 28, Color.WHITE, ANCHO / 2f, ALTO - 300f);
                break;

            case EstadoVista.FASE_RONDA_TERMINADA:
                Dibujo.textoCentrado(g, "GANA LA RONDA: " + nombres[ultimo.ganadorRonda], 48, Color.WHITE,
                    ANCHO / 2f, ALTO / 2f - 40f);
                break;

            case EstadoVista.FASE_FINALIZADA:
                Dibujo.textoCentrado(g, "GANA LA PARTIDA: " + nombres[ultimo.ganadorRonda], 48, Color.WHITE,
                    ANCHO / 2f, ALTO / 2f - 40f);
                Dibujo.textoCentrado(g, "ESPERANDO REVANCHA: " + ultimo.cantidadRevanchas() + "/2", 28, Color.WHITE,
                    ANCHO / 2f, ALTO - 260f);
                break;

            default:
                break;
        }
    }
}
