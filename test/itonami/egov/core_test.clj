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

(deftest applies-when-set-or-keyword
  (is (c/applies? :head-office-move :head-office-move))
  (is (c/applies? #{:a :b} :b))
  (is (not (c/applies? #{:a :b} :c)))
  (is (not (c/applies? nil :a))))

(deftest submission-without-receipt-is-not-done
  (is (= :not-submitted (c/submission-status {:submitted? false})))
  (is (= :submitted-unconfirmed (c/submission-status {:submitted? true})))
  (is (= :receipt-recorded (c/submission-status {:submitted? true :receipt {:id "r-1"}}))))
