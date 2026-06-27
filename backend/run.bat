@echo off
setlocal
if not exist out mkdir out
if exist sources.txt del sources.txt
for /R src %%f in (*.java) do echo %%f>> sources.txt
javac -d out @sources.txt
del sources.txt
if "%PORT%"=="" set PORT=9090
java -cp out com.askdb.Main
