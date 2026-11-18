(ns itonami.egov.guest
  "Gyosei: Shakai / Kaisha / Matsuri の画面（AWAI-3148）。

  Awai OS の shell が sandbox iframe（mountRestrictedClientApp）で動かす。
  この画面は通信できない（CSP connect-src 'none'）。外部操作は host の MessagePort に
  selector と payload を渡すだけで、エンドポイントの組み立てと同意は host 側の共通 broker が行う。"
  (:require [clojure.string :as str]
            [itonami.egov.core :as c])
  (:require-macros [itonami.egov.packs :refer [bundled-packs]]))

(def packs (bundled-packs))
(def jp (c/find-pack packs :jp))
(defn- pack-of [country] (c/find-pack packs country))

;; ---------------------------------------------------------------------------
;; 文言（安定したキー。文を断片から組み立てない）

(def lang (if (str/starts-with? (str (some-> js/navigator .-language)) "ja") :ja :en))

(def copy
  {:ja {:title "Gyosei" :tab/shakai "国民 · Shakai" :tab/kaisha "組織 · Kaisha" :tab/matsuri "政府 · Matsuri"
        :from "転出元" :to "転入先" :moved-on "異動日" :confirmed "住所が確定している" :plan "候補を出す"
        :country/jp "日本" :country/us "米国（州は未指定）" :country/se "スウェーデン" :other "パックにない市町村"
        :intro "住所変更の事実を入れると、必要になりそうな手続きを出典つきで出します。"
        :candidate "候補" :due "期限" :submit-to "提出先" :source "出典" :verify "出典を確認"
        :verifying "確認中…" :verified "取得時と一致" :changed "出典が更新されています。制度パックの見直しが必要です"
        :denied "送信しませんでした" :failed "確認できませんでした" :no-digest "digest を計算できませんでした"
        :unreviewed "専門家レビュー前の制度パックです。候補の確認までで、提出は提案しません。"
        :packet "受け渡し票" :make-packet "受け渡し票を作る"
        :deadline/within "{due} まで（{days}日以内）" :deadline/before "{date} より前"
        :source-line "出典: {title}（{issuer}, {date} 取得）"
        :packet-line "受領者: {recipient}　項目: {items}　同意の期限: {date}" :list-sep "・"
        :item/principal-ref "本人の参照" :item/address "住所" :item/moved-on "異動日" :item/procedures "手続き"
        :recipient/city-b "架空市B 市民課（テスト用）"
        :packet-made "受け渡し票を作りました（この端末内のテスト用。送信はしていません）" :hand-to-matsuri "Matsuri タブで開く"
        :form "組織形態" :form/kk "株式会社" :form/ippan "一般社団法人" :rep "代表権を確認した（代表者本人か、期限内の委任）"
        :agency "機関" :role "担当者の役割" :role/intake "受付" :role/viewer "閲覧のみ" :prepare "受付準備をする"
        :no-packet "受け渡し票がまだありません。Shakai タブで作ってください。"
        :ready "受付準備まで。受理・不受理・審査の決定は担当者が行います。" :decision-none "決定: なし" :handoff "引継ぎ先: 審査担当"
        :blocked "止めました" :undetermined "結論を出していません" :host-unavailable "host に接続していません"
        :local-refs "転入先の条例・方針（参考。手続きではありません）" :ref-line "{title}（{issuer}, {date} 取得）"
        :no-verify "この出典は画面から照合できません（取得時の sha256 は記録済み）"
        :packet-jp-only "受け渡し票と Matsuri のテストは日本の架空データだけです"}
   :en {:title "Gyosei" :tab/shakai "People · Shakai" :tab/kaisha "Organizations · Kaisha" :tab/matsuri "Government · Matsuri"
        :from "Moving from" :to "Moving to" :moved-on "Move date" :confirmed "The address is confirmed" :plan "Show candidates"
        :country/jp "Japan" :country/us "United States (no state)" :country/se "Sweden" :other "Municipality not in the pack"
        :intro "Enter the facts of an address change to see likely procedures with their sources."
        :candidate "Candidate" :due "Deadline" :submit-to "Submit to" :source "Source" :verify "Check source"
        :verifying "Checking…" :verified "Matches the recorded source" :changed "The source changed. The institution pack needs review"
        :denied "Nothing was sent" :failed "Could not check" :no-digest "Could not compute the digest"
        :unreviewed "This institution pack has not been reviewed by an expert. Candidates only; no submission is proposed."
        :packet "Handoff packet" :make-packet "Make packet"
        :deadline/within "by {due} (within {days} days)" :deadline/before "before {date}"
        :source-line "Source: {title} ({issuer}, retrieved {date})"
        :packet-line "Recipient: {recipient}  Items: {items}  Consent expires: {date}" :list-sep ", "
        :item/principal-ref "Your reference" :item/address "Address" :item/moved-on "Move date" :item/procedures "Procedures"
        :recipient/city-b "Fictional City B residents' office (test)"
        :packet-made "Packet made (test handoff on this device; nothing was sent)" :hand-to-matsuri "Open in Matsuri"
        :form "Entity form" :form/kk "Kabushiki kaisha" :form/ippan "General incorporated association" :rep "Representation confirmed (the representative or a valid delegation)"
        :agency "Agency" :role "Staff role" :role/intake "Intake" :role/viewer "View only" :prepare "Prepare intake"
        :no-packet "No packet yet. Make one in the Shakai tab."
        :ready "Prepared for intake only. A person decides acceptance and review." :decision-none "Decision: none" :handoff "Handed to: case reviewer"
        :blocked "Stopped" :undetermined "No conclusion" :host-unavailable "Not connected to the host"
        :local-refs "Local rules at the destination (reference only, not a procedure)" :ref-line "{title} ({issuer}, retrieved {date})"
        :no-verify "This source cannot be checked from this screen (its digest at retrieval is recorded)"
        :packet-jp-only "Packet and Matsuri tests use fictional Japanese data only"}})

(defn t [k] (get-in copy [lang k] (name k)))

(defn tf
  "Complete message with named placeholders, e.g. (tf :source-line {:title ..})."
  [k vars]
  (str/replace (t k) #"\{([a-z-]+)\}" (fn [[_ v]] (str (get vars (keyword v) "")))))

(def reason-text
  {:ja {:no-authority "権限がありません" :ambiguous-address "住所が確定していません"
        :subdivision-required "州・市町村を指定するまで手続きを確定できません"
        :unknown-subdivision "制度パックにない市町村・州です。推測はしません"
        :unsupported-jurisdiction "この法域の制度パックはまだありません"
        :cross-border-not-supported "国をまたぐ移動はまだ扱っていません"
        :pack-not-current "制度パックの確認期限が過ぎています" :moved-on-missing "異動日を入れてください"
        :representative-unconfirmed "代表権の確認が取れていません。代表者本人か、期限内の委任が必要です"
        :unsupported-entity-form "この組織形態の手続きはまだ収録していません"
        :consent-denied "同意がありません" :consent-expired "同意の期限が切れています"
        :agency-unconfirmed "機関の確認が取れていません" :wrong-recipient "この受け渡し票の受領者はこの機関ではありません"
        :not-this-agency "この機関で扱う手続きがありません" :no-packet "受け渡し票がありません"}
   :en {:no-authority "No authority" :ambiguous-address "The address is not confirmed"
        :subdivision-required "Procedures cannot be determined until a state or municipality is given"
        :unknown-subdivision "This municipality or state is not in the pack; nothing is guessed"
        :unsupported-jurisdiction "No institution pack for this jurisdiction yet"
        :cross-border-not-supported "Cross-border moves are not covered yet"
        :pack-not-current "The institution pack is past its review date" :moved-on-missing "Enter the move date"
        :representative-unconfirmed "Representation is not confirmed. The representative or a valid delegation is required"
        :unsupported-entity-form "Procedures for this entity form are not in the pack yet"
        :consent-denied "No consent" :consent-expired "Consent has expired"
        :agency-unconfirmed "The agency is not confirmed" :wrong-recipient "This packet is addressed to another agency"
        :not-this-agency "No procedure for this agency" :no-packet "No packet"}})

(defn reason [k] (get-in reason-text [lang k] (name k)))

;; ---------------------------------------------------------------------------
;; host への橋（kotoba.client.host.v1 / kotoba.client.operation.v1）

(defonce port (atom nil))
(defonce pending (atom {}))
(defonce counter (atom 0))

(defn- on-port-message [event]
  (let [data (.-data event)
        id (some-> data (unchecked-get "id"))
        cb (get @pending id)]
    (when cb
      (swap! pending dissoc id)
      (cb data))))

(defn- on-window-message [event]
  (let [data (.-data event)]
    (when (and (nil? @port) (= (.-source event) (.-parent js/window))
               (= "kotoba.client.host.v1" (some-> data (unchecked-get "protocol")))
               (= 1 (some-> event .-ports .-length)))
      (let [p (aget (.-ports event) 0)]
        (reset! port p)
        (set! (.-onmessage p) on-port-message)
        (.start p)))))

(defn invoke-host
  "selector と payload を host へ送る。返るのは host の応答（ok / status / content / error）。"
  [selector payload cb]
  (if-let [p @port]
    (let [id (str "gyosei-" (swap! counter inc))]
      (swap! pending assoc id cb)
      (.postMessage p #js {:protocol "kotoba.client.operation.v1" :id id
                           :selector (clj->js selector) :payload (clj->js payload)}))
    (cb #js {:ok false :error "host-unavailable"})))

(def statute-selector {:capability "awai-egov" :service "e-gov-law-api" :operation "read-statute"})

(defn- statute-request [source]
  (when-let [[_ law elm] (re-find #"law_data/([0-9A-Z]+)\?.*elm=([A-Za-z0-9_-]+)" (:api source ""))]
    {:lawId law :article elm}))

(defn- sha256-hex [text cb]
  (if-let [subtle (some-> js/crypto .-subtle)]
    (-> (.digest subtle "SHA-256" (.encode (js/TextEncoder.) text))
        (.then (fn [buf] (cb (str/join (map #(.padStart (.toString % 16) 2 "0") (js/Array.from (js/Uint8Array. buf)))))))
        (.catch (fn [_] (cb nil))))
    (cb nil)))

;; ---------------------------------------------------------------------------
;; 状態

(defn- today []
  (let [d (js/Date.)]
    (str (.getFullYear d) "-" (.padStart (str (inc (.getMonth d))) 2 "0") "-" (.padStart (str (.getDate d)) 2 "0"))))

(defonce state
  (atom {:tab :shakai
         :shakai {:country :jp :from :jp.fixture/city-a :to :jp.fixture/city-b :moved-on "2026-11-20" :confirmed true}
         :plan nil :checks {} :packet nil
         :kaisha {:form :kabushiki-kaisha :rep false :moved-on "2026-11-20"} :kaisha-result nil
         :matsuri {:agency :jp.fixture/city-b-office :role :intake} :intake nil}))

(def agencies
  {:jp.fixture/city-b-office {:id :jp.fixture/city-b-office :name "架空市B 市民課（テスト用）"
                              :jurisdiction {:country :jp :subdivision :jp.fixture/city-b} :confirmed? true}
   :jp.fixture/city-a-office {:id :jp.fixture/city-a-office :name "架空市A 市民課（テスト用）"
                              :jurisdiction {:country :jp :subdivision :jp.fixture/city-a} :confirmed? true}
   :jp.fixture/unconfirmed {:id :jp.fixture/unconfirmed :name "未確認の窓口（テスト用）"
                            :jurisdiction {:country :jp :subdivision :jp.fixture/city-b} :confirmed? false}})

(defn- place [country sub]
  (if (= country :us) {:country :us} {:country country :subdivision sub}))

(defn- shakai-facts [{:keys [country from to moved-on confirmed]}]
  {:person "local-user" :event :move :from (place country from) :to (place country to)
   :moved-on moved-on :address-confirmed? confirmed})

(defn run-shakai! []
  (let [s (:shakai @state)]
    (swap! state assoc :checks {} :packet nil
           :plan (c/shakai-plan {:packs packs :today (today) :facts (shakai-facts s)
                                 :grant {:actor :self :principal "local-user" :scope #{:plan}}}))))

(def packet-items #{:principal-ref :from :to :moved-on :procedures})
(def packet-item-labels
  "Display order. :from and :to are both shown as the address."
  [[:principal-ref #{:principal-ref}] [:address #{:from :to}] [:moved-on #{:moved-on}] [:procedures #{:procedures}]])
(defn- consent-expiry [] (c/add-days (today) 30))

(defn make-packet! []
  (let [s (:shakai @state)]
    (swap! state assoc :packet
           (c/share-packet {:plan (:plan @state) :facts (shakai-facts s) :today (today)
                            :consent {:decision :approved :recipient :jp.fixture/city-b-office
                                      :purpose :address-change-intake
                                      :items packet-items
                                      :expires (consent-expiry)}}))))

(defn run-kaisha! []
  (let [{:keys [form rep moved-on]} (:kaisha @state)]
    (swap! state assoc :kaisha-result
           (c/kaisha-plan {:packs packs :today (today)
                           :grant {:actor :self :principal "local-org" :scope #{:plan}}
                           :facts {:organization "local-org" :form form :event :head-office-move
                                   :from {:country :jp :subdivision :jp.fixture/city-a}
                                   :to {:country :jp :subdivision :jp.fixture/city-b}
                                   :moved-on moved-on :address-confirmed? true
                                   :representative-confirmed? rep}}))))

(defn run-matsuri! []
  (let [{:keys [agency role]} (:matsuri @state)
        a (get agencies agency)]
    (swap! state assoc :intake
           (c/matsuri-intake {:packet (or (:packet @state) {:status :none}) :agency a
                              :staff {:id "local-staff" :agency (:id a) :roles #{role}}
                              :packs packs :today (today)}))))

(defn check-source! [source-id]
  (let [source (some #(get-in % [:pack/sources source-id]) packs)
        req (statute-request source)]
    (swap! state assoc-in [:checks source-id] :verifying)
    (invoke-host statute-selector req
                 (fn [res]
                   (cond
                     (and (unchecked-get res "ok") (string? (unchecked-get res "content")) (<= 200 (unchecked-get res "status") 299))
                     (sha256-hex (unchecked-get res "content")
                                 (fn [hex]
                                   (swap! state assoc-in [:checks source-id]
                                          (cond (nil? hex) :no-digest
                                                (= hex (:sha256 source)) :verified
                                                :else :changed))))
                     (re-find #"not approved" (str (unchecked-get res "error")))
                     (swap! state assoc-in [:checks source-id] :denied)
                     (= "host-unavailable" (unchecked-get res "error"))
                     (swap! state assoc-in [:checks source-id] :host-unavailable)
                     :else (swap! state assoc-in [:checks source-id] :failed))))))

;; ---------------------------------------------------------------------------
;; DOM（React を使わない小さな描画）

(defn h [tag attrs & children]
  (let [el (.createElement js/document (name tag))]
    (doseq [[k v] attrs]
      (cond
        (= k :on) (doseq [[ev f] v] (.addEventListener el (name ev) f))
        (= k :class) (set! (.-className el) v)
        (true? v) (.setAttribute el (name k) "")
        (or (false? v) (nil? v)) nil
        :else (.setAttribute el (name k) (str v))))
    (doseq [ch (flatten children)]
      (cond (nil? ch) nil
            (string? ch) (.appendChild el (.createTextNode js/document ch))
            :else (.appendChild el ch)))
    el))

(defn- select [value options on-change label]
  (h :label {:class "field"} (h :span {} label)
     (apply h :select {:on {:change #(on-change (keyword (.. % -target -value)))}}
            (for [[k text] options]
              (h :option {:value (subs (str k) 1) :selected (= k value)} text)))))

(defn- banner [text red] (h :p {:class (if red "banner red" "banner") :role (when red "alert")} text))

(defn- stop-view [r]
  (banner (str (t (:status r)) ": " (reason (:reason r))) (= :blocked (:status r))))

(defn- deadline-text [{:keys [kind due due-before days]}]
  (case kind
    :within-days (tf :deadline/within {:due due :days days})
    :before (tf :deadline/before {:date due-before})
    "?"))

(defn- place-name [sub]
  (or (some #(get-in % [:pack/subdivisions sub :name]) packs) (str sub)))

(defn- verifiable? [source-id]
  (some? (statute-request (some #(get-in % [:pack/sources source-id]) packs))))

(defn- candidate-view [cand checks]
  (h :article {:class "card"}
     (h :div {:class "row"} (h :h3 {} (:procedure/title cand)) (h :span {:class "tag"} (t :candidate)))
     (h :p {:class "muted"} (str (t :due) ": " (deadline-text (:deadline cand)) "　"
                                 (t :submit-to) ": " (let [s (:submit-to cand)]
                                                      (cond (keyword? s) (place-name s)
                                                            (:name s) (:name s)
                                                            :else (str s)))))
     (for [src (:sources cand)]
       (let [st (get checks (:id src))]
         (h :div {:class "row"}
            (h :p {:class "muted small"} (tf :source-line {:title (:title src) :issuer (:issuer src) :date (:retrieved-on src)}))
            (if (verifiable? (:id src))
              (h :button {:type "button" :disabled (= st :verifying) :on {:click #(check-source! (:id src))}} (t :verify))
              (h :span {:class "muted small"} (t :no-verify)))
            (when st (h :span {:class (if (#{:verified :verifying} st) "tag" "tag red") :role "status"}
                        (t (if (= st :host-unavailable) :host-unavailable st)))))))
     (for [u (:unknowns cand)] (h :p {:class "muted small"} (str "? " (name u))))))

(defn- shakai-view [{:keys [shakai plan checks packet]}]
  (let [country (:country shakai)
        subs (into [] (map (fn [[k v]] [k (:name v)]) (:pack/subdivisions (pack-of country))))
        put #(do (swap! state assoc-in [:shakai %1] %2) (swap! state assoc :plan nil :packet nil))
        put-country (fn [k]
                      (let [ks (keys (:pack/subdivisions (pack-of k)))]
                        (swap! state update :shakai assoc :country k :from (first ks) :to (or (second ks) (first ks)))
                        (swap! state assoc :plan nil :packet nil)))
        other (keyword (name country) "other")]
    [(h :section {:class "card"}
        (h :div {:class "row wrap"}
           (select country [[:jp (t :country/jp)] [:se (t :country/se)] [:us (t :country/us)]] put-country "国 / Country")
           (when (not= :us country)
             [(select (:from shakai) (conj subs [other (t :other)]) #(put :from %) (t :from))
              (select (:to shakai) (conj subs [other (t :other)]) #(put :to %) (t :to))])
           (h :label {:class "field"} (h :span {} (t :moved-on))
              (h :input {:type "date" :value (:moved-on shakai) :on {:change #(put :moved-on (.. % -target -value))}})))
        (h :label {:class "check"}
           (h :input {:type "checkbox" :checked (:confirmed shakai) :on {:change #(put :confirmed (.. % -target -checked))}})
           (t :confirmed))
        (h :button {:type "button" :class "primary" :on {:click run-shakai!}} (t :plan)))
     (if (nil? plan)
       (h :p {:class "muted"} (t :intro))
       (if (c/stopped? plan)
         (stop-view plan)
         [(when (= :withheld-unreviewed-pack (:submission-proposal plan)) (banner (t :unreviewed) false))
          (for [cand (:candidates plan)] (candidate-view cand checks))
          (when (seq (:local-references plan))
            (h :section {:class "card"}
               (h :h3 {} (t :local-refs))
               (h :ul {} (for [r (:local-references plan)]
                           (h :li {:class "muted small"}
                              (tf :ref-line {:title (:title r) :issuer (:issuer r) :date (:retrieved-on r)}))))))
          (if (not= :jp country)
            (h :p {:class "muted small"} (t :packet-jp-only))
          (h :section {:class "card"}
             (h :h3 {} (t :packet))
             (h :p {:class "muted"} (tf :packet-line
                                       {:recipient (t :recipient/city-b)
                                        :items (str/join (t :list-sep)
                                                         (for [[label ks] packet-item-labels
                                                               :when (some packet-items ks)]
                                                           (t (keyword "item" (name label)))))
                                        :date (consent-expiry)}))
             (if (= :packet (:status packet))
               [(h :p {:role "status"} (t :packet-made))
                (h :button {:type "button" :on {:click #(do (swap! state assoc :tab :matsuri :intake nil))}} (t :hand-to-matsuri))]
               [(when (c/stopped? packet) (stop-view packet))
                (h :button {:type "button" :on {:click make-packet!}} (t :make-packet))])))]))]))

(defn- kaisha-view [{:keys [kaisha kaisha-result checks]}]
  (let [put #(do (swap! state assoc-in [:kaisha %1] %2) (swap! state assoc :kaisha-result nil))]
    [(h :section {:class "card"}
        (select (:form kaisha) [[:kabushiki-kaisha (t :form/kk)] [:ippan-shadan (t :form/ippan)]] #(put :form %) (t :form))
        (h :p {:class "muted"} (str (t :from) ": " (place-name :jp.fixture/city-a) "　" (t :to) ": " (place-name :jp.fixture/city-b)))
        (h :label {:class "field"} (h :span {} (t :moved-on))
           (h :input {:type "date" :value (:moved-on kaisha) :on {:change #(put :moved-on (.. % -target -value))}}))
        (h :label {:class "check"}
           (h :input {:type "checkbox" :checked (:rep kaisha) :on {:change #(put :rep (.. % -target -checked))}})
           (t :rep))
        (h :button {:type "button" :class "primary" :on {:click run-kaisha!}} (t :plan)))
     (cond (nil? kaisha-result) nil
           (c/stopped? kaisha-result) (stop-view kaisha-result)
           :else [(banner (t :unreviewed) false)
                  (for [cand (:candidates kaisha-result)] (candidate-view cand checks))])]))

(defn- proc-title [pid]
  (or (some #(when (= pid (:procedure/id %)) (:procedure/title %)) (:pack/procedures jp)) (name pid)))

(defn- matsuri-view [{:keys [matsuri intake packet]}]
  (let [put #(do (swap! state assoc-in [:matsuri %1] %2) (swap! state assoc :intake nil))]
    [(h :section {:class "card"}
        (select (:agency matsuri) (for [[k a] agencies] [k (:name a)]) #(put :agency %) (t :agency))
        (select (:role matsuri) [[:intake (t :role/intake)] [:viewer (t :role/viewer)]] #(put :role %) (t :role))
        (when-not (= :packet (:status packet)) (h :p {:class "muted"} (t :no-packet)))
        (h :button {:type "button" :class "primary" :on {:click run-matsuri!}} (t :prepare)))
     (cond (nil? intake) nil
           (c/stopped? intake) (stop-view intake)
           :else [(banner (t :ready) false)
                  (h :section {:class "card"}
                     (h :h3 {} (str/join " / " (map proc-title (:procedures intake))))
                     (h :ul {} (for [line (:checklist intake)] (h :li {} line)))
                     (h :div {:class "row"} (h :span {:class "tag"} (t :decision-none)) (h :span {:class "tag"} (t :handoff))))])]))

(defn render! []
  (let [s @state
        root (.getElementById js/document "app")
        tab (fn [k] (h :button {:type "button" :role "tab" :aria-selected (str (= k (:tab s)))
                                :class (if (= k (:tab s)) "tab on" "tab")
                                :on {:click #(swap! state assoc :tab k)}} (t (keyword "tab" (name k)))))]
    (.replaceChildren root
                      (h :header {} (h :h1 {} (t :title))
                         (h :nav {:role "tablist"} (tab :shakai) (tab :kaisha) (tab :matsuri)))
                      (apply h :main {}
                             (case (:tab s)
                               :shakai (shakai-view s)
                               :kaisha (kaisha-view s)
                               :matsuri (matsuri-view s))))))

(defn init []
  (.addEventListener js/window "message" on-window-message)
  (add-watch state ::render (fn [_ _ _ _] (render!)))
  (render!))
