param([string]$src, [string]$res, [string]$store)
Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile($src)
function Render([int]$size, [double]$artFraction, [string]$out) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.Clear([System.Drawing.Color]::Black)
    $art = [int][math]::Round($size * $artFraction)
    $off = [int](($size - $art) / 2)
    $g.DrawImage($img, $off, $off, $art, $art)
    $g.Dispose()
    New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}
# Adaptive foreground: 108 dp layer, the art 66 dp across: the 66 dp safe circle every launcher mask keeps.
$densities = @{ 'mdpi' = 108; 'hdpi' = 162; 'xhdpi' = 216; 'xxhdpi' = 324; 'xxxhdpi' = 432 }
foreach ($d in $densities.Keys) { Render $densities[$d] (66.0 / 108.0) "$res/mipmap-$d/ic_launcher_foreground.png" }
# Play listing icon: 512 x 512, Play rounds the corners itself.
Render 512 0.92 $store
$img.Dispose()
