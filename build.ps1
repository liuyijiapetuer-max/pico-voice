param(
  [string]$Root = 'G:\minimaxworkspace\pico-sonosphere',
  [switch]$Pass1
)
$ErrorActionPreference = 'Stop'

$parts = @(
  '00-head.html',
  '01-concept.html',
  '02-competitor.html',
  '03-mechanism.html',
  '04-audio-engine.html',
  '05-scene.html',
  '06-core-materials.html',
  '07-plan.html',
  '08-execution.html',
  '09-appendix.html',
  '99-tail.html'
)

$sb = New-Object System.Text.StringBuilder
foreach ($p in $parts) {
  $f = Join-Path $Root ("parts\" + $p)
  if (-not (Test-Path $f)) { throw "missing part: $f" }
  $txt = [System.IO.File]::ReadAllText($f, [System.Text.Encoding]::UTF8)
  [void]$sb.AppendLine($txt)
}
$html = $sb.ToString()

# 1) 大表格（≥7 行）允许跨页，<thead> 会自动重复表头。
$html = [regex]::Replace($html, '(?s)<table class="data avoid-break">(?<b>.*?)</table>', {
  param($m)
  $rows = ([regex]::Matches($m.Groups['b'].Value, '<tr')).Count
  if ($rows -ge 7) { return '<table class="data">' + $m.Groups['b'].Value + '</table>' }
  return $m.Value
})
Write-Output ("SPLITTABLE_TABLES=" + ([regex]::Matches($html, '<table class="data">')).Count)

# 2) 章节不再强制分页：正文自然流动，避免每章末尾留下半空页。
#    目录页之后仍强制换页（目录要有独立一页）。
$html = [regex]::Replace($html, '<section class="content page-break"(?! id="toc")', '<section class="content"')
Write-Output ("FORCED_BREAKS=" + ([regex]::Matches($html, 'class="content page-break"')).Count)

# 3) 章节头（眉标 + 标题 + 规则线）包成不可分割块
$html = [regex]::Replace($html, '(?s)(<span class="section-num">.*?<div class="section-line"></div>)', '<div class="sec-head">$1</div>')
Write-Output ("SEC_HEADS=" + ([regex]::Matches($html, 'class="sec-head"')).Count)

$enc = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $Root 'page.html'), $html, $enc)
Write-Output ("BUILD_OK parts=" + $parts.Count + " bytes=" + $html.Length)

$ph = ([regex]::Matches($html, '\{\{P:[^}]+\}\}')).Count
Write-Output ("PAGE_PLACEHOLDERS=" + $ph)

$bad = @()
if ($html -match "\uFFFD") { $bad += 'U+FFFD replacement char' }
if ($html -match '&amp;amp') { $bad += 'entity artifact' }
if ($html -match '8\.5in\s+11in') { $bad += 'Letter size in CSS' }
if ($html -match '100vh') { $bad += '100vh used' }
$bad += ([regex]::Matches($html, '<!-- PLACEHOLDER')).Count
Write-Output ("ISSUES=" + ($bad -join '; '))
