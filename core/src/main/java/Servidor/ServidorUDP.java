package Servidor;

import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;


public class ServidorUDP {

    private final HiloServer hiloServer;
    private HiloJuego hiloJuego;
    private final AtomicBoolean cerrado = new AtomicBoolean(false);

    public ServidorUDP(int puerto) throws SocketException {
        hiloServer = new HiloServer(puerto);
    }

    public void iniciar() {
        hiloJuego = new HiloJuego(hiloServer);
        hiloServer.start();
        hiloJuego.start();
    }

    public int getJugadoresConectados() {
        if (hiloJuego == null) {
            return 0;
        } else {
            return hiloJuego.getJugadoresConectados();
        }
    }

    public boolean isPartidaEnCurso() {
        return hiloJuego != null && hiloJuego.isPartidaEnCurso();
    }

    public String getUltimoEstado() {
        if (hiloJuego == null) {
            return null;
        } else {
            return hiloJuego.getUltimoEstado();
        }
    }

    public boolean isCorriendo() {
        if (hiloJuego != null && hiloJuego.isCorriendo() && !cerrado.get()) {
            return true;
        } else {
            return false;
        }
    }


    public void esperar() throws InterruptedException {
        if (hiloJuego != null) {
            hiloJuego.join();
        }
    }

    public void cerrar() {
        if (!cerrado.compareAndSet(false, true)) return;

        System.out.println("Cerrando servidor.");

        if (hiloJuego != null) hiloJuego.detener(); // avisa cerrado a los clientes
        hiloServer.cerrar();
        try {
            hiloServer.join(1000);
            if (hiloJuego != null && Thread.currentThread() != hiloJuego) hiloJuego.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Servidor cerrado.");
    }

    /** El mundo del juego*/
    public Mundo.MundoServidor getMundo() {
        return hiloJuego == null ? null : hiloJuego.getMundo();
    }
}
