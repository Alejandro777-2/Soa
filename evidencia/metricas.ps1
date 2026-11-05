# Genera evidencia/metricas.txt contando UNICAMENTE lineas de los logs reales de la corrida.
# No modifica ningun log.
$ErrorActionPreference = 'Stop'
$logs = Join-Path $PSScriptRoot '..\logs'
$out  = Join-Path $PSScriptRoot 'metricas.txt'

function Count([string]$file, [string]$needle) {
    $p = Join-Path $logs $file
    if (-not (Test-Path $p)) { return 0 }
    return @(Select-String -Path $p -SimpleMatch -Pattern $needle).Count
}

$aceptados   = Count 'OrderProducer.log' 'publicado (order.created)'
$invalidas   = Count 'OrderProducer.log' 'Pedido invalido rechazado (400)'
$reintentos  = Count 'PaymentWorker.log' 'Reintento #'
$aprobados   = Count 'PaymentWorker.log' 'Cobro EXITOSO'
$emails      = Count 'EmailWorker.log'   'Enviando e-mail de confirmacion'
$sinEmailFail= Count 'EmailWorker.log'   'No se envia e-mail (paymentStatus=FAILED)'

# Pedidos en DLQ: segun el total reportado por DlqInspector sobre payment.dlq.
$dlqLine = Select-String -Path (Join-Path $logs 'DlqInspector.log') -Pattern 'payment\.dlq:\s*(\d+)' | Select-Object -First 1
$dlq = if ($dlqLine) { [int]$dlqLine.Matches[0].Groups[1].Value } else { 0 }

$okTotal = ($aprobados + $dlq) -eq 20
$okEmail = $emails -eq $aprobados

$lines = @(
    "=== Metricas BikeStore Async v1.0 ===",
    "Generado: $(Get-Date -Format o)",
    "Fuente: logs/*.log de esta ejecucion (sin edicion)",
    "",
    "Pedidos aceptados (HTTP 202)..............: $aceptados",
    "Peticiones invalidas rechazadas (HTTP 400): $invalidas",
    "Cobros rechazados con reintento...........: $reintentos",
    "Cobros aprobados..........................: $aprobados",
    "E-mails de confirmacion enviados..........: $emails",
    "Pedidos en la DLQ (payment.dlq)...........: $dlq",
    "Pedidos sin e-mail por FAILED.............: $sinEmailFail",
    "",
    "--- Verificaciones ---",
    "aprobados + DLQ = $($aprobados + $dlq)  (debe ser 20) -> $(if($okTotal){'OK'}else{'FALLA'})",
    "emails = aprobados -> $emails = $aprobados -> $(if($okEmail){'OK'}else{'FALLA'})"
)
if (-not $okTotal) { $lines += "AVISO: aprobados + DLQ != 20. Revisar si quedaron mensajes sin procesar en las colas." }
if (-not $okEmail) { $lines += "AVISO: emails != aprobados. Posible e-mail duplicado o perdido." }

$lines | Set-Content -Path $out -Encoding utf8
Get-Content $out
