$ErrorActionPreference = 'Stop'
$desktop = [Environment]::GetFolderPath('Desktop')
$ws = New-Object -ComObject WScript.Shell
$linkPath = Join-Path $desktop 'OpenClaw 启动.lnk'
$lnk = $ws.CreateShortcut($linkPath)
$lnk.TargetPath = 'F:\project\lenzeto\scripts\start-openclaw.cmd'
$lnk.WorkingDirectory = 'F:\openclawd\openclaw'
$lnk.Arguments = ''
$lnk.Save()
Write-Output ('Shortcut created: ' + $linkPath)
