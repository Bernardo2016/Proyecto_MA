/* =========================================================================
 * EventGo - Controlador Principal Frontend (Taquilla, Roles, CRUD y Timer)
 * ========================================================================= */

let estadoGlobal = null;
let idEventoActivo = null;
let rolActivo = "ORGANIZADOR";
let categoriaSeleccionada = "VIP";
let asientosSeleccionados = [];
let timerSegundos = 60;
let timerInterval = null;

document.addEventListener("DOMContentLoaded", () => {
    inicializarEventosUI();
    cargarEstadoServidor();
});

async function cargarEstadoServidor() {
    try {
        const resp = await fetch("/api/estado");
        estadoGlobal = await resp.json();

        if (!idEventoActivo || !estadoGlobal.eventos.some(e => e.idEvento === idEventoActivo)) {
            idEventoActivo = estadoGlobal.eventos[0].idEvento;
        }

        renderizarSelectores();
        actualizarVistaCompleta();
    } catch (err) {
        mostrarToast("Error de conexión con el servidor Java.", true);
    }
}

function getEventoActual() {
    return estadoGlobal.eventos.find(e => e.idEvento === idEventoActivo) || estadoGlobal.eventos[0];
}

function renderizarSelectores() {
    const selEvento = document.getElementById("selectEventoActivo");
    selEvento.innerHTML = estadoGlobal.eventos.map(ev =>
        `<option value="${ev.idEvento}" ${ev.idEvento === idEventoActivo ? "selected" : ""}>
            ${ev.nombreEvento} (${ev.categoria})
        </option>`
    ).join("");

    const selRol = document.getElementById("selectRolActivo");
    selRol.innerHTML = estadoGlobal.usuarios.map(u =>
        `<option value="${u.rol}" ${u.rol === rolActivo ? "selected" : ""}>
            ${u.nombre} — [${u.rol}]
        </option>`
    ).join("");
}

function aplicarPermisosPorRol() {
    const badge = document.getElementById("badgeRolPermisos");
    if (rolActivo === "ORGANIZADOR") badge.textContent = "Acceso Total (Admin / Dashboard / CRUD)";
    else if (rolActivo === "TAQUILLA") badge.textContent = "Operación Taquilla, Caja y Check-In";
    else badge.textContent = "Vista Público / Compra de Boletos";

    document.querySelectorAll(".nav-item[data-role]").forEach(btn => {
        const permitidos = btn.getAttribute("data-role").split(",");
        if (permitidos.includes(rolActivo)) {
            btn.classList.remove("hidden-role");
        } else {
            btn.classList.add("hidden-role");
            if (btn.classList.contains("active")) {
                cambiarTab("taquilla");
            }
        }
    });
}

function actualizarVistaCompleta() {
    const ev = getEventoActual();
    if (!ev) return;

    document.getElementById("headerVenueInfo").textContent = `${ev.recinto} | ${ev.fechaEvento}`;
    document.getElementById("tituloMapaEvento").textContent = ev.nombreEvento;
    document.getElementById("subtituloMapaEvento").textContent =
        `Filas VIP exclusivas frente al escenario: Primeras ${ev.numFilasVIP} fila(s). Máximo 4 boletos por titular.`;
    document.getElementById("lblPrecioVIP").textContent = `$${ev.costoVIP.toLocaleString("es-MX")}`;
    document.getElementById("lblPrecioGEN").textContent = `$${ev.costoGeneral.toLocaleString("es-MX")}`;

    aplicarPermisosPorRol();
    renderizarMapaAsientos(ev);
    actualizarResumenCompra(ev);
    renderizarTablaGrupos(ev);
    renderizarCarteleraEventos();
    renderizarTablasCRUD(ev);
    renderizarDashboard(ev, estadoGlobal.eventos, estadoGlobal.clientes.length);
}

function renderizarMapaAsientos(ev) {
    const container = document.getElementById("seatGridContainer");
    container.innerHTML = "";

    for (let f = 1; f <= ev.numFilas; f++) {
        const rowDiv = document.createElement("div");
        rowDiv.className = "seat-row";

        const letra = String.fromCharCode(64 + f);
        const esFilaVIP = f <= ev.numFilasVIP;

        const label = document.createElement("span");
        label.className = "row-label";
        label.textContent = `Fila ${letra} ${esFilaVIP ? "★" : ""}`;
        rowDiv.appendChild(label);

        const asientosFila = ev.asientos.filter(a => a.fila === f);
        asientosFila.forEach(asiento => {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.textContent = asiento.id;
            btn.className = "seat-btn";

            if (asiento.ocupado) {
                btn.classList.add("occupied");
                btn.disabled = true;
                btn.title = "Asiento Ocupado";
            } else {
                btn.classList.add(asiento.esVIP ? "vip-free" : "gen-free");
                if (asientosSeleccionados.includes(asiento.id)) {
                    btn.classList.add("selected");
                }
                btn.addEventListener("click", () => toggleSeleccionAsiento(asiento, ev));
            }
            rowDiv.appendChild(btn);
        });

        container.appendChild(rowDiv);
    }
}

function toggleSeleccionAsiento(asiento, ev) {
    const idx = asientosSeleccionados.indexOf(asiento.id);
    if (idx !== -1) {
        asientosSeleccionados.splice(idx, 1);
        if (asientosSeleccionados.length === 0) detenerTemporizador();
        renderizarMapaAsientos(ev);
        actualizarResumenCompra(ev);
        return;
    }

    // Validación de cláusula VIP vs General en tiempo real
    if (categoriaSeleccionada === "VIP" && !asiento.esVIP) {
        mostrarToast(`Cláusula VIP: Solo puedes elegir asientos en las primeras ${ev.numFilasVIP} filas cercanas al escenario.`, true);
        return;
    }
    if (categoriaSeleccionada === "GENERAL" && asiento.esVIP) {
        mostrarToast("Esa fila es exclusiva para categoría VIP. Cambia a VIP o elige filas posteriores.", true);
        return;
    }
    if (asientosSeleccionados.length >= 4) {
        mostrarToast("Límite alcanzado: Máximo 4 boletos por compra grupal.", true);
        return;
    }

    asientosSeleccionados.push(asiento.id);
    iniciarOReiniciarTemporizador();
    renderizarMapaAsientos(ev);
    actualizarResumenCompra(ev);
}

function actualizarResumenCompra(ev) {
    document.getElementById("countSeleccionados").textContent = asientosSeleccionados.length;
    const chips = document.getElementById("chipsAsientos");

    if (asientosSeleccionados.length === 0) {
        chips.innerHTML = `<span class="muted">Haz clic en el mapa</span>`;
    } else {
        chips.innerHTML = asientosSeleccionados.map(id => `<span class="chip">${id}</span>`).join("");
    }

    const precioUnit = categoriaSeleccionada === "VIP" ? ev.costoVIP : ev.costoGeneral;
    const total = precioUnit * asientosSeleccionados.length;
    document.getElementById("totalVentaTexto").textContent = `$${total.toLocaleString("es-MX", {minimumFractionDigits:2})}`;
}

// Temporizador Anti-Inactividad de 60 segundos
function iniciarOReiniciarTemporizador() {
    timerSegundos = 60;
    const box = document.getElementById("timerBox");
    const lbl = document.getElementById("timerSeconds");
    box.classList.remove("hidden");
    lbl.textContent = `${timerSegundos}s`;

    if (timerInterval) clearInterval(timerInterval);
    timerInterval = setInterval(() => {
        timerSegundos--;
        lbl.textContent = `${timerSegundos}s`;
        if (timerSegundos <= 0) {
            detenerTemporizador();
            asientosSeleccionados = [];
            renderizarMapaAsientos(getEventoActual());
            actualizarResumenCompra(getEventoActual());
            mostrarToast("Tiempo de reserva agotado (60s). Los asientos seleccionados fueron liberados.", true);
        }
    }, 1000);
}

function detenerTemporizador() {
    if (timerInterval) clearInterval(timerInterval);
    timerInterval = null;
    document.getElementById("timerBox").classList.add("hidden");
}

function renderizarTablaGrupos(ev) {
    const tbody = document.getElementById("tablaGruposBody");
    const grupos = {};

    ev.boletos.forEach(b => {
        if (!grupos[b.idGrupo]) {
            grupos[b.idGrupo] = {
                idGrupo: b.idGrupo,
                titular: b.nombre,
                asientos: [],
                tipo: b.tipo,
                total: 0,
                pagado: b.pagado,
                yaEntro: b.yaEntro
            };
        }
        grupos[b.idGrupo].asientos.push(b.asientoId);
        grupos[b.idGrupo].total += b.precio;
    });

    const lista = Object.values(grupos);
    if (lista.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="muted" style="text-align:center;">Aún no hay grupos registrados en este evento.</td></tr>`;
        return;
    }

    tbody.innerHTML = lista.map(g => `
        <tr>
            <td><strong>${g.idGrupo}</strong></td>
            <td>${g.titular}</td>
            <td><code>${g.asientos.join(", ")}</code> (${g.asientos.length} pers.)</td>
            <td>${g.tipo}</td>
            <td>$${g.total.toLocaleString("es-MX", {minimumFractionDigits:2})}</td>
            <td>${g.pagado ? `<span class="badge-success">Pagado</span>` : `<span class="badge-warning">Pendiente</span>`}</td>
            <td>${g.yaEntro ? `<span class="badge-info">Ingresó (${g.asientos.length})</span>` : `<span class="muted">En espera</span>`}</td>
            <td>
                ${!g.pagado ? `<button class="btn btn-success btn-sm" onclick="autocompletarPago('${g.idGrupo}', ${g.total})">Cobrar</button>` : ""}
                ${g.pagado && !g.yaEntro ? `<button class="btn btn-primary btn-sm" onclick="ejecutarCheckInRapido('${g.idGrupo}')">Check-In</button>` : ""}
            </td>
        </tr>
    `).join("");
}

function renderizarCarteleraEventos() {
    const container = document.getElementById("listaEventosCards");
    container.innerHTML = estadoGlobal.eventos.map(ev => `
        <div class="event-item-card ${ev.idEvento === idEventoActivo ? "active-ev" : ""}">
            <div style="display:flex; justify-content:space-between; align-items:center;">
                <span class="badge-info">${ev.categoria}</span>
                <small class="muted">${ev.idEvento}</small>
            </div>
            <h4>${ev.nombreEvento}</h4>
            <p class="muted">${ev.recinto} • ${ev.fechaEvento}</p>
            <p style="font-size:0.82rem;">Aforo: <strong>${ev.metricas.boletosVendidos}/${ev.metricas.capacidadTotal}</strong> | VIP: $${ev.costoVIP} | Gen: $${ev.costoGeneral}</p>
            <div class="btn-row" style="margin-top:0.5rem;">
                <button class="btn btn-primary btn-sm" onclick="seleccionarEventoDesdeCard('${ev.idEvento}')">Operar Evento</button>
                <button class="btn btn-danger" onclick="eliminarEvento('${ev.idEvento}')">Eliminar</button>
            </div>
        </div>
    `).join("");
}

function renderizarTablasCRUD(ev) {
    // Tabla Clientes
    const tbodyCli = document.getElementById("tablaClientesBody");
    tbodyCli.innerHTML = estadoGlobal.clientes.map(c => `
        <tr>
            <td><strong>${c.idCliente}</strong></td>
            <td>${c.nombre}</td>
            <td>${c.edad}</td>
            <td>${c.correo}</td>
            <td>${c.telefono || "—"}</td>
            <td><span class="badge-info">${c.boletosHistorial} boleto(s)</span></td>
            <td>
                <button class="btn btn-secondary btn-sm" onclick="abrirModalCliente('${c.idCliente}')">Editar</button>
                <button class="btn btn-danger" onclick="eliminarCliente('${c.idCliente}')">Eliminar</button>
            </td>
        </tr>
    `).join("");

    // Tabla Boletos del Evento Activo
    const tbodyBol = document.getElementById("tablaBoletosBody");
    if (ev.boletos.length === 0) {
        tbodyBol.innerHTML = `<tr><td colspan="9" class="muted" style="text-align:center;">No hay boletos emitidos para este evento.</td></tr>`;
        return;
    }

    tbodyBol.innerHTML = ev.boletos.map(b => `
        <tr>
            <td>#${b.idBoleto}</td>
            <td><strong>${b.idGrupo}</strong></td>
            <td><code>${b.asientoId}</code></td>
            <td>${b.tipo}</td>
            <td>${b.nombre} (${b.edad})</td>
            <td>${b.correo}</td>
            <td>$${b.precio.toFixed(2)}</td>
            <td>${b.yaEntro ? "En Recinto" : (b.pagado ? "Pagado" : "Pendiente")}</td>
            <td>
                <button class="btn btn-secondary btn-sm" onclick="abrirModalBoleto(${b.idBoleto})">Editar</button>
                <button class="btn btn-danger" onclick="eliminarBoleto(${b.idBoleto})">Cancelar</button>
            </td>
        </tr>
    `).join("");
}

function inicializarEventosUI() {
    // Navegación por Tabs
    document.querySelectorAll(".nav-item").forEach(btn => {
        btn.addEventListener("click", () => cambiarTab(btn.getAttribute("data-tab")));
    });

    // Cambio de Evento Activo
    document.getElementById("selectEventoActivo").addEventListener("change", (e) => {
        idEventoActivo = e.target.value;
        asientosSeleccionados = [];
        detenerTemporizador();
        actualizarVistaCompleta();
    });

    // Cambio de Rol Activo
    document.getElementById("selectRolActivo").addEventListener("change", (e) => {
        rolActivo = e.target.value;
        aplicarPermisosPorRol();
        mostrarToast(`Vista cambiada al perfil: ${rolActivo}`);
    });

    // Botones Categoría VIP / General
    document.getElementById("btnCatVIP").addEventListener("click", () => cambiarCategoria("VIP"));
    document.getElementById("btnCatGEN").addEventListener("click", () => cambiarCategoria("GENERAL"));

    document.getElementById("btnLimpiarSeleccion").addEventListener("click", () => {
        asientosSeleccionados = [];
        detenerTemporizador();
        const ev = getEventoActual();
        renderizarMapaAsientos(ev);
        actualizarResumenCompra(ev);
    });

    // Formulario Venta Grupal
    document.getElementById("formVenta").addEventListener("submit", async (e) => {
        e.preventDefault();
        if (asientosSeleccionados.length === 0) {
            mostrarToast("Selecciona al menos 1 asiento en el mapa.", true);
            return;
        }
        const payload = {
            idEvento: idEventoActivo,
            nombre: document.getElementById("ventaNombre").value,
            edad: document.getElementById("ventaEdad").value,
            correo: document.getElementById("ventaCorreo").value,
            telefono: document.getElementById("ventaTelefono").value,
            tipo: categoriaSeleccionada,
            asientos: asientosSeleccionados.join(",")
        };
        const res = await postApi("/api/vender", payload);
        if (res.ok) {
            mostrarToast(res.mensaje);
            asientosSeleccionados = [];
            detenerTemporizador();
            document.getElementById("formVenta").reset();
            document.getElementById("ventaEdad").value = "22";
            await cargarEstadoServidor();
        }
    });

    // Formulario Pago en Caja
    document.getElementById("formPago").addEventListener("submit", async (e) => {
        e.preventDefault();
        const res = await postApi("/api/pagar", {
            idEvento: idEventoActivo,
            referencia: document.getElementById("pagoReferencia").value,
            monto: document.getElementById("pagoMonto").value
        });
        if (res.ok) {
            const box = document.getElementById("resultadoCaja");
            box.textContent = res.mensaje;
            box.classList.remove("hidden");
            mostrarToast(res.mensaje);
            document.getElementById("formPago").reset();
            await cargarEstadoServidor();
        }
    });

    // Formulario Check-In Grupal
    document.getElementById("formCheckIn").addEventListener("submit", async (e) => {
        e.preventDefault();
        const res = await postApi("/api/checkin", {
            idEvento: idEventoActivo,
            referencia: document.getElementById("checkinReferencia").value
        });
        if (res.ok) {
            const box = document.getElementById("resultadoCheckIn");
            box.textContent = res.mensaje;
            box.classList.remove("hidden");
            mostrarToast(res.mensaje);
            document.getElementById("formCheckIn").reset();
            await cargarEstadoServidor();
        }
    });

    // Formulario Nuevo Evento
    document.getElementById("formNuevoEvento").addEventListener("submit", async (e) => {
        e.preventDefault();
        const res = await postApi("/api/evento/crear", {
            nombreEvento: document.getElementById("evNombre").value,
            fechaEvento: document.getElementById("evFecha").value,
            categoria: document.getElementById("evCategoria").value,
            recinto: document.getElementById("evRecinto").value,
            numFilas: document.getElementById("evFilas").value,
            numColumnas: document.getElementById("evCols").value,
            numFilasVIP: document.getElementById("evFilasVIP").value,
            costoGeneral: document.getElementById("evCostoGen").value,
            costoVIP: document.getElementById("evCostoVIP").value
        });
        if (res.ok) {
            mostrarToast(res.mensaje);
            document.getElementById("formNuevoEvento").reset();
            await cargarEstadoServidor();
        }
    });

    // Modal CRUD
    document.getElementById("btnNuevoClienteModal").addEventListener("click", () => abrirModalCliente(null));
    document.getElementById("btnCerrarModal").addEventListener("click", cerrarModal);
    document.getElementById("btnCancelarModal").addEventListener("click", cerrarModal);

    document.getElementById("modalForm").addEventListener("submit", async (e) => {
        e.preventDefault();
        const mode = document.getElementById("modalMode").value;
        const id = document.getElementById("modalRecordId").value;

        if (mode === "CLIENTE") {
            const res = await postApi("/api/cliente/guardar", {
                idCliente: id,
                nombre: document.getElementById("modalNombre").value,
                edad: document.getElementById("modalEdad").value,
                telefono: document.getElementById("modalTelefono").value,
                correo: document.getElementById("modalCorreo").value
            });
            if (res.ok) {
                mostrarToast(res.mensaje);
                cerrarModal();
                await cargarEstadoServidor();
            }
        } else if (mode === "BOLETO") {
            const res = await postApi("/api/boleto/actualizar", {
                idEvento: idEventoActivo,
                idBoleto: id,
                nombre: document.getElementById("modalNombre").value,
                edad: document.getElementById("modalEdad").value,
                correo: document.getElementById("modalCorreo").value
            });
            if (res.ok) {
                mostrarToast(res.mensaje);
                cerrarModal();
                await cargarEstadoServidor();
            }
        }
    });
}

function cambiarTab(tabId) {
    document.querySelectorAll(".nav-item").forEach(b => b.classList.remove("active"));
    document.querySelectorAll(".tab-panel").forEach(p => p.classList.remove("active"));

    const btn = document.querySelector(`.nav-item[data-tab="${tabId}"]`);
    const panel = document.getElementById(`tab-${tabId}`);
    if (btn) btn.classList.add("active");
    if (panel) panel.classList.add("active");
}

function cambiarCategoria(cat) {
    categoriaSeleccionada = cat;
    asientosSeleccionados = [];
    detenerTemporizador();

    document.getElementById("btnCatVIP").classList.toggle("active", cat === "VIP");
    document.getElementById("btnCatGEN").classList.toggle("active", cat === "GENERAL");

    const hint = document.getElementById("reglaVipHint");
    hint.textContent = cat === "VIP"
        ? "Cláusula VIP: Solo habilita las primeras filas frente al escenario."
        : "Zona General: Habilita asientos posteriores a la zona VIP.";

    const ev = getEventoActual();
    renderizarMapaAsientos(ev);
    actualizarResumenCompra(ev);
}

// Funciones Globales para Botones en Tablas
window.autocompletarPago = (idGrupo, total) => {
    document.getElementById("pagoReferencia").value = idGrupo;
    document.getElementById("pagoMonto").value = total;
    document.getElementById("pagoMonto").focus();
};

window.ejecutarCheckInRapido = async (idGrupo) => {
    const res = await postApi("/api/checkin", { idEvento: idEventoActivo, referencia: idGrupo });
    if (res.ok) {
        mostrarToast(res.mensaje);
        await cargarEstadoServidor();
    }
};

window.seleccionarEventoDesdeCard = (idEv) => {
    idEventoActivo = idEv;
    asientosSeleccionados = [];
    detenerTemporizador();
    renderizarSelectores();
    actualizarVistaCompleta();
    cambiarTab("taquilla");
};

window.eliminarEvento = async (idEv) => {
    const res = await postApi("/api/evento/eliminar", { idEvento: idEv });
    if (res.ok) {
        mostrarToast(res.mensaje);
        await cargarEstadoServidor();
    }
};

window.abrirModalCliente = (idCliente) => {
    document.getElementById("modalMode").value = "CLIENTE";
    document.getElementById("modalTelGroup").classList.remove("hidden");

    if (idCliente) {
        const c = estadoGlobal.clientes.find(x => x.idCliente === idCliente);
        document.getElementById("modalTitle").textContent = `Editar Cliente (${c.idCliente})`;
        document.getElementById("modalRecordId").value = c.idCliente;
        document.getElementById("modalNombre").value = c.nombre;
        document.getElementById("modalEdad").value = c.edad;
        document.getElementById("modalTelefono").value = c.telefono;
        document.getElementById("modalCorreo").value = c.correo;
    } else {
        document.getElementById("modalTitle").textContent = "Registrar Nuevo Cliente";
        document.getElementById("modalRecordId").value = "";
        document.getElementById("modalForm").reset();
        document.getElementById("modalEdad").value = "22";
    }
    document.getElementById("crudModal").classList.remove("hidden");
};

window.eliminarCliente = async (idCliente) => {
    const res = await postApi("/api/cliente/eliminar", { idCliente });
    if (res.ok) {
        mostrarToast(res.mensaje);
        await cargarEstadoServidor();
    }
};

window.abrirModalBoleto = (idBoleto) => {
    const ev = getEventoActual();
    const b = ev.boletos.find(x => x.idBoleto === idBoleto);
    if (!b) return;

    document.getElementById("modalMode").value = "BOLETO";
    document.getElementById("modalTelGroup").classList.add("hidden");
    document.getElementById("modalTitle").textContent = `Editar Titular Boleto #${b.idBoleto}`;
    document.getElementById("modalRecordId").value = b.idBoleto;
    document.getElementById("modalNombre").value = b.nombre;
    document.getElementById("modalEdad").value = b.edad;
    document.getElementById("modalCorreo").value = b.correo;
    document.getElementById("crudModal").classList.remove("hidden");
};

window.eliminarBoleto = async (idBoleto) => {
    const res = await postApi("/api/boleto/eliminar", { idEvento: idEventoActivo, idBoleto: String(idBoleto) });
    if (res.ok) {
        mostrarToast(res.mensaje);
        await cargarEstadoServidor();
    }
};

function cerrarModal() {
    document.getElementById("crudModal").classList.add("hidden");
}

async function postApi(url, data) {
    try {
        const resp = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        });
        const json = await resp.json();
        if (!resp.ok || !json.ok) {
            mostrarToast(json.mensaje || "Error en la operación", true);
            return { ok: false };
        }
        return json;
    } catch (e) {
        mostrarToast("No se pudo conectar con el servidor.", true);
        return { ok: false };
    }
}

function mostrarToast(mensaje, esError = false) {
    const cont = document.getElementById("toastContainer");
    const div = document.createElement("div");
    div.className = `toast ${esError ? "error" : ""}`;
    div.textContent = mensaje;
    cont.appendChild(div);
    setTimeout(() => div.remove(), 4500);
}