package com.tfg.wearableapp.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tfg.wearableapp.data.processing.AccelerationBucketSummary
import com.tfg.wearableapp.data.processing.PostSessionSummary
import com.tfg.wearableapp.feature.profile.PlayerProfileUiState
import com.tfg.wearableapp.feature.session.DashboardMode
import com.tfg.wearableapp.feature.session.SessionDetailUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    paddingValues: PaddingValues,
    dashboardMode: DashboardMode,
    liveSessionDetail: SessionDetailUiState?,
    selectedSessionDetail: SessionDetailUiState?,
    sessionSetup: PlayerProfileUiState,
    onGoToSession: () -> Unit,
) {
    val dashboardDetail = when (dashboardMode) {
        DashboardMode.Live -> liveSessionDetail ?: selectedSessionDetail
        DashboardMode.Selected -> selectedSessionDetail ?: liveSessionDetail
    }
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF090B0F),
            Color(0xFF101419),
            Color(0xFF171D17),
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(paddingValues)
    ) {
        if (dashboardDetail?.processedSummary == null || dashboardDetail.session == null) {
            EmptyDashboardState(
                sessionSetup = sessionSetup,
                onGoToSession = onGoToSession,
            )
        } else {
            DashboardContent(
                detail = dashboardDetail,
                sessionSetup = sessionSetup,
                onGoToSession = onGoToSession,
            )
        }
    }
}

@Composable
private fun EmptyDashboardState(
    sessionSetup: PlayerProfileUiState,
    onGoToSession: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "PadelWear Insights",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Todavia no hay una sesion preparada para pintar el dashboard final. Guarda una sesion y abre su detalle para usarla como fuente del panel.",
            color = Color(0xFF98A2B3),
            style = MaterialTheme.typography.bodyLarge,
        )
        TechPanel(
            title = "Perfil activo",
            accent = Color(0xFFA3E635),
            surface = Color(0xFF171B20),
        ) {
            DashboardLabelRow("Nombre", sessionSetup.athleteName.ifBlank { "-" })
            DashboardLabelRow("Sexo", sessionSetup.sex)
            DashboardLabelRow("Mano dominante", sessionSetup.dominantHand)
            DashboardLabelRow("Nivel", sessionSetup.level)
        }
        Button(onClick = onGoToSession) {
            Text("Ir a sesion")
        }
    }
}

@Composable
private fun DashboardContent(
    detail: SessionDetailUiState,
    sessionSetup: PlayerProfileUiState,
    onGoToSession: () -> Unit,
) {
    val session = detail.session!!
    val summary = detail.processedSummary!!
    val accent = if (detail.isLive) Color(0xFFA3E635) else Color(0xFF4ADE80)
    val secondaryAccent = if (detail.isLive) Color(0xFFD9F99D) else Color(0xFF86EFAC)
    val surface = Color(0xFF171B20)
    val surfaceMuted = Color(0xFF1F252D)
    val statusLabel = if (detail.isLive) "LIVE TRACKING" else "FINAL REPORT"
    val statusCopy = if (detail.isLive) {
        "Lectura ligera durante la sesion para validar ritmo, carga e intensidad sobre el flujo IMU."
    } else {
        "Vista post-sesion con un tono mas analitico, reconstruida desde los datos persistidos y procesados."
    }
    val accelerationProfile = summary.accelerationBuckets.toAccelerationProfile()
    val directionChangesPerWindow = summary.accelerationBuckets.toDirectionChangeBars()
    val intensityZones = summary.accelerationBuckets.toIntensityZoneSummary()
    val intensityTimeline = summary.accelerationBuckets.toIntensityTimeline()
    val rallyTimeline = summary.accelerationBuckets.toRallyTimeline()
    val playerLoadTimeline = summary.accelerationBuckets.toPlayerLoadTimeline()
    val strokeAnalysis = simulatedStrokeAnalysis(summary.candidateImpactCount)
    val powerTimeline = summary.accelerationBuckets.toPowerTimeline()
    val consistencyTimeline = summary.accelerationBuckets.toConsistencyTimeline()
    val radarProfile = buildPerformanceRadar(summary, strokeAnalysis)
    val intensityHeatmap = summary.accelerationBuckets.toIntensityHeatmap()
    val eventTimeline = summary.accelerationBuckets.toEventTimeline()
    val primaryMetrics = listOf(
        PrimaryMetric(
            title = "TOTAL GOLPES",
            value = strokeAnalysis.totalStrokes.toString(),
            unit = null,
            supporting = "Impactos detectados por IMU",
        ),
        PrimaryMetric(
            title = "MAX ACCEL",
            value = formatGForce(summary.peakAccelMagnitudeRaw),
            unit = "G",
            supporting = "Pico en remate",
        ),
        PrimaryMetric(
            title = "CARGA TRIMP",
            value = summary.playerLoadScore.toString(),
            unit = "uds",
            supporting = classifyLoad(summary.playerLoadScore),
        ),
        PrimaryMetric(
            title = "DISTANCIA",
            value = summary.estimatedDistanceMeters?.let { formatDistanceKm(it) } ?: "-",
            unit = if (summary.estimatedDistanceMeters != null) "km" else null,
            supporting = if (summary.estimatedDistanceMeters != null) "Movilidad en pista" else "Pendiente de pasos validos",
        ),
        PrimaryMetric(
            title = "VEL. PROM. GOLPE",
            value = (summary.swingSpeedProxyP95Raw ?: 42).toString(),
            unit = "km/h",
            supporting = "Placeholder hasta clasificacion real",
        ),
        PrimaryMetric(
            title = "DIST. EXPLOSIVA",
            value = summary.explosiveDistanceMeters?.toString() ?: "-",
            unit = if (summary.explosiveDistanceMeters != null) "m" else null,
            supporting = "Umbral >= 1.8 G",
        ),
    )

    val dashboardMetrics = listOf(
        DashboardMetric("Player Load", summary.playerLoadScore.toString(), "carga total", "LOAD", 0.72f),
        DashboardMetric("Carga/min", summary.playerLoadPerMinute.toString(), "ritmo de trabajo", "PACE", 0.58f),
        DashboardMetric("Distancia", summary.estimatedDistanceMeters?.let { "${it} m" } ?: "-", "movilidad", "MOVE", 0.61f),
        DashboardMetric("Impactos/min", formatOneDecimal(summary.impactsPerMinute), "presion de golpeo", "HIT", 0.66f),
        DashboardMetric("Eventos accel", summary.accelerationEventCount.toString(), "explosividad", "BURST", 0.55f),
        DashboardMetric("Exposicion", "${summary.explosiveExposurePercent}%", "alta intensidad", "ZONE", summary.explosiveExposurePercent / 100f),
        DashboardMetric("RII proxy", summary.rallyIntensityIndexProxy.toString(), "intensidad rally", "RALLY", 0.63f),
        DashboardMetric("Swing p95", summary.swingSpeedProxyP95Raw?.toString() ?: "-", "tecnica", "SWING", 0.52f),
        DashboardMetric("Potencia p95", summary.powerIndexProxyP95?.toString() ?: "-", "golpe", "POWER", 0.69f),
        DashboardMetric("Consistencia", summary.consistencyScorePercent?.let { "$it%" } ?: "-", "estabilidad", "FORM", (summary.consistencyScorePercent ?: 45) / 100f),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            DashboardHeader(
                statusLabel = statusLabel,
                statusCopy = statusCopy,
                duration = formatDuration(summary.durationMs),
                accent = accent,
            )
        }

        item {
            HeroDashboardCard(
                sessionName = session.sessionName,
                sessionId = session.id.take(8),
                sessionMode = session.mode,
                startedAt = formatTimestamp(session.startedAtEpochMs),
                duration = formatDuration(summary.durationMs),
                batteryBand = "${summary.batteryAtStartPercent ?: "-"}% -> ${summary.batteryAtEndPercent ?: "-"}%",
                isLive = detail.isLive,
                accent = accent,
                secondaryAccent = secondaryAccent,
            )
        }

        item {
            PrimaryMetricsRow(
                metrics = primaryMetrics,
                accent = accent,
                surface = surface,
            )
        }

        item {
            SectionEyebrow(
                title = "Cuantificacion de la carga de trabajo",
                subtitle = "Carga, desplazamiento, intensidad y evolucion temporal de la sesion.",
                accent = accent,
            )
        }

        item {
            ChartPanel(
                title = "Cambios bruscos de direccion",
                surface = surface,
            ) {
                BarChart(
                    values = directionChangesPerWindow.values,
                    labels = directionChangesPerWindow.labels,
                    accent = accent,
                )
            }
        }

        item {
            ChartPanel(
                title = "Perfil de aceleracion (G)",
                surface = surface,
            ) {
                AccelerationProfileChart(
                    profile = accelerationProfile,
                    accent = accent,
                    secondaryAccent = secondaryAccent,
                )
            }
        }

        item {
            IntensityZoneSummaryCard(
                summary = intensityZones,
                surface = surface,
            )
        }

        item {
            ChartPanel(
                title = "Zonas de intensidad vs tiempo",
                surface = surface,
            ) {
                IntensityTimelineChart(
                    timeline = intensityTimeline,
                )
            }
        }

        item {
            ChartPanel(
                title = "Indice de intensidad del rally vs tiempo",
                surface = surface,
            ) {
                RallyIntensityTimelineChart(
                    timeline = rallyTimeline,
                    accent = accent,
                )
            }
        }

        item {
            ChartPanel(
                title = "Player Load acumulado vs tiempo",
                surface = surface,
            ) {
                PlayerLoadTimelineChart(
                    timeline = playerLoadTimeline,
                    accent = accent,
                )
            }
        }

        item {
            ChartPanel(
                title = "Heatmap temporal de intensidad",
                surface = surface,
            ) {
                IntensityHeatmapChart(
                    heatmap = intensityHeatmap,
                )
            }
        }

        item {
            SectionEyebrow(
                title = "Analisis tecnico del golpeo",
                subtitle = "Lectura tecnica del juego; parte simulada hasta integrar el modelo ML.",
                accent = accent,
            )
        }

        item {
            StrokeDistributionCard(
                data = strokeAnalysis.distribution,
                accent = accent,
                surface = surface,
            )
        }

        item {
            StrokeAnalysisSummaryCard(
                analysis = strokeAnalysis,
                surface = surface,
            )
        }

        item {
            ChartPanel(
                title = "Potencia del golpe vs tiempo",
                surface = surface,
            ) {
                MetricLineTimelineChart(
                    timeline = powerTimeline,
                    accent = Color(0xFFA3E635),
                    legend = "Power index proxy",
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartPanel(
                    title = "Consistencia tecnica vs tiempo",
                    surface = surface,
                ) {
                    MetricLineTimelineChart(
                        timeline = consistencyTimeline,
                        accent = Color(0xFF4ADE80),
                        legend = "Consistency score %",
                    )
                }
            }
        }

        item {
            ChartPanel(
                title = "Radar del perfil deportivo",
                surface = surface,
            ) {
                PerformanceRadarChart(
                    radar = radarProfile,
                    accent = accent,
                )
            }
        }

        item {
            ChartPanel(
                title = "Timeline de eventos relevantes",
                surface = surface,
            ) {
                EventTimelineCard(
                    events = eventTimeline,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionEyebrow(
                    title = "Resumen KPI",
                    subtitle = "Vista compacta para comparar rapidamente los indicadores clave de la sesion.",
                    accent = accent,
                )
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(dashboardMetrics) { metric ->
                        MetricTile(
                            metric = metric,
                            accent = accent,
                            surface = surface,
                            surfaceMuted = surfaceMuted,
                        )
                    }
                }
            }
        }

        item {
            SummaryAccentCard(
                title = if (detail.isLive) "Estado operativo" else "Lectura ejecutiva",
                body = buildInterpretation(summary),
                accent = accent,
                surface = surface,
            )
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TechPanel(
                    title = "Perfil de sesion",
                    accent = accent,
                    surface = surface,
                ) {
                    DashboardLabelRow("Nombre", sessionSetup.athleteName.ifBlank { "-" })
                    DashboardLabelRow("Sexo", sessionSetup.sex)
                    DashboardLabelRow("Mano", sessionSetup.dominantHand)
                    DashboardLabelRow("Nivel", sessionSetup.level)
                }
                TechPanel(
                    title = "Carga y movilidad",
                    accent = accent,
                    surface = surface,
                ) {
                    DashboardLabelRow("Pasos", formatStepBand(summary.stepCountStart, summary.stepCountEnd))
                    DashboardLabelRow("Distancia estimada", summary.estimatedDistanceMeters?.let { "$it m" } ?: "-")
                    DashboardLabelRow("Distancia explosiva", summary.explosiveDistanceMeters?.let { "$it m" } ?: "-")
                    DashboardLabelRow("Player Load", summary.playerLoadScore.toString())
                    DashboardLabelRow("Carga por minuto", summary.playerLoadPerMinute.toString())
                    DashboardLabelRow("Perfil de aceleraciones", summary.accelerationEventCount.toString())
                    DashboardLabelRow("Exposicion explosiva", "${summary.explosiveExposurePercent}%")
                }
                TechPanel(
                    title = "Tecnica y golpeo",
                    accent = accent,
                    surface = surface,
                ) {
                    DashboardLabelRow("Impactos detectados", summary.candidateImpactCount.toString())
                    DashboardLabelRow("Golpes detectados", strokeAnalysis.totalStrokes.toString())
                    DashboardLabelRow("Indice agresividad", formatAggressiveness(strokeAnalysis.aggressivenessIndex))
                    DashboardLabelRow("Estilo de juego", strokeAnalysis.styleLabel)
                    DashboardLabelRow("Impactos/min", formatOneDecimal(summary.impactsPerMinute))
                    DashboardLabelRow("RII proxy", summary.rallyIntensityIndexProxy.toString())
                    DashboardLabelRow("Swing p95", summary.swingSpeedProxyP95Raw?.toString() ?: "-")
                    DashboardLabelRow("Potencia p95", summary.powerIndexProxyP95?.toString() ?: "-")
                    DashboardLabelRow("Consistencia", summary.consistencyScorePercent?.let { "$it%" } ?: "-")
                }
                TechPanel(
                    title = "Lectura tecnica base",
                    accent = accent,
                    surface = surface,
                ) {
                    DashboardLabelRow("Muestras", summary.sampleCount.toString())
                    DashboardLabelRow("Bloques", detail.storedBlockCount.toString())
                    DashboardLabelRow("Packets", summary.packetCount.toString())
                    DashboardLabelRow("Media accel", summary.meanAccelMagnitudeRaw.toString())
                    DashboardLabelRow("Media giro", summary.meanGyroMagnitudeRaw.toString())
                    DashboardLabelRow("Pico accel", summary.peakAccelMagnitudeRaw.toString())
                    DashboardLabelRow("Pico giro", summary.peakGyroMagnitudeRaw.toString())
                    DashboardLabelRow("Power index p95", summary.powerIndexProxyP95?.toString() ?: "-")
                    DashboardLabelRow("Consistency score", summary.consistencyScorePercent?.let { "$it%" } ?: "-")
                    DashboardLabelRow("Archivo", session.rawFilePath?.substringAfterLast('\\')?.substringAfterLast('/') ?: "-")
                }
            }
        }

        item {
            OutlinedButton(onClick = onGoToSession) {
                Text("Volver a sesion")
            }
        }
    }
}

@Composable
private fun PrimaryMetricsRow(
    metrics: List<PrimaryMetric>,
    accent: Color,
    surface: Color,
) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(1),
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(metrics) { metric ->
            PrimaryMetricCard(
                metric = metric,
                accent = accent,
                surface = surface,
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    statusLabel: String,
    statusCopy: String,
    duration: String,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "PadelWear Insights",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = statusCopy,
                color = Color(0xFF98A2B3),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Surface(
            color = Color(0xFF151A14),
            shape = RoundedCornerShape(999.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .background(accent, RoundedCornerShape(999.dp))
                )
                Column {
                    Text(
                        text = statusLabel,
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = duration,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroDashboardCard(
    sessionName: String,
    sessionId: String,
    sessionMode: String,
    startedAt: String,
    duration: String,
    batteryBand: String,
    isLive: Boolean,
    accent: Color,
    secondaryAccent: Color,
) {
    val brush = Brush.linearGradient(
        colors = listOf(accent, secondaryAccent),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171B20)),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(104.dp)
                            .height(6.dp)
                            .background(brush, RoundedCornerShape(50))
                    )
                    Text(
                        text = sessionName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "id $sessionId",
                        color = Color(0xFF8E99A8),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = if (isLive) "LIVE SNAPSHOT" else "POST-SESSION SNAPSHOT",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = if (isLive) {
                    "Panel rapido para seguir la sesion activa y detectar cambios de ritmo con una vista mas deportiva."
                } else {
                    "Panel completo para revisar carga, golpeo y estabilidad tecnica una vez cerrada la sesion."
                },
                color = Color(0xFFB8C0CC),
                style = MaterialTheme.typography.bodyMedium,
            )
            DashboardModeBadge(sessionMode)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardLabelColumn("Modo", sessionMode, Modifier.weight(1f))
                DashboardLabelColumn("Inicio", startedAt, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardLabelColumn("Duracion", duration, Modifier.weight(1f))
                DashboardLabelColumn("Bateria", batteryBand, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SectionEyebrow(
    title: String,
    subtitle: String,
    accent: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(28.dp)
                .background(accent, RoundedCornerShape(999.dp))
        )
        Column {
            Text(
                text = title.uppercase(Locale.getDefault()),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = Color(0xFF98A2B3),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PrimaryMetricCard(
    metric: PrimaryMetric,
    accent: Color,
    surface: Color,
) {
    Card(
        modifier = Modifier
            .width(252.dp)
            .height(146.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1F)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = metric.title,
                color = Color(0xFF8FA2C0),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = metric.value,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                metric.unit?.let {
                    Text(
                        text = it,
                        color = Color(0xFF9AA4B2),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
            }
            Text(
                text = metric.supporting,
                color = accent,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            if (metric.unit == "km/h") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .width(62.dp)
                                .height(4.dp)
                                .background(
                                    color = if (index < 3) accent else surface,
                                    shape = RoundedCornerShape(999.dp),
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StrokeDistributionCard(
    data: List<StrokeSlice>,
    accent: Color,
    surface: Color,
) {
    ChartPanel(
        title = "Distribucion de golpes",
        surface = surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
            ) {
                val total = data.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
                val strokeWidth = size.minDimension * 0.18f
                val diameter = size.minDimension - strokeWidth
                var startAngle = -90f
                data.forEach { slice ->
                    val sweep = (slice.value / total) * 360f
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(
                            (size.width - diameter) / 2f,
                            (size.height - diameter) / 2f,
                        ),
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    )
                    startAngle += sweep
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                data.forEach { slice ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(slice.color, RoundedCornerShape(999.dp))
                        )
                        Text(
                            text = slice.label,
                            color = Color(0xFFD0D5DD),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${slice.value.toInt()}%",
                            color = if (slice.color == accent) accent else Color(0xFF98A2B3),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StrokeAnalysisSummaryCard(
    analysis: StrokeAnalysis,
    surface: Color,
) {
    ChartPanel(
        title = "Analisis tecnico del golpeo",
        surface = surface,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Surface(
                color = Color(0xFF1F252D),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = "Clasificacion simulada hasta integrar el modelo ML",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color(0xFF98A2B3),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompactInsightCard(
                    title = "Indice de agresividad",
                    value = formatAggressiveness(analysis.aggressivenessIndex),
                    supporting = analysis.styleLabel,
                    accent = Color(0xFFA3E635),
                    modifier = Modifier.weight(1f),
                )
                CompactInsightCard(
                    title = "Golpe dominante",
                    value = analysis.dominantStroke.label,
                    supporting = "${analysis.dominantStroke.value.toInt()}% del juego",
                    accent = analysis.dominantStroke.color,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Balance de juego",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                analysis.distribution.forEach { slice ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = slice.label,
                                color = Color(0xFFD0D5DD),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "${slice.value.toInt()}%",
                                color = slice.color,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .background(Color(0xFF1F252D), RoundedCornerShape(999.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(slice.value / 100f)
                                    .height(7.dp)
                                    .background(slice.color, RoundedCornerShape(999.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactInsightCard(
    title: String,
    value: String,
    supporting: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2329)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = Color(0xFF98A2B3),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = value,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = supporting,
                color = accent,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ChartPanel(
    title: String,
    surface: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                content()
            },
        )
    }
}

@Composable
private fun BarChart(
    values: List<Int>,
    labels: List<String>,
    accent: Color,
) {
    val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            values.forEach { value ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((value / maxValue.toFloat() * 150f).dp)
                            .background(accent, RoundedCornerShape(10.dp))
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    color = Color(0xFF98A2B3),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AccelerationProfileChart(
    profile: AccelerationProfile,
    accent: Color,
    secondaryAccent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendPill("Max Accel (G)", accent)
            LegendPill("Media Accel (G)", secondaryAccent)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            val minY = -3f
            val maxY = 4f
            val chartWidth = size.width
            val chartHeight = size.height
            val stepY = chartHeight / 7f
            repeat(7) { index ->
                val y = stepY * index
                drawLine(
                    color = Color(0xFF24303A),
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1f,
                )
            }

            fun pointFor(index: Int, value: Float, count: Int): Offset {
                val x = if (count == 1) chartWidth / 2f else index * (chartWidth / (count - 1))
                val normalized = (value - minY) / (maxY - minY)
                val y = chartHeight - normalized * chartHeight
                return Offset(x, y)
            }

            fun buildPath(values: List<Float>): Path {
                val path = Path()
                values.forEachIndexed { index, value ->
                    val point = pointFor(index, value, values.size)
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                return path
            }

            drawPath(
                path = buildPath(profile.maxValues),
                color = accent,
                style = Stroke(width = 5f, cap = StrokeCap.Round),
            )
            drawPath(
                path = buildPath(profile.meanValues),
                color = secondaryAccent,
                style = Stroke(width = 5f, cap = StrokeCap.Round),
            )
            profile.maxValues.forEachIndexed { index, value ->
                val point = pointFor(index, value, profile.maxValues.size)
                drawCircle(color = Color(0xFF171B20), radius = 8f, center = point)
                drawCircle(color = accent, radius = 5f, center = point)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            profile.labels.forEach { label ->
                Text(
                    text = label,
                    color = Color(0xFF98A2B3),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LegendPill(
    label: String,
    color: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(999.dp))
        )
        Text(
            text = label,
            color = Color(0xFF98A2B3),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun IntensityZoneSummaryCard(
    summary: IntensityZoneSummary,
    surface: Color,
) {
    ChartPanel(
        title = "Zonas de intensidad de aceleracion",
        surface = surface,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            summary.zones.forEach { zone ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(zone.color, RoundedCornerShape(999.dp))
                            )
                            Text(
                                text = zone.label,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = "${zone.percent}%",
                            color = zone.color,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color(0xFF1F252D), RoundedCornerShape(999.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(zone.percent / 100f)
                                .height(8.dp)
                                .background(zone.color, RoundedCornerShape(999.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntensityTimelineChart(
    timeline: IntensityTimeline,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            timeline.legend.forEach { legend ->
                LegendPill(legend.label, legend.color)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            timeline.bars.forEach { bar ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF1F252D), RoundedCornerShape(14.dp)),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        bar.segments.forEach { segment ->
                            if (segment.fraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(segment.fraction)
                                        .background(segment.color)
                                )
                            }
                        }
                    }
                    Text(
                        text = bar.label,
                        color = Color(0xFF98A2B3),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun RallyIntensityTimelineChart(
    timeline: RallyTimeline,
    accent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendPill("RII proxy por tramo", accent)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            val values = timeline.values
            val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1
            val chartWidth = size.width
            val chartHeight = size.height
            repeat(5) { index ->
                val y = index * (chartHeight / 4f)
                drawLine(
                    color = Color(0xFF24303A),
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1f,
                )
            }

            fun pointFor(index: Int, value: Int, count: Int): Offset {
                val x = if (count == 1) chartWidth / 2f else index * (chartWidth / (count - 1))
                val normalized = value / maxValue.toFloat()
                val y = chartHeight - normalized * chartHeight
                return Offset(x, y)
            }

            val fillPath = Path()
            val linePath = Path()
            values.forEachIndexed { index, value ->
                val point = pointFor(index, value, values.size)
                if (index == 0) {
                    linePath.moveTo(point.x, point.y)
                    fillPath.moveTo(point.x, chartHeight)
                    fillPath.lineTo(point.x, point.y)
                } else {
                    linePath.lineTo(point.x, point.y)
                    fillPath.lineTo(point.x, point.y)
                }
            }
            val lastX = if (values.size == 1) chartWidth / 2f else chartWidth
            fillPath.lineTo(lastX, chartHeight)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.04f)),
                ),
            )
            drawPath(
                path = linePath,
                color = accent,
                style = Stroke(width = 5f, cap = StrokeCap.Round),
            )
            values.forEachIndexed { index, value ->
                val point = pointFor(index, value, values.size)
                drawCircle(color = Color(0xFF171B20), radius = 8f, center = point)
                drawCircle(color = accent, radius = 5f, center = point)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            timeline.labels.forEach { label ->
                Text(
                    text = label,
                    color = Color(0xFF98A2B3),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PlayerLoadTimelineChart(
    timeline: PlayerLoadTimeline,
    accent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendPill("Player Load acumulado", accent)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            val values = timeline.cumulativeValues
            val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1
            val chartWidth = size.width
            val chartHeight = size.height
            repeat(5) { index ->
                val y = index * (chartHeight / 4f)
                drawLine(
                    color = Color(0xFF24303A),
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1f,
                )
            }

            fun pointFor(index: Int, value: Int, count: Int): Offset {
                val x = if (count == 1) chartWidth / 2f else index * (chartWidth / (count - 1))
                val normalized = value / maxValue.toFloat()
                val y = chartHeight - normalized * chartHeight
                return Offset(x, y)
            }

            val fillPath = Path()
            val linePath = Path()
            values.forEachIndexed { index, value ->
                val point = pointFor(index, value, values.size)
                if (index == 0) {
                    linePath.moveTo(point.x, point.y)
                    fillPath.moveTo(point.x, chartHeight)
                    fillPath.lineTo(point.x, point.y)
                } else {
                    linePath.lineTo(point.x, point.y)
                    fillPath.lineTo(point.x, point.y)
                }
            }
            val lastX = if (values.size == 1) chartWidth / 2f else chartWidth
            fillPath.lineTo(lastX, chartHeight)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.24f), accent.copy(alpha = 0.03f)),
                ),
            )
            drawPath(
                path = linePath,
                color = accent,
                style = Stroke(width = 5f, cap = StrokeCap.Round),
            )
            values.forEachIndexed { index, value ->
                val point = pointFor(index, value, values.size)
                drawCircle(color = Color(0xFF171B20), radius = 8f, center = point)
                drawCircle(color = accent, radius = 5f, center = point)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            timeline.labels.forEach { label ->
                Text(
                    text = label,
                    color = Color(0xFF98A2B3),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricLineTimelineChart(
    timeline: MetricTimeline,
    accent: Color,
    legend: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendPill(legend, accent)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            val values = timeline.values
            val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1
            val chartWidth = size.width
            val chartHeight = size.height
            repeat(5) { index ->
                val y = index * (chartHeight / 4f)
                drawLine(
                    color = Color(0xFF24303A),
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1f,
                )
            }

            fun pointFor(index: Int, value: Int, count: Int): Offset {
                val x = if (count == 1) chartWidth / 2f else index * (chartWidth / (count - 1))
                val normalized = value / maxValue.toFloat()
                val y = chartHeight - normalized * chartHeight
                return Offset(x, y)
            }

            val fillPath = Path()
            val linePath = Path()
            values.forEachIndexed { index, value ->
                val point = pointFor(index, value, values.size)
                if (index == 0) {
                    linePath.moveTo(point.x, point.y)
                    fillPath.moveTo(point.x, chartHeight)
                    fillPath.lineTo(point.x, point.y)
                } else {
                    linePath.lineTo(point.x, point.y)
                    fillPath.lineTo(point.x, point.y)
                }
            }
            val lastX = if (values.size == 1) chartWidth / 2f else chartWidth
            fillPath.lineTo(lastX, chartHeight)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.03f)),
                ),
            )
            drawPath(
                path = linePath,
                color = accent,
                style = Stroke(width = 5f, cap = StrokeCap.Round),
            )
            values.forEachIndexed { index, value ->
                val point = pointFor(index, value, values.size)
                drawCircle(color = Color(0xFF171B20), radius = 8f, center = point)
                drawCircle(color = accent, radius = 5f, center = point)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            timeline.labels.forEach { label ->
                Text(
                    text = label,
                    color = Color(0xFF98A2B3),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PerformanceRadarChart(
    radar: RadarProfile,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f),
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.36f
            val levels = 4
            repeat(levels) { level ->
                val fraction = (level + 1) / levels.toFloat()
                val levelRadius = radius * fraction
                val polygon = polygonPoints(center, levelRadius, radar.axes.size)
                drawPolygon(
                    points = polygon,
                    color = Color(0xFF24303A),
                    style = Stroke(width = 1.5f),
                )
            }
            polygonPoints(center, radius, radar.axes.size).forEach { point ->
                drawLine(
                    color = Color(0xFF24303A),
                    start = center,
                    end = point,
                    strokeWidth = 1.5f,
                )
            }

            val valuePoints = radar.axes.mapIndexed { index, axis ->
                polarPoint(center, radius * axis.value.coerceIn(0f, 1f), index, radar.axes.size)
            }
            drawPolygon(
                points = valuePoints,
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.08f)),
                    center = center,
                    radius = radius,
                ),
            )
            drawPolygon(
                points = valuePoints,
                color = accent,
                style = Stroke(width = 4f),
            )
            valuePoints.forEach {
                drawCircle(color = Color(0xFF171B20), radius = 7f, center = it)
                drawCircle(color = accent, radius = 4f, center = it)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            radar.axes.forEach { axis ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = axis.label,
                        color = Color(0xFFD0D5DD),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${(axis.value * 100).toInt()}",
                        color = accent,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntensityHeatmapChart(
    heatmap: IntensityHeatmap,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            heatmap.legend.forEach { legend ->
                LegendPill(legend.label, legend.color)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            heatmap.cells.forEach { cell ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(cell.color, RoundedCornerShape(16.dp))
                    )
                    Text(
                        text = cell.label,
                        color = Color(0xFF98A2B3),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventTimelineCard(
    events: List<TimelineEvent>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        events.forEach { event ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(event.color, RoundedCornerShape(999.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(42.dp)
                            .background(Color(0xFF24303A), RoundedCornerShape(999.dp))
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = event.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = event.detail,
                        color = Color(0xFFD0D5DD),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = event.label,
                        color = event.color,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    metric: DashboardMetric,
    accent: Color,
    surface: Color,
    surfaceMuted: Color,
) {
    Card(
        modifier = Modifier
            .width(196.dp)
            .height(146.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = metric.label,
                    color = Color(0xFF9AA4B2),
                    style = MaterialTheme.typography.labelLarge,
                )
                Surface(
                    color = surfaceMuted,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = metric.tag,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = metric.value,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = metric.hint,
                color = Color(0xFFD0D5DD),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(surfaceMuted, RoundedCornerShape(999.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(metric.progress.coerceIn(0.08f, 1f))
                        .height(6.dp)
                        .background(accent, RoundedCornerShape(999.dp))
                )
            }
        }
    }
}

@Composable
private fun SummaryAccentCard(
    title: String,
    body: String,
    accent: Color,
    surface: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(30.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title.uppercase(Locale.getDefault()),
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TechPanel(
    title: String,
    accent: Color,
    surface: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(22.dp)
                            .background(accent, RoundedCornerShape(999.dp))
                    )
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                content()
            },
        )
    }
}

@Composable
private fun DashboardLabelRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = Color(0xFF98A2B3))
        Text(text = value, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DashboardLabelColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = label, color = Color(0xFF98A2B3), style = MaterialTheme.typography.labelMedium)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

private data class DashboardMetric(
    val label: String,
    val value: String,
    val hint: String,
    val tag: String,
    val progress: Float,
)

private data class PrimaryMetric(
    val title: String,
    val value: String,
    val unit: String?,
    val supporting: String,
)

private data class StrokeSlice(
    val label: String,
    val value: Float,
    val color: Color,
)

private data class StrokeAnalysis(
    val totalStrokes: Int,
    val distribution: List<StrokeSlice>,
    val aggressivenessIndex: Float,
    val styleLabel: String,
    val dominantStroke: StrokeSlice,
)

private data class AccelerationProfile(
    val labels: List<String>,
    val maxValues: List<Float>,
    val meanValues: List<Float>,
)

private fun buildInterpretation(summary: PostSessionSummary): String {
    val intensity = when {
        summary.peakAccelMagnitudeRaw >= 1850 -> "alta"
        summary.peakAccelMagnitudeRaw >= 1450 -> "media"
        else -> "baja"
    }
    val distanceText = summary.estimatedDistanceMeters?.let { "$it m" } ?: "-"
    val consistencyText = summary.consistencyScorePercent?.let { "$it%" } ?: "-"

    return "Sesion con intensidad $intensity, Player Load ${summary.playerLoadScore} y " +
        "${formatOneDecimal(summary.impactsPerMinute)} impactos por minuto. La movilidad estimada " +
        "alcanza $distanceText y la consistencia tecnica queda en $consistencyText. " +
        "La lectura mantiene la division entre carga, desplazamiento, tecnica y telemetria base."
}

@Composable
private fun DashboardModeBadge(
    mode: String,
) {
    val normalized = mode.lowercase(Locale.getDefault())
    val label = when {
        normalized.contains("ble-real") -> "FUENTE BLE REAL"
        normalized.contains("simulated") -> "FUENTE SIMULADA"
        else -> mode.uppercase(Locale.getDefault())
    }
    val background = when {
        normalized.contains("ble-real") -> Color(0xFFA3E635).copy(alpha = 0.18f)
        normalized.contains("simulated") -> Color(0xFF4ADE80).copy(alpha = 0.18f)
        else -> Color(0xFFCBD5E1).copy(alpha = 0.18f)
    }
    val content = when {
        normalized.contains("ble-real") -> Color(0xFFA3E635)
        normalized.contains("simulated") -> Color(0xFF86EFAC)
        else -> Color.White
    }

    Surface(
        color = background,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            color = content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatTimestamp(timestampMs: Long): String {
    return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        .format(Date(timestampMs))
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}m ${seconds}s"
}

private fun formatOneDecimal(value: Double): String {
    return String.format(Locale.getDefault(), "%.1f", value)
}

private fun formatGForce(rawPeak: Int): String {
    return String.format(Locale.getDefault(), "%.1f", rawPeak / 1000f)
}

private fun formatDistanceKm(distanceMeters: Int): String {
    val value = distanceMeters / 1000f
    return String.format(Locale.getDefault(), "%.1f", value)
}

private fun formatAggressiveness(value: Float): String {
    return String.format(Locale.getDefault(), "%.2f", value)
}

private fun classifyLoad(playerLoad: Int): String {
    return when {
        playerLoad >= 110 -> "Sesion intensidad alta"
        playerLoad >= 70 -> "Sesion intensidad media"
        else -> "Sesion intensidad baja"
    }
}

private fun placeholderStrokeDistribution(): List<StrokeSlice> {
    return listOf(
        StrokeSlice("Volea", 35f, Color(0xFFA3E635)),
        StrokeSlice("Derecha", 22f, Color(0xFF4ADE80)),
        StrokeSlice("Reves", 18f, Color(0xFF22C55E)),
        StrokeSlice("Bandeja/Vibora", 15f, Color(0xFF16A34A)),
        StrokeSlice("Remate", 10f, Color(0xFF14532D)),
    )
}

private fun simulatedStrokeAnalysis(
    totalStrokes: Int,
): StrokeAnalysis {
    val distribution = listOf(
        StrokeSlice("Volea", 26f, Color(0xFFA3E635)),
        StrokeSlice("Derecha", 21f, Color(0xFF4ADE80)),
        StrokeSlice("Reves", 17f, Color(0xFF22C55E)),
        StrokeSlice("Bandeja", 14f, Color(0xFF16A34A)),
        StrokeSlice("Remate", 9f, Color(0xFF14532D)),
        StrokeSlice("Saque", 13f, Color(0xFF84CC16)),
    )
    val aggressionShare = distribution
        .filter { it.label == "Remate" || it.label == "Volea" }
        .sumOf { it.value.toDouble() }
        .toFloat() / 100f
    val styleLabel = when {
        aggressionShare < 0.3f -> "Estilo defensivo"
        aggressionShare < 0.5f -> "Estilo equilibrado"
        else -> "Estilo agresivo"
    }
    val dominantStroke = distribution.maxByOrNull { it.value } ?: distribution.first()

    return StrokeAnalysis(
        totalStrokes = totalStrokes,
        distribution = distribution,
        aggressivenessIndex = aggressionShare,
        styleLabel = styleLabel,
        dominantStroke = dominantStroke,
    )
}

private data class DirectionChangeBars(
    val labels: List<String>,
    val values: List<Int>,
)

private data class IntensityZoneDisplay(
    val label: String,
    val percent: Int,
    val color: Color,
)

private data class IntensityZoneSummary(
    val zones: List<IntensityZoneDisplay>,
)

private data class IntensityTimelineLegend(
    val label: String,
    val color: Color,
)

private data class IntensityTimelineSegment(
    val fraction: Float,
    val color: Color,
)

private data class IntensityTimelineBar(
    val label: String,
    val segments: List<IntensityTimelineSegment>,
)

private data class IntensityTimeline(
    val legend: List<IntensityTimelineLegend>,
    val bars: List<IntensityTimelineBar>,
)

private data class RallyTimeline(
    val labels: List<String>,
    val values: List<Int>,
)

private data class PlayerLoadTimeline(
    val labels: List<String>,
    val cumulativeValues: List<Int>,
)

private data class MetricTimeline(
    val labels: List<String>,
    val values: List<Int>,
)

private data class RadarAxis(
    val label: String,
    val value: Float,
)

private data class RadarProfile(
    val axes: List<RadarAxis>,
)

private data class HeatmapLegend(
    val label: String,
    val color: Color,
)

private data class HeatmapCell(
    val label: String,
    val color: Color,
)

private data class IntensityHeatmap(
    val legend: List<HeatmapLegend>,
    val cells: List<HeatmapCell>,
)

private data class TimelineEvent(
    val label: String,
    val title: String,
    val detail: String,
    val color: Color,
)

private fun List<AccelerationBucketSummary>.toAccelerationProfile(): AccelerationProfile {
    if (isEmpty()) {
        return AccelerationProfile(
            labels = listOf("0-10s"),
            maxValues = listOf(0f),
            meanValues = listOf(0f),
        )
    }

    return AccelerationProfile(
        labels = map { bucket -> bucket.toShortLabel() },
        maxValues = map { bucket -> bucket.maxAccelMagnitudeRaw / 1000f },
        meanValues = map { bucket -> bucket.meanAccelMagnitudeRaw / 1000f },
    )
}

private fun List<AccelerationBucketSummary>.toDirectionChangeBars(): DirectionChangeBars {
    if (isEmpty()) {
        return DirectionChangeBars(
            labels = listOf("0-10s"),
            values = listOf(0),
        )
    }

    return DirectionChangeBars(
        labels = map { bucket -> bucket.toShortLabel() },
        values = map { it.directionChangeCount },
    )
}

private fun List<AccelerationBucketSummary>.toIntensityZoneSummary(): IntensityZoneSummary {
    val totalSamples = sumOf { it.sampleCount }.coerceAtLeast(1)
    val low = sumOf { it.lowIntensitySampleCount }
    val medium = sumOf { it.mediumIntensitySampleCount }
    val high = sumOf { it.highIntensitySampleCount }
    val explosive = sumOf { it.explosiveIntensitySampleCount }

    return IntensityZoneSummary(
        zones = listOf(
            IntensityZoneDisplay("Baja", (low * 100f / totalSamples).toInt(), Color(0xFF1F7A3A)),
            IntensityZoneDisplay("Media", (medium * 100f / totalSamples).toInt(), Color(0xFF4ADE80)),
            IntensityZoneDisplay("Alta", (high * 100f / totalSamples).toInt(), Color(0xFFA3E635)),
            IntensityZoneDisplay("Explosiva", (explosive * 100f / totalSamples).toInt(), Color(0xFFD9F99D)),
        ),
    )
}

private fun List<AccelerationBucketSummary>.toIntensityTimeline(): IntensityTimeline {
    val legend = listOf(
        IntensityTimelineLegend("Baja", Color(0xFF1F7A3A)),
        IntensityTimelineLegend("Media", Color(0xFF4ADE80)),
        IntensityTimelineLegend("Alta", Color(0xFFA3E635)),
        IntensityTimelineLegend("Explosiva", Color(0xFFD9F99D)),
    )

    if (isEmpty()) {
        return IntensityTimeline(
            legend = legend,
            bars = listOf(
                IntensityTimelineBar(
                    label = "0s",
                    segments = legend.mapIndexed { index, item ->
                        IntensityTimelineSegment(
                            fraction = if (index == 0) 1f else 0f,
                            color = item.color,
                        )
                    },
                )
            ),
        )
    }

    return IntensityTimeline(
        legend = legend,
        bars = map { bucket ->
            val total = bucket.sampleCount.coerceAtLeast(1)
            IntensityTimelineBar(
                label = bucket.toShortLabel(),
                segments = listOf(
                    IntensityTimelineSegment(bucket.lowIntensitySampleCount / total.toFloat(), legend[0].color),
                    IntensityTimelineSegment(bucket.mediumIntensitySampleCount / total.toFloat(), legend[1].color),
                    IntensityTimelineSegment(bucket.highIntensitySampleCount / total.toFloat(), legend[2].color),
                    IntensityTimelineSegment(bucket.explosiveIntensitySampleCount / total.toFloat(), legend[3].color),
                ),
            )
        },
    )
}

private fun List<AccelerationBucketSummary>.toRallyTimeline(): RallyTimeline {
    if (isEmpty()) {
        return RallyTimeline(
            labels = listOf("0s"),
            values = listOf(0),
        )
    }
    return RallyTimeline(
        labels = map { it.toShortLabel() },
        values = map { it.rallyIntensityIndexProxy },
    )
}

private fun List<AccelerationBucketSummary>.toPlayerLoadTimeline(): PlayerLoadTimeline {
    if (isEmpty()) {
        return PlayerLoadTimeline(
            labels = listOf("0s"),
            cumulativeValues = listOf(0),
        )
    }

    var accumulated = 0
    return PlayerLoadTimeline(
        labels = map { it.toShortLabel() },
        cumulativeValues = map {
            accumulated += it.playerLoadScore
            accumulated
        },
    )
}

private fun List<AccelerationBucketSummary>.toPowerTimeline(): MetricTimeline {
    if (isEmpty()) {
        return MetricTimeline(
            labels = listOf("0s"),
            values = listOf(0),
        )
    }
    return MetricTimeline(
        labels = map { it.toShortLabel() },
        values = map { it.powerIndexProxy ?: 0 },
    )
}

private fun List<AccelerationBucketSummary>.toConsistencyTimeline(): MetricTimeline {
    if (isEmpty()) {
        return MetricTimeline(
            labels = listOf("0s"),
            values = listOf(0),
        )
    }
    return MetricTimeline(
        labels = map { it.toShortLabel() },
        values = map { it.consistencyScorePercent ?: 0 },
    )
}

private fun List<AccelerationBucketSummary>.toIntensityHeatmap(): IntensityHeatmap {
    val legend = listOf(
        HeatmapLegend("Baja", Color(0xFF1F7A3A)),
        HeatmapLegend("Media", Color(0xFF4ADE80)),
        HeatmapLegend("Alta", Color(0xFFA3E635)),
        HeatmapLegend("Pico", Color(0xFFD9F99D)),
    )

    if (isEmpty()) {
        return IntensityHeatmap(
            legend = legend,
            cells = listOf(HeatmapCell("0s", legend[0].color)),
        )
    }

    return IntensityHeatmap(
        legend = legend,
        cells = map { bucket ->
            val intensityColor = when {
                bucket.explosiveIntensitySampleCount > 0 -> legend[3].color
                bucket.highIntensitySampleCount > bucket.mediumIntensitySampleCount -> legend[2].color
                bucket.mediumIntensitySampleCount > bucket.lowIntensitySampleCount -> legend[1].color
                else -> legend[0].color
            }
            HeatmapCell(
                label = bucket.toShortLabel(),
                color = intensityColor,
            )
        },
    )
}

private fun List<AccelerationBucketSummary>.toEventTimeline(): List<TimelineEvent> {
    if (isEmpty()) {
        return listOf(
            TimelineEvent(
                label = "0s",
                title = "Sin eventos relevantes",
                detail = "Aun no hay suficientes bloques para destacar momentos clave.",
                color = Color(0xFF4ADE80),
            )
        )
    }

    return sortedByDescending { bucketScore(it) }
        .take(4)
        .sortedBy { it.startSecond }
        .map { bucket ->
            val hasPowerPeak = (bucket.powerIndexProxy ?: 0) >= 8000
            val hasExplosiveWindow = bucket.explosiveIntensitySampleCount > 0
            val hasRallySpike = bucket.rallyIntensityIndexProxy >= 40
            when {
                hasPowerPeak -> TimelineEvent(
                    label = bucket.toShortLabel(),
                    title = "Golpe potente destacado",
                    detail = "Power index proxy ${bucket.powerIndexProxy ?: 0} y pico de accel ${bucket.maxAccelMagnitudeRaw}.",
                    color = Color(0xFFA3E635),
                )
                hasExplosiveWindow -> TimelineEvent(
                    label = bucket.toShortLabel(),
                    title = "Ventana explosiva",
                    detail = "Alta exigencia con ${bucket.explosiveDistanceMeters} m explosivos estimados.",
                    color = Color(0xFFD9F99D),
                )
                hasRallySpike -> TimelineEvent(
                    label = bucket.toShortLabel(),
                    title = "Rally exigente",
                    detail = "RII proxy ${bucket.rallyIntensityIndexProxy} e impactos ${bucket.impactCount}.",
                    color = Color(0xFF4ADE80),
                )
                else -> TimelineEvent(
                    label = bucket.toShortLabel(),
                    title = "Bloque tecnico estable",
                    detail = "Consistencia ${bucket.consistencyScorePercent ?: 0}% y Player Load ${bucket.playerLoadScore}.",
                    color = Color(0xFF22C55E),
                )
            }
        }
}

private fun buildPerformanceRadar(
    summary: PostSessionSummary,
    strokeAnalysis: StrokeAnalysis,
): RadarProfile {
    fun normalized(value: Int, maxReference: Int): Float {
        return (value / maxReference.toFloat()).coerceIn(0f, 1f)
    }

    return RadarProfile(
        axes = listOf(
            RadarAxis("Potencia", normalized(summary.powerIndexProxyP95 ?: 0, 12000)),
            RadarAxis("Movilidad", normalized(summary.estimatedDistanceMeters ?: 0, 5000)),
            RadarAxis("Intensidad", normalized(summary.rallyIntensityIndexProxy, 60)),
            RadarAxis("Tecnica", ((summary.consistencyScorePercent ?: 0) / 100f).coerceIn(0f, 1f)),
            RadarAxis("Agresividad", strokeAnalysis.aggressivenessIndex.coerceIn(0f, 1f)),
        ),
    )
}

private fun polygonPoints(
    center: Offset,
    radius: Float,
    sides: Int,
): List<Offset> {
    return List(sides) { index -> polarPoint(center, radius, index, sides) }
}

private fun polarPoint(
    center: Offset,
    radius: Float,
    index: Int,
    count: Int,
): Offset {
    val angle = ((Math.PI * 2.0) / count * index.toDouble()) - (Math.PI / 2.0)
    return Offset(
        x = center.x + (kotlin.math.cos(angle) * radius).toFloat(),
        y = center.y + (kotlin.math.sin(angle) * radius).toFloat(),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygon(
    points: List<Offset>,
    color: Color? = null,
    brush: Brush? = null,
    style: Stroke? = null,
) {
    if (points.isEmpty()) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    when {
        brush != null -> drawPath(path = path, brush = brush, style = style ?: androidx.compose.ui.graphics.drawscope.Fill)
        color != null -> drawPath(path = path, color = color, style = style ?: androidx.compose.ui.graphics.drawscope.Fill)
    }
}

private fun bucketScore(bucket: AccelerationBucketSummary): Int {
    return (bucket.playerLoadScore * 2) +
        bucket.rallyIntensityIndexProxy +
        (bucket.powerIndexProxy ?: 0) / 200 +
        (bucket.explosiveDistanceMeters / 2)
}

private fun AccelerationBucketSummary.toShortLabel(): String {
    return "${startSecond}s"
}

private fun formatStepBand(
    start: Long?,
    end: Long?,
): String {
    if (start == null || end == null) return "-"
    return "$start -> $end"
}
