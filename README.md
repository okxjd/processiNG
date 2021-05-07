# ProcessiNG

### Строка запуска (примеры):
    - java -jar "bin/processing_ng-4.6.jar" split_xlsx_rowcount
    - java -jar "bin/processing_ng-4.6.jar" csv2xlsx "cfg/config2.cfg"

Сначала action, потом файл конфига. Если конфиг не указан, то по умолчанию `"cfg/config.conf"`.

### Доступные action:
- `actionchain`: цепочка действий, через delimiter, все действия должны быть заданы в этом же файле конфига
- `create_dir`: создать каталоги по списку в list, через delimiter
- `delete_dir`: рекурсивно удалить каталоги по списку в list, через delimiter
- `delete_file`: удалить файлы по списку в list, через delimiter
- `join_txt`: склеить TXT файлы (можно обрабатывать очень большие файлы)
- `join_csv`: склеить CSV файлы (можно обрабатывать очень большие файлы)
- `join_xlsx`: склеить XLSX файлы (ограничено памятью)
- `csv2xls`: конвертер CSV -> XLS (ограничено памятью)
- `csv2xlsx`: конвертер CSV -> XLSX
- `xls2xlsx`: конвертер XLS -> XLSX
- `xlsx2xls`: конвертер XLSX -> XLS (ограничено памятью)
- `split_txt`: разделить TXT файлы
- `split_csv`: разделить CSV файлы
- `split_xlsx_rows`: разделить XLSX по количеству строк (можно обрабатывать очень большие файлы)

### Note
Делалось быстро и на коленке - может падать на входных данных или по переполнению кучи.
Возможно, когда-нибудь будет оптимизировано и отлажено как-следует.