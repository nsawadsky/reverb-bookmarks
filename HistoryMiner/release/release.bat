xcopy /e /i /y HistoryAnalyzer HistoryAnalyzer_64
xcopy /e /i /y HistoryAnalyzer HistoryAnalyzer_Linux
xcopy /e /i /y HistoryAnalyzer HistoryAnalyzer_Linux_64
xcopy /e /i /y HistoryAnalyzer HistoryAnalyzer_Mac
xcopy /e /i /y HistoryAnalyzer HistoryAnalyzer_Mac_64

copy /y ..\lib\windows-x86\swt.jar HistoryAnalyzer\HistoryAnalyzer_lib\swt.jar
copy /y ..\lib\windows-x64\swt.jar HistoryAnalyzer_64\HistoryAnalyzer_lib\swt.jar
copy /y ..\lib\linux-x86\swt.jar HistoryAnalyzer_Linux\HistoryAnalyzer_lib\swt.jar
copy /y ..\lib\linux-x64\swt.jar HistoryAnalyzer_Linux_64\HistoryAnalyzer_lib\swt.jar
copy /y ..\lib\mac-x86\swt.jar HistoryAnalyzer_Mac\HistoryAnalyzer_lib\swt.jar
copy /y ..\lib\mac-x64\swt.jar HistoryAnalyzer_Mac_64\HistoryAnalyzer_lib\swt.jar

copy /y mac\start-history-analyzer HistoryAnalyzer_Mac
copy /y mac\start-history-analyzer HistoryAnalyzer_Mac_64

copy /y linux\start-history-analyzer HistoryAnalyzer_Linux
copy /y linux\start-history-analyzer HistoryAnalyzer_Linux_64



