# Alcance del MVP

## Lo que si entra

- app Android nativa
- conexion BLE con un wearable
- inicio y fin de sesion desde app
- soporte de boton fisico como respaldo
- recepcion de datos IMU por bloques
- almacenamiento local por sesiones
- deteccion de perdida de paquetes
- vista minima durante grabacion
- dashboard post-sesion
- exportacion de datos
- bateria del wearable
- perfil basico de usuario

## Lo que no entra por ahora

- iOS
- clasificacion ML en tiempo real
- reenvio fiable de bloques perdidos
- nube
- cuentas multiusuario con login
- sincronizacion remota
- memoria externa en el wearable
- dashboard en tiempo real complejo

## Vista minima durante sesion

- estado de conexion
- estado de grabacion
- bateria
- contador de paquetes recibidos
- tasa aproximada de recepcion
- ultimo `packet_id` recibido

## Dashboard post-sesion

KPIs iniciales recomendados:

- duracion de sesion
- numero de muestras
- bloques recibidos
- bloques perdidos estimados
- pasos
- picos de aceleracion
- magnitud de giro
- carga de movimiento
- energia vibratoria o RMS

## Exportacion

Formatos recomendados:

- `CSV` para muestras crudas
- `JSON` para resumen y metadatos

## Criterios de exito del MVP

- la app puede registrar una sesion completa sin cuelgues
- la perdida de paquetes es detectable
- las sesiones quedan guardadas y son consultables
- los datos se pueden exportar
- el dashboard post-sesion refleja datos coherentes
