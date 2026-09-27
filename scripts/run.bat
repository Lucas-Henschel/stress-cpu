@echo off
chcp 65001 >nul
set "DIR=%~dp0.."

if not exist "%DIR%\build\CMakeCache.txt" (
    cmake -B "%DIR%\build" -S "%DIR%"
    if errorlevel 1 exit /b 1
)

cmake --build "%DIR%\build" --config Release > "%DIR%\build\last-build.log" 2>&1
if errorlevel 1 (
    type "%DIR%\build\last-build.log"
    echo.
    echo Falha na compilacao - veja as mensagens acima.
    exit /b 1
)

java -Dstdout.encoding=UTF-8 -cp "%DIR%\build\classes" -Djava.library.path="%DIR%\build\lib" br.furb.so.stress.Main %*
