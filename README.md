# Áurea Mesero Pedidos v0.1.0

APK ligera para tablets de meseros. No imprime. Solo manda pedidos al backend `print.kmo.lat` usando el contrato correcto de Áurea Print Jobs.

## Uso en instalación

- Instalar esta APK en tablets de meseros.
- Instalar `Áurea Print Bridge` solo en la iMin/tablet fija que imprime por Bluetooth.
- API base recomendada: `http://print.kmo.lat`
- Token: `kmo_aurea_2026`
- Sucursal: `1`

## Qué hace

POST a:

`/api/orders/confirm?token=TOKEN`

con payload:

```json
{
  "branch_id": "1",
  "table_id": "4",
  "table_name": "Mesa 4",
  "waiter_name": "Luis",
  "idempotency_key": "app-...",
  "items": [
    {"name":"Tacos de arrachera","qty":2,"price":95,"printer_area":"barra_caliente"},
    {"name":"Limonada mineral","qty":3,"price":35,"printer_area":"bebidas"}
  ]
}
```

El backend crea los `print_jobs`; el puente los imprime.

## Compilar en GitHub

Sube el contenido a un repo y ejecuta Actions. Si `.github` no sube, crea manualmente:

`.github/workflows/build-apk.yml`
