param([switch]$Final)
$ErrorActionPreference = 'Stop'
$root = 'G:\minimaxworkspace\pico-sonosphere'
$py = 'E:\software\Dev\python\python.exe'
$node = 'C:\Program Files\nodejs\node.exe'
$renderer = 'F:\minimaxData\.minimax\.builtin-skills\pdf\scripts\render_html.cjs'
$pdf = "$root\声之建筑-落地方案.pdf"

Write-Host '=== build ==='
& "$root\build.ps1"

if ($Final) {
  Write-Host '=== render pass1 ==='
  & $node $renderer --in "$root\page.html" --out $pdf --format A4 --wait 15000 --header "$root\tpl-header.html" --footer "$root\tpl-footer.html" | Select-Object -Last 1
  Write-Host '=== resolve toc ==='
  & $py "$root\resolve_toc.py" resolve | Select-Object -Last 3
}

Write-Host '=== render final ==='
& $node $renderer --in "$root\page.html" --out $pdf --format A4 --wait 15000 --header "$root\tpl-header.html" --footer "$root\tpl-footer.html" | Select-Object -Last 1

if ($Final) {
  Write-Host '=== outline ==='
  & $py "$root\resolve_toc.py" outline | Select-Object -Last 1
  Write-Host '=== verify ==='
  & $py "$root\verify_pdf.py" | Select-Object -Last 8
}

Write-Host '=== rasterize ==='
& $node "$root\pdfshot.cjs" | Select-Object -Last 1
Write-Host '=== fill check ==='
& $py "$root\fillcheck.py" | Select-Object -Last 4
