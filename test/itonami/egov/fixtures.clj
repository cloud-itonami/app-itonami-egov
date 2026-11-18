(ns itonami.egov.fixtures
  "架空データ。実在の人・法人・自治体は使わない。"
  (:require [clojure.edn :as edn]))

(def packs
  [(edn/read-string (slurp "seido/jp.edn"))
   (edn/read-string (slurp "seido/us.edn"))
   (edn/read-string (slurp "seido/se.edn"))])

(def today "2026-11-18")

(def person "fixture-person-1")

(def move-facts
  {:person person
   :event :move
   :from {:country :jp :subdivision :jp.fixture/city-a}
   :to {:country :jp :subdivision :jp.fixture/city-b}
   :moved-on "2026-11-20"
   :address-confirmed? true})

(def self-grant {:actor :self :principal person :scope #{:plan :share}})

(def consent
  {:decision :approved
   :recipient :jp.fixture/city-b-office
   :purpose :address-change-intake
   :items #{:principal-ref :from :to :moved-on :procedures}
   :expires "2026-12-31"})

(def agency
  {:id :jp.fixture/city-b-office
   :name "架空市B 市民課（テスト用）"
   :jurisdiction {:country :jp :subdivision :jp.fixture/city-b}
   :confirmed? true})

(def staff {:id "fixture-staff-1" :agency :jp.fixture/city-b-office :roles #{:intake}})

(def org "fixture-kk-1")

(def kaisha-facts
  {:organization org
   :form :kabushiki-kaisha
   :event :head-office-move
   :from {:country :jp :subdivision :jp.fixture/city-a}
   :to {:country :jp :subdivision :jp.fixture/city-b}
   :moved-on "2026-11-20"
   :address-confirmed? true
   :representative-confirmed? true})

(def se-move-facts
  {:person person
   :event :move
   :from {:country :se :subdivision :se.fixture/kommun-a}
   :to {:country :se :subdivision :se.kommun/goteborg}
   :moved-on "2026-11-20"
   :address-confirmed? true})

(def org-grant {:actor :delegate :principal org :scope #{:plan} :expires "2026-12-31"})
