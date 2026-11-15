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
    'Show-AuthorizedTaxpayerType',
    'Invoke-TaxpayerTypeOnly'
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

$safeCategories = @(
    'SOCIEDAD',
    'PERSONA NATURAL',
    'CATEGORIA-PRUEBA',
    'TIPO_PERSONA',
    'PERSONA JURÍDICA'
)
foreach ($category in $safeCategories) {
    if ((Get-SafeTaxpayerTypeValue -Value $category) -cne $category) {
        throw 'Una categoría breve con letras Unicode y separadores permitidos fue rechazada.'
    }
}

$unsafeValues = @(
    '1234567890123',
    'SYNTHETIC@EXAMPLE.TEST',
    'https://example.test/type',
    'BEARER-SYNTHETIC-TOKEN',
    'bearer-synthetic-token',
    'access_token-synthetic',
    "PERSONA`nNATURAL",
    ('A' * 65),
    '',
    '   '
)
foreach ($unsafeValue in $unsafeValues) {
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

$script:TimeoutSeconds = 20
$script:MaxBodyCharacters = 65536
$script:modeResponses = [System.Collections.Generic.Queue[object]]::new()
$script:modeRequests = [System.Collections.Generic.List[object]]::new()
$script:privatePrompts = [System.Collections.Generic.List[string]]::new()
$script:consentPromptCount = 0
$script:mockConsent = 'S'
$script:mockRuc = '1234567890123'

function Read-PrivateValue {
    param([string]$Prompt, [string]$Pattern)
    $script:privatePrompts.Add($Prompt)
    return $script:mockRuc
}

function Invoke-EndpointGet {
    param(
        [string]$Service,
        [string]$BaseUrl,
        [System.Collections.IDictionary]$Parameters,
        [int]$TimeoutSeconds,
        [int]$MaxBodyCharacters,
        [switch]$CaptureTaxpayerType
    )

    $script:modeRequests.Add([pscustomobject]@{
        Service = $Service
        BaseUrl = $BaseUrl
        ParameterNames = @($Parameters.Keys) -join ','
        CaptureTaxpayerType = $CaptureTaxpayerType.IsPresent
    })
    if ($script:modeResponses.Count -eq 0) {
        throw 'Falta una respuesta HTTP sintética para la prueba local.'
    }
    return $script:modeResponses.Dequeue()
}

function Read-Host {
    param([string]$Prompt)
    $script:consentPromptCount++
    return $script:mockConsent
}

function Reset-TaxpayerModeTest {
    $script:modeResponses.Clear()
    $script:modeRequests.Clear()
    $script:privatePrompts.Clear()
    $script:consentPromptCount = 0
    $script:mockConsent = 'S'
    $script:mockRuc = '1234567890123'
}

Reset-TaxpayerModeTest
$script:modeResponses.Enqueue([pscustomobject]@{ StatusCode = 200; HasBoolean = $true; BooleanValue = $true })
$script:modeResponses.Enqueue([pscustomobject]@{
    TaxpayerTypeInspectionAvailable = $true
    SafeTaxpayerTypeValue = 'PERSONA NATURAL'
})
$authorizedModeOutput = @(& { Invoke-TaxpayerTypeOnly } 6>&1)
$authorizedModeMessages = @($authorizedModeOutput | ForEach-Object { [string]$_.MessageData })
$authorizedCategoryOutput = @($authorizedModeMessages | Where-Object { $_ -like 'tipoContribuyente*' })
if ($authorizedCategoryOutput.Count -ne 1 -or
    $authorizedCategoryOutput[0] -cne 'tipoContribuyente (valor categórico): PERSONA NATURAL') {
    throw 'El modo exclusivo debe mostrar solo la categoría segura tras autorización.'
}
if ($script:privatePrompts.Count -ne 1 -or $script:privatePrompts[0] -notmatch '(?i)RUC' -or
    $script:privatePrompts[0] -match '(?i)placa|cédula') {
    throw 'El modo exclusivo debe solicitar únicamente el RUC.'
}
if ($script:consentPromptCount -ne 1 -or $script:modeRequests.Count -ne 2 -or
    $script:modeRequests[0].ParameterNames -cne 'numeroRuc' -or
    $script:modeRequests[1].ParameterNames -cne 'ruc' -or
    $script:modeRequests[0].CaptureTaxpayerType -or
    -not $script:modeRequests[1].CaptureTaxpayerType) {
    throw 'El modo exclusivo debe consultar existencia, luego detalles y pedir autorización una sola vez.'
}
if ((@($script:modeRequests | ForEach-Object { $_.Service }) -join '|') -cne 'SRI — existencia del RUC|SRI — información del contribuyente' -or
    ($authorizedModeMessages -join '|') -match [regex]::Escape($script:mockRuc)) {
    throw 'El modo exclusivo no debe llamar otros servicios ni mostrar el RUC.'
}
$authorizedModeOutput = $null
$authorizedModeMessages = $null

Reset-TaxpayerModeTest
$script:mockConsent = 'N'
$script:modeResponses.Enqueue([pscustomobject]@{ StatusCode = 200; HasBoolean = $true; BooleanValue = $true })
$script:modeResponses.Enqueue([pscustomobject]@{
    TaxpayerTypeInspectionAvailable = $true
    SafeTaxpayerTypeValue = 'SOCIEDAD'
})
$deniedModeOutput = @(& { Invoke-TaxpayerTypeOnly } 6>&1)
$deniedModeMessages = @($deniedModeOutput | ForEach-Object { [string]$_.MessageData })
if (@($deniedModeMessages | Where-Object { $_ -like 'tipoContribuyente*' }).Count -ne 0 -or
    $script:modeRequests.Count -ne 2 -or $script:consentPromptCount -ne 1) {
    throw 'Con autorización N, el modo no debe mostrar el valor categórico.'
}
$deniedModeOutput = $null
$deniedModeMessages = $null

Reset-TaxpayerModeTest
$script:modeResponses.Enqueue([pscustomobject]@{ StatusCode = 200; HasBoolean = $true; BooleanValue = $false })
$negativeModeOutput = @(& { Invoke-TaxpayerTypeOnly } 6>&1)
if ($script:modeRequests.Count -ne 1 -or $script:consentPromptCount -ne 0 -or
    $script:privatePrompts.Count -ne 1) {
    throw 'Si el SRI responde false, el modo debe terminar sin consultar detalles ni pedir otros datos.'
}
$negativeModeOutput = $null

Reset-TaxpayerModeTest
$script:modeResponses.Enqueue([pscustomobject]@{ StatusCode = 503; HasBoolean = $false; BooleanValue = $false })
$errorModeOutput = @(& { Invoke-TaxpayerTypeOnly } 6>&1)
if ($script:modeRequests.Count -ne 1 -or $script:consentPromptCount -ne 0 -or
    $script:privatePrompts.Count -ne 1) {
    throw 'Ante un error HTTP, el modo debe terminar sin consultar detalles ni pedir otros datos.'
}
$errorModeOutput = $null

$modeFunctionAst = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-TaxpayerTypeOnly'
}, $true)
$modeCommandNames = @($modeFunctionAst.FindAll({ param($node) $node -is [System.Management.Automation.Language.CommandAst] }, $true) |
    ForEach-Object { $_.GetCommandName() })
foreach ($forbiddenCommand in @('Invoke-PrivateDiagnostics', 'Invoke-AntRequestWithSingleRetry', 'Invoke-AntGet', 'Get-OptionalAntRequestParameters')) {
    if ($modeCommandNames -contains $forbiddenCommand) {
        throw 'El modo exclusivo contiene una llamada al flujo vehicular o ANT.'
    }
}
if (@($modeCommandNames | Where-Object { $_ -eq 'Read-PrivateValue' }).Count -ne 1 -or
    @($modeCommandNames | Where-Object { $_ -eq 'Invoke-EndpointGet' }).Count -ne 2) {
    throw 'La función exclusiva debe solicitar solo un RUC y realizar como máximo las dos consultas SRI definidas.'
}
$modeFunctionAst = $null
$modeCommandNames = $null

$source = Get-Content -LiteralPath $scriptPath -Raw
if ($source -notmatch '(?m)Read-Host\s+-Prompt\s+\$Prompt\s+-AsSecureString') {
    throw 'La captura segura de identificadores debe usar Read-Host -AsSecureString.'
}
if ($source -notmatch '\[switch\]\$SoloTipoContribuyente' -or
    $source -notmatch '(?s)if\s*\(\$SoloTipoContribuyente\)\s*\{\s*Invoke-TaxpayerTypeOnly\s*\}\s*else\s*\{\s*Invoke-PrivateDiagnostics\s*\}') {
    throw 'El parámetro SoloTipoContribuyente debe despachar a un flujo independiente y conservar el diagnóstico normal.'
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

Write-Host 'Pruebas PowerShell aprobadas: categorías seguras, consentimiento, flujo SRI aislado, privacidad, codificación y límite de reintento.'
