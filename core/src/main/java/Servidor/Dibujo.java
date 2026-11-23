package Servidor;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Herramientas de dibujo compartidas por la ventana del servidor: cargar imagenes del .jar, texto, recortes. */
final class Dibujo {

    private static final String FUENTE = "/Fuentes/PixelifySans-SemiBold.ttf";
    private static final FontRenderContext CONTEXTO = new FontRenderContext(null, true, true);

    private static Font base;
    private static final Map<Integer, Font> fuentes = new HashMap<>();
    private static final Map<Integer, Float> alturasMayuscula = new HashMap<>();

    private Dibujo() { }

    /**
     * Abre un archivo de la carpeta assets. Dentro del .jar esta en la raiz; si se ejecuta desde el IDE
     * tambien se prueba en la carpeta assets del proyecto.
     */
    static InputStream abrir(String ruta) throws IOException {
        InputStream dentroDelJar = Dibujo.class.getResourceAsStream(ruta);
        if (dentroDelJar != null) return dentroDelJar;

        for (String carpeta : new String[] {"../assets", "assets"}) {
            File archivo = new File(carpeta + ruta);
            if (archivo.exists()) return new FileInputStream(archivo);
        }
        throw new IOException("No se encontro " + ruta);
    }

    static BufferedImage imagen(String ruta) throws IOException {
        try (InputStream entrada = abrir(ruta)) {
            BufferedImage imagen = ImageIO.read(entrada);
            if (imagen == null) throw new IOException("Imagen invalida: " + ruta);
            return imagen;
        }
    }

    /** La fuente del juego (la misma que el cliente). Si no se puede leer se usa una comun. */
    static synchronized Font fuente(int tamanio) {
        Font cacheada = fuentes.get(tamanio);
        if (cacheada != null) return cacheada;

        if (base == null) {
            try (InputStream entrada = abrir(FUENTE)) {
                base = Font.createFont(Font.TRUETYPE_FONT, entrada);
            } catch (IOException | FontFormatException e) {
                base = new Font(Font.MONOSPACED, Font.BOLD, 1);
            }
        }
        Font fuente = base.deriveFont((float) tamanio);
        fuentes.put(tamanio, fuente);
        return fuente;
    }

    /** Alto de una letra mayuscula: sirve para ubicar el texto por su borde de arriba, como hace el cliente. */
    private static synchronized float alturaMayuscula(Font fuente, int tamanio) {
        Float cacheada = alturasMayuscula.get(tamanio);
        if (cacheada != null) return cacheada;

        float altura = (float) fuente.createGlyphVector(CONTEXTO, "H").getVisualBounds().getHeight();
        alturasMayuscula.put(tamanio, altura);
        return altura;
    }

    /** Dibuja el texto con su borde de ARRIBA en yArriba (y hacia abajo) y su borde izquierdo en x. */
    static void texto(Graphics2D g, String texto, int tamanio, Color color, float x, float yArriba) {
        Font fuente = fuente(tamanio);
        g.setFont(fuente);
        g.setColor(color);
        g.drawString(texto, x, yArriba + alturaMayuscula(fuente, tamanio));
    }

    static float ancho(Graphics2D g, String texto, int tamanio) {
        return g.getFontMetrics(fuente(tamanio)).stringWidth(texto);
    }

    static void textoCentrado(Graphics2D g, String texto, int tamanio, Color color, float centroX, float yArriba) {
        texto(g, texto, tamanio, color, centroX - ancho(g, texto, tamanio) / 2f, yArriba);
    }

    static void textoDerecha(Graphics2D g, String texto, int tamanio, Color color, float xDerecha, float yArriba) {
        texto(g, texto, tamanio, color, xDerecha - ancho(g, texto, tamanio), yArriba);
    }

    /** Recorta la imagen dejando solo lo que no es transparente. */
    static BufferedImage sinBordeTransparente(BufferedImage imagen) {
        int minX = imagen.getWidth(), minY = imagen.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                if ((imagen.getRGB(x, y) >>> 24) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) return imagen;
        return imagen.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Recorta la imagen dejando solo la zona que no es negra (mas un margen). El logo es un lienzo negro con el dibujo al medio. */
    static BufferedImage sinFondoNegro(BufferedImage imagen, int margen) {
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
        if (maxX < 0) return imagen;

        int x0 = Math.max(0, minX - margen);
        int y0 = Math.max(0, minY - margen);
        int x1 = Math.min(imagen.getWidth() - 1, maxX + margen);
        int y1 = Math.min(imagen.getHeight() - 1, maxY + margen);
        return imagen.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    private static final Map<BufferedImage, BufferedImage> rojos = new HashMap<>();

    /** Igual que enRojo, pero la copia se guarda para no rehacerla en cada cuadro. */
    static synchronized BufferedImage enRojoCacheado(BufferedImage imagen) {
        return rojos.computeIfAbsent(imagen, Dibujo::enRojo);
    }

    /** Copia de la imagen mas rojiza (cuando un jugador recibe un golpe o un disparo). Mismo tinte que el cliente. */
    static BufferedImage enRojo(BufferedImage imagen) {
        BufferedImage copia = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                int argb = imagen.getRGB(x, y);
                int a = argb >>> 24;
                int r = argb >> 16 & 0xFF;
                int gr = (int) ((argb >> 8 & 0xFF) * 0.55f);
                int b = (int) ((argb & 0xFF) * 0.55f);
                copia.setRGB(x, y, a << 24 | r << 16 | gr << 8 | b);
            }
        }
        return copia;
    }
}
