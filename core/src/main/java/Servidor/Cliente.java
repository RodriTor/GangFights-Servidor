package Servidor;


public class Cliente {

    public static final int SALTO = 0;
    public static final int GOLPE = 1;
    public static final int APUNTAR = 2;
    public static final int DISPARO = 3;

    private final DireccionRed direccionRed;
    private final int[] ultimosContadores = new int[4];
    private long ultimoMensaje = System.currentTimeMillis();
    private int ultimaSecuencia = -1;

    public Cliente(DireccionRed direccionRed) {
        this.direccionRed = direccionRed;
    }

    public DireccionRed getDireccionRed() {
        return direccionRed;
    }

    /* Se llama con cualquier mensaje que llegue de este cliente: sirve para detectar que se desconecto. */
    public void registrarMensaje() {
        ultimoMensaje = System.currentTimeMillis();
    }

    public long getUltimoMensaje() {
        return ultimoMensaje;
    }

    public int getUltimaSecuencia() {
        return ultimaSecuencia;
    }

    public void setUltimaSecuencia(int secuencia) {
        this.ultimaSecuencia = secuencia;
    }

    public int getUltimoContador(int cual) {
        return ultimosContadores[cual];
    }

    public void setUltimoContador(int cual, int valor) {
        ultimosContadores[cual] = valor;
    }
}
