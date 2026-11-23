package Servidor;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * Ventana del servidor. Es SOLO para mirar (no manda nada ni toca el juego, solo lee lo que publica ServidorUDP):
 *  - Sin partida: pantalla de inicio con el logo, "SERVIDOR CONECTADO", el puerto y los jugadores (n/2).
 *  - Con partida: la partida en vivo, con una franja arriba con el logo, el estado y los jugadores.
 *  - Cuando se van los jugadores y la partida se corta, vuelve a la pantalla de inicio.
 * Se dibuja en un lienzo fijo de 1280 x 810 (la partida es de 1280x720 + 90 de franja) que se escala al tamano de la ventana.
 */
public class VentanaServidor {

    static final int ANCHO = PartidaVista.ANCHO;
    static final int ALTO_FRANJA = 90;
    static final int ALTO = ALTO_FRANJA + PartidaVista.ALTO;

    private static final Color FONDO = new Color(1, 1, 1); // el mismo negro que tiene el logo
    private static final Color VERDE = new Color(80, 230, 110);
    private static final Color ROJO = new Color(240, 90, 80);
    private static final Color AMARILLO = new Color(255, 215, 0);
    private static final int MILISEGUNDOS_ENTRE_CUADROS = 16; // ~60 cuadros por segundo

    private final ServidorUDP servidor;
    private final int numeroPuerto;
    private final BufferedImage logo;     // recortado al dibujo (sin el negro de alrededor)
    private PartidaVista partidaVista;    // null si no se pudieron cargar las imagenes
    private EstadoVista estadoActual;
    private String ultimoTexto;
    private long ultimoCuadro = System.nanoTime();

    private VentanaServidor(ServidorUDP servidor, int numeroPuerto) {
        this.servidor = servidor;
        this.numeroPuerto = numeroPuerto;
        this.logo = cargarLogo();

        try {
            this.partidaVista = new PartidaVista();
        } catch (Exception e) {
            System.out.println("[VENTANA] No se pudo preparar la vista de la partida: " + e);
        }
    }

    /**
     * Abre la ventana. alCerrar se ejecuta cuando el usuario la cierra (el servidor tiene que apagarse).
     * Se puede llamar desde cualquier hilo.
     */
    public static void abrir(ServidorUDP servidor, int numeroPuerto, Runnable alCerrar) {
        SwingUtilities.invokeLater(() -> new VentanaServidor(servidor, numeroPuerto).construir(alCerrar));
    }

    /** Cartel de error para cuando el servidor no puede arrancar (el .jar a veces se abre sin consola). */
    public static void mostrarError(String mensaje) {
        if (GraphicsEnvironment.isHeadless()) return;
        JOptionPane.showMessageDialog(null, mensaje, "GangFights - Servidor", JOptionPane.ERROR_MESSAGE);
    }

    private void construir(Runnable alCerrar) {
        JFrame ventana = new JFrame("GangFights - Servidor");
        ventana.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        ventana.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                ventana.dispose();
                alCerrar.run();
            }
        });

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics grafico) {
                super.paintComponent(grafico);
                pintar((Graphics2D) grafico, getWidth(), getHeight());
            }
        };
        panel.setBackground(Color.BLACK);
        panel.setPreferredSize(tamanioInicial());

        ventana.setContentPane(panel);
        ventana.pack();
        ventana.setMinimumSize(new Dimension(640, 420));
        ventana.setLocationRelativeTo(null);
        ventana.setVisible(true);

        new Timer(MILISEGUNDOS_ENTRE_CUADROS, e -> panel.repaint()).start();
    }

    /** El lienzo completo si entra en la pantalla; si no, mas chico manteniendo la proporcion. */
    private static Dimension tamanioInicial() {
        Rectangle pantalla = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        double escala = Math.min(1.0, Math.min((pantalla.width - 30) / (double) ANCHO, (pantalla.height - 70) / (double) ALTO));
        escala = Math.max(escala, 0.4);
        return new Dimension((int) (ANCHO * escala), (int) (ALTO * escala));
    }


    // ---------------------------------------------------------------- Dibujo

    private void pintar(Graphics2D g, int anchoVentana, int altoVentana) {
        long ahora = System.nanoTime();
        float delta = Math.min(0.1f, (ahora - ultimoCuadro) / 1_000_000_000f);
        ultimoCuadro = ahora;

        leerEstado();
        boolean corriendo = servidor.isCorriendo();
        boolean hayPartida = corriendo && estadoActual != null && partidaVista != null
            && estadoActual.fase != EstadoVista.FASE_ESPERANDO;

        if (hayPartida) partidaVista.actualizar(estadoActual, delta);

        // El lienzo de 1280x810 se escala para entrar en la ventana (con barras negras si la proporcion no coincide)
        double escala = Math.min(anchoVentana / (double) ANCHO, altoVentana / (double) ALTO);
        double desplazamientoX = (anchoVentana - ANCHO * escala) / 2.0;
        double desplazamientoY = (altoVentana - ALTO * escala) / 2.0;

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, anchoVentana, altoVentana);
        g.translate(desplazamientoX, desplazamientoY);
        g.scale(escala, escala);
        g.setClip(0, 0, ANCHO, ALTO);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int jugadores = servidor.getJugadoresConectados();

        if (hayPartida) {
            dibujarFranja(g, corriendo, jugadores);

            AffineTransform original = g.getTransform();
            g.translate(0, ALTO_FRANJA);
            partidaVista.dibujar(g);
            g.setTransform(original);
        } else {
            dibujarInicio(g, corriendo, jugadores);
        }
    }

    /** Toma la ultima foto del servidor (solo la convierte si cambio). */
    private void leerEstado() {
        String texto = servidor.getUltimoEstado();
        if (texto == null) {
            estadoActual = null;
            ultimoTexto = null;
        } else if (texto != ultimoTexto) {
            ultimoTexto = texto;
            estadoActual = EstadoVista.leer(texto);
        }
    }

    /** Pantalla de inicio: el logo en grande, el estado, el puerto y los jugadores. */
    private void dibujarInicio(Graphics2D g, boolean corriendo, int jugadores) {
        g.setColor(FONDO);
        g.fillRect(0, 0, ANCHO, ALTO);

        if (logo != null) {
            // Agrandado en numeros enteros para que el pixel art quede nitido
            int factor = Math.max(1, Math.min(820 / logo.getWidth(), 330 / logo.getHeight()));
            int ancho = logo.getWidth() * factor;
            int alto = logo.getHeight() * factor;
            g.drawImage(logo, (ANCHO - ancho) / 2, 30 + (330 - alto) / 2, ancho, alto, null);
        }

        Dibujo.textoCentrado(g, corriendo ? "SERVIDOR CONECTADO" : "SERVIDOR DESCONECTADO", 68,
            corriendo ? VERDE : ROJO, ANCHO / 2f, 385);
        Dibujo.textoCentrado(g, "PUERTO UDP: " + numeroPuerto, 34, Color.WHITE, ANCHO / 2f, 475);

        Dibujo.textoCentrado(g, "JUGADORES", 30, Color.LIGHT_GRAY, ANCHO / 2f, 545);
        Dibujo.textoCentrado(g, jugadores + "/2", 130, Color.WHITE, ANCHO / 2f, 585);

        String detalle;
        Color colorDetalle;
        if (!corriendo) {
            detalle = "EL SERVIDOR SE CERRO";
            colorDetalle = ROJO;
        } else {
            detalle = "ESPERANDO JUGADORES...";
            colorDetalle = AMARILLO;
        }
        Dibujo.textoCentrado(g, detalle, 34, colorDetalle, ANCHO / 2f, 735);
    }

    /** Franja de arriba durante la partida: logo, estado del servidor y jugadores. */
    private void dibujarFranja(Graphics2D g, boolean corriendo, int jugadores) {
        g.setColor(FONDO);
        g.fillRect(0, 0, ANCHO, ALTO_FRANJA);

        if (logo != null) {
            int alto = ALTO_FRANJA - 14;
            int ancho = Math.round(logo.getWidth() * (alto / (float) logo.getHeight()));
            g.drawImage(logo, 20, 7, ancho, alto, null);
        }

        Dibujo.textoCentrado(g, corriendo ? "SERVIDOR CONECTADO" : "SERVIDOR DESCONECTADO", 38,
            corriendo ? VERDE : ROJO, ANCHO / 2f, 12);
        Dibujo.textoCentrado(g, "PUERTO UDP: " + numeroPuerto + "   |   PARTIDA EN CURSO", 20,
            Color.LIGHT_GRAY, ANCHO / 2f, 60);

        Dibujo.textoDerecha(g, jugadores + "/2", 56, Color.WHITE, ANCHO - 30f, 10);
        Dibujo.textoDerecha(g, "JUGADORES", 16, Color.LIGHT_GRAY, ANCHO - 30f, 70);
    }

    /**
     * El logo viene dentro del .jar. La imagen es un lienzo negro con el logo chiquito en el centro:
     * se recorta alrededor del dibujo. Si no se puede leer, la ventana funciona igual sin imagen.
     */
    private static BufferedImage cargarLogo() {
        try {
            return Dibujo.sinFondoNegro(Dibujo.imagen("/LogoPrincipal.png"), 12);
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }
}
