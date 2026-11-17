# AGENTS.md

- 追跡: Linear AWAI-155（親）、AWAI-3147。workspace 全体の `AGENTS.md`（Kotoba end-user apps の共有アーキテクチャ契約、Awai Otehon の規則）に従う。
- profile は `hermes/profiles/<id>/` に置く Itonami profile（Hermes の配置を借りた定義）。`itonami.edn` と `yakuwari.edn` の capability は一致させ、`bb check-profiles` で確かめる。
- 提出・決定・資格判定・署名・支払い・送信・cron は `:blocked` のまま。広げるのは owner の決定。
- 制度パックの出典は一次情報だけを使い、URL・発行者・取得日・改正 ID・sha256 を残す。取得していない内容や、記憶から書いた条文は入れない。
- 架空データだけを使う。実在の人・法人・自治体の情報や本人確認書類を repo に入れない。
- 判定ロジックは `.cljc` の純粋関数にし、ファイル・ネットワーク・時計に触れない。`bb test` が通ることを PR の条件にする。
