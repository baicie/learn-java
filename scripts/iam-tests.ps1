$env:JAVA_HOME = "D:\install\jdk"
Set-Location "d:/workspace/git-code/ai-ops"
& mvn -pl modules/aiops-platform test 2>&1 | Select-String -Pattern '^\[ERROR\]|Tests run:|BUILD SUCCESS|BUILD FAILURE' | Select-Object -Last 50