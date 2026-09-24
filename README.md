# Xpra Android Client

Cliente ligero y nativo de Android para conectarse a escritorios remotos de **Xpra** sobre **Tailscale**.

## Características
- **Pantalla completa real:** Sin barras de navegación, sin zoom accidental por pellizco y sin el molesto efecto pull-to-refresh de los navegadores.
- **Botón flotante de Teclado:** Un botón discreto en la esquina inferior para desplegar el teclado de Android cuando lo necesites.
- **Configuración rápida:** Guarda tu IP de Tailscale y puerto (por defecto `http://100.94.216.124:9876`). Mantén presionado el botón flotante para cambiar de servidor.

---

## Cómo compilar el APK en GitHub Actions (Nube gratuita)

1. Crea un repositorio en GitHub (puede ser **Público** o **Privado**):
   ```bash
   cd ~/xpra-android-client
   git init
   git add .
   git commit -m "Initial commit Xpra Android Client"
   git branch -M main
   git remote add origin https://github.com/TU_USUARIO/TU_REPO.git
   git push -u origin main
   ```

2. Tan pronto hagas el `git push`, ve a la pestaña **Actions** en tu repositorio de GitHub.
3. Verás ejecutarse el flujo de trabajo **"Build Android APK"**.
4. En **1 minuto**, el build terminará con éxito y en la sección **Artifacts** podrás descargar el archivo:
   - `XpraClient-Debug.zip` (adentro contiene `app-debug.apk`).
5. Transfiérelo a tu teléfono o descárgalo directamente desde el navegador de tu celular, ¡e instálalo!
