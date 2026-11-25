package Servidor;

import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.utils.GdxNativesLoader;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

/**
 * Programa del servidor. Se ejecuta con:   java -jar GangFights-Servidor.jar
 * Para cerrarlo: cerrar la ventana, o escribir "salir" y ENTER (o Ctrl+C). Los clientes reciben el aviso.
 */
public class ServidorPrincipal {

    public static void main(String[] args) {
        System.out.println("=== GANGFIGHTS - SERVIDOR ===");

        ServidorUDP servidor;

        try {
            servidor = new ServidorUDP(Protocolo.PUERTO);
        } catch (BindException e) {
            System.out.println("el puerto UDP " + Protocolo.PUERTO + " ya esta en uso.");
            VentanaServidor.mostrarError("El puerto UDP " + Protocolo.PUERTO + " ya esta en uso.");
            return;
        } catch (SocketException e) {
            System.out.println("no se pudo abrir el puerto " + Protocolo.PUERTO);
            VentanaServidor.mostrarError("No se pudo abrir el puerto " + Protocolo.PUERTO);
            return;
        }

        try {
            GdxNativesLoader.load();
            Box2D.init();
            servidor.iniciar();
        } catch (Throwable e) {
            System.out.println("error al iniciar el mundo fisico: " + e);
            e.printStackTrace();
            servidor.cerrar();
            VentanaServidor.mostrarError("Error al iniciar el mundo fisico:\n" + e);
            return;
        }

        ServidorUDP servidorFinal = servidor;
        Runtime.getRuntime().addShutdownHook(new Thread(servidorFinal::cerrar, "Cierre-Servidor"));

        System.out.println("Servidor listo. Puerto UDP " + Protocolo.PUERTO + ". Direcciones de esta computadora:");
        mostrarDirecciones();

        if (!GraphicsEnvironment.isHeadless()) {
            VentanaServidor.abrir(servidor, Protocolo.PUERTO, () -> {
                servidorFinal.cerrar();
                System.exit(0);
            });
        }

        esperarOrdenDeCierre();
        servidor.cerrar();
    }

    private static void esperarOrdenDeCierre() {
        try {
            BufferedReader consola = new BufferedReader(new InputStreamReader(System.in));
            String linea;
            while ((linea = consola.readLine()) != null) {
                if (linea.trim().equalsIgnoreCase("salir")) {
                    return;
                }
            }
        } catch (IOException e) {
        }

        // No hay entrada por consola: se queda funcionando hasta que se cierre el proceso
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void mostrarDirecciones() {
        try {
            for (NetworkInterface red : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!red.isUp() || red.isLoopback()) continue;

                for (InetAddress direccion : Collections.list(red.getInetAddresses())) {
                    if (direccion.getAddress().length == 4) {
                        System.out.println("  - " + direccion.getHostAddress() + "  (" + red.getDisplayName() + ")");
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("  (no se pudieron leer las direcciones)");
        }
    }
}
