/* =========================================================================
 * Proyecto: EventGo - Sistema Centralizado de Gestión de Eventos
 * Autores:
 *  - Bernardo Velasco Ortega | Matrícula: AL07127004
 *  - Andrea Lizeth Sanchez Gomez
 * ========================================================================= */

public class Asiento {
    private String id;
    private int fila;
    private int columna;
    private boolean esVIP;
    private boolean ocupado;

    public Asiento(String id, int fila, int columna, boolean esVIP) {
        this.id = id;
        this.fila = fila;
        this.columna = columna;
        this.esVIP = esVIP;
        this.ocupado = false;
    }

    public String getId() { return id; }
    public int getFila() { return fila; }
    public int getColumna() { return columna; }
    public boolean isEsVIP() { return esVIP; }
    public void setEsVIP(boolean esVIP) { this.esVIP = esVIP; }
    public boolean isOcupado() { return ocupado; }
    public void setOcupado(boolean ocupado) { this.ocupado = ocupado; }
}