@echo off
setlocal
cd /d "%~dp0"
set USER=jignesh-rapol
set REPO_NAME=FleetPath
set REPO_URL=https://github.com/%USER%/%REPO_NAME%.git

echo ============================================
echo   FleetPath - upload to GitHub and build APK
echo ============================================
echo.

where git >nul 2>nul
if errorlevel 1 (
  echo Git is not installed.
  echo Install it from https://git-scm.com/download/win ^(keep the default options^), then run this file again.
  start https://git-scm.com/download/win
  pause
  exit /b 1
)

if not exist .git (
  git init -b main >nul
)

for /f "delims=" %%i in ('git config user.email') do set GIT_EMAIL=%%i
if "%GIT_EMAIL%"=="" (
  set /p GIT_EMAIL=Enter the email address of your GitHub account: 
  git config user.email "%GIT_EMAIL%"
)
for /f "delims=" %%i in ('git config user.name') do set GIT_NAME=%%i
if "%GIT_NAME%"=="" git config user.name "Jignesh Rapol"

git add -A
git commit -m "FleetPath Android app" >nul 2>nul

where gh >nul 2>nul
if not errorlevel 1 (
  gh auth status >nul 2>nul
  if not errorlevel 1 (
    echo GitHub CLI found - creating the repository %USER%/%REPO_NAME% ...
    gh repo create %REPO_NAME% --private --source=. --remote=origin --push
    if not errorlevel 1 goto done
  )
)

echo.
echo If you haven't yet, create an EMPTY repository named %REPO_NAME% now
echo ^(no README, no .gitignore^). Opening the page...
start https://github.com/new?name=%REPO_NAME%
echo.
pause

git remote remove origin >nul 2>nul
git remote add origin %REPO_URL%
echo Uploading... a GitHub sign-in window may open in your browser.
git push -u origin main
if errorlevel 1 (
  echo.
  echo Upload failed. Check that https://github.com/%USER%/%REPO_NAME% exists and is empty, then run this again.
  pause
  exit /b 1
)

:done
echo.
echo Done! GitHub is now building your APK ^(about 5 minutes^).
echo When the run shows a green tick, open it and download "FleetPath-debug-apk".
start https://github.com/%USER%/%REPO_NAME%/actions
pause
