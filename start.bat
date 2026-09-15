@echo off
setlocal

set "ROOT=%~dp0"

if exist "%ROOT%.env" (
  for /f "usebackq tokens=1,* delims==" %%A in (`findstr /r "^[A-Za-z_][A-Za-z0-9_]*=" "%ROOT%.env"`) do (
    set "%%A=%%B"
  )
)

if not defined BACKEND_PORT set "BACKEND_PORT=8080"
if not defined FRONTEND_PORT set "FRONTEND_PORT=5173"
if not defined APP_CORS_ALLOWED_ORIGIN set "APP_CORS_ALLOWED_ORIGIN=http://localhost:%FRONTEND_PORT%"
if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=local"

echo Starting Textile Management locally...
echo Backend:  http://localhost:%BACKEND_PORT%
echo Frontend: http://localhost:%FRONTEND_PORT%
echo.
echo No default account is created. Sign up locally or provide explicit seed credentials.
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found on PATH.
  echo Install JDK 17 or newer, then set JAVA_HOME and add Java to PATH.
  echo Backend cannot start until Java is available.
  echo.
  pause
  exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
  echo npm was not found on PATH.
  echo npm comes with Node.js. Install Node.js LTS from https://nodejs.org/
  echo Do not run "pip install npm"; npm is not installed through Python.
  echo.
  pause
  exit /b 1
)

if not exist "%ROOT%project\node_modules" (
  echo Frontend dependencies are missing. Installing with npm install...
  pushd "%ROOT%project"
  call npm install
  if errorlevel 1 (
    echo npm install failed.
    pause
    exit /b 1
  )
  popd
)

start "Textile Backend" /D "%ROOT%TextileManagement" cmd /k "mvnw.cmd spring-boot:run"
start "Textile Frontend" /D "%ROOT%project" cmd /k "npm run dev -- --host 0.0.0.0 --port %FRONTEND_PORT%"

echo Backend and frontend windows have been opened.
echo Keep both windows running while testing.

endlocal
