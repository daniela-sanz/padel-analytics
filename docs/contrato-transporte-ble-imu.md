# Contrato de transporte BLE-IMU

## 1. Objetivo

Este documento fija el contrato tecnico vigente entre el wearable y la app Android para la adquisicion y el envio de datos IMU. No busca ser una memoria extensa, sino una referencia viva sobre:

- formato de bloques y chunks
- control de sesion
- estrategia de buffering
- restricciones y optimizaciones BLE
- contadores de integridad

## 2. Principios de diseno

- el wearable se centra en adquisicion y envio
- la app se encarga de recepcion, persistencia y procesado posterior
- la transmision no se hace muestra a muestra
- la prioridad es integridad de sesion antes que rendimiento teorico
- el sistema debe funcionar en modo conservador y mejorar cuando el enlace lo permita

## 3. Estrategia BLE

### 3.1 Compatibilidad base

La condicion minima de compatibilidad sigue siendo:

- `ATT_MTU = 23`

Esto implica que el sistema debe seguir funcionando aunque no se negocie nada mejor.

### 3.2 Modo preferente de alto rendimiento

La decision vigente del proyecto pasa a ser:

- mantener compatibilidad con `MTU 23`
- intentar siempre una negociacion BLE mas favorable
- aprovecharla solo si se establece de forma estable

Al conectar, el sistema intentara:

- `MTU 247`
- `Data Length Update`
- `PHY 2M`
- intervalo de conexion corto
- prioridad alta de conexion en Android

La justificacion es:

- con `MTU 23` el sistema sigue siendo compatible
- con `MTU 247` baja radicalmente la fragmentacion
- eso aumenta el payload util por notificacion
- y facilita acercarse al objetivo de `104 Hz`

### 3.3 Payload util de referencia

Casos de trabajo:

- con `ATT_MTU = 23`, payload ATT util aproximado: `20 bytes`
- con `ATT_MTU = 247`, payload ATT util aproximado: `244 bytes`

Este salto es lo bastante grande como para justificar que la negociacion de MTU alto pase a ser una optimizacion prioritaria.

## 4. Estado validado

Actualmente ya se ha validado:

- descubrimiento BLE real
- conexion BLE real con la XIAO
- control `START_SESSION` y `STOP_SESSION`
- recepcion de chunks
- reensamblado en Android
- persistencia de sesion real
- exportacion y revision de CSV
- reinicio correcto de `packet_id` y `sample_start_index`

Tambien se ha identificado que el cuello principal estaba en el throughput del enlace y en la fragmentacion, no en la integridad del pipeline app.

## 5. Objetivo de adquisicion

Se mantiene como objetivo funcional:

- acelerometro + giroscopio continuos
- `104 Hz`
- muestras crudas compactas

Cada muestra IMU contiene:

- `ax`
- `ay`
- `az`
- `gx`
- `gy`
- `gz`

Formato por muestra:

- `6 x int16 = 12 bytes`

## 6. Estrategia de bloques

### 6.1 Tamano de bloque vigente

El tamano de trabajo actual sigue siendo:

- `52 muestras por bloque`

Equivale aproximadamente a:

- `0.5 s` por bloque a `104 Hz`

### 6.2 Justificacion

`52 muestras` se adopta como decision vigente porque:

- mantiene `104 Hz` efectivos en las pruebas reales
- mantiene robustez e integridad de `packet_id` y `sample_global_index`
- reduce el numero de notificaciones frente a `26 muestras`
- sigue teniendo una latencia razonable para el MVP
- con `MTU 247` se resuelve en muy pocos chunks

Con `MTU 247`, `52 muestras` se resuelve aproximadamente en `3 chunks`, lo que da un equilibrio muy bueno entre eficiencia y robustez.

### 6.3 Escalado futuro

Valores de referencia considerados:

- `52 muestras`
- `104 muestras`

`52 muestras` pasa a ser el valor vigente de trabajo. `104 muestras` queda como posible exploracion posterior si se quisiera reducir aun mas el numero de notificaciones por bloque sin perder robustez.

## 7. Estrategia de buffering

La arquitectura objetivo del firmware queda definida asi:

1. `FIFO interna del LSM6DS3TR` como primer nivel de buffering
2. `ring buffer en RAM` como segundo nivel
3. `cola de bloques listos` para transmision BLE

### 7.1 Justificacion

La captura no debe depender del ritmo de envio BLE. Por tanto:

- la IMU sigue produciendo muestras
- las muestras se acumulan
- al llegar a `52`, se forma un bloque
- ese bloque se encola
- el emisor BLE vacia la cola cuando el enlace lo permite

### 7.2 Politica de envio

El emisor BLE debe:

- drenar la cola de forma agresiva mientras `notify(...)` siga siendo aceptado
- frenar el envio justo antes del siguiente instante de muestreo

La idea no es un envio pausado, sino un vaciado intensivo compatible con captura continua.

## 8. Contrato de sesion

El control de sesion se mantiene como:

- `START_SESSION`
- `STOP_SESSION`

Comportamiento esperado:

- al conectar no se transmite flujo IMU todavia
- `START_SESSION` activa captura y envio
- `STOP_SESSION` detiene el streaming
- `START_SESSION` reinicia contadores de sesion

Contadores que deben reiniciarse:

- `packet_id`
- `sample_start_index`
- contadores acumulados de sesion

## 9. Contrato de bloque logico

### 9.1 Cabecera minima

El bloque logico principal debe llevar solo lo necesario para:

- ordenar bloques
- reconstruir el tiempo aproximado
- detectar discontinuidades
- interpretar cuantas muestras contiene

Por ello, la cabecera minima queda definida por:

- `packet_id`
- `timestamp_block_start_ms`
- `sample_start_index`
- `sample_count`
- muestras IMU consecutivas

### 9.2 Campos fuera del payload principal

Los siguientes campos dejan de ser obligatorios dentro de cada bloque IMU:

- `battery_level`
- `status_flags`
- `step_count_total`

Tratamiento acordado:

- `battery_level`: aproximadamente cada `1 min`
- `step_count_total`: aproximadamente cada `1 min`
- `status_flags`: solo cuando cambien

## 10. Contrato de chunk BLE

### 10.1 Tipos de mensaje

Se mantienen tres tipos:

- `0x01`: `first chunk`
- `0x02`: `continuation chunk`
- `0x03`: `telemetry message`

### 10.2 Cabeceras

`first chunk`:

- `chunk_type`: `uint8`
- `packet_id`: `uint16`
- `sample_start_index`: `uint32`
- `sample_count`: `uint8`
- `timestamp_block_start_ms`: `uint32`

Tamano de cabecera:

- `12 bytes`

`continuation chunk`:

- `chunk_type`: `uint8`
- `packet_id`: `uint16`
- `chunk_seq`: `uint8`

Tamano de cabecera:

- `4 bytes`

### 10.3 Payload adaptable al MTU

El `chunk v2` mantiene cabeceras fijas, pero el payload util de muestras depende del MTU real negociado.

Formulas:

- `first chunk payload = att_payload - 12`
- `continuation chunk payload = att_payload - 4`

Casos de referencia:

- con `ATT_MTU 23`: `att_payload = 20`
- con `ATT_MTU 247`: `att_payload = 244`

Por tanto:

- con `MTU 23`, `first chunk` aporta `8 bytes` y `continuation` aporta `16 bytes`
- con `MTU 247`, `first chunk` aporta `232 bytes` y `continuation` aporta `240 bytes`

### 10.4 Reensamblado esperado

La app debe:

1. abrir bloque al recibir `first chunk`
2. registrar `packet_id`, `sample_start_index`, `sample_count` y `timestamp_block_start_ms`
3. concatenar los payloads de `continuation chunk` del mismo `packet_id`
4. cerrar el bloque cuando se alcancen los bytes esperados de las muestras

### 10.5 Impacto practico del MTU

Cada muestra ocupa:

- `12 bytes`

Entonces:

- `52 muestras = 624 bytes`
- `104 muestras = 1248 bytes`

Con `MTU 23`:

- `52 muestras` requieren aproximadamente `1 + 39` chunks
- `104 muestras` requieren aproximadamente `1 + 78` chunks

Con `MTU 247`:

- `52 muestras` requieren aproximadamente `3 chunks`
- `104 muestras` requieren aproximadamente `6 chunks`

Esta diferencia justifica plenamente que el sistema intente un enlace BLE de mayor rendimiento.

## 11. Telemetria secundaria

### 11.1 Canal logico separado

La telemetria secundaria no se inserta dentro de cada bloque IMU. Se envia como mensaje BLE propio reutilizando la misma caracteristica.

### 11.2 Formato propuesto

- `chunk_type`: `uint8`
- `telemetry_flags`: `uint8`
- `battery_level`: `uint8`
- `step_count_total`: `uint32`
- `status_flags`: `uint8`

Tamano:

- `8 bytes`

### 11.3 Interpretacion

`telemetry_flags` indica que campos del mensaje deben considerarse actualizados:

- bit `0`: `battery_level`
- bit `1`: `step_count_total`
- bit `2`: `status_flags`

Uso previsto:

- mensaje periodico: `battery_level + step_count_total`
- mensaje por evento: `status_flags`

### 11.4 Justificacion

Esto evita:

- inflar todos los chunks IMU
- repetir bateria o pasos en cada bloque
- penalizar el flujo principal de muestras

## 12. Contadores de integridad

### 12.1 En firmware

- `samples_captured_total`
- `samples_enqueued_total`
- `samples_sent_total`
- `fifo_overrun_count`
- `ram_overrun_count`
- `blocks_dropped_count`

### 12.2 En la app

- huecos en `packet_id`
- huecos en `sample_start_index`
- frecuencia efectiva
- coherencia entre duracion y muestras reales

## 13. Recomendacion vigente

La estrategia activa de trabajo queda asi:

1. mantener `104 Hz` como objetivo
2. mantener `52 muestras` por bloque
3. mantener `chunk v2`
4. mantener compatibilidad con `MTU 23`
5. intentar siempre `MTU 247 + DLE + PHY 2M + intervalo corto`
6. medir si la frecuencia efectiva se acerca al objetivo
7. solo si sigue lejos, replantear el transporte hacia un modo todavia mas streaming

## 14. Estado contractual actual

En este momento el contrato puede resumirse asi:

- BLE real validado
- sesion controlada por app validada
- integridad de `packet_id` y `sample_start_index` validada
- `MTU 23` asumido como base de compatibilidad
- `MTU 247` adoptado como optimizacion preferente
- objetivo de `104 Hz`
- `52 muestras por bloque` como referencia vigente
- `chunk v2` con payload adaptable al MTU real
- telemetria secundaria separada del payload principal
- arquitectura objetivo: `FIFO + ring buffer + cola de bloques`

## 15. Regla de evolucion

Cualquier cambio futuro en:

- tamano de bloque
- cabeceras de chunk
- politica de timestamps
- estrategia de buffering
- telemetria secundaria
- politica de negociacion BLE

debe reflejarse en este documento para mantener coherencia entre firmware, app y memoria del TFG.
