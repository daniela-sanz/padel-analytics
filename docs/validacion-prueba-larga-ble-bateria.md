# Validacion breve de prueba larga BLE + bateria

## Objetivo

Documentar de forma breve la prueba larga realizada para validar:

- estabilidad del transporte BLE real
- mantenimiento de `104 Hz`
- robustez del reensamblado y persistencia
- comportamiento inicial del seguimiento de bateria

## Configuracion usada

- transporte BLE real con `MTU 247`
- frecuencia objetivo de adquisicion: `104 Hz`
- `52 muestras por bloque`
- envio por `notify`
- persistencia en `Room + CSV`
- telemetria de bateria real integrada en firmware

Archivo analizado:

- [csv_G_90min.csv](/abs/path/c:/Users/sanzt/Desktop/TFG/app/docs/evidencias/pruebas_csv/csv_G_90min.csv)

## Resultados verificados

- muestras totales: `664872`
- packets totales: `12786`
- `packet_id` de `1` a `12786`, sin huecos
- `sample_global_index` de `0` a `664871`, sin huecos
- `52` muestras por packet en todos los bloques
- timestamps monotonicamente crecientes
- duracion aproximada: `106.55 min`
- frecuencia efectiva sostenida: `104.00 Hz`

## Conclusiones del transporte

La prueba valida que el sistema puede mantener sesiones largas con:

- transporte BLE estable
- reensamblado correcto
- persistencia coherente
- frecuencia efectiva sostenida en torno a `104 Hz`

Por tanto, la configuracion actual con `MTU 247` y `52 muestras por bloque` se considera validada tambien en una sesion larga, no solo en pruebas cortas.

## Resultados de bateria

Valores observados en la prueba:

- bateria inicial: `59%`
- bateria final: `19%`
- minimo observado: `18%`
- maximo observado: `59%`

La bateria ya no se envia como valor fijo, sino como lectura real del wearable a partir de:

- lectura de `PIN_VBAT`
- activacion del divisor de bateria
- conversion a porcentaje mediante curva aproximada de LiPo

## Observacion importante

Aunque la lectura de bateria ya es real, el porcentaje observado todavia presenta oscilaciones de pocos puntos porcentuales entre lecturas consecutivas. Esto sugiere:

- ruido normal en lectura analogica
- sensibilidad de la conversion actual a pequenas variaciones de voltaje

## Decision actual

Se acepta temporalmente esta implementacion porque:

- permite hacer pruebas largas con seguimiento real de bateria
- resulta suficiente para validacion tecnica interna

## Pendiente para iteracion posterior

Queda pendiente estabilizar visualmente la bateria antes de considerarla cerrada para producto final, por ejemplo mediante:

- suavizado de lecturas
- histeresis de porcentaje
- actualizacion menos sensible a fluctuaciones pequenas

Esta mejora se pospone deliberadamente para una siguiente iteracion.
