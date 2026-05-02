# Contrato de transporte BLE-IMU

## 1. Objetivo del documento

Este documento fija el contrato tecnico vigente para la adquisicion y envio de datos IMU entre el wearable y la app Android. Su funcion es dejar por escrito las decisiones de diseno activas en cada fase, de forma que el sistema pueda evolucionar sin perder trazabilidad sobre:

- formato de bloques y chunks
- control de sesion
- estrategia de bufferizado
- restricciones del canal BLE
- contadores de integridad

No pretende ser una memoria extensa, sino una referencia viva y actualizable.

## 2. Principios de diseno

Las decisiones de este contrato se apoyan en estos principios:

- el wearable se centra en adquisicion y envio, no en procesado complejo
- la transmision no se hace muestra a muestra, sino por bloques
- la app es responsable de recepcion, persistencia, validacion y procesado posterior
- el sistema debe priorizar robustez e integridad antes que maximizar rendimiento teorico
- las decisiones deben ser compatibles con la restriccion real observada del enlace BLE

## 3. Restriccion base del canal BLE

### 3.1 MTU base de trabajo

El contrato de trabajo actual toma como base:

- `ATT_MTU = 23`

Esta cifra no es arbitraria ni propia de la app, sino el valor clasico por defecto del protocolo BLE cuando no se negocia uno superior. Por tanto:

- el diseno debe funcionar correctamente con `MTU 23`
- cualquier mejora futura por negociacion de MTU mayor se considerara optimizacion, no dependencia

### 3.2 Consecuencia practica

Con `ATT_MTU = 23`, el payload util ATT tipico es de aproximadamente:

- `20 bytes`

Esto obliga a minimizar el overhead por chunk y justifica que el rediseño del transporte no dependa de cabeceras grandes repetidas en todas las notificaciones.

## 4. Estado actual validado

Actualmente ya se ha validado lo siguiente:

- descubrimiento BLE real desde la app
- conexion BLE real con la XIAO
- control de sesion mediante `START_SESSION` y `STOP_SESSION`
- recepcion de chunks BLE
- reensamblado en Android
- persistencia de sesion real
- exportacion y validacion de CSV
- reinicio correcto de `packet_id` y `sample_start_index` al iniciar sesion

Tambien queda constatado que el perfil actual es todavia diagnostico y no representa la arquitectura final de captura continua a `104 Hz`.

## 5. Objetivo funcional de adquisicion

El objetivo de adquisicion que se mantiene como referencia es:

- acelerometro + giroscopio continuos
- `104 Hz`
- muestras crudas en formato compacto

Cada muestra IMU contiene:

- `ax`
- `ay`
- `az`
- `gx`
- `gy`
- `gz`

Formato previsto por muestra:

- `6 x int16 = 12 bytes`

## 6. Estrategia de bloques

### 6.1 Decision vigente

No se trabajara inicialmente con bloques de `2 s` completos, aunque esa idea aparezca en la reflexion teorica del diseno. Se adopta una fase intermedia mas realista:

- bloque de tamano intermedio
- frecuencia de adquisicion objetivo de `104 Hz`
- duracion del bloque preferentemente inferior a `1 s`

Como punto de partida, la referencia actual es:

- `26 muestras por bloque`
- aproximadamente `0.25 s` por bloque

Esta cifra no se considera cerrada de forma rigida. Se adopta como valor inicial razonable, pero puede modificarse si las pruebas reales de:

- latencia
- estabilidad BLE
- overhead de chunking
- uso de RAM
- integridad de sesion

aconsejan ajustar el tamano del bloque.

### 6.2 Justificacion

Tomar un bloque intermedio reduce:

- latencia
- estres de buffer
- coste de depuracion
- impacto de una posible perdida parcial

Y ademas facilita validar integridad de forma incremental antes de escalar a bloques mas grandes.

### 6.3 Regla de ajuste del tamano de bloque

El tamano de bloque se podra revisar entre valores como:

- `26 muestras`
- `52 muestras`
- `104 muestras`

La eleccion final debe guiarse por un equilibrio entre:

- frecuencia efectiva alcanzable
- coste de fragmentacion BLE
- ocupacion de memoria
- robustez ante desconexiones

En consecuencia, `26 muestras` queda fijado como valor inicial recomendado de trabajo, mientras que `52 muestras` pasa a considerarse un escalon posterior de evolucion si la arquitectura real de captura y envio lo soporta con suficiente robustez.

## 7. Estrategia de buffering

La arquitectura objetivo de firmware queda definida asi:

1. `FIFO interna del LSM6DS3TR` como primer nivel de buffering
2. `ring buffer en RAM` como segundo nivel
3. `cola de bloques listos` para transmision BLE

### 7.1 Justificacion

La captura no debe depender directamente del ritmo de envio BLE. Por tanto, se separan dos flujos:

- flujo de adquisicion
- flujo de transmision

La idea clave es:

- la IMU sigue capturando
- las muestras se acumulan
- cuando hay suficientes, se construye un bloque logico
- ese bloque se encola para envio
- el emisor BLE consume la cola cuando el enlace lo permite

## 8. Contrato de sesion

La sesion controlada por la app se mantiene como contrato vigente:

- `START_SESSION`
- `STOP_SESSION`

Comportamiento esperado:

- al conectar no se debe emitir flujo IMU todavia
- `START_SESSION` activa captura y envio
- `STOP_SESSION` detiene el streaming
- `START_SESSION` reinicia contadores de sesion

Contadores que deben reiniciarse al inicio:

- `packet_id`
- `sample_start_index`
- contadores acumulados de sesion que dependan del tramo capturado

## 9. Contrato de bloque logico

### 9.1 Cabecera minima de bloque

El bloque logico principal debe transportar solo la informacion estrictamente necesaria para:

- ordenar bloques
- reconstruir el tiempo aproximado
- detectar discontinuidades
- interpretar cuantas muestras contiene

Por ello, la cabecera minima de bloque queda definida conceptualmente por:

- `packet_id`
- `timestamp_block_start_ms`
- `sample_start_index`
- `sample_count`
- muestras IMU consecutivas

### 9.2 Justificacion

Este conjunto minimo ya permite:

- reconstruccion temporal aproximada
- deteccion de perdidas
- trazabilidad de sesion
- parsing estable del bloque

Se retiran del payload principal los campos que no sean imprescindibles para el reensamblado o la interpretacion inmediata del bloque.

### 9.3 Telemetria secundaria

Los campos siguientes dejan de considerarse parte necesaria del payload principal de cada bloque:

- `battery_level`
- `status_flags`
- `step_count_total`

Su tratamiento acordado es:

- `battery_level`: cada cierto tiempo, aproximadamente cada `1 min`
- `step_count_total`: cada cierto tiempo, aproximadamente cada `1 min`
- `status_flags`: solo cuando se produzcan cambios relevantes

### 9.4 Justificacion de la separacion

La telemetria secundaria puede ser util, pero no debe penalizar todos los chunks del transporte principal. Con `MTU 23`, la prioridad es reservar el mayor numero posible de bytes para:

- muestras IMU
- identificacion del bloque
- continuidad temporal

Por tanto, se distingue entre:

- `payload principal de senal`
- `telemetria de estado`

## 10. Contrato de chunk BLE

### 10.1 Decision de rediseño

Dado que `MTU 23` deja muy poco payload util, el transporte no debe seguir apoyandose en una cabecera relativamente grande repetida en todos los chunks si se quiere aumentar el volumen util por notificacion.

La linea de diseno acordada es:

- minimizar cabecera por chunk
- permitir una cabecera mas rica al inicio del bloque
- usar cabeceras mas cortas en los chunks de continuacion
- expulsar del payload principal todo campo no estrictamente necesario

### 10.2 Criterio de trabajo

El chunking debe diseñarse para:

- funcionar bien con `MTU 23`
- reducir overhead
- seguir permitiendo reensamblado fiable
- mantener deteccion clara de perdidas o incoherencias

### 10.3 Propuesta vigente de `chunk v2` para `MTU 23`

Se adopta como propuesta de trabajo una estructura de dos tipos de chunk:

- `first chunk`
- `continuation chunk`

La logica es sencilla:

- el primer chunk abre el bloque y lleva la metadata necesaria para interpretarlo
- los chunks de continuacion solo necesitan la informacion minima para completar el bloque

#### 10.3.1 Capacidad por notificacion

Con `ATT_MTU = 23`, se toma como capacidad util por notificacion:

- `20 bytes`

#### 10.3.2 First chunk

El primer chunk llevara:

- `chunk_type`: `uint8`
- `packet_id`: `uint16`
- `sample_start_index`: `uint32`
- `sample_count`: `uint8`
- `timestamp_block_start_ms`: `uint32`

Tamano de cabecera del primer chunk:

- `12 bytes`

Payload util restante en el primer chunk:

- `20 - 12 = 8 bytes`

Justificacion:

- el primer chunk fija la identidad y contexto del bloque
- evita repetir esos campos en todas las notificaciones
- permite que la app sepa desde el principio que espera reensamblar

#### 10.3.3 Continuation chunk

Cada chunk de continuacion llevara:

- `chunk_type`: `uint8`
- `packet_id`: `uint16`
- `chunk_seq`: `uint8`

Tamano de cabecera de continuacion:

- `4 bytes`

Payload util restante en cada continuation chunk:

- `20 - 4 = 16 bytes`

Justificacion:

- el `packet_id` liga el fragmento con su bloque
- `chunk_seq` preserva el orden
- se minimiza overhead en la parte repetitiva del transporte

#### 10.3.4 Identificacion de tipo

Se propone:

- `chunk_type = 0x01` para `first chunk`
- `chunk_type = 0x02` para `continuation chunk`
- `chunk_type = 0x03` para `telemetry message`

#### 10.3.5 Reensamblado esperado

La app debe:

1. abrir un bloque al recibir `first chunk`
2. registrar `packet_id`, `sample_start_index`, `sample_count` y `timestamp_block_start_ms`
3. concatenar payloads de `continuation chunk` con el mismo `packet_id`
4. dar por completo el bloque cuando se hayan recibido los bytes necesarios para las `sample_count` muestras esperadas

### 10.4 Justificacion de los tamanos elegidos

La propuesta no busca elegancia teorica, sino eficiencia real bajo `MTU 23`.

El diseño previo, con una cabecera relativamente grande en todos los chunks y campos secundarios repetidos en todos los bloques, desperdicia demasiados bytes por notificacion. En cambio, el esquema `first + continuation`:

- concentra la metadata donde realmente hace falta
- abarata la fragmentacion repetitiva
- simplifica el calculo de payload util
- encaja mejor con bloques medianos
- separa la telemetria secundaria del dato crudo prioritario

### 10.5 Estimacion orientativa para distintos tamanos de bloque

Cada muestra ocupa:

- `12 bytes`

Por tanto:

- `26 muestras = 312 bytes`
- `52 muestras = 624 bytes`
- `104 muestras = 1248 bytes`

Si se usa la propuesta `chunk v2`:

- primer chunk aporta `8 bytes` de payload de datos
- cada continuation aporta `16 bytes`

Entonces, de forma orientativa:

- para `26 muestras`, harian falta aproximadamente `1 + 19` chunks
- para `52 muestras`, harian falta aproximadamente `1 + 39` chunks
- para `104 muestras`, harian falta aproximadamente `1 + 78` chunks

Esto deja clara una conclusion importante:

- con `MTU 23`, incluso optimizando el chunk, bloques demasiado grandes siguen teniendo mucho coste de fragmentacion

### 10.6 Decision practica derivada

Mientras el sistema base siga trabajando con `MTU 23`, la estrategia mas viable y eficiente es:

- no crecer demasiado en tamano de bloque de golpe
- desacoplar primero bien captura y envio
- validar bloques medianos
- solo despues decidir si conviene mantener `52 muestras`, bajar a `26` o subir mas

## 10.7 Recomendacion vigente

La recomendacion de trabajo en este momento es:

1. rediseñar el transporte con `chunk v2`
2. mantener `104 Hz` como objetivo de captura
3. empezar con un bloque medio configurable
4. usar `26 muestras` como primer valor de prueba
5. subir a `52` solo si la fragmentacion, latencia y estabilidad real lo permiten

Dicho de forma clara:

- `26 muestras` es la hipotesis inicial mas prudente con `MTU 23`
- `52 muestras` queda como objetivo de evolucion, no como punto de partida
- la decision realmente prioritaria es optimizar el chunking y desacoplar captura de envio
- tambien es prioritario expulsar del payload principal todo campo no esencial
- el tamaño exacto del bloque se termina de fijar con pruebas reales, no solo con calculo teorico

## 10.8 Justificacion de no usar envios muy espaciados

No se adopta una estrategia de acumulacion larga, por ejemplo del orden de `1 min` antes de transmitir, por varios motivos:

- incrementa mucho el riesgo de perdida si ocurre una caida antes del vaciado
- obliga a reservar bastante mas memoria para almacenamiento temporal
- introduce una latencia excesiva para validar el estado real de la sesion
- dificulta detectar durante la sesion si hay huecos, problemas de enlace o incoherencias
- concentra demasiado trafico en momentos puntuales en vez de repartirlo de forma controlada

En este proyecto se busca un compromiso entre:

- robustez
- consumo
- latencia
- trazabilidad

Por ello, el contrato favorece bloques cortos o intermedios y un vaciado frecuente, en vez de acumulaciones largas. La idea no es transmitir muestra a muestra, pero tampoco diferir el envio durante intervalos excesivos.

## 11. Contadores de integridad

El contrato preve incorporar contadores explicitos para validar captura y envio.

### 11.1 En firmware

Se consideran prioritarios:

- `samples_captured_total`
- `samples_enqueued_total`
- `samples_sent_total`
- `fifo_overrun_count`
- `ram_overrun_count`
- `blocks_dropped_count`

### 11.1.1 Telemetria periodica de bajo ritmo

Ademas del flujo principal de señal, el firmware podra emitir telemetria secundaria con baja frecuencia o por evento:

- `battery_level`, aproximadamente cada `1 min`
- `step_count_total`, aproximadamente cada `1 min`
- `status_flags`, solo cuando cambien

Esta telemetria no debe entorpecer la captura ni inflar innecesariamente el transporte principal de muestras.

### 11.1.2 Encaje de la telemetria en el transporte BLE

La telemetria secundaria no se inserta dentro de cada bloque IMU ni dentro de cada chunk del flujo principal. En su lugar, se transporta como un tipo de mensaje BLE propio, reutilizando la misma caracteristica de datos pero con un identificador distinto.

Esto implica que el enlace BLE podra transportar tres clases de mensajes:

- `0x01`: `first chunk` de un bloque IMU
- `0x02`: `continuation chunk` de un bloque IMU
- `0x03`: `telemetry message`

La app debe mirar primero el `chunk_type` de cada notificacion y redirigirla al parser correspondiente:

- si es `0x01` o `0x02`, entra en el pipeline IMU
- si es `0x03`, actualiza telemetria de estado

### 11.1.3 Formato propuesto de `telemetry message`

Como propuesta minima de trabajo, el mensaje de telemetria llevara:

- `chunk_type`: `uint8`
- `telemetry_flags`: `uint8`
- `battery_level`: `uint8`
- `step_count_total`: `uint32`
- `status_flags`: `uint8`

Tamano orientativo:

- `1 + 1 + 1 + 4 + 1 = 8 bytes`

Con `ATT_MTU = 23`, este mensaje cabe holgadamente dentro de una sola notificacion.

### 11.1.4 Interpretacion de `telemetry_flags`

El campo `telemetry_flags` permite indicar que informacion del mensaje debe considerarse actualizada. Por ejemplo:

- bit `0`: `battery_level` valido
- bit `1`: `step_count_total` valido
- bit `2`: `status_flags` valido

Esto permite que, si en una fase posterior se desea enviar solo una parte de la telemetria, el formato siga siendo interpretable sin ambiguedad.

En la practica, se preve este uso:

- mensaje periodico: `battery_level + step_count_total`
- mensaje por evento: `status_flags`

### 11.1.5 Justificacion de por que no entorpece

La telemetria secundaria no debe entorpecer el flujo principal por estas razones:

- se envia con muy baja frecuencia
- ocupa pocos bytes
- no forma parte del bloque IMU
- no incrementa el overhead de cada chunk de muestras
- no obliga a repetir bateria, pasos o estado en todos los bloques

En consecuencia, el flujo de datos queda separado asi:

- flujo principal: bloques IMU fragmentados en `first chunk` y `continuation chunk`
- flujo secundario: mensajes de telemetria cortos, esporadicos y desacoplados

La prioridad del sistema debe seguir siendo el envio de muestras IMU. La telemetria se considera accesoria y no debe bloquear ni retrasar la captura continua.

### 11.2 En la app

La app debe validar al menos:

- huecos en `packet_id`
- huecos en `sample_start_index`
- coherencia entre duracion de sesion y numero de muestras reales

## 12. Estado contractual actual

En el momento actual del proyecto, el contrato puede resumirse asi:

- BLE real validado
- sesion controlada por app validada
- `MTU 23` asumido como restriccion base de diseño
- objetivo de migracion a `104 Hz`
- objetivo de migracion inicial a `26 muestras por bloque`
- posible evolucion posterior hacia `52 muestras por bloque`
- objetivo de evolucion hacia `FIFO + ring buffer + cola de bloques`
- rediseño del formato de chunk orientado a cabecera minima
- separacion entre payload principal IMU y telemetria secundaria

## 13. Regla de evolucion

Cualquier cambio futuro en:

- tamano de bloque
- cabecera de chunk
- politica de buffering
- contadores de integridad
- estrategia de timestamps
- politicas de telemetria secundaria

debe actualizar este documento para mantener coherencia entre firmware, app y memoria del TFG.
