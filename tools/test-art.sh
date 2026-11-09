set -e
cd "$(dirname "$0")/.."
rm -rf build-art
mkdir -p build-art/commands
CMDS_WIN='C:\Users\4d3m\Documents\Qoder\2026-10-23\94990867\spotify-mod\build-art\commands'
ART_WIN='C:\Users\4d3m\Documents\Qoder\2026-10-23\94990867\spotify-mod\build-art\art.png'

powershell.exe -NoProfile -ExecutionPolicy Bypass \
	-file src/main/resources/musicdeck/smtc-bridge.ps1 \
	-CommandDir "$CMDS_WIN" -ArtPath "$ART_WIN" > build-art/out.jsonl 2> build-art/err.txt &
BRIDGE=$!

sleep 5
printf 'quit' > build-art/commands/000000000001.cmd
wait $BRIDGE || true

echo "=== states ==="
grep '"t":"state"' build-art/out.jsonl | tail -2
echo "=== art ==="
ls -l build-art/art.png 2>/dev/null || echo "(no art.png)"
echo "=== stderr ==="
head -12 build-art/err.txt
