package Logica;

/** Etapas de una partida en el servidor. El numero (ordinal) es el que viaja por la red. */
public enum FasePartida {
    ESPERANDO,        // falta el segundo jugador
    CUENTA,           // 3, 2, 1 antes de cada ronda
    EN_CURSO,
    RONDA_TERMINADA,
    FINALIZADA        // alguien llego a 3 rondas: se espera la revancha
}
