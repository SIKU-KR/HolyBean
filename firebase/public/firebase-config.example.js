// Firebase 콘솔 > 프로젝트 설정 > 일반 > 웹 앱(</>) 등록 후 나오는 설정을 복사해
// 이 파일을 firebase-config.js 로 복사한 뒤 값을 채운다. firebase-config.js는 gitignore됨.
//
// 로컬 에뮬레이터 테스트만 할 때는 apiKey에 아무 문자열("demo-key")이나 넣어도 동작한다.
// projectId는 반드시 "holybean-e4201" 이어야 한다(에뮬레이터/실서버 동일).
export const firebaseConfig = {
  apiKey: "REPLACE_ME",
  authDomain: "holybean-e4201.firebaseapp.com",
  projectId: "holybean-e4201",
  appId: "REPLACE_ME",
};
