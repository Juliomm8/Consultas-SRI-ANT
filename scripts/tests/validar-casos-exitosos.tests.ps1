#requires -Version 7.2
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$scriptPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\validar-casos-exitosos.ps1'))
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -gt 0) {
    throw 'El script de diagnóstico tiene errores sintácticos.'
}

$functionNames = @(
    'New-EncodedQuery',
    'New-AntRequestParameters',
    'Get-OptionalAntRequestParameters',
    'Get-SafeTaxpayerTypeValue',
    'Get-TaxpayerTypeInspection',
    'Show-AuthorizedTaxpayerType'
)
foreach ($functionName in $functionNames) {
    $functionAst = $ast.Find({
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $functionName
    }, $true)
    if ($null -eq $functionAst) {
        throw "Falta la función requerida: $functionName"
    }
    . ([scriptblock]::Create($functionAst.Extent.Text))
}

# Synthetic values exercise request construction only; no HTTP function is loaded or called.
$authorized = Get-OptionalAntRequestParameters -Cedula '1234567890' -Plate 'TEST&123' -Authorized $true
if ($null -eq $authorized -or -not $authorized.Contains('ps_placa')) {
    throw 'La autorización S debe incluir ps_placa.'
}
if ($authorized['ps_placa'] -cne 'TEST&123') {
    throw 'La autorización S no conservó la placa en memoria.'
}

$encodedQuery = New-EncodedQuery -Parameters $authorized
if ($encodedQuery -cnotmatch '(^|&)ps_placa=TEST%26123($|&)') {
    throw 'El valor de ps_placa no quedó codificado en la construcción de la consulta.'
}

$unauthorized = Get-OptionalAntRequestParameters -Cedula '1234567890' -Plate '' -Authorized $false
if ($null -ne $unauthorized) {
    throw 'La autorización N debe omitir la solicitud opcional con placa.'
}

$baseline = New-AntRequestParameters -Cedula '1234567890' -Plate '' -IncludePlate
if (-not $baseline.Contains('ps_placa') -or $baseline['ps_placa'] -cne '') {
    throw 'La solicitud inicial ANT debe conservar ps_placa vacío.'
}

$taxpayerFixture = '{"numeroRuc":"SYNTHETIC_ID","razonSocial":"SYNTHETIC_PRIVATE_NAME","tipoContribuyente":"TYPE_ALPHA"}'
$inspection = Get-TaxpayerTypeInspection -Body $taxpayerFixture -StatusCode 200 -ContentType 'application/json' -Truncated $false
if (-not $inspection.Available -or $inspection.SafeValue -cne 'TYPE_ALPHA') {
    throw 'La inspección debe conservar únicamente el tipo categórico seguro de una respuesta JSON exitosa.'
}
if (($inspection.PSObject.Properties.Name -join ',') -cne 'Available,SafeValue') {
    throw 'La inspección no debe retornar otros campos de la respuesta.'
}

foreach ($unsafeValue in @(
    'SYNTHETIC PERSON NAME',
    '1234567890123',
    'SYNTHETIC@EXAMPLE.TEST',
    'HTTPS://EXAMPLE.TEST',
    "TYPE_ALPHA`nSYNTHETIC",
    ('A' * 33),
    'lowercase'
)) {
    if ($null -ne (Get-SafeTaxpayerTypeValue -Value $unsafeValue)) {
        throw 'Un valor que no tiene formato categórico seguro fue aceptado.'
    }
}

$notAuthorizedOutput = @(& { Show-AuthorizedTaxpayerType -Value 'TYPE_ALPHA' -Authorized $false } 6>&1)
if ($notAuthorizedOutput.Count -ne 0) {
    throw 'Sin autorización explícita no se debe mostrar el valor categórico.'
}
$authorizedOutput = @(& { Show-AuthorizedTaxpayerType -Value 'TYPE_ALPHA' -Authorized $true } 6>&1)
$displayText = ($authorizedOutput | ForEach-Object { [string]$_.MessageData }) -join ''
if ($displayText -cne 'tipoContribuyente (valor categórico): TYPE_ALPHA') {
    throw 'Con autorización se debe mostrar únicamente el valor categórico seguro.'
}
$authorizedOutput = $null
$notAuthorizedOutput = $null
$displayText = $null
$inspection = $null
$taxpayerFixture = $null

$source = Get-Content -LiteralPath $scriptPath -Raw
if ($source -notmatch '(?m)Read-Host\s+-Prompt\s+\$Prompt\s+-AsSecureString') {
    throw 'La captura segura de identificadores debe usar Read-Host -AsSecureString.'
}
if ($source -notmatch '(?m)Read-Host\s+[^\r\n]*autoriza mostrar únicamente el valor categórico seguro' -or
    $source -notmatch '\$showTaxpayerType\s*=\s*\$taxpayerTypeConsent\s+-match' -or
    $source -notmatch 'if\s*\(\$showTaxpayerType\s+-and\s+\$null\s+-ne\s+\$taxpayerResult\.SafeTaxpayerTypeValue\)' -or
    $source -notmatch '\-CaptureTaxpayerType') {
    throw 'La visualización debe requerir autorización S explícita y una respuesta SRI inspeccionable.'
}
if ($source -match '(?im)Write-(?:Host|Output|Verbose|Warning|Error|Information)\s+[^\r\n]*(?:\$ruc|\$cedula|\$plate|\$requestUri|\$query|\$body)') {
    throw 'Se detectó una posible impresión directa de datos personales o parámetros.'
}
if ($source -match '(?im)\b(?:Out-File|Set-Content|Add-Content|Export-Csv)\b') {
    throw 'El diagnóstico no debe guardar respuestas ni identificadores en archivos.'
}

$retryAst = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-AntRequestWithSingleRetry'
}, $true)
$retrySource = $retryAst.Extent.Text
if ($retrySource -notmatch 'Start-Sleep\s+-Seconds\s+10' -or
    $retrySource -notmatch '\@\(502,\s*503,\s*504\)' -or
    [System.Text.RegularExpressions.Regex]::Matches($retrySource, 'Invoke-AntGet').Count -ne 2) {
    throw 'El reintento ANT debe ser una única segunda solicitud tras 10 segundos para HTTP 502, 503 o 504.'
}

Write-Host 'Pruebas del diagnóstico ANT aprobadas: autorización S/N, codificación, privacidad y límite de reintento.'
