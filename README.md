# Project Starter with Docs as Code

アプリケーション開発とDocs as Codeをすぐ始められる、技術スタック非依存のプロジェクト雛形です。

`app/` や `docs/` を含む初期構成は作成済みです。プロジェクト生成後は、既存ファイル内のプレースホルダーを実際の内容へ置き換えて使います。

## Recommended Usage

GitHub上でこのリポジトリをTemplate Repositoryに設定し、リポジトリ画面の **Use this template** から新しいリポジトリを作成します。

この方法では、次が自動的に引き継がれます。

- `app/`, `tests/`, `scripts/`, `config/`
- Docs as Codeのディレクトリ構成
- Current State、ADR、Proposal、Roadmap、Changelogの初期ファイル
- `.gitignore`

新規リポジトリ作成後にcloneします。

```bash
cd ~/git
git clone git@github.com:<owner>/<new-project>.git
cd <new-project>
```

## Clone Fallback

Template Repositoryを使わず直接cloneする場合は、clone後に元のremoteを新規プロジェクトへ付け替えます。

```bash
cd ~/git
git clone https://github.com/YutaSSuzuki/md_docs.git <new-project>
cd <new-project>
git remote rename origin template
git remote add origin git@github.com:<owner>/<new-project>.git
git push -u origin main
```

`md_docs` へ誤ってpushしないため、通常は **Use this template** を使用します。

## Initial Structure

```text
.
├── app/                         application source
├── config/                      configuration templates
├── scripts/                     development and operation scripts
├── tests/                       automated tests
├── docs/
│   ├── index.md
│   ├── documentation-policy.md
│   ├── architecture/
│   │   ├── current-state.md
│   │   ├── system-context.md
│   │   ├── runtime-flow.md
│   │   └── deployment.md
│   ├── api/
│   ├── database/
│   ├── operations/
│   ├── performance/
│   ├── adr/
│   ├── proposals/
│   ├── roadmap/
│   └── changelog/
├── .gitignore
└── README.md
```

## First Update

プロジェクト開始時に、新しいファイルやディレクトリを作る必要はありません。まず既存ファイルを更新します。

1. このREADMEをプロジェクト名、目的、起動方法に書き換える
2. `app/README.md` に採用技術とソース配置を書く
3. `docs/architecture/current-state.md` に初期構成を書く
4. `docs/roadmap/current-roadmap.md` に最初の作業を書く
5. `docs/api/index.md` と `docs/database/schema.md` を現状に合わせる

## Documentation Model

| Idea | Role |
| --- | --- |
| Docs as Code | MarkdownをGitで管理し、コードと同じ変更履歴に載せる |
| Living Documentation | Current State文書を常に最新状態に保つ |
| ADR | 後から「なぜ？」となる判断を削除せずに残す |
| Proposal | 実装前の検討案と承認対象を残す |
| Roadmap | 未着手、進行中、保留、完了を管理する |
| Changelog | 実施済み変更を時系列で記録する |

## Adding Records Later

初期構成の作成は不要ですが、設計判断や変更案が増えた場合は履歴を別ファイルとして追加します。

```bash
cp docs/adr/template.md docs/adr/0001-example.md
cp docs/proposals/template.md docs/proposals/example.md
```

これは初期セットアップではなく、開発履歴を追加する通常運用です。
