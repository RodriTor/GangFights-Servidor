package Mundo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/*
 Lee el mapa de Tiled (Aula.tmx) y devuelve los rectangulos de la capa "Colisiones".
 */
public class CargadorColisiones {

    private static final String NOMBRE_MAPA = "Aula.tmx";
    private static final String CAPA_COLISIONES = "Colisiones";

    private static final String[] RUTAS_ALTERNATIVAS = {
        "../assets/Mapas/Aula.tmx",
        "assets/Mapas/Aula.tmx"
    };

    public static List<float[]> cargar() {
        try (InputStream entrada = abrirMapa()) {
            return leerColisiones(entrada);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar el mapa " + NOMBRE_MAPA, e);
        }
    }

    private static InputStream abrirMapa() throws IOException {
        InputStream dentroDelJar = CargadorColisiones.class.getResourceAsStream("/Mapas/" + NOMBRE_MAPA);
        if (dentroDelJar != null) {
            return dentroDelJar;
        }

        for (String ruta : RUTAS_ALTERNATIVAS) {
            File archivo = new File(ruta);
            if (archivo.exists()) {
                return new FileInputStream(archivo);
            }
        }
        throw new IOException("No se encontro " + NOMBRE_MAPA);
    }

    private static List<float[]> leerColisiones(InputStream entrada) throws Exception {
        List<float[]> colisiones = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document documento = builder.parse(entrada);

        Element mapa = documento.getDocumentElement();
        float altoMapa = Float.parseFloat(mapa.getAttribute("height"))
            * Float.parseFloat(mapa.getAttribute("tileheight"));

        NodeList capas = documento.getElementsByTagName("objectgroup");

        for (int i = 0; i < capas.getLength(); i++) {
            Element capa = (Element) capas.item(i);

            if (!capa.getAttribute("name").equals(CAPA_COLISIONES)) {
                continue;
            }

            NodeList objetos = capa.getElementsByTagName("object");

            for (int j = 0; j < objetos.getLength(); j++) {
                Element objeto = (Element) objetos.item(j);

                // Solo los rectangulos tienen ancho y alto
                if (!objeto.hasAttribute("width") || !objeto.hasAttribute("height")) {
                    continue;
                }

                float x = Float.parseFloat(objeto.getAttribute("x"));
                float y = Float.parseFloat(objeto.getAttribute("y"));
                float ancho = Float.parseFloat(objeto.getAttribute("width"));
                float alto = Float.parseFloat(objeto.getAttribute("height"));

                // Tiled mide desde arriba; Box2D desde abajo.
                colisiones.add(new float[] {x, altoMapa - y - alto, ancho, alto});
            }
        }

        System.out.println("[SERVIDOR] Colisiones cargadas: " + colisiones.size());
        return colisiones;
    }
}
