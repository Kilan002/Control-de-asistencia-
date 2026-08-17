const jwt = require('jsonwebtoken');

// Verifica el token JWT que manda el navegador en el header Authorization.
// Esto reemplaza a Firebase Auth: aquí es donde de verdad se sabe quién
// eres y qué rol tienes, y el navegador ya no puede mentir al respecto
// (a diferencia de antes, donde el rol solo se checaba en el JS del cliente).
function requiereSesion(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) {
    return res.status(401).json({ error: 'No hay sesión. Inicia sesión de nuevo.' });
  }
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET);
    req.usuario = payload; // { matricula, rol }
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Sesión inválida o expirada. Inicia sesión de nuevo.' });
  }
}

// Uso: requiereRol('admin') o requiereRol('admin', 'admin_lectura')
function requiereRol(...rolesPermitidos) {
  return (req, res, next) => {
    if (!req.usuario || !rolesPermitidos.includes(req.usuario.rol)) {
      return res.status(403).json({ error: 'No tienes permiso para hacer esto.' });
    }
    next();
  };
}

module.exports = { requiereSesion, requiereRol };
