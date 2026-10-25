# Play feature graphic, 1024 x 500: the icon artwork on the right, the name, tagline and privacy line on the left
# in the app's typeface (JetBrains Mono) and colours (Console theme: hi #E4E1DA, dim #8D8A84, copper #D08A55).
param([string]$root = (Resolve-Path "$PSScriptRoot/../.."))
Add-Type -AssemblyName System.Drawing
$fonts = New-Object System.Drawing.Text.PrivateFontCollection
$fonts.AddFontFile("$root/app/src/main/res/font/jetbrains_mono_extrabold.ttf")
$fonts.AddFontFile("$root/app/src/main/res/font/jetbrains_mono_regular.ttf")
$family = { param($n) $fonts.Families | Where-Object { $_.Name -eq $n } | Select-Object -First 1 }
$bold = & $family 'JetBrains Mono ExtraBold'; if (-not $bold) { $bold = $fonts.Families[0] }
$reg = & $family 'JetBrains Mono'; if (-not $reg) { $reg = $fonts.Families[-1] }

$bmp = New-Object System.Drawing.Bitmap 1024, 500, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.Clear([System.Drawing.Color]::Black)

$art = [System.Drawing.Image]::FromFile("$root/art/apophenia-icon-source.png")
$g.DrawImage($art, 544, 20, 460, 460)
$art.Dispose()

$hi = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(0xE4, 0xE1, 0xDA))
$dim = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(0x8D, 0x8A, 0x84))
$copper = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(0xD0, 0x8A, 0x55))
$px = [System.Drawing.GraphicsUnit]::Pixel
$g.DrawString("Apophenia", (New-Object System.Drawing.Font($bold, 66, [System.Drawing.FontStyle]::Regular, $px)), $hi, 52, 150)
$g.DrawString("A 21st century`nmusician's notebook", (New-Object System.Drawing.Font($reg, 28, [System.Drawing.FontStyle]::Regular, $px)), $dim, 56, 240)
$g.FillRectangle($copper, 58, 340, 64, 3)
$dot = [string][char]0x00B7   # the script stays ASCII: PowerShell 5.1 reads it as ANSI
$g.DrawString("OFFLINE $dot NO ACCOUNT $dot NO ADS", (New-Object System.Drawing.Font($reg, 16, [System.Drawing.FontStyle]::Regular, $px)), $copper, 56, 356)
$g.Dispose()
$out = "$root/fastlane/metadata/android/en-US/images/featureGraphic.png"
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
"wrote $out"
