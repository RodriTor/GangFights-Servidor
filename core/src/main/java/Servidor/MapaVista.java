package Servidor;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;


final class MapaVista {

    private static final String CARPETA = "/Mapas/";
    private static final long MASCARA_ESPEJO_HORIZONTAL = 0x80000000L;
    private static final long MASCARA_ESPEJO_VERTICAL = 0x40000000L;
    private static final long MASCARA_ID = 0x1FFFFFFFL;

    private MapaVista() { }

    static BufferedImage crear() throws Exception {
        Document mapa = leerXml(CARPETA + "Aula.tmx");
        Element raiz = mapa.getDocumentElement();

        int columnas = Integer.parseInt(raiz.getAttribute("width"));
        int filas = Integer.parseInt(raiz.getAttribute("height"));
        int anchoTile = Integer.parseInt(raiz.getAttribute("tilewidth"));
        int altoTile = Integer.parseInt(raiz.getAttribute("tileheight"));

        BufferedImage resultado = new BufferedImage(columnas * anchoTile, filas * altoTile, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resultado.createGraphics();

        Map<Integer, BufferedImage> tiles = new HashMap<>();

        for (Node nodo = raiz.getFirstChild(); nodo != null; nodo = nodo.getNextSibling()) {
            if (!(nodo instanceof Element)) continue;
            Element elemento = (Element) nodo;

            switch (elemento.getTagName()) {
                case "tileset":
                    cargarTileset(elemento, tiles);
                    break;
                case "imagelayer":
                    dibujarImagenDeFondo(g, elemento);
                    break;
                case "layer":
                    dibujarCapaDeTiles(g, elemento, tiles, columnas, anchoTile, altoTile);
                    break;
                default:
                    break; // objectgroup (colisiones): no se dibuja
            }
        }

        g.dispose();
        return resultado;
    }

    private static Document leerXml(String ruta) throws Exception {
        try (InputStream entrada = Dibujo.abrir(ruta)) {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(entrada);
        }
    }

    /** Cada tile del tileset es una imagen suelta (tileset "coleccion"): gid = firstgid + id. */
    private static void cargarTileset(Element tileset, Map<Integer, BufferedImage> tiles) throws Exception {
        int primerGid = Integer.parseInt(tileset.getAttribute("firstgid"));
        String origen = tileset.getAttribute("source");
        if (origen.isEmpty()) return;

        Element raizTileset = leerXml(CARPETA + origen).getDocumentElement();
        NodeList lista = raizTileset.getElementsByTagName("tile");

        for (int i = 0; i < lista.getLength(); i++) {
            Element tile = (Element) lista.item(i);
            NodeList imagenes = tile.getElementsByTagName("image");
            if (imagenes.getLength() == 0) continue;

            int id = Integer.parseInt(tile.getAttribute("id"));
            String ruta = ((Element) imagenes.item(0)).getAttribute("source");
            tiles.put(primerGid + id, Dibujo.imagen(CARPETA + ruta));
        }
    }

    private static void dibujarImagenDeFondo(Graphics2D g, Element capa) throws Exception {
        Element imagen = (Element) capa.getElementsByTagName("image").item(0);
        BufferedImage fondo = Dibujo.imagen(CARPETA + imagen.getAttribute("source"));
        g.drawImage(fondo, (int) numero(capa, "offsetx"), (int) numero(capa, "offsety"), null);
    }

    private static void dibujarCapaDeTiles(Graphics2D g, Element capa, Map<Integer, BufferedImage> tiles,
                                           int columnas, int anchoTile, int altoTile) {
        String datos = capa.getElementsByTagName("data").item(0).getTextContent();
        String[] valores = datos.trim().split("\\s*,\\s*");

        for (int i = 0; i < valores.length; i++) {
            long gid = Long.parseLong(valores[i].trim());
            if (gid == 0) continue;

            BufferedImage imagen = tiles.get((int) (gid & MASCARA_ID));
            if (imagen == null) continue;

            int x = (i % columnas) * anchoTile;
            int y = (i / columnas) * altoTile + altoTile - imagen.getHeight(); // se apoya abajo a la izquierda de su casilla
            int ancho = imagen.getWidth();
            int alto = imagen.getHeight();

            boolean espejoH = (gid & MASCARA_ESPEJO_HORIZONTAL) != 0;
            boolean espejoV = (gid & MASCARA_ESPEJO_VERTICAL) != 0;
            g.drawImage(imagen,
                espejoH ? x + ancho : x, espejoV ? y + alto : y,
                espejoH ? x : x + ancho, espejoV ? y : y + alto,
                0, 0, ancho, alto, null);
        }
    }

    private static double numero(Element elemento, String atributo) {
        String valor = elemento.getAttribute(atributo);
        return valor.isEmpty() ? 0 : Double.parseDouble(valor);
    }
}
