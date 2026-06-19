# Áurea Print Bridge RESCATE v0.2.1

Este APK es SOLO el puente de impresión Bluetooth. Úsalo para la iMin/tablet fija. No trae POS integrado ni Urovo; evita el error `android.device.PrinterManager`.

Configuración para mañana:

- API base: `http://print.kmo.lat`
- Token: `kmo_aurea_2026`
- Sucursal: `1`
- Puente ON solo en la iMin/tablet fija
- Tablets de meseros: usan la web/POS, NO puente

La notificación del servicio queda silenciosa. Si Android insiste en sonido: Ajustes > Apps > Áurea Print Bridge > Notificaciones > Silencioso.

---

# Áurea POS Bridge v0.2

APK Android para que una iMin/tablet/celular funcione como puente persistente de impresión para Áurea.

## Qué cambió en v0.2

- Servicio persistente en segundo plano con notificación fija.
- Arranque automático opcional al prender/reiniciar el dispositivo.
- WakeLock parcial para evitar que Android duerma el puente durante operación.
- Modo kiosko visual usando fijación de pantalla (`startLockTask`).
- Modo varios mini-puentes sin duplicar **si el backend implementa claim atómico**.
- Botón para abrir ajustes de batería y poner la app sin restricciones.
- Device ID único por tablet/celular.
- Lista nombre + MAC de Bluetooth emparejadas.
- Tickets de prueba por área.

## Flujo recomendado

```text
Meseros en iPad/celular/tablet
        ↓
Áurea crea print_jobs separados por área
        ↓
Una o varias APK Áurea POS Bridge reclaman jobs pendientes
        ↓
Cada job queda tomado por un solo dispositivo
        ↓
Impresión Bluetooth por área:
- barra_fria
- barra_caliente
- bebidas
- caja
```

## Modo central vs mini-puentes

### Modo central

Una iMin/tablet fija tiene configuradas todas las impresoras.

```text
iMin fija:
barra_fria -> MAC 1
barra_caliente -> MAC 2
bebidas -> MAC 3
caja -> MAC 4 / impresora integrada si aparece como Bluetooth
```

### Modo mini-puentes

Varias tablets/celulares pueden tener configuradas solo las impresoras que les quedan cerca.

```text
Tablet 1 cerca de barra fria:
barra_fria -> MAC 1

Tablet 2 cerca de bebidas:
bebidas -> MAC 2

Tablet 3 cerca de barra caliente:
barra_caliente -> MAC 3
```

Para que esto NO duplique comandas, el servidor debe responder `/api/print-jobs/claim` de forma atómica.

## Contrato de backend sin duplicados

### Reclamar trabajos pendientes

La APK llama cada 2 segundos:

```http
POST /api/print-jobs/claim?token=TOKEN
Content-Type: application/json
```

Body:

```json
{
  "branch_id": "1",
  "device_id": "aurea-uuid-del-dispositivo",
  "areas": ["barra_fria", "barra_caliente", "bebidas"],
  "max_jobs": 10,
  "client_time": "2026-06-19 20:00:00"
}
```

Respuesta:

```json
{
  "jobs": [
    {
      "id": 123,
      "printer_area": "barra_fria",
      "title": "MESA 4",
      "content": "MESERO: Luis\n\n2x Aguachile\n  Nota: sin cebolla"
    }
  ]
}
```

El backend debe hacer algo equivalente a:

```sql
UPDATE print_jobs
SET status = 'printing', claimed_by = :device_id, claimed_at = NOW()
WHERE id IN (
  SELECT id FROM print_jobs
  WHERE branch_id = :branch_id
    AND printer_area IN (:areas)
    AND status IN ('pending', 'error')
    AND (claimed_at IS NULL OR claimed_at < NOW() - INTERVAL '90 seconds')
  ORDER BY created_at ASC
  LIMIT :max_jobs
  FOR UPDATE SKIP LOCKED
)
RETURNING *;
```

La idea es que dos tablets no puedan tomar el mismo job al mismo tiempo.

### Marcar estado

```http
POST /api/print-jobs/{id}/status?token=TOKEN
Content-Type: application/json
```

Body:

```json
{
  "status": "printed",
  "error": "",
  "device_id": "aurea-uuid-del-dispositivo",
  "device_time": "2026-06-19 20:00:04"
}
```

Estados esperados:

```text
pending
printing
printed
error
```

## Compatibilidad con impresora integrada iMin

Si la impresora integrada aparece en Bluetooth como `InnerPrinter` o `BluetoothPrinter`, se puede mapear igual que una impresora externa usando su MAC.

Si no aparece como Bluetooth, hay que integrar el SDK específico de iMin en la app de Áurea o en este bridge. Esta versión deja listo el puente Bluetooth y el servicio persistente; la integración directa por SDK iMin se debe validar con el modelo exacto.

## Cómo compilar en GitHub

1. Sube estos archivos a la raíz del repo.
2. Entra a **Actions**.
3. Ejecuta **Build Áurea Print Bridge APK**.
4. Descarga el artifact `aurea-print-bridge-debug-apk`.
5. Instala `app-debug.apk`.

## Requisitos del Android puente

- Android 6 o superior.
- Bluetooth clásico.
- Internet/Wi-Fi.
- Impresoras emparejadas desde Ajustes > Bluetooth.
- En batería: poner la app como **Sin restricciones / No optimizar**.
- Para operación real: dejar el equipo conectado al cargador.

## Configuración en la app

- API base URL
- Token de sucursal
- Branch/Sucursal ID
- MAC Barra fría
- MAC Barra caliente
- MAC Bebidas
- MAC Caja/Ticket opcional
- Iniciar al prender/reiniciar
- Modo sin duplicados / varios mini-puentes
- Mantener pantalla encendida

## Notas importantes

- El botón “Modo kiosko visual” usa la fijación de pantalla de Android. No es un modo device-owner empresarial completo.
- Si el backend no tiene `/claim`, la APK cae al endpoint viejo `GET /api/print-jobs`; eso sirve para un solo puente, pero puede duplicar si hay varias tablets imprimiendo.
- El cliente no debe elegir impresora. Áurea debe separar por categorías y crear `print_jobs` por área.
