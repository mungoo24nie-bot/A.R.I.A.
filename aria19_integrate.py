"""Integrate ARIA 1.9 changes into repository checkout. Run before Gradle build."""
from pathlib import Path
import re
root=Path(__file__).resolve().parent
main=root/'app/src/main/java/pl/aria/app/MainActivity.java'
s=main.read_text()
if 'A.R.I.A. 1.9' not in s:
    s=s.replace('A.R.I.A. 1.8','A.R.I.A. 1.9').replace('ARIA-pamiec-1.8.json','ARIA-pamiec-1.9.json')
    s=s.replace('neuralBackground.setGrowth(memory.count()+KnowledgeLibrary.count(memory.getReadableDatabase()));',
                'neuralBackground.refreshMemory(memory.getReadableDatabase());')
    s=s.replace('if (isChatVisible && count != displayedCount) showConversation();',
                'if (isChatVisible && count != displayedCount) { showConversation(); neuralBackground.refreshMemory(memory.getReadableDatabase()); }')
    # After any new message, graph needs refresh even if activity is not visible.
    s=s.replace('long eventId = memory.add("user", message, "użytkownik");',
                'long eventId = memory.add("user", message, "użytkownik");\n        neuralBackground.refreshMemory(memory.getReadableDatabase());')
    # Give explicit internet requests priority over local rule acknowledgements and arithmetic.
    s=s.replace('if (factAck != null && !correctionSaved && !diagnosticMode) {',
                'if (factAck != null && !correctionSaved && !diagnosticMode && !AriaInternet.requested(message)) {')
    s=s.replace('if (exact != null && !diagnosticMode) {',
                'if (exact != null && !diagnosticMode && !AriaInternet.requested(message)) {')
    # Keep no-model path explicit: internet retrieval alone cannot produce LLM synthesis.
    s=s.replace('String answer = respondWithoutModel(message);',
                'String answer = AriaInternet.requested(message) ? "Aby przeanalizować wyniki internetu, najpierw wybierz lokalny model GGUF." : respondWithoutModel(message);')
    main.write_text(s)
manifest=root/'app/src/main/AndroidManifest.xml'
s=manifest.read_text()
for perm in ['android.permission.INTERNET','android.permission.WAKE_LOCK']:
    if perm not in s:s=s.replace('    <application ',f'    <uses-permission android:name="{perm}"/>\n    <application ')
manifest.write_text(s)
gradle=root/'app/build.gradle';s=gradle.read_text()
s=s.replace("versionName '1.8'","versionName '1.9'")
s=re.sub(r'versionCode\s+(\d+)',lambda m:'versionCode '+str(max(22,int(m.group(1))+1)),s,count=1)
gradle.write_text(s)
print('ARIA 1.9 integration applied; original user data untouched.')
