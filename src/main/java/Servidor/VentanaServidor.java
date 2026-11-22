package Servidor;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.swing.WindowConstants;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Ventana del servidor: el logo en grande, el estado ("SERVIDOR CONECTADO"), el puerto y cuantos jugadores hay (n/2).
 * Es solo para mostrar: la ventana no toca el juego, solo lee los contadores que publica ServidorUDP.
 * (Usa Swing para no tener que armar un segundo motor grafico dentro del servidor.)
 */
public class VentanaServidor {

    private static final Color FONDO = new Color(1, 1, 1); // el mismo negro que tiene el logo
    private static final Color VERDE = new Color(80, 230, 110);
    private static final Color ROJO = new Color(240, 90, 80);
    private static final Color AMARILLO = new Color(255, 215, 0);
    private static final int MILISEGUNDOS_ENTRE_ACTUALIZACIONES = 250;

    private final ServidorUDP servidor;
    private final JLabel estado = etiqueta(34, Font.BOLD, VERDE);
    private final JLabel puerto = etiqueta(22, Font.PLAIN, Color.WHITE);
    private final JLabel titulo = etiqueta(24, Font.PLAIN, Color.LIGHT_GRAY);
    private final JLabel jugadores = etiqueta(80, Font.BOLD, Color.WHITE);
    private final JLabel detalle = etiqueta(22, Font.PLAIN, Color.LIGHT_GRAY);

    private VentanaServidor(ServidorUDP servidor, int numeroPuerto) {
        this.servidor = servidor;
        puerto.setText("PUERTO UDP: " + numeroPuerto);
        titulo.setText("JUGADORES");
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
        if (java.awt.GraphicsEnvironment.isHeadless()) return;
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

        JPanel panel = new JPanel();
        panel.setBackground(FONDO);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 30, 20, 30));

        JLabel logo = new JLabel(cargarLogo(440), SwingConstants.CENTER);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(logo);
        panel.add(espacio(10));
        panel.add(estado);
        panel.add(espacio(8));
        panel.add(puerto);
        panel.add(espacio(18));
        panel.add(titulo);
        panel.add(jugadores);
        panel.add(espacio(4));
        panel.add(detalle);

        ventana.setContentPane(panel);
        ventana.setSize(640, 800);
        ventana.setMinimumSize(new java.awt.Dimension(560, 740));
        ventana.setLocationRelativeTo(null);
        ventana.setVisible(true);

        actualizar();
        new Timer(MILISEGUNDOS_ENTRE_ACTUALIZACIONES, e -> actualizar()).start();
    }

    private void actualizar() {
        if (servidor.isCorriendo()) {
            estado.setText("SERVIDOR CONECTADO");
            estado.setForeground(VERDE);
        } else {
            estado.setText("SERVIDOR DESCONECTADO");
            estado.setForeground(ROJO);
        }

        int cantidad = servidor.getJugadoresConectados();
        jugadores.setText(cantidad + "/2");

        if (!servidor.isCorriendo()) {
            detalle.setText("El servidor se cerro");
            detalle.setForeground(ROJO);
        } else if (cantidad >= 2) {
            detalle.setText("PARTIDA EN CURSO");
            detalle.setForeground(VERDE);
        } else {
            detalle.setText("ESPERANDO JUGADORES...");
            detalle.setForeground(AMARILLO);
        }
    }

    private static JLabel etiqueta(int tamanio, int estilo, Color color) {
        JLabel etiqueta = new JLabel(" ", SwingConstants.CENTER);
        etiqueta.setFont(new Font(Font.MONOSPACED, estilo, tamanio));
        etiqueta.setForeground(color);
        etiqueta.setAlignmentX(Component.CENTER_ALIGNMENT);
        return etiqueta;
    }

    private static Component espacio(int alto) {
        return javax.swing.Box.createVerticalStrut(alto);
    }

    /**
     * El logo viene dentro del .jar. La imagen es un lienzo negro con el logo chiquito en el centro:
     * se recorta alrededor del dibujo y se agranda en numeros enteros (sin suavizar, para que el pixel art
     * quede nitido). Si no se puede leer, la ventana funciona igual sin imagen.
     */
    private static ImageIcon cargarLogo(int anchoMaximo) {
        try (InputStream entrada = VentanaServidor.class.getResourceAsStream("/LogoPrincipal.png")) {
            if (entrada == null) return null;

            BufferedImage imagen = ImageIO.read(entrada);
            BufferedImage recorte = recortarAlDibujo(imagen, 12);

            int factor = Math.max(1, anchoMaximo / recorte.getWidth());
            BufferedImage grande = new BufferedImage(recorte.getWidth() * factor, recorte.getHeight() * factor,
                BufferedImage.TYPE_INT_RGB);
            Graphics2D g = grande.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(recorte, 0, 0, grande.getWidth(), grande.getHeight(), null);
            g.dispose();

            return new ImageIcon(grande);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Recorta la imagen dejando solo la zona que no es negra (mas un margen). */
    private static BufferedImage recortarAlDibujo(BufferedImage imagen, int margen) {
        int minX = imagen.getWidth(), minY = imagen.getHeight(), maxX = -1, maxY = -1;

        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                int rgb = imagen.getRGB(x, y);
                int brillo = ((rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF)) / 3;
                if (brillo > 40) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < 0) return imagen; // imagen toda negra

        int x0 = Math.max(0, minX - margen);
        int y0 = Math.max(0, minY - margen);
        int x1 = Math.min(imagen.getWidth() - 1, maxX + margen);
        int y1 = Math.min(imagen.getHeight() - 1, maxY + margen);
        return imagen.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }
}
