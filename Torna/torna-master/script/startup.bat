@echo off

set app_name=torna

java -server -Duser.timezone=Asia/Shanghai -jar -Xms512m -Xmx512m %app_name%.jar