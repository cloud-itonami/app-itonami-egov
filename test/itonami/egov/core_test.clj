(ns itonami.egov.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [itonami.egov.core :as c]
            [itonami.egov.fixtures :as f]))

(defn- plan [& {:as over}]
  (c/shakai-plan (merge {:packs f/packs :today f/today :grant f/self-grant :facts f/move-facts}
                        over)))

(deftest dates
  (is (= "2026-12-04" (c/add-days "2026-11-20" 14)))
  (is (= "2028-03-01" (c/add-days "2028-02-28" 2)) "閏年")
  (is (nil? (c/date->day "2026-02-30")))
  (is (nil? (c/date->day "20261118"))))

;; 受入条件 2: 架空データで Shakai -> Matsuri の受付準備と人への引継ぎを通す
(deftest jp-address-change-flow
  (let [p (plan)
        packet (c/share-packet {:plan p :facts f/move-facts :consent f/consent :today f/today})
        intake (c/matsuri-intake {:packet packet :agency f/agency :staff f/staff
                                  :packs f/packs :today f/today})]
    (testing "Shakai は候補と出典・期限を出す"
      (is (= :candidates (:status p)))
      (is (= #{:jp/tenshutsu-todoke :jp/tennyu-todoke}
             (set (map :procedure/id (:candidates p)))))
      (is (every? #(= :candidate (:standing %)) (:candidates p)))
      (let [tennyu (first (filter #(= :jp/tennyu-todoke (:procedure/id %)) (:candidates p)))]
        (is (= "2026-12-04" (get-in tennyu [:deadline :due])))
        (is (= :jp.fixture/city-b (:submit-to tennyu)))
        (is (= "4fcd8ce5cb628859634e33ac4c64fe14c2a9f768caf7a72d4395ed83d21ccd2b"
               (get-in tennyu [:sources 0 :sha256]))))
      (is (= :withheld-unreviewed-pack (:submission-proposal p))
          "専門家レビュー前のパックでは提出を提案しない"))
    (testing "同意した項目だけを受け渡し票にする"
      (is (= :packet (:status packet)))
      (is (= #{:principal-ref :from :to :moved-on :procedures}
             (set (keys (:packet/items packet))))))
    (testing "Matsuri は受付準備まで整えて人へ引き継ぎ、決定しない"
      (is (= :ready-for-human-review (:status intake)))
      (is (nil? (:decision intake)))
      (is (= {:to :human-reviewer :role :case-reviewer} (:handoff intake)))
      (is (= [:jp/tennyu-todoke] (:procedures intake))
          "転出届は転出元（架空市A）の手続きなので架空市B では扱わない")
      (is (seq (:checklist intake))))))

(deftest within-municipality-move
  (let [p (plan :facts (assoc f/move-facts :to {:country :jp :subdivision :jp.fixture/city-a}))]
    (is (= [:jp/tenkyo-todoke] (mapv :procedure/id (:candidates p))))))

;; 受入条件 3: 負の対照
(deftest no-authority
  (is (= :no-authority (:reason (plan :grant nil))))
  (is (= :no-authority (:reason (plan :grant (assoc f/self-grant :principal "someone-else")))))
  (is (= :no-authority
         (:reason (plan :grant {:actor :delegate :principal f/person :scope #{:plan}
                                :expires "2026-11-01"})))
      "期限切れの委任"))

(deftest consent-denied-sends-nothing
  (let [p (plan)]
    (doseq [consent [nil
                     (assoc f/consent :decision :denied)
                     (assoc f/consent :expires "2026-11-01")
                     (dissoc f/consent :recipient)]]
      (let [r (c/share-packet {:plan p :facts f/move-facts :consent consent :today f/today})]
        (is (= :blocked (:status r)))
        (is (not (contains? r :packet/items)))))))

(deftest minimal-disclosure
  (let [r (c/share-packet {:plan (plan) :facts f/move-facts
                           :consent (assoc f/consent :items #{:to :moved-on :home-phone})
                           :today f/today})]
    (is (= #{:to :moved-on} (set (keys (:packet/items r))))
        "同意していない項目と、共有できない項目は入らない")))

(deftest stale-pack-blocks
  (let [r (plan :today "2027-03-01")]
    (is (= :blocked (:status r)))
    (is (= :pack-not-current (:reason r)))
    (is (= :stale (:freshness r))))
  (let [packet (c/share-packet {:plan (plan) :facts f/move-facts :consent f/consent :today f/today})
        r (c/matsuri-intake {:packet packet :agency f/agency :staff f/staff
                             :packs f/packs :today "2027-03-01"})]
    (is (= :blocked (:status r)))))

(deftest ambiguous-address
  (is (= :ambiguous-address (:reason (plan :facts (assoc f/move-facts :address-confirmed? false))))))

(deftest matsuri-refuses-without-authority
  (let [packet (c/share-packet {:plan (plan) :facts f/move-facts :consent f/consent :today f/today})
        run #(c/matsuri-intake (merge {:packet packet :agency f/agency :staff f/staff
                                       :packs f/packs :today f/today} %))]
    (is (= :agency-unconfirmed (:reason (run {:agency (assoc f/agency :confirmed? false)}))))
    (is (= :no-authority (:reason (run {:staff (assoc f/staff :roles #{:viewer})}))))
    (is (= :no-authority (:reason (run {:staff (assoc f/staff :agency :jp.fixture/city-a-office)}))))
    (is (= :wrong-recipient
           (:reason (run {:agency (assoc f/agency :id :jp.fixture/other-office)
                          :staff (assoc f/staff :agency :jp.fixture/other-office)}))))
    (is (= :no-packet (:reason (run {:packet (c/share-packet {:plan (plan) :facts f/move-facts
                                                              :consent nil :today f/today})}))))))

(deftest kaisha
  (let [run #(c/kaisha-plan (merge {:packs f/packs :today f/today :grant f/org-grant
                                    :facts f/kaisha-facts} %))]
    (let [r (run {})]
      (is (= :candidates (:status r)))
      (is (= "2026-12-04" (get-in r [:candidates 0 :deadline :due])))
      (is (= [:jp/honten-iten-across-registry-offices] (get-in r [:candidates 0 :unknowns]))))
    (is (= :representative-unconfirmed
           (:reason (run {:facts (assoc f/kaisha-facts :representative-confirmed? false)}))))
    (is (= :unsupported-entity-form
           (:reason (run {:facts (assoc f/kaisha-facts :form :ippan-shadan)}))))))

;; 受入条件 4: 未対応の法域では結論を出さない
(deftest unsupported-jurisdiction-is-undetermined
  (let [us {:country :us}
        r (plan :facts (assoc f/move-facts :from us :to us))]
    (is (= :undetermined (:status r)))
    (is (= :subdivision-required (:reason r)) "米国は州を指定しない限り未確定"))
  (let [r (plan :facts (assoc f/move-facts :from {:country :us :subdivision :us/ca}
                              :to {:country :us :subdivision :us/ca}))]
    (is (= :unknown-subdivision (:reason r)) "州のパックが未収録なら未確定"))
  (let [fr {:country :fr :subdivision :fr/paris}
        r (plan :facts (assoc f/move-facts :from fr :to fr))]
    (is (= :unsupported-jurisdiction (:reason r))))
  (is (= :cross-border-not-supported
         (:reason (plan :facts (assoc f/move-facts :to {:country :us :subdivision :us/ca})))))
  (is (= :unknown-subdivision
         (:reason (plan :facts (assoc f/move-facts :to {:country :jp :subdivision :jp/real-city}))))
      "パックにない市町村は推測しない"))

;; AWAI-3421: スウェーデン（Göteborgs kommun）。届出先は国の機関で、市町村は参考情報だけを持つ
(deftest se-move-to-goteborg
  (let [p (plan :facts f/se-move-facts)
        cand (first (:candidates p))]
    (is (= :candidates (:status p)))
    (is (= [:se/flyttanmalan] (mapv :procedure/id (:candidates p))))
    (is (= {:kind :within-days :days 7 :from "2026-11-20" :due "2026-11-27"} (:deadline cand))
        "25 §: 引越しから 1 週間以内")
    (is (= {:agency :se/skatteverket :name "Skatteverket"} (:submit-to cand))
        "提出先は市町村ではなく Skatteverket")
    (is (= "bdaa12bc4ea3eb5cb6b3fab600325820e299cf3193e45de98e2de7bdf2b0bf7d"
           (get-in cand [:sources 0 :sha256])))
    (is (= :withheld-unreviewed-pack (:submission-proposal p)))
    (is (= #{:se.goteborg/lokala-ordningsforeskrifter :se.goteborg/stadsmiljopolicy}
           (set (map :id (:local-references p))))
        "転入先 Göteborg の条例・方針を参考として返す")
    (is (every? :sha256 (:local-references p))))
  (testing "同じ市町村内の引越しにも同じ届出"
    (let [g {:country :se :subdivision :se.kommun/goteborg}
          p (plan :facts (assoc f/se-move-facts :from g :to g))]
      (is (= [:se/flyttanmalan] (mapv :procedure/id (:candidates p))))))
  (testing "参考情報のない市町村では空"
    (let [a {:country :se :subdivision :se.fixture/kommun-a}
          p (plan :facts (assoc f/se-move-facts :to a))]
      (is (= [] (:local-references p))))))

(deftest se-negative-controls
  (is (= :unknown-subdivision
         (:reason (plan :facts (assoc f/se-move-facts :to {:country :se :subdivision :se.kommun/stockholm}))))
      "パックにない kommun は推測しない")
  (is (= :pack-not-current (:reason (plan :facts f/se-move-facts :today "2027-03-01"))))
  (is (= :no-authority (:reason (plan :facts f/se-move-facts :grant nil))))
  (is (= :cross-border-not-supported
         (:reason (plan :facts (assoc f/se-move-facts :from {:country :jp :subdivision :jp.fixture/city-a})))))
  (testing "Göteborgs kommun の窓口は Skatteverket への届出を扱わない"
    (let [p (plan :facts f/se-move-facts)
          consent (assoc f/consent :recipient :se.fixture/goteborg-office)
          packet (c/share-packet {:plan p :facts f/se-move-facts :consent consent :today f/today})
          office {:id :se.fixture/goteborg-office :name "Göteborg kontor（テスト用）"
                  :jurisdiction {:country :se :subdivision :se.kommun/goteborg} :confirmed? true}
          r (c/matsuri-intake {:packet packet :agency office
                               :staff {:id "fixture-staff-se" :agency :se.fixture/goteborg-office :roles #{:intake}}
                               :packs f/packs :today f/today})]
      (is (= :not-this-agency (:reason r))))))

(defn- move [country from to]
  {:person f/person :event :move
   :from {:country country :subdivision from} :to {:country country :subdivision to}
   :moved-on "2026-11-20" :address-confirmed? true})

;; AWAI-3448: ドイツ（Berlin・München）とオーストリア（Wien）。届出先は転入先の Meldebehörde
(deftest de-move-to-berlin
  (let [p (plan :facts (move :de :de.stadt/muenchen :de.land/berlin))
        cand (first (:candidates p))]
    (is (= :candidates (:status p)))
    (is (= [:de/anmeldung] (mapv :procedure/id (:candidates p))))
    (is (= {:kind :within-days :days 14 :from "2026-11-20" :due "2026-12-04"} (:deadline cand))
        "§ 17 Abs. 1: 転入後 2 週間以内")
    (is (= :de.land/berlin (:submit-to cand)))
    (is (= [:de/competent-meldebehoerde-not-in-cited-section] (:unknowns cand)))
    (is (= "c377275ab2ce15f8393536c53549b96c22c6b31b06f37e87ac8ab0ee4c1905cb"
           (get-in cand [:sources 0 :sha256])))
    (is (= #{:de.berlin/informationsfreiheitsgesetz :de.berlin/datenschutzgesetz}
           (set (map :id (:local-references p)))))
    (is (= :withheld-unreviewed-pack (:submission-proposal p))))
  (testing "München への転入では München の参考情報"
    (is (= #{:de.muenchen/gruenanlagensatzung :de.muenchen/strassenreinigungssatzung}
           (set (map :id (:local-references (plan :facts (move :de :de.land/berlin :de.stadt/muenchen))))))))

(deftest de-matsuri-berlin-office-prepares-intake
  (let [facts (move :de :de.stadt/muenchen :de.land/berlin)
        p (plan :facts facts)
        office {:id :de.fixture/berlin-office :name "Berlin Bürgeramt（テスト用）"
                :jurisdiction {:country :de :subdivision :de.land/berlin} :confirmed? true}
        packet (c/share-packet {:plan p :facts facts :consent (assoc f/consent :recipient :de.fixture/berlin-office)
                                :today f/today})
        run #(c/matsuri-intake {:packet packet :agency %1
                                :staff {:id "fixture-staff-de" :agency (:id %1) :roles #{:intake}}
                                :packs f/packs :today f/today})
        r (run office)]
    (is (= :ready-for-human-review (:status r)))
    (is (nil? (:decision r)))
    (is (= [:de/anmeldung] (:procedures r)))
    (is (= ["この手続きの確認項目は未収録。担当者が確認する"] (:checklist r))
        "DE の確認項目は収録していないので、作らずに担当者へ返す")
    (testing "München の窓口あての受け渡し票ではない"
      (is (= :wrong-recipient
             (:reason (run (assoc office :id :de.fixture/muenchen-office
                                  :jurisdiction {:country :de :subdivision :de.stadt/muenchen})))))))))

(deftest at-move-to-wien
  (let [p (plan :facts (move :at :at.fixture/gemeinde-a :at.gemeinde/wien))
        cand (first (:candidates p))]
    (is (= [:at/anmeldung] (mapv :procedure/id (:candidates p))))
    (is (= "2026-11-23" (get-in cand [:deadline :due])) "§ 3 Abs. 1: 3 日以内")
    (is (= :at.gemeinde/wien (:submit-to cand)))
    (is (= [:at.wien/bauordnung-geschichte] (mapv :id (:local-references p))))))

(deftest de-at-negative-controls
  (is (= :unknown-subdivision (:reason (plan :facts (move :de :de.land/berlin :de.stadt/hamburg))))
      "パックにない市町村は推測しない")
  (is (= :unknown-subdivision (:reason (plan :facts (move :at :at.gemeinde/wien :at.gemeinde/graz)))))
  (is (= :pack-not-current (:reason (plan :facts (move :de :de.land/berlin :de.stadt/muenchen) :today "2027-03-01"))))
  (is (= :pack-not-current (:reason (plan :facts (move :at :at.gemeinde/wien :at.gemeinde/wien) :today "2027-03-01"))))
  (is (= :no-authority (:reason (plan :facts (move :de :de.land/berlin :de.stadt/muenchen) :grant nil))))
  (is (= :cross-border-not-supported
         (:reason (plan :facts (assoc (move :de :de.land/berlin :de.land/berlin)
                                      :to {:country :at :subdivision :at.gemeinde/wien}))))))

(deftest applies-when-set-or-keyword
  (is (c/applies? :head-office-move :head-office-move))
  (is (c/applies? #{:a :b} :b))
  (is (not (c/applies? #{:a :b} :c)))
  (is (not (c/applies? nil :a))))

(deftest submission-without-receipt-is-not-done
  (is (= :not-submitted (c/submission-status {:submitted? false})))
  (is (= :submitted-unconfirmed (c/submission-status {:submitted? true})))
  (is (= :receipt-recorded (c/submission-status {:submitted? true :receipt {:id "r-1"}}))))

;; AWAI-3780: 出生・死亡・外国人住民の住所届（戸籍法 第49条・第86条、住民基本台帳法 第30条の46）
(deftest jp-birth
  (let [p (plan :facts f/birth-facts)
        cand (first (:candidates p))]
    (is (= :candidates (:status p)))
    (is (= :birth (:applies-when p)))
    (is (= [:jp/shusshin-todoke] (mapv :procedure/id (:candidates p))))
    (is (= {:kind :within-days :days 14 :from "2026-11-20" :due "2026-12-04"} (:deadline cand))
        "戸籍法 第49条第1項: 出生から14日以内（国内）")
    (is (= :jp.fixture/city-b (:submit-to cand)))
    (is (= "e160c11b1cdd57d3113d127890dbcdba1c0ad17a64107004594f4f65fbbb13e2"
           (get-in cand [:sources 0 :sha256])))
    (is (= [:jp/birth-abroad-3-month-period-not-encoded] (:unknowns cand)))
    (is (= :withheld-unreviewed-pack (:submission-proposal p))))
  (testing "負の対照"
    (is (= :born-on-missing (:reason (plan :facts (dissoc f/birth-facts :born-on)))))
    (is (= :no-authority (:reason (plan :facts f/birth-facts :grant nil))))
    (is (= :unknown-subdivision
           (:reason (plan :facts (assoc f/birth-facts :to {:country :jp :subdivision :jp/real-city})))))
    (is (= :unsupported-event (:reason (plan :facts (assoc f/birth-facts :event :retirement)))))))

(deftest jp-death
  (let [p (plan :facts f/death-facts)
        cand (first (:candidates p))]
    (is (= :candidates (:status p)))
    (is (= [:jp/shibou-todoke] (mapv :procedure/id (:candidates p))))
    (is (= {:kind :within-days :days 7 :from "2026-11-20" :due "2026-11-27"} (:deadline cand))
        "戸籍法 第86条第1項: 7日以内。起算は事実の :died-on（条文は死亡の事実を知った日）")
    (is (= :jp.fixture/city-b (:submit-to cand)))
    (is (= "b559e46a2c690b4c5c6844d7d841eb551a8d05dcb05f4f42e2bc8cc36c5e4ff8"
           (get-in cand [:sources 0 :sha256]))))
  (testing "負の対照"
    (is (= :died-on-missing (:reason (plan :facts (dissoc f/death-facts :died-on)))))
    (is (= :no-authority (:reason (plan :facts f/death-facts :grant nil))))))

(deftest jp-foreign-resident-move
  (let [p (plan :facts f/foreign-resident-facts)
        cand (first (:candidates p))]
    (is (= :candidates (:status p)))
    (is (= :foreign-resident-move (:applies-when p)))
    (is (= [:jp/gaikokujin-tennyu-todoke] (mapv :procedure/id (:candidates p))))
    (is (= {:kind :within-days :days 14 :from "2026-11-20" :due "2026-12-04"} (:deadline cand))
        "住民基本台帳法 第30条の46: 転入日から14日以内")
    (is (= :jp.fixture/city-b (:submit-to cand)))
    (is (= "360bbb25492b5af0a2ef64775bdc22b7ee33bcff6b84e408be06d973df5966f4"
           (get-in cand [:sources 0 :sha256])))
    (is (= :withheld-unreviewed-pack (:submission-proposal p))))
  (testing "複数国籍でも候補になる"
    (is (= :candidates (:status (plan :facts (assoc f/foreign-resident-facts
                                                     :nationalities #{:jp :kr}))))))
  (testing "負の対照: 国籍・在留状態が不明なら未確定"
    (is (= :nationality-missing (:reason (plan :facts (dissoc f/foreign-resident-facts :nationalities)))))
    (is (= :not-foreign-resident
           (:reason (plan :facts (assoc f/foreign-resident-facts :nationalities #{:jp})))))
    (is (= :residence-status-missing
           (:reason (plan :facts (dissoc f/foreign-resident-facts :residence-status)))))
    (is (= :moved-on-missing (:reason (plan :facts (dissoc f/foreign-resident-facts :moved-on)))))
    (is (= :cross-border-not-supported
           (:reason (plan :facts (assoc f/foreign-resident-facts
                                        :from {:country :us :subdivision :us/ca}))))))
  (testing "普通の住所変更では外国人住民の手続きは候補にしない（jp-address-change-flow の完全一致を保持）"
    (is (= #{:jp/tenshutsu-todoke :jp/tennyu-todoke}
           (set (map :procedure/id (:candidates (plan :facts (assoc f/move-facts
                                                                     :nationalities #{:kr}
                                                                     :residence-status :medium-long-term-resident))))))
        "第30条の46は国外からの転入の特例なので、国内の :move には適用しない")))

;; ===========================================================================
;; AWAI-4076 / AWAI-4075: 確定申告制度パック（所得税・消費税・税理士法・電帳法）
;; ===========================================================================

(deftest jp-kakutei-shinkoku-blue
  (let [p (plan :facts f/tax-facts-blue)
        cand (first (:candidates p))]
    (testing "青色申告の候補・期限・提出先・様式・一次出典"
      (is (= :candidates (:status p)))
      (is (= :tax-return (:applies-when p)))
      (is (= [:jp/shotoku-kakutei-shinkoku-aoiro] (mapv :procedure/id (:candidates p))))
      (is (= :candidate (:standing cand)))
      (is (= {:kind :statutory-period
              :from "2027-02-16"
              :due "2027-03-15"
              :holiday-exception :tsusoku-10}
             (:deadline cand))
          "法定申告期間: 翌年2月16日〜3月15日（所得税法 第120条、通則法 第10条）")
      (is (= {:agency :jp/tax-office :name "所轄税務署"} (:submit-to cand)))
      (is (= ["所得税確定申告書（第一表・第二表）" "青色申告決算書（一般用）"] (:forms cand)))
      (is (= :withheld-unreviewed-pack (:submission-proposal p))
          "専門家レビュー未了のため提出は提案しない")
      (is (= #{:jp/income-deduction-eligibility :jp/e-tax-or-paper-method :jp/tax-accountant-involvement}
             (set (:unknowns cand))))
      (let [sources (into {} (map (juxt :id identity) (:sources cand)))]
        (is (= "fb6e7acb23339be99b6198346e43c38fab24faa34c7d40f42e65b73df1218332"
               (get-in sources [:jp.law/shotoku-120 :sha256])))
        (is (= "af4d77b60c4b7eef4887ade597d097fbfeae7b734441fc8dcce09215f5ed4385"
               (get-in sources [:jp.law/shotoku-143 :sha256])))
        (is (= "5a54b4cb4496e92b4a5cb1d2c032ea0095992f2e20fa464a7478fd9c6978d99e"
               (get-in sources [:jp.law/tsusoku-10 :sha256])))
        (is (= "4539b86b8973efff65b555cafa1b54a01738cc8367f6d6c62caef3331903a06e"
               (get-in sources [:jp.law/zeirishi-52 :sha256])))
        (is (= "9e80d1292ea6c467761cf815d6a8fcf88bf3603c6fd05537be206907ca42bd06"
               (get-in sources [:jp.law/denshichobo-7 :sha256])))
        (is (= "https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/2020.htm"
               (get-in sources [:jp.nta/shotoku-2020 :url])))
        (is (= "https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/2070.htm"
               (get-in sources [:jp.nta/shotoku-2070 :url]))))
      (testing "AWAI-4075 法域パックスキーマ要件: 資格独占境界および電子帳簿保存法概要が含まれる"
        (is (some? (:regulated-professions p)))
        (is (contains? (get-in p [:regulated-professions :jp.profession/zeirishi :monopoly-scope])
                       :tax-representation))
        (is (true? (get-in p [:electronic-preservation :categories :electronic-transactions :mandatory?])))
        (is (false? (get-in p [:electronic-preservation :categories :electronic-books :mandatory?])))))))

(deftest jp-kakutei-shinkoku-white
  (let [p (plan :facts f/tax-facts-white)
        cand (first (:candidates p))]
    (is (= [:jp/shotoku-kakutei-shinkoku-shiroiro] (mapv :procedure/id (:candidates p))))
    (is (= ["所得税確定申告書（第一表・第二表）" "収支内訳書（一般用）"] (:forms cand)))
    (is (= {:kind :statutory-period
            :from "2027-02-16"
            :due "2027-03-15"
            :holiday-exception :tsusoku-10}
           (:deadline cand)))))

(deftest jp-kakutei-shinkoku-blue-new
  (let [p (plan :facts f/tax-facts-blue-new)
        pids (mapv :procedure/id (:candidates p))]
    (testing "新規青色申告希望の場合は青色申告承認申請手続と申告書の両方が候補"
      (is (= [:jp/aoiro-shinkoku-shonin-shinsei :jp/shotoku-kakutei-shinkoku-aoiro] pids))
      (let [shinsei (first (:candidates p))]
        (is (= ["所得税の青色申告承認申請書"] (:forms shinsei)))
        (is (= {:kind :statutory-deadline :due "2027-03-15" :holiday-exception :tsusoku-10}
               (:deadline shinsei)))))))

(deftest jp-kakutei-shinkoku-unspecified
  (let [p (plan :facts f/tax-facts-unspecified)
        pids (mapv :procedure/id (:candidates p))]
    (testing "申告区分未指定の場合は青色と白色の両方を提示し不明点に明示"
      (is (= [:jp/shotoku-kakutei-shinkoku-aoiro :jp/shotoku-kakutei-shinkoku-shiroiro] pids))
      (is (every? #(contains? (set (:unknowns %)) :jp/accounting-type-unspecified)
                  (:candidates p))))))

(deftest jp-kakutei-shinkoku-consumption-simplified
  (let [p (plan :facts f/tax-facts-consumption-simplified)
        pids (mapv :procedure/id (:candidates p))
        shohi (second (:candidates p))]
    (testing "消費税簡易課税選択の確定申告"
      (is (= [:jp/shotoku-kakutei-shinkoku-aoiro :jp/shohi-kakutei-shinkoku] pids))
      (is (= {:kind :statutory-deadline :due "2027-03-31" :holiday-exception :tsusoku-10}
             (:deadline shohi))
          "個人事業者の消費税申告期限は翌年3月31日（消費税法 第45条）")
      (is (not (contains? (set (:unknowns shohi)) :jp/simplified-tax-prior-notification-unconfirmed))
          "事前届出済みが確認されているため警告不明点は入らない"))
    (testing "事前届出が未確認の場合は不明点に届出未確認を明示"
      (let [p-unselected (plan :facts f/tax-facts-consumption-simplified-unselected)
            shohi-unselected (second (:candidates p-unselected))]
        (is (contains? (set (:unknowns shohi-unselected))
                       :jp/simplified-tax-prior-notification-unconfirmed))))))

(deftest jp-kakutei-shinkoku-consumption-two-tenths
  (let [p (plan :facts f/tax-facts-consumption-two-tenths)
        shohi (second (:candidates p))]
    (testing "インボイス2割特例選択時は経過措置要件の確認不明点を明示"
      (is (= [:jp/shotoku-kakutei-shinkoku-shiroiro :jp/shohi-kakutei-shinkoku]
             (mapv :procedure/id (:candidates p))))
      (is (contains? (set (:unknowns shohi))
                     :jp/two-tenths-special-eligibility-criteria-unconfirmed)))))

(deftest jp-kakutei-shinkoku-consumption-select-simplified
  (let [p (plan :facts f/tax-facts-consumption-select-simplified)
        pids (mapv :procedure/id (:candidates p))]
    (testing "簡易課税制度の選択届出書が候補に追加される"
      (is (= [:jp/shotoku-kakutei-shinkoku-aoiro :jp/shohi-kakutei-shinkoku :jp/kani-kazei-sentaku-todoke]
             pids))
      (let [sentaku (nth (:candidates p) 2)]
        (is (= ["消費税簡易課税制度選択届出書"] (:forms sentaku)))
        (is (= {:kind :statutory-deadline :due "2027-12-31" :holiday-exception :tsusoku-10}
               (:deadline sentaku)))))))

(deftest matsuri-tax-intake
  (let [p (plan :facts f/tax-facts-blue)
        packet (c/share-packet {:plan p :facts f/tax-facts-blue :consent (assoc f/consent :recipient :jp.fixture/tax-office)
                                :today f/today})
        intake (c/matsuri-intake {:packet packet :agency f/tax-office-agency :staff f/tax-office-staff
                                  :packs f/packs :today f/today})]
    (testing "Matsuri 税務署受付準備: 人間への引継ぎと確認項目"
      (is (= :ready-for-human-review (:status intake)))
      (is (nil? (:decision intake)) "税務判断・決定は人間が行う（代理決定なし）")
      (is (= {:to :human-reviewer :role :case-reviewer} (:handoff intake)))
      (is (= [:jp/shotoku-kakutei-shinkoku-aoiro] (:procedures intake)))
      (is (seq (:checklist intake)))
      (is (some #(clojure.string/includes? % "青色申告決算書") (:checklist intake))))))

;; ===========================================================================
;; 負の対照テスト（Negative control tests）: 安全境界・非送信・非代理原則
;; ===========================================================================

(deftest tax-negative-controls
  (testing "前提事実の不足・未確定時の停止"
    (is (= :tax-year-missing (:reason (plan :facts (dissoc f/tax-facts-blue :tax-year))))
        "課税年がなければ未確定")
    (is (= :ambiguous-address (:reason (plan :facts (assoc f/tax-facts-blue :address-confirmed? false))))
        "住所未確定なら未確定")
    (is (= :no-authority (:reason (plan :facts f/tax-facts-blue :grant nil)))
        "権限付与がなければ遮断"))
  (testing "制度パックの鮮度期限切れ（stale）時の遮断"
    (is (= :pack-not-current (:reason (plan :facts f/tax-facts-blue :today "2027-03-01"))))
    (is (= :stale (:freshness (plan :facts f/tax-facts-blue :today "2027-03-01")))))
  (testing "ガバナンス: 未レビューパックにおける提出提案の保留（非送信原則）"
    (let [p (plan :facts f/tax-facts-blue)]
      (is (= :withheld-unreviewed-pack (:submission-proposal p)))
      (is (not= :allowed-after-user-check (:submission-proposal p)))))
  (testing "税理士法第52条境界: 税務相談・代理判断アクションの拒絶"
    (is (not (c/grant-allows? {:actor :delegate :principal f/person :scope #{:plan} :expires "2026-12-31"}
                              f/person :tax-consultation f/today))
        "税務相談アクションは許可されない")
    (is (not (c/grant-allows? {:actor :delegate :principal f/person :scope #{:plan} :expires "2026-12-31"}
                              f/person :tax-proxy f/today))
        "税理士法上の税務代理・申告代行アクションは許可されない")
    (is (not (c/grant-allows? {:actor :delegate :principal f/person :scope #{:plan} :expires "2026-12-31"}
                              f/person :e-tax-auto-submit f/today))
        "e-Tax自動送信アクションは許可されない（本人手動提出のみ）"))
  (testing "管轄機関不一致: 市役所窓口は国税（確定申告）の受付準備を拒絶する"
    (let [p (plan :facts f/tax-facts-blue)
          packet (c/share-packet {:plan p :facts f/tax-facts-blue :consent (assoc f/consent :recipient :jp.fixture/city-b-office)
                                  :today f/today})
          r (c/matsuri-intake {:packet packet :agency f/agency :staff f/staff
                               :packs f/packs :today f/today})]
      (is (= :not-this-agency (:reason r))
          "市役所市民課は税務署の手続きを扱わない"))))

