package Servidor;

import Logica.PartidaServidor;
import Mundo.JugadorServidor;
import Mundo.MundoServidor;

public class HiloJuego extends Thread {

    private static final long PASO_NANOS = 1_000_000_000L / 60L;
    private static final int PASOS_ENTRE_ESTADOS = 2;
    private static final int VECES_QUE_SE_AVISA_EL_CIERRE = 3;

    private final HiloServer red;
    private final MundoServidor mundo;
    private final PartidaServidor partida;
    private final Cliente[] clientes = new Cliente[Protocolo.JUGADORES_POR_PARTIDA];

    private volatile boolean corriendo = true;

    private volatile int jugadoresVisibles = 0;
    private volatile boolean partidaEnCurso = false;
    private volatile String ultimoEstado = null;
    private int secuenciaEstado = 0;

    /* Crea el mundo fisico (Box2D tiene que estar inicializado */
    public HiloJuego(HiloServer red) {
        super("Hilo-Juego");
        this.red = red;
        this.mundo = new MundoServidor();
        this.partida = new PartidaServidor(() -> mundo.reiniciarRonda());
    }

    public MundoServidor getMundo() {
        return mundo;
    }

    public int getJugadoresConectados() {
        return jugadoresVisibles;
    }

    public boolean isPartidaEnCurso() {
        return partidaEnCurso;
    }

    public String getUltimoEstado() {
        return ultimoEstado;
    }

    public boolean isCorriendo() {
        return corriendo;
    }

    /* Avisa a los clientes que el servidor se apaga y hace que el bucle termine. */
    public void detener() {
        // Se avisa varias veces porque UDP puede perder mensajes
        for (int i = 0; i < VECES_QUE_SE_AVISA_EL_CIERRE; i++) {
            for (Cliente c : clientes) {
                if (c != null) red.enviarMensaje(Protocolo.CERRADO, c.getDireccionRed());
            }
        }
        corriendo = false;
    }

    @Override
    public void run() {
        long proximoPaso = System.nanoTime();
        int contadorPasos = 0;

        try {
            while (corriendo) {
                procesarMensajes();
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
            System.out.println(" Error en el bucle del juego: " + e);
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

    /** Atiende todos los mensajes que el hilo de red dejo en la cola desde el paso anterior. */
    private void procesarMensajes() {
        HiloServer.Mensaje mensaje;
        while ((mensaje = red.sacarMensaje()) != null) {
            procesar(mensaje);
        }
    }

    private void procesar(HiloServer.Mensaje mensaje) {
        String[] partes = mensaje.getTexto().split(Protocolo.SEPARADOR);
        DireccionRed origen = mensaje.getOrigen();
        int indice = buscarCliente(origen);

        if (indice >= 0) {
            clientes[indice].registrarMensaje();
        }

        switch (partes[0]) {
            case Protocolo.BUSCAR:
                red.enviarMensaje(Protocolo.SERVIDOR + ";" + contarClientes() + ";" + Protocolo.JUGADORES_POR_PARTIDA, origen);
                break;

            case Protocolo.CONECTAR:
                conectar(origen, indice);
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
                break;
        }
    }

    private void conectar(DireccionRed origen, int indiceActual) {
        if (indiceActual >= 0) {
            red.enviarMensaje(Protocolo.JUGADOR + ";" + (indiceActual + 1), origen);
            return;
        }

        for (int i = 0; i < clientes.length; i++) {
            if (clientes[i] == null) {
                clientes[i] = new Cliente(origen);
                mundo.getJugador(i).limpiarEntrada();
                red.enviarMensaje(Protocolo.JUGADOR + ";" + (i + 1), origen);
                System.out.println("Jugador " + (i + 1) + " conectado desde " + origen);
                return;
            }
        }

        red.enviarMensaje(Protocolo.LLENO, origen);
        System.out.println("Conexion rechazada (partida llena): " + origen.getIp().getHostAddress());
    }

    private void procesarEntrada(int indice, String[] partes) {
        if (partes.length < 10) return;

        try {
            Cliente cliente = clientes[indice];
            int secuencia = Integer.parseInt(partes[1]);

            // UDP puede entregar un mensaje viejo despues de uno nuevo: se descarta
            if (secuencia <= cliente.getUltimaSecuencia()) return;
            cliente.setUltimaSecuencia(secuencia);

            JugadorServidor jugador = mundo.getJugador(indice);
            jugador.setEntrada(partes[2].equals("1"), partes[3].equals("1"),
                partes[4].equals("1"), partes[5].equals("1"));

            // El cliente cuenta cuantas veces apreto saltar, golpear, apuntar y disparar. Si el numero subio,
            // hubo una pulsacion nueva, aunque se haya perdido el mensaje donde se apreto.
            int contadorSalto = Integer.parseInt(partes[6]);
            int contadorGolpe = Integer.parseInt(partes[7]);
            int contadorApuntar = Integer.parseInt(partes[8]);
            int contadorDisparo = Integer.parseInt(partes[9]);

            if (contadorSalto > cliente.getUltimoContador(Cliente.SALTO)) jugador.pedirSalto();
            if (contadorGolpe > cliente.getUltimoContador(Cliente.GOLPE)) jugador.pedirGolpe();
            if (contadorApuntar > cliente.getUltimoContador(Cliente.APUNTAR)) jugador.pedirApuntar();
            if (contadorDisparo > cliente.getUltimoContador(Cliente.DISPARO)) jugador.pedirDisparo();

            cliente.setUltimoContador(Cliente.SALTO, contadorSalto);
            cliente.setUltimoContador(Cliente.GOLPE, contadorGolpe);
            cliente.setUltimoContador(Cliente.APUNTAR, contadorApuntar);
            cliente.setUltimoContador(Cliente.DISPARO, contadorDisparo);

        } catch (NumberFormatException e) {
        }
    }

    private void revisarDesconexiones() {
        long ahora = System.currentTimeMillis();

        for (int i = 0; i < clientes.length; i++) {
            if (clientes[i] != null && ahora - clientes[i].getUltimoMensaje() > Protocolo.TIMEOUT_MS) {
                quitarCliente(i, "se desconecto (sin respuesta)");
            }
        }
    }

    private void quitarCliente(int indice, String motivo) {
        clientes[indice] = null;
        mundo.getJugador(indice).limpiarEntrada();
        System.out.println(" Jugador " + (indice + 1) + " " + motivo);
    }

    private int buscarCliente(DireccionRed origen) {
        for (int i = 0; i < clientes.length; i++) {
            if (clientes[i] != null && clientes[i].getDireccionRed().esIgualA(origen)) return i;
        }
        return -1;
    }

    private int contarClientes() {
        int cantidad = 0;
        for (Cliente c : clientes) {
            if (c != null) cantidad++;
        }
        return cantidad;
    }



    private void enviarEstado() {
        String estado = construirEstado();
        ultimoEstado = estado;

        if (contarClientes() == 0) return;

        for (Cliente c : clientes) {
            if (c != null) red.enviarMensaje(estado, c.getDireccionRed());
        }
    }


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
}
