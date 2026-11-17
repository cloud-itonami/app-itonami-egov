(ns fixture-run
  "架空データで Shakai -> 共有 -> Matsuri の通しを実行し、各段の結果を EDN で出力する。
  記録: bb fixture-run > docs/evidence/jp-address-change-fixture-run.edn"
  (:require [clojure.pprint :refer [pprint]]
            [itonami.egov.core :as c]
            [itonami.egov.fixtures :as f]))

(defn -main [& _]
  (let [plan (c/shakai-plan {:packs f/packs :today f/today :grant f/self-grant :facts f/move-facts})
        packet (c/share-packet {:plan plan :facts f/move-facts :consent f/consent :today f/today})
        intake (c/matsuri-intake {:packet packet :agency f/agency :staff f/staff
                                  :packs f/packs :today f/today})]
    (println ";; 架空データによる通しの記録（AWAI-3147）。実在の人・自治体・提出は含まない。")
    (println ";; 生成: bb fixture-run")
    (pprint {:run/today f/today
             :run/fixtures {:facts f/move-facts :consent f/consent :agency f/agency :staff f/staff}
             :step/shakai-plan plan
             :step/share-packet packet
             :step/matsuri-intake intake
             :step/after-submission {:without-receipt (c/submission-status {:submitted? true})}})))
