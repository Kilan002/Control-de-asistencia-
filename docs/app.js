/* =========================================================================
   CONFIGURACIÓN DE LA API
   -------------------------------------------------------------------------
   Cambia esto por la URL real de tu backend en Render, ej:
   'https://bitacora-backend.onrender.com'
   ========================================================================= */
const API_BASE = 'https://control-de-asistencia-l3e8.onrender.com';

/* =========================================================================
   ESTADO DE SESIÓN
   ========================================================================= */
let session = null; // { token, matricula, rol, nombre, grupo, debeCambiarPassword }
let asistenciaVal = null;
let evidenciaDataUrl = null;    // foto ya comprimida en base64
let editAsistenciaVal = null;
let editContext = null;         // { registroId }
let catalogosCache = { materias: [], profesores: [] };

/* ---------------- HELPER: escapar texto antes de insertarlo como HTML ----------------
   Los campos como "nombre" o "grupo" son texto libre que captura un admin;
   sin esto, alguien podría meter algo como <img src=x onerror=...> y que se
   ejecute como código al mostrar la lista. */
function escapeHtml(str) {
  return String(str ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

/* ---------------- HELPER: llamadas a la API ----------------
   Agrega el token de sesión automáticamente y convierte los errores del
   servidor en excepciones de JS con el mismo mensaje que mandó el backend. */
async function apiFetch(path, opts = {}) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, opts.headers || {});
  if (session && session.token) headers['Authorization'] = 'Bearer ' + session.token;

  let res;
  try {
    res = await fetch(API_BASE + path, Object.assign({}, opts, { headers }));
  } catch (err) {
    throw new Error('No se pudo conectar con el servidor. Revisa tu internet.');
  }

  if (res.status === 401) {
    // El token ya no sirve (expiró o nunca hubo sesión): se cierra sesión.
    handleLogout();
    throw new Error('Tu sesión expiró. Inicia sesión de nuevo.');
  }

  let data = null;
  try { data = await res.json(); } catch (e) { /* respuesta sin cuerpo, ok */ }

  if (!res.ok) {
    throw new Error((data && data.error) || 'Ocurrió un error inesperado.');
  }
  return data;
}

/* Al cargar la página, si había una sesión guardada la recuperamos.
   No sabemos si el token sigue vivo hasta que se use en una petición;
   si ya expiró, la primera llamada a la API cerrará la sesión sola. */
(function restaurarSesion() {
  const guardada = localStorage.getItem('bitacora_session');
  if (!guardada) return;
  try {
    session = JSON.parse(guardada);
    if (session.debeCambiarPassword) { goTo('cambiarPassword'); return; }
    afterLogin();
  } catch (e) {
    localStorage.removeItem('bitacora_session');
  }
})();

function guardarSesionLocal() {
  localStorage.setItem('bitacora_session', JSON.stringify(session));
}

function goTo(viewName) {
  const VISTAS_ALUMNO = ['home', 'registro', 'historial'];
  const VISTAS_ADMIN = ['admin-home', 'adminUsuarios', 'adminRegistros', 'adminEditar', 'adminAccesos', 'adminCatalogos'];

  if (viewName !== 'login' && viewName !== 'cambiarPassword' && !session) {
    viewName = 'login';
  } else if (session && viewName !== 'cambiarPassword' && session.rol === 'alumno' && VISTAS_ADMIN.includes(viewName)) {
    viewName = 'home';
  } else if (session && viewName !== 'cambiarPassword' && session.rol !== 'alumno' && VISTAS_ALUMNO.includes(viewName)) {
    viewName = 'admin-home';
  }
  document.body.classList.toggle('login-mode', viewName === 'login' || viewName === 'cambiarPassword');
  document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
  const target = document.getElementById('view-' + viewName);
  if (target) target.classList.add('active');

  document.querySelectorAll('footer.nav button').forEach(b => b.classList.remove('active'));
  const navId = 'nav' + viewName.split('-').map(s => s.charAt(0).toUpperCase() + s.slice(1)).join('');
  const navBtn = document.getElementById(navId);
  if (navBtn) navBtn.classList.add('active');

  const titles = {
    home:            ['Inicio', 'Bitácora', 'Registro por grupo · Ingeniería en Sistemas'],
    registro:        ['Nuevo registro', 'Bitácora', 'Captura la clase según el horario'],
    historial:       ['Mis registros', 'Bitácora', 'Consulta lo que ya capturaste'],
    'admin-home':    ['Panel', 'Bitácora Admin', 'Gestión de usuarios y registros'],
    adminUsuarios:   ['Usuarios', 'Bitácora Admin', 'Alta de alumnos y administradores'],
    adminRegistros:  ['Registros', 'Bitácora Admin', 'Todos los grupos'],
    adminEditar:     ['Editar registro', 'Bitácora Admin', 'Modificación directa'],
    adminAccesos:    ['Accesos', 'Bitácora Admin', 'Historial de inicios de sesión'],
    adminCatalogos:  ['Catálogos', 'Bitácora Admin', 'Materias y profesores disponibles']
  }[viewName];
  if (titles) {
    document.getElementById('mastheadEyebrow').textContent = titles[0];
    document.getElementById('mastheadTitle').textContent = titles[1];
    document.getElementById('mastheadSub').textContent = titles[2];
  }
  window.scrollTo(0, 0);

  if (viewName === 'historial') renderHistorial();
  if (viewName === 'adminUsuarios') renderUsuarios();
  if (viewName === 'adminRegistros') renderRegistrosAdmin();
  if (viewName === 'adminAccesos') renderAccesos();
  if (viewName === 'adminCatalogos') renderCatalogosAdmin();
  if (viewName === 'registro') cargarCatalogos().catch(() => {});
}

/* ---------------- LOGIN ---------------- */
async function handleLogin() {
  const matricula = document.getElementById('matricula').value.trim().toLowerCase();
  const password = document.getElementById('password').value;
  const errorEl = document.getElementById('loginError');
  errorEl.classList.remove('show');

  if (!matricula || !password) {
    errorEl.textContent = 'Ingresa tu matrícula y contraseña.';
    errorEl.classList.add('show');
    return;
  }

  const boton = document.querySelector('#view-login button.btn-primary');
  boton.disabled = true; boton.textContent = 'Entrando...';

  try {
    const data = await apiFetch('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ matricula, password })
    });

    session = {
      token: data.token,
      matricula: data.usuario.matricula,
      rol: data.usuario.rol,
      nombre: data.usuario.nombre,
      grupo: data.usuario.grupo,
      debeCambiarPassword: data.usuario.debeCambiarPassword
    };
    guardarSesionLocal();

    if (session.debeCambiarPassword) {
      goTo('cambiarPassword');
      return;
    }
    afterLogin();
  } catch (err) {
    errorEl.textContent = err.message;
    errorEl.classList.add('show');
  } finally {
    boton.disabled = false; boton.textContent = 'Entrar';
  }
}

async function handleCambiarPassword() {
  const nueva = document.getElementById('cpNueva').value;
  const confirmar = document.getElementById('cpConfirmar').value;
  const errorEl = document.getElementById('cpError');
  errorEl.classList.remove('show');

  if (nueva.length < 6) {
    errorEl.textContent = 'La contraseña debe tener al menos 6 caracteres.';
    errorEl.classList.add('show');
    return;
  }
  if (nueva !== confirmar) {
    errorEl.textContent = 'Las dos contraseñas no coinciden.';
    errorEl.classList.add('show');
    return;
  }

  try {
    await apiFetch('/auth/password', { method: 'PUT', body: JSON.stringify({ nueva }) });
    session.debeCambiarPassword = false;
    guardarSesionLocal();
    document.getElementById('cpNueva').value = '';
    document.getElementById('cpConfirmar').value = '';
    afterLogin();
  } catch (err) {
    errorEl.textContent = err.message;
    errorEl.classList.add('show');
  }
}

function handleLogout() {
  session = null;
  localStorage.removeItem('bitacora_session');
  document.getElementById('userChip').style.display = 'none';
  document.getElementById('matricula').value = '';
  document.getElementById('password').value = '';
  document.getElementById('navAlumno').style.display = 'none';
  document.getElementById('navAdmin').style.display = 'none';
  goTo('login');
}

function afterLogin() {
  document.getElementById('userChip').style.display = 'flex';
  const rolLabel = { alumno: 'jefe de grupo', admin: 'administrador', admin_lectura: 'administrador' }[session.rol];
  document.getElementById('userChipText').textContent = session.matricula.toUpperCase() + ' · ' + rolLabel;

  if (session.rol === 'alumno') {
    document.getElementById('navAlumno').style.display = 'flex';
    document.getElementById('navAdmin').style.display = 'none';
    goTo('home');
  } else {
    document.getElementById('navAlumno').style.display = 'none';
    document.getElementById('navAdmin').style.display = 'flex';
    document.getElementById('cardNuevoUsuario').style.display = session.rol === 'admin_lectura' ? 'none' : '';
    goTo('admin-home');
  }
}

/* ---------------- FORMULARIO DE REGISTRO (alumno) ---------------- */
function selectAsistencia(val) {
  asistenciaVal = val;
  document.querySelectorAll('#asistenciaSeg button').forEach(b => b.classList.toggle('active', b.dataset.val === val));
  updateEvidenceRequirement();
}

function updateEvidenceRequirement() {
  const needsEvidencia = asistenciaVal === 'falta' || asistenciaVal === 'retardo';
  document.getElementById('evidenceLabelReq').classList.toggle('req', needsEvidencia);
}

/* ---------------- CATÁLOGOS DE MATERIAS Y PROFESORES ---------------- */
function llenarSelectCatalogo(selectId, elementos, placeholder, valorActual = '') {
  const select = document.getElementById(selectId);
  if (!select) return;

  const nombres = elementos.map(item => item.nombre);
  // Conserva el valor de un registro histórico aunque el administrador ya
  // haya eliminado esa opción del catálogo.
  if (valorActual && !nombres.includes(valorActual)) nombres.push(valorActual);

  select.innerHTML = '<option value="">' + escapeHtml(placeholder) + '</option>' +
    nombres.map(nombre => `<option value="${escapeHtml(nombre)}">${escapeHtml(nombre)}</option>`).join('');
  select.value = valorActual;
}

async function cargarCatalogos({ forzar = false, materiaActual = '', profesorActual = '' } = {}) {
  if (forzar || catalogosCache.materias.length === 0 || catalogosCache.profesores.length === 0) {
    try {
      catalogosCache = await apiFetch('/catalogos');
    } catch (err) {
      llenarSelectCatalogo('materia', [], 'No se pudieron cargar las materias');
      llenarSelectCatalogo('maestro', [], 'No se pudieron cargar los profesores');
      throw err;
    }
  }

  const materiaSeleccionada = materiaActual || document.getElementById('materia')?.value || '';
  const profesorSeleccionado = profesorActual || document.getElementById('maestro')?.value || '';
  llenarSelectCatalogo('materia', catalogosCache.materias, 'Selecciona una materia', materiaSeleccionada);
  llenarSelectCatalogo('maestro', catalogosCache.profesores, 'Selecciona un profesor', profesorSeleccionado);
  llenarSelectCatalogo('edMateria', catalogosCache.materias, 'Selecciona una materia', materiaActual);
  llenarSelectCatalogo('edMaestro', catalogosCache.profesores, 'Selecciona un profesor', profesorActual);
  return catalogosCache;
}

async function renderCatalogosAdmin() {
  const puedeEditar = session.rol === 'admin';
  document.getElementById('catalogoControles').style.display = puedeEditar ? '' : 'none';
  document.getElementById('catalogoLecturaHint').style.display = puedeEditar ? 'none' : 'block';
  const materiasCont = document.getElementById('listaMaterias');
  const profesoresCont = document.getElementById('listaProfesores');
  materiasCont.innerHTML = '<p class="hint">Cargando...</p>';
  profesoresCont.innerHTML = '<p class="hint">Cargando...</p>';

  try {
    await cargarCatalogos({ forzar: true });
  } catch (err) {
    const mensaje = '<p class="hint">' + escapeHtml(err.message) + '</p>';
    materiasCont.innerHTML = mensaje;
    profesoresCont.innerHTML = mensaje;
    return;
  }

  const renderLista = (elementos, tipo) => elementos.length === 0
    ? '<p class="hint">No hay elementos agregados.</p>'
    : elementos.map(item => `<div class="list-row" style="cursor:default;">
        <span>${escapeHtml(item.nombre)}</span>
        ${puedeEditar ? `<button type="button" class="btn-danger" style="width:auto; padding:6px 10px; font-size:12px;" onclick="handleEliminarCatalogo('${item._id}', '${tipo}')">Eliminar</button>` : ''}
      </div>`).join('');

  materiasCont.innerHTML = renderLista(catalogosCache.materias, 'materia');
  profesoresCont.innerHTML = renderLista(catalogosCache.profesores, 'profesor');
}

async function handleAgregarCatalogo() {
  const tipo = document.getElementById('catalogoTipo').value;
  const nombreEl = document.getElementById('catalogoNombre');
  const nombre = nombreEl.value.trim();
  const errorEl = document.getElementById('catalogoError');
  const successEl = document.getElementById('catalogoSuccess');
  errorEl.classList.remove('show');
  successEl.classList.remove('show');

  if (!nombre) {
    errorEl.textContent = 'Escribe el nombre que deseas agregar.';
    errorEl.classList.add('show');
    return;
  }

  try {
    await apiFetch('/catalogos', {
      method: 'POST',
      body: JSON.stringify({ tipo, nombre })
    });
    nombreEl.value = '';
    successEl.classList.add('show');
    await renderCatalogosAdmin();
  } catch (err) {
    errorEl.textContent = err.message;
    errorEl.classList.add('show');
  }
}

async function handleEliminarCatalogo(id, tipo) {
  const etiqueta = tipo === 'materia' ? 'esta materia' : 'este profesor';
  if (!confirm('¿Eliminar ' + etiqueta + ' de las opciones disponibles? Los registros anteriores no cambiarán.')) return;
  try {
    await apiFetch('/catalogos/' + encodeURIComponent(id), { method: 'DELETE' });
    await renderCatalogosAdmin();
  } catch (err) {
    alert('No se pudo eliminar: ' + err.message);
  }
}

// Se sigue comprimiendo en el navegador con <canvas> y se manda como texto
// base64 dentro del JSON al backend (que la guarda tal cual en Mongo).
function comprimirImagen(file, maxDim = 900, calidad = 0.6) {
  return new Promise((resolve, reject) => {
    const lector = new FileReader();
    lector.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        let w = img.width, h = img.height;
        if (w > h && w > maxDim) { h = Math.round(h * maxDim / w); w = maxDim; }
        else if (h >= w && h > maxDim) { w = Math.round(w * maxDim / h); h = maxDim; }
        const canvas = document.createElement('canvas');
        canvas.width = w; canvas.height = h;
        canvas.getContext('2d').drawImage(img, 0, 0, w, h);
        resolve(canvas.toDataURL('image/jpeg', calidad));
      };
      img.onerror = reject;
      img.src = e.target.result;
    };
    lector.onerror = reject;
    lector.readAsDataURL(file);
  });
}

async function handleEvidencia(e) {
  const file = e.target.files[0];
  if (!file) return;
  document.getElementById('evidenceLabel').textContent = 'Comprimiendo foto...';
  try {
    evidenciaDataUrl = await comprimirImagen(file);
    const kb = Math.round(evidenciaDataUrl.length / 1024);
    document.getElementById('evidenceLabel').textContent = '1 foto adjunta (' + kb + ' KB) · toca para cambiarla';
    document.getElementById('evidenceBox').classList.remove('required-missing');
    const thumbs = document.getElementById('thumbs');
    thumbs.innerHTML = '';
    const img = document.createElement('img');
    img.src = evidenciaDataUrl;
    thumbs.appendChild(img);
  } catch (err) {
    document.getElementById('evidenceLabel').textContent = 'No se pudo procesar la foto, intenta de nuevo.';
  }
}

async function handleSubmitRegistro(e) {
  e.preventDefault();
  const materia = document.getElementById('materia').value;
  const maestro = document.getElementById('maestro').value;
  const horaInicio = document.getElementById('horaInicio').value;
  const horaFin = document.getElementById('horaFin').value;
  const observaciones = document.getElementById('observaciones').value.trim();
  const successEl = document.getElementById('registroSuccess');
  const errorEl = document.getElementById('registroError');
  successEl.classList.remove('show');
  errorEl.classList.remove('show');

  const faltantes = [];
  if (!materia) faltantes.push('materia');
  if (!maestro) faltantes.push('maestro');
  if (!horaInicio) faltantes.push('hora de inicio');
  if (!horaFin) faltantes.push('hora de fin');
  if (!asistenciaVal) faltantes.push('si se impartió la clase');
  const needsEvidencia = asistenciaVal === 'falta' || asistenciaVal === 'retardo';
  if (needsEvidencia && !evidenciaDataUrl) faltantes.push('foto de evidencia');

  if (faltantes.length > 0) {
    errorEl.textContent = 'Ingresa todos los datos obligatorios: ' + faltantes.join(', ') + '.';
    errorEl.classList.add('show');
    document.getElementById('evidenceBox').classList.toggle('required-missing', needsEvidencia && !evidenciaDataUrl);
    return false;
  }

  const boton = e.target.querySelector('button[type=submit]');
  boton.disabled = true; boton.textContent = 'Guardando...';

  try {
    await apiFetch('/registros', {
      method: 'POST',
      body: JSON.stringify({
        materia, maestro, horaInicio, horaFin,
        asistencia: asistenciaVal,
        observaciones,
        evidencia: evidenciaDataUrl,
        grupo: session.grupo
      })
    });
    successEl.classList.add('show');
    resetForm();
  } catch (err) {
    errorEl.textContent = err.message;
    errorEl.classList.add('show');
  } finally {
    boton.disabled = false; boton.textContent = 'Guardar registro';
  }
  return false;
}

function resetForm() {
  document.getElementById('formRegistro').reset();
  asistenciaVal = null;
  evidenciaDataUrl = null;
  document.querySelectorAll('#asistenciaSeg button').forEach(b => b.classList.remove('active'));
  document.getElementById('evidenceLabel').textContent = 'Toca para tomar o subir una foto';
  document.getElementById('evidenceBox').classList.remove('required-missing');
  document.getElementById('thumbs').innerHTML = '';
  updateEvidenceRequirement();
}

/* ---------------- HISTORIAL (alumno) ---------------- */
function fechaDe(ts) { return ts ? new Date(ts) : null; }
function msDe(ts) { return ts ? new Date(ts).getTime() : 0; }
function ultimaVersion(registro) { return registro.versiones[registro.versiones.length - 1]; }

function calcularFueraDeHorario(version) {
  const capturado = fechaDe(version.capturadoEn);
  if (!capturado || !version.horaFin) return false;
  const [hF, mF] = version.horaFin.split(':').map(Number);
  const limite = new Date(capturado);
  limite.setHours(hF, mF, 0, 0);
  return capturado > limite;
}

async function renderHistorial() {
  const cont = document.getElementById('historialList');
  cont.innerHTML = '<p class="card-label">Mis registros</p><p class="hint">Cargando...</p>';

  let registros;
  try {
    registros = await apiFetch('/registros/mios');
  } catch (err) {
    cont.innerHTML = '<p class="card-label">Mis registros</p><p class="hint">' + escapeHtml(err.message) + '</p>';
    return;
  }

  if (registros.length === 0) {
    cont.innerHTML = '<p class="card-label">Mis registros</p><p class="hint">Todavía no has capturado ningún registro.</p>';
    return;
  }
  const asistLabel = { normal: 'Clase normal', retardo: 'Retardo', falta: 'Falta' };
  cont.innerHTML = '<p class="card-label">Mis registros</p>' + registros.map(r => {
    const v = ultimaVersion(r);
    return `<div class="list-row">
      <div>
        <strong style="font-family:var(--font-display); font-size:14px;">${escapeHtml(r.materia)}</strong>
        <div class="meta">${escapeHtml(r.maestro)} · ${asistLabel[v.asistencia]}${v.evidencia ? ' · 📷' : ''}${r.versiones.length > 1 ? ' · editado ' + (r.versiones.length - 1) + '×' : ''}</div>
      </div>
      <span style="font-family:var(--font-mono); font-size:11px; color:var(--text-soft);">${escapeHtml(v.horaInicio)}–${escapeHtml(v.horaFin)}</span>
    </div>`;
  }).join('');
}

/* ---------------- ADMIN: USUARIOS ---------------- */
async function handleAddUsuario() {
  const matricula = document.getElementById('nuMatricula').value.trim().toLowerCase();
  const nombre = document.getElementById('nuNombre').value.trim();
  const grupo = document.getElementById('nuGrupo').value.trim();
  const rol = document.getElementById('nuRol').value;
  const passwordTemp = document.getElementById('nuPassword').value;
  const errorEl = document.getElementById('usuarioError');
  const successEl = document.getElementById('usuarioSuccess');
  errorEl.classList.remove('show'); successEl.classList.remove('show');

  if (!matricula || !nombre || !passwordTemp) {
    errorEl.textContent = 'Ingresa todos los datos obligatorios: matrícula, nombre y contraseña temporal.';
    errorEl.classList.add('show');
    return;
  }

  try {
    await apiFetch('/usuarios', {
      method: 'POST',
      body: JSON.stringify({ matricula, nombre, grupo, rol, passwordTemp })
    });
    successEl.classList.add('show');
    document.getElementById('nuMatricula').value = '';
    document.getElementById('nuNombre').value = '';
    document.getElementById('nuGrupo').value = '';
    document.getElementById('nuPassword').value = '';
    document.getElementById('nuRol').value = 'alumno';
    renderUsuarios();
  } catch (err) {
    errorEl.textContent = err.message;
    errorEl.classList.add('show');
  }
}

async function renderUsuarios() {
  const cont = document.getElementById('listaUsuarios');
  cont.innerHTML = '<p class="hint">Cargando...</p>';

  let usuarios;
  try {
    usuarios = await apiFetch('/usuarios');
  } catch (err) {
    cont.innerHTML = '<p class="hint">' + escapeHtml(err.message) + '</p>';
    return;
  }

  if (usuarios.length === 0) {
    cont.innerHTML = '<p class="hint">Aún no hay usuarios además de tu cuenta.</p>';
    return;
  }
  const puedeEditar = session.rol === 'admin';
  cont.innerHTML = usuarios.map(u => {
    const mat = u._id;
    const claseBadge = u.rol === 'alumno' ? 'alumno' : 'admin';
    const textoBadge = u.rol === 'alumno' ? 'Alumno' : 'Admin';
    return `<div class="list-row" style="cursor:default;">
      <div>
        <strong style="font-family:var(--font-display); font-size:14px;">${escapeHtml(u.nombre || mat.toUpperCase())}</strong>
        <div class="meta">${escapeHtml(mat.toUpperCase())} · ${escapeHtml(u.grupo || '—')}</div>
      </div>
      <div style="display:flex; align-items:center; gap:10px;">
        <span class="role-badge ${claseBadge}">${textoBadge}</span>
        ${puedeEditar ? `<button type="button" class="btn-danger" style="width:auto; padding:6px 10px; font-size:12px;" onclick="handleEliminarUsuario('${mat}')">Eliminar</button>` : ''}
      </div>
    </div>`;
  }).join('');
}

async function handleEliminarUsuario(matricula) {
  if (matricula === session.matricula) {
    alert('No puedes eliminar tu propia cuenta desde aquí. Pide a otro administrador que lo haga.');
    return;
  }
  if (!confirm('¿Eliminar a ' + matricula.toUpperCase() + '? Deja de poder entrar a la app de inmediato. Sus registros anteriores NO se borran.')) return;
  try {
    await apiFetch('/usuarios/' + encodeURIComponent(matricula), { method: 'DELETE' });
    renderUsuarios();
  } catch (err) {
    alert('No se pudo eliminar: ' + err.message);
  }
}

/* ---------------- ADMIN: TODOS LOS REGISTROS ---------------- */
async function renderRegistrosAdmin() {
  const cont = document.getElementById('listaRegistrosAdmin');
  cont.innerHTML = '<p class="hint">Cargando...</p>';

  let registros;
  try {
    registros = await apiFetch('/registros');
  } catch (err) {
    cont.innerHTML = '<p class="hint">' + escapeHtml(err.message) + '</p>';
    return;
  }

  if (registros.length === 0) {
    cont.innerHTML = '<p class="hint">Todavía no hay registros capturados.</p>';
    return;
  }
  const asistLabel = { normal: 'Clase normal', retardo: 'Retardo', falta: 'Falta' };
  cont.innerHTML = registros.map(r => {
    const v = ultimaVersion(r);
    return `<div class="list-row" onclick="abrirEdicion('${r._id}')">
      <div>
        <strong style="font-family:var(--font-display); font-size:14px;">${escapeHtml(r.materia)}</strong>
        <div class="meta">${escapeHtml(r.matricula.toUpperCase())} · ${escapeHtml(r.maestro)} · ${asistLabel[v.asistencia]}${v.evidencia ? ' · 📷' : ''}${calcularFueraDeHorario(v) ? ' · fuera de horario' : ''}${r.versiones.length > 1 ? ' · v' + r.versiones.length : ''}</div>
      </div>
      <span style="font-family:var(--font-mono); font-size:11px; color:var(--text-soft);">${escapeHtml(v.horaInicio)}–${escapeHtml(v.horaFin)}</span>
    </div>`;
  }).join('');
}

async function abrirEdicion(registroId) {
  let registro;
  try {
    registro = await apiFetch('/registros/' + registroId);
  } catch (err) {
    alert('No se pudo abrir el registro: ' + err.message);
    return;
  }
  const v = ultimaVersion(registro);
  editContext = { registroId };

  try {
    await cargarCatalogos({ materiaActual: registro.materia, profesorActual: registro.maestro });
  } catch (err) {
    alert('No se pudieron cargar los catálogos: ' + err.message);
    return;
  }

  document.getElementById('editHeader').textContent = 'Editar · ' + registro.matricula.toUpperCase();
  document.getElementById('edMateria').value = registro.materia;
  document.getElementById('edMaestro').value = registro.maestro;
  document.getElementById('edHoraInicio').value = v.horaInicio;
  document.getElementById('edHoraFin').value = v.horaFin;
  document.getElementById('edObservaciones').value = v.observaciones || '';
  if (v.evidencia) {
    document.getElementById('edEvidenciaLabel').style.display = 'block';
    document.getElementById('edEvidenciaBox').style.display = 'block';
    document.getElementById('edEvidenciaImg').src = v.evidencia;
  } else {
    document.getElementById('edEvidenciaLabel').style.display = 'none';
    document.getElementById('edEvidenciaBox').style.display = 'none';
    document.getElementById('edEvidenciaImg').src = '';
  }
  editAsistenciaVal = v.asistencia;
  document.querySelectorAll('#edAsistenciaSeg button').forEach(b => b.classList.toggle('active', b.dataset.val === v.asistencia));
  const capturado = fechaDe(v.capturadoEn);
  document.getElementById('edMeta').textContent =
    'Capturado ' + (capturado ? capturado.toLocaleString('es-MX') : '—') +
    (calcularFueraDeHorario(v) ? ' (fuera de horario)' : '') +
    ' · ' + registro.versiones.length + (registro.versiones.length === 1 ? ' versión' : ' versiones') +
    ' · última por ' + (v.editadoPorAdmin ? v.editadoPorAdmin.toUpperCase() + ' (admin)' : (v.autor || '—').toUpperCase()) +
    '. Guardar agrega una versión nueva; las anteriores no se borran.';

  const soloLectura = session.rol === 'admin_lectura';
  document.querySelectorAll('#formEditar input, #formEditar select, #formEditar textarea, #formEditar button').forEach(el => el.disabled = soloLectura);
  document.querySelectorAll('#edAsistenciaSeg button').forEach(b => b.disabled = soloLectura);

  goTo('adminEditar');
}

function selectAsistenciaEdit(val) {
  editAsistenciaVal = val;
  document.querySelectorAll('#edAsistenciaSeg button').forEach(b => b.classList.toggle('active', b.dataset.val === val));
}

async function handleGuardarEdicion(e) {
  e.preventDefault();
  const errorEl = document.getElementById('editError');
  const successEl = document.getElementById('editSuccess');
  errorEl.classList.remove('show'); successEl.classList.remove('show');

  const materia = document.getElementById('edMateria').value;
  const maestro = document.getElementById('edMaestro').value;
  const horaInicio = document.getElementById('edHoraInicio').value;
  const horaFin = document.getElementById('edHoraFin').value;
  const observaciones = document.getElementById('edObservaciones').value.trim();

  if (!materia || !maestro || !horaInicio || !horaFin || !editAsistenciaVal) {
    errorEl.textContent = 'Ingresa todos los datos obligatorios.';
    errorEl.classList.add('show');
    return false;
  }

  try {
    await apiFetch('/registros/' + editContext.registroId, {
      method: 'PUT',
      body: JSON.stringify({ materia, maestro, horaInicio, horaFin, asistencia: editAsistenciaVal, observaciones })
    });
    successEl.classList.add('show');
    renderRegistrosAdmin();
  } catch (err) {
    errorEl.textContent = err.message;
    errorEl.classList.add('show');
  }
  return false;
}

async function handleEliminarRegistro() {
  if (!confirm('¿Eliminar este registro? Esta acción no se puede deshacer.')) return;
  try {
    await apiFetch('/registros/' + editContext.registroId, { method: 'DELETE' });
    goTo('adminRegistros');
  } catch (err) {
    alert('No se pudo eliminar: ' + err.message);
  }
}

/* ---------------- ADMIN: ACCESOS ---------------- */
async function renderAccesos() {
  const cont = document.getElementById('listaAccesos');
  cont.innerHTML = '<p class="hint">Cargando...</p>';

  let accesos;
  try {
    accesos = await apiFetch('/accesos');
  } catch (err) {
    cont.innerHTML = '<p class="hint">' + escapeHtml(err.message) + '</p>';
    return;
  }

  if (accesos.length === 0) {
    cont.innerHTML = '<p class="hint">Aún no hay accesos registrados.</p>';
    return;
  }
  cont.innerHTML = accesos.map(a => {
    const fecha = fechaDe(a.timestamp);
    return `<div class="list-row">
      <strong style="font-family:var(--font-mono); font-size:13px;">${escapeHtml(a.matricula.toUpperCase())}</strong>
      <span style="font-family:var(--font-mono); font-size:11px; color:var(--text-soft);">${fecha ? fecha.toLocaleString('es-MX') : '—'}</span>
    </div>`;
  }).join('');
}
