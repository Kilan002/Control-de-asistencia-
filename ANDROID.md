# Aplicación Android y notificaciones

La aplicación reutiliza el frontend de `docs/` mediante Capacitor. Su identificador
es `com.controldocente.app` y las notificaciones se envían con Firebase Cloud
Messaging cuando se crea un registro con estado `falta` o cuando un administrador
cambia un registro a ese estado.

## Configurar Render

El archivo `google-services.json` solo configura el teléfono. Para que el backend
pueda enviar avisos, Firebase necesita una cuenta de servicio:

1. Abrir Firebase Console > Configuración del proyecto > Cuentas de servicio.
2. Seleccionar **Generar nueva clave privada** y descargar el JSON.
3. En Render, crear la variable `FIREBASE_SERVICE_ACCOUNT_JSON`.
4. Pegar como valor todo el contenido del JSON, en una sola variable.
5. No guardar ese JSON privado en Git ni compartirlo públicamente.

Render también debe conservar:

```text
FRONTEND_ORIGIN=https://kilan002.github.io
```

El backend permite adicionalmente `https://localhost`, que es el origen interno de
la aplicación Android.

## Compilar

Requisitos: Android Studio, Android SDK y Java 21.

```bash
npm install
npm run android:sync
npm run android:open
```

En Android Studio se puede ejecutar en un teléfono conectado o generar un APK con
**Build > Build App Bundles or APKs > Build APKs**. El APK de depuración también se
puede generar con:

```bash
npm run android:build
```

Cada vez que cambie `docs/`, hay que ejecutar `npm run android:sync` antes de volver
a compilar.

## Funcionamiento

- Solo `admin` y `admin_lectura` registran un token de dispositivo.
- Android solicita permiso de notificaciones en el primer inicio administrativo.
- Al cerrar sesión, el dispositivo se elimina de esa cuenta.
- Los tokens inválidos se depuran automáticamente.
- Un error de Firebase nunca impide guardar el registro de asistencia.
- Al tocar el aviso se abre el registro relacionado dentro de la aplicación.
