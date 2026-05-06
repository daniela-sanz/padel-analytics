# Prueba breve de cobertura BLE en pista

## Objetivo

Determinar la potencia minima de transmision BLE que cubre de forma estable:

- toda la pista
- la zona de entrada o salida
- un pequeno margen extra alrededor del caso ideal

Caso ideal asumido:

- movil fijo en una silla en la entrada o salida de la pista
- wearable en la muneca del jugador

## Firmware a usar

Sketch:

- [BLE_chunk_sender_v2_mtu23_imu.ino](/abs/path/c:/Users/sanzt/Desktop/TFG/arduino/BLE_chunk_sender_v2_mtu23_imu/BLE_chunk_sender_v2_mtu23_imu.ino)

Scripts para cambiar rapidamente la potencia:

- [set_tx_power_0dbm.ps1](/abs/path/c:/Users/sanzt/Desktop/TFG/arduino/tools/tx_power_tests/set_tx_power_0dbm.ps1)
- [set_tx_power_4dbm.ps1](/abs/path/c:/Users/sanzt/Desktop/TFG/arduino/tools/tx_power_tests/set_tx_power_4dbm.ps1)
- [set_tx_power_8dbm.ps1](/abs/path/c:/Users/sanzt/Desktop/TFG/arduino/tools/tx_power_tests/set_tx_power_8dbm.ps1)

## Uso rapido

Desde PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File "C:\Users\sanzt\Desktop\TFG\arduino\tools\tx_power_tests\set_tx_power_0dbm.ps1"
```

o

```powershell
powershell -ExecutionPolicy Bypass -File "C:\Users\sanzt\Desktop\TFG\arduino\tools\tx_power_tests\set_tx_power_4dbm.ps1"
```

o

```powershell
powershell -ExecutionPolicy Bypass -File "C:\Users\sanzt\Desktop\TFG\arduino\tools\tx_power_tests\set_tx_power_8dbm.ps1"
```

Despues:

1. subir el sketch a la XIAO
2. conectar la app
3. lanzar una sesion de prueba

## Protocolo sencillo

Para cada potencia (`0`, `4`, `8 dBm`):

1. cargar la potencia con el script correspondiente
2. subir el sketch a la XIAO
3. iniciar una sesion de `10-15 min`
4. dejar el movil fijo en la silla de entrada o salida
5. recorrer:
   - fondo cercano
   - red
   - fondo opuesto
   - esquinas
   - lateral mas alejado del movil
6. salir un poco fuera de la pista al final, para comprobar margen extra
7. detener la sesion y guardar CSV y capturas

## Que observar

En la app:

- `MTU negociado`
- `Frecuencia efectiva`
- `Huecos packet_id`
- `Huecos sample_index`
- si hubo desconexion

En el resultado:

- CSV completo
- frecuencia cercana a `104 Hz`
- ausencia de huecos
- continuidad de la sesion

## Criterio de decision

- si `0 dBm` cubre toda la pista y el pequeno margen extra, elegir `0 dBm`
- si `0 dBm` falla pero `4 dBm` funciona bien, elegir `4 dBm`
- si `4 dBm` todavia falla, usar `8 dBm`

La idea es quedarse con la potencia minima suficiente, no con la maxima por defecto.

## Tabla breve para rellenar

| Potencia | Duracion | MTU | Hz efectivos | Huecos packet_id | Huecos sample_index | Desconexion | Cobertura pista | Cobertura margen extra | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 0 dBm | ~15 s | no anotado | 107.55 Hz\* | 2 | 2 | si, comportamiento inestable | no validada | no validado | descartado |
| 4 dBm | ~2.04 min | no anotado | 103.59 Hz | 7 | 7 | no evidente, pero con discontinuidades | valida al lado de la pista | limitada | aceptable |
| 8 dBm | ~2.36 min | no anotado | 104.01 Hz | 6 | 6 | no evidente en el CSV | valida | valida con margen claro | recomendada |

\* La frecuencia efectiva anomala de `0 dBm` no debe interpretarse como mejor rendimiento, sino como consecuencia de una sesion corta e inestable con discontinuidades.

## CSV analizados

- [0db.csv](/abs/path/c:/Users/sanzt/Desktop/TFG/app/docs/evidencias/pruebas_csv/0db.csv)
- [4bB.csv](/abs/path/c:/Users/sanzt/Desktop/TFG/app/docs/evidencias/pruebas_csv/4bB.csv)
- [8bB.csv](/abs/path/c:/Users/sanzt/Desktop/TFG/app/docs/evidencias/pruebas_csv/8bB.csv)

## Resultados observados en los CSV

### 0 dBm

- `1612` muestras
- `31` bloques de `52` muestras
- duracion aproximada de solo `15 s`
- `2` discontinuidades en `packet_id`
- `2` discontinuidades en `sample_index`
- `1` retroceso temporal en `block_timestamp_ms`

Interpretacion:

- la configuracion queda descartada
- el enlace no resulta suficientemente robusto ni siquiera para el escenario conservador de movil junto a la entrada de la pista

### 4 dBm

- `12688` muestras
- `244` bloques de `52` muestras
- duracion aproximada de `2.04 min`
- frecuencia efectiva aproximada de `103.59 Hz`
- `7` discontinuidades en `packet_id`
- `7` discontinuidades en `sample_index`
- `2` retrocesos temporales

Interpretacion:

- para el caso base junto a la pista parece usable
- el CSV muestra, sin embargo, algunos episodios de discontinuidad o reordenacion
- por tanto, aunque es una opcion razonable, no ofrece el mejor margen

### 8 dBm

- `14716` muestras
- `283` bloques de `52` muestras
- duracion aproximada de `2.36 min`
- frecuencia efectiva aproximada de `104.01 Hz`
- `6` discontinuidades en `packet_id`
- `6` discontinuidades en `sample_index`
- `2` retrocesos temporales

Interpretacion:

- es la configuracion que mejor reproduce el objetivo de `104 Hz`
- el CSV sigue mostrando algunas discontinuidades puntuales, pero globalmente sale mejor que `4 dBm`
- ademas, segun la observacion realizada durante la prueba, fue la opcion con mejor cobertura y mejor margen fuera del caso ideal

## Conclusiones provisionales

Con la evidencia disponible:

- `0 dBm` queda descartado
- `4 dBm` puede servir si el movil permanece al lado de la pista
- `8 dBm` es la opcion mas robusta y la que ofrece un margen de cobertura mas amplio

Dado que la autonomia observada en pruebas largas sigue siendo suficiente para sesiones de mas de hora y media, la decision provisional mas razonable es:

- mantener `+8 dBm` como configuracion por defecto

Esto permite forzar una condicion de contorno mas segura y reducir el riesgo de problemas de enlace en escenarios reales de uso.
