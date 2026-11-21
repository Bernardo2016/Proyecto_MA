/* =========================================================================
 * Proyecto: EventGo - Sistema Centralizado de Gestión de Eventos
 * Autores:
 *  - Bernardo Velasco Ortega | Matrícula: AL07127004
 *  - Andrea Lizeth Sanchez Gomez | Matrícula: Al03039306
 * ========================================================================= */

import java.text.SimpleDateFormat;
import java.util.Date;

class Boleto implements Mostrable {
    protected int idBoleto;
    protected String idGrupo;
    protected Invitado invitado;
    protected double precio;
    protected boolean pagado;
    protected boolean yaEntro;
    protected Asiento asiento;
    protected String fechaVenta;

    public Boleto(int idBoleto, String idGrupo, Invitado invitado, double precio, Asiento asiento) {
        this.idBoleto = idBoleto;
        this.idGrupo = idGrupo;
        this.invitado = invitado;
        this.precio = precio;
        this.asiento = asiento;
        this.pagado = false;
        this.yaEntro = false;
        this.fechaVenta = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
    }

    public int getIdBoleto() { return idBoleto; }
    public String getIdGrupo() { return idGrupo; }
    public Invitado getInvitado() { return invitado; }
    public double getPrecio() { return precio; }
    public boolean isPagado() { return pagado; }
    public void setPagado(boolean pagado) { this.pagado = pagado; }
    public boolean isYaEntro() { return yaEntro; }
    public void setYaEntro(boolean yaEntro) { this.yaEntro = yaEntro; }
    public void registrarEntrada() { this.yaEntro = true; }
    public Asiento getAsiento() { return asiento; }
    public String getFechaVenta() { return fechaVenta; }
    public void setFechaVenta(String fechaVenta) { this.fechaVenta = fechaVenta; }

    public String getTipo() { return "GENERAL"; }

    @Override
    public String obtenerInformacion() {
        return "Folio #" + idBoleto + " [" + idGrupo + "] | Titular: " + invitado.obtenerInformacion() +
               " | Asiento: " + asiento.getId() + " | Estado: " + (pagado ? "PAGADO" : "PENDIENTE");
    }
}

class BoletoGeneral extends Boleto {
    private String zona;

    public BoletoGeneral(int idBoleto, String idGrupo, Invitado invitado, double precio, Asiento asiento, String zona) {
        super(idBoleto, idGrupo, invitado, precio, asiento);
        this.zona = zona;
    }

    public String getZona() { return zona; }

    @Override
    public String getTipo() { return "GENERAL"; }

    @Override
    public String obtenerInformacion() {
        return super.obtenerInformacion() + " | Tipo: GENERAL (" + zona + ")";
    }
}

class BoletoVIP extends Boleto {
    private String beneficios;

    public BoletoVIP(int idBoleto, String idGrupo, Invitado invitado, double precio, Asiento asiento, String beneficios) {
        super(idBoleto, idGrupo, invitado, precio, asiento);
        this.beneficios = beneficios;
    }

    public String getBeneficios() { return beneficios; }

    @Override
    public String getTipo() { return "VIP"; }

    @Override
    public String obtenerInformacion() {
        return super.obtenerInformacion() + " | Tipo: VIP (" + beneficios + ")";
    }
}

class Pago {
    private double totalPagar;
    private double montoPagado;

    public Pago(double totalPagar, double montoPagado) {
        this.totalPagar = totalPagar;
        this.montoPagado = montoPagado;
    }

    public double procesar() throws IllegalArgumentException {
        if (montoPagado < totalPagar) {
            throw new IllegalArgumentException("Monto insuficiente. Faltan $" + String.format("%.2f", (totalPagar - montoPagado)));
        }
        return montoPagado - totalPagar;
    }
}