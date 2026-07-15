@echo off
cd /d C:\Users\namra\Downloads\NovaOS-merged\novaos\backend
echo ===== COMPILING BACKEND =====
call mvnw.cmd compile -e > compile_output.txt 2>&1
echo Exit code: %ERRORLEVEL% >> compile_output.txt
echo ===== DONE =====
