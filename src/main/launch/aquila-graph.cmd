@echo off
setlocal
if defined JAVA_HOME (
  set "AQUILA_JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "AQUILA_JAVA=java.exe"
)
if defined AQUILA_JAVA_OPTIONS_FILE (
  "%AQUILA_JAVA%" "@%AQUILA_JAVA_OPTIONS_FILE%" --module-path "%~dp0lib" --module org.aquilagraph.cli/org.aquilagraph.AquilaGraphApplication %*
) else (
  "%AQUILA_JAVA%" --module-path "%~dp0lib" --module org.aquilagraph.cli/org.aquilagraph.AquilaGraphApplication %*
)
exit /b %ERRORLEVEL%
