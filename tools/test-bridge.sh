set -e
cd "$(dirname "$0")/.."
CMDS_WIN='C:\Users\4d3m\Documents\Qoder\2026-10-23\94990867\spotify-mod\build-test\commands'
rm -rf build-test
mkdir -p build-test
D="$PWD/build-test/commands"
mkdir -p "$D"

powershell.exe -NoProfile -ExecutionPolicy Bypass \
	-file src/main/resources/musicdeck/smtc-bridge.ps1 \
	-CommandDir "$CMDS_WIN" > build-test/out.jsonl 2> build-test/err.txt &
BRIDGE=$!

sleep 3
put() { printf '%s' "$1" > "$D/$(printf '%012d' $((++i))).cmd"; sleep 1.6; }
i=0
put 'toggle'
put 'toggle'
put 'repeat:Track'
put 'repeat:None'
put 'seek:12'
put 'shuffle:on'
put 'shuffle:off'
put 'quit'
wait $BRIDGE || true

echo "=== ACKS ==="
grep '"t":"ack"' build-test/out.jsonl || echo "(none)"
echo "=== last states ==="
grep '"t":"state"' build-test/out.jsonl | tail -2
echo "=== stderr ==="
head -8 build-test/err.txt
