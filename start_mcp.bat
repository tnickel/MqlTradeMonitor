@echo off
set "DIR=%~dp0mcp-server"
cd /d "%DIR%"
call "node_modules\.bin\ts-node.cmd" src\index.ts 2> mcp_error.log
