package Servidor;

import Logica.PartidaServidor;
import Mundo.JugadorServidor;
import Mundo.MundoServidor;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * El servidor del juego. Usa un unico socket UDP y dos hilos:
 *
 *  - Hilo receptor: solo lee datagramas y los deja en una cola. Nunca toca el juego.
 *  - Hilo del juego: 60 veces por segundo atiende los mensajes de la cola, avanza la fisica
 *    y la partida, revisa desconexiones y (30 veces por segundo) manda el ESTADO a los clientes.
 *
 * Como solo el hilo del juego toca Box2D y los datos de la partida, no hacen falta "synchronized".
 * UDP puede perder o desordenar mensajes, por eso el cliente manda su estado completo todo el tiempo
 * (no solo los cambios) y el servidor manda "fotos" completas del juego.
 */
public class ServidorUDP {

    private static final long PASO_NANOS = 1_000_000_000L / 60L;
    private static final int PASOS_ENTRE_ESTADOS = 2;
    private static final int VECES_QUE_SE_AVISA_EL_CIERRE = 3;

    /** Un datagrama recibido, con quien lo mando. */
    private static class Paquete {
        final InetAddress direccion;
        final int puerto;
        final String texto;

        Paquete(InetAddress direccion, int puerto, String texto) {
            this.direccion = direccion;
            this.puerto = puerto;
            this.texto = texto;
        }
    }

    /** Un cliente conectado a la partida. */
    private static class Conexion {
        final InetAddress direccion;
        final int puerto;
        long ultimoMensaje = System.currentTimeMillis();
        int ultimaSecuencia = -1;
        int ultimoContadorSalto = 0;
        int ultimoContadorGolpe = 0;
        int ultimoContadorApuntar = 0;
        int ultimoContadorDisparo = 0;

        Conexion(InetAddress direccion, int puerto) {
            this.direccion = direccion;
            this.puerto = puerto;
        }

        boolean esDe(Paquete paquete) {
            return direccion.equals(paquete.direccion) && puerto == paquete.puerto;
        }
    }

    private final DatagramSocket socket;
    private final ConcurrentLinkedQueue<Paquete> recibidos = new ConcurrentLinkedQueue<>();
    private final Conexion[] clientes = new Conexion[Protocolo.JUGADORES_POR_PARTIDA];
    private final AtomicBoolean cerrado = new AtomicBoolean(false);

    private MundoServidor mundo;
    private PartidaServidor partida;
    private Thread hiloReceptor;
    private Thread hiloJuego;
    private volatile boolean corriendo = false;

    // Copias para que la ventana del servidor (otro hilo) pueda leer sin tocar los datos del juego
    private volatile int jugadoresVisibles = 0;
    private volatile boolean partidaEnCurso = false;
    private volatile String ultimoEstado = null;
    private int secuenciaEstado = 0;

    /** @throws SocketException (BindException) si el puerto ya esta ocupado */
    public ServidorUDP(int puerto) throws SocketException {
        socket = new DatagramSocket(puerto);
    }

    public void iniciar() {
        mundo = new MundoServidor();
        partida = new PartidaServidor(() -> mundo.reiniciarRonda());
        corriendo = true;

        hiloReceptor = new Thread(this::recibir, "Hilo-Receptor");
        hiloJuego = new Thread(this::bucleDeJuego, "Hilo-Juego");
        hiloReceptor.start();
        hiloJuego.start();
    }

    /** Cuantos jugadores hay conectados (0, 1 o 2). Se puede llamar desde cualquier hilo. */
    public int getJugadoresConectados() {
        return jugadoresVisibles;
    }

    /** true si hay una partida andando (los dos jugadores adentro). */
    public boolean isPartidaEnCurso() {
        return partidaEnCurso;
    }

    /**
     * La ultima "foto" del juego (el mismo texto ESTADO que se manda a los clientes). Es un String, o sea que no
     * se puede modificar: la ventana del servidor lo lee desde su hilo sin tocar nada del juego. null si todavia no hay.
     */
    public String getUltimoEstado() {
        return ultimoEstado;
    }

    public boolean isCorriendo() {
        return corriendo && !cerrado.get();
    }

    /** Espera a que termine el hilo del juego (sirve para que el programa no se cierre). */
    public void esperar() throws InterruptedException {
        if (hiloJuego != null) {
            hiloJuego.join();
        }
    }


    // ---------------------------------------------------------------- Recepcion

    private void recibir() {
        byte[] buffer = new byte[Protocolo.TAMANIO_PAQUETE];

        while (corriendo) {
            DatagramPacket datagrama = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(datagrama);
            } catch (SocketException e) {
                if (corriendo) {
                    System.out.println("[SERVIDOR] Se cerro el socket: " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                System.out.println("[SERVIDOR] Error al recibir: " + e.getMessage());
                continue;
            }

            // Evita que un cliente que manda demasiado llene la memoria
            if (recibidos.size() > 2000) {
                continue;
            }

            String texto = new String(datagrama.getData(), 0, datagrama.getLength(), StandardCharsets.UTF_8).trim();
            recibidos.add(new Paquete(datagrama.getAddress(), datagrama.getPort(), texto));
        }
    }

    private void enviar(InetAddress direccion, int puerto, String mensaje) {
        if (socket.isClosed()) return;

        byte[] datos = mensaje.getBytes(StandardCharsets.UTF_8);
        try {
            socket.send(new DatagramPacket(datos, datos.length, direccion, puerto));
        } catch (IOException e) {
            if (corriendo) {
                System.out.println("[SERVIDOR] No se pudo enviar a " + direccion + ": " + e.getMessage());
            }
        }
    }


    // ---------------------------------------------------------------- Bucle del juego

    private void bucleDeJuego() {
        long proximoPaso = System.nanoTime();
        int contadorPasos = 0;

        try {
            while (corriendo) {
                procesarPaquetes();
                revisarDesconexiones();

                jugadoresVisibles = contarClientes();
                partidaEnCurso = partida.getFase() != Logica.FasePartida.ESPERANDO;

                partida.actualizar(1f / 60f, contarClientes(),
                    mundo.getJugador(0).isActivo(), mundo.getJugador(1).isActivo());
                mundo.actualizar(partida.puedenMoverse(), partida.hayCombate());

                contadorPasos++;
                if (contadorPasos % PASOS_ENTRE_ESTADOS == 0) {
                    enviarEstado();
                }

                proximoPaso += PASO_NANOS;
                long espera = proximoPaso - System.nanoTime();

                if (espera > 0) {
                    dormir(espera);
                } else if (espera < -10 * PASO_NANOS) {
                    proximoPaso = System.nanoTime(); // la PC se atraso mucho: no intentar recuperar todo
                }
            }
        } catch (RuntimeException e) {
            System.out.println("[SERVIDOR] Error en el bucle del juego: " + e);
            e.printStackTrace();
        } finally {
            mundo.dispose();
        }
    }

    private void dormir(long nanos) {
        try {
            Thread.sleep(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            corriendo = false;
        }
    }

    private void procesarPaquetes() {
        Paquete paquete;
        while ((paquete = recibidos.poll()) != null) {
            procesar(paquete);
        }
    }

    private void procesar(Paquete paquete) {
        String[] partes = paquete.texto.split(Protocolo.SEPARADOR);
        int indice = buscarCliente(paquete);

        if (indice >= 0) {
            clientes[indice].ultimoMensaje = System.currentTimeMillis();
        }

        switch (partes[0]) {
            case Protocolo.BUSCAR:
                enviar(paquete.direccion, paquete.puerto,
                    Protocolo.SERVIDOR + ";" + contarClientes() + ";" + Protocolo.JUGADORES_POR_PARTIDA);
                break;

            case Protocolo.CONECTAR:
                conectar(paquete, indice);
                break;

            case Protocolo.ENTRADA:
                if (indice >= 0) procesarEntrada(indice, partes);
                break;

            case Protocolo.REVANCHA:
                if (indice >= 0) partida.pedirRevancha(indice);
                break;

            case Protocolo.SALIR:
                if (indice >= 0) quitarCliente(indice, "se fue");
                break;

            default:
                break; // LATIDO u otro mensaje: alcanza con haber actualizado ultimoMensaje
        }
    }

    private void conectar(Paquete paquete, int indiceActual) {
        // Si el mensaje CONECTAR se repitio (se perdio la respuesta), se contesta lo mismo
        if (indiceActual >= 0) {
            enviar(paquete.direccion, paquete.puerto, Protocolo.JUGADOR + ";" + (indiceActual + 1));
            return;
        }

        for (int i = 0; i < clientes.length; i++) {
            if (clientes[i] == null) {
                clientes[i] = new Conexion(paquete.direccion, paquete.puerto);
                mundo.getJugador(i).limpiarEntrada();
                enviar(paquete.direccion, paquete.puerto, Protocolo.JUGADOR + ";" + (i + 1));
                System.out.println("[SERVIDOR] Jugador " + (i + 1) + " conectado desde "
                    + paquete.direccion.getHostAddress() + ":" + paquete.puerto);
                return;
            }
        }

        enviar(paquete.direccion, paquete.puerto, Protocolo.LLENO);
        System.out.println("[SERVIDOR] Conexion rechazada (partida llena): " + paquete.direccion.getHostAddress());
    }

    /** ENTRADA;seq;izquierda;derecha;agachar;arriba;contadorSalto;contadorGolpe;contadorApuntar;contadorDisparo */
    private void procesarEntrada(int indice, String[] partes) {
        if (partes.length < 10) return;

        try {
            Conexion conexion = clientes[indice];
            int secuencia = Integer.parseInt(partes[1]);

            // UDP puede entregar un mensaje viejo despues de uno nuevo: se descarta
            if (secuencia <= conexion.ultimaSecuencia) return;
            conexion.ultimaSecuencia = secuencia;

            JugadorServidor jugador = mundo.getJugador(indice);
            jugador.setEntrada(partes[2].equals("1"), partes[3].equals("1"),
                partes[4].equals("1"), partes[5].equals("1"));

            // El cliente cuenta cuantas veces apreto saltar, golpear, apuntar y disparar. Si el numero subio,
            // hubo una pulsacion nueva, aunque se haya perdido el mensaje donde se apreto.
            int contadorSalto = Integer.parseInt(partes[6]);
            int contadorGolpe = Integer.parseInt(partes[7]);
            int contadorApuntar = Integer.parseInt(partes[8]);
            int contadorDisparo = Integer.parseInt(partes[9]);

            if (contadorSalto > conexion.ultimoContadorSalto) jugador.pedirSalto();
            if (contadorGolpe > conexion.ultimoContadorGolpe) jugador.pedirGolpe();
            if (contadorApuntar > conexion.ultimoContadorApuntar) jugador.pedirApuntar();
            if (contadorDisparo > conexion.ultimoContadorDisparo) jugador.pedirDisparo();

            conexion.ultimoContadorSalto = contadorSalto;
            conexion.ultimoContadorGolpe = contadorGolpe;
            conexion.ultimoContadorApuntar = contadorApuntar;
            conexion.ultimoContadorDisparo = contadorDisparo;

        } catch (NumberFormatException e) {
            // Mensaje mal formado: se ignora
        }
    }

    private void revisarDesconexiones() {
        long ahora = System.currentTimeMillis();

        for (int i = 0; i < clientes.length; i++) {
            if (clientes[i] != null && ahora - clientes[i].ultimoMensaje > Protocolo.TIMEOUT_MS) {
                quitarCliente(i, "se desconecto (sin respuesta)");
            }
        }
    }

    private void quitarCliente(int indice, String motivo) {
        clientes[indice] = null;
        mundo.getJugador(indice).limpiarEntrada();
        System.out.println("[SERVIDOR] Jugador " + (indice + 1) + " " + motivo);
    }

    private int buscarCliente(Paquete paquete) {
        for (int i = 0; i < clientes.length; i++) {
            if (clientes[i] != null && clientes[i].esDe(paquete)) return i;
        }
        return -1;
    }

    private int contarClientes() {
        int cantidad = 0;
        for (Conexion c : clientes) {
            if (c != null) cantidad++;
        }
        return cantidad;
    }


    // ---------------------------------------------------------------- Estado

    private void enviarEstado() {
        String estado = construirEstado();
        ultimoEstado = estado; // para la ventana del servidor (se actualiza aunque no haya clientes)

        if (contarClientes() == 0) return;

        for (Conexion c : clientes) {
            if (c != null) enviar(c.direccion, c.puerto, estado);
        }
    }

    /**
     * ESTADO;seq;fase;ronda;ganadorRonda;rondas1;rondas2;conectados;revanchas;cuenta;jugador1;jugador2;botiquin;pistola;balas
     *   jugador = x,y,vida,animacion,mirandoDerecha,pistola,apuntando,anguloMira,rojo   (todo en numeros enteros)
     *   botiquin / pistola = x,base   o   "-" si no hay
     *   balas = x,y,angulo/x,y,angulo/...   o   "-" si no hay
     *   conectados y revanchas son mascaras de bits: bit 0 = jugador 1, bit 1 = jugador 2
     */
    private String construirEstado() {
        StringBuilder estado = new StringBuilder(Protocolo.ESTADO);

        estado.append(';').append(secuenciaEstado++);
        estado.append(';').append(partida.getFase().ordinal());
        estado.append(';').append(partida.getRonda());
        estado.append(';').append(partida.getGanadorRonda());
        estado.append(';').append(partida.getRondasGanadas(0));
        estado.append(';').append(partida.getRondasGanadas(1));
        estado.append(';').append(mascaraConectados());
        estado.append(';').append(partida.getMascaraRevanchas());
        estado.append(';').append(partida.getSegundosCuenta());
        estado.append(';').append(jugadorComoTexto(mundo.getJugador(0)));
        estado.append(';').append(jugadorComoTexto(mundo.getJugador(1)));
        estado.append(';').append(mundo.getItems().botiquinComoTexto());
        estado.append(';').append(mundo.getItems().pistolaComoTexto());
        estado.append(';').append(mundo.getBalas().comoTexto());

        return estado.toString();
    }

    private String jugadorComoTexto(JugadorServidor jugador) {
        return Math.round(jugador.getX()) + ","
            + Math.round(jugador.getY()) + ","
            + jugador.getVida() + ","
            + jugador.getAnimacion().ordinal() + ","
            + (jugador.estaMirandoDerecha() ? 1 : 0) + ","
            + (jugador.tienePistola() ? 1 : 0) + ","
            + (jugador.estaApuntando() ? 1 : 0) + ","
            + Math.round(jugador.getAnguloMira()) + ","
            + (jugador.estaRojo() ? 1 : 0);
    }

    private int mascaraConectados() {
        return (clientes[0] != null ? 1 : 0) | (clientes[1] != null ? 2 : 0);
    }


    // ---------------------------------------------------------------- Cierre

    /** Avisa a los clientes, detiene los hilos y libera todo. Se puede llamar mas de una vez. */
    public void cerrar() {
        if (!cerrado.compareAndSet(false, true)) return;

        System.out.println("[SERVIDOR] Cerrando servidor...");

        // Se avisa varias veces porque UDP puede perder mensajes
        for (int i = 0; i < VECES_QUE_SE_AVISA_EL_CIERRE; i++) {
            for (Conexion c : clientes) {
                if (c != null) enviar(c.direccion, c.puerto, Protocolo.CERRADO);
            }
        }

        corriendo = false;
        socket.close(); // saca al hilo receptor del receive()

        try {
            if (hiloReceptor != null) hiloReceptor.join(1000);
            if (hiloJuego != null && Thread.currentThread() != hiloJuego) hiloJuego.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("[SERVIDOR] Servidor cerrado.");
    }
}
