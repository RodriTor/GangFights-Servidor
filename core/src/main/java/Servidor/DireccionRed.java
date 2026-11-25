package Servidor;

import java.net.InetAddress;

public class DireccionRed {

    private final InetAddress ip;
    private final int puerto;

    public DireccionRed(InetAddress ip, int puerto) {
        this.ip = ip;
        this.puerto = puerto;
    }

    public InetAddress getIp() {
        return ip;
    }

    public int getPuerto() {
        return puerto;
    }

    /** true si es exactamente la misma IP y el mismo puerto. */
    public boolean esIgualA(DireccionRed otra) {
        return ip.equals(otra.ip) && puerto == otra.puerto;
    }

    @Override
    public String toString() {
        return ip.getHostAddress() + ":" + puerto;
    }
}
