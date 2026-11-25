package Servidor;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;

public class HiloServer extends Thread {

    public static class Mensaje {
        private final DireccionRed origen;
        private final String texto;

        Mensaje(DireccionRed origen, String texto) {
            this.origen = origen;
            this.texto = texto;
        }

        public DireccionRed getOrigen() {
            return origen;
        }

        public String getTexto() {
            return texto;
        }
    }

    private static final int MAXIMO_EN_COLA = 2000;

    private final DatagramSocket socket;
    private final ConcurrentLinkedQueue<Mensaje> recibidos = new ConcurrentLinkedQueue<>();
    private volatile boolean fin = false;

    public HiloServer(int puerto) throws SocketException {
        super("Hilo-Receptor");
        socket = new DatagramSocket(puerto);
    }

    @Override
    public void run() {
        byte[] buffer = new byte[Protocolo.TAMANIO_PAQUETE];

        while (!fin) {
            DatagramPacket datagrama = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(datagrama);
            } catch (SocketException e) {
                if (!fin) {
                    System.out.println("Se cerro el socket: " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                System.out.println(" Error al recibir: " + e.getMessage());
                continue;
            }

            if (recibidos.size() > MAXIMO_EN_COLA) {
                continue;
            }


            String texto = new String(datagrama.getData(), 0, datagrama.getLength(), StandardCharsets.UTF_8).trim();
            recibidos.add(new Mensaje(new DireccionRed(datagrama.getAddress(), datagrama.getPort()), texto));
        }
    }

    public Mensaje sacarMensaje() {
        return recibidos.poll();
    }

    public void enviarMensaje(String mensaje, DireccionRed destino) {
        enviarMensaje(mensaje, destino.getIp(), destino.getPuerto());
    }

    public void enviarMensaje(String mensaje, InetAddress ip, int puerto) {
        if (socket.isClosed()) return;

        byte[] datos = mensaje.getBytes(StandardCharsets.UTF_8);
        try {
            socket.send(new DatagramPacket(datos, datos.length, ip, puerto));
        } catch (IOException e) {
            if (!fin) {
                System.out.println(" No se pudo enviar a " + ip.getHostAddress() + ": " + e.getMessage());
            }
        }
    }
    public void cerrar() {
        fin = true;
        socket.close();
    }
}
