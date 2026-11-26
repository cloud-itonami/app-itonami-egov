# app-itonami-egov

電子政府の Itonami bot profile（AWAI-155 / AWAI-3147）。国民側と政府側を、同じ制度パックでつなぐ。

| profile | 立場 | すること | しないこと |
|---|---|---|---|
| `shakai` | 国民側（個人・家族） | 出典・期限・不明点つきの候補手続き、下書き、本人が同意した項目だけの受け渡し票 | 届出の要否や受理の判断、提出・送信 |
| `kaisha` | 国民側（法人・団体） | 代表権を確認したうえでの候補手続きと下書き | 登記の受否の判断、署名・提出 |
| `matsuri` | 政府側（担当者の補助） | 機関・担当者・受領者を確認し、受付準備の確認表を作って人の審査者へ引き継ぐ | 受理・審査・決定・通知の送付 |

3 つの profile は判定ロジック（`src/itonami/egov/core.cljc`）と制度パック（`seido/*.edn`）を共有する。
各国への対応は、国ごとに profile を複製せず、制度パックを足して行う。

## 制度パック

| パック | 中身 | 状態 |
|---|---|---|
| `seido/jp.edn` | 住民基本台帳法 第22〜24条（転入・転居・転出届）、第30条の46（外国人住民転入届）、戸籍法 第49条（出生届）・第86条（死亡届）、会社法 第915条（変更の登記）、所得税法 第120条（確定申告）・第143条/第144条（青色申告）、消費税法 第45条（確定申告）・第37条（簡易課税）、国税通則法 第10条/第18条/第19条、税理士法 第52条（独占業務境界）、電子帳簿保存法 第4条/第7条 | 出典は e-Gov 法令 API v2 で 2026-11-18, 2026-11-23, 2026-11-26（AWAI-4076/AWAI-4075）に取得し sha256 を記録。国税庁一次情報も実測確認。専門家レビュー前。資格独占境界（税理士法第52条）と電帳法保存要件概要を符号化 |
| `seido/us.edn` | 骨組みだけ | 州を指定しなければ未確定。州の手続きは未収録 |
| `seido/se.edn` | Folkbokföringslag (1991:481) 25 §（flyttanmälan。届出先は Skatteverket、1 週間以内） | 出典は data.riksdagen.se で 2026-11-18 に取得し文書全体の sha256 を記録。専門家レビュー前。実在の市町村は Göteborgs kommun（AWAI-3421）だけで、条例・方針 2 件を参考情報として持つ |
| `seido/de.edn` | Bundesmeldegesetz § 17 Abs. 1（Anmeldung、転入後 2 週間以内、転入先の Meldebehörde） | 出典は gesetze-im-internet.de（非公式の統合版）で 2026-11-18 に取得し § 17 のページの sha256 を記録。改正状態は xml.zip の standangabe。実在の subdivision は Land Berlin と Landeshauptstadt München（AWAI-3448） |
| `seido/at.edn` | Meldegesetz 1991 § 3 Abs. 1（3 日以内、Meldebehörde） | 出典は RIS の OGD API で 2026-11-18 に取得（NOR40257117、2023-12-12 から有効）。実在の subdivision は Wien（AWAI-3448） |

市町村の条例・方針は手続きではない。`:pack/subdivisions` の `:references` に参考として置き、Shakai は転入先の分を `:local-references` として返す。
参考情報の元は itonami registry の `skills/knowledge/municipal-ordinance-knowledge`（62 市町村。元の municipality repo は AWAI-3088 で registry に移った）。
条例の目録だけでは手続きを作らない。手続きは国の法令などの一次情報を取得して足す。

パックの `:pack/review-by` を過ぎると止まる。`:review/status` が `:reviewed` になるまで、候補は示すが提出は提案しない。

## 止まり方

結果は `:candidates`（候補）、`:packet`（受け渡し票）、`:ready-for-human-review`（受付準備）のほかに、
`:blocked`（権限なし、同意拒否、古いパック、代表権未確認など）か `:undetermined`（未対応の法域、州・市町村の未指定、曖昧な住所など）を返す。
`matsuri-intake` の `:decision` は常に `nil`。

## 画面（Gyosei、AWAI-3148）

`guest/` は Awai OS の shell が sandbox iframe（kotoba-client-runtime の `mountRestrictedClientApp`）で動かす画面。
`bb guest` で `dist/gyosei.html`（単一の HTML）と `dist/gyosei.json`（bytes と sha256）を作る。
画面は通信できない（CSP `connect-src 'none'`）。外部操作は e-Gov 法令 API の条文読み取り（`awai-egov` / `e-gov-law-api` / `read-statute`）だけで、
host に selector と法令 ID・条番号を渡す。エンドポイントの組み立てと毎回の同意は host 側の共通 broker が行う。
取得した条文の sha256 を制度パックの記録と比べ、一致か更新ありを表示する。Matsuri への受け渡しは、この端末内のテスト用で送信はしない。

## 実行

```bash
bb test            # test（clojure -M でも同じ test が通る）
bb check-profiles  # profile 定義の検査
bb fixture-run     # 架空データの通し。記録は docs/evidence/
bb guest           # 画面のビルド（JVM で shadow-cljs）
```

## 現状（2026-11-26）

- 定義・判定ロジック・test・架空データの通しの記録まで。itonami agent での実行（native turn）は未確認。
- 日本パックは住所変更・出生・死亡・外国人住民の住所届に加え、確定申告（所得税：青色申告/白色申告、消費税：簡易課税/本則課税/2割特例等の候補整理）を収録（AWAI-4076 / AWAI-4075）。
- 画面（Tauri app への取り込み）は AWAI-3148、制度パックのデータ元は AWAI-3150。
- profile は registry に登録するが、登録は権限を与えず、cron も有効にしない。
