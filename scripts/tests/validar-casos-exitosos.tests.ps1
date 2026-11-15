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
    'Get-ValueTypeName',
    'Get-SafeName',
    'Get-JsonShape',
    'Get-HtmlAttribute',
    'Test-SensitivePathSegment',
    'Get-SafePath',
    'Get-SafeAction',
    'Get-SafeUriPath',
    'Get-ResponseAnalysis',
    'Get-HttpStatusMeaning',
    'Get-ConnectionFailureCategory',
    'Test-SafeRedirect',
    'Test-AntRetryStatus',
    'Test-AntQueryFormObserved',
    'Test-AntAccessRestriction',
    'Show-DiagnosticResult',
    'Invoke-AntGet',
    'Invoke-AntRequestWithSingleRetry',
    'Get-SafeTaxpayerTypeValue',
    'Get-TaxpayerTypeInspection',
    'Show-AuthorizedTaxpayerType',
    'Invoke-TaxpayerTypeOnly',
    'Invoke-AntOnlyDiagnostics'
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
$script:antResponses = [System.Collections.Generic.Queue[object]]::new()
$script:antRequests = [System.Collections.Generic.List[object]]::new()
$script:privatePrompts = [System.Collections.Generic.List[string]]::new()
$script:consentPromptCount = 0
$script:sleepCalls = [System.Collections.Generic.List[int]]::new()
$script:mockConsent = 'S'
$script:mockRuc = '1234567890123'
$script:mockCedula = '0000000000'
$script:mockPlate = 'TEST1234'

function Read-PrivateValue {
    param([string]$Prompt, [string]$Pattern)
    $script:privatePrompts.Add($Prompt)
    if ($Pattern -match '\{10\}') { return $script:mockCedula }
    if ($Pattern -match 'A-Za-z0-9') { return $script:mockPlate }
    return $script:mockRuc
}

function Invoke-EndpointGet {
    param(
        [string]$Service,
        [string]$BaseUrl,
        [System.Collections.IDictionary]$Parameters,
        [int]$TimeoutSeconds,
        [int]$MaxBodyCharacters,
        [switch]$CaptureTaxpayerType,
        [System.Net.CookieContainer]$CookieContainer
    )

    if ($Service -like 'ANT*') {
        $safeValues = [ordered]@{}
        foreach ($key in $Parameters.Keys) { $safeValues[$key] = $Parameters[$key] }
        $script:antRequests.Add([pscustomobject]@{
            BaseUrl = $BaseUrl
            ParameterNames = @($Parameters.Keys) -join ','
            ParameterValues = $safeValues
            CookieContainer = $CookieContainer
        })
        if ($script:antResponses.Count -eq 0) {
            throw 'Falta una respuesta ANT sintética para la prueba local.'
        }
        foreach ($key in @($Parameters.Keys)) { $Parameters[$key] = $null }
        return $script:antResponses.Dequeue()
    }

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

function Start-Sleep {
    param([int]$Seconds)
    $script:sleepCalls.Add($Seconds)
}

function Reset-TaxpayerModeTest {
    $script:modeResponses.Clear()
    $script:modeRequests.Clear()
    $script:antResponses.Clear()
    $script:antRequests.Clear()
    $script:privatePrompts.Clear()
    $script:sleepCalls.Clear()
    $script:consentPromptCount = 0
    $script:mockConsent = 'S'
    $script:mockRuc = '1234567890123'
    $script:mockCedula = '0000000000'
    $script:mockPlate = 'TEST1234'
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
    $source -notmatch '\[switch\]\$SoloANT' -or
    $source -notmatch '(?s)elseif\s*\(\$SoloANT\)\s*\{\s*Invoke-AntOnlyDiagnostics\s*\}' -or
    $source -notmatch '(?s)elseif\s*\(\$SoloTipoContribuyente\)\s*\{\s*Invoke-TaxpayerTypeOnly\s*\}') {
    throw 'Los modos exclusivos SoloTipoContribuyente y SoloANT deben tener flujos independientes.'
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
    $source -notmatch '(?s)function Test-AntRetryStatus.*?\$StatusCode\s+-in\s+\@\(502,\s*503,\s*504\)' -or
    [System.Text.RegularExpressions.Regex]::Matches($retrySource, 'Invoke-AntGet').Count -ne 2) {
    throw 'El reintento ANT debe ser una única segunda solicitud tras 10 segundos para HTTP 502, 503 o 504.'
}

function New-SyntheticAntResult {
    param(
        [int]$StatusCode = 200,
        [string]$ResponseType = 'HTML',
        [string]$Forms = '',
        [int]$FormCount = 0,
        [string]$FieldNames = '',
        [string]$HiddenFieldNames = '',
        [string]$Indicators = '',
        [bool]$FormActionHasQuery = $false,
        [bool]$UsesAjax = $false,
        [bool]$HasScripts = $false,
        [bool]$HasIframe = $false
    )

    return [pscustomobject]@{
        Service = 'ANT — respuesta sintética'
        Url = 'https://consultaweb.ant.gob.ec/PortalWEB/'
        ParameterName = ''
        TestedAt = '2026-11-14 00:00:00 -05:00'
        StatusCode = $StatusCode
        StatusMeaning = Get-HttpStatusMeaning -StatusCode $StatusCode
        ContentType = if ($ResponseType -eq 'JSON') { 'application/json' } else { 'text/html' }
        ElapsedMs = 1
        Redirects = 'ninguna'
        Failure = ''
        ResponseType = $ResponseType
        Structure = 'estructura sintética; valores omitidos'
        HasBoolean = $false
        BooleanValue = $null
        PageTitle = $true
        Forms = $Forms
        FormCount = $FormCount
        FormActionHasQuery = $FormActionHasQuery
        FieldNames = $FieldNames
        HiddenFieldNames = $HiddenFieldNames
        TableCount = 0
        TableHeaders = ''
        PointsViewReferences = 0
        UsesAjax = $UsesAjax
        HasScripts = $HasScripts
        HasIframe = $HasIframe
        Indicators = $Indicators
        RelevantHeaders = 'ninguno observado'
    }
}

# Synthetic page fixtures exercise the safe structural inspection without network access.
$criteriaUri = [System.Uri]::new('https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_criterio_consulta.jsp')
$safeFormHtml = '<html><head><title>Consulta</title></head><body><form method="GET" action="/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp"><input name="ps_tipo_identificacion" value="CED"><input name="ps_identificacion" value="0000000000"><input name="ps_placa" value=""></form></body></html>'
$safeFormAnalysis = Get-ResponseAnalysis -MediaType 'text/html' -Body $safeFormHtml -Truncated $false -BaseUri $criteriaUri -MaxBodyCharacters 65536
$safeFormResult = New-SyntheticAntResult -Forms $safeFormAnalysis.Forms -FormCount $safeFormAnalysis.FormCount -FieldNames $safeFormAnalysis.FieldNames -HiddenFieldNames $safeFormAnalysis.HiddenFieldNames -Indicators $safeFormAnalysis.Indicators -FormActionHasQuery $safeFormAnalysis.FormActionHasQuery -UsesAjax $safeFormAnalysis.UsesAjax -HasScripts $safeFormAnalysis.HasScripts -HasIframe $safeFormAnalysis.HasIframe
if (-not (Test-AntQueryFormObserved -Result $safeFormResult) -or $safeFormAnalysis.FormCount -ne 1 -or
    $safeFormAnalysis.FieldNames -cne 'ps_tipo_identificacion, ps_identificacion, ps_placa') {
    throw 'El formulario GET sintético compatible no fue reconocido.'
}
if ((Get-SafeUriPath -Uri ([System.Uri]::new('https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_grid_puntos.jsp'))) -notmatch '/clp_grid_puntos\.jsp$' -or
    (Get-SafeUriPath -Uri ([System.Uri]::new('https://consultaweb.ant.gob.ec/private/0123456789abcdef0123456789abcdef'))) -match '0123456789abcdef') {
    throw 'La ruta oficial conocida debe seguir legible y los segmentos opacos deben ocultarse.'
}
if (($safeFormAnalysis | ConvertTo-Json -Compress) -match '0000000000') {
    throw 'El análisis HTML no debe exponer valores de identificación.'
}

$hiddenHtml = '<html><form method="GET" action="/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp"><input type="hidden" name="sessionToken" value="SYNTHETIC_SECRET"><input name="ps_tipo_identificacion"><input name="ps_identificacion"><input name="ps_placa"></form></html>'
$hiddenAnalysis = Get-ResponseAnalysis -MediaType 'text/html' -Body $hiddenHtml -Truncated $false -BaseUri $criteriaUri -MaxBodyCharacters 65536
$hiddenResult = New-SyntheticAntResult -Forms $hiddenAnalysis.Forms -FormCount $hiddenAnalysis.FormCount -FieldNames $hiddenAnalysis.FieldNames -HiddenFieldNames $hiddenAnalysis.HiddenFieldNames -Indicators $hiddenAnalysis.Indicators
if ($hiddenAnalysis.HiddenFieldNames -cne 'sessionToken' -or (Test-AntQueryFormObserved -Result $hiddenResult)) {
    throw 'Los nombres de campos ocultos se deben informar y bloquean un envío no verificado.'
}
if (($hiddenAnalysis | ConvertTo-Json -Compress) -match 'SYNTHETIC_SECRET') {
    throw 'El análisis HTML no debe retener ni exponer valores de campos ocultos.'
}
$authHeaderResult = New-SyntheticAntResult -Forms $safeFormAnalysis.Forms -FormCount $safeFormAnalysis.FormCount -FieldNames $safeFormAnalysis.FieldNames
$authHeaderResult.RelevantHeaders = 'WWW-Authenticate presente'
if (Test-AntQueryFormObserved -Result $authHeaderResult) {
    throw 'Un encabezado WWW-Authenticate debe impedir el envío automático del formulario.'
}
if (-not (Test-AntAccessRestriction -Result $authHeaderResult) -or
    -not (Test-AntAccessRestriction -Result (New-SyntheticAntResult -StatusCode 403)) -or
    -not (Test-AntAccessRestriction -Result (New-SyntheticAntResult -Indicators 'indicio de CAPTCHA'))) {
    throw 'Los estados 401/403 y los indicadores sintéticos de autenticación/CAPTCHA deben detener el diagnóstico.'
}
$scriptFormResult = New-SyntheticAntResult -Forms $safeFormAnalysis.Forms -FormCount $safeFormAnalysis.FormCount -FieldNames $safeFormAnalysis.FieldNames -HasScripts $true
if (Test-AntQueryFormObserved -Result $scriptFormResult) {
    throw 'Un formulario acompañado de scripts no verificados no debe enviarse automáticamente.'
}
$queryActionHtml = '<html><form method="GET" action="/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp?flow=unknown"><input name="ps_tipo_identificacion"><input name="ps_identificacion"><input name="ps_placa"></form></html>'
$queryActionAnalysis = Get-ResponseAnalysis -MediaType 'text/html' -Body $queryActionHtml -Truncated $false -BaseUri $criteriaUri -MaxBodyCharacters 65536
if (-not $queryActionAnalysis.FormActionHasQuery) {
    throw 'La query original del action debe detectarse sin imprimir sus valores.'
}
$queryActionResult = New-SyntheticAntResult -Forms $queryActionAnalysis.Forms -FormCount $queryActionAnalysis.FormCount -FieldNames $queryActionAnalysis.FieldNames -FormActionHasQuery $queryActionAnalysis.FormActionHasQuery
if (Test-AntQueryFormObserved -Result $queryActionResult) {
    throw 'Un action con parámetros no observados no debe habilitar el envío automático.'
}

$tableHtml = '<html><body><p>Consulta de puntos de licencia</p><table><tr><th>Puntos</th><th>Saldo licencia</th></tr><tr><td>PERSONAL_VALUE</td><td>PERSONAL_VALUE</td></tr></table></body></html>'
$tableAnalysis = Get-ResponseAnalysis -MediaType 'text/html' -Body $tableHtml -Truncated $false -BaseUri $criteriaUri -MaxBodyCharacters 65536
if ($tableAnalysis.TableCount -ne 1 -or $tableAnalysis.TableHeaders -cne 'Puntos | Saldo licencia' -or
    $tableAnalysis.Indicators -notmatch 'referencia a puntos de licencia' -or
    ($tableAnalysis | ConvertTo-Json -Compress) -match 'PERSONAL_VALUE') {
    throw 'La inspección de tablas debe conservar encabezados y omitir los valores de filas.'
}

$captchaHtml = '<html><body><form>CAPTCHA requerido</form></body></html>'
$captchaAnalysis = Get-ResponseAnalysis -MediaType 'text/html' -Body $captchaHtml -Truncated $false -BaseUri $criteriaUri -MaxBodyCharacters 65536
if ($captchaAnalysis.Indicators -notmatch 'indicio de CAPTCHA') {
    throw 'El indicador sintético de CAPTCHA no fue detectado.'
}

$ajaxHtml = '<html><body><iframe src="/frame"></iframe><script>fetch("/api")</script></body></html>'
$ajaxAnalysis = Get-ResponseAnalysis -MediaType 'text/html' -Body $ajaxHtml -Truncated $false -BaseUri $criteriaUri -MaxBodyCharacters 65536
if (-not $ajaxAnalysis.UsesAjax -or -not $ajaxAnalysis.HasScripts -or -not $ajaxAnalysis.HasIframe) {
    throw 'Los indicadores sintéticos de AJAX o iframe no fueron detectados.'
}

$malformedHtml = '<html><form><input name="field" value="unterminated'
$malformedAnalysis = Get-ResponseAnalysis -MediaType 'text/html' -Body $malformedHtml -Truncated $false -BaseUri $criteriaUri -MaxBodyCharacters 65536
if ($malformedAnalysis.ResponseType -ne 'HTML') {
    throw 'El HTML mal formado debe producir un resumen estructural seguro, no una excepción.'
}

$jsonAnalysis = Get-ResponseAnalysis -MediaType 'application/json' -Body '{"puntos":15,"identificacion":"0000000000"}' -Truncated $false -BaseUri $criteriaUri -MaxBodyCharacters 65536
if ($jsonAnalysis.ResponseType -ne 'JSON' -or ($jsonAnalysis | ConvertTo-Json -Compress) -match '0000000000') {
    throw 'El análisis JSON debe describir tipos y nombres sin exponer valores sintéticos.'
}

foreach ($status in @(200, 502, 503, 504)) {
    $meaning = Get-HttpStatusMeaning -StatusCode $status
    if ([string]::IsNullOrWhiteSpace($meaning)) { throw "No se clasificó HTTP $status." }
}
if (Test-AntRetryStatus -StatusCode $null) {
    throw 'Una conexión sin respuesta HTTP no debe activar el reintento por estado.'
}
if ((Get-ConnectionFailureCategory -Exception ([System.TimeoutException]::new())) -cne 'Timeout') {
    throw 'El timeout sintético no fue clasificado correctamente.'
}
$initialUri = [System.Uri]::new('https://consultaweb.ant.gob.ec/PortalWEB/')
if (-not (Test-SafeRedirect -InitialUri $initialUri -NextUri ([System.Uri]::new('https://consultaweb.ant.gob.ec/PortalWEB/start')) -RedirectCount 1) -or
    (Test-SafeRedirect -InitialUri $initialUri -NextUri ([System.Uri]::new('https://example.test/redirect')) -RedirectCount 1) -or
    (Test-SafeRedirect -InitialUri $initialUri -NextUri ([System.Uri]::new('http://consultaweb.ant.gob.ec/redirect')) -RedirectCount 1) -or
    (Test-SafeRedirect -InitialUri $initialUri -NextUri ([System.Uri]::new('https://consultaweb.ant.gob.ec/redirect')) -RedirectCount 5)) {
    throw 'La política sintética de redirecciones debe limitar HTTPS, el host ANT y cinco saltos.'
}

# Offline mocked HTTP responses validate the one-retry rule and parameter clearing.
foreach ($retryStatus in @(502, 503, 504)) {
    Reset-TaxpayerModeTest
    $script:antResponses.Enqueue((New-SyntheticAntResult -StatusCode $retryStatus))
    $script:antResponses.Enqueue((New-SyntheticAntResult -StatusCode $retryStatus))
    $queryParameters = New-AntRequestParameters -Cedula $script:mockCedula -Plate '' -IncludePlate
    $retryOutput = @(& { Invoke-AntRequestWithSingleRetry -Parameters $queryParameters -TimeoutSeconds 1 -MaxBodyCharacters 1024 } 6>&1)
    if ($script:antRequests.Count -ne 2 -or $script:sleepCalls.Count -ne 1 -or $script:sleepCalls[0] -ne 10 -or
        $script:antRequests[0].ParameterValues.ps_identificacion -cne $script:mockCedula -or
        $script:antRequests[1].ParameterValues.ps_identificacion -cne $script:mockCedula) {
        throw "HTTP $retryStatus debe permitir un solo reintento a los 10 segundos con los mismos parámetros en memoria."
    }
    $retryMessages = @($retryOutput | ForEach-Object { [string]$_.MessageData }) -join '|'
    if ($retryMessages -match [regex]::Escape($script:mockCedula)) {
        throw 'La salida del reintento no debe incluir la cédula sintética.'
    }
}
foreach ($nonRetryStatus in @(200, 500)) {
    Reset-TaxpayerModeTest
    $script:antResponses.Enqueue((New-SyntheticAntResult -StatusCode $nonRetryStatus))
    $queryParameters = New-AntRequestParameters -Cedula $script:mockCedula -Plate '' -IncludePlate
    $null = @(& { Invoke-AntRequestWithSingleRetry -Parameters $queryParameters -TimeoutSeconds 1 -MaxBodyCharacters 1024 } 6>&1)
    if ($script:antRequests.Count -ne 1 -or $script:sleepCalls.Count -ne 0) {
        throw "HTTP $nonRetryStatus no debe reintentarse."
    }
}

function Reset-AntModeTest {
    Reset-TaxpayerModeTest
    $script:antResponses.Enqueue((New-SyntheticAntResult -ResponseType 'HTML'))
    $script:antResponses.Enqueue((New-SyntheticAntResult -Forms $safeFormAnalysis.Forms -FormCount $safeFormAnalysis.FormCount -FieldNames $safeFormAnalysis.FieldNames))
    $script:antResponses.Enqueue((New-SyntheticAntResult -ResponseType 'HTML'))
}

Reset-AntModeTest
$script:mockConsent = 'S'
$script:antResponses.Enqueue((New-SyntheticAntResult -ResponseType 'HTML'))
$script:antResponses.Enqueue((New-SyntheticAntResult -ResponseType 'HTML'))
$antAuthorizedOutput = @(& { Invoke-AntOnlyDiagnostics } 6>&1)
$antAuthorizedMessages = @($antAuthorizedOutput | ForEach-Object { [string]$_.MessageData }) -join '|'
if ($script:privatePrompts.Count -ne 2 -or $script:privatePrompts[0] -notmatch 'cédula' -or $script:privatePrompts[1] -notmatch 'placa' -or
    $script:consentPromptCount -ne 1 -or $script:antRequests.Count -ne 5) {
    throw 'SoloANT debe pedir cédula después de validar el flujo y pedir placa únicamente tras consentimiento.'
}
if ($script:antRequests[3].ParameterValues.ps_placa -cne '' -or $script:antRequests[4].ParameterValues.ps_placa -cne $script:mockPlate) {
    throw 'SoloANT debe hacer primero la consulta sin placa y luego la variante autorizada.'
}
if ($script:antRequests[3].ParameterValues.ps_identificacion -cne $script:mockCedula -or
    $script:antRequests[4].ParameterValues.ps_identificacion -cne $script:mockCedula -or
    $antAuthorizedMessages -match [regex]::Escape($script:mockCedula) -or
    $antAuthorizedMessages -match [regex]::Escape($script:mockPlate)) {
    throw 'SoloANT no debe perder parámetros entre llamadas ni exponer identificaciones.'
}
$cookieContainer = $script:antRequests[0].CookieContainer
if ($null -eq $cookieContainer -or @($script:antRequests | Where-Object { -not [object]::ReferenceEquals($_.CookieContainer, $cookieContainer) }).Count -ne 0) {
    throw 'La navegación y consulta ANT deben compartir una cookie jar en memoria por ejecución.'
}
if (@($script:antRequests | Where-Object { $_.BaseUrl -notlike 'https://consultaweb.ant.gob.ec/*' }).Count -ne 0) {
    throw 'SoloANT no debe enviar la cookie jar a dominios ajenos a ANT.'
}

Reset-AntModeTest
$script:mockConsent = 'N'
$script:antResponses.Enqueue((New-SyntheticAntResult -ResponseType 'HTML'))
$antNoPlateOutput = @(& { Invoke-AntOnlyDiagnostics } 6>&1)
if ($script:privatePrompts.Count -ne 1 -or $script:consentPromptCount -ne 1 -or $script:antRequests.Count -ne 4 -or
    @($script:antRequests[3].ParameterValues.Keys) -cnotcontains 'ps_placa' -or
    $script:antRequests[3].ParameterValues.ps_placa -cne '') {
    throw 'Con autorización N, SoloANT debe consultar solo con ps_placa vacío y no pedir placa.'
}

Reset-TaxpayerModeTest
$script:antResponses.Enqueue((New-SyntheticAntResult -StatusCode 502))
$antUnavailableOutput = @(& { Invoke-AntOnlyDiagnostics } 6>&1)
if ($script:privatePrompts.Count -ne 0 -or $script:antRequests.Count -ne 1 -or $script:consentPromptCount -ne 0) {
    throw 'Si el portal inicial devuelve gateway, SoloANT debe terminar sin solicitar ni enviar datos.'
}

Reset-TaxpayerModeTest
$script:antResponses.Enqueue((New-SyntheticAntResult -ResponseType 'HTML'))
$script:antResponses.Enqueue((New-SyntheticAntResult -Forms $hiddenAnalysis.Forms -FormCount $hiddenAnalysis.FormCount -FieldNames $hiddenAnalysis.FieldNames -HiddenFieldNames $hiddenAnalysis.HiddenFieldNames))
$script:antResponses.Enqueue((New-SyntheticAntResult -ResponseType 'HTML'))
$antUnverifiedFormOutput = @(& { Invoke-AntOnlyDiagnostics } 6>&1)
if ($script:privatePrompts.Count -ne 0 -or $script:antRequests.Count -ne 3 -or $script:consentPromptCount -ne 0) {
    throw 'Un formulario con campos ocultos debe terminar antes de pedir o enviar identificaciones.'
}

Reset-TaxpayerModeTest
$script:antResponses.Enqueue((New-SyntheticAntResult -ResponseType 'HTML'))
$script:antResponses.Enqueue((New-SyntheticAntResult -StatusCode 502))
$antCriteriaUnavailableOutput = @(& { Invoke-AntOnlyDiagnostics } 6>&1)
if ($script:privatePrompts.Count -ne 0 -or $script:antRequests.Count -ne 2 -or $script:consentPromptCount -ne 0) {
    throw 'Si la página de criterios falla, SoloANT debe detenerse antes de consultar la vista de puntos o solicitar identificadores.'
}

$antModeAst = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-AntOnlyDiagnostics'
}, $true)
$antModeCommandNames = @($antModeAst.FindAll({ param($node) $node -is [System.Management.Automation.Language.CommandAst] }, $true) |
    ForEach-Object { $_.GetCommandName() })
if ($antModeCommandNames -contains 'Invoke-PrivateDiagnostics' -or
    $antModeCommandNames -contains 'Invoke-TaxpayerTypeOnly' -or
    $antModeCommandNames -contains 'Invoke-EndpointGet' -or
    $antModeAst.Extent.Text -match '(?i)srienlinea\.sri\.gob\.ec') {
    throw 'SoloANT no debe despachar a SRI ni invocar el flujo HTTP compartido directamente.'
}

Write-Host 'Pruebas PowerShell aprobadas: SoloANT aislado, formulario sintético, privacidad, cookies en memoria, errores HTTP, redirecciones y reintentos acotados.'
