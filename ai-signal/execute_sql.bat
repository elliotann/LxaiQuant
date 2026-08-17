@echo off
echo 执行technical_signal表字段添加脚本...
mysql -u root -p < add_missing_columns.sql
echo 脚本执行完成！
pause
