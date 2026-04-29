# BLE Transport v1 - Diseno concreto

Este documento aterriza el protocolo BLE del MVP a un formato util para firmware y app.

## Objetivo

Definir una estrategia simple, robusta y facil de depurar para transportar bloques IMU por BLE.

## Capas

Hay dos niveles distintos:

1. `Bloque logico`
2. `Chunk BLE`

El wearable genera un bloque logico con varias muestras.
Ese bloque se fragmenta en multiples chunks BLE.
La app recompone todos los chunks y solo entonces acepta el bloque como completo.

## Decision clave para el MVP

No recomiendo empezar con bloques de `2 s`.

Aunque logicamente son validos, para un primer firmware y una primera app son demasiado grandes:

- `104 Hz * 2 s = 208 muestras`
- `208 * 12 bytes = 2496 bytes` solo de IMU

Para el primer corte del MVP, recomiendo:

- `bloque logico de 0.5 s`
- `52 muestras por bloque`

Eso da:

- `52 * 12 = 624 bytes` de payload IMU

Sigue requiriendo fragmentacion, pero ya es mucho mas manejable.

## Por que 0.5 s es mejor para arrancar

- menor latencia
- menos RAM ocupada en wearable
- menos impacto si se pierde un bloque
- reensamblado mas simple de depurar
- mas facil probar reconexion

Mas adelante, si todo va bien, podemos subir a `1 s` o `2 s`.

## Tipos de datos

Por muestra IMU:

- `ax`: `int16`
- `ay`: `int16`
- `az`: `int16`
- `gx`: `int16`
- `gy`: `int16`
- `gz`: `int16`

Total por muestra:

- `12 bytes`

## Bloque logico

### Campos del header

Propongo este header fijo:

```text
protocol_version         uint8   1 byte
block_type               uint8   1 byte
flags                    uint16  2 bytes
packet_id                uint32  4 bytes
timestamp_block_start_ms uint32  4 bytes
sample_start_index       uint32  4 bytes
sample_count             uint16  2 bytes
step_count_total         uint32  4 bytes
battery_level_pct        uint8   1 byte
reserved                 uint8   1 byte
status_flags             uint16  2 bytes
```

Tamano total del header:

- `26 bytes`

### Comentarios

- `protocol_version`: para evolucionar el contrato
- `block_type`: por ahora siempre IMU, pero deja abierta la puerta a otros tipos
- `flags`: indica presencia o no de campos/eventos
- `packet_id`: contador incremental por bloque logico
- `timestamp_block_start_ms`: reloj relativo del micro
- `sample_start_index`: indice global de la primera muestra del bloque
- `sample_count`: numero de muestras IMU incluidas
- `step_count_total`: contador acumulado
- `battery_level_pct`: bateria simplificada de `0..100`
- `status_flags`: bits para reconexion, overflow, warning, etc.

## Payload del bloque

Para `52` muestras:

- `52 * 12 = 624 bytes`

Tamano total del bloque logico:

- `26 + 624 = 650 bytes`

## Chunk BLE

## Suposicion practica

Para el MVP, conviene negociar MTU alta, pero no depender de una cifra exacta extrema.

Para que el sistema sea razonable, propongo disenar el chunk con:

- `payload util de chunk = 180 bytes`

Es una cifra conservadora y comoda para trabajar si la negociacion de MTU sale bien.

Si luego el firmware o Android nos obligan a otra cifra, solo cambia el troceado, no el bloque logico.

### Header de chunk

```text
protocol_version  uint8   1 byte
packet_id         uint32  4 bytes
chunk_index       uint8   1 byte
chunk_count       uint8   1 byte
payload_size      uint16  2 bytes
```

Tamano del header de chunk:

- `9 bytes`

### Payload de chunk

- hasta `180 bytes`

### Tamano maximo por notificacion

- `9 + 180 = 189 bytes`

## Cuantos chunks hacen falta

Para un bloque logico de `650 bytes`:

- `650 / 180 = 3.61`

Por tanto:

- `4 chunks BLE`

Distribucion posible:

- chunk 0 -> bytes `0..179`
- chunk 1 -> bytes `180..359`
- chunk 2 -> bytes `360..539`
- chunk 3 -> bytes `540..649`

## Serializacion recomendada

Orden recomendado:

1. serializar header de bloque
2. serializar muestras en orden exacto de captura
3. dividir el byte array completo en chunks
4. enviar chunks en orden creciente de `chunk_index`

Orden de muestras dentro del payload:

```text
sample_0: ax ay az gx gy gz
sample_1: ax ay az gx gy gz
...
sample_n: ax ay az gx gy gz
```

## Reensamblado en la app

La app debe mantener un buffer temporal por `packet_id`.

### Algoritmo

1. llega un chunk
2. leer `packet_id`, `chunk_index`, `chunk_count`, `payload_size`
3. si no existe buffer para ese `packet_id`, crearlo
4. guardar el payload en la posicion de `chunk_index`
5. marcar ese chunk como recibido
6. si estan todos los chunks, concatenar en orden
7. parsear el bloque logico completo
8. persistir
9. liberar el buffer temporal

## Timeout de bloque incompleto

La app no debe esperar para siempre.

Propongo:

- timeout de reensamblado: `3 s`

Si un `packet_id` no se completa dentro de ese tiempo:

- marcar bloque incompleto
- registrar incidencia
- liberar memoria

## Deteccion de perdidas

La app puede detectar problemas en tres niveles:

### 1. Salto de `packet_id`

Ejemplo:

- llegan `120`
- luego `122`

Interpretacion:

- falta el bloque `121`

### 2. Falta de chunks dentro de un bloque

Ejemplo:

- llega `packet_id=200`, `chunk_count=4`
- solo llegan `chunk 0`, `1` y `3`

Interpretacion:

- bloque corrupto o incompleto

### 3. Discontinuidad en `sample_start_index`

Ejemplo:

- bloque A termina en muestra global `999`
- bloque B empieza en `1010`

Interpretacion:

- se han perdido muestras o bloques intermedios

## Flags recomendados

### `flags` del bloque logico

Bits sugeridos:

- bit 0: `double_tap_present`
- bit 1: `battery_included`
- bit 2: `status_included`
- bit 3: `step_count_included`

### `status_flags`

Bits sugeridos:

- bit 0: `ble_reconnected_recently`
- bit 1: `ram_buffer_overflow`
- bit 2: `imu_fifo_overflow`
- bit 3: `low_battery`

## Recomendacion de implementacion MVP

Para no complicar demasiado firmware y app al principio:

- enviar siempre `step_count_total`
- enviar siempre `battery_level_pct`
- enviar siempre `status_flags`

Aunque conceptualmente sean opcionales, en esta primera version simplifica el parser.

## Dos alternativas de evolucion

### Opcion A - Mantener `0.5 s`

Ventajas:

- mas robusta
- mas facil de depurar
- menor coste de perdida por bloque

### Opcion B - Subir a `1 s`

Con `104` muestras:

- `104 * 12 = 1248 bytes`
- mas `26 bytes` de header
- total `1274 bytes`

Con chunk payload de `180 bytes`:

- `1274 / 180 = 7.07`
- hacen falta `8 chunks`

Esto sigue siendo viable, pero ya mete mas presion en reconexion y buffers.

## Recomendacion final

Para el primer end-to-end real entre wearable y app:

- `104 Hz`
- `52 muestras por bloque`
- `1 bloque cada 0.5 s`
- `4 chunks BLE por bloque`
- `payload util por chunk = 180 bytes`
- `timeout de reensamblado = 3 s`

Es una configuracion equilibrada para arrancar sin meternos todavia en optimizacion fina.
