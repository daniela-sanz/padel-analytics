# Arquitectura propuesta

## Vision general

El sistema del MVP se divide en dos nodos:

1. `Wearable`
2. `App Android`

El wearable:

- adquiere IMU de forma continua
- empaqueta bloques
- transmite por BLE
- bufferiza temporalmente usando FIFO + RAM

La app:

- controla la sesion
- recibe bloques BLE
- persiste los datos localmente
- detecta perdidas por `packet_id`
- reconstruye la sesion
- calcula KPIs y genera visualizacion post-sesion

## Arquitectura logica de la app

Se recomienda una arquitectura en capas:

### 1. Presentation

- pantallas Compose
- view models
- estado de UI

### 2. Domain

- casos de uso
- reglas de negocio
- calculo de KPIs
- validacion de sesiones

### 3. Data

- BLE client
- parser de paquetes
- repositorios
- base de datos Room
- exportadores CSV y JSON

## Modulos funcionales

### BLE

Responsabilidades:

- escaneo de dispositivo
- conexion y reconexion
- suscripcion a notificaciones
- lectura de bateria y estado
- recepcion de bloques de datos

### Sesiones

Responsabilidades:

- crear sesion
- iniciar y detener captura
- asociar metadatos
- cerrar sesion correctamente

### Persistencia

Responsabilidades:

- guardar muestras IMU
- guardar bloques recibidos
- guardar eventos
- guardar incidencias de perdida

### Analitica

Responsabilidades:

- metricas por sesion
- resumen de carga
- graficas temporales
- deteccion simple de eventos offline

### Exportacion

Responsabilidades:

- exportar CSV crudo
- exportar resumen de sesion

## Flujo de datos

1. El usuario conecta el wearable.
2. La app inicia una sesion.
3. El wearable envia bloques BLE.
4. La app parsea cada paquete.
5. La app valida continuidad con `packet_id` y `sample_start_index`.
6. La app guarda datos en Room.
7. Al cerrar sesion, la app calcula KPIs.
8. La app muestra dashboard y permite exportar.

## Pantallas MVP

- `DeviceConnectionScreen`
- `SessionControlScreen`
- `RecordingStatusScreen`
- `SessionListScreen`
- `SessionDetailScreen`
- `SettingsScreen`

## Decisiones tecnicas iniciales

- frecuencia objetivo: `104 Hz`
- datos base: `ax, ay, az, gx, gy, gz`
- eventos: `step_count_total`, `double_tap_flag`
- envio BLE: bloques de `1-5 s`, arrancando en `~2 s`
- prioridad: robustez de datos sobre optimizacion extrema

## Riesgos a controlar

- MTU y fragmentacion BLE
- backpressure entre recepcion y escritura en BD
- reconexion y continuidad de sesion
- volumen de datos por sesion
- coherencia temporal entre timestamp de bloque y muestras
