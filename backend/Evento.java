/* =========================================================================
 * Proyecto: EventGo - Sistema Centralizado de Gestión de Eventos
 * Autores:
 *  - Bernardo Velasco Ortega | Matrícula: AL07127004
 *  - Andrea Lizeth Sanchez Gomez
 * ========================================================================= */

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Evento {
    private String nombreEvento;
    private String fechaEvento;
    private String recinto;
    private int numFilas;
    private int numColumnas;
    private int numFilasVIP;
    private double costoGeneral;
    private double costoVIP;

    private ArrayList<Asiento> asientos;
    private ArrayList<Boleto> listaBoletos;
    private int siguienteIdBoleto;
    private int siguienteIdGrupo;
    private Path rutaArchivoPersistencia;

    public Evento(Path rutaArchivoPersistencia) {
        this.rutaArchivoPersistencia = rutaArchivoPersistencia;
        this.asientos = new ArrayList<>();
        this.listaBoletos = new ArrayList<>();
        this.siguienteIdBoleto = 1;
        this.siguienteIdGrupo = 1;

        if (!cargarDesdeJSON()) {
            inicializarEstructura("Concierto Inaugural Tecmi", "2026-11-28 20:00", "Auditorio Principal", 6, 8, 2, 500.0, 1500.0);
            guardarEnJSON();
        }
    }

    // CREATE / RECONFIGURE EVENT
    public synchronized void configurarEvento(String nombre, String fecha, String recinto, int filas, int columnas, int filasVIP, double cGen, double cVip) {
        if (nombre == null || nombre.trim().isEmpty()) throw new IllegalArgumentException("El nombre del evento es obligatorio.");
        if (filas < 2 || filas > 15 || columnas < 2 || columnas > 20) throw new IllegalArgumentException("Dimensiones inválidas (Filas: 2-15, Columnas: 2-20).");
        if (filasVIP < 1 || filasVIP >= filas) throw new IllegalArgumentException("Las filas VIP deben ser al menos 1 y menores al total de filas (" + filas + ").");
        if (cGen <= 0 || cVip <= cGen) throw new IllegalArgumentException("El costo VIP debe ser estrictamente mayor al costo General.");

        inicializarEstructura(nombre.trim(), fecha.trim(), recinto.trim(), filas, columnas, filasVIP, cGen, cVip);
        guardarEnJSON();
    }

    private void inicializarEstructura(String nombre, String fecha, String recinto, int filas, int columnas, int filasVIP, double cGen, double cVip) {
        this.nombreEvento = nombre;
        this.fechaEvento = fecha;
        this.recinto = recinto;
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

    // CREATE GROUP TICKETS (1 a 4 boletos con validación de cercanía VIP al escenario)
    public synchronized String registrarCompraGrupal(String nombreTitular, int edad, String correo, String tipo, List<String> idsAsientos) {
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

        guardarEnJSON();
        double total = precioUnitario * asientosValidados.size();
        return "Compra registrada bajo folio grupal " + idGrupo + " (" + asientosValidados.size() + " boletos). Total a pagar: $" + String.format("%.2f", total);
    }

    // UPDATE: PROCESAR PAGO (Por folio de grupo GRP-X o por ID de boleto)
    public synchronized String procesarPago(String referencia, double montoRecibido) {
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

        guardarEnJSON();
        return "Pago confirmado para " + pendientes + " boleto(s) [" + objetivos.get(0).getIdGrupo() + "]. Cambio devuelto: $" + String.format("%.2f", cambio);
    }

    // UPDATE: CHECK-IN GRUPAL (Registra el ingreso de todos los usuarios asociados al grupo)
    public synchronized String registrarCheckInGrupal(String referencia) {
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

        guardarEnJSON();
        return "ACCESO CONCEDIDO (" + objetivos.get(0).getIdGrupo() + "): Se registró la entrada simultánea de " + recienIngresados + " asistente(s) a nombre de " + objetivos.get(0).getInvitado().getNombre() + ".";
    }

    // UPDATE (CRUD): ACTUALIZAR DATOS DEL TITULAR DE UN BOLETO
    public synchronized String actualizarBoleto(int idBoleto, String nuevoNombre, int nuevaEdad, String nuevoCorreo) {
        Boleto b = buscarBoletoPorId(idBoleto);
        if (b == null) throw new IllegalArgumentException("Boleto #" + idBoleto + " no encontrado.");
        if (nuevoNombre == null || nuevoNombre.trim().isEmpty()) throw new IllegalArgumentException("El nombre no puede estar vacío.");
        if (nuevaEdad < 12 || nuevaEdad > 110) throw new IllegalArgumentException("Edad inválida.");
        if (nuevoCorreo == null || !nuevoCorreo.contains("@")) throw new IllegalArgumentException("Correo inválido.");

        b.getInvitado().setNombre(nuevoNombre.trim());
        b.getInvitado().setEdad(nuevaEdad);
        b.getInvitado().setCorreo(nuevoCorreo.trim());
        guardarEnJSON();
        return "Boleto #" + idBoleto + " actualizado correctamente.";
    }

    // DELETE (CRUD): ELIMINAR / CANCELAR RESERVA DE BOLETO Y LIBERAR ASIENTO
    public synchronized String eliminarBoleto(int idBoleto) {
        Boleto b = buscarBoletoPorId(idBoleto);
        if (b == null) throw new IllegalArgumentException("Boleto #" + idBoleto + " no encontrado.");
        if (b.isYaEntro()) throw new IllegalArgumentException("No se puede eliminar un boleto cuyo titular ya ingresó al recinto.");

        b.getAsiento().setOcupado(false);
        listaBoletos.remove(b);
        guardarEnJSON();
        return "Boleto #" + idBoleto + " cancelado y asiento " + b.getAsiento().getId() + " liberado.";
    }

    private Asiento buscarAsiento(String id) {
        for (Asiento a : asientos) {
            if (a.getId().equalsIgnoreCase(id)) return a;
        }
        return null;
    }

    private Boleto buscarBoletoPorId(int id) {
        for (Boleto b : listaBoletos) {
            if (b.getIdBoleto() == id) return b;
        }
        return null;
    }

    private List<Boleto> buscarBoletosPorReferencia(String ref) {
        List<Boleto> resultado = new ArrayList<>();
        if (ref == null || ref.trim().isEmpty()) return resultado;
        String limpio = ref.trim().toUpperCase();

        if (limpio.startsWith("GRP-")) {
            for (Boleto b : listaBoletos) {
                if (b.getIdGrupo().equalsIgnoreCase(limpio)) resultado.add(b);
            }
        } else {
            try {
                int id = Integer.parseInt(limpio);
                Boleto encontrado = buscarBoletoPorId(id);
                if (encontrado != null) {
                    // Devuelve todos los boletos asociados a ese mismo grupo
                    for (Boleto b : listaBoletos) {
                        if (b.getIdGrupo().equals(encontrado.getIdGrupo())) resultado.add(b);
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
        return resultado;
    }

    // READ: GENERAR ESTADO COMPLETO Y MÉTRICAS DASHBOARD EN JSON
    public synchronized String obtenerEstadoJson() {
        int totalAsientos = asientos.size();
        int vipTotal = 0, vipOcupados = 0;
        int genTotal = 0, genOcupados = 0;

        for (Asiento a : asientos) {
            if (a.isEsVIP()) {
                vipTotal++;
                if (a.isOcupado()) vipOcupados++;
            } else {
                genTotal++;
                if (a.isOcupado()) genOcupados++;
            }
        }

        double ingresosConfirmados = 0;
        double ingresosPendientes = 0;
        int ingresadosRecinto = 0;

        for (Boleto b : listaBoletos) {
            if (b.isPagado()) ingresosConfirmados += b.getPrecio();
            else ingresosPendientes += b.getPrecio();
            if (b.isYaEntro()) ingresadosRecinto++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"evento\": {\n");
        sb.append("    \"nombreEvento\": \"").append(escapar(nombreEvento)).append("\",\n");
        sb.append("    \"fechaEvento\": \"").append(escapar(fechaEvento)).append("\",\n");
        sb.append("    \"recinto\": \"").append(escapar(recinto)).append("\",\n");
        sb.append("    \"numFilas\": ").append(numFilas).append(",\n");
        sb.append("    \"numColumnas\": ").append(numColumnas).append(",\n");
        sb.append("    \"numFilasVIP\": ").append(numFilasVIP).append(",\n");
        sb.append("    \"costoGeneral\": ").append(costoGeneral).append(",\n");
        sb.append("    \"costoVIP\": ").append(costoVIP).append("\n");
        sb.append("  },\n");

        sb.append("  \"metricas\": {\n");
        sb.append("    \"capacidadTotal\": ").append(totalAsientos).append(",\n");
        sb.append("    \"boletosVendidos\": ").append(listaBoletos.size()).append(",\n");
        sb.append("    \"disponibles\": ").append(totalAsientos - listaBoletos.size()).append(",\n");
        sb.append("    \"vipTotal\": ").append(vipTotal).append(",\n");
        sb.append("    \"vipOcupados\": ").append(vipOcupados).append(",\n");
        sb.append("    \"genTotal\": ").append(genTotal).append(",\n");
        sb.append("    \"genOcupados\": ").append(genOcupados).append(",\n");
        sb.append("    \"ingresosConfirmados\": ").append(ingresosConfirmados).append(",\n");
        sb.append("    \"ingresosPendientes\": ").append(ingresosPendientes).append(",\n");
        sb.append("    \"ingresadosRecinto\": ").append(ingresadosRecinto).append("\n");
        sb.append("  },\n");

        sb.append("  \"asientos\": [\n");
        for (int i = 0; i < asientos.size(); i++) {
            Asiento a = asientos.get(i);
            sb.append("    {\"id\":\"").append(a.getId()).append("\",\"fila\":").append(a.getFila())
              .append(",\"columna\":").append(a.getColumna()).append(",\"esVIP\":").append(a.isEsVIP())
              .append(",\"ocupado\":").append(a.isOcupado()).append("}");
            if (i < asientos.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"boletos\": [\n");
        for (int i = 0; i < listaBoletos.size(); i++) {
            Boleto b = listaBoletos.get(i);
            sb.append("    {\"idBoleto\":").append(b.getIdBoleto())
              .append(",\"idGrupo\":\"").append(escapar(b.getIdGrupo())).append("\"")
              .append(",\"tipo\":\"").append(b.getTipo()).append("\"")
              .append(",\"asientoId\":\"").append(b.getAsiento().getId()).append("\"")
              .append(",\"nombre\":\"").append(escapar(b.getInvitado().getNombre())).append("\"")
              .append(",\"edad\":").append(b.getInvitado().getEdad())
              .append(",\"correo\":\"").append(escapar(b.getInvitado().getCorreo())).append("\"")
              .append(",\"precio\":").append(b.getPrecio())
              .append(",\"pagado\":").append(b.isPagado())
              .append(",\"yaEntro\":").append(b.isYaEntro())
              .append(",\"fechaVenta\":\"").append(escapar(b.getFechaVenta())).append("\"}");
            if (i < listaBoletos.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    // PERSISTENCIA EN ARCHIVO JSON
    private void guardarEnJSON() {
        try {
            Files.write(rutaArchivoPersistencia, obtenerEstadoJson().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Error guardando persistencia JSON: " + e.getMessage());
        }
    }

    private boolean cargarDesdeJSON() {
        try {
            File f = rutaArchivoPersistencia.toFile();
            if (!f.exists()) return false;
            String contenido = new String(Files.readAllBytes(rutaArchivoPersistencia), StandardCharsets.UTF_8);

            String nombre = extraerString(contenido, "\"nombreEvento\":");
            String fecha = extraerString(contenido, "\"fechaEvento\":");
            String rec = extraerString(contenido, "\"recinto\":");
            int filas = (int) extraerNumero(contenido, "\"numFilas\":");
            int cols = (int) extraerNumero(contenido, "\"numColumnas\":");
            int filasVIP = (int) extraerNumero(contenido, "\"numFilasVIP\":");
            double cGen = extraerNumero(contenido, "\"costoGeneral\":");
            double cVip = extraerNumero(contenido, "\"costoVIP\":");

            if (nombre == null || filas <= 0 || cols <= 0) return false;
            inicializarEstructura(nombre, fecha, rec, filas, cols, filasVIP, cGen, cVip);

            int idxBoletos = contenido.indexOf("\"boletos\":");
            if (idxBoletos != -1) {
                String seccionBoletos = contenido.substring(idxBoletos);
                String[] bloques = seccionBoletos.split("\\{");
                for (String bloque : bloques) {
                    if (!bloque.contains("\"idBoleto\":")) continue;
                    int idB = (int) extraerNumero(bloque, "\"idBoleto\":");
                    String idG = extraerString(bloque, "\"idGrupo\":");
                    String tipo = extraerString(bloque, "\"tipo\":");
                    String asientoId = extraerString(bloque, "\"asientoId\":");
                    String nomInv = extraerString(bloque, "\"nombre\":");
                    int edadInv = (int) extraerNumero(bloque, "\"edad\":");
                    String correoInv = extraerString(bloque, "\"correo\":");
                    double precio = extraerNumero(bloque, "\"precio\":");
                    boolean pagado = bloque.contains("\"pagado\":true");
                    boolean yaEntro = bloque.contains("\"yaEntro\":true");
                    String fechaV = extraerString(bloque, "\"fechaVenta\":");

                    Asiento asientoObj = buscarAsiento(asientoId);
                    if (asientoObj != null) {
                        asientoObj.setOcupado(true);
                        Invitado inv = new Invitado(nomInv, edadInv, correoInv);
                        Boleto b = "VIP".equalsIgnoreCase(tipo)
                            ? new BoletoVIP(idB, idG, inv, precio, asientoObj, "Primera Fila + Lounge")
                            : new BoletoGeneral(idB, idG, inv, precio, asientoObj, "Gradas Generales");
                        b.setPagado(pagado);
                        b.setYaEntro(yaEntro);
                        if (fechaV != null) b.setFechaVenta(fechaV);
                        listaBoletos.add(b);

                        if (idB >= siguienteIdBoleto) siguienteIdBoleto = idB + 1;
                        try {
                            int numG = Integer.parseInt(idG.replace("GRP-", ""));
                            if (numG >= siguienteIdGrupo) siguienteIdGrupo = numG + 1;
                        } catch (Exception ignored) {}
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String extraerString(String texto, String clave) {
        int i = texto.indexOf(clave);
        if (i == -1) return null;
        int inicioComilla = texto.indexOf("\"", i + clave.length());
        int finComilla = texto.indexOf("\"", inicioComilla + 1);
        if (inicioComilla == -1 || finComilla == -1) return null;
        return texto.substring(inicioComilla + 1, finComilla);
    }

    private double extraerNumero(String texto, String clave) {
        int i = texto.indexOf(clave);
        if (i == -1) return 0;
        int inicio = i + clave.length();
        while (inicio < texto.length() && (texto.charAt(inicio) == ' ' || texto.charAt(inicio) == ':')) inicio++;
        int fin = inicio;
        while (fin < texto.length() && (Character.isDigit(texto.charAt(fin)) || texto.charAt(fin) == '.' || texto.charAt(fin) == '-')) fin++;
        try {
            return Double.parseDouble(texto.substring(inicio, fin));
        } catch (Exception e) {
            return 0;
        }
    }

    private String escapar(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ").replace("\r", "");
    }
}