# BLE Protocol v1

Este documento define una primera propuesta de contrato entre firmware y app.

## Objetivo

Transmitir bloques de muestras IMU de forma robusta y simple para el MVP.

## Principios

- formato binario compacto
- deteccion de perdidas, no reenvio
- parsing simple en firmware y app
- separacion entre datos frecuentes y metadatos ocasionales

## Variables del MVP

Por muestra:

- `ax`
- `ay`
- `az`
- `gx`
- `gy`
- `gz`

Por bloque:

- `packet_id`
- `timestamp_block_start_ms`
- `sample_start_index`
- `sample_count`
- `step_count_total` opcional
- `double_tap_flag` opcional
- `battery_level` opcional
- `status_flags` opcional

## Codificacion sugerida

- IMU: `int16`
- `packet_id`: `uint32`
- `timestamp_block_start_ms`: `uint64` o `uint32` si el reloj local lo permite
- `sample_start_index`: `uint32`
- `sample_count`: `uint16`
- `battery_level`: `uint8`
- `status_flags`: `uint16`

## Estructura conceptual del bloque

```text
BlockHeader
  packet_id
  timestamp_block_start_ms
  sample_start_index
  sample_count
  flags

OptionalFields
  step_count_total
  double_tap_flag
  battery_level
  status_flags

Samples[sample_count]
  ax, ay, az, gx, gy, gz
```

## Observacion importante

Con `104 Hz` y `~2 s`, se esperan `208` muestras por bloque.
Si cada muestra son `6 * int16 = 12 bytes`, el payload bruto IMU ronda `2496 bytes` sin cabeceras.

Eso significa que:

- no cabe en una unica notificacion BLE
- el bloque logico debe fragmentarse en multiples chunks BLE

## Recomendacion practica

Separar dos niveles:

1. `Bloque logico`
2. `Chunk BLE`

El firmware crea un bloque logico y luego lo divide en chunks compatibles con MTU.
La app recompone el bloque antes de persistirlo.

## Contrato minimo para chunking

Cada chunk BLE deberia incluir:

- `packet_id`
- `chunk_index`
- `chunk_count`
- `payload_bytes`

La app solo marca el bloque como completo cuando recibe todos los chunks.

## Propuesta concreta para el MVP

La especificacion operativa y numerica queda detallada en:

- `docs/ble-transport-v1-concrete.md`

Ese documento fija:

- tamano de bloque logico
- formato de header
- formato de chunk BLE
- estrategia de reensamblado
- reglas de timeout y deteccion de perdidas

## Deteccion de fallos

La app debe registrar:

- salto en `packet_id`
- discontinuidad en `sample_start_index`
- bloque incompleto por timeout

## Versionado

Incluir un campo de version en el header o una caracteristica BLE de metadata.
Eso simplifica la evolucion del firmware sin romper la app.
