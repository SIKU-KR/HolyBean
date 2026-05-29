# HolyBean 인쇄서버 Pi 셋업

세우 SLK-TS400B(USB) + Raspberry Pi 3B 기준. 스펙: `docs/superpowers/specs/2026-05-30-runtime-pi-address-discovery-design.md`.

## 토폴로지

```
[공유기] ─이더넷→ [Pi : DHCP IP, :9100 인쇄서버 + avahi(_holybean-print._tcp 광고)]
   └─Wi-Fi→ [태블릿]  ← 같은 서브넷, mDNS로 Pi 자동 발견
```

- Pi는 **유선 업링크 + `:9100` 인쇄서버 + avahi mDNS 광고**만 담당하는 stateless 서버.
- **핫스팟 / NAT / hostapd / dnsmasq / iptables 사용하지 않음.** (Firestore 마이그레이션 이후 NAT 명분 소멸)
- 태블릿은 공유기 Wi-Fi에 직접 붙고, mDNS(`_holybean-print._tcp`)로 Pi 주소를 런타임 해석한다.

## 빌드

Pi는 aarch64(64bit). 둘 중 택1:

- **Pi 네이티브 빌드**(자기완결적, Pi 3B에서 ~7분):
  ```sh
  rustup이 없으면: curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal
  cd pi && ~/.cargo/bin/cargo build --release -j2   # -j2: 1GB RAM OOM 방지
  ```
- **Mac 크로스컴파일**(`cross` + Docker): Apple Silicon에서는 cross-rs 이미지가 amd64 전용이라 까다로움. 네이티브 빌드 권장.

산출물: `pi/target/release/holybean-print-server`

## 배포

```sh
# 1) 바이너리 설치 (서비스 실행 중이면 stop 후 cp — ETXTBSY 방지)
sudo systemctl stop holybean-print-server 2>/dev/null || true
sudo cp target/release/holybean-print-server /usr/local/bin/

# 2) systemd 유닛 (부팅 자동시작 + 무한 재시작)
sudo cp pi/deploy/holybean-print-server.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now holybean-print-server

# 3) avahi mDNS 광고
sudo cp pi/deploy/holybean-print.service /etc/avahi/services/
sudo systemctl restart avahi-daemon

# 4) 프로덕션은 이더넷 only — Wi-Fi 끄기(재부팅에도 유지)
sudo nmcli radio wifi off
```

프린터는 USB 연결 시 `/dev/usb/lp0`(`usblp` 드라이버)로 잡힌다. 서비스는 root로 실행되어 장치에 직접 쓴다.

## 검증

```sh
# 서비스 상태 / 헬스
systemctl is-active holybean-print-server      # active
curl -s http://localhost:9100/health           # {"status":"ok"}

# mDNS 광고 (다른 LAN 기기에서)
dns-sd -B _holybean-print._tcp                  # macOS: 서비스 인스턴스 노출
avahi-browse -rt _holybean-print._tcp           # Linux(avahi-utils)

# 테스트 출력
curl -X POST http://localhost:9100/print -H 'Content-Type: application/json' \
  -d '{"commands":[{"type":"text","content":"HolyBean","align":"center","bold":true},{"type":"cut"}]}'
```

## 운영 메모

- **DHCP 예약 권고:** 코드는 mDNS로 IP 변동을 흡수하지만, 운영 안전망으로 공유기에서 Pi(eth0 MAC)에 고정 IP를 예약해두면 좋다. (코드 의존 아님)
- **`.local` DNS 하이재킹 주의:** 일부 ISP(예: KT)는 `.local` 단일이름 조회를 공인 IP로 가로챈다(`ping pi.local` → 엉뚱한 공인 IP). 안드로이드 NsdManager는 **순수 mDNS 멀티캐스트**라 이 영향을 받지 않는다. 진단 시 `ping`이 아니라 `dns-sd`/`avahi-browse`로 볼 것.
- **멀티캐스트:** 공유기에서 Wi-Fi↔이더넷 간 mDNS 멀티캐스트가 차단되거나 AP isolation이 켜져 있으면 탐색 실패. 이 경우 앱의 수동 주소 override로 탈출.
- **인쇄 칸 폭:** `LINE_WIDTH=42` (80mm/인쇄폭 72mm=512dot, 폰트A). 절단 전 피드 `FEED_BEFORE_CUT_DOTS=255`.
