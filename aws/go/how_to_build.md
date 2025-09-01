다음 단계에서는 go build 명령을 사용하여 실행 파일을 컴파일하고 Lambda용 .zip 파일 배포 패키지를 생성하는 방법을 보여줍니다. 코드를 컴파일하기 전에 GitHub에서 lambda 패키지를 설치했는지 확인하세요. 이 모듈은 Lambda와 함수 코드 간의 상호 작용을 관리하는 런타임 인터페이스의 구현을 제공합니다. 이 라이브러리를 다운로드하려면 다음 명령을 실행합니다.

## go get github.com/aws/aws-lambda-go/lambda
함수에서 AWS SDK for Go를 사용하는 경우 애플리케이션에 필요한 AWS 서비스 API 클라이언트와 함께 표준 SDK 모듈 세트를 다운로드하세요. SDK for Go를 설치하는 방법을 알아보려면 Getting Started with the AWS SDK for Go V2를 참조하세요.

## 제공된 런타임 패밀리 사용
Go는 다른 관리형 런타임과 다른 방법으로 구현됩니다. Go는 기본적으로 실행 가능한 바이너리로 컴파일되므로 전용 언어 런타임이 필요하지 않습니다. OS 전용 런타임(provided 런타임 패밀리)을 사용하여 Lambda에 Go 함수를 배포합니다.

## .zip 배포 패키지 생성(macOS/Linux)
애플리케이션의 main.go 파일이 들어 있는 프로젝트 디렉터리에서 실행 파일을 컴파일합니다. 다음 사항에 유의하세요.

실행 파일의 이름은 bootstrap이어야 합니다. 자세한 내용은 핸들러 이름 지정 규칙 단원을 참조하십시오.

```bash
GOOS=linux GOARCH=amd64 go build -tags lambda.norpc -o bootstrap main.go
```

실행 파일을 .zip 파일로 패키지하여 배포 패키지를 만듭니다.

```bash
zip myFunction.zip bootstrap
```

### 참고
bootstrap 파일은 .zip 파일의 루트에 있어야 합니다.

함수를 생성합니다. 다음 사항에 유의하세요.

바이너리의 이름은 bootstrap이어야 하지만 핸들러 이름은 무엇이든 지정할 수 있습니다. 자세한 내용은 핸들러 이름 지정 규칙 단원을 참조하십시오.