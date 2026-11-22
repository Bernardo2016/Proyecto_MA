/* =========================================================================
 * Proyecto: EventGo - Sistema Centralizado de Gestión de Eventos
 * Autores:
 *  - Bernardo Velasco Ortega | Matrícula: AL07127004
 *  - Andrea Lizeth Sanchez Gomez | Matrícula: Al03039306
 * ========================================================================= */

import java.util.ArrayList;
import java.util.List;

public class Evento {
    private String idEvento;
    private String nombreEvento;
    private String fechaEvento;
    private String recinto;
    private String categoria;
    private int numFilas;
    private int numColumnas;
    private int numFilasVIP;
    private double costoGeneral;
    private double costoVIP;

    private ArrayList<Asiento> asientos;
    private ArrayList<Boleto> listaBoletos;
    private int siguienteIdBoleto;
    private int siguienteIdGrupo;

    public Evento(String idEvento, String nombre, String fecha, String recinto, String categoria,
                  int filas, int columnas, int filasVIP, double cGen, double cVip) {
        this.idEvento = idEvento;
        this.asientos = new ArrayList<>();
        this.listaBoletos = new ArrayList<>();
        this.siguienteIdBoleto = 1;
        this.siguienteIdGrupo = 1;
        configurar(nombre, fecha, recinto, categoria, filas, columnas, filasVIP, cGen, cVip);
    }

    public void configurar(String nombre, String fecha, String recinto, String categoria,
                           int filas, int columnas, int filasVIP, double cGen, double cVip) {
        if (nombre == null || nombre.trim().isEmpty()) throw new IllegalArgumentException("El nombre del evento es obligatorio.");
        if (filas < 2 || filas > 15 || columnas < 2 || columnas > 20) throw new IllegalArgumentException("Dimensiones inválidas (Filas: 2-15, Columnas: 2-20).");
        if (filasVIP < 1 || filasVIP >= filas) throw new IllegalArgumentException("Las filas VIP deben ser al menos 1 y menores al total de filas (" + filas + ").");
        if (cGen <= 0 || cVip <= cGen) throw new IllegalArgumentException("El costo VIP debe ser estrictamente mayor al costo General.");

        this.nombreEvento = nombre.trim();
        this.fechaEvento = (fecha == null || fecha.trim().isEmpty()) ? "2026-12-01 20:00" : fecha.trim();
        this.recinto = (recinto == null || recinto.trim().isEmpty()) ? "Auditorio Principal" : recinto.trim();
        this.categoria = (categoria == null || categoria.trim().isEmpty()) ? "Concierto" : categoria.trim();
        this.numFilas = filas;
        this.numColumnas = columnas;
        this.numFilasVIP = filasVIP;
        this.costoGeneral = cGen;
        this.costoVIP = cVip;

        this.asientos.clear();
        this.listaBoletos.clear();
        this.siguienteIdBoleto = 1;
        this.siguienteIdGrupo = 1;

        char letraFila = 'A';
        for (int i = 0; i < filas; i++) {
            boolean filaEsVIP = (i + 1) <= filasVIP;
            for (int j = 1; j <= columnas; j++) {
                asientos.add(new Asiento(letraFila + "-" + j, i + 1, j, filaEsVIP));
            }
            letraFila++;
        }
    }

    public String registrarCompraGrupal(String nombreTitular, int edad, String correo, String tipo, List<String> idsAsientos) {
        if (nombreTitular == null || nombreTitular.trim().isEmpty()) throw new IllegalArgumentException("El nombre del titular es obligatorio.");
        if (edad < 12 || edad > 110) throw new IllegalArgumentException("Edad del titular no válida (12 a 110 años).");
        if (correo == null || !correo.contains("@")) throw new IllegalArgumentException("Ingresa un correo electrónico válido.");
        if (idsAsientos == null || idsAsientos.isEmpty() || idsAsientos.size() > 4) {
            throw new IllegalArgumentException("Por política de EventGo debes seleccionar entre 1 y 4 asientos por compra.");
        }

        boolean quiereVIP = "VIP".equalsIgnoreCase(tipo);
        List<Asiento> asientosValidados = new ArrayList<>();

        for (String idAsiento : idsAsientos) {
            Asiento a = buscarAsiento(idAsiento.trim());
            if (a == null) throw new IllegalArgumentException("El asiento " + idAsiento + " no existe.");
            if (a.isOcupado()) throw new IllegalArgumentException("El asiento " + idAsiento + " ya se encuentra ocupado.");
            if (quiereVIP && !a.isEsVIP()) {
                throw new IllegalArgumentException("Cláusula VIP: El asiento " + idAsiento + " no pertenece a las primeras " + numFilasVIP + " filas cercanas al escenario.");
            }
            if (!quiereVIP && a.isEsVIP()) {
                throw new IllegalArgumentException("Zona exclusiva: El asiento " + idAsiento + " es exclusivo para boletos VIP.");
            }
            if (asientosValidados.contains(a)) {
                throw new IllegalArgumentException("Asiento duplicado en la selección: " + idAsiento);
            }
            asientosValidados.add(a);
        }

        String idGrupo = "GRP-" + siguienteIdGrupo++;
        Invitado titular = new Invitado(nombreTitular.trim(), edad, correo.trim());
        double precioUnitario = quiereVIP ? costoVIP : costoGeneral;

        for (Asiento a : asientosValidados) {
            a.setOcupado(true);
            int idBoleto = siguienteIdBoleto++;
            Boleto nuevo = quiereVIP
                ? new BoletoVIP(idBoleto, idGrupo, titular, precioUnitario, a, "Primera Fila + Lounge")
                : new BoletoGeneral(idBoleto, idGrupo, titular, precioUnitario, a, "Gradas Generales");
            listaBoletos.add(nuevo);
        }

        double total = precioUnitario * asientosValidados.size();
        return "Compra registrada bajo folio grupal " + idGrupo + " (" + asientosValidados.size() + " boletos). Total a pagar: $" + String.format("%.2f", total);
    }

    public String procesarPago(String referencia, double montoRecibido) {
        List<Boleto> objetivos = buscarBoletosPorReferencia(referencia);
        if (objetivos.isEmpty()) throw new IllegalArgumentException("No se encontró ningún boleto o grupo con referencia: " + referencia);

        double totalPendiente = 0;
        int pendientes = 0;
        for (Boleto b : objetivos) {
            if (!b.isPagado()) {
                totalPendiente += b.getPrecio();
                pendientes++;
            }
        }
        if (pendientes == 0) throw new IllegalArgumentException("Los boletos de la referencia " + referencia + " ya están pagados.");

        Pago pago = new Pago(totalPendiente, montoRecibido);
        double cambio = pago.procesar();

        for (Boleto b : objetivos) {
            if (!b.isPagado()) b.setPagado(true);
        }
        return "Pago confirmado para " + pendientes + " boleto(s) [" + objetivos.get(0).getIdGrupo() + "]. Cambio devuelto: $" + String.format("%.2f", cambio);
    }

    public String registrarCheckInGrupal(String referencia) {
        List<Boleto> objetivos = buscarBoletosPorReferencia(referencia);
        if (objetivos.isEmpty()) throw new IllegalArgumentException("Folio o grupo no encontrado: " + referencia);

        for (Boleto b : objetivos) {
            if (!b.isPagado()) {
                throw new IllegalArgumentException("Acceso denegado: El grupo " + b.getIdGrupo() + " tiene boletos pendientes de pago.");
            }
        }

        int recienIngresados = 0;
        for (Boleto b : objetivos) {
            if (!b.isYaEntro()) {
                b.registrarEntrada();
                recienIngresados++;
            }
        }

        if (recienIngresados == 0) {
            throw new IllegalArgumentException("Los " + objetivos.size() + " asistentes del grupo " + objetivos.get(0).getIdGrupo() + " ya registraron su entrada previamente.");
        }
        return "ACCESO CONCEDIDO (" + objetivos.get(0).getIdGrupo() + "): Se registró el ingreso simultáneo de " + recienIngresados + " asistente(s) a nombre de " + objetivos.get(0).getInvitado().getNombre() + ".";
    }

    public String actualizarBoleto(int idBoleto, String nuevoNombre, int nuevaEdad, String nuevoCorreo) {
        Boleto b = buscarBoletoPorId(idBoleto);
        if (b == null) throw new IllegalArgumentException("Boleto #" + idBoleto + " no encontrado.");
        if (nuevoNombre == null || nuevoNombre.trim().isEmpty()) throw new IllegalArgumentException("El nombre no puede estar vacío.");
        if (nuevaEdad < 12 || nuevaEdad > 110) throw new IllegalArgumentException("Edad inválida.");
        if (nuevoCorreo == null || !nuevoCorreo.contains("@")) throw new IllegalArgumentException("Correo inválido.");

        b.getInvitado().setNombre(nuevoNombre.trim());
        b.getInvitado().setEdad(nuevaEdad);
        b.getInvitado().setCorreo(nuevoCorreo.trim());
        return "Boleto #" + idBoleto + " actualizado correctamente.";
    }

    public String eliminarBoleto(int idBoleto) {
        Boleto b = buscarBoletoPorId(idBoleto);
        if (b == null) throw new IllegalArgumentException("Boleto #" + idBoleto + " no encontrado.");
        if (b.isYaEntro()) throw new IllegalArgumentException("No se puede eliminar un boleto cuyo titular ya ingresó al recinto.");

        b.getAsiento().setOcupado(false);
        listaBoletos.remove(b);
        return "Boleto #" + idBoleto + " cancelado y asiento " + b.getAsiento().getId() + " liberado.";
    }

    public void restaurarBoletoDesdeBD(int idB, String idG, String tipo, String asientoId, String nom, int edad, String correo, double precio, boolean pagado, boolean yaEntro, String fechaV) {
        Asiento a = buscarAsiento(asientoId);
        if (a == null) return;
        a.setOcupado(true);
        Invitado inv = new Invitado(nom, edad, correo);
        Boleto b = "VIP".equalsIgnoreCase(tipo)
            ? new BoletoVIP(idB, idG, inv, precio, a, "Primera Fila + Lounge")
            : new BoletoGeneral(idB, idG, inv, precio, a, "Gradas Generales");
        b.setPagado(pagado);
        b.setYaEntro(yaEntro);
        if (fechaV != null && !fechaV.isEmpty()) b.setFechaVenta(fechaV);
        listaBoletos.add(b);

        if (idB >= siguienteIdBoleto) siguienteIdBoleto = idB + 1;
        try {
            int numG = Integer.parseInt(idG.replace("GRP-", ""));
            if (numG >= siguienteIdGrupo) siguienteIdGrupo = numG + 1;
        } catch (Exception ignored) {}
    }

    public Asiento buscarAsiento(String id) {
        for (Asiento a : asientos) if (a.getId().equalsIgnoreCase(id)) return a;
        return null;
    }

    private Boleto buscarBoletoPorId(int id) {
        for (Boleto b : listaBoletos) if (b.getIdBoleto() == id) return b;
        return null;
    }

    private List<Boleto> buscarBoletosPorReferencia(String ref) {
        List<Boleto> resultado = new ArrayList<>();
        if (ref == null || ref.trim().isEmpty()) return resultado;
        String limpio = ref.trim().toUpperCase();

        if (limpio.startsWith("GRP-")) {
            for (Boleto b : listaBoletos) if (b.getIdGrupo().equalsIgnoreCase(limpio)) resultado.add(b);
        } else {
            try {
                int id = Integer.parseInt(limpio);
                Boleto encontrado = buscarBoletoPorId(id);
                if (encontrado != null) {
                    for (Boleto b : listaBoletos) if (b.getIdGrupo().equals(encontrado.getIdGrupo())) resultado.add(b);
                }
            } catch (NumberFormatException ignored) {}
        }
        return resultado;
    }

    public String getIdEvento() { return idEvento; }
    public String getNombreEvento() { return nombreEvento; }
    public ArrayList<Boleto> getListaBoletos() { return listaBoletos; }

    public String toJson() {
        int totalAsientos = asientos.size();
        int vipTotal = 0, vipOcupados = 0, genTotal = 0, genOcupados = 0;
        for (Asiento a : asientos) {
            if (a.isEsVIP()) { vipTotal++; if (a.isOcupado()) vipOcupados++; }
            else { genTotal++; if (a.isOcupado()) genOcupados++; }
        }
        double ingresosConfirmados = 0, ingresosPendientes = 0;
        int ingresadosRecinto = 0;
        for (Boleto b : listaBoletos) {
            if (b.isPagado()) ingresosConfirmados += b.getPrecio();
            else ingresosPendientes += b.getPrecio();
            if (b.isYaEntro()) ingresadosRecinto++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"idEvento\":\"").append(BaseDatos.escapar(idEvento)).append("\",");
        sb.append("\"nombreEvento\":\"").append(BaseDatos.escapar(nombreEvento)).append("\",");
        sb.append("\"fechaEvento\":\"").append(BaseDatos.escapar(fechaEvento)).append("\",");
        sb.append("\"recinto\":\"").append(BaseDatos.escapar(recinto)).append("\",");
        sb.append("\"categoria\":\"").append(BaseDatos.escapar(categoria)).append("\",");
        sb.append("\"numFilas\":").append(numFilas).append(",");
        sb.append("\"numColumnas\":").append(numColumnas).append(",");
        sb.append("\"numFilasVIP\":").append(numFilasVIP).append(",");
        sb.append("\"costoGeneral\":").append(costoGeneral).append(",");
        sb.append("\"costoVIP\":").append(costoVIP).append(",");

        sb.append("\"metricas\":{");
        sb.append("\"capacidadTotal\":").append(totalAsientos).append(",");
        sb.append("\"boletosVendidos\":").append(listaBoletos.size()).append(",");
        sb.append("\"disponibles\":").append(totalAsientos - listaBoletos.size()).append(",");
        sb.append("\"vipTotal\":").append(vipTotal).append(",");
        sb.append("\"vipOcupados\":").append(vipOcupados).append(",");
        sb.append("\"genTotal\":").append(genTotal).append(",");
        sb.append("\"genOcupados\":").append(genOcupados).append(",");
        sb.append("\"ingresosConfirmados\":").append(ingresosConfirmados).append(",");
        sb.append("\"ingresosPendientes\":").append(ingresosPendientes).append(",");
        sb.append("\"ingresadosRecinto\":").append(ingresadosRecinto);
        sb.append("},");

        sb.append("\"asientos\":[");
        for (int i = 0; i < asientos.size(); i++) {
            Asiento a = asientos.get(i);
            sb.append("{\"id\":\"").append(a.getId()).append("\",\"fila\":").append(a.getFila())
              .append(",\"columna\":").append(a.getColumna()).append(",\"esVIP\":").append(a.isEsVIP())
              .append(",\"ocupado\":").append(a.isOcupado()).append("}");
            if (i < asientos.size() - 1) sb.append(",");
        }
        sb.append("],");

        sb.append("\"boletos\":[");
        for (int i = 0; i < listaBoletos.size(); i++) {
            Boleto b = listaBoletos.get(i);
            sb.append("{\"idBoleto\":").append(b.getIdBoleto())
              .append(",\"idGrupo\":\"").append(BaseDatos.escapar(b.getIdGrupo())).append("\"")
              .append(",\"tipo\":\"").append(b.getTipo()).append("\"")
              .append(",\"asientoId\":\"").append(b.getAsiento().getId()).append("\"")
              .append(",\"nombre\":\"").append(BaseDatos.escapar(b.getInvitado().getNombre())).append("\"")
              .append(",\"edad\":").append(b.getInvitado().getEdad())
              .append(",\"correo\":\"").append(BaseDatos.escapar(b.getInvitado().getCorreo())).append("\"")
              .append(",\"precio\":").append(b.getPrecio())
              .append(",\"pagado\":").append(b.isPagado())
              .append(",\"yaEntro\":").append(b.isYaEntro())
              .append(",\"fechaVenta\":\"").append(BaseDatos.escapar(b.getFechaVenta())).append("\"}");
            if (i < listaBoletos.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }
}