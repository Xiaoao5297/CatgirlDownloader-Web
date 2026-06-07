@echo off
cd /d "%~dp0"

echo Catgirl Downloader Web
echo.

REM Check Python
where python3 >nul 2>nul
if %errorlevel% neq 0 (
    where python >nul 2>nul
    if %errorlevel% neq 0 (
        echo  E Python is not installed.
        echo  * Download from https://www.python.org/downloads/
        pause
        exit /b 1
    )
    set PYTHON=python
) else (
    set PYTHON=python3
)

echo  ✓ %PYTHON% found

REM Check / install dependencies
%PYTHON% -c "import flask" 2>nul
if %errorlevel% neq 0 (
    echo ^→ Installing Python dependencies...
    %PYTHON% -m pip install -r requirements.txt --quiet
    echo ✓ Dependencies installed
) else (
    echo ✓ Dependencies satisfied
)

echo.
echo  * Starting server...
echo  * Open http://localhost:5000 in your browser
echo.

%PYTHON% server.py
pause
