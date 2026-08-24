# Control de Asistencia — Bitácora

App de control de asistencia docente. Migrada de Firebase a **MongoDB + API propia**.

Las materias y los profesores se administran desde la pestaña **Catálogos**.
Los usuarios `admin` pueden agregarlos o eliminarlos; `admin_lectura` solamente
puede consultarlos. Eliminar una opción no modifica los registros históricos.

Cada materia se asigna a uno o varios grupos mediante su código. Se aceptan
`RBM11` a `RBM59` para TSU y `IMTM11` a `IMTM59` para Ingeniería: el primer
número identifica el cuatrimestre y el segundo identifica el grupo. Por ejemplo,
una materia compartida por los dos grupos de primer cuatrimestre puede asignarse
a `RBM11, RBM12`.

```
frontend/   → HTML/CSS/JS estático, se sube a GitHub Pages
backend/    → API en Node + Express, se sube a Render (u otro hosting con Node)
```

## 1. Base de datos (MongoDB Atlas)

1. Crea una cuenta gratis en [mongodb.com/atlas](https://www.mongodb.com/atlas) y un cluster **M0 (gratis)**.
2. En **Database Access**, crea un usuario de base de datos con contraseña.
3. En **Network Access**, agrega `0.0.0.0/0` (permite conexión desde cualquier IP — Render usa IPs dinámicas).
4. En **Database → Connect → Drivers**, copia la cadena de conexión (empieza con `mongodb+srv://...`).

## 2. Backend (Render)

1. Sube la carpeta `backend/` a un repositorio de GitHub (puede ser este mismo repo).
2. En [render.com](https://render.com), **New → Web Service**, conecta el repo y selecciona la carpeta `backend/` como *Root Directory*.
3. Configura:
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`
4. En **Environment**, agrega estas variables (mismos nombres que `.env.example`):
   - `MONGODB_URI` → la cadena de conexión de Atlas (agrégale el usuario/contraseña que creaste)
   - `JWT_SECRET` → genera una con: `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`
   - `FRONTEND_ORIGIN` → la URL de tu GitHub Pages, ej. `https://tuusuario.github.io`
5. Despliega. Cuando termine, copia la URL que te da Render (ej. `https://bitacora-backend.onrender.com`).

### Crear el primer administrador

Con el repo del backend clonado en tu máquina y tu `.env` local apuntando al mismo `MONGODB_URI` de Atlas:

```bash
cd backend
npm install
cp .env.example .env   # y llena tus valores reales
node scripts/crearAdmin.js tuMatricula "Tu Nombre" contraseñaTemporal123
```

Ese usuario podrá entrar y dar de alta a los demás desde el panel de admin — ya no necesitas volver a correr este script.

## 3. Frontend (GitHub Pages)

1. Abre `frontend/app.js` y en la primera línea cambia:
   ```js
   const API_BASE = 'https://TU-BACKEND.onrender.com';
   ```
   por la URL real que te dio Render en el paso anterior.
2. En GitHub: **Settings → Pages**, selecciona la rama y la carpeta `/frontend` como fuente (o `/root` si subes esos 3 archivos sueltos a un repo aparte).
3. Espera unos minutos y tu app queda en `https://tuusuario.github.io/Control-de-asistencia-/`.

## Notas

- El plan gratis de Render "duerme" el backend tras un rato sin uso; la primera petición después de eso tarda ~30-50 segundos en responder (es normal, no está roto).
- Los tokens de sesión duran 12 horas; después de eso hay que volver a iniciar sesión.
- Nunca subas tu archivo `.env` real (con las contraseñas) a GitHub — el `.gitignore` del backend ya lo excluye.
