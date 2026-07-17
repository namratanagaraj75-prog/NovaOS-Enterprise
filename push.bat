@echo off
echo Committing and pushing all NovaOS changes...
git add -A
git commit -m "feat: complete Gmail SMTP, timezone formatting, and startup diagnostics"
git push
echo Done!
pause
