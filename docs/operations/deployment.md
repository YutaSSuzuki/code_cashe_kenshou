# Deployment Operation

## Status

Verified on Ubuntu EC2

## Prerequisites

- Ubuntu Server 24.04 LTS以降のx86_64 EC2が起動済みであること。
- EC2のIAMロールにCloudWatchへのメトリクス・ログ送信権限があること。
- Security GroupでSSHを検証元IPだけに許可していること。
- Security Groupで8080とJMX 9010を公開していないこと。
- アプリケーションのテストとパッケージ作成が成功していること。

## Required Materials

### Local: WSL2 Ubuntu 22.04 LTS

| Material | Package / Command | Purpose | Required |
| --- | --- | --- | --- |
| OpenJDK 21 JDK | `openjdk-21-jdk` | ビルド、実行、`jcmd`診断 | Yes |
| Git | `git` | ソース・文書・設定の管理とpush | Yes |
| curl | `curl` | ヘルスチェックと負荷生成 | Yes |
| unzip | `unzip` | 初期資材の展開 | Yes |
| AWS CLI v2 | `aws` | AWS設定・状態確認。コンソールだけで操作する場合は省略可 | Optional |
| Maven | `./mvnw` | リポジトリ内のMaven Wrapperを使用 | Bundled |

ローカルではCloudWatch Agentを使用しない。Code Cacheは`jcmd`とJVMログで確認する。

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk git curl unzip

java -version
javac -version
jcmd -h
git --version
curl --version
```

複数のJavaが入っていてJava 21が選択されない場合は、次のコマンドで切り替える。

```bash
sudo update-alternatives --config java
sudo update-alternatives --config javac
```

### EC2: Ubuntu Server 24.04 LTS or later, x86_64

| Material | Package / Source | Purpose | Required |
| --- | --- | --- | --- |
| OpenJDK 21 JDK | `openjdk-21-jdk` | EC2でのビルド、実行、`jcmd`診断 | Yes |
| Git | `git` | GitHubからclone | Yes |
| curl | `curl` | CloudWatch Agent取得とヘルスチェック | Yes |
| unzip | `unzip` | Maven Wrapperなどの展開 | Yes |
| CloudWatch Agent | AWS公式Ubuntu x86-64 `.deb` | JMXメトリクスとログ送信 | Yes |
| Maven | `./mvnw` | cloneしたリポジトリ内のWrapperを使用 | Bundled |

IPv6経路がない環境で`apt-get`がIPv6接続待ちになる場合があるため、この検証ではIPv4を強制する。

```bash
echo 'Acquire::ForceIPv4 "true";' \
  | sudo tee /etc/apt/apt.conf.d/99force-ipv4

sudo apt-get update
sudo apt-get install -y openjdk-21-jdk git curl unzip

curl -fL \
  https://amazoncloudwatch-agent.s3.amazonaws.com/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb \
  -o /tmp/amazon-cloudwatch-agent.deb
sudo dpkg -i -E /tmp/amazon-cloudwatch-agent.deb
```

インストール結果を記録する。

```bash
cat /etc/os-release
uname -m
java -version
javac -version
jcmd -h
git --version
dpkg-query -W amazon-cloudwatch-agent
```

ARM64 EC2を使用する場合はCloudWatch AgentのURLを`ubuntu/arm64/latest`へ変更する。
比較条件を単純にするため、初回検証はx86_64に固定する。

### AWS Resources

パッケージとは別に以下が必要になる。

| Resource | Requirement |
| --- | --- |
| EC2 IAM role | 初回検証ではAWS管理ポリシー`CloudWatchAgentServerPolicy`を付与 |
| Security Group inbound | SSH TCP 40000を検証元グローバルIPの`/32`だけに許可。TCP 22、8080、9010は許可しない |
| Security Group outbound | 構築中は全通信、または少なくともAPT用TCP 80とGitHub・Maven・AWS API用TCP 443を許可 |
| CloudWatch Logs | `/code-cache-demo/application`。保持期間は7日を初期値とする |
| CloudWatch custom metrics | `CodeCacheDemo`名前空間 |

GitHubのprivate repositoryを使用する場合は、EC2へ長期PATを保存せず、read-onlyの
Deploy Keyなどclone専用の認証方法を用意する。public repositoryなら認証は不要。

## Restore Procedure

public repositoryをHTTPSでcloneする。GitHub用のoutbound TCP 22は不要である。

```bash
cd ~
git clone https://github.com/YutaSSuzuki/code_cashe_kenshou.git
cd ~/code_cashe_kenshou
git log -1 --oneline

cd app
./mvnw clean test
./mvnw clean package
```

Spring BootとMaven本体をOSへ個別インストールする必要はない。Spring Bootはプロジェクト依存関係、
Mavenはリポジトリ内のMaven Wrapperから取得される。

ビルド後のJARを確認する。

```bash
ls -l target/code-cache-demo-*.jar
java -version
```

検証ではJVMを再起動してCode Cacheと生成classをリセットするため、手動起動を使用する。
本番相当の常駐化を行う場合は、検証完了後にsystemd unitを別途作成する。

## Verification

- `GET /actuator/health`がHTTP 200を返す。
- `jcmd <PID> Compiler.codecache`がCode Cache情報を返す。
- CloudWatchにCodeHeapメトリクスが表示される。
- `/code-cache-demo/application`へログが送信される。

## Rollback

1. 負荷生成を停止する。
2. 直前に正常動作したJARと設定を復元する。
3. アプリケーションとCloudWatch Agentを再起動する。
4. ヘルスチェックと監視送信を再確認する。

検証環境自体が不要になった場合は、ログ保持要否を確認してからEC2、アラーム、
ロググループなどの関連AWSリソースを削除する。

## Related Documents

- [Deployment Architecture](../architecture/deployment.md)
- [Runbook](runbook.md)
