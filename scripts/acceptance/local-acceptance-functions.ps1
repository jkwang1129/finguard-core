function ConvertFrom-FinguardUtf8Json {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [object]$Content
    )

    if ($Content -is [byte[]] -or $Content -is [System.Array]) {
        $bytes = [byte[]]$Content
        $json = [System.Text.Encoding]::UTF8.GetString($bytes)
    } else {
        $json = [string]$Content
    }
    if ([string]::IsNullOrWhiteSpace($json)) {
        return $null
    }
    return $json | ConvertFrom-Json
}

function New-FinguardCurlUploadConfig {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Token,
        [Parameter(Mandatory = $true)]
        [string]$CsvPath,
        [Parameter(Mandatory = $true)]
        [string]$OutputPath,
        [Parameter(Mandatory = $true)]
        [string]$Uri
    )

    function ConvertTo-CurlConfigValue([string]$Value) {
        return $Value.Replace('\', '\\').Replace('"', '\"')
    }

    $lines = @(
        'silent'
        'show-error'
        ('output = "{0}"' -f (ConvertTo-CurlConfigValue $OutputPath))
        'write-out = "%{http_code}"'
        ('header = "Authorization: Bearer {0}"' -f (
            ConvertTo-CurlConfigValue $Token
        ))
        ('form = "file=@{0};type=text/csv"' -f (
            ConvertTo-CurlConfigValue $CsvPath
        ))
        ('url = "{0}"' -f (ConvertTo-CurlConfigValue $Uri))
    )
    [System.IO.File]::WriteAllLines(
        $Path,
        $lines,
        (New-Object System.Text.UTF8Encoding($false))
    )
    return @('--config', $Path)
}
