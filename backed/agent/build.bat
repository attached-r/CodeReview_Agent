@echo off
cd /d D:\ProjectBySelf\CodeReview_Agent\backed\agent
mvn clean compile > D:\build_result.txt 2>&1
echo EXIT CODE: %ERRORLEVEL% >> D:\build_result.txt
