/* =========================================================================
 * EventGo - Módulo Analítico y Visualización de Métricas (Dashboard)
 * ========================================================================= */

function renderizarDashboard(eventoActual, todosEventos, totalClientes) {
    const container = document.getElementById("dashboardContent");
    if (!container || !eventoActual) return;

    const m = eventoActual.metricas;
    const pctTotal = m.capacidadTotal > 0 ? Math.round((m.boletosVendidos / m.capacidadTotal) * 100) : 0;
    const pctVIP = m.vipTotal > 0 ? Math.round((m.vipOcupados / m.vipTotal) * 100) : 0;
    const pctGen = m.genTotal > 0 ? Math.round((m.genOcupados / m.genTotal) * 100) : 0;
    const pctAsistencia = m.boletosVendidos > 0 ? Math.round((m.ingresadosRecinto / m.boletosVendidos) * 100) : 0;

    // Métricas globales de todos los eventos
    let ingresosGlobales = 0;
    let boletosGlobales = 0;
    todosEventos.forEach(ev => {
        ingresosGlobales += ev.metricas.ingresosConfirmados;
        boletosGlobales += ev.metricas.boletosVendidos;
    });

    const filasComparativa = todosEventos.map(ev => {
        const em = ev.metricas;
        const occ = em.capacidadTotal > 0 ? Math.round((em.boletosVendidos / em.capacidadTotal) * 100) : 0;
        return `
            <tr>
                <td><strong>${ev.nombreEvento}</strong><br><small class="muted">${ev.recinto}</small></td>
                <td>${ev.fechaEvento}</td>
                <td>${em.boletosVendidos} / ${em.capacidadTotal} (${occ}%)</td>
                <td>${em.ingresadosRecinto} asistentes</td>
                <td><strong style="color:#059669">$${em.ingresosConfirmados.toLocaleString("es-MX", {minimumFractionDigits:2})}</strong></td>
                <td><span style="color:#b45309">$${em.ingresosPendientes.toLocaleString("es-MX", {minimumFractionDigits:2})}</span></td>
            </tr>
        `;
    }).join("");

    container.innerHTML = `
        <div class="kpi-grid">
            <div class="kpi-card">
                <small>INGRESOS CONFIRMADOS (EVENTO)</small>
                <h3>$${m.ingresosConfirmados.toLocaleString("es-MX", {minimumFractionDigits:2})}</h3>
                <span>Global acumulado: $${ingresosGlobales.toLocaleString("es-MX", {minimumFractionDigits:2})}</span>
            </div>
            <div class="kpi-card">
                <small>POR COBRAR EN CAJA</small>
                <h3 style="color:#d97706">$${m.ingresosPendientes.toLocaleString("es-MX", {minimumFractionDigits:2})}</h3>
                <span style="color:#64748b">Folios reservados pendientes</span>
            </div>
            <div class="kpi-card">
                <small>OCUPACIÓN DEL RECINTO</small>
                <h3>${m.boletosVendidos} / ${m.capacidadTotal}</h3>
                <span>${pctTotal}% de aforo vendido</span>
            </div>
            <div class="kpi-card">
                <small>CHECK-IN EN PUERTA</small>
                <h3>${m.ingresadosRecinto} / ${m.boletosVendidos}</h3>
                <span>${pctAsistencia}% de asistentes ingresados</span>
            </div>
        </div>

        <div class="two-col-grid">
            <div class="card">
                <div class="card-header">
                    <h3>Ocupación por Zona (${eventoActual.nombreEvento})</h3>
                    <span class="badge-info">${totalClientes} Clientes en BD</span>
                </div>

                <div style="margin-bottom:1.25rem;">
                    <div style="display:flex; justify-content:space-between; font-size:0.88rem; font-weight:600;">
                        <span>Zona VIP (Primeras ${eventoActual.numFilasVIP} filas)</span>
                        <span>${m.vipOcupados} / ${m.vipTotal} asientos (${pctVIP}%)</span>
                    </div>
                    <div class="progress-track">
                        <div class="progress-fill" style="width:${pctVIP}%; background:#f59e0b;"></div>
                    </div>
                </div>

                <div style="margin-bottom:1.25rem;">
                    <div style="display:flex; justify-content:space-between; font-size:0.88rem; font-weight:600;">
                        <span>Zona General (Gradas)</span>
                        <span>${m.genOcupados} / ${m.genTotal} asientos (${pctGen}%)</span>
                    </div>
                    <div class="progress-track">
                        <div class="progress-fill" style="width:${pctGen}%; background:#10b981;"></div>
                    </div>
                </div>

                <div>
                    <div style="display:flex; justify-content:space-between; font-size:0.88rem; font-weight:600;">
                        <span>Flujo de Acceso Real (Check-In vs Vendidos)</span>
                        <span>${m.ingresadosRecinto} / ${m.boletosVendidos} personas (${pctAsistencia}%)</span>
                    </div>
                    <div class="progress-track">
                        <div class="progress-fill" style="width:${pctAsistencia}%; background:#4f46e5;"></div>
                    </div>
                </div>
            </div>

            <div class="card">
                <div class="card-header">
                    <h3>Consolidado Comparativo de Cartelera</h3>
                    <span class="badge-success">${boletosGlobales} Boletos Totales</span>
                </div>
                <div class="table-responsive">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Evento</th>
                                <th>Fecha</th>
                                <th>Aforo</th>
                                <th>En Recinto</th>
                                <th>Confirmado</th>
                                <th>Pendiente</th>
                            </tr>
                        </thead>
                        <tbody>${filasComparativa}</tbody>
                    </table>
                </div>
            </div>
        </div>
    `;
}