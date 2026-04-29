# MTU en BLE - Notas practicas

## Que es el MTU

`MTU` significa `Maximum Transmission Unit`.

En BLE, el MTU define el tamano maximo de una PDU ATT que pueden intercambiar central y periferico.

## Lo importante para nosotros

En Android, el dato que nos interesa no es un MTU teorico, sino:

- el `MTU negociado real` entre movil y wearable

Ese valor se usa para saber cuanto puede caber en una operacion ATT.

## De donde sacar esa informacion en Android

En Android la secuencia tipica es:

1. conectar con `BluetoothGatt`
2. llamar a `requestMtu(valorDeseado)`
3. esperar el callback `onMtuChanged(gatt, mtu, status)`

Ese callback te da el `mtu` real aceptado para esa conexion.

## Donde esta la verdad

La referencia practica para la app es:

- el parametro `mtu` recibido en `onMtuChanged(...)`

No debes asumir que el movil aceptara exactamente lo pedido ni que el periferico soportara cualquier valor.

## Regla practica para notificaciones

Si vas a enviar datos en una notificacion o indicacion ATT, el payload util suele ser:

- `ATT_MTU - 3 bytes`

Esos 3 bytes corresponden a la cabecera ATT de la operacion.

Ejemplo:

- si `mtu = 185`
- payload ATT util aproximado = `182 bytes`

Por eso en el diseno del MVP propusimos `180 bytes` utiles por chunk.

## Ojo

Ese `ATT_MTU - 3` es una regla practica muy util para estimar el payload de notificaciones, pero sigue habiendo matices de stack, periferico y libreria.

Por eso:

- el tamano de chunk debe ser configurable
- la app debe adaptarse al MTU negociado real

## Recomendacion de implementacion

En la capa BLE Android:

- pedir un MTU alto al conectar
- guardar el valor real de `onMtuChanged(...)`
- calcular `maxChunkPayload = mtu - 3 - chunkHeaderSize`

Con el header de chunk actual de `9 bytes`:

- `maxChunkPayload = mtu - 12`

Ejemplo:

- `mtu = 185`
- `maxChunkPayload = 173 bytes`

Si queremos `180 bytes` utiles de payload y mantenemos un header de chunk de `9 bytes`, necesitariamos:

- `mtu >= 192`

## Conclusiones para el MVP

- el numero `180` es un objetivo de diseno, no una garantia universal
- el chunking debe depender del MTU real negociado
- la fuente real del dato en Android es `onMtuChanged(...)`

## Que mirar tambien en firmware

En el wearable hay que confirmar:

- MTU soportado por la pila BLE
- longitud maxima de notificacion efectiva
- si la libreria o stack impone limites menores que el MTU negociado

## Fuentes oficiales

- Android `BluetoothGatt.requestMtu(...)` y callback `onMtuChanged(...)`:
  https://developer.android.com/reference/android/bluetooth/BluetoothGatt
- Android `BluetoothGattCallback.onCharacteristicChanged(...)`:
  https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback
- Bluetooth Core Specification, `ATT_HANDLE_VALUE_NTF`:
  https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Core-54/out/en/host/attribute-protocol--att-.html

## De donde sale la conclusion de "no cabe en una sola notificacion"

La especificacion Bluetooth define que en una notificacion `ATT_HANDLE_VALUE_NTF` el campo `Attribute Value` puede tener tamano:

- `0 .. (ATT_MTU - 3)` bytes

Eso no impide transmitir sesiones largas.
Lo que impide es meter un payload arbitrariamente grande en una unica notificacion.

Por tanto:

- `1 minuto` de grabacion si se puede transmitir
- pero como una secuencia de muchas notificaciones/chunks
- no como una unica notificacion ATT
