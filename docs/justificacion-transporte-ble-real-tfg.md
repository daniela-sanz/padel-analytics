# Justificacion del transporte BLE

## 1. Objetivo

Este apartado justifica las decisiones principales adoptadas en el transporte BLE entre el wearable basado en `Seeed Studio XIAO nRF52840 Sense` y la aplicacion Android. En concreto, se documentan:

- la validacion del enlace BLE real
- la consecucion de una frecuencia efectiva de `104 Hz`
- la eleccion de `52 muestras por bloque`
- el uso de `notify` como mecanismo de envio
- la ausencia de un `ARQ` de aplicacion explicito en esta fase

## 2. Contexto del problema

El wearable debe capturar y enviar una senal IMU continua a la app movil sin perder integridad y sin introducir un coste excesivo en el enlace BLE.

Cada muestra contiene:

- `ax`
- `ay`
- `az`
- `gx`
- `gy`
- `gz`

Al almacenarse cada eje como `int16`, cada muestra ocupa `12 bytes`. Para una frecuencia objetivo de `104 Hz`, el flujo bruto de datos es de aproximadamente `1248 bytes/s`.

El problema de diseno no era si BLE podia transportar ese volumen total, sino como hacerlo de forma suficientemente robusta y eficiente.

## 3. De MTU 23 a una estrategia de optimizacion del enlace

En las primeras pruebas reales, el enlace trabajaba en un caso conservador con:

- `ATT_MTU = 23`

En ese escenario, el payload util por notificacion era pequeno y la fragmentacion resultaba demasiado costosa para sostener `104 Hz`.

Una vez validada la integridad del pipeline, se adopto una estrategia distinta:

- mantener `MTU 23` como base de compatibilidad
- intentar automaticamente una negociacion mas favorable del enlace

En la practica, al conectar se paso a solicitar:

- `MTU 247`
- `Data Length Update`
- `PHY 2M`
- prioridad alta de conexion en Android
- un intervalo de conexion corto

La idea no es que el sistema dependa obligatoriamente de `MTU 247`, sino que:

- siga siendo compatible con `MTU 23`
- pero aproveche un enlace de mayor rendimiento cuando el hardware y el central lo permiten

## 4. Justificacion del envio por bloques

Se decidio no transmitir muestra a muestra, sino agrupar las muestras en bloques.

Esta decision se justifica porque el envio por bloques:

- reduce el numero de notificaciones BLE
- reduce el overhead de cabeceras
- mejora la eficiencia por cantidad de dato util
- permite desacoplar adquisicion y envio
- mantiene una latencia razonable

Tampoco se adopto una acumulacion excesivamente larga antes de transmitir, porque eso habria aumentado la latencia, el impacto de un posible fallo y la necesidad de buffer temporal.

Por tanto, el diseno se oriento a una solucion intermedia:

- bloques suficientemente pequenos para conservar robustez
- pero suficientemente grandes para reducir overhead

## 5. Eleccion final de 52 muestras por bloque

La decision sobre el tamano del bloque se tomo a partir de pruebas reales.

Primero se trabajo con:

- `26 muestras por bloque`

Con esta configuracion se verifico que el sistema podia alcanzar `104 Hz` efectivos sin huecos en `packet_id` ni en `sample_start_index`.

Una vez validado esto, se probo una configuracion mas eficiente:

- `52 muestras por bloque`

Con `MTU 247`, esta opcion permite transmitir mas dato util por activacion BLE sin perder robustez. En las pruebas realizadas se mantuvieron:

- `104.02 Hz` efectivos
- `0` huecos en `packet_id`
- `0` huecos en `sample_start_index`
- timestamps monotonicamente crecientes

Por ello, `52 muestras` se adopto como decision vigente, al ofrecer un mejor equilibrio entre:

- robustez
- frecuencia efectiva
- numero de notificaciones
- eficiencia de transporte

## 6. Justificacion del uso de notify

El transporte periferico -> central se implemento mediante:

- `BLE notify`

Se eligio esta opcion porque resulta adecuada para un flujo continuo de datos:

- reduce overhead
- permite mayor throughput practico
- simplifica el emisor en firmware

En este contexto, `notify` es mas apropiado que un intercambio estrictamente sincronizado mensaje a mensaje a nivel de aplicacion.

## 7. Ausencia de ARQ de aplicacion explicito

En esta fase no se ha implementado un protocolo de `ARQ` de aplicacion explicito. Es decir, la app no responde con un ACK del tipo:

- "he recibido el bloque N, ya puedes mandar el siguiente"

Por tanto:

- no hay ACK de aplicacion bloque a bloque
- no hay retransmision dirigida por la app
- no hay politica `stop-and-wait`

Esta decision es deliberada. Introducir una capa extra de confirmacion habria aumentado:

- el numero de mensajes
- la latencia
- la complejidad del sistema

Dado que las pruebas reales ya muestran:

- integridad de `packet_id`
- integridad de `sample_start_index`
- frecuencia efectiva correcta
- bloques completos reensamblados de forma consistente

no se considero necesario anadir esa complejidad en esta fase del MVP.

## 8. Validacion alcanzada

Las pruebas realizadas permiten afirmar que el transporte BLE ha quedado validado, en el alcance actual del proyecto, con:

- `MTU 247`
- `104 Hz` efectivos
- `52 muestras por bloque`
- integridad completa de la secuencia recibida

Esto significa que el transporte ya no debe considerarse solo una prueba de concepto, sino una solucion tecnica suficientemente validada para el MVP.

## 9. Conclusion

Las decisiones adoptadas pueden resumirse asi:

1. el sistema debe seguir siendo compatible con `MTU 23`, pero puede optimizarse cuando el enlace negocia mejor
2. el envio por bloques es preferible al envio muestra a muestra
3. `52 muestras por bloque` ofrece un mejor equilibrio que `26` entre robustez y eficiencia
4. `notify` es adecuado para esta fase por su buen compromiso entre simplicidad y throughput
5. no se implementa `ARQ` de aplicacion explicito porque no ha sido necesario para alcanzar robustez suficiente

En consecuencia, la configuracion elegida se considera tecnicamente justificada para el estado actual del TFG.

## 10. Mermaid opcional

```mermaid
flowchart LR
    A[IMU] --> B[Buffer temporal]
    B --> C[Formacion de bloque]
    C --> D[Chunking BLE]
    D --> E[Notify]
    E --> F[App Android]
    F --> G[Reensamblado y persistencia]
```
