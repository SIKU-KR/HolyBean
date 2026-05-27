# DynamoDB → Firestore 마이그레이션

구버전 DynamoDB(`holybean`, `holybean-menu`)의 주문/메뉴 데이터를
Firestore(`holybean-e4201`)로 일회성 이관한다.

## 준비

1. 가상환경 + 의존성:
   ```
   python3 -m venv .venv
   source .venv/bin/activate
   pip install -r requirements.txt
   ```
2. AWS: CLI 로그인 상태 사용(region `ap-northeast-2`).
3. Firebase 인증 — 둘 중 하나:
   - **gcloud ADC(권장):** `gcloud auth application-default login` 후 별도 인자 없이 실행.
     실행 계정이 `holybean-e4201` Firestore 쓰기 권한을 가져야 한다.
   - **service account JSON:** 콘솔 > 프로젝트 설정 > 서비스 계정 > 새 비공개 키 생성으로
     JSON을 받아 이 디렉터리에 두고 `--service-account`로 지정(커밋 금지, .gitignore 처리됨).

## 실행

dry-run(기록 없이 변환 결과만 확인, Firebase 인증 불필요):
```
python migrate_dynamo_to_firestore.py --dry-run
```

실제 이관(ADC):
```
python migrate_dynamo_to_firestore.py
```

service account JSON으로 실행:
```
python migrate_dynamo_to_firestore.py --service-account service-account.json
```

기록 후 자동 검증 패스가 orders 수와 openCredits 수를 대조한다.

> 주의: 대상 컬렉션(orders/daySummaries/reportRollups/aggregates)이 비어있다고
> 가정한다. 기존 데이터가 있으면 먼저 비워야 깨끗하게 이관된다.
