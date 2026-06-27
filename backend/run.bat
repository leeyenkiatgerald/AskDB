@echo off
setlocal
if not exist out mkdir out
where node >nul 2>nul
if %ERRORLEVEL%==0 (
  node ..\scripts\fetch-jdbc.mjs
) else (
  echo [setup] Node.js not found. MockDB will work, but remote JDBC drivers may be missing.
)
if exist sources.txt del sources.txt
for /R src %%f in (*.java) do echo %%f>> sources.txt
javac -cp "lib/*" -d out @sources.txt
del sources.txt
if "%PORT%"=="" set PORT=9090
java -cp "out;lib/*" com.askdb.Main
