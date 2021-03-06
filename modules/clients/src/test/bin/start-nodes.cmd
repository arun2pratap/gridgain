@echo OFF

rem Define environment paths.
SET SCRIPT_DIR=%~dp0

SET CONFIG_DIR=%SCRIPT_DIR%\..\resources
SET CLIENTS_MODULE_PATH=%SCRIPT_DIR%\..\..\..
SET BIN_PATH=%SCRIPT_DIR%\..\..\..\..\..\bin

cd %SCRIPT_DIR%\..\..\..\..\..\..
set GG_HOME=%CD%

echo Switch to build script directory %SCRIPT_DIR%
cd %SCRIPT_DIR%

rem Define this script configuration.
set CLIENT_TEST_JAR=%GG_HOME%\gridgain-clients-tests.jar
set NODES_COUNT=2

rem Clean up old jar files.
del /F /Q %GG_HOME%\*.jar

set ANT_TARGET="mk.tests.jar.full"

echo Generate client test jar [ant target=%ANT_TARGET%, jar file=%CLIENT_TEST_JAR%]
echo GG_HOME: %GG_HOME%
echo TEMP: %TEMP%
echo JAVA_HOME: %JAVA_HOME%
echo GIT_PATH: %GIT_PATH%
echo SCRIPT_DIR: %SCRIPT_DIR%
echo call ant -DGG_HOME="%GG_HOME%" -DTMP_DIR="%TEMP%" -DJAVA_HOME="%JAVA_HOME%" -f %SCRIPT_DIR%\..\build\build.xml %ANT_TARGET%

call ant -DGG_HOME="%GG_HOME%" -DTMP_DIR="%TEMP%" -DJAVA_HOME="%JAVA_HOME%" -f %SCRIPT_DIR%\..\build\build.xml %ANT_TARGET%

rem Force to create log's directory.
rmdir %GG_HOME%\work\log /S /Q
mkdir %GG_HOME%\work\log

rem Provide user library to the grid startup scripts.
set USER_LIBS=%CLIENT_TEST_JAR%

set JVM_OPTS=-DCLIENTS_MODULE_PATH=%CLIENTS_MODULE_PATH%

FOR /L %%G IN (1,1,%NODES_COUNT%) DO start "Node #%%G" /low /MIN cmd /C "%BIN_PATH%\ggstart.bat -v %CONFIG_DIR%\spring-server-node.xml >> %GG_HOME%\work\log\node-%%G.log 2>&1"
FOR /L %%G IN (1,1,%NODES_COUNT%) DO start "SSL Node #%%G" /low /MIN cmd /C "%BIN_PATH%\ggstart.bat -v %CONFIG_DIR%\spring-server-ssl-node.xml >> %GG_HOME%\work\log\node-ssl-%%G.log 2>&1"
FOR /L %%G IN (1,1,%NODES_COUNT%) DO start "Cache Node #%%G" /low /MIN cmd /C "%BIN_PATH%\ggstart.bat -v %CONFIG_DIR%\spring-cache.xml >> %GG_HOME%\work\log\cache-node-%%G.log 2>&1"

echo Wait 60 seconds while nodes started.
ping -n 60 127.0.0.1 > NUL

rem Disable hostname verification for self-signed certificates.
set JVM_OPTS=%JVM_OPTS% -DGRIDGAIN_DISABLE_HOSTNAME_VERIFIER=true

FOR /L %%G IN (1,1,1) DO start "Router #%%G" /low /MIN cmd /C "%BIN_PATH%\ggrouter.bat -v %CONFIG_DIR%\spring-router.xml >> %GG_HOME%\work\log\router-%%G.log 2>&1"
FOR /L %%G IN (1,1,1) DO start "SSL Router #%%G" /low /MIN cmd /C "%BIN_PATH%\ggrouter.bat -v %CONFIG_DIR%\spring-router-ssl.xml >> %GG_HOME%\work\log\router-ssl-%%G.log 2>&1"

echo Wait 10 seconds while routers started.
ping -n 10 127.0.0.1 > NUL

echo.
echo Expects all nodes started.

echo ON
