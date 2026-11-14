#requires -Version 7.2
[CmdletBinding()]
param(
    [ValidateRange(1, 60)]
    [int]$TimeoutSeconds = 20,

    [ValidateRange(1024, 262144)]
    [int]$MaxBodyCharacters = 65536
)

$minimumPowerShellVersion = [version]'7.2'
if ($PSVersionTable.PSVersion -lt $minimumPowerShellVersion) {
    [Console]::Error.WriteLine('Este script requiere PowerShell 7.2 o superior.')
    exit 1
}

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

function Read-PrivateValue {
    param(
        [Parameter(Mandatory)][string]$Prompt,
        [Parameter(Mandatory)][string]$Pattern
    )

    $secureValue = Read-Host -Prompt $Prompt -AsSecureString
    $pointer = [IntPtr]::Zero
    $plainValue = $null
    try {
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
        $plainValue = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        if ($plainValue -cnotmatch $Pattern) { return $null }
        return $plainValue
    }
    finally {
        if ($pointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        }
        if ($null -ne $secureValue) { $secureValue.Dispose() }
        # .NET strings are immutable; dropping this reference limits their lifetime in this scope.
        $plainValue = $null
    }
}

function Get-ValueTypeName {
    param($Value)

    if ($null -eq $Value) { return 'null' }
    if ($Value -is [string]) { return 'string' }
    if ($Value -is [bool]) { return 'boolean' }
    if ($Value -is [System.Collections.IDictionary]) { return 'object' }
    if ($Value -is [System.Array]) { return 'array' }
    if ($Value -is [System.ValueType]) { return 'number' }
    return 'value'
}

function Get-SafeName {
    param([Parameter(Mandatory)][string]$Name)

    $sensitiveNamePatterns = @(
        '@',
        '[0-9]{7,}',
        '(?i:[A-Z]{1,4}[- ]?[0-9]{3,4})',
        '(?i)(?<![0-9a-f])[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}(?![0-9a-f])',
        '(?i)(?:^|[-_.])(?:token|access[-_]?token|refresh[-_]?token|session|secret|api[-_]?key|auth|jwt|nonce|state)[-_.:=]?[A-Za-z0-9._~%-]{8,}(?:$|[-_.])',
        '^[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}$'
    )
    foreach ($pattern in $sensitiveNamePatterns) {
        if ($Name -match $pattern) { return '[nombre omitido]' }
    }
    if ($Name.Length -ge 24 -and $Name -match '^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9._~-]+$') { return '[nombre omitido]' }
    $safeName = [System.Text.RegularExpressions.Regex]::Replace($Name, '[\r\n\t]', ' ')
    if ($safeName.Length -gt 80) { $safeName = $safeName.Substring(0, 80) + '…' }
    return $safeName
}

function Get-JsonShape {
    param(
        $Value,
        [int]$Depth = 0,
        [int]$MaxProperties = 40
    )

    if ($Depth -gt 5) { return 'profundidad máxima alcanzada' }
    if ($Value -is [System.Collections.IDictionary]) {
        $names = @($Value.Keys)
        if ($names.Count -eq 0) { return 'object{}' }
        $parts = [System.Collections.Generic.List[string]]::new()
        foreach ($name in ($names | Select-Object -First $MaxProperties)) {
            $safeName = Get-SafeName -Name ([string]$name)
            $item = $Value[$name]
            if ($item -is [System.Collections.IDictionary]) {
                $parts.Add("${safeName}:$(Get-JsonShape -Value $item -Depth ($Depth + 1) -MaxProperties $MaxProperties)")
            }
            elseif ($item -is [System.Array]) {
                $itemShape = "array(count=$($item.Count))"
                if ($item.Count -gt 0) {
                    $itemShape += "; item=$(Get-JsonShape -Value $item[0] -Depth ($Depth + 1) -MaxProperties $MaxProperties)"
                }
                $parts.Add("${safeName}:$itemShape")
            }
            else {
                $parts.Add("${safeName}:$(Get-ValueTypeName -Value $item)")
            }
        }
        if ($names.Count -gt $MaxProperties) { $parts.Add('…') }
        return "object{$($parts -join '; ')}"
    }

    if ($Value -is [System.Array]) {
        $shape = "array(count=$($Value.Count))"
        if ($Value.Count -gt 0) {
            $shape += "; item=$(Get-JsonShape -Value $Value[0] -Depth ($Depth + 1) -MaxProperties $MaxProperties)"
        }
        return $shape
    }
    return Get-ValueTypeName -Value $Value
}

function Get-HtmlAttribute {
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

function Test-SensitivePathSegment {
    param([Parameter(Mandatory)][string]$Segment)

    try {
        $decoded = [System.Uri]::UnescapeDataString($Segment)
    }
    catch {
        return $true
    }

    if ($decoded -match '[\r\n\\/]' -or $decoded -match '@') { return $true }

    $sensitivePatterns = @(
        '(?i)(?<![0-9a-f])[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}(?![0-9a-f])',
        '(?i)(?<![0-9a-f])[0-9a-f]{32}(?![0-9a-f])',
        '(?<![0-9])[0-9]{7,}(?![0-9])',
        '(?i)(?<![A-Z0-9])[A-Z]{1,4}[- ]?[0-9]{3,4}(?![A-Z0-9])',
        '(?i)(?:^|[-_.])(?:token|access[-_]?token|refresh[-_]?token|session|secret|api[-_]?key|auth|jwt|nonce|state)[-_.:=]?[A-Za-z0-9._~%-]{8,}(?:$|[-_.])',
        '^[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}$'
    )
    foreach ($pattern in $sensitivePatterns) {
        if ($decoded -match $pattern) { return $true }
    }

    # Mixed alphanumeric segments and opaque path values are redacted conservatively.
    if ($decoded.Length -ge 12 -and $decoded -match '^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9._~%-]+$') { return $true }
    if ($decoded.Length -ge 16 -and $decoded -match '^[A-Za-z0-9._~%-]+$') { return $true }
    return $false
}

function Get-SafePath {
    param([Parameter(Mandatory)][string]$Path)

    $segments = foreach ($segment in ($Path -split '/')) {
        if ([string]::IsNullOrEmpty($segment)) {
            $segment
        }
        elseif (Test-SensitivePathSegment -Segment $segment) {
            '[segmento omitido]'
        }
        else {
            $segment
        }
    }
    return $segments -join '/'
}

function Get-SafeAction {
    param(
        [Parameter(Mandatory)][System.Uri]$BaseUri,
        [Parameter(Mandatory)][string]$Action
    )

    if ([string]::IsNullOrWhiteSpace($Action)) { return '(misma ruta)' }
    try {
        $uri = [System.Uri]::new($BaseUri, $Action)
        $safePath = Get-SafePath -Path $uri.AbsolutePath
        return "$($uri.Scheme)://$($uri.Authority)$safePath"
    }
    catch {
        return '(acción no interpretable)'
    }
}

function New-EncodedQuery {
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Parameters)

    $queryParts = foreach ($name in $Parameters.Keys) {
        $encodedName = [System.Uri]::EscapeDataString([string]$name)
        $encodedValue = [System.Uri]::EscapeDataString([string]$Parameters[$name])
        "$encodedName=$encodedValue"
    }
    return $queryParts -join '&'
}

function New-AntRequestParameters {
    param(
        [Parameter(Mandatory)][string]$Cedula,
        [AllowEmptyString()][string]$Plate = '',
        [switch]$IncludePlate
    )

    $parameters = [ordered]@{
        ps_tipo_identificacion = 'CED'
        ps_identificacion = $Cedula
    }
    if ($IncludePlate) {
        $parameters.ps_placa = $Plate
    }
    return $parameters
}

function Get-OptionalAntRequestParameters {
    param(
        [Parameter(Mandatory)][string]$Cedula,
        [AllowNull()][AllowEmptyString()][string]$Plate = '',
        [Parameter(Mandatory)][bool]$Authorized
    )

    if (-not $Authorized) { return $null }
    return New-AntRequestParameters -Cedula $Cedula -Plate $Plate -IncludePlate
}

function Get-BodySample {
    param(
        [Parameter(Mandatory)][System.Net.Http.HttpResponseMessage]$Response,
        [Parameter(Mandatory)][System.Threading.CancellationToken]$CancellationToken,
        [Parameter(Mandatory)][int]$MaximumCharacters
    )

    if ($null -eq $Response.Content) {
        return [pscustomobject]@{ Text = ''; Truncated = $false }
    }

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
    return [pscustomobject]@{ Text = $text; Truncated = $truncated }
}

function Get-ResponseAnalysis {
    param(
        [string]$MediaType,
        [string]$Body,
        [bool]$Truncated,
        [System.Uri]$BaseUri,
        [int]$MaxBodyCharacters
    )

    $empty = [string]::IsNullOrWhiteSpace($Body)
    if ($empty) {
        return [pscustomobject]@{
            ResponseType = 'Vacía'
            Structure = 'cuerpo vacío'
            HasBoolean = $false
            BooleanValue = $null
            PageTitle = $false
            Forms = ''
            FieldNames = ''
            TableCount = 0
            TableHeaders = ''
            Indicators = ''
        }
    }

    $trimmed = $Body.Trim()
    $jsonContentType = $MediaType -match '(?i)(^|/|\+)json'
    $looksJson = $jsonContentType -or $trimmed -match '^(\{|\[|true\b|false\b|null\b|"|-?[0-9])'
    if ($looksJson) {
        if ($Truncated) {
            return [pscustomobject]@{
                ResponseType = 'JSON probable, muestra truncada'
                Structure = "estructura no analizada; muestra limitada a $MaxBodyCharacters caracteres"
                HasBoolean = $false
                BooleanValue = $null
                PageTitle = $false
                Forms = ''
                FieldNames = ''
                TableCount = 0
                TableHeaders = ''
                Indicators = ''
            }
        }
        try {
            $parsed = ConvertFrom-Json -InputObject $trimmed -AsHashtable -Depth 100 -ErrorAction Stop
            $hasBoolean = $jsonContentType -and $parsed -is [bool]
            return [pscustomobject]@{
                ResponseType = if ($jsonContentType) { 'JSON' } else { 'JSON aparente' }
                Structure = Get-JsonShape -Value $parsed
                HasBoolean = $hasBoolean
                BooleanValue = if ($hasBoolean) { [bool]$parsed } else { $null }
                PageTitle = $false
                Forms = ''
                FieldNames = ''
                TableCount = 0
                TableHeaders = ''
                Indicators = ''
            }
        }
        catch {
            return [pscustomobject]@{
                ResponseType = 'JSON no analizado'
                Structure = 'cuerpo con aspecto JSON; análisis estructural falló'
                HasBoolean = $false
                BooleanValue = $null
                PageTitle = $false
                Forms = ''
                FieldNames = ''
                TableCount = 0
                TableHeaders = ''
                Indicators = ''
            }
        }
    }

    if ($MediaType -match '(?i)html' -or $trimmed -match '(?is)^<!doctype\s+html|^<html\b') {
        $hasTitle = [System.Text.RegularExpressions.Regex]::IsMatch($Body, '(?is)<title\b[^>]*>')
        $formDetails = [System.Collections.Generic.List[string]]::new()
        foreach ($tag in [System.Text.RegularExpressions.Regex]::Matches($Body, '(?is)<form\b[^>]*>')) {
            $method = Get-HtmlAttribute -Tag $tag.Value -Name 'method'
            if ([string]::IsNullOrWhiteSpace($method)) { $method = 'GET (predeterminado)' }
            elseif ($method -match '^(?i:get|post|put|delete|options|head|patch)$') { $method = $method.ToUpperInvariant() }
            else { $method = '(método no estándar omitido)' }
            $action = Get-HtmlAttribute -Tag $tag.Value -Name 'action'
            $formDetails.Add("$method $(Get-SafeAction -BaseUri $BaseUri -Action $action)")
        }

        $fieldNames = [System.Collections.Generic.List[string]]::new()
        $fieldTags = [System.Text.RegularExpressions.Regex]::Matches($Body, '(?is)<(?:input|select|textarea|button)\b[^>]*>')
        foreach ($tag in $fieldTags) {
            $name = Get-HtmlAttribute -Tag $tag.Value -Name 'name'
            if (-not [string]::IsNullOrWhiteSpace($name)) {
                $safeName = Get-SafeName -Name $name
                if (-not $fieldNames.Contains($safeName)) { $fieldNames.Add($safeName) }
            }
        }

        $tableCount = [System.Text.RegularExpressions.Regex]::Matches($Body, '(?is)<table\b').Count
        $tableHeaders = [System.Collections.Generic.List[string]]::new()
        foreach ($match in [System.Text.RegularExpressions.Regex]::Matches($Body, '(?is)<th\b[^>]*>(.*?)</th>') | Select-Object -First 30) {
            $header = [System.Text.RegularExpressions.Regex]::Replace($match.Groups[1].Value, '<[^>]+>', '')
            $header = [System.Net.WebUtility]::HtmlDecode(([System.Text.RegularExpressions.Regex]::Replace($header, '\s+', ' '))).Trim()
            if ($header) {
                $safeHeader = Get-SafeName -Name $header
                if (-not $tableHeaders.Contains($safeHeader)) { $tableHeaders.Add($safeHeader) }
            }
        }

        $labels = [System.Collections.Generic.List[string]]::new()
        $patterns = @(
            @{ Pattern = '(?i)captcha|recaptcha'; Label = 'indicio de CAPTCHA' },
            @{ Pattern = '(?i)iniciar sesión|iniciar sesion|login|autenticación|autenticacion|contraseña|password'; Label = 'indicio de autenticación' },
            @{ Pattern = '(?i)acceso denegado|access denied|forbidden|no autorizado'; Label = 'indicio de restricción de acceso' },
            @{ Pattern = '(?i)error|parámetro requerido|parametro requerido|campo obligatorio'; Label = 'indicio de error o validación' },
            @{ Pattern = '(?i)puntos.{0,30}licencia|licencia.{0,30}puntos'; Label = 'referencia a puntos de licencia' },
            @{ Pattern = '(?i)\bpuntos\b'; Label = 'referencia a puntos' }
        )
        foreach ($entry in $patterns) {
            if ($Body -match $entry.Pattern) { $labels.Add($entry.Label) }
        }

        $structure = "HTML; formularios=$($formDetails.Count); tablas=$tableCount"
        if ($fieldNames.Count -gt 0) { $structure += "; campos=$($fieldNames -join ', ')" }
        if ($tableHeaders.Count -gt 0) { $structure += "; encabezados=$($tableHeaders -join ' | ')" }
        if ($Truncated) { $structure += "; muestra truncada a $MaxBodyCharacters caracteres" }
        return [pscustomobject]@{
            ResponseType = 'HTML'
            Structure = $structure
            HasBoolean = $false
            BooleanValue = $null
            PageTitle = $hasTitle
            Forms = $formDetails -join '; '
            FieldNames = $fieldNames -join ', '
            TableCount = $tableCount
            TableHeaders = $tableHeaders -join ' | '
            Indicators = $labels -join '; '
        }
    }

    $description = if ($MediaType -like 'text/*') { "texto ($MediaType); contenido omitido" } else { 'contenido no JSON/HTML; valor omitido' }
    if ($Truncated) { $description += "; muestra truncada a $MaxBodyCharacters caracteres" }
    return [pscustomobject]@{
        ResponseType = if ($MediaType -like 'text/*') { 'Texto' } else { 'Otro' }
        Structure = $description
        HasBoolean = $false
        BooleanValue = $null
        PageTitle = $false
        Forms = ''
        FieldNames = ''
        TableCount = 0
        TableHeaders = ''
        Indicators = ''
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
    return 'Error de conexión sin respuesta HTTP'
}

function Get-SafeUriPath {
    param([Parameter(Mandatory)][System.Uri]$Uri)

    $safePath = Get-SafePath -Path $Uri.AbsolutePath
    return "$($Uri.Scheme)://$($Uri.Authority)$safePath"
}

function Get-HttpStatusMeaning {
    param($StatusCode)

    if ($null -eq $StatusCode) { return 'sin respuesta HTTP' }
    if ($StatusCode -eq 204) { return 'respuesta sin contenido; caso exitoso no confirmado' }
    if ($StatusCode -ge 200 -and $StatusCode -lt 300) { return 'respuesta HTTP satisfactoria; validar su contenido y semántica' }
    switch ($StatusCode) {
        400 { return 'petición rechazada o inválida' }
        401 { return 'autenticación requerida' }
        403 { return 'acceso restringido' }
        404 { return 'ruta o recurso no encontrado; no implica por sí solo que el proveedor esté roto' }
        429 { return 'límite de solicitudes; revisar Retry-After' }
        500 { return 'error del servidor' }
        502 { return 'gateway/proxy sin respuesta válida del upstream' }
        503 { return 'servicio no disponible' }
        504 { return 'timeout de gateway' }
        default { return "respuesta HTTP $StatusCode" }
    }
}

function Invoke-EndpointGet {
    param(
        [Parameter(Mandatory)][string]$Service,
        [Parameter(Mandatory)][string]$BaseUrl,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Parameters,
        [Parameter(Mandatory)][int]$TimeoutSeconds,
        [Parameter(Mandatory)][int]$MaxBodyCharacters
    )

    $start = [DateTimeOffset]::Now
    $parameterNames = @($Parameters.Keys) -join ', '
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.UseCookies = $false
    $handler.UseDefaultCredentials = $false
    $handler.AutomaticDecompression = [System.Net.DecompressionMethods]::GZip -bor [System.Net.DecompressionMethods]::Deflate
    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.Timeout = [System.Threading.Timeout]::InfiniteTimeSpan
    $cancellation = [System.Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($TimeoutSeconds))
    $request = $null
    $response = $null
    $requestUri = $null
    $currentUri = $null
    $query = $null
    $queryParts = $null
    $bodySample = $null
    $analysis = $null
    $phase = 'conexión'
    $statusCode = $null
    $contentType = ''
    $failure = ''
    $redirectNote = ''
    $redirects = [System.Collections.Generic.List[string]]::new()
    $cookiesIssued = $false
    $authHeaderSeen = $false
    $retryAfter = ''
    $cacheControl = ''

    try {
        $query = New-EncodedQuery -Parameters $Parameters
        $requestUri = [System.Uri]::new("${BaseUrl}?${query}")
        $currentUri = $requestUri
        for ($hop = 0; $hop -le 5; $hop++) {
            $phase = 'conexión'
            $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, $currentUri)
            $request.Headers.Accept.ParseAdd('application/json')
            $request.Headers.Accept.ParseAdd('text/html')
            $request.Headers.Accept.ParseAdd('*/*')
            $request.Headers.UserAgent.ParseAdd('Consultas-SRI-ANT-Private-Diagnostic/1.0')

            $response = $client.SendAsync(
                $request,
                [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead,
                $cancellation.Token).GetAwaiter().GetResult()
            $statusCode = [int]$response.StatusCode
            $contentType = if ($response.Content.Headers.ContentType) { $response.Content.Headers.ContentType.ToString() } else { '' }
            if ($response.Headers.Contains('Set-Cookie')) { $cookiesIssued = $true }
            if ($response.Headers.Contains('WWW-Authenticate')) { $authHeaderSeen = $true }
            if ($response.Headers.CacheControl) { $cacheControl = $response.Headers.CacheControl.ToString() }
            if ($response.Headers.RetryAfter) { $retryAfter = $response.Headers.RetryAfter.ToString() }

            if ($statusCode -notin @(300, 301, 302, 303, 307, 308)) { break }
            $location = $response.Headers.Location
            if ($null -eq $location) {
                $redirectNote = 'redirección sin Location'
                break
            }
            $nextUri = if ($location.IsAbsoluteUri) { $location } else { [System.Uri]::new($currentUri, $location) }
            $redirects.Add("HTTP $statusCode -> $(Get-SafeUriPath -Uri $nextUri)")
            if ($nextUri.Scheme -ne 'https' -or $nextUri.Host -ne $requestUri.Host) {
                $redirectNote = 'no seguida: cambiaría HTTPS o el host de destino'
                break
            }
            if ($redirects.Count -ge 5) {
                $redirectNote = 'límite de cinco redirecciones alcanzado'
                break
            }

            $response.Dispose()
            $request.Dispose()
            $response = $null
            $request = $null
            $currentUri = $nextUri
        }

        if ($null -ne $response) {
            $phase = 'lectura o análisis de respuesta'
            $bodySample = Get-BodySample -Response $response -CancellationToken $cancellation.Token -MaximumCharacters $MaxBodyCharacters
            $mediaType = if ($response.Content.Headers.ContentType) { $response.Content.Headers.ContentType.MediaType } else { '' }
            $analysis = Get-ResponseAnalysis -MediaType $mediaType -Body $bodySample.Text -Truncated $bodySample.Truncated -BaseUri $currentUri -MaxBodyCharacters $MaxBodyCharacters
        }
    }
    catch {
        if ($cancellation.IsCancellationRequested) {
            $failure = "Timeout durante $phase"
        }
        elseif ($phase -eq 'lectura o análisis de respuesta') {
            $failure = 'Error leyendo o analizando respuesta'
        }
        else {
            $failure = Get-ConnectionFailureCategory -Exception $_.Exception
        }
    }
    finally {
        $timer.Stop()
        if ($null -ne $response) { $response.Dispose() }
        if ($null -ne $request) { $request.Dispose() }
        $cancellation.Dispose()
        $client.Dispose()
        $handler.Dispose()
        $request = $null
        $response = $null
        $requestUri = $null
        $currentUri = $null
        $query = $null
        $queryParts = $null
        $encodedName = $null
        $encodedValue = $null
        foreach ($key in @($Parameters.Keys)) { $Parameters[$key] = $null }
        $Parameters = $null
        if ($null -ne $bodySample) { $bodySample.Text = $null }
        $bodySample = $null
    }

    $redirectSummary = if ($redirects.Count -gt 0) { $redirects -join '; ' } else { 'ninguna' }
    if ($redirectNote) { $redirectSummary += "; $redirectNote" }
    $headers = [System.Collections.Generic.List[string]]::new()
    if ($cookiesIssued) { $headers.Add('Set-Cookie presente (valor omitido)') }
    if ($authHeaderSeen) { $headers.Add('WWW-Authenticate presente') }
    if ($cacheControl) { $headers.Add("Cache-Control=$cacheControl") }
    if ($retryAfter) { $headers.Add("Retry-After=$retryAfter") }

    return [pscustomobject]@{
        Service = $Service
        Url = $BaseUrl
        ParameterName = $parameterNames
        TestedAt = $start.ToString('yyyy-MM-dd HH:mm:ss zzz')
        StatusCode = $statusCode
        StatusMeaning = Get-HttpStatusMeaning -StatusCode $statusCode
        ContentType = $contentType
        ElapsedMs = [math]::Round($timer.Elapsed.TotalMilliseconds)
        Redirects = $redirectSummary
        Failure = $failure
        ResponseType = if ($analysis) { $analysis.ResponseType } else { 'No analizada' }
        Structure = if ($analysis) { $analysis.Structure } else { 'no se obtuvo estructura' }
        HasBoolean = if ($analysis) { $analysis.HasBoolean } else { $false }
        BooleanValue = if ($analysis) { $analysis.BooleanValue } else { $null }
        PageTitle = if ($analysis) { $analysis.PageTitle } else { $false }
        Forms = if ($analysis) { $analysis.Forms } else { '' }
        FieldNames = if ($analysis) { $analysis.FieldNames } else { '' }
        TableCount = if ($analysis) { $analysis.TableCount } else { 0 }
        TableHeaders = if ($analysis) { $analysis.TableHeaders } else { '' }
        Indicators = if ($analysis) { $analysis.Indicators } else { '' }
        RelevantHeaders = if ($headers.Count -gt 0) { $headers -join '; ' } else { 'ninguno observado' }
    }
}

function Show-DiagnosticResult {
    param([Parameter(Mandatory)]$Result)

    Write-Host "`n=== $($Result.Service) ==="
    Write-Host "URL base: $($Result.Url)"
    Write-Host "Parámetro: $($Result.ParameterName) (valor omitido)"
    Write-Host "Fecha/hora: $($Result.TestedAt)"
    Write-Host "HTTP: $($Result.StatusCode); Content-Type: $($Result.ContentType); tiempo: $($Result.ElapsedMs) ms"
    Write-Host "Interpretación del estado: $($Result.StatusMeaning)"
    Write-Host "Redirecciones: $($Result.Redirects)"
    Write-Host "Tipo/estructura: $($Result.ResponseType); $($Result.Structure)"
    if ($Result.HasBoolean) { Write-Host "Booleano JSON: $($Result.BooleanValue)" }
    if ($Result.ResponseType -eq 'HTML') {
        $titleSummary = if ($Result.PageTitle) { 'presente; texto omitido' } else { 'no observado' }
        Write-Host "Título HTML: $titleSummary"
        Write-Host "Formularios: $(if ($Result.Forms) { $Result.Forms } else { 'ninguno observado' })"
        Write-Host "Nombres de campos: $(if ($Result.FieldNames) { $Result.FieldNames } else { 'ninguno observado' })"
        Write-Host "Tablas: $($Result.TableCount); encabezados: $(if ($Result.TableHeaders) { $Result.TableHeaders } else { 'ninguno observado' })"
    }
    Write-Host "Indicadores: $(if ($Result.Indicators) { $Result.Indicators } else { 'ninguno observado' })"
    Write-Host "Encabezados relevantes: $($Result.RelevantHeaders)"
    if ($Result.Failure) { Write-Host "Resultado técnico: $($Result.Failure)" }
}

function Invoke-AntRequestWithSingleRetry {
    param(
        [Parameter(Mandatory)][System.Collections.IDictionary]$Parameters,
        [Parameter(Mandatory)][int]$TimeoutSeconds,
        [Parameter(Mandatory)][int]$MaxBodyCharacters
    )

    $platePresent = $Parameters.Contains('ps_placa') -and -not [string]::IsNullOrEmpty([string]$Parameters['ps_placa'])
    $value = "ps_tipo_identificacion=CED; ps_identificacion=presente; ps_placa=$(if ($platePresent) { 'presente' } elseif ($Parameters.Contains('ps_placa')) { 'vacío' } else { 'omitido' })"
    Write-Host "`nANT — GET; $value (valores omitidos)"
    $baseUrl = 'https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp'
    $baseRequest = $null
    try {
        $baseRequest = Invoke-AntGet -BaseUrl $baseUrl -Parameters $Parameters -TimeoutSeconds $TimeoutSeconds -MaxBodyCharacters $MaxBodyCharacters
        Show-DiagnosticResult -Result $baseRequest
        if ($baseRequest.StatusCode -in @(502, 503, 504)) {
            Write-Host 'Gateway no disponible; se esperarán 10 segundos antes del único reintento permitido.'
            Start-Sleep -Seconds 10
            $retryRequest = Invoke-AntGet -BaseUrl $baseUrl -Parameters $Parameters -TimeoutSeconds $TimeoutSeconds -MaxBodyCharacters $MaxBodyCharacters
            Show-DiagnosticResult -Result $retryRequest
            return $retryRequest
        }
        return $baseRequest
    }
    finally {
        $baseRequest = $null
        $Parameters = $null
        $value = $null
        $platePresent = $null
    }
}

function Invoke-AntGet {
    param(
        [Parameter(Mandatory)][string]$BaseUrl,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Parameters,
        [Parameter(Mandatory)][int]$TimeoutSeconds,
        [Parameter(Mandatory)][int]$MaxBodyCharacters
    )

    $result = $null
    try {
        $result = Invoke-EndpointGet -Service 'ANT — consulta web' -BaseUrl $BaseUrl -Parameters $Parameters -TimeoutSeconds $TimeoutSeconds -MaxBodyCharacters $MaxBodyCharacters
        return $result
    }
    finally {
        $Parameters = $null
        $result = $null
    }
}

function Invoke-PrivateDiagnostics {
    $ruc = $null
    $plate = $null
    $cedula = $null
    $usePlateForAnt = $false
    $existenceResult = $null
    $antBlankResult = $null
    $antBlankParameters = $null
    $antPlateParameters = $null
    try {
        Write-Host 'Los valores se pedirán solo en esta consola mediante SecureString. No se imprimirán ni se guardarán en archivos.'
        Write-Host 'Solo se enviarán al servicio correspondiente si el formato estructural es correcto.'

        $ruc = Read-PrivateValue -Prompt 'RUC propio o autorizado (13 dígitos ASCII)' -Pattern '^[0-9]{13}$'
        if ($null -eq $ruc) {
            Write-Host 'Formato de RUC no válido; no se envió ninguna solicitud.'
            return
        }

        $existenceUrl = 'https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/existePorNumeroRuc'
        $existenceResult = Invoke-EndpointGet -Service 'SRI — existencia del RUC' -BaseUrl $existenceUrl -Parameters ([ordered]@{ numeroRuc = $ruc }) -TimeoutSeconds $TimeoutSeconds -MaxBodyCharacters $MaxBodyCharacters
        Show-DiagnosticResult -Result $existenceResult

        if ($existenceResult.StatusCode -eq 200 -and $existenceResult.HasBoolean -and $existenceResult.BooleanValue) {
            $taxpayerUrl = 'https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/obtenerPorNumerosRuc'
            $taxpayerResult = Invoke-EndpointGet -Service 'SRI — información del contribuyente' -BaseUrl $taxpayerUrl -Parameters ([ordered]@{ ruc = $ruc }) -TimeoutSeconds $TimeoutSeconds -MaxBodyCharacters $MaxBodyCharacters
            Show-DiagnosticResult -Result $taxpayerResult
            $taxpayerResult = $null
        }
        elseif ($existenceResult.StatusCode -eq 200 -and $existenceResult.HasBoolean -and -not $existenceResult.BooleanValue) {
            Write-Host 'El SRI devolvió false; se omite la consulta de información del contribuyente.'
        }
        else {
            Write-Host 'La existencia no quedó confirmada como true; se omite la consulta de información del contribuyente.'
        }
        $existenceResult = $null
        $ruc = $null

        $plate = Read-PrivateValue -Prompt 'Placa propia o autorizada (letras y números ASCII, sin separadores)' -Pattern '^[A-Za-z0-9]+$'
        if ($null -eq $plate) {
            Write-Host 'Formato de placa no válido; se omite la consulta de vehículo.'
        }
        else {
            $vehicleUrl = 'https://srienlinea.sri.gob.ec/sri-matriculacion-vehicular-recaudacion-servicio-internet/rest/BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn'
            $vehicleResult = Invoke-EndpointGet -Service 'SRI — información del vehículo' -BaseUrl $vehicleUrl -Parameters ([ordered]@{ numeroPlacaCampvCpn = $plate }) -TimeoutSeconds $TimeoutSeconds -MaxBodyCharacters $MaxBodyCharacters
            Show-DiagnosticResult -Result $vehicleResult
            $vehicleResult = $null

            $consent = Read-Host '¿Autoriza enviar también esta placa a ANT como ps_placa en una segunda consulta? Escriba S para autorizar o N para omitir'
            $usePlateForAnt = $consent -match '^(?i:s|si|sí)$'
            $consent = $null
            if (-not $usePlateForAnt) { $plate = $null }
        }

        $cedula = Read-PrivateValue -Prompt 'Cédula propia o autorizada para ANT (10 dígitos ASCII; no se deriva del RUC)' -Pattern '^[0-9]{10}$'
        if ($null -eq $cedula) {
            Write-Host 'Formato de cédula no válido; no se envió ninguna solicitud a ANT.'
            return
        }

        $antBlankParameters = New-AntRequestParameters -Cedula $cedula -Plate '' -IncludePlate
        $antBlankResult = Invoke-AntRequestWithSingleRetry -Parameters $antBlankParameters -TimeoutSeconds $TimeoutSeconds -MaxBodyCharacters $MaxBodyCharacters
        $antBlankParameters = $null

        $antPlateParameters = Get-OptionalAntRequestParameters -Cedula $cedula -Plate $plate -Authorized $usePlateForAnt
        if ($null -ne $antPlateParameters) {
            Write-Host 'Se ejecutará la consulta ANT opcional con la placa autorizada; su reintento se decide según su propia respuesta.'
            $antPlateResult = Invoke-AntRequestWithSingleRetry -Parameters $antPlateParameters -TimeoutSeconds $TimeoutSeconds -MaxBodyCharacters $MaxBodyCharacters
            $antPlateResult = $null
            $antPlateParameters = $null
            $plate = $null
        }
        elseif ($null -ne $plate) {
            Write-Host 'No se autorizó el envío de la placa a ANT; solo se probó ps_placa vacío.'
            $plate = $null
        }
    }
    finally {
        $ruc = $null
        $plate = $null
        $cedula = $null
        $existenceResult = $null
        $antBlankResult = $null
        $antBlankParameters = $null
        $antPlateParameters = $null
    }
}

try {
    Invoke-PrivateDiagnostics
}
catch {
    Write-Host 'Diagnóstico interrumpido por un error local; se omite el detalle para proteger los identificadores.'
}
