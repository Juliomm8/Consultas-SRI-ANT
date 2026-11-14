[CmdletBinding()]
param(
    [ValidateRange(1, 60)]
    [int]$TimeoutSeconds = 20,

    [ValidateRange(0, 10)]
    [int]$MaxRedirects = 5,

    [ValidateRange(1024, 262144)]
    [int]$MaxBodyCharacters = 65536
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$script:StartedAt = Get-Date

function New-RequestUrl {
    param(
        [Parameter(Mandatory)]
        [string]$BaseUrl,

        [Parameter(Mandatory)]
        [System.Collections.IDictionary]$Parameters
    )

    $parts = foreach ($name in $Parameters.Keys) {
        $nameEncoded = [System.Uri]::EscapeDataString([string]$name)
        $valueEncoded = [System.Uri]::EscapeDataString([string]$Parameters[$name])
        "$nameEncoded=$valueEncoded"
    }

    return "$BaseUrl`?$($parts -join '&')"
}

function Get-SafeUri {
    param([Parameter(Mandatory)][System.Uri]$Uri)

    return $Uri.GetLeftPart([System.UriPartial]::Path)
}

function Get-ValueTypeName {
    param($Value)

    if ($null -eq $Value) { return 'null' }
    if ($Value -is [string]) { return 'string' }
    if ($Value -is [bool]) { return 'boolean' }
    if ($Value -is [System.Array]) { return 'array' }
    if ($Value -is [System.Collections.IDictionary] -or $Value -is [pscustomobject]) { return 'object' }
    if ($Value -is [System.ValueType]) { return 'number' }
    return $Value.GetType().Name
}

function Get-JsonPropertyNames {
    param($Value)

    if ($Value -is [System.Collections.IDictionary]) {
        return @($Value.Keys | ForEach-Object { [string]$_ })
    }

    if ($null -eq $Value) { return @() }
    return @($Value.PSObject.Properties | ForEach-Object { $_.Name })
}

function Get-JsonObjectShape {
    param(
        [Parameter(Mandatory)]
        $Value,

        [int]$MaximumProperties = 30
    )

    $summaries = [System.Collections.Generic.List[string]]::new()
    $propertyNames = @(Get-JsonPropertyNames -Value $Value)
    foreach ($name in ($propertyNames | Select-Object -First $MaximumProperties)) {
        if ($Value -is [System.Collections.IDictionary]) {
            $propertyValue = $Value[$name]
        }
        else {
            $propertyValue = $Value.PSObject.Properties[$name].Value
        }

        $valueType = Get-ValueTypeName -Value $propertyValue
        if ($propertyValue -is [bool]) {
            $summaries.Add("${name}:boolean=$propertyValue")
        }
        elseif ($propertyValue -is [System.Array]) {
            $arrayShape = "${name}:array(count=$($propertyValue.Count))"
            if ($propertyValue.Count -gt 0 -and (Get-ValueTypeName -Value $propertyValue[0]) -eq 'object') {
                $nestedNames = @(Get-JsonPropertyNames -Value $propertyValue[0]) | Select-Object -First 15
                $arrayShape += "; first-item-keys=$($nestedNames -join ',')"
            }
            $summaries.Add($arrayShape)
        }
        elseif ($valueType -eq 'object') {
            $nestedNames = @(Get-JsonPropertyNames -Value $propertyValue) | Select-Object -First 15
            $nestedShape = [System.Collections.Generic.List[string]]::new()
            foreach ($nestedName in $nestedNames) {
                if ($propertyValue -is [System.Collections.IDictionary]) {
                    $nestedValue = $propertyValue[$nestedName]
                }
                else {
                    $nestedValue = $propertyValue.PSObject.Properties[$nestedName].Value
                }
                $nestedShape.Add("${nestedName}:$(Get-ValueTypeName -Value $nestedValue)")
            }
            $summaries.Add("${name}:object($($nestedShape -join ','))")
        }
        else {
            $summaries.Add("${name}:$valueType")
        }
    }

    if ($summaries.Count -eq 0) { return 'objeto sin propiedades visibles' }
    if ($propertyNames.Count -gt $MaximumProperties) { $summaries.Add('…') }
    return $summaries -join '; '
}

function Get-BodyAnalysis {
    param(
        [string]$MediaType,
        [string]$Body,
        [bool]$Truncated,
        [System.Uri]$BaseUri,
        [int]$MaxBodyCharacters
    )

    $trimmed = if ($null -eq $Body) { '' } else { $Body.Trim() }
    if ([string]::IsNullOrWhiteSpace($trimmed)) {
        return [pscustomobject]@{
            ResponseType = 'Vacía'
            Structure = 'El cuerpo de respuesta está vacío.'
            Signal = 'ninguna'
            PageTitle = ''
            Forms = ''
            TableCount = 0
            TableHeaders = ''
            FieldNames = ''
            Indicators = ''
        }
    }

    $looksLikeJson = $MediaType -match '(?i)(^|/|\+)json' -or $trimmed -match '^(\{|\[|true\b|false\b|null\b|"|-?[0-9])'
    if ($looksLikeJson) {
        try {
            $parsed = ConvertFrom-Json -InputObject $trimmed -ErrorAction Stop
            $rootIsArray = $trimmed.StartsWith('[')
            $signal = 'ninguna'

            if ($rootIsArray) {
                if ($parsed -is [System.Array]) {
                    $structure = "JSON array (elementos=$($parsed.Count))"
                    if ($parsed.Count -gt 0 -and (Get-ValueTypeName -Value $parsed[0]) -eq 'object') {
                        $structure += "; primer elemento: $(Get-JsonObjectShape -Value $parsed[0])"
                    }
                }
                elseif ($null -eq $parsed) {
                    $structure = 'JSON array vacío'
                }
                else {
                    $structure = "JSON array; elemento observado: $(Get-ValueTypeName -Value $parsed)"
                    if ((Get-ValueTypeName -Value $parsed) -eq 'object') {
                        $structure += "; propiedades: $(Get-JsonObjectShape -Value $parsed)"
                    }
                }
            }
            elseif ($parsed -is [bool]) {
                $structure = "JSON booleano: $parsed"
                $signal = if ($parsed) { 'positiva' } else { 'negativa' }
            }
            elseif ($parsed -is [string]) {
                $structure = 'JSON string (contenido omitido)'
                if ($parsed -match '(?i)(no existe|no encontrado|no válido|sin resultados|no registra)') {
                    $signal = 'negativa'
                    $structure += '; mensaje con indicio de resultado negativo'
                }
            }
            elseif ($null -eq $parsed) {
                $structure = 'JSON null'
            }
            elseif ((Get-ValueTypeName -Value $parsed) -eq 'object') {
                $structure = "JSON object: $(Get-JsonObjectShape -Value $parsed)"
                $properties = @($parsed.PSObject.Properties)
                if ($properties | Where-Object { $_.Value -is [bool] -and $_.Value -eq $false }) {
                    $signal = 'negativa'
                }
                elseif ($properties | Where-Object { $_.Value -is [bool] -and $_.Value -eq $true }) {
                    $signal = 'positiva'
                }
            }
            else {
                $structure = "JSON scalar: $(Get-ValueTypeName -Value $parsed) (valor omitido)"
            }

            if ($Truncated) { $structure += "; muestra limitada a $MaxBodyCharacters caracteres" }
            return [pscustomobject]@{
                ResponseType = 'JSON'
                Structure = $structure
                Signal = $signal
                PageTitle = ''
                Forms = ''
                TableCount = 0
                TableHeaders = ''
                FieldNames = ''
                Indicators = ''
            }
        }
        catch {
            $note = if ($Truncated) {
                'JSON probable; la muestra limitada no permitió analizar la estructura completa.'
            }
            else {
                'Content-Type o cuerpo con aspecto JSON, pero no se pudo analizar.'
            }
            return [pscustomobject]@{
                ResponseType = 'JSON no analizado'
                Structure = $note
                Signal = 'ninguna'
                PageTitle = ''
                Forms = ''
                TableCount = 0
                TableHeaders = ''
                FieldNames = ''
                Indicators = ''
            }
        }
    }

    if ($MediaType -match '(?i)html' -or $trimmed -match '(?is)^<!doctype\s+html|^<html\b') {
        $title = ''
        $titleMatch = [System.Text.RegularExpressions.Regex]::Match($Body, '(?is)<title\b[^>]*>(.*?)</title>')
        if ($titleMatch.Success) {
            $title = [System.Net.WebUtility]::HtmlDecode(([System.Text.RegularExpressions.Regex]::Replace($titleMatch.Groups[1].Value, '<[^>]+>', '')))
            $title = [System.Text.RegularExpressions.Regex]::Replace($title, '\s+', ' ').Trim()
            $title = [System.Text.RegularExpressions.Regex]::Replace($title, '\d{7,}', '[dígitos omitidos]')
            if ($title.Length -gt 120) { $title = $title.Substring(0, 120) + '…' }
        }

        $forms = [System.Collections.Generic.List[string]]::new()
        $formTags = [System.Text.RegularExpressions.Regex]::Matches($Body, '(?is)<form\b[^>]*>')
        foreach ($formTag in $formTags) {
            $method = Get-HtmlAttributeValue -Tag $formTag.Value -Name 'method'
            if ([string]::IsNullOrWhiteSpace($method)) { $method = 'GET (predeterminado)' }
            $action = Get-HtmlAttributeValue -Tag $formTag.Value -Name 'action'
            if ([string]::IsNullOrWhiteSpace($action)) {
                $safeAction = '(misma ruta)'
            }
            else {
                try {
                    $safeAction = Get-SafeUri -Uri ([System.Uri]::new($BaseUri, $action))
                }
                catch {
                    $safeAction = '(acción no interpretable)'
                }
            }
            $forms.Add("$method $safeAction")
        }

        $fieldNames = [System.Collections.Generic.List[string]]::new()
        $controls = [System.Text.RegularExpressions.Regex]::Matches($Body, '(?is)<(?:input|select|textarea|button)\b[^>]*>')
        foreach ($control in $controls) {
            $name = Get-HtmlAttributeValue -Tag $control.Value -Name 'name'
            if (-not [string]::IsNullOrWhiteSpace($name) -and -not $fieldNames.Contains($name)) {
                $fieldNames.Add($name)
            }
        }

        $tableCount = [System.Text.RegularExpressions.Regex]::Matches($Body, '(?is)<table\b').Count
        $headers = [System.Collections.Generic.List[string]]::new()
        $headerMatches = [System.Text.RegularExpressions.Regex]::Matches($Body, '(?is)<th\b[^>]*>(.*?)</th>')
        foreach ($header in $headerMatches | Select-Object -First 20) {
            $headerText = [System.Text.RegularExpressions.Regex]::Replace($header.Groups[1].Value, '<[^>]+>', '')
            $headerText = [System.Net.WebUtility]::HtmlDecode(([System.Text.RegularExpressions.Regex]::Replace($headerText, '\s+', ' '))).Trim()
            if ($headerText -and -not $headers.Contains($headerText)) { $headers.Add($headerText) }
        }

        $indicatorLabels = [System.Collections.Generic.List[string]]::new()
        $indicatorPatterns = @(
            @{ Pattern = '(?i)captcha|recaptcha'; Label = 'indicio de CAPTCHA' },
            @{ Pattern = '(?i)iniciar sesión|iniciar sesion|login|autenticación|autenticacion|contraseña|password'; Label = 'indicio de autenticación' },
            @{ Pattern = '(?i)acceso denegado|access denied|forbidden|no autorizado'; Label = 'indicio de restricción de acceso' },
            @{ Pattern = '(?i)no se encontraron|sin resultados|no existe|no registra'; Label = 'indicio de ausencia de resultados' },
            @{ Pattern = '(?i)error|parámetro requerido|parametro requerido|campo obligatorio'; Label = 'indicio de mensaje de error o validación' }
        )
        foreach ($indicator in $indicatorPatterns) {
            if ($Body -match $indicator.Pattern) { $indicatorLabels.Add($indicator.Label) }
        }

        $structure = "HTML; formularios=$($forms.Count); tablas=$tableCount"
        if ($fieldNames.Count -gt 0) { $structure += "; nombres de campos=$($fieldNames -join ', ')" }
        if ($headers.Count -gt 0) { $structure += "; encabezados de tabla=$($headers -join ' | ')" }
        $signal = if ($indicatorLabels -contains 'indicio de ausencia de resultados') { 'negativa' } else { 'ninguna' }
        if ($Truncated) { $structure += "; muestra limitada a $MaxBodyCharacters caracteres" }

        return [pscustomobject]@{
            ResponseType = 'HTML'
            Structure = $structure
            Signal = $signal
            PageTitle = $title
            Forms = $forms -join '; '
            TableCount = $tableCount
            TableHeaders = $headers -join ' | '
            FieldNames = $fieldNames -join ', '
            Indicators = $indicatorLabels -join '; '
        }
    }

    if ($trimmed -match '^(?i:true|false)$') {
        $signal = if ($trimmed -eq 'true') { 'positiva' } else { 'negativa' }
        return [pscustomobject]@{
            ResponseType = 'Texto booleano'
            Structure = "Texto plano booleano: $trimmed"
            Signal = $signal
            PageTitle = ''
            Forms = ''
            TableCount = 0
            TableHeaders = ''
            FieldNames = ''
            Indicators = ''
        }
    }

    $signal = if ($trimmed -match '(?i)(no existe|no encontrado|no válido|sin resultados|no registra)') { 'negativa' } else { 'ninguna' }
    $description = if ($MediaType -like 'text/*') { "Texto ($MediaType); contenido omitido." } else { 'Contenido no JSON/HTML; cuerpo omitido.' }
    if ($Truncated) { $description += " Muestra limitada a $MaxBodyCharacters caracteres." }
    return [pscustomobject]@{
        ResponseType = if ($MediaType -like 'text/*') { 'Texto' } else { 'Otro' }
        Structure = $description
        Signal = $signal
        PageTitle = ''
        Forms = ''
        TableCount = 0
        TableHeaders = ''
        FieldNames = ''
        Indicators = ''
    }
}

function Get-HtmlAttributeValue {
    param(
        [Parameter(Mandatory)][string]$Tag,
        [Parameter(Mandatory)][string]$Name
    )

    $escapedName = [System.Text.RegularExpressions.Regex]::Escape($Name)
    $patterns = @(
        '(?is)\b' + $escapedName + '\s*=\s*"([^"]*)"',
        "(?is)\b$escapedName\s*=\s*'([^']*)'",
        '(?is)\b' + $escapedName + '\s*=\s*([^\s>]+)'
    )
    foreach ($pattern in $patterns) {
        $match = [System.Text.RegularExpressions.Regex]::Match($Tag, $pattern)
        if ($match.Success) { return $match.Groups[1].Value }
    }
    return $null
}

function Read-BodySample {
    param(
        [Parameter(Mandatory)][System.Net.Http.HttpResponseMessage]$Response,
        [Parameter(Mandatory)][System.Threading.CancellationToken]$CancellationToken,
        [Parameter(Mandatory)][int]$MaximumCharacters
    )

    if ($null -eq $Response.Content) {
        return [pscustomobject]@{ Text = ''; Truncated = $false }
    }

    # Read at most MaximumCharacters + 1 characters; never persist or print response content.
    $encoding = [System.Text.Encoding]::UTF8
    $charset = $Response.Content.Headers.ContentType.CharSet
    if (-not [string]::IsNullOrWhiteSpace($charset)) {
        try { $encoding = [System.Text.Encoding]::GetEncoding($charset.Trim('"')) } catch { }
    }

    $stream = $Response.Content.ReadAsStreamAsync($CancellationToken).GetAwaiter().GetResult()
    $reader = [System.IO.StreamReader]::new($stream, $encoding, $true)
    $builder = [System.Text.StringBuilder]::new()
    $buffer = [char[]]::new([math]::Min(4096, $MaximumCharacters + 1))
    try {
        while ($builder.Length -lt ($MaximumCharacters + 1)) {
            $remaining = $MaximumCharacters + 1 - $builder.Length
            $readLength = [math]::Min($buffer.Length, $remaining)
            $memory = [System.Memory[char]]::new($buffer).Slice(0, $readLength)
            $read = $reader.ReadAsync($memory, $CancellationToken).AsTask().GetAwaiter().GetResult()
            if ($read -eq 0) { break }
            $null = $builder.Append($buffer, 0, $read)
        }
    }
    finally {
        $reader.Dispose()
    }

    $truncated = $builder.Length -gt $MaximumCharacters
    $text = if ($truncated) { $builder.ToString(0, $MaximumCharacters) } else { $builder.ToString() }
    return [pscustomobject]@{
        Text = $text
        Truncated = $truncated
    }
}

function Get-ConnectionFailureCategory {
    param([Parameter(Mandatory)][System.Exception]$Exception)

    $current = $Exception
    while ($null -ne $current) {
        if ($current -is [System.Threading.Tasks.TaskCanceledException] -or $current -is [System.TimeoutException]) {
            return 'Timeout'
        }
        if ($current -is [System.Security.Authentication.AuthenticationException]) {
            return 'Error TLS'
        }
        if ($current -is [System.Net.Sockets.SocketException]) {
            if ($current.SocketErrorCode.ToString() -in @('HostNotFound', 'TryAgain', 'NoData', 'NoRecovery')) {
                return 'DNS no resuelto'
            }
            return "Error de red ($($current.SocketErrorCode))"
        }
        $current = $current.InnerException
    }

    if ($Exception -is [System.Net.Http.HttpRequestException]) { return 'Error HTTP de conexión' }
    return 'Error de conexión'
}

function Invoke-DiagnosticRequest {
    param(
        [Parameter(Mandatory)]$Test,
        [Parameter(Mandatory)][int]$TimeoutSeconds,
        [Parameter(Mandatory)][int]$MaxRedirects,
        [Parameter(Mandatory)][int]$MaxBodyCharacters
    )

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.UseCookies = $false
    $handler.UseDefaultCredentials = $false
    $handler.AutomaticDecompression = [System.Net.DecompressionMethods]::GZip -bor [System.Net.DecompressionMethods]::Deflate
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [System.Threading.Timeout]::InfiniteTimeSpan
    $cancellation = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($TimeoutSeconds))
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $redirects = [System.Collections.Generic.List[string]]::new()
    $response = $null
    $request = $null
    $statusCode = $null
    $contentType = ''
    $phase = 'conexión y encabezados'
    $redirectNote = ''
    $analysis = $null
    $failureCategory = ''
    $safeBaseUri = [System.Uri]$Test.Url

    try {
        $currentUri = $safeBaseUri
        for ($hop = 0; $hop -le $MaxRedirects; $hop++) {
            $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $currentUri)
            $request.Headers.Accept.ParseAdd('application/json')
            $request.Headers.Accept.ParseAdd('text/html')
            $request.Headers.Accept.ParseAdd('*/*')
            $request.Headers.UserAgent.ParseAdd('Consultas-SRI-ANT-Diagnostic/1.0')

            $response = $client.SendAsync(
                $request,
                [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead,
                $cancellation.Token).GetAwaiter().GetResult()
            $statusCode = [int]$response.StatusCode
            $contentType = if ($response.Content.Headers.ContentType) { $response.Content.Headers.ContentType.ToString() } else { '' }

            if ($statusCode -notin @(300, 301, 302, 303, 307, 308)) { break }
            $location = $response.Headers.Location
            if ($null -eq $location) {
                $redirectNote = '3xx sin encabezado Location.'
                break
            }
            if ($redirects.Count -ge $MaxRedirects) {
                $redirectNote = "Se alcanzó el límite de $MaxRedirects redirecciones."
                break
            }

            $nextUri = if ($location.IsAbsoluteUri) { $location } else { [System.Uri]::new($currentUri, $location) }
            $redirects.Add("HTTP $statusCode -> $(Get-SafeUri -Uri $nextUri)")
            if ($currentUri.Scheme -eq 'https' -and $nextUri.Scheme -ne 'https') {
                $redirectNote = 'No se siguió una redirección que baja de HTTPS a HTTP.'
                break
            }

            $response.Dispose()
            $request.Dispose()
            $response = $null
            $request = $null
            $currentUri = $nextUri
        }

        if ($null -ne $response) {
            $phase = 'lectura limitada del cuerpo'
            $bodySample = Read-BodySample -Response $response -CancellationToken $cancellation.Token -MaximumCharacters $MaxBodyCharacters
            $analysis = Get-BodyAnalysis -MediaType $response.Content.Headers.ContentType.MediaType -Body $bodySample.Text -Truncated $bodySample.Truncated -BaseUri $currentUri -MaxBodyCharacters $MaxBodyCharacters
        }
    }
    catch {
        if ($phase -eq 'lectura limitada del cuerpo') {
            $failureCategory = 'Error durante lectura o análisis de la respuesta'
        }
        else {
            $failureCategory = Get-ConnectionFailureCategory -Exception $_.Exception
        }
        if ($failureCategory -eq 'Timeout') { $failureCategory += " durante $phase" }
    }
    finally {
        $stopwatch.Stop()
        if ($null -ne $response) { $response.Dispose() }
        if ($null -ne $request) { $request.Dispose() }
        $cancellation.Dispose()
        $client.Dispose()
    }

    $headerSummary = [System.Collections.Generic.List[string]]::new()
    if ($null -ne $response) {
        if ($response.Headers.Server) { $headerSummary.Add("Server=$($response.Headers.Server)") }
        if ($response.Headers.CacheControl) { $headerSummary.Add("Cache-Control=$($response.Headers.CacheControl)") }
        if ($response.Headers.Contains('Set-Cookie')) { $headerSummary.Add('Set-Cookie presente (valor omitido)') }
        if ($response.Headers.Contains('WWW-Authenticate')) { $headerSummary.Add('WWW-Authenticate presente') }
        if ($response.Headers.Contains('Retry-After')) { $headerSummary.Add('Retry-After presente') }
    }

    $classification = if ($failureCategory) {
        $failureCategory
    }
    elseif ($null -eq $statusCode) {
        'Sin respuesta HTTP'
    }
    elseif ($statusCode -ge 200 -and $statusCode -lt 300 -and $analysis.Signal -eq 'negativa') {
        "HTTP $statusCode con indicio de resultado negativo"
    }
    elseif ($statusCode -ge 200 -and $statusCode -lt 300 -and $analysis.Signal -eq 'positiva') {
        "HTTP $statusCode con indicio de resultado positivo"
    }
    elseif ($statusCode -ge 200 -and $statusCode -lt 300) {
        "HTTP $statusCode; respuesta recibida"
    }
    else {
        "HTTP $statusCode; respuesta del servidor (no se clasifica como endpoint roto)"
    }

    $redirectSummary = if ($redirects.Count -gt 0) { $redirects -join ' | ' } else { 'Ninguna' }
    if ($redirectNote) { $redirectSummary += "; $redirectNote" }
    $parameterNames = @($Test.Parameters.Keys) -join ', '

    return [pscustomobject]@{
        Service = $Test.Service
        URL = Get-SafeUri -Uri $safeBaseUri
        Method = 'GET'
        Parameters = $parameterNames
        TestedAt = $script:StartedAt.ToString('yyyy-MM-dd HH:mm:ss zzz')
        StatusCode = $statusCode
        ContentType = $contentType
        ElapsedMs = [math]::Round($stopwatch.Elapsed.TotalMilliseconds)
        Redirects = $redirectSummary
        Classification = $classification
        ResponseType = if ($analysis) { $analysis.ResponseType } else { 'No disponible' }
        Structure = if ($analysis) { $analysis.Structure } else { 'No se recibió cuerpo analizable.' }
        PageTitle = if ($analysis) { $analysis.PageTitle } else { '' }
        Forms = if ($analysis) { $analysis.Forms } else { '' }
        TableCount = if ($analysis) { $analysis.TableCount } else { $null }
        TableHeaders = if ($analysis) { $analysis.TableHeaders } else { '' }
        FieldNames = if ($analysis) { $analysis.FieldNames } else { '' }
        Indicators = if ($analysis) { $analysis.Indicators } else { '' }
        RelevantHeaders = if ($headerSummary.Count -gt 0) { $headerSummary -join '; ' } else { 'Sin encabezados adicionales relevantes.' }
    }
}

$rucSentinel = '0000000000000'
$vehicleSentinel = 'NOEXISTE'
$antCedulaSentinel = '0000000000'
$tests = @(
    [pscustomobject]@{
        Service = 'SRI — existencia del RUC'
        Url = New-RequestUrl -BaseUrl 'https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/existePorNumeroRuc' -Parameters ([ordered]@{ numeroRuc = $rucSentinel })
        Parameters = [ordered]@{ numeroRuc = $rucSentinel }
    },
    [pscustomobject]@{
        Service = 'SRI — información del contribuyente'
        Url = New-RequestUrl -BaseUrl 'https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/obtenerPorNumerosRuc' -Parameters ([ordered]@{ ruc = $rucSentinel })
        Parameters = [ordered]@{ ruc = $rucSentinel }
    },
    [pscustomobject]@{
        Service = 'SRI — información del vehículo'
        Url = New-RequestUrl -BaseUrl 'https://srienlinea.sri.gob.ec/sri-matriculacion-vehicular-recaudacion-servicio-internet/rest/BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn' -Parameters ([ordered]@{ numeroPlacaCampvCpn = $vehicleSentinel })
        Parameters = [ordered]@{ numeroPlacaCampvCpn = $vehicleSentinel }
    },
    [pscustomobject]@{
        Service = 'ANT — puntos de licencia'
        Url = New-RequestUrl -BaseUrl 'https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp' -Parameters ([ordered]@{ ps_tipo_identificacion = 'CED'; ps_identificacion = $antCedulaSentinel; ps_placa = '' })
        Parameters = [ordered]@{ ps_tipo_identificacion = 'CED'; ps_identificacion = $antCedulaSentinel; ps_placa = '' }
    }
)

$results = [System.Collections.Generic.List[object]]::new()
foreach ($test in $tests) {
    $results.Add((Invoke-DiagnosticRequest -Test $test -TimeoutSeconds $TimeoutSeconds -MaxRedirects $MaxRedirects -MaxBodyCharacters $MaxBodyCharacters))
}

Write-Output "Diagnóstico secuencial iniciado: $($script:StartedAt.ToString('yyyy-MM-dd HH:mm:ss zzz'))"
Write-Output "Timeout por servicio/redirecciones: $TimeoutSeconds s; máximo de redirecciones: $MaxRedirects; muestra máxima por respuesta: $MaxBodyCharacters caracteres."
Write-Output 'No se envían cookies ni credenciales, no se persisten cuerpos completos y los valores de parámetros no se imprimen.'
foreach ($result in $results) {
    Write-Output ''
    Write-Output "=== $($result.Service) ==="
    Write-Output "URL: $($result.URL)"
    Write-Output "Método: $($result.Method); parámetros: $($result.Parameters)"
    Write-Output "Fecha/hora: $($result.TestedAt)"
    Write-Output "HTTP: $($result.StatusCode); Content-Type: $($result.ContentType); tiempo: $($result.ElapsedMs) ms"
    Write-Output "Redirecciones: $($result.Redirects)"
    Write-Output "Clasificación: $($result.Classification)"
    Write-Output "Tipo/estructura: $($result.ResponseType); $($result.Structure)"
    if ($result.PageTitle) { Write-Output "Título HTML: $($result.PageTitle)" }
    if ($result.Forms) { Write-Output "Formularios (método/acción): $($result.Forms)" }
    if ($result.ResponseType -eq 'HTML' -or $result.TableCount -gt 0) { Write-Output "Tablas: $($result.TableCount); encabezados: $($result.TableHeaders)" }
    if ($result.FieldNames) { Write-Output "Nombres de campos HTML: $($result.FieldNames)" }
    if ($result.Indicators) { Write-Output "Indicadores: $($result.Indicators)" }
    Write-Output "Encabezados relevantes: $($result.RelevantHeaders)"
}
